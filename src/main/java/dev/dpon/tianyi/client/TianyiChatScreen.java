package dev.dpon.tianyi.client;

import dev.dpon.tianyi.network.TianyiBuildPayload;
import dev.dpon.tianyi.network.TianyiTalkPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Dialogue screen to talk with Tianyi using the configured LLM API. */
public final class TianyiChatScreen extends Screen {
    private final TianyiConfig config;
    private TianyiChatHistory history;
    private final List<TianyiApi.Message> conversation = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();
    private EditBox input;
    private Button sendButton;
    private boolean busy = false;
    private int scrollOffset = 0;
    private int lastLogSize = -1;
    private final List<FormattedCharSequence> wrappedLines = new ArrayList<>();

    public TianyiChatScreen() {
        super(Component.translatable("title.tianyi_companion.chat"));
        config = TianyiConfig.load();
    }

    @Override
    protected void init() {
        lastLogSize = -1;
        int inputY = height - 34;
        input = new EditBox(font, width / 2 - 156, inputY, 300, 20,
                Component.translatable("message.tianyi_companion.chat_input"));
        input.setMaxLength(2000);
        addRenderableWidget(input);
        setInitialFocus(input);

        sendButton = Button.builder(Component.translatable("message.tianyi_companion.chat_send"), b -> sendMessage())
                .bounds(width / 2 + 150, inputY - 2, 70, 20)
                .build();
        addRenderableWidget(sendButton);

        // Load persistent history on open (first time this screen is built for this session).
        if (history == null) {
            String playerId = minecraft != null && minecraft.player != null
                    ? minecraft.player.getStringUUID() : null;
            history = playerId != null ? TianyiChatHistory.load(playerId) : new TianyiChatHistory(new ArrayList<>());
            conversation.clear();
            logLines.clear();
            for (TianyiApi.Message m : history.messages()) {
                conversation.add(m);
                if ("user".equals(m.role())) {
                    logLines.add(ChatFormatting.WHITE + "主人: " + m.content());
                } else if ("assistant".equals(m.role())) {
                    logLines.add(ChatFormatting.AQUA + "天依: " + m.content());
                }
            }
            if (logLines.isEmpty()) {
                if (!config.isConfigured()) {
                    logLines.add(ChatFormatting.RED + "（请先在天依背包右侧的“AI配置”里填入 API 地址与密钥）");
                }
            }
        }
    }

    private void sendMessage() {
        if (busy) return;
        String text = input.getValue();
        if (text == null || text.isBlank()) return;
        input.setValue("");
        logLines.add(ChatFormatting.WHITE + "主人: " + text);
        TianyiApi.Message userMsg = new TianyiApi.Message("user", text);
        conversation.add(userMsg);
        history.add(userMsg);
        saveHistory();
        if (!config.isConfigured()) {
            logLines.add(ChatFormatting.RED + "（未配置 API）");
            return;
        }
        busy = true;
        sendButton.active = false;
        logLines.add(ChatFormatting.GRAY + ChatFormatting.ITALIC.toString() + "天依正在思考…");
        List<TianyiApi.Tool> tools = List.of(new TianyiApi.Tool("build",
                "在主人看着的地皮上盖一栋房子。传入一个 ops 建造指令数组，坐标是相对主人看中的格子的偏移。",
                TianyiBuildContext.buildToolSchema()));
        List<TianyiApi.Message> extraSystem = List.of(new TianyiApi.Message("system", TianyiBuildContext.systemNote()));
        TianyiApi.send(config, conversation, text, extraSystem, tools, new TianyiApi.Callback() {
            @Override
            public void onResponse(String content) {
                TianyiApi.Message reply = new TianyiApi.Message("assistant", content.trim());
                if (reply.content().isEmpty()) reply = new TianyiApi.Message("assistant", "…");
                conversation.add(reply);
                history.add(reply);
                saveHistory();
                logLines.add(ChatFormatting.AQUA + "天依: " + reply.content());
                busy = false;
                sendButton.active = true;
            }

            @Override
            public void onToolCall(List<TianyiApi.ToolCall> calls) {
                boolean handled = false;
                for (TianyiApi.ToolCall call : calls) {
                    if (!"build".equals(call.name())) continue;
                    handled = true;
                    BlockPos anchor = TianyiBuildContext.findAnchor();
                    if (anchor == null) {
                        logLines.add(ChatFormatting.RED + "（请先看着一块地皮，天依才能知道盖在哪里）");
                        continue;
                    }
                    PacketDistributor.sendToServer(new TianyiBuildPayload(anchor, call.arguments().toString()));
                }
                String replyText = handled ? "好的，我这就帮主人盖房子" : "…";
                TianyiApi.Message reply = new TianyiApi.Message("assistant", replyText);
                conversation.add(reply);
                history.add(reply);
                saveHistory();
                logLines.add(ChatFormatting.AQUA + "天依: " + replyText);
                busy = false;
                sendButton.active = true;
            }

            @Override
            public void onFailure(String error) {
                logLines.add(ChatFormatting.RED + "（出错了: " + error + "）");
                busy = false;
                sendButton.active = true;
            }
        });
    }

    private void saveHistory() {
        if (minecraft == null || minecraft.player == null) return;
        history.save(minecraft.player.getStringUUID());
    }

    @Override
    public void onClose() {
        sendTalk(false);
        super.onClose();
    }

    @Override
    public void removed() {
        sendTalk(false);
        super.removed();
    }

    private void sendTalk(boolean start) {
        PacketDistributor.sendToServer(new TianyiTalkPayload(start));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && input.isFocused()) {
            sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset += (int) (verticalAmount * 20);
        scrollOffset = clampScroll();
        return true;
    }

    private int clampScroll() {
        int available = height - 70;
        return Math.max(0, Math.min(scrollOffset, Math.max(0, totalContentHeight() - available)));
    }

    private void rebuildWrappedLines() {
        wrappedLines.clear();
        for (String line : logLines) {
            wrappedLines.addAll(font.split(styled(line), width - 60));
        }
    }

    private static Component styled(String text) {
        MutableComponent root = Component.empty();
        StringBuilder current = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = text.charAt(++i);
                if (!current.isEmpty()) {
                    root.append(Component.literal(current.toString()).withStyle(style));
                    current.setLength(0);
                }
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (fmt == ChatFormatting.RESET) {
                        style = Style.EMPTY;
                    } else if (fmt.isColor()) {
                        style = style.withColor(TextColor.fromLegacyFormat(fmt));
                    } else {
                        style = style.applyLegacyFormat(fmt);
                    }
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            root.append(Component.literal(current.toString()).withStyle(style));
        }
        return root;
    }

    private int totalContentHeight() {
        return wrappedLines.size() * (font.lineHeight + 6);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (lastLogSize != logLines.size()) {
            lastLogSize = logLines.size();
            scrollOffset = 0;
            rebuildWrappedLines();
        }
        int start = clampScroll();
        int y = 30 - start;
        int bottom = height - 40;
        int lineH = font.lineHeight + 6;
        graphics.enableScissor(0, 26, width, bottom - 26);
        for (FormattedCharSequence seq : wrappedLines) {
            if (y + lineH > 26 && y < bottom) {
                graphics.drawString(font, seq, 30, y, 0xFFFFFF);
            }
            y += lineH;
            if (y > bottom) break;
        }
        graphics.disableScissor();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
