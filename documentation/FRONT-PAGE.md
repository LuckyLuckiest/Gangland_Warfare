# 🏙️ Gangland Warfare

> **Turn your server into a living crime world.**
> Build gangs, fight rivals, use advanced weapons, earn money, place bounties, and rise to the top of the underworld.

---

## ❓ Why Gangland Warfare?

Gangland Warfare is not just another gun or PvP plugin.
It is a complete crime ecosystem designed for GTA-style, roleplay, and competitive servers.

Players don't just fight — they:

- 🤝 Create and manage gangs with internal hierarchies and shared banks
- 💰 Control power through money, reputation, and territory
- 🎯 Hunt players with bounties and wanted levels
- 📈 Progress through levels, ranks, and loot
- 🚔 Survive police pursuit or don a badge and become the law

---

## ⭐ Core Features

### 1. 🔫 Advanced Weapon System

A fully custom weapon engine entirely separate from vanilla combat.

- Configurable weapons via individual YAML files — each with its own stats, sounds, and behavior
- Multiple fire modes: single, burst, and full-auto with per-shot cooldowns
- Ammo types and reload mechanics — weapons go dry, magazines must be restocked
- Weapon durability that depletes with use and can be repaired with materials
- Projectile types: bullet, flare, rocket, and spread (shotgun-style)
- Headshot and critical hit system with configurable chances and bonus damage
- Bullet penetration through blocks and multiple entities with damage falloff
- Ricochet off configurable surface materials
- Accuracy spread that increases as you fire and resets over time
- Recoil patterns that push the camera per shot
- Bullet flyby and impact sounds heard by nearby players
- Weapon modifiers: armor piercing, flat damage, block breaking, and bullet tracers
- In-world holograms for dropped weapons
- 🎵 Resource pack integration for custom sounds

---

### 2. 👥 Gang System

Gangs are the social backbone of the plugin.

- Create a gang for a configurable fee — the creator becomes the owner
- Invite players, promote and demote members, and kick lower-ranked members
- Tiered rank hierarchy with custom permission inheritance — Recruit up to Leader
- 🤜🤛 Gang alliances with bidirectional agreement and friendly-fire protection
- Shared gang bank with a per-member contribution tracker
- Proportional bank payout when a gang is disbanded
- Customizable gang name, display name, color, and description
- Gang-restricted waypoints that act as private safe zones

---

### 3. 💵 Economy & Banking

Every player has two separate money pools.

- Personal cash balance — spent on gang creation, teleportation, bounties, and more
- Personal bank account — higher capacity vault that requires a one-time creation fee
- 💀 Death penalty: lose a configurable percentage of your carried cash (money in the bank is safe)
- Configurable death penalty formula — supports custom expressions and command-based execution
- Mob kills reward small random cash amounts
- Vault integration support for cross-plugin compatibility
- Admin economy commands to deposit, withdraw, set, or reset balances for individual players or all online players

---

### 4. 🌟 Bounty & Wanted System

A risk-vs-reward layer on top of all PvP.

- Kill combos accumulate wanted stars — up to ⭐⭐⭐⭐⭐ with escalating consequences
- Each star tier spawns more cops and drains money from the player at an increasing rate
- Stars decay over time, with higher-star players waiting longer between each reduction
- Dying immediately clears all wanted stars
- 💰 Bounties can be placed by players spending their own money or set by admins
- Kill bounties multiply over time for players on sustained hot streaks
- Configurable caps on maximum bounty per player

---

### 5. 🚓 Cops N Crooks

Fully AI-driven police NPCs powered by Citizens.

- Officers spawn in the world when a player accumulates wanted stars
- Five cop tiers — Officer, Sergeant, Lieutenant, SWAT, and Military — each with higher stats and better equipment
- 🤖 AI state machine: Pursuit → Combat → Cuffing
- Cops navigate toward the player, recalculating paths to avoid getting stuck
- Lower-tier cops attempt to cuff and arrest; higher-tier cops skip cuffing and go lethal
- When one cop enters combat range, all nearby cops in the group are alerted
- Configurable cop weapon pools — vanilla items or custom Gangland weapons
- Spawner placement system with intelligent fallback for areas without placed spawners
- Cop count scales with wanted level via a configurable formula

---

### 6. 🔒 Jail & Detainment

The outcome of a successful arrest.

- Cops that successfully cuff a player initiate the detainment sequence
- Jailed players are held at a configured jail location for a set duration
- Admins can handcuff, jail, and release players manually
- Detainment integrates with the wanted system — clearing stars on arrest

---

### 7. 📦 Loot Chests & Rewards

Randomized reward containers placed anywhere in the world.

- Admins designate any chest, barrel, or shulker box as a loot chest
- ⏳ A countdown timer ticks down before the chest becomes openable
- Five rarity tiers: Common, Uncommon, Rare, Epic, and Legendary
- Higher tiers require lockpicks or keys to access
- Loot tables contain weapons, ammo, repair materials, unique items, money, and XP
- Three built-in loot tables: Street Loot, Military Loot, and Supply Cache
- Rewards also include a configurable random money and XP amount
- Level gating — certain tiers require a minimum player level to access

---

### 8. 🛡️ Wearables

Custom armor pieces with specialized damage reduction.

- Wearables sit on top of vanilla armor and plug into Gangland's full damage pipeline
- Configurable base damage reduction percentage per piece
- Seven protective traits: REINFORCED, BULLETPROOF, PADDED, TOUGHENED, FIRE_RESISTANT, REACTIVE, and LIGHTWEIGHT
- Traits from multiple pieces stack for combined protection
- Durability depletes as damage is absorbed and can be repaired
- Four built-in pieces: Police Vest, Police Helmet, Gang Jacket, and Heavy Vest
- Fully configurable via `wearables.yml` — add, modify, or remove pieces freely

---

### 9. 🔧 Repair System

Weapons and wearables degrade with use and can be restored.

- Consumable repair materials with a limited number of uses each
- Flat or percentage-based durability restoration per material
- Materials can be restricted to weapons only, wearables only, or both
- Four built-in materials: Cleaning Kit, Mechanical Part, Weapon Repair Kit, and Field Kit
- Custom model data support for resource pack integration

---

### 10. 📊 Player Leveling

An XP-based progression system that gates access to higher rewards.

- Players earn XP through kills, looting, and gameplay
- Configurable XP formula — early levels are quick, later levels are a meaningful grind
- Maximum level of 100 by default
- Skill upgrade system: spend money to purchase upgrades with cost scaling per level
- Level requirements on loot chest tiers

---

### 11. 🗺️ Waypoints & Teleportation

Named teleport destinations placed by admins.

- Five waypoint types: Spawn, Gang, Safe Zone, Quest, and Global
- Configurable teleport cost, wait timer, and cooldown per waypoint
- 🕊️ Safe zones disable PvP for players inside them
- Gang waypoints are restricted to members of a specific gang
- Permission nodes auto-generated per waypoint for fine-grained access control
- Teleport cancels if the player moves or takes damage during the wait timer

---

### 12. 🪧 Trade Signs

In-world buy and sell signs for weapons and ammunition.

- Place signs that let players buy or sell weapons and ammo at fixed prices
- Two sign types: weapon signs and ammo signs
- No additional plugin required — works entirely within Gangland Warfare
- Configurable item, price, and quantity per sign

---

### 13. 📋 Scoreboard

Live stat display for players.

- Animated titles with configurable scroll speed
- Displays money, level, wanted stars, gang name, and other live values
- Multiple scoreboard drivers supported

---

### 14. 🎒 Unique Items

Special items with controlled inventory behavior.

- 📱 Phone — permanently in the last hotbar slot, given on join and respawn, cannot be dropped
- 🔑 Lockpick — consumable required to unlock Rare loot chests
- 🗝️ Epic Key and Legendary Key — required for Epic and Legendary chest tiers
- Configurable auto-give on join, slot pinning, drop-on-death, and duplicate rules

---

### 15. 🖥️ Server Infrastructure

Backend features for operators and developers.

- MySQL and SQLite support via HikariCP — selected in `settings.yml`
- Automatic periodic data saving and cache cleanup
- 🌐 Multi-language message support
- Resource pack auto-loading on join
- Custom scoreboard via FastBoard
- 🧰 Public developer API with events for weapons, cops, bounty, and more
- Permission list available at runtime: `/glw debug perms`

---

## 🚧 Upcoming Features

The following systems are actively in development:

- 🔐 Safe Cracking Minigame and Advanced Chest Mechanics
- ⚔️ Gang Attacks and Turf Wars
- 🏆 Challenges and Competitive Events
- 🚗 Vehicle System
- 🏠 Purchasable Houses and Properties
- 🤝 Dealer NPCs and Auction House
- 📜 Quest and Mission System

---

## 📋 Requirements

| Requirement       | Details                                                    |
|-------------------|------------------------------------------------------------|
| Minecraft version | 1.20+ (tested; earlier versions are partially implemented) |
| Java              | Java 21 or newer                                           |
| Required plugins  | NBTAPI, Citizens                                           |
| Optional plugins  | PlaceholderAPI, Vault                                      |

---

## 📥 Installation

1. ⬇️ Download the latest JAR from the releases section.
2. 🛑 Stop your Minecraft server.
3. 📂 Place the JAR in your server's `plugins/` folder.
4. ▶️ Start the server to generate configuration files.
5. ⚙️ Configure the plugin under the `Gangland_Warfare/` folder.
6. 🔄 Use `/glw reload` to apply configuration changes without restarting.

---

## ❤️ My Journey

Gangland Warfare is the culmination of 5 years of passionate development, countless hours of recoding, refactoring, and
relentless improvement. Originally known as Cubed-GTA, this plugin has been completely rewritten from the ground up with
a modular, multi-module architecture to deliver the ultimate GTA-style experience in Minecraft.

What started as a simple concept has evolved into a full-fledged urban warfare plugin through multiple development
cycles, periods of burnout, and continuous iteration. Every part of the codebase has been crafted to ensure stability,
performance, and an immersive gameplay experience.

Though the full game loop is still being fleshed out, this development build represents a major milestone. Community
feedback is appreciated — the comments section is the right place for it. 🙏

---

## 🔔 Disclosures

### 🔄 Automatic Update Checker

On startup, the plugin automatically contacts the SpigotMC API (`api.spigotmc.org`) every 6 hours to check whether a
newer version of the plugin is available. This is a read-only request — no player data or server data is sent. Operators
with the appropriate permission are notified in-game if an update is found.

> ⚠️ A configuration toggle for this exists in `settings.yml`, but it is not yet respected by the plugin — the update
> checker currently runs regardless of that setting. **This will be fixed in an upcoming update.**

### 🎨 Automatic Resource Pack Push

If the resource pack feature is enabled in `settings.yml`, the plugin automatically sends a resource pack download
prompt to every player when they join the server. Players are directed to a URL configured by the server administrator.
Players may accept or decline the prompt; declining does not prevent them from playing unless the server admin has
enabled the kick-on-decline option.

> ⚠️ The resource pack URL and the enable/disable toggle are fully controlled by the server administrator in
`settings.yml`. Proper configuration opt-out controls will be improved in an upcoming update.

---

## 💬 Support & Feedback

- 🐛 Bug reports: open an issue on the GitHub repository
- 🔄 Configuration changes take effect with `/glw reload` or a server restart
- 📖 Full documentation is available in the [documentation index](./README.md)

> ⚠️ This plugin is actively under development. Bugs are expected. Please report them rather than leaving a low rating —
> every report helps. Don't forget to leave a ⭐⭐⭐⭐⭐ rating if you enjoy it!

---

*All commands use the `/glw` dispatcher (alias: `/gangland`).*
