package dev.dpon.tianyi.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Server-side build engine. The player's chat LLM emits a "build" tool call with a
 * list of relative block ops; this engine expands them into cells and places them
 * one batch at a time, with Tianyi standing at the site looking on.
 */
public final class TianyiBuildEngine {
    public static final int MAX_CELLS = 16384;
    public static final int MAX_ANCHOR_DISTANCE = 48;
    private static final int PLACE_SOLID = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int PLACE_ATTACH = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_NEIGHBORS;

    private TianyiBuildEngine() {}

    public record Cell(BlockPos pos, BlockState state, boolean attachable) {
        public boolean isAir() {
            return state.isAir();
        }
    }

    public static final class BuildJob {
        enum Phase { MOVING, BUILDING, FINISHED }

        final List<Cell> cells;
        final BlockPos anchor;
        final UUID ownerUUID;
        final int rate;
        final Vec3 center;
        final AABB box;
        Phase phase = Phase.MOVING;
        int index;
        int ticksInPhase;
        int fxTick;

        BuildJob(List<Cell> cells, BlockPos anchor, UUID ownerUUID, int rate, Vec3 center, AABB box) {
            this.cells = cells;
            this.anchor = anchor;
            this.ownerUUID = ownerUUID;
            this.rate = rate;
            this.center = center;
            this.box = box;
        }

        void place(ServerLevel level, TianyiEntity tianyi) {
            ServerPlayer owner = level.getServer() == null
                    ? null : level.getServer().getPlayerList().getPlayer(ownerUUID);
            boolean creative = owner != null && owner.getAbilities().instabuild;
            if (owner == null && !creative) return; // survival + owner offline: pause
            int placed = 0;
            while (placed < rate && index < cells.size()) {
                Cell cell = cells.get(index++);
                BlockPos pos = cell.pos();
                if (!level.isLoaded(pos)) continue;
                if (level.getBlockState(pos).equals(cell.state())) continue;
                if (level.isOutsideBuildHeight(pos)) continue;
                if (!level.getWorldBorder().isWithinBounds(pos)) continue;
                if (!cell.isAir()) {
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir()
                            && (existing.getDestroySpeed(level, pos) < 0.0F || level.getBlockEntity(pos) != null)) continue;
                    if (!level.isUnobstructed(cell.state(), pos, CollisionContext.empty())) continue;
                    // Attachables that have no support would float: skip instead of placing.
                    if (cell.attachable() && !isDoubleBlock(cell.state())
                            && !cell.state().canSurvive(level, pos)) continue;
                }
                if (!creative && !cell.isAir()) {
                    consume(owner, cell.state().getBlock().asItem());
                }
                if (cell.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                } else {
                    level.setBlock(pos, cell.state(), cell.attachable() ? PLACE_ATTACH : PLACE_SOLID);
                    placeSibling(level, cell.state(), pos);
                }
                removeItemsAt(level, pos);
                if (!cell.isAir() && ++fxTick % 4 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, cell.state()),
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3, 0.2D, 0.2D, 0.2D, 0.02D);
                    level.playSound(null, pos, cell.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
                }
                placed++;
            }
            purgeStuckItems(level, this);
            if (index >= cells.size()) phase = Phase.FINISHED;
        }
    }

    /** True for doors/beds, whose survival depends on the other half (not on surroundings). */
    private static boolean isDoubleBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof DoorBlock || b instanceof BedBlock;
    }

    /** Places the matching other half of a door/bed right after the first half. */
    private static void placeSibling(ServerLevel level, BlockState state, BlockPos pos) {
        Block b = state.getBlock();
        if (b instanceof BedBlock) {
            Direction dir = state.getValue(BedBlock.FACING);
            boolean head = state.getValue(BedBlock.PART) == BedPart.HEAD;
            BlockPos other = head ? pos.relative(dir.getOpposite()) : pos.relative(dir);
            BlockState otherState = state.setValue(BedBlock.PART, head ? BedPart.FOOT : BedPart.HEAD);
            if (!level.getBlockState(other).equals(otherState)) {
                level.setBlock(other, otherState, PLACE_ATTACH);
                removeItemsAt(level, other);
            }
        } else if (b instanceof DoorBlock) {
            boolean lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            BlockPos other = lower ? pos.above() : pos.below();
            BlockState otherState = state.setValue(DoorBlock.HALF, lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            if (!level.getBlockState(other).equals(otherState)) {
                level.setBlock(other, otherState, PLACE_ATTACH);
                removeItemsAt(level, other);
            }
        }
    }

    /** Entry point called from the network payload handler. */
    public static void requestBuild(ServerPlayer player, BlockPos anchor, String opsJson) {
        ServerLevel level = player.serverLevel();
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi == null || !tianyi.isAlive()) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.build_no_tianyi"), false);
            return;
        }
        if (!player.getAbilities().instabuild && !tianyi.hasBuildSkill()) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.build_skill_locked"), false);
            return;
        }
        if (tianyi.getActiveBuild() != null) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.build_busy"), false);
            return;
        }
        if (tianyi.distanceToSqr(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D)
                > MAX_ANCHOR_DISTANCE * (double) MAX_ANCHOR_DISTANCE) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.build_too_far"), false);
            return;
        }

        List<Cell> cells;
        try {
            JsonElement parsed = JsonParser.parseString(opsJson);
            JsonArray ops;
            if (parsed.isJsonArray()) {
                ops = parsed.getAsJsonArray();
            } else if (parsed.isJsonObject() && parsed.getAsJsonObject().has("ops")
                    && parsed.getAsJsonObject().get("ops").isJsonArray()) {
                ops = parsed.getAsJsonObject().getAsJsonArray("ops");
            } else {
                ops = new JsonArray();
            }
            cells = parseOps(ops, anchor, level);
        } catch (Exception ex) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.build_parse_error"), false);
            return;
        }
        if (cells.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.build_parse_error"), false);
            return;
        }
        if (cells.size() > MAX_CELLS) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.build_too_big"), false);
            return;
        }
        if (!player.getAbilities().instabuild) {
            String missing = missingMaterials(player, cells);
            if (missing != null) {
                player.displayClientMessage(Component.translatable("message.tianyi_companion.build_no_material", missing), false);
                return;
            }
        }

        boolean creative = player.getAbilities().instabuild;
        BuildJob job = new BuildJob(cells, anchor.immutable(), player.getUUID(),
                creative ? 5 : 2, computeCenter(cells), computeBox(cells));
        tianyi.setActiveBuild(job);
        player.displayClientMessage(Component.translatable("message.tianyi_companion.build_started"), false);
        tianyi.getNavigation().moveTo(anchor.getX() + 2.5D, anchor.getY(), anchor.getZ() - 3.5D, 1.0D);
    }

    /** Ticks the active build job of a Tianyi entity. Called from aiStep. */
    public static void tick(TianyiEntity tianyi) {
        BuildJob job = tianyi.getActiveBuild();
        if (job == null || tianyi.level().isClientSide) return;
        ServerLevel level = (ServerLevel) tianyi.level();
        switch (job.phase) {
            case MOVING -> {
                job.ticksInPhase++;
                if (tianyi.getNavigation().isDone() || job.ticksInPhase > 200) {
                    job.phase = BuildJob.Phase.BUILDING;
                    job.ticksInPhase = 0;
                }
            }
            case BUILDING -> {
                if (tianyi.tickCount % 8 == 0) {
                    tianyi.getLookControl().setLookAt(job.center.x, job.center.y, job.center.z);
                }
                job.place(level, tianyi);
            }
            case FINISHED -> finish(level, tianyi, job);
        }
    }

    private static void finish(ServerLevel level, TianyiEntity tianyi, BuildJob job) {
        tianyi.setActiveBuild(null);
        purgeStuckItems(level, job);
        ServerPlayer owner = level.getServer() == null
                ? null : level.getServer().getPlayerList().getPlayer(job.ownerUUID);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.tianyi_companion.build_finished"), false);
            TianyiCompanionMod.award(owner, "build_house");
        }
        level.sendParticles(ParticleTypes.NOTE, job.anchor.getX() + 0.5D, job.anchor.getY() + 1.5D,
                job.anchor.getZ() + 0.5D, 32, 2.0D, 1.0D, 2.0D, 0.12D);
        level.playSound(null, job.anchor, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // ---------------------------------------------------------------- parsing

    private static final Comparator<Cell> CELL_ORDER = Comparator
            .comparingInt((Cell c) -> c.attachable() ? 1 : 0)
            .thenComparingInt(c -> c.pos().getY())
            .thenComparingInt(c -> c.pos().getX())
            .thenComparingInt(c -> c.pos().getZ());

    private static List<Cell> parseOps(JsonArray ops, BlockPos anchor, ServerLevel level) {
        LinkedHashMap<BlockPos, BlockState> cells = new LinkedHashMap<>();
        Random random = new Random();
        for (JsonElement el : ops) {
            if (!el.isJsonObject()) continue;
            JsonObject op = el.getAsJsonObject();
            String kind = opString(op, "op", "");
            List<PaletteEntry> palette = parsePalette(opString(op, "block", ""));
            switch (kind) {
                case "set", "air" -> {
                    int x = anchor.getX() + opInt(op, "x", 0);
                    int y = anchor.getY() + opInt(op, "y", 0);
                    int z = anchor.getZ() + opInt(op, "z", 0);
                    BlockState state = palette.isEmpty()
                            ? Blocks.AIR.defaultBlockState()
                            : applyProperties(pick(palette, x, y, z), op);
                    if ("air".equals(kind)) state = Blocks.AIR.defaultBlockState();
                    cells.put(new BlockPos(x, y, z), state);
                }
                case "box" -> box(cells, op, anchor, palette);
                case "walls" -> walls(cells, op, anchor, palette);
                case "line" -> line(cells, op, anchor, palette);
                case "cylinder" -> cylinder(cells, op, anchor, palette);
                case "sphere" -> sphere(cells, op, anchor, palette);
                case "roof" -> roof(cells, op, anchor, palette);
                case "door" -> door(cells, op, anchor, palette);
                case "bed" -> bed(cells, op, anchor, palette);
                case "scatter" -> scatter(cells, op, anchor, palette, random);
                default -> { /* unknown op: ignore */ }
            }
        }
        completeDoubleBlocks(cells);
        List<Cell> result = new ArrayList<>(cells.size());
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockState st = e.getValue();
            result.add(new Cell(e.getKey(), st, !st.isAir() && needsSupport(st)));
        }
        result.sort(CELL_ORDER);
        return result;
    }

    /** Ensures both halves of every door/bed are in the target map, so `set red_bed` builds a full bed.
     *  If a bed's head would collide with an intended block, the bed is flipped so it points into free space. */
    private static void completeDoubleBlocks(Map<BlockPos, BlockState> cells) {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(cells.entrySet());
        for (Map.Entry<BlockPos, BlockState> e : entries) {
            Block b = e.getValue().getBlock();
            BlockPos pos = e.getKey();
            if (b instanceof BedBlock) {
                Direction dir = e.getValue().getValue(BedBlock.FACING);
                boolean headPart = e.getValue().getValue(BedBlock.PART) == BedPart.HEAD;
                BlockPos other = headPart ? pos.relative(dir.getOpposite()) : pos.relative(dir);
                BlockState otherState = e.getValue().setValue(BedBlock.PART, headPart ? BedPart.FOOT : BedPart.HEAD);
                BlockState occupying = cells.get(other);
                if (occupying != null && !occupying.isAir() && !isDoubleBlock(occupying)) {
                    // The far side would overwrite an intended block: try facing the other way.
                    Direction flipped = dir.getOpposite();
                    BlockPos flippedOther = headPart ? pos.relative(flipped.getOpposite()) : pos.relative(flipped);
                    BlockState atFlip = cells.get(flippedOther);
                    if (atFlip == null || atFlip.isAir()) {
                        BlockState flippedState = e.getValue().setValue(BedBlock.FACING, flipped);
                        cells.put(pos, flippedState);
                        cells.put(flippedOther, flippedState.setValue(BedBlock.PART, headPart ? BedPart.FOOT : BedPart.HEAD));
                        continue;
                    }
                }
                cells.putIfAbsent(other, otherState);
            } else if (b instanceof DoorBlock) {
                boolean lower = e.getValue().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                BlockPos other = lower ? pos.above() : pos.below();
                BlockState otherState = e.getValue().setValue(DoorBlock.HALF, lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
                cells.putIfAbsent(other, otherState);
            }
        }
    }

    // ---------------------------------------------------------------- shapes

    private static void box(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        int x1 = anchor.getX() + opInt(op, "x1", 0), x2 = anchor.getX() + opInt(op, "x2", 0);
        int y1 = anchor.getY() + opInt(op, "y1", 0), y2 = anchor.getY() + opInt(op, "y2", 0);
        int z1 = anchor.getZ() + opInt(op, "z1", 0), z2 = anchor.getZ() + opInt(op, "z2", 0);
        int ax1 = Math.min(x1, x2), ax2 = Math.max(x1, x2);
        int ay1 = Math.min(y1, y2), ay2 = Math.max(y1, y2);
        int az1 = Math.min(z1, z2), az2 = Math.max(z1, z2);
        boolean hollow = opBool(op, "hollow");
        for (int y = ay1; y <= ay2; y++) {
            for (int x = ax1; x <= ax2; x++) {
                for (int z = az1; z <= az2; z++) {
                    if (hollow && x > ax1 && x < ax2 && y > ay1 && y < ay2 && z > az1 && z < az2) continue;
                    BlockState state = applyProperties(pick(palette, x, y, z), op);
                    if (state != null) cells.put(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static void walls(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        int x1 = anchor.getX() + opInt(op, "x1", 0), x2 = anchor.getX() + opInt(op, "x2", 0);
        int y1 = anchor.getY() + opInt(op, "y1", 0), y2 = anchor.getY() + opInt(op, "y2", 0);
        int z1 = anchor.getZ() + opInt(op, "z1", 0), z2 = anchor.getZ() + opInt(op, "z2", 0);
        int ax1 = Math.min(x1, x2), ax2 = Math.max(x1, x2);
        int ay1 = Math.min(y1, y2), ay2 = Math.max(y1, y2);
        int az1 = Math.min(z1, z2), az2 = Math.max(z1, z2);
        for (int y = ay1; y <= ay2; y++) {
            for (int x = ax1; x <= ax2; x++) {
                for (int z = az1; z <= az2; z++) {
                    if (x != ax1 && x != ax2 && z != az1 && z != az2) continue;
                    BlockState state = applyProperties(pick(palette, x, y, z), op);
                    if (state != null) cells.put(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static void line(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        int x1 = anchor.getX() + opInt(op, "x1", 0), x2 = anchor.getX() + opInt(op, "x2", 0);
        int y1 = anchor.getY() + opInt(op, "y1", 0), y2 = anchor.getY() + opInt(op, "y2", 0);
        int z1 = anchor.getZ() + opInt(op, "z1", 0), z2 = anchor.getZ() + opInt(op, "z2", 0);
        int steps = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x1 + (x2 - x1) * (float) i / steps);
            int y = Math.round(y1 + (y2 - y1) * (float) i / steps);
            int z = Math.round(z1 + (z2 - z1) * (float) i / steps);
            BlockState state = applyProperties(pick(palette, x, y, z), op);
            if (state != null) cells.put(new BlockPos(x, y, z), state);
        }
    }

    private static void cylinder(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        int cx = anchor.getX() + opInt(op, "x", 0), cy = anchor.getY() + opInt(op, "y", 0), cz = anchor.getZ() + opInt(op, "z", 0);
        int radius = Math.max(1, opInt(op, "radius", 1));
        int height = Math.max(1, opInt(op, "height", 1));
        boolean hollow = opBool(op, "hollow");
        String axis = opString(op, "axis", "y");
        int inner = radius - 1;
        for (int i = 0; i < height; i++) {
            for (int u = -radius; u <= radius; u++) {
                for (int v = -radius; v <= radius; v++) {
                    int d2 = u * u + v * v;
                    if (d2 > radius * radius) continue;
                    if (hollow && inner > 0 && d2 <= inner * inner) continue;
                    BlockPos pos;
                    if ("x".equals(axis)) pos = new BlockPos(cx + i, cy + u, cz + v);
                    else if ("z".equals(axis)) pos = new BlockPos(cx + u, cy + v, cz + i);
                    else pos = new BlockPos(cx + u, cy + i, cz + v);
                    BlockState state = applyProperties(pick(palette, pos.getX(), pos.getY(), pos.getZ()), op);
                    if (state != null) cells.put(pos, state);
                }
            }
        }
    }

    private static void sphere(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        int cx = anchor.getX() + opInt(op, "x", 0), cy = anchor.getY() + opInt(op, "y", 0), cz = anchor.getZ() + opInt(op, "z", 0);
        int radius = Math.max(1, opInt(op, "radius", 1));
        boolean hollow = opBool(op, "hollow");
        int inner = radius - 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > radius * radius) continue;
                    if (hollow && inner > 0 && d2 <= inner * inner) continue;
                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState state = applyProperties(pick(palette, pos.getX(), pos.getY(), pos.getZ()), op);
                    if (state != null) cells.put(pos, state);
                }
            }
        }
    }

    /** Gable/shed/flat roofs. Slab blocks build the surface; every column is filled solid below it. */
    private static void roof(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        int ax1 = anchor.getX() + opInt(op, "x1", 0), ax2 = anchor.getX() + opInt(op, "x2", 0);
        int az1 = anchor.getZ() + opInt(op, "z1", 0), az2 = anchor.getZ() + opInt(op, "z2", 0);
        int y = anchor.getY() + opInt(op, "y", 0);
        int minX = Math.min(ax1, ax2), maxX = Math.max(ax1, ax2);
        int minZ = Math.min(az1, az2), maxZ = Math.max(az1, az2);
        boolean overhang = opBool(op, "overhang");
        String shape = opString(op, "shape", "gable");

        BlockState any = palette.isEmpty() ? null : palette.get(0).block().defaultBlockState();
        if (any == null) return;

        if ("flat".equals(shape)) {
            int ex1 = minX - (overhang ? 1 : 0), ex2 = maxX + (overhang ? 1 : 0);
            int ez1 = minZ - (overhang ? 1 : 0), ez2 = maxZ + (overhang ? 1 : 0);
            BlockState top = any.getBlock() instanceof SlabBlock
                    ? any.setValue(SlabBlock.TYPE, SlabType.DOUBLE) : any;
            for (int x = ex1; x <= ex2; x++) {
                for (int z = ez1; z <= ez2; z++) {
                    cells.put(new BlockPos(x, y, z), top);
                }
            }
            return;
        }

        int w = maxX - minX + 1;
        int d = maxZ - minZ + 1;
        boolean ridgeAlongZ = d >= w; // ridge runs along the longer axis
        int slopeLen = ridgeAlongZ ? w : d;
        int ridgeMin = ridgeAlongZ ? minZ : minX, ridgeMax = ridgeAlongZ ? maxZ : maxX;
        int eaveMin = ridgeAlongZ ? minX : minZ, eaveMax = ridgeAlongZ ? maxX : maxZ;
        if (overhang) {
            eaveMin--;
            eaveMax++;
            slopeLen += 2;
        }
        double half = slopeLen / 2.0;
        double shedLen = Math.max(1, slopeLen - 1);

        for (int r = ridgeMin; r <= ridgeMax; r++) {
            for (int s = eaveMin; s <= eaveMax; s++) {
                double h;
                if ("shed".equals(shape)) {
                    h = 0.9 * (s - eaveMin) + 0.3 * (s - eaveMin) * (s - eaveMin) / shedLen;
                } else {
                    int dist = Math.min(s - eaveMin, eaveMax - s);
                    h = 0.9 * dist + 0.3 * dist * dist / half;
                }
                int hSteps = (int) Math.round(h);
                int layer = y + hSteps / 2;
                boolean bottom = (hSteps % 2) == 0;
                BlockState surface = bottom
                        ? roofCell(any, SlabType.BOTTOM)
                        : roofCell(any, SlabType.DOUBLE);
                BlockState mass = roofCell(any, SlabType.DOUBLE);
                for (int yy = y; yy < layer; yy++) {
                    putAt(cells, r, s, ridgeAlongZ, yy, mass);
                }
                putAt(cells, r, s, ridgeAlongZ, layer, surface);
            }
        }
    }

    private static void putAt(Map<BlockPos, BlockState> cells, int r, int s, boolean ridgeAlongZ, int y, BlockState state) {
        if (ridgeAlongZ) {
            cells.put(new BlockPos(s, y, r), state);
        } else {
            cells.put(new BlockPos(r, y, s), state);
        }
    }

    private static BlockState roofCell(BlockState block, SlabType type) {
        if (block.getBlock() instanceof SlabBlock) {
            return block.setValue(SlabBlock.TYPE, type);
        }
        return block;
    }

    private static void door(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        BlockState block = palette.isEmpty() ? null : palette.get(0).block().defaultBlockState();
        if (block == null || !(block.getBlock() instanceof DoorBlock)) {
            block = Blocks.OAK_DOOR.defaultBlockState();
        }
        int x = anchor.getX() + opInt(op, "x", 0), y = anchor.getY() + opInt(op, "y", 0), z = anchor.getZ() + opInt(op, "z", 0);
        Direction dir = Direction.byName(opString(op, "facing", "north"));
        if (dir == null || !dir.getAxis().isHorizontal()) dir = Direction.NORTH;
        cells.put(new BlockPos(x, y, z), block.setValue(DoorBlock.FACING, dir).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        cells.put(new BlockPos(x, y + 1, z), block.setValue(DoorBlock.FACING, dir).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void bed(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor, List<PaletteEntry> palette) {
        BlockState block = palette.isEmpty() ? null : palette.get(0).block().defaultBlockState();
        if (block == null || !(block.getBlock() instanceof BedBlock)) {
            block = Blocks.RED_BED.defaultBlockState();
        }
        int x = anchor.getX() + opInt(op, "x", 0), y = anchor.getY() + opInt(op, "y", 0), z = anchor.getZ() + opInt(op, "z", 0);
        Direction dir = Direction.byName(opString(op, "facing", "north"));
        if (dir == null || !dir.getAxis().isHorizontal()) dir = Direction.NORTH;
        BlockPos foot = new BlockPos(x, y, z);
        cells.put(foot, block.setValue(BedBlock.FACING, dir).setValue(BedBlock.PART, BedPart.FOOT));
        cells.put(foot.relative(dir), block.setValue(BedBlock.FACING, dir).setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static void scatter(Map<BlockPos, BlockState> cells, JsonObject op, BlockPos anchor,
                                List<PaletteEntry> palette, Random random) {
        if (palette.isEmpty()) return;
        int cx = anchor.getX() + opInt(op, "x", 0), cy = anchor.getY() + opInt(op, "y", 0), cz = anchor.getZ() + opInt(op, "z", 0);
        int radius = Math.max(1, opInt(op, "radius", 2));
        int count = Math.min(64, opInt(op, "count", 8));
        for (int i = 0; i < count; i++) {
            int x = cx + random.nextInt(radius * 2 + 1) - radius;
            int y = cy + random.nextInt(radius * 2 + 1) - radius;
            int z = cz + random.nextInt(radius * 2 + 1) - radius;
            BlockState state = applyProperties(pick(palette, x, y, z), op);
            if (state != null) cells.put(new BlockPos(x, y, z), state);
        }
    }

    // ---------------------------------------------------------------- helpers

    private record PaletteEntry(Block block, int weight) {}

    private static int opInt(JsonObject op, String key, int def) {
        JsonElement e = op.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : def;
    }

    private static boolean opBool(JsonObject op, String key) {
        JsonElement e = op.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static String opString(JsonObject op, String key, String def) {
        JsonElement e = op.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static List<PaletteEntry> parsePalette(String blockStr) {
        List<PaletteEntry> list = new ArrayList<>();
        for (String part : blockStr.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int weight = 1;
            int star = p.indexOf('*');
            if (star > 0) {
                try {
                    weight = Math.max(1, Integer.parseInt(p.substring(star + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
                p = p.substring(0, star).trim();
            }
            ResourceLocation rl = ResourceLocation.tryParse(p);
            Block block = rl == null ? null : BuiltInRegistries.BLOCK.get(rl);
            if (block == null || block == Blocks.AIR) continue;
            list.add(new PaletteEntry(block, weight));
        }
        return list;
    }

    /** Deterministic palette pick: same cell always picks the same block. */
    private static BlockState pick(List<PaletteEntry> palette, int x, int y, int z) {
        if (palette.isEmpty()) return null;
        if (palette.size() == 1) return palette.get(0).block().defaultBlockState();
        int total = 0;
        for (PaletteEntry e : palette) total += e.weight();
        int hash = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        int r = Math.floorMod(hash, total);
        for (PaletteEntry e : palette) {
            r -= e.weight();
            if (r < 0) return e.block().defaultBlockState();
        }
        return palette.get(0).block().defaultBlockState();
    }

    private static BlockState applyProperties(BlockState state, JsonObject op) {
        if (state == null) return null;
        if (op.has("facing")) {
            Direction dir = Direction.byName(opString(op, "facing", ""));
            if (dir != null) state = trySet(state, "facing", dir.getName());
        }
        if (op.has("axis")) state = trySet(state, "axis", opString(op, "axis", ""));
        if (op.has("half")) {
            if (state.getBlock() instanceof SlabBlock) {
                String half = opString(op, "half", "bottom");
                state = trySet(state, "type", "top".equals(half) ? "top" : "bottom");
            } else {
                state = trySet(state, "half", opString(op, "half", ""));
            }
        }
        if (op.has("properties") && op.get("properties").isJsonObject()) {
            JsonObject props = op.getAsJsonObject("properties");
            for (String key : props.keySet()) {
                JsonElement val = props.get(key);
                if (val.isJsonPrimitive()) state = trySet(state, key, val.getAsString());
            }
        }
        return state;
    }

    private static BlockState trySet(BlockState state, String name, String value) {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty(name);
        if (prop == null || value == null) return state;
        return setValue(state, prop, value);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setValue(BlockState state, Property<?> prop, String value) {
        Property<T> typed = (Property<T>) prop;
        return typed.getValue(value).map(v -> state.setValue(typed, v)).orElse(state);
    }

    private static boolean needsSupport(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof DoorBlock || b instanceof BedBlock) return true;
        if (b instanceof TorchBlock || b instanceof LanternBlock) return true;
        if (b instanceof ButtonBlock || b instanceof PressurePlateBlock || b instanceof LeverBlock) return true;
        if (b instanceof BaseRailBlock || b instanceof CarpetBlock) return true;
        if (b instanceof SignBlock || b instanceof BannerBlock || b instanceof LadderBlock) return true;
        if (b instanceof BushBlock) return true;
        return false;
    }

    private static Vec3 computeCenter(List<Cell> cells) {
        double x = 0, y = 0, z = 0;
        for (Cell c : cells) {
            x += c.pos().getX();
            y += c.pos().getY();
            z += c.pos().getZ();
        }
        int n = cells.size();
        return new Vec3(x / n, y / n, z / n);
    }

    /** Axis-aligned bounds of the whole build, used to sweep dropped items afterwards. */
    private static AABB computeBox(List<Cell> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Cell c : cells) {
            BlockPos p = c.pos();
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** Removes any item entity that just dropped at a placed cell (e.g. grass/flowers cleared by the build). */
    private static void removeItemsAt(ServerLevel level, BlockPos pos) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.05D))) {
            e.discard();
        }
    }

    /** Sweeps the build area for item entities that ended up inside solid blocks (stuck in walls). */
    private static void purgeStuckItems(ServerLevel level, BuildJob job) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, job.box)) {
            if (!e.isAlive()) continue;
            BlockState st = level.getBlockState(e.blockPosition());
            if (!st.isAir() && !st.getCollisionShape(level, e.blockPosition()).isEmpty()) {
                e.discard();
            }
        }
    }

    private static String missingMaterials(ServerPlayer player, List<Cell> cells) {
        Map<Item, Integer> needed = new HashMap<>();
        for (Cell c : cells) {
            if (c.isAir()) continue;
            BlockState st = c.state();
            if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD) continue;
            if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) continue;
            Item item = st.getBlock().asItem();
            if (item == null || item == Items.AIR) continue;
            needed.merge(item, 1, Integer::sum);
        }
        Map<Item, Integer> have = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) have.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            int avail = have.getOrDefault(e.getKey(), 0);
            if (avail < e.getValue()) {
                missing.add(e.getKey().getDescription().getString() + " ×" + (e.getValue() - avail));
                if (missing.size() >= 3) break;
            }
        }
        return missing.isEmpty() ? null : String.join("、", missing);
    }

    private static void consume(Player player, Item item) {
        if (item == null || item == Items.AIR) return;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
    }
}
