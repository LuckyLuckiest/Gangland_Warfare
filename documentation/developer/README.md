# Gangland Warfare -- Developer Documentation

[Back to Documentation Index](../README.md)

---

## About

This section contains in-depth technical documentation for developers working on the Gangland Warfare
codebase. It covers architecture, module internals, design patterns, and implementation details.

---

## Developer Guides

| #  | Guide                                          | Summary                                                           |
|----|------------------------------------------------|-------------------------------------------------------------------|
| 1  | [Architecture Overview](./architecture.md)     | Plugin lifecycle, initialization flow, event system, module graph |
| 2  | [Module Reference](./modules.md)               | Every module with classes, purpose, and dependencies              |
| 3  | [Beans System & DI](./dependency-injection.md) | Bean framework, phases, lifecycle, autowiring, listener discovery |
| 4  | [Persistence Layer](./persistence.md)          | Repository pattern, database handler, tables, queries, auto-save  |
| 5  | [Command System](./commands.md)                | Argument tree, command dispatch, tab completion, adding commands  |
| 6  | [Weapon System](./weapons.md)                  | Projectiles, modifiers, reload, spread, recoil, damage pipeline   |
| 7  | [Cops N Crooks](./cops-n-crooks.md)            | NPC AI state machines, spawning, wanted system, bounty tracking   |
| 8  | [Gadget System](./gadgets.md)                  | Cars, jetpacks, fuel, repair, physics                             |
| 9  | [Civilian NPCs](./civilians.md)                | Behavior states, spawning, trader interaction, navigation         |
| 10 | [Item System](./items.md)                      | Item parsing, unique items, fuel, wearables, repair interface     |
| 11 | [UI Framework](./ui-framework.md)              | Inventory, scoreboard, signs, loot chests, holograms              |
| 12 | [Version Compatibility](./compatibility.md)    | NMS adapters, version detection, recoil implementation            |
| 13 | [Configuration Reference](./configuration.md)  | All YAML files, settings, formulas, defaults                      |

---

## Quick Reference

| Topic                          | Location                                                      |
|--------------------------------|---------------------------------------------------------------|
| How initialization works       | [Architecture -> Bean Bootstrap Pipeline](./architecture.md)  |
| How to add a new command       | [Commands -> Adding a New Command](./commands.md)             |
| How the damage pipeline works  | [Weapons -> Damage Pipeline](./weapons.md)                    |
| How NPC AI decisions are made  | [Cops N Crooks -> Cop AI State Machine](./cops-n-crooks.md)   |
| How DI wires listeners         | [Beans -> Listener Auto-Discovery](./dependency-injection.md) |
| How to create a new repository | [Persistence -> Repository Pattern](./persistence.md)         |
| How car physics work           | [Gadgets -> Car System](./gadgets.md)                         |
| How custom inventories work    | [UI Framework -> Inventory System](./ui-framework.md)         |
| How to add a new MC version    | [Compatibility -> Adding New Versions](./compatibility.md)    |
| All configuration options      | [Configuration Reference](./configuration.md)                 |

---

## Project Structure

```
gangland_warfare/
├── gangland-impl/              Main plugin (entry point, commands, listeners, managers)
├── gangland-build/             Shade plugin assembly (final JAR)
├── gangland-core/              DI container, reflection, utilities
├── gangland-item/              Item parsing, fuel, unique items, wearables
├── plugin-persistence/         Repository pattern, database, file persistence
├── plugin-common/              Logger, exception hierarchy
├── gangland-features/
│   ├── cops-n-crooks/          Police NPCs, civilians, wanted/bounty, jail
│   ├── gangland-weapon/        Weapon engine, projectiles, modifiers
│   ╰── gangland-gadget/        Cars, jetpacks, fuel, repair
├── gangland-ui/
│   ├── inventory-api/          Custom inventory framework
│   ├── scoreboard-api/         FastBoard scoreboard
│   ├── sign-api/               Sign interaction system
│   ├── lootchest-api/          Loot chest with cracking
│   ╰── hologram-api/           Floating text displays
├── gangland-compatibility/
│   ├── version-impl/           Adapter interfaces
│   ╰── version-1_10_R1..R7/   28 NMS adapter modules (MC 1.10-1.21)
╰── documentation/
    ├── features/               User-facing feature guides
    ├── v0.7.3-DEV/             Version-specific docs
    ╰── developer/              This section (technical docs)
```

---

## Technology Stack

| Component        | Technology                  | Version |
|------------------|-----------------------------|---------|
| Language         | Java                        | 21      |
| Server Platform  | Spigot (not Paper)          | 1.21.x  |
| Build System     | Maven (multi-module)        | 3.x     |
| Database Pool    | HikariCP                    | 7.0.2   |
| Database Engines | MySQL, SQLite               | --      |
| NPC Framework    | Citizens                    | 2.0.42  |
| NBT Library      | NBTAPI                      | 2.15.6  |
| Material Compat  | XSeries                     | 13.6.0  |
| Scoreboard       | FastBoard                   | 2.1.5   |
| Math Expressions | exp4j                       | 0.4.8   |
| Annotations      | Lombok                      | 1.18.44 |
| Testing          | JUnit 6.0.3, Mockito 5.23.0 | --      |
| Metrics          | bStats                      | 3.2.1   |

---

## Design Patterns Used

| Pattern                 | Where Used                                                  |
|-------------------------|-------------------------------------------------------------|
| Repository              | `plugin-persistence` -- IRepository, AbstractRepository     |
| Dependency Injection    | `gangland-core` -- BeanFactory, DependencyContainer         |
| Template Method         | AbstractRepository, DriverHandler, LootChestService         |
| Strategy                | Scoreboard drivers (V1/V2/V3), weapon parsers               |
| State Machine           | Cop AI, Civilian AI behaviors                               |
| Builder                 | Fuel, UniqueItem, Wearable, ItemBuilder                     |
| Chain of Responsibility | Sign aspects, loot chest handlers                           |
| Observer/Event          | Bukkit events + 30+ custom events                           |
| Registry                | RepositoryRegistry, ItemConverterRegistry, SignTypeRegistry |
| Adapter                 | NMS version compatibility modules                           |
| Factory                 | MultiInventoryCreation, SlotItemFactory                     |
| Command                 | Argument tree command dispatch                              |

---

*All paths are relative to the project root. Primary package: `me.luckyraven`.*
