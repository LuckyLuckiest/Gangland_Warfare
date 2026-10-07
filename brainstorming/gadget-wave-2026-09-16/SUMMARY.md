# Gadget wave — SUMMARY (2026-09-16)

## Why the jetpack was moved to Bartizan, and the fix
Before 0.9.0 the jetpack was a wearable, and the Bartizan split ruled "Bartizan owns wearables" — so the jetpack's
item identity/config went to Bartizan's `wearables.yml` while its flight behaviour stayed in `gangland-gadget`,
requiring gadget to hard-depend on Bartizan (`Plugins: [Bartizan]`, fail-fast) purely to know what a jetpack *is*.
The census found this coupling is mostly load-bearing on identity, not weapons: only `JetpackTask.isScoped()`
(checks weapon-zoom state) and `CarDamageListener`'s two weapon-impact handlers are genuinely weapon-inherent —
everything else is a Gangland-owned item masquerading as a Bartizan dependency. **The fix (WS7)**: give the jetpack
its own `ItemKind.JETPACK`, YAML, serializer/refresher and NBT identity in gadget (copying the car's already-proven
three-registry pattern), delete `isScoped()` (the one avoidable weapon-inherent site), split the one real remaining
edge into its own conditionally-loaded listener, and drop Bartizan to a soft dependency for gadget.

## Coupling table (site → classification → target home)
| Site | Classification | Target home after WS7 |
|---|---|---|
| Jetpack identity/`extraTags()`/fuel keys (`JetpackService`, `JetpackSession`, `GadgetModuleConfig`) | REPLACEABLE | gadget-owned `ItemKind.JETPACK` + `items/jetpacks.yml` |
| `JetpackTask.isScoped()` (weapon-zoom check) | WEAPON-INHERENT but avoidable | **deleted** (ponytail; retires WS7-D2) |
| `CarDamageListener`'s 2 weapon-event handlers | WEAPON-INHERENT, real | split into a conditional `CarWeaponDamageListener` (Bartizan soft) |
| Civilians' `CombatEligibility` SPI + `GangAllyWeaponImpactListener` | WEAPON-INHERENT | stays hard-coupled unless WS7-D1 goes soft |
| Civilians' `BartizanNpcWeapons` factory | REPLACEABLE (already null-safe, currently dead code — module fails fast before it runs) | candidate for soft |
| cops-n-crooks' 3 `WeaponRaytraceImpactEvent`/`WeaponShootEvent` listeners | mostly WEAPON-INHERENT (1 REPLACEABLE: `DetainmentListener`) | stays hard unless WS7-D1 goes soft |
| turf | no direct coupling — 100% transitive via `Depends: [civilians]` | unaffected directly; inherits civilians' choice |
| Bartizan's jetpack `wearables.yml` entry, `legacyJetpackToExtraTags`, its test, migration.md §3.4 | Gangland residue | leaves Bartizan (0.3.0 → 0.4.0) |
| Bartizan's `Wearable`/`WearableCatalog`/`Extra_Tags` parsing | generic, serves other wearables | stays untouched |

## Verdicts
- **WS7 (jetpack ownership)**: `plans/WS7-gadget-ownership.md` (339 lines) — **PASS WITH FIXES → PASS** (B5/B6/B7 + the `JetpackItemRefresher` registration applied by the orchestrator as plan §0c on 2026-09-16, per the re-review's stated condition). Two fix-pass/re-review rounds ran. Remaining before an executor starts: **B5** (`CarMeleeWeaponLookup` must be a static helper, not a `@Bean` — as written it re-triggers the exact `NoClassDefFoundError`/bean-scan crash it was meant to fix), **B6** (`CarWeaponDamageListener` can't be both `@ListenerHandler` and `@Bean` — repo rule `feedback_listener_bean_conflict`), **B7** (a test assertion needs rewording to match the static-helper fix), and a **withdrawn simplification**: WS7 must register a `JetpackItemRefresher` in G3 (mirroring `CarItemRefresher`) — Bartizan's `WearableRefresher` currently prevents shop-purchased jetpacks from carrying frozen/partial fuel state, and removing the jetpack from Bartizan without a Gangland-side replacement is a real regression, not a safe cut. All three are precise, small (≈1 day total) plan-text fixes, not open design questions.
- **WS8 (gadget catalogue)**: `plans/WS8-gadget-catalogue.md` (337 lines) — **PASS**, ready to execute as written. One fix-pass round (4 blockers, all fixed) + a clean re-review with 3 residual risks noted (stale shop templates until re-issued — accepted; `PlayerFishEvent` behaviour across the 1.16–1.21 floor needs an early G2 smoke, not a G-final one; the parachute, if picked next, inherits GD-04/GD-07-style bugs and should be budgeted with corrected shapes, not a straight mirror).

## Decisions the user must make
| Id | Question | Recommendation |
|---|---|---|
| WS7-D1 | civilians/cops Bartizan dependency: soft or hard | No forced default. Options: (A) both stay hard — status quo, Bartizan-absent takes out civilians+cops+turf; (B) both go soft — degrades to unarmed NPCs, activates already-written but currently-dead null-safe fallback in `BartizanNpcWeapons`; **(C, reviewer-added) soft civilians / hard cops** — unblocks turf (only depends on civilians) while cops-n-crooks, the heavier weapon-detection consumer, stays as-is |
| WS7-D4 | Jetpack loses Bartizan's armor traits (`Base_Damage_Reduction 0.05`, `REINFORCED 1`, `LIGHTWEIGHT 2`) once it's no longer a Bartizan-registered wearable | **Accept the loss, document it** (ponytail default) vs. re-implement equivalent reduction inside gadget (new code/abstraction for one item) |
| WS8-D1 | Grappling hook resource model | **Cooldown only, no fuel/durability** — `FuelService` is confirmed reuse-ready, but a continuous-drain model doesn't fit a one-shot trigger item; upgrade path is a predicate-composition change to `FuelService.setFuelSinkPredicate`, not "one line" |
| WS8-D2 | First three gadgets after the grappling hook | **Grappling hook (mandatory) + parachute (cheapest, highest seam reuse) + smoke/flash device** (proves the zero-Bartizan-throwable pattern) from an 8-candidate shortlist (parachute, spike strip, lockpick tied to the safe-cracking roadmap, smoke/flash, drone/camera, disguise kit [recommended drop — no existing seam], boombox/radio, getaway flare) |
| WS8-D3 | Give-command shape | **Per-type** (`/glw jetpack give`, `/glw grapple give`, ...) matching the car precedent — both plans independently converged here; a generic `/glw gadget give <id>` + registry is explicit YAGNI until the shortlist grows past ~4 |

## Estimates
| Workstream | Estimate | Notes |
|---|---|---|
| WS7 | **~11.5 executor-days across 7 gates** (G0–G6, incl. a docs-sweep gate) | B5/B6/B7 + the `JetpackItemRefresher` registration folded in (§0c); G3 is 3.0 d |
| WS8 | **~4.5–5 executor-days** for the grappling hook only (G0–G-final) | The 7 remaining shortlist candidates are proposal-only, not costed |
| **Wave total** | **~16–17 executor-days**, Gangland 0.9.2, one new branch off 0.9.1 | Bartizan 0.4.0 on its own branch (residue removal, small); Keystone untouched |

## Text blocks for the orchestrator to merge into `decoupling-wave-2026-09-14/PLAN.md`
**§0 table** (new rows):
```
| WS7 | Jetpack identity+config live in Bartizan (`wearables.yml` jetpack entry); gadget `Plugins: [Bartizan]` fail-fast | Jetpack fully gadget-owned (`ItemKind.JETPACK`, `items/jetpacks.yml`, own serializer/refresher/NBT identity); gadget's Bartizan dependency is soft | Gangland, Bartizan |
| WS8 | No gadget beyond car + jetpack | Reusable per-gadget registration pattern (proven by WS7) + grappling hook shipped + 8-candidate shortlist | Gangland |
```
**§1 table** (new row): `| Bartizan | 0.3.0 | **0.4.0** on its own branch: jetpack residue removed (wearables.yml entry, legacy converter+test, migration.md §3.4 superseded) | Gangland-specific residue leaves per WS7/K3 |`
**§2 ordering sentence**: prepend "Gangland **0.9.2** (WS7 jetpack/gadget ownership → WS8 gadget catalogue) executes on a new branch off 0.9.1, **before** Gangland 0.10.0's G0" to the existing diagram — no plan or review argued for a different position.
**§10 decision rows**: append the 5 rows from "Decisions the user must make" above, ids WS7-D1/D4, WS8-D1/D2/D3.
**§11 estimate rows**: append `| WS7 | ~11 exec-days | jetpack rehome, 3 small fixes outstanding | | WS8 | ~4.5–5 exec-days | grappling hook only |` and revise the wave total to include ~16–17 exec-days upstream of the existing 46–50.

## New bugs found (K8)
None confirmed for triage. One candidate was investigated and **retracted**: a proposed "GD-29" (jetpack `fuel_efficient` trait desync on Bartizan-absence) turned out to rest on a wrong fact — `traitLevel()` reads static wearable-definition config, never per-item NBT, the NBT prefix that once held it is write-only (never read back), and the shipped jetpack doesn't even carry that trait. No triage file written.

## Docket disposition (GD-04/05/07/08/09/10/12/15/20)
Full per-id detail is in `plans/WS7-gadget-ownership.md` §11. Headlines: **GD-12** (jetpack without `Fuel_Key` never flies) is fixed for free by making `Fuel_Key` mandatory at YAML-load time. **GD-20** splits: its jetpack half (dead `glide_descent_rate` key) becomes moot by omission from the new YAML; its car half (`Car.maxHealth` display-only dead field) is unrelated and stays open. **GD-27** (a `pendingRightClickInteract` token race, not in the original 9) is newly implicated by the `CarDamageListener` split and added to WS7's docket table. The other 6 (GD-04/05/07/08/09/10) remain open — WS7's scope doesn't touch their fix logic, only the item's identity/ownership.

## Anything unverifiable
Whether any live server has production DB rows or shop listings keyed to the old `wearable:jetpack`/`WEARABLE:jetpack` identity string (both plans give a SELECT-before-cutover mitigation, but no live data was inspected); `Sounds:` nested-map round-tripping through Gangland's YAML loader; the actual runtime (not just static-read) behavior of the `isBartizanAvailable()` conditional listener gate once B5/B6 are fixed — needs a real boot-without-Bartizan smoke test; `PlayerFishEvent.State` behavior across the 1.16–1.21 version floor for the grappling hook; whether any operator hand-added a `FUEL_EFFICIENT` trait to a live jetpack (moot given the retraction above, but not disprovable in-repo).

## Agent health
All 8 subagents (3 Haiku census, 2 Sonnet planners, 1 Opus reviewer run across 4 turns) completed cleanly — none died, none hit a session limit, no restarts needed. The Opus reviewer session was reused across WS7 review → WS8 review → WS7 re-review → WS8 re-review per the 1-Opus-at-a-time rule.
