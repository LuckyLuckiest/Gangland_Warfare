# 0.17.1 Changelog

[← Back to Documentation Index](../README.md)

---

## 📦 Requires

- **Keystone 1.15.3**, unchanged from 0.17.0. No migration, no new config keys. Replace the core jar **and** the `gangland-gang` and `gangland-gadget` module jars (or simply all module jars): the disband fix lives in the gang module and the car, jetpack and grapple node fix in the gadget module.

## 🔧 Fixed

- **Disbanding a gang no longer loses an online member's share.** If an online member was missing from the plugin's member cache, their disband payout stayed in the deleted gang's bank. The payout now uses the gang's own member record. (T-279)
- **Gang owners who are not ops can disband** — `/glw gang delete` (and `remove`/`del`) was op-only on live servers because `gangland.command.gang.delete` was missing from `plugin.yml`. Keystone registers every command node at runtime, but with the OP default; only a `plugin.yml` entry with `default: true` opens it to players, and 0.17.0 left several player verbs out.
- **Other player commands that were op-only** now default to true: `gang rename`, `transfer`, `color`, `display`, `description`, the whole `gang ally` flow (`abandon`, `request`, `accept`, `reject`, `pending`, `pending cancel`), `/glw bank` (`balance`, `deposit`, `withdraw`, `menu`), `/glw respawn`, `/glw wanted`, `/glw contact`, `/glw resource`, `/glw car` (`list`, `info`), `/glw grapple`, `/glw jetpack` and `/glw filter` (the gang members view's search, sort and clear buttons run it, so it must be open to anyone who can open that menu). Staff children (`wanted add|remove|clear`, `car give`, `bank resetcap`) stay op. Rank gates and the `[player]` admin forms are still enforced inside the commands.

## 🔓 Now open to everyone (owner ruling)

- **`/glw bank create`, `/glw balance` and `/glw teleport`** default to true. `/glw balance <player>` (looking up someone else) stays behind the new admin node `gangland.command.balance.admin`, checked inside the command and hidden from tab completion (the completer stays silent for non-admins). A non-admin can no longer run `/glw balance <own name>`; plain `/glw balance` shows their own. `teleport.cooldown_bypass`, the `waypoint` create/delete/edit verbs and `bank resetcap` stay op. (T-826)
- **Waypoint use nodes** (`gangland.waypoint.<id>` and the per-name node a teleport checks) are registered with the default true when a waypoint is created or loaded, so a player can use the waypoints the server lists for them. (T-826)
- **Per-item nodes** `gangland.cars.<id>`, `gangland.jetpacks.<id>`, `gangland.grapples.<id>` and `gangland.uniqueitem.<id>` are registered with the default true, so a non-op can mount a car they bought, equip a jetpack and fire a grapple. The bypass and admin nodes (`gangland.cars.bypass` and the `give` commands, including the new `grapple give` and `jetpack give` entries in the op-only test list) stay op. (T-825)
- A new test walks the `commands.json` help entries of core and every module and fails when a listed command is neither declared in `plugin.yml` nor on an explicit staff list, so a player verb cannot ship op-only by accident again.
