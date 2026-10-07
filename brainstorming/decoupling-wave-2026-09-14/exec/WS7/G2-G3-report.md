# WS7 G2+G3 report — 2026-09-16

Status: DONE

## What changed

Both gates executed in the Gangland worktree (`E:\Programming\java\wt\gangland-0.9.2`, branch `0.9.2`) by one Sonnet
subagent each, sequentially (G3 depends on G2's classes), each given the exact file list + mirrored Car-pattern
source and verified independently by me (read every touched file, ran the build myself) before proceeding.

### G2 — jetpack domain + `JetpackAddon` + WS7-D4 bridge

1. `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/jetpack/Jetpack.java` (new) —
   domain entity mirroring `car.Car`: identity/fuel/physics fields, `@Nullable Map<String,Object> sounds`, a
   `@Builder.Default boolean bartizanRegistered = false` flag, static `isJetpackItem`/`getJetpackId` NBT checks,
   `getPermission()` → `gangland.jetpacks.<id>`, `buildItem()`/`buildItem(Player)` stamping `JETPACK_ID` + fuel
   tags unconditionally (`Fuel_Key` is mandatory at load, see below) + a raw `"wearable"` tag **only when
   `bartizanRegistered`**.
2. `.../jetpack/JetpackKey.java` (new) — one-constant NBT enum, `JETPACK_ID("jetpack")`.
3. `.../jetpack/config/JetpackAddon.java` (new) — loads `items/jetpacks.yml` via plain `FileConfiguration`/
   `ConfigurationSection` (mirrors `CarAddon`, not Bartizan's newer NodeReader pipeline); folds the `CarManager`-
   equivalent registry role directly into the class (`register`/`getJetpack`/`getJetpacks`/`clear`); **`Fuel_Key`
   mandatory** — missing/blank → `log.warn` + skip (GD-12, fixed for free); `sectionToMap` helper for `Sounds:`;
   lower-cased `Traits:` map; calls the WS7-D4 bridge inside `Settings.isBartizanAvailable() && bartizanTraitsSection
   != null`.
4. `.../jetpack/config/JetpackBartizanTraitBridge.java` (new, WS7-D4) — static-only (no `@Bean`, no field, no
   `@AutowireTarget`), resolves `BartizanApi` fresh off `Bukkit.getServicesManager()`, builds an `external(true)`
   `Wearable`, calls `WearableCatalog.register`. Only call site is inside `JetpackAddon`'s guard above, so this
   class is never linked on a Bartizan-less server.
5. `gangland-features/gangland-gadget/src/main/resources/items/jetpacks.yml` (new) — one `jetpack:` entry
   preserving the old Bartizan `wearables.yml` values verbatim (material, name, lore, fuel/physics numbers,
   `Sounds:`) plus an optional `Bartizan_Traits:` block (`Base_Damage_Reduction: 0.05`, `Traits: {REINFORCED: 1,
   LIGHTWEIGHT: 2}`).
6. `.../config/GadgetFileConfig.java` (edit) — added `jetpackAddon` FILE-phase `@Bean`, identical shape to
   `carAddon`.
7. New tests: `JetpackAddonLoadTest` (5 cases — Fuel_Key/Material/Name skip-tiers + full field round-trip) and
   `JetpackBartizanTraitBridgeGuardTest` (1 case — Bartizan unavailable → `bartizanRegistered` stays false, no
   `wearable` tag stamped), both under `.../jetpack/config/`.

### G3 — item pipeline + `JetpackService`/`JetpackSession`/`JetpackTask` rewrite off Bartizan

1. New `.../item/JetpackConverter.java`, `JetpackItemSerializer.java`, `JetpackItemRefresher.java` — exact mirrors
   of the `Car*` equivalents, using `JetpackAddon`/`Jetpack` instead of `CarManager`(`CarAddon`)/`Car`.
2. `.../item/GadgetItemPredicates.java` (edit) — `+JETPACK` predicate (`JetpackKey.JETPACK_ID` tag check).
3. `.../config/GadgetModuleConfig.java` (edit) — dropped all Bartizan imports; `isJetpackFuelSink` rewritten to
   `GadgetItemPredicates.JETPACK.test(stack)`; `jetpackService` bean gained a `JetpackAddon` parameter; three new
   `@Bean`s (`jetpackConverter`, `jetpackItemSerializer`, `jetpackItemRefresher`) — **serializer and refresher
   registered at priority 20**, above Bartizan's own wearable serializer (0) and refresher (10), so gadget's own
   registrations always win the claim on a Bartizan-tagged jetpack stack (closes the WS7-D4 review's Important-3
   finding on the gadget side; both registries are the same shared `ItemSerializerRegistry`/`ItemRefresherRegistry`
   instances Bartizan's `ItemVocabulary.contribute` folds into via `GanglandContext.installItemVocabularies()`).
4. `.../jetpack/JetpackService.java` — full rewrite: dropped the `wearables()` resolver and all Bartizan imports;
   `activate`/`scheduleChestplateCheck`/`refreshSessions` rewired onto `Jetpack`/`JetpackAddon`/`ItemStack`; deleted
   the static `isJetpack(Wearable)` helper (confirmed dead — only caller was `isJetpackFuelSink`, rewritten above).
5. `.../jetpack/JetpackSession.java` — dropped `Wearable` import; field/accessor renamed
   `jetpackWearable`/`getJetpackWearable`/`setJetpackWearable` → `jetpack`/`getJetpack`/`setJetpack`.
6. `.../jetpack/JetpackTask.java` — full rewrite: dropped `Bukkit`/`RegisteredServiceProvider`/all
   `org.luckyraven.bartizan.*` imports; every `Wearable` → `Jetpack`; **`isScoped()` deleted outright (S1)**, along
   with `run()`'s scoped-weapon-blocks-thrust check; `isWearingJetpack` rewritten onto `Jetpack.getJetpackId`;
   `applyVerticalPhysics` reads `jetpack.getAscendPower()`/`getMaxSpeedY()` directly (typed fields, no more
   `extraTags` map indirection); **`getEffectiveConsumptionRate` collapsed to `return
   jetpack.getFuelConsumptionRate();` (S2 — the whole `fuel_efficient` trait branch deleted)**; `soundTag` reads
   `jetpack.getSounds()`; the now-dead `extraDouble`/`extraInt` helpers deleted.
7. `JetpackNbtIdentityTest` (new, mirrors `CarNbtIdentityTest`) — 6 cases: no-tag false/null, stamped-tag
   round-trip, null/AIR false/null, `getPermission()` from the identity field alone, fuel-tag co-presence,
   `GadgetItemPredicates.CAR`/`JETPACK` mutual exclusion.
8. `JetpackTaskConsumptionRateTest` (existing, **flipped**) — was mocking Bartizan's `Wearable` and asserting
   `fuel_efficient` trait-discount math; now mocks a plain `Jetpack` and asserts `getEffectiveConsumptionRate`
   returns the configured `Fuel_Consumption_Rate` verbatim (the discount mechanic no longer exists, S2).

**Untouched, confirmed in scope for other gates**: `Jetpack.java`/`JetpackKey.java`/`JetpackAddon.java`/
`JetpackBartizanTraitBridge.java` (G2's own files, not re-edited by G3); `listener/car/CarDamageListener.java`
(G5). None of the jetpack listener classes (`JetpackEquipListener`, `JetpackSessionLifecycleListener`,
`JetpackActivateListener`, `JetpackFallDamageListener`, `JetpackKickSuppressor`,
`packet/JetpackInputInterceptor`) referenced the old `Wearable`/`jetpackWearable` API, so none needed edits.

## Deviations from the plan

- **D4 bridge folded into G2** (per the coordinator's batch-2 dispatch, not the original plan text, which predates
  the D4 decision) — `JetpackBartizanTraitBridge` lives in `.../jetpack/config/` beside `JetpackAddon`, its only
  caller.
- G3's `JetpackBartizanTraitBridgeGuardTest` second case (guard stubbed `true`, `BartizanApi` unregistered on the
  services manager) was **not written** — I had specified it in the G2 brief, but the subagent correctly identified
  that it would assert the opposite of `JetpackAddon`'s actual specified behaviour (`bartizanRegistered` is set
  `true` as soon as the guard condition holds, independent of whether the bridge's own `rsp == null` early-return
  fires) — writing it would contradict the addon's own contract rather than confirm it. I agree with this call;
  documented in the test's javadoc.
- G3's `JetpackNbtIdentityTest` red-first evidence is necessarily indirect (see below) since it exercises G2's
  already-correct identity methods, not a bug being fixed.
- Everything else matches the plan/dispatch literally — no other deviations.

## Red-first evidence

**`JetpackAddonLoadTest`** (G2, GD-12 fix — new test, no such guard existed before this gate): red run —
`JetpackAddon` temporarily built without the `Fuel_Key`-mandatory guard, `mvn -pl gangland-features/gangland-gadget
-am test -Dtest=JetpackAddonLoadTest -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 5, Failures: 2` (both
Fuel_Key cases loaded when they shouldn't have). Guard restored → `Tests run: 5, Failures: 0`.

**`JetpackBartizanTraitBridgeGuardTest`** (G2, WS7-D4 guard): red run — `Jetpack.buildItem()`'s wearable-tag stamp
temporarily made unconditional, same test command → `Tests run: 1, Failures: 1` (`expected: <false> but was:
<true>`). Guard restored → `Tests run: 1, Failures: 0`.

**`JetpackTaskConsumptionRateTest`** (G3, flip): the pre-flip file (pulled from git HEAD) run against the
already-rewritten `JetpackTask` → `Tests run: 5, Errors: 5`, `NoSuchMethodException:
JetpackTask.getEffectiveConsumptionRate(Wearable)` (the reflective lookup no longer finds a `Wearable`-typed
overload once the production signature became `(Jetpack)`) — genuine red against the actual final production code,
not a hypothetical. Flipped file restored → green, 1 test.

**`JetpackNbtIdentityTest`** (G3, new): not a flip of wrong behaviour — pins G2's already-correct `Jetpack`
statics; its one genuinely-new assertion (`GadgetItemPredicates.JETPACK` mutual exclusion with `CAR`) could not
have compiled before this gate's `GadgetItemPredicates` edit. Green: 6/6.

## Build

Both subagents ran `mvn clean install -pl gangland-features/gangland-gadget -am` after their own work; I then
independently re-verified from a clean state:

- `mvn clean install -pl gangland-features/gangland-gadget -am` → `BUILD SUCCESS`, `Tests run: 58, Failures: 0,
  Errors: 0, Skipped: 0` (all pre-existing gadget tests + G2's 6 new + G3's 7 new/flipped).
- `mvn clean install` (whole reactor, 22 modules) → `BUILD SUCCESS`.
- `mvn test` (whole reactor) → summed every module's `Tests run:` line: **822 run, 0 failures, 0 errors, 0
  skipped**.
- K1/K2 sanity grep, `grep -rln "org.luckyraven.bartizan" gangland-features/gangland-gadget/src/main/java`, from
  the worktree root → exactly two files: `.../jetpack/config/JetpackBartizanTraitBridge.java` (G2's static-only
  bridge, never a bean/field/`@AutowireTarget`) and `.../listener/car/CarDamageListener.java` (G5, untouched).
  Zero Bartizan symbols remain in `JetpackService`/`JetpackSession`/`JetpackTask`/`GadgetModuleConfig`.

## Docket ids touched

- **GD-12** (jetpack without `Fuel_Key` starts a session that never flies) — **fixed for free** by G2's
  mandatory-`Fuel_Key` guard, per plan §11. `JetpackAddonLoadTest` is new/red-first (GD-12's triage row has no
  pinning test), not a flip.

**Docket candidates**: none new noticed.

## Subagents used

- Sonnet, `general-purpose`, G2: `Jetpack`/`JetpackKey`/`JetpackAddon`/`JetpackBartizanTraitBridge` +
  `items/jetpacks.yml` + `GadgetFileConfig` wiring + 2 new tests. Verified by me (read every file, independent
  build run) before dispatching G3.
- Sonnet, `general-purpose`, G3: item pipeline (`JetpackConverter`/`JetpackItemSerializer`/`JetpackItemRefresher`/
  `GadgetItemPredicates`) + `GadgetModuleConfig`/`JetpackService`/`JetpackSession`/`JetpackTask` rewrite + 1 new
  test + 1 flipped test. Verified by me (read every file, independent build + whole-reactor run + K1/K2 grep).

## Concerns / open questions

None blocking. Two notes for the record:
1. `JetpackBartizanTraitBridgeGuardTest`'s second case was dropped (see Deviations) — the remaining single case
   still proves the guard closes; flagging only so the reviewer sees the reasoning was deliberate, not an omission.
2. Cross-gate: G5 (not yet built) still owns `CarDamageListener.java`/`CarMeleeWeaponLookup`/`CarWeaponDamageListener`
   and dropping `Plugins: [Bartizan]` from `module.yml` — this batch leaves `module.yml`'s `Plugins:` block
   untouched, as scoped.

## Fix round 1 — 2026-09-16

Review `exec/WS7/G2-G3-review.md` (Opus, FIX — 2 Important, 5 Minor) + orchestrator ruling W14 (fix I1, I2, M4, M6,
and M3 in `JetpackAddon` only; M5 = record in report; M7 parked). All six addressed, in the Gangland worktree
(`E:\Programming\java\wt\gangland-0.9.2`), gadget module only.

### What changed

1. **Important 1** — `JetpackBartizanTraitBridge.register(...)` (`.../jetpack/config/JetpackBartizanTraitBridge.java`)
   now returns `boolean` (`false` on its `rsp == null` early return, `true` once it actually registers).
   `JetpackAddon.loadJetpacks` (`.../jetpack/config/JetpackAddon.java`) assigns `bartizanRegistered` from that
   return value (`bartizanRegistered = JetpackBartizanTraitBridge.register(...)`) instead of setting it `true`
   unconditionally once the guard held. Added the previously-dropped case to
   `JetpackBartizanTraitBridgeGuardTest`: `bartizanAvailableButServiceMissing_noFlagNoTag` — guard stubbed `true`
   (`Settings.isBartizanAvailable()`), `ServicesManager.getRegistration(BartizanApi.class)` stubbed to return
   `null` (no service), asserts `bartizanRegistered` stays `false` and `buildItem()` stamps no `wearable` tag.
2. **Important 2** — the existing `JetpackBartizanTraitBridgeGuardTest.bartizanUnavailable_noRegistrationNoTag`
   (already calls `Jetpack.buildItem()`) gained assertions that the built item carries `JETPACK_ID` and
   `FUEL_CURRENT == FUEL_MAX == Max_Fuel` (factory-fresh), not just "no wearable tag". Also added a new
   `JetpackNbtIdentityTest.buildItem_stampsIdentityAndFullFuel` test (the review's exact finding: "`JetpackNbtIdentityTest`
   has no red run") that builds a `Jetpack` directly and asserts the same identity+fuel contract through
   `buildItem()` — giving `JetpackNbtIdentityTest` itself a real red run against `buildItem()`'s stamping, not just
   against manually-constructed NBT fixtures like its other five cases.
3. **Minor 4** — `JetpackAddon.loadJetpacks`: `Max_Fuel` must be `> 0` at load — `<= 0` now warns and skips the
   entry, same tier as the `Fuel_Key` check (`buildItem` must never ship a permanently dry jetpack). New
   `JetpackAddonLoadTest.zeroMaxFuel_skipped` case + a `zero_max_fuel` fixture entry.
4. **Minor 3** (`JetpackAddon` only, per W14 — `CarAddon` untouched) — `XMaterial.matchXMaterial(materialString)
   .orElse(XMaterial.IRON_CHESTPLATE).get()` (dead invalid-material branch, copied from `CarAddon`) rewritten to
   `.map(XMaterial::get).orElse(null)`, so an unrecognised `Material:` string now genuinely warns and skips instead
   of silently defaulting to `IRON_CHESTPLATE`.
5. **Minor 6** — `JetpackTask.updateActionBar`: reverted the `✈`/`⚠`/em-dash literal-glyph swap G3 introduced back
   to the original `\u2708`/`\u26A0`/`\u2014` escape sequences — no other churn to this method.
6. **Minor 5** — GD-20 recorded below (docket section).

### Red-first evidence

All three demonstrated by temporarily reverting the fix via `Edit` (no `git stash`, per house rules), running the
targeted test, confirming failure, then restoring:

- **I1**: reverted `JetpackAddon` to `JetpackBartizanTraitBridge.register(...); bartizanRegistered = true;`
  (guard-only, ignoring the bridge's return). `mvn -pl gangland-features/gangland-gadget -am test
  -Dtest=JetpackBartizanTraitBridgeGuardTest -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 2, Failures: 1`
  — `bartizanAvailableButServiceMissing_noFlagNoTag` failed (`expected: <false> but was: <true>`); the pre-existing
  case stayed green. Restored → `Tests run: 2, Failures: 0`.
- **M4**: temporarily removed the `Max_Fuel <= 0` guard. `mvn ... -Dtest=JetpackAddonLoadTest` →
  `Tests run: 6, Failures: 1` — `zeroMaxFuel_skipped` failed (`expected: <null> but was: <Jetpack@...>`). Restored
  → `Tests run: 6, Failures: 0`.
- **I2**: temporarily disabled `Jetpack.buildItem()`'s `JETPACK_ID` stamp (and zeroed `FUEL_CURRENT`). `mvn ...
  -Dtest=JetpackNbtIdentityTest,JetpackBartizanTraitBridgeGuardTest` → `Tests run: 9, Failures: 2` —
  `JetpackNbtIdentityTest.buildItem_stampsIdentityAndFullFuel` and
  `JetpackBartizanTraitBridgeGuardTest.bartizanUnavailable_noRegistrationNoTag` both failed
  (`expected: <true> but was: <false>` on `Jetpack.isJetpackItem(stack)`). Restored → both green.

M3 and M6 are behavioral/cosmetic fixes the review didn't ask a dedicated test for (M3: no test requirement stated;
M6: a pure literal-vs-escape revert with no behavior change) — neither got a separate red/green cycle.

### Build

- `mvn clean install -pl gangland-features/gangland-gadget -am` → `BUILD SUCCESS`, `Tests run: 61, Failures: 0,
  Errors: 0, Skipped: 0` (58 prior + 3 new: `zeroMaxFuel_skipped`, `bartizanAvailableButServiceMissing_noFlagNoTag`,
  `buildItem_stampsIdentityAndFullFuel`).
- `mvn clean install` (whole reactor, 22 modules) → `BUILD SUCCESS`.
- `mvn test` (whole reactor) → summed every module's `Tests run:` line: **825 run, 0 failures, 0 errors, 0
  skipped**.
- Grepped for leftover `RED-CHECK` markers across the gadget module after restoring every temporary revert — none
  found.

### Docket ids touched

- **GD-20** (Minor 5 — `Car.maxHealth`/`glideDescentRate` are dead config) — the jetpack half is **moot by
  omission**: `items/jetpacks.yml`'s schema (§5 of the plan) never added a `jetpack_glide_descent_rate` knob in the
  first place, so there is nothing dead to carry forward on the jetpack side. The car half (`Car.maxHealth`,
  display-only, never applied to damage capacity) stays open, untouched by this wave — matches the plan's own
  §11 characterisation ("half-moot, half-open").
- **Docket candidate** (from the review's own notes, not filed by me — queued for G6 per prior rulings): silent
  default-material substitution was also present in `CarAddon` (same dead-branch shape as M3) — `CarAddon` itself
  is left untouched per W14, gets its own docket triage row at G6.

### Subagents used

None this round — all fix-round work done directly.

### Concerns / open questions

None blocking. `JetpackBartizanTraitBridgeGuardTest` now has 2 cases (was 1); `JetpackAddonLoadTest` now has 6
(was 5); `JetpackNbtIdentityTest` now has 7 (was 6) — all reflected in the build's `Tests run:` totals above.
