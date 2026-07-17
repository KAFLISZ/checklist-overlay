package com.kaflisz.checklistoverlay.gui;

import com.kaflisz.checklistoverlay.ChecklistOverlayMod;
import com.kaflisz.checklistoverlay.ChecklistState;
import com.kaflisz.checklistoverlay.SupabaseConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Opened with the "Join Shared List" keybind. Lets the player enter a share
 * code from the website to start syncing, or stop syncing and fall back to
 * local/offline mode. Network calls happen on the background sync executor
 * (see ChecklistOverlayMod.joinByCode) -- this screen never blocks.
 */
public class ChecklistJoinScreen extends Screen {
    private TextFieldWidget input;
    private ButtonWidget joinButton;
    private String statusMessage = "";
    private int statusColor = 0xAAAAAA;

    public ChecklistJoinScreen() {
        super(Text.literal("Join Shared Checklist"));
    }

    @Override
    protected void init() {
        int boxWidth = Math.min(260, this.width - 40);
        int centerX = this.width / 2;
        int fieldY = this.height / 2 - 10;

        SupabaseConfig config = ChecklistOverlayMod.SUPABASE_CONFIG;
        if (!config.isConfigured()) {
            statusMessage = "Supabase isn't configured yet -- edit config/checklist-overlay-supabase.properties";
            statusColor = 0xFF5555;
        }

        ChecklistState state = ChecklistOverlayMod.STATE;

        input = new TextFieldWidget(this.textRenderer, centerX - boxWidth / 2, fieldY, boxWidth, 20,
            Text.literal("List code"));
        input.setMaxLength(64);
        input.setSuggestion("e.g. f8k2m1");
        if (state.checklistCode != null) {
            input.setText(state.checklistCode);
        }
        this.addDrawableChild(input);
        this.setInitialFocus(input);

        joinButton = ButtonWidget.builder(Text.literal("Join"), btn -> attemptJoin())
            .dimensions(centerX - boxWidth / 2, fieldY + 28, boxWidth / 2 - 4, 20).build();
        this.addDrawableChild(joinButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Stop Syncing"), btn -> {
            ChecklistOverlayMod.STATE.stopSyncing();
            ChecklistOverlayMod.STATE.save();
            statusMessage = "Stopped syncing -- back to offline mode.";
            statusColor = 0xAAAAAA;
        }).dimensions(centerX + 4, fieldY + 28, boxWidth / 2 - 4, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> this.close())
            .dimensions(centerX - boxWidth / 2, fieldY + 54, boxWidth, 20).build());

        if (state.isSyncing()) {
            statusMessage = "Currently synced to: " + state.checklistCode;
            statusColor = 0x55FF55;
        }
    }

    private void attemptJoin() {
        String code = input.getText().trim();
        if (code.isEmpty()) {
            statusMessage = "Enter a list code first.";
            statusColor = 0xFF5555;
            return;
        }

        joinButton.active = false;
        statusMessage = "Looking up list...";
        statusColor = 0xAAAAAA;

        ChecklistOverlayMod.joinByCode(code, success -> {
            joinButton.active = true;
            if (success) {
                statusMessage = "Joined! Now syncing with the group.";
                statusColor = 0x55FF55;
            } else {
                statusMessage = "Couldn't find that list (or Supabase isn't configured).";
                statusColor = 0xFF5555;
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Enter the share code from the website to sync a group checklist"),
            this.width / 2, this.height / 2 - 55, 0xAAAAAA);

        if (!statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(statusMessage), this.width / 2, this.height / 2 + 82, statusColor);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
