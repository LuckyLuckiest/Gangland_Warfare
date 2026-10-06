# `gangland-api` — the module API and the `GanglandApi` facade

[← Back to Documentation Index](./README.md)

`gangland-api` is the only host artifact a runtime module compiles against (`provided` scope; see the root
`CLAUDE.md`'s "Module API contract" section for the contract rule and versioning). Since WS6 G1 (0.10.0) it also
publishes `GanglandApi` — a Bukkit `ServicesManager` facade an **external plugin** (not a runtime module) resolves
to reach four always-present pieces of Gangland state, modelled directly on Bartizan's own `BartizanApi`.

## Resolving the facade

```java
RegisteredServiceProvider<GanglandApi> rsp = Bukkit.getServicesManager()
        .getRegistration(GanglandApi.class);
GanglandApi api = rsp != null ? rsp.getProvider() : null;

if (api == null) {
    // Gangland is absent, disabled, or hasn't finished enabling yet
    return;
}
```

**Resolve fresh on every call — never cache the reference.** Gangland may disable, reload, or not be installed at
all; a cached reference outlives the registration it came from. This is the same rule Bartizan's own facade
documents for `BartizanApi`.

A **runtime module** never resolves the facade this way — modules already have constructor injection from the
shared `DependencyContainer` (see `documentation/module-loader.md`). `GanglandApi` is exclusively the
external-plugin surface; there is no concrete external consumer of it today (the same honest starting point
Bartizan's own facade had when it shipped).

## The four accessors (final ruling R7, `plans/WS6-api.md` §0c)

| Accessor | Returns | Always present? |
|---|---|---|
| `users()` | `UserLookupContract` | Yes — core-mandatory |
| `gangs()` | `GangMembership` (`org.luckyraven.gangland.data.gang`) | Yes — **never `null`**. Inert (every query answers its documented absent-default: `gangIdOf` → `-1`, `gangsAllied`/`alliedOrSame` → `false`, `nameOf` → empty) until the `gangland-gang` module's `GangMembershipInstaller` calls `install(...)`. Check `isInstalled()` to distinguish "module absent" from "module answered no" |
| `waypoints()` | `WaypointLookupContract` | Yes — core-mandatory |
| `bankTiers()` | `BankTiers` | Yes — already the "optional module" pattern: `tierFor(...)` returns `null` until cops-n-crooks installs a lookup |

There is **no `ranks()` accessor**. `GangLookupContract`/`RankLookupContract` live inside the `gangland-gang`
module (WS5), not in `gangland-api` — the api can never name a module type, so the facade cannot expose them.
Earlier drafts of this plan proposed a fifth accessor returning `Optional<GangLookupContract>`/
`Optional<RankLookupContract>`, resolved lazily from the `DependencyContainer`; R7 replaced that design entirely
with the `GangMembership` holder above, which needed no `Optional` and no container lookup because it is always
a core bean.

## Service table

Every `ServicesManager` registration this reactor touches, publisher and consumer, as of this gate:

| Service class | Registered by | Priority | Consumed by |
|---|---|---|---|
| `org.luckyraven.gangland.GanglandApi` | **Gangland**, `WiringConfig.ganglandApi(...)` (WS6 G1) | Normal | any external plugin (no named consumer today) |
| `org.luckyraven.keystone.placeholder.PlaceholderProvider` | **Gangland**, `WiringConfig.ganglandPlaceholder(...)` (WS1 D5) — `GanglandPlaceholder.asProvider()` | Normal | `<ScoreboardPlugin>`'s `PapiText` (Plaque), when PAPI itself is absent/has no answer |
| `org.luckyraven.keystone.item.spi.ItemVocabulary` | Bartizan (or any item-owning plugin) | Normal | **Gangland core *consumes*, does not publish** — `GanglandContext.installItemVocabularies()` folds every registration in once, after all CONFIG beans exist but before any `@PostConstruct` |
| `org.luckyraven.bartizan.api.BartizanApi` | Bartizan | Normal | Gangland's civilians/cops-n-crooks/gadget modules |
| `org.luckyraven.bartizan.api.raytrace.WeaponRaytracer` | Bartizan | Normal | Bartizan's own controllers; open to third parties |
| `org.luckyraven.bartizan.api.combat.CombatEligibility` | the consumer registers it (Gangland could, doesn't today) | Normal | Bartizan's weapon/reload controllers |

**Not services — container beans instead:**

- `ShopAdminOpener` (`gangland-api`, WS4) — a one-method interface, registered under its interface type via a
  plain `@Bean`/`DependencyContainer` entry, pulled by `TraderModuleConfig.shopViewOpener()`. Never touches
  `ServicesManager`.
- `GangMembership` (`gangland-api`, WS5 R9) — the core-owned holder documented above, installed by the gang
  module's `@PostConstruct`. Also never touches `ServicesManager`.
- `PlaceholderContribution` (`gangland-api/.../data/placeholder/extension`, WS5) and `GangItemSourceContribution`
  (`gangland-api/.../data/gang`, WS5) — library seam interfaces consumed through
  `container.getAllInstances(...)` (the "Contributions" shape, `documentation/module-loader.md`'s Core seams
  section), not facade accessors either.

## Events

`gangland-api`'s `events/` package held exactly three files, across two subpackages, until 0.15.0 added the crime and evasion events (see [Api 2.1](#api-21-0150) below):

| Event | Package | Cancellable | Public api? |
|---|---|---|---|
| `TeleportEvent` | `events.teleportation` | Yes | Yes |
| `UserLevelUpEvent` | `events.user` | Yes | Yes |
| `UserDataInitEvent` | `events.user` | Yes | **No** — physically colocated (it needs `User`, a `gangland-core` type already re-exported here) but not a designed public extension point; it fires around `PlayerBootstrapService`'s internal init timing, an implementation detail, not a stable contract |

**Future-event rule** (unchanged from the plan's original G2 decision): cancellable if fired before the action
completes (`TeleportEvent`, `UserLevelUpEvent` both `implements Cancellable`), not cancellable if fired after (a
notification) — mirrors Bartizan's `WeaponStatusApplyEvent`(cancellable)/`WeaponStatusExpireEvent`(not) split.

No other event lives in `gangland-api`. Every other `*Event` class in the reactor stays with its owning
module/core package and is reachable transitively (`gangland-core`'s general-purpose events — `LevelUpEvent`,
`BountyEvent`, `WantedEvent` family — through `gangland-api`'s existing compile-scope dependency on
`gangland-core`) rather than being promoted into `gangland-api` itself:

- `GangBountyEvent`/`GangLevelUpEvent` moved into the `gangland-gang` module (WS5) alongside the rest of
  `gang.*` — reachable by a module through `Depends: [gang]`, not through this api.
- Loot-chest's 9 events, npc-shops' trader events, turf's 7 capture/enter events, cops-n-crooks' 4 events,
  civilians' 1 event and gangland-item's `PlayerItemInitEvent` all stay module/infra-owned — no named consumer
  outside their own module, so none were promoted (WS3's explicit decline for the loot-chest 9 still stands).

## Api 2.2 (0.15.1)

`GanglandApi.VERSION` is `"2.2"`. cops-n-crooks, gangland-civilians, gangland-turf, gangland-gang, gangland-gadget and
gangland-npc-shops declare `Host_Api: 2.2` because their settings moved out of the core `settings.yml` into their own
YAML and they read them through `MovedSetting`. Modules built for 2.0 or 2.1 still load. Nothing was removed or changed.

| Addition | Package | Kind |
|---|---|---|
| `MovedSetting` | `org.luckyraven.gangland.file.configuration` | One-release bridge. `MovedSetting.of(moduleFile, fileManager, moduleId)`, then `getInt`/`getLong`/`getDouble`/`getBoolean`/`getString`/`getStringList(path, legacyPath, def)` and `getMoney(path, legacyPath, "def")`. Returns the legacy value (with one warning) only when it is set, differs from the default and the module file still holds the default; the legacy value is `settings.yml`'s, or, when `settings.yml` does not set the path, the newest `settings-old*.yml` backup Keystone wrote on upgrade. Otherwise it returns the module value |
| `PlaceholderContribution.resolveSetting(parameter)`, `PlaceholderContributions.resolveSetting(parameter)` | `org.luckyraven.gangland.data.placeholder.extension` | `default` null. A playerless placeholder (a module-owned setting such as `gang_create_fee`); the core asks it for a null-player request before its own settings map |
| `WantedKillTracker.readsComboSettings()` | `org.luckyraven.gangland.core.wanted` | `default` false. A delegate answering true reads `Kill_Combo.Enable`/`.Reset_After` from its own config: the core routes it every counted kill and passes `0` as `resetAfterSeconds` |
| `WantedKillTrackers.routesKills()`, `recordKill(killer, wanted, victim)`, `WantedKillTrackers(BooleanSupplier, IntSupplier)` | same | The core no longer passes the combo settings from `EntityDamageListener`. The legacy suppliers feed only a pre-2.2 delegate. `routesKills(boolean)` and the four-argument `recordKill` stay, deprecated |

## Api 2.1 (0.15.0)

`GanglandApi.VERSION` is `"2.1"`. The cops-n-crooks and gangland-civilians modules declare `Host_Api: 2.1` because they
use the crime bus below; a module that only uses 2.0 types keeps `Host_Api: 2.0` and still loads (same major, module
minor `<=` host minor). Nothing was removed or changed; every row is an addition.

| Addition | Package | Kind |
|---|---|---|
| `CrimeService` | `org.luckyraven.gangland.crime` | Core bean (`WiringConfig.crimeService()`). `commit(player, crimeId, location[, seenByCop, witnesses])` fires `CrimeCommittedEvent` on the main thread and returns false when a listener cancelled it |
| `Crimes` | `org.luckyraven.gangland.crime` | String constants for the crime ids 0.15.0 publishes: `Kill_Player`, `Kill_Civilian`, `Kill_Cop`, `Assault_Cop`, `Assault_Civilian`, `Resisting_Arrest`. Crime ids are plain strings, so a new crime needs no api bump |
| `CrimeCommittedEvent` | `org.luckyraven.gangland.events.crime` | Cancellable; `getPlayer`, `getCrimeId`, `getLocation`, `isSeenByCop`, `getWitnesses` (witnesses is 0 from every 0.15.0 publisher) |
| `WantedEvasionStateEvent` | `org.luckyraven.gangland.events.wanted` | Not cancellable; `getPlayer`, `getState`, `getLevel`, `getSecondsLeft`, `getZoneCentre`, `getZoneRadius` |
| `EvasionState` | `org.luckyraven.gangland.events.wanted` | `SEEN`, `SEARCHING`, `EVADED`, `OFF` |

Also reachable by a module, because `gangland-api` re-exports `gangland-core` at compile scope (they live in core, not in
this artifact, and count as api surface under the same 2.1 bump):

| Type / member | Package | Kind |
|---|---|---|
| `WantedCause` | `org.luckyraven.gangland.core.wanted` | Enum of why a star changed (`CRIME`, `SIGN`, `ADMIN`, `RESTORE`, `DECAY`, `EVASION`, `BRIBE`, `ARREST`, `DEATH`, `UNKNOWN`) |
| `WantedStars` | same | Core bean: `raise`, `drop`, `restore`, `startDecayClock`, `installDecayPolicy`, `suppressStarChat` |
| `WantedDecayPolicy` | same | Functional seam: `handlesDecay(owner, wanted)` |
| `Wanted.setLevel(int, WantedCause)`, `incrementLevel(cause)`, `decrementLevel(cause)`, `reset(cause)` | same | The old no-cause methods stay and mean `UNKNOWN` |
| `getCause()` | `WantedLevelChangeEvent`, `WantedStartEvent`, `WantedEndEvent` | The old constructors stay and pass `UNKNOWN` |
| `MoneyFormula` | `org.luckyraven.gangland.core.money` | `evaluate(formula, variables, fallback)` never throws; `userVariables(user)` |
| `Bounty` posted/notoriety members | `org.luckyraven.gangland.core.bounty` | `getPostedAmount`, `getPostedAmountExcluding`, `getNotoriety`, `addNotoriety`, `claimPosted`, `serializeLedger`, `restoreLedger`, `restoreSaved` |
| `WantedKillTracker.exemptsKill(killer, victim)`, `appliesComboSwitch()` | `org.luckyraven.gangland.core.wanted` | `default` methods answering false. A delegate that exempts a kill (the heat tracker: defending your own turf) stops the core's kill notoriety too; one that applies `Wanted.Kill_Combo.Enable` itself gets every counted kill, while a pre-0.15.0 delegate keeps the core's combo gate |
| `Settings.isWantedTakeMoneyEnabled()`, `getWantedTakeMoneyFormula()`, `isBountyPayNotoriety()` | `org.luckyraven.gangland.file.configuration` | New `settings.yml` getters (`Wanted.Take_Money.Enable`, `.Formula`, `Bounty.Pay_Notoriety`) |
| `WantedClearContract.clearWanted(UUID, WantedCause)` | cops-n-crooks seam | A `default` method delegating to the one-argument form |

0.15.0 also fires two events that existed but were never sent: `CopDeathEvent` and `KillComboEvent` (now with a
`Kind`), both owned by cops-n-crooks, not by this api.

---

## What Gangland does not expose through the facade

- **`wanted()`/`bounty()`** — no existing lookup contract for either; nothing to wrap.
- **`placeholders()`** — `PlaceholderService` has zero external consumers; `PlaceholderProvider` (above) is
  already the public path for that need.
- **`shopRegistry()`** — WS4 explicitly recommended against it; no named consumer.
- **`modules()`/`ModuleInfo`** — cut (S1, 2026-09-14 review): `/glw module list` already exists as a command;
  nothing consumes a programmatic module listing.

## Api contract: within `2.0`, only additions

Until 0.15.0 `GanglandApi.VERSION` stayed `"2.0"` (the branch's own major bump, G0, before WS1) through every gate of this
plan. Everything WS6 G1/G2 added to the public surface, in order:

| Addition | Kind |
|---|---|
| `GanglandApi` becomes an `interface` (was `final class`) | Widening — every existing static-constant read (`VERSION`/`FULL_PREFIX`/`SHORT_PREFIX`) still compiles, confirmed reactor-wide (31 call sites, 25 files) |
| `UserLookupContract users()` | New method |
| `GangMembership gangs()` | New method |
| `WaypointLookupContract waypoints()` | New method |
| `BankTiers bankTiers()` | New method |
| `GanglandApiImpl` (`gangland-impl`) | New class, the facade's sole implementation |

No removal, rename or signature change — the contract rule (`CLAUDE.md`'s "Module API contract" section) holds.

## Follow-ups

- `japicmp` wiring — deferred until a second `2.x` release of `gangland-api` exists on a resolvable repository
  (nothing to diff against yet).
- `LocalizedModuleYaml` (WS6 G3) has one consumer (`CivilianMessages`) — `GanglandLootChestMessages`/
  `JetpackMessages` still carry their own copy of the same logic; migrating them onto the shared base is a
  follow-up, not executed by this plan.

## See also

- [`documentation/module-loader.md`](./module-loader.md) — the module-facing side (constructor injection, Core
  seams) this facade deliberately does not duplicate.
- [`documentation/migration-0.10.0.md`](./migration-0.10.0.md) — the WS6 section covering both G3 (civilians
  message migration) and G1/G2/G4 (this facade).
