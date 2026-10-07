# Lead-executor rules (decoupling wave execution, 2026-09-16)

Read this first. It binds you and every subagent you spawn.

## Where to work
- Your worktree is named in your BRIEF.md. **Only** edit, build and run git *read* commands there.
- The main checkouts — `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]`, `E:\Programming\java\Bartizan`,
  `E:\Programming\java\Keystone`, `E:\Programming\java\Oriel` — hold **someone else's uncommitted work**. Never edit,
  build, stash, checkout or commit there. Reading files there is fine (plans under `brainstorming/`, graphify).
- The plan folders live only in the main Gangland checkout (untracked): `brainstorming/decoupling-wave-2026-09-14/`
  and `brainstorming/gadget-wave-2026-09-16/`. Reports go to `brainstorming/decoupling-wave-2026-09-14/exec/<stream>/`.

## Orientation
- **graphify first.** For any "how does X work / who calls X / what breaks if X changes" question run, with cwd =
  the main Gangland checkout: `graphify query "<Identifier>"`, `graphify explain "<Class>"`,
  `graphify affected "<Class>"` (before any refactor), `graphify path "<A>" "<B>"`. The graph was refreshed today
  (AST, 0.9.1 + working tree). Only then open the files it points at. Quote `source_location` in reports.
- Keystone has its own `CLAUDE.md` (tracked). Gangland's `CLAUDE.md` is copied into its worktree root. Read the
  one for your repo before editing.

## House rules that reviewers will enforce
- Spigot only (no `io.papermc.*`). Java 17, no Java 21 APIs. API floor Spigot 1.16.5.
- Every method body's braces on their own lines — never `{ return x; }` one-liners.
- Lombok `@CustomLog` + `log.warn/info/error`; never `Bukkit.getLogger()`.
- Version-drifting enums through XSeries (`XMaterial`/`XParticle`/`XPotion`), sound through Keystone
  `SoundEffect`; never raw `Sound.X`/`Material.valueOf`.
- Keystone artifacts are `provided`, never shaded. A runtime module compiles against `gangland-api` only.
- YAML: block-style maps only, keys `Capitalized_Underscore_Separated`, lookup ids lowercase, no literal `§`
  (use `&` codes through `ChatUtil.color`). Never `setDefaults()`/`copyDefaults()`.
- Every new `/glw` sub-command gets a `commands.json` entry in the jar that owns it; positional input = chained
  `OptionalArgument` nodes with tab-completion.
- Stateful items need an `ItemRefresher` registered per type. Never `@ListenerHandler` + `@Bean` the same class.
  A listener whose handler parameter type belongs to a soft dependency carries
  `@ListenerHandler(condition = "is<Plugin>Available")` and lives in its own class.
- `getWorld()` is nullable — local + null-check. `Timer.start(true)` is async — Bukkit API needs `start(false)`.
- Ponytail: the shortest diff that satisfies the plan. No interface with one implementation, no registry for one
  entry, no config for a value that never changes. Mark deliberate ceilings with a `ponytail:` comment.
- Bug docket: every docket id your gate touches is listed in the report with what changed. A new bug you notice
  goes in the report under "Docket candidates" (the orchestrator files it).

## Tests and builds
- **Red first.** A new or flipped test must be shown failing against the pre-change code (run it before the
  production edit, or `git stash`-free: write the test, run, then implement). Record the red command + the one
  failing line in the report, then the green run.
- Test conventions: `documentation/TESTING.md` in the Gangland worktree is authoritative; never add test
  dependencies to a runtime-module pom. DB-touching tests: `@TempDir(cleanup = CleanupMode.NEVER)`.
- Builds: `mvn clean install` in the worktree root (`-DskipTests` for a compile check; `-pl <module> -am` for one
  module). **Never run two Maven processes of your own at the same time.** Another lead may be building a different
  repo — that is fine. Final gate state = the whole reactor `mvn clean install` green; paste the
  `BUILD SUCCESS`/`Tests run:` summary lines, not the log.
- Long output: pipe through `| tail -40` or grep for `ERROR|FAIL|Tests run|BUILD`; do not paste Maven logs.

## Subagents
- You MAY spawn subagents with the `Agent` tool: `subagent_type: "general-purpose"`, always an explicit `model`:
  `haiku` for mechanical, fully specified work (single-file edits with the exact text given, census/grep
  transcription, YAML edits); `sonnet` for multi-file implementation from a prose gate spec. Never `opus`.
- At most **2** subagents running at once. Never spawn a reviewer — the orchestrator reviews every gate.
- Every subagent prompt must contain verbatim: *"Run `graphify query` first with cwd = the main Gangland checkout;
  read raw files only after the graph has oriented you. Do not spawn sub-agents. Do not commit. Work only inside
  `<worktree path>`. Read `<this file>` and follow its house rules."* and must hand over the exact files to touch.
- Verify a subagent's work yourself (build + the covering tests) before you call the gate done.

## Git
- **Never commit, tag, push, stash, reset or checkout.** The orchestrator commits at gate boundaries with
  `git commit -- <paths>`. `git status`/`git diff` are yours to use.

## Report contract (one file per gate batch, path given in your brief)
```
# <stream> <gates> report — <date>
Status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
## What changed            (bullet per step of the plan's gate table; file paths relative to the worktree)
## Deviations from the plan (and why; "none" if none)
## Red-first evidence       (test, red command + failing line, green command + summary line)
## Build                    (final command + BUILD SUCCESS line + Tests run summary per touched reactor)
## Docket ids touched       (id → what changed) and Docket candidates (new bugs noticed)
## Subagents used           (model, gate, one line each)
## Concerns / open questions
```
Return to the orchestrator only: the status line, the report path, the list of files touched, one test-summary
line and any concerns. Nothing else — the report file carries the detail.

## W52 (2026-09-22) — test totals
Sum the per-module `Tests run:` rollup lines from the Maven console, not `target/surefire-reports/*.txt`: Surefire's
plain-text reporter overwrites one file per test class, so a class with several JUnit 5 `@Nested` inner classes reports
only the last one written (WS5's User/Bounty/Wanted/RankManager tests under-count by ~41 that way).

## W56 (2026-09-23) — module beans and boot proof
- A module class annotated `@Configuration` must not take core beans through its constructor: the bean graph orders
  beans by `@Bean` method parameters only, so a bare `@Configuration` with constructor parameters has no edge to the
  core bean it needs and fails at boot (`Cannot resolve required parameter …`, docket T-53). Produce such objects from
  a `@Bean` factory method whose parameters name every dependency.
- A gate that adds, moves or rewires a module configuration is not green on `mvn verify`: it needs the console boot
  smoke with every runtime module deployed (`Runtime modules: N loaded, 0 fault(s)`), and a server WITHOUT the module
  must not log repository faults (T-54: a core repository whose only supplier moved into a module).
