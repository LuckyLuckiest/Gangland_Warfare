# Gangland Warfare

> A GTA-inspired Minecraft plugin bringing street-level gang warfare, police pursuit, and an underground economy to your
> server.

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21%2B-brightgreen?style=flat-square" alt="Minecraft">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square" alt="Java">
  <img src="https://img.shields.io/badge/Version-0.7.3--DEV-blue?style=flat-square" alt="Version">
  <img src="https://img.shields.io/badge/Build-Maven-red?style=flat-square" alt="Build">
</p>

---

## Overview

Gangland Warfare is a multi-module Spigot/Paper plugin that lets players form gangs, accumulate wealth, build a wanted
level, and evade — or become — the police. It features a fully custom weapon system, AI-driven cop NPCs, a hierarchical
rank engine, loot chests, and a persistent economy backed by either MySQL or SQLite.

---

## Features

### 🚔 Cops N Crooks

Police NPCs powered by the Citizens API pursue wanted players, engage in combat, and make arrests. Cop count and
strength scale with the player's wanted level — from a pair of rookie officers at one star to a military response at
five. Arrested players are handcuffed, jailed, and held until released.

### 🔫 Weapons

A fully custom weapon system with configurable fire modes (auto, burst, single), magazine-based reload mechanics,
armor-piercing, bullet penetration, flat damage, and projectile tracers. Cops carry weapons too, drawing from their own
configurable loadouts per tier.

### 🛡️ Wearables

Custom armor pieces with a base damage reduction percentage and stackable protective traits — `REINFORCED`,
`BULLETPROOF`, `PADDED`, `TOUGHENED`, `FIRE_RESISTANT`, `REACTIVE`, and `LIGHTWEIGHT`. Traits from multiple pieces
stack.

### 🔧 Repair System

Weapons and wearables can be repaired using configurable materials — from disposable field kits that restore a small
amount of durability to full repair kits that restore an item completely.

### 🏪 Trade Signs

In-world signs that let players buy and sell weapons and ammunition at fixed prices. No external economy plugin
required.

### ⭐ Wanted & Bounty

A kill-streak-driven wanted level that escalates the police response. A parallel bounty system lets players place
rewards on each other's heads, with a kill multiplier for sustained hot streaks.

### 🏴 Gangs

Players form gangs with a configurable rank hierarchy, a shared bank account with individual contribution tracking, gang
colors and display names, and a bidirectional alliance system.

### 🏦 Economy

Dual-layer economy: a cash balance for everyday transactions and a higher-capacity personal bank account. Death costs
15% of your cash balance. Admin commands support bulk operations across all online players.

### 🗺️ Waypoints

Admin-placed teleportation destinations with configurable costs, timers, cooldowns, and safe zones. Waypoints can be
public, gang-restricted, or permission-gated.

### 📦 Loot Chests

Randomized reward containers that unlock on a countdown timer. Five rarity tiers — Common through Legendary — with the
upper tiers locked behind collectible keys. Rewards include money, XP, weapons, ammo, and more.

### 📊 Scoreboard

Live player stats with an animated title and per-row update intervals. Three rendering drivers available, including an
interactive mode for advanced UI use cases.

---

## Requirements

| Dependency                                                                | Type         | Notes                                                    |
|---------------------------------------------------------------------------|--------------|----------------------------------------------------------|
| [Citizens](https://www.spigotmc.org/resources/citizens.13811/)            | **Required** | Powers all police NPCs. Plugin will not load without it. |
| [NBTAPI](https://www.spigotmc.org/resources/nbt-api.7939/)                | **Required** | Custom item data for weapons, ammo, and wearables.       |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional     | Placeholders in messages and scoreboards.                |
| [Vault](https://www.spigotmc.org/resources/vault.34315/)                  | Optional     | Economy hook for cross-plugin compatibility.             |
| [ViaVersion](https://www.spigotmc.org/resources/viaversion.19254/)        | Optional     | Multi-version client support.                            |

---

## Installation

1. Download the latest release JAR from the [Releases](../../releases) page.
2. Place the JAR in your server's `plugins/` folder.
3. Install **Citizens** and **NBTAPI** — both are required.
4. Start the server once to generate all configuration files.
5. Configure `settings.yml`, `cops.yml`, and other files to your liking.
6. Restart the server.
7. Use `/glw cop spawner set` in-world to place cop spawn points.
8. Use `/glw jail create` to configure jail locations.

All commands use the `/glw` dispatcher (alias: `/gangland`).

---

## Configuration

| File               | Purpose                                                           |
|--------------------|-------------------------------------------------------------------|
| `settings.yml`     | Database, economy, wanted level, bounty, gang, and level settings |
| `cops.yml`         | Cop tier stats, AI behavior, spawn radii, and detainment options  |
| `wearables.yml`    | Custom armor pieces and their protective traits                   |
| `repair.yml`       | Repair materials, uses, and restoration amounts                   |
| `ammunition.yml`   | Ammo types and their item representations                         |
| `weapon/*.yml`     | Individual weapon configurations                                  |
| `unique_items.yml` | Phone, keys, lockpicks, and other special items                   |
| `scoreboard.yml`   | Scoreboard driver, layout, and animation                          |

---

## Documentation

Full documentation is available in the [`documentation/`](./documentation/) folder.

| Guide                                                              | Description                                       |
|--------------------------------------------------------------------|---------------------------------------------------|
| [Cops N Crooks](./documentation/v0.7.3-DEV/cops-n-crooks.md)       | NPC AI, spawning, tiers, and configuration        |
| [Jail & Detainment](./documentation/v0.7.3-DEV/jail-detainment.md) | Handcuffing, jailing, and the detainment API      |
| [Wearables](./documentation/v0.7.3-DEV/wearables.md)               | Traits, damage pipeline, and configuration        |
| [Repair System](./documentation/v0.7.3-DEV/repair.md)              | Materials, restore values, and configuration      |
| [Weapons](./documentation/features/weapons.md)                     | Fire modes, ammo, damage modifiers, and commands  |
| [Wanted & Bounty](./documentation/features/wanted-bounty.md)       | Star scaling, decay, and bounty multipliers       |
| [Trade Signs](./documentation/features/trade-signs.md)             | Sign format and setup                             |
| [Gangs](./documentation/features/gangs.md)                         | Creation, ranks, bank, and alliances              |
| [Economy](./documentation/features/economy.md)                     | Balances, bank, death penalty, and admin commands |
| [Waypoints](./documentation/features/waypoints.md)                 | Types, teleportation, and safe zones              |
| [Loot Chests](./documentation/features/loot-chests.md)             | Tiers, keys, and loot table configuration         |
| [Levels](./documentation/features/levels.md)                       | XP formula and skill upgrades                     |
| [Ranks](./documentation/features/ranks.md)                         | Hierarchy, inheritance, and permission management |
| [Database & Setup](./documentation/features/database.md)           | MySQL/SQLite, auto-save, and setup checklist      |

---

## Module Structure

| Module                             | Purpose                                               |
|------------------------------------|-------------------------------------------------------|
| `gangland-impl`                    | Plugin entry point, commands, listeners, and managers |
| `cops-n-crooks`                    | Cop AI, spawning, detainment, and jail logic          |
| `gangland-weapon`                  | Weapon, ammo, and projectile system                   |
| `plugin-persistence`               | Generic repository pattern and database abstraction   |
| `gangland-util`                    | Shared utilities and dependency injection container   |
| `scoreboard-api`                   | FastBoard-based scoreboard rendering                  |
| `inventory-api`                    | Custom inventory and GUI framework                    |
| `sign-api`                         | Sign interaction system                               |
| `lootchest-api`                    | Loot chest system with hologram support               |
| `gangland-build`                   | Shade assembly — produces the final deployable JAR    |
| `gangland-compatibility/version-*` | NMS adapters for Minecraft 1.10–1.21                  |

---

## Building

```bash
mvn clean package -DskipTests
```

The final shaded JAR is produced by the `gangland-build` module.

**Java 21** and **Maven** are required to build the project.
