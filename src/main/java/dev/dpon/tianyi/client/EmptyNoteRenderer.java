package dev.dpon.tianyi.client;

import dev.dpon.tianyi.entity.NoteProjectile;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public final class EmptyNoteRenderer extends EntityRenderer<NoteProjectile> {
    public EmptyNoteRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(NoteProjectile entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
