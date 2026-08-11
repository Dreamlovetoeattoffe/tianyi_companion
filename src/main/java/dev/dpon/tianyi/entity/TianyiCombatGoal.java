package dev.dpon.tianyi.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Drives Tianyi's combat: wields the weapon from inventory slot 0 (melee charge/attack
 *  or ranged bow fire), otherwise falls back to her signature note projectile. */
public class TianyiCombatGoal extends Goal {
    private static final double RANGED_RANGE_SQ = 256.0D;
    private final TianyiEntity mob;
    private long nextAttackTime;

    public TianyiCombatGoal(TianyiEntity mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && !mob.isOrderedToSit() && mob.getAffinity() >= 200;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        nextAttackTime = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;
        if (mob.tickCount < nextAttackTime) return;

        boolean melee = TianyiEntity.isMeleeWeapon(mob.getMainHandItem());
        double distanceSq = mob.distanceToSqr(target);
        if (melee) {
            mob.getNavigation().moveTo(target, 1.15D);
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (distanceSq <= 4.0D) {
                mob.performMeleeAttack(target);
                nextAttackTime = mob.tickCount + 20;
            } else {
                nextAttackTime = mob.tickCount + 5;
            }
        } else {
            boolean inRange = distanceSq <= RANGED_RANGE_SQ;
            boolean hasLineOfSight = mob.getSensing().hasLineOfSight(target);
            if (inRange && hasLineOfSight) {
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                mob.performRangedAttack(target, 1.0F);
                nextAttackTime = mob.tickCount + 12;
            } else {
                mob.getNavigation().moveTo(target, 1.05D);
                nextAttackTime = mob.tickCount + 5;
            }
        }
    }
}