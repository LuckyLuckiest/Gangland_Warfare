# Command System

[Back to Developer Docs](./README.md)

---

## Overview

All player-facing commands dispatch through a single Bukkit command (`/glw`, alias `/gangland`)
registered in `plugin.yml`. Sub-commands are routed via a tree-based `Argument` system that
supports permission checks, tab completion, and typo suggestions.

**Module:** `gangland-impl`  
**Package:** `me.luckyraven.command.*`

---

## Architecture

```
Player types: /glw gang create MyGang
                │    │     │      │
                │    │     │      └── args[2] = "MyGang"
                │    │     └── args[1] = "create"  (SubArgument)
                │    └── args[0] = "gang"     (Command group)
                └── /glw command handler     (CommandManager)

CommandManager.onCommand(sender, "glw", args)
    └── root Argument.execute("glw", sender, args)
          └── traverseList(tree, args, 0)
                ├── match "gang" → GangCommand node
                │     └── match "create" → GangCreateCommand node
                │           └── SUCCESS → executeArgument(sender, args)
                ├── NO_PERMISSION → "You don't have permission"
                └── NOT_FOUND → suggestion via SpellChecker
```

---

## Core Classes

### CommandManager

Registered as the Bukkit command handler for `/glw`.

**Responsibilities:**

- Maintains the root `Argument` tree
- Dispatches `onCommand()` calls to the argument tree
- Handles tab completion via `onTabComplete()`
- Registers all sub-command groups at startup

### Argument

The core routing node in the command tree. Each `Argument` represents one level in the
command hierarchy.

**Key Fields:**

| Field                 | Type                                             | Description                        |
|-----------------------|--------------------------------------------------|------------------------------------|
| `arguments`           | `String[]`                                       | Accepted argument strings          |
| `node`                | `Tree.Node<Argument>`                            | Position in the argument tree      |
| `tree`                | `Tree<Argument>`                                 | Reference to the full tree         |
| `permission`          | `String`                                         | Required permission node           |
| `action`              | `TriConsumer<Argument, CommandSender, String[]>` | Execution handler                  |
| `executeOnPass`       | `BiConsumer<CommandSender, String[]>`            | Runs when traversal passes through |
| `displayAllArguments` | `boolean`                                        | Show all aliases in help           |

**Key Methods:**

| Method                          | Description                                       |
|---------------------------------|---------------------------------------------------|
| `execute(prefix, sender, args)` | Main entry point -- traverses tree and dispatches |
| `executeArgument(sender, args)` | Runs the action consumer                          |
| `addSubArgument(argument)`      | Attaches a child argument node                    |
| `addAllSubArguments(List)`      | Batch attach children                             |
| `addPermission(permission)`     | Registers with PermissionManager                  |
| `getArgumentString(sender)`     | Returns displayable argument strings              |
| `clone()`                       | Deep clone of node, arguments, and tree           |

**Constructors:**

```java
// Simple: single argument string, no action
Argument(JavaPlugin plugin, String argument, Tree<Argument> tree)

// With action
Argument(JavaPlugin plugin, String argument, Tree<Argument> tree,
         TriConsumer<Argument, CommandSender, String[]> action)

// With permission
Argument(JavaPlugin plugin, String argument, Tree<Argument> tree,
         TriConsumer<Argument, CommandSender, String[]> action, String permission)

// Full: multiple aliases, permission, display flag
Argument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree,
         TriConsumer<Argument, CommandSender, String[]> action, String permission,
         boolean displayAllArguments)

// Copy constructor (deep clone)
Argument(Argument other)
```

### SubArgument

Abstract base class for concrete command implementations. Extends `Argument`.

```java
abstract class SubArgument extends Argument {
    protected abstract TriConsumer<Argument, CommandSender, String[]> action();
}
```

Subclasses implement `action()` to return the execution handler.

### ArgumentResult\<T\>

Wrapper returned by tree traversal with the outcome state.

```java
enum ResultState {
    SUCCESS,        // Argument found and permission granted
    NO_PERMISSION,  // Argument found but permission denied
    NOT_FOUND       // No matching argument in tree
}
```

**Factory Methods:**

```java
ArgumentResult.success(argument)      // matched with permission
ArgumentResult.noPermission(argument) // matched without permission
ArgumentResult.notFound()             // no match
```

### Argument Types

| Type               | Purpose                                        |
|--------------------|------------------------------------------------|
| `ConfirmArgument`  | Matches the "confirm" keyword in command chain |
| `OptionalArgument` | Wildcard that matches any input (marked `?`)   |

---

## Tree Traversal Algorithm

The `traverseList()` method recursively walks the argument tree:

```
traverseList(node, list, index, sender, args):
    1. If node is null or index >= list.length → NOT_FOUND
    2. If node data doesn't match list[index] and isn't a wildcard → NOT_FOUND
    3. Check permission:
       - If has permission string and sender lacks it → NO_PERMISSION
    4. Call node.executeOnPass(sender, args)
    5. If index == last element → SUCCESS (this is the target)
    6. For each child of node:
       a. Recursively traverseList(child, list, index + 1, ...)
       b. If SUCCESS → return it
       c. If NO_PERMISSION → return NO_PERMISSION
    7. Return NOT_FOUND
```

### Not Found Handling

When no argument matches, the system provides helpful suggestions:

1. Find the last valid argument in the input using `tree.traverseLastValid()`
2. Get the children of the last valid argument (valid next options)
3. Use `SpellChecker` to find the closest match to the invalid input
4. Generate a formatted suggestion: `Did you mean: /glw gang create?`

---

## Command Groups (17 total)

### Player Commands

| Command             | Permission                | Description                           |
|---------------------|---------------------------|---------------------------------------|
| `/glw bank`         | `gangland.command.bank`   | Deposit, withdraw, statement, balance |
| `/glw bounty`       | `gangland.command.bounty` | Post bounties, view bounty board      |
| `/glw fuel`         | `gangland.command.fuel`   | Refuel vehicles/jetpacks              |
| `/glw gang`         | `gangland.command.gang`   | Create, invite, leave, disband, ally  |
| `/glw gang promote` | `gangland.command.gang`   | Promote/demote gang members           |
| `/glw wanted`       | `gangland.command.wanted` | Check wanted level                    |

### Admin Commands

| Command          | Permission                   | Description                     |
|------------------|------------------------------|---------------------------------|
| `/glw car`       | `gangland.command.car`       | Place, pickup, list vehicles    |
| `/glw civilians` | `gangland.command.civilians` | Spawn/despawn civilian NPCs     |
| `/glw cops`      | `gangland.command.cops`      | Spawn/despawn cop NPCs          |
| `/glw cuff`      | `gangland.command.cuff`      | Handcuff/release players        |
| `/glw debug`     | `gangland.command.debug`     | Debug tools, permission list    |
| `/glw item`      | `gangland.command.item`      | Give unique items               |
| `/glw jail`      | `gangland.command.jail`      | Create/delete/manage jails      |
| `/glw lootchest` | `gangland.command.lootchest` | Create/edit loot chests         |
| `/glw rank`      | `gangland.command.rank`      | Manage rank hierarchy           |
| `/glw waypoint`  | `gangland.command.waypoint`  | Create/delete waypoints         |
| `/glw weapon`    | `gangland.command.weapon`    | Give weapons, reload configs    |
| `/glw reload`    | `gangland.command.reload`    | Hot reload plugin configuration |

---

## Adding a New Command

### Step 1: Create the Command Group

Create a class extending `SubArgument` in `me.luckyraven.command.sub.yourcommand/`:

```java
package me.luckyraven.command.sub.yourcommand;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

public class YourCommand extends SubArgument {

    private final Gangland gangland;

    public YourCommand(Gangland gangland, Tree<Argument> tree) {
        super(gangland, "yourcommand", tree, null, "gangland.command.yourcommand");
        this.gangland = gangland;

        // Register sub-arguments
        addSubArgument(new YourSubCommand(gangland, tree, this));
    }

    @Override
    protected TriConsumer<Argument, CommandSender, String[]> action() {
        return (argument, sender, args) -> {
            sender.sendMessage("Usage: /glw yourcommand <sub>");
        };
    }
}
```

### Step 2: Create Sub-Commands

```java
class YourSubCommand extends SubArgument {

    private final Gangland gangland;

    protected YourSubCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
        super(gangland, "sub", tree, parent);
        this.gangland = gangland;
    }

    @Override
    protected TriConsumer<Argument, CommandSender, String[]> action() {
        return (argument, sender, args) -> {
            // Command logic here
            sender.sendMessage("Executed!");
        };
    }
}
```

### Step 3: Register in Initializer

Add the command group to the CommandManager in `Initializer.postInitialize()`:

```java
commandManager.register(new YourCommand(gangland, tree));
```

### Step 4: Add to commands.json

Add the command entry to the `commands.json` file so it appears in help/documentation.

### Step 5: Permission

The permission `gangland.command.yourcommand` is auto-registered when the Argument is
created via `addPermission()`. It will be added to Bukkit's permission registry.

---

## Tab Completion

Tab completion is driven by the argument tree:

1. Player presses Tab after typing `/glw gan`
2. CommandManager receives `onTabComplete(sender, args)`
3. Traverse tree to find the deepest matching node for current input
4. Return the `getArgumentString()` of all children of that node
5. Filter by permission and by prefix match
6. Optional arguments (prefixed with `?`) are included at the end

---

## Permission System

### Auto-Registration

When an `Argument` is created with a permission string, it calls `addPermission()`:

```java
public void addPermission(String permission) {
    if (plugin instanceof Gangland gangland) {
        gangland.getInitializer().getPermissionManager().addPermission(permission);
        return;
    }
    // Fallback: register directly with Bukkit PluginManager
    PluginManager pluginManager = Bukkit.getPluginManager();
    if (!permissions.contains(permission)) {
        pluginManager.addPermission(new Permission(permission));
    }
}
```

### Permission Format

```
gangland.command.main          # Base /glw command
gangland.command.gang          # /glw gang group
gangland.command.gang.create   # /glw gang create (if separate)
gangland.command.debug         # /glw debug
```

### Viewing Permissions

```
/glw debug perms
```

Lists all registered permissions at runtime.

---

## Error Handling

Commands catch all `Throwable` in the execute method:

```java
try {
    ArgumentResult<Argument> argument = traverseList(modifiedArg, sender, args);
    switch (argument.getState()) {
        case SUCCESS -> argument.getArgument().executeArgument(sender, args);
        case NO_PERMISSION -> sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
        case NOT_FOUND -> notFound(commandPrefix, sender, args, modifiedArg);
    }
} catch (Throwable throwable) {
    if (throwable.getMessage() != null) sender.sendMessage(throwable.getMessage());
    else sender.sendMessage("null");
    log.warn(throwable.getMessage(), throwable);
}
```

This prevents command errors from crashing the server while still logging the stack trace.
