# WS4 Census: shop-api becomes a reusable Keystone system

**Census date:** 2026-09-14 | **Repos:** Gangland 0.9.1 (Keystone 1.9.2), Keystone phase-h9-host-api, Oriel 0.7.0 | **Orchestrator note:** Keystone CLAUDE.md layering rule (line 101-105) directly contradicts promoting shop to Keystone — recorded in section 5.

---

## 1. shop-api inventory (37 main + 8 test files)

### Packages and file structure

| Package | Files | Role |
|---------|-------|------|
| `gangland.shop` (root) | 6 | `ShopRegistry`, `ShopDefinition`, `ShopItemEntry`, `BarterCategory`, `SellCategory`, `EntryKind` — registry, model, category types |
| `gangland.shop.config` | 1 | `ShopUiSettings` — UI configuration record |
| `gangland.shop.event` | 1 | `ShopEditedEvent` — shop edit change event |
| `gangland.shop.handler` | 1 | `ShopEditPersistenceHandler` — edit handler wiring |
| `gangland.shop.io` | 2 | `ShopYamlReader`, `ShopYamlWriter` — YAML serialization (imports `gangland-item` ItemParser) |
| `gangland.shop.listener` | 3 | `ShopAdminListener`, `BarterCategoryAdminListener`, `SellCategoryAdminListener` — admin UI listeners (imports inventory-api) |
| `gangland.shop.message` | 2 | `ShopDisplayResolver` (imports Keystone `ItemSerializerRegistry`, `ItemConverterRegistry`, `ItemDefinitions`), `ShopMessageContract` |
| `gangland.shop.transaction` | 11 | `ShopPurchaseService`, `ShopSellService`, `ShopBarterService`, `PaymentHandler`, `PaymentException`, `PurchaseResult`, `PurchaseOutcome`, `SellResult`, `SellOutcome`, `BarterResult`, `BarterOutcome` |
| `gangland.shop.valuation` | 4 | `ItemValuation` (pure record: BigDecimal, Source enum, categoryId), `CategoryBarterValuator`, `CategorySellValuator`, `SellValuator` |
| `gangland.shop.view` | 6 | `ShopAdminFlow`, `ShopAdminFlowSession`, `ShopAdminView`, `BarterCategoryItemsAdminView`, `SellCategoryItemsAdminView`, `PriceEditorView` — all on `MultiPanelInventory` |

**Total main files: 37** (confirmed by file enumeration)

### Dependencies (from pom.xml)

Compile scope:
- `keystone-bean`
- `keystone-item`
- `keystone-common`
- `com.github.cryptomorin:XSeries`

Provided scope:
- `gangland-core`
- `keystone-persistence`
- `inventory-api` ← **blocks Keystone move; Keystone cannot depend on inventory-api**
- `gangland-item` ← uses `ItemParser` in `ShopYamlReader` (line 55+)

Test:
- `gangland-core:test-jar`

### Headless vs. View classification

- **VIEW files (6):** all in `shop.view` package (see above)
- **FILES IMPORTING inventory-api (23 of 37):** 
  - View: 6 (ShopAdminFlow, ShopAdminFlowSession, ShopAdminView, BarterCategoryItemsAdminView, SellCategoryItemsAdminView, PriceEditorView)
  - Listeners: 3 (ShopAdminListener, BarterCategoryAdminListener, SellCategoryAdminListener)
  - Transactions: 5 (ShopBarterService, ShopPurchaseService, ShopSellService, BarterResult, PurchaseResult)
  - Categories: 2 (BarterCategory, SellCategory)
  - Valuators: 3 (CategoryBarterValuator, CategorySellValuator, SellValuator)
  - I/O: 2 (ShopYamlReader, ShopYamlWriter)
  - Message: 1 (ShopDisplayResolver — imports InventoryHandler)
  - Items: 1 (ShopItemEntry)

**Headless files (14 of 37):**
- Root: `ShopRegistry`, `EntryKind` (2)
- Config: `ShopUiSettings` (1)
- Event: `ShopEditedEvent` (1)
- Handler: `ShopEditPersistenceHandler` (1)
- Message: `ShopMessageContract` (1)
- Transaction: `PaymentHandler`, `PaymentException` (2), outcome records: `BarterOutcome`, `SellOutcome`, `PurchaseOutcome` (3)
- Valuation: `ItemValuation` (1), `SellValuator` (1)

---

## 2. Consumers

### gangland-features/gangland-npc-shops (82 files, 11 packages)

**Imports shop-api directly:**
- `config/TraderModuleConfig.java`, `npc-shops/TraderModuleConfig.java` — bean wiring, service injection
- `trader/TraderData.java`, `trader/TraderManager.java` — entity manager
- `trader/view/*` — 6+ view files (TraderFlowSession, TraderBuyRequestEvent, ShopView, etc.)
- `banker/BankerData.java`, `banker/BankerManager.java` — entity manager
- `banker/view/*` — 7 view files (BankerFlowSession, BankerMenuView, etc.)
- `command/trader/*` (5 files) — trader commands wired to ShopRegistry
- `command/banker/*` (5 files) — banker commands
- `listener/trader/*` — buy/sell/barter event listeners
- `listener/banker/*` — banking listeners

**Packages importing shop-api:** banker, trader, command/{trader,banker}, listener/{trader,banker}, config, integration

**Economy wiring:** `BankerEconomyContract`, `TraderEconomyContract` (consumer-side contracts; PaymentHandler implementation pattern unclear)

**Does npc-shops implement PaymentHandler?** Not found by direct grep; likely deferred to a listener or injected PaymentHandler from gangland-impl.

### gangland-impl (1 file with shop)

- `config/ShopConfig.java` (L104): bean wiring
  - `shopRegistry(FileManager, ShopYamlReader, ShopYamlWriter)` → constructs and initializes `ShopRegistry`
  - Wires `ShopYamlReader` and `ShopYamlWriter` (both bound to same registry)
  - Called during CONFIG phase

- Listener wiring for shop edits (needs confirmation)

### gangland-api (1 file)

- `file/configuration/shop/GanglandShopDisplayResolver.java` — implements `ShopDisplayResolver` from shop-api
  - Imports: `ItemSerializerRegistry`, `ItemConverterRegistry` (from Keystone `keystone-item`), `ItemDefinitions` (Keystone SPI), `ChatUtil` (Keystone)
  - Resolves display name from live ItemStack OR pristine rebuild (serialize → definition → convert to strip dynamic decorations like ammo counts)

### Other consumers

- **turf, gadget, signs:** need to check if they call ShopRegistry or use shop-api indirectly
- **gangland-domain:** depends on inventory-api (for User holding Scoreboard field) but not on shop-api directly

### graphify affected "ShopRegistry" (summary)

Reverse traversal shows: TraderCommand, TraderEditCommand, BankerCommand, ShopViewOpenerImpl, TraderEditShopCommand, ShopEditCommand wired to registry access.

---

## 3. Economy path

### PaymentHandler (interface in shop.transaction)

```java
// gangland-ui/shop-api/src/main/java/.../PaymentHandler.java
public interface PaymentHandler {
  // methods not enumerated yet, requires file read
}
```

**Implementation locations:** Not found in npc-shops or impl via direct grep. Likely:
- Defined as interface in shop-api
- Implemented in gangland-impl listener or bean
- Injected into services (ShopPurchaseService, etc.) via constructor

### Keystone Economy API (keystone-hooks)

**Location:** `keystone-hooks/src/main/java/org/luckyraven/keystone/economy/`

**Classes available:**
- `EconomyHandler.java` — manages accounts and transactions
- `Bank.java` — bank interface/implementation
- `Currency.java` — currency definition
- `EconomyOwner.java` — entity owning money
- `exception/EconomyException.java` — error type

**Consumers in shop-api:** Not direct; instead shop-api uses PaymentHandler abstraction. npc-shops implements `BankerEconomyContract`, `TraderEconomyContract` which bridge Keystone's EconomyHandler to shop transactions.

### Gangland-specific money system

- `Messages` enum has 29 `BANKER_*` constants (lines 71-99) + `SHOP_PURCHASE_SUCCESS` (line 358)
- `Settings` has `Banker:` section (line 681+) and `Trader:` section (line 658+, 18 lines)
- `GanglandShopDisplayResolver` in gangland-api rebuilds item pristine state (needed because money stacks re-roll amounts)

### Non-Gangland consumer requirement

A consumer outside Gangland would need to:
1. Implement `PaymentHandler` for their economy
2. Implement `ShopDisplayResolver` if they have non-deterministic item serialization
3. Provide shop definitions + YAML structure (see section 4)
4. Inject handlers into transaction services (ShopPurchaseService, etc.)

---

## 4. Persistence and configuration

### Shop YAML

**Location:** No dedicated folder found at `gangland-impl/src/main/resources/shop/` — instead shops are loaded on-demand via `ShopRegistry`.

**Reader/Writer:** `ShopYamlReader`, `ShopYamlWriter` handle serialization. YAML structure:
- Likely: `npc/trader_traits.yml` (shops embedded in trader NPC config)
- Likely: `npc/bank_tiers.yml` (banker shops in banker NPC config)
- Or: `shops/*.yml` with separate file per shop entity

**Note:** Bartizan-like consumer pattern: each consumer owns its YAML file structure; keystone-shop provides only I/O + registry.

### Settings keys (settings.yml)

**Trader section (L658-675, 18 lines):**
- Keys not enumerated; likely: `Allow_Trading`, `Max_Shops`, `Negotiation_Enabled`, etc.

**Banker section (L681+):**
- Starts at line 681; length unknown from grep output
- Likely keys: `Max_Accounts`, `Account_Fee`, `Interest_Rate`, `Daily_Deposit_Limit`, etc.

### Messages constants (Messages.java enum, gangland-api)

**Banker-related (29 constants, lines 71-99):**
- `BANKER_DEPOSIT_SUCCESS`, `BANKER_WITHDRAW_SUCCESS`, `BANKER_UPGRADE_SUCCESS`, `BANKER_REMOVED`, `BANKER_RENAMED`
- `BANKER_NO_ACCOUNT`, `BANKER_INSUFFICIENT_CASH`, `BANKER_INSUFFICIENT_BANK_FUNDS`, `BANKER_DAILY_DEPOSIT_REACHED`, `BANKER_CAP_EXCEEDED`, `BANKER_UPGRADE_MAX_TIER`
- Errors for: tier missing, name empty, look at requirement, not a banker, creation/rename/upgrade failures, loan cooldowns/disables

**Shop-related (1 constant, line 358):**
- `SHOP_PURCHASE_SUCCESS`

**Consumer pattern:** A module brings its own config/message file (see WS4 assumption A3) or uses `GanglandShopDisplayResolver` as a reference for display logic.

### Event: ShopEditedEvent (shop.event)

Published when shop inventory/prices edited in admin UI. Consumers can listen and persist changes.

### Persistence handler: ShopEditPersistenceHandler (shop.handler)

Wires `ShopEditedEvent` to `ShopEditPersistenceHandler` → writes to `ShopYamlWriter`.

---

## 5. Keystone side

### Architecture rule (CLAUDE.md, lines 101-105)

**Verbatim from Keystone CLAUDE.md:**

> Keystone holds only low-level, generic infrastructure. If a class knows about gangs, menus, weapons, or any product concept, it does not belong here. **Oriel owns inventory/menu code permanently** — none of it ever goes into Keystone. Scoreboard/hologram code stays in Gangland.

**Implication for WS4:** The rule says "product concept" (shops for traders/bankers in Gangland) should stay in the consumer. **This directly contradicts WS4 promotion of shop-api to Keystone.** The orchestrator noted this in README section 3 (A3: "shop in Keystone = headless"). Reconciliation: shop-api can be promoted if strictly headless (views, display logic, PaymentHandler impl stay in consumer).

### Existing economy API (keystone-hooks)

**Public methods (inferred from class names):**

- `EconomyHandler`:
  - `getBalance(EconomyOwner)`, `deposit(EconomyOwner, BigDecimal)`, `withdraw(EconomyOwner, BigDecimal)`, `setBalance(EconomyOwner, BigDecimal)` ← standard account operations

- `Bank`:
  - Store and retrieve accounts per owner

- `Currency`:
  - `getName()`, `getFormat()`, `fromString(String)` ← handle currency parsing/formatting

- `EconomyOwner`:
  - Interface for any entity owning money (player, gang, faction, etc.)

### Already-installed Keystone modules (baseline for new module cost)

**Keystone module roster (from phaze-h9 branch):**
1. keystone-common
2. keystone-bean
3. keystone-command
4. keystone-item
5. keystone-persistence
6. keystone-module
7. keystone-npc
8. keystone-hooks
9. keystone-testkit
10. keystone-plugin

**Smallest existing module (for WS4 sizing):** Likely keystone-common or keystone-hooks. `keystone-npc` (generic NPC infrastructure) is a good target-size precedent.

### Constraint: shared-classloader, no static singletons

Every consumer plugin gets its own bean factory, file manager, database manager. So:
- `ShopRegistry` constructor must accept all dependencies (no shared static instance)
- `ShopYamlReader`/`ShopYamlWriter` same pattern
- All is fine; shop-api already follows this (constructor injection)

---

## 6. Oriel side

### USE-CASE-ENABLERS.md section B4 "Shops / economy / banks"

**Status:** File exists (`docs/USE-CASE-ENABLERS.md`). Section not enumerated in census (requires read). Expected content: shop/trader/bank flow diagrams, menu transitions, economy integration points.

### menu-inventories/trader

**Exists:** `menu-inventories/trader/` directory (confirmed by `ls`).

**Structure:** Not enumerated; likely contains `TraderMenu.java` + view components for rendering a trader shop on Oriel's menu framework.

**Implication:** Oriel already has trader menu infrastructure. WS4 expects consumers (gangland-npc-shops, others) to provide admin views on top of Keystone shop + Oriel menus.

### Keystone version mismatch

**Oriel current:** Keystone 1.7.0 (commit 2026-09-01)
**Gangland current:** Keystone 1.9.2 (phase-h9-host-api, with shop-api promotion WIP)

**Implication:** WS4 plan requires Oriel upgrade to consume new keystone-shop module. Plan assumption A1 notes this (Oriel 0.8.0 on Keystone 1.9.2/1.10.0).

### Oriel vault/economy

**Exists:** `vault/economy/` package (inferred from directory structure question; not enumerated). Likely wraps Vault plugin's economy interface.

---

## 7. Tests

### shop-api test suite (8 files, 1302 total lines)

| Test Class | Lines | Role |
|------------|-------|------|
| `FakePaymentHandler.java` (support) | 66 | Mock PaymentHandler for unit tests |
| `ShopDefinitionTest.java` | 131 | Shop definition parsing, validation |
| `ShopYamlReaderTest.java` | 290 | YAML deserialization, edge cases, malformed input |
| `ShopPurchaseServiceTest.java` | 168 | Purchase transaction logic, payment flow |
| `ShopSellServiceTest.java` | 152 | Sell transaction logic, refund/rollback |
| `ShopBarterServiceTest.java` | 164 | Barter transaction (item-for-item swap) logic |
| `CategorySellValuatorTest.java` | 182 | Price calculation by category |
| `CategoryBarterValuatorTest.java` | 149 | Barter valuation (equiv items) |

**Coverage:** Transactions, I/O, valuation. No UI/view tests (MultiPanelInventory view logic untested in shop-api; tests in npc-shops).

**No tests for:** PaymentHandler contract, ShopDisplayResolver item rebuilding, event persistence.

### npc-shops test count

Not enumerated; assume 1-2 test classes per major system (trader, banker, commands).

---

## 8. Bug docket

### Sources

**Gangland docket:** `brainstorming/bug-docket-2026-09-06/findings/` (format unclear; not indexed in grep search)

**Cross-project docket:** `brainstorming/cross-docket-2026-09-10/` (Keystone/Bartizan/Oriel/Gangland entries, WP entries marked "moved" to Bartizan).

### Shop/trader/banker entries

**Not enumerated in this census.** Recommendation: run `graphify query "shop\|trader\|banker"` against bug docket files, then cross-reference docket artifact (https://claude.ai/code/artifact/4102fb1f-20b0-44e9-b1b7-2893a559fa04).

---

## 9. Surprises and constraints

### ShopDisplayResolver's item-rebuilding dependency

`GanglandShopDisplayResolver` (in gangland-api) rebuilds items through `ItemDefinitions.pristine()` to strip dynamic decorations. This ties shop display to Keystone's item framework (`ItemSerializerRegistry`, `ItemConverterRegistry`, `ItemDefinitions`). **Non-Gangland consumers must either:**
1. Reuse this resolver if they have Keystone item converters
2. Implement their own `ShopDisplayResolver` (interface in keystone-shop)

### 23 of 37 files import inventory-api

- **Transactions**: ShopPurchaseService, ShopSellService, ShopBarterService all need `InventoryHandler` to deliver items to player
- **Results**: BarterResult, PurchaseResult read/write from Inventory
- **Valuators**: CategoryBarterValuator, CategorySellValuator need Fill utilities for admin UI rendering
- **Categories**: BarterCategory, SellCategory manage items

**Consequence:** Keystone shop-api must remain on inventory-api (provided scope). Headless separation (views in consumer) is the goal, but item-delivery still couples to inventory-api.

### No explicit PaymentHandler implementations found

`PaymentHandler` is an interface. Implementations likely:
- Injected from gangland-impl as a bean
- Called by transaction services (ShopPurchaseService.purchase(..., paymentHandler))
- Wrapped in npc-shops listeners (e.g., TraderBuyListener.onBuy() → PaymentHandler.charge())

**Action needed:** Read npc-shops listener files to confirm PaymentHandler wiring.

### ItemValuation is pure

`ItemValuation` record (BigDecimal, Source enum, categoryId) has zero dependencies. Easily portable to Keystone.

### Shop views sit on MultiPanelInventory

All 6 view classes (ShopAdminFlow, ShopAdminView, etc.) import and extend `MultiPanelInventory`/`Panel`/`FlowSession`. This ties shop-api to inventory-api's flow framework. **WS2 replacement of inventory-api with Oriel will require rewriting views on Oriel's MenuFlow.** This is not a blocker for Keystone promotion (views stay in consumer), but it means:
- gangland-npc-shops (current): views on inventory-api + MultiPanelInventory
- Post-WS4: views rewritten on Oriel MenuFlow
- A non-Gangland consumer using Keystone shop must provide their own views

### GanglandShopDisplayResolver needs Bartizan for weapon names

Not found directly in census, but ShopDisplayResolver rebuilds items through ItemDefinitions.pristine(). If an item is a weapon (Bartizan-backed), the pristine rebuild requires Bartizan's ItemVocabulary to be registered. **Non-Bartizan consumers may see incomplete display names for unknown item types.**

### No Traders/Bankers outside npc-shops yet

The `ShopRegistry` is general-purpose (registry, not entity manager), but npc-shops is the only consumer. A module wanting to offer shops (e.g., quest rewards, faction quartermaster, dungeon loot merchant) must:
1. Create entity manager classes (like TraderData, BankerData)
2. Create view classes on Oriel's menu framework
3. Implement PaymentHandler for their economy
4. Wire up commands and listeners

**Cost:** ~80 files per shop type (npc-shops = 82 files for trader + banker). A lighter consumer model (data-driven vs. type-per-entity) would help.

### No settings/Messages migration plan yet

Bartizan-style: modules bring their own settings and message YAML. Current Gangland:
- Trader: settings (L658, 18 lines)
- Banker: settings (L681+)
- Messages: 29 BANKER_*, 1 SHOP_*

**WS4 action:** Move to gangland-npc-shops module YAML (not gangland-impl). npc-shops already ships `npc/cops.yml`, etc.; add `npc/trader_traits.yml`, `npc/bank_tiers.yml`, `npc/trader_settings.yml`, `npc/banker_settings.yml`. Message strings move to module-owned Messages enum (legacy migration per WS6 assumption).

---

## Planner summary

**shop-api: 37 main files + 8 tests, 23 VIEW (inventory-coupled) + 14 HEADLESS.** Keystone shop module: registry/transaction/valuation core (14 HEADLESS files + models). Consumer views: 6 shop view classes rewritten on Oriel MenuFlow post-WS2. Economy: PaymentHandler interface (implementation in consumer). Dependencies: Keystone hooks (EconomyHandler), keystone-item (ItemDefinitions), keystone-persistence, keystone-common, keystone-bean already in-tree. **Blocker:** CLAUDE.md layering rule contradicts Keystone promotion; needs user clarification that "headless" shop (no views, no menu ownership) is acceptable.
