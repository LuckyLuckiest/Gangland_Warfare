# Wanted & Bounty

[← Weapons](./weapons.md) | [Back to Index](../README.md) | [Next: Trade Signs →](./trade-signs.md)

---

## Overview

The wanted level system tracks how much heat a player is carrying. Stars accumulate through kills and can be adjusted by
admins. Each star tier brings a stronger and larger police response. The bounty system runs alongside it, letting
players and gangs put a price on each other's heads.

---

## Wanted Level

### How Stars Are Earned

- **Kill combos** — the more players killed in succession, the faster stars accumulate.
    - 2 kills → 1 star
    - 5 kills → 2 stars
    - 10 kills → 3 stars
    - 15 kills → 4 stars
    - 20 kills → 5 stars
- Admin commands can set, add, or remove stars at any time.

### How Stars Are Lost

- Stars **decay over time** — the decay timer starts at `Time` (default 120 seconds) and scales up with each star:
  `time × Amount ^ stars`. A 5-star player waits significantly longer between each star reduction than a 1-star player.
- **Dying clears all wanted stars** immediately.
- Kill combos reset after `Reset_After` seconds of no kills (default 10 seconds).

### Police Response Per Star

| Stars   | Cops Sent | Minimum Tier    |
|---------|-----------|-----------------|
| 1 ★     | 2         | Officer         |
| 2 ★★    | 3         | Officer         |
| 3 ★★★   | 4         | Sergeant        |
| 4 ★★★★  | 5         | Lieutenant      |
| 5 ★★★★★ | 8         | SWAT / Military |

> The exact cop count follows the formula: `base + (stars - 1) × per-level`, capped at `max`. These values are
> configurable in `settings.yml`.

### Cost of Being Wanted

Each wanted star also takes money from the player at regular intervals. The formula is:

```
money taken = Amount * Multiplier ^ stars
```

With defaults (`Amount: 50`, `Multiplier: 5`):

| Stars   | Money taken per tick |
|---------|----------------------|
| 1 ★     | 50 × 5¹ = 250        |
| 2 ★★    | 50 × 5² = 1,250      |
| 3 ★★★   | 50 × 5³ = 6,250      |
| 4 ★★★★  | 50 × 5⁴ = 31,250     |
| 5 ★★★★★ | 50 × 5⁵ = 156,250    |

Both values are configurable in `settings.yml`.

---

## Bounty System

### How Bounties Work

A bounty is a monetary reward placed on a player or gang. Any player who kills the bounty target collects the reward.

- Bounties can be placed by players (spending their own money) or set by admins.
- There is a configurable maximum bounty cap per player/gang.
- Kill bounties stack: each kill earns a base bounty amount, and a multiplier timer can double the reward for
  consecutive kills within a time window.

### Multiplier

The kill bounty multiplier doubles every **300 seconds** of consecutive activity, up to a configurable cap (default 2×
with a 20,000 money maximum). This rewards players for sustained hot streaks.

---

## Commands

| Command                               | Description                               |
|---------------------------------------|-------------------------------------------|
| `/glw wanted`                         | View your current wanted level and stars. |
| `/glw wanted add <player> <stars>`    | Add wanted stars to a player.             |
| `/glw wanted remove <player> <stars>` | Remove wanted stars from a player.        |
| `/glw bounty`                         | View the current bounty on you.           |
| `/glw bounty set <player> <amount>`   | Place or update a bounty on a player.     |
| `/glw bounty clear <player>`          | Remove all bounty from a player.          |

---

## Configuration

In `settings.yml`:

```yaml
Wanted:
   Enable: true

   Take_Money:
      Amount: 50              # Base money deducted per tick while wanted
      Multiplier: 5           # Exponent base in the formula: Amount * Multiplier ^ stars

   Repeating_Timer:
      Enable: true
      Time: 120               # Base decay interval in seconds (at 1 star)
      Multiplier:
         Enable: true
         Amount: 1.1           # Decay timer scales as: Time * Amount ^ stars
         # Higher-star players wait longer between each star reduction

   Level:
      Increment: 1            # Stars added each time a kill threshold is crossed
      Maximum: 5              # Hard cap on wanted stars

   Kill_Combo:
      Enable: true
      Reset_After: 10         # Seconds without a kill before the combo counter resets
      Kill_Counter: # Kill thresholds that trigger each star level
         - 2                   # 2 kills → 1 star
         - 5                   # 5 kills → 2 stars
         - 10                  # 10 kills → 3 stars
         - 15                  # 15 kills → 4 stars
         - 20                  # 20 kills → 5 stars

Bounty:
   Enable: true
   Kill:
      Each: 5                 # Money added to the player's bounty per kill they commit
      Maximum: 50_000         # Hard cap on a player's total kill-accrued bounty
   Repeating_Timer:
      Enable: true
      Multiple: 2             # Multiplier applied to the bounty amount each interval
      Time: 300               # Seconds between multiplier applications
      Maximum: 20_000         # Cap on bonus bounty from the multiplier

# Cop count scaling is under the Cops key — see the Cops N Crooks guide
```

---

## API

```java
// Access the wanted executor
WantedExecutor wanted = gangland.getInitializer().getWantedExecutor();

// Get a player's current wanted level
int stars = wanted.getLevel(user);

// Add or remove stars
wanted.

addLevel(user, 1);
wanted.

removeLevel(user, 1);

// Bounty
BountyExecutor bounty = gangland.getInitializer().getBountyExecutor();

long currentBounty = bounty.getBounty(user);
bounty.

setBounty(user, 5000L);
bounty.

clearBounty(user);
```

---

[← Weapons](./weapons.md) | [Back to Index](../README.md) | [Next: Trade Signs →](./trade-signs.md)
