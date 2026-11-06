# Migrating a server from 0.21.5 to Gangland 0.21.6

[← Back to Documentation Index](./README.md)

0.21.6 makes the disguise depend on how hard the cops are. Your stars set how long the cops remember you and how many
pieces of your look must change, a swap made in front of a cop no longer works, and a cop no longer shoots a player they
do not recognise. A new install gets everything from the shipped files. **An existing server keeps its old files**, and
the code never rewrites them. **No edit is needed on upgrade**: every new key has a code default that applies when your
file lacks it. The notes below are for tuning and for the Spanish text.

## 1. Swap the jars

- Replace the Gangland core jar with the 0.21.6 build and the `cops-n-crooks` module jar in
  `plugins/Gangland_Warfare/modules/` with the 0.21.6 build. Only `cops-n-crooks` changed (the core jar changes only by
  its revision); swapping every module jar together is harmless.
- Keystone stays at 1.15.5 and `Host_Api` at 2.7. Bartizan is unchanged in the pom, which builds against **0.6.0**
  (`bartizan.version`); 0.6.0 is the floor. The live acceptance runs used Bartizan 0.6.1.

## 2. Nothing to edit: the new scale applies

A `cops.yml` without `Cops.Disguise.By_Difficulty` gets the shipped scale below. A row or a field left out of your block
keeps the shipped value too. A `Memory_Seconds` below 1 or an `Extra_Changes` below 0 is one config error in the console
and the shipped value applies.

| Stars (shipped tiers) | Row | `Memory_Seconds` | `Extra_Changes` | Pieces (`Changes_Needed` 2 + extra) |
|---|---|---|---|---|
| 1-2 | `Easy` | 3.0 | 0 | 2 |
| 3 | `Normal` | 5.0 | 0 | 2 |
| 4 | `Hard` | 10.0 | 1 | 3 |
| 5 | `Deadly` | 20.0 | 2 | 4 |

The row is the `Difficulty` of the tier for your stars in `Cops.Tiers`, so an admin who remaps a tier moves the row with
it. A total above 5 pieces means clothes alone never fool that row.

## 3. Tuning (optional): `By_Difficulty` in `copsncrooks/cops.yml`

To change the scale, paste this block under `Cops` -> `Disguise`, beside `Changes_Needed`, and edit the numbers (block
style, one key per line):

```yaml
      By_Difficulty:
         Easy:
            Extra_Changes: 0
            Memory_Seconds: 3.0
         Normal:
            Extra_Changes: 0
            Memory_Seconds: 5.0
         Hard:
            Extra_Changes: 1
            Memory_Seconds: 10.0
         Deadly:
            Extra_Changes: 2
            Memory_Seconds: 20.0
```

- `Memory_Seconds`: how long after a cop last had eyes on the player a new look can still fool them. At least 1; keep it
  above the AI tick.
- `Extra_Changes`: added to `Changes_Needed`.
- **Nearest to 0.21.5:** every `Extra_Changes` 0 and every `Memory_Seconds` 1 (the floor). 0.21.5 had no memory beyond one
  AI tick (0.5 s) and one flat count, so 1 is as close as the floor allows.
- If the playtest shows the top rows cannot be reached on foot, lower `Hard` and `Deadly` `Memory_Seconds` before touching
  tier `Speed`; if only 3 stars is too hard, `Normal: Memory_Seconds: 4`. The numbers are reasoned, not measured over a long
  play session.

## 4. New message: `Disguise.Lost`

`copsncrooks/wanted_messages.yml` gains `Disguise.Lost`, said once when no cop has seen the player for the memory:

```yaml
   Lost: "&eThe cops lost you. They want: &7%suspect%&e. %changes% of head, chest, legs, boots, on foot / in a car must differ."
```

- It works from the code fallback with no edit. `%suspect%` is what they want, `%changes%` is how many pieces must differ
  at the player's stars. `''` turns the line off (it also tells players the exact moment the memory runs out).
- A deployed `wanted_messages_es.yml` shows the line in **English** until you add the key to it by hand, under `Disguise`:

```yaml
   Lost: "&eLa policía te ha perdido. Busca a: &7%suspect%&e. Deben ser distintos %changes% de: cabeza, pecho, piernas, botas, a pie / en auto."
```

The shipped Spanish file already has it. `wanted.yml` only gains a comment on `Witness_Floor`; nothing to copy.

## 5. Behaviour that changed

- **Recognition lasts `Memory_Seconds`.** A corner, a door or a missed 0.5 s sample no longer ends it. A new look is learned at
  the next sighting, so changing clothes in front of the cops no longer works, and the last watcher dying no longer
  opens the window. Falling or rising stars move the memory and the piece count at once.
- **More cops count as witnesses.** A cop hunting the player counts to `Alert_Range` (40) and, while the cops know the
  player, the bare line is enough: darkness, crowds and sneaking only stop a first pick-up. Any other live cop (a beat
  cop, another player's squad) counts as a patrol does, within `Patrol.Warning.Range` (12). A cop walking home never
  counts. The squad no longer has to exist: a beat cop alone can watch a swap.
- **A hunting cop looks through glass, iron bars and fences** while the cops know the player, as far as `Alert_Range`,
  because the plain sight line stops at glass and bars. A wall, a closed door or leaves hide the player at any range. A
  Marksman on overwatch counts to the larger of `Alert_Range` and his `Sight_Range`.
- **No fire on an unrecognised player.** Cops hold fire at 3 stars and up as well, and keep searching. A vanilla entity
  target is still fired on. Hitting a cop in sight of any cop gives the player away at once and blows a police outfit
  (a 0-star attacker in the outfit is fired back on). Within `Wanted.Witnesses.Cops.Witness_Floor` (6) the cop hit counts with no line at all; that key also moves crime reports. This
  reverses the 0.20.0 rule that filters never stop firing. A beat cop the filters refuse walks past and goes back on its
  beat, and a hit beat cop also goes back when the cops stop hunting the player.
- **A cop's own report starts the memory.** A swap in front of the reporting cop is seen. A civilian's report or the 911
  call does not start it. An **escape ends the memory**; the post-escape search reads EASY.
- **A blown police outfit never fools,** whatever else changed.
- **The cops tell the player when they lose them** (section 4).
- **Night balance.** Role cops (Marksman, SWAT and the other squads) keep their tier's `Ignore_Darkness` at 4 and 5 stars
  again; it had been lost since 0.20.0. Night pursuit at 4-5 stars is harder than in 0.21.5, whatever the disguise.
- **Known disagreements** (docket 866 to 871): the boss bar can read SEARCHING while the memory holds, for a hunting cop
  at night past the sight factor range or behind glass. A breach (4 and more stars, indoors) acts on the
  squad's last-known spot, so an earned disguise indoors can still draw a stack-up.
- **Open for the playtest:** whether the player can get the memory on foot at 3, 4 and 5 stars (DIS-ESCAPE-3/4/5). If not,
  the top rows may only be reachable by car; tune in YAML.
- **Debug:** states are unchanged (`DISGUISE <player> state=seen|outfit|blown|match|mismatch`). The state is logged only on
  a change, so a pass reads as the absence of a mismatch line.

[`migration-0.21.5.md`](./migration-0.21.5.md) stays as history.
