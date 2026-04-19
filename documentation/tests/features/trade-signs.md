# Trade Signs — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/trade-signs.md)

---

## Overview

Placeable in-world signs that act as buy/sell endpoints for weapons and ammo. Managed by the sign-api module and
`SignManager`.

**Modules involved:** `gangland-ui/sign-api`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Op player online.
- [ ] At least one weapon and one ammo type configured.

---

## Smoke Test

- [ ] Place a sign. Write the configured trade-sign header on line 1.
- [ ] Fill remaining lines per the sign spec (buy/sell, item key, amount, price).
- [ ] Confirm the sign converts to a trade sign (glow, colour, or hologram marker per config).
- [ ] Right-click as a non-op buyer → confirmation prompt; accepting debits wallet and delivers the item.
- [ ] Right-click a sell sign holding the correct item → credits wallet; item removed.

---

## Edge Cases

- [ ] Malformed sign (typo in header) → sign stays as a normal sign; no exception.
- [ ] Item key refers to an item that no longer exists → error message; transaction cancelled.
- [ ] Insufficient funds on buy → denied; item not delivered.
- [ ] Insufficient inventory space on buy → denied; wallet not debited.
- [ ] Break the sign → deregisters from the manager; chunk unload/reload does not leave a ghost.

---

## Reload Safety

- [ ] Place a sign, run `/glw reload` → sign still functions.
- [ ] Edit the sign's parsing config, `/glw reload files` → sign uses new rules.

---

## Persistence

- [ ] Sign survives chunk unload/reload.
- [ ] Sign survives server restart on SQLite and MySQL.

---

## Regression Risks

- `SignManager` — block-place, block-break, sign-change events.
- Chunk-load hooks — sign must re-activate when its chunk loads.
- Economy contract — buy debits, sell credits.

---

[Back to Test Index](../README.md)
