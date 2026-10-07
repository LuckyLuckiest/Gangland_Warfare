# Oriel G1+G2 report — 2026-09-16

Status: DONE

Worktree: `E:\Programming\java\wt\oriel-0.8.0`, branch `0.8.0`, G0 committed at session start (verified clean
`git status` before starting). Nothing committed by this gate — the orchestrator commits at gate boundaries.

## What changed

### G1

1. **`MenuFlow.rerender()`** — `menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/flow/MenuFlow.java`.
   Re-runs the current panel's `Panel#render` into a throwaway `ChestMenuBuilder`/`ChestMenu`, then applies its
   computed component set to the LIVE, already-open `ChestMenu` via a new
   `menu-inventories/chest/.../ChestMenu.java` method, `adoptComponentsFrom(ChestMenu fresh)` — clears slots the
   panel removed, swaps in the fresh component map, repaints via the pre-existing `ChestMenu.rerender()`. No new
   Bukkit inventory, no close/open round trip, no flicker, no cursor/drag-state reset, and (unlike `switchTo` on
   the same panel id) nothing in a live `DepositSlotComponent` is abandoned.
2. **`OpenMenuTracker` on the `ServicesManager`** — `menu-plugin/.../OrielPlugin.java`: one new
   `registerService(services, OpenMenuTracker.class);` line in `registerServices()`, alongside the existing 12.
   The bean already existed (`MenuTrackingConfig`); this only publishes it so a consumer can build its own
   bookkeeping against the same tracker instead of duplicating it.
3. **`back()` on an empty stack + `hasBack()`** — `MenuFlow.java`: `back()` now calls `end()` when the back-stack
   is empty (GLW `MultiPanelInventory` parity) instead of silently no-op'ing on a dead button; added `hasBack()`
   so a panel can pick its own root-vs-back affordance ahead of a click.
4. **Doc fix** — `docs/MIGRATION-FROM-GLW.md` §8: documented `MenuFlow.rerender()` and `back()`/`hasBack()` (the
   G0 anvil-search correction from the prior gate is untouched and still accurate).

### G2

5. **Placeholder resolution — confirmed, no new Oriel primitive needed.** New test
   `menu-inventories/chest/src/test/java/org/luckyraven/oriel/chest/PlaceholderResolutionAndRowScopedSourceTest.java`
   proves both halves of the ask end to end: (a) a consumer's own in-process `PlaceholderProvider` (simulating
   Gangland's `PlaceholderService`, not PAPI) resolves a `%money_symbol%`-style menu-level token via the existing
   `ChestMenuBuilder.placeholders(...)` seam; (b) row-scoped values (`%member_*%`/`%ally_*%`-equivalent) need no
   Oriel mechanism at all — `PaginatedListComponent.from(...)`'s source function returns already-built
   `ItemStack`s, so the consumer bakes per-row values into name/lore itself (exactly how `GangItemSourceProvider`
   is planned to work) — and both compose in one menu without interfering. This matches the WS2 plan's own §3
   finding C4 conclusion ("no new Oriel ask for this row"); nothing in Oriel's production code changed for this
   item.
6. **Crash-safe `DepositSlotComponent` persistence — the in-process scope actually asked for (close, panel
   switch, server stop), not full on-disk kill-9 survival.** Fixed cross-docket findings menu-chest #1 and #2:
   - **#1** — `menu-inventories/chest/.../ChestMenu.java`: `close(Player, CloseReason)` used to skip
     `returnHeldItems` on `CloseReason.USER` (wrong: a deposit slot's stack lives in a plugin-owned inventory
     SLOT, not the player's cursor, so Bukkit's own close handling never returns it — only the cursor is
     auto-restored). Now drains on every reason. New `OrielMenuTarget.returnHeldItemsOnUserClose(Player)` hook
     (default no-op, `ChestMenu` overrides) wired into `ChestMenuCloseListener` — the REAL Bukkit
     `InventoryCloseEvent` path a player's Escape-press actually takes, which never called
     `close(Player, CloseReason)` at all before this fix (only the lifecycle-driven closes did).
   - **#2** — `MenuFlow.switchInternal`/`end()`: previously replaced/abandoned the outgoing `ChestMenu` without
     closing it, discarding anything in a live deposit slot. Both now call
     `currentMenu.close(viewer, CloseReason.NAVIGATION)` first — wiring up an enum value
     (`CloseReason.NAVIGATION`, "a MenuFlow switched panels or ended") that existed but was never used anywhere.
   - Flipped the two pinning tests that asserted today's wrong behaviour:
     `DepositSlotComponentTest.closeWithUserSkipsReturnSoBukkitCursorPickupHandlesIt` →
     `closeWithUserAlsoReturnsHeldItems`; `DepositSlotMultiSlotStressTest.closeOnUserSkipsReturnSoBukkitCursorPickupHandlesIt`
     → `closeOnUserAlsoReturnsHeldItems`.
   - New `DepositSurvivalIntegrationTest.java` (3 tests) proves all three named scenarios through their REAL
     production entry points (not the lower-level API): a genuine `InventoryCloseEvent` through
     `ChestMenuCloseListener`, a real `MenuFlow.switchTo(...)`, and `OpenMenuTracker.closeAll(PLUGIN_DISABLE)` —
     the exact call `DefaultOpenMenuTracker.onShutdown()` makes.
   - **Explicitly out of scope, flagged not silently dropped:** on-disk persistence surviving `kill -9` (Oriel's
     own A3 backlog item) — the gate's named acceptance criteria were "close, panel switch and server stop", all
     graceful-shutdown-hook scenarios, which the above fixes cover completely. Documented as such in
     `DepositSlotComponent`'s class javadoc and `MIGRATION-FROM-GLW.md`.
7. **Price-entry grid component** — new
   `menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/component/PriceGridComponent.java`: a
   slot-placement helper (like `ChestMenuBuilder.region(...)`, not itself a `Component<?>`) installing a mirrored
   ±-step row around a center preview slot. House rule verified exactly: for ascending steps `[1,10,100]` around
   a center slot, the row is `-1,-10,-100,[center],+100,+10,+1` — biggest step nearest the center, smallest at
   the edges. Click handlers re-read the current value live (never a build-time snapshot), clamp to `[min,max]`,
   write back, then call `ctx.rerender()`.
8. **Dropped per ruling W13** — anvil-driven paginated search: already handled, nothing to do (confirmed at G0,
   re-confirmed here — no regression).

## Deviations from the plan

1. **`CloseReason.NAVIGATION`, not `RELOAD`** — the cross-docket finding #2's suggested fix said "call
   `currentMenu.close(viewer, CloseReason.RELOAD)` (or a dedicated non-USER reason)". `CloseReason` already has
   an enum value defined exactly for this ("a `MenuFlow` switched panels or ended; the previous panel's inventory
   is being torn down") that was never used anywhere in the codebase — used that instead of `RELOAD`, which is
   semantically wrong here (RELOAD means `/menu reload`, an unrelated event).
2. **Scope of "crash-safe... persistence"** — interpreted narrowly per the gate's own listed acceptance criteria
   ("close, panel switch and server stop") rather than building Oriel's full on-disk A3 backlog item (which would
   need a new `DatabaseBackend`-persisted table + startup recovery pass — a materially bigger, unrequested piece
   of infrastructure). Flagged explicitly in docs and this report rather than silently doing less than "Oriel A3"
   might imply — if full kill-9 survival is actually wanted now, that's a new, separate ask.
3. **`DepositSurvivalIntegrationTest`'s first draft was itself red for the wrong reason** — my first version of
   the "genuine user close" scenario failed because the test never installed a real `OpenMenuTracker`
   (`MenuTracking` defaults to a no-op tracker in bare unit tests, so `ChestMenuCloseListener`'s
   `currentMenuOf(player) == target` guard was never true). Fixed by installing a real `DefaultOpenMenuTracker` in
   `@BeforeEach` — noting this because it's a real trap for anyone testing close-listener behaviour under
   MockBukkit, not because it affected production code.
4. Subagent A (G1) reported "worktree not isolated" as a concern — it saw files I (the lead) and Subagent B
   edited concurrently in the same worktree. Expected, not a problem: I sequenced my own edits to `ChestMenu.java`
   and `MenuFlow.java` to run only AFTER Subagent A finished with those specific files, and Subagent B's price-grid
   work touched entirely separate new files. No actual conflict occurred; confirmed by the final green build.

## Red-first evidence

- **`MenuFlow.rerender()`** — new capability, no prior behaviour to be red against. Red = compile failure:
  `./gradlew :menu-inventories:chest:compileTestJava` against the unmodified `MenuFlow.java` →
  `error: cannot find symbol` on `flow.rerender()`. Green: `./gradlew :menu-inventories:chest:test --tests
  "...MenuFlowTest"` → `rerenderReRunsPanelRenderWithoutABukkitReopen` passes, asserting the SAME `Inventory`
  object (`assertSame`) before/after — proving no reopen — and that a conditionally-rendered button appears.
- **`back()` on an empty stack** — genuine behavioural red: `MenuFlowTest.backOnEmptyStackEndsTheFlow()` FAILED
  against the pre-fix `back()` (`org.opentest4j.AssertionFailedError` — `onEnd` never fired). Green after the
  `back()`→`end()` change: full `MenuFlowTest` class green (7/7).
- **`hasBack()`** — new accessor, compile-failure red (same run as `rerender()`'s), green after.
- **Deposit-slot USER-close fix (finding #1)** — ran the two PRE-EXISTING pinning tests against my
  `ChestMenu.close(Player, CloseReason)` fix (before flipping them): both FAILED as expected —
  `DepositSlotComponentTest.closeWithUserSkipsReturnSoBukkitCursorPickupHandlesIt` at line 159,
  `DepositSlotMultiSlotStressTest.closeOnUserSkipsReturnSoBukkitCursorPickupHandlesIt` at line 167 — exactly
  finding #1's documented failure mode. Flipped both to `...AlsoReturnsHeldItems`, asserting the item now lands
  in `player.getInventory()`; green after.
- **Deposit-slot integration test (findings #1+#2 through their real entry points)** — temporarily reverted the
  `ChestMenuCloseListener` wiring and the `MenuFlow.switchInternal` `CloseReason.NAVIGATION` call (commented out
  in place, restored immediately after): `./gradlew :menu-inventories:chest:test --tests
  "...DepositSurvivalIntegrationTest"` → 2 of 3 FAILED (`itemSurvivesAGenuineUserClose` at line 81,
  `itemSurvivesAMenuFlowPanelSwitch` at line 103); `itemSurvivesAServerStop` stayed green, correctly, since
  `PLUGIN_DISABLE` was never subject to the USER-only bug. Restored both fixes → all 3 green.
- **Price-entry grid component** — new capability, compile-failure red (component moved aside, `PriceGridComponentTest`
  failed with `cannot find symbol ... PriceGridComponent`), green after restoring. Covers the exact mirrored slot
  layout, live re-read + clamp on click, and boundary clamping.
- **Placeholder/row-scoped confirmation** — no red/green cycle: this proves already-correct, already-shipped
  behaviour (the plan's own "no new Oriel ask" conclusion), not a bug fix. One iteration bug in my OWN test (raw
  `&`-coded strings built outside `ItemComponent` don't get colour-translated — `PaginatedListComponent.from`
  places pre-built `ItemStack`s verbatim) caught immediately by running it; fixed the test, not production code.

## Build

Full reactor: `./gradlew clean build` → **BUILD SUCCESSFUL in 25s**, 92 actionable tasks (85 executed, 7 from
cache). Aggregated JUnit XML across all 18 modules: **87 test files, 652 tests, 0 failures, 0 errors, 9 skipped**
(same 9 pre-existing skips as every prior gate — unrelated, not investigated). `./gradlew publishToMavenLocal` →
**BUILD SUCCESSFUL**. Test-count math checks out exactly against the prior gate's 640: +4 (G1: `rerender`,
`backOnEmptyStackEndsTheFlow`, `hasBackReflectsBackStackState`, `openMenuTrackerIsLoadableViaServicesManager`)
+3 (`PriceGridComponentTest`) +2 (`PlaceholderResolutionAndRowScopedSourceTest`) +3
(`DepositSurvivalIntegrationTest`) = 652.

## Docket ids touched

No Gangland docket ids apply to the Oriel repo. Cross-docket Oriel findings (`brainstorming/cross-docket-2026-09-10/oriel/findings/`,
first read and listed in full at G0):

- **menu-chest#1** (P0, test-pinned) — FIXED this gate. `ChestMenu.close(Player, CloseReason)` no longer skips
  the item return on `CloseReason.USER`; `ChestMenuCloseListener` now also drains held items on a genuine
  player-initiated close via the new `OrielMenuTarget.returnHeldItemsOnUserClose` hook. Covering tests:
  `DepositSlotComponentTest.closeWithUserAlsoReturnsHeldItems`,
  `DepositSlotMultiSlotStressTest.closeOnUserAlsoReturnsHeldItems`,
  `DepositSurvivalIntegrationTest.itemSurvivesAGenuineUserClose`.
- **menu-chest#2** (P0) — FIXED this gate. `MenuFlow.switchInternal`/`end()` now close the outgoing menu with
  `CloseReason.NAVIGATION` before replacing/ending it, draining held items instead of abandoning them. Covering
  test: `DepositSurvivalIntegrationTest.itemSurvivesAMenuFlowPanelSwitch`.
- All other findings from the G0 list (menu-chest#3-6, menu-config, menu-core, menu-editor, menu-integration,
  menu-inventory, menu-persistence, menu-plugin — 33 total) — untouched this gate, out of scope for what G1/G2
  asked for. Still unfiled as `OR-nn` ids in the live LuckyRaven Bug Docket artifact as far as I know (not
  checked from this Oriel-only worktree).

No new (un-filed) bugs noticed beyond what's already in the cross-docket source.

## Subagents used

- **Sonnet** — G1 (`MenuFlow.rerender()`, `ChestMenu.adoptComponentsFrom`, `OpenMenuTracker` publication,
  `back()`/`hasBack()`, doc fix). Given a fully pre-designed spec (exact method bodies) since I'd already traced
  the code paths; delivered as specified, correct red/green evidence, one honest deviation flagged (applied
  `adoptComponentsFrom` before writing the red test — harmless, additive-only, didn't weaken the red proof).
- **Sonnet** — G2 price-entry grid component (`PriceGridComponent.java` + tests), new/self-contained, no file
  overlap with any other stream. Delivered a clean, well-documented API with correct mirror-math and full
  red/green evidence; verified directly by reading the production file.

Both ran concurrently (no shared files between them); I sequenced my own `ChestMenu.java`/`MenuFlow.java` edits
to start only after Subagent A finished those specific files, avoiding any real edit conflict.

## Concerns / open questions

- **Full on-disk crash-safe persistence (true Oriel A3, surviving `kill -9`)** is explicitly NOT implemented —
  see Deviation #2. If the orchestrator wants this built now rather than left as Oriel's own backlog item, that's
  new scope (a `DatabaseBackend`-backed table + startup recovery pass), not something I did partially and should
  flag as missed.
- **`CloseReason.NAVIGATION` semantics** — this is a genuinely new production behaviour: a panel switch now fires
  a real, explicit `InventoryCloseEvent` for the outgoing inventory before the new one opens (previously the
  close was only ever implicit, via Bukkit's own "opening a new inventory auto-closes the old one"). I traced
  through `ChestMenuCloseListener`'s tracking-guard interaction carefully and believe this produces the same
  client-visible packet sequence as before (no new flicker), but this is exactly the kind of change worth a
  second pair of eyes / a live-server smoke test before shipping, given it touches every `MenuFlow` consumer.
- **menu-chest#3** (on_close fires during internal navigation) is adjacent to my finding #2 fix but explicitly
  out of scope (not asked for this gate) and untouched — still open, still P1.
- G1's `back()`/`hasBack()` change is a real behavioural change for every existing `MenuFlow` consumer (empty
  back-stack now ends the flow instead of no-op'ing) — worth flagging to Gangland's G4/G5 executors since it
  removes the need for their planned `boolean root` fallback (per the original plan's risk #4 mitigation).
- **A3 on-disk persistence (kill-9 survival) is accepted as out of scope in writing (Orchestrator ruling W21).**
  It stays an Oriel backlog item; its docket row (`OR-`, once filed) stays open with a note. Gangland's WS2 no
  longer parks items in Oriel menus (ruling R10 — WS2 re-targeted to a new `keystone-inventory` module), so no
  Gangland gate depends on it.

## Fix round 1 — 2026-09-17

Review: `exec/ORIEL/G1-G2-review.md` (Opus), verdict FIX (1 Critical, 4 Important, minors) + Orchestrator rulings
(W21): one fix round covering F1–F4, F5 = a "verified by reading" statement + a smoke-checklist item, Minor 6;
A3 accepted out of scope in writing (already added above per W21); then commit and stop the Oriel stream (Oriel
is off Gangland's critical path per ruling R10/W20, but this branch is real Oriel 0.8.0 value and F1 was data
loss, so it gets fixed regardless).

### Changes

1. **(Critical 1, F1 — data loss)** `ChestMenu.java`: `adoptComponentsFrom` was clearing an outgoing slot's
   component out of `components` (and its Bukkit `ItemStack`) before anything could return held items through
   it — once gone from `components`, `returnHeldItems`/`returnHeldItemsForSlot` can never find that slot's
   `ItemHoldingComponent` again, so a deposit sitting in a slot a rerender drops was unreturnable, not just
   un-returned. Extracted the per-component item-distribute loop out of `returnHeldItems` into a new private
   `returnHeldItemsForSlot(Player, ItemHoldingComponent, CloseReason)` (both callers share it now — no behaviour
   change for the existing close paths); `adoptComponentsFrom` calls it (reason `CloseReason.NAVIGATION`) for
   every outgoing `ItemHoldingComponent` slot, before nulling it.
2. **(Important 2)** `MenuFlow.java`: `switchTo(panelId)`'s same-id branch now calls `rerender()` instead of
   `switchInternal(panelId, false)` — a same-id `switchTo` was routing through the finding-#2 fix's
   `close(viewer, NAVIGATION)` call, ejecting deposits from a panel the flow never actually left (and would have
   broken `suspend → anvil → resume → switchTo(same)`, since that round trip relies on the panel's state surviving
   untouched). Javadoc updated on both `switchTo` and `rerender()`.
3. **(Important 3)** `MenuFlow.rerender()`: added a row-count guard — if `panel.rows(state) * 9` no longer equals
   the live inventory's actual size (the panel's row count itself depends on state that changed), falls back to
   `switchInternal(currentPanelId, false)` (a full reopen at the new size) instead of calling
   `adoptComponentsFrom` with a component map that would place items past the existing `Inventory`'s slot count.
   Documented as the method's "Row-count rule".
4. **(Important 4)** `PriceGridComponent.install`: added two guards before placing anything — `steps` must be
   strictly ascending (element-by-element check), and `centerSlot % 9 - steps.length >= 0 && centerSlot % 9 +
   steps.length <= 8` (the mirrored row must fit inside `centerSlot`'s own row without wrapping) — both throw
   `IllegalArgumentException` naming the offending value(s).
5. **(Important 5)** `OrielPlugin.java:211`'s `registerService(services, OpenMenuTracker.class);` — confirmed
   unchanged by re-reading the file (not re-tested this round; `openMenuTrackerIsLoadableViaServicesManager`
   registers its own hand-built tracker, so it would pass even with that line deleted, per the review's F5).
   **Stated here, in writing, per W21's instruction**: this publication is verified by reading only. **Smoke-check
   item** (Oriel has no existing checklist doc to add this to, so it lives here per the ruling's own fallback):
   on a live server with Oriel enabled, `Bukkit.getServicesManager().getRegistration(OpenMenuTracker.class)` must
   return non-null and resolve to the SAME instance `MenuTrackingConfig.openMenuTracker(...)` produced (i.e. the
   one every `ChestMenu.open()`/`close()` call already tracks/untracks against) — a container-level test that
   actually boots `OrielContext`/`BeanFactory` would be the stronger fix if `menu-plugin` ever grows a test
   source set; it doesn't have one today.
6. **(Minor 6)** `PriceGridComponent`'s constructor changed from package-private to `private`. `adoptComponentsFrom`'s
   javadoc corrected: it no longer claims titles are immutable post-creation (false — `advanceFrame` retitles a
   live `TextSource.Animated` title via the packet bridge, and `ensureTitleFor` rebuilds the inventory for a
   changed `TextSource.Static` title at open-time); it now says a live `panel.title(state)` goes stale after
   `rerender()` instead, and why that's a deliberate scope limit, not a technical wall. Added a `ponytail:` note
   in `MenuFlow.rerender()` on the per-call `builder.build()` throwaway-`Inventory` allocation (fine at
   click-driven frequency; names the upgrade path if that ever changes). Left `DepositSurvivalIntegrationTest`'s
   (and every other test file's) single-line `Panel` stub bodies alone, per the ruling.

### Red-first evidence

All four production fixes (F1–F4) were verified red by temporarily reverting each one in place (commented out or
restored to the pre-fix line), running the affected tests together, confirming exactly the 5 new tests failed and
every pre-existing test stayed green, then restoring all four and confirming green again:

- Reverted: `ChestMenu.adoptComponentsFrom`'s return-before-clear call, `MenuFlow.switchTo`'s same-id branch,
  `MenuFlow.rerender()`'s row-count guard, `PriceGridComponent.install`'s two new guard blocks.
- Red command: `./gradlew :menu-inventories:chest:test --tests "...DepositSurvivalIntegrationTest" --tests
  "...MenuFlowTest" --tests "...component.PriceGridComponentTest"` → **`18 tests completed, 5 failed`**:
  - `DepositSurvivalIntegrationTest.itemSurvivesARerenderThatDropsItsDepositSlot()` —
    `AssertionFailedError: held state should clear once the item is returned ==> expected: <false> but was: <true>`
  - `DepositSurvivalIntegrationTest.sameIdSwitchToKeepsDepositedItemsInPlace()` —
    `AssertionFailedError: a same-id switchTo must not eject the deposit still in place ==> expected: <true> but was: <false>`
  - `MenuFlowTest.rerenderFallsBackToAFullReopenWhenRowsChange()` —
    `AssertionFailedError: a row-count change must reopen a new inventory, not adopt in place ==> expected: not equal but was: <...>`
  - `PriceGridComponentTest.installRejectsNonAscendingSteps()` —
    `AssertionFailedError: Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.`
  - `PriceGridComponentTest.installRejectsACenterSlotWithNoRoomForTheStepsInItsRow()` — same "nothing was thrown".
- Green command (after restoring all four): same test selector → **`BUILD SUCCESSFUL`**, all 18 tests pass (13
  pre-existing + 5 new: `DepositSurvivalIntegrationTest` 3→5, `MenuFlowTest` 7→8, `PriceGridComponentTest` 3→5).

### Build

Full reactor: `./gradlew clean build` → **BUILD SUCCESSFUL in 30s**, 92 actionable tasks (84 executed, 8 from
cache). Aggregated JUnit XML across all 18 modules: **87 test files, 657 tests, 0 failures, 0 errors, 9 skipped**
(same 9 pre-existing skips as every prior gate). `./gradlew publishToMavenLocal` → **BUILD SUCCESSFUL**. Test
count checks out exactly against the prior gate's 652: +5 (this round's Critical/Important fixes' tests).

### Files touched (fix round 1, on top of G1+G2's original set)

- `menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/ChestMenu.java`
- `menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/flow/MenuFlow.java`
- `menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/component/PriceGridComponent.java`
- `menu-inventories/chest/src/test/java/org/luckyraven/oriel/chest/DepositSurvivalIntegrationTest.java` (+2 tests)
- `menu-inventories/chest/src/test/java/org/luckyraven/oriel/chest/MenuFlowTest.java` (+1 test)
- `menu-inventories/chest/src/test/java/org/luckyraven/oriel/chest/component/PriceGridComponentTest.java` (+2 tests)

### Concerns

- **Cannot verify (per the review, unchanged this round):** mid-click `rerender()` client desync needs a live
  smoke test; whether NAVIGATION produces a double `on_close` dispatch was checked by reading only
  (`ChestMenu.close(Player)` untracks before `closeInventory()`, so `ChestMenuCloseListener`'s tracked-menu guard
  is false by the time the resulting event fires — no double dispatch by this reading, but not test-proven).
- **menu-chest#3** (on_close fires during internal navigation) — still open, still P1, untouched, unrelated to
  this round's fixes.
- **Docket**: per W21, F1 is a new Oriel P0 ("found and fixed in 0.8.0 G1") for the orchestrator to file at the
  docket step — I did not file it myself (no docket-write access from this Oriel-only worktree).
- Per W21: this is the **last Oriel round in this wave** — Oriel is off Gangland's critical path (WS2 re-targeted
  to a new `keystone-inventory` Keystone module, ruling R10), so this branch's value is Oriel's own 0.8.0, to be
  committed once this round is clean.
