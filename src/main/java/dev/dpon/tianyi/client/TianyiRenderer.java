package dev.dpon.tianyi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dpon.tianyi.TianyiCompanionMod;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class TianyiRenderer extends MobRenderer<TianyiEntity, TianyiModel> {
    private static final ResourceLocation[] TEXTURES = {
            TianyiCompanionMod.id("textures/entity/tianyi.png"),
            TianyiCompanionMod.id("textures/entity/skins/tianyi_original.png"),
            TianyiCompanionMod.id("textures/entity/skins/tianyi_v4formula.png"),
            TianyiCompanionMod.id("textures/entity/skins/tianyi_chef.png"),
            TianyiCompanionMod.id("textures/entity/skins/tianyi_summer.png"),
            TianyiCompanionMod.id("textures/entity/skins/tianyi_variant.png"),
    };

    public TianyiRenderer(EntityRendererProvider.Context context) {
        super(context, new TianyiModel(context.bakeLayer(TianyiModel.LAYER)), 0.45F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    protected void scale(TianyiEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.93F, 0.93F, 0.93F);
    }

    @Override
    public ResourceLocation getTextureLocation(TianyiEntity entity) {
        int index = Math.max(0, Math.min(entity.getSkinIndex(), TEXTURES.length - 1));
        return TEXTURES[index];
    }
}
