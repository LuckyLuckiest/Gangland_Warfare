# Civilians — Test Checklist

[Back to Test Index](../README.md) | [Developer Doc](../../developer/civilians.md)

---

## Overview

Non-cop, non-trader NPCs: wanderers, bystanders, group members. Configured in `npc/civilians.yml`. Per the entity
marker plan, civilians share an abstract NPC/spawner layer with cops and traders. All civilian infrastructure lives
in `gangland-features/cops-n-crooks` per `feedback_npcs_in_copsncrooks`.

**Modules involved:** `gangland-features/cops-n-crooks`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Citizens plugin loaded.
- [ ] `npc/civilians.yml` has at least one civilian type defined and at least one group.

---

## Smoke Test

- [ ] `/glw civilian list` → shows active civilian NPCs.
- [ ] `/glw civilian groups` → shows configured groups.
- [ ] `/glw civilian spawn <typeId>` → spawns one civilian near you.
- [ ] Spawned civilian has `NPC.Metadata.SHOULD_SAVE = false` — Citizens' `saves.yml` does not grow.
- [ ] `/glw civilian spawngroup <groupId>` → spawns a whole group at your location.
- [ ] `/glw civilian spawner set` → single-type spawner created.
- [ ] `/glw civilian spawner setgroup <groupId>` → group spawner created.
- [ ] `/glw civilian spawner list` / `info <id>` / `teleport <id>` — all respond correctly.
- [ ] `/glw civilian spawner remove <id>` → spawner removed; active civilians remain until culled.

---

## Edge Cases

- [ ] Missing `typeId` → clear error, no partial spawn.
- [ ] Spawner placed in an unloaded world → null-checked per `feedback_get_world_null_check`; no crash.
- [ ] Attack a civilian → appropriate reaction (flee, wanted-level bump per policy).
- [ ] Civilian pathing near players → no lag spikes, no phantom cops spawn.

---

## Reload Safety

- [ ] Active civilians despawn cleanly on `/glw reload`; spawners re-register; new civilians spawn as expected.
- [ ] Edit `npc/civilians.yml` (change weapon pool, behaviour), `/glw reload files` → new civilians use updated
  config.

---

## Persistence

- [ ] Spawners persist across restart.
- [ ] Active civilian entities do **not** persist via Citizens; respawn from spawner rules.
- [ ] Verified on SQLite and MySQL.

---

## Regression Risks

- Shared NPC base — changes ripple to cops and traders (see [cops-n-crooks](./cops-n-crooks.md),
  [trader-shop](./trader-shop.md)).
- Weapon pool — civilians may carry weapons; verify all five action types still work for NPCs.

---

[Back to Test Index](../README.md)
