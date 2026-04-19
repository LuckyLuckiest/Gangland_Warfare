# Lifecycle & Reload — Test Checklist

[Back to Test Index](./README.md) | [Architecture Doc](../developer/architecture.md)

---

## Overview

`Gangland.onEnable()` constructs a `GanglandContext`, which drives the bean pipeline:
**KERNEL → FILE → DATABASE → CONFIG → LIFECYCLE → LISTENER → COMMAND**. Beans implementing `BeanLifecycle` participate
in the managed reload (`context.reloadBeans()`) and shutdown (`context.shutdownBeans()`) flows automatically.

This checklist exercises the enable, reload, and disable paths end-to-end.

---

## Cold Start (fresh install)

- [ ] Delete `plugins/Gangland/` and all SQLite files. Boot the server.
- [ ] Console shows each phase firing in order — watch for `KERNEL`, `FILE`, `DATABASE`, `CONFIG`, `LIFECYCLE`,
  `LISTENER`, `COMMAND`.
- [ ] All default YAML configs regenerate under `plugins/Gangland/`.
- [ ] SQLite database file is created with every table present.
- [ ] All `@Repository`-annotated classes auto-register (one log line per repository).
- [ ] All `@CommandHandler` sub-commands register under `/glw`.
- [ ] `/glw help` responds immediately.
- [ ] No stack traces, no "bean could not be resolved" errors.

---

## Warm Start (existing data)

- [ ] Boot against a data dir from the previous stable release.
- [ ] All prior gangs, members, users, ranks, waypoints, loot chests, cop spawners, civilian spawners, jails, and
  traders load back in.
- [ ] NPC entities re-spawn at their saved locations (Citizens must not have a persistent copy — our repositories
  own NPC state).
- [ ] Active scoreboard shows on every online player's screen at correct values.

---

## `/glw reload` (full reload)

- [ ] While the server is running, open an inventory, equip a weapon, and have a scoreboard visible.
- [ ] Run `/glw reload` as an op.
- [ ] Console shows `context.reloadBeans()` firing in topological order.
- [ ] After completion:
    - [ ] `/glw help` still responds.
    - [ ] The open inventory was closed or cleanly re-initialised (no ghost clicks).
    - [ ] Weapon still fires with correct stats.
    - [ ] Scoreboard still renders.
- [ ] Trigger an event the plugin listens to (join/leave, block place, sign interact). The handler fires **once**, not
  twice — verifies listeners did not double-register.

---

## `/glw reload files` (config-only)

- [ ] Edit `settings.yml` to change an obvious value (e.g. auto-save interval).
- [ ] Run `/glw reload files`.
- [ ] Change is visible without restart.
- [ ] No database reconnection happens (check HikariCP log — should stay quiet).

---

## `/glw reload data` (database)

- [ ] Run `/glw reload data`.
- [ ] In-memory caches drop and re-read from DB.
- [ ] User data for online players re-populates within one tick.

---

## `/glw reload scoreboard`

- [ ] Edit `scoreboard.yml` (e.g. change a line's colour code).
- [ ] Run `/glw reload scoreboard`.
- [ ] All online players see the new scoreboard immediately.

---

## Clean Shutdown

- [ ] Run `stop`.
- [ ] Console shows `shutdownBeans()` running in **reverse** topological order.
- [ ] `PluginDataCleanupService` flushes every dirty user, gang, and repository entry.
- [ ] `PeriodicalUpdates` final tick runs.
- [ ] HikariCP pool closes (both SQLite and MySQL modes).
- [ ] No "thread leaked" warnings, no lingering scheduler tasks.

---

## Hard Shutdown

- [ ] Start server, log in, perform a few actions.
- [ ] Wait for one auto-save tick.
- [ ] `kill -9` the JVM.
- [ ] Restart. State from the last auto-save is intact; post-save state may be lost — acceptable.
- [ ] SQLite file is not corrupt (`sqlite3 gangland.db "PRAGMA integrity_check;"`).

---

## `BeanPostInitialize` Hook

- [ ] Any bean that wires self-references via `BeanPostInitialize` (e.g. `CopManager`) initialises exactly once per
  enable and exactly once per reload — no NPE, no double init.
- [ ] Reference: [project_copmanager_reload_npe](../../CLAUDE.md) in project memory.

---

## Files & Symbols Referenced

- `gangland-impl/src/main/java/me/luckyraven/Gangland.java`
- `gangland-impl/src/main/java/me/luckyraven/GanglandContext.java`
- `gangland-core` → `BeanFactory`, `BeanGraph`, `BeanLifecycle`, `BeanPostInitialize`, `DependencyContainer`
- `PeriodicalUpdates` (CONFIG-phase bean), `PluginDataCleanupService`

---

[Back to Test Index](./README.md)
