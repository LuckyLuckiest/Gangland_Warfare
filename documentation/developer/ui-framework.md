# UI Framework

## Overview

`gangland-ui/inventory-api` was deleted outright at the 0.10.0 WS2 CUT gate — every menu in this repo now builds
on Keystone's `keystone-inventory` library plus a thin Gangland-only YAML dialect living in `gangland-impl`'s own
`org.luckyraven.gangland.menu.*` package. Three independent modules remain under `gangland-ui/`:

| Module           | Package                              | Classes | Purpose                                    |
|------------------|--------------------------------------|---------|--------------------------------------------|
| `sign-api`       | `org.luckyraven.gangland.sign`       | ~25     | Interactive sign placement and interaction |
| `lootchest-api`  | `org.luckyraven.gangland.lootchest`  | ~35     | Loot chest sessions with cracking minigame |
| `hologram-api`   | `org.luckyraven.gangland.hologram`   | 3       | Floating text via invisible armor stands   |

All modules are event-driven using Bukkit listeners. Listeners are annotated with `@ListenerHandler` for
auto-registration via the `DependencyContainer` scan.

---

## Inventory System

As of 0.10.0 (WS2 CUT gate), the old `gangland-ui/inventory-api` module is gone. Every menu in this repo builds
on Keystone's **`keystone-inventory`** library (`org.luckyraven.keystone.inventory.*`, `provided` scope, declared
directly by any pom that builds a menu — never re-exported by `gangland-api`, R6: the `bartizan-api` rule) plus a
thin Gangland-only YAML dialect that lives in `gangland-impl`'s own `org.luckyraven.gangland.menu.*` package
(moved there at WS2 G3a, retargeted onto `ChestMenuBuilder` at G3). See
`E:\Programming\java\wt\keystone-1.11.0\docs\keystone-inventory.md` in the Keystone repo for the full consumer
guide this section summarizes.

### Two layers

```
Keystone (org.luckyraven.keystone.inventory.*)
    ChestMenu / ChestMenuBuilder       — builds one menu: slots, fill/border/line decoration
    ItemComponent / FillComponent /
      BorderComponent / LineComponent  — per-slot/region primitives
    ClickContext / ClickHandler        — ctx.player(), ctx.closeMenu(), ctx.menu(); the click callback shape
    PageConfig / PagedRegion           — pagination math + rendering a list into a fixed interior grid
    MenuFlow<S> / Panel<S> / FlowState — multi-screen flows (was MultiPanelInventory / Panel / FlowSession)
    InventoryService / OpenMenuTracker — the per-consumer bean; tracks each player's one currently-open menu
    MenuOpener / MenuRegistry          — name -> menu lookup for command/sign-driven opens
    ItemHoldingComponent               — the item-return contract (see below)

Gangland's own YAML dialect (gangland-impl, org.luckyraven.gangland.menu.*)
    InventoryBuilder (record)  — builds a ChestMenu / paginated ChestMenu from parsed InventoryData
    InventoryData / Slot / OpenInventory / State
    menu.condition.*   — the %placeholder%-driven True/False slot-condition tree
    menu.filter.*       — canonical filter-button click handlers (sort/clear/cycle-enum)
    menu.handler.*      — YAML OnClick/OnInteract -> ClickHandler dispatch
    menu.multi.*         — ItemSourceProvider/ItemSourceEntry (paginated item sources)
    menu.part.*          — ButtonTags, ConditionalSlotResult, Slot
    menu.unique.*        — UniqueItemHandler (Open.Event.UniqueItem wiring)
    menu.villager.*      — the native-Bukkit-Merchant wrapper (unrelated to ChestMenu; unchanged since G3a)
    SimplePagedMenu       — a small static helper for a flat List<ItemStack> paginated view with no YAML
                            behind it (see below)
```

Every core menu is built **fresh** per open (`InventoryRuntimeContext.openInventoryForPlayer`) — there is no
cached `MenuRegistry` factory for YAML menus, because the real viewing `Player` is always in hand, and paginated
menus need it before `.build()` to fetch that player's filtered/sorted item source. `MenuOpener` (a plain
`(Player, String) -> void` functional interface, structurally identical to the deleted `InventoryOpener`) is what
YAML `OnClick.Inventory: <name>` navigation and command/sign-triggered opens both resolve against — Keystone's
own `MenuRegistry` is not populated by this dialect at all (a deliberate WS2 G3 choice: a registered
`Supplier<Menu>` factory is zero-arg and cannot supply the player a paginated menu's item-source fetch needs).

### `InventoryBuilder`

`InventoryBuilder(InventoryData inventoryData, String permission)` — a record, not the old `InventoryHandler`
wrapper — exposes two build methods:

```java
ChestMenu createMenu(InventoryService inventoryService, JavaPlugin plugin, Placeholder placeholder, Player player,
                     String fillMaterial, String fillName, String lineMaterial, String lineName,
                     ConditionEvaluator evaluator, MenuOpener opener);

ChestMenu createPagedMenu(InventoryService inventoryService, JavaPlugin plugin, Placeholder placeholder,
                          Player player, ConditionEvaluator evaluator, String fillMaterial, String fillName,
                          ButtonTags buttonTags, ItemSourceProvider itemSourceProvider, MenuOpener opener, int page);
```

The old `Fill` record (`org.luckyraven.gangland.inventory.part.Fill`, a 2-field `(name, material)` wrapper) was
deleted along with the rest of `inventory-api` and is **not** replaced by an equivalent type in `menu.part` — call
sites pass the material and name as two plain `String`s instead. `InventoryBuilder.DEFAULT_FILL_ITEM` /
`DEFAULT_FILL_NAME` / `DEFAULT_LINE_ITEM` / `DEFAULT_LINE_NAME` are literal copies of the `settings.yml`
`Inventory.Fill`/`Inventory.Line` block's shipped defaults, used by every core dialect call site that used to
build a `Fill` from `Settings.getInventoryFillItem()` etc. `ButtonTags.DEFAULT` is the equivalent for the three
paginated-nav-button head textures. **That `settings.yml` `Inventory:` block itself was *not* deleted** — despite
the original WS2 plan's text, it is still load-bearing for many already-shipped G4/G5/WS4 files outside the CUT
gate's scope (Banker/Trader views, `TurfModuleConfig`, `ShopAdminView` — see the CUT report's deviation note for
the full list); it stays live for everyone who still reads it, the core dialect simply no longer does.

Row/column arithmetic (`computeRows`), border/fill/line priority, and per-slot NBT-tag resolution (`color`/`head`
tags) are all ported 1:1 from the deleted `InventoryHandler`/`InventoryUtil` — see `InventoryBuilder`'s own
javadoc for exact provenance notes per method.

### `SimplePagedMenu`

A no-static-items, no-per-entry-click paginated `ChestMenu` of plain `ItemStack`s — the shape three real call
sites built via the old `MultiInventoryCreation.dynamicMultiInventory(...)` call: `DebugCommand`'s `multi` debug
argument, `GangCommand`'s member/ally lists, and `BountyAspect.openBountyView`. (`GangCommand` also had a fourth,
`gangStat()`/`itemToBalance()` — found to be genuinely dead code with zero callers anywhere in the reactor at the
CUT gate, and deleted rather than ported; `/glw gang info` is served by the YAML-driven `gang_info.yml` menu
through the generic dialect above, not that method.) Next/prev/home buttons rebuild the target page and swap it
into the already-open menu via `ChestMenu#adoptComponentsFrom` — no close/reopen flicker, a free improvement over
the old chained-Bukkit-inventory model.

```java
SimplePagedMenu.open(InventoryService inventoryService, Player player, List<ItemStack> items, String title,
                     String fillMaterial, String fillName, ButtonTags buttonTags);
```

### `Multi.*` YAML pagination (`Type: multi-inventory`)

Rebuilt on Keystone's `PageConfig`/`PagedRegion` at WS2 G3 — the old chained-Bukkit-inventory model
(`MultiInventory`/`MultiInventoryCreation`/`MultiInventoryNavigation`, all deleted) is now one fixed-size
`ChestMenu` whose interior grid renders per page. `Information.Multi.Item_Source` still selects an
`ItemSourceProvider` registration; **`Multi.Per_Page` is gone** (docket T-43, fixed at the CUT gate — it was
parsed into `InventoryData.perPage` but never read even before WS2 G3's rebuild, and `PageConfig`'s own
arithmetic drives page size, not a YAML override; the dead key was removed from `alliance_stat.yml`,
`phone_gang_search.yml` and `user_stat.yml`, and the dead field from `InventoryData`/`InventoryParser`).

### The item-return contract (interactive/draggable slots)

None of the 9 core YAML menus declare `Draggable: true` (grep-verified, round-trip-tested), so the core dialect
never needs to think about this — but any consumer that builds an `interactive` slot (`builder.interactive(slot)`
/ `ItemComponent.interactive(true)`) **must** either model it as an `ItemHoldingComponent` or return the slot's
contents itself on close. Keystone's `InventoryService`/`ChestMenu.close(...)` only auto-returns items sitting in
a declared `ItemHoldingComponent` on close (Escape, disconnect, `/glw reload`, a differently-sized/titled
`MenuFlow.switchTo`) — a plain `interactive` slot with no such component silently discards whatever the player
physically has in it. This is WS2 §0d's consumer rule, and it drove two real design decisions in this wave:

- **`DropzoneSlotComponent`** (`gangland-features/gangland-npc-shops/.../trader/view/DropzoneSlotComponent.java`)
  — shared by `BarterView`/`SellView`'s 20 dropzone slots. Its `renderInto` calls only `view.interactive(true)`,
  never `setItem` — a render pass never touches whatever the player physically has in the slot, because Bukkit's
  own uncancelled click/drag handling already writes straight into the live `Inventory`, which *is* the state, so
  there is nothing to snapshot or restore. `heldItems(player)`/`clearHeld(player)` read/clear that same live slot
  directly, captured via `RenderContext.menu()` at render time. **Copy this pattern** — not a `setItem`-based
  "snapshot and restore" dance — for any new drop-target slot that must survive a close.
- `ShopAdminView`'s BUY-tab drop target and `SellCategoryItemsAdminView`/`BarterCategoryItemsAdminView`'s item
  grids are **not** item-holding at all: `event.setCancelled(true)` fires before the drop physically applies, the
  source item is read via `event.getCursor()`/`event.getCurrentItem()` and cloned, and the original item never
  leaves the player. Read every click handler before assuming a drop-target slot needs `ItemHoldingComponent` —
  most of this codebase's "drop an item onto a slot to add it" UIs are peek-and-clone, not item-holding.

### YAML menus

The 9 core menus under `plugins/Gangland_Warfare/menus/` (`gangland-impl/src/main/resources/inventory/*.yml`)
load through an **unchanged schema** — WS2 kept Gangland's own YAML shape rather than migrating onto a foreign
one at any point. `Information.{Name,Display_Name,Size,Permission,Type,Open}`,
`Information.Configuration.{Fill,Border,Line}`, `Slots.<N>.{Item,Name,Lore,Enchanted,Draggable,Condition,
OnClick,OnInteract}`, `Static_Items`, `Information.Multi.Item_Source`, `Information.Item_Template` all parse
exactly as before (`InventoryRuntimeContext.registerInventory` / `InventoryParser`), just building a `ChestMenu`
instead of the deleted `InventoryHandler`.

### `/glw debug inv-data`

Reads `InventoryService.tracker()` (Keystone's `OpenMenuTracker`) live — one tracked menu per player, not the old
model's several-simultaneously-registered `InventoryHandler`s. `/glw debug inv-data special` (which used to list
`InventoryHandler.SPECIAL_INVENTORIES`, a static per-key registry) is gone — `keystone-inventory`'s per-open
`ChestMenu` model has no equivalent singleton registry to list (docket-recorded fixed-by-WS2, along with the
static `InventoryRegistry` seam `RemoveAccountListener`/`KernelConfig` used to wire and clear on quit/reload).

### Multi-screen flows (`MenuFlow<S>` / `Panel<S>` / `FlowState`)

Replaces the deleted `org.luckyraven.gangland.inventory.flow.{MultiPanelInventory, Panel, FlowSession}` trio with
Keystone's `org.luckyraven.keystone.inventory.flow.{MenuFlow, Panel, FlowState}` — same shape, different package:

```java
public interface FlowState { }   // marker — feature modules implement on their own session record

public interface Panel<S extends FlowState> {
	int rows(S session);            // was size(session) — pixel count -> row count

	String title(S session);

	void render(MenuFlow<S> flow, ChestMenuBuilder builder, S session);   // was (host, InventoryHandler, viewer, session)
}
```

```java
MenuFlow<TraderFlowSession> flow = MenuFlow.builder(inventoryService, plugin, viewer, session)
                                           .panel(TraderFlowSession.PANEL_MODE_SELECT, modeSelectPanel)
                                           .panel(TraderFlowSession.PANEL_SHOP, shopPanel)
                                           .onEnd(s -> sellPanel.onFlowEnd(viewer))   // fixed at build time —
                                           .build();                                   // unlike the old per-render
flow.openAt(TraderFlowSession.PANEL_MODE_SELECT);                                     // onEnd registration
```

`switchTo`/`back`/`end`/`rerender`/`suspend`/`resume` are unchanged names on `MenuFlow`. The one real behavior
difference from the old host: `MultiPanelInventory#onEnd` used to be re-registered by whichever panel was entered
last (each render call could overwrite it); `MenuFlow`'s `onEnd` is a `Consumer<S>` fixed once at `.build()` time
— a flow with several panels that each need teardown work wires **one** flow-wide `onEnd` that calls every
panel's own no-op-unless-entered cleanup method (see `TraderFlow`'s `onEnd` for the pattern).

`InventoryUtil`'s old "preserve dropzone, blank-fill, restore" dance and `aroundSlot`'s "clear ring, then
recolor" dance are both **gone, not ported** — Keystone's fill/border/line primitives already skip interactive
and already-occupied (explicitly `.slot()`-assigned) slots by construction, and every `Panel.render()` call
starts from a fresh, empty `ChestMenuBuilder`, so there is no stale prior-render state to clean up first.

### `.claude/skills/panel-create/`

Retargeted at the CUT gate to scaffold Keystone's `Panel<S extends FlowState>` (the shape above), not Oriel's or
the deleted `org.luckyraven.gangland.inventory.flow.Panel<S extends FlowSession>` — the field/method names in its
generated skeletons, the `MenuFlow` wiring snippet, and the "Shared conventions" section's `Panel<SomeFlowSession>`
wording were all updated to match.

---

## Scoreboard System

Removed in 0.10.0. Scoreboard rendering is no longer part of the UI framework — it now lives in the
standalone **Plaque** plugin (`E:\Programming\java\Plaque`), which renders via PlaceholderAPI
`%gangland_*%` tokens that Gangland's placeholder system still publishes.

---

## Sign System

### Architecture

The sign system uses a **Chain of Responsibility** pattern with composable aspects:

```
SignService (abstract, initialization)
    ├── registers ──> SignTypeDefinition (bundles type + validator + parser + handler + aspects)
        ├── into ──> SignTypeRegistry (lookup by typed/generated name)

Player places sign ──> SignCreation listener
    ├── validates via ──> SignValidator / AbstractSignValidator
    ├── formats via ──> SignFormatterService

Player right─clicks sign ──> PlayerSignInteract listener
    ├── looks up ──> SignTypeRegistry.findByLine(firstLine)
    ├── parses via ──> SignParser / AbstractSignParser ──> ParsedSign
    ├── executes via ──> SignHandler / AspectBasedSignHandler
        ├── chains ──> List<SignAspect> (sorted by priority)
            ├── each produces ──> AspectResult
```

### SignType

```java
public record SignType(String typed, String generated) { }
```

- `typed`: the raw text a player writes on line 1 (e.g., `[Trade]`)
- `generated`: the formatted text displayed after validation (e.g., `&2[Trade]`)

### SignTypeDefinition

Bundles all components for a sign type:

```java

@Builder
public class SignTypeDefinition {
	private final SignType         signType;
	private final SignValidator    signValidator;
	private final SignParser       signParser;
	private final SignHandler      handler;
	private final BulkSignHandler  bulkHandler;  // optional, for shift-click bulk actions
	private final List<SignAspect> aspects;

	public List<SignAspect> getSortedAspects();  // sorted by priority (highest first)
}
```

### SignTypeRegistry

Dual-keyed registry for fast lookup:

```java
public class SignTypeRegistry {
	private final Map<String, SignTypeDefinition> definitionsByTyped;
	private final Map<String, SignTypeDefinition> definitionsByGenerated;

	public void register(SignTypeDefinition definition);

	public Optional<SignTypeDefinition> findByLine(String line);     // checks both maps

	public Optional<SignTypeDefinition> getDefinition(SignType type);

	public boolean isRegistered(String typedName);
}
```

Keys are normalized: `ChatColor.stripColor(line).toLowerCase().replaceAll("[\\[\\]]", "").trim()`

### SignFormatRegistry

Stores `SignFormat` definitions for display formatting:

```java
public class SignFormatRegistry {
	public void register(SignFormat format);

	public Optional<SignFormat> getFormat(String formatName);

	public Optional<SignFormat> getFormatByPrefix(String prefix);
}
```

### SignFormat

Defines the expected line structure for a sign type:

```java

@Builder
public class SignFormat {
	private final String                             formatName;
	private final String                             signTypePrefix;
	private final List<SignLineFormat>               lineFormats;
	private final Map<String, ConditionalLineFormat> conditionalLines;

	public SignLineFormat getLineFormat(int lineNumber);

	public boolean hasConditionalFormat(int lineNumber, String triggerValue);
}
```

Conditional formats allow different formatting rules based on values on other lines.

### Validation

**`SignValidator`** (interface) -- validates sign lines:

```java
public interface SignValidator {
	void validate(String[] lines) throws SignValidationException;

	SignType getSignType();
}
```

**`AbstractSignValidator`** -- provides standard 4-line validation:

| Line | Validation                                                    |
|------|---------------------------------------------------------------|
| 0    | Sign type (matches `typed` or `generated` name)               |
| 1    | Content (non-empty, passes `isValidContent()`)                |
| 2    | Price (valid number, non-negative, <= max price, max 8 chars) |
| 3    | Amount (valid integer, positive, <= max amount, max 8 chars)  |

Subclasses override `isValidContent(String)` and optionally `performCustomValidation(String[])`.

### Parsing

**`SignParser`** (interface):

```java
public interface SignParser {
	ParsedSign parse(String[] lines, Location location) throws SignValidationException;
}
```

**`AbstractSignParser`** -- provides helper methods:

```java
protected String cleanLine(String line);                           // strip color codes

protected double parsePrice(String line, String moneySymbol);     // extract price

protected int parseAmount(String line);                            // extract amount

protected String parseContent(String line);                        // extract content text
```

### ParsedSign (Interface + BaseParsedSign)

```java
public interface ParsedSign {
	SignType getSignType();

	String getContent();

	double getPrice();

	int getAmount();

	Location getLocation();

	String[] getRawLines();

	<T> T getMetadata(String key, Class<T> type);

	boolean hasMetadata(String key);
}
```

`BaseParsedSign` provides the standard implementation with a `Map<String, Object> metadata` for
type-specific data.

### Aspect System

**`SignAspect`** (interface) -- a modular behavior unit:

```java
public interface SignAspect {
	AspectResult execute(Player player, ParsedSign sign);

	boolean canExecute(Player player, ParsedSign sign);

	String getName();

	default int getPriority() { return 0; }  // higher = executed first
}
```

**`AspectResult`** -- execution result:

```java
public class AspectResult {
	private final boolean success;
	private final String  message;
	private final boolean continueExecution;  // if false, stops the chain

	// Factory methods:
	static AspectResult success(String message);        // success, continue chain

	static AspectResult failure(String message);        // failure, stop chain

	static AspectResult successContinue(String message); // success, continue

	static AspectResult successStop(String message);     // success, stop chain
}
```

**`AspectBasedSignHandler`** -- chains aspects in order:

```java
public class AspectBasedSignHandler implements SignHandler {
	private final List<SignAspect> aspects;

	public List<AspectResult> handle(Player player, ParsedSign sign) {
		for (SignAspect aspect : aspects) {
			if (!aspect.canExecute(player, sign)) {
				results.add(AspectResult.failure(...));
				break;  // stop chain on precondition failure
			}
			AspectResult result = aspect.execute(player, sign);
			results.add(result);
			if (!result.isContinueExecution()) break;  // stop if aspect says so
		}
		return results;
	}
}
```

### SignInteractionService

Abstract service that ties together the registry, validation, parsing, and formatting:

```java
public abstract class SignInteractionService {
	private final String               prefix;           // e.g., "[GLW]"
	private final SignTypeRegistry     registry;
	private final SignFormatterService formatterService;

	public abstract boolean handlerInteraction(Player player, ParsedSign sign);

	public void validateSign(String[] lines) throws SignValidationException;

	public Optional<ParsedSign> parseSign(String[] lines, Location location);

	public String[] formatForDisplay(String[] lines, String moneySymbol);
}
```

### SignService

Abstract initializer that registers all sign type definitions:

```java
public abstract class SignService {
	public abstract List<SignTypeDefinition> setupSigns() throws SignValidationException;

	public void initialize() {
		// calls setupSigns() and registers each definition in the registry
	}
}
```

### Listeners

**`SignCreation`** -- handles `SignChangeEvent`:

1. Checks if first line starts with the sign prefix
2. Validates via `SignInteractionService.validateSign()`
3. Formats lines for display (colors, symbols)
4. On failure, cancels the event and notifies the player

**`PlayerSignInteract`** -- handles `PlayerInteractEvent` (right-click on sign block):

1. Checks if the block is a sign with a registered type on line 1
2. Parses the sign into a `ParsedSign`
3. If player is sneaking and a `BulkSignHandler` exists, enters bulk interaction flow:
    - First shift-click: shows preview and initiates pending action
    - Second shift-click on same sign: confirms and executes bulk action
4. Otherwise, delegates to `SignInteractionService.handlerInteraction()`

**Example -- registering a custom sign type:**

```java
public class MySignService extends SignService {

	@Override
	public List<SignTypeDefinition> setupSigns() {
		SignType tradeType = new SignType("[Trade]", "&2[Trade]");

		SignTypeDefinition tradeDef = SignTypeDefinition.builder()
		                                                .signType(tradeType)
		                                                .signValidator(new TradeSignValidator(tradeType, "$"))
		                                                .signParser(new TradeSignParser(tradeType))
		                                                .handler(new AspectBasedSignHandler(List.of(
																new PermissionAspect("gangland.sign.trade"),
				                                                new BalanceCheckAspect(),
				                                                new TradeExecuteAspect()
														)))
		                                                .build();

		return List.of(tradeDef);
	}
}
```

---

## Loot Chest System

### Architecture

```
LootChestConfig (settings, tiers, loot tables)
    ├── loaded by ──> LootChestLoader

LootChestData (chest definition: location, tier, items, cooldown state)
    ├── creates ──> LootChestSession (active player session)
        ├── optionally creates ──> CrackingSession (lockpick minigame)

LootChestHandler<T> (abstract handler chain)
    ├── subclassed by:
        SessionStartHandler, SessionCompleteHandler
        ChestCooldownTickHandler, ChestCooldownCompleteHandler
        CrackingStartHandler, CrackingTickHandler,
        CrackingSuccessHandler, CrackingFailedHandler

LootChestEvent (abstract base)
    ├── Cracking events: Start, During, Success, Failure, End
    ├── Chest events: Open, Close, DuringCooldown, CooldownComplete
```

### LootChestData

Represents a placed loot chest in the world:

```java

@Builder
public class LootChestData {
	private final UUID     id;
	private final Location location;
	private final String   lootTableId;
	private final LootTier tier;
	private final long     respawnTime;
	private final int      inventorySize;
	private final String   displayName;

	// Mutable state
	private long            lastOpened;
	private boolean         isLooted;
	private long            cooldownEndTime;
	private List<ItemStack> currentInventory;  // persisted between sessions
	private int[]           currentSlotMapping;

	// Optional cracking minigame settings
	private boolean crackingEnabled;
	private long    crackingTimeSeconds;

	// Key methods
	public void markAsLooted();

	public void startCooldown(long cooldownSeconds);

	public boolean isOnCooldown();

	public long getRemainingCooldownSeconds();

	public boolean hasItemsRemaining();

	public boolean isBlocked();              // empty AND on cooldown

	public boolean canRespawn();

	public void respawn();                   // resets all state

	public void clearInventory();
}
```

### LootTier

```java
public record LootTier(
		String id,
		String displayName,
		int level,
		UnlockRequirement unlockRequirement,
		String unlockItemId          // for KEY/LOCKPICK types
) {
	public enum UnlockRequirement {
		NONE,
		LOCKPICK,
		KEY,
		PERMISSION
	}
}
```

### LootChestSession

Manages an active player-chest interaction:

```java
public class LootChestSession {
	private final UUID                sessionId;
	private final Player              player;
	private final LootChestData       chestData;
	private final SharedLootInventory inventory;   // was InventoryHandler — CUT gate, see below
	private final List<ItemStack>     generatedLoot;
	private final boolean          usingSharedInventory;
	private       int[]            slotMapping;
	private       SessionState     state;
	private       boolean          itemTaken;

	// Cracking state
	private boolean crackingRequired;
	private boolean crackingCompleted;

	public void open();              // populates inventory and opens for player

	public void markItemTaken();     // tracks that player took an item

	public void close();             // syncs inventory state back to LootChestData

	public void cancel();

	public enum SessionState {
		OPEN,
		CRACKING,
		LOOTING,
		CLOSED,
		CANCELLED
	}
}
```

**Inventory population:**

- If chest has persisted inventory from a previous session, restores it
- Otherwise, generates random slot placement from the loot table
- Items are placed at shuffled random positions
- State is synced back to `LootChestData` on close for persistence

### CrackingSession

The lockpick/cracking minigame runs on a 1-second timer:

```java
public class CrackingSession {
	private final Player        player;
	private final LootChestData chestData;
	private final LootTier      tier;
	private final long          totalTime;
	private       long          timeRemaining;
	private       int           progress;        // 0-100
	private       int           targetProgress;  // default 100
	private       CrackState    state;

	public void start(
			BiConsumer<CrackingSession, Long> onTick,     // called every second
			Consumer<CrackingSession> onSuccess,           // called on completion
			Consumer<CrackingSession> onFailed             // called on timeout
	);

	public void addProgress(int amount);    // auto-completes at target

	public void complete();                 // marks as completed

	public void cancel();                   // cancels and stops timer

	public double getProgressPercentage();  // 0.0 - 1.0

	public double getTimePercentage();      // 0.0 - 1.0

	public enum CrackState {
		PENDING,
		IN_PROGRESS,
		COMPLETED,
		FAILED,
		CANCELLED
	}
}
```

### Handler Chains

`LootChestHandler<T>` is an abstract handler that maintains a `List<Consumer<T>>`:

```java
public abstract class LootChestHandler<T> {
	public void addHandler(Consumer<T> handler);

	public void removeHandler(Consumer<T> handler);

	public void handle(T session);  // invokes all registered handlers in order
}
```

Concrete handlers (all extend `LootChestHandler`):

| Handler                        | Type Parameter     | Purpose                      |
|--------------------------------|--------------------|------------------------------|
| `SessionStartHandler`          | `LootChestSession` | Called when a session begins |
| `SessionCompleteHandler`       | `LootChestSession` | Called when a session ends   |
| `ChestCooldownTickHandler`     | `LootChestData`    | Called each cooldown tick    |
| `ChestCooldownCompleteHandler` | `LootChestData`    | Called when cooldown expires |
| `CrackingStartHandler`         | `CrackingSession`  | Called when cracking begins  |
| `CrackingTickHandler`          | `CrackingSession`  | Called each cracking tick    |
| `CrackingSuccessHandler`       | `CrackingSession`  | Called on successful crack   |
| `CrackingFailedHandler`        | `CrackingSession`  | Called on cracking failure   |

### Events

All extend `LootChestEvent` (which extends Bukkit `Event`):

**Cracking events:**

| Event                           | Cancellable | Data                         |
|---------------------------------|-------------|------------------------------|
| `LootChestCrackingStartEvent`   | Yes         | `CrackingSession`            |
| `LootChestDuringCrackingEvent`  | No          | `CrackingSession`, tick data |
| `LootChestCrackingSuccessEvent` | No          | `CrackingSession`            |
| `LootChestCrackingFailureEvent` | No          | `CrackingSession`            |
| `LootChestCrackingEndEvent`     | No          | `CrackingSession`            |

**Chest events:**

| Event                            | Data               |
|----------------------------------|--------------------|
| `LootChestOpenEvent`             | `LootChestSession` |
| `LootChestCloseEvent`            | `LootChestSession` |
| `LootChestDuringCooldownEvent`   | Cooldown data      |
| `LootChestCooldownCompleteEvent` | Completion data    |

### Configuration

**`LootChestConfig`** -- built from a `LootChestSettingsProvider`:

```java

@Builder
public class LootChestConfig {
	private final Map<String, LootTier>  tiers;
	private final Map<String, LootTable> lootTables;
	private final long                   defaultCountdownTime;
	private final String                 openingSound;
	private final String                 lockedSound;
	private final String                 closingSound;
	private final List<String>           allowedBlockTypes;
	private final Map<Rarity, Double>    globalRarityChances;
}
```

`LootChestSettingsProvider` and `LootChestMessagesProvider` are contract interfaces implemented
in `gangland-impl`, following the project pattern of never importing Settings/Messages directly
from feature modules.

---

## Hologram System

### Architecture

The hologram system uses invisible armor stands to display floating text:

```
HologramService (manager, ConcurrentHashMap-backed)
    ├── creates/manages --> Hologram (armor stand lines)
    ├── optional --> BukkitTask (auto-updating holograms)
    ├── protected by --> HologramProtectionListener
```

### HologramService

Central manager for all holograms:

```java
public class HologramService {
	// Create a static hologram
	public Hologram createHologram(Location location, String... lines);

	// Create a hologram that auto-updates on an interval
	public Hologram createUpdatingHologram(
			Location location,
			long updateIntervalTicks,
			BiConsumer<Hologram, Long> updater,
			String... initialLines
	);

	// Lookup
	public Optional<Hologram> getHologram(UUID id);

	public Optional<Hologram> getHologramAt(Location location);

	// Removal
	public void removeHologram(UUID id);

	public void removeHologramAt(Location location);

	public void cancelUpdateTask(UUID hologramId);

	// Cleanup
	public void clear();  // despawns all holograms and cancels all tasks
}
```

### Hologram

Each hologram is a list of invisible, marker armor stands stacked vertically:

```java
public class Hologram {
	private static final double LINE_HEIGHT = 0.25;  // spacing between lines

	private final UUID             id;
	private final Location         baseLocation;
	private final List<ArmorStand> lines;
	private       boolean          spawned;

	public void spawn(String... text);              // create armor stands

	public void update(String... text);             // update all lines (respawns if count changed)

	public void updateLine(int lineIndex, String text);  // update single line

	public void despawn();                          // remove all armor stands

	public void teleport(Location newLocation);     // move hologram

	public int getLineCount();
}
```

**Armor stand properties:**

- Invisible, no gravity, marker mode (no hitbox)
- Invulnerable, silent, small, no base plate, no arms
- Custom name visible (the hologram text)
- Equipment slots locked (preventing item placement)
- Non-persistent (`setPersistent(false)`) -- will not save to disk

**Example -- creating and updating a hologram:**

```java
HologramService hologramService = new HologramService(plugin);

// Static hologram
Hologram label = hologramService.createHologram(
		chestLocation.clone().add(0, 2, 0),
		"&6Loot Chest",
		"&7Tier: &eGold",
		"&aRight-click to open"
);

// Auto-updating hologram (updates every second)
Hologram timer = hologramService.createUpdatingHologram(
		location.clone().add(0, 2.5, 0),
		20L,  // 20 ticks = 1 second
		(hologram, currentTime) -> {
			long remaining = cooldownEnd - currentTime;
			hologram.update(
					"&cOn Cooldown",
					"&7Respawns in: &e" + (remaining / 1000) + "s"
			);
		},
		"&cOn Cooldown",
		"&7Calculating..."
);

// Later cleanup
hologramService.

removeHologram(label.getId());
		hologramService.

clear();  // remove everything
```

### HologramProtectionListener

Prevents players from interacting with hologram armor stands:

- Listens to `PlayerArmorStandManipulateEvent` and `PlayerInteractAtEntityEvent`
- Checks if the target armor stand belongs to any registered hologram
- Cancels the event if it does

---

## Cross-Module Integration

### Loot Chest + Hologram

The loot chest system uses `HologramService` to display floating labels above chests showing tier,
status, and cooldown timers. When a chest enters cooldown, an updating hologram can show remaining time.

### Loot Chest + Inventory

The chest-opening view is **not** a menu — it's a plain shared Bukkit `Inventory` any number of players can have
open at once, with clicks handled directly by `LootChestListener`'s raw `InventoryClickEvent`/`InventoryCloseEvent`
dispatch (unrelated to the `ChestMenu`/`ClickHandler` model above). At the CUT gate, `LootChestSession` (and
`LootChestService`'s `sharedChestInventories` map) swapped the deleted `InventoryHandler` for a small,
module-owned `SharedLootInventory` wrapper (~40 lines, `gangland-ui/lootchest-api/.../lootchest/
SharedLootInventory.java`) around a raw `Bukkit.createInventory(...)` — no menu framework needed for this path.
Take/deposit policy is unchanged either way: any viewer can freely take or place items, and a cursor-held stack on
close/disconnect is returned or dropped by stock CraftBukkit `InventoryView`-close behavior, which neither the old
`InventoryHandler` nor the new `SharedLootInventory` ever intercepted (see the CUT report for the full evidence
trail). Items are placed at random slots, and the inventory state is synced back to `LootChestData` on close for
persistence across sessions. The admin wand-preview screen (`LootChestWand`/`LootChestWandEditCommand`, both
`gangland-impl`, not `lootchest-api`) is a real menu, rebuilt onto `ChestMenuBuilder`/`PagedRegion` at the same
gate — `gangland-impl` already depends on `keystone-inventory` for its own core menus, so no new pom dependency
was needed for this.

### Inventory + Sign

Sign interactions that open a menu go through `MenuOpener` (see above) rather than the deleted `InventoryOpener`.

---

## Module Dependency Graph

```
gangland-core (Placeholder, ItemBuilder, ChatUtil, TriConsumer)
    ^
    |
sign-api ──────────> (standalone, depends on gangland-core)
    
hologram-api ───────> (standalone, depends on gangland-core)
    ^
    |
lootchest-api ──────> (depends on hologram-api, gangland-core; no inventory-api dependency any more)
```

All UI modules depend on `gangland-core` for shared utilities (`Placeholder`, `ItemBuilder`, `ChatUtil`,
`ColorUtil`, `TriConsumer`). `lootchest-api` additionally depends on `hologram-api` (for hologram labels); its own
chest-opening view is a raw Bukkit `Inventory` (`SharedLootInventory`), so it needs no Keystone menu dependency at
all. The admin wand-preview screen (`gangland-impl`'s `LootChestWand`) is the only loot-chest GUI on
`keystone-inventory`. All other modules are independent of each other.
