# Economy

[← Gangs](./gangs.md) | [Back to Index](../README.md) | [Next: Waypoints →](./waypoints.md)

---

## Overview

Every player has two separate money pools: a **personal balance** (cash on hand) and a **personal bank account** (stored
savings). Cash is what you spend day to day — on gang creation, waypoints, upgrades. The bank is a higher-capacity vault
that requires a one-time creation fee to unlock.

Death can cost you a percentage of your cash balance, so keeping your money in the bank is the safer play.

---

## Personal Balance

Your balance is the money you carry. It is what gets spent on most actions in the plugin — gang creation, teleportation
costs, bounties, etc.

| Command        | Description                     |
|----------------|---------------------------------|
| `/glw balance` | View your current cash balance. |

---

## Personal Bank

The bank is an optional secondary account with a much higher maximum balance. Creating one costs money (default **$5,000
**). Once created, you can deposit and withdraw freely.

| Command                       | Description                                                             |
|-------------------------------|-------------------------------------------------------------------------|
| `/glw bank create`            | Opens a confirmation prompt to create your bank account (costs $5,000). |
| `/glw bank delete`            | Permanently closes your bank account.                                   |
| `/glw bank deposit <amount>`  | Moves money from your balance into the bank.                            |
| `/glw bank withdraw <amount>` | Moves money from the bank to your balance.                              |
| `/glw bank balance`           | View your bank account balance.                                         |

---

## Death Penalty

By default, dying causes you to lose **15% of your cash balance**. This only applies if your balance is above a minimum
threshold (default **$1,000**). Money in the bank is never affected by death.

```
Loss = balance × 0.15
```

This is fully configurable and can be disabled entirely.

The death money behavior has two modes — formula-based loss or server command execution:

- **Formula mode** (default): calculates money lost/gained using the configured formula.
- **Command mode**: runs a list of server commands instead and ignores the formula entirely. Useful for custom
  punishments via other plugins.

When `Command.Enable: true`, the formula is bypassed completely.

---

## Admin Economy Commands

Admins can adjust balances for individual players, all online players, or themselves using a specifier syntax.

| Specifier   | Targets                       |
|-------------|-------------------------------|
| `@<player>` | A specific player by name.    |
| `*`         | All currently online players. |
| `**`        | Only yourself (the sender).   |

| Command                                      | Description                                   |
|----------------------------------------------|-----------------------------------------------|
| `/glw economy deposit <specifier> <amount>`  | Adds money to the target's balance.           |
| `/glw economy withdraw <specifier> <amount>` | Removes money from the target's balance.      |
| `/glw economy set <specifier> <amount>`      | Sets the target's balance to an exact amount. |
| `/glw economy reset <specifier>`             | Resets the target's balance to 0.             |

**Examples:**

```
/glw economy deposit @Steve 5000       → give Steve $5,000
/glw economy set * 0                   → wipe all online players' balances
/glw economy deposit ** 99999          → give yourself $99,999
```

---

## Mob Kills

Killing mobs can drop a small random cash reward. The amount is randomly chosen between the configured minimum and
maximum values.

---

## Configuration

In `settings.yml`:

```yaml
Money_Symbol: '$'                 # Symbol displayed before all money values

Balance_Format:
   Enable: true                   # Whether to format numbers with comma/decimal separators
   Format: "%,.2f"                # Java DecimalFormat pattern — e.g. "1,000,000.00"

User:
   Account:
      Initial_Balance: 0          # Starting cash balance for new players
      Maximum_Balance: 10_000_000 # Highest cash balance allowed

   Bank:
      Initial_Balance: 0          # Bank balance when the account is first created
      Create_Cost: 5_000          # One-time fee to open a bank account. Set to 0 to remove the fee.
      Maximum_Balance: 1_000_000_000

   Death:
      Enable: true                # Master switch for all death-related money/respawn effects

      Money:
         Command:
            Enable: false         # If true, runs the commands below instead of the formula. The formula is ignored.
            Executable:           # Server commands run on player death (%player% = the dead player's name)
               - "/glw eco withdraw %player% 20"
         Lose_Money: true         # If false, death gives money instead of taking it (formula still applies)
         Formula: "balance * 0.15"
                                  # Expression evaluated to determine money lost/gained.
                                  # Variables: balance, level, experience, bounty, wanted
         Threshold: 1_000         # Minimum cash balance before the penalty applies. Below this, nothing is deducted.

      Respawn:
         Enable: false            # Whether the respawn sequence (screen title, gamemode change, etc.) is active
         Screen:
            Enable: true
            Title: "&cWASTED"     # Title shown on screen at death
            Subtitle: "..."       # Subtitle — supports %gangland_waypoint_spawn%, %gangland_time%, %gangland_time_unit%
         Chat:
            Enable: true
            Message:              # Lines sent to the player in chat on respawn
               - ""
         GameMode:
            Enable: true
            Change_To: "spectator"  # Gamemode set on respawn: survival, creative, adventure, spectator
         Health:
            Enable: true
            Amount: 20            # Health restored on respawn
         Hunger:
            Enable: true
            Amount: 20            # Hunger restored on respawn
```

---

[← Gangs](./gangs.md) | [Back to Index](../README.md) | [Next: Waypoints →](./waypoints.md)
