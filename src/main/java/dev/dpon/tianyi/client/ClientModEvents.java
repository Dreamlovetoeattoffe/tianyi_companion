package dev.dpon.tianyi.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dpon.tianyi.TianyiCompanionMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = TianyiCompanionMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    public static final KeyMapping CHAT_KEY = new KeyMapping(
            "key.tianyi_companion.chat",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.tianyi_companion");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TianyiCompanionMod.TIANYI.get(), TianyiRenderer::new);
        event.registerEntityRenderer(TianyiCompanionMod.NOTE_PROJECTILE.get(), EmptyNoteRenderer::new);
        event.registerEntityRenderer(TianyiCompanionMod.TIANYI_GRAVE.get(), TianyiGraveRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CHAT_KEY);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TianyiModel.LAYER, TianyiModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(TianyiCompanionMod.TIANYI_MENU.get(), TianyiScreen::new);
    }
}
