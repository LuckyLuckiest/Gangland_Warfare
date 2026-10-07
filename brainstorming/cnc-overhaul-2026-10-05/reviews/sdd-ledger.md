# SDD ledger — plan: brainstorming/cnc-overhaul-2026-10-05/PLAN.md
Spec: brainstorming/cnc-overhaul-2026-10-05/SPEC-0.15.md (artifact U6qF8Ey5 extract, owner rulings header). Orchestrator = Opus 5.5 main session.
Integration worktree E:/Programming/java/wt/gangland-0.15.0, branch cnc-lose-them, base 16d1f064 (master = 0.13.0 + CopRadioTest fix).
Briefs: .superpowers/sdd/cnc-overhaul-0.15/task-<N>-brief.md. Reports: brainstorming/cnc-overhaul-2026-10-05/reports/T<N>.md.

## Setup
- 2026-10-05 H13 merged + pushed: Gangland master 415f34a5, Keystone b818483 (1.14.0), Bartizan 6c8fca9 (0.6.1). CopRadioTest fix 16d1f064 pushed.
- Planning workflow wf_ad1f7305-1e1: 7 Sonnet mappers (brainstorming/.../maps/), Opus planner, 3 lens reviewers (spec-coverage opus, code-grounding sonnet, parallel-tests opus: 6 critical, 11 important), Opus reviser applied 29, rejected 1 (reviews/plan-review-1.md).

## Runs
- Build workflow wf_4836461f-48d (task w38uzem0u) launched 2026-10-05: T1-T17 DAG (T18 later). Script: ~/.claude/projects/E--Programming-java-Gangland-Warfare--Cubed-GTA-recoded---superpowers-sdd/3ebacf97-51ee-4d9e-aa02-550a42127dcc/workflows/scripts/cnc-015-build-wf_4836461f-48d.js — resume with resumeFromRunId if killed; journal in subagents/workflows/wf_4836461f-48d/journal.jsonl.

- Build workflow done: 59 agents, all 17 lanes review-clean (fix rounds: T4, T7, T8, T9, T11 one each), 0 parked, 60 minors deferred, 74 implementer rulings -> brainstorming/cnc-overhaul-2026-10-05/reviews/build-deferred.md. cnc-lose-them head 07fdd4f9 (37 commits, 152 files, +9906/-532, revision 0.15.0).
Task 1: complete (merge 61f3e072, review clean) | Task 2: complete (e2bdddc3) | Task 3: complete (8c888342) | Task 4: complete (734e4dcb, 1 fix round) | Task 5: complete (36b3244e)
Task 6: complete (746742f2, merged with T7) | Task 7: complete (58abb411, 1 fix round) | Task 8: complete (8422a75b, 1 fix round) | Task 9: complete (9ec6a1aa, 1 fix round) | Task 10: complete (untracked authoring, DONE_WITH_CONCERNS)
Task 11: complete (b88119ad, 1 fix round) | Task 12: complete (7cafa2f9) | Task 13: complete (8f374878) | Task 14: complete (34232b3e) | Task 15: complete (d495e380) | Task 16: complete (07fdd4f9) | Task 17: complete (36c0fd78)
Task 18: pending (live acceptance run after final review)
- Full mvn clean install on 07fdd4f9: BUILD SUCCESS, 1760 tests, 0 failures.
- Final review workflow wf_5b9ae7de-0e6: 5 Opus lenses -> 24 raw -> 18 unique -> 17 confirmed / 1 refuted (F12). One Opus fix wave on cnc-0.15-final 743d7415..68f6d107 fixed 16, disputed F3 (2 stars for a lethal first cop hit = designed: Assault_Cop + Kill_Cop) — re-reviewer accepted; 0 new breakage; 1781 tests green. Fast-forwarded into cnc-lose-them = 68f6d107; jars rebuilt.
- Ruling: F3 (one-shot cop kill = 2 stars) stands as designed per CONTRACTS C6/C12 + PLAN risks 7/8 — cost if wrong: tune Assault_Cop/Kill_Cop weights in npc/wanted.yml.
- Ruling: F2 rule — only OTHER players' escrow makes a kill a crime-free takedown; a self-posted bounty is refunded but the kill stays a crime — owner should confirm; the accomplice token-bounty case (N2, Bounty.Minimum ships 0) is left as an owner decision.
- Finish workflow wf_eac019d7-85c launched: T18 sandbox acceptance (smoke.py live-server row skipped pending owner approval), polish lane (8 residual items incl. N1 bounty-set money loss), docket clerk.

## Rulings
- Ruling: schedule is a dependency DAG, not barrier waves — each task starts as soon as its Depends-on tasks are merged; merges are serialized into cnc-lose-them; T6+T7 merge as one group — user asked for maximum parallelism — a task can start on a head that later merges change under it (merge agent resolves).
- Ruling: per-merge verification = `mvn -q clean install -DskipTests` + `mvn test -pl <changed modules> -amd`; ONE full `mvn test` after the last merge (plan said full suite per wave) — keeps the serialized merge path short — a cross-module regression outside -amd is caught only at the end.
- Ruling: T18 (live acceptance run on the test server) runs after the final review, outside the implementation workflow — it needs the live server and the final build.
- Ruling: accept the planner's OWNER-CHECK ruling (notoriety is NOT cleared on death/arrest; today's behaviour kept) — the spec sentence "stays until death or arrest clears it, as today" is self-contradictory and "as today" points at no change; with Pay_Notoriety false notoriety pays nothing — if the owner meant death/arrest to clear it, one line in T13's code + a test.
- Ruling: lane reviewers are Opus for code tasks, Sonnet for docs/changelog; scoped re-reviews Sonnet; fix round 3 escalates to Opus — memory: Opus judges gates; no Fable.
