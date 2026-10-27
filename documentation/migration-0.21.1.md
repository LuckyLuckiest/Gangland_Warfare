# Migrating a server from 0.21.0 to Gangland 0.21.1

[← Back to Documentation Index](./README.md)

0.21.1 changes how an ignored brandish warning ends: the player is told the cause in chat, and the first star arrives at
once. The star title no longer repeats the whole description (sections 1, 2 and 4). It also changes where the hand-off
units search and how often the "en route" dispatch line repeats (section 3), and how a civilian witness calls 911
(section 5). A new install gets all of this from the shipped files. **An existing server keeps its old files**
(`wanted.yml`, `cops.yml`, `civilians.yml`, `civilian_messages.yml`, `cop_radio_messages.yml`), and the code cannot
rewrite them, so the values in sections 1, 3 and 5 must be edited by hand to get the new behaviour.

## 1. Edit `copsncrooks/wanted.yml`

```yaml
Wanted:
   Heat:
      Crimes:
         # was 25. 67 x Seen_By_Cop_Multiplier (1.5) = 100.5, just over the first Star_Thresholds entry (100)
         Brandish_Near_Cop: 67
   Hud:
      Title:
         Gain:
            # was "%stars% %card%". The card (cause, tier, stance) now goes to chat as a message
            Subtitle: "%stars%"
```

- Without the first edit an ignored warning still sends the cause line but adds only 37.5 hidden heat, so the star comes
  after about three ignored warnings. The `Patrol.Ignored` line says "Cause", not "Wanted for", so it stays true either way.
- Without the second edit the gain subtitle keeps showing the whole card. A subtitle that still contains `%card%` is
  never repeated in chat.
- If you changed either value on purpose, keep your value: both are plain settings.

## 2. Behaviour that changed

- **Warning cooldown** (`Cops.Warning.Repeat_Seconds`, 30) now counts from the verdict of the last warning, not from its
  start, and is shared by every beat cop.
- **Ignored warning**: `Patrol.Ignored` (new placeholder `%cause%`) names the crime; leaving the cop's view when time is up
  sends `Patrol.Lost_Sight` and costs nothing.
- **Crime subtitles**: every title cue takes `%cause%` and the chat card is sent whenever `Star_Card` is on and the cue
  does not carry `%card%`.

## 3. Edit `copsncrooks/cops.yml`

### 3.1 Required: `Dispatch_En_Route` cooldown

```yaml
Cops:
   Radio:
      Cooldown_Ticks:
         # was 0. Said once per squad per 30 s, so refilling a leashed or lost unit does not sound like a new dispatch
         Dispatch_En_Route: 600
```

0.21.0 shipped an explicit `Dispatch_En_Route: 0`, and a key in the file overrides the code default, so without this
edit every leash or loss refill still sounds like a new dispatch. If you changed it on purpose, keep your value.

### 3.2 Optional: new keys

0.21.1 adds five keys. The code default applies when one is missing, so an old `cops.yml` works unchanged; add them to
see and tune them:

```yaml
Cops:
   Handoff:
      # new in 0.21.1: blocks past the squad's last-known spot, along the heading it saw him take, that the units sent
      # after the hand-off head for and search. At most 0.75 x Pursuit.Max_Distance long; a negative value counts as 0.
      Lead_Blocks: 24.0
      # new in 0.21.1: how far from his live spot toward that lead the hand-off units spawn, 0.0-1.0 (0.5 = midway,
      # 0 = around him ahead of the heading as before). Never in his view; their goal stays the lead.
      Spawn_Between: 0.5
   Return:
      # ... your existing Max_Ticks and Station_Arrival_Distance ...
      # new in 0.21.1: seconds a player within 24 blocks watching a returning cop keeps it past Max_Ticks (0 = no cap)
      Watched_Cap_Seconds: 60
   Patrol:
      # new in 0.21.1: seconds a station waits after losing a beat cop (killed or lost) before a replacement walks out.
      Respawn_Seconds: 30
   Radio:
      Cooldown_Ticks:
         # new in 0.21.1: cooldown of the Route_Search line (300 = 15 s)
         Route_Search: 300
```

After a hand-off ("Lost him heading X. Units ahead, pick him up.") the new units no longer search the spot where he was
lost: they walk on along the heading the squad saw him take (stopping short of a wall, on dry ground there; over water or lava, or into unloaded chunks, they search the last-known spot) and
search there. Neither the heading nor that spot uses his live position. `Lead_Blocks: 0` keeps the old behaviour of searching the last-known spot.

Those units now spawn between where he really is and that spot (`Spawn_Between`), so they do not start a long walk
from far ahead of a suspect who doubled back; spawning near him is what every unit does, and their goal is still the
spot. `Spawn_Between: 0` keeps the 0.16 spawn ahead of the heading. A returning cop that a player within 24 blocks
keeps watching now goes `Watched_Cap_Seconds` after `Return.Max_Ticks` anyway; `0` keeps it while watched, as 0.21.1
did before this key.

## 4. Text keys

`copsncrooks/wanted_messages.yml` gains `Patrol.Ignored` (`"&cBeat cop: &fYou were warned. &7Cause: &c%cause%"`) and
`Patrol.Lost_Sight`. A file without them uses the same text from the code.

`copsncrooks/cop_radio_messages.yml` (and `cop_radio_messages_es.yml`) gains `Lines.Route_Search`, said instead of
`Route` when a cop starts a route while nobody sees him, so the line does not claim to know where he is. A file without
it uses the English text from the code; the Spanish lines ship in the jar's `copsncrooks/cop_radio_messages_es.yml`.

## 5. Witnesses and 911 calls

0.21.1 makes a 911 call something you can see and stop on purpose: the witness looks at you, says a line, takes out a
phone, and your action bar counts down. A witness must be facing a silent crime. Killing a civilian waits for a witness
too. A new install gets all of it from the shipped files. **An existing server keeps its old `civilians.yml`,
`civilian_messages.yml` and `wanted.yml`**, and the code does not rewrite them (owner ruling: manual migration), so
edit them by hand.

### 5.1 `copsncrooks/wanted.yml`

```yaml
Wanted:
   Witnesses:
      Crimes:
         - Car_Theft
         - Assault_Civilian
         # new: an unseen kill of a civilian waits for a witness
         - Kill_Civilian
```

Without this edit an unseen kill still stars at once, as in 0.21.0 (an existing file lists its crimes explicitly, so
the new default does not apply). `Store_Robbery` is deliberately not listed: the shop's silent alarm already reports it
with a witness.

### 5.2 `npc/civilians.yml`

Add inside the `Witness:` block:

```yaml
Witness:
   # ... your existing keys ...
   View_Angle: 180.0
   Loud_Crimes: []
   Loud_Shot_Seconds: 3
   Loud_Weapon_Types:
      - GUN
      - THROWABLE
   Notice_Seconds: 1
   Notice_Particle: "ANGRY_VILLAGER"
   Phone_Item: "CLOCK"
   Intimidate_Range: 4.0
   Scared_Cooldown_Seconds: 60
```

A file without these keys already gets these values from the code, so this edit only documents them; edit a value to
change it. `View_Angle: 360.0` restores the 0.20.0 rule (civilians see all round). `Loud_Shot_Seconds: 0` stops
shots counting as noise (a throwable is loud from the throw until `Loud_Shot_Seconds` after its Bartizan `Fuse_Time`); `Loud_Weapon_Types` names the Bartizan weapon categories whose shot is noise (a melee swing is
not). List a crime id under `Loud_Crimes` (for example `Kill_Civilian`) to make it loud whatever the weapon. `Phone_Item: ""` and `Notice_Particle: ""` turn those off. `Intimidate_Range: 0` turns off
the right-click.

Add to the `Shouts.Priority` list:

```yaml
Shouts:
   Priority:
      # ... your existing entries ...
      - "Witness_Start"
      - "Witness_Scared"
      - "Witness_Landed"
```

**This one matters:** a file that has its own `Priority` list does not get the code default, and without these entries
the 911 lines are subject to the shout gaps, so one can be dropped when another shout was just heard.

### 5.3 `npc/civilian_messages.yml` (and `civilian_messages_es.yml`)

```yaml
Witness_Calling: "&c911 &f%caller% &7is calling - &e%seconds%s"
Witness_Stopped: "&aCall stopped."
Witness_Unknown_Place: "here"
Shouts:
   Witness_Format: "&c[911] &f%unit%&7: &f%line%"
   Lines:
      Witness_Start:
         - "Hey! I saw that! I'm calling the cops!"
      Witness_Scared:
         - "Okay, okay! I'm hanging up!"
      Witness_Landed:
         - "Yes, police? He's right here, near %place%!"
```

The new keys fall back to this English text when missing. **`Witness_Calling` does not**: your file has the old line,
which still works but shows no caller and no countdown, so replace it. The Spanish file ships its own text; copy it from
the jar's `npc/civilian_messages_es.yml`.

### 5.4 `copsncrooks/wanted_messages.yml`

`Hud.Card.Reported: "%crime% (reported by a witness)"` is new; a file without it uses that text from the code.

### 5.4a `copsncrooks/cop_radio_messages.yml` (and `cop_radio_messages_es.yml`)

`Caller_911` now plays through `Dispatch_Format` (`[DISPATCH] ...`), so drop the old `Dispatch: ` lead-in or players see
it twice:

```yaml
   Caller_911:
      - "911 caller reports %crime% near %place%. Suspect: %suspect%."
```

The Spanish file: `- "Llamada al 911 reporta %crime% cerca de %place%. Sospechoso: %suspect%."`.

### 5.5 Behaviour that changed

- A silent crime (car theft, a punch) is reported only by a civilian whose view (`View_Angle`) covers you. A loud one
  (any crime within `Loud_Shot_Seconds` of your own gunshot, or from a grenade's throw to `Loud_Shot_Seconds` after its blast, or one in `Loud_Crimes`, none by default)
  makes every civilian in range turn to it
  first, visibly.
- Notice, dialling, stopped and connected each show and say something (see
  [wanted-bounty.md](./features/wanted-bounty.md#witnesses-and-911-calls-0200)). The countdown bar replaces the one-off
  "A witness is calling 911!" line.
- The reported player can right-click the caller within `Intimidate_Range` to stop the call, no weapon needed. A
  stopped caller (hit, scared or intimidated) does not call 911 again, about you or anyone, for
  `Scared_Cooldown_Seconds`.
- `Caller_911` is a dispatch line now (`Dispatch_Format`), so it is heard even when the call has just made you wanted
  and no cop has spawned yet (T-345). Its `%crime%` is the crime's display name (`Crimes.<id>` of
  `wanted_messages.yml`), no longer the raw id (`Car_Theft`).
- A star raised by a civilian's 911 call says so on the chat card: "Car theft (reported by a witness)". The title's
  `%cause%` stays the plain crime name. A store hold-up's silent alarm or a safe alarm is not labelled.
- A gated crime (now including `Kill_Civilian`) committed in front of a cop counts at once: the gate checks whether any
  live squad or beat cop picks you up within `Alert_Range` before it waits for a witness.
