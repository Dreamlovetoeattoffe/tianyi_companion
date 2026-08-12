package dev.dpon.tianyi.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 音之精灵·天钿: the accessory worn in Tianyi's offhand-like accessory slot. */
public final class SpiritTianItem extends Item {
    public SpiritTianItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.spirit_tian.equip"));
        tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.spirit_tian.stack"));
        tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.spirit_tian.peace"));
    }
}