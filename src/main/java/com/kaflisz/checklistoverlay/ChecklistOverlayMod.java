package com.kaflisz.checklistoverlay;

import com.kaflisz.checklistoverlay.gui.ChecklistEditScreen;
import com.kaflisz.checklistoverlay.gui.ChecklistJoinScreen;
import com.kaflisz.checklistoverlay.gui.ChecklistPasteScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChecklistOverlayMod implements ClientModInitializer {
    public static final ChecklistState STATE = new ChecklistState();
    public static final SupabaseConfig SUPABASE_CONFIG = new SupabaseConfig();
    public static final ChecklistApi API = new ChecklistApi(SUPABASE_CONFIG);

    // Background thread for all blocking HTTP calls, so the render/tick
    // thread never stalls on network I/O. Results are handed back to the
    // main thread exclusively through mainThreadQueue below -- STATE.entries
    // is only ever mutated on the client thread.
    private static final ScheduledExecutorService SYNC_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "checklist-overlay-sync");
            t.setDaemon(true);
            return t;
        });
    private static final ConcurrentLinkedQueue<Runnable> mainThreadQueue = new ConcurrentLinkedQueue<>();
    private static volatile boolean syncInFlight = false;

    private static KeyBinding openPasteKey;
    private static KeyBinding openJoinKey;
    private static KeyBinding openEditKey;
    private static KeyBinding toggleVisibleKey;

    @Override
    public void onInitializeClient() {
        SUPABASE_CONFIG.load();
        STATE.load();

        openPasteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.paste",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
            "key.categories.checklistoverlay"
        ));

        openJoinKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.join",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.categories.checklistoverlay"
        ));

        openEditKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.toggle_edit",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_APOSTROPHE,
            "key.categories.checklistoverlay"
        ));

        toggleVisibleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.toggle_visible",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.checklistoverlay"
        ));

        HudRenderCallback.EVENT.register(ChecklistHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drain any results the background sync thread produced since
            // the last tick. There's normally at most one pending task
            // (the sync loop waits for the previous round-trip to finish
            // before scheduling another), so this is cheap.
            Runnable task;
            while ((task = mainThreadQueue.poll()) != null) {
                task.run();
            }

            if (client.player != null && STATE.visible) {
                if (STATE.isSyncing()) {
                    maybeStartSyncRound(client);
                } else {
                    // Offline (pasted-string) mode: personal-inventory ratchet,
                    // exactly as before -- never un-checks once obtained.
                    for (ChecklistEntry entry : STATE.entries) {
                        if (entry.obtained) continue;
                        Item item = entry.resolveStack().getItem();
                        int count = client.player.getInventory().count(item);
                        if (count >= entry.quantity) {
                            entry.obtained = true;
                        }
                    }
                }
            }

            if (client.currentScreen != null) return;

            while (openPasteKey.wasPressed()) {
                client.setScreen(new ChecklistPasteScreen());
            }
            while (openJoinKey.wasPressed()) {
                client.setScreen(new ChecklistJoinScreen());
            }
            while (openEditKey.wasPressed()) {
                client.setScreen(new ChecklistEditScreen());
            }
            while (toggleVisibleKey.wasPressed()) {
                STATE.visible = !STATE.visible;
                STATE.save();
            }
        });
    }

    /**
     * Kicks off one sync round every ~5 seconds (100 ticks) while joined to
     * a shared list: report this player's current held counts for every
     * synced item, then re-fetch the authoritative item list. Only one
     * round runs at a time -- if a round-trip takes longer than 5 seconds
     * (slow connection) the next tick simply skips scheduling another until
     * the in-flight one completes, rather than piling up requests.
     */
    private static int tickCounter = 0;

    private static void maybeStartSyncRound(MinecraftClient client) {
        tickCounter++;
        if (tickCounter < 100) return;
        tickCounter = 0;

        if (syncInFlight || !SUPABASE_CONFIG.isConfigured()) return;
        if (client.player == null || client.getSession() == null) return;

        String checklistId = STATE.checklistId;
        String contributor = client.getSession().getUsername();

        // Snapshot each synced entry's target item + how much of it this
        // player currently holds -- must happen here, on the main thread,
        // since it touches the player's inventory.
        List<Map.Entry<String, Integer>> contributions = new ArrayList<>();
        for (ChecklistEntry entry : STATE.entries) {
            if (!entry.isSynced()) continue;
            int count = client.player.getInventory().count(entry.resolveStack().getItem());
            contributions.add(Map.entry(entry.serverId, count));
        }

        syncInFlight = true;
        SYNC_EXECUTOR.submit(() -> {
            try {
                for (Map.Entry<String, Integer> contribution : contributions) {
                    API.upsertContribution(contribution.getKey(), contributor, contribution.getValue());
                }

                List<ChecklistApi.RemoteItem> remoteItems = API.fetchItems(checklistId);
                mainThreadQueue.add(() -> {
                    // Only apply if we're still syncing the same list -- the
                    // player may have switched lists while this round was
                    // in flight.
                    if (checklistId.equals(STATE.checklistId)) {
                        STATE.applyRemoteItems(remoteItems);
                    }
                });
            } finally {
                syncInFlight = false;
            }
        });
    }

    /** Fire-and-forget helper for one-off background calls (e.g. deleting an item). */
    public static void runInBackground(Runnable task) {
        SYNC_EXECUTOR.submit(task);
    }

    /**
     * Resolves a share code to a checklist id and switches into synced mode.
     * Runs on the background executor; invokes onResult on the main thread
     * with either the resolved id or null on failure/not-found.
     */
    public static void joinByCode(String code, java.util.function.Consumer<Boolean> onResult) {
        if (!SUPABASE_CONFIG.isConfigured()) {
            mainThreadQueue.add(() -> onResult.accept(false));
            return;
        }

        SYNC_EXECUTOR.submit(() -> {
            ChecklistApi.ChecklistMeta meta = API.fetchChecklistMeta(code);
            if (meta == null) {
                mainThreadQueue.add(() -> onResult.accept(false));
                return;
            }

            List<ChecklistApi.RemoteItem> items = API.fetchItems(meta.id());
            mainThreadQueue.add(() -> {
                STATE.startSyncing(code, meta.id());
                STATE.applyRemoteItems(items);
                STATE.save();
                onResult.accept(true);
            });
        });
    }
}
