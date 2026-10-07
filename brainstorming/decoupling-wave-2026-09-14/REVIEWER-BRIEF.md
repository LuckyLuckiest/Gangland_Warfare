# Reviewer brief (Opus reviewers, one per plan)

You review ONE plan in `plans/WS<n>-*.md` and write ONE file: `reviews/REVIEW-WS<n>.md`. You change nothing
else. No code, no edits to the plan (the planner applies your fixes). Do not spawn sub-agents.

## Inputs
`README.md`, `PLANNER-BRIEF.md` (the template and the cross-workstream contracts C1–C8), the plan, its census
`census/WS<n>-*.md`, and the plans of the workstreams it depends on. All four repos have a fresh `graphify-out/`;
use `graphify query/explain/affected/path` (cwd = repo root) to check the planner's claims before reading raw
files. Quote `file:line` for every disagreement.

## What to attack (in priority order)
1. **Wrong facts** — a class, bean, seam, YAML key or dependency the plan describes that does not exist or does
   not behave as claimed. Verify with the graph; then open the file at the cited line.
2. **Missing consumers** — a caller, listener, placeholder, command, sign, menu, test or docket entry that the
   move breaks and the plan does not mention. `graphify affected "<X>"` on every type the plan moves or deletes.
3. **Contract breaks** — C1–C8, the Keystone layering/shared-classloader rules, the Bartizan api rules, the repo
   CLAUDE.md rules (Spigot-only, provided-scope Keystone, module→api only, lazy `ServicesManager`, conditioned
   Citizens listeners, YAML style, method braces, Windows/SQLite test rule, docket workflow).
4. **Over-engineering** (ponytail) — an interface with one implementation that is neither the test seam nor the
   module boundary; a new Maven module for <5 classes; a config knob for a constant; a migration for data that
   can be regenerated; a seam where a Bukkit event or `Depends:` would do. Name the smaller alternative.
5. **Under-engineering** — a corner cut that loses data, breaks reload/shutdown ordering, leaks entities
   (holograms, NPCs), races timers, or leaves a server without Oriel/Keystone-1.10/Bartizan in an undefined state.
6. **Order and gates** — can each gate be built and smoked green on its own? Is there a rollback? Does the gate's
   test actually fail before the change (the "genuinely red" rule)?
7. **Decisions** — is every real fork surfaced to the user with a recommendation, and is nothing that is the
   user's call decided silently? Conversely, is anything surfaced as a "decision" that has an obvious default?
8. **Estimate honesty** — S/M/L sizes that hide a day of work; missing steps (docs, `commands.json`, graph
   refresh, docket record, smoke rows, `plugin.yml` softdepend edits, `gangland-build` shade list, memory notes).

## Output template
```
# REVIEW WS<n> — <plan title>
Verdict: PASS | PASS WITH FIXES | REWORK
## Blockers (must fix before executors start)
- B1. <claim> — <evidence file:line> — <required change>
## Corrections (fix in place)
- C1. ...
## Simplifications (ponytail)
- S1. <what to delete/merge> — <why it is safe>
## Missing consumers found by graphify affected
| Type moved | Consumer the plan misses | file:line | Impact |
## Decisions: agree / disagree with the planner's recommendation
| Decision | Planner rec | Reviewer view | Why |
## Estimate check
## Things I could not verify
```
Length: 80–200 lines. Every point has evidence or says "unverified".
