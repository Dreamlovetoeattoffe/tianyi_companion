package dev.dpon.tianyi.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = TianyiCompanionMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class TianyiChatInputHandler {
    private TianyiChatInputHandler() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;

        if (event.getKey() != ClientModEvents.CHAT_KEY.getKey().getValue()) return;
        if (!canOpenChat(mc)) {
            mc.player.displayClientMessage(
                    Component.translatable("message.tianyi_companion.look_to_chat"), true);
            return;
        }
        mc.setScreen(new TianyiChatScreen());
    }

    /** Chat opens when looking at your Tianyi, or when she is nearby (lets you aim at a build site). */
    private static boolean canOpenChat(Minecraft mc) {
        Player player = mc.player;
        if (player == null) return false;
        if (lookingAtOwnedTianyi(mc)) return true;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof TianyiEntity t && t.isOwnedBy(player) && player.distanceToSqr(t) < 144.0D) {
                return true;
            }
        }
        return false;
    }

    /** True when the player's crosshair points at their own Tianyi within 5 blocks. */
    private static boolean lookingAtOwnedTianyi(Minecraft mc) {
        Player player = mc.player;
        if (player == null) return false;
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 end = eye.add(look.x * 5.0D, look.y * 5.0D, look.z * 5.0D);
        AABB box = player.getBoundingBox().expandTowards(look.scale(5.0D)).inflate(1.0D);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, box, e -> e instanceof TianyiEntity t && t.isOwnedBy(player), 25.0F);
        return hit != null;
    }
}
