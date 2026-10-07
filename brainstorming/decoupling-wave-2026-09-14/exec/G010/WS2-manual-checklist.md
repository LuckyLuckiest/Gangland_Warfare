# WS2/WS4 manual checklist — client-session interactive rows (G3, ruling W42)

Started per fix round 1, F6 (Opus review `WS2-G3-review.md`, orchestrator ruling W42): console-only smoke
(`WS2-G3-report.md`'s S1) proves the server boots and the menu registry wires up, but every row below needs a real
client to render a chest inventory and click it — a console has no client viewport. Console S1 stands as the boot
proof; this file is the outstanding client-side gap. Run against the deployed 0.10.0 build (core jar +
`modules/*.jar`) on `E:\Documents\Minecraft\Test Server\`.

Check off with date + tester name; note the client MC version used (ViaVersion is soft-depended, so cross-version
clients are worth spot-checking if available).

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 1 | `/glw phone` | Opens the phone menu (`phone.yml`); every static button renders with its configured icon/texture; clicking an app tile navigates without error | ☐ |
| 2 | Gang info menu (`/glw gang info` or equivalent tile from `/glw phone`) | Gang roster/stat panels render; member entries paginate if the gang has more than one page of members (`SimplePagedMenu` path, F1) — Home button appears only from page 2 onward, next/prev textures show real player-head skins (not blank heads) | ☐ |
| 3 | Bank menu (`phone_banking.yml`, incl. the anvil branch) | Deposit/withdraw tiles work; the anvil-input branch (custom amount entry) opens, accepts a typed amount, and applies it correctly | ☐ |
| 4 | Bounty sign view (`-bounty` sign, VIEW) | Right-clicking a bounty sign opens the bounty list (`BountyAspect.openBountyView`, `SimplePagedMenu`); online/offline status colors correct; pagination + Home/nav buttons behave as row 2 | ☐ |
| 5 | Gang search + filter (`phone_gang_search.yml`) | Search opens with border-only decoration (no full fill — F2's fix); typing/selecting a filter narrows results; paginated result list navigates correctly | ☐ |
| 6 | `/glw debug inv-data` while a menu is open | Shows the currently-open menu's identity/state (title, page, slot count) matching what the client sees on screen | ☐ |
| 7 | `/glw debug multi` (nav-button smoke, `DebugCommand`) | Opens a `SimplePagedMenu` test list; next/prev/home textures render from `Settings`' configured `Multi_Inventory` tags (not blank heads); clicking cycles pages in place (same inventory view, no re-open) | ☐ |

## Notes for the tester (G3 rows)
- Rows 2, 4, 5, 7 exercise the F1/F2/F3 fix-round-1 behavior (nav textures, unconditional paged border, left-click
  fallback via `onAnyClick`) that unit tests can only verify structurally (slot positions, materials) — the actual
  skin/texture rendering and real click-type dispatch need a live client per the Opus review's "Cannot verify"
  section.
- If any row fails, capture: server console output at the time of the click, the `.yml` definition involved, and
  the client's MC version, then file it in the bug docket per CLAUDE.md's mandatory bug-docket workflow before
  reporting back.

## G4 — npc-shops (trader/banker flows, client-only rows)

Added per WS2 G4 (batch 4): the 16-file generic `Panel`→`Panel` swap + `BarterView`/`SellView`'s re-point onto
`keystone-inventory`'s `MenuFlow`/`ChestMenuBuilder`/`DropzoneSlotComponent`. Unit tests
(`BarterViewItemSurvivalTest`, `SellViewItemSurvivalTest`) pin the item-return mechanism structurally (a real
`ChestMenu` + a mocked `Player`/`PlayerInventory`) but cannot exercise a real client's click routing, drag-and-drop
feel, or in-place panel-swap flicker — those need a live session, same rationale as the G3 rows above.

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 8 | Trader: mode select → BUY | Right-click a trader NPC; mode-select panel opens (BUY/SELL/close); clicking BUY switches in-place (same inventory, no flicker) to the shop browser | ☐ |
| 9 | Trader: shop browsing + pagination | Shop browser paginates correctly (prev/next/page-info); clicking an item pivots to the negotiation panel with the right asking price | ☐ |
| 10 | Trader: negotiate → BUY confirm | Negotiation panel's BUY button charges correctly and ends the flow; BUY AMOUNT pivots to the quantity-picker panel (+/- buttons, custom-amount anvil, mode multiplier anvil) and confirming buys the staged quantity | ☐ |
| 11 | Trader: BARTER flow (drop-zone) | From negotiation, BARTER opens the 20-slot dropzone; dragging/shift-clicking items in works (multi-slot stacking placement); the running offer value updates live; CONFIRM only enables once the offer meets the asking value; CLEAR returns everything; BACK returns to negotiation and returns un-accepted items | ☐ |
| 12 | Trader: SELL flow (drop-zone) | Same dropzone mechanics as row 11 for the sell panel; CONFIRM sells only the items the valuator accepted, returning the rest | ☐ |
| 13 | Trader: barter/sell item survival, live | With items sitting in the dropzone: press Escape — items land back in your inventory; disconnect and rejoin — items were returned (check server log / inventory); trigger `/glw reload` while a dropzone has items staged — items return (this is the `InventoryService` shutdown/reload sweep + `DropzoneSlotComponent`'s `ItemHoldingComponent` contract, only unit-verified with a mocked player so far) | ☐ |
| 14 | Banker: menu → deposit/withdraw | `/glw bank` or a banker NPC opens the account menu; DEPOSIT/WITHDRAW pivot in-place to the amount panel; +/- buttons and the custom-amount anvil work; confirming applies the transaction and returns to the menu with updated balances | ☐ |
| 15 | Banker: create account | With no account: OPEN ACCOUNT pivots to the create panel; confirming opens an anvil for the account name; a valid name creates the account and returns to the menu | ☐ |
| 16 | Banker: claim rewards | REWARDS pivots to the claim panel; ready/cooldown/disabled icon states render correctly; claiming a ready reward updates the balance and re-renders the cooldown state in place | ☐ |
| 17 | Banker: upgrade tier | UPGRADE pivots to the upgrade panel; confirming spends from the bank balance and returns to the menu with the new tier | ☐ |
| 18 | Banker: rename account | RENAME ACCOUNT opens an anvil (not a panel); a valid new name renames the account and returns to the menu regardless of success/failure | ☐ |
| 19 | Banker: online-banking from phone | `phone_banking.yml`'s banking tile opens the SAME banker flow with no physical NPC (`BankerFlow.startFromPhone`) — title/behavior falls back to "Online Banking" correctly | ☐ |

### Notes for the tester (G4 rows)
- Rows 11-13 are the highest-risk rows — the item-return contract re-point (§0d) replaced the old
  `MultiPanelInventory#onEnd`-based cleanup with `DropzoneSlotComponent` (an `ItemHoldingComponent`); row 13
  specifically targets close paths a unit test can only simulate (a mocked `Player`), not a real client session,
  real disconnect, or a real `/glw reload`.
- All panel-to-panel transitions in rows 8-19 should feel instant/in-place (no close-then-reopen flicker) whenever
  the target panel's size and title both match what's already open — `MenuFlow`'s in-place-switch optimization
  (ported from the old `MultiPanelInventory`); a differently-sized/titled transition (e.g. menu → dropzone panels,
  which are both 54-slot but differently titled) is expected to visibly reopen.
- If any row fails, capture: server console output at the time of the click, which panel/file was involved, and
  the client's MC version, then file it in the bug docket per CLAUDE.md's mandatory bug-docket workflow before
  reporting back.

## WS4 G1a — shop admin editor + trader/banker (client-only rows)

Added per WS4 G1a (batch 6a): `gangland-ui/shop-api` deleted, `keystone-shop` (provided everywhere, B2) supplies
the 26 headless classes, and the 9 admin-editor UI files (`ShopAdminFlow`/`ShopAdminFlowSession`/`ShopAdminView`/
`PriceEditorView`/`SellCategoryItemsAdminView`/`BarterCategoryItemsAdminView` + their 3 listeners) were relocated
bodily to `gangland-impl`'s `shop/admin/{view,listener}` — still on the OLD inventory-api
(`MultiPanelInventory`/`Panel`/`Fill`/`InventoryUtil`), unchanged this gate. The `ShopAdminOpener` seam (B1) is new
but has no UI of its own to click — it only routes `npcshops`' existing "sneak-open admin" / `/glw shop edit`
entry points to the same `ShopAdminFlow` as before. None of this changes what a client sees; these rows re-verify
the admin editor and the trader/banker flows still work correctly after the relocation + import rewrite, since a
console run cannot open a chest inventory.

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 28 | `/glw shop edit <key>` (or sneak-right-click a trader/banker with `gangland.shop.admin`) | Opens the 3-tab admin editor (BUY/SELL/BARTER); dropping or shift-clicking an item into the BUY tab adds an entry; the SELL/BARTER "+ Add category" anvil creates a new category; navigating pages (prev/next) works | ☐ |
| 29 | Admin editor: price editor | Left-clicking a BUY entry (or a category's "Edit base price" button) opens the price editor; the +/- buttons adjust by the mode multiplier; the mode anvil accepts a custom multiplier capped at `settings.yml`'s new `Shop: Max_Mode_Multiplier`; the custom-price anvil accepts an exact value; SAVE commits and returns, CANCEL discards and returns | ☐ |
| 30 | Admin editor: category item editor | Opening a SELL or BARTER category (left-click from the root) shows its template items; dropping/shift-clicking adds an item; left-click on an item opens its per-item price editor; right-click removes it; the category's own base-price button opens the same price editor | ☐ |
| 31 | Admin editor: save-on-close | Closing the editor (ESC or the close button) persists the edited definition — reopening `/glw shop edit <key>` (or `/glw shop list`) shows the changes made in rows 28-30 | ☐ |
| 32 | Trader: buy/sell/barter still work | With `npcshops` deployed, right-click a trader NPC and run the full BUY → confirm, SELL (drop-zone), and BARTER (drop-zone) flows end to end — confirms `TraderSettingsImpl`'s move from core `Settings` to `npc/trader_settings.yml` (Respawn_Cooldown, Head_Track_Radius, Fallback_Trait_Id, Sell.Max_Offer_Slots, Sell.Mood_Per_Sale, Tip_Amount) didn't silently zero out any of those knobs | ☐ |
| 33 | Banker: deposit/withdraw still work | `/glw bank` or a banker NPC: deposit, withdraw, and check head-track/invulnerability behavior — confirms `BankerSettingsImpl`'s move to `npc/banker_settings.yml` (Head_Track_Radius, Max_Health, Invulnerable, Fallback_Tier_Id) didn't silently reset any of those knobs to their hardcoded defaults | ☐ |

### Notes for the tester (WS4 G1a rows)
- Rows 28-31 are the highest-risk rows: the 9 relocated files' shop-domain imports were mechanically retargeted
  from `org.luckyraven.gangland.shop.*` to `org.luckyraven.keystone.shop.*` (26 classes now living in Keystone's
  `keystone-shop`) while every `org.luckyraven.gangland.inventory.*` import was left untouched — a wrong import
  would be a compile error, not a runtime bug, so these rows are really testing behavior, not wiring. `host.rerender()`
  is still `MultiPanelInventory.rerender()` in this gate (G1b's job is the `MenuFlow` rewrite) — panel transitions
  and in-place refreshes should look and feel identical to before this batch.
- Rows 32-33 exist because `TraderSettingsImpl`/`BankerSettingsImpl` changed from reading core `Settings` statics
  to reading a `FileHandler`-backed module YAML at construction time (`onInitialize` reload included) — a wrong
  key name or a missing `Sell:`/`Max_Offer_Slots` nesting would silently fall back to the hardcoded default rather
  than error, so only a live check confirms the values actually loaded from `npc/trader_settings.yml`/
  `npc/banker_settings.yml` rather than silently using defaults throughout.
- If any row fails, capture: server console output at the time of the click, which panel/file was involved, and
  the client's MC version, then file it in the bug docket per CLAUDE.md's mandatory bug-docket workflow before
  reporting back.

## WS4 G1b — shop admin editor, rebuilt onto keystone-inventory (client-only rows)

Added per WS4 G1b (batch 6b): the 9 admin-editor files (still unchanged in G1a) are now rebuilt off
`MultiPanelInventory`/inventory-api onto Keystone's `MenuFlow`/`ChestMenuBuilder` (`Panel<ShopAdminFlowSession>`,
`ItemComponent`, `flow.switchTo`/`.back`/`.rerender`/`.suspend`/`.resume`). **Rows 28-31 from the WS4 G1a section
above now exercise this rebuilt code** — re-run them against the new implementation; the rows below are the
G1b-specific additions covering what actually changed in the port (not just re-verifying G1a's ground again).

§0d finding, confirmed by reading every click handler in the 9 files before porting: **none of them holds a player
item in a menu slot.** The BUY-tab / SELL-category / BARTER-category "drop an item to add a template" mechanic
`event.setCancelled(true)`s the click *before* reading the source item and cloning it — the original item never
leaves the player's cursor or bottom inventory, so there is nothing for Keystone's item-holding contract
(`ItemHoldingComponent`, `builder.interactive(slot)`) to apply to; see WS4-G1b-report.md's slot-by-slot table for
the full accounting (every slot in all 9 files). Rows 34-36 below re-verify this peek-and-clone contract survived
the port unchanged, since it's the one piece of behavior a wrong click-routing translation could silently break.

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 34 | Admin editor: drop-to-add still works, item never consumed | On the BUY tab, drag an item from your inventory onto an empty interior slot (or shift-click it from your hotbar) — a new BUY entry appears with a cloned copy; **the original item is still in your hand/inventory afterward**, not consumed or duplicated away. Repeat for a SELL category's item grid and a BARTER category's item grid (both should behave identically) | ☐ |
| 35 | Admin editor: panel-switch mid-edit doesn't lose anything | Start dropping items into a BUY/SELL/BARTER slot, then switch tabs or navigate into a sub-panel (category editor, price editor) and back — no item is ever "stuck" anywhere (there's nothing to get stuck, since nothing is ever held — this row exists to catch a regression if a future change adds a real drop-zone here) | ☐ |
| 36 | Admin editor: ESC / disconnect mid-edit doesn't half-write an entry | Open the admin editor, add a BUY entry and edit its price but do **not** click SAVE, then press Escape (or disconnect and rejoin) — reopening the editor shows the state as of the **last completed SAVE**, never a half-edited price; the `ShopEditedEvent`/`ShopEditPersistenceHandler` write on flow-end persists whatever the working-copy session held at that moment, exactly once (not lost, not double-written) | ☐ |

### Notes for the tester (WS4 G1b rows)
- Row 34 is the highest-risk row for this batch: the click-routing translation (raw `InventoryClickEvent` →
  `ItemComponent.onLeftClick`/`onRightClick`/`onAnyClick` for display slots, but the drop-to-add path stays a raw
  `@EventHandler(priority = EventPriority.HIGH)` listener bridging into the view's own `handleClick`, mirroring
  `BarterView`/`BarterSessionListener`'s already-shipped pattern) is new code, not a mechanical rename — a mistake
  here would show up as either "nothing happens when I drop an item" or "my item vanishes," both silent failures
  from a console's point of view.
- Row 36's "exactly once" claim rests on `MenuFlow`'s `onEnd` firing exactly once per flow (set once at
  `ShopAdminFlow.start()`, not per-panel) — confirmed by reading the framework, not independently live-tested
  outside console smoke (which can't open a chest inventory to exercise the SAVE/Escape paths at all).
- If any row fails, capture: server console output at the time of the click, which panel/file was involved, and
  the client's MC version, then file it in the bug docket per CLAUDE.md's mandatory bug-docket workflow before
  reporting back.

## G5 — turf · cops-n-crooks · gadget (client-only rows)

Added per WS2 G5 (batch 5): turf's Quartermaster flow (`TurfPowerupFlow`, generic `Panel`→`MenuFlow` swap),
cops-n-crooks' detainment paperwork/handcuff-bribe GUIs (standalone `ChestMenu` builds, no flow), and gadget's
car-sign hover view (also standalone). None of the three views/flows in this gate have a player-placeable slot
(§0d does not apply — no `DropzoneSlotComponent` needed; every button click is display-only or fires an
economy/service call, confirmed by reading every `ItemComponent`/slot declaration before porting: zero
`.interactive(true)` calls anywhere in this gate's files).

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 20 | Turf: Quartermaster root menu | Right-click a Quartermaster NPC (or however it's opened); root menu shows owner/garrison/buffs/gang-bank info correctly; BUFFS and GARRISON buttons switch in-place (same inventory, no flicker — both sub-panels are smaller than the 3-row root, so this is a full reopen, not in-place — confirm which it actually is) | ☐ |
| 21 | Turf: buff catalogue | Buff catalogue lists every configured powerup with correct cost/effect/duration; clicking an affordable one debits the gang bank, activates the buff, and re-renders in place (`MenuFlow.rerender()`) so the "currently active" count updates immediately; an unaffordable one shows the insufficient-funds message and does not activate | ☐ |
| 22 | Turf: garrison | Garrison panel shows stock + per-defender cost + gang bank; BUY adds one defender (debits bank, re-renders in place); insufficient funds shows the deny message | ☐ |
| 23 | Turf: back/close navigation | BACK from either sub-panel returns to the root menu; CLOSE from any panel ends the flow cleanly (no lingering listener, confirmed via a second open working normally) | ☐ |
| 24 | Cops: detainment paperwork | Get jailed, right-click the Jail Paperwork item; three-row GUI opens with BAIL / BRIBE / SENTENCE / INFO; BAIL with sufficient funds releases and closes; BRIBE attempts the jail bribe (success/fail messages both work); SENTENCE button just closes (informational) | ☐ |
| 25 | Cops: handcuff bribe | Get handcuffed, right-click the guarding cop; single-button GUI opens with the bribe cost/balance; clicking it attempts the bribe (success title+subtitle, or insufficient-funds message); CLOSE dismisses without side effects | ☐ |
| 26 | Cops: restrained-player inventory access still blocked | While either GUI above is open, confirm `DetainmentGuiAccess`'s allowlist still lets clicks on the GUI's own buttons through while every OTHER inventory-open attempt for a restrained player stays blocked (this is `DetainmentListener`'s pre-existing logic, unmodified by this gate — verifying it still composes correctly with Keystone's `MenuListener` is the point of this row) | ☐ |
| 27 | Gadget: car sign hover view | Interact with a `view` sign pointed at a car; single-row GUI opens showing the car's stats (speed/acceleration/health/durability/fuel) with no clickable buttons (display-only) | ☐ |

### Notes for the tester (G5 rows)
- Row 26 is the one genuinely novel verification this gate needs live: `DetainmentListener.onInventoryClick`
  unconditionally cancels clicks for a restrained player (except their own crafting-type inventory), and Keystone's
  `MenuListener.onClick` does not set `ignoreCancelled`, so it still dispatches the paperwork/bribe buttons' click
  handlers regardless of that earlier cancel — the same "two independent listeners, one cancels, the other still
  dispatches" composition the old `InventoryClickHandler` relied on. Traced from source, not live-tested.
- Row 20's in-place-vs-reopen note: `TurfPowerupMenuView` is 3 rows (27 slots), `TurfPowerupBuffCatalogueView` is 4
  rows (36 slots) and `TurfPowerupGarrisonView` is 3 rows (27 slots, different title) — `MenuFlow.switchTo`'s
  in-place optimization only fires when BOTH size and title match the currently-open menu, which none of these
  pairs do, so every transition in this flow should visibly reopen, not switch in place. Worth confirming this
  matches the OLD `MultiPanelInventory`-based behavior (same size mismatch existed before this gate) rather than a
  regression.
- If any row fails, capture: server console output at the time of the click, which panel/file was involved, and
  the client's MC version, then file it in the bug docket per CLAUDE.md's mandatory bug-docket workflow before
  reporting back.

## CUT — inventory-api deletion, loot-chest chest-opening view + wand preview (client-only rows)

`gangland-ui/inventory-api` is deleted outright this gate. Every remaining console-unverifiable surface: the
loot-chest opening view (now `SharedLootInventory`, a raw Bukkit `Inventory`, replacing the deleted
`InventoryHandler`) and the admin wand-preview screen (rebuilt onto `ChestMenuBuilder`/`PagedRegion`, with LS-30
and LS-31 fixed as a free side effect — see the CUT report).

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 37 | Loot chest: place + open (shared inventory) | `/glw lootchest wand`, right-click an allowed block to place a chest, right-click again to open it; the chest GUI opens with randomly-placed loot items | ☐ |
| 38 | Loot chest: take an item, close, reopen | Take one item from an open chest, close the menu, reopen it — the taken item stays gone (not regenerated), the rest of the loot is unchanged, and cooldown/hologram behavior is unaffected by the `InventoryHandler` → `SharedLootInventory` swap | ☐ |
| 39 | Loot chest: two players share one chest | Two players open the same placed chest at the same time; both see the same live contents; an item taken by one player disappears for the other in real time (confirms the raw shared `Bukkit.createInventory` still behaves like the old shared `InventoryHandler` did — no per-viewer copy) | ☐ |
| 40 | Loot chest: cursor item survives a menu close | With an item held on your cursor while a loot-chest GUI is open, close the menu (Escape) or disconnect/rejoin — the cursor item returns to your inventory or drops at your feet, never vanishes (this is vanilla Bukkit `InventoryView`-close behavior, unrelated to `SharedLootInventory`, but worth a live confirmation since the wrapper class is brand new) | ☐ |
| 41 | Wand preview: open + paginate | With the loot-chest wand in hand, left-click to open the config/preview screen; the 28-slot interior grid shows the chest's loot table entries; Next/Prev/Back buttons at the bottom row page through correctly with no visual glitches | ☐ |
| 42 | Wand preview: edit an entry, hotbar-slot-safe (LS-30 regression check) | Open the wand's config screen, then **switch hotbar slots** (e.g. press `2` to select a different item) before completing an edit — the edit still applies to the wand, not to whatever item is now in the selected hotbar slot. This is the live confirmation of the LS-30 fix (edits now target the exact slot the wand was in when the screen opened, not `getItemInMainHand()` at click time) | ☐ |
| 43 | Wand preview: allowed-block exact match (LS-31 regression check) | Configure the wand's allowed-block list to include `CHEST` only (not `TRAPPED_CHEST`); confirm right-clicking a `TRAPPED_CHEST` with the wand does **not** let you place a loot chest there, while a plain `CHEST` still works — the live confirmation that the block check is now an exact match, not a substring match | ☐ |
| 44 | `/glw lootchest edit` (LootChestWandEditCommand) | Run `/glw lootchest edit` while holding a configured wand; the same config screen opens as row 41's left-click path, with identical behavior | ☐ |

### Notes for the tester (CUT rows)
- Rows 37-40 exercise `SharedLootInventory` (`gangland-ui/lootchest-api`) — a brand-new, ~40-line class with no
  existing unit test (a real Bukkit `Inventory` isn't unit-testable without a server); these rows are the only
  regression guard for it besides a direct source read.
- Rows 41-44 exercise the wand-preview rewrite (`gangland-impl`'s `LootChestWand`/`LootChestWandEditCommand`/
  `LootChestWandListener`) — no render-path unit test exists for the same reason every other real-GUI gate in this
  wave has none; console can only confirm the module compiles and the command resolves without an exception.
- If any row fails, capture: server console output at the time of the click, which panel/file was involved, the
  client's MC version, and the wand's exact NBT state if relevant, then file it in the bug docket per CLAUDE.md's
  mandatory bug-docket workflow before reporting back.

## WS3 merge — loot chests are now the `gangland-lootchest` runtime module (client-only rows)

Merged 2026-09-22 (`5b915c17`, then G4+G5 fast-forwarded in as `837966c3`). Loot chests + `HologramService` moved
out of `gangland-ui/{lootchest-api,hologram-api}` into the 7th runtime module, `gangland-features/gangland-lootchest`
(id `lootchest`), consuming Keystone's `keystone-hologram` directly; as of `837966c3` the module also owns its own
config (`lootchests/loot_chest_settings.yml`), messages (`lootchests/lootchest_messages.yml` +
`lootchest_messages_es.yml`) and `commands.json` (4 entries) — `gangland-ui/lootchest-api` is now fully deleted.
Console-verified already (merge smoke, `cut-full-regression`, both merge rounds): the module loads with
`Runtime modules: 7 loaded, 0 fault(s)` (proving `HologramService`+`registerProtection`,
`LootChestManager`/`LootChestService`/`LootChestLoader`/`LootChestSettings`/`GanglandLootChestMessages` bean
construction, the `loot_chest` table repository registration, and the 9 `LootChestWandTag` NBT-tag registrations
all succeeded with zero faults), `/glw lootchest` resolves cleanly ("You need to be a player", not an exception —
and correctly comes out as "¡Necesitas ser un jugador para usar esto!" with `Language: es` set, confirming the
i18n pipeline itself works end to end with the module loaded), `/glw lootchest help` shows the module's own 4
commands.json entries correctly (help text itself is not translated, by design — same as every other module),
`/glw reload` completes cleanly with the module loaded (both with and without a legacy `settings.yml Loot_Chest:`
block present), and `plugins/Gangland_Warfare/lootchests/{loot_chests.yml,tiers.yml,loot_chest_settings.yml,
lootchest_messages.yml,lootchest_messages_es.yml}` are all confirmed extracted onto disk from the module jar. The
G4 legacy-block migration warning is also fully console-verified both directions: a `settings.yml` with a
`Loot_Chest:` block logs the exact targeted warning ("...those keys moved to plugins/Gangland_Warfare/lootchests/
loot_chest_settings.yml and lootchest_messages.yml...") once at boot and once per `/glw reload`; a `settings.yml`
without one logs nothing. What's left needs a real client — the 26 Spanish `LootChestMessagesProvider` strings
specifically could not be triggered from console (every command that would display one gates on
`sender instanceof Player` and returns silently otherwise, confirmed by reading `LootChestRemoveCommand`/
`LootChestWandEditCommand`), so their actual Spanish *content* (not just that the file loads without fault, which
is confirmed) still needs row 46/rows below with `Language: es` set:

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 45 | Module present: wand + place + open (WS3 deferred row 2) | `/glw lootchest wand` gives the wand; right-click an allowed block places a chest; right-click again opens it (shared inventory, take-only per the CUT-gate fix); take an item, close | ☐ |
| 46 | Cooldown hologram text (WS3 deferred row 2, cont.) | After the chest empties and enters cooldown, a hologram spawns above it showing the `LOOT_CHEST_HOLOGRAM_COOLDOWN` text (now rendered via `keystone-hologram`, not the deleted `gangland-ui/hologram-api`) | ☐ |
| 47 | Cracking-enabled chest always fails (WS3 deferred row 3, docket LS-02) | Configure a chest with `Cracking_Enabled: true`; confirm the session always fails after `Cracking_Time` seconds (`LootChestCrackingFailureEvent` then `LootChestCrackingEndEvent`) regardless of any action taken — this is LS-02's documented dead-minigame shape, not something this merge fixed | ☐ |
| 48 | `/glw reload` survives a placed chest (WS3 deferred row 4) | With a chest placed and in a known state, run `/glw reload` — the chest registry and its hologram both survive (config re-read, in-memory chest re-registered from DB); the reload *mechanism* itself is already console-confirmed clean, this row confirms the *data* survives it | ☐ |
| 49 | Restart persistence (WS3 deferred row 5) | With a chest placed, fully restart the server — the chest row survives (DB round-trip through the relocated `LootChestRepository`/`LootChestTable`, unchanged schema) and its hologram re-spawns from `is_looted`/`respawn_time` | ☐ |
| 50 | No armor-stand leak on disable (WS3 deferred row 6, docket UI-13/UI-15) | Before/after a full chest cooldown cycle + server stop, `world.getEntitiesByClass(ArmorStand.class)` count is unchanged — confirms `HologramService.clear()`/`onShutdown()` (now Keystone's `keystone-hologram`, same body, moved verbatim) still removes every stand it owns | ☐ |

### Notes for the tester (WS3 merge rows)
- Row 45's take-only chest behavior is the CUT-gate fix (rows 37-40 above); this row is the first time it gets
  exercised through the *module's* own `LootChestService`/`LootChestListener` copy (moved verbatim, but worth
  confirming the move didn't silently drop the guard).
- Rows 47/50 pin pre-existing, carried-over docket behavior (LS-02 dead minigame, UI-13/UI-15 hologram-removal
  leaks) — not fixed by this merge, just relocated. A row failing in a *new* way (not matching the docket's
  existing description) is a new bug; failing in the *same* documented way is not a regression.
- If any row fails, same capture/file protocol as the CUT section above.

## WS5 merge — identity (User/UserManager/Level/Bounty/Wanted/rank) moved to `gangland-core` (client-only row)

Merged 2026-09-22 (`f999ab72` → `5b915c17`). `MemberManager`'s `orderingDep` parameter (a `DataConfig.java`
bean-graph ordering hint) was deleted — `UserDataLoader`/`PlayerBootstrapService` were confirmed by direct code
read to already take `MemberManager` as a **direct** constructor parameter (never through `UserManager`), and a
new `MemberCachePopulationOrderTest` pins the cache-readiness invariant with zero `UserManager` reference. The
one thing that check cannot cover — a real `BeanFactory`/`BeanGraph` boot end to end — is now console-verified via
the merge smoke: **a genuinely fresh, empty database** (the shared test-server DB was parked aside, a clean boot
run against a database that didn't exist yet, then the original DB restored afterward) produced
`[Keystone Persistence.FileHandler] Created file: ...\gangland.db` followed immediately by
`Runtime modules: 7 loaded, 0 fault(s)` — no ordering fault, no exception, gang/member/rank/user tables created
successfully with the `orderingDep` parameter gone. `/glw reload` also completed cleanly against that same fresh
database. What's left needs a real client (gang creation itself is player-only, confirmed in WS5's own G0 report):

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 51 | Member's gangId attaches correctly, live (WS5 step-1c, the one part console cannot reach) | Create a gang, have a member join it, confirm `/glw gang`/a `%gangland_*%` placeholder shows the correct `gangId` for that member — immediately after joining, again after `/glw reload`, and again after a full server restart (three separate checks in one row, matching the plan's exact wording: "first load, `/glw reload`, and a fresh empty DB") | ☐ |

### Notes for the tester (WS5 merge row)
- The "fresh empty DB" third of this row is **already console-verified** above (table creation + zero ordering
  fault) — what's left for a human is specifically the *gangId-is-correct* observation, which needs a real gang
  and a real member, i.e. a player.
- If this row fails, it points at the `orderingDep` deletion specifically (`DataConfig.java`,
  `userManager()`/`offlineUserManager()` bean methods) — capture the exact symptom (member shows no gang / wrong
  gang / an exception) and file it in the bug docket before reporting back.

## WS5/WS6 merge — gang domain → `gangland-gang` module (WS5 G1-G3) + civilians module-owned messages (WS6 G3)

Merged 2026-09-23 (`f5caaaca`/`9e2c378e` → `89746105`). **BLOCKING: docket T-53 (P0).** Deploying the gang module
jar as the 8th module (`cut-full-regression`, real Paper server, Keystone 1.11.1) makes `Gangland.onEnable` throw
`IllegalStateException: Failed to instantiate @Configuration class org.luckyraven.gangland.gang.GangMembershipInstaller`
→ `Cannot resolve required parameter of type ...GangMembership for constructor` every single time — the plugin
disables immediately and every `/glw` subcommand then throws "unexpected error" (command tree never registered).
Root cause: `GangMembershipInstaller` (gang module) is a bare `@Configuration` class instantiated via its own
constructor rather than a `@Bean` factory method, so `BeanGraph`'s topological sort — which only reads `@Bean`
*method* parameters as ordering edges (house convention, `feedback_bean_ordering_via_params.md`) — has no edge
forcing it after `IdentityContractConfig.gangMembership()` (gangland-impl, the zero-arg core bean it depends on).
Full stack trace, fix direction and reproduction steps are in the docket entry; recorded there rather than only
here per CLAUDE.md's mandatory bug-docket workflow. **Every row below that needs the plugin to actually enable
with the gang module present is blocked until T-53 is fixed** — re-run this section's automated parts (module
count, `/glw module list`, `/glw gang help`, `/glw rank list`) as part of verifying that fix, not as a fresh ask.

A second, lower-severity finding (docket **T-54**, P1) surfaced while isolating the WS6 civilians check around
T-53: with the gang module genuinely absent (the only way to get the plugin to boot this round), every
`/glw reload`/autosave logs `Failed to save data for repository: Permission` → `IllegalStateException: No data
supplier set for repository: PermissionRepository`. WS5 moved `RankManager` (the sole caller of
`PermissionRepository.setDataSupplier`) into the optional `gang` module while leaving `PermissionRepository`
itself core-hosted and unconditionally registered — so any server that genuinely runs without the gang module
(which WS5 G1 explicitly designed `gadget`/`civilians`/`cops-n-crooks` to support via the `GangMembership` inert
holder) gets this ERROR every reload/autosave cycle forever. Not a boot-blocker, just a permanent log-spam/silent
partial-save regression on gang-less installs.

What *was* independently verified this round, isolated from T-53 by dropping `gang`/`turf`/`mail` from the deploy
set (civilians has no `Depends: [gang]` edge, so it boots fine alone) — **WS6 G3 is otherwise sound**:
- `npc/civilian_messages.yml` and `npc/civilian_messages_es.yml` both extract from the module jar on boot
  (`[Keystone Persistence.FileHandler] Created file: ...\npc\civilian_messages.yml (from module resources)`,
  same line for `_es.yml`).
- `/glw civilian list` (console-reachable — unlike every other module's message-bearing command, it has no
  `sender instanceof Player` gate) renders "No civilians are currently active." under `Language: en` and, after
  parking/editing/restoring `settings.yml` to `Language: es`, "No hay civiles activos actualmente." — confirms
  `LocalizedModuleYaml`'s language-suffixed file selection works end to end, both directions.
- The fresh-install no-warning path is implicitly confirmed: neither boot (en or es) logged a `Civilian:` legacy
  block warning, and no legacy block exists on this server's message files pre-merge.
- **Not verified this round**: the legacy-block-warning-fires path (WS6's own deferred row 3/4 — a
  `message_<lang>.yml` with a `Commands.Civilian:` block should log the targeted warning once per boot/`/glw
  reload`). Could not locate a persisted `message_en.yml`/`message_es.yml` anywhere under
  `E:\Documents\Minecraft\Test Server` to edit a synthetic block into — `LanguageLoader` clearly loads and
  diff-reports against message content (`message_es.yml is missing 116 declared key(s)` appears in both boot
  logs) but no matching file was written to disk in this topology; worth a follow-up to find where Keystone's
  `LanguageLoader` actually keeps it before asserting this path is broken. The *mechanism* itself
  (`Settings.warnIfLegacyShopBlockPresent`, generalized in WS6 G3) already has unit coverage per WS6's own
  G3-report.md.

The rows below all need either a real client (Player-gated commands, confirmed by reading the source) or a
working boot with the gang module present (blocked by T-53, then T-55 — see the END batch update below) —
several rows need both:

| # | Row | What to verify | Status |
|---|-----|-----------------|--------|
| 52 | `/glw filter gangs ...` opens and behaves correctly | Confirms `GangItemSourceContributions` resolves `GangMenuItemSourceContribution` and the restored `GangFilterAdapter`/`MemberFilterAdapter`/`GangFilterRegistration` render real gang/member rows, not the empty list the pre-fix1 gate shipped (WS5 `G1-G3-fix1-report.md` F1). `FilterCommand` gates all three action handlers on `sender instanceof Player` — no console path exists even once T-53/T-55 are fixed. | ☐ |
| 53 | `/glw waypoint gangid <unknown id>` shows `GANG_DOESNT_EXIST` | `WaypointGangIdCommand` casts `sender` to `Player` unconditionally in its action *and* both tab-completion suppliers (no console path). Confirms W54 F4/the restored `gang == null` guard via `GangMembership#nameOf` for an id that names no real gang, while a *known* id still shows the real gang name (not the raw id) per the same fix. | ☐ |
| 54 | Gang menus open and render (`/glw gang`, `/glw gang members`, etc.) | Real-player confirmation that the menu GUIs built on `keystone-inventory` render actual gang/member data through the item-source seam, matching what row 52's filter check exercises from the search side — this is exactly the bean `GangConfig.gangItemSourceContribution()` that T-55 crashes on, so it doubly needs T-55 fixed first. | ☐ |
| 55 | Placeholders resolve for a real player | `/glw debug placeholder-data` (the `%player%`/`%info%`/`%user_gang-id%` test) also gates on `sender instanceof Player`, printing "Can't process non-player data." from console — confirmed by reading `DebugCommand.getPlaceholder()`. Needs a real player once T-53/T-55 are fixed. | ☐ |
| 56 | Legacy `Civilian:` block warning fires (WS6 G3 deferred rows 3/4) | With a `message_en.yml` (or `_es.yml`) carrying a `Commands: Civilian: ...` block, boot and `/glw reload` should each log the targeted warning once. Not reachable this round — see the note above; find where `LanguageLoader` actually persists the language file on this server first. | ☐ |

### Notes for the tester (WS5/WS6 merge rows)
- Rows 52-55 all require a working 8-module boot first (the plugin cannot enable with the gang module present at
  all right now) — attempting them against the current state will just reproduce whatever boot crash is still
  open (see the END batch update immediately below for the current one), not test what the row asks.
- Row 56 needs someone to first locate where the core message file actually lives on a real install before it's
  testable at all — it may simply need `Language` toggled and the server booted once first to force a write, or
  it may reveal `LanguageLoader` doesn't persist a copy to disk in every topology (worth its own docket entry if
  so — check first whether that's already documented as intended behavior).
- If any row fails in a way that doesn't match the currently-filed docket entries' description, same capture/file
  protocol

## END batch update (2026-09-23, fcc41a32) — T-53/T-54 fixed, a new blocker (T-55) found in their place

`fcc41a32` fixed both bugs above exactly as diagnosed: `GangConfig` now produces `GangMembershipInstaller` from a
`@Bean` factory method (real ordering edge) instead of a bare `@Configuration` class, and `PermissionRepository`/
`PermissionTable` moved into the gang module's own database package. Both verified fixed this round and marked
`fixed` in the docket db (T-53, T-54) with the covering tests (`GangConfigBeanGraphTest`, `CoreRepositoryScanTest`).
T-54's fix is fully proven end-to-end: booting without the gang module (turf/mail correctly refuse with
`module.dependency.missing`, non-fatal, civilians/gadget/lootchest/npcshops load fine) through boot + `/glw reload`
+ one autosave cycle produced **zero errors** — no more `No data supplier set for repository: Permission`.

Fixing T-53 let boot proceed further and immediately hit a **new** crash in the same class, same failure mode
(`Gangland.onEnable` throws, plugin disables, every `/glw` subcommand then errors): `GangConfig` has 4 `@Bean`
methods taking a raw, unqualified `UserManager<Player>` parameter (`gangPlaceholderContribution`,
`gangOptionContribution`, `gangDebugContribution`, `gangItemSourceContribution`) when `DataConfig` registers
**two** `UserManager` beans by design (`@Bean(name = "online"/"offline", isGeneric = true)`) — every other
consumer in the codebase correctly qualifies with `@Qualifier("online")`. Only `gangItemSourceContribution`
crashed this round (`IllegalStateException: Ambiguous bean ... 2 candidates registered`, confirmed identical
across 2 separate boots), but all 4 are equally vulnerable — this is a concrete instance of the already-docketed
`CL-24` (JVM-dependent bean order for beans with equal dependency sets), so which of the 4 actually throws could
change on a recompile. **Filed as docket T-55 (P0), still open — rows 52-55 above are still blocked, now by T-55
instead of T-53.** No genuinely new client-only rows this round; the existing rows 52-56 already cover everything
still outstanding. Full stack trace and fix direction in the docket entry and in `exec/END/end-report.md`.

## END batch update 2 (2026-09-23, 198d101d) — T-55 fixed; the 8-module boot is clean end to end

`198d101d` fixed T-55 exactly as diagnosed: all 4 of `GangConfig`'s `UserManager<Player>` parameters now carry
`@Qualifier("online")`, plus the same qualifier added to every other unqualified `UserManager` constructor/bean
parameter across impl and the modules (a 90-file sweep). Verified fixed this round and marked `fixed` in the
docket db (T-55) with `GangConfigBeanGraphTest` as the covering test. `cut-full-regression` (8 modules, fresh DB)
now **PASSES** end to end: `Runtime modules: 8 loaded, 0 fault(s)`, Gangland enables, `/glw reload` clean, 0
errors — across **2 separate boots on the same DB**, with `rank_tree`/`rank_parent` row counts unchanged both
times (2 and 1) confirming no duplicate rank seeding. `/glw module list` lists all 8 modules including `gang`;
`turf`/`mail` both being present in that list (rather than skipped with `module.dependency.missing`, as they were
in the T-54-proof run without gang) is itself the console-visible proof that their `Depends: [gang]` edge is
satisfied. `/glw gang help` and `/glw rank list` (`owner, member`) both render correctly. `/glw filter gangs`
reaches its `Player`-gate cleanly (`You need to be a player to use this!`) rather than crashing — since this is
the exact `GangConfig.gangItemSourceContribution()` bean T-55 broke, a clean Player-gate response is
console-visible proof `GangItemSourceContributions` now resolves without fault. `/glw debug placeholder-data`
also reaches its `Player`-gate cleanly. `/glw waypoint gangid` was not re-tested — already established via direct
source read (`WaypointGangIdCommand` casts `sender` to `Player` unconditionally, no console path exists at all,
independent of T-53/T-54/T-55).

**Rows 52-55 above are no longer blocked by any boot crash** — the sole remaining gap is that all four are
inherently `Player`-gated commands (confirmed by source, not by a bug), so they still need a real client to
complete. No new row added; their existing text already describes exactly what's left. Full detail in
`exec/END/end-report-2.md`.
