# END batch verification report — 2026-09-23

Status: **PARTIAL — T-53 and T-54 confirmed fixed; a third, previously-masked P0 (T-55) now blocks the same
8-module boot.** HEAD confirmed `fcc41a32` ("0.10.0 WS5: the gang module boots - installer bean has real ordering
edges; permission repository moves with its owner") on top of `6977eff9` (END docs) and `207e7282` (WS6 facade),
tree clean on arrival. No commits made this session; worktree still clean, still at `fcc41a32`, at handback.

## 1. Whole reactor build

`mvn clean install` (one build) → **BUILD SUCCESS**, 01:04 min, **18 reactor modules** `SUCCESS`
(`gangland-infra/gangland-domain` is gone from the reactor entirely, per `366b43de` — matches expectation).

**Test count (Maven console rollup, W52): 927, 0 Failures, 0 Errors, 0 Skipped** — exactly matching expectation.

| Module | Tests run |
|---|---|
| Gangland Core | 67 |
| Gangland Item | 43 |
| Sign API | 63 |
| Gangland API | 14 |
| Gangland (impl) | 238 |
| Gangland Gangs | 104 |
| Gangland Mail | 25 |
| Gangland Civilians | 23 |
| Gangland Turf | 91 |
| Cops N Crooks | 76 |
| Gangland Gadgets | 113 |
| Gangland NPC Shops | 17 |
| Gangland Loot Chests | 53 |
| **Total** | **927** |

Delta vs. last round: Gangland (impl) 234→238 (+4, `CoreRepositoryScanTest`), Gangland Gangs 102→104 (+2,
`GangConfigBeanGraphTest`'s new cases) — matches the fix commit's own file list exactly.

`target/modules/` holds **8 module jars**: `cops-n-crooks`, `gangland-civilians`, `gangland-gadget`,
`gangland-gang`, `gangland-lootchest`, `gangland-mail`, `gangland-npc-shops`, `gangland-turf` — confirmed.

`PermissionRepository`/`PermissionTable` confirmed moved: `target/modules/gangland-gang-0.10.0.jar` now contains
`org/luckyraven/gangland/gang/database/repositories/permission/PermissionRepository.class` and
`.../tables/permission/PermissionTable.class`; the shaded core jar (`target/gangland_warfare-0.10.0.jar`) retains
only `org/luckyraven/gangland/core/permission/Permission.class` (the entity) — no repository/table class. Matches
the commit message exactly ("the module jar carries the moved classes and the shaded core jar none of them").

**No merge-caused compile break — nothing to fix at the build level.**

## 2. Smoke

Harness: `smoke.py`, paths repointed to this worktree + Keystone 1.11.1
(`E:\Programming\java\wt\keystone-1.11.0\keystone-plugin\target\Keystone-1.11.1.jar`), restored to
`gangland-0.9.2`/`keystone-1.10.0` afterward. `scenarios.json`'s `cut-full-regression` updated with a
`GanglandApi facade published for external consumers` expectation; a new `end-t54-no-gang` scenario added for
item (c). Server DB (`plugins/Gangland_Warfare/database/gangland.db`) parked aside for a genuinely fresh boot,
then restored byte-identical.

### (a) `cut-full-regression`, 8 modules, fresh DB — **FAIL (new bug, not T-53)**

```
[cut-full-regression] verdict=FAIL boot=True stop=True
  modules=['civilians','gadget','gang','lootchest','npcshops','mail','turf','copsncrooks'] errors=17
```

All 8 module jars loaded via `ModuleLoader` again. **T-53's specific crash is gone** — `GangMembershipInstaller`
no longer appears in the trace at all. Boot now proceeds one step further into `BeanFactory.instantiate()` and
hits a **different** `@Configuration` class in the same package:

```
java.lang.IllegalStateException: Ambiguous bean for parameter 'arg0' of type UserManager for bean
  GangConfig.gangItemSourceContribution(): 2 candidates registered. Add @Qualifier to disambiguate.
	at Keystone-1.11.1.jar//org.luckyraven.keystone.bean.BeanFactory.resolveParameter(BeanFactory.java:528)
	at Keystone-1.11.1.jar//org.luckyraven.keystone.bean.BeanFactory.invokeBean(BeanFactory.java:483)
	at Keystone-1.11.1.jar//org.luckyraven.keystone.bean.BeanFactory.instantiate(BeanFactory.java:278)
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.bootstrap.GanglandContext.bootstrap(GanglandContext.java:195)
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.Gangland.onEnable(Gangland.java:93)
```

Plugin disables immediately, same downstream symptom as T-53 (every `/glw` subcommand throws "unexpected error").
**100% reproducible — identical stack trace across 2 separate boots this session.** Per this round's explicit
instruction ("if the boot still fails, stop and report the stack trace — do not patch") **this was not fixed.**

**Root cause**: `GangConfig` (gang module) has **4** `@Bean` methods taking a raw, unqualified
`UserManager<Player> userManager` parameter — `gangPlaceholderContribution` (line 125), `gangOptionContribution`
(line 135), `gangDebugContribution` (line 146), `gangItemSourceContribution` (line 174). `DataConfig.java` (core)
registers **two** `UserManager` beans by design: `@Bean(name = "online", isGeneric = true)` and
`@Bean(name = "offline", isGeneric = true)` — every other consumer in the codebase correctly disambiguates
(`IdentityContractConfig.userLookupContract(@Qualifier("online") UserManager<Player> userManager)` is the
established pattern). Only `gangItemSourceContribution` actually crashed this round — the other 3 happened to
resolve while only the "online" candidate existed so far in the bean graph — but all 4 are equally exposed; this
is a concrete instance of the **already-docketed `CL-24`** ("Bean order is JVM-dependent for beans with equal
dependency sets... non-deterministic relative order across JVMs/recompiles"), so which of the 4 throws could
differ on a future recompile. **Filed as docket `T-55` (P0, open)** with full stack trace and fix direction (add
`@Qualifier("online")` to all 4 — online is clearly correct for all four, matching every other consumer). A
parallel run with the gang module entirely absent (item (c) below) had **zero errors**, confirming this is
specific to `GangConfig`'s own beans, not a wider regression.

### (b) — rows T-53 previously blocked: partially verified before the T-55 crash, rest still blocked

The crash happens in the **CONFIG** phase, which runs after **DATABASE** — so table creation and initial-data
seeding already completed successfully on the fresh DB before the crash:

- **Fresh-DB table creation — PASS.** Querying the fresh `gangland.db` directly (`sqlite3`) after the crashed
  boot confirms `gang`, `member`, `rank_tree`, `rank_parent`, `rank_permission` and `permission` tables all exist.
- **Rank seeding boot-twice, no duplicates — PASS.** After the first (crashed) boot, `rank_tree` held exactly 2
  rows (`1, 'owner'`; `2, 'member'`) and `rank_parent` held 1 row. Rebooted on the **same** DB (not re-parked) —
  identical crash, and `rank_tree`/`rank_parent` row counts unchanged (still 2 and 1) after the second boot. No
  duplicate seeding.
- **`/glw module list` (gang shown with turf/mail depending on it), `/glw gang help`, `/glw rank list`,
  placeholders, `/glw filter gangs ...`, any `GangItemSourceContributions` visibility, `/glw waypoint gangid`**
  — **still blocked**, now by T-55 instead of T-53: the plugin never finishes enabling, so the command tree never
  registers and none of these are reachable. `/glw filter gangs` and any `GangItemSourceContributions` visibility
  are doubly blocked — `GangConfig.gangItemSourceContribution()` is the exact bean T-55 crashes on, so nothing
  downstream of it can ever construct until T-55 is fixed.

### (c) T-54 proof: boot without the gang module — **PASS, zero errors**

```
[end-t54-no-gang] verdict=FAIL(*) boot=True stop=True modules=['civilians','gadget','lootchest','npcshops'] errors=0
```
(*) The scenario's own `loaded_modules` expectation was wrong on my part (I included `copsncrooks`) — see below;
the actual observed behavior is correct. **0 errors is the real result and the one that matters for T-54.**

Deployed all 7 non-gang modules (mail, turf, civilians, cops, gadget, npcshops, lootchest) on the restored
original DB. Confirmed via log:
```
[Keystone Common.LoggingSink] Module 'mail' needs module 'gang', which is not installed or failed to load [module.dependency.missing]
[Keystone Common.LoggingSink] Module 'turf' needs module 'gang', which is not installed or failed to load [module.dependency.missing]
[Keystone Common.LoggingSink] Module 'copsncrooks' needs module 'turf', which is not installed or failed to load [module.dependency.missing]
[Keystone Module.ModuleLoader] Loaded module civilians/gadget/lootchest/npcshops 0.10.0
```
`mail` and `turf` refuse directly (both `Depends: [gang]`/`Depends: [civilians, gang]`); `copsncrooks` then
refuses **transitively** because it `Depends: [turf]` and turf never loaded — expected cascade given the module
dependency graph, not a new bug (my scenario's expected-modules list just didn't account for the transitive
case). All non-fatal, server still boots (established S6/D6 pattern). Ran `glw`, `glw modules`, `glw reload`
(one full autosave cycle) — **zero ERROR-level lines of any kind**, specifically **no** `Failed to save data for
repository: Permission` / `No data supplier set for repository: PermissionRepository`. **T-54 is fixed, fully
proven end-to-end.**

### (d) GanglandApi facade registration — **confirmed, console-visible**

Same run as (c) (gang module absent, so this proves the facade doesn't depend on the gang module being present —
it's wired from core-only beans in `WiringConfig`):
```
[09:03:56 INFO]: [Gangland.WiringConfig] GanglandApi facade published for external consumers
```
Matches `WiringConfig.java:132`'s `log.info(...)` call exactly, one line, once per boot.

## 3. Bug docket

Checked first (not already filed), then:
- **T-53** and **T-54** marked `fixed` in the docket's shared db (`bugs` collection), each with the fixing commit
  (`fcc41a32`), what changed, the covering test, and — for T-53 — a note that fixing it exposed T-55 in its place.
- **T-55 (P0)** — `GangConfig.gangItemSourceContribution` crashes `Gangland_Warfare` enable: unqualified
  `UserManager` is ambiguous (2 candidates) — added to
  `brainstorming/bug-docket-2026-09-06/triage/new-findings.txt`, docket rebuilt (515 bugs total: P0 45 / P1 86 /
  P2 189 / P3 192 / X 3) and republished to https://claude.ai/artifact/92cuYyaj2dmZGhJLJWyoEj (version 31).

## 4. Graph

`graphify update . --force` (AST only) in this worktree: **15,934 nodes, 33,377 edges, 2,500 communities**
(`graph.html` skipped — over the 5,000-node viz limit, as before). Not committed (`graphify-out/` is gitignored).

`graphify affected "GanglandApi"` (first lines):
```
Affected nodes for GanglandApi
Relations: calls, indirect_call, references, imports, imports_from, re_exports, inherits, extends, implements, uses, mixes_in, embeds
Depth: 2
- Command.java [imports] gangland-api/src/main/java/org/luckyraven/gangland/command/Command.java:L9
- GanglandChatUtil.java [imports] gangland-api/src/main/java/org/luckyraven/gangland/util/GanglandChatUtil.java:L4
- CopSpawnerInfoCommand.java [imports] gangland-features/cops-n-crooks/.../CopSpawnerInfoCommand.java:L8
- CopSpawnerListCommand.java [imports] gangland-features/cops-n-crooks/.../CopSpawnerListCommand.java:L10
- JailInfoCommand.java [imports] gangland-features/cops-n-crooks/.../JailInfoCommand.java:L8
```
(21+ more affected files across every module — cops-n-crooks, civilians, turf, gang, lootchest, mail, impl.)

`graphify explain "GangMembership"` (first lines):
```
Node: GangMembership
  Source:    gangland-api/src/main/java/org/luckyraven/gangland/data/gang/GangMembership.java L17
  Type:      code
  Community: GanglandCopSettings
  Degree:    65

Connections (65):
  <-- GadgetModuleConfig.java [imports] gangland-features/gangland-gadget/.../GadgetModuleConfig.java:L31
  <-- GangConfig.java [imports] gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/gang/GangConfig.java:L15
  <-- DataConfig.java [imports] gangland-impl/src/main/java/org/luckyraven/gangland/config/DataConfig.java:L18
  <-- WiringConfig.java [imports] gangland-impl/src/main/java/org/luckyraven/gangland/config/WiringConfig.java:L13
  <-- GangConfigBeanGraphTest.java [imports] gangland-features/gangland-gang/src/test/.../GangConfigBeanGraphTest.java:L10
```
Confirms `GangConfig.java` imports `GangMembership` at line 15 and `GangConfigBeanGraphTest.java` exists as a
test — both structurally consistent with the fix commit's own description.

## 5. Manual checklist

`exec/G010/WS2-manual-checklist.md`'s `## WS5/WS6 merge` section updated in place (not duplicated) with a new
`## END batch update` subsection: T-53/T-54 marked fixed with proof, T-55 recorded as the new blocker for the
same rows 52-55. **No genuinely new client-only rows this round** — everything still outstanding was already
covered by rows 52-56.

## Cleanup confirmed

`scenarios.json` paths restored to `gangland-0.9.2`/`keystone-1.10.0` (validated by re-parsing). Test server DB
restored byte-identical (parked aside, restored after both fresh-DB boots). `settings.yml` untouched this round
(`Language: en`, unchanged). `modules/` folder back to its single pre-existing jar; parked Bartizan/Citizens
plugin jars all restored. No leftover backup/scratch files. Worktree `git status --short` empty, still at
`fcc41a32`, zero commits made. Zero reviewers spawned, zero subagents used (well within the ≤2 budget — all work
done directly).

## Summary for the coordinator

- Build: clean, 927/0/0/0, 18 modules, 8 module jars, `PermissionRepository`/`PermissionTable` confirmed moved —
  **exactly as expected, nothing to fix.**
- T-53: **fixed and verified** — `GangMembershipInstaller` no longer appears anywhere in the crash trace.
- T-54: **fixed and verified end-to-end** — boot + reload + one full autosave cycle with the gang module absent,
  zero errors.
- GanglandApi facade: **confirmed registering**, console-visible line, independent of gang-module presence.
- **T-55 (new, P0, open)**: fixing T-53 let boot proceed one step further and immediately hit a second,
  previously-masked crash in the same `GangConfig` class — an unqualified `UserManager<Player>` parameter across
  4 `@Bean` methods, ambiguous against `DataConfig`'s two named `UserManager` beans. **The 8-module boot still
  fails end-to-end.** Root cause and fix (`@Qualifier("online")` on all 4 methods) are fully diagnosed and filed;
  recommend a short, targeted fix round (same shape as the T-53/T-54 fix) before the wave can be called done.
- No code changes made this session per the explicit "stop and report, do not patch" instruction.
