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
    public boolean collapsed = false; // Tracks if the list is minimized

    public int panelWidth = 200;
    public int panelHeight = 200;
    public double scrollOffset = 0;

    // Shared-list sync. checklistCode is what the player typed in; checklistId
    // is the resolved Supabase row id, fetched once on join and reused for
    // every subsequent poll so we don't re-resolve the code every few seconds.
    // Both are null/blank when running in offline (pasted-string) mode.
    public String checklistCode = null;
    public String checklistId = null;

    public boolean isSyncing() {
        return checklistId != null && !checklistId.isBlank();
    }

    private static class SaveData {
        List<String> rawIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        int overlayX;
        int overlayY;
        boolean visible;
        boolean collapsed;
        int panelWidth = 200;
        int panelHeight = 200;
        String checklistCode;
        String checklistId;
    }

    /** Loads a plain pasted "id*qty,id*qty,..." string -- offline mode, stops any active sync. */
    public void parseAndReplace(String raw) {
        stopSyncing();

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
        this.collapsed = false;
        save();
    }

    /** Switches to shared-list mode once a code has been resolved to a Supabase row id. */
    public void startSyncing(String code, String resolvedId) {
        this.checklistCode = code;
        this.checklistId = resolvedId;
        this.collapsed = false;
        save();
    }

    public void stopSyncing() {
        this.checklistCode = null;
        this.checklistId = null;
    }

    /**
     * Replaces the current entries with what the server returned, preserving
     * scroll position but not any prior local state -- the server is
     * authoritative for synced lists.
     */
    public void applyRemoteItems(List<ChecklistApi.RemoteItem> remoteItems) {
        List<ChecklistEntry> updated = new ArrayList<>();
        for (ChecklistApi.RemoteItem remote : remoteItems) {
            ChecklistEntry entry = new ChecklistEntry(remote.itemId(), remote.targetQty());
            entry.serverId = remote.id();
            entry.contributedQty = remote.contributedQty();
            entry.obtained = remote.obtained();
            updated.add(entry);
        }
        this.entries = updated;
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
        data.checklistCode = checklistCode;
        data.checklistId = checklistId;

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
            // Only restore locally-pasted entries if we're not resuming a
            // synced list -- synced entries get fully re-fetched from the
            // server on the next poll instead (they're the source of truth).
            if (data.checklistId == null || data.checklistId.isBlank()) {
                for (int i = 0; i < data.rawIds.size(); i++) {
                    int qty = i < data.quantities.size() ? data.quantities.get(i) : 1;
                    entries.add(new ChecklistEntry(data.rawIds.get(i), qty));
                }
            }

            overlayX = data.overlayX;
            overlayY = data.overlayY;
            visible = data.visible;
            collapsed = data.collapsed;
            checklistCode = data.checklistCode;
            checklistId = data.checklistId;

            if (data.panelWidth >= 100) panelWidth = data.panelWidth;
            if (data.panelHeight >= 50) panelHeight = data.panelHeight;
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to load config: " + e.getMessage());
        }
    }
}
