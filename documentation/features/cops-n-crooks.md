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

- **Seen in the last 1.5 seconds:** the squad chases them. Melee cops surround them instead of queueing behind
  each other; armed cops spread across their tier's formation arc 7–12 blocks away and keep moving while they fire
  (see [Squad Tactics](#squad-tactics)).
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

Armed cops fire at the player whenever they can see them within `Alert_Range`, with proper reload cycles. Inside
their firing band they work a post on the squad's formation and side-step to a new spot every few seconds while
they keep shooting; out of view they climb after the player like any other cop. A shooter holds its shot while a
squad-mate stands in its line of fire.

Melee cops start a swing within `Combat_Range` (4 blocks), but a swing only lands within `Melee.Reach` (3 blocks). It
can miss (the tier's `Difficulty` sets the hit chance), does less damage the further out it lands, and varies by
`Melee.Damage_Spread`. When one cop is attacked, every cop in its group joins the fight.

A badly hurt cop (30% health or less by default) breaks off, radios it, and takes cover out of the player's sight for
up to 10 seconds before it comes back out and fights on. A cop with no cover nearby keeps fighting.

### Cuffing

Officers and sergeants cuff first, then fight. While one cop cuffs the player, the rest of the group holds posts
around them. Only one cop can cuff a player at a time. Every cuff the player breaks out of counts against the whole
group: after `Max_Cuff_Attempts` escapes (3 by default), or as soon as the player hits a cop, the whole group stops
cuffing, switches to combat and radios "Suspect is resisting!" The group stays hostile until the wanted level clears.

---

## Squad Tactics

Every tier fights as a squad. Shooters spread over a **formation arc** around the player, so they never stand
shoulder to shoulder on one side. Melee tiers always surround the player evenly, whatever the arc says.

| Tier       | Role   | Formation arc | Strafe | Reposition |
|------------|--------|---------------|--------|------------|
| Officer    | Melee  | surround      | 10°    | every 4 s  |
| Sergeant   | Melee  | surround      | 10°    | every 4 s  |
| Lieutenant | Ranged | 200°          | 10°    | every 4 s  |
| SWAT       | Ranged | 270°          | 15°    | every 3 s  |
| Military   | Ranged | 330°          | 20°    | every 2 s  |

A shooter that is walking aims a little worse (`Moving_Aim_Error`). Set `Tactics.Enabled: false` (for every tier, or
in one tier's own `Tactics` block) to restore 0.11's positioning only: shooters freeze in their firing band, with no
formation, no strafing and no order / contact / reload / check-fire radio lines. It is not a full rollback to 0.11:
casualty, backup and dispatch radio, retreat to cover and the cuff-then-fight escalation stay on. Switch those off
with `Radio.Enabled: false`, `Backup.Enabled: false`, `Retreat.Enabled: false` and `Radio.Responder_Max: 0`.

### Squad Roles (0.13.0)

Each new cop takes a **role** inside its squad, laid over its tier: the role decides how the cop is dressed and armed,
where it stands and how it fights, and it shows in the cop's name. Roles fill in the order of the wanted level's
`Squad_Composition`; cops past the end of the list (backup, extra cops) take its last role. Everything about the roles
lives in its own file, `npc/cop_roles.yml`, written on first start when it is missing (an older `cops.yml` is never
touched). Delete the file and the same built-in roles apply.

| Role         | From level | Looks like                                    | Gun by tier (3 / 4 / 5)        | Does                                                                                   |
|--------------|------------|-----------------------------------------------|--------------------------------|----------------------------------------------------------------------------------------|
| ➤ Pointman   | 1          | Blue leather cap                              | rifle / steyr_aug / steyr_aug  | Front and centre, close in; the squad's radio voice while no Commander is on it        |
| ⚔ Assault    | 1          | Black balaclava, chainmail                    | mp5 / steyr_aug / golden_ak47  | The fan's ends, pushing in and strafing wide                                           |
| ★ Commander  | 3          | Glinting gold helmet                          | revolver / revolver / golden_ak47 | Radio voice from the rear of the band; when it goes down: "Commander down!", fall back |
| ⛨ Defender   | 3          | Shield, the squad's heaviest armour           | shotgun / shotgun / sawn_off or shotgun | Holds the centre post up front; its shield takes half of every hit from its front |
| ⌖ Marksman   | 3          | Green ghillie hood and leggings               | scout / scout / awp            | Stands 22-32 blocks out, far behind the rest; slower but surer shots                   |
| ✚ Medic      | 4          | Red cap, white leather, a golden apple        | revolver / mp5 / mp5 or steyr_aug | Walks over to hurt squad mates and patches them up (see Field Care below)          |

Every role keeps its identity piece on every tier, while its armour and gun grow with the tier: Officer and Sergeant
light (leather/chain), Lieutenant chain/iron, SWAT iron/diamond, Military diamond/netherite. The Medic always carries
the weakest gun of its squad, but never a weak one at Military. Tiers 1 and 2 are melee (`Can_Use_Weapons: false`):
their cops keep the tier's melee weapon whatever the role's `Weapon_Pool` says.

The Marksman's band is clamped under its gun's reach (`Projectile.Distance`), so it only stands far with a long gun:
the scout (100) and the awp (120) leave its 22-32 band whole, the M4 rifle (10) would pull it in to 8-10.

The default callsign `Format` is `"%rank% %role% &f%name% &7#%badge%"`: `Officer ✚ Medic Bob #1592` above the head,
with the role's colour and symbol from its `Display` block. `%rank%` stays the tier's `Display_Name`; for a cop with
no role (`Roles_Enabled: false`) `%role%` disappears with its space. The radio uses the same text without colours.
Remove `%role%` from `Cops.Names.Format` to hide the role. Set `Roles_Enabled: false` in `cop_roles.yml` to spawn
every cop as its plain tier again.

The Defender's front cone is judged from whoever dealt the damage: the shooter for a bullet or arrow, the attacker for
anything else. Area damage (a grenade, fire) is credited to its thrower, so a blast behind a Defender is still halved
while the thrower stands in front of it. The Commander fall-back is a retreat: `Cops.Retreat.Enabled: false` turns it
off, and each cop that falls back may radio the Fall_Back line right after "Commander down!".

### Field Care (0.13.0)

A cop at or below `Field_Care.Health_Fraction` (half) of its max health is **hurt**: it limps (`Limp_Speed`, 0.7 of its
tier speed), bleeds (redstone-block blood at random body spots, denser the more hurt, with a heavier burst on every hit taken) and radios "I'm hit!" once. A **Medic** cop of the same squad within
`Medic_Radius` (36 blocks, enough to reach a Marksman at its far post) walks straight over to it (a direct route, not from cover to cover), and the squad calls
"Covering fire!". Once the medic is within `Heal_Range` (2.5 blocks) both hold still: the patient crouches and both keep
shooting. A patient on its way to cover is treated once it gets there, never held in the open; the time it spends
walking there does not count toward the 15 s below. After `Channel_Ticks` (3 s) the patient gets `Heal_Fraction` (half)
of its max health back, with heart particles and "Patched up". A hit on the medic starts the 3 s over ("Pinned down"). A
squad without a Medic cop never heals; a medic that cannot reach its patient within 15 s gives up. If another plugin
cancels the heal, the treatment ends quietly and that cop is not treated again for 15 s.

| Key (`Cops.Field_Care`) | Default | Does                                                     |
|-------------------------|---------|----------------------------------------------------------|
| `Enabled`               | true    | false: no limp, bleeding or treatment                    |
| `Health_Fraction`       | 0.5     | Hurt at or below this share of max health (0-1)          |
| `Limp_Speed`            | 0.7     | A hurt cop's speed, as a share of its tier speed (0.1-1) |
| `Medic_Enabled`         | true    | false: cops still get hurt, but nobody treats them       |
| `Medic_Radius`          | 36.0    | Blocks a medic answers a hurt squad mate within (2-64)   |
| `Heal_Range`            | 2.5     | Blocks from the patient the medic treats it from (1-6)   |
| `Channel_Ticks`         | 60      | Ticks one treatment takes                                |
| `Heal_Fraction`         | 0.5     | Share of max health one treatment restores (0-1)         |
| `Hurt.Bleed_Particle`   | BLOCK_CRACK | Blood particle; drawn with redstone-block data (`BLOCK` on 1.20.5+), falls back to red dust |
| `Hurt.Bleed_Count`      | 6       | Particles per spot (1-50), up to double at death's door  |
| `Hurt.Bleed_Spots`      | all six | HEAD, CHEST, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG; a burst picks 1-2 |

Every key is optional: a `cops.yml` without the block gets these defaults.

"I'm hit!" and "Patched up" are `Radio.Priority` lines, so other squad chatter never swallows them. A `cops.yml` from
before 0.13.0 keeps its own `Radio.Priority` list: add `"Hit"` and `"Patched_Up"` to it.

---

## Police Radio

Cops talk on the radio, and the lines show up in chat. Every player within `Radio.Range` (32 blocks) of the speaking
cop hears them, bystanders included. The hunted player hears their own pursuers from `Radio.Target_Range` (64
blocks). Each line plays a short click sound.

- **Contacts:** a cop that spots the player calls out the distance and direction, and the squad calls it when it
  loses sight of them.
- **Orders and acknowledgements:** the squad leader orders members to push in or take a flank, and the ordered cop
  answers ("Copy.") a moment later. Shooters also call out repositioning, reloading and check-fire.
- **Casualties:** "Officer down!" when a cop dies, or a leader change when the leader dies.
- **Dispatch:** a new wanted level, a tier escalation, and the stand-down when the player is cleared.
- **Responders:** a Contact, Officer Down or Backup call pulls in up to `Radio.Responder_Max` (2) nearby cops within
  `Radio.Range`. Only cops that are idle, walking home or chasing a civilian answer, including cops from another
  wanted player's group. A cop that is cuffing, guarding or hunting a player of its own never leaves it. This is the
  cops hearing each other, so it works even when no player is near.
- **Backup:** when a cop goes down, the squad requests `Backup.Extra_Cops` (1) extra cops for
  `Backup.Duration_Ticks` (30 s), at most once per `Backup.Cooldown_Ticks` (60 s). When the backup runs out, the
  surplus cops that aren't fighting walk home.
- **Regroup (0.15.0):** "Two down! Pull back to cover, backup is coming!" when the squad falls back, and "Backup's
  here! All units, push together!" when it pushes. See Regroup below.
- **Shots fired (0.15.0):** the nearest cop of the hunted player's squad says where it heard a shot. See Shot Noise below.
- **Resisting**, **retreat** and **field care** lines, as described above.

Lines are throttled per squad and per player, so chat never floods. Every line is in `npc/cop_radio_messages.yml`
(Spanish: `_es.yml`). Each key is a list that one entry is picked from at random, and `[]` silences that line.
`Radio.Enabled: false` silences the radio for players but keeps responders and backup working.

---

## Losing the Cops and the Chase (0.15.0)

The chase around a wanted player is configured in `npc/wanted.yml` (heat, evasion, HUD, charge sheet) and
`npc/wanted_messages.yml`; see [Wanted & Bounty](./wanted-bounty.md) for the player-facing rules.

- **Evasion reads the squad's sightings.** Every cop in the group shares what it sees. The evasion clock counts "no
  cop has seen you" from the squad's last sighting, and a cop that is walking home (`RETURNING`) does not count as
  pursuit. When no live hunting cop is left the evasion state turns off and the fixed decay timer takes over.
- **Regroup.** When `Regroup.Casualties` (2) cops of one squad die within `Regroup.Window_Seconds` (20), the whole squad
  falls back to cover for at most `Regroup.Fall_Back_Seconds` (15), radios for backup, and pushes together once backup
  is within `Regroup.Arrival_Radius` (24) blocks. One regroup per `Regroup.Cooldown_Seconds` (60) and squad; a squad
  that cuffs first never regroups. `Regroup.Enabled: false` keeps the 0.13.0 behaviour. A Commander call for a fall-back
  during a regroup keeps the longer of the two.
- **Shot noise.** A Bartizan weapon fired by a wanted player inside `Shot_Noise.Radius` of a cop of his squad (GUN 48,
  THROWABLE 16, MELEE 0 = silent; a type that is not listed is silent) reports the shooter's position to the squad.
  The squad counts it as a sighting, so evasion resets, and the nearest cop calls "Shots fired!" (throttled to once
  per `Cooldown_Ticks.Shots_Fired`, 3 s, per shooter). The line respects the radio gaps like any line outside
  `Radio.Priority`, so it can be dropped right after another squad line; the sighting always lands. Players who are not
  wanted, and NPC shooters, are ignored.
- **Wanted HUD.** The boss bar, star card, title, siren, zone ring and compass are shown to the hunted player only and
  disappear when the chase ends.

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
         Display_Name: "&9Officer"   # The %rank% in Cops.Names.Format (supports & color codes)
         Health: 20.0                # Max health points
         Damage: 2.0                 # Melee damage per attack
         Speed: 1.0                  # Movement speed multiplier (1.0 = normal player speed)
         Cuff_Radius: 3.0            # Blocks from target at which this tier can attempt a cuff
         Can_Use_Weapons: false      # Whether this tier fires Gangland ranged weapons
         Skip_Cuffing: false         # If true, skips cuffing entirely and goes straight to lethal combat
         Difficulty: EASY            # EASY / NORMAL / HARD / DEADLY: aim error, reaction time, fire rate, melee hit chance
         Fire_Rate_Multiplier: 0.1   # Gun cadence as a fraction of the weapon's own fire rate (0.1 = the 0.11 cadence)
         Tactics:                    # Overrides Cops.Tactics key by key for this tier
            Formation_Arc: 200.0
         Weapon_Pool: # Items the cop can carry. One is selected randomly on spawn.
            - "WOODEN_SWORD"          # Vanilla Bukkit material name
            - "weapon:rifle"          # Custom Gangland weapon — prefix with "weapon:" then the weapon name
         Helmet: ""                  # Vanilla armor material for the helmet slot (empty = none)
         Chestplate: ""              # Vanilla armor material for the chestplate slot
         Leggings: ""                # Vanilla armor material for the leggings slot
         Boots: ""                   # Vanilla armor material for the boots slot
```

`Fire_Rate_Multiplier` defaults to `1 / Cops.Behaviour.AI_Tick_Rate`, which keeps the 0.11 cadence. A vanilla
`BOW`/`CROSSBOW` counts from a 15-tick base, so at `0.1` it fires every 150 ticks before `Difficulty`. See
[Migrating to 0.12.0](../migration-0.12.0.md), section 5.

`Weapon_Pool` accepts two formats:

- Plain vanilla material (e.g., `IRON_SWORD`, `CROSSBOW`) — gives the NPC that vanilla item.
- `weapon:<name>` (e.g., `weapon:rifle`) — gives the NPC a configured Gangland weapon from the `weapon/` folder.

---

### Tactics, Melee, Radio, Backup, Retreat and Stuck (`cops.yml`)

```yaml
Cops:
   Melee:
      Reach: 3.0                   # A swing lands only within this many blocks
      Approach: 2.0                # Melee cops surround here (capped at Cuff_Radius - 0.5); full damage inside it
      Damage_Spread: 0.15          # Damage varies by up to +/-15%
      Edge_Damage: 0.7             # Fraction of full damage at the edge of Reach
   Tactics:                        # Defaults for every tier; a tier's own Tactics block overrides key by key
      Enabled: true                # false = 0.11 positioning only (freeze in band); radio/backup/retreat stay on
      Formation_Arc: 270.0         # Degrees the shooters spread over (melee tiers always surround)
      Strafe_Degrees: 15.0         # Side-step per reposition
      Reposition_Ticks: 60         # Server ticks between repositions (+/-25%)
      Moving_Aim_Error: 0.10       # Extra aim error while walking
   Radio:
      Enabled: true                # false silences chat; responders and backup still work
      Range: 32.0                  # Who hears a line, and how far other cops hear a call
      Target_Range: 64.0           # How far the hunted player hears their pursuers
      Squad_Gap_Ticks: 30          # Gap between two lines of one squad (priority lines and acks skip it)
      Player_Gap_Ticks: 20         # Gap between two low-priority lines reaching one player
      Ack_Delay_Ticks: 25          # Delay before an ordered cop answers
      Responder_Max: 2             # Nearby cops pulled in per call (0 = none)
      # Priority (line kinds that skip the gaps), Cooldown_Ticks (per-kind repeat cooldown; 0.15.0 adds Regroup 1200,
      # Regroup_Push 1200 and Shots_Fired 60) and Sound: see the file
   Backup:
      Enabled: true
      Extra_Cops: 1                # On top of the wanted-level count, capped by Max_Per_Player
      Duration_Ticks: 600          # 30 s; afterwards the surplus cops that aren't fighting walk home
      Cooldown_Ticks: 1200         # 60 s between requests from one group
   Regroup:                        # 0.15.0; every key falls back to the value shown
      Enabled: true
      Casualties: 2                # Cops lost inside Window_Seconds that trigger a pull-back
      Window_Seconds: 20
      Fall_Back_Seconds: 15        # Longest stay in cover before the squad pushes anyway
      Cooldown_Seconds: 60         # One regroup per squad per this long
      Arrival_Radius: 24.0         # Push together once backup is this close
   Shot_Noise:                     # 0.15.0
      Enabled: true
      Radius:                      # Blocks a cop hears a shot, by weapon type; 0 or unlisted = silent
         GUN: 48
         THROWABLE: 16
         MELEE: 0
   Retreat:
      Enabled: true
      Health_Fraction: 0.3         # Retreat at or below 30% health
      Radius: 12.0                 # Blocks searched for cover the player can't see
   Stuck:                          # Optional block (0.13.0); every key falls back to the value shown
      Enabled: true                # false = a cop that finds no way to the player stays where it is
      Recycle_Seconds: 12          # A cop stranded this long, out of the player's sight, is replaced
      Avoid_Spawner_Seconds: 60    # Its spawner is skipped for replacements this long (0 = never)
```

A stranded cop is not replaced while the player is looking at it (in front of him, clear line, within
`Cops.Spawn.Visibility_Check_Distance`, never less than 24 blocks), while another player faces it, or while it is within
melee reach on his level with nothing in between. Once it has been stranded for twice `Recycle_Seconds`, a view only
keeps it within 24 blocks, the player's own and every other player's alike. With its spawner skipped, the replacement comes from the next spawner within `Spawner_Max_Y_Diff`, or from
the ring around the player at his level (for a player indoors, the street outside counts too). The wanted-level cop
count and `Max_Per_Player` still cap the total. Dispatch also tells the replacements where the player is now, so a
player who slipped away unseen from a spot the stranded cops could not reach is not hunted there again.

---

### Roles and Squad_Composition (`npc/cop_roles.yml`, 0.13.0)

Every key is optional; an entry under `Roles` is read key by key over the built-in role of the same name (a new name
starts from a plain role). The shipped file lists the whole catalogue with a comment per key. A bad value (an unknown
material, a colour that is not `#RRGGBB` or `R, G, B`, a number out of range) is reported at startup and the built-in
value kept.

```yaml
Roles_Enabled: true
Roles:
   Medic:
      Display:
         Name: "Medic"               # the word, also used by radio lines
         Color: "&c"
         Symbol: "✚"                 # "" = none
      Fan_Placement: CENTER          # ANY, CENTER or FLANK
      Ranged_Min_Distance: 8.0       # the role's own firing band, clamped under the gun's Projectile Distance
      Ranged_Max_Distance: 12.0
      Health_Multiplier: 1.0
      Leader_Priority: 0             # the highest live priority speaks for the squad
      Strafe_Degrees: 15.0           # replaces the tier's Tactics.Strafe_Degrees
      Fire_Rate_Scale: 1.0           # multiplies the tier's Fire_Rate_Multiplier
      Difficulty_Bonus: 0            # steps above the tier's Difficulty
      Block_Fraction: 0.0            # damage taken off a hit from inside the front cone (the Defender: 0.5)
      Block_Cone_Degrees: 60.0
      Medic: true                    # treats hurt squad mates
      Commander: false               # its death: Commander_Down, the squad falls back for 5 s
      Retreat:
         Health_Fraction: 0.5        # read over cops.yml Cops.Retreat
      Weapon_Pool:                   # weapon:<Bartizan weapon>; other entries are vanilla items held without Bartizan
         - "weapon:pistol"
      Gear:                          # Helmet, Chestplate, Leggings, Boots, Off_Hand
         Helmet:
            Material: LEATHER_HELMET
            Leather_Color: "#B02E26" # or "176, 46, 38"
            Glow: false
         Off_Hand: GOLDEN_APPLE      # a plain material name works too; "" empties the slot
      Tiers:                         # by level number or the tier's Display_Name without colours (Military)
         5:
            Weapon_Pool:
               - "weapon:mp5"
               - "weapon:steyr_aug"
            Gear:
               Chestplate: NETHERITE_CHESTPLATE
Squad_Composition:
   3:
      - "Commander"
      - "Pointman"
      - "Defender"
      - "Marksman"
      - "Assault"
```

Which gear a cop wears, slot by slot, and which weapon pool it draws from:

1. the role's entry for the cop's tier (`Roles.<Name>.Tiers.<tier>`),
2. else the role's own `Gear` / `Weapon_Pool`,
3. else the tier's own `Wearables` / `Weapon_Pool` from `cops.yml`.

On a melee tier step 2's `Weapon_Pool` is skipped (the cop keeps its melee weapon). A role pool with no vanilla item
keeps the tier's vanilla items, which the cop holds when no Bartizan weapon resolves (Bartizan missing, unknown name).

### AI Settings (`settings.yml` → `Cops.Behaviour`)

```yaml
Cops:
   Behaviour:
      Max_Per_Player: 8             # Hard cap on active cop NPCs per wanted player at any time
      AI_Tick_Rate: 10              # Ticks between each AI decision cycle. Lower = faster reactions, more CPU.
      Spawn_Check_Rate: 40          # Ticks between checks that decide whether to spawn more cops
      Cuff_Radius: 3.0              # Default cuff radius in blocks (individual tiers override this)
      Max_Cuff_Attempts: 3          # Cuffs the player may break out of (across the group) before the group fights
      Cuff_Cooldown_Ticks: 100      # Ticks between consecutive cuffing attempts
      Alert_Range: 40.0             # Sight range: a cop sees a wanted player this close with line of sight; shared by the squad
      Combat_Range: 4.0             # Distance at which a melee cop starts a swing (ranged cops fire within Alert_Range)
      Attack_Cooldown_Ticks: 20     # Server ticks between melee swings
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
