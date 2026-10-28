# 0.21.2 Changelog - Who saw it

[← Back to Documentation Index](../README.md)

Read [migration-0.21.2.md](../migration-0.21.2.md) before you start the server. It lists every new key, the value used
when it is absent, the two `Crimes` lines that switch the cop-crime changes on, and the behaviour that changed.
Upgrade from 0.21.1. Config files are never rewritten: edit them by hand.

## Requires

- **Keystone 1.15.4** (unchanged from 0.21.1).
- **gangland-api 2.7 and cops-n-crooks `Host_Api` 2.7** (unchanged). Update `cops-n-crooks` and `gangland-civilians`
  together: the 0.21.2 cops-n-crooks reads the 911 call's head count from the 0.21.2 civilians jar.

## Changed

### Stars come from who saw the crime

- **A cop eyewitness radios it in.** A cop who sees you commit a held crime (`Wanted.Witnesses.Crimes`; the bundled
  file now holds `Assault_Cop` and `Kill_Cop` too) glows and reports it after `Report_Seconds` (3). It then counts as
  seen by every witness still alive, and dispatch names the crime spot, never where you stand by then. Kill every
  witness first and the call never goes out. No cop saw it: a civilian's 911 call; nobody: no star (T-538, T-541).
- **More witnesses, more heat.** Each eyewitness past the first adds `Per_Extra_Witness` (0.5) to the multiplier, up to
  `Max_Extra_Witnesses` (3). A 911 call counts the caller and every civilian still watching (T-540). A held crime no
  longer gets the squad's hidden x1.5 (T-538).
- **A cop killed unseen goes silent.** No cop saw the killer or the body: no `Officer down!`, no backup, his slot stays
  filled. Dispatch says `Radio_Check` 10 s after his last radio check-in and `Officer_Missing` 20 s after it, then
  sends backup toward that spot. When his squad was hunting you the kill counts then; otherwise a scene opens there
  for 60 s, and a cop who spots you near it counts the kill. A 911 call that lands first closes the silence, and
  the kill counts once whichever report lands first (T-545, T-546). Keys: `cops.yml` `Cops.Check_In`.
  - A dead beat cop's station sends a beat mate to that spot at `Officer_Missing`; its replacement follows once
    `Respawn_Seconds` (30) have passed, and both hold the spot for `Scene_Seconds` (60) (T-579, T-587). Only a cop who
    held a slot of the squad holds it while silent, and a silent Commander still leads (T-580, T-581).
- **One crime, one star announcement.** A crime that raises two stars at once shows one subtitle, one chat card and
  one siren, at the level it ends at, and dispatch names that level (T-583).
- **Beat cops never appear in view.** A station's beat cop spawns only while no player has the station spot in view
  (owner ruling R6, T-587).
- **One cop kill is one crime**, booked when the cop really dies, at the body: a lethal blow no longer also books
  `Assault_Cop` (T-535), the hit cop's own sighting counts (T-536), and a hit the Defender's shield absorbs is never a
  kill (T-544). `Kill_Civilian` is committed where the civilian died (T-550).
- **Logging out forgives nothing.** A report whose witness is alive when you log out lands at your next join, within
  `Witnesses.Defer_Minutes` (10) (T-551).

### The end of a chase

- **Killing every cop never clears a star** (T-537). While the cops hunt you, only an escape, an arrest, a death or a
  command lowers your stars. With no cop standing the search runs at the last-known spot at `No_Cop_Inside_Speed`
  (0.25) inside the zone: leaving the zone pays, standing on the bodies does not. The first time it does, the chat says
  `No units on you - leave the area` (`Hud.No_Cop_Search`, T-586).
- `Stand_Down` is heard after a wipe (T-542). Late reports name and tip the crime spot (T-543).

## Known limits

- A restart inside a report window, a silence or a scene forgets it.
- A squad's backup spawns on your side of the spot, not at it, and is still held to `Pursuit.Max_Distance` (80) of
  you: a killer more than about 80 blocks from the scene is not found there.
- Requeued units that never find a spawn spot are not re-announced (T-539, cosmetic now).
