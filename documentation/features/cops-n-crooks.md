# Cops N Crooks

[← Changelog](../v0.7.5-DEV/CHANGELOG.md) | [Back to Index](../README.md) | [Next: Traders →](./traders.md)

---

## Overview

Cops N Crooks brings fully AI-driven police NPCs to your server. When a player accumulates a wanted level, police
officers spawn in the world, track the player down, and attempt to arrest them. The system is powered by the Citizens
plugin and requires it to function.

The number and strength of cops that respond scales with the player's wanted level — a low-level offender draws a couple
of rookie officers, while a five-star fugitive faces a military response.

> **Module status (0.7.5-DEV): feature-complete.** Cops N Crooks shipped across three releases:
> 0.7.3-DEV laid down the cop AI and wanted system, 0.7.4-DEV added civilians and the shared NPC base,
> and 0.7.5-DEV closes the loop with traders, a banker, and bail. See the **Related Systems** footer.

---

## How It Works

1. A player earns **wanted stars** through kills, crimes, or admin commands.
2. The system periodically checks for wanted players and spawns cops near them.
3. Cops pick up the player's trail, pursue them, and attempt a cuff-and-arrest.
4. If the arrest is successful, the player is jailed. If the player escapes or kills the cops, their wanted level decays
   over time.
5. **Dying clears your wanted level** — but so does evading the cops long enough.

---

## Cop Tiers

Cop strength is determined by the player's wanted level. Higher tiers have more health, deal more damage, move faster,
and carry better equipment.

| Tier | Name       | Health | Damage | Speed | Can Use Firearms | Skips Cuffing |
|------|------------|--------|--------|-------|------------------|---------------|
| 1    | Officer    | 20     | 2.0    | 1.0×  | No               | No            |
| 2    | Sergeant   | 25     | 3.0    | 1.1×  | No               | No            |
| 3    | Lieutenant | 30     | 4.0    | 1.2×  | Yes              | No            |
| 4    | SWAT       | 40     | 5.0    | 1.3×  | Yes              | Yes           |
| 5    | Military   | 60     | 7.0    | 1.4×  | Yes              | Yes           |

> **Skip Cuffing**: Tier 4 and 5 cops do not attempt to cuff the player first — they go straight to lethal engagement.

---

## Cop AI Behavior

Cops follow a state machine with three primary states. All cops hunting the same wanted player form one **squad** that
shares what its members see.

### Squad awareness

A cop knows where the player is only through a sighting — its own or any squad member's. A cop *sees* the player when
they are within `Alert_Range` (default 40 blocks) and in its line of sight. The crime scene counts as the first
sighting, and a player who hits a cop gives their position away to the whole squad.

- **Seen in the last 1.5 seconds:** the squad chases them. Melee cops spread around them instead of queueing behind
  each other; armed cops hold a firing spot 7–12 blocks away while they have a clear shot.
- **Out of sight:** the squad goes to where they were last seen, then fans out and searches in widening circles until
  someone spots them again.

### Pursuit

The cop is closing in to cuff the player. When the direct path fails — stairs on the far side of a building, a closed
door, a long approach — the squad plans a route starting from the player's side (a rooftop is searched from the top
down), shares it between its members, opens doors and climbs ladders on the way. If no route exists at all, the cops
wait at the foot of the structure, facing the player, for as long as they are wanted.

A cop leaves a pursuit in only two cases, and the spawner then replaces it: it stayed stuck for `Pursuit.Max_Ticks`
AI ticks while no squad member could see the player, or the player got farther away than `Pursuit.Max_Distance`.

### Combat

Within 12 blocks (ranged) or 4 blocks (melee), the cop switches to combat mode. Armed cops fire their configured weapon
with proper reload cycles: they hold position while they see the player inside their firing band and climb after them
like any other cop once they step out of view. When one cop is attacked, every cop in its group joins the fight.

### Cuffing

Lower-tier cops that reach the player attempt to cuff rather than kill. A cop makes up to 3 cuffing attempts with a
cooldown between each. If all attempts fail, the cop falls back to combat mode. Only one cop can attempt to cuff a
player at a time — the others stand by.

---

## Rosters & Backup Waves (0.12)

Since 0.12 (F4), the squad sent after a wanted player is a **mixed-tier roster** — a fixed `tier -> count` composition
per wanted star — instead of one tier sized by a formula. `Cops.Rosters` in `cops.yml` holds it, star by star; the
shipped defaults:

```yaml
Cops:
   Rosters:                                  # Who shows up per wanted star: star -> { tier: count }
      1: { 1: 2 }                            # 1-star: 2 Tier-1 (Officer)
      2: { 1: 2, 2: 2 }                      # 2-star: 2 Tier-1, 2 Tier-2 (Officer + Sergeant)
      3: { 2: 2, 3: 2 }                      # 3-star: 2 Tier-2, 2 Tier-3 (Sergeant + Lieutenant)
      4: { 3: 2, 4: 4 }                      # 4-star: 2 Tier-3, 4 Tier-4 (Lieutenant + SWAT)
      5: { 4: 3, 5: 4 }                      # 5-star: 3 Tier-4, 4 Tier-5 (SWAT + Military)
   Backup_Delay_Seconds: [15, 12, 10, 8, 6]  # Seconds before a cop lost from the roster respawns, per star
```

- A star with **no roster entry** (or a server that removes `Rosters` entirely) falls back to the pre-0.12 single-tier
  behavior: tier `min(stars, Max_Tier)` (so star *N* → tier *N* — Officer, Sergeant, Lieutenant, SWAT, Military for
  stars 1–5), sized by `Cops.Count`'s linear formula or `Formula` (see [Cop Count Scaling](#configuration) below).
  This is `CopSpawnManager#getRosterForWantedLevel`'s fallback, and it applies per star — a server can keep some
  stars on the old system and override only others.
- Either way, the group's total is clamped by `Cops.Behaviour.Max_Per_Player`; the roster clamp also accounts for cops
  already assigned from a *previous*, lower-star roster, so a star rise tops the squad up to the new roster's total
  instead of stacking a whole extra squad on top of the old one.
- **Backup waves**: a cop lost from the roster (killed, despawned, gone invalid) does not respawn on the next spawn
  check. It queues behind `Backup_Delay_Seconds[star - 1]` instead, so a chase gets a breather rather than an instant
  replacement. Several losses before the first wave lands are batched into that one wave (the delay doesn't reset per
  loss). The **initial response** when a player first becomes wanted is never delayed — only losses after that first
  spawn pass queue as backup.
- A `CopGroup` that outlives its wanted episode (its cops are still walking home when a new chase starts) resets its
  backup-wave bookkeeping for the new episode, so the new chase's initial response is never cut short by the previous
  chase's pending wave.

---

## Wanted HUD (0.12)

While wanted, and only if `Hud.Enable` is true (`cops.yml`), a player sees:

- **Boss bar** — red and full while the squad has a fresh sighting (`IN SIGHT`) or evasion isn't handling the chase
  (`WANTED`); yellow while `SEARCHING`, its progress bar counting down the evasion clock and its stars flashing
  filled/grey every second; a brief green `STAR LOST` flash (~2 seconds) whenever a star drops. Toggle:
  `Hud.Boss_Bar`.
- **Search-zone ring** — a sparse red-dust particle ring along the search zone's edge, refreshed about once a second,
  visible only to the wanted player, only while `SEARCHING`. Toggle: `Hud.Search_Zone_Ring`.
- **Escape compass** — the player's compass points to the nearest point just outside the search zone while
  `SEARCHING` (5 blocks past the edge, along the ray from zone center through the player); their previous compass
  target is restored once the chase leaves `SEARCHING` or ends. Toggle: `Hud.Escape_Compass`.
- **Star-gain feedback** — a title/subtitle flash plus a sound on every star gained (including the initial 0 → 1).
  Toggle: `Hud.Star_Gain_Title`.

The HUD is driven entirely by `EvasionService`'s state (`SEEN` / `SEARCHING` / `NONE`) — see
[Line-of-Sight Evasion](./wanted-bounty.md#how-stars-are-lost-evasion-and-decay) in the Wanted & Bounty guide — and is
shown on wanted start, refreshed on every evasion state change, and hidden on wanted end, quit, death or downed.
`%stars%` and `%time%` placeholders in `Hud.Messages` are the star string (color-coded, flashing while `SEARCHING`)
and the remaining search countdown as `m:ss`.

---

## Pursuit Report (0.12)

Every chase — from the wanted level rising off zero to it reaching zero again — is tracked (`Pursuit_Report.Enable`,
`cops.yml`) for duration, the highest star level reached, and cops killed by the player during it. When the chase
ends, its outcome is resolved as one of:

- **ESCAPED** — wanted dropped to 0★ through evasion, decay, or an admin clear, without the player ever being
  restrained.
- **BUSTED** — the player is (or was, within a 30-second grace window after being cuffed) handcuffed or jailed when
  the chase ends.
- **WASTED** — sticky once set: the player died or was downed at any point during the chase, even if the wanted level
  is later cleared some other way.

The player then gets a short chat report (`Pursuit_Report.Messages.Report`), and — if the chase's top stars reached
`Breaking_News.Min_Stars` **or** it ran at least `Breaking_News.Min_Duration_Seconds` — the whole server gets a
"breaking news" broadcast line, subject to a global `Breaking_News.Cooldown_Seconds` between broadcasts.

---

## Spawner System

Cops spawn from **spawner locations** you place in the world. When the system needs to spawn cops for a wanted player,
it looks for the nearest spawner within 80 blocks and spawns from there.

If no configured spawner is nearby, it falls back through a series of phases:

1. **Phase 1** — Attempts to find a valid location in a ring approximately 30 blocks behind the player.
2. **Phase 2** — Shrinks the search radius progressively until a valid spot is found.
3. **Global fallback** — Spawns at any valid location if all else fails.

Each spawner candidate is validated: the location must have at least two open sides and solid ground beneath it, and
must not be indoors in a way that would trap the NPC.

---

## Commands

All commands require appropriate permissions.

### Spawner Management

| Command                          | Description                                                 |
|----------------------------------|-------------------------------------------------------------|
| `/glw cop spawner set`           | Places a cop spawner at your current location.              |
| `/glw cop spawner remove <id>`   | Removes the spawner with the given ID.                      |
| `/glw cop spawner list`          | Lists all configured spawners with their IDs and locations. |
| `/glw cop spawner info <id>`     | Shows details about a specific spawner.                     |
| `/glw cop spawner teleport <id>` | Teleports you to a spawner's location.                      |

### Active Cops

| Command         | Description                          |
|-----------------|--------------------------------------|
| `/glw cop list` | Lists all currently active cop NPCs. |

---

## Configuration

Cop behavior is split across two files: `cops.yml` (tier definitions and AI tuning) and `settings.yml` (cop count
scaling).

---

### Tier Configuration (`cops.yml`)

Tiers are numbered `1` through `5` under `Cops.Tiers`. Each tier defines the stats and equipment for that cop rank.

```yaml
Cops:
   Tiers:
      1:
         Display_Name: "&9Officer"   # Name shown above the NPC (supports & color codes)
         Health: 20.0                # Max health points
         Damage: 2.0                 # Melee damage per attack
         Speed: 1.0                  # Movement speed multiplier (1.0 = normal player speed)
         Cuff_Radius: 3.0            # Blocks from target at which this tier can attempt a cuff
         Can_Use_Weapons: false      # Whether this tier fires Gangland ranged weapons
         Skip_Cuffing: false         # If true, skips cuffing entirely and goes straight to lethal combat
         Weapon_Pool: # Items the cop can carry. One is selected randomly on spawn.
            - "WOODEN_SWORD"          # Vanilla Bukkit material name
            - "weapon:rifle"          # Custom Gangland weapon — prefix with "weapon:" then the weapon name
         Helmet: ""                  # Vanilla armor material for the helmet slot (empty = none)
         Chestplate: ""              # Vanilla armor material for the chestplate slot
         Leggings: ""                # Vanilla armor material for the leggings slot
         Boots: ""                   # Vanilla armor material for the boots slot
```

`Weapon_Pool` accepts two formats:

- Plain vanilla material (e.g., `IRON_SWORD`, `CROSSBOW`) — gives the NPC that vanilla item.
- `weapon:<name>` (e.g., `weapon:rifle`) — gives the NPC a configured Gangland weapon from the `weapon/` folder.

---

### AI Settings (`settings.yml` → `Cops.Behaviour`)

```yaml
Cops:
   Behaviour:
      Max_Per_Player: 8             # Hard cap on active cop NPCs per wanted player at any time
      AI_Tick_Rate: 10              # Ticks between each AI decision cycle. Lower = faster reactions, more CPU.
      Spawn_Check_Rate: 40          # Ticks between checks that decide whether to spawn more cops
      Cuff_Radius: 3.0              # Default cuff radius in blocks (individual tiers override this)
      Max_Cuff_Attempts: 3          # Cuff attempts before the cop gives up and switches to combat
      Cuff_Cooldown_Ticks: 100      # Ticks between consecutive cuffing attempts
      Alert_Range: 40.0             # Sight range: a cop sees a wanted player this close with line of sight; shared by the squad
      Combat_Range: 4.0             # Melee attack range in blocks (ranged range is derived from this)
      Attack_Cooldown_Ticks: 20     # Ticks between melee attacks
```

---

### Spawn Settings (`settings.yml` → `Cops.Spawn`)

```yaml
Cops:
   Spawn:
      Min_Distance: 10.0            # Minimum spawn distance from the player (blocks)
      Max_Distance: 50.0            # Maximum spawn distance from the player (blocks)
      Phase1_Min_Distance: 30.0     # Target ring radius for Phase 1 (preferred, behind-player) spawn attempts
      Radius_Shrink_Step: 5.0       # How much the ring radius shrinks per Phase 2 iteration
      Vertical_Search_Range: 10     # Blocks searched above and below the player's Y to find valid ground
      Y_Offset: 0                   # Vertical offset from the player's Y when searching (0 = same level)
      Min_Open_Sides: 2             # Minimum open horizontal sides required at a spawn position
      Spawner_Preference_Radius: 80.0  # Blocks within which a placed cop spawner is preferred over a random position
      Visibility_Check_Distance: 48.0  # Distance within which nearby players trigger despawn visibility checks
      Phase1_Attempts: 20           # Number of spawn attempts in Phase 1 (preferred ring)
      Phase2_Attempts: 15           # Number of attempts per shrink step in Phase 2
```

---

### Navigation Settings (`settings.yml` → `Cops.Navigation`)

```yaml
Cops:
   Navigation:
      Recalculation_Ticks: 10       # Ticks between pathfinding path recalculations
      Stuck_Check_Interval: 5       # AI ticks between movement-progress samples for stuck detection
      Max_Stuck_Checks: 3           # Consecutive stuck samples before the cop retries pathfinding
      Max_Hopeless_Stuck_Checks: 6  # Consecutive stuck samples before navigation is considered permanently failed
      Hopeless_Close_Threshold: 8.0 # If the target is within this many blocks, a hopeless cop still tries to navigate directly
      Min_Progress_Distance: 0.75   # Minimum blocks moved between samples to count as progress (not stuck)
      Ranged_Min_Distance: 7.0      # Ranged cops hold their firing position when target is closer than this
      Ranged_Max_Distance: 12.0     # Ranged cops hold position when target is farther than this
      Min_Repath_After_Loss_Ticks: 2.0  # Minimum AI ticks before the cop re-paths after losing combat
```

---

### Pursuit Settings (`settings.yml` → `Cops.Pursuit`)

```yaml
Cops:
   Pursuit:
      Max_Distance: 80.0            # A pursuing cop farther than this from the player (blocks) is rotated out and replaced
      Max_Ticks: 120                # AI ticks a cop may stay stuck while no squad member sees the player (120 ≈ 60 s)
```

---

### Return Settings (`settings.yml` → `Cops.Return`)

```yaml
Cops:
   Return:
      Max_Ticks: 600                # AI ticks before a cop with no target is force-despawned (600 ≈ 30 s)
      Station_Arrival_Distance: 3.0 # Blocks from the spawn station at which the cop considers itself arrived and despawns
```

---

### Cop Count Scaling (`settings.yml` → `Cops.Count`)

```yaml
Cops:
   Count:
      Formula_Enabled: false        # If true, evaluates the Formula string instead of the linear calculation
      Formula: "base + (level - 1) * perLevel"
      # Custom expression when Formula_Enabled is true.
      # Available variables: level, base, perLevel, max
      Base: 2                       # Cops spawned at 1 wanted star (also the 'base' variable in the formula)
      Per_Level: 1                  # Additional cops per additional wanted star (also 'perLevel' in the formula)
      Max: 8                        # Hard cap — result is always clamped to this value

```

---

### Roster & Backup Settings (`cops.yml` → `Cops.Rosters` / `Cops.Backup_Delay_Seconds`)

New in 0.12 (F4). See [Rosters & Backup Waves](#rosters--backup-waves-012) above for behavior.

```yaml
Cops:
   Rosters:
      1: { 1: 2 }
      2: { 1: 2, 2: 2 }
      3: { 2: 2, 3: 2 }
      4: { 3: 2, 4: 4 }
      5: { 4: 3, 5: 4 }
   Backup_Delay_Seconds: [15, 12, 10, 8, 6] # Seconds before a lost roster spot respawns, per star
```

---

### Heat Ledger Settings (`cops.yml` → `Heat`)

New in 0.12 (F1). Full behavior in the [Wanted & Bounty guide](./wanted-bounty.md#how-stars-are-earned-the-heat-ledger-012).

```yaml
Heat:
   Enable: true                              # false = pre-0.12 flat kill system (Wanted.Level.Increment / Kill_Counter)
   Star_Thresholds: [100, 250, 450, 700, 1000] # Heat needed for each star; stretched linearly if too short
   Streak_Bonus: 1.5                         # Multiplier for a crime committed while the kill combo is running
   Turf_War_Multiplier: 0.5                  # Multiplier for a kill inside a CONTESTING turf (cheaper)
   Assault_Cop_Cooldown_Seconds: 10          # One Assault_Cop scored per (player, cop) pair per this many seconds
   Crimes:
      Kill_Player: 80                        # Heat for killing a player
      Kill_Civilian: 100                     # Heat for killing a civilian
      Kill_Cop: 150                          # Heat for killing a cop
      Assault_Cop: 100                       # Heat for any non-lethal damage dealt to a cop
```

---

### HUD Settings (`cops.yml` → `Hud`)

New in 0.12 (F3). Full behavior in [Wanted HUD](#wanted-hud-012) above.

```yaml
Hud:
   Enable: true                              # The whole HUD service no-ops when false
   Boss_Bar: true                            # The wanted boss bar
   Search_Zone_Ring: true                    # Red-dust particle ring while SEARCHING (wanted player only)
   Escape_Compass: true                      # Compass points outside the search zone while SEARCHING
   Star_Gain_Title: true                     # Title/subtitle + sound on every star gained
   Messages:
      In_Sight: "%stars% &c&lIN SIGHT"              # Boss bar title: fresh sighting
      Searching: "%stars% &e&lSEARCHING %time%"     # Boss bar title: SEARCHING, %time% = m:ss remaining
      Wanted: "%stars% &c&lWANTED"                  # Boss bar title: evasion not handling this chase
      Star_Lost: "%stars% &a&lSTAR LOST"             # Boss bar title flash when a star drops
      Star_Gained_Title: "&c&lWANTED"               # Title shown on every star gained
      Star_Gained_Subtitle: "%stars%"               # Subtitle shown on every star gained
```

---

### Pursuit Report Settings (`cops.yml` → `Pursuit_Report`)

New in 0.12 (F6). Full behavior in [Pursuit Report](#pursuit-report-012) above.

```yaml
Pursuit_Report:
   Enable: true                              # The whole feature no-ops when false: no report, no broadcast
   Breaking_News:
      Enable: true                           # The server-wide broadcast for a long or high-star chase
      Min_Stars: 4                           # Broadcasts when the chase's top star reaches this level...
      Min_Duration_Seconds: 120              # ...or when the chase ran at least this long (either qualifies)
      Cooldown_Seconds: 300                  # Global cooldown between broadcasts
   Messages:
      Report:                                # Sent to the player, in order, when their chase ends
         - "&8&m----------&r &c&lPURSUIT REPORT &8&m----------"
         - "&7Outcome: %outcome%"
         - "&7Duration: &f%duration%  &7Top stars: %stars%"
         - "&7Cops down: &f%cops%"
      Outcome_Escaped: "&aESCAPED"            # Colored %outcome% value for ESCAPED
      Outcome_Busted: "&9BUSTED"              # Colored %outcome% value for BUSTED
      Outcome_Wasted: "&4WASTED"              # Colored %outcome% value for WASTED
      Breaking_News: "&c&lBREAKING NEWS &7» &f%player% %outcome_plain% after a %duration% chase at %stars%&f, %cops% officers down."
```

---

## API

The main entry point for the cops system is `CopService`, accessible from the plugin context.

```java
CopService copService = gangland.getContext().get(CopService.class);

// Check if a player is being pursued
boolean pursued = copService.isBeingPursued(player);

// Manually trigger a cop spawn for a player
copService.

spawnCopsFor(player);

// Despawn all cops currently targeting a player
copService.

despawnCopsFor(player);
```

The `CopSpawnManager` handles spawner persistence:

```java
CopSpawnManager spawnerManager = gangland.getContext().get(CopSpawnManager.class);

// Get all registered spawner locations
List<CopSpawner> spawners = spawnerManager.getSpawners();
```

---

## Related Systems

Cops N Crooks is the umbrella for several NPC / economy systems that ship in the same module. Each has its own
feature doc:

- [Traders](./traders.md) — stationary shop NPCs with buy / barter / sell / tip and a per-player mood model.
- [Bank & Banker](./bank.md) — the Banker NPC and tiered bank-balance ladder.
- [Jail & Detainment](./jail-detainment.md) — handcuffs, jail, bail, bribery, and sentence timers.
- [Wanted & Bounty](./wanted-bounty.md) — how players accumulate stars and how bounties are placed and claimed.

---

[← Changelog](../v0.7.5-DEV/CHANGELOG.md) | [Back to Index](../README.md) | [Next: Traders →](./traders.md)
