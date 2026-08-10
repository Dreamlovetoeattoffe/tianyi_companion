package dev.dpon.tianyi.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Minimal OpenAI-compatible chat-completions client used for Tianyi's dialogue.
 * All requests run on a background thread; callbacks are marshalled back to the render thread.
 */
public final class TianyiApi {
    /** A single chat message: role is {@code system}/{@code user}/{@code assistant}. */
    public record Message(String role, String content) {}

    /** An OpenAI-style function tool offered to the model. */
    public record Tool(String name, String description, JsonObject parameters) {}

    /** A tool call requested by the model. */
    public record ToolCall(String id, String name, JsonObject arguments) {}

    public interface Callback {
        void onResponse(String content);

        void onFailure(String error);

        default void onToolCall(List<ToolCall> toolCalls) {
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private TianyiApi() {}

    public static void send(TianyiConfig config, List<Message> conversation, String userText, Callback callback) {
        send(config, conversation, userText, null, null, callback);
    }

    public static void send(TianyiConfig config, List<Message> conversation, String userText,
                            List<Message> extraSystem, List<Tool> tools, Callback callback) {
        if (!config.isConfigured()) {
            callback.onFailure("尚未配置");
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                List<Message> all = new ArrayList<>();
                all.add(new Message("system", config.personality));
                if (extraSystem != null) all.addAll(extraSystem);
                all.addAll(conversation);
                all.add(new Message("user", userText));

                JsonObject body = new JsonObject();
                body.addProperty("model", config.model.isEmpty() ? "gpt-4o-mini" : config.model);
                body.addProperty("temperature", config.temperature);
                JsonArray messages = new JsonArray();
                for (Message m : all) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("role", m.role());
                    obj.addProperty("content", m.content());
                    messages.add(obj);
                }
                body.add("messages", messages);
                if (tools != null && !tools.isEmpty()) {
                    JsonArray toolsArr = new JsonArray();
                    for (Tool t : tools) {
                        JsonObject fn = new JsonObject();
                        fn.addProperty("name", t.name());
                        fn.addProperty("description", t.description());
                        fn.add("parameters", t.parameters());
                        JsonObject entry = new JsonObject();
                        entry.addProperty("type", "function");
                        entry.add("function", fn);
                        toolsArr.add(entry);
                    }
                    body.add("tools", toolsArr);
                }

                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(resolveUrl(config.apiUrl)))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
                if (config.apiKey != null && !config.apiKey.isBlank()) {
                    builder.header("Authorization", "Bearer " + config.apiKey);
                }

                HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + "：" + snippet(response.body()));
                }
                Reply reply = extractReply(response.body());
                if (reply.toolCalls() != null && !reply.toolCalls().isEmpty()) {
                    Minecraft.getInstance().execute(() -> callback.onToolCall(reply.toolCalls()));
                } else {
                    Minecraft.getInstance().execute(() -> callback.onResponse(reply.content()));
                }
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                Minecraft.getInstance().execute(() -> callback.onFailure(msg));
            }
        });
    }

    private record Reply(String content, List<ToolCall> toolCalls) {}

    /** Appends the default chat/completions path if the configured URL looks like a base address. */
    private static String resolveUrl(String url) {
        if (url == null || url.isBlank()) return url;
        String u = url.trim();
        if (u.endsWith("/chat/completions") || u.endsWith("chat/completions")) return u;
        if (!u.endsWith("/")) u += "/";
        return u + "chat/completions";
    }

    private static Reply extractReply(String bodyText) {
        JsonElement root = JsonParser.parseString(bodyText); // throws if not JSON -> caught -> shown with body
        JsonObject object = root.isJsonObject() ? root.getAsJsonObject() : root.getAsJsonArray().get(0).getAsJsonObject();
        if (object.has("error")) {
            JsonElement err = object.get("error");
            String detail = err.isJsonObject() && err.getAsJsonObject().has("message")
                    ? err.getAsJsonObject().get("message").getAsString() : err.toString();
            throw new IllegalStateException("API error: " + detail + " || " + snippet(bodyText));
        }
        if (!object.has("choices") || !object.get("choices").isJsonArray()) {
            throw new IllegalStateException("返回中没有 choices： " + snippet(bodyText));
        }
        JsonArray choices = object.getAsJsonArray("choices");
        if (choices.isEmpty()) {
            throw new IllegalStateException("返回中 choices 为空: " + snippet(bodyText));
        }
        JsonElement messageEl = choices.get(0).getAsJsonObject().get("message");
        if (messageEl == null || !messageEl.isJsonObject()) {
            throw new IllegalStateException("返回中没有 message: " + snippet(bodyText));
        }
        JsonObject message = messageEl.getAsJsonObject();
        List<ToolCall> toolCalls = null;
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            toolCalls = new ArrayList<>();
            for (JsonElement callEl : message.getAsJsonArray("tool_calls")) {
                if (!callEl.isJsonObject()) continue;
                JsonObject call = callEl.getAsJsonObject();
                String id = call.has("id") ? call.get("id").getAsString() : "";
                JsonObject fn = call.has("function") && call.get("function").isJsonObject()
                        ? call.getAsJsonObject("function") : null;
                if (fn == null || !fn.has("name")) continue;
                String name = fn.get("name").getAsString();
                JsonObject arguments = new JsonObject();
                if (fn.has("arguments") && fn.get("arguments").isJsonPrimitive()) {
                    try {
                        JsonElement parsed = JsonParser.parseString(fn.get("arguments").getAsString());
                        if (parsed.isJsonObject()) arguments = parsed.getAsJsonObject();
                    } catch (Exception ignored) {
                    }
                }
                toolCalls.add(new ToolCall(id, name, arguments));
            }
        }
        String content = extractContent(message, choices);
        return new Reply(content, toolCalls);
    }

    private static String extractContent(JsonObject message, JsonArray choices) {
        if (!message.has("content") || message.get("content").isJsonNull()) {
            // 部分接口把 content 放在 choices[0].text 或 delta 里
            if (choices.get(0).getAsJsonObject().has("text")) {
                return choices.get(0).getAsJsonObject().get("text").getAsString().trim();
            }
            return "";
        }
        JsonElement content = message.get("content");
        if (content.isJsonArray()) { // OpenAI 新版富文本/推理格式
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : content.getAsJsonArray()) {
                if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                    sb.append(part.getAsJsonObject().get("text").getAsString());
                } else if (part.isJsonPrimitive()) {
                    sb.append(part.getAsString());
                }
            }
            return sb.toString().trim();
        }
        return content.getAsString().trim();
    }

    private static String snippet(String bodyText) {
        if (bodyText == null || bodyText.isEmpty()) return "(空响应体)";
        return bodyText.length() < 200 ? bodyText : bodyText.substring(0, 200);
    }
}