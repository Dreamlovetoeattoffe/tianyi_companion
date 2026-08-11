package dev.dpon.tianyi;

import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

public final class CommonEvents {
    private CommonEvents() {}

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().hasUUID(TianyiCompanionMod.OWNER_ENTITY_KEY)) {
            event.getEntity().getPersistentData().putUUID(
                    TianyiCompanionMod.OWNER_ENTITY_KEY,
                    event.getOriginal().getPersistentData().getUUID(TianyiCompanionMod.OWNER_ENTITY_KEY));
        }
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
}
