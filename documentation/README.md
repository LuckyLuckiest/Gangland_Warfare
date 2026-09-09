# Gangland Warfare — Documentation

[← Back to Project](../README.md)

---

## 📚 Table of Contents

### Version Changelogs

| Version                                 | Status  | Changelog                                                      |
|-----------------------------------------|---------|----------------------------------------------------------------|
| [v0.7.5-DEV](./v0.7.5-DEV/CHANGELOG.md) | Current | Traders, Banker NPC, Bail — **Cops N Crooks feature-complete** |
| [v0.7.4-DEV](./v0.7.4-DEV/CHANGELOG.md) | Stable  | Civilians, five weapon categories, gadgets (cars + jetpacks)   |
| [v0.7.3-DEV](./v0.7.3-DEV/CHANGELOG.md) | Stable  | Cops N Crooks, Wearables                                       |

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
| 8  | [Wanted & Bounty](./features/wanted-bounty.md)     | Wanted stars, cop scaling, and bounty system                     |
| 9  | [Trade Signs](./features/trade-signs.md)           | In-world generic `item-buy`/`item-sell` signs (any item definition, not just weapons) |
| 10 | [Ranks](./features/ranks.md)                       | Gang rank hierarchy and permission management                    |
| 11 | [Database & Setup](./features/database.md)         | MySQL/SQLite config, auto-save, and first-time setup             |
| 12 | [Inventory System](./features/inventory.md)        | Custom GUI menus, slots, conditions, pagination, and API usage   |
| 13 | [Cops N Crooks](./features/cops-n-crooks.md)       | Police NPC AI, spawning, pursuit, and arrest                     |
| 14 | [Traders](./features/traders.md)                   | Trader NPCs — buy, barter, sell, tip; mood and traits (module: `gangland-npc-shops`) |
| 15 | [Bank & Banker](./features/bank.md)                | Banker NPC, tier ladder, daily caps, interest, loans (module: `gangland-npc-shops`) |
| 16 | [Jail & Detainment](./features/jail-detainment.md) | Handcuffing, jailing, bail, bribery, and sentence timers         |

Weapons, ammunition and wearables are provided by the standalone **Bartizan** plugin as of 0.9.0, not this repo —
see its own documentation in `E:\Programming\java\Bartizan\documentation`.

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
| 6  | [Cops N Crooks](./developer/cops-n-crooks.md)               | Cop NPC AI, spawning, wanted system, bounty tracking (module: `cops-n-crooks`) |
| 7  | [Gadget System](./developer/gadgets.md)                     | Cars, jetpacks, fuel, physics (module: `gangland-gadget`)    |
| 8  | [Civilian NPCs](./developer/civilians.md)                   | Behaviors, spawning, trader interaction (module: `gangland-civilians`, split out of cops-n-crooks in 0.9.0) |
| 9  | NPC Shops                                                    | Trader and banker NPC shops (module: `gangland-npc-shops`, split out of cops-n-crooks in 0.9.0) — no dedicated developer-internals page yet; see the [Traders](./features/traders.md) and [Bank & Banker](./features/bank.md) feature guides |
| 10 | [Item System](./developer/items.md)                         | Parsing, unique items, fuel (item conversion/serialization now lives in Keystone's `keystone-item`) |
| 11 | [UI Framework](./developer/ui-framework.md)                 | Inventory, scoreboard, signs, loot chests, holograms         |
| 12 | [Configuration Reference](./developer/configuration.md)     | All YAML files, settings, formulas, defaults                 |

Bartizan integration (what the core gets from the weapons plugin, and what degrades without it) is documented in
[`bartizan-integration.md`](./bartizan-integration.md); server-owner migration notes are in
[`migration-0.9.0.md`](./migration-0.9.0.md). Recoil is documented in
[Version Compatibility](./developer/compatibility.md), now a Bartizan-side reflective packet call rather than an
NMS adapter this repo ships.

[Full Developer Docs Index](./developer/README.md)

---

### Quick Reference

| Topic                          | Location                                                                                        |
|--------------------------------|-------------------------------------------------------------------------------------------------|
| First-time server setup        | [Database & Setup → Setup Checklist](./features/database.md#first-time-setup-checklist)         |
| Required dependencies          | [v0.7.3-DEV Changelog → New Requirements](./v0.7.3-DEV/CHANGELOG.md#new-requirements)           |
| Cop configuration (`cops.yml`) | [Cops N Crooks → Configuration](./features/cops-n-crooks.md#configuration)                      |
| Trade sign setup               | [Trade Signs → Setting Up a Sign](./features/trade-signs.md#setting-up-a-sign)                  |
| Loot chest tiers               | [Loot Chests → Chest Tiers & Unlock Items](./features/loot_chests.md#chest-tiers--unlock-items) |
| Trader traits                  | [Traders → Traits](./features/traders.md#traits)                                                |
| Bank tier ladder               | [Bank & Banker → Tier Ladder](./features/bank.md#tier-ladder)                                   |
| Bail costs                     | [Jail & Detainment → Bail](./features/jail-detainment.md#bail)                                  |
| Economy admin commands         | [Economy → Admin Commands](./features/economy.md#admin-economy-commands)                        |

---

*All commands use the `/glw` dispatcher (alias: `/gangland`).*
