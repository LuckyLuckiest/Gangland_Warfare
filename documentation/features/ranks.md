# Ranks

[← Trade Signs](./trade-signs.md) | [Back to Index](../README.md)

---

## Overview

The rank system lets server owners define a hierarchy of gang ranks, each with its own set of permissions. Ranks can
have **parent ranks** — when a rank inherits from a parent, it automatically gains all of that parent's permissions in
addition to its own. This makes it easy to build structured trees like Recruit → Member → Officer → Leader without
duplicating permissions at every level.

---

## How Inheritance Works

When a rank has a parent, it inherits all permissions from that parent recursively. This means:

- A rank inherits its direct parent's permissions.
- It also inherits its grandparent's permissions, and so on up the chain.
- Permissions are resolved at the time of a permission check, so changes to a parent rank take effect immediately for
  all child ranks.

**Example chain:**

```
Recruit  (can: gang.chat)
   └── Member  (can: gang.deposit, gang.invite)
          └── Officer  (can: gang.kick, gang.promote)
                 └── Leader  (can: gang.delete, gang.rename)
```

A Leader in this chain can do everything — all permissions from every rank below them are inherited.

---

## Commands

All rank commands require appropriate admin permissions.

### Rank Lifecycle

| Command                   | Description                             |
|---------------------------|-----------------------------------------|
| `/glw rank create <name>` | Creates a new rank with the given name. |
| `/glw rank delete <name>` | Permanently deletes a rank.             |
| `/glw rank list`          | Lists all configured ranks.             |
| `/glw rank info <name>`   | Shows a rank's permissions and parent.  |

### Permissions

| Command                                           | Description                       |
|---------------------------------------------------|-----------------------------------|
| `/glw rank permission add <rank> <permission>`    | Grants a permission to a rank.    |
| `/glw rank permission remove <rank> <permission>` | Revokes a permission from a rank. |

### Hierarchy

| Command                                   | Description                                          |
|-------------------------------------------|------------------------------------------------------|
| `/glw rank parent add <rank> <parent>`    | Sets a parent rank, enabling permission inheritance. |
| `/glw rank parent remove <rank> <parent>` | Removes the parent relationship.                     |
| `/glw rank traverse`                      | Displays a visual tree of the entire rank hierarchy. |

---

## Example Setup

**1. Create the ranks:**

```
/glw rank create Recruit
/glw rank create Member
/glw rank create Officer
/glw rank create Leader
```

**2. Assign permissions to each rank:**

```
/glw rank permission add Recruit gang.chat
/glw rank permission add Member gang.deposit
/glw rank permission add Member gang.invite
/glw rank permission add Officer gang.kick
/glw rank permission add Officer gang.promote
/glw rank permission add Leader gang.delete
/glw rank permission add Leader gang.rename
```

**3. Link the hierarchy:**

```
/glw rank parent add Member Recruit
/glw rank parent add Officer Member
/glw rank parent add Leader Officer
```

**4. Verify:**

```
/glw rank traverse
```

This will display the full tree with all inherited permissions at each level.

---

## API

```java
RankManager rankManager = gangland.getInitializer().getRankManager();

// Get a rank by name
Optional<Rank> rank = rankManager.getRank("Leader");

// Check if a rank has a permission (including inherited)
boolean canKick = rankManager.hasPermission(rank.get(), "gang.kick");

// Get all permissions for a rank (including all inherited)
Set<String> allPermissions = rankManager.resolvePermissions(rank.get());

// Get direct parent
Optional<Rank> parent = rankManager.getParent(rank.get());

// Set a parent
rankManager.setParent(childRank, parentRank);

// Remove parent
rankManager.removeParent(childRank);
```

---

[← Trade Signs](./trade-signs.md) | [Back to Index](../README.md)
