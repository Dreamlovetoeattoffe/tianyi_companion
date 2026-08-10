package dev.dpon.tianyi.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Screen to configure Tianyi's LLM API endpoint, key, model and personality. */
public final class TianyiConfigScreen extends Screen {
    private static final int BOX_WIDTH = 300;
    private static final int BOX_HEIGHT = 20;
    private static final int ROW_HEIGHT = 52;

    private final TianyiConfig cfg;
    private final List<EditBox> boxes = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private String status = "";
    private int statusY = 0;

    public TianyiConfigScreen() {
        super(Component.translatable("title.tianyi_companion.config"));
        cfg = TianyiConfig.load();
    }

    @Override
    protected void init() {
        int left = width / 2 - BOX_WIDTH / 2;
        int y = 28;
        boxes.clear();
        labels.clear();
        y = addRow(left, y, "message.tianyi_companion.cfg_url", cfg.apiUrl);
        y = addRow(left, y, "message.tianyi_companion.cfg_key", cfg.apiKey);
        y = addRow(left, y, "message.tianyi_companion.cfg_model", cfg.model);
        y = addRow(left, y + 10, "message.tianyi_companion.cfg_persona", cfg.personality);

        int buttonY = y + 10;
        addRenderableWidget(Button.builder(Component.translatable("message.tianyi_companion.cfg_save"), b -> save())
                .bounds(left, buttonY, BOX_WIDTH / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("message.tianyi_companion.cfg_back"), b -> onClose())
                .bounds(left + BOX_WIDTH / 2 + 4, buttonY, BOX_WIDTH / 2 - 4, 20).build());
        statusY = buttonY + 28;
    }

    private int addRow(int left, int y, String labelKey, String value) {
        labels.add(labelKey);
        EditBox box = new EditBox(font, left, y + 16, BOX_WIDTH, BOX_HEIGHT, Component.translatable(labelKey));
        box.setMaxLength(Integer.MAX_VALUE);
        box.setValue(value == null ? "" : value);
        boxes.add(box);
        addRenderableWidget(box);
        return y + ROW_HEIGHT;
    }

    private void save() {
        cfg.apiUrl = boxes.get(0).getValue();
        cfg.apiKey = boxes.get(1).getValue();
        cfg.model = boxes.get(2).getValue();
        cfg.personality = boxes.get(3).getValue();
        cfg.save();
        status = ChatFormatting.GREEN + ChatFormatting.BOLD.toString() + "已保存！";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw a fully opaque backdrop first (no translucent veil), then widgets,
        // then the top-most text labels so nothing can cover them.
        graphics.fill(0, 0, width, height, 0xFF202020);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, this.title, width / 2, 6, 0xFFFFFF);
        int left = width / 2 - BOX_WIDTH / 2;
        for (int i = 0; i < boxes.size(); i++) {
            graphics.drawString(font, Component.translatable(labels.get(i)), left, boxes.get(i).getY() - 12, 0xFFFFFF);
        }
        if (!status.isEmpty() && statusY > 0) {
            graphics.drawCenteredString(font, status, width / 2, statusY, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}