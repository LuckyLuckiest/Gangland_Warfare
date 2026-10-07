# PLAQUE G0-G1 report — 2026-09-16

Status: DONE

## What changed

All paths relative to `E:\Programming\java\Plaque` (the new repo) unless noted.

**G0 — repo skeleton**
- `pom.xml` — single Maven module (`org.luckyraven.plaque:plaque`, `packaging: jar`, no aggregator, per plan §2
  "does `<ScoreboardPlugin>` need an `-api` module? No."). `${revision}` = `0.1.0`, `maven.compiler.release` 17,
  `bukkit.version` = `1.16.5-R0.1-SNAPSHOT` (Keystone's floor, confirmed cached in `~/.m2` and compiles clean —
  plan §13's open question resolved: nothing ported needs a newer Bukkit symbol), `keystone.version` = `1.9.2`
  (pinned per the brief, independent of Keystone's 1.10.0 branch tip). `flatten-maven-plugin` +
  `maven-shade-plugin` (FastBoard shaded + relocated to `org.luckyraven.plaque.dependency.fastboard`, same
  pattern as `gangland-build/pom.xml`; every `keystone-*`/`spigot-api`/`viaversion-api` dependency stays
  `provided`, so shade's default scope filtering excludes them automatically) + an inactive Central `release`
  profile (source/javadoc/gpg/central-publishing, not activated — P1: install to `~/.m2` this wave). Dependencies
  declared: `keystone-common`/`keystone-bean`/`keystone-persistence`/`keystone-hooks` (provided),
  `fastboard` 2.1.5 (compile, shaded), `viaversion-api` 5.8.1 (provided), `log4j-api` (provided, needed for
  `@CustomLog`), `spigot-api`/`lombok` (provided), `junit-jupiter`/`mockito-core`/`keystone-testkit` (test) — the
  full list from WS1-scoreboard.md §2, declared now to avoid pom churn across G0/G1/G2 rather than growing it
  gate-by-gate.
- `lombok.config` — copied from Bartizan verbatim (`@CustomLog` → Keystone's `Logger.getLogger(TYPE)`).
- `.gitignore` — copied from Bartizan, dropped the Bartizan-specific git-archive-snapshot line (not applicable
  here).
- `src/main/resources/plugin.yml` — `name: Plaque`, `main: org.luckyraven.plaque.Plaque`, `api-version: '1.16'`,
  `depend: [Keystone]`, `softdepend: [PlaceholderAPI, ViaVersion]`, per brief.
- `src/main/resources/org/luckyraven/plaque/module.properties` — `module.name=${project.name}` (filtered resource,
  same pattern as upstream `scoreboard-api`'s own `module.properties`).
- `src/main/java/org/luckyraven/plaque/Plaque.java` — minimal `JavaPlugin` stub (`onEnable`/`onDisable` logging
  only). Deliberately does **not** wire `BeanFactory`/bootstrap/dependency soft-dep checks/bStats yet — those are
  G2 per the brief's gate table; adding them now would be scope creep on an "empty plugin" gate.
- `CLAUDE.md` — Bartizan's structure (what Plaque is / server platform / package map / build / code style /
  testing), plus two Plaque-specific sections the plan flagged as load-bearing: the D1 silent-migration warning
  (an unrecognised `Driver:` value must default to `DriverV3` with a warning, never fall back to a deleted `V1`)
  and the `FlashPlaceholderWrapper.currentTick` cross-plugin static-clock caveat (C8) so a future session doesn't
  relocate `org.luckyraven.keystone.*` in this plugin's shade config.
- `README.md` — expanded from the orchestrator's one-line stub: status table (G0/G1 done, G2 not started), build
  commands, the config-compatibility note from plan §5.
- Verified: `mvn clean install -DskipTests` green on the plugin.yml + `Plaque.java`-only skeleton before adding
  any board code (see Build section).

**G1 — rendering engine port** (package `org.luckyraven.gangland.scoreboard.*` → `org.luckyraven.plaque.board.*`,
source: `E:\Programming\java\wt\gangland-0.9.2\gangland-ui\scoreboard-api`, read-only)
- `src/main/java/org/luckyraven/plaque/board/Board.java` — was `Scoreboard.java`, renamed class only. UI-02 fix
  intact (`timer.start(false)`).
- `src/main/java/org/luckyraven/plaque/board/driver/DriverHandler.java` — unchanged shape (UI-01 fix intact: owns
  a private copy of the `lines` list; the ViaVersion-aware `FastBoardImpl.hasLinesMaxLength()` override, confirmed
  by the plan's §0 correction to be repo-wide, not V3-specific, ported as-is). **C4**: field/constructor param
  `Placeholder placeholder` → `PlaceholderProvider placeholder`; the `updateLine(Line)` call site is unchanged
  (`line.update(placeholder, fastBoard.getPlayer())` — only the field's declared type moved).
- `src/main/java/org/luckyraven/plaque/board/driver/version/DriverV3.java` — the **only** driver ported (WS1-D1).
  `DriverV1.java`/`DriverV2.java` and `ScoreboardManager`'s reflection-based `getDrivers()` scan were **not**
  ported (D1(b) + S1: one driver enumerates to nothing worth reflecting over). Same C4 constructor-param retype
  as `DriverHandler`.
- `src/main/java/org/luckyraven/plaque/board/part/Line.java` — **C4**: `update(Placeholder, Player)` →
  `update(PlaceholderProvider, Player)`, body's `placeholder.convert(player, data)` →
  `placeholder.resolve(player, data)` (`Player extends OfflinePlayer`, so every existing call site is source- and
  binary-compatible). **C3/UI-17 fix** (2 lines, one per method): `getCurrentContent()` and `update()` both guard
  `contents.isEmpty()` before indexing/`% contents.size()` — see red-first evidence below.
- `src/main/java/org/luckyraven/plaque/board/part/StaticLine.java` — package rename only, no other change.
- `src/main/java/org/luckyraven/plaque/board/configuration/BoardAddon.java` — was `ScoreboardAddon.java`, renamed
  class + package only (S1/C7 note: this class is not yet constructed by anything — no `FileManager` bean exists
  until G2 — but it already compiles against `keystone-persistence`'s `FileHandler`/`FileInitializer`/
  `FileManager`, confirming B1's pom correction). The internal file key stays the literal string `"scoreboard"`
  (matches the plan's "package rename only" instruction — the YAML file itself stays named `scoreboard.yml`
  in G2, not renamed to `board.yml`).
- `src/test/java/org/luckyraven/plaque/board/BoardTest.java` — was `ScoreboardTest.java`, ported verbatim
  (class/package rename only, `Scoreboard` → `Board`). Already green pre-port (UI-02 pin), stayed green.
- `src/test/java/org/luckyraven/plaque/board/part/LineTest.java` — was `LineTest.java`, ported verbatim
  (`Placeholder IDENTITY` → `PlaceholderProvider IDENTITY`, still `(player, text) -> text`) plus **one new test**,
  `emptyContents_doesNotThrowAndReturnsEmptyString` (UI-17) — see red-first evidence.

No Gangland file changes (out of scope for this stream).

## Deviations from the plan

None. `bukkit.version` (1.16.5) and FastBoard 2.1.5's compatibility with it — both flagged "not verified" in the
plan's §13 — are now verified: the reactor compiles and both `mvn clean install -DskipTests` (G0) and
`mvn clean install` (G1, full test run) are green against that floor with no code needing a newer Bukkit symbol.

## Red-first evidence

Test: `LineTest.emptyContents_doesNotThrowAndReturnsEmptyString` (UI-17, C3).

Red command: `mvn test -Dtest=LineTest,BoardTest` run against `Line.java` **before** the guard was added (the
rest of the port — C4's `PlaceholderProvider` retype, `DriverHandler`, `DriverV3`, `Board`, `BoardAddon`,
`StaticLine` — was already in place; only the two guard lines were withheld).

Failing line:
```
java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
	at org.luckyraven.plaque.board.part.Line.getCurrentContent(Line.java:79)
	at org.luckyraven.plaque.board.part.LineTest.emptyContents_doesNotThrowAndReturnsEmptyString(LineTest.java:107)
[ERROR] Tests run: 6, Failures: 0, Errors: 1, Skipped: 0
```
(The other 5 tests — `BoardTest`'s UI-02 pin and `LineTest`'s 4 pre-existing UI-01 cases — were green in this
same run, confirming the failure was isolated to the new case, not a broken port.)

Green command: `mvn clean install` after adding the 2-line guard (`if (contents.isEmpty()) return "";` in both
`getCurrentContent()` and `update()`):
```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Build

Final command: `mvn clean install` (full reactor — single module).
```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  6.030 s
```
Shaded jar verified by hand (`jar tf target/Plaque-0.1.0.jar`): only `org.luckyraven.plaque.*` classes, the
relocated `org.luckyraven.plaque.dependency.fastboard.*`, `plugin.yml` and `module.properties` — no
`org.luckyraven.keystone.*` (confirms `provided` scope is excluded from the shade correctly) and no
`DriverV1`/`DriverV2` class anywhere in the repo (`grep -rl "DriverV1\|DriverV2" src` → no matches).

G0's standalone check (`mvn clean install -DskipTests` on the plugin.yml + `Plaque.java`-only skeleton, before any
board code existed) was also green: `BUILD SUCCESS`, `target/Plaque-0.1.0.jar` produced.

## Docket ids touched

None written yet — UI-01/UI-02/UI-17/UI-16 dispositions are exactly as the plan's §11 table already states
(UI-01/UI-02 carried forward fixed, UI-17 fixed at this port step, UI-16 `wontfix`/`moved` since `DriverV1`/`V2`
were never ported). Per CLAUDE.md, `write_db` needs a `read_db` first against the live docket, which the plan's
own §13 flags as not yet independently checked — deferred to G4 step 16 (docket writes), as the plan schedules it,
not this batch. No new bug found during the port.

Docket candidates: none.

## Subagents used

None. The port is a small, fully-specified mechanical transformation (package/class rename + two named fixes)
over 6 source files + 2 tests already read in full during orientation; executing it directly kept the diff
traceable against the plan's exact line-level citations without a handoff-fidelity risk, and stayed inside the
"at most 2, only when it helps" rule rather than spending coordination overhead for no benefit.

## Concerns / open questions

- **BRIEF.md vs. the binding plan disagree on G2's command framework.** The brief's own G2 gate row says
  "`/plaque reload` via keystone-command," but the binding plan (`WS1-scoreboard.md` §9 D2, and
  `PLAN.md` §10 row `WS1-D2`) recommends **(b) plain Bukkit `CommandExecutor`**, explicitly rejecting
  keystone-command for a single leaf command as a "one interface, one implementation" smell. This doesn't affect
  G0/G1 (no command code was written), but it changes G2's dependency list (whether `keystone-command` is
  `provided`) and the shape of `ReloadCommand`/whether a COMMAND bootstrap phase exists. Flagging now so the
  orchestrator can confirm which one is the actual instruction before I start G2 — I have not guessed at this
  since it is a structural decision, not a filled-in detail.
- `bukkit.version` 1.16.5 and FastBoard 2.1.5's compatibility with it are now build-verified, closing plan §13's
  open item for this repo (worth a one-line update to the plan doc if the orchestrator wants that tracked there).
- The Maven shade warning "`Plaque-0.1.0.jar`, `fastboard-2.1.5.jar` define 1 overlapping resource: META-INF/
  MANIFEST.MF" appears on every build — harmless (same warning Bartizan/Gangland accept today), not a defect.
