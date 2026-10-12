# 0.19.0 Changelog - Gang & Turf II

[← Back to Documentation Index](../README.md)

Read [migration-0.19.0.md](../migration-0.19.0.md) before you start the server. It lists every new key, node, table and
behaviour change.

## Requires

- **Keystone 1.15.3** (as 0.17.0). Gangland refuses to start on an older Keystone.
- **Host_Api 2.6** for `gangland-gang`, `gangland-civilians`, `gangland-turf`, `cops-n-crooks` and `gangland-gang-fights`.
  Update all jars together.
- **Optional:** dynmap (turf layer) and WorldGuard (`glw-gang-pvp` flag, after a full restart). Neither is required.

---

## Turf

- **Guards in waves.** Guards come in waves of 3 (at most 6 standing). Survivors and unused guards return to the reserve.
- **Guard bounty, not cash.** A rival who kills a guard gets half the guard price; guards drop no civilian cash.
- **Guard roles.** `Garrison.Composition` gives guards jobs: shields block half of a frontal hit, marksmen keep range, and the
  commander's death makes the rest take cover.
- **Outlines are local.** `/glw turf show` and `/glw map border` draw only the part within 40 blocks of you.
- **Teleport guard.** Gang teleports are refused near turfs under attack, into rival turf and during a turf fight.
- **Map zoom.** `/glw map zoom in|out` sets your own zoom: 8, 16, 32 or 64 blocks per cell.
- **dynmap.** Turfs appear on a Turfs layer when dynmap is installed.
- **Civilian shouts.** Owners hear shouts when a turf is held or lost.
- **Turf create.** Refused in worlds where gangs are disabled.

## Gangs

- **Levels cost money.** A level needs held-turf XP and a bank fee: `/glw gang upgrade`, then `/glw gang upgrade confirm`.
  Set every `Cost` to `0` to keep automatic levelling.
- **Caps are grow-only.** Member, safehouse and ally caps apply to existing gangs without removing anything.
- **Ranks by minimum.** `Ranks.Minimum` grants a feature to a rank and every rank above it.
- **Safehouses and rally.** `/glw gang safehouse set|remove|list|<name>` and `/glw gang rally [safehouse]` with `join`.
  Safehouses are a `gang_safehouse` table; the number you may hold grows with the gang level.
- **Friendly fire rules.** `/glw gang pvp on|off` and `/glw gang ally pvp <gang> on|off` (works only when both sides turn it on).
  Own arrows and splash potions hurt you again (`Allow_Self_Damage`). Harmful potions are blocked between friends by default.
- **Combat tag.** Players hit by another gang are tagged for 15 seconds; a tagged player cannot leave the gang or use a gang
  waypoint.
- **Chat.** `/glw gang chat allies` talks to your gang and its allies. Staff can spy on gang and ally chat. The public chat
  tag `{glw_gang}` is off by default.
- **Notice.** `/glw gang notice <text|clear>` shows a message to members when they log in.
- **Statistics.** Gang kills, deaths and assists per member; `/glw gang profile [player]`; `/glw gang ranking [stat]` by kills,
  kdr, assists, level, members, online, turfs or fight wins.
- **Bank privacy.** Gang banks are private to the gang unless a player has `gangland.gang.balance.others`.
- **Display names.** Any language and hex colours; names that look alike are refused.
- **Console.** `/glw gang list` and `/glw gang info <gang>` work from the console. Staff tools live under `/glw option gang`,
  with every bank change logged.
- **Purge.** Off by default. `/glw option gang purge preview` lists candidates before `run`.
- **Short labels.** `/gw`, `/gwc` and `/gwa` when the gang module is installed.
- **Menus.** New `player_profile` and `gang_ranking` menus.

## Fights (new module)

- **New module `gangland-gang-fights`** (install id `fights`, needs `gang`): challenge another gang to a fight of `<size>` per
  side, with an optional bet that moves bank to bank.
- **Commands.** `/glw gang fight propose|accept|decline|enlist|drop|info|arenas|stats`.
- **Arenas.** Staff build arenas with `/glw arena create|corner|spawn|exit|size|finish|show|list|tp|on|off|delete|reload`.
  Arenas live in `plugins/Gangland_Warfare/fight/arenas.yml`.
- **Ranked fights** feed fight stats (wins, losses, draws) and the gang ranking.
- **Command limit.** During a fight a fighter may run only the fight verbs and `Guards.Allowed_Commands`.

## Fixes

- **Turf waves and bounty (TF-20, TF-35, TF-46):** guard waves and the bounty-share rules replace the all-at-once spend and the cash drop.
- **Turf visuals (TF-43, TF-21):** outlines and the map border draw only the part near you.
- **Turf civilian shouts (TF-47):** held and lost shouts use the civilian messages file.
- **Friendly fire (GR-39, GR-40, GR-41, GR-15, CJ-43):** own arrows and potions, world rules, fire and the ally switch are now enforced as documented.
- **Waypoint teleports (LS-35, LS-36):** a cancelled or world-missing warm-up clears its timer and distance.
- **Console verbs (T-227):** staff gang list and info run from the console.
- **Gang levels (US-43, US-08, GP-10):** a cancelled level-up changes nothing; the fee is taken before the level is claimed.
- **Rank gates (T-235):** the seed rank preset is written atomically or healed on boot.
- **Gang fights and commands (CM-38, T-701):** the fights command set and its help text are complete.
- **Guards (T-700):** a guard kill does not mint cash.
- **Permissions (T-702):** new parent nodes replace the flat command gates (see the migration page).

## API

- **`gangland-api` 2.6** (additive; no removal, rename or signature change):
  - `NpcRole`: a role record for NPC squads (shield, marksman, commander behaviour).
  - `GangMembershipView` defaults, `GangChatChannel`, and the `events.gangs` events.
  - `PlaceholderContribution.resolveGlobal`.
  - `GanglandMoneyDropClassifier.installFirst(NpcMoneyDropSource)`.
- 2.4 is the 0.17.0 hardening (Messages.USER_DATA_LOAD_FAILED, offline-cache API); 2.5 is reserved and unused on this branch. Full details in [gangland-api.md](../gangland-api.md).
