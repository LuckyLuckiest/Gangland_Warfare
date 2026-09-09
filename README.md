# Gangland Warfare

> A GTA-inspired Minecraft plugin bringing street-level gang warfare, police pursuit, and an underground economy to your
> server.

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21%2B-brightgreen?style=flat-square" alt="Minecraft">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square" alt="Java">
  <img src="https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2FLuckyLuckiest%2FGangland_Warfare%2Fmaster%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D%27revision%27%5D&label=Version&prefix=v&style=flat-square&color=blue" alt="Version">
  <img src="https://img.shields.io/badge/Build-Maven-red?style=flat-square" alt="Build">
</p>

---

## Overview

Gangland Warfare is a multi-module Spigot/Paper plugin that lets players form gangs, accumulate wealth, build a wanted
level, and evade — or become — the police. It features AI-driven cop NPCs, a hierarchical rank engine, loot chests,
and a persistent economy backed by either MySQL or SQLite. Since 0.9.0 the weapon/ammo/wearable system lives in the
standalone companion plugin **Bartizan** (optional — cops and civilians are skipped entirely by the module loader (`module.plugin.missing`), and turf is skipped with them because it depends on civilians without it; see
[`documentation/bartizan-integration.md`](./documentation/bartizan-integration.md)).

---

## Features

### 🚔 Cops N Crooks

Police NPCs powered by the Citizens API pursue wanted players, engage in combat, and make arrests. Cop count and
strength scale with the player's wanted level — from a pair of rookie officers at one star to a military response at
five. Arrested players are handcuffed, jailed, and held until released.

### 🏪 Trade Signs

In-world `item-buy`/`item-sell` signs that let players buy and sell any registered item definition at fixed prices —
weapons and ammo included when Bartizan is installed. No external economy plugin required.

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

| Dependency                                                                | Type         | Notes                                                                |
|---------------------------------------------------------------------------|--------------|-----------------------------------------------------------------------|
| Keystone                                                                  | **Required** | DI/bean container, persistence, command framework. Plugin will not load without it. |
| [NBTAPI](https://www.spigotmc.org/resources/nbt-api.7939/)                | **Required** | Custom item data (unique items, loot chests, and more).             |
| [Citizens](https://www.spigotmc.org/resources/citizens.13811/)            | Optional     | Powers cop and civilian NPCs. Without it, NPC spawning is skipped with a logged fault — the server still boots. |
| Bartizan                                                                  | Optional     | The companion weapons plugin (weapons, ammo, wearables, projectiles). Without it, the civilians, cops-n-crooks and gadget modules are skipped entirely and turf with them and weapon-related item vocabularies do not resolve. |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional     | Placeholders in messages and scoreboards.                            |
| [Vault](https://www.spigotmc.org/resources/vault.34315/)                  | Optional     | Economy hook for cross-plugin compatibility.                          |
| [ViaVersion](https://www.spigotmc.org/resources/viaversion.19254/)        | Optional     | Multi-version client support.                                        |

---

## Installation

1. Install **Keystone** and **NBTAPI** — both are required.
2. Download the latest release JAR from the [Releases](../../releases) page.
3. Place the JAR in your server's `plugins/` folder, and drop the runtime module jars you want into
   `plugins/Gangland_Warfare/modules/`.
4. Install **Citizens** (NPC spawning) and **Bartizan** (weapons) if you want the full experience — both are
   optional, and the server boots without them with a logged fault per missing-dependent module.
5. Start the server once to generate all configuration files.
6. Configure `settings.yml`, `cops.yml`, and other files to your liking.
7. Restart the server.
8. Use `/glw cop spawner set` in-world to place cop spawn points.
9. Use `/glw jail create` to configure jail locations.

All commands use the `/glw` dispatcher (alias: `/gangland`).

---

## Configuration

| File               | Purpose                                                           |
|--------------------|-------------------------------------------------------------------|
| `settings.yml`     | Database, economy, wanted level, bounty, gang, and level settings |
| `cops.yml`         | Cop tier stats, AI behavior, spawn radii, and detainment options (ships inside the cops-n-crooks module jar) |
| `unique_items.yml` | Phone, keys, lockpicks, and other special items                   |
| `scoreboard.yml`   | Scoreboard driver, layout, and animation                          |

Weapon/ammo/wearable configuration (`weapon/*.yml`, `ammunition.yml`, `wearables.yml`) is Bartizan's, generated
under `plugins/Bartizan/` — not this plugin's config surface.

---

## Documentation

Full documentation is available in the [`documentation/`](./documentation/) folder.

| Guide                                                            | Description                                       |
|------------------------------------------------------------------|---------------------------------------------------|
| [Cops N Crooks](./documentation/features/cops-n-crooks.md)       | NPC AI, spawning, tiers, and configuration        |
| [Jail & Detainment](./documentation/features/jail-detainment.md) | Handcuffing, jailing, and the detainment API      |
| [Wanted & Bounty](./documentation/features/wanted-bounty.md)     | Star scaling, decay, and bounty multipliers       |
| [Trade Signs](./documentation/features/trade-signs.md)           | Generic `item-buy`/`item-sell` sign format and setup |
| [Bartizan integration](./documentation/bartizan-integration.md)  | What the weapons plugin provides, and what degrades without it |
| [0.9.0 migration notes](./documentation/migration-0.9.0.md)      | Server-owner upgrade guide from 0.8.x             |
| [Gangs](./documentation/features/gangs.md)                       | Creation, ranks, bank, and alliances              |
| [Economy](./documentation/features/economy.md)                   | Balances, bank, death penalty, and admin commands |
| [Waypoints](./documentation/features/waypoints.md)               | Types, teleportation, and safe zones              |
| [Loot Chests](./documentation/features/loot_chests.md)           | Tiers, keys, and loot table configuration         |
| [Levels](./documentation/features/levels.md)                     | XP formula and skill upgrades                     |
| [Ranks](./documentation/features/ranks.md)                       | Hierarchy, inheritance, and permission management |
| [Database & Setup](./documentation/features/database.md)         | MySQL/SQLite, auto-save, and setup checklist      |

---

## Module Structure

The core jar (`gangland-impl`, `gangland-core`, `gangland-infra/*`, `gangland-ui/*`) ships no NMS and never names a
Bartizan type. Six runtime modules ship alongside it and can be dropped or added independently:

| Module                                 | Purpose                                                                     |
|-----------------------------------------|------------------------------------------------------------------------------|
| `gangland-impl`                        | Plugin entry point, commands, listeners, and managers                        |
| `cops-n-crooks` (runtime module)       | Cop AI, spawning, detainment, and jail logic                                 |
| `gangland-civilians` (runtime module)  | Civilian NPCs — split out of cops-n-crooks in 0.9.0                          |
| `gangland-npc-shops` (runtime module)  | Trader and banker NPC shops — split out of cops-n-crooks in 0.9.0            |
| `gangland-turf` (runtime module)       | Turf capture, contribution, garrison gameplay, and turf-NPC powerups         |
| `gangland-gadget` (runtime module)     | Cars (`/glw car`) and jetpacks                                               |
| `gangland-mail` (runtime module)       | Mail, gang invites, and alliance requests                                    |
| `scoreboard-api`                       | FastBoard-based scoreboard rendering                                         |
| `inventory-api`                        | Custom inventory and GUI framework                                           |
| `sign-api`                             | Sign interaction system                                                      |
| `lootchest-api`                        | Loot chest system with hologram support                                      |
| `gangland-build`                       | Shade assembly — produces the final deployable JAR                           |

The weapon system (`gangland-weapon`) and the recoil NMS adapters (`gangland-compatibility/version-*`) left this
repo entirely in 0.9.0 for the standalone **Bartizan** plugin.

---

## Building

```bash
mvn clean package -DskipTests
```

The final shaded JAR is produced by the `gangland-build` module.

**Java 17+** (JDK 17 or newer; the build targets release 17 to match Keystone) and **Maven** are required to build the project.
