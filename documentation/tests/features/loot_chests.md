# Loot Chests — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/loot_chests.md)

---

## Overview

Placeable loot chests with tiered loot tables, keys, hologram labels, and cooldowns. Managed by `LootChestManager`
(module `gangland-ui/lootchest-api`) with hologram support from `gangland-ui/hologram-api`. Configured via
`lootchests/loot_chests.yml` and `lootchests/tiers.yml`.

**Modules involved:** `gangland-ui/lootchest-api`, `gangland-ui/hologram-api`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Op player online.
- [ ] `lootchests/tiers.yml` has at least one tier with loot entries.

---

## Smoke Test

- [ ] `/glw lootchest` → receive loot-chest wand.
- [ ] Right-click a block to place a chest of the wand's current tier.
- [ ] Hologram appears above the chest showing tier label.
- [ ] A regular player right-clicks → loot is rolled from the tier table; inventory opens with contents.
- [ ] Re-click before cooldown expires → cooldown message; no loot.
- [ ] `/glw lootchest edit` → edit wand settings (tier, cooldown).
- [ ] `/glw lootchest remove` → targeted loot chest removed; hologram disappears.

---

## Edge Cases

- [ ] Place chest in unloaded chunk, let chunk unload → chest still opens correctly after chunk re-loads.
- [ ] Open with full inventory → graceful handling (overflow drops, or denied).
- [ ] Tier with zero loot entries → clear error, not empty inventory.
- [ ] Corrupt `tiers.yml` → plugin logs clear error; server stays up; loot chest feature disabled gracefully.

---

## Reload Safety

- [ ] Place chest, open it mid-session. Run `/glw reload`.
- [ ] Chest still exists at the same location.
- [ ] Hologram re-renders correctly (no duplicate holograms).
- [ ] Loot rolls still work.

---

## Persistence

- [ ] Place chest, restart server → chest and hologram reappear.
- [ ] Cooldown state (per-player last-opened timestamp) persists.
- [ ] Verified on SQLite and MySQL.

---

## Regression Risks

- `LootChestManager` — placement, roll, persistence.
- Hologram API — spawn, update, remove on reload (see [scoreboard](./scoreboard.md) for similar concerns).
- Item refresher pattern — if chest delivers stateful items (like fuel-bearing cars), they must refresh via
  `ItemRefresher` per `feedback_item_refresher_pattern`.

---

[Back to Test Index](../README.md)
