package dev.dpon.tianyi.item;

import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.entity.TianyiEntity;
import dev.dpon.tianyi.entity.TianyiHuntManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * One of the nine 天钿 cores. The writable ones ({@link #isEdible()}) are food:
 * feeding them to Tianyi grants 10000..20000 random affinity (exactly 12712
 * makes her hand over the 音之精灵·天钿 accessory), and a player eating one
 * gains infinite saturation but forces their Tianyi to -100 affinity and
 * directly starts a global hunt against them.
 */
public final class TianCoreItem extends Item {
    private final boolean edible;

    public TianCoreItem(boolean edible, Properties properties) {
        super(properties);
        this.edible = edible;
    }

    public boolean isEdible() {
        return edible;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        if (edible) {
            tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.tian_core.feed"));
            tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.tian_core.eat"));
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (level.isClientSide || !(livingEntity instanceof ServerPlayer player)) return result;
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, MobEffectInstance.INFINITE_DURATION, 0));
        player.getPersistentData().putBoolean(TianyiCompanionMod.PLAYER_FORCED_HUNT_KEY, true);
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi != null) {
            tianyi.setAffinity(TianyiEntity.HATE_THRESHOLD);
            TianyiHuntManager.startHunt(player.getUUID(), tianyi.getHuntWeapon());
            TianyiHuntManager.ensureHuntGroup(player, player.serverLevel());
        } else {
            TianyiHuntManager.startHunt(player.getUUID(), ItemStack.EMPTY);
            TianyiHuntManager.ensureHuntGroup(player, player.serverLevel());
        }
        player.displayClientMessage(Component.translatable("message.tianyi_companion.tian_eaten"), false);
        return result;
    }
}