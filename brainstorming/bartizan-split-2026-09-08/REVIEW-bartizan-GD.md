# Gate review GD — Bartizan groups C+D+E (B5, B6, B7, B20-half)

Reviewer R-B-CDE (Opus, feature-dev `code-reviewer`, read-only), 2026-09-08 ~20:00. Reviewed the 71 main files under
`bartizan-api/src/main/java/org/luckyraven/bartizan/api/` plus `RecoilManagerTest` against `bartizan.md` §Contract,
§1.1 (groups C/D/E), §1.6 (1)(3)(5)(6)(7)(8)(9), §2 B5–B7/B20, §6, §7, `architecture/PICK.md` Amendments (a)(b) and the
Gangland originals. Transcribed by the orchestrator; orchestrator rulings are marked **→**.

## Verdict: GD PASS WITH FIXES — P3 may start compiling against `bartizan-api`

Every type and member §1.6 and PICK specify exists with the specified name and signature; the two behaviour-changing
rewrites a compiler cannot catch (recoil transform, reload polarity) are correct against the originals line by line,
and the recoil one is pinned by a test that fails on a sign flip or a divisor change. The findings are surface
hygiene; none changes a type P3 consumes.

## Findings

### Major

**M1 — three runtime classes were pulled into the frozen api that nothing outside Bartizan will call.**
`api/raytrace/WeaponShooting.java`, `api/raytrace/WeaponMuzzle.java`, `api/raytrace/SteppedProjectileTask.java`.
`SteppedProjectileTask` is a per-tick `RepeatingTimer` driver that spawns particles, plays a sound and calls
`target.damage(...)` in an AOE loop — implementation, not contract, now permanently public. Chain: `WeaponShooting`
was planned into the api (§C.2, because cops read `SPREAD_PELLET_COUNT`) → `fireSlow` constructs
`SteppedProjectileTask` and calls `WeaponMuzzle.compute` → both were forced across. The premise no longer holds:
outside `gangland-weapon` the only `WeaponShooting` caller is `cops-n-crooks/npc/NpcCombatDelegate.java:334`, which
becomes Bartizan's own `NpcWeaponControllerImpl` (§1.6(8), PICK ruling (b)); `SPREAD_PELLET_COUNT` has no other
reader. Fix: move the three to `PLG/raytrace/`, delete them from the api. Cheap now, breaking after B13.
`RaytraceContext` and `WeaponVisualSpawner` are genuinely forced by `WeaponRaytracer`'s own §1.6(7) signature
(`getVisualSpawner()`, `advanceSegment(RaytraceContext, …)`) and are accepted.
**→ Applied after executor X-B-FG reports (it may be reading these files): the three classes move to
`bartizan-plugin` package `org.luckyraven.bartizan.raytrace`, the api is reinstalled to `~/.m2`, §C.2 amended.
`WeaponShooting.SPREAD_PELLET_COUNT` stays `public static final` plugin-side.**

**M2 — `@Nullable` dropped from every catalog method whose implementation returns null.**
`WeaponCatalog.getWeaponTemplate/createTransientWeapon/validateAndGetWeapon`, `WearableCatalog.getWearable`,
`WeaponItemApi.buildItem`. The originals are annotated (`WeaponService.java:129-130,155-156,235-236`,
`WearableService.java:47-48`); `resolveWearable` kept its `@Nullable`, so the rest is an oversight. Fix before P3
writes code against it: annotate returns and nullable params; state what `buildItem` does for an unknown name.
**→ Applied now.**

### Minor

**m3** — `Wearable.getPermission()` now returns `"bartizan.wearables." + key` (was `gangland.wearables.`). Right for a
standalone plugin, but unrecorded and it invalidates existing permission grants. **→ recorded in §7; B21's
`documentation/migration.md` must list `gangland.wearables.<key>` → `bartizan.wearables.<key>`.**

**m4** — `CombatEligibility.resolve()` NPEs when no Bukkit server is installed (`Bukkit.getServicesManager()` on a
null static), so no unit test can drive `InstantReload`/`NumberedReload` — the polarity inversion is pinned by
nothing. Fix: `if (Bukkit.getServer() == null) return DEFAULT;`. **→ Applied now.**

**m5** — the `canBeHit` polarity contract is not stated where P3 (the implementor) will read it. Fix: one javadoc
line — returns `false` for a player who is out of combat (downed, dead …); callers gate with `!canBeHit(player)`,
the inverse of the old `DownedPlayerRegistry.isDowned`. **→ Applied now.**

**m6** — `WeaponShooting.java:22` javadoc names `AbstractNpc#performGanglandWeaponAttack`, which will not exist after
P3. **→ Reworded to `NpcWeaponControllerImpl` (file moves plugin-side with M1).**

**m7** — `SteppedProjectileTaskTest`'s planned home (§3: `bartizan-plugin`) — `falloffDamage` is package-private.
**→ Moot once M1 moves the class to the plugin; B19 note added.**

**m8** — `InstantReload.java:15` imports `Reload` from its own package (sed residue). **→ Dropped now.**

### Verified clean

Api surface = §1.6 + PICK exactly: `BartizanApi` five accessors; `WeaponCatalog` the five `WeaponService` methods;
`AmmunitionCatalog` (2); `WearableCatalog` (6); `WeaponItemApi` (4); `NpcWeaponFactory.create(LivingEntity, String,
double, double)`; `NpcWeaponController extends org.luckyraven.keystone.npc.spi.NpcRangedAttack` (Keystone 1.9.0's SPI
carries `default void tick()` and `NONE`, so PICK ruling (b) holds); `CombatEligibility` `@FunctionalInterface` with
`DEFAULT = player -> !player.isDead()`; `BartizanItemPredicates.WEARABLE` only; `WeaponEntityDamageEvent` gains
`DamageKind{DIRECT,EXPLOSION,FIRE,BIOLOGICAL,MELEE}`, `weaponName()`, `kind()`, no static maps; `WeaponRaytracer` is
an interface with the four §1.6(7) members plus a forced `setRaytraceDamageInProgress` (the original writer was
private static inside the class). Recoil: `RecoilManager` line-for-line the original with only the
`RecoilCompatibility` parameter removed and `recoil` rewritten to `PacketBridge.adapter().relativeCameraRotation(player,
-yaw + 1, pitch - 1)`; sneak/scope branches untouched (pattern path `/2` scoped, `/4` unscoped; default path the same);
pattern-index advance unchanged; the ViaVersion `v1_13` gate dropped per Q1's default. `RecoilManagerTest` installs a
capturing adapter, resets in `@AfterEach`, pins the four cases with exact values — note the checklist prose has the
divisors backwards (`/2` sneak, `/4` sneak+scoped); the source and the test are right, do not "fix" the test to the
prose. Reload polarity correct at all four sites (`InstantReload:61,74`, `NumberedReload:90,132`), no other line
differs. `Wearable`: all eight trait rows and the five caps identical to `WearableTrait.java:20-60`; `traits()`,
`traitLevel(String)`, `extraTags()` added; `isJetpack()` and the fuel fields removed; NBT stamp loop as specified —
`items/wearables.yml` must carry `fuel_current` explicitly (the old code derived it from `maxFuel`). PKG-only ports
body-identical by line count for all 33 pairable files; the only three that differ are the three that should
(`BlockRegenerationSettings` javadoc, `Reload` inlined helper, `WeaponRaytracer` interface extraction). Purity: zero
`org.luckyraven.gangland`, NMS, `io.papermc`, `%gangland_*%`, `keystone.{bean,persistence,module,command,hooks}` in
the api; `@Nullable` is `org.jetbrains.annotations` throughout; no collapsed method bodies.

## Deviation rulings

| # | Deviation | Ruling |
|---|---|---|
| B1 | `CombatEligibility.resolve()` static lookup instead of ctor injection | **Accept** — a lazy `ServicesManager` lookup with a `DEFAULT` fallback, not a static registry; ctor injection would thread a parameter through `Weapon`'s constructor (`ReloadType.createInstance(Weapon, Ammunition)`) and every call site. Apply m4 + m5. |
| B2 | `Reload.java` inlines `WeaponService.getWeaponUUID` as `private static readWeaponUUID` | **Accept** — logic identical to `WeaponService.java:36-49`, widens nothing, removes an api→plugin edge §1.1 missed. |
| B3 | `Ammunition.getHeldAmmunition` parameter `AmmunitionManager` → `AmmunitionCatalog` | **Accept, an improvement** — the manager's two used members are exactly the catalog's; every call site type-checks once the manager `implements AmmunitionCatalog`. |
| B4 | Four extra files in `api/raytrace` | **Split** — `RaytraceContext` + `WeaponVisualSpawner` accepted (forced, pre-authorised by B7); `WeaponMuzzle` + `SteppedProjectileTask` (+ `WeaponShooting`) → M1. |
| B5 | `CombatEligibility` created in B5 | **Accept** — B5's Done-when permits it, recorded, GC's corrected criterion accounts for it. |

## The api surface P3 may rely on

P3 compiles against `org.luckyraven.bartizan:bartizan-api:0.1.0` at `provided` scope and reaches everything through
one lazy, never-cached `ServicesManager` lookup of `org.luckyraven.bartizan.api.BartizanApi`, whose provider exposes
five accessors: `weapons()` → `weapon.WeaponCatalog` (`getWeaponTemplate(String)`, `getWeaponTemplates()`,
`createTransientWeapon(String)`, `validateAndGetWeapon(Player, ItemStack)`, `isWeapon(ItemStack)`; the lookups may
return null), `wearables()` → `wearable.WearableCatalog` (`getWearable(String)`, `getWearables()`,
`resolveWearable(@Nullable ItemStack)`, `applyWearableReduction(double, LivingEntity, boolean)`,
`reduceCritBonus(double, LivingEntity)`, `reduceFireTicks(int, LivingEntity)`), `ammunition()` →
`ammo.AmmunitionCatalog` (`getAmmunitionKeys()`, `getAmmunition(String)`), `npcWeapons()` →
`npc.NpcWeaponFactory.create(LivingEntity, String weaponName, double fireRateMultiplier, double aimErrorDegrees)`
returning `npc.NpcWeaponController extends org.luckyraven.keystone.npc.spi.NpcRangedAttack` (sole implementation of
that SPI; `gangland-civilians`' `BartizanNpcWeapons` hook hands it to `AbstractNpc.setRangedAttack(...)` and returns
`NpcRangedAttack.NONE` when Bartizan is absent), and `items()` → `item.WeaponItemApi` (`buildItem(String)`,
`isValidWeaponName(String)`, `isSameWeapon(ItemStack, ItemStack)`, `cleanDisplayName(ItemStack)`). Gangland
**registers** `combat.CombatEligibility` (`boolean canBeHit(Player)`; return `false` for a downed player — the inverse
of today's `DownedPlayerRegistry.isDowned`) and Bartizan pulls it. Domain types P3 may name: `weapon.Weapon` and its
five subclasses, `weapon.{WeaponType, ThrowableType, SelectiveFire, WeaponTag, ProjectileType, ProjectileState}`, the
15 `weapon.dto.*` records, `ammo.Ammunition`, `wearable.Wearable` (string traits via `traits()`/`traitLevel(String)`;
jetpack data via `extraTags()` — test `extraTags().containsKey("fuel")` instead of the deleted `isJetpack()`; NBT keys
`fuel`/`fuel_current`/`fuel_max` unchanged so `FuelService` needs no edit), `BartizanItemPredicates.WEARABLE`. Events
in `api.event`: `WeaponEvent`, `WeaponShootEvent`, `WeaponRaytraceImpactEvent` (cancelling suppresses damage only),
`WeaponEntityDamageEvent` with `weaponName()` and `kind()` (how `CarDamageListener` replaces
`ThrowableAction.pendingVehicleExplosionDamage`), `WeaponKillEntityEvent`, `WeaponReloadEvent`/`Start`/`Complete`,
`WeaponChangeSelectiveFireEvent`. `raytrace.WeaponRaytracer` stays the `ServicesManager` key with today's call shape;
the only member a Gangland module still uses is the static `WeaponRaytracer.isRaytraceDamageInProgress()` in
`cops-n-crooks/listener/detainment/CopListener.java:105`, which contradicts §C.3's "no Gangland class consumes it" —
**P3 must resolve it** (see the orchestrator note in `gangland-0.9.0.md`). Everything else under `api.raytrace` is
Bartizan-internal; P3 must not reference it.

## Not verifiable by the reviewer (no shell)

The recorded Maven outcomes and commit `d112e7a`; byte-for-byte equality beyond line counts and the spot-checked
bodies; whether javac's effective classpath excludes `keystone-bean`/`keystone-persistence` (ruled acceptable at GA);
the red-first state of `RecoilManagerTest`; line endings.
