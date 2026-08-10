package dev.dpon.tianyi.network;

import dev.dpon.tianyi.TianyiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: owner opened (start=true) or closed the chat screen with Tianyi. */
public final class TianyiTalkPayload implements CustomPacketPayload {
    public static final Type<TianyiTalkPayload> TYPE = new Type<>(TianyiCompanionMod.id("talk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TianyiTalkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TianyiTalkPayload::start,
            TianyiTalkPayload::new);

    private final boolean start;

    public TianyiTalkPayload(boolean start) {
        this.start = start;
    }

    public boolean start() {
        return start;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
