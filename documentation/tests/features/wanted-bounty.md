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

## Chase (0.15.0)

- [ ] Heat on, default config: hit a cop, one star (cause crime) and a boss bar appear; the star card names the crime.
- [ ] Heat off (`Wanted.Heat.Enable: false`): the old kill-combo star maths apply; a civilian kill counts once either way.
- [ ] Break line of sight for `Lost_Sight_Seconds`: the bar turns yellow with a countdown; at 0 a star drops and the bar
      shows the green "star lost" state for 3 s.
- [ ] While cops hunt you, the `Repeating_Timer` drops no star; with `Evasion.Enable: false` it drops as before.
- [ ] A shot you fire near a cop puts the bar back to red.
- [ ] Each `Wanted.Hud.*.Enable: false` removes only its own piece (Boss_Bar, Star_Card, Title, Siren, Zone_Ring, Compass).
- [ ] Sign `[WANTED]` raise and `/glw wanted add` start a decay clock; a login with a saved level restores it and starts one.
- [ ] A kill in self-defence (the other player struck first) adds no star and no bounty; a turf defender killing a raider
      inside their own contested turf adds none either.

## Evasion AUTO Mode (0.15.2)

- [ ] `Drop_Mode: AUTO` with one crime (1 star): a drop shows a Petty card ("Small fry, they dropped the case").
- [ ] `Drop_Mode: AUTO` after a 4+ star rampage: a drop shows a Still Hot card ("Still hot, one star at a time").
- [ ] `Drop_Mode: AUTO` leaving the search zone (outside ratio > 0.5): a drop shows a Clean Break card ("Clean break, you left the area") losing half stars.
- [ ] `Drop_Mode: AUTO` with 2+ stars lost at once: the bar flashes an Evaded_Many line ("X -%count% STARS").

## Star-Drop Charge (0.15.0)

- [ ] Default (and an upgraded `settings.yml` without `Take_Money.Enable`): a star dropping moves no money.
- [ ] `Take_Money.Enable: true`: 50 x 5^stars is charged per drop; a wallet never goes below zero and the star still drops.
- [ ] A broken `Take_Money.Formula`: one console warning, the default price is charged, the star drops.
- [ ] `User.Death.Money.Lose_Money: false`: dying costs and pays nothing; a broken death `Formula` warns once.

## Paid Bounties (0.15.0)

- [ ] Player `B` posts 500 on `A`; `A`'s kill by `C` pays 500 (posted) and leaves the server-made notoriety on `A`.
- [ ] `Bounty.Pay_Notoriety: true` also pays the notoriety, as 0.13.0 did.
- [ ] A gangmate or ally of `A` who kills `A` collects nothing; the bounty stays.
- [ ] A bounty saved before the upgrade (no `bounty_posters`) is paid in full, once.
- [ ] `A` dies or is jailed with notoriety: the notoriety is still there afterwards.
- [ ] The bounty timer grows notoriety only and cannot make a claim pay money nobody posted.
- [ ] The `user` table gains `bounty_posters` on first boot; existing rows load.

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
