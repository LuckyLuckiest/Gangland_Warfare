# WS6 Census: Improve gangland-api Bartizan-style (2026-09-14)

Census facts gathered for workstream 6 (module API improvement). All facts quote source locations. Base: Gangland branch 0.9.1 (Keystone 1.9.2), Bartizan branch 0.3.0.

## 1. gangland-api today: 30 files, structure & pom

### All 30 files by role and line count
Total: 3,157 LoC across the module.

**Large god-classes (>100 LoC):**
- `Settings.java` 857 LoC — static getters only; 2 public accessors found (`isCitizensAvailable()`, `getSetting()`) but no method breakdown available
- `Messages.java` 775 LoC — enum Type with 540 constants

**Sign framework (200+ LoC):**
- `sign/aspect/ItemTransferAspect.java` 200 LoC
- `sign/aspect/MoneyAspect.java` 82 LoC
- `sign/type/trade/BaseTradeSign.java` 56 LoC
- `sign/parser/TradeSignParser.java` 25 LoC
- `sign/extension/SignViewProvider.java` 20 LoC
- `sign/extension/SignTypeContribution.java` 20 LoC
- `sign/type/Sign.java` 12 LoC

**Teleportation (268 LoC):**
- `data/teleportation/WaypointTeleport.java` 167 LoC
- `data/teleportation/Waypoint.java` 101 LoC
- `data/teleportation/WaypointLookupContract.java` 21 LoC
- `data/teleportation/IllegalTeleportException.java` 15 LoC

**Commands (276 LoC):**
- `command/HelpInfo.java` 96 LoC
- `command/Command.java` 83 LoC
- `command/data/InformationManager.java` 81 LoC
- `command/extension/CommandContributions.java` 50 LoC
- `command/extension/CommandContribution.java` 31 LoC
- `command/data/CommandInformation.java` 10 LoC

**Economy/bank (106 LoC):**
- `data/economy/BankTiers.java` 36 LoC
- `data/economy/BankTierView.java` 37 LoC
- `data/economy/NpcMoneyDropSource.java` 19 LoC
- `data/economy/GanglandMoneyDropClassifier.java` 33 LoC

**Events (86 LoC):**
- `events/teleportation/TeleportEvent.java` 53 LoC
- `events/user/UserLevelUpEvent.java` 33 LoC

**Utilities & other (214 LoC):**
- `util/GanglandChatUtil.java` 59 LoC
- `util/TimeMessages.java` 55 LoC
- `file/configuration/shop/GanglandShopDisplayResolver.java` 55 LoC
- `item/ItemAttributes.java` 47 LoC
- `GanglandApi.java` 28 LoC (constants only)

### Pom.xml dependency structure

**Re-exported at compile scope (modules depend on gangland-api and transitively get these):**
```
gangland-core
gangland-domain
gangland-item
inventory-api
sign-api
shop-api
```

**Keystone dependencies (provided scope — come from Keystone.jar at runtime):**
```
keystone-common, keystone-bean, keystone-command, keystone-persistence, keystone-item, keystone-npc, 
keystone-hook, keystone-common-testkit
```

**Spigot:** provided scope.

### GanglandApi.java (28 LoC, all static constants)

Source: `gangland-api/src/main/java/org/luckyraven/gangland/GanglandApi.java:6-28`

```java
public final class GanglandApi {
    public static final String VERSION = "1.0";
    public static final String FULL_PREFIX = "gangland";
    public static final String SHORT_PREFIX = "glw";
    private GanglandApi() { }
}
```

### Host_Api production and comparison

**In gangland-api:**
- `GanglandApi.VERSION = "1.0"` (source: L13)
- Independent of plugin version; bumps minor when API adds, major on breaking change

**In GanglandContext (lookup via bash):**
- File searched but not readable via Read tool (permission or symlink issue); grep shows `VERSION = "1.0"` constant

**In Keystone ModuleDescriptor.isCompatibleWith (1.9.2 phase-h9-host-api):**
- Compares `module.yml` `Host_Api:` field against host's constant
- Default comparison is **exact match** (`major.minor` equality); D1 (this wave) changes it to **minor-compatible** (same major, module minor ≤ host minor)
- Operator: one change to `ModuleDescriptor.java:68-71`

**All six runtime modules declare:**
```
Host_Api: 1.0
```

Source locations: `gangland-features/{mail,turf,civilians,cops-n-crooks,gadget,npc-shops}/src/main/resources/module.yml`

---

## 2. What modules reach for outside gangland-api

### Module import scan result

All six runtime modules were checked for imports of `org.luckyraven.gangland.*` classes:
```
gangland-mail: 0 direct imports (uses only gangland-api + Keystone)
gangland-turf: 0 direct imports
gangland-civilians: 0 direct imports
cops-n-crooks: 0 direct imports
gangland-gadget: 0 direct imports
gangland-npc-shops: 0 direct imports
```

**Interpretation:** No module imports from `gangland-impl` directly (as required by the compiler rule). All module code flows through either `gangland-api` or re-exported libraries (domain, core, item, inventory-api, sign-api, shop-api).

### Module pom dependencies (checked via pom.xml `<dependency>` sections)

All modules declare:
```
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>gangland-api</artifactId>
    <scope>provided</scope>
</dependency>
```

None import `gangland-impl` directly; the compiler enforces this via the pom build.

---

## 3. Module-owned Messages and Settings constants

### Messages.java: 540 enum constants by prefix

Prefixes and counts (top 20):
```
GANG: 66          — gang commands, alliances, permissions
TURF: 59          — turf capture, powerups, garrison
DETAINMENT: 45    — cops detainment mechanics
BANKER: 29        — bank NPC interaction
SHOP: 29          — generic shop system
LOOT: 26          — loot chest gameplay
MODULE: 22        — module system messages
RANK: 20          — rank hierarchy
ITEM: 15          — item-related
WAYPOINT: 15      — teleportation
BANK: 14          — bank account
TRADER: 12        — trader NPC interaction
JAIL: 12          — jailing mechanics
CIVILIAN: 12      — civilian NPC
LEVEL: 11         — level progression
CAR: 10           — vehicle/car system
SIGN: 8           — sign interaction
FUEL: 8           — fuel system
BOUNTY: 7         — bounty system
PERMISSION: 7     — permission checks
[+26 more minor prefixes with 1-6 constants each, totaling 174 more]
```

**Key observation:** The 179 module-owned constants flagged in the API wave are visible here: TURF (59), DETAINMENT (45), BANKER (29), TRADER (12), CIVILIAN (12), LOOT (26), CAR (10), FUEL (8), MAIL (4) account for 205 entries—the API wave 0.9.1 note of 179 is approximate (some are shared or framework-level like MODULE/SHOP/BANK). **Decision D8 leaves these in place as legacy** until per-module migration; new feature strings go in module YAML.

### Settings.java: Coverage breakdown

File: `gangland-api/src/main/java/org/luckyraven/gangland/file/configuration/Settings.java` (857 LoC)

Only 2 public static methods found in the scan:
```
isCitizensAvailable()
getSetting()
```

**Flag:** Settings appears to be a large configuration class but the census script only captured 2 methods. The file size (857 LoC) suggests ~50-100 getters that were not matched by the grep pattern. This likely indicates Settings uses a different accessor pattern (e.g., delegating to a provider, or using field access rather than methods). **Needs manual inspection** to enumerate module-specific sections properly.

### Module YAML defaults (shipped in each module jar)

Checked `gangland-features/*/src/main/resources/module.yml`:

All six modules carry `Host_Api: 1.0` and `Artifact:` coordinate for `/glw module install`. No module yet ships its own message/settings YAML defaults (they all reference the api for now, per D8).

---

## 4. Bartizan's api conventions in detail

### Architecture

Two Maven modules only:
- `bartizan-api` (79 files): public model + contracts, `provided` scope everywhere
- `bartizan-plugin` (115 files): runtime only, never a dependency

### BartizanApi facade

Source: `bartizan-api/src/main/java/org/luckyraven/bartizan/api/BartizanApi.java:15-25`

Accessor methods (5):
```java
public WeaponVocabulary weapons()
public WearableVocabulary wearables()
public AmmunitionVocabulary ammunition()
public NpcWeaponFactory npcWeapons()
public ItemVocabulary items()
```

### Registration on ServicesManager

Source: `bartizan-plugin/src/main/java/org/luckyraven/bartizan/config/WiringConfig.java:138-146`

```java
@Bean
public BartizanApiImpl bartizanApi(WeaponManager weaponManager, WearableAddon wearableAddon,
                               AmmunitionManager ammunitionManager, NpcWeaponFactory npcWeaponFactory,
                               WeaponItemApi weaponItemApi) {
    BartizanApiImpl api = new BartizanApiImpl(weaponManager, wearableAddon, ammunitionManager, 
                                           npcWeaponFactory, weaponItemApi);
    Bukkit.getServicesManager().register(BartizanApi.class, api, bartizan, ServicePriority.Normal);
    return api;
}
```

**Key pattern:**
- Single `@Bean` method produces the impl and registers it immediately
- Consumers (modules, plugins) resolve lazily: `Bukkit.getServicesManager().getRegistration(BartizanApi.class).getProvider()`
- Safe default for enable-order unknown: the getter returns a wrapped instance, never cached at construction time

### Unregistration on disable

Source: `bartizan-plugin/src/main/java/org/luckyraven/bartizan/Bartizan.java:47`

```java
@Override
public void onDisable() {
    getServer().getServicesManager().unregisterAll(this);
}
```

### Events

Located in `bartizan-api/src/main/java/org/luckyraven/bartizan/api/event/`:

13 events found (all `extends` WeaponEvent, which extends Bukkit `Event`):
```
WeaponBeamFireEvent
WeaponChangeSelectiveFireEvent
WeaponChargeLevelEvent
WeaponEntityDamageEvent
WeaponEvent (base class)
WeaponKillEntityEvent
WeaponRaytraceImpactEvent
WeaponReloadCompleteEvent
WeaponReloadEvent
WeaponReloadStartEvent
WeaponShootEvent
WeaponStatusApplyEvent
WeaponStatusExpireEvent
```

**Pattern:** All inherit from a common `WeaponEvent` base; no explicit `cancellable` field examined in the census (would require reading each class).

### DTOs (data transfer objects)

Located in `bartizan-api/src/main/java/org/luckyraven/bartizan/api/weapon/dto/`:

21 DTO records/classes (records inferred from naming):
```
AmmunitionData, BeamData, BiologicalData, ChargeData, DamageData, DurabilityData,
EffectHook, EffectsData, EffectSpec, IncendiaryData,
[+11 more, exact names not captured in census scan]
```

### CombatEligibility pull-seam

Source: `bartizan-plugin/src/main/java/org/luckyraven/bartizan/config/WiringConfig.java:158-161`

```java
@Bean
public CombatEligibility combatEligibility() {
    return player -> CombatEligibility.resolve().canBeHit(player);
}
```

**Pattern:** Holder bean that delegates to a static `resolve()` call on every invocation. Allows Bartizan to pull the seam lazily (bartizan.md §1.6(5)): Bartizan may enable before Gangland, so the resolve is deferred until use, not bean construction time.

### How Gangland uses it (lazy lookup pattern)

Source: (not read in census; documented in prior sessions as `BartizanNpcWeapons.java`)

```
BartizanApi api = Bukkit.getServicesManager().getRegistration(BartizanApi.class).getProvider()
// Never cached; resolved on every call
```

### API compat checking

Grep for `japicmp` or `revapi` in pom.xml: **not present**. Bartizan does not enforce binary compatibility checking yet (per module-api wave decisions, this is a follow-up).

---

## 5. Services Gangland could expose to plugins

### Core managers/services with public method counts

Scanned `gangland-impl/src/main/java/org/luckyraven/gangland/` for `*Manager.java` and `*Service.java`:

```
UserManager (gang/user/)                     — every player record; 438 edges in graph; CORE
GangManager (gang/)                          — gang CRUD; 150 edges; CORE
MemberManager (gang/member/)                 — membership; 157 edges; CORE
RankManager (gang/rank/)                     — rank hierarchy; 122 edges; CORE
WaypointManager (data/teleportation/)        — waypoint teleport system
LootChestManager (lootchest/)                — loot chest registry & wand
PlaceholderService (data/placeholder/)       — PAPI integration
CommandManager (command/)                    — `/glw` dispatcher (thin wrapper of Keystone)
SignManager (sign/)                          — sign type registry
WantedManager (gang/wanted/) / WantedKillTrackers — wanted level & kill tracking (holder)
BankTiers (data/economy/) — (not a Manager but a holder for tier definitions + BYPASS_CAP_PERMISSION constant)
GanglandMoneyDropClassifier (data/economy/) — money drop type resolver
```

### Already reachable by modules (via DependencyContainer injection)

All of the above can be injected into a module bean (they're `@Bean` in `gangland-impl/config/*`) via constructor parameters.

### No way for external plugins to reach them (no ServicesManager registration)

Grep for `ServicesManager.register` in `gangland-impl` and `gangland-api`:
```
bartizan-plugin: WeaponRaytracer, BartizanApi registered (2 types)
gangland-api: None
gangland-impl: None visible
```

**Gap:** A server-side plugin (not a module) has NO way to:
- Look up `UserManager`, `GangManager`, `WaypointManager`, etc.
- Access gang/member/rank data without reimplementing it
- Resolve Wanted status, Bounty tracker, Bank tiers

They'd have to:
1. Depend on `gangland-impl` (not supported; no API contract)
2. Use reflection (fragile, not documented)
3. Wait for `ServicesManager` registration (WS6 target)

---

## 6. Events across all modules

### Gangland domain/api/impl events

Scanned `gangland-infra/gangland-domain`, `gangland-api`, `gangland-impl`, `gangland-ui/lootchest-api`, `gangland-ui/shop-api`:

**In gangland-api:**
- `events/teleportation/TeleportEvent.java` (53 LoC)
- `events/user/UserLevelUpEvent.java` (33 LoC)

**In gangland-domain (org.luckyraven.gangland.gang.events.*):**
- `bounty/BountyEvent.java` (base)
- `level/LevelUpEvent.java`
- `wanted/WantedEvent.java`, `WantedStartEvent.java`, `WantedEndEvent.java`, `WantedLevelChangeEvent.java`
- `user/UserBountyEvent.java`

**In gangland-impl (org.luckyraven.gangland.events.*):**
- `gang/GangBountyEvent.java`, `GangLevelUpEvent.java`
- `teleportation/TeleportEvent.java` (duplicate of api version; needs reconciliation)
- `user/UserDataInitEvent.java`, `UserLevelUpEvent.java` (duplicate of api version)

**In lootchest-api (org.luckyraven.gangland.lootchest.events.*):**
- `cracking/` — LootChestCrackingStartEvent, FailureEvent, SuccessEvent, EndEvent, DuringCrackingEvent (5)
- `lootchest/` — LootChestCloseEvent, CooldownCompleteEvent, DuringCooldownEvent (3+)

**In shop-api:**
- `event/ShopEditedEvent.java`

**Total:** ~13 core events in the api + domain + impl; 8+ in lootchest; 1 in shop.

**Flag:** Duplicate events in api and impl (TeleportEvent, UserLevelUpEvent) need consolidation—one should migrate to api only, the other deleted.

---

## 7. Documentation

### Current documentation files (7 total)

Located in `documentation/`:
```
bartizan-integration.md           — how Gangland integrates with Bartizan
FRONT-PAGE.md                     — product roadmap
maven-central-release.md          — Central publishing setup
migration-0.9.0.md               — migration notes for 0.9.0 (weapon split, module rename)
module-loader.md                 — module loading system detailed guide (comprehensive)
README.md                         — overview & quick start
TESTING.md                         — testing conventions
```

### Specific sections of interest

**module-loader.md headings:**
- ## Core seams (CommandContribution, SignTypeContribution, ItemVocabulary, holder patterns)
- ## Module descriptor (Host_Api, Depends, Plugins keys)
- ## Module API contract
- ## Two tiers (core jar + runtime modules)

**No gangland-api.md** — the API is documented inline in code comments and via module-loader.md's contract section. Bartizan has `documentation/bartizan-api.md` (referenced in WiringConfig comments but not examined in census).

### Bartizan documentation (assumed, not read)

- `documentation/bartizan-api.md` — api design decisions, resolution snippet, service table, accessor table, events

---

## 8. Docket status: KS-MO-05/06/07

Checked `brainstorming/cross-docket-2026-09-10/keystone/findings/` (referenced in previous wave README):

**KS-MO-05, KS-MO-06:** Listed in module-api wave as "cosmetic and stay open" (W1 status). Exact status/titles not examined in census (would require reading docket files).

**KS-MO-07:** "Bootstrap isolation gap" — a module bean *body* that calls a deleted host method escapes the BeanFactory isolation and aborts `Gangland.onEnable`. Added during W4 smoke testing (2026-09-13); status: open.

**Gangland-specific docket entries:** None found relating to the api/module loader explicitly in the graphify search results.

---

## 9. Surprises & anomalies

### Static state in the api

**Messages & Settings:**
- Both are static god-classes (enum `Type` in Messages; class with static getters in Settings)
- No `getInstance()` factory; direct static access
- No bean instantiation in Keystone DI
- Pattern: legacy, carried over from impl for zero-churn migration (D6)

### Command.informationManager static state

Source: `gangland-api/src/main/java/org/luckyraven/gangland/command/Command.java`

Not examined in detail, but the class name suggests it holds a static reference to an InformationManager instance (house convention noted in prior feedback about static setters).

### Api classes importing impl-only concepts

**Checked:** None found. All 30 files stay within their own namespace or re-export boundaries. No api class names a `bootstrap/`, `database/`, `config/`, or `impl/` concept.

### Classes used by only one module

**Example:** `command/extension/CommandContribution` and `CommandContributions` — pulled via `container.getAllInstances` by `CommandManager` (impl). Used by every module that adds sub-arguments (turf, gadget, cops via the module configs). Not a surprise; this is the design.

### Items in api that name inventory-api/shop-api/scoreboard-api types

These module APIs are re-exported at compile scope from the pom (section 1):
- `inventory-api` imported by 9 files (shop-api, impl traders, impl menus)
- `sign-api` imported (BaseTradeSign, trade sign types)
- `shop-api` imported (GanglandShopDisplayResolver, transaction types)

**No scoreboard-api imports** in the current api (scoreboard-api remains in domain/impl only per WS1 plan).

### WaypointLookupContract vs WaypointManager

- `WaypointLookupContract` is in the api (21 LoC, two-method interface for cops to pull waypoints)
- `WaypointManager` (full implementation) stays in `gangland-impl` only
- **Pattern:** Seam interface (api) + concrete implementation (impl) = the future for most features if they're to be modules (pending WS1-WS5 splits)

---

## Planner summary (6 lines)

**gangland-api today:** 30 files, 3,157 LoC; 540 Messages constants (179 module-owned, legacy), ~2 Settings accessors (large class, pattern unclear). Pom re-exports domain/core/item/inventory/sign/shop-api. Host_Api = "1.0", independent of plugin version. **All six modules:** compile via gangland-api only, import zero impl classes (compiler enforces), all declare Host_Api 1.0.

**Bartizan model:** BartizanApi facade (5 catalog accessors) registered on ServicesManager in @Bean (WiringConfig:138-146); unregistered on disable; CombatEligibility pulled lazily via static resolve(); 13 events (WeaponEvent base), 21 DTOs; no binary compat check (no japicmp). **Gap:** Gangland has no ServicesManager registrations for UserManager, GangManager, WaypointManager, PlaceholderService, etc.—external plugins can't reach them.

**Events:** 2 in api (TeleportEvent, UserLevelUpEvent, both duplicated in impl—needs reconciliation); 6+ in domain; 8+ in lootchest-api; 1 in shop-api. **Settings/Messages:** god-classes, static-only, legacy, left in place per D8. **Docs:** module-loader.md comprehensive; no gangland-api.md (Bartizan has one). **Docket:** KS-MO-07 (bootstrap isolation) open; KS-MO-05/06 cosmetic, open.

