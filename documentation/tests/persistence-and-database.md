# Persistence & Database — Test Checklist

[Back to Test Index](./README.md) | [Persistence Doc](../developer/persistence.md) | [Feature: Database & Setup](../features/database.md)

---

## Overview

`GanglandDatabase` wraps HikariCP and supports both MySQL and SQLite, selected by `settings.yml`. All `@Repository`
classes are auto-registered via `RepositoryRegistry.scanAndRegisterRepositories("me.luckyraven.database.repositories")`.

Run this checklist **twice** per release — once with SQLite, once with MySQL.

---

## SQLite Mode

- [ ] `settings.yml` → `database.type: SQLITE`.
- [ ] Cold-start the server. `gangland.db` appears in the plugin data dir.
- [ ] `sqlite3 gangland.db ".tables"` — every expected table is present (users, gangs, members, waypoints,
  loot_chests, cop_spawners, civilian_spawners, traders, jails, …).
- [ ] Create a gang, join it, gain some XP, teleport to a waypoint. Stop the server cleanly.
- [ ] Inspect the `.db` file → the new rows are there.
- [ ] `sqlite3 gangland.db "PRAGMA integrity_check;"` → returns `ok`.
- [ ] On Windows: `gangland.db`, `gangland.db-shm`, `gangland.db-wal` files release within ~5 seconds of shutdown.
  (HikariCP's `minimumIdle=5` opens native handles; per CLAUDE.md the cleanup path must work.)

---

## MySQL Mode

- [ ] `settings.yml` → `database.type: MYSQL` with valid host/user/password/database.
- [ ] Cold-start the server. Plugin connects, creates all tables on the MySQL server.
- [ ] Reproduce the SQLite smoke test (create gang, join, XP, waypoint).
- [ ] Stop cleanly. Restart. All state is restored from MySQL.
- [ ] `SELECT COUNT(*) FROM users;` — matches the in-game player count.

---

## MySQL Failure Path

- [ ] Set `settings.yml` → `database.type: MYSQL` with a **wrong** host or password.
- [ ] Boot the server.
- [ ] Console shows a clear error about the failed connection. HikariCP throws `PoolInitializationException` (a
  `RuntimeException`, not `SQLException`) — confirm the error surfaces rather than being swallowed.
- [ ] Server does not crash. Plugin is either disabled cleanly or falls back per configuration.
- [ ] Per CLAUDE.md: assertions on `db == null || db.getConnection() == null` — a non-null `MySQL` instance without a
  live connection is the failure mode.

---

## Auto-Save (`PeriodicalUpdates`)

- [ ] Confirm the auto-save interval in `settings.yml`.
- [ ] Create new state, wait at least one interval, `kill -9` the JVM.
- [ ] Restart → the auto-saved state is present.

---

## Save-on-Shutdown

- [ ] Create new state in-game.
- [ ] Run `stop` **before** an auto-save tick would fire.
- [ ] Restart → state is present (shutdown flush worked).
- [ ] `PluginDataCleanupService` ran in the disable phase (check logs).

---

## Async Upsert

- [ ] Spawn a burst of writes (e.g. 20 players gaining XP simultaneously). `AbstractRepository`'s async upsert via
  `DatabaseHelper` must handle the load without dropping rows.
- [ ] Stop the server after the burst. Restart. All 20 XP values are persisted correctly.

---

## Repository Registration

- [ ] On boot, every `@Repository` class under `me.luckyraven.database.repositories` logs a registration line.
- [ ] `RepositoryRegistry.getAllRepositories()` returns one entry per entity type — not per class. Registering two
  repositories under the same entity class overwrites the first (this is intentional per CLAUDE.md).

---

## Table Schema

- [ ] Tables are defined as constants under `me.luckyraven.database.tables.*`.
- [ ] If a schema migration is part of this release: migration runs exactly once; second boot is a no-op; no data
  loss.

---

## Known Gotchas (from CLAUDE.md)

- **Windows SQLite temp files.** Tests use `@TempDir(cleanup = CleanupMode.NEVER)` + `MockPluginFactory.releaseDbFiles`.
  At runtime, the OS must release handles within seconds of plugin disable. Confirm with repeated
  enable/disable cycles — no "file in use" errors.
- **HikariCP `minimumIdle=5`.** Plugin startup opens 5 connections eagerly. Confirm the console shows exactly 5
  connections initialised on boot; more or fewer indicates a config regression.

---

[Back to Test Index](./README.md)
