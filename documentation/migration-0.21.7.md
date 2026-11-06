# Migrating a server from 0.21.5 or 0.21.6 to Gangland 0.21.7

[← Back to Documentation Index](./README.md)

0.21.7 makes bought guards stand on the turf, adds three guard powerups and gives the Quartermaster panels a border and
readable lore. **An existing server keeps its old files**, and the code never rewrites them. Everything works without an
edit except the new powerups, which you must copy in if you want them on the panel.

## 1. Swap the jars

Replace the Gangland core jar and, in `plugins/Gangland_Warfare/modules/`, the `gangland-turf` and `cops-n-crooks` jars with
the 0.21.7 builds. Swapping every module jar together is harmless. Keystone stays at 1.15.5, and Bartizan does not change.

## 2. What changes on your server at first boot

- `turf_garrison.count` now means guards **owned**, standing or in reserve. No data changes. A turf with stock 10 shows
  **5 guards standing** on its posts once its area is loaded, and 5 in reserve. You do not need a contest to see them.
- Only a guard killed during a contest leaves the stock. A guard lost to a chunk unload, a `/glw reload` or a restart comes
  back free as soon as its area loads. A guard killed with no contest running comes back free after
  `Garrison.Wave_Cooldown_Seconds`.
- A kill with no contest running pays no bounty. A kill during a contest still pays the bounty and leaves the stock.
- Guards no longer return to a reserve after a contest, so the "back in the reserve" line is gone. The guards that are left
  walk home and stay at their posts.
- Standing guards answer a cop who hits a gang member on the turf, at any time, not only during a contest. The Hold order
  keeps them off cops.
- Guard Toughness reaches guards already standing, and health falls back when it ends without killing anyone.
- `Garrison.Max_Reserve` counts guards owned, so guards on duty no longer count twice.

To keep the old behaviour, where guards appear only in a contest, add `Standing_Cap: 0` under `Garrison:` in
`turf/turf_npcs.yml`.

## 3. Copy in the new powerups (`turf/turf_powerups.yml`)

The panel will not show the three new powerups until you add them. The code never merges keys into a file that already
exists. Add these under `Powerups:`, or delete the file to regenerate it (this loses your price edits):

```yaml
   # Guards have 1.5 times the health while this runs, standing ones too. Current health keeps its share, and when it
   # ends health falls back without ever killing a guard.
   guard_toughness:
      Display_Name: "&cGuard Toughness"
      Icon: IRON_BLOCK
      Description:
         - "&7Your guards, standing or new,"
         - "&7are tougher while this runs."
      Cost: 2000
      Effect_Type: GUARD_TOUGHNESS
      Magnitude: 1.5
      Duration_Seconds: 1800
   # 1.5 times Wave_Size and Max_Alive during a contest. Spends only guards already bought.
   bigger_guard_waves:
      Display_Name: "&6Bigger Guard Waves"
      Icon: BELL
      Description:
         - "&7More of your bought guards fight"
         - "&7at once during a contest."
      Cost: 3000
      Effect_Type: BIGGER_GUARD_WAVES
      Magnitude: 1.5
      Duration_Seconds: 1800
   # Half the wait before replacement guards arrive, in a contest and outside one.
   faster_reinforcements:
      Display_Name: "&bFaster Reinforcements"
      Icon: CLOCK
      Description:
         - "&7Replacement guards arrive"
         - "&7sooner after one falls."
      Cost: 1500
      Effect_Type: FASTER_REINFORCEMENTS
      Magnitude: 0.5
      Duration_Seconds: 1800
```

Optional: add a `Limits:` section at the top level of the same file (next to `Powerups:`, not inside it). Code defaults apply
until you do:

```yaml
Limits:
   # Most health multiplier from Guard Toughness. Minimum 1.0. Total health never passes 1024.
   Max_Guard_Toughness: 3.0
   # Most multiplier on Wave_Size and Max_Alive from Bigger Guard Waves. Minimum 1.0.
   Max_Guard_Waves: 2.0
   # Shortest wait as a share of Wave_Cooldown_Seconds from Faster Reinforcements. Between 0 and 1.
   Min_Reinforcement_Wait: 0.25
```

The three existing entries (`small_income_boost`, `large_income_boost`, `garrison_discount`) get no icon or description
from your old file. They show the default icon for their effect (`GOLD_INGOT` for income boosts, `EMERALD` for the discount)
and no lore line until you add `Icon:` and `Description:` to them. The header comment of the shipped file lists the three
new `Effect_Type` values; copying it is optional.

## 4. Optional edits

- `turf/turf_settings.yml`, for other pane colours (the defaults are the shipped ones):

      Menu:
         Border_Item: BLACK_STAINED_GLASS_PANE
         Fill_Item: GRAY_STAINED_GLASS_PANE

  Any XSeries material name works. A name that does not match falls back to the default pane.

- `turf/turf_npcs.yml`: `Standing_Cap: 5` under `Garrison:`. It is the default. Above 5, guards share posts. Raise
  `Max_Alive` (the ceiling, 6 by default) with it.
- `items/money.yml`: set `Money.Drop_Sources.COP.Enabled` to `false` if your file still has it on. Guards now answer cops in
  peace and respawn free, so that source would pay cash for every cop they kill.

## 5. `turf/turf_messages.yml`

New keys need no edit: the code supplies their text. To get the new look and wording, **delete** these lines and the code
default applies: `Quartermaster.Title_Menu`, `Title_Buffs`, `Title_Garrison`, `Place_Title`, `Buffs_Duration`,
`Garrison_Next_Wave`, `Garrison_Buy_Full`, `Map.Hover_Guards` (the old line calls guards owned "in reserve").

Dead keys, safe to delete: `Quartermaster.Buffs_Effect`, `Garrison_Stock`, `Garrison_Reserve`, `Reserve_Full`,
`Garrison.Returned`. In `turf/turf_npcs.yml`, `Defender.Lifespan_Seconds` is no longer read, so you may delete it.

New keys and their code defaults. Each one is optional in your file:

| Key | Default text |
|---|---|
| `Quartermaster.Buffs_Does` | `&7Effect: %effect%` |
| `Quartermaster.Effect_Income` | `&a+%percent%% &fturf income` |
| `Quartermaster.Effect_Discount` | `&a%percent%% off &fnew guards` |
| `Quartermaster.Effect_Toughness` | `&a+%percent%% &fhealth on your guards while it runs` |
| `Quartermaster.Effect_Waves` | `&a+%percent%% &fguards fighting at once in a contest` |
| `Quartermaster.Effect_Reinforcements` | `&fReplacements arrive &a%percent%% &fsooner` |
| `Quartermaster.Buffs_Active` | `&aActive: &f%time% &aleft` |
| `Quartermaster.Buffs_Stack` | `&7Buying again stacks, up to a limit.` |
| `Quartermaster.Garrison_Owned` | `&7Owned: &f%count%&7/&f%max%` |
| `Quartermaster.Garrison_In_Reserve` | `&7In reserve: &f%count%` |
| `Quartermaster.Garrison_Where_One` | `&7Guards stand on the turf while the` |
| `Quartermaster.Garrison_Where_Two` | `&7area is loaded. The rest wait in reserve.` |
| `Quartermaster.Bought_Guard_Post` | `&7It takes its post on &f%turf% &7shortly while the area is loaded.` |
| `Quartermaster.Bought_Guard_Reserve` | `&7It waits in the reserve and joins when &f%turf% &7is attacked.` |
| `Quartermaster.Owned_Full` | `&cThis turf already has %max% guards.` |

Chat after a purchase: `Bought_Guard` is followed by `Bought_Guard_Post` while the guard fits a post (`Standing_Cap`), and
by `Bought_Guard_Reserve` once every post is taken. Your old `Bought_Guard` text still reads correctly.

## 6. Rolling back

Put the previous jars back. Stock is untouched. Running buffs of the new types are skipped until they expire.
