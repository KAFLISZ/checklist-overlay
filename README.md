# Checklist Overlay

A client-side Fabric mod for Minecraft 1.20.1 that allows you to paste a shopping-list string (generated from a web app or typed manually) and track your item gathering with a customizable, in-game overlay.

## Features

- **Smart Inventory Tracking**: Automatically checks off items on your list as you collect them in your inventory. Once an item is checked, it stays checked, even if you drop or use the item later.
- **Fully Customizable HUD**:
  - **Move**: Drag the header to position the overlay anywhere on your screen.
  - **Resize**: Drag the bottom-right corner to adjust the panel's width and height.
  - **Scroll**: Use the mouse wheel to scroll through long lists.
  - **Minimize**: Collapse the list down to just the header to save screen space.
- **Item Management**: Left-click an item to manually toggle its completion status, or right-click to delete it from the list entirely.
- **Localized Names**: Displays human-readable, properly translated in-game item names instead of raw registry IDs.
- **Persistent State**: The mod remembers your window position, size, collapse state, and checklist progress across game restarts.

## Default Keybinds

You can change these at any time in the standard Minecraft Controls menu under the **Checklist Overlay** category.

- `;` (Semicolon): Open the Paste Screen (to load a new list).
- `'` (Apostrophe): Toggle Move/Edit Mode (to resize, move, delete items, or collapse the panel).
- Unbound by default: Show/Hide the overlay completely.

## How to Use

1. **Paste a List**: Press `;` to open the Paste Screen. You can type a list manually or click "Paste From Clipboard".
2. **List Format**: The mod reads lists in a compact `id*quantity` format separated by commas.
   - Example: `diamond*3,iron_ingot*12,ender_pearl*8`
   - If no namespace is provided, it defaults to `minecraft:`. Modded items are supported (e.g., `somemod:custom_item*5`).
3. **Edit the Overlay**: Press `'` to enter Edit Mode. In this mode, your mouse is freed up to click buttons at the top of the screen (Collapse/Expand, Hide/Show), drag the panel around, or right-click list items to remove them. Press `Esc` to return to the game.

## Installation

1. Ensure you have the [Fabric Loader](https://fabricmc.net/) installed for Minecraft 1.20.1.
2. Download the required [Fabric API](https://modrinth.com/mod/fabric-api) mod and place it in your `.minecraft/mods` folder.
3. Place the `checklistoverlay-0.2.0.jar` file into your `.minecraft/mods` folder.
4. Launch the game.

## Changelog

### v0.2.0

- Added auto-checking when items enter the player's inventory.
- Overlay is now dynamic: added scrolling and resizing capabilities.
- Items can now be deleted from the list by right-clicking them in Edit Mode.
- Added Collapse/Expand and Hide/Show buttons directly into the Edit screen.
- List now displays proper translated item names instead of raw block IDs.
