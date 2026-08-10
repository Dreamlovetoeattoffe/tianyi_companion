package dev.dpon.tianyi.network;

import dev.dpon.tianyi.TianyiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class SkinUpdatePayload implements CustomPacketPayload {
    public static final Type<SkinUpdatePayload> TYPE = new Type<>(TianyiCompanionMod.id("skin_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkinUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkinUpdatePayload::entityId,
            ByteBufCodecs.VAR_INT, SkinUpdatePayload::skinIndex,
            SkinUpdatePayload::new);

    private final int entityId;
    private final int skinIndex;

    public SkinUpdatePayload(int entityId, int skinIndex) {
        this.entityId = entityId;
        this.skinIndex = skinIndex;
    }

    public int entityId() {
        return entityId;
    }

    public int skinIndex() {
        return skinIndex;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}