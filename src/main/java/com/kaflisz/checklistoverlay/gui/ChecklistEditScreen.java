package com.kaflisz.checklistoverlay.gui;

import com.kaflisz.checklistoverlay.ChecklistEntry;
import com.kaflisz.checklistoverlay.ChecklistHud;
import com.kaflisz.checklistoverlay.ChecklistOverlayClient;
import com.kaflisz.checklistoverlay.ChecklistState;
import com.kaflisz.checklistoverlay.NetworkChannels;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class ChecklistEditScreen extends Screen {
    private boolean draggingHeader = false;
    private boolean draggingResize = false;
    private int dragOffsetX;
    private int dragOffsetY;

    public ChecklistEditScreen() {
        super(Text.literal("Move Checklist"));
    }

    @Override
    protected void init() {
        super.init();
        ChecklistState state = ChecklistOverlayClient.STATE;

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(state.collapsed ? "Expand List" : "Collapse List"),
            btn -> {
                state.collapsed = !state.collapsed;
                btn.setMessage(Text.literal(state.collapsed ? "Expand List" : "Collapse List"));
                state.save();
            }
        ).dimensions(this.width / 2 - 165, 10, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(state.visible ? "Hide In-Game" : "Show In-Game"),
            btn -> {
                state.visible = !state.visible;
                btn.setMessage(Text.literal(state.visible ? "Hide In-Game" : "Show In-Game"));
                state.save();
            }
        ).dimensions(this.width / 2 - 55, 10, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Add Item"),
            btn -> {
                if (state.joinedListName == null) return;
                this.client.setScreen(new ChecklistItemPickerScreen(this));
            }
        ).dimensions(this.width / 2 + 55, 10, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Switch List"),
            btn -> this.client.setScreen(new ChecklistListsScreen())
        ).dimensions(this.width / 2 + 165, 10, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ChecklistState state = ChecklistOverlayClient.STATE;

        if (state.joinedListName == null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Not in a list -- press \"Switch List\" above to join or create one."),
                this.width / 2, this.height / 2, 0xFFAAAAAA);
            return;
        }

        ChecklistHud.drawPanel(context, state.overlayX, state.overlayY, true);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Drag header to move \u00b7 drag corner to resize \u00b7 right-click a row to remove"),
            this.width / 2, this.height - 24, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        ChecklistState state = ChecklistOverlayClient.STATE;
        if (!state.collapsed) {
            state.scrollOffset -= amount * 15;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        ChecklistState state = ChecklistOverlayClient.STATE;
        if (state.joinedListName == null) return false;

        if (button == 0 && !state.collapsed) {
            boolean insideResize = mouseX >= state.overlayX + state.panelWidth - 8 && mouseX <= state.overlayX + state.panelWidth
                && mouseY >= state.overlayY + state.panelHeight - 8 && mouseY <= state.overlayY + state.panelHeight;
            if (insideResize) {
                draggingResize = true;
                return true;
            }
        }

        if (button == 0) {
            boolean insideHeader = mouseX >= state.overlayX && mouseX <= state.overlayX + state.panelWidth
                && mouseY >= state.overlayY && mouseY <= state.overlayY + ChecklistHud.HEADER_HEIGHT;
            if (insideHeader) {
                draggingHeader = true;
                dragOffsetX = (int) mouseX - state.overlayX;
                dragOffsetY = (int) mouseY - state.overlayY;
                return true;
            }
        }

        if (!state.collapsed) {
            boolean insideRows = mouseX >= state.overlayX && mouseX <= state.overlayX + state.panelWidth
                && mouseY >= state.overlayY + ChecklistHud.HEADER_HEIGHT && mouseY <= state.overlayY + state.panelHeight;

            if (insideRows) {
                double adjustedY = mouseY - (state.overlayY + ChecklistHud.HEADER_HEIGHT) + state.scrollOffset;
                int index = (int) (adjustedY / ChecklistHud.ROW_HEIGHT);

                if (index >= 0 && index < state.entries.size()) {
                    ChecklistEntry entry = state.entries.get(index);
                    if (button == 1) {
                        // Server is authoritative -- removing sends a request
                        // and the server rebroadcasts the updated list to
                        // everyone joined to it, including us.
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeString(state.joinedListName);
                        buf.writeUuid(entry.id);
                        ClientPlayNetworking.send(NetworkChannels.REMOVE_ITEM, buf);
                    }
                    // Left-click does nothing now -- obtained is entirely
                    // server-driven (the group contribution ratchet), so
                    // there's nothing for a manual click to toggle.
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ChecklistState state = ChecklistOverlayClient.STATE;
        if (state.joinedListName == null) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        if (draggingResize && !state.collapsed) {
            state.panelWidth = Math.max(120, (int) mouseX - state.overlayX);
            state.panelHeight = Math.max(ChecklistHud.HEADER_HEIGHT + 20, (int) mouseY - state.overlayY);
            return true;
        }

        if (draggingHeader) {
            state.overlayX = (int) mouseX - dragOffsetX;
            state.overlayY = (int) mouseY - dragOffsetY;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingHeader || draggingResize) {
            draggingHeader = false;
            draggingResize = false;
            ChecklistOverlayClient.STATE.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        ChecklistOverlayClient.STATE.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
