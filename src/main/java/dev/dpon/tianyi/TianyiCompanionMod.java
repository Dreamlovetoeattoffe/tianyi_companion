package dev.dpon.tianyi;

import dev.dpon.tianyi.entity.NoteProjectile;
import dev.dpon.tianyi.entity.TianyiEntity;
import dev.dpon.tianyi.entity.TianyiGraveEntity;
import dev.dpon.tianyi.item.CompanionSummonItem;
import dev.dpon.tianyi.menu.TianyiMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

@Mod(TianyiCompanionMod.MOD_ID)
public final class TianyiCompanionMod {
    public static final String MOD_ID = "tianyi_companion";
    public static final String OWNER_ENTITY_KEY = "TianyiCompanionEntity";
    /** Times the player died to their hunting Tianyi; at the cap she stops and is dismissed. */
    public static final String PLAYER_HUNT_DEATHS_KEY = "TianyiHuntDeaths";
    /** Set when the hunt banishes Tianyi; summoning stays blocked until xiaolongbao are paid. */
    public static final String PLAYER_SUMMON_BAN_KEY = "TianyiSummonBanned";
    /** One-shot flag to tell the player on respawn that their Tianyi left them. */
    public static final String PLAYER_BANISH_NOTICE_KEY = "TianyiBanishNotice";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TianyiEntity>> TIANYI = ENTITIES.register(
            "tianyi", () -> EntityType.Builder.of(TianyiEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build(id("tianyi").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<NoteProjectile>> NOTE_PROJECTILE = ENTITIES.register(
            "note_projectile", () -> EntityType.Builder.<NoteProjectile>of(NoteProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(2)
                    .build(id("note_projectile").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<TianyiGraveEntity>> TIANYI_GRAVE = ENTITIES.register(
            "tianyi_grave", () -> EntityType.Builder.<TianyiGraveEntity>of(TianyiGraveEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.15F).clientTrackingRange(8).build(id("tianyi_grave").toString()));
    public static final DeferredHolder<MenuType<?>, MenuType<TianyiMenu>> TIANYI_MENU = MENUS.register(
            "tianyi_menu", () -> IMenuTypeExtension.create(TianyiMenu::new));

    public static final DeferredItem<Item> RAW_XIAOLONGBAO = ITEMS.register("raw_xiaolongbao", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> XIAOLONGBAO = ITEMS.register("xiaolongbao", () -> new Item(
            new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build())));
    public static final DeferredItem<Item> SUMMON_CHARM = ITEMS.register("summon_charm", () ->
            new CompanionSummonItem(false, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TIANYI_HEART = ITEMS.register("tianyi_heart", () ->
            new Item(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<Item> REBIRTH_CHARM = ITEMS.register("rebirth_charm", () ->
            new CompanionSummonItem(true, new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.tianyi_companion"))
                    .icon(() -> SUMMON_CHARM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(XIAOLONGBAO.get());
                        output.accept(RAW_XIAOLONGBAO.get());
                        output.accept(SUMMON_CHARM.get());
                        output.accept(TIANYI_HEART.get());
                        output.accept(REBIRTH_CHARM.get());
                    }).build());

    public TianyiCompanionMod(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(this::createAttributes);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onPlayerDeath);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onPlayerWakeUp);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(TianyiCommands::register);
    }

    private void createAttributes(EntityAttributeCreationEvent event) {
        event.put(TIANYI.get(), TianyiEntity.createAttributes().build());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void award(ServerPlayer player, String advancementId) {
        var advancement = player.server.getAdvancements().get(id(advancementId));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "done");
        }
    }

    /** Finds the player's own alive Tianyi, refreshing the cached UUID when found. */
    public static TianyiEntity findOwnedTianyi(ServerPlayer player) {
        if (player.getPersistentData().hasUUID(OWNER_ENTITY_KEY)) {
            UUID cached = player.getPersistentData().getUUID(OWNER_ENTITY_KEY);
            for (ServerLevel level : player.server.getAllLevels()) {
                if (level.getEntity(cached) instanceof TianyiEntity tianyi) return tianyi;
            }
        }
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof TianyiEntity tianyi
                        && player.getUUID().equals(tianyi.getOwnerUUID()) && tianyi.isAlive()) {
                    player.getPersistentData().putUUID(OWNER_ENTITY_KEY, tianyi.getUUID());
                    return tianyi;
                }
            }
        }
        player.getPersistentData().remove(OWNER_ENTITY_KEY);
        return null;
    }

    /** Counts every stack of the item across the player's whole inventory. */
    public static int countItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    /** Removes {@code amount} of the item from the player's inventory. Returns
     *  false (without removing anything) if the player has fewer than that. */
    public static boolean consumeItems(ServerPlayer player, Item item, int amount) {
        if (countItems(player, item) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
        }
        return true;
    }
}
