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

## Lose Them, Regroup and Shot Noise (0.15.0)

- [ ] Cops hunting you lose sight of you for `Lost_Sight_Seconds`: the squad radios the lost contact, the zone ring
      shows, and a star drops after `Seconds_To_Drop` out of sight (see the Wanted & Bounty checklist).
- [ ] Kill two cops in quick succession (`Regroup.Casualties` inside `Window_Seconds`): the rest pull back to cover with the
      Regroup line, radio for backup, and push together with the Regroup_Push line once all of them, backup included,
      are within `Arrival_Radius` of you (or after `Fall_Back_Seconds`). With `Cops.Backup.Enabled: false` they fall
      back and push without either line.
- [ ] `Cops.Regroup.Enabled: false`: the squad keeps fighting as in 0.13.0. A squad set to cuff first never regroups.
- [ ] Fire a Bartizan gun within 48 blocks of a cop of your squad: the squad turns to your position (counts as a sighting)
      and the nearest cop says the Shots_Fired line, at most once per 3 s.
- [ ] A shot outside `Shot_Noise.Radius`, a melee weapon, a throwable beyond 16 blocks, or a shot by a player who is not
      wanted, reveals nothing; `Shot_Noise.Enabled: false` silences all of it.
- [ ] An existing `cops.yml` without the `Regroup` / `Shot_Noise` / three new `Cooldown_Ticks` keys behaves as the shipped file.
- [ ] `npc/wanted.yml` and `npc/wanted_messages.yml` are copied on first boot and are never overwritten afterwards.
- [ ] Console shows `Runtime modules: ... loaded, 0 fault(s)` with cops-n-crooks and civilians at `Host_Api: 2.1`.

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
