# Bartizan wave — weapons become a standalone plugin, item + NPC infrastructure move to Keystone

Opened 2026-09-08 (session cb80e892) right after the module split sprint (`../module-split-2026-09-07/`, all five
features are runtime modules on branch `0.8.4`). Orchestrator: the Fable session that owns this folder. Explorers,
architects and reviewers: **Opus** (`feature-dev:code-explorer`, `feature-dev:code-architect`,
`feature-dev:code-reviewer`). Executors: **Sonnet** `claude` agents with a role-specific prompt, one task group each.
Never a plain general-purpose agent.

**Artifact page:** https://claude.ai/code/artifact/a680bf26-64d7-46f5-bc88-a26cc28845e9 ("Bartizan Wave", db capability: collections `phases` A..E, `smoke` S1..S8, `findings` T-12..T-18, `feedback` one doc per agent report; regenerate the page with `build_board.py` and republish passing `url`). Bug docket: https://claude.ai/code/artifact/4102fb1f-20b0-44e9-b1b7-2893a559fa04.

## Decisions (user, 2026-09-08)

| # | Decision |
|---|---|
| D1 | The weapons plugin is called **Bartizan** (architecture family with Keystone/Oriel; no Spigot/Modrinth/Hangar collision; QualityArmory, WeaponMechanics, CrackShot, Gunshell, Awesome Guns, Guns Plugin exist; Armory/Bastion/Arsenal are taken). |
| D2 | New sibling repo `E:\Programming\java\Bartizan`, **Maven** like Keystone (`${revision}` + flatten, Central profile, groupId `org.luckyraven.bartizan`, api + plugin modules), version **0.1.0**, `plugin.yml depend: [Keystone]`. |
| D3 | The generic parts of `gangland-infra/gangland-item` are **promoted to Keystone `keystone-item`** (Keystone **1.9.0**). Gangland, Oriel and Bartizan share one copy. |
| D4 | New **`keystone-npc`** module (AbstractNpc, EntitySpawner, navigation + combat delegates, difficulty, spawn config, the `SHOULD_SAVE=false` rule) wrapping Citizens as a **soft dependency everywhere** (Keystone `softdepend`, Gangland core `softdepend`; NPC-owning modules skip with a readable fault when Citizens is absent). |
| D5 | Turf NPCs (`copsncrooks/npc/turf/**`, `TurfNpcsModuleConfig`, `TurfNpcsConfigLoader`, `TurfNpcContractImpl`, `turf/turf_npcs.yml`, the `TurfPowerupNpc` repository/table) move to **gangland-turf**; turf never depends on cops. |
| D6 | Traders + bankers move out of cops-n-crooks into their own runtime module **`gangland-npc-shops`** (id `npcshops`). Cops + civilians stay in cops-n-crooks. |
| D7 | **No bridge module.** Gangland 0.9.0 drops weapons entirely: `/glw weapon`, `/glw ammo`, `/glw item wearable`, `/glw debug weapon`, the weapon `Settings` sections, `weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml`, the weapon/ammo/wearable sign types, `gangland-compatibility/version-*` (recoil adapters) all leave the Gangland repo. Signs, shops and loot chests reach weapons only through Keystone's item registries (Bartizan registers `weapon:` / `ammo:` / `wearable:` converters there). |
| D8 | cops-n-crooks and gadget compile against `bartizan-api` (provided) and declare a plugin-level dependency in `module.yml` through a new Keystone 1.9.0 descriptor key; a module whose plugin is missing is skipped with a readable fault. |
| D9 | Versions: Gangland **0.9.0** (new branch off `0.8.4`; a core feature and its settings are removed, so minor not patch), Keystone **1.9.0** (new phase branch), Bartizan **0.1.0**. Commit at gate boundaries; **never push** without the user. |
| D10 | Smoke tests are **console-only**, driven by agents on `E:\Documents\Minecraft\Test Server` through `ServerStartDebug.bat` (Paper 1.21.11, JDWP 5005). In-game checks are listed for the user. `0.8.4` is smoke-fixed **before** the split starts. |

## Target topology

```
plugins/
├── Keystone-1.9.0.jar              + keystone-item (promoted from gangland-item), keystone-npc (Citizens soft)
├── Bartizan-0.1.0.jar              standalone weapons plugin: weapons, ammo, wearables, projectiles, recoil NMS adapters
├── Gangland_Warfare-0.9.0.jar      core, weapon-free
└── Gangland_Warfare/modules/                                       (six jars, not five — PICK refinement 2)
    ├── gangland-mail-0.9.0.jar          mail, gang invites, alliance requests — no Depends, no Plugins
    ├── gangland-turf-0.9.0.jar          turf capture + turf NPCs (Quartermaster/garrison defender), Depends: [civilians]
    ├── gangland-civilians-0.9.0.jar     civilian NPCs, NpcMarkManager/CombatEligibility/target-filter seams, Plugins: [Bartizan]
    ├── gangland-npc-shops-0.9.0.jar     traders + bankers (needs keystone-npc + Citizens) — no Depends, no Plugins
    ├── cops-n-crooks-0.9.0.jar          cops only (civilians split out), Depends: [turf, civilians]; Plugins: [Bartizan]
    └── gangland-gadget-0.9.0.jar        cars + jetpacks, Plugins: [Bartizan]
```

Compile direction: Gangland modules → `bartizan-api` (provided) → Keystone. Bartizan → Keystone only. Keystone → nothing
of ours. Bartizan never names a Gangland type; the Gangland core never names a Bartizan type.

## Phases and gates

| Phase | What | Gate |
|---|---|---|
| **A · 0.8.4 stabilisation** | file the smoke findings (docket T-12..T-18), fix them (Keystone 1.8.1 hotfix + Gangland 0.8.4), build the console smoke harness, run the matrix below, commit `0.8.4` | GA: every matrix row boots with 0 Gangland/Keystone ERRORs and only the WARNs the row expects |
| **B · exploration + architecture** (feature-dev phases 2–4) | three Opus explorers (weapon boundary, item + module loader infra, NPC infra), then three Opus architects (minimal / clean / pragmatic), orchestrator picks, user confirms | GB: user picks an approach |
| **C · checklists + execution** | Opus planners write one executor checklist per stream (`keystone-1.9.0.md`, `bartizan.md`, `gangland-0.9.0.md`), consistency review, Sonnet executors run task groups in order Keystone → Bartizan → Gangland, Opus reviewers per gate | GC: reactor builds green in all three repos, tests green, jars assembled |
| **D · smoke of the new topology** | harness matrix with Keystone 1.9.0 + Bartizan + Gangland 0.9.0 + modules; findings → docket → fix agents | GD: same as GA on the new topology |
| **E · wrap-up** | docs, memory, `graphify update . --force` in all three repos, artifact final, commits at gates | — |

## Smoke matrix (phase A, and again in phase D)

| Row | modules/ content | Expect |
|---|---|---|
| S1 | empty | boots, `Runtime modules: 0 loaded, 0 fault(s)`, `/glw help` lists only core commands |
| S2 | weapon | boots, `Loaded module weapon`, `/glw weapon list` answers |
| S3 | weapon + gadget | both load, `/glw car` answers |
| S4 | turf + weapon + cops | all three load, `/glw cops`, `/glw turf` answer, detainment table migrates |
| S5 | all five | 5 loaded, 0 faults, `/glw help` count matches |
| S6 | cops without turf | cops skipped with `module.dependency.missing`, server still boots |
| S7 | a copy of weapon with `Host_Api: 0.7` | skipped with a readable fault |
| S8 | S5 + `/glw reload` | modules keep answering, no duplicate listener errors |
| Every row | `stop` | `Disabling`, `onDisabled` per module, no classloader errors, no fault spam |

Harness: `smoke/smoke.py` (see `smoke/README.md`). Reports land in `smoke/reports/<date>-<row>.md`.

## Findings from the user's runs last night (2026-09-08 01:20–01:27, Paper 1.21.11)

| Docket | Tier | Finding |
|---|---|---|
| T-12 | P0 | cops module present → boot crash `NoClassDefFoundError: net/wesjd/anvilgui/AnvilGUI$StateSnapshot` (core relocates AnvilGUI, module jars reference the original package) |
| T-13 | P1 | Keystone `ListenerService.registerGuarded` has no `isAssignableFrom` filter → `argument type mismatch` on every ArmorStand/creature spawn once a listener handles `ItemSpawnEvent` (shared `EntitySpawnEvent` HandlerList) |
| T-14 | P2 | core `WearableEquipListener` (gangland-item) needs `WearableEquipService`, which only the weapon module provides → `Failed to instantiate listener` with no weapon module |
| T-15 | P2 | Keystone `UpdateNotifier.start()` runs the first HTTP check on the server thread → 10 s boot stall when the network is unreachable |
| T-16 | P3 | `DatabaseManager.startBackup` logs `Failed to create a backup` on every shutdown: a SQLite primary tries the MySQL backup DataSource (cause visible since Keystone 1.8.1); Keystone-side fix queued |
| T-17 | P3 | `Found Vault, linking...` / `Linked Vault` logged twice per boot |
| T-18 | P2 | `message_en.yml` declared `Errors.Bounty` twice, so YAML last-wins dropped `Below_Minimum` (the WB-02 message never resolved); merged in 20649afc with `MessageFileDuplicateKeysTest` |

## Folder

- `README.md` — this board. `PLANNER-BRIEF.md`, `EXECUTOR-BRIEF.md`, `TEMPLATE.md` — reused from the module-split
  sprint with the wave's names substituted (read those in `../module-split-2026-09-07/` until copied).
- `exploration/*.md` — the three explorer reports. `architecture/*.md` — the three architect blueprints + the pick.
- `keystone-1.9.0.md`, `bartizan.md`, `gangland-0.9.0.md` — executor checklists (section 7 status tables).
- `smoke/` — harness + reports. `build_board.py` — regenerates the artifact page.

## Status board

| Item | Status | Note |
|---|---|---|
| A · docket T-12..T-18 filed | done | 479 entries, republished |
| A · Keystone 1.8.1 hotfix (T-13, T-15, T-16 cause) | done | Keystone commit 23950c0 on `phase-h7-module-loader`; 966 tests |
| A · Gangland 0.8.4 fixes (T-12, T-14, T-17; T-18 investigated) | done | Gangland commit 011ad30c on `0.8.4`, pinned to Keystone 1.8.1 |
| A · smoke harness | done | `smoke/smoke.py` (fake + dry-run verified) |
| A · smoke matrix S1–S8 on 0.8.4 | done | all eight PASS (reports `smoke/reports/2026-09-08-1247-*`), 0 Gangland/Keystone ERRORs; gate GA reached; T-18 real cause fixed in commit 20649afc |
| B · exploration | done | `exploration/E1..E3` |
| B · architecture | done | `architecture/A1..A3`, pick = **A2 + refinements** (`architecture/PICK.md`) |
| C · checklists | done | `keystone-1.9.0.md` (39 tasks), `bartizan.md` (22), `gangland-0.9.0.md` (16 groups); `REVIEW-consistency.md` = ready after patches (5 blockers, 6 majors, rulings a–f binding, see `architecture/PICK.md` amendments); planners patching |
| C · execution Keystone → Bartizan → Gangland | **done (code)** | **Keystone 1.9.0** (3 commits, 1035 tests). **Bartizan 0.1.0** (`master` HEAD `152eba4`, 12 commits; gates G1–G5; 20 test classes / 101 tests). **Gangland 0.9.0** (`0.9.0`, 25 commits over 0.8.4, HEAD `e2b9d82b` + docket `bd5db9fd`): every group A–P done incl. five review-fix groups; **gate P passed** — reactor install/package green, core jar weapon- and NMS-free with AnvilGUI unrelocated, six module jars, zero Bartizan refs in the core, `Host_Api: 0.9` ×6, 742 tests / 126 classes. Final Opus review of M–O running |
| D · smoke of the new topology | **done** | **10/10 PASS** (`smoke/reports/2026-09-09-verdicts.md`) on Keystone 1.9.1 + Bartizan 0.1.0 + Gangland 0.9.0; first run found T-35/T-36/T-37 (all fixed); Citizens-present rows still need a Citizens build for 1.21.11 |
| E · wrap-up | **done** | graphs refreshed in all three repos; docs done; fix-commit review applied (Keystone `61621a8`, Gangland `900fc0c8`; 1047 / 793 tests); board, docket, memory current; **nothing pushed**; open items for the user in `smoke/reports/2026-09-09-verdicts.md` §3 |

## Decisions log (orchestrator)

- 2026-09-08 · Opus session limit (HTTP 429) killed the three planners, the smoke tester and their research sub-agents at ~12:00; the user reset the limit at ~12:50 and the planners/tester were resumed with `SendMessage` (context intact). Rule added to `CLAUDE.md` and memory: agents must not spawn sub-agents unless told to.

- 2026-09-08 · **Pick = A2 "clean architecture"** with three user refinements: reflective recoil through
  `PacketAdapter.relativeCameraRotation` (setRotation only as fallback — the user found setRotation teleports and feels
  rough on older clients, fine on 1.21.x through ViaBackwards), civilians module named **`gangland-civilians`** (id
  `civilians`), generic `[ITEM-BUY]`/`[ITEM-SELL]` signs with legacy aliases. Binding text: `architecture/PICK.md`.
- 2026-09-08 · T-18 is not a code bug: Keystone's `FileHandler` regenerates an on-disk file only on a `Config_Version`
  change, so a message key added mid-cycle stays missing on servers that already had the file. Docket entry updated.
- 2026-09-08 · Explorer/architect agents (feature-dev types) have no shell and cannot write files; their reports are
  transcribed into `exploration/` and `architecture/` by the orchestrator. Planners and executors use `claude`-type
  agents with role prompts so they can write their own checklist/status files.

- **2026-09-08 ~19:30, gate GA (Bartizan):** the checklist's compile gate GC was unreachable as written (`dto/AmmunitionData` imports the group-D `Ammunition`); GC redefined to "fails only on `Ammunition` (+`CombatEligibility`)", exit 0 is B6's gate. bStats is relocated in the plugin shade (`org.luckyraven.bartizan.dependency.bstats`) — the checklist's "no relocations" was wrong for a jar that ships a third-party library.
- **2026-09-08 ~19:45, gate GD (Bartizan):** the root pom compiles against `spigot-api 1.21.11-R0.1-SNAPSHOT`, not Keystone's 1.16.5 floor — the verbatim-ported weapon code uses `Enchantment.PROTECTION`, `PotionEffect.INFINITE_DURATION`, `Player.isClimbing()`, the 3-arg `sendBlockDamage` and `Particle.BLOCK` (10 compile errors otherwise). Same situation as Gangland's root pom today; lowering to the 1.16 floor is a later wave (risk R-GD in `bartizan.md` §6).
- **2026-09-08 ~20:10, GD review ruling M1:** `WeaponShooting`, `WeaponMuzzle`, `SteppedProjectileTask` are Bartizan-internal (their only external caller, `NpcCombatDelegate`, becomes Bartizan's own `NpcWeaponControllerImpl`); they move from `bartizan-api` to the plugin before B13. `RaytraceContext`/`WeaponVisualSpawner` stay api (forced by `WeaponRaytracer`'s signature). §C.2 amended.
- **2026-09-08 ~20:20, copy source:** `E:\Programming\java\Bartizan\.gangland-0.8.4\` (gitignored `git archive 0.8.4`) is the copy source for every Bartizan port from group F on, so Gangland 0.9.0's deletion groups (B, C) run in parallel instead of waiting for group N.
- **2026-09-08 ~21:50, Gangland gate-C review ruling B1:** the weapon deletion had silenced every kill announcement on the downed path (lethal damage is cancelled there, so `PlayerDeathEvent` never fires and Bartizan's listener cannot help). The core keeps a generic `Death.Global` list (`%killer%`/`%victim%`, no `%item%`) and the fallback branch; the weapon name on that path is docket #22 for a later Bartizan hook. Eleven consumer-less `Messages` members whose YAML left with the weapon module were deleted.
- **2026-09-08 ~22:00, T-C3b:** group N's deferral of the two `DataCleanupTask` tests would have left `gangland-impl`'s test sources uncompilable through gates D–G, so they were repaired right after gate C (methods kept, only the deleted seam removed).
- **2026-09-08 22:0x, session limit again** (three agents lost at launch, reset 22:40): the running set is capped at two Sonnet executors + one Opus reviewer.
- **2026-09-09 ~00:15, Gangland gate D–G review (`REVIEW-gangland-DEFG.md`):** PASS WITH FIXES. Rulings: sign similarity keeps `isSimilar` for the material catch-all tier (a `[SELL] DIAMOND_SWORD` sign must not take an enchanted sword); the vocabulary fold moves to Keystone's `instantiate(beforeLifecycle)` seam so it always precedes the loader `@PostConstruct`s; `FuelContract.isFuelSink` restores the container→sink direction; the shop display resolver reads the live name before `pristine`. Filed as group G-R in `gangland-0.9.0.md`, run after group H.
- **Keystone follow-up (1.9.1):** `keystone-testkit`'s `RecordingNbtAccessor` keys tags by name only, not per `ItemStack`; two live stacks read each other's tags. Fix upstream with an identity-keyed map, then delete `ItemDefinitionSimilarityTest`'s in-test `PerStackNbtAccessor`.
- **Commit-boundary note:** the 20 group-D deletions in `gangland-item` were already staged when the gate-C repair commit `66b01acc` was made, so they sit there rather than in the D+E commit `de802972`; every gate's tree state is correct, only that label is off. Not rewritten (local history, but not worth a rebase).
- **2026-09-09 ~01:30, Gangland gate H review (`REVIEW-gangland-H.md`):** PASS WITH FIXES — filed as group H-R (weapon-name validation before Bartizan, weapon item in the civilian's hand, two stray civilian beans in cops, explicit legacy-alias header match, Citizens gate at the factory). Bartizan `38913d5` makes `NpcWeaponFactoryImpl` reject unknown names up front.
- **USER DECISION NEEDED (review m8):** civilians declares `Plugins: [Bartizan]`, and turf + cops will declare `Depends: [civilians]`, so a server WITHOUT Bartizan loses civilians, turf and cops entirely (the loader drops them with `module.plugin.missing`/`module.dependency.missing`). Turf capture itself has no weapon coupling. Alternatives: (a) accept — Bartizan is a hard companion of the NPC-bearing modules (current plan, matches D-row smoke expectations); (b) split the three Bartizan-touching classes out of civilians into a tiny `gangland-civilians-bartizan` module that carries the `Plugins:` gate, so civilians/turf/cops load without Bartizan and NPCs simply have no guns. Not changed pending the user's call.
- **2026-09-09 ~01:50, Bartizan final review (`REVIEW-bartizan-FINAL.md`):** PASS WITH FIXES — blocker: nothing ever saved the weapon table or closed the database (no autosave, no shutdown save/disconnect); fixed by an autosave timer + a staged `onDisable` (executor X-B-R). Rulings: `WeaponDeathListener` never resurrects a death Gangland suppressed; the legacy-table import retries on a failed read instead of writing its marker; **NBT-API becomes a hard `depend` of Bartizan** (deviation from §C.5 — Keystone's no-op NBT fallback would make every item inert); `validateAndGetWeapon` documented as registering. The review's §4 is the Bartizan half of the phase-D smoke checklist.
- **2026-09-09 ~12:15, Gangland gate K/L review (`REVIEW-gangland-KL.md`):** PASS WITH FIXES — two blockers: the executor's T-K5 status row claimed a Citizens guard that is NOT in the tree (process note: executor claims are only trusted after the gate review; this one was false), and the gadget half of `FuelContract.isFuelSink` was never written so jetpack refuelling from a can is dead. Plus the grenade-on-car suppression on the wrong event, unguarded Citizens reaches in cops' listeners, a dead `startingAmmoMagazines` chain. Filed as group K-R (after N, before O). Ruling m6: `jetpack_glide_descent_rate` is dropped from Bartizan's `wearables.yml` (dead pre-migration).
- **2026-09-09, group K-R done (executor X-G-KRO):** all five fixes landed and gate K-R is green (`mvn -q -pl gangland-features/cops-n-crooks,gangland-features/gangland-gadget,gangland-infra/gangland-item -am test`, 166 tests / 25 classes, 0 failures), then root `mvn test -q` green at 742 tests / 126 classes (was 741 before, +1 for T-KR2's positive-branch test). T-KR1's guard genuinely did not exist pre-fix (confirmed by `grep -rn "NpcSupport" gangland-features/cops-n-crooks/src` returning nothing before the fix) — the T-K5 row is corrected in place, not rewritten. T-KR4's `NpcDamageUnprotectListener.onNpcSpawn` was split into a new class `NpcSpawnUnprotectListener` (`@ListenerHandler(condition = "isCitizensAvailable")`, same mechanism group M built into `Settings.isCitizensAvailable()` and turf's `TurfPowerupInteractListener` already used) since the class also carries two plain-Bukkit handlers that must keep working without Citizens; `WantedLevelListener`/`CopListener`'s fixes used `NpcSupport.isNpc(...)` (the T-M3-established never-throws idiom) rather than the review's literal blanket early-return, since a blanket return would have silently disabled kill-combo tracking for real players on a Citizens-less server — recorded as a reasoned deviation, not a literal-text miss.
- **2026-09-09, group O done (executor X-G-KRO):** docs — `CLAUDE.md`, this README's topology block, `documentation/module-loader.md`, `documentation/README.md`, root `README.md`, `documentation/developer/compatibility.md` (rewritten around Keystone's reflective `PacketAdapter`/`ReflectivePacketAdapter`, preserving the three NMS position-packet constructor shapes `CameraRotationPackets` collapses the 20 deleted per-version adapters into, as the historical record), two new pages `documentation/bartizan-integration.md` and `documentation/migration-0.9.0.md` (both link to, not duplicate, Bartizan's own `documentation/bartizan-api.md`/`migration.md`). Smoke — `smoke/scenarios.json` gained the D0-D9 phase-D matrix (`"plugins"`/`"remove_plugins"` per-scenario keys, `legacy_module_jar_prefix` fallback so the pre-split S1-S8 rows stay dry-run-verifiable under `"legacy": true`) and `smoke/smoke.py` gained `must_not_contain` support plus real plugin staging/parking (`ensure_parked_plugins`/`restore_parked_plugins`, mirroring the existing module-jar parking) — verified with `python smoke.py --list` (all 18 rows print, legacy ones labelled) and `python smoke.py --dry-run --deploy --rows D0,...,D9` plus all eight legacy rows (no crashes, D6 correctly shows "would REMOVE plugin: Bartizan", D7 found the real `Citizens-2.0.42-b4164.jar` already on the test server to park). D6's `expect` block encodes the checklist's default reading (3 loaded/3 faults) pending the still-open USER DECISION above — flagged inline in the scenario title and in `smoke/README.md`'s Limitations. `graphify update . --force` run last: 15129 nodes / 39138 edges / 638 communities, `graph.json` newer than HEAD. Two docs-debt findings filed as docket #33 (five stale developer-doc pages naming pre-split modules/packages, and the two orphaned `features/weapons.md`/`wearables.md`/`repair.md` pages) and #34 (a stale `%weapon%`/`%ammo%` placeholder comment in `message_en.yml`) — both out of group O's named ~9-file scope, not fixed directly.
- **2026-09-09 13:20, phase D batch D0–D3:** D0 PASS (Bartizan standalone). D1/D3 FAIL only on Bartizan's shipped `Backup: true` (T-16 repeated; fixed in Bartizan `0e18356`). **D2 FAIL = boot crash**: with the civilians module deployed Gangland dies in Keystone's post-construct pass on `NoClassDefFoundError: net/citizensnpcs/api/npc/NPC` — `getDeclaredMethods()` on a bean whose helper takes `NPC`. Two fixes in flight: Keystone **1.9.1** hotfix (scans skip an unloadable class with fault `reflection.type.missing`) and Gangland D-fix-1 (no Citizens type in any scanned signature; classloader test). **The test server has NO Citizens jar** (only its data folder) — every row so far ran Citizens-absent; the `citizens-main 2.0.33` jar in `~/.m2` (Jan 2024) predates Paper 1.21.11 and is not a usable stand-in. **User action:** drop a Citizens build for 1.21.11 into `E:\Documents\Minecraft\Test Server\plugins` for the Citizens-present rows; until then D7 (Citizens removed) is the only distinct Citizens row.
- **2026-09-09 ~15:50, phase D rerun D0–D5 (Keystone 1.9.1, Gangland `05996be6`, Bartizan `0e18356`): ALL PASS** — six modules `6 loaded, 0 fault(s)`, `Item vocabularies installed: [bartizan]`, four `npc.citizens.missing` faults (Citizens absent on the test server), clean reverse-order disable, zero plugin error lines. Two environment lessons: the harness deploys Keystone only with `--keystone` (the 1.9.0 jar had stayed in `plugins/`), and a data folder generated by an earlier boot keeps old defaults (`plugins/Bartizan/settings.yml` still said `Backup: true`; deleted once). D6–D9 running.
- **2026-09-09 ~16:20, phase D closed: 10/10 rows PASS** after the three fixes; D6 confirms the Bartizan-absent blast radius (2 loaded, 4 faults) — still the user's call; D7 is not distinct until a Citizens build for 1.21.11 is on the test server. Verdicts: `smoke/reports/2026-09-09-verdicts.md`.
- **2026-09-09 ~17:10, wave closed.** Final HEADs: Keystone `phase-h8-item-npc` @ `61621a8` (1.9.1, 1047 tests); Bartizan `master` @ `152eba4`/`0e18356` (0.1.0, 20 test classes / 101 tests); Gangland `0.9.0` @ `900fc0c8` (31 commits over 0.8.4, 793 tests). Phase D 10/10. Review follow-ups: the Citizens-blind safety test now refuses by class name and covers interface-typed beans; Keystone skips a configuration poisoned in its constructor and rethrows static-init failures. Not done by design: push (never), the wave folder is untracked (user's call), Citizens-present smoke rows (no Citizens build for 1.21.11 on the test server), the Bartizan-absent design decision, bStats id, in-game checks.
