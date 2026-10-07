<!-- E1 · feature-dev:code-explorer (opus) · 2026-09-08 · weapon module boundary trace. The agent ran before README.md existed; decisions D1–D10 are in README.md (D7 = no bridge, signs leave, was honoured). -->

# E1 — Bartizan split: weapon boundary trace

## 0. Ground truth

`gangland-features/gangland-weapon/src/main/java` contains **231 import statements naming a non-weapon `org.luckyraven.gangland.*` type across 44 of 144 main files**. That is the whole compile-time boundary. Zero reflective couplings to core types (`Gangland.` / `Gangland.CONSTANT` grep returns nothing).

Owning Maven module per imported type:

| Owner | Types imported by weapon |
|---|---|
| `gangland-impl` | `Gangland`, `Settings`, `Messages`, `GanglandChatUtil`, `command.Command`, `command.extension.CommandContribution`, `database.GanglandDatabase`, `data.plugin.DataCleanupTask`, `data.placeholder.PlaceholderService`, `metrics.MetricsContributor`, `listener.death.DeathMessageContributor`, `file.configuration.shop.ShopDisplayNameProvider`, `item.ItemPredicates`, `item.ItemAttributes`, `item.NbtTagCatalog`, `item.configuration.UniqueItemAddon`, `sign.type.Sign`, `sign.type.trade.BaseTradeSign`, `sign.aspect.MoneyAspect`, `sign.aspect.ItemTransferAspect` (+ `ItemSimilarityChecker`), `sign.parser.TradeSignParser`, `sign.extension.{SignTypeContribution,SignViewProvider}` |
| `gangland-infra/gangland-domain` | `gang.user.User`, `gang.user.UserManager`, `gang.Gang`, `gang.GangManager` |
| `gangland-infra/gangland-item` | `ItemKind`, `ItemConverterRegistry`, `ItemSerializerRegistry`, `ItemRefresherRegistry`, `ItemSerializer`, `ItemRefresher`, `item.contract.WearableEquipService`, `item.unique.UniqueItem`, `item.wearable.Wearable`, `item.wearable.WearableTrait` |
| `gangland-core` | `core.downed.DownedPlayerRegistry` |
| `gangland-ui/sign-api` | `SignType`, `SignTypeDefinition`, `SignValidator`, `AbstractSignValidator`, `SignParser`, `SignHandler`, `AspectBasedSignHandler`, `SignAspect`, `AspectResult`, `SignFormat`, `SignLineFormat`, `ParsedSign`, `BulkActionPreview`, `BulkSignHandler` |
| `gangland-ui/inventory-api` | `InventoryHandler`, `inventory.part.Fill`, `inventory.util.InventoryUtil` |
| `gangland-compatibility/version-impl` | `CompatibilityWorker`, `recoil.RecoilCompatibility` |

## 1. Per-type disposition

Key: **(a)** Bartizan rewrites/copies · **(b)** belongs in Keystone · **(c)** Gangland-only concern → Bartizan extension point Gangland implements · **(d)** dies with the split.

### 1.1 Plugin handle and infrastructure

| Type | Files | Use | Class |
|---|---|---|---|
| `Gangland` | 17 | Purely a `JavaPlugin` handle; no method or constant called. Passed to `super(gangland, …)` (`WeaponCommand.java:32`), `new FileHandler(gangland, …)` (`WeaponFileConfig.java:53,63,94`), `new Argument(gangland, …)` (`DebugWeaponContribution.java:36`), `WeaponSignViewProvider` (field already `JavaPlugin plugin`, `sign/view/WeaponSignViewProvider.java:37` vs `WeaponModuleConfig.java:244`). | (a) — `Bartizan extends JavaPlugin`; most sites take `JavaPlugin`. |
| `database.GanglandDatabase` | 2 | `WeaponManager.java:4,23` and `WeaponModuleConfig.java:9,73,258-259`; only `getRepositoryRegistry()` used. | (a) — `BartizanDatabase extends DatabaseHandler` (§6). |
| `data.placeholder.PlaceholderService` | 1 | `WeaponFileConfig.java:4,50,62,80`, passed to `WeaponAddon`/`AmmunitionAddon`/`WearableAddon` whose constructors declare Keystone `org.luckyraven.keystone.util.Placeholder` (`configuration/WeaponAddon.java:8,35,37`; `AmmunitionAddon.java:9,36,39`); `PlaceholderService implements Placeholder` (`PlaceholderService.java:27`). | (d)/(a) — Bartizan supplies its own PAPI-backed `Placeholder` or `null` (both addons take `@Nullable`). |
| `compatibility.CompatibilityWorker` | 1 | `WeaponModuleConfig.java:7,114-116` (`getRecoilCompatibility()`); constructed at `gangland-impl/.../config/KernelConfig.java:148-150`. | (a) — moves wholesale (§3). |
| `compatibility.recoil.RecoilCompatibility` | 9 | Constructor param through every action type: `types/gun/GunAction.java:21,25,29,82`, `types/gun/FullAutoTask.java:52,61,68,96`, `types/melee/MeleeAction.java:31,35,38,127`, `types/throwable/ThrowableAction.java:49,52,56,83`, `types/incendiary/IncendiaryAction.java:43,48,53,111`, `types/biological/BiologicalAction.java:37,42,46,113`, `listener/WeaponInteract.java:17,77,123,127,285,366,374,403,447,610,682`, `projectile/recoil/RecoilManager.java:4,26-101`. | (a) — moves (§3). |
| `core.downed.DownedPlayerRegistry` | 3 | Static `isDowned(uuid)` gate: `listener/WeaponInteract.java:148,225`, `reload/type/NumberedReload.java:90,132`, `reload/type/InstantReload.java:61,74`. 31-line static set (`gangland-core/.../DownedPlayerRegistry.java:14-30`) written only by Gangland's death listener; also read by cops (`copsncrooks/npc/NpcCombatDelegate.java:51`) and gadget (`gadget/listener/car/CarDismountListener.java:46`). | (c) — Bartizan exposes a `Predicate<Player>`-style combat-eligibility SPI (default `Player::isDead`); Gangland installs a delegate backed by its registry. |

### 1.2 Config and text

| Type | Files | Use | Class |
|---|---|---|---|
| `Settings` | 7 | Seven static getters (§7) + one ordering-only parameter at `WeaponFileConfig.java:44`. | (a) — Bartizan's own `settings.yml`; only the three block-regeneration knobs are weapon-owned. |
| `Messages` | 10 | 14 enum members (§7). | (a) — Bartizan's own enum on Keystone's `org.luckyraven.keystone.message.MessageProvider`/`YamlMessageProvider` (`keystone-common/.../message/MessageProvider.java:12-18`) + `keystone-persistence` `LanguageLoader` (`.../persistence/message/LanguageLoader.java:33-45`). Seam exists; no Keystone work. |
| `GanglandChatUtil` | 10 | Commands calling `setArguments(...)`/`commandMessage(...)` (e.g. `command/WeaponGiveCommand.java:51,88`). `GanglandChatUtil extends ChatUtil` adds a money-symbol replacement + four prefix helpers (`gangland-impl/.../util/GanglandChatUtil.java:9-58`). | (a) — 30-line `BartizanChatUtil extends keystone.util.ChatUtil`. |

### 1.3 Commands

| Type | Files | Use | Class |
|---|---|---|---|
| `command.Command` | 2 | `WeaponCommand.java:6,20`, `AmmunitionCommand.java:6`; thin adapter over Keystone `Command` adding `gangland.command.<label>` permissions, `commands.json` help, `Messages` errors (`gangland-impl/.../command/Command.java:28-67`). | (a) — Bartizan's own adapter, own root command. |
| `command.extension.CommandContribution` | 2 | `DebugWeaponContribution.java:4,17` (`parent()=="debug"`), `ItemWearableContribution.java:5` (`parent()=="item"`). | (d) — Bartizan owns `/bartizan debug …`, `/bartizan wearable …`. |

### 1.4 Users and gangs — see §2. `gang.Gang`/`GangManager` used only in `listener/gang/GangAllyWeaponImpactListener.java:7-8,42-45` → (c), the whole listener leaves Bartizan.

### 1.5 Item framework

| Type | Files | Use | Class |
|---|---|---|---|
| `item.ItemKind` | 4 | Enum labels in the three serializers + `WeaponModuleConfig.java:12,195-198`; enum hardcodes `WEAPON/AMMUNITION/WEARABLE/CAR` (`gangland-item/.../ItemKind.java:13-20`). | (b) via Keystone's `ItemKind` interface; Bartizan defines its own kinds. |
| `item.ItemSerializer`, `item.ItemRefresher` | 3 each | `kind()`/`extract`; `canRefresh`/`refresh`/`decorate`. | (b) — already in `keystone-item`. |
| `item.ItemAttributes` | 2 | Base of `WeaponConverter`/`AmmunitionConverter`/`WearableConverter`; `applyAttributes` calls `GanglandChatUtil.color` (`gangland-impl/.../item/ItemAttributes.java:7,22,29`). | (b) if genericised, else (a). |
| `ItemConverterRegistry` / `ItemSerializerRegistry` / `ItemRefresherRegistry` | 1 (`WeaponModuleConfig.java:11,14,15,183-206`) | Registers 4 converters, 3 serializers, 3 refreshers (10/10/0). | (c)+(d) — internal to Bartizan, but Gangland loses weapon serialize/refresh unless Bartizan publishes providers Gangland pulls (E2 §3c). |
| `item.ItemPredicates` | 1 | `WeaponModuleConfig.java:13,202` — `ItemPredicates.WEARABLE` only (`WEAPON`/`AMMUNITION` already in `item/WeaponItemPredicates.java`). | (a) — `WEARABLE` moves with `Wearable`. |
| `item.NbtTagCatalog` | 1 | `WeaponModuleConfig.java:16,220-225`. | (d). |
| `item.configuration.UniqueItemAddon`, `item.unique.UniqueItem` | 2, 1 | Sign view + `BaseTradeSign.getUniqueOrMaterialItem` (`gangland-impl/.../sign/type/trade/BaseTradeSign.java:14-28`). | (d) with the sign layer. |
| `item.wearable.Wearable` | 11 | The wearable domain object: `wearable/WearableService.java:9`, `wearable/WearableAddon.java:13`, `item/WearableRefresher.java:9`, `item/WearableItemSerializer.java:8`, `item/WearableConverter.java:6`, `sign/WearableBuySign.java:6`, `sign/WearableSellSign.java:6`, `sign/view/WeaponSignViewProvider.java:13`, `command/wearable/ItemWearable{List,Info,Give}Command.java`. Depends only on Keystone + `item.fuel.FuelKey` (`gangland-item/.../wearable/Wearable.java:13-17`). | (a) — must move into Bartizan (armour-damage model for the raytracer). Risk R4: carries `isJetpack()`/`fuelKey` (`Wearable.java:58-60`) consumed by gadget (`gadget/jetpack/JetpackService.java:112-115,138-140`). |
| `item.wearable.WearableTrait` | 3 | `WearableService.java:10`, `WearableAddon.java:14`, sign view, `ItemWearableInfoCommand.java:17`; gadget reads `WearableTrait.FUEL_EFFICIENT` (`gadget/jetpack/JetpackTask.java:215-219`). | (a) + exported. |
| `item.contract.WearableEquipService` | 2 | `WearableService implements WearableEquipService` (`WearableService.java:8`), bound at `WeaponModuleConfig.java:18,109-111`; only consumer is gangland-item's `WearableEquipListener` (`gangland-item/.../contract/WearableEquipService.java:13-21`). | (c)→(d): listener moves to Bartizan (T-14), interface dies. |

### 1.6 Inventory UI — `InventoryHandler`, `Fill`, `InventoryUtil` used only by `sign/view/WeaponSignViewProvider.java:8-10,78,115,133,197` → (d) with the view sign; a `/bartizan weapon info` GUI would need its own helper.

### 1.7 Core seams the module implements today

| Type | Use | Class |
|---|---|---|
| `metrics.MetricsContributor` | `metrics/WeaponMetricsContributor.java:3,13,23` → `number_of_weapons`. | (d) — Bartizan's own bStats id. |
| `data.plugin.DataCleanupTask` | `data/WeaponDataCleanupTask.java:4,18,31-47` — clears the `weapon` table + cache. | (a) — Bartizan's own timer. |
| `listener.death.DeathMessageContributor` | `death/WeaponDeathMessageContributor.java:6,17,27-45` — resolves "killed by X" from `ThrowableAction.pendingKillerWeapon` or the held item. | (c) — invert: Bartizan publishes a death-cause API/event; Gangland's `PlayerDeathListener` (`gangland-impl/.../listener/player/PlayerDeathListener.java:210`, `Messages.DEAD_USING_WEAPON`) consumes it. |
| `file.configuration.shop.ShopDisplayNameProvider` | `shop/WeaponShopDisplayNameProvider.java:5,18,28-40` — strips the magazine counter for shop listings. | (c) — Bartizan exposes `String cleanDisplayName(ItemStack)`. |
| `sign.extension.SignTypeContribution` / `SignViewProvider` | `sign/WeaponSignContribution.java:6,19,35-50`, `sign/view/WeaponSignViewProvider.java:15,35,53-72`. | (d) per D7 — see §4. |

### 1.8 Sign framework (the 94-import block) — imported only by the six sign classes, three validators and `WeaponSignContribution`. sign-api types → (d) per D7 (generic enough for a future `keystone-sign`); `sign.type.Sign`, `BaseTradeSign`, `TradeSignParser`, `ItemTransferAspect`, `MoneyAspect` (`gangland-impl/.../sign/aspect/MoneyAspect.java:24-51`, `user.getEconomy()`) → (d).

## 2. `UserManager` (19 files) and `User` (7 files) — every use

**Group A — `MoneyAspect` plumbing only (7 files, never dereferenced):** `sign/WeaponBuySign.java:25,32,41`, `sign/WeaponSellSign.java:25,32,44`, `sign/AmmoBuySign.java:25,32,41`, `sign/AmmoSellSign.java:25,32,44`, `sign/WearableBuySign.java:30,40,50`, `sign/WearableSellSign.java:30,40,59`, `sign/WeaponSignContribution.java:24,31,44-49`; wiring `WeaponModuleConfig.java:239-240`. `MoneyAspect` does `userManager.getUser(player).getEconomy()` (`MoneyAspect.java:24-28,55-59`). All die with the signs (d).

**Group B — command messaging (10 files, one method):** pattern `User<Player> user = userManager.getUser(player); if (user == null) return; … user.sendMessage(...)` in `command/WeaponGiveCommand.java` (`:57,:78` / `:66,:69,:88,:98,:101`), `WeaponInfoCommand.java` (`:50,:69` / `:58,:77,:90`), `AmmunitionGiveCommand.java` (`:52,:73` / `:61,:64,:83,:91,:94`), `AmmunitionInfoCommand.java` (`:44,:69` / `:51,:58,:77,:90`), `command/wearable/ItemWearableGiveCommand.java` (`:52,:73` / `:60,:64,:83,:90,:94`), `ItemWearableInfoCommand.java` (`:41` / `:48,:59,:82`); forwarding constructor params in `WeaponCommand.java:22,34,56,58`, `AmmunitionCommand.java:20,28,48,50`, `ItemWearableCommand.java:22,32,45,46`, `ItemWearableContribution.java:24,30,41`. Only `sendMessage(String)` is used → `player.sendMessage` (d).

**Group C — friendly fire (1 file, the only semantic use):** `listener/gang/GangAllyWeaponImpactListener.java:37-45` uses `hasGang()`, `getGangId()`, `Gang.isAlly(Gang)`; `@ListenerHandler(condition = "isGangEnabled")` (`:20`), `EventPriority.LOWEST` on the cancellable `WeaponRaytraceImpactEvent`. **Moves verbatim into Gangland 0.9.0** compiled against `bartizan-api` — same for cops' `TurfFriendlyFireListener.java:22` and `NpcDamageUnprotectListener.java:20`.

Verdict: Bartizan needs no user seam. Requirement: `WeaponRaytraceImpactEvent` stays `Cancellable`, fires before damage, exposes `getShooter()`/`getHitEntity()` (`events/projectile/WeaponRaytraceImpactEvent.java:30,34-35,63-71`). Cancelling suppresses damage only, not penetration/ricochet counters (`:26-27`) — document in `bartizan-api`.

## 3. The recoil path — already portable

No `VersionSetup` class exists (stale name in `CLAUDE.md`). Live chain:
1. `gangland-impl/.../config/KernelConfig.java:148-150` — KERNEL bean `new CompatibilityWorker(gangland::getViaAPI)`.
2. `gangland-compatibility/version-impl/.../CompatibilityWorker.java:28-42` — `VersionedAdapterLoader.loadOrFallback(Compatibility.class, VERSION_PACKAGE, () -> null)` with `VERSION_PACKAGE = "org.luckyraven.gangland.compatibility.version"` (`:23`); fallback builds the Bukkit-API `RecoilCompatibility` with the ViaVersion supplier (`:33-39`).
3. `Keystone/keystone-common/.../nms/VersionedAdapterLoader.java:38-83` — `CraftBukkitRevision.current().name()` → `Class.forName(basePackage + "." + revision, false, loader)`; faults via `Diagnostics.active()` (`:46-53`).
4. Contract `Compatibility.getRecoilCompatibility()` (`version-impl/.../Compatibility.java:5-9`); 20 `v1_XX_RY` classes return `Recoil_1_XX_RY extends RecoilCompatibility` overriding `modifyCameraRotation` with a raw position packet (`version-1_21_R7/.../recoil/Recoil_1_21_R7.java:14-38`).
5. `WeaponModuleConfig.java:113-116` unwraps `RecoilCompatibility`; `RecoilManager.recoil(...)` calls `modifyCameraRotation(player, yaw, pitch, true)` (`projectile/recoil/RecoilManager.java:100-102`).

All 21 compatibility artifacts are shaded into the core jar (`gangland-build/pom.xml:195-296`). Bartizan can host them with zero Keystone changes: `VersionedAdapterLoader.load(Class<T>, String basePackage, ClassLoader)` (`:38-40`) takes any contract/package/loader. `RecoilCompatibility` (41 lines) and `Compatibility` (9 lines) reference nothing Gangland (imports: `com.viaversion.*`, Lombok, `keystone.nms`). Carry-overs: ViaVersion `Supplier<ViaAPI<?>>` (`RecoilCompatibility.java:24,27-37`) → Bartizan softdepend + supplier; the `viaversion-api` provided test-classpath note in `gangland-weapon/pom.xml:84-96` (Mockito inline mock maker; docket T-09) must be kept in Bartizan's pom.

## 4. Weapon ↔ signs

Six sign types built in `weapon/sign/WeaponSignContribution.java:36-49`: `<prefix>weapon-buy/sell` (`WeaponBuySign`, `WeaponSellSign`), `ammo-buy/sell`, `wearable-buy/sell`; three validators (`WeaponSignValidator`, `AmmoSignValidator`, `WearableSignValidator`, each reading `Settings.getMoneySymbol()`); base `sign/AbstractWeaponTradeSign.java:27-78` (`getWeaponItem`, `getAmmoItem`, `weaponSimilarityChecker`, `ammoSimilarityChecker`). Canonical shape `sign/WeaponBuySign.java:37-98`; wearable pair also `BulkSignHandler`/`BulkActionPreview` (`WearableBuySign.java:12-13`).

Seams: `SignTypeContribution.signs(prefix)`, resolved lazily by `SignManager.setupSigns()` (`gangland-impl/.../sign/SignManager.java:77`, appended at `:124`); `SignViewProvider.open(Player, content)` (`WeaponSignViewProvider.java:53-72`, weapon → ammo → wearable); `SignContributions` (`gangland-impl/.../sign/extension/SignContributions.java:26-53`).

What Gangland loses under D7: (1) all six sign types — placed `[WEAPON-BUY]` signs become unrecognised (live-world break); (2) the `[VIEW]` sign's weapon/ammo/wearable branches; (3) `weaponSimilarityChecker` (`:60-68`, `weaponService.compare(w1,w2)==0`) — any generic sign trading `weapon:rifle` mis-stacks without it.

Minimum `bartizan-api` contract for a generic Gangland trade sign / shop (recommend one `WeaponItemApi`): `ItemStack buildItem(String weaponName)` (reproducing the throwable determinism rule, `UUID.nameUUIDFromBytes("throwable:" + name)`, `AbstractWeaponTradeSign.java:41-45` / `WeaponService.mintUuid:274-283`); `boolean isSameWeapon(Player, ItemStack, ItemStack)` (for `ItemTransferAspect.ItemSimilarityChecker`, `gangland-impl/.../sign/aspect/ItemTransferAspect.java:194`; ammo keyed on `Ammunition.NBT_KEY`, `:70-78`); `boolean isValidWeaponName(String)`; `String cleanDisplayName(ItemStack)`.

## 5. Events + public API surface `bartizan-api` must expose

### 5.1 Events (9 classes, `weapon/events/**`)

| Class | Shape | Payload | Consumers outside the module |
|---|---|---|---|
| `events/WeaponEvent.java:8-15` | abstract | `getWeapon()` | base |
| `events/projectile/WeaponRaytraceImpactEvent.java:30-77` | `Cancellable` | `shooter`, `hitEntity` (nullable), `hitBlock`, `hitBlockFace`, `impactPoint`, `state`, mutable `damage` (`:41-42`) | `copsncrooks/listener/turf/TurfFriendlyFireListener.java:22`, `copsncrooks/listener/NpcDamageUnprotectListener.java:20`, `copsncrooks/listener/detainment/CopListener.java:23`, `gadget/listener/car/CarDamageListener.java:28,196`, `weapon/listener/gang/GangAllyWeaponImpactListener.java:33` |
| `events/projectile/WeaponShootEvent.java:12-44` | `Cancellable` | `shooter` | `copsncrooks/npc/NpcCombatDelegate.java:16,324-330`, `copsncrooks/listener/police/DetainmentListener.java:26` |
| `events/WeaponEntityDamageEvent.java:15-39` | plain | `entity`, `damage`, `shooter` (`Player`) | `gadget/listener/car/CarDamageListener.java:27,177` |
| `events/WeaponKillEntityEvent.java:11-20` | `Cancellable` | `killer`, `killed` | internal |
| `events/reload/WeaponReloadEvent.java:9-15`, `WeaponReloadStartEvent.java:11-17`, `WeaponReloadCompleteEvent.java:11-17` | — | `player` | internal |
| `events/selective/WeaponChangeSelectiveFireEvent.java:9-15` | `Cancellable` | — | internal |

All nine ship in `bartizan-api`.

### 5.2 `WeaponService` (`weapon/WeaponService.java:23-306`)

`static UUID getWeaponUUID(ItemStack)` `:36`; `compare(Weapon,Weapon)` `:54` (`AbstractWeaponTradeSign:66`); `getHeldWeaponName` `:59`; **`isWeapon(ItemStack)`** `:65` (gadget `CarDamageListener.java:117`); `hasAmmunition` `:84`; `getHeldWeaponItem` `:103`; **`getWeaponTemplate(String)`** `:130` (`WeaponShopDisplayNameProvider:34`, `WeaponDeathMessageContributor:33`); **`getWeaponTemplates()`** `:140`; **`createTransientWeapon(String)`** `:156` (cops `CopNpcFactory.java:205`, `CivilianNpcFactory.java:160`); `getWeapon(...)` overloads `:165,:170,:175,:198`; **`validateAndGetWeapon(Player, ItemStack)`** `:236` (gadget `JetpackTask.java:245`, `CarDamageListener.java:260`); `getWeapons()` (`:27-28`); `clear()` `:257`; `isHeadPosition` `:261`. `WeaponManager extends WeaponService implements BeanLifecycle` (`weapon/WeaponManager.java:11`, `initialize()` `:22-36`). **cops names `WeaponManager` directly** at `copsncrooks/config/CopsNCrooksModuleConfig.java:69` — narrow to the API type.

### 5.3 `Weapon` and subtypes

`Weapon` public methods (`weapon/Weapon.java`): `copyWithUUID` `:122`, `getTagProperName` `:126`, `pickDeathMessage` `:130`, `scope/unScope` `:135,:144`, `isReloading` `:153`, `reload(JavaPlugin,Player,boolean)` `:157`, `stopReloading` `:162`, `isMagazineFull/Empty` `:167,:172`, `addAmmunition` `:177`, `consumeShot` `:186`, `requiresReload` `:192`, `buildItem()`/`buildItem(Player)` `:197,:202`, `updateWeaponData` `:220,:224`, `updateWeapon` `:254`, `removeWeapon` `:258`, durability `:262-276`, `containsTag` `:288`, `clone` `:298`, `compareTo` `:311`, `applyPush` `:324`; Lombok getters for category/name/displayName/uuid/ammunitionData/scopeData/soundData/reloadData/damageData/recoil/durabilityCalculator.

Cross-module consumption — cops: `AbstractNpc.java:13,37,97-98,133-134` (`setHeldWeapon`, `stopReloading`), `NpcCombatDelegate.java:107,156,178,195-197,267,300,310-328` (`isReloading`, `updateWeaponData`, `isBroken`, `isMagazineEmpty`, `getReloadData`, `reload`, `consumeShot`, `addAmmunition`, `getSoundData`), `CopNpcFactory.java:223-230`/`CivilianNpcFactory.java` (`getAmmunitionData().getAmmoType()/getMaxMagCapacity()`); gadget: `JetpackTask.java:245-247` (`getScopeData()`), `CarDamageListener.java:261-262` (`instanceof MeleeWeapon` → `getMeleeData().getDamage()`). Subtypes needed: `GunWeapon` (`NpcCombatDelegate.java:19,176,183-190,260-341`: `getCurrentSelectiveFire()`, `getProjectileData().getPerShot()/getCooldown()`, `getSpread()`), `MeleeWeapon`, and `WeaponType`, `ProjectileType`, `SelectiveFire`, `ProjectileData`, `AmmunitionData`, `MeleeData`, `ScopeData`, `SoundData`, `DamageData`, `ReloadData`, `SpreadData`, `ProjectileState`.

### 5.4 Firing API

`SelectiveFire` enum (cops switch on `SINGLE/BURST/AUTO`, `NpcCombatDelegate.java:15,183-190`); **`WeaponShooting.fire(JavaPlugin, WeaponRaytracer, LivingEntity, GunWeapon)`** (`raytrace/WeaponShooting.java:39-47`, called at `NpcCombatDelegate.java:334`) + `SPREAD_PELLET_COUNT` (`:31`); `WeaponRaytracer` public surface: `static isRaytraceDamageInProgress()` `:88`, `getVisualSpawner()` `:160`, `fireInstant(RaytraceRequest)` `:173`, `advanceSegment(RaytraceContext, Location, Location)` `:195`, `RaytraceRequest.builder()`. Discovery is already cross-plugin-safe: `Bukkit.getServicesManager().register(WeaponRaytracer.class, raytracer, gangland, ServicePriority.Normal)` at `WeaponModuleConfig.java:99`, looked up at `NpcCombatDelegate.java:332-335`. Bartizan registers `WeaponService`/`WearableService` the same way.

### 5.5 Ammo / wearable lookups

`AmmunitionManager.getAmmunitionKeys()`, `getAmmunition(String)` (`AbstractWeaponTradeSign.java:50-53,74-75`, `WeaponSignViewProvider.java:60`); `Ammunition.isAmmunition(ItemStack)`, `Ammunition.NBT_KEY`, `Ammunition.buildItem()`; cops uses `Ammunition` for starting ammo (`CopNpcFactory.java:25,226`, `CivilianNpcFactory.java:30`). `WearableService` (`weapon/wearable/WearableService.java`): `register` `:43`, `getWearable` `:48`, `getWearables` `:52`, `clear` `:56`, **`resolveWearable(@Nullable ItemStack)`** `:68` (gadget `JetpackService.java:112,138`), **`applyWearableReduction(double, LivingEntity, boolean)`** `:98`, `reduceCritBonus` `:139`, `reduceFireTicks` `:165`.

### 5.6 Static mutable cross-plugin state — must be redesigned

`weapon/types/throwable/ThrowableAction.java:38,45`: `public static final Map<UUID,String> pendingKillerWeapon` and `Map<UUID,Double> pendingVehicleExplosionDamage`, written at `:205,:243`, cleaned at `:220`, `remove()`d from `gadget/listener/car/CarDamageListener.java:161` and `weapon/death/WeaponDeathMessageContributor.java:29`. Replace with events or a queryable service before any consumer compiles against `bartizan-api` (R1).

## 6. Persistence

`weapon/database/WeaponTable.java:11-33` — table `weapon`, columns `uuid` (key) + `type`; runtime state lives in item NBT (`WeaponService.setWeaponData:292-306`). `weapon/database/WeaponRepository.java:21-78` — `@Repository(Weapon.class)`, ctor `(JavaPlugin, DatabaseHandler, DatabaseBackend)` (`:29`), `doLoadAll()` re-hydrates via `WeaponAddon` + `copyWithUUID` (`:44-62`), `deleteAll()` async (`:38-41`), `doDelete` by uuid (`:75-77`). `WeaponModule.REPOSITORY_PACKAGE` (`WeaponModule.java:18`). `WeaponManager.initialize()` (`:22-36`) sets the addon, `loadAll()`, populates the map, **`setDataSupplier`** (`:35`).

`GanglandDatabase` value-add: `connectBackend()` (`:56-81`), `disconnectBackend()` (`:100-105`), `createSchema` (`:108-122`), `createTables()` (`:125-132`), rank seeding (`:135-148`), SQLite `database/<schema>.db` path (`:151-157`). Bartizan needs only Keystone's `DatabaseHandler`, `DatabaseBackend` + `SqliteBackend`/`MysqlBackend` + `ConnectionParams`, `RepositoryRegistry(plugin, handler, backend)`, `AbstractRepository`, `TableBackend`, `DatabaseHelper`, `DatabaseSettingsProvider` → `BartizanDatabase extends DatabaseHandler` (~60 lines) with the file under `plugins/Bartizan/database/`. Migration hazard (R3): the `weapon` table lives in Gangland's schema today; a fresh Bartizan database orphans persisted UUIDs (items still work via the catalogue fallback `WeaponService.java:76-81`, but re-mint).

## 7. Settings and Messages to recreate

Settings (exhaustive): `getBlockRestoreDelayTicks()` / `getBlockRegenerationDelayTicks()` / `getBlockRegenerationStepTicks()` (`file/WeaponBlockRegenerationSettings.java:13,18,23`; keys `Block_Regeneration.Restore_Delay_Ticks|Regeneration_Delay_Ticks|Regeneration_Step_Ticks`, defaults 100/100/4 at `Settings.java:194-196,707-709`); `getMoneySymbol()` (three validators; `Money_Symbol`, `"$"`, `:51,:427`); `getInventoryFillItem()`/`getInventoryFillName()` (`WeaponSignViewProvider.java:115,133,197`; `Inventory.Fill.Item|Name`, `:48,:414-415`); `isAutoSaveDebug()` (`data/WeaponDataCleanupTask.java:20`; `Auto_Save.Debug`, `:44,:404`). The only weapon section of `settings.yml` is `Block_Regeneration` at `settings.yml:643-656`; `Starting_Ammo_Magazines` (`:386-387`) is cops'; the logging module list at `settings.yml:40` names `Gangland Weapons`.

Messages (14): `ARGUMENTS_MISSING` (`Commands.Syntax.Missing_Arguments` `:27`), `MUST_BE_NUMBERS` (`:286`), `RECEIVED_WEAPON` (`:34`), `INVALID_WEAPON` (`:316`), `WEAPON_LIST_HEADER` (`:566`), `RECEIVED_AMMO` (`:32`), `INVALID_AMMO` (`:315`), `AMMO_LIST_HEADER` (`:567`), `ITEM_WEARABLE_GAVE` (`:559`), `ITEM_WEARABLE_INVALID` (`:561`), `ITEM_WEARABLE_LIST_HEADER` (`:560`), `ITEM_WEARABLE_NOT_REGISTERED` (`:562`), `ITEM_WEARABLE_NOT_WEARABLE` (`:563`), plus the prefixes via `GanglandChatUtil:20-32`. Dead in Gangland after the split: `GAVE_AMMO :33`, `GAVE_WEAPON :35`, `AMMO_NOT_IN_INVENTORY :322`, `AMMO_BOUGHT :323`, `AMMO_SOLD :324`, `NOT_ENOUGH_AMMO :325`; `DEAD_USING_WEAPON :328` is still used at `PlayerDeathListener.java:210` and pinned by `MessagesTest.java:107`.

Resources moving: 22 `weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml`, `commands.json` (12 keys, `:2-49`), `module.yml`, `module.properties`. Only 5 of 22 weapon files are registered as expected (`WeaponFileConfig.java:93`; docket T-06).

## 8. Tests

Weapon-module tests (16): `WeaponModuleTest`, `WeaponServiceTest`, `WeaponCloneTest`, `WeaponConsumeShotTest`, `SelectiveFireTest`, `TypeEnumParsingTest`, `durability/DurabilityCalculatorTest`, `projectile/ProjectileStateTest`, `projectile/spread/SpreadManagerTest`, `projectile/recoil/RecoilManagerTest`, `modifiers/ModifierHandlerTest`, `dto/DamageDataTest`, `raytrace/SteppedProjectileTaskTest`, `item/WeaponItemPredicatesTest`, `data/WeaponDataCleanupTaskTest`, fixture `support/WeaponFixtures`. Two reach outside the package: `RecoilManagerTest.java:6` (`RecoilCompatibility`, ViaVersion classpath note) and `ModifierHandlerTest.java:11` (`org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture` from `gangland-core`'s test-jar, `gangland-weapon/pom.xml:119-124`) — Bartizan must ship its own fixture. `WeaponModuleTest.java:5-7` asserts module packages (`WeaponCommand`, `WeaponRepository`, `WeaponQuitCleanupListener`) — rewrite against Bartizan's bootstrap.

Core tests referencing weapons: `command/data/InformationManagerTest.java:43-47` (comment only), `sign/SignManagerContributionTest.java:33` (comment only), `file/configuration/MessagesTest.java:107` (`DEAD_USING_WEAPON`, red if deleted), `data/plugin/PluginDataCleanupServiceTest.java` (string match only). Per `documentation/module-loader.md:185-203`, the `getAllInstances` seams degrade silently — no core test goes red when weapons leave.

## Key files to read (10)

1. `gangland-features/gangland-weapon/.../weapon/WeaponModuleConfig.java` — every core coupling in one file.
2. `.../weapon/WeaponService.java` — API surface, UUID minting, throwable determinism.
3. `.../weapon/WeaponFileConfig.java` — FILE-phase YAML wiring.
4. `gangland-compatibility/version-impl/.../CompatibilityWorker.java` — recoil stack already Keystone-hosted.
5. `Keystone/keystone-common/.../nms/VersionedAdapterLoader.java`.
6. `gangland-features/cops-n-crooks/.../npc/NpcCombatDelegate.java` — heaviest consumer; minimum firing API.
7. `.../weapon/events/projectile/WeaponRaytraceImpactEvent.java`.
8. `.../weapon/sign/AbstractWeaponTradeSign.java` — item identity a replacement must reproduce.
9. `.../weapon/database/WeaponRepository.java` + `WeaponTable.java`.
10. `documentation/module-loader.md` `## Core seams` (`:133-203`).

## Risks the architects must know

- **R1** `ThrowableAction`'s two public static maps are a cross-plugin handshake (`ThrowableAction.java:38,45`; readers `CarDamageListener.java:161`, `WeaponDeathMessageContributor.java:29`; 1-tick TTL `:220`). Replace with events/service in `bartizan-api` first.
- **R2** Five seams fail silently (`MetricsContributor`, `DataCleanupTask`, `NbtTagCatalog`, `ShopDisplayNameProvider`, `DeathMessageContributor`; `module-loader.md:140-141`, `SignContributions.java:26-30`). With Bartizan gone Gangland loses weapon death messages, shop display names and NBT debug output with no error. Each needs an explicit accept-or-re-expose decision.
- **R3** The `weapon` table migration is a real data event (`WeaponTable.java:14`; SQLite path `GanglandDatabase.java:151-157`).
- **R4** `Wearable` is co-owned by weapon and gadget (`Wearable.java:17,58-60`; `JetpackService.java:112-115,138-140`; `JetpackTask.java:215-219`). Decide before writing `bartizan-api`.
- **R5** Six sign types vanish from live worlds (`WeaponSignContribution.java:36-41`; `[VIEW]` branches `WeaponSignViewProvider.java:53-72`). Migration/announcement plan + the four-method `WeaponItemApi`.
- **R6** `ItemKind` is a closed enum; `lootchests/loot_chests.yml` has 167 `weapon:`/`wearable:`/`ammo:` references with no converter once Bartizan leaves — unless Bartizan's converters reach Gangland's registry (E2 §3c pull model).
- **R7** Item registries reverse direction; refresher priorities 10/10/0 (`WeaponModuleConfig.java:204-206`, `:172-181`) must be preserved or a unique weapon rebuilds as a plain unique item (`module-split-2026-09-07/weapon.md:116-121`).
- **R8** `WeaponRaytracer` via `ServicesManager` (`WeaponModuleConfig.java:99`, `NpcCombatDelegate.java:332`): consumers resolve lazily at use time; never cache at bean construction; `softdepend` + null guards.
- **R9** The `viaversion-api` test-classpath trap (`gangland-weapon/pom.xml:84-96`) is docket T-09; Bartizan's pom must keep it.
