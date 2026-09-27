# Wanted & Bounty

[← Weapons](./weapons.md) | [Back to Index](../README.md) | [Next: Trade Signs →](./trade-signs.md)

---

## Overview

The wanted level system tracks how much heat a player is carrying. Stars accumulate through a per-crime heat ledger
(or, with heat disabled, the legacy kill-combo counter) and can be adjusted by admins. Each star tier brings a
stronger and larger police response, made up of a mixed-tier squad. Since 0.12, escaping is a skill: break line of
sight from the squad chasing you long enough and stars drop through evasion instead of only through time. The bounty
system runs alongside it, letting players and gangs put a price on each other's heads.

---

## Wanted Level

### How Stars Are Earned: the Heat Ledger (0.12)

By default (`Heat.Enable: true` in `cops.yml`), every crime adds **heat** to the offender's running total instead of
one flat point per kill, and stars follow heat thresholds instead of a fixed kill count:

| Crime                          | Heat    |
|---------------------------------|---------|
| Kill a player (`Kill_Player`)   | 80      |
| Kill a civilian (`Kill_Civilian`) | 100   |
| Kill a cop (`Kill_Cop`)         | 150     |
| Assault a cop (`Assault_Cop`)   | 100     |

- **Assault a cop** is any non-lethal damage a player deals to a cop NPC (melee, projectiles, or a Bartizan weapon
  shot) — a lethal hit scores `Kill_Cop` instead. It is rate-limited to once per (player, cop) pair per
  `Assault_Cop_Cooldown_Seconds` (default 10), so standing in one fight cannot stack heat every hit.
- **Killing a hostile civilian that is fighting back** (self-defence) never adds heat, on the heat ledger or the
  legacy path.
- **Streak bonus** (`Streak_Bonus`, default `1.5`) — from the second kill onward while your kill combo is still
  running (i.e. within `Wanted.Kill_Combo.Reset_After` seconds of the previous kill), every crime's heat is
  multiplied by this. The first kill of a combo doesn't get the bonus; it starts the combo the next kill benefits
  from.
- **Turf war multiplier** (`Turf_War_Multiplier`, default `0.5`) — a kill whose victim stands inside a turf currently
  `CONTESTING` is *cheaper*, not more expensive: its heat is multiplied by this fraction. No-ops (always 1×) on a
  server without the turf module.
- **Star thresholds** (`Star_Thresholds`, default `[100, 250, 450, 700, 1000]`) — the heat needed for star 1, 2, 3, 4,
  5. A shorter list than `Wanted.Level.Maximum` is stretched out linearly. Heat never falls below the threshold of
  your current star — losing a star (evasion, decay, admin) caps heat down to that floor instead of zeroing it, so a
  fresh crime picks up from where the star started rather than from zero.
- Every heat-driven star gain goes through the same path a flat kill does (`EntityDamageListener.handleWanted`), so
  timers, the wanted event, and the bounty bump all still fire.

With `Heat.Enable: false`, kills raise wanted the pre-0.12 way — one point per kill (or, with `Wanted.Kill_Combo`
enabled, combo thresholds):

- 2 kills → 1 star
- 5 kills → 2 stars
- 10 kills → 3 stars
- 15 kills → 4 stars
- 20 kills → 5 stars

Admin commands can set, add, or remove stars at any time, on either system.

### How Stars Are Lost: Evasion and Decay

Since 0.12, **line-of-sight evasion** (`Wanted.Evasion.Enable: true`, on by default) is the primary way stars drop
during an active chase — dying still clears all wanted stars immediately, and kill combos still reset after
`Reset_After` seconds of no kills (default 10).

Evasion works off the cop squad's shared sighting of the player, once at least one cop has actually been sent
(`CopGroup.isStaffed()`):

- **SEEN** — the squad has seen the player within the last `Lost_Sight_Seconds` (default 3). Stars are solid; the
  evasion clock is paused and reset.
- **SEARCHING** — nobody in the squad has seen the player for `Lost_Sight_Seconds`. A search zone opens as a circle on
  the squad's last known position, radius `Search_Radius[star - 1]` (default `[40, 60, 90, 130, 180]` blocks). A clock
  starts counting toward `Seconds_To_Drop[star - 1]` (default `[10, 20, 30, 45, 60]`), at normal speed while the
  player stays inside the zone and at `Outside_Zone_Speed` (default `2.0`×) while outside it.
- **Clock runs out** — one star drops (`Drop_Mode: ONE_STAR`, the default — the search then restarts at the new,
  lower level) or every star drops at once (`Drop_Mode: ALL_STARS`, ending the chase).

For a player evasion doesn't handle — evasion disabled, or no cop was ever sent after them (e.g. no valid spawn
location) — the old **repeating decay timer** (`Wanted.Repeating_Timer`) is the fallback, unchanged from pre-0.12: it
starts at `Time` (default 120 seconds) and scales up with each star, `time × Amount ^ stars` — a 5-star player waits
significantly longer between each star reduction than a 1-star player. While evasion *is* handling the chase, this
timer's own decrease is cancelled for that player, not merely ignored, so the two systems never both act on the same
step.

### Police Response Per Star

Since 0.12 F4, the police response is a **mixed-tier squad** read from `Cops.Rosters` in `cops.yml`, not a single tier
sized by a formula. The shipped defaults:

| Stars   | Roster                    | Tier Names Involved          |
|---------|---------------------------|-------------------------------|
| 1 ★     | 2× Tier 1                 | Officer                        |
| 2 ★★    | 2× Tier 1, 2× Tier 2      | Officer, Sergeant              |
| 3 ★★★   | 2× Tier 2, 2× Tier 3      | Sergeant, Lieutenant           |
| 4 ★★★★  | 2× Tier 3, 4× Tier 4      | Lieutenant, SWAT                |
| 5 ★★★★★ | 3× Tier 4, 4× Tier 5      | SWAT, Military                  |

> Each star's total is still clamped by `Cops.Behaviour.Max_Per_Player`. A star with no `Cops.Rosters` entry (or a
> server that removes the section entirely) falls back to the pre-0.12 behavior: one tier — `min(stars, Max_Tier)`,
> so star *N* means tier *N* (Officer, Sergeant, Lieutenant, SWAT, Military for stars 1–5) — sized by
> `Cops.Count`'s `base + (stars - 1) × per-level` formula (or `Formula` when `Formula_Enabled`), capped at `max`. See
> the [Cops N Crooks guide](./cops-n-crooks.md#rosters--backup-waves-012) for rosters and backup waves.

### Cost of Being Wanted

In 0.12+, costs for being wanted come from **death penalties**, not per-star drains during a chase. When a player dies
while wanted, the death formula may include the `wanted` level (and `bounty`) as variables, allowing custom per-star
fines:

```
loss on death = balance * 0.15 + wanted * 500   (example; adjust to taste)
```

The available formula variables are `balance`, `level`, `experience`, `bounty` and `wanted` — all may be zero.

The shipped `Wanted.Take_Money.Amount` (per-star drain, charged by the repeating decay timer) defaults to `0` in
`settings.yml` as of 0.12 — servers that already have this key keep their own value; only the shipped default
changed. Money is only ever taken **once per decay step** (i.e. once per repeating-timer firing, whose own interval
scales with stars as above) — never per game tick or per HUD tick, both of which run far more often. When evasion
handles a chase, the repeating timer's step (and so this drain) is cancelled outright for that player, leaving their
balance untouched by wanted alone unless they die or get arrested. Servers can still enable the legacy drain by
setting `Take_Money.Amount` to a non-zero value.

Arrest fines are separate: bail, bribe, and sentence costs scale by wanted level and are configurable under
`Detainment:` in `settings.yml`.

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
      Amount: 0               # DEPRECATED (0.12): Legacy per-star drain, taken once per decay step (not per tick).
                               # Set to 0 (disabled, the 0.12 shipped default). Existing servers keep their value.
      Multiplier: 5           # Exponent base in the formula: Amount * Multiplier ^ stars

   Repeating_Timer:
      Enable: true
      Time: 120               # Base decay interval in seconds (at 1 star) — fallback when evasion disengages
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
      Kill_Counter: # Kill thresholds that trigger each star level (only used with Heat.Enable: false)
         - 2                   # 2 kills → 1 star
         - 5                   # 5 kills → 2 stars
         - 10                  # 10 kills → 3 stars
         - 15                  # 15 kills → 4 stars
         - 20                  # 20 kills → 5 stars

   # Line-of-sight evasion (0.12): the primary way stars drop during an active chase. See "How Stars Are Lost" above.
   Evasion:
      Enable: true             # false = old repeating-decay-only behavior
      Lost_Sight_Seconds: 3    # Seconds without a squad sighting before the search zone opens
      Drop_Mode: ONE_STAR      # ONE_STAR (search restarts at the new level) or ALL_STARS (chase ends outright)
      Search_Radius: [40, 60, 90, 130, 180]     # Search zone radius per star, in blocks
      Seconds_To_Drop: [10, 20, 30, 45, 60]     # Unseen seconds needed to drop a star, per star
      Outside_Zone_Speed: 2.0  # Clock speed multiplier while outside the zone (1.0 = normal, same as inside)
      Hideout_Speed: 1.5       # Reserved for 0.14 hideouts — parsed but not used yet

Bounty:
   Kill:
      Each: 5                 # Money added to the player's bounty per kill they commit
      Maximum: 50_000         # Hard cap on a player's total kill-accrued bounty
   Repeating_Timer:
      Enable: true
      Multiple: 2             # Multiplier applied to the bounty amount each interval
      Time: 300               # Seconds between multiplier applications
      Maximum: 20_000         # Cap on bonus bounty from the multiplier

# Cop count scaling and rosters are under the Cops key — see the Cops N Crooks guide
```

The heat ledger lives in `cops.yml` (module config), not `settings.yml`, since it's owned by the cops-n-crooks module:

```yaml
Heat:
   Enable: true
   Star_Thresholds: [100, 250, 450, 700, 1000]  # Heat needed for each star; stretched if shorter than Level.Maximum
   Streak_Bonus: 1.5              # Multiplier while the kill combo (Wanted.Kill_Combo.Reset_After) is still running
   Turf_War_Multiplier: 0.5       # Multiplier for a kill inside a CONTESTING turf (cheaper, not more expensive)
   Assault_Cop_Cooldown_Seconds: 10  # One Assault_Cop scored per (player, cop) pair per this many seconds
   Crimes:
      Kill_Player: 80
      Kill_Civilian: 100
      Kill_Cop: 150
      Assault_Cop: 100
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
