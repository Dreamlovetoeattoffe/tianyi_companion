package dev.dpon.tianyi.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Persists chat history per-player so conversations survive screen close / game restart. */
public final class TianyiChatHistory {
    private static final int MAX_MESSAGES = 60; // ~30 user/assistant rounds
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<TianyiApi.Message> messages;

    TianyiChatHistory(List<TianyiApi.Message> messages) {
        this.messages = messages;
    }

    public List<TianyiApi.Message> messages() {
        return messages;
    }

    public void add(TianyiApi.Message m) {
        messages.add(m);
        while (messages.size() > MAX_MESSAGES) messages.remove(0);
    }

    private static Path historyDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("tianyi_chat_history");
    }

    private static Path fileFor(String playerId) {
        return historyDir().resolve(playerId + ".json");
    }

    public static TianyiChatHistory load(String playerId) {
        Path path = fileFor(playerId);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                List<TianyiApi.Message> list = GSON.fromJson(json,
                        new TypeToken<List<TianyiApi.Message>>(){}.getType());
                if (list != null) return new TianyiChatHistory(new ArrayList<>(list));
            } catch (IOException ignored) {}
        }
        return new TianyiChatHistory(new ArrayList<>());
    }

    public void save(String playerId) {
        try {
            Files.createDirectories(historyDir());
            Files.writeString(fileFor(playerId), GSON.toJson(messages));
        } catch (IOException ignored) {}
    }
}
