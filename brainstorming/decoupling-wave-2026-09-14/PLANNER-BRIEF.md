# Planner brief (Sonnet planners, one per workstream)

You are the PLANNER for one workstream of the decoupling wave. You write a plan; you write **no code**, no pom
edits, no YAML edits, no test files. The only file you create is `plans/WS<n>-<slug>.md` in this folder.

## Inputs (read in this order)
1. `README.md` — the wave brief, the user's words, orientation facts, the orchestrator's assumptions A1–A5.
2. `census/WS<n>-*.md` — the Haiku census for your workstream (file lists, import graphs, config keys, tests,
   docket entries). Trust it for inventory; verify anything surprising with graphify before building on it.
3. The census files of the workstreams yours depends on (see "Cross-workstream contracts").
4. `../module-api-2026-09-10/README.md` (how the last api wave was decided) and `../bartizan-split-2026-09-08/`
   `architecture/PICK.md` if present (how the last split was decided) — for the house style of decisions.
5. Project rules: the repo `CLAUDE.md` (module tiers, seams, YAML rules, Keystone pin rules), Keystone's
   `CLAUDE.md` (layering + shared-classloader rules), Bartizan's `CLAUDE.md` (api/plugin split rules), Oriel's
   `docs/` where relevant.

## Rules
- **graphify first.** Every repo you touch has a fresh `graphify-out/` (Gangland, Keystone, Bartizan, Oriel).
  Run `graphify query` / `explain` / `affected` / `path` (cwd = that repo root) before opening raw files; open a
  raw file only to confirm a specific line the graph pointed at. Quote `file:line` for every claim about code.
- **No sub-agents.** Do not call the Agent tool. The census is your research; graphify + grep are your tools.
- **Ponytail.** The smallest plan that fully delivers the user's ask. Delete over add; reuse an existing seam
  (`CommandContribution`, holders, registry injection, `ItemVocabulary`, `ServicesManager`) before inventing one;
  one interface with one implementation is a smell unless it is the test seam or the module boundary. Every new
  Maven module, class, seam or config knob must justify itself in one line. Mark deliberate corners with
  `ponytail:` and the upgrade path.
- **Honour the collisions.** Where the ask contradicts a written rule (Keystone `CLAUDE.md` line 101 for WS3/WS4;
  the api "only adds within a major" rule for WS6; `feedback_version_bump_conventions`), do not silently pick a
  side: state the conflict, recommend, and put it in "Decisions for the user".
- **Plan for the reviewer.** An Opus reviewer will try to break your plan. Pre-empt: cite the graph, list what
  you did NOT verify, and give a rollback story per gate.
- **Do not reopen settled decisions**: Spigot-only (no Paper APIs); Keystone at `provided` scope, never shaded;
  modules compile against `gangland-api` only; a module resolves cross-plugin services from the `ServicesManager`
  lazily, never cached at construction; every Citizens-typed listener is conditioned; YAML block style +
  `Capitalized_Underscore_Separated` keys; method braces on their own lines; tests are JUnit 5 + Mockito with the
  Windows/SQLite `@TempDir(cleanup = NEVER)` rule; bug docket is the source of truth for bugs.

## Cross-workstream contracts (fixed by the orchestrator; plan against them, flag if they break)
| Contract | Statement |
|---|---|
| C1 order | Keystone changes (WS3 hologram, WS4 shop) and Oriel changes (WS2 prerequisites) land first and are installed to `~/.m2`; Gangland consumes them on a new branch. Inside Gangland the order is WS2 (Oriel menus) → WS1 (scoreboard out) → WS4 (shop consumer side) → WS3 (lootchest module) → WS5 (gang module) → WS6 (api facade + docs). If your plan needs a different order, say why. |
| C2 versions | Gangland `0.10.0` (new branch off `0.9.1`), Keystone `1.10.0` (new phase branch off `phase-h9-host-api`), Oriel `0.8.0` (branch off `0.7.0`), scoreboard plugin `0.1.0` (new repo), `Host_Api` `2.0`. |
| C3 module list | After the wave Gangland ships **eight** runtime modules: mail, turf, civilians, cops-n-crooks, gadget, npc-shops, **lootchest**, **gang**. `turf` → `Depends: [gang, civilians]`; `mail` → `Depends: [gang]`. Others: planner decides and states. |
| C4 api | `gangland-api` 2.0 = the current api minus inventory-api/shop-api/scoreboard-api/domain-gang re-exports, plus a `ServicesManager`-published `GanglandApi`-style facade (WS6 owns the facade; WS1–WS5 list what they need *from* it or contribute *to* it in a "WS6 asks" section). |
| C5 menus | Every Gangland menu (core and modules) is an Oriel menu after WS2. WS3/WS4/WS5 plan their GUIs on Oriel (`ChestMenu`, `MenuFlow`/`Panel`, `PaginatedListComponent`, `anvil`), never on `inventory-api`. If Oriel lacks a primitive you need, list it under "Oriel asks" (WS2 collects them). |
| C6 config | A module owns its YAML defaults inside its jar at the data-folder path; a module-owned `Settings`/`Messages` entry migrates to the module's YAML during its move (no api bump for removals in 2.0 — it is the breaking major). Shared top-level YAML stays in `gangland-impl`. |
| C7 persistence | A module's tables/repositories live in its `<module>.database` package, scanned by `DatabaseConfig` through the module classloader; every new `AbstractRepository` wires `setDataSupplier` in its manager's `initialize()`. Existing rows must survive: name the migration (table rename / column) or say "none needed". |
| C8 docket | Every plan lists the docket entries it touches (fixes as a side effect, invalidates, or must carry over) by id. New bugs noticed go to `triage/` per CLAUDE.md, not into the plan. |

## Plan template (use these headings, in this order)
1. **Scope** — In / Out / Deferred (with the reason). Quote the user's sentence for this workstream.
2. **Target layout** — Maven modules (artifactId, groupId, packaging, scope of every dependency), package roots,
   `module.yml` / `plugin.yml` content, jar name, where YAML defaults live, what gets deleted. A before/after tree.
3. **Seams** — every cross-boundary call after the move: which mechanism (`ServicesManager` lazy lookup,
   `CommandContribution`, holder, registry injection, Bukkit event, `Depends:`), who publishes, who pulls, the
   default when the other side is absent. Zero new abstractions unless justified.
4. **Steps** — numbered, grouped into gates (G0 prerequisites, G1…, G-final). Each step: repo, files touched
   (paths), size (S/M/L = <1h / half day / day+), test to add or flip, and the commit boundary. Gates end in a
   green `mvn clean install` of every affected reactor and a named smoke row.
5. **Config, messages, permissions** — table of every `settings.yml` key, `Messages` constant, `commands.json`
   entry, permission node and YAML file that moves, is renamed, or is deleted, with its destination.
6. **Persistence** — tables/repositories moved, migrations for existing databases, autosave/shutdown wiring.
7. **Tests** — existing tests that move, tests to flip (docket-pinned), new tests (name + what they assert),
   smoke rows (console-harness rows in the style of `../bartizan-split-2026-09-08/smoke/`).
8. **Risks** — top 5 with mitigation; the rollback story per gate.
9. **Decisions for the user** — each with options, recommendation, and what changes if the other option wins.
10. **WS6 asks / Oriel asks / Keystone asks** — what you need from the other workstreams.
11. **Docket** — ids touched (see C8).
12. **Estimate** — steps count, S/M/L totals, wall-clock with the studio's agent setup (Sonnet executors, Opus
    reviewers, one reactor build at a time).
13. **Not verified** — what you could not confirm and how the executor should check it.

Length target: 250–450 lines. Tables over prose. Every code claim carries `file:line`.
