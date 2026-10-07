# Gangland 0.10.0 lead brief — G0 (api 2.0) → WS1 G3 (scoreboard out) → WS2 Gangland half → WS4 consumer → CUT → WS3 → WS5 → WS6

Rules: `../LEAD-RULES.md` (read first). This is the long sequential Gangland stream of PLAN.md §12.

## Worktree
`E:\Programming\java\wt\gangland-0.10.0` — branch `0.10.0` off `0.9.2` @ 9a44ea45 (WS7 complete + WS8 G1). WS8's
later commits (G2–G4, G-final) land on 0.9.2 and are merged into 0.10.0 by the orchestrator before WS2 G5. `CLAUDE.md`
(gitignored) and `graphify-out/` were copied from the 0.9.2 worktree; run `graphify query` with cwd = this worktree.
`~/.m2` holds Keystone **1.11.0** (keystone-hologram, keystone-shop, keystone-inventory), Plaque 0.1.0 is a separate
repo (`E:\Programming\java\Plaque`, master c0fd9d4) — WS1 G3 assumes owners install it.

## Binding plans
- `../../PLAN.md` §1 (0.10.0 row: **G0 = first commit bumps `GanglandApi.VERSION` and every `module.yml` `Host_Api`
  to 2.0**, R1), §2 rulings R1–R11, §12 order.
- WS1: `../../plans/WS1-scoreboard.md` (G3 "remove from Gangland incl. CM-04 fix", G4 docs/docket) + its §0d.
- WS2: `../../plans/WS2-inventory-keystone.md` (G1 plumbing → G2 domain inversion → G3a move 46 dialect files →
  G3 parser over `ChestMenuBuilder` + `Multi.*` on `PagedRegion` → G4 npc-shops → G5 turf/cops/gadget → CUT) + its §0d
  (interactive non-ItemHolding slots discard player items on close — consumer responsibility in G4).
- Keystone's module guide for the consumer side: `E:\Programming\java\wt\keystone-1.11.0\docs\keystone-inventory.md`
  and `docs\phase-e6-inventory.md` (item-return contract, two-service rule, `handleDepositClick`, `MenuFlow` detour rule).
- WS4: `../../plans/WS4-shop.md` (G1a headless repoint, G1b admin views on keystone-inventory, `ShopAdminOpener`) + §0d.
- WS3: `../../plans/WS3-lootchest-hologram.md` (G1 consume keystone-hologram, delete hologram-api; G2–G5).
- WS5: `../../plans/WS5-gang.md` + §0c (R7/R9 `GangMembership` holder). WS6: `../../plans/WS6-api.md` + §0c.

## Rulings that bind this stream
W31 (start off 9a44ea45, merge WS8 later), W32 (G0 also bumps `<revision>` → 0.10.0 and `keystone.version` → 1.11.0;
adapt any Keystone API drift inside G0), R1 (api 2.0 in the first commit), R3 (CUT only after a grep proves zero
importers of `org.luckyraven.gangland.inventory`), R11 (lootchest consumers = WS3, shop views = WS4), R6 (api never
re-exports keystone-inventory), user decision N1 = Plaque, WS1-D2 keystone-command (already built), WS2-D1 settled
by R10 (no Oriel at all).

## Batches — STOP and report after each
| Batch | Gates | Report (under `exec/G010/`) |
|---|---|---|
| 1 | **G0**: revision 0.10.0, `GanglandApi.VERSION` → "2.0", every `module.yml` `Host_Api: 2.0`, keystone.version 1.11.0, whole reactor green, module loader test expectations updated; **WS1 G3**: delete `gangland-ui/scoreboard-api` and every wiring site the plan lists (impl beans/listeners/lifecycle, `User.scoreboard`, `UserManager.onPreClear` scoreboard half, `Scoreboard:` settings block, `scoreboard.yml`, poms, FastBoard shade), the CM-04 fix, tests flipped/deleted red-first, `documentation/` + CLAUDE.md pointers to Plaque | `G0-WS1-report.md` |
| 2 | WS2 G1 (poms: keystone-inventory `provided` where inventory-api was; `plugin.yml` unchanged) + G2 (sever the domain inversion: `User`'s `Set<InventoryHandler>` / `InventoryRegistry` → the service's tracker; the three domain tests) | `WS2-G1-G2-report.md` |
| 3 | WS2 G3a (move the 46 dialect files to `gangland-impl` `org.luckyraven.gangland.menu.*`, no behaviour change, one commit) + G3 (`InventoryParser`/handler dialect target `ChestMenuBuilder`; nine YAML menus load unchanged — a round-trip test per menu; `Multi.*` paginated stack on `PagedRegion`) | `WS2-G3a-G3-report.md` |
| 4 | WS2 G4 npc-shops (18 files; barter/sell views as interactive-slot re-points with the item-survival test per view) | `WS2-G4-report.md` |
| 5 | WS2 G5 turf · cops-n-crooks · gadget (after the orchestrator merges 0.9.2's WS8 commits) | `WS2-G5-report.md` |
| 6 | WS4 G1a headless repoint to keystone-shop + G1b the nine admin views on keystone-inventory + `ShopAdminOpener` | `WS4-report.md` |
| 7 | CUT: delete `inventory-api` (R3 grep gate), `anvilgui` shade drop if unused, docs | `CUT-report.md` |
| 8 | WS3 G1–G5 (hologram consumer, lootchest module) | `WS3-report.md` |
| 9 | WS5 G0–G5 (gang module) | `WS5-report.md` |
| 10 | WS6 G0–G4 (api facade) + END (docs, smoke, graph) | `WS6-report.md` |

Every batch: whole reactor `mvn clean install` green, red-first tests, smoke rows where the plan names them
(harness `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` — add rows, paths already point at the
worktree jars; for this stream set `paths.repo_dir` to this worktree when you run), docket ids listed in the report.
Subagents: ≤2 at a time, one Maven build per worktree at a time, exact file lists.
