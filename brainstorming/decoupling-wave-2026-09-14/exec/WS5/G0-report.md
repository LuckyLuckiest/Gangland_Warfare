# WS5 G0 report — 2026-09-22

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws5` (branch `0.10.0-ws5`, base HEAD `85299070`)
Scope: plan `plans/WS5-gang.md` §4 **G0 only** — steps **1, 1b, 1c, 2, 3, 4** exactly as written (§0c ruling R7
noted and respected: this gate never touches `GangLookupContract`/`RankLookupContract`, which stay untouched in
`gang.contract` for now — they move with the module in a later gate, not G0).

---

## Spec table — step by step

| Step | What the plan says | What I did | Deviation |
|---|---|---|---|
| **1** | Move `gang/user/{User,UserManager,Level,UserFactory}.java` → `gangland-core/.../user/`. Update every importer's import line. | `git mv` all 4 files to `gangland-core/src/main/java/org/luckyraven/gangland/core/user/`; package line rewritten; every importer's import line rewritten via a repo-wide sed pass (208 files matched the moving-package patterns). | None. |
| **1b** | Split `GangSettingsContract` → `IdentitySettingsContract` (11 getters) + `GangSettingsContract` (3 getters, stays named). Split the `GangSettings` facade the same way. Rewrite `User.java:63-67`'s constructor calls. Delete `User.flushPermissions(Rank)` and `UserManager.initializeUserPermission` **without moving their bodies**; add the narrow `User` primitives (§3 row 8) as stubs. | Split done exactly as specified (see "Contract/facade split" below). `User`'s constructor now calls `IdentitySettings.getBountyEachKillValue()`/`getBountyTimerMultiple()`/`getWantedLevelIncrement()`/`getWantedMaximumLevel()`. `flushPermissions`/`initializeUserPermission` deleted from `User`/`UserManager`. Added `User.grantedPermissionNames()`, `User.setPermission(String,boolean)`, `User.updateCommands()` (real, not throwing, implementations — see "Temporary inline bridges" below for why they're consumed immediately rather than sitting idle). | **`IdentitySettingsContract`'s module placement**: the plan says "stays in gangland-api" (§3 row 5b) but `gangland-api` depends on `gangland-core` (not the reverse) — `User`/`Level`/`IdentitySettings` all live in `gangland-core` and cannot reach an interface in `gangland-api` without a dependency cycle. Confirmed by a real compile failure (`package org.luckyraven.gangland.data.user does not exist` when `IdentitySettingsContract` sat in api). Placed it in **`gangland-core`**, package `org.luckyraven.gangland.core.user`, mirroring exactly where the original `GangSettingsContract` sat relative to `GangSettings`/`Gang` (same module, contract next to its facade and its data classes). Flagged as a plan correction, not a silent choice — see "Concerns / open questions" below. **6 additional call sites** needed a bridge because the plan's "delete without moving" instruction, taken literally, breaks the build: `Gang.java:137`, `GangDemoteCommand.java:147`, `GangPromoteCommand.java:179`, `GangTransferCommand.java:179,188`, `PlayerBootstrapService.java:122`, `CreateAccountListener.java:115` all called the deleted methods. Each got a `ponytail:`-tagged temporary inline replacement built from the 3 new narrow primitives (identical net behaviour, see "Temporary inline bridges" below) — this is Risk #2's own named fallback shape, just inlined at each call site instead of kept as a `User`/`UserManager` method, since a `Rank`-typed method could not stay on `User` per B2. |
| **1c** | Delete the `MemberManager orderingDep` parameter from `DataConfig.java:77-82,90-98`. | Deleted from both `userManager()` and `offlineUserManager()` bean methods. | None — see the required boot proof below (§3 row 6b), covered by a new test rather than a live boot. |
| **2** | Move `gang/bounty/*` (4) → `.../bounty/`, `gang/wanted/*` (6) → `.../wanted/`, `gang/events/{bounty,level,user,wanted}/*` (7) → `.../events/{bounty,level,user,wanted}/`. | All 17 files moved via `git mv`, packages rewritten, importers fixed. | None. |
| **3** | Move `gang/rank/Permission.java` → `gangland-core/.../permission/Permission.java`; repackage `PermissionTable`/`PermissionRepository` imports only. | `Permission.java` moved. `PermissionTable.java`/`PermissionRepository.java` (gangland-impl, unmoved) already had explicit imports — fixed by the same sed pass. | **Two files not named by the plan needed a new import** that didn't exist before: `Rank.java` and `RankManager.java` (both stay in `gang.rank`) referenced `Permission` by **implicit same-package access** (no import line existed to rewrite), so the move silently broke their compile until I added `import org.luckyraven.gangland.core.permission.Permission;` to both. Also: `Permission.setID(int)` was `protected` (reachable from `RankManager` only because it was same-package); widened to `public` since `RankManager` is now cross-module from `Permission` — its only external caller, doing a rank-persistence resync on boot. |
| **4** | `gangland-api/pom.xml`: replace the `gangland-domain` re-export with a `gangland-core` compile dep. | Deleted the `gangland-domain` `<dependency>` block; `gangland-core` dep was already present so nothing to add there. Confirmed the 4 named api files (`WaypointTeleport.java`, `TeleportEvent.java`, `UserLevelUpEvent.java`, `MoneyAspect.java`) only ever needed the moved identity types (fixed by the sed pass). | **Two modules broke on the removed transitive re-export**: `gangland-mail` and `gangland-gadget` never declared `gangland-domain` directly — they got it only through `gangland-api`'s (now-removed) compile-scope re-export, and both still import real `gang.*` types (`Gang`, `GangManager`, etc. — unmoved, still module-owned in a later gate). Added a direct `gangland-domain` dependency to both poms (matching each module's existing scope convention: unscoped/compile for mail, `provided` for gadget, mirroring their `gangland-core` dep). This is a direct, unavoidable consequence of step 4 as written, not a scope expansion. |

---

## Move map (old → new)

### Production code (22 files)

| Old (gangland-infra/gangland-domain) | New (gangland-core) |
|---|---|
| `gang/user/User.java` | `core/user/User.java` |
| `gang/user/UserManager.java` | `core/user/UserManager.java` |
| `gang/user/Level.java` | `core/user/Level.java` |
| `gang/user/UserFactory.java` | `core/user/UserFactory.java` |
| `gang/bounty/{Bounty,BountyContext,BountyExecutor,BountySettings}.java` | `core/bounty/*` |
| `gang/wanted/{Wanted,WantedContext,WantedExecutor,WantedKillTracker,WantedKillTrackers,WantedSettings}.java` | `core/wanted/*` |
| `gang/events/bounty/BountyEvent.java` | `core/events/bounty/BountyEvent.java` |
| `gang/events/level/LevelUpEvent.java` | `core/events/level/LevelUpEvent.java` |
| `gang/events/user/UserBountyEvent.java` | `core/events/user/UserBountyEvent.java` |
| `gang/events/wanted/{WantedEndEvent,WantedEvent,WantedLevelChangeEvent,WantedStartEvent}.java` | `core/events/wanted/*` |
| `gang/rank/Permission.java` | `core/permission/Permission.java` |

### Test code (7 files, per plan §7's identity-slice assignment)

| Old | New |
|---|---|
| `gang/user/UserTest.java` | `core/user/UserTest.java` (3 obsolete `flushPermissions` tests deleted, 6 new narrow-primitive tests added) |
| `gang/user/UserManagerTest.java` | `core/user/UserManagerTest.java` |
| `gang/user/LevelTest.java` | `core/user/LevelTest.java` |
| `gang/bounty/BountyTest.java` | `core/bounty/BountyTest.java` |
| `gang/wanted/WantedTest.java` | `core/wanted/WantedTest.java` |
| `gang/wanted/WantedKillTrackersTest.java` | `core/wanted/WantedKillTrackersTest.java` |
| `gang/support/TestLevelUpEvent.java` | `core/support/TestLevelUpEvent.java` |

### New files (6)

- `gangland-core/.../core/user/IdentitySettingsContract.java` — 11-getter contract (see deviation note on module placement above).
- `gangland-core/.../core/user/IdentitySettings.java` — static facade, `GangSettings`'s exact shape.
- `gangland-impl/.../file/configuration/identity/GanglandIdentitySettings.java` — impl binder, delegates to `Settings`.
- `gangland-core/src/test/.../core/support/FakeIdentitySettingsContract.java` — test fixture for core.
- `gangland-infra/gangland-domain/src/test/.../gang/support/FakeIdentitySettingsContract.java` — duplicate fixture for domain tests that construct `Gang` (its constructor now needs `IdentitySettings` bound too — see below); duplicated rather than wired as a cross-module test-jar dependency since domain doesn't otherwise consume core's test-jar and the fixture is ~15 lines.
- `gangland-infra/gangland-domain/src/test/.../gang/member/MemberCachePopulationOrderTest.java` — the step-1c ordering proof (see below).

### Contract/facade split (step 1b)

`GangSettingsContract` (14 getters) → `IdentitySettingsContract` (11: `isAutoSave`, `getUserMaxLevel`,
`getUserLevelBaseAmount`, `getUserLevelFormula`, `getBountyEachKillValue`, `getBountyTimerMultiple`,
`getBountyTimerMax`, `isBountyTimerEnabled`, `getWantedLevelIncrement`, `getWantedMaximumLevel`,
`isWantedTimerEnabled` — `gangland-core`) + `GangSettingsContract` (3: `getGangDisplayNameChar`, `getGangRankHead`,
`getGangRankTail` — stays in `gang.contract`, unmoved, moves to the module in G1). Same split for the static
facades (`IdentitySettings` / `GangSettings`) and the impl-side binders (`GanglandIdentitySettings` /
`GanglandGangSettings`, both now implement their trimmed contract only). `GangModuleConfig` gained a second bean
method, `identitySettingsContract()`, alongside the existing `gangSettingsContract()` — both still bound from the
same `@Configuration` class since `IdentityContractConfig` (G1 step 9) doesn't exist yet.

**Blast radius the plan didn't name**: `Gang.java`'s own constructor (`new Gang(int id)`) also builds a `Bounty` via
`GangSettings.getBountyEachKillValue()`/`getBountyTimerMultiple()` (line 67 — a *gang*-level bounty, separate from
the per-user one) **and** constructs a `new Level()` (whose no-arg constructor itself reads
`GangSettings.getUserMaxLevel()`/`getUserLevelBaseAmount()`). Both rewired to `IdentitySettings.*`. Every domain
test that constructs a `Gang` (`GangAllianceTest`, `GangManagerAllianceTest`, `GangMembershipTest`) and every impl
test that does the same (`GangAllianceRepositorySpiTest`, `GangFilterAdapterTest`) needed
`IdentitySettings.bind(...)` added alongside their existing `GangSettings.bind(...)` call — none of this was named
in §1b/§3 row 5b, but it's an unavoidable consequence of the split reaching into `Gang`'s constructor. Also:
`MemberManager.java:62` (unmoved, stays `gang.member`) called `GangSettings.isAutoSave()` — rewired to
`IdentitySettings.isAutoSave()`.

### Temporary inline bridges (the 6 call sites, §1b)

`User.flushPermissions(Rank)` cleared every currently-granted node then (if a rank was given) granted that rank's
nodes, then refreshed the client command list. `UserManager.initializeUserPermission(User,Member)` attached a
fresh `PermissionAttachment`, granted the member's rank nodes, refreshed commands. Both are gone from
`User`/`UserManager`; each of the 6 call sites now does the same sequence inline through the 3 new narrow
primitives (`grantedPermissionNames()`, `setPermission(String,boolean)`, `updateCommands()`), tagged
`// ponytail: temporary inline bridge ... G2's RankPermissionApplier (gang module) replaces this once it exists.`
**One documented behaviour nuance**: the original used `PermissionAttachment.unsetPermission(name)` (removes the
override entirely, inherits from parent) to clear; the inline bridges use `setPermission(name, false)` (explicit
deny) instead, matching the narrow primitive's literal signature from §3 row 8. For custom `gangland.*` permission
strings with no default/parent value (the only kind a rank ever grants), `hasPermission` resolves to `false`
either way, so this is functionally equivalent in every real scenario, but it is a real, if inert, semantic
difference worth G2's `RankPermissionApplier` re-confirming rather than silently inheriting. Not a docket item
(no bug), flagged here per the plan's own precedent (step 16d's "named behaviour change" treatment).

---

## Importer counts per module

**245 unique files touched** in total: 210 modified in place (import-line fixes to files that don't move) + 29
moved (`git mv`, old path → new path, each counted once) + 6 brand-new files. Verified with `git add -N .` (so
git reports each path individually instead of collapsing a brand-new directory into one line) followed by
`git reset`:

| Module | Modified (import-line only) | Moved (22 main + 7 test → `gangland-core`) | New |
|---|---|---|---|
| `gangland-api` | 5 (4 source files + pom.xml) | — | — |
| `gangland-core` | 1 (pom.xml) | 29 (destination) | 3 |
| `gangland-impl` | 135 | — | 1 |
| `gangland-infra` (domain) | 17 | 29 (source, moved *out*) | 2 |
| `gangland-features/cops-n-crooks` | 10 | — | — |
| `gangland-features/gangland-civilians` | 2 | — | — |
| `gangland-features/gangland-gadget` | 9 (7 source + pom.xml + 1 test) | — | — |
| `gangland-features/gangland-mail` | 13 (12 source + pom.xml) | — | — |
| `gangland-features/gangland-npc-shops` | 6 | — | — |
| `gangland-features/gangland-turf` | 12 | — | — |
| **Total** | **210** | **29** | **6** |

The plan's own estimate (177 files, C2) undercounted — actual full-repo grep for the moving package patterns hit
**208 importer files** (before counting the 30 moved files' own internal cross-references, the 6 permission
call-site bridges, or the 2 pom additions the removed `gangland-domain` re-export forced); the extra ~31 over
the plan's count come from `gang.wanted.*`/`gang.events.*` importers the review's grep evidently missed.

---

## Tests — red-first evidence

**Genuinely new coverage** (not a docket bug flip — nothing wrong was being fixed, so "red" means "didn't exist /
wouldn't compile" against the pre-change tree, not "asserted today's wrong behaviour"):

1. **`UserTest`'s 6 new tests** for `grantedPermissionNames()`/`setPermission(String,boolean)`/`updateCommands()`
   — these methods don't exist on pre-change `User`, so the tests are red-by-non-existence against the pre-change
   class. Green after step 1b's edit: `mvn test -pl gangland-core -am -Dtest=UserTest` → `Tests run: 15,
   Failures: 0, Errors: 0, Skipped: 0` (15 = the file's full count after deleting 3 obsolete `flushPermissions`
   tests and adding 6 new ones; pre-change file had 12).
2. **`MemberCachePopulationOrderTest`** (2 tests) — pins the step-1c proof (see below). Tests *existing,
   unchanged* `MemberManager` behaviour (I did not modify `MemberManager`'s `initialize()`/`onInitialize()`), so
   this is a regression pin, not a bug-fix flip; it was never red against any version of the code, it simply
   didn't exist before. Green: `mvn test -pl gangland-infra/gangland-domain -am
   -Dtest=MemberCachePopulationOrderTest` → `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

**Deleted** (obsolete, method no longer exists): `UserTest.flushPermissions_offlinePlayer_isNoOpAndDoesNotNpe`,
`.flushPermissions_onlinePlayer_clearsThenReapplies`, `.flushPermissions_nullRank_clearsOnly` — 3 tests.

**Net test delta this gate**: −3 (deleted) + 6 (User primitives) + 2 (ordering proof) = **+5** vs. the pre-change
tree's count for these files. No baseline `mvn test` was captured before starting (the worktree's HEAD had no
prior gate on it), so I can't give an exact whole-reactor "before" number; the delta above is file-level and
exact.

**All other production classes moved with zero behaviour change** — their existing tests moved with them
unmodified in assertion content (only import/package lines changed), and all pass green in the final run below.

---

## Step 1c's required proof (§3 row 6b)

The plan requires: *"members still attach gangId correctly across first load, `/glw reload`, and a fresh empty
DB"* before trusting the `orderingDep` parameter deletion. Per my brief, this cannot be a live smoke here (test
server owned by the 0.10.0 lead) — covered by a test instead, since the reactor's test seams do allow it:

- **Confirmed by direct code reading** (not asserted): `MemberManager implements BeanLifecycle`, and
  `onInitialize(boolean)` → `initialize()` populates the member cache from `memberRepository.loadAll()` — this
  answers §13's "not verified: whether `MemberManager` implements `BeanLifecycle`" outright: **yes**.
- **Confirmed by direct code reading**: the two real consumers of member data at boot,
  `UserDataLoader.loadUserData` (line 107: `memberManager.getMember(user.getUuid())`) and
  `PlayerBootstrapService.loadOnlinePlayers` (line 119: same call), both take `MemberManager` as a **direct**
  constructor parameter — neither reaches it through `UserManager`. `PlayerBootstrapService` is a
  `BeanPostInitialize` bean whose own javadoc states it runs *"after every `BeanLifecycle.onInitialize(...)` call
  has completed"* — a Keystone-level phase guarantee, not a `BeanGraph` construction-order edge between
  `UserManager` and `MemberManager` specifically.
- **`MemberCachePopulationOrderTest`** (new, gangland-domain) pins exactly this: `getMember()` returns nothing
  before `onInitialize()` runs, and returns the loaded member after — with **zero** `UserManager` reference
  anywhere in the test, demonstrating the cache-readiness invariant has no dependency on `UserManager`'s
  construction or existence at all.

**What this does *not* cover, deferred explicitly to the merge smoke** (per my brief's allowance): an actual
server boot exercising the real `BeanFactory`/`BeanGraph` end to end — first load, `/glw reload`, and a fresh
empty DB, with a live client actually online and `gangId` visible via `/glw gang` or a placeholder. The structural
proof above is strong (both real consumers already bypass `UserManager` entirely) but is not a substitute for
watching a real boot. **Named explicitly as owed to the 0.10.0 lead's merge smoke**, not silently assumed green.

---

## Build

Full reactor, one build at a time, no `mvn install` used anywhere (per constraint):

```
mvn clean compile -q -DskipTests     → clean (after 2 fix rounds: IdentitySettingsContract's module
                                        placement, then Rank.java/RankManager.java's implicit Permission import)
mvn clean test-compile -q            → clean (after adding the same missing import to 2 test files)
mvn clean verify                     → BUILD SUCCESS (after binding IdentitySettings in 2 more impl tests
                                        that construct Gang)
```

Final `mvn clean verify`: **BUILD SUCCESS**. Test count = sum of every module's
`target/surefire-reports/*.txt` "Tests run" lines:

| Module | Tests run |
|---|---|
| `gangland-core` | 56 |
| `gangland-features` (6 submodules combined) | 324 |
| `gangland-impl` | 258 |
| `gangland-infra` (gangland-domain) | 93 |
| `gangland-ui` (3 remaining submodules) | 92 |
| **Total** | **823** |

**Failures: 0, Errors: 0, Skipped: 0** across all 823.

---

## Docket ids touched

No bug was found or fixed this gate — this is a mechanical repackaging gate per an already-reviewed plan, not a
bug-triage session. Docket ids whose covering tests were **mechanically touched** (import-line fix or an added
`IdentitySettings.bind(...)` call, assertions unchanged, all still green):

| Id | File | What changed |
|---|---|---|
| GR-03 (already fixed, 0.8.3 wave 3) | `RankAssignmentPolicyTest.java` | **Not touched** — no reference to any moved package. |
| GR-08 (already fixed, 0.8.3 wave 3) | `GangPermissionsTest.java` | Import-line fix only (`gang.rank.Permission` → `core.permission.Permission`). Pinned assertions unchanged, still green. |
| GR-12, GR-13, GR-35 (test-pinned) | `RankManagerTest.java` / `RankTest.java` (per plan §7: "`RankManagerTest`/`RankTest` carry the GR-12/13/35 pins") | Both gained the same `Permission` import (previously implicit same-package access, broken by the move). No assertion touched; still green. |

**Docket candidates** (new, for the orchestrator to file): none rise to "bug" — the `unsetPermission` →
`setPermission(false)` nuance in the 6 temporary inline bridges (documented above) is a deliberate, flagged,
almost-certainly-inert simplification tied to a `ponytail:` comment and this report, not a filed defect. If G2's
`RankPermissionApplier` author wants it filed as a tracked follow-up rather than left as inline documentation,
that's their call.

---

## Subagents used

None. Every file in scope required either a mechanical but semantically-loaded rewrite (package prefix swap
across 208 files via a single repo-wide `sed` pass — cheaper and more reliable than orchestrating haiku agents
over hundreds of individually-specified files) or actual judgement (the contract split, the 6 permission
call-site bridges, the module-placement fix for `IdentitySettingsContract`) that didn't parallelize into
independent, fully-specified chunks worth a subagent hand-off.

---

## Concerns / open questions

1. **`IdentitySettingsContract`'s module** — moved to `gangland-core` instead of `gangland-api` as the plan
   literally says (§3 row 5b). This is architecturally forced (api depends on core, not the reverse); flag for
   the orchestrator to correct the plan text before G1's executor reads it, since G1 references this contract
   again (`IdentityContractConfig`, step 9).
2. **`UserManager`'s `gangland` field is now unused** (only `initializeUserPermission` read it). Left in place —
   removing it would mean also dropping/rewriting the constructor parameter, rippling into every `new
   UserManager<>(gangland, ...)` call site (`DataConfig`, both test classes) for a cosmetic gain outside this
   gate's scope. Noted, not fixed.
3. **Step 1c's live-boot proof** is structurally argued and unit-pinned (above) but not smoke-tested against a
   real server — explicitly owed to the 0.10.0 lead's merge smoke, not silently assumed.
4. **The 6 permission call-site bridges are temporary and duplicated** (not DRY) by design — each is 4-8 lines,
   tagged `ponytail:`, and all six get replaced wholesale by G2's `RankPermissionApplier` (step 11b). Building a
   shared helper now would have meant creating exactly the abstraction G2 owns, one gate early, in the wrong
   module (no gang module exists yet in G0).

## Files touched

Full list: `git status --short` in the worktree (245 paths) or `G0-package.diff` (this directory) for the
complete unified diff. Report path: `brainstorming/decoupling-wave-2026-09-14/exec/WS5/G0-report.md`. Diff path:
`brainstorming/decoupling-wave-2026-09-14/exec/WS5/G0-package.diff`.
