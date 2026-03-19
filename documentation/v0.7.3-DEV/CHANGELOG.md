# 🚔 Badges, Bullets & Bars — v0.7.3-DEV Changelog

> *The streets just got a lot more dangerous. Cops have radios, weapons, handcuffs, and nowhere to be
> except right behind you. Welcome to Cops N Crooks.*

---

[← Back to Documentation Index](../README.md)

---

## ⚠️ New Requirements

This update introduces new hard dependencies. **The plugin will refuse to load if these are not installed.**

| Dependency     | Type                | Purpose                                                                               |
|----------------|---------------------|---------------------------------------------------------------------------------------|
| **Citizens**   | Required            | Powers all police NPCs. Without it, no cops spawn and the plugin fails to initialize. |
| **NBTAPI**     | Required (existing) | Custom item data for weapons, ammo, and wearables.                                    |
| PlaceholderAPI | Optional            | Custom placeholders in messages and scoreboards.                                      |
| Vault          | Optional            | Economy integration.                                                                  |
| ViaVersion     | Optional            | Multi-version client support.                                                         |

---

## 📄 Configuration Changes

- **`cops.yml`** is a new file introduced in this update. It controls all cop NPC tiers, AI behavior, spawning rules,
  and detainment options. It is auto-generated on first run. See the [Cops N Crooks guide](./cops-n-crooks.md) for full
  details.
- **`wearables.yml`** is a new file for configuring custom armor pieces and their protective traits. See
  the [Wearables guide](./wearables.md).
- **`repair.yml`** is a new file defining what materials can be used to repair weapons and armor. See
  the [Repair guide](./repair.md).
- **`settings.yml`** has been extended with new sections for cop count scaling, AI tick rates, spawn radii, detainment
  capacity, and wanted level thresholds. Existing installs should regenerate this file or merge in the new keys
  manually.

---

## ✨ Features

### Cops N Crooks

The entire police system has been built from the ground up. Cops are now fully functioning Citizens NPCs with AI-driven
behavior.

- Police NPCs spawn in the world when a player has a wanted level, chase targets, engage in combat, and make arrests.
- Every cop engages immediately upon spawning — no idle standing. The moment they appear, they are on the move toward
  their target.
- When one cop spots a wanted player, **the entire group is alerted** and joins the pursuit.
- **Cop count and tier scale with wanted level** — a 1-star wanted player draws two rookie officers; a 5-star wanted
  player gets up to eight military-grade units.
- Five cop tiers are built in: Officer, Sergeant, Lieutenant, SWAT, and Military — each with increasing health, speed,
  damage, and equipment.
- Higher-tier cops (Lieutenant and above) carry and fire Gangland custom weapons with their own ammo pools and reload
  mechanics.
- **Bullet flyby and impact sounds** play when cops shoot near or at a player.
- **Cops spawn from configured spawner locations** in the world. If no spawner is close enough, the system falls back
  through progressively wider search rings before using a global fallback, ensuring cops always find a way to you.
- Dying clears your wanted level.

See the full [Cops N Crooks guide](./cops-n-crooks.md) for configuration and commands.

---

### Arrest & Detainment

- Cops can handcuff players during an arrest. Once cuffed, a player is slowed, partially blinded, and cannot use
  weapons.
- Multiple cops will never fight over the same target — once a cop commits to cuffing a player, others back off.
- If a cuffed player logs out before being jailed, they are automatically moved to jailed status when they return.

See the full [Jail & Detainment guide](./jail-detainment.md).

---

### Jail System

- Arrested players are teleported to a configured jail location and held there until released.
- Admins can create and remove jails in-game. Each jail has a configurable capacity cap.
- Players can be manually jailed or released via commands.

See the full [Jail & Detainment guide](./jail-detainment.md) for configuration and commands.

---

### Wanted & Bounty System

- The wanted level system is now directly tied to the cop spawning system — stars you earn result in active police
  pursuit.
- The bounty system has been moved under the Cops N Crooks umbrella and tightened up with configurable scaling and kill
  multipliers.

See the full [Wanted & Bounty guide](./wanted-bounty.md).

---

### Wearables

Custom armor pieces are now a fully supported item category, separate from vanilla armor. Wearables have:

- A **base damage reduction** percentage — a flat amount of every hit absorbed before other calculations.
- **Trait slots** — each piece can have one or more stacking protection traits:
    - `REINFORCED` — general damage reduction per level
    - `BULLETPROOF` — specifically reduces firearm damage
    - `PADDED` — broad physical protection
    - `TOUGHENED` — reduces critical hit bonus damage
    - `FIRE_RESISTANT` — burns hurt less
    - `REACTIVE` — small chance to completely nullify a hit per level
    - `LIGHTWEIGHT` — cosmetic trait, no combat effect

Four built-in wearable pieces ship with the plugin: **Police Vest**, **Police Helmet**, **Gang Jacket**, and **Heavy
Vest**. Additional pieces can be configured in `wearables.yml`.

See the full [Wearables guide](./wearables.md) for configuration.

---

### Weapon Repair

Weapons and wearables no longer have to be discarded when their durability runs low. Four repair materials ship with the
plugin, each with different uses, restoration amounts, and item compatibility:

- **Cleaning Kit** — a light maintenance item, works on both weapons and wearables.
- **Mechanical Part** — a single-use part that restores a significant chunk of a weapon's durability.
- **Weapon Repair Kit** — fully restores a weapon in one use.
- **Field Kit** — a cheap, low-restoration tool useful in the field.

All materials are configurable in `repair.yml` — you can define what vanilla item represents each kit, how many uses it
has, and how much durability it restores.

See the full [Repair System guide](./repair.md).

---

### Trade Signs

Players and admins can now set up in-world signs that allow buying and selling of:

- **Ammunition** — any configured ammo type.
- **Weapons** — any weapon from the weapon configs.
- Both support configurable prices and quantities per transaction.

See the full [Trade Signs guide](./trade-signs.md) for setup instructions.

---

### Rank Hierarchy

Gang ranks now support parent-child relationships, enabling a full hierarchical permission structure. Admins can assign
or remove a rank's parent rank via commands, creating inheritance chains where child ranks automatically receive the
permissions of their parents.

See the full [Ranks guide](./ranks.md) for commands.

---

## 🐛 Bug Fixes

### Weapon Reload Getting Fully Reset on Cancel

When a player cancelled an in-progress reload — by switching items, opening inventory, or being disrupted — the entire
reload progress was being wiped by the `stop()` call. This meant that even if a reload was 90% complete, interrupting it
sent the player back to square one with no ammo loaded. The fix separates the "halt current phase" path from the "reset
all progress" path so interruptions only pause where they are.

---

### Creative Mode Players Blocked by Magazine Limits

The magazine check was running without first validating the player's game mode. Creative mode players were being told
their magazine was full and blocked from loading ammo, even though creative players should bypass all resource
constraints. The check now skips the capacity validation entirely for players in creative mode.

---

### Configuration Reload Silently Skipping Files

When `/glw reload` (or the equivalent reload command) was used to refresh configuration files, some files were quietly
being skipped because they had not been registered in the reload pipeline. This meant changes to those files had no
effect until a full server restart, with no error or warning ever shown. All configuration files are now properly
registered and reloaded together.

---

### Sign Material Parsing Failing on Older Versions

Signs that referenced certain block or item materials by name were failing to parse correctly on servers running older
Minecraft versions. The material lookup was going directly to the Bukkit API which uses 1.13+ naming conventions,
causing a silent failure and a broken sign. The lookup now routes through XMaterial, which handles name mapping for all
versions from 1.10 to 1.21.

---

### Commands With Similar Names Routing to Wrong Handler

Sub-commands that shared a common prefix (e.g., `spawner` and `spawner set`) were being incorrectly matched by the
dispatcher. In some cases, typing the longer, more specific command was matching the shorter one first and dispatching
to the wrong handler — sometimes with the leftover arguments passed incorrectly. The argument resolution logic now
correctly distinguishes between commands with overlapping prefixes.

---

### Backup Failures Going Unnoticed

When the automatic database backup failed, the failure was being logged at the `INFO` level — the same level as routine
status messages. This meant backup failures were buried in normal log output and effectively invisible unless you were
specifically watching for them. Backup failures are now logged at `WARN` level so they stand out and can be acted on.

---

[← Back to Documentation Index](../README.md)
