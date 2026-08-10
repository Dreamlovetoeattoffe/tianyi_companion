package dev.dpon.tianyi.entity;

import dev.dpon.tianyi.TianyiCompanionMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public final class TianyiGraveEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(TianyiGraveEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> AFFINITY = SynchedEntityData.defineId(TianyiGraveEntity.class, EntityDataSerializers.INT);

    public TianyiGraveEntity(EntityType<? extends TianyiGraveEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public TianyiGraveEntity(Level level, UUID owner, int affinity) {
        this(TianyiCompanionMod.TIANYI_GRAVE.get(), level);
        entityData.set(OWNER, Optional.ofNullable(owner));
        entityData.set(AFFINITY, affinity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(AFFINITY, 0);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        Optional<UUID> owner = entityData.get(OWNER);
        if (owner.isPresent() && !owner.get().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.tianyi_companion.not_your_grave")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ItemStack heart = TianyiCompanionMod.TIANYI_HEART.toStack();
        if (!player.addItem(heart)) player.drop(heart, false);
        if (player instanceof ServerPlayer serverPlayer) {
            TianyiCompanionMod.award(serverPlayer, "recover_tianyi_heart");
        }
        player.displayClientMessage(Component.translatable("message.tianyi_companion.heart_obtained"), false);
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(AFFINITY, tag.getInt("Affinity"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER).ifPresent(id -> tag.putUUID("Owner", id));
        tag.putInt("Affinity", entityData.get(AFFINITY));
    }
}
