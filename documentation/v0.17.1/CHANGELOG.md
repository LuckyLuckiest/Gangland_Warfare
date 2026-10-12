# 0.17.1 Changelog

[← Back to Documentation Index](../README.md)

---

## 📦 Requires

- **Keystone 1.15.3**, unchanged from 0.17.0. No migration, no new config keys. Replace the core jar only.

## 🔧 Fixed

- **Disbanding a gang no longer loses an online member's share.** If an online member was missing from the plugin's member cache, their disband payout stayed in the deleted gang's bank. The payout now uses the gang's own member record. (T-279)
- **Gang owners who are not ops can disband** — `/glw gang delete` (and `remove`/`del`) was op-only on live servers because `gangland.command.gang.delete` was missing from `plugin.yml`. Keystone registers every command node at runtime, but with the OP default; only a `plugin.yml` entry with `default: true` opens it to players, and 0.17.0 left several player verbs out.
- **Other player commands that were op-only** now default to true: `gang rename`, `transfer`, `color`, `display`, `description`, the whole `gang ally` flow (`abandon`, `request`, `accept`, `reject`, `pending`, `pending cancel`), `/glw bank` (`balance`, `deposit`, `withdraw`, `menu`), `/glw respawn`, `/glw wanted`, `/glw contact`, `/glw resource`, `/glw car` (`list`, `info`), `/glw grapple` and `/glw jetpack`. Staff children (`wanted add|remove|clear`, `car give`, `bank resetcap`) stay op. Rank gates and the `[player]` admin forms are still enforced inside the commands.

## ⚠ Still op-only (owner decision pending)

`bank create`, `balance`, `teleport` and the per-item nodes (`gangland.cars.<id>`, `gangland.jetpacks.<id>`, `gangland.grapples.<id>`, `gangland.uniqueitem.<id>`, `gangland.waypoint.<id>`) are unchanged. Grant them with a permissions plugin if you want players to have them.
