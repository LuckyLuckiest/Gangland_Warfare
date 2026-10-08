# Migrating a server from 0.16.0 to Gangland 0.16.1

[← Back to Documentation Index](./README.md)

Gangland 0.16.1 is a patch release: the wanted HUD, the cop and jail commands, the setup list, the radio names and tab
completion. Every new key has a default, so an old config keeps working. The notes below cover the keys whose text moved,
which a customised file keeps on upgrade without a warning.

## 1. Update the dependencies

- **Keystone 1.15.1 is required.** Replace `plugins/Keystone-1.15.0.jar` with `Keystone-1.15.1.jar`. Gangland 0.16.1 refuses
  to start on an older Keystone: the console says `Gangland 0.16.1 needs Keystone 1.15.1 or newer; found 1.15.0` and the
  plugin disables itself instead of failing half-way through boot. The tab-completion change (see the changelog) lives in
  Keystone 1.15.1 as well.

## 2. Keys that moved or stopped being read

The server keeps its old files, so an edit made in one of these keys has no effect after the upgrade. The plugin shows the
new default and logs nothing about it. Check each row and copy your text to its new home.

| What you changed | 0.16.0 location | Now read from | Notes |
|---|---|---|---|
| The wanted title and subtitle | `copsncrooks/wanted_messages.yml` `Hud.Title` | `copsncrooks/wanted.yml` `Wanted.Hud.Title.<event>.Title` and `.Subtitle` (`Gain`, `Lost`, `Escaped`) | `Hud.Title` in `wanted_messages.yml` is ignored. Each event has its own fades too. To get the old look back, see the [0.16.1 changelog](./v0.16.1/CHANGELOG.md). |
| The cop, jail and cuff command replies | core `message_en.yml` / `message_es.yml` (`Cuff.*`, `Jail.*`, `Cop.No_Chased`, `Cop.Target_Not_Chased`, `Cop.Spawner.*`, `Spawner_List_*`, ...) | `copsncrooks/commands.yml` (English) and `copsncrooks/commands_es.yml` (Spanish, used when `Settings.Language` is `es`) | The core keys are no longer read by these commands. `commands_es.yml` replaces `commands.yml` whole, so copy your edits into the file for your language. |
| The setup wand's usage line | `copsncrooks/setup.yml` `Setup.Messages.Usage` | `Setup.Messages.Arguments_Missing` | `Usage` is dead. The list rows now use `Row_Label` (with `%kind%`), `Row_Where` and `Row_Tp`. |
| How a cop is named on the radio | `cops.yml` `Cops.Names.Format` | `cop_radio_messages.yml` (and `_es`) `Speaker_Name` | `Names.Format` now only drives the hologram name. |

The wanted AUTO drop cards `Drop_Petty` and `Drop_Cold_Trail` changed wording in `copsncrooks/wanted_messages.yml`. A
customised value stays as it is. Only the default text changed: "Small fry, the heat is off you for now" and "The trail is
fading" (the search can still be on after an escape).

## 3. Setup list rows and the console

`/glw cop setup list` now shows the kind in each row (`- station #1 HQ`). A player sees the world and coordinates in the
hover over the row. The console cannot see a hover, so it gets the row and the place as one plain line.

## 4. Help

`/glw cop list [player]` (the player is optional) and `/glw cop setup list [station|region|point]` are the usages in the
help page.

## 5. New keys

Every key below has a code default, so a file that lacks it still works. Copy one into your file only to change its text
or value.

- **`copsncrooks/wanted.yml`**
  - `Wanted.Post_Escape.Enable` (`true`), `.Search_Seconds` (`120`), `.Spotted_Stars` (`1`), `.Announce` (`true`).
  - `Wanted.Hud.Title.Gain`, `.Lost` and `.Escaped`, each with `.Enable`, `.Title`, `.Subtitle`, `.Fade_In`, `.Stay` and
    `.Fade_Out`.
  - `Wanted.Hud.Bounty.Enable` (`true`) and `.Bar_Color` (`YELLOW`).
- **`copsncrooks/wanted_messages.yml`**
  - `Hud.Bar.Bounty` and `Hud.Bar.Bounty_None`.
  - `Hud.Announce.Bounty`, `.No_Bounty`, `.Gave_Up` and `.Gave_Up_Bounty`.
  - `Crimes.Spotted` (`Spotted by the cops`). A file without it still shows that text.
- **`copsncrooks/cop_radio_messages.yml`** and **`cop_radio_messages_es.yml`**: `Speaker_Name` (`{rank} {role} &f{name} &7#{number}`).
- **`turf/turf_npcs.yml`**: the `Cop_Response` block (`Enabled` `true`, `Targeting_Radius` `32.0`, `Include_Allies` `true`)
  and `Powerup_Npc.Targeting_Radius` (`32.0`). An existing server does not get the `Cop_Response` block on upgrade: see
  [configuration.md](./developer/configuration.md#turf_npcsyml-turfturf_npcsyml) for the YAML to add.
- **`copsncrooks/setup.yml`**: `Setup.Messages.Arguments_Missing`, `List_Header`, `Kind_All`, `Row_Label`, `Row_Where` and
  `Row_Tp`.
- **New files**: `copsncrooks/commands.yml` and `copsncrooks/commands_es.yml` (the replies of `/glw cop`, `/glw jail` and
  `/glw cuff`).
