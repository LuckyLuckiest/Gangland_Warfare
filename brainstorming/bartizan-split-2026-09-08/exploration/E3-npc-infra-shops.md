<!-- E3 · feature-dev:code-explorer (opus) · 2026-09-08 · NPC base → keystone-npc, Citizens soft-dep, turf NPCs (D5), trader/banker split (D6), NPC combat vs Bartizan. The agent ran before README.md existed; decisions D1–D10 are in README.md. -->

# E3 — NPC base, Citizens soft-dep, turf NPCs, trader/banker split

All paths below are under `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/` unless stated.

## 1. The generic NPC base — file-by-file cut

### 1.1 `npc/AbstractNpc.java` (453 lines)

Public API: `NPC npc` (`@Getter`, `:28`), `Location spawnLocation` (`:30`), `int aiTickRate` (`:31`, from `NpcNavigationConfig.getAiTickRate()` `:57`), **`Weapon heldWeapon`** (`:37`), `boolean reloading` (`:38`), **`JavaPlugin plugin` (`:39`, smuggled in via `setHeldWeapon`)**, `NpcDifficulty difficulty` (`:42`), `markedForRemoval`/`despawnTicks`/`pursuitTicks`/`attackCooldown` (`:45-52`), ctor `(NPC, Location, NpcNavigationConfig, NpcDifficulty)` (`:54-68`, creates both delegates), abstract `canUseWeapons()`, `getAttackDamage()`, `equip()`, `cleanupTransientState()` (`:75-90`), `setHeldWeapon(Weapon, JavaPlugin)` (`:97`), `markForRemoval`/`getEntity`/`isValid`/`destroy(EntityMarkManager)`/`destroy()` (`:107-154`), `isUsingRangedWeapon()` (`:159`), `shouldHoldPursuitPosition`/`canAttack`/`attack(Player)`/`attackEntity` (`:169-198`), `distanceTo`/`hasLineOfSight` (`:205-258`), navigation forwards (`:274-351`), protected tick helpers (`:358-434`), `ensureDamageable()` (`:441-452`).

Citizens: `net.citizensnpcs.api.npc.NPC` only (`:6`): `getEntity()` (`:115,:122,:138,:446`), `isSpawned()` (`:122,:137`), `despawn()` (`:141`), `destroy()` (`:145`), `isProtected()/setProtected(false)` (`:442-443`). Gangland deps: exactly two — `npc.entity.EntityMarkManager` (`:12`, only in `destroy(EntityMarkManager)` `:131-147`) and `weapon.Weapon` (`:13`).

Cut: moves to `org.luckyraven.keystone.npc.AbstractNpc` after (1) `heldWeapon`/`reloading`/`setHeldWeapon` → an SPI field (§1.11) and **`plugin` becomes a constructor parameter** (`CopNpcFactory.java:100-102` calls `setHeldWeapon(null, plugin)` only to wire the plugin — that hack must not survive); (2) `destroy(EntityMarkManager)` → `destroy(Consumer<Entity> onDespawn)` or a `NpcMarkRegistry` type.

### 1.2 `npc/NpcCombatDelegate.java` (374 lines) — the hard one

Package-private `final class` (`:31`), same-package field access to `owner.heldWeapon/attackCooldown/reloading/plugin/difficulty/npc` (`:26-28`). API: `attack` `:50`, `attackEntity` `:81`, `canAttack` `:105`, `decrementAttackCooldown` `:113`, `faceTarget` `:119`, `faceTargetEntity` `:130`, `refreshHeldItem` `:143`, `isHoldingVanillaRangedWeapon` `:160`, `performGanglandWeaponAttack` `:175`, `triggerReload` `:193`, `performVanillaRangedAttack` `:200`, `performMeleeAttack` `:222`, `performMeleeAttackOnEntity` `:241`; private `performSingleShot/AutoShot/BurstFire` `:260-307`, `fireSingleRound` `:309`, `applyAimError` `:343`, `applyReactionTimeOnTargetSwitch` `:354`, `scaleCooldown` `:364`, `hasLineOfSight` `:369`.

Bukkit: `world.rayTrace(...)` `:208-210`, particles/sound `:212-213`, `damage(dmg, shooter)` `:216/:227/:246`, knockback `:237/:256`, facing `:127/:138`, equipment `:152-157`, `callEvent` `:325`, `Bukkit.getServicesManager().getRegistration(WeaponRaytracer.class)` `:332`. Deps: `keystone.item.ItemBuilder` `:11`, `keystone.sound.SoundEffect` `:12`, `gangland.core.downed.DownedPlayerRegistry` `:13`, `keystone.timer.SequenceTimer` `:14`, `weapon.SelectiveFire` `:15`, `weapon.events.projectile.WeaponShootEvent` `:16`, `weapon.raytrace.WeaponRaytracer` `:17`, `weapon.raytrace.WeaponShooting` `:18`, `weapon.types.gun.GunWeapon` `:19`.

Cut — split in two. Keystone keeps (weapon-free): cooldown/reaction/difficulty scaling, facing + aim error, melee, vanilla bow/crossbow, the `attack`/`attackEntity` skeleton with the gun branch delegating to the SPI (`ItemBuilder`, `SoundEffect`, `SequenceTimer` are Keystone types already). Seams: `DownedPlayerRegistry.isDowned` (`:51`) → **`NpcTargetFilter`** (`isAttackable(LivingEntity)`, default true; cops installs the downed-backed one); everything gun-shaped (`performGanglandWeaponAttack` `:175-191`, `triggerReload` `:193-198`, `performSingle/Auto/BurstFire`, `fireSingleRound` `:309-341`, `refreshHeldItem` `:143-158`) → **`NpcRangedAttack`** SPI; `AbstractNpc.canAttack()` consulting `heldWeapon.isReloading()` (`AbstractNpc:179` → `:107`) → `rangedAttack.isBusy()`.

### 1.3 `npc/NpcNavigationDelegate.java` (1096 lines)

API: `navigateTo` `:101`, `stopNavigation` `:215`, `pauseNavigation` `:224`, `isNavigationStuck` `:230`, `isNavigationHopeless` `:234`, `isInRangedHoldRange` `:245`, `resolvePursuitLocation` `:252/:309`, `resolveHopelessFallbackLocation` `:271/:318`, `findForwardWanderDestination` `:356`, `updateNavigationProgress` `:413`, ~25 private helpers. Citizens (fully-qualified, no import): `getNavigator().setTarget` `:187`, `isNavigating` `:197/:225`, `cancelNavigation` `:217/:227/:956`, `getDefaultParameters` `:479`, `NavigatorParameters.range(64f)` `:480`, `pathfinderType(PathfinderType.MINECRAFT)` `:481`. Bukkit: `Tag.DOORS` `:1024`, `Bisected/Openable/BlockData` `:1025-1027,:969-981`, `rayTraceBlocks` `:666`, `setGravity` `:496/:874/:960`, ladder teleport `:861/:868`, `setRotation` `:207`, `runTaskLater(owner.plugin, …)` `:979`. Gangland deps: zero; coupling only via `owner.isUsingRangedWeapon()` `:742` and `owner.plugin` `:966/:979`.

Cut: verbatim except `owner.isUsingRangedWeapon()` → `rangedAttack.isRanged()` and **⚠ `world.getMinHeight()` at `:795` is 1.17+ API; Keystone compiles against `bukkit.version = 1.16.5-R0.1-SNAPSHOT` (`Keystone/pom.xml:68`), Gangland against 1.21.11 (`pom.xml:71`). Will not compile in Keystone as-is.**

### 1.4–1.8 Small types

- `npc/NpcBehavior.java` — `NpcBehavior<T extends AbstractNpc>` with `tick/onEnter/onExit` (`:13-23`). Moves verbatim.
- `npc/NavObstacle.java` — package-private enum `NONE, OPEN_DOOR, CLIMB_LADDER` (`:6-10`). Moves verbatim, same package as the delegate.
- `npc/NavStep.java` — package-private (`:13-16`). Moves verbatim, same package.
- `npc/NpcDifficulty.java` — `EASY/NORMAL/HARD/DEADLY` with `aimError`, `reactionTimeTicks`, `fireRateMultiplier`, `meleeDamageMultiplier` (`:29-32`); javadoc `:8-9,:21-22` already argues weapon-decoupling. Moves verbatim.
- `npc/NpcNavigationConfig.java` — interface, 10 getters (`:14-60`). Moves verbatim; impls stay in cops (`npc/police/config/CopConfigProvider`, `npc/civilian/config/CivilianNavigationConfig`). `getMinRepathAfterLossTicks()` (`:60`) is never read — dead method, flag.

### 1.9 `npc/entity/*`

- `EntitySpawner<S extends EntitySpawnerPoint>` (`:27`, 314 lines) — `implements BeanLifecycle`, holds `SpawnConfigProvider` + `IRepository<S>` + `Map<Integer,S>`, `repository.setDataSupplier(spawners::values)` in the ctor `:40`; public `reloadSpawners` `:52`, `onClear/onInitialize` `:57/:63`, `setSpawnerLocation` `:67`, `removeSpawner` `:74`, `getSpawnerIds` `:81`, `getSpawnerLocations` `:85`, `getSpawners` `:93`, `getSpawnerLocation(int)` `:98`, `isVisibleToOtherPlayers` `:108`; protected `createSpawnerPoint` `:48`, `findClosestSpawnerLocation` `:136`, `findSpawnLocation` `:176`, `isOutdoor` `:202`, `normalizeAngle` `:208`. Gangland deps zero. **⚠ `world.getMinHeight()` at `:262`** (same 1.17 problem). Moves once the height floor is solved.
- `EntitySpawnerPoint` (`:11-29`), `SpawnConfigProvider` (`:6-75`, 13 getters; impl `GanglandCivilianSpawnConfigProvider` in `integration/config/` reads `Settings`, stays in cops). Move verbatim.
- `EntityMark` (`:3-17`) — `CIVILIAN, POLICE, UNSET` + `isCivilian()`, **`countForWanted()` (Gangland wanted concept)**. Do not move verbatim: either keep in cops with a generic Keystone mark, or move a `keystone.npc.NpcMark` without `countForWanted()`.
- `EntityMarkManager` (`:15-128`) — `BeanLifecycle`, `NamespacedKey(plugin,"entity_mark")` `:24`, PDC read/write `:50-52,:62-63,:90-92`, `Map<UUID,EntityMark>`. Deps: `npc.civilian.config.CiviliansConfig` `:9` + `CiviliansLoader` `:10` (ctor `:28-30`, `onInitialize` `:41-43`) seeding `defaultPoliceEntities`/`defaultCivilianEntities`. Seam: `NpcMarkDefaults` (police types, civilian types); the hardcoded fallback `VILLAGER/WANDERING_TRADER/PLAYER → CIVILIAN`, `PILLAGER → POLICE` (`:117-122`) goes behind the same provider. Consumers: `AbstractNpc.destroy` `:131`, `CopNpcFactory` `:94`, `NpcDamageUnprotectListener` `:93/:128`, `KillComboWantedTracker` (`CopsNCrooksModuleConfig:452`).

### 1.10 `events/npc/NpcEvent.java` (`:14-17`) — abstract `extends Event` with `@Getter AbstractNpc npc`; subclasses `CopDeathEvent`, `CivilianDeathEvent`. Moves to `keystone.npc.event.NpcEvent` with the base, or stays if the base is genericised.

### 1.11 Proposed seam set

```
org.luckyraven.keystone.npc
├─ AbstractNpc, NpcBehavior, NpcDifficulty, NpcNavigationConfig
├─ NpcNavigationDelegate, NavStep, NavObstacle          (package-private, must co-locate)
├─ NpcCombatDelegate                                     (weapon-free)
├─ NpcSupport                                            (Citizens availability, §2)
├─ spi/ NpcRangedAttack    — isRanged(), isBusy(), tryFire(), triggerReload(), refreshHeldItem(), onDestroy()   ← cops impl wraps Bartizan
├─ spi/ NpcTargetFilter    — isAttackable(LivingEntity)          ← cops impl wraps DownedPlayerRegistry
├─ spi/ NpcMarkDefaults    — policeTypes(), civilianTypes()      ← cops impl wraps CiviliansLoader
└─ entity/ EntitySpawner, EntitySpawnerPoint, SpawnConfigProvider, NpcMarkManager
```

Stays cops-specific: `npc/police/**`, `npc/civilian/**`, `combo/`, `detainment/**` (28), `jail/**` (6), `integration/detainment/**`, `seam/{CopsMoneyDropSource,KillComboWantedTracker}`, `events/{combo,police}`, `command/{civilians,cops,cuff,jail}`, `listener/{civilian,detainment,police,npc,player}`, `listener/NpcDamageUnprotectListener`, `listener/NpcPortalListener`, `npc/cops.yml`, `npc/civilians.yml`.

## 2. Citizens as a soft dependency

### 2.1 Every `net.citizensnpcs` reference (21 main files)

| Destination | File | Surface |
|---|---|---|
| keystone-npc | `npc/AbstractNpc.java:6` | `NPC` |
| keystone-npc | `npc/NpcNavigationDelegate.java:479,481` | `NavigatorParameters`, `PathfinderType.MINECRAFT` |
| cops | `npc/police/npc/CopNpc.java:6,7` (`isNPC` `:141`), `CopNpcFactory.java:3,4` (`createNPC(EntityType.PLAYER, name)` `:79`, `SHOULD_SAVE=false` `:81`, `spawn` `:82`, `speedModifier` `:114`), `CopManager.java:4`, `CivilianNpc.java:7`, `CivilianNpcFactory.java:3,4`, `listener/civilian/CivilianDamageListener.java:4`, `listener/detainment/CopListener.java:4`, `listener/player/WantedLevelListener.java:3`, `listener/NpcDamageUnprotectListener.java:4-6` (`NPCSpawnEvent`), `listener/police/HandcuffBribeListener.java:4` (`NPCRightClickEvent`), `command/cops/CopListCommand.java:3` | |
| turf (D5) | `npc/turf/TurfPowerupManager.java:4,5` (`getNPC(entity)` `:115`), `listener/turf/TurfPowerupInteractListener.java:4` | |
| npc-shops (D6) | `npc/trader/TraderNpc.java:4,5`, `TraderManager.java:4,5`, `listener/trader/TraderInteractListener.java:4`, `npc/banker/BankerNpc.java:4,5`, `BankerManager.java:4,5`, `listener/banker/BankerInteractListener.java:4` | `createNPC`, `SHOULD_SAVE`, `NPCRightClickEvent` |
| core | `gangland-impl/.../listener/player/PlayerDeathListener.java:3` (`isNPC` `:60`, `:181`), `CustomPlayerDeathListener.java:3` (`:97`) | |

Every access goes through static `CitizensAPI.getNPCRegistry()` — the single interception point.

### 2.2 What breaks without Citizens today

`gangland-impl/src/main/resources/plugin.yml:7-10` `depend: [Keystone, NBTAPI, Citizens]` (Bukkit refuses to enable); `Gangland.java:179-180` `new Dependency("Citizens", REQUIRED).validate(null)` → `disablePlugin` (`:252-265`); the two core death listeners throw `NoClassDefFoundError` at invocation on a naive softdepend flip.

### 2.3 `keystone-npc` shape

```java
public final class NpcSupport {
    public static boolean available();               // getPlugin("Citizens") != null && isEnabled() && CitizensAPI.hasImplementation()
    public static Optional<NPCRegistry> registry();  // never throws when absent
    public static boolean isNpc(Entity entity);      // false when absent
}
```
No static Citizens-typed field in `NpcSupport`; real calls behind a package-private `CitizensBridge` loaded only inside guarded branches. Keystone `plugin.yml` (`keystone-plugin/src/main/resources/plugin.yml:7`) → `softdepend: [Vault, PlaceholderAPI, Citizens]`. Root pom: `<module>keystone-npc</module>` after `keystone-module` (`Keystone/pom.xml:48`), dependencyManagement entry (`:168-172`), `citizens-repo` (`https://maven.citizensnpcs.co/repo`, mirror `Gangland/pom.xml:141-145`), `citizens-main` provided, `keystone-plugin` shade dependency (`keystone-plugin/pom.xml:59-87`). `keystone-npc` needs `keystone-common` + `keystone-bean` (`BeanLifecycle`) + `keystone-persistence` (`IRepository`), i.e. the `keystone-hooks/pom.xml` template plus Citizens.

### 2.4 Gangland-side gating

`plugin.yml`: `Citizens` from `depend:` (line 10) to `softdepend:` (`:11-14`). `Gangland.java:179-180` → `Type.SOFT` + the `isEnabled()` fix. Core death listeners: `PlayerDeathListener.java:60,:181`, `CustomPlayerDeathListener.java:97` → `NpcSupport.isNpc(x)`. NPC-owning modules gate at module `onEnabled` (`CopsNCrooksModule.java:39-41`): if `!NpcSupport.available()` report `npc.citizens.missing` (kind dependency, via `Diagnostics.active()`, installed at `gangland-impl/.../config/KernelConfig.java:92-96`) and skip the managers' `start()`/spawn tasks — the 60-tick deferred spawn in `TurfPowerupManager.onInitialize` (`:57`) and `TurfDefenderDeployer.start()` (from `TurfNpcsModuleConfig.java:67`) must not arm.

## 3. D5 — turf NPCs out of cops into gangland-turf

### 3.1 Inventory (20 files + YAML + table + commands.json key)

`npc/turf/TurfPowerupNpc.java` (138), `npc/turf/TurfPowerupManager.java` (194, `BeanLifecycle`, pending-chunk queue), `npc/turf/TurfPowerupData.java`, `npc/turf/TurfPowerupOpenContract.java`, `npc/turf/config/TurfPowerupSettings.java`, `npc/turf/defender/TurfDefenderConfig.java`, `npc/turf/defender/TurfDefenderDeployer.java` (213), `npc/turf/view/{TurfPowerupFlow,TurfPowerupFlowSession,TurfPowerupMenuView,TurfPowerupBuffCatalogueView,TurfPowerupGarrisonView}.java`, `integration/turf/{TurfNpcContractImpl,TurfNpcsConfigLoader,TurfPowerupOpenContractImpl}.java`, `listener/turf/{TurfPowerupInteractListener,TurfPowerupChunkLoadListener,TurfFriendlyFireListener}.java`, `command/turf/{TurfPowerupNpcCommand,TurfPowerupNpcContribution}.java`; DB `database/TurfPowerupNpcRepository.java` + `TurfPowerupNpcTable.java` (table `turf_powerup_npc`, PK `turf_id`, `TurfPowerupNpcTable.java:13-35`); `config/TurfNpcsModuleConfig.java` (9 `@Bean`, `:53-123`, all turf-NPC); YAML `src/main/resources/turf/turf_npcs.yml` (registered `CopsNCrooksYamlConfig.java:34`); `commands.json` key `turf_powerupnpc` (`:170-173`); wiring `CopsNCrooksModule.java:32`, `CopsNCrooksModuleConfig.java:424-430` (contribution bean) and `:454` (`TurfNpcContracts.install`).

### 3.2 Dependencies

**On cops-only code — the blocker: both turf NPC types are civilians.** `TurfPowerupNpc.java:11-13` imports `CivilianState`, `CivilianNpc`, `CivilianSpawnManager`; `spawn(...)` calls `spawnManager.spawnCivilian(loc, settings.typeId())` (`:59`), drives `setTargetPlayerId`/`transitionTo(CivilianState.COMBAT)` (`:132-135`). `TurfDefenderDeployer.java:10-13` imports `CivilianService`, `CivilianState`, `CivilianNpc`, `CivilianSpawnManager`; `deploy` → `spawnCivilian(spawnLocation, civilianTypeId)` (`:92`); `retarget` → `COMBAT` (`:181-184`). `TurfPowerupManager.java:12-13`, `TurfNpcContractImpl.java:5`, `TurfFriendlyFireListener.java:11` (javadoc import of `CivilianDamageListener`), `TurfNpcsModuleConfig.java:8-9` (injects `CivilianService` + `CivilianSpawnManager`). Type ids `quartermaster`, `turf_defender` (`TurfNpcsConfigLoader.java:57,66`) live in cops' `npc/civilians.yml`.

Not dependent on detainment/jail/cop targeting/`EntityMark` (verified). On the NPC base only transitively via `CivilianNpc extends AbstractNpc` (`CivilianNpc.java:28`). On core/turf: `turf.data.Turf`, `turf.manager.TurfManager`, `turf.powerups.{ActiveBuffManager,GarrisonManager,PowerupRegistry,PowerupDefinition}`, `gang.Gang`, `gang.contract.{GangLookupContract,UserLookupContract}`, `gang.user.User`, `Settings`, inventory-api (`InventoryHandler`, `inventory.flow.{MultiPanelInventory,Panel,FlowSession}`, `Fill`, `InventoryUtil`), Keystone `ItemBuilder`/`ChatUtil`/`NumberUtil`/`EconomyException`. The five view classes carry zero cops imports.

### 3.3 What the move requires

1. **Solve the civilian dependency** — the single decision gating D5: (a) move the whole civilian system out of cops too (`npc/civilian/**` ~30 files + `npc/civilians.yml` + `CivilianSpawnerRepository/Table` + `listener/civilian/**` + `listener/npc/CivilianDeathRewardListener` + `command/civilians/**`) — `keystone-npc` cannot own it (names `ItemParser`, `WeaponService`, `Messages`); (b) a turf-side `TurfDefenderSpawner` seam installed by cops (= today's `TurfNpcContract`, i.e. not doing D5 for the spawning half); (c) move turf NPCs to turf with `Depends: [copsncrooks]` — **creates a cycle** with cops' `Depends: [turf, weapon]` (`cops-n-crooks/src/main/resources/module.yml:7-9`) → `module.cycle`, both skipped (`Keystone/docs/keystone-module.md:160`). Only viable if D5 is total: after the move no `copsncrooks` file may import `org.luckyraven.gangland.turf.*` (today `CopsNCrooksModuleConfig.java:65-68` imports `TurfMessageContract`, `TurfManager`, `WandSelectionManager`, `TurfNpcContracts` — all for turf-NPC beans). After D5 cops' `Depends:` becomes `[weapon]` (or `[]` post-Bartizan).
2. turf pom gains `inventory-api` (none today, `gangland-turf/pom.xml:18-86`), `keystone-npc`, `citizens-main` (provided).
3. `TurfNpcContract` + `TurfNpcContracts` become unnecessary if D5 is total — `GarrisonDeployListener` (`gangland-turf/.../listener/powerups/GarrisonDeployListener.java:33`) injects the deployer directly. Delete `turf/turfnpcs/TurfNpcContract.java`, `TurfNpcContracts.java`, `TurfModuleConfig.turfNpcContracts()` (`:205-208`), `CopsNCrooksModuleConfig.java:454`, `integration/turf/TurfNpcContractImpl.java`, `gangland-turf/src/test/.../TurfModuleConfigHolderSeamTest.java`; update `documentation/module-loader.md:144` and `:153`.
4. `TurfPowerupOpenContract` also unnecessary (keep only for Bukkit-free panel tests).
5. Repository → `org.luckyraven.gangland.turf.database` (`TurfModule.java:32`); table name unchanged → no migration.
6. YAML → `gangland-turf/src/main/resources/turf/turf_npcs.yml`; drop `CopsNCrooksYamlConfig.java:34`; register in `TurfModuleFileConfig` (already registers `turf/turf_powerups.yml`).
7. Commands → `turf.command`; `TurfCommand` is already in turf (`gangland-turf/.../command/TurfCommand.java`), so `TurfPowerupNpcCommand` attaches directly and the `CommandContribution` (`TurfPowerupNpcContribution.java:36-43`) + bean (`CopsNCrooksModuleConfig.java:424-430`) disappear; move the `turf_powerupnpc` key (`cops commands.json:170-173`) to turf's `commands.json`.
8. Fold the 9 `TurfNpcsModuleConfig` beans into `TurfModuleConfig`; drop `CopsNCrooksModule.java:32`; update `CopsNCrooksModuleTest.java:37-39` (six → five, → four after D6).

## 4. D6 — traders + bankers into `gangland-npc-shops`

### 4.1 Inventory

`npc/trader/**` (24): `TraderNpc`, `TraderData`, `TraderManager`, `ShopViewOpener`, `ShopViewOpenerImpl`, `config/TraderSettings`, `economy/TraderEconomyContract`, `message/TraderMessageContract`, `mood/{MoodService,MoodState}`, `respawn/TraderRespawnService`, `trait/{TraderTraitDefinition,TraderTraitProfile,TraderTraitRegistry,TraderTraitsLoader}`, `view/{TraderFlow,TraderFlowSession,ModeSelectView,NegotiationView,QuantitySelectorView,SellView,ShopView,BarterView}`.
`npc/banker/**` (16): `BankerNpc`, `BankerData`, `BankerManager`, `config/BankerSettings`, `economy/BankerEconomyContract`, `message/BankerMessageContract`, `tier/{BankTier,BankTierRegistry,BankTiersLoader}`, `view/{BankerFlow,BankerFlowSession,BankerAmountView,BankerClaimView,BankerCreateAccountView,BankerMenuView,BankerRenameAccountView,BankerUpgradeView}`.
Commands (16): `command/trader/{TraderCommand,TraderCreateCommand,TraderRemoveCommand}`, `command/trader/edit/{TraderEditCommand,TraderEditNameCommand,TraderEditShopCommand,TraderEditTraitCommand}`, `command/banker/{BankerCommand,BankerCreateCommand,BankerEditCommand,BankerEditNameCommand,BankerRemoveCommand}`, `command/bank/{BankMenuCommand,BankMenuContribution}`.
Listeners (9): `listener/trader/{TraderInteractListener,TraderDamageListener,TraderBuyListener,TraderSellListener,TraderBarterListener,BarterSessionListener,TraderSellSessionListener}`, `listener/banker/{BankerInteractListener,BankerDamageListener}`.
Events (4): `events/trader/{TraderEvent,TraderBuyRequestEvent,TraderSellRequestEvent,TraderBarterEvent}`. Integration (6): `integration/config/{TraderSettingsImpl,GanglandTraderEconomy,GanglandTraderMessages,BankerSettingsImpl,GanglandBankerEconomy,GanglandBankerMessages}`. Config (2): `config/TraderModuleConfig.java` (17 `@Bean` + `@PostConstruct registerPermissions` `:65-69`), `config/BankerModuleConfig.java` (12 `@Bean`). DB (4): `database/TraderRepository` + `TraderTable` (table `trader`, `:13-39`), `BankerRepository` + `BankerTable` (table `banker`, `:13-35`). No `bank_tiers` table — tiers come from `npc/bank_tiers.yml` via `BankTiersLoader` into `BankTierRegistry`; `Bank` rows are a core repository (`BankerModuleConfig.java:79`). YAML: `npc/trader_traits.yml`, `npc/bank_tiers.yml` (`CopsNCrooksYamlConfig.java:32-33`). commands.json keys (13): `trader`, `trader_help`, `trader_create`, `trader_edit_shop`, `trader_edit_trait`, `trader_edit_name`, `trader_remove`, `banker`, `banker_help`, `banker_create`, `banker_edit_name`, `banker_remove`, `bank_menu` (`:118-169`).

### 4.2 Dependencies

On cops-specific code: **none** (verified for detainment/jail/combo/police/civilian). On the NPC base: **none** — `TraderNpc` (`:14`) and `BankerNpc` (`:14`) are final classes holding a raw Citizens `NPC`, not `AbstractNpc`; they call `createNPC(EntityType.PLAYER, name)`, `SHOULD_SAVE=false`, `setProtected`, `spawn`, `faceLocation` (`TraderNpc.java:31-56`, `BankerNpc.java:31-56`). **`gangland-npc-shops` needs only `NpcSupport` for the gate, not the NPC base.** On UI: heavy — shop-api (`ShopRegistry`, `ShopDefinition`, `ShopItemEntry`, `shop.message.{ShopDisplayResolver,ShopMessageContract}`, `shop.valuation.{CategoryBarterValuator,SellValuator}`, `shop.view.ShopAdminFlow`; `TraderModuleConfig.java:26-31`) and inventory-api (all 15 views). On core: `Gangland`, `GanglandContext`, `Settings`, `Messages`, `file.configuration.shop.GanglandShopDisplayResolver` (`TraderModuleConfig.java:23`), `UserManager`/`User`, `ItemRefresherRegistry`, `GanglandChatUtil`, `command.Command`, `CommandContribution`, `command.sub.bank.BankCommand` (`GanglandBankerEconomy.java:6`, permission constants). Keystone: `economy.{Currency,EconomyHandler,bank.Bank,exception.EconomyException}`, `permission.PermissionManager`, `persistence.{FileManager,IRepository,RepositoryRegistry}`, `bean.*`.

AnvilGUI (T-12) — exactly 6 files, all trader/banker: `command/banker/BankerEditNameCommand`, `command/trader/edit/TraderEditNameCommand`, `npc/trader/view/QuantitySelectorView`, `npc/banker/view/{BankerRenameAccountView,BankerCreateAccountView,BankerAmountView}`. anvilgui arrives transitively at compile scope from `gangland-ui/inventory-api/pom.xml:52-56`; the core relocates it (`gangland-build/pom.xml:34-38`). D6 relocates the bug into npc-shops unless T-12 is fixed first (options: shade+relocate in the module jar; stop relocating in `gangland-build` — used also by core `DebugCommand`, `GangDescriptionCommand`, `ShopTitleCommand`, `LootChestWand`, shop-api `ShopAdminView`/`PriceEditorView`; or an anvil-input seam in inventory-api).

`BankTiers` holder: `CopsNCrooksModuleConfig.java:444-448` installs the delegate → moves to npc-shops' `@PostConstruct`; core holder `org.luckyraven.gangland.data.economy.BankTiers` stays (pinned by `HolderSeamBeanTypeTest.java:36-38`, `BankTiersTest`). `BankMenuContribution` (`parent()=="bank"`, `commands.json:166-169`) + bean (`CopsNCrooksModuleConfig.java:414-417`) move verbatim. `ShopViewOpenerImpl.ADMIN_PERMISSION` registration (`TraderModuleConfig.java:65-69`) moves verbatim.

Cross-module coupling to break: `listener/NpcDamageUnprotectListener.java:17` imports `TraderNpc`; `isTrader(npc)` (`:148-150`) tests `npc.data().has(TraderNpc.METADATA_TRADER_ID)`. Fix: shared metadata constants in `keystone-npc` (`NpcMetadata.TRADER_ID` = `"gangland.trader.id"`, `BANKER_ID`, `TURF_ID` = `"gangland.turfpowerup.turfid"`), or invert via `SHOULD_SAVE` metadata the listener already skips (`:55-58`).

### 4.3 What `gangland-npc-shops` needs

`module.yml`: `Id: npcshops`, `Name: Gangland NPC Shops`, `Main: org.luckyraven.gangland.npcshops.NpcShopsModule`, `Host_Api: 0.8`, `Artifact: org.luckyraven:gangland-npc-shops`; `Depends:` empty (shop-api and inventory-api are core jar contents). Citizens requirement = host `softdepend` + `NpcSupport.available()` gate in `onEnabled`. Packages `org.luckyraven.gangland.npcshops.{trader,banker}` + `.command`, `.listener`, `.database`, `.events`, `.config`; `src/main/resources/org/luckyraven/gangland/npcshops/module.properties`. Pom: parent `gangland-features`; `keystone-{bean,common,item,command,module,persistence,hooks}`, `gangland-impl` (provided), `gangland-domain`, `gangland-item`, `inventory-api`, `shop-api`, `citizens-main`, XSeries, spigot-api; no `gangland-weapon`, `gangland-turf`, `keystone-npc`. Build: `<exclude>org.luckyraven:gangland-npc-shops</exclude>` in `gangland-build/pom.xml:76-83`, an `<artifactItem>` in `copy-runtime-modules` (`:116-142`), a provided dependency (`:157-162` pattern).

Cops keeps everything else; `CopsNCrooksModule.configure` drops `BankerModuleConfig` and `TraderModuleConfig` (`CopsNCrooksModule.java:30-31`) → four configuration classes after D5+D6.

## 5. NPC combat vs Bartizan — the exact weapon surface

12 cops files import `org.luckyraven.gangland.weapon.*`: `npc/AbstractNpc.java:13` (`Weapon`: `heldWeapon`, `stopReloading()` `:134`, `buildItem()` in `CopNpc.equip():90`); `npc/NpcCombatDelegate.java:15-19`; `npc/police/npc/CopNpcFactory.java:23-26` (`Weapon`, `WeaponService`, `ammo.Ammunition`, `dto.AmmunitionData`: `createTransientWeapon(name)` `:205`, off-hand ammo `:223-233`); `npc/civilian/npc/CivilianNpcFactory.java:28-31` (same; `WeaponService` already `@Nullable` `:47,:54`); `npc/police/spawn/CopSpawnManager.java:17`; `config/CopsNCrooksModuleConfig.java:69` (`WeaponManager` in bean params `:347,:356`); `listener/detainment/CopListener.java:23-24` (`isRaytraceDamageInProgress()` `:105`; `onWeaponRaytraceImpact` `:160` cop friendly fire `:170-172`); `listener/NpcDamageUnprotectListener.java:20` (`:118-146`); `listener/police/DetainmentListener.java:26` (`WeaponShootEvent` `:46-48`); `listener/turf/TurfFriendlyFireListener.java:22` (`:81-100`, moves with D5).

Inside `NpcCombatDelegate`: `heldWeapon instanceof GunWeapon gun` `:176`; `isBroken()`, `isMagazineEmpty()` `:178,:267,:278,:300,:310`; `gun.getCurrentSelectiveFire()` `:183-190`; `getProjectileData().getPerShot()/getCooldown()` `:263-264,:275,:286-287`; `isReloading()`, `getReloadData()`, `reload(plugin, null, false)` `:195-197`; `consumeShot()` `:315`, `addAmmunition(1)` `:328`; `new WeaponShootEvent(heldWeapon, shooter)` + `callEvent` `:324-325`, `isCancelled()` `:327`; `getRegistration(WeaponRaytracer.class)` `:332` → `WeaponShooting.fire(plugin, provider, shooter, gun)` `:334`; `getSoundData().getShotCustom()/getShotDefault()` → `SoundEffect.playSoundsAtLocation` `:336-338`; `updateWeaponData(ItemBuilder)` `:156`; `SequenceTimer` burst `:292-306`. `WeaponShooting.fire` (`gangland-weapon/.../raytrace/WeaponShooting.java:39-47`) is documented as the shared player/NPC dispatch point (`:22-24`); `WeaponRaytracer` registered on the `ServicesManager` at `WeaponModuleConfig.java:99`.

What Bartizan's public API must offer: (1) shoot-as-NPC with `LivingEntity` shooter; (2) ammo-less/self-managed NPC magazine (`consumeShot()` + `reload(plugin, null, false)`) so the off-hand ammo hack (`CopNpcFactory.java:212-237`) can go; (3) selective-fire introspection (mode, `perShot`, `cooldown`) or Bartizan-owned cadence (`tryFire()`; changes cop feel — see `feedback_selective_fire_semantics`); (4) raytrace access via `ServicesManager` or a `BartizanApi` service, no compile-time module dependency; (5) events `WeaponShootEvent` (cancellable), `WeaponRaytraceImpactEvent` (`getHitEntity`, `getShooter`, `setCancelled`), `WeaponRaytracer.isRaytraceDamageInProgress()` (ThreadLocal flag, `WeaponRaytracer.java:88`); (6) `createTransientWeapon(String)` (`WeaponService.java:156-162`); (7) `buildItem()`, `updateWeaponData(ItemBuilder)`, `getSoundData()`; (8) the name→weapon catalogue for `weaponNamePool` in `cops.yml`/`civilians.yml`. None of 1–8 may appear in `keystone-npc`: cops implements `NpcRangedAttack` with Bartizan types; `canUseWeapons()`, `isUsingRangedWeapon()` and `NpcNavigationDelegate:742` collapse onto `NpcRangedAttack.isRanged()`.

## 6. Tests

Existing: `CopsNCrooksModuleTest` (`:37-39` the exact ordered list of six configuration classes; `:40-42` the three packages; `:48-50` package placement of `DetainmentRepository`/`CopCommand`/`TraderBuyListener`); `BarterViewTest` (`npc/trader/view/`, CT-01, `:36-85`); `KillComboWantedTrackerTest`; `CuffLockRegistryTest`; `DetainmentCostsContractTest`, `DetainmentServiceQuitTest`, `GanglandSeizedInventoryServiceTest`, `DetainmentRepositoryMigrationTest`, `DetainmentRepositorySpiTest`; `JailRegistryTest`, `JailExitRegistryTest`, `JailExitServiceTest`, `KillComboTrackerTest`; `support/FakeRepository`; impl `HolderSeamBeanTypeTest` (`:36-38` `DataConfig.bankTiers`; comment `:24-26`), `BankTiersTest`; turf `TurfModuleConfigHolderSeamTest` (`:24-30`), `TurfModuleTest`.

Gap: no test for `AbstractNpc`, `NpcCombatDelegate`, `NpcNavigationDelegate`, `NpcDifficulty`, `EntitySpawner`, `EntityMarkManager`, `TurfPowerupManager`, `TurfPowerupNpc`, `TurfDefenderDeployer`, `TurfNpcsConfigLoader`, `TraderManager`, `TraderNpc`, `BankerManager`, `BankerNpc`, `BankTierRegistry`/`BankTiersLoader`, `TraderTraitRegistry`/`TraderTraitsLoader`, `MoodService`.

Proposed split: keystone-npc (`keystone-testkit`): `NpcDifficultyTest`, `NavStepPlanTest`, `EntitySpawnerTest` (CRUD + Y-filter `EntitySpawner.java:151` + `maxSpawnYDiff` reject `:249`), `NpcSupportTest` (`BukkitStatics`, `documentation/TESTING.md:40`). turf: add `TurfPowerupManagerTest` (`onChunkLoaded` `:144-155`), `TurfDefenderDeployerTest` (`findOwningTurfId` `:115-125`) red-first before the move; delete `TurfModuleConfigHolderSeamTest` if `TurfNpcContracts` goes. npc-shops: `git mv` `BarterViewTest`; add `TraderManagerTest`/`BankerManagerTest`/`BankTierRegistryTest`, `NpcShopsModuleTest`. cops: update `CopsNCrooksModuleTest.java:37-39` (six → four). impl: `HolderSeamBeanTypeTest` unchanged (update javadoc `:24-26`).

## Key files to read (10)

1. `npc/AbstractNpc.java`; 2. `npc/NpcCombatDelegate.java`; 3. `npc/NpcNavigationDelegate.java` (`:479-481`, `:795`); 4. `npc/entity/EntityMarkManager.java` + `EntitySpawner.java`; 5. `npc/turf/TurfPowerupNpc.java` + `npc/turf/defender/TurfDefenderDeployer.java`; 6. `config/TurfNpcsModuleConfig.java`; 7. `config/CopsNCrooksModuleConfig.java` (`installCoreSeams()` `:436-455`); 8. `config/CopsNCrooksYamlConfig.java` (`:30-34`); 9. `gangland-build/pom.xml` (`:34-38`, `:76-83`, `:100-146`); 10. `Keystone/docs/keystone-module.md` + `Keystone/pom.xml:43-51,54-102` (API floor `:68`) + `keystone-plugin/pom.xml` + its `plugin.yml`.

## Risks the architects must know

1. 🔴 **Keystone's API floor is 1.16.5; the NPC base uses 1.17+ `World#getMinHeight()`** (`NpcNavigationDelegate.java:795`, `EntitySpawner.java:262`; `Keystone/pom.xml:68`). Raise the floor (breaks Oriel per `:65-67`), reflect with a `0` fallback, or a `WorldHeights` helper in `keystone-common`. Decide before any code moves. (`Attribute.MAX_HEALTH` in `TraderNpc.java:46`/`BankerNpc.java:45` is 1.21.3+ but stays in Gangland.)
2. 🔴 **D5 as stated creates a module cycle unless it is total** (`cops module.yml:7-9` `Depends: [turf, weapon]`; `module.cycle` skips both). Verify zero `org.luckyraven.gangland.turf` imports remain in cops.
3. 🟠 **Turf NPCs are civilians** (`spawnCivilian` + `CivilianState.COMBAT`; type ids in cops' `civilians.yml`). Moving the wrappers without the civilian system just relocates the dependency. Cost "move civilians too".
4. 🟠 **T-12 (AnvilGUI) is live in shipped cops-n-crooks**; all six users are trader/banker. Fix before D6.
5. 🟠 `EntityMark.countForWanted()` and the `VILLAGER/…→CIVILIAN`, `PILLAGER→POLICE` fallback are Gangland policy; do not leak into Keystone.
6. 🟠 `AbstractNpc.setHeldWeapon(weapon, plugin)` is the only way the base gets `JavaPlugin` (`CopNpcFactory.java:100-102`; delegate needs it at `:966,:979`). Add a constructor parameter.
7. 🟡 `NpcCombatDelegate`/`NpcNavigationDelegate`/`NavStep`/`NavObstacle` are package-private with same-package field access (`NpcCombatDelegate.java:26-28`); co-locate in one Keystone package.
8. 🟡 Citizens soft-dep changes the failure mode to "NPCs silently absent"; `Dependency.validate` (`Gangland.java:252-265`) misses present-but-disabled; add `npc.citizens.missing` + a startup log line.
9. 🟡 Zero test coverage on every unit being moved; write pins first.
10. 🟡 Bartizan must keep `WeaponRaytracer` on the `ServicesManager` (`NpcCombatDelegate.java:332`, `WeaponModuleConfig.java:99`).
11. 🟡 Metadata strings (`"gangland.trader.id"`, banker id, `"gangland.turfpowerup.turfid"`) are the de-facto cross-module contract; give them a shared home.
12. 🟡 Repository/YAML relocation: table names unchanged, but `scanAndRegisterRepositories` runs per module package — check every moved repository's owning manager still calls `setDataSupplier`.
