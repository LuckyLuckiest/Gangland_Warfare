# Review — Oriel 0.8.0 G1+G2 — 2026-09-17 (Opus, transcribed)
Verdict: FIX (1 Critical, 4 Important, minors)

## Spec table (condensed)
`MenuFlow.rerender()` in place ✅ (`MenuFlow.java:157-165`, `ChestMenu.adoptComponentsFrom` :466-478; handlers refreshed per slot :917-932; documented `MIGRATION-FROM-GLW.md:383-386`) · `OpenMenuTracker` published ✅ (`OrielPlugin.java:211`, interface-typed bean `MenuTrackingConfig.java:30`, unregistered on disable) but evidence weak (F5) · `back()` empty stack → `end()`, `hasBack()` ✅ (`MenuFlow.java:128-145`; no Oriel-shipped caller relied on the no-op; doc :388-390) · doc fix ✅ · consumer placeholders + row-scoped tokens ✅ (`PlaceholderResolutionAndRowScopedSourceTest` uses a consumer lambda; YAML path `ChestMenuLoader.java:357`) · anvil search dropped (W13) ✅ · deposit persistence ❌ two of three halves: menu-chest#1 (`ChestMenu.java:387-391`) and #2 (`MenuFlow.java:216-218,190`) fixed, return path safe (`:670-708`), kill-9/on-disk A3 (`USE-CASE-ENABLERS.md:77-86`) not built · price grid mirror-outward ✅ (`PriceGridComponent.java:72-89`), bounds gap F4

## Findings
**Critical 1** — `ChestMenu.adoptComponentsFrom` (:466-478) clears a slot + handlers for a component the fresh render dropped; if that component is an `ItemHoldingComponent` holding a stack, it leaves `components`, so `returnHeldItems` (:673) can never return it — the item is gone from the inventory and unreturnable. Fix: before `inventory.setItem(oldSlot, null)`, if the outgoing component holds items for the viewer, run the return path for that slot.
**Important 2** — `switchTo(<current panel id>)` (:119-126, documented as re-render in place) now goes through `close(viewer, NAVIGATION)` (:216-218) and ejects deposits; breaks `suspend → anvil → resume → switchTo(same)`. Fix: same-id `switchTo` → `rerender()`; update javadoc.
**Important 3** — `rerender()` (:161-164) builds `fresh` at `panel.rows(state)` but adopts into the live inventory's size; slots ≥ size fail inside `Guard.of("render.component")` every pass. Fix: if the row count differs, fall back to `switchInternal(currentPanelId, false)` (or throw a named `IllegalStateException`); document.
**Important 4** — `PriceGridComponent.install` (:72-89) never checks `centerSlot % 9 ± steps.length` stays in the row nor that `steps` is ascending. Fix: one guard block with `IllegalArgumentException`s.
**Important 5** — `openMenuTrackerIsLoadableViaServicesManager` registers its own tracker; it passes with `OrielPlugin.java:211` deleted. Fix: a `menu-plugin` test against the container, or state "verified by reading only" and book a smoke check.
**Minor 6** — `PriceGridComponent.java:41` ctor should be private; `adoptComponentsFrom` javadoc claims titles are immutable while `advanceFrame` retitles via `PacketBridge` (:881) — say instead that a live `title(state)` goes stale after `rerender()`; `ponytail:` note for the per-call `builder.build()` inventory allocation; single-line method bodies in `DepositSurvivalIntegrationTest` panel stubs (pre-existing style).

## Cannot verify
Mid-click `rerender()` client desync (live smoke); no double `on_close` on NAVIGATION verified by reading (`ChestMenu.java:346-354` untracks before `closeInventory()`); menu-chest#3 unchanged.

## Notes
- A3 decision: menu-chest#1/#2 fixed (tests listed); Oriel A3 (on-disk, startup recovery) stays open — accept in writing or schedule before a Gangland deposit UI ships.
- Docket candidate (new P0): F1, `cross-docket-2026-09-10/oriel/findings/menu-chest.txt`.
- Cross-gate: `switchTo(same)` and `back()` semantics changed for every MenuFlow consumer.

## Orchestrator rulings (W21)
Oriel is off the wave's path (R10, W20) but this branch is real Oriel value and F1 is data loss: one fix round covering F1–F4, F5 = "verified by reading" statement + a smoke row on Oriel's own checklist, Minor 6 (private ctor, corrected javadoc, ponytail note; leave the test-stub style). A3 kill-9 persistence: **accepted as out of scope in writing** — Oriel backlog item, docket row `OR-` stays open with a note; Gangland's WS2 no longer parks items in Oriel menus (R10), so no Gangland gate depends on it. Then commit on Oriel 0.8.0 and stop the stream. F1 is filed as a new Oriel P0 "found and fixed in 0.8.0 G1" at the docket step.
