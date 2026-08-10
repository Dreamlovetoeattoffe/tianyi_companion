package dev.dpon.tianyi.network;

import dev.dpon.tianyi.TianyiCompanionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: request Tianyi to build a structure from LLM-generated ops. */
public final class TianyiBuildPayload implements CustomPacketPayload {
    public static final Type<TianyiBuildPayload> TYPE = new Type<>(TianyiCompanionMod.id("build"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TianyiBuildPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TianyiBuildPayload::anchor,
            ByteBufCodecs.STRING_UTF8, TianyiBuildPayload::opsJson,
            TianyiBuildPayload::new);

    private final BlockPos anchor;
    private final String opsJson;

    public TianyiBuildPayload(BlockPos anchor, String opsJson) {
        this.anchor = anchor;
        this.opsJson = opsJson;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public String opsJson() {
        return opsJson;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
