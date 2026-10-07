# Final review — Bartizan 0.1.0 groups J–O, fix commits, whole-plugin runtime pass

Reviewer R-B-FINAL (Opus, feature-dev `code-reviewer`, read-only), 2026-09-09 ~01:45. Scope: what the GA/GD/GG reviews
did not cover — database, commands, NPC cadence, death listener, api impl, YAML, tests, docs (commits `2b9a02c`,
`36d9d39`, `dfe3b29`) plus the fix commits `1ee6fd4` and `38913d5`, and a whole-plugin pass for runtime hazards the unit
tests cannot catch. Transcribed by the orchestrator; orchestrator actions are marked **→**.

## Verdict: PASS WITH FIXES — deployable to the smoke server, but one blocker must land before any real server

## Findings

### Blocker

**B1 — nothing in Bartizan ever saves the weapon table, closes the database or disconnects the backend.**
`WeaponManager.initialize()` wires `setDataSupplier` (byte-identical to 0.8.4) but the only `saveAll` in the plugin is the
one-shot import. Gangland drove persistence through `PeriodicalUpdates` (autosave on `Auto_Save.Time`) and
`ShutdownSequence` (bean shutdown → final save → `closeConnections()` → `disconnectBackend()`); Bartizan ported neither.
Consequences: every runtime weapon state (magazine, durability, selective fire) is lost on restart — the very state the
import task preserves once; `/bartizan reload` reloads from a table never written; the HikariCP SQLite pool and its Windows
file handles outlive a reload; `Auto_Save.Time` is dead while `migration.md` documents it. Fix: an autosave `Timer` bean
(`start(false)`, `Auto_Save.Time` minutes) calling the registry's `saveAll`, and `onDisable` = `saveAll` →
`closeConnections()` → `disconnectBackend()` each in its own try/catch, `container.clear()` in `finally`, per
`ShutdownSequence:34-51`. **→ Executor X-B-R.**

### Major

**M1 — `WeaponDeathListener` at `HIGH` overrides Gangland's three deliberate `setDeathMessage(null)` suppressions**
(Citizens-NPC victim, duplicate event inside the dedup window, Citizens-NPC killer). Shooting a cop NPC would broadcast a
death message; a melee kill could broadcast twice. Fix: `if (event.getDeathMessage() == null) return;` at the top.
**→ X-B-R.**
**M2 — `WeaponTableImportTask` writes the one-shot marker after a caught `SQLException`**, so a transient `SQLITE_BUSY`
on the first boot (Gangland's pool holds `gangland.db`) permanently skips the migration. Ruling: retry next boot — marker
on the success and file-absent paths only; `migration.md:100-103` corrected. **→ X-B-R.**
**M3 — NBT-API is functionally required, not soft.** Keystone's `NbtBridge.detect()` falls back to a no-op accessor when
it is absent, making every Bartizan item inert; `softdepend` only orders loading. Gangland's own `plugin.yml` has `NBTAPI`
under `depend`. **→ Ruling: `depend: [Keystone, NBTAPI]` (deviation from §C.5 recorded); X-B-R.**
**M4 — `bartizan-api.md:69` claims `validateAndGetWeapon` is read-only**; it registers the item's uuid (the side effect
GG-B3 removed from `isSameWeapon`). **→ Doc scoped to the four read-only members; X-B-R.**

### Minor

**m1** — the throwable victim map is never swept (the `remove` sits after the `killer == null` return; entries are kept
even when the damage event is cancelled). **→ X-B-R.**
**m2** — `migration.md` shows flow-style `Default_Sound: { … }` examples the shipped file does not use. **→ X-B-R.**
**m3** — `items/wearables.yml:18-19` header still says `gangland.wearables.<key>` (docket #26). **→ X-B-R.**
**m4** — `NpcWeaponCadenceTest` never exercises `fireRateMultiplier != 1.0`, so `scaleCooldown`'s rounding and `max(…, 5)`
floor are unpinned. **→ X-B-R adds a 0.5 case.**
**m5** — `WeaponDataCleanupTask` computes its period once; `/bartizan reload` never re-reads `Clean_Up.Time`. **→ Not
fixed (30-day cadence); documented as a known limitation.**

### Verified clean

Database: table `weapon`, columns `uuid`/`type` unchanged; repository on the DatabaseBackend SPI; `WeaponManager.initialize()`
byte-identical incl. `setDataSupplier`; `insertInitialData()` empty; `setType` → optional `createSchema` → `connectBackend`;
SQLite at `plugins/Bartizan/database/bartizan.db`. Commands: 14 classes, `commands.json` 14 keys matching `/bartizan …`
paths, the four `wearable_*` keys line up with the `startsWith` filters, every positional input a chained `OptionalArgument`
with completion, `InformationManager` a KERNEL bean constructor-injected (no static), `ReloadCommand` = `initializeAll` +
`reloadBeans` (Gangland does not re-register tab completion on reload either), `&`-codes only. NPC cadence: every line of
`NpcWeaponControllerImpl` matches `NpcCombatDelegate` (SINGLE `max(perShot*cooldown,5)`, BURST `perShot*cooldown + cooldown`
via `SequenceTimer(plugin,1,1)` `start(false)`, AUTO `max(cooldown,5)`, `fireRound`, `triggerReload`, `refreshHeldItem`,
`scaleCooldown`, `tick()`), plus a defensive `getSoundData()` null guard the original lacked; `aimErrorDegrees` stored never
read; the cadence test fails on any off-by-one; the factory throws on an unknown name. Api impl holds live managers; the
three services registered under the api interfaces; `onDisable` unregisters; `combatEligibility()` resolves per call.
Resources: `settings.yml` keys one-to-one with `BartizanSettings`; `message_en.yml` exactly the 18 paths; jetpack
`Extra_Tags` complete. Tests: 17 classes, nothing weakened, no real-database test (so the Windows temp-dir rules do not
apply — stated, not a gap). Docs: ten spot-checked claims true. Runtime: the four `start(true)` sites are the 0.8.4
originals (pre-existing); no `Bukkit.getLogger()`; the only `§` is docket #24; no `ignoreCancelled` on a
`PlayerInteractEvent`; no listener-as-bean; no Citizens class named; no phase inversion.

## Death-message assessment

Gun and melee kills still produce the weapon message: `onPlayerDeath` falls back to `validateAndGetWeapon(killer,
mainHand)` when no throwable claim exists — the branch the deleted `WeaponDeathMessageContributor.resolve` took — so there
is no fall-through to Gangland's generic text. The real problem is the priority inversion (M1). Gangland's downed-player
broadcast path can no longer consult a contributor, so downed broadcasts stop naming the weapon after 0.9.0 (docket #22).

## What the phase-D console smoke must look for in Bartizan's boot log

Set `Debug.Enable: true` in `plugins/Bartizan/settings.yml` first.
1. Boot order: no `Could not connect to the 'sqlite' database`, no `ConfigReport` warnings from `BartizanSettings`.
2. Exactly one import line (`Legacy weapon table import: N row(s) imported …` / `0 row(s) found` / DEBUG `No legacy
   Gangland database found`). `Failed to import the legacy weapon table` or `No suitable driver found for jdbc:sqlite` is a
   finding — `sqlite-jdbc` is test-scoped; the runtime driver must reach `DriverManager` through Keystone's classloader.
3. After boot: `plugins/Bartizan/.weapon-import-done` and `plugins/Bartizan/database/bartizan.db` exist; a second boot does
   not repeat the import line.
4. `Found NBTAPI, linking...` and NO Keystone warning about a no-op NBT accessor.
5. `Item vocabulary published: bartizan` (INFO).
6. `Listener phase complete: N listener(s) registered`, `Command phase complete: N command(s) registered`; a `Plugin
   command /bartizan not declared in plugin.yml` WARN is a hard failure; a `BrigadierTabRegistrar` WARN is tolerable.
7. bStats `new Metrics(this, 0)` must not throw.
8. Persistence proof (B1): `/bartizan weapon give rifle 1`, fire, `stop`, restart, inspect `bartizan.db`'s `weapon` table
   — must not be empty after the fix.
9. Shutdown: no stage errors on `stop`; `bartizan.db`/`-wal`/`-shm` not locked afterwards on Windows.
10. `/bartizan reload` → `Reload has been completed.` with no stack trace.
11. M1 proof: killing a Citizens cop with a Bartizan gun must not broadcast a death message; a melee kill must not
    broadcast twice.
12. `/bartizan weapon|ammo|wearable|debug|reload` each render their help entries (proves `commands.json` loaded through the
    plugin classloader and the four filters partition the 14 keys).

## Not verifiable by the reviewer (no shell)

Whether `org.sqlite.JDBC` is reachable from Bartizan's `PluginClassLoader` at runtime; Maven/surefire outcomes and the
jar; byte equality of the 23 unchanged YAMLs beyond the jetpack block; the installed Keystone jar's shape; Brigadier
registration on the target build; recoil feel, NPC rhythm and cross-plugin loot-chest resolution (in-game only).
