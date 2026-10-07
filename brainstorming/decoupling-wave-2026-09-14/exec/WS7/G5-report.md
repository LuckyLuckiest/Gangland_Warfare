# WS7 G5 + G5b report — 2026-09-17

Status: DONE

## What changed

Executed as two sequential Sonnet subagents in the Gangland worktree (`E:\Programming\java\wt\gangland-0.9.2`,
branch `0.9.2`), each verified by me directly (read every touched file myself, ran the standalone-module and
whole-reactor builds myself, re-ran the `grep -rln "org.luckyraven.bartizan"` sanity check myself) before moving on.

### G5 — gadget: `CarMeleeWeaponLookup` extraction, soft Bartizan

- `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/listener/car/CarMeleeWeaponLookup.java`
  (new) — static-only helper (no `@Bean`/field/ctor-param/`@AutowireTarget`), `isHoldingWeapon`/`resolveMeleeDamage`,
  called only inside `Settings.isBartizanAvailable()` branches.
- `.../listener/car/CarDamageState.java` (new) — the only new `@Bean` this gate adds; centralizes
  `pendingRightClickInteract`/`recentWeaponExplosionDamage` read/consume across both car damage listeners (GD-27
  mitigation, see Docket below), zero Bartizan symbols.
- `.../listener/car/CarWeaponDamageListener.java` (new) — `@ListenerHandler(condition = "isBartizanAvailable")` +
  `@AutowireTarget({CarService.class, CarDamageState.class})`, no `@Bean` method, auto-scanned; holds
  `onWeaponEntityDamage`/`onWeaponRaytraceImpact` (the two handlers whose parameter types are Bartizan events) moved
  verbatim out of `CarDamageListener`, plus a deliberately duplicated private `applyDamage` (plan's 4-file budget —
  ponytail: a 5th shared-helper class was rejected as unnecessary for one small method).
- `.../listener/car/CarDamageListener.java` (edited) — the always-loaded listener; dropped all 6 Bartizan imports +
  `RegisteredServiceProvider`, gained `CarDamageState` as a 4th `@AutowireTarget` entry, `onVehicleDamage` now reads
  `Settings.isBartizanAvailable() ? CarMeleeWeaponLookup.resolveMeleeDamage(...) : fallback`.
- `.../src/main/resources/module.yml` (edited) — dropped `Plugins: [Bartizan]`; `Host_Api: 1.1` and `bartizan-api`
  `provided` compile dependency both kept.
- `gangland-core/src/test/java/org/luckyraven/gangland/core/testsupport/BartizanBlindScan.java` (new, shared) —
  mirrors `CitizensBlindScan` exactly (same `URLClassLoader`-by-name-refusal design, same
  `getDeclaredMethods()`/`getMethods()`/`getDeclaredConstructors()` reflection sites); placed in `gangland-core`'s
  test-jar specifically so G5b could reuse it with zero pom changes (both `gangland-gadget` and `gangland-civilians`
  already declare `gangland-core` as a `test-jar` dependency, confirmed by direct pom read). Public
  `bartizanBlindClassLoader()` (unlike `CitizensBlindScan`'s private one) so a consumer test can force-load one
  specific unscanned class.
- `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/GadgetBartizanBlindScanTest.java`
  (new) — standard scan (`findUnsafeClasses` empty) + a second test force-loading `JetpackBartizanTraitBridge` (via
  `getDeclaredMethods()`) and `CarMeleeWeaponLookup` (via `getMethods()` — deliberate deviation, see Deviations)
  through the blind loader, proving neither is ever linked unless its guarded call site executes.
- `.../test/.../listener/car/CarMeleeWeaponLookupGuardTest.java` (new, B7) — stubs
  `Bukkit.getServer().getPluginManager().isPluginEnabled("Bartizan")` false, drives the real
  `CarDamageListener.onVehicleDamage` against a real `VehicleSession`, asserts the applied damage equals exactly the
  vanilla fallback (`Math.max(1, (int) Math.ceil(event.getDamage()))`) — the damage VALUE observable, not
  "non-invocation" of a mock.

### G5b — civilians: soft Bartizan (user decision WS7-D1 = C)

- `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians/CombatEligibilityConfig.java`
  (new) — isolated `@Configuration` class holding only the `combatEligibility()`
  `@Bean(publishToServicesManager = true)` bean extracted out of `CiviliansModuleConfig` (return type
  `org.luckyraven.bartizan.api.combat.CombatEligibility`, the wave's one Gangland-publishes-to-Bartizan direction
  reversal). Confirmed via direct read of Keystone's `BeanFactory.registerConfiguration` (lines 171-260) that
  `ReflectionGuard.orSkip(configClass, ..., configClass::getDeclaredMethods)` is applied **once per `@Configuration`
  class** — isolating this one Bartizan-typed bean means only it is lost when Bartizan is absent, not
  `CiviliansModuleConfig`'s other 8 unrelated beans (`civiliansLoader`, `npcMarkManager`, `bartizanNpcWeapons`,
  `civilianService`, etc.).
- `.../CiviliansModuleConfig.java` (edited) — `combatEligibility()` and its two now-unused Bartizan/
  `GanglandCombatEligibility` imports removed; class javadoc points at the sibling `CombatEligibilityConfig`.
- `.../listener/gang/GangAllyWeaponImpactListener.java` (edited) — `@ListenerHandler(condition = "isGangEnabled")`
  → `condition = "isBartizanAvailable"` (the handler's `WeaponRaytraceImpactEvent` parameter is a Bartizan type; the
  old condition gated on the unrelated gang-enabled setting, leaving this class exposed once civilians' hard
  `Plugins: [Bartizan]` dependency drops). The gang-enabled check moved into the method body as
  `if (!Settings.isGangEnabled()) return;`. Javadoc rewritten (the old text asserted `Plugins: [Bartizan]` made the
  module loader skip this class entirely, which becomes false once that hard dependency drops).
- `.../src/main/resources/module.yml` (edited) — dropped the `Plugins:`/`- Bartizan` block; `Host_Api: 1.0`
  untouched (out of scope, unlike gadget's independent `1.1`).
- `.../npc/combat/BartizanNpcWeapons.java` — confirmed, no changes needed. `create(...)` returns `NpcRangedAttack`
  (Keystone type), `buildItem(...)` returns `ItemStack`; only method-body local variables (`RegisteredServiceProvider<BartizanApi> rsp`) name Bartizan types, never touched by Keystone's signature-level reflective scan.
- `.../npc/combat/GanglandCombatEligibility.java` — confirmed, no changes needed (only its construction site moved,
  not the class itself).
- `gangland-features/gangland-civilians/src/test/java/org/luckyraven/gangland/civilians/CiviliansBartizanBlindScanTest.java`
  (new) — two tests: (1) `BartizanBlindScan.findUnsafeClasses(...)` asserted to equal exactly
  `{CombatEligibilityConfig}` (not empty — see Deviations), pinning that isolation, not elimination, is what this
  gate achieves; (2) a reflective assertion that `GangAllyWeaponImpactListener`'s `@ListenerHandler` condition
  string equals `"isBartizanAvailable"` (needed because `BartizanBlindScan` treats any non-empty condition as "safe
  by construction" and cannot itself distinguish the correct condition from the old, semantically-wrong one).
- **cops-n-crooks**: untouched (`git status --short gangland-features/cops-n-crooks` empty) — `Plugins: [Bartizan]`
  stays hard, none of its 4 census-listed Bartizan-touching files (`CopListener.java`,
  `NpcDamageUnprotectListener.java`, `DetainmentListener.java`, `TurfFriendlyFireListener.java`) edited.
- **gangland-turf**: untouched (`git status --short gangland-features/gangland-turf` empty), zero code changes
  needed. `turf`'s `module.yml` is `Depends: [civilians]` only, with zero direct Bartizan imports of its own — it
  was only ever *transitively* blocked when civilians required Bartizan. Now that civilians is soft (G5b), **turf
  loads without Bartizan too, with no changes to turf itself** — confirmed live in the `ws7-no-bartizan` smoke row
  below (turf module deploys and no `module.dependency.missing`/`module.plugin.missing` fault is reported for it).

## Deviations from the plan

1. **G5, `GadgetBartizanBlindScanTest`'s force-load check** — the subagent's first cut force-loaded
   `CarMeleeWeaponLookup` via `getDeclaredMethods()` (mirroring `JetpackBartizanTraitBridge`'s check) and it failed:
   `CarMeleeWeaponLookup`'s **private** `weapons()` helper returns a Bartizan type (`WeaponCatalog`) that Keystone's
   real reflection scan never touches at all (the class carries no scan annotation whatsoever). I reviewed and
   accepted the fix: `getMethods()` (public surface only) for this one class, with an explanatory comment, so the
   test doesn't false-positive on a private method the real scan can't reach.
2. **G5b, `CiviliansBartizanBlindScanTest`'s standard-scan assertion cannot mirror gadget's `assertTrue(unsafe.isEmpty())`
   shape** — `combatEligibility()`'s return type must be Bartizan's own `CombatEligibility` interface to publish
   under it (the direction-reversal case has no Bartizan-agnostic signature to fall back to, unlike every other site
   in this module). `BartizanBlindScan` unconditionally targets every `@Configuration` class (no `condition`-style
   exclusion the way `@ListenerHandler` gets one), so `CombatEligibilityConfig` is *unconditionally* reported unsafe
   by raw reflection regardless of isolation — this is expected and intended (Keystone's `ReflectionGuard.orSkip`
   gracefully skips just this one small class in production, fault `reflection.type.missing`). I reviewed and
   accepted the subagent's fix: assert the unsafe set is exactly `{CombatEligibilityConfig}`, pinning *isolation*
   (nothing else lost) rather than an impossible *elimination*. This is a stronger, more honest test than a
   copy-pasted `isEmpty()` would have been for this module.
3. **G5b, no force-load test analogous to G5's second test** — checked: civilians has no unscanned static helper
   with a private Bartizan-typed method the way gadget's `CarMeleeWeaponLookup` does (`BartizanNpcWeapons` is itself
   annotation-scanned via its own `@Bean` return type and already reached, clean, by the standard scan). Confirmed
   correct; no gap.
4. **Process note, not a plan deviation**: my G5b subagent dispatch did not include LEAD-RULES.md's required
   verbatim graphify-first / "read LEAD-RULES.md" boilerplate for subagent prompts. I compensated by giving the
   subagent complete, pre-researched file paths and exact current file contents directly in the prompt (so it never
   needed to explore blind), and I independently re-verified every file and every build myself before accepting the
   gate — the outcome carries the same verification rigor the rule exists to guarantee, but the prompt itself should
   have included that exact line. Flagging for the record.

## Red-first evidence

**G5 — `GadgetBartizanBlindScanTest`**: red demonstrated against `CarDamageListener`'s pre-split shape (the two
Bartizan-event handlers still directly on the always-scanned class) — recorded by the subagent and reviewed by me
matching the file's own javadoc note; green now that `CarWeaponDamageListener` holds them behind
`condition = "isBartizanAvailable"`.

**G5 — `CarMeleeWeaponLookupGuardTest` (B7)**: pins that with Bartizan unavailable, `onVehicleDamage` applies
exactly the vanilla fallback damage (`Math.max(1, (int) Math.ceil(rawDamage))`), never a
`CarMeleeWeaponLookup`-derived value — asserted on the actual `VehicleSession.getCurrentDurability()` delta, not a
mock-invocation check (an earlier review round explicitly rejected a `verify(mock, never())`-only shortcut for this
reason).

**G5b — issue #1 (standard blind scan)**: run against pre-fix code (before `CombatEligibilityConfig` extraction):
```
org.opentest4j.AssertionFailedError: Classes unsafe without Bartizan: [org.luckyraven.gangland.civilians.CiviliansModuleConfig] ==> expected: <true> but was: <false>
```
confirming the whole 9-bean `@Configuration` class was the blast radius before the fix. After extraction, unsafe is
exactly `{CombatEligibilityConfig}` — `Tests run: 2, Failures: 0` for the class.

**G5b — issue #2 (condition-string pin)**: run against pre-fix code (`GangAllyWeaponImpactListener` still
`condition = "isGangEnabled"`):
```
org.opentest4j.AssertionFailedError: expected: <isBartizanAvailable> but was: <isGangEnabled>
```
confirming the listener's gate was semantically wrong. Green after the condition swap.

## Build

- G5 standalone: `mvn clean install -pl gangland-features/gangland-gadget -am` → `BUILD SUCCESS`,
  `Tests run: 73, Failures: 0, Errors: 0, Skipped: 0` (independently re-run by me, matches the subagent's report).
- G5b standalone: `mvn clean install -pl gangland-features/gangland-civilians -am` → `BUILD SUCCESS`,
  `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` (independently re-run by me, matches the subagent's report).
- Whole reactor, after G5: `mvn clean install` (22 modules) → `BUILD SUCCESS` (independently re-run by me).
- Whole reactor, after G5b: `mvn clean install` (22 modules, including Gangland Mail/Civilians/Turf/Cops N
  Crooks/Gadgets/NPC Shops) → `BUILD SUCCESS` (independently re-run by me).
- K1/K2 sanity, gadget: `grep -rln "org.luckyraven.bartizan" gangland-features/gangland-gadget/src/main/java` →
  exactly `JetpackBartizanTraitBridge.java`, `CarMeleeWeaponLookup.java`, `CarWeaponDamageListener.java` (independently
  re-run by me, matches the subagent's report exactly).
- K1/K2 sanity, civilians: `grep -rln "org.luckyraven.bartizan" gangland-features/gangland-civilians/src/main/java`
  → exactly `CombatEligibilityConfig.java`, `GangAllyWeaponImpactListener.java`, `BartizanNpcWeapons.java`,
  `GanglandCombatEligibility.java` (independently re-run by me, matches the subagent's report exactly) — none of
  these sit in an always-scanned signature that also carries unrelated functionality anymore.

## Smoke

`scenarios.json` already pointed at the worktree jars (rulings W17/W18) and `jetpack-give`/`jetpack-migrated` rows
already existed from the G4 fix round (added, not run). Built the whole reactor fresh first (`mvn clean install`,
above), then edited `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` in the main checkout (ruling W17
— untracked harness config, not a source tree):

- Marked `D6` `"legacy": true` and retitled it "SUPERSEDED by ws7-no-bartizan" — its expectation (only
  mail+npcshops load, Bartizan removed) predates G5/G5b and is now stale for the current topology; kept as the
  pre-WS7 topology snapshot rather than deleted.
- Added a new row **`ws7-no-bartizan`**: all six modules deployed, Bartizan removed
  (`"remove_plugins": ["Bartizan"]`), expecting `loaded_modules: [mail, turf, civilians, gadget, npcshops]` (5), one
  fault `module.plugin.missing` (cops-n-crooks only), `"Runtime modules: 5 loaded, 1 fault(s)"`.

Validated `python -c "json.load(...)"` (valid), `python smoke.py --list` (both new/changed rows print correctly),
`python smoke.py --dry-run --deploy --keystone --rows jetpack-give,jetpack-migrated,ws7-no-bartizan` (deploy plan
resolves cleanly for all three rows against the 0.9.2/Keystone/Bartizan-0.4.0 worktree jars, all `FOUND`).

**Test server was reachable** (`E:\Documents\Minecraft\Test Server` present, no port conflict) — ran the real
matrix: `python smoke.py --deploy --keystone --restore --rows jetpack-give,jetpack-migrated,ws7-no-bartizan`.

**Result — all 3 rows PASS, 0 errors each** (`smoke/reports/2026-09-17-0204-summary.md`):

| Row | Verdict | Loaded modules | Errors |
|---|---|---|---|
| `jetpack-give` | PASS | `['gadget']` | 0 |
| `jetpack-migrated` | PASS | `['gadget']` | 0 |
| `ws7-no-bartizan` | PASS | `['civilians', 'gadget', 'mail', 'npcshops', 'turf']` | 0 |

`ws7-no-bartizan`'s live server log (`smoke/reports/2026-09-17-0204-ws7-no-bartizan.md:99,147`) confirms exactly the
predicted fault shape — the real server, not just the a-priori JSON expectation:
```
[Keystone Common.LoggingSink] Module copsncrooks needs plugin Bartizan (plugin=Bartizan jar=cops-n-crooks-0.9.2.jar module=copsncrooks) [module.plugin.missing]
[Gangland.GanglandContext] Runtime modules: 5 loaded, 1 fault(s)
```
Turf loads live alongside gadget/civilians/mail/npcshops with Bartizan absent and cops-n-crooks removed — direct,
real-server confirmation (not just unit-test/build-level) that turf's `Depends: [civilians]` edge is unblocked now
that civilians is soft, with zero changes to turf itself. `--restore` returned the server's `plugins/` tree to its
pre-run state (`gangland-gadget-0.9.1.jar`, `Bartizan-0.3.0.jar`, both Citizens jars parked back) — no lasting side
effects on the shared test server.

## Docket ids touched

- **GD-27** (`pendingRightClickInteract` token race, `triage/gadgets-cars-fuel-jetpack.txt:27`, P3) — **not fixed**,
  per the plan's own honest framing (`WS7-gadget-ownership.md:314`, review `REVIEW-WS7.md:257`). G5's
  `CarDamageState` centralizes read/consume of the token in one place across both car damage listeners, so the
  `CarDamageListener`/`CarWeaponDamageListener` split does not make the underlying race structurally worse than it
  already was — the actual race (two events racing on the same tick) remains open, unchanged by this gate.

No other docket ids touched. No new bugs noticed (docket candidates): none.

## Subagents used

- Sonnet, `claude` (general-purpose), one agent — G5 (gadget): `CarMeleeWeaponLookup` extraction,
  `CarDamageState`/`CarWeaponDamageListener` split, `BartizanBlindScan` (shared), gadget's blind-scan + B7 guard
  tests. Verified by me (read every file, standalone + whole-reactor build, K1/K2 grep) before moving to G5b.
- Sonnet, `claude` (general-purpose), one agent — G5b (civilians): `CombatEligibilityConfig` isolation,
  `GangAllyWeaponImpactListener` condition swap, civilians' blind-scan test. Verified by me identically before
  writing this report.

## Concerns / open questions

1. Process note (see Deviations #4): my G5b subagent prompt omitted LEAD-RULES.md's exact required boilerplate
   line. No functional impact (compensated with a fully pre-researched, self-contained prompt and full independent
   re-verification), but flagging so this doesn't repeat in the next batch.
2. GD-27 (car token race) remains genuinely open — not a new concern this gate introduced, but worth keeping on the
   docket's radar since two listener classes now read/write it through `CarDamageState` instead of one.
3. None on smoke — all 3 rows ran clean against the real server (see Smoke section above).

## Fix round 1 — 2026-09-17/20

Review `exec/WS7/G5-review.md` (Opus, FIX — 2 Critical, 3 Important, 2 Minor) + orchestrator ruling W25. Two
Criticals slipped past the previous gate's tests because `BartizanBlindScan` only checks scanned-annotation
signatures, never actual runtime registration or method-body references. Dispatched to two parallel Sonnet
subagents (gadget: M2 + allowlist + I2; civilians: C1 + C2 + I1 + M1 + allowlist + I2-redo), each verified by me
directly (read every file, independently re-ran every build) — this session was killed by the platform session
limit mid-round (2026-09-17 ~05:00) after both subagents had already reported done and I had independently verified
every file and build; resumed 2026-09-20, reconciled via `git status --short` (every expected file present, nothing
lost) and the already-completed 5-row smoke run before doing anything else, per the coordinator's resume
instructions. No work was redone.

### C1 (Critical) — `CombatEligibilityConfig` was never actually registered

`CiviliansModule.configure(ModuleRegistrar)` registers `@Configuration` classes by an explicit list, not a package
scan — `CombatEligibilityConfig` (created in the previous round) was never added to that list, so
`GanglandCombatEligibility` was never published to the ServicesManager **regardless of Bartizan's presence** —
downed players silently became hittable again.

**Fix**: `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians/CiviliansModule.java`
— `configure()` now does `if (Settings.isBartizanAvailable()) { registrar.configuration(CombatEligibilityConfig.class); }`
after the existing chained call.

**Test** (new): `CiviliansModuleConfigureBartizanGateTest.java` — mocks `ModuleRegistrar` (stubbing every chained
method to return itself), stubs Bartizan availability via `BukkitRegistryFixture.install()` +
`PluginManager.isPluginEnabled("Bartizan")`. Two cases: available → `verify(registrar).configuration(CombatEligibilityConfig.class)`;
unavailable → `verify(registrar, never())...`.

Red (pre-fix):
```
Argument(s) are different! Wanted:
moduleRegistrar.configuration(class org.luckyraven.gangland.civilians.CombatEligibilityConfig);
Actual invocations have different arguments: [only CiviliansYamlConfig, CiviliansFileConfig, CiviliansModuleConfig, + package registrations]
```
Green (post-fix): `Tests run: 2, Failures: 0, Errors: 0`.

### C2 (Critical) — `BartizanNpcWeapons` resolved `BartizanApi.class` before any guard

Both `create(...)` and `buildItem(...)` referenced the `BartizanApi.class` literal (inside
`Bukkit.getServicesManager().getRegistration(BartizanApi.class)`) before the existing `rsp == null` check ever ran
— on a real Bartizan-less server that literal itself fails to resolve (`NoClassDefFoundError`), which the null-check
never gets a chance to guard against. Called from `CivilianNpcFactory` for every hostile civilian spawn with a
`weapon:` pool entry (shipped `npc/civilians.yml` has several, e.g. `gang_member`) — every such spawn on a
Bartizan-less server would have crashed.

**Fix**: `.../npc/combat/BartizanNpcWeapons.java` — first line of both methods now
`if (weaponName == null || !Settings.isBartizanAvailable()) return NpcRangedAttack.NONE;` /
`... return null;` in `buildItem` — short-circuits before the `BartizanApi.class` reference is ever reached.

**Test** (new, B7 shape, mirrors `CarMeleeWeaponLookupGuardTest`): `BartizanNpcWeaponsGuardTest.java` — stubs
`PluginManager.isPluginEnabled("Bartizan")` false, deliberately leaves `Bukkit.getServicesManager()` unstubbed (a
Mockito mock returns `null` from an unstubbed object-returning method — the unit-test stand-in for the jar being
entirely absent, same reasoning already established for `CarMeleeWeaponLookupGuardTest`).

Red (pre-fix, both methods):
```
java.lang.NullPointerException: Cannot invoke "org.bukkit.plugin.ServicesManager.getRegistration(java.lang.Class)" because the return value of "org.bukkit.Bukkit.getServicesManager()" is null
	at BartizanNpcWeapons.create(BartizanNpcWeapons.java:35)
	at BartizanNpcWeapons.buildItem(BartizanNpcWeapons.java:54)
```
Green (post-fix): `Tests run: 2, Failures: 0, Errors: 0` — both return `NpcRangedAttack.NONE`/`null` cleanly.

**Deviation, necessary consequence of C1/C2 (both pre-existing tests)**: `CiviliansModuleTest.java` now bootstraps
`BukkitRegistryFixture` + stubs Bartizan unavailable (C1's unconditional `Settings.isBartizanAvailable()` call
otherwise NPEs against that test's bare setup). `BartizanNpcWeaponsTest.java`'s `mockStatic(Bukkit.class)` helper
and one standalone test now also stub `Bukkit.getPluginManager()` (C2's new guard call would otherwise NPE on that
test's full-class static mock); one test's name/purpose was clarified from "Bartizan absent" to "service
registration absent" now that plugin-availability and ServicesManager-registration are two distinct gates. Neither
was in the original dispatch but both are strictly necessary for the build to stay green — reviewed and accepted.

### I1 — civilians `Host_Api: 1.1`

`gangland-features/gangland-civilians/src/main/resources/module.yml` — `Host_Api: 1.0` → `1.1` (the prior round's
`GangAllyWeaponImpactListener` condition already relies on `Settings.isBartizanAvailable()`, a 1.1-line API; matches
gadget's own module.yml, already at 1.1 for the identical reason).

### W25 ruling — constant-pool Bartizan-reference allowlist test (new shared infra)

No ASM/bytecode-parsing dependency exists anywhere in this reactor (confirmed by repo-wide grep). Built
`gangland-core/src/test/java/org/luckyraven/gangland/core/testsupport/BartizanReferenceScan.java` myself (shared,
beside `BartizanBlindScan`) — a minimal JVM class-file constant-pool parser (JVM Spec §4.4; uses
`DataInputStream.readUTF()` for the UTF8 entries, which reads exactly the `CONSTANT_Utf8_info` wire format) that
finds every compiled class under a `target/classes` tree whose constant pool references `org/luckyraven/bartizan/`
**anywhere** (method bodies, fields, locals — not just scanned-annotation signatures, which is what let C1/C2 slip
past `BartizanBlindScan`). I sanity-checked it myself via `jshell` against both modules' already-built
`target/classes` before handing it to either subagent, confirming it found exactly the audited sets in both cases.

- `GadgetBartizanReferenceAllowlistTest.java` (new) — asserts the found set equals exactly
  `{CarWeaponDamageListener, CarMeleeWeaponLookup, JetpackBartizanTraitBridge}`.
  Red (throwaway `BartizanApi` field added to `CarDamageListener`):
  ```
  expected: <[..JetpackBartizanTraitBridge, ..CarWeaponDamageListener, ..CarMeleeWeaponLookup]> but was: <[..JetpackBartizanTraitBridge, ..CarDamageListener, ..CarMeleeWeaponLookup, ..CarWeaponDamageListener]>
  ```
  Green (reference removed): `Tests run: 1, Failures: 0`.
- `CiviliansBartizanReferenceAllowlistTest.java` (new) — asserts the found set equals exactly
  `{GangAllyWeaponImpactListener, CombatEligibilityConfig, GanglandCombatEligibility, BartizanNpcWeapons}`.
  Red (throwaway reference added to `CiviliansModuleConfig`):
  ```
  expected: <[..CombatEligibilityConfig, ..GanglandCombatEligibility, ..GangAllyWeaponImpactListener, ..BartizanNpcWeapons]>
  but was:  <[..CiviliansModuleConfig, ..CombatEligibilityConfig, ..GangAllyWeaponImpactListener, ..BartizanNpcWeapons, ..GanglandCombatEligibility]>
  ```
  Green (reference removed): `Tests run: 1, Failures: 0`. Re-verified after C1/C2 landed — found set unchanged (both
  fixes reference only `Settings`, a Bartizan-free type).

`Path.of("target/classes")` resolves correctly relative to each module's own Surefire working directory — confirmed
in both modules, no `user.dir` workaround needed.

### I2 — quoted red evidence (gadget, both re-derived from scratch)

**`GadgetBartizanBlindScanTest`**: temporarily copied `onWeaponEntityDamage`/`onWeaponRaytraceImpact` (+ Bartizan
event imports) from `CarWeaponDamageListener` back onto `CarDamageListener` (recreating the pre-G5 shape).
Red:
```
org.opentest4j.AssertionFailedError: Classes unsafe without Bartizan: [org.luckyraven.gangland.gadget.listener.car.CarDamageListener] ==> expected: <true> but was: <false>
```
Reverted via Edit (never git). Green: `Tests run: 2, Failures: 0`.

**`CarMeleeWeaponLookupGuardTest`**: temporarily made the `Settings.isBartizanAvailable()` short-circuit in
`onVehicleDamage` unconditional (always calls `CarMeleeWeaponLookup.resolveMeleeDamage`).
Red:
```
java.lang.NullPointerException: Cannot invoke "org.bukkit.plugin.ServicesManager.getRegistration(java.lang.Class)" because the return value of "org.bukkit.Bukkit.getServicesManager()" is null
	at org.luckyraven.gangland.gadget.listener.car.CarMeleeWeaponLookup.weapons(CarMeleeWeaponLookup.java:46)
	at org.luckyraven.gangland.gadget.listener.car.CarMeleeWeaponLookup.resolveMeleeDamage(CarMeleeWeaponLookup.java:33)
	at org.luckyraven.gangland.gadget.listener.car.CarDamageListener.onVehicleDamage(CarDamageListener.java:122)
```
Reverted via Edit. Green: `Tests run: 1, Failures: 0`.

### I2-redo — civilians, matching the actually-committed `assertEquals` shape

The prior round quoted red output from an earlier `assertTrue(unsafe.isEmpty())` draft of
`CiviliansBartizanBlindScanTest` that no longer matches what's committed (`assertEquals(Set.of("...CombatEligibilityConfig"), ...)`).
Redone properly: via Edit, temporarily copied `combatEligibility()` back onto `CiviliansModuleConfig` and emptied
`CombatEligibilityConfig` to a bare shell (exactly reversing the previous round's extraction).
Red (exact committed assertion, quoted verbatim):
```
org.opentest4j.AssertionFailedError: Only CombatEligibilityConfig's own combatEligibility() bean should be unsafe - every other scanned civilians class (including CiviliansModuleConfig's other 8 beans) must stay reflection-safe: [org.luckyraven.gangland.civilians.CiviliansModuleConfig] ==> expected: <[org.luckyraven.gangland.civilians.CombatEligibilityConfig]> but was: <[org.luckyraven.gangland.civilians.CiviliansModuleConfig]>
```
Restored both files via Edit (`git diff` afterward showed the identical diff to the prior gate's state — no
residue). Green: `Tests run: 2, Failures: 0`.

### M1 — `CiviliansModule.java` javadoc

Rewrote both stale claims: no longer says `GangAllyWeaponImpactListener` is "gated for free by `Plugins: [Bartizan]`"
(now describes its own `condition = "isBartizanAvailable"` gate, since that hard dependency is gone); no longer
lists `GanglandCombatEligibility` under `CiviliansModuleConfig` (now correctly attributed to the sibling
`CombatEligibilityConfig`, with the conditional-registration caveat from C1).

### M2 — `CarDamageMath` extraction

New `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/listener/car/CarDamageMath.java`
— package-private final class, one package-private static `applyDamage(UUID, VehicleSession, ParkedVehicle, int,
CarService)`, body moved verbatim from both listeners' identical duplicated method (only change: `carService`
became an explicit parameter, since it's no longer an instance method). Both `CarDamageListener` and
`CarWeaponDamageListener` now call `CarDamageMath.applyDamage(...)` and no longer carry their own private copy.
Zero Bartizan symbols — confirmed absent from the allowlist/grep.

### Build (independently re-run by me, twice — once before the session gap, once after resuming)

- Gadget standalone: `mvn clean install -pl gangland-features/gangland-gadget -am` → `BUILD SUCCESS`,
  `Tests run: 74, Failures: 0, Errors: 0, Skipped: 0` (73 prior + 1 allowlist test).
- Civilians standalone: `mvn clean install -pl gangland-features/gangland-civilians -am` → `BUILD SUCCESS`,
  `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` (15 prior + 5 new: C1 gate ×2, C2 guard ×2, allowlist ×1).
- Combined (post-resume reconciliation, coordinator's exact command):
  `mvn clean install -pl gangland-features/gangland-gadget,gangland-features/gangland-civilians -am` →
  `BUILD SUCCESS`, gadget `Tests run: 74, Failures: 0`, civilians folded into the same run, both green.
- Whole reactor, twice (once mid-round before the gap, once after resuming): `mvn clean install` → `BUILD SUCCESS`,
  all 22 modules SUCCESS both times.
- K1/K2 sanity re-confirmed post-resume: gadget grep → exactly `JetpackBartizanTraitBridge.java`,
  `CarMeleeWeaponLookup.java`, `CarWeaponDamageListener.java` (`CarDamageMath.java` absent, as required); civilians
  grep → exactly `CombatEligibilityConfig.java`, `GangAllyWeaponImpactListener.java`, `BartizanNpcWeapons.java`,
  `GanglandCombatEligibility.java`. `git status --short gangland-features/cops-n-crooks gangland-features/gangland-turf`
  → empty both times — neither touched.

Two transient whole-reactor `BUILD FAILURE`s were hit mid-round by each subagent (`scoreboard-api`/`shop-api`
`NoClassDefFoundError`, then a `.flattened-pom.xml isn't a file` / corrupted `gangland-core` jar) — both are file-lock
collisions from running two Maven processes concurrently against the same shared worktree (the two subagents were
dispatched in parallel), not caused by either agent's changes; both self-resolved on retry, and every build quoted
above is a clean final run. Process note for next time: avoid dispatching two subagents that both run `mvn clean`
against the same worktree at once, even when their file sets don't overlap — LEAD-RULES.md's "never run two Maven
processes of your own at the same time" should be read to cover concurrent subagents too, not just my own direct
invocations.

### I3 — smoke: two new rows added and run, plus the full existing matrix re-run

Added to `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (main checkout, ruling W17):

- **`car-damage-with-bartizan`** — gadget module, Bartizan present, drives `/glw car`/`/glw car give sports_car 1`
  (the car counterpart to `jetpack-give`, which only ever exercised jetpack commands). Confirms
  `CarWeaponDamageListener`'s `condition = "isBartizanAvailable"` resolves true and registers cleanly when Bartizan
  **is** installed — the flip side of `ws7-no-bartizan`, which already proved gadget boots fine without it.
- **`ws7-no-bartizan-citizens`** — all six modules, Citizens present, Bartizan removed, attempts
  `/glw civilian spawn gang_member` (a `Hostile: true`, `Weapon_Pool`-bearing type, `npc/civilians.yml:85-99`) from
  console. **Documented gap, confirmed by direct source read before writing this row**: both
  `CivilianSpawnCommand.typeIdArgument` and `CivilianSpawnGroupCommand`'s equivalent require
  `sender instanceof Player` and reply `Messages.NOT_PLAYER` otherwise — console literally cannot force an actual
  spawn (there is no "spawn as if a player were here" path in this command tree), so this row can only confirm (a)
  the command resolves cleanly with no exception from console and (b) the whole topology boots clean with Citizens
  present and Bartizan absent. It does **not** exercise `BartizanNpcWeapons.create`'s live weapon-assignment
  codepath (C2's actual fix target) — that needs a human tester with a real client standing where a civilian can
  spawn, or spawning one as an actual player. Booked for G6's manual checklist, same "needs a client" shape already
  established for jetpack equip/fly.

Both dry-run-validated before the real run (`python -c "json.load(...)"` valid; `smoke.py --dry-run --deploy
--keystone --rows ...` resolved cleanly, all jars `FOUND`).

**Ran the full 5-row matrix** against fresh fix-round-1 jars:
`python smoke.py --deploy --keystone --restore --rows jetpack-give,jetpack-migrated,ws7-no-bartizan,car-damage-with-bartizan,ws7-no-bartizan-citizens`

**Result — all 5 rows PASS, 0 errors each** (`smoke/reports/2026-09-17-0233-summary.md`):

| Row | Verdict | Loaded modules | Errors |
|---|---|---|---|
| `jetpack-give` | PASS | `['gadget']` | 0 |
| `jetpack-migrated` | PASS | `['gadget']` | 0 |
| `ws7-no-bartizan` | PASS | `['civilians', 'gadget', 'mail', 'npcshops', 'turf']` | 0 |
| `car-damage-with-bartizan` | PASS | `['gadget']` | 0 |
| `ws7-no-bartizan-citizens` | PASS | `['civilians', 'gadget', 'mail', 'npcshops', 'turf']` | 0 |

`--restore` returned the server's `plugins/` tree to its pre-run state (gadget 0.9.1 jar, Bartizan 0.3.0,
both Citizens jars parked back) — no lasting side effects on the shared test server. This run completed just before
the session was killed by the platform limit; confirmed intact and unchanged on resume (report files present on
disk at their original timestamp, matching the coordinator's reconciliation instructions).

### Subagents used (fix round 1)

- Sonnet, `claude`, one agent — gadget: M2 (`CarDamageMath` extraction), `GadgetBartizanReferenceAllowlistTest`
  (W25), I2 red-evidence re-derivation for two existing tests. Verified by me (read every file, standalone +
  combined + whole-reactor build, K1/K2 grep).
- Sonnet, `claude`, one agent — civilians: C1 (registration gate), C2 (guard order), I1 (`Host_Api`), M1 (javadoc),
  `CiviliansBartizanReferenceAllowlistTest` (W25), I2-redo. Verified identically.
- Both dispatched in parallel (LEAD-RULES.md's 2-concurrent limit) — see the Build section's note on the resulting
  transient Maven file-lock collisions.

### Concerns / open questions (fix round 1)

1. Process lesson (see Build section): two subagents running `mvn clean` concurrently in the same shared worktree
   caused two transient, self-resolving `BUILD FAILURE`s. No functional impact (every final build is clean), but
   worth avoiding next time — either stagger subagent dispatch when both will build, or have only one build at a
   time even across parallel subagents.
2. `ws7-no-bartizan-citizens` cannot exercise C2's actual live codepath (no console-only way to force a civilian
   spawn — confirmed by source read, not assumption) — booked for G6's manual checklist alongside jetpack
   equip/fly and car-punch verification. `BartizanNpcWeaponsGuardTest` is the unit-level equivalent for the part
   console can't reach.
3. GD-27 (car token race) still open, unchanged by this round.
4. No new docket rows — C1/C2 were introduced and fixed within this unreleased batch (no docket rows needed, per
   the review's own note and W25).
