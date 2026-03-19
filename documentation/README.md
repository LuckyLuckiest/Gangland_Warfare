# Gangland Warfare — Documentation

[← Back to Project](../README.md)

---

## 📚 Table of Contents

### Version Changelogs

| Version                                 | Status  | Changelog                        |
|-----------------------------------------|---------|----------------------------------|
| [v0.7.3-DEV](./v0.7.3-DEV/CHANGELOG.md) | Current | Cops N Crooks, Wearables, Repair |

---

### Core Feature Guides

Guides for features that are part of the base plugin and not tied to a specific version.

| #  | Guide                                          | Summary                                                          |
|----|------------------------------------------------|------------------------------------------------------------------|
| 1  | [Gangs](./features/gangs.md)                   | Creating gangs, member management, alliances, and gang bank      |
| 2  | [Economy](./features/economy.md)               | Personal balance, bank accounts, death penalties, admin commands |
| 3  | [Waypoints](./features/waypoints.md)           | Teleportation destinations, costs, timers, and safe zones        |
| 4  | [Loot Chests](./features/loot-chests.md)       | Randomized reward containers, tiers, and keys                    |
| 5  | [Levels](./features/levels.md)                 | XP system, level formulas, and skill upgrades                    |
| 6  | [Unique Items](./features/unique-items.md)     | Phone, lockpicks, keys, and custom inventory behavior            |
| 7  | [Scoreboard](./features/scoreboard.md)         | Live stat display, drivers, and animated titles                  |
| 8  | [Weapons](./features/weapons.md)               | Custom weapons, ammo, damage, and combat                         |
| 9  | [Wanted & Bounty](./features/wanted-bounty.md) | Wanted stars, cop scaling, and bounty system                     |
| 10 | [Trade Signs](./features/trade-signs.md)       | In-world buy/sell signs for weapons and ammo                     |
| 11 | [Ranks](./features/ranks.md)                   | Gang rank hierarchy and permission management                    |
| 12 | [Database & Setup](./features/database.md)     | MySQL/SQLite config, auto-save, and first-time setup             |

---

### v0.7.3-DEV Feature Guides

Guides for features introduced in v0.7.3-DEV.

| # | Guide                                                | Summary                                           |
|---|------------------------------------------------------|---------------------------------------------------|
| 1 | [Cops N Crooks](./v0.7.3-DEV/cops-n-crooks.md)       | Police NPC AI, spawning, pursuit, and arrest      |
| 2 | [Jail & Detainment](./v0.7.3-DEV/jail-detainment.md) | Handcuffing, jailing, and player restraint        |
| 3 | [Wearables](./v0.7.3-DEV/wearables.md)               | Custom armor pieces, traits, and damage reduction |
| 4 | [Repair System](./v0.7.3-DEV/repair.md)              | Repairing weapons and wearables with materials    |

---

### Quick Reference

| Topic                          | Location                                                                                |
|--------------------------------|-----------------------------------------------------------------------------------------|
| First-time server setup        | [Database & Setup → Setup Checklist](./features/database.md#first-time-setup-checklist) |
| Required dependencies          | [v0.7.3-DEV Changelog → New Requirements](./v0.7.3-DEV/CHANGELOG.md#new-requirements)   |
| Cop configuration (`cops.yml`) | [Cops N Crooks → Configuration](./v0.7.3-DEV/cops-n-crooks.md#configuration)            |
| Wearable traits                | [Wearables → Traits](./v0.7.3-DEV/wearables.md#traits)                                  |
| Repair materials               | [Repair System → Built-in Materials](./v0.7.3-DEV/repair.md#built-in-repair-materials)  |
| Trade sign setup               | [Trade Signs → Setting Up a Sign](./features/trade-signs.md#setting-up-a-sign)          |
| Loot chest tiers               | [Loot Chests → Tiers](./features/loot-chests.md#tiers)                                  |
| Economy admin commands         | [Economy → Admin Commands](./features/economy.md#admin-economy-commands)                |

---

*All commands use the `/glw` dispatcher (alias: `/gangland`).*
