# Database & Setup

[← Scoreboard](./scoreboard.md) | [Back to Index](../README.md)

---

## Overview

Gangland Warfare stores all persistent data — players, gangs, waypoints, jails, cop spawners, and more — in a database.
It supports both **MySQL** and **SQLite**. SQLite requires no external setup and is the default. MySQL is recommended
for production servers with many concurrent players.

---

## Choosing a Database

|                    | SQLite                            | MySQL                              |
|--------------------|-----------------------------------|------------------------------------|
| Setup required     | None                              | Yes                                |
| Best for           | Development, small servers        | Production, large servers          |
| Data file location | `plugins/GanglandWarfare/data.db` | Remote/local MySQL server          |
| Fallback           | —                                 | Can fall back to SQLite on failure |

---

## SQLite (Default)

No setup is needed. The database file is created automatically in the plugin's data folder on first run.

Automatic **backups** are enabled by default. If a backup fails, a warning is logged.

```yaml
Database:
   Type: sqlite
   SQLite:
      Backup: true
```

---

## MySQL

To use MySQL, set `Type: mysql` and fill in your server credentials:

```yaml
Database:
   Type: mysql
   MySQL:
      Host: localhost
      Port: 3306
      Username: root
      Password: "yourpassword"
   SQLite:
      Failed_MySQL: true          # Fall back to SQLite if MySQL cannot connect
```

When `Failed_MySQL: true`, if the plugin cannot reach the MySQL server on startup, it automatically switches to SQLite
instead of refusing to load. This is useful as a failsafe but should not be relied on in production.

---

## Auto-Save

Player and gang data is periodically saved to the database in the background. This reduces the risk of data loss if the
server crashes.

```yaml
Database:
   Auto_Save:
      Enable: true
      Time: 10                    # Save interval in minutes
      Debug: true                 # Print a log message on each auto-save cycle
```

Data is also saved on clean player logout.

---

## Data Cleanup

Old records for players who have not been online in a long time are automatically purged to keep the database lean.

```yaml
Database:
   Clean_Up:
      Time: 30                    # Days of inactivity before a record is removed
```

---

## Full Configuration Reference

```yaml
Database:
   Type: sqlite                  # Options: mysql, sqlite

   MySQL:
      Host: localhost
      Port: 3306
      Username: root
      Password: ""

   SQLite:
      Backup: true                # Create backups of the SQLite file
      Failed_MySQL: true          # Fall back to SQLite if MySQL fails to connect

   Auto_Save:
      Enable: true
      Time: 10                    # Minutes between auto-save cycles
      Debug: true                 # Log auto-save events to console

   Clean_Up:
      Time: 30                    # Days before inactive player records are deleted
```

---

## First-Time Setup Checklist

1. Install the plugin JAR into your `plugins/` folder.
2. Install **Citizens** and **NBTAPI** — both are required. The plugin will not load without them.
3. Start the server once to generate all default config files.
4. Edit `settings.yml` to set your database type, auto-save interval, and economy values.
5. Edit `cops.yml` to configure cop tiers and AI behavior (or leave defaults).
6. Restart the server.
7. Use `/glw cop spawner set` in-world to place cop spawn points.
8. Use `/glw jail create` to place jail locations.

---

[← Scoreboard](./scoreboard.md) | [Back to Index](../README.md)
