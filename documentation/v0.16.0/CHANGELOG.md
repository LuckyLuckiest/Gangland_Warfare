# 🚨 Where They Come From — v0.16.0 Changelog

> *The cops know where to find you. Station dispatches, perimeter posts, hideout refuges, and the long chase home. Welcome to **Cops N Crooks 0.16**.*

---

[← Back to Documentation Index](../README.md)

---

## Overview

Cops N Crooks expands from the squad mechanic into a full police response. Crimes dispatch units from stations with an ETA, squads arrive in mixed tiers and regroup after casualties, and perimeter posts cut off escape routes while units search. Hideouts and cold trails slow the hunt when you stay unseen, bribe-star pickups and crooked contacts offer payoffs, hospitals become waypoints with a respawn shield, and the setup wand places districts, stations and posts on the map.

---

## ✨ New

- **Dispatch from stations** — Crimes call units from the nearest station in your world (or ring when none exists). ETA = clamp(distance / Unit_Speed, Min_Eta_Seconds–Max_Eta_Seconds, default 0–40 s). Rejoin grace (default 15 s) pauses the search while units arrive. Reload the server to refresh the dispatch cache.
- **Mixed-tier squads and breather** — A squad composition can specify role-at-tier entries (`Marksman@3`, `Pointman@2`, etc.) instead of one tier per star. When a squad loses every cop within Wipe_Window_Seconds (default 10 s), the replacement waits a breather (15–6 s for 1–5 stars) before arriving, with the full ETA applied after. The `Wipe_Refill` radio confirms squad lost and backup inbound.
- **Hand-off and perimeter posts** — When a cop walks out of pursuit beyond 40 blocks while the squad is not actively pursuing, hand-off biases the next dispatch ahead of the suspect within a cone (default 60 degrees, 10 s window). A 3+ star evasion spawns perimeter posts (default two, Marksman and Defender) on a ring around the search zone; posts hold the perimeter, radio sight updates and fight if attacked; they release when the suspect is spotted, all posts return, or Max_Seconds elapse (default 60 s).
- **Hideouts and cold trail** — Gang waypoints and admin regions tagged `hideout` speed the clock 2× when the suspect is inside and unseen. Cold trail speeds it further (default 0.25×/min, capped at 2×) when the suspect stays quiet (no crime, no new star, no cop sighting; offline time never counts) for every minute.
- **Crooked contacts and bribe-star pickups** — Phone desk `/glw contact [stars]` costs Price_Per_Star per star (default 1000) and clears up to Max_Stars (default 2) when no cop has seen the player recently (same cooldown as paid REMOVE signs). Bribe-star pickups sit on the map (NETHER_STAR by default) and vanish when a wanted player walks over them (unseen), clawing back 1 star each; they respawn every Respawn_Seconds (default 300 s / 5 min).
- **Hospital respawn and one bill** — Waypoints of type `HOSPITAL` become respawn points (nearest by world and distance). Downed players receive one bill charged when they respawn or quit. Hospital respawns grant Shield_Seconds of damage immunity (default 5 s); attacking anything cancels it early. Arrests charged when a downed player is brought to jail.
- **Setup wand and place names** — `/glw cop setup` places and edits stations (anchors), admin regions (districts, hideouts, restricted, breaker trigger points) and setup points on the map. PlaceNames ranks regions by horizontal footprint (smallest first); dispatch radio and `%place%` token use them. Every admin region is a PlaceRegion with source `copsncrooks`.

---

## 🔧 Changed

- **Self-defence keys and guards** — Killing a player who hit you first for ≥ 2 damage within the last 8 s is not a crime. A provocation check blocks the exemption if you struck them in the preceding 60 s. Crime-free takedowns require the bounty pool ≥ $100 + a Minimum threshold ($100 new).
- **[WANTED] sign gate** — Paid REMOVE/CLEAR signs refuse when a cop has seen the player within the last 60 s or the sign's cooldown is active. Sight is shared with the phone desk. Price-0 admin signs stay free and gate-less.
- **Bundled PLAYER cash drop off** — `items/money.yml` PLAYER Enabled is now false (one bill pays the death cost without a wallet drop). Flip it true to restore the drop.
- **Rampage counts heavy crimes only** — The AUTO drop opening counts only crimes ≥ 80 weight; cheap crimes (Brandish_Near_Cop 25, Assault 30, Car_Theft 60) cannot start a rampage alone, only make a chase less petty.

---

## 🐛 Fixed

- **WB-48** — Crime-free takedowns require a minimum bounty (`Bounty.Takedown_Minimum`, default 100); killing a target below the threshold is an ordinary crime.
- **US-33 / WB-17** — Recent-death entries are pruned on every update; the collection no longer grows without bound under repeated deaths.
- **T-180** — Spawner and jail IDs are no longer reused for rows in worlds that load after Gangland boots; each world keeps its row-id namespace, new rows take fresh IDs, and unloaded worlds' rows are unaffected.
- **risk-11** — Rampage-weighted crime count threshold enforces `Auto.Rampage_Min_Weight` default 80, fixing rampage openings from cheap crimes alone.

---

## ⚙️ Configuration

### New Files and Tables
- **`copsncrooks/setup.yml`** — Setup wand configuration: `Setup.Wand.Item`, `Setup.Outline.Particle`, `Setup.Outline.Interval_Ticks`, and message strings.
- **Database tables**: `cop_station` (id, name, world, x, y, z, yaw, jail_id), `cop_region` (id, name, world, min_x..max_z, tags), `cop_point` (id, kind, name, world, x, y, z). `cop_spawner` table gains column `station_id`.

### New Blocks in copsncrooks/cops.yml
- **`Cops.Dispatch`** — Dispatch control:
  - `Enabled: true` — Enable dispatch from stations (false = instant ring spawn, 0.15 behavior)
  - `Unit_Speed: 10.0` — Blocks per second a unit travels
  - `Min_Eta_Seconds: 0` — Minimum ETA from a station
  - `Max_Eta_Seconds: 40` — Maximum ETA from a station
  - `Station_Radius: 32.0` — Radius where spawners join a station
  - `Rejoin_Grace_Seconds: 15` — Grace period when a player rejoins before first units dispatch
- **`Cops.Breather`** — Squad respawn delay after a wipe:
  - `Enabled: true` — Enable breather delays
  - `Seconds: [15, 13, 10, 8, 6]` — Pause duration by star level (1–5 stars; others clamp to nearest)
  - `Wipe_Window_Seconds: 10` — Window in which a squad is considered "wiped" if all cops die
- **`Cops.Handoff`** — Ahead-of-target dispatch:
  - `Enabled: true` — Enable hand-off (false = no heading call)
  - `Heading_Seconds: 2` — Duration of heading calculation
  - `Bias_Seconds: 10` — Time units spawn ahead
  - `Cone_Degrees: 60.0` — Half-angle either side of heading direction
- **`Cops.Perimeter`** — Perimeter posts configuration:
  - `Enabled: true` — Enable perimeter posts
  - `Min_Level: 3` — Star level needed to spawn posts
  - `Posts: 2` — Maximum posts at once
  - `Roles: [Marksman, Defender]` — Roles to post first
  - `Max_Seconds: 60` — Longest a perimeter holds
  - `Lane_Length: 16.0` — Blocks of clear lane kept toward zone center
  - `Sight_Range: 40.0` — How far a posted cop sees
  - `Leash_Radius: 4.0` — How far a posted cop may stray
- **Six radio cooldowns** — Under `Radio.Cooldown_Ticks`:
  - `Dispatch_En_Route: 0` — Dispatch arrival announcement cooldown (ticks)
  - `Wipe_Refill: 0` — Squad-wiped/backup-inbound cooldown
  - `Handoff: 200` — Hand-off call cooldown
  - `Post_Up: 100` — Post-up cooldown
  - `Eyes_On: 60` — "Eyes on" (sight update) cooldown
  - `Returning_To_Patrol: 1200` — "Returning to patrol" cooldown

### New Blocks in copsncrooks/wanted.yml
- **`Wanted.Evasion.Hideout`** — Hideout speedup:
  - `Enable: true` — Speed clock inside hideouts
  - `Speed: 2.0` — Clock multiplier when unseen in hideout
- **`Wanted.Evasion.Quiet_Speed`** — Cold trail speedup:
  - `Enable: true` — Enable cold trail bonus
  - `Per_Minute: 0.25` — Speed gained per minute of quiet
  - `Max: 2.0` — Maximum cold trail speed cap
  - `Backup_Skip_Seconds: 60` — Quiet time after which a backup wave is skipped
- **`Wanted.Evasion.Max_Speed: 4.0`** — Overall speed cap (hideout + quiet + zone)
- **`Wanted.Auto.Rampage_Min_Weight: 80`** — Crime weight threshold for rampage opening in AUTO mode
- **`Wanted.Bribe_Stars`** — Bribe-star pickups:
  - `Enable: true` — Enable bribe-star drops
  - `Stars: 1` — Stars a pickup removes
  - `Respawn_Seconds: 300` — Time before a taken pickup returns
  - `Pickup_Radius: 1.5` — Range in which a player collects it
  - `Item: NETHER_STAR` — Pickup material

### New Blocks in copsncrooks/cop_roles.yml
- **`Squad_Composition` entries with `@<tier>` suffix** — Mix roles across tiers:
  - 3-star: `[Commander@3, Pointman@2, Defender@3, Marksman@3, Assault@2]`
  - 4-star: `[Commander@4, Pointman@3, Defender@4, Marksman@4, Medic@3, Assault@3]`
  - 5-star: `[Commander@5, Pointman@4, Defender@5, Marksman@5, Medic@4, Assault@4]` (new)

### New Lines in copsncrooks/wanted_messages.yml
- **`Bribe_Star.Taken`** — Message when a player collects a bribe-star
- **`Bribe_Star.Seen`** — Message when a cop sees a bribe-star

### New Lines in copsncrooks/cop_radio_messages(_es).yml
- **`Dispatch_En_Route`** — Dispatch arrival announcement (replaces default)
- **`Wipe_Refill`** — Squad-wiped-and-backup-inbound announcement
- **`Handoff`** — Hand-off direction announcement
- **`Post_Up`** — Perimeter-posts deployed announcement
- **`Eyes_On`** — Cop sight update announcement
- **`Returning_To_Patrol`** — Units returning to patrol after cold trail expires
- **`Unknown_Place`** — Fallback place name when no district applies to a station or last-known location

### Settings Changes (settings.yml)
- **Wanted block** gains:
  - `Self_Defence.Enable: true` — Enable self-defence exemption
  - `Self_Defence.Window_Seconds: 8` — Time to respond to incoming damage
  - `Self_Defence.Min_Damage: 2.0` — Minimum damage required to trigger exemption
  - `Self_Defence.Pair_Cooldown_Seconds: 600` — Cooldown before same pair may trigger again
  - `Contacts.Enable: true` — Enable crooked contacts
  - `Contacts.Price_Per_Star: 1000` — Cost per star to wipe
  - `Contacts.Cooldown_Seconds: 600` — Cooldown between uses
  - `Contacts.Max_Stars: 2` — Maximum stars that can be wiped per use
- **Bounty block** gains:
  - `Takedown_Minimum: 100` — Minimum escrow for crime-free takedown
- **User.Death block** gains:
  - `Hospital.Enable: true` — Enable hospital respawn
  - `Hospital.Shield_Seconds: 5` — Damage immunity at hospital respawn (0 = off)

### Message Changes (message_en.yml, message_es.yml)
- **Eight new keys**:
  - `Wanted_Level.Contact.Used` — Contact successful
  - `Wanted_Level.Contact.Seen` — Contact refused (cop has eyes on you)
  - `Wanted_Level.Contact.Cooldown` — Contact on cooldown
  - `Wanted_Level.Contact.Not_Wanted` — Contact when no stars to wipe
  - `Wanted_Level.Contact.No_Money` — Contact when player can't afford
  - `Wanted_Level.Contact.Disabled` — Contact disabled on server
  - `Death.Ward_Bill` — Bill charged at hospital respawn
  - `Death.Hospital_Shield` — Hospital protection timer display

### Other Changes
- **`items/money.yml`** — `Money.Drop_Sources.PLAYER.Enabled: false` (was true; one bill now pays the cost)
- **`cop_roles.yml`** — Five-star squad composition entry (new): `Squad_Composition: [Commander@5, Pointman@4, Defender@5, Marksman@5, Medic@4, Assault@4]`

---

## 🧩 API 2.3

- **`GanglandApi.VERSION` is 2.3** — Additive only. Owner ruling D14 allowed api 2.3 to carry `WantedCause.CONTACT`, eleven `Settings` getters and eight `Messages` constants beyond the three named surfaces (`RegionProvider`, `PlaceNames`, `WaypointType.HOSPITAL`); ruling D19 chose the 5 s hospital respawn shield (`User.Death.Hospital.Shield_Seconds`), whose getter and message are among those. Modules that declare `Host_Api: 2.2` keep loading on a 0.16.0 host. `cops-n-crooks` and `gangland-turf` declare `Host_Api: 2.3` because they implement the region SPI; they do not load on a 0.15.x core. Details in [gangland-api.md](../gangland-api.md).
- **Region SPI** — Four new types: `RegionShape` (sealed: `Cuboid`, `Sphere`), `PlaceRegion` (record: id, name, world, shape, ownerGangId, tags), `RegionProvider` (interface: `source()`, `regionsAt(Location)`), `PlaceNames` (core holder bean: register providers, query regions by location/tag/name). Core places: waypoints (GANG type, tagged hideout), admin regions (cops-n-crooks, tagged district/hideout/restricted/breaker). Turf contributes `TurfRegionProvider` at 2.3.
- **New waypoint type** — `Waypoint.WaypointType.HOSPITAL` (safe-zone waypoint for respawns).
- **New wanted cause** — `WantedCause.CONTACT` (crooked contact and bribe-star payments).
- **New Settings getters (11 total)** — `isSelfDefenceEnabled()`, `getSelfDefenceWindowSeconds()`, `getSelfDefenceMinDamage()`, `getSelfDefencePairCooldownSeconds()`, `getBountyTakedownMinimum()`, `isContactsEnabled()`, `getContactsPricePerStar()`, `getContactsCooldownSeconds()`, `getContactsMaxStars()`, `isHospitalEnabled()`, `getHospitalShieldSeconds()`.
- **New Messages constants (8 total)** — `CONTACT_USED`, `CONTACT_SEEN`, `CONTACT_COOLDOWN`, `CONTACT_NOT_WANTED`, `CONTACT_NO_MONEY`, `CONTACT_DISABLED`, `DEATH_WARD_BILL`, `DEATH_HOSPITAL_SHIELD`.
- **Keystone 1.15.0 required** — `EntitySpawner.isOutOfSight()` and `findHiddenSpawnLocation()` spawn helpers, `NpcPost` and `AbstractNpc.holdPost/tickPost/releasePost` for perimeter posts and point-of-interest holds (see [Keystone docs](../docs/phase-h14-dispatch-posts.md)).

---

## ⬆️ Upgrade notes

**Read [migration-0.16.0.md](../migration-0.16.0.md) before upgrading.**

- **Keystone 1.15.0 is required.** Gangland 0.16.0 refuses to start on Keystone 1.14.0 or older and displays a clear error message. Replace your Keystone jar first.
- **Module jars.** Replace every runtime module with the 0.16.0 build; `cops-n-crooks` and `gangland-turf` declare `Host_Api: 2.3` and do not load on a 0.15.x core.
- **Settings and message files regenerate.** On first 0.16.0 boot, your old `settings.yml` and message files are renamed to `-old` backups, and fresh files are written from the jar (as in 0.15.1). Core keys you tuned are back at defaults until you copy them from the backup; the fresh files carry the new keys below and the eight new message lines. Copy the new blocks from the backup or the jar to keep the 0.16.0 behaviour.
- **New file:** `copsncrooks/setup.yml` is copied from the module jar on first boot.
- **Database.** New tables `cop_station`, `cop_region`, `cop_point`, and a new column `cop_spawner.station_id` are added automatically. No schema breaking changes; waypoints with type `HOSPITAL` are recognized.
- **Stations must be placed before dispatch changes anything.** Use `/glw cop setup` to place a station; only then will cop spawners in `Station_Radius` (default 32 blocks) join it and units dispatch with an ETA. Until then, all worlds behave as in 0.15 (ring spawning).
- **Permissions to grant.** Grant `gangland.command.cop.setup` (admins) and `gangland.command.contact` (players for crooked contacts).
- **Dispatch disabled?** Set `Cops.Dispatch.Enabled` false to restore instant ring spawning; the default is true.
- **Hideouts and cold trail disabled?** Set `Wanted.Evasion.Hideout.Enable` or `Quiet_Speed.Enable` false; both default true.
- **Hospital respawn disabled?** Set `User.Death.Hospital.Enable` false; the default is true and adds one-bill charged on respawn even without a hospital waypoint.
- **Crooked contacts disabled?** Set `Wanted.Contacts.Enable` false to keep the old phone (no sight gate); the default is true.
- **Bounty takedown minimum.** A posted bounty must reach `Bounty.Takedown_Minimum` (default 100, new) for the kill to be crime-free (docket WB-48); existing servers should review their threshold.
- **The hospital shield is on.** `User.Death.Hospital.Shield_Seconds` default 5; set to 0 to turn it off.
- **Tier mix.** Edit `cop_roles.yml` `Squad_Composition` to mix tiers with role-at-tier entries; existing entries without `@` suffixes continue working unchanged.
- **Rampage opening weight.** In `Drop_Mode: AUTO` only crimes ≥ `Auto.Rampage_Min_Weight` (default 80) count toward a rampage opening; set to 0 for 0.15.2 behaviour (docket risk-11).
- **Player cash drop is off.** `items/money.yml` `Money.Drop_Sources.PLAYER.Enabled` now defaults to false (one death bill pays the cost). Flip it true to restore the drop.
- **Self-defence tuned.** The window is now configurable (default 8 s, was fixed at 30 s), with `Min_Damage` (2.0), a provocation check and a `Pair_Cooldown` (600 s). To loosen it, set `Window_Seconds: 30`, `Min_Damage: 0`, `Pair_Cooldown_Seconds: 0`.
- **Bartizan unchanged.** `cops-n-crooks` still requires Bartizan 0.6.0 or newer; without it, all 0.16 dispatch/posts/wand features are skipped.

