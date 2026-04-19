# Commands — Smoke-Test Checklist

[Back to Test Index](./README.md) | [Command System Doc](../developer/commands.md)

---

## Overview

Every subcommand lives under the `/glw` (alias `/gangland`) dispatcher. The authoritative list is
[`commands.json`](../../gangland-impl/src/main/resources/commands.json); per `feedback_commands_json`, any new command
must appear there.

This checklist spot-tests one golden-path invocation per subcommand tree. A "DB" tag means the command touches the
database and must also be re-verified after `/glw reload data`. Per `feedback_optional_arguments`, positional user
input must be chained `OptionalArgument` nodes with tab-completion — verify tab-completion on every variadic command.

---

## General

| Command                  | Golden-path test                                                                | DB  |
|--------------------------|---------------------------------------------------------------------------------|-----|
| `/glw help`              | Responds with the main help page; every top-level verb is listed.               | —   |
| `/glw help <page>`       | Page 1 and page 2 both render.                                                  | —   |
| `/glw reload`            | Completes without error. See [lifecycle-and-reload](./lifecycle-and-reload.md). | —   |
| `/glw reload files`      | Edits to `settings.yml` become visible.                                         | —   |
| `/glw reload data`       | Caches drop and re-read.                                                        | Yes |
| `/glw reload scoreboard` | New `scoreboard.yml` values render.                                             | —   |
| `/glw update`            | Reports current version + latest release.                                       | —   |
| `/glw update download`   | Skip on a dev server — only test on the designated release smoke server.        | —   |
| `/glw resource`          | Prompts the player to download the resource pack.                               | —   |

- [ ] All rows above pass.

---

## Economy

| Command                                   | Golden-path test                                   | DB  |
|-------------------------------------------|----------------------------------------------------|-----|
| `/glw balance`                            | Shows your own balance.                            | Yes |
| `/glw balance <name>`                     | Shows another player's balance (online + offline). | Yes |
| `/glw economy deposit <player> <amount>`  | Credits the target; their balance updates.         | Yes |
| `/glw economy withdraw <player> <amount>` | Debits the target.                                 | Yes |
| `/glw economy set <player> <amount>`      | Overwrites balance.                                | Yes |
| `/glw economy reset <player>`             | Resets to default.                                 | Yes |
| `/glw bank create <name>`                 | Creates a personal bank.                           | Yes |
| `/glw bank deposit <amount>` / `withdraw` | Transfers between wallet and bank.                 | Yes |
| `/glw bank balance`                       | Shows bank balance.                                | Yes |
| `/glw bank delete`                        | Deletes bank (confirm prompt appears).             | Yes |

- [ ] All rows pass. Re-run at least one after `/glw reload data`.

---

## Gang & Members

| Command                                   | Golden-path test                       | DB  |
|-------------------------------------------|----------------------------------------|-----|
| `/glw gang create <name>`                 | Creates gang. Caller is the founder.   | Yes |
| `/glw gang invite <player>`               | Invite notification appears to target. | —   |
| `/glw gang accept`                        | Target joins as lowest rank.           | Yes |
| `/glw gang leave`                         | Member leaves.                         | Yes |
| `/glw gang kick <player>`                 | Officer kicks member.                  | Yes |
| `/glw gang promote <player>` / `demote`   | Rank shifts up/down.                   | Yes |
| `/glw gang name <name>`                   | Renames gang.                          | Yes |
| `/glw gang display <name>` / `remove`     | Sets/clears chat display name.         | Yes |
| `/glw gang desc`                          | Opens anvil/description editor.        | Yes |
| `/glw gang color`                         | Colour picker opens.                   | Yes |
| `/glw gang deposit <amount>` / `withdraw` | Gang bank transfer.                    | Yes |
| `/glw gang balance`                       | Shows gang balance.                    | Yes |
| `/glw gang ally request <id>` / `abandon` | Alliance request and abandon.          | Yes |
| `/glw gang delete`                        | Deletes gang (confirm prompt).         | Yes |

- [ ] All rows pass.

---

## Ranks

| Command                                           | Golden-path test                 | DB  |
|---------------------------------------------------|----------------------------------|-----|
| `/glw rank list`                                  | Lists configured ranks.          | Yes |
| `/glw rank create <name>`                         | New rank appears in the list.    | Yes |
| `/glw rank delete <name>`                         | Removed.                         | Yes |
| `/glw rank info <name>`                           | Shows permissions + parents.     | Yes |
| `/glw rank permission add <name> <permission>`    | Permission added.                | Yes |
| `/glw rank permission remove <name> <permission>` | Permission removed.              | Yes |
| `/glw rank parent add <rank> <parent>` / `remove` | Hierarchy updated.               | Yes |
| `/glw rank traverse`                              | Shows next rank(s) up the chain. | Yes |

- [ ] All rows pass.

---

## Waypoints

| Command                           | Golden-path test                | DB  |
|-----------------------------------|---------------------------------|-----|
| `/glw waypoint list`              | Lists all waypoints.            | Yes |
| `/glw waypoint create <name>`     | Saves at current location.      | Yes |
| `/glw waypoint delete <id>`       | Removes.                        | Yes |
| `/glw waypoint select <id>`       | Selects for editing.            | —   |
| `/glw waypoint info`              | Details for selected.           | —   |
| `/glw waypoint type <value>`      | Type applied.                   | Yes |
| `/glw waypoint timer <amount>`    | Timer applied.                  | Yes |
| `/glw waypoint cost <amount>`     | Cost applied.                   | Yes |
| `/glw waypoint cooldown <amount>` | Cooldown applied.               | Yes |
| `/glw waypoint shield <amount>`   | Shield duration applied.        | Yes |
| `/glw waypoint radius <amount>`   | Radius applied.                 | Yes |
| `/glw waypoint gangId <gangId>`   | Binds to gang.                  | Yes |
| `/glw tp`                         | Teleports to selected waypoint. | —   |
| `/glw tp <name>`                  | Teleports to a named waypoint.  | —   |

- [ ] All rows pass.

---

## Weapons & Ammo

| Command                            | Golden-path test             |
|------------------------------------|------------------------------|
| `/glw weapon list`                 | All weapons listed.          |
| `/glw weapon give <name> <amount>` | Weapon appears in inventory. |
| `/glw weapon info <name>`          | Stats shown.                 |
| `/glw ammo list`                   | All ammo listed.             |
| `/glw ammo give <name> <amount>`   | Ammo appears in inventory.   |
| `/glw ammo info <name>`            | Details shown.               |

- [ ] All rows pass. For full weapon-system coverage see [features/weapons.md](./features/weapons.md).

---

## Items

| Command                                            | Golden-path test       |
|----------------------------------------------------|------------------------|
| `/glw item unique list` / `give <name> <amount>`   | Unique item delivered. |
| `/glw item wearable list` / `give <name> <amount>` | Wearable delivered.    |
| `/glw item money list` / `give <name> <amount>`    | Money item delivered.  |
| `/glw item unique info <name>`                     | Stats shown.           |

- [ ] All rows pass.

---

## Cars & Fuel

| Command                             | Golden-path test            |
|-------------------------------------|-----------------------------|
| `/glw car list`                     | Cars listed.                |
| `/glw car give <name> <amount>`     | Car item delivered.         |
| `/glw car info`                     | Details of held car item.   |
| `/glw fuel add <amount>` / `remove` | Fuel adjusted on held item. |
| `/glw fuel info`                    | Fuel shown.                 |
| `/glw fuel refuel` / `defuel`       | Full refuel / full drain.   |

- [ ] All rows pass.

---

## Kits, Spawns, Warps

| Command                                                        | Golden-path test       | DB  |
|----------------------------------------------------------------|------------------------|-----|
| `/glw kit list` / `create <name>` / `delete <name>` / `<name>` | Kit CRUD + redeem.     | Yes |
| `/glw spawn list` / `set <type>` / `<type>` / `remove <type>`  | Spawn CRUD + teleport. | Yes |
| `/glw warp list` / `set <name>` / `<name>` / `remove <name>`   | Warp CRUD + teleport.  | Yes |

- [ ] All rows pass.

---

## Cops N Crooks

| Command                                                                     | Golden-path test             | DB  |
|-----------------------------------------------------------------------------|------------------------------|-----|
| `/glw cop list`                                                             | Active cops listed.          | —   |
| `/glw cop spawner set`                                                      | Spawner created at location. | Yes |
| `/glw cop spawner remove <id>`                                              | Removed.                     | Yes |
| `/glw cop spawner list` / `info` / `teleport`                               | Listing, details, teleport.  | Yes |
| `/glw civilian list` / `groups`                                             | Active civilians + groups.   | —   |
| `/glw civilian spawn <typeId>`                                              | Spawns one civilian.         | —   |
| `/glw civilian spawngroup <groupId>`                                        | Spawns a group.              | —   |
| `/glw civilian spawner set` / `setgroup`                                    | Spawner created.             | Yes |
| `/glw civilian spawner remove/list/info/tp`                                 | CRUD + teleport.             | Yes |
| `/glw cuff <player>` / `uncuff`                                             | Handcuff toggles.            | —   |
| `/glw jail throw <player>`                                                  | Jails the player.            | Yes |
| `/glw jail release <player>`                                                | Releases.                    | Yes |
| `/glw jail create` / `remove <id>` / `list` / `info <id>` / `teleport <id>` | Jail CRUD + teleport.        | Yes |
| `/glw wanted`                                                               | Shows wanted level.          | Yes |
| `/glw wanted add <amount>` / `remove` / `clear` / `clear <player>`          | Wanted adjustments.          | Yes |
| `/glw bounty set <player> <amount>` / `remove`                              | Bounty set/removed.          | Yes |
| `/glw respawn`                                                              | Revives a downed player.     | Yes |

- [ ] All rows pass.

---

## Shop & Trader

| Command                                         | Golden-path test                                                                | DB  |
|-------------------------------------------------|---------------------------------------------------------------------------------|-----|
| `/glw shop create <key>`                        | New shop YAML created.                                                          | —   |
| `/glw shop edit <key>`                          | Admin GUI opens; drag-in, L-click price, R-click remove all work.               | —   |
| `/glw shop list`                                | Lists shops with buy/sell counts.                                               | —   |
| `/glw shop title <key>`                         | Anvil GUI renames the shop's inventory title.                                   | —   |
| `/glw trader create <shopKey> <traitId> [name]` | NPC spawns, stationary, bound to shop. `NPC.Metadata.SHOULD_SAVE` is **false**. | Yes |
| `/glw trader edit shop <shopKey>`               | Retargets crosshair trader.                                                     | Yes |
| `/glw trader edit trait <traitId>`              | Trait swap applies.                                                             | Yes |
| `/glw trader edit name`                         | Anvil GUI renames trader.                                                       | Yes |
| `/glw trader remove`                            | Removes crosshair trader within 5 blocks.                                       | Yes |

- [ ] All rows pass. Full flow (buy / barter / tip / mood) in [features/trader-shop.md](./features/trader-shop.md).

---

## Loot Chests

| Command                 | Golden-path test            |
|-------------------------|-----------------------------|
| `/glw lootchest`        | Receive a loot-chest wand.  |
| `/glw lootchest edit`   | Edit wand settings.         |
| `/glw lootchest remove` | Remove targeted loot chest. |

- [ ] All rows pass.

---

## Levels

| Command                                  | Golden-path test      |
|------------------------------------------|-----------------------|
| `/glw level`                             | Shows current level.  |
| `/glw level next`                        | Stats for next level. |
| `/glw level exp add <amount>` / `remove` | XP adjusted.          |
| `/glw level add <amount>` / `remove`     | Level adjusted.       |

- [ ] All rows pass.

---

## Admin / Misc

| Command                              | Golden-path test          |
|--------------------------------------|---------------------------|
| `/glw safewand`                      | Receive a safe-zone wand. |
| `/glw dealer create` / `remove <id>` | Dealer NPC CRUD.          |

- [ ] All rows pass.

---

## Tab Completion & Permission Gate

- [ ] Tab completion responds on every chained `OptionalArgument` node (player names, numeric ranges, registered
  keys). Missing tab completion usually means a `SubArgument` read `args[n]` directly — see
  `feedback_optional_arguments`.
- [ ] Non-op player without the rank permission is denied on every admin command with a clear message.

---

[Back to Test Index](./README.md)
