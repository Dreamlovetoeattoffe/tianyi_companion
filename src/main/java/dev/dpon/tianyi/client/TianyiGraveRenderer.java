package dev.dpon.tianyi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.dpon.tianyi.entity.TianyiGraveEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

public final class TianyiGraveRenderer extends EntityRenderer<TianyiGraveEntity> {
    // The grave is a centered 20 x 8 x 28 voxel model inside a 32 voxel block:
    // width x thickness x height = 0.625 x 0.25 x 0.875 blocks.
    private static final float WIDTH = 20.0F / 32.0F;
    private static final float THICKNESS = 8.0F / 32.0F;
    private static final float HEIGHT = 28.0F / 32.0F;
    private final BlockRenderDispatcher blocks;

    public TianyiGraveRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(TianyiGraveEntity entity, float yaw, float partialTick, PoseStack poseStack,
        MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        // Entity positions are at the block center, so offset the scaled
        // cuboid by half its width and thickness to keep it centered.
        poseStack.translate(-WIDTH / 2.0F, 0.0F, -THICKNESS / 2.0F);
        poseStack.scale(WIDTH, HEIGHT, THICKNESS);
        blocks.renderSingleBlock(Blocks.POLISHED_DEEPSLATE.defaultBlockState(), poseStack, buffers,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TianyiGraveEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
