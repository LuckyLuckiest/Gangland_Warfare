# Plaque 0.1.0 lead brief — the scoreboard plugin (WS1 G0–G2; the Gangland removal G3 is 0.10.0 work)

Rules: `../LEAD-RULES.md` (read first).

## Repo
New sibling repo `E:\Programming\java\Plaque` (the orchestrator runs `git init` + first commit of an empty README
before dispatching you; verify). Name decided by the user (N1 = **Plaque**). Layout = Bartizan's repo layout
(`E:\Programming\java\wt\bartizan-0.4.0` is a clean reference: `${revision}` + flatten + Central profile,
`plugin.yml` `depend: [Keystone]`, `softdepend: [PlaceholderAPI, ViaVersion]`), **single Maven module, no `-api`**.
Keystone pin **1.9.2** (`~/.m2` has it). Java 17, Spigot 1.16.5 API floor.

## Plan (binding)
- `brainstorming/decoupling-wave-2026-09-14/plans/WS1-scoreboard.md` (whole file; §0b review response).
- `../../PLAN.md` §4 and §10: WS1-D1 = port **DriverV3 only** (silent-migration note: `ScoreboardManager.java:67`
  falls back to V1 today); **WS1-D2 = keystone-command** (the user: "stick with keystone implementation since they
  already have the whole library") — the `reload` command is a Keystone command, not a plain `CommandExecutor`;
  WS1-D3 = boot warning + header comment when PAPI is absent, graceful raw text; A5/WS1-D5 = placeholders resolve
  PAPI first, then an optional Keystone `PlaceholderProvider` found on the `ServicesManager`, then raw text.
- Sources to port (read-only): `E:\Programming\java\wt\gangland-0.9.2\gangland-ui\scoreboard-api\` (8 files +
  2 tests, FastBoard + ViaVersion) and the impl wiring the plan lists; the `scoreboard.yml` schema is kept
  **identical** so owners copy the file.

## Gates — STOP and report after each
| Gate | Content | Report (under `exec/PLAQUE/`) |
|---|---|---|
| G0 | repo skeleton, pom (shade FastBoard only; Keystone `provided`), `plugin.yml`, CLAUDE.md/README stubs, `mvn clean install` green on an empty plugin | `G0-report.md` |
| G1 | rendering engine ported, DriverV3 only, UI-17 2-line guard, UI-16 gone with V1/V2, tests ported | `G1-report.md` |
| G2 | bootstrap on Keystone beans, `scoreboard.yml` (same schema), placeholder chain (PAPI → `PlaceholderProvider` → raw), enable-time board sweep, `/plaque reload` via keystone-command, docs | `G2-report.md` |

No Gangland file changes in your stream.
