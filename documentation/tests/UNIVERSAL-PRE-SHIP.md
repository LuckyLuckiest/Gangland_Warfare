# Universal Pre-Ship Checklist

[Back to Test Index](./README.md)

---

## Purpose

These checks are **mandatory for every release**, regardless of what changed. They cover the plugin's core lifecycle
and the invariants that every feature depends on. If any box here fails, do not ship.

Deep-dive versions of each section live in:

- [lifecycle-and-reload.md](./lifecycle-and-reload.md)
- [persistence-and-database.md](./persistence-and-database.md)
- [config-and-yaml.md](./config-and-yaml.md)
- [commands.md](./commands.md)

Use this file as the top-level gate; drop into the deep-dive files only when a row fails or when the release touches
the relevant subsystem heavily.

---

## 1. Build & Install

- [ ] Final shaded JAR (`gangland-build`) loads on a clean Spigot server with no `ClassNotFoundException` or
  `NoSuchMethodError` in console.
- [ ] Required dependencies present: `NBTAPI`, `Citizens`. Plugin refuses to enable and logs a clear message if either
  is missing.
- [ ] Soft dependencies (`PlaceholderAPI`, `Vault`, `ViaVersion`) behave correctly when present **and** when absent.

---

## 2. Cold-Start Enable (fresh world)

- [ ] Delete the plugin data dir and boot the server.
- [ ] `onEnable` completes with no stack traces.
- [ ] All default config files regenerate under `plugins/Gangland/` (see [config-and-yaml.md](./config-and-yaml.md)).
- [ ] Default database is created (`gangland.db` for SQLite) with all tables.
- [ ] Every bean phase logs success: **KERNEL → FILE → DATABASE → CONFIG → LIFECYCLE → LISTENER → COMMAND**.

---

## 3. Warm-Start Enable (existing data)

- [ ] Start the server against a data dir from the previous stable release.
- [ ] Existing gangs, members, users, waypoints, loot chests, jails, cop spawners, civilian spawners, traders all
  reappear with their prior state.
- [ ] No schema migration errors; if a migration is expected, it runs exactly once and is idempotent on a second boot.

---

## 4. Reload

- [ ] `/glw reload` completes with no errors.
- [ ] `/glw reload files`, `/glw reload data`, `/glw reload scoreboard` each individually complete cleanly.
- [ ] After reload: a previously-working command still responds; a previously-registered listener fires exactly once
  (no duplicates).
- [ ] Active player state (open inventories, equipped weapons, scoreboard) is preserved or cleanly re-initialised.

---

## 5. Clean Shutdown

- [ ] Run `stop` on the server. Shutdown phase runs in reverse bean order.
- [ ] `PluginDataCleanupService` and `PeriodicalUpdates` flush pending writes.
- [ ] Database connections close; no "connection leak" warnings from HikariCP.
- [ ] On Windows, the SQLite `.db`, `.db-shm`, `.db-wal` files release within a few seconds after shutdown.

---

## 6. Hard Shutdown (crash simulation)

- [ ] With the server running, `kill -9` (or Windows equivalent) the JVM.
- [ ] Restart: data from the most recent auto-save tick is present. Data between the last save and the crash may be
  lost — this is expected.
- [ ] No corrupt SQLite database after restart (if MySQL, no orphan half-written rows).

---

## 7. Database Mode Coverage

Run items 2–6 twice: once with `database.type: SQLITE`, once with `database.type: MYSQL`.

- [ ] SQLite cold-start works.
- [ ] MySQL cold-start works against a real MySQL server.
- [ ] MySQL failure path: with a wrong host/credentials, the plugin logs a clear error but the server stays up.
  `HikariCP` throws `PoolInitializationException` (a `RuntimeException`, not `SQLException`) — confirm the
  error message reaches the console rather than being silently swallowed.

---

## 8. Config Integrity

- [ ] Every `.yml` listed in [config-and-yaml.md](./config-and-yaml.md) exists after cold start.
- [ ] No file contains a literal `§` colour code (use `&` codes and run them through `ChatUtil.color()` instead —
  see [feedback_chat_color_codes](../../CLAUDE.md)).
- [ ] No `.yml` uses inline flow syntax (`{ key: value }`); block style only.
- [ ] Config keys use `Capitalized_Underscore_Separated`, matching the loaders.

---

## 9. Commands

- [ ] `/glw help` lists every top-level subcommand.
- [ ] Tab completion works on every top-level verb (`gang`, `bank`, `waypoint`, `weapon`, `shop`, …).
- [ ] See [commands.md](./commands.md) for the per-subcommand smoke tests. At minimum, spot-check one command per
  top-level verb.

---

## 10. Permissions

- [ ] Non-op player without rank permissions is denied on admin commands with a clear message.
- [ ] Op player can run everything.
- [ ] Rank permission grants: promote a player, confirm they now pass the check for a permission their rank holds.

---

## 11. Citizens & NPCs

- [ ] All NPCs (cops, civilians, traders) spawn with `NPC.Metadata.SHOULD_SAVE = false` — confirm Citizens'
  `saves.yml` does not grow when plugin NPCs spawn.
- [ ] Our repositories are the sole source of NPC persistence.

---

## 12. No Paper-Only API

- [ ] Grep the codebase for `io.papermc.paper` — must return zero hits in production code.

---

## 13. Per-Feature Rollup

Run every checklist under [`features/`](./features/) whose code changed this release. For a major version bump, run
them all.

- [ ] All relevant feature checklists passed.
- [ ] Any failed boxes have tracking issues filed and are mentioned in the version changelog.

---

[Back to Test Index](./README.md)
