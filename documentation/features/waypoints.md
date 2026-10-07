# Waypoints

[← Economy](./economy.md) | [Back to Index](../README.md) | [Next: Loot Chests →](./loot_chests.md)

---

## Overview

Waypoints are named teleportation destinations placed in the world by admins. Players pay a configurable fee and wait
through a short timer before being transported. Waypoints can be public, gang-restricted, or tied to specific
permissions. Some waypoint types also act as safe zones where PvP is disabled.

---

## Waypoint Types

| Type        | Safe Zone | Description                                                 |
|-------------|-----------|-------------------------------------------------------------|
| `SPAWN`     | Yes       | A server spawn point. PvP is disabled in this area.         |
| `GANG`      | Yes       | Restricted to members of a specific gang. Also a safe zone. |
| `SAFE_ZONE` | Yes       | General safe area — no PvP, publicly accessible.            |
| `QUEST`     | No        | Quest-related destination. PvP is not disabled.             |
| `GLOBAL`    | No        | Publicly accessible, no PvP protection.                     |
| `HOSPITAL`  | Yes       | Where players wake up after dying (0.16.0). See Hospitals.  |

---

## Creating and Managing Waypoints

Waypoints are created first, then configured. Select a waypoint before editing its properties.

### Lifecycle Commands

| Command                       | Description                                                         |
|-------------------------------|---------------------------------------------------------------------|
| `/glw waypoint create <name>` | Creates a waypoint at your current location. Requires confirmation. |
| `/glw waypoint delete <id>`   | Permanently deletes a waypoint.                                     |
| `/glw waypoint list`          | Lists all waypoints with their IDs and types.                       |
| `/glw waypoint info`          | Shows full details of the currently selected waypoint.              |

### Selection

You must select a waypoint before you can configure it.

| Command                     | Description                     |
|-----------------------------|---------------------------------|
| `/glw waypoint select <id>` | Selects a waypoint for editing. |
| `/glw waypoint deselect`    | Deselects the current waypoint. |

### Configuration Commands

All configuration commands apply to the currently selected waypoint.

| Command                            | Description                                                               |
|------------------------------------|---------------------------------------------------------------------------|
| `/glw waypoint type <type>`        | Sets the waypoint type (`SPAWN`, `GANG`, `SAFE_ZONE`, `QUEST`, `GLOBAL`, `HOSPITAL`). |
| `/glw waypoint cost <amount>`      | Money players must pay to teleport here. Use `0` for free.                |
| `/glw waypoint timer <seconds>`    | How long players must stand still before teleporting.                     |
| `/glw waypoint cooldown <seconds>` | How long before the same player can use this waypoint again.              |
| `/glw waypoint radius <blocks>`    | Sets the area of effect radius around the waypoint.                       |
| `/glw waypoint shield <value>`     | Sets the shield protection value for this waypoint's safe zone.           |
| `/glw waypoint gangId <gang_id>`   | Restricts the waypoint to a specific gang. Only applies to `GANG` type.   |

### Using a Waypoint

| Command                       | Description                                                                |
|-------------------------------|----------------------------------------------------------------------------|
| `/glw teleport <waypoint_id>` | Teleports to the specified waypoint (after paying cost and waiting timer). |

---

## How Teleportation Works

1. Player runs `/glw teleport <id>`.
2. The system checks the player has enough money and is not on cooldown.
3. The fee is deducted.
4. A countdown starts (the `timer` value in seconds). If the player moves or takes damage during this window, the
   teleport is cancelled and the fee is refunded.
5. On completion, the player is teleported to the waypoint's coordinates.
6. The cooldown begins — the player cannot use this waypoint again until it expires.

---

## Permissions

Each waypoint automatically generates a permission node:

```
gangland.waypoint.<name>
```

Players without this permission node cannot see or use the waypoint. Grant it via your permissions plugin to control
access.

---

## Gang-Restricted Waypoints

Set a waypoint's type to `GANG` and assign a gang ID with `/glw waypoint gangId <id>`. Only players who are members of
that gang can teleport to it. These waypoints are also safe zones.

Since 0.16.0 a gang waypoint is also a **hideout**: while the police search for a member inside its radius (8 blocks when
the waypoint's radius is 0, at most 64) the "nobody has seen you" clock runs faster. See Cops N Crooks.

---

## Hospitals (0.16.0)

Create a waypoint where players should wake up, select it with `/glw waypoint select <id>` and set it with `/glw waypoint type hospital`. Several hospitals
can exist; the **nearest one in the world where the player died** is used. The setting is `User.Death.Hospital.Enable`
(default true, in `settings.yml`).

- **The respawn rule.** A player who dies and respawns the vanilla way wakes at the nearest hospital, unless he respawns
  at his bed or respawn anchor. A player who is *downed* and then respawns (with `User.Death.Respawn.Enable: true`) is
  teleported straight to the nearest hospital, with no timer, cooldown or cost. With no hospital in that world the old
  behaviour applies (the configured respawn waypoint, or the vanilla spawn). A jail can still override the respawn of a
  jailed or handcuffed player.
- **One bill.** The death penalty (`Lose_Money`) is now one "Ward bill": taken from the wallet at the down (so emptying
  the wallet while downed dodges nothing) and reported when the downed player gets up (respawn); a player who is
  downed and then really dies pays once. With `Hospital.Enable: false` the old message stays (also charged at the down).
- **The respawn shield.** After a hospital respawn the player takes no damage for `User.Death.Hospital.Shield_Seconds`
  (5) seconds, so a hospital cannot be camped the moment he wakes. Incoming damage is cancelled (the void still kills);
  the shield ends early when he attacks anything, quits, or the time runs out. A bed, anchor or fallback respawn gets no
  shield. `Shield_Seconds: 0` switches it off. A server with an older `settings.yml` has no `Shield_Seconds` key and
  gets the default 5; add `Shield_Seconds: 0` to turn it off.

---

[← Economy](./economy.md) | [Back to Index](../README.md) | [Next: Loot Chests →](./loot_chests.md)
