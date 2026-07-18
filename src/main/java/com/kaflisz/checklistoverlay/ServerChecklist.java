package com.kaflisz.checklistoverlay;

import java.util.ArrayList;
import java.util.List;

public class ServerChecklist {
    public String name;
    public List<ServerChecklistItem> items = new ArrayList<>();

    public ServerChecklist() {
        // for Gson
    }

    public ServerChecklist(String name) {
        this.name = name;
    }
}
