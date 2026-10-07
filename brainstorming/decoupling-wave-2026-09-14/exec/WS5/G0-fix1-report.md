# WS5 G0 fix round 1 report — 2026-09-22

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws5` (branch `0.10.0-ws5`, still uncommitted on top of `85299070`)
Responds to: coordinator's WS5 G0 review verdict **FIX (2 Important, 1 Minor)**.

---

## F1 (Important) — `unsetPermission` primitive, all 5 clear loops fixed

**Root cause confirmed**: the deleted `User.flushPermissions(Rank)` cleared existing nodes with
`PermissionAttachment.unsetPermission(name)` (removes the override, falls back to whatever a group/Vault/
LuckPerms grant resolves to). The temporary inline bridges I wrote in the original G0 pass used
`setPermission(name, false)` instead — an explicit `false` override that *beats* a group grant, which is exactly
backwards for a codebase that ships `VaultPermissionBridge` for this case.

**Fix**: added `User.unsetPermission(String)` (`gangland-core/src/main/java/org/luckyraven/gangland/core/user/
User.java`, beside `grantedPermissionNames()`/`setPermission(String,boolean)`/`updateCommands()`) — a direct,
narrow, rank-free mirror of the exact call `flushPermissions` used to make. Switched all 5 clear loops from
`setPermission(node, false)` to `unsetPermission(node)`; left the 2 grant-only bridges untouched as instructed.

| # | File : line (post-fix) | Before | After |
|---|---|---|---|
| 1 | `gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland/gang/Gang.java:143` (`removeMember`) | `user.setPermission(permission, false);` | `user.unsetPermission(permission);` |
| 2 | `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/gang/GangDemoteCommand.java:154` | `onlineUser.setPermission(node, false);` | `onlineUser.unsetPermission(node);` |
| 3 | `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/gang/GangPromoteCommand.java:186` | `onlineUser.setPermission(node, false);` | `onlineUser.unsetPermission(node);` |
| 4 | `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/gang/GangTransferCommand.java:185` (loop 1) | `user.setPermission(node, false);` | `user.unsetPermission(node);` |
| 5 | `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/gang/GangTransferCommand.java:206` (loop 2) | `targetUser.setPermission(node, false);` | `targetUser.unsetPermission(node);` |

The two `initializeUserPermission` bridges (`PlayerBootstrapService.java:127-134`,
`CreateAccountListener.java:119-127`) were re-checked and confirmed to contain **no** `setPermission(_, false)`
call at all — they only grant (`setPermission(perm, true)`) against a freshly-attached, empty
`PermissionAttachment`, so there is nothing to clear and nothing to change. Left exactly as they were, per the
review's instruction.

Every `ponytail:` tag on all 7 bridge sites (5 fixed + 2 untouched) is preserved — G2's `RankPermissionApplier`
still replaces every one of them.

### Red-first for real (F2's second clause — genuine failing run required, not compile-red)

`UserTest` can't call the actual production bridges directly (`gangland-core` doesn't depend on
`gangland-domain`/`gangland-impl`), so the test replicates the clear-loop's exact pattern inline against a mocked
`PermissionAttachment` and asserts the contract the bridges must satisfy. Written and run *before* adding
`unsetPermission` — using the then-current bridge pattern (`grantedPermissionNames()` + `setPermission(node,
false)`, both of which already existed and compiled) — to get a genuine behavioural failure, not a
does-not-exist compile error:

**Red** (`mvn test -pl gangland-core -am -q -Dtest=UserTest#clearPath_mustCallUnsetPermission_notSetPermissionFalse`):
```
Wanted but not invoked:
permissionAttachment.unsetPermission(
    "old.node"
);
However, there were exactly 2 interactions with this mock:
permissionAttachment.getPermissions();
permissionAttachment.setPermission(
    "old.node",
    false
);
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

Then: added `User.unsetPermission(String)`, switched the test's loop body from `user.setPermission(node, false)`
to `user.unsetPermission(node)`, and fixed the 5 production sites the same way.

**Green** (`mvn test -pl gangland-core -am -q -Dtest=UserTest`): `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`
(15 from the original G0 pass + 1 new — this fix round adds exactly the one test F1 asked for, no more).

---

## F2 (Important, policy) — W48 ruling applied

Per orchestrator ruling **W48**: "red by non-existence" (the test cannot compile before the member exists) is
accepted as the red run for new coverage of **newly added members**; every bug pin or behaviour-change test still
needs a genuine failing run.

- The **6 `UserTest` primitive tests** (`grantedPermissionNames_*` ×2, `setPermission_*` ×2, `updateCommands_*`
  ×2) added in the original G0 pass, and the **2 `MemberCachePopulationOrderTest`** cases, fall under the
  **first clause**: they cover members (`grantedPermissionNames()`, `setPermission(String,boolean)`,
  `updateCommands()`) that did not exist before that pass, so "red" for them meant "doesn't compile against the
  pre-change class" — accepted per W48, not re-litigated here.
- The **F1 test** (`clearPath_mustCallUnsetPermission_notSetPermissionFalse`) falls under the **second clause** —
  it is a behaviour-change test (asserting the clear loop must call `unsetPermission`, not
  `setPermission(_, false)`), so a genuine failing run was required and produced (above), not just non-existence.

---

## M3 (Minor) — `Permission.setID` visibility

`Permission.setID(int)` was widened from `protected` to `public` in the original G0 pass because `RankManager`
(which calls it during boot/reload to resync the static id counter after a bulk load) moved from being
same-package with `Permission` (both in `gang.rank`) to a different module (`Permission` now in
`gangland-core.permission`, `RankManager` still in `gangland-domain.gang.rank`) — `protected` was no longer
reachable. This widening is a **G0-only artifact of the identity/gang split being mid-flight**: once `RankManager`
itself moves into the `gang` runtime module (a later WS5 gate, currently targeted at G1 per the plan's step 6),
`RankManager` and `Permission` will again be governed by an explicit module boundary that doesn't need `public`
visibility on a pure bookkeeping setter — **`setID` should be narrowed back down** (to `protected`, or reduced
further to a package-private/module-internal mechanism if the gang module's own package layout allows it) as
part of that gate. Not narrowed now because `RankManager` still needs it and no narrower access level reaches
across the current module boundary. Flagged for G1's executor.

---

## Build

Full reactor, one build at a time, never `mvn install`:

```
mvn test -pl gangland-core -am -q -Dtest=UserTest#clearPath_mustCallUnsetPermission_notSetPermissionFalse
    → RED (genuine failure, shown above) — before unsetPermission existed
mvn test -pl gangland-core -am -q -Dtest=UserTest
    → GREEN, Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 — after the fix
mvn clean verify
    → BUILD SUCCESS
```

Surefire file count: **153** `.txt` reports (unchanged from the original G0 gate — no new test *class*, one new
test *method* added to the existing `UserTest.java`). Test count = sum of every module's
`target/surefire-reports/*.txt` "Tests run" lines: **824** (823 + 1, exactly matching the gate's expectation).
**Failures: 0, Errors: 0, Skipped: 0.**

---

## Deliverables

- `exec/WS5/G0-fix1-report.md` (this file)
- `exec/WS5/G0-fix1-package.diff` — cumulative diff (`git add -N . && git diff HEAD`, then `git reset`), 246
  changed files, includes every change from the original G0 pass plus this fix round.

No commits, no smoke, no `scenarios.json` touched, no subagents (the fix was 6 small, precisely-located edits
plus one test — not worth spawning a haiku agent for).

## Concerns / open questions

None new. The 3 open items from the original G0 report stand: (1) `IdentitySettingsContract`'s module placement
should be corrected in the plan text for G1's executor; (2) `UserManager`'s unused `gangland` field, left alone;
(3) step 1c's live-boot proof is still owed to the 0.10.0 lead's merge smoke. M3 above is a new, fourth item for
G1's executor specifically (narrow `Permission.setID` back down once `RankManager` moves into the module).
