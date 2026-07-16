package com.kaflisz.checklistoverlay.gui;

import com.kaflisz.checklistoverlay.ChecklistOverlayMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Opened with the "Open Paste Screen" keybind. Player can either hit
 * "Paste From Clipboard" (reads whatever they copied from the web
 * checklist) or type/paste the string directly into the field, then
 * confirm to load it into the overlay.
 */
public class ChecklistPasteScreen extends Screen {
    private TextFieldWidget input;

    public ChecklistPasteScreen() {
        super(Text.literal("Paste Checklist"));
    }

    @Override
    protected void init() {
        int boxWidth = Math.min(420, this.width - 40);
        int centerX = this.width / 2;
        int fieldY = this.height / 2 - 10;

        input = new TextFieldWidget(this.textRenderer, centerX - boxWidth / 2, fieldY, boxWidth, 20,
            Text.literal("Checklist string"));
        input.setMaxLength(8192);
        input.setSuggestion("diamond*3,iron_ingot*12,ender_pearl*8");
        this.addDrawableChild(input);
        this.setInitialFocus(input);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Paste From Clipboard"), btn -> {
            String clipboard = this.client.keyboard.getClipboard();
            if (clipboard != null) {
                input.setText(clipboard.trim());
            }
        }).dimensions(centerX - boxWidth / 2, fieldY - 28, boxWidth / 2 - 4, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), btn -> input.setText(""))
            .dimensions(centerX + 4, fieldY - 28, boxWidth / 2 - 4, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Load"), btn -> {
            ChecklistOverlayMod.STATE.parseAndReplace(input.getText());
            this.close();
        }).dimensions(centerX - boxWidth / 2, fieldY + 28, boxWidth / 2 - 4, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.close())
            .dimensions(centerX + 4, fieldY + 28, boxWidth / 2 - 4, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Paste the string you copied from the web checklist"),
            this.width / 2, this.height / 2 - 55, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
