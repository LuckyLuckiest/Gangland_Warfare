# 🚔 Lose Them — v0.15.0 Changelog

> *Every crime now feeds one heat ledger, the stars on your screen mean something, and the only way out is to break line of sight and slip the search zone. Welcome to **Cops N Crooks 0.15**.*

---

[← Back to Documentation Index](../README.md)

---

## Overview

Cops N Crooks gets a proper chase. Crimes add **heat**, heat becomes **stars**, and stars drop when you **get out of sight and stay out of the search zone** — not because a timer ran out or you paid a toll. A new wanted HUD tells you where you stand, arrests end in a **charge sheet**, and squads fall back and regroup when you take two of them down. Bounties are now paid by the people who post them.

---

## ✨ New

- **Heat ledger** — Every crime — from brandishing near a cop to a jailbreak — now adds points to one per-player ledger, and the ledger converts to stars through `Wanted.Heat.Star_Thresholds`. Repeat crimes build a streak bonus, being seen by a cop and turf-war crimes scale the points. Turn it off with `Wanted.Heat.Enable: false` to keep the old per-event stars.
- **Line-of-sight evasion** — Break line of sight with every cop for `Lost_Sight_Seconds` and the squad starts searching a zone around your last known position. Stay outside the zone for the star's `Seconds_To_Drop` and you lose a star (or all of them with `Drop_Mode: ALL_STARS`). Getting spotted resets the countdown, and the zone grows with your star level.
- **Wanted HUD and compass** — A star card, a title flash, a siren, a boss bar (red while you are seen, flashing when searched, green for three seconds when you lose them), a particle ring around the search zone and a compass pointing at it. Each piece has its own `Wanted.Hud.<piece>.Enable` switch.
- **Charge sheet** — Arrest reads out every distinct crime on your sheet with counts, the total fine and what you paid. The fine comes out of your wallet only; whatever you cannot cover is served as extra jail time (`Seconds_Per_Unpaid`, capped by `Max_Extra_Seconds`). The paperwork view shows the fine too.
- **Shots give you away** — A wanted player's gunshot re-centres the squad on him, and the nearest cop radios it in. Radius per weapon type is set in `Shot_Noise.Radius` in `cops.yml`.
- **Pull back and regroup** — Two casualties inside `Window_Seconds` and the squad falls back for `Fall_Back_Seconds`, then pushes again once it has regrouped within `Arrival_Radius`. New radio lines cover both.
- **Paid bounties** — A bounty is now the sum of what each poster put up, kept per poster and saved with the user. Killing the target pays out the posted money only; the bounty clock grows notoriety, not money. `Bounty.Pay_Notoriety` lets notoriety pay out too (off by default).

---

## 🔧 Changed

- **Star-drop charge is off by default** — `Wanted.Take_Money.Enable` is now the single switch and defaults to false. When on, the price comes from `Wanted.Take_Money.Formula`.
- **Death bill rules** — `Lose_Money: false` really means no money moves on death. The death `Formula` is evaluated safely: a broken formula warns once and charges 15% of the wallet instead of throwing, and a bill above your balance is clamped to your balance.
- **Decay clock** — The wanted decay clock is always started on the main thread through one path, on login as well as on every raise, and yields to evasion while evasion is handling the player's decay.
- **Self-defence and own-turf defence kills are not crimes** — Killing a player who struck you first raises no star, and a turf defender killing inside their own contested turf mints nothing. Civilian kills count once, not twice.

---

## 🐛 Fixed

- **WB-04** — Logging in while wanted restores your stars and their decay clock.
- **WB-06 / US-18** — Wanted and bounty clocks start on the main thread.
- **WB-12, WB-13, WB-14, WB-16, WB-43** — Bounty arithmetic: posted money is no longer doubled by the timer, and claims pay the posted amount only.
- **WB-20** — Sign, admin and kill raises now start a decay clock.
- **WB-21 / LS-22 (wanted half)** — The wanted aspect now changes the level through the single routed setter.
- **WB-28, WB-29** — Kill-combo increments are signalled, and the combo events fire.
- **CJ-39** — `CopDeathEvent` now fires when a cop dies.
- **US-05** — Death money loss is clamped to the wallet.
- **TF-49** — A defender's kill in their own contested turf mints nothing: no star, no combo step, no kill notoriety.
- **New rows** — The star-drop charge was withdrawn before the level change, so a cancelled change still cost money. `Lose_Money: false` paid the player on every death. A broken death formula threw. The bounty timer doubled the posted escrow, so a claim minted money. Killing in self-defence raised a star. A civilian kill counted twice toward wanted. A player killed by his own arrow claimed the bounty on his own head. A token bounty the killer posted himself made his kill crime-free. A kill at the maximum stars still added the auto bounty. A bounty posted on a player while his row was loading was wiped with the poster's money. An admin jail throw of a player with no stars charged the base fine. A reload in the middle of a chase froze the evasion HUD.

---

## ⚙️ Configuration

- **New file `npc/wanted.yml`** — Heat, Evasion, Hud and Charge_Sheet blocks. Shipped inside the cops-n-crooks module jar.
- **New file `npc/wanted_messages.yml`** — Every wanted HUD, chase and charge sheet string.
- **`cops.yml`** — New `Regroup` and `Shot_Noise` blocks and the `Regroup`, `Regroup_Push` and `Shots_Fired` radio cooldowns.
- **`settings.yml`** — New `Wanted.Take_Money` block (`Enable`, `Formula`, `Amount`, `Multiplier`) and `Bounty.Pay_Notoriety`.

---

## 🧩 API 2.1

- **`GanglandApi.VERSION` is 2.1** — Additive only. Modules that declare `Host_Api: 2.0` keep loading.
- **Crime events** — `CrimeService`, `Crimes` and `CrimeCommittedEvent` let any module report a crime to the ledger.
- **Wanted events** — `WantedEvasionStateEvent` with `EvasionState`, and a `WantedCause` on `WantedLevelChangeEvent`, `WantedStartEvent` and `WantedEndEvent`.
- **Kill combo** — `KillComboEvent` carries a `Kind` of INCREMENT, WANTED_TRIGGER or RESET.

---

## ⬆️ Upgrade notes

- **Read the migration guide** — See [migration-0.15.0.md](../migration-0.15.0.md) before upgrading.
- **Database** — `detainment` gains nullable `fine_paid` and `fine_extra_seconds`; `users` gains nullable `bounty_posters`. Both migrate on first boot.
- **Existing bounties** — Bounties saved before 0.15 are treated as posted money, with no notoriety.
- **Behaviour change** — If you relied on the star-drop charge, set `Wanted.Take_Money.Enable: true`.
- **Bartizan** — `cops-n-crooks` still needs Bartizan 0.6.0 or newer; without it the whole chase (heat, evasion, HUD, charge sheet, regroup, shot noise) is skipped.
