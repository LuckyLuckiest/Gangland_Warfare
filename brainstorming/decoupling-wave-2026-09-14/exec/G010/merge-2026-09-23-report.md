# Merge verification report (round 3) — 2026-09-23

Status: **FAIL — critical merge-caused regression found, not fixed (out of this session's scope).** HEAD was
`89746105` ("0.10.0 WS6 G3: module-owned messages have one mechanism; civilians is the worked example") on
arrival, tree clean, as dispatched. **HEAD has since moved to `207e7282` via two more commits that landed on the
shared branch during this session, unprompted** — see "HEAD drift" at the end. No commits made this session; the
worktree tree is still clean (`git status --short` empty) at whichever commit it currently points at.

## 1. Whole reactor build (as of 89746105)

`mvn clean install` (one build) → **BUILD SUCCESS**, all 19 reactor modules `SUCCESS`. **No merge-caused compile
break — nothing to fix.**

**Test count (Maven console rollup, per W52): 921, 0 Failures, 0 Errors, 0 Skipped** — exactly matching the
expectation.

| Module | Tests run |
|---|---|
| Gangland Core | 67 |
| Gangland Item | 43 |
| Sign API | 63 |
| Gangland API | 14 |
| Gangland (impl) | 234 |
| Gangland Gangs | 102 |
| Gangland Mail | 25 |
| Gangland Civilians | 23 |
| Gangland Turf | 91 |
| Cops N Crooks | 76 |
| Gangland Gadgets | 113 |
| Gangland NPC Shops | 17 |
| Gangland Loot Chests | 53 |
| **Total** | **921** |

19 reactor SUCCESS lines confirmed: Gangland Warfare, Gangland Core, Gangland Infrastructure, Gangland Item,
Gangland UI, Sign API, Gangland API, Gangland, Gangland Features, Gangland Gangs, Gangland Mail, Gangland
Civilians, Gangland Turf, Cops N Crooks, Gangland Gadgets, Gangland NPC Shops, Gangland Loot Chests, Gangland
Build, Gangland Domain. `gangland-infra/gangland-domain` produces **zero** tests — empty module, matches the
dispatch's own note ("gangland-domain now empty"); confirmed later deleted from the reactor entirely by
`366b43de` (see "HEAD drift").

Gang module jar confirmed at `target/modules/gangland-gang-0.10.0.jar`; its `module.yml`:
```
Id: gang
Name: Gangland Gangs
Version: 0.10.0
Main: org.luckyraven.gangland.gang.GangModule
Host_Api: 2.0
Artifact: org.luckyraven:gangland-gang
```
No `Depends:`/`Plugins:` of its own (correct — gang is the foundation). `turf`'s `module.yml` has
`Depends: [civilians, gang]`; `mail`'s has `Depends: [gang]` — both confirmed by unzipping the built jars. Matches
the dispatch's expectation ("turf/mail depending on it").

## 2. Smoke — CRITICAL FINDING: the plugin cannot enable with the gang module present

Harness: `smoke.py`, `paths.repo_dir`/`paths.keystone_jar_dir` repointed to this worktree + Keystone 1.11.1
(`E:\Programming\java\wt\keystone-1.11.0\keystone-plugin\target\Keystone-1.11.1.jar`) for the duration, restored
to `gangland-0.9.2`/`keystone-1.10.0` afterward (confirmed via a final `python -c "import json..."` read).
`scenarios.json` updated: `module_jar_prefix`/`module_probe_commands` gained a `"gang": "gangland-gang"` entry
(permanent, like `lootchest` before it); `cut-full-regression` expanded to the 8-module set with `gang` added,
`expect.loaded_modules` gained `gang`, `must_contain` updated to `Runtime modules: 8 loaded, 0 fault(s)`.

### (a) `cut-full-regression`, real server, 8 modules + Bartizan — **FAIL**

```
[cut-full-regression] verdict=FAIL boot=True stop=True
  modules=['civilians','gadget','gang','lootchest','npcshops','mail','turf','copsncrooks'] errors=11
```

All 8 modules' jars loaded cleanly through Keystone's `ModuleLoader` (`Host_Api`/`Depends` checks all passed —
confirmed by the post-crash "Disabled module <id>" log lines for all 8). The failure is **not** a module-loading
problem; it happens one step later, inside `Gangland.onEnable`'s `BeanFactory.instantiate()`:

```
[08:17:07] [Server thread/ERROR]: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)
java.lang.IllegalStateException: Failed to instantiate @Configuration class
  org.luckyraven.gangland.gang.GangMembershipInstaller — check that every constructor parameter is already
  registered in the container.
	at Keystone-1.11.1.jar//org.luckyraven.keystone.bean.BeanFactory.instantiate(BeanFactory.java:244)
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.bootstrap.GanglandContext.bootstrap(GanglandContext.java:195)
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.Gangland.onEnable(Gangland.java:93)
Caused by: java.lang.IllegalStateException: Cannot resolve required parameter of type
  org.luckyraven.gangland.data.gang.GangMembership for constructor in
  org.luckyraven.gangland.gang.GangMembershipInstaller
	at Keystone-1.11.1.jar//org.luckyraven.keystone.bean.autowire.DependencyContainer.resolveConstructorParameters(...)
```

The plugin disables immediately after the enable failure. Every subsequent console command in the scenario
(`glw`, `glw help`, `glw modules`, `glw reload`, `glw debug inv-data`, `glw lootchest`, `glw lootchest help`,
`glw gang help`, `glw rank list`, `glw civilian list`) throws "An unexpected error occurred trying to execute that
command" — the command tree never registered. **100% reproducible, blocks every row that needs a booted plugin**
(rows (b)/(c)/(d) below, and half of (a) itself).

**Root cause**: `GangMembershipInstaller` (gang module,
`gangland-features/gangland-gang/.../gang/GangMembershipInstaller.java`) is a bare `@Configuration` class
instantiated directly via its own 3-arg constructor (`GangMembership`, `MemberManager`, `GangManager`), not a
`@Bean` factory method. `IdentityContractConfig.gangMembership()` (`gangland-impl/.../config/
IdentityContractConfig.java:54`) is the zero-arg core `@Bean` that produces the `GangMembership` it needs.
`BeanGraph`'s topological sort derives ordering edges **only from `@Bean` method parameters** (the house
convention recorded in `feedback_bean_ordering_via_params.md`), so a bare `@Configuration`-class-as-bean's own
constructor parameters give it no such edge — nothing forces `GangMembershipInstaller` to be built after
`gangMembership()`, and on this build it isn't. **Filed as docket `T-53` (P0)**, republished to the docket
artifact with full stack trace, fix direction (give the installer a proper `@Bean` factory method, or fix Keystone
bean scanning to also read a bare `@Configuration` class's constructor as ordering edges) and a red-first-test
ask. Per this round's brief ("no code changes unless the build itself is broken") this is a runtime bean-wiring
fault, not a compile break — **not fixed this session, reported only.**

### (b)/(c)/(d) — blocked entirely by T-53

Fresh-DB gang/member/rank table creation + rank-seeding boot-twice check, `/glw module list` (gang shown with
turf/mail depending on it), `/glw gang help`, placeholders, `/glw filter gangs ...`, any console-visible
`GangItemSourceContributions` resolution, `/glw waypoint gangid <unknown id>` — **none of these could be run**.
The plugin never enables with the gang module present, so there is nothing to check. No workaround exists that
would still be representative of the real 8-module topology; forcing a partial deploy would misrepresent what was
tested. Added to `WS2-manual-checklist.md` (rows 52-55) with the T-53 blocker noted explicitly, since three of the
four (filter, waypoint gangid, gang-menu-open) are *also* inherently `sender instanceof Player`-gated and will
still need a real client once T-53 is fixed — confirmed by reading `FilterCommand.java` (all 3 action handlers
gate on `instanceof Player`) and `WaypointGangIdCommand.java` (casts `sender` to `Player` unconditionally in its
action and both tab-completion suppliers, no console path at all).

### (e) WS6 G3 civilians — isolated from T-53, otherwise sound

Since civilians has no `Depends: [gang]` edge, a second scenario (`ws6-civilians-isolated`: civilians, cops,
gadget, npcshops, lootchest — gang/turf/mail dropped since turf/mail need gang) was added to `scenarios.json` and
run twice (once `Language: en`, once `Language: es`, `settings.yml` parked/edited/restored around the second run).
Both boots succeeded:

- `npc/civilian_messages.yml` and `npc/civilian_messages_es.yml` both extract from the module jar on boot
  (`[Keystone Persistence.FileHandler] Created file: ...\npc\civilian_messages.yml (from module resources)`, same
  for `_es.yml`).
- `/glw civilian list` (console-reachable — no `Player` gate, unlike every other module's message command) →
  **"No civilians are currently active."** under `Language: en`, then, after the settings edit + reboot →
  **"No hay civiles activos actualmente."** under `Language: es`. Confirms `LocalizedModuleYaml`'s language-suffixed
  file selection works end to end, both directions.
- Fresh-install no-warning path implicitly confirmed: neither boot logged a `Civilian:` legacy-block warning (none
  exists on this server's message files).
- **Not verified**: the legacy-block-fires path. Could not locate a persisted `message_en.yml`/`message_es.yml`
  anywhere under `E:\Documents\Minecraft\Test Server` to inject a synthetic `Commands.Civilian:` block into —
  `LanguageLoader` clearly loads and diff-reports against message content (`message_es.yml is missing 116
  declared key(s)` in both boot logs) but no matching file was ever written to disk in this topology across
  either boot. Not investigated further (out of budget for this round); flagged in the checklist as a follow-up
  rather than asserted broken — the mechanism (`Settings.warnIfLegacyShopBlockPresent`, generalized in WS6 G3)
  already has unit coverage per WS6's own `G3-report.md`.

A second, unrelated, lower-severity finding surfaced as a side effect of this isolation run: with the gang module
genuinely absent, every `/glw reload`/autosave logs `Failed to save data for repository: Permission` →
`IllegalStateException: No data supplier set for repository: PermissionRepository`
(`AbstractRepository.saveAllFromMemory <- RepositoryRegistry.saveAll <- PeriodicalUpdates.updatingDatabase`).
Root cause: WS5 moved `RankManager` (the sole caller of `PermissionRepository.setDataSupplier`, at
`RankManager.java:124`) into the optional `gang` module, while `PermissionRepository` itself
(`gangland-impl/.../database/repositories/plugin/PermissionRepository.java`) stayed core-hosted and
unconditionally registered. Any server that genuinely runs without the gang module — which WS5 G1 explicitly
designed `gadget`/`civilians`/`cops-n-crooks` to support via the `GangMembership` inert holder — gets this ERROR
every reload/autosave cycle forever, not a one-off. Not a boot-blocker. **Filed as docket `T-54` (P1)**.

## 3. Bug docket

Both findings checked against the docket first (not already filed) and added to
`brainstorming/bug-docket-2026-09-06/triage/new-findings.txt`, rebuilt (`parse_obs.py` + `build_docket.py`,
514 bugs total, P0 44 / P1 86 / P2 189 / P3 192 / X 3) and republished to
https://claude.ai/artifact/92cuYyaj2dmZGhJLJWyoEj (same artifact the docket has always lived at) as versions 29
and 30:

- **T-53 (P0)** — `GangMembershipInstaller` crashes `Gangland_Warfare` enable whenever the gang module is
  present. Full stack trace, root cause and fix direction as above.
- **T-54 (P1)** — Core `PermissionRepository` has no data supplier and spams ERROR on every reload/autosave when
  the gang module is absent.

No status rows written to the docket's shared db — both default correctly to `open`.

## 4. Manual checklist

`exec/G010/WS2-manual-checklist.md` gained a new `## WS5/WS6 merge` section (rows 52-56) covering: filter-gangs
(Player-gated, also T-53-blocked), waypoint-gangid-unknown-id (Player-gated, also T-53-blocked), gang-menu-open
(needs a client, also T-53-blocked), placeholders (Player-gated, also T-53-blocked), and the civilians
legacy-block-warning-fires path (not T-53-blocked, just couldn't locate the target file this round). Each row
notes exactly why it's blocked/deferred and what unblocks it.

## 5. HEAD drift during this session

The dispatch's arrival check was `git log -1` = `89746105`, tree clean — confirmed true at the start of this
round, and all build/smoke work above is anchored there. **By the time this report was written, `HEAD` had moved
to `207e7282` via two more commits that landed on the shared `0.10.0` branch during this session, without a new
dispatch:**

- `366b43de` — "WS5 G5: the empty `gangland-domain` module is deleted; docs follow the gang module." Its own
  commit message: "Reactor 921 tests green (`mvn verify`), 18 modules."
- `207e7282` — "WS6 G1+G2+G4: `GanglandApi` is the facade a module resolves from the `ServicesManager`." Its own
  commit message: "Reactor 924 tests green (`mvn verify`)."

`git merge-base --is-ancestor 89746105 HEAD` confirms this is a clean fast-forward, not a divergence — nothing to
reconcile in the worktree itself (`git status --short` is empty). Read-only `git show --stat` on both commits
confirms **neither touches `GangMembershipInstaller.java`, `IdentityContractConfig.java`, or `RankManager.java`**
— T-53 and T-54 are both still present, unmodified, at current HEAD. Both commits' own messages cite only
`mvn verify` (compile + unit tests), never a live-server boot with the gang module deployed — consistent with
neither catching T-53, since it only manifests when Keystone's real `BeanFactory` actually runs `Gangland.onEnable`
against a booted Paper server with the module jar present, which no unit test in this repo currently exercises.

This session's build/smoke/docket work was not re-run against `207e7282` — out of scope for a round dispatched
against `89746105`, and the two new commits' own content (module deletion + a new API facade, neither near the
bug) make it very unlikely the numbers would come out differently. Flagging this explicitly rather than silently
either ignoring the drift or unilaterally expanding scope to re-verify two undispatched commits.

## Summary for the coordinator

- Build: clean, 921/0/0/0, 19 modules, exactly as expected — **nothing to fix there.**
- Smoke: **the headline ask (8-module boot) fails 100% of the time** on a real bean-wiring bug in the gang
  module's `GangMembershipInstaller`, filed as `T-53` (P0) with full root cause and fix direction. A second,
  independent, lower-severity issue (`T-54`, P1) was found as a side effect of working around T-53.
- WS6 G3 civilians work is otherwise sound and independently verified (extraction, English, Spanish, fresh-install
  no-warning) — only the legacy-block-warning-fires path and the gang-dependent rows are outstanding.
- `HEAD` has moved twice more since this round's dispatch (`89746105` → `366b43de` → `207e7282`); neither new
  commit touches the T-53 bug area, but a fresh dispatch against `207e7282` (or wherever `0.10.0` sits next) would
  still need to fix T-53 before an 8-module smoke can pass — recommend that be the next round's first item.
- `scenarios.json` paths restored to `gangland-0.9.2`/`keystone-1.10.0`; the test server's `settings.yml` and
  `gadget`/`Bartizan`/`Citizens` jars restored byte-identical/parked-and-restored throughout. No commits made; no
  reviewers spawned; 0 subagents used this round (all work done directly, well within the ≤2 budget).
