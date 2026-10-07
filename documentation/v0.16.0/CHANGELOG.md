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
- **Mixed-tier squads and breather** — A squad composition can specify role-at-tier entries (`Marksman@3`, `Pointman@2`, etc.) instead of one tier per star. When every cop in the squad dies within Wipe_Window_Seconds (default 10 s), the survivors fall back and regroup for 6–15 s (by stars); a full wipe triggers an immediate backup with a fresh full squad after the breather.
- **Hand-off and perimeter posts** — When a cop walks out of pursuit beyond 40 blocks while the squad is not actively pursuing, hand-off biases the next dispatch ahead of the suspect within a cone (default 60 degrees, 10 s window). A 3+ star evasion spawns perimeter posts (default two, Marksman and Defender) on a ring around the search zone; posts hold the perimeter, radio sight updates and fight if attacked; they release when the suspect is spotted, all posts return, or Max_Seconds elapse (default 60 s).
- **Hideouts and cold trail** — Gang waypoints and admin regions tagged `hideout` speed the clock 2× when the suspect is inside and unseen. Cold trail speeds it further (default 0.25×/min, capped at 2×) when no crime has been reported in the last minute (crimes from signs, commands, or the kill-combo do not reset quiet).
- **Crooked contacts and bribe-star pickups** — Phone desk `/glw contact [stars]` costs Price_Per_Star per star (default 1000) and clears up to Max_Stars (default 2) when no cop has seen the player recently (same cooldown as paid REMOVE signs). Bribe-star pickups sit on the map (NETHER_STAR by default) and vanish when a wanted player walks over them (unseen), clawing back 1 star each; they respawn every Respawn_Seconds (default 300 s / 5 min).
- **Hospital respawn and one bill** — Waypoints tagged `HOSPITAL` become respawn points (nearest by world and distance). Downed players receive one bill charged when they respawn or quit. Hospital respawns grant Shield_Seconds of damage immunity (default 5 s); attacking anything cancels it early. Arrests charged when a downed player is brought to jail.
- **Setup wand and place names** — `/glw cop setup` places and edits stations (anchors), admin regions (districts, hideouts, restricted, breaker trigger points) and setup points on the map. PlaceNames ranks regions by horizontal footprint (smallest first); dispatch radio and `%place%` token use them. Every admin region is a PlaceRegion with source `copsncrooks`.

---

## 🔧 Changed

- **Self-defence keys and guards** — Killing a player who dealt ≥ 2 damage to you in the last 8 s (and you were the first to strike) is not a crime. A provocation memory (60 s) means a killer who hit the victim before is never covered. Crime-free takedowns require the bounty pool ≥ $100 + a Minimum threshold ($100 new).
- **[WANTED] sign gate** — Paid REMOVE/CLEAR signs refuse when a cop has seen the player within the last 60 s or the sign's cooldown is active. Sight is shared with the phone desk. Price-0 admin signs stay free and gate-less.
- **Bundled PLAYER cash drop off** — `items/money.yml` PLAYER Enabled is now false (one bill pays the death cost without a wallet drop). Flip it true to restore the drop.
- **Rampage counts heavy crimes only** — The AUTO drop opening counts only crimes ≥ 80 weight; cheap crimes (Brandish_Near_Cop 25, Assault 30, Car_Theft 60) cannot start a rampage alone, only make a chase less petty.

---

## 🐛 Fixed

- **WB-48** — Crime-free takedowns require a minimum bounty threshold before the kill is crime-free; docket case, T19 docs, T20 reflects migration rule (D20).
- **US-33 / WB-17** — Duplicate recent-death entries are pruned on every charge; servers no longer grow unbounded memory from repeated deaths (both rows from the same cause).
- **T-180** — Spawner and jail IDs are no longer reused for rows in worlds that load after Gangland boots; each world keeps its row-id namespace, new rows take fresh IDs, and unloaded worlds' rows are unaffected.
- **risk-11** — Rampage-weighted crime count threshold enforces `Auto.Rampage_Min_Weight` default 80, fixing rampage openings from cheap crimes alone.

---

## ⚙️ Configuration

### New Files and Tables
- **`copsncrooks/setup.yml`** — Setup wand configuration: `Setup.Wand.Item`, `Setup.Outline.Particle`, `Setup.Outline.Interval_Ticks`, and message strings.
- **Database tables**: `cop_station` (id, name, world, x, y, z, yaw, jail_id), `cop_region` (id, name, world, min_x..max_z, tags), `cop_point` (id, kind, name, world, x, y, z). `cop_spawner` table gains column `station_id`.

### New Blocks in copsncrooks YAML Files
- **`copsncrooks/cops.yml`** gains: `Cops.Dispatch` (Enabled, Unit_Speed, Min_Eta_Seconds, Max_Eta_Seconds, Station_Radius, Rejoin_Grace_Seconds), `Cops.Breather` (Enabled, Seconds, Wipe_Window_Seconds), `Cops.Handoff` (Enabled, Heading_Seconds, Bias_Seconds, Cone_Degrees), `Cops.Perimeter` (Enabled, Min_Level, Posts, Roles, Max_Seconds, Lane_Length, Sight_Range, Leash_Radius).
- **`copsncrooks/wanted.yml`** gains: `Wanted.Evasion.Hideout.*` (Enable, Speed), `Quiet_Speed.*` (Enable, Per_Minute, Max, Backup_Skip_Seconds), `Max_Speed`, `Auto.Rampage_Min_Weight`, `Wanted.Bribe_Stars.*` (Enable, Stars, Respawn_Seconds, Pickup_Radius, Item).
- **`copsncrooks/wanted_messages.yml`** gains: `Bribe_Star.Taken`, `Bribe_Star.Seen`.
- **`copsncrooks/cop_radio_messages(_es).yml`** gains: `Dispatch_En_Route`, `Wipe_Refill`, `Handoff`, `Post_Up`, `Eyes_On`, `Returning_To_Patrol`, `Unknown_Place` (fallback when a station/last-known position has no district).

### Settings Changes
- `settings.yml` **Wanted block** gains: `Self_Defence.Enable` true, `Self_Defence.Window_Seconds` 8, `Self_Defence.Min_Damage` 2.0, `Self_Defence.Pair_Cooldown_Seconds` 600; `Contacts.Enable` true, `Contacts.Price_Per_Star` 1000, `Contacts.Cooldown_Seconds` 600, `Contacts.Max_Stars` 2.
- `settings.yml` **Bounty block** gains: `Takedown_Minimum` 100.
- `settings.yml` **User.Death block** gains: `Hospital.Enable` true, `Hospital.Shield_Seconds` 5 (0 = off).
- `items/money.yml` **PLAYER entry**: `Enabled` false (was true).

---

## 🧩 API 2.3

- **`GanglandApi.VERSION` is 2.3** — Additive only; modules that declare `Host_Api: 2.2` keep loading on a 0.16.0 host. `cops-n-crooks` and `gangland-turf` declare `Host_Api: 2.3` because they implement the region SPI; they do not load on a 0.15.x core.
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

