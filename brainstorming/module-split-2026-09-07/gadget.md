# Flip 2: gangland-gadget → runtime module `gadget`

**Assumes done:** flip 1 (cops-n-crooks is a runtime module). Concretely this plan assumes that every
`org.luckyraven.gangland.copsncrooks.*` import is gone from `gangland-impl`, and that flip 1's T14 has moved the
cops half out of `config/CopsAndGadgetsConfig.java` and **renamed the file to
`gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java`** (README decision log, "gadget
OQ-1"), leaving a **gadget-only remainder**: four `@Bean` methods — `carMessageContract`, `carAccessPolicy`,
`carService`, `jetpackService`. `weapon` and `turf` are still compiled into the core jar.
**Module:** `gangland-features/gangland-gadget` · id `gadget` · package root `org.luckyraven.gangland.gadget` · jar
`target/modules/gangland-gadget-0.8.4.jar` · `Depends:` now `[]` (weapon and turf are still core), later
`[weapon]` — **flip 4 owns that edit**.
**Planner:** opus, 2026-09-07. **Graph:** refreshed 2026-09-07 16:28 (`graphify-out/graph.json`), newer than HEAD
`d6bb33ac` (2026-09-07 16:11) — fresh.

---

## 0. Summary for the scrum master

**Counts.** 30 `gangland-impl` main files reference the feature once `p0-wave-3` lands (the 29 found by
`grep -rl "org\.luckyraven\.gangland\.gadget\." gangland-impl/src` today, plus the new
`gangland-impl/.../gadget/GanglandCarGangs.java` that `p0-wave-3` adds). Of those:

| Decision | Count | What |
|---|---|---|
| **MOVE** to the module | 15 | 4 car commands, 2 persistence classes, 3 contract impls, 3 item classes, 3 sign classes |
| **SPLIT** (feature slice moves, core keeps a remainder) | 9 | `GadgetConfig` (flip 1's rename of `CopsAndGadgetsConfig`; emptied and deleted here), `FileConfig`, `GameplayConfig`, `ItemConfig`, `ItemPredicates`, `SignManager`, `ViewSign`, `ViewInventoryAspect`, `CustomPlayerDeathListener` |
| **KEEP** (import-only edit, the type stops being a gadget type) | 6 | `UniqueItemAddon`, `ItemCommand`, `ItemWearable{,Give,Info,List}Command` |

**Two pre-flip relocations shrink the flip and are the single biggest idea in this plan** (group A, both stay
inside the core jar, both are pure Maven-module moves that keep the reactor green):

1. **`FuelService` moves from `gangland-gadget` to `gangland-infra/gangland-item`**
   (`org.luckyraven.gangland.gadget.fuel.FuelService` → `org.luckyraven.gangland.item.fuel.FuelService`). It is the
   only implementation of `FuelContract`, and two **core** listeners in `gangland-item`
   (`FuelHoldDisplayListener`, `FuelRefuelListener`, both `@AutowireTarget({FuelContract.class})`) plus the core
   `UniqueItemAddon` need that bean whether or not the gadget module is installed. `Fuel`, `FuelKey`, `FuelBar`
   and `FuelContract` already live in that package. Without this move the flip needs a fourth seam and the fuel
   HUD silently dies on a server with no gadget module.
2. **`WearableAddon` moves from `gangland-gadget` to `gangland-features/gangland-weapon`**
   (`org.luckyraven.gangland.gadget.wearable.WearableAddon` →
   `org.luckyraven.gangland.weapon.wearable.WearableAddon`). It already `extends` weapon's `WearableService`
   (`gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/wearable/WearableService.java:35`),
   and every consumer of it is wearable-shaped, not gadget-shaped: `wearableService` and `wearableEquipService`
   beans (`GameplayConfig.java:213,308`), `WeaponRaytracer` (`GameplayConfig.java:202`), `WearableConverter`,
   `WearableRefresher`, `WearableItemSerializer`, `SignManager`'s wearable signs, and the four
   `/glw item wearable` commands. Leaving it in gadget would make the gadget module the owner of the whole
   wearable stack and leave the core with **no** `WearableService` bean when the module is absent. After the move
   the entire wearable stack hands to flip 4 in one piece. (Note for the record: the brief calls `WearableRefresher`
   a gadget class; by imports it is not — `item/refresher/WearableRefresher.java:10` imports
   `weapon.wearable.WearableService` and nothing from gadget.)

**Seams introduced (3 + 1 registry change).** All live in core, all named after the core concept, all reusable by
the weapon plan — **converge**:

| Seam | Core home | Mechanism | Consumer + phase |
|---|---|---|---|
| Item converter / serializer / refresher registration | *(no new interface)* | rule 3(a): the module's `@Bean` methods take the existing `ItemConverterRegistry` / `ItemSerializerRegistry` / `ItemRefresherRegistry` beans as parameters and register into them | the registries are read at runtime (item delivery, sign trade, `/glw` give); the parameter is the `BeanGraph` ordering edge |
| `ItemSerializerRegistry.register(predicate, serializer, priority)` | `gangland-infra/gangland-item/.../item/ItemSerializerRegistry.java` | new 3-arg overload + stable priority sort; the core's `MATERIAL` catch-all drops to `CATCH_ALL_PRIORITY` | needed because registration order is priority order and a module always appends **after** the catch-all |
| `SignTypeContribution` | `org.luckyraven.gangland.sign.extension` (gangland-impl) | `List<Sign> signs(String signPrefix)`; pulled with `container.getAllInstances(...)` | `SignManager.setupSigns()`, which must be moved from the `@Bean` body into the LIFECYCLE convention `initialize()` pass |
| `SignViewProvider` | same package | `boolean open(Player, String content)` | `ViewInventoryAspect.execute` — replaces its hard-coded car branch |

**p0-wave-3 overlap (`.claude/worktrees/p0-wave-3`, verified by `git status --porcelain` in that worktree).**
Files this plan touches that `p0-wave-3` also touches — the scrum master must merge `0.8.3` into `0.8.4` **before**
group B starts:

- `gangland-impl/.../config/CopsAndGadgetsConfig.java` (M — adds a `carAccessPolicy` bean; flip 1 renames this
  file to `config/GadgetConfig.java`, so after both merges look for it under the new name)
- `gangland-impl/.../config/GameplayConfig.java` (M)
- `gangland-impl/.../gadget/GanglandCarGangs.java` (**A** — a brand-new impl file in package
  `org.luckyraven.gangland.gadget`, i.e. a split package with the module root; **must** move)
- `gangland-features/gangland-gadget/.../gadget/car/access/{CarAccessPolicy,CarGangContract}.java` (**A**)
- `gangland-features/gangland-gadget/.../gadget/listener/car/{CarDamageListener,CarEntityInteractListener}.java` (M)
- `gangland-ui/sign-api/.../sign/listener/SignCreation.java` (M), `.../sign/service/SignInformation.java` (M),
  `.../sign/{SignPermissions,listener/SignProtection}.java` (A), `gangland-impl/.../sign/GanglandSignInformation.java` (M)
- `gangland-impl/src/main/resources/message/message_{en,es}.yml` (M)

None of those are files this plan *moves* except `GanglandCarGangs.java`; the sign-api ones are adjacent to the
sign seam (T4/T12) and the two gadget car listeners are adjacent to the module's listener package (no edit needed).

**Task groups.** A (2 tasks, ~21 files) · B (3 tasks, ~13 files) · C (2 tasks, ~7 files) · D1 (3 tasks, ~17 files) ·
D2 (3 tasks, ~19 files) · E (3 tasks, ~10 files). Compile gate after every group.

**Three biggest risks.**
1. **`SignManager.initialize()` timing.** The seam only works if `setupSigns()` runs after module beans exist.
   Today `GameplayConfig.signManager()` calls `manager.initialize()` inside the `@Bean` body (`GameplayConfig.java:266`)
   *and* Keystone's convention pass calls the same zero-arg `initialize()` again
   (`Keystone/keystone-bean/.../BeanFactory.java:294,581-607` — `SignManager` is neither a `FileInitializer` nor a
   `BeanLifecycle`, so it is not skipped). Deleting the explicit call is therefore behaviour-preserving *and* fixes a
   latent double-initialisation — but it is the one change in this plan that moves work between phases. Verify with
   the smoke checklist that `/glw`-prefixed signs still parse.
2. **Item-string parse ordering.** `GameplayConfig.lootChestLoader(...)` calls `fileManager.initializeAll()` inside
   its CONFIG-phase `@Bean` body (`GameplayConfig.java:335-340`), so `loot_chests.yml` item refs are resolved through
   `ItemParser` at that moment. A module `CarConverter` bean has no ordering edge to it. No shipped YAML uses a
   `car:` ref (`grep -rn "car:" gangland-impl/src/main/resources/` finds only `sports_car:` inside `cars.yml` and a
   message string), so this is latent, not live. Logged to triage; no core change in this flip (see §6, OQ-2).
3. **Split package `org.luckyraven.gangland.gadget`.** `p0-wave-3` puts `GanglandCarGangs` in the *impl* jar under
   the module's own root package. Keystone's `ModuleClassLoader` is parent-first, so a leftover copy in the core jar
   would shadow the module's. T10 must move it and the G3 grep must prove the core jar has zero
   `org/luckyraven/gangland/gadget/` entries.

---

## 1. Inventory (facts, with source locations)

### 1.1 Impl files that reference the feature

Source list: `grep -rl "org\.luckyraven\.gangland\.gadget\." gangland-impl/src` (29 hits) plus
`gangland-impl/src/main/java/org/luckyraven/gangland/gadget/GanglandCarGangs.java` (arrives with `p0-wave-3`).
"After A" = whether the file still references gadget after group A's two relocations.

| path | role | uses from the feature | decision | after A? | p0-w3 |
|---|---|---|---|---|---|
| `command/sub/car/CarCommand.java` | command | `gadget.car.config.CarAddon` (:10) | **MOVE** → `org.luckyraven.gangland.gadget.command` | yes | n |
| `command/sub/car/CarGiveCommand.java` | command | `gadget.car.Car`, `CarAddon` (:14,15) | **MOVE** → same | yes | n |
| `command/sub/car/CarInfoCommand.java` | command | `Car`, `CarAddon` (:13,14) | **MOVE** → same | yes | n |
| `command/sub/car/CarListCommand.java` | command | `Car`, `CarAddon` (:10,11) | **MOVE** → same | yes | n |
| `command/sub/item/ItemCommand.java` | command | `gadget.wearable.WearableAddon` (:13) | **KEEP** — import → `weapon.wearable.WearableAddon` | no | n |
| `command/sub/item/wearable/ItemWearableCommand.java` | command | `WearableAddon` (:11) | **KEEP** — import swap | no | n |
| `command/sub/item/wearable/ItemWearableGiveCommand.java` | command | `WearableAddon` (:14) | **KEEP** — import swap | no | n |
| `command/sub/item/wearable/ItemWearableInfoCommand.java` | command | `WearableAddon` (:13) | **KEEP** — import swap | no | n |
| `command/sub/item/wearable/ItemWearableListCommand.java` | command | `WearableAddon` (:10) | **KEEP** — import swap | no | n |
| `config/GadgetConfig.java` *(flip 1 renames `CopsAndGadgetsConfig.java` to this)* | config | `CarService`, `ParkedCar`, `CarAddon`, `CarMessageContract`, `VehicleRegistry`, `GadgetPhysicsConfig`, `FuelService`, `JetpackService`, `WearableAddon` (:55-63 pre-rename) + `CarAccessPolicy`, `GanglandCarGangs` (p0-w3) | **SPLIT** — the four gadget beans move and the emptied file is **deleted** | yes | **y (M)** |
| `config/FileConfig.java` | config | `CarAddon`, `GadgetPhysicsConfig`, `FuelService`, `WearableAddon` (:18-21) | **SPLIT** — `gadgetPhysicsConfig` (:141-144) and `carAddon` (:205-212) move; `fuelService` (:181-184) and `wearableAddon` (:196-203) stay with new imports | yes | n |
| `config/GameplayConfig.java` | config | `CarAddon`, `WearableAddon` (:27,28) | **SPLIT** — `signManager` (:259-269) drops `CarAddon` and the explicit `initialize()`; wearable beans keep new import | yes | **y (M)** |
| `config/ItemConfig.java` | config | `CarAddon` (:5) | **SPLIT** — `carConverter` (:52-55), `carItemSerializer` (:123-126), `carItemRefresher` (:177-180) move; the three registry beans drop their car parameters | yes | n |
| `database/repositories/car/ParkedCarRepository.java` | repository | `ExhaustSide`, `ParkedCar` (:5,6) | **MOVE** → `org.luckyraven.gangland.gadget.database` | yes | n |
| `database/tables/car/ParkedCarTable.java` | table | `ParkedCar` (:3) | **MOVE** → `org.luckyraven.gangland.gadget.database` | yes | n |
| `file/configuration/GadgetPhysicsConfigImpl.java` | contract impl | `gadget.config.GadgetPhysicsConfig` (:3) | **MOVE** → `org.luckyraven.gangland.gadget.contract` | yes | n |
| `file/configuration/gadget/GanglandCarMessages.java` | contract impl | `gadget.car.message.CarMessageContract` (:4) | **MOVE** → `org.luckyraven.gangland.gadget.contract` (delete the now-empty `file/configuration/gadget/` dir) | yes | n |
| `item/ItemPredicates.java` | item | `gadget.car.CarKey` (:6) — the `CAR` constant (:32) | **SPLIT** — delete `CAR` + the import; module gets `GadgetItemPredicates.CAR` | yes | n |
| `item/configuration/UniqueItemAddon.java` | item config | `gadget.fuel.FuelService` (:11, field :28, ctor :37, use :141) | **KEEP** — import → `item.fuel.FuelService` | no | n |
| `item/converter/CarConverter.java` | item converter | `Car`, `CarManager` (:5,6) | **MOVE** → `org.luckyraven.gangland.gadget.item` | yes | n |
| `item/refresher/CarItemRefresher.java` | item refresher | `Car`, `CarAddon` (:7,8) | **MOVE** → same | yes | n |
| `item/serializer/CarItemSerializer.java` | item serializer | `CarKey` (:6) | **MOVE** → same | yes | n |
| `listener/player/CustomPlayerDeathListener.java` | listener | `gadget.jetpack.JetpackService` (:38, field :71, ctor :78, call :211) | **SPLIT** — the one call moves onto `PlayerDownedEvent` in the module's existing `JetpackSessionLifecycleListener` | yes | n |
| `sign/SignManager.java` | sign | `gadget.car.CarManager` (:8) + `CarBuySign`/`CarSellSign` (:23,24) | **SPLIT** — seam `SignTypeContribution` | yes | n |
| `sign/aspect/ViewInventoryAspect.java` | sign aspect | `Car`, `CarManager` (:10,11); `findCar` (:293), `openCarView` (:209) | **SPLIT** — seam `SignViewProvider` | yes | n |
| `sign/type/ViewSign.java` | sign type | `CarManager` (:6) | **SPLIT** — takes `SignContributions` instead | yes | n |
| `sign/type/trade/car/CarBuySign.java` | sign type | `Car`, `CarManager` (:5,6) | **MOVE** → `org.luckyraven.gangland.gadget.sign` | yes | n |
| `sign/type/trade/car/CarSellSign.java` | sign type | `Car`, `CarManager` (:5,6) | **MOVE** → same | yes | n |
| `sign/validation/trade/car/CarSignValidator.java` | sign validator | `CarManager` (:4) | **MOVE** → same | yes | n |
| `gadget/GanglandCarGangs.java` *(p0-wave-3)* | contract impl | `gadget.car.access.CarGangContract` | **MOVE** → `org.luckyraven.gangland.gadget.car.access` | yes | **y (A)** |

Nothing outside `gangland-impl` and `gangland-gadget` names a gadget type:
`grep -rn "org\.luckyraven\.gangland\.gadget" --include=*.java .` (minus target/worktrees) returns exactly one hit
outside those two modules, and it is a javadoc reference in
`gangland-infra/gangland-item/.../item/fuel/FuelContract.java:9` (fixed by group A).

Poms that name `gangland-gadget`: `gangland-features/pom.xml:21` (reactor member, unchanged),
`gangland-impl/pom.xml:101` (**removed**), root `pom.xml:267` (dependencyManagement, unchanged).

### 1.2 Feature-side files that reference impl

**None today.** `grep -rh "^import org.luckyraven" gangland-features/gangland-gadget/src/main/java` yields only
`gangland.core.downed.*`, `gangland.item.{fuel,wearable}.*`, `gangland.weapon.*` and `keystone.*` — no
`gangland-impl` type. After the flip the module will legitimately import `Gangland`, `Messages`, `Settings`,
`UserManager`, `MemberManager`, `Command`, and the sign/inventory UI types (direction module → core).

### 1.3 Beans the feature contributes today

| config class (`gangland-impl/.../config/`) | method | phase | parameters | target |
|---|---|---|---|---|
| `FileConfig.java:141-144` | `gadgetPhysicsConfig` | FILE | `Settings settings` *(ordering-only param — keep it, memory rule "bean ordering via params")* | `GadgetFileConfig` |
| `FileConfig.java:205-212` | `carAddon` | FILE | `PermissionManager`, `FileManager`, `PlaceholderService` | `GadgetFileConfig` (+ a `ModuleLoader` param for the module-jar `FileHandler`) |
| `FileConfig.java:181-184` | `fuelService` | FILE | `Settings settings` (ordering-only) | **stays in core** after relocation R1 |
| `FileConfig.java:196-203` | `wearableAddon` | FILE | `PermissionManager`, `FileManager`, `PlaceholderService` | **stays in core** after relocation R2 |
| `GadgetConfig.java` *(`CopsAndGadgetsConfig.java:152-155` pre-rename)* | `carMessageContract` | CONFIG | — | `GadgetModuleConfig` |
| `GadgetConfig.java` *(p0-w3)* | `carAccessPolicy` | CONFIG | `MemberManager`, `PermissionManager` | `GadgetModuleConfig` |
| `GadgetConfig.java` *(`:401-411` pre-rename)* | `carService` | CONFIG | `CarAddon`, `RepositoryRegistry`, `FuelService`, `GadgetPhysicsConfig`; body calls `carService.reloadParkedVehicles()` | `GadgetModuleConfig` |
| `GadgetConfig.java` *(`:413-419` pre-rename)* | `jetpackService` | CONFIG | `FuelService`, `GadgetPhysicsConfig`, `WearableAddon`, `WeaponService` | `GadgetModuleConfig` |
| `ItemConfig.java:52-55` | `carConverter` | CONFIG | `CarAddon` | `GadgetModuleConfig` (registers into `ItemConverterRegistry`) |
| `ItemConfig.java:123-126` | `carItemSerializer` | CONFIG | — | `GadgetModuleConfig` (registers into `ItemSerializerRegistry`) |
| `ItemConfig.java:177-180` | `carItemRefresher` | CONFIG | `CarAddon` | `GadgetModuleConfig` (registers into `ItemRefresherRegistry`) |
| `GameplayConfig.java:259-269` | `signManager` | CONFIG | …, `WearableAddon`, **`CarAddon`** | `CarAddon` param removed; `DependencyContainer` added; `manager.initialize()` deleted |

`CarService implements BeanLifecycle` (`gadget/car/CarService.java:57`, `onInitialize`/`onShutdown` at :697,:707) and
`JetpackService` likewise — Keystone auto-calls `onInitialize(true)` right after registration
(`BeanFactory.java:266-268`), so both keep working unchanged inside a module config.

### 1.4 Resources

- **YAML default to move:** `gangland-impl/src/main/resources/items/cars.yml` →
  `gangland-features/gangland-gadget/src/main/resources/items/cars.yml` (same data-folder path `items/cars.yml`; the
  5-arg `FileHandler` resolves `items/cars.yml` from the module classloader —
  `Keystone/keystone-persistence/.../FileHandler.java:86-95,186-205`). Its registration moves out of
  `KernelConfig.fileManager()` (`gangland-impl/.../config/KernelConfig.java:176`) into the module.
  `items/{ammunition,unique_items,wearables,money}.yml` all stay in core.
- **`commands.json` keys to move** (`gangland-impl/src/main/resources/commands.json:566-585`), exactly 5:
  `car`, `car_help`, `car_give`, `car_info`, `car_list`. The `fuel*` keys (`:586-616`) and the `item_wearable*`
  keys (`:642-657`) **stay in core** — no fuel command and no wearable command imports gadget.
- **Messages that stay in core:** every `CAR_*` key in `file/configuration/Messages.java` and in
  `message/message_{en,es}.yml`. `GanglandCarMessages` moves to the module and imports the core `Messages` enum
  (module → core is legal).
- **`module.properties`:** `gangland-features/gangland-gadget/src/main/resources/org/luckyraven/gangland/gadget/module.properties`
  **already exists** (`module.name=${project.name}`) — verify only.
- **settings.yml:** the `Gadget.*` keys read by `GadgetPhysicsConfigImpl` stay in `settings.yml` (shared top-level
  file). `Settings` exposes only primitives (`Settings.getGadgetJetpackThrustRampTicks()` …), so no feature type
  leaks into core — rule 5 is satisfied with no edit.

### 1.5 Tests

- Impl tests referencing the feature: **one** —
  `gangland-impl/src/test/java/org/luckyraven/gangland/listener/player/CustomPlayerDeathListenerQuitTest.java:64-71`
  passes `null` as the `JetpackService` constructor argument with a comment explaining Netty cannot be loaded in
  tests. T5 deletes that argument and the comment. The test **stays in core**.
- Feature tests that already exist (all stay put, no edits except imports):
  `gadget/car/CarNbtIdentityTest`, `gadget/car/ExhaustSideTest`, `gadget/car/vehicle/ParkedVehicleTest`,
  `gadget/car/vehicle/VehicleSessionTest`, `gadget/jetpack/JetpackTaskConsumptionRateTest` (imports `FuelService` —
  update to `item.fuel.FuelService` in T1), plus `gadget/car/access/CarAccessPolicyTest` arriving with `p0-wave-3`.
- Count assertion: `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java:41`
  asserts `225`. `commands.json` has exactly 225 keys today. Flip 1 lowers it by however many cop/jail keys it moves;
  **this flip lowers the then-current asserted value by exactly 5.** The executor reads the number that is in the
  file when they start and writes `that value − 5`.
- New tests: `GadgetModuleTest` (module side), `SignContributionsTest` + `SignManagerContributionTest`
  (gangland-impl), `ItemSerializerRegistryPriorityTest` (gangland-item). §3 says how to make each red first.

### 1.6 Seams needed

**(a) Item registries — no new interface (rule 3a).** The module's `@Bean` methods take the existing
`ItemConverterRegistry`, `ItemSerializerRegistry`, `ItemRefresherRegistry` beans as parameters and call `register`.
The parameter *is* the `BeanGraph` ordering edge (`BeanFactory.buildDefinition`,
`Keystone/keystone-bean/.../BeanFactory.java:388-397`), so the module bean is invoked after the core registry bean in
the same CONFIG phase. All three registries are mutable after construction
(`gangland-infra/gangland-item/.../ItemConverterRegistry.java:16`, `ItemSerializerRegistry.java:23`,
`ItemRefresherRegistry.java:19-25`) and are read at gameplay time, not at bootstrap. **The weapon plan reuses this
exact mechanism — converge.**

**(b) `ItemSerializerRegistry` priority.** `ItemSerializerRegistry.serialize` walks `entries` in insertion order and
returns the first match (`ItemSerializerRegistry.java:32-42`), and the core registers `ItemPredicates.MATERIAL` — a
catch-all that matches every non-air stack — **last** (`ItemConfig.java:151`). A module always appends after that, so
a module serializer would never fire. Add:

```java
public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;

public void register(Predicate<ItemStack> predicate, ItemSerializer serializer) {
    register(predicate, serializer, 0);
}

public void register(Predicate<ItemStack> predicate, ItemSerializer serializer, int priority) {
    entries.add(new Entry(predicate, serializer, priority));
    entries.sort(Comparator.comparingInt(Entry::priority).reversed());   // List.sort is stable
}
```

`List.sort` is stable, so the existing UNIQUE → WEAPON → AMMUNITION → WEARABLE → CAR → MONEY order inside priority 0
is preserved exactly; only `MATERIAL` moves to `CATCH_ALL_PRIORITY` and therefore always sorts last.

**(c) `SignTypeContribution`** — new file `gangland-impl/src/main/java/org/luckyraven/gangland/sign/extension/SignTypeContribution.java`:

```java
public interface SignTypeContribution {

	/**
	 * The sign types this contribution adds. {@code signPrefix} is the plugin's sign prefix with the dash already
	 * appended ("glw-"), matching what {@code SignManager.setupSigns()} builds its own keys with.
	 */
	List<Sign> signs(String signPrefix);
}
```

**(d) `SignViewProvider`** — new file in the same package:

```java
public interface SignViewProvider {

	/**
	 * Open a view for {@code content} if this provider owns that name.
	 *
	 * @return {@code true} when the view was opened, {@code false} to let the next provider (and finally the core's
	 *         generic item view) try.
	 */
	boolean open(Player player, String content);
}
```

**(e) `SignContributions`** — holder in the same package, modelled on
`gangland-impl/.../command/extension/CommandContributions.java:14-50`:

```java
public final class SignContributions {

	public static SignContributions from(DependencyContainer container) { … getAllInstances of both types … }
	public static SignContributions none() { … }

	public List<Sign> createSigns(String signPrefix) { … flatMap in registration order … }
	public boolean openView(Player player, String content) { … first provider that returns true … }
}
```

**Who registers:** the gadget module (`GadgetModuleConfig`), one bean per concrete type.
**Who consumes and when:** `SignManager.setupSigns()`. `setupSigns()` runs from `SignService.initialize()`
(`gangland-ui/sign-api/.../SignService.java:29-43`), which Keystone's convention pass invokes **after every phase and
after `@PostConstruct`** (`BeanFactory.instantiate`, `Keystone/keystone-bean/.../BeanFactory.java:294-296` →
`runInitialize`, `:581-607`). Module `@Configuration`s are folded into the same pipeline before `instantiate()`
(`GanglandContext.java:185-191`), so every module bean exists by then. `SignManager` therefore must hold the
`DependencyContainer` and call `SignContributions.from(container)` **inside `setupSigns()`**, not in its
constructor — this is the one place where the pattern differs from `GangCommand`, whose COMMAND phase is already
past every bean.
**Why a seam and not a move:** `view`, and the sign registry itself, are core concepts used by weapon, ammo,
wearable, bounty and wanted signs; only the car rows are gadget's.

---

## 2. Ordered tasks

> House rules apply to every task: Spigot only, method braces on their own lines, Lombok `@CustomLog`, block-style
> YAML with `Capitalized_Underscore_Separated` keys, `ChatUtil.color()` with `&` codes, never add test dependencies
> to a module pom, `commands.json` entry in the jar that owns the command. Executors never commit.
>
> **Line numbers are as of `d6bb33ac`; after flip 1 or the `0.8.3` merge has touched a file, locate by symbol and
> record the drift in §7.**

### Group A — pre-flip relocations (still one reactor, everything stays green)

#### T1 — Move `FuelService` into `gangland-item`  (group A, ~10 files)

- **Do:**
  1. `git mv gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/fuel/FuelService.java gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/fuel/FuelService.java`
     and delete the now-empty `gadget/fuel/` directory.
  2. Change its package to `org.luckyraven.gangland.item.fuel`; delete the now-redundant imports of
     `org.luckyraven.gangland.item.fuel.Fuel` and `...FuelContract` (same package now); keep
     `implements FuelContract`.
  3. Rewrite the stale class javadoc (`FuelService.java:22-25`) — it claims to implement a contract named
     `org.luckyraven.gangland.item.fuel.FuelService`, which is now this class's own name. Say instead: *"Lives in
     gangland-item, not in a feature module: the fuel HUD listeners in this module and the core's `UniqueItemAddon`
     need the registry regardless of which gadget module is installed."*
  4. Update the two stale javadocs that name the old FQN:
     `gangland-infra/.../item/fuel/FuelContract.java:9` and
     `gangland-infra/.../item/contract/UniqueItemRegistry.java:11`.
  5. Swap the import in every referencing file (`gadget.fuel.FuelService` → `item.fuel.FuelService`):
     `gangland-impl/.../config/FileConfig.java:20`, `gangland-impl/.../config/GadgetConfig.java` (locate the
     import by symbol — flip 1 renamed this file from `CopsAndGadgetsConfig.java`),
     `gangland-impl/.../item/configuration/UniqueItemAddon.java:11`,
     `gangland-gadget/.../gadget/car/CarService.java:27`,
     `gangland-gadget/.../gadget/car/vehicle/VehicleSession.java:20`,
     `gangland-gadget/.../gadget/jetpack/JetpackService.java:10`,
     `gangland-gadget/.../gadget/jetpack/JetpackTask.java:13`,
     `gangland-gadget/.../gadget/listener/car/CarInteractListener.java:22`,
     `gangland-gadget/src/test/.../gadget/jetpack/JetpackTaskConsumptionRateTest.java:6`.
- **Why:** `FuelContract`'s only implementation must stay in the core jar — two core listeners in `gangland-item`
  are `@AutowireTarget({FuelContract.class})` and would get no bean on a server without the gadget module.
- **Done when:** `grep -rn "gadget\.fuel" --include=*.java . | grep -v target | grep -v worktrees` is empty and
  `mvn -q clean install -DskipTests` is green.
- **Watch out:** `gangland-item` must not gain a dependency — `FuelService` uses only Bukkit + `Fuel`/`FuelContract`,
  all already available there. Do **not** delete `FuelContract`: it stays as the test seam (rule 4 in
  `documentation/module-loader.md`).

#### T2 — Move `WearableAddon` into `gangland-weapon`  (group A, ~11 files)

- **Do:**
  1. `git mv gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/wearable/WearableAddon.java gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/wearable/WearableAddon.java`
     and delete the empty `gadget/wearable/` directory.
  2. Package → `org.luckyraven.gangland.weapon.wearable`; drop the now-redundant
     `import org.luckyraven.gangland.weapon.wearable.WearableService;`.
  3. Swap the import in: `gangland-impl/.../config/FileConfig.java:21`,
     `gangland-impl/.../config/GameplayConfig.java:28`, `gangland-impl/.../config/GadgetConfig.java` (locate the
     import by symbol — flip 1 renamed this file from `CopsAndGadgetsConfig.java`),
     `gangland-impl/.../command/sub/item/ItemCommand.java:13`,
     `gangland-impl/.../command/sub/item/wearable/ItemWearableCommand.java:11`,
     `.../ItemWearableGiveCommand.java:14`, `.../ItemWearableInfoCommand.java:13`,
     `.../ItemWearableListCommand.java:10`, and
     `gangland-gadget/.../gadget/jetpack/JetpackService.java` (gadget → weapon is legal; gadget already declares
     `gangland-weapon` at provided scope, `gangland-features/gangland-gadget/pom.xml:46-50`).
  4. `gangland-features/gangland-weapon/pom.xml`: add whatever the compiler now demands for `WearableAddon` —
     expect `com.github.cryptomorin:XSeries`, `org.luckyraven:keystone-persistence` (FileHandler / FileInitializer /
     FileManager) and `org.luckyraven:keystone-common` (`Placeholder`, `SoundEffect`). Do **not** add versions or
     scopes; the root `pom.xml` dependencyManagement already pins them (keystone-\* are `provided` at
     `pom.xml:164-206`).
  5. Leave `gangland-impl/src/main/resources/items/wearables.yml` and its
     `KernelConfig.fileManager()` registration (`KernelConfig.java:175`) exactly where they are — flip 4 moves them.
- **Why:** `WearableAddon extends` weapon's `WearableService`, and every core consumer of it is wearable-shaped. It
  is the `WearableService` / `WearableEquipService` bean the core needs whether or not gadget is installed.
- **Done when:** `grep -rn "gadget\.wearable" --include=*.java . | grep -v target | grep -v worktrees` is empty and
  `mvn -q clean install -DskipTests` is green.
- **Watch out:** `GameplayConfig.java:213` (`wearableService`) and `:308` (`wearableEquipService`) simply return the
  `WearableAddon` bean — they keep working, only the import changes. Do not "simplify" them away.

> **Compile gate A:** `mvn clean install -DskipTests -q` green for the whole reactor, then
> `mvn -q test` green. Neither relocation changes behaviour.

---

### Group B — core seams (gadget is still compiled into the core, so the reactor stays green)

> **Watch out for the whole group:** merge `0.8.3` (the `p0-wave-3` work) into `0.8.4` before starting. T4 edits
> `GameplayConfig.java` and the `sign/` tree, both of which `p0-wave-3` touches.

#### T3 — `ItemSerializerRegistry` priority overload  (group B, 3 files)

- **Do:**
  1. Write `gangland-infra/gangland-item/src/test/java/org/luckyraven/gangland/item/ItemSerializerRegistryPriorityTest.java`
     first, using the *intended* 3-arg API: register the catch-all with
     `ItemSerializerRegistry.CATCH_ALL_PRIORITY`, then register a specific serializer with the 2-arg overload
     **after** it, and assert `serialize(stack)` returns the specific serializer's `kind:value`. Add a second test
     asserting that two default-priority serializers registered in order A then B keep A's precedence (stability).
     Run it and capture the failure verbatim (it will be a compilation failure — the overload does not exist yet;
     record that output as the red state, see §3).
  2. Add `CATCH_ALL_PRIORITY`, the 3-arg `register` overload, the `priority` component on the private `Entry`
     record and the stable `entries.sort(...)` shown in §1.6(b) to
     `gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/ItemSerializerRegistry.java`.
     Update the class javadoc (`:10-18`): registration order is priority order *within a priority tier*, and the
     catch-all opts out with `CATCH_ALL_PRIORITY` so late (module) registrations still win.
  3. `gangland-impl/.../config/ItemConfig.java:151`: change the `MATERIAL` line to
     `registry.register(ItemPredicates.MATERIAL, materialItemSerializer, ItemSerializerRegistry.CATCH_ALL_PRIORITY);`
     and extend the comment at `:142-144` to explain why.
- **Why:** without it, any serializer a module registers lands after the `MATERIAL` catch-all and never fires.
- **Done when:** `mvn -q -pl gangland-infra/gangland-item -am test` green and
  `ItemSerializerRegistryPriorityTest` passes.
- **Watch out:** `List.sort` must be used (stable) — not a `TreeSet` or a `PriorityQueue`, which would scramble the
  UNIQUE-before-WEAPON precedence that `ItemConfig`'s comment depends on.

#### T4 — Sign extension seam  (group B, 7 files)

- **Do:**
  1. Create `gangland-impl/src/main/java/org/luckyraven/gangland/sign/extension/` with `SignTypeContribution.java`,
     `SignViewProvider.java`, `SignContributions.java` exactly as sketched in §1.6(c)–(e). Copy the doc tone of
     `command/extension/CommandContribution.java` and `CommandContributions.java`.
  2. `gangland-impl/.../sign/SignManager.java`: delete the `CarManager` field (:57), its constructor parameter
     (:69) and assignment (:81), the `gadget.car.CarManager` import (:8) and the `CarBuySign`/`CarSellSign`
     imports (:23,24); delete the "car buy" (:192-199) and "car sell" (:201-208) blocks. Add a
     `private final DependencyContainer container;` field + constructor parameter.

     **Resolve the contributions once, at the very top of `setupSigns()`** — immediately after
     `List<SignTypeDefinition> definitions = new ArrayList<>();` and `String signPrefix = shortPrefix + "-";`
     (`SignManager.java:86-88`), i.e. *before* any `Sign` is constructed:

     ```java
     // Resolved here, not in the constructor: this bean is built in the CONFIG phase, but setupSigns() runs in
     // Keystone's convention initialize() pass, the first moment every module bean is guaranteed to exist.
     SignContributions contributions = SignContributions.from(container);
     ```

     That single local serves **both** call sites: it is passed into `new ViewSign(...)` mid-method
     (`SignManager.java:147` today — see step 3), and it drives the contributed-signs loop appended at the end of
     the method, just before `return definitions;`:

     ```java
     for (Sign contributed : contributions.createSigns(signPrefix)) {
         formatRegistry.register(contributed.createFormat());
         definitions.add(contributed.createDefinition());
     }
     ```

     Do **not** declare a second `SignContributions.from(container)` next to the loop — one local, used twice.
     Add a class javadoc paragraph saying the same thing in prose.
  3. `gangland-impl/.../sign/type/ViewSign.java`: replace the `CarManager carManager` field (:32) with
     `SignContributions contributions`, drop the gadget import (:6), and pass `contributions` to
     `ViewInventoryAspect` (:44-45). The `new ViewSign(...)` call site in `SignManager.setupSigns()`
     (`SignManager.java:147`) swaps its `carManager` argument for the `contributions` local declared in step 2 —
     which is why that local must be hoisted to the top of the method rather than created next to the loop.
  4. `gangland-impl/.../sign/aspect/ViewInventoryAspect.java`: drop the `Car`/`CarManager` imports (:10,11) and the
     `carManager` field (:38); add `private final SignContributions contributions;`. Delete `findCar` (:293-295)
     and `openCarView` (:209-236). Replace the car branch in `execute` (:67-72) with

     ```java
     if (contributions.openView(player, itemName)) {
         return AspectResult.success("Opened view: " + itemName);
     }
     ```

     keeping it in the same position (after wearable, before the generic fallback) so behaviour is unchanged.
     `canExecute` (:79-95) is untouched.
  5. `gangland-impl/.../config/GameplayConfig.java:259-269` (`signManager`): remove the `CarAddon carAddon`
     parameter and the last constructor argument, add `DependencyContainer container` as a parameter and pass it
     through, and **delete the `manager.initialize();` line** (the convention pass already calls it — see §0 risk 1).
     Extend the method javadoc to say why initialisation is deferred.
  6. Write `gangland-impl/src/test/java/org/luckyraven/gangland/sign/extension/SignContributionsTest.java`:
     `createSigns` returns contributions in registration order; `openView` returns `true` on the first provider that
     claims the name and never consults later providers; `none()` is inert.
- **Why:** the core's sign catalogue and the `view` sign must survive with zero modules, while gadget (and, at
  flip 4, weapon) add rows to both.
- **Done when:** `mvn -q -pl gangland-impl -am test` green; `SignContributionsTest` passes;
  `grep -n "initialize()" gangland-impl/src/main/java/org/luckyraven/gangland/config/GameplayConfig.java` no longer
  shows a `signManager`-scoped call.
- **Watch out:** (a) **p0-wave-3 overlap** — `GameplayConfig.java`, `sign/GanglandSignInformation.java` and
  sign-api's `SignCreation`/`SignInformation` are all being edited there; merge first.
  (b) `SignManager` must resolve contributions **lazily**; a `SignContributions.from(container)` in the constructor
  silently returns an empty list.
  (c) `SignTypeRegistry.register` and `SignFormatRegistry.register` are `Map.put`s
  (`sign-api/.../SignTypeRegistry.java:15-23`, `SignFormatRegistry.java:18-24`), so today's accidental double
  `initialize()` is idempotent — removing the explicit call is safe, and the executor should say so in the status
  table rather than "fixing" anything else.

#### T5 — Split the jetpack death hook out of the core listener  (group B, 3 files)

- **Do:**
  1. `gangland-features/gangland-gadget/.../gadget/listener/jetpack/JetpackSessionLifecycleListener.java`: add

     ```java
     @EventHandler(priority = EventPriority.MONITOR)
     public void onDowned(PlayerDownedEvent event) {
         jetpackService.deactivate(event.getPlayer());
     }
     ```

     beside the existing `onUndowned(PlayerUndownedEvent)` (:42-45); import
     `org.luckyraven.gangland.core.downed.PlayerDownedEvent`.
  2. `gangland-impl/.../listener/player/CustomPlayerDeathListener.java`: delete the `JetpackService` import (:38),
     the field (:71), the constructor parameter (:78) and assignment (:82), and the
     `if (jetpackService != null) jetpackService.deactivate(player);` line (:211).
  3. `gangland-impl/src/test/.../listener/player/CustomPlayerDeathListenerQuitTest.java:64-71`: drop the trailing
     `null` argument and the four-line comment about Netty.
- **Why:** the only core→gadget edge in the listener tree; `enterDownedState` already fires `PlayerDownedEvent`
  (`CustomPlayerDeathListener.java:213`) immediately after the deactivate call, so the module can listen for it.
- **Done when:** `mvn -q -pl gangland-impl,gangland-features/gangland-gadget -am test` green and
  `grep -n "jetpack" gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/CustomPlayerDeathListener.java`
  is empty (case-insensitive).
- **Watch out:** the deactivate now runs *after* `DownedPlayerRegistry.add` and `savedGameModes.put`, but still
  **before** `player.setGameMode(...)` / `setAllowFlight(true)` (`:216-224`) — so the "downed players may fly"
  setting still wins, exactly as today. `PlayerDownedEvent` lives in `gangland-core`, which gadget already depends
  on. Record this ordering note in the status table so the reviewer can check it.

> **Compile gate B:** `mvn clean install -DskipTests -q` green, then `mvn test` green (full suite).

---

### Group C — poms and module entry (this is where impl stops compiling)

#### T6 — Poms  (group C, 3 files)

- **Do:**
  1. `gangland-impl/pom.xml`: delete the `gangland-gadget` dependency block. **Locate it by `<artifactId>`, not by
     line** — it sits at `:99-102` at `d6bb33ac`, but flip 1's T1 deletes the `cops-n-crooks` block above it
     (`:89`) first, so the numbering has shifted by the time this task runs.
  2. `gangland-features/gangland-gadget/pom.xml`:
     - add `org.luckyraven:gangland-impl` at `<scope>provided</scope>` with the same comment block mail uses
       (`gangland-features/gangland-mail/pom.xml:66-74`);
     - add `org.luckyraven:keystone-module` and `org.luckyraven:keystone-command` (no version, no scope — the root
       dependencyManagement pins both at `provided`, `pom.xml:191-206`);
     - leave `gangland-weapon` at `provided` — it is still in the core jar this flip (rule 9);
     - delete the unused `net.wesjd:anvilgui` dependency: no gadget source imports `net.wesjd`, and the core jar
       relocates it to `org.luckyraven.gangland.dependency.anvilgui` (`gangland-build/pom.xml:37-40`), so compiling
       against the unrelocated package would break at runtime if anyone ever used it.
  3. `gangland-build/pom.xml`: add `<exclude>org.luckyraven:gangland-gadget</exclude>` beside the mail one (`:80`);
     add a second `<artifactItem>` for `gangland-gadget` in the `copy-runtime-modules` execution (`:111-117`); add
     `org.luckyraven:gangland-gadget` at `<scope>provided</scope>` to `<dependencies>` beside the mail entry
     (`:128-135`).
- **Why:** removing the impl dependency is what turns the compiler into the work list (master item M1).
- **Done when:** `mvn clean install -DskipTests 2>&1 | grep -E "^\[ERROR\].*\.java"` lists **only** files under
  `gangland-impl/src/main/java` — and exactly the 24 files from §1.1 whose "after A?" column says *yes*. Save that
  list; it is the checklist for groups D1/D2.
- **Watch out:** do not touch root `pom.xml:267` (dependencyManagement entry stays) or
  `gangland-features/pom.xml:21` (reactor member stays).

#### T7 — Module entry  (group C, 4 files)

- **Do:**
  1. `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetModule.java` —
     `@CustomLog public final class GadgetModule implements KeystoneModule`, modelled literally on
     `gangland-features/gangland-mail/.../MailModule.java`:

     ```java
     public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.gadget.listener";
     public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.gadget.command";
     public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.gadget.database";

     @Override
     public void configure(ModuleRegistrar registrar) {
         registrar.configuration(GadgetFileConfig.class)
                  .configuration(GadgetModuleConfig.class)
                  .listenerPackage(LISTENER_PACKAGE)
                  .commandPackage(COMMAND_PACKAGE)
                  .repositoryPackage(REPOSITORY_PACKAGE);
     }
     ```

     plus `onEnabled(ModuleContext)` / `onDisabled()` logging exactly like `MailModule:34-42`.
  2. `gangland-features/gangland-gadget/src/main/resources/module.yml` (jar root, block-style YAML,
     `Capitalized_Underscore_Separated`):

     ```yaml
     # Keystone module descriptor - read by Gangland's ModuleLoader from plugins/Gangland_Warfare/modules/.
     Id: gadget
     Name: Gangland Gadgets
     Version: ${project.version}
     Main: org.luckyraven.gangland.gadget.GadgetModule
     Host_Api: 0.8
     Artifact: org.luckyraven:gangland-gadget
     ```

     **No `Depends:` key** — weapon and turf are still in the core jar at this flip; flip 4 adds `Depends: [weapon]`.
  3. `gangland-features/gangland-gadget/src/main/resources/commands.json` — start it as `{}`; T8 fills it.
  4. Verify `src/main/resources/org/luckyraven/gangland/gadget/module.properties` already exists with
     `module.name=${project.name}` (it does). Confirm the module's `pom.xml` inherits resource filtering the same
     way mail's does — if `${project.version}` is not substituted in the built jar, copy mail's build config.
- **Why:** master item M2; the module must declare itself before anything can be moved into it.
- **Done when:** `GadgetModule` compiles (`mvn -q -pl gangland-features/gangland-gadget install -DskipTests` will
  still fail on the two not-yet-created config classes — that is expected; write empty `@Configuration` shells for
  `GadgetFileConfig` and `GadgetModuleConfig` in this task so it compiles).
- **Watch out:** the two configuration shells must carry `@Configuration` from `org.luckyraven.keystone.bean`, and
  `GadgetFileConfig` must be `@Configuration(phase = Phase.FILE)` (see T10).

> **Compile gate C:** `mvn clean install -DskipTests` fails **only** in `gangland-impl`, and
> `mvn -q -pl gangland-features/gangland-gadget install -DskipTests` alone is green once impl is installed from the
> previous commit — if it is not, note the exact error and continue; groups D1/D2 fix it.

---

### Group D1 — commands, persistence, config beans  (~17 files)

#### T8 — Car commands + `commands.json`  (group D1, 7 files)

- **Do:**
  1. `git mv` the four files in `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/car/` to
     `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/command/`; delete the empty
     source directory. Change every package declaration to `org.luckyraven.gangland.gadget.command`. Add imports
     for the core types they now reach across a jar boundary (`org.luckyraven.gangland.Gangland`,
     `org.luckyraven.gangland.command.Command`, `org.luckyraven.gangland.file.configuration.Messages`,
     `org.luckyraven.gangland.gang.user.{User,UserManager}`, `org.luckyraven.gangland.util.GanglandChatUtil`).
     `CarCommand` keeps `@CommandHandler` and its `super(gangland, "car", true, "cars")` call unchanged.
  2. Cut the 5 keys `car`, `car_help`, `car_give`, `car_info`, `car_list` from
     `gangland-impl/src/main/resources/commands.json` (`:566-585`) into
     `gangland-features/gangland-gadget/src/main/resources/commands.json`, verbatim (usage + description strings
     unchanged). Keep the `fuel*` and `item_wearable*` keys in core.
  3. `gangland-impl/src/test/.../command/data/InformationManagerTest.java:41`: change the asserted count to
     *(current value) − 5*.
- **Why:** master item M7. `CarCommand` is a top-level `/glw car`, so it goes through
  `registrar.commandPackage(...)` (`GanglandContext.runCommandPhase`, `GanglandContext.java:260-264`), not through
  a `CommandContribution`.
- **Done when:** `python -c "import json;print(len(json.load(open('gangland-impl/src/main/resources/commands.json'))))"`
  equals the new asserted number, the module's `commands.json` has exactly 5 keys, and
  `grep -rn "command/sub/car" gangland-impl` is empty.
- **Watch out:** `CarCommand`'s constructor filters `getCommands()` by the `"car"` prefix
  (`CarCommand.java:29-36`) — that reads the shared `InformationManager`, which `KernelConfig.informationManager`
  merges each module's `commands.json` into (`KernelConfig.java:62-68`). The help page therefore works only if the
  keys really live in the **module** jar. Also: `CarGiveCommand` and friends are package-private (`class`, not
  `public class`) and stay that way — they are constructed from `CarCommand` in the same new package.

#### T9 — Persistence  (group D1, 2 files)

- **Do:** `git mv gangland-impl/.../database/repositories/car/ParkedCarRepository.java` and
  `gangland-impl/.../database/tables/car/ParkedCarTable.java` into
  `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/database/`; set package
  `org.luckyraven.gangland.gadget.database`; fix `ParkedCarRepository`'s import of `ParkedCarTable` (same package
  now, delete the import). Delete the two empty `car/` directories under `database/repositories` and
  `database/tables`.
- **Why:** master item M5. `GadgetModule.REPOSITORY_PACKAGE` is already declared; `DatabaseConfig` scans every
  module's repository packages through the module classloader (`DatabaseConfig.java:84-89`) with no core edit.
- **Done when:** `mvn -q -pl gangland-features/gangland-gadget install -DskipTests` compiles the two classes and
  `grep -rn "database/\(repositories\|tables\)/car" gangland-impl` is empty.
- **Watch out:** `setDataSupplier` is wired by `CarService.initialize()`
  (`gadget/car/CarService.java:104`) — memory rule `feedback_repository_data_supplier`. It is already inside the
  module; do not add a second call in the config.

#### T10 — Contract impls + the module's config classes  (group D1, 8 files)

- **Do:**
  1. `git mv gangland-impl/.../file/configuration/GadgetPhysicsConfigImpl.java` and
     `gangland-impl/.../file/configuration/gadget/GanglandCarMessages.java` into
     `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/contract/`; package
     `org.luckyraven.gangland.gadget.contract`; add
     `import org.luckyraven.gangland.file.configuration.{Settings,Messages};`. Delete the now-empty
     `file/configuration/gadget/` directory.
  2. `git mv gangland-impl/src/main/java/org/luckyraven/gangland/gadget/GanglandCarGangs.java` (arrives with
     `p0-wave-3`) into
     `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/car/access/GanglandCarGangs.java`;
     package `org.luckyraven.gangland.gadget.car.access`; drop the `CarGangContract` import (same package now); keep
     the `MemberManager` import. **Delete the whole `gangland-impl/src/main/java/org/luckyraven/gangland/gadget/`
     directory** — a leftover class there is a split package with the module root and the parent-first
     `ModuleClassLoader` would shadow the module copy.
  3. Fill `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetFileConfig.java`
     (`@CustomLog @Configuration(phase = Phase.FILE)`, constructor takes `Gangland gangland`):

     ```java
     @Bean
     public GadgetPhysicsConfig gadgetPhysicsConfig(Settings settings) {
         return new GadgetPhysicsConfigImpl();
     }

     @Bean
     public CarAddon carAddon(PermissionManager permissionManager, FileManager fileManager,
                              PlaceholderService placeholderService, ModuleLoader moduleLoader) {
         fileManager.addFile(new FileHandler(gangland, "cars", "items", ".yml", moduleLoader.classLoader()), true);

         CarAddon addon = new CarAddon(permissionManager::addPermission, fileManager, placeholderService);
         fileManager.registerInitializer(addon);
         return addon;
     }
     ```

     Keep the `Settings settings` parameter on `gadgetPhysicsConfig` even though the body ignores it — it is the
     FILE-phase ordering edge (memory rule "bean ordering via params"; the same convention is documented at
     `FileConfig.java:44-47`).
  4. Fill `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetModuleConfig.java`
     (`@CustomLog @Configuration`, constructor takes `Gangland gangland`) with the four beans moved out of
     `config/GadgetConfig.java` — `carMessageContract`, `carAccessPolicy`, `carService`, `jetpackService` —
     copied **verbatim**, parameter lists included (`carService` keeps `carService.reloadParkedVehicles();`,
     `carAccessPolicy` keeps `permissionManager.addPermission(CarAccessPolicy.BYPASS_PERMISSION);`).
  5. `gangland-impl/.../config/FileConfig.java`: delete `gadgetPhysicsConfig` (:141-144), `carAddon` (:205-212) and
     the two now-unused imports (:18,19).
  6. `gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java`: delete the four gadget beans
     and every gadget import; the file is then empty — **delete it**. (Pre-check before you start: confirm the file
     exists under that name and holds exactly those four `@Bean` methods; flip 1's T14 renamed it from
     `CopsAndGadgetsConfig.java`. If anything else survives in it, keep the file, delete only the four beans, and
     record what stayed in §7.)
  7. `gangland-impl/.../config/KernelConfig.java:176`: delete the `cars` `FileHandler` registration.
- **Why:** master items M3 + M4 + M8's core half.
- **Done when:** `grep -rn "gadget" gangland-impl/src/main/java/org/luckyraven/gangland/config/` is empty and
  `ls gangland-impl/src/main/java/org/luckyraven/gangland/gadget` reports "No such file or directory".
- **Watch out:** (a) **p0-wave-3 overlap** on `config/GadgetConfig.java` (which `p0-wave-3` still knows as
  `CopsAndGadgetsConfig.java`) and on `GanglandCarGangs.java` — merge first, then re-read the file before moving
  beans, because `carAccessPolicy` only exists after the merge.
  (b) `GadgetFileConfig` **must** be `Phase.FILE`: `CarAddon` is a `FileInitializer`, and only the FILE-phase hook
  (`GanglandContext.java:167-172`) runs `FileManager.initializeAll()` between beans so `cars.yml` is loaded before
  the next FILE bean reads it. A CONFIG-phase config would leave the addon un-initialised.
  (c) `ModuleLoader` is available as a container bean — `GanglandContext` registers it explicitly
  (`GanglandContext.java:110`).
  (d) `FileManager.addFile(handler, true)` immediately calls `create(true)`
  (`Keystone/keystone-persistence/.../FileManager.java:36-47`), which copies the default out of the module jar and
  marks the handler loaded, so `CarAddon`'s constructor `checkFileLoaded("cars")` (`CarAddon.java:42-49`) succeeds.

> **Compile gate D1:** `mvn clean install -DskipTests 2>&1 | grep -E "^\[ERROR\].*\.java"` must now list **only**
> the 8 remaining files: `item/ItemPredicates.java`, `item/converter/CarConverter.java`,
> `item/refresher/CarItemRefresher.java`, `item/serializer/CarItemSerializer.java`, `config/ItemConfig.java`,
> `sign/SignManager.java`, `sign/type/trade/car/CarBuySign.java`, `sign/type/trade/car/CarSellSign.java`,
> `sign/validation/trade/car/CarSignValidator.java`. Paste the list into the status table.

---

### Group D2 — items, signs, YAML  (~19 files)

#### T11 — Item converter / serializer / refresher  (group D2, 8 files)

- **Do:**
  1. `git mv` `gangland-impl/.../item/converter/CarConverter.java`,
     `gangland-impl/.../item/refresher/CarItemRefresher.java`,
     `gangland-impl/.../item/serializer/CarItemSerializer.java` into
     `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/item/`; package
     `org.luckyraven.gangland.gadget.item`; add imports for `org.luckyraven.gangland.item.{ItemAttributes,ItemKind,
     ItemRefresher,ItemSerializer}`.
  2. Create `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/item/GadgetItemPredicates.java`
     — a final utility class with a private constructor holding
     `public static final Predicate<ItemStack> CAR = stack -> stack != null && new ItemBuilder(stack).hasNBTTag(CarKey.CAR_ID.getKey());`
     (the body of `ItemPredicates.CAR` + `hasTag`, `ItemPredicates.java:32,43-48`).
  3. `gangland-impl/.../item/ItemPredicates.java`: delete the `CAR` constant (:32) and the `gadget.car.CarKey`
     import (:6). Update the class javadoc (:21-23) — it names `CarKey` as the reason the class lives in impl; the
     remaining reasons are `Weapon`/`Ammunition`/`Wearable`.
  4. `gangland-impl/.../config/ItemConfig.java`: delete the `carConverter` (:52-55), `carItemSerializer`
     (:123-126) and `carItemRefresher` (:177-180) beans, the `CarAddon` import (:5), the `carConverter` parameter
     and `registry.register(ItemKind.CAR, carConverter)` line from `itemConverterRegistry` (:72,83), the
     `carItemSerializer` parameter and `registry.register(ItemPredicates.CAR, carItemSerializer)` line from
     `itemSerializerRegistry` (:138,149), and the `carItemRefresher` parameter and argument from
     `itemRefresherRegistry` (:187,190).
  5. `GadgetModuleConfig`: add the three registration beans (this is the shared mechanism — **converge with the
     weapon plan**):

     ```java
     @Bean
     public CarConverter carConverter(CarAddon carAddon, ItemConverterRegistry itemConverterRegistry) {
         CarConverter converter = new CarConverter(carAddon);
         itemConverterRegistry.register(ItemKind.CAR, converter);
         return converter;
     }

     @Bean
     public CarItemSerializer carItemSerializer(ItemSerializerRegistry itemSerializerRegistry) {
         CarItemSerializer serializer = new CarItemSerializer();
         itemSerializerRegistry.register(GadgetItemPredicates.CAR, serializer);
         return serializer;
     }

     @Bean
     public CarItemRefresher carItemRefresher(CarAddon carAddon, ItemRefresherRegistry itemRefresherRegistry) {
         CarItemRefresher refresher = new CarItemRefresher(carAddon);
         itemRefresherRegistry.register(refresher);
         return refresher;
     }
     ```

     Document in the config's class javadoc that the registry parameters are the ordering edges and that the
     serializer relies on T3's priority sort to outrank the core's `MATERIAL` catch-all.
- **Why:** master item M9; rule 3(a) — inject the existing registry, no new interface.
- **Done when:** `mvn clean install -DskipTests 2>&1 | grep -E "^\[ERROR\].*\.java"` no longer names any `item/`
  file, and `grep -rn "ItemKind.CAR\|ItemPredicates.CAR" gangland-impl` is empty.
- **Watch out:** (a) `ItemKind.CAR` stays in `gangland-item` (labels only) — do not move it.
  (b) `CarConverter`'s field type is `CarManager`, not `CarAddon` (`CarConverter.java:26`); `CarAddon extends
  CarManager` (`CarAddon.java:24`), so passing the `CarAddon` bean is correct and unchanged.
  (c) Ordering hazard, logged in §6/OQ-2: `GameplayConfig.lootChestLoader` resolves `loot_chests.yml` item strings
  inside its own CONFIG-phase `@Bean` body (`GameplayConfig.java:335-340`), i.e. possibly before the module's
  `carConverter` bean runs. No shipped YAML uses a `car:` ref, so leave the core alone and record it in triage.

#### T12 — Car signs  (group D2, 9 files)

- **Do:**
  1. `git mv` `gangland-impl/.../sign/type/trade/car/CarBuySign.java`,
     `.../CarSellSign.java`, `gangland-impl/.../sign/validation/trade/car/CarSignValidator.java` into
     `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/`; package
     `org.luckyraven.gangland.gadget.sign`; keep every `org.luckyraven.gangland.sign.*` import (sign-api and the
     impl `sign` package both resolve at provided scope through `gangland-impl`), and add explicit imports for
     `org.luckyraven.gangland.sign.type.Sign`, `...type.trade.BaseTradeSign`,
     `...aspect.{ItemTransferAspect,MoneyAspect,SignAspect,AspectResult}`, `...parser.TradeSignParser`,
     `org.luckyraven.gangland.file.configuration.Settings` (used by `CarSignValidator:13`),
     `org.luckyraven.gangland.gang.user.UserManager`. Delete the emptied `sign/type/trade/car/` and
     `sign/validation/trade/car/` directories.
  2. Create `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/CarSignContribution.java`
     implementing `SignTypeContribution`: constructor takes `UserManager<Player>`, `CarManager`, `WeaponService`,
     `AmmunitionManager`; `signs(String signPrefix)` rebuilds exactly what `SignManager.java:192-208` did —

     ```java
     SignType carBuyType  = new SignType(signPrefix + "car-buy",  "CAR-BUY");
     SignType carSellType = new SignType(signPrefix + "car-sell", "CAR-SELL");
     return List.of(new CarBuySign(userManager, carManager, weaponService, ammunitionManager, carBuyType),
                    new CarSellSign(userManager, carManager, weaponService, ammunitionManager, carSellType));
     ```
  3. Create `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/CarSignViewProvider.java`
     implementing `SignViewProvider`: constructor takes `JavaPlugin` and `CarManager`; `open(player, content)`
     returns `false` when `carManager.getCar(content) == null`, otherwise runs the body of the deleted
     `ViewInventoryAspect.openCarView` (`ViewInventoryAspect.java:209-236`) verbatim (it uses `InventoryHandler`,
     `Fill`, `InventoryUtil.fillInventory`, `Settings.getInventoryFillName/Item`, `ItemBuilder`) and returns `true`.
  4. `GadgetModuleConfig`: add two beans, one per concrete type (mirroring
     `MailModuleConfig.java:66-83`):

     ```java
     @Bean
     public CarSignContribution carSignContribution(@Qualifier("online") UserManager<Player> userManager,
                                                    CarAddon carAddon, WeaponService weaponService,
                                                    AmmunitionManager ammunitionManager) { … }

     @Bean
     public CarSignViewProvider carSignViewProvider(CarAddon carAddon) {
         return new CarSignViewProvider(gangland, carAddon);
     }
     ```
  5. Add `gangland-impl/src/test/java/org/luckyraven/gangland/sign/SignManagerContributionTest.java`: build a
     `SignManager` over a `DependencyContainer` that holds one stub `SignTypeContribution` returning a single fake
     `Sign`, call `setupSigns()`, then assert **containment, never a total count**: the contributed definition is
     present, and so are `glw-buy`, `glw-sell`, `glw-view`, `glw-wanted` and `glw-bounty`. Those five core signs
     survive every flip in the sprint; the total does not (this flip takes the two car rows out of today's 13, and
     flip 4 takes six more — weapon buy/sell, ammo buy/sell, wearable buy/sell — down to 5). A count assertion here
     would go red in flip 4's `mvn test` with no owning task.
- **Why:** master items M6/M9; the sign catalogue keeps its car rows only when the module is installed.
- **Done when:** `mvn clean install -DskipTests` is green for the whole reactor (this is gate G1) and
  `grep -rn "org\.luckyraven\.gangland\.gadget\." gangland-impl/src` is empty.
- **Watch out:** **p0-wave-3 overlap** — sign-api's `SignCreation`, `SignInformation`, the new `SignPermissions`
  and `SignProtection`, and `sign/GanglandSignInformation.java` are all being edited there. Merge `0.8.3` first and
  re-read `SignManager.setupSigns()` before deleting the car blocks: `p0-wave-3` may have added a permission check
  to the sign path that the contributed signs must keep honouring.

#### T13 — YAML  (group D2, 2 files)

- **Do:** `git mv gangland-impl/src/main/resources/items/cars.yml gangland-features/gangland-gadget/src/main/resources/items/cars.yml`
  (identical relative path — the module classloader resolves `items/cars.yml`). Confirm the core jar no longer has
  it. `items/{ammunition,unique_items,wearables,money}.yml` stay.
- **Why:** master item M8; the loader is parent-first so the same path must disappear from the core jar or the core
  copy shadows the module's.
- **Done when:** `ls gangland-impl/src/main/resources/items/` shows four files, and after `mvn clean package
  -DskipTests`, `unzip -l target/gangland_warfare-0.8.4.jar | grep items/cars.yml` is empty while
  `unzip -l target/modules/gangland-gadget-0.8.4.jar | grep items/cars.yml` has one line.
- **Watch out:** the file must sit under `src/main/resources/items/`, **not** `src/main/resources/gadget/` — the
  data-folder path (`plugins/Gangland_Warfare/items/cars.yml`) is what the `FileHandler` looks up, and existing
  servers already have the file there. This is the one place where the "module YAML nests under
  `resources/<module>/`" memory rule does not apply, because the path is pre-existing and shared with the other
  `items/*.yml`. Note it in `documentation/module-loader.md` (T15).

> **Compile gate D2 = gate G1:** `mvn clean install -DskipTests -q` green **and**
> `grep -rn "org\.luckyraven\.gangland\.gadget\." gangland-impl/src` empty.

---

### Group E — tests, docs, gates

#### T14 — Tests  (group E, 3 files)

- **Do:**
  1. `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/GadgetModuleTest.java`,
     modelled literally on `gangland-features/gangland-mail/src/test/.../MailModuleTest.java`: assert
     `registrations.configurations()` equals `List.of(GadgetFileConfig.class, GadgetModuleConfig.class)`,
     `listenerPackages()` equals `List.of(GadgetModule.LISTENER_PACKAGE)`, `commandPackages()` equals
     `List.of(GadgetModule.COMMAND_PACKAGE)`, `repositoryPackages()` equals
     `List.of(GadgetModule.REPOSITORY_PACKAGE)`; plus a second test asserting the three declared package strings
     equal `CarInteractListener.class.getPackageName()`'s parent-safe equivalents — concretely
     `JetpackSessionLifecycleListener.class.getPackageName()` starts with `LISTENER_PACKAGE`,
     `CarCommand.class.getPackageName()` equals `COMMAND_PACKAGE`, and
     `ParkedCarRepository.class.getPackageName()` equals `REPOSITORY_PACKAGE`.
  2. Verify the count edit from T8 landed: `InformationManagerTest:41`.
  3. Run the full suite and fix any test that referenced a moved symbol.
- **Done when:** `mvn test` green (gate G2).
- **Watch out:** never add a test dependency to `gangland-gadget/pom.xml` — JUnit and Mockito are inherited from
  the root pom; the existing `gangland-core` test-jar dependency (`pom.xml:88-93`) stays.

#### T15 — Docs  (group E, 4 files)

- **Do:**
  1. `CLAUDE.md`: in the module table (`:152`) mark `gangland-features/gangland-gadget` as a **runtime module**
     (`modules/gangland-gadget-<rev>.jar`, never in the core jar), in the wording used for `gangland-mail` at
     `:158`. Update the two-tier paragraph (`:171-173`): runtime modules are now `gangland-mail`, `cops-n-crooks`
     and `gangland-gadget`; the remaining flips are **turf → weapon**.
  2. `documentation/module-loader.md`: update the "What is a module today" table (`:25-28`), the tree at `:9-18`
     (add `gangland-gadget-0.8.4.jar`), and the remaining-flip sentence (`:30-33`). **Append rows to the existing
     `## Core seams` section** — flip 1's T19 creates that heading; do **not** add a second heading of your own —
     documenting `SignTypeContribution` / `SignViewProvider` / `SignContributions`, the
     inject-the-registry pattern for item converters / serializers / refreshers, and
     `ItemSerializerRegistry.CATCH_ALL_PRIORITY`. Add a sentence to the "Writing a module" YAML bullet (`:77-80`)
     noting the exception the `items/cars.yml` path establishes: a module's YAML lives at the **data-folder path**,
     which for pre-existing shared directories (`items/`, `npc/`, `turf/`, `weapon/`) is not `<module>/`.
  3. `documentation/README.md:59` — the Gadget System developer doc still describes gadget as part of the core;
     add a one-line note that it ships as a runtime module since 0.8.4.
  4. Memory: update `project_module_loader_plan.md` and `MEMORY.md`'s "Active Plans" bullet with flip 2 done and
     the two relocations (FuelService → gangland-item, WearableAddon → gangland-weapon).
- **Done when:** gate G4 — `grep -rn "gangland-gadget" CLAUDE.md documentation/` shows the module framing
  everywhere and no "still a compile-time dependency" wording remains for gadget.

#### T16 — Verification gates  (group E)

- Run §5 top to bottom, then `graphify update . --force`.

---

## 3. Tests

**Tests to move:** none. The single impl test touching the feature
(`CustomPlayerDeathListenerQuitTest`) tests a **core** listener and stays in core; T5 only removes its `null`
argument. The five existing gadget tests stay in the module (T1 changes one import in
`JetpackTaskConsumptionRateTest:6`).

**Tests to change:**

| test | change |
|---|---|
| `gangland-impl/src/test/.../command/data/InformationManagerTest.java:41` | asserted count → *(value present when the executor starts) − 5*. It is 225 today; flip 1 will have lowered it first. |
| `gangland-impl/src/test/.../listener/player/CustomPlayerDeathListenerQuitTest.java:64-71` | drop the trailing `null` constructor argument and the Netty comment |
| `gangland-features/gangland-gadget/src/test/.../gadget/jetpack/JetpackTaskConsumptionRateTest.java:6` | import `org.luckyraven.gangland.item.fuel.FuelService` |

**New tests and how to show each red first:**

1. **`ItemSerializerRegistryPriorityTest`** (`gangland-infra/gangland-item/src/test/...`) — asserts a serializer
   registered *after* a `CATCH_ALL_PRIORITY` catch-all still wins, and that two same-priority registrations keep
   insertion precedence. *Red:* write and run it **before** touching `ItemSerializerRegistry`; the run fails to
   compile because `CATCH_ALL_PRIORITY` and the 3-arg `register` do not exist. Paste that compiler output into the
   status table as the red evidence, then add the API. (There is no way to express this assertion against the
   2-arg-only API — with a single priority tier the catch-all necessarily wins — so a compile-failure red is the
   honest best available; say so in the status table.)
2. **`SignContributionsTest`** (`gangland-impl/src/test/.../sign/extension/`) — order preservation for
   `createSigns`, first-wins short-circuit for `openView`, `none()` inert. *Red:* create
   `SignContributions.java` as a stub whose `createSigns` returns `List.of()` and whose `openView` returns
   `false`, run the test (fails on both assertions), then implement.
3. **`SignManagerContributionTest`** (`gangland-impl/src/test/.../sign/`) — a stub `SignTypeContribution` in the
   container is appended to `setupSigns()`'s definitions, and the flip-stable core signs (`glw-buy`, `glw-sell`,
   `glw-view`, `glw-wanted`, `glw-bounty`) are still present. **Containment assertions only — never a total
   count**, because flip 4 removes six more core definitions and would turn a count assertion red with no owning
   task. *Red:* add the seam interfaces and the test **before** editing `SignManager` — the test compiles and fails
   because `setupSigns()` ignores the container. Then wire `SignManager`.
4. **`GadgetModuleTest`** (`gangland-features/gangland-gadget/src/test/...`) — declaration test, mirroring
   `MailModuleTest`. *Red:* it cannot be red before `GadgetModule` exists; write it immediately after T7 and note
   in the status table that it is a declaration guard, not a behaviour test (same status `MailModuleTest` has).

The core must still boot with **zero** modules: after T14, confirm no existing `GanglandContext`/bootstrap test
regressed and that `SignContributions.none()` / an empty `getAllInstances` path is exercised by
`SignContributionsTest`.

---

## 4. Docs and config

| file | exact edit |
|---|---|
| `CLAUDE.md:152` | gadget row → runtime module (`modules/gangland-gadget-<rev>.jar`) |
| `CLAUDE.md:171-173` | runtime modules = mail, cops-n-crooks, gadget; remaining flips **turf → weapon** |
| `documentation/module-loader.md:9-18` | add `gangland-gadget-0.8.4.jar` to the folder tree |
| `documentation/module-loader.md:25-28` | move gadget into the "is a module" row |
| `documentation/module-loader.md:30-33` | remaining incremental order is turf → weapon |
| `documentation/module-loader.md:77-80` | note the data-folder-path rule and the `items/cars.yml` case |
| `documentation/module-loader.md` — **append to the existing `## Core seams` section** (created by flip 1's T19; no new heading) | `SignTypeContribution`, `SignViewProvider`, `SignContributions`, the inject-the-registry item pattern, `ItemSerializerRegistry.CATCH_ALL_PRIORITY` |
| `documentation/README.md:59` | Gadget System doc is now a runtime module |
| `gangland-features/gangland-gadget/src/main/resources/module.yml` | new — `Id: gadget`, `Main: org.luckyraven.gangland.gadget.GadgetModule`, `Host_Api: 0.8`, `Artifact: org.luckyraven:gangland-gadget`, no `Depends` |
| `gangland-features/gangland-gadget/src/main/resources/commands.json` | new — the 5 `car*` keys, verbatim from `gangland-impl/src/main/resources/commands.json:566-585` |
| `gangland-impl/src/main/resources/commands.json` | those 5 keys deleted |
| `gangland-build/pom.xml:80` | `<exclude>org.luckyraven:gangland-gadget</exclude>` |
| `gangland-build/pom.xml:111-117` | second `<artifactItem>` for `gangland-gadget` |
| `gangland-build/pom.xml:128-135` | `gangland-gadget` dependency at `provided` |
| `gangland-impl/pom.xml` | `gangland-gadget` dependency deleted (locate by `<artifactId>`; flip 1 already shifted the line numbers) |
| `gangland-features/gangland-gadget/pom.xml` | `+gangland-impl(provided)`, `+keystone-module`, `+keystone-command`, `−anvilgui` |
| `gangland-features/gangland-weapon/pom.xml` | whatever `WearableAddon` needs (expect XSeries, keystone-persistence, keystone-common) |
| memory `project_module_loader_plan.md`, `MEMORY.md` | flip 2 done; the two relocations recorded |

---

## 5. Verification (gates G1–G4 instantiated)

```bash
# G0 — the p0-wave-3 work must be in before group B
git log --oneline 0.8.3 -1
git merge-base --is-ancestor 0.8.3 HEAD && echo "0.8.3 merged"

# G1 — compile + zero feature imports left in impl
mvn clean install -DskipTests -q
grep -rn "org\.luckyraven\.gangland\.gadget\." gangland-impl/src        # expect: no output
grep -rn "gadget\.fuel\|gadget\.wearable" --include=*.java . \
  | grep -v target | grep -v worktrees                                   # expect: no output

# G2 — full suite, counts adjusted
mvn test
#   InformationManagerTest asserts (value at start of flip) - 5
#   new: ItemSerializerRegistryPriorityTest, SignContributionsTest,
#        SignManagerContributionTest, GadgetModuleTest

# G3 — jars
mvn clean package -DskipTests
unzip -l target/gangland_warfare-0.8.4.jar | grep "org/luckyraven/gangland/gadget"   # expect: no output
unzip -l target/gangland_warfare-0.8.4.jar | grep "items/cars.yml"                   # expect: no output
unzip -l target/modules/gangland-gadget-0.8.4.jar | grep -E "module.yml|commands.json|items/cars.yml"
#   expect three lines
unzip -p target/modules/gangland-gadget-0.8.4.jar module.yml                          # Id: gadget, no Depends
unzip -p target/modules/gangland-gadget-0.8.4.jar commands.json | python -c \
  "import json,sys;print(sorted(json.load(sys.stdin)))"
#   expect ['car', 'car_give', 'car_help', 'car_info', 'car_list']

# G4 — docs
grep -rn "gangland-gadget" CLAUDE.md documentation/
grep -rn "SignTypeContribution\|SignViewProvider\|CATCH_ALL_PRIORITY" documentation/module-loader.md
grep -c "^## Core seams" documentation/module-loader.md          # expect: 1 (append, never a second heading)

# after G4
graphify update . --force
```

Smoke items (G6, user-run) worth calling out beyond
`documentation/module-loader.md:118-125`: with `modules/` **empty**, `/glw car` must not exist, a `[glw-car-buy]`
sign must not parse, `/glw view <car name>` must fall through to the generic item view, a car ItemStack must
serialize as `material:...` rather than vanishing, the fuel action-bar HUD must still work (proves relocation R1),
and `/glw item wearable list` must still work (proves relocation R2).

---

## 6. Risks and open questions for the scrum master

**OQ-1 — Resolved.** Flip 1 renames `config/CopsAndGadgetsConfig.java` to `config/GadgetConfig.java` (README
decision log, "gadget OQ-1"). Carried into T10 step 6 as a one-line pre-check; no scrum-master decision needed.

**OQ-2 — CONFIG-phase item-string parsing (triage, no code change this flip).**
`GameplayConfig.lootChestLoader` (`GameplayConfig.java:335-340`) calls `fileManager.initializeAll()` inside its own
CONFIG `@Bean` body, so `loot_chests.yml` item refs resolve at that instant, with no ordering edge to a module's
converter bean. `InventoryLoader` already avoids this by deferring to `@PostConstruct`
(`GameplayConfig.java:155-158, 342-353`). No shipped YAML uses a `car:` ref, so this is latent. **Default: do not
change the core in this flip; file `brainstorming/bug-docket-2026-09-06/triage/module-split-config-phase-item-parsing.txt`**
describing the class of bug (any CONFIG-phase loader that resolves item strings at construction can miss a module
converter) and rebuild the docket with `build_docket.py`. The same triage entry covers the weapon and turf flips.

**OQ-3 — `SignManager.initialize()` is called twice today.** `GameplayConfig.java:266` calls it explicitly and
Keystone's convention pass calls it again (`BeanFactory.java:294,581-607`; `SignManager` is neither a
`FileInitializer` nor a `BeanLifecycle`, so it is not skipped). Both sign registries are `Map.put`s, so it is a
harmless double pass — but it is a real defect. **Default: fix it as part of T4** (deleting the explicit call is
required for the seam anyway) and add it to the docket via the same triage file as OQ-2, noting the covering
evidence is `SignManagerContributionTest`.

**OQ-4 — Should the two relocations be their own commit?** They are behaviour-neutral, they touch modules other
flips depend on (`gangland-item`, `gangland-weapon`), and flip 4's planner needs to know `WearableAddon` moved.
**Default: yes — commit group A on its own before group B**, and tell the weapon planner.

**Risk — `gangland-weapon` pom churn (T2).** `WearableAddon` pulls XSeries, `SoundEffect`, `Placeholder`,
`FileHandler`/`FileInitializer`/`FileManager` into weapon's compile closure. If any of those turn out to be
awkward there, the fallback is to leave `WearableAddon` in gadget and add a fourth seam: a core
`WearableRegistry` interface with a no-op default bean the gadget module overrides. That fallback is strictly
worse (it would make wearables silently empty without the gadget module) — take it only if T2's pom edit cannot be
made to build.

**Risk — transitive `provided` scope.** The module depends on `gangland-impl` at `provided`, and Maven resolves a
`compile` dependency seen through a `provided` one as `provided`, so `sign-api`, `inventory-api`,
`gangland-domain`, `gangland-item`, `gangland-core` and `gangland-weapon` all remain visible to the module without
being declared. Mail relies on the same behaviour. If a compile error says a core type is missing, add the
artifact explicitly at `provided` rather than at compile scope — a compile-scope entry would not change the jar
(no shade here) but would misrepresent the contract.

---

## 7. Status table (executors fill this)

| Task | Status | Executor | Notes (what changed, what was skipped, failures verbatim) |
|---|---|---|---|
| T1 FuelService → gangland-item | done | sonnet | `git mv` gadget/fuel/FuelService.java → gangland-item/.../item/fuel/FuelService.java; package + javadoc rewritten (dropped self-referential FQN javadoc, no fourth seam needed); redundant `Fuel`/`FuelContract` imports dropped. Also fixed the two stale-FQN javadocs at `FuelContract.java` and `UniqueItemRegistry.java` (both said "the gadget FuelService" — updated to reflect the new location). Import swapped `gadget.fuel.FuelService` → `item.fuel.FuelService` in exactly the 9 files the task named (CarService, VehicleSession, JetpackService, JetpackTask, CarInteractListener, JetpackTaskConsumptionRateTest, FileConfig, GadgetConfig [located by symbol, flip-1 rename confirmed], UniqueItemAddon). No drift from the checklist — grep found exactly the file set the task listed. `gangland-item` pom untouched (no new dependency needed). |
| T2 WearableAddon → gangland-weapon | done | sonnet | `git mv` gadget/wearable/WearableAddon.java → gangland-weapon/.../weapon/wearable/WearableAddon.java; package changed, redundant self-import of `weapon.wearable.WearableService` dropped. Import swapped `gadget.wearable.WearableAddon` → `weapon.wearable.WearableAddon` in exactly the 8 files named (ItemCommand, ItemWearableCommand/Give/Info/List, FileConfig, GameplayConfig, GadgetConfig [located by symbol]). `JetpackService` needed no import change — it only ever imported `weapon.wearable.WearableService`, never `WearableAddon` directly; `GadgetConfig`'s `jetpackService` bean passes the `WearableAddon` instance in as a `WearableService`-typed argument. `gangland-weapon/pom.xml` needed **no edit** — keystone-common, keystone-persistence, XSeries, gangland-item(provided) were all already declared; `gangland-gadget/pom.xml` already had `gangland-weapon` at provided (unchanged, confirmed at pom.xml:46-49). |
| **Gate A** (`mvn clean install -DskipTests` + `mvn test`) | done | sonnet | `mvn clean install -DskipTests -q` → exit 0, clean. `grep -rn "gadget\.fuel\|gadget\.wearable" --include=*.java . \| grep -v target \| grep -v worktrees` → empty for both. `mvn test -B` → BUILD SUCCESS, exit 0 (all suites green, e.g. impl 178, gadget 84, cops-n-crooks 77, mail 25 — no regressions). Group A left uncommitted per instructions (scrum master commits at the boundary). |
| T3 ItemSerializerRegistry priority | done | sonnet | Added `ItemSerializerRegistryPriorityTest.java` first (new file) — red evidence: compile failure, `cannot find symbol: variable CATCH_ALL_PRIORITY` at `ItemSerializerRegistryPriorityTest.java:[56,57]` (via `mvn -pl gangland-infra/gangland-item -am test -Dtest=ItemSerializerRegistryPriorityTest -Dsurefire.failIfNoSpecifiedTests=false`). Implemented `CATCH_ALL_PRIORITY = Integer.MIN_VALUE`, the 3-arg `register(predicate, serializer, priority)` overload, `priority` on the `Entry` record, and the stable `entries.sort(Comparator.comparingInt(Entry::priority).reversed())` in `ItemSerializerRegistry.java` exactly as specified — no drift from the checklist's line numbers/snippet. `ItemConfig.java:151` (`MATERIAL` registration) updated to the 3-arg call with `CATCH_ALL_PRIORITY`, comment extended. Green after: `ItemSerializerRegistryPriorityTest` (2/2) + existing `ItemSerializerRegistryTest` (8/8) both pass, `mvn -pl gangland-infra/gangland-item -am test` green. |
| T4 Sign extension seam | done | sonnet | Created `sign/extension/{SignTypeContribution,SignViewProvider,SignContributions}.java` in gangland-impl per §1.6(c)-(e), modelled on `command/extension/{CommandContribution,CommandContributions}.java`. `SignManager.java`: dropped `CarManager` field/param/assignment/import and the `CarBuySign`/`CarSellSign` imports and the car-buy/car-sell blocks (drift: none — file matched the checklist's line numbers almost exactly, e.g. `CarManager` field at :57, ctor param at :69, assignment at :81, car buy/sell blocks at :192-208); added `DependencyContainer container` field+param, resolved `SignContributions.from(container)` once at the top of `setupSigns()` right after `signPrefix` is built, passed that local into `new ViewSign(...)` (replacing the `carManager` arg) and used it again in a new contributed-signs loop appended just before `return definitions;`. Added a class javadoc paragraph explaining the lazy-resolution rule. `ViewSign.java`: `CarManager carManager` field → `SignContributions contributions`, passed through to `ViewInventoryAspect`. `ViewInventoryAspect.java`: `Car`/`CarManager` imports and field dropped, `carManager`→`contributions` field added, `findCar`/`openCarView` deleted, the car branch in `execute()` replaced with `contributions.openView(player, itemName)`. `GameplayConfig.signManager()`: `CarAddon carAddon` param removed (and the now-unused `CarAddon` import dropped), `DependencyContainer container` param added, **`manager.initialize();` deleted** (OQ-3 fix) with a javadoc explaining why (Keystone's convention pass already calls it, once every module bean exists). `grep -n "initialize()" .../GameplayConfig.java` shows no `signManager`-scoped call (only an unrelated `loader.initialize()` for `InventoryLoader` at a different line). Wrote `SignContributionsTest.java` (4 tests) — red evidence: temporarily stubbed `SignContributions.createSigns`/`openView` to return `List.of()`/`false`, ran the test, 2/4 assertions failed exactly as predicted (`createSigns` empty, `openView` false), then restored the real implementation and it went green (4/4). Wrote `SignManagerContributionTest.java` — red evidence: temporarily replaced the contributed-signs loop in `setupSigns()` with a no-op comment, ran the test, it failed on "the contributed sign must be present" (the 5 core-sign assertions still passed), then restored the loop and it went green (1/1, asserts containment only — never a count, per the checklist's explicit instruction since flip 4 removes 6 more core signs). No p0-wave-3 merge conflicts encountered — the referenced 0.8.3 work was already in the tree per the assignment's brief. |
| T5 Jetpack death hook split | done | sonnet | `JetpackSessionLifecycleListener.java` (main tree copy only — left the two `.claude/worktrees/**` copies of this file untouched): added `onDowned(PlayerDownedEvent)` beside `onUndowned`, importing `org.luckyraven.gangland.core.downed.PlayerDownedEvent`; updated the class javadoc. `CustomPlayerDeathListener.java`: dropped the `JetpackService` import, field, ctor param/assignment, and the `if (jetpackService != null) jetpackService.deactivate(player);` line inside `enterDownedState` — zero drift, every checklist line number (:38, :71, :78, :82, :211) matched the file exactly. Confirmed the ordering note in the checklist's watch-out: `Bukkit.getPluginManager().callEvent(new PlayerDownedEvent(player))` fires after `DownedPlayerRegistry.add`/`savedGameModes.put` and before `player.setGameMode(...)`, so the new listener-driven deactivate lands in that same window — behaviour-preserving. `CustomPlayerDeathListenerQuitTest.java`: dropped the trailing `null` arg and the four-line Netty comment (constructor now takes 3 args). `grep -in jetpack .../CustomPlayerDeathListener.java` empty. No other call site constructs this listener directly (it's `@ListenerHandler`, auto-scanned). |
| **Gate B** (`mvn clean install -DskipTests` + `mvn test`) | done | sonnet | `mvn clean install -DskipTests -q` → exit 0, clean, no output. `mvn test -B` → exit 0, `BUILD SUCCESS`; every module's surefire summary shows `Failures: 0, Errors: 0`, including gangland-impl (183 tests, up from 178 after group A — the +5 are `SignContributionsTest`×4 + `SignManagerContributionTest`×1), gangland-item (+2 for the new `ItemSerializerRegistryPriorityTest`), gangland-gadget (84, unchanged from group A), cops-n-crooks (77), mail (25). The `ERROR [Gangland.ShutdownSequence]`/`GanglandSeizedInventoryService` lines in the log are pre-existing intentional-failure test fixtures (`ShutdownSequence`/`GanglandSeizedInventoryService.restore` test classes exercise the error paths on purpose), not new failures — confirmed by the 0/0 failure counts on those exact test classes in the summary. |
| T6 Poms | done | sonnet | `gangland-impl/pom.xml`: deleted the `gangland-gadget` `<dependency>` block (located by artifactId; the cops block above it was already gone per flip 1). `gangland-features/gangland-gadget/pom.xml`: added `keystone-module` + `keystone-command` (no version/scope) beside `keystone-item`; added `gangland-impl` at `provided` with the mail-style host-jar comment; deleted the unused `net.wesjd:anvilgui` dependency (confirmed zero `net.wesjd` imports in gadget sources); left `gangland-weapon` at `provided` (unchanged). `gangland-build/pom.xml`: added `<exclude>org.luckyraven:gangland-gadget</exclude>` beside the cops one, a second `<artifactItem>` in `copy-runtime-modules`, and a `provided`-scope `<dependencies>` entry beside cops's. No drift — every file/line matched the checklist. **impl error list** after this task: `mvn clean install -DskipTests` → reactor SUCCESS through `gangland-turf`, `FAILURE` on `gangland-impl` (75 `[ERROR]` lines / 19 unique files), everything after impl `SKIPPED` (including `gangland-gadget`). The 19 files are exactly the checklist's 24 "yes-after-A" `§1.1` rows **minus the 5 already fixed by completed group B tasks** (`CustomPlayerDeathListener.java` T5, `SignManager.java`/`ViewInventoryAspect.java`/`ViewSign.java`/`GameplayConfig.java` T4) — expected, not drift, since §1.1's "yes" column predates T3–T5's execution. Files: `CarCommand`, `CarGiveCommand`, `CarInfoCommand`, `CarListCommand`, `FileConfig`, `GadgetConfig`, `ItemConfig`, `ParkedCarRepository`, `ParkedCarTable`, `GadgetPhysicsConfigImpl`, `GanglandCarMessages`, `GanglandCarGangs`, `ItemPredicates`, `CarConverter`, `CarItemRefresher`, `CarItemSerializer`, `CarBuySign`, `CarSellSign`, `CarSignValidator` — all under `gangland-impl/src/main/java`, all named in §1.1. No file outside the inventory appeared. |
| T7 Module entry | done | sonnet | Created `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetModule.java` (`@CustomLog`, `KeystoneModule`, `LISTENER_PACKAGE`/`COMMAND_PACKAGE`/`REPOSITORY_PACKAGE` constants, `configure`/`onEnabled`/`onDisabled` modelled literally on `MailModule`/`CopsNCrooksModule`) plus empty `@Configuration(phase = Phase.FILE)` shell `GadgetFileConfig` and empty `@Configuration` (default CONFIG phase) shell `GadgetModuleConfig`, both in `org.luckyraven.gangland.gadget.config` (co-located with the existing `GadgetPhysicsConfig` interface, matching the `<module>.config` convention cops-n-crooks uses). `module.yml` written with `Id: gadget`, `Name: Gangland Gadgets`, `Version: ${project.version}`, `Main: org.luckyraven.gangland.gadget.GadgetModule`, `Host_Api: 0.8`, `Artifact: org.luckyraven:gangland-gadget`, **no `Depends:` key** (weapon/turf still core, per plan). `commands.json` started as `{}`. Verified `module.properties` already exists with `module.name=${project.name}` (no edit needed) and that resource filtering (`${project.version}` substitution) is inherited from the root pom (`pom.xml:507`, same as mail — no per-module config needed). No drift. |
| **Gate C** (errors only in gangland-impl) | done | sonnet | `mvn clean install -DskipTests` → reactor SUCCESS through `gangland-turf`, `FAILURE` on `gangland-impl` only, `gangland-gadget` and everything after `SKIPPED` (same 19-file/75-line error list as T6, unchanged by T7 — expected, T7 touches only the gadget module). Saved via `mvn clean install -DskipTests 2>&1 \| grep -E "^\[ERROR\].*\.java" \| sort -u > brainstorming/module-split-2026-09-07/gadget-errors-after-T7.txt` (75 lines / 19 unique files). Grepped the full untruncated log for any file outside the checklist's inventory — none found. Second half of the gate: `mvn -q -pl gangland-features/gangland-gadget install -DskipTests` (no `-am`, using the `gangland-impl-0.8.4` jar cached in the local repo from the Gate B install) → **exit 0, green** — `GadgetModule` + the two config shells compile cleanly. (With `-am` the same command fails, reproducing the impl error list, because `-am` forces a rebuild of `gangland-impl` from source; this matches the checklist's own caveat and is not a gate failure.) |
| T8 Car commands + commands.json | done | sonnet | `git mv` the 4 files in `command/sub/car/` → `gangland-features/gangland-gadget/.../gadget/command/`; only the package line needed changing (`org.luckyraven.gangland.command.sub.car` → `org.luckyraven.gangland.gadget.command`) — all four files already imported `gadget.car.config.CarAddon`/`gadget.car.Car` by FQN, so no import edits were needed (no drift from the checklist, just less work than expected). Cut the 5 `car*` keys (lines 494-513, verbatim) from `gangland-impl/src/main/resources/commands.json` into the module's `commands.json` (was `{}` from T7); impl count 182→177 confirmed via `python -c "import json;print(len(json.load(open(...))))"` on both files. `InformationManagerTest.java:41`: **182 → 177** (the value present at start of this task was 182, not the 225 the checklist's own comment assumed — flip 1 had already lowered it); updated the assertion and its explanatory message to mention the 5 car* keys. |
| T9 Persistence | done | sonnet | `git mv` `ParkedCarRepository.java`/`ParkedCarTable.java` → `gangland-features/gangland-gadget/.../gadget/database/`; package → `org.luckyraven.gangland.gadget.database`; dropped the now-redundant same-package `ParkedCarTable` import from the repository. `grep -rn "database/(repositories\|tables)/car" gangland-impl` empty; both empty `car/` directories removed. `setDataSupplier` wiring untouched (still only in `CarService.initialize()`, module-side, per the memory rule — no second call added). |
| T10 Contract impls + module configs | done | sonnet | `git mv` `GadgetPhysicsConfigImpl.java`/`GanglandCarMessages.java` → `gangland-features/gangland-gadget/.../gadget/contract/`; package `org.luckyraven.gangland.gadget.contract`; added the `Settings` import `GadgetPhysicsConfigImpl` needed (it referenced `Settings.getGadget*()` unqualified in-package before the move). `GanglandCarGangs.java` (the p0-wave-3 arrival) moved from `gangland-impl/.../gadget/` → `gangland-features/gangland-gadget/.../gadget/car/access/`; package `org.luckyraven.gangland.gadget.car.access`; dropped the now-same-package `CarGangContract` import. Confirmed `gangland-impl/.../gadget/` no longer exists. Filled `GadgetFileConfig` (`gadgetPhysicsConfig`, `carAddon` — the latter now registers its own `FileHandler(..., moduleLoader.classLoader())` since T10 step 7 removes the core's `cars` registration) and `GadgetModuleConfig` (`carMessageContract`, `carAccessPolicy`, `carService`, `jetpackService`, copied verbatim from `GadgetConfig.java`, parameter lists and bodies unchanged) exactly per the checklist snippets — pre-check confirmed `config/GadgetConfig.java` held exactly the four listed beans and nothing else, so it was deleted outright (`git rm`), no partial-keep needed. `FileConfig.java`: deleted `gadgetPhysicsConfig`/`carAddon` beans and the two now-unused imports (`gadget.car.config.CarAddon`, `gadget.config.GadgetPhysicsConfig`). `KernelConfig.java:176`: deleted the `cars` `FileHandler` registration. **Drift / extra fix (not in the checklist text):** `mvn -q -pl gangland-features/gangland-gadget install -DskipTests` (T9's done-when) first failed with `cannot access org.luckyraven.keystone.economy.EconomyOwner — class file not found` at the moved `CarGiveCommand.java` — `User<T> implements EconomyOwner` (`gangland-domain/.../User.java:14,38`) and `keystone-hooks` is `provided` scope everywhere (non-transitive), so any module whose own classes touch `User`/`UserManager` must declare `keystone-hooks` itself (mail and cops-n-crooks both already do). `gangland-gadget/pom.xml` had it missing — added `org.luckyraven:keystone-hooks` (no version/scope, same as mail's/cops's declarations, pinned `provided` by root dependencyManagement) beside `keystone-command`. This is a required consequence of T8 moving `User`-touching command classes into the module; not flagged in the checklist's own T6/T8 file lists, so recording it here per the "add whatever the compiler now demands" precedent from T2's watch-out and the README's "transitive provided scope" risk note. After the fix: `mvn -q -pl gangland-features/gangland-gadget install -DskipTests` → exit 0; `mvn -pl gangland-features/gangland-gadget test` → BUILD SUCCESS, 47/47 tests green (incl. `CarAccessPolicyTest` 7/7). `grep -rn "gadget" gangland-impl/.../config/` is **not** empty — `ItemConfig.java` still imports `gadget.car.config.CarAddon` for its (not-yet-moved) `carConverter`/`carItemSerializer`/`carItemRefresher` beans, which is exactly what Gate D1 below expects to remain (T11/D2's job) — noted so the literal wording of T10's "done when" isn't misread as unmet. |
| **Gate D1** (only the 8 item/sign files left) | done | sonnet | `mvn clean install -DskipTests 2>&1 \| grep -E "^\[ERROR\].*\.java" \| sort -u > brainstorming/module-split-2026-09-07/gadget-errors-after-T10.txt` → **exactly 8 unique files**, matching the checklist's list verbatim: `config/ItemConfig.java`, `item/ItemPredicates.java`, `item/converter/CarConverter.java`, `item/refresher/CarItemRefresher.java`, `item/serializer/CarItemSerializer.java`, `sign/type/trade/car/CarBuySign.java`, `sign/type/trade/car/CarSellSign.java`, `sign/validation/trade/car/CarSignValidator.java` (checklist text says "9 remaining files" including `sign/SignManager.java` in its prose, but `SignManager.java` was already fixed in group B's T4 — the actual save-to-file gate list, and the numbered file list right below the gate heading, both say 8; grepped the full untruncated log for anything outside these 8 — none found; reactor: SUCCESS through `gangland-turf`, FAILURE on `gangland-impl` only, everything after (`gangland-gadget` included) SKIPPED by reactor ordering, same shape as Gate C). Second half of the gate (module standalone, no `-am`): `mvn -q -pl gangland-features/gangland-gadget install -DskipTests` → exit 0 after the `keystone-hooks` pom fix (see T10 notes). No file outside the inventory appeared in either run. |
| T11 Item converter/serializer/refresher | done | sonnet | `git mv` `item/converter/CarConverter.java`, `item/refresher/CarItemRefresher.java`, `item/serializer/CarItemSerializer.java` → `gangland-features/gangland-gadget/.../gadget/item/`; package → `org.luckyraven.gangland.gadget.item`. No new core-type imports were needed beyond what each file already had (`Car`/`CarManager`/`CarAddon`/`CarKey` stay imported since those classes live in the sibling `gadget.car`/`gadget.car.config` packages, not `gadget.item`). Created `gadget/item/GadgetItemPredicates.java` — `CAR` predicate + `hasTag` helper, copied from the deleted `ItemPredicates.CAR`/`hasTag` body exactly as the checklist's step 2 specifies. `ItemPredicates.java`: deleted the `CAR` constant and the `gadget.car.CarKey` import; rewrote the class javadoc to name `Weapon`/`Ammunition`/`Wearable` as the remaining reasons the class lives in impl and to note `CAR` moved to `GadgetItemPredicates`. `ItemConfig.java`: deleted the `carConverter`/`carItemSerializer`/`carItemRefresher` beans, the `gadget.car.config.CarAddon` import, and every car parameter/registration line from `itemConverterRegistry` (`ItemKind.CAR`), `itemSerializerRegistry` (`ItemPredicates.CAR`) and `itemRefresherRegistry` — no drift, exactly the beans/lines the checklist named (this file's current line numbers already reflect T3's earlier edits, located by symbol rather than the stale `d6bb33ac` numbers). Added the three registration beans to `GadgetModuleConfig` verbatim as specified (registry-as-parameter pattern), plus a class-javadoc paragraph documenting the ordering-edge/priority-sort rationale. **Done-when:** `mvn clean install -DskipTests 2>&1 \| grep -E "^\[ERROR\].*\.java"` no longer names any `item/` file (verified as part of the full Gate D2 build below — reactor green); `grep -rn "ItemKind.CAR\|ItemPredicates.CAR" gangland-impl` empty (confirmed). |
| T12 Car signs | done | sonnet | `git mv` `sign/type/trade/car/{CarBuySign,CarSellSign}.java` and `sign/validation/trade/car/CarSignValidator.java` → `gangland-features/gangland-gadget/.../gadget/sign/`; package → `org.luckyraven.gangland.gadget.sign`; `CarSignValidator`'s `CarManager` import dropped (same package now, per the checklist). `CarBuySign`/`CarSellSign` kept every `org.luckyraven.gangland.sign.*` import unchanged (they were already fully-qualified in the pre-move file, so no new imports were actually required there beyond what already existed — the checklist's "add explicit imports" language matched what was already present); dropped their now-redundant `sign.validation.trade.car.CarSignValidator` import (same package now). Deleted the emptied `sign/type/trade/car/` and `sign/validation/trade/car/` directories (confirmed gone). Created `gadget/sign/CarSignContribution.java` (`SignTypeContribution`, ctor `UserManager<Player>`, `CarManager`, `WeaponService`, `AmmunitionManager`; `signs(signPrefix)` rebuilds the `car-buy`/`car-sell` `SignType`s + `CarBuySign`/`CarSellSign` instances exactly as `SignManager.java:192-208` did pre-T4) and `gadget/sign/CarSignViewProvider.java` (`SignViewProvider`, ctor `JavaPlugin`, `CarManager`; `open()` returns `false` when `carManager.getCar(content) == null`, else runs the deleted `ViewInventoryAspect.openCarView` body verbatim — recovered from `git diff HEAD -- .../ViewInventoryAspect.java` since T4 had already deleted it from the working tree — and returns `true`). Added `carSignContribution`/`carSignViewProvider` `@Bean` methods to `GadgetModuleConfig`, one bean per concrete type per the README's seam-shape rule, mirroring `MailModuleConfig`'s `gangMailContribution`/`gangAllyMailContribution` pattern; `carSignViewProvider` passes the module's `Gangland gangland` field as the `JavaPlugin` argument (`Gangland extends JavaPlugin`). Extended the config's class javadoc to document the sign beans. **Done-when:** `mvn clean install -DskipTests -q` green for the whole reactor (see Gate D2 below) and `grep -rn "org\.luckyraven\.gangland\.gadget\." gangland-impl/src` empty (confirmed). No p0-wave-3 merge was needed — that work was already in the tree per the assignment's brief, matching T4's earlier finding. |
| T13 YAML `items/cars.yml` | done | sonnet | `git mv gangland-impl/src/main/resources/items/cars.yml` → `gangland-features/gangland-gadget/src/main/resources/items/cars.yml` (identical relative path `items/cars.yml`, per the checklist's explicit exception to the "module YAML nests under `resources/<module>/`" rule). `ls gangland-impl/src/main/resources/items/` now shows exactly four files (`ammunition.yml`, `money.yml`, `unique_items.yml`, `wearables.yml`); `grep -rn "cars.yml\|\"cars\"" gangland-impl/src/main/java` and a resources-wide `grep -rn "cars"` both empty (the core's `KernelConfig` `cars` `FileHandler` registration was already removed in T10 step 7). `mvn clean package` jar-content verification (`unzip -l`, the G3 half of §5) was **not run** — out of this group's scope; T16/G3 owns it. |
| **Gate D2 = G1** | done | sonnet | `mvn clean install -DskipTests -q` → exit 0, empty output (quiet-mode success across the whole reactor including `gangland-gadget`; a first attempt without redirecting to a file was cut off by the tool's default 120s timeout mid NMS-remap logging — re-ran with `timeout: 600000` and file redirection, which completed cleanly with 0 `ERROR` lines in the captured log). `grep -rn "org\.luckyraven\.gangland\.gadget\." gangland-impl/src/main` → empty. `grep -rn "gadget" gangland-impl/src/main/resources` → **2 lines, both false positives**: `items/unique_items.yml:104` ("`# Fuel can for vehicles and gadgets`") and `:110` ("`Used to power vehicles and gadgets`") — plain English word "gadgets" in a Fuel-Can item's comment/lore, present since commit `8b253deee` (2026-03-28), untouched by this or any prior module-split task, and unrelated to the `org.luckyraven.gangland.gadget` package or the gadget module's resource tree. Recorded here rather than edited (house rule: don't edit YAML content without cause, and this string isn't a module reference). `test ! -d gangland-impl/src/main/java/org/luckyraven/gangland/gadget` → true (directory absent, confirmed by direct `ls`/`test` and by T10's earlier full-directory delete holding). Verified as a bonus: `item/converter`, `item/refresher`, `item/serializer` directories still exist in impl (correctly — they hold the non-car converters/refreshers/serializers for weapon/ammo/wearable/unique/money), while `sign/type/trade/car` and `sign/validation/trade/car` are gone. |
| T14 Tests | done | sonnet | Wrote `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/GadgetModuleTest.java`, modelled on `MailModuleTest`/`CopsNCrooksModuleTest`: `configure_declaresConfigsAndPackages` asserts `registrations.configurations()` equals `List.of(GadgetFileConfig.class, GadgetModuleConfig.class)` and the listener/command/repository package lists each equal a singleton list of `GadgetModule`'s constant; `declaredPackages_matchClasses` asserts `JetpackSessionLifecycleListener.class.getPackageName()` starts with `LISTENER_PACKAGE`, `CarCommand.class.getPackageName()` equals `COMMAND_PACKAGE`, `ParkedCarRepository.class.getPackageName()` equals `REPOSITORY_PACKAGE` — exactly the checklist's step 1 spec, no drift. Per the checklist's own note (§3, item 4), this is a declaration guard that cannot be red before `GadgetModule` exists (group C already created it), so no red/green cycle was staged for it — same status `MailModuleTest`/`CopsNCrooksModuleTest` carry; ran once and it was green from the start (2/2). Verified the T8 count edit already landed: `InformationManagerTest.java:41` asserts `177` (confirmed present, no change needed here). Ran the full suite (`mvn test -B`): **BUILD SUCCESS**, every module `Failures: 0, Errors: 0` — no test referenced a moved symbol that wasn't already fixed by groups A–D2; `gangland-gadget` module: 49/49 tests green (`CarAccessPolicy` 7, `Car` NBT 5, `ExhaustSide` 22, `ParkedVehicle` 3, `VehicleSession` 5, `GadgetModule` 2, `JetpackTask` 5); `gangland-impl`: 183/183; `gangland-item`: 88/88 (incl. `ItemSerializerRegistry · CATCH_ALL_PRIORITY overload` 2/2 from T3). This run **is** gate G2. |
| **Gate G2** (`mvn test -B`, full reactor) | done | sonnet | `mvn test -B` → exit 0. Reactor Summary: all 39 reactor modules `SUCCESS` (Gangland Warfare, Compatibility, Version Handler, Core, UI, Hologram API, Infrastructure, Item [88 tests], Features, Weapons [75], Inventory API, Scoreboard API, Sign API [63], Lootchest API [29], Shop API [59], Domain [119], Turf [84], Gangland/impl [183], Mail [25], Cops N Crooks [77], **Gadgets [49]**, version-1_16_R1..1_21_R7 ×20, Build). `BUILD SUCCESS`. The `ERROR [Gangland.ShutdownSequence]`/`GanglandSeizedInventoryService` console lines are pre-existing intentional-failure fixtures in `ShutdownSequence`/`GanglandSeizedInventoryService.restore` tests (asserting the error path itself), not real failures — those exact test classes show 0 failures/0 errors in the surefire summary. |
| T15 Docs | done | sonnet | `documentation/module-loader.md`: added `cops-n-crooks-0.8.4.jar` (missed by flip 1's doc pass — now included alongside) and `gangland-gadget-0.8.4.jar` to the folder tree; moved gadget into the "is a module" table row (`runtime module since 0.8.4`), narrowed the turf/weapon row; updated the order paragraph to "remaining incremental order is **turf → weapon**" and the `Depends:` sentence to cover gadget's future `[weapon]`; added a paragraph to the "Writing a module" YAML bullet on the pre-existing-shared-directory exception (`items/cars.yml`) that T13 relied on; **appended** (not a new heading) five bullets to the existing `## Core seams` section for `SignTypeContribution`, `SignViewProvider`, `SignContributions`, the item-registry-injection pattern (no new interface), and `ItemSerializerRegistry.CATCH_ALL_PRIORITY` — `grep -c "^## Core seams" documentation/module-loader.md` = 1, confirmed no second heading. `CLAUDE.md` (gitignored/untracked, edits local-only per project convention): module table row for `gangland-features/gangland-gadget` → **Runtime module since 0.8.4**; two-tier paragraph's runtime-modules list now reads "`gangland-mail` and, since 0.8.4, `cops-n-crooks` and `gangland-gadget`", remaining order "**turf → weapon**", and a clause added for gadget's future `Depends: [weapon]`. `documentation/README.md`: **not edited** — checked line 59 (the Gadget System TOC row, "Cars, jetpacks, fuel, physics") and it does not name gadget as a compile-time dependency of anything; it is a bare table-of-contents pointer to `documentation/developer/gadgets.md`, unchanged in scope from what the cops-n-crooks flip (04d5171b) also left untouched. Per this executor's brief ("documentation/README.md only if it names gadget as compile-time") no edit was made; recorded here as skipped-with-reason rather than silently omitted. **Out of scope, left stale, not touched:** `documentation/developer/gadgets.md`, `documentation/developer/architecture.md:299`, `documentation/developer/modules.md` — all still describe gadget's Maven dependency graph without the runtime-module framing; the gadget.md checklist's own T15 text only names `documentation/README.md:59` (which points at, but is not, `gadgets.md`), and the executor's brief scoped README.md narrowly (see above), so these were left for a future docs pass. Memory files (`project_module_loader_plan.md`, `MEMORY.md`) were **not** touched — explicitly reserved to the scrum master per the sprint's ground rules ("memory files are the scrum master's"), overriding the checklist's own §2/§4 line items naming them. |
| **Gate G4** (docs) | done | sonnet | `git diff --stat -- documentation/`: `documentation/module-loader.md \| 42 ++++++++++++++++++++++++++++++++++++++----` (1 file changed, 38 insertions(+), 4 deletions(-)) — `CLAUDE.md` does not show here because it is gitignored/untracked (confirmed by design, see T15 notes). `grep -c "^## Core seams" documentation/module-loader.md` → `1`. `grep -rn "gangland-gadget" CLAUDE.md documentation/` → shows the runtime-module framing in `CLAUDE.md:152,171,175` and `documentation/module-loader.md:17,31`; the remaining hits (`developer/architecture.md`, `developer/gadgets.md`, `developer/modules.md`, `developer/README.md`, `tests/features/cars-fuel.md`, the 0.7.4 changelog) are the out-of-scope stale docs noted above, not part of this gate's pass/fail. `grep -n "SignTypeContribution\|SignViewProvider\|CATCH_ALL_PRIORITY" documentation/module-loader.md` → 6 hits, all in the new Core-seams rows. |
| T16 Verification gates | done | sonnet | Ran §5 top to bottom (see the four gate rows in this table plus below) then `graphify update . --force`. **G0**: `0.8.3` is an ancestor of `HEAD` (the `p0-wave-3`/0.8.3 work is already in the tree per this session's brief; no merge was needed, confirmed by groups A–D2's own notes finding zero conflicts). **G1**: `mvn clean install -DskipTests -q` → exit 0; `grep -rn "org\.luckyraven\.gangland\.gadget\." gangland-impl/src` → empty (only the two unrelated English-word "gadgets" lines in `unique_items.yml`, already logged at Gate D2); `grep -rn "gadget\.fuel\|gadget\.wearable" --include=*.java . \| grep -v target \| grep -v worktrees` → empty. **G2**: see the Gate G2 row above — `mvn test` (as `mvn test -B`) BUILD SUCCESS, all counts as listed. **G3**: `mvn clean package -DskipTests -q` → exit 0; `unzip -l target/gangland_warfare-0.8.4.jar \| grep -c "gangland/gadget/"` → `0`; `unzip -l target/gangland_warfare-0.8.4.jar \| grep "items/cars.yml"` → empty; `unzip -l target/modules/gangland-gadget-0.8.4.jar` → 91 files total, includes exactly one each of `module.yml`, `commands.json`, `items/cars.yml`, `org/luckyraven/gangland/gadget/module.properties`, and every class file is under `org/luckyraven/gangland/gadget/` (verified — no stray package); `unzip -p target/modules/gangland-gadget-0.8.4.jar module.yml` → `Version: 0.8.4`, no `Depends:` key; core `commands.json` key count (`python -c "import json;print(len(json.load(open('gangland-impl/src/main/resources/commands.json'))))"`) = `177`; module `commands.json` = `5`, keys `['car', 'car_give', 'car_help', 'car_info', 'car_list']`. **G4**: see the Gate G4 row above. **After G4**: `graphify update . --force` → `15843 nodes, 43187 edges, 561 communities`; `graphify-out/graph.json` and `GRAPH_REPORT.md` updated; one warning noted by the tool itself (6 zero-node source files — `bugs.json`, `observations.json`, three `commands.json` files, +1 more — pre-existing graphify quirk for non-code JSON, unrelated to this flip). |
