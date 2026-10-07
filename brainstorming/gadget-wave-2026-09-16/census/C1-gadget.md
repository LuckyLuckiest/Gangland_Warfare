# C1 — Gadget module Bartizan-coupling census (repo: Gangland Warfare, branch 0.9.1, graph = HEAD fb460b35)

Source: Haiku Explore agent, graphify-first on the fresh Gangland graph.

## 1. Coupling site table — `gangland-features/gangland-gadget`

| file:line | Bartizan symbol | used for | fallback when Bartizan absent | classification |
|---|---|---|---|---|
| `config/GadgetModuleConfig.java:8-10` (imports), behavior at `:67` | `BartizanApi`, `Wearable`, `WearableCatalog` | resolves the wearable catalog to build an `isJetpackFuelSink` predicate | `ServicesManager` registration null → predicate returns `false` (line 67) | REPLACEABLE — only reads wearable identity, not weapon behavior |
| `jetpack/JetpackService.java:15-17` (imports), lookup at `:47` | `BartizanApi`, `Wearable`, `WearableCatalog` | lazy catalog resolution; `activate(Player, Wearable)` takes a Bartizan `Wearable` as its session param type | `wearables()` → null if unregistered (line 47) | REPLACEABLE — `Wearable` here is used purely as an item-identity/extraTags carrier, not a weapon |
| `jetpack/JetpackSession.java:6` | `Wearable` | session field holds the `Wearable` instance for the active jetpack | **no null-check** — assumes present once constructed | flagged WEAPON-INHERENT by the census agent, but on inspection this is the same identity-only use as above — **lead's read: likely REPLACEABLE too, verify with planner**; the agent's own summary text calls "jetpack identity + catalog reads" REPLACEABLE, which includes this file, so the per-row tag here looks like a copy/paste slip — planner should re-check `JetpackSession.java:6` directly |
| `jetpack/JetpackTask.java:18-22` (imports), check at `:255` | `BartizanApi`, `Wearable`, `Weapon`, `WeaponCatalog`, `ScopeData` | `isScoped()` — checks whether the player's currently-held Bartizan weapon is zoomed, to block jetpack thrust while scoped | `rsp == null` → `false` (line 255) | WEAPON-INHERENT — this genuinely needs a real Bartizan weapon concept (scope state), not just item identity |
| `listener/car/CarDamageListener.java:28-33` | `BartizanApi`, `WeaponEntityDamageEvent`, `WeaponRaytraceImpactEvent`, `MeleeWeapon`, `Weapon`, `WeaponCatalog` | listens for Bartizan weapon-impact events landing on car entities (damage routing) | no fallback observed; car-specific, unrelated to jetpack | WEAPON-INHERENT — genuinely needs Bartizan's weapon-damage event types; this is K2's "one remaining edge" |
| test `jetpack/JetpackTaskConsumptionRateTest.java:7` | `Wearable` | mocks a `Wearable` to test fuel consumption rate | test-only, uses a Mockito mock | REPLACEABLE (test double, not a runtime dependency) |

**Net for K1/K2**: only `JetpackTask.java`'s `isScoped()` check and `CarDamageListener` are true weapon-behavior dependencies. Every other jetpack-path Bartizan import is item-identity/extraTags reads that K1 says must leave. `JetpackTask`'s scope-check is itself gadget-side logic reading a *car/weapon* interaction, not intrinsic to jetpack flight — planner should confirm whether jetpack thrust genuinely needs to know weapon scope state or whether this can be dropped/replaced with a generic "player is aiming" Bukkit-observable check.

## 2. Car item registration pattern — the template for the jetpack to copy

`GadgetModuleConfig.java`:
- **Converter** (`:120`): `ItemConverterRegistry.register(ItemKind.CAR, converter)` — raw YAML entry → `Car` domain entity.
- **Serializer** (`:127`): `ItemSerializerRegistry.register(GadgetItemPredicates.CAR, serializer)` — `Car` entity → `ItemStack` NBT.
- **Refresher** (`:134`): `ItemRefresherRegistry.register(refresher)` — reload/restamp hook.

`CarAddon` (`:24-26`, extends `CarManager`) loads `items/cars.yml`; `CarItemSerializer` reads/writes the NBT tag from `CarKey.CAR_ID`; `GadgetItemPredicates.CAR` is the predicate used to recognize a car `ItemStack`.

**Pipeline**: YAML (`items/cars.yml`) → `CarAddon.loadCars()` → `carService.buildItem()` → `ItemBuilder` stamps `CarKey` NBT tag → `CarItemSerializer` applies on the stack → `ItemRefresherRegistry`'s refresher keeps stacks in sync on config reload. This is the exact three-registration shape (converter/serializer/refresher via keystone-item's registries) that K4 wants the jetpack to reuse: a `jetpack:` (or `gadget:`) `ItemKind`, its own converter/serializer/refresher, same `GadgetModuleConfig` wiring.

## 3. Jetpack give/detect/fuel mechanics

- **Give**: `JetpackEquipListener` detects a chestplate placed into the armor slot (`PlayerInventoryClickEvent`, slot 38) and `PlayerInteractEvent` (right-click equip), then calls `JetpackService.activate(player, wearable)`. No dedicated `/glw jetpack give` command was found by this census — confirm with planner whether jetpacks are currently obtained only via loot/shop (`wearable:jetpack` vocabulary string), not a direct give command.
- **Detect**: `JetpackTask.java:247` compares `Wearable.getWearableKey(chestplate)` against the active session's jetpack key.
- **`extraTags()` keys read** (all in `JetpackTask.java`): `jetpack_ascend_power` (`:136`), `jetpack_max_speed_y` (`:137`), `jetpack_fuel_consumption_rate` (`:216`), and a `Sounds` map (`:286`). These four are the full config surface a gadget-owned jetpack YAML must replicate.
- **Fuel**: `gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/fuel/` — `FuelService` (contract methods `consumeFuelFromWearable()`, `hasFuelOnWearable()`, `getWearableFuelLevel()`), `FuelKey` (NBT tag constants), `FuelContract` (interface). This is already Gangland-owned and reusable as-is (K5) — its API is currently phrased around "Wearable" but the underlying NBT-tag mechanism should work on any `ItemStack`; planner should verify `FuelService`'s method signatures don't hard-require a Bartizan `Wearable` type (this census did not confirm the exact parameter types — flagged as unverified).

## 4. Settings/Messages inventory

Grep across `jetpack/` and `listener/jetpack/` for `Settings.` / `Messages.` returned **empty** — the jetpack path uses neither today. Nothing to migrate on this front.

## 5. Condition-method pattern for optional plugins

- `Settings.isCitizensAvailable()` exists at `gangland-api`'s `Settings.java:294` — a static boolean method, used as `@ListenerHandler(condition = "isCitizensAvailable")`.
- **`Settings.isBartizanAvailable()` does NOT exist** — K2 requires the planner to add this (additive api change, no version bump needed per contract rules since it's a pure addition).
- Keystone mechanism: `keystone-bean`'s listener service resolves the `condition` string via reflection against a static method (see §6 below); an empty condition string means "always register".

## 6. Keystone-side condition/ReflectionGuard mechanics (cwd `E:\Programming\java\Keystone`; freshness not re-checked by this census — verify separately)

- `ListenerHandler.condition()` (`keystone-bean/.../listener/ListenerHandler.java`) — a `String` annotation field, default `""`.
- `ListenerService`'s `invokeMethod(String condition)` (`:196`) — abstract method whose implementation reflectively invokes the named static method (by convention, on `Settings`) to decide whether to register the listener; empty string = unconditional registration (`:195`).
- `ReflectionGuard` (`keystone/diagnostics/ReflectionGuard.java`) — catches `NoClassDefFoundError`/`TypeNotPresentException` during whole-class reflective scanning, reports fault `reflection.type.missing`, logs a WARN, and skips the offending class rather than aborting boot. This is what already protects a Citizens-typed listener; the same guard would protect a hypothetical Bartizan-event-typed listener if one existed (currently `CarDamageListener` is the only Bartizan-event-typed listener in gadget, per §1).

## Not verified by this census (flag to planner)
- Whether `FuelService`/`FuelKey` method signatures reference `Wearable` by type or accept a generic `ItemStack`/NBT holder — check before assuming zero-friction reuse.
- Whether a `/glw jetpack give` (or similar) command exists at all today, or jetpacks are shop/loot-only.
- The apparent classification inconsistency on `JetpackSession.java:6` (tagged WEAPON-INHERENT in the raw table but described as identity-only, matching the REPLACEABLE group, in the agent's own summary) — re-verify directly before the plan relies on it.
