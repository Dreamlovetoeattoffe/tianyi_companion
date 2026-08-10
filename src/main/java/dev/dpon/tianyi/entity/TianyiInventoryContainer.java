package dev.dpon.tianyi.entity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Tianyi's inventory: 27 main-inventory slots and four armor slots. */
public final class TianyiInventoryContainer extends SimpleContainer {
    private final TianyiEntity owner;

    public TianyiInventoryContainer(TianyiEntity owner) {
        // 27 main-inventory slots + 4 armor slots.
        super(31);
        this.owner = owner;
    }

    private EquipmentSlot equipmentSlot(int index) {
        return switch (index - 27) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public ItemStack getItem(int index) {
        return index < 27 ? super.getItem(index) : owner.getItemBySlot(equipmentSlot(index));
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 27) {
            super.setItem(index, stack);
        } else {
            owner.setItemSlot(equipmentSlot(index), stack);
            setChanged();
        }
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack current = getItem(index);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = current.split(count);
        if (current.isEmpty()) setItem(index, ItemStack.EMPTY);
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack current = getItem(index);
        setItem(index, ItemStack.EMPTY);
        return current;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    public ListTag saveInventory(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag item = (CompoundTag) stack.save(registries);
                item.putByte("Slot", (byte) i);
                list.add(item);
            }
        }
        return list;
    }

    public void loadInventory(ListTag list, HolderLookup.Provider registries) {
        clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            int slot = item.getByte("Slot") & 255;
            if (slot < getContainerSize()) {
                ItemStack.parse(registries, item).ifPresent(stack -> setItem(slot, stack));
            }
        }
    }
}
