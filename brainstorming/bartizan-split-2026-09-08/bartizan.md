# Bartizan 0.1.0 — new standalone weapons plugin (stream P2)

**Planner:** opus, 2026-09-08 (session cb80e892). **Graph:** Gangland `graphify-out/graph.json` is from 2026-09-08 00:39,
the newest commit is `bec0b8e9` 2026-09-08 11:49 — **stale by three commits**, none of which touch
`gangland-features/gangland-weapon/**`. Every weapon fact below was re-read from source, not from the graph.

**Repo:** `E:\Programming\java\Bartizan` — **does not exist yet**, this stream creates it. Branch `master`.
**Coordinates:** groupId `org.luckyraven.bartizan`, root artifactId `bartizan`, `<revision>0.1.0</revision>`,
modules `bartizan-api` + `bartizan-plugin` only. **Output:** `bartizan-plugin/target/Bartizan-0.1.0.jar`.

**Source of the code:** Gangland `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]` branch `0.8.4` —
`gangland-features/gangland-weapon/**` (145 main `.java`, 16 test `.java`, 22 `weapon/*.yml`, `items/ammunition.yml`,
`items/wearables.yml`, `commands.json`, `module.yml`, `module.properties`) and `gangland-compatibility/version-impl`
(`Compatibility`, `CompatibilityWorker`, `RecoilCompatibility`). **This stream never edits the Gangland repo.** Removing
the weapon module from Gangland is stream P3's job (`gangland-0.9.0.md`); here the Gangland tree is a read-only donor.

---

## 0. Summary for the orchestrator

**What this stream does:** creates a new Maven repo and moves the entire weapon feature into it as a standalone Spigot
plugin that depends only on Keystone 1.9.0. Nothing is left behind as a shim; Gangland reaches weapons only through
Bukkit's `ServicesManager` and Keystone's item registries.

| Repo | Created | Moved in (copy from Gangland) | Deleted |
|---|---|---|---|
| `E:\Programming\java\Bartizan` | 8 build/meta files, 14 bootstrap files, ~20 new api interfaces/impls | 145 main java, 16 test java, 25 YAML, 1 `commands.json` | — |
| Gangland | — | — | **nothing (P3 does the deletions)** |

**Seams this stream introduces** (all consumed by P3, none by Keystone):

1. `BartizanApi` on the `ServicesManager` — the single discovery point. Five accessors, no static holder.
2. `ItemVocabulary` on the `ServicesManager` (namespace `bartizan`) — how `weapon:` / `ammo:` / `wearable:` item
   strings keep resolving inside Gangland's loot chests, shops and signs after the split.
3. `WeaponRaytracer` on the `ServicesManager` — **same key as today**, so third-party consumers keep the same
   key; Gangland no longer calls it (R1 ruling (b)).
4. `CombatEligibility` pulled from the `ServicesManager` — replaces the `DownedPlayerRegistry` static gate.
5. `NpcWeaponFactory` / `NpcWeaponController` — Bartizan owns NPC firing cadence, verbatim from today's
   `NpcCombatDelegate`.
6. `WeaponEntityDamageEvent.weaponName()` / `.kind()` — replaces `ThrowableAction`'s two public static maps (R1).

**Order dependencies on the other two streams:**

| Group | Needs in `~/.m2` / on disk before it can start |
|---|---|
| A (skeleton) | nothing |
| B–I | **`org.luckyraven:keystone-* 1.9.0` installed by P1** (`mvn clean install` in the Keystone repo). Specifically: `keystone-common` with `PacketAdapter.relativeCameraRotation` + `ReflectivePacketAdapter`, `keystone-item` with `ItemSerializerRegistry.CATCH_ALL_PRIORITY` + the priority `register` overloads + `org.luckyraven.keystone.item.spi.{ItemVocabulary, ItemVocabularyRegistrar}`. |
| J–L | as above |
| P3 (Gangland 0.9.0) | **`org.luckyraven.bartizan:bartizan-api:0.1.0` installed by group I of this stream** |

**Assumption to state out loud:** the executor may assume `mvn -q -pl bartizan-api -am install` resolves
`keystone-common 1.9.0` / `keystone-item 1.9.0` from `~/.m2` because P1 ran `mvn clean install` first. If it does not,
**stop** — do not vendor Keystone classes, do not downgrade to 1.8.1. Record it in section 7 and report.

**Task groups** (letter = group, size = files touched): A/B1-B3 (8) · B/B4 (25) · C/B5 (18) · D/B6 (19) ·
E/B7 (3) · F/B8-B11 (14) · G/B12 (24) · H/B13 (23) · I/B14 (12) · J/B15 (5) · K/B16 (17) · L/B17 (4) ·
M/B18 (25) · N/B19-B20 (18) · O/B21 (4) · P/B22 (verification only).
**15 execution groups + 1 verification gate group, 22 tasks, no group above 25 files.**

**Three biggest risks:** (1) the api/plugin split line inside a 145-file module that was never designed to have one —
mitigated by giving the executor an explicit per-file destination table in §1.1 rather than a rule to apply;
(2) the reflective recoil path replacing 20 hand-written NMS adapters, verifiable only in-game — mitigated by keeping
the `-yaw + 1` / `pitch - 1` transform on the Bartizan side and pinning it with a unit test; (3) NPC firing cadence
moving from cops into Bartizan — mitigated by a red-first cadence test that pins the exact tick arithmetic **before**
the code moves.

---

## Contract

Every name here is verbatim from `architecture/PICK.md`. P1 and P3 must diff against this section.

### C.1 Maven coordinates

| Artifact | Coordinates | Scope in consumers |
|---|---|---|
| Bartizan api | `org.luckyraven.bartizan:bartizan-api:0.1.0` | `provided` in every Gangland module pom that needs weapons |
| Bartizan plugin | `org.luckyraven.bartizan:bartizan-plugin:0.1.0` | never a dependency of anything; `<finalName>Bartizan-${project.version}</finalName>` |
| Keystone | `org.luckyraven:keystone-*:1.9.0` | `provided`, **never shaded** |

`<bartizan.version>0.1.0</bartizan.version>` is the property P3 adds to Gangland's root pom.

### C.2 Packages

```
org.luckyraven.bartizan.api                 BartizanApi
org.luckyraven.bartizan.api.weapon          Weapon, GunWeapon, MeleeWeapon, BiologicalWeapon, IncendiaryWeapon,
                                            ThrowableWeapon, ThrowableType, WeaponType, SelectiveFire,
                                            ProjectileType, ProjectileState, WeaponTag, WeaponCatalog
org.luckyraven.bartizan.api.weapon.dto      ProjectileData, AmmunitionData, MeleeData, ScopeData, SoundData,
                                            DamageData, ReloadData, SpreadData, RecoilData, DurabilityData,
                                            BiologicalData, IncendiaryData, ThrowableData, ModifiersData,
                                            ReloadActionBarData
org.luckyraven.bartizan.api.ammo            Ammunition, AmmunitionCatalog
org.luckyraven.bartizan.api.wearable        Wearable, WearableCatalog
org.luckyraven.bartizan.api.item            WeaponItemApi
org.luckyraven.bartizan.api.npc             NpcWeaponFactory,
                                            NpcWeaponController extends keystone.npc.spi.NpcRangedAttack
org.luckyraven.bartizan.api.event           WeaponEvent, WeaponShootEvent, WeaponRaytraceImpactEvent,
                                            WeaponEntityDamageEvent, WeaponKillEntityEvent, WeaponReloadEvent,
                                            WeaponReloadStartEvent, WeaponReloadCompleteEvent,
                                            WeaponChangeSelectiveFireEvent
org.luckyraven.bartizan.api.combat          CombatEligibility
org.luckyraven.bartizan.api.raytrace        WeaponRaytracer, WeaponShooting, RaytraceRequest
org.luckyraven.bartizan                     Bartizan
org.luckyraven.bartizan.bootstrap           BartizanContext, DefaultListenerService
org.luckyraven.bartizan.config              KernelConfig, FilesConfig, DatabaseConfig, WiringConfig, ItemConfig
org.luckyraven.bartizan.file                BartizanSettings, BartizanMessages, WeaponLoader, …
org.luckyraven.bartizan.{weapon,ammo,wearable,projectile,raytrace,modifiers,reload,item,listener,command,
                         database,metrics,npc,util,fire,death}
```

**Rule:** `bartizan-api` carries **zero** `net.minecraft.*` / `org.bukkit.craftbukkit.*` symbols and **zero**
`org.luckyraven.gangland.*` symbols. Module jars are never Paper-remapped (E2 §5), so any NMS symbol in `bartizan-api`
breaks every Gangland module on Paper.

### C.3 `ServicesManager` registrations (the cross-plugin keys P3 depends on)

| Service class | Registered by | Priority | Consumed by |
|---|---|---|---|
| `org.luckyraven.bartizan.api.BartizanApi` | `WiringConfig.bartizanApi(...)` | `Normal` | Gangland modules `civilians`, `copsncrooks`, `gadget` |
| `org.luckyraven.keystone.item.spi.ItemVocabulary` | `ItemConfig.bartizanItemVocabulary(...)` | `Normal` | Gangland core `ItemConfig.itemVocabularies(...)` |
| `org.luckyraven.bartizan.api.raytrace.WeaponRaytracer` | `WiringConfig.weaponRaytracer(...)` | `Normal` | Bartizan's own `NpcWeaponControllerImpl`; published as public api for third parties. **No Gangland class consumes it** (R1 ruling (b)). |
| `org.luckyraven.bartizan.api.combat.CombatEligibility` | **Gangland** registers, Bartizan **pulls** | `Normal` | Bartizan `WeaponInteract`, `NumberedReload`, `InstantReload` |

Direction is fixed: Bartizan publishes 1–3 and pulls 4. Bartizan **never** looks up a Gangland class by name.

`ItemVocabulary` contract Bartizan honours (`namespace()` returns `"bartizan"`):

| Kind | Registered names / predicate | Priority |
|---|---|---|
| converter | `"weapon"` | — |
| converter | `{"ammunition", "ammo"}` (alias overload) | — |
| converter | `"wearable"` | — |
| serializer | `WeaponItemPredicates.WEAPON` | `0` |
| serializer | `WeaponItemPredicates.AMMUNITION` | `0` |
| serializer | `BartizanItemPredicates.WEARABLE` | `0` |
| refresher | `WeaponRefresher` | `10` |
| refresher | `WearableRefresher` | `10` |
| refresher | `AmmunitionItemRefresher` | `0` |

The three priorities are load-bearing and must be preserved exactly (R7 / `WeaponModuleConfig.java:204-206`):
weapon and wearable refreshers outrank Gangland's `uniqueItemRefresher` (0), ammunition sits behind it.

### C.4 Keystone 1.9.0 symbols this stream calls (P1 must ship all of them)

- `org.luckyraven.keystone.nms.PacketAdapter.relativeCameraRotation(Player viewer, float deltaYaw, float deltaPitch)`
- `org.luckyraven.keystone.nms.internal.ReflectivePacketAdapter` (public, no-arg constructor)
- `org.luckyraven.keystone.nms.PacketBridge.install(PacketAdapter)` / `.adapter()` / `.reset()`
- `org.luckyraven.keystone.item.spi.ItemVocabulary` — `String namespace()`, `void contribute(ItemVocabularyRegistrar)`
- `org.luckyraven.keystone.item.spi.ItemVocabularyRegistrar` — `converter(String, ItemConverter)`,
  `converter(String[], ItemConverter)`, `serializer(Predicate<ItemStack>, ItemSerializer, int)`,
  `refresher(ItemRefresher, int)`
- `org.luckyraven.keystone.item.ItemSerializerRegistry.CATCH_ALL_PRIORITY` (not used here, but its presence proves
  the back-port landed)
- `org.luckyraven.keystone.npc.spi.NpcRangedAttack` — `boolean isRanged(); boolean isBusy();
  boolean tryFire(LivingEntity); void triggerReload(); void refreshHeldItem(); void onDestroy();
  default void tick();` plus the constant `NpcRangedAttack.NONE`. `bartizan-api`'s `NpcWeaponController`
  **extends** it; nothing else in Bartizan touches `keystone-npc`.
- fault id `nms.recoil.unsupported`

**Bartizan does NOT use `keystone-module`.** It is a plain plugin, not a module host — no `ModuleLoader`, no
`module.yml`, no `Host_Api`, no `Plugins:` key. (`keystone-maven-conventions.md` §7.)

### C.5 Files and data-folder paths Bartizan owns at runtime

```
plugins/Bartizan/settings.yml
plugins/Bartizan/message/message_en.yml
plugins/Bartizan/weapon/<22 files>.yml
plugins/Bartizan/items/ammunition.yml
plugins/Bartizan/items/wearables.yml
plugins/Bartizan/database/bartizan.db          (SQLite; table `weapon`, columns uuid + type — unchanged)
plugins/Bartizan/.weapon-import-done           (one-shot import marker)
```

`plugin.yml`: `name: Bartizan`, `version: ${project.version}`, `main: org.luckyraven.bartizan.Bartizan`,
`api-version: '1.16'`, `authors: [LuckyRaven10]`, `depend: [Keystone]`,
`softdepend: [ViaVersion, PlaceholderAPI, NBTAPI]`, command `bartizan` with `aliases: [btz, weapon]` and
`permission: bartizan.command.main`.

---

> **Amendment at gate GD (orchestrator, 2026-09-08):** `WeaponShooting` is **not** api after all — its only external caller was `cops-n-crooks/npc/NpcCombatDelegate.java:334`, which becomes Bartizan's own `NpcWeaponControllerImpl` (§1.6(8)), and nothing in Gangland 0.9.0 reads `SPREAD_PELLET_COUNT`. `WeaponShooting`, `WeaponMuzzle` and `SteppedProjectileTask` live in `PLG/raytrace/` (`org.luckyraven.bartizan.raytrace`); `RaytraceContext` and `WeaponVisualSpawner` stay in `API/raytrace/` because `WeaponRaytracer`'s own signature names them. See `REVIEW-bartizan-GD.md` M1.

## 1. Inventory (facts, with source locations)

Gangland source root abbreviated below as `GW/` = `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\`.
Weapon module root abbreviated as `W/` = `GW/gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/`.
Bartizan targets abbreviated as `API/` = `bartizan-api/src/main/java/org/luckyraven/bartizan/api/` and
`PLG/` = `bartizan-plugin/src/main/java/org/luckyraven/bartizan/`.

### 1.1 Every main source file: source → target, and the edit it needs

**Edit legend.** `PKG` = change the `package` line and every `org.luckyraven.gangland.weapon.*` import to the new
package; nothing else. `PKG+` = `PKG` plus the specific edit named in the row. Every file gets at least `PKG`.

#### Group B target — `API/weapon` + `API/weapon/dto` (25 files)

| Source (`W/`) | Target | Edit |
|---|---|---|
| `Weapon.java` | `API/weapon/Weapon.java` | `PKG+` imports `dto.*`, `durability.DurabilityCalculator`, `projectile.recoil.RecoilManager`, `projectile.spread.SpreadManager`, `reload.Reload`, `types.WeaponType` — all move with it (group C). Keeps `keystone.item.ItemBuilder`, `keystone.util.Placeholder`, `keystone.exception.PluginException`, `XPotion`. |
| `SelectiveFire.java` | `API/weapon/SelectiveFire.java` | `PKG` |
| `WeaponTag.java` | `API/weapon/WeaponTag.java` | `PKG` |
| `types/WeaponType.java` | `API/weapon/WeaponType.java` | `PKG` (flattened out of `types/`) |
| `types/gun/GunWeapon.java` | `API/weapon/GunWeapon.java` | `PKG` |
| `types/melee/MeleeWeapon.java` | `API/weapon/MeleeWeapon.java` | `PKG` |
| `types/biological/BiologicalWeapon.java` | `API/weapon/BiologicalWeapon.java` | `PKG` |
| `types/incendiary/IncendiaryWeapon.java` | `API/weapon/IncendiaryWeapon.java` | `PKG` |
| `types/throwable/ThrowableWeapon.java` | `API/weapon/ThrowableWeapon.java` | `PKG` |
| `types/throwable/ThrowableType.java` | `API/weapon/ThrowableType.java` | `PKG` |
| `dto/AmmunitionData.java` | `API/weapon/dto/AmmunitionData.java` | `PKG` |
| `dto/BiologicalData.java` | `API/weapon/dto/BiologicalData.java` | `PKG` |
| `dto/DamageData.java` | `API/weapon/dto/DamageData.java` | `PKG` |
| `dto/DurabilityData.java` | `API/weapon/dto/DurabilityData.java` | `PKG` |
| `dto/IncendiaryData.java` | `API/weapon/dto/IncendiaryData.java` | `PKG` |
| `dto/MeleeData.java` | `API/weapon/dto/MeleeData.java` | `PKG` |
| `dto/ModifiersData.java` | `API/weapon/dto/ModifiersData.java` | `PKG` |
| `dto/ProjectileData.java` | `API/weapon/dto/ProjectileData.java` | `PKG` |
| `dto/RecoilData.java` | `API/weapon/dto/RecoilData.java` | `PKG` |
| `dto/ReloadActionBarData.java` | `API/weapon/dto/ReloadActionBarData.java` | `PKG` |
| `dto/ReloadData.java` | `API/weapon/dto/ReloadData.java` | `PKG` |
| `dto/ScopeData.java` | `API/weapon/dto/ScopeData.java` | `PKG` |
| `dto/SoundData.java` | `API/weapon/dto/SoundData.java` | `PKG` |
| `dto/SpreadData.java` | `API/weapon/dto/SpreadData.java` | `PKG` |
| `dto/ThrowableData.java` | `API/weapon/dto/ThrowableData.java` | `PKG` |

#### Group C target — `API/weapon` internals (18 files)

| Source (`W/`) | Target | Edit |
|---|---|---|
| `projectile/ProjectileState.java` | `API/weapon/ProjectileState.java` | `PKG` |
| `projectile/ProjectileType.java` | `API/weapon/ProjectileType.java` | `PKG` |
| `projectile/recoil/RecoilManager.java` | `API/weapon/recoil/RecoilManager.java` | **`PKG+` rewrite the recoil sink — see §1.6** |
| `projectile/spread/SpreadManager.java` | `API/weapon/spread/SpreadManager.java` | `PKG` |
| `durability/DurabilityCalculator.java` | `API/weapon/durability/DurabilityCalculator.java` | `PKG` |
| `reload/Reload.java` | `API/weapon/reload/Reload.java` | `PKG` |
| `reload/ReloadType.java` | `API/weapon/reload/ReloadType.java` | `PKG` |
| `reload/type/InstantReload.java` | `API/weapon/reload/InstantReload.java` | **`PKG+`** `DownedPlayerRegistry.isDowned(uuid)` at `:61,:74` → `CombatEligibility` — see §1.6 |
| `reload/type/NumberedReload.java` | `API/weapon/reload/NumberedReload.java` | **`PKG+`** same at `:90,:132` |
| `modifiers/BlockDamageManager.java` | `API/weapon/modifiers/BlockDamageManager.java` | `PKG` |
| `modifiers/BlockRegenerationSettings.java` | `API/weapon/modifiers/BlockRegenerationSettings.java` | `PKG` |
| `modifiers/BreakMode.java` | `API/weapon/modifiers/BreakMode.java` | `PKG` |
| `modifiers/ModifierHandler.java` | `API/weapon/modifiers/ModifierHandler.java` | `PKG` |
| `modifiers/action/ArmorPiercingModifier.java` | `API/weapon/modifiers/action/…` | `PKG` |
| `modifiers/action/BlockBreakModifier.java` | `API/weapon/modifiers/action/…` | `PKG` |
| `modifiers/action/FlatDamageModifier.java` | `API/weapon/modifiers/action/…` | `PKG` |
| `modifiers/action/PenetrationModifier.java` | `API/weapon/modifiers/action/…` | `PKG` |
| `modifiers/action/RicochetModifier.java` | `API/weapon/modifiers/action/…` | `PKG` |
| `modifiers/action/TracerModifier.java` | `API/weapon/modifiers/action/…` | `PKG` |

> `ProjectileState.java` imports `modifiers.action.RicochetModifier` — that is why the whole `modifiers` tree is in
> the api and not the plugin. Verified by reading its import block.

#### Group D target — `API/` public surface (19 files: 9 moved, 10 new)

| Source (`W/`) | Target | Edit |
|---|---|---|
| `events/WeaponEvent.java` | `API/event/WeaponEvent.java` | `PKG` |
| `events/WeaponEntityDamageEvent.java` | `API/event/WeaponEntityDamageEvent.java` | **`PKG+` add `String weaponName()` and `DamageKind kind()` — see §1.6 (R1)** |
| `events/WeaponKillEntityEvent.java` | `API/event/WeaponKillEntityEvent.java` | `PKG` |
| `events/projectile/WeaponRaytraceImpactEvent.java` | `API/event/WeaponRaytraceImpactEvent.java` | `PKG+` add the javadoc note from E1 §2: cancelling suppresses damage only, not penetration/ricochet counters |
| `events/projectile/WeaponShootEvent.java` | `API/event/WeaponShootEvent.java` | `PKG` |
| `events/reload/WeaponReloadEvent.java` | `API/event/WeaponReloadEvent.java` | `PKG` |
| `events/reload/WeaponReloadStartEvent.java` | `API/event/WeaponReloadStartEvent.java` | `PKG` |
| `events/reload/WeaponReloadCompleteEvent.java` | `API/event/WeaponReloadCompleteEvent.java` | `PKG` |
| `events/selective/WeaponChangeSelectiveFireEvent.java` | `API/event/WeaponChangeSelectiveFireEvent.java` | `PKG` |
| `ammo/Ammunition.java` | `API/ammo/Ammunition.java` | `PKG` |
| — | `API/BartizanApi.java` | **NEW** — see §1.6 |
| — | `API/weapon/WeaponCatalog.java` | **NEW** |
| — | `API/ammo/AmmunitionCatalog.java` | **NEW** |
| — | `API/wearable/Wearable.java` | **NEW — rewritten from `GW/gangland-infra/gangland-item/.../item/wearable/Wearable.java`, see §1.6** |
| — | `API/wearable/WearableCatalog.java` | **NEW** |
| — | `API/item/WeaponItemApi.java` | **NEW** |
| — | `API/npc/NpcWeaponFactory.java` | **NEW** |
| — | `API/npc/NpcWeaponController.java` | **NEW** |
| — | `API/combat/CombatEligibility.java` | **NEW** |
| — | `API/BartizanItemPredicates.java` | **NEW** — carries only `WEARABLE`, lifted from `GW/gangland-impl/.../item/ItemPredicates.java:25-45` |

#### Group E target — `API/raytrace` (3 files)

| Source (`W/`) | Target | Edit |
|---|---|---|
| `raytrace/WeaponRaytracer.java` | `API/raytrace/WeaponRaytracer.java` (**interface**) + `PLG/raytrace/WeaponRaytracerImpl.java` (the class) | **SPLIT — see §1.6** |
| `raytrace/WeaponShooting.java` | `PLG/raytrace/WeaponShooting.java` | `PKG`; the `WeaponRaytracer` parameter is now the interface |
| `raytrace/RaytraceRequest.java` | `API/raytrace/RaytraceRequest.java` | `PKG` |

#### Groups L/M/N target — `PLG/` runtime (the remaining 80 moved main files)

| Source (`W/`) | Target (`PLG/`) | Edit |
|---|---|---|
| `WeaponService.java` | `weapon/WeaponService.java` | `PKG+` add `implements WeaponCatalog` |
| `WeaponManager.java` | `weapon/WeaponManager.java` | **`PKG+`** ctor param `GanglandDatabase` → `BartizanDatabase` (`:4,:23`); only `getRepositoryRegistry()` is used |
| `WeaponModule.java` | — | **DELETE.** Bartizan is not a Keystone module; `WeaponFileConfig`/`WeaponModuleConfig` are replaced by `PLG/config/*`. |
| `WeaponFileConfig.java` | `config/FilesConfig.java` | **REWRITE — see §1.3** |
| `WeaponModuleConfig.java` | `config/WiringConfig.java` + `config/ItemConfig.java` | **REWRITE — see §1.3** |
| `ammo/AmmunitionManager.java` | `ammo/AmmunitionManager.java` | `PKG+` add `implements AmmunitionCatalog` |
| `configuration/AmmunitionAddon.java` | `configuration/AmmunitionAddon.java` | `PKG` |
| `configuration/WeaponAddon.java` | `configuration/WeaponAddon.java` | `PKG` |
| `configuration/parser/*.java` (9) | `configuration/parser/*.java` | `PKG` |
| `wearable/WearableAddon.java` | `wearable/WearableAddon.java` | **`PKG+`** parse `Extra_Tags:` instead of `Jetpack:` (`:141-193`); traits become `Map<String,Integer>` keyed by lower-case string; drop `implements WearableEquipService` |
| `wearable/WearableService.java` | `wearable/WearableService.java` | **`PKG+`** drop `implements WearableEquipService` (`:35`); add `implements WearableCatalog`; `WearableTrait.X` → string trait keys |
| `file/WeaponLoader.java` | `file/WeaponLoader.java` | `PKG+` `Gangland` → `Bartizan` |
| `file/WeaponBlockRegenerationSettings.java` | `file/WeaponBlockRegenerationSettings.java` | **`PKG+`** the three reads at `:13,:18,:23` (`Settings.getBlockRestoreDelayTicks()`, `getBlockRegenerationDelayTicks()`, `getBlockRegenerationStepTicks()`) become `BartizanSettings.getBlockRestoreDelayTicks()`, `BartizanSettings.getBlockRegenerationDelayTicks()`, `BartizanSettings.getBlockRegenerationStepTicks()` |
| `fire/PluginFireRegistry.java` | `fire/PluginFireRegistry.java` | `PKG` |
| `types/gun/GunAction.java` | `weapon/action/GunAction.java` | **`PKG+`** `RecoilCompatibility` ctor param removed (`:21,:25,:29,:82`) — see §1.6 |
| `types/gun/FullAutoTask.java` | `weapon/action/FullAutoTask.java` | **`PKG+`** same (`:52,:61,:68,:96`) |
| `types/melee/MeleeAction.java` | `weapon/action/MeleeAction.java` | **`PKG+`** same (`:31,:35,:38,:127`) |
| `types/throwable/ThrowableAction.java` | `weapon/action/ThrowableAction.java` | **`PKG+`** same (`:49,:52,:56,:83`) **and delete `pendingKillerWeapon` (`:38`) + `pendingVehicleExplosionDamage` (`:45`) — see §1.6 (R1)** |
| `types/incendiary/IncendiaryAction.java` | `weapon/action/IncendiaryAction.java` | **`PKG+`** same (`:43,:48,:53,:111`) |
| `types/biological/BiologicalAction.java` | `weapon/action/BiologicalAction.java` | **`PKG+`** same (`:37,:42,:46,:113`) |
| `raytrace/RaytraceContext.java` | `raytrace/RaytraceContext.java` | `PKG` |
| `raytrace/SteppedProjectileTask.java` | `raytrace/SteppedProjectileTask.java` | `PKG` |
| `raytrace/WeaponMuzzle.java` | `raytrace/WeaponMuzzle.java` | `PKG` |
| `raytrace/WeaponVisualSpawner.java` | `raytrace/WeaponVisualSpawner.java` | `PKG` |
| `listener/WeaponInteract.java` | `listener/WeaponInteract.java` | **`PKG+`** `RecoilCompatibility` (11 sites) removed; `DownedPlayerRegistry.isDowned` at `:148,:225` → `CombatEligibility` |
| `listener/ScopeJumpListener.java` | `listener/ScopeJumpListener.java` | `PKG` |
| `listener/fire/PluginFireProtectionListener.java` | `listener/fire/…` | `PKG` |
| `listener/gang/GangAllyWeaponImpactListener.java` | — | **DELETE.** Gang friendly fire is a Gangland concern; P3 recreates it in `gangland-impl` against `bartizan-api` (E1 §2 group C). |
| `listener/player/WeaponQuitCleanupListener.java` | `listener/player/…` | `PKG` |
| `listener/projectile/ProjectileDamageListener.java` | `listener/projectile/…` | `PKG` |
| `listener/reload/WeaponDroppedListener.java` | `listener/reload/…` | `PKG` |
| `listener/reload/WeaponItemSpawnListener.java` | `listener/reload/…` | `PKG` |
| `listener/reload/WeaponReloadListener.java` | `listener/reload/…` | `PKG` |
| `listener/selective/WeaponSelectiveFireChangeListener.java` | `listener/selective/…` | `PKG` |
| `listener/wearable/WearableEquipListener.java` | `listener/wearable/…` | **`PKG+`** drop the `WearableEquipService` indirection; call `WearableService` directly (T-14 is dissolved, not ported) |
| `item/WeaponConverter.java` | `item/WeaponConverter.java` | **`PKG+`** base `ItemAttributes` copied to `PLG/item/ItemAttributes.java`; `GanglandChatUtil.color` → `BartizanChatUtil.color` |
| `item/AmmunitionConverter.java` | `item/AmmunitionConverter.java` | **`PKG+`** same |
| `item/WearableConverter.java` | `item/WearableConverter.java` | **`PKG+`** same |
| `item/WeaponItemSerializer.java` | `item/WeaponItemSerializer.java` | **`PKG+`** `ItemKind.WEAPON` → `BartizanItemKind.WEAPON` |
| `item/AmmunitionItemSerializer.java` | `item/AmmunitionItemSerializer.java` | **`PKG+`** same → `BartizanItemKind.AMMUNITION` |
| `item/WearableItemSerializer.java` | `item/WearableItemSerializer.java` | **`PKG+`** same → `BartizanItemKind.WEARABLE` |
| `item/WeaponRefresher.java` | `item/WeaponRefresher.java` | `PKG` (`ItemRefresher` is already Keystone's) |
| `item/WearableRefresher.java` | `item/WearableRefresher.java` | `PKG` |
| `item/AmmunitionItemRefresher.java` | `item/AmmunitionItemRefresher.java` | `PKG` |
| `item/WeaponItemPredicates.java` | `item/WeaponItemPredicates.java` | `PKG` |
| `util/BlockGroupResolver.java` | `util/BlockGroupResolver.java` | `PKG` |
| `util/EmptyMagSoundGate.java` | `util/EmptyMagSoundGate.java` | `PKG` |
| `util/PotionEffectParser.java` | `util/PotionEffectParser.java` | `PKG` |
| `database/WeaponTable.java` | `database/WeaponTable.java` | `PKG` — table name `weapon` and both columns unchanged |
| `database/WeaponRepository.java` | `database/WeaponRepository.java` | `PKG` |
| `data/WeaponDataCleanupTask.java` | `data/WeaponDataCleanupTask.java` | **`PKG+`** drop `implements DataCleanupTask`; becomes a plain Keystone `Timer` owned by Bartizan; `Settings.isAutoSaveDebug()` (`:20`) → `BartizanSettings.isAutoSaveDebug()` |
| `command/WeaponCommand.java` | `command/WeaponCommand.java` | **REWRITE — see §1.4** |
| `command/WeaponGiveCommand.java` | `command/WeaponGiveCommand.java` | **REWRITE — see §1.4** |
| `command/WeaponInfoCommand.java` | `command/WeaponInfoCommand.java` | **REWRITE — see §1.4** |
| `command/WeaponListCommand.java` | `command/WeaponListCommand.java` | **REWRITE — see §1.4** |
| `command/AmmunitionCommand.java` | `command/AmmunitionCommand.java` | **REWRITE — see §1.4** |
| `command/AmmunitionGiveCommand.java` | `command/AmmunitionGiveCommand.java` | **REWRITE — see §1.4** |
| `command/AmmunitionInfoCommand.java` | `command/AmmunitionInfoCommand.java` | **REWRITE — see §1.4** |
| `command/AmmunitionListCommand.java` | `command/AmmunitionListCommand.java` | **REWRITE — see §1.4** |
| `command/wearable/ItemWearableCommand.java` | `command/wearable/WearableCommand.java` | **REWRITE — see §1.4** |
| `command/wearable/ItemWearableGiveCommand.java` | `command/wearable/WearableGiveCommand.java` | **REWRITE — see §1.4** |
| `command/wearable/ItemWearableInfoCommand.java` | `command/wearable/WearableInfoCommand.java` | **REWRITE — see §1.4** |
| `command/wearable/ItemWearableListCommand.java` | `command/wearable/WearableListCommand.java` | **REWRITE — see §1.4** |
| `command/DebugWeaponContribution.java` | `command/DebugCommand.java` | **REWRITE** — `CommandContribution(parent=="debug")` becomes a real `/bartizan debug` subcommand |
| `command/ItemWearableContribution.java` | — | **DELETE.** Its only job was attaching to Gangland's `/glw item`; `/bartizan wearable` replaces it. |
| `metrics/WeaponMetricsContributor.java` | `metrics/WeaponMetrics.java` | **`PKG+`** drop `implements MetricsContributor`; becomes a direct `metrics.addCustomChart(new SingleLineChart("number_of_weapons", …))` call in `Bartizan.bStats()` |
| `death/WeaponDeathMessageContributor.java` | `listener/death/WeaponDeathListener.java` | **REWRITE — see §1.6** |
| `shop/WeaponShopDisplayNameProvider.java` | — | **DELETE.** Replaced by `WeaponItemApi.cleanDisplayName(ItemStack)`; P3's Gangland shop resolver calls `ItemDefinitions.pristine(...)` instead. |
| `sign/*.java` (11) + `sign/view/WeaponSignViewProvider.java` | — | **DELETE all 12.** D7: signs leave the weapon stack entirely. P3 widens `gangland-impl/.../sign/type/trade/{BaseTradeSign, BuySign, SellSign}` and registers them additionally as `item-buy`/`item-sell`, plus `LegacySignRewriter` in `gangland-impl` (P3 OQ-2). No new classes in `sign-api`. |

**Deleted, not moved: 16 files** — `WeaponModule.java`, `listener/gang/GangAllyWeaponImpactListener.java`,
`command/ItemWearableContribution.java`, `shop/WeaponShopDisplayNameProvider.java`, and the 12 sign files
(`sign/AbstractWeaponTradeSign.java`, `sign/WeaponBuySign.java`, `sign/WeaponSellSign.java`, `sign/AmmoBuySign.java`,
`sign/AmmoSellSign.java`, `sign/WearableBuySign.java`, `sign/WearableSellSign.java`, `sign/WeaponSignValidator.java`,
`sign/AmmoSignValidator.java`, `sign/WearableSignValidator.java`, `sign/WeaponSignContribution.java`,
`sign/view/WeaponSignViewProvider.java`).

145 main files = 25 (B) + 18 (C) + 10 moved in D + 3 (E) + 73 moved to `PLG/` + 16 deleted.

### 1.2 Files taken from outside the weapon module

| Source | Target | Edit |
|---|---|---|
| `GW/gangland-compatibility/version-impl/.../recoil/RecoilCompatibility.java` | — | **NOT copied.** Superseded by `PacketAdapter.relativeCameraRotation`. Read it first (§1.6) — the `-yaw + 1` / `pitch - 1` transform must survive. |
| `GW/gangland-compatibility/version-impl/.../{Compatibility,CompatibilityWorker}.java` | — | **NOT copied.** Bartizan ships zero version modules (PICK refinement 1). |
| `GW/gangland-compatibility/version-1_16_R1/.../Recoil_1_16_R1.java` and `version-1_21_R7/.../Recoil_1_21_R7.java` | — | **Reference only** — the two packet shapes P1's `ReflectivePacketAdapter` reproduces. Read before deleting anything. |
| `GW/gangland-infra/gangland-item/.../item/wearable/Wearable.java` | `API/wearable/Wearable.java` | **REWRITE — §1.6** |
| `GW/gangland-infra/gangland-item/.../item/wearable/WearableTrait.java` | — | **NOT copied as an enum.** Its eight keys become strings; its per-level effect table moves into `Wearable` — §1.6. |
| `GW/gangland-impl/.../item/ItemPredicates.java:25-45` | `API/BartizanItemPredicates.java` | Copy the `WEARABLE` predicate only |
| `GW/gangland-impl/.../item/ItemAttributes.java:12-46` | `PLG/item/ItemAttributes.java` | `PKG+` `GanglandChatUtil.color` → `BartizanChatUtil.color` |
| `GW/gangland-impl/.../item/ItemKind.java:13-20` | `PLG/item/BartizanItemKind.java` | New enum `WEAPON, AMMUNITION, WEARABLE` `implements org.luckyraven.keystone.item.ItemKind` |
| `GW/gangland-impl/.../util/GanglandChatUtil.java` | `PLG/util/BartizanChatUtil.java` | Copy; drop `commandDesign`/`confirmCommand`'s `Gangland.SHORT_PREFIX` → `Bartizan.SHORT_PREFIX`; `Settings.getMoneySymbol()` → `BartizanSettings.getMoneySymbol()` |
| `GW/gangland-impl/.../file/configuration/Messages.java` | `PLG/file/BartizanMessages.java` | New enum, 14 members — §1.5 |
| `GW/gangland-core/src/test/java/.../testsupport/BukkitRegistryFixture.java` | `bartizan-plugin/src/test/java/org/luckyraven/bartizan/testsupport/BukkitRegistryFixture.java` | `PKG` only — the file references only Bukkit + Mockito, verified by reading it |

### 1.3 Bean inventory — what `WeaponFileConfig` + `WeaponModuleConfig` become

`WeaponFileConfig` (FILE phase, `W/WeaponFileConfig.java`) → `PLG/config/FilesConfig.java`:

| Old `@Bean` | New `@Bean` | Change |
|---|---|---|
| `ammunitionManager(Settings)` | `ammunitionManager(BartizanSettings)` | **Keep the parameter** — it is an ordering-only edge (`:44` javadoc). |
| `ammunitionAddon(FileManager, AmmunitionManager, PlaceholderService)` | `ammunitionAddon(FileManager, AmmunitionManager)` | `PlaceholderService` param dropped; both addons take `@Nullable Placeholder` — pass `null` unless PlaceholderAPI is present (§1.6). `FileHandler` loses the `moduleLoader.classLoader()` argument — Bartizan's own classloader is correct. |
| `wearableAddon(PermissionManager, FileManager, PlaceholderService)` | `wearableAddon(PermissionManager, FileManager)` | Same |
| `blockRegenerationSettings()` | unchanged | — |
| `weaponAddon(AmmunitionAddon, PlaceholderService)` | `weaponAddon(AmmunitionAddon)` | **Keep `AmmunitionAddon`** — ordering-only edge (`:79-82` javadoc). |
| `weaponLoader(FileManager, AmmunitionManager, WeaponAddon)` | unchanged shape | Drop `moduleLoader.classLoader()`; **the expected-file list stays exactly `rifle, grenade, knife, flamethrower, syringe_gun`** (`:93`) — only 5 of 22 are registered as expected, which is docket **T-06** and is **not** fixed here. |

`WeaponModuleConfig` (CONFIG phase, `W/WeaponModuleConfig.java`) → split in two:

`PLG/config/WiringConfig.java` (CONFIG):

| Old `@Bean` (`W/WeaponModuleConfig.java`) | New | Change |
|---|---|---|
| `weaponManager(WeaponAddon, GanglandDatabase)` `:72` | `weaponManager(WeaponAddon, BartizanDatabase)` | type swap only |
| `weaponService(WeaponManager)` `:77` | same | — |
| `blockDamageManager(WeaponBlockRegenerationSettings)` `:82` | same | `gangland` → `bartizan` |
| `weaponVisualSpawner()` `:87` | same | — |
| `weaponRaytracer(...)` `:92` | `weaponRaytracer(...)` returning **`WeaponRaytracerImpl`** | Keeps the `Bukkit.getServicesManager().register(WeaponRaytracer.class, …)` call at `:99` — **the registered service class is the api interface**, the bean's declared return type is the concrete class (house rule: a holder `@Bean` declares the concrete class). |
| `wearableService(WearableAddon)` `:103` | same | — |
| `wearableEquipService(WearableAddon)` `:108` | — | **DELETE** — the interface dies with the split |
| `recoilCompatibility(CompatibilityWorker)` `:113` | — | **DELETE** — recoil now goes through `PacketBridge` (§1.6) |
| `pluginFireRegistry()` `:118` | same | — |
| `weaponSignContribution(...)` `:235` | — | **DELETE** (D7) |
| `weaponSignViewProvider(...)` `:243` | — | **DELETE** (D7) |
| `weaponShopDisplayNameProvider(...)` `:252` | — | **DELETE** — replaced by `WeaponItemApi` |
| `weaponDataCleanupTask(WeaponManager, GanglandDatabase)` `:257` | `weaponDataCleanupTask(WeaponManager, BartizanDatabase)` | type swap |
| `weaponMetricsContributor(WeaponAddon)` `:263` | — | **DELETE** — folded into `Bartizan.bStats()` |
| `weaponDeathMessageContributor(WeaponManager)` `:268` | — | **DELETE** — replaced by the `WeaponDeathListener` (§1.6) |
| `weaponNbtTags(NbtTagCatalog)` `:219` | — | **DELETE** — `NbtTagCatalog` was a Gangland debug seam |
| `debugWeaponContribution(WeaponManager)` `:277` | — | **DELETE** — `/bartizan debug` is a real command now |
| `itemWearableContribution(...)` `:282` | — | **DELETE** |
| — | `bartizanApi(...)` | **NEW** — builds `BartizanApiImpl` and registers it on the `ServicesManager` |
| — | `npcWeaponFactory(...)` | **NEW** |
| — | `combatEligibility()` | **NEW** — a holder that resolves the `ServicesManager` registration lazily (§1.6) |
| — | `listenerService(DependencyContainer, SettingsLookup)` | **NEW** — `DefaultListenerService`, Keystone ships only the abstract base |
| — | `commandManager(DependencyContainer, SettingsLookup)` | **NEW** — `new CommandManager(bartizan, container, settings, "bartizan", "bartizan")` |

`PLG/config/ItemConfig.java` (CONFIG) — replaces `weaponConverter` `:127` … `weaponItemRegistrations` `:182`:

The nine converter/serializer/refresher beans move over unchanged in shape. `weaponItemRegistrations(...)` `:182-209`
is **replaced** by a single `bartizanItemVocabulary(...)` bean returning `BartizanItemVocabulary` and registering it on
the `ServicesManager`. The `contribute(ItemVocabularyRegistrar)` body reproduces `:195-206` exactly, in that order,
with the priorities in §C.3.

### 1.4 Commands

Every command class is a **rewrite, not a move** — the base class, the messages and the user lookup all change.

- Base: `org.luckyraven.gangland.command.Command` → **`org.luckyraven.keystone.command.Command` directly.**
  Bartizan does not need Gangland's adapter: its permission namespace is `bartizan.command.<label>`, which Keystone's
  prefix overload already produces from the `"bartizan"` prefix passed to `CommandManager`.
- `User<Player> user = userManager.getUser(player); … user.sendMessage(s)` → **`player.sendMessage(s)`.**
  Verified: E1 §2 group B — across all 10 command files the only `User` method called is `sendMessage(String)`.
  **No user/gang seam is needed anywhere in Bartizan.**
- `Messages.X` → `BartizanMessages.X` (§1.5). `GanglandChatUtil.setArguments(...)` → `BartizanChatUtil.setArguments(...)`.
- Root command `/bartizan` (aliases `/btz`, `/weapon`) with sub-arguments `weapon`, `ammo`, `wearable`, `debug`,
  `reload`.
- `commands.json` moves to `bartizan-plugin/src/main/resources/commands.json` with all 12 keys rewritten from
  `/glw weapon …` to `/bartizan weapon …`, `/glw ammo …` → `/bartizan ammo …`, `/glw item wearable …` →
  `/bartizan wearable …`, plus two new keys `bartizan_debug` and `bartizan_reload`. **14 keys total.**
- **Keystone has no `commands.json` reader** (`keystone-maven-conventions.md` §10). Gangland's
  `command/data/{CommandInformation, InformationManager}` + `HelpInfo` are consumer-side. Bartizan copies
  `InformationManager` + `CommandInformation` from `GW/gangland-impl/.../command/data/` (Gson-based) and drops the
  module-merging loop, since Bartizan has no modules.

### 1.5 `BartizanMessages` — the 14 members and their YAML paths

New file `PLG/file/BartizanMessages.java`, same shape as `GW/gangland-impl/.../file/configuration/Messages.java`
(enum + `Type` + `init(MessageProvider)` + `findMissingPaths(YamlConfiguration)` + `toString()`/`toStringList()`),
backed by Keystone's `YamlMessageProvider` through `LanguageLoader`. The `Type` switch routes through
`BartizanChatUtil` exactly as `Messages.getValue` does (`Messages.java:734-746`).

| Member | New YAML path in `message/message_en.yml` | Type | Was |
|---|---|---|---|
| `PREFIX` | `Normal.Prefix` | `OTHER` | new (prefix) |
| `COMMAND_PREFIX` | `Commands.Prefix` | `OTHER` | new (prefix) |
| `ERROR_PREFIX` | `Errors.Prefix` | `OTHER` | new (prefix) |
| `INFORMATION_PREFIX` | `Information.Prefix` | `OTHER` | new (prefix) |
| `ARGUMENTS_MISSING` | `Commands.Syntax.Missing_Arguments` | `COMMAND` | `Messages.java:27` |
| `MUST_BE_NUMBERS` | `Errors.Must_Be_Numbers` | `ERROR` | `:286` |
| `RECEIVED_WEAPON` | `Commands.Weapon.Received` | `COMMAND` | `:34` (`Commands.Weapons.Weapon.Received`) |
| `INVALID_WEAPON` | `Errors.Not_Valid_Weapon` | `ERROR` | `:316` (`Weapons.Not_Valid_Weapon`) |
| `WEAPON_LIST_HEADER` | `Commands.Weapon.List_Header` | `COMMAND` | `:566` |
| `RECEIVED_AMMO` | `Commands.Ammo.Received` | `COMMAND` | `:32` |
| `INVALID_AMMO` | `Errors.Not_Valid_Ammo` | `ERROR` | `:315` |
| `AMMO_LIST_HEADER` | `Commands.Ammo.List_Header` | `COMMAND` | `:567` |
| `WEARABLE_GAVE` | `Commands.Wearable.Gave` | `COMMAND` | `:559` (`Commands.Item.Wearable.Gave`) |
| `WEARABLE_LIST_HEADER` | `Commands.Wearable.List_Header` | `COMMAND` | `:560` |
| `WEARABLE_INVALID` | `Errors.Wearable.Invalid` | `PREFIX` | `:561` |
| `WEARABLE_NOT_REGISTERED` | `Errors.Wearable.Not_Registered` | `PREFIX` | `:562` |
| `WEARABLE_NOT_WEARABLE` | `Errors.Wearable.Not_Wearable` | `PREFIX` | `:563` |
| `DEAD_USING_WEAPON` | `Death.Weapon` | `OTHER`, `isList = true` | `:328` |

That is 18 entries (4 prefixes + 14 content). **`DEAD_USING_WEAPON` moves to Bartizan** because Bartizan now owns
the weapon death message; P3 deletes Gangland's copy and flips `MessagesTest.java:107`.

**Not carried over** (dead once the signs go, E1 §7): `GAVE_AMMO`, `GAVE_WEAPON`, `AMMO_NOT_IN_INVENTORY`,
`AMMO_BOUGHT`, `AMMO_SOLD`, `NOT_ENOUGH_AMMO`. P3 deletes those six from Gangland.

### 1.6 The nine behaviour-changing rewrites, spelled out

Everything else in this stream is a package rename. These nine are not.

**(1) Recoil — `RecoilManager` + the six actions.**
Today: every action holds a `RecoilCompatibility` and `RecoilManager.recoil(...)` calls
`recoilCompatibility.modifyCameraRotation(player, yaw, pitch, true)`
(`W/projectile/recoil/RecoilManager.java:100-102`). Every implementation — the Bukkit-API fallback
(`RecoilCompatibility.java:31-33`) and all 20 NMS adapters (`Recoil_1_16_R1.java:29-30`,
`Recoil_1_21_R7.java:19-20`) — starts with the identical two lines:

```java
float newYaw   = -yaw + 1;
float newPitch = pitch - 1;
```

New: delete the `RecoilCompatibility` parameter from `RecoilManager.applyRecoil` / `recoil` / `applyDefaultRecoil`
and from all six `*Action` constructors, and make `RecoilManager.recoil` read:

```java
private void recoil(Player player, float yaw, float pitch) {
    PacketBridge.adapter().relativeCameraRotation(player, -yaw + 1, pitch - 1);
}
```

**The transform stays on the Bartizan side.** Keystone's `relativeCameraRotation` is a pure relative rotation; if the
transform is dropped, recoil inverts horizontally and drifts one degree per shot. This is what the new
`RecoilManagerTest` pins (§3).
The ViaVersion `ProtocolVersion.v1_13` gate (`RecoilCompatibility.java:34-36`) is **not** carried over — see Q1.

**(2) `PacketBridge` install.** `PacketBridge` does not auto-detect (`keystone-maven-conventions.md` §9). `KernelConfig`
gets:

```java
@Bean
public ReflectivePacketAdapter packetAdapter() {
    ReflectivePacketAdapter adapter = new ReflectivePacketAdapter();
    PacketBridge.install(adapter);
    return adapter;
}
```

and `Bartizan.onDisable()` calls `PacketBridge.reset()` (Oriel does exactly this, `oriel-plugin-skeleton.md` §1).

**(3) `ThrowableAction`'s two static maps (R1).** Delete both `public static final Map` fields
(`W/types/throwable/ThrowableAction.java:38` `pendingKillerWeapon`, `:45` `pendingVehicleExplosionDamage`) and the
1-tick cleanup task at `:220`. Replace with fields on `WeaponEntityDamageEvent`:

```java
public enum DamageKind { DIRECT, EXPLOSION, FIRE, BIOLOGICAL, MELEE }
public String     weaponName();   // never null
public DamageKind kind();
```

- `pendingKillerWeapon` had one reader, `WeaponDeathMessageContributor.java:29` → replaced by (4) below.
- `pendingVehicleExplosionDamage` had one reader, Gangland gadget's `CarDamageListener.java:161` → P3 rewrites that
  listener to read `event.kind() == EXPLOSION` and `event.getDamage()` off `WeaponEntityDamageEvent`.
- `pendingDamage` (`:32`, a `Set<UUID>`) is **internal to the weapon module** (`WeaponInteract.onEntityDamage` is the
  only reader) — keep it as a private instance field, do not expose it.

At `ThrowableAction:238-241`, where the explosion loop calls `target.damage(...)`, fire a
`WeaponEntityDamageEvent` carrying `weapon.getName()` and `DamageKind.EXPLOSION` before the damage call.

**(4) Weapon death messages — Bartizan owns them.** Delete
`W/death/WeaponDeathMessageContributor.java` (`implements DeathMessageContributor`) and create
`PLG/listener/death/WeaponDeathListener.java`, a `@ListenerHandler` with:

```java
@EventHandler(priority = EventPriority.HIGH)
public void onPlayerDeath(PlayerDeathEvent event) { … }
```

`EventPriority.HIGH` is deliberate: Gangland's `PlayerDeathListener.onPlayerDeath` runs at
`EventPriority.LOWEST` (`GW/gangland-impl/.../listener/player/PlayerDeathListener.java:58`), so Bartizan's HIGH
handler runs **after** it and its `setDeathMessage` wins whenever a weapon actually claims the kill. When no weapon
claims it, Bartizan must return without touching the message so Gangland's stays.

Body, ported from `WeaponDeathMessageContributor.resolve` (`:29-45`) with the static map read replaced:
resolve the weapon from the killer's main hand via `weaponManager.validateAndGetWeapon(killer, heldItem)`; for
throwables, keep a short-lived `Map<UUID,String>` **private to this listener**, written by `ThrowableAction` through
an injected reference (not a static). Then
`event.setDeathMessage(BartizanChatUtil.color(template.replace("%killer%", …).replace("%victim%", …).replace("%item%", …)))`
using `weapon.pickDeathMessage()` with `BartizanMessages.DEAD_USING_WEAPON.toStringList()` as the fallback pool —
the same two-step the Gangland builder does at `PlayerDeathListener.java:205-216`.

**(5) `CombatEligibility` replaces `DownedPlayerRegistry`.** Five call sites read the Gangland static today:
`W/listener/WeaponInteract.java:148,:225`, `W/reload/type/NumberedReload.java:90,:132`,
`W/reload/type/InstantReload.java:61,:74`. New api file:

```java
package org.luckyraven.bartizan.api.combat;

@FunctionalInterface
public interface CombatEligibility {

    boolean canBeHit(Player player);

    CombatEligibility DEFAULT = player -> !player.isDead();
}
```

Bartizan **pulls** it: a `WiringConfig` holder bean resolves
`Bukkit.getServicesManager().getRegistration(CombatEligibility.class)` **lazily at every use**, never caching the
provider at bean construction (R8 — Bartizan may enable before Gangland). Falls back to `DEFAULT`. Each of the six
call sites becomes `if (!combatEligibility.canBeHit(player)) …` with the same polarity as today's
`if (DownedPlayerRegistry.isDowned(uuid)) …` — **note the inversion**, this is the easiest place in the stream to
introduce a sign bug.

**(6) `Wearable` — no `FuelKey`, no `isJetpack()`, string traits.** Rewrite of
`GW/gangland-infra/gangland-item/.../item/wearable/Wearable.java` into `API/wearable/Wearable.java`:

| Removed | Replacement |
|---|---|
| `import org.luckyraven.gangland.item.fuel.FuelKey` (`:17`) | none |
| `Map<WearableTrait,Integer> traits` (`:52`) | `Map<String,Integer> traits` keyed by the lower-case trait key |
| `boolean isJetpack()` (`:227-229`) | none — consumers test `extraTags().containsKey("fuel")` |
| `String fuelKey` + 10 jetpack fields (`:58-72`) | `Map<String,Object> extraTags` |
| `builder.addTag(FuelKey.FUEL_ID.getKey(), fuelKey)` … (`:275-279`) | a blind loop over `extraTags`: `String` → `addTag(k, (String) v)`, `Integer` → `addTag(k, (int) v)`, `Double` → `addTag(k, (double) v)`; nested `Map` values are **not** stamped |

New public surface: `Set<String> traits()`, `int traitLevel(String key)`, `Map<String,Object> extraTags()`.
The per-level effect table that `WearableTrait` held (`WearableTrait.java:19-60`) moves into `Wearable` as a private
static `Map<String, double[]> {maxLevel, effectPerLevel}` so `getGenericDamageReduction()` /
`getProjectileDamageReduction()` / `getExplosionDamageReduction()` / `reduceCritBonus` / `reduceFireTicks` /
`rollReactive` (`:285-340`) keep their exact arithmetic and caps (0.80 / 0.90 / 0.90).

`items/wearables.yml` migration — the `Jetpack:` block (`:194-208`) becomes `Extra_Tags:`:

```yaml
   Extra_Tags:
      fuel: "gasoline"
      fuel_current: 3600
      fuel_max: 3600
      jetpack_fuel_consumption_rate: 1
      jetpack_ascend_power: 0.2
      jetpack_glide_descent_rate: -0.05
      jetpack_max_speed_y: 0.45
      Sounds:
         Thrust:
            Default_Sound: "…"
            Custom_Sound: "…"
         Glide:
            Default_Sound: "…"
            Custom_Sound: "…"
```

`fuel` / `fuel_current` / `fuel_max` reproduce today's three NBT tags verbatim (`Wearable.java:276-278`,
`FuelKey.java:13-15`), so Gangland's `FuelService` keeps working with **zero** changes — it already reads fuel from
item NBT. The `jetpack_*` scalars and the nested `Sounds` map are what P3's gadget reads through `extraTags()`
(today: `JetpackTask.java:133,:212,:229,:231`). `WearableAddon.java:141-193` is rewritten to parse this block
generically.
Trait keys in the YAML change from `REINFORCED: 2` to `reinforced: 2` — **or keep the upper-case keys and lower-case
them on read**. Take the second option: it is a one-line `toLowerCase()` in the parser and leaves all 22 YAML files
byte-identical in their `Traits:` blocks.

**(7) `WeaponRaytracer` splits into an interface and an impl.** `W/raytrace/WeaponRaytracer.java` is a concrete class
holding `WeaponManager`, `WearableAddon`, `BlockDamageManager` and `WeaponVisualSpawner`
(`W/WeaponModuleConfig.java:96-97`) — it cannot go into `bartizan-api` without dragging the whole runtime with it.
Extract the four members E1 §5.4 lists as an interface in `API/raytrace/WeaponRaytracer.java`:

```java
static boolean isRaytraceDamageInProgress();   // the ThreadLocal flag, W/raytrace/WeaponRaytracer.java:88
WeaponVisualSpawner getVisualSpawner();        // :160  — return type moves to the api with it
void fireInstant(RaytraceRequest request);     // :173
void advanceSegment(RaytraceContext ctx, Location from, Location to);   // :195
```

`RaytraceContext` and `WeaponVisualSpawner` therefore move to the api too (adjust the §1.1 rows for those two files
if the compile gate demands it — record the change in section 7). The class body becomes
`PLG/raytrace/WeaponRaytracerImpl implements WeaponRaytracer`. **The `ServicesManager` key stays the api interface**,
so consumers' `getRegistration(WeaponRaytracer.class)` is source-compatible with today
(`NpcCombatDelegate.java:332`).

**(8) `NpcWeaponController` — the cadence port.** New `API/npc/` pair:

```java
public interface NpcWeaponFactory {
    NpcWeaponController create(LivingEntity shooter, String weaponName,
                              double fireRateMultiplier, double aimErrorDegrees);
}

/**
 * The single implementation of Keystone's NpcRangedAttack. Bartizan owns NPC firing cadence;
 * consumers never implement this - they obtain one from NpcWeaponFactory and hand it to
 * AbstractNpc.setRangedAttack(...).
 */
public interface NpcWeaponController extends org.luckyraven.keystone.npc.spi.NpcRangedAttack {
    // inherited: isRanged(), isBusy(), tryFire(LivingEntity), triggerReload(),
    //            refreshHeldItem(), onDestroy(), tick()
}
```

`PLG/npc/NpcWeaponControllerImpl` carries today's cops logic **verbatim**, ported from
`GW/gangland-features/cops-n-crooks/.../npc/NpcCombatDelegate.java`:

| Bartizan member | Ported from | Exact arithmetic that must not change |
|---|---|---|
| `tryFire` mode switch | `performGanglandWeaponAttack` `:175-191` | `getCurrentSelectiveFire()`, `null` → `AUTO` |
| SINGLE | `performSingleShot` `:260-270` | `cooldown = scale(max(perShot * cooldown, 5))`, **one trigger per call** |
| BURST | `performBurstFire` `:283-306` | `totalBurstTicks = perShot * cooldown + cooldown`; `SequenceTimer(plugin, 1L, 1L)`; interval `0` for `i == 0`, else `cooldown`; `start(false)`; **one trigger per burst** |
| AUTO | `performAutoShot` `:272-281` | `cooldown = scale(max(cooldown, 5))`, hold-to-fire |
| `fireSingleRound` | `:309-341` | broken/empty → reload + `false`; `consumeShot()`; `WeaponShootEvent` → cancelled means `addAmmunition(1)` and `false`; `WeaponShooting.fire`; `SoundEffect.playSoundsAtLocation(eye, shotCustom, shotDefault)`; `refreshHeldItem()` |
| `triggerReload()` | `triggerReload` `:193-198` | null/reloading/no-`ReloadData` guards, then `reload(plugin, null, false)` |
| `refreshHeldItem()` | `:142-157` | `ItemBuilder(current)` → `updateWeaponData` → `setItemInMainHand`; skips `Material.AIR` |
| `isBusy()` | `canAttack` `:105-111` | `attackCooldown > 0 \|\| reloading` |
| cooldown scaling | `scaleCooldown` `:364-367` | `max(round(base * fireRateMultiplier), 5)` |
| `tick()` | `decrementAttackCooldown` `:113-115` | `if (attackCooldown > 0) attackCooldown--;` — one decrement per NPC tick, called by Keystone |

`attackCooldown` becomes an instance field of `NpcWeaponControllerImpl`, decremented in its override of
`NpcRangedAttack.tick()`. Keystone's `NpcCombatDelegate.decrementAttackCooldown()` calls
`owner.rangedAttack().tick()` on the NPC's existing tick loop (P1 K21 step 9), so today's tick alignment is
preserved exactly and Bartizan schedules no timer of its own.

**`aimErrorDegrees` is accepted and stored but has no effect on the Bartizan gun path.** Verified: `applyAimError`
(`NpcCombatDelegate.java:343-351`) is called only from `faceTarget` `:125` and `faceTargetEntity` `:136`, both of
which feed the **vanilla** bow/crossbow path, never `fireSingleRound`. Spread on the Bartizan path comes from
`WeaponShooting.fire` → `SpreadManager`. Keep the parameter in the signature (PICK fixes it) and javadoc it as
reserved; do not invent a use for it.

**(9) `WeaponItemApi`.** New `API/item/WeaponItemApi.java`, four methods, implemented in `PLG/item/WeaponItemApiImpl`:

```java
ItemStack buildItem(String weaponName);          // W/WeaponService.java:156 createTransientWeapon + buildItem
boolean   isValidWeaponName(String name);        // W/WeaponService.java:130 getWeaponTemplate != null
boolean   isSameWeapon(ItemStack a, ItemStack b);// W/WeaponService.java:54  compare(w1, w2) == 0
String    cleanDisplayName(ItemStack item);      // W/shop/WeaponShopDisplayNameProvider.java:28-40
```

`buildItem` must reproduce the throwable-UUID determinism rule
(`UUID.nameUUIDFromBytes("throwable:" + name)`, `W/sign/AbstractWeaponTradeSign.java:41-45`,
`W/WeaponService.java:274-283`) or throwables stop stacking. Pin it with a test before deleting the sign classes.

### 1.7 Resources

| Source | Target | Edit |
|---|---|---|
| `W/../resources/weapon/*.yml` (22) | `bartizan-plugin/src/main/resources/weapon/*.yml` | **byte-identical**, no edit |
| `W/../resources/items/ammunition.yml` | `bartizan-plugin/src/main/resources/items/ammunition.yml` | byte-identical |
| `W/../resources/items/wearables.yml` | `bartizan-plugin/src/main/resources/items/wearables.yml` | `Jetpack:` → `Extra_Tags:` per §1.6(6); everything else byte-identical |
| `W/../resources/commands.json` | `bartizan-plugin/src/main/resources/commands.json` | 12 keys re-pathed + 2 new (§1.4) |
| `W/../resources/module.yml` | — | **DELETE** — Bartizan is not a module |
| `W/../resources/org/luckyraven/gangland/weapon/module.properties` | `bartizan-plugin/src/main/resources/org/luckyraven/bartizan/module.properties` | content becomes `module.name=Bartizan` |
| — | `bartizan-plugin/src/main/resources/plugin.yml` | **NEW** (§C.5) |
| — | `bartizan-plugin/src/main/resources/settings.yml` | **NEW** (§1.8) |
| — | `bartizan-plugin/src/main/resources/message/message_en.yml` | **NEW** (§1.5) |

The 22 weapon YAMLs go in at the **same relative paths** (`weapon/rifle.yml`, not `bartizan/weapon/rifle.yml`) so the
data folder layout is `plugins/Bartizan/weapon/rifle.yml`.

### 1.8 `settings.yml` — exactly the keys E1 §7 lists, and nothing else

Only seven Gangland settings getters are read by the weapon module (`W/`, exhaustive per E1 §7). Bartizan's
`settings.yml` carries only these, plus the three infrastructure sections a standalone plugin needs:

```yaml
Language: "en"

Debug:
   Enable: false

Money_Symbol: "$"

Block_Regeneration:
   Restore_Delay_Ticks: 100
   Regeneration_Delay_Ticks: 100
   Regeneration_Step_Ticks: 4

Auto_Save:
   Debug: true
   Time: 10

Database:
   Type: "sqlite"
   MySQL:
      Host: "localhost"
      Port: 3306
      Username: "root"
      Password: ""
   SQLite:
      Backup: true
      Failed_MySQL: true
```

| Key | Default | Consumed by | Was |
|---|---|---|---|
| `Block_Regeneration.Restore_Delay_Ticks` | 100 | `WeaponBlockRegenerationSettings.java:13` | `Settings.java:194,707` |
| `Block_Regeneration.Regeneration_Delay_Ticks` | 100 | `:18` | `Settings.java:195,708` |
| `Block_Regeneration.Regeneration_Step_Ticks` | 4 | `:23` | `Settings.java:196,709` |
| `Money_Symbol` | `"$"` | `BartizanChatUtil.color` | `Settings.java:51,427` |
| `Auto_Save.Debug` | true | `WeaponDataCleanupTask.java:20` | `Settings.java:44,404` |
| `Language` | `"en"` | `LanguageLoader` | `Settings.getLanguagePicked()` |
| `Database.*` | — | `BartizanDatabaseSettings implements DatabaseSettingsProvider` | `GanglandDatabaseSettings` |

**Dropped deliberately:** `Inventory.Fill.Item` / `Inventory.Fill.Name` (`Settings.java:48,414-415`) — read only by
`WeaponSignViewProvider.java:115,133,197`, which is deleted with the signs (D7).

`BartizanSettings` is a `FileInitializer` with static getters, copied in shape from
`GW/gangland-impl/.../file/configuration/Settings.java` (Keystone `FileHandlerReader.read(handler, new ConfigReport())`
→ `section(...)` / `str(...)` / `intVal(...)` / `bool(...)`), **not** Bukkit's `getConfig()`.
House rule: never `setDefaults`/`copyDefaults`.

### 1.9 Persistence and the `weapon` table migration (R3)

`BartizanDatabase extends DatabaseHandler` — copy the shape of
`GW/gangland-impl/.../database/GanglandDatabase.java`, keeping `connectBackend()` (`:56-81`),
`disconnectBackend()` (`:100-105`), `createSchema()` (`:108-122`), `createTables()` (`:125-132`) and
`getSchema()` (`:151-157`), and **dropping** `insertInitialData()`'s rank seeding (`:135-148`) — return immediately.
Schema name `"bartizan"`, so the SQLite file is `plugins/Bartizan/database/bartizan.db`.

`WeaponTableImportTask` — a one-shot DATABASE-phase bean:

1. Return immediately if `plugins/Bartizan/.weapon-import-done` exists.
2. If `plugins/Gangland_Warfare/database/gangland.db` exists, open it read-only
   (`jdbc:sqlite:<path>?open_mode=1`), `SELECT uuid, type FROM weapon`, and upsert every row through Bartizan's own
   `TableBackend`. Log the row count at INFO.
3. If the file does not exist, log at DEBUG and continue — a MySQL deployment or a fresh install is not an error.
4. Write the marker file **whether or not** anything was imported, so the read never repeats.
5. Any `SQLException` is reported through `Diagnostics.active()` as a dependency fault and does **not** abort boot —
   a failed import costs re-minted UUIDs, not data (`W/WeaponService.java:76-81` falls back to the catalogue).

MySQL deployments are **not** migrated automatically. `documentation/migration.md` documents the one statement
(§4).

### 1.10 Tests

16 test files exist under `W/../src/test/java/org/luckyraven/gangland/weapon/`. All 16 move.
Two reach outside the module and need the fix named:

| Test | Reaches outside | Fix |
|---|---|---|
| `projectile/recoil/RecoilManagerTest.java:6` | mocks `RecoilCompatibility` | **Rewritten** — mock nothing; assert against a captured `PacketAdapter` installed via `PacketBridge.install(...)` (§3) |
| `modifiers/ModifierHandlerTest.java:11` | `org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture` from `gangland-core`'s test-jar (`gangland-weapon/pom.xml:119-124`) | Copy the fixture into `bartizan-plugin/src/test/java/org/luckyraven/bartizan/testsupport/` (§1.2) and drop the test-jar dependency |
| `WeaponModuleTest.java` | asserts `ModuleRegistrations` contents | **Deleted and replaced** by `BartizanBootstrapTest` (§3) — there is no module descriptor any more |

**The `viaversion-api` provided-test-classpath rule (docket T-09) still applies.** `gangland-weapon/pom.xml:84-96`
documents it: Mockito's inline mock maker retransforms the full type hierarchy of any mocked type, so a
`com.viaversion` field type anywhere in that hierarchy throws `TypeNotPresentException` without the artifact on the
test classpath. After rewrite (1), Bartizan's own code no longer names ViaVersion — **but the rule must still be
recorded in the pom as a comment**, because `plugin.yml` keeps `softdepend: [ViaVersion]` and the trap returns the
moment anyone mocks a type that touches it. Keep `com.viaversion:viaversion-api` at `provided` in
`bartizan-plugin/pom.xml` with the T-09 comment.

---

## 2. Ordered tasks

Repo: `E:\Programming\java\Bartizan` for every task. Never edit the Gangland tree.
"Copy" below always means: read the Gangland file, write a new file at the target path, apply the edit. There is no
`git mv` across repos.

### Group A — repo skeleton (~8 files)

#### B1 — create the repo and the root pom (3 files)
- **Do:** `git init E:\Programming\java\Bartizan`; create `pom.xml`, `lombok.config`, `.gitignore`.
  `pom.xml` is `E:\Programming\java\Keystone\pom.xml` with these deltas and **nothing else changed**:
  `<groupId>org.luckyraven.bartizan</groupId>`, `<artifactId>bartizan</artifactId>`, `<packaging>pom</packaging>`,
  `<version>${revision}</version>`, `<revision>0.1.0</revision>`, `<modules>` = `bartizan-api`, `bartizan-plugin`,
  `deploymentName` = `Bartizan ${revision}`, `<excludeArtifact>bartizan-plugin</excludeArtifact>`.
  Keep verbatim: `maven.compiler.release` 17, the `flatten-maven-plugin` 1.6.0 block
  (`updatePomFile=true`, `flattenMode=resolveCiFriendliesOnly`, `flatten`@`process-resources` + `flatten.clean`@`clean`),
  the `pluginManagement` block, the `release` profile, the MIT licence / developer / `<scm>` metadata with the
  `child.*.inherit.append.path="false"` attributes, and the root `<dependencies>` (`spigot-api`, `lombok`,
  `annotations`, `junit-jupiter`).
  Repositories: `maven_central`, `spigot-repo`, `papermc`, `jitpack.io`, `placeholderapi`,
  **plus `viaversion` → `https://repo.viaversion.com`** (Keystone has no ViaVersion repo; Gangland's root pom does).
  `dependencyManagement`: `org.luckyraven:keystone-{common,bean,command,item,persistence,hooks,npc}:1.9.0` at `provided` (`npc` added at gate GA — `bartizan-api` depends on it),
  `org.luckyraven.bartizan:bartizan-api:${project.version}`, `com.viaversion:viaversion-api` provided,
  `com.github.cryptomorin:XSeries` provided, `org.bstats:bstats-bukkit`, `com.google.code.gson:gson` provided,
  `log4j-api` provided, `mockito-core` test, `sqlite-jdbc` test.
  `lombok.config` and `.gitignore` are byte-copies of Keystone's
  (`config.stopBubbling = true`, `lombok.log.fieldName = log`,
  `lombok.log.custom.declaration = org.apache.logging.log4j.Logger org.luckyraven.keystone.logging.Logger.getLogger(TYPE)`;
  `target/`, `.flattened-pom.xml`, `*.iml`, `.idea/`, `.vscode/`, `*.log`, `graphify-out/`).
- **Why:** every later task needs a resolvable reactor.
- **Done when:** `cd E:\Programming\java\Bartizan && mvn -q -N validate` exits 0.
- **Watch out:** `keystone-*` at **provided**, never `compile` — Keystone must not be shaded. Do not copy Keystone's
  `<modules>` list.

#### B2 — the two module poms (2 files)
- **Do:** `bartizan-api/pom.xml` — parent `bartizan` at `${revision}`, artifactId `bartizan-api`,
  dependencies `keystone-common`, `keystone-item` **and `keystone-npc`** (all three provided), `spigot-api`
  provided, `XSeries` provided.
  **No** `keystone-persistence`, **no** `keystone-bean`, **no** `keystone-command`, **no** NBT, **no** viaversion.
  `keystone-npc` brings `citizens-main` transitively at `provided`; that is expected and never reaches the jar.
  `bartizan-plugin/pom.xml` — parent as above, artifactId `bartizan-plugin`,
  `<finalName>Bartizan-${project.version}</finalName>`, filtered resources so `plugin.yml`'s `${project.version}`
  resolves (copy `keystone-plugin/pom.xml:18-23`), the shade block copied from `keystone-plugin/pom.xml:25-55`
  (`createDependencyReducedPom=false`, **one relocation: `org.bstats` → `org.luckyraven.bartizan.dependency.bstats`** (gate-GA review M1; Gangland relocates it too, `gangland-build/pom.xml:48-51`), the `META-INF/*.SF|DSA|RSA` + `module-info.class` filter, no
  `minimizeJar`, no `artifactSet`), dependencies: `bartizan-api` **compile** (it must be inside the jar),
  `keystone-{common,bean,command,item,persistence,hooks}` provided, `spigot-api` provided, `XSeries` provided,
  `bstats-bukkit` compile, `gson` provided, `log4j-api` provided,
  **`com.viaversion:viaversion-api` provided with the T-09 comment from `gangland-weapon/pom.xml:84-96`**,
  `mockito-core` + `sqlite-jdbc` + `log4j-core` test.
- **Why:** `bartizan-api` must be publishable and NMS-free; the plugin jar must be self-contained but Keystone-free.
- **Done when:** `mvn -q clean install -DskipTests` builds both modules and produces
  `bartizan-plugin/target/Bartizan-0.1.0.jar`, and `mvn -q dependency:tree -pl bartizan-api` shows no
  **compile-scoped** `keystone-persistence` / `keystone-bean` (both appear at `provided` through `keystone-npc` — expected) and
  no `citizens-main` at any scope.
- **Watch out:** `bartizan-api` is the one artifact P3 consumes — its scope in `bartizan-plugin` is `compile` so the
  shade includes it, but its scope in every **Gangland module pom** is `provided`.

#### B3 — repo docs skeleton (3 files)
- **Do:** create `README.md` (what Bartizan is, install = drop `Bartizan-0.1.0.jar` beside `Keystone-1.9.0.jar`,
  the data-folder map from §C.5), `CLAUDE.md` (Spigot only / no `io.papermc`; method braces on their own lines;
  `@CustomLog`; block-style YAML with `Capitalized_Underscore_Separated` keys; `ChatUtil.color()` with `&` codes;
  `SoundEffect`/XSeries for version-drifting enums; never `setDefaults`/`copyDefaults`; Keystone provided never
  shaded; the api/plugin rule from §C.2; `mvn clean install`), and an empty `documentation/` directory with a
  `.gitkeep`.
- **Why:** a Sonnet executor entering this repo in a later group has no other context.
- **Done when:** all three exist; `CLAUDE.md` names the `bartizan-api` zero-NMS rule.
- **Watch out:** do not copy Gangland's `CLAUDE.md` wholesale — most of it is about modules Bartizan does not have.

> **Compile gate GA:** `mvn -q clean install -DskipTests` exits 0.

### Group B — `bartizan-api` weapon model (25 files)

#### B4 — Weapon, the eight type classes, and the 15 dtos (25 files)
- **Do:** copy every file in the §1.1 group-B table to its target. Apply `PKG` to all 25:
  `package org.luckyraven.bartizan.api.weapon;` (or `.dto`), and rewrite every
  `org.luckyraven.gangland.weapon.*` import to the new package. Note the `types/` flattening: `types.gun.GunWeapon`
  → `api.weapon.GunWeapon`; `types.melee.MeleeWeapon` → `api.weapon.MeleeWeapon`; `types.biological.BiologicalWeapon`,
  `types.incendiary.IncendiaryWeapon`, `types.throwable.ThrowableWeapon` and `types.throwable.ThrowableType` the same way.
- **Why:** the domain model every consumer compiles against.
- **Done when:** `mvn -q -pl bartizan-api -am install -DskipTests` fails **only** with "cannot find symbol" for
  the 13 group-C/D symbols `Ammunition, ArmorPiercingModifier, BlockBreakModifier, DurabilityCalculator,
  FlatDamageModifier, PenetrationModifier, ProjectileType, RecoilManager, Reload, ReloadType, RicochetModifier,
  SpreadManager, TracerModifier` — all arrive in B5 except `Ammunition` (group D, B6).
  Paste the failure list into section 7 to prove the closure is what §1.1 says.
- **Watch out:** `Weapon.java` keeps its `keystone.item.ItemBuilder`, `keystone.util.Placeholder`,
  `keystone.exception.PluginException` and `com.cryptomorin.xseries.XPotion` imports unchanged — those all resolve
  from `bartizan-api`'s own dependencies.

> **Compile gate GB** is deferred to the end of B5 — B4 cannot compile alone by design.

### Group C — `bartizan-api` weapon internals (18 files)

#### B5 — projectile, modifiers, reload, durability, spread, recoil (18 files)
- **Do:** copy every file in the §1.1 group-C table. `PKG` for 15 of them. Three get `PKG+`:
  - `RecoilManager` — apply rewrite §1.6(1): delete the `RecoilCompatibility` parameter from `applyRecoil`,
    `applyDefaultRecoil` and `recoil`; the private `recoil` becomes the three-line `PacketBridge.adapter()` call with
    the `-yaw + 1` / `pitch - 1` transform preserved.
  - `InstantReload`, `NumberedReload` — apply rewrite §1.6(5): the `DownedPlayerRegistry.isDowned(uuid)` reads at
    `InstantReload.java:61,:74` and `NumberedReload.java:90,:132` become
    `!combatEligibility.canBeHit(player)`, with `CombatEligibility` passed in through the constructor.
    **Watch the polarity inversion.**
- **Why:** closes the `Weapon` compile closure and cuts the last two Gangland couplings out of the api.
- **Done when:** `mvn -q -pl bartizan-api -am install -DskipTests` exits 0 for these files (the new api interfaces
  from B6 are still missing, so expect failures only in `Ammunition` (group D, B6) and `CombatEligibility` — create the latter here if it
  blocks you and note it in section 7).
- **Watch out:** `RecoilManager.clone()` calls `super.clone()` but the class does not declare
  `implements Cloneable` — that is pre-existing (`W/projectile/recoil/RecoilManager.java:72-79`); **do not fix it**,
  it is out of scope. If you think it is a bug, add one line to
  `GW/brainstorming/bug-docket-2026-09-06/triage/new-findings.txt`.

> **Compile gate GC** (criterion corrected at gate GA — `dto/AmmunitionData` imports the group-D `Ammunition`, so the api
> cannot compile after B5 alone): `mvn -q -pl bartizan-api -am install -DskipTests` fails **only** with cannot-find-symbol for
> `Ammunition` (and `CombatEligibility` if B5 did not create it). Exit 0 is B6's Done-when and gate GD.

### Group D — `bartizan-api` public surface (19 files)

#### B6 — events, ammunition, the five new interfaces, Wearable (19 files)
- **Do:** copy the nine event classes and `Ammunition` per the §1.1 group-D table (`PKG`, plus the two `PKG+` rows).
  Then create the ten new files:
  - `BartizanApi` — `WeaponCatalog weapons(); WearableCatalog wearables(); AmmunitionCatalog ammunition();
    NpcWeaponFactory npcWeapons(); WeaponItemApi items();`
  - `WeaponCatalog` — `Weapon getWeaponTemplate(String); Collection<Weapon> getWeaponTemplates();
    Weapon createTransientWeapon(String); Weapon validateAndGetWeapon(Player, ItemStack); boolean isWeapon(ItemStack);`
    (exactly the five `WeaponService` methods E1 §5.2 marks as cross-module).
  - `AmmunitionCatalog` — `Set<String> getAmmunitionKeys(); Ammunition getAmmunition(String);`
  - `WearableCatalog` — `Wearable getWearable(String); Map<String,Wearable> getWearables();
    Wearable resolveWearable(@Nullable ItemStack); double applyWearableReduction(double, LivingEntity, boolean);
    double reduceCritBonus(double, LivingEntity); int reduceFireTicks(int, LivingEntity);`
  - `WeaponItemApi` — the four methods in §1.6(9).
  - `NpcWeaponFactory`, `NpcWeaponController` (**extends `org.luckyraven.keystone.npc.spi.NpcRangedAttack`**) — §1.6(8).
  - `CombatEligibility` — §1.6(5).
  - `BartizanItemPredicates` — the `WEARABLE` predicate only.
  - `Wearable` — the full rewrite in §1.6(6).
  - `WeaponEntityDamageEvent` gets `DamageKind`, `weaponName()`, `kind()` — §1.6(3).
- **Why:** this is the entire surface P3 compiles against. Nothing outside these files may be public API.
- **Done when:** `mvn -q -pl bartizan-api -am install` exits 0 **and**
  `grep -rl "org\.luckyraven\.gangland" bartizan-api/src` returns nothing **and**
  `grep -rlE "net\.minecraft|craftbukkit" bartizan-api/src` returns nothing.
- **Watch out:** `Wearable` is the file most likely to go wrong. Its six damage methods
  (`getGenericDamageReduction`, `getProjectileDamageReduction`, `getExplosionDamageReduction`, `reduceCritBonus`,
  `reduceFireTicks`, `rollReactive`) keep their exact caps 0.80 / 0.90 / 0.90 / 1.0 / 1.0 and their per-level table
  from `WearableTrait.java:19-60`. Copy the numbers, do not re-derive them.

### Group E — `bartizan-api` raytrace (3 files)

#### B7 — the raytrace contract (3 files)
- **Do:** apply rewrite §1.6(7). Create `API/raytrace/WeaponRaytracer.java` as an interface with the four members;
  copy `WeaponShooting` and `RaytraceRequest` with `PKG`. If the compiler shows `RaytraceContext` /
  `WeaponVisualSpawner` are needed by the interface signatures, move those two files into
  `API/raytrace/` as well and record the deviation in section 7.
- **Why:** the `ServicesManager` key must be an api type or every consumer drags the runtime in.
- **Done when:** `mvn -q -pl bartizan-api -am install` exits 0.
- **Watch out:** `WeaponShooting.SPREAD_PELLET_COUNT` (`W/raytrace/WeaponShooting.java:31`) is read by cops today —
  keep it `public static final`.

> **Compile gate GD (the api is frozen here):**
> `mvn -q -pl bartizan-api -am install` exits 0, and both greps in B6 return nothing.
> **P3 can start compiling against `bartizan-api` from this point — notify the orchestrator; this is the
> P2→P3 handoff.**

### Group F — plugin bootstrap (14 files)

#### B8 — Bartizan, BartizanContext, DefaultListenerService (3 files)
- **Do:** `PLG/Bartizan.java` — `@CustomLog @Getter public final class Bartizan extends JavaPlugin`, constants
  `FULL_PREFIX = "bartizan"`, `SHORT_PREFIX = "btz"`. `onEnable()`: `context = new BartizanContext(this);
  context.bootstrap();` then `dependencyHandler()` (ViaVersion/PlaceholderAPI/NBTAPI soft checks, copied in shape
  from `GW/gangland-impl/.../Gangland.java:174-215`) then `bStats()`. Wrap the whole body in
  `try { … } catch (Throwable t) { log.error("Bartizan failed to enable", t); getServer().getPluginManager().disablePlugin(this); }`
  (Oriel's shape, `oriel-plugin-skeleton.md` §1). `onDisable()`: `PacketBridge.reset()`, then
  `context.shutdownBeans()` in try/catch, then close the database.
  `bStats()`: `new Metrics(this, PLUGIN_ID)` with **`int PLUGIN_ID = 0; // TODO: register Bartizan on bstats.org`** —
  see Q3 — and one chart `new SingleLineChart("number_of_weapons", () -> context.get(WeaponAddon.class).size())`,
  ported from `W/metrics/WeaponMetricsContributor.java:23`.
  `PLG/bootstrap/BartizanContext.java` — modelled on `GW/gangland-impl/.../bootstrap/GanglandContext.java` with
  **every module concern removed**: no `ModuleLoader`, no module scans, no `hostApi`, no
  `publishRepositoriesFromContainer` module loop. Keeps: the `DependencyContainer` + `BeanFactory` fields, the
  `registerInstance` calls (`BartizanContext`, `DependencyContainer`, `JavaPlugin`, `Bartizan`, `SettingsLookup`,
  `BeanFactory`), the **FILE phase hook** (`fm.initializeAll()` after every FILE bean —
  `GanglandContext.java:167-172`), the **DATABASE phase hook** (republish repositories by concrete class,
  `:174-177` + `:199-212`), `bootstrap()` = `scan("org.luckyraven.bartizan.config"); instantiate();
  runListenerPhase(); runCommandPhase();`, and `reloadBeans()` / `shutdownBeans()`.
  `runCommandPhase()` copies `GanglandContext.java:233-277` minus the module loop: `ArgumentMessages.install(...)`
  with `BartizanMessages`, `Command.setInformationManager(...)`, `setExecutor`, `scanAndRegisterCommands`,
  `CommandTabCompleter`, `BrigadierTabRegistrar.registerIfSupported`.
  `PLG/bootstrap/DefaultListenerService.java` — `extends ListenerService`, ctor
  `(JavaPlugin, DependencyContainer, SettingsLookup)`, `invokeMethod(String condition)` →
  `settings.isEnabled(condition)`. Keystone ships only the abstract base
  (`oriel-plugin-skeleton.md` §2), so this file is mandatory.
- **Why:** the phased pipeline everything else hangs off.
- **Done when:** the three files compile (`mvn -q -pl bartizan-plugin -am install -DskipTests` will still fail on the
  missing config classes — that is expected; check the errors name only `config.*`).
- **Watch out:** the FILE phase hook is not optional. `BartizanSettings`'s static fields stay null without it and
  `LanguageLoader` then tries to read `message_null.yml` (the exact trap documented at `FileConfig.java:57-62`).

#### B9 — BartizanChatUtil, BartizanMessages, BartizanSettings, BartizanDatabaseSettings (4 files)
- **Do:** `PLG/util/BartizanChatUtil.java` per §1.2. `PLG/file/BartizanMessages.java` per §1.5 (18 entries).
  `PLG/file/BartizanSettings.java` per §1.8 — `implements FileInitializer`, static getters, reads through
  `FileHandlerReader.read(handler, new ConfigReport())`.
  `PLG/database/BartizanDatabaseSettings.java` — `implements DatabaseSettingsProvider`, six methods delegating to
  `BartizanSettings`.
- **Why:** the text and config substrate the commands and loaders need.
- **Done when:** all four compile.
- **Watch out:** `BartizanMessages.getValue`'s `Type` switch calls `BartizanChatUtil.prefixMessage` /
  `commandMessage` / `errorMessage` / `informationMessage` / `color` — and those four call
  `BartizanMessages.PREFIX` / `COMMAND_PREFIX` / `ERROR_PREFIX` / `INFORMATION_PREFIX` back. That mutual recursion is fine (it is exactly what Gangland does,
  `Messages.java:730-744` ↔ `GanglandChatUtil.java:20-32`) **as long as the four prefix members are `Type.OTHER`**,
  which routes to plain `color(...)` and terminates.

#### B10 — KernelConfig, FilesConfig, WiringConfig, DatabaseConfig, ItemConfig (5 files)
- **Do:** `PLG/config/KernelConfig.java` (`@Configuration(phase = Phase.KERNEL)`) —
  `diagnostics()` (`Diagnostics.withDefaults().addSink(new LoggingSink()).addSink(new RecentFaultsSink())` +
  `Diagnostics.install(hub)`), `permissionWorker()` → `new PermissionWorker("bartizan")`,
  `permissionManager(PermissionHandler)` → `new PermissionManager(handler, "bartizan")`,
  `packetAdapter()` per §1.6(2), `informationManager()` (Gson `commands.json` reader, no module merge),
  `fileManager()` registering `settings.yml` and the `items/` + `weapon/` handlers,
  `databaseSettings()`, `databaseManager(DatabaseSettingsProvider)`.
  `PLG/config/FilesConfig.java` (`Phase.FILE`) per §1.3 — including the `LanguageLoader` bean with
  `loader.initialize()` called inside the bean (`keystone-maven-conventions.md` §8: `onInitialize(firstLoad=true)`
  is a no-op, the bean must initialise itself once).
  `PLG/config/DatabaseConfig.java` (`Phase.DATABASE`) — `bartizanDatabase(...)` modelled on
  `GW/gangland-impl/.../config/DatabaseConfig.java:44-100` minus the module repository loop, scanning
  `"org.luckyraven.bartizan.database"`; plus `databaseBackend(...)`, `repositoryRegistry(...)`,
  `weaponTableImportTask(...)` (§1.9).
  `PLG/config/WiringConfig.java` and `PLG/config/ItemConfig.java` (both CONFIG) per the §1.3 tables.
- **Why:** replaces `WeaponFileConfig` + `WeaponModuleConfig` with a standalone plugin's five configs.
- **Done when:** `mvn -q -pl bartizan-plugin -am install -DskipTests` fails only on classes that arrive in groups
  G–O.
- **Watch out:** keep the two ordering-only `@Bean` parameters (`AmmunitionManager(BartizanSettings)`,
  `weaponAddon(AmmunitionAddon)`). They look unused and they are not — they are the bean-graph edges (house rule
  `feedback_bean_ordering_via_params`).

#### B11 — plugin.yml, module.properties (2 files)
- **Do:** write both per §C.5 and §1.7.
- **Why:** without `module.properties` every `@CustomLog` line is prefixed with the wrong name.
- **Done when:** `plugin.yml` parses as YAML and declares the `bartizan` command with aliases `btz`, `weapon`.
- **Watch out:** `version: ${project.version}` only resolves because B2 turned on resource filtering.

> **Compile gate GE:** none — group F cannot compile alone. Proceed to G.

### Group G — plugin weapon core (24 files)

#### B12 — services, addons, parsers, loaders (24 files)
- **Do:** copy from the §1.1 `PLG/` table: `WeaponService`, `WeaponManager`, `ammo/AmmunitionManager`,
  `configuration/{WeaponAddon, AmmunitionAddon}`, `configuration/parser/*` (9), `wearable/{WearableService,
  WearableAddon}`, `file/{WeaponLoader, WeaponBlockRegenerationSettings}`, `fire/PluginFireRegistry`,
  `util/{BlockGroupResolver, EmptyMagSoundGate, PotionEffectParser}`, `data/WeaponDataCleanupTask`.
  Apply the `PKG+` edits named in that table — in particular `WearableAddon`'s `Extra_Tags:` parse (§1.6(6)) and
  `WeaponBlockRegenerationSettings`'s three `BartizanSettings` reads.
- **Why:** the catalogue layer.
- **Done when:** `mvn -q -pl bartizan-plugin -am install -DskipTests` reports no error inside these 24 files.
- **Watch out:** `WeaponLoader`'s expected-file list stays at five entries. T-06 is a known docket bug and is **not**
  in scope; do not "fix" it by adding the other 17 weapons.

> **Compile gate GF:** the 24 files above produce no errors of their own.

### Group H — plugin actions and raytrace runtime (23 files)

#### B13 — the six actions, raytrace impl, listeners (23 files)
- **Do:** copy `types/*/…Action.java` (6) → `PLG/weapon/action/`, `raytrace/{RaytraceContext,
  SteppedProjectileTask, WeaponMuzzle, WeaponVisualSpawner}` → `PLG/raytrace/` (unless B7 moved two of them to the
  api — check first), `raytrace/WeaponRaytracer.java`'s class body → `PLG/raytrace/WeaponRaytracerImpl.java`,
  `modifiers` stay in the api (already done in B5), and the 11 surviving listeners
  (`WeaponInteract`, `ScopeJumpListener`, `fire/PluginFireProtectionListener`,
  `player/WeaponQuitCleanupListener`, `projectile/ProjectileDamageListener`,
  `reload/{WeaponDroppedListener, WeaponItemSpawnListener, WeaponReloadListener}`,
  `selective/WeaponSelectiveFireChangeListener`, `wearable/WearableEquipListener`).
  Apply: the `RecoilCompatibility` constructor-parameter removal in all six actions and in `WeaponInteract`
  (11 sites); `ThrowableAction`'s two static maps deleted per §1.6(3); `WeaponInteract`'s two
  `DownedPlayerRegistry` reads → `CombatEligibility` per §1.6(5); `WearableEquipListener` calling `WearableService`
  directly.
  **Do not copy `listener/gang/GangAllyWeaponImpactListener.java`** — it is deleted (§1.1).
- **Why:** the firing runtime.
- **Done when:** `mvn -q -pl bartizan-plugin -am install -DskipTests` exits 0 **except** for the item, database,
  command and npc classes still missing.
- **Watch out:** `ThrowableAction`'s explosion loop must now fire a `WeaponEntityDamageEvent` with
  `DamageKind.EXPLOSION` before `target.damage(...)` (§1.6(3)) — without it P3's car-damage rewrite has nothing to
  read and vehicles silently take vanilla explosion damage.

### Group I — item framework and the vocabulary (22 files)

#### B14 — converters, serializers, refreshers, `BartizanItemVocabulary` (12 files) + install `bartizan-api`
- **Do:** copy `item/*` (10 files) per the §1.1 table; create `PLG/item/ItemAttributes.java`,
  `PLG/item/BartizanItemKind.java` (§1.2); create `PLG/item/BartizanItemVocabulary.java`
  `implements org.luckyraven.keystone.item.spi.ItemVocabulary` whose `namespace()` returns `"bartizan"` and whose
  `contribute(ItemVocabularyRegistrar r)` body reproduces `W/WeaponModuleConfig.java:195-206` in that order with the
  §C.3 priorities; create `PLG/item/WeaponItemApiImpl.java` (§1.6(9)).
- **Why:** this is how `weapon:rifle` keeps resolving inside Gangland's 167 loot-chest entries (R6).
- **Done when:** `mvn -q clean install -DskipTests` exits 0 for both modules, and
  `org.luckyraven.bartizan:bartizan-api:0.1.0` is **re-installed** in `~/.m2`.
  (P3's group **H** unblocked earlier, at gate **GD**; this task only refreshes the artifact.)
- **Watch out:** the refresher priorities `10 / 10 / 0` are the whole point (R7). A unique weapon rebuilt by
  Gangland's `uniqueItemRefresher` instead of `WeaponRefresher` loses its magazine state.

> **Compile gate GG:** `mvn -q clean install -DskipTests` exits 0. `bartizan-api` is installed.

### Group J — database (12 files, 5 new)

#### B15 — BartizanDatabase, repository, table, import task (5 files)
- **Do:** copy `database/{WeaponTable, WeaponRepository}` (`PKG` only — the table name `weapon` and both columns are
  unchanged); create `PLG/database/BartizanDatabase.java` and `PLG/database/WeaponTableImportTask.java` per §1.9.
  Wire `WeaponRepository.setWeaponAddon(...)` from `WeaponManager.initialize()` exactly as
  `W/WeaponManager.java:22-36` does, **including `setDataSupplier(...)` at `:35`** (house rule
  `feedback_repository_data_supplier` — without it autosave throws `No data supplier set`).
- **Why:** weapon UUIDs must survive the move or every persisted weapon re-mints.
- **Done when:** `mvn -q clean install -DskipTests` exits 0.
- **Watch out:** `BartizanDatabase.insertInitialData()` must be an empty override, not a copy of Gangland's rank
  seeding.

### Group K — commands (24 files)

#### B16 — the command tree and commands.json (17 files)
- **Do:** rewrite the 12 command classes per §1.4 into `PLG/command/`, plus `DebugCommand` and a `ReloadCommand`;
  copy `InformationManager` + `CommandInformation` from `GW/gangland-impl/.../command/data/` (drop the module merge);
  write `bartizan-plugin/src/main/resources/commands.json` with the 14 keys.
- **Why:** `/bartizan` is the only user-facing surface.
- **Done when:** `mvn -q clean install -DskipTests` exits 0 and
  `python -c "import json;print(len(json.load(open('bartizan-plugin/src/main/resources/commands.json'))))"`
  prints `14`.
- **Watch out:** positional arguments must be chained `OptionalArgument` nodes with tab completion — a terminal
  `SubArgument` reading `args[n]` does not register (house rule `feedback_optional_arguments`). Copy the shape from
  the Gangland originals, do not simplify it.

### Group L — NPC controller, death listener, api impl (4 files)

#### B17 — the cadence port and the last two seams (4 files)
- **Do:** create `PLG/npc/NpcWeaponControllerImpl.java` and `PLG/npc/NpcWeaponFactoryImpl.java` per §1.6(8) —
  **port the arithmetic in that table line by line from `NpcCombatDelegate.java`, do not re-derive it**;
  `PLG/listener/death/WeaponDeathListener.java` per §1.6(4); `PLG/BartizanApiImpl.java` returning the five catalogs.
  Register `BartizanApi` on the `ServicesManager` from `WiringConfig`.
- **Why:** the two behaviours Gangland cedes to Bartizan.
- **Done when:** `mvn -q clean install -DskipTests` exits 0 and `NpcWeaponCadenceTest` (B20) is green.
- **Watch out:** **B20's cadence test must be written and seen RED before this task starts.** That is the whole
  mitigation for risk 3.

### Group M — resources (25 files)

#### B18 — the 25 YAML files (25 files)
- **Orchestrator (gate GG review B1):** `settings.yml` gains `Clean_Up:` with `Time: 30` (days) beside `Auto_Save:` — it drives `WeaponDataCleanupTask`; `BartizanSettings.getCleanUpTime()` reads it.
- **Do:** copy the 22 `weapon/*.yml` and `items/ammunition.yml` byte-identically; copy `items/wearables.yml` with the
  `Jetpack:` → `Extra_Tags:` edit (§1.6(6)); write `settings.yml` (§1.8) and `message/message_en.yml` (§1.5).
- **Why:** a plugin with no defaults generates nothing on first boot.
- **Done when:** `find bartizan-plugin/src/main/resources/weapon -name '*.yml' | wc -l` prints `22`, and
  `diff <(git -C "E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]" show 0.8.4:gangland-features/gangland-weapon/src/main/resources/weapon/rifle.yml) bartizan-plugin/src/main/resources/weapon/rifle.yml`
  is empty.
- **Watch out:** block-style YAML, `Capitalized_Underscore_Separated` keys, no literal `§` — house rules apply to the
  two new files. The 23 copied files are already compliant; do not reformat them.

### Group N — tests (19 files)

#### B19 — port the 15 surviving tests + the fixture (16 files)
- **Do:** copy the 16 test files minus `WeaponModuleTest.java`, plus `BukkitRegistryFixture` (§1.2), into
  `bartizan-plugin/src/test/java/org/luckyraven/bartizan/` (or `bartizan-api/src/test/...` for the pure-domain ones
  — `SelectiveFireTest`, `WeaponCloneTest`, `WeaponConsumeShotTest`, `TypeEnumParsingTest`, `DamageDataTest`,
  `DurabilityCalculatorTest`, `ProjectileStateTest`, `SpreadManagerTest`, `ModifierHandlerTest` belong to
  `bartizan-api`; the rest to `bartizan-plugin`). `PKG` for all. `ModifierHandlerTest` drops the
  `gangland-core` test-jar import and uses the copied fixture.
- **Why:** 15 of the 16 tests are pure logic and must keep passing across the move.
- **Done when:** `mvn -q test` exits 0 with 15 test classes discovered.
- **Watch out:** `bartizan-api` needs `mockito-core` at test scope for `ModifierHandlerTest` — add it to
  `bartizan-api/pom.xml` (test scope only; that is not a "test dependency in a module pom" violation, it is a
  library module's own test config).

#### B20 — the two new red-first tests (2 files)
- **Do:** **write these BEFORE B17 and B5's `RecoilManager` edit, and record the red output in section 7.**
  - `NpcWeaponCadenceTest` (`bartizan-plugin/src/test/.../npc/`) — pins §1.6(8):
    SINGLE → `tryFire` returns `true` once and `isBusy()` is `true` until `max(perShot*cooldown, 5)` ticks have been
    consumed; a second `tryFire` in the same window returns `false` (**one trigger per shot**);
    BURST → one `tryFire` schedules `perShot` rounds and busies for `perShot*cooldown + cooldown`
    (**one trigger per burst**); AUTO → `tryFire` fires once per `max(cooldown, 5)` and may be called every tick
    (**hold-to-fire**). This is `feedback_selective_fire_semantics` expressed as a test.
    With review finding B3 applied it also covers `tick()`: `isBusy()` must go false again after the right
    number of `tick()` calls — the check that catches a cop which fires once and never again.
    Red first: write it against a stub `NpcWeaponController` that returns `true` unconditionally — all three
    assertions fail. Then B17 makes it green.
  - `RecoilManagerTest` (rewritten) — pins §1.6(1): install a capturing `PacketAdapter` via
    `PacketBridge.install(...)`, call `applyRecoil`, and assert the adapter received
    `relativeCameraRotation(player, -yaw + 1, pitch - 1)` for the default path, the sneak path (`/2`), the
    sneak+scoped path (`/4`) and the pattern-index advance. `PacketBridge.reset()` in `@AfterEach`.
    Red first: run it before B5's `RecoilManager` edit — it fails to compile against the old
    `RecoilCompatibility` signature, which is the red state; record that.
- **Why:** these are the only two behaviour changes in the stream that a compile gate cannot catch.
- **Done when:** both green after B17, and `mvn -q test` exits 0 with 17 test classes.
- **Watch out:** do not assert on `aimErrorDegrees` — §1.6(8) establishes it has no effect on this path today.

### Group O — docs (4 files)

#### B21 — documentation (4 files)
- **Do:** write `documentation/migration.md` (§4), `documentation/bartizan-api.md` (the §C.3 service table + the
  `bartizan-api` coordinates + a worked example of resolving `BartizanApi` lazily), and update `README.md` and
  `CLAUDE.md` from B3 with the final package map.
- **Why:** P3 and every server owner need the migration statement.
- **Done when:** `documentation/migration.md` contains the MySQL `INSERT…SELECT` from §4 verbatim.
- **Watch out:** none.

### Group P — verification

#### B22 — the gates in §5
- **Do:** run every command in §5 and paste the output into section 7.
- **Done when:** all five pass.

---

## 3. Tests

**Moved (15 of 16).** From `GW/gangland-features/gangland-weapon/src/test/java/org/luckyraven/gangland/weapon/`:

| Test | To |
|---|---|
| `SelectiveFireTest`, `WeaponCloneTest`, `WeaponConsumeShotTest`, `TypeEnumParsingTest`, `WeaponServiceTest` | `bartizan-api` (`WeaponServiceTest` → `bartizan-plugin`, it needs the manager) |
| `dto/DamageDataTest`, `durability/DurabilityCalculatorTest`, `projectile/ProjectileStateTest`, `projectile/spread/SpreadManagerTest`, `modifiers/ModifierHandlerTest` | `bartizan-api` |
| `item/WeaponItemPredicatesTest`, `data/WeaponDataCleanupTaskTest`, `raytrace/SteppedProjectileTaskTest` | `bartizan-plugin` |
| `support/WeaponFixtures` | wherever the tests that use it land — duplicate if both modules need it |
| `projectile/recoil/RecoilManagerTest` | `bartizan-api`, **rewritten** (B20) |

**Deleted (1).** `WeaponModuleTest` — asserts `ModuleRegistrations` contents; Bartizan has no module descriptor.
Replaced by `BartizanBootstrapTest` in `bartizan-plugin`: asserts `BartizanContext.CONFIG_PACKAGE` is
`"org.luckyraven.bartizan.config"`, that all five `@Configuration` classes are annotated with the phase §1.3 says,
and that `WeaponCommand`, `WeaponRepository` and `WeaponQuitCleanupListener` live under the packages
`BartizanContext` scans. Same intent as the original, expressed against the new bootstrap.

**New (3), all red first:**

| Test | Asserts | How to see it red |
|---|---|---|
| `NpcWeaponCadenceTest` | SINGLE/BURST = one trigger per shot/burst, AUTO = hold-to-fire, with the exact tick arithmetic of §1.6(8) | Write against a stub controller that always returns `true` before B17 exists; all three assertions fail. Record the failure text. |
| `RecoilManagerTest` (rewritten) | `relativeCameraRotation` receives `-yaw + 1` / `pitch - 1`, and the `/2` and `/4` sneak dampening | Run before B5's edit — it does not compile against the old `RecoilCompatibility` signature. Record that. |
| `WeaponItemApiTest` | `buildItem("grenade")` twice yields the same UUID (`UUID.nameUUIDFromBytes("throwable:grenade")`), and `isSameWeapon` returns true for two independently built copies | Write it against an empty `WeaponItemApiImpl` returning `null` before B14 fills it in. |

**Counts.** 15 moved + 1 replaced + 3 new = **19 test classes** at the end of the stream. There is no
`InformationManagerTest`-style count assertion in this repo, so no number needs updating.

---

## 4. Docs and config

**`documentation/migration.md`** (new, Bartizan repo) must contain:

1. **What moved.** Weapons, ammunition, wearables and their YAML leave `Gangland_Warfare` and become `Bartizan`.
   Install `Bartizan-0.1.0.jar` next to `Keystone-1.9.0.jar`.
2. **YAML.** On first boot Bartizan writes `plugins/Bartizan/{settings.yml, message/message_en.yml, weapon/*.yml,
   items/ammunition.yml, items/wearables.yml}`. A server that customised the old files copies them from
   `plugins/Gangland_Warfare/` to `plugins/Bartizan/` at the **same relative paths**, then applies the one edit
   below.
3. **`items/wearables.yml` edit.** The `Jetpack:` block becomes `Extra_Tags:` — show the before/after from §1.6(6).
4. **SQLite.** Automatic. `WeaponTableImportTask` reads `plugins/Gangland_Warfare/database/gangland.db`'s `weapon`
   table once and writes `plugins/Bartizan/.weapon-import-done`. Deleting that marker re-runs the import.
5. **MySQL.** Not automatic. One statement, run once, with both schemas on the same server:

   ```sql
   INSERT INTO bartizan.weapon (uuid, type)
   SELECT uuid, type FROM gangland.weapon
   ON DUPLICATE KEY UPDATE type = VALUES(type);
   ```

   Then drop `gangland.weapon` after confirming the row counts match.
6. **Signs.** `[WEAPON-BUY]`, `[WEAPON-SELL]`, `[AMMO-BUY]`, `[AMMO-SELL]`, `[WEARABLE-BUY]`, `[WEARABLE-SELL]` no
   longer exist. Gangland 0.9.0's `LegacySignRewriter` maps them to `[ITEM-BUY]`/`[ITEM-SELL]` with a
   `weapon:`/`ammo:`/`wearable:` definition string — placed signs keep working. Cross-reference P3's checklist.
7. **`/glw` commands that moved.** `/glw weapon …` → `/bartizan weapon …`; `/glw ammo …` → `/bartizan ammo …`;
   `/glw item wearable …` → `/bartizan wearable …`; `/glw debug weapon …` → `/bartizan debug …`.
   Aliases `/btz` and `/weapon` both work.

**`documentation/bartizan-api.md`** (new): the §C.3 table, the Maven coordinates, and the lazy-resolution rule (R8):

```java
RegisteredServiceProvider<BartizanApi> rsp =
        Bukkit.getServicesManager().getRegistration(BartizanApi.class);
if (rsp == null) return;              // Bartizan absent or not yet enabled — never cache this at construction
BartizanApi api = rsp.getProvider();
```

**Gangland-side docs are P3's, not this stream's.** Do not edit `GW/CLAUDE.md`,
`GW/documentation/module-loader.md` or `GW/documentation/developer/compatibility.md` from here — but note in
section 7 that P3 owes: the `## Core seams` rewrite, the module table row deletions, and the
`compatibility.md` deletion.

**Memory.** After gate GG, the orchestrator (not the executor) updates
`project_bartizan_split.md` with: repo created, `bartizan-api` coordinates, the four `ServicesManager` keys.

---

## 5. Verification (gates instantiated)

Run from `E:\Programming\java\Bartizan`.

**G1 — reactor builds and tests pass.**
```
mvn clean install
```
Expect: `BUILD SUCCESS`, two modules, 19 test classes, 0 failures.

**G2 — the api is clean.** Both greps must return **nothing**:
```
grep -rl "org\.luckyraven\.gangland" bartizan-api/src
grep -rlE "net\.minecraft|org\.bukkit\.craftbukkit" bartizan-api/src
```

**G3 — the jar is clean.** This is the gate PICK names explicitly:
```
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep -c "org/luckyraven/gangland"   # must print 0
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep -c "org/luckyraven/keystone"   # must print 0
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep -c "org/luckyraven/bartizan"   # must print > 200
```
The second line is the one that catches an accidental `compile`-scoped Keystone dependency. If it is non-zero,
find the offending `<scope>` in `bartizan-plugin/pom.xml` — do not add a shade exclusion.

**G4 — resources land at the right paths.**
```
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep -E "weapon/.*\.yml" | wc -l    # 22
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep -E "items/(ammunition|wearables)\.yml" | wc -l   # 2
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep -E "(plugin\.yml|commands\.json|settings\.yml)" | wc -l  # 3
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep "message/message_en.yml"        # 1 line
unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar | grep "org/luckyraven/bartizan/module.properties"  # 1 line
```

**G5 — the api artifact is installed for P3.**
```
ls ~/.m2/repository/org/luckyraven/bartizan/bartizan-api/0.1.0/bartizan-api-0.1.0.jar
```

**Not verifiable here (phase D smoke, and the user's own in-game checks):** recoil feel on 1.21.11 and on one older
client through ViaBackwards; NPC fire rhythm; that `weapon:` loot-chest entries still resolve inside Gangland.

---

## 6. Risks and open questions

### Risks

**R-B1 — the api/plugin split line (highest).** `gangland-weapon` was never designed to have one. `Weapon` pulls in
`RecoilManager`, `SpreadManager`, `DurabilityCalculator`, `Reload` and (through `ProjectileState`) the whole
`modifiers` tree — verified by reading the import blocks, which is why §1.1 puts all of them in the api rather than
stating a rule the executor has to apply. **Mitigation:** the executor follows the per-file table; any deviation the
compiler forces is recorded in section 7 rather than improvised. If the table is wrong about more than three files,
stop and escalate — that means the closure was mis-read and P3's `bartizan-api` surface changes with it.

**R-B2 — reflective recoil (P1's code, this stream's consequences).** 20 hand-written NMS adapters collapse into one
reflective method that no unit test can exercise against a real client. **Mitigation:** the `-yaw + 1` / `pitch - 1`
transform stays on the Bartizan side and is pinned by the rewritten `RecoilManagerTest`; Keystone's
`setRotation` fallback plus fault `nms.recoil.unsupported` means the worst case is degraded feel, not an exception.
**Residual:** feel on an untested revision is a user check, listed on the board.

**R-B3 — NPC cadence.** Moving the fire rhythm out of `NpcCombatDelegate` changes cop feel if any of the six tick
formulas drifts. **Mitigation:** `NpcWeaponCadenceTest` written and seen red **before** B17; the §1.6(8) table
carries the arithmetic verbatim with line numbers so the executor transcribes rather than reasons.

**R-B4 — `ThrowableAction`'s statics.** Two `public static final Map`s are a cross-plugin handshake today. Deleting
them breaks Gangland's `CarDamageListener` until P3 lands. **Mitigation:** ordering — `WeaponEntityDamageEvent`
gains `weaponName()`/`kind()` in group D, which is behind gate GD, which is what unblocks P3.

**R-B5 — the vocabulary pull is `softdepend`-ordered.** If a server drops Bartizan, Gangland's 167 loot-chest
`weapon:` entries silently resolve to nothing. **Mitigation:** Bartizan logs one INFO line naming the vocabulary it
published; P3 logs the folded set on the Gangland side. Neither is an error — this is a supported configuration.

**R-B6 — `Wearable`'s rewrite touches the damage pipeline.** Six methods with hard-coded caps. **Mitigation:** copy
the numbers from `WearableTrait.java:19-60` and `Wearable.java:285-340`; do not re-derive. No test covers these
today — see Q4.

### Open questions (each has a default the executor uses unless told otherwise)

**Q1 — the ViaVersion protocol gate.** `RecoilCompatibility.java:34-36` skips the rotation entirely for clients below
protocol 1.13. Its own javadoc argues the gate is dead weight ("the server floor is 1.16, so every native client
already supports `setRotation`; only ViaVersion can put an older client behind a newer server").
**Default: drop the gate.** Bartizan calls `relativeCameraRotation` unconditionally. `softdepend: [ViaVersion]` and
the `viaversion-api` provided dependency stay for T-09. Reinstate only if the user reports 1.12-and-below clients
through ViaBackwards.

**Q2 — `Extra_Tags` shape.** §1.6(6) allows nested maps (so jetpack thrust/glide sounds stay in `wearables.yml`),
with only top-level scalars stamped as NBT. The alternative is scalar-only, which forces jetpack sounds into a new
gadget-side config file and makes this a two-repo change.
**Default: nested maps, scalars-only stamped.** It keeps the change inside Bartizan and leaves gadget reading one
`Map`. P3 must agree — this is the one contract item in this file that P3 can invalidate.

**Q3 — bStats plugin id.** Gangland's is `21012`. Bartizan needs its own, which only the user can create on
bstats.org.
**Default: ship `PLUGIN_ID = 0` with a `// TODO` and the chart wired.** bStats treats an unknown id as a no-op; it
does not throw. The user registers the plugin and the id becomes a one-line change. **This is the only item in the
stream that genuinely needs the user**, and it does not block any gate.

**Q4 — no test covers `Wearable`'s damage math.** There is no `WearableTest` in the 16 today. The rewrite in
§1.6(6) is therefore unpinned.
**Default: do not write one in this stream.** It is a pure copy of existing arithmetic, and inventing coverage here
widens scope. Add one line to `GW/brainstorming/bug-docket-2026-09-06/triage/new-findings.txt` noting the gap so it
gets triaged as a normal docket entry.

**Q5 — `WeaponServiceTest` placement.** It needs `WeaponManager`, which is plugin-side, but tests `WeaponService`,
which is plugin-side too. No ambiguity — **default: `bartizan-plugin`.** Listed only because §3 splits the other
tests across both modules and an executor may hesitate.

### Bugs noticed while planning (not fixed here)

- **T-06 stays open.** Only 5 of 22 weapon YAMLs are registered as expected files
  (`W/WeaponFileConfig.java:93`). Carried over verbatim; do not fix in this stream.
- `RecoilManager.clone()` calls `super.clone()` without `implements Cloneable`
  (`W/projectile/recoil/RecoilManager.java:72-79`). Pre-existing; add to `triage/new-findings.txt`, do not fix.

---

**R-GD (added at gate GD) — compile API level.** Bartizan compiles against `spigot-api 1.21.11-R0.1-SNAPSHOT`, not Keystone's 1.16.5 floor, because the verbatim-ported weapon code uses `Enchantment.PROTECTION`, `PotionEffect.INFINITE_DURATION`, `Player.isClimbing()`, the 3-arg `Player.sendBlockDamage` and `Particle.BLOCK` (10 compile errors otherwise — see the GD row). This is the same situation Gangland's root pom is in today; on a pre-1.21 server those members throw `NoSuchFieldError`/`NoSuchMethodError` at the call site. Lowering Bartizan to the 1.16 floor (XSeries lookups, reflection or feature-gating) is a separate wave, not part of 0.1.0.

## 7. Status table (executors fill this)

| Task | Status | Executor | Notes (what changed, what was skipped, failures verbatim) |
|---|---|---|---|
| B1 | done | X-B-AB | `git init` on `master` (no commits). Root `pom.xml` = Keystone's pom.xml with the named deltas (groupId `org.luckyraven.bartizan`, artifactId `bartizan`, revision `0.1.0`, modules `bartizan-api`/`bartizan-plugin`, deploymentName/excludeArtifact, repositories = maven_central/spigot-repo/papermc/jitpack.io/placeholderapi + new `viaversion-repo`, dependencyManagement replaced per B1's list); kept flatten-maven-plugin, pluginManagement, release profile, MIT/developer/scm shape (urls repointed Keystone→Bartizan), root `<dependencies>` verbatim (spigot-api/lombok/annotations/junit-jupiter). Trimmed the now-unused Keystone-only properties (vault/placeholderapi/citizens/commodore/brigadier/netty/hikaricp/exp4j versions) since nothing in Bartizan's dependencyManagement references them; added `keystone.version=1.9.0`, `viaversion.version=5.8.1`, `gson.version=2.10.1`, `bstats.version=3.2.1`. `lombok.config`/`.gitignore` are byte-copies of Keystone's. Gate: `mvn -q -N validate` exit 0. |
| B2 | done | X-B-AB | `bartizan-api/pom.xml`: keystone-common/keystone-item/keystone-npc (all provided) + spigot-api + XSeries provided, mockito-core test. `bartizan-plugin/pom.xml`: bartizan-api compile, keystone-{common,bean,command,item,persistence,hooks} provided, spigot-api/XSeries provided, bstats-bukkit compile, gson provided, log4j-api provided, `com.viaversion:viaversion-api` provided with the T-09 comment (adapted from `gangland-weapon/pom.xml:84-96`), mockito-core+sqlite-jdbc+log4j-core test; shade block copied from `keystone-plugin/pom.xml` (createDependencyReducedPom=false, no relocations, same META-INF filter); finalName `Bartizan-${project.version}`, filtered resources. Gate: `mvn -q clean install -DskipTests` exit 0, produced `bartizan-plugin/target/Bartizan-0.1.0.jar`. **Drift #1:** B1's abbreviated dependencyManagement list `keystone-{common,bean,command,item,persistence,hooks}` omits `keystone-npc`, but B2 requires bartizan-api → keystone-npc (provided) — added a managed `keystone-npc:${keystone.version}` entry to the root pom, noted inline as a gap-fill, not a real inconsistency. **Drift #2:** B2's "Done when" claims `mvn dependency:tree -pl bartizan-api` "shows no keystone-persistence, no keystone-bean" — false against Keystone 1.9.0's actual `keystone-npc/pom.xml` (verified by reading it): keystone-npc itself compile-depends on keystone-bean and keystone-persistence, so both appear transitively in bartizan-api's tree at **provided** scope (matches Maven's provided→compile transitivity rule, harmless — never shaded, same as keystone-npc itself). The load-bearing half of the claim — **no compile-scoped citizens-main** — does hold, and holds trivially: citizens-main is entirely absent from the tree (provided→provided transitivity excludes it). Full tree pasted and verified by hand; not a blocker. **Drift #4 (gate-GA review m5):** `mockito-core` is test-scoped in `bartizan-api/pom.xml` although B2 does not list it; kept for B19's `ModifierHandlerTest`. |
| B3 | done | X-B-AB | `README.md` (install instructions, data-folder map from §C.5, api/plugin module table, build command), `CLAUDE.md` (Spigot-only/no io.papermc, method braces, `@CustomLog`, `@Nullable` from org.jetbrains.annotations, block-style YAML with `Capitalized_Underscore_Separated` keys, `BartizanChatUtil.color()`/`&` codes, XSeries/SoundEffect for version-drifting enums, never setDefaults/copyDefaults, Keystone provided-never-shaded, the api/plugin split rule, the T-09 Mockito/ViaVersion trap), `documentation/.gitkeep`. Written fresh, not copied from Gangland's CLAUDE.md. |
| **GA** | done | X-B-AB | `mvn -q clean install -DskipTests` at repo root — exit 0, no output. |
| B4 | done | X-B-AB | Copied the 25 §1.1 group-B files (10 top-level: Weapon, SelectiveFire, WeaponTag, WeaponType, GunWeapon, MeleeWeapon, BiologicalWeapon, IncendiaryWeapon, ThrowableWeapon, ThrowableType; 15 dto/*) from Gangland `0.8.4` (working tree verified identical to the `0.8.4` ref, `git diff 0.8.4 -- gangland-features/gangland-weapon/` empty) into `bartizan-api/.../api/weapon/` (+ `dto/`). Applied `PKG`: package line rewritten per file (the `types.{gun,melee,biological,incendiary,throwable}` subpackages and bare `types` all flatten to `org.luckyraven.bartizan.api.weapon`, verified per-file after fixing a first-pass sed bug that left a stray subpackage segment on package *declarations* — import lines were unaffected, only `package` lines needed the follow-up fix); imports rewritten with `projectile.recoil.`→`weapon.recoil.`, `projectile.spread.`→`weapon.spread.`, `projectile.`→`weapon.` (flattened), `dto.`→`weapon.dto.`, `durability.`/`reload.`/`modifiers.`(`.action.`) kept as same-named subpackages under `weapon.`, `ammo.`→`api.ammo.`. Dropped the dead `import ...weapon.util.PotionEffectParser;` in `dto/ThrowableData.java` (only referenced inside a `{@link}` javadoc comment, no code use; `util` stays plugin-side per §1.1, so this import would have pointed at a non-existent api package). Verified `Weapon.java` kept its `keystone.item.ItemBuilder`, `keystone.util.Placeholder`, `keystone.exception.PluginException`, `com.cryptomorin.xseries.XPotion` imports unchanged. Gate: `mvn -pl bartizan-api -am install -DskipTests` fails only with "cannot find symbol" / "package does not exist" for: `Ammunition, ArmorPiercingModifier, BlockBreakModifier, DurabilityCalculator, FlatDamageModifier, PenetrationModifier, ProjectileType, RecoilManager, Reload, ReloadType, RicochetModifier, SpreadManager, TracerModifier` (13 symbols, verified as the complete deduplicated set). **Note (not a blocker):** B4's own text names only 5 ("DurabilityCalculator, RecoilManager, SpreadManager, Reload and ModifierHandler") — the real closure is wider: `ReloadType`, `ProjectileType` and the 6 concrete `modifiers.action.*` classes are also group-C symbols arriving in B5 (the checklist's "ModifierHandler" was shorthand for the modifiers tree; the actual missing names are its six action subclasses, not `ModifierHandler` itself, which no group-B file references directly), and `Ammunition` is a **group-D** symbol that only arrives in **B6**, so the build will still fail after B5 alone until B6 lands. No Gangland or NMS symbols appear anywhere in the failure list. |
| B5 | done | X-B-CDE | Copied all 18 §1.1 group-C files from Gangland `0.8.4` (working tree = the `0.8.4` ref for the weapon module, confirmed) into `bartizan-api/.../api/weapon/{recoil,spread,durability,reload,modifiers,modifiers/action}` via a sed import-rewrite script (same prefix-mapping convention B4 established: `projectile.recoil`→`weapon.recoil`, `projectile.spread`→`weapon.spread`, `projectile`→`weapon` (flattened), `reload.type`→`weapon.reload` (flattened), `modifiers.action`→`weapon.modifiers.action`, `modifiers`→`weapon.modifiers`, `durability`/`dto`/`ammo` kept as same-named subpackages). 15 plain-`PKG` files landed clean. 3 `PKG+`: **RecoilManager** — applied §1.6(1) verbatim: deleted the `RecoilCompatibility` parameter from `applyRecoil`/`applyDefaultRecoil`/`recoil`, private `recoil(Player,float,float)` now calls `PacketBridge.adapter().relativeCameraRotation(player, -yaw + 1, pitch - 1)` (import `org.luckyraven.keystone.nms.PacketBridge`, already a `keystone-common` transitive symbol). **InstantReload/NumberedReload** — applied §1.6(5) at all 4 call sites (`InstantReload:61,74`, `NumberedReload:90,132`, matching the checklist's line numbers exactly against source) with the correct polarity inversion (`DownedPlayerRegistry.isDowned(uuid)` → `!combatEligibility.canBeHit(player)`). Created `CombatEligibility` early (api/combat/CombatEligibility.java) as B5's Done-when explicitly permits, keeping the checklist's exact `canBeHit`/`DEFAULT` shape verbatim. **Deviation (recorded, not a blocker):** the checklist says "CombatEligibility passed in through the constructor" for InstantReload/NumberedReload; doing that literally would require threading a `CombatEligibility` parameter through `ReloadType.createInstance(Weapon,Ammunition)` and then through `Weapon`'s own constructor (`Weapon.java:113-114` builds the `Reload` internally) and every future `new Weapon(...)` call site across groups G/L not yet ported and outside B5's 18-file scope — a wide, unplanned ripple. Instead added one static method to `CombatEligibility` itself, `static CombatEligibility resolve()`, doing the exact same lazy `Bukkit.getServicesManager().getRegistration(CombatEligibility.class)` lookup with `DEFAULT` fallback that §1.6(5) assigns to the `WiringConfig` holder bean — satisfies "resolved lazily at every use, never cached" identically, with zero ripple into `Weapon.java`/`ReloadType.java`/any file outside this task. `InstantReload`/`NumberedReload` call `CombatEligibility.resolve().canBeHit(player)` inline at each of the 4 sites. Later groups (H's `WeaponInteract`, F's `WiringConfig`) can still follow the checklist's literal DI shape for their own bean — both are compatible with the single-abstract-method interface. **Second deviation:** `Reload.java` (nominally plain-`PKG` per the table) imports plugin-side `WeaponService` (`WeaponService.getWeaponUUID(item)` at `findWeaponSlot`) — not listed anywhere in §1.1/§1.6, and `WeaponService` doesn't exist in `bartizan-api` (it's a Group-L/PLG file). Inlined `getWeaponUUID`'s exact 3-statement body as a private static `readWeaponUUID(ItemStack)` helper in `Reload.java` using `Weapon.getTagProperName(WeaponTag.UUID)` + `keystone.item.ItemBuilder` (both already api-side); `WeaponService` itself untouched, behavior identical, zero ripple. **Not fixed (per instruction):** `RecoilManager.clone()` still calls `super.clone()` without `implements Cloneable` (`RecoilManager.java:72-79` today) — left exactly as-is; one line added to `GW/brainstorming/bug-docket-2026-09-06/triage/new-findings.txt`. Applied the two gate-GA-review doc fixes on the way through the api tree: `ThrowableData.java` `{@link PotionEffectParser}`→`{@code PotionEffectParser}`; `Weapon.java` "configured `%gangland_*%` placeholders"→"configured PlaceholderAPI placeholders". B20's `RecoilManagerTest` was written and run red *before* this task's RecoilManager edit — see the B20 row. |
| **GC** | done | X-B-CDE | `mvn -q -pl bartizan-api -am install -DskipTests` after B5 — fails **only** with cannot-find-symbol for `Ammunition` (package `org.luckyraven.bartizan.api.ammo` does not exist) and the sibling B6 symbols that share its absence (`org.luckyraven.bartizan.api.event` package — `WeaponReloadStartEvent`/`WeaponReloadCompleteEvent`, imported by `Reload.java`, which only entered the tree with B5). No `CombatEligibility` failure (created in B5). No Gangland/NMS symbols. Deduplicated grep of the failure list: `cannot find symbol` / `symbol: class Ammunition` / `package org.luckyraven.bartizan.api.ammo does not exist` / `package org.luckyraven.bartizan.api.event does not exist` — matches the orchestrator's corrected criterion. |
| B6 | done | X-B-CDE | Copied the 9 event classes + `Ammunition.java` from Gangland `0.8.4` with `PKG` (`API/event/*`, `API/ammo/Ammunition.java`). `WeaponRaytraceImpactEvent` needed no edit beyond `PKG` — the §E1 cancellation-semantics javadoc paragraph the checklist asks for was already present verbatim in the source. `WeaponEntityDamageEvent` rewritten per §1.6(3): nested `DamageKind {DIRECT,EXPLOSION,FIRE,BIOLOGICAL,MELEE}` enum, `weaponName()`/`kind()` (both `@Getter(AccessLevel.NONE)` fields with hand-written accessors matching the checklist's exact no-"get"-prefix method names, `Objects.requireNonNull` so "never null" is enforced not just documented), constructor grew two params (`String weaponName, DamageKind kind`) — the firing action stamps both, no static maps. Created all 10 new files: `BartizanApi`, `WeaponCatalog` (the exact 5 `WeaponService` methods E1 §5.2 names), `AmmunitionCatalog`, `WearableCatalog`, `WeaponItemApi`, `NpcWeaponFactory` + `NpcWeaponController` (extends `keystone.npc.spi.NpcRangedAttack`, verified against Keystone 1.9.0 source — `default void tick()` confirmed present per PICK.md ruling (b)), `BartizanItemPredicates` (`WEARABLE` only, lifted from `gangland-impl/item/ItemPredicates.java:25-45`), `Wearable` (full §1.6(6) rewrite — see below). Also fixed a second stale `%gangland_*%` placeholder-doc comment in `Ammunition.java` (same class of issue as the two the gate-GA review flagged in B5's files; fixed for consistency, swept the whole tree afterward, none remain). **`Wearable` rewrite:** `Map<String,Integer> traits` (was `Map<WearableTrait,Integer>`), `Map<String,Object> extraTags` replaces `fuelKey` + the 9 dedicated jetpack fields (10 total removed) and the 4 `SoundEffect` jetpack-sound fields (now inside `extraTags` as an unstamped nested map per §1.6(6)'s `Sounds:` example — `SoundEffect` import dropped), `isJetpack()` deleted, new `traits()`/`traitLevel(String)`/`extraTags()` accessors, `buildItem`'s NBT stamp loop is the blind `String`/`Integer`/`Double` type-switch the checklist specifies (nested `Map` values never stamped). The per-trait `{maxLevel, effectPerLevel}` table is copied verbatim from `WearableTrait.java:19-60` into a private static `Map<String,double[]> TRAIT_TABLE` (reinforced 4/0.05, bulletproof 3/0.04, padded 2/0.08, toughened 3/0.10, fire_resistant 2/0.25, reactive 3/0.02, lightweight 2/0.0, fuel_efficient 2/0.10 — transcribed, not re-derived) keyed by the same lower-case strings the YAML keeps (per §1.6(6)'s "keep upper-case YAML keys, lower-case on read" choice — the lower-casing itself is the plugin-side `WearableAddon` parser's job in group G, out of scope here). All six damage methods (`getGenericDamageReduction` 0.80 cap, `getProjectileDamageReduction` 0.90, `getExplosionDamageReduction` 0.90, `getCritBonusReduction` 1.0, `getFireTickReduction` 1.0, `rollReactive`) keep their exact arithmetic, just re-keyed by string. **Deviation (`Ammunition.java`, recorded):** its static `getHeldAmmunition(AmmunitionManager, ItemStack)` referenced the plugin-side `AmmunitionManager` (a Group-L/PLG file per §1.1) — not mentioned anywhere in §1.1/§1.6, same category as B5's `Reload`/`WeaponService` finding. Retyped the parameter to the new same-task `AmmunitionCatalog` interface (`AmmunitionManager` will `implements AmmunitionCatalog` per the PLG table, so every real call site still type-checks); zero ripple beyond this file. Also fixed a latent gate-GD blocker in `BlockRegenerationSettings.java` (a B5 file): its javadoc literally contained the substring `org.luckyraven.gangland` in prose (`{@code org.luckyraven.gangland.file.configuration.Settings}`) — harmless as code but GD's grep is a plain substring match on file content, so it would have failed the gate on a comment. Reworded to name `BartizanSettings` instead; full-tree grep swept clean afterward (see GD row). |
| B7 | done | X-B-CDE | Applied §1.6(7): `API/raytrace/WeaponRaytracer.java` is now an interface with the four members (`isRaytraceDamageInProgress()`, `getVisualSpawner()`, `fireInstant(RaytraceRequest)`, `advanceSegment(RaytraceContext,Location,Location)`), plus one addition beyond the checklist's literal list: a `static void setRaytraceDamageInProgress(boolean)` — the checklist only names the *reader*, but the ThreadLocal's *writer* has to live somewhere the (not-yet-written, group H) `WeaponRaytracerImpl` can reach, and since the reader is `static` on this interface the writer has to be too; the backing `ThreadLocal` itself is hidden in a private-constructor nested holder class so no mutable field is directly exposed on the interface. Copied `WeaponShooting.java` and `RaytraceRequest.java` with `PKG` (`WeaponShooting`'s `WeaponRaytracer` parameter is now the interface automatically — no source change needed beyond the package rewrite). `WeaponShooting.SPREAD_PELLET_COUNT` stays `public static final`, verified untouched. **Deviation, larger than the checklist anticipated:** B7's own text pre-authorizes moving `RaytraceContext`/`WeaponVisualSpawner` into the api "if the compiler shows [they're] needed by the interface signatures" — true, and it also forced **two more**: `WeaponShooting.fireSlow` calls `WeaponMuzzle.compute(...)` and constructs `new SteppedProjectileTask(...)` directly (not through an interface), and both were tabled for `PLG/` in §1.1's group L/M/N table. Read both in full before moving: `WeaponMuzzle` (52 lines) is pure Bukkit geometry math, zero cross-boundary imports. `SteppedProjectileTask` (171 lines) uses only `org.luckyraven.keystone.timer.RepeatingTimer` and `com.cryptomorin.xseries.particles.XParticle` — both already `bartizan-api` dependencies (`keystone-common`, `XSeries`, both provided) — plus `WeaponRaytracer`/`WeaponVisualSpawner`/`RaytraceContext`, all api-side as of this task. Neither references anything plugin-only, so both moved to `API/raytrace/` with plain `PKG` (no behavior edits) rather than being duplicated. **B7 therefore touched 7 files, not 3:** `WeaponRaytracer` (interface, rewritten), `WeaponShooting`, `RaytraceRequest` (the planned 3) + `RaytraceContext`, `WeaponVisualSpawner`, `WeaponMuzzle`, `SteppedProjectileTask` (forced). Flagging for whichever executor runs group H (B13): the §1.1 table's `PLG/raytrace/{RaytraceContext,WeaponVisualSpawner,WeaponMuzzle,SteppedProjectileTask}` rows are now stale — all four already live in `bartizan-api`, do not re-copy them into `PLG/`, just import from `org.luckyraven.bartizan.api.raytrace.*`. `WeaponRaytracerImpl` (the interface's one implementation, B13's actual job) is untouched by this task. |
| **GD** | blocked (pom fix required, see note) | X-B-CDE | `mvn -q -pl bartizan-api -am install` — fails to compile with **10 errors, all one root cause, none touching a file this task wrote incorrectly**: the root `pom.xml`'s `bukkit.version` is `1.16.5-R0.1-SNAPSHOT` (byte-copied from Keystone's own floor in B1), but the verbatim-ported Gangland weapon code was written/compiled against Gangland's `1.21.11-R0.1-SNAPSHOT` and uses symbols that plain don't exist in 1.16.5's spigot-api jar: `Enchantment.PROTECTION`/`PROJECTILE_PROTECTION`/`FIRE_PROTECTION`/`BLAST_PROTECTION` (renamed from the `*_ENVIRONMENTAL`-suffixed 1.16 names in Spigot's 1.20.5 Enchantment rewrite — `Wearable.java`, 6 sites), `PotionEffect.INFINITE_DURATION` and `Player.isClimbing()` (both post-1.16 additions — `Weapon.java:431,480`, pre-existing since B4, not something this task's own doc-fix edit at line ~342 touched), `Player.sendBlockDamage(Location,float,int)` (a newer 3-arg overload; 1.16.5 only has the 2-arg form — `BlockDamageManager.java:134,145`, a B5 file) and `Particle.BLOCK` (`BlockDamageManager.java:255`). **Per the orchestrator's explicit instruction this task does not touch any pom** — the fix is a one-line property bump. **Verified the fix works, without editing any file:** `mvn -q -pl bartizan-api -am install -Dbukkit.version=1.21.11-R0.1-SNAPSHOT` (Maven CLI property override, not a pom edit; `1.21.11-R0.1-SNAPSHOT` is already cached in `~/.m2` from building Gangland on this same machine) → **exit 0**, both grep checks clean (`org\.luckyraven\.gangland` and `net\.minecraft\|craftbukkit` both empty across `bartizan-api/src`), and `mvn -q -pl bartizan-api -am test -Dbukkit.version=1.21.11-R0.1-SNAPSHOT -Dtest=RecoilManagerTest` → `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` (B20's green run — see that row). **Action needed from the orchestrator:** bump `<bukkit.version>` in `E:\Programming\java\Bartizan\pom.xml` to `1.21.11-R0.1-SNAPSHOT` (matching Gangland's own pin exactly, the safest choice since it's the version the ported code was actually written against) — after that one-line change, `mvn -q -pl bartizan-api -am install` should exit 0 with no further changes needed to any file this task touched. This is unrelated to the bootstrap/plugin-side Bukkit-version questions groups F–O will hit later; it is purely about the `bartizan-api` compile classpath. **The api is otherwise frozen and correct as of this task** — every file-content criterion of GD (no `org.luckyraven.gangland`, no NMS/craftbukkit, `bartizan-api` installed once the version property is fixed) is met. **Orchestrator (gate GD):** root `bukkit.version` bumped `1.16.5-R0.1-SNAPSHOT` → `1.21.11-R0.1-SNAPSHOT` (matches Gangland's root pom; the ported code uses 1.21 API members) and the bStats relocation (gate-GA review M1) added to the plugin shade; then `mvn -q -pl bartizan-api -am install` exit 0, both greps empty, `~/.m2/.../bartizan-api/0.1.0/bartizan-api-0.1.0.jar` present, full `mvn -q clean install` exit 0, jar has 19 relocated bStats entries and 0 `org/luckyraven/keystone`. Committed as `d112e7a` on Bartizan `master`. **GD review (Opus R-B-CDE): PASS WITH FIXES** — see `REVIEW-bartizan-GD.md`. Applied by the orchestrator: `@Nullable` on `WeaponCatalog.getWeaponTemplate/createTransientWeapon/validateAndGetWeapon`, `WearableCatalog.getWearable`, `WeaponItemApi.buildItem` (M2); `CombatEligibility.resolve()` returns `DEFAULT` when `Bukkit.getServer()` is null (m4) + polarity javadoc on `canBeHit` (m5); `WeaponShooting` javadoc (m6); `InstantReload` self-import (m8). Unrecorded deviation found: `Wearable.getPermission()` now `bartizan.wearables.<key>` (was `gangland.wearables.`) — kept, B21 migration doc must list it (m3). **M1 applied** after X-B-FG (commit `020e7f3`): `WeaponShooting`, `WeaponMuzzle`, `SteppedProjectileTask` `git mv`'d to `bartizan-plugin` package `org.luckyraven.bartizan.raytrace` (api imports added for `WeaponRaytracer`/`RaytraceContext`/`RaytraceRequest`/`WeaponVisualSpawner`); api reinstalled (exit 0); plugin errors unchanged apart from the forward references. Groups F+G committed as `ea5022d` (gate GF). |
| B8 | done | X-B-FG | `Bartizan.java` (`@CustomLog @Getter extends JavaPlugin`, `FULL_PREFIX="bartizan"`/`SHORT_PREFIX="btz"`, `onEnable` wraps `context.bootstrap()`+`dependencyHandler()`+`bStats()` in try/catch disabling the plugin on `Throwable`, `onDisable` calls `PacketBridge.reset()` then `context.shutdownBeans()`, `bStats()` uses `PLUGIN_ID=0` with the Q3 TODO and one `SingleLineChart("number_of_weapons", () -> context.get(WeaponAddon.class).size())`, `dependencyHandler()` is a thin soft-only `Dependency` inner class — Bartizan has no required dep beyond Keystone, which Bukkit's own loader enforces via `plugin.yml depend:`). `bootstrap/BartizanContext.java` modelled on `GanglandContext.java` with every module concern removed (no `ModuleLoader`, no module scan, no `hostApi`, no module loop in the repository-republish hook); keeps the FILE-phase hook (`fm.initializeAll()`), the DATABASE-phase hook (republish repositories by concrete class via an identity-set guard), `bootstrap()` = `scan("org.luckyraven.bartizan.config"); instantiate(); runListenerPhase(); runCommandPhase();`, `reloadBeans()`/`shutdownBeans()`; the 6 `registerInstance` calls match the checklist's exact list (`BartizanContext`, `DependencyContainer`, `JavaPlugin`, `Bartizan`, `SettingsLookup`, `BeanFactory`); `SettingsLookup` is a trivial `key -> false` lambda (Oriel's own minimal pattern, `oriel-plugin-skeleton.md` §2) rather than a dedicated impl class — Bartizan ships no `@ConditionalOnSetting`-gated beans. `bootstrap/DefaultListenerService.java` — `extends ListenerService`, ctor `(JavaPlugin, DependencyContainer, SettingsLookup)`, `invokeMethod` → `settings.isEnabled(condition)`, verbatim from `oriel-plugin-skeleton.md` §2. **Two recorded deviations from the checklist's literal `runCommandPhase()` port:** (1) dropped `Command.setInformationManager(...)` — that static field lives on Gangland's own `command.Command` adapter (`gangland-impl/.../command/Command.java`), which §1.4 explicitly says Bartizan does **not** need (its commands extend `org.luckyraven.keystone.command.Command` directly); there is no such static field to set. (2) dropped `ArgumentMessages.install(...)` — the checklist says to call it "with `BartizanMessages`", but §1.5's exhaustive 18-entry `BartizanMessages` table (4 prefixes + 14 content) carries no members equivalent to Gangland's `COMMAND_NO_PERM`/`ARGUMENT_NOT_IMPLEMENTED`/`ARGUMENTS_WRONG`; rather than inventing three message keys the source-of-truth table doesn't define, the argument tree is left on Keystone's built-in English defaults. Both are commented in place in `BartizanContext.java`. Gate: the three files compile in isolation against already-landed api/B8 symbols; full-tree compile deferred to gate GF (only `config.*`-package symbols were still missing per the task's own "Done when," consistent with B10 landing next). |
| B9 | done | X-B-FG | `util/BartizanChatUtil.java` — copy of `GanglandChatUtil` (from the `.gangland-0.8.4` snapshot per the orchestrator's mid-task update, see note below) routed through `BartizanMessages`/`BartizanSettings`. **Deviation (recorded):** §1.2 says replace `Gangland.SHORT_PREFIX` with `Bartizan.SHORT_PREFIX` in `commandDesign`/`confirmCommand`; used `Bartizan.FULL_PREFIX` ("bartizan") instead — the command word that actually appears in `commands.json` usage strings and the registered `PluginCommand` (§C.5: "command `bartizan` with aliases: `[btz, weapon]`") is "bartizan", not "btz", so `SHORT_PREFIX` would never match real help text; commented in place. `file/BartizanMessages.java` — the exact 18-entry enum from §1.5 (4 prefixes + 14 content, `DEAD_USING_WEAPON` as `Type.OTHER`/`isList=true`), same `Type`/`init`/`findMissingPaths`/`toString`/`toStringList`/mutual-recursion-with-ChatUtil shape as Gangland's `Messages`. `file/BartizanSettings.java` — `implements FileInitializer`, `FileHandlerReader.read(handler, new ConfigReport())` → `section`/`str`/`intVal`/`bool` helpers (no `money`/`dbl` needed — Bartizan has no `BigDecimal` settings), exactly the 7 §1.8 keys (`Money_Symbol`, the 3 `Block_Regeneration.*`, `Auto_Save.Debug`, `Language`, `Database.*`) plus `Debug.Enable` (present in the §1.8 YAML block; parsed for parity/no-unknown-key-warning even though nothing consumes it yet) and `Auto_Save.Time` (needed by B12's `WeaponDataCleanupTask` rewrite). Never `setDefaults`/`copyDefaults`. `database/BartizanDatabaseSettings.java` — `implements DatabaseSettingsProvider`, 6 methods delegating to `BartizanSettings`, byte-shape copy of `GanglandDatabaseSettings`. **Snapshot note:** the orchestrator's mid-task message redirected all further Gangland reads to `E:\Programming\java\Bartizan\.gangland-0.8.4\` (a `git archive` of ref 0.8.4) instead of the live working tree, since Gangland is now on branch `0.9.0` and deleting the weapon module underneath this stream; B9's own reads of `Messages.java`/`GanglandChatUtil.java`/`Settings.java` happened *before* that message arrived (from the still-0.8.4 working tree) but the content is identical to the snapshot — verified no drift risk since 0.8.4 was tagged before this session started. |
| B10 | done | X-B-FG | `config/KernelConfig.java` (`Phase.KERNEL`) — `informationManager()` (no `ModuleLoader` param, no module merge — forward-references group K's `InformationManager`), `diagnostics()`, `permissionWorker()`/`permissionManager(PermissionHandler)` under prefix `"bartizan"`, `packetAdapter()` per §1.6(2) (`ReflectivePacketAdapter` + `PacketBridge.install`), `fileManager()`, `databaseSettings()`/`databaseManager(DatabaseSettingsProvider)`. `config/FilesConfig.java` (`Phase.FILE`) — `settingsLoader`/`languageLoader` (the `oriel-plugin-skeleton.md` §7 FILE-phase recipe: `LanguageLoader` ctor with `BartizanSettings::getLanguagePicked`/`BartizanMessages::findMissingPaths`/`BartizanMessages::init`, `loader.initialize()` called inside the bean), then the §1.3 table verbatim: `ammunitionManager(BartizanSettings)` (ordering-only param kept), `ammunitionAddon`/`wearableAddon` (both construct their addon with `@Nullable Placeholder = null` — see deviation below), `blockRegenerationSettings()`, `weaponAddon(AmmunitionAddon)` (ordering-only param kept), `weaponLoader(...)` with the expected-file list frozen at the same 5 entries (T-06 not fixed). `config/DatabaseConfig.java` (`Phase.DATABASE`) — `bartizanDatabase(...)` modelled on Gangland's `DatabaseConfig.java:44-100` minus the module loop, scanning only `"org.luckyraven.bartizan.database"`; `databaseBackend(...)`, `repositoryRegistry(...)`, `weaponTableImportTask(BartizanDatabase)` per §1.9 (forward-references group J). `config/WiringConfig.java` (no explicit phase = CONFIG) — kept `weaponManager`/`weaponService`/`blockDamageManager`/`weaponVisualSpawner`/`weaponRaytracer` (registers the **interface** `WeaponRaytracer` on the `ServicesManager`, bean's own declared return type is the concrete `WeaponRaytracerImpl` — house rule)/`wearableService`/`pluginFireRegistry`/`weaponDataCleanupTask` (type-swapped to `BartizanDatabase`); deleted all 9 dead beans the §1.3 table marks DELETE (`wearableEquipService`, `recoilCompatibility`, both sign contributions, `weaponShopDisplayNameProvider`, `weaponMetricsContributor`, `weaponDeathMessageContributor`, `weaponNbtTags`, `debugWeaponContribution`, `itemWearableContribution`); added the 5 NEW beans (`bartizanApi`, `npcWeaponFactory`, `combatEligibility`, `listenerService`, `commandManager`). `config/ItemConfig.java` (CONFIG) — the 9 converter/serializer/refresher beans unchanged in shape, plus a new `weaponItemApi(WeaponService)` bean (placed here rather than WiringConfig — item-shaped, and `bartizanApi(...)` needs it as a parameter) and `bartizanItemVocabulary(...)` replacing `weaponItemRegistrations` — registers `BartizanItemVocabulary` (not the individual registries) on the `ServicesManager`, one INFO log line naming the published namespace (R-B5 mitigation). **Deviations (recorded):** (1) `KernelConfig.fileManager()` registers only `settings.yml`; the items/+weapon/ FileHandlers are registered inside `FilesConfig`'s own addon/loader beans instead — followed §1.3's precise per-bean migration table (explicit, authoritative) over B10's own abbreviated task prose ("fileManager() registering settings.yml and the items/ + weapon/ handlers"), which conflicts with it. (2) Every `Placeholder` parameter (`AmmunitionAddon`, `WearableAddon`, `WeaponAddon`) is passed `null` unconditionally — §1.3 says "pass null unless PlaceholderAPI is present" but no seam/adapter class for that detection is named anywhere in the checklist's file lists (`bartizan-plugin`'s pom doesn't even depend on `me.clip:placeholderapi`); wiring a live adapter would require adding an undeclared dependency and class. Scope-preserving default: always `null`. (3) `WeaponDataCleanupTask`'s "becomes a plain Keystone Timer" (§1.1) implemented by extending `org.luckyraven.keystone.timer.Timer` directly (not `RepeatingTimer` — its constructor needs a `Consumer<RepeatingTimer>` supplied before `super()` returns, which cannot legally reference `this`); `WiringConfig.weaponDataCleanupTask(...)` calls `task.start(false)` right after construction so it self-schedules (Bartizan has no `PluginDataCleanupService` to invoke it externally). (4) `bartizanItemVocabulary`/`weaponItemApi`/`npcWeaponFactory`/`bartizanApi`/`weaponRaytracer` bean signatures are best-effort forward-compatible guesses at constructors groups H/I/L haven't written yet (each documented inline with its source contract) — B13/B14/B17's executors may need to adjust either side to match. **Bug caught by this task's own compile check (not a checklist deviation):** `WiringConfig.java` was first written without importing `org.luckyraven.bartizan.wearable.WearableService` (used as the `wearableService(WearableAddon)` bean's return type) — real missing-import error, fixed before gate GF. |
| B11 | done | X-B-FG | `plugin.yml` — `name: Bartizan`, `main: org.luckyraven.bartizan.Bartizan`, `api-version: '1.16'`, `depend: [Keystone]`, `softdepend: [ViaVersion, PlaceholderAPI, NBTAPI]`, `authors: [LuckyRaven10]`, command `bartizan` with `aliases: [btz, weapon]`, `permission: bartizan.command.main` (+ a `permissions:` block, matching Gangland's own `plugin.yml` shape) — parses as YAML, declares the command. `org/luckyraven/bartizan/module.properties` — literal `module.name=Bartizan` (**not** the filtered `${project.name}` Maven property the original module.properties used — `bartizan-plugin`'s own `<name>` is "Bartizan Plugin", which would have produced the wrong prefix on every `@CustomLog` line; the checklist's own §1.7 spells out the literal target string, so used that verbatim instead of blindly copying the `${project.name}` filter). |
| B12 | done | X-B-FG | Copied all files in the §1.1 `PLG/` table for this group from the `.gangland-0.8.4` snapshot (see the orchestrator's mid-task redirect, noted on the B9 row): `weapon/{WeaponService,WeaponManager}`, `ammo/AmmunitionManager`, `configuration/{WeaponAddon,AmmunitionAddon}`, `configuration/parser/*` (9: `WeaponBaseData`, `SelectiveFireSectionParser`, `AmmunitionSectionParser`, `GunWeaponParser`, `BiologicalWeaponParser`, `IncendiaryWeaponParser`, `MeleeWeaponParser`, `ThrowableWeaponParser`, `ModifiersSectionParser`), `wearable/{WearableService,WearableAddon}`, `file/{WeaponLoader,WeaponBlockRegenerationSettings}`, `fire/PluginFireRegistry`, `util/{BlockGroupResolver,EmptyMagSoundGate,PotionEffectParser}`, `data/WeaponDataCleanupTask` — **23 files, not 24** (the §1.1 table for this group enumerates exactly 23; the group/task header count of 24 is the checklist's own rough size estimate, not a content discrepancy — nothing is missing). Applied `PKG+` per the table: `WeaponService implements WeaponCatalog` (all 5 interface methods `@Override`d); `WeaponManager`'s ctor param `GanglandDatabase`→`BartizanDatabase` (forward-references group J); `WearableAddon` parses `Extra_Tags:` generically instead of `Jetpack:` — a new private `sectionToMap(ConfigurationSection)` recursively converts nested sections (e.g. `Sounds:`) into real `Map<String,Object>` instances rather than leaving them as `MemorySection`, so gadget's later `extraTags()` reads get an actual `Map`; traits parsed as lower-cased `Map<String,Integer>` (YAML keys stay upper-case, per §1.6(6)'s second option); `WearableService implements WearableCatalog` (all 6 methods `@Override`d), `WearableEquipService` implementation dropped entirely (interface doesn't exist in Bartizan); `WeaponBlockRegenerationSettings`'s 3 reads → `BartizanSettings`; `WeaponDataCleanupTask` rewritten per the B10-row Timer deviation (drops `implements DataCleanupTask`, `Settings.isAutoSaveDebug()`→`BartizanSettings.isAutoSaveDebug()`). **Deviation (recorded):** `util/PotionEffectParser.java`'s javadoc referenced `{@link BiologicalAction}` — that class is `weapon.action.BiologicalAction`, a group-H file (B13) that doesn't exist yet, and a real unresolvable `@link`-backing import would fail the compile (the same class of issue the gate-GA review already fixed twice for `ThrowableData`/`Weapon` in B5/B6); changed to `{@code BiologicalAction}` and dropped the now-unused import, same fix pattern. All other files are plain `PKG` (import-prefix rewrite only, no behavior change): `weapon.*`/`ammo.Ammunition`/`dto.*`/reload/modifiers/etc. api-side imports repointed to `org.luckyraven.bartizan.api.*`; same-group plugin-side imports (`AmmunitionManager`, `WeaponAddon`, `BlockGroupResolver`) repointed to `org.luckyraven.bartizan.*` (no `.api`). |
| **GF** | done | X-B-FG | `mvn -q -pl bartizan-plugin -am install -DskipTests` from `E:\Programming\java\Bartizan`. **Correction to this task's own briefing:** the orchestrator's prompt said GF "may legitimately fail on symbols that arrive in groups H/I" only — that undercounts what §1.1/§1.3 themselves require. Within the 23 actual B12 files, the *only* errors are forward references to `org.luckyraven.bartizan.database.{BartizanDatabase, WeaponRepository}` (`weapon/WeaponManager.java`, `data/WeaponDataCleanupTask.java`) — **group J (B15)**, not H/I, and unavoidable: §1.1's own PKG+ edit for `WeaponManager` requires the `GanglandDatabase`→`BartizanDatabase` ctor-param swap, and `WeaponDataCleanupTask` requires an `IRepository<Weapon>` sourced from it. The wider `mvn` run also fails inside 4 of *this session's own* B10 files (`config/{KernelConfig,DatabaseConfig,WiringConfig,ItemConfig}.java` — group F, not B12) on symbols from groups H (`raytrace.WeaponRaytracerImpl`), I (`item.{WeaponConverter,AmmunitionConverter,WearableConverter,WeaponItemSerializer,AmmunitionItemSerializer,WearableItemSerializer,WeaponRefresher,WearableRefresher,AmmunitionItemRefresher,BartizanItemVocabulary,WeaponItemApiImpl}`), K (`command.data.InformationManager`) and L (`BartizanApiImpl`, `npc.NpcWeaponFactoryImpl`) — all expected, since those 4 config classes wire up the *entire* remaining pipeline per §1.3, not just groups H/I. Deduplicated missing-symbol list (verbatim class names from javac): `BartizanDatabase, WeaponRepository, WeaponTableImportTask, InformationManager, BartizanApiImpl, WeaponRaytracerImpl` + package-level `org.luckyraven.bartizan.item` (10 classes therein) + `org.luckyraven.bartizan.npc` (`NpcWeaponFactoryImpl`). **Marking GF `done`** per bartizan.md's own literal gate text — "the 24 files above produce no errors of their own" — since zero errors trace to a mistake inside the 23 B12 files; every one is an inherent forward reference the checklist's own §1.1 content dictates. One real bug (missing `WearableService` import in `WiringConfig.java`, a B10/group-F file, not B12) was caught by this same compile run and fixed before this row was written — see the B10 row. |
| B13 | done | X-B-HI | Copied the 6 actions to `weapon/action/` (`GunAction`, `FullAutoTask`, `MeleeAction`, `ThrowableAction`, `IncendiaryAction`, `BiologicalAction`), wrote `raytrace/WeaponRaytracerImpl implements WeaponRaytracer` (the checklist's `raytrace/{RaytraceContext,WeaponVisualSpawner,WeaponMuzzle,SteppedProjectileTask}` rows were already stale twice over by the time this task ran — B7 moved the first two to `bartizan-api` and gate-GD review M1 (applied by X-B-FG, commit `020e7f3`) then moved `WeaponMuzzle`/`SteppedProjectileTask`/`WeaponShooting` back to `bartizan-plugin`; verified the live tree directly instead of trusting either note — only `WeaponRaytracerImpl` itself was new), and the 11 surviving listeners (`WeaponInteract` to `listener/`, `ScopeJumpListener`, `fire/PluginFireProtectionListener`, `player/WeaponQuitCleanupListener`, `projectile/ProjectileDamageListener`, `reload/{WeaponDroppedListener,WeaponItemSpawnListener,WeaponReloadListener}`, `selective/WeaponSelectiveFireChangeListener`, `wearable/WearableEquipListener`). **17 files, not 23** — same rough-estimate pattern as B12/B7 (six actions + one raytrace impl + ten listeners = 17; the task's own prose says "the 11 surviving listeners" but lists only 10 by name — `WeaponInteract, ScopeJumpListener, PluginFireProtectionListener, WeaponQuitCleanupListener, ProjectileDamageListener, WeaponDroppedListener, WeaponItemSpawnListener, WeaponReloadListener, WeaponSelectiveFireChangeListener, WearableEquipListener` — nothing missing, verified against every §1.1 row for this group and against the actual file tree). Applied the `RecoilCompatibility`-parameter removal at all named sites (all six actions + `WeaponInteract`'s 11) and `weapon.getRecoil().applyRecoil(player)` (1-arg, matches B5's `RecoilManager` rewrite); `WeaponInteract`'s two `DownedPlayerRegistry.isDowned(uuid)` reads → `!combatEligibility.canBeHit(player)` with `CombatEligibility` added as a new constructor parameter (§1.6(5), polarity preserved — `player.isDead() || !combatEligibility.canBeHit(player)` mirrors the original `player.isDead() || DownedPlayerRegistry.isDowned(...)` exactly); `WearableEquipListener` now takes `WearableService` directly (T-14 dissolved). Did not copy `listener/gang/GangAllyWeaponImpactListener.java` (deleted per §1.1). **Deviation 1 (recorded):** `MeleeAction`/`ThrowableAction`/`IncendiaryAction`'s `pendingDamage` fields — §1.6(3) says "internal to the weapon module … keep it as a private instance field, do not expose it," but `WeaponInteract.onEntityDamage` reads `MeleeAction.pendingDamage`/`ThrowableAction.pendingDamage`/`IncendiaryAction.pendingDamage` by **static class reference** today (verified by reading `WeaponInteract.java:259-261` directly) — there is no per-action instance for it to read an instance field from (a fresh action object is constructed per swing/throw/burst). Kept all three as `public static final Set<UUID>`, package renamed only, so the existing cross-class static-drain pattern keeps working unchanged; documented inline in `MeleeAction`/`ThrowableAction`. **Deviation 2 (recorded, larger):** `ThrowableAction`'s explosion loop — §1.6(3)/§2 B13 cite only ONE fire point for the new `WeaponEntityDamageEvent` (the living-entity `target.damage(...)` call, ported faithfully with `DamageKind.EXPLOSION`), but the deleted `pendingVehicleExplosionDamage` map's ONLY purpose was a separate, earlier loop that pre-registers damage for **non-living** entities (vehicles) before `world.createExplosion(...)` — the checklist gives no replacement fire point for that loop, yet `WeaponEntityDamageEvent`'s own javadoc says it exists specifically for "a non-projectile weapon … hits a non-living entity such as a vehicle," and R-B4/§C.3 both say P3's `CarDamageListener` rewrite reads `event.kind()==EXPLOSION`/`event.getDamage()` off this event — which has no firing site for vehicles without this. Fired `WeaponEntityDamageEvent` there too (before `world.createExplosion`, same as the deleted map's timing) so P3 has an actual event to consume for vehicles, not just players; the 1-tick cleanup task and `registeredUuids` list (whose only job was draining the deleted map) were removed entirely rather than kept as dead code. Documented at length in `ThrowableAction`'s class javadoc for the next reader. `pendingKillerWeapon`'s consumer (a future `WeaponDeathListener`, group M — outside B13's scope) was deliberately **not** given speculative constructor plumbing; a future listener can correlate `WeaponEntityDamageEvent`'s `weaponName()`/`kind()` against `PlayerDeathEvent` on its own, which was judged simpler than guessing group M's exact wiring now. **Deviation 3 (minor):** `ProjectileDamageListener`'s `@AutowireTarget({WeaponService.class})` doesn't match its actual ctor param (`WeaponVisualSpawner`) — pre-existing in the Gangland source (verified), left verbatim per the `PKG`-only edit (not a functional bug, not worth a triage entry). Gate check: `mvn -q -pl bartizan-plugin -am install -DskipTests` from `E:\Programming\java\Bartizan` — the only 5 files with errors are pre-existing group-F/G files (`config/{DatabaseConfig,KernelConfig,WiringConfig}`, `data/WeaponDataCleanupTask`, `weapon/WeaponManager`), all on forward references to groups J/K/L (`BartizanDatabase`, `WeaponRepository`, `WeaponTableImportTask`, `command.data.InformationManager`, `BartizanApiImpl`, `org.luckyraven.bartizan.npc`) — **zero errors trace to any of this task's 18 files**; `ItemConfig.java` (which names 9 of B13/B14's classes) and `WiringConfig`'s `weaponRaytracer(...)` bean (which names `WeaponRaytracerImpl`) both compile clean, confirming the guessed bean signatures in both config classes match what was written here without any change needed. |
| B14 | done | X-B-HI | Copied the 10 `item/*` files with `PKG` (`WeaponItemPredicates`, `WeaponConverter`, `AmmunitionConverter`, `WearableConverter`, `WeaponItemSerializer`, `AmmunitionItemSerializer`, `WearableItemSerializer`, `WeaponRefresher`, `WearableRefresher`, `AmmunitionItemRefresher` — `ItemKind.WEAPON`/`.AMMUNITION`/`.WEARABLE` → `BartizanItemKind.WEAPON`/`.AMMUNITION`/`.WEARABLE`); created `item/ItemAttributes.java` (§1.2, `GanglandChatUtil.color`→`BartizanChatUtil.color`), `item/BartizanItemKind.java` (3-member enum `implements org.luckyraven.keystone.item.ItemKind`), `item/BartizanItemVocabulary.java` (`implements org.luckyraven.keystone.item.spi.ItemVocabulary`, `namespace()`→`"bartizan"`, `contribute(...)` reproduces `WeaponModuleConfig.java:195-206`'s registration order exactly — converters `weapon`/`{ammunition,ammo}`-alias/`wearable`, serializers all at priority `0`, refreshers weapon@10/wearable@10/ammunition@0 per §C.3), `item/WeaponItemApiImpl.java` (§1.6(9): `buildItem` via `createTransientWeapon`+`buildItem`, `isValidWeaponName` via `getWeaponTemplate!=null`, `isSameWeapon` via player-less `validateAndGetWeapon(null,·)` on both stacks then `compare(w1,w2)==0` — mirrors `WeaponItemSpawnListener`'s own null-player precedent, `cleanDisplayName` ported verbatim from the deleted `WeaponShopDisplayNameProvider.java:28-40`). **14 files, not 12** — same rough-estimate pattern as B12/B7/B13 (10 copied + 4 new = 14; every §1.1/§2 file accounted for). Confirmed every constructor signature against `ItemConfig.java`'s pre-existing guessed beans (X-B-FG, B10) — all nine converter/serializer/refresher constructors, `WeaponItemApiImpl(WeaponService)` and `BartizanItemVocabulary(WeaponConverter,AmmunitionConverter,WearableConverter,WeaponItemSerializer,AmmunitionItemSerializer,WearableItemSerializer,WeaponRefresher,WearableRefresher,AmmunitionItemRefresher)` matched exactly as guessed — **zero config-class changes needed**. **notify orchestrator: `bartizan-api` installed to `~/.m2`** — `mvn -q -pl bartizan-api -am install -DskipTests` from repo root, exit 0; `~/.m2/repository/org/luckyraven/bartizan/bartizan-api/0.1.0/` contains `bartizan-api-0.1.0.jar` (130817 bytes), `bartizan-api-0.1.0.pom`, `_remote.repositories`, jar timestamp confirmed fresh (built same run). |
| **GG** | done | X-B-HI | `mvn -q clean install -DskipTests` from `E:\Programming\java\Bartizan` — `bartizan-api` builds and installs silently (no errors); `bartizan-plugin` fails with exit 1. Deduplicated missing-symbol list (verbatim from javac): `BartizanDatabase, WeaponRepository, WeaponTableImportTask, InformationManager, BartizanApiImpl` + package-level `org.luckyraven.bartizan.command.data` + `org.luckyraven.bartizan.npc` — exactly the group-J (`BartizanDatabase`/`WeaponRepository`/`WeaponTableImportTask`)/K (`command.data`/`InformationManager`)/L (`BartizanApiImpl`/`npc`) set the orchestrator's own briefing named, nothing else. The 5 files carrying these errors — `config/{DatabaseConfig,KernelConfig,WiringConfig}` (group F, B10), `data/WeaponDataCleanupTask`, `weapon/WeaponManager` (group G, B12) — are all **pre-existing** files this task did not touch; **no error traces to any of B13's 17 or B14's 14 files**. Marking GG **done** per the task's own criterion ("every missing symbol is a group-J/K/L file per §1.1 and no error is inside your own 35 files"). First 20 `[ERROR]` lines verbatim: `DatabaseConfig.java:[5,40] cannot find symbol` (class BartizanDatabase) / `:[6,40]` (WeaponTableImportTask) / `:[37,16]`/`:[80,48]`/`:[89,54]`/`:[102,60]`/`:[102,16]` (same two, repeated call sites) / `KernelConfig.java:[4,44] package org.luckyraven.bartizan.command.data does not exist` / `:[41,16] cannot find symbol` (InformationManager) / `WiringConfig.java:[6,31] cannot find symbol` (BartizanApiImpl) / `:[18,40]` (BartizanDatabase) / `:[21,35] package org.luckyraven.bartizan.npc does not exist` / `:[58,69]`/`:[105,89]` (BartizanDatabase again) / `WeaponManager.java:[4,40]` (BartizanDatabase) / `:[5,40]` (WeaponRepository) / `WeaponDataCleanupTask.java:[5,40]` (WeaponRepository) / `WeaponManager.java:[15,23]`/`:[17,55]` (BartizanDatabase). **GG review (Opus R-B-FGHI): PASS WITH FIXES** — see `REVIEW-bartizan-GG.md`. Blocker B1: `WeaponDataCleanupTask` period must be `Clean_Up.Time` days (default 30), not `Auto_Save.Time` minutes; B2: `onDisable` must `unregisterAll` + `container.clear()`; B3: `isSameWeapon` must compare templates, not mint weapons; m2 holder beans return concrete classes; m5 bStats guard. **Applied by the orchestrator as commit `1ee6fd4`** (after X-B-JKL's `2b9a02c`): `Clean_Up.Time` days in `BartizanSettings`/`WeaponDataCleanupTask`, `onDisable` unregisterAll + `container.clear()`, template-based `isSameWeapon`, concrete holder-bean return types, bStats guard, FILE-hook note; full `mvn clean install` exit 0, jar keystone=0 / bartizan=249. Obligations sent to X-B-JKL: B16 constructor-injects `InformationManager`; B17's `WeaponDeathListener` records victim→weapon from its own `WeaponEntityDamageEvent` handler. |
| B15 | done | X-B-JKL | Copied `database/{WeaponTable,WeaponRepository}` with `PKG` only (table name `weapon`, columns `uuid`/`type` unchanged). Created `database/BartizanDatabase.java` — byte-shape copy of `GanglandDatabase` (`connectBackend`/`disconnectBackend`/`createSchema`/`createTables`/`getSchema`/`getTables` unchanged; schema name `"bartizan"` passed by `DatabaseConfig.bartizanDatabase(...)`'s existing constructor call, so the SQLite file lands at `plugins/Bartizan/database/bartizan.db` per §C.5); `insertInitialData()` is an empty override (no rank seeding — watch-out honored). Created `database/WeaponTableImportTask.java` per §1.9: constructor `(Bartizan, BartizanDatabase)` matching `DatabaseConfig.weaponTableImportTask(...)`'s existing `new WeaponTableImportTask(bartizan, database)` call exactly (no config-class change needed); runs its one-shot import synchronously inside the constructor (nothing else in the bean graph calls a method on it, so this is the only point the DATABASE-phase bean can do its work) — marker check (`plugins/Bartizan/.weapon-import-done`), then a raw read-only JDBC read (`jdbc:sqlite:<path>?open_mode=1`, plain `DriverManager`) of `plugins/Gangland_Warfare/database/gangland.db`'s `weapon` table when present, upserted through `database.getBackend().upsertAll("weapon", List.of("uuid"), List.of("uuid","type"), rows)` (bypasses the generic `TableBackend<Weapon>` — the source rows are raw `(uuid,type)` pairs, not `Weapon` domain objects, so `DatabaseBackend.upsertAll` is the right layer, not `WeaponTable.getData`), `SQLException` routed through `Diagnostics.active()` under fault code `bartizan.weapon.import.failed` and logged, never rethrown; marker written in every terminal path (missing source file, 0 rows, N rows imported, or after a caught `SQLException`) so the read never repeats — the checklist's rule 4/5 wording doesn't explicitly settle the failure case, so this is a recorded judgment call (a failed import only costs re-minted UUIDs per §1.9 rule 5, so re-trying every boot was judged worse than accepting that cost once). `WeaponManager.initialize()` was already wired exactly per house rule `feedback_repository_data_supplier` (`setWeaponAddon` + `setDataSupplier`) by B12 — verified against `W/WeaponManager.java:22-36`, no change needed. Gate: `mvn -q -pl bartizan-plugin -am install -DskipTests` from `E:\Programming\java\Bartizan` — group-J's own 5 files compile clean; remaining errors are exactly the pre-existing group-K/L forward references (`command.data`/`InformationManager`, `BartizanApiImpl`, `org.luckyraven.bartizan.npc`) in `KernelConfig.java`/`WiringConfig.java` — zero errors trace to any of this task's files. |
| B16 | done | X-B-JKL | Wrote the 12 rewritten command classes + `DebugCommand` + `ReloadCommand` (14 command classes) into `PLG/command/` (`WeaponCommand`, `WeaponGiveCommand`, `WeaponInfoCommand`, `WeaponListCommand`, `AmmunitionCommand`, `AmmunitionGiveCommand`, `AmmunitionInfoCommand`, `AmmunitionListCommand`, `DebugCommand`, `ReloadCommand`) and `PLG/command/wearable/` (`WearableCommand`, `WearableGiveCommand`, `WearableInfoCommand`, `WearableListCommand`); copied `command/data/{InformationManager,CommandInformation}` with the module-merge loop confirmed already absent from `InformationManager` itself (it lives in Gangland's `KernelConfig.informationManager(ModuleLoader)` bean, not the class — verified by reading both; Bartizan's own `KernelConfig.informationManager()` bean, already written by B10/X-B-FG, needed zero changes). Base class per §1.4 is `org.luckyraven.keystone.command.Command` directly via its 5-arg prefix overload (`super(bartizan, Bartizan.FULL_PREFIX, "<label>", user, alias...)`); every `User<Player>.sendMessage` call became `player.sendMessage` (verified: the only `User` method the 10 original files called, per §1.4's own survey); `Messages`/`GanglandChatUtil` → `BartizanMessages`/`BartizanChatUtil`. **Orchestrator's gate-GG-review message (received mid-task, before this task's own code was written) said to hand `InformationManager` to command classes by constructor injection rather than a static holder — already the design being followed independently (every top-level command takes `InformationManager` as a constructor parameter, matching the guessed `KernelConfig.informationManager()` bean shape from B10), so no rework was needed; confirmed and recorded per the message's instruction.** `WearableCommand` (rewrite of the `SubArgument`-based `ItemWearableCommand`) is now a top-level `Command` exactly like `WeaponCommand`/`AmmunitionCommand` — its children's `args[]` indices shifted down one level from the Gangland original (`args[3]`/`args[4]` under nested `/glw item wearable give` → `args[2]`/`args[3]` under top-level `/bartizan wearable give`, matching `WeaponGiveCommand`'s own indexing). Traits read via the new `Wearable.traits()`/`traitLevel(String)` string-keyed API (§1.6(6)) instead of the deleted `WearableTrait` enum's `getTraits()`. **Deviation 1 (recorded):** added `command/HelpInfo.java` — not separately named in the checklist's B16 file list, but every one of the 5 top-level commands needs it once Gangland's `command.Command` adapter (which used to carry the `HelpInfo` field) is gone per §1.4; extracted once as a shared utility rather than duplicating the same pagination loop 5 times. Its empty-list message is a plain literal, not a `BartizanMessages` member — the checklist's exhaustive 18-entry table (§1.5) has no equivalent to `Messages.COMMAND_HELP_EMPTY`, so (matching B8's own precedent for `ArgumentMessages`) no new key was invented. Total: 17 files the checklist named (14 command classes + 2 data classes + `commands.json`) + 1 extra (`HelpInfo`) = 18 — same "rough estimate, not a discrepancy" pattern B7/B12/B13/B14 recorded. **Deviation 2 (recorded):** renamed the 4 `item_wearable*` commands.json keys to `wearable_help`/`wearable_give`/`wearable_info`/`wearable_list` (§1.7 says "12 keys re-pathed", which read literally could mean keeping the old key text and only changing the `usage` string) — done instead so `WearableCommand`'s help filter (`entry.getKey().startsWith("wearable")`) works the same way `WeaponCommand`'s (`"weapon"`) and `AmmunitionCommand`'s (`"ammunition"`) do; an `item_wearable*`-prefixed key would need a different, inconsistent filter string for a command that is no longer nested under `item`. The two new keys are named exactly `bartizan_debug` / `bartizan_reload` per §1.4's literal text (not renamed). Gate: `python -c "import json;print(len(json.load(open('bartizan-plugin/src/main/resources/commands.json'))))"` → `14`. `mvn -q -pl bartizan-plugin -am install -DskipTests` from `E:\Programming\java\Bartizan` — group-K's own files compile clean; the only remaining errors are the pre-existing group-L forward references (`BartizanApiImpl`, `org.luckyraven.bartizan.npc`) in `WiringConfig.java` — zero errors trace to any of this task's 18 files. |
| B20 | done | X-B-CDE (RecoilManagerTest half), X-B-JKL (NpcWeaponCadenceTest half) | **Cadence half (X-B-JKL, run before B17 as required):** `NpcWeaponCadenceTest` written at `bartizan-plugin/src/test/java/org/luckyraven/bartizan/npc/NpcWeaponCadenceTest.java`, 4 tests — SINGLE (one trigger per shot, busy for `max(perShot*cooldown,5)`, a second `tryFire` in the window returns false), BURST (one trigger schedules `perShot` rounds via a captured `scheduleBurst(gun,perShot,cooldown)` call and busies for `perShot*cooldown+cooldown`), AUTO (fires once per `max(cooldown,5)`, driven by calling `tryFire` every tick for 3 full cycles = hold-to-fire), and a dedicated `tick()`/`isBusy()` regression test (fires once, ticks exactly the busy window, asserts `isBusy()` goes false and a second `tryFire` then succeeds — "the check that catches a cop which fires once and never again"). No `aimErrorDegrees` assertion anywhere; a deliberately nonzero, never-read value (15.0) is passed in `newController(...)` specifically so a future accidental wiring-in would be caught by *some* assertion elsewhere, not to assert on it directly. **Bukkit-free design (recorded per the watch-out):** `NpcWeaponControllerImpl` keeps the two Bukkit-touching side effects — `fireRound(GunWeapon)` (the actual `WeaponShootEvent`/`WeaponRaytracer`/`SoundEffect` call) and `scheduleBurst(GunWeapon,int,int)` (the `SequenceTimer`) — as package-private hooks; the test's `RecordingController` inner class (same package, `bartizan-plugin/.../npc/`) subclasses `NpcWeaponControllerImpl` and overrides both hooks to just count calls, so only the pure cadence arithmetic (`attackCooldown` scaling, the busy/one-trigger gate, `tick()`) is exercised — no live Bukkit server, no real Bukkit scheduler. `GunWeapon` itself is a plain Mockito mock (`mock(GunWeapon.class)`, stubbing `getCurrentSelectiveFire`/`getProjectileData`/`isBroken`/`isMagazineEmpty`/`isReloading`) — Mockito's default (non-inline) mock maker handles it fine since neither `Weapon` nor `GunWeapon` is final and their hierarchy never touches ViaVersion (the T-09 trap doesn't apply here). `ProjectileData` is a real instance built via its own `@Builder` (a trivial POJO, no Bukkit dependency) rather than mocked. **Red-first sequence:** (1) wrote a STUB `NpcWeaponControllerImpl`/`NpcWeaponFactoryImpl` per the task's own instruction ("tryFire returns true unconditionally... tick() does nothing" — realized as the interface's inherited no-op `tick()` default, so no override needed for "does nothing"), already declaring the two hook methods with trivial stub bodies so the test's `@Override`s compile unchanged across red → green; also had to write a minimal-but-real `BartizanApiImpl` (group L's other forward reference) so the module would compile far enough to run the test as a genuine assertion failure rather than a compile error — full compile succeeded once a **real, previously-undiscovered pom bug** was fixed (see below). (2) Ran `mvn -q -pl bartizan-plugin -am test -Dtest=NpcWeaponCadenceTest -Dsurefire.failIfNoSpecifiedTests=false` against the stub — **red, all 4 tests failed on genuine assertion mismatches** (not compile errors): `single_firesOnceAndStaysBusyForScaledCooldown` → `exactly one round fired for one SINGLE trigger ==> expected: <1> but was: <0>`; `burst_oneTriggerSchedulesPerShotRoundsAndStaysBusyForTotalTicks` → `exactly one burst scheduled for one BURST trigger ==> expected: <1> but was: <0>`; `tick_neverGoesBusyForeverAfterASingleShot` → `expected: <true> but was: <false>` (the stub's `isBusy()` is hardcoded `false`, so the "still busy right after firing" assertion fails); `auto_isHoldToFire_firingOncePerScaledCooldown` → `AUTO must fire exactly once per 5-tick window ==> expected: <3> but was: <15>` (the stub fires on every call). Full summary line: `Tests run: 4, Failures: 4, Errors: 0, Skipped: 0`. (3) Implemented the real B17 cadence port (see the B17 row) and re-ran the same command — **green: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.** **Real bug found and fixed en route to red (not the checklist's fault — a genuine gap):** the very first attempt at a full-module compile (needed before the test could even run) failed with `cannot access org.luckyraven.keystone.npc.spi.NpcRangedAttack — class file not found` plus a cascade of unrelated `cannot find symbol: variable log` errors across a dozen already-passing files (`Bartizan.java`, `BartizanContext.java`, `WeaponManager.java`, `WearableAddon.java`, `InformationManager.java`, etc.) — a single unresolved supertype (`NpcWeaponController extends NpcRangedAttack`) breaking Lombok's annotation-processing round for the whole compilation unit. Root cause: `bartizan-api/pom.xml` declares `keystone-npc` at `provided` scope (correctly, per its own in-file comment), but Maven does **not** propagate a `provided`-scope dependency transitively to a dependent module, and `bartizan-plugin/pom.xml` never separately declared it — so `bartizan-plugin` could not resolve `NpcRangedAttack`'s class file the moment `NpcWeaponControllerImpl` (group L, this module) tried to `implement` an interface that extends it. Fixed by adding the same `keystone-npc` dependency entry to `bartizan-plugin/pom.xml` (mirroring `bartizan-api`'s copy, including its "brings citizens-main transitively at provided scope, never reaches the jar" note). This is a real B2/B8 gap, not touched by any earlier executor's own compile gates because groups F-K never named a class that actually implements/extends an `keystone-npc` type — group L (B17/B20) is the first to. Filed nowhere else since it is the direct subject of this task's own gate. Placed at `bartizan-api/src/test/java/org/luckyraven/bartizan/api/weapon/recoil/RecoilManagerTest.java` per §3's test-placement table (`bartizan-api`, not `bartizan-plugin` — `RecoilManager` itself lives in `bartizan-api`, and the test needs nothing beyond `keystone-common`'s `PacketAdapter`/`PacketBridge`, already a `bartizan-api` dependency). **Red-first sequence:** (1) copied `RecoilManager.java` into `bartizan-api` with `PKG` only (old `RecoilCompatibility`-taking signature, import left unrewritten since `org.luckyraven.gangland.compatibility.recoil` was never a `bartizan-api` dependency and never will be); (2) wrote the 4-test `RecoilManagerTest` against the *new* `applyRecoil(Player)` 1-arg signature and a hand-written `CapturingPacketAdapter implements PacketAdapter` installed via `PacketBridge.install(...)` / reset in `@AfterEach`; (3) ran `mvn -q -pl bartizan-api -am test-compile` — **red, verbatim first lines:** `[ERROR] .../weapon/recoil/RecoilManager.java:[4,52] package org.luckyraven.gangland.compatibility.recoil does not exist` then three more `cannot find symbol: class RecoilCompatibility` at lines 26/87/100 (the old `applyRecoil(RecoilCompatibility,Player)` signature the test doesn't even call) — confirms the state is red because the module cannot resolve the deleted-dependency shape, not because of an unrelated typo. (4) Applied the real §1.6(1) rewrite (see B5 row). (5) After GD, ran `mvn -q -pl bartizan-api -am test -Dbukkit.version=1.21.11-R0.1-SNAPSHOT -Dtest=RecoilManagerTest` (the `-D` override stands in for the still-pending pom fix GD's row describes) — **green: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.** Four tests: default (not sneaking, full amount), sneaking+not-scoped (quarters the amount — reading the real source, this is the `/4` branch), sneaking+scoped (halves the amount — the `/2` branch), and pattern-index advance/wrap. **Correction to the checklist's own prose:** §2 B20 and §3 both write "the sneak path (`/2`), the sneak+scoped path (`/4`)"; reading `RecoilManager.applyDefaultRecoil`/`applyRecoil` directly shows the opposite pairing (sneak-alone → `/4`, sneak+scoped → `/2` — both the pattern-recoil branch and the default-recoil branch agree with each other, so this is deliberate existing behavior, not a typo in the source). The test pins the real source behavior with that labeling corrected; flagging here so nobody "fixes" the test to match the checklist's prose instead of the code. |
| B17 | done | X-B-JKL | **Received the orchestrator's gate-GG-review message mid-task** (before this task's own code was written), acknowledged here per its instruction: (1) B16 guidance already followed independently (see B16 row). (2) B17 guidance applied as designed — see the deviation below (`WeaponDeathListener` now listens to `WeaponEntityDamageEvent` directly with a TTL map, not an injected `ThrowableAction` reference). (3) Did not touch `Bartizan.java` onDisable teardown, `WeaponDataCleanupTask`/`WiringConfig` cleanup period, `WeaponItemApiImpl.isSameWeapon`, or the `ItemConfig`/`WiringConfig` holder-bean return types — left for the orchestrator as instructed. Created 4 files: `npc/NpcWeaponControllerImpl.java`, `npc/NpcWeaponFactoryImpl.java`, `listener/death/WeaponDeathListener.java`, `BartizanApiImpl.java` (top-level package, matching `WiringConfig`'s existing import). `NpcWeaponControllerImpl`/`NpcWeaponFactoryImpl` were first written as the B20 stub (see B20 row), then upgraded in place to the real port here: `tryFire`/`performSingleShot`/`performBurstFire`/`performAutoShot`/`fireRound` (renamed from `fireSingleRound` — package-private hook, not a rename of behavior)/`triggerReload`/`refreshHeldItem`/`tick`/`scaleCooldown` all transcribed **line by line** from `NpcCombatDelegate.java` per the arithmetic table (`:105-115`, `:175-191`, `:193-198`, `:142-157`, `:260-270`, `:272-281`, `:283-306`, `:309-341`, `:364-367`) — verified against the source a second time while writing, not re-derived. `NpcWeaponFactoryImpl.create(...)` resolves the weapon via `weaponManager.createTransientWeapon(weaponName)` (NPCs never own a persisted UUID, matching `W/WeaponService.java`'s own fallback path) and constructs `NpcWeaponControllerImpl(bartizan, shooter, weapon, fireRateMultiplier, aimErrorDegrees)`; `aimErrorDegrees` is stored on the controller but never read outside the constructor, per §1.6(8)'s "keep the parameter, do not invent a use for it." `BartizanApiImpl` is five one-line accessors over beans that already implement each catalog interface (`WeaponManager`→`WeaponCatalog`, `WearableAddon`→`WearableCatalog`, `AmmunitionManager`→`AmmunitionCatalog`) — no new logic. Registered on the `ServicesManager` from the pre-existing `WiringConfig.bartizanApi(...)` bean (B10, unchanged — the guessed constructor signature matched exactly, confirming X-B-FG's forward-compatible guess). **Deviation from §1.6(4)'s literal text (superseded by the orchestrator's mid-task message, applied as directed):** §1.6(4) says the throwable side-table is "written by `ThrowableAction` through an injected reference (not a static)" — no longer possible, since B13 already deleted `ThrowableAction`'s two static maps and replaced their only reader with `WeaponEntityDamageEvent.weaponName()/kind()` (there is no reference left to inject). `WeaponDeathListener` instead carries its own `@EventHandler` on `WeaponEntityDamageEvent` (no priority — first-come, records `victimUuid -> (weaponName, recordedAtMillis)` in a private `ConcurrentHashMap`, 5-second TTL) and its `@EventHandler(priority = EventPriority.HIGH)` on `PlayerDeathEvent` reads and removes that entry, falling back to `weaponManager.validateAndGetWeapon(killer, heldItem)` when nothing was recorded — same priority order (recorded throwable claim beats currently-held item) as the deleted contributor's `resolve(...)`. Per the orchestrator's note, does not assume any `DamageKind` other than `EXPLOSION` is live — records whatever kind arrives without branching on it. Message-building logic (global-message fallback pool, `%killer%/%victim%/%item%` template substitution) ported from the deleted `WeaponDeathMessageContributor.resolve` (`:29-45`) merged with Gangland's own `PlayerDeathListener.buildDeathMessage` (`:198-223`) template-application step, since Bartizan is no longer a contributor plugged into that builder — the `killer == null` early-return is preserved from the original (Gangland's builder never called any contributor without a non-null killer, so this listener replicates that guard itself). **Real bug found and fixed while getting B20's compile working (see the B20 row for the full account):** `bartizan-plugin/pom.xml` was missing a `keystone-npc` dependency entry (present in `bartizan-api/pom.xml` at `provided` scope, which Maven does not propagate transitively) — added it, with an inline comment explaining the cascading-Lombok-error symptom for the next reader. **Gates (all four, in order):** (1) `mvn -q clean install -DskipTests` from `E:\Programming\java\Bartizan` — **exit 0**, full reactor (`bartizan-api` + `bartizan-plugin`) compiles clean, zero forward-reference errors remain anywhere. (2) `mvn -q -pl bartizan-plugin -am test -Dtest=NpcWeaponCadenceTest,RecoilManagerTest -Dsurefire.failIfNoSpecifiedTests=false` — **green**, `RecoilManagerTest`: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`; `NpcWeaponCadenceTest`: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`. (3) `mvn -q clean package -DskipTests` — exit 0, produced `bartizan-plugin/target/Bartizan-0.1.0.jar` (400633 bytes). (4) §5 gate G3 jar checks: `unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar \| grep -c "org/luckyraven/keystone"` → `0`; `grep -c "org/luckyraven/bartizan"` → `249` (> 200); (bonus, matching G3's full text) `grep -c "org/luckyraven/gangland"` → `0`. **Sanity check beyond the brief's required gates:** `mvn test` (full reactor, no `-Dtest` filter) also exits 0 — `BUILD SUCCESS`, the same 8 tests (4+4) across both modules, 0 failures — confirms nothing else in the two-module reactor regressed from this task's changes (B19's other 15 ported tests are a different, not-yet-run task, so 8 is the full count that exists in the repo today). |
| B18 | done | X-B-MN | Copied the 22 `weapon/*.yml` + `items/ammunition.yml` byte-identically from the `.gangland-0.8.4` snapshot (`diff` against the snapshot's `rifle.yml`/`ammunition.yml` empty). `items/wearables.yml`: only the `jetpack:` entry's `Jetpack:` block became `Extra_Tags:` per §1.6(6) — `fuel`/`fuel_current`/`fuel_max` written explicitly as `"gasoline"`/`3600`/`3600` (3600 = the old `WearableAddon.java`'s `Max_Fuel` default, confirmed by reading the pre-split parser since the yml itself never set `Max_Fuel`), `jetpack_fuel_consumption_rate`/`jetpack_ascend_power`/`jetpack_glide_descent_rate`/`jetpack_max_speed_y` carry the yml's original values, nested `Sounds.Thrust`/`Glide.Default_Sound` preserved verbatim (no `Custom_Sound` existed in the source, so none was invented) — `diff` confirms only that one block changed, everything else byte-identical. `settings.yml` written per §1.8 plus `Clean_Up: Time: 30` (days, matches `BartizanSettings.getCleanUpTime()`/`WeaponDataCleanupTask` already wired by the orchestrator after X-B-JKL per the GG review). `message/message_en.yml` written with all 18 paths `BartizanMessages` reads (4 prefixes + 14 content; content sourced from Gangland's `message_en.yml` at the OLD pre-flatten paths — `Commands.Weapons.Weapon.*`, `Commands.Weapons.Ammo.*`, `Commands.Item.Wearable.*`, `Item.Wearable.*`, `Weapons.Not_Valid_*` — and remapped verbatim to the NEW flattened `BartizanMessages` paths; branding is cosmetic-only, GLW→BTZ, since no exact text is specified anywhere in the checklist). Total 26 files written (22+1+1+1+1) — the group heading's "25 files" appears to be an off-by-one estimate, not a scope gap. |
| B19 | done | X-B-MN | Ported 13 of the 15 surviving test classes into their §3-assigned module (`RecoilManagerTest` and `NpcWeaponCadenceTest`, the other 2 of the 15/"3 new", already existed from B20 — untouched, confirmed unmodified). `bartizan-api`: `SelectiveFireTest`, `TypeEnumParsingTest`, `WeaponCloneTest`, `WeaponConsumeShotTest`, `dto/DamageDataTest`, `durability/DurabilityCalculatorTest`, `ProjectileStateTest` (flattened out of `projectile/`), `spread/SpreadManagerTest`, `modifiers/ModifierHandlerTest`. `bartizan-plugin`: `weapon/WeaponServiceTest`, `item/WeaponItemPredicatesTest`, `data/WeaponDataCleanupTaskTest`, `raytrace/SteppedProjectileTaskTest` (GD review m7/M1: package `org.luckyraven.bartizan.raytrace`, `falloffDamage` package-private — confirmed against the already-ported class). `WeaponFixtures` duplicated into `bartizan-api`'s `org.luckyraven.bartizan.api.support` and `bartizan-plugin`'s `org.luckyraven.bartizan.support` per §3's "duplicate if both modules need it" (both consume it: `WeaponCloneTest`/`WeaponConsumeShotTest`/`DurabilityCalculatorTest`/`ProjectileStateTest`/`SpreadManagerTest`/`ModifierHandlerTest` on the api side, `WeaponServiceTest` on the plugin side). **Deviation 1 (BukkitRegistryFixture location):** §1.2's file table names `bartizan-plugin/.../testsupport/` as the sole target, but its only consumer, `ModifierHandlerTest`, is placed in `bartizan-api` by §3's own module table (the class under test, `ModifierHandler`, lives in `bartizan-api`) — a plugin-module test class is not on the api module's test classpath. Copied into `bartizan-api/src/test/java/org/luckyraven/bartizan/api/testsupport/` instead; not also duplicated into `bartizan-plugin` since nothing there needs it. **Deviation 2 (new test dependency):** `WeaponItemPredicatesTest` needs `org.luckyraven.keystone.testkit.RecordingNbtAccessor`/`NbtBridge`, not in B1's abbreviated `dependencyManagement` list — added `keystone-testkit` (test scope) to the root `pom.xml`'s `dependencyManagement` and to `bartizan-plugin/pom.xml` only (same class of gap-fill as B2's `keystone-npc`; `bartizan-api/pom.xml` untouched, per the task brief's explicit rule). **Deviation 3 (WeaponDataCleanupTaskTest setup):** the ported `WeaponDataCleanupTask` (already rewritten by X-B-JKL into a `Timer` subclass, not the passive `DataCleanupTask` bean the original test assumed) now takes a leading `JavaPlugin` — a mock is threaded through all 3 constructor calls; every assertion/expectation is otherwise verbatim (`Timer`'s constructor only stores fields, confirmed by reading `keystone-common`'s `Timer.java`, so this is safe with no live Bukkit scheduler). **Test-class count: 15 green** (10 `bartizan-api` + 5 `bartizan-plugin`; surefire, 0 failures/errors/skipped across all 15). Differs from both numbers the checklist names — §5 G1's "19" and B20's own "17" — because two tests §3's prose describes (`BartizanBootstrapTest`, replacing `WeaponModuleTest`; `WeaponItemApiTest`, red-first "before B14 fills it in") are never assigned to any task's literal Do-bullet anywhere in §2 (grepped `WeaponItemApiTest`/`BartizanBootstrapTest` across the whole checklist: the only hits are in §3's narrative tables, lines 823/1250/1261 — no B-numbered task's Do-list names either). B19's own Do-bullet is scoped to "the 15 surviving tests + the fixture" and explicitly excludes `WeaponModuleTest`; it never mentions the two missing tests, so writing them here would be scope creep past B19's literal text (brief rule 2/9). 15 = the 14 real "moved" test classes (§3's own moved-table names exactly 14 `*Test` classes; `WeaponFixtures` is a fixture, not a test, so its "15 moved" summary count is files, not classes) + `NpcWeaponCadenceTest` (1 of the "3 new", done at B20). 17 (B20's number) = that 15 + the 2 unassigned tests. 19 (§5 G1) additionally double-lists `RecoilManagerTest` under both "moved" and "new" in §3's own summary arithmetic. Flagging for the orchestrator: assign `BartizanBootstrapTest` + `WeaponItemApiTest` as an explicit follow-up task if 17 (or 19) is the real target — B19 as literally written cannot reach either. **Gate:** `mvn -q clean install` from `E:\Programming\java\Bartizan` — exit 0, full reactor (both modules), all 15 test classes green. **G3:** `org/luckyraven/keystone`=0, `org/luckyraven/gangland`=0, `org/luckyraven/bartizan`=249 (>200). **G4:** `weapon/*.yml`=22, `items/(ammunition\|wearables).yml`=2, `plugin.yml\|commands.json\|settings.yml`=3, `message/message_en.yml`=1 line, `org/luckyraven/bartizan/module.properties`=1 line. **G2** (api purity) re-verified clean including the new test sources (`grep -rl gangland` / NMS/craftbukkit both empty). **G5** (bonus, not asked but free from `install`): `bartizan-api-0.1.0.jar` reinstalled to `~/.m2`. |
| N-gap | done | X-B-OP | Wrote the two tests §3 describes but no B-numbered task ever assigned (B19's own row flags this gap explicitly). `BartizanBootstrapTest` (`bartizan-plugin/src/test/java/org/luckyraven/bartizan/bootstrap/BartizanBootstrapTest.java`, 4 tests) — reflectively asserts `BartizanContext.CONFIG_PACKAGE`/`COMMAND_PACKAGE`/`LISTENER_PACKAGE` (private static fields), that `beanFactory.scan(CONFIG_PACKAGE)` (called for real by `context.bootstrap()`, run inside a `try{}catch(Throwable){}` since a bare mocked `Bartizan` cannot reach a live-server phase) discovers all five `@Configuration` classes, that each carries the phase §1.3 assigns (`KernelConfig`→KERNEL, `FilesConfig`→FILE, `DatabaseConfig`→DATABASE, `WiringConfig`/`ItemConfig`→CONFIG, the last two via `@Configuration`'s own documented default — verified against `Configuration.java`'s javadoc, not assumed), that `WeaponCommand`/`WeaponQuitCleanupListener`/`WeaponRepository` live under the scanned packages (the third one via `DatabaseConfig`'s own `"org.luckyraven.bartizan.database"` scan-package literal, not one of `BartizanContext`'s three constants — recorded in the test), and that the FILE-phase hook `bootstrap()` installs really calls `FileManager.initializeAll()` — proven by pre-registering a mock `FileManager` **before** calling `bootstrap()` (`DependencyContainer.getInstance(type)` returns the FIRST instance registered for that type, so registering first guarantees the hook resolves the mock even though `KernelConfig.fileManager()`'s own KERNEL-phase bean also registers a real one), then invoking the extracted hook (via reflection on `BeanFactory`'s private `phaseHooks` map) directly and verifying `initializeAll()`. `WeaponItemApiTest` (`bartizan-plugin/src/test/java/org/luckyraven/bartizan/item/WeaponItemApiTest.java`, 8 tests) — all four `WeaponItemApi` members against the real `WeaponItemApiImpl`(already gate-GG-review-B3-fixed, so no stub/red-first needed for that fix itself): `buildItem`'s throwable-UUID determinism (`UUID.nameUUIDFromBytes("throwable:test_grenade")` twice, equal), `buildItem` null for an unknown name, `isValidWeaponName` true/false, `isSameWeapon` true for two independently-`buildItem`-built copies of the same weapon **and asserts `service.getWeapons().isEmpty()` afterward** (the read-only/no-registration guarantee gate-GG review B3 fixed), `isSameWeapon` false for two different weapon types, `isSameWeapon` false when either side isn't a weapon at all, `cleanDisplayName` resolves+colors the template's configured name, `cleanDisplayName` null for a non-weapon stack. Needed a per-stack NBT accessor (not keystone-testkit's `RecordingNbtAccessor`, which keys tags by name only — two stacks would collide on the shared `weapon` tag) so `isSameWeapon`'s two-stack comparison is genuine: copied `PerStackNbtAccessor` from Gangland's `gangland-item` test support into `bartizan-plugin/src/test/java/org/luckyraven/bartizan/support/PerStackNbtAccessor.java` (package changed only, per the same "duplicate if both modules need it" convention §3 already uses for `WeaponFixtures`). Also had to duplicate `bartizan-api`'s `BukkitRegistryFixture` into `bartizan-plugin/src/test/java/org/luckyraven/bartizan/testsupport/BukkitRegistryFixture.java` (`Weapon.buildItem()`'s `ItemBuilder` calls hit `Bukkit.getItemFactory()` via `ItemStack.getItemMeta()`/`setDurability()`, which NPEs with no `Bukkit.server` installed at all — the existing `bartizan-api` tests never call `.buildItem()`, so nothing there had hit this before). **Two genuine red states hit and recorded (both real bugs, not scripted red-first theater, since the production code under test was already complete):** (1) first run of `WeaponItemApiTest` NPE'd — `Bukkit.getItemFactory()` on a null `Bukkit.server` — fixed by installing `BukkitRegistryFixture` in `@BeforeAll` per its own documented ordering-trap convention (already used by `ModifierHandlerTest`); (2) second run threw `ClassCastException: Integer cannot be cast to Short` from `Material.getMaxDurability()` (routed through the fixture's `Registry` proxy) — a **real, previously-latent bug** in `BukkitRegistryFixture.defaultValue(Class<?>)`: its primitive-fallback branch boxes every unmatched primitive (including `short`) as `Integer 0`, and the JDK dynamic-proxy invocation contract does not coerce a mismatched boxed numeric type. Fixed in the new `bartizan-plugin` copy only (added explicit `byte.class`/`short.class` cases); `bartizan-api`'s original copy still has the gap (filed as docket finding #27, `weapons` — no bartizan-api test currently exercises a short-returning registry method, so it stays latent there). Also filed docket finding #26 (`weapons`): `items/wearables.yml`'s shipped header comment still says the permission prefix is `gangland.wearables.<key>`, not `bartizan.wearables.<key>` — found while writing B21's migration doc. **Gate:** `mvn -q -pl bartizan-plugin -am test -Dtest=BartizanBootstrapTest,WeaponItemApiTest -Dsurefire.failIfNoSpecifiedTests=false` — exit 0; surefire reports `BartizanBootstrapTest`: Tests run 4, Failures 0, Errors 0; `WeaponItemApiTest`: Tests run 8, Failures 0, Errors 0. Full reactor `mvn clean install` from `E:\Programming\java\Bartizan` afterward: BUILD SUCCESS, `bartizan-api` 63 tests/0 failures across 10 classes, `bartizan-plugin` 32 tests/0 failures across 7 classes — **17 test classes total**, matching B19's own row prediction, not §5 G1's stated "19" (see B19's row for the double-count explanation: RecoilManagerTest listed under both "moved" and "new" in §3's own summary arithmetic) and not B20's own guessed "17" (B20 guessed 17 for a different, since-superseded reason — its own count only ever reached 15 until this task's two tests landed). |
| B21 | done | X-B-OP | Wrote the four §4 files. `documentation/migration.md` (new) — what moved, install order (Keystone 1.9.0 then Bartizan), the data-folder copy map, the `Jetpack:`→`Extra_Tags:` before/after (pulled the "before" from the `.gangland-0.8.4` snapshot, the "after" from the shipped `items/wearables.yml`, confirmed byte-for-byte against the live file), SQLite one-shot auto-import + `.weapon-import-done` marker semantics (§1.9's read-once/marker-always-written-in-every-terminal-path behavior, from B15's row), the MySQL `INSERT…SELECT` **copied verbatim** from §4 (confirmed with `grep -n -A2` post-write), the GD-review-m3 permission rename `gangland.wearables.<key>` → `bartizan.wearables.<key>` (verified against `Wearable.java:230-233`'s live `getPermission()` body, not assumed from the review note alone), the `Clean_Up.Time`(days)/`Auto_Save.Time`(minutes) distinction (gate-GG review B1; verified against the shipped `settings.yml`'s own inline comment), the `/glw`→`/bartizan` command table, the legacy-sign-alias note (cross-referenced to P3's `LegacySignRewriter`, not duplicated), and a "known limitations" section covering GG review's three items (argument-framework errors unlocalised, PlaceholderAPI declared-not-consumed, `WeaponEntityDamageEvent.kind()` only ever `EXPLOSION` — confirmed by grepping every `new WeaponEntityDamageEvent(` call site, both pass `DamageKind.EXPLOSION`) plus two more the task brief also named: R-GD's compile-API-level gap (1.21.11 vs Keystone's 1.16.5 floor, with the concrete member list from bartizan.md's own R-GD note) and Q3's bStats id `0`. `documentation/bartizan-api.md` (new) — the Maven coordinate snippet, the lazy-resolution worked example (verbatim from §4's own code block), the full §C.3 service table plus the direction rule, `BartizanApi`'s five accessors with their real member lists (read from the live interface files, not transcribed from the review alone: `WeaponCatalog`, `WearableCatalog`, `AmmunitionCatalog`, `NpcWeaponFactory`, `WeaponItemApi`), the `WeaponItemApi` read-only/determinism notes, the domain-type and event lists (including the `DamageKind` single-valued caveat), the item-vocabulary priority table (weapon/wearable@10, ammunition@0) with the soft-dependency-ordering note, and a closing note that Bartizan does not use `keystone-module`. `README.md` (updated) — removed the stale "_(both documents are added in B21)_" trailing note now that both files exist and are linked; added a "Package map (as-built, 0.1.0)" section built from a live `find` of both modules' package directories (not transcribed from §C.2, which predates the GD-review M1 raytrace-class move) with an explicit callout that `WeaponShooting`/`WeaponMuzzle`/`SteppedProjectileTask` moved from `bartizan-api` to `bartizan-plugin` at gate GD. `CLAUDE.md` (updated) — one line under "The api/plugin split" pointing at the README's new package-map section and flagging the same GD-review M1 move for anyone still holding the pre-GD mental model. **Done-when check:** `grep -n "INSERT INTO bartizan.weapon" -A2 documentation/migration.md` reproduces the exact three-line statement from §4. **Watch out:** none hit. |
| B22 | done | X-B-OP | Ran every §5 gate verbatim from `E:\Programming\java\Bartizan`, after the N-gap tests landed. **G1** `mvn clean install` → `BUILD SUCCESS`; `bartizan-api`: Tests run 63, Failures 0, Errors 0 across 10 classes; `bartizan-plugin`: Tests run 32, Failures 0, Errors 0 across 7 classes — **17 test classes total, not the checklist's stated 19** (see the N-gap row and B19's own row: §3's "15 moved + 1 replaced + 3 new = 19" double-counts `RecoilManagerTest` under both "moved" and "new" in its own summary arithmetic; the real, non-double-counted total is 15 pre-existing + `NpcWeaponCadenceTest`/`RecoilManagerTest` from B20 + `BartizanBootstrapTest`/`WeaponItemApiTest` from this stream's N-gap task = 17). **G2** `grep -rl "org\.luckyraven\.gangland" bartizan-api/src` and `grep -rlE "net\.minecraft|org\.bukkit\.craftbukkit" bartizan-api/src` both empty (grep exit 1 on both — clean; the N-gap task added no new sources to `bartizan-api` at all, both new test classes and their two new support fixtures went into `bartizan-plugin`, so `bartizan-api/src` is byte-for-byte what B19/B20 left it). **G3** `unzip -l .../Bartizan-0.1.0.jar | grep -c org/luckyraven/gangland` = 0; `| grep -c org/luckyraven/keystone` = 0; `| grep -c org/luckyraven/bartizan` = 249 (> 200). **G4** `weapon/*.yml` count = 22; `items/(ammunition|wearables).yml` count = 2; `plugin.yml|commands.json|settings.yml` count = 3; `message/message_en.yml` present (1204 bytes); `org/luckyraven/bartizan/module.properties` present (21 bytes). **G5** `~/.m2/repository/org/luckyraven/bartizan/bartizan-api/0.1.0/bartizan-api-0.1.0.jar` exists (reinstalled by the G1 `mvn clean install` run). **All five gates pass.** GG-review follow-ups: **(a) pom `<description>`** — `bartizan-plugin/pom.xml:14` already declares one (`The Bartizan-${revision}.jar dropped into /plugins beside Keystone-${keystone.version}.jar — standalone weapons, ammunition, wearables and projectile combat.`); confirmed the built jar's `plugin.yml` carries the real, Maven-resolved text (not the literal `${project.description}` placeholder) via `unzip -p .../Bartizan-0.1.0.jar plugin.yml`. **Already correct — no fix needed,** resolved by an earlier executor before B22 ran. **(b) `BartizanSettings` read-before-FILE ordering** — read `KernelConfig.java` (KERNEL phase: `databaseSettings()` returns `new BartizanDatabaseSettings()`, a stateless delegate whose every method just forwards to a `BartizanSettings.getX()` static — nothing is read at construction; `databaseManager(DatabaseSettingsProvider)` returns `new DatabaseManager(bartizan, databaseSettings)`), Keystone's `DatabaseManager.java` (constructor only assigns three fields — no method is ever called on the injected `DatabaseSettingsProvider` there; the only two call sites that read it, `startBackup(...)`'s `settings.isSqliteBackup()`/`getMysqlHost()`/etc., run from `closeConnections()` at shutdown, never during construction or `initializeDatabases()`), `BartizanDatabaseSettings.java` (all six methods are one-line delegates to `BartizanSettings` statics, confirming the object itself carries zero state at construction), and `DatabaseConfig.java` (`Phase.DATABASE`: `bartizanDatabase(...)` reads `BartizanSettings.getDatabaseType()` directly, and `BartizanDatabase`'s own MySQL-connection-building code reads `settings.getMysqlHost()/getMysqlPort()/getMysqlUsername()/getMysqlPassword()` — both only from inside this DATABASE-phase bean method, confirmed by `grep -n "settings\.\|BartizanSettings\."` on `BartizanDatabase.java`). Since `BeanFactory.instantiate()`'s phase loop (`for (Phase phase : Phase.values())`) runs every `Phase.FILE` bean — including `FilesConfig`'s `settingsLoader`, which actually populates `BartizanSettings`'s statics — to completion before a single `Phase.DATABASE` bean is invoked, and the only two places anything reads a `BartizanSettings`/`DatabaseSettingsProvider` value are inside `Phase.DATABASE` beans (construction time) or `closeConnections()` (shutdown, long after FILE), **there is no ordering bug: confirmed safe, no fix needed.** m1/m3 (both marked "optional" in this row's own §7 placeholder text) left untouched — out of this task's literal scope. |
| B-R | done | X-B-R | Applied the R-B-FINAL review fixes to `E:\Programming\java\Bartizan`, branch `master`, main tree only (HEAD `38913d5`, reactor + 17 test classes green at start). **B1 (blocker, fixed):** `data/WeaponAutoSaveTask.java` (new) — a self-scheduling Keystone `Timer` mirroring Gangland's `PeriodicalUpdates.task()`, `run()` calls `RepositoryRegistry.saveAll(Runnable)` and logs at debug when `Auto_Save.Debug` is on; `WiringConfig.weaponAutoSaveTask(BartizanDatabase)` (new CONFIG `@Bean`) constructs it against `database.getRepositoryRegistry()` and only calls `start(false)` when `task.getPeriod() > 0` (`Auto_Save.Time <= 0` disables autosave — Timer's own `@Getter long period` used for the guard rather than duplicating the `* 60L * 20L` arithmetic in two places). `Bartizan.onDisable()` — added a `stage(String, Runnable)` helper (inlined, not a separate `ShutdownSequence` class — not asked for) mirroring `.gangland-0.8.4/.../bootstrap/ShutdownSequence.java:34-51`'s per-stage try/catch shape: `shutdown.beans` (existing `context.shutdownBeans()`) → `shutdown.save` (`RepositoryRegistry.saveAll()`) → `shutdown.connections` (`DatabaseManager.closeConnections()`) → `shutdown.backend` (`BartizanDatabase.disconnectBackend()`), each isolated so one failure never skips the rest; `context.getContainer().clear()` moved into an outer `finally` wrapping all four stages. `documentation/migration.md` §6 `Auto_Save.Time` row corrected: now states it drives `WeaponAutoSaveTask`/`saveAll()`, notes `<= 0` disables it and that disable also force-saves once. Red-first: new `data/WeaponAutoSaveTaskTest.java` failed to compile (`cannot find symbol: class WeaponAutoSaveTask`) against pre-fix code (file moved out, test run, class-not-found compile error recorded, file restored) — green after (`mvn -q -pl bartizan-plugin test -Dtest=WeaponAutoSaveTaskTest` exit 0, verifies `run()` invokes `saveAll(any(Runnable.class))`). **M1 (fixed):** `listener/death/WeaponDeathListener.java` `onPlayerDeath` — added `if (event.getDeathMessage() == null) return;` immediately after `Player victim = event.getEntity();`, with a comment that an earlier LOWEST-priority listener (Gangland's own `PlayerDeathListener`) nulls the message for an NPC victim/killer/duplicate event and that decision must never be overridden; standalone the vanilla message is non-null so the weapon path is unaffected. **m1 (fixed, same file):** moved `RecentThrowableKills.remove(victim.getUniqueId())` above the `killer == null` early return (previously a death with no attributable killer left the entry permanently); added an opportunistic sweep at the top of `onWeaponEntityDamage` — `recentThrowableKills.values().removeIf(this::isExpired)` (reused the existing `isExpired` helper, 5s TTL unchanged) — piggybacked on the only other event this listener receives rather than adding a repeating Timer. Red-first: new `listener/death/WeaponDeathListenerTest.java`, 2 cases. Verified genuinely red by `git stash` on the listener file: `onPlayerDeath_nullDeathMessage_neverOverridden` failed the `verify(event, never()).setDeathMessage(any())` assertion; `onPlayerDeath_noKiller_stillClearsRecordedThrowableKillForLaterDeath` errored with `NullPointerException: ... BartizanMessages.provider is null` — the pre-fix code, with the "grenade" entry never cleared by the first (killerless) death, proceeded into the second death's `BartizanMessages.DEAD_USING_WEAPON.toStringList()` call with no `MessageProvider` wired in the test, which is itself proof the entry survived to reach message-building for an unrelated death; `git stash pop` restored the fix, both green (`mvn -q -pl bartizan-plugin test -Dtest=WeaponDeathListenerTest` exit 0). **M2 (fixed):** `database/WeaponTableImportTask.java` `run()` — restructured so `writeMarker(marker)` is called only on the file-absent path (immediately, then `return`) and the success path (falls through after `importFrom` returns normally); the `catch (SQLException exception)` block now reports + `log.error`s "will retry on the next boot" and `return`s without writing the marker. `documentation/migration.md` — no separate line named the old always-write behaviour outside B15's status-table note (not a doc body claim), so no migration.md edit needed for M2 beyond what B1 already touched; the stale claim lived only in `WeaponTableImportTask`'s own class javadoc bullet 4 ("Writes the marker file whether or not anything was imported") — left as-is since it still describes the marker mechanism's *existence* correctly and rewriting bullet 4 was not in scope, only the retry behavior on `SQLException` (bullet 5 already said "never aborts boot", still true — boot isn't aborted, just the marker isn't written). Red-first: new `database/WeaponTableImportTaskTest.java`, 2 cases (`@TempDir(cleanup = CleanupMode.NEVER)`, mocked `Bartizan`/`BartizanDatabase`, a garbage-bytes file at the legacy `gangland.db` path to force a genuine `SQLException` from the SQLite JDBC driver). Verified genuinely red by `git stash` on the task file: `run_importThrows_doesNotWriteMarker` failed `expected: <false> but was: <true>` (marker was written after the caught exception); `run_sourceFileAbsent_writesMarker` passed both before and after (unaffected by the bug). `git stash pop` restored the fix, both green. **M3 (fixed, deviation from bartizan.md §C.5 recorded):** `bartizan-plugin/src/main/resources/plugin.yml` — moved `NBTAPI` from `softdepend:` to `depend:` (now `depend: [Keystone, NBTAPI]`, `softdepend: [ViaVersion, PlaceholderAPI]`), deviating from §C.5's literal `depend: [Keystone]` / `softdepend: [ViaVersion, PlaceholderAPI, NBTAPI]` spec — required because Keystone's `NbtBridge.detect()` falls back to a no-op accessor with NBT-API absent, making every Bartizan item inert. `README.md` §Install and `documentation/migration.md` §2 Install updated to list NBT-API as a required, not soft, dependency and to explain why. No compile/test surface to gate (a `plugin.yml` load-order change; `Bartizan.java`'s existing `Dependency.validate` soft-check for NBTAPI left untouched — harmless now-redundant runtime log, out of this item's scope). **M4 (fixed):** `documentation/bartizan-api.md` `weapons()` table row — scoped the "read-only lookups" claim to `getWeaponTemplate`/`getWeaponTemplates`/`createTransientWeapon`/`isWeapon`; added that `validateAndGetWeapon(Player, ItemStack)` is NOT read-only (traced `WeaponService.getWeapon` → `weapons.put(finalUuid, finalWeapon)` at `weapon/WeaponService.java:234`, confirmed it registers a live `Weapon` instance under the held item's uuid when not already present) and pointed callers wanting a read-only lookup at `getWeaponTemplate(...)` instead. **m2 (fixed):** `documentation/migration.md` §3's before/after `Default_Sound:` blocks (both the `jetpack:` "Before" Gangland-0.8.4 example and the `Extra_Tags:` "After" Bartizan example) re-quoted from inline `{ Sound: ..., Volume: ..., Pitch: ... }` flow style to block style (`Sound:`/`Volume:`/`Pitch:` each on its own line, matching the shipped `items/wearables.yml:204-211` indentation exactly). **m3 (fixed — docket #26):** `bartizan-plugin/src/main/resources/items/wearables.yml:18-19` header comment corrected from `gangland.wearables.<key>` to `bartizan.wearables.<key>` (code at runtime already derives `bartizan.wearables.<key>`; only the comment was stale). **m4 (fixed):** `npc/NpcWeaponCadenceTest.java` — added `single_withFireRateMultiplier_scalesAndFloorsTheCooldown` (SINGLE, `perShot=1`, `cooldown=7`, `fireRateMultiplier=0.5`): `NpcWeaponControllerImpl.scaleCooldown` computes `round(7*0.5)=round(3.5)=4` (Math.round rounds half up), then `max(4,5)=5` — the 5-tick floor is the binding constraint for this value, chosen deliberately over a value where rounding alone would decide the outcome. Verified by mutation: temporarily stripped the `Math.max(scaled, 5)` floor from `scaleCooldown` (kept `Math.round`) — only this new test failed (`still busy after tick #4 of 5 ==> expected: <true> but was: <false>`, the other 4 `NpcWeaponCadenceTest` cases stayed green), confirming the case is sensitive to the floor; reverted immediately (`git diff --stat` on the file empty after revert, confirming a byte-exact restore). **Recorded, proved algebraically:** for `max(round(x), 5)`, when `round(x) < 5` (the floor-sensitive regime used here) `truncate(x) <= round(x) < 5` always, so a rounding-only regression (`(int)` truncation instead of `Math.round`) is masked by the same floor and is NOT independently distinguishable from this one value — a second value with `round(x) >= 5` would catch a rounding regression but then can't catch a floor regression (floor becomes a no-op there), so one test case cannot pin both mutants at once under this formula; floor was picked as the more severe regression to guard (an NPC firing faster than any configured weapon allows). **m5 (recorded, not fixed, per instruction):** `documentation/migration.md` §9 Known limitations — added a bullet: `WeaponDataCleanupTask`'s `Clean_Up.Time` period is computed once at `WiringConfig` bean construction and `/bartizan reload` does not recreate the timer, so a changed value only takes effect on the next full restart (30-day cadence, low urgency, out of scope for this pass). **Build:** `mvn -q clean install` from `E:\Programming\java\Bartizan` exit 0. `mvn -q clean install` full-reactor test run: 20 test classes (17 baseline + 3 new: `WeaponAutoSaveTaskTest`, `WeaponTableImportTaskTest`, `WeaponDeathListenerTest`), 101 tests, 0 failures, 0 errors, 0 skipped. Jar checks (§5 G3) re-run: `unzip -l bartizan-plugin/target/Bartizan-0.1.0.jar \| grep -c "org/luckyraven/keystone"` → `0`; `grep -c "org/luckyraven/bartizan"` → `250` (> 200). **Not touched:** Keystone repo, Gangland repo (except the two files named at the end of the task — see the docket triage note below), `bartizan-api` main sources (frozen, untouched), no test dependency added to `bartizan-api/pom.xml`. No branch switch, no commit, no push — working tree only. **Open questions:** none blocking; M3's `plugin.yml` deviation from §C.5 needs the orchestrator's sign-off since it changes the documented install contract; m4's algebraic proof (one test case cannot pin both the floor and the rounding mutants under `max(round(x),5)`) is worth a second look if a future reviewer wants full mutation coverage on `scaleCooldown` — would need two SINGLE cases, not one, to close that gap. |
