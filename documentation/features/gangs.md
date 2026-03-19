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

## Rank Hierarchy

Gangs use the server's global rank system internally. Two ranks are reserved:

| Role                              | Description                                         |
|-----------------------------------|-----------------------------------------------------|
| **Head rank** (default: `member`) | The rank every new member starts at when they join. |
| **Tail rank** (default: `owner`)  | The highest rank — held only by the gang creator.   |

Ranks between Head and Tail are filled in by the server admin using the `/glw rank` commands. See
the [Ranks guide](../v0.7.3-DEV/ranks.md) for how to build a hierarchy.

When a member is promoted or demoted, the system traverses up or down the rank tree automatically — there is no need to
specify which rank to promote to, it always moves to the next defined rank in the chain.

---

## Member Management

| Command                      | Description                                                                  |
|------------------------------|------------------------------------------------------------------------------|
| `/glw gang invite <player>`  | Sends a join invitation. The invited player has **60 seconds** to accept.    |
| `/glw gang accept`           | Accepts a pending gang invitation.                                           |
| `/glw gang kick <player>`    | Removes a member. You can only kick members who hold a lower rank than you.  |
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
| `/glw gang rename <new name>` | Renames the gang.                                             |
| `/glw gang display <name>`    | Sets a custom display name shown in chat and scoreboards.     |
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

In `settings.yml`:

```yaml
Gang:
   Enable: true
   Name_Duplicates: false        # Whether two gangs can share the same name

   Rank:
      Head: "member"              # Rank assigned to every new joiner
      Tail: "owner"               # The top rank, held by the gang creator

   Account:
      Initial_Balance: 0          # Starting gang bank balance
      Create_Cost: 100_000        # Fee to create a gang
      Maximum_Balance: 100_000_000_000
      Contribution_Rate: 1_000    # Internal divisor used to track contributions
```

---

[← Back to Index](../README.md) | [Next: Economy →](./economy.md)
