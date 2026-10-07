# WS8 lead brief — gadget catalogue: the grappling hook (Gangland 0.9.2, after WS7)

Rules: `../LEAD-RULES.md` (read first).

## Worktree
`E:\Programming\java\wt\gangland-0.9.2` — branch `0.9.2`, already carrying WS7's commits (jetpack rehome, soft
Bartizan). Build baseline before you start: `mvn clean install -DskipTests` must be green; if not, report BLOCKED.

## Plan (binding)
`E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\gadget-wave-2026-09-16\plans\WS8-gadget-catalogue.md`
(whole file; §0b review response overrides earlier text) and `../../PLAN.md` §9b. WS7's finished code is the
pattern you copy: `ItemKind.JETPACK`, `items/jetpacks.yml`, `Jetpack`/`JetpackKey`/`JetpackAddon`,
`JetpackConverter`/`JetpackItemSerializer`/`JetpackItemRefresher`, `/glw jetpack give` — read those first
(`graphify query "Jetpack"` after `graphify update . --force` has run in the worktree, or `git log --stat` there).

## User decisions (binding)
- WS8-D1 = **cooldown only** (no fuel/durability through `FuelService`).
- WS8-D2 = grappling hook first; parachute + smoke/flash are the next two (proposal only, not this stream).
- WS8-D3 = per-type `/glw grapple give` (car/jetpack precedent); no `Gadget` interface, no registry (K5).

## Gates — STOP and report after each batch
| Batch | Gates | Report (under `exec/WS8/`) |
|---|---|---|
| 1 | G1 item (`ItemKind.GRAPPLE`, `items/grapples.yml`, domain type, addon, converter/serializer/refresher, predicates, beans) | `G1-report.md` |
| 2 | G2 mechanics (`PlayerFishEvent` launch, hook lands within `Max_Range`, LOS ray trace, per-tick capped pull, cancel on damage/sneak/timeout/chunk unload) + G3 anti-abuse + fall damage (must **not** reproduce GD-04's shape — read the docket entry text in `brainstorming/bug-docket-2026-09-06/` first) | `G2-G3-report.md` |
| 3 | G4 give command + permissions + `commands.json`; G-final docs (`CLAUDE.md` module table line, `documentation/module-loader.md`), docket rows, `graphify update . --force` | `G4-report.md` |

`PlayerFishEvent` behaviour differs across 1.16–1.21: the plan asks for an early smoke on the test server
(`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`); if the harness is unavailable, write the
version-branching down in the report and mark it "not smoked".
