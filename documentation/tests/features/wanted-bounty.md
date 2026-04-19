# Wanted & Bounty — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/wanted-bounty.md)

---

## Overview

Wanted-star system (actions raise/lower wanted level; drives cop scaling) and bounty system (players place cash
bounties on other players' heads).

**Modules involved:** `gangland-features/cops-n-crooks`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Online player `A` (target) and `B` (placer).
- [ ] `B` has enough cash to place a bounty.

---

## Wanted Smoke Test

- [ ] `/glw wanted` → shows current wanted level (0 stars initially).
- [ ] Op: `/glw wanted add 2` → wanted increases to 2 stars; cop scaling kicks in.
- [ ] `/glw wanted remove 1` → drops to 1 star.
- [ ] `/glw wanted clear` → drops to 0.
- [ ] Op: `/glw wanted clear A` → clears another player's level.
- [ ] Commit a wanted-raising action (e.g. fire weapon in safe zone, attack civilian) → level increases automatically.

---

## Bounty Smoke Test

- [ ] `B`: `/glw bounty` → no active bounty.
- [ ] `B`: `/glw bounty set A 500` → `B`'s wallet debited; `A` has a 500 bounty.
- [ ] `A` is killed by `C` → `C` receives 500; bounty clears.
- [ ] `B` places bounty, then `/glw bounty remove A` → refunded (or per policy).

---

## Edge Cases

- [ ] `B` places bounty on themselves → rejected.
- [ ] Negative bounty amount → rejected.
- [ ] `A` logs off with active wanted level → level persists; cops despawn per config.
- [ ] Two players stack bounties on `A` → both payouts resolve on death.

---

## Reload Safety

- [ ] Active wanted level survives `/glw reload`.
- [ ] Active bounty survives `/glw reload`.
- [ ] Cop-spawn scaling re-initialises off the restored wanted level.

---

## Persistence

- [ ] Wanted level persists across restart.
- [ ] Bounties persist across restart (both SQLite and MySQL).

---

## Regression Risks

- Cop spawn scaling — wanted level drives `CopSpawnManager` spawn rates.
- Economy contract — bounty placement debits wallet; payout credits killer.
- Safe-zone detection — entering a safe zone should clear or pause wanted accumulation per config.

---

[Back to Test Index](../README.md)
