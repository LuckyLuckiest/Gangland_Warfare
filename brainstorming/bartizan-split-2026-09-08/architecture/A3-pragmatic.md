<!-- A3 · feature-dev:code-architect (opus) · 2026-09-08 · focus: pragmatic balance, staged, named deferrals -->

# A3 — Pragmatic blueprint (Keystone 1.9.0 · Bartizan 0.1.0 · Gangland 0.9.0)

## 1. Approach (10 lines)

1. **Move code, don't re-abstract it.** Types that already depend only on Keystone+Bukkit (`Weapon` and its dtos, the 20 recoil adapters, `NpcNavigationDelegate`) are `git mv`'d verbatim; only boundary types get new interfaces.
2. **Only four new seam interfaces in the whole wave**: `ItemVocabularyProvider`, `NpcRangedAttack`, `NpcTargetFilter`, `CivilianSpawnBridge`. Everything else reuses the two shapes the team already accepts (contributions, holders).
3. **`bartizan-api` = moved domain types + events + service interfaces**, not a re-declared parallel interface tier. Consumers change imports, not code.
4. Bartizan publishes on Bukkit's `ServicesManager`; Gangland pulls. No static registries, no bridge module (D7).
5. Keystone's 1.16.5 floor is kept (Oriel); one 25-line reflective `WorldHeights` helper solves `getMinHeight()`.
6. Turf owns its NPCs; the *civilian spawn* stays in cops behind an inert-default holder. No cycle, no 30-file civilian move.
7. Live worlds keep their six weapon sign types via generic item-string signs in Gangland core — the cheapest fix for R5.
8. The `weapon` table gets a one-shot SQLite importer instead of an accepted data loss.
9. Deferred by name: reflective recoil (Bartizan 0.2.0), a `gangland-civilians` module (Gangland 0.10.0), `ItemConverter` viewer overload for Oriel (Keystone 2.0.0).
10. Optimises for: every stage ships a bootable server; the 0.8.4 module set keeps working until the single Gangland flip commit.

## 2. Per-stream blueprint

### 2.1 Keystone 1.9.0 (branch `phase-h8-item-npc`)

**`keystone-common`** — new `org.luckyraven.keystone.util.WorldHeights`:
```java
public static int minHeight(World world);   // reflective World#getMinHeight, 0 fallback, cached Method
public static int maxHeight(World world);   // World#getMaxHeight (1.16-safe, direct)
```
Test `WorldHeightsTest` (mock `World` with and without the method).

**`keystone-item`** — back-port Gangland's drift, then add the cross-plugin seam:
- `ItemSerializerRegistry`: `CATCH_ALL_PRIORITY = Integer.MIN_VALUE`, `register(Predicate<ItemStack>, ItemSerializer, int priority)`, stable descending re-sort.
- `ItemRefresherRegistry`: `CATCH_ALL_PRIORITY`, `register(ItemRefresher, int priority)`, same sort.
- New package `org.luckyraven.keystone.item.service`:
```java
public interface ItemVocabularyProvider {
    String vocabularyId();                                   // "bartizan"
    void contribute(ItemVocabulary target);
}
public interface ItemVocabulary {
    ItemVocabulary converter(String label, ItemConverter converter);
    ItemVocabulary serializer(Predicate<ItemStack> when, ItemSerializer serializer, int priority);
    ItemVocabulary refresher(ItemRefresher refresher, int priority);
}
public final class ItemVocabularies {
    public static int pull(Plugin consumer, ItemConverterRegistry converters,
                           ItemSerializerRegistry serializers, ItemRefresherRegistry refreshers);
}
```
`pull` walks `Bukkit.getServicesManager().getRegistrations(ItemVocabularyProvider.class)`, applies each into the *caller's own* instances, logs one line per vocabulary, returns the count. Tests: `ItemVocabulariesTest` (two providers, priority ordering preserved), plus priority tests on both registries.

**`keystone-npc`** (new Maven module, after `keystone-module` in the root pom; deps `keystone-common`, `keystone-bean`, `keystone-persistence`, `citizens-main` provided, repo `https://maven.citizensnpcs.co/repo`):
```
org.luckyraven.keystone.npc
├─ AbstractNpc            ctor (JavaPlugin, NPC, Location, NpcNavigationConfig, NpcDifficulty)
│                         destroy(Consumer<Entity> onDespawn); canAttack() -> !rangedAttack.isBusy()
├─ NpcBehavior, NpcDifficulty, NpcNavigationConfig
├─ NpcNavigationDelegate, NavStep, NavObstacle   (package-private, co-located)
├─ NpcCombatDelegate                             (weapon-free; gun branch -> NpcRangedAttack)
├─ NpcSupport            available() / registry() / isNpc(Entity)  — CitizensBridge behind guards
├─ NpcMetadata           TRADER_ID, BANKER_ID, TURF_ID string constants
├─ event/NpcEvent
├─ spi/NpcRangedAttack   isRanged(); isBusy(); boolean tryFire(LivingEntity target);
│                        void triggerReload(); void refreshHeldItem(); void onDestroy();
│                        static NpcRangedAttack none()
├─ spi/NpcTargetFilter   boolean isAttackable(LivingEntity); static NpcTargetFilter all()
├─ spi/NpcMarkDefaults   Set<EntityType> policeTypes(); Set<EntityType> civilianTypes()
└─ entity/ EntitySpawner, EntitySpawnerPoint, SpawnConfigProvider, NpcMark, NpcMarkManager
```
`NpcMark` = `CIVILIAN, POLICE, UNSET` + `isCivilian()`; **no `countForWanted()`**. Both `getMinHeight()` sites use `WorldHeights.minHeight`. Tests (`keystone-testkit`): `NpcDifficultyTest`, `EntitySpawnerTest`, `NpcSupportTest`, `NpcMarkManagerTest`.

**`keystone-module`** — `Plugins:` exactly as E2 §4: `ModuleDescriptorReader.parse` reads `yaml.getStringList("Plugins")` validated by a new `ModuleDescriptor.PLUGIN_NAME_PATTERN = [A-Za-z0-9_.-]+`; `ModuleDescriptor` record gains `List<String> plugins` (defensive copy); `ModuleResolution.resolve(Collection, PluginVersion, Predicate<String> pluginEnabled)` with the 2-arg overload delegating `n -> true`, checked as step 2.5 so misses cascade; `ModuleLoader.load()` passes `n -> { Plugin p = Bukkit.getPluginManager().getPlugin(n); return p != null && p.isEnabled(); }`; fault `module.plugin.missing` (kind dependency, context `module`/`jar`/`plugin`). Tests per E2 §4.

**`keystone-plugin`** — shade `keystone-npc`; `plugin.yml` `softdepend: [Vault, PlaceholderAPI, Citizens]`. Docs: new `docs/keystone-npc.md`, `docs/phase-h8-item-npc.md`, README row, `keystone-item.md` wiring recipe + `keystone-module.md` fault table + `extraction-roadmap.md` E5 amendment.

### 2.2 Bartizan 0.1.0 (`E:\Programming\java\Bartizan`)

```
pom.xml (${revision}=0.1.0, flatten, central-release profile, groupId org.luckyraven.bartizan)
├─ bartizan-api        org.luckyraven.bartizan.api.**            deps: keystone-* provided, spigot-api
├─ bartizan-compat/
│   ├─ compat-api      RecoilCompatibility, Compatibility, CompatibilityWorker
│   └─ version-1_16_R1 … version-1_21_R7   (20 modules, git mv verbatim)
└─ bartizan-plugin     org.luckyraven.bartizan.**  <finalName>Bartizan-${project.version}</finalName>
                       shades bartizan-api + all compat modules; keystone-* provided, never shaded
```

`bartizan-api` public surface: `weapon.{Weapon, GunWeapon, MeleeWeapon, WeaponType, ProjectileType, SelectiveFire, ProjectileState}`, `weapon.dto.{ProjectileData, AmmunitionData, MeleeData, ScopeData, SoundData, DamageData, ReloadData, SpreadData}`, `ammo.Ammunition`, `wearable.{Wearable, WearableTrait}`, the nine `events/**` classes, and the service interfaces `BartizanApi`, `WeaponService`, `WearableService`, `AmmunitionService`, `WeaponItemApi`, `NpcWeaponSupport`, `raytrace.{WeaponRaytracer, WeaponShooting, RaytraceRequest}`, `combat.CombatEligibility` (`boolean canBeHit(Player)`, default `p -> !p.isDead()`), plus `events.WeaponVehicleDamageEvent`.

```java
public interface BartizanApi {
    WeaponService weapons(); WearableService wearables(); AmmunitionService ammunition();
    WeaponItemApi items(); NpcWeaponSupport npcWeapons();
    Optional<String> killCredit(UUID victim);                 // replaces pendingKillerWeapon
    void installCombatEligibility(CombatEligibility delegate);
    static Optional<BartizanApi> get() {                      // no caching, ServicesManager lookup per call
        return Optional.ofNullable(Bukkit.getServicesManager().load(BartizanApi.class));
    }
}
public interface WeaponItemApi {
    @Nullable ItemStack buildItem(String weaponName);
    boolean isValidWeaponName(String name);
    boolean isSameWeapon(Player viewer, ItemStack a, ItemStack b);
    @Nullable String cleanDisplayName(ItemStack item);
}
public interface NpcWeaponSupport {
    NpcRangedAttack attachTo(JavaPlugin owner, LivingEntity shooter, String weaponName);
}
```

`bartizan-plugin`: `Bartizan extends JavaPlugin` → `BartizanContext.bootstrap()` reusing Keystone's `BeanFactory` phases (KERNEL `BartizanDatabase`+`Diagnostics`+`CompatibilityWorker`, FILE `BartizanFileConfig` (22 `weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml`, `message/message_en.yml`, `settings.yml`), DATABASE `WeaponRepository` scan, CONFIG `BartizanModuleConfig` = today's `WeaponModuleConfig` minus core seams, LISTENER, COMMAND). `plugin.yml`: `depend: [Keystone]`, `softdepend: [NBTAPI, ViaVersion, PlaceholderAPI]`, command `bartizan` (alias `bz`), own `commands.json`. `BartizanChatUtil extends keystone.util.ChatUtil`; `BartizanMessages` enum on `YamlMessageProvider`+`LanguageLoader`. Services registered in `onEnable` **after** bootstrap: `BartizanApi`, `WeaponService`, `WearableService`, `WeaponRaytracer` (unchanged key, R8/R10), `ItemVocabularyProvider` (`BartizanItemVocabulary`, weapon/ammo/ammunition/wearable converters, three serializers @0, weapon+wearable refreshers @10, ammunition @0 — R7 preserved).

Database: `BartizanDatabase extends DatabaseHandler`, `plugins/Bartizan/database/bartizan.db`, table `weapon` unchanged. `WeaponTableImportTask` (KERNEL, one-shot): when `Import_Legacy_Weapon_Table: true` and no `.weapon-import-done` marker, opens `plugins/Gangland_Warfare/database/<schema>.db`, copies `weapon` rows, writes the marker. MySQL users share the schema — nothing to do. Tests: the 16 moved weapon tests + own `BukkitRegistryFixture` (copied from `gangland-core` test-jar) + `viaversion-api` provided test-classpath note kept (T-09/R9).

### 2.3 Gangland 0.9.0 (branch off `0.8.4`)

**Deleted**: `gangland-features/gangland-weapon/**`; the whole `gangland-compatibility/**` tree (21 modules) and its `gangland-build` shade/copy blocks; `gangland-item`'s nine generic classes (E2 §1a) + `item/wearable/**` + `contract/WearableEquipService` + `listener/wearable/WearableEquipListener` (fixes T-14); `KernelConfig.compatibilityWorker()`; `Settings.Block_Regeneration` + `WeaponBlockRegenerationSettings`; `Messages.{GAVE_AMMO, GAVE_WEAPON, AMMO_NOT_IN_INVENTORY, AMMO_BOUGHT, AMMO_SOLD, NOT_ENOUGH_AMMO, DEAD_USING_WEAPON}` (flip `MessagesTest.java:107`); `settings.yml:40` logging entry.

**Changed**: `ItemKind` becomes `public enum ItemKind implements org.luckyraven.keystone.item.ItemKind` losing `WEAPON/AMMUNITION/WEARABLE`; all `org.luckyraven.gangland.item.{ItemParser,ItemConverter*,ItemSerializer*,ItemRefresher*,MaterialItemSerializer,dsl}` imports re-point to `org.luckyraven.keystone.item.*` (~88 sites); `ItemConfig.itemConverterRegistry/itemSerializerRegistry/itemRefresherRegistry` build the Keystone instances and a new `itemVocabularies(...)` bean calls `ItemVocabularies.pull(gangland, …)` — this is how the 167 loot-chest `weapon:`/`ammo:`/`wearable:` strings keep resolving; `GameplayConfig.lootChestLoader`'s `initializeAll()` moves to `@PostConstruct` (T-11).

**Moves**: turf NPCs (20 files + `turf/turf_npcs.yml` + `TurfPowerupNpc{Repository,Table}` + the `turf_powerupnpc` commands.json key) → `gangland-turf`, folded into `TurfModuleConfig`; traders + bankers (E3 §4.1, 81 files) → new `gangland-features/gangland-npc-shops` (`Id: npcshops`, `Main: org.luckyraven.gangland.npcshops.NpcShopsModule`, `Host_Api: 0.9`, `Depends:` empty). `GangAllyWeaponImpactListener` moves verbatim from weapon into `gangland-impl/listener/gang/` compiled against `bartizan-api`.

**Seams**: removed `WearableEquipService`, `TurfNpcContract(s)`, `TurfNpcContractImpl`, `TurfModuleConfigHolderSeamTest`. Added holder `org.luckyraven.gangland.turf.turfnpcs.CivilianSpawns` (gangland-turf) holding `CivilianSpawnBridge { Optional<AbstractNpc> spawn(Location, String typeId); void setCombatTarget(AbstractNpc, UUID); boolean isCivilian(Entity); static CivilianSpawnBridge inert(); }`, installed by cops' `installCoreSeams()`. Kept unchanged: `MetricsContributor`, `DataCleanupTask`, `NbtTagCatalog`, `ShopDisplayNameProvider`, `DeathMessageContributor`, `SignTypeContribution`, `SignViewProvider`, `CommandContribution`.

**Signs (R5)**: `gangland-impl/sign/type/trade/GenericItemTradeSign` + `WeaponCompatSignContribution` re-register `weapon-buy/sell`, `ammo-buy/sell`, `wearable-buy/sell` as item-string signs (`ItemParser` + `WeaponItemApi.isSameWeapon` as the `ItemSimilarityChecker`, `MoneyAspect` unchanged). Existing signs keep working; without Bartizan they report a parse error on use instead of vanishing.

**`plugin.yml`**: `depend: [Keystone, NBTAPI]`; `softdepend: [PlaceholderAPI, Vault, ViaVersion, Citizens, Bartizan]`. `Gangland.java:179` Citizens → `Type.SOFT` + `isEnabled()`; the three core `isNPC` sites → `NpcSupport.isNpc`. All five modules' `module.yml` `Host_Api: 0.9`; cops and gadget gain `Plugins:` with `- Bartizan`; cops keeps `Depends: [turf]` (weapon drops), gadget's `Depends:` empties.

## 3. Answers Q-A … Q-J

**Q-A** Per-consumer registries + ServicesManager pull, exactly as E2 §3c. Service interface **`org.luckyraven.keystone.item.service.ItemVocabularyProvider`** with `ItemVocabulary` as the registrar and `ItemVocabularies.pull(...)` as the consumer helper (new files in `keystone-item`; consumed at `gangland-impl/src/main/java/org/luckyraven/gangland/config/ItemConfig.java`). Bartizan registers one provider in its `onEnable`; Gangland's `softdepend: [Bartizan]` guarantees Bartizan enables first; the priority triple (10/10/0) travels in the provider so R7 is preserved.

**Q-B** A **reflective helper**, `org.luckyraven.keystone.util.WorldHeights.minHeight(World)` in `keystone-common`, cached `Method`, `0` fallback. Raising the floor breaks Oriel (`Keystone/pom.xml:65-67`) for two call sites — not worth it. Both `NpcNavigationDelegate` and `EntitySpawner` use it.

**Q-C** Civilians **stay in cops-n-crooks**; turf owns everything else. Turf gains the holder `CivilianSpawns`/`CivilianSpawnBridge` (`gangland-turf/.../turf/turfnpcs/CivilianSpawns.java`), inert by default, installed by `CopsNCrooksModuleConfig.installCoreSeams()`. Turf spawns *a civilian NPC of a configured type id* (`quartermaster`, `turf_defender`) through that bridge and drives combat targeting through it — it never names `CivilianNpc`. `Depends:` turf = empty, cops = `[turf]`: no cycle. With turf but no cops, powerup NPCs do not spawn and one `turf.npc.provider.missing` fault is logged. Moving `npc/civilian/**` into its own module is deferred to Gangland 0.10.0.

**Q-D** **Bartizan owns `Wearable` and `WearableTrait`**, moved verbatim into `org.luckyraven.bartizan.api.wearable`. `isJetpack()` is renamed `isPowered()` and `FuelKey fuelKey` becomes `@Nullable String getFuelKey()` (the `FuelKey` type stays in `gangland-item`; gadget maps the string). Gadget compiles against `bartizan-api` at provided scope and declares `Plugins: [Bartizan]` — three call sites change (`JetpackService.java:112-115,138-140`, `JetpackTask.java:215-219`).

**Q-E** Surface as listed in §2.2. Discovery = **Bukkit `ServicesManager`**, with `BartizanApi.get()` as a non-caching static *lookup helper* in `bartizan-api` (no stored state, returns `Optional`) — no Keystone bean, because Bartizan is a separate plugin. `ThrowableAction.pendingKillerWeapon` → `BartizanApi.killCredit(UUID)` (same 1-tick TTL, internal); `pendingVehicleExplosionDamage` → the new cancellable `WeaponVehicleDamageEvent(Entity vehicle, double damage, Player shooter)` consumed by gadget's `CarDamageListener`. Death message: **Bartizan owns it** — a `WeaponDeathMessageListener` at `EventPriority.HIGHEST` in `bartizan-plugin` overrides Gangland's `NORMAL`-priority message using Bartizan's own `Messages.DEAD_USING_WEAPON`; Gangland deletes its member and keeps the `DeathMessageContributor` interface unused. `WeaponItemApi` (four methods above) serves signs and shops. **`NpcRangedAttack` is implemented in `bartizan-plugin`** (`org.luckyraven.bartizan.npc.WeaponRangedAttack`), handed out by `NpcWeaponSupport.attachTo(...)` — so cops' weapon import surface collapses from 12 files to `CopNpcFactory`/`CivilianNpcFactory` alone.

**Q-F** Layout in §2.2: `bartizan-api`, `bartizan-compat/{compat-api, 20 × version-1_xx_Rn}`, `bartizan-plugin` (shade). **Keep the 20 adapters** — the 24-line `Recoil_1_21_R7` is version-specific packet construction; Keystone's `PacketAdapter` has no camera-rotation method, so consolidation means writing and smoke-testing a reflective rotate across 20 revisions. Deferred to Bartizan 0.2.0 / Keystone 2.0.0 (`PacketAdapter.rotateCamera`). Shading: api + compat into the plugin jar; Keystone always provided, never shaded; `bartizan-api` deployed to Central for consumers. Own `settings.yml` (only `Block_Regeneration.*`, `Money_Symbol`, `Inventory.Fill.*`, `Auto_Save.Debug`, `Import_Legacy_Weapon_Table`) and `message/message_en.yml` via Keystone's `YamlMessageProvider`/`LanguageLoader`. Database + `weapon`-table importer as §2.2 (R3 closed, not accepted).

**Q-G** Loot chests: fixed by Q-A, zero YAML edits. Signs: `GenericItemTradeSign` + `WeaponCompatSignContribution` keep all six type names and the `[VIEW]` fallback path alive (the `[VIEW]` weapon/ammo/wearable branches are dropped; the generic item view claims them). Dead `Messages` members and `DEAD_USING_WEAPON` deleted with the `MessagesTest` flip. `Settings.Block_Regeneration` and `WeaponBlockRegenerationSettings` deleted. Docs: `CLAUDE.md` module table + two-tier section, `documentation/module-loader.md` (module table, seam table, YAML list), `README.md`, `documentation/developer/compatibility.md` (points at Bartizan).

**Q-H** `Plugins:` implemented in `keystone-module` per E2 §4 (parse, record component, injected `Predicate<String>`, `module.plugin.missing`) — a fail-fast guard only. Gangland's `plugin.yml` gets **both** `softdepend: Bartizan` (ordering + silences the cross-plugin class warning, which names Gangland) and `softdepend: Citizens` (moved off `depend:`).

**Q-I** `keystone-npc` contents per §2.1. `NpcSupport` is the sole Citizens interception point; the `npc.citizens.missing` fault (kind dependency) is raised by each NPC-owning module's `onEnabled` — `CopsNCrooksModule`, `TurfModule`, `NpcShopsModule` — which then skips `start()`/deferred spawn arming (`TurfPowerupManager.onInitialize`'s 60-tick task, `TurfDefenderDeployer.start()`). **`EntityMark` moves as `NpcMark` without `countForWanted()`**; cops keeps `WantedMarks.countForWanted(NpcMark)`. `EntityMarkManager` moves as `NpcMarkManager` with the `VILLAGER/PILLAGER` fallback behind `NpcMarkDefaults` (cops' impl wraps `CiviliansLoader`). `AbstractNpc`'s constructor takes `JavaPlugin plugin` first; `setHeldWeapon(Weapon, JavaPlugin)` is deleted outright.

**Q-J** See §4. `~/.m2` order: Keystone 1.9.0 → Bartizan 0.1.0 (`bartizan-api` must be installed before Gangland compiles cops/gadget) → Gangland 0.9.0. 0.8.4 keeps working because nothing in that tree is touched until step 5; the Gangland flip is one branch and the old topology stays shippable on `0.8.4`.

## 4. Build order

| # | Repo | Work | Gate command |
|---|---|---|---|
| 1 | Keystone | `WorldHeights`, item registry back-port, `ItemVocabularyProvider` | `mvn -pl keystone-common,keystone-item -am clean install` |
| 2 | Keystone | `keystone-npc` (+ pom, plugin.yml softdepend, shade) | `mvn clean install`; `NpcSupportTest`/`EntitySpawnerTest` green |
| 3 | Keystone | `Plugins:` in `keystone-module` + docs | `mvn -pl keystone-module -am test`; `Keystone-1.9.0.jar` built |
| 4 | Bartizan | api + compat + plugin + resources + db + importer | `mvn clean package`; `Bartizan-0.1.0.jar`; 16+ tests green |
| 5 | Gangland | branch `0.9.0`; delete weapon + compatibility; item migration; sign compat; plugin.yml | `mvn clean install -DskipTests`; core jar has no `gangland/weapon` entry |
| 6 | Gangland | turf-NPC move (D5) + `CivilianSpawns` holder | `mvn test`; `grep -rn "gangland\.copsncrooks" gangland-turf/src` empty |
| 7 | Gangland | `gangland-npc-shops` (D6), after T-12 AnvilGUI fix | `mvn clean package`; `target/modules/` has 6 jars |
| 8 | Gangland | cops/gadget re-point to `bartizan-api`, `Plugins:` in module.yml | `mvn test` all modules green |
| 9 | all three | phase-D smoke matrix S1–S8 + Bartizan rows | 0 ERRORs, expected WARNs only |

## 5. Risks and deferrals

Risks: (R-a) `ItemVocabularies.pull` runs in Gangland's CONFIG phase — if Bartizan is installed *after* Gangland in the plugin order despite `softdepend`, weapon items silently become unknown; mitigate with a boot log line `Pulled N item vocabularies` and a smoke row. (R-b) 20 compat modules need 20 spigot NMS jars in `~/.m2`; Bartizan's build has the same prerequisite Gangland has today. (R-c) `NpcRangedAttack` changes NPC firing cadence ownership — pin `SelectiveFireTest` semantics (`feedback_selective_fire_semantics`) before the move. (R-d) Turf powerup NPCs are inert without cops — accepted and documented, mirroring the 0.8.4 `BankTiers` decision.

Deferred: reflective recoil consolidation → **Bartizan 0.2.0 / Keystone 2.0.0**; `gangland-civilians` module → **Gangland 0.10.0**; `keystone-sign` extraction → **Keystone 2.0.0**; `ItemConverter` viewer overload for Oriel → **Keystone 2.0.0** (Oriel stays on 1.7.0); Bartizan Central publication → **0.2.0** (local `mvn install` suffices for the wave).

## 6. The eight files a planner must read first

1. `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/WeaponModuleConfig.java`
2. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/NpcCombatDelegate.java`
3. `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/config/CopsNCrooksModuleConfig.java`
4. `gangland-impl/src/main/java/org/luckyraven/gangland/config/ItemConfig.java`
5. `E:\Programming\java\Keystone\keystone-item\src\main\java\org\luckyraven\keystone\item\ItemConverterRegistry.java`
6. `E:\Programming\java\Keystone\keystone-module\src\main\java\org\luckyraven\keystone\module\ModuleResolution.java`
7. `gangland-build/pom.xml`
8. `documentation/module-loader.md` (`## Core seams`)
