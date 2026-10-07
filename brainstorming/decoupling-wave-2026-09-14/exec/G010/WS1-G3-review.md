# Review — Gangland 0.10.0 WS1 G3 (scoreboard out, Plaque replaces it) — 2026-09-20 (Opus, transcribed)
Verdict: FIX (4 Important, 2 Minor) — code deletion complete and correct; findings are docs, docket and one seam.

## Spec table (condensed)
scoreboard-api module + gangland-ui pom ✅ · User.scoreboard, poms, onPreClear scoreboard half only ✅ · beans/lifecycle/listener/RemoveAccountListener/ReloadPlugin/ReloadCommand/commands.json ✅ · CM-04 `DownloadResourceCommand.java:31` → `Settings.isResourcePackEnabled()` ✅ (matches the docket's fix) · Settings/settings.yml/scoreboard.yml/bStats chart ✅ · gangland-build fastboard relocation + artifactSet, root version ✅ · zero `fr.mrmicky`/scoreboard left except the canary comment and a true `KernelConfig.java:104` comment ✅ · doc sweep ⚠ (I1, I2; CurrentlyWorking.txt left — acceptable) · docket ❌ (I3) · nothing deleted by accident (ViaVersion kept: `Gangland.java:3-4,51`, `DebugCommand.java:517-519`; placeholders untouched; domain tests intact; `UserDataInitEvent` still has the item bridge subscriber) ✅ · canary 154→153 ✅ · leftover `Scoreboard:` inert (pull-based `Settings.populate()`, `ConfigReport` only known keys) ✅

## Findings
**I1** — `documentation/developer/configuration.md:498-500` claims Plaque uses "`title`/`lines` with `text`/`update_interval`"; the real schema is `Board.Title.{Interval,Lines}` / `Board.Rows.<n>.{Interval,Lines}` (Plaque `scoreboard.yml:26-28,55-58`, identical to Gangland's deleted file). Fix the sentence: copy `plugins/Gangland_Warfare/scoreboard.yml` to `plugins/Plaque/scoreboard.yml` verbatim.
**I2** — `documentation/developer/modules.md:324` still says "Relocates … HikariCP, FastBoard"; only bStats is relocated now.
**I3** — US-19 missing from the report's docket section (stays open: one async `UserDataInitEvent` subscriber removed; root untouched — `CreateAccountListener.java:105` → `PlayerItemInitBridgeListener.java:20`); UI-16 should read wontfix/moved (DriverV1/V2 deleted under D1(b)).
**I4** — the D5 placeholder seam: Plaque consumes a Keystone `PlaceholderProvider` from the ServicesManager (`PapiText.java:30-35`, lazy, uncached) but Gangland publishes only `ItemVocabulary` (`GanglandContext.java:204-215`); no `asProvider`/`registerService` anywhere → Plaque's non-PAPI branch is dead. Either the ~5-line registration now or a note naming WS6.
**M5** — no owner-facing migration note (precedent `documentation/migration-0.9.2.md`). **M6** — worktree CLAUDE.md has no Plaque pointer; `CLAUDE.md:206,208` still say `Host_Api: 1.1` (G0 leftover).

## Cannot verify
Build/test (227 impl tests); the canary red observed naturally (acceptable); smoke row `no-scoreboard-boot` not added (harness in the main checkout); docket writes deferred.

## Notes
R8/W31 respected (`gangland-domain/pom.xml` still carries inventory-api for WS2). Plan defect: §2/C5 lists `brainstorming/CurrentlyWorking.txt` — a historical log, amend §2. Cross-gate: I4 is WS6 contract C4 if not fixed here.

## Orchestrator rulings (W36)
Land I4 now (additive, revert-safe): a `WiringConfig` registration of `GanglandPlaceholder.asProvider()` as Keystone `PlaceholderProvider` on the `ServicesManager` at enable, `unregisterAll` on disable (WS6's `unregisterAll`-first rule), one test; WS6's facade later reuses it. Fix I1, I2, I3 (report + docket rows via the clerk at the next docket step), M5 (`documentation/migration-0.10.0.md` started with the WS1 section: install Plaque, copy `scoreboard.yml`, carry the two `Scoreboard:` values, delete the inert block), M6 (CLAUDE.md Plaque pointer + Host_Api 2.0). Smoke: W35 lets the lead add `no-scoreboard-boot` and run plan §4 rows (a)/(d) with `paths.repo_dir` temporarily pointed at wt/gangland-0.10.0 (restore after). Plan §2/C5 amended by the orchestrator.
