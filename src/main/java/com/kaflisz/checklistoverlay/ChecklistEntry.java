package com.kaflisz.checklistoverlay;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * One row of the overlay: an item id + quantity, resolved against the
 * client's own item registry -- the mod never needs names or icons from
 * the web app, just the vanilla (or modded) item id.
 *
 * When synced to a shared list (serverId != null), `quantity` is the
 * group's target, `contributedQty` is the live sum of what everyone
 * currently holds (as last reported by the server), and `obtained` is the
 * server's sticky ratchet -- manual left-click toggling is disabled for
 * synced entries since the server is authoritative and would just
 * overwrite it on the next poll anyway.
 */
public class ChecklistEntry {
    public final String rawId;      // e.g. "diamond" or "somemod:custom_item"
    public final int quantity;      // target quantity
    public boolean obtained;        // local ratchet (offline mode) or mirrored server ratchet (synced mode)

    public String serverId;         // checklist_items.id on Supabase, or null if this is a local/offline-only entry
    public int contributedQty;      // group total currently held, only meaningful when serverId != null

    public ChecklistEntry(String rawId, int quantity) {
        this.rawId = rawId;
        this.quantity = quantity;
        this.obtained = false;
        this.serverId = null;
        this.contributedQty = 0;
    }

    public boolean isSynced() {
        return serverId != null;
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
