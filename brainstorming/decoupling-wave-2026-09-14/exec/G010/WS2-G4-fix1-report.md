# WS2 G4 fix round 1 report — 2026-09-21

Status: DONE

Review: `exec/G010/WS2-G4-review.md` (Opus, transcribed), verdict FIX (1 Important, 4 Minor) — no item-loss path
found; the blocking item was an evidence gap on the test claiming to pin the rerender path (F1). Orchestrator
ruling W43: fix F1-F4 this round; F5 (`MenuFlow.rerender()`'s missing `ended` guard) is Keystone's and goes to the
docket, not this gate — **not touched**, per the coordinator's explicit instruction.
Worktree `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, uncommitted on top of `a7a4a050` (unchanged —
G4 itself was never committed, this fix round lands on the same uncommitted tree). Verified clean-except-G4 via
`git status --short` before starting (23 entries, matching G4's own file list exactly).

## Findings fixed

### F1 (Important) — `roundTrip_survivesRerender` didn't pin the real `MenuFlow.rerender()` path

**Problem**: both `BarterViewItemSurvivalTest`/`SellViewItemSurvivalTest`'s `roundTrip_survivesRerender` called
`ChestMenu.rerender()` directly — a method that only re-invokes `renderAll` against the **already-live** component
set. Production never calls this from `MenuFlow`. `MenuFlow.rerender()`'s real body builds a **fresh**
`ChestMenuBuilder`, re-invokes `Panel.render` into it, then calls `ChestMenu.adoptComponentsFrom(fresh.build())` —
a materially different code path (`components.clear()`, `interactiveSlots.clear()`,
`builderInteractiveSlots = fresh.builderInteractiveSlots`, per `ChestMenu.java:378` in `keystone-inventory`). The
test's own comment claimed to pin "every render is re-invoked fresh," but the assertion never actually exercised
that fresh-render path.

**Fix**: both `roundTrip_survivesRerender` methods now build a second `ChestMenuBuilder` (via
`ChestMenu.builder(rig.inventoryService())`, matching `MenuFlow`'s own default `menuBuilderFactory`), call
`rig.view().title(rig.session())`/`rig.view().rows(rig.session())` for the title/rows (matching
`MenuFlow.rerender()`'s own `panel.title(state)`/`panel.rows(state)` calls exactly), re-render the **same** view
into it via `rig.view().render(rig.flow(), freshBuilder, rig.session())`, then call
`rig.menu().adoptComponentsFrom(freshBuilder.build())` on the **live** menu — the literal production body. To make
this possible, both `Rig` records grew four fields (`view`, `session`, `inventoryService`, `flow`) that `buildRig`
already constructed internally but didn't previously expose.

- `gangland-features/gangland-npc-shops/src/test/.../trader/view/BarterViewItemSurvivalTest.java:246-265`
  (`roundTrip_survivesRerender`, rewritten); `:95-96` (`Rig` record, 4 new fields); `:170` (`buildRig`'s return
  statement, now passing them); class javadoc `:53-62` corrected to describe the `adoptComponentsFrom` path
  instead of "a plain rerender."
- `gangland-features/gangland-npc-shops/src/test/.../trader/view/SellViewItemSurvivalTest.java:223-242`
  (`roundTrip_survivesRerender`); `:91-92` (`Rig` record); `:159` (`buildRig`'s return); class javadoc `:53-58`.

**Incidental fix needed to make the real path runnable**: the second, non-empty render now genuinely walks
`recomputeOffer`'s valuation lookup (the first render's dropzone is empty, so `recomputeOffer`'s loop body never
ran before; the fresh render's dropzone now holds the test's dropped item) — the mocked
`CategoryBarterValuator`/`SellValuator` had no stub for `.value(...)`, so Mockito's `null` default NPE'd on
`ItemValuation.hasValue()`. Stubbed both to return `ItemValuation.UNKNOWN` (the "not accepted" constant — the
exact valuation outcome doesn't matter for this test, only that render completes and the physical item survives).
`BarterViewItemSurvivalTest.java`'s `valuator` stub, `SellViewItemSurvivalTest.java`'s `valuator` stub (both right
next to the mock's own declaration).

**Red-first evidence against the real path** (F1's explicit ask — the review named this exact probe shape):
temporarily added `view.setItem(null)` to `DropzoneSlotComponent.renderInto`
(`gangland-features/gangland-npc-shops/src/main/.../trader/view/DropzoneSlotComponent.java:46-51`), simulating a
naive re-point that clears the slot on every render instead of relying on `interactive(true)` alone (no `setItem`
call) to leave it untouched.
- Command: `mvn -pl gangland-features/gangland-npc-shops -am test -Dtest=BarterViewItemSurvivalTest,SellViewItemSurvivalTest -Dsurefire.failIfNoSpecifiedTests=false`.
- Red: `Tests run: 8, Failures: 2` — **exactly** the two `roundTrip_survivesRerender` tests failed:
  `AssertionFailedError: adoptComponentsFrom (MenuFlow.rerender()'s real body) must not disturb a physically-dropped
  item ==> expected: <ItemStack{GOLD_INGOT x 3, null}> but was: <null>` (Barter) and the `x 5` sibling (Sell); the
  other 6 tests (item-return-on-close, item-return-on-disconnect, item-return-on-panel-switch, both views) stayed
  green — confirming the probe is isolated to the render/rerender path this finding is about, not a general
  breakage.
- Reverted `DropzoneSlotComponent.renderInto` to its original body; green: full module run,
  `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`.

### F2 (Minor) — dropzone slots didn't set the builder's permanent interactive floor

**Problem**: `builder.interactive(slot)` (Keystone's N1 permanent floor, `ChestMenu.java:85`/`renderAll:517`) was
never called beside the dropzone `builder.slot(slot, new DropzoneSlotComponent(slot))` calls — only
`DropzoneSlotComponent.renderInto`'s own `view.interactive(true)` line set it, dynamically, per render. If that
component ever threw before reaching its `interactive(true)` line, the slot would fall back to non-interactive
(click-cancelled) with no floor to catch it.

**Fix**: added `builder.interactive(slot);` immediately before each `builder.slot(slot, new
DropzoneSlotComponent(slot))` call, in both views' dropzone-declaration loops.
- `gangland-features/gangland-npc-shops/src/main/.../trader/view/BarterView.java:204-210` (the loop; `:208` is the
  new line).
- `gangland-features/gangland-npc-shops/src/main/.../trader/view/SellView.java:197-203` (`:201` is the new line).

### F3 (Minor) — write-only `committed` field + stale comments

**Problem**: `boolean committed` (a `BarterState`/`SellState` field) was written in `onShutdown`/`onBack`/
`onConfirm` but never read anywhere — a leftover from an earlier design where `onFlowEnd` did its own conditional
return pass (`if (!st.committed) returnItemsToPlayer(...)`), before this gate replaced that with Keystone's
`ItemHoldingComponent` contract. Three comments describing that removed behavior ("avoid returning twice from
onFlowEnd's belt-and-suspenders path", "committed=true skips onFlowEnd's belt-and-suspenders return...") were left
behind, actively misleading about what `onFlowEnd` (now just `active.remove(viewer)`) actually does.

**Fix**: deleted the field declaration and every assignment to it (`onShutdown`, `onBack`, `onConfirm` in both
views) along with the three stale comments; `onShutdown`'s explicit `returnItemsToPlayer(...)` call — the actual
behavior, not the dead bookkeeping around it — is unchanged, per the ruling.
- `gangland-features/gangland-npc-shops/src/main/.../trader/view/BarterView.java`: field removed (was `:487`),
  `onShutdown` (was `:186`), `onBack` (was `:345`), `onConfirm` (was `:372,374`) — all four sites cleaned.
- `gangland-features/gangland-npc-shops/src/main/.../trader/view/SellView.java`: field removed (was `:423`),
  `onShutdown` (was `:180`), `onBack` (was `:307`), `onConfirm` (was `:345,347`) — all four sites cleaned.
- Confirmed zero remaining references: `grep -n "committed" BarterView.java SellView.java` → no hits.

### F4 (Minor) — unreachable `getOpenInventory()`/`getTopInventory()` stubs

**Problem**: both test rigs stubbed `player.getOpenInventory()`/`.getTopInventory()` believing it fed
`ChestMenu.close(Player)`'s bookkeeping check — but the read of `getOpenInventory()` itself throws
`IncompatibleClassChangeError` against this mocked `Player` in this environment (the documented Keystone-1.16.5-vs-
Gangland-1.21.11 `InventoryView` class/interface mismatch), regardless of what the stub would have returned. The
stubs were dead code the reviewer confirmed could never be reached.

**Fix**: removed both stub blocks (and the now-unused `InventoryView` import in both files), replacing them with a
one-line comment explaining why no stub is needed there. The `catch (IncompatibleClassChangeError)` in
`closeIgnoringKnownBukkitVersionGap` is unchanged — the reviewer confirmed it's correctly placed (after
`returnHeldItems`, the first statement of `ChestMenu.close(Player, CloseReason)`, so it masks nothing this test
verifies).
- `gangland-features/gangland-npc-shops/src/test/.../trader/view/BarterViewItemSurvivalTest.java`: stub block
  removed (was `:151-153`), import removed.
- `gangland-features/gangland-npc-shops/src/test/.../trader/view/SellViewItemSurvivalTest.java`: stub block removed
  (was `:139-141`), import removed.

## Not touched (per ruling)

F5 (`scheduleRecompute` vs. `MenuFlow.rerender()` missing an `ended` guard) — Keystone-side, goes to the docket as
KS-IV-05 per the review's "For the docket" list, not fixed in this gate. No npc-shops file outside the four
findings above was touched; scope stayed within `gangland-features/gangland-npc-shops`.

## Build

**Module gate**: `mvn clean install -pl gangland-features/gangland-npc-shops -am` → **BUILD SUCCESS**. Module test
count, summed fresh from its own `target/surefire-reports/*.txt`: **Tests run: 17, Failures: 0, Errors: 0,
Skipped: 0** (unchanged from G4's own count — this round fixed existing tests, added none).

**Whole reactor**: `mvn clean install` (full reactor) → **BUILD SUCCESS**. Aggregate test count, summed fresh from
every module's own `target/surefire-reports/*.txt`: **Tests run: 875, Failures: 0, Errors: 0, Skipped: 0**
(unchanged from G4's own reactor total — same reasoning: no test added or removed this round, only two existing
ones corrected to exercise the right production path).

## Files touched (fix round 1, on top of G4's own 23)

**Modified** (5, all `gangland-features/gangland-npc-shops/`):
`src/main/java/org/luckyraven/gangland/npcshops/trader/view/{BarterView,SellView,DropzoneSlotComponent}.java`
(`DropzoneSlotComponent.java` only briefly, for the F1 red probe — reverted to its G4 state, net diff is the
class's own javadoc/comment shape unchanged), `src/test/java/org/luckyraven/gangland/npcshops/trader/view/
{BarterViewItemSurvivalTest,SellViewItemSurvivalTest}.java`.

No files added or deleted this round.

Review package (real hunks, `git add -N . && git diff HEAD -M -U5`, intent-to-add reset afterward so the worktree
stays uncommitted but clean): `exec/G010/WS2-G4-fix1-package.diff` (cumulative with G4 itself, since nothing has
been committed between gates — same shape as WS2-G3's own fix-round-1 package).
