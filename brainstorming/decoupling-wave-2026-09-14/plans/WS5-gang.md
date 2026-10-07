# WS5 Plan — the gang system becomes a runtime module, turf builds on it

Graph: refreshed 2026-09-14 (`graphify update . --force`). Rework pass (2026-09-14, after REVIEW-WS5.md verdict
**REWORK**): re-read `User.java`, `UserManager.java`, `GangSettingsContract.java`, `GanglandDatabase.java`,
`BountyIncreaseListener.java`, `LevelUpListener.java`, `TurfFriendlyFireListener.java`,
`GangAllyWeaponImpactListener.java`, `SettingsLookupImpl.java`, `DataConfig.java:60-170`,
`PlayerBootstrapService.java:1-45` in full this pass — every blocker below is checked against the actual file, not
just the reviewer's citation.

User's sentence for this workstream (README.md:11): *"The gang system can be a module by itself, and thus
implemented with turfs."*

---

## 0b. Review response

REVIEW-WS5.md verdict: **REWORK**, 6 blockers / 9 corrections / 4 simplifications / 2 new decisions. Orchestrator
rulings adopted: **R7** (refined, `PLAN.md:51`) and **R9** (`PLAN.md:53`). Every id below maps to what changed;
"§n" = the section of *this* rewritten plan.

| Id | What it said | What changed | Where |
|---|---|---|---|
| B1 | cops-n-crooks imports `Gang`/`GangLookupContract` via `TurfFriendlyFireListener.java:16-19`; D2's grep evidence was wrong | Re-read the file in full: `isFriendly(int,int)` only needs an **ally check between two gang ids** — rewritten onto the new `GangMembership` holder (`gangsAllied(int,int)`); `UserLookupContract` stays (already core-safe). Cops-n-crooks now needs **zero** new pom dep or `Depends:` entry. D2's conclusion stands, evidence replaced. | §2 table, §3 row 3, §9 D2 |
| B2 | `User`/`UserManager`/`Level` name `GangSettings`/`Rank`/`Permission`/`Member` in their own bodies | `GangSettingsContract` split 11/3 (confirmed: exactly 14 getters, `isAutoSave/getUserMaxLevel/BaseAmount/Formula` + 4×bounty + 3×wanted = 11 identity, 3×gang = 3); `User.flushPermissions(Rank)` and `UserManager.initializeUserPermission(User,Member)` both move into a module-side `RankPermissionApplier`, exposed to core `User` only through new narrow, `Rank`-free primitives | §2, §3 rows 5b/8, §4 steps 1b/11 |
| B3 | Missing contract bean aborts boot (`BeanFactory.java:517-522`) | `MembershipLookupContract` deleted; replaced by core-owned `GangMembership` holder (R9), always registered, no required params | §3 row 6, §4 step 9b |
| B4 | `MemberManager`→`UserManager` ordering edge is real (`DataConfig.java:69-82`, confirmed read) | Documented mechanism: `PlayerBootstrapService`'s `BeanPostInitialize` phase boundary (confirmed javadoc, `PlayerBootstrapService.java:30-34`) is what actually matters, not the construction-order parameter — `GangMembership`'s lazy read-on-use means no construction-time snapshot is needed anywhere. Parameter deletion is gated on a **real boot proof**, not asserted | §3 row 6b, §4 step 1c, §8 risk 6 |
| B5 | `GanglandDatabase.java:136-145` (rank seeding), `BountyIncreaseListener`, `LevelUpListener` all name module types, missed | Confirmed all three by reading them in full. Rank seeding moves into the module's own `RankManager`/`RankRepository` init. Both listeners **split**: the `User*`/`UserLevelUpEvent`-handling method stays core, the `Gang*Event`-handling method moves into a new module-side listener class — Bounty/Level themselves stay core | §3 rows 8b/8c, §4 steps 15b/15c/16b |
| B6 | WS5 never answered WS6's parked `gang()`/`ranks()` facade question | Answered directly per R7: `GangLookupContract`/`RankLookupContract` **interfaces** stay in `gangland-api` (never name a module class), implementations ship in the module, WS6's facade resolves `Optional<...>` lazily per call — no inert-default object for these two; `GangMembership` is the inert-default holder for the narrower is/which-gang facts. `gangland-api`'s own 4 files that import `User`/`Level`/`UserLevelUpEvent` (confirmed: `WaypointTeleport.java`, `TeleportEvent.java`, `UserLevelUpEvent.java`, `MoneyAspect.java`) keep `gangland-api` on a compile dependency on whichever artifact carries the identity slice (`gangland-core`) | §3 row 3b, §10 |
| C1 | `Host_Api: 1.0` violates R1 (api bumps to 2.0 at G0 of `0.10.0`) | Module descriptor now `Host_Api: 2.0` | §2 |
| C2 | G0 gate `-pl gangland-core,gangland-domain,gangland-impl -am` can't be green — 177 files across 9 Maven modules | G0 gate is now a **full-reactor** `mvn clean install` + `mvn test` | §4 G0 gate |
| C3 | `gangland-domain`'s 18 test files have no destination | Named home per file: split table added | §7 |
| C4 | §11 wrong on GR-03/GR-08 (already fixed 0.8.3) and pin count (3, not 6) | Both rows corrected; pin count corrected to **3** (GR-12, GR-13, GR-35 — GR-09 is a comment, not a pin) | §11 |
| C5 | `condition = "isGangEnabled"` silently unregisters `GangAllyWeaponImpactListener` once `Gang:` leaves core `Settings` | Confirmed via `SettingsLookupImpl.java` (fails closed on unknown key). Fix: **delete the condition** from `GangAllyWeaponImpactListener` — the B1/S1 rewrite onto `GangMembership` makes the class safe unconditionally (inert when gang absent), so the redundant switch is removed, not patched. `GangCommand`/`GangMembersDamageListener` move into the module where module-presence is already the switch; their `condition` is dropped too (module-internal soft-disable, if wanted later, is a manual flag check, not this attribute — not built this wave) | §4 step 20b, §5, §9 D5 |
| C6 | Config migration loses `Create_Cost`/`Maximum_Balance`/etc. silently; block size wrong | Confirmed via `Settings.java:541-554`: **10 parsed keys**, not "29 + 15 more" (29 was the YAML line count including comments/blank lines). Added a one-shot copy-on-first-boot mechanism | §5 |
| C7 | WS2 hand-off bigger than a YAML drop — `GangItemSourceProvider`'s row-scoped tokens + `ItemProviderRegistry` sources | Named as an explicit "Oriel/WS2 ask": the registry-injection mechanism by which a module registers into core's Oriel source/filter/item-provider registries | §3 row 13b, §10 |
| C8 | `gangland-build` is 3 edits not 1; missing `gangland-features/pom.xml` + root `dependencyManagement` | All 5 touch points now separate steps | §4 steps 27a-27e |
| C9 | 3 "not verified" items are answered: `SchedulingConfig`'s param, `WaypointCommand`'s `GangManager`, `initializeUserPermission`'s body | Folded in (confirmed `SchedulingConfig.java:47-57` feeds `playerBootstrapService(...)`, not a scheduled task itself — not re-verified this pass, carried from reviewer; `initializeUserPermission` body now fully quoted, §3 row 8) | §13 (trimmed) |
| S1 | Replace `MembershipLookupContract` with `GangMembership` holder (`BankTiers` shape) — kills 2 `Depends:` edges | Adopted as R9, **extended by one primitive** beyond the reviewer's sketch: `gangsAllied(int,int)` in addition to `gangIdOf(UUID)`/`alliedOrSame(UUID,UUID)`, because `TurfFriendlyFireListener.isFriendly(int,int)` (confirmed read) operates on gang **ids**, not player UUIDs — `Turf.ownerGangId` has no associated player. Justified in §3 row 6 | §2, §3 row 6, §4 |
| S2 | Use `UserDataInitEvent` (fires on both join and reload) instead of a new `PlayerJoinEvent` listener | Adopted verbatim. `MemberJoinListener`/Risk 4/its ordering test all deleted | §3 row 7, §4 step 11, §7, §8 |
| S3 | Move `GangFilterAdapter`/`MemberFilterAdapter`/`GangFilterRegistration` once, not twice — ask WS2 to leave them in `gangland-domain` | Adopted; §2/§4 no longer show a WS2-then-WS5 double move | §2, §4 |
| S4 | Keep `PlaceholderContribution` as the only new api type | Adopted; `MembershipLookupContract` is gone (replaced by `GangMembership`, a holder not an api interface consumers implement independently) | §3, §10 |
| D5 (new) | Is `gang` optional or mandatory? | Surfaced explicitly, recommend optional (the S1 world: 2 of 8 modules lost, not 5) | §9 D5 |
| D6 (new) | Does the WS6 facade expose gangs? | Answered: `Optional` accessors per R7, no `gangs()`/`ranks()` object return | §9 D6 |

---

## 0c. Orchestrator amendment — final ruling R7 (2026-09-14 22:40, supersedes every R7 mention below)

REVIEW-WS5's re-review (N1) showed that `GangLookupContract.java:13-16` and `RankLookupContract.java:12-14` name
`Gang`/`Rank` in every signature, so keeping those interfaces in `gangland-api` while the entities ship in the gang
module would invert the module→api direction. Final shape, binding for WS5 and WS6 executors:

- `GangLookupContract` and `RankLookupContract` **move into `gangland-gang`** together with `Gang`/`Rank`. They are
  not in `gangland-api`. turf and mail reach them through `Depends: [gang]` + the `provided` pom (turf→civilians pattern).
- The api exposes gang facts **only** through the core-owned `GangMembership` holder (R9): `int gangIdOf(UUID)`,
  `boolean gangsAllied(int, int)` — **strict**: allied, never same-gang, pinned by a unit test so cops' turf
  friendly-fire keeps today's semantics — and the derived default `alliedOrSame(UUID, UUID)`. Inert until the gang
  module's `@PostConstruct` installs the real view.
- WS6's facade: `GangMembership gangs()` (never null); **no `ranks()` accessor**; the `Optional` lookups are removed.
  The facade has four accessors: `users()`, `gangs()`, `waypoints()`, `bankTiers()`.
- Everything else in this plan stands; where a section says "interface stays in the api" for the two contracts,
  read this block instead.

## 1. Scope

**In**
- Extract `org.luckyraven.gangland.gang.*` (40 files, `gangland-infra/gangland-domain`) into a new runtime module
  `gangland-features/gangland-gang` (id `gang`).
- Repackage the player-identity slice (`User`, `UserManager`, `Level`, `UserFactory`, `Bounty*`, `Wanted*`,
  `Permission*`) out of `gang.*` into core (`gangland-core`) — **and** split the two classes that mix identity and
  gang logic in their own bodies (`GangSettingsContract`, `User.flushPermissions`,
  `UserManager.initializeUserPermission`, per B2).
- One core-owned **`GangMembership` holder** (R9, `BankTiers`/`BankTierView` shape) replaces the narrower
  `MembershipLookupContract` from the previous pass and serves every "is/which gang" site in core **and** in
  gadget/civilians/cops-n-crooks, so none of those three need `Depends: [gang]` (S1).
- Delete `gangland-domain` once its only package is empty; give its 18 tests a new home (C3).
- Re-point `turf` (`Depends: [gang, civilians]`) and `mail` (`Depends: [gang]`) — the only two modules that need a
  real `Gang`/`Member`/`Rank` object graph, not just an is/which-gang fact.
- Move the 31 gang/rank commands, the gang listener, gang/rank/member persistence, the gang-prefixed `Messages`/
  `Settings` block (10 parsed keys, C6), the gang placeholders, and the 5 gang-flavoured Oriel menus into the module.
- `Host_Api: 2.0` (R1/C1) — G0 of the `0.10.0` branch already bumped `GanglandApi.VERSION`; the gang module is
  created at 2.0 from the start, not 1.0-then-bumped.
- Turf keeps `Integer ownerGangId` — zero signature change to `Turf.java`.

**Out**
- Turf folding into the gang module (§9 D1, unchanged from the previous pass).
- Moving `Wanted`/`Bounty` into `cops-n-crooks` (§9 D2 — conclusion unchanged, **evidence replaced**: the old
  "cops has zero `Gang` imports" grep was wrong (B1); the real reason to keep Wanted/Bounty core is that they are
  per-player consequence state constructed **inside `User`'s own constructor** (`User.java:63-67`), not that cops
  avoids a `Gang` import).
- Fixing docket bugs as part of the move.
- Building a Keystone-level "multiple contributors to `insertInitialData`" seam — the rank-seeding move (B5) uses
  the module's own `BeanLifecycle`/manager-init instead, no new Keystone mechanism.
- A module-side soft-disable flag equivalent to today's `Gang.Enable` — module presence *is* the switch this wave
  (§9 D5); a runtime on/off flag inside the module is a named follow-up, not built here.

**Reason for the split shape (unchanged):** `gangland-domain`'s source tree is **100% `gang/`** (`find
gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland -maxdepth 1 -mindepth 1 -type d` returns one
directory). G5 deletes it outright.

**What B2 changes about "repackage, import-line only":** it doesn't hold at the source level. `User.java:17,20-21`
imports `GangSettings`, `rank.Permission`, `rank.Rank`; `:63-67` builds `Bounty`/`Wanted` from 7 `GangSettings`
static getters; `:229-241` (confirmed, `flushPermissions`) takes a `@Nullable Rank` parameter directly.
`UserManager.java:12-14` imports `Member`, `Rank`, `Permission`; `:124-139` (confirmed, `initializeUserPermission`)
takes `Member` and reads `member.getRank()`/`rank.getPermissions()`. `Level.java` also reads `GangSettings`. None
of this is "just an import line" — it's four call sites of real gang-shaped logic sitting on core-owned classes.
§2/§3/§4 below carry the actual split.

---

## 2. Target layout

### Before → after (Maven reactor)

| Module | Before | After |
|---|---|---|
| `gangland-infra/gangland-domain` | 40 files, all `gang.*`, 18 test files | **deleted** — 18 tests re-homed per §7's split table |
| `gangland-core` | downed-player trio + `feature.Executor` | **+** `user/{User,UserManager,Level,UserFactory}`, `bounty/*` (4), `wanted/*` (6), `permission/Permission*` (2), `events/{bounty,level,user,wanted}` (7) — all repackaged from `gang.*` |
| `gangland-api` | `GanglandApi.VERSION = "1.0"`; re-exports `gangland-domain` | `VERSION = "2.0"` (R1); domain re-export **replaced by** a compile dep on `gangland-core` (its own 4 files — `WaypointTeleport.java`, `TeleportEvent.java`, `UserLevelUpEvent.java`, `MoneyAspect.java` — import `User`/`Level`/`UserLevelUpEvent`, confirmed by B6); **adds** `contract.PlaceholderContribution` (new, S4) and `data.gang.{GangMembership,GangMembershipView}` (new holder, R9); `GangLookupContract`/`RankLookupContract`/`UserLookupContract`/`PermissionRegistryContract` **stay** (interfaces only, R7) |
| `gangland-features/gangland-gang` (**new**) | — | `gang/{Gang,GangAlliance,GangManager}`, `GangSettings`(3-getter split, B2)`, contract/` **implementations** of `GangLookupContract`/`RankLookupContract`/`GangAllianceRepositoryContract`/`MemberRepositoryContract`/`GangMessageContract`/`GangPermissionBridgeContract` (interfaces stay in api per R7, only 2 of the 7 — `GangLookupContract`/`RankLookupContract` — have that split; the other 5 have no reason to live in the api since nothing outside the module consumes them, so they move wholesale, interface and all), `member/*` (3), `rank/{Rank,RankManager,RankParent,RankAssignmentPolicy,RankPermission}`, `permission/GangPermissions`, `vault/permission/VaultPermissionBridge`; from `gangland-impl`: 31 gang/rank commands, `listener/gang/GangMembersDamageListener`, `GangFilterAdapter`/`MemberFilterAdapter`/`GangFilterRegistration` (moved **once** here, per S3 — WS2 asked to leave them in `gangland-domain`), `GangItemSourceProvider`, gang/rank persistence (7 tables+repos incl. `MemberRepository`/`MemberTable`), `events/gang/{GangBountyEvent,GangLevelUpEvent}`, `GangPlaceholderContribution`, `RankPermissionApplier` (new, B2 — houses the moved `flushPermissions`/`initializeUserPermission` logic), rank-seeding logic (moved out of `GanglandDatabase`, B5), `GangBountyMessageListener`/`GangLevelMessageListener` (new, B5 split), a `GangMembershipInstaller` (`@PostConstruct`, R9), 5 Oriel menus |
| `gangland-impl` | owns gang wiring directly | keeps only identity-side beans; `GangModuleConfig` deleted, replaced by a small `IdentityContractConfig` (4 beans: `userLookupContract`, `permissionRegistryContract`, `gangMembership` holder, unchanged `GangLookupContract`/`RankLookupContract` are **not** published from core anymore — R7 says their impls live module-side, so core publishes nothing for those two, it only compiles against the interface where needed); `GanglandDatabase.insertInitialData()` loses its rank-seeding body (B5); `BountyIncreaseListener`/`LevelUpListener` lose their `Gang*Event` handler methods (B5) |

### `module.yml` (C1)
```yaml
Id: gang
Name: Gangland Gangs
Version: ${project.version}
Main: org.luckyraven.gangland.gang.GangModule
Host_Api: 2.0
Artifact: org.luckyraven:gangland-gang
```
No `Depends:`/`Plugins:`.

### `Depends:` additions — **revised down from the previous pass (S1/B1)**

| Module | module.yml today | module.yml after WS5 | Why |
|---|---|---|---|
| `turf` | `Depends: [civilians]` | `Depends: [gang, civilians]` | 36 files need real `Gang` objects (`gang.getEconomy()`, `getMembers()`, `getName()`) — census §10, unchanged |
| `mail` | none | `Depends: [gang]` | `GangMailContribution`/`GangAllyMailContribution` use `GangManager, MemberManager, User, Gang, RankManager, GangPermissions` directly — unchanged |
| `gadget` | `Plugins: [Bartizan]` | **unchanged — no `Depends: [gang]`** (was added in the previous pass, now reverted per S1) | `GanglandCarGangs.java:24-33` rewrites onto `GangMembership.alliedOrSame(UUID,UUID)` — a `UUID,UUID → boolean` call over the core-owned holder, zero module types named |
| `civilians` | `Plugins: [Bartizan]` | **unchanged — no `Depends: [gang]`** (reverted, S1) | `GangAllyWeaponImpactListener.java` rewrites onto `GangMembership.alliedOrSame(UUID,UUID)` (confirmed: today's body is `gang1.isAlly(gang2) || sameGangId` — an exact match for the holder's contract) |
| `cops-n-crooks` | `Depends: [turf, civilians]` | **unchanged — no `Depends: [gang]`** (B1 correction) | `TurfFriendlyFireListener.java:16-19` (confirmed read) rewrites `isFriendly(int attackerGangId, int ownerGangId)` onto `GangMembership.gangsAllied(int,int)`; `UserLookupContract` (unchanged, stays api) still resolves the `User` for `user.getGangId()` |
| `npc-shops` | none | unchanged | still zero `Gang`/`Member` imports |

**New blast radius statement (replaces the previous pass's "5 of 8 modules"):** a server without the `gang` jar
loses **`/glw gang`/`/glw rank` and the two modules that directly declare `Depends: [gang]` — `turf` and `mail`**.
`cops-n-crooks` is lost **transitively** (its own `Depends: [turf, civilians]` fails once `turf` is unloaded) —
not because it names `gang` itself. `gadget`, `civilians`, `npc-shops` are **unaffected**: their gang-touching code
now runs entirely against the always-present `GangMembership` holder, which degrades to `-1`/`false` when the
module is absent, exactly like `BankTiers` degrades today when cops-n-crooks is absent.

### `gangland-build`/root pom edits (C8 — 5 touch points, was 1)
1. `gangland-build/pom.xml` shade `<exclude>` list (`:82-87` per the review) gains `gangland-gang`.
2. `gangland-build/pom.xml` `target/modules/` copy set (`:123-148`) gains `gangland-gang`.
3. `gangland-build/pom.xml` `<dependency>` block (`:170-200`) gains `gangland-gang`.
4. `gangland-features/pom.xml` gains `<module>gangland-gang</module>`.
5. Root `pom.xml` `<dependencyManagement>` gains the `gangland-gang` artifact/version entry.

### Deletions
- `gangland-infra/gangland-domain/` (whole module — pom, dir, `<module>` line in `gangland-infra/pom.xml`) — its
  18 tests re-homed first (§7), not deleted with it.
- `gangland-impl/.../config/GangModuleConfig.java` — replaced by `IdentityContractConfig` (4 beans, above).
- `gangland-impl/.../file/configuration/gang/{GanglandGangSettings,GanglandGangMessages,
  GanglandGangPermissionBridge,GanglandRankLookup}.java` — the module owns its own YAML directly, no bridge needed.
  `GanglandUserLookup`/`GanglandPermissionRegistry` **stay** (core still publishes these two, R7/§3 row 1-2).
- `User.flushPermissions(Rank)` (B2) — logic moves to `RankPermissionApplier` (module); `User` keeps only the
  `Rank`-free primitives that method needs (§3 row 8).
- `UserManager.initializeUserPermission(User,Member)` (B2) — same destination.
- `GanglandDatabase.insertInitialData()`'s rank-seeding body (B5) — moves to the module's own init.
- `BountyIncreaseListener.onGangBountyIncrease`/`LevelUpListener.onGangLevelUp` (B5) — split into new module-side
  listener classes; the `User*`/level-up methods stay in the original core classes.

---

## 3. Seams

Ordering guarantee, restated after B4's correction: `BeanGraph`'s parameter-edge topological sort still orders
core+module beans correctly for every **direct** dependency (`memberManager(gangLookup, rankLookup)` — real,
consumed parameters). The one **ordering-only** trick (`MemberManager orderingDep` on `UserManager`,
`DataConfig.java:69-82`, confirmed read) cannot cross the module boundary as a compile-time parameter any more —
row 6b below is the replacement mechanism, not a hand-wave.

| # | Cross-boundary call | Mechanism | Publisher | Puller | Default when absent |
|---|---|---|---|---|---|
| 1 | Core needs the current user record | `UserLookupContract` — **stays in `gangland-api`** (R7, unchanged from previous pass) | core (`IdentityContractConfig.userLookupContract`) | gang module, mail, turf | n/a — core always publishes |
| 2 | Rank assignment needs a catalogue of known permission strings | `PermissionRegistryContract` — **stays core** (§9 D3, unchanged) | core (`IdentityContractConfig.permissionRegistryContract`) | gang module's `RankManager` | n/a |
| 3 | mail/turf resolve a real `Gang`/`Rank` object | `GangLookupContract`/`RankLookupContract` **interfaces stay in `gangland-api`** (R7 — "the api can never name a module class" is satisfied because these two interfaces' method signatures return `Gang`/`Rank`, and `Gang`/`Rank` **do** live in the api-adjacent... — correction: they do **not**; `Gang`/`Rank` are module types. R7's actual mechanism (confirmed against `PLAN.md:51`) is that the **interfaces** are declared in the api with their return types resolved as module types **at the api's compile scope only if the api takes a `provided` (not `compile`) dependency on the module artifact — the same shape modules already use for cross-module deps.** This is the one place the api leans on the established module→module `provided` pattern instead of shipping the impl. Mail/turf, which already declare `gangland-gang` `provided`, see the same interface+impl either way. | gang module | mail, turf | consumers already null-check `@Nullable Gang findById` |
| 3b | gadget/civilians/cops-n-crooks need only an is/which-gang **fact**, not a `Gang` object | **new `GangMembership` holder** (R9, S1) in `gangland-api`, `org.luckyraven.gangland.data.gang` package (siblings `BankTiers`/`data.economy`): `int gangIdOf(UUID uuid)` (-1 = none/absent), `boolean gangsAllied(int gangIdA, int gangIdB)` (false = not allied/absent, **added beyond the reviewer's 2-method sketch** because `TurfFriendlyFireListener.isFriendly(int,int)` (confirmed read, `TurfFriendlyFireListener.java`) operates on gang **ids** — `Turf.ownerGangId` has no player UUID to hand `alliedOrSame`), `boolean alliedOrSame(UUID a, UUID b)` (default method: `gangIdOf(a) == gangIdOf(b) && gangIdOf(a) != -1 \|\| gangsAllied(gangIdOf(a), gangIdOf(b))`), `void install(GangMembershipView view)` | gang module (`GangMembershipInstaller`, `@PostConstruct`) | core `UserDataLoader`; gadget `GanglandCarGangs`; civilians `GangAllyWeaponImpactListener`; cops-n-crooks `TurfFriendlyFireListener` | every method above states its inert default inline; matches `BankTiers.tierFor`'s null-safe pattern exactly |
| 4 | Member persistence needs gang/rank facts at hydrate time | `MemberRepositoryContract`/`GangAllianceRepositoryContract` (module-internal after the move — no core/other-module consumer, so interface **and** impl both move) | gang module | gang module internals | n/a |
| 5 | Gang's own settings/messages/permission-bridge | `GangMessageContract`/`GangPermissionBridgeContract` move wholesale (no split needed — every getter is gang-specific); `GangSettingsContract` bridges (`GanglandGangSettings` etc.) deleted, module implements directly against its own YAML | gang module | gang module internals | n/a |
| 5b | **new** — identity's own settings need (B2) | `GangSettingsContract` **splits**: an 11-getter `IdentitySettingsContract` (`isAutoSave, getUserMaxLevel/BaseAmount/Formula`, 4×bounty, 3×wanted — confirmed exact list, `GangSettingsContract.java`) stays in `gangland-api`, implemented by core against `settings.yml`'s `User:`/`Bounty:`/`Wanted:` blocks (unchanged location); the 3-getter remainder (`getGangDisplayNameChar/getGangRankHead/getGangRankTail`) stays named `GangSettingsContract`, moves into the module, implemented against the module's own YAML. The static facade class `GangSettings` **splits the same way**: a core `IdentitySettings` static facade (bound from `IdentityContractConfig`) and a module-owned `GangSettings` (bound from the module's own config) — `User`'s constructor (`User.java:63-67`) calls `IdentitySettings.getBountyEachKillValue()` etc. instead of `GangSettings.*` | core (`IdentitySettings`) + gang module (`GangSettings`) | `User`/`Level` (identity side); `Gang`/`RankManager` (gang side) | n/a — both facades always bound at their own bootstrap |
| 6 | Post-hydrate: `User.gangId` needs the member's gang id | `UserDataLoader`'s `MemberManager memberManager` ctor param → `GangMembership gangMembership` (always-present holder, row 3b); `user.setGangId(gangMembership.gangIdOf(user.getUuid()))` | gang module (installs the view) | core `UserDataLoader` | `gangIdOf` returns `-1` when absent — `user.getGangId()` stays at its default, matching every other optional-module degrade in this codebase |
| 6b | **B4 — the real ordering question**: member data must be ready before `PlayerBootstrapService` attaches `gangId`/permissions at boot/reload | **No parameter trick survives the boundary.** Mechanism: `PlayerBootstrapService implements BeanPostInitialize`, confirmed javadoc (`PlayerBootstrapService.java:30-34`): *"runs ... after every `BeanLifecycle.onInitialize(...)` call has completed."* If the module's `MemberManager` populates its cache during its own `onInitialize()` (a `BeanLifecycle` phase, strictly earlier than `BeanPostInitialize`), the ordering holds with **zero** cross-boundary parameter — a phase boundary, not a `BeanGraph` edge, is what was actually load-bearing. Everywhere else that reads gang facts at runtime (row 3b, row 6) reads them **lazily through the holder**, which has no construction-time ordering requirement at all (the `volatile` field is read fresh on every call). | gang module (`MemberManager.onInitialize`, if it implements `BeanLifecycle` — **not verified this pass**, §13) | core `PlayerBootstrapService` | **Required G0/G1 gate step, not assumed**: boot with the `MemberManager orderingDep` parameter deleted from `DataConfig.java:77-82,90-98` and confirm members still attach `gangId` correctly on first load, reload, and a fresh empty DB. If it fails, fall back: give the module's `MemberManager` bean an explicit `@PostConstruct`/`BeanLifecycle` ordering relative to `RepositoryRegistry`'s own data-load completion (Keystone-side, not a new cross-artifact parameter) |
| 7 | Player join / reload: create/attach the `Member` row, apply rank permissions | **S2 — `UserDataInitEvent`, not a new `PlayerJoinEvent` listener.** Confirmed: this event already fires on **both** account paths — `CreateAccountListener.java:105` (async join) and `PlayerBootstrapService.java:113` (boot/reload, players already online) — and it carries the `User` object itself, no `UserLookupContract` round-trip needed. A new module-side `MemberJoinListener` (`@EventHandler` on `UserDataInitEvent`) creates/loads the `Member`, calls `RankPermissionApplier.initialize(user, member)` (row 8). `CreateAccountListener`/`PlayerBootstrapService` (core) are trimmed to pure `User` creation — all `Member`/`MemberManager` code deleted from both | gang module | none — internal to the module | absent module → no `Member` row is ever created; commands already null-check (GR-01) |
| 8 | `User.flushPermissions(Rank)` / `UserManager.initializeUserPermission(User,Member)` (B2, confirmed bodies) | Both move into a module-side `RankPermissionApplier` with two methods mirroring the two entry points: `initialize(User<Player>, Member)` (fresh `PermissionAttachment` via `user.getUser().addAttachment(...)`) and `flush(User<Player>, @Nullable Rank)` (re-apply after promote/demote/transfer, called from `GangPromoteCommand.java:179`/`GangDemoteCommand.java:147`/`GangTransferCommand.java:179,188`/`Gang.java:137`, all already moving into the module). Both operate through **narrow, `Rank`-free primitives added to core `User`**: `List<String> grantedPermissionNames()`, `void setPermission(String, boolean)`, `void updateCommands()` — mechanical widening of `User`'s public surface, not a new abstraction (`User` already exposes `setPermissionAttachment`, per `UserManager.java:130`). `CreateAccountListener.java:110-116`'s existing `Bukkit.getScheduler().runTask(...)` main-thread hop (confirmed necessary — `PermissionAttachment`/`updateCommands()` are main-thread-only) is preserved verbatim in the module's call site | gang module | gang module internals (called from row 7's listener + the 4 command sites) | n/a — **not verified**: exact primitive method names to add to `User` (§13) |
| 8b | `GanglandDatabase.insertInitialData()` seeds the head/tail ranks (B5, confirmed `GanglandDatabase.java:134-146`) | Delete the rank-seeding body from core's `insertInitialData()` (it becomes a no-op or is removed entirely if nothing else seeds there — **not verified**, §13). The module's own `RankRepository`/`RankManager` does the equivalent "if empty, insert head/tail" check in its own init, reading `head`/`tail` from the module's own `GangSettingsContract` (row 5b) instead of core `Settings.getGangRankHead/Tail()` | gang module | n/a — self-contained | if the module never loads, no ranks ever exist, which is already true today whenever `Rank`/`RankManager` aren't reachable |
| 8c | `BountyIncreaseListener.onGangBountyIncrease`/`LevelUpListener.onGangLevelUp` (B5, confirmed bodies) | Both methods (which read `Gang`/`gang.getOnlineMembers(...)`) move into two new module-side listener classes (`GangBountyMessageListener`, `GangLevelMessageListener`), each with its own `@Qualifier("online") UserManager<Player>` (core type, fine) constructor param. The `onUserBountyIncrease`/`onPlayerLevelUp` methods (already `User`/`Bounty`/`Level`-only) stay in the original core classes untouched | gang module (2 new classes) | none | absent module → the two new listener classes are never scanned (module never loaded), so no `NoClassDefFoundError` risk at all — cleaner than a `condition=` guard |
| 9 | Gang/rank facts in PAPI placeholders | `PlaceholderContribution { @Nullable String resolve(OfflinePlayer player, String param); }` — **only new api type per S4** (`MembershipLookupContract` from the previous pass is gone). Core's `GanglandPlaceholder` keeps balance/kills/deaths/level/bounty/wanted; delegates `gang_*` and the member-touching `user_*` sub-keys to `container.getAllInstances(PlaceholderContribution.class)` | gang module (`GangPlaceholderContribution`) | core `GanglandPlaceholder` | unresolved param → `null` |
| 10 | `/glw waypoint gangid`, gang/rank branches of `/glw debug`/`/glw option` | Existing `CommandContribution` seam, unchanged from the previous pass | gang module | core `WaypointCommand`/`DebugCommand` | contribution absent → branch doesn't appear |
| 11 | Vault group provider lifecycle | `VaultPermissionBridge.set(...)`/`.isEnabled()` calls move from `Gangland.java:71-72,204` into the module's own `GangModule.configure()`/enable/disable | gang module | n/a | absent module → no Vault group bridge registered |
| 12 | bStats `number_of_gangs`/`number_of_ranks` charts | Dropped (§9 D4, unchanged) | — | — | — |
| 13 | Oriel menus (5 files) | Unchanged from the previous pass: module ships them in its jar, extracted into the shared `plugins/Gangland_Warfare/inventory/` folder during FILE phase, before core's `OrielMenuConfig` scans it | gang module | Oriel's `MenuRegistry` | unregistered menu, same degrade as WS2's own |
| 13b | **C7 — the WS2 hand-off is bigger than the YAML drop.** `GangItemSourceProvider.java:74-104` (WS2's own citation) builds row-scoped `%member_*%`/`%ally_*%` tokens and registers `gang_info.yml`'s slots through Oriel's `ItemProviderRegistry`/`FilterRegistry`/source registry — all of which become module-owned calls once `GangItemSourceProvider` moves | gang module registers into core's already-wired Oriel registries via the **existing registry-injection family** (documentation/module-loader.md), the exact mechanism named is **still open** — this is an ask **of WS2**, not a new WS5 abstraction (§10) | core (registries already exist per WS2) | gang module | if the registration call shape isn't settled by WS2's G0, this step blocks — flagged |
| 14 | `phone.yml`'s "open gang menu" button when the module is absent | Oriel's `condition:` block gated on a placeholder resolved via row 9's `PlaceholderContribution` — unchanged mechanism | gang module (via row 9) | core `phone.yml` | button hidden, not dead |

---

## 4. Steps

Order per contract C1, unchanged. Every gate ends in a green build + a named smoke row (§7). **G0's gate is now a
full-reactor build (C2)**, not a 3-module `-pl` build.

### G0 — Repackage identity out of `gang.*` (core only, no module yet)
1. **[L]** Move `gang/user/{User,UserManager,Level,UserFactory}.java` → `gangland-core/.../user/`. Update every
   importer's `import` line (438+161 edges).
1b. **[L]** (**new, B2**) Split `GangSettingsContract` → `IdentitySettingsContract` (11 getters, api) +
   `GangSettingsContract` (3 getters, stays named, moves to module in G1). Split the `GangSettings` static facade
   the same way (`IdentitySettings` core / `GangSettings` module). Rewrite `User.java:63-67`'s constructor calls.
   Delete `User.flushPermissions(Rank)` (`:229-241`) and `UserManager.initializeUserPermission` (`:124-139`) —
   **do not move their bodies yet**, that's G2 step 11b once the module exists to receive them; this step only
   removes them from the identity slice and adds the narrow `User` primitives (§3 row 8) as stubs.
1c. **[M]** (**new, B4**) Delete the `MemberManager orderingDep` parameter from `DataConfig.java:77-82,90-98`
   (cannot compile once `MemberManager` is a module type regardless of whether the ordering proof (§3 row 6b)
   succeeds — if it fails, the fallback in row 6b is a **Keystone-side** fix, not a parameter re-add here).
2. **[M]** Move `gang/bounty/*` (4) → `gangland-core/.../bounty/`, `gang/wanted/*` (6) → `.../wanted/`,
   `gang/events/{bounty,level,user,wanted}/*` (7) → `.../events/{bounty,level,user,wanted}/`.
3. **[M]** Move `gang/rank/Permission.java` → `gangland-core/.../permission/Permission.java` (§9 D3); repackage
   `PermissionTable`/`PermissionRepository` imports only (location unchanged).
4. **[S]** `gangland-api/pom.xml`: replace the `gangland-domain` re-export with a `gangland-core` compile dep
   (B6 — 4 files: `WaypointTeleport.java`, `TeleportEvent.java`, `UserLevelUpEvent.java`, `MoneyAspect.java`).
   **Gate:** `mvn clean install` + `mvn test` on the **full reactor** (C2 — 177 files across 9 Maven modules per
   the review's grep: impl 124, turf 12, mail 12, gadget 7, domain 6, npc-shops 6, cops 4, api 4, civilians 2);
   smoke row **G0**: server boots on `0.10.0`, `/glw waypoint`, `/glw bank`, `/glw bounty`, `/glw wanted`
   unaffected; **explicit sub-check for step 1c**: members still attach `gangId` correctly across first load,
   `/glw reload`, and a fresh empty DB (§3 row 6b's required proof).

### G1 — Module skeleton + persistence move (C7)
5. **[M]** Create `gangland-features/gangland-gang` (pom, `module.yml` §2 — `Host_Api: 2.0`, `Main: GangModule`).
6. **[L]** Move `gang/{Gang,GangAlliance,GangManager}.java`, the module-owned half of `GangSettings`/
   `GangSettingsContract` (step 1b), `contract/` (the 5 wholly-module contracts + the 2 R7 interfaces' impls —
   §2 table), `member/*` (3), `rank/{Rank,RankManager,RankParent,RankAssignmentPolicy,RankPermission}.java`,
   `permission/GangPermissions.java`, `vault/permission/VaultPermissionBridge.java`.
7. **[L]** Move persistence: `database/repositories/{gang(2),rank(3)}` + `database/tables/{gang(2),rank(3)}` +
   `database/repositories/player/MemberRepository.java` + `database/tables/player/MemberTable.java` into the
   module's own `database` package. Table names unchanged, no migration (C7).
7b. **[S]** (**new, B5**) Add the module's own rank-seeding check to `RankManager`/`RankRepository` init (§3 row
   8b); delete the corresponding body from `GanglandDatabase.insertInitialData()` (`:134-146`).
8. **[S]** `RepositoryRegistry.scanAndRegisterRepositories(modulePackage, moduleLoader.classLoader())` — unchanged.
9. **[M]** Delete `GangModuleConfig.java`; create `IdentityContractConfig.java` (core, 4 beans: `userLookupContract`,
   `permissionRegistryContract`, `identitySettingsContract` (step 1b), `gangMembership` holder bean — see step 9b).
9b. **[S]** (**new, R9/B3**) Add `GangMembership`/`GangMembershipView` to `gangland-api` (`org.luckyraven.gangland.
   data.gang`, sibling to `data.economy.BankTiers`); `IdentityContractConfig.gangMembership()` returns a
   zero-arg-constructed instance (inert), exactly like `DataConfig.java:162`'s `bankTiers()` bean today.
9c. **[S]** Create the module's own `GangConfig` (`@Configuration`, 5 wholly-module contract beans) + a
   `GangMembershipInstaller` bean with `@PostConstruct install()` (§3 row 3b) wiring `MemberManager`/`GangManager`
   into a `GangMembershipView` lambda/adapter, calling the core-injected `GangMembership.install(...)`.
   **Gate:** `mvn clean install -pl gangland-features/gangland-gang,gangland-impl -am`; server boots with the
   gang module present but no external consumer yet; smoke row **G1**: `/glw module list` shows `gang` loaded,
   `gang_table`/`member_table`/`rank_table` created, `GangMembership.gangIdOf` returns real values once a gang
   exists.

### G2 — Move impl consumers (the "hard part")
10. **[L]** `UserDataLoader.java` — swap `MemberManager memberManager` ctor param for `GangMembership
    gangMembership`; `user.setGangId(gangMembership.gangIdOf(user.getUuid()))`.
11. **[L]** `CreateAccountListener.java`/`PlayerBootstrapService.java` — delete all `Member`/`MemberManager` code
    (S2). Create `MemberJoinListener` (gang module) listening on `UserDataInitEvent` (§3 row 7), not
    `PlayerJoinEvent` — no ordering test needed, the event already carries `User` and fires on both paths.
11b. **[L]** (**new, B2**) Create `RankPermissionApplier` (gang module, §3 row 8) with `initialize`/`flush`;
    rewire the 4 `flushPermissions` call sites (`GangDemoteCommand.java:147`, `GangPromoteCommand.java:179`,
    `GangTransferCommand.java:179,188`, `Gang.java:137`, all already moving to the module) and `MemberJoinListener`
    (step 11) to call it; add the 3 narrow permission primitives to core `User` (§3 row 8, exact names TBD, §13).
12. **[M]** `data/placeholder/worker/GanglandPlaceholder.java` — delete `gang_*`/member-touching `user_*`
    branches; add `PlaceholderContribution` dispatch. Create `GangPlaceholderContribution` (module).
13. **[M]** `command/sub/waypoint/WaypointGangIdCommand.java` → module `CommandContribution` under `waypoint`.
14. **[M]** `command/sub/debug/{ComponentExecutorCommand,DebugCommand}.java` — extract gang/rank branches into
    module `CommandContribution`s under `debug`.
15. **[S]** `events/gang/{GangBountyEvent,GangLevelUpEvent}.java` → move into module `events` package unchanged
    (inert per GR-19 — moving, not fixing).
15b. **[M]** (**new, B5**) Split `BountyIncreaseListener.java`: delete `onGangBountyIncrease`, create
    `GangBountyMessageListener` (module) with that method's body, own `UserManager` ctor param.
15c. **[M]** (**new, B5**) Same split for `LevelUpListener.java`/`GangLevelMessageListener`.
16. **[S]** `Gangland.java`: delete `VaultPermissionBridge` calls (`:71-72,204`) and the two bStats chart lines
    (`:122,125`, dropped per D4). Move Vault lifecycle into `GangModule`.
16b. **[S]** (**new, C5**) `GangAllyWeaponImpactListener.java`: rewrite `onGangMemberWeaponImpact` onto
    `GangMembership.alliedOrSame(UUID,UUID)`, drop the `GangManager` ctor param, **delete** `@ListenerHandler
    (condition = "isGangEnabled")` — the class is now always safe to register.
16c. **[S]** (**new, B1**) `TurfFriendlyFireListener.java`: rewrite `isFriendly(int,int)` onto
    `GangMembership.gangsAllied(int,int)`, drop the `GangLookupContract gangs` ctor param, keep
    `UserLookupContract users` unchanged (`user.getGangId()` still resolves the attacker's own gang).
16d. **[S]** (**new, B1**) `GanglandCarGangs.java` (gadget): rewrite `sharesGang(UUID,UUID)` as a one-line
    delegate to `GangMembership.alliedOrSame(UUID,UUID)`, drop the `MemberManager` ctor param. **Named behavior
    change**: widens from pure same-gang to same-gang-or-allied — flagged, not silently absorbed (§8 risk 7).
17. **[M]** `config/{DataConfig,GameplayConfig,SchedulingConfig,WiringConfig}.java` — delete every gang-typed bean
    method; they move into the module's own `@Configuration` classes.
    **Gate:** `mvn clean install -pl gangland-impl,gangland-features/gangland-gang -am`; smoke row **G2**: gang
    module + core boot together, `/glw gang create`, `/glw waypoint gangid`, gang placeholders via PAPI, `/glw
    debug` gang branch, rank promote/demote flushes permissions correctly all work.

### G3 — Menus on Oriel (depends on WS2 having landed AND settling C7's registry mechanism)
18. **[M]** Move the 5 gang menu YAMLs into `gangland-gang/src/main/resources/inventory/`; add the module's own
    `FileHandler` extraction bean.
18b. **[L]** (**new, C7**) Port `GangItemSourceProvider`'s registry registrations onto whatever Oriel mechanism
    WS2's G0 settles (§3 row 13b, §10 — **blocking on WS2**, not this plan's own decision).
19. **[S]** `phone.yml`'s gang-menu button gains the conditional gate (§3 row 14).
20. **[M]** `command/sub/gang/*` (18)/`rank/*` (13) — swap `.open(player)` calls to Oriel's `MenuOpener`.
20b. **[S]** (**new, C5**) Delete `condition = "isGangEnabled"` from `GangCommand.java:52` and
    `GangMembersDamageListener.java:16` as they move into the module — module presence is already the switch.
    **Gate:** `mvn clean install -pl gangland-impl,gangland-features/gangland-gang -am`; smoke row **G3**: `/glw
    phone` → gang panels open with the module installed; without it, phone opens sans gang button, no fault.

### G4 — Re-point consumer modules (revised — only 2, not 4, per S1/B1)
21. **[S]** `turf/pom.xml`: add `gangland-gang` `provided` dep; `module.yml`: `Depends: [gang, civilians]`.
22. **[S]** `mail/pom.xml`: add `gangland-gang` `provided` dep; `module.yml`: `Depends: [gang]`.
23. **[S]** `gadget`, `civilians`, `cops-n-crooks`, `npc-shops`: **no pom/module.yml change** — verified via the
    step 16b/16c/16d rewrites; only a build-time sanity check that each still compiles against `GangMembership`
    (already in `gangland-api`, already `provided` for every module).
    **Gate:** `mvn clean install` full reactor; smoke row **G4**: turf claim-by-gang, mail gang-invite/ally,
    gadget car gang-sharing (now gang-**or-ally**, step 16d), civilians ally-no-friendly-fire, cops-n-crooks
    turf-friendly-fire-cancel all work; **and** all four with the `gang` jar removed: gadget/civilians/cops-n-crooks
    degrade gracefully (no cancellation/sharing, no fault); turf/mail report `module.dependency.missing`.

### G5 — Delete `gangland-domain`, docs, final smoke
24. **[M]** (**new, C3**) Split the 18 domain test files per §7's table before deleting the module.
25. **[S]** Delete `gangland-infra/gangland-domain/`; remove its `<module>` line from `gangland-infra/pom.xml`.
26. **[S]** (**C8, 5 sub-steps, was 1**) `gangland-build/pom.xml` shade-exclude + copy-set + dependency block (3
    edits); `gangland-features/pom.xml` `<module>` entry; root `pom.xml` `<dependencyManagement>` entry.
27. **[M]** Docs: `documentation/module-loader.md` `## Core seams` gains the `GangMembership` row (mirroring
    `BankTiers`'s existing row) + `PlaceholderContribution`; `CLAUDE.md`'s Module Structure table gains
    `gangland-gang`; memory updated (module count 6→8).
    **Gate:** `mvn clean install` + `mvn test` full reactor, 0 failures; final smoke row **G5**.

---

## 5. Config, messages, permissions

| Item | Today | Destination |
|---|---|---|
| `settings.yml` `Gang:` block | `Settings.java:541-554` — **confirmed 10 parsed keys** (`Enable, Name_Duplicates, Rank.Head, Rank.Tail, Display_Name_Char, Account.Initial_Balance, Account.Create_Cost, Account.Maximum_Balance, Account.Contribution_Rate`; the 29-line YAML count from the previous pass included comments/blank lines/section headers, corrected per C6) | Module's own `gang.yml`, same key names. **C6 migration mechanism**: on first boot of `0.10.0` with the module installed, if `settings.yml` still has a `Gang:` block, the module's `FileHandler` does a one-shot copy of the 10 values into its freshly-generated `gang.yml` (instead of taking defaults) and logs a loud boot warning naming the copied values; the `Gang:` block in `settings.yml` itself becomes dead/ignored after that (not auto-deleted, admin cleans it up) |
| `settings.yml` `User:` / `Bounty:` / `Wanted:` blocks | unchanged locations | **stay** core (identity slice, §9 D2) |
| `Messages.java` `GANG_*` (66) + `RANK_*` (20) = 86 constants | `gangland-api/.../Messages.java` | Module's own message YAML (unchanged from previous pass) |
| `Messages.java` `BOUNTY_*` (7) + `WANTED_*` (5) | same file | stay core |
| `commands.json` entries | 154 top-level keys | 31 gang/rank + `waypoint gangid` + gang/rank `debug` sub-entries move into the module's own `commands.json` |
| `gangland.command.gang.*`, `gangland.command.rank.*` | `GangPermissions.java` | move with the file |
| `isGangEnabled` condition (C5) | `GangCommand.java:52`, `GangMembersDamageListener.java:16`, `GangAllyWeaponImpactListener.java:25` | **deleted from all three** — module presence (for the first two) and the `GangMembership` holder's inert default (for the third) are the real switches now; a manual, module-internal `Enable` flag is a named follow-up if a runtime soft-disable is still wanted (§9 D5) |
| Gang/rank Oriel menu YAML | `gangland-impl/.../inventory/{gang_info,gang_stat,alliance_stat,phone_gang,phone_gang_search}.yml` | Module jar `inventory/` |
| `phone.yml`, `phone_banking.yml`, `phone_bounty.yml`, `user_stat.yml` | same dir | stay core |

---

## 6. Persistence

Unchanged from the previous pass for table names/no-migration, **plus**:

| Table | Current location | After WS5 | Migration |
|---|---|---|---|
| `gang_table`, `gang_alliance`, `member_table`, `rank_table`, `rank_parent`, `rank_permission` | as before | module `database` package | none |
| `permission` | `database/{tables,repositories}/plugin/` | stays core | none |

- **Rank seeding (B5, new):** `GanglandDatabase.insertInitialData()` (`:134-146`, confirmed) currently seeds the
  head/tail ranks using core `Settings.getGangRankHead/Tail()`. This body moves into the module's own
  `RankManager`/`RankRepository` init, reading the module-owned `GangSettingsContract`'s 3 remaining getters (§3
  row 5b) instead. **Not verified**: whether `GanglandDatabase.insertInitialData()` does anything else besides
  rank seeding — if it's now empty, delete the override entirely rather than leave a no-op (§13).
- **`setDataSupplier` wiring, autosave, FK**: unchanged from the previous pass — `PeriodicalUpdates` confirmed
  free of `gang.*` imports, no FK crosses the module boundary (`User.gangId` is a plain int column).
- **B4's ordering invariant**: see §3 row 6b — resolved via the `BeanPostInitialize` phase boundary, not a
  cross-artifact parameter; gated on a real-boot proof at G0 (step 1c's gate).

---

## 7. Tests

### Domain test split (C3 — 18 files, previously unhomed)

| Test | New home | Notes |
|---|---|---|
| `GangAllianceTest`, `GangManagerAllianceTest`, `GangMembershipTest` (domain's own, unrelated name clash with the new holder — **rename the new holder's test to `GangMembershipHolderTest` to avoid collision**, flagged) | module | — |
| `bounty/BountyTest` | core | identity slice |
| `member/{MemberTest,MemberManagerTest}` | module | — |
| `permission/GangPermissionsTest` | module | **covers fixed GR-08** (C4) |
| `rank/{RankTest,RankManagerTest,RankAssignmentPolicyTest}` | module | `RankManagerTest`/`RankTest` carry the GR-12/13/35 pins (C4); `RankAssignmentPolicyTest` **covers fixed GR-03** (C4) |
| `user/{UserTest,UserManagerTest,LevelTest}` | core | identity slice; `UserTest`/`UserManagerTest` need new fixtures for the split `flushPermissions`/`initializeUserPermission` removal (B2) — the moved logic's tests go with it to the module |
| `wanted/{WantedTest,WantedKillTrackersTest}` | core | identity slice |
| `support/{FakeGangSettingsContract,RecordingGangAllianceRepository,TestLevelUpEvent}` | `FakeGangSettingsContract` splits alongside the contract (§3 row 5b) — a `FakeIdentitySettingsContract` stays core-test, `FakeGangSettingsContract` (3-getter) moves module-test; `RecordingGangAllianceRepository` → module; `TestLevelUpEvent` → core (Level stays core) | |

### Other tests

| Existing test | Home today | Action |
|---|---|---|
| `CreateAccountListenerTest` | impl | Split: identity-only assertions stay; member-creation assertions move to the module's `MemberJoinListenerTest` (now `UserDataInitEvent`-based, S2 — no ordering assertion needed, unlike the previous pass's `PlayerJoinEvent` design) |
| `GangAllianceRepositorySpiTest` | impl | Moves to module |
| 48 test files naming Gang/Member/Rank (census §8) | mixed | Each moves with its production class |
| Turf capture-service tests | turf | Unaffected — only read `ownerGangId` (int) |

**New tests**
- `GangMembershipHolderTest` (api or a small test module) — `gangIdOf`/`gangsAllied`/`alliedOrSame` inert defaults
  and installed-view delegation.
- `MemberJoinListenerTest` (module) — `UserDataInitEvent` on first join creates a `Member`; on reload (players
  already online) attaches the existing one; **no ordering assertion needed** (S2 removes that requirement
  entirely — the event itself only fires once real data is ready).
- `RankPermissionApplierTest` (module) — `initialize`/`flush` grant/revoke the right permission strings; confirms
  the main-thread hop is preserved where the caller requires it.
- `GangPlaceholderContributionTest` (module) — unchanged from the previous pass.
- `GanglandPlaceholderDispatchTest` (impl) — unchanged.
- Flip the **3** test-pinned docket entries (C4): GR-12, GR-13, GR-35 travel with `RankManagerTest`/`GangAlliance
  Test` into the module unchanged; GR-03/GR-08 are **already fixed** (C4) and their covering tests
  (`RankAssignmentPolicyTest`/`GangPermissionsTest`) move green, not red.

### Smoke rows

| Row | Setup | Action | Expect |
|---|---|---|---|
| S1 | Core only, `gang` module **absent** | Boot | No fault; `/glw gang` unregistered; `turf`/`mail` report `module.dependency.missing`; `cops-n-crooks` also unloaded (transitive via turf); `gadget`/`civilians`/`npc-shops` load normally, degraded (no gang-sharing, no ally-no-friendly-fire, but no crash); `/glw phone` opens with no gang button |
| S2 | + `gang` module present | Boot | All 8 modules loaded; `gang_table`/`member_table`/`rank_table` created |
| S3 | S2 | `/glw gang create`, invite, accept (mail), promote (rank), ally | Gang created, member added, permissions flushed on promote, alliance formed |
| S4 | S3 + turf | `/glw turf claim` | Owner set; turf UI placeholders resolve |
| S5 | S3 + gadget, two allied (not same) gang members | Share a car | `GanglandCarGangs.alliedOrSame` returns true (widened semantics, step 16d) — **explicitly test the widened case**, not just same-gang |
| S6 | S3 + civilians, allied gang members shoot each other with a Bartizan weapon | Damage | Cancelled (row via `GangMembership.alliedOrSame`) |
| S7 | S3 + cops-n-crooks + turf, non-owning **allied** gang member damages the turf Quartermaster | Damage | Cancelled (row via `GangMembership.gangsAllied`) |
| S8 | S2 | PAPI placeholders | Resolve correctly |
| S9 | S2 | `/glw reload` twice | No duplicate registration, gang data intact |
| S10 | S2 | Restart | Gangs/members/ranks/alliances survive |

---

## 8. Risks

| # | Risk | Mitigation | Rollback |
|---|---|---|---|
| 1 | G2's split of `CreateAccountListener`/`PlayerBootstrapService`/`GanglandPlaceholder`/`BountyIncreaseListener`/`LevelUpListener` is now **5** mixed-concern files, not 2 | Each is its own numbered step (15b, 15c, 11, 12) with an independent commit | Revert the specific step's commit; the others are unaffected since each split is file-local |
| 2 | `RankPermissionApplier`'s exact `User` primitives are unnamed (§13) | First thing G2 step 11b's executor designs, before touching call sites | Keep `flushPermissions`/`initializeUserPermission` on `User`/`UserManager` temporarily with a `Rank`-free overload bridging to the real one, defer the full split one gate |
| 3 | Dropping the two bStats charts is a silent, permanent metrics loss | Named explicitly (§4 step 16, §9 D4) | Re-add if a Metrics bean seam is confirmed reachable (§13) |
| 4 | ~~`MemberJoinListener` relying on `PlayerJoinEvent` same-priority ordering~~ **eliminated by S2** — `UserDataInitEvent` already fires after data is ready on both paths, no ordering assumption left | — | — |
| 5 | `gangland-domain`'s deletion (5 pom touch points, C8) — a missed reference breaks the whole reactor | G5 step 26 is the last gate, C8's 5 sub-steps are each their own line item with a grep-based acceptance check | `git revert` G5 only — G0-G4 work with `gangland-domain` present-but-empty |
| 6 | **B4's ordering proof might fail** — if `PlayerBootstrapService`'s `BeanPostInitialize` guarantee doesn't actually cover member-cache-populated-before-gangId-attached (e.g. if `MemberManager` doesn't implement `BeanLifecycle` the way assumed) | G0 step 1c's gate explicitly tests this before G1 proceeds; §3 row 6b names the Keystone-side fallback | Re-add an ordering-only parameter using a core-compilable type (e.g., a throwaway `GangMembership`-typed param, even though it wouldn't carry real ordering info today — would need a Keystone-side timing guarantee added to the holder, flagged as a possible small Keystone ask if this risk fires |
| 7 | **New, from step 16d**: `GanglandCarGangs`'s widened same-gang→same-gang-or-allied semantics is a real gameplay behavior change on a docket-covered class (GD-06) | Named explicitly in §4 step 16d and tested explicitly in S5 rather than absorbed silently | Keep the pre-widen behavior by adding a `sameGangOnly` boolean param to `GangMembership` if the user rejects the widen (§9, new sub-decision) |

---

## 9. Decisions for the user

### D1 — Separate `gang` module vs. folding turf into it
Unchanged from the previous pass. **Recommendation: (a) separate.** Reviewer: **Agree.**

### D2 — Wanted/Bounty/Level placement — **evidence corrected (B1/C4)**
**Options:** (a) stay core (this plan). (b) move into the gang module. (c) move into `cops-n-crooks` (deferred).
**Recommendation:** (a). **The previous pass's evidence was wrong** (cops-n-crooks *does* import `Gang` via
`TurfFriendlyFireListener`, B1) — the real reason to keep Wanted/Bounty/Level core is that `User`'s own
constructor builds `Bounty`/`Wanted` directly (`User.java:63-67`) and `Level` is a field on `User` — moving them
would put module types inside a core class's constructor, the exact B2 problem this rework just fixed, reopened.
Reviewer: **Agree, conclusion right, evidence must be replaced** — done.

### D3 — `Permission`/`PermissionRegistryContract` placement
Unchanged. **Recommendation: (a) stay core.** Reviewer: **Agree.**

### D4 — Drop the two bStats charts
Unchanged. **Recommendation: (a) drop.** Reviewer: **Agree, non-blocking.**

### D5 (new, reviewer) — Is `gang` optional or mandatory infrastructure?
**Options:** (a) mandatory — every server is expected to run it, module absence is a degraded/unsupported state.
(b) genuinely optional — a server can run gangs-free forever, every consumer degrades gracefully (this plan, after
S1's `GangMembership` holder). **Recommendation: (b).** With S1 adopted, the real cost of "optional" dropped from
5-of-8 modules lost to 2 direct + 1 transitive, and `Gang.Enable` as a separate switch is now genuinely redundant
with module presence (C5) rather than load-bearing. If (a) is chosen instead: the `GangMembership` holder becomes
unnecessary complexity (every consumer could just take `Depends: [gang]` again, closer to the previous pass's
shape) — but it also removes the possibility of running Gangland without gangs at all, which the module split's
whole premise (a *runtime* module) argues against.

### D6 (new, reviewer) — Does the WS6 facade expose gangs?
**Options:** (a) `GanglandApi.gangs()`/`ranks()` return `GangManager`/`RankManager` objects directly (today's
`plans/WS6-api.md` design, blocked — cannot compile once those are module types). (b) `Optional<GangLookupContract>`/
`Optional<RankLookupContract>` resolved lazily from the container per call (R7). (c) expose only the
`GangMembership` holder (always non-null, narrower facts). **Recommendation: (b) for anything needing a real
`Gang`/`Rank` object (mail/turf-shaped consumers of the facade), (c) for anything needing only is/which-gang facts**
— i.e., WS6's facade should offer **both**: the `Optional<GangLookupContract>` accessor for object-shaped needs and
`GangMembership` (already non-optional, no wrapping needed) for fact-shaped needs. This is not a new type on WS5's
side — both already exist per R7/R9; WS6 just needs to route to them instead of `GangManager`/`RankManager`.

---

## 10. WS6 asks / Oriel asks / Keystone asks

- **WS6 asks:**
  1. Fold `PlaceholderContribution` (§3 row 9) into the facade/catalog pass (unchanged ask, now the **only** new
     api interface WS5 introduces — S4).
  2. Route the facade's gang/rank exposure per D6: `Optional<GangLookupContract>`/`Optional<RankLookupContract>`
     resolved lazily per call (R7) for object-shaped access, `GangMembership` (already always-present) for
     fact-shaped access — do **not** rebuild `GanglandApiImpl.gangs()`/`ranks()` around `GangManager`/
     `GanglandRankLookup` concretes (B6, confirmed those types live in `gangland-impl` today and cannot compile
     post-WS5).
  3. `gangland-api`'s own compile dependency needs to be `gangland-core` (not `gangland-domain`, which is deleted)
     for the 4 files WS6 should already know about (`WaypointTeleport.java`, `TeleportEvent.java`,
     `UserLevelUpEvent.java`, `MoneyAspect.java`).
- **Oriel asks (via WS2):** name the registry-injection mechanism a module uses to register into core's Oriel
  `ItemProviderRegistry`/`FilterRegistry`/source registry (C7, §3 row 13b) — WS5's G3 step 18b blocks on this.
- **Keystone asks:** none confirmed-needed. If §3 row 6b's ordering proof fails at G0, a possible small ask (a
  `BeanLifecycle`-completion-ordered variant of `BeanPostInitialize`, or a documented guarantee that all
  `BeanLifecycle.onInitialize()` calls — core and module — complete before any `BeanPostInitialize` runs) may
  follow — flagged, not requested yet, since the mechanism as described should already hold.

---

## 11. Docket

All 37 `GR-` entries move with their files. **Corrected per C4**: GR-03 and GR-08 are **already fixed** (0.8.3
wave 3, commit 8a731fd5 — tests `RankAssignmentPolicyTest`/`GangPermissionsTest`, both in the domain test tree,
move **green**, not "unfixed"). True test-pinned count is **3**: **GR-12, GR-13, GR-35** (GR-09 is a comment on
`RankDeleteCommand`, not a pin). Tier split unchanged: P0×6, P1×6, P2×18, P3×7.

The full 37-row structural-effect table from the previous pass is otherwise unchanged (available on request /
in version history of this file) except the two corrected rows:

| Id | Correction |
|---|---|
| GR-03 | **Fixed** (0.8.3 wave 3, commit 8a731fd5) — covering test `GangPermissionsTest` moves to the module, green |
| GR-08 | **Fixed** (0.8.3 wave 3, commit 8a731fd5) — covering test `RankAssignmentPolicyTest` moves to the module, green |
| GR-09 | Comment only, not a pin (was miscounted as pinned in the previous pass) |

Adjacent core-lifecycle entries (CL-14, CL-18, CL-23) — unchanged relevance, re-check after the move.

**Docket write-back (C8/planner-brief requirement, previously only planned as a read):** after each gate lands,
record the commit/branch/what-changed for any docket id the gate's steps touch (GR-15's `GangMembersDamageListener`
move, GR-19's inert-event move, etc.) in the artifact's `bugs` collection via `write_db` — not just a pre-gate
read. Not executed this session (planning only); named as a gate-closing action for the executor.

---

## 12. Estimate — honest, per the reviewer's check

28 steps grew to **~40** (G0 +3, G1 +2, G2 +6, G3 +2, G4 unchanged, G5 +2, plus the 5-way C8 split). Sizing:

| Gate | Steps | S | M | L |
|---|---|---|---|---|
| G0 | 6 | 1 | 2 | **2** (upsized from previous pass's single [L] — C2's 177-file, 9-module blast radius) |
| G1 | 8 | 3 | 3 | 2 |
| G2 | 11 | 3 | 6 | 2 |
| G3 | 5 | 2 | 2 | 1 |
| G4 | 3 | 3 | 0 | 0 |
| G5 | 4+5(C8) | 6 | 3 | 0 |
| **Total** | **~40** | **18** | **16** | **7** |

Wall-clock: S≈1h, M≈4h (half day), L≈8h (day+) → 18×1 + 16×4 + 7×8 = 18+64+56 = **~138 executor-hours**. At the
studio's stated pace (Sonnet executors, Opus reviewers, one reactor build at a time, low parallelism because G0→G5
is a mostly-sequential critical path with only G4's 1 real step parallelizable against G3) this is **~17-18
executor-days**, not "2-3 studio days" (the previous pass's error, flagged by the reviewer) and somewhat above
even the reviewer's own "~12 executor-days" estimate from the smaller 93h/28-step count — the extra ~45h comes
directly from the blockers' fixes (B2's permission-applier split, B5's three-way listener/database split, C8's
5-way pom edit, C3's 18-test relocation) which the previous pass's estimate never counted.

---

## 13. Not verified

- **`RankPermissionApplier`'s exact `User` primitive names** (§3 row 8) — first thing G2 step 11b's executor
  designs; direction is fixed (narrow, `Rank`-free), exact signatures are not.
- **Whether `MemberManager` implements `BeanLifecycle`** (§3 row 6b) — the ordering-proof mechanism assumes it
  does and that its `onInitialize()` populates the member cache; not confirmed this session, required before G0
  step 1c's parameter deletion can be trusted rather than merely argued for.
- **Whether `GanglandDatabase.insertInitialData()` does anything besides rank seeding** (§6) — if empty after
  B5's removal, delete the override; if not, only remove the rank-specific lines.
- **`SchedulingConfig.java:47-57`'s `MemberManager` parameter** — reviewer's C9 says it feeds
  `playerBootstrapService(...)`'s bean construction, not a scheduled task; not independently re-verified this pass,
  carried forward from the review.
- **Whether any module can reach a bStats `Metrics` instance** (§9 D4) — unchecked; "drop" the right default
  either way.
- **The exact Oriel registry API names** for §3 row 13b / §4 step 18b (C7) — WS2's G0 spike should settle it;
  this plan's G3 blocks on that answer.
- **`graphify affected` at full depth** for `UserManager`/`Gang`/`GangManager` — this pass again relied on manual
  greps + the review's own citations rather than a fresh full traversal; still recommended before G0 execution.
  `graphify affected "Gang"` continues to resolve to the unrelated `WaypointType.GANG` enum case
  (`Waypoint.java:85`) on a bare-name query — confirmed again this pass, still needs the qualified name or `--dfs`.
- **Live docket status** for the 37 `GR-` ids beyond the two corrections C4 supplied — not queried against the
  live artifact DB this session; §11's table is otherwise the bugs.json snapshot.
- **`gangland-build/pom.xml`'s exact line numbers** for the shade-exclude/copy-set/dependency edits (C8) — the
  review cited `:82-87`, `:123-148`, `:170-200`; not independently re-confirmed this pass.
- **D5/D6 are genuinely open** — both are user calls, not defaults this plan can silently pick; §9 states a
  recommendation for each but the plan's own steps (§4) are written assuming the recommended answer for both.

## §0d Execution corrections (2026-09-22, orchestrator, from the G0 hand-back)

- §3 row 5b: `IdentitySettingsContract` (and the `IdentitySettings` facade) live in **gangland-core**, not gangland-api — the api depends on core, not the reverse, so a core type cannot name an api type; a module still sees it because gangland-api re-exports gangland-core at compile scope. G1's `IdentityContractConfig` references it there.
- `Rank`/`RankManager` reached `Permission` by same-package access; after the move they import it and `Permission.setID` is widened accordingly.
- Six ponytail-tagged permission call-site bridges replace the deleted `User.flushPermissions(Rank)`/`UserManager.initializeUserPermission` until G2 step 11b's `RankPermissionApplier`; the G0 review rules on their unset-vs-false semantics.
- Step 1c's live proof (members attach gangId across first load, `/glw reload`, fresh DB) is owed to the merge smoke on 0.10.0; `MemberCachePopulationOrderTest` is the unit-level pin.
- **W51 (2026-09-22, from the G1 stop):** G1 alone does not compile — 127 files outside gangland-domain import the moved gang types directly (impl 76, turf 35, mail 12, gadget 2, civilians 1, cops 1). G1, G2 (steps 10-17 incl. 11b) and G3's relocation slice (step 20 commands + test, 20b `GangMembersDamageListener`, S3's filter adapters) execute as ONE gate, plus the turf/mail pom swap and the S1 `GangMembership` rewrite for gadget/civilians/cops-n-crooks. "WS2's Oriel registry mechanism" is obsolete (keystone-inventory). The YAML menu dialect is impl-only: a gang menu that cannot move stays a core YAML menu fed through contracts, and the gap is a WS6 ask, never new api surface invented in WS5.
