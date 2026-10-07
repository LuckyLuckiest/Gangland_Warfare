# PLAQUE G2 report — 2026-09-16

Status: DONE

## What changed

All paths relative to `E:\Programming\java\Plaque`.

**Command framework correction (per coordinator, authoritative):** `/plaque reload` is a keystone-command
`Command`, not the plain `CommandExecutor` `WS1-scoreboard.md` §9 D2 recommended. WS1-D2 was decided by the user
in the decisions register ("stick with keystone implementation since they already have the whole library"),
overriding the plan document's own recommendation (orchestrator ruling W5). `pom.xml` gained `keystone-command`
at `provided` scope; `PlaqueContext` runs a real COMMAND phase.

**Bootstrap (`bootstrap/`)**
- `PlaqueContext.java` — the standalone-plugin twin of Gangland's `GanglandContext`, copied in shape from
  `BartizanContext` (`E:\Programming\java\wt\bartizan-0.4.0`, read-only reference) with the COMMAND phase
  reinstated (Bartizan itself has one — its `ReloadCommand` is also keystone-command, confirming this is the
  house pattern for a Keystone-powered standalone plugin, not something invented for Plaque). Owns the
  `DependencyContainer`/`BeanFactory`, the FILE-phase `FileManager.initializeAll()` hook, and the post-bootstrap
  listener/command scans (`org.luckyraven.plaque` / `org.luckyraven.plaque.command`).
- `DefaultListenerService.java` — Keystone's one-method `ListenerService` subclass every consumer writes itself;
  copied verbatim in shape from Bartizan's own.

**Config (`config/`)**
- `KernelConfig.java` (KERNEL) — `Diagnostics` hub (`LoggingSink` + `RecentFaultsSink`, installed process-wide so
  keystone-command's dispatch-error funnel has somewhere to report — keystone-command.md "Failure handling");
  `FileManager` bean registering `settings.yml` + `scoreboard.yml` (`FileHandler(plaque, "settings", ".yml")` /
  `FileHandler(plaque, "scoreboard", ".yml")`, both `addFile(..., true)`).
- `FileConfig.java` (FILE) — `BoardSettings`/`BoardAddon` beans, each self-registering with `FileManager` before
  the FILE-phase hook calls `initializeAll()` — same two-bean shape as Gangland's own `FileConfig`
  (`scoreboardAddon`/ordering-forcing-param pattern was **not** copied: Plaque's two files are independent, so
  there is no real ordering constraint to force).
- `BoardConfig.java` (CONFIG, default phase) — `PlaceholderProvider` bean (`new PapiText()`), `BoardManager`
  bean, `DefaultListenerService` bean, `CommandManager` bean (`new CommandManager(plaque, container, settings,
  Plaque.FULL_PREFIX, Plaque.FULL_PREFIX)` — same constructor shape Bartizan's `WiringConfig` uses).
- `BoardSettings.java` (C7, ~40 lines with docs) — reads `Enable`/`Driver` off `settings.yml` via plain
  `FileConfiguration.getBoolean`/`getString` defaults, **not** Gangland's `NodeReader` validation layer (that
  class lives in `gangland-api`, is Gangland application code, and is not part of Keystone — confirmed by
  reading `Settings.java` directly before deciding against porting it).

**Board (`board/`)**
- `BoardManager.java` (was `ScoreboardManager.java`) — the driver factory plus, new in this repo, the
  `Map<UUID, Board>` registry Gangland's `User`/`UserManager` used to hold per-player (this plugin has neither).
  `getDriverHandler` hardcodes `DriverV3` (D1(b)) and `log.warn`s on an unrecognised `Driver:` value instead of
  silently reproducing the deleted `DriverV1` fallback (the CLAUDE.md-documented silent-migration risk).
  `createBoard`/`removeBoard`/`sweepOnlinePlayers` (B5)/`rebuildAll`/`removeAll` are new. `viaApi` is a
  `@Setter`-mutable field, **not** constructor-injected — `DependencyContainer.registerInstance` calls
  `instance.getClass()` on whatever is passed, which would NPE on a null `ViaAPI` (the common case, ViaVersion
  absent); confirmed this risk by reading `DependencyContainer.java` directly before choosing the imperative
  `Plaque#dependencyHandler()` → `boardManager.setViaApi(Via.getAPI())` wiring instead (documented in CLAUDE.md).

**Listeners (`listener/`)** — flat, not `listener/player/` (this plugin is not a Gangland module; the WS1 plan's
own file tree places both flat, and `feedback_listener_package` is a Gangland-monorepo convention, not binding
here — noted in CLAUDE.md so a future session doesn't "fix" this into a subpackage).
- `PlayerBoardListener.java` — was `PlayerScoreboardListener`; `PlayerJoinEvent` (not Gangland's
  `UserDataInitEvent`) → `BoardManager#createBoard`.
- `RemoveBoardListener.java` — was the scoreboard branch of `RemoveAccountListener`/`UserManager.onPreClear()`;
  `PlayerQuitEvent` → `BoardManager#removeBoard`. The other half of that upstream cleanup (every board still
  active at shutdown) is `BoardManager#removeAll()`, called from `Plaque#onDisable()`.

**Command (`command/`)**
- `ReloadCommand.java` — keystone-command `Command` (see correction above), shape copied from Bartizan's own
  `ReloadCommand.java`. Reloads `FileManager`, runs `context.reloadBeans()`, then `BoardManager#rebuildAll()`.
  Deliberately **no** `InformationManager`/`HelpInfo`/`commands.json` — that help-listing machinery exists in
  Bartizan/Gangland to list *many* commands; Plaque has exactly one, so `help()` is a single literal line through
  `ChatUtil.color()` (ponytail — confirmed by reading keystone-command.md: "Help rendering is the subclass's job
  ... Keystone ships no help metadata layer", so skipping the listing layer is not skipping anything the
  framework requires).

**Placeholder (`placeholder/`)**
- `PapiText.java` — implements `PlaceholderProvider` directly, backed by
  `CompositePlaceholderProvider.of(papiOrPassthrough, serviceFallbackLambda)` (keystone-common's own chain
  utility — confirmed its "each provider receives the previous provider's output, unresolved tokens flow onward"
  semantics by reading `CompositePlaceholderProvider.java` before relying on it, since that pipeline behaviour is
  exactly what makes "raw text last" free — it's not a third provider, it's simply what happens when both
  providers leave a token unresolved). PAPI presence checked once at construction (touches PAPI's static bridge,
  unsafe when the plugin is absent); the `ServicesManager` fallback is a lambda re-resolving the registration on
  every call, never cached.

**`Plaque.java`** — full G2 rewrite. `onEnable`: build `PlaqueContext`, `bootstrap()`, `dependencyHandler()`
(ViaVersion link + `BoardManager.setViaApi`; PlaceholderAPI link-or-boot-warning, WS1-D3), then
`BoardManager#sweepOnlinePlayers()` (B5). `onDisable`: `BoardManager#removeAll()` before `shutdownBeans()`.

**Resources**
- `settings.yml` (new) — top-level `Enable`/`Driver` (not nested under a `Scoreboard:` block — this file owns
  nothing else), same default values as Gangland's `Scoreboard:` block, same ASCII banner style.
- `scoreboard.yml` (new) — Gangland's 108 lines copied byte-for-byte (title + 15 rows), with a header-comment
  block added stating the schema-compatibility guarantee and the PAPI requirement (WS1-D3's "header comment"
  half; the "boot warning" half is `Plaque#dependencyHandler()`'s `log.warn`).
- `plugin.yml` — added `commands: plaque:` (aliases `plq`, permission `plaque.command.main`) and
  `permissions: plaque.command.main: default: op`, needed for `PlaqueContext.runCommandPhase()`'s
  `plaque.getCommand(Plaque.FULL_PREFIX)` to resolve a non-null `PluginCommand`.
- `pom.xml` — added `keystone-command` (provided).

**Docs** — `CLAUDE.md`: full package map through G2, a new "Command framework: keystone-command, not a plain
CommandExecutor" section explaining the plan-vs-decisions-register conflict and its resolution (so a future
session doesn't revert to the plan document's stale recommendation), "ViaAPI wiring" section, two testing-gotcha
sections (`BukkitStatics` only pre-wires four statics; testing a bean that only orchestrates other beans).
`README.md`: G2 marked done, a new "Running it" section, expanded config-compatibility note.

## Deviations from the plan

- **Command framework**: keystone-command instead of plain `CommandExecutor` — per the coordinator's explicit
  correction, not a deviation I introduced; flagged here for traceability against `WS1-scoreboard.md`'s own text,
  which still reads the other way and should be treated as superseded for this point (see CLAUDE.md's note).
- **BoardManager owns the board registry directly** (a plain `Map<UUID, Board>`) rather than a separate
  registry/service class. The plan's file tree only names `BoardManager.java`, not a second holder class; since
  this plugin has no `User` to attach a board reference to, some class has to hold the map, and splitting it into
  `BoardManager` + `BoardRegistry` for four small methods would be an unrequested abstraction (ponytail).
- **No `InformationManager`/`commands.json`** even though keystone-command is now in use — S2's original
  reasoning ("nothing reads it, one command doesn't need a help-listing layer") still holds independently of
  which command *dispatch* framework is chosen; keystone-command's own docs confirm help rendering is
  consumer-optional, not framework-required.

## Red-first evidence

Two behaviours were built with a deliberately incomplete first version, tested, then completed:

**B5 (`BoardManagerTest`, both cases)** — `BoardManager.sweepOnlinePlayers()` was left as an empty stub
(`// TODO(B5): populate from Bukkit.getOnlinePlayers()`) while everything else in the class was written for
real. Red command: `mvn test -Dtest=BoardManagerTest,PapiTextTest`:
```
[ERROR] BoardManagerTest.sweepOnlinePlayers_reachesEveryOnlinePlayer:48
  expected: <[Mock for Player.., Mock for Player..]> but was: <[]>
[ERROR] BoardManagerTest.rebuildAll_killsAndRecreatesEveryOnlinePlayersBoard:64
  the board must be rebuilt for the still-online player ==> expected: <[Mock for Player..]> but was: <[]>
```
Fix: restored the real body (`if (!settings.isEnabled()) return; Bukkit.getOnlinePlayers().forEach(this::createBoard);`).

**D5 fallback (`PapiTextTest.papiAbsent_serviceFallbackRegistered_fallbackResolves`)** — `PapiText`'s
`CompositePlaceholderProvider` chain was built with only the PAPI-or-passthrough provider, omitting the
`ServicesManager` fallback lambda entirely. Same red run:
```
[ERROR] PapiTextTest.papiAbsent_serviceFallbackRegistered_fallbackResolves:69
  expected: <Purse: 500> but was: <Purse: %gangland_user_balance%>
```
(The other two `PapiTextTest` cases — PAPI-absent-no-fallback and PAPI-present-percent-free-text — were already
green in this same run, confirming the failure was isolated to the fallback path, not a broken harness.) Fix:
added the `serviceFallback` lambda to `CompositePlaceholderProvider.of(papi, serviceFallback)`.

Green command: `mvn clean install` after both fixes — `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`,
`BUILD SUCCESS`.

Other G2 code (bootstrap wiring, config beans, listeners, the command's `FileManager`/`reloadBeans()` calls) is
plumbing with no meaningful "wrong behaviour" to demonstrate red against — it was verified by the full green
build + the two targeted tests' real dependencies (`BoardManager` itself, `PapiText` itself) exercising it, per
LEAD-RULES' intent (proving behaviour, not performing red-first theater on wiring code).

## Build

Final command: `mvn clean install` (single-module reactor).
```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  6.797 s
```
Shaded jar verified by hand (`jar tf target/Plaque-0.1.0.jar`): every `org.luckyraven.plaque.*` class present
(18 main classes across `board`/`bootstrap`/`command`/`config`/`listener`/`placeholder`), the relocated
`org.luckyraven.plaque.dependency.fastboard.*`, `plugin.yml`/`settings.yml`/`scoreboard.yml`/`module.properties`
— **no** `org.luckyraven.keystone.*` class leaked in, confirming `keystone-command` (this gate's new dependency)
stayed `provided` and excluded from the shade exactly like the other four Keystone modules.

## Docket ids touched

None written this gate — same as G0/G1, the docket entries this workstream touches (UI-01/UI-02/UI-17/UI-16/
US-19) are unaffected by G2's bootstrap/config/command work; their dispositions were already fixed at the G1 port
and are scheduled for `write_db` at G4 per the plan's own step ordering (`read_db` first, per CLAUDE.md).

Docket candidates: none new. One thing worth a note for whoever runs G4's doc sweep: the live
`WS1-scoreboard.md` plan document's §9 D2 text is now **stale** relative to the decisions register (WS1-D2) and
this repo's shipped code — not a bug, but worth a one-line amendment to the plan doc itself so a future reader
doesn't trust the stale recommendation over the decisions register.

## Subagents used

None. Same reasoning as G0/G1: this gate required precise cross-referencing of exact Keystone 1.9.2 API
signatures (`DependencyContainer.registerInstance`'s null-instance NPE risk, `CompositePlaceholderProvider`'s
pipeline semantics, `BukkitStatics`'s actual pre-wired-statics list) verified directly against decompiled/source
Keystone classes before writing code that depends on them — handing this off would have meant re-deriving the
same verification, at higher fidelity risk, for no time saved.

## Concerns / open questions

- **`WS1-scoreboard.md` §9 D2 is now stale** relative to the decisions register and this repo (see "Docket
  candidates" above) — recommend a one-line amendment when G4's doc sweep runs, so the plan document doesn't
  contradict what actually shipped.
- `ReloadCommand.onExecute`'s `context.reloadBeans()` call and the full COMMAND-phase wiring
  (`CommandManager`/`BrigadierTabRegistrar`) are **not** covered by a JUnit test — they need a live/mocked full
  bean graph and a real `PluginCommand`, which is disproportionate to build for this gate; the individually
  testable unit underneath it (`BoardManager.rebuildAll()`) is covered. This mirrors Bartizan's own testing
  boundary (no `ReloadCommandTest` there either) but is worth a G-final smoke-server check (`/plaque reload`
  actually dispatches and rebuilds boards) rather than leaving it purely as an assumption.
- Diagnostics (`KernelConfig.diagnostics()`) was added even though nothing in the WS1 plan names it explicitly —
  justified because keystone-command's own docs state command-dispatch errors funnel through `Diagnostics.active()`,
  and every other Keystone-powered plugin in this studio (Bartizan included) wires it in `KernelConfig` for
  exactly that reason; flagging in case the orchestrator wants it called out as a plan gap rather than an
  unremarked addition.

## Fix round 1

Opus review at `exec/PLAQUE/G2-review.md`, verdict FIX (2 Important, 3 Minor), orchestrator rulings W19. All 5
items addressed in this round (item 5 = README note only, per the ruling — no chain rebuild on reload).

**Important 1 — `ViaAPI` off `BoardManager`, onto `Plaque`.** `BoardManager`'s `@Setter private ViaAPI<?> viaApi`
generated `public void setViaApi(ViaAPI<?>)` — a soft-dependency type in a registered bean's public method
descriptor, which `BeanFactory.runPostConstruct`'s reflective `getDeclaredMethods()` scan resolves for every
bean, throwing `NoClassDefFoundError` for the whole class on a ViaVersion-less boot (`ReflectionGuard.orSkip`
then silently skips `BoardManager`'s post-construct wiring). Fixed: `Plaque` gained `private ViaAPI<?> viaAPI`
(class-level `@Getter` → `getViaAPI()`), set once in `dependencyHandler()` after `context.bootstrap()` — `Plaque`
is safe to hold this because `PlaqueContext` `registerInstance`s it directly, never scanning it reflectively.
`BoardManager`'s constructor now takes `Plaque plaque` instead of `JavaPlugin plugin`; `getDriverHandler(Player)`
reads `plaque.getViaAPI()` inline, every call, never cached in a field of its own. `BoardManagerTest`'s
`CountingBoardManager` updated to the new `Plaque`-typed constructor.

**Important 2 — `ReloadCommandTest`.** New test (`src/test/java/org/luckyraven/plaque/command/ReloadCommandTest.java`,
same package as `ReloadCommand` since `onExecute` is `protected`) mocks `Plaque` and `PlaqueContext` (`final`,
Mockito 5.x's default inline mock maker handles it) under `BukkitStatics`, stubs `context.get(FileManager.class)`/
`context.get(BoardManager.class)`, calls `onExecute(null, sender, new String[0])` directly, and asserts via
`org.mockito.InOrder` that `fileManager.initializeAll()` → `context.reloadBeans()` → `boardManager.rebuildAll()`
fire in that exact order. Red-first evidence below.

**Minor 3 — unrecognised-`Driver:` warning moved.** Was in `BoardManager.getDriverHandler()` (fired once per
board, i.e. once per player join/reload-sweep entry — spammy). Now in `BoardSettings.initialize()` (fires once
per file load/reload). `BoardSettings` gained `@CustomLog`.

**Minor 4 — `rebuildAll()` simplified and made exhaustive.** Was `for (Player p : Bukkit.getOnlinePlayers()) removeBoard(p); sweepOnlinePlayers();`
(only removed boards for players still online at the moment of reload). Now `removeAll(); sweepOnlinePlayers();`
— exhaustive over the tracked board map regardless of a player's current online status. `BoardManagerTest`'s
`rebuildAll_killsAndRecreatesEveryOnlinePlayersBoard` test (which asserted the old per-player
remove-then-recreate shape) was replaced with `rebuildAll_callsRemoveAllThenSweepOnlinePlayers`, which overrides
`removeAll`/`sweepOnlinePlayers` on a fresh anonymous `BoardManager` subclass and asserts call order.

**Minor 5 — README note.** Added to "Running it": PlaceholderAPI presence is detected once, at enable —
`/plaque reload` does not re-check for it, so installing PlaceholderAPI after Plaque has already started needs a
restart (or a full plugin-manager reload) before `%gangland_*%` tokens resolve through it; the `ServicesManager`
fallback has no such limitation (looked up fresh every render). No code change — the orchestrator's ruling was
explicit that item 5 is documentation only, not a chain rebuild.

**Docs also updated:** `CLAUDE.md`'s "ViaAPI wiring" section rewritten to describe the actual bug and the fix
(not just "no", the *why*, so a future session doesn't reintroduce a `ViaAPI`-typed bean method); "Silent
migration warning" section updated to point at `BoardSettings.initialize()`; "Testing a bean that only
orchestrates other beans" section updated for the two-subclass `BoardManagerTest` shape and the new
`ReloadCommandTest`.

### Red-first evidence (Important 2)

`ReloadCommand.onExecute`'s `context.reloadBeans()` and `boardManager.rebuildAll()` calls were temporarily
swapped (`rebuildAll()` moved before `reloadBeans()`) while `ReloadCommandTest` was already written. Red command:
`mvn test -Dtest=ReloadCommandTest,BoardManagerTest`:
```
[ERROR] ReloadCommandTest.onExecute_reloadsInOrder:50
Verification in order failure
Wanted but not invoked:
boardManager.rebuildAll();
Wanted anywhere AFTER following interaction:
plaqueContext.reloadBeans();
```
(`BoardManagerTest`'s 2 cases — including the new Minor-4 order test — were already green in this same run,
confirming the failure was isolated to the deliberately swapped order, not a broken fixture.) Fix: restored the
original order (`initializeAll()` → `reloadBeans()` → `rebuildAll()`).

### Build

Final command: `mvn clean install` (single-module reactor), after all 5 fixes:
```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  5.296 s
```
(11 tests from G2 + 1 new `ReloadCommandTest`; `BoardManagerTest`'s test count stayed at 2 — one test replaced,
not added.) Shaded jar re-verified clean (`jar tf target/Plaque-0.1.0.jar`): no `org.luckyraven.keystone.*` leak.

### Files touched this round

`src/main/java/org/luckyraven/plaque/Plaque.java`, `board/BoardManager.java`, `config/BoardSettings.java`,
`command/ReloadCommand.java` (touched only transiently for the red-first swap, restored to its G2 shape — final
diff against G2 is zero), `CLAUDE.md`, `README.md`. New:
`src/test/java/org/luckyraven/plaque/command/ReloadCommandTest.java`. Modified test:
`src/test/java/org/luckyraven/plaque/board/BoardManagerTest.java`.
