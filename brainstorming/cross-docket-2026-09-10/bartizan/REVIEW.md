# Bartizan (BZ) — review lead notes, 2026-09-10

Repo: `E:\Programming\java\Bartizan` @ `0e18356` (branch master). Two modules: `bartizan-api`, `bartizan-plugin`.
Central folder: `brainstorming/cross-docket-2026-09-10/bartizan/`. Final findings in `findings/<slug>.txt`;
scanner output preserved as `findings/<slug>.raw.txt` for the audit trail.

**Totals: 71 raw → 64 verified.** 3 P0, 17 P1, 24 P2, 20 P3.
(BZ-FA-09 was added by the review lead on a follow-up order, not by a scanner — see the WP-14 line below.)

| system | slug | raw | kept | P0 | P1 | P2 | P3 |
|---|---|---|---|---|---|---|---|
| WM weapon-model | `weapon-model` | 11 | 8 | 0 | 5 | 2 | 1 |
| FA firing-actions | `firing-actions` | 10 | 9 | 0 | 5 | 2 | 2 |
| WE wearables | `wearables` | 5 | 5 | 1 | 0 | 1 | 3 |
| RT raytrace | `raytrace` | 12 | 12 | 2 | 1 | 3 | 6 |
| CF config-parsing | `config-parsing` | 11 | 10 | 0 | 1 | 7 | 2 |
| DB persistence | `persistence` | 5 | 5 | 0 | 1 | 4 | 0 |
| CM commands | `commands` | 4 | 4 | 0 | 1 | 2 | 1 |
| EV events | `events` | 10 | 8 | 0 | 2 | 3 | 3 |
| NU nucleus | `nucleus` | 3 | 3 | 0 | 1 | 0 | 2 |
| — conventions sweep | (folded in) | 2 | 0 | 0 | 0 | 0 | 0 |

## Corrections to `systems.md` (the phase-1 haiku map was wrong in several places)

I rebuilt the package map from `find` before writing any order. Corrections:

- **No `metrics/` package exists.** Listed under NU; does not exist.
- **No `GanglandContext`.** The bootstrap class is `BartizanContext`. (The name in the map was carried over from Gangland.)
- **Only `WeaponRepository` exists.** The map claimed `AmmunitionRepository` and `WearableRepository`; neither exists.
- **`plugin.yml` does NOT declare Citizens** in `depend:` or `softdepend:` (it declares `depend: [Keystone, NBTAPI]`,
  `softdepend: [ViaVersion, PlaceholderAPI]`), contradicting the map's NU row. I made the NU scanner resolve this
  explicitly; it traced `NpcWeaponController` through Keystone's `NpcRangedAttack` and confirmed the NPC package is
  genuinely Citizens-blind (only `org.bukkit.entity.LivingEntity` in the signature chain), so there is no
  `NoDefClassFoundError` boot risk and nothing to file.
- **Several claimed tests do not exist**: no `SelectiveFireTest` in bartizan-plugin (it is in bartizan-api), no
  `WearableEquipListenerTest`, no `CommandInformationTest`, no `InformationManagerTest`. There is **no test at all**
  covering the `command` or `wearable` packages. I told the CM/WE scanners to verify by directory listing first.
- `listener/projectile`, `listener/wearable`, `listener/selective` **do** exist as the map claimed.

## Scope decisions (which scanner, and why)

Every system got exactly one sonnet order; one haiku order ran the project-wide conventions sweep. Max 3 concurrent,
batched: (WM, FA, RT) → CF → EV → CM → (DB, WE, NU) → conventions.

Two deliberate reassignments away from the map, because I place a finding in the system that owns **the fix site**:

- **`api/weapon/modifiers/**` moved WM → RT.** Modifiers execute at raytrace impact; the fix for a modifier bug lands
  in the impact path, not the model.
- **`api/event/**` moved RT → EV.** They are event contracts; EV owns Bukkit event correctness.
- **`command/wearable/*` stayed in CM, not WE.** CM owns give/permission logic across all three item kinds; WE owns
  the wearable service, converters and equip listener.

Every `.java` file under both source roots is covered by exactly one system order.

## Orders given (one line each)

- **WM** (sonnet) — weapon types, DTOs, durability/recoil/spread/reload, `WeaponService`/`WeaponManager`; 13 WP suspects to confirm or refute.
- **FA** (sonnet) — the five action classes, `FullAutoTask`, `AmmunitionManager`, `PluginFireRegistry`; trace one full shot; 15 WP suspects.
- **RT** (sonnet) — raytracer, stepped projectile, damage listener, modifiers, block damage, `CombatEligibility`; 10 WP suspects; async/`getWorld()`/XSeries house rules.
- **CF** (sonnet) — parsers, converters/serializers/refreshers, vocabulary, `file/`, plus all 26 weapon YAMLs; NBT symmetry both directions; 10 WP suspects.
- **EV** (sonnet) — all listeners + the nine api events; `ignoreCancelled`/RIGHT_CLICK_AIR, timer lifecycle, `getHandlerList()` contract; 8 WP suspects.
- **CM** (sonnet) — all 17 command classes + `commands.json`/`plugin.yml`/`message_en.yml`; permission holes vs lock-outs; give-command item creation.
- **DB** (sonnet) — repository, autosave, cleanup, schema, import task; I flagged `deleteAll()` as the highest-value question and told it to determine the real player-facing cost.
- **WE** (sonnet) — wearable catalog/service/equip listener/converters; told it to first determine which event the equip listener actually uses, since `PlayerArmorChangeEvent` is Paper-only.
- **NU** (sonnet) — entry point, bootstrap, wiring, NPC, api surface; told it `combatEligibility()` is already confirmed correct so it would not re-file it, and to settle the Citizens question.
- **conventions** (haiku) — 10 grep-able rules (Paper imports, NMS/gangland symbols in api, cached service lookups, raw logger, raw enums, `getWorld()` chaining, `ignoreCancelled` on interact, async timers, `@ListenerHandler`+`@Bean`, YAML drift), with the sites other scanners had already reported explicitly excluded.

## Findings CULLED, with reason

1. **WM raw #11 — `Weapon.applyPush` dereferences `recoilData` unguarded.** Culled: all five call sites
   (`GunAction:78`, `BiologicalAction:110`, `IncendiaryAction:107`, `MeleeAction:133`, `ThrowableAction:83`) already
   guard with `if (weapon.getRecoilData() != null)`. No trigger today — speculative per the rubric.
2. **WM raw #7 / FA raw #3 — `Consumed_Amount` default 0.** Duplicate kept once as **BZ-CF-03**; the fix is a one-line
   parser default in `GunWeaponParser.java:78`, so CF owns it.
3. **WM raw #8 — `DurabilityCalculator` divide-by-zero.** Duplicate kept once as **BZ-CF-09**; validating
   `Durability.Base >= 1` at parse time fixes both unguarded divisors (`DurabilityCalculator` and `Weapon.buildItem`),
   so CF owns it rather than guarding two consumers.
4. **CF raw #9 — `modifiersData` null.** Duplicate kept once as **BZ-WM-01**; initialising the field in `Weapon.java`
   fixes all 15 unguarded dereference sites at once, which the parser-side fix would not.
5. **FA raw #7 — `Effects_Per_Level` index -1.** Duplicate kept once as **BZ-CF-05**, framed as the parser divergence
   (`ThrowableWeaponParser` validates its equivalent list, `BiologicalWeaponParser` does not).
6. **EV raw #5 — throwable shared UUID.** Merged into **BZ-WM-06** (owns `mintUuid`); EV's stronger concrete
   consequence — one player's throw press-locks every other player holding that type — folded into that observation,
   which I raised P2 → P1 as a result.
7. **EV raw #8 / RT raw #6 — `WeaponKillEntityEvent` never fired.** Duplicate kept once as **BZ-RT-07**, which owns the
   kill path where the event would be fired.
8. **conventions raw #1 — `player.getWorld()` chained in the give commands.** Culled as a false positive:
   `Entity#getWorld()` is `@NotNull` in Bukkit; only `Location#getWorld()` and `Bukkit.getWorld(String)` are nullable.
   The cited `WeaponGiveCommand.java:121` is `items[i] = item;` and contains no `getWorld()` call at all.
9. **conventions raw #2 — `block.getWorld()` in `PluginFireRegistry`.** Same false positive: `Block#getWorld()` is
   `@NotNull`. The genuine nullable-`Location#getWorld()` case is filed correctly as **BZ-RT-06**.

The conventions sweep therefore contributed **zero** findings. That is a real result, not a failure: rules R1–R5 and
R7–R9 have no violations anywhere in the repo — no Paper imports, no NMS or `org.luckyraven.gangland.*` symbols in
`bartizan-api`, no cached `ServicesManager` lookups, no `Bukkit.getLogger()`, no `@ListenerHandler`+`@Bean` collision,
and no `ignoreCancelled = true` on a `PlayerInteractEvent` handler.

## Re-tiering I applied during verification

- **BZ-WM-05 (`getWeapon(String)` mints a row) P0 → P2.** Gangland's WP-6 was P0 because Gangland had read-only
  callers. At the new location the public `WeaponCatalog` exposes only `getWeaponTemplate`, `getWeaponTemplates`,
  `createTransientWeapon`, `validateAndGetWeapon`, `isWeapon` — **none of which mint** — and the only in-repo caller of
  the minting path is `WeaponGiveCommand` with `newInstance=true`, where registration is intended. The api/plugin split
  defused it; the two unused minting overloads remain as a trap.
- **BZ-EV-08 (private `getHandlerList()`) P1 → P3.** The scanner argued Bukkit resolves the handler list with
  `getMethod` so a private declaration breaks registration. That is wrong: `SimplePluginManager` uses
  `getDeclaredMethod("getHandlerList")` followed by `setAccessible(true)`, so registration works fine. The real (minor)
  cost is that a consumer of this `bartizan-api` class cannot call the accessor to unregister.
- **BZ-RT-03 (`CombatEligibility` never checked on the target) P0 → P1.** A protection contract that is never applied
  on the damage path is bounded wrong behaviour, not data loss or a crash.
- **BZ-RT-06 (`BlockDamageManager` world-unload NPE) P1 → P2**, and **BZ-RT-10 (raw `Sound`) P2 → P3** — edge-case
  crash and convention violation respectively, per the rubric's own wording.
- **BZ-CF-08 (`Money_Symbol: ""` aborts boot) filed P2, confidence Medium.** It is a boot-stopping crash, but requires a
  specific misconfiguration (rubric: "edge-case crashes" = P2), and it depends on Keystone's `orDefault` treating an
  explicit empty value as present. Worth an early check if it is ever escalated.

## One finding I reframed rather than accepted

**RT raw #3** claimed `applyBlockBreak` running before the `WeaponRaytraceImpactEvent` cancellation check is a bug.
Reading `handleBlockImpact`'s own javadoc shows that ordering is deliberate ("this never applies damage by itself —
block damage is handled separately"). But tracing it surfaced the real defect underneath, which I filed as
**BZ-RT-01 (P0)**: `BlockDamageManager` removes blocks with a bare `block.setType(Material.AIR)` and **no
`BlockBreakEvent` is ever fired anywhere in the plugin** — a repo-wide grep finds only an import and a listener in
`WeaponInteract`. Five shipped weapons (rifle, shotgun, minigun, steyr_aug, golden_ak47) configure `Break_Blocks`
(rifle ships `GLASS-3`, `ICE-5`, `TERRACOTTA-10`), so out of the box gunfire destroys blocks inside a WorldGuard or
GriefPrevention claim with no plugin able to observe or veto it.

## Hypothesis I checked and cleared (no finding)

`WeaponService.weapons` is a plain `HashMap` and `WeaponManager.initialize()` wires it as the repository data supplier
(`repository.setDataSupplier(() -> getWeapons().values())`), which would be a P0 thread-safety fault if autosave read it
off-thread. It does not: `WeaponAutoSaveTask` extends Keystone's `Timer` and `WiringConfig` starts it with
`start(false)` (main thread). No finding. The DB scanner independently reached the same conclusion.

The DB scanner also verified against the Keystone sibling repo that the Gangland-era `enforceType(MYSQL)` /
`PoolInitializationException` bug (a `catch (SQLException)` that misses the RuntimeException HikariCP actually throws)
is **already fixed** — `DatabaseHandler` now catches `SQLException | RuntimeException`. Not filed.

## Gangland WP docket cross-reference (for orchestrator dedup)

Every confirmed carry-over carries `Was Gangland WP-nn` in its observation. Summary:

- **Confirmed still present at the new location (16):** WP-3, WP-4, WP-5, WP-7, WP-8, WP-9, WP-11, WP-13, WP-14
  (melee/throwable half), WP-15, WP-16, WP-18 (kill-event half only), WP-22, WP-24, WP-26, WP-27, WP-28, WP-29,
  WP-30, WP-31, WP-32, WP-33, WP-35, WP-38, WP-39, WP-41, WP-42.
- **Confirmed FIXED by the split (do not re-file):** WP-1 (radius/damage confusion — now two distinct config values
  with an explanatory comment in `WeaponShooting.java:110-114`), WP-2 (rocket damage now configurable via
  `DamageData.getExplosionDamage()`), WP-19 (AP damage now flows through the unified `living.damage(dmg, shooter)`
  with a damager), and the `WeaponEntityDamageEvent` half of WP-18 (now fired from `ThrowableAction`).
- **Severity reduced by the split:** WP-6 (see re-tiering above).
- **WP-14 biological-ammo-NBT half — verified by the review lead 2026-09-10 (follow-up order): STILL PRESENT**, filed as
  **BZ-FA-09 (P1)**. `BiologicalAction.fire()` decrements via `consumeShot()` at line 102 and then never writes the
  magazine back to the item — the class contains no `updateWeaponData`/`updateWeapon` call at all, while `GunAction`
  (68, 76) and `IncendiaryAction` (97, 104) both do. `Weapon.updateWeaponData` is the only writer of the `AMMO_LEFT`
  tag (`Weapon.java:249`), so on the next interaction `validateAndGetWeapon` → `setWeaponData` runs
  `weapon.setCurrentMagCapacity(amountLeft)` off the stale NBT and refills the magazine. Both halves of WP-14 are
  therefore live at the Bartizan location: the melee/throwable half as BZ-FA-03, the biological half as BZ-FA-09.
- **All three P0s are NEW** — none of them corresponds to a WP entry.

## Open questions for the orchestrator

1. **BZ-RT-01 vs. intended design.** Does Bartizan intend weapon block-breaking to be claim-aware? If yes it needs a
   `BlockBreakEvent` (or the region-plugin API); if the answer is "weapons ignore claims by design", then the five
   shipped `Break_Blocks` configs should be documented as server-owner opt-in and this drops to P2. I filed P0 on the
   assumption that silently bypassing every protection plugin is not intended.
2. **BZ-RT-03 ownership.** `CombatEligibility` is the seam Gangland uses for downed players. Confirming the target-side
   check belongs in Bartizan (rather than Gangland cancelling `WeaponEntityDamageEvent`) needs a decision from whoever
   owns the Bartizan↔Gangland contract — it may be a Gangland-side finding instead.
3. **BZ-DB-04 (30-day full-table wipe).** I tiered this P2 because items self-heal from their own NBT. If any consumer
   is expected to read `weaponManager.getWeapons()` as an authoritative population (Gangland tooling, metrics), it is
   worse than P2 and should be re-tiered.
4. **BZ-CF-08 confidence.** Whether `Money_Symbol: ""` really throws depends on Keystone's `orDefault` semantics for an
   explicitly-empty string. One five-minute check against `NodeReader` would settle Medium → High or cull it.
5. **Bartizan has no test coverage for the `command` or `wearable` packages at all** — including the P0 permission
   bypass (BZ-WE-01) and the unbounded give amount (BZ-CM-01). Worth a coverage task independent of the individual fixes.
6. ~~The biological-ammo-NBT half of WP-14 fell outside every scanner's file set and is unverified.~~
   **CLOSED 2026-09-10** by review-lead follow-up: still present, filed as **BZ-FA-09 (P1)**. Detail in the WP-14 line
   of the cross-reference section above. Note for whoever fixes it: BZ-FA-09 and **BZ-FA-03** (melee/throwable never
   call `consumeShot` at all) are the two halves of the same docket entry and should be scheduled together, and
   `WeaponConsumeShotTest` pins the in-memory decrement for all three types while asserting nothing about the NBT
   write-back — so that test stays green through the bug and must be extended, not merely flipped.
