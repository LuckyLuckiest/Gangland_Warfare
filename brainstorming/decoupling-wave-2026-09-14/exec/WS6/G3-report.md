# WS6 G3 report — module-owned Messages/Settings migration mechanism, civilians worked example

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws6`, branch `0.10.0-ws6`, based on `837966c3` (WS3 G4+G5).
Scope: `plans/WS6-api.md` §4 G3, steps 11-13 only, per the orchestrator's dispatch notes (G1/G2/G4 are separate
landings, not covered here). No commits made; worktree left uncommitted per LEAD-RULES.md.

## What changed

| Plan step | What I did |
|---|---|
| 11 | Built the worked example on **`gangland-civilians`**: a new shared helper `LocalizedModuleYaml` in `gangland-api` (generalised from WS3 G4 fix round 1's `GanglandLootChestMessages` language-fallback logic, W53/F1), a new `CivilianMessages` holder in the module built on it, reading `npc/civilian_messages.yml`/`npc/civilian_messages_es.yml` through the existing `CiviliansYamlConfig` `FileHandler` pipeline (module classloader, `npc/` data-folder convention already used by `npc/civilians.yml`). Both languages shipped (no translation dropped). Deleted the 12 `CIVILIAN_*` constants from `Messages.java` and their blocks from `message_en.yml`/`message_es.yml` after repointing every caller (grepped the whole reactor before and after). Added a legacy-block warning via the generalised `Settings.warnIfLegacyShopBlockPresent` helper. Added a WS6 section to `documentation/migration-0.10.0.md`. |
| — (reusability ask) | `LocalizedModuleYaml` is the smallest shared piece, landed in `gangland-api`'s `file.configuration` package (additive only, no `GanglandApi.VERSION` bump). `CivilianMessages` is its only consumer this gate — `GanglandLootChestMessages`/`JetpackMessages` were **not** refactored onto it (out of scope per the dispatch); see "Follow-up" below. |
| 12 | Wrote the per-module migration table to `exec/WS6/messages-settings-migration-table.md` — re-verified every prefix count against the actual worktree (all match the plan's 2026-09-14 figures exactly), added the T-47 docket row, and a docket candidate noticed while building it (a stray core `BANKER_*` caller). |
| 13 | Static-vs-instance recommendation below. |

## The `LocalizedModuleYaml` helper's api

`gangland-api/src/main/java/org/luckyraven/gangland/file/configuration/LocalizedModuleYaml.java` (new, abstract
class, package-visible alongside `Settings`/`Messages`):

- **Constructor**: `protected LocalizedModuleYaml(FileManager fileManager, String baseName)` — resolves
  `<baseName>_es.yml` over `<baseName>.yml` when `Settings.getLanguagePicked()` is `"es"` **and** the localized
  file is registered (via `fileManager.checkFileLoaded`/`getFile`, `IOException` → fall through), otherwise (or
  when missing) falls back to `<baseName>.yml`. Throws `PluginException` only if neither file is registered at
  all. `"es"` is hard-coded, not a generic `lang` parameter — it is the only second language anywhere in this
  codebase (mirrors `message_en.yml`/`message_es.yml`); parameterize if a third language is ever added.
- **`FileInitializer` contract**: `getFileHandler()` (final) returns the resolved handler; `initialize()` is a
  no-op by default (flat strings read lazily per call) — overridable if a future subclass needs to pre-parse.
- **Protected formatting helpers** a subclass calls from its own accessor methods: `raw(key, fallback)`,
  `color(key, fallback)`, `command(key, fallback)`, `error(key, fallback)`, `information(key, fallback)`,
  `prefix(key, fallback)` — each wraps `raw()` through the matching `GanglandChatUtil` method
  (`color`/`commandMessage`/`errorMessage`/`informationMessage`/`prefixMessage`), i.e. exactly what
  `Messages.getValue(Type, data)`'s `switch` does per `Type`, so a module-owned string renders identically to
  what the deleted `Messages` constant produced.

`CivilianMessages extends LocalizedModuleYaml` (`gangland-features/gangland-civilians/.../civilians/message/`):
one constructor (`FileManager` only, passes `"civilian_messages"` as the base name) + 12 accessor methods, one
per deleted constant — 11 call `command(...)` (matching the deleted constants' `Type.COMMAND`), 1
(`spawnerListHeader()`) calls `prefix(...)` (matching `Type.PREFIX`).

## Caller map (12 constants → 10 files repointed)

Every caller was a `SubArgument`/`Command` class constructed directly with `new` (not `@CommandHandler`
DI-injected for the leaves — only the two parents are), so `CivilianMessages` was threaded through the
constructor chain from the DI-injected root down:

```
CivilianCommand (@CommandHandler, now takes CivilianMessages)
├─ CivilianListCommand           — listEmpty()
├─ CivilianGroupsCommand         — groupsEmpty()
├─ CivilianSpawnCommand          — spawnFailed(type), spawned(type)
├─ CivilianSpawnGroupCommand     — groupUnknown(group), groupSpawned(group)
└─ CivilianSpawnerCommand
   ├─ CivilianSpawnerSetCommand      — typeUnknown(type), spawnerTypeSet(type)
   ├─ CivilianSpawnerSetGroupCommand — groupUnknown(group), spawnerGroupSet(group)
   ├─ CivilianSpawnerRemoveCommand   — spawnerRemoved(id)
   ├─ CivilianSpawnerListCommand     — spawnerListHeader()
   └─ CivilianSpawnerTeleportCommand — spawnerTeleported(id)
```

(`CivilianSpawnerInfoCommand` used no `CIVILIAN_*` constant — untouched.) Verified with a whole-reactor grep for
`CIVILIAN_` before and after: 12 live call sites before, 0 after (only 2 explanatory comments remain).

Bean wiring: `CiviliansModuleConfig` gained a `@Bean civilianMessages(FileManager)` (constructs
`CivilianMessages`, calls `fileManager.registerInitializer(...)`, returns it) in a new "Messages (WS6 G3 worked
example)" section, ahead of the existing "Civilians config + entity marks" section.
`CiviliansYamlConfig.civiliansFiles(...)` gained the two new `FileHandler` registrations (`civilian_messages`,
`civilian_messages_es`, folder `npc`, `.yml`, module classloader) alongside the existing `civilians` one.

## Key map (12 constants)

| Old `Messages` constant | Old path | New key (both `npc/civilian_messages*.yml`) | Type |
|---|---|---|---|
| `CIVILIAN_LIST_EMPTY` | `Commands.Civilian.List_Empty` | `List_Empty` | COMMAND |
| `CIVILIAN_GROUPS_EMPTY` | `Commands.Civilian.Groups_Empty` | `Groups_Empty` | COMMAND |
| `CIVILIAN_SPAWNED` | `Commands.Civilian.Spawned` | `Spawned` | COMMAND |
| `CIVILIAN_GROUP_SPAWNED` | `Commands.Civilian.Group_Spawned` | `Group_Spawned` | COMMAND |
| `CIVILIAN_GROUP_UNKNOWN` | `Commands.Civilian.Group_Unknown` | `Group_Unknown` | COMMAND |
| `CIVILIAN_TYPE_UNKNOWN` | `Commands.Civilian.Type_Unknown` | `Type_Unknown` | COMMAND |
| `CIVILIAN_SPAWNER_REMOVED` | `Commands.Civilian.Spawner_Removed` | `Spawner_Removed` | COMMAND |
| `CIVILIAN_SPAWNER_TELEPORTED` | `Commands.Civilian.Spawner_Teleported` | `Spawner_Teleported` | COMMAND |
| `CIVILIAN_SPAWN_FAILED` | `Commands.Civilian.Spawn_Failed` | `Spawn_Failed` | COMMAND |
| `CIVILIAN_SPAWNER_TYPE_SET` | `Commands.Civilian.Spawner_Type_Set` | `Spawner_Type_Set` | COMMAND |
| `CIVILIAN_SPAWNER_GROUP_SET` | `Commands.Civilian.Spawner_Group_Set` | `Spawner_Group_Set` | COMMAND |
| `CIVILIAN_SPAWNER_LIST_HEADER` | `Civilian.Spawner_List_Header` (top-level) | `Spawner_List_Header` | PREFIX |

Both `message_en.yml` and `message_es.yml` had their matching blocks deleted (`Commands.Civilian:` sub-block +
top-level `Civilian:` block, in both files) — the Spanish strings were **copied**, not dropped, into
`npc/civilian_messages_es.yml` (same-value translations, verified against the pre-deletion `message_es.yml`
text).

## The legacy-block warning

`Settings.warnIfLegacyShopBlockPresent` (WS4 G1a → generalised in WS3 G4 for a 4th `module` param → generalised
again here for a 5th `sourceFile` param, since a message-YAML block isn't `settings.yml`) is now package-visible
(was `private`) and called from `Messages.init(MessageProvider)` — same cadence as the settings-side call sites
(once at boot, once per `/glw reload`): `provider.getString("Commands.Civilian.List_Empty") != null` is the
presence probe (mirrors the settings-side `section(root, "X", report) != null` pattern, translated to
`MessageProvider`'s leaf-only API). Fires:

```
[Gangland.Settings] message_<lang>.yml still has a legacy 'Civilian:' block — those keys moved to
plugins/Gangland_Warfare/npc/civilian_messages.yml (extracted by the civilians module); customised values are
NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
```

This closes the specific gap the WS3 section of `documentation/migration-0.10.0.md` called out ("no equivalent
warning" for a leftover message-file block, unlike `settings.yml`) — for the civilians case only; it is a
per-block, hand-wired probe, not a generic "any deleted Messages block" detector.

## Tests, with reds

| Test | File | Asserts | Red evidence |
|---|---|---|---|
| `languageEs_resolvesSpanishFile`, `languageOther_fallsBackToEnglish`, `languageEs_missingSpanishFile_fallsBackToEnglish` | `CivilianMessagesTest` (new, `gangland-civilians`) | The es-pick/fallback behaviour, mirroring `GanglandLootChestMessagesTest` | **Genuine red**: temporarily changed `LocalizedModuleYaml.resolveFileHandler`'s Spanish branch to `if (false && "es"...)`, reran — `languageEs_resolvesSpanishFile` failed (`expected: <marcador-es> but was: <marker-en>`, `Tests run: 3, Failures: 1`); reverted, confirmed byte-identical, reran green (3/3) |
| `init_legacyCivilianMessageBlock_logsTargetedMigrationWarning`, `init_noLegacyCivilianMessageBlock_logsNoMigrationWarning` | `MessagesTest` (existing file, `gangland-impl`, +2) | The legacy-block warning fires/doesn't fire | **Genuine red**: temporarily deleted the `Settings.warnIfLegacyShopBlockPresent(...)` call from `Messages.init(...)`, reran — the positive-case test failed (`expected a targeted migration warning...; got: []`, `Tests run: 12, Failures: 1`); reverted, reran green (12/12) |
| `SettingsTest`'s existing 6 legacy-block tests (Trader/Banker/Loot_Chest, 2 each) | `SettingsTest` (existing, unchanged) | The 5-param `warnIfLegacyShopBlockPresent` generalisation didn't break the 3 existing call sites | Not independently red-checked this gate (no new assertion added) — ran green before and after the signature change (10/10 both times), confirming the mechanical param-add didn't regress them |

The new `LocalizedModuleYaml` base class itself has no dedicated test file — its logic is exercised end-to-end
through `CivilianMessagesTest` (its only consumer this gate), consistent with W48's "red-by-non-existence
accepted for the new helper's own tests" carve-out; a genuine red was still produced for the *behaviour* (the
es-pick) rather than relying on non-existence alone.

## Build

Targeted: `mvn clean verify -pl gangland-features/gangland-civilians,gangland-impl -am` → **BUILD SUCCESS**
(gangland-impl 262 tests, gangland-civilians 23 tests, both 0 failures/errors/skipped).

Full reactor (one build, never `install`): `mvn clean verify` → **BUILD SUCCESS**, ~60s, 17/17 modules green.

**Test count (Maven console rollup, per W52 — not surefire `.txt` files):**

| Module | Tests run |
|---|---|
| gangland-core | 67 |
| gangland-infra/gangland-item | 43 |
| gangland-ui/sign-api | 63 |
| gangland-infra/gangland-domain | 63 |
| gangland-impl (shown as "Gangland") | 262 |
| gangland-features/gangland-mail | 25 |
| **gangland-features/gangland-civilians** | **23** |
| gangland-features/gangland-turf | 91 |
| gangland-features/cops-n-crooks | 76 |
| gangland-features/gangland-gadget | 113 |
| gangland-features/gangland-npc-shops | 17 |
| gangland-features/gangland-lootchest | 53 |
| **Total** | **896**, 0 Failures, 0 Errors, 0 Skipped |

**Delta vs. WS3 G4-G5-fix1's 891**: **+5** = gangland-impl **260→262** (+2, `MessagesTest`'s 2 new legacy-warning
tests) + gangland-civilians **20→23** (+3, `CivilianMessagesTest`'s 3 new tests). Every other module's count is
byte-for-byte unchanged from the WS3 baseline.

## Deferred smoke rows (not run this gate — no smoke, no `scenarios.json`, per the dispatch)

1. Boot with `gangland-civilians` present, English server: `/glw civilian list` (empty) renders
   `civilian_messages.yml`'s `List_Empty` text through the `Messages.COMMAND_PREFIX`-prefixed path, byte-identical
   to the pre-gate `Messages.CIVILIAN_LIST_EMPTY` output.
2. Same boot, `settings.yml` `Language: es`: the same command renders `civilian_messages_es.yml`'s Spanish text.
3. A server upgrading with a leftover `message_en.yml`/`message_es.yml` `Commands.Civilian:` block: boot log shows
   the new targeted warning naming `npc/civilian_messages.yml`, not just the generic `unknown key` line.
4. `/glw reload` with civilians loaded: the warning (if the legacy block is still present) fires again, matching
   the existing Trader/Banker/Loot_Chest cadence.
5. Fresh install (no legacy block anywhere): no warning at all; `npc/civilian_messages.yml`/`_es.yml` are
   auto-created from the module jar's bundled defaults on first boot, matching this report's key-map table
   exactly.

## Docket

Plan §11 (`WS6-api.md`) names only `KS-MO-05`, `KS-MO-06`, `KS-MO-07` for this workstream — all Keystone-side
module-loader items, unrelated to G3's message-migration mechanism. **None touched by this gate**; still open,
unfixed, exactly as the plan describes ("not fixed by this plan" for KS-MO-07; "unaffected" for KS-MO-05/06).

**Docket candidate** (not filed — out of this gate's G3-only scope, recorded in
`exec/WS6/messages-settings-migration-table.md` for whoever picks up the `BANKER` row):
`gangland-impl/.../command/sub/bank/BankDepositCommand.java` reads a `Messages.BANKER_*` constant directly from
core, while every other `BANKER_*` caller lives inside `gangland-npc-shops` — either a pre-npc-shops-split
leftover or a deliberate boundary crossing, not investigated further here.

## Step 13 — static vs. instance recommendation (§9 D4)

**Stay static this wave**, matching D4's existing recommendation and D8's 2026-09-10 decision — nothing in this
gate changes that calculus. This gate's own work is direct evidence for *why*: `CivilianMessages` (the new,
YAML-backed replacement for 12 of the ~600 module-owned constant call sites) is itself an **instance**, DI-wired
through the bean container exactly like `GanglandLootChestMessages`/`JetpackMessages` already are — the
static-vs-instance question only actually bites for the constants that are staying in `gangland-api`'s
`Messages`/`Settings` (the ones this gate's migration table marks core-owned or not-yet-migrated). Converting
*those* remaining ~600 call sites from static enum/field access to instance injection is a mechanical but
sprawling refactor with no behavioural payoff — every module that finishes its own YAML migration (per the table)
naturally ends up on an instance-based holder anyway, the same way civilians just did, without ever touching the
static `Messages`/`Settings` machinery the untouched prefixes still rely on. The YAML-migration mechanism (this
gate) is the smaller, in-scope, incrementally-adoptable step; a wholesale static→instance conversion is not.

## Follow-up (not this gate)

- Migrate `GanglandLootChestMessages` (`gangland-lootchest`) and `JetpackMessages`/`GrappleMessages`
  (`gangland-gadget`) onto `LocalizedModuleYaml` — both predate the shared base and still carry their own copy of
  the identical resolve/fallback logic. Mechanical, low-risk (same behaviour, same tests already passing), but
  explicitly out of scope for this gate per the dispatch ("do NOT refactor lootchest/jetpack onto it in this
  gate").
- Execute the migration table's remaining rows (`TURF`, `DETAINMENT`, `BANKER`/`TRADER` messages, `CAR`) once a
  gate is scoped for them — `CAR` is the cheapest (mechanism already proven via `gadget_messages.yml`).
- `GANG`/`RANK` are blocked on WS5 creating the `gangland-gang` module.
- `civilianAi*`/`civilianSpawner*`/`civilianSpawn*` (22 `Settings` getters) are a real gap this gate did **not**
  touch: civilians has message-YAML plumbing now (this gate) and NPC-config YAML (`npc/civilians.yml`, pre-existing),
  but its ~130-getter-family settings knobs are still core. Worth its own follow-up gate, symmetric to what WS4
  G1a did for Trader/Banker.

## Subagents used

None. The mechanical caller-repoint work (10 files) was done directly rather than via the ≤2-haiku-subagent
allowance — each file's exact before/after text was already in hand from reading every caller up front, so
writing precise subagent prompts and then verifying their output line-by-line would have cost more than editing
directly.

## Concerns / open questions

- `LocalizedModuleYaml.resolveFileHandler` hard-codes `"es"` rather than taking a `lang` parameter — deliberate
  (YAGNI: no third language exists anywhere in this codebase today); flagged in the class javadoc for whoever
  adds one.
- The Civilian legacy-message-block warning is a single hand-wired presence probe
  (`Commands.Civilian.List_Empty`), not a generic "any deleted Messages block" detector — it would need
  re-deriving per module if the same treatment is wanted for `TURF`/`DETAINMENT`/etc. later; not attempted as a
  generic mechanism this gate (would have been scope creep beyond "one worked example").

## Deliverables

- `exec/WS6/G3-report.md` — this file.
- `exec/WS6/G3-package.diff` — `git add -N . && git diff HEAD` (then `git reset`, worktree left uncommitted).
  1219 lines, 24 files touched: 19 modified (`documentation/migration-0.10.0.md`; `Messages.java`/`Settings.java`;
  `CiviliansModuleConfig.java`/`CiviliansYamlConfig.java`; the 10 civilian command classes;
  `message_en.yml`/`message_es.yml`; `MessagesTest.java`) + 5 new (`LocalizedModuleYaml.java`,
  `CivilianMessages.java`, `CivilianMessagesTest.java`, `civilian_messages.yml`, `civilian_messages_es.yml`).
- `exec/WS6/messages-settings-migration-table.md` — step 12's table.

No smoke run, no `scenarios.json`, no commits, no reviewers invoked.
