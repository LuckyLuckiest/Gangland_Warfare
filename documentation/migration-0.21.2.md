# Migrating a server from 0.21.1 to Gangland 0.21.2

[← Back to Documentation Index](./README.md)

0.21.2 makes cop crimes and the end of a chase follow what the cops actually saw. A new install gets everything from the
shipped files. **An existing server keeps its old files** (`copsncrooks/wanted.yml` and the others), and the code
never rewrites them, so every new key below is read with the value shown when it is absent. Edit the files by hand only
to change those values.

This file grows with each 0.21.2 stage. It covers stages S1 and S2 so far.

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
  - It still holds, keeping its progress, for the **first response**: units on their way before any cop of the chase has
    stood in the world (up to 10 s past their ETA), and for a **hand-off in progress** (0.21.1 cause E).
  - **Changed from 0.21.1:** after a wipe the breather and the ETA of the replacement units no longer freeze the clock;
    the search runs through them at the slow inside speed.
- **`Stand_Down` is heard after a wipe** (T-542). When a chase ends and no cop of the squad is left to say it, dispatch
  says it to the player. A squad that never put a cop on the ground still says nothing.

## 3. Debug lines

- `EVASION <player> hold=enroute` now means the first response only; `hold=handoff` is new (a hand-off bias or a leashed
  walker past `Pursuit.Max_Distance`).
