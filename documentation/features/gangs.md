# Gangs

[← Back to Index](../README.md) | [Next: Economy →](./economy.md)

---

## Overview

Gangs are the social backbone of the plugin. Players form crews, build a shared bank, climb an internal rank ladder,
form alliances with other gangs, level up by holding turf, and compete for territory and money. Every gang has a single
owner at the top and a rank hierarchy that decides what each member may do.

The gang module ships as the runtime module `gangland-gang`. Its rules live in `gang/gang_rules.yml`, its settings in
`gang/gang_settings.yml`, and its player-facing text in `gang/gang_messages.yml`. The rank nodes are listed in the
[Ranks guide](./ranks.md).

---

## Creating a Gang

Any player can create a gang for a configurable fee (default **$100,000**, `Account.Create_Cost`). The command will ask
for confirmation before the money is deducted.

```
/glw gang create <name>
```

- Gang names must match `Name_Pattern` in `gang/gang_rules.yml` (default 3 to 16 letters, digits or underscores) and
  are always unique: `Name_Duplicates` in `gang/gang_settings.yml` is ignored. Uniqueness ignores colour, case and
  look-alike letters, so two names that look the same are refused too.
- The creator automatically becomes the gang owner.
- The gang is immediately active and can accept invites.

---

## Gang List, Info and Members

```
/glw gang list [page]            Every gang, most members first (any player, console too)
/glw gang info <gang>            Public card: name, description, members online/total, owner, allies, created date
/glw gang info <player>          The card of the gang a player belongs to
/glw gang members [page]         Your own gang's members, grouped by rank (owner rank first)
/glw gang profile [player]       One player's gang card: gang, rank, joined date, kills, deaths, assists
/glw gang ranking [stat] [page]  The gang ranking (see Ranking and Profiles below)
```

- `gang info` takes a gang name (any case), a numeric id, or a player's name. It never shows the bank balance unless the
  viewer holds `gangland.gang.balance.others` (staff by default).
- `gang list` and `gang info <gang>` work from the console as well as for players.

---

## Rank Hierarchy

Gangs use the server's global rank tree. Two ranks are reserved:

| Role                              | Description                                         |
|-----------------------------------|-----------------------------------------------------|
| **Head rank** (default: `member`) | The rank every new member starts at when they join. |
| **Tail rank** (default: `owner`)  | The highest rank — held by the gang creator, and by whoever `/glw gang transfer` hands it to. |

Ranks between Head and Tail are built by the server admin with the `/glw rank` commands. See the [Ranks guide](./ranks.md)
for how to build a hierarchy and which node each rank holds.

Promotion and demotion walk the tree one step at a time, so there is no need to name the target rank. When a rank has
several ranks above it, the promoter gets a clickable list to choose from. A promotion only hands out ranks below the
promoter's own and never the Tail rank: ownership moves with `/glw gang transfer`. Staff holding
`gangland.admin.gang.force_rank` (op by default) skip the rank-hierarchy checks, but still cannot promote anyone to the
Tail rank.

A member may use a gated feature when their rank holds its node, or when their rank is at or above the lowest rank
`Ranks.Minimum` names for that feature, or when they hold the top rank. A denied action names that lowest rank, for
example "Withdraw needs officer or higher." The minimums are listed in the [Ranks guide](./ranks.md#minimum-rank-per-feature).

---

## Member Management

| Command                      | Description                                                                  |
|------------------------------|------------------------------------------------------------------------------|
| `/glw gang invite <player>`  | Sends a join invitation. An online player has **60 seconds** to accept; an offline player has **72 hours** (`Invite_Offline_Expiry_Hours` in `mail/mail_rules.yml`, `0` = until they next log in). Minimum rank: officer. |
| `/glw gang invite` (no arguments) | Lists your gang's outgoing pending invites. `/glw gang invite cancel <name>` cancels one. |
| `/glw gang accept [gang]`    | Accepts a pending gang invitation. Name the gang when you have more than one invite. Accepting one cancels the player's other gang invites. A full gang refuses. |
| `/glw gang decline [gang]`   | Declines a pending gang invitation. Name the gang when you have more than one invite. |
| `/glw gang kick <player>`    | Removes a member. You can only kick members who hold a lower rank than you. Minimum rank: officer. |
| `/glw gang join <gang>`      | Joins an open gang at the starting rank. Needs the gang's recruiting set to `open`. A full gang refuses. |
| `/glw gang recruiting <open\|invite>` | Sets whether anyone may join (`open`) or only invited players (`invite`, the default; every existing gang starts there). Minimum rank: officer. |
| `/glw gang leave`            | Leaves the gang. The owner cannot leave — they must delete the gang or transfer it first. Refused for 15 seconds after a rival gang hit a member (see Friendly Fire). |
| `/glw gang promote <player>` | Moves a member up one rank in the hierarchy. Minimum rank: officer.          |
| `/glw gang demote <player>`  | Moves a member down one rank in the hierarchy. Minimum rank: officer.        |
| `/glw gang transfer <player>` | Hands gang ownership to another member. The old owner steps down.           |
| `/glw gang bounty <gang> <amount>` | Puts money from your own balance on another gang (not your own or an ally). The gang that captures one of its turfs collects the whole bounty, unless that would take the capturing gang's bank over `Account.Maximum_Balance`; then the bounty stays on the target. Needs `gangland.command.gang.bounty` (default true). |

---

## Gang Bank

The gang has a shared bank account separate from all personal balances. Deposits are tracked per member using a
**contribution system** — the plugin remembers how much each individual has put in.

| Command                       | Description                                                   |
|-------------------------------|---------------------------------------------------------------|
| `/glw gang balance`           | Shows the gang's total bank balance.                          |
| `/glw gang deposit <amount>`  | Deposits money from your personal balance into the gang bank. Minimum rank: member. |
| `/glw gang withdraw <amount>` | Withdraws up to your contributed amount from the gang bank. Minimum rank: officer. |

> **On deletion**: When the gang is deleted, the bank is distributed to members based on how much they contributed. A
> disband with zero contributions splits the bank equally among members; the owner takes the rounding remainder. The
> owner also receives a refund of a quarter of the creation fee (fixed in code; the notice line is `Fee_Refund` in `gang/gang_messages.yml`). A bounty on a gang is
> destroyed with the gang; posters are not refunded.

---

## Levels

Gangs level up by holding turf. Levels unlock more members, safehouses, allies and a chat prefix.

- **XP.** A gang earns XP only from turf it holds while a member is online: one XP per paying turf payout by default
  (`turf/turf_rules.yml` `Income`). `gang/gang_rules.yml` `Progression` sets `Max_Level` (20) and `Base_Experience`
  (200). Level 1 needs 200 XP; each later level needs `Base_Experience` × n^1.5 more than the one before (200, then
  566, then 1,039, ...). Each level lets the gang hold one more turf (`turf/turf_rules.yml` `Turf_Cap`).
- **Cost 0 = automatic.** A level whose `Cost` is `0` is claimed as soon as the XP is there.
- **Paid claim.** A level with a `Cost` waits for you. Once the XP bar is full, XP keeps banking and the level is not
  claimed until an owner runs the command below. The fee comes out of the gang bank, once.

```
/glw gang upgrade              See what the next level costs: the XP and the money still missing
/glw gang upgrade confirm      Claim the next level when the XP bar is full; the fee is paid from the gang bank
```

Minimum rank for both: owner (`Ranks.Minimum.Upgrade`).

Level table excerpt (`gang/gang_rules.yml` `Levels`; a missing row inherits the row before it):

| Level | Cost (bank)   | Max members | Max safehouses | Max allies | Chat prefix |
|-------|---------------|-------------|----------------|------------|-------------|
| 0     | 0 (automatic) | 8           | 1              | 1          | none        |
| 1     | 20,000        | 10          | 1              | 1          | `[1]`       |
| 2     | 40,000        | 12          | 2              | 2          | `[2]`       |
| 3     | 75,000        | 14          | 2              | 2          | `[3]`       |
| 5     | 180,000       | 18          | 3              | 3          | `[5]`       |
| 10    | 455,000       | 24          | 5              | 3          | `[10]`      |
| 20    | 1,205,000     | 29          | 5              | 3          | `[20]`      |

- **Level rewards.** `Levels.Level_<n>.Commands` runs from the console for every member older than
  `Reward_Min_Member_Age_Hours` (24 hours). The shipped rows run nothing. Tokens: `%gang%` (the ASCII name), `%gang_id%`,
  `%player%`, `%level%`. A row that inherits from the one before carries no commands, so a reward never repeats.

---

## Limits

The limits come from the gang's current level row. A limit of `0` means unlimited.

| Limit            | Enforced by                        |
|------------------|------------------------------------|
| `Max_Members`    | invite, join, accept               |
| `Max_Allies`     | ally request and accept            |
| `Max_Safehouses` | `/glw gang safehouse set`          |

A gang at its cap is refused with a message that shows the count and the maximum. Turf is capped separately by `Turf_Cap`
in `turf/turf_rules.yml`.

---

## Safehouses and Rally

A safehouse is a named spot the gang can teleport to. A rally calls the gang to one place.

```
/glw gang safehouse                 Teleport to one of your safehouses after a short warm-up (without a name: lists them)
/glw gang safehouse set <name>      Set a safehouse where you stand
/glw gang safehouse remove <name>   Remove a safehouse
/glw gang safehouse list            List your gang's safehouses
/glw gang rally [safehouse]         Ask your online gang to join you, or to meet at a safehouse
/glw gang rally join                Answer your gang's rally call (a normal gang teleport with a warm-up)
```

- Names are 1 to 16 letters, digits or underscores, and may not be `set`, `remove`, `list` or `join`.
- `Safehouse.Warm_Up_Seconds` (5) and `Safehouse.Cooldown_Seconds` (60) apply to teleports. `Safehouse.Cost` (0) is
  charged after a successful safehouse teleport; a rally join never pays.
- `Safehouse.Safe_Check` (on) refuses a spot that has lava, fire, a cactus, a one-block gap or no floor. It is checked
  when a safehouse is set, when a rally is sent and again on each arrival.
- A rally call stays open for `Safehouse.Rally_Consent_Seconds` (60). Members answer with `rally join`.
- **Turf fights refuse.** With the turf module installed, a gang teleport (safehouse or rally join) is refused when it
  starts inside a turf under attack, lands inside or within Teleport_Guard.Buffer_Blocks (48, `turf/turf_rules.yml`) of
  a turf under attack, lands inside a turf a rival gang holds, or while your gang is the owner or challenger of any
  running turf fight. A safehouse cannot be set inside a rival turf or a turf under attack.
- A member tagged by a rival gang (see Friendly Fire) cannot use any gang teleport until the tag runs out.
- Safehouses and rallies are off in a world listed in `Worlds.Disabled`, and everywhere when `Safehouse.Enabled` is
  `false`.
- Minimum ranks: `Safehouse` member (teleport; rally join is open to every invited member, not checked yet),
  `Safehouse_Manage` officer (set and remove), `Rally` officer (call a rally).

---

## Chat

```
/glw gang chat <message>         Sends one message to your online gang
/glw gang chat                   Switches gang chat mode on or off; while on, everything you type goes to your gang
/glw gang chat allies <message>  Sends one message to your gang and its allies
/glw gang chat allies            Switches ally chat mode on or off
```

- Gang lines use `Social.Chat_Format` and ally lines use the `Chat` section of `gang/gang_messages.yml`. Both carry the
  sender's level prefix (`Levels.Level_<n>.Chat_Prefix`).
- Chat mode is kept in memory and forgotten when you quit. Leaving the gang also clears it.
- `Chat.Log_To_Console` (off) also prints gang and ally lines to the server console.
- `/glw option gang spy` (staff, see Staff Tools) gives a staff member a copy of every gang and ally line, in
  `Chat.Spy_Format`.
- `Chat.Public_Tag.Enabled` (off) lets `{glw_gang}` in the public chat format show the sender's gang, with
  `Chat.No_Gang_Tag` shown for a gangless player.
- Minimum rank: `Chat` member. Not checked yet: every member may chat, whatever `Ranks.Minimum.Chat` says.

---

## Friendly Fire

One rule decides every player-versus-player hit between gangmates and allies. `gang/gang_rules.yml`
`Friendly_Fire` sets the defaults. The rule covers melee, arrows, potions, fire, pets, TNT and guns (guns need the
Bartizan plugin). The steps below run in order; the first one that matches decides:

1. **Gangless or gang-free world.** A player with no gang on either side, or a hit in a world listed in `Worlds.Disabled`
   (or any world when `Gang.Enable` is off), is never blocked.
2. **Hitting yourself.** Blocked only when `Friendly_Fire.Allow_Self_Damage` is `false`. The default is `true`, so your
   own arrows and splash potions hit you (bow boosting works).
3. **Region flag.** Reserved for a region plugin (WorldGuard). No region plugin is wired in this release, so this step
   has no effect yet.
4. **Per-world override.** `Friendly_Fire.Worlds` (`ALLOW` or `DENY` per world name) decides, for gangmates and allies
   only. A `DENY` never starts protecting rivals. The key ships commented out, so no world is overridden by default.
5. **Gangmates.** Blocked unless `Friendly_Fire.Global` is `GANG_CHOICE` (the default) and your gang turned on
   `/glw gang pvp on`, or `Global` is `ON`. `Global: OFF` always blocks.
6. **Allies.** Blocked unless both alliance rows are on (`/glw gang ally pvp <gang> on`, each side sets its own row),
   and `Global` is not `OFF`.
7. **Rival gangs.** Never blocked by this rule. It only ever protects friends.

Potions between friends follow `Friendly_Fire.Potions`: `NEGATIVE_ONLY` (the default) blocks only harmful potions,
`ALL` blocks every effect.

Out of the rule, by design: guards and the Quartermaster (player-against-guard protection always applies), cars
(access, not damage), and capture counting on turfs.

A member hit by a member of a rival gang is **tagged** for `Combat.Tag_Seconds` (15): they cannot leave the gang or take
a gang teleport for that long. Only hits between members of two different gangs tag.

Minimum rank: `Friendly_Fire` owner for `/glw gang pvp` and `/glw gang ally pvp`.

```
/glw gang pvp <on|off>                Let members of your gang hurt each other, or stop it
/glw gang ally pvp <gang> <on|off>    Let your gang and an ally hurt each other; it works only when both gangs turned it on
```

---

## Ranking and Profiles

```
/glw gang ranking [stat] [page]    The gang ranking, in chat and in placeholders
/glw gang profile [player]         A player's gang card, in chat
```

- Stats: `kills`, `kdr`, `assists`, `level`, `members`, `online` for everyone. `balance` and `money` (the members'
  money) need `gangland.gang.ranking.wealth` (staff by default). Turfs held come from the turf module.
- `/glw gang ranking` shows kills by default. `kdr` only lists gangs with at least `Ranking.Min_Kills` (10) kills.
  `Ranking.Page_Size` is 10.
- **What never counts.** A kill is never counted when the killer and the victim are gangmates or allies, when the killer
  has no gang, when the victim is a member younger than `Stats.Min_Victim_Member_Age_Hours` (24), or in a world listed in
  `Worlds.Disabled`. The same killer on the same victim counts again only after `Stats.Repeat_Kill_Cooldown_Seconds`
  (300). A victim counts at most `Stats.Max_Kills_Per_Victim_Per_Day` (3) times a day. An assist counts only when the
  assister hit the victim within `Stats.Assist_Window_Seconds` (10) of the death.
- The gang ranking and profile numbers refresh about every `Stats.Snapshot_Seconds` (60).
- `Placeholders.Default_Value` (`-`) fills in for a gangless player or an empty board row.
- Staff reset a player's kills, deaths and assists with `/glw option gang stats reset <player>`.

---

## Notice

```
/glw gang notice <text>   Set the notice members see when they log in
/glw gang notice clear    Remove the notice
```

- Every member sees the notice once per login while it is set. A notice longer than 200 plain characters (colour codes
  do not count) is refused.
- Minimum rank: `Notice` owner.
- Titles: a gang's members see a title on level-up; a member sees one on join and on disband.

---

## Display Names and Names

```
/glw gang rename <name>          Renames the gang. Uses the Name_Pattern rule.
/glw gang display <name>         Sets a custom display name shown in chat and (via Plaque) scoreboards
/glw gang display remove         Removes the custom display name, reverting to the plain name
/glw gang desc                   Opens an anvil GUI to write a gang description
/glw gang color                  Opens a GUI to pick from 16 gang colors used in displays
```

- The display name may use any language and hex colours. `Display_Name.Allow_Hex`, `Parse_Colours`, `Max_Length` (24)
  and `Blocked_Words` apply. Uniqueness of display names ignores colour, case and look-alike letters.
- Minimum ranks: `Rename` owner, `Display` owner, `Color` owner, `Description` officer.

---

## Alliances

Two gangs can form an alliance. Alliances are **bidirectional** — both sides must agree.

| Command                               | Description                                                                          |
|---------------------------------------|--------------------------------------------------------------------------------------|
| `/glw gang ally request <gang_id>`    | Sends an alliance request to another gang. The request expires after **60 seconds**. |
| `/glw gang ally accept [gang]`        | Accepts a pending alliance request. Name the gang when more than one is pending.     |
| `/glw gang ally reject [gang]`        | Rejects a pending alliance request. Name the gang when more than one is pending.     |
| `/glw gang ally pending`              | Lists your gang's outgoing pending alliance requests.                                |
| `/glw gang ally pending cancel <gang>` | Cancels one of your outgoing pending alliance requests.                             |
| `/glw gang ally abandon <gang>`       | Breaks an existing alliance with one of your own allies (by name, `name:id` or id).  |
| `/glw gang ally pvp <gang> <on\|off>` | Turns ally friendly fire on or off for your side. See Friendly Fire.                 |

Minimum rank: `Ally` owner. Each side's `Max_Allies` limit applies to requests and accepts.

---

## Deleting a Gang

Only the gang owner can delete a gang.

```
/glw gang delete
```

The command requires confirmation. On confirmation, bank funds are distributed to members, the fee refund goes to the
owner, and the gang is permanently removed.

---

## Staff Tools

Staff run `/glw option gang ...`. Every verb works from the console except `spy` and `safehouse tp`, which need a player.

| Command                                                              | What it does |
|----------------------------------------------------------------------|--------------|
| `/glw option gang rank <target> <rank>`                              | Sets a member's rank. Staff with `gangland.admin.gang.force_rank` skip the hierarchy checks. |
| `/glw option gang disband <gang> [confirm]`                          | Disbands a gang; members are paid out as in a normal disband. The word `confirm` is required. |
| `/glw option gang leader <gang> <player>`                            | Makes a member the gang's leader (owner). |
| `/glw option gang bank <gang> <balance\|give\|take\|reset> [amount]` | Reads or changes a gang bank. Every change is logged with the balance before and after. |
| `/glw option gang safehouse <gang> <list\|tp\|remove> [name]`        | Lists, visits or removes a gang's safehouses. `tp` teleports the staff member directly, with no fee and no warm-up. |
| `/glw option gang stats reset <player>`                              | Resets a player's gang kills, deaths and assists. |
| `/glw option gang spy`                                               | Reads every gang and ally line (needs `gangland.admin.gang.spy`, op by default). |
| `/glw option gang purge <preview\|run>`                              | Lists, or disbands, gangs inactive for `Purge.Inactive_Days`. Off by default (`Purge.Enabled: false`). |

Staff-only nodes (op by default through `gangland.admin.gang`): `gangland.admin.gang.spy`,
`gangland.admin.gang.force_rank`, `gangland.gang.balance.others`, `gangland.gang.ranking.wealth`,
`gangland.gang.fight.bypass_commands`.

---

## Worlds

- `gang/gang_rules.yml` `Worlds.Disabled` lists the worlds where gangs do not exist. Gang commands there answer
  "Gangs are disabled in this world." Friendly fire, gang stats, safehouses and rallies are off there too.
- `Gang.Enable` in `settings.yml` turns gangs off everywhere.

---

## Short Labels

When the gang module is installed, three short labels run gang commands:

| Label  | Runs                     |
|--------|--------------------------|
| `/gw`  | `/glw gang`              |
| `/gwc` | `/glw gang chat`         |
| `/gwa` | `/glw gang chat allies`  |

Each label respects the same permissions as the command it runs.

---

## Configuration

### Gang Settings (`settings.yml` → `gang/gang_settings.yml`)

Most gang configuration lives in `gang/gang_settings.yml` in the gang module. Only `Gang.Enable` remains in `settings.yml`.

In `settings.yml`:

```yaml
Gang:
   Enable: true                   # Master toggle for gang system
```

In `gang/gang_settings.yml`:

```yaml
Name_Duplicates: false            # Ignored: gang names are always unique
Display_Name_Char: '*'            # Character prepended/appended to the gang display name in chat
                                  # to distinguish it from the plain name

Rank:
   Head: "member"                 # Rank automatically assigned to every new member on join
   Tail: "owner"                  # The top rank — always held by the gang creator

Account:
   Initial_Balance: 0             # Starting gang bank balance when the gang is created
   Create_Cost: 100_000           # Money deducted from the creator's balance to form a gang. Set to 0 to remove the fee.
   Maximum_Balance: 100_000_000_000  # Hard cap on the gang bank balance
   Contribution_Rate: 1_000       # Divisor that converts deposited money into contribution units.
                                  # e.g. rate=1000: depositing $1 = 0.001 contribution, $1000 = 1 contribution.
                                  # Withdrawals are limited to the player's accrued contribution units.
```

### Gang Rules (`gang/gang_rules.yml`)

One top-level section per feature (`# ---- Name ----` blocks):

| Section          | Holds |
|------------------|-------|
| `Name_Pattern`   | Allowed gang names |
| `Ranks`          | `Preset`, `Preset_Permissions`, `Minimum` (see the [Ranks guide](./ranks.md)) |
| `Progression`    | `Max_Level`, `Base_Experience` |
| `Levels`         | `Reward_Min_Member_Age_Hours`, one `Level_<n>` row per level (cost, limits, prefix, commands) |
| `Commands`       | `On_Join`, `On_Leave`, `On_Kick` console lines |
| `Purge`          | `Enabled`, `Inactive_Days`, `Member_Inactive_Days`, `First_Run_Delay_Hours`, `Refund_Fee` |
| `Worlds`         | `Disabled` |
| `Friendly_Fire`  | `Global`, `Allow_Self_Damage`, `Potions`, `Worlds` |
| `Combat`         | `Tag_Seconds` |
| `Safehouse`      | `Enabled`, `Warm_Up_Seconds`, `Cooldown_Seconds`, `Cost`, `Safe_Check`, `Rally_Consent_Seconds` |
| `Chat`           | `Log_To_Console`, `No_Gang_Tag`, `Public_Tag.Enabled` |
| `Stats`          | `Assist_Window_Seconds`, `Min_Victim_Member_Age_Hours`, `Repeat_Kill_Cooldown_Seconds`, `Max_Kills_Per_Victim_Per_Day`, `Snapshot_Seconds` |
| `Ranking`        | `Min_Kills`, `Page_Size` |
| `Placeholders`   | `Default_Value` |
| `Display_Name`   | `Allow_Hex`, `Parse_Colours`, `Max_Length`, `Blocked_Words` |

## Turf income and the bounty claim (0.17.0)

- **Gang XP from turf.** Gang XP comes only from held turf while a member is online (see Levels above).
- **Gangless players** running `/glw gang` get the onboarding screen; members get the gang menu.
- **Bounty claim.** A bounty on a gang with no turf cannot be claimed until it holds one. Disbanding a bountied gang
  destroys the bounty; posters are not refunded.

---

[← Back to Index](../README.md) | [Next: Economy →](./economy.md)
