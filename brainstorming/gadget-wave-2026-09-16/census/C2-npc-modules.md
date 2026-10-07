# C2 — NPC modules (civilians, cops-n-crooks, turf) Bartizan-coupling census (repo: Gangland Warfare, 0.9.1, graph = HEAD fb460b35)

Source: Haiku Explore agent, graphify-first on the fresh Gangland graph.

## 1. Coupling site table (11 sites across 9 files)

| file:line | Bartizan symbol | used for | classification | reasoning |
|---|---|---|---|---|
| `gangland-civilians/.../CiviliansModuleConfig.java:4` | `CombatEligibility` | interface import, published to `ServicesManager` | WEAPON-INHERENT | Bartizan's SPI interface; no Bukkit/Keystone equivalent hook exists today |
| `gangland-civilians/.../CiviliansModuleConfig.java:9` | `BartizanNpcWeapons` | bean instantiation; NPC ranged-weapon factory | REPLACEABLE | a Keystone-only factory could serve unarmed/vanilla NPCs; Bartizan is convenience, not necessity |
| `gangland-civilians/.../CiviliansModuleConfig.java:11` | `GanglandCombatEligibility` | implements `CombatEligibility`, published to `ServicesManager` | WEAPON-INHERENT | same SPI contract as row 1 |
| `gangland-civilians/.../npc/combat/BartizanNpcWeapons.java:8` (lookup at `:35-36`, `:54-55`) | `BartizanApi` | lazy `RegisteredServiceProvider` resolution | REPLACEABLE | **degrades gracefully today**: returns `NpcRangedAttack.NONE` / `null` when provider absent (see §2) |
| `gangland-civilians/.../npc/combat/GanglandCombatEligibility.java:4` | `CombatEligibility` | implementation for downed-player eligibility checks | WEAPON-INHERENT | Bartizan SPI interface; no Bukkit equivalent contract |
| `gangland-civilians/.../listener/gang/GangAllyWeaponImpactListener.java:7` | `WeaponRaytraceImpactEvent` | event listener param; gang-ally friendly-fire suppression | WEAPON-INHERENT | Bartizan-specific unified raytrace-impact event; no Bukkit equivalent |
| test `gangland-civilians/.../npc/combat/BartizanNpcWeaponsTest.java:10-13` | `BartizanApi`, `WeaponItemApi`, `NpcWeaponController`, `NpcWeaponFactory` | mocking/assertions | REPLACEABLE | test-only, no production impact |
| `cops-n-crooks/.../listener/detainment/CopListener.java:23-24,105,163` | `WeaponRaytraceImpactEvent`, `WeaponRaytracer` | checks `isRaytraceDamageInProgress()` flag; handles impact for detention | WEAPON-INHERENT | `WeaponRaytracer` state flag is Bartizan-specific |
| `cops-n-crooks/.../listener/NpcDamageUnprotectListener.java:19,86` | `WeaponRaytraceImpactEvent` | strips NPC protection pre-damage, at raytrace phase | WEAPON-INHERENT | Bartizan-specific unified raytrace-timing event |
| `cops-n-crooks/.../listener/police/DetainmentListener.java:26,46` | `WeaponShootEvent` | detects weapon fire to trigger detention | REPLACEABLE | `ProjectileSpawnEvent` or item-use detection could substitute |
| `cops-n-crooks/.../listener/turf/TurfFriendlyFireListener.java:22,81` | `WeaponRaytraceImpactEvent` | friendly-fire suppression on weapon impact | WEAPON-INHERENT | unified weapon-impact event; no Bukkit equivalent |

**Summary: 7 WEAPON-INHERENT, 4 REPLACEABLE.** The REPLACEABLE group (`BartizanNpcWeapons` factory itself, `DetainmentListener`'s shoot-detection, plus the two test-only sites) is where a soft-dependency degraded mode is realistic; the WEAPON-INHERENT group (both `CombatEligibility` SPI implementations, all three `WeaponRaytraceImpactEvent` listeners, `GangAllyWeaponImpactListener`) genuinely has no Bukkit/Keystone substitute today — going soft there means "feature does nothing" rather than "feature runs a fallback."

## 2. Degraded-mode behavior today

- `BartizanNpcWeapons.java` **does** have internal null-safety: `:35-36` `if (rsp == null) return NpcRangedAttack.NONE;`, `:54-55` `if (rsp == null) return null;` — written to degrade gracefully to vanilla/melee NPCs if Bartizan is absent.
- **But this code never runs**, because both modules fail-fast at load time on the `Plugins:` check: `gangland-civilians/src/main/resources/module.yml:7-8` and `cops-n-crooks/src/main/resources/module.yml:10-11` both declare `Plugins: [Bartizan]`.
- **Conclusion: dead code today.** The graceful-degradation logic already exists inside `BartizanNpcWeapons` but is unreachable because the module loader rejects the whole module before that class is ever exercised without Bartizan present. This is a strong signal for WS7-D<n>: making civilians "soft" on Bartizan would immediately activate already-written fallback logic rather than requiring new code — worth flagging as a specific finding for the planner and possibly a docket entry (dead defensive code masking as safety net, K8).

## 3. Turf's transitive reach

- `gangland-turf/src/main/resources/module.yml` (`:1-9`): `Depends: [civilians]` only — **no** `Plugins: [Bartizan]` entry.
- Direct Bartizan imports in turf: **zero** (grep across all `.java` under `gangland-turf/src` found no `org.luckyraven.bartizan` matches).
- **Conclusion confirmed**: turf's Bartizan exposure is entirely transitive through its `Depends: [civilians]` edge — if civilians fails to load (Bartizan absent, fail-fast), turf fails to load too via `module.dependency.missing`, even though turf itself never touches a Bartizan symbol. This is the "Bartizan-absent blast radius" the Bartizan-split README already flagged as open.

## 4. Keystone module loader's `Plugins:` missing behavior

File: `E:\Programming\java\Keystone\keystone-module\src\main\java\org\luckyraven\keystone\module\ModuleResolution.java`

- `:92-106` (fault `module.plugin.missing`): when a module's `Plugins:` entry names an absent/disabled plugin, the module is **removed from the accepted set before dependency resolution** (`:97` `accepted.remove(descriptor.id())`), and a `Fault.dependency("module.plugin.missing", ...)` is recorded (`:98-103`).
- **The module fails to load entirely — no partial load, no warn-only mode.**
- This cascades: any module that `Depends:` on the rejected module fails in the same fixpoint pass with `module.dependency.missing` (`:108-121`). This is exactly the turf ← civilians cascade in §3.
- Mechanism runs at "pass 2.5" of `ModuleResolution.resolve()`, strictly before any module jar is added to the module classloader.

## Implication for WS7-D<n> (the user decision K7 requires, not decided here)

- Going **soft** on civilians/cops-n-crooks would require: (a) dropping `Plugins: [Bartizan]` from both `module.yml`s, (b) conditioning every WEAPON-INHERENT listener (`GangAllyWeaponImpactListener`, `CopListener`, `NpcDamageUnprotectListener`, `TurfFriendlyFireListener`, both `CombatEligibility` implementations) on a `Settings.isBartizanAvailable()`-style gate per the Citizens pattern (C1 §5), since none of those 7 sites has existing null-safety the way `BartizanNpcWeapons` does.
- The REPLACEABLE sites are cheap either way; the WEAPON-INHERENT sites are where "soft" means real feature loss (no NPC weapon combat, no friendly-fire suppression, no raytrace-based detainment) rather than a fallback behavior — this is the substance of the Bartizan-absent blast-radius decision.
- Turf needs **no direct change** to go along with either choice — its only exposure is the `Depends: [civilians]` cascade; if civilians goes soft (loads without Bartizan), turf loads too, degraded only insofar as civilians itself is degraded.

## Not verified by this census
- Whether `DetainmentListener`'s "REPLACEABLE" classification survives contact with `WeaponShootEvent`'s exact payload (e.g. does it carry ammo/reload state a `ProjectileSpawnEvent` substitute would lose?) — planner should confirm before committing to that substitution in a plan.
- Whether any additional Bartizan symbol appears in `gangland-npc-shops` or `gangland-mail` (README states neither carries `Plugins:`/`Depends:` edges to Bartizan; this census did not re-grep those two modules — trust the README unless the planner needs it re-verified).


> **Correction (2026-09-17, orchestrator, WS7 G5 review C2):** the claim that `BartizanNpcWeapons` "degrades gracefully" without Bartizan is wrong — `getRegistration(BartizanApi.class)` resolves the class constant before the null check and throws `NoClassDefFoundError`. G5b guards both methods with `Settings.isBartizanAvailable()`.
