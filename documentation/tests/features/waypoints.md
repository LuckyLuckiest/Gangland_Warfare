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

## Hospitals and the Respawn Shield (0.16.0)

**S14 Downed, then respawn** (`User.Death.Respawn.Enable: true`, a HOSPITAL waypoint exists)
- [ ] `/glw waypoint create Ward`, `select`, `type hospital` (`/glw waypoint type` accepts `hospital`); `/glw waypoint info` shows type HOSPITAL.
- [ ] Die and respawn through the downed screen: you are teleported straight to Ward (no timer, cooldown or cost) and told
      "Hospital protection for 5 s. Attacking anything ends it."
- [ ] One bill: "Ward bill: -N" is taken once at the down and shown when you respawn, only above `Death.Money.Threshold`; depositing the whole wallet while downed does not dodge it.
      Quit while downed: the bill is charged at the quit. Downed, then killed: still one bill.
- [ ] With two hospitals in the world the nearest one to where you died is used; one in another world is ignored.

**S15 Vanilla death** (`Death.Respawn.Enable: false`)
- [ ] You wake at the nearest hospital (not at your bed or respawn anchor, which still win), one "Ward bill" at the death, with the
      shield, and no cash item at your body with the bundled `money.yml`.

**Respawn shield**
- [ ] After a hospital respawn, let a mob or player hit you for 5 s: no damage (the void still kills).
- [ ] Wake, wait out 5 s: damage works again.
- [ ] Wake and hit any mob or player (or fire at one): damage works at once.
- [ ] `User.Death.Hospital.Shield_Seconds: 0` (then `/glw reload`): no shield and no message. A settings.yml with no
      `Shield_Seconds` key behaves as 5.
- [ ] A bed, anchor or fallback-waypoint respawn gets no shield; `Hospital.Enable: false` restores the old respawn and the old
      timing (bill at the down, old "Death penalty" line).
- [ ] An unknown waypoint type in the database (edit a row) logs one warning and skips only that row; the others load.

**Gang hideout**
- [ ] A gang waypoint with a gang id counts as that gang's hideout (8 block radius when the waypoint radius is 0; see the Cops N
      Crooks checklist S7).

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
