# WS6 G3 rebase report — onto 0.10.0 f5caaaca (WS5 G1-G3)

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws6`. Pre-rebase: `61dd3b41` (WS6 G3, reviewed/committed) on top
of `837966c3` (WS3 G4+G5). `git rebase 0.10.0` run with a clean working tree (nothing uncommitted).

## Rebased hash

**`897461057aa0da01733edc3dc211207436d56654`** — one commit, same message as `61dd3b41` (git rebase replayed it
verbatim, no reword needed). `git log -1 0.10.0` confirms `f5caaacad2ed5f3833cac86d700b40c8485c1f49` ("0.10.0 WS5:
/glw waypoint gangid keeps its gang-exists guard") as the new first parent; `git merge-base --is-ancestor 0.10.0
0.10.0-ws6` confirms `0.10.0` is now an ancestor of the rebased branch.

## Conflicts and resolutions

**None.** `git rebase 0.10.0` reported `Successfully rebased and updated refs/heads/0.10.0-ws6.` with zero
manual conflict resolution needed. Spot-checked every touch point the dispatch named, after the rebase:

| Expected touch point | Verified |
|---|---|
| `gangland-api`: my `LocalizedModuleYaml.java` vs WS5's new api files (`GangItemSourceContributions`, `GangMembership`, `PlaceholderContribution`, `UserDataInitEvent`) | Different paths, no conflict, as predicted — `LocalizedModuleYaml.java` present and unchanged; WS5's new files present alongside it |
| Civilians pom: WS5 dropped `gangland-domain` | Confirmed — the pom carries a comment noting "civilians is gang-module-free, gangland-domain (now empty) is no longer needed"; no `gangland-domain` dependency re-added by my commit (it never touched that pom) |
| `Messages.java`: WS5 did not touch it | Confirmed by the clean rebase itself — my 12-constant `CIVILIAN_*` deletion applied without a hunk conflict |
| `migration-0.10.0.md`: append order only | Confirmed — my "## WS6 G3" section is still the last section in the file (after WS1/WS4/WS2/WS3); WS5 did not add its own section to this doc, so there was nothing to interleave with |

Also re-ran the postcondition and the caller/bean wiring checks directly (not just trusting a conflict-free
rebase):

- `civilianMessages` bean still present in `CiviliansModuleConfig.java` (1 hit, unchanged).
- Whole-reactor grep for `Messages\.CIVILIAN_` → **zero live references**, only the 2 explanatory comments in
  `CivilianMessages.java`/`CivilianMessagesTest.java` (same as pre-rebase).

## Build

Full reactor (one build, never `install`): `mvn clean verify` → **BUILD SUCCESS**, 19/19 modules (up from 17 —
WS5 added `gangland-gang` as a new reactor module and `gangland-domain` is now built as an empty jar), ~1:20 min.

**Test count (Maven console rollup, per W52):**

| Module | Tests run | Δ vs. my pre-rebase G3 gate (896 total) |
|---|---|---|
| gangland-core | 67 | 0 |
| gangland-infra/gangland-item | 43 | 0 |
| gangland-ui/sign-api | 63 | 0 |
| **gangland-api** | **14** | **+14** (new — WS5's `GangItemSourceContributions` 3 + `GangMembership` 11; this module had 0 tests before WS5) |
| gangland-impl (shown as "Gangland") | 234 | **-28** (WS5 moved `GangAllyAbandonCommand`(4)/`GangAllianceRepository`(6)/`RankRepository`(4) etc. out to the new `gangland-gang` module, net of +3 new `WaypointGangIdCommand.gangUnknown` tests) |
| **gangland-gang** (new module) | **102** | **+102** (new — the gang/member/rank domain WS5 moved out of `gangland-domain` + `gangland-impl`, plus WS5's own new coverage) |
| gangland-features/gangland-mail | 25 | 0 |
| **gangland-features/gangland-civilians** | **23** | **0** — my G3 work (`CivilianMessagesTest`'s 3 tests) is intact and untouched by the rebase |
| gangland-features/gangland-turf | 91 | 0 |
| gangland-features/cops-n-crooks | 76 | 0 |
| gangland-features/gangland-gadget | 113 | 0 |
| gangland-features/gangland-npc-shops | 17 | 0 |
| gangland-features/gangland-lootchest | 53 | 0 |
| **gangland-infra/gangland-domain** | **0** | **-63** (WS5 emptied this module — no source left, "No tests to run") |
| **Total** | **921**, 0 Failures, 0 Errors, 0 Skipped | **+25** (net: +14 api -28 impl +102 gang -63 domain) |

**Matches the coordinator's expected `916 + 5 = 921` exactly.** All deltas above are WS5's landing (a new module,
a new empty module, and test relocation), not anything this rebase changed about G3's own work — `Messages` test
class still reports 12 (my +2 legacy-warning tests intact) and `gangland-civilians` still reports 23 (my +3
es-pick tests intact), unchanged from the pre-rebase gate.

## Postcondition

Zero `Messages.CIVILIAN_` references (live code) confirmed via whole-reactor grep, both immediately after the
rebase and again after the full build — matches the pre-rebase state exactly.

No smoke run, no reviewers invoked, one rebased commit (`897461057aa0da01733edc3dc211207436d56654`), worktree
left as-is (branch tip now the rebased commit; nothing further staged or committed by me).
