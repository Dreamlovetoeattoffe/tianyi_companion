package dev.dpon.tianyi.item;

import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.entity.TianyiEntity;
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
 * One of the 天钿 cores. Only the 可食用核心 ({@link Grade#EDIBLE}) and the
 * final 天钿 ({@link Grade#TIAN_DIAN}) are food.
 * <ul>
 *   <li>{@link Grade#EDIBLE}: a plain food whose nutrition is the sum of the
 *       recipe's ingredients; feeding it to Tianyi grants a flat +50 affinity.</li>
 *   <li>{@link Grade#TIAN_DIAN}: feeding grants 10000..20000 random affinity
 *       (exactly 12712 makes her hand over the 音之精灵·天钿 accessory), and a
 *       player eating one gains infinite saturation but forces her affinity to
 *       -100, dropping her into hate mode where she snatches the owner's weapon.</li>
 * </ul>
 */
public final class TianCoreItem extends Item {
    public enum Grade {
        CORE,
        EDIBLE,
        TIAN_DIAN
    }

    private final Grade grade;

    public TianCoreItem(Grade grade, Properties properties) {
        super(properties);
        this.grade = grade;
    }

    public Grade getGrade() {
        return grade;
    }

    public boolean isEdible() {
        return grade == Grade.EDIBLE || grade == Grade.TIAN_DIAN;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        if (grade == Grade.EDIBLE) {
            tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.tian_core_edible"));
        } else if (grade == Grade.TIAN_DIAN) {
            tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.tian_core.feed"));
            tooltipComponents.add(Component.translatable("tooltip.tianyi_companion.tian_core.eat"));
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (grade != Grade.TIAN_DIAN) return result;
        if (level.isClientSide || !(livingEntity instanceof ServerPlayer player)) return result;
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, MobEffectInstance.INFINITE_DURATION, 0));
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi != null) {
            tianyi.setAffinity(TianyiEntity.HATE_THRESHOLD);
        }
        TianyiCompanionMod.award(player, "tian_eaten");
        player.displayClientMessage(Component.translatable("message.tianyi_companion.tian_eaten"), false);
        return result;
    }
}