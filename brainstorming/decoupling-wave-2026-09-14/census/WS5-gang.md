# WS5 Census: Gang System as a Runtime Module

**Date:** 2026-09-14 | **Branch:** 0.9.1 | **Keystone pin:** 1.9.2 | **Graph:** fresh

---

## 1. Domain Inventory

**Path:** `gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland/gang/`

**Total files:** 40 | **Est. LOC:** ~2,600

### By Subdirectory

- **gang/** (5 files)
  - `Gang.java` (126 edges) — holds `List<Member> members`, `Set<GangAlliance> allies`, name, displayName, color, description, `lastMemberOnlineAt`
  - `GangManager.java` — manager bean, methods: getGang, findById, add, remove, getAll, clear, size, contains, onInitialize, onClear
  - `GangAlliance.java` — alliance model
  - `GangFilterAdapter.java` — inventory filter
  - `GangSettings.java` — static settings accessor

- **bounty/** (4 files)
  - `Bounty.java`, `BountyContext.java`, `BountyExecutor.java`, `BountySettings.java`

- **contract/** (9 files, all interfaces)
  - `GangLookupContract`, `GangAllianceRepositoryContract`, `GangMessageContract`, `GangPermissionBridgeContract`, `GangSettingsContract`, `MemberRepositoryContract`, `PermissionRegistryContract`, `RankLookupContract`, `UserLookupContract`

- **events/** (7 files)
  - bounty/, level/, user/, wanted/ subdir events

- **member/** (3 files)
  - `Member.java` (104 LOC) — holds `UUID uuid`, `int gangId`, `double contribution`, `@Nullable Rank rank`, `long gangJoinDateLong`; **does NOT hold User object**
  - `MemberManager.java` — CRUD on members
  - `MemberFilterAdapter.java`

- **permission/** (1 file)
  - `GangPermissions.java` (3477 bytes) — permission node constants

- **rank/** (6 files)
  - `Rank.java`, `RankManager.java`, `RankParent.java`, `RankPermission.java`, etc.

- **user/** (4 files)
  - `User.java` (161 edges, 7612 bytes) — holds `InventoryRegistry inventoryRegistry`, `Scoreboard scoreboard`; methods: resetGang(), hasGang(), flushPermissions(Rank), addInventory(), removeInventory()
  - `UserManager.java` (438 edges) — online/offline player cache
  - `Level.java` — experience/progression
  - `UserFactory.java`

- **vault/permission/** (1 file)
  - `VaultPermissionBridge.java` — Vault integration

- **wanted/** (6 files)
  - `Wanted.java`, `WantedContext.java`, `WantedExecutor.java`, `WantedKillTracker.java`, `WantedKillTrackers.java`, `WantedSettings.java`

### Key Observations

- **User is the player record** every module uses; **stays in core** per WS5 assumption.
- **Member holds UUID + gangId**, not User object; Gang → List\<Member\> relationship.
- **Bounty and Wanted are per-user but live in `gang.*`** — they are personal, not gang-scoped.
- **GangSettings static access exists** — may need refactoring for module isolation.

---

## 2. Impl Consumers Grouped

**Total impl files importing from `org.luckyraven.gangland.gang`:** 167

### By Subdirectory

- **command/sub/** (86 files)
  - gang/ (18): GangCommand, GangCreateCommand, GangDeleteCommand, GangKickCommand, GangLeaveCommand, GangPromoteCommand, GangDemoteCommand, GangInviteCommand, GangTransferCommand, GangColorCommand, GangDisplayCommand, GangDescriptionCommand, GangDepositCommand, GangWithdrawCommand, GangMembersCommand, GangRenameCommand, GangAllyAbandonCommand, GangAllyCommand
  - rank/ (13): RankCreateCommand, RankDeleteCommand, etc.
  - bounty/ (3): files
  - wanted/ (4): files
  - level/ (7): files
  - other (41): WaypointGangIdCommand, etc.

- **listener/** (10 files)
  - GangMembersDamageListener, etc.

- **config/** (8 files)
  - DataConfig, GangModuleConfig, GangFilterRegistration, GameplayConfig, others

- **database/** (18 files)
  - repositories/ (5): GangRepository, GangAllianceRepository, RankRepository, RankPermissionRepository, RankParentRepository, MemberRepository
  - tables/ (6): GangTable, GangAllianceTable, MemberTable, RankTable, RankPermissionTable, RankParentTable
  - others

- **data/** (4 files)
  - GanglandPlaceholder.java (36-line worker) — renders `%gangland_gang_*%`, `%gangland_rank_*%` placeholders
  - UserDataLoader.java
  - others

- **file/** (10 files)
  - configuration/inventory/itemsource/GangItemSourceProvider.java
  - configuration/{gang,rank,bounty,wanted}/ settings/messages files

- **bootstrap/** (4 files)
  - PlayerBootstrapService.java
  - ReloadPlugin (gang reload hooks)
  - others

### Coupling Profile

- **Core-gang coupling:** command/sub → config → database → persistence forms the tight core.
- **Gang-only vs. User-only:** Most command/sub/* use Gang/Member/Rank; some (e.g., WaypointGangIdCommand) use User/Level only. A split should separate these cleanly.

---

## 3. Module Consumers

### Import Counts

- **turf** (36 files) — highest consumer
- **mail** (14 files)
- **cops** (0 files) — **DOES NOT import gang.***
- **gadget** (8 files)
- **npc-shops** (6 files)
- **civilians** (2 files)

### Turf's Top Gang Method Calls (from grep)

1. `getEconomy()` (4 calls)
2. `getName()` (3 calls)
3. `getMembers()` (3 calls)
4. `getId()` (3 calls)
5. `setLastMemberOnlineAt()` (2 calls)
6. `getDisplayNameString()` (1 call)

### Turf-Gang Relationship

- **Turf.java location:** `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/data/Turf.java`
- **Owner field:** `Integer ownerGangId` (null = unclaimed) — turf uses gang **ID** only, not Gang object.
- **Turf module descriptor:** `Depends: [civilians]` (from module.yml grep) — **turf does NOT yet depend on gang module**, but with WS5 it will become `Depends: [gang, civilians]`.

### Mail Module

- Two CommandContribution beans:
  - `GangMailContribution` — gang command path
  - `GangAllyMailContribution` — gang.ally command path
- Uses GangManager, MemberManager, User, Gang for invite/ally system.

---

## 4. Persistence

### Tables

| Table | File | Columns (approx.) | FKs |
|-------|------|-------------------|-----|
| **gang_table** | GangTable | id, name, displayName, color, description, economy, lastMemberOnlineAt | — |
| **gang_alliance** | GangAllianceTable | id, gangId, alliedGangId | FK to gang_table (2×) |
| **member_table** (in player/) | MemberTable | uuid, gangId, contribution, rankId, joinDate | FK to gang_table, rank_table |
| **rank_table** | RankTable | id, gangId, name, hierarchy | FK to gang_table |
| **rank_parent** | RankParentTable | rankId, parentRankId | FK to rank_table (2×) |
| **rank_permission** | RankPermissionTable | rankId, permission | FK to rank_table |

### Repositories

- **GangRepository** (extends AbstractRepository\<Gang\>)
  - Wraps: GangTable, GangAllianceTable
- **RankRepository** (extends AbstractRepository\<Rank\>)
  - Wraps: RankTable, RankParentTable, RankPermissionTable
- **MemberRepository** (via RepositoryRegistry)
  - Wraps: MemberTable

### Wiring

- **DataConfig** (gangland-impl):
  - L78: `userManager(online)` bean
  - L91: `offlineUserManager` bean
  - L121: `rankManager` bean
  - L127: `gangManager` bean
  - L134: `memberManager` bean — **depends on gangManager and rankManager**
  - L171: `wantedKillTrackers` holder bean
  - L182: `registerGanglandPermissions` method

- **setDataSupplier** wiring: Each repository manager calls `setDataSupplier(...)` in `initialize()` method for autosave participation.

- **Autosave** (PeriodicalUpdates): Participates via TableBackend.upsertAll() on gang/rank/member tables.

- **DataCleanupTask**: Gang deletion cascades to members, ranks, alliances.

---

## 5. Wiring

### GangModuleConfig (9 Contract Beans)

```java
@Bean public GangSettingsContract gangSettingsContract()
@Bean public GangMessageContract gangMessageContract()
@Bean public GangPermissionBridgeContract gangPermissionBridgeContract()
@Bean public GangLookupContract gangLookupContract(GangManager)
@Bean public UserLookupContract userLookupContract(@Qualifier("online") UserManager<Player>)
@Bean public RankLookupContract rankLookupContract(RankManager)
@Bean public PermissionRegistryContract permissionRegistryContract(PermissionManager)
@Bean public GangAllianceRepositoryContract gangAllianceRepositoryContract(RepositoryRegistry)
@Bean public MemberRepositoryContract memberRepositoryContract(RepositoryRegistry)
```

### Bean Ordering Invariants (DataConfig)

- `userManager` (online) L78 → `offlineUserManager` L91 (shared type, qualified names)
- `rankManager` L121 → `memberManager` L134 (memberManager parameter depends on rankManager)
- `gangManager` L127 → all of above (used by turf, mail)

### Listeners

- **GangMembersDamageListener** — responds to member damage events
- **CreateAccountListener** — sets up new user account, initializes Member record

---

## 6. Config & UI

### settings.yml `Gang:` Block (29 lines)

```yaml
Gang:
  Enable: true
  Name_Duplicates: false
  Display_Name_Char: '*'
  Rank:
    Head: "member"      # first rank
    Tail: "owner"       # final rank
  Account:
    Initial_Balance: 0
    Create_Cost: 100_000
    Maximum_Balance: 100_000_000_000
    Contribution_Rate: 1_000
  # (+ 15 more lines for member perms, ally rules, bounty, wanted)
```

### Messages Enum

- **Count:** Hundreds of constants; prefix groups include:
  - `GANG_*` (creation, member join, leave, etc.)
  - `ALLY_*` (alliance request, accept, reject, break)
  - `RANK_*` (promote, demote, assign)
  - `MEMBER_*` (damage, contribution, status)
  - `BOUNTY_*` (set, claim, cancel)
  - `WANTED_*` (level change, status)

### Inventory Menus (YAML)

- `inventory/gang_info.yml` — gang overview
- `inventory/gang_stat.yml` — gang statistics (note: has no Slots block per docket GR-17)
- `inventory/alliance_stat.yml` — alliance stats
- `inventory/phone_gang.yml` — gang phone menu
- `inventory/phone_gang_search.yml` — search gang directory
- (+ others using gang placeholders)

### Placeholders (scoreboard.yml)

- Subset relevant to gang (rest are user/level/bounty/wanted):
  - `%gangland_gang_*%` (not explicitly listed in scoreboard.yml grep, but infrastructure exists)
  - `%gangland_rank_*%` (same)
  - `%gangland_user_balance%`, `%gangland_user_bounty%`, `%gangland_user_level%` (user-only)

---

## 7. Existing Seams

### Core Seams (from documentation/module-loader.md)

- **Contracts** (9 gang-related, all in `gang/contract/`):
  - `GangLookupContract` — resolve Gang by ID (used by turf, mail)
  - `UserLookupContract` — resolve User by Player (used by gang, mail)
  - `GangSettingsContract` — gang configuration
  - `GangMessageContract` — gang messages (extends core Messages)
  - `GangPermissionBridgeContract` — permission node lookup
  - `RankLookupContract` — resolve Rank by ID
  - `PermissionRegistryContract` — permission manager
  - `GangAllianceRepositoryContract` — alliance storage SPI
  - `MemberRepositoryContract` — member storage SPI

- **Contribution Paths** (existing):
  - `gang` — mail's GangMailContribution registers here
  - `gang.ally` — mail's GangAllyMailContribution registers here

- **Holder Beans** (multi-instance registries):
  - `WantedKillTrackers` holder (L171 DataConfig) — @Qualifier("wantedKillTrackers") can be injected by modules
  - `PermissionRegistry` (PermissionManager bean) — already injectable

- **ItemVocabulary SPI** (Keystone 1.9.0):
  - No gang-specific vocabulary today (weapons handled by Bartizan).

---

## 8. Tests

**Test count:** 48 files naming Gang/Member/Rank

### Test Classes (partial list from graphify)

- `CreateAccountListenerTest` (impl)
- `GangAllianceRepositorySpiTest` (impl)
- `CaptureServiceOwnedTurfTest`, `CaptureServiceUnclaimedTurfTest`, `CaptureServiceStartAndCompleteTest` (turf)
- Others in turf/mail/cops

### Test Patterns

- **Windows file-locking issue:** HikariCP's minimumIdle=5 holds SQLite handles; tests use `@TempDir(cleanup = CleanupMode.NEVER)` + `MockPluginFactory.releaseDbFiles()` in @AfterEach.
- **Mocking:** Test classes like `RecordingGangAllianceRepository`, `FakeGangLookup` exist.

---

## 9. Docket

**Docket source:** `brainstorming/bug-docket-2026-09-06/` (464 entries, P0-P3 and X)

### Gang-Related Entries (sample from grep)

- **GR-03** — permission holes on gang invite/ally commands (FIXED 0.8.3 wave 3)
- **GR-17** — gang_stat.yml missing Slots block (UI empty)
- **GR-36** — (Player) sender cast in ComponentExecutorCommand (console-allowed cmd)
- **GM-?** — (no explicit Gang Manager entries found; gang bugs roll under GR prefix)

### Test-Pinned Entries

- Unknown exact count; 68 test-pinned entries exist across the full docket.

---

## 10. Turf-on-Gang Details

### Turf.java Structure (source_location: `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/data/Turf.java:30`)

```java
@Getter public final class Turf {
  private final int id;
  private String displayName;
  private CuboidRegion region;
  @Setter private Integer ownerGangId;  // null = unclaimed
  @Setter private BigDecimal incomeAmount;
  @Setter private long lastCaptureTimestamp;
  
  public boolean isUnclaimed() { return ownerGangId == null; }
}
```

### Turf Methods Calling Gang

| Method | Count | Note |
|--------|-------|------|
| `gang.getEconomy()` | 4 | access gang money |
| `gang.getName()` | 3 | display gang name |
| `gang.getMembers()` | 3 | check online members |
| `gang.getId()` | 3 | compare gang ID |
| `gang.setLastMemberOnlineAt()` | 2 | update gang timestamp |
| `gang.getDisplayNameString()` | 1 | display name formatting |

### Turf Module Dependencies (module.yml)

- **Depends:** `[civilians]` (turf NPC Quartermaster needs CombatEligibility/NpcMarkManager beans)
- **After WS5:** will add `Depends: [gang]`
- **No Plugins declared** (no Bartizan direct coupling for turf itself)

### Mail Invites & Alliance System

- Invite/ally storage: per-user, but creates gang joins/alliances when accepted.
- Uses GangLookupContract to resolve gang by ID when processing invite acceptance.
- Two CommandContribution paths: `gang` (for sending) and `gang.ally` (for requests).

---

## 11. Surprises & Static Patterns

- **GangSettings static access** — grep found references (exact pattern unknown due to grep issue), but static getters exist; module isolation will require injection.
- **Bounty/Wanted as user properties** — conceptually "personal consequences" but live in `gang.*` package; can stay there (low coupling to gang proper).
- **Member ↔ User ↔ Gang cycle** — Member holds gangId (not User); Gang holds Members; User holds Scoreboard (not Gang); no true cycle, but User.resetGang() touches gang state indirectly.
- **UserManager generics** — `UserManager<Player>` (online) vs `UserManager<OfflinePlayer>` (offline) with `@Qualifier` — modules get the online version by default.
- **Level (experience) belongs to User, not Gang** — correctly placed; no coupling to move.
- **VaultPermissionBridge** — Vault integration point; used by GangPermissions for group assignment.
- **CommandContribution bean pattern** — mail's two contributions already wire correctly; turf will add similar paths when it becomes a module that depends on gang.
- **No static config holder singleton** — DataConfig is beans-based; safe for multi-instance (Keystone SPI).

---

## Planner Summary

**What must stay in core (identity):**
- `User`, `UserManager` (online/offline), `Level` — every server module needs player identity.
- `UserLookupContract` — the seam for module access to player records.

**What moves to gang module** (WS5):
- `Gang`, `GangManager`, `Member`, `MemberManager`, `Rank`, `RankManager`
- `GangAlliance`, `GangSettings`, `GangPermissions`
- All gang contracts (9 interfaces)
- Bounty and Wanted systems (per-user consequence mechanics)
- Gang commands (18 subcommands under `/glw gang/rank/bounty/wanted`)
- Gang listeners, database tables/repositories, YAML configs, UI menus

**Top 5 couplings to seam with `*Contract` interfaces:**

1. **GangLookupContract** (85 edges in graph) — turf, mail, placeholders look up gangs by ID; module must expose this via `container.getBean(GangLookupContract.class)`.

2. **MemberRepositoryContract** (source: GangModuleConfig) — core autosave participation; gang module must wire and return a live repository.

3. **RankLookupContract** (used by commands) — resolve rank by ID; contracts allow mail/gadget to reference ranks without importing impl.

4. **GangPermissionBridgeContract** (Vault integration) — permission node pattern; gang module owns the node constants (`GangPermissions`).

5. **CommandContribution** path (`gang`, `gang.ally`) — mail already injects; turf will do the same; no new seam needed, but gang module must maintain the paths and inject resolved implementations.

**Test isolation:** 48 test files exist; module boundary will require mock implementations of all 9 contracts for unit tests (or a separate test-api artifact like Keystone's keystone-testkit does).

