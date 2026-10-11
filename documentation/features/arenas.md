# Fight Arenas

[← Gang Fights](./fights.md) | [Back to Index](../README.md)

---

## Overview

An **arena** is a box of the world with two spawn sets (side A and side B) and an exit. A [gang fight](./fights.md) is
held inside one arena. Staff build arenas in game with `/glw arena`, and every arena is saved to `fight/arenas.yml`.

Arenas are staff-only: every `/glw arena` verb is under the `gangland.arena.admin` node, which is op by default.

> **Place arenas outside turfs.** Arenas are per server and cannot see turfs. A fight inside a turf does not know about
> the turf, so keep every arena outside every turf. `/glw arena finish` prints this reminder when it saves an arena.
> The module does not check it for you.

---

## Build an Arena

Build a draft, then check and save it. You need to stand in the world where the arena goes; the draft remembers each
point at the spot you ran the command from.

| Step | Command | What it does |
|------|---------|--------------|
| 1 | `/glw arena create <name>` | Start a new draft. Names are case-insensitive and must be unused. |
| 2 | `/glw arena corner` | Set one corner of the box where you stand. Run it **twice**, once for each opposite corner. |
| 3 | `/glw arena spawn a` | Add a spawn for side A where you stand. Stand **inside** the box. Repeat for each spawn. |
| 4 | `/glw arena spawn b` | Add a spawn for side B the same way. |
| 5 | `/glw arena exit` | Set where fighters return after a fight. Stand where they should land. |
| 6 | `/glw arena size` | Show the draft's box size, to check it before you save. |
| 7 | `/glw arena finish` | Check the draft and save it. |

Use `/glw arena show [name]` to draw the box as particles for `Arenas.Outline_Seconds` (10) seconds.

### What `finish` checks

`finish` refuses a draft and says why. Each refusal is a line from `fight/fight_messages.yml`:

| Refusal | Fix |
|---------|-----|
| `Finish_No_Corners`: corners not set | Run `/glw arena corner` twice. |
| `Finish_Too_Small`: the box has fewer blocks than `Arenas.Min_Arena_Volume` (64) | Make the box bigger. |
| `Finish_No_Spawn`: side A or B has no spawn | Stand inside the box and run `/glw arena spawn a` or `b`. |
| `Finish_Spawn_Outside`: a spawn of side A or B is outside the box | Every spawn must be inside the box; add the spawns again from inside it. |
| `Finish_No_Exit`: no exit set | Run `/glw arena exit`. |
| `Finish_World_Missing`: the box's world is not loaded | Load the world and run `finish` again. |
| `Finish_Exit_World`: the exit's world is not loaded | Load that world and run `finish` again. |
| `Finish_Overlap`: the box overlaps another arena | Move the box so it does not touch the other arena. |
| `Finish_Name_Taken`: an arena with this name exists | Use another name. |

When the draft passes, it is saved and opened for fights straight away:

> Arena `<name>` saved (`<a>` v `<b>` spawns). Place arenas outside turfs.

---

## Manage Arenas

| Command | What it does |
|---------|--------------|
| `/glw arena list` | List the arenas. |
| `/glw arena tp <name>` | Teleport to the arena's exit. |
| `/glw arena show [name]` | Draw the arena's box as particles. |
| `/glw arena on <name>` | Open the arena for fights. |
| `/glw arena off <name>` | Close the arena. A closed arena is never offered for a fight. |
| `/glw arena delete <name>` | Delete the arena. |
| `/glw arena reload` | Re-read `fight/arenas.yml` after a hand edit. |

---

## Hand Editing `fight/arenas.yml`

The file is block style. Each arena is a key under `Arenas`. Keys are `Capitalized` as shown. Edit the file only while
the server is stopped, or edit it and run `/glw arena reload`.

```yaml
Arenas:
   Pit:
      World: world
      Enabled: true
      Box:
         Min:
            X: 0
            Y: 60
            Z: 0
         Max:
            X: 9
            Y: 69
            Z: 9
      Spawns:
         A:
            - X: 1.5
              Y: 60.0
              Z: 1.5
              Yaw: 0.0
              Pitch: 0.0
         B:
            - X: 8.5
              Y: 60.0
              Z: 8.5
              Yaw: 180.0
              Pitch: 0.0
      Exit:
         World: world
         X: 50.5
         Y: 64.0
         Z: 50.5
         Yaw: 0.0
         Pitch: 0.0
```

- `Enabled` (default `true` when missing) is the open or closed state that `on` and `off` change.
- `Box` is two corners, `Min` and `Max`. `Spawns` has an `A` list and a `B` list.
- An entry that does not parse is skipped with a console warning. The other arenas still load.

---

## Rules of Thumb

- Keep every arena outside every turf (see the note at the top).
- Give each side at least one spawn (`finish` refuses an arena without one).
- Closing an arena with `off` keeps it saved; it is simply not offered for fights.

---

## Related

- [Gang Fights](./fights.md): how a fight uses an arena, the guards inside it, and every `fight_rules.yml` key.
