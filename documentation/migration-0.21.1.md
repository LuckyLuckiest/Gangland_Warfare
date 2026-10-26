# Migrating a server from 0.21.0 to Gangland 0.21.1

[← Back to Documentation Index](./README.md)

0.21.1 changes how an ignored brandish warning ends. The player is told the cause in chat, and the first star arrives at
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
