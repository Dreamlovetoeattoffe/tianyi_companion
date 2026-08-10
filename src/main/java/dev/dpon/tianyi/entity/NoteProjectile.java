package dev.dpon.tianyi.entity;

import dev.dpon.tianyi.TianyiCompanionMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class NoteProjectile extends Projectile {
    public NoteProjectile(EntityType<? extends NoteProjectile> type, Level level) {
        super(type, level);
    }

    public NoteProjectile(Level level, LivingEntity owner) {
        this(TianyiCompanionMod.NOTE_PROJECTILE.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.2D, owner.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) onHit(hit);
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        if (level().isClientSide) level().addParticle(ParticleTypes.NOTE, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        if (tickCount > 80) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity owner = getOwner();
        DamageSource source = owner instanceof LivingEntity living
                ? damageSources().mobProjectile(this, living) : damageSources().magic();
        float damage = owner instanceof TianyiEntity tianyi ? tianyi.getNoteDamage() : 1.0F;
        result.getEntity().hurt(source, damage);
        if (owner instanceof TianyiEntity tianyi && tianyi.getOwner() instanceof ServerPlayer sp) {
            TianyiCompanionMod.award(sp, "first_tone");
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
