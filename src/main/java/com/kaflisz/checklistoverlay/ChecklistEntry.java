package com.kaflisz.checklistoverlay;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client-side mirror of one server-side checklist row (ServerChecklistItem).
 * Everything here is display data pushed by the server -- the client never
 * computes obtained/contributedQty itself anymore, it just renders what it
 * was told.
 */
public class ChecklistEntry {
    public final UUID id;
    public final String itemId;
    public final int targetQty;
    public int contributedQty;
    public boolean obtained;

    public ChecklistEntry(UUID id, String itemId, int targetQty, int contributedQty, boolean obtained) {
        this.id = id;
        this.itemId = itemId;
        this.targetQty = targetQty;
        this.contributedQty = contributedQty;
        this.obtained = obtained;
    }

    public ItemStack resolveStack() {
        Identifier id = itemId.contains(":")
            ? Identifier.tryParse(itemId)
            : Identifier.of("minecraft", itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = Registries.ITEM.get(id);
        return new ItemStack(item, Math.max(1, targetQty));
    }

    public String displayId() {
        return itemId;
    }
}
