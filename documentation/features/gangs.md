# Gangs

[← Back to Index](../README.md) | [Next: Economy →](./economy.md)

---

## Overview

Gangs are the social backbone of the plugin. Players form crews, build a shared bank, climb an internal rank ladder,
form alliances with other gangs, and compete for territory and money. Every gang has a single owner at the top and a
rank hierarchy that determines what each member can do.

---

## Creating a Gang

Any player can create a gang for a configurable fee (default **$100,000**). The command will ask for confirmation before
the money is deducted.

```
/glw gang create <name>
```

- Gang names must be unique by default (configurable).
- The creator automatically becomes the gang owner.
- The gang is immediately active and can accept invites.

---

## Gang List and Info

```
/glw gang list [page]      Every gang, most members first (any player)
/glw gang info [gang]      Public card: name, description, members online/total, owner, allies, created date
/glw gang members          Your own gang's members
```

- `gang info` accepts a name (any case) or a numeric id. It never shows the bank balance.
- Gang names must match `Name_Pattern` in `gang/gang_rules.yml` (default 3 to 16 letters, digits or underscores).
  Names are always unique: `Name_Duplicates` in `gang/gang_settings.yml` is ignored.
- Known gap: `gang list` and `gang info` refuse console senders (see [migration 0.14.0](../migration-0.14.0.md)).

---

## Rank Hierarchy

Gangs use the server's global rank system internally. Two ranks are reserved:

| Role                              | Description                                         |
|-----------------------------------|-----------------------------------------------------|
| **Head rank** (default: `member`) | The rank every new member starts at when they join. |
| **Tail rank** (default: `owner`)  | The highest rank — held only by the gang creator.   |

Ranks between Head and Tail are filled in by the server admin using the `/glw rank` commands. See
the [Ranks guide](./ranks.md) for how to build a hierarchy.

When a member is promoted or demoted, the system traverses up or down the rank tree automatically — there is no need to
specify which rank to promote to, it always moves to the next defined rank in the chain. When a rank has several ranks
above it, the promoter gets a clickable list to choose from. A promotion only hands out ranks below the promoter's own
and never the Tail rank: ownership moves with `/glw gang transfer`. Staff holding `gangland.admin.gang.force_rank`
(op by default) skip the rank checks, but still cannot promote anyone to the Tail rank.

---

## Member Management

| Command                      | Description                                                                  |
|------------------------------|------------------------------------------------------------------------------|
| `/glw gang invite <player>`  | Sends a join invitation. An online player has **60 seconds** to accept; an offline player has **72 hours** (`Invite_Offline_Expiry_Hours` in `mail/mail_rules.yml`, `0` = until they next log in). |
| `/glw gang accept [gang]`    | Accepts a pending gang invitation. Name the gang when you have more than one invite. Accepting one cancels the player's other gang invites. |
| `/glw gang decline [gang]`   | Declines a pending gang invitation. Name the gang when you have more than one invite. |
| `/glw gang kick <player>`    | Removes a member. You can only kick members who hold a lower rank than you.  |
| `/glw gang join <gang>`      | Joins an open gang at the starting rank. Needs the gang's recruiting set to `open`. |
| `/glw gang recruiting open\|invite` | Sets whether anyone may join (`open`) or only invited players (`invite`, the default; every existing gang starts there). Leader or a rank with `gangland.gang.invite`. |
| `/glw gang chat <message>`   | Sends a message to your online gang. `/glw gang chat` alone toggles gang chat mode; leaving the gang or quitting clears it. |
| `/glw gang bounty <gang> <amount>` | Puts money from your own balance on another gang (not your own or an ally). The gang that captures one of its turfs collects the whole bounty. Needs `gangland.command.gang.bounty` (default true). |
| `/glw gang leave`            | Leaves the gang. The owner cannot leave — they must delete the gang instead. |
| `/glw gang promote <player>` | Moves a member up one rank in the hierarchy.                                 |
| `/glw gang demote <player>`  | Moves a member down one rank in the hierarchy.                               |

---

## Gang Bank

The gang has a shared bank account separate from all personal balances. Deposits are tracked per member using a *
*contribution system** — the plugin remembers how much each individual has put in.

| Command                       | Description                                                   |
|-------------------------------|---------------------------------------------------------------|
| `/glw gang balance`           | Shows the gang's total bank balance.                          |
| `/glw gang deposit <amount>`  | Deposits money from your personal balance into the gang bank. |
| `/glw gang withdraw <amount>` | Withdraws up to your contributed amount from the gang bank.   |

> **On deletion**: When the gang is deleted, the bank is distributed proportionally to all members based on how much
> they contributed. The owner also receives a 25% refund of the gang creation fee.

---

## Customization

| Command                       | Description                                                   |
|-------------------------------|---------------------------------------------------------------|
| `/glw gang rename <name>` | Renames the gang.                                             |
| `/glw gang display <name>`    | Sets a custom display name shown in chat and (via Plaque) scoreboards. |
| `/glw gang display remove`    | Removes the custom display name, reverting to the plain name. |
| `/glw gang desc`              | Opens an anvil GUI to write a gang description.               |
| `/glw gang color`             | Opens a GUI to pick from 16 gang colors used in displays.     |

---

## Alliances

Two gangs can form an alliance. Alliances are **bidirectional** — both sides must agree.

| Command                            | Description                                                                          |
|------------------------------------|--------------------------------------------------------------------------------------|
| `/glw gang ally request <gang_id>` | Sends an alliance request to another gang. The request expires after **60 seconds**. |
| `/glw gang ally accept`            | Accepts a pending alliance request.                                                  |
| `/glw gang ally reject`            | Rejects a pending alliance request.                                                  |
| `/glw gang ally abandon <gang_id>` | Breaks an existing alliance.                                                         |

---

## Deleting a Gang

Only the gang owner can delete a gang.

```
/glw gang delete
```

The command requires confirmation. On confirmation, bank funds are distributed to members and the gang is permanently
removed.

---

## Configuration

### Gang Settings (`settings.yml` → `gang/gang_settings.yml`)

Most gang configuration moved to `gang/gang_settings.yml` in the gang module. Only `Gang.Enable` remains in `settings.yml`.

In `settings.yml`:

```yaml
Gang:
   Enable: true                   # Master toggle for gang system
```

In `gang/gang_settings.yml`:

```yaml
Name_Duplicates: false            # Whether two gangs can share the same name
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

---

[← Back to Index](../README.md) | [Next: Economy →](./economy.md)

## Levels, disband split and turf income (0.17.0)

- **Gang levels.** Gang XP comes only from turf the gang holds while a member is online (1 XP per paying turf payout by
  default). `gang/gang_rules.yml` `Progression` sets `Max_Level` and `Base_Experience`. Each level lets the gang hold one
  more turf (`turf/turf_rules.yml` `Turf_Cap`).
- **Disband split.** A disband with zero contributions splits the gang bank equally among members; the owner takes the
  rounding remainder. The disband fee refund has its own line (`Progression.Fee_Refund`).
- **Gangless players** running `/glw gang` get the onboarding screen; members get the gang menu.
- **Bounty claim.** A bounty on a gang with no turf cannot be claimed until it holds one. Disbanding a bountied gang
  destroys the bounty; posters are not refunded.
