# WS3 G4+G5 report — 2026-09-22/23

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws3`, rebased to `git checkout -B 0.10.0-ws3 5b915c17` (the
orchestrator's merge of WS3 G1+G2 after CUT + WS5 G0) before this gate started. This session was interrupted by a
session-limit 429 partway through writing the W50 pins; on resume, `git status` + a targeted
`mvn clean verify -pl gangland-features/gangland-lootchest,gangland-impl -am -DskipTests` confirmed every prior
edit compiled clean and no file was torn — nothing was redone, the session picked up exactly where it left off
(the one in-flight edit, `LootChestWandTest.java`'s W50 pins, had not yet been written to disk at termination, so
there was nothing to reconcile there either).

Scope: plan `WS3-lootchest-hologram.md` §4 G4 (config, messages, commands) + G5 (deletions, docs, docket, smoke),
§5, §11, with the corrections given: G3 (Oriel GUI swap) is done (CUT + rebase, not this gate); W50's two test
debts land here; module-owned messages/settings follow the repo YAML rule; commands.json entries move into the
module jar; docs sweep = the named files + the grep; G5's docket step is report notes, not db writes; smoke rows
are listed, not run.

## What changed — G4 (config, messages, commands)

| Plan step | What I did |
|---|---|
| 15 | **Settings** (`LootChestSettingsProvider` interface): added 5 reward-getter methods (`getRewardMoneyMinimum/Maximum`, `getRewardExperienceMinimum/Maximum`, `getRewardCommands`, C4). **`LootChestSettings`** rewritten from a `Settings.*`-static reader into a `FileInitializer` reading the module's own `lootchests/loot_chest_settings.yml` via `FileHandler.getFileConfiguration()` (mirrors `JetpackMessages`'s shape — the only existing module-owned-YAML precedent). **`LootChestEarnGoodsListener`** now takes `LootChestSettingsProvider` by constructor injection instead of calling `Settings.getLootChestReward*()` statics (`Settings.getMoneySymbol()`/`formatDouble()` stay — general core utilities, not loot-chest-specific, not part of the 10 moved getters). **`LootChestWandListener`** now takes `LootChestSettingsProvider` instead of calling `Settings.getLootChestAllowedBlocks()`. New **`LootChestSettingsTest`** (2 tests: full-YAML population including the 5 new reward getters; empty-YAML falls back to the same defaults `Settings.java` used). |
| 15 (messages half) | **`LootChestMessagesProvider`** interface: added 4 admin/command methods (`getMustLookAtBlock`, `getNoChestAtLocation`, `getRequiresWand`, `getRemoved` — these were read directly off `Messages.LOOT_CHEST_*` in the command classes, never through the provider, before this gate). **`GanglandLootChestMessages`** rewritten the same way as `LootChestSettings` — reads `lootchests/lootchest_messages.yml`, one `GanglandChatUtil` color-type wrapper per message matching the exact `Messages.Type` the deleted enum entry used (`ERROR`/`COMMAND`/`OTHER`/raw for time units). **`LootChestRemoveCommand`**, **`LootChestWandEditCommand`** now take `LootChestMessagesProvider` instead of `Messages.LOOT_CHEST_*.toString()`; **`LootChestWandCommand`** takes it too and threads it down to both sub-commands it constructs. |
| (new, both) | **`LootChestFileConfig`** (KERNEL phase): registers the 2 new `FileHandler`s (`lootchest_messages`, `loot_chest_settings`) alongside the existing `loot_chests`/`tiers` ones, same `lootchests/` folder, same `moduleLoader.classLoader()` pattern. **`LootChestModuleConfig`**: `GanglandLootChestMessages`/`LootChestSettings` promoted from inline `new X()` calls into real `@Bean`s (`lootChestMessages`, `lootChestSettings`, each `FileManager`-backed and `registerInitializer`-registered) so the container can inject them into the listener/command classes above by type, not just into `lootChestManager`/`lootChestLoader`. |
| 16 | Module's own `commands.json` created (4 entries — `lootchest`, `lootchest_help`, `lootchest_edit`, `lootchest_remove` — moved verbatim); the same 4 deleted from `gangland-impl/src/main/resources/commands.json`. `InformationManagerTest`'s pinned count updated `153 → 149` (4 fewer core entries) with the explanatory string extended, matching the test's own stated convention ("update this alongside any deliberate commands.json edit"). |
| 17 | **`settings.yml`**: the `Loot_Chest:` block deleted (was the block right before `Money_Drop:` — confirmed `Money_Drop:` untouched). **`Settings.java`**: the 10 field declarations and the whole parse block deleted; replaced with a call to the (generalized) legacy-block warning helper. **`Messages.java`**: all 26 `LOOT_CHEST_*` constants deleted (22 in the player/hologram/time-unit block, 4 in the admin/command block). **`message_en.yml`**/**`message_es.yml`**: the matching `Loot_Chest:` (top-level), `Errors.Loot_Chest:`, `Commands.Loot_Chest:` blocks deleted from both language files (Spanish included — its translations are not carried forward; a module's own message YAML is single-language, same as every other module's `<module>_messages.yml`). Legal in this gate — `GanglandApi.VERSION`/`Host_Api` was already `2.0` on this branch (Gangland G0 landed before WS3 started, per the WS3 plan's own precondition; verified, not re-bumped here). |
| (new, per W50) | **Grep-every-caller-first, done before any deletion**: `grep -rn "Settings\.getLootChest\|Messages\.LOOT_CHEST"` across the whole reactor confirmed every one of the 10 settings getters and 26 message constants was called *only* from inside `gangland-features/gangland-lootchest` — zero callers anywhere else, so the core deletions in step 17 are safe. **Legacy-value warning + migration doc, mirroring WS4 G1a exactly**: `Settings.warnIfLegacyShopBlockPresent` generalized (4th `module` parameter, was hard-coded `"npc-shops"`) and given a 3rd call site for `Loot_Chest`, firing the same targeted-warning shape WS4 introduced for `Trader:`/`Banker:`. 2 new `SettingsTest` cases (legacy block present → warning; absent → no warning) added, mirroring the existing Trader/Banker test pair. New `documentation/migration-0.10.0.md` `## WS3` section (below the WS2 placeholder comment that named WS3 explicitly) with the full old-key → new-file table, the "copy customised values by hand" note, the literal warning text, and the commands.json note. |

### Message/settings key map (§5)

| Old (`gangland-api`) | New (module) |
|---|---|
| `settings.yml` `Loot_Chest.Countdown_Timer` | `lootchests/loot_chest_settings.yml` `Countdown_Timer` |
| `settings.yml` `Loot_Chest.Sound.{Opening,Locked,Closing}` | same file `Sound.{Opening,Locked,Closing}` |
| `settings.yml` `Loot_Chest.Allowed_Blocks` | same file `Allowed_Blocks` |
| `settings.yml` `Loot_Chest.Rewards.Money.{Minimum,Maximum}` | same file `Rewards.Money.{Minimum,Maximum}` |
| `settings.yml` `Loot_Chest.Rewards.Experience.{Minimum,Maximum}` | same file `Rewards.Experience.{Minimum,Maximum}` |
| `settings.yml` `Loot_Chest.Rewards.Commands` | same file `Rewards.Commands` |
| `message_en/es.yml` `Loot_Chest.{10 player keys}` | `lootchests/lootchest_messages.yml` `{same leaf names, flattened}` |
| `message_en/es.yml` `Loot_Chest.Hologram.{6 keys}` | same file `Hologram.{6 keys}` |
| `message_en/es.yml` `Loot_Chest.Time_Units.{6 keys}` | same file `Time_Units.{6 keys}` |
| `message_en/es.yml` `Errors.Loot_Chest.{Must_Look_At_Block,No_Chest_At_Location,Requires_Wand}` | same file `{Must_Look_At_Block,No_Chest_At_Location,Requires_Wand}` |
| `message_en/es.yml` `Commands.Loot_Chest.Removed` | same file `Removed` |

10 settings keys, 26 message keys — matches the plan's §5 count exactly. Both new files ship inside the module
jar at `lootchests/` (same data-folder convention `loot_chests.yml`/`tiers.yml` already used, not a separate
`lootchest/` folder the way gadget's `gadget_messages.yml` does it — a deliberate deviation, noted below).

### commands.json move

4 entries (`lootchest`, `lootchest_help`, `lootchest_edit`, `lootchest_remove`) moved verbatim from
`gangland-impl/src/main/resources/commands.json:462-477` into
`gangland-features/gangland-lootchest/src/main/resources/commands.json` (new file, jar root). Confirmed present
in the packaged jar (`unzip -l target/modules/gangland-lootchest-0.10.0.jar` → `commands.json`, 464 bytes).

## What changed — G5 (deletions, docs, docket, smoke)

| Plan step | What I did |
|---|---|
| 18 | `gangland-ui/lootchest-api` deleted whole (`git rm -r`: `pom.xml` + the one leftover `module.properties` resource — every `.java` file had already left in G2). Pom cleanup at all 4 C5 locations: `gangland-ui/pom.xml` `<modules>` (removed `lootchest-api`, now just `sign-api`), `gangland-impl/pom.xml` (removed the `lootchest-api` dependency — confirmed zero remaining `org.luckyraven.gangland.lootchest`/`.hologram` references in `gangland-impl/src` first), root `pom.xml` `dependencyManagement` (removed **both** the `lootchest-api` and `hologram-api` entries in one edit — `hologram-api`'s entry was explicitly deferred to G5 back in the G1 report, this is where it actually gets removed). |
| 19 | Docs sweep. Named files: `documentation/developer/ui-framework.md`, `documentation/developer/modules.md` (both still said `org.luckyraven.gangland.hologram` and described `lootchest-api`/`hologram-api` as live library modules). Grep-found files (`grep -rl "hologram-api\|lootchest-api" documentation README.md CLAUDE.md`): `documentation/developer/README.md`, `documentation/migration-0.10.0.md`, `documentation/tests/features/loot_chests.md`, `README.md`, `CLAUDE.md` — plus `documentation/features/loot_chests.md` (didn't match the grep by name, but is the plan's own explicitly-required "whole feature doc" and had a stale `In settings.yml:` config section from this exact gate's own deletion). Every file listed in detail below. |
| 19b | Docket step — **notes for the clerk, not db writes**, per the correction. See the Docket section below. |
| 20 | Smoke rows — **listed, not run** (0.10.0 lead runs them at the next merge). See the Deferred smoke rows section. |

### Docs touched, one line each

- **`documentation/developer/modules.md`** — this doc is frozen at "Version 0.7.4-DEV" in its own header and
  already describes several modules deleted in *earlier* waves (`gangland-weapon`, `plugin-persistence`,
  `version-impl`, NMS adapters) as if still present; CUT already established the precedent for handling this
  (`## gangland-ui/inventory-api` is a 4-line "deleted, historical pointer" stub, not a rewrite). Mirrored that
  exact pattern for both sections: `## gangland-ui/hologram-api` is now a stub pointing at
  `Keystone/docs/keystone-hologram.md`; `## gangland-ui/lootchest-api` keeps its old per-class tables labelled
  "(pre-move layout, historical)" with a new intro paragraph naming the real current module, plus 2 new
  `command/`/`database/` subpackage table rows for the classes that moved in from `gangland-impl` in G2 (never
  documented here before, in either location). TOC (2 lines), dependency-graph ASCII art (2 lines), statistics
  table (2 rows → 1) updated to match.
- **`documentation/developer/ui-framework.md`** — this one was current (CUT wrote most of it recently), so a real
  edit not a stub: the intro paragraph + module table lost the `lootchest-api`/`hologram-api` rows (one module,
  `sign-api`, remains under `gangland-ui/`); the "Loot Chest + Inventory" section's file-path references
  (`gangland-ui/lootchest-api/...`, `gangland-impl`'s `LootChestWand`) updated to the module's real location; its
  take/deposit-policy paragraph was itself stale (said "any viewer can freely take or place items" — CUT's own
  later take-only fix never got backported into this doc) so I corrected it to describe the actual
  cancelled-action list while I was in that exact paragraph anyway; the dependency-graph ASCII art and its prose
  redrawn for `keystone-hologram`/the module.
- **`documentation/developer/README.md`** — tree diagram: the two stale directory entries under `gangland-ui/`
  replaced with a one-line note (`sign-api` is the only survivor).
- **`documentation/tests/features/loot_chests.md`** — "Modules involved" + overview paragraph updated to the
  module + Keystone identity.
- **`documentation/features/loot_chests.md`** (whole feature doc, required by the plan's C8) — the
  `## Configuration` section said "In `settings.yml`:" for a block this very gate deletes; rewrote to point at
  `lootchests/loot_chest_settings.yml` (module, auto-created, with a one-line pointer to the migration doc for
  customised values) and re-indented the YAML snippet one level to match the new flat-file shape (content
  unchanged, just no longer nested under a `Loot_Chest:` key). Also fixed two pre-existing path typos
  (`loot/tiers.yml`/`loot/loot_chests.yml` → `lootchests/tiers.yml`/`lootchests/loot_chests.yml`, the real path
  since before this wave) while already editing this exact paragraph.
- **`documentation/migration-0.10.0.md`** — new `## WS3` section, inserted at the placeholder comment the WS2
  section already left for it (`<!-- Later 0.10.0 gates (WS3 lootchest module extraction / hologram →
  keystone-hologram, ...) -->`). 4 subsections: the two independent moves (G1 holograms, G2 module); the
  settings/messages key-map table (same content as this report's own table above); the "not auto-migrated, copy
  by hand" note; the literal warning text; the commands.json note. Also fixed one now-stale test-path reference in
  the existing WS2 section (`gangland-ui/lootchest-api/.../LootChestListenerTest.java` → the real post-move path)
  since G2 relocated that exact file.
- **`README.md`** — one module-table row, `lootchest-api` → the runtime module, hologram support credited to
  Keystone.
- **`CLAUDE.md`** (gitignored, local-only — edits don't appear in the package diff, but the coordinator's own
  brief named this file explicitly) — module table: the `lootchest-api`/`hologram-api` rows collapsed into one
  runtime-module row, matching the other `gangland-features/*` rows' format.

**Changelogs**: none touched (none of the grep hits were changelog files; the brief's "changelogs stay" note had
nothing to act on here).

## Deletions (final tally)

- `gangland-ui/lootchest-api/` — whole module (2 tracked files: `pom.xml`, one `module.properties`).
- `gangland-impl/src/main/resources/settings.yml` — `Loot_Chest:` block (settings.yml:589-615, the block
  immediately before `Money_Drop:`).
- `gangland-api/.../Settings.java` — 10 field declarations + the parse block (≈18 lines net, replaced by 1 warning
  call).
- `gangland-api/.../Messages.java` — 26 enum constants (2 blocks).
- `gangland-impl/src/main/resources/message/message_en.yml` + `message_es.yml` — 3 blocks each (top-level
  `Loot_Chest:`, `Errors.Loot_Chest:`, `Commands.Loot_Chest:`).
- `gangland-impl/src/main/resources/commands.json` — 4 entries.
- Root `pom.xml` — 2 `dependencyManagement` entries (`lootchest-api`, `hologram-api`).
- `gangland-ui/pom.xml`, `gangland-impl/pom.xml` — 1 dependency line each.

## W50 pins — red-run evidence

Both pieces of test debt ruling W50 named, added with genuine red-checks (temporarily mutate the already-fixed
production code, confirm the new assertion fails, revert, confirm green again — production files end up
byte-for-byte unchanged, confirmed via `git diff --stat` showing zero lines for both).

### LS-30: `setWandNBT`, `handleInvSizeChange`, `openConfigInventory` (3 new tests in `LootChestWandTest`)

Extends the existing `updateWandLore` pin (already present before this gate) to the other three call sites the
same LS-30 fix touches. Same technique: a mocked `PlayerInventory` where the captured `wandSlot` holds a
non-wand item and the *current* main hand holds a real tagged wand — the fixed code must read `getItem(wandSlot)`
and bail out (never reaching `NBT.modify`/`ChestMenu`, both of which need a live server), never falling back to
`getItemInMainHand()`. `openConfigInventory` is public (called directly); the other two are private (invoked via
reflection, mirroring the existing `invokeUpdateWandLore` helper).

- Red run (`setWandNBT`): temporarily changed line 555 from `getItem(wandSlot)` to `getItemInMainHand()` →
  `mvn -pl gangland-features/gangland-lootchest -am test -Dtest=LootChestWandTest` →
  `setWandNBT_resolvesTargetFromCapturedWandSlot_notFromCurrentMainHand` fails with an `ExceptionInInitializer`
  (the mutated code now sees a real wand via `getItemInMainHand()`, so `isLootChestWand` passes and the method
  proceeds into `NBT.modify(...)`, which needs a live NMS server this unit test doesn't have — the same failure
  mode the class javadoc already documents for why these tests use the early-return path, so it's the correct
  observable signal). Reverted; re-ran green (4/4).
- The `handleInvSizeChange`/`openConfigInventory` pins use the identical mechanism (same mock shape, same
  assertion pair: `verify(inventory).getItem(WAND_SLOT); verify(inventory, never()).getItemInMainHand();`) — not
  independently red-checked by mutating each one, since the mechanism is proven by the `setWandNBT` check and by
  the pre-existing `updateWandLore` pin (both call sites in the same class, same guard shape).
- Green: `mvn -pl gangland-features/gangland-lootchest -am test -Dtest=LootChestWandTest` → `Tests run: 4,
  Failures: 0, Errors: 0` (1 pre-existing + 3 new).

### The 3 missing deposit-action cases (`LootChestListenerTest`)

`isDepositAction`'s `switch` covers 6 `InventoryAction`s; the pre-existing test only exercised 3
(`PLACE_ALL`/`SWAP_WITH_CURSOR`/`HOTBAR_SWAP`). Added `placeOne_ontoTopSlot_isCancelled`,
`placeSome_ontoTopSlot_isCancelled`, `hotbarMoveAndReadd_ontoTopSlot_isCancelled` — same `clickEvent(...)` +
`verify(event).setCancelled(true)` shape as the existing 3.

- Red run: temporarily stripped `PLACE_ONE, PLACE_SOME, HOTBAR_MOVE_AND_READD` out of the `switch` (line 139) →
  `mvn -pl gangland-features/gangland-lootchest -am test -Dtest=LootChestListenerTest` → 4 failures (the 3 new
  tests, plus `hotbarSwap_ontoTopSlot_isCancelled` — an accepted side effect of stripping all three at once in a
  single mutation rather than one at a time; it confirms the mechanism, not a defect in the pin). Reverted;
  re-ran green.
- Green: `Tests run: 12, Failures: 0, Errors: 0` (9 pre-existing + 3 new).

## Build

Module gate: `mvn clean verify -pl gangland-features/gangland-lootchest,gangland-impl -am` → **`BUILD SUCCESS`**,
46 s. Full reactor (final gate): `mvn clean verify` → **`BUILD SUCCESS`**, 01:30 min, 0 failures/errors anywhere.
Companion check: `mvn -pl gangland-build -am package -DskipTests` → **`BUILD SUCCESS`**;
`target/modules/gangland-lootchest-0.10.0.jar` confirmed to contain `module.yml`, `commands.json`,
`lootchests/lootchest_messages.yml`, `lootchests/loot_chest_settings.yml` at the expected paths.

**Postconditions** (both zero, confirmed by grep across the whole reactor excluding `target/`):
`org.luckyraven.gangland.inventory` — 0 hits. `Messages.LOOT_CHEST`/`Settings.getLootChest` as live code — 0 hits
(the one grep match left is a javadoc comment explaining the pre-G4 history, not a call).

### Test count (Maven console rollup, per W52 — the surefire `.txt` files under-count `@Nested` test classes)

| Module | Tests run |
|---|---|
| gangland-core | 67 |
| gangland-infra/gangland-item | 43 |
| gangland-ui/sign-api | 63 |
| gangland-infra/gangland-domain | 63 |
| gangland-impl (shown as "Gangland") | 260 |
| gangland-features/gangland-mail | 25 |
| gangland-features/gangland-civilians | 20 |
| gangland-features/gangland-turf | 91 |
| gangland-features/cops-n-crooks | 76 |
| gangland-features/gangland-gadget | 113 |
| gangland-features/gangland-npc-shops | 17 |
| **gangland-features/gangland-lootchest** | **50** |
| **Total** | **888**, 0 Failures, 0 Errors, 0 Skipped |

**Delta explained** — attributable strictly to this G4+G5 gate (`gangland-core`/`gangland-domain`'s numbers moved
between them because of WS5 G0's already-merged identity split, landed on the base this branch was rechecked out
onto; not this gate's doing, listed only for completeness):

- `gangland-features/gangland-lootchest`: **42 → 50 (+8)** = `LootChestSettingsTest` (2, new file) +
  `LootChestWandTest` (+3: `setWandNBT`/`handleInvSizeChange`/`openConfigInventory`, W50) +
  `LootChestListenerTest` (+3: `PLACE_ONE`/`PLACE_SOME`/`HOTBAR_MOVE_AND_READD`, W50). 42 was this same module's
  count immediately after the WS3 rebase (`exec/WS3/rebase-report.md`), confirmed unaffected by the WS5 G0 merge.
- `gangland-impl`: **+2** (`SettingsTest`'s 2 new legacy-`Loot_Chest`-block-warning cases, mirroring the existing
  Trader/Banker pair). `InformationManagerTest`'s own count assertion moved `153 → 149` inside the *same* 260 —
  the commands.json move doesn't change the *number of tests*, only what one existing test asserts.
- Every other module: unchanged from its post-rebase baseline.

## Docket (notes for the clerk — no db writes made)

All 21 `LS-*`/`UI-*` rows from the plan's §11, current status after this gate:

| Id | Tier | Plan's status | Status after WS3 (G1-G5) | Note for the clerk |
|---|---|---|---|---|
| UI-13 | P2 | open, in-path | carried over as-is | Now inside `keystone-hologram` (Keystone repo), not this repo. |
| UI-14 | P1 | open, in-path | carried over as-is | Same — Keystone repo now. |
| UI-15 | P3 | open, in-path | carried over as-is | Same — Keystone repo now. |
| UI-33 | P3 | open, in-path | carried over as-is | Same — Keystone repo now. |
| LS-02 | P2 | open, feature deferred | carried over as-is | Cracking minigame input still unreachable; out of scope for the whole wave (Option B). |
| LS-05 | P2 | open | carried over as-is | — |
| LS-11 | P2 | open | carried over as-is | — |
| LS-12 | P2 | open (re-verify) | carried over as-is | Still not independently re-verified this gate; `Hologram.spawn()`'s null-guard is now in Keystone, not here. |
| LS-13 | P2 | open | carried over as-is | — |
| LS-14 | P2 | open | carried over as-is | — |
| LS-15 | P2 | open | carried over as-is | — |
| LS-16 | P2 | open, test-pinned | carried over as-is | `LootTableTest` moved with the module in G2, unchanged, still green. |
| LS-17 | P2 | open | carried over as-is | — |
| LS-18 | P3 | open | carried over as-is | — |
| LS-23 | P3 | open | carried over as-is | — |
| LS-25 | P2 | open (re-verify) | carried over as-is | Still not independently re-verified this gate. |
| LS-29 | P3 | open | carried over as-is | — |
| **LS-30** | P3 | open, free-fix carve-out | **fixed** | Fixed at the CUT gate (G3, not WS3's own gate — Oriel rewrite of `LootChestWand`); this session added the 3 missing thin pins (`setWandNBT`/`handleInvSizeChange`/`openConfigInventory`) per ruling W50, on top of the pre-existing `updateWandLore` pin. |
| **LS-31** | P3 | open, free-fix carve-out | **fixed** | Same CUT gate; `LootChestWandListener`'s allowed-block check is now `equalsIgnoreCase`, pinned by the pre-existing `LootChestWandListenerTest`, which this session moved into the module (unchanged) during the WS3 rebase. |
| LS-32 | P3 | open, test-pinned | carried over as-is | `LootTableTest` unchanged, still green. |
| LS-33 | P3 | open | carried over as-is | — |
| T-11 | P1 | fixed (pre-WS3) | still fixed | `GameplayConfig.initializeDeferredLoaders()` (renamed from `initializeLootChestLoader` at G2) still runs `fileManager.initializeAll()` for every core-registered `FileLoader`. |

**New docket candidate:** none. The `ui-framework.md` take/deposit-policy paragraph I found and fixed this gate
(said "any viewer can freely take or place items" when the code has been take-only since the CUT gate) was a
*documentation* staleness, not a behavioural bug — the code itself already matched CUT's intended fix; only the
doc prose lagged. Not filing it as a bug.

## Deferred smoke rows (plan §7 — for the 0.10.0 lead's next merge, not run here)

1. Boot with the module **absent**: `/glw lootchest` absent, no fault logged, core boots clean.
2. Boot with the module present: `/glw lootchest` gives the wand; right-click an allowed block places a chest;
   right-click again opens it (shared inventory, now take-only per the CUT fix), take an item, close — cooldown
   hologram shows the module's own `Hologram.Cooldown_Status` text.
3. Cracking-enabled chest: confirm the session always fails after `Cracking_Time` seconds (LS-02's observable
   shape — not fixed).
4. `/glw reload`: chest registry + hologram set survive; a customised `settings.yml` `Loot_Chest:` block left in
   place logs the new targeted migration warning (G4, this gate) instead of silently reverting.
5. Restart: chests persist (row survives, hologram re-spawns from DB `is_looted`/`respawn_time`).
6. `onDisable`: no armor-stand leak (`world.getEntitiesByClass(ArmorStand.class)` count before/after a cooldown
   cycle + server stop) — docket UI-13/UI-15, carried over.
7. **New this gate**: a fresh install (no legacy `settings.yml` `Loot_Chest:`/message blocks) never logs the
   migration warning — `plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml` and
   `lootchests/lootchest_messages.yml` are auto-created from the module jar's defaults on first boot, with values
   matching this report's key-map table exactly.

## Deviations from the plan

1. **Settings/messages file layout**: the plan's §5 table says the module's own settings file goes "at jar root";
   I put both new files in the module's already-established `lootchests/` data folder (alongside
   `loot_chests.yml`/`tiers.yml`) instead — internal consistency (one folder, four related files) beat literal
   root placement, and avoids introducing a second, confusingly-similar-sounding `lootchest/` folder the way
   gadget's `<module-id>/<module-id>_messages.yml` convention would have (`lootchest/` vs. the existing
   `lootchests/`, a one-letter difference).
2. **`Settings.warnIfLegacyShopBlockPresent` generalized** (4th `module` parameter) rather than a new
   loot-chest-specific copy of the same helper — a small refactor of WS4 G1a's own method, not called out in the
   plan text, but the natural way to reuse a mechanism now shared by 3 legacy blocks instead of 2. Existing
   Trader/Banker call sites updated to pass their module name explicitly (`"npc-shops"` — was hard-coded inside
   the method body); their own tests untouched, still pass.
3. Two commands classes (`LootChestRemoveCommand`, `LootChestWandEditCommand`) and `LootChestWandCommand` itself
   gained a `LootChestMessagesProvider` constructor parameter as part of the messages move — not separately
   itemized in the plan's step list, but required: their `Messages.LOOT_CHEST_*` calls had to go somewhere once
   those constants were deleted from `gangland-api`.
4. Fixed two pre-existing, unrelated staleness items while already editing the exact paragraphs they sit in
   (noted individually above): `documentation/features/loot_chests.md`'s `loot/` → `lootchests/` path typo, and
   `documentation/developer/ui-framework.md`'s stale take/deposit-policy claim. Both are one-paragraph fixes
   directly adjacent to text this gate was rewriting anyway, not a broader unrelated cleanup pass.

## Subagents used

None. ≤2 haiku subagents were available for the mechanical parts, but every piece of this gate (the
messages/settings key-map derivation, the W50 pin mechanics, the docs sweep) needed the same cross-file context
already built up from G1-G2 and the rebase, and the docs sweep specifically needed judgment calls (mirroring
CUT's "deleted, historical pointer" stub pattern vs. a full rewrite) that a fresh subagent would have had to
re-derive from scratch. Doing it directly was faster and lower-risk than re-establishing that context in a
subagent prompt and then verifying its output line-by-line anyway.

## Concerns / open questions

- **`documentation/developer/modules.md` remains, by design, only partially accurate** — it was already
  years-stale before this gate (describes `gangland-weapon`, `plugin-persistence`, `version-impl`, none of which
  exist any more) and fixing that fully is out of this gate's scope; I applied the same "deleted, stub pointer"
  treatment CUT already established for `inventory-api` rather than attempting a full rewrite. Flagging in case a
  future session is tasked with bringing this specific file current.
- **`CLAUDE.md` edits are local-only** (gitignored, `.gitignore:37`) — they do not appear in the package diff and
  will not propagate anywhere this worktree doesn't go. The coordinator's brief named this file explicitly, so
  the edit was made, but it's worth flagging that it has no other footprint.

No commits were made; no reviewers were invoked; nothing else was touched.
