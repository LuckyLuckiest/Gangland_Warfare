# Ranks

[← Trade Signs](./trade-signs.md) | [Back to Index](../README.md) | [Next: Database & Setup →](./database.md)

---

## Overview

The rank system lets server owners define a hierarchy of gang ranks, each with its own set of permissions. A rank has
exactly the permissions listed on it. A **parent rank** orders the ranks: promotion and demotion move a member along the
parent chain (Recruit → Member → Officer → Leader). A parent does not pass its permissions down to its children.

---

## How Ranks Are Ordered

Each rank sits above at most one parent. The parent link decides the order that `/glw gang promote` and
`/glw gang demote` walk. Permission checks read only the permissions listed on the rank itself (`Rank.contains`), and a
member's own permission check does not walk the parent chain.

```
Recruit  (gang.chat)
   ╰── Member  (gang.deposit, gang.invite)
          ╰── Officer  (gang.kick, gang.promote)
                 ╰── Leader  (gang.delete, gang.rename)
```

A Leader holds only `gang.delete` and `gang.rename`. To give a Leader the kick permission too, add `gang.kick` to Leader.

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

### Hierarchy

| Command                                   | Description                                          |
|-------------------------------------------|------------------------------------------------------|
| `/glw rank parent add <rank> <parent>`    | Sets a parent rank, which orders promotion and demotion. A rank sits above one rank only, and a rank already below `<rank>` is refused. |
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

This displays the full tree. Each rank's own permissions are listed on it.

---

## API

```java
RankManager rankManager = gangland.getContext().get(RankManager.class);

// Get a rank by name
Rank leader = rankManager.get("Leader");

// Grant a permission to a rank (returns false if the permission is unknown)
rankManager.addPermission(leader, "gang.kick");

// Set a parent (returns false when refused)
rankManager.addParent(leader, officer);

// Remove a parent
rankManager.removeParent(leader, officer);
```

---

[← Trade Signs](./trade-signs.md) | [Back to Index](../README.md) | [Next: Database & Setup →](./database.md)
