# Unique Items — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/unique-items.md)

---

## Overview

Custom items with bespoke interaction handlers (phones, lockpicks, keys, etc.). Configured in
`items/unique_items.yml`. Interaction dispatch is handled by `GanglandUniqueItemInteractionService`. Stateful items
use an `ItemRefresher` registered in `GameplayConfig` per `feedback_item_refresher_pattern`.

**Modules involved:** `gangland-item`, `gangland-impl`.

---

## Pre-Conditions

- [ ] `items/unique_items.yml` has at least one item defined (e.g. phone).

---

## Smoke Test

- [ ] `/glw item unique list` → lists all registered unique items.
- [ ] `/glw item unique give <name> 1` → item delivered.
- [ ] Right-click with the item → interaction fires (e.g. phone opens inventory GUI).
- [ ] `/glw item unique info <name>` → shows item stats.

---

## Edge Cases

- [ ] Right-click in air → interaction fires if the item supports air clicks (watch `feedback_interact_event_air` —
  `ignoreCancelled = true` on `PlayerInteractEvent` hides `RIGHT_CLICK_AIR` events).
- [ ] Right-click on a block → interaction fires, block action is cancelled appropriately.
- [ ] Duplicate item via `/give` or creative mode → unique-item NBT attaches correctly; behaviour still fires.
- [ ] Drop item → no stale handler remains attached.
- [ ] Stack two unique items → behaviour of stack is consistent (either coalesce or refuse, per config).

---

## Stateful Items (Refresher)

- [ ] If the item carries mutable state (fuel, charges, durability), deliver it via a shop / trader / loot chest.
- [ ] On delivery, the `ItemRefresher` runs and the lore + NBT reflect current state.
- [ ] Pick up the item from the ground → refresher fires again.
- [ ] Reload the plugin → existing items in inventories still render correctly.

---

## Reload Safety

- [ ] Give a unique item, run `/glw reload`.
- [ ] Item still triggers its handler after reload.
- [ ] Config change to the item (e.g. rename) takes effect on next delivery.

---

## Persistence

- [ ] Unique item in an offline player's inventory is untouched by server restart.
- [ ] Item NBT survives server restart.

---

## Regression Risks

- `GanglandUniqueItemInteractionService` — interaction dispatch.
- `ItemRefresher` — shop/trader/loot-chest delivery paths.
- NBT-API integration — required dep; handle gracefully if NBTAPI is missing.

---

[Back to Test Index](../README.md)
