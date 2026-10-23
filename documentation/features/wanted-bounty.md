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

Since 0.15.0 stars come from **heat** when `Wanted.Heat.Enable` is true in `copsncrooks/wanted.yml` (the default). Each crime
adds heat, and the heat crosses `Star_Thresholds` (100, 250, 450, 700, 1000) to become stars.

| Crime | Heat | | Crime | Heat |
|---|---|---|---|---|
| Kill a player | 80 | | Kill a civilian | 100 |
| Hit a cop | 100 | | Fight off an arrest | 100 |
| Kill a cop | 150 | | Other crimes in the file | read by later releases |

- A crime chained within `Kill_Combo.Reset_After` seconds of the last one is multiplied by `Streak_Bonus` (1.5), only
  while `Kill_Combo.Enable` is true.
- A crime a cop saw is multiplied by `Seen_By_Cop_Multiplier` (1.5).
- A player kill inside a contested turf is multiplied by `Turf_War_Multiplier` (0.5).
- Hitting the same cop again within `Assault_Repeat_Seconds` (10) is not a new crime.
- **Not crimes:** a kill in self-defence (see Self-defence below), a crime-free takedown of a player with a bounty
  players posted (see Takedowns below), and a turf defender killing a raider inside their own gang's contested turf.
- With `Heat.Enable: false` the 0.13.0 maths apply: the kill combo (2 kills = 1 star, 5 = 2, 10 = 3, 15 = 4,
  20 = 5) when `Wanted.Kill_Combo.Enable` is on, otherwise one star per counted kill. A civilian kill counts once either way.
- Admin commands and `[WANTED]` signs can set, add or remove stars at any time. Every star change carries its cause
  (crime, sign, admin, restore, decay, evasion, bribe, arrest, death, contact), which the HUD and plugins can read.
- **Rampage (0.16.0).** In `Drop_Mode: AUTO` only heavy crimes count toward the rampage opening: a crime must be worth at
  least `Auto.Rampage_Min_Weight` (80) heat. The cheap crimes (brandishing near a cop, hitting a civilian, car theft)
  never make a rampage by themselves.

### New crimes (0.20.0)

| Crime | Heat | When it is committed |
|---|---|---|
| `Car_Theft` | 60 | Taking a gadget car you may not use. Witness-gated (below). |
| `Assault_Civilian` | 30 | Hitting a civilian. Published from 0.20.0; witness-gated. |
| `Safe_Cracking` | 150 | Cracking a `police_armory` or `military_vault` loot chest, successful or not. Once per cracking session. |
| `Store_Robbery` | 200 | A store hold-up trips the silent alarm (see [traders.md](./traders.md#store-hold-ups-0200)). |
| `Trespass_Restricted` | 300 | Walking into an admin region tagged `restricted`. Repeats no faster than `Trespass.Repeat_Seconds` (60). Players with `gangland.cop.trespass.bypass` are exempt. |
| `Evading_Police` | 100 | A driver who flees a traffic stop (`Patrol.Fled`). |

Warnings (`Brandish_Near_Cop`, 25) and the rest of the 0.15 list are unchanged. Crimes outside the witness list count at once,
exactly as before.

### Witnesses and 911 calls (0.20.0)

A car theft or an assault on a civilian that no cop saw is not counted at once. It is counted only if someone calls it in.

- A civilian within `Witness.Range` (24 blocks), with line of sight, stops, glows and calls 911. The criminal sees
  "A witness is calling 911!".
- If the call lasts `Witness.Call_Seconds` (8) it lands: the crime counts (heat, stars), the squad gets a sighting at the crime
  spot, and the radio plays the 911 caller line.
- Stop the caller in time and the call is cancelled. Hit the caller (they flee), aim a weapon at them within `Scare_Range`
  (16 blocks, `Scare_Angle` 12 degrees: they flee), or kill them (`Kill_Civilian` counts as shipped). A killed caller hands the
  call to the next civilian still watching; a scared one does not.
- Nobody saw it and no cop saw it: the gated crime adds no heat.
- Hitting a civilian publishes `Assault_Civilian` at most once per attacker and civilian every `Assault_Repeat_Seconds` (10).

Keys: `wanted.yml` `Wanted.Witnesses.Enable` and `Crimes`; `civilians.yml` `Witness.*`. See [migration-0.20.0.md](../migration-0.20.0.md).

### The hot-cash bag (0.20.0)

Cash you pick up while wanted goes into your bag, not your wallet. The bag pays out once, when the chase ends.

| Ending | Trigger | What happens to the bag |
|---|---|---|
| Get away | Evasion clock or the safety-net timer takes your last star (`EVASION`, `DECAY`) | Banks into your wallet, up to `Wanted.Bag.Max_Banked_Per_Day` per server day. Anything above that converts at `Fence_Cut`. |
| Other clear | Sign, contact, handcuff bribe, admin, unknown | Converts at `Fence_Cut` (default 0.35, so you get 65 percent). |
| Busted | Jail intake | Seized. The charge sheet prints `Bag seized: $X`. |
| Wasted or quit | Death (including going down) or quitting while the server runs | Spills as money items where you stood. Seized instead if you are handcuffed or in transit. |
| Server stop, reload or disable | Shutdown | Saved and restored on rejoin. Nothing spills. |

- The bag pays exactly once: every ending, the fence and a bribe zero the bag before any money moves.
- A spilled bill is hot: a player who picks it up and is not wanted gains one star, and the bill goes into his bag.
- The bag caps per chase at `Wanted.Bag.Max_Per_Chase` (5000). A full bag refuses the pickup, and a store take that does not fit
  is not paid.
- **Fence the bag.** `/glw contact fence` or the phone's "Fence the bag" slot converts the bag for wallet cash minus `Fence_Cut`.
  It is refused while you are in cuffs, in transit or jailed, and it is gated like the crooked contact (no cop sees you, and the
  contacts cooldown is free). Stars are not touched.
- **Bribes.** Handcuff and jail bribes take from the bag first and charge the wallet only for the rest.

Keys and messages are listed in [migration-0.20.0.md](../migration-0.20.0.md).

### How Stars Are Lost

- **Evasion.** While cops hunt you, break line of sight: after `Evasion.Lost_Sight_Seconds` (3) with no cop seeing
  you a search zone opens (radius `Search_Radius`, by level). Stay hidden for `Seconds_To_Drop` (10 s at 1 star up to
  60 s at 5) and one star drops (or all stars with `Drop_Mode: ALL_STARS`). Outside the zone the clock runs
  `Outside_Zone_Speed` (2x) faster. A sighting, or a shot you fire near a cop, puts you back to "seen"; being cuffed
  pauses it. Cops that are all walking home count as no pursuit.
  - **AUTO mode (0.15.2+).** With `Drop_Mode: AUTO`, the cops judge how the chase went. A rampage loses one star at a time; a small chase loses them all at once. A long quiet chase with no new crime for 90+ seconds also loses them all. Leaving the search zone loses half your stars (rounded up). The next timer gets faster after each drop, and known repeat offenders stay locked to one star. The server learns how long chases typically last and who keeps getting away, so timers adjust over time. Cold starts use guessed numbers; warm servers tune based on play. An upgraded server from 0.15.1 keeps its old `wanted.yml` (no `Auto` block) and reads AUTO defaults, or an admin can copy the new `Auto` block from `plugins/Gangland_Warfare/modules/cops-n-crooks-<version>.jar`.
- **The fixed decay timer** still runs when no cop is hunting you, and as the safety net otherwise: it starts at `Time`
  (default 120 seconds) and scales up with each star: `time x Amount ^ stars`. While cops hunt, evasion owns decay and the
  timer drops nothing. `Evasion.Enable: false` gives the timer back full control.
- **Dying clears all wanted stars** immediately.
- Kill combos reset after `Reset_After` seconds of no kills (default 10 seconds).

### Self-defence

Hitting back is not a crime. Since 0.16.0 the rules are keys of `Wanted.Self_Defence` in `settings.yml`:

- The victim must have been hit **first** by the other player, for at least `Min_Damage` (2.0). A weaker hit does not open the
  window.
- The window lasts `Window_Seconds` (8) after that first hit; a kill after it is an ordinary crime.
- Not for provocation: a player who hit the other one in the minute before the other's first strike cannot claim it.
- Not between gang mates or allies.
- Once an exemption is granted, the same two players get a new one only after `Pair_Cooldown_Seconds` (600).
- `Enable: false` removes the exemption: every kill is a crime.

### Takedowns (bounty kills)

Killing a player with a bounty that other players posted is crime-free only when the players' escrow on him is at least
`Bounty.Takedown_Minimum` (100) and above `Bounty.Minimum`. A cheaper bounty still pays out but the kill is an ordinary
crime. The same pair cannot farm it (the `Pair_Cooldown_Seconds` above applies) and one killer gets at most three
crime-free takedowns per rolling hour.

### Crooked contacts (0.16.0)

A crooked desk sergeant makes stars disappear for money, but **only while no cop has eyes on you**:

- **`/glw contact [stars]`** (or the phone's Contacts page): wipes `stars` stars (1 up to `Max_Stars`, at most your level) for
  `Price_Per_Star` (1000) each, cause `CONTACT`. Afterwards your contact lies low for `Cooldown_Seconds` (600).
- **`[WANTED]` signs** that remove or clear stars follow the same rules: refused while a cop sees you, and a paid wipe
  starts the same cooldown. Increase signs and free signs are unchanged. Refusals cost nothing.
- A player with no evasion state at all counts as unseen; only "a cop has eyes on you" blocks it.
- `Contacts.Enable: false` switches the desk off: `/glw contact` answers "Nobody picks up." and the sign behaves as in 0.15.
- Placeholders: `%gangland_contact_price%` (per star) and `%gangland_contact_cooldown%` (`ready` or the time left).
- Cooldowns are kept in memory; a restart clears them.

Bribe star pickups in the world (placed by admins) work the same way and are described in the
[Cops N Crooks guide](./cops-n-crooks.md).

### What You See

A boss bar with the stars (red when a cop sees you, yellow with a countdown while searching, green for three seconds
when a star is lost), a star card (the crime, the tier coming and whether it cuffs or shoots; or why a star dropped),
a title and, when a star is gained, a siren, a particle ring marking the search zone, and a compass pointing the way
out of the zone (it never shows where the cops are). Switch each off under `Wanted.Hud` (`Boss_Bar`, `Star_Card`, `Title`, `Siren`, `Zone_Ring`, `Compass`);
the text is in `copsncrooks/wanted_messages.yml`.

The title is set per event under `Wanted.Hud.Title` (`Gain`, `Lost`, `Escaped`), each with its own `Title`, `Subtitle`
and `Fade_In` / `Stay` / `Fade_Out` in ticks. The shipped default is a small subtitle (the star row and the star card) with no big
title and a one-second stay, so the screen stays readable mid-chase. To get the old full-screen star count back, set
`Title` to `"&c%stars%"`, `Subtitle` to `"%card%"`, `Stay` to `40` and `Fade_Out` to `10` on each event. Blank title and subtitle send nothing;
write `Title: ""` to blank a line, since a bare key with no value takes the default.

### After An Escape: The Search And The Bounty (0.16.1)

Losing your last star by staying out of sight is an escape, but the cops do not stand down. For
`Wanted.Post_Escape.Search_Seconds` (120) they keep searching for you, and a bounty goes on you:

- **The bounty.** The escape adds the auto bounty of your level and the peak star count of the chase (the highest level you
  reached, not only the star you lost last), as notoriety, once per escape. Nothing is added when the increase would take
  your notoriety past `Bounty.Kill.Maximum` (the kill path's rule). A chat line (`Wanted.Post_Escape.Announce`) says the bounty is on you, and the bar turns into the bounty bar: `BOUNTY $amount · Cops still
  looking`, in the colour of `Wanted.Hud.Bounty.Bar_Color` (`YELLOW`), counting down with the search. With no bounty
  standing the bar reads only `Cops still looking`. The amount is read live, so a bounty posted on you during the
  search shows at once.
- **The search.** Cops keep hunting you as they would a wanted player, but they never cuff or shoot at you during it.
  The squad keeps looking: the cops walking home come back to the search, and the spawns carry on at the lowest tier
  (one star's squad) until the search ends. Hitting a cop is still a crime: it makes you wanted again and the normal
  chase resumes.
- **Contact.** A squad that sights you during the search raises you by `Wanted.Post_Escape.Spotted_Stars` (default 1). That
  is a new wanted start: the search ends and the normal chase resumes, with its HUD. `Spotted_Stars: 0` keeps the search
  harmless: the cops only trail you and a sighting changes nothing.
- **The end.** The search ends when it runs out, when you are wanted again (the normal wanted HUD takes the bar back),
  or when you die or are arrested. Death and arrest end the cops' hunt at once: the squad stands down and is removed,
  and the bounty bar goes. Quitting removes the squad at once, with no stand-down line. When the search runs out on its
  own the cops give up, and the chat says so, with the bounty still standing if it is. An admin wanted clear does not
  end a search that is already running; it runs to its timer (or a reload).
- **The bounty is not a second money system.** It is the server-made notoriety of the bounty system: a player who kills
  you can collect it when `Pay_Notoriety` is on, like any other notoriety. With `Pay_Notoriety` on, two players can farm
  it (one escapes, the other kills him and collects); it is off by default, so leave it off unless you police that.

`Wanted.Post_Escape.Enable: false` makes an escape end the chase at once, as in 0.16.0: the cops stand down and walk home,
with no search and no bounty. `Wanted.Hud.Bounty.Enable: false` hides the bounty bar and keeps the chat line and the countdown.
`Wanted.Post_Escape.Announce: false` drops the chat lines. The search is not carried through a server restart, and a rejoin
finds the cops gone; the bounty itself is saved with the player and comes back with him. A reload of the cops (a bean
reload) ends every search at once, and the bounty bar goes with it on its next beat.

### Police Response Per Star

| Stars   | Cops Sent | Minimum Tier    |
|---------|-----------|-----------------|
| 1 ★     | 2         | Officer         |
| 2 ★★    | 3         | Officer         |
| 3 ★★★   | 4         | Sergeant        |
| 4 ★★★★  | 5         | Lieutenant      |
| 5 ★★★★★ | 8         | SWAT / Military |

> The exact cop count follows the formula: `base + (stars - 1) × per-level`, capped at `max`. These values are
> configurable in `copsncrooks/cops.yml` under `Cops.Count`.

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

When a cop jails you, `Wanted.Charge_Sheet` in `copsncrooks/wanted.yml` lists the crimes of the chase and fines you
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
| `/glw contact [stars]`                | Pay a contact to wipe stars while no cop sees you (0.16.0). |
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

   # Kill_Combo moved to copsncrooks/wanted.yml (same Wanted.Kill_Combo path, Enable / Reset_After / Kill_Counter) in 0.15.1.

   # 0.16.0: hitting back is not a crime
   Self_Defence:
      Enable: true
      Window_Seconds: 8          # how long after being hit the victim may hit back crime-free
      Min_Damage: 2.0            # weaker hits do not open the window
      Pair_Cooldown_Seconds: 600 # the same pair gets a fresh window only after this long
   # 0.16.0: crooked contacts (/glw contact and the [WANTED] sign), only while no cop sees you
   Contacts:
      Enable: true
      Price_Per_Star: 1000
      Cooldown_Seconds: 600
      Max_Stars: 2

Bounty:
   Pay_Notoriety: false      # true also pays the server-made part of a bounty on a kill
   Takedown_Minimum: 100     # 0.16.0: escrow a bounty needs for a kill to be a crime-free takedown
   Kill:
      Each: 5                 # Money added to the player's bounty per kill they commit
      Maximum: 50_000         # Hard cap on a player's total kill-accrued bounty
   Repeating_Timer:
      Enable: true
      Multiple: 2             # Multiplier applied to the bounty amount each interval
      Time: 300               # Seconds between multiplier applications
      Maximum: 20_000         # Cap on bonus bounty from the multiplier

# The chase (Heat, Evasion, Hud, Charge_Sheet, Bribe_Stars) is in copsncrooks/wanted.yml; see the migration guide and Configuration Reference.
# Cop count scaling is Cops.Count in copsncrooks/cops.yml — see the Cops N Crooks guide
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
