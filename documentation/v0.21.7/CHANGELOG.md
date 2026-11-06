# 0.21.7 Changelog - Turf garrison

[← Back to Documentation Index](../README.md)

Read [migration-0.21.7.md](../migration-0.21.7.md) before you start the server. **Existing servers: copy the three new
powerups into `turf/turf_powerups.yml`**, or the Quartermaster will not sell them. Every other new key has a code default.
Upgrade from 0.21.5 or 0.21.6. Config files are never rewritten: edit them by hand to tune.

## Requires

- **Keystone 1.15.5** (unchanged).
- **gangland-api and module `Host_Api`** (unchanged). The `gangland-turf` and `cops-n-crooks` module jars change.
- **Bartizan:** the build floor is 0.6.0 (the pom, unchanged).
- **Citizens** (soft): guards need it. Without it nothing spawns and nothing is spent.

## Changed

### Bought guards stand on the turf

- **Stock means guards owned.** A bought guard stands at one of the turf's posts as soon as the area is loaded, with no
  contest needed. With stock 10 and the default `Standing_Cap` of 5, five guards stand, one per post, and five wait in
  reserve. Above 5 the guards share posts.
- **Only a contest kill costs stock.** A guard killed while a contest runs leaves the stock and pays its bounty. A guard
  killed with no contest running is free, pays no bounty, and comes back after `Wave_Cooldown_Seconds`. A guard lost to a
  chunk unload, a `/glw reload` or a restart comes back free as soon as its area loads.
- **Contests top up from stock.** The guards already standing fight on, and replacements come in waves of `Wave_Size` up to
  `Max_Alive`. Nothing doubles and nothing flaps: a capture that starts does not spawn a second set of guards.
- **Guards stay on the turf.** Survivors no longer return to a reserve after a contest. They walk home and stay at their
  posts. Surplus guards above `Standing_Cap` leave `Stand_Down_Seconds` after a fight.
- **The Garrison panel counts guards owned.** It shows `Owned n/max`, `On duty` and `In reserve`. `Garrison.Max_Reserve`
  is the cap on guards owned, standing or in reserve.
- **Owners are told** of a contest top-up with the wave line. A peace-time respawn is silent.

### Guards answer cops at all times

- A standing guard answers a cop who hits a gang member on the turf, in peace as well as in a contest. The **Hold** order
  still keeps guards off cops, and a cop whose stray hit lands on a guard is dropped within a quarter second.

### Hitting an idle guard is still a crime

- Hitting an idle rival guard still commits assault, and killing one that is not fighting still commits a killing. No code
  change: the guards are idle most of the time now, so this decides who dares attack a turf.

### Three guard powerups

- **Guard Toughness** (`guard_toughness`, 2000): guards have 1.5 times the health while it runs, standing guards too. Current
  health keeps its share. When it ends, health falls back and no guard dies.
- **Bigger Guard Waves** (`bigger_guard_waves`, 3000): 1.5 times `Wave_Size` and `Max_Alive` during a contest. It only spends
  guards the turf already owns.
- **Faster Reinforcements** (`faster_reinforcements`, 1500): half the wait before replacements arrive, in a contest and
  outside one.
- Buying again stacks, up to `Limits` (`Max_Guard_Toughness` 3.0, `Max_Guard_Waves` 2.0, `Min_Reinforcement_Wait` 0.25).
  Out-of-range values are skipped or clamped with a console warning at load.

### Quartermaster panels

- **Oriel look.** Each panel has a border and a fill from `turf_settings.yml` (`Menu.Border_Item`, `Menu.Fill_Item`) and an
  Oriel-style title.
- **Readable lore.** Icons carry no attribute or potion text. The catalogue shows the friendly effect ("+50% health on your
  guards while it runs") instead of the raw enum, `Duration`, `Cost`, and the time left on an active buff. A boost bought
  from the panel is named by its display name in chat.
- **Catalogue pages hold 21 boosts.**

### Config

- **New** `Garrison.Standing_Cap` (`turf/turf_npcs.yml`, default 5). `0` keeps the old behaviour, where guards appear only in
  a contest.
- **Removed** `Defender.Lifespan_Seconds`. Guards no longer expire.
- `Garrison.Stand_Down_Seconds` now means how long surplus guards stay after a fight.

## Fixed

- Bought guards never appeared until a rival started a capture, so a solo owner saw nothing for the money (row 880).
- A late garrison save could land after a turf was removed and bring its guards back for the next owner (row 881).
- A guard that a chunk reload left untracked could survive the recall (row 882).
- A contest refund after a turf was deleted wrote a stock row for the deleted id (row 883).
- A guard that vanished mid-contest cost stock for its replacement (row 884).
- Buff lore printed the raw `Effect_Type` and chat printed the powerup id (row 888).
- The Quartermaster panels had no border, and icons showed attribute and potion tooltips (row 889).
- Two comments described the buff code wrongly (row 890).
- A cop's killing blow on a guard paid the guard bounty.
- A guard that was not fighting kept a remembered player as its target after a fight.
- A recycled guard could walk to a post in an unloaded chunk.

## Docket

Fixed: 880, 881, 882, 883, 884, 888, 889, 890. Open by decision: 885 (a rival's hit or an answer to a cop sets wanted on a
standing guard; watch it on the Test Server), 886 (hitting or killing an idle guard is a crime, by owner ruling),
887 (a discount of 0.5 or lower lets an alt account farm the bounty once the buff lapses), 891 (a wanted member can shelter
on the turf, and each fight can pull more cops; watch it on the Test Server). Still open: 298 for contest batches sharing a
post, and 628 (crowd cover).

## Decisions and open items

- **The numbers are tuning, not measurement.** `Standing_Cap` 5, the three prices, the 1.5 multipliers and the 20 s wave
  cooldown are reasoned defaults. Change them in YAML.
- **The owner's visual check is still open.** Open all four Quartermaster panels: the border and fill, no attribute or potion
  text on any icon, the friendly effect wording, the time left on an active buff, and the three new powerups after you copy
  them in.
- **Safe-house risk (row 891).** Peace deaths are free and guards answer cops at all times, so a wanted member can hide
  behind the guards. Watch how often this pulls more cops in on the Test Server. The one-line gate, if it plays badly, is a
  contest check in the cop answer.

## For developers

- gangland-api, Keystone and Bartizan are unchanged. `GarrisonRoster` is the new bean that reconciles every stocked turf and
  reads the buffs live. `TurfDefenderDeployer` runs the one 5-tick loop that spawns, trims and reaps guards. `TurfDefenderConfig`
  gains `standingCap` and loses `lifespanSeconds`. `GuardBuffs` holds the pure rules for the three effects. The turf menus
  share a `MenuStyle` seam. The friendly-fire handlers for guards and the owner move from cops to the turf module.
