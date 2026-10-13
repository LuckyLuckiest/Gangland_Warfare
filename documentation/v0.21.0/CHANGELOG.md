# 0.21.0 Changelog - Gang & Turf III

[← Back to Documentation Index](../README.md)

Read [migration-0.21.0.md](../migration-0.21.0.md) before you start the server. It lists every new key, node, table and behaviour change.

## Requires

- **Keystone 1.15.3** (as 0.17.0 and 0.19.0). Gangland refuses to start on an older Keystone.
- **gangland-api 2.6** (unchanged) and no module `Host_Api` change. Nothing new to install; update all jars together.
- **Citizens** is needed for Quartermaster placement (without it the command refuses and charges nothing).

## Quartermaster

- **Place your own Quartermaster.** `/glw turf quartermaster` places or moves the Quartermaster where you stand, inside a turf your gang owns. It costs `Powerup_Npc.Placement_Cost` from the gang bank (default `5000.0`, `0` = free) and needs the rank that holds `gangland.turf.upgrade`. Allies cannot place it.
- **Refusals name the owner.** A gangless player or a stranger is told which gang the Quartermaster serves and how to ally (`/glw gang ally request <id>`).
- **Hub read-out.** The hub shows the guards on duty and who may spend (the owner buys, allies look).
- **Gang tag.** A short gang tag (1 to 4 characters) is drawn above each Quartermaster. It refreshes on an owner change or respawn, not on a gang rename.
- **Staff placement.** `/glw turf powerupnpc set` is refused unless the staff member stands inside the selected turf.

## Guards and alerts

- **Guard Orders.** Slot 13 of the Quartermaster hub switches the turf between **Hold** and **Defend**. Hold keeps guards within `Garrison.Hold_Radius` (default `6.0`, minimum `3.0`) of their post and away from cops; the Quartermaster fights back when hit but stays within the hold radius (T-834). Defend is the default. Only the owning gang's upgrade rank can switch it; allies can look. The order is saved and resets to Defend when the turf changes hands.
- **Owner alerts.** Online members of the owning gang get a chat line and an action bar when a cop hits a member on the turf, a cop arrests a member on the turf (T-832), a guard falls, an outsider hits the Quartermaster, or the Quartermaster is killed. A death is told once per NPC; each kind has a per-turf cooldown (`Owner_Alerts.Cooldown_Seconds`, default `60`). Allies and rivals hear nothing.
- **Garrison panel.** Shows guards on duty, the reserve against the cap (`x/12`), the next wave, and "Reserve full" to the owner at the cap.

## Gangs

- **Allies for everyone.** Non-op players can use `/glw gang ally` again: seven `gangland.command.gang.ally*` nodes default to `true`. The gang rank still gates each command; `request`, `accept`, `reject` and `pending` need the `gangland-mail` module.

## Config and permissions

- **New keys.** `turf/turf_npcs.yml`: `Powerup_Npc.Placement_Cost`, `Garrison.Hold_Radius`, `Owner_Alerts.Cooldown_Seconds`. `turf/turf_messages.yml`: the `Quartermaster.*` placement, hub, order and garrison keys and the five `Alerts.*` keys (full list in the migration page). `Quartermaster.Only_Owner_Or_Allies` is retired; copy any custom text into `Refuse_No_Gang` and `Refuse_Stranger`.
- **New permissions.** `gangland.command.turf.quartermaster` and the seven `gangland.command.gang.ally*` nodes, all default `true` and children of `gangland.command.player` (negating that parent removes them too).
- **Database.** `turf_garrison` gains a `hold` column at first boot. No SQL to run.

## Upgrade notes

- Read [migration-0.21.0.md](../migration-0.21.0.md) after [migration-0.17.0.md](../migration-0.17.0.md) and [migration-0.19.0.md](../migration-0.19.0.md).
- `settings.yml` and the shipped menus regenerate on first boot; your previous copies are kept as `*-old.yml` so you can merge your edits back.
- Existing `turf_npcs.yml` and `turf_messages.yml` are not rewritten. A missing key uses the shipped default, so copy a block into your file only to customise it.

## Fixed

- **Earlier fixes included.** The 0.17.1 fixes (T-307, T-279, T-825, T-826): non-op gang owners can disband and every online member is paid; the player command nodes for bank, balance, teleport, respawn, wanted, contact, filter, resource, car, grapple and jetpack default to true.
- **Gang & Turf II included.** The whole 0.19.0 release (waves, levels, safehouses, friendly-fire rules, fights).
- **Waypoints (T-827, T-828).** Players may list, inspect, select and deselect waypoints, and `/glw waypoint select <name>` works by name.
- **Quartermaster naming and parity (T-800, T-801, T-802).** The tag, nameplate and owner-naming refusals work, and the fallback texts in code match the shipped YAML.
- **Placement (T-804).** A placement that cannot spawn refunds the gang, tells the player, and no longer leaves a ghost Quartermaster that reappears after a restart.
- **Ally commands (T-808).** Players can use `/glw gang ally` without being operators.
- **Staff placement (T-803).** `/glw turf powerupnpc set` is refused outside the selected turf.
- **Garrison panel (T-822, T-823).** The panel shows the reserve and the next wave, and the owner sees "Reserve full" at the cap.
- **Owner alerts (T-820, T-821 partly, T-829).** Owners are told when the Quartermaster is attacked or killed and when a guard falls. The death alerts now fire in the live game; they used to look up the NPC body after it had despawned.
- **Hold radius (T-824).** `Garrison.Hold_Radius` below 3 is raised to 3, and a non-finite value falls back to the default, so a held guard no longer ignores its attacker.
- **Placement cost (T-810).** A non-finite `Placement_Cost` no longer breaks placement.
- **Arrest alert (T-832).** Owners are alerted when a member is arrested on their turf (owner ruling).
- **Hold leash (T-834).** Under Hold the Quartermaster fights back but stays within its hold radius instead of chasing out of sight (owner ruling).
- **Cop protection (T-835).** Each Bartizan hit is now handled once by the turf cop-protection listener, not twice.
- **Spawn log (T-833).** The turf-NPC restart log reports spawned and pending-for-chunk-load counts instead of a misleading `Spawned 0`.

## Known issues

- T-805 a Quartermaster re-placed during a contest is never engaged.
- T-806 a Quartermaster lost on chunk unload stays gone until restart.
- T-807 the refusal hint names a `gangland-mail` command, which is absent without that module.
- T-809 wording nits: alias versus gang name in hub lines, `Paid $0` and `Cost $0`, and the gang tag does not refresh on a gang rename.
- T-811 traders can stay unspawned until restart after a first-join database lock (`TraderManager` loads on the main thread).
- T-821 guards do not engage a cop that hits the Quartermaster (the owner is told, but the guards do not answer).
- T-804 residual: a database error thrown during placement, after the gang is charged, loses the price.
- T-727 fight escrow is memory-only: a crash in the middle of a fight loses both stakes.
- T-707 `Ranks.Minimum.Chat` and `gangland.gang.chat` are not enforced on gang and ally chat.
- T-708 rally join has no rank gate.
- T-710 `Ranks.Minimum` Upgrade=owner does not stop officers, who hold `gangland.turf.upgrade` through the preset.

## API

- **`gangland-api` 2.6** is unchanged: no removal, rename or signature change, and no module `Host_Api` change.
