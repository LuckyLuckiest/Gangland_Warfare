# merge master into 0.10.0 — report

**Worktree:** `E:\Programming\java\wt\gangland-0.10.0` (branch `0.10.0`)
**Pre-merge HEAD:** `198d101d` (WS5: UserManager qualifiers)
**Merge commit:** `47bca22a73e0d94e79c1789097b10638c86cc85c`
**Commit message:** `0.10.0: merge master (wiki pages; the wb-02 bank/bounty guards were already superseded)`

master (`fd06d15b`) is now confirmed reachable from 0.10.0's new HEAD
(`git merge-base --is-ancestor master HEAD` → true), so `master` can be
fast-forwarded to `47bca22a`.

## Commits brought in

7 commits on master that 0.10.0 lacked, exactly as scoped:

| commit | description |
|---|---|
| `463a787e` | Add starter wiki overview |
| `f50e4bd4` | Add workflow audit wiki pages (September 2026) |
| `5150f791` | Drop the diagrams-are-source note from the audit wiki pages |
| `e1727b0f` | Fix six P0 bugs: unique-item identity and money-amount guards (0.8.3) — branch `wb-02-bounty-guard` |
| `85c2ae7b` | Merge the 0.8.x development line into master (0.8.0 -> 0.8.3) |
| `1c120ed8` | Merge wb-02-bounty-guard into master (already superseded on 0.8.3) |
| `fd06d15b` | Merge worktree-p0-wave-3 into master: the six P0 permission holes |

## Conflicts

**None.** `git merge master --no-commit --no-ff` completed cleanly
("Automatic merge went well"). The staged diff was purely additive:

```
15 files changed, 12299 insertions(+)
```

— all 15 new files under `docs/wiki/*.md` (the three wiki-page commits).
None of the five bank/bounty files were touched by the merge at all, which
is the expected outcome given the five-file comparison below.

## Five-file comparison (e1727b0f vs 0.10.0 pre-merge HEAD `198d101d`)

Per the resolution rule, confirmed the wb-02 guards were already superseded
before trusting the "already superseded" framing, by diffing e1727b0f's
version of each file against 0.10.0's:

| file | diff vs e1727b0f | guard present on 0.10.0? |
|---|---|---|
| `gangland-impl/.../command/util/ParsedAmount.java` | **byte-identical** (0 diff) | Yes — untouched since e1727b0f |
| `gangland-impl/.../command/sub/bank/BankCommand.java` | 26+/21- | Yes — `BYPASS_CAP_PERMISSION` constant moved from here to `BankTiers.BYPASS_CAP_PERMISSION` (per the module-API wave's "not in the api" list); permission check itself unchanged |
| `gangland-impl/.../command/sub/bank/BankDepositCommand.java` | 15+/19- | Yes — `ParsedAmount.of(args[2])` call sites intact (lines 91, 187); diff is `Gangland`→`JavaPlugin`, `gang.user`→`core.user` package moves, `BankTierRegistry`→`BankTiers`, `@Qualifier("online")` added by WS5/T-55 |
| `gangland-impl/.../command/sub/bank/BankWithdrawCommand.java` | 8+/6- | Yes — `ParsedAmount.of(args[2])` intact (lines 84, 160); same package-rename/qualifier churn as above |
| `gangland-impl/.../command/sub/bounty/BountySetCommand.java` | 11+/9- | Yes — `ParsedAmount.of(amountStr)` intact (line 80); `gang.bounty`/`gang.events.*`→`core.bounty`/`core.events.*` package moves, `@Qualifier("online")` added |

Conclusion: every diff in the four non-identical files is refactor churn
from later waves (the `Gangland`→`JavaPlugin`/`gang.*`→`core.*` decoupling
renames, `BankTierRegistry`→`BankTiers`, and WS5's explicit
`@Qualifier("online")` on every `UserManager<Player>` parameter). The actual
guard call sites (`ParsedAmount.of(...)`, the sign check inside
`ParsedAmount`, the bank-cap/bypass-permission check, and the
`Bounty.Minimum` floor) are present and unchanged in content. No guard was
ported — nothing was missing. Also cross-checked that `ParsedAmountTest.java`
(`gangland-impl/src/test/java/org/luckyraven/gangland/command/util/`) and
`BankAmountGuardIntegrationTest.java`
(`gangland-impl/src/test/java/org/luckyraven/gangland/database/repositories/player/`)
both exist on 0.10.0 pre-merge and pass in the post-merge build (below),
covering the guard behavior directly.

Additionally verified via `git merge-base --is-ancestor`: the two commits
that actually carried the guards into master history, `c94fceab` (five bugs)
and `61d45ddc` (WB-02 + `Bounty.Minimum` floor), are both ancestors of
0.10.0's pre-merge HEAD; `e1727b0f` itself is not an ancestor (it lives only
on the superseded `wb-02-bounty-guard` branch/merge commit), which matches
merge commit `1c120ed8`'s own claim that "every file the branch touches is
byte-identical to master's post-merge tree."

Since the merge was conflict-free, the resolution rule ("keep 0.10.0's
version") required no action — 0.10.0's versions of all five files pass
through the merge unmodified.

## Build gate

**`mvn clean verify` (full reactor):** `BUILD SUCCESS`, ~1:11 elapsed.

Console rollup (W52 convention — sum the per-module `Tests run:` lines,
not `target/surefire-reports/*.txt`, because Surefire's plain-text reporter
undercounts `@Nested` classes):

```
67 + 43 + 63 + 14 + 238 + 106 + 25 + 23 + 91 + 76 + 113 + 17 + 53 = 929
```

**929 tests run, 0 failures, 0 errors, 0 skipped** — matches the recorded
baseline (929, from `T-55` fix landing on 198d101d, per
`.superpowers/sdd/PLAN/progress.md` line 200) exactly, confirming the merge
introduced no test regressions and no new tests (the wiki-only + already-
superseded merge changed no `.java` files).

**`mvn -pl gangland-build -am package -DskipTests`:** `BUILD SUCCESS`.
8 module jars produced in `target/modules/`:

```
cops-n-crooks-0.10.0.jar
gangland-civilians-0.10.0.jar
gangland-gadget-0.10.0.jar
gangland-gang-0.10.0.jar
gangland-lootchest-0.10.0.jar
gangland-mail-0.10.0.jar
gangland-npc-shops-0.10.0.jar
gangland-turf-0.10.0.jar
```

plus the core jar `target/gangland_warfare-0.10.0.jar`.

## Outcome

Worktree `E:\Programming\java\wt\gangland-0.10.0` is on branch `0.10.0`,
clean working tree, HEAD `47bca22a`. `master` can now be fast-forwarded to
this commit. Nothing pushed (per task scope); main checkout
`E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]` was not touched.
