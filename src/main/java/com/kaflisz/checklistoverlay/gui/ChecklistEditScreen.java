package com.kaflisz.checklistoverlay.gui;

import com.kaflisz.checklistoverlay.ChecklistEntry;
import com.kaflisz.checklistoverlay.ChecklistHud;
import com.kaflisz.checklistoverlay.ChecklistOverlayMod;
import com.kaflisz.checklistoverlay.ChecklistState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
        ChecklistState state = ChecklistOverlayMod.STATE;
        
        // NEW: Button to collapse/expand the list
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(state.collapsed ? "Expand List" : "Collapse List"),
            btn -> {
                state.collapsed = !state.collapsed;
                btn.setMessage(Text.literal(state.collapsed ? "Expand List" : "Collapse List"));
            }
        ).dimensions(this.width / 2 - 105, 10, 100, 20).build());

        // NEW: Button to toggle if it shows outside the edit screen at all
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(state.visible ? "Hide In-Game" : "Show In-Game"),
            btn -> {
                state.visible = !state.visible;
                btn.setMessage(Text.literal(state.visible ? "Hide In-Game" : "Show In-Game"));
            }
        ).dimensions(this.width / 2 + 5, 10, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ChecklistState state = ChecklistOverlayMod.STATE;
        ChecklistHud.drawPanel(context, state.overlayX, state.overlayY, true);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Use top buttons to toggle list. Drag bottom-right to resize. Right-click row to delete."),
            this.width / 2, this.height - 24, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        ChecklistState state = ChecklistOverlayMod.STATE;
        if (!state.collapsed) {
            state.scrollOffset -= amount * 15;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // MUST call super first so our new Buttons get the click event!
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        ChecklistState state = ChecklistOverlayMod.STATE;

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
                    if (button == 0) { 
                        ChecklistEntry entry = state.entries.get(index);
                        entry.obtained = !entry.obtained;
                    } else if (button == 1) { 
                        state.entries.remove(index);
                        ChecklistOverlayMod.STATE.save();
                    }
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ChecklistState state = ChecklistOverlayMod.STATE;
        
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
            ChecklistOverlayMod.STATE.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        ChecklistOverlayMod.STATE.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}