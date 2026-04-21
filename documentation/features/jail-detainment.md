# Jail & Detainment

[← Bank & Banker](./bank.md) | [Back to Index](../README.md) | [Next: Wearables →](./wearables.md)

---

## Overview

The detainment system handles the full arrest pipeline — from a cop snapping handcuffs on a player to the player sitting
in a jail cell. It also supports manual admin arrests and releases at any time via commands.

A player moves through three possible states:

| State        | Description                                                  |
|--------------|--------------------------------------------------------------|
| `NORMAL`     | Free and unrestrained.                                       |
| `HANDCUFFED` | Restrained but not yet jailed. Movement is severely limited. |
| `JAILED`     | Teleported to a jail location and held there.                |

---

## Handcuffing

When a cop successfully cuffs a player — or an admin uses the cuff command — the following effects are applied
immediately and persist until release:

- **Slowness IV** — movement is almost completely restricted.
- **Blindness I** — partial vision impairment.
- **Weapons disabled** — the player cannot fire or use any Gangland weapon while restrained.
- An **action bar message** updates every tick to remind the player they are restrained.

These effects do not expire on their own. They are lifted only when the player is formally released.

> **Logout protection**: If a handcuffed player disconnects before being jailed, they are automatically moved to the
`JAILED` state when they reconnect. They cannot escape by logging out.

---

## Jailing

After a player is handcuffed, the system (or an admin) jails them:

1. The player is teleported to the configured jail location.
2. Restraint effects continue inside the jail.
3. The player's detainment record is saved to the database — server restarts do not free them.
4. On respawn (if the player dies while jailed), they are re-teleported to the jail and effects are reapplied.

Each jail has a **capacity cap** — once full, no additional players can be sent there until someone is released. You can
configure the capacity in `settings.yml`.

---

## Bail

As of 0.7.5-DEV, a jailed player can **pay their way out**. The paperwork view inside the jail exposes a
**Pay Bail** action that charges the player's cash balance and releases them with their seized inventory intact.

- **Cost** — `Bail.Base_Cost + (wanted_at_arrest × Bail.Per_Wanted_Level)`. A one-star arrest pays base; a
  five-star fugitive pays a lot more.
- **Charged from cash** — not from bank balance. If the player's cash is short, bail fails with
  `INSUFFICIENT_FUNDS` and no money is deducted.
- **Inventory restoration** — routes through the same `ReleasePipeline` used by sentence-served and admin-release
  paths, so seized items are returned intact.
- **Post-jail only** — you cannot bail out of handcuffs. See **Bribery** below for the pre-jail path.

---

## Bribery

Two kinds of bribes run off the same `BribeService` machinery but at different stages of the arrest flow.

### Handcuff Bribe (pre-jail)

While a player is handcuffed but not yet jailed, they can attempt to bribe the arresting cop. This path is **always
successful if funded** — no dice roll.

- **Cost** — `Handcuff_Bribe.Base_Cost + (wanted × Handcuff_Bribe.Per_Wanted_Level)`.
- **Effect** — releases the cuffs and clears the wanted level; the cop returns to patrol.

### Jail Bribe (post-jail)

A risky shortcut from inside the cell. Pay the cost and roll against `Success_Chance`:

- **Cost** — `Jail_Bribe.Base_Cost + (wanted × Jail_Bribe.Per_Wanted_Level)`.
- **On success** — same release path as bail: seized inventory restored.
- **On failure** — the money is gone **and** your sentence is extended by `Fail_Penalty_Seconds`.

---

## Break Free

Handcuffed (pre-jail) players can attempt to physically break free by tapping a configured key. Requires
`Break_Free.Taps_Required` inputs within `Break_Free.Reset_Window_Ticks` ticks — 25 taps in 40 ticks by default
(two seconds).

If they drop below the pace, the counter resets. This is an intentionally narrow window: break-free is meant as
a skill path, not a reliable escape.

---

## Sentence

If the player does nothing — no bail, no bribe, no break-free — they serve their time and are released
automatically.

- **Duration** — `Sentence.Base_Seconds + (wanted_at_arrest × Sentence.Per_Wanted_Level_Seconds)`. Default: 3
  minutes base + 1 minute per wanted star. A five-star fugitive serves 8 minutes.
- **Completion sound** — `Detainment.Sounds.Sentence_Complete` plays on release.
- **Released exit waypoint** — falls back to `Detainment.Fallback_Exit_Waypoint` if the jail has no per-jail
  exit configured.

---

## Release

Releasing a player removes all restraint effects, clears their detainment record, and returns them to the `NORMAL`
state. Release can happen via:

- **Bail** — player pays (see above).
- **Successful bribe** — handcuff bribe (pre-jail) or jail bribe (post-jail, with dice roll).
- **Break-free** — player wins the tap-race while handcuffed.
- **Sentence served** — automatic timeout.
- **Admin release** — `/glw jail release <player>` or `/glw uncuff <player>`.

All paths funnel through a single `ReleasePipeline`, so seized items are restored consistently regardless of how
the player got out.

---

## Commands

### Admin — Cuffing

| Command                | Description                       |
|------------------------|-----------------------------------|
| `/glw cuff <player>`   | Manually handcuff a player.       |
| `/glw uncuff <player>` | Remove handcuffs without jailing. |

### Admin — Jail Management

| Command                      | Description                                      |
|------------------------------|--------------------------------------------------|
| `/glw jail create`           | Creates a jail at your current location.         |
| `/glw jail remove <id>`      | Removes the jail with the given ID.              |
| `/glw jail throw <player>`   | Sends a player directly to jail (skips cuffing). |
| `/glw jail release <player>` | Releases a jailed or cuffed player.              |

---

## Configuration

The full detainment block lives in `settings.yml` under the top-level `Detainment:` key. Every subsection is
tunable independently.

```yaml
Detainment:
   Transit:
      Delay_Ticks: 400                 # Ticks between handcuff and jail teleport
      Guard_Radius: 5.0                # Blocks; cops stay within this radius during transit
   Break_Free:
      Taps_Required: 25
      Reset_Window_Ticks: 40
   Handcuff_Bribe:
      Base_Cost: 500.0
      Per_Wanted_Level: 250.0
   Bail:
      Base_Cost: 2500.0
      Per_Wanted_Level: 1000.0
   Jail_Bribe:
      Base_Cost: 1000.0
      Per_Wanted_Level: 500.0
      Success_Chance: 0.35             # 0.0 – 1.0
      Fail_Penalty_Seconds: 60         # Sentence extension on a failed bribe
   Sentence:
      Base_Seconds: 180
      Per_Wanted_Level_Seconds: 60
   Fallback_Exit_Waypoint: "spawn"     # Used when a jail has no specific exit waypoint
   Sounds:
      Bail_Success: "BLOCK_NOTE_BLOCK_PLING"
      Bribe_Success: "ENTITY_VILLAGER_YES"
      Bribe_Fail: "ENTITY_VILLAGER_NO"
      Transit_Commit: "BLOCK_IRON_DOOR_CLOSE"
      Sentence_Complete: "BLOCK_BELL_USE"
```

Cuffing behavior (cop-side radius, attempts, cooldown) is configured per cop tier in `cops.yml`. See
the [Cops N Crooks guide](./cops-n-crooks.md#configuration).

---

## API

The `DetainmentService` is the single point of contact for all arrest logic.

```java
DetainmentService detainment = gangland.getContext().get(DetainmentService.class);

// Check a player's state
DetainmentState state = detainment.getState(player);
// state is NORMAL, HANDCUFFED, or JAILED

boolean cuffed = detainment.isHandcuffed(player);
boolean inJail = detainment.isJailed(player);

// Apply handcuffs
detainment.

handcuff(player);

// Send to a specific jail
detainment.

jail(player, jailId);

// Release completely
detainment.

release(player);
```

The `JailService` manages jail locations:

```java
JailService jailService = gangland.getContext().get(JailService.class);

// Get all jails
List<Jail> jails = jailService.getAllJails();

// Get a specific jail
Optional<Jail> jail = jailService.getJail(jailId);

// Check if a jail has available capacity
boolean hasSpace = jailService.hasCapacity(jailId);
```

Bail and bribery each have their own typed-result service:

```java
BailService   bail   = gangland.getContext().get(BailService.class);
BribeService  bribe  = gangland.getContext().get(BribeService.class);

double cost = bail.computeCost(player);
BailResult result = bail.tryPayBail(player);
// SUCCESS | INSUFFICIENT_FUNDS | NOT_JAILED | ECONOMY_ERROR
```

Every release path — bail, bribe, break-free, sentence-served, admin — funnels through the single
`ReleasePipeline`.

---

[← Bank & Banker](./bank.md) | [Back to Index](../README.md) | [Next: Wearables →](./wearables.md)
