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

public class ChecklistState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("checklist-overlay.json");

    public List<ChecklistEntry> entries = new ArrayList<>();
    public int overlayX = 12;
    public int overlayY = 12;
    public boolean visible = true;
    public boolean collapsed = false; // NEW: Tracks if the list is minimized
    
    public int panelWidth = 200;
    public int panelHeight = 200;
    public double scrollOffset = 0;

    private static class SaveData {
        List<String> rawIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        int overlayX;
        int overlayY;
        boolean visible;
        boolean collapsed;
        int panelWidth = 200;
        int panelHeight = 200;
    }

    public void parseAndReplace(String raw) {
        List<ChecklistEntry> parsed = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            this.entries = parsed;
            this.scrollOffset = 0;
            save();
            return;
        }

        for (String part : raw.trim().split(",")) {
            if (part.isBlank()) continue;
            String[] pieces = part.split("\\*");
            if (pieces.length != 2) continue;

            String id = pieces[0].trim();
            int qty;
            try {
                qty = Integer.parseInt(pieces[1].trim());
            } catch (NumberFormatException e) {
                qty = 1;
            }
            if (id.isEmpty()) continue;

            parsed.add(new ChecklistEntry(id, Math.max(1, qty)));
        }

        this.entries = parsed;
        this.scrollOffset = 0;
        this.collapsed = false; // Auto-expand when pasting a new list
        save();
    }

    public void save() {
        SaveData data = new SaveData();
        for (ChecklistEntry e : entries) {
            data.rawIds.add(e.rawId);
            data.quantities.add(e.quantity);
        }
        data.overlayX = overlayX;
        data.overlayY = overlayY;
        data.visible = visible;
        data.collapsed = collapsed;
        data.panelWidth = panelWidth;
        data.panelHeight = panelHeight;

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

            entries.clear();
            for (int i = 0; i < data.rawIds.size(); i++) {
                int qty = i < data.quantities.size() ? data.quantities.get(i) : 1;
                entries.add(new ChecklistEntry(data.rawIds.get(i), qty));
            }
            overlayX = data.overlayX;
            overlayY = data.overlayY;
            visible = data.visible;
            collapsed = data.collapsed;
            
            if (data.panelWidth >= 100) panelWidth = data.panelWidth;
            if (data.panelHeight >= 50) panelHeight = data.panelHeight;
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to load config: " + e.getMessage());
        }
    }
}