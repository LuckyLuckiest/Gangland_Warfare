# 0.21.3 Changelog - Playtest fixes

[← Back to Documentation Index](../README.md)

Read [migration-0.21.3.md](../migration-0.21.3.md) before you start the server. It lists every hand edit an existing
server needs. Upgrade from 0.21.2. Config files are never rewritten: edit them by hand.

## Requires

- **Keystone 1.15.5** (was 1.15.4). Swap `Keystone-1.15.5.jar` into `/plugins`.
- **gangland-api 2.7 and module `Host_Api` 2.7** (unchanged).

## Changed

### Civilians

- **Hostile factions never call 911 or watch a crime.** A civilian type marked `Hostile` is no longer a caller, a
  watcher or an extra eyewitness, and a loud crime does not turn it to face you. Its own faction still reacts to a hit
  (T-549).
- **A civilian who is told to flee obeys `Flee_On_Damage`.** A type with fleeing switched off goes back to idle when its
  call ends or is stopped, instead of running.

### Quartermaster

- **He walks back to his post.** A Quartermaster pushed or lured past `Garrison.Hold_Radius` walks home: always under
  Hold orders, and under Defend once the fight is over. Hold orders no longer flip him between fighting and idle
  every second.
- **He paces his perimeter.** The shipped `quartermaster` type now wanders a few blocks around the post.
- **He comes back.** One lost to a chunk unload returns when the chunk loads. A killed one returns after
  `Powerup_Npc.Respawn_Seconds` (120; `0` = never until a restart) (T-806).

### Commands

- **Short usage.** Usage lines show a short placeholder (`<action>`, `<mode>`), never a list of choices; tab completion
  lists the choices.
- **One-line help.** Help rows are one short line, and `/glw cop help` shows one row each for `cop setup` and
  `cop spawner`.
- **A bare command always answers.** `/glw option`, `option click`, `option gang`, `option gang rank`,
  `option gang bank`, `option gang safehouse`, `filter`, `gang fight`, the arena verbs, `cop setup`, `cop spawner` and
  `cop item` now reply `Missing Arguments` with their usage instead of staying silent or saying "not implemented yet".

### Tab completion (Keystone 1.15.5)

- **The first tab after `/glw ` is never empty.** Typing `/glw ` and pressing tab lists the sub-commands again on
  Paper and other Brigadier clients (KS-CM-34).

## Known limits

- On a Brigadier client, a half-typed alias that matches no sub-command (`/glw gv`) completes nothing. A fully typed
  alias still runs and completes (`/glw gv `).
- A Quartermaster whose `Hold_Radius` is set below about 4 twitches between pacing and being recalled.
- A respawn timer is kept in memory only; a restart respawns every Quartermaster from the saved list.
