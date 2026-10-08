# Migrating a server from 0.16.0 to Gangland 0.16.1

[← Back to Documentation Index](./README.md)

Gangland 0.16.1 is a patch release: the wanted HUD, the cop and jail commands, the setup list and the radio names. Nothing
needs a new dependency. Every new key has a default, so an old config keeps working. The notes below cover the three
keys whose text moved, which a customised file keeps on upgrade without a warning.

## 1. Keys that moved or stopped being read

The server keeps its old files, so an edit made in one of these keys has no effect after the upgrade. The plugin shows the
new default and logs nothing about it. Check each row and copy your text to its new home.

| What you changed | 0.16.0 location | Now read from | Notes |
|---|---|---|---|
| The wanted title and subtitle | `copsncrooks/wanted_messages.yml` `Hud.Title` | `copsncrooks/wanted.yml` `Wanted.Hud.Title.<event>.Title` and `.Subtitle` (`Gain`, `Lost`, `Escaped`) | `Hud.Title` in `wanted_messages.yml` is ignored. Each event has its own fades too. To get the old look back, see the [0.16.1 changelog](./v0.16.1/CHANGELOG.md). |
| The cop, jail and cuff command replies | core `message_en.yml` / `message_es.yml` (`Cuff.*`, `Jail.*`, `Cop.No_Chased`, `Cop.Target_Not_Chased`, `Cop.Spawner.*`, `Spawner_List_*`, ...) | `copsncrooks/commands.yml` (English) and `copsncrooks/commands_es.yml` (Spanish, used when `Settings.Language` is `es`) | The core keys are no longer read by these commands. `commands_es.yml` replaces `commands.yml` whole, so copy your edits into the file for your language. |
| The setup wand's usage line | `copsncrooks/setup.yml` `Setup.Messages.Usage` | `Setup.Messages.Arguments_Missing` | `Usage` is dead. The list rows now use `Row_Label` (with `%kind%`), `Row_Where` and `Row_Tp`. |

The wanted AUTO drop cards `Drop_Petty` and `Drop_Cold_Trail` changed wording in `copsncrooks/wanted_messages.yml`. A
customised value stays as it is. Only the default text changed: "Small fry, the heat is off you for now" and "The trail is
fading" (the search can still be on after an escape).

## 2. Setup list rows and the console

`/glw cop setup list` now shows the kind in each row (`- station #1 HQ`). A player sees the world and coordinates in the
hover over the row. The console cannot see a hover, so it gets the row and the place as one plain line.

## 3. Help

`/glw cop list [player]` (the player is optional) and `/glw cop setup list [station|region|point]` are the usages in the
help page.
