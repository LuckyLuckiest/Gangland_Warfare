# WS2 G4 report — 2026-09-21

Status: DONE

Plan refs: `plans/WS2-inventory-keystone.md` §4 row G4, §0d (item-return contract), §7 (tests); Keystone consumer
guide `docs/keystone-inventory.md` (`MenuFlow`/`Panel`/`FlowState`, item-return contract table, `handleDepositClick`
seam, extension points) and `docs/phase-e6-inventory.md`; exec/G010/BRIEF.md batch 4.
Worktree `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, started at HEAD a7a4a050 (WS2 G3 + fix round 1,
committed). Verified clean via `git status --short` before starting.

## What changed

**16 generic `Panel`→`Panel` swaps** across the trader/banker view+flow files, mechanically retargeting the old
`org.luckyraven.gangland.inventory.flow.{Panel,MultiPanelInventory,FlowSession}` trio onto Keystone's
`org.luckyraven.keystone.inventory.flow.{Panel,MenuFlow,FlowState}`:
- `Panel<S>.size(session)` → `rows(state)` (pixel-count → row-count; every panel's old `SIZE`/pixel constant divided
  cleanly by 9, no remainder arithmetic needed).
- `render(MultiPanelInventory<S>, InventoryHandler, Player, S)` → `render(MenuFlow<S>, ChestMenuBuilder, S)` — the
  `Player viewer` parameter dropped everywhere (`MenuFlow.viewer()` replaces it); every `handler.setItem(slot,
  ItemBuilder, draggable, TriConsumer<Player, InventoryHandler, ItemBuilder>)` call becomes
  `builder.slot(slot, ItemComponent.of(itemBuilder).onAnyClick(ctx -> ...))` — the `InventoryHandler`/`ItemBuilder`
  callback parameters were never dereferenced anywhere in this 16-file set either (same finding G3's own dialect
  port made), confirmed by grep before dropping them.
- `host.switchTo/back/end/rerender/suspend/resume` → the identical method names on `MenuFlow`, unchanged shape.
- `InventoryUtil.fillInventory(handler, fill)` → `builder.fill(FillComponent.of(material).name(name))`;
  `InventoryUtil.createBoarder` → `builder.border(BorderComponent...)` — Keystone's fill/border already skip
  interactive and already-occupied slots by construction (`ChestMenuBuilder.build()`'s border/line/explicit-slot
  layering + `ChestMenu.applyFillIfEmpty`'s own interactive-slot skip), so the old "preserve dropzone → blank-fill
  around it → restore dropzone" dance (both `BarterView`/`SellView`'s `renderChrome`) is gone entirely — a
  simplification, not a port.
- `NegotiationView`'s `InventoryUtil.aroundSlot` (recolor the 8 slots around a preview, explicit clear-first pass
  required because the old primitive skipped occupied slots) is gone too: since every `Panel.render()` call starts
  from a fresh, empty `ChestMenuBuilder`, the ring slots can just be set directly with no stale-slot risk.
- Files: `trader/view/{ModeSelectView,QuantitySelectorView,NegotiationView,ShopView,TraderFlow,TraderFlowSession}.java`,
  `banker/view/{BankerAmountView,BankerClaimView,BankerCreateAccountView,BankerMenuView,BankerUpgradeView,BankerFlow,
  BankerFlowSession,BankerRenameAccountView}.java`, `listener/trader/{BarterSessionListener,
  TraderSellSessionListener}.java` (these two keep their exact shape — see "BarterView/SellView" below).

**`TraderFlow`/`BankerFlow` gained an `InventoryService` constructor param** (new, not in the 16-file baseline):
`MenuFlow.builder(service, plugin, viewer, state)` needs a real `InventoryService`, unlike the old
`MultiPanelInventory`'s `(plugin, viewer, session)`. Threaded from `TraderModuleConfig.traderFlow(...)`/
`BankerModuleConfig.bankerFlow(...)` bean methods (both already resolve everything else from the same
CONFIG-phase container `InventoryService` lives in — `GameplayConfig`, per G3 — so this is a same-phase
bean-graph edge, not a new wiring layer).

**`BarterView.java`/`SellView.java` — re-point, not redesign (§0d), plus the item-return contract.** The
deposit-zone read/restore logic keeps its exact shape: `handleClick`/`handleDrag` (called from
`BarterSessionListener`/`TraderSellSessionListener`, unchanged Bukkit-listener shape) still do the multi-slot
stacking placement for a player's shift-click (`tryPlaceInDropzone`) and the "pass through, don't cancel, schedule a
recompute" logic for a direct click inside the dropzone — `InventoryHandler.getInventory()` becomes
`ChestMenu.bukkitInventory()`, read via a small `BarterState.inventory()`/`SellState.inventory()` helper
(`flow.currentMenu().bukkitInventory()`) rather than a cached field, since `MenuFlow.currentMenu()` is always live
and there is no risk of it going stale between renders.

The one genuine design decision (§0d's explicit ask): the 20 dropzone slots are Keystone's `SlotView.interactive`
(the plain draggable flag) plus a new **`DropzoneSlotComponent`** (`trader/view/DropzoneSlotComponent.java`, shared
by both views) implementing `ItemHoldingComponent`. Its `renderInto` calls only `view.interactive(true)` — never
`setItem` — so a render pass never touches whatever the player physically has in the slot (Bukkit's own uncancelled
click/drag handling already writes straight into the live `Inventory`, which *is* the state — nothing to snapshot
or restore). `heldItems(player)`/`clearHeld(player)` read/clear that same live inventory slot directly, captured via
`RenderContext.menu()` at render time. This plugs into Keystone's own item-return contract
(`keystone-inventory.md` §"The item-return contract") for every close path it promises — Escape, disconnect, reload/
shutdown, and a differently-sized/titled `MenuFlow.switchTo`/`back()` — automatically, replacing the old
`MultiPanelInventory#onEnd` per-render registration, which has **no equivalent** on the new `MenuFlow`: its `onEnd`
is a `Consumer<S>` set once at `MenuFlowBuilder.build()` time, immutable afterward, so the old "each view registers
its own onEnd when entered, overwriting whichever view registered last" pattern doesn't translate. `TraderFlow`
instead wires **one** flow-wide `onEnd` (set at construction) that calls `sellPanel.onFlowEnd(viewer)` and
`barterPanel.onFlowEnd(viewer)` unconditionally — each is a no-op (`active.remove(viewer)`, nothing else) unless
that view was actually entered this session; the physical item return already happened by the time `onEnd` fires
(Keystone's contract runs before/independently of the flow's own close listener). `onShutdown()` (the
`BeanLifecycle` hook, unrelated to `InventoryService`'s own reload/shutdown sweep — no guaranteed bean-shutdown
ordering between the two exists) is kept as a belt-and-suspenders explicit return pass, matching the old code
exactly; harmless if the contract already returned the items first (checks `stack == null` before acting).

`BarterView.acceptedSlots` (the CT-01 pinning helper, static, signature unchanged) needed no changes — confirmed by
`BarterViewTest.java` (pre-existing, 4 methods) passing unmodified.

## Deviations from the plan

1. **20 files touched, not 18** — `TraderModuleConfig.java`/`BankerModuleConfig.java` needed a 1-parameter change
   each (`traderFlow(...)`/`bankerFlow(...)` bean methods gain `InventoryService inventoryService`) because
   `MenuFlow.builder(...)` requires one and the old `MultiPanelInventory` constructor didn't. Not in the plan's
   18-file census (which predates `MenuFlow`'s exact constructor shape); a same-phase bean-graph edge, zero risk.
2. **`InventoryUtil`'s "preserve dropzone, blank-fill, restore" dance and `aroundSlot`'s "clear ring, then
   recolor" dance are both deleted, not ported** — Keystone's fill/border primitives already skip interactive and
   already-occupied slots by construction, and a fresh-every-render `ChestMenuBuilder` has no stale prior-render
   state to clear. This is a simplification the plan didn't call out specifically, but it's strictly narrower than
   "re-point, not redesign" would forbid — same visible behavior, less code, confirmed unit-tested via the round-trip
   assertions in `BarterViewItemSurvivalTest`/`SellViewItemSurvivalTest`.
3. **§7's "Interactive-slot barter/sell test" split into item-survival + a separate rerender-safety assertion**
   inside the same two test classes, rather than one test doing only the close/reopen round trip — the *rerender*
   half (does a plain in-panel rerender, not a close, ever wipe a dropped item?) turned out to be an equally
   real regression risk given the new declarative-builder model (a component that forgets to re-declare itself
   every render gets nulled — see `ChestMenu.renderAll`'s untouched-slot clear), so it's pinned alongside the
   close-path tests rather than left to the manual checklist.

## Red-first evidence

Both `BarterViewItemSurvivalTest`/`SellViewItemSurvivalTest` are new test infrastructure pinning new mechanism
(`DropzoneSlotComponent`, which didn't exist before this gate) — red-first took the form of a genuine-bug probe
against the production class, not a wrong-value flip, matching G3/G3a's own precedent for new-behavior tests:

- **Probe 1 (item-return path)**: temporarily made `DropzoneSlotComponent.heldItems` always return `List.of()`
  (simulating the pre-fix bug: a plain interactive slot with no `ItemHoldingComponent` at all, silently discarding
  whatever a player dropped on any close). Command:
  `mvn -pl gangland-features/gangland-npc-shops -am test -Dtest=BarterViewItemSurvivalTest,SellViewItemSurvivalTest -Dsurefire.failIfNoSpecifiedTests=false`.
  Red: `Tests run: 8, Failures: 6` — the three return-path tests (`userClose`/`disconnect`/`panelSwitch`) failed in
  **both** classes with `Wanted but not invoked: playerInventory.addItem(...)` / `zero interactions with this
  mock`; the two `roundTrip_survivesRerender` tests correctly stayed green (they pin a different property).
  Reverted; green: `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`.
- **Probe 2 (rerender-safety path)**: temporarily added an unconditional `view.setItem(null)` to
  `DropzoneSlotComponent.renderInto` (simulating a naive re-point that declared an ordinary item-clearing component
  instead of relying on `interactive(true)` alone with no `setItem` call to leave the slot untouched). Same command.
  Red: `Tests run: 8, Failures: 2` — exactly the two `roundTrip_survivesRerender` tests failed (`expected:
  <ItemStack{GOLD_INGOT x 3/5, null}> but was: <null>`); the other 6 correctly stayed green. Reverted; green:
  full module run confirms `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` (9 pre-existing + 8 new).

**Cannot-verify / environment gap found while writing these tests (not a production bug):** `ChestMenu.close(Player,
CloseReason)` calls `returnHeldItems(...)` (what these tests check) then unconditionally the 1-arg
`close(Player)`, whose `player.getOpenInventory().getTopInventory()` bookkeeping check throws
`IncompatibleClassChangeError` against a mocked `Player` in this module's unit-test environment. Root cause
confirmed by reading both root poms: Keystone pins `bukkit.version` = **1.16.5** (its documented MC-floor pin,
where `InventoryView` is a `class`), Gangland pins **1.21.11** (where it's an `interface`) — `keystone-inventory`'s
pre-compiled `ChestMenu.class` bytecode was built against the older, class-shaped API. Real CraftBukkit servers
never hit this (their actual `InventoryView` implementation satisfies both compile-time shapes at runtime); only
Mockito's compile-time-typed proxy does. The item-return this test cares about has already completed by the time
this throws (it's the *second* statement in `close(Player, CloseReason)`, after `returnHeldItems`), so the
`disconnect`/`panelSwitch` test methods call the real production entry point wrapped in a documented
`catch (IncompatibleClassChangeError)` rather than working around it by calling a lower-level method instead —
see `closeIgnoringKnownBukkitVersionGap` in both test classes. Flagging as a **finding for the docket**, not fixed
here: it's a cross-repo (Keystone vs. Gangland) `bukkit.version` floor decision, out of this gate's scope, and it
would affect *any* future Gangland unit test that calls `ChestMenu.close(Player, CloseReason)` against a mocked
`Player`, not just these two.

## Build

**Module gate**: `mvn clean install -pl gangland-features/gangland-npc-shops -am` → **BUILD SUCCESS**. Module test
count (fresh from its own `target/surefire-reports/*.txt`, not hand-tallied): **Tests run: 17, Failures: 0,
Errors: 0, Skipped: 0** (9 pre-existing + `BarterViewItemSurvivalTest` 4 + `SellViewItemSurvivalTest` 4).

**Whole reactor**: `mvn clean install` (full reactor, exit code 0) → **BUILD SUCCESS**. Aggregate test count, summed
fresh from every module's own `target/surefire-reports/*.txt` this run (per the coordinator's explicit instruction —
the G3 906-vs-867 hand-tally confusion doesn't recur here): **Tests run: 875, Failures: 0, Errors: 0, Skipped: 0**
(867 at the end of G3 fix round 1 + 8 new this gate). No other module's test count moved — confirmed by grepping
every reactor module for references to the 20 changed classes (§ "Cross-module check" below) before starting: zero
hits outside `gangland-ui/inventory-api`'s own generic `FlowSession`/`MultiPanelInventory` javadoc (text mentions of
`TraderFlowSession`/`BarterView` as *examples*, not real imports — `inventory-api` has no dependency on
`gangland-npc-shops`).

**Cross-module check** (before touching anything, and re-confirmed after): `grep -rln "TraderFlow\|BankerFlow\|
BarterView\|SellView\b" --include=*.java` across `gangland-features/{turf,cops-n-crooks,gadget,mail}`,
`gangland-impl`, `gangland-ui` → only the two `gangland-ui/inventory-api` javadoc hits above; no other module
references any of the 20 changed npc-shops classes, so this gate's constructor-signature changes
(`TraderFlow`/`BankerFlow` gaining `InventoryService`) cannot have broken anything outside the module the
`-pl ... -am` gate already rebuilt.

## Smoke

**Console-drivable (harness)**: new scenario `npcshops-boot` added to
`brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (`paths.repo_dir`/`keystone_jar_dir` temporarily
repointed at this worktree / `wt\keystone-1.11.0\keystone-plugin\target`, restored to their prior values —
`wt\gangland-0.9.2` / `wt\keystone-1.10.0` — immediately after the run, per W17/W35; the new scenario definition
itself is kept, matching the existing single-module `grapple-boot`/`jetpack-give` row pattern). Deploys `npcshops`
alone (not the 6-module `D5` row — turf/cops-n-crooks/gadget aren't G4's scope and don't need rebuilding for this
gate). Command: `python smoke.py --rows npcshops-boot --deploy --keystone --restore`.
**Verdict: PASS** — boot detected, clean stop, `loaded_modules=['npcshops']`, 0 ERROR-signature mismatches.
`glw trader`/`glw banker` from console both resolve cleanly to `"You need to be a player to use this!"` (no
exception/stack trace) — the module's bean graph (`TraderModuleConfig`/`BankerModuleConfig`, including the new
`InventoryService`-consuming `traderFlow`/`bankerFlow` beans) constructs without a fault. Report:
`brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-21-2026-npcshops-boot.md` (+`.log`,
+`-summary.md/.json`) — main checkout, untracked, per LEAD-RULES. Parked jars (`gangland-gadget-0.9.1.jar`,
`Bartizan-0.3.0.jar`, 2× Citizens jars) restored via `--restore`; core/Keystone jars left deployed at their new
version, matching the harness's own steady-state design (not parked/restored — only the *module set* varies
per row) and G3's own precedent.

**Client-only rows**: 12 new rows (8-19) appended to `exec/G010/WS2-manual-checklist.md` under a new "## G4 —
npc-shops" section — trader buy/sell/barter/negotiate (rows 8-11), sell (12), barter/sell item-survival under a
*real* client session/disconnect/reload (13, since the unit tests above can only simulate a mocked `Player`),
banker create/deposit/claim/upgrade/rename/online-banking (14-19). All unchecked pending a client session.

## Docket

No entries fixed this gate. **Docket finding, new, not yet filed**: the Keystone/Gangland `bukkit.version` gap
above (§ "Red-first evidence" cannot-verify note) — `ChestMenu.close(Player, CloseReason)` throws
`IncompatibleClassChangeError` against any mocked `Player` in a Gangland unit test, because `keystone-inventory` is
compiled against Keystone's 1.16.5 floor (`InventoryView` a class there) while Gangland's own test classpath
resolves 1.21.11 (`InventoryView` an interface). Not a production bug (real servers don't hit it) and not fixed
here (a cross-repo `bukkit.version` decision, out of WS2 G4's scope) — flagging for whoever next writes a Gangland
unit test that needs to call this specific method against a mocked `Player`; the workaround
(`closeIgnoringKnownBukkitVersionGap` in both new test classes) is documented inline and reusable.

## Subagents used

None. Sixteen of the eighteen plan-named files are one mechanical substitution pattern, established and verified
against the first three files (`ModeSelectView`/`QuantitySelectorView`/`NegotiationView`) before repeating it —
delegating the remaining 13 to a fresh subagent would have cost more in context-transfer (the exact API surface:
`Panel.rows/title/render` signatures, `ItemComponent`/`FillComponent`/`BorderComponent` composition order,
`ChestMenuBuilder.interactive`, the `TriConsumer`-params-never-dereferenced finding) than doing them directly with
that context already loaded. `BarterView`/`SellView` (the two highest-risk files, item-return contract) were done
personally throughout — reading the full item-return-contract mechanism (`ChestMenu.returnHeldItems`/
`ItemHoldingComponent`/`ChestSlotView.wasTouched`) first was itself the load-bearing research this gate needed, and
splitting it across a subagent boundary would have meant re-deriving that same reading elsewhere.

## Concerns / open questions

- **The Keystone/Gangland `bukkit.version` gap** (see Docket above) will resurface for any future Gangland test
  that needs `ChestMenu.close(Player, CloseReason)`'s full behavior against a mocked `Player` — worth a real fix
  (aligning Keystone's test-scope Bukkit API version, or a Keystone-side `InventoryView`-shape-agnostic close path)
  at some point, but explicitly out of this gate's scope.
- **G4's item-survival tests only exercise a mocked `Player`** — the manual checklist's row 13 is the only place
  this gate's real regression risk (does a genuine client disconnect / `/glw reload` / Escape actually return
  dropped items) gets a live check; flagging since §0d's item-loss risk is the highest-stakes part of this gate.
- **`Fill.java` still not moved** (carried from G3a/G3's own deviation note) — `BarterView`/`SellView`/all 16
  Panel files import it from `org.luckyraven.gangland.inventory.part.Fill` (unchanged package); still correct until
  the CUT gate re-points every remaining `inventory-api` importer at once.
- No new `gangland-api` surface was needed — every primitive this gate required (`MenuFlow`, `Panel`, `FlowState`,
  `ChestMenuBuilder`, `ItemComponent`/`FillComponent`/`BorderComponent`, `ItemHoldingComponent`, `SlotView`,
  `RenderContext`) already existed in `keystone-inventory` from G1/G3's own dependency addition.

## Files touched

**Modified** (20, all `gangland-features/gangland-npc-shops/`):
`banker/view/{BankerAmountView,BankerClaimView,BankerCreateAccountView,BankerFlow,BankerFlowSession,BankerMenuView,
BankerRenameAccountView,BankerUpgradeView}.java`, `config/{BankerModuleConfig,TraderModuleConfig}.java`,
`listener/trader/{BarterSessionListener,TraderSellSessionListener}.java`,
`trader/view/{BarterView,ModeSelectView,NegotiationView,QuantitySelectorView,SellView,ShopView,TraderFlow,
TraderFlowSession}.java`.

**New** (3): `gangland-features/gangland-npc-shops/src/main/java/org/luckyraven/gangland/npcshops/trader/view/
DropzoneSlotComponent.java`; `.../src/test/java/.../trader/view/{BarterViewItemSurvivalTest,
SellViewItemSurvivalTest}.java` (4 test methods each).

**Unchanged, confirmed by grep + the module gate build, not assumption**: `BarterView.acceptedSlots` and its
pinning test `BarterViewTest.java` (4 methods, unmodified, still passing); every other npc-shops file (trader/
banker command classes, database repositories, mood/economy services, YAML config loaders) — none reference the
`Panel`/`MultiPanelInventory`/`InventoryHandler` types this gate retargeted.

**Also touched (main checkout, untracked)**: `exec/G010/WS2-manual-checklist.md` (new "## G4" section, rows 8-19),
`brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (new `npcshops-boot` scenario retained; `paths.
repo_dir`/`keystone_jar_dir` restored to their pre-gate values).

Review package (real hunks, `git add -N . && git diff HEAD -M -U5`, intent-to-add reset afterward so the worktree
stays clean/uncommitted): `exec/G010/WS2-G4-package.diff` (3972 lines).
