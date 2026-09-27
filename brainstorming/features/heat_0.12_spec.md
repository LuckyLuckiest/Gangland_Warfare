# 0.12 "Heat" — implementation spec

Source plan: `brainstorming/features/cops_n_crooks_overhaul.html` (stage headlines, star ladder, roadmap).
Goal of the release: **escaping becomes a skill.**
Done when: a three-star chase can be shaken in under a minute by breaking line of sight, and dying is never the
cheapest way out.

Six features ship. Every one sits behind an `Enable` key and defaults must keep the plugin loadable on an existing
server (old configs without the new keys fall back to the defaults below).

Constraints (from the repo's rules):
- New cops-n-crooks settings live in the module's own config (`npc/cops.yml` or a new file inside the module jar),
  not in the host's `settings.yml`, except keys that already live under `Wanted:` in `settings.yml`.
- `gangland-api` / `gangland-core` changes must be additive (no removed or changed public signatures).
- No NMS, no packets. Stock Bukkit/Spigot 1.16.5 API (the compile floor) plus Citizens and Keystone.
- Match the surrounding code style (tabs, aligned field declarations, Javadoc density, `@ListenerHandler`).
- The build cannot run in this environment (dependency repos are blocked). Write code you are sure compiles against
  the APIs already used in the repo; don't guess at Keystone APIs you have not seen used here.

## F1. Heat ledger (replaces flat 1-point kills)

- Each crime has a heat weight; star thresholds are in heat. Defaults:
  `Star_Thresholds: [100, 250, 450, 700, 1000]`, `Streak_Bonus: 1.5` (crimes chained inside
  `Wanted.Kill_Combo.Reset_After`), `Turf_War_Multiplier: 0.5` (kills inside a contested turf, only if turf info is
  reachable without a new hard dependency; otherwise leave a TODO-free no-op and document it).
- 0.12 crimes: `Kill_Player: 80`, `Kill_Civilian: 100`, `Kill_Cop: 150`, `Assault_Cop: 100` (any damage by a
  player to a cop NPC; rate-limit so one fight cannot stack heat every hit — e.g. once per cop per N seconds).
- The `KillComboTracker.addKill(entity, points)` path already accepts points; `KillCombo.recordKill` hardcodes 1.
- Fix the known bug: `CivilianDeathRewardListener` raises wanted with `incrementLevel()` and never starts the decay
  timer, and the same kill also scores through `EntityDamageListener`. After F1, a civilian kill raises heat through
  exactly one path, the self-defence exemption (hostile NPC in COMBAT) applies on that path, and every star gain
  goes through the same code that starts timers and adds bounty (`EntityDamageListener.handleWanted` today).
- Stars gained must fire the existing wanted events so `CopManager` keeps working.

## F2. Line-of-sight evasion and search zone

- The squad's `NpcSquad.hasFreshSighting()` / last known position (via `CopGroup.getSquad()`) decides state:
  - SEEN: fresh sighting. Stars solid, evasion clock paused and reset.
  - SEARCHING: no fresh sighting for `Lost_Sight_Seconds` (3). Zone = circle on last known position,
    radius `Search_Radius[star-1]` = `[40, 60, 90, 130, 180]`. Clock counts toward `Seconds_To_Drop[star-1]` =
    `[10, 20, 30, 45, 60]`, at `Outside_Zone_Speed` (2.0) when the player is outside the zone.
  - Clock done: drop one star (`Drop_Mode: ONE_STAR`) or all (`ALL_STARS`). Re-enter SEARCHING at the new level.
- Config under `settings.yml` → `Wanted.Evasion` (Enable, Lost_Sight_Seconds, Drop_Mode, Search_Radius,
  Seconds_To_Drop, Outside_Zone_Speed, Hideout_Speed reserved for 0.14 — parse but unused is fine).
- When evasion is enabled, the old repeating decay (`WantedExecutor`) becomes a fallback only for players with no
  cop group (e.g. no cop could spawn). When disabled, behaviour is exactly as today.
- Expose state for the HUD: an `EvasionState` enum (SEEN, SEARCHING, NONE) + remaining seconds + zone center/radius,
  and fire an additive `EvasionStateChangeEvent` when it changes.

## F3. HUD: flashing stars, boss bar, escape compass

- Per-player Bukkit `BossBar` while wanted: SEEN → red, full, title `★★★☆☆ IN SIGHT`; SEARCHING → yellow,
  progress = remaining clock, stars alternate filled/grey every second, title shows `SEARCHING m:ss`;
  star lost → green `STAR LOST` for ~2 s. Removed when wanted ends, on quit, on death.
- Search-zone ring: `Player#spawnParticle` (only the wanted player sees it) of red dust along the circle edge, sampled
  sparsely (performance), refreshed ~1/s, only while SEARCHING.
- Escape compass: while SEARCHING, `Player#setCompassTarget` to the nearest point outside the zone; restore the
  previous compass target when wanted ends. (The phone unique item is a COMPASS.)
- Title flash + sound on every star gained. Message strings go through the module's message config if one exists.
- `Wanted.buildStars` exists for the star string.

## F4. Mixed squads and backup waves

- `npc/cops.yml` gains `Cops.Rosters.<star>: { <tier>: <count>, ... }`. Defaults:
  1: {1: 2}; 2: {1: 2, 2: 2}; 3: {2: 2, 3: 2}; 4: {3: 2, 4: 4}; 5: {4: 3, 5: 4}. If a star has no roster, fall back
  to today's `getTierForWantedLevel` + `getTargetCopCount`. Total still clamped by `Max_Per_Player`.
- Backup waves: when the squad drops below its roster because cops died, missing cops respawn only after
  `Backup_Delay_Seconds` per star (default `[15, 12, 10, 8, 6]`), not on the next 40-tick spawn check. Initial
  response on wanted start is unchanged.

## F5. Fines and bills instead of a chase tax

- Default `Wanted.Take_Money.Amount` to `0` in the shipped `settings.yml` (existing servers keep their value).
- Document in `settings.yml` comments that `User.Death.Money.Formula` accepts `wanted` and `bounty`, with the
  example `balance * 0.15 + wanted * 500` (don't change the shipped formula).
- Arrest fine: none new in 0.12 beyond bail — just make sure nothing else charges per star during a chase.

## F6. Pursuit report

- Track per chase (wanted start → wanted end): duration, max stars, cops killed (`CopDeathEvent`), outcome
  (ESCAPED when stars reach 0 by evasion/decay, BUSTED on cuff/jail, WASTED on death/downed).
- On end, send the player a short chat report. At max stars ≥ 4 or duration ≥ 120 s, broadcast one server-wide
  "breaking news" line, with a global cooldown (default 300 s). Config keys under the module config, `Enable` gated.

## Out of scope for 0.12

Witnesses, dispatch ETA, patrols, masks, surrender, chopper/K9/roadblocks, record, jail changes, player police.
