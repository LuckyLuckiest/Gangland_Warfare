# Cars & Fuel — Test Checklist

[Back to Test Index](../README.md) | [Developer Doc](../../developer/gadgets.md)

---

## Overview

Drivable cars (gadget system) with a fuel layer. Car templates in `items/cars.yml`; runtime state (fuel) stored on
the item NBT and refreshed via `ItemRefresher`.

**Modules involved:** `gangland-features/gangland-gadget`, `gangland-item`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Op player online.
- [ ] `items/cars.yml` has at least one car type.

---

## Car Smoke Test

- [ ] `/glw car list` → shows all cars.
- [ ] `/glw car give <name> 1` → car item delivered.
- [ ] `/glw car info` (holding the car item) → shows stats + current fuel.
- [ ] Right-click on a flat block → car entity spawns; player mounts.
- [ ] Drive forward/back/left/right → physics feel correct; no teleporting.
- [ ] Dismount → car remains parked.
- [ ] Re-mount and drive → continues from parked state.
- [ ] Park car, `/glw reload` → parked car reappears where it was.

---

## Fuel Smoke Test

- [ ] `/glw fuel info` (holding a car item) → shows current / max fuel.
- [ ] `/glw fuel add 10` → fuel increases by 10 (clamped at max).
- [ ] `/glw fuel remove 5` → fuel drops by 5.
- [ ] `/glw fuel refuel` → fills to max.
- [ ] `/glw fuel defuel` → drains to 0; max unchanged.
- [ ] `/glw fuel defuel 5` → drains 5 units; max unchanged.
- [ ] Drive a car → fuel decreases over time / per unit distance per config.
- [ ] Run out of fuel → car stops; cannot be driven until refuelled.

---

## Item Refresher

- [ ] Fuel changes update the lore/NBT on the next refresher tick.
- [ ] Giving a car via `/glw car give` delivers a freshly-refreshed item.
- [ ] Delivery via shop / trader / loot chest refreshes the item **on each delivery**, not once at placement —
  `feedback_item_refresher_pattern`.

---

## Edge Cases

- [ ] Spawn car in an unsuitable location (inside a wall, inside another car) → rejected with clear message.
- [ ] Multiple cars overlapping → physics do not lag the server.
- [ ] Car entity despawns (chunk unload, world removed) → no orphan NBT on the item.
- [ ] Bukkit `getWorld()` returns null → null-checked per `feedback_get_world_null_check`.

---

## Reload Safety

- [ ] Parked car survives `/glw reload` — part of the lifecycle-level guarantee from
  [lifecycle-and-reload](../lifecycle-and-reload.md).
- [ ] Edit car config (`items/cars.yml`), `/glw reload files` → new car items get updated stats; already-spawned
  cars keep their current stats until respawned.

---

## Persistence

- [ ] Parked car persists across restart.
- [ ] Car item with mid-range fuel persists with that fuel value.
- [ ] Verified on SQLite and MySQL.

---

## Regression Risks

- `gangland-gadget` — car entity, physics, driver binding.
- Fuel system — tied to `gangland-item` fuel NBT.
- Item refresher — stateful delivery pipeline.

---

[Back to Test Index](../README.md)
