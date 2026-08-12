package dev.dpon.tianyi.menu;

import com.mojang.datafixers.util.Pair;
import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.entity.TianyiEntity;
import dev.dpon.tianyi.entity.TianyiInventoryContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class TianyiMenu extends AbstractContainerMenu {
    private final TianyiEntity tianyi;
    private final TianyiInventoryContainer companionInventory;

    public TianyiMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, resolveEntity(playerInventory, data.readVarInt()));
    }

    public TianyiMenu(int containerId, Inventory playerInventory, TianyiEntity tianyi) {
        super(TianyiCompanionMod.TIANYI_MENU.get(), containerId);
        this.tianyi = tianyi;
        this.companionInventory = tianyi.getCompanionInventory();

        for (int i = 0; i < 27; i++) {
            int column = i % 9;
            int row = i / 9;
            // Vanilla player inventory coordinates: the 3x9 main grid sits below
            // the armor/crafting area, at y=84.
            addSlot(new Slot(companionInventory, i, 8 + column * 18, 84 + row * 18));
        }
        addSlot(new ArmorContainerSlot(companionInventory, 27, 8, 8, EquipmentSlot.HEAD, tianyi));
        addSlot(new ArmorContainerSlot(companionInventory, 28, 8, 26, EquipmentSlot.CHEST, tianyi));
        addSlot(new ArmorContainerSlot(companionInventory, 29, 8, 44, EquipmentSlot.LEGS, tianyi));
        addSlot(new ArmorContainerSlot(companionInventory, 30, 8, 62, EquipmentSlot.FEET, tianyi));
        int accessory = TianyiEntity.ACCESSORY_SLOT;
        addSlot(new AccessoryContainerSlot(companionInventory, accessory, 77, 44, tianyi));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 168 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 226));
        }
    }

    private static TianyiEntity resolveEntity(Inventory inventory, int entityId) {
        if (inventory.player.level().getEntity(entityId) instanceof TianyiEntity tianyi) return tianyi;
        return null;
    }

    public TianyiEntity getTianyi() {
        return tianyi;
    }

    public static Component title() {
        return Component.translatable("container.tianyi_companion.tianyi");
    }

    @Override
    public boolean stillValid(Player player) {
        return tianyi != null && tianyi.isAlive() && tianyi.isOwnedBy(player)
                && tianyi.distanceToSqr(player) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        if (index < 0 || index >= slots.size()) return empty;
        Slot source = slots.get(index);
        if (!source.hasItem()) return empty;
        ItemStack original = source.getItem();
        ItemStack moved = original.copy();

        if (index < 32) {
            if (!moveItemStackTo(original, 32, slots.size(), true)) return empty;
        } else {
            boolean movedToArmor = moveItemStackTo(original, 27, 32, false);
            if (!movedToArmor) movedToArmor = moveItemStackTo(original, 0, 27, false);
            if (!movedToArmor) return empty;
        }
        if (original.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
        else source.setChanged();
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        companionInventory.setChanged();
    }

    private static final class ArmorContainerSlot extends Slot {
        private final EquipmentSlot equipmentSlot;
        private final TianyiEntity owner;

        private ArmorContainerSlot(TianyiInventoryContainer container, int index, int x, int y,
                                   EquipmentSlot equipmentSlot, TianyiEntity owner) {
            super(container, index, x, y);
            this.equipmentSlot = equipmentSlot;
            this.owner = owner;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.canEquip(equipmentSlot, owner);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
            net.minecraft.resources.ResourceLocation icon = switch (equipmentSlot) {
                case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                default -> null;
            };
            return icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }
    }

    /** Offhand-like slot that only accepts the 音之精灵·天钿 accessory. */
    private static final class AccessoryContainerSlot extends Slot {
        private final TianyiEntity owner;

        private AccessoryContainerSlot(TianyiInventoryContainer container, int index, int x, int y,
                                       TianyiEntity owner) {
            super(container, index, x, y);
            this.owner = owner;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(TianyiCompanionMod.SPIRIT_TIAN.get());
        }

        @Override
        public int getMaxStackSize() {
            return 4;
        }

        @Override
        public Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        }
    }
}
