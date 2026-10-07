# Gate review B/C — Gangland 0.9.0 groups B+C (weapon + compatibility deletion, core fallout)

Reviewer R-G-BC (Opus, feature-dev `code-reviewer`, read-only), 2026-09-08 ~21:45. Reviewed executor X-G-BC's
changes on branch `0.9.0` (commit `2a3136e1`) against `gangland-0.9.0.md` groups B/C, `architecture/PICK.md` and
`REVIEW-consistency.md` ruling (a). Transcribed by the orchestrator; orchestrator rulings are marked **→**.

## Verdict: PASS WITH FIXES

The deletions are clean and complete (six poms, both trees, T-C1/C2/C3/C7 verified correct), but T-C4 removed a
death message that still had a live non-weapon path, and T-C5 left eleven `Messages` members pointing at YAML keys
it deleted.

## Findings

### Blocker

**B1 — `buildDeathMessage()` returned `null` on every path.** `listener/player/PlayerDeathListener.java` (the real
path; the checklist said `listener/death/`). In 0.8.4 the contributor walk was a preference: when no contributor
claimed the kill, the template fell back to a random `Death.Weapon` entry with `%item%` = `""`, so every
player-killer death rendered a global message. The executor's rewrite returned `null` once `killer != null`, so:
non-downed deaths (`Respawn.Enable: false`, the default) fell back to the vanilla message — a behaviour change; and
**downed deaths** (`Respawn.Enable: true`) went silent, because `CustomPlayerDeathListener` cancels the lethal
damage, `PlayerDeathEvent` never fires, `broadcastDeathMessage` was the only announcement, and Bartizan's
`EventPriority.HIGH` listener cannot fire on an event that is never raised. `downedBroadcasted` was never populated,
so the 500 ms dedup branch suppressed nothing-for-nothing. The executor followed T-C4's concrete "Do"/"Done when"
and flagged the contradiction with its "Watch out"; a reasonable call, but a user-visible regression.
**→ Ruling: restore parity with a core-owned generic list.** `Messages.DEATH_GLOBAL("Death.Global", Type.OTHER, true)`,
a `Death.Global` list in `message_en.yml` beside `Respawn` (two templates, `%killer%`/`%victim%` only — only
Bartizan can fill `%item%`), `getRandomGlobalMessage` + the substitution restored in `buildDeathMessage`, the unused
`DependencyContainer` constructor parameter dropped. Pinned by `DeathGlobalMessageTest`. The downed-path weapon name
is lost by design until Bartizan gets a hook on the downed event — filed as docket finding #22.

### Major

**B2 — eleven `Messages` members point at deleted YAML paths**, so `findMissingPaths` logs eleven warnings at every
boot: `RECEIVED_AMMO`, `RECEIVED_WEAPON`, `INVALID_AMMO`, `INVALID_WEAPON`, `INVALID_AMOUNT`, `KILLED_PLAYER`,
`GUN_NOT_IN_INVENTORY`, `GUN_BOUGHT`, `GUN_SOLD`, `WEAPON_LIST_HEADER`, `AMMO_LIST_HEADER` — all with zero consumers
repo-wide (the executor's finding #21 said ten and listed seven). Every other member's path resolves.
**→ All eleven deleted with their three section comments.**

### Minor

**B3 — leftovers:** unused imports in `PlayerDeathListener` (`Nullable` is used again after B1; `Supplier` and
`DependencyContainer` dropped), `PeriodicalUpdates`' write-only `DependencyContainer container`, `Gangland.java`'s
unused `IntSupplier`. **→ Removed.**
**B4 — stale shade comment** in `gangland-build/pom.xml` naming "NMS adapters". **→ Reworded; the
`<include>org/luckyraven/</include>` stays.**
**B5 — `gangland-item`'s `ItemListenerModuleBoundaryTest`** still names `org.luckyraven.gangland.weapon` in javadoc
and an assertion message (runtime-inert). **→ Left to group D, which owns that test's subject.**
**B6 — untracked build output** survived under both deleted trees (`target/`, `.flattened-pom.xml`, `.iml`; 380
files naming the deleted classes). **→ Both directories removed (no tracked file remained).**

### Verified clean

All poms free of `gangland-weapon`/`gangland-compatibility`/`version-impl`/`version-1_`; `gangland-build`'s
relocations, AnvilGUI comment and `org/luckyraven/` include intact; the executor's extra `version-impl` removal from
`gangland-impl/pom.xml` complete (`KernelConfig` was its only consumer; `Gangland.getViaAPI()` survives with two live
callers). T-C1, T-C2 (all five core bStats charts intact), T-C3 (`SchedulingConfig` is the only `@Bean` building
`PeriodicalUpdates`; `new PluginDataCleanupService(pluginManager)` matches), T-C5 YAML side (no `Weapon`/`Gun_`/`Ammo`
left; `Death.Respawn` survives; no duplicate key created — T-18 guard holds), `MessagesTest` edits, T-C7 (`Settings`
and `settings.yml` clean, neighbours undamaged). Core purity: zero hits for `org.luckyraven.bartizan`, NMS,
`RecoilCompatibility`, `CompatibilityWorker`, `MetricsContributor`, `DataCleanupTask`, `DeathMessageContributor`,
`DEAD_USING_WEAPON`, `Block_Regeneration`, `gangland.weapon` in `gangland-impl/src/main`, `gangland-core`,
`gangland-ui`; ruling (a) holds.

## Weapon-import inventory for groups K and L (34 lines, 15 files; the checklist's headline "35/16" is off by one)

cops-n-crooks (group K, 21 lines): `npc/NpcCombatDelegate` 5, `npc/civilian/npc/CivilianNpcFactory` 4,
`npc/police/npc/CopNpcFactory` 4, `listener/detainment/CopListener` 2, `config/CopsNCrooksModuleConfig` 1,
`npc/AbstractNpc` 1, `listener/turf/TurfFriendlyFireListener` 1, `listener/police/DetainmentListener` 1,
`listener/NpcDamageUnprotectListener` 1, `npc/police/spawn/CopSpawnManager` 1.
gangland-gadget (group L, 13 lines): `listener/car/CarDamageListener` 5, `jetpack/JetpackTask` 3,
`config/GadgetModuleConfig` 2, `jetpack/JetpackService` 2, test `jetpack/JetpackTaskConsumptionRateTest` 1.
Both `module.yml` files still declare `Depends: - weapon`; groups K/L flip them to `Plugins: [Bartizan]`.

## Not verifiable by the reviewer (no shell)

The reactor state (gate C's test-compile coupling), `MessageFileDuplicateKeysTest`/`MessagesTest` live runs (invariants
verified by hand), the cops/gadget error lists (never reached by the reactor), git tracking of the leftover output.
