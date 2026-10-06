# Migrating a server from 0.15.0 to Gangland 0.15.1

[← Back to Documentation Index](./README.md)

Gangland 0.15.1 moves every `settings.yml` key that only one runtime module reads into that module's own YAML. No
gameplay changes. On first boot your `settings.yml` is replaced by a fresh one (section 1); moved keys you had tuned
keep their values for now because the modules read them from the backup Keystone keeps (section 2), but **core keys you
had tuned are back at their defaults until you copy them into the new `settings.yml`**.

## 1. Update

Replace the core jar and the module jars together. Modules declare `Host_Api: 2.2` (the module API gained
`MovedSetting`, see [gangland-api.md](./gangland-api.md)); a 2.2 module does not load on a 0.15.0 core.

`settings.yml` carries `Config_Version: '${project.version}'`, so the version bump makes Keystone, on the first 0.15.1
boot and before any module reads, move your 0.15.0 file to `settings-old.yml` and write a fresh 0.15.1 `settings.yml`
from the jar. An existing backup is never overwritten: if `settings-old.yml` is already there, the new backup is
`settings-old (1).yml` (then `(2)`, ...). The fresh file holds the shipped defaults and none of the moved keys.

- **Core keys** (everything that stays in `settings.yml`): copy any value you customised from the backup into the new
  `settings.yml`. Nothing reads the backup for these.
- **Moved keys**: copy any value you customised from the backup into the module file listed in section 3. Until you
  do, the module still uses the backup's value (section 2).

## 2. The fallback rule

For each moved key the module reads its own file first. The legacy value is the old path in `settings.yml` if that
file still sets it, otherwise the old path in the newest `settings-old*.yml` in `plugins/Gangland_Warfare/` (newest by
modification time; read once per config load, ignored with a warning if it does not parse). If the legacy value differs
from the default while the module file is absent or at the default, the legacy value is used and the console warns once
per key, naming the file it came from:
`plugins/Gangland_Warfare/settings-old.yml still sets '<legacy path>', which moved to plugins/Gangland_Warfare/<file> ...`.
Copy the value into the module file (once it differs from the default, the module file wins) and delete it from
`settings.yml` if it is there; deleting the backup also ends the fallback. The legacy read stops in a later release.

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
| `NPC_Navigation.*` | `copsncrooks/cops.yml` | `Cops.Navigation.<key>` |
| `NPC_Navigation.Recalculation_Ticks` | `copsncrooks/cops.yml` | `Cops.Navigation.Recalculation_Ticks` |
| `NPC_Navigation.Min_Repath_After_Loss_Ticks` | `copsncrooks/cops.yml` | `Cops.Navigation.Min_Repath_After_Loss_Ticks` |
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
