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

## Contacts, Self-Defence and Takedowns (0.16.0)

**S9 Contact (command and phone)**
- [ ] Wanted at 2 stars and unseen: `/glw contact` wipes 1 star, takes `Price_Per_Star` (1000), says "Your contact made 1 star(s) disappear";
      `/glw contact 2` wipes up to 2 (never more than your level, never above `Max_Stars`).
- [ ] The phone's Contacts page (`phone_contacts`) does the same; `%gangland_contact_price%` and `%gangland_contact_cooldown%` read
      the price per star and `ready` / the time left.
- [ ] Right after a wipe: "Your contact is lying low. Try again in ..." for `Cooldown_Seconds` (600); the cooldown survives leaving the
      chase, a quit and a new chase, and is cleared by a restart.
- [ ] Not wanted: "You have no stars to wipe."; too little money: "You need ...", nothing taken.
- [ ] `Contacts.Enable: false`: "Nobody picks up." and the sign behaves as in 0.15.

**S10 [WANTED] sign gate**
- [ ] A cop has eyes on you: a paid REMOVE/CLEAR sign refuses ("Not while a cop has eyes on you.") and your balance is unchanged.
- [ ] Unseen: the sign works, and a paid wipe starts the shared cooldown (the command then refuses too, and the other way round).
- [ ] An INCREASE sign and a price-0 sign are unchanged.

**S11 Self-defence**
- [ ] A rival hits you for at least `Min_Damage` (2.0, one heart) first; you kill him within 8 s: no star.
- [ ] You hit him first, wait, he hits back, you kill him: a crime (provocation). A hit for less than `Min_Damage` opens no window.
- [ ] A second kill of the same player inside 600 s is a crime. Gang mates and allies never count. `Self_Defence.Enable: false`:
      every kill is a crime.

**S12 Posted bounty takedown**
- [ ] A bounty of at least `Takedown_Minimum` (100) posted by other players: killing the target gives you the money and no star.
- [ ] A posted bounty below 100: it pays, but the kill is an ordinary crime (WB-48).
- [ ] The fourth crime-free takedown inside an hour is a crime; the same pair inside the pair cooldown is a crime.

**S13 Kill with nothing posted**
- [ ] Kill a five-star player nobody has put money on: you gain a star.

**Rampage (AUTO mode)**
- [ ] Brandish at a cop, hit a civilian and steal cars four times in the opening: not a rampage (`Rampage_Min_Weight` 80); four kills or
      cop kills still are.

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
