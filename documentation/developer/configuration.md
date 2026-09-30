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
| `civilians.yml`    | Civilian type definitions and spawner config |
| `cars.yml`         | Car type definitions                         |
| `wearables.yml`    | Wearable armor definitions                   |
| `unique_items.yml` | Unique item definitions                      |
| `ammunition.yml`   | Ammunition type definitions                  |
| `plugin.yml`       | Spigot plugin metadata (not user-editable)   |

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
```

### Bounty

```yaml
Bounty:
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
      Amount: 50
      Multiplier: 5                # amount * multiplier ^ stars
   Repeating_Timer:
      Enable: true
      Time: 120                    # Default seconds between level reduction
      Multiplier:
         Enable: true
         Amount: 1.1                # time * amount ^ stars
   Level:
      Increment: 1
      Maximum: 5
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
ships inside the cops-n-crooks module jar as `npc/cops.yml`. Abridged:

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
| `Names.Format` | `%rank% &f%name% &7#%badge%` | The callsign above the cop's head and on the radio. `%rank%` = the role's `Display_Name` in the tier's colour (the tier's `Display_Name` with no role), `%badge%` = 1000 + the Citizens id. The Citizens name itself is the short plain `Bob #1592` |
| `Names.First_Names` | 28 built-in names | First-name pool; `[]` = no first name, a missing key = the built-in pool |
| `Stuck.*` | see above | Optional (0.13.0). A cop that has found no way to its player for `Recycle_Seconds` (at least 1), out of his view (cone plus clear line within `Cops.Spawn.Visibility_Check_Distance`, never under 24 blocks; past twice `Recycle_Seconds` within 24 blocks only), unseen by other players and outside melee reach on his level, is replaced; its spawner is skipped for `Avoid_Spawner_Seconds` (0 = never). `Enabled: false` never replaces |

The radio lines are in `npc/cop_radio_messages.yml` (Spanish `_es.yml`). See
[Cops N Crooks](../features/cops-n-crooks.md) and [Migrating to 0.12.0](../migration-0.12.0.md).

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
