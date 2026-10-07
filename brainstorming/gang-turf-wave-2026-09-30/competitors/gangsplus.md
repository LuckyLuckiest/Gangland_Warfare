# Gangs+ feature inventory (brcdev, SpigotMC 2604, v2.42.0, read 2026-09-30)

Sources, read through the owner's signed-in Chrome: the SpigotMC overview, docs.brcdev.net (gangs: commands &
permissions, fight arenas, WorldGuard integration, placeholders), and the 144 update notes (features only).
Written as our own checklist; nothing copied. Ids `GP-nn` are for the parity matrix.

Owner goal (2026-09-30): every Gangs+ feature is available in Gangland's gang system, and Gangland outclasses every
other gang plugin.

## Membership and lifecycle
- GP-01 Create a gang; rename it; disband with a confirmation step (`confirm`); admin disband.
- GP-02 Invite and cancel an invite (uninvite); join/accept by gang name; kick; leave.
- GP-03 Transfer leadership (player command) and admin set-leader.
- GP-04 Gang list (every gang, entry can show bank balance); gang info by gang name or by player name (members,
  allies, stats); `/g` with no args shows help; help lists only what you may run.
- GP-05 Configurable console commands run when a player joins, leaves or is kicked.
- GP-06 Unicode / any-language gang names; option to parse or ignore colour codes in gang names; hex colours.

## Ranks and permissions
- GP-07 Five ranks, names configurable, same ladder in every gang; promote/demote.
- GP-08 Per-feature minimum rank ("requiredRanks": which rank may invite, kick, withdraw, sethome, ally, fight...).
- GP-09 A permission node per command, wildcard nodes for all player commands.

## Levels and progression
- GP-10 `/g levelup`: the gang pays (bank or leader) to reach the next level; "amount remaining" shown when short.
- GP-11 Per-level limits: max members, max homes, chat prefix.
- GP-12 Level-up rewards (console commands) with an option to reward offline members too; level-up event.

## Chat
- GP-13 Gang chat: toggle mode and one-shot message; ally chat, same two modes.
- GP-14 Admin social spy on gang chat (per player); option to log gang and ally chat to the server log.
- GP-15 `{GANG}` token for chat-format plugins, DeluxeChat placeholders; default prefix for gangless players.
- GP-16 Chat events for other plugins (gang chat, ally chat).

## Friendly fire and PvP
- GP-17 Friendly fire toggle per gang (leader) and globally (admin); ally PvP handling.
- GP-18 Friendly fire disabled per world, with per-world overrides; WorldGuard region flag `gangs-friendly-fire`.
- GP-19 Option: friendly fire blocks only negative potion effects; option to allow self bow-boosting.

## Statistics and leaderboards
- GP-20 Player and gang stats: kills, deaths, assists, KDR, fights won/lost, WLR; profile per player (`/g player`).
- GP-21 Leaderboard `/g top`: top 10, sortable by KDR, WLR, level, members, online members, bank, members' total
  money; default order configurable.
- GP-22 Admin reset of a player's kills/deaths/assists; no stat counting in worlds where gangs are disabled.

## Bank
- GP-23 Gang bank deposit/withdraw; hide other gangs' balance unless permitted.
- GP-24 Admin bank balance/give/take/reset.

## Alliances
- GP-25 Alliance request, back to neutral (alias enemy); limit on alliances per gang; ally chat (GP-13).

## Homes
- GP-26 Multiple named gang homes: set, delete, list, teleport; safe-location check (can be disabled); max homes by
  level (GP-11); module can be switched off.
- GP-27 `/g regroup <home>`: ask every online member to regroup at a home.
- GP-28 Admin home tools: teleport to, list, delete a gang's homes.

## Gang fights (arenas)
- GP-29 Challenge another gang: team size, money bet, target gang; accept / decline; members join / leave the fight.
- GP-30 Multiple arenas (two corners + one spawn per side); players cannot leave the arena during a fight; admin
  arena create / delete / set location / set name / save / list.
- GP-31 Command blocking during a fight (list, or block all), with a bypass permission; option to require equal
  team sizes (2v2, 3v3); winner takes the bet; fights won/lost feed stats (GP-20).

## Worlds, integrations, platform
- GP-32 Disable all gang commands in chosen worlds.
- GP-33 Combat-tag plugin support (no gang escapes mid-combat).
- GP-34 PlaceholderAPI: ~25 player/gang placeholders (in gang, name, formatted name, rank and rank number, friendly
  fire, online/offline/all member lists and counts, leader, level, wins, losses, WLR, kills, deaths, KDR, bank,
  members' money) plus leaderboard placeholders `top_<stat>_<position>_<property>`; configurable default value.
- GP-35 MySQL / SQLite, periodic save, schema updater.
- GP-36 Notifications as titles; every message customisable; any language.
- GP-37 Public API: gangs list, player's gang, membership, chat toggles; events create, level-up, join, leave (with
  reason), gang chat, ally chat.
- GP-38 Admin reload.

## Announced but not shipped by Gangs+ (chances to beat it)
- GP-39 Player and gang profile GUI.
- GP-40 Auto-purge of inactive players and gangs.
- GP-41 More fight features, more events and API methods.

## From other gang plugins (beat them too)
- GP-42 Summon-all / teleport requests to the whole gang (Gangs [1.20+]).
- GP-43 Top lists by balance and by member count; member list by rank (Gangs [1.20+]).
- GP-44 Negotiated ally friendly-fire (both gangs agree) (Gangs [1.20+]).
- GP-45 Open vs invite-only join; gang MOTD (Candid Gangs).
