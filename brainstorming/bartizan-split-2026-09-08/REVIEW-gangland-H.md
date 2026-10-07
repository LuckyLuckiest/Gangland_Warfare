# Gate review H — Gangland 0.9.0 group H (gangland-civilians module) + T-G4b

Reviewer R-G-H (Opus, feature-dev `code-reviewer`, read-only), 2026-09-09 ~01:20. Reviewed commit `1602e41b` on
`0.9.0` against `gangland-0.9.0.md` §1.5, T-G4b, group H, `architecture/PICK.md`, `documentation/module-loader.md`,
Keystone 1.9.0's `keystone-npc`/`keystone-bean` and the 0.8.4 snapshot. Transcribed by the orchestrator; orchestrator
actions are marked **→**.

## Verdict: H PASS WITH FIXES

Module skeleton, descriptor, build registration, bean wiring, SPI shapes, the `CivilianNpc` rebase and the T-G4b sign
redirection are structurally correct and faithful. Four issues need fixing before group K re-points cops onto the same
code (two sit in the Bartizan hand-off path and cops would inherit them).

## Findings

### Major

**M1 — an unvalidated weapon name reaches Bartizan and NPEs the NPC on its first combat tick.**
`CivilianNpcFactory.pickWeaponName` picks from `typeConfig.weaponNamePool()`, which `YamlCiviliansConfigProvider:83-88`
fills with `weapon:`-prefixed entries AND every non-prefixed entry (bare materials). `NpcWeaponFactoryImpl.create` did not
null-check `createTransientWeapon`, so `NpcWeaponControllerImpl.isBusy()` NPE'd; `CivilianService.tickAll` caught it and
destroyed the NPC. 0.8.4 looped the pool and skipped unresolvable names. Fix: `BartizanNpcWeapons.create` returns
`NpcRangedAttack.NONE` unless `api.items().isValidWeaponName(weaponName)`; Bartizan guards its factory.
**→ Bartizan side done (`NpcWeaponFactoryImpl` throws `IllegalArgumentException` for an unknown name; api doc notes it).
Gangland side = task T-HR1.**

**M2 — a Bartizan-armed civilian is never given the weapon item; `refreshHeldItem()` is a permanent no-op.**
`CivilianNpc:111-117` dropped the old `heldWeapon.buildItem()` main-hand assignment; Bartizan's `refreshHeldItem` only
updates an item already in hand and returns on `AIR`. With the shipped `civilians.yml` a hostile civilian fights
bare-handed (firing still works). Fix: `BartizanNpcWeapons.buildItem(name)` pass-through (`api.items().buildItem`) set into
the main hand right after `setRangedAttack`, reproducing the old `heldWeapon` precedence over the vanilla pool. **→ T-HR2.**

**M3 — cops still declares the two civilian beans T-H4 claims it moved.** `CopsNCrooksFileConfig:4-7,27-35` keeps
`civilianSettings()` / `civilianSpawnConfigProvider()` and three imports of classes now in civilians; once cops compiles,
two modules register the same bean types and the later silently overwrites. Fix: delete both beans and the imports (they
exist verbatim in `CiviliansFileConfig`). **→ T-HR3.**

**M4 — a mistyped legacy alias silently turns a BUY sign into a SELL sign.** `SignManager:190-191` uses
`"item-buy".equals(headerKey) ? buy : sell`; any other key (typo, `glw-item-buy`, a malformed value without a colon)
resolves every placed `[WEAPON-BUY]` sign through the SELL definition — the server pays players. Fix: explicit match,
`item-buy` → buy, `item-sell` → sell, anything else → `log.warn` + skip. **→ T-HR4.**

### Minor

**m5** — the Citizens gate covers the timers but not the command path (`createCivilian:85` calls `CitizensAPI` unguarded;
matters once Citizens goes soft in group M). Fix: `if (!NpcSupport.available()) return null;` first line of
`createCivilian`. **→ T-HR5.**
**m6** — cops' `module.yml` still `Depends: [turf, weapon]`; `weapon` no longer exists, so the loader would drop cops with
`module.dependency.missing`. Group K owes `Depends: [turf, civilians]` + `Plugins: [Bartizan]`. **→ noted for group K.**
**m7** — the javadoc explaining why `manager.initialize()` is not called moved onto the new `legacySignRewriter()` bean
instead of `signManager(...)`. **→ T-HR6.**
**m8 — design consequence (spec-level):** `Plugins: [Bartizan]` on civilians plus `Depends: [civilians]` on turf and cops
means a server without Bartizan loses civilians, turf and cops entirely (`module.plugin.missing` cascades through the
dependency fixpoint). Turf capture has no weapon coupling of its own. Also `gangland-impl/src/main/resources/plugin.yml`
has no `Bartizan` under `softdepend:` yet (group M, T-M1) — without it Bukkit may enable Gangland first and drop the three
modules even with Bartizan installed. **→ Recorded in the wave README for the user's decision; T-M1 confirmed in the
checklist.**

### Verified clean

`module.yml`, `CiviliansModule` scans, `npc/civilians.yml` from the KERNEL-phase `CiviliansYamlConfig` with the module
classloader and gone from the cops jar, 11/32 command keys, build registration complete (incl. the added `keystone-npc`
management), no cops/turf/weapon/`io.papermc` references; `BartizanNpcWeapons` resolves per call and returns `NONE` on a
null name or absent provider; `DownedTargetFilter` set at spawn and consulted by `NpcCombatDelegate.attack`;
`GanglandMarkDefaults` reproduces the three-tier cascade exactly; `CombatEligibility` published under the interface (the
ServicesManager key per `BeanFactory:530-534`), every other SPI bean concrete, `NpcMarkManager` its own bean; no
listener-as-bean; `@Bean` parameter lists match the originals; `CivilianNpc` method-for-method identical apart from the
planned changes, `SHOULD_SAVE=false` kept; `setDataSupplier` survives via Keystone's `EntitySpawner` constructor;
`GangAllyWeaponImpactListener` byte-identical except the event import; `LegacyAliasSignAdapter` clones `lines[]`, rewrites
header + content only, delegates everything, end-to-end resolution works; `LegacySignRewriter` null-safe and
case-insensitive; `SignManager` takes the rewriter as a bean; zero `org.luckyraven.bartizan` outside civilians.

## What a server sees

**Bartizan absent:** civilians is not loaded (`module.plugin.missing`), and once turf/cops declare `Depends: [civilians]`
they are dropped too; the graceful-degradation code in `BartizanNpcWeapons`/`GanglandCombatEligibility` only protects
against Bartizan being disabled after boot. **Citizens absent (Bartizan present):** the module loads, reports
`npc.citizens.missing` once, starts no timers; `/glw civilian spawn` still reaches `CitizensAPI` unguarded (m5).

## Turf's dependency on the civilian type ids

Turf never reads `civilians.yml`: `turf/turf_npcs.yml` carries `quartermaster`/`turf_defender` as strings handed to
`CivilianSpawnManager.spawnCivilian(location, typeId)`, a runtime lookup against a civilians-module bean. It keeps working
provided the file moved unedited (it did), turf declares `Depends: [civilians]` (group I), and `turf/turf_npcs.yml` +
`TurfNpcsConfigLoader` move from the cops jar into the turf jar (group I). Existing installs keep their extracted
`npc/civilians.yml`.

## Not verifiable by the reviewer (no shell)

Gate H greenness and the reactor error confinement; the file counts and `git mv` history; the deleted weapon module's legacy
sign type names (recovered from `git show 2a3136e1^`); the red-first claims; whether `gangland-civilians-0.9.0.jar` lands in
`target/modules/`.
