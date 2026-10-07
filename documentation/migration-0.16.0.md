# Migrating a server from 0.15.x to Gangland 0.16.0

[← Back to Documentation Index](./README.md)

Gangland 0.16.0 is "Where they come from" for Cops N Crooks: the police have stations and arrive after an ETA, squads mix
tiers and take a breather after a wipe, a suspect who breaks contact meets posted cops and a hand-off, hideouts and a cold
trail speed the chase clock up, and crooked contacts, a hospital and a respawn shield round out the loop. Every new key has
a default, so an old config keeps working; the notes below say what changes and what you must do to get the new behaviour.

## 1. Update the dependencies

- **Keystone 1.15.0 is required.** Replace `Keystone-<version>.jar` in `plugins/`. Gangland 0.16.0 uses the out-of-sight
  spawn helper and the hold-post API added in 1.15.0, so on an older Keystone it **refuses to start**: the console says
  `Gangland 0.16.0 needs Keystone 1.15.0 or newer; found 1.14.0` and the plugin disables itself instead of failing
  half-way through boot with a `NoSuchMethodError`. Keystone 1.15.0 only adds API (it is additive).
- **Bartizan** is unchanged (0.6.0 is the pin; later 0.6.x works). `cops-n-crooks` still declares `Plugins: [Bartizan]`.
- **Module jars.** Replace every jar in `plugins/Gangland_Warfare/modules/` with the 0.16.0 builds. `cops-n-crooks` and
  `gangland-turf` now declare `Host_Api: 2.3` (the module API gained the region SPI, see [gangland-api.md](./gangland-api.md)),
  so they need a 0.16.0 core and **do not load** on 0.15.x (`module.host.incompatible`). The other modules keep their `2.0` or
  `2.2` line and load as they are, but replace them anyway so the whole set is one release. Upgrade the core jar and the
  modules together.
- Citizens is unchanged (2.0.42 or newer recommended).

## 2. What happens on first boot

- **`settings.yml` and the message files are regenerated.** They carry the plugin version in `Config_Version`, so on the
  first 0.16.0 boot Keystone renames your old file to a `-old` backup (`settings-old.yml` and one for each message file) and
  writes a fresh one from the jar, exactly as in 0.15.1. **Core keys you had tuned are back at their defaults until you copy
  them from the backup.** The fresh files carry the new keys (below) and the eight new message lines; the module files and
  `items/money.yml` are never rewritten.
- **New file:** `copsncrooks/setup.yml` (the setup wand) is copied from the cops-n-crooks jar. The other module files
  (`cops.yml`, `cop_roles.yml`, `cop_radio_messages(_es).yml`, `wanted.yml`, `wanted_messages.yml`) keep your values and gain
  nothing: a key they lack reads its shipped default (the code carries it), so the 0.16.0 behaviour is **on** after the
  upgrade. To see and tune the new blocks, copy them from the module jar's file (open the jar, or rename your file and let
  the plugin write a fresh one) into yours: `Cops.Dispatch`, `Breather`, `Handoff`, `Perimeter` in `cops.yml`;
  `Wanted.Evasion.Hideout`, `Quiet_Speed`, `Max_Speed`, `Auto.Rampage_Min_Weight` and `Wanted.Bribe_Stars` in `wanted.yml`;
  `Bribe_Star.*` in `wanted_messages.yml`. Every key is in the [Configuration Reference](./developer/configuration.md).
- **Database.** New tables `cop_station`, `cop_region` and `cop_point`, and a new column `cop_spawner.station_id`, are added
  automatically (SQLite and MySQL). Nothing is rewritten and no row is lost.

## 3. What changes for your players

- **Police arrive from stations, but only after you place some.** Until an admin places a station with the setup wand
  (`/glw cop setup`), every world behaves as in 0.15 (units spawn around the suspect at once). Place a station, and the cop
  spawners within `Cops.Dispatch.Station_Radius` (32 blocks) join it; see [Cops N Crooks](./features/cops-n-crooks.md).
- **Mixed squads need the new `cop_roles.yml` entries.** A kept `cop_roles.yml` has no `@tier` suffixes, so its squads stay at
  one tier per star (and five stars keeps the four-star squad). Copy the 3, 4 and 5 star lines of the bundled file to get
  mixed tiers; to switch the mixing off again, remove the `@` suffixes (`"Marksman@3"` becomes `"Marksman"`).
- **Radio.** A server whose `cops.yml` has its own `Cops.Radio.Priority` list still gets the two new dispatch lines
  (`Dispatch_En_Route`, `Wipe_Refill`) added to it; the rest of your list is kept. A `cop_radio_messages.yml` from before 0.16
  lacks the six new lines and the place words: the plugin speaks its English built-in text for them (a Spanish file needs the
  keys copied from the bundled `_es` file for Spanish lines), and your own `Dispatch_Wanted` and `Contact_Lost` text is kept
  (without the place).
- **Hospitals.** Nothing changes until an admin makes a hospital: `/glw waypoint create <name>` then
  `/glw waypoint type <name> hospital`, and (for the downed screen) `User.Death.Respawn.Enable: true`. With a hospital in the
  world, deaths respawn at the nearest one. **Even with no hospital, the death bill now follows the "one bill" rule** (charged
  when a downed player respawns, with the new "Ward bill" line), unless you set `User.Death.Hospital.Enable: false`.
- **The hospital respawn shield is on.** `User.Death.Hospital.Shield_Seconds` is 5 in the fresh `settings.yml`, and a
  `settings.yml` that lacks the key (an old file you copied back) reads the same code default 5. Add
  `Shield_Seconds: 0` to turn it off.
- **Player cash drop is off for new installs.** The bundled `items/money.yml` now has `Money.Drop_Sources.PLAYER.Enabled: false`
  (one death costs one bill). Your existing `money.yml` is not rewritten; set the key to `false` there to match.
- **Self-defence is tuned.** The fixed 30 second window is now `Wanted.Self_Defence.Window_Seconds` (8), with `Min_Damage`
  (2.0), a provocation check and a `Pair_Cooldown_Seconds` (600) per pair. To loosen it toward 0.15, set
  `Window_Seconds: 30`, `Min_Damage: 0` and `Pair_Cooldown_Seconds: 0` (the provocation check stays).
- **Bounty takedowns.** Killing a target whose posted bounty is below `Bounty.Takedown_Minimum` (100) is now an ordinary
  crime (docket WB-48). Set it to 0 to drop only that minimum (the pair cooldown and the limit of three crime-free
  takedowns per hour stay).
- **Crooked contacts.** `/glw contact [stars]` and the phone's Contacts page wipe stars for `Wanted.Contacts.Price_Per_Star`
  (1000) while no cop sees you; the `[WANTED]` sign's remove and clear actions follow the same gate and cooldown. Grant the
  permission `gangland.command.contact` the way you grant `/glw bounty` (there is no default), or set
  `Wanted.Contacts.Enable: false`.
- **Rampage.** In `Drop_Mode: AUTO` only crimes of at least 80 heat count toward a rampage opening (`Auto.Rampage_Min_Weight`);
  set it to 0 for the 0.15.2 count.
- **Ids no longer collide (docket T-180).** A cop spawner, civilian spawner or jail in a world that was not loaded when
  Gangland started now keeps its id: new rows take the next free id instead of overwriting the unloaded world's row. Nothing
  to do; rows already lost to the old bug are not recovered.
- **Permissions to grant.** `gangland.command.cop.setup` (admins), `gangland.command.contact` (players).

## 4. New settings, one place

| File | New keys |
|---|---|
| `settings.yml` | `Wanted.Self_Defence.*`, `Wanted.Contacts.*`, `Bounty.Takedown_Minimum`, `User.Death.Hospital.Enable` and `.Shield_Seconds` |
| `copsncrooks/cops.yml` | `Cops.Dispatch.*`, `Cops.Breather.*`, `Cops.Handoff.*`, `Cops.Perimeter.*`, six radio cooldowns, two `Radio.Priority` entries |
| `copsncrooks/cop_roles.yml` | `Squad_Composition` entries with `@<tier>`, a five-star squad |
| `copsncrooks/wanted.yml` | `Wanted.Evasion.Hideout.*`, `Quiet_Speed.*`, `Max_Speed`, `Auto.Rampage_Min_Weight`; `Wanted.Bribe_Stars.*` |
| `copsncrooks/wanted_messages.yml` | `Bribe_Star.Taken`, `Bribe_Star.Seen` |
| `copsncrooks/cop_radio_messages(_es).yml` | `Dispatch_En_Route`, `Wipe_Refill`, `Handoff`, `Post_Up`, `Eyes_On`, `Returning_To_Patrol`, `Unknown_Place` |
| `copsncrooks/setup.yml` (new) | `Setup.Wand.*`, `Setup.Outline.*`, `Setup.Messages.*` |
| `message/message_en.yml`, `_es.yml` | `Wanted_Level.Contact.*` (6), `Death.Ward_Bill`, `Death.Hospital_Shield` |

Gangland-api 2.3 and `Host_Api: 2.3` are described in [gangland-api.md](./gangland-api.md) and
[module-loader.md](./module-loader.md).

## 5. Known limits you will meet

Dying still resets all stars, hospitals can be camped once the 5 second shield ends, cars and ender pearls outrun posts, and
waypoint teleports still escape under the waypoint rules; see "Known Limits" in the
[Cops N Crooks guide](./features/cops-n-crooks.md).

## 6. Downgrading to 0.15.x

Possible, with three rules:

1. **Turn every hospital back into another type first** (`/glw waypoint type <name> global`). 0.15 does not know the type
   `hospital`, so a waypoint saved with it fails to load on the old build.
2. Put the 0.15 core and module jars back together; the `Host_Api: 2.3` modules (cops-n-crooks, gangland-turf) do not load on a
   0.15 core. Keystone 1.15.0 can stay.
3. The new tables and the `station_id` column stay in the database and are ignored by the old build; stations, regions and
   points are kept for a later upgrade. Restore the `settings-old.yml` backup if you want your 0.15 `settings.yml` back.
