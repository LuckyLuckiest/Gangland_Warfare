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
- [ ] `copsncrooks/cops.yml` and `cops.yml` (root) present.

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
- [ ] `copsncrooks/wanted.yml` and `copsncrooks/wanted_messages.yml` are copied on first boot and are never overwritten afterwards.
- [ ] Console shows `Runtime modules: ... loaded, 0 fault(s)` with cops-n-crooks and civilians at `Host_Api: 2.1`.

---

## Where the Police Come From (0.16.0)

Numbers S1-S16 match the acceptance scenarios; S9-S13 are in the [Wanted & Bounty](./wanted-bounty.md) checklist and
S14-S15 in the [Waypoints](./waypoints.md) checklist. Run with `Debug.Enabled: true` for module "Cops N Crooks" to see the
`DISPATCH`, `UNIT`, `PERIMETER`, `HANDOFF` and `EVASION` debug lines. Needs Keystone 1.15.0 and the `gangland.command.cop.setup`
permission.

**S1 Setup wand**
- [ ] `/glw cop setup wand` gives the wand; left click a block = pos1, right click = pos2 (one right click sets pos2 once).
- [ ] A particle outline of the selection shows to the admin holding the wand and to nobody else; it stops when the wand is not held.
- [ ] `mode station`, stand at the spot, `save Northside Station` → "Station Northside Station (#1) saved, N spawner(s) assigned".
      Saving the same name again stores nothing.
- [ ] `mode district` with pos1 + pos2 around the docks, `save Docks` → a region saved with the `district` tag.
- [ ] `list`, `list station`, `list region` print `kind id name world x y z [tags]`; `tp station 1` and `tp region 1` teleport you;
      `remove region 1` and `remove station 1` remove them (the station frees its spawners).
- [ ] `link 1 <jailId>` then `link 1 none`; an unknown station or jail id refuses.
- [ ] A player without `gangland.command.cop.setup` can use none of it, and the wand does nothing in his hand.
- [ ] Restart the server: stations, regions and points are still there (SQLite and MySQL).

**S2 Dispatch from a station**
- [ ] With a station 90 blocks away and the suspect indoors/hidden, commit a crime in the Docks: the radio says the wanted line with
      "the Docks" and "3 units en route from Northside Station, ETA 9 s" (ETA = distance / `Unit_Speed`, 0..40 s).
- [ ] Units appear only after the ETA, from a spot you cannot see (`UNIT ... fromStation=true hidden=true`).
- [ ] While the units are on the road no star drops and no search opens (`EVASION ... hold=enroute`).
- [ ] A world with no station: cops appear at once around you as in 0.15 (`station=ring eta=0s`).
- [ ] `Cops.Dispatch.Enabled: false` restores the instant ring spawn.

**S3 Mixed squads**
- [ ] At three stars the squad has mixed tiers (`UNIT ... tier=2` and `tier=3`): the Pointman/Assault tier 2, the others tier 3.
      Four and five stars use the `Commander@4` / `@5` lists. Removing the `@suffixes` from `cop_roles.yml` makes every unit the star's tier.

**S4 Breather**
- [ ] Kill every cop of a three-star squad inside 10 s: "Squad down. Backup inbound in N s." and `DISPATCH ... reason=wipe hold>0`
      (10 s + the ETA at 3 stars); the refill arrives after the hold. A wipe is counted once.
- [ ] `Breather.Enabled: false`: the refill is immediate.

**S5 Perimeter**
- [ ] At three stars break contact: `PERIMETER ... start posts=2 radius=32`, two Marksmen/Defenders walk to posts, say "Holding the corner.",
      and hold within 4 blocks; the rest of the squad searches.
- [ ] Walk into a post's line of sight: "Eyes on suspect ..." and the perimeter ends (`end reason=SIGHTED`/`CONTACT`); stay hidden
      60 s: `end reason=TIMEOUT`. A search at 1-2 stars posts nobody. `Perimeter.Enabled: false` posts nobody.
- [ ] Attack a posted cop: it drops its post and fights.

**S6 Hand-off**
- [ ] Outrun the squad until the pursuers give up and walk home: "Lost him heading <direction>. Units ahead, pick him up."
      (`HANDOFF ... heading=`), then the next `DISPATCH ... bias=true` and `UNIT ... bias=true ahead=true` spawn in the 60 degree cone
      ahead of you and report your last position, not your live one. One hand-off per bias; `Handoff.Enabled: false` skips it.

**S7 Hideout**
- [ ] Place a `hideout` region with the wand (or own a turf, or set a gang waypoint) and reach it unseen: `EVASION ... hideout=2.0`
      and the countdown runs about twice as fast. A rival gang's turf or waypoint gives no bonus (`hideout=1.0`); the hideout you were
      seen in gives none. The total never exceeds `Max_Speed`.

**S8 Cold trail**
- [ ] Stay quiet for a minute (no crime, no new star, no sighting): `EVASION ... quiet=1.25`, rising to 2.0; at 60 s "Units returning
      to patrol." and no backup wave. Offline time never counts. `Quiet_Speed.Enable: false` keeps `quiet=1.0`.

**Bribe stars** (placed with `mode pickup`)
- [ ] A floating nether star appears at the pickup point and never despawns or falls.
- [ ] Wanted and unseen, walk within 1.5 blocks: you lose 1 star ("You pocketed a police bribe star"), the item vanishes and returns
      after 300 s. With a cop who saw you recently: "Not with a cop watching.", the star stays. `Bribe_Stars.Enable: false` shows none.

**S16 Logout and rejoin**
- [ ] At one star, quit mid-chase and rejoin: the chase is restored, `DISPATCH ... reason=restore hold=15s`, `EVASION ... hold=enroute`,
      the star is still there when the first unit appears; the cops are not dropped on your spot.

**Ids**
- [ ] Start with a cop spawner and a jail in a world that is not loaded at boot (a world a multiworld plugin loads later); place a new
      spawner and a new jail → their ids do not reuse the unloaded world's ids (docket T-180); load the world → both rows present.

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
