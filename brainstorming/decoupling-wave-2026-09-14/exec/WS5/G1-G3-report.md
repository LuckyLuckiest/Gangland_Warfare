# WS5 (gang module) — G1+G2+G3-relocation-slice combined gate (W51 ruling a)

Branch `0.10.0-ws5`, base `5b915c17` (WS3 lootchest merge picked up per the rebase instruction), working
tree at `E:\Programming\java\wt\gangland-0.10.0-ws5`. **Nothing committed** — per the dispatch, this report and
the package diff are the hand-back artifact; the coordinator commits after review.

## Gate result

```
mvn clean verify -pl gangland-features/gangland-gang -am   → BUILD SUCCESS (9 modules in the chain)
mvn clean verify                                            → BUILD SUCCESS (all 20 reactor modules)
mvn -pl gangland-build -am package (ran as part of verify)  → target/gangland_warfare-0.10.0.jar +
                                                                target/modules/gangland-gang-0.10.0.jar
                                                                (module.yml + commands.json confirmed at its root)
```

No `mvn install` was run at any point (per instruction); all resolution went through reactor `-am` builds.
The module gate and the full reactor gate were each run to completion at least once; the full reactor needed
one fix (see "Fix landed mid-gate" below) and was re-run clean afterward.

### Test counts — Maven console rollup (W52: surefire `.txt` under-counts `@Nested`, so these are the
authoritative per-module `Tests run:` lines from the final full-reactor `mvn clean verify`, not a summation of
surefire report files)

| Module | Tests | Failures/Errors |
|---|---|---|
| gangland-core | 67 | 0 |
| gangland-item | 43 | 0 |
| sign-api | 63 | 0 |
| **gangland-api** | **8** | 0 |
| gangland-impl | 227 | 0 |
| **gangland-gang** | **85** | 0 |
| gangland-mail | 25 | 0 |
| gangland-civilians | 20 | 0 |
| gangland-turf | 91 | 0 |
| cops-n-crooks | 76 | 0 |
| gangland-gadget | 113 | 0 |
| gangland-npc-shops | 17 | 0 |
| gangland-lootchest | 42 | 0 |
| **Reactor total** | **877** | **0** |

(gangland-domain, gangland-infra, gangland-ui, gangland-features, gangland-build, the root pom — 0 tests,
either pure-`pom` aggregators or now-empty jars.)

**Delta explained** — moved tests keep their names (per instruction), so the module-level counts above are not
directly comparable to a pre-gate baseline; the two genuine count changes are:
- `gangland-impl`'s `InformationManagerTest.processCommands_populatesFromBundledJson` pinned entry count dropped
  `153 → 121` (see "Fix landed mid-gate").
- `gangland-gang` gained 3 brand-new test methods this gate (not moved): `RankPermissionApplierTest` (4 tests,
  new file), `RankManagerTest.SeedInitialRanksTest` (2 tests, new nested class), and `gangland-api` gained
  `GangMembershipTest` (8 tests, new file) — all documented under "New tests" below.

### Fix landed mid-gate

The first full-reactor run failed on `gangland-impl`'s `InformationManagerTest` — a drift test that pins the
exact entry count of the bundled `commands.json` and documents every historical move in its own assertion
message. Moving the 32 gang/rank entries out (see "commands.json split" below) dropped the count `153 → 121`;
updated the assertion and its message to add the new clause, verified it now passes, re-ran the full reactor
clean. This is not a red-first behavior pin — it is the same "update alongside any deliberate commands.json
edit" maintenance the test's own docstring calls for.

## Step table (plan step numbers, `plans/WS5-gang.md` §4)

| Step | What | Status |
|---|---|---|
| 5 | `gangland-gang` module skeleton (pom, `module.yml`, `GangModule`, `GangModuleTest`) | Done (prior turn) |
| 6 | Move `Gang`/`GangAlliance`/`GangManager`/`GangSettings`/7 contracts/`Member`/`MemberManager`/`Rank`+rank family/`GangPermissions`/`VaultPermissionBridge`, package unchanged | Done (prior turn) |
| 7 | Persistence subtree move + package rename (`database.*` → `gang.database.*`), `ForeignUserTable`/`ForeignPermissionTable` FK stand-ins | Done (prior turn) |
| 7b | Rank seeding moved out of `GanglandDatabase.insertInitialData()` into `RankManager.seedInitialRanks()` | **Done this session** |
| 8 | 4 impl binder classes (`GanglandGangMessages`/`GanglandGangPermissionBridge`/`GanglandGangSettings`/`GanglandRankLookup`) moved | Done (prior turn) |
| 9 | `UserLookupContract`/`PermissionRegistryContract` relocated to `gangland-core` (module-reachable) | Done (prior turn) |
| 9b | `GangMembership`/`GangMembershipView` holder (R9) in `gangland-api`; `IdentityContractConfig.gangMembership()` bean | Done (prior turn) |
| 9c | `GangConfig` (module's own `@Configuration`) | Done (prior turn) |
| 10 | `UserDataLoader` drops `MemberManager`, reads gang id via `GangMembership` | **Done this session** |
| 11 | — (folded into 11b) | — |
| 11b | `RankPermissionApplier` (static) replaces all 7 ponytail bridges: `Gang.removeMember`, `GangDemoteCommand`, `GangPromoteCommand`, `GangTransferCommand` ×2 (prior turn), `PlayerBootstrapService`/`CreateAccountListener` via new `MemberJoinListener` (**this session** — see "The wall and its resolution") | Done |
| 12 | `GanglandPlaceholder`'s `gang_*`/member-touching `user_*` placeholders → `PlaceholderContribution` seam + `GangPlaceholderContribution` | **Done this session** |
| 13 | `WaypointGangIdCommand` — investigated, **stays in impl** (see WS6 ask #2) | **Done this session** |
| 14 | `ComponentExecutorCommand`'s gang/rank branch → `GangOptionContribution`; `DebugCommand`'s gang-data/member-data/rank-data → `GangDebugContribution` | **Done this session** |
| 15b/15c | `BountyIncreaseListener`/`LevelUpListener` gang-event handlers → `GangBountyMessageListener`/`GangLevelMessageListener` | **Done this session** |
| 16 | `Gangland.java` Vault-permission wiring + bStats rank/gang charts removed; Vault lifecycle → `GangModule.onEnabled/onDisabled` | **Done this session** |
| 16b/16c/16d | S1 rewrite: `GanglandCarGangs`+`GadgetModuleConfig` (gadget), `GangAllyWeaponImpactListener` (civilians), `TurfFriendlyFireListener` (cops-n-crooks) — all onto `GangMembership`, all 3 modules' `gangland-domain` pom dependency removed entirely | **Done this session** |
| — | turf/mail pom swap `gangland-domain` → `gangland-gang` (no source change), `Depends: [gang]` added to both `module.yml`s | **Done this session** |
| 20 | 31 gang/rank commands + `commands.json` split (32 entries) | Commands: done (prior turn). commands.json split: **done this session** |
| 20b | `GangMembersDamageListener` | Done (prior turn) |
| S3 | `GangFilterAdapter`/`MemberFilterAdapter` — **not moved**, deleted + stubbed (WS6 ask #1, pre-approved stopgap, prior turn) |
| M3 | `Permission.setID` — confirmed permanently `public`, documented as final (not narrowable) | **Done this session** |

## The wall and its resolution (recovery-message follow-up)

The session was terminated mid-verification of a fork's claim about a "second compile-boundary wall" around
`GangDeleteCommand`/`GangKickCommand`. On resume: reconciled the worktree (3 torn edits from the interrupted
run — stale `PermissionTable`/`UserTable` imports in `RankPermissionRepository`/`MemberRepository`, and 3 test
files still importing the deleted `gang.support.FakeIdentitySettingsContract`), then re-verified the claimed
wall. **It was not real** — `GangDeleteCommand`/`GangKickCommand` only needed `RepositoryRegistry`/`IRepository`
(pure Keystone types) plus the already-populated `offlineUserManager` cache; both compiled and moved into the
module cleanly (already done before the interruption).

The **actual** remaining wall, found by continuing the investigation past that point: `UserDataInitEvent` (the
event `PlayerBootstrapService`/`CreateAccountListener` fire once a `User` is hydrated) was impl-owned, but the
module needed to listen for it to finish rewiring the last 2 of 7 `RankPermissionApplier` bridges. Resolution,
within the ruled scope ("an impl consumer that reaches the module only through gangland-api"): moved
`UserDataInitEvent` to `gangland-api` (package unchanged — mirrors `UserLevelUpEvent`, already api-hosted there,
confirmed via direct lookup before moving), a pure relocation with zero import changes for its 3 impl
consumers. Wrote a new `MemberJoinListener` in the module reacting to it, which now **owns** both Member
creation/hydration (previously duplicated inline in both impl call sites) and the rank-permission attach
(previously the ponytail bridges). Deliberately dropped the old per-join `MemberManager.initializeMemberData`
DB re-fetch: `MemberManager.initialize()` (a `BeanLifecycle` bean) already loads every existing row from the DB
on every boot/reload before any player can join, so a cache miss in `MemberJoinListener` can only mean a
genuine first-ever join — the re-fetch was defensive redundancy, not load-bearing behavior. See
`MemberJoinListener`'s javadoc for the full reasoning, including the async-vs-sync thread-hop handling (a
handler invoked from an async-fired event runs on that async thread and must hop back to main before touching
`PermissionAttachment`, exactly like the original `CreateAccountListener` bridge did via
`Bukkit.getScheduler().runTask(...)`).

## Move map (this session's portion — G0/G1 prior-turn moves are in the earlier gate reports)

**Package-unchanged relocations** (`git mv`, zero import churn beyond the moved file itself):
- `gangland-impl/.../database/repositories/gang/GangAllianceRepositorySpiTest.java` →
  `gangland-gang/.../gang/database/repositories/gang/` (package renamed `database.repositories.gang` →
  `gang.database.repositories.gang` to match its subject's already-moved package; settings binding switched
  from `GanglandGangSettings`+`SettingsFixture` to the module's own `FakeGangSettingsContract`/
  `FakeIdentitySettingsContract`, matching every sibling gang test)
- `gangland-impl/.../database/repositories/rank/RankRepositorySpiTest.java` → same treatment,
  `gang.database.repositories.rank`
- `gangland-impl/.../events/user/UserDataInitEvent.java` → `gangland-api/.../events/user/` (package unchanged)

**New files, `gangland-api`:**
- `data/gang/GangMembership.java`, `data/gang/GangMembershipView.java` — already existed (prior turn)
- `data/placeholder/extension/PlaceholderContribution.java`, `PlaceholderContributions.java` — new seam,
  mirrors `command/extension/CommandContribution(s)`
- `src/test/.../data/gang/GangMembershipTest.java` — new, 8 tests

**New files, `gangland-core`:**
- (none this session; `UserLookupContract`/`PermissionRegistryContract` relocated prior turn)

**New files, `gangland-impl`:**
- `database/tables/fk/ForeignGangTable.java` — mirrors the module's `ForeignUserTable`/`ForeignPermissionTable`
  in reverse (impl can't import the module's real `GangTable`), so `WaypointTable`'s FK to `gang` keeps working
  with an unchanged schema

**New files, `gangland-gang`:**
- `gang/listener/gang/MemberJoinListener.java`, `GangBountyMessageListener.java`, `GangLevelMessageListener.java`
- `gang/command/option/GangOptionContribution.java`, `gang/command/debug/GangDebugContribution.java`
- `gang/placeholder/GangPlaceholderContribution.java`
- `gang/permission/RankPermissionApplierTest.java` (test)
- `RankManagerTest.SeedInitialRanksTest` (nested test class added to the existing file)

**Deleted:**
- `gangland-impl/.../file/configuration/inventory/itemsource/GangItemSourceProvider.java`,
  `gang/GangFilterAdapter.java`, `gang/member/MemberFilterAdapter.java`, `config/GangFilterRegistration.java` +
  their 2 tests (prior turn, confirmed intact this session — see WS6 ask #1)
- `gangland-impl/.../config/GangModuleConfig.java` (prior turn, replaced by `IdentityContractConfig` +
  `GangConfig`)

**Trimmed in place (impl, no relocation):**
- `CreateAccountListener.java`, `PlayerBootstrapService.java` — Member/rank-permission logic removed entirely,
  now pure User creation + event fire
- `UserDataLoader.java` — `MemberManager memberManager` → `GangMembership gangMembership`
- `WaypointTable.java`, `WaypointRepository.java`, `WaypointManager.java` — `GangTable` → `ForeignGangTable`
- `DataConfig.java` — `rankManager`/`gangManager`/`memberManager` beans deleted (now in `GangConfig`);
  `userDataLoader` bean's `MemberManager` param → `GangMembership`
- `SchedulingConfig.java` — `playerBootstrapService` bean drops its `MemberManager` param
- `WiringConfig.java` — `ganglandPlaceholder` bean drops `MemberManager`/`GangManager` params, gains
  `DependencyContainer` (see "Placeholder contribution timing bug" below)
- `GanglandPlaceholder.java` — `getUser`'s member block and the whole `getGang` method deleted; dispatch falls
  through to `contributions().resolve(...)`
- `ComponentExecutorCommand.java` — gang/rank branch deleted (`gangArgument`/`getRankType`/`messageFor`), now
  pulls it from `CommandContributions.createFor("option", ...)`
- `DebugCommand.java` — `getGangData`/`getMemberData`/`getRankData` deleted; `getAnvil()`'s gang-description
  prefill dropped to empty string (debug-only convenience, not worth a new seam — see docstring)
- `WaypointGangIdCommand.java`, `WaypointCommand.java` — `GangManager` dependency removed; the real validation
  (`user.getGangId() != id`) never needed it, only the cosmetic gang-name display did (degraded to the raw id)
- `BountyIncreaseListener.java`, `LevelUpListener.java` — `onGangBountyIncrease`/`onGangLevelUp` deleted
- `Gangland.java` — Vault-permission block (both onDisable teardown and dependencyHandler's linking) and the
  `number_of_ranks`/`number_of_gangs` bStats charts deleted
- `GanglandDatabase.java` — `insertInitialData()` body emptied (now a documented no-op; the abstract method
  still fires once from Keystone's `DatabaseHandler.initialize()`, before any bean exists)
- `Permission.java` — javadoc updated to mark M3 resolved (public is final, not temporary)

**Trimmed in place (cops-n-crooks/civilians/gadget, S1):**
- `TurfFriendlyFireListener.java` (cops-n-crooks) — `GangLookupContract gangs` → `GangMembership membership`;
  `isFriendly` now `attackerGangId == ownerGangId || membership.gangsAllied(...)`
- `GangAllyWeaponImpactListener.java` (civilians) — `UserManager`+`GangManager` → single `GangMembership`
  param; body collapses to one `membership.alliedOrSame(damager.uuid, damaged.uuid)` call (its `idA != -1`
  guard already excludes the both-gangless false positive the old explicit `hasGang()` checks existed for)
- `GanglandCarGangs.java` (gadget) — `MemberManager` → `GangMembership`; deliberately uses `gangIdOf` equality,
  **not** `alliedOrSame` (GD-06 pins "shares a gang", not "same or allied" — an ally is not close enough to
  share a car)
- `GadgetModuleConfig.java` (gadget) — `carAccessPolicy` bean's stale javadoc ("which the gadget module cannot
  see — hence the contract") now matches the actual code for the first time

## Bean list (new/changed, this session)

| Bean | Where | Type |
|---|---|---|
| `gangPlaceholderContribution` | `GangConfig` | `PlaceholderContribution` |
| `gangOptionContribution` | `GangConfig` | `CommandContribution` |
| `gangDebugContribution` | `GangConfig` | `CommandContribution` |
| `userDataLoader` | `DataConfig` (impl) | unchanged bean name, param swapped `MemberManager`→`GangMembership` |
| `ganglandPlaceholder` | `WiringConfig` (impl) | unchanged bean name, params swapped, now takes `DependencyContainer` |
| `playerBootstrapService` | `SchedulingConfig` (impl) | unchanged bean name, `MemberManager` param dropped |

`MemberJoinListener`, `GangBountyMessageListener`, `GangLevelMessageListener` are `@ListenerHandler` classes
(package-scan discovered via `GangModule.LISTENER_PACKAGE`, no explicit `@Bean` registration needed — same as
every other listener in the module).

## `module.yml` (gangland-gang, unchanged this session, shown for completeness)

```yaml
Id: gang
Name: Gangland Gangs
Version: ${project.version}
Main: org.luckyraven.gangland.gang.GangModule
Host_Api: 2.0
Artifact: org.luckyraven:gangland-gang
```

`gangland-turf`'s and `gangland-mail`'s `module.yml` each gained `Depends: [gang]` this session (turf's list
becomes `[civilians, gang]`) — both modules directly name `Gang`/`GangManager`/etc. types, so the dependency is
functionally real, not just documentation.

## commands.json split

Moved 32 gang/rank entries (`gang`, `gang_help`, `gang_create`, `gang_remove`, `gang_display_name`,
`gang_display_remove`, `gang_color`, `gang_economy_deposit`, `gang_economy_withdraw`, `gang_balance`,
`gang_members`, `gang_change_name`, `gang_change_desc`, `gang_kick_player`, `gang_leave`, `gang_rank_promote`,
`gang_rank_demote`, `gang_transfer`, `gang_ally_remove`, `rank`, `rank_help`, `rank_create`, `rank_delete`,
`rank_list`, `rank_add_permission`, `rank_remove_permission`, `rank_info`, `rank_traverse`, `rank_parent_add`,
`rank_parent_remove`, `rank_vaultgroup_set`, `rank_vaultgroup_clear`) from `gangland-impl/src/main/resources/
commands.json` (153 → 121 entries) into a new `gangland-gang/src/main/resources/commands.json` (32 entries),
matching every other module's convention (verified against `gangland-mail`'s own `commands.json`, which already
carries the 7 mail-owned gang-invite/ally-request entries this repo split off separately). The core file's diff
is a pure 128-line deletion — no reformatting, no reordering of the remaining 121 entries (verified via
`git diff --stat`).

## Seam classes explained

- **`RankPermissionApplier`** (`gang.permission`, static, not a bean) — replaces all 7 ponytail bridges for the
  deleted `UserManager.initializeUserPermission`/`User.flushPermissions(Rank)`. `initialize(plugin, user,
  member)` = first attach; `flush(user, rank)` = re-apply after promote/demote/transfer/leave/kick/disband,
  using `unsetPermission` (never `setPermission(_, false)` — the whole point being an unset node still falls
  back to a Vault/LuckPerms group grant, an explicit deny would override it). Static because one call site
  (`Gang.removeMember`) is inside a plain POJO with no DI access, same reasoning as `GangPermissions`/
  `VaultPermissionBridge`.
- **`GangMembership`/`GangMembershipView`/`GangMembershipInstaller`** (R9) — the "is/which gang" fact holder,
  sibling of `BankTiers`. Always present as a zero-arg core bean (`IdentityContractConfig.gangMembership()`);
  inert (`gangIdOf` → -1, `gangsAllied`/`alliedOrSame` → false) until the module's `GangMembershipInstaller`
  installs a live view in its `@PostConstruct`. This is the mechanism that let `UserDataLoader`,
  `TurfFriendlyFireListener`, `GangAllyWeaponImpactListener` and `GanglandCarGangs` all drop their `GangManager`/
  `MemberManager` dependency this session.
- **`IdentityContractConfig`** (impl) — replaces the deleted `GangModuleConfig`; wires
  `identitySettingsContract`/`userLookupContract`/`permissionRegistryContract` (core-hosted contracts a module
  can consume) plus the `gangMembership()` holder bean.
- **`GangConfig`** (module) — the module's own `@Configuration`: the 5 wholly-module contracts, the 2
  `GangLookupContract`/`RankLookupContract` bindings, `GangManager`/`RankManager`/`MemberManager` beans, and
  (new this session) the 3 contribution beans.
- **`PlaceholderContribution`/`PlaceholderContributions`** (new, api) — mirrors `CommandContribution(s)`
  exactly. One difference from the command version: `GanglandPlaceholder` is a **CONFIG**-phase bean, and a
  module's `PlaceholderContribution` bean has no declared parameter edge forcing it to construct first within
  that same phase (unlike `CommandContributions`, only ever looked up from the later COMMAND phase after every
  module bean is guaranteed to exist). Building `PlaceholderContributions.from(container)` eagerly at
  `WiringConfig.ganglandPlaceholder`'s construction time would have been a genuine, easy-to-miss ordering bug —
  caught before it shipped by tracing bootstrap phase order, not by a failing test. Fixed by holding the raw
  `DependencyContainer` and resolving lazily (cached after first call) on the first actual placeholder request,
  which is always well after full bootstrap.
- **`GangOptionContribution`/`GangDebugContribution`** (module) — same `CommandContribution` pattern
  `BankMenuContribution` already established; `ComponentExecutorCommand`/`DebugCommand` in impl now pull their
  gang-owned sub-arguments from `CommandContributions.createFor(...)` instead of constructing them inline.

## Tests, with red-first runs (W48)

- **`RankPermissionApplierTest`** (new file, 4 tests) — genuinely red-first verified: temporarily reverted
  `flush`'s `unsetPermission` call back to `setPermission(_, false)`, ran the suite, confirmed 2 of 4 tests
  failed with the exact Mockito diff showing `setPermission("old.node", false)` was called instead of
  `unsetPermission("old.node")`, then reverted and confirmed green again. Full run: `Tests run: 4, Failures: 0,
  Errors: 0` after revert; `Tests run: 4, Failures: 2` during the temporary break.
- **`GangMembershipTest`** (new file, `gangland-api`, 8 tests) — a brand-new class (the holder itself is new
  this gate), so "red by non-existence" is the honest category per W48's distinction; covers the inert-until-
  installed default (-1/false), null-uuid safety, delegation once installed, and — the one behavior worth
  pinning precisely — `alliedOrSame`'s `idA != -1` guard against the "both gang-less uuids compare equal (-1
  == -1)" false positive.
- **`RankManagerTest.SeedInitialRanksTest`** (new nested class, 2 tests) — proves `RankManager.initialize()`
  now calls `RankRepository.insertInitialRanks`/`RankParentRepository.insertInitialRelation` before
  `loadAll()`, using concrete-class Mockito mocks (not the outer test's `IRepository` interface mocks) so the
  `instanceof RankRepository` guard `seedInitialRanks` branches on actually passes. A second test confirms the
  guard fails closed (no exception) against the outer suite's plain interface mocks, proving old tests weren't
  silently broken by the new seeding call.
- Pre-existing `UserTest`'s unset-semantics pin (`unsetPermission` not `setPermission(_, false)`, from WS5 G0
  fix round 1 F1) still passes unmodified — `RankPermissionApplier.flush` calls the same `User.unsetPermission`
  primitive that test already pins at the `User` level; the new test re-verifies the distinction survives at
  the new call site specifically, since that's where a regression could silently reappear without either test
  catching it alone.

## Deferred smoke rows

None run this session (not in scope per the dispatch: "no smoke... G1's rows + your step-1c live proof run at
the merge"). No new rows to defer beyond what G1's own report already listed.

## WS6 asks (gaps found this gate, smallest additive proposal each)

1. **Gang menu item sources** (`GangItemSourceProvider`/`GangFilterAdapter`/`MemberFilterAdapter`, deleted
   prior turn, confirmed intact this session). `gang_info.yml`/`gang_stat.yml`/`phone_gang.yml`/
   `phone_gang_search.yml`/`alliance_stat.yml` render with no dynamic rows until a real contract lands.
   Proposal: an api-hosted `GangItemSourceContribution` interface (mirrors the new `PlaceholderContribution`
   exactly — one `List<ItemStack> items(Player, String sourceId)` method), with the module supplying the real
   implementation and the core YAML menu path staying untouched otherwise.
2. **`WaypointGangIdCommand`'s gang-name display** (this session). `/glw waypoint gangid <id>` tab-completes
   and echoes the caller's gang **id** instead of its **name** now (the real validation — `user.getGangId() !=
   id` — never depended on `GangManager` at all). Proposal: extend `GangMembership` with `nameOf(int gangId)` →
   `String` (default `""`/absent-safe, mirrors `gangIdOf`'s absent-sentinel pattern) if the cosmetic name
   matters enough to justify the api surface; low priority, this is an admin/setup-only command.
3. **`DebugCommand.getAnvil()`'s dropped gang-description prefill** (this session, debug-only). Not worth a
   seam on its own; folding into ask #1's `GangItemSourceContribution`-style mechanism (if built) would cover
   it for free since it just needs `gang.getDescription()` by the caller's own gang id.

## Docket notes

No new bugs found or fixed this gate — this was a pure architectural relocation with behavior held constant
(verified via the red-first tests above and the unchanged assertion counts in every moved/untouched test). The
already-referenced docket ids (GR-01, GR-02, GR-03, GR-05, GR-06, GR-08, GD-06) all still have their pinning
tests intact and green; none needed re-triage.
