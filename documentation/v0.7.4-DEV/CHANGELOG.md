# 🌆 The Streets Got Louder — v0.7.4-DEV Changelog

> *Civilians walk the block, cars idle at the curb, and the first grenade in
> plugin history just bounced off a wall. Welcome to **Part 2 of Cops N
> Crooks**.*

---

[← Back to Documentation Index](../README.md) | [Previous: v0.7.3-DEV →](../v0.7.3-DEV/CHANGELOG.md)

---

## Overview

Three big shifts land in this version. **Civilians** join the streets as their
own NPC class (friendly, hostile, or traders). The **weapon system quadruples**
from one category (guns) to five (guns, melee, incendiary, biological,
throwable), each with its own schema and physics. And **gadgets** — cars and
jetpacks — ship as a new module with persistent fuel, database-backed parking,
and shared refueling.

Under the hood, the plugin was also rewired onto a beans-based bootstrap, so
every manager now auto-initializes in phased order without a hand-written
`Initializer`.

---

## 📄 Configuration Changes

Upgrading from 0.7.3? You'll need to merge in new files and new `settings.yml`
sections — or regenerate them by bumping `Config_Version`.

**New files shipped in this version:**

| File            | Purpose                                                                |
|-----------------|------------------------------------------------------------------------|
| `civilians.yml` | Civilian NPC types and spawn groups (friendly, hostile, trader).       |
| `cars.yml`      | Drivable vehicle catalogue (materials, speed, acceleration, fuel).     |
| `money.yml`     | Cash pickup variations and per-source drop rules.                      |
| `weapon/*.yml`  | New melee, incendiary, biological and throwable weapon configurations. |

**New sections added to `settings.yml`:** `NPC_Navigation`, `Civilians`,
`Money_Drop`, `Gadgets` (with `Jetpack` and `Car` subsections), and
`Block_Regeneration`. The `Cops` section was also expanded with new tuning
keys (spawn phases, cuff cooldown, return-to-station, starting ammo).

**Required field added to every weapon file:** `Information.Category`. See
the weapons section below for the before/after shape.

---

## ✨ Features

### Civilian NPCs

Civilians are a new NPC class that share the cop AI base — they wander, flee,
trade, or return fire depending on their type. Types and spawn groups live in
a new `civilians.yml` file, and spawning is proximity-driven from
registered spawner points.

- Each type picks a Bukkit entity, health, wearables, weapon pool, item pool,
  drops, and an optional trader inventory.
- Hostile civilians fight back with weapons from their pool; non-hostile ones
  flee at their configured speed.
- Attacking a non-hostile civilian now **increments your wanted level** — the
  same system cops use.
- Trader civilians open a custom inventory on right-click using the shared
  `InventoryHandler` framework.
- Groups bind spawn locations to a type, with a population cap and activation
  radius. Civilians spawn when a player walks into range and despawn when
  everyone leaves.
- Navigation is shared with cops (see the NPC_Navigation block further down),
  so a path-finding fix lands on both at once.
- New commands under `/glw civilian …` for manual spawn, despawn, and group
  control.

**New file — `civilians.yml` shape:**

```yaml
Types:
   friendly_villager:
      Entity_Type: VILLAGER
      Health: 20
      Hostile: false
      Wearables:
         head: ""
         chest: ""
         legs: ""
         feet: ""
      Item_Pool:
         - material:BREAD
      Drops:
         Items:
            - material:EMERALD
         Experience: 5
      Inventory: trader_default      # omit for non-trader types
      AI:
         Wander_Range: 12
         Flee_Speed: 1.3
         Combat:
            Difficulty: NORMAL        # EASY | NORMAL | HARD | DEADLY

Groups:
   downtown:
      Type: friendly_villager
      Max: 6
      Proximity: 64
      Spawns:
         - world,120.5,64,-40.5
```

**Add to `settings.yml`:**

```yaml
Civilians:
   Behaviour:
      Enabled: true
      AI_Tick_Rate: 20
   Spawner_Proximity:
      Activation_Radius: 60.0
      Despawn_Radius: 80.0
      Max_Npcs_Per_Spawner: 5
      Check_Interval: 100
      Default_Type_Id: ""
```

---

### Smarter Cops

Cops now run on the shared NPC base with civilians, so every navigation fix
lands on them too. This cycle also brought dedicated cop polish.

- Targeting falls back to the most recent attacker when no wanted target is
  in line of sight, so cops stop standing still while you plink them from
  cover.
- Despawn now uses 3D distance — climbing a tower no longer makes cops
  evaporate.
- Reload handling during NPC combat is smoother; downed players are also
  blocked from reloading.
- Jail creation uses a 5-block radius check instead of exact-coordinate
  matching, so you no longer accidentally stack two jails in the same cell.
- `CopDeathEvent` fires on every cop death and is available to other
  listeners.
- `Cops` section in `settings.yml` gained phased spawn controls, cuff
  cooldown, return-to-station distance, and starting magazine count.

---

### Weapon System — Four New Categories

0.7.3 shipped one weapon category. 0.7.4 adds **melee, incendiary,
biological, and throwable** — each with its own parser, action class, and
YAML schema. Every weapon file now declares a `Category:` field that picks
the action class, and the schema under `Shoot:` / `Attack:` / `Throw:` varies
by type.

- **Melee** (`knife`, `machete`, `crowbar`) — sweep-range attacks with
  `Attack:` block: damage, range, cooldown, knockback, per-hit cooldowns,
  and melee-specific damage handling on non-living entities.
- **Incendiary** (`flamethrower`) — cone-shaped fire spray with
  `Cone_Angle`, `Fire_Duration`, and per-tick fuel `Consume_Rate`. Ignites
  entities in the cone and applies block fire where configured.
- **Biological** (`syringe_gun`) — hold-to-charge / release-to-fire single
  shots on a new `pathogen_vial` ammo type. Applies potion effects on impact.
- **Throwable** (`grenade`, `molotov`, `flashbang`, `smoke_grenade`,
  `tomahawk`) — thrown items with physics, bounce, fuse timer, and blast
  radius. Identical throwables stack via shared UUIDs so sign-bought
  grenades merge with looted ones.
- Push-on-hit is now supported by **every** weapon type, not just guns.
- A unified raytracer runs the same hit-detection loop across all five
  categories. Ricochet, block-break, and tracers now work uniformly
  regardless of weapon type.
- `WeaponEntityDamageEvent` fires for non-projectile hits on non-living
  entities (cars, armor stands, blocks) — handy for feature modules that
  want to react to melee or grenade damage without intercepting the
  projectile lifecycle.

**Required schema change — every weapon file now needs `Category:`:**

```yaml
# BEFORE (0.7.3 — category was implicit: always gun)
Information:
   Name: "&7Pistol"
   Material: IRON_HORSE_ARMOR

# AFTER (0.7.4 — required field)
Information:
   Name: "&7Pistol"
   Material: IRON_HORSE_ARMOR
   Category: gun        # gun | melee | incendiary | biological | throwable
```

**Melee example — `knife.yml`:**

```yaml
Information:
   Name: "&7Combat Knife"
   Category: melee
   Material: IRON_SWORD
Attack:
   Damage: 6.0
   Range: 2.5
   Cooldown: 8
   Knockback: 0.4
   Selective_Fire: single
```

**Incendiary example — `flamethrower.yml`:**

```yaml
Information:
   Name: "&6Flamethrower"
   Category: incendiary
   Material: BLAZE_ROD
Shoot:
   Selective_Fire: auto
   Rate: 2
   Range: 5.0
   Cone_Angle: 30.0
   Fire_Duration: 60
   Consume_Rate: 4
Ammunition:
   Ammo_Type: fuel_canister
   Capacity: 200
```

**Throwable example — `grenade.yml`:**

```yaml
Information:
   Name: "&cFrag Grenade"
   Category: throwable
   Material: LIME_DYE
Throw:
   Type: EXPLOSIVE
   Fuse_Time: 60
   Explosion_Radius: 4.0
   Explosion_Damage: 10
   Bounces: true
   Max_Bounces: 5
   Sticky: false
   Entity_Type: SNOWBALL
```

---

### Bullet Physics & Projectiles

Bullets drop now. A new gravity system pulls shots down over distance, so
zeroed-in rifles actually need range compensation.

- Projectile gravity is applied per-tick in the unified raytracer.
- Block ricochet bounces along the block normal instead of dying on contact,
  enabling skip-shots off walls and floors.
- Throwable particle trails were redone and stop cleanly on detonation —
  no more leftover smoke puffs after the blast.
- Stacking throwable UUIDs were unified, so grenades bought from signs
  merge with ones you loot.

---

### Gadgets — Cars

Cars move out of prototype territory into a proper `gangland-gadget` module.
Every parked car is now saved to the database, so your curb-side ride
survives a restart.

- New `cars.yml` catalogs every vehicle with its item, custom model data,
  lore, and per-vehicle vehicle-physics block (max speed, acceleration, max
  fuel, exhaust side).
- Parked-car persistence — position, fuel level, and exhaust side go into
  a dedicated SQL table and reload on boot.
- The minecart carrier got a wobble animation so idle cars no longer look
  like frozen blocks.
- Shift + right-click is now ignored on car interactions so the car pickup
  hotkey stops colliding with boarding.
- Car items are no longer droppable — you can't accidentally toss your
  vehicle.
- New commands: `refuel` and `defuel`.

**New file — `cars.yml` shape:**

```yaml
sports_car:
   Material: MINECART
   Display_Name: "&bSports Car"
   Custom_Model_Data: 4001
   Lore:
      - "&7Fast. Fragile. Red."
   Vehicle:
      Max_Speed: 0.95
      Acceleration: 0.05
      Max_Fuel: 200
```

**Add to `settings.yml`:**

```yaml
Gadgets:
   Car:
      Reverse_Speed_Ratio: 0.5
      Hard_Brake_Multiplier: 3.0
      Fuel_Consume_Per_Tick: 1
```

---

### Gadgets — Jetpack

The jetpack is new in 0.7.4. It's a gadget that burns fuel per-tick while
gliding and triggers a reload session when empty, so you can't tap-spam it
like a free jump.

- Activates via off-hand equip, with thrust that ramps from 10 % to 100 %
  over a configurable tick window.
- Gliding consumes fuel per-tick (not per activation), so short hops cost
  almost nothing and long flights drain smoothly.
- Thrust defaults were retuned; the "bouncing at apex" oscillation bug is
  gone.
- Particle exhaust and sound effects were overhauled.
- Refueling runs through the shared fuel component — same UX as cars.
- Reload sessions — an empty jetpack prompts a timed refuel cycle instead
  of silently cutting out mid-air.

**Add to `settings.yml`:**

```yaml
Gadgets:
   Jetpack:
      Thrust_Ramp_Ticks: 20
      Descent_Accel: 0.022
      Max_Descent_Speed: -0.5
      Horiz_Influence: 0.03
      Max_Horiz_Speed: 0.25
```

---

### Shared Fuel Component

Cars and the jetpack both used to carry their own fuel code. They now share
a single `FuelContract` component, so fuel levels persist uniformly and a
third fuel-burning gadget becomes a drop-in. Jetpack and car refuel flows
use the same underlying item and sound handling.

---

### Money System

Cash is now a proper pickup item with a dedicated `money.yml` registry.
Kills, drops, and manual placements all route through one set of rules that
server owners can tune per-source.

- Variations — small, medium, large stacks can use different materials and
  display names.
- Per-source rules — `Player_Kill`, `Civilian_Kill`, `Cop_Kill`, etc. each
  pick a variation, amount range, and drop chance.
- Custom pickup sound per variation, with name validation — invalid sound
  names fall back to a safe default instead of throwing.
- Master switch: `Money_Drop.Enabled` in `settings.yml` disables the entire
  system without editing `money.yml`.
- New commands: money give / set / reset, grouped under the new command
  category packages.

**Add to `settings.yml`:**

```yaml
Money_Drop:
   Enabled: true
```

---

### Wanted & Command Expansion

- `/glw wanted clear <player>` — reset a player's wanted level without
  killing them.
- Sub-commands were grouped into category packages (`money`, `wanted`,
  `jail`, `civilian`), so `commands.json` stays tidy and new entries slot
  in cleanly.
- Info commands were fixed — they were previously hiding half their output.
- Optional tab completions were added across several commands.
- Waypoint commands now include hover + click events on their message
  output, so you can jump to a waypoint from chat.
- Jail commands expanded with tab values and proximity-based duplicate
  prevention on `jail create`.

---

### NPC Navigation — Shared Tuning

Cops and civilians now pathfind with identical tuning. A single
`NPC_Navigation` block in `settings.yml` controls tick-rate, path
recalculation, stuck detection, and ranged stand-off distances — so a tune
for one NPC is a tune for all of them.

**Add to `settings.yml`:**

```yaml
NPC_Navigation:
   Recalculation_Ticks: 10
   Stuck_Check_Interval: 5
   Max_Stuck_Checks: 3
   Max_Hopeless_Stuck_Checks: 6
   Hopeless_Close_Threshold: 8.0
   Min_Progress_Distance: 0.75
   Ranged_Min_Distance: 7.0
   Ranged_Max_Distance: 12.0
   Min_Repath_After_Loss_Ticks: 2
```

---

### File Safety — Reload & Regeneration

Startup and `/glw reload` are much harder to break now. Missing keys get
rewritten in place, and a missing file is recreated from the jar with a
retry on the init step instead of silently failing.

- Init-time checkup scans each file for required keys and fills in missing
  ones before the loader runs.
- Reload regeneration — if you accidentally delete a section, `/glw reload`
  rewrites it.
- Expected-folder check prevents duplicate folder creation on first boot.
- Settings load order was fixed so database and file beans always see a
  valid `Settings` before spinning up.
- Block regeneration tuning got its own `Block_Regeneration` block for
  weapon-broken block restore/crack-reverse timings.

---

### Block Regeneration

The weapon `Break_Blocks` modifier now restores or uncracks blocks over
time. Per-weapon mode is set on the weapon itself (`RESTORE`, `CRACK_ONLY`,
`DESTROY`); the timing lives in `settings.yml`.

**Add to `settings.yml`:**

```yaml
Block_Regeneration:
   Restore_Delay_Ticks: 100
   Regeneration_Delay_Ticks: 100
   Regeneration_Step_Ticks: 4
```

---

### Internal — Beans System Overhaul

The old `Initializer` + manual dependency wiring is gone. Every manager,
service, and listener is now a `@Bean` in a phased graph and implements
`BeanLifecycle` for reload and shutdown hooks.

- Phase ordering — **KERNEL → FILE → DATABASE → CONFIG → LIFECYCLE →
  LISTENER → COMMAND** — each phase topologically sorts `@Bean` methods by
  their parameter dependencies.
- Constructor-injected listeners and commands via `@ListenerHandler` and
  `@CommandHandler`. No more manual `register()` calls.
- Statics decoupled — `PlaceholderService`, `CarService`, and several others
  no longer reach into static state; static setters were replaced with beans.
- Database operations send in batches to reduce individual query count.
- Full developer documentation pass: architecture, module map, DI, commands,
  weapons, cops-n-crooks, civilians, gadgets, UI framework, persistence,
  configuration, version compatibility.

---

## 🐛 Bug Fixes

### Weapons & Combat

- **Weapon hit target** — hitscan weapons occasionally missed the final
  entity in a ray because the raytracer stopped short of its bounding box.
- **Multiple projectiles registering zero hits** — volley weapons like
  shotgun pellets and minigun bursts were dropping hits when two projectiles
  overlapped in the same tick.
- **Incendiary weapon damage** — player death from fire spray was
  double-counting in edge cases and applying the penalty twice.
- **Selective fire burst count** — burst mode sometimes fired one shot too
  many because the tick counter advanced early.
- **Reload cancel on jump** — vertical movement was interrupting reloads;
  only horizontal movement should cancel them.
- **Scope-jump exploit** — players could jump while scoped to keep the
  stability bonus. Now scope-sprint/jump is blocked.
- **Durability over max** — repair kits could push a weapon's durability
  above its configured maximum.
- **First-tick shot delay** — shots fired on the exact frame you equipped a
  weapon were being swallowed by a tick-skip guard.
- **No-durability items treated as broken** — weapons configured without a
  durability component were being flagged as destroyed.
- **Grenade UUID stacking** — throwables bought from signs now merge with
  looted stacks instead of sitting in separate slots.
- **Duplicate player death events** — firing on a target that died the same
  tick was running the death handler twice.
- **Weapon data persistence** — reload ammo, selective-fire mode, and
  durability weren't being saved on shutdown, so they reset on restart.

### NPCs

- **Cop targeting attacker** — with no wanted target in range, cops idled
  instead of turning on whoever shot them.
- **Cop despawn in 3D** — the distance check was 2D only, so cops vanished
  when you climbed a tower or dug a pit.
- **NPC pushback through walls** — shove force was applying even when a
  solid block sat between the NPC and the target.
- **Civilian navigation** — flee paths sometimes picked points behind the
  threat, making civilians run toward the danger.
- **NPC portal events** — attempt to block NPCs from teleporting through
  portals on world-hop. (See the Ambiguous list for caveat.)
- **Wanted level decrement** — `WantedExecutor` wasn't reducing the level
  on its timer.
- **Jail spawn proximity** — jail creation required exact coordinates,
  letting you stack multiple jails in the same block.
- **Detainment jail ID handling** — cuffed players rejoining were resolved
  against the wrong jail key.
- **Jail throw command** — `/glw jail throw` was throwing the sender rather
  than the target argument.
- **Session completion callout** — arrest/release session end wasn't firing
  its completion message.

### Gadgets

- **Car load on startup** — parked cars weren't rehydrating from the
  database on boot.
- **Car respawn on restart** — session cleanup ran *after* save, so the
  last position was lost between restarts.
- **Jetpack upward thrust** — thrust applied twice in the first activation
  tick, producing a jolt.
- **Jetpack gliding oscillation** — altitude bounced because fuel drain
  toggled state mid-frame.
- **Jetpack activation** — refused to fire when fuel was exactly 0 instead
  of gracefully falling through.
- **Jetpack edge cases** — assorted null / equip-slot races patched.
- **Max fuel display on refuel** — action bar showed the old cap after
  swapping to a bigger tank.

### Items & UI

- **Money item interaction** — right-clicking cash in an inventory slot was
  eating the stack without crediting the player.
- **Pickup sound handling** — custom sounds from `money.yml` were throwing
  on slightly malformed names; they now validate and fall back.
- **Multi-inventory navigation** — next/previous page buttons on paginated
  inventories were skipping a page.
- **Full-inventory item picker** — a new task routes picked-up items to the
  correct slots when the inventory is full.
- **FullAutoTask class reference** — a leftover reference from a rename
  NPE'd on the first auto-fire after `/glw reload`.
- **Async `PlayerItemInitEvent`** — listeners were running off-thread while
  touching the Bukkit API; now dispatched on the main thread.

### System & Reliability

- **File reload skipping files** — some files weren't registered with the
  reload pipeline and silently ignored `/glw reload`.
- **WeaponLoader / InventoryLoader caches** — neither was refreshing on
  `/glw reload`; they do now.
- **Max health set** — `setMaxHealth` used the attribute's current value
  instead of its base, so modifiers stacked incorrectly.
- **Multiple entity drops** — a single death fired the drop listener twice
  in some death paths.
- **Settings load order** — file beans were reading `Settings` before the
  parse had finished; bootstrap order now guarantees it.
- **Plugin download via commands** — in-game update download command was
  failing to resolve the remote URL.
- **Batch DB queries** — repositories now send reads in batches to cut
  individual query count sharply.

---

## ⚠️ Possibly Fixes / Ambiguous Changes

Commits whose message didn't clearly label them as either a feature or a
fix. Flagged here so server owners can watch for behavior changes.

- **"Removed some unnecessary settings"** — unnamed `settings.yml` keys were
  deleted. If you upgrade by merging your old `settings.yml` in, diff it
  against the new default first so you don't carry dead keys forward.
- **"Fixed visibility"** — commit message doesn't specify what kind of
  visibility. Could be a Java access modifier, an inventory visibility
  flag, or an NPC/hologram being hidden from the wrong players.
- **"Tried to deny NPC portal events"** — the word *tried* suggests this
  didn't fully land. NPCs may still teleport between worlds via portals in
  some conditions.
- **"Broke the cyclic approach by removing self-referential lambda capture"**
  — latent fix. No user-facing symptom was recorded, but a
  self-referential lambda capture usually means a memory leak or
  infinite-recursion risk on reload.
- **"Decoupled the placeholder service"** — may silently fix cases where a
  plugin reload left ghost placeholders registered.
- **"Added block regeneration"** — block-break-and-heal behavior shipped
  mid-cycle and its mode is set per-weapon; if your existing weapon YAMLs
  don't specify a `Break_Blocks` mode, double-check the default matches what
  you want.

---

## Requirements

No new hard dependencies compared to v0.7.3-DEV.

| Requirement       | Details                                              |
|-------------------|------------------------------------------------------|
| Minecraft version | 1.20+ (1.21.x recommended)                           |
| Java              | Java 21 or newer                                     |
| Required plugins  | NBTAPI, Citizens                                     |
| Optional plugins  | PlaceholderAPI, Vault, ViaVersion                    |
| Server platform   | **Spigot** (not Paper — Paper-only APIs aren't used) |

---

[← Back to Documentation Index](../README.md) | [Previous: v0.7.3-DEV →](../v0.7.3-DEV/CHANGELOG.md)
