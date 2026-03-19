# Wanted & Bounty

[← Jail & Detainment](../v0.7.3-DEV/jail-detainment.md) | [Back to Index](../README.md) | [Next: Weapons →](./weapons.md)

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

- Stars **decay over time** — every 120 seconds without a kill, the wanted level reduces by one.
- A **kill multiplier** applies during active wanted periods — kills within the window refresh the timer and can trigger
  escalation.
- **Dying clears all wanted stars** immediately.

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

Each wanted star also takes money from the player at regular intervals:

```
money taken = 50 + (stars ^ 5)
```

A player with 5 stars loses significantly more money per tick than one with 1 star. This is configurable in
`settings.yml`.

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
wanted:
  increment: 1              # Stars added per threshold crossed
  max: 5                    # Maximum wanted stars
  money-taken: 50           # Base money deducted per star tick
  star-exponent: 5          # Exponent in money formula (50 + stars^exponent)
  decay-timer: 120          # Seconds before a star is removed

bounty:
  kill-reward: 5            # Base money earned per kill
  max-bounty: 50000         # Hard cap on a single player's bounty
  multiplier-interval: 300  # Seconds between multiplier doublings
  multiplier-cap: 20000     # Maximum extra bounty from multiplier

cops:
  count:
    base: 2
    per-level: 1
    max: 8
```

---

## API

```java
// Access the wanted executor
WantedExecutor wanted = gangland.getInitializer().getWantedExecutor();

// Get a player's current wanted level
int stars = wanted.getLevel(user);

// Add or remove stars
wanted.addLevel(user, 1);
wanted.removeLevel(user, 1);

// Bounty
BountyExecutor bounty = gangland.getInitializer().getBountyExecutor();

long currentBounty = bounty.getBounty(user);
bounty.setBounty(user, 5000L);
bounty.clearBounty(user);
```

---

[← Jail & Detainment](../v0.7.3-DEV/jail-detainment.md) | [Back to Index](../README.md) | [Next: Weapons →](./weapons.md)
