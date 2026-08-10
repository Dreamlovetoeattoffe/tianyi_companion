package dev.dpon.tianyi.network;

import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.build.TianyiBuildEngine;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = TianyiCompanionMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkHandler {
    private NetworkHandler() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TianyiCompanionMod.MOD_ID).versioned("1");
        registrar.playToServer(SkinUpdatePayload.TYPE, SkinUpdatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Entity entity = player.serverLevel().getEntity(payload.entityId());
                        if (entity instanceof TianyiEntity tianyi && tianyi.isOwnedBy(player)) {
                            tianyi.setSkinIndex(payload.skinIndex());
                            TianyiCompanionMod.award(player, "change_skin");
                            int bits = player.getPersistentData().getInt("TianyiSeenSkins") | (1 << payload.skinIndex());
                            player.getPersistentData().putInt("TianyiSeenSkins", bits);
                            if (bits == (1 << (TianyiEntity.MAX_SKIN_INDEX + 1)) - 1) {
                                TianyiCompanionMod.award(player, "all_skins");
                            }
                        }
                    }
                }));
        registrar.playToServer(TianyiBuildPayload.TYPE, TianyiBuildPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TianyiBuildEngine.requestBuild(player, payload.anchor(), payload.opsJson());
                    }
                }));
        registrar.playToServer(TianyiTalkPayload.TYPE, TianyiTalkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
                        if (tianyi != null) tianyi.setTalkingToOwner(payload.start());
                    }
                }));
    }
}
