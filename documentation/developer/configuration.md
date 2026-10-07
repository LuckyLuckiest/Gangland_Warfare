# Configuration Reference

[Back to Developer Docs](./README.md)

---

## Overview

All YAML configuration files live in `gangland-impl/src/main/resources/`. The plugin uses a
config versioning system: when `Config_Version` changes (usually on plugin update), the old
file is renamed with a `-old` suffix and a fresh default is generated.

---

## Configuration Files

| File               | Purpose                                      |
|--------------------|----------------------------------------------|
| `settings.yml`     | Main runtime configuration (all systems)     |
| `cops.yml`         | Per-tier cop NPC definitions                 |
| `setup.yml`        | Cops setup wand: item, outline, messages (0.16.0) |
| `civilians.yml`    | Civilian type definitions and spawner config |
| `cars.yml`         | Car type definitions                         |
| `wearables.yml`    | Wearable armor definitions                   |
| `unique_items.yml` | Unique item definitions                      |
| `ammunition.yml`   | Ammunition type definitions                  |
| `plugin.yml`       | Spigot plugin metadata (not user-editable)   |

---

## Where module-owned settings live (0.15.1)

Since 0.15.1 a key that only one runtime module reads lives in that module's own YAML, not in `settings.yml`. The
`settings.yml` sections below for **Wanted.Kill_Combo, Cops, Detainment, Gang (except `Gang.Enable`), NPC Navigation,
Civilians, Gadgets and Turf**, and `User.Bank.Rename_Fee`, describe the pre-0.15.1 layout: the shipped file no longer
contains them. The full legacy path -> file -> path table is in [migration-0.15.1.md](../migration-0.15.1.md).

| Module | File (under `plugins/Gangland_Warfare/`) |
|---|---|
| `cops-n-crooks` | `copsncrooks/wanted.yml`, `copsncrooks/cops.yml`, `copsncrooks/detainment.yml` |
| `gangland-civilians` | `npc/civilians.yml` |
| `gangland-turf` | `turf/turf_settings.yml` |
| `gangland-gang` | `gang/gang_settings.yml` |
| `gangland-gadget` | `gadget/gadget_settings.yml` |
| `gangland-npc-shops` | `npc/banker_settings.yml` |

The `Settings` getters for these keys stay in `gangland-api` (additive contract) but are `@Deprecated` and read
`settings.yml` only.

---

## settings.yml

The main configuration file controlling all plugin systems.

### Config Versioning

```yaml
Config_Version: '0.7.4-DEV'    # Triggers file regeneration on mismatch
```

### Update Checker

```yaml
Update_Checker:
  Enable: true                   # Check SpigotMC API for updates
  Notify_Privileged_Players: false  # Notify ops on join
  Auto_Download: true            # Auto-download new versions
```

### Language

```yaml
Language: en                     # Language code for message files
```

Message files follow the pattern `message_XX.yml` in a `message/` folder.

### Resource Pack

```yaml
Resource_Pack:
  Enable: true
  URL: "https://..."             # Direct download URL
  Kick: false                    # Kick on decline
```

### Database

```yaml
Database:
   Type: sqlite                   # "mysql" or "sqlite"
   MySQL:
      Host: localhost
      Port: 3306
      Username: root
      Password: ""
   SQLite:
      Backup: true                 # Create backups
      Failed_MySQL: true           # Fallback to SQLite if MySQL fails
   Auto_Save:
      Enable: true
      Time: 10                     # Minutes between saves
      Debug: true                  # Log save performance
   Clean_Up:
      Time: 30                     # Days before old data cleanup
```

### Inventory

```yaml
Inventory:
  Fill:
    Item: BLACK_STAINED_GLASS_PANE
    Name: " "
  Line:
    Item: WHITE_STAINED_GLASS_PANE
    Name: " "
  Multi_Inventory:
    Next_Page: "base64..."
    Previous_Page: "base64..."
    Home_Page: "base64..."
```

### User

```yaml
User:
   Account:
      Initial_Balance: 0
      Maximum_Balance: 10_000_000
   Bank:
      Initial_Balance: 0
      Create_Cost: 5_000
      Maximum_Balance: 1_000_000_000
   Level:
      Maximum_Level: 100
      Base_Amount: 1_000
      Formula: "base * level ^ 1.5"
      Skill:
         Upgrade: 1
         Cost: 500
         Formula: "base * level ^ 1.8"
   Death:
      Enable: true
      Money:
         Command:
            Enable: false
            Executable:
               - "/glw eco withdraw %player% 20"
         Lose_Money: true
         Formula: "balance * 0.15"
         Threshold: 1_000
      Respawn:
         Enable: false
         Delay: 10
         Screen:
            Enable: true
            Title: "&cWASTED"
            Subtitle: "&7Respawning after &a%time%"
         GameMode:
            Change_To: "spectator"
            Allow_Fly: true
         Teleport:
            Enable: true
            Waypoint: "spawn"
         Health: 20
         Hunger: 20
      Hospital:                    # 0.16.0: respawn at the nearest HOSPITAL waypoint (/glw waypoint type <name> hospital)
         Enable: true              # false = the old respawn rules and the old death-penalty timing (charged at the down)
         Shield_Seconds: 5         # Damage immunity after a hospital respawn; attacking anything ends it; 0 = off.
                                   # A settings.yml without the key reads 5
```

### Bounty

```yaml
Bounty:
   Pay_Notoriety: false           # 0.15.0: true also pays the server-made part of a bounty on a kill
   Takedown_Minimum: 100          # 0.16.0: escrow a posted bounty needs for the kill to be a crime-free takedown (and above Minimum)
   Kill:
      Each: 5                      # Bounty added per kill
      Maximum: 50_000
   Repeating_Timer:
      Enable: true
      Multiple: 2                  # Multiplier per timer cycle
      Time: 300                    # Seconds between multiplications
      Maximum: 20_000
```

### Wanted

```yaml
Wanted:
   Enable: true
   Take_Money:
      Enable: false                # 0.15.0: off by default; a missing key counts as false
      Formula: "amount * multiplier ^ wanted"   # Price of one star drop; broken = fallback, warned once
      Amount: 50                   # Numbers for the formula only
      Multiplier: 5
   Repeating_Timer:
      Enable: true
      Time: 120                    # Default seconds between level reduction
      Multiplier:
         Enable: true
         Amount: 1.1                # time * amount ^ stars
   Level:
      Increment: 1
      Maximum: 5
   Self_Defence:                   # 0.16.0: hitting back is not a crime
      Enable: true                 # false = every kill is a crime
      Window_Seconds: 8            # how long after being hit the victim may hit back crime-free
      Min_Damage: 2.0              # weaker hits do not open the window
      Pair_Cooldown_Seconds: 600   # the same pair gets a fresh exemption only after this long
   Contacts:                       # 0.16.0: /glw contact and the [WANTED] sign, only while no cop sees you
      Enable: true
      Price_Per_Star: 1000
      Cooldown_Seconds: 600        # lying low after a paid wipe (shared by the command, the phone and the sign)
      Max_Stars: 2                 # most stars one /glw contact call wipes
   Kill_Combo:
      Enable: true
      Reset_After: 10              # Seconds of inactivity to reset combo
      Kill_Counter: # Kills needed per wanted level
         - 2
         - 5
         - 10
         - 15
         - 20
```

### Cops

```yaml
Cops:
   Count:
      Formula_Enabled: false
      Formula: "base + (level - 1) * perLevel"
      Base: 2                      # Cops at wanted level 1
      Per_Level: 1                 # Additional cops per level above 1
      Max: 8                       # Hard cap
   Behaviour:
      Max_Per_Player: 8
      AI_Tick_Rate: 10             # Ticks between AI cycles
      Spawn_Check_Rate: 40
      Cuff_Radius: 3.0
      Max_Cuff_Attempts: 3         # Cuffs broken out of, across the group, before the whole group fights (0.12.0)
      Cuff_Cooldown_Ticks: 100
      Alert_Range: 40.0            # Sight range (blocks, line of sight); every sighting is shared by the player's cops.
                                   # Ranged cops fire at anything they see within it (0.12.0)
      Combat_Range: 4.0            # Melee engage distance only (0.12.0)
      Attack_Cooldown_Ticks: 20    # Melee swing cooldown in server ticks (applied since 0.12.0)
   Spawn:
      Min_Distance: 10.0
      Max_Distance: 50.0
      Phase1_Min_Distance: 30.0
      Radius_Shrink_Step: 5.0
      Vertical_Search_Range: 10
      Y_Offset: 0
      Min_Open_Sides: 2
      Spawner_Preference_Radius: 80.0
      Visibility_Check_Distance: 48.0
      Phase1_Attempts: 20
      Phase2_Attempts: 15
   Pursuit:
      Max_Distance: 80.0           # Rotate a pursuing cop out beyond this distance from the player
      Max_Ticks: 120               # AI ticks stuck with no squad sighting before a cop is rotated out
   Return:
      Max_Ticks: 600
      Station_Arrival_Distance: 3.0
```

### Detainment

```yaml
Detainment:
  Jail:
    Max_Capacity: 10
```

### Gang

```yaml
Gang:
   Enable: true
   Name_Duplicates: false
   Display_Name_Char: '*'
   Rank:
      Head: "member"               # Initial rank
      Tail: "owner"                # Final rank
   Account:
      Initial_Balance: 0
      Create_Cost: 100_000
      Maximum_Balance: 100_000_000_000
      Contribution_Rate: 1_000
```

### Economy

```yaml
Money_Symbol: '$'
Balance_Format:
  Enable: true
  Format: "%,.2f"
```

### NPC Navigation

```yaml
NPC_Navigation:
  Recalculation_Ticks: 10
  Stuck_Check_Interval: 5
  Max_Stuck_Checks: 3
  Max_Hopeless_Stuck_Checks: 6
  Hopeless_Close_Threshold: 8.0
  Min_Progress_Distance: 0.75
  Ranged_Min_Distance: 7.0
  Ranged_Max_Distance: 12.0
  Min_Repath_After_Loss_Ticks: 2
```

### Civilians

```yaml
Civilians:
  Behaviour:
    Enabled: true
    AI_Tick_Rate: 20
  Spawn:
    Min_Distance: 10.0
    Max_Distance: 50.0
    Phase1_Min_Distance: 30.0
    Radius_Shrink_Step: 5.0
    Vertical_Search_Range: 10
    Y_Offset: 0
    Min_Open_Sides: 2
    Spawner_Preference_Radius: 80.0
    Visibility_Check_Distance: 48.0
    Phase1_Attempts: 20
    Phase2_Attempts: 15
  Spawner_Proximity:
    Activation_Radius: 60.0
    Despawn_Radius: 80.0
    Max_Npcs_Per_Spawner: 5
    Check_Interval: 100
    Default_Type_Id: ""
```

### Loot Chest

```yaml
Loot_Chest:
  Countdown_Timer: 300           # Seconds before chest opens
  Sound:
    Opening: "BLOCK_CHEST_OPEN"
    Locked: "BLOCK_CHEST_LOCKED"
    Closing: "BLOCK_CHEST_CLOSE"
  Allowed_Blocks:
    - "CHEST"
    - "TRAPPED_CHEST"
    - "BARREL"
    - "SHULKER_BOX"
    - "ENDER_CHEST"
  Rewards:
    Money:
      Minimum: 10
      Maximum: 1_000
    Experience:
      Minimum: 5
      Maximum: 100
    Commands:
      - ""
```

### Gadgets

```yaml
Gadgets:
  Jetpack:
    Thrust_Ramp_Ticks: 20
    Descent_Accel: 0.022
    Max_Descent_Speed: -0.5
    Horiz_Influence: 0.03
    Max_Horiz_Speed: 0.25
  Car:
    Reverse_Speed_Ratio: 0.5
    Hard_Brake_Multiplier: 3.0
    Fuel_Consume_Per_Tick: 1
```

---

## cops.yml

Defines the cop tiers and, since 0.12.0, the squad tactics, melee band, police radio, backup and retreat tuning;
ships inside the cops-n-crooks module jar as `copsncrooks/cops.yml`. Abridged:

```yaml
Cops:
   Melee:
      Reach: 3.0
      Approach: 2.0
      Damage_Spread: 0.15
      Edge_Damage: 0.7
   Tactics:
      Enabled: true
      Formation_Arc: 270.0
      Strafe_Degrees: 15.0
      Reposition_Ticks: 60
      Moving_Aim_Error: 0.10
   Radio:
      Enabled: true
      Range: 32.0
      Target_Range: 64.0
      Squad_Gap_Ticks: 30
      Player_Gap_Ticks: 20
      Ack_Delay_Ticks: 25
      Responder_Max: 2
      # Priority, Cooldown_Ticks and Sound: see the shipped file
   Backup:
      Enabled: true
      Extra_Cops: 1
      Duration_Ticks: 600
      Cooldown_Ticks: 1200
   Retreat:
      Enabled: true
      Health_Fraction: 0.3
      Radius: 12.0
   Names:                            # (0.13.0)
      Format: "%rank% &f%name% &7#%badge%"
      First_Names:
         - Bob
         - Kate
   Stuck:
      Enabled: true
      Recycle_Seconds: 12
      Avoid_Spawner_Seconds: 60
   Tiers:
      4:
         Display_Name: "&1SWAT"
         Health: 40.0
         Damage: 5.0
         Speed: 1.3
         Cuff_Radius: 4.5
         Can_Use_Weapons: true
         Skip_Cuffing: true
         Difficulty: HARD
         Fire_Rate_Multiplier: 0.1
         Weapon_Pool:
            - "weapon:rifle"
            - "CROSSBOW"
         Wearables:
            Helmet: "IRON_HELMET"
         Tactics:
            Formation_Arc: 270.0
```

| Key | Default | Meaning |
|---|---|---|
| `Melee.*` | see above | Swing reach, surround distance (capped at `Cuff_Radius - 0.5`), damage spread and edge falloff |
| `Tactics.*` | see above | Formation arc, strafe, reposition interval and moving aim error; `Enabled: false` = 0.11 positioning only (radio, backup, retreat keep their own switches) |
| `Tiers.<n>.Tactics` | none | Overrides `Tactics` key by key. Shipped arcs: Lieutenant 200, SWAT 270, Military 330 |
| `Tiers.<n>.Fire_Rate_Multiplier` | `1 / AI_Tick_Rate` | Gun cadence as a fraction of the weapon's own rate; the default keeps 0.11's cadence |
| `Radio.*` | see above | Police radio in chat: who hears it, throttling, ack delay, `Responder_Max` cross-group responders |
| `Backup.*` | see above | Extra cops requested when a cop goes down, for how long, and how often |
| `Retreat.*` | see above | When a hurt cop breaks off to cover, and how far it looks |
| `Dispatch.Enabled` | `true` | 0.16.0. Units leave the nearest station and arrive after an ETA. `false` = the 0.15 instant ring spawn |
| `Dispatch.Unit_Speed` | `10.0` | Blocks per second a unit covers (at least 0.1) |
| `Dispatch.Min_Eta_Seconds` / `Max_Eta_Seconds` | `0` / `40` | ETA = horizontal distance / `Unit_Speed`, clamped, rounded up. A max below the min is clamped to the min |
| `Dispatch.Station_Radius` | `32.0` | Spawners within this of a station belong to it (`/glw cop spawner set`, saving a station) |
| `Dispatch.Rejoin_Grace_Seconds` | `15` | Seconds after a wanted player rejoins before his first units are sent (ETA on top; the clock holds meanwhile). A logout drops the queue; the reappearance spot is not seeded |
| `Breather.Enabled` | `true` | 0.16.0. `false` = a wiped squad is refilled at once |
| `Breather.Seconds` | `15, 13, 10, 8, 6` | Pause after a wipe for 1 to 5 stars (other levels clamp to the nearest end); a non-number entry makes the whole list default |
| `Breather.Wipe_Window_Seconds` | `10` | A squad is wiped when it loses every cop inside this |
| `Handoff.Enabled` | `true` | 0.16.0. `false` = no heading call and no ahead-of-him spawns |
| `Handoff.Heading_Seconds` | `2` | Seconds of the suspect's movement used for the heading |
| `Handoff.Bias_Seconds` | `10` | How long new units spawn ahead of him (also how much slower a station ahead may be and still win) |
| `Handoff.Cone_Degrees` | `60.0` | Half-angle either side of the heading |
| `Perimeter.Enabled` | `true` | 0.16.0. `false` = no posts |
| `Perimeter.Min_Level` | `3` | Stars needed |
| `Perimeter.Posts` | `2` | Most cops posted at once; the last free cop is never posted |
| `Perimeter.Roles` | `Marksman, Defender` | Roles posted first |
| `Perimeter.Max_Seconds` | `60` | The longest a perimeter holds |
| `Perimeter.Lane_Length` | `16.0` | Blocks of clear lane kept toward the centre of the zone |
| `Perimeter.Sight_Range` | `40.0` | How far a posted cop sees; the ring radius is the smaller of the zone radius and 0.8 x this |
| `Perimeter.Leash_Radius` | `4.0` | How far a posted cop may stray (a post never uses less than 1.5) |
| `Radio.Cooldown_Ticks.Dispatch_En_Route` / `Wipe_Refill` / `Handoff` / `Post_Up` / `Eyes_On` / `Returning_To_Patrol` | `0` / `0` / `200` / `100` / `60` / `1200` | 0.16.0 repeat cooldowns of the new radio lines |
| `Radio.Priority` | see the file | `Dispatch_En_Route` and `Wipe_Refill` are always added to the list a server file carries (they must not be swallowed by the gap after `Dispatch_Wanted`) |
| `Regroup.Enabled` | `true` | 0.15.0. `false` = no pull-back after casualties |
| `Regroup.Casualties` | `2` | Cops lost inside `Window_Seconds` that trigger the pull-back |
| `Regroup.Window_Seconds` | `20` | The window for those casualties |
| `Regroup.Fall_Back_Seconds` | `15` | Longest stay in cover before the squad pushes anyway |
| `Regroup.Cooldown_Seconds` | `60` | One regroup per squad per this long |
| `Regroup.Arrival_Radius` | `24.0` | Blocks; the squad pushes together once all of it, backup included, is this close to the suspect, or when `Fall_Back_Seconds` runs out. The regroup grants backup itself when none is active |
| `Shot_Noise.Enabled` | `true` | 0.15.0. `false` = shots never reveal the shooter |
| `Shot_Noise.Radius.<TYPE>` | `GUN` 48, `THROWABLE` 16, `MELEE` 0 | Blocks a cop hears a Bartizan weapon of that category; `0` or an unlisted type = silent |
| `Radio.Cooldown_Ticks.Regroup` / `Regroup_Push` / `Shots_Fired` | `1200` / `1200` / `60` | 0.15.0 per-kind repeat cooldowns. None needs a `Radio.Priority` entry: the two regroup lines bypass the gaps; `Shots_Fired` respects them (the squad still converges on the shot), so add it to `Priority` if it must always be heard |
| `Names.Format` | `%rank% &f%name% &7#%badge%` | The callsign above the cop's head and on the radio. `%rank%` = the tier's `Display_Name`, `%badge%` = 1000 + the Citizens id, optional `%role%` = the squad role's `Display_Name` (empty, with its colour code and the doubled space dropped, for a cop with no role; the default `Format` has none, e.g. `%rank% &e%role% &f%name% &7#%badge%` reads `Officer Medic Bob #1592`). The Citizens name itself is the short plain `Bob #1592` |
| `Names.First_Names` | 28 built-in names | First-name pool; `[]` = no first name, a missing key = the built-in pool |
| `Stuck.*` | see above | Optional (0.13.0). A cop that has found no way to its player for `Recycle_Seconds` (at least 1), out of his view (cone plus clear line within `Cops.Spawn.Visibility_Check_Distance`, never under 24 blocks; past twice `Recycle_Seconds` within 24 blocks only), unseen by other players (past twice `Recycle_Seconds` the same 24-block view rule) and outside melee reach on his level with a clear line, is replaced; its spawner is skipped for `Avoid_Spawner_Seconds` (0 = never). `Enabled: false` never replaces |

The radio lines are in `copsncrooks/cop_radio_messages.yml` (Spanish `_es.yml`). See
[Cops N Crooks](../features/cops-n-crooks.md) and [Migrating to 0.12.0](../migration-0.12.0.md).

---

## wanted.yml (`copsncrooks/wanted.yml`)

New in 0.15.0; ships inside the cops-n-crooks module jar and is copied to `plugins/Gangland_Warfare/copsncrooks/` on first boot.
Every key falls back to the value shown, a bad value is reported once and defaulted, and each feature's `Enable: false`
restores the 0.13.0 behaviour of that piece. Texts are in `copsncrooks/wanted_messages.yml` (below).

| Key | Default | Meaning |
|---|---|---|
| `Wanted.Heat.Enable` | `true` | `false` = no heat ledger; stars rise by the kill combo or one per counted kill |
| `Wanted.Heat.Star_Thresholds` | `100, 250, 450, 700, 1000` | Heat needed for star 1, 2, 3 ...; a shorter list is stretched to the max level |
| `Wanted.Heat.Streak_Bonus` | `1.5` | Multiplier for a crime inside `copsncrooks/wanted.yml` `Wanted.Kill_Combo.Reset_After` of the last; only while `Wanted.Kill_Combo.Enable` there is true |
| `Wanted.Heat.Seen_By_Cop_Multiplier` | `1.5` | Multiplier when a cop saw the crime |
| `Wanted.Heat.Turf_War_Multiplier` | `0.5` | Multiplier for a player kill inside a contested turf |
| `Wanted.Heat.Assault_Repeat_Seconds` | `10` | Hitting the same cop again inside this is not a new crime |
| `Wanted.Heat.Crimes.<Id>` | see below | Heat per crime. Merged key by key over the defaults |
| `Wanted.Evasion.Enable` | `true` | `false` = only the fixed decay timer |
| `Wanted.Evasion.Lost_Sight_Seconds` | `3` | No cop sighting for this long opens the search zone |
| `Wanted.Evasion.Drop_Mode` | `ONE_STAR` | `ONE_STAR`, `ALL_STARS` or `AUTO` per completed evasion; an unknown value warns and uses `ONE_STAR` |
| `Wanted.Evasion.Auto.*` | *see below* | AUTO mode only: the cops judge how the chase went and take 1, part or all stars (29 tunable keys under `Auto`, if missing uses defaults) |
| `Wanted.Evasion.Search_Radius` | `40, 60, 90, 130, 180` | Zone radius in blocks by wanted level |
| `Wanted.Evasion.Seconds_To_Drop` | `10, 20, 30, 45, 60` | Seconds hidden for a drop, by wanted level |
| `Wanted.Evasion.Outside_Zone_Speed` | `2.0` | Clock speed outside the zone |
| `Wanted.Evasion.Hideout.Enable` / `Speed` | `true` / `2.0` | 0.16.0. Clock speed while searching inside a hideout (a gang's turf, a gang waypoint, an admin `hideout` region) that is not the one he was seen in; a rival gang's hideout never counts |
| `Wanted.Evasion.Quiet_Speed.Enable` | `true` | 0.16.0. The cold trail |
| `Wanted.Evasion.Quiet_Speed.Per_Minute` / `Max` | `0.25` / `2.0` | Speed added for each full quiet minute (no crime, no new star, no cop sighting; offline time never counts), and its ceiling |
| `Wanted.Evasion.Quiet_Speed.Backup_Skip_Seconds` | `60` | After this much quiet the next backup wave is skipped and the squad says `Returning_To_Patrol` |
| `Wanted.Evasion.Max_Speed` | `4.0` | Cap on the zone, hideout and quiet speeds multiplied together (below 1 resets to 4.0) |
| `Wanted.Evasion.Auto.Rampage_Min_Weight` | `80` | 0.16.0, AUTO only. A crime counts toward the rampage opening only at this heat weight or more (the shipped cheap crimes never do) |
| `Wanted.Bribe_Stars.Enable` | `true` | 0.16.0. `false` = no pickups |
| `Wanted.Bribe_Stars.Stars` | `1` | Stars a pickup takes (at least 1) |
| `Wanted.Bribe_Stars.Respawn_Seconds` | `300` | Seconds before a taken pickup returns |
| `Wanted.Bribe_Stars.Pickup_Radius` | `1.5` | Blocks within which a player takes it (zero or less resets to 1.5) |
| `Wanted.Bribe_Stars.Item` | `NETHER_STAR` | The pickup's material (an unknown name uses `NETHER_STAR` with a warning) |
| `Wanted.Hud.Boss_Bar.Enable`, `Star_Card.Enable`, `Title.Enable`, `Zone_Ring.Enable`, `Compass.Enable` | `true` | Each switch removes only its own piece |
| `Wanted.Hud.Siren.Enable` / `Sound` / `Volume` / `Pitch` | `true` / `BLOCK_NOTE_BLOCK_BELL` / `1.0` / `0.5` | The siren when stars rise |
| `Wanted.Hud.Zone_Ring.Particle` / `Points` | `DUST` / `48` | The ring around the search zone; an unknown particle uses `DUST` |
| `Wanted.Charge_Sheet.Enable` | `true` | `false` = no fine |
| `Wanted.Charge_Sheet.Base` / `Per_Wanted_Level` / `Maximum` | `200` / `250` / `10000` | Fine = `Base + Per_Wanted_Level x stars`, capped; paid from the wallet only |
| `Wanted.Charge_Sheet.Seconds_Per_Unpaid` / `Max_Extra_Seconds` | `0.1` / `600` | Unpaid money becomes extra jail seconds, capped |

Shipped crime weights (`Heat.Crimes`): `Brandish_Near_Cop` 25, `Assault_Civilian` 30, `Car_Theft` 60, `Kill_Player` 80,
`Kill_Civilian` 100, `Assault_Cop` 100, `Resisting_Arrest` 100, `Kill_Cop` 150, `Safe_Cracking` 150, `Store_Robbery` 200,
`Trespass_Restricted` 300, `Jailbreak` 450. 0.15.0 reports `Kill_Player`, `Kill_Civilian`, `Kill_Cop`, `Assault_Cop`
and `Resisting_Arrest`; the others are read by later releases. A weight of 0 ignores that crime.

`copsncrooks/wanted_messages.yml` also holds (0.16.0) `Bribe_Star.Taken` (`%stars%`) and `Bribe_Star.Seen`, and holds `Hud.Bar.Seen` / `Searching` / `Evaded` / `Evaded_Many`, `Hud.Title`, `Hud.Card.Raise` /
`Stance_Cuffs` / `Stance_Shoot` / `Drop_Evasion` / `Drop_Decay` / `Drop_Other` / `Drop_Petty` / `Drop_Cold_Trail` / 
`Drop_Clean_Break` / `Drop_Still_Hot` / `Drop_Known_Face` / `Drop_Narrow`, `Charge_Sheet.Header` / `Crime` / `Total` /
`Paid` / `Extra_Time` / `Paperwork`, and `Crimes.<Id>` (including `Crimes.Unknown_Crime`, the card text when no crime is
on record). AUTO ending cards (`Drop_*`) are new in 0.15.2. Placeholders: `%stars%`, `%time%`, `%crime%`, `%tier%`, `%stance%`, `%count%`, `%amount%`, `%paid%`,
`%money_symbol%`, and `%count%` fills the star-drop lines.

#### AUTO mode settings

When `Wanted.Evasion.Drop_Mode: AUTO`, the cops decide how many stars to drop based on how the chase went. 29 tunable keys live under `Wanted.Evasion.Auto` in `copsncrooks/wanted.yml`. If the block is missing, all keys use their defaults. The mode reads:
1. **How the chase started** — rampage (4+ opening crimes, 4+ peak stars, or a cop killed) or small fry (1-2 crimes, max 2 stars).
2. **How long was the chase** — wall-clock seconds since the chase began (with offline time removed), compared to a learned typical getaway length per star level.
3. **After a specific period** — the lock lifts after 180 s with no new crime; a cold trail needs 90 s quiet. Logging out during a chase locks it until quiet is restored.
4. **How it might end** — did you leave the search zone (clean break), sit tight (hunker down), or slip after a long pursuit (narrow escape, faster next timer).

Five **endings** map these rules to 1, part or all stars: **Still Hot** (1, a rampage or logout lock), **Petty** (all, a small chase), **Cold Trail** (all, a long quiet chase), **Clean Break** (half, leaving the zone), **Hunker Down** (1, the default). The `Learning` sub-block (11 keys) lets the server track how long chases typically last and how often each player gets away, then adjusts timers and denies small-fry lumps to repeat offenders.

All keys are under `Wanted.Evasion.Auto`. A missing key uses its default; a value outside its range is reported (`config.range`) and the default is used instead. In the lists, a `Typical_Seconds` entry below 5 uses the entry before it (the default for the first), and an `Escape_Rate` entry outside 0 to 1 is clamped.

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `Opening_Seconds` | `30` | >= 0 | Seconds from the first crime of the chase that count as its opening. A crime from more than this long before the chase began is not on the chase |
| `Rampage_Crimes` | `4` | >= 1 | Crimes in the opening that make the chase a rampage |
| `Rampage_Peak_Level` | `4` | >= 1 | Peak stars that make a chase a rampage (a cop kill always does) |
| `Lock_Cool_Seconds` | `180` | >= 0 | A rampage or logged-out chase drops one star at a time until this many quiet seconds pass |
| `Respot_Limit` | `4` | >= 0 | Respots after which PETTY and CLEAN_BREAK stop paying out |
| `Petty.Max_Crimes` | `2` | >= 1 | Most crimes a small chase may hold (a chase with none never counts) |
| `Petty.Max_Peak_Level` | `2` | >= 1 | Highest level a small chase may reach; at or above `Rampage_Peak_Level` warns (`config.conflict`) |
| `Cold_Trail.Typical_Seconds` | `30, 60, 90, 120, 150` | each >= 5 | Fresh-server typical getaway seconds by peak level; Learning replaces it within half to double |
| `Cold_Trail.Ratio` | `2.0` | >= 0.01 | The chase must last this many times the typical time |
| `Cold_Trail.Quiet_Seconds` | `90` | >= 0 | Seconds with no new crime and no new star |
| `Clean_Break.Outside_Ratio` | `0.5` | 0 to 1 | Share of the search time spent outside the zone |
| `Clean_Break.Drop_Fraction` | `0.5` | 0 to 1 | Share of the stars that drop, rounded up, at least one |
| `Momentum.Step_Speed` | `0.75` | 0.01 to 1 | Each later timer is this share of the previous one |
| `Momentum.Narrow_Step_Speed` | `0.5` | 0.01 to 1 | The same after a narrow escape; above `Step_Speed` warns and uses `Step_Speed` |
| `Momentum.Narrow_Seen_Seconds` | `20` | >= 0 | Seconds in sight before breaking away that make it a narrow escape |
| `Momentum.Floor` | `0.4` | 0.01 to 1 | No timer shrinks below this share of `Seconds_To_Drop` |
| `Repeat_Chases` | `3` | 0 to 8 | Recent crime chases at which the cops stop going easy (0 = off) |
| `Repeat_Window_Minutes` | `30` | >= 0 | How far back those chases count (memory only) |
| `Learning.Enable` | `true` | | `false` = nothing stored or read |
| `Learning.Escape_Rate` | `0.90, 0.75, 0.55, 0.35, 0.20` | each 0 to 1 | Fresh-server getaway rate by peak level |
| `Learning.Prior_Chases` | `5` | >= 1 | Chases of evidence before a player's habit counts fully |
| `Learning.Decay_Per_Chase` | `0.90` | 0.5 to 1 | How much an older chase still counts after each newer one |
| `Learning.Habitual_Escaper_Delta` | `0.20` | 0.05 to 1 | Habit at which a player is a known face (no small-fry or clean-break lumps) |
| `Learning.Habit_Time_Strength` | `0.8` | 0 to 2 | How strongly the habit stretches or shortens the timer |
| `Learning.Min_Time_Factor` | `0.6` | 0.1 to 1 | The habit alone never shortens a timer below this share |
| `Learning.Max_Time_Factor` | `1.6` | 1 to 4 | No timer is stretched above this share of `Seconds_To_Drop` |
| `Learning.Min_Chase_Seconds` | `30` | >= 0 | Shorter chases are not learned from |
| `Learning.Min_Seconds_Between_Outcomes` | `180` | >= 0 | A player's chases ending closer together are not learned from |
| `Learning.Forget_After_Days` | `90` | >= 1 | Habit and level rows untouched this long are deleted at startup |

---

## Other 0.16.0 files and keys

### setup.yml (`copsncrooks/setup.yml`)

New in 0.16.0; ships inside the cops-n-crooks jar and is copied to `plugins/Gangland_Warfare/copsncrooks/` on first boot
(it is in `CopsNCrooksYamlConfig.FILES`). It configures the `/glw cop setup` wand; the shipped file lists every message
with its placeholders, so copy key names from it.

| Key | Default | Meaning |
|---|---|---|
| `Setup.Wand.Item` | `BLAZE_ROD` | The wand's material (an unknown name uses `BLAZE_ROD`) |
| `Setup.Outline.Particle` | `DUST` | The particle of the selection outline, shown only to the admin holding the wand (an unknown name uses `DUST`) |
| `Setup.Outline.Interval_Ticks` | `10` | Ticks between two draws |
| `Setup.Messages.*` | see the file | Every wand and command message (`Usage`, `Wand_Given`, `Mode_Set`, `Pos_Set`, `Station_Saved`, `Region_Saved`, `Point_Saved`, `Removed`, `Linked`, ...), `&` colour codes, `%placeholders%` |

### cop_roles.yml `Squad_Composition`

An entry is `"<Role>"` or `"<Role>@<tier id>"`. The bundled 3, 4 and 5 star squads carry tiers (5 stars has its own entry
since 0.16.0); 1-2 stars and the code defaults have none. A bad `@` value counts as 0 (the star's tier) with a
`config.unknown_tier` warning. Removing the `@suffixes` from a server's file restores one tier per squad.

### cop_radio_messages.yml

0.16.0 adds the keys `Dispatch_En_Route` (`%count% %station% %eta% %place%`), `Wipe_Refill` (`%eta%`), `Handoff`
(`%direction%`), `Post_Up` (`%place%`), `Eyes_On` (`%place% %direction%`) and `Returning_To_Patrol`, the root word
`Unknown_Place` ("the area"), and the places in the texts of `Dispatch_Wanted` (`%target% %level% %place%`) and
`Contact_Lost` (`%place%`). The code carries the same texts as fallbacks (`CopRadioMessages.DEFAULT_LINES`), so an old
server file keeps working and speaks the new lines; a server file keeps its own text for an old key. The Spanish file has
the same keys.

### items/money.yml

The bundled `Money.Drop_Sources.PLAYER.Enabled` is `false` since 0.16.0: one death costs one bill (the ward bill) and no
longer also drops part of the wallet at the body. The key stays; set it to `true` to get the drop back. A server's existing
`money.yml` is never rewritten, so only new installs change. `COP` is unchanged.

### New messages (`message_en.yml`, `message_es.yml`)

`Wanted_Level.Contact.Used` (`%stars% %money_symbol% %amount%`), `.Seen`, `.Cooldown` (`%time%`), `.Not_Wanted`, `.No_Money`
(`%money_symbol% %amount%`), `.Disabled`, `Death.Ward_Bill` (`%money_symbol% %amount%`) and `Death.Hospital_Shield`
(`%seconds%`). A message file kept from an older version lacks them and prints `<missing: ...>` for the line until it is
regenerated (see the migration guide).

---

## civilians.yml

Defines civilian NPC types and groups; ships inside `modules/gangland-civilians-<rev>.jar` as `npc/civilians.yml`.
Abridged example with the combat-squad keys added in 0.11.0:

```yaml
Types:
   gang_member:
      Display_Name: "&c&lGang Member"
      Entity_Type: PLAYER
      Health: 25.0
      Hostile: true
      Faction: gang_member          # Side it fights for (default: the type id)
      Weapon_Pool:
         - "weapon:pistol"
      AI:
         Wander:
            Enabled: true
            Range: 10
         Combat:
            Enabled: true
            Attack_Damage: 4.0
            Attack_Range: 12.0
            Attack_Interval_Ticks: 20
            Difficulty: NORMAL
            Alert_Range: 16.0        # Sight range, and how far it hears a faction member being hit (default 16.0)
            Search_Seconds: 20       # Give up after the squad has not seen the target this long (default 20)
```

| Key                        | Default     | Meaning                                                                                   |
|----------------------------|-------------|-------------------------------------------------------------------------------------------|
| `Faction`                  | the type id | Hostile NPCs of one faction fight as a squad; a hit alerts the victim's faction           |
| `AI.Combat.Alert_Range`    | `16.0`      | Sight range, and how far (from the victim) a faction member hears the hit                 |
| `AI.Combat.Search_Seconds` | `20`        | Seconds the squad may go without seeing its target before a member gives up and goes idle |
| `AI.Combat.Attack_Range`   | per type    | Melee engage distance only since 0.12.0; ranged types fire at anything seen within `Alert_Range` |
| `AI.Combat.Fire_Rate_Multiplier` | `1 / AI_Tick_Rate` | Gun cadence as a fraction of the weapon's own rate; the default (`0.05` at tick rate 20) keeps 0.11's cadence (0.12.0) |
| `AI.Combat.Tactics.*`      | arc 160     | Same keys as `Cops.Tactics`; shipped arcs gang_member 140, turf_defender 100, quartermaster 120 (0.12.0) |
| `AI.Combat.Melee.*`        | reach 3.0   | Same keys as `Cops.Melee`; the cooldown is `Attack_Interval_Ticks` (0.12.0) |
| `AI.Combat.Retreat.*`      | 0.3 / 12.0  | Same keys as `Cops.Retreat` (0.12.0) |
| `Shouts.*` (top level)     | range 24.0  | Faction shouts in chat, same shape as `Cops.Radio`. A Contact shout also recruits same-faction allies within `Shouts.Range` (0.12.0) |

Shout lines are in the `Shouts` block of `npc/civilian_messages.yml` (Spanish `_es.yml`).

---

## cars.yml

Car type definitions with physics and fuel configuration.

```yaml
# Example car definition
cars:
   sedan:
      display_name: "&aSport Sedan"
      material: MINECART
      custom_model_data: 1001
      max_speed: 0.8
      acceleration: 0.02
      deceleration: 0.01
      health: 100.0
      fuel:
         max: 1000
         material: COAL
         per_item: 100
```

---

## wearables.yml

Wearable armor definitions with traits and damage reduction.

```yaml
wearables:
  police_vest:
    material: LEATHER_CHESTPLATE
    name: "&9Police Vest"
    base_damage_reduction: 0.15
    leather_color: "0,0,139"
    traits:
      BULLETPROOF: 2
      REINFORCED: 1
    lore:
      - "&7Standard issue body armor"
```

---

## unique_items.yml

Unique item definitions with inventory behavior rules.

```yaml
items:
  phone:
    material: PAPER
    name: "&bPhone"
    custom_model_data: 1000
    add_on_join: true
    add_on_respawn: true
    drop_on_death: false
    allow_duplicates: false
    inventory_slot: 8
    overrides_slot: true
    movable: false
    droppable: false
```

---

## ammunition.yml

Ammo type definitions.

```yaml
ammunition:
  9mm:
    material: IRON_NUGGET
    name: "&79mm Round"
    stack_amount: 32
  shotgun_shell:
    material: GOLD_NUGGET
    name: "&6Shotgun Shell"
    stack_amount: 8
```

---

## scoreboard.yml

Removed in 0.10.0. Scoreboard rendering moved to the standalone **Plaque** plugin
(`E:\Programming\java\Plaque`), which owns its own `scoreboard.yml` with the identical schema
(`Board.Title.{Interval,Lines}` / `Board.Rows.<n>.{Interval,Lines}`) against Gangland's `%gangland_*%`
PlaceholderAPI tokens — copy `plugins/Gangland_Warfare/scoreboard.yml` to `plugins/Plaque/scoreboard.yml`
verbatim and it loads unchanged.

---

## plugin.yml

Spigot plugin metadata (not user-editable, filtered at build time).

```yaml
name: Gangland_Warfare
version: ${project.version}
main: org.luckyraven.gangland.Gangland
database: true
api-version: 1.13
depend:
   - NBTAPI
   - Citizens
softdepend:
   - PlaceholderAPI
   - Vault
commands:
   glw:
      description: Gangland warfare main command.
      permission: gangland.command.main
permissions:
   gangland.command.main:
      default: op
```

---

## Formula System

The plugin uses the **exp4j** library for evaluating mathematical expressions in
configuration files. Formulas are written as strings and parsed at runtime.

### Available Functions

| Function | Description     | Example            |
|----------|-----------------|--------------------|
| `+`      | Addition        | `base + level`     |
| `-`      | Subtraction     | `max - level`      |
| `*`      | Multiplication  | `base * 1.5`       |
| `/`      | Division        | `balance / 2`      |
| `^`      | Exponentiation  | `level ^ 1.5`      |
| `neg()`  | Negation        | `neg(amount)`      |
| `logb()` | Log base x of y | `logb(2, level)`   |
| `sin()`  | Sine            | `sin(level)`       |
| `cos()`  | Cosine          | `cos(level)`       |
| `sqrt()` | Square root     | `sqrt(experience)` |

### Formula Contexts

| Formula Location     | Available Variables                                  |
|----------------------|------------------------------------------------------|
| Level XP formula     | `base`, `max`, `level`, `experience`                 |
| Skill cost formula   | `base`, `level`                                      |
| Death money formula  | `balance`, `level`, `experience`, `bounty`, `wanted` |
| Cop count formula    | `level`, `base`, `perLevel`, `max`                   |
| Wanted money drain   | `amount`, `multiplier`, `stars`                      |
| Wanted timer formula | `time`, `amount`, `stars`                            |
