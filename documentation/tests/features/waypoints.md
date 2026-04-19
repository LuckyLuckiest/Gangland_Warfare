# Waypoints — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/waypoints.md)

---

## Overview

Named teleport destinations with timers, costs, cooldowns, shield (invulnerability grace), radius, and optional gang
binding. Managed by `WaypointManager`.

**Modules involved:** `gangland-impl`.

---

## Pre-Conditions

- [ ] Op player online.
- [ ] A second, non-op player online for permission tests.

---

## Smoke Test

- [ ] Stand at location `L1`. `/glw waypoint create Plaza` → waypoint stored.
- [ ] `/glw waypoint list` → `Plaza` appears.
- [ ] Move elsewhere. `/glw tp Plaza` → teleports back to `L1`.
- [ ] `/glw waypoint select <id>` → selected for editing.
- [ ] `/glw waypoint info` → shows type, timer, cost, cooldown, shield, radius.
- [ ] `/glw waypoint cost 50` → cost applied; verify on `info`.
- [ ] `/glw waypoint cooldown 10` → cooldown applied.
- [ ] `/glw waypoint shield 5` → shield duration applied.
- [ ] `/glw waypoint timer 3` → channel timer applied.
- [ ] `/glw waypoint radius 2` → radius applied.
- [ ] `/glw waypoint deselect` → clears selection.
- [ ] `/glw waypoint delete <id>` → waypoint gone.

---

## Edge Cases

- [ ] `/glw tp NonExistent` → clear "waypoint not found" message.
- [ ] `/glw tp Plaza` with insufficient funds → denied.
- [ ] `/glw tp Plaza` during cooldown → denied with remaining seconds.
- [ ] Move during channel timer → teleport cancelled.
- [ ] Bind waypoint to gang: `/glw waypoint gangId <id>` → non-gang members rejected on `/tp`.
- [ ] Teleport into a world that doesn't exist (edit the YAML to point to a missing world, `/glw reload files`) →
  graceful error; server stays up. Per `feedback_get_world_null_check`, `Bukkit.getWorld()` must be null-checked.

---

## Reload Safety

- [ ] Create a waypoint, run `/glw reload` → waypoint still present, still teleportable.
- [ ] Edit cost via commands, run `/glw reload data` → updated cost persists.

---

## Persistence

- [ ] Waypoint survives restart on SQLite and MySQL.
- [ ] Gang-bound waypoint still bound after restart.

---

## Regression Risks

- `WaypointManager` — create/delete, teleport flow.
- Economy contract — cost deduction.
- World resolution — null-check on `Bukkit.getWorld()`.

---

[Back to Test Index](../README.md)
