package com.kaflisz.checklistoverlay;

import com.kaflisz.checklistoverlay.gui.ChecklistEditScreen;
import com.kaflisz.checklistoverlay.gui.ChecklistPasteScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import org.lwjgl.glfw.GLFW;

public class ChecklistOverlayMod implements ClientModInitializer {
    public static final ChecklistState STATE = new ChecklistState();

    private static KeyBinding openPasteKey;
    private static KeyBinding openEditKey;
    private static KeyBinding toggleVisibleKey;

    @Override
    public void onInitializeClient() {
        STATE.load();

        openPasteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.checklistoverlay.paste",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
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
            // NEW: Auto-check inventory
            if (client.player != null && STATE.visible) {
                for (ChecklistEntry entry : STATE.entries) {
                    if (!entry.obtained) {
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
            while (openEditKey.wasPressed()) {
                client.setScreen(new ChecklistEditScreen());
            }
            while (toggleVisibleKey.wasPressed()) {
                STATE.visible = !STATE.visible;
                STATE.save();
            }
        });
    }
}