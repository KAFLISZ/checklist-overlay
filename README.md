# Checklist Overlay (Fabric client mod)

A client-side Fabric mod for Minecraft 1.20.1 that pairs with the web
item checklist at kaflisz.com. It shows a movable, resizable, in-game
overlay listing whatever items you (or your group) selected on the site --
either pasted in as a one-off string, or live-synced through a shared list
so everyone sees the same progress.

## Two ways to use it

### Offline: paste a string
No account, no network, no shared progress -- just a personal list.

1. On the web checklist, select items and set quantities, then hit
   **"Copy List For Mod"**. That copies a compact string to your clipboard,
   e.g. `diamond*3,iron_ingot*12,ender_pearl*8`.
2. In Minecraft, press `;` (semicolon) to open the paste screen.
3. Click **"Paste From Clipboard"** (or paste manually with Ctrl+V), then
   **Load**.
4. Items check themselves off automatically once your inventory holds
   enough of them -- and once checked, they stay checked even if the
   items later leave your inventory (crafted into something else, etc).

### Online: join a shared list
Multiple players see and contribute to the same list in real time.

1. On the web checklist, select items and hit **"Create Shared List (Live
   Sync)"**. This creates the list on the backend and gives you a short
   share code (e.g. `f8k2m1`).
2. In Minecraft, press `[` (left bracket) to open the Join screen, type in
   the code, and hit **Join**.
3. Every few seconds, the mod reports how much of each item you're
   currently holding, and the server sums that across everyone syncing to
   the same list -- so the overlay shows real group progress (e.g. `7/16`
   dirt gathered by the whole group, not just you).
4. Once the group total reaches the target, that item is marked obtained
   for everyone -- permanently, even if the materials later get used up.
   This is enforced server-side, so it can't get out of sync between
   players.
5. Right-clicking a row in edit mode removes it from the list for
   everyone, not just locally.

Both modes share the same overlay and edit screen -- you can only be in
one mode at a time; loading a pasted string stops any active sync, and
vice versa.

## Controls

| Key | Action |
|---|---|
| `;` | Open the offline paste screen |
| `[` | Open the Join Shared List screen |
| `'` | Toggle move/edit mode |

All three are rebindable in **Options > Controls > Checklist Overlay** if
they clash with anything else you use.

### While in edit mode (`'`)
- **Drag the header** -- reposition the panel anywhere on screen.
- **Drag the bottom-right corner** -- resize the panel.
- **Scroll** -- scroll the list when it's taller than the panel.
- **Left-click a row** -- toggle obtained (offline mode only; synced lists
  are controlled by the server, so manual toggling is disabled there).
- **Right-click a row** -- remove it (from the shared list, for a synced
  entry; just locally, for an offline one).
- Buttons at the top of the screen: **Collapse/Expand** the list down to
  just its header, and **Hide/Show In-Game** to toggle the overlay's
  visibility during normal play.

Position, size, collapsed state, and which list (if any) you're synced to
all persist across restarts, saved to
`.minecraft/config/checklist-overlay.json`.

## Connecting to your own Supabase project

The mod ships with a Supabase project's URL + anon key already baked into
`SupabaseConfig.java` -- the anon key is meant to be public by design (the
website uses the exact same one), so this isn't a secret being exposed,
just a convenience so the mod works out of the box with no setup.

If you want to point a specific install at a *different* project without
recompiling (e.g. your own test project), the mod also reads an optional
override file at `.minecraft/config/checklist-overlay-supabase.properties`
-- it's auto-created with blank fields on first run; fill in `supabase_url`
and `supabase_anon_key` there to override the built-in defaults.

## Project layout

```
checklist-overlay/
  build.gradle
  gradle.properties
  settings.gradle
  src/main/resources/fabric.mod.json
  src/main/resources/assets/checklistoverlay/lang/en_us.json
  src/main/java/com/kaflisz/checklistoverlay/
    ChecklistOverlayMod.java   -- entrypoint: keybinds, HUD registration, background sync loop
    ChecklistState.java        -- offline/synced list state, save/load config
    ChecklistEntry.java        -- one row (item id, quantity, obtained flag, server id + group contribution when synced)
    ChecklistHud.java          -- draws the panel (shared by gameplay HUD + edit screen), resizing/scrolling
    ChecklistApi.java          -- Supabase REST client (fetch list, report contribution, delete item)
    SupabaseConfig.java        -- Supabase URL/anon key (hardcoded defaults + optional override file)
    gui/ChecklistPasteScreen.java  -- offline paste-a-string screen
    gui/ChecklistJoinScreen.java   -- online join-by-code screen
    gui/ChecklistEditScreen.java   -- drag/resize/scroll/toggle/remove screen
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

If a repo checkout doesn't include the wrapper *scripts*
(`gradlew` / `gradlew.bat`) and jar, generate them once from the pinned
properties file:

```bash
cd checklist-overlay
gradle wrapper   # uses gradle/wrapper/gradle-wrapper.properties, downloads Gradle 8.4
```

(Any Gradle version can run the `wrapper` task itself -- it just reads the
properties file and fetches the pinned version for you. If you don't have
Gradle installed at all, IntelliJ IDEA's "Import Gradle Project" will also
generate the wrapper automatically.)

From then on, always build through the wrapper:

```bash
./gradlew build        # macOS/Linux
gradlew.bat build      # Windows
```

The built jar lands in `build/libs/checklist-overlay-<version>.jar`. Drop
it, along with the matching **Fabric API** jar for 1.20.1, into your
`.minecraft/mods` folder.

## Backend

The shared-list feature runs on Supabase (Postgres + auto-generated REST
API + Row Level Security). The schema (`checklists`, `checklist_items`,
`checklist_contributions`, plus the trigger that sums contributions and
sticks the `obtained` flag once a target is reached) lives alongside the
website project, not in this repo.

## Extending it

A few natural next steps if you want to take this further:
- Group/sort rows (e.g. obtained items at the bottom).
- A "my lists" view on the website for lists you've created, rather than
  the share code being the only record of a list's existence.
- Cross-reference against shared storage (a chest, a shulker box) instead
  of just personal inventory, for bases where materials get deposited
  centrally rather than carried.
