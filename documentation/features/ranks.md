# Ranks

[← Trade Signs](./trade-signs.md) | [Back to Index](../README.md) | [Next: Database & Setup →](./database.md)

---

## Overview

The rank system defines a gang's ladder. Each rank holds a set of `gangland.gang.*` nodes, and the ranks form one global
tree shared by every gang. The tree orders promotion and demotion. A rank's own node list decides what its members may
do, with a few extra routes described below.

There is **no inheritance**. A rank does not receive the nodes of the ranks below it, and a child rank does not receive
the nodes of its parent. List the nodes on each rank that needs them.

---

## How Ranks Are Ordered

The tree is rooted at the Head rank (default `member`). Each rank sits below at most one parent. The parent link decides
the order that `/glw gang promote` and `/glw gang demote` walk, and the Tail rank (default `owner`) is the deepest leaf.

```
member   (gangland.gang.deposit)
   ╰── officer   (gangland.gang.deposit, withdraw, invite, description, turf.upgrade)
          ╰── owner   (the top rank: every gang node, always)
```

The top rank always passes every gang check, so the owner never needs its node list filled in.

---

## Who May Use a Feature

A member may use a gang feature guarded by a `gangland.gang.*` node when any of these hold:

1. The player holds the node on the server (op, or a permission plugin). This is the staff path.
2. The member's rank lists the node, or Vault grants it through the rank's Vault group (`/glw rank vaultgroup`).
3. The member holds the top rank.
4. The member's rank is at or above the lowest rank `Ranks.Minimum` names for that feature. "Above" means toward the Tail.

Clause 4 only grants access. It never removes a node a rank already holds.

---

## Fresh-Install Preset

A server with an empty rank table gets the preset from `gang/gang_rules.yml`:

- `Ranks.Preset` lists the ranks, lowest first (default `member`, `officer`, `owner`). The first should equal `Rank.Head`
  and the last `Rank.Tail` in `gang/gang_settings.yml`. A mismatch is logged and the seed uses the Head and Tail names at
  the ends.
- `Ranks.Preset_Permissions` lists the `gangland.gang.*` nodes each preset rank gets. Shipped values:

  | Rank      | Nodes |
  |-----------|-------|
  | `member`  | `gangland.gang.deposit` |
  | `officer` | `gangland.gang.deposit`, `gangland.gang.withdraw`, `gangland.gang.invite`, `gangland.gang.description`, `gangland.turf.upgrade` |
  | `owner`   | none listed; the top rank may do everything anyway |

The preset is seeded only into an empty table. An existing rank tree is never changed by it, and edits go through the
commands below. The seed is completed in one pass and is repaired on the next boot if a restart interrupted it.

---

## Minimum Rank per Feature

`Ranks.Minimum` maps each feature to the lowest rank that may use it. A rank above that one may use it too. An unknown
rank name logs one warning and turns that entry off. The node lists and the top rank still pass.

| Feature (`Ranks.Minimum` key) | Lowest rank | Gang command or node |
|-------------------------------|-------------|----------------------|
| `Chat`                        | member      | `/glw gang chat` (`gangland.gang.chat`) |
| `Deposit`                     | member      | `/glw gang deposit` |
| `Safehouse`                   | member      | `/glw gang safehouse`, `rally join` |
| `Fight`                       | officer     | gang fights (reserved; not a command in this release) |
| `Rally`                       | officer     | `/glw gang rally` |
| `Invite`                      | officer     | `/glw gang invite` |
| `Kick`                        | officer     | `/glw gang kick` |
| `Promote`                     | officer     | `/glw gang promote` |
| `Demote`                      | officer     | `/glw gang demote` |
| `Withdraw`                    | officer     | `/glw gang withdraw` |
| `Safehouse_Manage`            | officer     | `/glw gang safehouse set`, `remove` |
| `Recruiting`                  | officer     | `/glw gang recruiting` |
| `Description`                 | officer     | `/glw gang desc` |
| `Ally`                        | owner       | `/glw gang ally ...` |
| `Rename`                      | owner       | `/glw gang rename` |
| `Display`                     | owner       | `/glw gang display` |
| `Color`                       | owner       | `/glw gang color` |
| `Notice`                      | owner       | `/glw gang notice` |
| `Upgrade`                     | owner       | `/glw gang upgrade` |
| `Friendly_Fire`               | owner       | `/glw gang pvp`, `/glw gang ally pvp` |

When a denied member runs a gated command, the message names the feature and the lowest rank that may use it.

---

## Gang Permission Nodes

Rank permission add accepts these nodes, and the server nodes that come with them. Staff-only nodes (`gangland.admin.gang.*`,
`gangland.gang.balance.others`, `gangland.gang.ranking.wealth`) are never put on a rank.

| Node | Used for |
|------|----------|
| `gangland.gang.withdraw` | gang bank withdraw |
| `gangland.gang.deposit` | gang bank deposit |
| `gangland.gang.rename` | rename the gang |
| `gangland.gang.description` | gang description |
| `gangland.gang.display` | gang display name |
| `gangland.gang.color` | gang colour |
| `gangland.gang.invite` | invite players |
| `gangland.gang.ally` | alliance requests, accepts, abandon |
| `gangland.turf.upgrade` | buy guards and boosts at the Quartermaster |
| `gangland.gang.kick` | kick members (lower rank only) |
| `gangland.gang.promote` | promote members |
| `gangland.gang.demote` | demote members |
| `gangland.gang.chat` | gang and ally chat |
| `gangland.gang.safehouse` | teleport to a safehouse; join a rally |
| `gangland.gang.safehouse.manage` | set and remove safehouses |
| `gangland.gang.rally` | call a rally |
| `gangland.gang.pvp` | `/glw gang pvp` |
| `gangland.gang.ally.pvp` | `/glw gang ally pvp` |
| `gangland.gang.upgrade` | `/glw gang upgrade confirm` |
| `gangland.gang.fight` | gang fights (reserved) |
| `gangland.gang.notice` | `/glw gang notice` |
| `gangland.gang.recruiting` | `/glw gang recruiting` |

---

## Commands

All rank commands require appropriate admin permissions.

### Rank Lifecycle

| Command                   | Description                             |
|---------------------------|-----------------------------------------|
| `/glw rank create <name>` | Creates a new rank with the given name. |
| `/glw rank delete <name>` | Permanently deletes a rank. The Head and Tail ranks, a rank a member still holds, and a rank with ranks above it are refused. |
| `/glw rank list`          | Lists all configured ranks.             |
| `/glw rank info <name>`   | Shows a rank's permissions and parent.  |

### Permissions

| Command                                           | Description                       |
|---------------------------------------------------|-----------------------------------|
| `/glw rank permission add <rank> <permission>`    | Grants a permission to a rank.    |
| `/glw rank permission remove <rank> <permission>` | Revokes a permission from a rank. |
| `/glw rank vaultgroup <rank> <group>`             | Links the rank to a Vault permission group; members gain that group on assignment. |
| `/glw rank vaultgroup <rank> clear`               | Removes the Vault group link. |

### Hierarchy

| Command                                   | Description                                          |
|-------------------------------------------|------------------------------------------------------|
| `/glw rank parent add <rank> <parent>`    | Sets a parent rank, which orders promotion and demotion. A rank sits above one rank only, and a rank already below `<rank>` is refused. |
| `/glw rank parent remove <rank> <parent>` | Removes the parent relationship.                     |
| `/glw rank traverse`                      | Displays a visual tree of the entire rank hierarchy. |

---

## Example Setup

This adds an extra rank between `member` and `owner` and gives it the officer-style nodes by hand.

**1. Create the rank:**

```
/glw rank create captain
```

**2. Give it the nodes it needs (nodes are not inherited):**

```
/glw rank permission add captain gangland.gang.deposit
/glw rank permission add captain gangland.gang.withdraw
/glw rank permission add captain gangland.gang.invite
/glw rank permission add captain gangland.gang.kick
```

**3. Link the hierarchy:**

```
/glw rank parent add captain officer
/glw rank parent add owner captain
```

**4. Verify:**

```
/glw rank traverse
```

This displays the full tree. Each rank's own permissions are listed on it with `/glw rank info <name>`.

---

## API

```java
RankManager rankManager = gangland.getContext().get(RankManager.class);

// Get a rank by name
Rank owner = rankManager.get("owner");

// Grant a permission to a rank (returns false if the rank already holds it)
rankManager.addPermission(owner, "gangland.gang.kick");

// Set a parent (returns false when refused)
rankManager.addParent(owner, captain);

// Remove a parent
rankManager.removeParent(owner, captain);
```

---

[← Trade Signs](./trade-signs.md) | [Back to Index](../README.md) | [Next: Database & Setup →](./database.md)
