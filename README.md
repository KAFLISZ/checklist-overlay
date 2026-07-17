# Checklist Overlay (Fabric client mod)

A tiny client-side Fabric mod for Minecraft 1.20.1 that pairs with the web
item checklist. It shows a movable, in-game overlay listing whatever items
you selected on the web page.

There's no server involved and no networking in the mod at all -- the only
data it ever gets is the string you paste in.

## How the workflow fits together

1. On the web checklist, select items and set quantities, then hit
   **"Copy List For Mod"**. That copies a compact string to your clipboard,
   e.g. `diamond*3,iron_ingot*12,ender_pearl*8`.
2. In Minecraft, press `;` (semicolon) to open the paste screen.
3. Click **"Paste From Clipboard"** (or paste manually with Ctrl+V into the
   field), then **Load**.
4. The overlay appears in the top-left corner showing each item's icon,
   quantity, and id.
5. Press `'` (apostrophe) to open the move/edit screen: drag the header to
   reposition the panel anywhere on screen, or click a row to mark it
   obtained (it goes green with a strike-through). Press Escape to lock
   it back into place during normal play.
6. Position and the last-loaded list are saved to
   `.minecraft/config/checklist-overlay.json` and persist between sessions
   (the "obtained" checkmarks reset on a fresh paste, since that's meant
   to represent a new run).

Both keybinds are rebindable in **Options > Controls > Checklist Overlay**
if `;` / `'` clash with anything else you use.

## Project layout

```
checklist-overlay/
  build.gradle
  gradle.properties
  settings.gradle
  src/main/resources/fabric.mod.json
  src/main/resources/assets/checklistoverlay/lang/en_us.json
  src/main/java/com/kaflisz/checklistoverlay/
    ChecklistOverlayMod.java   -- entrypoint: keybinds + HUD registration
    ChecklistState.java        -- parses the pasted string, saves/loads config
    ChecklistEntry.java        -- one row (item id + quantity + obtained flag)
    ChecklistHud.java          -- draws the panel (shared by gameplay HUD + edit screen)
    gui/ChecklistPasteScreen.java  -- paste-a-string screen
    gui/ChecklistEditScreen.java   -- drag-to-reposition / click-to-check-off screen
```

## Building it

This is a standard Fabric mod project (Fabric Loom). You'll need:

- Java 17+
- An internet connection the first time you build (Gradle needs to pull
  Minecraft, Yarn mappings, Fabric Loader, and Fabric API)

The project pins **Fabric Loom 1.5.7** and **Gradle 8.4** (in
`gradle/wrapper/gradle-wrapper.properties`) -- a known-working pair for
Minecraft 1.20.1. Using a mismatched Gradle version with Loom is the most
common cause of a `SelfResolvingDependency` / "Metadata provider not
setup" configuration error, so it matters that you build through the
wrapper below rather than whatever Gradle you already have installed.

The wrapper *scripts* (`gradlew` / `gradlew.bat`) and the small
`gradle-wrapper.jar` that goes with them aren't included in this zip --
generate them once from the pinned properties file:

```bash
cd checklist-overlay
gradle wrapper   # uses gradle/wrapper/gradle-wrapper.properties, downloads Gradle 8.4
```

(Any Gradle version can run the `wrapper` task itself -- it just reads the
properties file and fetches the pinned version for you. If you don't have
Gradle installed at all, IntelliJ IDEA's "Import Gradle Project" will also
generate the wrapper automatically.)

From then on, always build through the wrapper it just created:

```bash
./gradlew build        # macOS/Linux
gradlew.bat build      # Windows
```

The built jar lands in `build/libs/checklist-overlay-0.1.0.jar`. Drop it,
along with the matching **Fabric API** jar for 1.20.1, into your
`.minecraft/mods` folder.

## Extending it

A few natural next steps if you want to take this further:
- Resolve item **display names** (not just the raw id) via
  `Registries.ITEM.get(id).getName()` in `ChecklistEntry` for nicer labels.
  Currently it just shows the raw id to keep the mod fully offline.
- Cross-reference against your **actual inventory** (`client.player.getInventory()`)
  to auto-mark items obtained instead of doing it by hand -- this is the
  natural bridge to the "read my live inventory" idea we talked about
  earlier, and it's all local to the client so no extra networking is
  needed for it either.
- Group/sort rows (e.g. obtained items at the bottom).
