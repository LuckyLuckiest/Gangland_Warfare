# 🏙️ Gangland Warfare

> **Turn your server into a living crime world.**
> Build gangs, fight rivals, wield a full arsenal of firearms, blades, and explosives, drive the streets,
> and outrun (or become) the law.

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
- 🚶 Share the streets with civilians — rob them, or answer for it
- 🛒 Trade with stationary **Trader NPCs** — buy, barter, sell, or tip
- 🏦 Deposit cash with **Banker NPCs** across a four-tier balance ladder
- 🚔 Get arrested — then pay bail, bribe the cop, or serve your time
- 🚗 Drive cars, fly jetpacks, and refuel at the pump

---

## ⭐ Core Features

### 1. 🔫 Advanced Weapon System

A fully custom weapon engine entirely separate from vanilla combat, now with
**five weapon categories**.

- **Five weapon categories** — guns, melee, incendiary, biological, and
  throwable — each with its own action class, YAML schema, and physics
- Configurable weapons via individual YAML files — each with its own stats,
  sounds, and behavior
- Multiple fire modes: single, burst, and full-auto with per-shot cooldowns
- Ammo types and reload mechanics — weapons go dry, magazines must be
  restocked
- Weapon durability that depletes with use
- Projectile types: bullet, flare, rocket, and spread (shotgun-style)
- **Bullet gravity** — shots drop over distance, rewarding range compensation
- Headshot and critical hit system with configurable chances and bonus damage
- Bullet penetration through blocks and multiple entities with damage falloff
- Ricochet off configurable surface materials — bounces along the block normal
- Accuracy spread that increases as you fire and resets over time
- Recoil patterns that push the camera per shot
- Bullet flyby and impact sounds heard by nearby players
- Weapon modifiers: armor piercing, flat damage, block breaking, and bullet
  tracers
- **Block regeneration** — broken blocks can restore, uncrack, or stay
  destroyed per weapon (`RESTORE`, `CRACK_ONLY`, `DESTROY`)
- **Melee weapons** — knives, machetes, and crowbars with range, knockback,
  and per-hit cooldowns
- **Incendiary weapons** — flamethrower-style cone fire spray that ignites
  everything in its arc
- **Biological weapons** — hold-to-charge syringe guns that apply potion
  effects on impact
- **Throwables** — grenades, molotovs, flashbangs, smoke grenades, and
  tomahawks, with bounce physics, fuse timers, blast radius, and stackable
  UUIDs
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

Every player has two separate money pools — cash on hand and a tiered bank balance — plus a **Banker NPC** that
serves as the physical deposit counter.

- Personal cash balance — spent on gang creation, teleportation, bounties, trader buys, and more
- **Four-tier bank ladder** — Basic / Premium / Elite / Vault, each with its own max balance, daily deposit cap,
  interest rate, death-loss discount, and weekly / monthly free-loan deposits
- 🏦 **Banker NPC** — a stationary Citizens NPC that opens the bank UI on right-click; scatter as many as you want
  without per-NPC configuration
- 💰 **Rolling-window daily cap** — deposits are rate-limited over a true 24h window, not until midnight
- 📈 **Continuous interest** — bank balance earns a fraction per 24h, clamped at the tier `Max_Balance`
- 💀 Death penalty: lose a configurable percentage of your **cash** (bank balance is safe; higher tiers reduce
  the loss)
- **BigDecimal money math** — bank arithmetic uses `BigDecimal` so large balances don't drift
- **Cash pickup items** — drops are real items players loot, with small / medium / large variations in
  `money.yml`
- **Per-source drop rules** — `Player_Kill`, `Civilian_Kill`, `Cop_Kill`, etc. each pick a variation, amount
  range, and drop chance
- Vault integration support for cross-plugin compatibility
- Optional player targets on every bank command —
  `/glw bank deposit <amount> [player]`, `/glw bank withdraw <amount> [player]`
- Admin economy commands to deposit, withdraw, set, or reset balances for individual players or all online
  players

---

### 4. 🌟 Bounty & Wanted System

A risk-vs-reward layer on top of all PvP.

- Kill combos accumulate wanted stars — up to ⭐⭐⭐⭐⭐ with escalating consequences
- Each star tier spawns more cops and drains money from the player at an increasing rate
- Stars decay over time, with higher-star players waiting longer between each reduction
- Attacking a non-hostile civilian now also increments the wanted level
- Dying immediately clears all wanted stars
- 💰 Bounties can be placed by players spending their own money or set by admins
- Kill bounties multiply over time for players on sustained hot streaks
- Configurable caps on maximum bounty per player
- `/glw wanted clear <player>` — admin command to reset stars without killing

---

### 5. 🚓 Cops N Crooks — *Module Complete*

Fully AI-driven police NPCs powered by Citizens, sharing a unified NPC base
with civilians. **0.7.5-DEV closes this module** — traders, bankers, and
bail all land in this release.

- Officers spawn in the world when a player accumulates wanted stars
- Five cop tiers — Officer, Sergeant, Lieutenant, SWAT, and Military — each
  with higher stats and better equipment
- 🤖 AI state machine: Pursuit → Combat → Cuffing
- Cops navigate via the shared `NPC_Navigation` tuning, so path-finding fixes
  land on cops and civilians in lock-step
- Lower-tier cops attempt to cuff and arrest; higher-tier cops skip cuffing
  and go lethal
- When one cop enters combat range, all nearby cops in the group are alerted
- Cops fall back to targeting their most recent attacker when no wanted
  target is in range
- 3D-aware despawn — cops no longer vanish when you climb a tower or dig a pit
- Configurable cop weapon pools — vanilla items or custom Gangland weapons
- Spawner placement system with intelligent fallback for areas without
  placed spawners
- Cop count scales with wanted level via a configurable formula
- `CopDeathEvent` fires for other listeners to react to fallen officers

---

### 6. 🚶 Civilians

A new NPC class that shares the cop AI base — wander, flee, or fight. (Trade moved to dedicated **Trader NPCs**
— see next section.)

- Civilian types are defined in `civilians.yml` with per-type entity,
  health, wearables, item pool, weapon pool, drops, and AI profile
- **Friendly civilians** wander and flee from danger; attacking them
  increments your wanted level
- **Hostile civilians** return fire using weapons from their pool
- **Per-type drop chances** — friendly and hostile civilians roll loot
  with distinct probability buckets
- Combat difficulty profiles — `EASY` / `NORMAL` / `HARD` / `DEADLY` — scale aim,
  reaction time, and fire rate
- Groups bind spawn points to a type with population caps, activation
  radius, and despawn radius — civilians appear when players walk in range
  and disappear when the area empties
- Shared navigation tuning with cops via `NPC_Navigation` in `settings.yml`
- `/glw civilian …` commands for manual spawn, despawn, and group control

---

### 7. 🛒 Trader NPCs & Shop API

Stationary, damageable Citizens NPCs that run full shops.

- **Buy, barter, sell, and tip** — four distinct transaction types per trader
- **Six built-in personality traits** (`easygoing`, `hotheaded`, `stubborn`,
  `generous`, `shrewd`, `timid`) with configurable mood gain rates, friend
  discount, barter policy, and max HP — add your own in `trader_traits.yml`
- **Per-player mood** — positive-only scalar that raises on purchases and
  tips; trader gives up to `Min_Friend_Discount` off at full mood
- **Pure-swap barter** — no money changes hands on a barter; items are
  valued against the trader's trait and the shop's category rates
- **Killable traders** — `Invulnerable: false` lets players rob a shop
- **Shop API** — the UI, persistence, and transaction pipeline live in a
  shared `gangland-ui/shop-api` module for future shop surfaces
- **In-game admin editing** — spawn, edit shop key, edit trait, rename
  via anvil GUI, remove — no YAML wrestling day-to-day

---

### 8. 🔒 Jail & Detainment

The outcome of a successful arrest — with four ways out.

- Cops that successfully cuff a player initiate the detainment sequence
- Jailed players are held at a configured jail location with seized inventory
- 💸 **Pay bail** — charges a cost scaling with wanted level; returns seized items
- 🤝 **Bribe the cop** — handcuff-stage bribe (pre-jail) always succeeds if funded; jail-stage bribe rolls against
  a configured success chance with a fail-penalty sentence extension
- 💪 **Break free** — handcuffed players can tap-race their way out (25 taps in 40 ticks)
- ⏲️ **Serve your time** — automatic release after a wanted-level-scaled sentence
- Admins can handcuff, jail, and release players manually
- Players cuffed at logout are auto-routed to jail on rejoin
- Every release path funnels through a single `ReleasePipeline` — seized inventory restoration is consistent

---

### 9. 🛻 Gadgets — Cars & Jetpacks

A dedicated gadget module for drivable vehicles and deployable equipment.

- **Cars** — drivable vehicles defined in `cars.yml` with per-vehicle max
  speed, acceleration, and fuel capacity
- Every parked car is saved to the database (position, fuel, exhaust side)
  and rehydrated on boot — curb-side parking survives restarts
- Wobble animation on idle cars, shift-safe interactions, and dedicated
  `refuel` / `defuel` commands
- **Jetpacks** — off-hand-equipped flight gadget with ramped thrust, fuel-
  per-tick gliding, particle exhaust, and sound effects
- Empty jetpacks trigger a timed refuel session instead of silently cutting
  out mid-air
- **Shared fuel component** — cars and jetpacks use the same `FuelContract`,
  so refuel items and UX are consistent across gadgets

---

### 10. 📦 Loot Chests & Rewards

Randomized reward containers placed anywhere in the world.

- Admins designate any chest, barrel, or shulker box as a loot chest
- ⏳ A countdown timer ticks down before the chest becomes openable
- 🔍 **Item preview above the chest** while it ticks down — no blind clicks
- Five rarity tiers: Common, Uncommon, Rare, Epic, and Legendary
- Higher tiers require a lockpick or key — which hovers above the chest
  as a floating in-world item, resource-pack friendly
- **Drop-chance buckets** — per-item `COMMON` / `UNCOMMON` / `RARE` / `EPIC` / `LEGENDARY`
  decide how often each item rolls; tier gating controls which tables a chest
  can draw from (not individual items)
- Loot tables contain weapons, ammo, unique items, money, and XP
- Three built-in loot tables: Street Loot, Military Loot, and Supply Cache
- Weapon drops land as real weapon items under a floating **name hologram**
- Rewards also include a configurable random money and XP amount
- Level gating — certain tiers require a minimum player level to access

---

### 11. 🛡️ Wearables

Custom armor pieces with specialized damage reduction.

- Wearables sit on top of vanilla armor and plug into Gangland's full damage pipeline
- Configurable base damage reduction percentage per piece
- Seven protective traits: `REINFORCED`, `BULLETPROOF`, `PADDED`, `TOUGHENED`, `FIRE_RESISTANT`, `REACTIVE`, and
  `LIGHTWEIGHT`
- Traits from multiple pieces stack for combined protection
- Durability depletes as damage is absorbed
- Four built-in pieces: Police Vest, Police Helmet, Gang Jacket, and Heavy Vest
- Fully configurable via `wearables.yml` — add, modify, or remove pieces freely

---

### 12. 📊 Player Leveling

An XP-based progression system that gates access to higher rewards.

- Players earn XP through kills, looting, and gameplay
- Configurable XP formula — early levels are quick, later levels are a meaningful grind
- Maximum level of 100 by default
- Skill upgrade system: spend money to purchase upgrades with cost scaling per level
- Level requirements on loot chest tiers

---

### 13. 🗺️ Waypoints & Teleportation

Named teleport destinations placed by admins.

- Five waypoint types: Spawn, Gang, Safe Zone, Quest, and Global
- Configurable teleport cost, wait timer, and cooldown per waypoint
- 🕊️ Safe zones disable PvP for players inside them
- Gang waypoints are restricted to members of a specific gang
- Permission nodes auto-generated per waypoint for fine-grained access control
- Teleport cancels if the player moves or takes damage during the wait timer
- Chat output includes hover and click events to teleport directly

---

### 14. 🪧 Trade Signs

In-world buy and sell signs for weapons and ammunition.

- Place signs that let players buy or sell weapons and ammo at fixed prices
- Two sign types: weapon signs and ammo signs
- Throwables bought from signs stack with looted ones via shared UUIDs
- No additional plugin is required — works entirely within Gangland Warfare
- Configurable item, price, and quantity per sign

---

### 15. 📋 Scoreboard

Live stat display for players.

- Animated titles with configurable scroll speed
- Displays money, level, wanted stars, gang name, and other live values
- Multiple scoreboard drivers supported

---

### 16. 🎒 Unique Items

Special items with controlled inventory behavior.

- 📱 Phone — permanently in the last hotbar slot, given on join and respawn, cannot be dropped
- 🔑 Lockpick — consumable required to unlock Rare loot chests
- 🗝️ Epic Key and Legendary Key — required for Epic and Legendary chest tiers
- Configurable auto-give on join, slot pinning, drop-on-death, and duplicate rules

---

### 17. 🖥️ Server Infrastructure

Backend features for operators and developers.

- MySQL and SQLite support via HikariCP — selected in `settings.yml`
- **Beans-based bootstrap** — phased dependency-injection pipeline
  (KERNEL → FILE → DATABASE → CONFIG → LIFECYCLE → LISTENER → COMMAND) with
  auto-registered `@Bean`, `@ListenerHandler`, and `@CommandHandler` classes
- **YAML validation layer** — every loader reports unknown keys with file +
  line number, so stale settings never silently rot between releases
- **Reload regeneration** — missing config sections are rewritten on
  `/glw reload`; a missing file is recreated from the jar with an init retry
- **Debug logging block** — per-module `DEBUG` toggle in `settings.yml`,
  no JVM flags required
- **Vault permission integration** — soft-dep; falls back to the built-in
  permission system when Vault is absent
- `/glw permissions` command for inspecting / granting / revoking permissions
  in-game
- `/gangland` alias for `/glw`
- Batched database queries to reduce individual query overhead
- Automatic periodic data saving and cache cleanup
- 🌐 Multi-language message support with jar-bundled fallback
- Resource pack auto-loading on join
- Custom scoreboard via FastBoard
- 🧰 Public developer API with events for weapons, cops, civilians, traders,
  bounty, and more

---

## 🚧 Upcoming Features

The following systems are actively in development:

- 🔐 Safe Cracking Minigame and Advanced Chest Mechanics
- ⚔️ Gang Attacks and Turf Wars
- 🏆 Challenges and Competitive Events
- 🏍️ More Vehicle Types (motorcycles, boats, aircraft)
- 🏠 Purchasable Houses and Properties
- 🏦 Player-to-Player Market / Auction House
- 📜 Quest and Mission System

---

## 📋 Requirements

| Requirement       | Details                                                    |
|-------------------|------------------------------------------------------------|
| Minecraft version | 1.20+ (tested; earlier versions are partially implemented) |
| Java              | Java 17 or newer                                           |
| Server platform   | **Spigot** (Paper-only APIs are not used)                  |
| Required plugins  | NBTAPI, Citizens                                           |
| Optional plugins  | PlaceholderAPI, Vault, ViaVersion                          |

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

### 🎨 Automatic Resource Pack Push

If the resource pack feature is enabled in `settings.yml`, the plugin automatically sends a resource pack download
prompt to every player when they join the server. Players are directed to a URL configured by the server administrator.
> Players may accept or decline the prompt; declining does not prevent them from playing unless the server admin has
> enabled the kick-on-decline option.

---

## 💬 Support & Feedback

- 🐛 Bug reports: open an issue on the GitHub repository
- 🔄 Configuration changes take effect with `/glw reload` or a server restart
- 📖 Full documentation is available in
  the [github page](https://github.com/LuckyLuckiest/Gangland_Warfare/tree/master/documentation)

> ⚠️ This plugin is actively under development. Bugs are expected. Please report them rather than leaving a low rating —
> every report helps. Don't forget to leave a ⭐⭐⭐⭐⭐ rating if you enjoy it!

---

*All commands use the `/glw` dispatcher (alias: `/gangland`).*
