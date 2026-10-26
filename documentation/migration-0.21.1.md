# Migrating a server from 0.21.0 to Gangland 0.21.1

[← Back to Documentation Index](./README.md)

0.21.1 changes how an ignored brandish warning ends (sections 1-3) and how a civilian witness calls 911 (section 4). The player is told the cause in chat, and the first star arrives at
once. The star title no longer repeats the whole description. A new install gets all of this from the shipped
`wanted.yml`. **An existing server keeps its old `wanted.yml`**, and the code cannot rewrite it, so two values must be
edited by hand to get the new behaviour.

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

## 3. Text keys

`copsncrooks/wanted_messages.yml` gains `Patrol.Ignored` (`"&cBeat cop: &fYou were warned. &7Cause: &c%cause%"`) and
`Patrol.Lost_Sight`. A file without them uses the same text from the code.

## 4. Witnesses and 911 calls

0.21.1 makes a 911 call something you can see and stop on purpose: the witness looks at you, says a line, takes out a
phone, and your action bar counts down. A witness must be facing a silent crime. Killing a civilian waits for a witness
too. A new install gets all of it from the shipped files. **An existing server keeps its old `civilians.yml`,
`civilian_messages.yml` and `wanted.yml`**, and the code does not rewrite them (owner ruling: manual migration), so
edit them by hand.

### 4.1 `copsncrooks/wanted.yml`

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

### 4.2 `npc/civilians.yml`

Add inside the `Witness:` block:

```yaml
Witness:
   # ... your existing keys ...
   View_Angle: 180.0
   Loud_Crimes:
      - Kill_Civilian
   Loud_Shot_Seconds: 3
   Notice_Seconds: 1
   Notice_Particle: "ANGRY_VILLAGER"
   Phone_Item: "CLOCK"
   Intimidate_Range: 4.0
   Scared_Cooldown_Seconds: 60
```

A file without these keys already gets these values from the code, so this edit only documents them; edit a value to
change it. `View_Angle: 360.0` restores the 0.20.0 rule (civilians see all round). `Loud_Shot_Seconds: 0` stops
gunshots counting as noise. `Phone_Item: ""` and `Notice_Particle: ""` turn those off. `Intimidate_Range: 0` turns off
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

### 4.3 `npc/civilian_messages.yml` (and `civilian_messages_es.yml`)

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

### 4.4 `copsncrooks/wanted_messages.yml`

`Hud.Card.Reported: "%crime% (reported by a witness)"` is new; a file without it uses that text from the code.

### 4.5 Behaviour that changed

- A silent crime (car theft, a punch) is reported only by a civilian whose view (`View_Angle`) covers you. A loud one
  (`Loud_Crimes`, or any crime within `Loud_Shot_Seconds` of your own gunshot) makes every civilian in range turn to it
  first, visibly.
- Notice, dialling, stopped and connected each show and say something (see
  [wanted-bounty.md](./features/wanted-bounty.md#witnesses-and-911-calls-0200)). The countdown bar replaces the one-off
  "A witness is calling 911!" line.
- The reported player can right-click the caller within `Intimidate_Range` to stop the call, no weapon needed. A
  stopped caller (hit, scared or intimidated) does not call about you again for `Scared_Cooldown_Seconds`.
- `Caller_911` is a dispatch line now (`Dispatch_Format`), so it is heard even when the call has just made you wanted
  and no cop has spawned yet (T-345). Its `%crime%` is the crime's display name (`Crimes.<id>` of
  `wanted_messages.yml`), no longer the raw id (`Car_Theft`).
- A star raised by a crime a witness reported says so in the cause: "Car theft (reported by a witness)". A store
  hold-up's silent alarm counts as a witness report too.
