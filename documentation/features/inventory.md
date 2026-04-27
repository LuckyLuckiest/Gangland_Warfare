# Inventory System

[← Database & Setup](./database.md) | [Back to Index](../README.md)

---

## Overview

The inventory system lets you define fully custom GUI menus in YAML files. Each file declares one inventory:
its title, size, layout, per-slot items, click actions, and optional conditions. Inventories can be opened by
command, by right-clicking a unique item, or opened from another inventory's click action. A special
`multi-inventory` type handles paginated lists with automatic next/previous/home navigation.

---

## Inventory Types

| Type              | Description                                                                              |
|-------------------|------------------------------------------------------------------------------------------|
| `inventory`       | Standard single-page GUI. Slots are defined statically in the YAML.                      |
| `multi-inventory` | Paginated GUI. Items are sourced dynamically at runtime and split across multiple pages. |

---

## File Structure

Each inventory is a separate YAML file placed in the inventories resource directory. The plugin loads every
file in that directory on startup.

### Top-level sections

| Section        | Required | Description                                                       |
|----------------|----------|-------------------------------------------------------------------|
| `Information`  | Yes      | Metadata: name, title, size, type, opening triggers, layout flags |
| `Slots`        | No       | Per-slot item and action definitions (standard inventories)       |
| `Static_Items` | No       | Persistent slots shown on every page of a `multi-inventory`       |

### `Information` fields

| Field                               | Type    | Description                                                              |
|-------------------------------------|---------|--------------------------------------------------------------------------|
| `Name`                              | String  | Internal key used to reference this inventory. Defaults to filename.     |
| `Display_Name`                      | String  | Title shown in the inventory UI. Supports color codes and placeholders.  |
| `Size`                              | Integer | Number of slots. Rounded up to the nearest multiple of 9 (max 54).       |
| `Type`                              | String  | `inventory` or `multi-inventory`.                                        |
| `Permission`                        | String  | Optional permission node. Players without it cannot open this inventory. |
| `Open.Command`                      | String  | Sub-command that opens this inventory (registered under `/glw`).         |
| `Open.Event.OnItemClick.UniqueItem` | String  | Unique item key that triggers this inventory on right-click.             |
| `Open.Event.OnItemClick.Action`     | List    | Bukkit `Action` values that count as a trigger (default: right-clicks).  |
| `Open.Event.Permission`             | String  | Permission required to open via the item event.                          |

### `Configuration` fields (inside `Information`)

| Field             | Type         | Description                                                  |
|-------------------|--------------|--------------------------------------------------------------|
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

### Simple item

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

### Complex item — colored or dynamic material

Use an `Item` sub-section to supply a `Color` or `Data` value resolved via PlaceholderAPI at open time.

```yaml
Slots:
   13:
      Item:
         Type: WHITE_WOOL
         Color: "%gangland_gang_color%"
      Name: "&fGang Color"
      Lore:
         - "&7Your gang's color"
```

### Item fields

| Field       | Type         | Description                                                         |
|-------------|--------------|---------------------------------------------------------------------|
| `Item`      | String / Map | Material name, or a map with `Type`, `Color`, and/or `Data`.        |
| `Name`      | String       | Display name. Supports color codes and PlaceholderAPI placeholders. |
| `Lore`      | String list  | Lore lines. Each line supports color codes and placeholders.        |
| `Enchanted` | Boolean      | Adds a hidden enchantment glow effect.                              |
| `Draggable` | Boolean      | Whether players can drag this item out of the inventory.            |

---

## Event Triggers on Slots

An event key attached to a slot determines *when* its action fires. Only one event key is read per slot.

| Event key     | Fires when                                                 |
|---------------|------------------------------------------------------------|
| `OnClick`     | Player left-clicks the slot inside the inventory.          |
| `OnInteract`  | Player interacts with the inventory (left or right click). |
| `OnClose`     | Player closes the inventory.                               |
| `OnItemClick` | Player right-clicks a physical block or air with the item. |
| `OnDrop`      | Player drops the item from their hand.                     |
| `OnSwapHand`  | Player swaps the item to their off-hand (F key).           |
| `OnJoin`      | Player joins the server.                                   |
| `OnQuit`      | Player leaves the server.                                  |

A separate `OnRightClick` key can be added alongside any inventory event to provide a distinct action for
right-clicks on that slot.

```yaml
Slots:
   20:
      Item: PAPER
      Name: "&fInfo"
      OnClick:
         Command: "glw info"
      OnRightClick:
         Command: "glw info detailed"
```

---

## Click Actions

Every event section must contain exactly one of the following action types.

### Run a command

The command is run as the player. Omit the leading `/`.

```yaml
OnClick:
   Command: "glw gang info"
```

### Open another inventory

Pass the target inventory's internal `Name` as a string:

```yaml
OnClick:
   Inventory: "gang_settings"
```

### Open an anvil text-input

Opens the AnvilGUI dialog. On confirm, `%gangland_anvil_output%` is replaced with whatever the player typed.

```yaml
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
PlaceholderAPI expression evaluates to true or false.

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
            Lore:
               - "&7Gang: &e%gangland_gang_name%"
            OnClick:
               Command: "glw gang info"
         False:
            Item: GRAY_DYE
            Name: "&7No Gang"
            Lore:
               - "&7Click to create one."
            OnClick:
               Inventory: "create_gang"
```

Each branch (`True` / `False`) supports all the same fields as a normal slot — `Item`, `Name`, `Lore`,
`Enchanted`, `Draggable`, click actions, and even a nested `Condition` for chained logic.

### Condition evaluation rules

| Expression value     | Evaluates to |
|----------------------|--------------|
| `true`, `yes`, `1`   | `true`       |
| `false`, `no`, `0`   | `false`      |
| Any non-empty string | `true`       |
| Empty string         | `false`      |
| Numeric `> 0`        | `true`       |
| Numeric `<= 0`       | `false`      |

---

## Paginated Inventories

Set `Type: "multi-inventory"` to split a dynamic item list across multiple pages automatically.

### `Multi` fields (inside `Information`)

| Field               | Type    | Default | Description                                          |
|---------------------|---------|---------|------------------------------------------------------|
| `Multi.Item_Source` | String  | —       | Key identifying the data source that provides items. |
| `Multi.Per_Page`    | Integer | `28`    | How many dynamic items appear per page.              |

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
      Per_Page: 28
```

Navigation buttons (previous page, home, next page) are added automatically using the skull textures
configured in `settings.yml`.

### Static items

`Static_Items` defines slots that appear on every page alongside the dynamic content. Supports the same
item and action fields as `Slots`.

```yaml
Static_Items:
   49:
      Item: BARRIER
      Name: "&cClose"
      OnClick:
         Command: "glw inventory close"
```

---

## Opening Inventories

### Via command

When `Open.Command` is set, the inventory opens when the player runs `/glw <command>`.

```yaml
Open:
   Command: "mymenu"
```

The player types `/glw mymenu` to open the inventory.

### Via unique item

When `Open.Event` is configured, holding the specified unique item and right-clicking opens the inventory.

```yaml
Open:
   Event:
      OnItemClick:
         UniqueItem: "gang_phone"
         Action:
            - RIGHT_CLICK_AIR
            - RIGHT_CLICK_BLOCK
      Permission: "gangland.use.gangphone"
```

If no `Action` list is provided, both `RIGHT_CLICK_AIR` and `RIGHT_CLICK_BLOCK` are used by default.

### From another inventory's click action

Any slot's `OnClick` or `OnRightClick` can open a different inventory by name:

```yaml
OnClick:
   Inventory: "gang_settings"
```

---

## API Usage

The sections below are for developers working with the inventory system in Java code.

### `InventoryHandler` — creating inventories programmatically

`InventoryHandler` wraps a Bukkit `Inventory` and manages per-slot click handlers.

```java
// Player-owned inventory (registered in InventoryRegistry)
InventoryHandler handler = new InventoryHandler(plugin, "&6My Menu", 54, player);

// Add a static item with no click action
handler.

setItem(4,Material.DIAMOND, "&bTitle",List.of("&7Lore line"), false,false);

// Add a clickable item — left-click only
ItemBuilder item = new ItemBuilder(Material.GOLD_INGOT).setDisplayName("&6Click me");
handler.

setItem(10,item, false,(p, inv, builder) ->{
		p.

sendMessage("You clicked!");
});

// Add a clickable item — separate left and right handlers
		handler.

setItem(20,item, false,
		(p, inv, builder) ->p.

sendMessage("Left click"),
    (p,inv,builder)->p.

sendMessage("Right click")
);

// Open for the player
		handler.open(player);

// Remove an item
		handler.removeItem(10);

// Update the title
		handler.rename(plugin,"&6New Title");

// Unregister when done
		handler.unregister();
```

`InventoryHandler` implements `Listener` — it is registered with Bukkit automatically when opened and
deregistered via `unregister()`.

### `InventoryHandler` — key methods

| Method                                        | Description                                               |
|-----------------------------------------------|-----------------------------------------------------------|
| `setItem(slot, item, draggable, click)`       | Place an item with an optional left-click handler.        |
| `setItem(slot, item, draggable, left, right)` | Place an item with separate left and right handlers.      |
| `removeItem(slot)`                            | Clear a slot and remove its handlers.                     |
| `open(player)`                                | Open the inventory for the given player.                  |
| `close(player)`                               | Close the inventory for the given player.                 |
| `rename(plugin, name)`                        | Update the inventory title in place.                      |
| `unregister()`                                | Remove from `InventoryRegistry`.                          |
| `itemOccupied(slot)`                          | Returns `true` if a slot already has an item.             |
| `clear()`                                     | Clear all items from the inventory.                       |
| `getSize()`                                   | Returns the inventory size (always a multiple of 9).      |
| `getOwner()`                                  | Returns the UUID of the player this inventory belongs to. |

### `InventoryBuilder` — building from `InventoryData`

`InventoryBuilder` is used when constructing an inventory from a parsed YAML config. For standard
programmatic use, create `InventoryHandler` directly. Use `InventoryBuilder` when you already have an
`InventoryData` object (e.g. a loaded config):

```java
InventoryData data = ...; // populated from YAML or constructed manually
InventoryBuilder builder = new InventoryBuilder(data, "gangland.inv.mymenu");

Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());
Fill line = new Fill(Settings.getInventoryLineName(), Settings.getInventoryLineItem());
InventoryOpener opener = inventoryRuntimeContext::openInventoryForPlayer;

InventoryHandler handler = builder.createInventory(plugin, placeholder, player, fill, line, evaluator, opener);
handler.open(player);
```

### `MultiInventory` — paginated inventories

`MultiInventory` extends `InventoryHandler` and manages a `LinkedList` of pages.

```java
MultiInventory multi = MultiInventoryCreation.dynamicMultiInventory(
		plugin,
		player,
		items,          // List<ItemStack> — the dynamic content
		"&8Members",    // title
		true,           // include static items
		true,           // border
		fill,
		buttonTags,
		staticItemsMap  // Map<ItemStack, TriConsumer> for persistent slots
);

multi.open(player);

// Navigation (also wired to the nav buttons automatically)
		multi.nextPage();
		multi.previousPage();
		multi.homePage();

// Refresh content (e.g. after data changes)
		multi.updateItems(plugin,newItems,player,true,fill);
```

### `MultiPanelInventory` — seamless multi-screen flows

`MultiPanelInventory<S>` is the enhancement layered on top of the seamless in-place updates that
`InventoryHandler.rename()` and `MultiInventory.updateItems()` already provide. Where those two
swap *content* inside one fixed window, `MultiPanelInventory` keeps a single open window across a
sequence of fully distinct screens (`Panel<S>`) — each with its own size, title, and layout — while
threading one typed `FlowSession` through every screen.

Use it instead of the legacy `player.closeInventory(); next.open(player)` pattern. The session
survives panel swaps, the back-stack is built in, and side-effect detours (anvil prompts, chat input,
external GUIs) are first-class via `suspend()` / `resume()`.

#### When to use

- A feature has multiple GUI screens (menu → list → detail → confirm) that share state.
- A user-driven action triggers an anvil text prompt, then returns to the same flow.
- You want one back button to walk the player back through previously opened screens.

For a single, self-contained menu — keep using `InventoryHandler` directly. For a paginated list of
items — keep using `MultiInventory`.

#### Concepts

| Type                           | Role                                                                                               |
|--------------------------------|----------------------------------------------------------------------------------------------------|
| `FlowSession`                  | Marker interface for the typed payload threaded through the flow. Implement it on your own record. |
| `Panel<S extends FlowSession>` | One screen. Owns `size(session)`, `title(session)`, and `render(host, handler, viewer, session)`.  |
| `MultiPanelInventory<S>`       | Host. Holds the registered panels, the back-stack, and the open `InventoryHandler`.                |

#### Lifecycle

| Method                | Behavior                                                                                               |
|-----------------------|--------------------------------------------------------------------------------------------------------|
| `register(id, panel)` | Add a panel under a string id. Returns `this` for chaining.                                            |
| `openAt(id)`          | Open the flow at the given panel; registers the close listener on first open.                          |
| `switchTo(id)`        | Push the current panel onto the back-stack and switch to `id`.                                         |
| `back()`              | Pop the back-stack and switch to the previous panel; calls `end()` if the stack is empty.              |
| `rerender()`          | Re-run the current panel's render against the same inventory handle (use after session state changes). |
| `suspend()`           | Pause the flow's close listener while an external UI (anvil, chat) takes over the screen.              |
| `resume()`            | Resume after `suspend()`; call before re-entering a panel from the side-effect's completion callback.  |
| `end()`               | Close the inventory, fire `onEnd`, and unregister the close listener.                                  |
| `onEnd(callback)`     | Register a `Consumer<S>` that runs when the flow ends (natural close, `back()` past root, or `end()`). |

#### Seamless re-render vs. rebuild

Bukkit forbids resizing an open inventory, so `switchTo` takes one of two paths automatically:

- **Re-render in place** — when the target panel's `size` and `title` match the current handle, the host
  clears the slots and re-runs `panel.render(...)`. The viewer's screen never flickers.
- **Rebuild** — when size or title differ, the host builds a new `InventoryHandler`, renders into it,
  and opens it for the viewer. An internal `suppressClose()` latch prevents the close event Bukkit
  fires during the swap from tearing the session down.

#### Example

```java
public record TraderFlowSession(Trader trader, Player buyer) implements FlowSession { }

MultiPanelInventory<TraderFlowSession> flow =
		new MultiPanelInventory<>(plugin, player, new TraderFlowSession(trader, player))
				.register("menu", new TraderMenuPanel())
				.register("buy", new TraderBuyPanel())
				.register("barter", new TraderBarterPanel())
				.onEnd(session -> trader.releaseHold(session.buyer()));

flow.openAt("menu");

// Inside a panel's render method, wire a slot to:
handler.setItem(13, buyButton, false, (p, inv, item) -> flow.switchTo("buy"));
handler.setItem(22, backButton, false, (p, inv, item) -> flow.back());

// Anvil detour from inside a panel:
flow.suspend();
new AnvilGUI.Builder()
		.onClick((slot, snapshot) -> {
			flow.resume();
			flow.session().setBidAmount(parse(snapshot.getText()));
			flow.switchTo("buy");
			return List.of(AnvilGUI.ResponseAction.close());
		})
		.open(player);
```

---

[← Database & Setup](./database.md) | [Back to Index](../README.md)
