# Levels — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/levels.md)

---

## Overview

Per-player XP and level system with a configurable XP formula and level-up rewards.

**Modules involved:** `gangland-impl`.

---

## Pre-Conditions

- [ ] Online player `A`.

---

## Smoke Test

- [ ] `/glw level` → shows current level and XP bar.
- [ ] `/glw level next` → shows XP required for next level.
- [ ] Op: `/glw level exp add 100` → XP bar increases; no level-up if below threshold.
- [ ] Op: `/glw level exp add 10000` → player levels up; level-up message fires; rewards (if any) delivered.
- [ ] Op: `/glw level exp remove 50` → XP decreases; cannot go below zero for current level.
- [ ] Op: `/glw level add 1` → level increases by 1; next-level XP recalculated.
- [ ] Op: `/glw level remove 1` → level decreases; can underflow to 0 or configured minimum.

---

## Edge Cases

- [ ] Add XP that crosses multiple levels in one hit → all intermediate level-up events fire in order.
- [ ] Remove XP below the current level → current level drops by 1 and XP rolls over.
- [ ] Gain XP while offline (via op command on offline player) → reflected on next login.
- [ ] Level cap reached → further XP is clamped; no overflow.

---

## Reload Safety

- [ ] Level + XP state survives `/glw reload`.

---

## Persistence

- [ ] Level and XP persist across restart on SQLite and MySQL.

---

## Regression Risks

- XP formula calculation — verify via `/glw level next` after config changes.
- Level-up reward delivery — must use the item refresher pattern if rewards are stateful.

---

[Back to Test Index](../README.md)
