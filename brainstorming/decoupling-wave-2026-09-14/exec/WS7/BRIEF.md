# WS7 lead brief — jetpack ownership + soft Bartizan (Gangland 0.9.2, Bartizan 0.4.0)

Rules: `../LEAD-RULES.md` (read first).

## Worktrees
- Bartizan 0.4.0: `E:\Programming\java\wt\bartizan-0.4.0` — branch `0.4.0` off `0e9e456` (= today's 0.3.0 HEAD).
- Gangland 0.9.2: `E:\Programming\java\wt\gangland-0.9.2` — branch `0.9.2` off `0.9.1` (`fb460b35`).
- Both are clean checkouts. `~/.m2` already holds keystone-* 1.9.2 and bartizan-api 0.1.0/0.2.0/0.3.0.

## Plan (binding text)
`E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\gadget-wave-2026-09-16\plans\WS7-gadget-ownership.md`
— read it whole; **§0c is the final ruling layer**, §2 the target layout, §3 seams, §4 the gate table, §5–§7
config/persistence/tests, §11 docket ids. Context: `../../PLAN.md` §9a (one screen). The census files
`brainstorming/gadget-wave-2026-09-16/census/C1..C3` hold the file inventories — use them instead of re-scanning.

## User decisions (binding, from the artifact db)
- K1–K9 as fixed in `brainstorming/gadget-wave-2026-09-16/README.md`.
- **WS7-D1 = C**: civilians goes soft on Bartizan, cops-n-crooks stays hard → **new gate G5b** (after G5): drop
  `Plugins: [Bartizan]` from `gangland-civilians/module.yml`, guard its Bartizan-touching sites the same way G5
  guards gadget (census C2 §1/§4 lists the 7 weapon-inherent + 4 replaceable sites; `BartizanNpcWeapons:35-36,54-55`
  already has the null-safe fallback), generalise `BartizanBlindScan` so both modules use it (S3), and a
  `CiviliansBartizanBlindScanTest`. `turf` then loads without Bartizan (its only edge is `Depends: [civilians]`).
- **WS7-D4 = the user's own option** (not A, not B). User's words: *"jetpack would not lose the traits if bartizan
  as the api layer. Bartizan handles reference for already existing gadgets and changes on them."* Orchestrator
  ruling W2: the jetpack is gadget-owned exactly as the plan says, **and** when Bartizan is present gadget hands the
  jetpack's armour traits (`Base_Damage_Reduction 0.05`, `REINFORCED 1`, `LIGHTWEIGHT 2` — an optional
  `Bartizan_Traits:` block in `items/jetpacks.yml`) to Bartizan through `BartizanApi`, inside the
  `Settings.isBartizanAvailable()` guard, via a static bridge helper (same shape as `CarMeleeWeaponLookup`, B5) so
  K1/K2 hold. Bartizan 0.4.0 G0 therefore gains the **smallest** api hook that lets an external plugin register an
  armour `ItemStack` identity + traits so Bartizan's existing damage-reduction/trait path applies to it — no trait
  reimplementation in gadget, nothing Gangland-specific in Bartizan. **Design first:** a Haiku census of how
  Bartizan resolves a worn item to its traits (which NBT tag, which listener, `WearableAddon`/`Wearable`), then you
  write `exec/WS7/D4-design.md` (≤ 60 lines: the Bartizan api addition with its signature, the gadget bridge class,
  what happens when Bartizan is absent, the tests) and **stop for review** before implementing it (see batches).
- WS8 (grappling hook) is not yours; it starts after your G1.

## Gate batches — STOP and report after each batch
| Batch | Gates | Report file (under `exec/WS7/`) |
|---|---|---|
| 1 | G0 (Bartizan, incl. the D4 api hook *if* the design is trivial — else leave the hook for batch 2) + G1 (Gangland) + `D4-design.md` | `G0-G1-report.md` |
| 2 | G2 + G3 (+ the D4 bridge once the design is approved) | `G2-G3-report.md` |
| 3 | G4 | `G4-report.md` |
| 4 | G5 + G5b | `G5-report.md` |
| 5 | G6 (docs, docket rows, `graphify update . --force` in the two worktrees) | `G6-report.md` |

After a batch report the orchestrator reviews, commits, and messages you with either findings to fix (fix in
place, append a "Fix round" section to the same report, re-run the covering tests) or "proceed to batch N".
Bartizan must be `mvn clean install`ed (0.4.0 in `~/.m2`) before Gangland's root pom `bartizan.version` moves.

## Smoke
The console harness `brainstorming/bartizan-split-2026-09-08/smoke/smoke.py` (README beside it) drives the test
server at `E:\Documents\Minecraft\Test Server`. Use it only at G4/G5 for the rows the plan names; if the server or
harness is unavailable, say so in the report — do not block on it.
