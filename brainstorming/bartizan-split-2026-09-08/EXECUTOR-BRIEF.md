# Executor brief (Sonnet agents)

You execute one task group of one stream checklist in `brainstorming/bartizan-split-2026-09-08/` (`keystone-1.9.0.md`,
`bartizan.md` or `gangland-0.9.0.md`). You will be told the stream, the task ids and the repo. Read, in order:
`README.md` (decisions D1–D10, topology, gates), the stream checklist (whole file, then your tasks), the architecture
pick in `architecture/PICK.md`, and the reference files the checklist names.

## Repos and branches

| Stream | Repo | Branch | Build |
|---|---|---|---|
| Keystone 1.9.0 | `E:\Programming\java\Keystone` | `phase-h8-item-npc` (cut from `phase-h7-module-loader`) | `mvn clean install -q` (installs to `~/.m2`, builds `keystone-plugin/target/Keystone-1.9.0.jar`) |
| Bartizan 0.1.0 | `E:\Programming\java\Bartizan` (new) | `master` | `mvn clean install -q` → `bartizan-plugin/target/Bartizan-0.1.0.jar` |
| Gangland 0.9.0 | `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]` | `0.9.0` (cut from `0.8.4`) | `mvn clean install -DskipTests -q`, `mvn test -q`, `mvn clean package -DskipTests -q` |

## Rules

1. Work only in the repo and branch your task names, main tree only. Never touch `.claude/worktrees/**`. Never commit,
   never switch branches, never `git checkout --` / `reset` anything you did not create. Never push.
2. Follow the task text literally. Where the checklist names a file, symbol or package, use exactly that. If the code
   differs from what the checklist says (a symbol moved, a line is gone), stop that task, record the difference in the
   status table, and continue with the next task that does not depend on it.
3. Run `graphify query "<identifier>"` before opening a file you were not pointed at (each repo has `graphify-out/`;
   Bartizan gets one after its first build — until then Grep is allowed there).
4. House rules from `CLAUDE.md`: Spigot only (no `io.papermc` imports); method braces on their own lines; Lombok
   `@CustomLog`; block-style YAML with `Capitalized_Underscore_Separated` keys; `ChatUtil.color()` with `&` codes;
   `SoundEffect`/XSeries for version-drifting enums; never `setDefaults/copyDefaults`; no test dependencies in a
   module pom; `commands.json` entries live in the jar that owns the command; every new `AbstractRepository` keeps
   its `setDataSupplier` wiring; keep `@Bean` parameters that only encode load order; a holder-seam `@Bean` declares
   the CONCRETE class as its return type; every contribution/seam implementation has its own `@Bean`.
5. Moving a file = `git mv` (history), then fix the `package` line and every import. Across repos (Gangland →
   Keystone or → Bartizan) it is copy + `git rm` on the source side; keep the class name unless the checklist renames it.
6. Compile gates are mandatory where the checklist puts them. Paste the first 30 lines of any failure verbatim into
   the status table.
7. Tests: run the test class the checklist names after each test task; run the stream's full test command at the end
   of a group that touches tests. A new test must be seen red before the fix (record the red output), then green.
8. After every task, update the checklist's section 7 status table: `done` / `blocked` / `skipped`, what changed
   (files, counts), what you left out and why. Never mark a task done with a failing gate.
9. Do not widen scope. A bug you notice goes into
   `brainstorming/bug-docket-2026-09-06/triage/<slug>.txt` as one line in the file's `~~` format (see
   `triage/new-findings.txt`), not into the code, unless the checklist task is the fix.
10. Report at the end: tasks done, gates passed with the exact command, anything skipped, open questions. Facts only;
    if a step was skipped say so.
