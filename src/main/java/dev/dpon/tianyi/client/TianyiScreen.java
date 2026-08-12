package dev.dpon.tianyi.client;

import dev.dpon.tianyi.menu.TianyiMenu;
import dev.dpon.tianyi.entity.TianyiEntity;
import dev.dpon.tianyi.network.SkinUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TianyiScreen extends AbstractContainerScreen<TianyiMenu> {
    private static final int COMPANION_HEIGHT = 150;
    private static final ResourceLocation INVENTORY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int SKIN_COUNT = TianyiEntity.MAX_SKIN_INDEX + 1;

    private Button skinButton;
    private Button configButton;

    public TianyiScreen(TianyiMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 244;
    }

    @Override
    protected void init() {
        super.init();
        skinButton = Button.builder(Component.empty(), button -> cycleSkin())
                .bounds(leftPos - 62, topPos + 34, 58, 20)
                .tooltip(Tooltip.create(Component.translatable("message.tianyi_companion.skin_tooltip")))
                .build();
        addRenderableWidget(skinButton);
        refreshSkinLabel();

        configButton = Button.builder(
                        Component.translatable("message.tianyi_companion.ai_config"),
                        button -> Minecraft.getInstance().setScreen(new TianyiConfigScreen()))
                .bounds(leftPos + imageWidth + 6, topPos + 34, 58, 20)
                .tooltip(Tooltip.create(Component.translatable("message.tianyi_companion.ai_config_tooltip")))
                .build();
        addRenderableWidget(configButton);
    }

    private void cycleSkin() {
        if (menu.getTianyi() == null) return;
        int next = (menu.getTianyi().getSkinIndex() + 1) % SKIN_COUNT;
        PacketDistributor.sendToServer(new SkinUpdatePayload(menu.getTianyi().getId(), next));
        refreshSkinLabel();
    }

    private void refreshSkinLabel() {
        if (skinButton == null || menu.getTianyi() == null) return;
        skinButton.setMessage(TianyiEntity.getSkinName(menu.getTianyi().getSkinIndex()));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshSkinLabel();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Tianyi's compact section is custom-drawn so it contains no crafting
        // grid, offhand slot, or Tianyi hotbar.
        graphics.fill(x, y, x + imageWidth, y + COMPANION_HEIGHT, 0xFFC6C6C6);
        drawBorder(graphics, x, y, imageWidth, COMPANION_HEIGHT);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, x + 8 + column * 18, y + 84 + row * 18);
            }
        }
        for (int row = 0; row < 4; row++) {
            drawSlot(graphics, x + 8, y + 8 + row * 18);
        }
        // Offhand-like accessory slot for the 音之精灵·天钿.
        drawSlot(graphics, x + 77, y + 44);
        // Reserve the original character-preview area immediately to the
        // right of the armor slots and render Tianyi during the background
        // phase, matching vanilla InventoryScreen's render order.
        int previewLeft = x + 26;
        int previewTop = y + 8;
        graphics.fill(previewLeft - 2, previewTop - 2, x + 77, y + 80, 0xFF303030);
        graphics.fill(previewLeft, previewTop, x + 75, y + 78, 0xFF000000);
        if (menu.getTianyi() != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, previewLeft, previewTop, x + 75, y + 78,
                    30, 0.0625F, mouseX, mouseY, menu.getTianyi());
        }
        // Keep the player's own inventory available for item transfer below it.
        graphics.fill(x, y + COMPANION_HEIGHT, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        // Reuse the lower part of the vanilla inventory texture for the
        // player's own 3x9 inventory and hotbar. The menu slot coordinates
        // match the source texture (main grid at y=84, hotbar at y=142).
        graphics.blit(INVENTORY_TEXTURE, x, y + 167, 0, 83, imageWidth, 83, 256, 256);
        graphics.fill(x, y + COMPANION_HEIGHT, x + imageWidth, y + COMPANION_HEIGHT + 2, 0xFF555555);
        graphics.fill(x, y + COMPANION_HEIGHT, x + 2, y + imageHeight, 0xFF555555);
        graphics.fill(x + imageWidth - 2, y + COMPANION_HEIGHT, x + imageWidth, y + imageHeight, 0xFF555555);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Do not draw overlapping custom labels: the vanilla slot layout already
        // identifies the sections visually and this prevents mojibake-like overlap.
        graphics.drawString(font, title, 86, 72, 0x404040, false);
        if (menu.getTianyi() != null) {
            int currentHealth = Math.round(menu.getTianyi().getHealth());
            int maxHealth = Math.round(menu.getTianyi().getMaxHealth());
            graphics.drawString(font, Component.literal("生命值："), 86, 6, 0x404040, false);
            graphics.drawString(font, Component.literal(currentHealth + "/" + maxHealth), 86, 18, 0x404040, false);
            graphics.drawString(font, Component.literal("好感度："), 86, 30, 0x404040, false);
            graphics.drawString(font, Component.literal(menu.getTianyi().getAffinity()
                    + "/" + TianyiEntity.MAX_AFFINITY), 86, 42, 0x404040, false);
            int stacks = menu.getTianyi().getCompanionInventory().getItem(TianyiEntity.ACCESSORY_SLOT).getCount();
            boolean hasAccessory = stacks > 0;
            // Note and melee damage always shown.
            graphics.drawString(font, Component.literal("音符伤害:" + menu.getTianyi().getNoteDamage()),
                    86, 54, 0x404040, false);
            // The accessory +50 attack modifier is server-only and doesn't sync
            // to the client attribute; add 50 manually for the display.
            double atkDmg = menu.getTianyi().getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (hasAccessory) atkDmg += 50.0D;
            graphics.drawString(font, Component.literal("近战伤害:" + String.format("%.1f", atkDmg)),
                    86, 66, 0x404040, false);
            // Potion effects only shown when the accessory is equipped.
            if (hasAccessory) {
                int yOff = 84;
                var boost = menu.getTianyi().getEffect(MobEffects.DAMAGE_BOOST);
                if (boost != null) {
                    graphics.drawString(font, Component.literal("力量 " + (boost.getAmplifier() + 1)),
                            86, yOff, 0x404040, false);
                    yOff += 12;
                }
                var speed = menu.getTianyi().getEffect(MobEffects.MOVEMENT_SPEED);
                if (speed != null) {
                    graphics.drawString(font, Component.literal("迅捷 " + (speed.getAmplifier() + 1)),
                            86, yOff, 0x404040, false);
                    yOff += 12;
                }
                var resist = menu.getTianyi().getEffect(MobEffects.DAMAGE_RESISTANCE);
                if (resist != null) {
                    graphics.drawString(font, Component.literal("抗性 " + (resist.getAmplifier() + 1)),
                            86, yOff, 0x404040, false);
                }
            }
        }
        graphics.drawString(font, playerInventoryTitle, 8, 156, 0x404040, false);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        graphics.fill(x, y, x + 16, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + 16, 0xFF373737);
        graphics.fill(x + 1, y + 15, x + 16, y + 16, 0xFFFFFFFF);
        graphics.fill(x + 15, y + 1, x + 16, y + 15, 0xFFFFFFFF);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + 2, 0xFF555555);
        graphics.fill(x, y, x + 2, y + height, 0xFF555555);
        graphics.fill(x + width - 2, y, x + width, y + height, 0xFF555555);
        graphics.fill(x, y + height - 2, x + width, y + height, 0xFF555555);
    }

}
