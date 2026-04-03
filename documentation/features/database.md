# Database & Setup

[← Ranks](./ranks.md) | [Back to Index](../README.md) | [Next: Inventory System →](./inventory.md)

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

## Global Settings

The following sections in `settings.yml` are not database-specific but apply to the whole plugin.

### Config Version

```yaml
Config_Version: '1.0.0'
```

Changing `Config_Version` to any value different from the current plugin version causes the file to be regenerated. The
old file is renamed with an `-old` suffix. This works on every YAML file in the plugin, even if the section was not
originally present — you can add it manually to any config to force a reset.

---

### Update Checker

```yaml
Update_Checker:
  Enable: true                    # Check for new plugin releases on startup
  Notify_Privileged_Players: false # Notify players with the notify permission on join if an update is available
  Auto_Download: true             # Automatically download new releases into the plugin's 'release' folder
```

---

### Language

```yaml
Language: en
```

Sets the active message language. Messages are loaded from `plugins/GanglandWarfare/message/message_<Language>.yml`. To
use a custom language, create that file in the message folder. Entering an invalid language name disables the plugin and
prints a list of valid options in the console.

---

### Resource Pack

```yaml
Resource_Pack:
  Enable: true                    # Whether the plugin prompts players to download the resource pack on join
  URL: "https://..."              # Direct download URL for the resource pack
  Kick: false                     # If true, players who decline the resource pack are kicked
```

---

### Inventory Appearance

Controls the decorative items used inside plugin GUIs.

```yaml
Inventory:
  Fill:
    Item: BLACK_STAINED_GLASS_PANE  # Material used to fill empty GUI slots
    Name: " "                        # Display name of the fill item
  Line:
    Item: WHITE_STAINED_GLASS_PANE  # Material used for divider rows in GUIs
    Name: " "
  Multi_Inventory:
    Next_Page: "<base64>"           # Skull texture for the next-page navigation button
    Previous_Page: "<base64>"       # Skull texture for the previous-page navigation button
    Home_Page: "<base64>"           # Skull texture for the home navigation button
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

[← Ranks](./ranks.md) | [Back to Index](../README.md) | [Next: Inventory System →](./inventory.md)
