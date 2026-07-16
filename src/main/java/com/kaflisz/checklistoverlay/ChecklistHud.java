package com.kaflisz.checklistoverlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public class ChecklistHud {
    public static final int ROW_HEIGHT = 20;
    public static final int PADDING = 6;
    public static final int HEADER_HEIGHT = PADDING + 14;

    public static void render(DrawContext context, float tickDelta) {
        ChecklistState state = ChecklistOverlayMod.STATE;
        if (!state.visible || state.entries.isEmpty()) return;
        drawPanel(context, state.overlayX, state.overlayY, false);
    }

    public static void drawPanel(DrawContext context, int x, int y, boolean editMode) {
        MinecraftClient client = MinecraftClient.getInstance();
        ChecklistState state = ChecklistOverlayMod.STATE;
        List<ChecklistEntry> entries = state.entries;
        
        int width = state.panelWidth;
        // NEW: Force height to just the header if collapsed
        int height = state.collapsed ? HEADER_HEIGHT : state.panelHeight;

        int bg = editMode ? 0xC0202020 : 0x90101010;
        int border = editMode ? 0xFF55FF55 : 0x40FFFFFF;
        
        context.fill(x, y, x + width, y + height, bg);
        context.drawBorder(x, y, width, height, border);

        // Header - Add [+] or [-] to show status
        String headerLabel = "Checklist " + (state.collapsed ? "[+]" : "[-]") + (editMode ? " (drag)" : "");
        context.drawText(client.textRenderer, Text.literal(headerLabel), x + PADDING, y + PADDING, 0xFFFFFF, true);

        // NEW: Only render list contents if it's NOT collapsed
        if (!state.collapsed) {
            int totalContentHeight = entries.size() * ROW_HEIGHT;
            int maxScroll = Math.max(0, totalContentHeight - (height - HEADER_HEIGHT - PADDING));
            state.scrollOffset = Math.max(0, Math.min(state.scrollOffset, maxScroll));

            context.enableScissor(x, y + HEADER_HEIGHT, x + width, y + height);

            int rowY = y + HEADER_HEIGHT - (int)state.scrollOffset;
            for (ChecklistEntry entry : entries) {
                if (rowY + ROW_HEIGHT > y + HEADER_HEIGHT && rowY < y + height) {
                    ItemStack stack = entry.resolveStack();
                    context.drawItem(stack, x + PADDING, rowY);

                    int textColor = entry.obtained ? 0xFF55FF55 : 0xFFDDDDDD;
                    String itemName = stack.getName().getString();
                    String label = "x" + entry.quantity + "  " + itemName;
                    
                    context.drawText(client.textRenderer, Text.literal(label), x + PADDING + 20, rowY + 4, textColor, false);

                    if (entry.obtained) {
                        int textWidth = client.textRenderer.getWidth(label);
                        int strikeMaxX = Math.min(x + PADDING + 20 + textWidth, x + width - PADDING);
                        context.fill(x + PADDING + 20, rowY + 9, strikeMaxX, rowY + 10, 0xFF55FF55);
                    }
                }
                rowY += ROW_HEIGHT;
            }
            
            context.disableScissor();

            if (editMode) {
                context.fill(x + width - 8, y + height - 8, x + width, y + height, 0xAAFFFFFF);
            }
        }
    }
}