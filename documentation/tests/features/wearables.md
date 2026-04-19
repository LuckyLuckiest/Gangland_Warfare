# Wearables — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/wearables.md)

---

## Overview

Custom armour pieces with trait-based damage reduction. Configured in `items/wearables.yml`.

**Modules involved:** `gangland-item`, `gangland-impl`.

---

## Pre-Conditions

- [ ] `items/wearables.yml` has at least one wearable defined.

---

## Smoke Test

- [ ] `/glw item wearable list` → all wearables listed.
- [ ] `/glw item wearable give <name> 1` → delivered.
- [ ] Equip the wearable in the correct armour slot.
- [ ] Take damage from a configured damage source → reduction applies per the trait.
- [ ] `/glw item wearable info <name>` → traits + reductions shown.

---

## Edge Cases

- [ ] Equip a wearable in a wrong slot → either rejected by the slot check or worn without bonuses per config.
- [ ] Two wearables with overlapping traits → additive vs capped per config policy; no double-application beyond
  the cap.
- [ ] Unequip in combat → reduction drops mid-hit correctly.
- [ ] Damage source not in the wearable's trait list → full damage applied (no accidental blanket reduction).

---

## Reload Safety

- [ ] Wearing a wearable, run `/glw reload` → wearable continues to function.
- [ ] Edit a trait value in `items/wearables.yml` → `/glw reload files` → new value applies on next damage event.

---

## Persistence

- [ ] Wearable in inventory / equipped in armour slot survives restart.
- [ ] NBT traits intact after restart.

---

## Regression Risks

- Damage pipeline — wearable reduction must apply after armour but before `Flat_Damage` from weapons (see
  [weapons](./weapons.md)).

---

[Back to Test Index](../README.md)
