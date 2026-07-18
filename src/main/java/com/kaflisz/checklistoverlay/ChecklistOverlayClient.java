package com.kaflisz.checklistoverlay;

import com.kaflisz.checklistoverlay.gui.ChecklistEditScreen;
import com.kaflisz.checklistoverlay.gui.ChecklistListsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChecklistOverlayClient implements ClientModInitializer {
    public static final ChecklistState STATE = new ChecklistState();

    private static KeyBinding openListsKey;
    private static KeyBinding openEditKey;
    private static KeyBinding toggleVisibleKey;

    @Override
    public void onInitializeClient() {
        STATE.load();

        openListsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.lists", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.categories.checklistoverlay"));

        openEditKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.toggle_edit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_APOSTROPHE,
            "key.categories.checklistoverlay"));

        toggleVisibleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.toggle_visible", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.checklistoverlay"));

        HudRenderCallback.EVENT.register(ChecklistHud::render);

        registerNetworkReceivers();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Try to resume whatever list we were last in, if any -- the
            // server will just no-op (via an error message) if it doesn't
            // recognize the name on this particular server.
            if (STATE.joinedListName != null) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(STATE.joinedListName);
                ClientPlayNetworking.send(NetworkChannels.JOIN_LIST, buf);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STATE.entries = new ArrayList<>();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null) return;

            while (openListsKey.wasPressed()) {
                client.setScreen(new ChecklistListsScreen());
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

    private void registerNetworkReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkChannels.LIST_NAMES, (client, handler, buf, sender) -> {
            int count = buf.readVarInt();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < count; i++) names.add(buf.readString(64));
            client.execute(() -> STATE.applyListNames(names));
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkChannels.LIST_STATE, (client, handler, buf, sender) -> {
            String listName = buf.readString(64);
            int count = buf.readVarInt();
            List<ChecklistEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                UUID id = buf.readUuid();
                String itemId = buf.readString(256);
                int targetQty = buf.readVarInt();
                int contributedQty = buf.readVarInt();
                boolean obtained = buf.readBoolean();
                entries.add(new ChecklistEntry(id, itemId, targetQty, contributedQty, obtained));
            }
            client.execute(() -> {
                STATE.applyListState(listName, entries);
                STATE.save();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkChannels.ERROR_MESSAGE, (client, handler, buf, sender) -> {
            String message = buf.readString(256);
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("[Checklist] " + message), false);
                }
            });
        });
    }

    /** Sends an add-item request for the currently joined list. Used by the item picker screen. */
    public static void requestAddItem(String itemId, int quantity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (STATE.joinedListName == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[Checklist] Join a list first (press [)."), false);
            }
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(STATE.joinedListName);
        buf.writeString(itemId);
        buf.writeVarInt(quantity);
        ClientPlayNetworking.send(NetworkChannels.ADD_ITEM, buf);
    }
}
