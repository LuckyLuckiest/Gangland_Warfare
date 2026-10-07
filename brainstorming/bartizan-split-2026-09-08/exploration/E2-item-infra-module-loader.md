<!-- E2 · feature-dev:code-explorer (opus) · 2026-09-08 · item framework promotion (D3), module plugin-dependency (D8), classloading (D5), Keystone release mechanics. The agent ran before README.md existed; decisions D1–D10 are in README.md. -->

# E2 — Item framework promotion (D3), module plugin-dependency (D8), classloading and Keystone release mechanics

> **The single biggest finding, up front: D3 is not a new extraction. It already shipped upstream as Keystone Phase E5 (v1.5.0).** `keystone-item` already contains `ItemKind`, `StandardItemKind`, `ItemConverter(+Registry)`, `ItemParser`, `ItemSerializer(+Registry)`, `MaterialItemSerializer`, `ItemRefresher(+Registry)`, `ItemBuilder` and `item.nbt.*`; `keystone-persistence` already contains `config.dsl.ItemDslAdapter`. `E:\Programming\java\Keystone\docs\extraction-roadmap.md:224-318` records it as complete, and `:316-318` states the outstanding work: *"Consumer migration is the separate track… Gangland deletes its generic tier, adds the one-line `implements`, re-points ~70 `ItemParser` and ~18 registry call sites."* **D3 = execute that consumer migration, plus back-port the drift Gangland has accumulated since 1.5.0.**

## 1. `gangland-item` inventory, class by class

Module: `gangland-infra/gangland-item`, package root `org.luckyraven.gangland.item`. Its pom (`gangland-infra/gangland-item/pom.xml:18-68`) already depends on `keystone-item` (`:24-26`) and hard-depends on `de.tr7zw:item-nbt-api-plugin` (`:50-53`).

### 1a. Generic tier — already promoted, delete locally (duplicate of `keystone-item`)

| Gangland class | Keystone counterpart | Verdict |
|---|---|---|
| `ItemParser.java` (`:14-66`) | `keystone-item/.../ItemParser.java:35-113` | **Delete.** Keystone's is a strict superset: `tryParse` returning `Result<ItemStack>` with `item.missing_type`/`item.unknown_type`/`item.conversion_failed` faults (`:38-42`), `parse` a nullable wrapper (`:58-60`). |
| `ItemConverter.java` (`:7-20`) | `keystone-item/.../ItemConverter.java` | **Delete**, identical contract. |
| `ItemConverterRegistry.java` (`:6-62`) | `keystone-item/.../ItemConverterRegistry.java:20-101` | **Delete.** Keystone adds `resolve(String)` (`:87-99`) which folds the material fallback into the registry, plus null guards. |
| `ItemKind.java` (`:13-31`) — enum `UNIQUE/WEAPON/AMMUNITION/WEARABLE/CAR/MONEY/MATERIAL` | `keystone-item/.../ItemKind.java` (interface, `:21-34`) + `StandardItemKind.MATERIAL` (`:7-26`) | **Split.** Keystone deliberately made it an interface (roadmap `:257-264`). Gangland's migration is one line: `public enum ItemKind implements org.luckyraven.keystone.item.ItemKind` (fully qualified). `ItemKind.MATERIAL` call sites (`ItemConfig.java:49`) bind by subtyping. |
| `ItemSerializer.java` (`:14-20`) | `keystone-item/.../ItemSerializer.java` | **Delete.** |
| `ItemSerializerRegistry.java` (`:26-65`) | `keystone-item/.../ItemSerializerRegistry.java:22-60` | **Back-port, then delete.** Real drift: Gangland's has `CATCH_ALL_PRIORITY = Integer.MIN_VALUE` (`:32`), a 3-arg `register(predicate, serializer, priority)` (`:40-43`) and a stable priority re-sort. Keystone's 1.5.0 copy has neither. Added 2026-09-07 by the module split (`brainstorming/module-split-2026-09-07/README.md:157-160`) and load-bearing: `ItemConfig.java:96` registers `MATERIAL` at `CATCH_ALL_PRIORITY`. |
| `ItemRefresher.java` (`:19-72`) | `keystone-item/.../ItemRefresher.java` | **Delete** (both carry the `decorate` default). |
| `ItemRefresherRegistry.java` (`:25-85`) | `keystone-item/.../ItemRefresherRegistry.java:18-72` | **Back-port, then delete.** Same drift: `CATCH_ALL_PRIORITY` (`:31`) and `register(refresher, int priority)` (`:44-48`). Load-bearing: `WeaponModuleConfig.java:204-206` registers `weaponRefresher`/`wearableRefresher` at priority 10 to outrank `uniqueItemRefresher`, `ammunitionItemRefresher` at 0. |
| `MaterialItemSerializer.java` (`:11-30`) | `keystone-item/.../MaterialItemSerializer.java` | **Delete.** Keystone tests `isAir()` instead of `== Material.AIR` (roadmap `:281-282`). |
| `dsl/ItemDslAdapter.java` (`:38-147`) | `keystone-persistence/.../config/dsl/ItemDslAdapter.java` | **Delete.** Keystone unified the upper-casing drift between `resolveConverter` (`:136-145`) and `ItemParser.getConverter` (`ItemParser.java:55-64`) through `ItemConverterRegistry.resolve`. |

Evidence the tier is clean: a grep of every `import org.luckyraven.gangland.*` / `org.luckyraven.keystone.*` / `de.tr7zw` / `com.cryptomorin` line under `gangland-infra/gangland-item/src/main/java` returns no hit for any of the nine files above except `ItemDslAdapter` (sibling item types + keystone-persistence). No `Messages`, `Settings` or Gangland domain type anywhere in the module's main sources.

### 1b. Gangland-specific — stays

| Package | Classes | Why it stays |
|---|---|---|
| `money/` | `MoneyAddon`, `MoneyItem`, `MoneyItemFactory`, `MoneyItemUtil`, `MoneyConverter`, `MoneyDepositService`, `MoneyDropClassifier`, `MoneyDropContext` | Game economy. `MoneyAddon.java:19-24` loads `money.yml`; `MoneyConverter.java:18-46` reads `amount=`. |
| `unique/` | `UniqueItem`, `UniqueItemUtil`, `UniqueItemKeys` (`UNIQUE_ITEM_KEY = "uniqueItem"`, `:5`) | Gangland's own NBT vocabulary. |
| `wearable/` | `Wearable` (`:41-367`), `WearableTrait` (`:15-88`) | `Wearable` hardcodes `gangland.wearables.<key>` permissions (`:219-222`), damage-pipeline percentages, jetpack fuel fields. Candidate to move with the weapon stack — `WearableAddon` already relocated there 2026-09-07. |
| `fuel/` | `Fuel`, `FuelKey`, `FuelBar`, `FuelService` (`:23-262`), `FuelContract` | Relocated here 2026-09-07 because two core listeners need it with or without gadget (`module-split-2026-09-07/gadget.md:32-38`). |
| `contract/` | `WearableEquipService` (`:13-21`), `UniqueItemRegistry`, `UniqueItemInteractionService` | Test seams over Gangland types. |
| `listener/` | `fuel/{FuelHoldDisplayListener,FuelRefuelListener}`, `money/{MoneyDrop,MoneyPickup,MoneyInteract,MoneyProximityPickupTask}`, `unique/{LoadUniqueItem,UniqueItemInteract,UniqueItemInventoryRestrict}`, `wearable/WearableEquipListener` | `@ListenerHandler`/`@AutowireTarget`; `LoadUniqueItem` imports `gangland.core.downed.PlayerDownedEvent` (`:14-15`). |
| `event/PlayerItemInitEvent` | — | Gangland event. |

### 1c. Related classes in `gangland-impl`

- `gangland-impl/.../item/ItemPredicates.java:25-45` — the `Predicate<ItemStack>` half. Stays (references `Wearable.NBT_KEY`, `UniqueItemKeys`, `MoneyItemUtil.MARKER_TAG`); Keystone keeps predicates consumer-side (`keystone-item/.../ItemKind.java:15-18`).
- `gangland-impl/.../item/ItemAttributes.java:12-46` — abstract `implements ItemConverter`, uses `GanglandChatUtil.color`. Mixed: generic shape, Gangland colour call. Stay as-is, or promote with a `Function<String,String> colorizer`.
- `gangland-impl/.../item/NbtTagCatalog.java:7-24` — ordered `List<String>` for `/glw debug nbt brief`; leave.
- `gangland-impl/.../item/{converter,serializer,refresher,configuration}/*` — `MaterialConverter`, `UniqueConverter`, `UniqueItemSerializer`, `MoneyItemSerializer`, `UniqueItemRefresher`, `UniqueItemAddon`. All Gangland.

### 1d. Prefix grammar and where every converter is registered today

Grammar (`ItemParser.java:16-52`, identical in `keystone-item/.../ItemParser.java:44-111`, documented at `docs/keystone-item.md:44-58`): `<type>[:<modifier>][{key=value,key=value}]`. `ATTRIBUTE_PATTERN = \{([^}]+)}` (`:16`), `KEY_VALUE_PATTERN = (\w+)=([^,}]+)` (`:17`); attribute block stripped, then `split(":", 2)` gives type (upper-cased, `:44`) and modifier. Registry keys stored lower-cased (`ItemConverterRegistry.java:17`). A type with no converter falls back to the `material` converter iff `Material.valueOf(type)` succeeds (`ItemParser.java:55-64`; Keystone folds this into `ItemConverterRegistry.resolve`, `:87-99`).

| Prefix | Converter | Registered at | Owner |
|---|---|---|---|
| `material` + any bare `Material` | `MaterialConverter` | `gangland-impl/.../config/ItemConfig.java:49` | core |
| `unique` | `UniqueConverter` | `ItemConfig.java:51` | core |
| `money` / `cash` | `MoneyConverter` | `ItemConfig.java:53-54` | core |
| `weapon` | `WeaponConverter` | `gangland-weapon/.../WeaponModuleConfig.java:195` | module |
| `ammunition` / `ammo` | `AmmunitionConverter` | `WeaponModuleConfig.java:196-197` | module |
| `wearable` | `WearableConverter` | `WeaponModuleConfig.java:198` | module |
| `car` | `CarConverter` | `gangland-gadget/.../GadgetModuleConfig.java:100` | module |

Serializers: `unique` @0 `ItemConfig.java:94`, `money` @0 `:95`, `material` @`CATCH_ALL_PRIORITY` `:96`; `weapon`/`ammunition`/`wearable` @0 `WeaponModuleConfig.java:200-202`; `car` @0 `GadgetModuleConfig.java:107`. Refreshers: `uniqueItemRefresher` @0 (`ItemConfig.java:118`); `weaponRefresher` @10, `wearableRefresher` @10, `ammunitionItemRefresher` @0 (`WeaponModuleConfig.java:204-206`); `carItemRefresher` @0 (`GadgetModuleConfig.java:114`).

Priority semantics (`ItemSerializerRegistry.java:40-43`): every `register` appends then re-sorts with `Comparator.comparingInt(Entry::priority).reversed()`; `List.sort` is stable so registration order is preserved within a tier. `serialize` returns the first entry whose predicate matches and whose serializer yields a non-empty value (`:50-60`). `ItemRefresherRegistry` mirrors this and falls back to `source.clone()` when nothing claims the stack (`:61`, `:79`).

## 2. What `keystone-item` already offers

Inventory of `E:\Programming\java\Keystone\keystone-item\src\main\java\org\luckyraven\keystone\item\`: `ItemKind` (interface), `StandardItemKind` (enum: `MATERIAL` only), `ItemConverter`, `ItemConverterRegistry`, `ItemParser`, `ItemSerializer`, `ItemSerializerRegistry`, `MaterialItemSerializer`, `ItemRefresher`, `ItemRefresherRegistry`, `ItemBuilder`; `item.nbt/`: `ItemNbtAccessor`, `NbtType`, `NbtBridge`, `ReflectiveNbtApiAccessor`, `NoOpNbtAccessor`. 110 tests (`docs/keystone-item.md:191`).

- Package layout: nothing new needed; classes are name-for-name identical, so the consumer migration is an import swap plus the `ItemKind implements` line.
- `keystone-item/pom.xml:31-53` — `keystone-common` + log4j-api + XSeries only; the config-DSL binding lives in `keystone-persistence` (persistence → item, never the reverse; roadmap `:251-255`). `keystone-plugin/pom.xml:74` shades it.
- NBT: `keystone-item` owns it via `item.nbt.*`. NBT-API is not a Keystone dependency at any scope (`keystone-item/pom.xml:51-53`). `ReflectiveNbtApiAccessor` binds `NBT.modify`/`NBT.get` reflectively, tolerating both `de.tr7zw.nbtapi` and `de.tr7zw.changeme.nbtapi` (`docs/keystone-item.md:150-156`). `NbtBridge` auto-detects; `NoOpNbtAccessor` drops writes and warns once (`:159-171`).
- Gangland declares `de.tr7zw:item-nbt-api-plugin` at provided (`pom.xml:311-315`, 2.15.7 at `:76`) and hard-depends on the NBTAPI plugin (`plugin.yml:7-10`). 104 `org.luckyraven.keystone.item.*` imports across 99 Gangland files, essentially all `ItemBuilder` and `nbt.NbtBridge`. The E5.2 half is done; only E5.1 (registries) is outstanding.
- Bartizan gets NBT through `NbtBridge` with no NBTAPI build dependency; it may `NbtBridge.install(...)` a PDC accessor (`docs/keystone-item.md:178-182`).

## 3. Registration timing (T-11) and cross-plugin registry ownership

### 3a. How the phases run

`Gangland.onEnable` → `GanglandContext.bootstrap()` (`gangland-impl/.../bootstrap/GanglandContext.java:162-197`): phase hooks FILE (`:167-172`) and DATABASE (`:177`); `moduleLoader.load()` (`:182`); `beanFactory.scan("org.luckyraven.gangland.config")` (`:185`) + `registerConfiguration` per module (`:186-190`); `beanFactory.instantiate()` (`:191`); listener/command phases (`:193-194`); `moduleLoader.enableAll(container)` (`:196`).

`BeanFactory.instantiate` (`keystone-bean/.../BeanFactory.java:210-301`): every `@Bean` inherits its class-level `@Configuration(phase=…)`, default `Phase.CONFIG` (`Configuration.java:37`; `BeanFactory.java:236,247`); phases run KERNEL → FILE → DATABASE → CONFIG → LIFECYCLE → LISTENER → COMMAND (`Phase.java:33-41`); within a phase `BeanGraph.topologicalSort` orders by `@Bean` method-parameter type edges only (`:254`, `:392-397`); phase hook fires per bean (`:276-278`); after all phases `@PostConstruct` (`:292-293`), convention `initialize()` (`:294-296`), `BeanPostInitialize` (`:297`).

Consequence: `ItemConfig` (core) and `WeaponModuleConfig`/`GadgetModuleConfig` are all CONFIG-phase. The only reason module registrations land after the core registry beans is that the module's registration `@Bean` takes the registries as parameters (`WeaponModuleConfig.java:172-181`, `GadgetModuleConfig.java:41-46`).

### 3b. T-11 — the missing edge

`GameplayConfig.java:271-278` — `lootChestLoader(LootChestManager, FileManager)` calls `fileManager.initializeAll()` inline; neither parameter reaches a module converter bean. Mitigating fact (correct the docket): loot chest item strings are not eagerly converted — `LootChestLoader.java:240-258` stores the raw string, `LootTable.generateLoot(tierId, parser)` (`gangland-ui/lootchest-api/.../data/LootTable.java:41`) resolves at roll time, `ChestCooldownManager.resolveUnlockIcon` (`:254-268`) parses at icon-spawn time. So T-11 is latent for loot chests. The genuinely eager path is `SlotItemFactory.create` (`gangland-ui/inventory-api/.../handler/SlotItemFactory.java:36-39`), which is why `inventoryLoader`'s `initialize()` was deferred to a `@PostConstruct` (`GameplayConfig.java:144-158`, `:287-293`). `lootChestLoader` should adopt the same deferral.

### 3c. When converters come from a separate plugin (Bartizan)

1. Bukkit enable order: `SimplePluginManager.loadPlugins` orders construction and `onEnable` by `depend`/`softdepend`/`loadbefore`; `CraftServer.enablePlugins` enables in that order. With Gangland → Bartizan declared, `Bartizan.onEnable()` returns before `Gangland.onEnable()` begins. Without a declaration the order is unspecified.
2. Gangland's registry instance does not exist during Bartizan's `onEnable` — it is created by `ItemConfig.itemConverterRegistry` (`ItemConfig.java:45-56`) inside `beanFactory.instantiate()`.
3. Keystone forbids static singletons: `ItemConverterRegistry` javadoc (`keystone-item/.../ItemConverterRegistry.java:16-19`, `docs/keystone-item.md:93-97`, `docs/phase-h7-module-loader.md:33-36` "No static per-plugin state").

Therefore: **Bartizan publishes, Gangland pulls.**
- Bartizan, in its own `onEnable`, publishes its converters/serializers/refreshers through Bukkit's `ServicesManager` (`Bukkit.getServicesManager().register(ItemConverterProvider.class, impl, this, ServicePriority.Normal)`). Precedent: `@Bean(publishToServicesManager = true)` (`keystone-bean/.../Bean.java:41-45`, `BeanFactory.java:532-534`); Gangland already registers `WeaponRaytracer` this way (`WeaponModuleConfig.java:99`).
- Gangland's `ItemConfig.itemConverterRegistry` bean folds every published provider into its own instance at construction; the Bukkit plugin graph is the ordering edge.
- Gangland declares `softdepend: [Bartizan]` in `gangland-impl/src/main/resources/plugin.yml` (today `:11-14`).

Reject: a Keystone-plugin-scoped singleton registry (breaks the rule and collides Gangland/Bartizan/Oriel vocabularies); a push into Gangland's live registry after its CONFIG phase.

## 4. `Plugins:` — extending the module descriptor for D8

Keys parsed at `keystone-module/.../ModuleDescriptorReader.java:69-97`: `Id` (`:69`), `Version` (`:74`), `Main` (`:77`), `Host_Api` (`:80`), `Depends` (`:83-91`), `Artifact` (`:93`), `Name` (`:96`). `Depends` entries validated against `ModuleDescriptor.ID_PATTERN = [a-z0-9][a-z0-9_-]*` (`ModuleDescriptor.java:33`).

- **Parse** in `ModuleDescriptorReader.parse`, mirroring the `Depends` block (`:83-91`): `yaml.getStringList("Plugins")`, fail `invalid(jar, "Plugins must be a list of Bukkit plugin names", "Plugins")` when present but not a list; validate each name. **Do not reuse `ID_PATTERN`** (lower-case only; rejects `PlaceholderAPI`, `NBTAPI`, `Citizens`, `Bartizan`). Add `PLUGIN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+")` beside `ID_PATTERN`.
- **Record** `ModuleDescriptor` (`:24-45`): add `List<String> plugins` between `depends` and `artifact`; defensive copy like `depends` (`:43`). Record component addition is binary-incompatible → minor bump 1.8.0 → 1.9.0.
- **Validate** in `ModuleResolution` (`:43-140`), whose contract is pure (`:17`). Inject a predicate: `resolve(Collection<ModuleDescriptor>, PluginVersion hostApi, Predicate<String> pluginEnabled)`; keep the 2-arg overload delegating with `name -> true`. Slot in as step 2.5 between the `Host_Api` filter (`:68-79`) and the dependency fixpoint (`:82-100`) so a missing plugin cascades to dependants.
- **Enforce** in `ModuleLoader.load()` (`:135-192`) at the single `ModuleResolution.resolve(discover(), hostApi)` call (`:141`): predicate = `Bukkit.getPluginManager().getPlugin(name)` non-null and `isEnabled()`.
- **Fault id**: existing ids `module.duplicate`, `module.host.incompatible`, `module.dependency.missing`, `module.cycle` (`ModuleResolution.java:30-33`), `module.folder`, `module.main.*`, `module.configure.failed`, `module.enable.failed`, `module.disable.failed` (`ModuleLoader.java:54-60`). Use `module.plugin.missing`, built as `Fault.dependency(...)` (kind at `:89`), context `module`, `jar`, `plugin`; document in `docs/keystone-module.md` fault table (`:153-165`).
- **Tests**: `ModuleDescriptorReaderTest.read_complete` (`:29-54`) add `Plugins:` to the fixture + assert; `read_defaults` (`:56-66`) assert empty; new `read_pluginsNotAList` (mirror `:91-100`) and `read_badPluginName` (mirror `:80-89`); `ModuleResolutionTest` new case with `pluginEnabled = name -> false` producing `module.plugin.missing` and cascading; `ModuleLoaderTest` (`:47-…`) end-to-end — it installs `BukkitStatics` around `factory.instantiate()` (`:104-106`); `BukkitStatics.install()` mocks `Bukkit.getPluginManager()` (`keystone-testkit/.../BukkitStatics.java:60,76`). Injection avoids moving `loader.load()` (`:80`) inside the try. `TestJars` (`keystone-testkit/.../TestJars.java:52-56`) needs no change.
- Limitation: `Plugins:` is a fail-fast guard, not an ordering mechanism. Bukkit enable order still needs `softdepend: [Bartizan]` in Gangland's `plugin.yml`. Both are required.

## 5. Classloader implications for a module compiled against `bartizan-api`

`ModuleLoader` builds its loader with the host plugin's loader as parent (`ModuleLoader.java:78-80`); `ModuleClassLoader extends URLClassLoader` (`ModuleClassLoader.java:20-28`), parallel-capable (`:22-24`), jars added via `addJar` (`:31-37`) before any `Main` is instantiated (`ModuleLoader.java:144-148`); no `loadClass` override → parent-first (`:11-14`).

Resolution of a Bartizan class from a module walks: `ModuleClassLoader.loadClass` → parent = Gangland's `PluginClassLoader` → `loadClass0(name, resolve, checkGlobal=true, checkLibraries=true)` → (1) own jar, (2) `libraryLoader`, (3) `checkGlobal` → `JavaPluginLoader.getClassByName` iterates every registered `PluginClassLoader` with `checkGlobal=false` → Bartizan's loader defines it. **Bartizan's classes are reachable from a module with no reflection.**

`softdepend` is not what gates visibility (Spigot's global lookup hands back any plugin's class); it changes load/enable order, silences the once-per-class warning `"[Gangland_Warfare] loaded class … from Bartizan which is not a depend, softdepend or loadbefore of this plugin"` (the plugin named is Gangland, whose `PluginClassLoader` started the lookup — so the declaration goes in Gangland's `plugin.yml`), and defines absence behaviour (`NoClassDefFoundError` at first touch, which `Plugins:` converts into a clean `module.plugin.missing`).

Packaging: `bartizan-api` at `provided` scope in module poms, never bundled (precedent: cops/gadget declare `gangland-weapon` provided). Resources are parent-first too (`ModuleClassLoader.java:16-18`); root-level module files go through `LoadedModule.readResource` (`LoadedModule.java:41-62`). `@CustomLog` resolves `module.properties` through the module's loader (`docs/keystone-module.md:142-146`).

Paper: plugin jars are remapped into `plugins/.paper-remapped/`; module jars under the host data folder are never remapped. Any NMS symbol in a module jar breaks on Paper. Gangland is safe today (NMS lives in `gangland-compatibility/version-*`, shaded into the core jar; `documentation/developer/compatibility.md:173-175`, `:215-217`). Rule for Bartizan: recoil/NMS stays in the Bartizan plugin jar; `bartizan-api` carries zero NMS symbols. Paper's `PaperPluginClassLoader`/`PluginClassLoaderGroup` tightening is another reason to declare `softdepend`.

## 6. Keystone versioning, docs, build, artifact location

- Version: `<revision>1.8.0</revision>` at `E:\Programming\java\Keystone\pom.xml:58`; `flatten-maven-plugin` (`:321-345`). Policy: one minor bump per phase (`docs/extraction-roadmap.md:348`). D3 + D8 → **1.9.0**.
- Phase doc model: `docs/phase-h7-module-loader.md` (`:1-70`): title `# Phase H<n> — <title> (v<version>)`, 3-row table Version/Driver/Compatibility (`:10-14`), `## The inventory`, `## The rule`, `## What shipped`, decisions, tests, limitations. Add a row to `docs/README.md` (`:29-35`); update `docs/keystone-item.md` wiring recipe (`:93-131`) with the priority overloads and the test count (`:191`); amend `docs/extraction-roadmap.md` E5 section (`:224-318`) like E3.2/E4 (`:139-143`, `:209-215`).
- Build: `mvn clean install` at the Keystone root installs every module to `~/.m2`. Plugin jar: `keystone-plugin/pom.xml:17` `<finalName>Keystone-${project.version}</finalName>`, shades common/bean/command/item/persistence/hooks/module (`:62-87`; testkit test-scope only `:89-94`) → `E:\Programming\java\Keystone\keystone-plugin\target\Keystone-1.9.0.jar`. Gangland pins `<keystone.version>` (`pom.xml:75`) at provided (`:166-212`).
- Central: `release` profile (root pom `:409-488`), `keystone-plugin` excluded (`:480-482`). Not needed locally.

## 7. Oriel

Oriel pins `keystone = "1.7.0"` (`E:\Programming\java\Oriel\gradle\libs.versions.toml:10`); aliases (`:44-49`) exclude `keystone-item`. Repo-wide grep for `keystone.item|ItemBuilder|ItemParser|ItemConverter|ItemSerializer|ItemRefresher` returns no files. Oriel has its own prefix machinery: `menu-core/.../core/registry/ItemProvider.java:33-41` (`ItemStack resolve(Player viewer, String key)`, javadoc names Gangland's unique-item registry, ItemsAdder, MMOItems, MythicMobs at `:8-10`, example registering `"gangland"` at `:19-30`) and `MaterialSource.java:37-45` (`resolve(Player viewer, String value)`, dash-separated).

Implications: the promotion cannot break Oriel; Oriel may stay on 1.7.0. The blocking difference for one shared framework is the `Player viewer` parameter — Keystone's `ItemConverter` is viewer-blind (Gangland threads the player through `ItemRefresher.refresh(source, context)` and `Wearable.buildItem(Player)`). Decide the viewer overload in 1.9.0 or accept two consumers instead of three. Oriel's `menu-plugin` uses `mavenLocal()` for Keystone (Oriel `CLAUDE.md:155`).

## Key files to read (10)

1. `E:\Programming\java\Keystone\docs\extraction-roadmap.md` §E5 (`:224-318`, `:316-318`).
2. `E:\Programming\java\Keystone\docs\keystone-item.md` (`:93-131` wiring recipe).
3. `gangland-impl/src/main/java/org/luckyraven/gangland/config/ItemConfig.java` (`:80-98`).
4. `gangland-impl/src/main/java/org/luckyraven/gangland/config/GameplayConfig.java` (`:271-278`, `:138-158`, `:280-293`).
5. `Keystone/keystone-bean/src/main/java/org/luckyraven/keystone/bean/BeanFactory.java` (`:210-301`, `:392-397`).
6. `gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/GanglandContext.java` (`:182`, `:185-191`).
7. `Keystone/keystone-module/src/main/java/org/luckyraven/keystone/module/ModuleDescriptorReader.java` (`:68-98`).
8. `Keystone/keystone-module/src/main/java/org/luckyraven/keystone/module/ModuleResolution.java` (`:17`, `:43-140`).
9. `Keystone/keystone-module/src/main/java/org/luckyraven/keystone/module/ModuleClassLoader.java` (`:11-43`) + `ModuleLoader.java:78-88, 144-148`.
10. `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/WeaponModuleConfig.java` (`:172-213`).

## Risks the architects must know

1. D3 is a consumer migration, not an extraction — and Gangland has drifted away from the upstream copy: the priority overloads + `CATCH_ALL_PRIORITY` exist only in Gangland and are load-bearing (`ItemConfig.java:96`, `WeaponModuleConfig.java:204-206`, `GadgetModuleConfig.java:44-46`). Back-port to Keystone before deleting Gangland's copies.
2. Static shared registries are forbidden by Keystone's architecture. Any design where Bartizan pushes into a Keystone registry needs a per-consumer instance plus a `ServicesManager` publish/pull. Most likely design mistake in this wave.
3. A separate plugin cannot register into Gangland's registry today (created inside `Gangland.onEnable`). Invert: Gangland pulls, with `softdepend` + a service seam.
4. T-11 as filed is slightly mis-scoped (loot chests resolve lazily; `SlotItemFactory` is the eager path, already deferred). Fix `lootChestLoader` by the same deferral; update the docket entry.
5. `Plugins:` cannot fix ordering; ship it together with `softdepend: [Bartizan]`.
6. Do not reuse `ModuleDescriptor.ID_PATTERN` for plugin names.
7. Do not put `Bukkit.getPluginManager()` inside `ModuleResolution`; inject a `Predicate<String>`.
8. Adding a record component to `ModuleDescriptor` is binary-incompatible (contained; forces the minor bump).
9. `ItemConverter` is viewer-blind; Oriel's `ItemProvider` is not. Decide in 1.9.0.
10. Module jars are never Paper-remapped; `bartizan-api` must carry zero NMS symbols; recoil stays in the Bartizan plugin jar.
11. The cross-plugin class warning names Gangland; `softdepend` goes in Gangland's `plugin.yml`, not in a module.
12. Oriel is on 1.7.0, Gangland on 1.8.0; an additive-only 1.9.0 leaves Oriel free to stay.
