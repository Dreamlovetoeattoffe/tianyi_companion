package dev.dpon.tianyi.item;

import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

public final class CompanionSummonItem extends Item {
    private final boolean rebirth;

    public CompanionSummonItem(boolean rebirth, Properties properties) {
        super(properties);
        this.rebirth = rebirth;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        Entity existing = findExisting(player);
        if (existing instanceof TianyiEntity && existing.isAlive()) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.already_has_companion")
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.FAIL;
        }

        int banCost = player.getPersistentData().getInt(TianyiCompanionMod.PLAYER_SUMMON_BAN_COST_KEY);
        if (banCost > 0) {
            if (TianyiCompanionMod.consumeItems(player, TianyiCompanionMod.XIAOLONGBAO.get(), banCost)) {
                player.getPersistentData().remove(TianyiCompanionMod.PLAYER_SUMMON_BAN_COST_KEY);
                player.getPersistentData().remove(TianyiCompanionMod.PLAYER_HUNT_DEATHS_KEY);
                player.displayClientMessage(Component.translatable("message.tianyi_companion.summon_debt_paid", banCost), false);
            } else {
                player.displayClientMessage(Component.translatable("message.tianyi_companion.summon_refused", banCost), true);
                return InteractionResult.FAIL;
            }
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawnPos.getX(), spawnPos.getZ());
        int descentY = Math.min(level.getMaxBuildHeight() - 3,
                Math.max(spawnPos.getY() + 10, terrainY + 10));
        TianyiEntity tianyi = TianyiCompanionMod.TIANYI.get().create(level);
        if (tianyi == null) return InteractionResult.FAIL;
        tianyi.moveTo(spawnPos.getX() + 0.5D, descentY, spawnPos.getZ() + 0.5D, player.getYRot(), 0.0F);
        tianyi.tame(player);
        tianyi.setAffinity(rebirth ? 50 : 0);
        tianyi.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true, true));
        tianyi.setCustomName(Component.translatable("entity.tianyi_companion.tianyi"));
        level.addFreshEntity(tianyi);
        player.getPersistentData().putUUID(TianyiCompanionMod.OWNER_ENTITY_KEY, tianyi.getUUID());
        TianyiCompanionMod.award(player, rebirth ? "rebirth_tianyi" : "summon_tianyi");
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        level.broadcastEntityEvent(tianyi, (byte) 7);
        player.displayClientMessage(Component.translatable(rebirth
                ? "message.tianyi_companion.reborn" : "message.tianyi_companion.summoned"), false);
        return InteractionResult.CONSUME;
    }

    private static Entity findExisting(ServerPlayer player) {
        if (player.getPersistentData().hasUUID(TianyiCompanionMod.OWNER_ENTITY_KEY)) {
            UUID id = player.getPersistentData().getUUID(TianyiCompanionMod.OWNER_ENTITY_KEY);
            for (ServerLevel level : player.server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity != null) return entity;
            }
        }
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof TianyiEntity tianyi && player.getUUID().equals(tianyi.getOwnerUUID()) && tianyi.isAlive()) {
                    player.getPersistentData().putUUID(TianyiCompanionMod.OWNER_ENTITY_KEY, tianyi.getUUID());
                    return tianyi;
                }
            }
        }
        player.getPersistentData().remove(TianyiCompanionMod.OWNER_ENTITY_KEY);
        return null;
    }
}
