package dev.dpon.tianyi.entity;

import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.build.TianyiBuildEngine;
import dev.dpon.tianyi.menu.TianyiMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class TianyiEntity extends TamableAnimal implements RangedAttackMob {
    public static final int MAX_AFFINITY = 712_712;
    /** Most negative affinity allowed. */
    public static final int MIN_AFFINITY = -1000;
    /** At this affinity Tianyi grabs the owner's strongest weapon and attacks them. */
    public static final int HATE_THRESHOLD = -100;
    /** At this affinity every Tianyi on the server hunts the player. */
    public static final int GLOBAL_HUNT_THRESHOLD = -200;
    /** Number of times the hunted player must die to Tianyi before she stops and is dismissed. */
    public static final int HUNT_DEATHS_TO_BAN = 7;
    /** Xiaolongbao Tianyi confiscates to forgive a hunted player, or to accept a banned summon. */
    public static final int XIAOLONGBAO_FORGIVE_COUNT = 64;
    /** Xiaolongbao required to resummon Tianyi after she was killed while hunting (7 stacks). */
    public static final int HUNT_KILL_BAN_COST = 7 * 64;
    /** Max health Tianyi fights with while hunting a player. */
    public static final int HUNT_MAX_HEALTH = 712;
    /** Fixed note damage she fires while hostile (hate mode / no weapon hunt / ranged switched). */
    public static final int HUNT_NOTE_DAMAGE = 15;
    /** Number of skin variants: 0 = default, 1..5 = extra. */
    public static final int MAX_SKIN_INDEX = 5;
    /** Inventory slot she wields as a weapon: the first slot of the last 3x9 row. */
    public static final int WEAPON_SLOT = 18;
    private static final String[] SKIN_KEYS = {
            "skin.tianyi_companion.default",
            "skin.tianyi_companion.original",
            "skin.tianyi_companion.v4formula",
            "skin.tianyi_companion.chef",
            "skin.tianyi_companion.summer",
            "skin.tianyi_companion.variant"
    };
    /** Nights the owner slept near this Tianyi; 5 unlocks the house-building skill. */
    public static final int REQUIRED_SHARED_NIGHTS = 5;
    /** At this affinity Tianyi climbs into a nearby bed and spends the night with her owner. */
    public static final int SLEEP_TOGETHER_THRESHOLD = 7120;
    private static final String[] GIFT_BASIC = {
            "minecraft:coal", "minecraft:charcoal", "minecraft:oak_log", "minecraft:spruce_log",
            "minecraft:birch_log", "minecraft:oak_planks", "minecraft:stone", "minecraft:cobblestone",
            "minecraft:dirt", "minecraft:sand", "minecraft:gravel", "minecraft:flint", "minecraft:stick",
            "minecraft:string", "minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot",
            "minecraft:potato", "minecraft:apple", "minecraft:bread", "minecraft:bone",
            "minecraft:arrow", "minecraft:leather", "minecraft:feather", "minecraft:paper",
            "minecraft:melon_slice", "minecraft:pumpkin", "minecraft:brown_mushroom", "minecraft:red_mushroom"
    };
    private static final String[] GIFT_ORES = {
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot",
            "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
            "minecraft:iron_nugget", "minecraft:gold_nugget", "minecraft:redstone",
            "minecraft:lapis_lazuli", "minecraft:quartz", "minecraft:emerald", "minecraft:amethyst_shard"
    };
    private static final String[] GIFT_VALUABLES = {
            "minecraft:diamond", "minecraft:diamond_sword", "minecraft:diamond_pickaxe",
            "minecraft:diamond_axe", "minecraft:diamond_shovel", "minecraft:diamond_hoe",
            "minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings",
            "minecraft:diamond_boots", "minecraft:netherite_scrap", "minecraft:netherite_ingot",
            "minecraft:netherite_sword", "minecraft:netherite_pickaxe", "minecraft:netherite_axe",
            "minecraft:netherite_shovel", "minecraft:netherite_hoe",
            "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings",
            "minecraft:netherite_boots", "minecraft:netherite_block", "minecraft:diamond_block",
            "minecraft:gold_block", "minecraft:iron_block", "minecraft:emerald_block",
            "minecraft:golden_apple", "minecraft:enchanted_golden_apple", "minecraft:elytra",
            "minecraft:totem_of_undying", "minecraft:ender_pearl", "minecraft:trident", "minecraft:shulker_box"
    };
    private static final EntityDataAccessor<Boolean> BUILD_SKILL = SynchedEntityData.defineId(TianyiEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AFFINITY = SynchedEntityData.defineId(TianyiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(TianyiEntity.class, EntityDataSerializers.INT);
    private final TianyiInventoryContainer companionInventory = new TianyiInventoryContainer(this);
    private int careCooldown;
    private int sharedNights;
    private boolean hateGrabbedWeapon;
    private boolean huntHealed;
    private boolean huntUsesNotes;
    /** Hunted player this Tianyi was summoned to help hunt; null for normal companions. */
    private UUID helperHuntOwner;
    private TianyiBuildEngine.BuildJob activeBuild;
    private boolean talkingToOwner;
    private int talkingTicks;

    public TianyiBuildEngine.BuildJob getActiveBuild() {
        return activeBuild;
    }

    public void setActiveBuild(TianyiBuildEngine.BuildJob job) {
        this.activeBuild = job;
    }

    /** True while the owner is talking with her in the chat screen; she freezes and faces them. */
    public boolean isTalkingToOwner() {
        return talkingToOwner;
    }

    public void setTalkingToOwner(boolean value) {
        talkingToOwner = value;
        talkingTicks = 0;
    }

    /** Marks this Tianyi as a hunt helper summoned for the given hunted player. */
    public void setHuntHelperOwner(UUID uuid) {
        helperHuntOwner = uuid;
    }

    /** True if this Tianyi was summoned from thin air to help hunt a player. */
    public boolean isHuntHelper() {
        return helperHuntOwner != null;
    }

    public TianyiEntity(EntityType<? extends TianyiEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new TianyiCombatGoal(this));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1D, 5.0F, 2.0F));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AFFINITY, 0);
        builder.define(SKIN, 0);
        builder.define(BUILD_SKILL, false);
    }

    public int getAffinity() {
        return entityData.get(AFFINITY);
    }

    public void setAffinity(int value) {
        entityData.set(AFFINITY, Math.max(MIN_AFFINITY, Math.min(MAX_AFFINITY, value)));
        updateMaxHealthFromAffinity();
    }

    /** Whether this Tianyi has learned the house-building skill. Synced to clients. */
    public boolean hasBuildSkill() {
        return entityData.get(BUILD_SKILL);
    }

    public void setBuildSkill(boolean value) {
        entityData.set(BUILD_SKILL, value);
    }

    public int getSharedNights() {
        return sharedNights;
    }

    public void setSharedNights(int value) {
        sharedNights = Math.max(0, value);
    }

    /** Counts one night the owner slept nearby; unlocks the building skill at the threshold. */
    public void recordSharedNight(ServerPlayer owner) {
        if (level().isClientSide || hasBuildSkill()) return;
        sharedNights++;
        if (sharedNights >= REQUIRED_SHARED_NIGHTS) {
            setBuildSkill(true);
            owner.displayClientMessage(Component.translatable("message.tianyi_companion.build_skill_unlocked"), false);
            TianyiCompanionMod.award(owner, "build_skill");
            ((ServerLevel) level()).sendParticles(ParticleTypes.NOTE, getX(), getY() + 1.2D, getZ(), 24, 0.6D, 1.0D, 0.6D, 0.15D);
            level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.15F);
        }
    }

    /** Lies down in a nearby bed for the night beside her sleeping owner. */
    public void sleepInBedTogether(BlockPos bedPos) {
        if (level().isClientSide || isSleeping() || !isAlive()) return;
        BlockPos slot = bedPos;
        BlockState state = level().getBlockState(bedPos);
        if (state.is(BlockTags.BEDS) && state.hasProperty(BedBlock.FACING) && state.hasProperty(BedBlock.PART)) {
            Direction facing = state.getValue(BedBlock.FACING);
            // Her owner occupies one half of the bed; Tianyi takes the other half.
            slot = state.getValue(BedBlock.PART) == BedPart.FOOT
                    ? bedPos.relative(facing)
                    : bedPos.relative(facing.getOpposite());
            if (!level().getBlockState(slot).is(BlockTags.BEDS)) slot = bedPos;
        }
        getNavigation().stop();
        startSleeping(slot);
        setNoAi(true);
        if (getOwner() instanceof ServerPlayer owner) {
            TianyiCompanionMod.award(owner, "sleep_together");
            owner.displayClientMessage(Component.translatable("message.tianyi_companion.sleep_together"), false);
        }
    }

    /** Gets Tianyi back out of bed after her owner wakes. */
    public void wakeFromBedTogether() {
        setNoAi(false);
        stopSleeping();
    }

    /** Returns the currently equipped skin variant index, 0 = default. */
    public int getSkinIndex() {
        return entityData.get(SKIN);
    }

    /** Sets the skin variant index, clamped to the available variants. */
    public void setSkinIndex(int value) {
        entityData.set(SKIN, Math.max(0, Math.min(MAX_SKIN_INDEX, value)));
    }

    /** Returns a localized display name for the given skin variant. */
    public static Component getSkinName(int index) {
        int i = Math.max(0, Math.min(index, SKIN_KEYS.length - 1));
        return Component.translatable(SKIN_KEYS[i]);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(AFFINITY)) updateMaxHealthFromAffinity();
    }

    private void updateMaxHealthFromAffinity() {
        if (getAttribute(Attributes.MAX_HEALTH) == null) return;
        double maxHealth = getAffinity() <= GLOBAL_HUNT_THRESHOLD
                ? HUNT_MAX_HEALTH : 20.0D + getAffinity() / 100;
        if (Math.abs(getAttributeValue(Attributes.MAX_HEALTH) - maxHealth) > 0.001D) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
            if (getHealth() > maxHealth) setHealth((float) maxHealth);
        }
    }

    /** Returns the current note projectile damage, including all affinity growth. */
    public int getNoteDamage() {
        if (getAffinity() <= HATE_THRESHOLD) return HUNT_NOTE_DAMAGE;
        int affinity = getAffinity();
        if (affinity <= 1_000) return Math.min(5, 1 + affinity / 200);
        return 5 + (affinity - 1_000) / 500;
    }

    private void changeAffinity(int delta) {
        int previous = getAffinity();
        setAffinity(previous + delta);
        int current = getAffinity();
        if (delta > 0 && current > previous && !level().isClientSide
                && getOwner() instanceof ServerPlayer owner) {
            TianyiCompanionMod.award(owner, "affinity_root");
            awardAffinityMilestones(owner, previous, current);
            awardRelationshipMilestones(owner, previous, current);
        }
    }

    private void awardRelationshipMilestones(ServerPlayer owner, int previous, int current) {
        if (previous < 200 && current >= 200) TianyiCompanionMod.award(owner, "relationship_friend");
        if (previous < 600 && current >= 600) TianyiCompanionMod.award(owner, "relationship_close");
        if (previous < 1000 && current >= 1000) TianyiCompanionMod.award(owner, "relationship_confidant");
        if (previous < 1500 && current >= 1500) TianyiCompanionMod.award(owner, "relationship_devoted");
    }

    private void awardAffinityMilestones(ServerPlayer owner, int previous, int current) {
        if (previous < 412 && current >= 412) TianyiCompanionMod.award(owner, "affinity_412");
        if (previous < 712 && current >= 712) TianyiCompanionMod.award(owner, "affinity_712");
        if (previous < 712_412 && current >= 712_412) TianyiCompanionMod.award(owner, "affinity_712412");
        if (previous < 712_712 && current >= 712_712) TianyiCompanionMod.award(owner, "affinity_712712");
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (level().isClientSide || (getAffinity() < 200 && !TianyiHuntManager.isHunted(target.getUUID()))) return;
        ItemStack weapon = getMainHandItem();
        // Hostile Tianyi never shoots the stolen bow; she sings notes instead.
        if (isRangedWeapon(weapon) && getAffinity() > HATE_THRESHOLD) {
            fireRangedProjectile(target, weapon);
            return;
        }
        NoteProjectile note = new NoteProjectile(level(), this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.5D) - note.getY();
        double dz = target.getZ() - getZ();
        note.shoot(dx, dy, dz, 1.35F, 1.5F);
        level().addFreshEntity(note);
        level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.NEUTRAL, 0.8F, 1.4F);
    }

    private void fireRangedProjectile(LivingEntity target, ItemStack weapon) {
        if (weapon.getItem() instanceof TridentItem) {
            ThrownTrident trident = new ThrownTrident(level(), this, weapon.copy());
            double dx = target.getX() - getX();
            double dy = target.getY(0.5D) - getY(0.5D);
            double dz = target.getZ() - getZ();
            trident.shoot(dx, dy, dz, 1.8F, 1.0F);
            level().addFreshEntity(trident);
            level().playSound(null, blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            return;
        }
        Arrow arrow = new Arrow(level(), this, new ItemStack(Items.ARROW), weapon);
        arrow.setBaseDamage(2.5D);
        double dx = target.getX() - getX();
        double dy = target.getY(0.5D) - getY(0.5D);
        double dz = target.getZ() - getZ();
        arrow.shoot(dx, dy, dz, 1.6F, 1.0F);
        level().addFreshEntity(arrow);
        level().playSound(null, blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** Equips the weapon stored in inventory slot {@link #WEAPON_SLOT} into her main hand immediately. */
    public void updateEquippedWeapon() {
        if (level().isClientSide) return;
        ItemStack weapon = companionInventory.getItem(WEAPON_SLOT);
        ItemStack held = getMainHandItem();
        if (isWieldableWeapon(weapon)) {
            if (!ItemStack.isSameItemSameComponents(held, weapon)) {
                setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
            }
        } else if (!held.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    /** True if the stack is a bow, crossbow or trident (ranged combat). */
    public static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ProjectileWeaponItem || item instanceof TridentItem;
    }

    /**
     * True if the stack can be swung as a melee weapon. Covers all vanilla melee
     * tools/weapons (class checks), items registered to the vanilla weapon/tool
     * tags (the common way mods declare weapons), and any item exposing a mainhand
     * attack-damage modifier through the vanilla item system.
     */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty() || isRangedWeapon(stack)) return false;
        Item item = stack.getItem();
        if (item instanceof TieredItem || item instanceof MaceItem) return true;
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.MACE_ENCHANTABLE) || stack.is(ItemTags.SHARP_WEAPON_ENCHANTABLE)) {
            return true;
        }
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND)) {
                return true;
            }
        }
        return false;
    }

    /** True if the stack can be used as a weapon at all (melee or ranged). */
    public static boolean isWieldableWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (isRangedWeapon(stack) || isMeleeWeapon(stack));
    }

    /** Swings the weapon held in the main hand at the target, applying its full
     *  attack-damage modifiers (any operation) plus offensive enchantments. */
    public void performMeleeAttack(LivingEntity target) {
        if (level().isClientSide) return;
        ItemStack weapon = getMainHandItem();
        double base = getAttributeValue(Attributes.ATTACK_DAMAGE);
        double damage = base;
        for (ItemAttributeModifiers.Entry entry : weapon.getAttributeModifiers().modifiers()) {
            if (!entry.attribute().is(Attributes.ATTACK_DAMAGE) || !entry.slot().test(EquipmentSlot.MAINHAND)) continue;
            AttributeModifier modifier = entry.modifier();
            switch (modifier.operation()) {
                case ADD_VALUE -> damage += modifier.amount();
                case ADD_MULTIPLIED_BASE -> damage += base * modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> damage *= 1.0D + modifier.amount();
            }
        }
        ServerLevel serverLevel = (ServerLevel) level();
        DamageSource source = serverLevel.damageSources().mobAttack(this);
        float totalDamage = EnchantmentHelper.modifyDamage(serverLevel, weapon, target, source, (float) damage);
        // The weapon was stolen from her owner: she swings it for triple its max damage.
        if (hateGrabbedWeapon) totalDamage *= 3.0F;
        boolean hurt = target.hurt(source, totalDamage);
        if (hurt) {
            target.knockback(0.4F, getX() - target.getX(), getZ() - target.getZ());
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, source, weapon);
        }
        swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return getAffinity() >= 200;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && talkingToOwner) {
            talkingTicks++;
            Player owner = getOwner() instanceof Player o ? o : null;
            boolean valid = owner != null && owner.isAlive() && distanceToSqr(owner) <= 2304.0D;
            if (!valid || talkingTicks > 3600) {
                talkingToOwner = false;
            } else {
                setSpeed(0.0F);
                setXxa(0.0F);
                setYya(0.0F);
                setZza(0.0F);
                getNavigation().stop();
                getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ(), 10.0F, 10.0F);
            }
        }
        if (!level().isClientSide) TianyiBuildEngine.tick(this);
        if (!level().isClientSide) updateEquippedWeapon();
        if (careCooldown > 0) careCooldown--;
        if (!level().isClientSide && tickCount % 100 == 0 && getAffinity() >= 600
                && getOwner() instanceof ServerPlayer nearbyOwner && distanceToSqr(nearbyOwner) < 144.0D) {
            nearbyOwner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 140, 0));
        }
        if (!level().isClientSide && tickCount % 20 == 0 && getOwner() instanceof ServerPlayer owner
                && hasCompanionEquipment()) {
            TianyiCompanionMod.award(owner, "arm_tianyi");
            if (hasFullArmorEquipped()) TianyiCompanionMod.award(owner, "full_armor_tianyi");
        }
        if (!level().isClientSide && careCooldown == 0 && getOwner() instanceof ServerPlayer owner
                && owner.isAlive() && owner.getHealth() < owner.getMaxHealth() * 0.60F && distanceToSqr(owner) < 144.0D) {
            careForOwner(owner);
        }
        if (!level().isClientSide) updateHateAndHunt();
    }

    /**
     * Handles Tianyi's hatred: at -100 affinity she grabs the strongest weapon
     * from her owner's inventory and attacks her owner; at -200 affinity every
     * Tianyi on the server materializes that same weapon and hunts the player
     * wherever they are.
     */
    private void updateHateAndHunt() {
        // Hunt helpers vanish once the hunt they were summoned for ends.
        if (helperHuntOwner != null && !TianyiHuntManager.isHunted(helperHuntOwner)) {
            remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            return;
        }
        if (getAffinity() <= GLOBAL_HUNT_THRESHOLD) {
            if (!huntHealed) {
                huntHealed = true;
                setHealth(getMaxHealth());
                if (getOwner() instanceof ServerPlayer owner) {
                    TianyiCompanionMod.award(owner, "hunt_started");
                    // With no weapon to grab she hunts with note projectiles.
                    huntUsesNotes = !hateGrabbedWeapon;
                }
            }
        } else {
            huntHealed = false;
            huntUsesNotes = false;
        }
        if (getAffinity() <= HATE_THRESHOLD && getOwner() instanceof ServerPlayer owner) {
            if (!hateGrabbedWeapon) {
                hateGrabbedWeapon = grabStrongestWeaponFrom(owner);
                if (hateGrabbedWeapon) {
                    TianyiCompanionMod.award(owner, "weapon_snatched");
                    owner.displayClientMessage(Component.translatable(
                            "message.tianyi_companion.hate_grabbed_weapon"), false);
                }
            }
            if (owner.isAlive() && getTarget() != owner) {
                setTarget(owner);
            }
        } else if (getTarget() == getOwner() && getAffinity() > HATE_THRESHOLD) {
            setTarget(null);
        }
        if (getAffinity() <= GLOBAL_HUNT_THRESHOLD) {
            if (getOwner() instanceof ServerPlayer owner) {
                if (tickCount % 40 == 0 && tryConfiscateXiaolongbao(owner)) {
                    forgiveHuntedOwner(owner);
                } else {
                    TianyiHuntManager.startHunt(owner.getUUID(), getHateWeapon());
                    if (tickCount % 40 == 0) {
                        TianyiHuntManager.ensureHuntGroup(owner, (ServerLevel) level());
                    }
                }
            }
        } else if (getOwnerUUID() != null && TianyiHuntManager.isHunted(getOwnerUUID())
                && getAffinity() > GLOBAL_HUNT_THRESHOLD) {
            TianyiHuntManager.endHunt(getOwnerUUID());
        }
        if (tickCount % 20 == 0) {
            globalHuntStep();
        }
    }

    /** Confiscates {@link #XIAOLONGBAO_FORGIVE_COUNT} xiaolongbao from the player's
     *  inventory. Returns true only if the full amount was taken. */
    private boolean tryConfiscateXiaolongbao(ServerPlayer player) {
        if (TianyiCompanionMod.countItems(player, TianyiCompanionMod.XIAOLONGBAO.get())
                < XIAOLONGBAO_FORGIVE_COUNT) {
            return false;
        }
        TianyiCompanionMod.consumeItems(player, TianyiCompanionMod.XIAOLONGBAO.get(), XIAOLONGBAO_FORGIVE_COUNT);
        return true;
    }

    /** Ends the global hunt: the player paid in xiaolongbao, so she forgives them,
     *  restores affinity to 0 and stops hunting. */
    private void forgiveHuntedOwner(ServerPlayer owner) {
        setAffinity(0);
        TianyiHuntManager.endHunt(owner.getUUID());
        owner.getPersistentData().remove(TianyiCompanionMod.PLAYER_HUNT_DEATHS_KEY);
        owner.getPersistentData().remove(TianyiCompanionMod.PLAYER_SUMMON_BAN_COST_KEY);
        hateGrabbedWeapon = false;
        if (getTarget() == owner) setTarget(null);
        TianyiCompanionMod.award(owner, "hunt_forgiven");
        owner.displayClientMessage(Component.translatable("message.tianyi_companion.hunt_forgiven"), true);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 1.3D, getZ(), 24, 0.5D, 0.7D, 0.5D, 0.1D);
        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.3F);
    }

    /** Every registered hunt is where all Tianyi converge: equip the recorded
     *  weapon and seek out the hunted player, teleporting across the world. */
    private void globalHuntStep() {
        if (TianyiHuntManager.allHunts().isEmpty()) return;
        ServerLevel server = (ServerLevel) level();
        ServerPlayer nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<UUID, ItemStack> hunt : TianyiHuntManager.allHunts().entrySet()) {
            ServerPlayer target = server.getServer().getPlayerList().getPlayer(hunt.getKey());
            if (target == null || !target.isAlive()) continue;
            double distance = distanceTo(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = target;
            }
        }
        if (nearest == null) return;

        ItemStack weapon = TianyiHuntManager.getWeaponFor(nearest.getUUID());
        if (!weapon.isEmpty() && !ItemStack.isSameItemSameComponents(getMainHandItem(), weapon)) {
            setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
        }
        if (getTarget() != nearest) setTarget(nearest);
        setOrderedToSit(false);
        boolean farAway = bestDistance > 48.0D || level() != nearest.level();
        if (farAway && tickCount % 60 == 0) {
            teleportNear(nearest);
        }
    }

    private ItemStack getHateWeapon() {
        ItemStack held = getMainHandItem();
        if (isWieldableWeapon(held)) return held;
        ItemStack bagged = companionInventory.getItem(WEAPON_SLOT);
        if (isWieldableWeapon(bagged)) return bagged;
        if (huntUsesNotes) return ItemStack.EMPTY;
        return new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
    }

    private void teleportNear(ServerPlayer target) {
        ServerLevel targetLevel = target.serverLevel();
        if (level() != targetLevel) {
            DimensionTransition transition = new DimensionTransition(
                    targetLevel, target.position().add(0.0D, 2.0D, 0.0D), Vec3.ZERO,
                    target.getYRot(), 0.0F, DimensionTransition.DO_NOTHING);
            changeDimension(transition);
            return;
        }
        double x = target.getX() + (random.nextDouble() - 0.5D) * 6.0D;
        double z = target.getZ() + (random.nextDouble() - 0.5D) * 6.0D;
        double y = target.getY();
        randomTeleport(x, y, z, true);
        ((ServerLevel) level()).sendParticles(ParticleTypes.PORTAL,
                getX(), getY() + 1.0D, getZ(), 24, 0.6D, 0.8D, 0.6D, 0.1D);
    }

    /**
     * Removes the strongest weapon (largest melee or ranged damage) from the
     * owner's whole inventory and equips it. Returns true if a weapon was taken.
     */
    private boolean grabStrongestWeaponFrom(ServerPlayer owner) {
        ItemStack best = ItemStack.EMPTY;
        int bestSlot = -1;
        double bestPower = -1.0D;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            if (!isWieldableWeapon(stack)) continue;
            double power = weaponPower(stack);
            if (power > bestPower) {
                bestPower = power;
                best = stack.copy();
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return false;
        owner.getInventory().setItem(bestSlot, ItemStack.EMPTY);
        companionInventory.setItem(WEAPON_SLOT, best);
        updateEquippedWeapon();
        return true;
    }

    private double weaponPower(ItemStack stack) {
        if (isMeleeWeapon(stack)) {
            double base = 1.0D;
            for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
                if (!entry.attribute().is(Attributes.ATTACK_DAMAGE) || !entry.slot().test(EquipmentSlot.MAINHAND)) continue;
                base += entry.modifier().amount();
            }
            return base;
        }
        if (isRangedWeapon(stack)) return 6.0D;
        return 0.0D;
    }

    /** Collects every effect on a food item (ignoring probability). */
    private static List<MobEffectInstance> itemEffectsOf(FoodProperties foodProperties) {
        List<MobEffectInstance> effects = new ArrayList<>();
        for (FoodProperties.PossibleEffect possible : foodProperties.effects()) {
            effects.add(possible.effect());
        }
        return effects;
    }

    /** Hearts restored by a positive effect (instant health / regeneration). */
    private static double healingHeartsOf(MobEffectInstance instance) {
        int amplifier = instance.getAmplifier();
        if (instance.is(MobEffects.HEAL)) {
            return (amplifier + 1) * 3.0D;
        }
        if (instance.is(MobEffects.REGENERATION)) {
            return instance.getDuration() * (amplifier + 1) / 50.0D / 2.0D;
        }
        return 0.0D;
    }

    /** Damage (in hearts taken by enemies) caused by negative effects: poison,
     *  hunger, wither, instant damage. Used to punish feeding bad items. */
    private static double totalNegativeDamage(List<MobEffectInstance> effects) {
        double damage = 0.0D;
        for (MobEffectInstance instance : effects) {
            int amplifier = instance.getAmplifier();
            if (instance.is(MobEffects.POISON)) {
                // 1 HP every 25 / (amplifier+1) ticks for the whole duration.
                damage += instance.getDuration() / (25.0D / (amplifier + 1)) / 2.0D;
            } else if (instance.is(MobEffects.WITHER)) {
                damage += instance.getDuration() / (40.0D / (amplifier + 1)) / 2.0D;
            } else if (instance.is(MobEffects.HUNGER)) {
                damage += (amplifier + 1) * 2.0D;
            } else if (instance.is(MobEffects.HARM)) {
                damage += (amplifier + 1) * 3.0D;
            }
        }
        return damage;
    }

    private void careForOwner(ServerPlayer owner) {
        int foodSlot = -1;
        // Search Tianyi's 3x9 main inventory for food.
        for (int i = 0; i < 27; i++) {
            ItemStack stack = companionInventory.getItem(i);
            if (!stack.isEmpty() && stack.getFoodProperties(owner) != null) {
                foodSlot = i;
                break;
            }
        }
        if (foodSlot >= 0) {
            ItemStack food = companionInventory.getItem(foodSlot);
            food.shrink(1);
            owner.heal(getAffinity() >= 1000 ? 7.0F : 5.0F);
            owner.getFoodData().eat(4, 0.5F);
            TianyiCompanionMod.award(owner, "care_for_owner");
            owner.displayClientMessage(Component.translatable("message.tianyi_companion.fed_owner"), true);
        } else {
            owner.heal(getAffinity() >= 1000 ? 5.0F : 3.0F);
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, getAffinity() >= 1000 ? 160 : 100, 0));
            owner.displayClientMessage(Component.translatable("message.tianyi_companion.healed_owner"), true);
        }
        if (getAffinity() >= 1500) {
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        }
        ((ServerLevel) level()).sendParticles(ParticleTypes.NOTE, owner.getX(), owner.getY() + 1.0D, owner.getZ(), 8, 0.5D, 0.7D, 0.5D, 0.1D);
        level().playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.3F);
        careCooldown = 400;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isOwnedBy(player)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            setOrderedToSit(!isOrderedToSit());
            player.displayClientMessage(Component.translatable(isOrderedToSit()
                    ? "message.tianyi_companion.wait" : "message.tianyi_companion.follow", getAffinity())
                    .withStyle(ChatFormatting.AQUA), true);
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                TianyiCompanionMod.award(serverPlayer, "sit_tianyi");
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        FoodProperties foodProperties = held.getFoodProperties(this);
        if (foodProperties != null) {
            if (!level().isClientSide) {
                double negativeDamage = totalNegativeDamage(itemEffectsOf(foodProperties));
                if (negativeDamage > 0.0D) {
                    int loss = (int) (negativeDamage * 10.0D);
                    changeAffinity(-loss);
                    if (player instanceof ServerPlayer serverPlayer) {
                        TianyiCompanionMod.award(serverPlayer, "negative_feed");
                    }
                    player.displayClientMessage(Component.translatable(
                            "message.tianyi_companion.negative_feed", loss), true);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                } else {
                    int affection = calculateFoodAffection(held, foodProperties);
                    changeAffinity(affection);
                    heal(Math.min(8.0F, affection));
                    applyFoodEffect(held);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    if (player instanceof ServerPlayer serverPlayer) {
                        TianyiCompanionMod.award(serverPlayer, "feed_tianyi");
                        if (getAffinity() >= 100) TianyiCompanionMod.award(serverPlayer, "devoted_tianyi");
                        if (held.is(TianyiCompanionMod.XIAOLONGBAO)) {
                            TianyiCompanionMod.award(serverPlayer, "feed_xiaolongbao");
                        }
                    }
                    ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 1.3D, getZ(), 6, 0.4D, 0.5D, 0.4D, 0.0D);
                    player.displayClientMessage(Component.translatable("message.tianyi_companion.affinity",
                            getAffinity(), Component.translatable(getAffinityTierKey())), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (held.getItem() instanceof PotionItem) {
            if (!level().isClientSide) {
                PotionContents contents = held.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                List<MobEffectInstance> effects = new ArrayList<>();
                contents.getAllEffects().forEach(effects::add);
                double healedHearts = 0.0D;
                double negativeDamage = totalNegativeDamage(effects);
                for (MobEffectInstance instance : effects) {
                    healedHearts += healingHeartsOf(instance);
                }
                int delta = (int) (healedHearts * 10.0D) - (int) (negativeDamage * 10.0D);
                changeAffinity(delta);
                if (healedHearts > 0.0D) heal((float) (healedHearts * 2.0D));
                if (negativeDamage > 0.0D) {
                    player.displayClientMessage(Component.translatable(
                            "message.tianyi_companion.potion_harm", (int) negativeDamage), true);
                } else if (healedHearts > 0.0D) {
                    player.displayClientMessage(Component.translatable(
                            "message.tianyi_companion.potion_heal", (int) healedHearts), true);
                }
                if (!player.getAbilities().instabuild) held.shrink(1);
                if (player instanceof ServerPlayer serverPlayer) {
                    TianyiCompanionMod.award(serverPlayer, "potion_feed");
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (held.isEmpty()) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (TianyiHuntManager.isHunted(serverPlayer.getUUID())) {
                    serverPlayer.displayClientMessage(Component.translatable(
                            "message.tianyi_companion.hunt_no_inventory"), true);
                    return InteractionResult.sidedSuccess(level().isClientSide);
                }
                TianyiCompanionMod.award(serverPlayer, "open_tianyi_inventory");
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) -> new TianyiMenu(containerId, inventory, this),
                        TianyiMenu.title()), buffer -> buffer.writeVarInt(getId()));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /**
     * Calculates the affinity gain from a food using its nutrition value.
     * Xiaolongbao intentionally has a custom affinity base: its nutrition
     * is 8, while its affinity value A is 12.
     */
    private int calculateFoodAffection(ItemStack stack, FoodProperties foodProperties) {
        return stack.is(TianyiCompanionMod.XIAOLONGBAO) ? 12 : foodProperties.nutrition();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.getFoodProperties(this) != null;
    }

    private String getAffinityTierKey() {
        if (getAffinity() <= GLOBAL_HUNT_THRESHOLD) return "tier.tianyi_companion.hunted";
        if (getAffinity() <= HATE_THRESHOLD) return "tier.tianyi_companion.hateful";
        if (getAffinity() < 0) return "tier.tianyi_companion.wary";
        if (getAffinity() >= 1500) return "tier.tianyi_companion.devoted";
        if (getAffinity() >= 1000) return "tier.tianyi_companion.confidant";
        if (getAffinity() >= 600) return "tier.tianyi_companion.close";
        if (getAffinity() >= 200) return "tier.tianyi_companion.friend";
        return "tier.tianyi_companion.new";
    }

    private void applyFoodEffect(ItemStack stack) {
        if (stack.is(Items.COOKIE)) addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
        if (stack.is(Items.CAKE)) addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0));
        if (stack.is(Items.GOLDEN_CARROT)) addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0));
        if (stack.is(Items.GOLDEN_APPLE)) {
            setHealth(getMaxHealth());
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
        }
    }

    /** On waking up, Tianyi hands the owner one random item from the tier her affection unlocked. */
    public void giveDailyGift(ServerPlayer owner) {
        if (level().isClientSide || owner == null) return;
        long day = level().getDayTime() / 24000L;
        if (owner.getPersistentData().getLong("TianyiGiftDay") == day) return;
        owner.getPersistentData().putLong("TianyiGiftDay", day);

        int affinity = getAffinity();
        List<Item> pool = giftPool(affinity);
        if (pool.isEmpty()) return;
        Random random = new Random();
        Item picked = pool.get(random.nextInt(pool.size()));
        ItemStack stack = new ItemStack(picked, giftCount(affinity));
        if (!owner.getInventory().add(stack)) {
            owner.drop(stack, false);
        }
        owner.displayClientMessage(Component.translatable("message.tianyi_companion.daily_gift",
                stack.getHoverName()), false);
        TianyiCompanionMod.award(owner, "gift_first");
        if (affinity >= 712_000) TianyiCompanionMod.award(owner, "gift_forbidden");
        else if (affinity >= 71_200) TianyiCompanionMod.award(owner, "gift_host");
        else if (affinity >= 7_120) TianyiCompanionMod.award(owner, "gift_precious");
        else if (affinity >= 712) TianyiCompanionMod.award(owner, "gift_mineral");
    }

    private static List<Item> giftPool(int affinity) {
        List<Item> pool = new ArrayList<>();
        addGifts(pool, GIFT_BASIC);
        if (affinity >= 712) addGifts(pool, GIFT_ORES);
        if (affinity >= 7_120) addGifts(pool, GIFT_VALUABLES);
        if (affinity >= 71_200) {
            boolean everything = affinity >= 712_000;
            Set<String> excluded = everything
                    ? Set.of()
                    : Set.of("minecraft:bedrock", "minecraft:barrier");
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                if (excluded.contains(BuiltInRegistries.ITEM.getKey(item).toString())) continue;
                pool.add(item);
            }
        }
        return pool;
    }

    private static void addGifts(List<Item> pool, String[] ids) {
        for (String id : ids) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
            if (item != null && item != Items.AIR) pool.add(item);
        }
    }

    private static int giftCount(int affinity) {
        Random random = new Random();
        if (affinity < 712) return 4 + random.nextInt(13);
        if (affinity < 7_120) return 2 + random.nextInt(7);
        return 1;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            if (source.getEntity() instanceof LivingEntity attacker && attacker.isAlive()
                    && !attacker.is(this) && !isOwnedBy(attacker)) {
                setTarget(attacker);
            }
            if (source.getEntity() instanceof ServerPlayer attacker) {
                changeAffinity(-5);
                TianyiCompanionMod.award(attacker, "hurt_tianyi");
                attacker.displayClientMessage(Component.translatable("message.tianyi_companion.affinity_lost"), true);
                // Hostile Tianyi has a 30% chance to reflect the hit back at her attacker.
                if (getAffinity() <= HATE_THRESHOLD && attacker.isAlive()
                        && random.nextDouble() < 0.30D) {
                    attacker.hurt(level().damageSources().mobAttack(this), amount);
                    attacker.displayClientMessage(Component.translatable(
                            "message.tianyi_companion.hate_reflect"), true);
                }
            }
        }
        return hurt;
    }

    private boolean hasCompanionEquipment() {
        return !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
                || !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                || !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()
                || !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
    }

    private boolean hasFullArmorEquipped() {
        return !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
                && !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                && !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()
                && !getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = this.dead;
        if (getOwnerUUID() != null) {
            TianyiHuntManager.endHunt(getOwnerUUID());
        }
        super.die(source);
        if (!level().isClientSide && !wasDead && this.dead && !isRemoved()) {
            if (getOwner() instanceof ServerPlayer owner) {
                owner.getPersistentData().remove(TianyiCompanionMod.OWNER_ENTITY_KEY);
                // Killed mid-hunt she leaves no grave behind, and can only be
                // summoned again after the owner pays 7 stacks of xiaolongbao.
                if (getAffinity() > GLOBAL_HUNT_THRESHOLD) {
                    TianyiGraveEntity grave = new TianyiGraveEntity(level(), getOwnerUUID(), getAffinity());
                    grave.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
                    level().addFreshEntity(grave);
                    owner.displayClientMessage(Component.translatable("message.tianyi_companion.grave_placed"), false);
                } else {
                    owner.getPersistentData().putInt(
                            TianyiCompanionMod.PLAYER_SUMMON_BAN_COST_KEY, HUNT_KILL_BAN_COST);
                    TianyiCompanionMod.award(owner, "hunt_slayer");
                    owner.displayClientMessage(Component.translatable("message.tianyi_companion.hunt_killed_ban"), true);
                }
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Affinity", getAffinity());
        tag.putInt("Skin", getSkinIndex());
        tag.putInt("SharedNights", sharedNights);
        tag.putBoolean("BuildSkill", hasBuildSkill());
        tag.put("CompanionInventory", companionInventory.saveInventory(registryAccess()));
        tag.putInt("CareCooldown", careCooldown);
        tag.putBoolean("HateGrabbedWeapon", hateGrabbedWeapon);
        if (helperHuntOwner != null) tag.putUUID("HuntHelperOwner", helperHuntOwner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAffinity(tag.getInt("Affinity"));
        setSkinIndex(tag.getInt("Skin"));
        setSharedNights(tag.getInt("SharedNights"));
        setBuildSkill(tag.getBoolean("BuildSkill"));
        if (tag.contains("CompanionInventory")) {
            companionInventory.loadInventory(tag.getList("CompanionInventory", 10), registryAccess());
        } else {
            companionInventory.fromTag(tag.getList("FoodBag", 10), registryAccess());
        }
        careCooldown = tag.getInt("CareCooldown");
        hateGrabbedWeapon = tag.getBoolean("HateGrabbedWeapon");
        if (tag.contains("HuntHelperOwner")) helperHuntOwner = tag.getUUID("HuntHelperOwner");
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    public TianyiInventoryContainer getCompanionInventory() {
        return companionInventory;
    }
}
