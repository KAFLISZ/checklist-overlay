package com.kaflisz.checklistoverlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Server-side source of truth for every checklist on this world. Lists are
 * persisted as one JSON file each under <world>/checklistoverlay/lists/, so
 * "the database" is just the server's own save folder -- nothing external
 * to run or configure.
 *
 * Not thread-safe by design: every method here is only ever called from
 * the server thread (network packet handlers and the server tick loop both
 * already run there), so no synchronization is needed.
 */
public class ServerChecklistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, ServerChecklist> lists = new LinkedHashMap<>();
    private final Map<UUID, String> joinedList = new LinkedHashMap<>(); // playerId -> list name
    private Path listsDir;

    public void onServerStarted(MinecraftServer server) {
        listsDir = server.getSavePath(WorldSavePath.ROOT).resolve("checklistoverlay").resolve("lists");
        loadAll();
    }

    public void onServerStopping() {
        lists.clear();
        joinedList.clear();
    }

    public void onPlayerDisconnect(UUID playerId) {
        joinedList.remove(playerId);
    }

    // ---------------------------------------------------------------
    // List CRUD
    // ---------------------------------------------------------------

    public List<String> getListNames() {
        return new ArrayList<>(lists.keySet());
    }

    /** Returns false if the name is blank or already taken. */
    public boolean createList(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 48) return false;
        if (lists.containsKey(trimmed)) return false;

        ServerChecklist list = new ServerChecklist(trimmed);
        lists.put(trimmed, list);
        save(list);
        return true;
    }

    public ServerChecklist getList(String name) {
        return lists.get(name);
    }

    public void joinList(UUID playerId, String listName) {
        joinedList.put(playerId, listName);
    }

    public void leaveList(UUID playerId) {
        joinedList.remove(playerId);
    }

    public String getJoinedList(UUID playerId) {
        return joinedList.get(playerId);
    }

    public boolean addItem(String listName, String itemId, int targetQty) {
        ServerChecklist list = lists.get(listName);
        if (list == null) return false;
        list.items.add(new ServerChecklistItem(itemId, targetQty));
        save(list);
        return true;
    }

    public boolean removeItem(String listName, UUID itemId) {
        ServerChecklist list = lists.get(listName);
        if (list == null) return false;
        boolean removed = list.items.removeIf(i -> i.id.equals(itemId));
        if (removed) save(list);
        return removed;
    }

    // ---------------------------------------------------------------
    // Live group progress
    // ---------------------------------------------------------------

    /**
     * Called periodically from the server tick loop. For every list that
     * has at least one online, joined player, sums each item's target
     * across all of those players' current inventories, and sticks the
     * "obtained" flag once the group total first reaches target.
     * Returns the set of list names whose state changed and should be
     * rebroadcast (in practice: every list with online joined players,
     * since contributedQty is live and can fluctuate even before target
     * is reached).
     */
    public List<String> recomputeAndGetActiveLists(MinecraftServer server) {
        List<String> active = new ArrayList<>();

        for (ServerChecklist list : lists.values()) {
            List<ServerPlayerEntity> members = playersJoinedTo(server, list.name);
            if (members.isEmpty()) continue;

            boolean changed = false;
            for (ServerChecklistItem item : list.items) {
                Item target = resolveItem(item.itemId);
                int total = 0;
                for (ServerPlayerEntity player : members) {
                    total += player.getInventory().count(target);
                }

                if (total != item.contributedQty) changed = true;
                item.contributedQty = total;

                if (!item.obtained && total >= item.targetQty) {
                    item.obtained = true;
                    changed = true;
                }
            }

            active.add(list.name);
            if (changed) save(list); // only obtained/definition changes need persisting; contributedQty itself is transient
        }

        return active;
    }

    public List<ServerPlayerEntity> playersJoinedTo(MinecraftServer server, String listName) {
        List<ServerPlayerEntity> result = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (listName.equals(joinedList.get(player.getUuid()))) {
                result.add(player);
            }
        }
        return result;
    }

    public static Item resolveItem(String rawId) {
        Identifier id = rawId.contains(":")
            ? Identifier.tryParse(rawId)
            : Identifier.of("minecraft", rawId);
        if (id == null) return net.minecraft.item.Items.AIR;
        return Registries.ITEM.get(id);
    }

    public static ItemStack resolveStack(String rawId, int count) {
        return new ItemStack(resolveItem(rawId), Math.max(1, count));
    }

    // ---------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------

    private void loadAll() {
        lists.clear();
        if (listsDir == null || !Files.isDirectory(listsDir)) return;

        try (Stream<Path> paths = Files.list(listsDir)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    String json = Files.readString(path, StandardCharsets.UTF_8);
                    ServerChecklist list = GSON.fromJson(json, ServerChecklist.class);
                    if (list != null && list.name != null && !list.name.isBlank()) {
                        if (list.items == null) list.items = new ArrayList<>();
                        for (ServerChecklistItem item : list.items) {
                            if (item.id == null) item.id = UUID.randomUUID();
                        }
                        lists.put(list.name, list);
                    }
                } catch (IOException | RuntimeException e) {
                    System.err.println("[ChecklistOverlay] Failed to load " + path + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to list checklist files: " + e.getMessage());
        }
    }

    private void save(ServerChecklist list) {
        if (listsDir == null) return;
        try {
            Files.createDirectories(listsDir);
            Path path = listsDir.resolve(sanitizeFileName(list.name) + ".json");
            Files.writeString(path, GSON.toJson(list), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to save list '" + list.name + "': " + e.getMessage());
        }
    }

    private static String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        // Include a short hash so two different names that sanitize to the
        // same string (e.g. "a/b" and "a b") don't collide on disk.
        return cleaned + "_" + Integer.toHexString(name.hashCode());
    }
}
