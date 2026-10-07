# 🚨 Where They Come From — v0.16.0 Changelog

> *The cops know where to find you. Station dispatches, perimeter posts, hideout refuges, and the long chase home. Welcome to **Cops N Crooks 0.16**.*

---

[← Back to Documentation Index](../README.md)

---

## Overview

Cops N Crooks expands from the squad mechanic into a full police response. Crimes dispatch units from stations with an ETA, squads arrive in mixed tiers and regroup after casualties, and perimeter posts cut off escape routes while units search. Hideouts and cold trails slow the hunt when you stay unseen, bribe-star pickups and crooked contacts offer payoffs, hospitals become waypoints with a respawn shield, and the setup wand places districts, stations and posts on the map.

---

## ✨ New

- **Dispatch from stations** — Crimes call units from the nearest station in your world (or ring when none exists). ETA = clamp(distance / 10 blocks/s, 0–40 s). Rejoin grace (15 s) pauses the search while units arrive. Reload the server to refresh the dispatch cache.
- **Mixed-tier squads and breather** — A squad composition can specify role-at-tier entries (`Commander@3, Pointman@2, ...`) instead of tier-per-star. When half a squad dies inside 10 s, the survivors fall back and regroup for 15–6 s (by stars); a full wipe triggers an immediate backup with a full new squad after the breather.
- **Hand-off and perimeter posts** — When a cop walks home more than 40 blocks away while the squad is not pursuing, hand-off seeds the next units ahead of the suspect with a 60-degree cone bias (10 s window). A 3+ star evasion spawns two posts (Marksman, Defender) on a ring around the search zone that hold the perimeter, radio sight updates and fight if attacked; posts release when the suspect is spotted, all posts return, or 60 s elapse.
- **Hideouts and cold trail** — Gang waypoints (with radius > 0) and districts/hideouts tagged `hideout` speed the clock 2× when the suspect is inside and unseen. Cold trail adds more speed (0.25×/min, capped at 2×) when no heat has been ledged in the last minute (crimes from signs, commands, or the kill-combo do not reset quiet).
- **Crooked contacts and bribe-star pickups** — Phone desk `/glw contact [stars]` clears 1–Max_Stars for a price per star when not in a cop's sight (same cooldown as paid REMOVE signs). Bribe-star pickups sit on the map (NETHER_STAR by default) and disappear when a wanted player walks over them (unseen), clawing back 1 star each; they respawn every 5 min.
- **Hospital respawn and one bill** — Death waypoints tagged `HOSPITAL` become respawn points (nearest by world and distance). Downed players are quoted a bill at the down and charged on recovery or quit. Hospital respawns (both paths) grant 5 s of damage immunity (the shield); attacking anything ends it early. Jailed arrests pay one charge, the bill, not a charge sheet.
- **Setup wand and place names** — `/glw cop setup` places and edits stations (anchors), admin regions (districts, hideouts, restricted, breaker trigger points) and pickup points on the map. PlaceNames queries rank regions by footprint (smallest first); dispatch radio and `%place%` token use them. Every admin region is a PlaceRegion with source `copsncrooks`.

---

## 🔧 Changed

- **Self-defence keys and guards** — Killing a player who dealt ≥ 2 damage to you in the last 8 s (and you were the first to strike) is not a crime. A provocation memory (60 s) means a killer who hit the victim before is never covered. Crime-free takedowns require the bounty pool ≥ $100 + a Minimum threshold ($100 new).
- **[WANTED] sign gate** — Paid REMOVE/CLEAR signs refuse when a cop has seen the player within the last 60 s or the sign's cooldown is active. Sight is shared with the phone desk. Price-0 admin signs stay free and gate-less.
- **Bundled PLAYER cash drop off** — `items/money.yml` PLAYER Enabled is now false (one bill pays the death cost without a wallet drop). Flip it true to restore the drop.
- **Rampage counts heavy crimes only** — The AUTO drop opening counts only crimes ≥ 80 weight; cheap crimes (Brandish_Near_Cop 25, Assault 30, Car_Theft 60) cannot start a rampage alone, only make a chase less petty.

---

## 🐛 Fixed

- **WB-48** — Crime-free takedowns now require a minimum bounty of $100 to avoid turns-into-a-murder edge cases.
- **US-33 / WB-17** — Duplicate death entries in the recent-deaths map are pruned on every charge; servers no longer grow unbounded memory from repeated deaths.
- **T-180** — Spawner and jail IDs are no longer reused for rows in worlds that load after Gangland boots. A world loaded later keeps its old rows and new rows get fresh IDs.
- Every docket ID marked fixed in the task reports (see Docket section below).

---

## ⚙️ Configuration

### New Files
- **`copsncrooks/cops.yml`** — New blocks: `Cops.Dispatch` (Enabled, Unit_Speed, Min_ETA, Max_ETA, Station_Radius, Rejoin_Grace), `Cops.Breather` (Enabled, Seconds, Wipe_Window), `Cops.Handoff` (Enabled, Heading_Seconds, Bias_Seconds, Cone_Degrees), `Cops.Perimeter` (Enabled, Min_Level, Posts, Roles, Max_Seconds, Lane_Length, Sight_Range, Leash_Radius). Moved to `copsncrooks/wanted.yml`: Evasion, Hideout, Quiet_Speed, Max_Speed, Auto.Rampage_Min_Weight, Bribe_Stars blocks.
- **`copsncrooks/setup.yml`** — Setup wand configuration: `Setup.Wand.Item`, `Setup.Outline.Particle`, `Setup.Outline.Interval_Ticks`, and message strings.
- **Database tables**: `cop_station` (id, name, world, x, y, z, yaw, jail_id), `cop_region` (id, name, world, min_x..max_z, tags), `cop_point` (id, kind, name, world, x, y, z).

### Settings Changes
- `settings.yml` **Wanted block** gains: `Self_Defence.Enable` true, `Self_Defence.Window_Seconds` 8, `Self_Defence.Min_Damage` 2.0, `Self_Defence.Pair_Cooldown_Seconds` 600; `Contacts.Enable` true, `Contacts.Price_Per_Star` 1000, `Contacts.Cooldown_Seconds` 600, `Contacts.Max_Stars` 2.
- `settings.yml` **Bounty block** gains: `Takedown_Minimum` 100.
- `settings.yml` **User.Death block** gains: `Hospital.Enable` true, `Hospital.Shield_Seconds` 5 (0 = off).
- `items/money.yml` **PLAYER entry**: `Enabled` false (was true).

### Existing Blocks Extended
- `cops.yml` `Cops.Radio.Priority` list now includes `Dispatch_En_Route` and `Wipe_Refill` (bundled defaults; servers cannot deprioritize them).
- `cops.yml` `Cops.Radio.Compass` root word `Unknown_Place` (fallback when a station/last-known position has no district).
- `npc/wanted_messages.yml` new keys: `Dispatch_Wanted` (text change), `Dispatch_En_Route`, `Wipe_Refill`, `Handoff`, `Post_Up`, `Eyes_On`, `Returning_To_Patrol`, `Contact_Used`, `Contact_Seen`, `Contact_Cooldown`, `Contact_Not_Wanted`, `Contact_No_Money`, `Contact_Disabled`, `Bribe_Star_Seen`, `Bribe_Star_Taken`, and Ward_Bill (`Death.Ward_Bill`), Hospital Shield (`Death.Hospital_Shield`).

---

## 🧩 API 2.3

- **`GanglandApi.VERSION` is 2.3** — Additive only. Modules that declare `Host_Api: 2.2` keep loading.
- **Region SPI** — `RegionShape`, `PlaceRegion`, `RegionProvider`, `PlaceNames` let any module publish geographic data (stations, turfs, hideouts). Core places: waypoints (GANG, tagged hideout), admin regions (cops, tagged by kind). Turf contributes a RegionProvider.
- **New place types** — `Waypoint.WaypointType.HOSPITAL`.
- **New cause** — `WantedCause.CONTACT` (crooked contact and bribe-star payments).
- **New settings & messages** — Self-defence, contacts, hospital, takedown minimum (Ruling R2).
- **Keystone 1.15.0** — `EntitySpawner.isOutOfSight` and `findHiddenSpawnLocation` spawn helpers. `NpcPost` and `AbstractNpc.holdPost/tickPost/releasePost` for perimeter posts and point-of-interest holds.

---

## ⬆️ Upgrade notes

- **Read the migration guide** — See [migration-0.16.0.md](../migration-0.16.0.md) before upgrading.
- **Database** — New tables for stations, admin regions and setup points. No schema breaking changes; waypoints with type name `HOSPITAL` are recognized.
- **Dispatch configuration** — Set `Cops.Dispatch.Enabled` false to use the instant ring spawning of 0.15.2.
- **Hideout and cold trail** — Both default enabled; set `Wanted.Evasion.Hideout.Enable` or `Quiet_Speed.Enable` false to disable.
- **Hospital respawn** — Set `User.Death.Hospital.Enable` false to use the configured waypoint as in 0.15.2.
- **Crooked contacts** — Set `Wanted.Contacts.Enable` false to keep the old phone (no sight gate).
- **Bounty security** — A posted bounty must reach `Bounty.Takedown_Minimum` (default 100, new) for the kill to be crime-free. Existing servers should review and raise their Minimum as intended.
- **Tier mix** — Existing `cop_roles.yml` files without role-at-tier entries continue working; edit `Squad_Composition` to mix tiers.
- **Bartizan** — `cops-n-crooks` still needs Bartizan 0.6.0 or newer; without it, the whole dispatch, posts, and wand (all 0.16 features) are skipped.

