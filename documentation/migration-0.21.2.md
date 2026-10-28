# Migrating a server from 0.21.1 to Gangland 0.21.2

[← Back to Documentation Index](./README.md)

0.21.2 makes cop crimes and the end of a chase follow what the cops actually saw. A new install gets everything from the
shipped files. **An existing server keeps its old files** (`copsncrooks/wanted.yml` and the others), and the code
never rewrites them, so every new key below is read with the value shown when it is absent. Edit the files by hand only
to change those values.

This file grows with each 0.21.2 stage. It covers stages S1, S2 and S3 so far.

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

- Absent: 0.5 and 3. Set `Per_Extra_Witness: 0` to score every crime as 0.21.1 did whatever the head count.

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
- In 0.21.2 S3 only `Reported` can appear: `Seen`, `Missing` and `Scene` are shown once the cop reports, the silent
  officer and the scene of the later stages land.

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
  At one star he needs 40 s inside the 40-block zone instead of 10 s.
  - It still holds, keeping its progress, for the **first response**: units on their way, or the rejoin grace, before
    any cop of the chase has stood in the world (up to 10 s past their ETA), and for a **hand-off in progress** (0.21.1
    cause E).
  - **Changed from 0.21.1:** after a wipe the breather and the ETA of the replacement units no longer freeze the clock;
    the search runs through them at the slow inside speed.
- **`Stand_Down` is heard after a wipe** (T-542). When a chase ends and no cop of the squad is left to say it, dispatch
  says it to the player. A squad that never put a cop on the ground still says nothing.

- **More witnesses, more heat** (T-540). A crime's heat is now scaled by how many saw it: each eyewitness past the
  first adds `Per_Extra_Witness` (0.5) to the multiplier, up to `Max_Extra_Witnesses` (3). A civilian's 911 call now
  counts the caller plus every other civilian who saw the crime and is still alive when the call lands (it counted 1).
  From 0 stars, a Kill_Civilian three civilians saw is 200 heat instead of 100. Once cop reports land (stage S4, not
  in this build yet), a Kill_Cop four cops saw will be 450 (three stars) instead of 225; until then a cop kill or
  assault commits with no witness count and scores as before. A crime outside `Wanted.Witnesses.Crimes` committed with
  no witness or one witness scores exactly as before; a held crime with none can lose the squad's x1.5 (next bullet).
  Safe alarms and store hold-ups commit with one witness, so they do not change. A crew mate's share of a crime
  carries the crime's witness count, as it already carried whether a cop saw it.
- **A held crime no longer gets a hidden x1.5** (T-538). For a crime in `Wanted.Witnesses.Crimes` (with
  `Witnesses.Enable` on), your squad merely having you in sight no longer counts as a cop who saw it. A 911 call is
  therefore always x1.0 plus its head count. Crimes outside the list keep the squad's sighting as before.
  - **Until stage S4 (not in this build):** the witness gate still lets a held crime through at once when the chasing
    squad has you in sight or any cop sees you, with no report and no witness count. One your squad has in sight now
    scores x1.0 where 0.21.1 gave x1.5: a Kill_Civilian in front of the squad is 100 heat, was 150 (one only another
    cop sees already scored x1.0). S4 replaces both shortcuts with a cop's delayed, named report that carries the x1.5
    and the head count (PLAN.md, staging S4).

## 3. Debug lines

- `CALL_IN <player> crime=<id> caller=<name>` (civilians) now ends with `viewers=<n>`, the head count the crime is
  committed with.
- `EVASION <player> hold=enroute` now means the first response only; `hold=handoff` is new (a hand-off bias or a leashed
  walker past `Pursuit.Max_Distance`).
