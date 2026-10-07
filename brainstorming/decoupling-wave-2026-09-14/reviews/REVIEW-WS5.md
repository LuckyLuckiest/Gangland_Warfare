# REVIEW WS5 — the gang system becomes a runtime module, turf builds on it

Verdict: **REWORK**

The gate structure (G0 repackage → G1 module+persistence → G2 hard part → G3 menus → G4 re-point → G5 delete),
the persistence story, the `Depends:` topology and most of the seam table are sound and well evidenced. But the
plan's load-bearing premise — "move `User`/`UserManager` to core, *update every importer's import line only, no
behaviour change*" (§4 step 1) — is false at the source level, and three other claims the plan rests on
(cops-n-crooks has no `Gang` import; the `MemberManager` ordering edge does not exist; an absent contract bean
just means "never called") are contradicted by the code. §1–§3 need re-cutting before an executor starts; §4–§12
survive largely intact once the boundary is right.

Graph checks run this session: `graphify affected "GangManager"` (matches the plan's consumer list exactly),
plus full-tree import greps per Maven module. `graphify affected "Gang"` still resolves to `WaypointType.GANG` —
the plan's §13 warning about the literal matcher is correct and worth keeping.

## Blockers (must fix before executors start)

- **B1. cops-n-crooks *does* import `Gang`/`GangLookupContract`; §2's "unchanged" row and §9 D2's grep are wrong.**
  `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/listener/turf/TurfFriendlyFireListener.java:16-19`
  imports `gang.Gang`, `gang.contract.GangLookupContract`, `gang.contract.UserLookupContract`, `gang.user.User`.
  Today it compiles through `gangland-api`'s `gangland-domain` re-export; after G5 deletes that artifact,
  cops-n-crooks **will not compile**. Required: `cops-n-crooks/pom.xml` gains `gangland-gang` at `provided`
  scope and `module.yml` becomes `Depends: [turf, civilians, gang]`. (The D2 *conclusion* — Wanted stays core —
  still holds; only its stated evidence collapses.)

- **B2. `User`/`UserManager` cannot move to core "import-line only".** The identity slice names gang types in its
  own body:
  - `gang/user/User.java:17,20,21` imports `GangSettings`, `rank.Permission`, `rank.Rank`;
    `:63-67` builds `Bounty`/`Wanted` from `GangSettings.getBountyEachKillValue/getBountyTimerMultiple/
    getWantedLevelIncrement/getWantedMaximumLevel`; `:229-241` `flushPermissions(@Nullable Rank)`.
  - `gang/user/UserManager.java:12-14` imports `Member`, `Rank`, `Permission`; `:124-139`
    `initializeUserPermission(User<Player>, Member)`.
  - `gang/user/Level.java` also reads `GangSettings`.
  Required decisions the plan must make explicitly:
  1. **Split `GangSettings`/`GangSettingsContract`.** 11 of its 14 getters are identity, not gang:
     `GangSettingsContract.java:11-31` = `isAutoSave`, `getUserMaxLevel/BaseAmount/Formula`, 4 × bounty, 3 ×
     wanted; only `:33-37` (`getGangDisplayNameChar`, `getGangRankHead`, `getGangRankTail`) are gang. §2 moves the
     whole class to the module while §5 keeps `Bounty:`/`Wanted:`/`User:` in core `settings.yml` — contradictory.
  2. **`flushPermissions(Rank)`** (4 call sites, all gang commands: `GangDemoteCommand.java:147`,
     `GangPromoteCommand.java:179`, `GangTransferCommand.java:179,188`, plus `Gang.java:137`) must move module-side
     with `initializeUserPermission`; §3 row 8 names only the latter.
  3. `initializeUserPermission`'s body is **no longer unverified**: `UserManager.java:124-139` — reads
     `member.getRank()`, creates a `PermissionAttachment`, calls `user.setPermissionAttachment(...)`, applies
     `rank.getPermissions()`, `updateCommands()`. Small, but it mutates core `User` state and **must stay on the
     main thread** (`CreateAccountListener.java:110-116` wraps it in `Bukkit.getScheduler().runTask`). Carry that
     hop into whatever module-side service replaces it.

- **B3. A missing contract bean aborts the boot — smoke row S1 as written cannot pass.** Seam row 6 says "absent
  module → no `MembershipLookupContract` bean registered → `gangIdOf` never called". Keystone's
  `keystone-bean/.../BeanFactory.java:517-522` throws `IllegalStateException("Cannot resolve parameter … no bean
  of that type is registered")` for any unresolved `@Bean` parameter, and `userDataLoader(...)` takes that param
  (`DataConfig.java:105-112` today, `MemberManager`). On a gangless server `Gangland.onEnable` dies.
  Required: a **core-owned inert holder**, exactly the pattern already in `DataConfig.java:152-166`
  (`GanglandMoneyDropClassifier`, `BankTiers`, `WantedKillTrackers`), or `@ConditionalOnBean`
  (`keystone-bean/.../conditional/ConditionalOnBean.java`). See S1 for the version that also kills two `Depends:`
  edges.

- **B4. The `MemberManager` → `UserManager` ordering edge is real and §6 denies it.** `DataConfig.java:69-82`:
  *"Both UserManager beans declare MemberManager as a parameter purely for ordering … Dropping the parameter
  re-creates the pre-0.8.0 bug where members loaded after users and every member's gang link self-healed to -1 on
  startup"*, and `:91-99` repeats it on the `offline` bean. §6 claims the invariant "is no longer needed … the
  actual code shows the opposite edge" — both edges exist (`userDataLoader` at `:105-112` is the *second* one).
  A core-owned holder satisfies resolution but gives **no ordering guarantee** (it is a core CONFIG bean created
  long before the module's `MemberManager`). The plan must say which of these it relies on:
  (a) `PlayerBootstrapService implements BeanPostInitialize` (`PlayerBootstrapService.java:30-41`: runs after every
  `BeanLifecycle.onInitialize`) already guarantees member caches are loaded before user loading, making the
  parameter belt-and-braces — **prove it, then delete the parameter and correct
  `feedback_bean_ordering_via_params.md`**; or (b) keep an equivalent edge. Do not leave this to the executor.

- **B5. Two core listeners and the core database handler name module types; none are in the plan.**
  | File | Line | What breaks |
  |---|---|---|
  | `database/GanglandDatabase.java` | `:9-10,136-145` | Seeds the initial rank pair — `IRepository<Rank>`, `RankParent`, `RankRepository.insertInitialRanks(Settings.getGangRankHead(), getGangRankTail())`. Core cannot name `Rank` after the move; this seeding must move into the gang module (docket CL-18 lives here). |
  | `listener/player/BountyIncreaseListener.java` | `:13,42-53` | `@EventHandler onGangBountyIncrease(GangBountyEvent)` — step 15 moves `GangBountyEvent` to the module. Compile break, **and** a Bukkit `NoClassDefFoundError` on a gangless server (the same failure mode CLAUDE.md documents for Citizens-typed handlers; the repo's own fix is to split the module-typed handler into a module-side listener class). |
  | `listener/player/LevelUpListener.java` | `:11,45` | same, `GangLevelUpEvent`. |
  Bounty/Level stay core per D2, so these two handlers must be split, not moved wholesale.

- **B6. WS5 never answers the question WS6 explicitly parked on it.** `plans/WS6-api.md` §3 last row: *"`gang()`-style
  optional-module facade exposure — **pending WS5** … flag, don't build until WS5 confirms"*, and WS6 §3's
  `gangs()`/`ranks()` accessors return `GangManager`/`GanglandRankLookup` from a `GanglandApiImpl` that lives in
  `gangland-impl` — which after WS5 cannot name either type. WS5 §10's "WS6 asks" lists two new interfaces and
  stops. Required: WS5 states the answer — drop `gangs()`/`ranks()` from the facade, or expose them through the
  same core-owned holder as B3/S1 (inert when the module is absent). Same for WS6 §2's `gangland-domain` pom row,
  which is blocked on WS5 naming the artifact that ships `User`/`UserManager`/`Level`/`LevelUpEvent` — note
  `gangland-api` itself has 4 files importing them (`data/teleportation/WaypointTeleport.java:15`,
  `events/teleportation/TeleportEvent.java:12`, `events/user/UserLevelUpEvent.java:6-8`,
  `sign/aspect/MoneyAspect.java:10-11`), so `gangland-api` must keep a compile-scope dependency on whichever
  artifact wins.

## Corrections (fix in place)

- **C1. `Host_Api: 1.0` in §2's `module.yml` violates C2 and PLAN §2 R1** (the 0.10.0 branch's first commit bumps
  `GanglandApi.VERSION` and every descriptor to `2.0`; all six existing descriptors are `Host_Api: 1.0` today).
  The new gang module is created at `2.0`.
- **C2. G0's gate command cannot be green.** The repackage touches **177 files across 9 Maven modules**
  (impl 124, turf 12, mail 12, gadget 7, domain 6, npc-shops 6, cops 4, api 4, civilians 2 — grep on
  `org.luckyraven.gangland.gang.user.`), but the gate builds `-pl gangland-core,gangland-domain,gangland-impl -am`.
  Make G0's gate a full-reactor `mvn clean install` + `mvn test`.
- **C3. `gangland-domain` has 18 test files of its own and the plan gives them no home.** `GangAllianceTest`,
  `GangManagerAllianceTest`, `GangMembershipTest`, `bounty/BountyTest`, `member/{MemberTest,MemberManagerTest}`,
  `permission/GangPermissionsTest`, `rank/{RankTest,RankManagerTest,RankAssignmentPolicyTest}`,
  `user/{UserTest,UserManagerTest,LevelTest}`, `wanted/{WantedTest,WantedKillTrackersTest}`, plus
  `support/{FakeGangSettingsContract,RecordingGangAllianceRepository,TestLevelUpEvent}`. §7 lists two impl tests
  and "48 files naming Gang/Member/Rank" but never says these split core/module. G5 deletes the module — without
  a destination that is a silent loss of the wave's best regression net (including the GR-12/13/35 pins).
- **C4. §11 is wrong on two rows and on the pin count.** From `bug-docket-2026-09-06/bugs.json`: GR-03 and GR-08
  are **fixed** (`tests`: *"FIXED 0.8.3 wave 3, commit 8a731fd5"*, covering tests `GangPermissionsTest` /
  `RankAssignmentPolicyTest`, both in the domain test tree) — not "bug travels unfixed"; their tests are part of
  C3's move. True pins are **3**: GR-12, GR-13 (`RankManagerTest (pins)`), GR-35 (`GangAllianceTest (pins; flip
  when fixed)`); GR-09 is a comment only. The 37-entry count and P0×6/P1×6/P2×18/P3×7 split are correct. No `GM-`
  or `RK-` prefix exists in the docket (prefixes: CJ CL CM CT GD GR IT LS T TF UI US WB WP) — the plan is right to
  use only `GR-`.
- **C5. `condition = "isGangEnabled"` silently becomes `false` when the `Gang:` block leaves core `Settings`.**
  `SettingsLookupImpl.java:23-37` returns `false` for an unknown key. Three sites: `GangCommand.java:52`,
  `listener/gang/GangMembersDamageListener.java:16` (both move into the module — fine, but the module's own
  `SettingsLookup` must then supply the key) and **`gangland-civilians/.../GangAllyWeaponImpactListener.java:25`**,
  which stays outside the gang module and would be silently unregistered. Also decide what `Gang.Enable: false`
  means once "module absent" is the real off switch — two switches for one feature is a knob to delete.
- **C6. §5's config migration loses every existing server's gang economy settings.** "Admin must copy this block
  into the module's data-folder YAML on upgrade" means `Create_Cost`, `Maximum_Balance`, `Contribution_Rate`,
  `Rank.Head/Tail` silently revert to defaults on first boot of 0.10.0. Add either a one-shot copy from the host's
  `settings.yml` when the module's YAML is freshly generated, or a loud boot warning while a `Gang:` block is
  still present. Sizing nit: the block is **10 parsed keys** (`Settings.java:542-554`), not "29 + 15 more".
- **C7. The WS2 hand-off is bigger than §3 row 13's YAML drop.** WS2 §3 shows the row-scoped
  `%member_*%`/`%ally_*%` tokens are built by `GangItemSourceProvider.java:74-104` and need a Gangland-written
  `PlaceholderProvider` adapter threaded through the paginated-source registration, and WS2 D5 replaces
  `Enchanted: true` at `gang_info.yml:19,46,59` with code-registered `ItemProviderRegistry` sources. All of those
  become module-owned. Name the mechanism by which a module registers into core's Oriel
  `SourceProviderRegistry`/`ItemProviderRegistry`/`FilterRegistry` (registry-injection, the existing family) — it
  is not in §3.
- **C8. `gangland-build` is three edits, not one "[S] confirm".** `gangland-build/pom.xml` lists every module in
  three places: shade `<exclude>` (`:82-87`), the `target/modules/` copy set (`:123-148`) and a `<dependency>`
  block (`:170-200`). Add also `gangland-features/pom.xml`'s `<module>` entry and the root `pom.xml`
  `dependencyManagement` entry for the new artifact — §4 step 5 mentions neither.
- **C9. Three §13 "not verified" items are now answered** (fold them in so the executor does not re-derive them):
  `SchedulingConfig.java:47-57`'s `MemberManager` param belongs to the `playerBootstrapService(...)` bean and is
  passed into `PlayerBootstrapService`'s constructor — not a scheduled task; `WaypointCommand.java:14,28,35,92`
  holds `GangManager` only to pass it into the child node constructed at `:92`; `initializeUserPermission` is
  `UserManager.java:124-139` (see B2).

## Simplifications (ponytail)

- **S1. One core-owned holder instead of two new api interfaces plus two new `Depends:` edges.** Replace
  `MembershipLookupContract` (row 6) with a `GangMembership` holder in core — `int gangIdOf(UUID)`,
  `boolean alliedOrSame(UUID, UUID)`, inert defaults (`-1`, `false`), `install(...)` from the gang module's
  `@PostConstruct` — modelled verbatim on `BankTiers` (`DataConfig.java:158-166`). It (a) fixes B3 by always
  existing, (b) gives gadget everything it needs: `GanglandCarGangs.java:24-33` is a pure `UUID,UUID → boolean`
  over `getGangId()`, so **gadget needs no `gangland-gang` pom dep and no `Depends: [gang]`**, and (c) covers
  `GangAllyWeaponImpactListener.java:38-50` (`hasGang` + `isAlly` + same-gang), so **civilians needs neither
  either**. Consequence worth having: with §2's table as written, a server without the gang jar loses turf, mail,
  gadget, civilians *and* cops-n-crooks (cascade through `Depends:`) — 5 of 8 modules. With S1 it loses turf and
  mail only.
- **S2. Delete `MemberJoinListener`/`PlayerJoinEvent` (step 11, risk 4, one new test) and use the existing
  `UserDataInitEvent`.** It is already fired on **both** account paths — `CreateAccountListener.java:105` (async
  join) and `PlayerBootstrapService.java:113` (boot/reload, for players already online) — and carries the `User`
  object itself, so no `UserLookupContract` round-trip. The plan's `PlayerJoinEvent` listener covers only the join
  path, so **`/glw reload` and a restart with players online would stop creating/attaching `Member` rows**
  (`PlayerBootstrapService.java:118-128`) — an under-engineering bug, not just churn. Bonus: the
  `HandlerList` same-priority ordering invariant, Risk 4 and `MemberJoinListenerTest`'s ordering assertion all
  disappear.
- **S3. Move the filter adapters once, not twice.** §2 has `GangFilterAdapter`/`MemberFilterAdapter`/
  `GangFilterRegistration` moved by WS2 G2 step 7/10 and again here. Ask WS2 to leave them in place; WS5 moves
  them straight into the module.
- **S4. Keep `PlaceholderContribution` as the *single* new api type** (it is genuinely uncovered by any existing
  seam) and drop the second one per S1 — that halves WS5's "WS6 asks" and matches WS6's stated preference for one
  facade pass.

## Missing consumers found by graphify affected

| Type moved | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `Gang`, `GangLookupContract` | cops-n-crooks `TurfFriendlyFireListener` | `cops-n-crooks/.../listener/turf/TurfFriendlyFireListener.java:16-19` | Reactor compile break at G5; missing `Depends:`/pom entry (B1) |
| `Rank`, `RankParent`, `RankRepository` | core `GanglandDatabase.insertInitialData` | `gangland-impl/.../database/GanglandDatabase.java:9-10,136-145` | Core cannot name `Rank`; initial-rank seeding orphaned (B5) |
| `GangBountyEvent` | core `BountyIncreaseListener` | `gangland-impl/.../listener/player/BountyIncreaseListener.java:13,42-53` | Compile break + `NoClassDefFoundError` when module absent (B5) |
| `GangLevelUpEvent` | core `LevelUpListener` | `gangland-impl/.../listener/player/LevelUpListener.java:11,45` | same (B5) |
| `Rank`, `Permission`, `GangSettings` | `User` / `UserManager` / `Level` themselves | `User.java:17,20-21,63-67,229`; `UserManager.java:12-14,124-139`; `Level.java` | The identity/gang cut line is not actually drawn (B2) |
| `MemberManager` | `userManager` + `offlineUserManager` ordering params | `DataConfig.java:78-82,91-99` | Load-order invariant lost / bean unresolvable (B3, B4) |
| `GangItemSourceProvider`, gang filters | core Oriel registries (WS2's adapter) | WS2 §3; `GangItemSourceProvider.java:74-104` | Row-scoped `%member_*%`/`%ally_*%` and 3 `gang_info.yml` slots (C7) |
| `GangPermissionsTest`, `RankAssignmentPolicyTest` + 16 more | `gangland-domain/src/test` | (C3 list) | Deleted with the module at G5 |
| `Settings.gangEnabled` | `@ListenerHandler(condition="isGangEnabled")` in civilians | `GangAllyWeaponImpactListener.java:25`; `SettingsLookupImpl.java:36` | Listener silently never registers (C5) |

Verified-correct claims worth keeping: shared module classloader (`ModuleLoader.java:38-46`), Kahn topological
`Depends:` ordering with `module.dependency.missing` cascaded to a fixpoint (`ModuleResolution.java:108-144`),
module `@Configuration`s registered beside core ones before `instantiate()` (`GanglandContext.java:183-195`), the
module→module `provided` precedent (`gangland-turf/pom.xml:42-46`), table names unchanged / no migration, no FK
crossing the boundary (`User.gangId` is a plain column), `PeriodicalUpdates` free of gang types, `DataCleanupTask`
correctly absent (the census's §4 mention of it is stale — it died with the weapon module), 86 `GANG_`/`RANK_`
`Messages` constants, 37 `GR-` entries with the stated tier split, and the per-module import counts for
turf/mail/gadget/npc-shops/civilians.

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| D1 separate `gang` vs fold turf in | (a) separate | **Agree** | Turf's coupling is `Integer ownerGangId`; folding forces a gang redeploy for every turf change. |
| D2 Wanted/Bounty/Level placement | (a) stay core | **Agree, evidence must be replaced** | Conclusion right (cops' `Wanted` use would otherwise force a new `Depends:`), but the cited grep is wrong — cops *does* import `Gang` (B1). Restate D2 on the honest ground: Wanted/Bounty are per-player consequence state hanging off `User`. |
| D3 `Permission`/`PermissionRegistryContract` stay core | (a) stay core | **Agree** | Its tables already live under `database/*/plugin/` and the registry is fed from Keystone's `PermissionManager`; moving it would give every future module a spurious `Depends: [gang]`. Note GR-26/GR-37 become cross-boundary (the plan already flags this). |
| D4 drop the two bStats charts | (a) drop | **Agree, non-blocking** | Matches the Bartizan-wave deletion of `MetricsContributor`. Verified `Gangland.java:120-126` is the only site. |
| **D5 (new) — is `gang` optional or mandatory?** | not surfaced | **Surface it** | As planned, a gangless server loses 5 of 8 modules and `/glw gang`, and `Gang.Enable` becomes a second, redundant off switch. With S1 it loses 2. The user should choose: "gang is mandatory infra shipped as a module" (accept the cascade, keep the plan) or "gang is genuinely optional" (adopt S1's holder and drop the gadget/civilians `Depends:`). Recommend S1. |
| **D6 (new) — does the WS6 facade expose gangs?** | deferred to WS6, which deferred to WS5 | **WS5 must answer** | See B6. Recommend: facade exposes the core-owned `GangMembership` holder (inert when absent) and drops `gangs()`/`ranks()` object accessors. |

## Estimate check

28 steps / 13 S / 10 M / 5 L is a fair shape, but two numbers are not honest:
- **93h of sequential executor time ≠ "2-3 studio days."** The plan itself says the critical path is
  G0→G1→G2→G3→G4→G5 with poor parallelism; 93h sequential is ~12 working days. Either state the parallel plan or
  state the real wall clock.
- **G0 is undersized at one [L].** 177 files across 9 Maven modules plus poms plus the 18 domain tests plus the
  four `gangland-api` files. It is the whole-reactor rename; call it 2 L.
- Missing steps to add: `gangland-features/pom.xml` `<module>` + root `dependencyManagement` (C8);
  `gangland-api`'s four import rewrites and its `gangland-domain` pom row (B6); `Host_Api`/`GanglandApi.VERSION`
  → 2.0 (C1); the domain test split (C3); the module-side Oriel registry registrations (C7); the settings
  migration warning (C6); docket **write-back** after each gate (C8 of the planner brief requires the artifact DB
  update, §13 only plans a read).

## Things I could not verify

- Whether `PlayerBootstrapService`'s `BeanPostInitialize` contract alone makes the `MemberManager` ordering
  parameter redundant (B4 option (a)) — it needs a real boot with the parameter removed; the javadoc asserts the
  opposite, so the burden is on the change.
- The exact Oriel registry API names a module would call for source/filter/item-provider registration (C7) —
  WS2's territory; its G0 spike should settle it.
- Whether any module can reach a bStats `Metrics` instance (D4) — still unchecked, but "drop" is the right default
  either way.
- Live docket status for the GR ids in the artifact's shared `bugs` collection (not queried this session); §11's
  structural table is otherwise sound.
- `graphify affected "User"` / `"UserManager"` at full depth — I used per-module import greps (counts above)
  rather than the 438-edge traversal; the executor should still run both before G0 as §13 says.

---

# Re-review (after rework)

Plan re-read at 560 lines (`plans/WS5-gang.md`, new §0b). Verdict: **PASS WITH FIXES** — every blocker and
correction from the first pass is addressed, several better than proposed. One **new blocker (N1)** comes from
orchestrator ruling R7 itself and must go back to the orchestrator before G1.

## B1–B6 / C1–C9 status

| Id | Status | Evidence / note |
|---|---|---|
| B1 | **Resolved** | Re-read `TurfFriendlyFireListener.java:102-106`: `isFriendly(int,int)` is `attackerGang.isAlly(ownerGang)` over two ids — `gangsAllied(int,int)` is an exact fit; `UserLookupContract users` stays. No pom dep, no `Depends:`. Confirmed. |
| B2 | **Resolved** | 11/3 split matches `GangSettingsContract.java:11-31` (identity) vs `:33-37` (gang). `RankPermissionApplier` absorbs `User.java:229-241` + `UserManager.java:124-139`. Keep the main-thread hop (`CreateAccountListener.java:110-116`) — §4 step 11 should name it explicitly. |
| B3 | **Resolved** | Holder has no required params, so `BeanFactory.java:517-522` can no longer fire. |
| B4 | **Resolved — stronger than the plan claims** | I verified it rather than deferring: `UserManager.initialize()` (`UserManager.java:86-92`) only wires data suppliers — it does **not** load users; loading is `PlayerBootstrapService`, a `BeanPostInitialize`, and Keystone's `BeanPostInitialize.java:3-21` guarantees it runs after *every* `onInitialize`, while `MemberManager.initialize()` (`MemberManager.java:40-48`, called from `onInitialize` at `:104-106`) does the `loadAll()`. The construction-order parameter is genuinely redundant; `DataConfig.java:69-82`'s javadoc is **stale** (it predates the 2026-04-15 move of player bootstrap to post-init). Keep step 1c's boot check as cheap insurance, but §8 risk 6 can be downgraded and its confused fallback ("a throwaway `GangMembership`-typed param … wouldn't carry real ordering info") deleted. Correct the memory note as part of G0. |
| B5 | **Resolved** | Rank seeding → module `RankManager` init; both listeners split core/module. |
| B6 | **Answered, but the answer creates N1** | See below. |
| C1 | Resolved (`Host_Api: 2.0`) | C2 Resolved (full-reactor G0 gate) |
| C3 | **Resolved** | Per-file destination table at `plans/WS5-gang.md:358-366`. One swap to fix: `:363-364` credits `GangPermissionsTest` to GR-08 and `RankAssignmentPolicyTest` to GR-03; `bugs.json` (and the plan's own §11 `:498-499`) has it the other way round. |
| C4 | Resolved | GR-03/GR-08 marked fixed, pin count 3. |
| C5 | **Resolved, better than proposed** | Deleting `condition = "isGangEnabled"` outright (rather than re-homing the key) is right once the listener is inert-by-default; `SettingsLookupImpl.java:23-37` fails closed, so no orphan key remains. |
| C6 | Resolved | 10 parsed keys (`Settings.java:541-554`) + one-shot copy on first boot. |
| C7 | **Partially** | Correctly escalated to a WS2/Oriel ask, but the registry-injection mechanism is still unnamed and G3 is gated on it. Acceptable only if WS2's G0 spike returns an answer before WS5 G3 — track it as a cross-plan dependency, not a WS5 internal. |
| C8 | Resolved (5 separate steps) | C9 Resolved — and `SchedulingConfig.java:47-57` **is** independently confirmed this pass: the `MemberManager` param belongs to the `playerBootstrapService(...)` bean, not a scheduled task. Drop it from §13. |

## New blocker introduced by the rework

- **N1. R7 as written is a compile cycle: `gangland-api` cannot host `GangLookupContract`/`RankLookupContract`
  while `Gang`/`Rank` live in the module.** Every method of both interfaces names a module type —
  `GangLookupContract.java:13-16` (`@Nullable Gang findById(int)`, `Collection<Gang> getAll()`) and
  `RankLookupContract.java:12-14` (`@Nullable Rank get(int)`, `Rank getRootRank()`). The plan states both halves
  of the contradiction: `plans/WS5-gang.md:102` keeps the two interfaces in `gangland-api`, `:103` ships
  `gang/{Gang,…}` and `rank/{Rank,…}` in the module. `gangland-api` would need a dependency on `gangland-gang`,
  inverting the module→api direction the whole wave rests on.
  Resolution (orchestrator's call, since R7 is a ruling — recommend option b):
  (a) `Gang`/`Rank` **entity types** also stay in `gangland-api`, and only managers/persistence/commands move —
  contradicts §1 and leaves the module owning no domain type;
  (b) **both interfaces move into the module with their types** (the pre-R7 shape): turf and mail already carry
  `Depends: [gang]` + a `provided` pom dep, so nothing is lost, and WS6's facade exposes the core-owned
  `GangMembership` instead of gang objects — which §3 row 3b already provides. Option (b) makes R7's `Optional`
  accessor unnecessary rather than merely lazy;
  (c) keep them in the api but re-typed to id/DTO primitives — that is `GangMembership` again, so it collapses
  into (b).

## Holder shape: are the three methods minimal?

Yes — and it is really **two abstract primitives plus one default**, which is the right shape.
`gangIdOf(UUID)` is required by gadget (`GanglandCarGangs.java:24-33` has UUIDs only); `gangsAllied(int,int)` is
required by cops (`TurfFriendlyFireListener.java:102-106` has ids only, `Turf.ownerGangId` has no player);
`alliedOrSame(UUID,UUID)` is a derived default and carries no new authority, so keeping it costs nothing and saves
the civilians listener (`GangAllyWeaponImpactListener.java:45-50` = `isAlly || sameGangId`) from re-deriving it.
Two semantics to pin, both cheap:
1. **`gangsAllied` must mean `Gang.isAlly` strictly, excluding same-gang** — cops' `isFriendly` currently returns
   `isAlly` only, and the same-gang case takes a different branch at `:69`/`:97`. If `gangsAllied(a,a)` returned
   true, turf friendly-fire behaviour changes silently. Add a unit test pinning `gangsAllied(x,x) == false` and
   `gangsAllied(-1,-1) == false`.
2. The `alliedOrSame` default at `:171` calls `gangIdOf` up to four times; make it read each id once (trivial, but
   it is called per weapon impact).
The plan's own **risk 7** (gadget's `sharesGang` widening from same-gang to same-gang-or-allied, docket GD-06) is
correctly surfaced with a rollback — that is the right call, keep it as a user decision rather than absorbing it.

## Estimate

~138 h / 40 steps / 17–18 executor-days is now honest and consistent with the stated sequential critical path;
the ~45 h delta over the first pass is accounted for by B2's split, B5's three moves and C3's test rehoming.
