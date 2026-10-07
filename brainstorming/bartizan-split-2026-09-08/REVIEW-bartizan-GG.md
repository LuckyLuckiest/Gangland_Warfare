# Gate review GG — Bartizan groups F–I (B8–B14)

Reviewer R-B-FGHI (Opus, feature-dev `code-reviewer`, read-only), 2026-09-08 ~23:00. Reviewed `bartizan-plugin/src/main`
as of commits `ea5022d` (GF) and `75f6a16` (GG) against `bartizan.md` §Contract, §1.2–1.7, tasks B8–B14, §4, the §7
rows, `architecture/PICK.md`, the Oriel bootstrap template and Keystone 1.9.0 sources. Groups J/K/L were being written
concurrently and were excluded. Transcribed by the orchestrator; orchestrator actions are marked **→**.

## Verdict: GG PASS WITH FIXES

The bootstrap, FILE-phase wiring, action port and item-vocabulary layer are structurally correct and faithful to the
originals. Nothing forces a wrong foundation on J–P; every defect is local to a file already written. One finding is
data-destructive and must land before B22.

## Findings

### Blocker

**B1 — `WeaponDataCleanupTask` wipes the weapon table every 10 minutes instead of every 30 days.**
`data/WeaponDataCleanupTask.java:27,32-35`, `config/WiringConfig.java:105-113`. The executor used `Auto_Save.Time`
(minutes, default 10) as the timer period; in Gangland the same `cleanup()` ran on `PluginDataCleanupService`'s scan
schedule (`Clean_Up.Time: 30` **days**). `cleanup()` calls `WeaponRepository.deleteAll()` and `weaponManager.clear()`
while the repository's data supplier is live, so every 10 minutes every weapon's persisted magazine/durability/
selective-fire state is dropped and re-minted. `Auto_Save.Time` is also the wrong key; §1.8's layout has no cleanup key.
Fix: add `Clean_Up.Time` (days, default 30) to `BartizanSettings` and to B18's `settings.yml`; period and delay =
`days * 24 * 60 * 60 * 20` ticks; guard `<= 0`. **→ Java side applied by the orchestrator after X-B-JKL reports;
`Clean_Up.Time` added to B18's task text.**

### Major

**B2 — `onDisable` never unregisters the `ServicesManager` providers and never clears the container.**
`Bartizan.java:43-54`. Three services are published at bean construction (`WeaponRaytracer`, `BartizanApi`,
`ItemVocabulary`) but teardown only does `PacketBridge.reset()` + `shutdownBeans()`. A `/reload` or disable→enable
leaves dead providers that hand Gangland a `BartizanApiImpl` over a shut-down `WeaponManager`, and the second enable
registers a duplicate `ItemVocabulary` under the same namespace. Fix: `getServer().getServicesManager().unregisterAll(this)`,
`PacketBridge.reset()`, `shutdownBeans()` in try/catch, `container.clear()` in `finally`. **→ Applied after X-B-JKL.**

**B3 — `WeaponItemApiImpl.isSameWeapon` mints and registers weapon instances as a side effect of a read-only comparison.**
`item/WeaponItemApiImpl.java:39-47` calls `validateAndGetWeapon(null, stack)`, which registers a uuid'd weapon that the
live data supplier then persists — a cross-plugin api method P3's sign/shop similarity code calls per item. `Weapon.compareTo`
uses only name/category/material/durability, all on the template. Fix: `getWeaponTemplate(getHeldWeaponName(x))` for
both sides, `compare(w1, w2) == 0`. **→ Applied after X-B-JKL.**

### Minor

**m1** — `pendingDamage` statics in `MeleeAction`/`ThrowableAction`/`IncendiaryAction`, read in `WeaponInteract:258-260`:
`WeaponInteract` already injects a shared per-weapon map into the actions (`meleeCooldowns`); the same route gives one
instance-scoped `Set<UUID>` passed through the three constructors. Low urgency. **→ Deferred to B22/B19 cleanup; noted.**
**m2** — three holder `@Bean` methods return the interface instead of the concrete class (`ItemConfig.bartizanItemVocabulary`,
`ItemConfig.weaponItemApi`, `WiringConfig.bartizanApi`); `weaponRaytracer` gets it right. **→ Applied after X-B-JKL.**
**m3** — the FILE-phase hook does not register `FileInitializer` beans; the three beans self-register. Works today (the
hook fires per bean), but a future FILE bean that forgets the manual call never initialises. **→ Comment added to the
hook stating registration is the bean's job; revisit in B22.**
**m4** — `DamageKind` is effectively single-valued: only `ThrowableAction` fires `WeaponEntityDamageEvent`, always
`EXPLOSION`. Not a regression (0.8.4 never fired it); in scope per §1.6(3). **→ Recorded for B21 docs and P3.**
**m5** — `Bartizan.bStats()` chart `context.get(WeaponAddon.class).size()` NPEs on a partial container. **→ Guarded after X-B-JKL.**

## Deviation rulings

| Deviation | Ruling |
|---|---|
| Dropped `Command.setInformationManager(...)` | Accept — no such member on Keystone's `Command`; **B16 must constructor-inject `InformationManager`** (sent to X-B-JKL). |
| Dropped `ArgumentMessages.install(...)` | Accept with a note — argument-framework errors stay unlocalised English; B18/B21 record it as a known limitation. |
| `FULL_PREFIX` in `BartizanChatUtil.commandDesign`/`confirmCommand` | Accept, an improvement (`SHORT_PREFIX` would never match a usage string). |
| `KernelConfig.fileManager()` registers only `settings.yml` | Accept — §1.3's per-bean table is authoritative; the addon beans add the `items/` handlers. |
| `Placeholder` params passed `null` | Accept — no PlaceholderAPI adapter is planned; B21 docs say "declared, not yet consumed". |
| `WeaponDataCleanupTask` on Keystone `Timer` | Accept the mechanism (`start(false)` is right), reject the period — B1. |
| `pendingDamage` kept `public static final` | Accept as correct-but-improvable — m1. |
| `ThrowableAction` raises `WeaponEntityDamageEvent` at two sites | Accept, correct — the vehicle site reproduces the deleted `pendingVehicleExplosionDamage` loop exactly; without it P3's `CarDamageListener` has no producer. |
| `pendingKillerWeapon` deleted with no replacement | Accept with an obligation on group L: **`WeaponDeathListener` records `victimUuid → weaponName` from its own `WeaponEntityDamageEvent` handler** (sent to X-B-JKL). |
| B7/M1 raytrace shuffle | Settled correctly; `WeaponRaytracerImpl` implements all four members and uses the static flag with try/finally. |

## Verified clean

Fire semantics unchanged (`GunAction`/`FullAutoTask` byte-equivalent minus `RecoilCompatibility`; `WeaponInteract` keeps
AUTO hold-to-fire vs SINGLE/BURST one-per-press with the verbatim burst spacing); no `ignoreCancelled` on the
`PlayerInteractEvent` handler; `CombatEligibility` polarity correct at both reload sites; `WeaponItemSpawnListener`
byte-identical (the `isAssignableFrom` guard the brief mentioned never existed in 0.8.4 — nothing lost). Bootstrap: six
`registerInstance` calls in Oriel order, config scan → instantiate → listener → command phases, FILE and DATABASE hooks,
`getCommand("bartizan")` wiring with Brigadier, `onEnable` guarded; `KernelConfig` installs `Diagnostics`, `PacketBridge`
(`ReflectivePacketAdapter`), permissions under prefix `bartizan`, `FileManager`; bStats id 0 + TODO; no static service
locator; no phase-ordering hazard; no listener that is also a `@Bean`. Item framework: converter/serializer/refresher
order and priorities reproduce the donor exactly (weapon@10, wearable@10, ammunition@0), namespace `bartizan` published on
the `ServicesManager`, throwable-UUID determinism intact. `plugin.yml`: `depend: [Keystone]`, `softdepend: [ViaVersion,
PlaceholderAPI, NBTAPI]` (the brief's "Citizens" was wrong — §C.5 says NBTAPI), command `bartizan` aliases `btz`/`weapon`,
permission `bartizan.command.main`; `module.properties` correct. Settings/messages: `FileHandlerReader`, no
`setDefaults`, every read key exists in §1.8 (except the new `Clean_Up.Time`), the 18-entry `BartizanMessages` table with
`findMissingPaths`. Purity: zero `org.luckyraven.gangland` imports, `io.papermc`, `Bukkit.getLogger`; the
`BiologicalAction:72` literal `§` is the only one (finding #24 accurate); braces on their own lines.

## Not verifiable by the reviewer (no shell)

Maven outcomes; the api reinstall; byte equality beyond the eight files diffed by hand; whether `bartizan-plugin/pom.xml`
declares `<description>` (else `plugin.yml`'s `${project.description}` stays literal — **→ check at B22**); whether
`DatabaseManager`'s constructor reads `DatabaseSettingsProvider` eagerly in KERNEL before `BartizanSettings` is populated
in FILE (**→ check at B22 / phase D smoke**).
