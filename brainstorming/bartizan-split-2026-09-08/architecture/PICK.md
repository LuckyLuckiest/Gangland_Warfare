# The pick — binding for every planner and executor

**User decision 2026-09-08 (after reading A1/A2/A3):** build **A2 "clean architecture" as written** (`A2-clean.md`), with these three refinements. Where A2 and this file disagree, this file wins; where this file is silent, A2 wins; A1/A3 are reference only.

## Refinements

1. **Recoil is reflective, with `setRotation` only as the fallback.** The user's own testing: `Player#setRotation` works but
   teleports the player and feels rough on older clients (fine on 1.21.x through ViaBackwards). So Keystone 1.9.0 ships
   `PacketAdapter.relativeCameraRotation(Player viewer, float deltaYaw, float deltaPitch)` implemented in
   `ReflectivePacketAdapter` for the three known position-packet shapes (1.16–1.20.4 `PacketPlayOutPosition`,
   1.20.5–1.21.1, 1.21.2+ `ClientboundPlayerPositionPacket` with `PositionMoveRotation` + `Set<Relative>`), resolved once
   per JVM. The 20 `Recoil_1_xx_Ry` adapter classes in `gangland-compatibility/version-*` are the **reference
   implementations** for those shapes — the planner must point the executor at them (and at
   `documentation/developer/compatibility.md`) before they are deleted. Unsupported shape → fault
   `nms.recoil.unsupported` once + `setRotation` fallback. Unit tests cover the shape resolver with synthetic classes;
   **in-game recoil feel on 1.21.11 and one older client through ViaBackwards is the user's check** (listed on the board).
   Bartizan ships **zero** version modules.
2. **Civilians module is `gangland-features/gangland-civilians`, module id `civilians`, package
   `org.luckyraven.gangland.civilians`** (not `gangland-npc`/`npc`). It owns `npc/civilian/**`, `npc/civilians.yml`,
   `CivilianSpawnerRepository/Table`, `listener/civilian/**`, `CivilianDeathRewardListener`, `command/civilians/**`,
   `EntityMark` + `countForWanted()`, and the Bartizan-backed `BartizanRangedAttack implements NpcRangedAttack`,
   `DownedTargetFilter implements NpcTargetFilter`, `GanglandMarkDefaults implements NpcMarkDefaults`. `module.yml`:
   `Id: civilians`, `Plugins: [Bartizan]`. cops-n-crooks and gangland-turf declare `Depends: [civilians]`.
3. **Signs:** generic `[ITEM-BUY]` / `[ITEM-SELL]` in `gangland-ui/sign-api` taking any item definition string
   (`weapon:rifle`, `unique:x`, `car:y`, `money:…`), similarity through `ItemDefinitions.sameDefinition`, plus
   `LegacySignRewriter` with `settings.yml` `Signs.Legacy_Aliases` mapping the six old names (`weapon-buy/sell`,
   `ammo-buy/sell`, `wearable-buy/sell`) so placed signs keep working when Bartizan is installed.

## Cross-stream contract (names every checklist must use verbatim)

**Keystone 1.9.0** — branch `phase-h8-item-npc` (cut from `phase-h7-module-loader` at 23950c0), `<revision>1.9.0</revision>`, phase doc `docs/phase-h8-item-npc.md`.
- `keystone-item`: back-port `CATCH_ALL_PRIORITY` + `register(Predicate<ItemStack>, ItemSerializer, int)` into `ItemSerializerRegistry`, `register(ItemRefresher, int)` into `ItemRefresherRegistry` (stable descending sort, exactly Gangland's `gangland-infra/gangland-item` semantics); `ItemConverter.convert(@Nullable Player viewer, String type, @Nullable String modifier, Map<String,String> attributes)` default overload; `ItemParser.tryParse(@Nullable Player, String)` / `parse(@Nullable Player, String)`; new package `org.luckyraven.keystone.item.spi` with `ItemVocabulary { String namespace(); void contribute(ItemVocabularyRegistrar r); }`, `ItemVocabularyRegistrar { converter(String, ItemConverter); converter(String[], ItemConverter); serializer(Predicate<ItemStack>, ItemSerializer, int); refresher(ItemRefresher, int); }`, `ItemVocabularies.install(Collection<? extends ItemVocabulary>, ItemConverterRegistry, ItemSerializerRegistry, ItemRefresherRegistry)` (no Bukkit statics inside; the caller passes `Bukkit.getServicesManager().getRegistrations(ItemVocabulary.class)`), `ItemDefinitions.describe(ItemSerializerRegistry, ItemStack)` / `sameDefinition(ItemSerializerRegistry, ItemStack, ItemStack)` / `pristine(ItemSerializerRegistry, ItemConverterRegistry, ItemStack)`.
- `keystone-common`: `org.luckyraven.keystone.util.WorldHeights.min(World)` / `max(World)`; `org.luckyraven.keystone.nms.PacketAdapter.relativeCameraRotation(Player, float, float)` + `ReflectivePacketAdapter` implementation + `PacketBridge` no-op; fault `nms.recoil.unsupported`.
- `keystone-npc` (new Maven module after `keystone-module`; deps `keystone-common`, `keystone-bean`, `keystone-persistence`, `citizens-main` provided from `https://maven.citizensnpcs.co/repo`; shaded by `keystone-plugin`): `org.luckyraven.keystone.npc.{AbstractNpc, NpcBehavior, NpcDifficulty, NpcNavigationConfig, NpcNavigationDelegate, NavStep, NavObstacle, NpcCombatDelegate, NpcSupport, NpcMetadata}`, `npc.spi.{NpcRangedAttack, NpcTargetFilter, NpcMarkDefaults}`, `npc.entity.{EntitySpawner, EntitySpawnerPoint, SpawnConfigProvider, NpcMarkManager}` (string-typed marks), `npc.event.NpcEvent`. `AbstractNpc(JavaPlugin, NPC, Location, NpcNavigationConfig, NpcDifficulty)`, `destroy(Consumer<Entity> onDespawn)`. `NpcRangedAttack { boolean isRanged(); boolean isBusy(); boolean tryFire(LivingEntity target); void triggerReload(); void refreshHeldItem(); void onDestroy(); }`, `NpcTargetFilter { boolean isAttackable(LivingEntity); }` (default all), `NpcMarkDefaults { Map<EntityType,String> defaults(); }`. `NpcSupport.available()/registry()/isNpc(Entity)`; `NpcMetadata.TRADER_ID = "gangland.trader.id"`, `BANKER_ID`, `TURF_ID = "gangland.turfpowerup.turfid"`. Fault `npc.citizens.missing`.
- `keystone-module`: `ModuleDescriptor.plugins()` + `PLUGIN_NAME_PATTERN = [A-Za-z0-9_.-]+`; `Plugins:` key; `ModuleResolution.resolve(Collection<ModuleDescriptor>, PluginVersion, Predicate<String> pluginEnabled)` (2-arg overload kept); fault `module.plugin.missing`; `ModuleLoader.load()` predicate = plugin present **and** enabled.
- `keystone-plugin` `plugin.yml`: `softdepend: [Vault, PlaceholderAPI, Citizens]`.

**Bartizan 0.1.0** — repo `E:\Programming\java\Bartizan` (new git repo, `master`), Maven, groupId `org.luckyraven.bartizan`, modules `bartizan-api` and `bartizan-plugin` only, `<revision>0.1.0</revision>`, flatten + `release` profile copied from Keystone. Packages `org.luckyraven.bartizan.api.{BartizanApi, weapon.*, weapon.dto.*, ammo.Ammunition, wearable.{Wearable, WearableCatalog}, item.WeaponItemApi, npc.{NpcWeaponFactory, NpcWeaponController}, event.*, combat.CombatEligibility, raytrace.{WeaponRaytracer, WeaponShooting, RaytraceRequest}}`; plugin `org.luckyraven.bartizan.{Bartizan, bootstrap.BartizanContext, config.*, …}`. `ServicesManager` registrations: `BartizanApi`, `ItemVocabulary` (namespace `bartizan`; converters `weapon`, `ammunition` + alias `ammo`, `wearable`; serializers @0; refreshers weapon @10, wearable @10, ammunition @0), `WeaponRaytracer` (same key as today). Command `/bartizan` (aliases `/btz`, `/weapon`), own `commands.json`, own `settings.yml` + `message/message_en.yml` (Keystone `YamlMessageProvider` + `LanguageLoader`), data folder `plugins/Bartizan/`, database `plugins/Bartizan/database/bartizan.db` (table `weapon` unchanged) + `WeaponTableImportTask` with `.weapon-import-done` marker + `documentation/migration.md` MySQL note. `plugin.yml`: `name: Bartizan`, `api-version: 1.16`, `depend: [Keystone]`, `softdepend: [ViaVersion, PlaceholderAPI, NBTAPI]`. Bartizan owns the weapon death message (own listener at `EventPriority.HIGH`), NPC firing cadence (`NpcWeaponController`), wearables (no `FuelKey`, `Extra_Tags:` + string traits), `WeaponEntityDamageEvent.weaponName()/kind()` replaces `ThrowableAction`'s statics.

**Gangland 0.9.0** — branch `0.9.0` cut from `0.8.4` after gate GA; root `<revision>0.9.0</revision>`, `<keystone.version>1.9.0</keystone.version>`, new `<bartizan.version>0.1.0</bartizan.version>`; every `module.yml` `Host_Api: 0.9`. Modules: `mail` (unchanged), `turf` (`Depends: [civilians]`, owns turf NPCs), `civilians` (`Plugins: [Bartizan]`), `copsncrooks` (`Depends: [turf, civilians]`, `Plugins: [Bartizan]`), `gadget` (`Plugins: [Bartizan]`), `npcshops` (no Depends/Plugins). `plugin.yml`: `depend: [Keystone, NBTAPI]`, `softdepend: [PlaceholderAPI, Vault, ViaVersion, Citizens, Bartizan]`. Deleted: `gangland-features/gangland-weapon`, `gangland-compatibility/**`, the nine generic `gangland-item` classes + `dsl/ItemDslAdapter` + `item/wearable/**` + `contract/WearableEquipService`, the five silent seams (`MetricsContributor`, `DataCleanupTask`, `NbtTagCatalog`, `ShopDisplayNameProvider`, `DeathMessageContributor`), `TurfNpcContract(s)`, the six weapon sign types, the seven dead `Messages` members, `Settings.Block_Regeneration`, weapon YAML. `ItemKind implements org.luckyraven.keystone.item.ItemKind` minus the three weapon kinds. `ItemConfig` gains the `itemVocabularies(...)` bean; `GameplayConfig.lootChestLoader` defers to `@PostConstruct` (T-11). Sign-api: `ItemBuySign`/`ItemSellSign` + `LegacySignRewriter`. Core `isNPC` sites → `NpcSupport.isNpc`; `Gangland.java` Citizens dependency → SOFT with an `isEnabled()` check.

## Build order (from A2, with the 0.8.4 regression gate kept)

1. Keystone: item back-port + `ItemVocabulary` + `ItemDefinitions` + viewer overload → `mvn -pl keystone-item -am test`
2. Keystone: `WorldHeights` + `PacketAdapter.relativeCameraRotation` → `mvn -pl keystone-common -am test`
3. Keystone: `keystone-npc` → `mvn -pl keystone-npc -am test`
4. Keystone: `Plugins:` → `mvn -pl keystone-module -am test`
5. Keystone: docs + 1.9.0 → `mvn clean install` → `keystone-plugin/target/Keystone-1.9.0.jar`
6. Gangland 0.8.4 regression: bump `<keystone.version>` to 1.9.0 on a throwaway basis, `mvn clean package`, smoke S1–S8 (proves 1.9.0 is additive)
7. Bartizan: `bartizan-api` → `mvn -pl bartizan-api -am install`
8. Bartizan: `bartizan-plugin` + ported tests → `mvn clean install` → `bartizan-plugin/target/Bartizan-0.1.0.jar`
9. Gangland 0.9.0: `gangland-civilians` → `mvn -pl gangland-features/gangland-civilians -am test`
10. Gangland 0.9.0: turf NPCs → turf; `gangland-npc-shops` → module tests
11. Gangland 0.9.0: delete weapon + compatibility; generic signs; vocabulary pull; plugin.yml → `mvn clean package`
12. Smoke matrix on the new topology (phase D)

## Explicitly deferred (named, not silent)

Bartizan Central publication (0.2.0); Oriel migrating its `ItemProvider` onto the viewer overload (Oriel's own wave); a `keystone-sign` extraction (Keystone 2.0.0).

## Amendments (orchestrator, after REVIEW-consistency.md, 2026-09-08)

- **Four silent seams are deleted, not five:** `NbtTagCatalog` stays in `gangland-impl` — its producer and consumer are
  both core (loot-chest wand tags). `MetricsContributor`, `DataCleanupTask`, `ShopDisplayNameProvider` and
  `DeathMessageContributor` go.
- **Ruling (a):** `GangAllyWeaponImpactListener` lives in `gangland-civilians` (`listener.gang`); the Gangland core,
  `gangland-core`, `gangland-infra/*` and `gangland-ui/*` carry **no** `org.luckyraven.bartizan` reference at all (gate grep).
- **Ruling (b):** Bartizan's `NpcWeaponController extends NpcRangedAttack` is the only implementation of the SPI;
  `gangland-civilians` keeps a `BartizanNpcWeapons` factory hook that resolves `BartizanApi` lazily at spawn time and
  returns `NpcRangedAttack.none()` when Bartizan is absent. `NpcRangedAttack` gains `default void tick()` (P1 B3).
- **Ruling (c):** P3's OQ-1 (`TurfFriendlyFireListener` stays in cops-n-crooks), OQ-2 (generic item signs widen the
  existing `BuySign`/`SellSign` in `gangland-impl`, registered additionally as `item-buy`/`item-sell`;
  `LegacySignRewriter` + `Signs.Legacy_Aliases` kept) and OQ-4 (`NbtTagCatalog` stays) are accepted.
- **Rulings (d)–(f):** P2's defaults (nested `Extra_Tags`, ViaVersion protocol gate dropped, `aimErrorDegrees`
  reserved), P1's defaults (`relativeCameraRotation` as a default method, `keystone-npc` out of the Central profile,
  `canUseWeapons()` renamed, nullable `getMark`, recoil sign transform caller-side, `item.vocabulary.failed`), and the
  greppable boot line is Gangland's `Item vocabularies installed: …`.
- **User check list:** Bartizan's bStats plugin id (ships as `0` with a TODO; register on bstats.org and set it in one place).
