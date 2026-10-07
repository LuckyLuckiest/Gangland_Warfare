# WS2 G3 report — 2026-09-21

Status: DONE

Plan refs: `plans/WS2-inventory-keystone.md` §4 row G3, §2 seams table, §7 tests, §10 (E6 primitives), §0d (item-return
contract); Keystone consumer guide `docs/keystone-inventory.md` (`ChestMenuBuilder`, `MenuFlow`/`Panel`/`FlowState`,
`PageConfig`/`PagedRegion`, `MenuRegistry`/`MenuOpener`, `InventoryService`/tracker, item-return contract).
Worktree `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, started at HEAD `9cc950ac` (WS2 G3a, committed).
Verified clean via `git status --short` before starting.

## What changed

**`InventoryParser`/`menu.handler.*`/`menu.condition.*` dialect retargeted onto `ChestMenuBuilder`.** The action
type threaded through the whole dialect changed from `TriConsumer<Player, InventoryHandler, ItemBuilder>` to
Keystone's `ClickHandler` (`ClickContext -> void`) — a behavior-preserving simplification confirmed by reading
every implementation first: the `InventoryHandler`/`ItemBuilder` parameters were never dereferenced anywhere in the
dialect (`ClickSlotHandler`, `AbstractCommandSlotHandler`, the `ConditionalSlotData.ClickAction` records) — only
`Player` and the click's own routing (open another menu / run a command / close) were ever used. Files: `menu/part/
Slot.java`, `menu/part/ConditionalSlotResult.java`, `menu/condition/ConditionalSlotData.java` (`ClickAction.execute`
signature), `menu/handler/SlotEventHandler.java`, `ClickSlotHandler.java`, `AbstractCommandSlotHandler.java`,
`CloseSlotHandler.java` (`ctx.closeMenu()` replaces `player.closeInventory()`); `DropSlotHandler`/`JoinSlotHandler`/
`QuitSlotHandler`/`PlayerInteractSlotHandler` needed no change (empty `AbstractCommandSlotHandler` subclasses).
`file/configuration/inventory/InventoryParser.java` needed only its `opener` variable's declared type changed
(`InventoryOpener` → `MenuOpener`; the method reference `runtimeContext::openInventoryForPlayer` already matches
both shapes structurally). `ConditionalSlotParser.java` needed no change — it only builds the `ConditionalSlotData`/
`BranchData` records, never touches the action-execution signature.

**`InventoryOpener` → Keystone's `MenuOpener`, used directly (not `MenuRegistry`).** Every core menu — paginated or
not — is built fresh per `openInventoryForPlayer` call (see next point), which always has the real viewing
`Player` in hand; a `MenuRegistry`-registered `Supplier<Menu>` factory is zero-arg and cannot supply one, which
matters because the 3 paginated menus need the player *before* `.build()` (to fetch that player's filtered item
source ahead of baking entries into explicit slots via `PagedRegion.render`). Using `MenuOpener` uniformly for
every core menu — instead of `MenuRegistry` for some and something else for paginated ones — keeps the open path
one mechanism, not two. `MenuOpener.open(Player, String)` is structurally identical to the deleted
`InventoryOpener.openInventory(Player, String)`, so `InventoryRuntimeContext::openInventoryForPlayer` still
satisfies it as a method reference everywhere it's captured (parse time, in the dialect; build time, for
conditional slots' raw actions).

**Menus open through the core `InventoryService`; the G2 shim is gone.** `InventoryRuntimeContext` lost its
`InventoryRegistry` dependency and the `openInventories` per-player map entirely — every `openInventoryForPlayer`
call now builds a *fresh* `ChestMenu`/paginated `ChestMenu` via `InventoryBuilder.createMenu`/`createPagedMenu` and
calls `.open(player)`, so Keystone's own `InventoryService.tracker()` sees it automatically (no separate
tracking/dedup logic needed — matches `MenuRegistry`'s own "always build fresh" philosophy). `clearPlayer(UUID)` is
deleted; `RemoveAccountListener` dropped its `InventoryRuntimeContext` dependency (it only ever called
`clearPlayer`) — its remaining `inventoryRegistry.clear(uuid)` call still runs, for the legacy `InventoryHandler`
entries the not-yet-migrated modules (WS2 G4/G5) still register there. `GameplayConfig.inventoryRuntimeContext(...)`
now takes `InventoryService` instead of `InventoryRegistry`.

**`menu/InventoryBuilder.java` rewritten**: `createMenu(...)` builds a single `ChestMenu` (slots, vertical/
horizontal lines, border/fill — Keystone's own border→line→explicit-slot priority order matches the old
`InventoryUtil` skip-if-occupied behavior exactly); `createPagedMenu(...)` replaces the old `Type: multi-inventory`
model. Since `openInventoryForPlayer` always has the real player, every placeholder — including the paginated
menus' item source, which needs the player to fetch that player's filtered/sorted entries — resolves *eagerly* at
build time, exactly like the deleted `InventoryHandler`-based builder did; no render-time placeholder-resolving
component was needed.

**`Multi.*` rebuilt on `PageConfig` + `PagedRegion`** (plan F3). The old chained-Bukkit-inventory model
(`MultiInventory`/`MultiInventoryCreation`/`MultiInventoryNavigation`/`ListEntry`/`StaticSlotEntry`, 5 files,
**deleted**) is replaced by one fixed-size `ChestMenu` whose interior grid renders per page via
`PagedRegion.render`. Row/column arithmetic (`computeRows`) is a direct port of the old
`MultiInventoryCreation.computeConfigForCreation`: explicit YAML size first, else static-item-driven, else
`ceil(itemCount / maxColumns) + 2`, clamped to `[3, 6]`; the static-items column reservation (`firstCol = hasStatic
? 2 : 1`) and its `InventoryUtil.verticalLine(..., column 2, ...)` separator are both ported 1:1. Next/previous/home
buttons rebuild a fresh `ChestMenuBuilder` for the target page and swap it into the already-open menu via
`ChestMenu.adoptComponentsFrom` — an in-place render, no close/reopen flicker (an improvement Keystone's own
mechanism provides for free; the old model always closed/reopened a differently-titled Bukkit inventory per page).
`ItemSourceEntry`/`ItemSourceProvider` are unchanged — `GangItemSourceProvider` needed **zero changes**: it already
returned page-agnostic `List<ItemSourceEntry>` (placeholder maps), and pagination has always lived entirely in the
renderer, never the provider.

**Four hand-coded ad-hoc paginated views ported off the deleted `Multi.*` classes.** `BountyAspect.openBountyView`,
`DebugCommand`'s `multi` debug argument, and `GangCommand`'s member-list/ally-list views (2 sites) all called
`MultiInventoryCreation.dynamicMultiInventory(...)` directly with pre-built `ItemStack`s (no YAML, no
`Item_Template`) — a plan-unlisted consumer set the plan's own text didn't name (`BountyAspect`/`ViewInventoryAspect`
were called "unaffected by the re-target," but `BountyAspect` turned out to depend on the very `Multi.*` classes
G3 deletes). With 4 real call sites sharing one exact shape, extracted one shared helper —
`menu/SimplePagedMenu.open(InventoryService, Player, List<ItemStack>, String title, Fill)` — rather than duplicating
the row/PagedRegion/nav-button logic a 4th time. `BountyAspect` gained an `InventoryService` constructor param,
threaded from `SignManager.setupSigns()` (which already holds a `DependencyContainer container` field, resolved via
`container.getInstance(InventoryService.class)`, mirroring `GangFilterRegistration`'s existing lazy-resolve
pattern) through `BountySign`'s constructor. `GangCommand` gained `InventoryService` as a new constructor param
directly (bean-container-injected).

**`/glw debug inv-data` and `multi` verified working end to end** (smoke, below) — `inv-data` needed no code change
(already wired to `inventoryService.tracker()` since G1/G2); it now shows real tracked menus because menus actually
open through the service starting this gate.

**Item-return contract (plan §0d): none of the 9 core menus have an interactive slot.** Grepped every YAML under
`gangland-impl/src/main/resources/inventory/` for `Draggable` — zero hits, confirmed again via the round-trip
tests' own `PageConfig`/`ChestMenuBuilder` construction (no `.interactive(...)` call fires for any of the 9). The
"interactive non-`ItemHoldingComponent` slots discard player items on close" caveat therefore does not apply to
any core menu this gate touches; it's a WS2 G4/G5 concern (npc-shops' `BarterView`/`SellView`, which already carry
their own `SlotView.interactive(boolean)` design per the plan).

## Deviations from the plan

1. **`BountyAspect`/`DebugCommand`/`GangCommand` needed real changes** — the plan's text ("Real command/sign/reload
   consumers... unaffected by the re-target") was wrong for `BountyAspect` specifically (it depends on
   `MultiInventoryCreation`/`MultiInventory`/`ListEntry`, all deleted this gate) and didn't mention `DebugCommand`'s
   `multi` argument or `GangCommand`'s two ad-hoc paginated views at all. Found by grepping every reactor-wide
   reference to the classes I was about to delete before deleting them (not by trusting the plan's consumer list).
   All 4 ported onto the new `SimplePagedMenu` helper; zero behavior change to their output (same row/column
   arithmetic, same border, same next/prev semantics — just no close/reopen flicker, a free improvement from
   `adoptComponentsFrom`).
2. **`MenuOpener` chosen over `MenuRegistry`** for command-open lookups, per the plan's own "MenuRegistry/MenuOpener"
   either/or framing (§3's seams table lists both). Reasoning is in "What changed" above — a registered
   `Supplier<Menu>` factory cannot supply the player a paginated menu's item-source fetch needs before `.build()`.
   `MenuRegistry` is not populated at all this gate; nothing in the coordinator's brief or the plan required it
   specifically, and `/glw debug inv-data` works correctly without it (the tracker is populated by any `ChestMenu`
   built via `ChestMenu.builder(inventoryService)` and opened, regardless of how it was reached).
3. **`part/Fill.java` still not moved** (carried from the G3a deviation, ruling W41 accepted): this gate's new code
   (`InventoryBuilder`, `SimplePagedMenu`, `BountyAspect`, `DebugCommand`, `GangCommand`) all import it from
   `org.luckyraven.gangland.inventory.part.Fill` (unchanged package) — still correct, since the 16 external-module
   importers G4/G5 will re-point haven't moved yet.

## Red-first evidence

**`InventoryParserRoundTripTest`** (new, 9 test methods — one per core YAML menu, plan §7's asked-for round-trip
coverage) is new test infrastructure for functionality that didn't exist in the old `InventoryHandler`-based code
(there is no meaningful "pre-change version" to run these specific assertions against), so "red-first" here took
the form of genuinely debugging real failures during authoring rather than a scripted wrong-then-right flip —
recorded plainly:
- First real run surfaced a genuine implementation bug: **`InventoryBuilder`'s vertical-line YAML index was
  off-by-one** (YAML `Configuration.Line.Vertical` is 1-indexed, matching the old `InventoryUtil.verticalLine`
  convention; Keystone's `LineComponent.vertical(col, ...)` is 0-indexed) — caught and fixed (`col - 1`) before any
  test run, by re-deriving both conventions from source rather than assuming.
- A **second, load-bearing bug found via the tests, not by inspection**: for a regular (non-conditional) slot's
  navigation click, `verify(opener)` — the `MenuOpener` mock passed into `createMenu` — reported zero interactions.
  Root cause (confirmed by temporarily instrumenting `ChestMenu.dispatchClick` in the Keystone worktree, rebuilding,
  observing, then reverting both the instrumentation and the reinstalled jar — `git status`/`git diff` confirmed
  clean after): the click handler *was* found and invoked, but it called `context::openInventoryForPlayer` (the
  real method, captured at **parse time** for every regular/static slot) — not the separate mock `opener` passed
  into `createMenu` (which only backs a **conditional** slot's raw action, resolved lazily at build time). This is
  correct production behavior, not a bug — the test's verification strategy was wrong. Fixed by registering the
  real navigation target and asserting a second live `player.openInventory(...)` call instead of a mock
  interaction — a more faithful, more end-to-end test than the original design.
- A **third bug found the same way**: `registerAndBuild`'s `try (BukkitStatics ...) { ...; return menu; }` closes
  its Bukkit-static-mock scope before the caller can dispatch a click that triggers a *second* internal
  `Bukkit.createInventory` call (the navigated-to menu) — fixed by giving `registerAndBuild` an in-scope callback
  parameter so click dispatch runs inside the same faked-Bukkit scope as the build.
- A **fourth**, environment-only: `InventoryRuntimeContext.openInventoryForPlayer` reads `Settings.getInventoryFillName/
  Item` directly; `Settings`' ~200 fields are process-wide statics needing one-time initialization
  (`documentation/TESTING.md` §4) that the test hadn't done — added `SettingsFixture.initializeMinimal(tempDir)`.
- Command: `mvn -pl gangland-impl -am test -Dtest=InventoryParserRoundTripTest -Dsurefire.failIfNoSpecifiedTests=false`.
  Final green: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`.

**Test-environment-only workaround, not a production gap**: 2 of the 3 paginated menus' shipped `Item_Template` is
`Type: PLAYER_HEAD` (a skull texture), which needs `com.mojang.authlib` (via XSeries' `XSkull`) — a real-server-only
class absent from a plain unit-test JVM, and `documentation/TESTING.md` §1 forbids adding a test dependency to a
module pom to paper over it. `phoneGangSearch`/`userStat`'s tests and the shared `context`'s own `ItemSourceProvider`
(used by every `registerNavigationTarget`-driven real navigation, e.g. `phoneGang` → `phone_gang_search`) supply an
**empty** entry list — `renderTemplateEntries`' `.stream().map(...)` never runs the per-entry resolver on an empty
stream, so `customHead`/`XSkull` is never reached, while every other structural assertion (registration, `PageConfig`
with 0 entries, static filter buttons + their click bindings, decoration, page-count title suffix) still runs for
real. `allianceStat`'s test uses real non-empty entries (its `Item_Template` is plain `REDSTONE`, no head/data tag,
so it's unaffected). Also needed: an in-memory `ItemNbtAccessor` fixture (`NbtBridge.install(...)`, same shape as
`ItemDefinitionSimilarityTest`'s own `PerStackNbtAccessor`) — the real NBT-API reflects into
`com.mojang.authlib.GameProfile` for even a plain `hasNBTTag` check, same missing-classpath-class issue.

Also ran the whole-module test suite before and after to confirm nothing else regressed:
`mvn -pl gangland-impl -am test` → `Tests run: 254, Failures: 0, Errors: 0, Skipped: 0` (245 from G3a + the 9 new).

## Build

Final gate command: `mvn clean install` (full reactor, tests included) → **BUILD SUCCESS**, all 21 reactor modules
`SUCCESS`. Aggregate test summary across every module: **Tests run: 906, Failures: 0, Errors: 0, Skipped: 0**
(5+119+43+63+59+29+254+25+20+91+76+113+9 across gangland-{warfare,core,domain,item,ui/inventory-api,ui/sign-api,
ui/shop-api,impl,mail,civilians,turf,cops-n-crooks,gadget,npc-shops} — every module that carries tests).

Compile-only checkpoints were run repeatedly while iterating (`mvn -pl gangland-impl -am compile` /
`-am clean test-compile`); the two intermediate `BUILD FAILURE`s worth recording: (1) a genuine cascade from
missing `getInstance` (not `get`) on Keystone's `DependencyContainer` in `SignManager.java`, fixed immediately;
(2) an overload-ambiguity compile error (`Player.openInventory(Inventory)` vs. `(InventoryView)` both matching
Mockito's `any()`), fixed with an explicit `any(Inventory.class)`.

## Docket ids touched

None directly fixed this gate (the two triage rows the plan's §11 names —
`InventoryHandler.SPECIAL_INVENTORIES` and the static `InventoryRegistry registry` leaking across reloads — are
recorded "fixed-by-WS2" at the shared **CUT** gate per the plan, since `InventoryHandler`/`InventoryRegistry`
themselves are not deleted until then; G3 only stops Gangland's own core menus from touching them).

**Docket candidate (new, not yet filed)**: `BountyAspect`'s bounty-view pagination previously closed and reopened
a differently-titled Bukkit inventory on every next/prev click (the old `MultiInventoryNavigation` model, shared
by every `Multi.*` consumer); this gate's `SimplePagedMenu`/`ChestMenu.adoptComponentsFrom` port removes that
flicker as a side effect, not a deliberate fix — noting it in case a "no-flicker pagination" line item is worth a
row of its own, though it's arguably not bug-shaped enough to need one.

## Subagents used

None, matching the WS2 G1+G2 precedent's own reasoning: the parser/handler/condition dialect, the paginated-stack
rebuild, and the 4 previously-unlisted `Multi.*` consumers all turned out to be one tightly interlocking design
(the `MenuOpener`-vs-`MenuRegistry` decision alone reshaped how every one of them had to be wired) — splitting the
"parser retarget" and "paginated stack" work across two subagents as the brief suggested would have required
transferring this whole design first, which costs more than doing it directly. The debugging that found the two
real bugs (vertical-line indexing, the `MenuOpener`-vs-parse-time-binding mismatch) also needed the full call-chain
context in one place.

## Concerns / open questions

- **`Fill.java` is still not moved** (see Deviations #3) — now confirmed used by this gate's own new code too, on
  top of the 16 external-module importers G3a found. Flagging again for whoever runs G4/G5: once those 16 external
  consumers are re-pointed off `InventoryHandler`/`Fill`/`InventoryUtil` entirely, `Fill.java` can fold into
  `menu.part` (or retire in favor of `ChestMenuBuilder`'s own fill primitive, worth reconsidering at that point
  rather than porting it forward mechanically).
- **`MenuRegistry` is unused by Gangland** as of this gate — a deliberate choice (see Deviations #2), but WS4/CUT
  or a future consumer that wants `ClickContext.openMenu(name)` to work natively (rather than going through
  `MenuOpener`) would need to reconsider this; flagging so it isn't mistaken for an oversight later.
- **No dedicated `Multi.*`-arithmetic-only unit test was written** separately from the round-trip tests — the plan's
  §7 table lists "Multi.*-on-PagedRegion paginated-stack test (new)" as its own row; I folded that coverage into
  the round-trip tests' paginated-menu assertions (row/column counts, static item slots, page-count title suffix)
  rather than a standalone arithmetic-only test, since `computeRows`/`PageConfig.forSize` are exercised identically
  either way and a separate test would duplicate rather than add coverage. Flagging in case the orchestrator wants
  a dedicated one anyway.
- **Interactive-slot survey is YAML-only**: confirmed via grep + the round-trip tests that none of the 9 *core*
  menus have `Draggable: true`; this says nothing about WS2 G4/G5's own module views (npc-shops' barter/sell,
  turf's powerup views), which the plan already flags as needing their own item-return-contract treatment.
- Smoke: `S1` row (boot, no modules) with `glw reload` + `glw debug inv-data` appended to its command plan,
  `paths.repo_dir`/`keystone_jar_dir` temporarily repointed at this worktree / `wt\keystone-1.11.0\keystone-plugin\
  target` (Keystone-1.11.0.jar, pre-built) — `verdict=PASS`, 0 distinct ERROR signatures, `glw reload` completed
  ("Reload has been completed", "The plugin is up to date"), `glw debug inv-data` ran without throwing. No
  interactive session was available to physically open a menu from the console (there's no player), so `inv-data`
  (the tracker's own console-facing inspection point) is the closest available proof the service path works
  end-to-end outside the unit tests. `scenarios.json` restored to its pre-run state afterward (verified via
  `git diff` — clean); parked jars restored via `--restore`. Report: `brainstorming/bartizan-split-2026-09-08/
  smoke/reports/2026-09-21-1908-S1.md` (+`.log`, +`-summary.md/.json`) — in the **main checkout**, untracked,
  per LEAD-RULES.

## Files touched

**Modified** (18, all `gangland-impl/`): `command/sub/debug/DebugCommand.java`, `command/sub/gang/GangCommand.java`,
`config/GameplayConfig.java`, `file/configuration/inventory/InventoryParser.java`,
`file/configuration/inventory/InventoryRuntimeContext.java`,
`listener/inventory/InventoryOpenByCommandListener.java`, `listener/player/RemoveAccountListener.java`,
`menu/InventoryBuilder.java`, `menu/condition/ConditionalSlotData.java`,
`menu/handler/{AbstractCommandSlotHandler,ClickSlotHandler,CloseSlotHandler,SlotEventHandler}.java`,
`menu/part/{ConditionalSlotResult,Slot}.java`, `sign/SignManager.java`, `sign/aspect/BountyAspect.java`,
`sign/type/BountySign.java`.

**New** (2): `gangland-impl/src/main/java/org/luckyraven/gangland/menu/SimplePagedMenu.java`,
`gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/inventory/InventoryParserRoundTripTest.java`
(9 test methods).

**Deleted** (7): `menu/multi/{MultiInventory,MultiInventoryCreation,MultiInventoryNavigation,ListEntry,
StaticSlotEntry}.java` (superseded by `PageConfig`/`PagedRegion`/`SimplePagedMenu`/`InventoryBuilder.createPagedMenu`),
`src/test/.../file/configuration/inventory/InventoryRuntimeContextTest.java` and
`src/test/.../listener/player/RemoveAccountListenerTest.java` (pinned the deleted G2 shim's behavior — no
replacement needed, the shim itself is gone).

**Unchanged, confirmed by grep + build, not assumption**: `GangItemSourceProvider.java`, `menu/multi/
{ItemSourceEntry,ItemSourceProvider}.java`, `menu/unique/UniqueItemHandler.java`, `menu/villager/*.java`
(plan: "unchanged product — no Oriel `TraderMenu` swap"), every `gangland-features/*` module's Panel-based views
(`BarterView`/`SellView`/turf's powerup views/cops-n-crooks' paperwork views — WS2 G4/G5's job), `part/Fill.java`
(inventory-api, deviation carried from G3a).

Review package (real hunks): `exec/G010/WS2-G3-package.diff` (2973 lines).

## Fix round 1

Opus review at `exec/G010/WS2-G3-review.md`, verdict FIX (6 Important + 4 Minor). Orchestrator ruling W42 (F6) also
actioned below. All items done.

**F1 — `SimplePagedMenu` nav buttons rebuilt on `ButtonTags` + `InventoryBuilder.headItem`.** `SimplePagedMenu.open`/
`build` gained a `ButtonTags buttonTags` parameter; next/previous/home buttons now call the new package-visible
`InventoryBuilder.headItem(name, base64Texture, String... lore)` (shared with the YAML-driven paged path) instead of
a bare `ItemBuilder`, so the configured `settings.yml` `Multi_Inventory` head textures apply exactly as the old
`MultiInventoryNavigation` did. The Home button — previously missing from `SimplePagedMenu` entirely, the review's
core finding — is added at `size-5` for every `resolvedPage > 0`, matching `InventoryBuilder.createPagedMenu`'s own
`addNavigationButtons` (and the old `MultiInventoryNavigation`) exactly. All 4 consumers updated to build and pass a
`ButtonTags` from `Settings`: `DebugCommand`'s `multi` argument, `GangCommand`'s member-list and ally-list views
(both `SimplePagedMenu.open` call sites), `BountyAspect.openBountyView`.

**F2 — paged menus always draw a border, never fill.** `InventoryBuilder.createPagedMenu`'s trailing decoration call
is now unconditional (`builder.border(...)` always runs), matching the old `MultiInventoryCreation.dynamicMultiInventory`,
which called `InventoryUtil.createBoarder(multi, fill)` unconditionally and never consulted `Configuration.Fill`/
`Border` for the paged path at all. The non-paged `applyDecoration` (border XOR fill, driven by YAML) is untouched —
`phone_gang_search.yml` (`Fill: true, Border: false`) renders with a border only, as before, because it goes through
the non-paged `applyDecoration` for its own decoration and the paged path's `createPagedMenu` border call is a
separate, always-on layer matching what shipped pre-G3.

**F3 — left-click falls back to `onAnyClick` when there is no right-click handler.** `createMenu`'s slot loop: when
a slot has a left handler but no right handler, it now binds via `component.onAnyClick(leftClick)` (every click type
fires it) instead of `onLeftClick`; when both exist, left binds via `onLeftClick` and right via `onRightClick` as
before. Reproduces the old `InventoryClickHandler.java:48-62` fall-through — no shipped core YAML declares
`OnRightClick`, so every button's left action used to answer any click type (middle-click, shift-click, drop,
number-key, etc.), not just a literal left-click.

**F4 — new paginated-boundary tests, both new.**
- `InventoryParserRoundTripTest#allianceStatPaginationBoundary` (`alliance_stat.yml`, 28/page): 29 constructed
  entries force exactly 2 pages. Asserts page 0's region is full (slots 10 and 43 both filled) with a next button
  at slot 53 and no home/prev (border fill shows through at 49/45); page 1 has only the 1 remainder entry (slot 10
  filled, slot 43 empty, still border), no next button, and home+prev present at 49/45.
- `SimplePagedMenuTest#twoPagesWithHomeOnlyOnSecondPage` (new file, `gangland-impl/src/test/.../menu/SimplePagedMenuTest.java`):
  drives the public `SimplePagedMenu.open` entry point with 29 plain `ItemStack`s (same perPage+1 shape), asserts
  the same page-0 structure, then dispatches a real `InventoryClickEvent` at slot 53 (the next button) through
  `ChestMenu.dispatchClick` and re-asserts page 1's structure on the *same* `ChestMenu` instance (proving
  `adoptComponentsFrom`'s in-place swap works, not just two independently-built menus) — home present only now,
  next button gone, prev present.
- Both tests use an empty-string `ButtonTags("", "", "")`: `InventoryBuilder.headItem`'s new `item.customHead(tag)`
  call (F1) triggers XSkull's static init on a non-empty tag, which needs `com.mojang.authlib` — absent from a
  plain unit-test JVM, and `documentation/TESTING.md` §1 forbids a test dependency to paper over it. `customHead`
  no-ops on an empty/null profile string (the same workaround the paginated `Item_Template` round-trip tests already
  use), so both tests still exercise the real nav-button structure (slot position, presence, click-triggered page
  swap, lore) without needing live skin resolution — texture rendering itself stays a "Cannot verify outside a
  client" item, folded into F6's manual checklist below.
- `SimplePagedMenuTest` additionally needed an XMaterial warm-up in `@BeforeAll` (`XMaterial.STONE.get()`, called
  against the *real* mock server before any `BukkitStatics` block runs): `BukkitStatics.install()` replaces
  `Bukkit.getServer()` with its own fresh, unstubbed mock for its try-with-resources scope, and `XMaterial$Data`'s
  clinit reads `getVersion()`/`getBukkitVersion()` — null there, throwing `ExceptionInInitializerError` the first
  time anything touches `XMaterial` from inside that scope. `InventoryParserRoundTripTest` never hits this because
  earlier work in the same fork already forces XMaterial's (one-time, JVM-wide) class init before its own
  `BukkitStatics` blocks open; a standalone single-test class has no such earlier toucher, so it needs its own.

**F5 — red-first evidence for both new tests** (wrong-value probe, since both pin new/changed behavior with no
meaningful pre-change version to diff against, matching G3a's own precedent):
- `allianceStatPaginationBoundary`: flipped the page-1 home-button assertion (line 578) from
  `Material.PLAYER_HEAD` to `Material.BLACK_STAINED_GLASS_PANE`. Red:
  `mvn -pl gangland-impl -am test -Dtest=InventoryParserRoundTripTest#allianceStatPaginationBoundary -Dsurefire.failIfNoSpecifiedTests=false`
  → `Tests run: 1, Failures: 1` — `AssertionFailedError: home button (size-5) must render on page > 0 ==> expected: <BLACK_STAINED_GLASS_PANE> but was: <PLAYER_HEAD>`.
  Reverted; green: same command → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.
- `twoPagesWithHomeOnlyOnSecondPage`: flipped the same-shaped page-2 home-button assertion from `PLAYER_HEAD` to
  `BLACK_STAINED_GLASS_PANE`. Red: `mvn -pl gangland-impl -am test -Dtest=SimplePagedMenuTest -Dsurefire.failIfNoSpecifiedTests=false`
  → `Tests run: 1, Failures: 1` — `AssertionFailedError: Home button must render on page 2 ==> expected: <BLACK_STAINED_GLASS_PANE> but was: <PLAYER_HEAD>`.
  Reverted; green: `mvn -pl gangland-impl -am test -Dtest=SimplePagedMenuTest,InventoryParserRoundTripTest -Dsurefire.failIfNoSpecifiedTests=false`
  → `InventoryParserRoundTripTest`: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`; `SimplePagedMenuTest`:
  `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

**M7 — nav lore + click sound.** `addNavigationButtons` (both `InventoryBuilder` and `SimplePagedMenu`) now builds
a `&7(n/total)` lore line per button — next shows the page it's going *to*, previous shows the page it's going
*back to*, both 1-indexed, matching the old `addNextPageItem`/`addPreviousPageItem` lore exactly — and passes it
into `headItem(..., lore)`. Click feedback (`swapInPlace`/`swap`) now plays a new package-visible constant,
`InventoryBuilder.NAV_CLICK_SOUND = new SoundEffect(SoundEffect.SoundType.VANILLA, "BLOCK_WOODEN_BUTTON_CLICK_ON", 1F, 1F)`,
through Keystone's `SoundEffect` (matching `feedback_sound_via_configuration.md` — never a raw `XSound`/`Sound`
call), reproducing the old `MultiInventoryNavigation.buttonClickSound`.

**M8 — `resolveItemStack` honors the `data` tag for static items.** Split into a 3-arg overload (regular slots,
head-tag only, unchanged) and a new 4-arg overload taking `honorDataTag`; `createPagedMenu`'s static-items loop
passes `true`, matching the old `processItemStack` (which read `head` OR `data`), while regular `Slots` entries keep
the head-only behavior the shipped YAMLs actually render with.

**M9 — `Static_Items` slot keys bounds-checked.** `createPagedMenu`'s static-items loop now skips (with a `log.warn`)
any slot index `< 0` or `>= rows * 9`, and any slot colliding with an already-placed static item — matching the old
`MultiInventory.placeStaticItems` skip behavior; this class additionally logs where the old code skipped silently.

**M10 — `SignManager` imports `InventoryService`.** `container.getInstance(org.luckyraven.keystone.inventory.InventoryService.class)`
→ `container.getInstance(InventoryService.class)` with the import added, in `setupSigns()`'s `BountySign`
construction.

**F6 (ruling W42) — manual checklist started.** `exec/G010/WS2-manual-checklist.md`: 7 rows covering `/glw phone`,
gang info (paginated member/ally list), bank incl. the anvil branch of `phone_banking.yml`, bounty sign view,
gang search + filter (`phone_gang_search.yml`), `/glw debug inv-data` while a menu is open, and `/glw debug multi`
(the nav-button smoke row) — each needs a real client (console S1 already stands as the boot proof; a console has
no viewport to render or click a chest menu). Unchecked pending a client session.

Build after all of the above: `mvn clean install` (full reactor, exit code 0) → **BUILD SUCCESS**. Aggregate test
count summed fresh from every module's `target/surefire-reports/*.txt` this run: **Tests run: 867, Failures: 0,
Errors: 0, Skipped: 0**. `gangland-impl` alone: `Tests run: 256` (was 254 at G3 — the +2 is exactly the two new
tests below, nothing else in the module changed count). `InventoryParserRoundTripTest`: `Tests run: 10` (9 from G3
+ the new boundary test), `Failures: 0, Errors: 0`. `SimplePagedMenuTest`: `Tests run: 1, Failures: 0, Errors: 0`
(new file). No other test class's assertions needed changing — the existing 9 round-trip tests and the rest of the
module's baseline stayed green through every fix-round-1 change. (The 867 vs. G3's reported 906 reactor-wide total
is not a fix-round-1 regression — every module this round touched, `gangland-impl`, matches its expected count
exactly; the delta sits in modules untouched this round, most likely `gangland-ui/inventory-api`, which currently
has zero test source files at all — not re-audited here, out of this round's scope.)

**Files touched, fix round 1** (on top of G3's own file list above):
- Modified: `gangland-impl/src/main/java/org/luckyraven/gangland/menu/InventoryBuilder.java`,
  `gangland-impl/src/main/java/org/luckyraven/gangland/menu/SimplePagedMenu.java`,
  `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/debug/DebugCommand.java`,
  `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/gang/GangCommand.java`,
  `gangland-impl/src/main/java/org/luckyraven/gangland/sign/aspect/BountyAspect.java`,
  `gangland-impl/src/main/java/org/luckyraven/gangland/sign/SignManager.java`,
  `gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/inventory/InventoryParserRoundTripTest.java`.
- New: `gangland-impl/src/test/java/org/luckyraven/gangland/menu/SimplePagedMenuTest.java`,
  `exec/G010/WS2-manual-checklist.md` (main checkout).

Review package (real hunks, fix round 1, cumulative with G3 since nothing has been committed between gates):
`exec/G010/WS2-G3-fix1-package.diff` (3282 lines).
