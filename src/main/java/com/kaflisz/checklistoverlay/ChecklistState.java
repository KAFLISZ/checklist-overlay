package com.kaflisz.checklistoverlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side display cache. Entries and available list names are always
 * server-pushed -- this class never invents or mutates checklist data
 * itself, it just holds the most recent state the server sent and the
 * purely-local UI prefs (panel position/size/visibility).
 */
public class ChecklistState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("checklist-overlay.json");

    public List<ChecklistEntry> entries = new ArrayList<>();
    public String joinedListName = null; // null = not currently joined to any list
    public List<String> availableListNames = new ArrayList<>();

    public int overlayX = 12;
    public int overlayY = 12;
    public boolean visible = true;
    public boolean collapsed = false;
    public int panelWidth = 200;
    public int panelHeight = 200;
    public double scrollOffset = 0;

    private static class SaveData {
        int overlayX;
        int overlayY;
        boolean visible;
        boolean collapsed;
        int panelWidth = 200;
        int panelHeight = 200;
        String joinedListName;
    }

    public void applyListState(String listName, List<ChecklistEntry> newEntries) {
        this.joinedListName = listName;
        this.entries = newEntries;
    }

    public void applyListNames(List<String> names) {
        this.availableListNames = names;
    }

    public void clearJoinedList() {
        this.joinedListName = null;
        this.entries = new ArrayList<>();
        this.scrollOffset = 0;
    }

    public ChecklistEntry findEntry(UUID id) {
        for (ChecklistEntry e : entries) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    public void save() {
        SaveData data = new SaveData();
        data.overlayX = overlayX;
        data.overlayY = overlayY;
        data.visible = visible;
        data.collapsed = collapsed;
        data.panelWidth = panelWidth;
        data.panelHeight = panelHeight;
        data.joinedListName = joinedListName;

        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to save config: " + e.getMessage());
        }
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;

        try {
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            SaveData data = GSON.fromJson(json, SaveData.class);
            if (data == null) return;

            overlayX = data.overlayX;
            overlayY = data.overlayY;
            visible = data.visible;
            collapsed = data.collapsed;
            joinedListName = data.joinedListName; // used to auto-rejoin on connect

            if (data.panelWidth >= 100) panelWidth = data.panelWidth;
            if (data.panelHeight >= 50) panelHeight = data.panelHeight;
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to load config: " + e.getMessage());
        }
    }
}
