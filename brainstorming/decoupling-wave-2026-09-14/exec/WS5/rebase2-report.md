# WS5 rebase onto 0.10.0=837966c3 + guard-restoration commit

Worktree `E:\Programming\java\wt\gangland-0.10.0-ws5`, branch `0.10.0-ws5`.

## Rebased hashes

| | Before | After |
|---|---|---|
| WS5 G1-G3 + fix1 (single commit) | `d8d08e07` | `9e2c378e` (rebased onto `837966c3`, same message, "one rebased commit" per instruction) |
| New: `/glw waypoint gangid` guard restoration | — | `f5caaaca` (on top of `9e2c378e`) |

`git log --oneline -3 HEAD`:
```
f5caaaca 0.10.0 WS5: /glw waypoint gangid keeps its gang-exists guard
9e2c378e 0.10.0 WS5 G1-G3: gangs, members and ranks become the gangland-gang runtime module
837966c3 0.10.0 WS3 G4+G5: the loot-chest module owns its config, messages and commands; lootchest-api is gone
```

## Rebase conflicts and resolutions

`git rebase 0.10.0` produced exactly **one** file with real conflict markers; three other files the tool
flagged in its plan ("Auto-merging...") resolved themselves via git's own 3-way merge with no manual
intervention needed:

- **`gangland-impl/pom.xml`, `gangland-impl/src/main/resources/commands.json`, root `pom.xml`** —
  auto-merged cleanly, no markers, nothing to resolve.
- **`gangland-impl/src/test/java/.../command/data/InformationManagerTest.java`** — the one real conflict,
  exactly where predicted. HEAD (tip, `837966c3`) had `149` (`225 - ... - the 4 lootchest* keys` from WS3
  G4); my side had `121` (`153 - my 32 gang/rank keys`, computed from the pre-WS3-G4 base). Resolved to
  **`117`** (`149 - 32`, taking the number per instruction, not either hunk) and merged both narrative
  clauses (the lootchest-move sentence from HEAD's message + the gang-move sentence from mine) into one
  combined explanation. Verified against the actual post-merge `commands.json`:
  `python -c "import json; print(len(json.load(open('commands.json'))))"` → **117**, exact match.

**Module `<module>` adjacency** — checked directly rather than assumed: root `pom.xml` doesn't list feature
modules individually (it only aggregates via `<module>gangland-features</module>`); the actual adjacency
lives in `gangland-features/pom.xml`, which has both `<module>gangland-lootchest</module>` and
`<module>gangland-gang</module>` present and adjacent (lines 26-27) — auto-merged correctly, confirmed by
direct read, not just by the absence of conflict markers. `gangland-build/pom.xml` likewise carries both
modules at all 3 touch points (shade excludes, dependency:copy artifactItems, the plain `<dependency>` block)
— also auto-merged clean, confirmed by direct grep.

**`module.yml` Depends edits** — no overlap, as predicted (turf's `module.yml` gained `Depends: [gang]`
this WS5 stream; WS3 G4/G5 never touched turf's `module.yml`).

**Settings/Messages/message\*.yml/settings.yml** — untouched by WS5, so WS3's hunks applied with zero
conflict, confirmed by their absence from the conflict list.

## Second commit: `/glw waypoint gangid` gang-exists guard

The original (pre-WS5) command had two checks: `user.getGangId() != id` (the primary validation — a player
can only ever try to set their own gang's id) **and** `gangManager.getGang(id) == null -> GANG_DOESNT_EXIST`
(a defensive re-check). The first WS5 pass dropped the second check entirely, reasoning it was redundant
given the first — true in the common case, but it misses a real (if rare) desync window: a gang can be
deleted after a player's cached `User.gangId` was set but before it gets reset, in which case `id` still
equals `user.getGangId()` yet no longer names a real gang.

Restored via:
- **`GangMembership.isInstalled()`** (new, `gangland-api`) — `true` once the module's view is installed,
  `false` before. Needed because `nameOf(id)` alone can't distinguish "the module answered no" from "there's
  no module to ask" — both return an absent result.
- **`WaypointGangIdCommand.gangUnknown(GangMembership, int)`** (new, package-private static decision helper,
  `gangland-impl`) — `gangMembership.isInstalled() && gangMembership.nameOf(id).isEmpty()`. Only rejects when
  the module is actually installed and confirms the id is unknown; an uninstalled module falls back to
  trusting the raw-id path, matching this command's behaviour everywhere else when the module is absent.
- Wired into the main action, right after the primary `user.getGangId() != id` check.

**Red-first test**: `WaypointGangIdCommandTest` (new file, `gangland-impl`, 3 tests) on the decision helper
directly. Genuinely verified red: temporarily hard-coded `gangUnknown` to always return `false`, ran the
suite, confirmed exactly the "installed + unknown id -> true" case failed (`expected: <true> but was:
<false>`), then reverted. Also added `GangMembershipTest.isInstalled_reflectsWhetherAViewExists` (new test,
`gangland-api`, 1 test) covering the new holder method directly.

Commit `f5caaaca`, message `"0.10.0 WS5: /glw waypoint gangid keeps its gang-exists guard"` +
`Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` / `Claude-Session:
https://claude.ai/code/session_01PBKRAg3Pnh5B7kBbDTcGMK`, exactly as specified.

## Gate result

```
mvn clean verify   (rebase commit alone, before the second commit) → BUILD SUCCESS, all 20 modules
mvn clean verify   (both commits, final)                            → BUILD SUCCESS, all 20 modules
mvn -pl gangland-build -am package (ran as part of both verify runs) → target/modules/gangland-gang-0.10.0.jar
                                                                        confirmed, module.yml + commands.json
                                                                        at its root, both runs
```

Never installed at any point (`mvn clean verify` only, per instruction).

## Test count diff against `837966c3`'s own baseline

Source of the baseline table: `exec/G010/merge-2026-09-22b-report.md` (WS3 merge report, `891` total, 12
modules with tests, Maven console rollup). Compared against this round's final `mvn clean verify` console
rollup (also W52-compliant: per-module `Tests run:` line, not surefire `.txt` summation).

| Module | `837966c3` baseline | This round | Δ | Explanation |
|---|---|---|---|---|
| Gangland Core | 67 | 67 | 0 | untouched |
| Gangland Item | 43 | 43 | 0 | untouched |
| Sign API | 63 | 63 | 0 | untouched |
| Gangland Domain | 63 | **0** | **−63** | every test moved into `gangland-gang` — the module is now fully empty (`compiler:testCompile` logs "No sources to compile"); the 9 test classes/support files (`GangAllianceTest`, `GangManagerAllianceTest`, `GangMembershipTest`\*, `MemberCachePopulationOrderTest`, `MemberManagerTest`, `MemberTest`, `GangPermissionsTest`, `RankAssignmentPolicyTest`, `RankManagerTest`, `RankTest` + 2 support classes) are all named in the earlier `G1-G3-report.md` move map |
| Gangland (impl) | 260 | 232 | **−28** | net of two opposite moves: **−31** (5 test classes moved to `gangland-gang` — `GangAllyAbandonCommandTest` 4, `GangFilterAdapterTest` 9, `GangAllianceRepositorySpiTest` 6, `RankRepositorySpiTest` 4, `MemberFilterAdapterTest` 8) **+3** new this round (`WaypointGangIdCommandTest`) |
| **Gangland API** | *(no row — 0 tests)* | **14** | **+14** | brand new this WS5 stream: `GangMembershipTest` 11 (8 original + 2 `nameOf` + 1 `isInstalled`, this round), `GangItemSourceContributionsTest` 3 |
| **Gangland Gangs** | *(module didn't exist)* | **102** | **+102** | new module. 94 of its 102 tests are the ones moved from Domain (63) and impl (31) above; the other 8 are genuinely new to this stream: `RankPermissionApplierTest` 4, `RankManagerTest.SeedInitialRanksTest` 2, `GangModuleTest` 2 |
| Gangland Mail | 25 | 25 | 0 | untouched |
| Gangland Civilians | 20 | 20 | 0 | untouched |
| Gangland Turf | 91 | 91 | 0 | untouched |
| Cops N Crooks | 76 | 76 | 0 | untouched |
| Gangland Gadgets | 113 | 113 | 0 | untouched |
| Gangland NPC Shops | 17 | 17 | 0 | untouched |
| Gangland Loot Chests | 53 | 53 | 0 | untouched |
| **Total** | **891** | **916** | **+25** | −63 (Domain) − 28 (impl) + 14 (API) + 102 (Gangs) = **+25**, verified arithmetically and by direct count — no residual |

The reactor now has 19 modules with a `Tests run:` line (up from 12 — `gangland-api` and `gangland-gang` are
new to the list; `gangland-domain` dropped off it, now 0). Every one of the +25 net new tests is named above
against the specific class it came from; nothing is unaccounted for.

## Postcondition greps

```
grep -rl "^import org\.luckyraven\.gangland\.gang\." --include="*.java" gangland-impl
  → (empty) — gangland-impl never imports the gang module's own gang.* package.

grep -rl "^import org\.luckyraven\.gangland\.gang\." --include="*.java" \
  gangland-features/gangland-civilians gangland-features/cops-n-crooks gangland-features/gangland-gadget
  → (empty) — the S1 "gang-module-free" trio stays gang-module-free; every one of their gang-fact reads
    goes through gangland-api's GangMembership holder instead.

grep -n "gangland-gang" gangland-impl/pom.xml
  → (empty) — impl has no gangland-gang dependency, compile or otherwise.
```

`gangland-mail` and `gangland-turf` do import `org.luckyraven.gangland.gang.*` directly (47 files combined) —
expected and correct, not a violation: both modules are explicitly permitted to depend on `gangland-gang`
per the original W51 ruling (they declare it in their pom at `provided` scope and `Depends: [gang]` in their
`module.yml`), unlike the S1 trio.

## Deliverables

- This file: `exec/WS5/rebase2-report.md` (main checkout).
- No package diff produced this round — the coordinator committed the prior round's work directly
  (`d8d08e07` → `9e2c378e` after rebase, `f5caaaca` new), so there is nothing uncommitted to diff; `git
  status` is clean at HEAD.
