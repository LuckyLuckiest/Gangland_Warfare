# UI Framework

## Overview

The Gangland Warfare UI framework is composed of five independent modules under `gangland-ui/`:

| Module           | Package                    | Classes | Purpose                                    |
|------------------|----------------------------|---------|--------------------------------------------|
| `inventory-api`  | `me.luckyraven.inventory`  | ~37     | Custom inventory GUIs with click handlers  |
| `scoreboard-api` | `me.luckyraven.scoreboard` | ~8      | Per-player sidebar scoreboards             |
| `sign-api`       | `me.luckyraven.sign`       | ~25     | Interactive sign placement and interaction |
| `lootchest-api`  | `me.luckyraven.lootchest`  | ~35     | Loot chest sessions with cracking minigame |
| `hologram-api`   | `me.luckyraven.hologram`   | 3       | Floating text via invisible armor stands   |

All modules are event-driven using Bukkit listeners. Listeners are annotated with `@ListenerHandler` for
auto-registration via the `DependencyContainer` scan.

---

## Inventory System

### Architecture

```
InventoryBuilder (record)
    |-- creates --> InventoryHandler (core runtime object)
    |-- creates --> MultiInventory (paginated variant)
    
InventoryHandler
    |-- registered in --> InventoryRegistry (singleton, per-player tracking)
    |-- events routed by --> InventoryClickHandler, InventoryCloseHandler, InventoryDragHandler
    
Slot (data model)
    |-- evaluated by --> ConditionEvaluator / BooleanExpressionEvaluator
    |-- produces --> ConditionalSlotResult (resolved item + actions)
```

### InventoryHandler (Core Class)

`InventoryHandler` manages a single Bukkit `Inventory` with per-slot click callbacks, drag control, and
lifecycle management.

**Constructor overloads:**

| Constructor                                                         | Purpose                                                       |
|---------------------------------------------------------------------|---------------------------------------------------------------|
| `(String title, int size, NamespacedKey, UUID owner)`               | Base constructor. Creates raw inventory.                      |
| `(JavaPlugin, String title, int size, Player)`                      | Player-bound. Auto-registers in `InventoryRegistry`.          |
| `(JavaPlugin, String title, int size)`                              | Global/special inventory. Added to `SPECIAL_INVENTORIES` map. |
| `(JavaPlugin, String title, int size, String special, boolean add)` | Named special inventory with optional registration.           |
| `(String title, int size, Player, NamespacedKey)`                   | Player-bound with explicit key.                               |

**Size normalization:** The `size` is rounded up to the nearest multiple of 9, capped at `MAX_SLOTS = 54`.

**Static registry:** `SPECIAL_INVENTORIES` is a `Map<NamespacedKey, InventoryHandler>` for globally-accessible
inventories (e.g., shop menus shared across players).

**Key methods:**

```java
// Set an item with a left-click handler
handler.setItem(int slot, ItemBuilder item, boolean draggable,
                TriConsumer<Player, InventoryHandler, ItemBuilder> clickAction);

// Set an item with separate left-click and right-click handlers
handler.setItem(int slot, ItemBuilder item, boolean draggable,
                TriConsumer<Player, InventoryHandler, ItemBuilder> leftClick,
                TriConsumer<Player, InventoryHandler, ItemBuilder> rightClick);

// Set a raw ItemStack (no click handler)
handler.setItem(int slot, ItemStack itemStack, boolean draggable);

// Remove an item and its handlers from a slot
handler.removeItem(int slot);

// Open the inventory for a player (re-registers in InventoryRegistry)
handler.open(Player player);

// Close the inventory for a player
handler.close(Player player);

// Rename the inventory (re-creates the Bukkit Inventory, preserves contents)
handler.rename(JavaPlugin plugin, String name);

// Copy contents from another handler, resolving placeholders
handler.copyContent(Placeholder placeholder, InventoryHandler source, Player player);

// Clear all items
handler.clear();
```

**Example -- creating a simple inventory programmatically:**

```java
InventoryHandler menu = new InventoryHandler(plugin, "My Menu", 27, player);

// Glass pane border item (not draggable, no click action)
ItemBuilder glass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
        .setDisplayName(" ");
menu.setItem(0, glass, false, null);

// Clickable item
ItemBuilder diamond = new ItemBuilder(Material.DIAMOND)
        .setDisplayName("&bClick Me")
        .setLore("&7Left-click to execute", "&7Right-click for info");

menu.setItem(13, diamond, false,
    // Left-click
    (p, inv, item) -> {
        p.performCommand("glw shop buy diamond");
        p.closeInventory();
    },
    // Right-click
    (p, inv, item) -> {
        p.sendMessage("This diamond costs $500!");
    }
);

menu.open(player);
```

### InventoryBuilder (Record)

`InventoryBuilder` is a `record(InventoryData inventoryData, String permission)` that creates `InventoryHandler`
or `MultiInventory` instances from YAML-parsed `InventoryData`.

**`createInventory` method:**

```java
InventoryHandler createInventory(
    JavaPlugin plugin,
    Placeholder placeholder,    // resolves %placeholder% tokens
    Player player,
    Fill fill,                  // border/fill material
    Fill line,                  // vertical/horizontal line material
    ConditionEvaluator evaluator,
    InventoryOpener inventoryOpener
)
```

Processing pipeline per slot:

1. Evaluate slot conditions via `slot.getConditionalResult(player, evaluator)`
2. Resolve `color` NBT tag to dynamic material color (e.g., gang color -> wool color)
3. Resolve `head` NBT tag to player skull owner
4. Apply placeholder resolution to display name and lore
5. Handle enchantment glow effects
6. Wire click actions (command, inventory-open, or anvil GUI)
7. Apply vertical/horizontal lines and border/fill

**`createMultiInventory` method:**

```java
MultiInventory createMultiInventory(
    JavaPlugin plugin,
    Placeholder placeholder,
    Player player,
    List<ItemStack> items,      // dynamic item list to paginate
    ButtonTags buttonTags,      // custom head textures for nav buttons
    Fill fill
)
```

### InventoryData

Parsed from YAML configuration. Contains:

| Field              | Type                  | Description                                                       |
|--------------------|-----------------------|-------------------------------------------------------------------|
| `name`             | `String`              | Internal identifier                                               |
| `displayName`      | `String`              | Title shown to player (supports `&` color codes and placeholders) |
| `size`             | `int`                 | Inventory size (normalized to factor of 9)                        |
| `slots`            | `List<Slot>`          | All configured slots                                              |
| `permission`       | `String`              | Required permission to open                                       |
| `border`           | `boolean`             | Whether to draw a glass-pane border                               |
| `fill`             | `boolean`             | Whether to fill empty slots                                       |
| `verticalLine`     | `List<Integer>`       | Columns to draw vertical lines                                    |
| `horizontalLine`   | `List<Integer>`       | Rows to draw horizontal lines                                     |
| `isMultiInventory` | `boolean`             | Whether this is a paginated inventory                             |
| `staticItems`      | `Map<Integer, Slot>`  | Fixed items in multi-inventory pages                              |
| `openInventories`  | `List<OpenInventory>` | Nested inventories that can be opened from this one               |

### Slot

Each slot in an inventory is represented by a `Slot` object:

```java
public class Slot {
    private final int         slot;       // slot index (0-53)
    private final boolean     clickable;  // whether click events fire
    private final boolean     draggable;  // whether item can be dragged out
    private final ItemBuilder item;       // the displayed item

    // Optional conditional display logic
    private ConditionalSlotData conditionalData;

    // Click handlers
    private TriConsumer<Player, InventoryHandler, ItemBuilder> clickableSlot;     // left-click
    private TriConsumer<Player, InventoryHandler, ItemBuilder> rightClickSlot;    // right-click
}
```

**Conditional resolution:** When `conditionalData` is set, calling `getConditionalResult(player, evaluator)` walks
the condition tree (which supports nesting) and returns a `ConditionalSlotResult` containing the resolved item,
click actions, and draggable state.

### Condition System

The condition system enables YAML-driven dynamic slot content based on player state.

**`SlotCondition`** -- wraps a placeholder expression string (e.g., `%gangland_has_gang%`):

```java
public record SlotCondition(String valueExpression) {
    public boolean evaluate(Player player, ConditionEvaluator evaluator) {
        return evaluator.evaluate(player, valueExpression);
    }
}
```

**`ConditionEvaluator`** -- interface for evaluating condition expressions:

```java
public interface ConditionEvaluator {
    boolean evaluate(Player player, String expression);
}
```

**`BooleanExpressionEvaluator`** -- default implementation that resolves placeholders and parses the result
as boolean (`true`/`yes`/`1` = true, `false`/`no`/`0`/`na` = false, non-zero numbers = true, non-empty strings = true).

**`ConditionalSlotData`** -- tree structure with True/False branches:

```java
public class ConditionalSlotData {
    private final SlotCondition condition;
    private final BranchData    trueData;   // shown when condition is true
    private final BranchData    falseData;  // shown when condition is false
}
```

Each `BranchData` can contain a `nestedCondition` for chaining:

```
Condition: %gangland_has_gang%
  True -> show "Gang Info" button
    Nested Condition: %gangland_is_leader%
      True -> show "Manage Gang" button
      False -> show "Leave Gang" button
  False -> show "Create Gang" button
```

**Click action types** (inner classes of `ConditionalSlotData`):

| Type      | Record                                                          | Behavior                                            |
|-----------|-----------------------------------------------------------------|-----------------------------------------------------|
| Command   | `CommandAction(String command)`                                 | Executes command as the player                      |
| Inventory | `InventoryAction(String inventoryName)`                         | Opens another named inventory via `InventoryOpener` |
| Anvil     | `AnvilAction(String title, String text, String successCommand)` | Opens AnvilGUI with text input                      |

### Multi-Inventory (Pagination)

`MultiInventory` extends `InventoryHandler` and manages a `LinkedList<InventoryHandler>` of pages.

**Page layout:**

- Items are placed in the interior grid (rows 2-5, columns 2-8), avoiding the border
- With static items, column 1 is reserved for fixed sidebar items, column 2 is a divider line
- Navigation buttons use custom player head textures

**Navigation methods:**

```java
InventoryHandler nextPage();      // advance to next page
InventoryHandler previousPage();  // go back one page
InventoryHandler homePage();      // return to page 0
boolean hasNextPage();            // check if more pages exist
```

**`MultiInventoryCreation`** -- factory that computes `PageConfig` and builds all pages:

```java
static MultiInventory dynamicMultiInventory(
    JavaPlugin plugin,
    Player player,
    List<ItemStack> items,
    String title,
    boolean staticItemsAllowed,
    boolean fixedSize,
    Fill fill,
    ButtonTags buttonTags,
    Map<ItemStack, TriConsumer<...>> staticItems
)
```

**`PageConfig`** -- pagination math:

```java
public record PageConfig(
    int maxRows,          // usable rows per page (typically 4)
    int maxColumns,       // usable columns (7 without static items, 6 with)
    int perPage,          // items per page (maxRows * maxColumns)
    int pages,            // total page count
    int remainingAmount,  // items on the last page
    int finalPage,        // slot count for the last page
    int initialPage       // slot count for non-final pages (usually MAX_SLOTS=54)
)
```

**`MultiInventoryNavigation`** -- adds previous/next/home player-head buttons to the bottom row:

| Position                   | Button             | Condition         |
|----------------------------|--------------------|-------------------|
| `size - 1` (bottom-right)  | Next Page `->`     | Not on last page  |
| `size - 9` (bottom-left)   | Previous Page `<-` | Not on first page |
| `size - 5` (bottom-center) | Home Page          | Not on first page |

Each button plays `BLOCK_WOODEN_BUTTON_CLICK_ON` on click.

**`ButtonTags`** -- custom head textures for navigation:

```java
public record ButtonTags(String previousPage, String homePage, String nextPage) {}
```

**`Fill`** -- border/line material:

```java
public record Fill(String name, String material) {}
```

### Slot Event Handlers

Slot event handlers build `Slot` objects from YAML configuration sections. They are used during inventory
parsing to wire behavior to slots.

**`SlotEventHandler`** (interface):

```java
public interface SlotEventHandler {
    Slot handle(SlotContext context, InventoryOpener opener);
}
```

**`SlotContext`** (record) -- carries all per-slot data from YAML:

```java
public record SlotContext(
    ConfigurationSection eventSection,      // left-click config
    ConfigurationSection rightClickSection, // right-click config
    int slotLoc,                            // slot position
    String item,                            // material name
    String itemName,                        // display name
    Map<String, Object> data,              // NBT tag data (color, head)
    List<String> lore,                      // lore lines
    boolean enchanted,                      // enchantment glow
    boolean draggable                       // can be dragged
) {}
```

**`ClickSlotHandler`** -- the standard handler for `OnClick`/`OnInteract` events:

- Reads `Command`, `Inventory`, `Permission` from the YAML section
- Supports both left-click and right-click actions independently
- Permission-gated execution

**`AbstractCommandSlotHandler`** -- template-method base class:

```java
public abstract class AbstractCommandSlotHandler implements SlotEventHandler {
    // Reads Command/Inventory/Permission from config, then calls:
    protected void onSlotAction(Player player, InventoryHandler inv, ItemBuilder builder) {
        // Override in subclasses for additional behavior
    }
}
```

**`SlotItemFactory`** -- builds `ItemBuilder` from raw YAML values, handling material validation,
color tags, data tags, enchantments.

### Listeners

| Listener                 | Event                 | Behavior                                                                                       |
|--------------------------|-----------------------|------------------------------------------------------------------------------------------------|
| `InventoryClickHandler`  | `InventoryClickEvent` | Routes clicks to registered left/right-click handlers. Cancels event unless slot is draggable. |
| `InventoryCloseHandler`  | `InventoryCloseEvent` | Routes to close handlers                                                                       |
| `InventoryDragHandler`   | `InventoryDragEvent`  | Prevents dragging in custom inventories                                                        |
| `PlayerInventoryCleanup` | `PlayerQuitEvent`     | Clears player from `InventoryRegistry`                                                         |

**Click routing logic (simplified):**

```java
// In InventoryClickHandler.onInventoryClick:
InventoryHandler inv = InventoryRegistry.getInstance().findByInventory(topInventory);

if (event.isRightClick()) {
    var rightClickAction = inv.getRightClickSlots().get(rawSlot);
    if (rightClickAction != null) {
        rightClickAction.accept(player, inv, itemBuilder);
        event.setCancelled(!inv.getDraggableSlots().contains(rawSlot));
        return;
    }
}

var leftClickAction = inv.getClickableSlots().get(rawSlot);
leftClickAction.accept(player, inv, itemBuilder);
event.setCancelled(!inv.getDraggableSlots().contains(rawSlot));
```

### InventoryRegistry (Singleton Service)

Thread-safe per-player inventory tracking using `ConcurrentHashMap`:

```java
InventoryRegistry.getInstance().registerInventory(UUID, InventoryHandler);
InventoryRegistry.getInstance().unregisterInventory(UUID, InventoryHandler);
InventoryRegistry.getInstance().findByInventory(Inventory);  // reverse lookup
InventoryRegistry.getInstance().getInventories(UUID);         // all inventories for player
InventoryRegistry.getInstance().clear(UUID);                  // cleanup on quit
```

### InventoryOpener

Functional interface that decouples `inventory-api` from `gangland-impl`:

```java
@FunctionalInterface
public interface InventoryOpener {
    void openInventory(Player player, String inventoryName);
}
```

The implementation in `gangland-impl` resolves the inventory name from the YAML-configured inventory map
and opens it for the player.

---

## Scoreboard System

### Architecture

```
ScoreboardAddon (config loader)
    |-- reads YAML --> Line / StaticLine objects
    
Scoreboard (orchestrator)
    |-- uses --> RepeatingTimer (tick every 1 tick)
    |-- delegates to --> DriverHandler (abstract)
                            |-- DriverV1 (clustered updates)
                            |-- DriverV2 (built-in clustering)
                            |-- DriverV3 (minimal-diff, change detection)
                                |-- wraps --> FastBoard (packet-based scoreboard)
```

### Scoreboard (Orchestrator)

Creates a `RepeatingTimer` that fires every tick (50ms) and calls `driver.update()`:

```java
public class Scoreboard {
    public Scoreboard(JavaPlugin plugin, DriverHandler driver);
    public void start();  // begins tick-based updates
    public void end();    // stops timer and deletes FastBoard
}
```

### DriverHandler (Abstract Base)

Wraps FastBoard with ViaVersion compatibility and per-line update intervals:

```java
public abstract class DriverHandler {
    // Fields
    private final Placeholder placeholder;
    private final FastBoard   fastBoard;
    private final List<Line>  lines;
    private final Line        title;
    private final Map<Line, Long> lineUpdateCounts;
    private long globalTickCount;

    // Abstract -- each driver version implements its own update strategy
    public abstract void update();

    // Shared helpers
    protected String updateLine(Line line);   // resolves placeholders
    protected void incrementTick();
}
```

**ViaVersion support:** `FastBoardImpl` (inner class) overrides `hasLinesMaxLength()` to check the
player's protocol version via ViaVersion API. Players on 1.13+ get unlimited line length.

### Driver Versions

**DriverV1** -- Caches lines and uses a clustering algorithm to group lines with similar update intervals,
minimizing FastBoard API calls.

**DriverV2** -- Similar to V1 with a built-in library-based clustering approach.

**DriverV3** (recommended) -- Minimal-diff driver with change detection:

```java
public class DriverV3 extends DriverHandler {
    private final Map<Long, List<Line>> clusters;        // lines grouped by interval
    private final Map<Long, Integer>    clustersInterval; // tick counters per cluster
    private final Map<Line, String>     cache;            // last rendered text per line
}
```

Strategy:

1. Group lines by their update interval into clusters
2. Each tick, only process clusters whose interval counter is due
3. Within due clusters, only send FastBoard updates for lines whose text actually changed
4. Flash effects (`flashif:`, `flash:`) are updated every tick regardless of interval

### Line / StaticLine

**`Line`** -- a content unit with animated text support:

```java
public class Line {
    private final long         interval;  // update interval in ticks (0 = static)
    private final List<String> contents;  // rotating content frames
    private final int          usedIndex; // position index in the scoreboard

    public void addContent(String content);      // add a frame (auto-colorized)
    public String getCurrentContent();           // current frame
    public String update(Placeholder, Player);   // resolve placeholders, advance frame
    public boolean isStatic();                   // true if interval==0 or StaticLine
}
```

**`StaticLine`** -- extends `Line` with `interval=0`, never updates after initial render.

### ScoreboardAddon (Configuration)

Loads scoreboard configuration from `scoreboard.yml`:

```yaml
Board:
  Title:
    Interval: 20          # ticks between title frame changes
    Lines:
      - "&6Gangland &7Warfare"
      - "&eGangland &7Warfare"
  Rows:
    1:
      Interval: 0         # static line
      Lines:
        - "&7&m                    "
    2:
      Interval: 20        # update every second
      Lines:
        - "&fKills: &a%gangland_kills%"
```

**Example -- creating a scoreboard for a player:**

```java
ScoreboardAddon addon = new ScoreboardAddon(fileManager);

DriverV3 driver = new DriverV3(
    placeholder,                // Placeholder resolver
    viaAPI,                     // ViaVersion API (nullable)
    player,                     // target player
    addon.getTitle(),           // title Line
    new ArrayList<>(addon.getLines())  // content Lines
);

Scoreboard scoreboard = new Scoreboard(plugin, driver);
scoreboard.start();

// Later, on quit:
scoreboard.end();
```

---

## Sign System

### Architecture

The sign system uses a **Chain of Responsibility** pattern with composable aspects:

```
SignService (abstract, initialization)
    |-- registers --> SignTypeDefinition (bundles type + validator + parser + handler + aspects)
        |-- into --> SignTypeRegistry (lookup by typed/generated name)

Player places sign --> SignCreation listener
    |-- validates via --> SignValidator / AbstractSignValidator
    |-- formats via --> SignFormatterService

Player right-clicks sign --> PlayerSignInteract listener
    |-- looks up --> SignTypeRegistry.findByLine(firstLine)
    |-- parses via --> SignParser / AbstractSignParser --> ParsedSign
    |-- executes via --> SignHandler / AspectBasedSignHandler
        |-- chains --> List<SignAspect> (sorted by priority)
            |-- each produces --> AspectResult
```

### SignType

```java
public record SignType(String typed, String generated) {}
```

- `typed`: the raw text a player writes on line 1 (e.g., `[Trade]`)
- `generated`: the formatted text displayed after validation (e.g., `&2[Trade]`)

### SignTypeDefinition

Bundles all components for a sign type:

```java
@Builder
public class SignTypeDefinition {
    private final SignType      signType;
    private final SignValidator signValidator;
    private final SignParser    signParser;
    private final SignHandler   handler;
    private final BulkSignHandler bulkHandler;  // optional, for shift-click bulk actions
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
    private final String formatName;
    private final String signTypePrefix;
    private final List<SignLineFormat> lineFormats;
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
    private final String              prefix;           // e.g., "[GLW]"
    private final SignTypeRegistry    registry;
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
    |-- loaded by --> LootChestLoader

LootChestData (chest definition: location, tier, items, cooldown state)
    |-- creates --> LootChestSession (active player session)
        |-- optionally creates --> CrackingSession (lockpick minigame)

LootChestHandler<T> (abstract handler chain)
    |-- subclassed by:
        SessionStartHandler, SessionCompleteHandler
        ChestCooldownTickHandler, ChestCooldownCompleteHandler
        CrackingStartHandler, CrackingTickHandler,
        CrackingSuccessHandler, CrackingFailedHandler

LootChestEvent (abstract base)
    |-- Cracking events: Start, During, Success, Failure, End
    |-- Chest events: Open, Close, DuringCooldown, CooldownComplete
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
    private long    lastOpened;
    private boolean isLooted;
    private long    cooldownEndTime;
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
        NONE, LOCKPICK, KEY, PERMISSION
    }
}
```

### LootChestSession

Manages an active player-chest interaction:

```java
public class LootChestSession {
    private final UUID             sessionId;
    private final Player           player;
    private final LootChestData    chestData;
    private final InventoryHandler inventory;
    private final List<ItemStack>  generatedLoot;
    private final boolean          usingSharedInventory;
    private int[]                  slotMapping;
    private SessionState           state;
    private boolean                itemTaken;

    // Cracking state
    private boolean crackingRequired;
    private boolean crackingCompleted;

    public void open();              // populates inventory and opens for player
    public void markItemTaken();     // tracks that player took an item
    public void close();             // syncs inventory state back to LootChestData
    public void cancel();

    public enum SessionState {
        OPEN, CRACKING, LOOTING, CLOSED, CANCELLED
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
    private long                timeRemaining;
    private int                 progress;        // 0-100
    private int                 targetProgress;  // default 100
    private CrackState          state;

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
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
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
    |-- creates/manages --> Hologram (armor stand lines)
    |-- optional --> BukkitTask (auto-updating holograms)
    |-- protected by --> HologramProtectionListener
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
    private boolean                spawned;

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
hologramService.removeHologram(label.getId());
hologramService.clear();  // remove everything
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

`LootChestSession` wraps an `InventoryHandler` to display the chest contents. Items are placed at random
slots, and the inventory state is synced back to `LootChestData` on close for persistence across sessions.

### Inventory + Sign

Both systems support the `InventoryOpener` functional interface to open inventories by name, enabling
sign interactions that open custom inventory GUIs.

---

## Module Dependency Graph

```
gangland-core (Placeholder, ItemBuilder, ChatUtil, TriConsumer)
    ^
    |
inventory-api -----> (standalone, depends on gangland-core)
    ^
    |
scoreboard-api ----> (standalone, depends on gangland-core)
    
sign-api ----------> (standalone, depends on gangland-core)
    
hologram-api -------> (standalone, depends on gangland-core)
    ^
    |
lootchest-api ------> (depends on inventory-api, hologram-api, gangland-core)
```

All UI modules depend on `gangland-core` for shared utilities (`Placeholder`, `ItemBuilder`, `ChatUtil`,
`ColorUtil`, `TriConsumer`). The `lootchest-api` additionally depends on `inventory-api` (for `InventoryHandler`)
and `hologram-api` (for hologram labels). All other modules are independent of each other.
