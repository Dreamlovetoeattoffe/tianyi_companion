package dev.dpon.tianyi.entity;

import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.build.TianyiBuildEngine;
import dev.dpon.tianyi.menu.TianyiMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class TianyiEntity extends TamableAnimal implements RangedAttackMob {
    public static final int MAX_AFFINITY = 712_712;
    /** Number of skin variants: 0 = default, 1..5 = extra. */
    public static final int MAX_SKIN_INDEX = 5;
    private static final String[] SKIN_KEYS = {
            "skin.tianyi_companion.default",
            "skin.tianyi_companion.original",
            "skin.tianyi_companion.v4formula",
            "skin.tianyi_companion.chef",
            "skin.tianyi_companion.summer",
            "skin.tianyi_companion.variant"
    };
    private static final int RECENT_FOOD_LIMIT = 100;
    /** Nights the owner slept near this Tianyi; 5 unlocks the house-building skill. */
    public static final int REQUIRED_SHARED_NIGHTS = 5;
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
    private final Deque<String> recentFoods = new ArrayDeque<>();
    private int careCooldown;
    private int sharedNights;
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

    public TianyiEntity(EntityType<? extends TianyiEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, 4.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.05D, 30, 16.0F));
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
        entityData.set(AFFINITY, Math.max(0, Math.min(MAX_AFFINITY, value)));
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
        double maxHealth = 20.0D + getAffinity() / 100;
        if (Math.abs(getAttributeValue(Attributes.MAX_HEALTH) - maxHealth) > 0.001D) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
            if (getHealth() > maxHealth) setHealth((float) maxHealth);
        }
    }

    /** Returns the current note projectile damage, including all affinity growth. */
    public int getNoteDamage() {
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
        if (level().isClientSide || getAffinity() < 200) return;
        NoteProjectile note = new NoteProjectile(level(), this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.5D) - note.getY();
        double dz = target.getZ() - getZ();
        note.shoot(dx, dy, dz, 1.35F, 1.5F);
        level().addFreshEntity(note);
        level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.NEUTRAL, 0.8F, 1.4F);
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
                String foodId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                int recentCount = countRecentFood(foodId);
                int affection = calculateFoodAffection(held, foodProperties, recentCount);
                recordRecentFood(foodId);
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
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (held.isEmpty()) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
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
     * Calculates the affinity gain from a food using its nutrition value and
     * the number of times that exact item was recently fed to this entity.
     * The current food is not included in {@code recentCount} yet.
     */
    private int calculateFoodAffection(ItemStack stack, FoodProperties foodProperties, int recentCount) {
        // Xiaolongbao intentionally has a custom affinity base: its nutrition
        // is 8, while its first-feed affinity value A is 12.
        int baseAffection = stack.is(TianyiCompanionMod.XIAOLONGBAO)
                ? 12 : foodProperties.nutrition();
        int modifierTenths = Math.max(0, (int) Math.ceil(10.0D - recentCount / 7.0D));
        // Affinity is stored as an integer, so fractional results are truncated.
        return baseAffection * modifierTenths / 10;
    }

    private int countRecentFood(String foodId) {
        int count = 0;
        for (String recentFood : recentFoods) {
            if (recentFood.equals(foodId)) count++;
        }
        return count;
    }

    private void recordRecentFood(String foodId) {
        recentFoods.addLast(foodId);
        while (recentFoods.size() > RECENT_FOOD_LIMIT) {
            recentFoods.removeFirst();
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.getFoodProperties(this) != null;
    }

    private String getAffinityTierKey() {
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
        if (hurt && !level().isClientSide && source.getEntity() instanceof ServerPlayer attacker) {
            changeAffinity(-5);
            TianyiCompanionMod.award(attacker, "hurt_tianyi");
            attacker.displayClientMessage(Component.translatable("message.tianyi_companion.affinity_lost"), true);
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
        super.die(source);
        if (!level().isClientSide && !wasDead && this.dead && !isRemoved()) {
            TianyiGraveEntity grave = new TianyiGraveEntity(level(), getOwnerUUID(), getAffinity());
            grave.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
            level().addFreshEntity(grave);
            if (getOwner() instanceof ServerPlayer owner) {
                owner.getPersistentData().remove(TianyiCompanionMod.OWNER_ENTITY_KEY);
                owner.displayClientMessage(Component.translatable("message.tianyi_companion.grave_placed"), false);
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
        ListTag recentFoodTag = new ListTag();
        for (String foodId : recentFoods) {
            recentFoodTag.add(net.minecraft.nbt.StringTag.valueOf(foodId));
        }
        tag.put("RecentFoods", recentFoodTag);
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
        recentFoods.clear();
        if (tag.contains("RecentFoods", 9)) {
            ListTag recentFoodTag = tag.getList("RecentFoods", 8);
            for (int i = 0; i < recentFoodTag.size(); i++) {
                recentFoods.addLast(recentFoodTag.getString(i));
            }
            while (recentFoods.size() > RECENT_FOOD_LIMIT) {
                recentFoods.removeFirst();
            }
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    public TianyiInventoryContainer getCompanionInventory() {
        return companionInventory;
    }
}
