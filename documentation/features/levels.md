# Levels

[← Loot Chests](./loot_chests.md) | [Back to Index](../README.md) | [Next: Unique Items →](./unique-items.md)

---

## Overview

Every player has a level and an XP pool. As XP accumulates, the player levels up. Higher levels unlock access to higher
loot chest tiers and cost more to upgrade skills. The XP required to reach the next level grows with each level using a
configurable formula, making early leveling quick and later levels a meaningful grind.

---

## How It Works

- Players earn XP through gameplay (kills, loot, quests, etc.).
- Once a player's XP crosses the threshold for their current level, they level up automatically.
- The XP required to reach the next level is calculated from a formula that takes the current level into account — each
  level costs more than the last.
- The maximum level is capped at **100** by default.

---

## XP Formula

The XP required to advance from one level to the next is calculated as:

```
XP required = base × level ^ 1.5
```

With the default base of **1,000**:

| Level | XP Required |
|-------|-------------|
| 1     | 1,000       |
| 5     | 11,180      |
| 10    | 31,623      |
| 25    | 125,000     |
| 50    | 353,553     |
| 100   | 1,000,000   |

The formula uses three available variables: `base` (the base XP amount), `level` (the player's current level), and
`experience` (the player's current accumulated XP).

---

## Skill Upgrades

Players can spend money to purchase skill upgrades. Each upgrade costs more than the previous, calculated by a separate
formula:

```
Cost = base × level ^ 1.8
```

The default increment per upgrade is **1** and the base cost is **500**.

---

## Loot Chest Tier Gating

Some loot chest tiers require a minimum player level before the player can access them:

| Tier      | Minimum Level |
|-----------|---------------|
| Common    | 0             |
| Uncommon  | 5             |
| Rare      | 10            |
| Epic      | 15            |
| Legendary | 25            |

A player below the required level cannot open items from that tier, even if they have the required key.

---

## Commands

| Command      | Description                                                       |
|--------------|-------------------------------------------------------------------|
| `/glw level` | Shows your current level, XP, and progress toward the next level. |

---

## Configuration

In `settings.yml`:

```yaml
User:
   Level:
      Maximum_Level: 100
      Base_Amount: 1_000              # The `base` variable in the XP formula
      Formula: "base * level ^ 1.5"  # XP required to reach the next level

      Skill:
         Upgrade: 1                    # How many skill points gained per upgrade
         Cost: 500                     # Base cost for the first skill upgrade
         Formula: "base * level ^ 1.8" # Upgrade cost growth formula
```

The formula field accepts standard mathematical expressions. Available variables: `base`, `level`, `max`, `experience`.

---

[← Loot Chests](./loot_chests.md) | [Back to Index](../README.md) | [Next: Unique Items →](./unique-items.md)
