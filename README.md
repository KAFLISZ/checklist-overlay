# Checklist Overlay (Fabric mod)

A Fabric mod for Minecraft 1.20.1 that adds in-game, multiplayer-synced
shopping checklists -- no website, no external database. Lists live on
the Minecraft server itself (one JSON file per list, in the world save
folder), and every player sees the same live group progress through a
movable, resizable overlay.

**This mod must now be installed on both the server and every client.**
It's no longer client-only -- the server is the source of truth for every
list, so a dedicated server (or the person hosting a LAN/singleplayer
world) needs the jar too.

## How it works

- **Lists live on the server.** Press `[` to open the Lists screen: create
  a new named list, or join an existing one. Anyone on the server can join
  any list.
- **Add items in-game** from the edit screen (`'`): hit **Add Item** to
  open a searchable browser over every item currently loaded -- vanilla
  plus anything added by other installed mods.
- **Group progress, not personal progress.** Every ~5 seconds, the server
  sums how much of each item is currently held across everyone who's
  joined that list, and shows it as `contributed/target` on each row. Once
  the group total first reaches target, that item is marked obtained --
  permanently, even if the materials later get used up. This is enforced
  server-side, so it can never disagree between players.
- **Right-click a row** in edit mode to remove it -- for everyone on the
  list, since the server is authoritative.

## Controls

| Key | Action |
|---|---|
| `[` | Open the Lists screen (browse/create/join) |
| `'` | Toggle move/edit mode |

All rebindable in **Options > Controls > Checklist Overlay**.

### While in edit mode (`'`)
- **Drag the header** -- reposition the panel.
- **Drag the bottom-right corner** -- resize it.
- **Scroll** -- scroll the list when it's taller than the panel.
- **Right-click a row** -- remove it from the list (for everyone).
- Buttons at the top: **Collapse/Expand**, **Hide/Show In-Game**, **Add
  Item** (opens the item browser), **Switch List** (opens the Lists
  screen).

Panel position/size/collapsed state and which list you were last in are
saved locally per-player to `.minecraft/config/checklist-overlay.json`,
and the mod tries to automatically rejoin that list the next time you
connect to a server.

## Project layout

```
checklist-overlay/
  build.gradle
  gradle.properties
  settings.gradle
  src/main/resources/fabric.mod.json
  src/main/resources/assets/checklistoverlay/lang/en_us.json
  src/main/java/com/kaflisz/checklistoverlay/
    ChecklistOverlayMod.java     -- common "main" entrypoint: server network handlers, tick loop
    ChecklistOverlayClient.java  -- client entrypoint: keybinds, client network receivers
    NetworkChannels.java         -- shared packet channel identifiers
    ServerChecklistManager.java  -- server-side list storage, persistence, live contribution recompute
    ServerChecklist.java / ServerChecklistItem.java -- server-side data model (persisted to JSON)
    ChecklistEntry.java          -- client-side mirror of a server item row
    ChecklistState.java          -- client-side display cache + local UI prefs
    ChecklistHud.java            -- draws the panel (shared by gameplay HUD + edit screen)
    gui/ChecklistListsScreen.java     -- browse/create/join lists
    gui/ChecklistItemPickerScreen.java -- search/browse all loaded items to add one
    gui/ChecklistEditScreen.java      -- drag/resize/scroll/remove screen
```

## Persistence

Each list is one JSON file at
`<world save>/checklistoverlay/lists/<sanitized-name>_<hash>.json` on the
server -- created automatically, no setup needed. `contributedQty` is
never persisted (it's recomputed live from online players' inventories
every ~5 seconds); everything else (item definitions, target quantities,
the sticky `obtained` flag) is.

## Building it

Standard Fabric mod project (Fabric Loom), same setup as before:

- Java 17+
- Pinned **Fabric Loom 1.5.7** / **Gradle 8.4** (see
  `gradle/wrapper/gradle-wrapper.properties`) -- build through the wrapper
  (`./gradlew` / `gradlew.bat`), not whatever Gradle you have globally, to
  avoid the `SelfResolvingDependency` configuration error that comes from
  a version mismatch.
- If the wrapper scripts aren't present in your checkout, generate them
  once: `gradle wrapper` (from a Gradle 8.4 install, or IntelliJ's Gradle
  import).

```bash
./gradlew build        # macOS/Linux
gradlew.bat build      # Windows
```

The built jar lands in `build/libs/checklist-overlay-<version>.jar`.
Install it, along with **Fabric API**, on:
- **The server** (dedicated server or the world host for
  singleplayer/LAN) -- required, this is where lists actually live now.
- **Every client** that wants to see/use the overlay.

## Extending it

- Cross-reference against shared storage (a chest, a shulker box) instead
  of just personal inventory, for bases where materials get deposited
  centrally.
- A permissions model (currently anyone can create/join/edit any list --
  fine for a small trusted group, less so for a public server).
- Sort obtained items to the bottom, or filter them out of view.
- Optional JEI/EMI integration (hover an item, press a key to add it) --
  left out for now since it pulls in third-party mod dependencies with
  their own version-compatibility overhead; the in-game item browser
  covers the same need without that.
