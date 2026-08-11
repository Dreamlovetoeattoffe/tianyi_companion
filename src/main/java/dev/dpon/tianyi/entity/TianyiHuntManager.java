package dev.dpon.tianyi.entity;

import dev.dpon.tianyi.TianyiCompanionMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide registry of hunted players. When a Tianyi's affinity reaches
 * -200 she registers her owner here; every Tianyi on the server will then
 * materialize the recorded weapon and hunt down that player. If fewer than
 * {@link #HUNT_GROUP_SIZE} Tianyi are near the hunted player, helpers are
 * summoned from thin air until five of them are hunting at once.
 */
public final class TianyiHuntManager {
    /** Desired number of Tianyi hunting one player at the same time. */
    public static final int HUNT_GROUP_SIZE = 5;
    private static final double HUNT_GROUP_RADIUS = 48.0D;
    private static final Map<UUID, ItemStack> HUNT_WEAPONS = new HashMap<>();

    private TianyiHuntManager() {}

    public static void startHunt(UUID playerId, ItemStack weapon) {
        HUNT_WEAPONS.put(playerId, weapon.isEmpty() ? ItemStack.EMPTY : weapon.copy());
    }

    public static void endHunt(UUID playerId) {
        HUNT_WEAPONS.remove(playerId);
    }

    public static boolean isHunted(UUID playerId) {
        return HUNT_WEAPONS.containsKey(playerId);
    }

    public static ItemStack getWeaponFor(UUID playerId) {
        return HUNT_WEAPONS.getOrDefault(playerId, ItemStack.EMPTY);
    }

    public static Map<UUID, ItemStack> allHunts() {
        return HUNT_WEAPONS;
    }

    /** Counts Tianyi near the hunted player and summons helpers from thin air so
     *  that {@link #HUNT_GROUP_SIZE} are hunting them at once. */
    public static void ensureHuntGroup(ServerPlayer huntedPlayer, ServerLevel level) {
        int count = level.getEntitiesOfClass(TianyiEntity.class,
                new AABB(huntedPlayer.blockPosition()).inflate(HUNT_GROUP_RADIUS),
                entity -> entity.isAlive()).size();
        if (count >= HUNT_GROUP_SIZE) return;
        int summoned = 0;
        for (int i = 0; i < HUNT_GROUP_SIZE - count; i++) {
            TianyiEntity helper = TianyiCompanionMod.TIANYI.get().create(level);
            if (helper == null) continue;
            helper.moveTo(huntedPlayer.getX() + (level.random.nextDouble() - 0.5D) * 6.0D,
                    huntedPlayer.getY(), huntedPlayer.getZ() + (level.random.nextDouble() - 0.5D) * 6.0D,
                    huntedPlayer.getYRot(), 0.0F);
            helper.setAffinity(TianyiEntity.GLOBAL_HUNT_THRESHOLD);
            helper.setHuntHelperOwner(huntedPlayer.getUUID());
            helper.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true, true));
            level.addFreshEntity(helper);
            summoned++;
        }
        if (summoned > 0) TianyiCompanionMod.award(huntedPlayer, "hunt_pack");
    }
}
