package com.kaflisz.checklistoverlay.gui;

import com.kaflisz.checklistoverlay.ChecklistOverlayClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Browses the client's full item registry (whatever's currently
 * loaded -- vanilla plus every installed mod's items) with a live search
 * filter, similar to creative inventory search. Picking one and hitting
 * "Add to List" sends an add-item request for the currently joined list.
 */
public class ChecklistItemPickerScreen extends Screen {
    private static final int CELL_SIZE = 20;
    private static final int GRID_COLUMNS = 9;

    private final Screen parent;
    private TextFieldWidget searchBox;
    private TextFieldWidget quantityBox;
    private List<Item> filteredItems = new ArrayList<>();
    private Item selectedItem = null;
    private int scrollRows = 0;
    private int gridLeft, gridTop, gridBottom;

    public ChecklistItemPickerScreen(Screen parent) {
        super(Text.literal("Add Item"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int gridWidth = GRID_COLUMNS * CELL_SIZE;
        gridLeft = centerX - gridWidth / 2;
        gridTop = 60;
        gridBottom = this.height - 70;

        searchBox = new TextFieldWidget(this.textRenderer, gridLeft, 30, gridWidth, 20, Text.literal("Search"));
        searchBox.setSuggestion("Search items...");
        searchBox.setChangedListener(text -> refreshFilter());
        this.addDrawableChild(searchBox);
        this.setInitialFocus(searchBox);

        quantityBox = new TextFieldWidget(this.textRenderer, centerX - 40, this.height - 34, 60, 20, Text.literal("Qty"));
        quantityBox.setText("1");
        quantityBox.setMaxLength(5);
        this.addDrawableChild(quantityBox);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add to List"), btn -> {
            if (selectedItem == null) return;
            int qty = parseQuantity();
            Identifier id = Registries.ITEM.getId(selectedItem);
            ChecklistOverlayClient.requestAddItem(id.toString(), qty);
            this.client.setScreen(parent);
        }).dimensions(centerX + 30, this.height - 34, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.client.setScreen(parent))
            .dimensions(centerX - 160, this.height - 34, 100, 20).build());

        refreshFilter();
    }

    private int parseQuantity() {
        try {
            int q = Integer.parseInt(quantityBox.getText().trim());
            return Math.max(1, q);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void refreshFilter() {
        String query = searchBox.getText().trim().toLowerCase();
        filteredItems = Registries.ITEM.stream()
            .filter(item -> {
                if (item == net.minecraft.item.Items.AIR) return false;
                if (query.isEmpty()) return true;
                String name = item.getName().getString().toLowerCase();
                String path = Registries.ITEM.getId(item).getPath().toLowerCase();
                return name.contains(query) || path.contains(query);
            })
            .collect(Collectors.toList());
        scrollRows = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal(filteredItems.size() + " items \u2014 click one to select, scroll to see more"),
            this.width / 2, 14, 0xAAAAAA);

        int visibleRows = (gridBottom - gridTop) / CELL_SIZE;
        int startIndex = scrollRows * GRID_COLUMNS;

        context.enableScissor(gridLeft, gridTop, gridLeft + GRID_COLUMNS * CELL_SIZE, gridBottom);
        for (int row = 0; row < visibleRows + 1; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                int index = startIndex + row * GRID_COLUMNS + col;
                if (index >= filteredItems.size()) continue;

                Item item = filteredItems.get(index);
                int cellX = gridLeft + col * CELL_SIZE;
                int cellY = gridTop + row * CELL_SIZE;

                boolean hovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE && mouseY >= cellY && mouseY < cellY + CELL_SIZE;
                boolean selected = item == selectedItem;
                if (selected) {
                    context.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, 0x8055FF55);
                } else if (hovered) {
                    context.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, 0x40FFFFFF);
                }

                context.drawItem(new ItemStack(item), cellX + 2, cellY + 2);
            }
        }
        context.disableScissor();

        if (selectedItem != null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(selectedItem.getName().getString()), this.width / 2, gridBottom + 8, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        if (mouseX >= gridLeft && mouseX < gridLeft + GRID_COLUMNS * CELL_SIZE && mouseY >= gridTop && mouseY < gridBottom) {
            int col = (int) ((mouseX - gridLeft) / CELL_SIZE);
            int row = (int) ((mouseY - gridTop) / CELL_SIZE);
            int index = (scrollRows + row) * GRID_COLUMNS + col;
            if (index >= 0 && index < filteredItems.size()) {
                selectedItem = filteredItems.get(index);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int totalRows = (filteredItems.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        int visibleRows = (gridBottom - gridTop) / CELL_SIZE;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scrollRows = Math.max(0, Math.min(maxScroll, scrollRows - (int) amount));
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
