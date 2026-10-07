# WS3 G1+G2 report — 2026-09-22

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws3` (branch `0.10.0-ws3`, started at HEAD `85299070`, side branch
— to be rebased onto `0.10.0` by the orchestrator). Scope: WS3 G1 (Keystone hologram consumption) + G2 (loot chest
module scaffold + persistence) only, per the corrected brief (Keystone already 1.11.0; "Oriel" = keystone-inventory;
G3 = the CUT lead's in-place GUI swap on branch `0.10.0`, not this stream; G4/G5 land after the merge).

## What changed

### G1 — Gangland consumes Keystone hologram, deletes hologram-api

| Plan step | What I did | File(s) |
|---|---|---|
| 5 (corrected) | Added `keystone-hologram` to root `pom.xml`'s `dependencyManagement` (new entry, `provided`, `${keystone.version}` = 1.11.0, right after the `keystone-npc` block) — the plan's step 5 only touched `lootchest-api`'s pom relying on transitivity, which doesn't work: `provided`-scope deps never transit in Maven, and `gangland-impl` declares `hologram-api` as its **own** direct dependency (not inherited from `lootchest-api`). Per the brief's correction ("add the keystone-hologram provided dependency **where hologram-api was**"), I replaced the `hologram-api` dependency block with `keystone-hologram` in **both** places it was declared: `gangland-ui/lootchest-api/pom.xml` and `gangland-impl/pom.xml`. | `pom.xml`, `gangland-ui/lootchest-api/pom.xml`, `gangland-impl/pom.xml` |
| 6 | Swapped `org.luckyraven.gangland.hologram.*` → `org.luckyraven.keystone.hologram.*` imports, one line (or two, for `ChestCooldownManager`) each, no other line touched. | `ChestCooldownManager.java` (2 import lines), `LootChestManager.java` (impl, 1 line), `GameplayConfig.java` (1 line), `LootChestService.java` (1 line — the `hologramService.clear()` call at `clearChests()` needed no change, only its import) |
| 7 | Deleted `gangland-ui/hologram-api` (`git rm -r`: `pom.xml`, 3 Java classes, `module.properties`); removed `<module>hologram-api</module>` from `gangland-ui/pom.xml`. Left `hologram-api`'s own `dependencyManagement` entry in root `pom.xml` untouched — it's inert with nothing referencing it, and its removal is explicitly C5/G5 scope, not mine. | `gangland-ui/hologram-api/**` (deleted), `gangland-ui/pom.xml` |

Gate after G1 alone: `mvn clean verify -DskipTests` → `BUILD SUCCESS`.

### G2 — Module skeleton + persistence

**Move map** (old → new; verbatim = `git mv` + package-line-only edit, "edited" = also a body change):

| Old location | New location | Verbatim? |
|---|---|---|
| `gangland-ui/lootchest-api/src/main/java/.../lootchest/**` (31 files: `LootChestService`, `ChestCooldownManager`, `config/*` × 4, `data/*` × 5, `events/**` × 9, `handler/**` × 9, `item/LootItemReference`, `listener/LootChestListener`) | `gangland-features/gangland-lootchest/src/main/java/.../lootchest/**` | Verbatim (package `org.luckyraven.gangland.lootchest.*` unchanged — pure directory move) |
| `gangland-ui/lootchest-api/src/test/java/.../lootchest/**` (5 files: `CrackingSessionTest`, `LootChestDataTest`, `LootTableTest`, `LootItemReferenceTest`, `support/TestItemParsers`) | `gangland-features/gangland-lootchest/src/test/java/.../lootchest/**` | Verbatim |
| `gangland-impl/.../lootchest/{LootChestManager,LootChestWand,LootChestWandTag}` | module `.../lootchest/` | `LootChestWand`/`LootChestWandTag` verbatim; `LootChestManager` **edited** — `Gangland` → `JavaPlugin` field/ctor param (D7 house rule: a module never names the concrete `Gangland` class) + import path fix for the relocated `LootChestRepository` |
| `gangland-impl/.../database/repositories/lootchest/LootChestRepository.java`, `.../database/tables/lootchest/LootChestTable.java` | module `.../lootchest/database/{LootChestRepository,LootChestTable}.java` | Package line only (`org.luckyraven.gangland.database.{repositories,tables}.lootchest` → `org.luckyraven.gangland.lootchest.database`, mirrors `gangland-mail`/`gangland-gadget`'s flat `<module>.database` layout — no repositories/tables subsplit). Table name (`loot_chest`) and schema unchanged. |
| `gangland-impl/.../file/configuration/lootchest/{GanglandLootChestMessages,LootChestSettings}` | module `.../lootchest/config/{GanglandLootChestMessages,LootChestSettings}.java` | Package line only — **required now, not deferrable to G4**: both classes `implements` a `LootChestMessagesProvider`/`LootChestSettingsProvider` interface that lives in the module (moved with the rest of `lootchest-api/config`), and the core must never hold a compile dependency on a module. Bodies (still reading `Messages.LOOT_CHEST_*`/`Settings.getLootChest*()` statics from `gangland-api`, which G4 hasn't deleted yet) are byte-for-byte unchanged. |
| `gangland-impl/.../command/sub/lootchest/{LootChestWandCommand,LootChestRemoveCommand,LootChestWandEditCommand}` | module `.../lootchest/command/*` | `LootChestWandEditCommand` verbatim (package line only — it already took `JavaPlugin`, not `Gangland`); `LootChestWandCommand`/`LootChestRemoveCommand` **edited** per B2: `GanglandDatabase` ctor param replaced with Keystone's `RepositoryRegistry` directly (`repositoryRegistry.getRepository(LootChestData.class)` in place of `ganglandDatabase.getRepositoryRegistry().getRepository(...)`) |
| `gangland-impl/.../listener/loot/{LootChestEarnGoodsListener,LootChestWandListener}` | module `.../lootchest/listener/*` | `LootChestEarnGoodsListener` verbatim; `LootChestWandListener` **edited** — `Gangland` → `JavaPlugin` (D7), package line |
| `gangland-impl/src/main/resources/lootchests/{loot_chests.yml,tiers.yml}` | module `src/main/resources/lootchests/{loot_chests.yml,tiers.yml}` | Verbatim (module's own YAML defaults at the data-folder path, per the standing convention) |

**Files created** (no plan/mail-module precedent to move from):

- `gangland-features/gangland-lootchest/pom.xml` — see "Deviation: pom dependencies" below.
- `gangland-features/gangland-lootchest/src/main/resources/module.yml`:
  ```yaml
  # Keystone module descriptor - read by Gangland's ModuleLoader from plugins/Gangland_Warfare/modules/.
  Id: lootchest
  Name: Gangland Loot Chests
  Version: ${project.version}
  Main: org.luckyraven.gangland.lootchest.LootChestModule
  Host_Api: 2.0
  Artifact: org.luckyraven:gangland-lootchest
  ```
- `LootChestModule.java` — mirrors `GadgetModule` (it, like gadget, owns a top-level `/glw lootchest` command, unlike mail): `LISTENER_PACKAGE = lootchest.listener`, `COMMAND_PACKAGE = lootchest.command`, `REPOSITORY_PACKAGE = lootchest.database`; registers **two** configuration classes.
- `LootChestFileConfig.java` (`@Configuration(phase = Phase.KERNEL)`) — **new, not named in the brief's step list but required**: registers the `loot_chests`/`tiers` `FileHandler`s via `moduleLoader.classLoader()`, mirroring `CiviliansYamlConfig`/`CiviliansFiles` exactly (same KERNEL phase, same marker-class pattern for the `@Bean`'s return type). These two handlers used to be registered inline inside the core's `KernelConfig.fileManager()` bean (`fm.addFile(new FileHandler(gangland, "loot_chests"/"tiers", "lootchests", ".yml"), true)`, no module classloader) — removed from there (see "core edits" below), since leaving them would double-register the same file names once the module registers its own, and the YAML template files themselves moved into the module jar (the old core-classloader registration would no longer find them).
- `LootChestModuleConfig.java` (`@Configuration`, CONFIG phase) — bean list:
  - `hologramService()` → `new HologramService(plugin); service.registerProtection(plugin); return service;`
  - `lootChestManager(HologramService, RepositoryRegistry, ItemParser)` → `new LootChestManager(plugin, GanglandApi.FULL_PREFIX, hologramService, repositoryRegistry, itemParser, new GanglandLootChestMessages())`
  - `lootChestService(LootChestManager)` → returns the manager upcast (needed: `LootChestListener`'s ctor takes `LootChestService`, not `LootChestManager`)
  - `lootChestLoader(LootChestManager, FileManager)` → constructs `LootChestLoader` with `new LootChestSettings()`, then **inline** `fileManager.registerInitializer(loader); fileManager.initializeAll();` (B1 — no `@PostConstruct`; exact precedent `CopsNCrooksModuleConfig.copLoader`/`CiviliansModuleConfig`'s civilian loader)
  - `lootChestWandTags(NbtTagCatalog)` → registers the 9 `LootChestWandTag` values' lowercase string names (`tag.toString().toLowerCase()`, matching the pre-split `ItemConfig` loop exactly, C11), returns `LootChestWandTag[]` as the ordering-edge bean (item-registry-injection pattern, `documentation/module-loader.md`'s `NbtTagCatalog` entry)
- `LootChestModuleTest.java` — mirrors `GadgetModuleTest` (2 configs + 3 packages), not `MailModuleTest` (mail has no command package).

**gangland-build/pom.xml** (WS3 correction C6/9b): added the 7th `<artifactItem>` block (artifact copy to `target/modules/`), the 7th `<dependency>` block (`provided`), and a 7th `artifactSet` exclude (`org.luckyraven:gangland-lootchest`) so the shade plugin doesn't fold the module into the core jar.

**gangland-features/pom.xml**: added `<module>gangland-lootchest</module>`.

**Core edits (step 12 + companions):**
- `ItemConfig.java`: removed the `LootChestWandTag` import and the 9-value registration loop from `nbtTagCatalog()`; the bean now returns a bare empty `new NbtTagCatalog()` (module registers into it at runtime when present).
- `GameplayConfig.java`: deleted the 4 hologram/lootchest `@Bean` methods (`hologramService`, `lootChestManager`, `lootChestService`, `lootChestLoader`) and their now-dead imports (`HologramService`, `LootChestManager`, `LootChestService`, `LootChestLoader`, `GanglandLootChestMessages`, `LootChestSettings`, `RepositoryRegistry`). **Kept** `initializeLootChestLoader()`, renamed `initializeDeferredLoaders()` (body — `fileManager.initializeAll()` — byte-for-byte unchanged) per B1: it's the global CONFIG-phase initializer for every core-registered `FileLoader`, not lootchest-specific.
- `KernelConfig.java`: removed the two `fm.addFile(new FileHandler(gangland, "loot_chests"/"tiers", "lootchests", ".yml"), true)` lines from `fileManager()` — now owned by the module's `LootChestFileConfig` (see above).

## Deviations from the plan

1. **`NbtTagCatalog` moved `gangland-impl` → `gangland-api`.** CLAUDE.md's own module-API-contract list flags this explicitly: *"NbtTagCatalog (no module uses it yet — move it the day one does)."* That day is today — `LootChestModuleConfig.lootChestWandTags` is the first module bean to take it as a constructor param, and a module can only compile against `gangland-api`, never `gangland-impl`. `git mv gangland-impl/.../item/NbtTagCatalog.java gangland-api/.../item/NbtTagCatalog.java`, package unchanged (`org.luckyraven.gangland.item`), body untouched. `ItemConfig`/`ReadNBTCommand`/`HolderSeamBeanTypeTest` needed no import edits (same FQN, gangland-impl already depends on gangland-api). Not in the plan text or the brief's step list, but required for a green build — flagged here per the brief's docket-note expectation.
2. **`gangland-impl/pom.xml`'s `hologram-api`→`keystone-hologram` swap wasn't in the plan's G1 step 5** (which only touched `lootchest-api`'s pom, relying on Maven transitivity that doesn't apply to `provided` scope). Handled per the brief's own correction text, which named both locations explicitly ("where hologram-api was").
3. **`LootChestFileConfig` (new class, KERNEL phase) + the `KernelConfig.java` edit** — not named in the brief's G2 step list, but required: the `loot_chests`/`tiers` `FileHandler`s have to move with their YAML templates or `LootChestLoader.resolvePrimaryHandler` finds nothing.
4. **`LootChestWand.java` and `LootChestWandEditCommand.java` moved in G2, not deferred whole to "G3."** The plan's own §4 table put `LootChestWandEditCommand`'s *move* in G3 (owned by the CUT lead). But `LootChestWandCommand` (explicitly mine, B2) directly constructs both `LootChestWand` and `LootChestWandEditCommand` — leaving either behind in `gangland-impl` while `LootChestWandCommand` moved to the module would leave two classes on opposite sides of a compile boundary neither is allowed to cross. I moved both **verbatim** (package line only, zero body edits — their `inventory-api` imports, `AnvilGUI`/`XSeries`/`item-nbt-api-plugin` usage, and admin-preview logic are completely untouched) so the CUT lead's actual Oriel rewrite has one clean set of files to edit in place inside the module, instead of two files split across two repos. This is the same "don't touch `LootChestWand*` bodies" instruction the brief gave for G1, applied to the physical location these files need to be in for G2 to produce a compiling module.
5. **Module pom carries a temporary `inventory-api` (`provided`) + `XSeries` + `item-nbt-api-plugin` + `anvilgui` dependency**, not listed in the plan's abbreviated "pom deps" table (which only named the keystone-* block + `gangland-core`/`gangland-api`). `LootChestService`/`LootChestSession` (mine, explicitly untouched per the brief) and `LootChestWand`/`LootChestWandEditCommand`/`LootChestWandListener` (moved above) all still import `org.luckyraven.gangland.inventory.*` — real, current compile dependencies, not something I can wish away without editing bodies I was told not to touch. Marked with a `ponytail:` comment in the new pom naming the ceiling (drops out once the CUT lead's G3 swap lands) and the upgrade path.
6. **`commands.json` left untouched** (both the core's 4 lootchest entries and no new module `commands.json`) — explicitly G4 scope per the brief ("G4/G5 come after the merge"). `InformationManagerTest`'s hardcoded `assertEquals(153, ...)` count confirms this is safe: I didn't touch the file, so the count is unaffected. Flagged so the G4 executor doesn't miss it.
7. **Root `pom.xml`'s `hologram-api` `dependencyManagement` entry and `gangland-impl`'s/`lootchest-api`'s `lootchest-api` self-dependency were left in place** — both are C5/G5 scope. `gangland-ui/lootchest-api` itself now builds as an **empty jar** (pom + one `module.properties` resource only, zero `.java` files) until G5 deletes the module outright; this is intentional (matches the plan's own G2 rollback note: "module is additive until step 12 deletes core beans... module + core coexist").

## Red-first evidence

No red-first run applies to this gate. Every new/changed test is a **green-on-arrival wiring assertion**, not a
docket-pinned behaviour flip — the plan's own §7 says so explicitly ("every new test in this plan... is a
green-on-arrival wiring test... A gate reviewer should not go looking for red here"), and it's true of the one test
I actually wrote:

- `LootChestModuleTest` (new, 2 sub-tests) — asserts `LootChestModule.configure()` registers
  `{LootChestFileConfig, LootChestModuleConfig}` + the 3 declared packages, and that those packages match where the
  classes actually live. The classes it references (`LootChestModule`, `LootChestFileConfig`,
  `LootChestModuleConfig`) didn't exist before this gate, so there is no pre-fix "wrong behaviour" for it to have
  pinned red against — it's scaffolding verification, mirroring `GadgetModuleTest`/`MailModuleTest` exactly.
- The 5 moved tests (`CrackingSessionTest`, `LootChestDataTest`, `LootTableTest`, `LootItemReferenceTest`, plus the
  `TestItemParsers` support class) were green before the move (under `lootchest-api`) and are green after (under
  `gangland-lootchest`) — a pure relocation, bodies untouched.

## Build

Final command: `mvn clean verify` (full reactor, `E:\Programming\java\wt\gangland-0.10.0-ws3`) →
**`BUILD SUCCESS`**, 01:04 min. Companion check: `mvn -pl gangland-build -am package -DskipTests` →
**`BUILD SUCCESS`**, confirms `target/modules/gangland-lootchest-0.10.0.jar` exists with `module.yml` at its jar
root (verified via `unzip -l`).

**Test count** — sum of every module's Surefire rollup line in the `mvn clean verify` run:

| Module | Tests run |
|---|---|
| gangland-core | 5 |
| gangland-infra/gangland-domain | 119 |
| gangland-infra/gangland-item | 43 |
| gangland-ui/sign-api | 63 |
| gangland-impl | 258 |
| gangland-features/gangland-mail | 25 |
| gangland-features/gangland-civilians | 20 |
| gangland-features/gangland-turf | 91 |
| gangland-features/cops-n-crooks | 76 |
| gangland-features/gangland-gadget | 113 |
| gangland-features/gangland-npc-shops | 17 |
| **gangland-features/gangland-lootchest (new)** | **31** |
| **Total** | **861**, 0 Failures, 0 Errors, 0 Skipped |

**Delta explained:**
- `gangland-ui/hologram-api` **leaves**: 0 tests before, 0 after (G0's `HologramServiceTest`/
  `HologramProtectionListenerTest` are Keystone-side, not this repo's — not in scope here).
- `gangland-ui/lootchest-api` **loses 29** (`CrackingSessionTest` 6 + `LootChestDataTest` 8 + `LootTableTest` 10 +
  `LootItemReferenceTest` 5; `TestItemParsers` is a support class, contributes 0 directly) — the module jar is now
  empty of tests (and of `.java` files).
- `gangland-features/gangland-lootchest` **gains 31** = the same 29 moved verbatim + 2 new (`LootChestModuleTest`'s
  two `@Test` methods).
- `gangland-impl` **unchanged at 258** — confirmed via a pre-edit grep (`grep -rl "LootChest|hologram|Hologram"
  gangland-impl/src/test` → no hits) that no lootchest/hologram test lived there to begin with.
- Net global delta vs. the implied pre-gate total (861 − 31 + 29 = **859**): **+2**, exactly
  `LootChestModuleTest`'s two new assertions. Everything else is a lateral move.

## Docket ids touched

None fixed (structural move only, per the plan's D5 — 19 of 21 open loot-chest/hologram docket bugs carry over
as-is; LS-30/LS-31 are the reviewer's free-fix carve-out but live inside `LootChestWand`/`LootChestWandListener`,
which G3 — not this gate — rewrites). **The docket `write_db` note-row pass per entry (plan's G5 step 19b) is
explicitly G5 scope, not run here.** For the record, the 21 ids this move touches the *location* of (unchanged
behaviour): UI-13, UI-14, UI-15, UI-33, LS-02, LS-05, LS-11, LS-12, LS-13, LS-14, LS-15, LS-16, LS-17, LS-18, LS-23,
LS-25, LS-29, LS-30, LS-31, LS-32, LS-33, plus fixed-already T-11 (its fix, `GameplayConfig`'s deferred
`fileManager.initializeAll()` call, is exactly what `initializeDeferredLoaders()` — kept, renamed — still does).

**Docket candidates (new, not previously filed):** none found. No new bug was introduced or noticed while doing
this move; the compile-boundary issues encountered (`NbtTagCatalog`, the `KernelConfig` FileHandler registration,
`LootChestWand`/`WandEditCommand`'s forced co-location) are pre-existing architectural facts this move had to
account for, not new bugs.

## Deferred smoke rows (for the merge — plan §7, not run here per the brief: "no smoke, list the rows the plan names")

1. Boot with the module **absent**: `/glw lootchest` absent, no fault logged, core boots clean.
2. Boot with the module present: `/glw lootchest` gives the wand; right-click an allowed block places a chest;
   right-click again opens it (shared inventory), take an item, close — cooldown hologram shows the
   `LOOT_CHEST_HOLOGRAM_COOLDOWN` text.
3. Cracking-enabled chest: confirm the session **always fails** after `Cracking_Time` seconds
   (`LootChestCrackingFailureEvent` then `LootChestCrackingEndEvent`) — docket LS-02's observable shape, not fixed
   here.
4. `/glw reload`: chest registry + hologram set survive (config re-read, in-memory chests re-registered from DB).
5. Restart: chests persist (row survives, hologram re-spawns from DB `is_looted`/`respawn_time`).
6. `onDisable`: no armor-stand leak — `world.getEntitiesByClass(ArmorStand.class)` count before/after a chest
   cooldown cycle + server stop, confirming `HologramService.clear()`/`onShutdown()` removed every stand (docket
   UI-13/UI-15 already document related leaks — not fixed here).

## Subagents used

None. Given the compile-boundary analysis required to get G2 right (NbtTagCatalog, the KernelConfig FileHandler
split, the forced LootChestWand/WandEditCommand co-location, the inventory-api pom gap) needed full-context
reasoning across ~40 files before a single line moved, and the token budget for this session was not constrained,
I did the reading, moving and wiring directly rather than delegating mechanical steps to a haiku subagent whose
output I'd still have had to independently re-verify file-by-file against the same dependency graph.

## Concerns / open questions

- **G3 (CUT lead)** should find `LootChestWand.java`, `LootChestWandEditCommand.java`, `LootChestService.java`,
  `LootChestSession.java` already living inside `gangland-features/gangland-lootchest/src/main/java/.../lootchest/`
  (not `gangland-impl`/`gangland-ui`) when their branch merges here — flag this to them explicitly so they don't
  look in the old locations. Their Oriel rewrite of `LootChestWand`/`LootChestWandEditCommand` is the trigger that
  lets the module pom's temporary `inventory-api`/`XSeries`/`item-nbt-api-plugin`/`anvilgui` dependencies (deviation
  5 above) come back out.
- **G4 executor**: `GanglandApi.VERSION`/`Host_Api` is already `2.0` on this branch (confirmed — I did not need to
  bump it, matching the brief's note that Gangland G0 already landed), so the `Messages`/`Settings` Loot_Chest
  deletions (plan step 17) are legal whenever G4 runs. `LootChestSettingsProvider` still needs its 5 reward-getter
  methods (C4) and `LootChestEarnGoodsListener` still reads `Settings.getLootChestReward*()` statics directly —
  both untouched here, exactly as scoped.
- **Rebase risk**: this branch is a side branch off `0.10.0-ws3` (not `0.10.0` itself). Every file this gate moved
  or edited is lootchest/hologram-specific except `pom.xml`, `gangland-impl/pom.xml`, `gangland-features/pom.xml`,
  `gangland-build/pom.xml`, `gangland-ui/pom.xml`, `KernelConfig.java`, `ItemConfig.java`, `GameplayConfig.java` —
  worth a close look at rebase time since other WS streams likely touch some of the same pom files.

## Files touched (summary — full detail in `G1-G2-package.diff`)

72 files changed (`git diff HEAD --stat`): 397 insertions, 477 deletions net (most "deletions" are `git mv`'d files
appearing as delete+add since `git diff` without `-M` doesn't collapse a cross-module move into a rename). Package
diff at `brainstorming/decoupling-wave-2026-09-14/exec/WS3/G1-G2-package.diff` (from `git add -N . && git diff HEAD`
in the worktree, then `git reset` — nothing was staged or committed).
