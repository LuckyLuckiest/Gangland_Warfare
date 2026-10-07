<!-- A2 · feature-dev:code-architect (opus) · 2026-09-08 · focus: clean long-term boundaries -->

# A2 — Clean architecture blueprint (Bartizan wave)

## 1. Approach (10 lines)

1. Treat this as **three library extractions plus one product split**, not a file move.
2. Every cross-plugin edge becomes a **named SPI on Bukkit's `ServicesManager`** — no statics, no compile edges Gangland→Bartizan.
3. Keystone gains three generic capabilities only: item **vocabularies**, `keystone-npc`, `Plugins:` in the module descriptor.
4. **Delete the 21 `version-*` modules**: recoil becomes one reflective method on Keystone's existing `PacketAdapter`, with today's `setRotation` path as the guaranteed fallback.
5. Civilians are the shared NPC substrate, so they get their own Gangland module `gangland-npc` — this is what makes D5 acyclic without moving turf into cops.
6. Bartizan owns **weapon cadence, weapon death messages and wearables**; Gangland cedes them rather than keeping shim seams.
7. Item identity across the plugin boundary is expressed once: serialize→definition-string→convert. That single round-trip replaces `weaponSimilarityChecker`, `ShopDisplayNameProvider` and the six weapon signs.
8. `ItemConverter` gains a viewer-aware overload so Oriel's `ItemProvider` can converge on one framework.
9. Optimises: long-term boundary correctness, one copy of every generic mechanism, five fewer silent-failure seams, 21 fewer Maven modules.
10. Costs: one extra Gangland module, one reflective NMS path, a Keystone SPI that only Gangland uses today.

## 2. Stream blueprints

### Keystone 1.9.0 (`docs/phase-h8-item-vocabulary-npc.md`)

**`keystone-item`** — back-port Gangland's drift, then add the SPI.
- `ItemSerializerRegistry`: add `CATCH_ALL_PRIORITY = Integer.MIN_VALUE`, `register(Predicate<ItemStack>, ItemSerializer, int priority)`, stable descending re-sort.
- `ItemRefresherRegistry`: same pair (`register(ItemRefresher, int priority)`).
- `ItemConverter`: add `default @Nullable ItemStack convert(@Nullable Player viewer, String type, @Nullable String modifier, Map<String,String> attributes) { return convert(type, modifier, attributes); }`. `ItemParser` gains `Result<ItemStack> tryParse(@Nullable Player viewer, String definition)` and `@Nullable ItemStack parse(@Nullable Player viewer, String definition)`. **Answers Oriel's `ItemProvider(Player, String)`.**
- New package `org.luckyraven.keystone.item.spi`:
  ```java
  public interface ItemVocabulary {
      String namespace();
      void contribute(ItemVocabularyRegistrar registrar);
  }
  public interface ItemVocabularyRegistrar {
      void converter(String type, ItemConverter converter);
      void converter(String[] aliases, ItemConverter converter);
      void serializer(Predicate<ItemStack> claims, ItemSerializer serializer, int priority);
      void refresher(ItemRefresher refresher, int priority);
  }
  public final class ItemVocabularies {   // no Bukkit statics inside
      public static void install(Collection<? extends ItemVocabulary> vocabularies,
                                 ItemConverterRegistry converters,
                                 ItemSerializerRegistry serializers,
                                 ItemRefresherRegistry refreshers);
  }
  ```
- `ItemDefinitions` (same package): `static @Nullable String describe(ItemSerializerRegistry, ItemStack)`, `static boolean sameDefinition(ItemSerializerRegistry, ItemStack, ItemStack)`, `static @Nullable ItemStack pristine(ItemSerializerRegistry, ItemConverterRegistry, ItemStack)`. This is the generic replacement for weapon similarity + shop display cleaning.

**`keystone-common`** — `org.luckyraven.keystone.util.WorldHeights`: `static int min(World)`, `static int max(World)`, cached `MethodHandle` on `World#getMinHeight`, `0` fallback (**Q-B**). `nms.PacketAdapter` gains `void relativeCameraRotation(Player viewer, float deltaYaw, float deltaPitch)`; `ReflectivePacketAdapter` resolves the position-packet shape once per JVM (three known shapes: 1.16–1.20.4 `PacketPlayOutPosition(d,d,d,f,f,Set,int)`, 1.20.5–1.21.1, 1.21.2+ `ClientboundPlayerPositionPacket(int, PositionMoveRotation, Set<Relative>)`), and on failure reports `nms.recoil.unsupported` and degrades to `viewer.setRotation(...)`. `PacketBridge` no-op gains the method.

**New module `keystone-npc`** (root pom after `keystone-module`; `keystone-plugin` shades it; `citizens-repo` added; `citizens-main` provided).
```
org.luckyraven.keystone.npc
├─ AbstractNpc  ctor (JavaPlugin, NPC, Location, NpcNavigationConfig, NpcDifficulty)
├─ NpcBehavior, NpcDifficulty, NpcNavigationConfig
├─ NpcNavigationDelegate, NavStep, NavObstacle, NpcCombatDelegate   (package-private, co-located)
├─ NpcSupport            available() / registry() / isNpc(Entity)
├─ NpcMetadata           TRADER_ID, BANKER_ID, TURF_ID, MARK constants
├─ spi.NpcRangedAttack   isRanged(); isBusy(); boolean tryFire(LivingEntity target);
│                        void triggerReload(); void refreshHeldItem(); void onDestroy()
├─ spi.NpcTargetFilter    boolean isAttackable(LivingEntity)                 default true
├─ spi.NpcMarkDefaults    Map<EntityType,String> defaults()
└─ entity.{EntitySpawner, EntitySpawnerPoint, SpawnConfigProvider, NpcMarkManager}
```
`AbstractNpc.destroy(Consumer<Entity> onDespawn)` replaces `destroy(EntityMarkManager)`; `NpcMarkManager` stores `String` marks (Gangland's `EntityMark` enum and `countForWanted()` stay in Gangland). `keystone-plugin/src/main/resources/plugin.yml` → `softdepend: [Vault, PlaceholderAPI, Citizens]`.

**`keystone-module`** — `ModuleDescriptor` gains `List<String> plugins`; `ModuleDescriptorReader` parses `Plugins:` with a new `PLUGIN_NAME_PATTERN = [A-Za-z0-9_.-]+`; `ModuleResolution.resolve(Collection<ModuleDescriptor>, PluginVersion, Predicate<String> pluginEnabled)` (2-arg overload delegates with `n -> true`), step 2.5, cascading; fault `module.plugin.missing`; `ModuleLoader.load()` supplies `n -> { Plugin p = Bukkit.getPluginManager().getPlugin(n); return p != null && p.isEnabled(); }`.

**Tests** (target +90): `ItemVocabulariesTest`, `ItemDefinitionsTest`, `ItemConverterViewerOverloadTest`, `ItemSerializerPriorityTest`, `ItemRefresherPriorityTest`, `WorldHeightsTest`, `NpcDifficultyTest`, `NavStepPlanTest`, `EntitySpawnerTest`, `NpcSupportTest` (via `BukkitStatics`), `ModuleDescriptorReaderTest.read_pluginsNotAList/read_badPluginName`, `ModuleResolutionTest.pluginMissingCascades`, `ModuleLoaderTest.pluginMissing`. Docs: `docs/keystone-npc.md`, `docs/phase-h8-*.md`, rows in `docs/README.md`, amendments in `docs/keystone-item.md` and `docs/extraction-roadmap.md` §E5.

### Bartizan 0.1.0

```
E:\Programming\java\Bartizan
├─ pom.xml                 ${revision}=0.1.0, flatten, central-release profile, groupId org.luckyraven.bartizan
├─ bartizan-api/           NO NMS, NO Gangland, deps: spigot-api + keystone-common (provided)
└─ bartizan-plugin/        finalName Bartizan-${project.version}
```
`bartizan-api` — `org.luckyraven.bartizan.api`:
- `BartizanApi` (the single `ServicesManager` registration): `WeaponCatalog weapons(); WearableCatalog wearables(); AmmunitionCatalog ammunition(); NpcWeaponFactory npcWeapons(); WeaponItemApi items();`
- `weapon.{Weapon, GunWeapon, MeleeWeapon, WeaponType, SelectiveFire, ProjectileType, ProjectileState}` + `dto.{ProjectileData, AmmunitionData, MeleeData, ScopeData, SoundData, DamageData, ReloadData, SpreadData}`
- `wearable.{Wearable, WearableCatalog}` — `Wearable` carries `Set<String> traits()`, `int traitLevel(String)`, `Map<String,Object> extraTags()`; **no `FuelKey`, no `isJetpack()`**
- `item.WeaponItemApi` — `ItemStack buildItem(String name)`, `boolean isValidWeaponName(String)`, `boolean isSameWeapon(ItemStack, ItemStack)`, `String cleanDisplayName(ItemStack)`
- `npc.NpcWeaponFactory` — `NpcWeaponController create(LivingEntity shooter, String weaponName, double fireRateMultiplier, double aimErrorDegrees)`; `npc.NpcWeaponController` — `boolean tryFire(LivingEntity target)`, `boolean isBusy()`, `boolean isRanged()`, `void reload()`, `void refreshHeldItem()`, `void destroy()` (**Bartizan owns cadence — Q-E**)
- `event.{WeaponEvent, WeaponShootEvent, WeaponRaytraceImpactEvent, WeaponEntityDamageEvent, WeaponKillEntityEvent, WeaponReloadEvent, WeaponReloadStartEvent, WeaponReloadCompleteEvent, WeaponChangeSelectiveFireEvent}`. `WeaponEntityDamageEvent` gains `String weaponName()` and `DamageKind kind()` (`DIRECT, EXPLOSION, FIRE, BIOLOGICAL, MELEE`) — this is what replaces `ThrowableAction`'s two static maps.
- `combat.CombatEligibility` — `boolean canBeHit(Player)`, default `p -> !p.isDead()`; installed by consumers through `ServicesManager`.

`bartizan-plugin` — `org.luckyraven.bartizan.{Bartizan, BartizanContext, config.*, weapon.*, ammo.*, wearable.*, projectile.*, raytrace.*, command.*, listener.*, database.*, item.*, metrics.*}`. Bootstrap mirrors `GanglandContext`: KERNEL (`Diagnostics`, `PacketBridge.install(new ReflectivePacketAdapter())`, `BartizanChatUtil`) → FILE (`weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml`, `settings.yml`, `message/message_en.yml`) → DATABASE (`BartizanDatabase extends DatabaseHandler`, `plugins/Bartizan/database/bartizan.db`) → CONFIG (catalogs, `BartizanItemVocabulary implements ItemVocabulary`, published with `@Bean(publishToServicesManager = true)`) → LISTENER → COMMAND (`/bartizan`, aliases `/btz`, `/weapon`). Resources: `commands.json`, `plugin.yml` (`depend: [Keystone]`, `softdepend: [ViaVersion, PlaceholderAPI, NBTAPI]`). Registers on `ServicesManager`: `BartizanApi`, `ItemVocabulary`, `WeaponRaytracer`. Its own `PlayerDeathListener` at `EventPriority.HIGH` writes the weapon death message. Tests: the 16 weapon tests ported + its own `BukkitRegistryFixture`, keeping the `viaversion-api` provided-test note (T-09).

### Gangland 0.9.0

**Deleted:** `gangland-features/gangland-weapon`, all 21 `gangland-compatibility/*` modules, `gangland-impl/.../compatibility` wiring in `KernelConfig`, `item/wearable/**` + `item/contract/WearableEquipService` + `listener/wearable/WearableEquipListener` (gangland-item), core seams `MetricsContributor`, `DataCleanupTask`, `NbtTagCatalog`, `ShopDisplayNameProvider`, `DeathMessageContributor` (all five: the silent-failure set R2), the six weapon sign types, `Messages.{GAVE_AMMO, GAVE_WEAPON, AMMO_NOT_IN_INVENTORY, AMMO_BOUGHT, AMMO_SOLD, NOT_ENOUGH_AMMO, DEAD_USING_WEAPON}`, `Settings.Block_Regeneration`, `weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml`.

**Moved:** turf NPCs (E3 §3.1 inventory) → `gangland-turf`; traders + bankers (E3 §4.1) → new `gangland-features/gangland-npc-shops` (id `npcshops`); civilians (`npc/civilian/**`, `npc/civilians.yml`, `CivilianSpawnerRepository/Table`, `listener/civilian/**`, `listener/npc/CivilianDeathRewardListener`, `command/civilians/**`) → new `gangland-features/gangland-npc` (id `npc`, package `org.luckyraven.gangland.npc`), which also owns `BartizanRangedAttack implements NpcRangedAttack`, `DownedTargetFilter implements NpcTargetFilter`, `GanglandMarkDefaults implements NpcMarkDefaults`, `EntityMark`.

**module.yml matrix:** `npc` → `Plugins: [Bartizan]`; `cops-n-crooks` → `Depends: [npc]`, `Plugins: [Bartizan]`; `gangland-turf` → `Depends: [npc]`; `gangland-gadget` → `Plugins: [Bartizan]`; `npcshops`, `mail` → neither. No cycles.

**Core changes:** `ItemConfig.itemConverterRegistry/itemSerializerRegistry/itemRefresherRegistry` become one `@Bean itemVocabularies(...)` that calls `ItemVocabularies.install(servicesManager.getRegistrations(ItemVocabulary.class)…)` after the core's own registrations; `GameplayConfig.lootChestLoader` defers `initializeAll()` to `@PostConstruct` (T-11). `sign-api` gains generic `ItemBuySign`/`ItemSellSign` (`[ITEM-BUY]`, `[ITEM-SELL]`, definition string on line 3, similarity via `ItemDefinitions.sameDefinition`) and `LegacySignRewriter` mapping `[WEAPON-BUY]`→`[ITEM-BUY]`+`weapon:<n>` (six mappings, `settings.yml` `Signs.Legacy_Aliases`). `GanglandShopDisplayResolver` uses `ItemDefinitions.pristine(...)`. `plugin.yml`: `depend: [Keystone, NBTAPI]`, `softdepend: [PlaceholderAPI, Vault, ViaVersion, Citizens, Bartizan]`; `Gangland.java:179` Citizens → `Type.SOFT`; the two core death listeners use `NpcSupport.isNpc`.

## 3. Answers

**Q-A** Promote with the priority overloads back-ported first, then delete Gangland's nine duplicates. The cross-plugin service interface is **`ItemVocabulary`** (`keystone-item`, `org.luckyraven.keystone.item.spi`), published by Bartizan on the `ServicesManager` and folded into Gangland's per-consumer registries by `ItemVocabularies.install(...)` inside `gangland-impl/.../config/ItemConfig.java`. Yes to the viewer-aware `ItemConverter` overload (default method, source- and binary-compatible), so Oriel's `ItemProvider` can become `(viewer, key) -> parser.parse(viewer, key)`.

**Q-B** A cached-`MethodHandle` helper `org.luckyraven.keystone.util.WorldHeights` in `keystone-common`, `0` fallback. Oriel stays free at 1.16.5.

**Q-C** Civilians move to a new Gangland runtime module **`gangland-npc`** (id `npc`). Turf declares `Depends: [npc]` and spawns through `CivilianSpawnManager.spawnCivilian(location, typeId)` + `CivilianNpc.transitionTo(CivilianState.COMBAT)` exactly as today, with `quartermaster`/`turf_defender` type ids in `gangland-npc`'s `npc/civilians.yml`. Cops also declares `Depends: [npc]`. Graph: `npc ← cops`, `npc ← turf` — acyclic, and turf never names cops.

**Q-D** Bartizan owns `Wearable` (`bartizan-api`, `org.luckyraven.bartizan.api.wearable.Wearable`). The fuel coupling is severed: the YAML's fuel keys become entries in a generic `Extra_Tags:` block that Bartizan writes blindly, and `WearableTrait.FUEL_EFFICIENT` becomes the string trait `"fuel_efficient"` read via `wearable.traitLevel("fuel_efficient")`. Gadget compiles against `bartizan-api` (provided) plus Gangland's own `FuelService` (stays in `gangland-item`), reading fuel from item NBT as it already does.

**Q-E** Discovery is `ServicesManager` only — one `BartizanApi` registration, resolved lazily at use time (never cached at bean construction, R8). No static `Bartizan.api()`, no Keystone bean. `ThrowableAction`'s two statics are replaced by `WeaponEntityDamageEvent.weaponName()/kind()`. Death messages: **Bartizan owns them** at `EventPriority.HIGH` with its own message file; Gangland deletes `DeathMessageContributor` and `DEAD_USING_WEAPON`. `WeaponItemApi` ships in `bartizan-api` for third parties, but Gangland does not use it — signs and shops go through `ItemDefinitions`. `NpcRangedAttack` is implemented once, in `gangland-features/gangland-npc/.../npc/combat/BartizanRangedAttack.java`. **Yes, Bartizan owns firing cadence** via `NpcWeaponController.tryFire`, with `fireRateMultiplier`/`aimError` passed in from `NpcDifficulty`.

**Q-F** Two Maven modules (`bartizan-api`, `bartizan-plugin`), **zero** version modules — recoil goes through `PacketAdapter.relativeCameraRotation` with the `setRotation` fallback. Shading policy: nothing shaded (Keystone provided, NBT reflective via `NbtBridge`). Own `settings.yml` + `message/message_en.yml` on `YamlMessageProvider`/`LanguageLoader`; own `BartizanDatabase` under `plugins/Bartizan/database/`. `weapon`-table migration: a one-shot `WeaponTableImportTask` reading Gangland's SQLite file when present, guarded by a `.weapon-import-done` marker, plus a documented MySQL `INSERT…SELECT` in `documentation/migration-0.9.0.md`.

**Q-G** The 167 loot-chest refs stay verbatim and resolve through the pulled `ItemVocabulary`. The six sign types are replaced by one generic `[ITEM-BUY]`/`[ITEM-SELL]` pair in `gangland-ui/sign-api`, with `LegacySignRewriter` keeping placed signs alive on live worlds. Dead `Messages` members and the `Block_Regeneration` `Settings` section are deleted (`MessagesTest.java:107` flips). Docs: rewrite the `## Core seams` section of `documentation/module-loader.md` and add `documentation/bartizan-integration.md`.

**Q-H** Ship `Plugins:` exactly as E2 §4 specifies (new `PLUGIN_NAME_PATTERN`, injected `Predicate<String>`, `module.plugin.missing`), and ship `softdepend: [Bartizan, Citizens]` in `gangland-impl/src/main/resources/plugin.yml` at the same time — the key is a fail-fast guard, the softdepend is the ordering and warning fix. Both required; neither substitutes.

**Q-I** `keystone-npc` gets `AbstractNpc` (ctor takes `JavaPlugin`), the two delegates + `NavStep`/`NavObstacle` package-private and co-located, `NpcBehavior`, `NpcDifficulty`, `NpcNavigationConfig`, `NpcSupport`, `NpcMetadata`, the three SPIs and `entity/*`. `NpcMarkManager` moves (string-typed); `EntityMark` and `countForWanted()` do **not** — they stay in `gangland-npc`. `npc.citizens.missing` is raised by each NPC-owning module's `onEnabled` via `Diagnostics.active()` before any spawn task arms.

**Q-J** Keystone 1.9.0 `mvn clean install` first (everything else needs it in `~/.m2`), then rebuild **0.8.4 unchanged** against it as gate K3 — 1.9.0 is additive, so the existing five-module set keeps working and stays shippable while Bartizan is written. Bartizan 0.1.0 installs next (`bartizan-api` must be in `~/.m2` before any Gangland module compiles). Gangland 0.9.0 last, on a new branch off 0.8.4.

## 4. Build order

| # | Repo | Step | Gate command |
|---|---|---|---|
| 1 | Keystone | item drift back-port + `ItemVocabulary` + `ItemDefinitions` + viewer overload | `mvn -pl keystone-item -am test` |
| 2 | Keystone | `WorldHeights` + `PacketAdapter.relativeCameraRotation` | `mvn -pl keystone-common -am test` |
| 3 | Keystone | `keystone-npc` module | `mvn -pl keystone-npc -am test` |
| 4 | Keystone | `Plugins:` descriptor key | `mvn -pl keystone-module -am test` |
| 5 | Keystone | docs + version 1.9.0 | `mvn clean install` |
| 6 | Gangland 0.8.4 | regression only — bump `<keystone.version>` | `mvn clean package` then smoke S1–S8 |
| 7 | Bartizan | `bartizan-api` | `mvn -pl bartizan-api -am install` |
| 8 | Bartizan | `bartizan-plugin` + ported tests | `mvn clean install` |
| 9 | Gangland 0.9.0 | `gangland-npc` module (civilians) | `mvn -pl gangland-features/gangland-npc -am test` |
| 10 | Gangland 0.9.0 | turf NPCs → turf; npc-shops split (T-12 first) | `mvn -pl gangland-features/gangland-turf,gangland-features/gangland-npc-shops -am test` |
| 11 | Gangland 0.9.0 | delete weapon + `version-*`; generic signs; item vocabulary pull | `mvn clean package` |
| 12 | All | smoke matrix on the new topology | `smoke/smoke.py` rows S1–S8 |

## 5. Risks and extra cost over "minimal"

Extra cost: one additional Gangland module (`gangland-npc`, ~35 files moved, +1 `module.yml`, +1 pom, +1 `gangland-build` artifactItem); a reflective recoil path that only in-server smoke can verify; an `ItemVocabulary` SPI with a single producer today; a generic sign type plus `LegacySignRewriter` that minimal would skip by accepting broken signs. Roughly +3 executor task groups.

Risks: (1) reflective recoil regressing feel on an untested revision — mitigated by the always-available `setRotation` fallback and `nms.recoil.unsupported`; (2) Bartizan-owned cadence changing cop fire rhythm — pin `SelectiveFireTest` semantics before the port; (3) the `ItemVocabulary` pull ordering depends on `softdepend`, so a server that removes it silently loses `weapon:` loot — add a boot log line naming every folded vocabulary; (4) the civilians module is a new load-order dependency for both cops and turf — S6-style row needed with `npc` absent; (5) `ItemDefinitions.sameDefinition` must reproduce throwable UUID determinism — pin with a red-first test before deleting `weaponSimilarityChecker`.

## 6. Read these eight first

1. `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/WeaponModuleConfig.java`
2. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/NpcCombatDelegate.java`
3. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/NpcNavigationDelegate.java`
4. `E:\Programming\java\Keystone\keystone-item\src\main\java\org\luckyraven\keystone\item\ItemConverterRegistry.java`
5. `E:\Programming\java\Keystone\keystone-module\src\main\java\org\luckyraven\keystone\module\ModuleResolution.java`
6. `E:\Programming\java\Keystone\keystone-common\src\main\java\org\luckyraven\keystone\nms\PacketAdapter.java`
7. `gangland-impl/src/main/java/org/luckyraven/gangland/config/ItemConfig.java`
8. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/config/CopsNCrooksModuleConfig.java`
