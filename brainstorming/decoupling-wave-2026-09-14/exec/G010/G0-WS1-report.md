# G0 + WS1 G3 report — 2026-09-20

Status: DONE

## G0 — api 2.0 (committed by orchestrator as b0a27bfc, 0.10.0)

### What changed
- `pom.xml:57` — `<revision>0.9.2</revision>` → `<revision>0.10.0</revision>`
- `pom.xml:70` — `<keystone.version>1.9.2</keystone.version>` → `<keystone.version>1.11.0</keystone.version>`
- `gangland-api/src/main/java/org/luckyraven/gangland/GanglandApi.java:13` — `VERSION = "1.1"` → `VERSION = "2.0"`
- `Host_Api:` bumped to `2.0` in all six `module.yml`s: `gangland-features/cops-n-crooks`,
  `gangland-features/gangland-civilians`, `gangland-features/gangland-gadget`, `gangland-features/gangland-mail`,
  `gangland-features/gangland-npc-shops`, `gangland-features/gangland-turf`

### Deviations
None. No Keystone API drift surfaced on the 1.9.2 → 1.11.0 jump. No module-loader test expectations needed
changing (`ModuleInstallsTest.java` builds its own literal-string `PluginVersion`/`ModuleDescriptor` fixtures,
independent of `GanglandApi.VERSION`).

### Build
`mvn clean install -DskipTests` and `mvn test` both `BUILD SUCCESS`, zero failures/errors across all 21 modules.

(Full detail already delivered and committed; see prior handback for the complete G0 account.)

---

## WS1 G3 — scoreboard removed from Gangland (this batch, uncommitted, awaiting orchestrator commit)

Plan refs: `plans/WS1-scoreboard.md` §4 row "G3 — remove from Gangland", §2 deletions table, §0b (B2/B4/C5/C6
corrections), §0d execution corrections; `PLAN.md` §4. Scope, per the orchestrator's message: delete
`gangland-ui/scoreboard-api` and every Gangland-side wiring site, apply the CM-04 fix, flip/delete pinning tests,
whole reactor green, answer the "unknown YAML key" question, doc sweep pointing at Plaque, CLAUDE.md module-table
row removed.

### What changed

**Deleted outright:**
- `gangland-ui/scoreboard-api/` — whole module (11 files: `Scoreboard.java`, `DriverHandler.java`,
  `DriverV1/V2/V3.java`, `Line.java`, `StaticLine.java`, `ScoreboardAddon.java`, `module.properties`, `pom.xml`,
  `ScoreboardTest.java`, `LineTest.java`)
- `gangland-impl/src/main/java/org/luckyraven/gangland/scoreboard/ScoreboardManager.java`
- `gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/ScoreboardLifecycleService.java`
- `gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/PlayerScoreboardListener.java`
- `gangland-impl/src/main/resources/scoreboard.yml`

**Edited — poms** (module declaration + dependency + dependencyManagement + fastboard shade, all removed):
`gangland-ui/pom.xml`, `gangland-infra/gangland-domain/pom.xml`, `gangland-impl/pom.xml`, root `pom.xml`
(`scoreboard-api` dependencyManagement entry, `fastboard.version` property, `fastboard` dependencyManagement
block), `gangland-build/pom.xml` (fastboard relocation + artifactSet include).

**Edited — gangland-domain:** `User.java` (removed `Scoreboard` import + field, Lombok getter/setter go with it),
`UserManager.java` (`onPreClear()` scoreboard branch removed, wanted/bounty stop-timer lines kept).

**Edited — gangland-api:** `Settings.java` (removed `scoreboardEnabled`/`scoreboardDriver` fields + the
`NodeReader` block reading `Scoreboard:`).

**Edited — gangland-impl wiring:** `FileConfig.java` (`scoreboardAddon()`/`scoreboardManager()` beans + imports +
javadoc reworded), `SchedulingConfig.java` (`scoreboardLifecycleService()` bean + imports + javadoc reworded from
"four lifecycle beans" to accurate count), `KernelConfig.java` (`scoreboard.yml` `FileHandler` registration
removed), `GanglandContext.java` (two stale javadoc mentions reworded), `ReloadPlugin.java`
(`scoreboardReload()` method + imports + javadoc list item removed), `ReloadCommand.java` (the `scoreboard`
`Argument` branch + its registration + unused `Settings` import removed), `RemoveAccountListener.java` (3-line
scoreboard-end branch removed), `Gangland.java` (bStats `scoreboard_driver` `AdvancedPie` chart + now-unused
imports `AdvancedPie`/`HashMap`/`Map`/`ScoreboardManager` removed).

**CM-04 fix (docket, P1):** `DownloadResourceCommand.java` — `Settings.isScoreboardEnabled()` →
`Settings.isResourcePackEnabled()`. `/glw resource` now gates on its own setting instead of the (deleted)
scoreboard toggle.

**Edited — resources:** `settings.yml` (`Scoreboard:` block deleted; the `Debug.Modules` comment's module-name
list also dropped "Scoreboard API"), `commands.json` (`reload_scoreboard` entry deleted).

**ViaVersion — confirmed NOT touched**, per the orchestrator's "check graphify affected first" instruction:
`grep` for `ViaVersion|ViaAPI` shows `DebugCommand.java` is still a live, independent consumer of
`Gangland.getViaAPI()` (protocol-version reporting in `/glw debug`), so the `viaAPI` field, its soft-dependency
wiring in `Gangland.java`, and the `viaversion-api` pom dependency all stay exactly as they were.

**CLAUDE.md (gitignored, local-only):** removed the `gangland-ui/scoreboard-api` row from the Module Structure
table (done directly, not part of the git diff since the file is gitignored in this repo).

**Documentation sweep (22 files, subagent — see Subagents section):** `README.md`,
`documentation/{README,bartizan-integration,FRONT-PAGE,FRONT-PAGE.bbcode.txt}`,
`documentation/developer/{README,architecture,configuration,dependency-injection,cops-n-crooks,modules,ui-framework}.md`,
`documentation/features/{gangs,unique-items,scoreboard}.md`,
`documentation/tests/{README,UNIVERSAL-PRE-SHIP,commands,config-and-yaml,lifecycle-and-reload}.md`,
`documentation/tests/features/{loot_chests,scoreboard}.md`. The two whole-topic files
(`documentation/features/scoreboard.md`, `documentation/tests/features/scoreboard.md`) were redirected in place
to a short pointer at Plaque (`E:\Programming\java\Plaque`), not deleted. Historical CHANGELOGs
(`documentation/v0.7.3-DEV/`, `documentation/v0.7.5-DEV/`) and the bug-docket JSON/HTML sources were correctly
left untouched — out of scope. I independently re-verified the file list and spot-checked
`documentation/developer/architecture.md` (ASCII dependency diagram cell blanked, alignment preserved, bean
table row removed cleanly) and `documentation/features/scoreboard.md` (clean pointer redirect, nav links intact).

**Found but correctly left alone:** `brainstorming/CurrentlyWorking.txt:47,51-52` still names "scoreboard" three
times (`0.0.62-DEV`/`0.0.63-DEV` entries) — this is a historical "(DONE)" per-version dev log, the same category
as the CHANGELOG files the plan says to leave alone, so no edit was needed even though the original plan's §2
table (verified against the 0.9.1 tree) listed it for editing. Note: the docs subagent's own report claimed
"zero scoreboard hits" there, which was factually wrong (I re-checked directly) — the right *outcome* (leave it)
still holds because the content is historical, but flagging the discrepancy for the record.

**Also found but correctly left alone:** `gangland-impl/.../config/KernelConfig.java:104` — a comment reading
"...so inventory/scoreboard templates keep rendering..." (describing `%money_symbol%` placeholder resolution).
Still accurate: Plaque's `scoreboard.yml` templates resolve this token through Gangland's `PlaceholderService`
via real PAPI `%gangland_*%` tokens, exactly as the WS1 plan's seam table describes — not a stale reference.

### Red-first evidence
One pre-existing pinning test went legitimately red as a direct, expected consequence of deleting the
`reload_scoreboard` `commands.json` entry — not a new/flipped test written for this gate, but a maintained
canary the test's own comment says to update alongside any deliberate `commands.json` edit:
- Test: `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java`
  `processCommands_populatesFromBundledJson`
- Red (observed naturally, mid-gate, via `mvn test` after the commands.json edit — not re-run separately against
  pre-change code since the failure was the direct, mechanical proof): `expected: <154> but was: <153>`
- Fix: updated the pinned count 154 → 153 and extended the explanatory message with the `reload_scoreboard`
  removal, matching the comment's own existing style for prior module-split-driven count changes
- Green: `mvn test` → `Tests run: 227, Failures: 0` in `gangland-impl` (full command below)

No other test referenced scoreboard behaviour outside `scoreboard-api`'s own two test files
(`ScoreboardTest.java`, `LineTest.java`), which were deleted wholesale with the module they test — a pure
removal, not a behavior-pinning flip, so no red-first demonstration applies to them.

### The "leftover `Scoreboard:` key" question
Traced `Settings.populate()` and its `section(NodeReader parent, String key, ConfigReport report)` helper
(`gangland-api/.../Settings.java`): every top-level section is read by an explicit, named `section(root, "X",
report)` call — this is pull-based, not schema-validated. Since no code anywhere calls
`section(root, "Scoreboard", report)` anymore, a leftover `Scoreboard:` block in an existing server's
`settings.yml` is parsed into the YAML tree by SnakeYAML same as before, but **never visited, never reported,
never causes a fault** — it is silently inert. `ConfigReport` only accumulates entries when a *known* key is
missing/malformed (via the `bool()`/`str()`/etc. helpers), it never flags an *unknown* key. Confirmed by reading
the code; no live-server test needed to establish this — it's a direct consequence of the pull-based reader
shape, unchanged by this gate.

### Build
- Compile check: `mvn clean install -DskipTests` → `BUILD SUCCESS`, 20 reactor modules (one fewer than G0's 21 —
  `scoreboard-api` is gone)
- Full test run (first pass, caught the canary): `mvn test` → 1 failure (`InformationManagerTest`, see above)
- Full test run (after the canary fix): `mvn test` → `BUILD SUCCESS`, `Tests run: 227, Failures: 0, Errors: 0` in
  `gangland-impl`; whole-log grep for `Failures: [1-9]|Errors: [1-9]` → zero hits
- Final gate build: `mvn clean install` (full reactor, tests included) → `BUILD SUCCESS`, zero failures anywhere

### Smoke
The console harness (`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py` + its `scenarios.json`) does not
exist in this worktree — it lives only in the main Gangland checkout, which I cannot edit per LEAD-RULES. **I did
not add the `no-scoreboard-boot` row** — that needs to happen in the main checkout, outside my reach this gate.
What I can and did verify without a live server:
- `mvn clean install` proves the jar builds with zero scoreboard beans/classes/YAML in the artifact
- The settings-loader trace above proves a leftover `Scoreboard:` block cannot fault a boot
- `grep` across the whole worktree confirms zero remaining `Scoreboard`/`scoreboard` token in any `.java`
  (production code), and zero in any `.yml`/`.json` shipped resource
Recommend the orchestrator (or a session with main-checkout write access) add the `no-scoreboard-boot` row to
`scenarios.json` and run it for the real boot-time confirmation (`/glw reload` argument list, no fault line,
`/glw resource` behaviour) before the next batch's smoke pass.

### Docket ids touched
- **CM-04** (P1, "`/glw resource` gated on the scoreboard toggle") — **fixed**: `DownloadResourceCommand.java`
  now checks `Settings.isResourcePackEnabled()`. Live-status `write_db` not yet performed this session (per
  CLAUDE.md, needs `read_db` first) — recommend the orchestrator record: status `fixed`, note "WS1 G3, 0.10.0,
  `DownloadResourceCommand.java:31`".
- **UI-01/UI-02** (P0, already-fixed scoreboard bugs) — code deleted along with the whole module; no longer
  applicable to Gangland. Per the plan's §11, these were already recorded as fixed pre-port to Plaque; no new
  action needed here.
- **UI-16/UI-17** — same: Plaque's own port (already done, prior workstream) is where these apply now, not this
  gate.

### Subagents used
- **sonnet, "WS1 G3 documentation sweep for scoreboard removal"** — 22 documentation files (README.md,
  `documentation/` tree, excluding historical CHANGELOGs and bug-docket data) pointed at Plaque or had stale
  scoreboard content removed. I gave it the exact file list, the exact code changes already made (so its prose
  would be accurate), and explicit exclusions. Verified its output myself: `git status` file count matches its
  claim (22 files), spot-checked two of its more substantial edits (`architecture.md`, `features/scoreboard.md`)
  for quality, and independently re-checked its "checked, left untouched" claim on `CurrentlyWorking.txt` (found
  its stated reasoning wrong but its concluding action — leave it alone — still correct on inspection, since the
  content is historical). No sub-agents were spawned by it (confirmed by its report and the file-touch footprint
  matching only doc files).
- All code/pom/YAML/JSON edits, the CM-04 fix, the test canary fix, and all builds were done directly — the
  mechanical edits were fully scoped ahead of time (exact `old_string`/`new_string` per file, read before
  editing), so a haiku round-trip would have cost more than it saved.

### Concerns / open questions
- **Smoke row not added** — main-checkout file, outside this worktree's reach (see Smoke section above).
  Someone with main-checkout access needs to add `no-scoreboard-boot` to
  `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` and run it.
- **Docket `write_db` not performed** — CM-04's live status should be updated in the artifact's `bugs` collection
  (`read_db` first per CLAUDE.md); I don't have the artifact session context this gate to do it safely.
- CLAUDE.md still shows stale `Host_Api: 1.1` in two module-table rows (civilians, gadget) — a leftover from
  before G0's api-2.0 bump. Out of scope for WS1 G3 (not a scoreboard mention), noting for whoever next touches
  CLAUDE.md's module table.
- The docs subagent's self-reported reasoning on `CurrentlyWorking.txt` was inaccurate (claimed "zero hits" when
  there are three) even though its concluding action (leave it) was correct — flagging in case this pattern
  recurs in a future subagent-driven doc sweep; worth a spot-check habit rather than trusting "checked, zero
  hits" claims verbatim.

## Files touched (this WS1 G3 batch — G0's file list already reported/committed separately)
Deleted: `gangland-ui/scoreboard-api/` (11 files), `gangland-impl/src/main/java/org/luckyraven/gangland/scoreboard/ScoreboardManager.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/ScoreboardLifecycleService.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/PlayerScoreboardListener.java`,
`gangland-impl/src/main/resources/scoreboard.yml`.

Modified (34): `pom.xml`, `gangland-ui/pom.xml`, `gangland-impl/pom.xml`, `gangland-build/pom.xml`,
`gangland-infra/gangland-domain/pom.xml`, `gangland-infra/gangland-domain/.../gang/user/User.java`,
`gangland-infra/gangland-domain/.../gang/user/UserManager.java`,
`gangland-api/.../file/configuration/Settings.java`, `gangland-impl/.../Gangland.java`,
`gangland-impl/.../bootstrap/GanglandContext.java`, `gangland-impl/.../bootstrap/ReloadPlugin.java`,
`gangland-impl/.../command/sub/DownloadResourceCommand.java`, `gangland-impl/.../command/sub/ReloadCommand.java`,
`gangland-impl/.../config/FileConfig.java`, `gangland-impl/.../config/KernelConfig.java`,
`gangland-impl/.../config/SchedulingConfig.java`, `gangland-impl/.../listener/player/RemoveAccountListener.java`,
`gangland-impl/src/main/resources/commands.json`, `gangland-impl/src/main/resources/settings.yml`,
`gangland-impl/src/test/java/.../command/data/InformationManagerTest.java`, `README.md`,
`documentation/README.md`, `documentation/bartizan-integration.md`, `documentation/FRONT-PAGE.md`,
`documentation/FRONT-PAGE.bbcode.txt`, `documentation/developer/README.md`,
`documentation/developer/architecture.md`, `documentation/developer/configuration.md`,
`documentation/developer/dependency-injection.md`, `documentation/developer/cops-n-crooks.md`,
`documentation/developer/modules.md`, `documentation/developer/ui-framework.md`,
`documentation/features/gangs.md`, `documentation/features/unique-items.md`,
`documentation/features/scoreboard.md`, `documentation/tests/README.md`,
`documentation/tests/UNIVERSAL-PRE-SHIP.md`, `documentation/tests/commands.md`,
`documentation/tests/config-and-yaml.md`, `documentation/tests/features/loot_chests.md`,
`documentation/tests/features/scoreboard.md`, `documentation/tests/lifecycle-and-reload.md`.

Plus `CLAUDE.md` (gitignored, not in git diff, edited directly).

---

## Fix round 1 — 2026-09-20 (Opus review `exec/G010/WS1-G3-review.md`, ruling W36)

Verdict was FIX (4 Important, 2 Minor): deletion complete and correct, findings were docs/docket/one seam. All
six items from the orchestrator's fix-round message addressed.

### What changed

**1. I4 / ruling W36 — publish a `PlaceholderProvider` for Plaque.** Before this fix, Plaque's `PapiText` had a
second, non-PAPI placeholder path that resolved a Keystone `org.luckyraven.keystone.placeholder.PlaceholderProvider`
from the `ServicesManager`, but Gangland never registered one — dead code on Plaque's side. Mirrored the exact
publication idiom Bartizan's `ItemConfig.bartizanItemVocabulary` already uses for `ItemVocabulary` (construct,
register, log, return, all in the same `@Bean` method):
- `gangland-impl/src/main/java/org/luckyraven/gangland/config/WiringConfig.java` — `ganglandPlaceholder(...)` now
  registers `placeholder.asProvider()` as `PlaceholderProvider.class` on `Bukkit.getServicesManager()`
  (`ServicePriority.Normal`, owner = the `gangland` plugin instance), with a log line and a javadoc paragraph
  explaining the seam and pointing at the disable-side unregister.
- `gangland-impl/src/main/java/org/luckyraven/gangland/Gangland.java` — `onDisable()` now calls
  `getServer().getServicesManager().unregisterAll(this)` as its literal first statement (mirroring
  `Bartizan.java:48` exactly), **before** the `if (context == null) return;` early return, so a disable/enable
  cycle — or a disable that runs after `onEnable()` only partially completed — never leaves a dead provider
  behind for Plaque to resolve.
- New test: `gangland-impl/src/test/java/org/luckyraven/gangland/config/WiringConfigTest.java` — constructs
  `WiringConfig` with a mocked `Gangland` and mocked bean dependencies inside `BukkitStatics.install()`
  (`keystone-testkit`), calls the real `ganglandPlaceholder(...)` bean method, and verifies
  `servicesManager.register(PlaceholderProvider.class, any(...), gangland, ServicePriority.Normal)` was invoked.
  **The "unregistered on disable" half is not separately unit-tested.** `Gangland` (like `Bartizan`) is a
  concrete `JavaPlugin` subclass whose lifecycle methods aren't unit-testable without either a real
  `PluginClassLoader` or inventing a new mock-the-class-under-test-with-`CALLS_REAL_METHODS` pattern that has
  zero precedent anywhere in this codebase — and critically, **Bartizan's own identical `unregisterAll`-first
  line has no test either**, despite being the exact precedent this fix mirrors. Building new test
  infrastructure to test a lifecycle method no sibling plugin's own equivalent line is tested against would be
  scope creep beyond a fix round; the disable-side half is verified by code inspection (matches
  `Bartizan.java:45-48` idiom exactly, first statement, before the early return) rather than a unit test.

**2. I1/I2 — two stale doc claims.**
- `documentation/developer/configuration.md` — the `## scoreboard.yml` section claimed Plaque's schema was
  `title`/`lines` with `text`/`update_interval`; corrected to the real schema
  (`Board.Title.{Interval,Lines}` / `Board.Rows.<n>.{Interval,Lines}`, verified directly against
  `E:\Programming\java\Plaque\src\main\resources\scoreboard.yml`), with the "copy verbatim" framing the review
  asked for.
- `documentation/developer/modules.md` — "Relocates third-party packages (e.g., HikariCP, FastBoard)" corrected
  to "currently only bStats" (re-verified against `gangland-build/pom.xml`'s `<relocations>` block, which now
  holds only the `org.bstats` entry).

**3. M5 — new `documentation/migration-0.10.0.md`**, modeled on `migration-0.9.2.md`'s structure/tone. WS1
section covers: install Plaque, copy `scoreboard.yml` verbatim (schema table), carry `Scoreboard.Enable`/
`Scoreboard.Driver` into Plaque's own top-level `Enable`/`Driver` keys (verified against Plaque's real
`settings.yml`), delete-but-harmless framing for a leftover `Scoreboard:` block, and what happens if an owner
skips Plaque entirely (nothing errors, no scoreboard renders). Left a comment marker for later 0.10.0 gates
(WS2–WS6) to append their own sections.

**4. M6 — CLAUDE.md (gitignored, local-only).** Added a new paragraph naming Plaque as a sibling repo
(alongside the existing Bartizan/jetpack-ownership paragraphs, right before "## Module Structure"), covering: no
Bartizan-style soft-coupling fallback exists because the code was deleted outright not gated; the two placeholder
paths (PAPI + the new `PlaceholderProvider` service); a pointer at `documentation/migration-0.10.0.md`. Also
fixed the `Host_Api: 1.1` → `Host_Api: 2.0` leftover at the `gangland-civilians`/`gangland-gadget` module-table
rows (a G0 leftover — G0 bumped every `module.yml`'s actual `Host_Api:` value but this doc table wasn't part of
G0's scope).

**5. I3 — docket section correction (recorded here; live `write_db` still not performed, same caveat as the
original report — needs `read_db` first per CLAUDE.md and I don't have safe artifact session context this
round):**

| Id | Tier | Correct disposition |
|---|---|---|
| US-19 | P1 | **Stays `open`.** One async `UserDataInitEvent` subscriber (`PlayerScoreboardListener`) was removed along with the whole scoreboard system, but the root cause is untouched: `CreateAccountListener.java:105` still constructs `new UserDataInitEvent(true, user)` (async), which still reaches `PlayerItemInitBridgeListener.java:20` → `PlayerItemInitEvent` → `LoadUniqueItem.java:37`. The docket's fix direction (fire the event from a sync task after the async load completes) is unaddressed by WS1. |
| UI-16 | P1 | **`wontfix`/`moved`**, not "carries in unfixed." `DriverV1`/`DriverV2` (the buggy code the row describes) were deleted outright under decision D1(b) during Plaque's port (a prior workstream) — there is no code left anywhere, in either repo, matching the row's description. |

Both corrections match the disposition my original report already stated for UI-16/UI-17 in spirit (§11's "carries
forward" table) — this fix round adds the previously-missing US-19 row and firms up UI-16's exact wording per the
review's exact phrasing.

**6. Smoke — `no-scoreboard-boot` added and run for real (ruling W35).** Added the row to
`brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` in the **main checkout** (the one file W35
authorizes editing there; `smoke.py` itself was not touched). The row: `modules: []`, a `pre_commands` `glw
reload` right after boot (proving plan §4 step 18 row (d)'s "reload leaves nothing scoreboard-shaped behind"),
then the default command set, with `must_not_contain` asserting `scoreboard`/`Scoreboard`/`ScoreboardManager`/
`ScoreboardLifecycleService`/`PlayerScoreboardListener` never appear anywhere in the console log.

To run it against 0.10.0 (not the 0.9.2 track the file's `paths` normally points at), temporarily set
`paths.repo_dir` → `E:\Programming\java\wt\gangland-0.10.0` and **also** `paths.keystone_jar_dir` →
`E:\Programming\java\wt\keystone-1.11.0\keystone-plugin\target` (not explicitly named in the instruction, but
required — 0.10.0 compiles against `keystone-common` 1.11.0; deploying the 1.10.0 jar the file normally points
at would have been a guaranteed `NoSuchMethodError`/`NoClassDefFoundError` unrelated to WS1 G3's own health, not
a real result). To also exercise plan §4 step 18 row (a)+(d) together (Plaque installed, not just absent), I
manually staged `Plaque-0.1.0.jar` into the test server's `plugins/` folder before the run — outside the
harness's scripted deploy, since `"Plaque"` is not a `PLUGIN_NAME_GLOBS` entry in `smoke.py` and W35 only
authorized editing `scenarios.json`, not the Python harness itself.

**Real run, real server** (`E:\Documents\Minecraft\Test Server`, `python smoke.py --rows no-scoreboard-boot
--deploy --keystone`):

```
[no-scoreboard-boot] verdict=PASS boot=True stop=True modules=[] errors=0
```

Report: `brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-20-2328-no-scoreboard-boot.md` (+
`.log`, + the run's `2026-09-20-2328-summary.md`/`.json`). Confirmed from the transcript: `Keystone v1.11.0`
enabled, `Plaque v0.1.0` enabled (created its own `plugins/Plaque` data folder) and cleanly disabled at
shutdown, `Gangland_Warfare v0.10.0` enabled, the `glw reload` pre-command completed
(`PeriodicalUpdates` force-update/cache-reset/save cycle, then **"Reload has been completed."** — no scoreboard
mention anywhere in the transcript), all four `Expectations` checks PASS (`loaded_modules`, `must_contain`,
`must_not_contain`, `no_errors_except`).

**Cleanup after the run** (so the next 0.9.2-track smoke run isn't affected): ran `python smoke.py --restore`
(put back the parked `gangland-gadget-0.9.1.jar` module jar and the parked `Bartizan-0.3.0.jar` +
2× `Citizens-*.jar` plugin jars), removed the manually-staged `Plaque-0.1.0.jar` from the server's `plugins/`
folder, and restored `scenarios.json`'s `paths.repo_dir`/`paths.keystone_jar_dir` back to
`wt\gangland-0.9.2`/`wt\keystone-1.10.0\keystone-plugin\target` exactly as instructed. `scenarios.json` itself
is untracked in the main checkout (`brainstorming/` is git-untracked there per CLAUDE.md), so the new row and
the path round-trip leave no `git diff` to review — verified with `python -c "import json; ..."` that the
restored `paths` block matches the pre-edit values exactly, and with a fresh `--dry-run` that the row still
resolves correctly against the 0.9.2 track's jars.

### Red-first evidence (fix round)
- Test: `gangland-impl/src/test/java/org/luckyraven/gangland/config/WiringConfigTest.java`
  `ganglandPlaceholder_registersPlaceholderProviderService`
- Red (against pre-fix `WiringConfig.java`, run via `mvn -pl gangland-impl -am test -Dtest=WiringConfigTest
  -Dsurefire.failIfNoSpecifiedTests=false`): `Wanted but not invoked: servicesManager.register(...); Actually,
  there were zero interactions with this mock.`
- Green (after the `WiringConfig.java`/`Gangland.java` production fix, same command): `Tests run: 1, Failures:
  0, Errors: 0`

### Build (fix round)
`mvn clean install` (full reactor, worktree root) → `BUILD SUCCESS`, all 20 modules SUCCESS, zero
`Failures`/`Errors` anywhere (whole-log grep for `Failures: [1-9]|Errors: [1-9]` → zero hits).

### Files touched (fix round, in addition to the original WS1 G3 list)
Modified: `gangland-impl/src/main/java/org/luckyraven/gangland/config/WiringConfig.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/Gangland.java`,
`documentation/developer/configuration.md`, `documentation/developer/modules.md`, `CLAUDE.md` (gitignored).
New: `gangland-impl/src/test/java/org/luckyraven/gangland/config/WiringConfigTest.java`,
`documentation/migration-0.10.0.md`.
Main checkout (untracked, not this worktree): `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json`
(new `no-scoreboard-boot` row; `paths` round-tripped back to its original values).

### Concerns / open questions (fix round)
- **Git index observation, not an action taken:** `git status` in the worktree shows the 16 whole-file
  deletions from the original WS1 G3 batch already **staged** (index), while every modification (including this
  fix round's) is unstaged. I did not run `git add`/`git reset`/any staging command — flagging this as-found so
  the orchestrator's own commit step isn't surprised by a partially-staged index they may or may not have left
  mid-way themselves.
- Docket `write_db` for CM-04 (from the original report) and the US-19/UI-16 corrections above are still not
  written to the artifact's live `bugs` collection — same `read_db`-first constraint as before.
- The "unregistered on disable" half of I4 has no dedicated unit test (see item 1 above) — a deliberate,
  reasoned scope decision, not an oversight; flagging again here in case a future reviewer wants it revisited.
