# Waypoints

[← Economy](./economy.md) | [Back to Index](../README.md) | [Next: Loot Chests →](./loot-chests.md)

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
| `/glw waypoint type <type>`        | Sets the waypoint type (`SPAWN`, `GANG`, `SAFE_ZONE`, `QUEST`, `GLOBAL`). |
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

---

[← Economy](./economy.md) | [Back to Index](../README.md) | [Next: Loot Chests →](./loot-chests.md)
