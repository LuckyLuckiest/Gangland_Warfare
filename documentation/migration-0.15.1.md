# Migrating a server from 0.15.0 to Gangland 0.15.1

[← Back to Documentation Index](./README.md)

Gangland 0.15.1 moves every `settings.yml` key that only one runtime module reads into that module's own YAML. No
gameplay changes. Nothing breaks if you do nothing about it: the modules still honour an edited `settings.yml` value
for now (section 2).

## 1. Update

Replace the core jar and the module jars together. Modules declare `Host_Api: 2.2` (the module API gained
`MovedSetting`, see [gangland-api.md](./gangland-api.md)); a 2.2 module does not load on a 0.15.0 core.

`settings.yml` carries `Config_Version: '${project.version}'`, so the version bump itself makes Keystone rename your old
file to `settings-old.yml` and write a fresh one on first boot. Nothing is overwritten without a backup. The fresh file
no longer holds the moved keys, so **copy any value you customised from `settings-old.yml` into the module file below**.

## 2. The fallback rule

For each moved key the module reads its own file first and also looks at the legacy `settings.yml` path. If
`settings.yml` still sets the key to a non-default value while the module file is absent or at the default, the
`settings.yml` value is used and the console warns once per key:
`settings.yml still sets '<legacy path>', which moved to plugins/Gangland_Warfare/<file> ...`. Copy the value across and
delete it from `settings.yml`; the legacy read stops in a later release.

`%glw_settings_*%` placeholders and `/glw debug` entries for moved keys keep reporting the `settings.yml` value. The
matching `Settings` getters are deprecated; do not rely on them for a module's real value.

## 3. Where each key went

All module-file paths are relative to `plugins/Gangland_Warfare/`. Unless a Path is shown, it equals the legacy path.

### cops-n-crooks

| Legacy path (`settings.yml`) | File | Path |
|---|---|---|
| `Wanted.Kill_Combo.*` (`Enable`, `Reset_After`, `Kill_Counter`) | `copsncrooks/wanted.yml` | same |
| `Cops.Count.*`, `Cops.Behaviour.*`, `Cops.Spawn.*`, `Cops.Pursuit.*`, `Cops.Return.*` | `copsncrooks/cops.yml` | same |
| `Detainment.Transit.Guard_Radius` | `copsncrooks/cops.yml` | `Cops.Behaviour.Guard_Radius` |
| `NPC_Navigation.*` (all but `Recalculation_Ticks`, `Min_Repath_After_Loss_Ticks`) | `copsncrooks/cops.yml` | `Cops.Navigation.<key>` |
| `Detainment.Jail.*`, `Transit.Delay_Ticks`, `Break_Free.*`, `Handcuff_Bribe.*`, `Bail.*`, `Jail_Bribe.*`, `Sentence.*`, `Fallback_Exit_Waypoint`, `Sounds.*` | `copsncrooks/detainment.yml` | same |

### gangland-civilians

| Legacy path | File | Path |
|---|---|---|
| `Civilians.<Section>.<key>` (`Behaviour`, `Spawn`, `Spawner_Proximity`) | `npc/civilians.yml` | `<Section>.<key>` |
| `NPC_Navigation.*` | `npc/civilians.yml` | `Navigation.<key>` |

### gangland-turf

| Legacy path | File | Path |
|---|---|---|
| `Turf.*` (income, wand, visualization, `Capture.*` incl. sounds, `Contribution.Points.*`) | `turf/turf_settings.yml` | drop the `Turf.` prefix |

### gangland-gang

| Legacy path | File | Path |
|---|---|---|
| `Gang.Name_Duplicates`, `Display_Name_Char`, `Rank.*`, `Account.*` | `gang/gang_settings.yml` | drop the `Gang.` prefix |

`Gang.Enable` stays in `settings.yml`: gangland-civilians still reads it.

### gangland-gadget

| Legacy path | File | Path |
|---|---|---|
| `Gadgets.Jetpack.*`, `Gadgets.Car.*` | `gadget/gadget_settings.yml` | same |

### gangland-npc-shops

| Legacy path | File | Path |
|---|---|---|
| `User.Bank.Rename_Fee` | `npc/banker_settings.yml` | `Rename_Fee` |

## 4. For module authors

`gangland-api` 2.2 adds `MovedSetting` (module file first, legacy `settings.yml` fallback with a one-time warning) and
the `WantedKillTracker`/`WantedKillTrackers` changes; see [gangland-api.md](./gangland-api.md), "Api 2.2 (0.15.1)".
