# Review — Plaque G2 — 2026-09-16 (Opus, transcribed; the reviewer was cut by the session limit right after delivering this text)
Verdict: FIX (2 Important, 3 Minor)

## Spec table (condensed)
PlaqueContext on Keystone beans (Bartizan twin, FILE hook → `FileManager.initializeAll()`) ✅ · settings.yml + scoreboard.yml FILE beans ✅ (BoardAddon/BoardSettings correctly in FILE, plan §2/§4 said CONFIG — plan text to amend) · B5 enable-time sweep `Plaque.java:35` after `dependencyHandler()` ✅ · `PapiText` PAPI → fresh ServicesManager `PlaceholderProvider` → raw (`PapiText.java:22-35`) ✅ · WS1-D3 boot warning `Plaque.java:78` + `scoreboard.yml:16-22` header ✅ · `/plaque reload` via keystone-command, console allowed, permission `plaque.command.reload`, COMMAND phase real ✅ · settings.yml keys = Gangland's `settings.yml:136-145` ✅ · scoreboard.yml `Board:` block byte-identical ✅ · no commands.json (keystone-command needs none) ✅ · build 11 tests, jar clean ✅ · G2 smoke row (Plaque + Keystone only) ❌ not run · PapiTextTest ✅ · BoardManagerTest (= plan's BoardManagerEnableSweepTest) ✅ · ReloadCommandTest ❌ not written (F2) · D1 unrecognised Driver warns per board (F3) · UI-01/UI-02, no listener/bean overlap ✅ · reload/shutdown leak-free (`rebuildAll` stops timers + deletes FastBoards; `onDisable` → `removeAll`) ✅ · docs ✅

## Findings
**Important 1** — `BoardManager.java:45-46` `@Setter private ViaAPI<?> viaApi`: a soft-dependency type in a public method descriptor of a registered bean → `BeanFactory.runPostConstruct` (`BeanFactory.java:312,565`) calls `getDeclaredMethods()` → `NoClassDefFoundError` without ViaVersion → `ReflectionGuard.orSkip` logs a `reflection.type.missing` fault on every ViaVersion-less boot and silently skips `BoardManager`'s post-construct wiring. Fix: keep `ViaAPI` on `Plaque` (`@Getter`, set in `dependencyHandler()`; `Plaque` is `registerInstance`d, never method-scanned) and read `plaque.getViaAPI()` inside `BoardManager.getDriverHandler`'s body; `BoardManager` ctor takes `Plaque`.
**Important 2** — no test over `ReloadCommand.onExecute` (`ReloadCommand.java:38-52` sequences `initializeAll()` → `reloadBeans()` → `rebuildAll()`); plan §7 names `ReloadCommandTest`. One test under `BukkitStatics` with a mocked `PlaqueContext` asserting the three calls in order.
**Minor 3** — `BoardManager.java:62-67` unrecognised-`Driver:` warning fires per board build; move to `BoardSettings.initialize()` (once per load/reload) as CLAUDE.md/settings.yml promise.
**Minor 4** — `rebuildAll()` (`:101-106`) iterates `Bukkit.getOnlinePlayers()`, not the board map; `removeAll(); sweepOnlinePlayers();` is exhaustive.
**Minor 5** — `PapiText` latches PAPI presence for the JVM lifetime (not rebuilt on `/plaque reload`); document or rebuild the chain in `ReloadCommand`.

## Cannot verify
G2 smoke row (Plaque + Keystone only — would surface F1's WARN); the D5 producer half (Gangland publishing `GanglandPlaceholder.asProvider()`, 0.10.0 WS6 work); `context.reloadBeans()` is a no-op today; `FastBoard.delete()` on quit unchanged from upstream.

## Notes
- Docket candidate (Keystone `KS-`): `Diagnostics.install(hub)` sets a process-wide static in unrelocated keystone-common; Gangland, Bartizan and Plaque each install a hub — last enabled wins (`KernelConfig.java:33-38`, `Diagnostics.java:119-121`).
- Plan defects to amend at G4: §9 D2 `CommandExecutor` recommendation stale (WS1-D2/W5); §2/§4 BoardSettings/BoardAddon phase = FILE; §7 test name = BoardManagerTest.
- Behavioural delta (not a defect): join listener always registered, gated inside `createBoard` → `Enable` toggles on reload.

## Orchestrator rulings (W19)
Fix 1–5 in one round (5 = README note, not a chain rebuild). Smoke row for Plaque + Keystone only: run at G-final together with the Gangland 0.10.0 WS1 G3 row (needs the test server; not blocking). Docket candidate → `KS-` triage row at the wave's docket step. Plan §9 D2/§2/§7 amendments → WS1 plan §0d note by the orchestrator.
