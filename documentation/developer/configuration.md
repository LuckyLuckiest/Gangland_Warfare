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
| `scoreboard.yml`   | Scoreboard layout and lines                  |
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

### Scoreboard

```yaml
Scoreboard:
  Enable: true
  Driver: "Driver_V3"           # Driver_V1, Driver_V2, or Driver_V3
```

| Driver      | Algorithm                                         |
|-------------|---------------------------------------------------|
| `Driver_V1` | Clustering algorithm for similar-interval updates |
| `Driver_V2` | Built-in library for cluster management           |
| `Driver_V3` | Change detection caching, temporary alternative   |

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
      Max_Cuff_Attempts: 3
      Cuff_Cooldown_Ticks: 100
      Alert_Range: 40.0
      Combat_Range: 4.0
      Attack_Cooldown_Ticks: 20
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
   Return:
      Max_Ticks: 600
      Station_Arrival_Distance: 3.0
   Starting_Ammo_Magazines: 3
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

Defines per-tier cop NPC configurations. Each tier has its own equipment, stats, and
behavior overrides.

```yaml
# Example tier structure
tiers:
   officer:
      display_name: "&9Officer"
      health: 20.0
      damage: 4.0
      armor:
         helmet: IRON_HELMET
         chestplate: IRON_CHESTPLATE
      weapons:
         - "pistol"
      can_cuff: true
      cuff_priority: 0.8

   swat:
      display_name: "&4SWAT"
      health: 40.0
      damage: 8.0
      armor:
         helmet: DIAMOND_HELMET
         chestplate: DIAMOND_CHESTPLATE
      weapons:
         - "assault_rifle"
         - "shotgun"
      can_cuff: false
```

---

## civilians.yml

Defines civilian NPC types and spawner configurations.

```yaml
types:
   street_vendor:
      display_name: "&eStreet Vendor"
      skin: "texture_data"
      behaviour:
         wander_range: 10.0
         flee_range: 20.0
         combat_enabled: false
      inventory:
         title: "&6Street Vendor"
         size: 27
         items:
            0: "weapon:pistol{amount=1}"
            1: "ammo:9mm{amount=32}"

   pedestrian:
      display_name: "&7Pedestrian"
      behaviour:
         wander_range: 15.0
         flee_range: 25.0
         combat_enabled: false
```

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

Scoreboard layout configuration.

```yaml
title:
   text: "&6Gangland Warfare"
   animated: false
lines:
   -  text: "&7Balance: &a%balance%"
      update_interval: 20
   -  text: "&7Level: &e%level%"
      update_interval: 100
   -  text: "&7Gang: &b%gang_name%"
      update_interval: 60
   -  text: "&7Wanted: %wanted_stars%"
      update_interval: 10
```

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
