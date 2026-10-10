# Turf

[← Jail & Detainment](./jail-detainment.md) | [Back to Index](../README.md)

---

## Overview

A **turf** is an admin-drawn named area of the world that a gang can own and earn from. Owners collect income from it
every interval, rivals capture it by standing inside and filling a bar, and every player can read the map to see who
holds what. From 0.17.0 the map (`/glw map`), the action-bar turf line and the placeholders show the same facts, so a
player never has to guess why a turf can or cannot be attacked.

Vocabulary used in every message, hover and line of this page:

| Term | Meaning |
|---|---|
| **Turf** | An admin-drawn named area (a cuboid of blocks), stored per world. |
| **Capture** (capturing) | Filling the capture bar to take a turf. |
| **Under attack** | A rival is capturing an owned turf. Shown as *italic* on the map. |
| **Shielded** | The turf cannot be attacked right now, and the reason is always shown (for example `shielded: just captured, attackable in 12m`). Shown as underlined on the map. |
| **Guards** | The NPCs bought for a turf (the defender stock set by `/glw turf garrison`). |
| **Quartermaster** | The turf's shop NPC (the powerup catalogue, `/glw turf buff`, `/glw turf powerupnpc`). |

Turf is provided by the `gangland-turf` runtime module. Turf admin rights come from the `gangland.turf.admin` node.

---

## Drawing Turf

Turf is drawn with the turf wand. Left-click a block for corner 1 (`pos1`), right-click for corner 2 (`pos2`).

```
/glw turf wand                 Receive the wand (CARROT_ON_A_STICK by default; Wand_Item_Type in turf_settings.yml)
/glw turf pos1                 Set corner 1 to your current location
/glw turf pos2                 Set corner 2 to your current location
/glw turf create <displayName> Create a turf from your selection. The id is assigned automatically.
/glw turf rename <name>        Rename your active-selection turf (or the turf you stand in)
```

- **Snap to chunks.** A new turf's corners snap outward to whole chunks (16 x 16 blocks) when
  `Create.Snap_To_Chunks` is `true` in `turf_rules.yml` (the default). The map then shows no partial cells. The
  create message prints the snapped bounds when they changed. Existing turfs are never changed.
- **Size limits.** Each side of a new turf, measured after snapping, must be at least `Create.Min_Side_Blocks` (16)
  and at most `Create.Max_Side_Blocks` (512) blocks.
- **Overlap.** Turfs never overlap. The overlap check runs on the snapped area.
- **One world.** A corner in another world than your selection is refused; clear the other corner first.
- **Names.** A name must be unique (ignoring case and colour codes). `create` and `rename` refuse a name already in use.
- **Admins.** `gangland.turf.admin` grants every turf admin verb (create, delete, setowner, income, garrison, buff,
  powerupnpc, rename, tp, select, show, status). Players without it cannot run them.

---

## Capturing

A rival captures a turf by standing inside it. The capture bar fills while attackers are inside and defenders inside push
it back; the turf changes hands when the bar completes. Current W1 rules, set in `turf/turf_settings.yml` under `Capture:`:

| Key | Default | Meaning |
|---|---|---|
| `Capture.Cooldown_Minutes` | `15` | After a capture completes, the turf cannot be contested again for this long. |
| `Capture.Post_Logoff_Protection_Minutes` | `10` | After the last member of the owning gang logs off, its turfs stay shielded for this long. |

The map, the action bar, the info card and the enter message all give the same one-sentence reason. The sentences are
in `turf/turf_messages.yml` under `Reason:`:

| Key | Default sentence | When |
|---|---|---|
| `Reason.Attackable` | `attackable` | Nothing stops you from capturing the turf now. |
| `Reason.Cooldown` | `shielded: just captured, attackable in %time%` | The turf was captured recently. `%time%` is the time left. |
| `Reason.Owners_Active` | `shielded: %gang% were online in the last %grace%` | The owners were online recently. `%grace%` is `Post_Logoff_Protection_Minutes`. |

> These are the W1-build rules. W2 ("one rule per fight") replaces them, including who may attack and when.

`/glw turf info [id|name]` shows the turf card, which includes a `Capture:` line with the reason. Tab completion offers
every turf id and name. While a capture runs, a boss bar shows the attacking gang, the turf, the attackers against the
defenders and the time left (or `stalled` when the capture is not moving forward).

---

## Map

`/glw map` prints a grid of the turfs around you, centred on you, north up. Every gang in the window has a letter, and
the colour of the letter says how it relates to you.

### Sample

Viewer in Vipers (3 of 4 turfs), Kings allied, Rats rival, all offline for longer than the protection window
(attackable); Saints rival with a member online (shielded, underlined). Kings' Docks East is under attack by Saints and
renders *italic*.

```
Map - Turfs 3 - north is up - + you
--RRRRRRRR-#######-----------------------
--RRRRRRRR-#######------KKKKKKKKK--------
--RRRRRRRR--------------KKKKKKKKK-SSSSSS-
--RRRRRRRR----VVVVVV+VV-KKKKKKKKK-SSSSSS-
--------------VVVVVVVVV-KKKKKKKKK-SSSSSS-
--------------VVVVVVVVV-----------SSSSSS-
--------------VVVVVVVVV--#######---------
V Vipers (you)  K Kings (ally)  R Rats (rival)  +1 more
# unclaimed  - none  italic attacked  underlined shielded
```

The `+` is your cell. Every row is 41 cells wide.

### Glyphs, colours and styles

| Cell | Glyph | Colour |
|---|---|---|
| You | `+` | white |
| Your gang's turf | the gang's letter | green |
| Ally's turf | the gang's letter | aqua |
| Rival's turf | the gang's letter | red |
| Owned turf, seen by a player without a gang | the gang's letter | yellow |
| Unclaimed turf | `#` | gray |
| No turf | `-` | dark gray |

- **Letters.** A gang's letter is the first `A-Z` of its colour-stripped display name (never `I`). A clash takes the next
  letter of its name, then the first free letter. Digits `2-9` follow after 25 gangs. Your gang is allocated first, then
  allies, then gangs by id, so your letters never move when a neighbour is added.
- **Colour is always the relation.** The chat colour never carries the identity or the state, so the map reads the same
  for colour-blind players.
- **Italic = under attack.** *Italic* letters are turfs with an active capture.
- **Underline = shielded.** An underlined letter cannot be attacked by you right now.
- **No bold.** Bold would widen the glyphs and break the alignment.
- **Which turf a cell shows.** The turf covering at least half of the cell; a small turf that covers less than half of
  every cell still shows in the cell holding its centre.
- **Header.** `Map - Turfs <owned> - north is up - + you`. Without a gang: `Map - north is up - + you`. When no turf is in
  view, the header names the nearest turf with its distance and direction. When the world has no turf, it says so.
- **Legend.** Two lines, each under 300 px. Line 1 lists the gangs in view with their letter and relation (names are cut
  to 8 characters; the full name shows on hover) and ends with `+N more` when some do not fit. Line 2 is fixed and
  explains the colours and styles in words, so hover is never the only carrier.

### Hover and click

- **Hover a letter:** the turf name, the owner (or `Unclaimed`), the state (`Quiet`, or `Under attack by Rats, 62 %`),
  the reason sentence, and what the turf pays per interval (or `dormant`, from W2). Your own gang's last-online time, and your
  own turf's guards in reserve, show only for your gang. Other gangs' online times and guards never show.
- **Admins** also see `/glw turf tp <id>` in the hover text. It is text, not a click.
- **Hover the `+` cell:** the turf under you, your coordinates and your facing.
- **Click a letter:** runs `/glw turf info <id>`.
- **`+N more`:** hover lists the missing gangs; click runs `/glw map legend`.

### Commands

| Command | What it does |
|---|---|
| `/glw map` | Prints the grid. Bedrock players get the text form instead. |
| `/glw map auto` | Turns the auto map on or off. When on, the map is sent again when what it shows changes (its content, not your position). Sent at most every `Map.Auto_Min_Interval_Seconds` seconds. Off by default; the setting is forgotten when you log off. |
| `/glw map text` | The turfs around you as a list, nearest first, with owner, relation and state in words. |
| `/glw map legend` | Every gang on your map with its letter and relation. |
| `/glw map hud <on\|off\|default>` | Sets the turf line in the action bar (see HUD). |

### Map keys (`turf/turf_map.yml`, `Map:`)

| Key | Default | Meaning |
|---|---|---|
| `Map.Width` | `41` | Columns of the grid. Odd numbers only (an even value is raised by one); 11 to 61. |
| `Map.Height` | `7` | Rows of the grid. Odd numbers only; 3 to 15. With the header and the two legend lines this is 10 chat lines, the height of the unfocused chat. `9` is the tall option. |
| `Map.Cell_Blocks` | `16` | Blocks per cell along each side (16 = one chunk). One of `8`, `16`, `32` or `64`; anything else uses 16. |
| `Map.Auto_Min_Interval_Seconds` | `5` | Minimum seconds between two automatic maps. |
| `Map.Bedrock_Text_Form` | `true` | Bedrock players (detected by their UUID prefix) get the text list instead of the grid, because their font is not monospaced. |

Changes apply after `/glw reload`.

---

## HUD

While you stand inside a turf, the action bar shows one line about it. The line is sent every `Hud.Interval_Seconds`
(2) seconds.

```
Harbor - Rats turf - attackable
Docks - your turf - quiet
Harbor - Rats turf - join a gang to capture
Docks - your turf - under attack by Rats, 62%
```

- **Who it is for.** Your own turf says `your turf`; an ally's says `<gang> turf` in aqua; a rival's says `<gang> turf`
  in red; an owned turf seen by a player without a gang says `<gang> turf` in yellow; an unclaimed turf says `unclaimed`.
- **Reason.** Gangless players see `join a gang to capture`. Your own and allied turfs say `quiet`, or `under attack`
  when a capture runs. Rival and unclaimed turfs show the reason sentence, and `under attack by <gang>, N%` while a
  capture runs.
- **Outside every turf** the line is empty, unless you turn it on with `/glw map hud on`. Then it shows
  `Nearest turf: <turf> <distance>m <direction>` in the action bar.

### Priority

The turf line uses action-bar priority `Hud.Priority` (9). Cars, jetpacks and Bartizan guns use priority 10, so while
you hold a gun or drive a car or jetpack that line replaces the turf line, and the turf line comes back when it stops.
The fuel item bar (priority 0) loses to the turf line.

The line about entering a turf (the enter message in the action bar) holds the bar for about 2.5 seconds, so the turf
line first appears about 2.5 seconds after you enter.

### HUD commands and keys

| Command | Effect |
|---|---|
| `/glw map hud on` | Inside turfs and the nearest turf outside. |
| `/glw map hud off` | No turf line at all. |
| `/glw map hud default` | Inside turfs only (the default). |

The keys are in `turf/turf_map.yml`, `Hud:`:

| Key | Default | Meaning |
|---|---|---|
| `Hud.Enabled` | `true` | Turns the turf line off for everyone when `false` (the command still stores each player's choice). |
| `Hud.Interval_Seconds` | `2` | Seconds between two turf lines. |
| `Hud.Priority` | `9` | Action-bar priority. Keep it below 10. |

---

## Placeholders

Six placeholders for a sidebar (PlaceholderAPI, `%gangland_<token>%`). Each reads the turf you stand in, or the nearest
turf in your world for the two `nearest` tokens. Values are plain words without colour codes.

| Token | Example inside a rival turf | Example outside every turf |
|---|---|---|
| `%gangland_turf-name%` | `Harbor` | `-` |
| `%gangland_turf-owner%` | `Rats` | `-` |
| `%gangland_turf-state%` | `attackable` | `-` |
| `%gangland_turf-relation%` | `rival` | `-` |
| `%gangland_turf-nearest-name%` | `Harbor` | `Docks` |
| `%gangland_turf-nearest-distance%` | `0` | `320` |

- `turf-owner` says `unclaimed` for an unclaimed turf.
- `turf-state` says `under attack` during a capture; otherwise `attackable` or `shielded`.
- `turf-relation` says `own`, `ally`, `rival`, `unclaimed` or `gangless`.
- `-` means "no value": outside every turf, and `turf-nearest-*` when the world has no turf.

A sidebar that shows these tokens needs no change to the sidebar plugin's own configuration.

---

## Enter Messages

Walking into a turf shows a title and subtitle. The subtitle depends on your relation to the turf:

| Relation | Subtitle |
|---|---|
| Own | `Your turf` |
| Ally | `<gang> turf - allied` |
| Rival | `<gang> turf - <reason>` |
| Unclaimed | `Unclaimed - <reason>` |
| Gangless | `Join a gang to capture turf` (the title) |

A gang member who enters a rival or unclaimed turf they cannot attack right now also gets one chat line with the reason
(`<turf> - <reason>`). Nothing is printed for your own or allied turfs, for a turf already under attack, or for a
gangless player. `Show_Enter_Title` in `turf_settings.yml` turns the big title off; the action-bar line stays.

---

## Commands

Player commands are open to everyone from 0.17.0. Admin verbs are marked **admin**; they need `gangland.turf.admin`.
`/glw turf` lists only the verbs you may run.

| Command | Usage | Who |
|---|---|---|
| `/glw turf` | Shows the turf you stand in, or your gang's turfs. `[map]` link on the root. | Everyone |
| `/glw turf info [id\|name]` | Turf card: owner, size, income, state, the reason, and whether you can attack it. | Everyone |
| `/glw turf list [page]` | Turfs nearest first with distance and direction, 8 per page. | Everyone |
| `/glw map` | Turf map around you. | Everyone |
| `/glw map auto` | Turns the auto map on or off. | Everyone |
| `/glw map hud <on\|off\|default>` | Turf line in the action bar. | Everyone |
| `/glw map text` | Turfs around you as a list. | Everyone |
| `/glw map legend` | Every gang on your map with its letter and relation. | Everyone |
| `/glw turf wand` | Receives the selection wand. Left-click = pos1, right-click = pos2. | Admin |
| `/glw turf pos1` / `pos2` | Sets a selection corner to your location. | Admin |
| `/glw turf create <displayName>` | Creates a turf from your selection. | Admin |
| `/glw turf rename <name>` | Renames your active-selection turf. | Admin |
| `/glw turf delete` | Deletes your active-selection turf (or the one you stand in). | Admin |
| `/glw turf setowner <gang\|none>` | Sets the owner of your active-selection turf. `none` clears it. | Admin |
| `/glw turf status` | Live state of your active-selection turf: capture progress, cooldown, challenger. | Admin |
| `/glw turf select [id]` | Marks a turf as your active selection. | Admin |
| `/glw turf tp [id]` | Teleports to the centre of a turf. | Admin |
| `/glw turf show` | Particle outline along the edges of the turf. | Admin |
| `/glw turf income <amount>` | Sets the per-interval income of your active-selection turf. | Admin |
| `/glw turf garrison [count]` | Sets, or prints, the guard stock of your active-selection turf. | Admin |
| `/glw turf buff [powerup_id]` | Activates a powerup on your active-selection turf; with no id, lists the active buffs. | Admin |
| `/glw turf powerupnpc <set\|remove>` | Places or removes the Quartermaster NPC of your active-selection turf. | Admin |

Wand, status and turf-card texts come from `turf/turf_messages.yml`.

---

## Permissions

| Node | Default | Grants |
|---|---|---|
| `gangland.command.main` | `true` | `/glw` itself. |
| `gangland.command.map` (and `.auto`, `.hud`, `.text`, `.legend`) | `true` | The map commands. |
| `gangland.command.turf`, `.turf.info`, `.turf.list` | `true` | The turf root, info and list. |
| `gangland.turf.admin` | `op` | Every turf admin verb (the `gangland.command.turf.<verb>` nodes for wand, pos1, pos2, create, delete, setowner, show, status, select, tp, income, garrison, buff, powerupnpc and rename are its children). |

Turf admin rights come only from `gangland.turf.admin`. Its verbs are checked in code as well, so a player without the node
gets `Only turf admins can do that.` even if a permission plugin grants the command node.

---

[← Back to Index](../README.md)
