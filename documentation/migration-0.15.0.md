# Migrating a server from 0.13.0 to Gangland 0.15.0

[← Back to Documentation Index](./README.md)

Gangland 0.15.0 is the "Lose them" release of Cops N Crooks. A wanted player is now chased, not just billed: crimes add
heat that becomes stars, breaking line of sight with the cops shakes stars off, the player sees the chase on a boss bar,
and an arrest ends with a charge sheet. Squads pull back after casualties, and a gunshot gives the shooter away. Every
new key has a default, so an untouched 0.13.0 config keeps working, except where a "behaviour changed" note below says
otherwise. No command was added, renamed or removed.

## 1. Update the dependencies

- **Keystone 1.14.0.** Replace `Keystone-<version>.jar` in `plugins/`.
- **Bartizan 0.6.x** if you use Bartizan weapons on cops or civilians (unchanged floor from 0.12.0). The shot-noise
  rule listens to Bartizan's weapon shot event, so without Bartizan nobody fires a weapon that cops can hear. Nothing
  else in this release needs it.
- **Module jars.** Replace the jars in `plugins/Gangland_Warfare/modules/` with the 0.15.0 builds. `cops-n-crooks` and
  `gangland-civilians` now declare `Host_Api: 2.1` (the module API gained the crime bus, see
  [gangland-api.md](./gangland-api.md)), so they need a 0.15.0 core. The other modules keep `Host_Api: 2.0` and still
  load on the 0.15.0 core, so you can leave them as they are. A module jar built for `2.1` does **not** load on a
  0.13.0 core: upgrade the core jar and the two modules together.
- Citizens is unchanged (2.0.42 or newer recommended).

## 2. New files, copied on first boot

`cops-n-crooks` ships two new files and copies them to `plugins/Gangland_Warfare/npc/` the first time it boots. Nothing
to do unless you want to change them.

| File | What it holds |
|---|---|
| `npc/wanted.yml` | The chase: `Wanted.Heat`, `Wanted.Evasion`, `Wanted.Hud`, `Wanted.Charge_Sheet`. Each feature has its own `Enable` switch; switching one off restores the 0.13.0 behaviour of that piece. |
| `npc/wanted_messages.yml` | The text of the boss bar, star card, title and charge sheet (`Hud`, `Charge_Sheet`) and the crime names (`Crimes`). `&` colour codes work. |

A file already in the folder is never overwritten. If a key is missing from either file the shipped default is used
and nothing is reported as an error, so a partly edited file is safe. The tables are in
[Configuration Reference](./developer/configuration.md#wantedyml-npcwantedyml).

## 3. `npc/cops.yml`: keys that exist only as code defaults until you add them

Your existing `cops.yml` is not rewritten, so these new keys are absent from it. The code default applies and is
identical to the value the 0.15.0 file ships. Copy a block in only if you want to change it.

| Key | Default | Meaning |
|---|---|---|
| `Cops.Regroup.Enabled` | `true` | `false` = squads never pull back after casualties (today's behaviour). |
| `Cops.Regroup.Casualties` | `2` | Cops lost inside `Window_Seconds` that make the squad pull back. |
| `Cops.Regroup.Window_Seconds` | `20` | The window those casualties must fall in. |
| `Cops.Regroup.Fall_Back_Seconds` | `15` | The longest the squad stays back before it pushes anyway. |
| `Cops.Regroup.Cooldown_Seconds` | `60` | Minimum gap between two regroups of one squad. |
| `Cops.Regroup.Arrival_Radius` | `24.0` | Blocks; the squad pushes together once backup is this close. |
| `Cops.Shot_Noise.Enabled` | `true` | `false` = shots never reveal the shooter. |
| `Cops.Shot_Noise.Radius.GUN` | `48` | Blocks within which a cop hears a Bartizan gun. `0` = silent. |
| `Cops.Shot_Noise.Radius.THROWABLE` | `16` | Same for a throwable. |
| `Cops.Shot_Noise.Radius.MELEE` | `0` | Same for a melee weapon (silent). A weapon type not listed is silent. |
| `Cops.Radio.Cooldown_Ticks.Regroup` | `1200` | Repeat cooldown of the pull-back radio line. |
| `Cops.Radio.Cooldown_Ticks.Regroup_Push` | `1200` | Repeat cooldown of the push radio line. |
| `Cops.Radio.Cooldown_Ticks.Shots_Fired` | `60` | Per-shooter throttle of the shots-fired line (3 s). |

The three new radio lines (`Regroup`, `Regroup_Push`, `Shots_Fired`) need **no** `Radio.Priority` edit: they bypass the
radio gaps on their own. The new line texts are in `npc/cop_radio_messages.yml` / `_es.yml`; as with 0.12.0, an
existing copy of those files is not rewritten, so a missing line falls back to the default text.

Squads whose tier is set to cuff first (`Skip_Cuffing: false` and still attempting to cuff) never regroup.

## 4. `settings.yml`

The existing file is not rewritten either. These are the keys that matter, with what a file without them does.

| Key | Without the key | Meaning |
|---|---|---|
| `Wanted.Take_Money.Enable` | `false` | **Upgraded servers stop charging money when a star drops, with no edit.** Set `true` to keep the 0.13.0 charge. |
| `Wanted.Take_Money.Formula` | `amount * multiplier ^ wanted` | The price of one star drop. Variables: `amount`, `multiplier`, `wanted` (the stars before this drop), `balance`, `level`, `experience`, `bounty`. |
| `Wanted.Take_Money.Amount` / `Multiplier` | `50` / `5` | Only numbers fed to the formula. They no longer switch the charge on or off. |
| `Bounty.Pay_Notoriety` | `false` | `true` also pays the server-made part of a bounty on a kill, as 0.13.0 did. |
| `User.Death.Money.Lose_Money` | `true` | `false` now means dying costs nothing. |

To keep today's charge exactly: `Take_Money.Enable: true`, `Amount: 50`, `Multiplier: 5` and the default formula. That
gives 50 x 5^stars per star dropped: $250 at 1 star, $156,250 at 5. Gentler examples: `"amount * wanted"` or
`"balance * 0.02 * wanted"`. A price of 0 charges nothing. If the formula is broken, or its result is negative or not a
number, the console warns once and `amount * multiplier ^ wanted` is used instead (never below 0). The star always
drops, and a wallet never goes below zero. The same formula engine now prices the death bill, with the same fallback.

## 5. Database

Three columns are added automatically at the first boot; no SQL and no backup step is needed beyond your usual one.

| Table.column | Type | Holds |
|---|---|---|
| `user.bounty_posters` | text, nullable | Who posted how much of a player's bounty (the escrow ledger). |
| `detainment.fine_paid` | decimal, nullable | The charge-sheet fine actually paid at intake. |
| `detainment.fine_extra_seconds` | integer, nullable | Jail seconds added for the part the wallet could not cover. |

**Every bounty that exists before the upgrade is read as fully posted**: the first kill after the upgrade pays it in
full, once, whoever the killer is and whatever `Pay_Notoriety` says. Bounties created from then on follow the rules in §6.
Rows without the new columns keep working (a missing fine reads as none).

## 6. Behaviour that changed

**Wanted stars**

- **Crimes add heat, heat becomes stars.** A kill or a hit on a cop no longer counts "one kill, one combo step" when
  `Wanted.Heat.Enable` is true: each crime adds the weight in `wanted.yml` (`Crimes`), multiplied by a streak bonus,
  by 1.5 when a cop saw it, and by 0.5 for a player kill inside a contested turf. The star thresholds turn heat into
  stars. `Wanted.Heat.Enable: false` goes back to the 0.13.0 star maths: the kill combo when
  `Wanted.Kill_Combo.Enable` is on, otherwise one star per counted kill.
- **Evasion drives decay while cops hunt.** While at least one of your cops is live and not heading home, the old fixed
  decay timer drops nothing; instead, when no cop has seen you for `Lost_Sight_Seconds` a search zone opens, and staying
  out of sight for `Seconds_To_Drop` removes a star (`Drop_Mode` ONE_STAR or ALL_STARS). Outside the zone the clock
  runs `Outside_Zone_Speed` times faster. A cop sighting, a shot you fire near a cop, or being cuffed resets or pauses
  it. With no cops hunting, or `Evasion.Enable: false`, `Wanted.Repeating_Timer` decays stars exactly as before and
  stays as the safety net.
- **The decay clock is on the main thread** and every star change names a cause (crime, sign, admin, restore, decay,
  evasion, bribe, arrest, death). The decay clock now also starts after a sign or admin raise and after a login
  restore, where before the level could sit there for ever.
- **Heat ledger.** The heat of a chase is kept in memory per player and ends with the chase (`WantedEndEvent`). It is
  not saved: a restart or a quit forgets the heat but not the stars.
- **A civilian kill counts once.** It used to add a star twice (once in the damage listener, once in the civilian
  reward listener); it is now one crime.
- **Not crimes:** a kill in self-defence (the other player struck first inside a 30 second fight window), a kill that
  claims a bounty players posted, and a turf defender killing a raider inside their own gang's contested turf. These
  mint no star and no combo step.

**Bounties**

- **Posted claims.** A kill pays what players actually put up (the posted escrow). The server-made growth ("notoriety":
  the kill bonus and the repeating multiplier) is kept on the player and is **not** paid unless `Bounty.Pay_Notoriety`
  is true. The repeating timer now grows notoriety only, so a claim can no longer pay money that was never posted.
- **Gangmates collect nothing.** A killer in the same gang as the victim, or in an allied gang, gets no payout and the
  bounty stays.
- **Notoriety is kept through death and arrest.** Nothing in 0.15.0 clears the server-made part of a bounty on death
  or arrest; only a paid claim (with `Pay_Notoriety: true`), `/glw bounty clear` or a sign removes it. This is
  deliberate for now and is flagged for the owner to confirm.

**Death and arrest**

- **Safe death formula.** The money lost on death is read through the same safe evaluator: a broken formula warns once
  and uses 15% of the wallet, and the bill can never exceed the wallet. `Lose_Money: false` charges nothing and pays
  nothing (it used to pay the player on every death).
- **Charge sheet.** On arrest the cops read the crimes of the chase, fine the player (`Base + Per_Wanted_Level x stars`,
  at most `Maximum`) from the **wallet only**, never the bank and never below zero, and turn the part the wallet could
  not cover into extra jail time (`Seconds_Per_Unpaid` per unit, at most `Max_Extra_Seconds`). A player who is dead at
  intake is not fined again. The paperwork screen shows the fine paid and the extra time. `Charge_Sheet.Enable: false`
  removes it.
- **Regroup.** After `Casualties` cops fall inside `Window_Seconds` the squad falls back to cover, radios for backup
  and pushes together when backup is within `Arrival_Radius`.
- **Shots give you away.** A gun, or a throwable, fired within the `Shot_Noise` radius of a cop of your squad reports
  your position to the squad, which counts as a sighting, and the nearest cop says "shots fired".
- **Wanted HUD.** A boss bar with the stars (red in sight, yellow and searching with a countdown, green for three
  seconds when a star is lost), a star card (why you are wanted, which tier is coming, or why a star dropped), a title
  and a siren, a particle ring around the search zone, and a compass pointing at the nearest cop. Each piece has its
  own switch under `Wanted.Hud`.

## 7. Events and API for plugin authors

New events: `CrimeCommittedEvent` (cancellable, fired by `CrimeService.commit`), `WantedEvasionStateEvent`. The wanted
events gain `getCause()`. `KillComboEvent` and `CopDeathEvent` are now actually fired through Bukkit. Details in
[gangland-api.md](./gangland-api.md) and the [developer guide](./developer/cops-n-crooks.md#events).

## 8. Required versions

- Keystone **>= 1.14.0** (required).
- Bartizan **>= 0.6.0** for armed cops and the shot-noise rule; a server without Bartizan needs nothing.
- Modules: `cops-n-crooks` and `gangland-civilians` 0.15.0 (`Host_Api: 2.1`); the other modules may stay on their
  0.13.0 jars (`Host_Api: 2.0`).

## 9. Upgrade checklist

1. Stop the server and back up `plugins/Gangland_Warfare/`.
2. Replace `Keystone`, `Gangland_Warfare` and the module jars (§1).
3. Start the server. Check the console for `Runtime modules: ... loaded, 0 fault(s)` and that `npc/wanted.yml` and
   `npc/wanted_messages.yml` now exist.
4. Decide on money: leave `Take_Money.Enable` off (the new default) or set it to `true` to keep the charge (§4).
5. Decide on bounties: leave `Pay_Notoriety` off, or set it to `true` for the 0.13.0 payout (§4).
6. Optionally copy the `Regroup` and `Shot_Noise` blocks into `cops.yml` to tune them (§3).
7. Play-test: `/glw wanted add 2`, break line of sight and watch the boss bar count down.
