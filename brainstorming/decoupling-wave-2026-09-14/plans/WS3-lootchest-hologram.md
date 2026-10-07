# WS3 Plan: Loot chests → runtime module; holograms → Keystone

**Planner:** Sonnet, 2026-09-14 (revised same day after Opus review — see §0b). Repos: Gangland Warfare `0.9.1` →
`0.10.0` (new branch), Keystone `phase-h9-host-api` (1.9.2) → `phase-h10-hologram` (1.10.0). No code written;
graphify-oriented, raw files read only to confirm the lines cited below.

## 0b. Review response

Opus review (`reviews/REVIEW-WS3.md`, verdict PASS WITH FIXES) applied in place, plus two orchestrator rulings
that supersede the review's own recommendation on B3 and B4's resolution. Every id below changed the section
named; nothing was rejected.

| Id | What changed |
|---|---|
| **B1** | §2b move list, §3 seams, §4 G2 corrected: delete only `GameplayConfig.java:267-290` (the 4 hologram/lootchest `@Bean` methods). `initializeLootChestLoader()` (`:313-319`, the T-11 fix, body = global `fileManager.initializeAll()`) **stays in the core**, renamed `initializeDeferredLoaders`. The module's own `lootChestLoader` `@Bean` calls `fileManager.registerInitializer(loader); fileManager.initializeAll();` **inline**, verified against the exact precedent: `CopsNCrooksModuleConfig.java:301` (`CopLoader`) and `CiviliansModuleConfig.java:54` (`CiviliansLoader`) both do exactly this — no separate `@PostConstruct` in the module. |
| **B2** | §2b, §3, §4 G2/G3 corrected: `LootChestWandCommand`/`LootChestRemoveCommand` swap their `GanglandDatabase` ctor param (`LootChestWandCommand.java:11,22,26,62`; `LootChestRemoveCommand.java:12,22,25,28,59`) for Keystone's `RepositoryRegistry` directly (`ganglandDatabase.getRepositoryRegistry()` → the injected field itself) — `RepositoryRegistry` is a Keystone class, not excluded from the api. `LootChestWandEditCommand` (`LootChestWandEditCommand.java:13`, imports `inventory.part.Fill`) moves out of G2's "unchanged" list into G3's Oriel step. |
| **B3 / orchestrator ruling (1)** | §2b, §4 G4, §9 D6 corrected: **not** the reviewer's "defer Messages/Settings deletion to WS6" fix. Per orchestrator ruling: the Gangland `0.10.0` branch bumps `GanglandApi.VERSION` and every existing module's `module.yml` `Host_Api` to **`2.0`** in its very first commit ("Gangland G0" — branch-wide, orchestrator-owned, not one of this plan's own gates, lands before WS3's G4 runs). The breaking major is therefore already open when G4 executes, so the `Messages`/`Settings` deletions stay in G4 as originally planned — not deferred. §9 D6 rewritten to record this. |
| **B4 / orchestrator ruling (2)** | §3, §9 D4, §10 resolved (no longer an open arbitration): **WS3 owns `LootChestService`/`LootChestSession`**; the orchestrator is telling WS2's planner to drop those 2 files from WS2's port list. §10's Oriel ask is corrected to the real one — the wand-preview admin screen (`PaginatedChestMenu`/`ChestMenuBuilder`) — not "none". |
| **C1** | §4 G1 step 6: import-swap list now includes `LootChestService.java` (`clearChests()`, ~`:419`, calls `hologramService.clear()`), not just the 3 files named before. |
| **C2** | §3 seams table: listener-boundary reasoning corrected. `ListenerService.scanAndRegisterListeners(String, ClassLoader)` (`keystone-bean/.../ListenerService.java:173`) genuinely **can** cross a plugin boundary — passing `HologramService.class.getClassLoader()` would work. `registerProtection(JavaPlugin)` is kept on ponytail grounds (no scan indirection needed for one listener), not because the scan "cannot see it". Strengthened precedent: `keystone-npc` ships **zero** `implements Listener` classes at all (grep-verified by the reviewer). |
| **C3** | §5 table line numbers corrected: `Messages` 26 constants at `Messages.java:330-355` + `:575-578` (not `329-355,574-578`); `Settings` **10** getters (not 11) at `Settings.java:209-214`; YAML parse block `:690-706` (not `:689`); `settings.yml` `Loot_Chest:` starts `:593`, `Money_Drop:` banner at `:627` (not `:625`) — do not delete `Money_Drop:`. |
| **C4** | §5 table split: `LootChestSettingsProvider` (`.../config/LootChestSettingsProvider.java:5-31`) covers only 5 of the 10 settings (countdown, 3 sounds, allowed blocks). The 5 reward getters (`getLootChestRewardMoneyMinimum/Maximum`, `…ExperienceMinimum/Maximum`, `getLootChestRewardCommands`), read as `Settings.*` statics by `LootChestEarnGoodsListener`, need **new** provider methods. §4 step 15 resized S→M. |
| **C5** | §2b "Deleted" + §4 G5 step 18: pom cleanup widened from `gangland-ui/pom.xml`'s `<modules>` alone to also `gangland-impl/pom.xml:55` (hologram-api dep), `:79` (lootchest-api dep), root `pom.xml:263` and `:268` (dependencyManagement entries for both). |
| **C6** | §4 new step (G2): `gangland-build/pom.xml` enumerates modules **twice** — the artifact-copy list at `:123-148` (6 `<artifactItem>` blocks, verified) and `<dependencies>` at `:170-200` (6 `<dependency>` blocks, verified) — `gangland-lootchest` needs one new block in each, or the jar never reaches `target/modules/`. §2b's "copied by gangland-build's existing assembly step" corrected. |
| **C7** | §7 smoke row 3 rewritten: a cracking-enabled chest **always fails** after `Cracking_Time` seconds (`CrackingSession.start`, `CrackingSession.java:64-81`, sets `FAILED` and fires `onFailed` once `timeRemaining <= 0`) since nothing calls `complete()`/`addProgress()` — not "stuck at CRACKING_STARTED forever". |
| **C8** | §4 G5 step 19 widened: `documentation/features/loot_chests.md` (whole feature doc) and `documentation/developer/modules.md` added as required; the rest of `documentation/developer/{architecture,ui-framework,persistence,commands,configuration,dependency-injection,items}.md` and `documentation/tests/*` flagged as possibly touched. |
| **C9** | §4 new step (G5), §11: after the move, write a `note` row (via `write_db`) per touched docket id (all 21 `LS-*`/`UI-*`) recording the new file path — "carried over as-is" was not the same as "recorded". LS-16/LS-32 (`LootTableTest`) and LS-02 (`CrackingSessionTest`) are test-pinned and move with their tests in G2 step 9 already. |
| **C10** | §1, §3 GUI point 3, §11 LS-02 row reworded: `CrackingSession` **is** constructed in production (`LootChestService.java:428`, inside `startCrackingMinigame(...)`) and `completeCracking(Player)` exists (`LootChestService.java:273`) — what has zero callers is specifically `addProgress`/`complete`/`completeCracking`, not the class's construction path. |
| **C11** | §3, §5: `ItemConfig.java:132-138` registers `tag.toString().toLowerCase()` — the **lowercase string name**, not the enum object — corrected everywhere this was described as "registers the 9 values". |
| **C12** | §3 gang seam row, §4 G6 step 21: gang-domain import list widened from 3 types to **4** — `User`, `UserManager`, `Level`, **and `org.luckyraven.gangland.gang.events.level.LevelUpEvent`** (`LootChestEarnGoodsListener.java:11`). `UserLevelUpEvent` (`:8`) stays safe — already in `gangland-api`. |
| **C13** | §6 new line: census's open "DataCleanupTask: TBD" question answered — the SPI and its sole implementor were deleted with the weapon module (`PluginDataCleanupServiceTest.java:27-28`); loot chests never participate, nothing to wire. |
| **C14** | §2a: added `ponytail:` note — `Hologram` is ArmorStand-only (Keystone floor Spigot 1.16.5-compliant); upgrade path is `TextDisplay` (1.19.4+) behind an `NmsVersion` gate, never a bare import. Shared-classloader rule already satisfied (`HologramService` has no statics; `Hologram`'s only static is the server-global `LINE_HEIGHT` constant). |
| **S1** | §9 D2 wording fixed: the cheaper fallback (rejected) is `keystone-common`, not `keystone-npc` — `keystone-npc`'s Citizens guard has nothing to do with armor stands. Decision (a), new module, unchanged. |
| **S2** | §4 step 13, §7: **dropped** the new `LootChestSessionTest` — a real Bukkit `Inventory` isn't unit-testable without a server; smoke row 2 is the real check. `SharedLootInventory` itself stays ~20-30 lines. |
| **S3** | §4 step 11, §7: **dropped** `LootChestModuleConfigTest` (asserts beans the container already constructs at boot); kept `LootChestModuleTest` and `LootChestSettingsTest`. |
| **S4** | §10: no change — not promoting the 9 events into `gangland-api` is confirmed correct (no consumer; `turf`→`civilians` precedent is the escape hatch). |
| Estimate check | §12 rewritten to **~6 days** with the review's reasoning (gangland-build + pom cleanups, B2's command edits, C4's provider methods, C8's real docs scope, C9's docket pass); §4 G3 step 14 now explicitly carries `LootChestWandEditCommand`, so the "540 LoC" figure is a floor, not the whole Oriel-rebuild size. |
| "Genuinely red" note | §7: added — every new test in this plan is a green-on-arrival wiring test (consistent with D5's carry-over stance), not a docket flip; a gate reviewer should not expect red here. |

## 1. Scope

User's words: *"Lootchests needs a revamp, since they are coupled with hologram api and they can be set as a
module. Thus you can have hologram to be in keystone plugin."*

**In (this wave, Option A — structural revamp):**
- `HologramService`/`Hologram`/`HologramProtectionListener` (`gangland-ui/hologram-api`, 3 files, 346 LoC) move to a
  new Keystone module `keystone-hologram` (1.10.0). Amends Keystone `CLAUDE.md:101` — recorded as a decision, §9.
- Loot chests (`gangland-ui/lootchest-api` 31 files + `gangland-impl` 10 files, ~41 files total) become the runtime
  module `gangland-features/gangland-lootchest` (id `lootchest`), built on the `gangland-mail` template.
- `lootchest-api` is folded into the module (no library jar survives) — one jar, no dependency left in the core.
- Every GUI touch point currently on `inventory-api` is rebuilt: the admin wand-preview screen (`LootChestWand` +
  `LootChestWandEditCommand`) on Oriel; the chest-opening view (`LootChestService`/`LootChestSession`) is **not**
  menu-shaped and moves to a small raw-Bukkit-`Inventory` wrapper the module owns — **WS3 owns both files**, per
  orchestrator ruling (2) resolving what was an open WS2/WS3 conflict at review time (§9 D4).
- Config/messages/commands/persistence migrate per C6/C7 (§5, §6).

**Out / Deferred (Option B — feature revamp, not this wave; each is a one-line, separately-scoped ask):**
- Safe-cracking minigame **input mechanism**. `CrackingSession` is genuinely constructed and run in production
  (`LootChestService.java:428`, `startCrackingMinigame(...)`; `completeCracking(Player)` exists at
  `LootChestService.java:273`) — but nothing anywhere in the reactor calls `addProgress()`, `complete()`, or
  `completeCracking()`. The session always runs its full timer and fails. FRONT-PAGE.md lists "Safe Cracking
  Minigame and Advanced Chest Mechanics" as an Upcoming Feature, and docket **LS-02** (open, P2) already says "the
  cracking mini-game is unreachable (dead feature)". Building the input UI is a feature addition, not a
  decoupling; deferred.
- Tier/refill/cooldown gameplay redesign — no such ask in the user's sentence; the fields (`LootTier`, cooldown
  timer) move as-is.
- Per-tier hologram text variation — the 6 `LOOT_CHEST_HOLOGRAM_*` messages already exist and move with the module
  (§5); no new design.
- 19 of the 21 open loot-chest/hologram docket bugs (§11) are **carried over as-is**, not fixed. **LS-30** ("the
  wand writes to the currently held item, not the GUI's item") and **LS-31** ("allowed-block substring match",
  confirmed at `LootChestWandListener.java:73`) are the reviewer's carve-out to D5: both sit inside
  `LootChestWand`/`LootChestWandListener`, which G3 step 14 rewrites wholesale for Oriel anyway, so fixing them
  there is free — flipping nothing extra, no separate step.

## 2. Target layout

### 2a. Keystone: new module `keystone-hologram`

| Item | Value |
|---|---|
| Coordinates | `org.luckyraven:keystone-hologram:1.10.0`, packaging `jar` |
| Package | `org.luckyraven.keystone.hologram` (renamed from `org.luckyraven.gangland.hologram`) |
| Deps (compile/default scope, shaded) | `keystone-common`, `keystone-bean` (for `BeanLifecycle`) — mirrors `keystone-npc/pom.xml`'s dependency shape minus `keystone-persistence` (hologram needs no persistence) |
| Deps (provided) | `spigot-api` |
| Root `pom.xml` | new `<module>keystone-hologram</module>` after `<module>keystone-npc</module>` (`Keystone/pom.xml:49`) |
| Shaded by | `keystone-plugin/pom.xml` — new dependency block after the existing `keystone-npc` block (`keystone-plugin/pom.xml:88-91`) |
| Doc | `Keystone/docs/keystone-hologram.md`, new, mirrors `docs/keystone-npc.md`'s shape |
| Classes | `HologramService` (unchanged body, `implements BeanLifecycle`, **+1 new method** `registerProtection(JavaPlugin)` — §3), `Hologram` (unchanged), `HologramProtectionListener` (loses `@ListenerHandler`, becomes a plain `implements Listener` POJO — §3) |
| Version floor | **`ponytail:`** `Hologram` is ArmorStand-only (`Hologram.java`, `EntityType.ARMOR_STAND`) — compliant with Keystone's API floor (Spigot 1.16.5, "never reference newer Bukkit API from Keystone code"). Upgrade path when the floor moves: `TextDisplay` (1.19.4+) behind an `NmsVersion`/version-gate check, never a bare import that would break sub-1.19.4 servers. Shared-classloader rule already satisfied: `HologramService` has no static state; `Hologram`'s only `static` is the server-global `LINE_HEIGHT` constant (`Hologram.java:22`), which is allowed |
| `CLAUDE.md` | `Keystone/CLAUDE.md:101` amended: "Scoreboard code stays in Gangland; hologram code moved to `keystone-hologram` (2026-09-14, WS3 decoupling wave — see `docs/keystone-hologram.md`)." |

Gangland deletes `gangland-ui/hologram-api` (3 files) entirely; its `pom.xml` entry is removed from
`gangland-ui/pom.xml`'s `<modules>`.

### 2b. Gangland: new runtime module `gangland-features/gangland-lootchest`

| Item | Value |
|---|---|
| Artifact | `org.luckyraven:gangland-lootchest`, id `lootchest` |
| Package | `org.luckyraven.gangland.lootchest` (unchanged root — both halves already share it) |
| `Main` | `org.luckyraven.gangland.lootchest.LootChestModule` |
| `module.yml` | `Id: lootchest`, **`Host_Api: 2.0`** — bumped branch-wide at "Gangland G0" (the `0.10.0` branch's very first commit, orchestrator-owned, lands before this module's own `module.yml` is authored; every one of the six existing modules is bumped in that same commit, not something WS3 executes itself), no `Depends:` at ship time (see gang-dependency seam, §3; G6 patches this post-WS5), no `Plugins:` (loot tables reach `weapon:` items only through `ItemVocabulary`, confirmed no compile-time Bartizan symbol in any of the 41 files grepped), `Artifact: org.luckyraven:gangland-lootchest` |
| pom deps | Same keystone-* block as `gangland-mail` (`keystone-common`, `keystone-bean`, `keystone-command`, `keystone-persistence`, `keystone-item`, `keystone-module`) **+ new `keystone-hologram`** (default scope, shaded transitively through `Keystone.jar` at runtime — same `provided`-at-Gangland/`compile`-at-Keystone split every other keystone-* dep uses), `gangland-core` (provided), `gangland-api` (provided, the only Gangland host artifact) |
| jar | `target/modules/gangland-lootchest-<rev>.jar` — **requires** a new `<artifactItem>` block in `gangland-build/pom.xml:123-148` and a new `<dependency>` block in `:170-200` (both currently enumerate the six existing modules one block each, verified — the assembly step is not automatic, §4 G2 new step) |
| YAML defaults | `lootchests/loot_chests.yml`, `lootchests/tiers.yml` ship inside the module jar at the data-folder path (mirrors `npc/cops.yml`) |
| `commands.json` | at jar root — the 4 entries below move out of the core's |

**Move list** (source → destination, package root unchanged unless noted):

| From | Files | Notes |
|---|---|---|
| `gangland-ui/lootchest-api/.../lootchest/*` | `LootChestService`, `ChestCooldownManager` | `LootChestService.InventoryHandler` field replaced — §3 GUI findings. `LootChestService` also imports `HologramService` and calls `hologramService.clear()` in `clearChests()` (~`:419`) — included in the G1 import swap (C1) |
| `.../lootchest/data/*` | `CrackingSession`, `LootChestData`, `LootChestSession`, `LootTable`, `LootTier` | `LootChestSession`'s `InventoryHandler` field replaced too |
| `.../lootchest/events/**` (9), `handler/**` (9) | unchanged | pure POJOs, no inventory-api/hologram-api coupling found |
| `.../lootchest/item/LootItemReference`, `listener/LootChestListener` | unchanged | listener already uses raw `InventoryClickEvent`/`InventoryCloseEvent`, not `InventoryHandler` dispatch |
| `.../lootchest/config/*` | `LootChestConfig`, `LootChestLoader`, `*Provider` | `LootChestLoader` keeps its `FileLoader<LootChestConfig>` shape; its 2 `FileHandler`s + `fileManager.initializeAll()` call move into the module's own config bean, called **inline** (B1 — no `@PostConstruct`) |
| `gangland-impl/.../lootchest/{LootChestManager,LootChestWand,LootChestWandTag}` | rewritten | `LootChestWand` (540 LoC) drops its `inventory-api` imports for Oriel (§3); `LootChestWandTag` registers its 9 values' **lowercase string names** (not the enum objects, C11) into `NbtTagCatalog` |
| `gangland-impl/.../database/{repositories,tables}/lootchest/*` | unchanged | table name `loot_chest` unchanged — §6, no migration |
| `gangland-impl/.../file/configuration/lootchest/{GanglandLootChestMessages,LootChestSettings}` | unchanged bodies, new home, **+5 new methods** | become the module's own provider implementations; `LootChestSettingsProvider` gains 5 reward-getter methods it lacks today (C4, §5) |
| `gangland-impl/.../command/sub/lootchest/{LootChestWandCommand,LootChestRemoveCommand}` (2 of 3) | **edited, not verbatim** | swap `GanglandDatabase` ctor param for Keystone's `RepositoryRegistry` (B2) — `LootChestWandCommand extends Command` is already a **root** `@CommandHandler` (`LootChestWandCommand.java:19,27`: `super(gangland, "lootchest", true, "wand", "lootchestwand", "chestwand", "lcwand")`), not a `CommandContribution` — matches `gangland-gadget`'s `CarCommand extends Command` precedent, so it moves into the module's command package and is picked up by the existing per-module command scan |
| `gangland-impl/.../command/sub/lootchest/LootChestWandEditCommand` (3rd of 3) | **moved to G3, not G2** | imports `inventory.part.Fill` (`LootChestWandEditCommand.java:13`) — an inventory-api/Oriel consumer, rebuilt alongside `LootChestWand` |
| `gangland-impl/.../listener/loot/{LootChestEarnGoodsListener,LootChestWandListener}` | unchanged mechanism | `LootChestWandListener` drops `Gangland` for `JavaPlugin` (D7 rule); `LootChestEarnGoodsListener` keeps its `gang.user.{User,UserManager,Level}` **and `gang.events.level.LevelUpEvent`** imports (C12) — see gang seam, §3 |

**Deleted:** `gangland-ui/lootchest-api` (whole module — pom entries removed from `gangland-ui/pom.xml`'s
`<modules>`, `gangland-impl/pom.xml:55` (hologram-api dep) and `:79` (lootchest-api dep), root `pom.xml:263` and
`:268` (dependencyManagement entries), C5), the 10 `gangland-impl` lootchest files above, `ItemConfig.java:131-138`'s
`LootChestWandTag` loop (kept as an empty catalog bean), `GameplayConfig.java:267-290`'s hologram/lootchest beans
(**not** `:313-319`, B1), 4 `commands.json` entries (`gangland-impl/src/main/resources/commands.json:466-481`),
the `Loot_Chest:` block in `settings.yml` (`:593` through the line before the `Money_Drop:` banner at `:627` — not
`:621`, C3; **do not** delete `Money_Drop:`), 26 `LOOT_CHEST_*` `Messages` constants
(`gangland-api/.../Messages.java:330-355,575-578`, C3), 10 `Settings` fields/getters + their YAML parse block
(`gangland-api/.../Settings.java:209-214,690-706`, C3) — deleted in G4 as originally planned, legal because
"Gangland G0" already bumped the major (B3/ruling 1, not deferred to WS6).

## 3. Seams

| Boundary | Mechanism | Publishes | Pulls | Default when absent |
|---|---|---|---|---|
| Hologram bean creation | `@Bean` in the module's own config (moved from core `GameplayConfig.hologramService()`, `GameplayConfig.java:267-270`) | `LootChestModuleConfig` | `LootChestManager` ctor param (unchanged shape) | n/a — hologram has no external plugin gate |
| Hologram protection listener registration | `HologramService.registerProtection(JavaPlugin)` calls plain `Bukkit.getPluginManager().registerEvents(new HologramProtectionListener(this), plugin)`. **Correction (C2):** this is a *choice*, not a necessity — `ListenerService.scanAndRegisterListeners(String basePackage, ClassLoader)` (`keystone-bean/.../listener/ListenerService.java:173`) genuinely can cross a plugin boundary; Gangland could pass `HologramService.class.getClassLoader()` and the existing `@ListenerHandler` scan would find `HologramProtectionListener` inside `Keystone.jar` fine. `registerProtection` is kept anyway because it is the lazier option: one direct stdlib Bukkit call beats threading a Keystone-jar classloader reference through the core's bootstrap for one listener. Precedent, stated more strongly than before: `keystone-npc` ships **zero** `implements Listener` classes at all (grep-verified) | `LootChestModuleConfig` calls `hologramService.registerProtection(gangland)` once, using the **host** `JavaPlugin` (all listeners — core or module — already register under the one host plugin identity; `ListenerService` is constructed once with that `plugin` field) | n/a |
| `NbtTagCatalog` registration | **Existing** registry-injection seam (`documentation/module-loader.md:225-230`) | `LootChestModuleConfig.@Bean` takes `NbtTagCatalog` param, registers the 9 `LootChestWandTag` values **as their lowercase string names** (`tag.toString().toLowerCase()` — the exact form `ItemConfig.java:132-138` uses today, C11; registering the enum objects instead would break existing wands) | Core's `ItemConfig.nbtTagCatalog()` (`ItemConfig.java:131-138`) loses its loop, returns an empty catalog | Catalog stays valid and empty if the module is absent |
| Repository/table scan | **Existing**: `RepositoryRegistry.scanAndRegisterRepositories(modulePackage, moduleLoader.classLoader())`, same call shape as `DatabaseConfig.java:81,86` | `LootChestModuleConfig` package | `DatabaseConfig`'s per-module loop | Module absent → repository/table never registered |
| Command discovery | **Existing**: per-module command-package scan, `GanglandContext.java:286-292` | `LootChestWandCommand` (root `@CommandHandler extends Command`, now taking `RepositoryRegistry` not `GanglandDatabase`, B2) | n/a — not a `CommandContribution`, its own root, like gadget's `CarCommand` | Module absent → `/glw lootchest` doesn't exist, no fault |
| Listener discovery | **Existing**: per-module listener-package scan, `GanglandContext.java:250-256` | `LootChestListener`, `LootChestWandListener`, `LootChestEarnGoodsListener` (all `@ListenerHandler`, unchanged) | n/a | Module absent → no listeners registered |
| YAML defaults copy-out + deferred load | **Existing** pattern (`module-loader.md:116`) for the `FileHandler`; **B1 fix** for the initializer: the module's `lootChestLoader` `@Bean` calls `fileManager.registerInitializer(loader); fileManager.initializeAll();` **inline**, exactly like `CopsNCrooksModuleConfig.java:301`/`CiviliansModuleConfig.java:54` — no `@PostConstruct`. The core's own `initializeLootChestLoader()` (`GameplayConfig.java:313-319`) is **kept**, renamed `initializeDeferredLoaders`, since its body (`fileManager.initializeAll()`) is the global CONFIG-phase initializer for every core-registered `FileLoader`, not lootchest-specific — deleting it would silently stop every other core `FileLoader` | `LootChestModuleConfig.@Bean` (takes `ModuleLoader` param for `.classLoader()`, already an injectable container bean per `GanglandContext.java:117`) | `LootChestLoader` (unchanged `FileLoader<LootChestConfig>` shape) | n/a |
| Gang domain (`User`/`UserManager`/`Level`/`LevelUpEvent`) | **Transitional.** `LootChestEarnGoodsListener.java:10-14,33` imports `org.luckyraven.gangland.gang.user.{User,UserManager,Level}` **and `gang.events.level.LevelUpEvent`** (`:11`, C12) directly for money/XP rewards. At WS3's execution point these resolve through the **current** `gangland-api`'s domain re-export (README.md:135) — no new seam. **After** WS5 ships the `gang` module and WS6's own content work removes the domain re-export from `gangland-api` (a separate axis from the `Host_Api`/`GanglandApi.VERSION` **number** already being `2.0` from Gangland G0 — the number flipping does not itself move any code), this import stops resolving. Follow-up gate **G6** (§4): add `provided`-scope pom dependency on `gangland-gang` + `Depends: [gang]` to `module.yml`, mirroring the **already-shipped** precedent `gangland-features/gangland-turf/pom.xml`'s dependency on `gangland-civilians`. `UserLevelUpEvent` (used elsewhere) stays safe — already lives in `gangland-api` | — | — | Not patched → compile-time break, caught immediately, not a runtime surprise |
| Oriel menus (admin wand preview) | `ServicesManager` lazy lookup of `MenuRegistry`/`MenuOpener`, same pattern WS2 census §4 describes — **assumed** WS2 makes Oriel a `plugin.yml depend:` (hard, like Keystone) rather than a soft `Plugins:` module gate, since core menus need it unconditionally too. Still unconfirmed against WS2's actual plan (§13) | Oriel's `menu-plugin` | `LootChestModuleConfig`'s `@Bean` pulled from `ServicesManager` | If WS2 instead treats Oriel as optional per-consumer, this module needs its own `Plugins: [Oriel]` — noted as the fallback |

### GUI findings (read past the census into the actual source — corrects it)

Reading `LootChestService.java`, `LootChestSession.java` and `LootChestListener.java` shows **three separate,
structurally different** GUI touch points:

1. **Chest-opening view is not a menu.** `LootChestService.java:576`
   (`inventory = new InventoryHandler(title, chestData.getInventorySize(), key, player.getUniqueId());`) and
   `LootChestSession.java:20,63,102-179` use `InventoryHandler` purely as a thin wrapper around a raw Bukkit
   `Inventory` — multiple players can share one instance, items are freely taken/placed, and
   `LootChestListener.java:73-92` handles `InventoryClickEvent`/`InventoryCloseEvent` **directly**. **Resolved by
   orchestrator ruling (2):** WS3 owns this file pair; WS2 drops it from its own port list. **Decision:** replace
   `InventoryHandler` with a ~20-30 line module-owned wrapper around `Bukkit.createInventory(holder, size, title)`
   — stdlib rung of the ladder, no Oriel dependency for this path.
2. **Admin wand-preview screen is a real menu.** `LootChestWand.java` (540 LoC) imports `InventoryHandler`,
   `inventory.part.Fill`, `inventory.util.InventoryUtil` for a 54-slot paginated preview grid opened from
   `LootChestWandListener.java:45,89` (`wand.openConfigInventory(player, fill)`); `LootChestWandEditCommand.java:13`
   also imports `inventory.part.Fill` and belongs in this same rebuild (B2). Rebuild on Oriel's
   `PaginatedChestMenu`/`ChestMenuBuilder` per C5(WS2). This is the **real** Oriel ask (§10).
3. **Cracking minigame view does not exist as a UI, but the state machine is live.** `CrackingSession` is
   constructed and started in production (`LootChestService.java:428`, `startCrackingMinigame`); its progress/complete
   entry points (`addProgress`, `complete`, `completeCracking`) have zero callers (C10) — `CrackingSessionTest.java:26-30`
   documents this. Nothing to port for the UI; the session always runs its timer to `FAILED`.

**Oriel ask:** the admin wand-preview screen (`LootChestWand` + `LootChestWandEditCommand`) needs
`PaginatedChestMenu`/`ChestMenuBuilder` — confirmed against WS2 census §4's capability matrix, not independently
re-derived from the Oriel repo itself (§13). Touch point 1 needs nothing from Oriel.

## 4. Steps

### G0 — Keystone hologram module (prereq, installs to `~/.m2`)
| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 1 | Keystone | new `keystone-hologram/pom.xml`, `<module>` in root `pom.xml:49` area | S | — | scaffold |
| 2 | Keystone | move `Hologram.java`, `HologramService.java` verbatim (package rename only) + new `registerProtection(JavaPlugin)` method | S | `HologramServiceTest` (new — currently **zero** tests on these 3 classes) | `mvn -pl keystone-hologram -am test` |
| 3 | Keystone | `HologramProtectionListener.java` — drop `@ListenerHandler`/DI ctor, plain POJO | S | `HologramProtectionListenerTest` (new) | same |
| 4 | Keystone | `keystone-plugin/pom.xml` dependency block, `docs/keystone-hologram.md`, `CLAUDE.md:101` amendment | S | — | `mvn clean install` → `Keystone-1.10.0.jar` |

Rollback: revert the branch; Gangland `0.9.1` is untouched (still on `hologram-api`).

**Prerequisite fact (not a WS3 step):** "Gangland G0" — the `0.10.0` branch's very first commit, orchestrator-owned
— bumps `GanglandApi.VERSION` and every existing `module.yml`'s `Host_Api` to `2.0` before any workstream's own
gates run. This plan's G4 (Messages/Settings deletion) and this module's `module.yml` (§2b) both assume it has
already landed.

### G1 — Gangland consumes Keystone hologram, deletes hologram-api
| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 5 | Gangland | `<keystone.version>1.10.0</keystone.version>`, new `keystone-hologram` dep in `gangland-ui/lootchest-api/pom.xml` (still the old module at this point) | S | — | compile check |
| 6 | Gangland | swap `org.luckyraven.gangland.hologram.*` imports → `org.luckyraven.keystone.hologram.*` in `ChestCooldownManager.java:11-12`, `LootChestManager.java` (impl), `GameplayConfig.java:267-269`, **and `LootChestService.java`** (C1 — calls `hologramService.clear()` in `clearChests()`, ~`:419`; missed in the first draft) | S | existing tests still green | `mvn clean install -DskipTests` reactor-wide |
| 7 | Gangland | delete `gangland-ui/hologram-api` (3 files), remove its `<module>` entry from `gangland-ui/pom.xml` | S | — | `mvn clean install` full reactor green |

Rollback: this gate is a pure import-path swap; revert the 4 files + pom line to restore `hologram-api`.

### G2 — Module skeleton + persistence
| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 8 | Gangland | scaffold `gangland-features/gangland-lootchest/pom.xml`, `module.yml` (`Host_Api: 2.0`), `LootChestModule.java` (mirrors `MailModule.java`) | M | `LootChestModuleTest` (mirrors `MailModuleTest`, kept per S3) | scaffold compiles |
| 9 | Gangland | move `LootChestService`, `ChestCooldownManager`, `data/*` (5 classes), `events/**` (9), `handler/**` (9), `item/LootItemReference`, `LootChestListener` verbatim | M | move 5 existing tests (`CrackingSessionTest`, `LootChestDataTest`, `LootTableTest`, `LootItemReferenceTest`, `TestItemParsers`) unchanged | `mvn -pl gangland-features/gangland-lootchest -am test` |
| 9b | Gangland | **new (C6):** `gangland-build/pom.xml` gains a `<artifactItem>` block (after `:148`) and a `<dependency>` block (after `:200`) for `gangland-lootchest` | S | `mvn -pl gangland-build package` produces `target/modules/gangland-lootchest-<rev>.jar` | build wiring only |
| 10 | Gangland | move `LootChestManager`, `LootChestWandTag` (registers lowercase tag names, C11), repository/table (unchanged table name — §6); **edit** `LootChestWandCommand`/`LootChestRemoveCommand` to take `RepositoryRegistry` instead of `GanglandDatabase` (B2) | M | — | same |
| 11 | Gangland | `LootChestModuleConfig`: hologram bean, `NbtTagCatalog` registry-injection, `LootChestLoader` bean calling `fileManager.registerInitializer(loader); fileManager.initializeAll();` inline (B1 — no `@PostConstruct`) | M | `LootChestModuleTest`/`LootChestSettingsTest` cover wiring; **no** separate `LootChestModuleConfigTest` (dropped, S3 — it would only assert beans the container already constructs at boot) | same |
| 12 | Gangland | `ItemConfig.java:131-138` loop deleted (empty catalog kept); `GameplayConfig.java:267-290` hologram/lootchest beans deleted — **`:313-319`'s `initializeLootChestLoader()` (renamed `initializeDeferredLoaders`) is kept, not deleted** (B1) | S | reactor `mvn clean install -DskipTests` | core still compiles standalone |

Rollback: module is additive until step 12 deletes core beans; revert step 12 alone (leaving `:313-319` untouched
either way, since it's never deleted) to restore a working (if duplicated) core + module split for debugging.

### G3 — GUI (after WS2 ships Oriel into the reactor)
| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 13 | Gangland | new `SharedLootInventory` (raw `Bukkit.createInventory`) replaces `InventoryHandler` in `LootChestService`/`LootChestSession` | M | **no new unit test** (S2 — a real Bukkit `Inventory` isn't unit-testable without a server; smoke row 2 is the check) | — |
| 14 | Gangland | `LootChestWand.java` admin preview **and `LootChestWandEditCommand.java`** rebuilt on Oriel `PaginatedChestMenu`/`ChestMenuBuilder`; `LootChestWandListener.java` updated call site, `Gangland` → `JavaPlugin`; **free side-effect fixes** for docket LS-30 (wand writes to held item not GUI item) and LS-31 (allowed-block substring match) since this file pair is rewritten wholesale anyway (D5 carve-out) | L | manual smoke (no unit-test seam for a real Bukkit inventory render) | `mvn -pl gangland-features/gangland-lootchest -am test` |

Rollback: G3 is isolated to the wand's UI layer; G2's gameplay (chest opening via `SharedLootInventory`, step 13)
works even if step 14 (admin preview) is reverted to a placeholder command-line fallback.

### G4 — Config, messages, commands
| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 15 | Gangland | module's own `LootChestSettings`/`GanglandLootChestMessages` read the module's own YAML; `LootChestSettingsProvider` gains **5 new reward-getter methods** it lacks today (C4 — money min/max, exp min/max, commands) so `LootChestEarnGoodsListener` no longer reads `Settings.*` statics directly | M (resized from S, C4) | new `LootChestSettingsTest` | — |
| 16 | Gangland | module `commands.json` (4 entries moved verbatim from `commands.json:466-481`), core `commands.json` entries deleted | S | schema check | — |
| 17 | Gangland | `settings.yml` `Loot_Chest:` block deleted (`:593` through the line before `Money_Drop:` at `:627`, C3); `Settings.java:209-214,690-706` fields/parse deleted; `Messages.java:330-355,575-578` constants deleted — **legal now, in this gate, not deferred to WS6** (B3/ruling 1: the major is already `2.0` as of Gangland G0) | M | `SettingsDefaultsTest`/message-key tests updated | `mvn clean install` reactor |

Rollback: config-only; revert the 3 deleted-block diffs to restore core-owned config if the module is pulled.

### G5 — Deletions, docs, docket, smoke
| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 18 | Gangland | delete `gangland-ui/lootchest-api` (whole module); pom cleanup at **4 locations** (C5): `gangland-ui/pom.xml` `<modules>`, `gangland-impl/pom.xml:55,79`, root `pom.xml:263,268` | S | — | full reactor `mvn clean package` |
| 19 | Gangland | docs sweep, widened (C8): `documentation/module-loader.md` module table + "Core seams" `NbtTagCatalog` note, `documentation/features/loot_chests.md` (whole feature doc — required), `documentation/developer/modules.md` (required); check `developer/{architecture,ui-framework,persistence,commands,configuration,dependency-injection,items}.md` and `documentation/tests/*` for stale references (possibly touched, not all confirmed to need edits) | M | — | docs only |
| 19b | Gangland | **new (C9):** write a `note` row (docket `write_db`) per touched id — all 21 `LS-*`/`UI-*` entries (§11) — recording the new file path post-move; "carried over as-is" is not the same as "recorded" | S | — | docket hygiene |
| 20 | Gangland | smoke rows (§7) on the test server | M | console harness | tag the wave |

### G6 — Follow-up patch (after WS5 + WS6 land, not blocking WS3's own gates)
| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 21 | Gangland | `gangland-lootchest/pom.xml` gains `provided`-scope `gangland-gang` dep; `module.yml` gains `Depends: [gang]` — needed for `User`/`UserManager`/`Level`/`LevelUpEvent` (4 types, C12) once WS5+WS6's content work removes the domain re-export from `gangland-api` | S | compile check only | — |

## 5. Config, messages, permissions

| Item | Count | From | To |
|---|---|---|---|
| `settings.yml` `Loot_Chest:` block | 1 block, `:593` through the line before `Money_Drop:` at `:627` (C3 — not `:593-621`) | `gangland-impl/src/main/resources/settings.yml` | module's own `loot_chest.yml` at jar root, same block-style keys |
| `Settings` fields/getters | **10** getters (not 11, C3) across 5 declaration lines (`Settings.java:209-214`), YAML parse (`:690-706`) | `gangland-api` | 5 covered today by `LootChestSettingsProvider`; **5 reward getters need new provider methods** (C4) — all 10 end up on the module's provider implementation |
| `Messages` constants | 26 (`Messages.java:330-355` player+hologram+time-unit, `:575-578` admin/command — C3) | `gangland-api` | module's own message YAML + `LootChestMessagesProvider` implementation (contract already exists) |
| `commands.json` entries | 4 (`lootchest`, `lootchest_help`, `lootchest_edit`, `lootchest_remove`, `commands.json:466-481`) | `gangland-impl/src/main/resources/commands.json` | module's own `commands.json` at jar root |
| NBT tags | 9 `LootChestWandTag` values, registered as **lowercase string names** (`tag.toString().toLowerCase()`, C11 — not the enum objects) | registered by core `ItemConfig.nbtTagCatalog()` | registered by module `@Bean` via registry injection (§3), same string form |
| Permission nodes | none found named `gangland.lootchest.*` in the files read — a grep-negative result over the specific files opened, not an exhaustive reactor search (§13); wand/remove/edit commands rely on the default `Command` permission wiring | — | — |

## 6. Persistence

- Table `loot_chest` (`LootChestTable.java:13`, 12 attributes) — **name and schema unchanged**, so **no migration
  needed**: existing rows load identically once the module's `RepositoryRegistry.scanAndRegisterRepositories`
  picks up the relocated `LootChestRepository`/`LootChestTable`. Do not add a `migrateSchema()`.
- `LootChestManager.initialize()` (`LootChestManager.java:56`) already calls `repository.setDataSupplier(this::getAllChests)`
  — the C7-contract rule is already satisfied, carries over unchanged.
- Autosave: `PeriodicalUpdates`'s `upsertAll()` batch picks up the module's repository through the same
  `RepositoryRegistry` the core uses — no module-specific autosave wiring needed.
- Shutdown: `LootChestManager.onClear()` (`LootChestManager.java:64-73`) already cancels sessions and clears
  chests/holograms; `HologramService.onShutdown()` (`HologramService.java:128-131`) still fires via `BeanLifecycle`
  regardless of source package (container-tracked, not scan-tracked).
- **`DataCleanupTask` (C13, closes the census's open question):** the SPI and its sole implementor were deleted
  with the weapon module — `PluginDataCleanupServiceTest.java:27-28` records this. Loot chests never participated
  and there is nothing to wire; the census's "TBD" is answered "not applicable, SPI gone."

## 7. Tests

| Existing test | Moves to | Notes |
|---|---|---|
| `CrackingSessionTest`, `LootChestDataTest`, `LootTableTest`, `LootItemReferenceTest`, `TestItemParsers` (5, `gangland-ui/lootchest-api/src/test`) | `gangland-features/gangland-lootchest/src/test` | unchanged bodies |
| none for hologram-api (0 tests today) | `keystone-hologram/src/test` | **new**: `HologramServiceTest` (create/update/remove/clear), `HologramProtectionListenerTest` (cancels manipulate/interact on a hologram-owned stand, no-ops otherwise) |
| none for `LootChestManager`/wand/commands/listeners (impl testing gap) | module | **new**: `LootChestModuleTest`, `LootChestSettingsTest`. **Dropped** (per review S2/S3): `LootChestSessionTest` (untestable without a live server) and `LootChestModuleConfigTest` (would only assert container wiring `mvn clean install` already proves) |

**Note on "genuinely red":** every new test in this plan (`HologramServiceTest`, `HologramProtectionListenerTest`,
`LootChestModuleTest`, `LootChestSettingsTest`) is a **green-on-arrival wiring test**, not a docket-pinned flip —
consistent with D5's "carry the 19 remaining bugs as-is" stance (§1, §9, §11). A gate reviewer should not go
looking for red here; the only red-to-green pins in this plan are the 5 existing tests that move unchanged (they
were already green before the move and stay green after).

**Smoke rows** (console-harness style, `brainstorming/bartizan-split-2026-09-08/smoke/`):
1. Boot with the module **absent**: `/glw lootchest` absent, no fault logged, core boots clean.
2. Boot with the module present: `/glw lootchest` gives the wand; right-click an allowed block places a chest;
   right-click again opens it (shared `SharedLootInventory`), take an item, close — cooldown hologram shows the
   `LOOT_CHEST_HOLOGRAM_COOLDOWN` text.
3. **Corrected (C7).** Cracking-enabled chest: confirm the session **always fails** after `Cracking_Time` seconds
   (`LootChestCrackingFailureEvent` then `LootChestCrackingEndEvent` fire) regardless of player action, since
   nothing calls `complete()`/`addProgress()` — this is the observable shape of docket LS-02, not a hang at
   `CRACKING_STARTED`.
4. `/glw reload`: chest registry + hologram set survive (config re-read, in-memory chests re-registered from DB).
5. Restart: chests persist (row survives, hologram re-spawns from DB `is_looted`/`respawn_time`).
6. `onDisable`: **no armor-stand leak** — check via a direct world scan
   (`world.getEntitiesByClass(ArmorStand.class)` count before/after a chest cooldown cycle + server stop) that
   `HologramService.clear()`/`onShutdown()` removed every stand; docket **UI-13**/**UI-15** already document
   related leaks.

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | Gang-domain import (`LootChestEarnGoodsListener`, now 4 types per C12) breaks after WS5+WS6's content work removes the domain re-export from `gangland-api` | G6 (§4) is a named, tracked follow-up patch, not silent breakage; flagged to WS5/WS6 planners (§10) |
| 2 | Oriel dependency shape (hard `depend:` vs. per-module `Plugins:`) is a WS2 decision this plan assumes but does not control | §3 states the assumption and the fallback explicitly; re-confirm against WS2's actual plan before G3 |
| 3 | `SharedLootInventory` (new raw-Bukkit wrapper, step 13) has no unit test (S2) — behavior parity with today's `InventoryHandler`-backed shared chest must be verified manually | Smoke row 2; keep the class under 30 lines so a manual read suffices as review |
| 4 | Armor-stand leak on shutdown (docket UI-13/UI-15) could be copied into `keystone-hologram` unchanged, since G0 moves the code verbatim | Smoke row 6 explicitly checks entity count; `HologramServiceTest` should assert `clear()` empties the internal maps |
| 5 | "Gangland G0" (the branch-wide `Host_Api`/`GanglandApi.VERSION` → `2.0` bump) is an orchestrator-owned prerequisite outside this plan's own gates — if it lands late or incorrectly, G4's `Messages`/`Settings` deletion (step 17) becomes an illegal removal from a still-1.x api | This plan's G4 explicitly states the dependency on Gangland G0 (§2b, §4); an executor should verify `GanglandApi.VERSION == "2.0"` before running step 17, not assume it |
| 6 | The module's own YAML file name could collide with an existing top-level Gangland resource if named `settings.yml` | Name the module's own settings file `loot_chest.yml` (not `settings.yml`) inside the module jar |

**Rollback story per gate:** each gate (G0-G5) ends in a green `mvn clean install` of the affected reactor; G1's
import swap and G2's bean deletion are the only two steps that touch files outside the new module/new Keystone
module, and both are single-file, easily-reverted diffs (§4 notes the exact rollback for each gate).

## 9. Decisions for the user

| # | Decision | Options | Recommendation |
|---|---|---|---|
| D1 | **Hologram → Keystone amends `CLAUDE.md:101`.** | (a) Amend the line, keep the exception narrow (hologram only, scoreboard stays put) (b) leave the line as written | (a), reviewer agrees — user's sentence is unambiguous. Amend the sentence only, leave the scoreboard half intact for WS1 |
| D2 | **`keystone-hologram` as its own Maven module vs. a package inside an existing bean-aware module.** | (a) new `keystone-hologram` module (b) package inside `keystone-common` (the cheaper fallback — **not** `keystone-npc`, corrected per S1: `keystone-npc`'s Citizens soft-dependency guard has nothing to do with armor stands) | (a), reviewer agrees (S1) — cost is one `pom.xml` + one `<module>` line + one `keystone-plugin` dependency block, and `keystone-common` has no `keystone-bean` dependency to lend `BeanLifecycle` |
| D3 | **Fold `lootchest-api` into the module vs. keep it as a shaded library.** | (a) fold in, one jar (b) keep as a library module | (a), reviewer agrees — no reactor consumer of lootchest-api types outside the move set |
| D4 | **Chest-opening view: raw Bukkit `Inventory` vs. Oriel `Menu`. RESOLVED, not open.** | (a) raw wrapper (b) force it through Oriel's `Menu` model | **(a), decided by orchestrator ruling (2):** WS3 owns `LootChestService`/`LootChestSession`; WS2 drops them from its port list. The reviewer agreed on the merits before this ruling landed (raw is correct; faking "allow everything" through Oriel's declarative model is more code for no behavior change) |
| D5 | **Scope: structural-only (Option A) vs. also fixing the 21 open loot-chest/hologram docket bugs.** | (a) carry all 21 over as-is (b) fold in the cheap fixes | **(a) + the reviewer's carve-out:** carry 19 over as-is; LS-30 and LS-31 get fixed for free in G3 step 14 since `LootChestWand`/`LootChestWandListener` are rewritten wholesale there anyway — not a separate step, not a broader bug-fix pass |
| D6a | **`Host_Api`/`GanglandApi.VERSION` line for this module.** | (a) `1.0` (the pre-review plan's choice, correct only if WS3 ships before any api-major bump) (b) `2.0` | **(b), orchestrator ruling (1):** "Gangland G0" bumps the line to `2.0` branch-wide before WS3's own gates run, so the module ships at `2.0` from its first commit — not `1.0` |
| D6b | **Timing of the `Messages`/`Settings` deletions (G4 step 17).** | (a) same gate as originally planned (b) reviewer's proposed fix: defer to WS6's own 2.0 sweep, keep the api members in place (optionally `@Deprecated`) until then | **(a), orchestrator ruling (1) overrides the reviewer's B3 fix:** because D6a already puts the major at `2.0` before G4 runs, the deletion is legal in this gate. (b) remains the safe fallback *only if* Gangland G0 has not actually landed by the time G4 executes — see Risk 5 |

## 10. WS6 asks / Oriel asks / Keystone asks

- **WS5 ask:** confirm the `gangland-gang` module artifact id/coordinates before G6 (§4) is scheduled, and land G6
  as part of WS5's own gate sequence (or immediately after).
- **WS6 ask:** the 9 loot-chest events are **not** requested for promotion into `gangland-api`'s facade (S4 —
  confirmed correct by review, no change) — no current or planned consumer needs them at compile time; a future
  module follows the `turf`→`civilians` precedent instead. **No longer asking WS6 to bump this module's
  `Host_Api`** (superseded — it ships at `2.0` from Gangland G0, D6a); instead, confirm Gangland G0 has actually
  landed on the branch before this module's `module.yml` is authored (G2 step 8), since D6b's legality depends on it.
- **WS2 ask (resolved by orchestrator ruling 2, confirm only):** `LootChestService`/`LootChestSession` are WS3's,
  not WS2's — confirm WS2's plan has dropped these 2 files from its port list (the orchestrator states it is
  telling WS2's planner directly). The **real** remaining Oriel ask (§3, GUI point 2): `PaginatedChestMenu`/
  `ChestMenuBuilder` for the admin wand-preview screen (`LootChestWand` + `LootChestWandEditCommand`) — confirmed
  present per WS2 census §4, not independently re-derived from the Oriel repo (§13). Also still open: confirm the
  Oriel dependency shape (hard `depend:` vs. per-module `Plugins:`) before G3.
- **Keystone ask:** none beyond G0 itself — `keystone-hologram`'s two dependencies (`keystone-common`,
  `keystone-bean`) are both already-shipped 1.9.2 modules.

## 11. Docket

**Correction to the Haiku census**, which reported "no matches for loot/chest/cracking/hologram in the bug
docket findings" (WS3 census §10). That grep missed `brainstorming/bug-docket-2026-09-06/triage/lootchests-signs-waypoints.txt`,
whose entries build into docket ids `LS-*` and `UI-*`. Cross-checked against the live docket's shared DB (61 rows
currently written — a missing row means **open**, per CLAUDE.md's docket rule). **New this revision (C9):** G5
step 19b writes a `note` row per id below recording the file's new path post-move — carrying a bug over silently
is not the same as recording it moved.

| Id | Tier | Status | Title | Relevance to this plan |
|---|---|---|---|---|
| UI-13 | P2 | open | Chest hologram removal bypasses HologramService and leaks map entries | in-path (moved in G0) |
| UI-14 | P1 | open | Holograms vanish after a chunk unload and never respawn | in-path |
| UI-15 | P3 | open | `hologramsByLocation` keyed by the caller's mutable `Location` | in-path |
| UI-33 | P3 | open | `HologramProtectionListener` linear scan per interaction | in-path — carried over as-is (D5) |
| LS-02 | P2 | open | Cracking mini-game is unreachable (dead feature) | confirms §1/§3's own finding (sharpened per C10: the session construction path is live, only `addProgress`/`complete`/`completeCracking` are dead); carried over, feature deferred |
| LS-05 | P2 | open | Respawn time written as long, read as int (always 300) | carried over |
| LS-11 | P2 | open | Looted chests get no hologram/timer after restart | carried over |
| LS-12 | P2 | open | Null world NPEs in hologram spawn | `Hologram.spawn()` already null-guards `getWorld()` (`Hologram.java:44`) — re-verify before assuming fixed elsewhere (§13) |
| LS-13 | P2 | open | First viewer's close starts cooldown; respawn re-syncs stale viewers | carried over |
| LS-14 | P2 | open | Removing a chest leaves active sessions | carried over (D5 — not in the carve-out, only LS-30/31 are) |
| LS-15 | P2 | open | Quitting with a chest open never starts the cooldown | carried over |
| LS-16 | P2 | open | `Allowed_Tiers`/`tierId` never applied | carried over; test-pinned by `LootTableTest`, which moves with the module in G2 step 9 |
| LS-17 | P2 | open | `Minimum >= Maximum` throws inside the handler | carried over |
| LS-18 | P3 | open | `openedLootChests` keyed by `Player`, rewards on open without taking | carried over |
| LS-23 | P3 | open | Loot chest events are `Cancellable` but never checked | carried over |
| LS-25 | P2 | open | Async `CountdownTimer` callbacks mutate plain `HashMap`s | `LootChestManager`'s own `CountdownTimer` already uses `.start(false)` (sync, `LootChestManager.java:53`) — likely a different call site; re-verify (§13) |
| LS-29 | P3 | open | `Countdown_Timer` config key unread | carried over |
| LS-30 | P3 | open | Wand writes to the held item, not the GUI's item | **fixed as a free side effect of G3 step 14** (D5 carve-out, reviewer S-column) |
| LS-31 | P3 | open | Allowed-block substring match (`LootChestWandListener.java:73`) | **fixed as a free side effect of G3 step 14** (D5 carve-out) |
| LS-32 | P3 | open | Zero-weight tables always return the last entry | carried over; test-pinned by `LootTableTest` |
| LS-33 | P3 | open | Bulk actions identical to single clicks | carried over |
| T-11 | P1 | **fixed** | `GameplayConfig.lootChestLoader` eager `initializeAll()` ordering | already fixed (0.9.0); carries forward unchanged — its fix is exactly the `:313-319` method B1 keeps in the core |

None of the 21 open entries are P0/blocking for a structural move except UI-14 (P1) — none require a design
change to this plan's target layout.

## 12. Estimate

**Revised to ~6 executor-days** (up from the pre-review ~4.75, per the review's estimate check). 24 steps across 6
gates (G0-G6, including the two new steps 9b and 19b). Size mix: 15 S, 8 M, 1 L. Added since the first draft:
`gangland-build` two blocks + four pom cleanups (S each), the command rewiring in B2 (S, but real edit work, not a
verbatim move), C4's five new provider methods (S→M), the docs sweep at real scope (M, not S), the docket
note-row pass (S). B3's original "split G4 into a now-half and a WS6-half" concern is **resolved differently**:
Gangland G0 removes the need to split G4 at all (D6b), so that specific cost is avoided, but the other five
additions above are real and net the wave out to roughly a day more than originally estimated. G3's L step
(Oriel wand rebuild) now explicitly carries `LootChestWandEditCommand` too, so the "540 LoC" figure remains a
floor, not the whole size of that step. **G3 is blocked on WS2 shipping Oriel; G6 is blocked on WS5 + WS6.**

Per-gate: G0 ~0.5 day, G1 ~0.5 day, G2 ~1.25 days (9b + the B2 command edits add to the original 1 day), G3 ~1.75
days (the confirmed-larger L step), G4 ~0.75 day (C4's provider methods), G5 ~0.75 day (wider docs sweep + docket
pass), G6 ~0.25 day (post-WS5/WS6, unblocked estimate only).

## 13. Not verified

- **Keystone graphify was not re-run this session** for hologram-adjacent queries — `keystone-npc/pom.xml` and its
  source tree were read directly via Grep/Read rather than `graphify query` in that repo. The Opus review's own
  graph pass (dated after this plan's first draft) confirms freshness and re-verified every cited line
  independently, which substitutes for this gap for the facts it checked, but the executor should still run
  `graphify explain "NpcSupport"`/`"AbstractNpc"` in Keystone before G0 as a final check.
- **WS2's actual Oriel dependency shape** (hard `depend:` vs. per-module `Plugins:`) is assumed, not read from a
  WS2 plan file. The reviewer flagged the same gap independently and additionally noted WS2 §2 shows `gangland-api`
  gaining `oriel-core`/`oriel-chest` at `provided` scope, which may mean the real answer is "nothing — Oriel
  arrives via the api" rather than either of §3's two options. Confirm before G3.
- **LS-12/LS-25** may already be partially mitigated by code read in this session (`Hologram.java:44`'s null
  guard; `LootChestManager.java:53`'s `.start(false)`) — the docket entries may point at a different call site not
  read here (e.g. inside `ChestCooldownManager`'s own timer, which was read and found synchronous too — so
  LS-25's description doesn't match either checked call site). Grep every `CountdownTimer`/
  `runTaskTimerAsynchronously` use in the lootchest files before G2, not just the two checked.
- **`Money_Drop:` settings.yml block** (immediately after `Loot_Chest:`, banner now confirmed at `:627` not
  `:625`, C3) is unrelated (money-drop-on-death, not loot chests) — confirmed by name only; do not delete it in
  G4 step 17.
- **Permission node table (§5)** — no `gangland.lootchest.*` node was found by name in the files read; this is a
  grep-negative result over specific files, not an exhaustive reactor search. Grep `gangland.lootchest` and
  `gangland.command.lootchest` across the full reactor before finalizing G4's permission table.
- **`keystone-hologram`'s exact Maven coordinates relative to `<revision>`** — assumed to follow the same
  `${revision}` parent-POM pattern every other `keystone-*` module uses.
- **Oriel's `PaginatedChestMenu`/`ChestMenuBuilder`** — neither this plan nor the Opus review independently opened
  the Oriel repo; the claim that they cover the wand preview rests entirely on WS2 census §4.
- **Whether any other CONFIG-phase bean besides `LootChestLoader`/`InventoryLoader` registers a `FileLoader`**
  that depends on the kept `GameplayConfig.java:313-319` global `initializeAll()` — B1's fix is safe regardless
  (the method is kept, not deleted), but the full blast radius of the original (wrong) deletion was never fully
  mapped.
