# Gangland Warfare — Pre-Ship Test Checklists

[← Back to Documentation](../README.md)

---

## Purpose

Every checklist in this directory is a **manual pre-ship test**. Walk through every file in this index on a running
test server before tagging a new release. If a step fails, fix it or document it as a known issue in the version
changelog — do not ship regressions silently.

Checklists are GitHub-flavoured markdown with `- [ ]` boxes. Copy the file into a release-issue on GitHub (or a local
scratch file) and tick each box as you go.

---

## How To Use

1. Spin up a local test server with the new build installed.
2. Open `UNIVERSAL-PRE-SHIP.md` first — these checks must pass regardless of what changed this release.
3. Run through each relevant `features/*.md` file. At minimum, run the checklist for every feature touched by this
   version's commits; in a major release, run all of them.
4. If a check fails, open an issue before shipping.

---

## Universal Checklists (run on every release)

| # | Checklist                                               | Covers                                                       |
|---|---------------------------------------------------------|--------------------------------------------------------------|
| 1 | [Universal Pre-Ship](./UNIVERSAL-PRE-SHIP.md)           | The one-page top-level gate — everything below rolls up here |
| 2 | [Lifecycle & Reload](./lifecycle-and-reload.md)         | `onEnable` / `onDisable` / `/glw reload`                     |
| 3 | [Persistence & Database](./persistence-and-database.md) | SQLite + MySQL, auto-save, shutdown flush                    |
| 4 | [Config & YAML](./config-and-yaml.md)                   | Every `.yml` regenerates, reloads, survives corruption       |
| 5 | [Commands](./commands.md)                               | Every `/glw` subcommand smoke-tested                         |

---

## Per-Feature Checklists

Run the checklist for any feature whose code, config, or dependencies changed this release. File order mirrors
[the feature index](../README.md#core-feature-guides).

| #  | Feature                                            | Feature Doc                                       |
|----|----------------------------------------------------|---------------------------------------------------|
| 1  | [Gangs](./features/gangs.md)                       | [gangs](../features/gangs.md)                     |
| 2  | [Economy](./features/economy.md)                   | [economy](../features/economy.md)                 |
| 3  | [Waypoints](./features/waypoints.md)               | [waypoints](../features/waypoints.md)             |
| 4  | [Loot Chests](./features/loot_chests.md)           | [loot_chests](../features/loot_chests.md)         |
| 5  | [Levels](./features/levels.md)                     | [levels](../features/levels.md)                   |
| 6  | [Unique Items](./features/unique-items.md)         | [unique-items](../features/unique-items.md)       |
| 7  | [Scoreboard](./features/scoreboard.md)             | [scoreboard](../features/scoreboard.md)           |
| 8  | [Weapons](./features/weapons.md)                   | [weapons](../features/weapons.md)                 |
| 9  | [Wanted & Bounty](./features/wanted-bounty.md)     | [wanted-bounty](../features/wanted-bounty.md)     |
| 10 | [Trade Signs](./features/trade-signs.md)           | [trade-signs](../features/trade-signs.md)         |
| 11 | [Ranks](./features/ranks.md)                       | [ranks](../features/ranks.md)                     |
| 12 | [Inventory System](./features/inventory.md)        | [inventory](../features/inventory.md)             |
| 13 | [Cops N Crooks](./features/cops-n-crooks.md)       | [cops-n-crooks](../features/cops-n-crooks.md)     |
| 14 | [Jail & Detainment](./features/jail-detainment.md) | [jail-detainment](../features/jail-detainment.md) |
| 15 | [Wearables](./features/wearables.md)               | [wearables](../features/wearables.md)             |
| 16 | [Civilians](./features/civilians.md)               | [civilians (dev)](../developer/civilians.md)      |
| 17 | [Trader & Shop](./features/trader-shop.md)         | —                                                 |
| 18 | [Cars & Fuel](./features/cars-fuel.md)             | [gadgets (dev)](../developer/gadgets.md)          |

---

## Adding A New Feature Checklist

1. Copy [`TEMPLATE.md`](./TEMPLATE.md) into `features/<feature>.md`.
2. Fill in every section. Keep every step actionable: verb + object + expected result.
3. Add a row to the "Per-Feature Checklists" table above.

---

*Back to [Documentation Index](../README.md).*
