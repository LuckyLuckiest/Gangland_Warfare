# Traders

[← Changelog](../v0.7.5-DEV/CHANGELOG.md) | [Back to Index](../README.md) | [Next: Bank & Banker →](./bank.md)

---

## Overview

A Trader is a stationary, damageable Citizens NPC that runs a full shop. Each Trader is bound at spawn to a **shop
key** (the pricing / inventory catalogue) and a **trait** (the personality — how generous, how friendly, whether it
accepts barter). Players right-click to open the shop UI and can **buy**, **barter**, **sell**, or **tip**.

Traders are the player-facing surface of the **shop-api** framework in `gangland-ui/shop-api`. The shop layer is
generic — any future surface (vending kiosks, black-market terminals, mission boards) can reuse the same shop
definitions.

---

## How It Works

1. An admin runs `/glw trader create <shopKey> <traitId>` while standing where the NPC should live. The NPC is
   pinned to that location, faces the nearest player, and is saved to the plugin's own repository — **not to
   Citizens' `saves.yml`**.
2. Players right-click the NPC to open the buy view. Navigation tabs inside the UI switch between **buy**,
   **barter**, and **sell** categories.
3. Every successful purchase pushes the trader's mood toward the player up by `Mood_Per_Purchase`. Every currency
   unit spent on a tip adds `Mood_Per_Tip_Currency`. There is no negative mood — accidents are forgotten, not
   punished.
4. At maximum mood the trader gives the configured `Min_Friend_Discount` on every buy.
5. The trader can be killed if its trait sets `Invulnerable: false`; otherwise it's protected from damage.

---

## Transaction Types

| Type   | What the player does                       | What the trader does                                                              |
|--------|--------------------------------------------|-----------------------------------------------------------------------------------|
| Buy    | Spends cash on a shop entry.               | Delivers the entry's item stack(s), refreshed via item-decorate.                  |
| Barter | Hands items to the trader for other items. | Values incoming items against category rates × `Barter_Price_Ratio`. Pure swap.   |
| Sell   | Hands items to the trader for cash.        | Pays the player `Sell_Price_Ratio` of the category rate, debited from house cash. |
| Tip    | Spends cash with no item in return.        | Raises the trader's mood toward the player by `Mood_Per_Tip_Currency` per unit.   |

**Barter is a pure item-for-item swap** — no money ever enters or leaves on a barter. If you want money to change
hands, use buy or sell.

---

## Traits

Each trait is a personality profile. Six ship by default; add your own by defining a new top-level id in
`trader_traits.yml`.

| Trait       | Mood gain (tip / buy) | Friend discount | Allows barter | Sell ratio | Barter ratio | Health | Killable |
|-------------|-----------------------|-----------------|---------------|------------|--------------|--------|----------|
| `easygoing` | 0.0008 / 0.03         | 0.92            | Yes           | 0.55       | 1.0          | 20     | No       |
| `generous`  | 0.002 / 0.05          | 0.80            | Yes           | 0.70       | 1.1          | 16     | No       |
| `timid`     | 0.003 / 0.04          | 0.88            | Yes           | 0.50       | 0.9          | 10     | No       |
| `shrewd`    | 0.0001 / 0.02         | 0.95            | Yes           | 0.45       | 0.7          | 20     | No       |
| `hotheaded` | 0.0002 / 0.01         | 0.98            | No            | 0.40       | 0.4          | 24     | No       |
| `stubborn`  | 0.0003 / 0.01         | 1.00            | No            | 0.50       | 0.5          | 40     | Yes      |

`stubborn` is the example killable trait — it has the highest HP and the worst player-facing terms.

---

## Commands

All admin commands target the trader in your crosshair (within 5 blocks) unless a trader key is passed.

| Command                                                | Description                                            |
|--------------------------------------------------------|--------------------------------------------------------|
| `/glw trader create <shopKey> <traitId> [displayName]` | Spawns a trader at your location.                      |
| `/glw trader edit shop <shopKey>`                      | Retargets the trader to a different shop definition.   |
| `/glw trader edit trait <traitId>`                     | Changes the trader's personality.                      |
| `/glw trader edit name`                                | Opens an anvil to rename the trader (supports spaces). |
| `/glw trader remove`                                   | Removes the trader in your crosshair.                  |
| `/glw shop remove`                                     | Removes the shop definition backing the trader.        |

---

## Configuration

### Trait Catalogue (`npc/trader_traits.yml`)

Each top-level key is a trait id. All eight fields below are required unless noted.

```yaml
easygoing:
   Display_Name: "Easygoing"              # shown in admin UIs; supports & color codes
   Mood_Per_Tip_Currency: 0.0008          # mood gain per currency unit spent on a tip
   Mood_Per_Purchase: 0.03                # mood gain on a successful buy
   Min_Friend_Discount: 0.92              # price multiplier at mood = +1 (0.92 = 8% off)
   Allows_Barter: true                    # whether the trader accepts item-for-item barters
   Sell_Price_Ratio: 0.55                 # fraction of ask the trader pays when BUYING from the player
   Barter_Price_Ratio: 1.0                # barter credit per item (optional; defaults to Sell_Price_Ratio)
   Max_Health: 20.0                       # half-hearts (20 = 10 hearts)
   # Invulnerable: false                  # (optional) omit to keep the trader damage-proof
```

**Notes:**

- Mood clamps to `[0, 1]`. `Min_Friend_Discount` is the price multiplier at mood `1`; at mood `0` the player pays
  the full ask.
- `Barter_Price_Ratio` > 1 makes the trader generous on barter intake; < 1 makes them undervalue incoming items.
  Defaults to `Sell_Price_Ratio` if omitted.
- `Invulnerable` defaults to `true`. Set it to `false` on the traits you want players to be able to fight.

### Shop Catalogue (`shop/<shop-key>.yml`)

One file per shop, scanned on boot and hot-reloaded by `/glw reload`. The shop file holds:

- **Title** and **size** of the UI.
- **Buy entries** — per-item template, ask price, and how many copies you get per purchase.
- **Sell categories** — category-by-category sell pricing.
- **Barter categories** — category-by-category barter valuation.

The shop layer is managed primarily through in-game admin GUIs (the **ShopAdminView**), so you rarely edit the
YAML by hand. Changes round-trip from GUI → YAML automatically.

---

## Mood & Pricing

The buy price a player sees is:

```
displayed_price = base_price × (1 - (1 - Min_Friend_Discount) × mood)
```

- At `mood = 0`: displayed_price = base_price.
- At `mood = 1`: displayed_price = base_price × `Min_Friend_Discount`.
- `easygoing` → up to 8 % off at full mood. `shrewd` → 5 % off. `stubborn` → 0 %.

**Tipping** contributes mood without changing inventory. A player spending 1000 currency on a tip to an
`easygoing` trader gains `0.0008 × 1000 = 0.8` mood. They'd reach max mood at 1250 currency tipped.

---

## Item Freshness

Shop entries deliver their configured template via the **item decorator** on every purchase. This means:

- If you edit an entry's display name or lore in the admin GUI, subsequent purchases get the new decoration
  without touching existing player inventories.
- Freshness is applied **without mutating NBT**, so stateful items (weapons with saved ammo, unique items) keep
  their state across purchases.

See the [item refresher pattern](../developer/item-refresher-pattern.md) for the shared framework.

---

## Shop Entry Semantics — Copies vs. Items

A buy entry's `template.amount` is one **copy**. The `BUY AMOUNT` picker counts copies, not items.

- A buy entry for `"material:ARROW"` with `template.amount: 5` delivers 5 arrows per copy. Buying 3 copies =
  15 arrows.
- A buy entry for `"weapon:rifle"` always has amount 1 per copy (weapons don't stack).

This matters if you're copying a prior-version shop into 0.7.5 — the picker label changed from "items" to "copies".

---

## API

The trader repository is registered as a bean; inject it wherever you need it.

```java
TraderRegistry registry = gangland.getContext().get(TraderRegistry.class);

// Get the trader the player is looking at
TraderNpc trader = registry.getNearestTrader(player, 5.0);

// Mood check
double mood = registry.getMoodTracker().getMood(player, trader);
```

Shop transactions are serviced through `ShopPurchaseService`, `ShopBarterService`, and `ShopSellService`, all of
which return typed `*Result` objects rather than booleans — pattern-match on the result enum for
`SUCCESS` / `INSUFFICIENT_FUNDS` / `INVENTORY_FULL` / etc.

---

## Related

- [Bank & Banker](./bank.md) — the other NPC-driven economy surface.
- [Cops N Crooks](./cops-n-crooks.md) — the module Traders ship under.
- [Jail & Detainment](./jail-detainment.md) — bail uses the same account balance a trader's buy / sell touches.

---

[← Changelog](../v0.7.5-DEV/CHANGELOG.md) | [Back to Index](../README.md) | [Next: Bank & Banker →](./bank.md)
