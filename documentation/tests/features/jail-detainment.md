# Jail & Detainment — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/jail-detainment.md)

---

## Overview

Handcuff → transport → jail → timer → release flow. Managed by `DetainmentService` and `JailManager`.

**Modules involved:** `gangland-features/cops-n-crooks`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Two players: op `A` and target `B`.
- [ ] At least one jail configured via `/glw jail create`.

---

## Smoke Test

- [ ] `A` (op): `/glw jail create` → jail at current location.
- [ ] `/glw jail list` → jail appears.
- [ ] `/glw jail info <id>` → capacity, location shown.
- [ ] `A`: `/glw cuff B` → `B` is handcuffed (movement restricted, glow per config).
- [ ] `A`: `/glw jail throw B` → `B` teleports into the jail; release timer starts.
- [ ] Timer elapses → `B` auto-released.
- [ ] `/glw cuff B` followed by `/glw uncuff B` → cuffs removed.
- [ ] `/glw jail release B` → early release by op.
- [ ] `/glw jail teleport <id>` → op teleports to jail.
- [ ] `/glw jail remove <id>` → jail removed.

---

## Edge Cases

- [ ] Cuff `B` while already cuffed → idempotent; no stacked effects.
- [ ] Throw `B` in a jail when it is full → either overflow rules apply or command is rejected; no NPE.
- [ ] `B` logs out while in jail → on next login, `B` is still in jail with remaining timer.
- [ ] Jail location in an unloaded / missing world → per `feedback_get_world_null_check`, `Bukkit.getWorld()` must
  be null-checked. Clear error, no crash.
- [ ] Cuff an op → verify whether ops are exempt per policy.

---

## Reload Safety

- [ ] `B` is in jail. Run `/glw reload`.
- [ ] `B` remains in jail; timer continues correctly.
- [ ] Jail list and locations are intact.

---

## Persistence

- [ ] Jail definitions persist across restart.
- [ ] Currently-jailed players persist across restart with remaining time.
- [ ] Cuff state — verify whether this persists per design; if not, cuffs clear on logout, no ghost cuff on relog.
- [ ] Verified on SQLite and MySQL.

---

## Regression Risks

- `DetainmentService` — cuff toggle, movement restrictions.
- `JailManager` — jail CRUD + timer scheduler.
- Cop NPC detainment trigger — cops auto-cuff on catch-up (see [cops-n-crooks](./cops-n-crooks.md)).

---

[Back to Test Index](../README.md)
