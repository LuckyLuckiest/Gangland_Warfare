# Review — WS7 G5 + G5b — 2026-09-17 (Opus, transcribed)
Verdict: FIX (2 Critical, 3 Important, 2 Minor) — both Criticals slipped past because the blind scan checks signatures only and the smoke never spawned a civilian nor published the eligibility bean.

## Spec table (condensed)
G5.1 `CarMeleeWeaponLookup` static, only inside the guard (`CarDamageListener.java:109,124`) ✅ (two statics; `isHoldingWeapon` needed) · G5.2 `CarDamageState` only new bean ✅ · G5.3 `CarWeaponDamageListener` condition + `@AutowireTarget`, no bean ✅ · G5.4 gadget module.yml no `Plugins:`, pom untouched, `Host_Api: 1.1` ✅ · G5.5 `BartizanBlindScan` in the pre-existing gangland-core test-jar (`gangland-core/pom.xml:34-43`, `CitizensBlindScan` precedent, no pom edits) ✅ · B7 guard test ✅ (no red run, I2) · §7 exercising smoke rows ❌ not run/declared (I3) · GD-27 not worsened ✅ · G5b civilians `Plugins:` dropped ✅, `Host_Api` 1.0 ❌ (I1) · G5b site guards ❌ (C1, C2) · `CiviliansBartizanBlindScanTest` ✅ but blind to C1/C2 · cops-n-crooks hard, turf untouched ✅

## Linkage audit
gadget `CarWeaponDamageListener` conditional → safe · gadget `CarMeleeWeaponLookup` static, guarded callers → safe · gadget `JetpackBartizanTraitBridge` static, no Bartizan in signature, sole caller guarded → safe · civilians `GangAllyWeaponImpactListener` conditional → safe (needs Host_Api 1.1) · civilians `CombatEligibilityConfig` `@Bean` returns a Bartizan type → never registered (C1) · civilians `GanglandCombatEligibility` only built by that config; `ReflectionUtil.findClasses:168,189` swallows NCDFE → safe · civilians `BartizanNpcWeapons` always-loaded bean, unguarded body → VIOLATION (C2)

## Findings
**C1 Critical** — `CiviliansModule.java:39-44` `configure()` registers configs by explicit list (`GanglandContext.java:187-190`, no package scan); the new `CombatEligibilityConfig` is never registered → `GanglandCombatEligibility` never published, with or without Bartizan → downed players hittable again with Bartizan installed. Fix: `if (Settings.isBartizanAvailable()) registrar.configuration(CombatEligibilityConfig.class);` + mocked-registrar test (red today).
**C2 Critical** — `BartizanNpcWeapons.java:35,54` `getRegistration(BartizanApi.class)` resolves the class constant before the null check → `NoClassDefFoundError` without Bartizan; called from `CivilianNpcFactory.java:125-131` for every hostile civilian with a weapon pool (shipped `npc/civilians.yml:97-99,158-159,188-189` have `weapon:` entries) → every hostile spawn fails on a Citizens-without-Bartizan server. Fix: first line `if (weaponName == null || !Settings.isBartizanAvailable()) return NpcRangedAttack.NONE;` (`return null;` in `buildItem`) + guard test (red first).
**I1 Important** — civilians `module.yml:6` `Host_Api: 1.0` while using the 1.1 `Settings.isBartizanAvailable` → on a 1.0 host the listener condition resolves false (and after C2, `NoSuchMethodError`). Fix: `Host_Api: 1.1`.
**I2 Important** — no quoted red output for `GadgetBartizanBlindScanTest` and none for `CarMeleeWeaponLookupGuardTest` (report:117-126); civilians red came from an `assertTrue` variant, committed test uses `assertEquals`.
**I3 Important** — exercising smoke rows not run/declared: nothing punched a car, flew a jetpack or spawned a civilian; `car-damage-with-bartizan` not run. Add at least a no-Bartizan row with Citizens present and a forced civilian spawn.
**M1** — `CiviliansModule.java:19-24` stale javadoc (listener gated by `Plugins: [Bartizan]`; `GanglandCombatEligibility` under `CiviliansModuleConfig`).
**M2** — `CarWeaponDamageListener.applyDamage` (~84-110) duplicates `CarDamageListener.applyDamage`.

## Cannot verify
Live jetpack flight / punched car without Bartizan; `GangAllyWeaponImpactListener` gang gate moved into the body (`if (!Settings.isGangEnabled()) return;` ~l.43) — correct, one-condition annotation ✅.

## Notes
- Plan defect: census C2 row 12 / plan §9 "BartizanNpcWeapons degrades gracefully" is wrong.
- Blind-scan scope: cannot see body references; suggested constant-pool allowlist test (src/main classes referencing `org/luckyraven/bartizan` must be one of the audited ones).
- C1/C2 introduced in this unreleased batch — no docket rows if fixed before commit.

## Orchestrator rulings (W25)
Fix C1, C2, I1, I2 (quote reds), M1, M2 (package-private static `CarDamageMath.applyDamage` next to `CarDamageState`). **Add the constant-pool allowlist test** in both gadget and civilians (read each `src/main` `.class` file's constant pool for `org/luckyraven/bartizan/`; the allowed set is the audited list — gadget: `CarWeaponDamageListener`, `CarMeleeWeaponLookup`, `JetpackBartizanTraitBridge`; civilians: `GangAllyWeaponImpactListener`, `CombatEligibilityConfig`, `GanglandCombatEligibility`, `BartizanNpcWeapons`) — cheap, and it turns "every body reference is audited" into a red test. I3: add and run a no-Bartizan row with Citizens present + a forced civilian spawn (console can do it) and a Bartizan-present `car-damage` boot row; anything a console cannot drive (punch, fly) is declared "needs a client" in the report and booked for G6's manual checklist. Census C2 row 12 corrected by the orchestrator. No docket rows for C1/C2 (fixed pre-commit).
