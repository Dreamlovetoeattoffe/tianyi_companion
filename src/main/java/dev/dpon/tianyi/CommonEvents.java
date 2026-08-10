package dev.dpon.tianyi;

import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.server.level.ServerPlayer;
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
