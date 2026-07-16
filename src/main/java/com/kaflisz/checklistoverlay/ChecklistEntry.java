package com.kaflisz.checklistoverlay;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * One row of the overlay: an item id + quantity, resolved against the
 * client's own item registry -- the mod never needs names or icons from
 * the web app, just the vanilla (or modded) item id.
 */
public class ChecklistEntry {
    public final String rawId;      // e.g. "diamond" or "somemod:custom_item"
    public final int quantity;
    public boolean obtained;        // toggled in-game, not sent from the web app

    public ChecklistEntry(String rawId, int quantity) {
        this.rawId = rawId;
        this.quantity = quantity;
        this.obtained = false;
    }

    /**
     * Resolves this entry's id against the item registry. Defaults to the
     * "minecraft" namespace if the pasted string didn't include one, so
     * the compact "diamond*3" format from the web app works directly.
     */
    public ItemStack resolveStack() {
        Identifier id = rawId.contains(":")
            ? Identifier.tryParse(rawId)
            : Identifier.of("minecraft", rawId);

        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item = Registries.ITEM.get(id);
        return new ItemStack(item, Math.max(1, quantity));
    }

    public String displayId() {
        return rawId;
    }
}
