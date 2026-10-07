# Cross-checklist consistency review (R1)

**Reviewer:** R1 (principal engineer), 2026-09-08, session cb80e892.
**Scope:** `architecture/PICK.md` (binding) vs `keystone-1.9.0.md` (P1, 39 tasks), `bartizan.md` (P2, 22 tasks),
`gangland-0.9.0.md` (P3, 16 groups). Sources of evidence: the four documents, `exploration/E1..E3`,
`exploration/research/*.md`, `smoke/scenarios.json`, and — where the documents disagreed — the raw code
(`Keystone/keystone-bean/.../BeanFactory.java:292-293,516-535,652`,
`gangland-impl/.../config/GameplayConfig.java:270-293`).

---

## Verdict

**Ready after patches.** The three checklists are unusually well aligned on coordinates, packages, fault ids,
descriptor matrix and data-folder paths — I found no drift in the Maven versions, the `module.yml` id/`Depends:`/
`Plugins:`/`Host_Api` matrix, the three `plugin.yml` dependency lists, the vocabulary namespace and priorities,
the `commands.json` arithmetic, or the six-jar count. What is **not** ready is the NPC seam: the three streams
each describe a different shape for the same interface, and one cross-plugin service (`CombatEligibility`) has a
publisher named in P2 and no publisher anywhere in P3.

**5 blockers, 6 majors, 7 minors.** Every blocker is a one-paragraph patch; none requires re-planning a group.
No executor may start P2 group D or P3 group H until B1–B5 are applied.

---

## Findings

Severity: **blocker** = an executor following the checklist verbatim writes code that does not compile or
silently loses behaviour. **major** = a group runs out of order, or a stated gate cannot be met.
**minor** = documentation drift that will be resolved by the compiler or a reviewer, but should be fixed now.

### B1 — `NpcWeaponController`'s method names do not match `NpcRangedAttack` — blocker (class 1)

`keystone-1.9.0.md` §Contract (`keystone-npc — org.luckyraven.keystone.npc`, the `npc.spi` block) declares:

```java
public interface NpcRangedAttack { boolean isRanged(); boolean isBusy(); boolean tryFire(LivingEntity target);
                                   void triggerReload(); void refreshHeldItem(); void onDestroy(); }
```

`bartizan.md` §1.6(8) declares `NpcWeaponController` with **`void reload();`** and **`void destroy();`**.
Under ruling (b) below these are the same interface, so the two names must be identical or nothing compiles.

- **File · task:** `bartizan.md` · §1.6(8) (the `NpcWeaponController` code block) and §C.2, and group D task **B6**
  (the `NpcWeaponFactory, NpcWeaponController — §1.6(8)` bullet).
- **Patch — replace the `NpcWeaponController` block in `bartizan.md` §1.6(8) with:**

```java
public interface NpcWeaponFactory {
    NpcWeaponController create(LivingEntity shooter, String weaponName,
                              double fireRateMultiplier, double aimErrorDegrees);
}

/**
 * The single implementation of Keystone's NpcRangedAttack. Bartizan owns NPC firing cadence;
 * consumers never implement this — they obtain one from NpcWeaponFactory and hand it to
 * AbstractNpc.setRangedAttack(...).
 */
public interface NpcWeaponController extends org.luckyraven.keystone.npc.spi.NpcRangedAttack {
    // inherited: isRanged(), isBusy(), tryFire(LivingEntity), triggerReload(),
    //            refreshHeldItem(), onDestroy(), tick()
}
```

  and in the same section replace the sentence
  *"expose `tickCooldown()` on the impl and have P3's `BartizanRangedAttack` call it, **or** let the controller
  schedule its own 1-tick timer; take the first, it preserves today's tick alignment exactly"*
  with:

  > `attackCooldown` becomes an instance field of `NpcWeaponControllerImpl`, decremented in its override of
  > `NpcRangedAttack.tick()`. Keystone's `NpcCombatDelegate.decrementAttackCooldown()` calls
  > `owner.rangedAttack().tick()` on the NPC's existing tick loop (P1 K21 step 9), so today's tick alignment is
  > preserved exactly and Bartizan schedules no timer of its own.

  In **B6**, change `NpcWeaponFactory, NpcWeaponController — §1.6(8)` to
  `NpcWeaponFactory, NpcWeaponController (extends org.luckyraven.keystone.npc.spi.NpcRangedAttack) — §1.6(8)`.

### B2 — `bartizan-api` has no `keystone-npc` dependency — blocker (class 1)

`bartizan.md` group A task **B2** builds `bartizan-api/pom.xml` with "dependencies `keystone-common`,
`keystone-item` (both provided) … **No** `keystone-persistence`, **no** `keystone-bean`, **no** `keystone-command`".
After B1, `bartizan-api` names `org.luckyraven.keystone.npc.spi.NpcRangedAttack` and cannot compile.
`bartizan.md` §C.4 ("Keystone 1.9.0 symbols this stream calls") also omits the whole `keystone-npc` line.

- **File · task:** `bartizan.md` · group A **B2** (the `bartizan-api/pom.xml` bullet) and §C.4.
- **Patch — B2, dependency list:** `dependencies keystone-common`, `keystone-item` **and `keystone-npc`** (all
  three provided), `spigot-api` provided, `XSeries` provided. **No** `keystone-persistence`, **no** `keystone-bean`,
  **no** `keystone-command`, **no** NBT, **no** viaversion. `keystone-npc` brings `citizens-main` transitively at
  `provided`; that is expected and never reaches the jar — the B3 `dependency:tree` check gains
  `no compile-scoped citizens-main`.
- **Patch — §C.4, append a bullet:**

  > - `org.luckyraven.keystone.npc.spi.NpcRangedAttack` — `boolean isRanged(); boolean isBusy();
  >   boolean tryFire(LivingEntity); void triggerReload(); void refreshHeldItem(); void onDestroy();
  >   default void tick();` plus the constant `NpcRangedAttack.NONE`. `bartizan-api`'s `NpcWeaponController`
  >   **extends** it; nothing else in Bartizan touches `keystone-npc`.

### B3 — `NpcRangedAttack` has no tick hook, so the ported cadence never expires — blocker (class 1)

`bartizan.md` §1.6(8) ports `attackCooldown` into the Bartizan controller and makes
`isBusy() == attackCooldown > 0 || reloading`. `keystone-1.9.0.md` K21 step 9 keeps
`decrementAttackCooldown` (`:113`) "verbatim" — it decrements `AbstractNpc`'s own field, which no longer gates
weapon fire. Nothing decrements the Bartizan-side cooldown, so after the first shot `isBusy()` is permanently
true and every gun-armed cop stops firing. This is invisible to the compiler and to every unit test.

- **File · task:** `keystone-1.9.0.md` · §Contract (`npc.spi` block), **K19** (SPI creation), **K21** step 9.
- **Patch — §Contract, `NpcRangedAttack`:**

```java
public interface NpcRangedAttack {
    NpcRangedAttack NONE = …;
    boolean isRanged();
    boolean isBusy();
    boolean tryFire(LivingEntity target);
    void triggerReload();
    void refreshHeldItem();
    void onDestroy();
    /** Called once per NPC tick, from NpcCombatDelegate#decrementAttackCooldown. ★ */
    default void tick() { }
}
```

- **Patch — K21 step 9:** change `keep verbatim: decrementAttackCooldown (:113), …` to

  > 9. keep verbatim: `faceTarget` (`:119`), `faceTargetEntity` (`:130`), `isHoldingVanillaRangedWeapon` (`:160`),
  >    `performVanillaRangedAttack` (`:200`), `performMeleeAttack` (`:222`), `performMeleeAttackOnEntity` (`:241`),
  >    `applyAimError` (`:343`), `applyReactionTimeOnTargetSwitch` (`:354`), `scaleCooldown` (`:364`),
  >    `hasLineOfSight` (`:369`);
  > 10. `decrementAttackCooldown` (`:113-115`) keeps its existing body and gains one line:
  >     `owner.rangedAttack().tick();`. **Why:** the ranged implementation lives in another plugin and owns its
  >     own cooldown; without this call a fired weapon never becomes ready again.

- **Patch — K25 test 6 (`NpcCombatDelegateTest`), append a fifth case:**
  `decrementAttackCooldown_callsRangedAttackTick` — one `tick()` per call, verified with a Mockito `verify`.

### B4 — `CombatEligibility` has a stated consumer and no publisher — blocker (class 1)

`bartizan.md` §C.3 row 4: *"`CombatEligibility` — **Gangland** registers, Bartizan **pulls**"*, and §1.6(5)
rewires six weapon call sites onto it. `gangland-0.9.0.md` lists `api.combat.CombatEligibility` under "Packages
consumed" but has **no task that registers it** — grep of P3 returns only the contract line and the unrelated
`DownedTargetFilter`. Result as written: `getRegistration(CombatEligibility.class)` is always empty, Bartizan
falls back to `DEFAULT = player -> !player.isDead()`, and **downed players become shootable** — a silent
gameplay regression with no test and no log line.

Ruling (a) forbids `bartizan-api` in `gangland-impl`, so the publisher cannot live in the core. It belongs
beside `DownedTargetFilter`, which already wraps the identical registry.

- **File · task:** `gangland-0.9.0.md` · group H **T-H3**, plus §Contract ("From Bartizan 0.1.0").
- **Patch — T-H3, add a fourth bullet and extend the "Do":**

  > Add `npc/combat/GanglandCombatEligibility implements org.luckyraven.bartizan.api.combat.CombatEligibility`
  > (one method, `canBeHit(Player p)` → `!DownedPlayerRegistry.isDowned(p.getUniqueId())` — **note the
  > inversion**), and publish it with `@Bean(publishToServicesManager = true)` in `CiviliansModuleConfig`.
  > Keystone's `BeanFactory` registers a bean under its declared return type on the `ServicesManager` when that
  > flag is set (`BeanFactory.java:531-534`), so the `@Bean` return type must be `CombatEligibility`, not the
  > concrete class.
  > **Done when:** with the civilians module deployed, a downed player cannot be hit by a Bartizan weapon; a
  > red-first `GanglandCombatEligibilityTest` pins both polarities.
  > **Watch out:** with the civilians module absent, Bartizan falls back to `CombatEligibility.DEFAULT` and
  > downed players are hittable. That is the accepted degradation (it matches every other module-gated
  > behaviour) — record it in `documentation/migration-0.9.0.md` and in smoke row **D6**.

- **Patch — §Contract, "From Bartizan 0.1.0", after the `ServicesManager` keys paragraph, add:**

  > `ServicesManager` keys this stream **publishes**: `org.luckyraven.bartizan.api.combat.CombatEligibility`,
  > from `gangland-civilians` (T-H3). This is the one direction reversal in the wave — Bartizan pulls it.

### B5 — `EntityMarkManager` exists twice: moved into civilians *and* promoted to `keystone-npc` — blocker (class 1)

`keystone-1.9.0.md` §1.4 row 13 moves `npc/entity/EntityMarkManager.java` (128 lines) into Keystone as
`NpcMarkManager` (rename + surgery, K23), deleting `isCivilian`, `countsForWanted`, the hardcoded switch and
`processEntityTypes`, and states those become Gangland's `GanglandMarkDefaults`.
`gangland-0.9.0.md` §1.5 instead moves `npc/entity/{EntityMark, EntityMarkManager}` — **both** — into
`org.luckyraven.gangland.civilians.npc.entity`, and says of `EntityMarkManager` *"those types move with it, so
the read stays"*. §1.8's "12 files deleted in favour of `org.luckyraven.keystone.npc.*`" does not list it.

Consequences as written: two implementations of the same PDC key; `GanglandMarkDefaults` (T-H3) has no
constructor to feed; `NpcMarkManager` (P1's Contract, P3's Contract) is never instantiated; K23's whole surgery
and `NpcMarkManagerTest` are dead code.

- **File · task:** `gangland-0.9.0.md` · §0 Volume table, §1.5, §1.8, group H **T-H2**/**T-H3**, group K **T-K1**.
- **Patch — §1.5, the moved-files table:** change the row
  `| npc/entity/{EntityMark, EntityMarkManager} | 2 | org.luckyraven.gangland.civilians.npc.entity |`
  to
  `| npc/entity/EntityMark | 1 | org.luckyraven.gangland.civilians.npc.entity |`
  and change **45 files moved** to **44 files moved** in the same paragraph.
- **Patch — §1.5, replace the `EntityMark`/`EntityMarkManager` paragraph with:**

  > `EntityMark` (17 lines) moves as-is and keeps `countsForWanted()` — Keystone's `NpcMarkManager` is
  > string-typed and must not learn the Gangland wanted concept. **`EntityMarkManager` is deleted**, not moved:
  > `org.luckyraven.keystone.npc.entity.NpcMarkManager` replaces it (same `NamespacedKey(plugin, "entity_mark")`,
  > same `PersistentDataType.STRING`, so live marks survive). Two new civilians-side classes carry the Gangland
  > policy `NpcMarkManager` refuses:
  > - `npc/entity/GanglandMarkDefaults implements NpcMarkDefaults` — the `CiviliansConfig`/`CiviliansLoader`
  >   lists plus the hardcoded switch at `EntityMarkManager.java:116-121`
  >   (`VILLAGER, WANDERING_TRADER, PLAYER → CIVILIAN`, `PILLAGER → POLICE`) and `processEntityTypes(:124)`;
  > - `npc/entity/EntityMarks` — a 3-method static helper holding `of(@Nullable String) → EntityMark`
  >   (**`null` → `EntityMark.UNSET`**, per P1 Q-K4), `isCivilian(Entity, NpcMarkManager)` (`:79`) and
  >   `countsForWanted(Entity, NpcMarkManager)` (`:83`).
  >
  > The 12 main + 1 test consumers of `EntityMarkManager` (`npc-base-inventory.md` §H) inject `NpcMarkManager`
  > and route every `getEntityMark(...)` read through `EntityMarks.of(markManager.getMark(e))`.

- **Patch — §1.8, the deleted list:** append `npc/entity/EntityMarkManager.java (128)` and change
  **"(12 files)"** to **"(13 files)"**; in §0's Volume table change the `12` row to `13` and the `45` row to `44`.
- **Patch — T-K1 "Done when":** `gangland-features/cops-n-crooks/.../npc/` holds no top-level `.java` file,
  `npc/entity/` is empty, and `grep -rn "EntityMarkManager" .` returns nothing.
- **Patch — T-H3, first bullet:** `GanglandMarkDefaults` is constructed from the civilians config and passed to
  `new NpcMarkManager(plugin, defaults)`; give `NpcMarkManager` its own `@Bean` in `CiviliansModuleConfig`.

---

### M1 — P3's "no Bartizan type is named yet" is false for group C — major (class 2)

`gangland-0.9.0.md` §0 "Order dependency on the other two streams" says *"B, C | Nothing beyond A. Pure deletion
— no Bartizan type is named yet."* But **T-C6** imports `org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent`
and adds `bartizan-api` (provided) to `gangland-impl/pom.xml`. An executor trusting the table starts group C
before P2's gate GD and gets an unresolvable dependency.

Ruling (a) removes T-C6 from group C entirely, which fixes this. Apply the patch under **Rulings applied · (a)**,
then change the order table row to:

```
| B, C | Nothing beyond A. Pure deletion — **no Bartizan type is named in either group** (`GangAllyWeaponImpactListener` moved to group H by R1 ruling (a)). |
```

### M2 — no Citizens gate for `gangland-turf`, which owns turf NPCs after group I — major (class 2)

`gangland-0.9.0.md` smoke row S7 expects `npc.citizens.missing` "once per NPC-owning module". After group I,
turf owns `TurfPowerupNpc`, `TurfNpcsModuleConfig` and `turf/turf_npcs.yml` — it is an NPC-owning module. There
are Citizens gates for civilians (T-H4), npcshops (T-J*, §1088) and cops (T-K5), and **none for turf**.
Without it, a Citizens-less server throws inside turf's NPC spawn wiring instead of degrading.

- **File · task:** `gangland-0.9.0.md` · group I, new task **T-I6**.
- **Patch — insert before "Compile gate I":**

  > #### T-I6 — Citizens gate in `TurfModule.onEnabled`
  > - **Do:** in `TurfModule.onEnabled`, report `npc.citizens.missing` through `Diagnostics.active()` and skip
  >   arming the turf-NPC spawn/garrison tasks when `!NpcSupport.available()`. Model it exactly on T-K5.
  > - **Why:** after T-I2 turf owns Citizens-backed NPCs; smoke row **D7** asserts one fault per NPC-owning module.
  > - **Done when:** booting without Citizens logs the fault once from turf and turf capture still works.
  > - **Watch out:** turf capture, contribution and garrison **placement** must keep working without Citizens —
  >   only NPC spawning is skipped. Turf gameplay is not gated on Citizens.

### M3 — `@Bean public ItemVocabularies itemVocabularies(...)` cannot be instantiated — major (class 1)

`keystone-1.9.0.md` §Contract declares `public final class ItemVocabularies` with a single `static void install`
— a utility class. `gangland-0.9.0.md` **T-F2** writes `@Bean public ItemVocabularies itemVocabularies(...)`.
Keystone's `BeanFactory` throws `IllegalStateException("… returned null — bean methods must produce a value.")`
on a null return (`BeanFactory.java:516-521`) and rejects `void` factory methods, so this bean cannot be written
without adding a public constructor to a class PICK defines as static-only.

The cheapest fix uses the pattern already in this repo: `GameplayConfig.initializeInventoryLoader()`
(`:287-293`) is a `@PostConstruct` on a `@Configuration` class, and `BeanFactory.java:292` runs
`runPostConstruct(configInstances)` after **all** beans are registered — a strictly stronger ordering guarantee
than the parameter edges T-F2 relies on. No Keystone change is needed.

- **File · task:** `gangland-0.9.0.md` · group F **T-F2** and **T-F3**.
- **Patch — replace T-F2 and T-F3 with:**

  > #### T-F2 — fold the published vocabularies (`@PostConstruct`, not a `@Bean`)
  > - **Do:** in `gangland-impl/.../config/ItemConfig.java`, add a `GanglandContext context` field if it has none
  >   (copy the shape from `GameplayConfig`), then add as the **last** member of the class:
  >   ```java
  >   /**
  >    * Bartizan (and any other plugin) publishes an {@link ItemVocabulary} on the ServicesManager; the core
  >    * folds them into its own registries. This runs as a @PostConstruct rather than a @Bean because
  >    * ItemVocabularies is a static utility with no instance to register, and because BeanFactory runs
  >    * @PostConstruct after every CONFIG bean exists — a stronger ordering guarantee than parameter edges.
  >    */
  >   @PostConstruct
  >   public void installItemVocabularies() {
  >       List<ItemVocabulary> vocabularies = Bukkit.getServicesManager()
  >               .getRegistrations(ItemVocabulary.class).stream()
  >               .map(RegisteredServiceProvider::getProvider).toList();
  >       ItemVocabularies.install(vocabularies,
  >                                context.get(ItemConverterRegistry.class),
  >                                context.get(ItemSerializerRegistry.class),
  >                                context.get(ItemRefresherRegistry.class));
  >       log.info(vocabularies.isEmpty()
  >               ? "Item vocabularies installed: none — weapon:/ammo:/wearable: item strings will not resolve"
  >               : "Item vocabularies installed: " + vocabularies.stream().map(ItemVocabulary::namespace).toList());
  >   }
  >   ```
  > - **Why:** Bartizan publishes, Gangland pulls (E2 §3c). Keystone forbids static shared registries.
  > - **Done when:** `ItemVocabularies.install` is called exactly once, from a `@PostConstruct`, and
  >   `grep -n "@Bean" ItemConfig.java` shows no `itemVocabularies` bean.
  > - **Watch out:** `getRegistrations` returns `Collection<RegisteredServiceProvider<ItemVocabulary>>` — map to
  >   providers first. `ItemVocabularies.install` takes no Bukkit statics itself, by contract.
  >
  > #### T-F3 — pin the two boot log strings
  > - **Do:** the two strings above are **contract**, not examples. Both smoke rows D1 and D6 grep
  >   `Item vocabularies installed:`; the empty variant must contain the literal substring `none`.
  >   Use Lombok `@CustomLog` + `log.info`, never `Bukkit.getLogger()`.
  > - **Why:** risk 1 — a server that removes Bartizan otherwise loses `weapon:` loot chests **silently**.
  > - **Done when:** booting with no Bartizan prints the `none` line; booting with Bartizan prints `[bartizan]`.
  > - **Watch out:** this line is distinct from Keystone's per-vocabulary line
  >   (`Item vocabulary {} contributed {} converter(s), {} serializer(s), {} refresher(s)`, P1 K6 step 3).
  >   Both ship; only this one is greppable when the collection is empty.

### M4 — gadget must read the jetpack **sounds** out of `Extra_Tags:`, and P3 never says so — major (class 1)

`bartizan.md` Q2 fixes the nested-map `Extra_Tags:` shape *specifically* so jetpack thrust/glide sounds stay in
`items/wearables.yml`, and calls it "the one contract item in this file that P3 can invalidate".
`gangland-0.9.0.md` **T-L2** and §1.9 name only `fuel_efficient` and "the jetpack fuel key" — they never mention
`Sounds.Thrust` / `Sounds.Glide` (today `JetpackTask.java:133,:212,:229,:231`). An executor following T-L2
literally leaves those four reads dangling.

- **File · task:** `gangland-0.9.0.md` · group L **T-L2** and §1.9.
- **Patch — T-L2, extend the "Do" with:**

  > Read **every** jetpack value from `wearable.extraTags()`: the scalars
  > `fuel`, `fuel_current`, `fuel_max`, `jetpack_fuel_consumption_rate`, `jetpack_ascend_power`,
  > `jetpack_glide_descent_rate`, `jetpack_max_speed_y`, and the **nested** `Sounds` map
  > (`Sounds.Thrust.{Default_Sound, Custom_Sound}`, `Sounds.Glide.{…}`) that `JetpackTask.java:133,:212,:229,:231`
  > reads today. `extraTags()` is `Map<String,Object>`; nested maps arrive as `Map<String,Object>` and are **not**
  > stamped as NBT by Bartizan (P2 Q2) — gadget reads them from the `Wearable`, never from item NBT.
  > Play them through Keystone `SoundEffect`, never a raw `Sound` enum (`feedback_sound_via_configuration`).
  > - **Watch out (add):** if `extraTags()` turns out to be scalar-only when group L runs, **stop** — that is a P2
  >   deviation from Q2 and must be reported, not worked around with a new gadget-side config file.

### M5 — P3 smoke row S2 is self-contradictory and the matrix has no Bartizan-present/absent split — major (class 2)

`gangland-0.9.0.md` G6 row S2 reads *"`civilians` | loads; with Bartizan absent, skipped with
`module.plugin.missing`"* — one row asserting two mutually exclusive outcomes. The matrix also never states
which plugins are installed per row, which is the whole point of the new topology, and rows S1/S3/S4/S5/S8 name
no fault ids at all. Replaced wholesale by **§ Smoke rows for phase D** below.

- **File · task:** `gangland-0.9.0.md` · group P **G6**.
- **Patch:** replace G6's table and its preamble with the table in this review's `## Smoke rows for phase D`
  section, verbatim, and append the `smoke/scenarios.json` edit list from that section as a new task **T-O9**
  in group O.

### M6 — `WeaponRaytracer` is listed as a Gangland-consumed service, and after ruling (b) it is not — major (class 1)

`bartizan.md` §C.3 row 3 says `WeaponRaytracer` is *"Consumed by `NpcCombatDelegate`-equivalent in Gangland
`civilians`"*, and `gangland-0.9.0.md` §Contract lists it among "`ServicesManager` keys this stream reads".
Under ruling (b) the raytracer is called only from inside Bartizan's `NpcWeaponControllerImpl.fireSingleRound`;
no Gangland class touches it. Leaving the claim in makes an executor add a lookup that is never used, and makes
`bartizan-api`'s raytrace surface look load-bearing for P3 when it is not.

- **Patch — `bartizan.md` §C.3 row 3, "Consumed by" column:** `Bartizan's own NpcWeaponControllerImpl; published
  as public api for third parties. **No Gangland class consumes it** (R1 ruling (b)).`
- **Patch — `gangland-0.9.0.md` §Contract, "From Bartizan 0.1.0":** delete `**WeaponRaytracer** (same key as
  today)` from the "`ServicesManager` keys this stream reads" sentence, and delete
  `api.raytrace.{WeaponRaytracer, WeaponShooting, RaytraceRequest}` from the "Packages consumed" list.
- **Patch — `bartizan.md` §0 seam 3:** change *"so cops' NPC combat code compiles unchanged"* to
  *"so third-party consumers keep the same key; Gangland no longer calls it"*.

---

### m1 — P2 still sends the generic signs to `sign-api` — minor (class 1)

`bartizan.md` §1.1 (`sign/*.java` row, line ~352): *"P3 ships generic `[ITEM-BUY]`/`[ITEM-SELL]` +
`LegacySignRewriter` in `gangland-ui/sign-api`"*. Ruling (c) / P3 OQ-2 puts them in `gangland-impl`.
- **Patch:** `… P3 widens `gangland-impl/.../sign/type/trade/{BaseTradeSign, BuySign, SellSign}` and registers
  them additionally as `item-buy`/`item-sell`, plus `LegacySignRewriter` in `gangland-impl` (P3 OQ-2). No new
  classes in `sign-api`.`

### m2 — P3 T-K3 uses the pre-rename `AbstractNpc` method names — minor (class 1)

`gangland-0.9.0.md` T-K3 "Watch out": *"`canUseWeapons()` and `isUsingRangedWeapon()` now route through
`NpcRangedAttack.isRanged()`"*. P1 Q-K3 renames them to `canUseRangedAttack()` and `isRangedAttacker()`.
The same stale pair appears in §1.8's "Weapon-shaped behaviour" paragraph.
- **Patch (both places):** `CopNpc.equip()` and `CivilianNpc` override `canUseRangedAttack()` (renamed from
  `canUseWeapons()`, P1 Q-K3); `isUsingRangedWeapon()` is now `AbstractNpc.isRangedAttacker()` and routes
  through `NpcRangedAttack.isRanged()`.

### m3 — P3's Contract omits three names P1 marks ★ — minor (class 1)

Missing from `gangland-0.9.0.md` §Contract "From Keystone 1.9.0": `NpcMetadata.MARK_KEY = "entity_mark"`,
`NpcRangedAttack.NONE` / `NpcTargetFilter.ALL`, and the fault id `item.vocabulary.failed`.
- **Patch — append to the `keystone.npc` paragraph:** `NpcMetadata.MARK_KEY = "entity_mark"` (the PDC key
  `NpcMarkManager` builds against the **consumer's** plugin — do not change it or every marked entity loses its
  mark); safe defaults `NpcRangedAttack.NONE` and `NpcTargetFilter.ALL`; `NpcMarkManager.getMark` is
  `@Nullable String` and Gangland maps `null → EntityMark.UNSET` in `EntityMarks.of` (P1 Q-K4).
- **Patch — append to the `item.spi` paragraph:** fault id **`item.vocabulary.failed`** (raised by
  `ItemVocabularies.install` when a vocabulary's `contribute` throws; the remaining vocabularies still fold).

### m4 — P2's `~/.m2` handoff note points at a stale group number — minor (class 2)

`bartizan.md` **B14** (group I): *"**Announce this to the orchestrator — P3's group 9 unblocks here.**"*
"group 9" is PICK's *build-order step* 9, not a P3 group; P3 uses letters. And `bartizan-api` is already
installed at gate **GC** (after B5) and frozen at **GD** (after B7), so P3 group H unblocks at GD, not B14.
- **Patch — B14 "Done when":** `… and `org.luckyraven.bartizan:bartizan-api:0.1.0` is re-installed in `~/.m2`.
  (P3's group **H** unblocked earlier, at gate **GD**; this task only refreshes the artifact.)`
- **Patch — gate GD line:** already says *"P3 can start compiling against `bartizan-api` from this point."* —
  keep, and add: **notify the orchestrator; this is the P2→P3 handoff.**

### m5 — the T-16 backup line and the `Disabled module` line are unowned in phase D — minor (class 2)

`keystone-1.9.0.md` introduces two new console strings the harness must see —
`Backup skipped for '<schema>': …` (K-GE2) and `Disabled module <id> <version>` (K29b) — and its §Contract calls
them "the console contract the smoke harness greps for". Neither appears in P3's G6, and `smoke/scenarios.json`
asserts only `Disabling`.
- **Patch:** covered by the "Every row" line in `## Smoke rows for phase D`; add it to G6 verbatim.

### m6 — README topology is stale on both count and `Depends:` — minor (class 1)

`README.md` "Target topology" lists **five** module jars and gives cops `Depends: turf` only. The correct state
is six jars, `turf → Depends: [civilians]`, `copsncrooks → Depends: [turf, civilians]`. P3 R-4 flags the count;
the `Depends:` drift is unflagged.
- **Patch:** `gangland-0.9.0.md` T-O2 already owns the diagram fix — extend its "Do" to *"correct both the jar
  count (six) **and** every `Depends:`/`Plugins:` line in the topology block"*.

### m7 — Bartizan's bStats id `0` is the only user-blocking item and is not on the board — minor (class 2)

`bartizan.md` Q3 defaults to `PLUGIN_ID = 0` with a TODO and calls it *"the only item in the stream that
genuinely needs the user"*. It appears in no board list.
- **Patch:** orchestrator adds one line to `README.md`'s in-game/user-check list: *"register Bartizan on
  bstats.org and give the executor the plugin id (P2 Q3; ships as `0` until then)."* No checklist change.

---

## Rulings applied

These are **binding**. Planners copy them into their checklists as written; executors do not re-litigate them.

### (a) `GangAllyWeaponImpactListener` moves into `gangland-civilians`; the core never sees `bartizan-api`

PICK's contract sentence wins over PICK's Gangland paragraph. `gangland-impl`, `gangland-core`,
`gangland-infra/**` and `gangland-ui/**` take **no** `bartizan-api` dependency of any scope, and name no
`org.luckyraven.bartizan.*` type. The listener becomes an ordinary module listener — `@ListenerHandler`,
auto-scanned, no manual registration, no `isPluginEnabled` guard — because the module that carries it already
declares `Plugins: [Bartizan]` and is skipped wholesale with `module.plugin.missing` when Bartizan is absent.
That is strictly safer than a hand-registered guard in the core, and it is the mechanism the wave already built.

**Patch — `gangland-0.9.0.md`:**
1. **Delete T-C6 entirely** from group C. Renumber nothing; leave a stub line
   `#### T-C6 — (moved to group H as T-H5 by R1 ruling (a))` so status-table ids stay stable.
2. **Add T-H5** to group H:
   > #### T-H5 — `GangAllyWeaponImpactListener`
   > - **Do:** move the deleted weapon module's `GangAllyWeaponImpactListener` (48 lines) to
   >   `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians/listener/gang/GangAllyWeaponImpactListener.java`,
   >   package `org.luckyraven.gangland.civilians.listener.gang`. **Keep `@ListenerHandler`** — it is scanned
   >   like every other module listener via `CiviliansModule.LISTENER_PACKAGE`. Import
   >   `org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent`; inject `GangManager`/`MemberManager` from
   >   `gangland-impl` exactly as the weapon-module original did.
   > - **Why:** ally friendly-fire is gang policy, but it only fires when a Bartizan weapon does. The civilians
   >   module already carries `Plugins: [Bartizan]`, so the module loader gates the class for free — no manual
   >   registration, no lazy-class-loading trick, no T-14 repeat.
   > - **Done when:** the file exists in civilians with `@ListenerHandler` intact and
   >   `grep -rn "GangAllyWeaponImpactListener" gangland-impl gangland-core` is empty.
   > - **Watch out:** it needs `gangland-impl` at `provided` (the module already has it) — it does **not** justify
   >   putting `bartizan-api` anywhere in the core.
3. **Add to group P gate G4** as the new first grep, above the `gangland.weapon` line:
   ```
   grep -rn "org\.luckyraven\.bartizan" gangland-impl/ gangland-core/ gangland-infra/ gangland-ui/   # must be EMPTY
   grep -rn "bartizan-api" gangland-impl/pom.xml gangland-core/pom.xml gangland-infra/*/pom.xml gangland-ui/*/pom.xml   # must be EMPTY
   ```
   > **This pair is the headline architectural gate of the stream, ahead of the `gangland.weapon` grep.**
4. **Rewrite OQ-3** to: *"**Settled by R1 ruling (a):** the literal reading wins. The listener lives in
   `gangland-civilians` (T-H5); the core takes no `bartizan-api` dependency; gate G4's first grep enforces it."*
5. **§0 risk 3** — replace with: *"**The core naming a Bartizan type — closed.** R1 ruling (a) moved
   `GangAllyWeaponImpactListener` into `gangland-civilians`; gate G4's first grep proves the core is
   Bartizan-free."*

### (b) Bartizan's `NpcWeaponController` is the sole `NpcRangedAttack` implementation

`org.luckyraven.bartizan.api.npc.NpcWeaponController extends org.luckyraven.keystone.npc.spi.NpcRangedAttack`
and adds no methods of its own. Gangland implements `NpcRangedAttack` **nowhere**. `gangland-civilians` keeps
one small factory hook, `BartizanNpcWeapons`, that resolves `BartizanApi` from the `ServicesManager` **at spawn
time** and returns `NpcRangedAttack.NONE` when Bartizan is absent or the weapon name is unknown.

**Patch — `keystone-1.9.0.md`:** apply B3 (the `tick()` default + the `decrementAttackCooldown` call).
Add to §Contract, under the `npc.spi` block: *"`NpcRangedAttack`'s only production implementation is Bartizan's
`NpcWeaponController`. Nothing in Keystone or Gangland implements it; Gangland only hands one to
`AbstractNpc.setRangedAttack(...)`."*

**Patch — `bartizan.md`:** apply B1 and B2.

**Patch — `gangland-0.9.0.md`:**
- **§0 "Introduces (Gangland side)":** replace `BartizanRangedAttack implements NpcRangedAttack` with
  `BartizanNpcWeapons` (a factory hook, **not** an `NpcRangedAttack` implementation).
- **§1.5 "New files":** same replacement.
- **§1.8**, the "Weapon-shaped behaviour" paragraph: replace *"reached through `NpcRangedAttack` (implemented
  once, in the civilians module as `BartizanRangedAttack`)"* with *"reached through `NpcRangedAttack`, whose sole
  implementation is Bartizan's `NpcWeaponController` (R1 ruling (b))"*.
- **T-H3, first bullet — replace with:**
  > `npc/combat/BartizanNpcWeapons` — a factory hook, **not** an `NpcRangedAttack` implementation:
  > ```java
  > public NpcRangedAttack create(LivingEntity shooter, @Nullable String weaponName, NpcDifficulty difficulty) {
  >     if (weaponName == null) return NpcRangedAttack.NONE;
  >     RegisteredServiceProvider<BartizanApi> rsp =
  >             Bukkit.getServicesManager().getRegistration(BartizanApi.class);
  >     if (rsp == null) return NpcRangedAttack.NONE;
  >     return rsp.getProvider().npcWeapons()
  >               .create(shooter, weaponName, difficulty.getFireRateMultiplier(), difficulty.getAimError());
  > }
  > ```
  > Called from `CopNpcFactory` / `CivilianNpcFactory` at spawn, result handed to
  > `AbstractNpc.setRangedAttack(...)`. **Resolve `BartizanApi` on every call — never cache the provider in a
  > field set at bean construction** (P2 R8: Bartizan may enable after Gangland).

### (c) OQ-1, OQ-2 and OQ-4 stand as the planner's defaults

- **OQ-1** — `TurfFriendlyFireListener` stays in `cops-n-crooks` (which already has `Depends: [turf]` and
  `Plugins: [Bartizan]`), imports re-pointed to `org.luckyraven.gangland.turf.npc.*`. **Turf never declares
  `Plugins:`** — turf capture must work on a weapons-free server. Confirmed consistent with PICK's module matrix
  and with P3 T-I2 and gate G5 as written; no patch.
- **OQ-2** — the generic signs are the **existing** `gangland-impl/.../sign/type/trade/{BaseTradeSign, BuySign,
  SellSign}`, widened and registered *additionally* under `item-buy` / `item-sell`. No new classes anywhere, and
  **no new dependency on `gangland-ui/sign-api`**. `LegacySignRewriter` (T-G4) and the `settings.yml`
  `Signs.Legacy_Aliases` block (six keys, values lowercase because they are lookup ids) **survive verbatim** —
  they are the only thing keeping placed `[…-WEAPON-BUY]` signs alive. Patch P2 per **m1**; P3 needs no change.
- **OQ-4** — `NbtTagCatalog`, `ItemConfig.nbtTagCatalog()` and `HolderSeamBeanTypeTest.java:47` **stay**. Exactly
  **four** seams are deleted: `MetricsContributor`, `DataCleanupTask`, `ShopDisplayNameProvider`,
  `DeathMessageContributor`. PICK's "five silent seams" sentence is wrong and is superseded here.
  **Patch — `architecture/PICK.md` is binding and must not be silently contradicted:** the orchestrator appends
  one line to PICK's Gangland paragraph — *"**Amended by R1 (2026-09-08):** four seams, not five —
  `NbtTagCatalog` stays (P3 OQ-4)."* No other PICK edit is authorised by this review.

### (d) P2's three defaults are confirmed, with one P3 patch

- **`Extra_Tags:` nested maps, top-level scalars only stamped as NBT** — confirmed. P3 must consume the nested
  `Sounds` map; see **M4**. `fuel` / `fuel_current` / `fuel_max` reproduce today's three NBT tags verbatim, so
  Gangland's `FuelService` in `gangland-infra/gangland-item` stays untouched — consistent across P2 §1.6(6) and
  P3 §1.9/T-L2.
- **ViaVersion protocol-1.13 recoil gate dropped** — confirmed. Above the 1.16 server floor the gate is dead
  weight. P1 states Keystone applies no protocol gate; P2 Q1 drops it caller-side; P3 keeps `ViaVersion` in
  `softdepend`. All three consistent, no patch. `viaversion-api` stays `provided` on Bartizan's test classpath
  with the T-09 comment.
- **`aimErrorDegrees` accepted, stored, no effect on the Bartizan gun path** — confirmed. P1 K21 keeps
  `applyAimError` on the Keystone side for the vanilla bow/crossbow path; P2 §1.6(8) javadocs the parameter as
  reserved; P3 T-H3 passes `difficulty.getAimError()`. Consistent, no patch. **Do not invent a use for it.**

### (e) P1's six defaults are confirmed; P2/P3 must use these names

`relativeCameraRotation` is a **`default`** method on `PacketAdapter` (Q-K1) · `keystone-npc` is excluded from
the Central `release` profile beside `keystone-plugin` over the Citizens SNAPSHOT (Q-K2) · `canUseWeapons()` is
**renamed** to `canUseRangedAttack()`, not deleted (Q-K3) · `NpcMarkManager.getMark` returns **`@Nullable
String`** with no `UNSET` sentinel (Q-K4) · the **`-yaw + 1` / `pitch - 1` transform is caller-side** (Q-K5) ·
the fault id for a throwing vocabulary is **`item.vocabulary.failed`** (Q-K6).

Cross-check result:
- P2 §1.6(1) applies the transform on the Bartizan side —
  `PacketBridge.adapter().relativeCameraRotation(player, -yaw + 1, pitch - 1)` — and pins it with the rewritten
  `RecoilManagerTest`. **Correct as written, no patch.** This is the single highest-consequence line in the wave:
  folding it into Keystone inverts every weapon's recoil.
- P2 §1.6(2) installs `ReflectivePacketAdapter` via `PacketBridge.install(...)` in `KernelConfig` and resets in
  `onDisable` — matches Q-K1's default method + override design. No patch.
- P3 uses `canUseRangedAttack()`/`isRangedAttacker()` **except** in T-K3 and §1.8 — patched by **m2**.
- P3 never states the `null → EntityMark.UNSET` mapping — patched by **B5** (`EntityMarks.of`).
- Neither P2 nor P3 names `item.vocabulary.failed` — P3 patched by **m3**; P2 needs none (it never handles the
  fault, it only must not throw out of `contribute`).

### (f) The two vocabulary log lines are distinct, and only the Gangland one is a smoke contract

- Keystone (`ItemVocabularies.install`, P1 K6 step 3), one line **per folded namespace**:
  `Item vocabulary {} contributed {} converter(s), {} serializer(s), {} refresher(s)`. Prints nothing when the
  collection is empty — therefore **not** greppable for the absence case, which is the case that matters.
- Gangland (`ItemConfig`, P3 T-F2/T-F3), one line **always**:
  `Item vocabularies installed: [bartizan]` / `Item vocabularies installed: none — weapon:/ammo:/wearable: item
  strings will not resolve`.

Smoke greps the **Gangland** line only, substring `Item vocabularies installed:`, with the extra assertion
`none` on the Bartizan-absent row. Both strings are now pinned in the patches under **M3**; no P1 change needed.

---

## Execution order

Two `~/.m2` handoffs and one jar handoff. Nothing else couples the repos.

```
 ①  P1 · Keystone  groups A → B → C → D → E → E2 → F
     gates K-GA, K-GB, K-GC, K-GD, K-GE, K-GE2
     ├─ K35 = K-G3  `mvn clean install`  →  keystone-*:1.9.0 + keystone-npc:1.9.0 in ~/.m2
     ├─ K36 = K-G4  Gangland 0.8.4 regression (bump <keystone.version> to 1.9.0, mvn clean test,
     │              identical Tests run/Failures/Errors, revert the line, git diff --stat empty)  ← BLOCKING
     └─ K37 = K-G5  Oriel ./gradlew build at keystone = "1.9.0", toml reverted            ← NON-BLOCKING

        ══ HANDOFF 1 (~/.m2): keystone-* 1.9.0 ══  P2 and P3 both unblock here.
        K-G4 must be GREEN before either starts. A red K-G4 is a P1 defect; do not patch Gangland.

 ②  P2 · Bartizan  A → B → C            gate GC: bartizan-api installed to ~/.m2
                   D → E                gate GD: api FROZEN
        ══ HANDOFF 2 (~/.m2): bartizan-api 0.1.0, frozen ══  P3 groups H/K/L unblock here.

 ③  ── from here P2 and P3 run in parallel, different repos, no shared artifact ──

     P2 · Bartizan  F → G → H → I (B14 refreshes bartizan-api) → J → K
                    B20 (cadence test, RED) → B17 → M → N → O → P (G1–G5)
                    →  Bartizan-0.1.0.jar

     P3 · Gangland  A  (needs P1 only; gate A must be green — a failure is a P1 defect)
                    B  (point of no return: gangland-weapon + gangland-compatibility deleted.
                        PRECONDITION: P2 has copied all 144 weapon files AND P1's shape resolver is green)
                    C  (pure deletion after ruling (a) removed T-C6)
                    D → E → F → G                       (P1 only)
                    I → J                               (P1 only — turf NPCs, npc-shops)
                    H → K → L                           (needs HANDOFF 2)
                    M → N → O → P (G1–G6)
                    →  gangland_warfare-0.9.0.jar + six module jars

 ④  ══ HANDOFF 3 (jars → test server) ══
     Keystone-1.9.0.jar + Bartizan-0.1.0.jar + gangland_warfare-0.9.0.jar + target/modules/*.jar
     →  phase D smoke, rows D1–D9 below.
```

Notes the planners must carry:
- **P3 group B is irreversible in the working tree.** Its stated precondition (P1's `ReflectivePacketAdapter`
  green, P2 has copied the 20 `Recoil_1_xx_Ry` reference adapters) is correct and must be confirmed by the
  orchestrator, not by the executor.
- **P3 groups I and J need only P1** and can run before HANDOFF 2 — use them to fill the wait if P2 is behind.
- **P2 B20 (`NpcWeaponCadenceTest`) must be seen RED before B17.** With B3 applied it also covers `tick()`.
- **P3 T-G1 and T-G4's `LegacySignRewriterTest` are red-first**; so are P1's K25 suites and K29b's disable-log
  tests. Every one of them pins behaviour that crosses a repo boundary with zero coverage today.

---

## Smoke rows for phase D

Replaces `gangland-0.9.0.md` G6 wholesale. Every row addresses modules by **id**, never by jar name. `plugins/`
always contains Keystone 1.9.0 + Gangland 0.9.0; the third column says what else.

| Row | Also in `plugins/` | `modules/` (ids) | Expect |
|---|---|---|---|
| **D0** | Bartizan, **no Gangland** | — | Bartizan boots standalone; `/bartizan` and `/weapon list` answer; `plugins/Bartizan/{settings.yml, message/message_en.yml, weapon/*.yml (22), items/ammunition.yml, items/wearables.yml}` generated; `plugins/Bartizan/database/bartizan.db` created; `.weapon-import-done` written; 0 ERRORs |
| **D1** | Bartizan, Citizens | *(empty)* | `Runtime modules: 0 loaded, 0 fault(s)`; `Item vocabularies installed: [bartizan]`; `/glw help` lists only core commands |
| **D2** | Bartizan, Citizens | `civilians` | `Loaded module civilians 0.9.0`; 0 faults; civilian spawner command answers; `Item vocabularies installed: [bartizan]` |
| **D3** | Bartizan, Citizens | `turf` | turf **skipped**, fault **`module.dependency.missing`** (turf `Depends: [civilians]`); `Runtime modules: 0 loaded, 1 fault(s)`; server boots |
| **D4** | Bartizan, Citizens | `civilians`, `turf`, `copsncrooks` | 3 loaded, 0 faults; `/glw cops` and `/glw turf` answer; `/glw turf powerupnpc` resolves **without** a `CommandContribution` |
| **D5** | Bartizan, Citizens | all six (`mail`, `turf`, `civilians`, `copsncrooks`, `gadget`, `npcshops`) | `Runtime modules: 6 loaded, 0 fault(s)`; `/glw help` count matches; `Item vocabularies installed: [bartizan]` |
| **D6** | **Bartizan REMOVED**, Citizens | all six | `civilians`, `copsncrooks`, `gadget` skipped with **`module.plugin.missing`**; `mail`, `turf`, `npcshops` load → `3 loaded, 3 fault(s)`; `Item vocabularies installed: none`; `/glw turf` still answers; server boots, 0 ERRORs |
| **D7** | Bartizan, **Citizens REMOVED** | all six | boots; **`npc.citizens.missing`** reported **exactly once each** by `civilians`, `copsncrooks`, `turf`, `npcshops` (4 faults, 6 loaded); no NPC spawns; no `NoClassDefFoundError`; turf capture still answers |
| **D8** | Bartizan, Citizens | all six + `/glw reload` | every module still answers its probe command; no duplicate-listener errors; no second `Item vocabularies installed:` line |
| **D9** | Bartizan, Citizens | all six + a copy of `mail` with `Host_Api: 0.8` | the copy skipped with **`module.host.incompatible`**; the other six load |
| **Every row** | — | — | on `stop`: `Disabled module <id> <version>` **once per loaded module, reverse load order**; `Backup skipped for 'gangland': …` and **no** `Failed to create a backup` (T-16); no classloader errors; no fault spam |

Fault ids asserted by this matrix: `module.dependency.missing` (D3), `module.plugin.missing` (D6),
`npc.citizens.missing` (D7), `module.host.incompatible` (D9). `nms.recoil.unsupported` and
`item.vocabulary.failed` must **not** appear in any row — if either does, it is a P1 defect.

**`smoke/scenarios.json` edits (new P3 task T-O9, or the orchestrator before phase D):**
1. `module_jar_prefix` — drop `"weapon"`; add `"civilians": "gangland-civilians"`,
   `"npcshops": "gangland-npc-shops"`. Keep `mail`, `cops` → `cops-n-crooks`, `gadget`, `turf`.
2. `module_probe_commands` — drop `"weapon"`; add a `civilians` and an `npcshops` probe taken from the moved
   `commands.json` keys (the executor picks; it must be a command that answers without an NPC in the world).
3. New per-scenario key `"plugins"` listing the extra jars to stage (`["Bartizan"]`, `[]`, `["Bartizan"]` minus
   Citizens…), plus a `"remove_plugins"` list, so D6 and D7 are expressible without hand-editing the server.
4. Add `"Item vocabularies installed:"` to `must_contain` on every row, and `"none"` additionally on D6/D1.
5. Add `"Disabled module "` to `must_contain` on every row that loads a module, and
   `"Failed to create a backup"` to a new `must_not_contain` list on every row.

In-game checks that stay the user's (already on the board): recoil feel on 1.21.11 and on one older client
through ViaBackwards; cop fire rhythm after the cadence port; a placed legacy `[…-WEAPON-BUY]` sign still
trading after the alias rewrite; a `weapon:` loot chest still rolling a weapon.
