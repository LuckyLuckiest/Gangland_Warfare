# Turf - Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/turf.md)

---

## Overview

Turf: admin-drawn areas that gangs own, capture and earn from. This checklist is the manual gate for the map
(`/glw map`), the action-bar turf line, the placeholders and the turf card. The map has to be checked by eye: its
alignment, italic and underline styles, and hover cannot be asserted by unit tests. Run it at GUI scale 1, 2, 3 and 4
where the item says so.

**Modules involved:** `gangland-features/gangland-turf`, `gangland-features/gangland-gadget` (cars and jetpacks, for the
HUD priority items), `gangland-features/gangland-gang` (gang onboarding and ranks), `gangland-impl` (`plugin.yml`
permissions and the `turf-` placeholder prefix), Bartizan (a gun in hand, for the HUD priority item), Plaque (sidebar
placeholders).

---

## Pre-Conditions

- [ ] Server is running the 0.17.0 build with Keystone, Bartizan and Plaque installed; Citizens optional.
- [ ] At least four test accounts: one op, one non-op in gang Vipers (member), one non-op in gang Rats (rival; online for shielded items, logged off longer than `Capture.Post_Logoff_Protection_Minutes` for attack items),
  one non-op with no gang.
- [ ] Two turfs exist: `Docks` owned by Vipers and `Harbor` owned by Rats, both inside one world, at least 100 blocks apart.
- [ ] Turf config files exist at their defaults: `turf/turf_rules.yml`, `turf/turf_map.yml`, `turf/turf_messages.yml`,
  `turf/turf_settings.yml` (regenerate by deleting them and restarting if unsure).
- [ ] A Bedrock test account is available for the text-form item, or the item is marked as skipped.
- [ ] The client's GUI scale can be set to 1, 2, 3 and 4 (Options > Video Settings).

---

## Map Layout

- [ ] At GUI scale 1, `/glw map` prints 7 grid rows; every grid row ends in the same column (no row is longer or shorter).
- [ ] At GUI scale 2, the same grid is aligned: the columns of each row line up under the rows above and below.
- [ ] At GUI scale 3, the same grid is aligned.
- [ ] At GUI scale 4, the same grid is aligned.
- [ ] At every GUI scale the header line and both legend lines do not wrap onto a second chat line.
- [ ] The header reads `Map - Turfs <n> - north is up - + you` for a Vipers member; the `+` marks your own cell.
- [ ] The map block is 10 chat lines in total (header, 7 grid rows, two legend lines).

---

## Map Styles

- [ ] A turf under attack (Vipers capturing Harbor, viewed by a third account) renders its letters in *italic*, and the columns to its right do
  NOT shift compared with the same map when no attack runs.
- [ ] A shielded turf (an owner online now or within `Capture.Post_Logoff_Protection_Minutes`, or just captured) renders its letters underlined, and the columns to its right
  do NOT shift.
- [ ] No letter anywhere renders bold.
- [ ] Your own turf is green, an ally's aqua, a rival's red, an unclaimed cell `#` gray, empty cells `-` dark gray, your
  `+` white.
- [ ] Line 2 of the legend reads `# unclaimed  - none  italic attacked  underlined shielded` and does not need colour to
  be read.

---

## Map Hover and Click

- [ ] Hover a rival turf's letter: the tooltip shows the turf name, the owner, the state, the reason sentence and the
  pay per interval; it does NOT show the rival's guard count or the rival's members' online times.
- [ ] Hover your own turf's letter: the tooltip shows the same facts plus your guards in reserve and your own gang's
  last-online time.
- [ ] As an admin (op), hover a turf's letter: the tooltip shows `/glw turf tp <id>` as text; there is no click action
  for it.
- [ ] Click a turf's letter: chat runs `/glw turf info <id>` and the turf card opens with a `Capture:` line.
- [ ] Hover the `+` cell: the tooltip shows the turf under you (or none), your coordinates and your facing.
- [ ] Hover the `+N more` text on line 1 (needs more gangs than fit): the tooltip lists the gangs left out.
- [ ] Click `+N more`: chat runs `/glw map legend`, which lists every gang on the map with its letter and relation.

---

## Map Auto and Text

- [ ] `/glw map auto` prints `Auto map on`. Walk from outside into a turf: the map is sent again once.
- [ ] With auto on, walk from one turf into another turf of a different owner: a new map is sent; not more often than
  every 5 seconds (walk back and forth quickly: at most one map per 5 s).
- [ ] `/glw map auto` again prints `Auto map off`; walking into turfs now sends nothing.
- [ ] Turn auto on, log off and log back in: auto is off again (the setting is forgotten on relog).
- [ ] `/glw map text` prints the turfs around you as a list, nearest first, with owner, relation and state in words.
- [ ] A Bedrock player running `/glw map` gets the text form, not the grid. *(Skip if no Bedrock account.)*

---

## Map Legend

- [ ] `/glw map legend` lists every gang on the map with its letter and relation.
- [ ] A gang with a name in colour codes (for example `&c` in the name) shows the plain name in the legend.

---

## Turf Rules

- [ ] `/glw turf rules` prints a header and five sentences; the numbers match `turf/turf_rules.yml` and `turf/turf_settings.yml`.
- [ ] Edit one value (for example `Capture.Presence_Seconds` in `turf/turf_rules.yml`), run `/glw reload`, run `/glw turf rules` again: the new number shows at once.
- [ ] With `Capture.Window: OWNERS_OFFLINE`, the rules show the offline sentence and no wake-up sentence.
- [ ] With `Capture.Presence_Seconds: 0` and `Capture.Guard_Weight_Cap: 0`, the start and bar sentences drop their presence and guard clauses.
- [ ] Any player without a gang can run `/glw turf rules`.

## HUD

- [ ] Stand inside `Harbor` as a Vipers member: the action bar shows the turf line (`Harbor - Rats turf - ...`) and it
  refreshes every 2 seconds.
- [ ] The first turf line appears about 2.5 seconds after you enter a turf (the enter line holds the bar that long).
- [ ] Stand inside a turf as a player with a Bartizan gun in hand: the gun line shows; once you lower the gun, the turf
  line returns within about 2 seconds.
- [ ] Sit in a car (gadget) inside a turf: the fuel line shows; leave the car: the turf line returns.
- [ ] Mount a jetpack inside a turf: the jetpack line shows; dismount: the turf line returns.
- [ ] While the fuel item bar (priority 0) is shown, the turf line replaces it inside a turf.
- [ ] Walk out of every turf: the action bar shows nothing (default HUD mode).
- [ ] `/glw map hud on` (outside every turf): the action bar shows `Nearest turf: <turf> <distance>m <direction>`.
- [ ] `/glw map hud off` (inside a turf and outside): the action bar shows no turf line at all.
- [ ] `/glw map hud default`: inside turfs only again; the reply reads `Turf line: inside turfs only.`
- [ ] `/glw map hud` with no argument: the reply names the current mode.

---

## Placeholders

- [ ] On a Plaque sidebar with a line `%gangland_turf-name%`: inside `Harbor` it reads `Harbor`; outside every turf it
  reads `-`.
- [ ] Add a line for `%gangland_turf-owner%`, `%gangland_turf-state%`, `%gangland_turf-relation%`: each updates when you
  walk into and out of a turf, with zero change to the Plaque configuration after the line was added.
- [ ] `%gangland_turf-nearest-name%` and `%gangland_turf-nearest-distance%` outside every turf show the nearest turf in
  your world and its distance in blocks; inside a turf the distance reads `0`.
- [ ] Turn the sidebar on, enter a rival turf, leave it: the turf line changes within one refresh; nothing in Plaque's
  config files changed between the two states.

---

## Enter Titles and Messages

- [ ] Enter an own turf (Vipers member in `Docks`): title subtitle reads `Your turf`.
- [ ] Enter an allied turf (an ally's turf, with an alliance active): subtitle reads `<gang> turf - allied`; no chat line.
- [ ] Enter a rival turf in cooldown as a Vipers member (select `Harbor`, set it unclaimed with `/glw turf setowner none`, let Rats
  capture it, then a Vipers member enters): subtitle reads `<gang> turf - shielded:
  just captured, attackable in ...`; exactly one chat line `<turf> - shielded: just captured ...`.
- [ ] Enter a rival turf that is attackable as a Vipers member: subtitle reads `<gang> turf - attackable`; no chat line.
- [ ] Enter an unclaimed turf: subtitle reads `Unclaimed - attackable` (or the reason).
- [ ] Enter any turf as a gangless player: title reads `Join a gang to capture turf`; no chat line.
- [ ] Enter a turf already under attack as a Vipers member: the under-attack enter line shows; no reason chat line is added.
- [ ] Set `Show_Enter_Title: false` in `turf_settings.yml`, run `/glw reload`, enter a turf: no big title; the
  action-bar line still shows.

---

## Turf Card and Capture Boss Bar

- [ ] `/glw turf info Harbor` (by name, lower-case `harbor`) opens the card for `Harbor`.
- [ ] `/glw turf info <id>` opens the same card for the same turf.
- [ ] `/glw turf info` with an unknown name prints the turf-not-found line.
- [ ] Tab completion after `/glw turf info ` offers every turf id and every turf name without spaces.
- [ ] The card's `Capture:` line matches the reason in the map hover and in the enter message for the same turf.
- [ ] During a capture, the boss bar title reads `<gang> is capturing <turf> - <attackers> vs <defenders> - <time>`; the
  time updates about once a second and is `stalled` when attackers do not outnumber defenders.
- [ ] Turf boss bar disappears when the capture ends; no persistent turf boss bar is shown outside a capture.

---

## Turf Admin

- [ ] Op: `/glw turf wand` gives the wand; left-click sets pos1 and right-click sets pos2 with a confirmation line per
  corner (`Turf pos1 set to (x, y, z) in world ...`).
- [ ] `/glw turf create Test` from a 20 x 20 selection: the turf is created; the create line prints the snapped bounds
  when they changed (snapping is on by default).
- [ ] `/glw turf create` from an 8 x 8 selection with snap off: refused with the too-small message.
- [ ] `/glw turf create` from a 600-block-wide selection: refused with the too-large message.
- [ ] Create a second turf named `docks` (lower case) when `Docks` exists: refused with `A turf named ... already exists`.
- [ ] Set pos1 in world A, then pos2 in world B: refused with `That corner is in another world than your selection`;
  pos1 is kept.
- [ ] `/glw turf rename Harbor2` on the active turf: the reply reads `Renamed turf ... to Harbor2`; `/glw turf info`
  shows the new name after a restart.
- [ ] `/glw turf tp <id>` teleports to the turf centre (op only).
- [ ] `/glw turf status` inside a turf in cooldown reads the cooldown reason, not an idle state.

---

## Non-Op Player

- [ ] Non-op player: `/glw` opens the help.
- [ ] Non-op player: `/glw map` prints the map.
- [ ] Non-op player: `/glw turf info` and `/glw turf list` run.
- [ ] Non-op player: `/glw gang`, `/glw gang list`, `/glw gang info <name>` and `/glw gang create <name>` run.
- [ ] Non-op player: `/glw turf tp`, `/glw turf delete` and `/glw turf create` are refused with `Only turf admins can do that.`
- [ ] Non-op player: `/glw turf` help lists only `turf`, `turf info`, `turf list` and the `[map]` link.

---

## Fresh Install Ranks

- [ ] Fresh install, no rank data: a gang member can deposit into the gang bank (`/glw gang deposit`) and it succeeds.
- [ ] Fresh install, no rank data: a member promoted to officer can invite a player (`/glw gang invite`).

---

## Restart and Cooldown

- [ ] Capture a turf, let its cooldown start, restart the server during the cooldown; after the restart, `/glw turf status`
  on that turf still says cooldown (TF-27), not idle.
- [ ] After the restart, the map shows the turf as shielded (underlined) for a viewer from another gang.

---

## Regression Risks

- `gangland-turf` - the map, HUD, placeholders, enter messages and turf card all read the same
  `CaptureEligibility` reason; a change there shows up on every surface at once.
- `gangland-gadget` and Bartizan - their priority-10 action-bar lines win over the turf line; check the HUD items above
  after any change to `ActionBarManager` priorities.
- Plaque - the `turf-` placeholders depend on the impl prefix line `turf-` in `GanglandPlaceholder`.
- `gangland-gang` - gang list, info and onboarding are read by the Non-Op section.
- `plugin.yml` - player nodes default true; admin nodes under `gangland.turf.admin`.

---

## W2 Manual Checks (0.17.0 Fair fights)

- [ ] Owner's ten-minute script and one live two-client fight: alert reaches the owning gang anywhere, with the right side.
- [ ] Guards hold the bar; the boss bar shows `+N guards`; a failed and a stalled capture are told to both sides; the digest arrives on login.
- [ ] One boss bar for an unclaimed capture; titles; hover and click on the map.
- [ ] Quartermaster: buy a guard through the NPC GUI (cannot be scripted); reserve cap 12; a recruit is refused; an ally can only look; catalogue pages turn.
- [ ] Half-guards alert fires once guards are killed.
- [ ] Wake-up shield after a server restart; the turf cap refusal at a gang's cap; dormant income after 24 h; over-cap payout; multiplier cap. (Unit tests cover the last four; the restart and digest checks need a live server.)
- [ ] `/glw turf rules` prints the five sentences with this server's numbers.

## 0.18.0 Manual Checks (Guards, map zoom and border, dynmap)

- [ ] Two guard waves with Citizens: a capture on a turf with six guards in stock brings the first wave of three to its posts; after a guard falls a top-up follows after the cooldown; the owners get the `took their posts` line each time.
- [ ] No stock spent without Citizens: on a server without Citizens, start a capture; `/glw turf garrison` on that turf shows the same count afterwards.
- [ ] Guards walk back after a chase past the edge: chase a guard beyond the 16-block margin; it walks to a post and stops targeting.
- [ ] Survivors return after a failed attack: defend a capture and let it fail; the count goes back to the reserve (capped at 12) and the owners get the `back in the reserve` line.
- [ ] Callsign and bar layout at GUI scale 2: `[TAG] Guard n` sits above each guard, the health bar directly under it, and nothing overlaps.
- [ ] Commander down: kill the commander guard; the rest of the squad holds for about 5 seconds and then resumes.
- [ ] Shield guard blocks frontal hits: take hits from the front and from the side; the front hits do about half the damage.
- [ ] Killing guards pays the attacker, never the owner: a rival kill and a gangless player's kill each drop cash worth half the guard price; an owner or ally kill drops nothing.
- [ ] Cop response: a cop hitting a protected player brings the nearby defenders onto the cop; defenders do not start a fight with a cop on their own.
- [ ] Map zoom in/out limits: from 16, `zoom in` twice shows `Closest zoom already` at 8; from 16, `zoom out` three times shows `Widest zoom already` at 64.
- [ ] Map border near a 512-block turf: the outline appears only within about 40 blocks of you, for about 10 seconds, and the server stays responsive.
- [ ] Dynmap layer colours after a capture: the rectangle takes the new owner's colour at once; a turf created in the last 30 seconds appears on the layer.
- [ ] Server without dynmap boots normally, and the map commands still work.
- [ ] `/glw turf buff` on a turf does not change the gang bank balance (TF-31, by design: staff tool).

---

[Back to Test Index](../README.md)
