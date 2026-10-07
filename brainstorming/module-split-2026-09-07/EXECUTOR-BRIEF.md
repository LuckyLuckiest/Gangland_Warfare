# Executor brief (Sonnet agents)

You execute one task group of one feature checklist in `brainstorming/module-split-2026-09-07/<feature>.md`.
You will be told the feature and the task ids. Read, in order: `README.md` (ground rules and gates), the feature
checklist (whole file, then your tasks), `documentation/module-loader.md`, and the mail module under
`gangland-features/gangland-mail/` as the reference for every file you create.

## Rules

1. Work on branch `0.8.4` in the main tree only. Never touch `.claude/worktrees/**`. Never commit, never switch
   branches, never `git checkout --`/`reset` anything you did not create.
2. Line numbers in the checklists are as of commit d6bb33ac. After an earlier flip or the `0.8.3` merge has touched
   a file, locate by symbol name, do the task, and record the drift in the status table. Follow the task text literally. Where the checklist names a file, symbol or package, use exactly that. If the
   code differs from what the checklist says (a symbol moved, a line is gone), stop that task, record the difference
   in the status table, and continue with the next task that does not depend on it.
3. Run `graphify query "<identifier>"` before opening a file you were not pointed at.
4. House rules from `CLAUDE.md`: Spigot only; method braces on their own lines; Lombok `@CustomLog`; block-style
   YAML with `Capitalized_Underscore_Separated` keys; `ChatUtil.color()` with `&` codes; no test dependencies added
   to a module pom; `commands.json` entries live in the jar that owns the command; every new `AbstractRepository`
   keeps its `setDataSupplier` wiring; keep `@Bean` parameters that only encode load order.
5. Moving a file = `git mv` (keeps history), then fix the `package` line and every import. Moved classes keep
   their names unless the checklist renames them.
6. Compile gates are mandatory where the checklist puts them. Use the module-scoped build the checklist names
   (`mvn -q -pl <module> -am install -DskipTests`) and the full `mvn clean install -DskipTests -q` at the group's
   end. Paste the first 30 lines of any failure verbatim into the status table.
7. Tests: run the test class the checklist names after each test task; run `mvn test -q` at the end of a group
   that touches tests. A new test must be seen red before the fix (record the red output), then green.
8. After every task, update the checklist's section 7 status table: `done` / `blocked` / `skipped`, what changed
   (files, counts), what you left out and why. Never mark a task done with a failing gate.
9. Do not widen scope. A bug you notice goes into `brainstorming/bug-docket-2026-09-06/triage/<slug>.txt` as a
   short note (id, file:line, what is wrong), not into the code, unless the checklist task is the fix.
10. Report at the end: tasks done, gates passed with the exact command, anything skipped, open questions. Facts
    only; if a step was skipped say so.
