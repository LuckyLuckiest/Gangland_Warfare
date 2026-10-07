<!-- A1 · feature-dev:code-architect (opus) · 2026-09-08 · focus: minimal change, maximum reuse -->

# A1 — Minimal-change blueprint (Bartizan wave)

## 1. Approach in ten lines

1. Move code, don't rewrite it: `git mv` + package rename is the default operation in all three repos.
2. Honour D3 by executing the *already-shipped* keystone-item promotion (import swap, 9 file deletions), nothing more.
3. Add exactly **three** new Keystone seams (`ItemTypeProvider`, `NpcRangedAttack`, `NpcTargetFilter`) plus `NpcSupport`, `WorldHeights`, `Plugins:`.
4. Reuse Bukkit `ServicesManager` for every cross-plugin edge — no new discovery mechanism, no static API holder.
5. One aggregate service `BartizanApi` so consumers do one lookup instead of six.
6. Civilians stay in cops-n-crooks; turf gets the turf-NPC *files* and an inverted spawner holder — D5 without moving 30 civilian files.
7. `EntityMark`/`EntityMarkManager` stay in cops; `AbstractNpc.destroy` takes a `Consumer<Entity>`.
8. The 21 recoil compatibility modules move verbatim; no reflective `PacketBridge` rewrite.
9. Gangland's three weapon-aware seam implementations move into cops-n-crooks (the only unit that already has Bartizan on its classpath) — deliberate wart, documented.
10. Optimises: shippable soonest, reviewable diffs, no behaviour change outside the weapon boundary. Sacrifices: two mislocated concerns and one accepted feature loss (weapon signs).

## 2. Stream blueprints

### 2.1 Keystone 1.9.0 (branch `phase-h8-npc-item-plugins`)

**New module `keystone-npc`** (root `pom.xml:42-52` after `keystone-module`; deps `keystone-common`, `keystone-bean`, `keystone-persistence`, `citizens-main` provided; repo `citizens-repo` = `https://maven.citizensnpcs.co/repo`; shaded by `keystone-plugin/pom.xml:62-87`).

`org.luckyraven.keystone.npc`: `AbstractNpc` (ctor `(JavaPlugin plugin, NPC npc, Location spawn, NpcNavigationConfig nav, NpcDifficulty difficulty)`; `destroy(Consumer<Entity> onDespawn)`; `heldWeapon`/`reloading`/`setHeldWeapon` deleted), `NpcBehavior`, `NpcDifficulty`, `NpcNavigationConfig`, `NpcNavigationDelegate`, `NavStep`, `NavObstacle` (co-located, package-private), `NpcCombatDelegate` (weapon-free), `NpcSupport`, `NpcMetadata`, `event.NpcEvent`; `npc.entity`: `EntitySpawner`, `EntitySpawnerPoint`, `SpawnConfigProvider`.

```java
package org.luckyraven.keystone.npc.spi;
public interface NpcRangedAttack {
    boolean isRanged();
    boolean isBusy();
    boolean tryFire(LivingEntity shooter, LivingEntity target, NpcDifficulty difficulty);
    void triggerReload();
    void refreshHeldItem(LivingEntity holder);
    void onDestroy();
    NpcRangedAttack NONE = /* melee-only no-op */;
}
public interface NpcTargetFilter {
    boolean isAttackable(LivingEntity target);
    NpcTargetFilter ALL = target -> true;
}
```
```java
public final class NpcSupport {
    public static boolean available();
    public static Optional<NPCRegistry> registry();
    public static boolean isNpc(@Nullable Entity entity);
}
public final class NpcMetadata {
    public static final String TRADER_ID = "gangland.trader.id";
    public static final String BANKER_ID = "gangland.banker.id";
    public static final String TURF_ID   = "gangland.turfpowerup.turfid";
}
```

**keystone-common**: `org.luckyraven.keystone.util.WorldHeights` — `public static int minHeight(World world)` / `maxHeight(World world)`, cached `MethodHandle` on `World#getMinHeight`, fallback `0` / `world.getMaxHeight()`.

**keystone-item**: back-port `CATCH_ALL_PRIORITY` + `register(Predicate<ItemStack>, ItemSerializer, int)` and `register(ItemRefresher, int)` with the stable descending sort into `ItemSerializerRegistry` / `ItemRefresherRegistry`. New package `org.luckyraven.keystone.item.service`:

```java
public interface ItemTypeProvider {
    String providerId();
    Set<String> types();                                            // "weapon","ammo","ammunition","wearable"
    @Nullable ItemStack create(String type, @Nullable String modifier, Map<String,String> attributes);
    @Nullable String serialize(ItemStack item);                     // null = not mine
    @Nullable ItemStack refresh(ItemStack source, @Nullable Player viewer);
    default int refreshPriority() { return 0; }
    default int serializerPriority() { return 0; }
}
public final class ItemTypeProviders {
    public static int installAll(ItemConverterRegistry converters, ItemSerializerRegistry serializers,
                                 ItemRefresherRegistry refreshers);   // pulls Bukkit ServicesManager registrations
}
```

**keystone-module**: `ModuleDescriptor` gains `List<String> plugins` between `depends` and `artifact` + `PLUGIN_NAME_PATTERN = [A-Za-z0-9_.-]+`; `ModuleDescriptorReader.parse` reads `Plugins`; `ModuleResolution.resolve(Collection<ModuleDescriptor>, PluginVersion, Predicate<String> pluginEnabled)` with a 2-arg overload delegating `n -> true`, step 2.5 between the `Host_Api` filter and the dependency fixpoint; new fault `module.plugin.missing` (`Fault.dependency`, context `module`/`jar`/`plugin`); `ModuleLoader.load()` passes `n -> { Plugin p = Bukkit.getPluginManager().getPlugin(n); return p != null && p.isEnabled(); }`.

**keystone-plugin** `plugin.yml:7` → `softdepend: [Vault, PlaceholderAPI, Citizens]`.

**Tests** (`keystone-testkit`): `WorldHeightsTest`, `NpcSupportTest` (BukkitStatics), `NpcDifficultyTest`, `EntitySpawnerTest`, `ItemTypeProvidersTest`, `ItemSerializerRegistryPriorityTest`, `ItemRefresherRegistryPriorityTest`, `ModuleDescriptorReaderTest.read_plugins*` (4 cases), `ModuleResolutionTest.pluginMissingCascades`, `ModuleLoaderTest` end-to-end. **Docs**: `docs/phase-h8-npc-item-plugins.md`, rows in `docs/README.md`, `docs/keystone-module.md` fault table, `docs/keystone-item.md` provider recipe, `docs/keystone-npc.md`.

### 2.2 Bartizan 0.1.0 (`E:\Programming\java\Bartizan`)

Maven modules: `bartizan-api`, `bartizan-plugin`, `bartizan-compat/version-impl`, `bartizan-compat/version-1_16_R1 … version-1_21_R7` (20, moved verbatim; only parent/groupId/package change). Root pom copies Keystone's `${revision}` + flatten + `release` profile; `<revision>0.1.0</revision>`; keeps `gangland-weapon/pom.xml:84-96`'s viaversion-api test-classpath block (T-09).

`bartizan-api` (zero NMS symbols, zero shading): `org.luckyraven.bartizan.api` — `BartizanApi`, `WeaponService`, `AmmunitionService`, `WearableService`, `WeaponItemApi`, `Weapon`, `GunWeapon`, `MeleeWeapon`, `WeaponType`, `ProjectileType`, `SelectiveFire`, `ProjectileData`, `AmmunitionData`, `MeleeData`, `ScopeData`, `SoundData`, `DamageData`, `ReloadData`, `SpreadData`, `ProjectileState`, `Ammunition`, `Wearable`, `WearableTrait`; `…api.event` — the nine event classes; `…api.raytrace` — `WeaponRaytracer`, `WeaponShooting`, `RaytraceRequest`.

```java
public interface BartizanApi {
    WeaponService weapons();  AmmunitionService ammunition();  WearableService wearables();
    WeaponItemApi items();    WeaponRaytracer raytracer();
    @Nullable String lastKillWeapon(UUID victimId);
    double takePendingVehicleExplosionDamage(UUID entityId);
}
public interface WeaponItemApi {
    @Nullable ItemStack buildItem(String weaponName);
    boolean isValidWeaponName(String name);
    boolean isSameWeapon(@Nullable Player viewer, ItemStack a, ItemStack b);
    @Nullable String cleanDisplayName(ItemStack item);
}
```

`bartizan-plugin`: `org.luckyraven.bartizan.Bartizan extends JavaPlugin`; `bootstrap.BartizanContext` (copy of `GanglandContext`, no `ModuleLoader`); configurations `BartizanKernelConfig` (KERNEL: `Diagnostics`, `CompatibilityWorker`, `BartizanChatUtil`), `BartizanFileConfig` (FILE: `settings.yml`, `message/message_en.yml`, 22 `weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml`), `BartizanDatabaseConfig` (DATABASE: `BartizanDatabase extends DatabaseHandler`, `plugins/Bartizan/database/bartizan.db`, table `weapon` unchanged, `WeaponTableImporter`), `WeaponConfig` (CONFIG: today's `WeaponModuleConfig` minus every core seam; registers `BartizanApi`, `WeaponService`, `WearableService`, `WeaponRaytracer`, `BartizanItemTypeProvider` on the `ServicesManager`), `BartizanCommandConfig` (COMMAND). Root command `/bartizan` (alias `/btz`) with `weapon`, `ammo`, `wearable`, `debug`, `reload`; own `commands.json`, `Messages` enum on Keystone `YamlMessageProvider`. `plugin.yml`: `name: Bartizan`, `api-version: 1.16`, `depend: [Keystone, NBTAPI]`, `softdepend: [PlaceholderAPI, Vault, ViaVersion]`. Tests: the 16 weapon tests + own `BukkitRegistryFixture` copy + `BartizanApiSurfaceTest`.

### 2.3 Gangland 0.9.0 (branch off `0.8.4`)

**Deleted**: `gangland-features/gangland-weapon/**`, `gangland-compatibility/**` (reactor entries, `gangland-build/pom.xml:195-296` shade list, `KernelConfig.java:148-150`), `gangland-infra/gangland-item`'s nine generic classes + `dsl/ItemDslAdapter` + `wearable/{Wearable,WearableTrait}` + `contract/WearableEquipService` + `listener/wearable/WearableEquipListener`, `settings.yml:643-656`, six `Messages` members, `weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml`.

**Migrated (mechanical)**: ~104 files swap `org.luckyraven.gangland.item.{ItemParser,ItemConverter,ItemConverterRegistry,ItemSerializer(+Registry),ItemRefresher(+Registry),MaterialItemSerializer}` → `org.luckyraven.keystone.item.*`; `ItemKind` becomes `public enum ItemKind implements org.luckyraven.keystone.item.ItemKind` minus `WEAPON/AMMUNITION/WEARABLE`. `ItemConfig` adds `ItemTypeProviders.installAll(...)` inside `itemConverterRegistry`'s downstream registration bean; `GameplayConfig.lootChestLoader` (`:271-278`) defers `initializeAll()` to `@PostConstruct` (T-11).

**Moves**: turf-NPC files (E3 §3.1, 20 files + `turf/turf_npcs.yml` + repository/table + `turf_powerupnpc` commands.json key) → `gangland-turf`, folded into `TurfModuleConfig`; trader+banker (E3 §4.1) → new module `gangland-npc-shops` (`Id: npcshops`, `Main: org.luckyraven.gangland.npcshops.NpcShopsModule`); `GangAllyWeaponImpactListener`, `WeaponDeathMessageContributor`, `WeaponShopDisplayNameProvider` → `cops-n-crooks`.

**module.yml**: cops `Depends: [turf]`, `Plugins: [Bartizan]`; gadget `Depends: []`, `Plugins: [Bartizan]`; turf/mail/npcshops unchanged (no `Plugins:`). **plugin.yml**: `depend: [Keystone, NBTAPI]`, `softdepend: [PlaceholderAPI, Vault, ViaVersion, Citizens, Bartizan]`; `Gangland.java:179-180` Citizens → `Type.SOFT`; `PlayerDeathListener:60,181` and `CustomPlayerDeathListener:97` → `NpcSupport.isNpc`. Seams **kept** (`MetricsContributor`, `DataCleanupTask`, `NbtTagCatalog`, `ShopDisplayNameProvider`, `DeathMessageContributor`, `SignTypeContribution`, `SignViewProvider`, `CommandContribution`); seams **deleted**: `TurfNpcContract`/`TurfNpcContracts` replaced by the turf-owned `TurfNpcSpawner` holder.

## 3. Answers

**Q-A** — Execute D3's consumer migration (it is an import swap), back-port the two priority overloads first, then delete Gangland's duplicates. Bartizan reaches loot chests/shops through **`org.luckyraven.keystone.item.service.ItemTypeProvider`**, published on the Bukkit `ServicesManager` in `Bartizan.onEnable`, pulled by `ItemTypeProviders.installAll(...)` from Gangland's CONFIG-phase `ItemConfig`. Lands in `Keystone/keystone-item/src/main/java/org/luckyraven/keystone/item/service/ItemTypeProvider.java` and `gangland-impl/.../config/ItemConfig.java`.

**Q-B** — Neither raise the floor nor duplicate: `org.luckyraven.keystone.util.WorldHeights.minHeight(World)` in `keystone-common`, cached `MethodHandle`, fallback `0`. Two call sites (`NpcNavigationDelegate:795`, `EntitySpawner:262`). Oriel stays on 1.16.5.

**Q-C** — Civilians stay in cops-n-crooks. The 20 turf-NPC files move to `gangland-turf` (D5 satisfied: turf has no cops import, cops keeps `Depends: [turf]`, no cycle). Turf spawns nothing itself: it declares `org.luckyraven.gangland.turf.turfnpcs.TurfNpcSpawner` — `@Nullable TurfNpcHandle spawn(Location, String typeId, @Nullable UUID owningGang)`, `void retarget(TurfNpcHandle, Player)`, `void despawn(TurfNpcHandle)` — with a no-op default in the `TurfNpcSpawners` holder; cops installs `CivilianTurfNpcSpawner` from `CopsNCrooksModuleConfig.installCoreSeams()`. Without cops, turf powerup NPCs silently don't spawn.

**Q-D** — Bartizan owns `Wearable`/`WearableTrait`/`WearableService`, in `bartizan-api`. `FuelKey` stays in `gangland-item`, so Bartizan's `Wearable` carries `boolean jetpack` + `String fuelKey` (plain String). Gadget compiles against `bartizan-api` at `provided` and maps the string via `FuelKey.valueOf`; `JetpackService.java:112,138` and `JetpackTask.java:215-219` change import only.

**Q-E** — Surface as listed in §2.2. Discovery: `ServicesManager` only, resolved lazily at use time (R8), one `BartizanApi` aggregate registration so consumers do one lookup; no static `Bartizan.api()`, no Keystone bean. `ThrowableAction`'s two static maps become `BartizanApi.lastKillWeapon(UUID)` and `takePendingVehicleExplosionDamage(UUID)` (same 1-tick TTL, internal maps). Death-message seam: core `DeathMessageContributor` stays; the implementation moves to `cops-n-crooks` (`.../seam/BartizanDeathMessageContributor`). `WeaponItemApi` (4 methods + `cleanDisplayName`) serves signs/shops. `NpcRangedAttack` interface lives in `keystone-npc`; its Bartizan-backed implementation is `cops-n-crooks/.../npc/weapon/BartizanRangedAttack.java` — the only unit with both Citizens and Bartizan on its classpath.

**Q-F** — 23 Maven modules: `bartizan-api`, `bartizan-plugin`, `bartizan-compat/version-impl` + 20 adapters (moved verbatim; **not** Keystone's reflective `PacketBridge` — a rewrite of working NMS is out of scope). Shading: Keystone provided/never shaded, XSeries provided (comes from Keystone.jar), no AnvilGUI; only the 21 compat artifacts are shaded into `Bartizan-0.1.0.jar`. `plugin.yml` as §2.2. Own `settings.yml` (`Block_Regeneration`, `Money_Symbol`, `Inventory.Fill`, `Auto_Save`, `Database`) and `message/message_en.yml` via `YamlMessageProvider` + `LanguageLoader`. Own `BartizanDatabase`, `plugins/Bartizan/database/bartizan.db`, table `weapon` unchanged, plus `WeaponTableImporter` (one-shot JDBC copy from Gangland's SQLite when Bartizan's `weapon` table is empty; flag `Import_Legacy_Weapon_Table`).

**Q-G** — Loot chests: the 167 refs keep resolving through the pulled provider; with Bartizan absent each roll logs `item.unknown_type` (non-fatal, already lazy) — documented, docket entry filed. Signs: **no code migration**. The six placed sign types become inert text blocks; the `[VIEW]` weapon branches vanish with `WeaponSignViewProvider` (module-owned, so core is untouched). Changelog + docket entry. `Messages`: delete `GAVE_AMMO`, `GAVE_WEAPON`, `AMMO_NOT_IN_INVENTORY`, `AMMO_BOUGHT`, `AMMO_SOLD`, `NOT_ENOUGH_AMMO`; **keep** `DEAD_USING_WEAPON` (still consumed by `PlayerDeathListener:210` through the contributor) and its `MessagesTest:107` pin. `Settings`: drop `Block_Regeneration` only. Docs: `documentation/module-loader.md`, `CLAUDE.md` module table, `README.md`, `documentation/developer/compatibility.md`.

**Q-H** — `Plugins:` per E2 §4 (new record component, own pattern, injected predicate, `module.plugin.missing`). Gangland's `plugin.yml` carries `softdepend: [… Citizens, Bartizan]` — `Plugins:` is fail-fast, `softdepend` is ordering; both are required. Citizens moves from `depend` to `softdepend` (Q-I gate).

**Q-I** — `keystone-npc` as §2.1. `EntityMark` and `EntityMarkManager` **do not move** (they carry `countForWanted()` and Gangland's `VILLAGER→CIVILIAN` policy); `AbstractNpc.destroy(EntityMarkManager)` becomes `destroy(Consumer<Entity> onDespawn)`. `AbstractNpc`'s constructor takes `JavaPlugin` first; `setHeldWeapon(weapon, plugin)` is deleted. Modules gate in `onEnabled`: `if (!NpcSupport.available()) { Diagnostics.active().report(Fault.dependency("npc.citizens.missing", …)); return; }` — cops, turf, npcshops each.

**Q-J** — Keystone 1.9.0 must be in `~/.m2` before Bartizan compiles (`keystone-item` service SPI, `keystone-npc`, `WorldHeights`); Bartizan 0.1.0 must be in `~/.m2` before Gangland's cops/gadget compile. Gangland 0.8.4's module set keeps working throughout because the work happens on a new `0.9.0` branch — `0.8.4` jars stay installable against Keystone 1.8.1 and are never rebuilt. Gates below.

## 4. Build order

| # | Repo | Step | Gate command |
|---|---|---|---|
| 1 | Keystone | `WorldHeights`, item priority back-port, `ItemTypeProvider(s)` | `mvn -pl keystone-common,keystone-item -am clean install` |
| 2 | Keystone | `Plugins:` descriptor + resolution + loader | `mvn -pl keystone-module -am test` |
| 3 | Keystone | `keystone-npc` module (move + SPI) | `mvn -pl keystone-npc -am test` |
| 4 | Keystone | shade + docs + `<revision>1.9.0</revision>` | `mvn clean install` |
| 5 | Bartizan | repo skeleton + `bartizan-compat/**` moved | `mvn -pl bartizan-compat/version-impl -am clean install` |
| 6 | Bartizan | `bartizan-api` types + events | `mvn -pl bartizan-api -am clean install` |
| 7 | Bartizan | `bartizan-plugin` bootstrap, DB, commands, resources | `mvn clean package` (16 tests green) |
| 8 | Gangland | keystone-item consumer migration + `ItemTypeProviders.installAll` | `mvn clean install -DskipTests` |
| 9 | Gangland | delete weapon module + compat tree, prune Settings/Messages | `mvn test` |
| 10 | Gangland | cops/gadget → `bartizan-api`, `Plugins:` in module.yml, `NpcRangedAttack` impl | `mvn -pl gangland-features/cops-n-crooks -am test` |
| 11 | Gangland | turf-NPC move + `TurfNpcSpawner` holder | `mvn -pl gangland-features/gangland-turf -am test` |
| 12 | Gangland | `gangland-npc-shops` module + `gangland-build` wiring | `mvn clean package` |
| 13 | all | smoke matrix S1–S8 on the new topology | `smoke/smoke.py --all` |

## 5. Risks and what this leaves worse

- **Mislocation (deliberate).** `GangAllyWeaponImpactListener`, the Bartizan death-message contributor and the weapon shop-display provider live in `cops-n-crooks`. Remove cops and gang friendly-fire under weapon fire, weapon death messages and clean shop names all disappear silently (R2). The clean alternative is a small `gangland-combat` module; D7 forbids a bridge module, so this is the cost.
- **npc-shops loses weapon display-name cleaning** entirely (it must not depend on Bartizan). Filed, not fixed.
- **Weapon signs are abandoned**, not migrated (R5). No converter command, no world scan.
- **Civilians stay in cops** — turf's NPC feature is inert without cops (Q-C). The clean alternative moves ~30 civilian files into a `gangland-civilians` module.
- **`gangland-item` keeps `money/`, `unique/`, `fuel/`** and now has no wearables — the module is smaller than its name suggests; renaming is deferred.
- **Reflective `WorldHeights`** costs a MethodHandle invoke per navigation step; measured cost is negligible but it is uglier than raising the floor.
- **Two NPC bases coexist briefly** (steps 3→10) — keep the Keystone copy and the cops copy compiling in parallel until step 10 flips.
- **`weapon` table migration** is import-once and SQLite-only; MySQL operators must re-point Bartizan at the same schema by hand.

## 6. First eight files for the planner

1. `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/WeaponModuleConfig.java`
2. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/NpcCombatDelegate.java`
3. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/AbstractNpc.java`
4. `E:\Programming\java\Keystone\keystone-item\src\main\java\org\luckyraven\keystone\item\ItemConverterRegistry.java`
5. `E:\Programming\java\Keystone\keystone-module\src\main\java\org\luckyraven\keystone\module\ModuleDescriptor.java`
6. `gangland-impl/src/main/java/org/luckyraven/gangland/config/ItemConfig.java`
7. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/config/CopsNCrooksModuleConfig.java`
8. `gangland-build/pom.xml`
