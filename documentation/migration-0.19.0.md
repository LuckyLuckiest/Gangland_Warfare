# Migrating a server to Gangland 0.19.0 (Gang & Turf II)

[← Back to Documentation Index](./README.md)

This guide covers the upgrade from 0.17.0 to 0.19.0. A server still on 0.16.x applies [the 0.17.0 notes](./migration-0.17.0.md) first (data-safety fixes, offline cache, migrations after commit, Keystone 1.15.3), then this page. It lists what changes on disk, in the database and in play, in the
order you meet it. Read the whole page before you start the server on the new jars.

## Before you update

- **Update every jar together.** `gangland_warfare-0.19.0.jar` and every module jar in `plugins/Gangland_Warfare/modules/`
  come from the same build. Keep one revision on the server.
- **Mail and gang together.** The 0.19.0 `mail` module calls `GangPermissions.refusal`. A pre-0.19 `gang` jar has no such
  method, so the first refused ally or invite throws `NoSuchMethodError`. Replace the `gang` and `mail` jars in the same restart.
- **Host_Api 2.6.** Five modules now declare `Host_Api: 2.6`: `gang`, `civilians`, `turf`, `copsncrooks` and `fights`.
  Each needs a 0.19.0 host. `gadget`, `npcshops`, `mail`, `lootchest` and `healthbars` keep their older Host_Api lines.
- **Keystone 1.15.3** (the `keystone.version` in the root `pom.xml`). Nothing else needs a new dependency.
- **Optional plugins.** `dynmap` and `WorldGuard` are optional soft dependencies. Neither is required (see Optional plugins).
- **Back up** `plugins/Gangland_Warfare/` before the first boot. The first boot adds columns and tables and refreshes some
  menus; the backup is your rollback.
- **Every upgrade boot refreshes** `settings.yml` and all shipped menus (alliance_stat, gang_info, gang_stat, phone, phone_banking, phone_bounty, phone_contacts, phone_gang, phone_gang_search, user_stat), keeping `*-old.yml` copies. Copy your edits back from the old files.

## New and changed files

Files are created on first boot when they do not exist. The rules for files that already exist:

| File | What is new | What happens to your copy |
|---|---|---|
| `gang/gang_rules.yml` | Sections `Ranks` (with `Minimum`), `Progression` and `Levels` (each level has `Cost` and `Max_Members`), `Commands`, `Purge`, `Worlds`, `Friendly_Fire`, `Combat`, `Safehouse`, `Chat`, `Stats`, `Ranking`, `Placeholders`, `Display_Name` | Kept. An older file works with the code defaults; copy the new sections from the jar to change them. |
| `gang/gang_messages.yml` | `Combat` section (`Pvp_Now`, `Pvp_On`, `Pvp_Off`, `Ally_Pvp_On`, `Ally_Pvp_Off`, `Ally_Pvp_Waiting`, `Ally_Not_Allied`, `Tagged_Teleport`) and the other new sections | Kept. Missing keys fall back to the text in code. |
| `turf/turf_npcs.yml` | `Garrison` section: `Wave_Size: 3`, `Max_Alive: 6`, `Wave_Cooldown_Seconds: 20`, `Stand_Down_Seconds: 8`, `Leash_Margin: 16`, `Composition`, `Bounty_Share: 0.5`, `Recycle_Seconds: 12` | Kept. Missing keys use these defaults. |
| `turf/turf_map.yml` | `Web` section (dynmap layer) | Kept. Unused without dynmap. |
| `turf/turf_rules.yml` | `Teleport_Guard.Buffer_Blocks: 48` | Kept. |
| `npc/civilian_messages.yml` | `Shouts.Turf_Held` and `Shouts.Turf_Lost` lines | Kept. Existing files keep working with the English fallbacks. |
| `npc/civilians.yml` | A type may define `AI.Combat.Roles` (used by `Garrison.Composition`) | Kept. Without `Roles` nothing changes. |
| `fight/fight_rules.yml` | New file: `Challenge`, `Bets`, `Ranked`, `Guards` (including `Allowed_Commands`), `Arenas` | Created on first boot by the `fights` module. |
| `fight/fight_messages.yml` | New file | Created on first boot. |
| `fight/arenas.yml` | New file: the arena list, `plugins/Gangland_Warfare/fight/arenas.yml`, hand-editable; reload with `/glw arena reload` | Created on first boot. A broken file is copied to a backup before the next save writes over it. |
| `inventory/player_profile.yml`, `inventory/gang_ranking.yml` | New menus | Created on first boot. |
| `inventory/gang_stat.yml` | Replaced from the jar when the shipped `Config_Version` is newer | The old file is kept as `gang_stat-old.yml`. |

Menus are replaced automatically only when they carry an older `Config_Version`. Your own menus are not touched.

## Database

These columns and tables are added on the first boot. Existing rows keep today's behaviour through their defaults.

- `gang.notice`, `gang.friendly_fire`, `gang_ally.friendly_fire` (gang module).
- `member.kills`, `member.deaths`, `member.assists` (gang module). Counting starts at the upgrade; older kills are not
  back-filled.
- `gang_safehouse` (gang module). Existing `GANG` waypoints stay admin waypoints; they are not converted to safehouses.
- `fight_stats`, `fight_player_stats`, `fight_record` (fights module only). Fight bets move money from bank to bank; nothing
  is created.

## Behaviour changes

**Turf guards**

- Guards come in waves of 3 (at most 6 standing). Survivors and unused guards go back to the reserve, so an attack no
  longer spends every guard at once. Nothing is spent when Citizens is missing.
- Guards drop no civilian cash. A rival who kills a guard receives half the guard price (`Garrison.Bounty_Share: 0.5`);
  owners and allies receive nothing.
- Guards classify as an NPC money drop. If you enabled an NPC `Drop_Source` in `money.yml`, a guard kill mints cash again,
  on top of the bounty.
- Guards spawn with roles when `Garrison.Composition` is set: shield guards block half of a frontal hit, marksmen keep
  range, and the commander's death makes the others take cover for 5 seconds.

**Friendly fire (gangs)**

- A player's own arrow or splash potion damages them again. `Friendly_Fire.Allow_Self_Damage: false` in `gang_rules.yml`
  restores the old block.
- Bartizan weapon impacts between gangmates and allies follow `/glw gang pvp` and `/glw gang ally pvp` instead of always
  being blocked.
- Gangmates stay protected by default (`Friendly_Fire.Global: GANG_CHOICE`). Allies hurt each other only when both gangs ran
  `/glw gang ally pvp <gang> on`.
- Flame and Fire Aspect fire between protected friends is cancelled at ignition.
- Splash potions and lingering clouds skip protected friends. `Friendly_Fire.Potions: NEGATIVE_ONLY` blocks harmful potions
  only; `ALL` blocks every potion. A potion with any harmful effect is stopped whole for that friend.
- `Friendly_Fire.Worlds` (`world: ALLOW` or `DENY`) is read now.

**Combat tag and teleports (gangs and turf)**

- Hit by a member of another gang, both players are combat-tagged for `Combat.Tag_Seconds` (15; `0` turns it off). A tagged
  player cannot use a gang waypoint (safehouse, rally, admin gang waypoint) and cannot leave the gang.
- A cuffed or jailed player's pending teleport warm-up is cancelled.
- Gang teleports (safehouse, rally, gang waypoints) are refused out of, into or within 48 blocks of a turf under attack,
  into rival turf, and while your gang is in any turf fight (`turf_rules.yml` `Teleport_Guard.Buffer_Blocks`).
- `/glw turf create` refuses worlds where gangs are disabled.

**Gang recruiting**

- `/glw gang recruiting` now checks `gangland.gang.recruiting` (`Ranks.Minimum.Recruiting`, officer) instead of the invite node. A custom rank below officer holding only `gangland.gang.invite` needs `/glw rank permission add <rank> gangland.gang.recruiting`.

**Gang levels and caps**

- A level now needs held-turf XP and a bank fee. `/glw gang upgrade` shows what is missing; `/glw gang upgrade confirm` pays.
  Set every `Levels.Level_<n>.Cost` to `0` to keep automatic levelling.
- Level caps (members, safehouses, allies) apply to existing gangs grow-only: a gang keeps its current members and
  safehouses, and it cannot grow past its level's `Max_Members` again.
- `Gang.Enable: false` in `settings.yml` now really turns gangs off. `Worlds.Disabled` in `gang_rules.yml` turns them off per
  world.

**Ranks and purge**

- `Ranks.Minimum` grants a feature by rank: the named rank and every rank above it toward the leader. Entries naming a missing
  rank are ignored with one warning. Rank node lists still work.
- Auto-purge is off (`Purge.Enabled: false`). Staff can run `/glw option gang purge preview` first, then `run`.

**Sides frozen during fights**

- During a gang fight, a fighter may run only the fight verbs and the commands in `fight_rules.yml` `Guards.Allowed_Commands`.
  Bypass: `gangland.gang.fight.bypass_commands`.

## Permissions

New nodes, and the three parents that group them:

| Node | Default | Covers |
|---|---|---|
| `gangland.command.player` | true (parent) | Every player gang verb, including the owner verbs (transfer, delete, rename, description, display, color) and `/glw gang ally` |
| `gangland.admin.gang` | op (parent) | Staff gang tools under `/glw option gang` (includes `gangland.admin.gang.spy` and `gangland.admin.gang.force_rank`) |
| `gangland.arena.admin` | op (parent) | Arena staff verbs (`/glw arena ...`) |
| `gangland.gang.balance.others` | op (child of `gangland.admin.gang`) | Reading or changing another gang's bank; gang banks are private to the gang without it |
| `gangland.gang.fight.bypass_commands` | op (child of `gangland.admin.gang`) | Running commands during a gang fight outside `Allowed_Commands` |

Non-op players can now run `/glw gang ally ...` and the owner verbs. The rank gates still decide who may act. Check your
rank permissions after the upgrade.

## Optional plugins

- **dynmap**: with dynmap installed, turfs appear on a `Turfs` layer (`turf/turf_map.yml`, `Web` section). Without dynmap nothing
  changes and nothing is logged.
- **WorldGuard**: the flag `glw-gang-pvp` exists only after a full restart with WorldGuard loaded first. Restart the server
  once after installing WorldGuard, then set the flag per region.

## Commands

New verbs, grouped by where they live. The full help text is in each module's `commands.json`.

- **Gangs (gang module):** `/glw gang notice`, `/glw gang chat allies`,
  `/glw gang ranking`, `/glw gang profile`, `/glw gang safehouse` (`set`, `remove`, `list`), `/glw gang rally` (`join`),
  `/glw gang pvp`, `/glw gang ally pvp`, `/glw gang upgrade` (`confirm`), `/glw gang bounty`, `/glw gang recruiting`.
- **Staff:** `/glw option gang rank`, `disband`, `leader`, `bank`, `safehouse`, `stats reset`, `spy`, `purge preview|run`.
  Staff gang tools also work from the console (`/glw option gang`, `/glw gang list`, `/glw gang info <gang>`).
- **Turf:** `/glw turf rules`, `/glw turf rename`, `/glw turf garrison`, `/glw turf powerupnpc`. `/glw turf show` and
  `/glw map border` draw only the part within 40 blocks of you.
- **Map:** `/glw map zoom in|out` (8, 16, 32 or 64 blocks per cell; your own view only, reset on logout).
- **Fights (new module):** `/glw gang fight propose|accept|decline|enlist|drop|info|arenas|stats`, and staff `/glw arena ...`
  (`create`, `corner`, `spawn`, `exit`, `size`, `finish`, `show`, `list`, `tp`, `on`, `off`, `delete`, `reload`).
- **Short labels:** `/gw`, `/gwc` and `/gwa` work only when the gang module is installed. If another plugin owns a label,
  use `/gangland_warfare:gw` or remap it in `commands.yml`.

## Module install ids

`/glw module install <id>` knows the official ids. Gang, lootchest and healthbars are now in the list:

| Id | Module | Needs |
|---|---|---|
| `gang` | `gangland-gang` | nothing |
| `fights` | `gangland-gang-fights` | `gang` |
| `lootchest` | `gangland-lootchest` | nothing |
| `healthbars` | `gangland-healthbars` | Citizens |

To add fights by hand, drop `gangland-gang-fights-<rev>.jar` into `plugins/Gangland_Warfare/modules/`, or run
`/glw module install fights`. Restart the server after any module change.

## New waypoint permission nodes

Default true, and children of `gangland.command.player`: `gangland.command.waypoint`, `.list`, `.info`, `.select` and
`.deselect`. `info` and `select` still show only waypoints the player can use (own gang or the waypoint's node). The
staff verbs (`create`, `delete`, `type`, `gangid`, `timer`, `cooldown`, `shield`, `cost`, `radius`) stay op.

## Checklist

1. Back up `plugins/Gangland_Warfare/`.
2. Install Keystone 1.15.3 and the 0.19.0 core jar, and every module jar of the same revision.
3. Start the server once and read the console for `module.*` and `Config_Version` lines.
4. Copy new `gang_rules.yml` and `turf_npcs.yml` sections into your files if you want to change the defaults.
5. Set each level's `Cost` (or `0`), choose `Ranks.Minimum`, and set `Friendly_Fire` to your pvp rules.
6. Check each rank's permissions for the new `gangland.command.player` parent.
7. Add the fights module if you want gang fights, then create arenas with `/glw arena create`.
