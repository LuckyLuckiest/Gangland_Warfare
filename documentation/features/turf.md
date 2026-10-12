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

## How turf wars work

`/glw turf rules` prints these five sentences with this server's own numbers, so the page below shows the shipped
defaults. The numbers come from `turf/turf_rules.yml` and `turf/turf_settings.yml`; `/glw reload` applies a change at
once.

1. **Window.** You can attack a turf while one of its owners is online, and for 10 minutes after the last one logs off.
   (`OWNERS_OFFLINE` instead: you can attack a turf once all its owners have been offline for 10 minutes.)
2. **Wake-up.** When the first owner logs in, the turf stays shielded for 120 seconds. (Only with `OWNERS_ONLINE` and a
   wake-up above 0.)
3. **Start.** A capture starts when your gang has more people inside than the owners and their allies, and more than any
   other gang. You count after 15 seconds inside; dying or leaving starts your count again.
4. **Bar.** Each living guard holds back one attacker (up to 4). A capture takes at least 60 seconds; one that goes
   nowhere for 10 minutes ends. A third gang inside a capture of someone else's turf does not count.
5. **Cap and income.** Your gang can hold 2 turfs, plus 1 for each gang level, at most 6. A new turf pays $250 every 10
   minutes while one of its owners was online in the last 24 hours; boosts at most x2.

Clauses whose rule is off (`0`) are left out of the sentence, so a server that turns guards or presence off reads only
what applies to it.

### Key table

| Key | File | Default | Meaning |
|---|---|---|---|
| `Capture.Window` | `turf_rules.yml` | `OWNERS_ONLINE` | `OWNERS_ONLINE` or `OWNERS_OFFLINE` (see Window above). |
| `Capture.Wake_Up_Shield_Seconds` | `turf_rules.yml` | `120` | Shield after the first owner logs in (`OWNERS_ONLINE` only). `0` turns it off. |
| `Capture.Window_Min_Member_Age_Hours` | `turf_rules.yml` | `24` | Only members who joined their gang this many hours ago keep its turfs open and wake them up. In a younger gang every member counts. `0` counts everyone. |
| `Capture.Presence_Seconds` | `turf_rules.yml` | `15` | Seconds an attacker must stay alive inside before counting. Defenders count at once. `0` turns it off. |
| `Capture.Min_Capture_Seconds` | `turf_rules.yml` | `60` | The bar never fills faster than this, whatever the head-count. `0` turns it off. |
| `Capture.Max_Contest_Minutes` | `turf_rules.yml` | `10` | A capture still running after this ends as stalled; the defenders keep the turf. `0` turns it off. |
| `Capture.Guard_Weight_Cap` | `turf_rules.yml` | `4` | Each living guard holds back one attacker, up to this many. `0` means guards never count. |
| `Turf_Cap.Base` | `turf_rules.yml` | `2` | Turfs a gang may hold at level 0. |
| `Turf_Cap.Per_Level` | `turf_rules.yml` | `1` | Extra turfs per gang level. |
| `Turf_Cap.Ceiling` | `turf_rules.yml` | `6` | The most turfs any gang may hold. |
| `Income.Idle_Hours` | `turf_rules.yml` | `24` | A turf pays only while one of its owners was online in the last this many hours. |
| `Income.Max_Multiplier` | `turf_rules.yml` | `2.0` | Income boosts stack, but a turf never pays more than this many times its income. |
| `Income.Gang_XP_Per_Payout` | `turf_rules.yml` | `1` | Gang XP per paying turf per payout. |
| `Garrison.Max_Reserve` | `turf_rules.yml` | `12` | Most guards a turf can hold in reserve. |
| `Garrison.Cost_Per_Guard` | `turf_powerups.yml` | `1000` | Price of one guard at the Quartermaster, from the gang bank. A `garrison_discount` boost multiplies it. |
| `Powerup_Npc.Display_Name` | `turf_npcs.yml` | `Quartermaster` | The Quartermaster's name in panel titles and chat lines. The name over its head comes from `civilians.yml`. |
| `Powerup_Npc.Placement_Cost` | `turf_npcs.yml` | `5000.0` | Price the owning gang pays to place or move its Quartermaster with `/glw turf quartermaster`. `0` = free. |
| `Capture.Duration_Seconds` | `turf_settings.yml` | `180` | Seconds for one attacker with nobody against them to fill the bar from 0 to 100. Owned and unclaimed turfs alike. |
| `Capture.Cooldown_Minutes` | `turf_settings.yml` | `15` | After a capture completes, the turf cannot be contested again for this long. |
| `Capture.Abandon_Grace_Seconds` | `turf_settings.yml` | `15` | Grace after the challenger leaves the turf before the capture is cancelled as abandoned. |
| `Capture.Post_Logoff_Protection_Minutes` | `turf_settings.yml` | `10` | The logout window: how long after the last owner logs off the turf stays attackable (`OWNERS_ONLINE`) or shielded (`OWNERS_OFFLINE`). |
| `Capture.Inactivity_Auto_Release_Days` | `turf_settings.yml` | `4` | Days with no owner online before every turf the gang owns is released. |
| `Income_Interval_Minutes` | `turf_settings.yml` | `10` | Minutes between income payouts. |
| `Default_Income_Amount` | `turf_settings.yml` | `250` | Income stamped on a new turf. Existing turfs keep their own amount (`/glw turf income`). |

The `Capture.Unclaimed_Phase1_Seconds` and `Capture.Unclaimed_Phase2_Seconds` keys are unused since 0.17.0: every
capture fills one bar at `Capture.Duration_Seconds`. They stay so older files load.

### Rules in short

- **Who counts.** A member counts for the window and the wake-up only if they joined their gang at least 24 hours ago,
  or the gang is younger than that. Guards count up to 4 and never block a start.
- **Alerts.** When a capture starts, the owning gang is told wherever it is, in any world, with the attacker count and
  side; allies get the start line. Half the guards down, the last stand at 75%, and the outcome (held, lost, taken or
  stalled) are announced the same way. A gang that starts again on the same turf within 15 minutes of its last start is
  not announced again; the half-guards, last-stand and outcome lines still are.
- **While you were away.** On login you get up to five lines: the income earned since you left, then the turf events
  (captured, lost, held, stalled, released).
- **Start.** The side with the most people inside wins the start. Sides are frozen at the start: a player who joins
  later, or a new ally, is a bystander.
- **One bar.** Owned and unclaimed turfs use the same bar at `Capture.Duration_Seconds`. A bar fills no faster than
  60 seconds, and a capture still running after 10 minutes ends as stalled.
- **Cap.** A gang's turf cap is `2 + 1` per gang level, never more than 6. A gang at its cap cannot start a capture. A
  gang over its cap keeps its turfs, but only the oldest turfs up to its cap pay.
- **Income.** A turf pays money and gang XP only while one of its owners was online in the last 24 hours. Boosts stack
  but stop at x2. A turf over its gang's cap pays nothing.
- **Payout line.** At each payout, online members get an action-bar line: `Turf income +<amount> (<n> turfs) - bank <bank>`
  (`Income.Action_Bar` in `turf_messages.yml`).
- **Cap line.** `/glw turf` opens with `Turfs <owned>/<cap> - <paying> paying` for your gang (`Income.Cap_Line`).
- **Reasons.** Every surface shows one reason sentence (table below). The sentences are the same on the map, the action
  bar, the info card and the enter message.

| Key (`turf_messages.yml`, `Reason:`) | Sentence | When |
|---|---|---|
| `Reason.Attackable` | `attackable` | Nothing stops you from capturing the turf now. |
| `Reason.Cooldown` | `shielded: just captured, attackable in %time%` | The turf was captured recently. |
| `Reason.Owners_Active` | `shielded: %gang% were online in the last %grace%` | `OWNERS_OFFLINE`: an owner was online recently. |
| `Reason.No_Owners_Online` | `shielded: no %gang% online (attackable when one logs in)` | `OWNERS_ONLINE`: nobody of the owning gang is online or was recently. |
| `Reason.Waking_Up` | `shielded: %gang% waking up, attackable in %time%` | The owning gang just came online; the wake-up shield is running. |
| `Reason.Over_Cap` | `shielded: your gang already holds its turf cap` | Your gang already holds as many turfs as its level allows. |

Gang level comes from the gang's turf income (see `gang_rules.yml`, `Progression`), so a gang grows its cap by holding
turf.

### Quartermaster

Each turf has a Quartermaster NPC that sells boosts and guards. Prices come from `turf_powerups.yml`; the shipped boosts
are `small_income_boost` (300), `large_income_boost` (1000) and `garrison_discount` (2500, 20% off guards).

- **Who buys.** The owning gang, and only a member who holds `gangland.turf.upgrade` (the top rank always may). Allies
  can open the panel and look, but cannot buy.
- **Every click re-checks** membership, rank and ownership. A turf that changed hands closes the panel.
- **Guards.** One guard costs `Garrison.Cost_Per_Guard` (1000) from the gang bank, multiplied by any active
  `garrison_discount`. The price stays between 10 % and 100 % of `Garrison.Cost_Per_Guard` and never drops below 1. A turf holds at most `Garrison.Max_Reserve` (12) guards in reserve.
- **Survivors.** Guards still alive when a contest fails return to the reserve, up to the `Garrison.Max_Reserve` cap.
  Guards on the field count against that cap too, so a buy is refused while stock plus live guards reach it. How the
  guards fight and stand down is in [Guards](#guards).
- **Boost catalogue** pages nine boosts at a time.
- **Owner placement.** The owning gang places its Quartermaster with `/glw turf quartermaster`, standing inside the turf.
  It costs `Powerup_Npc.Placement_Cost` from the gang bank (`0` is free). Staff use `/glw turf powerupnpc` instead, for free.
- **Hub rows.** The hub shows the guards on duty and who may spend: the owning gang buys, allies only look.
- **Gang tag.** A short tag of the owning gang is drawn on a line above the Quartermaster. It follows owner changes.
- **Guard orders.** The hub's Guard Orders button (slot 13) switches the guards between Hold and Defend. The owning gang's rank with `gangland.turf.upgrade` may switch it; allies only look. See [Guards](#guards).
- **Garrison read-out.** The Garrison panel shows guards on duty, the reserve against `Garrison.Max_Reserve` (`turf_rules.yml`), and the next wave, which comes from the reserve at no extra cost.

---

## Guards

Guards are the NPCs a gang buys at its turf's Quartermaster. When a rival starts capturing the turf, they defend it.
This section describes the code as shipped. The numbers are the defaults in `turf/turf_npcs.yml` (section `Garrison:`)
and `Garrison.Max_Reserve` in `turf/turf_rules.yml`. Guards need Citizens: on a server without it nothing spawns and
nothing is spent.

### Stock and waves

- **Stock.** Buying a guard adds one to the turf's reserve. The reserve holds at most `Garrison.Max_Reserve` (12) guards,
  counting guards on duty.
- **Spending.** A capture does not take anything from the reserve. Each guard leaves the reserve only once it has
  spawned, so a wave that never spawns costs nothing.
- **First wave.** When a rival starts a capture, the first wave of `Garrison.Wave_Size` (3) guards spawns within a few
  ticks at one of five posts: the four corners of the turf, inset 2 blocks, and the centre, each on the ground above
  the surface.
- **Hidden posts.** A post is hidden when no attacker can see it. A wave takes the hidden post farthest from the nearest
  attacker. If every post is in sight of an attacker, the wave waits 3 seconds for a hidden one and then takes the
  farthest post. With no attacker near the turf, the first corner is used.
- **Top-ups.** Each guard that falls is owed a replacement. Replacements come in waves of up to `Wave_Size`, at most
  `Garrison.Max_Alive` (6) guards stand at once, and a top-up waits `Garrison.Wave_Cooldown_Seconds` (20) after the last
  fall. A top-up never takes more than the reserve holds.
- **Owners are told** when a wave takes its posts: `N guards took their posts at <turf>` (`Garrison.Wave` in
  `turf_messages.yml`).

### Targets, leash and stand-down

- **Targets.** A guard fights the closest attacker within `Defender.Targeting_Radius` (32) that stands inside the leash.
  The leash is the turf plus `Garrison.Leash_Margin` (16) blocks on every side. Attackers outside it are ignored, and
  they do not pick the posts either.
- **Leash.** A guard that is past the leash drops its target and walks back to the nearest post.
- **Stand-down.** When a capture ends, survivors stop fighting and walk to the nearest post. When the contest fails, they
  shout `Shouts.Turf_Held` ("Turf's ours. Back to your posts."). When the turf is captured, they shout `Shouts.Turf_Lost`, but the
  owner change recalls the guards in the same tick, so that line is often cut short. After
  `Garrison.Stand_Down_Seconds` (8) the survivors leave.
- **Return.** A failed contest sends the survivors back to the reserve, up to `Max_Reserve`, and the owners are told
  `N guards back in the reserve at <turf>`. A completed capture returns nothing, because the turf changes hands and the
  old owner's stock is reset. Guards still in the field when the server stops are not refunded.
- **Recycling.** A guard that cannot reach anyone for `Garrison.Recycle_Seconds` (12), while no attacker can see it, is
  replaced by a new guard at another post. The new guard keeps the number and the role of the old one and spends no
  stock. Set `0` to turn this off.

### Callsigns and health bars

Each guard shows a gold hologram line above its head: `[TAG] Guard n`. The tag is the first three letters or digits of
the gang's name without colour codes. It is padded from the gang name when that is short, it is `GNG` when the name has
none, and it is four characters long when three would spell `CIT`. The guard's Citizens nameplate is hidden. The number
`n` counts from 1 in each contest; a replacement takes the next number, and a recycled guard keeps its number. With
`gangland-healthbars` installed, the guard's health bar sits directly under the callsign.

### Roles

`Garrison.Composition` gives roles to the guards in spawn order. The shipped default is:

```yaml
Garrison:
   Composition:
      - "Commander@1"
      - "Shield@2"
      - "Marksman@2"
```

The role names are the `AI.Combat.Roles` of the `turf_defender` type in `civilians.yml`.

| Role | Guards | What it does |
|---|---|---|
| Commander | 1 | When it falls, its squad holds for 5 seconds: the others stop moving and stop attacking, and the squad shouts once. |
| Shield | 2 and 3 | Takes half of every hit that comes from inside the 90-degree cone in front of it. |
| Marksman | 4 and 5 | Fires from its own band (12 to 24 blocks in `civilians.yml`), clamped under its gun's reach. With Bartizan's shipped rifle that is 8 to 10 blocks. |

- Guards 6 and up are plain guards. A replacement past guard 5 is plain, and a fallen commander is not replaced by another
  commander.
- An empty list gives no roles. A name the guard type does not define is logged once, and that guard is plain.

### Guard bounty

A guard drops no civilian cash. Any player who is neither in the owning gang nor in a gang allied to it (a player with no
gang included) who kills a guard finds cash at its feet worth `Garrison.Bounty_Share` (0.5) of the guard's price, rounded down. The price is the
guard price in force when the capture starts, discount included. It is the price the owner pays for a guard now, not a
stored record of what it paid. Nothing is minted: the owner has paid for the guard already. The owning gang and its allies
get nothing. Set `Bounty_Share` to `0` to turn the bounty off. If a server enables an NPC drop in `money.yml`, that drop
is paid on top of the bounty. The bounty needs `money.yml` to have a default variation; without one the bounty is not paid
and only a warning is logged.

### Cop response

`Cop_Response` in `turf_npcs.yml` covers the turf's defenders when a cop hits a player on a turf that the gang protects
(the owning gang, and its allies when `Include_Allies` is on). The Quartermaster and the guards within
`Cop_Response.Targeting_Radius` of the cop then turn on it. Defenders never start a fight with a cop on their own. They
stop again when the cop dies, when the victim leaves or stops being protected, or when the cop moves out of range. Set
`Enabled` to `false` and defenders ignore cops entirely.

**Guard orders.** Under Hold (Quartermaster hub, Guard Orders) the Quartermaster and the guards answer no cop, and a cop a guard was engaged with is released. The order is saved per turf. The order resets to Defend whenever a turf changes hands (capture, admin, disband, inactivity): the garrison row is dropped with the old owner's stock (T-270). Deleting a turf drops it too.

**Owner alerts.** Online members of the owning gang (not allies or rivals) get a chat line and an action-bar line when a cop hits one of them on the turf, a guard falls, an outsider hits the Quartermaster, or the Quartermaster is killed. Each kind is sent at most once per `Owner_Alerts.Cooldown_Seconds` (default 60) per turf; guard and Quartermaster deaths are told once per NPC. Cop-hit alerts need `Cop_Response.Enabled` to be `true`. Hold does not silence them.

### Guard keys (`turf/turf_npcs.yml`)

| Key | Default | Meaning |
|---|---|---|
| `Garrison.Wave_Size` | `3` | Guards per wave. |
| `Garrison.Max_Alive` | `6` | Most guards standing at once on one turf. |
| `Garrison.Wave_Cooldown_Seconds` | `20` | Seconds after a fall before the next top-up. |
| `Garrison.Stand_Down_Seconds` | `8` | Seconds survivors stay after the fight before they leave. |
| `Garrison.Leash_Margin` | `16` | Blocks past the turf edge a guard may chase. |
| `Garrison.Composition` | the three roles above | Roles in spawn order. An empty list gives none. |
| `Garrison.Bounty_Share` | `0.5` | Share of a guard's price paid to a rival who kills it. `0` turns it off. |
| `Garrison.Recycle_Seconds` | `12` | Seconds a guard may be unreachable before it reappears at another post. `0` turns it off. |
| `Defender.Targeting_Radius` | `32.0` | How far a guard looks for an attacker. |
| `Defender.Lifespan_Seconds` | `600` | Safety lifespan. A guard lives at least as long as the longest contest (`Max_Contest_Minutes` plus 60 seconds). |
| `Cop_Response.Enabled` | `true` | Defenders answer cops hitting a protected player. |
| `Cop_Response.Targeting_Radius` | `32.0` | Range within which defenders answer a cop. |
| `Cop_Response.Include_Allies` | `true` | Also protect members of allied gangs. |
| `Garrison.Hold_Radius` | `6.0` | Blocks around its post a guard keeps to while the order is Hold. Minimum `3.0`. |
| `Owner_Alerts.Cooldown_Seconds` | `60` | Seconds before the same owner alert for the same turf is sent again. `0` = every time. |

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
it back; the turf changes hands when the bar completes. The rules are in the "How turf wars work" section above.

- **Who can attack, and when.** The window (`Capture.Window`) decides it. With the default `OWNERS_ONLINE`, a turf can be
  attacked while one of its owners is online and for `Capture.Post_Logoff_Protection_Minutes` after the last one logs
  off. When an owner comes back, the turf is shielded for the wake-up shield.
- **Start and bar.** A capture starts when the attacking gang has more people inside than the owners and their allies,
  and more than any other gang. Attackers and defenders fill or push back one bar; each living guard of the turf holds
  back one attacker, up to `Capture.Guard_Weight_Cap`; guards never block a start.
- **After a capture.** The turf cools down for `Capture.Cooldown_Minutes` and cannot be contested again until then. A
  capture is cancelled as abandoned only after no member of the attacking gang has been inside the turf for
  `Capture.Abandon_Grace_Seconds`. An attacker who steps out and back in keeps the contest.

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
| `/glw map zoom in\|out` | Halves or doubles your cell size (8 to 64 blocks). See Zoom and border. |
| `/glw map border` | Outlines the turf you stand in, or the nearest one within 64 blocks, for 10 seconds. |
| `/glw map hud <on\|off\|default>` | Sets the turf line in the action bar (see HUD). |

### Map keys (`turf/turf_map.yml`, `Map:`)

| Key | Default | Meaning |
|---|---|---|
| `Map.Width` | `41` | Columns of the grid. Odd numbers only (an even value is raised by one); 11 to 61. |
| `Map.Height` | `7` | Rows of the grid. Odd numbers only; 3 to 15. With the header and the two legend lines this is 10 chat lines, the height of the unfocused chat. `9` is the tall option. |
| `Map.Cell_Blocks` | `16` | Blocks per cell along each side (16 = one chunk). One of `8`, `16`, `32` or `64`; anything else uses 16. This is the starting size for every player; `/glw map zoom` changes it for that player until they log off. |
| `Map.Auto_Min_Interval_Seconds` | `5` | Minimum seconds between two automatic maps. |
| `Map.Bedrock_Text_Form` | `true` | Bedrock players (detected by their UUID prefix) get the text list instead of the grid, because their font is not monospaced. |

Changes apply after `/glw reload`.

### Zoom and border

`/glw map zoom in` halves the cell size you see, and `out` doubles it: 8, 16, 32 or 64 blocks per cell. The size is yours
alone. It starts at `Map.Cell_Blocks` and is forgotten when you log off. The chat map, the text form and the auto map use
your size, and an auto map redraws when you change it. Zooming past 8 or 64 shows a limit line and changes nothing.

`/glw map border` outlines the turf you stand in, or the nearest turf within 64 blocks, for 10 seconds. Only the part of
the outline within 40 blocks of you is drawn, in the particle set by `Visualization_Particle` (`turf_settings.yml`,
`FLAME` by default). It is redrawn each second with at most 600 particles, and on a large turf the spacing grows to keep
the count under that. With no turf within 64 blocks it says so.

### Web map (dynmap)

When the dynmap plugin is installed and `Web.Enabled` is `true`, every turf is drawn as a rectangle on a dynmap layer
named by `Web.Layer_Label` (`Turfs`). Without dynmap nothing is loaded and nothing happens; the server boots as normal.

- **Colour.** The fill and the border take the owning gang's colour. Unclaimed turfs, and gangs whose colour cannot be
  read, use `Web.Unclaimed_Color`.
- **Popup.** The turf name, then `Owner: <gang>` or `Owner: Unclaimed`. Colour codes are stripped.
- **Timing.** An owner change redraws that turf at once. Creating or deleting a turf fires no event, so it reaches the
  map within 30 seconds. `/glw reload` redraws the whole layer.

| Key (`turf/turf_map.yml`, `Web:`) | Default | Meaning |
|---|---|---|
| `Web.Enabled` | `true` | Draws the turfs on dynmap when dynmap is installed. |
| `Web.Layer_Label` | `"Turfs"` | Name of the dynmap layer. |
| `Web.Fill_Opacity` | `0.25` | Fill opacity of a turf rectangle, 0.0 to 1.0. |
| `Web.Line_Opacity` | `0.8` | Border opacity, 0.0 to 1.0. |
| `Web.Line_Weight` | `2` | Border width in pixels. |
| `Web.Unclaimed_Color` | `"#AAAAAA"` | Colour of unclaimed turfs and of gangs whose colour cannot be read. |

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
| `/glw turf` | Shows the turf you stand in, or your gang's turfs, under the cap line (`Turfs owned/cap - n paying`). `[map]` link on the root. | Everyone |
| `/glw turf info [id\|name]` | Turf card: owner, size, income, state, the reason, and whether you can attack it. | Everyone |
| `/glw turf list [page]` | Turfs nearest first with distance and direction, 8 per page. | Everyone |
| `/glw turf rules` | The five sentences of how turf wars work, with this server's numbers. | Everyone |
| `/glw map` | Turf map around you. | Everyone |
| `/glw map auto` | Turns the auto map on or off. | Everyone |
| `/glw map hud <on\|off\|default>` | Turf line in the action bar. | Everyone |
| `/glw map text` | Turfs around you as a list. | Everyone |
| `/glw map legend` | Every gang on your map with its letter and relation. | Everyone |
| `/glw map zoom <in\|out>` | Halves or doubles your map cell size (8 to 64 blocks). | Everyone |
| `/glw map border` | Outlines the nearest turf for 10 seconds, the part near you. | Everyone |
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
| `/glw turf buff [powerup_id]` | Activates a powerup on your active-selection turf, for no gang money (a staff tool); with no id, lists the active buffs. | Admin |
| `/glw turf powerupnpc <set\|remove>` | Places the Quartermaster NPC where you stand (`set` only while you are inside the selected turf), or removes it. | Admin |
| `/glw turf quartermaster` | Places or moves your gang's Quartermaster where you stand, inside a turf your gang owns. Costs `Powerup_Npc.Placement_Cost` from the gang bank. | Player (gang rank with `gangland.turf.upgrade`) |

Wand, status and turf-card texts come from `turf/turf_messages.yml`.

---

## Permissions

| Node | Default | Grants |
|---|---|---|
| `gangland.command.main` | `true` | `/glw` itself. |
| `gangland.command.map` (and `.auto`, `.hud`, `.text`, `.legend`, `.zoom`, `.border`) | `true` | The map commands. |
| `gangland.command.turf`, `.turf.info`, `.turf.list`, `.turf.rules` | `true` | The turf root, info, list and rules. |
| `gangland.command.turf.quartermaster` | `true` | `/glw turf quartermaster`. Placing also needs `gangland.turf.upgrade` in the gang. |
| `gangland.turf.admin` | `op` | Every turf admin verb (the `gangland.command.turf.<verb>` nodes for wand, pos1, pos2, create, delete, setowner, show, status, select, tp, income, garrison, buff, powerupnpc and rename are its children). |

Turf admin rights come only from `gangland.turf.admin`. Its verbs are checked in code as well. A player without the command
node gets the server's no-permission text. `Only turf admins can do that` is for players who hold an admin command node but
not `gangland.turf.admin`.

---

[← Back to Index](../README.md)
