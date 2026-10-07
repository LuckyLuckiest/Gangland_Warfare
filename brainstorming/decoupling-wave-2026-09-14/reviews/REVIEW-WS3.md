# REVIEW WS3 — Loot chests → runtime module; holograms → Keystone

**Reviewer:** Opus, 2026-09-14. Graphs fresh in both repos (Gangland `graph.json` 18:43 > commit 18:10; Keystone
18:19 > commit 18:05). Verified with `graphify affected` on `LootChestData`/`LootChestManager`/`LootChestWandTag`,
then raw reads at every cited line.

Verdict: **PASS WITH FIXES**

The bones are right and the plan is unusually honest: all four census corrections hold up under verification, the
GUI triage (raw chest vs. real menu vs. nonexistent minigame) is the single best call in the document, and §13 is a
real not-verified list rather than decoration. Four things must be fixed before an executor starts — one of them
(B1) would break every YAML loader in the core.

## Blockers (must fix before executors start)

- **B1. Step 12 and §2 delete the core's only deferred global `initializeAll()`.** §2 "Deleted" names
  `GameplayConfig.java:267-290,313-319`; step 12 widens it to `263-319`. Verified line map:
  `hologramService()` 267-270, `lootChestManager()` 272-277, `lootChestService()` 279-281, `lootChestLoader()`
  284-290, `initializeInventoryLoader()` 299-304, **`initializeLootChestLoader()` 313-319** — whose body is
  `fileManager.initializeAll()` (`GameplayConfig.java:317`), not a lootchest-specific call. It is the T-11 fix and
  the only CONFIG-phase `initializeAll()` in the core (`grep initializeAll()`: the other core site is the FILE-phase
  hook at `GanglandContext.java:171`, which runs *before* CONFIG beans exist). Deleting `313-319` silently stops
  every CONFIG-phase-registered `FileLoader` from initializing; deleting `263-319` additionally eats
  `initializeInventoryLoader()`, which is WS2's, not WS3's. **Required change:** delete only `267-290`; keep the
  `@PostConstruct` (rename to `initializeDeferredLoaders`) in the core, and have the module call
  `fileManager.initializeAll()` itself — the precedent already exists twice:
  `CopsNCrooksModuleConfig.java:301`, `CiviliansModuleConfig.java:54`.

- **B2. The three commands cannot move "unchanged" — they inject a type the api deliberately excludes.**
  §2's move table says `command/sub/lootchest/*` (3) move unchanged. But `LootChestWandCommand.java:11,22,26,62`
  and `LootChestRemoveCommand.java:12,22,25,28,59` take `org.luckyraven.gangland.database.GanglandDatabase`, which
  lives in `gangland-impl/.../database/GanglandDatabase.java` and which the repo CLAUDE.md lists under **"Not in the
  api, by design"**. A module compiling against `gangland-api` only cannot import it. Both use it for exactly one
  thing — `ganglandDatabase.getRepositoryRegistry()` — so the fix is one ctor param each: inject Keystone's
  `RepositoryRegistry` directly. Separately, `LootChestWandEditCommand.java:13` imports
  `org.luckyraven.gangland.inventory.part.Fill`, so that command is an inventory-api consumer too and belongs in
  G3's Oriel step, not in G2's "unchanged" list.

- **B3. G4 step 17 removes public members from `gangland-api` while the api is still 1.x.** It deletes 26 `Messages`
  constants and the `Settings` lootchest block, but D6 deliberately ships `Host_Api: 1.0` and C1 puts WS6's 2.0 bump
  *after* WS3. The repo CLAUDE.md contract rule is explicit: "Within a major of `GanglandApi.VERSION`, `gangland-api`
  only **adds**: no removal, rename or signature change of a public member." C4/C6 assign these removals to the 2.0
  major. **Required change:** split step 17 — G4 deletes the `settings.yml` `Loot_Chest:` block (593-626) and
  rewires the module onto its own YAML now; the `Messages`/`Settings` members stay in place (unread, optionally
  `@Deprecated`) until WS6's 2.0 sweep deletes them. Add this to the §10 WS6 ask, which currently only asks WS6 to
  bump the module's `Host_Api` line.

- **B4. WS3 and WS2 both claim `LootChestService`/`LootChestSession`, and they disagree.** WS2 §1 ("Out") says:
  *"WS2 only removes lootchest-api's 2-file inventory-api dependency (`LootChestService.java`,
  `LootChestSession.java`) so WS3 doesn't inherit a dead import"*, and WS2 §2's dependency table adds
  `oriel-core`, `oriel-chest` to `gangland-ui/lootchest-api/pom.xml` to "replace its 2-file `InventoryHandler`
  usage". WS3 G3 step 13 replaces those same two files' inventory with a raw-Bukkit `SharedLootInventory` and §3
  states "Oriel ask: none". Under C1's order WS2 runs first, ports both files to Oriel, and WS3 then rips the port
  out. **Required change:** settle it before WS2 executes and record the outcome in both plans. On the merits WS3 is
  right (see S2), so WS2's line should become "leave `LootChestService`/`LootChestSession` on `InventoryHandler`;
  WS3 replaces it with a raw Bukkit inventory" — and that *is* an Oriel/WS2 ask, so §10's "none" must change.

## Corrections (fix in place)

- **C1. G1 step 6's import-swap list is missing a file.** Repo-wide grep for `HologramService|gangland.hologram`
  returns `GameplayConfig.java`, `LootChestManager.java`, `ChestCooldownManager.java` **and
  `gangland-ui/lootchest-api/.../LootChestService.java`** (which calls `hologramService.clear()` in
  `clearChests()`, ~:419). Step 6 names only the first three.
- **C2. The `@ListenerHandler`-across-plugins reasoning is wrong; the conclusion is right.**
  `ListenerService.scanAndRegisterListeners(String basePackage, ClassLoader)` exists
  (`keystone-bean/.../listener/ListenerService.java:173`) — Gangland *could* pass
  `HologramService.class.getClassLoader()` and the scan would enumerate Keystone.jar fine. So "cannot see it" is
  overstated. Keep `registerProtection(JavaPlugin)` (it is the lazier option and needs no scan), but restate the
  justification. The precedent claim is actually *stronger* than the plan says: `keystone-npc` ships **zero**
  `implements Listener` classes (grep-verified).
- **C3. Counts and ranges.** `Messages`: 26 confirmed, but at `Messages.java:330-355` + `:575-578`, not
  `329-355,574-578`. `Settings`: **10** getters (not 11) across 5 declaration lines, `Settings.java:209-214`; parse
  block `:690-706` (not 689). `settings.yml`: `Loot_Chest:` starts at `:593`, `Money_Drop:` at `:627` — the plan's
  `:593-621` / "`Money_Drop:` at `:625`" is off by a few lines of comment banner (the §13 warning not to delete
  `Money_Drop:` is correct and worth keeping).
- **C4. The settings contract does not cover half the settings.** `LootChestSettingsProvider` (`.../config/
  LootChestSettingsProvider.java:5-31`) declares 7 methods: countdown timer, 3 sounds, allowed blocks, and defaulted
  `isCrackingEnabled`/`getCrackingTime`. It has **no** reward getters. `LootChestEarnGoodsListener` reads
  `Settings.getLootChestRewardMoneyMinimum/Maximum`, `…ExperienceMinimum/Maximum` and `getLootChestRewardCommands()`
  as statics. §5's "contract already exists and is kept" is true for 5 of the 10 settings only; the other 5 need new
  provider methods (or the listener reads the module's own config bean). Size step 15 accordingly.
- **C5. Pom cleanup is under-listed.** The plan names only `gangland-ui/pom.xml`'s `<modules>`. Also required:
  `gangland-impl/pom.xml:55` (hologram-api) and `:79` (lootchest-api), plus root `pom.xml:263` and `:268`
  (dependencyManagement entries for both).
- **C6. `gangland-build` does not pick modules up automatically.** `gangland-build/pom.xml` enumerates each module
  **twice** — the dependency-plugin copy list at `:123-148` and `<dependencies>` at `:170-200` (six modules, one
  block each). §2's "copied by `gangland-build`'s existing assembly step" is wrong; `gangland-lootchest` needs two
  new blocks. Add it as a step in G2 or G5.
- **C7. Smoke row 3 asserts behaviour the code does not have.** It expects a cracking chest to "stay stuck at
  `CRACKING_STARTED` forever". `CrackingSession.start` (`CrackingSession.java:64-81`) runs a 20-tick timer that sets
  `FAILED` and fires `onFailed` once `timeRemaining <= 0`. The real dead-feature symptom is "the session always
  **fails** after `Cracking_Time` seconds because nothing ever calls `complete()`/`addProgress()`". Fix the row or
  it will pass for the wrong reason.
- **C8. Docs sweep is under-scoped.** G5 step 19 names `module-loader.md` and FRONT-PAGE only. Lootchest/hologram
  also appear in `documentation/features/loot_chests.md` (a whole feature doc) and in
  `documentation/developer/{architecture,ui-framework,persistence,modules,commands,configuration,dependency-injection,items}.md`
  plus `documentation/tests/*`. At minimum `features/loot_chests.md` and `developer/modules.md` must change.
- **C9. Docket hygiene (C8 contract) is incomplete.** Every LS-*/UI-* entry's evidence points at
  `gangland-ui/lootchest-api/**` or `gangland-ui/hologram-api/**`; all 21 go stale on the move. Two of them are
  test-pinned by tests that move with the module (LS-16 and LS-32 → `LootTableTest`; LS-02 → `CrackingSessionTest`,
  both confirmed in the triage source). The plan must add a step that writes a `note` row per touched id recording
  the new path — "carried over as-is" is not the same as "left unrecorded".
- **C10. Sharpen census correction (b).** `CrackingSession` **is** constructed in production —
  `LootChestService.java:428` inside `startCrackingMinigame(...)` — and `LootChestService.completeCracking(Player)`
  exists at `:273`. What has zero callers is the progress/complete entry point. §1's prose already says this
  correctly; the phrase "zero production callers anywhere in the reactor" should say "zero callers of
  `addProgress`/`complete`/`completeCracking`" so an executor doesn't delete the construction path as dead.
- **C11. NBT key shape.** `ItemConfig.java:132-138` registers `tag.toString().toLowerCase()` — the lowercase
  **name**, not the enum object. §3/§5 say "registers the 9 `LootChestWandTag` values"; the module must reproduce the
  lowercase-string form or existing wands stop resolving.
- **C12. Gang-seam import list is incomplete.** §3 names `User`/`UserManager`/`Level`. `LootChestEarnGoodsListener`
  also imports `org.luckyraven.gangland.gang.events.level.LevelUpEvent` (`:11`). `UserLevelUpEvent` (`:8`) is safe —
  it already lives in `gangland-api/.../events/user/UserLevelUpEvent.java`. G6's "two-line patch" still holds, but
  list four types, not three.
- **C13. Answer the census's open question.** Census §4 left "DataCleanupTask: TBD if lootchests participate". The
  answer is that the SPI no longer exists — `PluginDataCleanupServiceTest.java:27-28` records that the
  `DataCleanupTask` SPI and its sole implementor died with the weapon module. One line in §6 closes it.
- **C14. No 1.16-floor note on the moved hologram.** Keystone's `CLAUDE.md` (same "Hard design rules" block as
  line 101) sets **API floor Spigot 1.16.5 — "Never reference newer Bukkit API from Keystone code"**. `Hologram` is
  ArmorStand-only, so the move is compliant, but the plan should carry a `ponytail:` comment naming the ceiling and
  the upgrade path (`TextDisplay` on 1.19.4+ behind `NmsVersion`, never a bare import). Shared-classloader rule is
  satisfied as designed: `HologramService` has no statics (verified, `HologramService.java:18-133`) and `Hologram`'s
  only static is the `LINE_HEIGHT` constant (`Hologram.java:22`) — server-global, allowed.

## Simplifications (ponytail)

- **S1. `keystone-hologram` as a module for 3 classes — argued both ways, planner's (a) wins.** Against: 346 LoC and
  two dependencies (`keystone-common`, `keystone-bean`) is under the "new Maven module for <5 classes" smell, and a
  `org.luckyraven.keystone.hologram` package inside `keystone-common` would cost zero new build units. For:
  `keystone-plugin` shades per-module (`keystone-plugin/pom.xml:88-91` is exactly the one-block cost the plan
  claims — verified), a module is the unit a consumer's pom names, and `keystone-common` has no `keystone-bean`
  dependency to lend `BeanLifecycle`. **Keep (a).** Reject D2's option (b) firmly: never `keystone-npc` — its
  Citizens soft-dependency guard has nothing to do with armor stands.
- **S2. `SharedLootInventory` is the right call and should stay ~20 lines.** `LootChestSession.java:20,38` and
  `LootChestService.java:553,576` use `InventoryHandler` as a shared, free-form container keyed per chest
  (`sharedChestInventories.get(chestId)`), and `LootChestListener.java` handles `InventoryClickEvent`/
  `InventoryCloseEvent` itself — there is no declarative slot model to port. `Bukkit.createInventory` is the stdlib
  rung. But **drop the new `LootChestSessionTest`** the plan adds for it (step 13): a real Bukkit `Inventory` is not
  unit-testable without a server, so the test can only assert field plumbing. Smoke row 2 is the real check.
- **S3. Three new wiring tests where `mvn clean install` + smoke rows 1-2 already prove the wiring.**
  `LootChestModuleTest` (mirrors `MailModuleTest`, keep — house precedent), `LootChestSettingsTest` (keep — real
  parsing logic, and it is the flip target if LS-29 is ever fixed), `LootChestModuleConfigTest` (**drop** — it
  asserts that beans the container already constructs at boot get constructed).
- **S4. Not promoting the 9 events into `gangland-api` (§10) is correct** — no consumer, and the
  `turf`→`civilians` `provided`+`Depends:` precedent is the right escape hatch. No change.

## Missing consumers found by graphify affected

| Type moved | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `HologramService` | `LootChestService` (calls `hologramService.clear()`) | `gangland-ui/lootchest-api/.../LootChestService.java` (import + `clearChests()`) | G1 step 6 import swap fails to compile |
| `GanglandDatabase` (stays) | `LootChestWandCommand`, `LootChestRemoveCommand` | `LootChestWandCommand.java:11,62`; `LootChestRemoveCommand.java:12,59` | B2 — module cannot import it |
| `InventoryHandler` / `Fill` | `LootChestWandEditCommand` | `LootChestWandEditCommand.java:13` | listed as "unchanged"; is an inventory-api consumer |
| `lootchest-api` / `hologram-api` artifacts | `gangland-impl/pom.xml`, root `pom.xml` | `gangland-impl/pom.xml:55,79`; `pom.xml:263,268` | reactor fails after module deletion |
| new module jar | `gangland-build` copy + dependency lists | `gangland-build/pom.xml:123-148,170-200` | jar never reaches `target/modules/` |
| `LootChestWandTag` | `ItemConfig.nbtTagCatalog()` (graphify `affected` returned **no** nodes — enum-value edges are not tracked; grep found it) | `ItemConfig.java:132-138` | plan has it right; noting the graph blind spot |
| `Settings.getLootChestReward*` | `LootChestEarnGoodsListener` | reward block in `listener/loot/LootChestEarnGoodsListener.java` | C4 — no provider method exists |

Confirmed clean: no consumer of `LootItemReference` outside lootchest (`LootChestWand.java:22,428,443,489,504` only);
no `ItemVocabulary`/Bartizan symbol in the moved files; no external consumer of hologram beyond the four files above.

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| D1 CLAUDE.md:101 amendment | (a) amend, keep narrow | **Agree** | Rule text verified in Keystone `CLAUDE.md` "Hard design rules"; user's sentence is unambiguous. Amend the sentence only, leave the scoreboard half intact for WS1. |
| D2 new Keystone module vs package | (a) new module | **Agree**, see S1 | Cost verified (one `<module>`, one `keystone-plugin/pom.xml:88-91`-shaped block). Name `keystone-common` as the cheaper fallback, not `keystone-npc`. |
| D3 fold `lootchest-api` in | (a) fold | **Agree** | No reactor consumer of lootchest-api types outside the move set; a library jar behind a module jar is pure ceremony. |
| D4 raw chest vs Oriel menu | (a) raw | **Agree on the merits, but it is no longer WS3's decision alone** | B4: WS2 has already scheduled the Oriel port of the same two files. Surface it as a WS2/WS3 arbitration, with (a) as the recommendation. |
| D5 carry 21 docket bugs vs fix cheap ones | (a) carry | **Agree with one carve-out** | LS-30 and LS-31 sit in `LootChestWand`/`LootChestWandListener`, both rewritten wholesale in G3 step 14 — fixing them there is genuinely free and flipping nothing. Recommend (a) + those two, and say so rather than leaving it fully open. |
| D6 `Host_Api: 1.0` now | (a) ship at 1.0 | **Agree on the version, disagree on the package** | Shipping 1.0 is right, but B3: the same wave deletes api members, which only 2.0 permits. Keep 1.0 *and* defer the api deletions to WS6. |

## Estimate check

~4.75 executor-days is optimistic by roughly a day once the above land: `gangland-build` two blocks + four pom
cleanups (S each), the command rewiring in B2 (S, but it moves work out of "verbatim move" into "edit"), C4's five
new provider methods (S→M), the docs sweep at real scope (M, not S), the docket path pass (S), and B3 splitting
G4 into a now-half and a WS6-half. Call it **~6 days**, with G3 fully blocked until B4 is arbitrated — and note
that G3's L step also inherits `LootChestWandEditCommand`, so the "540 LoC" figure for the Oriel rebuild is low.

Gate independence is otherwise good: G0 installs to `~/.m2` and leaves Gangland untouched; G1 is a pure import
swap; G2 is additive until step 12 (which B1 shrinks); G4/G5 are config and deletion. Rollback per gate is
stated and credible. The "genuinely red" rule is not addressed anywhere — every new test in §7 is a
green-on-arrival wiring test rather than a docket flip, which is consistent with D5(a), but say so explicitly so a
reviewer at the gate does not go looking for red.

## Things I could not verify

- **Oriel's `PaginatedChestMenu`/`ChestMenuBuilder`** — I did not open the Oriel repo; §3's claim that they cover
  the wand preview rests on WS2's census §4, which I did not independently re-derive.
- **WS2's Oriel dependency shape** — WS2 §2 shows `gangland-api` gaining `oriel-core`/`oriel-chest` at `provided`
  and re-exporting them, which suggests compile-time availability rather than WS3 §3's assumed `ServicesManager`
  lazy lookup. I did not read far enough into WS2 to find its `plugin.yml depend:` decision. WS3's §13 flag stands;
  the fallback sentence may need to change from "`Plugins: [Oriel]`" to "nothing — it arrives via the api".
- **LS-05/23/25 wording** verified against the triage source (entries 5, 23, 25 match the plan's titles verbatim);
  I did **not** re-read the live docket DB, so "open" status per id is the plan's `read_db` result, not mine. The
  missing-row-means-open rule makes that safe either way.
- **Whether any other CONFIG-phase bean registers a `FileLoader`** that depends on `GameplayConfig.java:317`'s
  global `initializeAll()` — B1's fix is safe regardless, but the blast radius of the original deletion is
  "at least the lootchest loader, possibly more".
