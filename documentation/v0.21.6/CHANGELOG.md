# 0.21.6 Changelog - Disguise difficulty

[← Back to Documentation Index](../README.md)

Read [migration-0.21.6.md](../migration-0.21.6.md) before you start the server. **No edit is needed:** every new key has a
code default. Upgrade from 0.21.5. Config files are never rewritten: edit them by hand to tune.

## Requires

- **Keystone 1.15.5** (unchanged).
- **gangland-api 2.7 and module `Host_Api` 2.7** (unchanged). Only the `cops-n-crooks` module changes.
- **Bartizan:** the build floor is 0.6.0 (the pom); the live acceptance runs used 0.6.1.

## Changed

### The cops remember you, and the difficulty sets how long

- **Recognition lasts `Memory_Seconds` after a cop last had eyes on you.** 3, 5, 10 or 20 seconds for 1-2, 3, 4 and 5
  stars. A corner, a door or one missed 0.5 s sample no longer ends it, so changing clothes in front of the cops no longer
  works; a change is learned at the next sighting. Killing the last watcher no longer opens a window either.
- **How many pieces must differ follows the difficulty.** `Changes_Needed` (2) plus `Extra_Changes` per row: 2, 2, 3 and 4
  of head, chest, legs, boots and on foot / in a car. Stripping counts as changing. New `Cops.Disguise.By_Difficulty`
  (`Easy`, `Normal`, `Hard`, `Deadly`, each with `Extra_Changes` and `Memory_Seconds`); the row is the `Difficulty` of the tier
  for your stars.
- **A blown police outfit never fools,** whatever else changed.

### Who counts as the cops' eyes

- **A cop hunting you counts to `Alert_Range` with the bare line** while the cops know you: darkness, crowds and sneaking
  only stop a first pick-up now.
- **Any other cop counts as a patrol does,** within `Patrol.Warning.Range` (12): a beat cop watching a swap counts, and the
  squad does not have to exist yet. A cop walking home never counts.
- **Glass and iron bars no longer hide you from a cop at the window.** They stop the sight line (measured live), so while
  the cops know you a hunting cop within `Wanted.Witnesses.Cops.Witness_Floor` (6) counts with no line at all. That also
  means a cop within 6 blocks sees through a wall; a first pick-up still needs the line. A fence does not stop the line.
- **A cop's own report starts the memory,** so a swap in front of the reporting cop is seen. An escape ends it.

### The fire gate

- **Cops hold fire on a player they do not recognise,** at every star level, and keep searching. "The cops don't recognise
  you" is now true at 3 stars and up. This reverses the 0.20.0 rule that filters never stop firing.
- **Hitting a cop in sight gives you away at once,** and blows a worn police outfit (a 0-star attacker in it is fired back
  on). A hit and a duck behind a wall still pays.

### The cops tell you

- **New chat line `Disguise.Lost`,** said once when no cop has seen you for the memory: "The cops lost you. They want: ...
  N of head, chest, legs, boots, on foot / in a car must differ." Set it to `''` to turn it off. Spanish file updated; a
  deployed `wanted_messages_es.yml` needs the key added by hand (see the migration doc).

### Night balance change

- **Role cops (Marksman, SWAT and the other squads) see in the dark again at 4 and 5 stars.** `Ignore_Darkness` was dropped by
  the role overlay in 0.20.0. Night pursuit at 4-5 stars is harder than in 0.21.5, whatever the disguise. Own commit, can be
  reverted on its own.

## Fixed

- `CopRole.overlay` lost `Ignore_Darkness` (row 856).
- A swap in front of the cop reporting a crime fooled that cop (row 869).
- The last cop in range dying or an emptied group reset recognition (row 861).

## Docket

Fixed: 856, 857, 858, 859, 860, 861, 862, 863, 864, 865, 869, 871. Open by design (P3): 866 (an unrecognised suspect is
not re-checked at arm's length), 867 (any vehicle or empty slot counts as a changed piece), 870 (the breach acts on the
last-known spot). Open: 868 (a late report stores the held crime look over a newer one).

## Decisions and open items

- The memory and piece numbers are reasoned tuning, not measured over a long play session. **Open for the playtest:** can the
  player get the memory on foot at 3, 4 and 5 stars? The top rows may only be reachable by car. Numbers change in YAML only.
- The boss bar and the memory can disagree: a hunting cop at night past the sight factor range, or behind glass inside
  6 blocks, keeps the memory while the bar reads SEARCHING.
- `Witness_Floor` is one key for two rules: tuning it for crime reports also moves the disguise rule.
- The Lost line reveals the exact moment the memory runs out. That is the point: it teaches the rule.

## For developers

- gangland-api, Keystone and Bartizan are unchanged. `DisguiseSettings` gains `byDifficulty` and the 6-argument
  constructor stays; `DisguiseFilter` takes a difficulty function; `DisguiseModuleConfig.eyesOf` takes the descriptions, a
  witness supplier and the `Witness_Floor`. See [developer/cops-n-crooks.md](../developer/cops-n-crooks.md).
