# Migrating a server from 0.21.2 to Gangland 0.21.3

[← Back to Documentation Index](./README.md)

0.21.3 fixes the playtest findings: hostile civilians stop calling 911, the Quartermaster walks back, paces and
respawns, command usage becomes short and consistent, and Keystone 1.15.5 fixes the empty first tab. A new install gets
everything from the shipped files. **An existing server keeps its old files**, and the code never rewrites them.

## 1. Swap the jars

- Replace `plugins/Keystone-1.15.4.jar` with **`Keystone-1.15.5.jar`**.
- Replace the Gangland core jar and every module jar in `plugins/Gangland_Warfare/modules/` with the 0.21.3 builds.
  Update `gangland-civilians`, `gangland-turf` and `cops-n-crooks` together.
- `Host_Api` stays 2.7. No other plugin changes.

## 2. Hand edit: the Quartermaster wanders (`npc/civilians.yml`)

The shipped `quartermaster` type now paces. Your existing file still has it standing still. Under
`Types` -> `quartermaster` -> `AI`, set:

```yaml
      AI:
         Wander:
            Enabled: true
            Range: 4
```

- Without the edit he does not pace, but still walks home when pushed past the hold radius and still respawns.
- Keep `Range` at or below `Garrison.Hold_Radius` (6 by default). A hold radius under about 4 makes him twitch between
  pacing and being recalled.

## 3. New key (optional): the respawn delay (`turf/turf_npcs.yml`)

```yaml
Powerup_Npc:
   # Seconds a KILLED Quartermaster stays down before it respawns at its anchor. 0 = it never comes back (until a
   # restart). A Quartermaster lost to a chunk unload always returns when the chunk loads. Minimum 0.
   Respawn_Seconds: 120
```

- Absent: 120. The timer is memory only; a restart respawns every Quartermaster.

## 4. Removed text keys (optional clean-up)

Usage replies now use the standard `Missing Arguments` message. These keys are no longer read and can be deleted:

- `copsncrooks/commands.yml` and `copsncrooks/commands_es.yml`: `Item.Usage` (`Item.Unknown` is kept).
- `gang/gang_messages.yml`: `Staff.Usage`.

Leaving them is harmless. The `commands.json` help rows ship inside each module jar and need no edit.

## 5. Behaviour that changed

- A civilian type with `Hostile: true` never calls 911, never counts as a witness and never turns to face a loud crime.
  If you relied on a hostile guard raising the alarm, set `Hostile: false` on that type or group.
- A civilian sent to flee by a witness call obeys `AI.Flee_On_Damage.Enabled`; with it `false` the civilian returns to
  idle.
- Bare commands (`/glw option`, `filter`, `gang fight`, `cop setup`, ...) answer `Missing Arguments` with a short usage.
- On a Brigadier client (Paper), `/glw ` plus tab lists the sub-commands again. A half-typed alias that matches no
  sub-command completes nothing; type it fully.
