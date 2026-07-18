package com.kaflisz.checklistoverlay.gui;

import com.kaflisz.checklistoverlay.ChecklistOverlayClient;
import com.kaflisz.checklistoverlay.ChecklistState;
import com.kaflisz.checklistoverlay.NetworkChannels;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/**
 * Browse / create / join lists on this server. Re-requests the name list
 * from the server on open, and renders whatever ChecklistState currently
 * holds (which updates live as the LIST_NAMES response arrives) rather
 * than a fixed snapshot taken at init time.
 */
public class ChecklistListsScreen extends Screen {
    private static final int ROW_HEIGHT = 20;

    private TextFieldWidget newListInput;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listWidth;

    public ChecklistListsScreen() {
        super(Text.literal("Checklist Lists"));
    }

    @Override
    protected void init() {
        // Ask the server for the current names every time this screen opens.
        ClientPlayNetworking.send(NetworkChannels.REQUEST_LIST_NAMES, PacketByteBufs.create());

        int centerX = this.width / 2;
        listWidth = Math.min(280, this.width - 40);
        listLeft = centerX - listWidth / 2;

        newListInput = new TextFieldWidget(this.textRenderer, listLeft, 40, listWidth - 90, 20, Text.literal("New list name"));
        newListInput.setMaxLength(48);
        newListInput.setSuggestion("e.g. base-materials");
        this.addDrawableChild(newListInput);
        this.setInitialFocus(newListInput);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Create"), btn -> {
            String name = newListInput.getText().trim();
            if (name.isEmpty()) return;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(name);
            ClientPlayNetworking.send(NetworkChannels.CREATE_LIST, buf);
            this.close();
        }).dimensions(listLeft + listWidth - 84, 40, 84, 20).build());

        listTop = 72;
        listBottom = this.height - 40;

        ChecklistState state = ChecklistOverlayClient.STATE;
        if (state.joinedListName != null) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Leave Current List"), btn -> {
                ClientPlayNetworking.send(NetworkChannels.LEAVE_LIST, PacketByteBufs.create());
                state.clearJoinedList();
                state.save();
                this.close();
            }).dimensions(listLeft, this.height - 32, listWidth / 2 - 4, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> this.close())
            .dimensions(listLeft + listWidth / 2 + 4, this.height - 32, listWidth / 2 - 4, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Existing lists on this server:"), this.width / 2, listTop - 12, 0xAAAAAA);

        java.util.List<String> names = ChecklistOverlayClient.STATE.availableListNames;
        if (names.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("(none yet -- create one above)"), this.width / 2, listTop + 8, 0x888888);
            return;
        }

        int y = listTop;
        for (String name : names) {
            if (y + ROW_HEIGHT > listBottom) break;
            boolean hovered = mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean isCurrent = name.equals(ChecklistOverlayClient.STATE.joinedListName);

            int bg = hovered ? 0x40FFFFFF : 0x20FFFFFF;
            context.fill(listLeft, y, listLeft + listWidth, y + ROW_HEIGHT - 2, bg);

            int color = isCurrent ? 0xFF55FF55 : 0xFFFFFFFF;
            String label = name + (isCurrent ? "  (current)" : "");
            context.drawText(this.textRenderer, Text.literal(label), listLeft + 6, y + 5, color, false);

            y += ROW_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        java.util.List<String> names = ChecklistOverlayClient.STATE.availableListNames;
        int y = listTop;
        for (String name : names) {
            if (y + ROW_HEIGHT > listBottom) break;
            if (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(name);
                ClientPlayNetworking.send(NetworkChannels.JOIN_LIST, buf);
                this.close();
                return true;
            }
            y += ROW_HEIGHT;
        }
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
