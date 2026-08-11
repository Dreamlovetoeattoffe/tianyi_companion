package dev.dpon.tianyi;

import dev.dpon.tianyi.entity.TianyiEntity;
import dev.dpon.tianyi.entity.TianyiHuntManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

public final class CommonEvents {
    private CommonEvents() {}

    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        CompoundTag clone = event.getEntity().getPersistentData();
        if (original.hasUUID(TianyiCompanionMod.OWNER_ENTITY_KEY)) {
            clone.putUUID(TianyiCompanionMod.OWNER_ENTITY_KEY,
                    original.getUUID(TianyiCompanionMod.OWNER_ENTITY_KEY));
        }
        clone.putInt(TianyiCompanionMod.PLAYER_HUNT_DEATHS_KEY,
                original.getInt(TianyiCompanionMod.PLAYER_HUNT_DEATHS_KEY));
        int banCost = original.getInt(TianyiCompanionMod.PLAYER_SUMMON_BAN_COST_KEY);
        if (banCost > 0) {
            clone.putInt(TianyiCompanionMod.PLAYER_SUMMON_BAN_COST_KEY, banCost);
        }
        if (original.getBoolean(TianyiCompanionMod.PLAYER_BANISH_NOTICE_KEY)
                && event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.hunt_banished"), true);
        }
        clone.remove(TianyiCompanionMod.PLAYER_BANISH_NOTICE_KEY);
    }

    /** Counts deaths at Tianyi's hands while she hunts the player. On the 7th
     *  death she stops hunting, is dismissed, and the next summon is refused. */
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        if (!isTianyiKill(event.getSource()) || !TianyiHuntManager.isHunted(player.getUUID())) return;
        CompoundTag data = player.getPersistentData();
        int deaths = data.getInt(TianyiCompanionMod.PLAYER_HUNT_DEATHS_KEY) + 1;
        data.putInt(TianyiCompanionMod.PLAYER_HUNT_DEATHS_KEY, deaths);
        if (deaths < TianyiEntity.HUNT_DEATHS_TO_BAN) return;
        TianyiHuntManager.endHunt(player.getUUID());
        data.putInt(TianyiCompanionMod.PLAYER_SUMMON_BAN_COST_KEY, TianyiEntity.XIAOLONGBAO_FORGIVE_COUNT);
        data.putBoolean(TianyiCompanionMod.PLAYER_BANISH_NOTICE_KEY, true);
        TianyiCompanionMod.award(player, "hunt_banished");
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi != null) {
            player.getPersistentData().remove(TianyiCompanionMod.OWNER_ENTITY_KEY);
            tianyi.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    /** True if the kill was dealt by a Tianyi, either directly or via a projectile she fired. */
    private static boolean isTianyiKill(DamageSource source) {
        if (source.getEntity() instanceof TianyiEntity) return true;
        return source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof TianyiEntity;
    }

    /** Teleports Tianyi beside her owner after they travel through a dimension portal. */
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi == null || !tianyi.isAlive() || tianyi.level() == player.level()) return;
        ServerLevel targetLevel = player.serverLevel();
        Vec3 position = player.position();
        DimensionTransition transition = new DimensionTransition(
                targetLevel, position, Vec3.ZERO, player.getYRot(), 0.0F, DimensionTransition.DO_NOTHING);
        tianyi.changeDimension(transition);
    }

    /** Counts a night slept together when the owner wakes up naturally with Tianyi nearby. */
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (event.wakeImmediately() || event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi == null || !tianyi.isAlive()) return;
        // 8 blocks around the player's sleeping spot.
        if (tianyi.distanceToSqr(player) > 64.0D) return;
        tianyi.recordSharedNight(player);
        tianyi.giveDailyGift(player);
    }

    /** At 7120 affinity Tianyi climbs into a nearby bed and spends the night with her owner. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        if (!player.isSleeping()
                && !player.getPersistentData().hasUUID(TianyiCompanionMod.OWNER_ENTITY_KEY)) return;
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi == null || !tianyi.isAlive() || tianyi.level() != player.level()) return;
        if (player.isSleeping()) {
            // Join her owner in bed: she must be within 5 blocks and close enough to climb in.
            if (!tianyi.isSleeping()
                    && tianyi.getAffinity() >= TianyiEntity.SLEEP_TOGETHER_THRESHOLD
                    && tianyi.distanceToSqr(player) <= 25.0D) {
                player.getSleepingPos().ifPresent(tianyi::sleepInBedTogether);
            }
        } else if (tianyi.isSleeping() || tianyi.isNoAi()) {
            tianyi.wakeFromBedTogether();
        }
    }
}
