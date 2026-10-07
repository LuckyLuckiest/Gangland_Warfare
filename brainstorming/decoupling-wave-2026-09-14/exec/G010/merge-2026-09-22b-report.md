# Merge verification report (round 2) — 2026-09-22

Status: **DONE**. HEAD `837966c3` ("0.10.0 WS3 G4+G5: the loot-chest module owns its config, messages and
commands; lootchest-api is gone") confirmed via `git log -1`, tree clean on arrival. No commits made this session.

## 1. Whole reactor build

`mvn clean install` (one build) → **BUILD SUCCESS**, 01:13 min, all 17 reactor modules `SUCCESS`
(`gangland-ui/lootchest-api` is now gone from the reactor entirely, confirmed — 17 not 18 this time). **No
merge-caused compile break — nothing to fix.**

**Test count (Maven console rollup, per W52 — file-based summing under-counts `@Nested` test classes, established
last round): 891, 0 Failures, 0 Errors, 0 Skipped** — exactly matching the expectation.

| Module | Tests run |
|---|---|
| Gangland Core | 67 |
| Gangland Item | 43 |
| Sign API | 63 |
| Gangland Domain | 63 |
| Gangland (impl) | 260 |
| Gangland Mail | 25 |
| Gangland Civilians | 20 |
| Gangland Turf | 91 |
| Cops N Crooks | 76 |
| Gangland Gadgets | 113 |
| Gangland NPC Shops | 17 |
| Gangland Loot Chests | 53 |
| **Total** | **891** |

Matches WS3's own `G4-G5-fix1-report.md` per-module table exactly (`gangland-lootchest` 50→53 for the F1 Spanish
fix's 3 new `GanglandLootChestMessagesTest` cases; every other module unchanged from the previous merge round).

Module jar audit: `target/modules/gangland-lootchest-0.10.0.jar` confirmed to contain `module.yml`,
`commands.json` (482 bytes), and all 5 `lootchests/*.yml` files (`loot_chests.yml`, `tiers.yml`,
`loot_chest_settings.yml`, `lootchest_messages.yml`, `lootchest_messages_es.yml`) at their expected paths.

## 2. Smoke

Harness: `smoke.py`, `paths.repo_dir`/`paths.keystone_jar_dir` repointed to this worktree /
`wt\keystone-1.11.0\keystone-plugin\target` (→ `Keystone-1.11.1.jar`) for all 3 runs below, restored to
`gangland-0.9.2`/`keystone-1.10.0` immediately after the last one. `cut-full-regression`'s `commands` list gained
`"glw lootchest help"` (to surface the module's own commands.json help page). Three separate boot/stop cycles
were needed to cover both the legacy-warning check and its inverse plus the language check, since each requires a
different `settings.yml` state:

### (a) Run 1 — baseline (live `settings.yml` as found: still carries a legacy `Loot_Chest:` block, `Language: en`)

Command: `python smoke.py --rows cut-full-regression --deploy --keystone --restore`. **Result: PASS**,
`modules=[...,'lootchest',...] errors=0`.
- `Runtime modules: 7 loaded, 0 fault(s)`, `Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar`.
- All 5 `lootchests/*.yml` files confirmed extracted to `plugins/Gangland_Warfare/lootchests/` on disk.
- `/glw lootchest help` → shows exactly the module's 4 commands.json entries (`lootchest`, `lootchest edit`,
  `lootchest help <page>`, `lootchest remove`) under their own "Loot Chest Wand [1/1]" help page — confirms the
  module's `commands.json` merged into `InformationManager` correctly.
- `/glw reload` → `"Reload has been completed."`, zero faults.
- **The G4 legacy-block warning fires exactly as designed, both required times** (boot + reload, `Settings.init()`
  runs once per each): `"settings.yml still has a legacy 'Loot_Chest:' block — those keys moved to
  plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by
  the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See
  documentation/migration-0.10.0.md."` — matched byte-for-byte against `Settings.java:733-735`'s call site, and
  logged twice (once at `[23:22:59]` boot, once at `[23:23:08]` reload). This is the "inverse" check the brief
  asked for. Report: `smoke/reports/2026-09-22-2322-cut-full-regression.{md,log}`.

### (b) Run 2 — fresh-install simulation (`Loot_Chest:` block removed from `settings.yml`)

Backed up the live `settings.yml` first (`settings.yml.backup-by-G010-lead`), then removed lines 579-612 (the
whole `Loot_Chest:` block, confirmed `Money_Drop:` at 613 untouched and now immediately following). Re-ran the
same scenario. **Result: PASS**, `errors=0`.
- `Runtime modules: 7 loaded, 0 fault(s)` — unaffected by the block's absence, as expected.
- **Zero legacy-warning lines** anywhere in the log (`grep -c` for the warning text → 0) — the new-this-round
  deferred row 7 ("a fresh install never logs the migration warning") is fully confirmed. Report:
  `smoke/reports/2026-09-22-2323-cut-full-regression.{md,log}`.

### (c) Run 3 — `Language: es`

Restored the `Loot_Chest:` block from the backup, then flipped `Language: en` → `Language: es` (line 82). Re-ran
the same scenario. **Result: PASS**, `errors=0`.
- `Runtime modules: 7 loaded, 0 fault(s)` — the module (including the newly-added `lootchest_messages_es.yml`
  `FileHandler` registration and `GanglandLootChestMessages`'s language-branching constructor) boots with zero
  faults under `Language: es`, confirming F1's fix doesn't throw when the Spanish file is actually loaded by a
  real server, not just the unit test.
- `/glw lootchest` → **`"¡Necesitas ser un jugador para usar esto!"`** — genuinely translated (this is a
  Keystone/core-level "not a player" message, not one of the 26 moved `LootChestMessagesProvider` keys, but its
  translation confirms the whole `Settings.getLanguagePicked()`-driven i18n pipeline is live and working with the
  lootchest module loaded).
- `/glw lootchest help` → same 4 entries, in English — expected, command *descriptions* (from `commands.json`) are
  never translated, in this module or any other; not a bug.
- **What I could not verify**: the actual Spanish *content* of the 26 `LootChestMessagesProvider` strings
  themselves (the ones F1's fix specifically restored). Read `LootChestRemoveCommand.action()` and
  `LootChestWandEditCommand`'s equivalent before attempting this: both gate on `if (!(sender instanceof Player
  player)) return;` and return **silently, with no message at all**, when run from console — there is no
  console-reachable code path that displays one of these 26 strings. The unit-level proof
  (`GanglandLootChestMessagesTest`, F1's red-first test) already confirms the *lookup* logic picks the Spanish
  file correctly; this run additionally confirms the *real* Spanish file loads on a *real* server with zero
  faults — but seeing an actual translated loot-chest-specific string requires a real player. Restored the
  `Loot_Chest:` block + `Language: en` immediately after (see below). Report:
  `smoke/reports/2026-09-22-2324-cut-full-regression.{md,log}`.

**0 errors, 0 faults across all 3 runs.**

## Checklist

No new rows added — every genuinely new ask this round (the fresh-install-no-warning check and its inverse) was
fully console-verified above, and the remaining player-only pieces (place/open/take a chest, cooldown hologram
text, cracking minigame, reload/restart data-survival, armor-stand leak, and now also the 26 Spanish strings'
actual content) are the same set already covered by the previous merge round's checklist rows 45-50
(`exec/G010/WS2-manual-checklist.md`, "## WS3 merge" section). That section's intro paragraph was expanded in
place to record this round's additional console-verified facts (commands.json/help entries, both directions of
the legacy-warning check, the `Language: es` i18n-pipeline confirmation, and the explicit "26 Spanish strings need
a player" finding) rather than duplicating rows that already exist.

## Cleanup / restoration confirmed

- `settings.yml` on the test server restored to byte-identical to its pre-session state (`diff` confirmed
  identical, `Loot_Chest:` block present, `Language: en`); the temporary backup file deleted.
- `scenarios.json` `paths` restored to `gangland-0.9.2`/`keystone-1.10.0`; the `cut-full-regression` row's
  `commands` list permanently gained `"glw lootchest help"` (kept, matching every prior round's "keep the
  scenario improvement" pattern).
- Worktree (`E:\Programming\java\wt\gangland-0.10.0`) confirmed still 0 uncommitted changes throughout — no code
  edits were needed (build succeeded cleanly on the first attempt), so there was nothing to make or revert there.

## Deliverables

- `exec/G010/merge-2026-09-22b-report.md` — this file.
- `exec/G010/WS2-manual-checklist.md` — "## WS3 merge" section's intro expanded (no new rows; see "Checklist"
  above for why).
- `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` — `cut-full-regression` commands list extended;
  paths restored.
- `brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-{2322,2323,2324}-cut-full-regression.*` — the
  3 runs' reports, main checkout, untracked.

## Anything not verified

- The actual Spanish content of the 26 `LootChestMessagesProvider` strings (module boots clean under
  `Language: es` with zero faults; the strings themselves need a real player, per the finding in Run 3 above).
- Everything else from the previous merge round's "not verified" list still stands unchanged (checklist rows
  45-50): wand-give/place/open/take, cooldown hologram text, cracking-minigame shape, reload/restart data
  persistence, armor-stand-leak count.
- No commits made anywhere; no reviewers invoked; no subagents used this round (build succeeded cleanly with no
  fix needed, and the smoke/report work was straightforward enough to not warrant delegation).
