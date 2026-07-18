package com.kaflisz.checklistoverlay;

import java.util.UUID;

/** Server-side item row. Definition (id/itemId/targetQty/obtained) is persisted; contributedQty is recomputed live from online players' inventories and never saved. */
public class ServerChecklistItem {
    public UUID id;
    public String itemId;
    public int targetQty;
    public boolean obtained; // sticky ratchet -- set true once, never cleared automatically
    public transient int contributedQty;

    public ServerChecklistItem() {
        // for Gson
    }

    public ServerChecklistItem(String itemId, int targetQty) {
        this.id = UUID.randomUUID();
        this.itemId = itemId;
        this.targetQty = Math.max(1, targetQty);
        this.obtained = false;
        this.contributedQty = 0;
    }
}
