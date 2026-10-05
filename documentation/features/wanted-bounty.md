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

Since 0.15.0 stars come from **heat** when `Wanted.Heat.Enable` is true in `npc/wanted.yml` (the default). Each crime
adds heat, and the heat crosses `Star_Thresholds` (100, 250, 450, 700, 1000) to become stars.

| Crime | Heat | | Crime | Heat |
|---|---|---|---|---|
| Kill a player | 80 | | Kill a civilian | 100 |
| Hit a cop | 100 | | Fight off an arrest | 100 |
| Kill a cop | 150 | | Other crimes in the file | read by later releases |

- A crime chained within `Kill_Combo.Reset_After` seconds of the last one is multiplied by `Streak_Bonus` (1.5).
- A crime a cop saw is multiplied by `Seen_By_Cop_Multiplier` (1.5).
- A player kill inside a contested turf is multiplied by `Turf_War_Multiplier` (0.5).
- Hitting the same cop again within `Assault_Repeat_Seconds` (10) is not a new crime.
- **Not crimes:** a kill in self-defence (the other player struck first inside a 30 second fight window), a kill that
  claims a bounty players posted, and a turf defender killing a raider inside their own gang's contested turf.
- With `Heat.Enable: false` the 0.13.0 maths apply: the kill combo (2 kills = 1 star, 5 = 2, 10 = 3, 15 = 4,
  20 = 5) when `Wanted.Kill_Combo.Enable` is on, otherwise one star per counted kill. A civilian kill counts once either way.
- Admin commands and `[WANTED]` signs can set, add or remove stars at any time. Every star change carries its cause
  (crime, sign, admin, restore, decay, evasion, bribe, arrest, death), which the HUD and plugins can read.

### How Stars Are Lost

- **Evasion.** While cops hunt you, break line of sight: after `Evasion.Lost_Sight_Seconds` (3) with no cop seeing
  you a search zone opens (radius `Search_Radius`, by level). Stay hidden for `Seconds_To_Drop` (10 s at 1 star up to
  60 s at 5) and one star drops (`Drop_Mode: ALL_STARS` drops them all). Outside the zone the clock runs
  `Outside_Zone_Speed` (2x) faster. A sighting, or a shot you fire near a cop, puts you back to "seen"; being cuffed
  pauses it. Cops that are all walking home count as no pursuit.
- **The fixed decay timer** still runs when no cop is hunting you, and as the safety net otherwise: it starts at `Time`
  (default 120 seconds) and scales up with each star: `time x Amount ^ stars`. While cops hunt, evasion owns decay and the
  timer drops nothing. `Evasion.Enable: false` gives the timer back full control.
- **Dying clears all wanted stars** immediately.
- Kill combos reset after `Reset_After` seconds of no kills (default 10 seconds).

### What You See

A boss bar with the stars (red when a cop sees you, yellow with a countdown while searching, green for three seconds
when a star is lost), a star card (the crime, the tier coming and whether it cuffs or shoots; or why a star dropped),
a title and, when a star is gained, a siren, a particle ring marking the search zone, and a compass pointing the way
out of the zone (it never shows where the cops are). Switch each off under `Wanted.Hud` (`Boss_Bar`, `Star_Card`, `Title`, `Siren`, `Zone_Ring`, `Compass`);
the text is in `npc/wanted_messages.yml`.

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

### Cost of Losing a Star

Money is charged only when `Wanted.Take_Money.Enable` is true, which is **off by default** since 0.15.0. When on,
each star that drops costs the result of `Take_Money.Formula` (default `amount * multiplier ^ wanted`, with `wanted`
the stars before the drop). With `Amount: 50` and `Multiplier: 5`:

| Stars   | Money taken per drop |
|---------|----------------------|
| 1 ★     | 50 × 5¹ = 250        |
| 2 ★★    | 50 × 5² = 1,250      |
| 3 ★★★   | 50 × 5³ = 6,250      |
| 4 ★★★★  | 50 × 5⁴ = 31,250     |
| 5 ★★★★★ | 50 × 5⁵ = 156,250    |

The variables are `amount`, `multiplier`, `wanted`, `balance` (wallet), `level`, `experience` and `bounty`. A broken
formula warns once and uses the default; the wallet never goes below zero and the star always drops. The charge
applies to every cause of a drop (timer or evasion).

### The Charge Sheet

When a cop jails you, `Wanted.Charge_Sheet` in `npc/wanted.yml` lists the crimes of the chase and fines you
`Base + Per_Wanted_Level x stars` (200 + 250 per star, at most 10,000) from your **wallet only**. What the wallet cannot
cover is served as extra jail time (`Seconds_Per_Unpaid` 0.1 s per unit, at most `Max_Extra_Seconds` 600). A player
who is already dead at intake is not fined again. The paperwork screen shows the fine paid and the extra time.

---

## Bounty System

### How Bounties Work

A bounty is a monetary reward placed on a player or gang. Any player who kills the bounty target collects the reward.

- Bounties can be placed by players (spending their own money) or set by admins.
- **You collect what players posted.** Since 0.15.0 a kill pays the money players actually put up. The part the server
  adds (the per-kill bonus and the repeating multiplier, called notoriety) stays on the target and is paid only when
  `Bounty.Pay_Notoriety` is true. A bounty saved before the upgrade counts as fully posted and is paid in full once.
- A killer in the target's gang or an allied gang collects nothing and the bounty stays. A kill in self-defence is
  not a crime and does not add to the killer's bounty.
- Notoriety is not cleared by death or arrest.
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
| `/glw bounty clear <player>`          | Remove the bounty you posted on a player. |

---

## Configuration

In `settings.yml`:

```yaml
Wanted:
   Enable: true

   Take_Money:
      Enable: false           # Off by default since 0.15.0; true charges every star drop
      Formula: "amount * multiplier ^ wanted"
      Amount: 50              # Numbers for the formula only; they do not switch the charge
      Multiplier: 5

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
   Pay_Notoriety: false      # true also pays the server-made part of a bounty on a kill
   Kill:
      Each: 5                 # Money added to the player's bounty per kill they commit
      Maximum: 50_000         # Hard cap on a player's total kill-accrued bounty
   Repeating_Timer:
      Enable: true
      Multiple: 2             # Multiplier applied to the bounty amount each interval
      Time: 300               # Seconds between multiplier applications
      Maximum: 20_000         # Cap on bonus bounty from the multiplier

# The chase (Heat, Evasion, Hud, Charge_Sheet) is in npc/wanted.yml; see the migration guide and Configuration Reference.
# Cop count scaling is under the Cops key — see the Cops N Crooks guide
```

---

## API

```java
// Access the wanted executor
WantedExecutor wanted = gangland.getContext().get(WantedExecutor.class);

// Get a player's current wanted level
int stars = wanted.getLevel(user);

// Add or remove stars
wanted.

addLevel(user, 1);
wanted.

removeLevel(user, 1);

// Bounty
BountyExecutor bounty = gangland.getContext().get(BountyExecutor.class);

long currentBounty = bounty.getBounty(user);
bounty.

setBounty(user, 5000L);
bounty.

clearBounty(user);
```

---

[← Weapons](./weapons.md) | [Back to Index](../README.md) | [Next: Trade Signs →](./trade-signs.md)
