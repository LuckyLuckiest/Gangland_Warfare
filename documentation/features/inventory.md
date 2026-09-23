# Inventory System

[← Database & Setup](./database.md) | [Back to Index](../README.md)

---

## Overview

The inventory system lets you define fully custom GUI menus in YAML files. Each file declares one menu: its
title, size, layout, per-slot items, click actions, and optional conditions. Menus can be opened by command, by
right-clicking a unique item, or opened from another menu's click action. A `multi-inventory` type handles
paginated lists with automatic next/previous/home navigation.

As of 0.10.0, `gangland-ui/inventory-api` is gone. Every menu builds on Keystone's **`keystone-inventory`**
library (`ChestMenuBuilder`) through a thin Gangland-only YAML dialect (`InventoryParser`/`InventoryBuilder`,
`org.luckyraven.gangland.menu.*` in `gangland-impl`) — **the YAML schema documented below is unchanged**: no
menu file needs editing for this migration. For the Java-level picture (the two layers, multi-screen flows, the
item-return contract), see [`documentation/developer/ui-framework.md`](../developer/ui-framework.md) — this page
covers only what a server owner or menu author writes in YAML.

---

## Inventory Types

| Type              | Description                                                                              |
|-------------------|-------------------------------------------------------------------------------------------|
| `inventory`       | Standard single-page GUI. Slots are defined statically in the YAML.                       |
| `multi-inventory` | Paginated GUI. Items are sourced dynamically at runtime and split across multiple pages. |

---

## File Structure

Each menu is a separate YAML file placed in the inventories resource directory. The plugin loads every file in
that directory on startup.

### Top-level sections

| Section        | Required | Description                                                       |
|----------------|----------|---------------------------------------------------------------------|
| `Information`  | Yes      | Metadata: name, title, size, type, opening triggers, layout flags |
| `Slots`        | No       | Per-slot item and action definitions (standard inventories)       |
| `Static_Items` | No       | Persistent slots shown on every page of a `multi-inventory`       |

### `Information` fields

| Field                               | Type    | Description                                                              |
|--------------------------------------|---------|----------------------------------------------------------------------------|
| `Name`                               | String  | Internal key used to reference this menu. Defaults to filename.          |
| `Display_Name`                       | String  | Title shown in the menu. Supports color codes and placeholders.          |
| `Size`                                | Integer | Number of slots. Rounded up to the nearest multiple of 9 (max 54).        |
| `Type`                                | String  | `inventory` or `multi-inventory`.                                        |
| `Permission`                         | String  | Optional permission node. Players without it cannot open this menu.      |
| `Open.Command`                       | String  | Sub-command that opens this menu (registered under `/glw`).              |
| `Open.Event.OnItemClick.UniqueItem`  | String  | Unique item key that triggers this menu on right-click.                  |
| `Open.Event.OnItemClick.Action`      | List    | Bukkit `Action` values that count as a trigger (default: right-clicks).  |
| `Open.Event.Permission`              | String  | Permission required to open via the item event.                          |

### `Configuration` fields (inside `Information`)

| Field             | Type         | Description                                                  |
|-------------------|--------------|----------------------------------------------------------------|
| `Fill`            | Boolean      | Fill all empty slots with the configured fill item.          |
| `Border`          | Boolean      | Place the fill item along the outer border only.             |
| `Line.Vertical`   | Integer list | Column indices (0–8) where a vertical divider line is drawn. |
| `Line.Horizontal` | Integer list | Row indices (0–5) where a horizontal divider line is drawn.  |

### Full skeleton

```yaml
Information:
   Name: "my_menu"
   Display_Name: "&8» &6My Menu"
   Size: 54
   Type: "inventory"
   Permission: "gangland.menu.mymenu"

   Open:
      Command: "mymenu"

   Configuration:
      Fill: false
      Border: true
      Line:
         Vertical: []
         Horizontal: []

Slots:
   0:
      Item: DIAMOND
      Name: "&bExample"
      Lore:
         - "&7Click me!"
      Enchanted: false
      Draggable: false
      OnClick:
         Command: "glw somecommand"
```

---

## Slots

Slots are defined under the `Slots` section, keyed by their zero-based inventory index (0 = top-left).

```yaml
Slots:
   4:
      Item: GOLD_INGOT
      Name: "&6Bank"
      Lore:
         - "&7Balance: &e%gangland_bank_balance%"
      Enchanted: false
      Draggable: false
```

Use an `Item` sub-section to supply a `Color` or `Data` value resolved via PlaceholderAPI at open time:

```yaml
Slots:
   13:
      Item:
         Type: WHITE_WOOL
         Color: "%gangland_gang_color%"
      Name: "&fGang Color"
```

| Field       | Type         | Description                                                         |
|-------------|--------------|-----------------------------------------------------------------------|
| `Item`      | String / Map | Material name, or a map with `Type`, `Color`, and/or `Data`.        |
| `Name`      | String       | Display name. Supports color codes and PlaceholderAPI placeholders. |
| `Lore`      | String list  | Lore lines. Each line supports color codes and placeholders.        |
| `Enchanted` | Boolean      | Adds a hidden enchantment glow effect.                              |
| `Draggable` | Boolean      | Whether players can drag this item out of the menu.                 |

---

## Event Triggers on Slots

| Event key     | Fires when                                                 |
|---------------|---------------------------------------------------------------|
| `OnClick`     | Player left-clicks the slot inside the menu.               |
| `OnInteract`  | Player interacts with the menu (left or right click).      |
| `OnClose`     | Player closes the menu.                                    |
| `OnItemClick` | Player right-clicks a physical block or air with the item. |
| `OnDrop`      | Player drops the item from their hand.                     |
| `OnSwapHand`  | Player swaps the item to their off-hand (F key).            |
| `OnJoin`      | Player joins the server.                                    |
| `OnQuit`      | Player leaves the server.                                   |

A separate `OnRightClick` key can be added alongside any menu event to provide a distinct action for right-clicks
on that slot.

## Click Actions

Every event section must contain exactly one of the following action types.

```yaml
# Run a command as the player (omit the leading /)
OnClick:
   Command: "glw gang info"

# Open another menu by its internal Name
OnClick:
   Inventory: "gang_settings"

# Open an anvil text-input; %gangland_anvil_output% resolves to what the player typed
OnClick:
   Inventory:
      Type: anvil
      Title: "&8Enter Name"
      Text: "Type here..."
      Success:
         Command: "glw gang setname %gangland_anvil_output%"
```

---

## Conditional Slots

A `Condition` section on a slot shows different items and triggers different actions depending on whether a
PlaceholderAPI expression evaluates to true or false. Each branch (`True` / `False`) supports all the same
fields as a normal slot — `Item`, `Name`, `Lore`, `Enchanted`, `Draggable`, click actions, and even a nested
`Condition` for chained logic.

```yaml
Slots:
   22:
      Item: LIME_DYE
      Name: "&aGang Status"
      Condition:
         Value: "%gangland_is_in_gang%"
         True:
            Item: LIME_DYE
            Name: "&aIn a Gang"
            OnClick:
               Command: "glw gang info"
         False:
            Item: GRAY_DYE
            Name: "&7No Gang"
            OnClick:
               Inventory: "create_gang"
```

| Expression value      | Evaluates to |
|------------------------|--------------|
| `true`, `yes`, `1`    | `true`       |
| `false`, `no`, `0`    | `false`      |
| Any non-empty string  | `true`       |
| Empty string           | `false`      |
| Numeric `> 0`          | `true`       |
| Numeric `<= 0`         | `false`      |

---

## Paginated Menus

Set `Type: "multi-inventory"` to split a dynamic item list across multiple pages automatically.

```yaml
Information:
   Name: "gang_members"
   Display_Name: "&8» &eGang Members"
   Size: 54
   Type: "multi-inventory"
   Open:
      Command: "members"
   Configuration:
      Border: true

   Multi:
      Item_Source: "gang_members"
```

`Multi.Item_Source` selects a registered `ItemSourceProvider`; page size is driven by Keystone's own
`PageConfig`/`PagedRegion` arithmetic, not a YAML override — **`Multi.Per_Page` no longer exists** (it was
already unread by the old engine and was removed outright at the 0.10.0 CUT gate, docket T-43).
Next/previous/home nav buttons use fixed built-in head textures (`ButtonTags.DEFAULT` in
`org.luckyraven.gangland.menu.part`) — the old `settings.yml` `Inventory.Multi_Inventory` block that used to
configure these textures was deleted at the same gate, so they are no longer YAML-configurable.

`Static_Items` defines slots that appear on every page alongside the dynamic content, using the same item and
action fields as `Slots`.

---

## Opening Menus

A menu opens via `Open.Command` (`/glw <command>`), via `Open.Event` (holding a unique item and right-clicking —
see the `Information` fields table above), or from another menu's `OnClick`/`OnRightClick` action
(`Inventory: "<name>"`).

---

## Beyond YAML: flows, item-return, and loot chests

A handful of screens are not built from this YAML dialect at all:

- **Multi-screen flows** (trader buy/barter, banker menus, turf powerup, shop admin) are plain Java, built on
  Keystone's `MenuFlow<S>` / `Panel<S extends FlowState>` — the replacement for the deleted
  `MultiPanelInventory`/`Panel`/`FlowSession` trio. See ui-framework.md's "Multi-screen flows" section for the
  shape and an example.
- Any menu with an **interactive/draggable slot** (a slot a player can put items into) must return those items on
  close via Keystone's `ItemHoldingComponent`, or model the slot as a live-inventory dropzone that never snapshots
  it in the first place — see ui-framework.md's "The item-return contract" section before adding a new drop
  target.
- The **loot chest** opening view is not a menu at all — it's a raw shared Bukkit `Inventory`
  (`SharedLootInventory`, `gangland-features/gangland-lootchest`) with a **take-only** policy: every deposit-shaped
  click or drag onto the chest is cancelled, every take path is left alone. See ui-framework.md's "Loot Chest +
  Inventory" section.

For the full developer-facing picture (the two layers, `InventoryBuilder`'s build methods, `SimplePagedMenu`, the
module dependency graph), read
[`documentation/developer/ui-framework.md`](../developer/ui-framework.md).

---

[← Database & Setup](./database.md) | [Back to Index](../README.md)
