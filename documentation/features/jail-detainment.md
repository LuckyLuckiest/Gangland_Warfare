# Jail & Detainment

[← Cops N Crooks](./cops-n-crooks.md) | [Back to Index](../README.md) | [Next: Wearables →](./wearables.md)

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

## Release

Releasing a player removes all restraint effects, clears their detainment record, and returns them to the `NORMAL`
state. Release can happen:

- Via the admin release command.
- Automatically when the detainment timer expires (if configured).

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

Jail capacity is set in `settings.yml`:

```yaml
detainment:
   max-jail-capacity: 10     # Maximum number of players per jail at one time
```

Cuffing behavior (radius, attempts, cooldown) is configured per cop tier in `cops.yml`. See
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

---

[← Cops N Crooks](./cops-n-crooks.md) | [Back to Index](../README.md) | [Next: Wearables →](./wearables.md)
