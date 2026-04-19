# Gangland Warfare — Documentation

[← Back to Project](../README.md)

---

## 📚 Table of Contents

### Version Changelogs

| Version                                 | Status  | Changelog                                 |
|-----------------------------------------|---------|-------------------------------------------|
| [v0.7.4-DEV](./v0.7.4-DEV/CHANGELOG.md) | Current | Smarter NPC AI, bug fixes, developer docs |
| [v0.7.3-DEV](./v0.7.3-DEV/CHANGELOG.md) | Stable  | Cops N Crooks, Wearables                  |

---

### Core Feature Guides

Guides for features that are part of the base plugin and not tied to a specific version.

| #  | Guide                                              | Summary                                                          |
|----|----------------------------------------------------|------------------------------------------------------------------|
| 1  | [Gangs](./features/gangs.md)                       | Creating gangs, member management, alliances, and gang bank      |
| 2  | [Economy](./features/economy.md)                   | Personal balance, bank accounts, death penalties, admin commands |
| 3  | [Waypoints](./features/waypoints.md)               | Teleportation destinations, costs, timers, and safe zones        |
| 4  | [Loot Chests](./features/loot_chests.md)           | Randomized reward containers, tiers, and keys                    |
| 5  | [Levels](./features/levels.md)                     | XP system, level formulas, and skill upgrades                    |
| 6  | [Unique Items](./features/unique-items.md)         | Phone, lockpicks, keys, and custom inventory behavior            |
| 7  | [Scoreboard](./features/scoreboard.md)             | Live stat display, drivers, and animated titles                  |
| 8  | [Weapons](./features/weapons.md)                   | Custom weapons, ammo, damage, and combat                         |
| 9  | [Wanted & Bounty](./features/wanted-bounty.md)     | Wanted stars, cop scaling, and bounty system                     |
| 10 | [Trade Signs](./features/trade-signs.md)           | In-world buy/sell signs for weapons and ammo                     |
| 11 | [Ranks](./features/ranks.md)                       | Gang rank hierarchy and permission management                    |
| 12 | [Database & Setup](./features/database.md)         | MySQL/SQLite config, auto-save, and first-time setup             |
| 13 | [Inventory System](./features/inventory.md)        | Custom GUI menus, slots, conditions, pagination, and API usage   |
| 14 | [Cops N Crooks](./features/cops-n-crooks.md)       | Police NPC AI, spawning, pursuit, and arrest                     |
| 15 | [Jail & Detainment](./features/jail-detainment.md) | Handcuffing, jailing, and player restraint                       |
| 16 | [Wearables](./features/wearables.md)               | Custom armor pieces, traits, and damage reduction                |

---

### Developer Documentation (Codebase Internals)

In-depth technical documentation for developers working on the codebase.

| #  | Guide                                                       | Summary                                                      |
|----|-------------------------------------------------------------|--------------------------------------------------------------|
| 1  | [Architecture Overview](./developer/architecture.md)        | Plugin lifecycle, initialization, event system, module graph |
| 2  | [Module Reference](./developer/modules.md)                  | Every module with classes, purpose, and dependencies         |
| 3  | [Dependency Injection](./developer/dependency-injection.md) | DI container, autowiring, listener discovery                 |
| 4  | [Persistence Layer](./developer/persistence.md)             | Repository pattern, database, tables, auto-save              |
| 5  | [Command System](./developer/commands.md)                   | Argument tree, dispatch, tab completion, adding commands     |
| 6  | [Weapon System](./developer/weapons.md)                     | Projectiles, modifiers, reload, damage pipeline              |
| 7  | [Cops N Crooks](./developer/cops-n-crooks.md)               | NPC AI, spawning, wanted system, bounty tracking             |
| 8  | [Gadget System](./developer/gadgets.md)                     | Cars, jetpacks, fuel, physics                                |
| 9  | [Civilian NPCs](./developer/civilians.md)                   | Behaviors, spawning, trader interaction                      |
| 10 | [Item System](./developer/items.md)                         | Parsing, unique items, fuel, wearables                       |
| 11 | [UI Framework](./developer/ui-framework.md)                 | Inventory, scoreboard, signs, loot chests, holograms         |
| 12 | [Version Compatibility](./developer/compatibility.md)       | NMS adapters, version detection, recoil                      |
| 13 | [Configuration Reference](./developer/configuration.md)     | All YAML files, settings, formulas, defaults                 |

[Full Developer Docs Index](./developer/README.md)

---

### Quick Reference

| Topic                          | Location                                                                                |
|--------------------------------|-----------------------------------------------------------------------------------------|
| First-time server setup        | [Database & Setup → Setup Checklist](./features/database.md#first-time-setup-checklist) |
| Required dependencies          | [v0.7.3-DEV Changelog → New Requirements](./v0.7.3-DEV/CHANGELOG.md#new-requirements)   |
| Cop configuration (`cops.yml`) | [Cops N Crooks → Configuration](./features/cops-n-crooks.md#configuration)              |
| Wearable traits                | [Wearables → Traits](./features/wearables.md#traits)                                    |
| Trade sign setup               | [Trade Signs → Setting Up a Sign](./features/trade-signs.md#setting-up-a-sign)          |
| Loot chest tiers               | [Loot Chests → Tiers](./features/loot_chests.md#tiers)                                  |
| Economy admin commands         | [Economy → Admin Commands](./features/economy.md#admin-economy-commands)                |

---

*All commands use the `/glw` dispatcher (alias: `/gangland`).*
