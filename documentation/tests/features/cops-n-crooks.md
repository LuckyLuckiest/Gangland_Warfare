# Cops N Crooks — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/cops-n-crooks.md) | [Developer Doc](../../developer/cops-n-crooks.md)

---

## Overview

Police NPC AI: cop spawners scale with wanted level, pursuit + detainment + jailing flow. Cops are Citizens NPCs.
Managed by `CopService`, `CopSpawnManager`, `DetainmentService`, `JailManager`. All NPC infrastructure lives in
`gangland-features/cops-n-crooks` per `feedback_npcs_in_copsncrooks`.

**Modules involved:** `gangland-features/cops-n-crooks`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Citizens plugin loaded.
- [ ] At least one cop spawner configured or created at runtime.
- [ ] `npc/cops.yml` and `cops.yml` (root) present.

---

## Smoke Test

- [ ] `/glw cop spawner set` → creates a cop spawner at your location.
- [ ] `/glw cop spawner list` → shows the new spawner.
- [ ] Raise wanted level: `/glw wanted add 3` → cops spawn from the spawner within the configured radius.
- [ ] Confirm each spawned cop NPC has `NPC.Metadata.SHOULD_SAVE = false` — run `/citizens save` and verify the
  spawned cops are **not** persisted by Citizens. Reference: `feedback_citizens_should_save_flag`.
- [ ] Cop pursues the wanted player, fires their pooled weapon.
- [ ] Cop catches up → handcuff/detainment flow begins (see [jail-detainment](./jail-detainment.md)).

---

## Edge Cases

- [ ] Spawn cops, then `kill -9` server → cops do not re-spawn as orphan Citizens NPCs on restart. Our repository
  owns them.
- [ ] Remove a spawner with active cops → active cops persist until culled; new ones do not spawn.
- [ ] Wanted player enters safe zone → cops break pursuit per config.
- [ ] Wanted player logs out mid-pursuit → cops despawn per config.

---

## Reload Safety

- [ ] `/glw reload` while cops are pursuing → cops despawn cleanly, spawners re-register, wanted level preserved.
  Reference: `project_copmanager_reload_npe` — this was a past bug; `BeanPostInitialize` fixes it.
- [ ] New cops spawn after reload when wanted level is still high.

---

## Persistence

- [ ] Spawners persist across restart.
- [ ] Active cops (Citizens entities) do **not** persist via Citizens saves — they respawn from spawner rules.
- [ ] Verified on SQLite and MySQL.

---

## Regression Risks

- `CopManager` / `CopSpawnManager` — the reload NPE that triggered the `BeanPostInitialize` contract.
- `gangland-weapon` — cops fire pooled weapons; weapon changes must not break cop gunfire.
- Wanted-level feedback loop — clearing wanted despawns cops.

---

[Back to Test Index](../README.md)
