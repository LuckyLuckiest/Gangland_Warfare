# Trade Signs

[← Repair System](../v0.7.3-DEV/repair.md) | [Back to Index](../README.md) | [Next: Ranks →](./ranks.md)

---

## Overview

Trade signs are in-world signs that let players buy and sell weapons and ammunition at fixed prices. Once placed and
configured, any player can interact with the sign to complete a transaction. This gives server owners a flexible,
physical economy layer for weapon shops without needing a separate plugin.

---

## Sign Types

| Type Tag        | Purpose                          |
|-----------------|----------------------------------|
| `[WEAPON_SIGN]` | Buys or sells a Gangland weapon. |
| `[AMMO_SIGN]`   | Buys or sells an ammo type.      |

---

## Setting Up a Sign

Write the following on the sign when placing it. Each line corresponds to a specific field:

```
Line 1: [WEAPON_SIGN] or [AMMO_SIGN]     ← Sign type (required)
Line 2: <item name>                       ← What is being sold
Line 3: $<price>                          ← Cost per transaction
Line 4: <quantity>                        ← Amount exchanged per transaction
```

### Examples

**Ammo sign selling 10 rounds of 9mm for $50:**

```
[AMMO_SIGN]
9mm
$50
10
```

**Weapon sign selling a Rifle for $500:**

```
[WEAPON_SIGN]
Rifle
$500
1
```

---

## Item Names

- **Ammo**: Use the exact key from `ammunition.yml` (e.g., `9mm`, `5.56`, `7.62 NATO`).
- **Weapons**: Use the exact `name` field from the weapon's config file (e.g., `Rifle`).

Names are case-insensitive. If a name is not recognized, the sign creation will fail silently — double-check spelling
against your config files.

---

## How Transactions Work

When a player right-clicks a trade sign:

1. The server checks if the player has enough money (for a buy sign) or the required item (for a sell sign).
2. If the check passes, the transaction completes: money is deducted and the item is added (or vice versa).
3. If the player's inventory is full, the transaction is blocked and the player is notified.

---

## Permissions

Sign creation requires the appropriate admin permission. Interacting with signs (buying/selling) is available to all
players by default.

---

## API

The sign system is built on the `sign-api` module. Trade signs are a specialized implementation of `SignInteraction`.

```java
// Listen for sign creation
@EventHandler
public void onSignCreate(SignInteractionCreateEvent event) {
    SignInteraction sign = event.getSign();
    String type = sign.getSignInformation().getType(); // "WEAPON_SIGN" or "AMMO_SIGN"
}

// Listen for sign use
@EventHandler
public void onSignUse(SignInteractionUseEvent event) {
    Player player = event.getPlayer();
    SignInteraction sign = event.getSign();
    event.setCancelled(true); // Can cancel the transaction
}
```

---

[← Repair System](../v0.7.3-DEV/repair.md) | [Back to Index](../README.md) | [Next: Ranks →](./ranks.md)
