# Cross-Module Seams: Gang & Turf Systems (Wave Census)

**Date:** 2026-09-30 | **Survey:** Master @ ff9d813f (0.12.0) | **Keystone:** 1.13.x

## Module Dependency Graph

```
Modules (as declared in module.yml):
  gang (core)
    ↑
    ├── mail
    │
    └── turf ─────╮
        ├─→ gang  │
        └─→ civs ─┤
                  ├─→ cops-n-crooks
                  │   ├─→ Bartizan (soft)
                  │   └─→ turf, civs
                  │
                  ├─→ gadget
                  │   └─→ Bartizan (soft)
                  │
                  └─→ npcshops, lootchest (independent)
```

### Declared Soft Dependencies
- **civilians:** soft-depends on Bartizan (via module police/combat/BartizanNpcWeapons)
- **cops-n-crooks:** Plugins: [Bartizan] (fail-fast)
- **gadget:** Plugins: [Bartizan] (fail-fast)

---

## Cross-Module Seams Table

| From | To | Type | Seam Interface / Class | File | Purpose | Degradation / Coupling |
|---|---|---|---|---|---|---|
| **turf** | **gang** | Contract | `GangLookupContract` | `gangland-features/gangland-gang/src/main/java/.../contract/GangLookupContract.java` L12 | `CaptureService.turf.capture.CaptureService` (L54) resolves gang ownership; `TurfModuleConfig` wires it into `captureService()` bean | Hard dependency; turf capture cannot complete without gang info |
| turf | gang | Data | `Gang` entity (via GangLookupContract.findById) | `gangland-features/gangland-gang/src/main/java/.../Gang.java` | Turf ownership, capture target resolution | Turf displays hang if GangLookupContract misses |
| turf | gang | Contract | `GangAllianceRepositoryContract` | `gangland-features/gangland-gang/.../contract/GangAllianceRepositoryContract.java` L27 | `TurfIncomeDistributor`, garrison balance, allied gang recognition | Hard: turf alliances and income split fail silently |
| turf | civilians | Service | `CivilianService` | `gangland-features/gangland-civilians/.../npc/CivilianService.java` L45 | `TurfDefenderDeployer.getCivilianService()` (L137 in turf) spawns garrison defenders; turf powerup flow reads civilian NPCs | Hard dependency declared in module.yml Depends: [civilians] |
| turf | civilians | Manager | `CivilianSpawnManager` | `gangland-features/gangland-civilians/.../npc/spawn/CivilianSpawnManager.java` | Garrison defender deployment (TurfDefenderDeployer constructor injects it) | Turf garrison feature silently unavailable |
| turf | civilians | Mark Manager | `NpcMarkManager` | Keystone (no source file in Gangland) | Marks garrison defenders; wired by `CiviliansModuleConfig.npcMarkManager()` L75 | Defenders won't show marks if absent |
| cops-n-crooks | turf | Manager | `TurfManager` | `gangland-features/gangland-turf/.../manager/TurfManager.java` L22 | `TurfFriendlyFireListener.onDamage()` (L48 in cops) queries turf data to determine friendly fire rules | Hard dependency declared in module.yml Depends: [turf] |
| cops-n-crooks | turf | Data | `Turf` entity (via TurfManager) | `gangland-features/gangland-turf/.../data/Turf.java` | Cop damage checks ownership/allies within same turf | Friendly fire logic broken |
| cops-n-crooks | turf | Manager | `TurfPowerupManager` | `gangland-features/gangland-turf/.../npc/TurfPowerupManager.java` L28 | Garrison defender lookup by entity; `CopRadio.pursue()` / `CopLoader` consults TurfPowerupManager.getByEntity() | Cop radio targeting of garrison NPCs fails |
| cops-n-crooks | civilians | Service | `CivilianService` | `gangland-features/gangland-civilians/.../npc/CivilianService.java` | Cop targeting/combat delegates via `CopSpawnManager`; detainment service checks eligibility | Hard dependency declared in module.yml Depends: [civilians] |
| cops-n-crooks | civilians | Combat | `CombatEligibility` interface + `GanglandCombatEligibility` impl | `gangland-features/gangland-civilians/.../npc/combat/GanglandCombatEligibility.java` L16 | Downed players cannot be hit by cops; `DownedPlayerRegistry` wired via gangland-core | Downed player protection ineffective |
| cops-n-crooks | gang | Data | `GangMembership` (via read from api) | `gangland-api/src/main/java/.../data/gang/GangMembership.java` L17 | `TurfFriendlyFireListener.isFriendly()` (L101) calls `.alliedOrSame()` to check gang relation | Declared through api, no hard module.yml edge (GD-06: "shares gang" not "allied") |
| gadget | gang | Data | `GangMembership` | `gangland-api/src/main/java/.../data/gang/GangMembership.java` | Car access policy: `GanglandCarGangs.sharesGang()` (L23) checks if both players are in same gang | Always-present holder, no Depends edge; degrades cleanly if gang module absent |
| gadget | gadget | Contract | `CarGangContract` (functional interface) | `gangland-features/gangland-gadget/.../car/access/CarGangContract.java` L10 | Pluggable gang-access policy; default impl is `GanglandCarGangs` | Policy swappable; functional design prevents tight coupling |
| mail | gang | Manager | `GangManager` | `gangland-features/gangland-gang/.../GangManager.java` L13 | Alliance request/invite: `GangAllyMailContribution` + `GangMailContribution` build command trees; resolve gang names, members, ranks | Hard dependency declared in module.yml Depends: [gang] |
| mail | gang | Manager | `MemberManager` | `gangland-features/gangland-gang/.../member/MemberManager.java` | Invite validation, member list query | Part of GangManager dependency edge |
| mail | gang | Manager | `RankManager` | `gangland-features/gangland-gang/.../rank/RankManager.java` | Permission checks (leader/officer only for invite/ally); rank hierarchy | Part of GangManager dependency edge |
| mail | gang | CommandContribution | `CommandContribution` interface + impl classes `GangMailContribution`, `GangAllyMailContribution` | `gangland-mail/.../command/GangMailContribution.java` | Attaches `/glw gang invite`, `/glw gang accept` under core's gang command; wired by `MailModuleConfig` | Seam pattern: mail extends gang without editing gang code |
| **gang** | **core** | Contract | `GangMembership` | `gangland-api/src/main/java/.../data/gang/GangMembership.java` | `GangMembershipInstaller` (in gang) installs view at `@PostConstruct`; made available to all modules via api | Reverse flow: gang pushes membership *up* to api, then pulled by turf/cops/gadget |
| gang | core | Manager | `UserManager<Player>` / `UserManager<OfflinePlayer>` | `gangland-core/.../user/UserManager.java` (via UserLookupContract) | Member account resolution; gang invites bind to offline players | Core service pulled by GangManager |
| gang | core | Downed | `DownedPlayerRegistry` | `gangland-core/.../downed/DownedPlayerRegistry.java` | `GanglandCombatEligibility.canBeHit()` in civilians module checks registry | Gang features don't depend on downed state; civilians (which turf depends on) does |

---

## Critical Seams to Preserve / Extend

### 1. **GangMembership: Always-Present Holder**
   - **Why:** Gadget, cops-n-crooks, and civilians read gang facts without declaring `Depends: [gang]`.
   - **Current Form:** Core-owned (`gangland-api`), installed by gang module at startup.
   - **Seam Boundary:** `GangMembership.isInstalled()` lets callers gracefully degrade if module absent.
   - **Redesign Rule:** Gang map expansion must preserve the holder contract:
     - Callers invoke `membership.gangIdOf(uuid)`, `membership.gangsAllied(idA, idB)`, `membership.nameOf(gangId)`.
     - Redesign may add methods (e.g., `gangTerritoryOf(gangId)`, `gangAllianceChainOf(gangId)`) additively.
     - Never remove/rename existing methods or change return types.

### 2. **GangLookupContract: Turf Capture Dependency**
   - **Why:** `CaptureService` needs gang ownership to drive capture state machine.
   - **Current Form:** `findById(int gangId) → Gang`; wired into `CaptureService` constructor.
   - **Seam Boundary:** `GangLookupContract` is a read-only contract (no mutations).
   - **Redesign Rule:** Capture redesign (e.g., adding map grid display) may query:
     - Gang owner ID at turf location
     - Ally list of owner (for friendly-fire, garrison, siege mechanics)
     - Gang treasury/power for turf income
     - Gang color/display name for HUD rendering
     - All additive; never remove `.findById()`.

### 3. **GangAllianceRepositoryContract: Alliance Graph**
   - **Why:** Turf, mail, and cops query alliance relationships; income distribution, siege eligibility.
   - **Current Form:** `gangsAllied(int gangIdA, int gangIdB) → boolean` (bidirectional).
   - **Seam Boundary:** Read-only; used to check "are these gangs allies" for combat/siege/income.
   - **Redesign Rule:** Map redesign may query alliance chains (e.g., "who is allied with X at this turf"), but:
     - Keep `gangsAllied()` as the atomic check (many callers already use it).
     - Add new read methods to the contract (never remove old ones).
     - Alliances remain immutable once set; changes go through commands only.

### 4. **TurfManager: Region Lookup & State**
   - **Why:** Cops-n-crooks needs to know which turf a player is in and who owns it.
   - **Current Form:** `get(UUID turfs) → Turf`; `TurfManager.get(player location)` for proximity query.
   - **Seam Boundary:** Read-only for cops; capture state managed by turf module only.
   - **Redesign Rule:** Map redesign may add methods:
     - `getTurfGrid()` or `getTurfsInRegion(region)` (for map chunk rendering)
     - `getTurfsByOwner(gangId)` (for gang territory summary)
     - Never mutate turf state from outside turf module.

### 5. **CivilianService & CivilianSpawnManager: NPC Spawning**
   - **Why:** Turf garrison defenders are civilians NPCs; cops-n-crooks spawns police NPCs sharing the framework.
   - **Current Form:** `CivilianService` registry; `CivilianSpawnManager` wires Citizens integration.
   - **Seam Boundary:** Turf calls `garrisonDeployer.deploy(location, gang)` → internally spawns civil NPCs.
   - **Redesign Rule:** NPC map representation may add:
     - NPC location rendering on map (via `CivilianSpawnManager.getAll()`)
     - Garrison squad composition display (via updated `Garrison` entity)
     - Squad pursuit tactics (Keystone 1.13 `NpcSquad` / `AbstractNpc.pursue()`)
     - All additive; never remove Citizens integration or `CivilianService` bean.

### 6. **CommandContribution Pattern: Mail Extends Gang**
   - **Why:** Mail module adds `/glw gang invite`, `/glw gang accept` without editing gang module.
   - **Current Form:** `CommandContribution` bean registered in `MailModuleConfig`; parent path "gang"; creates `GangInviteCommand` tree.
   - **Seam Boundary:** Query-based: `GangCommand.queryContributions("gang")` collects all extensions.
   - **Redesign Rule:** Gang map feature may add new turf-related commands via contributions:
     - Turf could contribute `/glw turf siege` (attack another gang's turf) via similar seam.
     - Never edit core `GangCommand`; use contributions for module-owned sub-commands.

### 7. **CombatEligibility: Downed Player Protection**
   - **Why:** Civilians module owns NPC combat; turf garrison defenders and cops both hit NPCs.
   - **Current Form:** `CombatEligibility.canBeHit(player) → boolean`; checks `DownedPlayerRegistry`.
   - **Seam Boundary:** Cops-n-crooks and turf both respect the same eligibility check.
   - **Redesign Rule:** Siege mechanics may extend eligibility checks (e.g., "can be hit only if in defending turf"), but:
     - Keep the interface stable; add methods (never change `.canBeHit()` signature).
     - Eligibility is read-only; state lives in DownedPlayerRegistry (core) and turf data (turf module).

### 8. **CarGangContract: Gadget Access Policy**
   - **Why:** Gadget stays gang-free; reads gang membership only via `CarGangContract` abstraction.
   - **Current Form:** Functional interface; default impl `GanglandCarGangs` uses `GangMembership.gangIdOf()`.
   - **Seam Boundary:** `CarAccessPolicy` invokes contract to allow/deny player access to a car.
   - **Redesign Rule:** Map redesign may swap policy (e.g., "only members of turf-owner gang") via bean:
     - Create new `CarGangContract` impl in turf or gadget (never in gang).
     - Wire it into `GadgetModuleConfig.carAccessPolicy()` instead.
     - Gadget stays decoupled; policy becomes pluggable.

### 9. **Mail → Gang CommandContribution: Alliance Requests**
   - **Why:** Alliance requests are part of gang relations; mail module manages inbox.
   - **Current Form:** `GangAllyMailContribution` provides `/glw gang ally request|pending|accept|reject|abandon`.
   - **Seam Boundary:** Decisions (accept/reject/abandon) update `GangAllianceRepositoryContract` via `GangManager`.
   - **Redesign Rule:** Map redesign may affect alliance mechanics (e.g., turf-owners auto-ally if neighbors), but:
     - Always route alliance changes through `GangManager.ally()` / `GangManager.unally()`.
     - Mail module remains the UX layer; gang module remains the data layer.

---

## Seams by Category

### **Read-Only Contracts (Safe to Extend Additively)**
| Contract | Module | Readers | Extensibility |
|----------|--------|---------|---|
| GangLookupContract | gang | turf (capture) | Add read methods (e.g., .getTerritoryOf) |
| GangAllianceRepositoryContract | gang | turf, cops, mail | Add methods (e.g., .getAllies(gangId)) |
| TurfManager (read) | turf | cops (location query) | Add query methods (e.g., .getTurfsByOwner) |
| CombatEligibility | civilians | turf, cops | Add conditions (e.g., .canBeHitByOutsiders) |
| CarGangContract | gadget | gadget (access check) | Already pluggable |
| GangMembership | api (gang installs) | turf, cops, gadget | Add methods (e.g., .gangTerritoryOf) |

### **Write/Command Contracts (Strictly Owned)**
| Contract | Owner | Mutators | Access Boundary |
|----------|-------|----------|---|
| GangManager | gang | gang commands, mail contributions | Only gang module commands mutate |
| TurfManager (state changes) | turf | turf capture service, commands | Only turf capture updates state |
| CivilianService (spawn/despawn) | civilians | turf (garrison), cops (police) | Lifecycle bound to gang/capture |
| MailManager | mail | mail commands | Inbox mutations are mail-owned |

### **Module-to-Module Message Interfaces**
| Path | Type | Mechanism | Example |
|------|------|-----------|---------|
| mail → gang | Command | `CommandContribution` + `.create()` tree | GangMailContribution extends /glw gang |
| turf ← gang | Data pull | Contracts (GangLookupContract, GangAllianceRepositoryContract) | CaptureService queries GangLookupContract.findById |
| cops ← turf | Data pull | TurfManager for location/owner query | TurfFriendlyFireListener.resolveTurfId() |
| gadget ← gang | Data pull | GangMembership (always-present api holder) | CarAccessPolicy.canUse() calls membership |
| turf ← civilians | Service use | CivilianService for NPC spawning | TurfDefenderDeployer.deploy() |

---

## Degradation Modes (When Modules Are Absent)

| Missing Module | Dependent Modules | Behavior | Recovery |
|---|---|---|---|
| gang | mail, turf, gadget, cops | mail: never loads (Depends: [gang]); turf: garrison deploys but no owner-gang affiliation (silent fail); gadget: car access is `GangMembership.isInstalled()` check = fails open (anyone can access); cops: TurfFriendlyFireListener throws NPE if TurfManager queried | Restart with gang |
| civilians | turf, cops | turf: garrison feature skipped (TurfDefenderDeployer constructor fails); cops: no police NPC spawning | Restart with civilians |
| turf | cops | cops: TurfFriendlyFireListener throws; console errors flood on first damage | Restart with turf |
| Bartizan | gadget, cops, civilians | graceful: gadget loses weapon-damage routing (cars can't shoot); cops/civilians lose NPC weapon holds (NPCs unarmed) | No restart needed, features degrade |

---

## Reserved Seam Names & Patterns

**CommandContribution Query Paths** (used by CommandManager to find module extensions):
- `"gang"` → GangMailContribution, GangAllyMailContribution (mail module)
- `"gang.ally"` → GangAllyMailContribution sub-paths
- `"turf"` → reserved for future turf-module command extensions
- `"bank"` → npcshops module (for trading)

**Bean `@Qualifier` Names** (used in constructor injection):
- `@Qualifier("online")` → UserManager<Player> (online players)
- `@Qualifier("offline")` → UserManager<OfflinePlayer> (offline/historical)
- Others: named by module convention (e.g., `@Qualifier("turf")` for turf-specific beans)

**Contract Interfaces** (in separate `contract/` packages, read by other modules):
- Always suffix with `Contract` (e.g., `GangLookupContract`, `TurfRepositoryContract`)
- One interface per logical seam; never mix unrelated reads into one contract
- Interfaces live in owning module; callers import them via dependency

---

## Recommendations for Gang/Turf Map Redesign

### Don't Break These:
1. **Keep `GangMembership.install()` ceremony** — installed by gang module at `@PostConstruct`, read by gadget without Depends.
2. **Keep `GangLookupContract.findById()`** — turf capture is hardcoded to look up owner; it's a contract, not implementation.
3. **Keep `TurfManager.get(location)`** — cops-n-crooks queries this to find turf owner for friendly-fire checks.
4. **Keep `CommandContribution` for mail extensions** — reuse the pattern for new turf commands (e.g., siege requests).
5. **Keep `CivilianService` bean** — turf and cops both depend on the same NPC framework.

### Safe Additions:
- `GangMembership.gangTerritoryOf(gangId)` → new method returning list of turfs owned
- `TurfManager.getTurfGrid()` → chunk-based grid for map rendering
- `TurfManager.getTurfsByOwner(gangId)` → reverse lookup (gang → turfs)
- `GangAllianceRepositoryContract.getDirectAllies(gangId)` → new read method
- New `TurfMapDisplayContract` — pluggable map rendering (for HUD / minimap / web)
- New `GangTerritoryContribution` — extends turf commands with territory viewing (analogous to mail extending gang)

### Red Flags (Avoid):
- Don't add a new `GangModule2` or `TurfRefactorModule` — extend the existing modules with new commands/contributions.
- Don't move `GangMembership` to gang module (breaks gadget/cops decoupling).
- Don't remove `GangLookupContract` — replace its implementation, not the seam.
- Don't hardcode turf owner checks in cops module — keep using `TurfManager` contract.

---

## Summary: Seam Count & Density

| Type | Count | Examples |
|------|-------|----------|
| Read-only contracts | 6 | GangLookupContract, TurfManager, CombatEligibility |
| Service integrations | 4 | CivilianService, MailManager, GangManager (read), UserManager |
| Data holders | 2 | GangMembership, Turf entity |
| Command extensions | 2 | GangMailContribution, GangAllyMailContribution |
| Functional policies | 1 | CarGangContract |
| **Total seams** | **15** | Turf capture: 6 seams; Cops integration: 5 seams; Gadget integration: 2 seams; Mail integration: 2 seams |

---

## References

- **Graphify queries used:** GangLookupContract, GangMembership, GangAllianceRepositoryContract, TurfModuleConfig, CarGangContract, NpcMarkManager, GanglandCombatEligibility, TurfFriendlyFireListener
- **Files inspected:** 
  - `gangland-turf/capture/CaptureService.java` L50–70
  - `gangland-turf/TurfModuleConfig.java` L52–120
  - `gangland-gadget/car/access/GanglandCarGangs.java` L1–34
  - `gangland-mail/command/GangMailContribution.java` L1–60
  - `gangland-api/data/gang/GangMembership.java` L1–73
  - All `module.yml` files across gangland-features
- **Keystone contracts (external):** UserLookupContract, UserManager (core)
- **Keystone base classes:** AbstractNpc, NpcSquad (for phase H11+ squad tactics)
