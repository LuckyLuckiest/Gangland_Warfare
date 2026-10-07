# Competitor gang plugins - feature notes (2026-09-30)

Our own summaries of public pages; nothing copied. Goal set by the owner: every Gangs+ feature is available in
Gangland's gang system, and Gangland outclasses every other gang plugin.

## Gangs+ (brcdev, SpigotMC 2604, premium, v2.42.0, MC 1.8-1.21)

Status: the resource page needs a SpigotMC login (premium). Chrome session unavailable; the owner is signing in to
the built-in browser pane so the description, Updates and any documentation can be read. Until then, from public
sources only:

- Public API (github.com/brcdev-minecraft/gangs-api, MIT, API 1.0.0 for Gangs+ 2.0.0+): list gangs, player's gang,
  is-in-gang, gang name / owner / members / isMember, gang-chat and ally-chat toggles; events: gang create, gang
  level up, player join gang, player leave gang (reasons: left, kicked, disbanded, disbanded by leader), gang chat,
  ally chat.
- Search snippets: command roots /gang (/g), /gangchat (/gc), /allychat (/ac), /fight, /gangadmin (/ga);
  /g help; gang-vs-gang fights: /fight challenge <gang>, accept, decline, join, leave, in admin-built arenas
  (/ga arena create, delete, setlocation, setname, save); gang bank with admin balance/give/take/reset; disband by
  admin; {GANG} tag for chat-format plugins and DeluxeChat placeholders; gang levels with configurable upgrade
  rewards; aimed at prison, GTA and PvP servers; heavy customisation.

TODO once logged in: full description, Updates changelog (features over time), config/permission docs.

## Gangs [1.20+] (ORANG3I, SpigotMC 114072, free)

Invite, set-rank per player, leave, kick, disband; friendly fire toggle inside the gang and a negotiated ally
friendly-fire toggle; gang-only and ally-only chat toggles; ally request / return to neutral; bank deposit,
withdraw, balance; multiple named bases (set, remove, tp); summon-all (teleport request to every member);
gang-vs-gang challenge with teleport requests; top 10 gangs by balance and by member count; member list; player
profile and gang profile stats; admin: set a player's gang/rank, gang bank add/remove, profiles, list members by rank.

## Gangs (CandidDevTeam, SpigotMC 66920, Skript, pre-alpha)

Create, disband, invite, join, kick, settings; join option (open vs invite only); gang MOTD. Planned (never shipped):
no friendly fire, war declarations.
