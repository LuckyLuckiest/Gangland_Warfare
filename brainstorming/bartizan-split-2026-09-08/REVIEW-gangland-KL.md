# Gate review K/L — Gangland 0.9.0 cops rebase and gadget re-point

Reviewer R-G-KL (Opus, feature-dev `code-reviewer`, read-only), 2026-09-09 ~12:10. Reviewed commits `b3c60602` (I-R + K)
and `7e512679` (L) on `0.9.0` against the checklist, PICK, the H review, the Bartizan producers (`ThrowableAction`,
`WeaponEntityDamageEvent`, `wearables.yml`, `NpcWeaponControllerImpl`) and Keystone 1.9.0's `keystone-npc`. Transcribed
by the orchestrator; orchestrator actions are marked **→**.

## Verdict: K/L PASS WITH FIXES (2 blockers, 2 majors)

Both gates compile and the reactor is green, but three of the group's own "Do" items are unimplemented or landed on the
wrong handler, and one status-table row records a change that is not in the working tree.

## Findings

### Blocker

**B1 — T-K5 is entirely unimplemented; its status row is factually wrong.** `grep -rn "NpcSupport"
gangland-features/cops-n-crooks/src` returns nothing: `CopNpcFactory.createCop` reaches `CitizensAPI.getNPCRegistry()`
unguarded (`:83`), and `CopsNCrooksModule.onEnabled` neither reports `npc.citizens.missing` nor skips the managers. The
T-K5 row claims the guard and its import exist. Fix: `if (!NpcSupport.available()) return null;` first statement of
`createCop(Location, int, boolean)` (the 2-arg overload delegates); `onEnabled` reports the fault once and skips
`CopSpawnManager`/`CopManager`; correct the row. **→ T-KR1. Process note in the README: executor claims are verified by
the gate review, and this one was false.**

**B2 — the gadget half of `FuelContract.isFuelSink` was never written; jetpack inventory refuel is dead.** T-R3's guard
(`isFuelItem(cursor) && !isFuelSink(cursor) && isFuelItem(clicked) && isFuelSink(clicked)`) can never be true because the
only implementation, `FuelService`, inherits the `false` default — clicking a jetpack with a gasoline can no longer
refuels it. Green because `FuelRefuelListenerTest` pins only the negative case. Fix: `FuelService` gets a settable
`Predicate<ItemStack> fuelSinkPredicate` (default `s -> false`) and `isFuelSink` delegates to it; `GadgetModuleConfig`
installs it from a `@PostConstruct` resolving `BartizanApi` per call (`wearables().resolveWearable(stack)` non-null and
`JetpackService.isJetpack(wearable)`); a test drives `onInventoryClick` with a stubbed sink predicate through the
positive branch. **→ T-KR2.**

### Major

**M3 — the explosion double-damage suppression is on the wrong handler for a Minecart car.** The ordering assumption
holds (`WeaponEntityDamageEvent` first, `createExplosion` after, same tick), but the vanilla explosion damages a minecart
through `VehicleDamageEvent` (the file's own javadoc says so), not `EntityDamageEvent`; `onVehicleDamage` takes it as an
ordinary punch by the throwing player and applies a second hit, and the 1-tick cleanup drops the entry unread. Fix: in
`onVehicleDamage`, right after `event.setCancelled(true)`: `if (recentWeaponExplosionDamage.remove(entityUUID)) return;`
— correct under both hypotheses. **→ T-KR3; the D-row smoke throws a grenade at a parked car and reads the durability
delta.**

**M4 — every `CitizensAPI` reach in cops is unguarded, and two listeners may fail to register without Citizens.**
Unguarded: `NpcDamageUnprotectListener:91,100,126,135` (generic `EntityDamageEvent`; also an `NPCSpawnEvent` handler at
`:53`), `CopListener:114,117,175`, `WantedLevelListener:23`, `CopNpcFactory:83` (B1). `CopNpc:144` is safe. A handler
whose parameter type is a Citizens class (`NPCSpawnEvent` here, `NPCRightClickEvent` in turf per review RI I-2) makes
Bukkit throw at registration when Citizens is absent. Fix: `if (!NpcSupport.available()) return;` at the top of the
plain-Bukkit handlers; the Citizens-event handlers behind conditional registration (the executor of group M is
investigating what Keystone's `ListenerService`/module loader does with such a class — apply the same mechanism in
cops and turf). **→ T-KR4, coordinated with group M's finding.**

### Minor

**m5** — `startingAmmoMagazines` is a dead config chain (`CopConfigProvider`, `CopSettings`, `YamlCopConfigProvider`,
`CopConfig`, `GanglandCopSettings` → core `Settings.getCopStartingAmmoMagazines()` and its `settings.yml` key) now that
Bartizan owns the NPC magazine. **→ T-KR5 (delete the chain and the key).**
**m6** — `jetpack_glide_descent_rate` remains in Bartizan's shipped `wearables.yml` with no consumer (dead pre-migration
too). **→ Ruling: drop the key from Bartizan's `wearables.yml` and `migration.md` (Bartizan follow-up commit); descent
comes from `GadgetPhysicsConfig`.**

### Verified clean

`CopNpc` method-for-method equivalent to 0.8.4 except the weapon coupling; `isRangedAttacker()` reproduces the old
predicate including the `NONE` case; `SHOULD_SAVE=false`; the state machine, cuff and attacker queue byte-identical.
Hand-off shape = `equip()` → `BartizanNpcWeapons.create` → `setRangedAttack` → `buildItem` into the main hand (cops are not
bare-handed); `DownedTargetFilter` set; `BartizanApi` resolved per call with `isValidWeaponName` first. No firing cadence
survives in cops. `CopSpawnManager` keeps `setDataSupplier` through Keystone's `EntitySpawner`. Every civilian type cops
injects is a civilians bean reachable through `Depends: [civilians]`. `configure` lists the three surviving
configurations. Jetpack reads with the old defaults; `fuel_efficient` via `traitLevel`; the three-level sound walk matches
the shipped YAML and `WearableAddon.sectionToMap` (real maps); all sounds via `SoundEffect`. Descriptors/poms correct; no
test dependency added; `gangland-build` handles all six module jars. Hygiene clean; no cached `BartizanApi` field
anywhere.

## What a cop does in combat now

Spawn → Citizens NPC with `SHOULD_SAVE=false`, `POLICE` mark, `CopNpc` on Keystone's `AbstractNpc`, `DownedTargetFilter`,
`equip()`, then `BartizanNpcWeapons.create` (validated name → Bartizan's `NpcWeaponControllerImpl`, else `NONE`) via
`setRangedAttack`, and the Bartizan item into the main hand. Each tick: `decrementAttackCooldown()` (drives
`NpcRangedAttack.tick()`), navigation, behaviour; `PursuingBehavior` asks `isRangedAttacker()`, and `AbstractNpc.attack`
→ `NpcCombatDelegate.attack` → target filter → `NpcRangedAttack.tryFire` → vanilla bow → melee. Cadence, spread, magazine,
reload and selective fire live in Bartizan. Destroy → `NpcRangedAttack.onDestroy()`.

## What a thrown grenade does to a car now

`ThrowableAction.detonate` fires `WeaponEntityDamageEvent(EXPLOSION, totalDmg)` for every non-living entity in range;
`CarDamageListener.onWeaponEntityDamage` applies `ceil(totalDmg)` once and records the UUID for one tick; the vanilla
explosion that follows reaches the minecart as `VehicleDamageEvent` with the thrower as attacker and applies a second hit
until M3 is fixed; after the fix the car takes the configured value exactly once, and a creeper/TNT blast still falls
through to vanilla damage.

## Not verifiable by the reviewer (no shell)

Whether CraftBukkit routes minecart explosion damage to `VehicleDamageEvent` on the target build (decides whether M3 is a
live double hit or belt-and-braces); classloader identity of `bartizan-api` classes at runtime; whether the Citizens-event
listeners fail to register (D7 smoke); the current test counts.
