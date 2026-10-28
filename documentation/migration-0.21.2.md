# Migrating a server from 0.21.1 to Gangland 0.21.2

[← Back to Documentation Index](./README.md)

0.21.2 makes cop crimes and the end of a chase follow what the cops actually saw. A new install gets everything from the
shipped files. **An existing server keeps its old files** (`copsncrooks/wanted.yml` and the others), and the code
never rewrites them, so every new key below is read with the value shown when it is absent. Edit the files by hand only
to change those values.

It covers every 0.21.2 stage, S1 to S5.

**Update `gangland-civilians` and `cops-n-crooks` together.** The 0.21.2 cops-n-crooks jar reads the 911 call's head
count from the 0.21.2 civilians jar (`CivilianCalledInEvent.getViewers()`). A 0.21.2 cops-n-crooks next to a 0.21.1
civilians jar loads, but the first 911 call that lands throws `NoSuchMethodError` inside the event handler and the
crime is never counted (`Depends: [civilians]` names no version; docket KS-MO-07). Drop both jars into
`plugins/Gangland_Warfare/modules/` in the same restart.

## 1. New keys (all optional)

`copsncrooks/wanted.yml`:

```yaml
Wanted:
   Evasion:
      # 0.21.2: how fast the timer runs inside the search zone while no cop stands (every cop dead or walking home):
      # 0.25 = four times slower, so leaving the zone pays and standing on the bodies does not; floored at 0.01, so
      # it never freezes
      No_Cop_Inside_Speed: 0.25
```

- Absent: 0.25. Use `1.0` for no penalty for staying, or a value near 0 for a strict server (0 is read as 0.01).

```yaml
Wanted:
   Heat:
      # 0.21.2: added to the witness multiplier (Seen_By_Cop_Multiplier when a cop saw it, else 1.0) for each
      # eyewitness past the first, cop or civilian, counted when the report lands. 0 = the head count is ignored
      Per_Extra_Witness: 0.5
      # 0.21.2: how many eyewitnesses past the first count
      Max_Extra_Witnesses: 3
```

- Absent: 0.5 and 3. Set `Per_Extra_Witness: 0` (or `Max_Extra_Witnesses: 0`) to ignore the head count. That does
  not score every crime as 0.21.1 did: a crime in `Wanted.Witnesses.Crimes` no longer gets the squad's x1.5 for having
  you in sight (T-538), and a cop crime now lands through a cop's report (section 2).

`copsncrooks/wanted_messages.yml`, under `Hud.Card` (absent = the English shown; `%count%` is how many saw it):

```yaml
Hud:
   Card:
      Reported: "%crime% (reported by %count% witness(es))"
      Seen: "%crime% (seen by %count% officer(s))"
      Missing: "%crime% (an officer stopped answering)"
      Scene: "%crime% (spotted near the scene)"
```

- `Reported` existed in 0.21.1 as `"%crime% (reported by a witness)"`. A deployed file keeps that text, which still
  works (it simply names no count). Replace it to show the count.
- `Reported`, (S4) `Seen` and (S5) `Missing` (a kill dispatch counted when a cop hunting you stopped answering) and
  `Scene` (a cop spotted you near the scene of a kill nobody saw) can appear.

`copsncrooks/wanted_messages.yml`, under `Hud` (absent = the English shown; `""` turns it off):

```yaml
Hud:
   # said in chat once per chase when the search runs with every cop dead or walking home
   No_Cop_Search: "&7No units on you - leave the area"
```

### 1.1 Cop eyewitness reports (stage S4)

`copsncrooks/wanted.yml`, under `Wanted.Witnesses`. **The two `Crimes` lines are the switch:** a deployed file lists its
crimes, so without them a cop crime still counts at once, as in 0.21.1.

```yaml
Wanted:
   Witnesses:
      # a report whose witness is still alive when you log out lands when you next join, within this many minutes
      Defer_Minutes: 10
      Crimes:
         # ... your existing entries ...
         # 0.21.2: remove these two to count a cop crime at once again
         - Assault_Cop
         - Kill_Cop
      Cops:
         # seconds the nearest witness takes to radio it in
         Report_Seconds: 3
         # full width of a cop's view cone in degrees; 360 = all round
         View_Angle: 180.0
         # a cop this close with a clear line of sight sees you whatever the light or sneaking
         Witness_Floor: 6.0
         # crimes that turn every cop in range toward the noise (a gunshot is always loud)
         Loud_Crimes: []
         # the reporting cop glows while he radios it in
         Reporter_Glow: true
```

- Absent: `Defer_Minutes` 10; the whole `Cops` block as shown. A server with no `Witnesses` block at all gets the
  shipped five crimes, cop crimes included.

`copsncrooks/cops.yml`, under `Cops.Radio`: **no edit needed.** The code adds `Officer_Report` to the radio's
priority lines even when the file has its own `Priority` list (as Ruling R43 does for `Most_Wanted`), so the report is
never dropped because another line was just heard. Its cooldown is 100 ticks when `Cooldown_Ticks` does not name it.

`copsncrooks/cop_radio_messages.yml` (and `_es`), under `Lines` (absent = the English shown; `%reporter%` is the cop,
`%place%` the crime spot):

```yaml
   Officer_Report:
      - "%reporter% reports %crime% near %place%. All units respond."
```

`copsncrooks/wanted_messages.yml` (absent = the English shown; `""` turns a line off):

```yaml
Witness:
   No_Witness: "&7Nobody saw that."
   Cop_Calling: "&9%reporter% &7is radioing it in - &e%seconds%s"
   Report_Cut: "&aThe radio call never went out."
   Deferred: "&7A report on you came in while you were away."
```

### 1.2 Silent officers (stage S5)

The silent officer only runs with `Kill_Cop` in `Wanted.Witnesses.Crimes` (the two `Crimes` lines of 1.1) and the heat
ledger on. **A deployed file without those lines keeps 0.21.1's radio:** a dead cop's squad says `Officer down!` at once.

`copsncrooks/cops.yml`, under `Cops`, next to `Backup` (the whole block may be absent: the values shown are used):

```yaml
   Check_In:
      # false = a dead cop's squad radios at once (pre-0.21.2)
      Enable: true
      # a cop on patrol checks in this often (a cop in a chase checks in all the time); dispatch asks after this long
      Check_In_Seconds: 10
      # seconds after his last check-in that dispatch says Officer_Missing
      Silent_Seconds: 20
      # the killer seen by any cop within this many blocks of the scene: the kill counts (seen by that cop)
      Scene_Radius: 32.0
      # seconds the scene stays open before the trail goes cold (no stars)
      Scene_Seconds: 60
      # a cop spawned less than this many seconds ago can neither see the killer for the scene nor find it; 0 = off
      Spawn_Grace_Seconds: 3
      # the dead cop's squad was hunting the killer: the kill counts at Officer_Missing (x1.0, one witness)
      Pursuit_Presumed: true
      # the killer sees the countdown to Officer_Missing on his action bar
      Show_Timer: true
```

| Key | Absent |
|---|---|
| `Check_In.Enable` | `true` |
| `Check_In.Check_In_Seconds` | `10` (at least 1) |
| `Check_In.Silent_Seconds` | `20` (at least 1) |
| `Check_In.Scene_Radius` | `32.0` |
| `Check_In.Scene_Seconds` | `60` |
| `Check_In.Spawn_Grace_Seconds` | `3` (0 = off; R3, T-601) |
| `Check_In.Pursuit_Presumed` | `true` |
| `Check_In.Show_Timer` | `true` |

`copsncrooks/cops.yml`, under `Cops.Radio`: **no edit needed.** The code adds `Radio_Check`, `Officer_Missing`,
`Scene_Found`, `Scene_Suspect` and `Scene_Clear` to the priority lines even when the file has its own `Priority` list
(T-571), and their cooldown is 0 when `Cooldown_Ticks` does not name them (each is one announcement). All six 0.21.2
radio keys are therefore `Officer_Report` (1.1), `Radio_Check`, `Officer_Missing`, `Scene_Found`, `Scene_Suspect` and
`Scene_Clear`.

`copsncrooks/cop_radio_messages.yml` (and `_es`), under `Lines` (absent = the English shown; `%unit%` is the silent
cop, `%place%` his last check-in or the scene):

```yaml
   Radio_Check:
      - "Unit %unit%, status check."
   Officer_Missing:
      - "Unit %unit% is not answering. Units, check his last position near %place%."
   Scene_Found:
      - "On scene near %place%. Officer down, no sign of the shooter."
   Scene_Suspect:
      - "Suspect spotted near the scene, moving in!"
   Scene_Clear:
      - "Scene near %place% is clear. Suspect is gone."
```

`copsncrooks/wanted_messages.yml`, under `Witness` (absent = the English shown; `""` turns a line off):

```yaml
Witness:
   # %unit% is the cop, %seconds% the seconds until Officer_Missing (Check_In.Show_Timer)
   Silent: "&7%unit% &8misses check-in in &e%seconds%s"
   Scene_Heading: "&eUnits heading to %place%"
   Scene_Cold: "&7The trail went cold."
```

## 2. Behaviour that changed (no edit needed)

- **One cop kill is one crime.** A lethal blow on a cop no longer also books `Assault_Cop`: the assault is reported one
  tick after the hit and dropped when the cop is dead by then. One unseen shot from 0 stars used to give two stars
  (250-325 heat); it now gives the `Kill_Cop` weight alone (150, one star) (T-535). The hit cop's own sighting now
  counts before the assault is scored, so a hit he saw gets `Seen_By_Cop_Multiplier` (T-536).
- **`Kill_Cop` is booked when the cop really dies, at the body.** A hit the Defender's shield absorbs is never a kill
  (T-544). A death that names no killer (some Bartizan area damage) is blamed on the player who hit that cop in the
  last 5 seconds. A cop who dies to a fall, lava, fire or an NPC with no player hit in those 5 seconds books no crime, as
  before. With `Heat.Enable: false` nothing changes: the kill combo counts the kill.
- **`Kill_Civilian` is committed where the civilian died**, not where the killer stood (T-550). The witness range and
  the 911 tip now centre on the body.
- **Killing every cop never clears a star** (T-537). While the cops hunt a player the old `Wanted.Repeating_Timer`
  decay never takes his stars, even with every cop dead and no unit able to spawn. Only an escape (`EVASION`, which also
  starts the post-escape search), an arrest, a death or a command lowers them. `Evasion.Enable: false` keeps the old
  decay.
- **The search runs with no cop standing.** With every cop dead or walking home, the clock no longer turns off. It
  searches him at the squad's last-known spot at `No_Cop_Inside_Speed` inside the zone (`Outside_Zone_Speed` outside).
  At one star he needs 40 s inside the 40-block zone instead of 10 s. The first time in a chase it runs that way he is
  told in chat: `No units on you - leave the area` (`Hud.No_Cop_Search`, T-586).
  - It still holds, keeping its progress, for the **first response**: units on their way, or the rejoin grace, before
    any cop of the chase has stood in the world (up to 10 s past their ETA), and for a **hand-off in progress** (0.21.1
    cause E).
  - **Changed from 0.21.1:** after a wipe the breather and the ETA of the replacement units no longer freeze the clock;
    the search runs through them at the slow inside speed.
- **`Stand_Down` is heard after a wipe** (T-542). When a chase ends and no cop of the squad is left to say it, dispatch
  says it to the player. A squad that never put a cop on the ground still says nothing.

- **One crime, one star announcement** (T-583). A crime whose heat crosses two star thresholds at once (common with
  more witnesses, next bullet) used to announce each star: two subtitles, two chat cause cards (the first naming the
  lower tier) and a `Dispatch_Wanted` naming the lower level. It now announces once, at the level it ends at, and the
  squad is sized for that level from the start.
- **Beat cops never appear in view** (owner ruling R6, R2). A station's beat cop, the first ones and every replacement,
  never spawns at a station spot a player within `max(24, Visibility_Check_Distance)` has in front of him with a clear
  line. Since R3 (T-606) such a station is no longer left empty: once its cop is due (at once for the first ones,
  `Respawn_Seconds` after a loss) he spawns at a nearby spot no player has in view and walks to the station to begin
  his beat; with no such spot that second it tries again the next. No new key.
- **A fresh cop cannot close a scene** (R3, T-601). A cop spawned less than `Check_In.Spawn_Grace_Seconds` (3) ago
  neither counts the kill at a scene nor finds it; after that the usual rules apply. Absent key: 3; 0 restores R2.

- **More witnesses, more heat** (T-540). A crime's heat is now scaled by how many saw it: each eyewitness past the
  first adds `Per_Extra_Witness` (0.5) to the multiplier, up to `Max_Extra_Witnesses` (3). A civilian's 911 call now
  counts the caller plus every other civilian who saw the crime and is still alive when the call lands (it counted 1).
  From 0 stars, a Kill_Civilian three civilians saw is 200 heat instead of 100, and (stage S4, with the two `Crimes`
  lines) a Kill_Cop four cops saw is 450 (three stars) instead of 225. A crime outside `Wanted.Witnesses.Crimes` committed with
  no witness or one witness scores exactly as before; a held crime with none can lose the squad's x1.5 (next bullet).
  Safe alarms and store hold-ups commit with one witness, so they do not change. A crew mate's share of a crime
  carries the crime's witness count, as it already carried whether a cop saw it.
- **A held crime no longer gets a hidden x1.5** (T-538). For a crime in `Wanted.Witnesses.Crimes` (with
  `Witnesses.Enable` on), your squad merely having you in sight no longer counts as a cop who saw it. A 911 call is
  therefore always x1.0 plus its head count. Crimes outside the list keep the squad's sighting as before.
  - Stage S4 removed the gate's two shortcuts (the squad's sighting, a cop in view): a cop who saw it files a delayed,
    named report that carries the x1.5 and the head count (below).

- **Stage S4: a cop who saw a gated crime radios it in** (T-538, T-541). The witness gate no longer lets a held crime
  through at once because your squad has you in sight or a cop sees you. A cop eyewitness (he sees you, and he is
  fighting or chasing you, the crime is loud, or you are inside his 180-degree view) glows and radios it in after
  `Report_Seconds` (3): then it counts as seen by a cop (x1.5) with every witness still alive counted, and dispatch says
  `Officer_Report` about the crime spot. Kill every witness first and nothing counts. **Superseded 0.21.1 note:** a
  gated crime in front of a cop used to count in the same tick ([migration-0.21.1.md](./migration-0.21.1.md)); it now
  lands 3 s later (owner decision 11). With no cop eyewitness the civilians' 911 call runs as before; with nobody at
  all, `Nobody saw that.` and no heat. With the two `Crimes` lines added, a cop killed where no cop and no civilian saw
  it is a silent officer (stage S5, below).
- **Late reports name the crime spot** (T-543). A wanted start caused by a report that landed late (a cop's, a 911
  call, a deferred one) says `Dispatch_Wanted` about the crime spot and tips the squad there, not where you stand by
  then. This also applies to 911 calls, which used to name your live position.
- **Logging out forgives nothing** (T-551). A cop report with a witness alive, or a civilian's 911 call still running,
  when you log out lands at your next join (one tick after your data loads) within `Defer_Minutes`. 0.21.1 cancelled
  the 911 call. This applies at once, even without the `Crimes` lines. A restart in between forgives it.
  - The 911 call is handed over **at the logout**, with the caller and the watchers alive then; the caller goes back
    to idle and stops glowing, so nobody can stop that call any more. (PLAN.md 1.4 let the call run out its
    `Call_Seconds` first; the call would then land on the old, offline player object, or on a player who rejoined
    inside the call before his data loaded.)
- Death, a cuff or an arrest drops your pending cop reports and the reports deferred to your next join; being
  restrained when one would land (at its deadline, or at the join: cuffed during the call and jailed by the transit
  intake) drops it too. Going down keeps them.
- **One cop is one crime.** The dead cop of a `Kill_Cop` never witnesses it. A cop kill of a cop whose `Assault_Cop`
  report is pending replaces that report: a cop saw the kill, the kill keeps the assault's deadline; only a civilian
  saw the kill but another living cop saw the assault, the assault report stands and lands and the civilians are not
  asked (a cop report beats a 911 call, so killing the caller never forgives what a cop saw); only the dying cop saw
  the assault, its report closes and the civilians report the kill (a 911 call), or nobody does (`Nobody saw that.`,
  never `The radio call never went out.`).
- A report that lands while a cop has you in sight leaves the squad's live sighting alone (a late tip never drags a
  chase back to the crime spot); dispatch still names the crime spot. This holds for a 911 call too: it lands through
  the same path as a cop report, so a call that lands after you were cuffed during it is dropped as well.
- A report that raises no star (a cop-reported `Car_Theft` is 90 heat) says nothing on the radio, as a 911 call
  without a star already did in 0.21.1.

- **Stage S5: a cop killed unseen goes silent** (T-545, T-546; needs the two `Crimes` lines of 1.1). A cop killed where
  no cop saw the killer and no cop saw the body fall no longer radios `Officer down!`, requests backup or counts as a
  casualty at once. His slot in the squad and a beat cop's station replacement stay filled, and the evasion clock holds
  while he is all dispatch thinks is on you (`hold=silence`, bounded by `Silent_Seconds`). Dispatch says `Radio_Check`
  `Check_In_Seconds` after his last radio check-in and `Officer_Missing` `Silent_Seconds` after it, naming that spot.
  Then the casualty and the backup go out (the cold-trail hold released), the units spawn on your side of that spot and
  search it, a beat cop's station sends a second beat cop there, and the death event fires (the standoff casualty and
  the pursuit report's cops-down count it then, not at the death). The station's held replacement follows once
  `Patrol.Respawn_Seconds` (30) have passed since the death, at the station spot or, while a player has it in view,
  at a nearby spot nobody sees (owner rulings R6 and T-606), never at `Officer_Missing` itself. Both beat cops hold that spot for `Scene_Seconds`
  (60) from the moment dispatch learns of the death (`Officer_Missing`, or a 911 call that closed the silence first)
  before they walk back to the beat (T-579); a replacement that spawns after that stays on its beat.
  - Only a cop who held a slot of the squad holds it while he is silent: a walker the squad sent home (a star drop, a
    backup run out) or one stranded past `Pursuit.Max_Distance` holds none, so the squad refills and calls walkers back
    for a death it saw as before (T-580). A silent Commander still leads: a Commander the squad sent home keeps walking
    (T-581).
  - His squad was hunting you: the kill counts at `Officer_Missing`, x1.0 and one witness (150 heat from 0 stars,
    `Pursuit_Presumed`). Otherwise a **scene** opens there: a cop who sees you within `Scene_Radius` of it in
    `Scene_Seconds` counts the kill as seen by a cop (225 heat from 0 stars), else it goes cold with no stars.
  - A cop who saw the body fall (not you) radios `Officer down!` at once, as before, and a scene opens at the body.
  - A civilian's 911 call about the kill that lands first closes that kill's silence (one commit, no
    `Officer_Missing`); one landing after the kill was presumed or counted at a scene commits nothing.
  - A cop who saw the assault on the officer keeps the radio live (no silence): the `Assault_Cop` report stands, one
    crime. A squad gone between the death and `Officer_Missing` presumes nothing: a scene opens.
  - Leaving, dying, a cuff or an arrest forgets your silences and scenes. A restart forgets them too.
  - `Check_In.Enable: false`, or `Kill_Cop` not in `Witnesses.Crimes`, keeps the 0.21.1 radio.

## 3. Debug lines

- (S4) `REPORT open|land|cut|drop|defer|stand <player> crime=<id> reporter=<name> witnesses=<n> at=x,y,z`, with
  ` reason=<death|cuffed|arrested|restrained|refused|superseded|quit>` on a drop or a cut at quit (`refused`: the
  recommit was cancelled, nothing committed) and ` deferred=true` on a landing at a join. A 911 call landing live logs
  `REPORT land|drop <player> ... reporter=CIVILIAN witnesses=<viewers>`. `REPORT stand ... crime=Assault_Cop ...
  reason=unseen_kill`: a cop kill no cop saw left the assault report another cop saw in place. `WITNESS <player> crime=<id> callers=<n>` now comes from the report when no cop saw it.
- (S4, civilians) `CALL_DEFER <player> crime=<id> viewers=<n>`: a 911 call handed over at his logout.

- `CALL_IN <player> crime=<id> caller=<name>` (civilians) now ends with `viewers=<n>`, the head count the crime is
  committed with.
- (S5) `CHECKIN silent|radio_check|missing <unit> last=x,y,z age=<s>`: a cop killed unseen, the radio check and the
  Officer_Missing of his silence (`last` his last check-in, `age` the seconds since it); `CHECKIN close ... reason=911`
  when a 911 call closed it, `CHECKIN drop ... reason=<quit|death|cuffed|arrested>` when it was forgotten.
- (S5) `SCENE open|found|seen|clear <player> at=x,y,z` (`SCENE close ... reason=911`); a scene sighting or a presumed
  kill lands as `REPORT land <player> crime=Kill_Cop reporter=SCENE|DISPATCH ...`.
- (S5) `EVASION <player> hold=silence`: the clock waits for dispatch to notice a silent officer.
- (S5) `PATROL station=<name> respond=<n> at=x,y,z`: a beat cop's station answers his Officer_Missing; `<n>` counts
  the beat mate sent now and the replacement still owed (it spawns later, logging its own `PATROL station=<name>
  cops=<n>`).
- `EVASION <player> hold=enroute` now means the first response only; `hold=handoff` is new (a hand-off bias or a leashed
  walker past `Pursuit.Max_Distance`).
