# Bank & Banker

[← Traders](./traders.md) | [Back to Index](../README.md) | [Next: Jail & Detainment →](./jail-detainment.md)

---

## Overview

Every player has two currency containers:

- An **account balance** — the cash on hand. Lost on death (subject to tier-based discount), drops as pickup
  money, and is what the trader buy / sell flows touch.
- A **bank balance** — protected currency at a bank. Gated by a tier ladder that caps max balance, limits daily
  deposits, and earns interest.

The **Banker NPC** is the player-facing UI for the bank. Players right-click a Banker to open the bank menu;
admins place Bankers anywhere in the world.

---

## How It Works

1. A player's first interaction with any Banker NPC creates their bank row (charged from cash — see
   `User.Bank.Create_Cost`).
2. The Banker UI lets the player **deposit**, **withdraw**, **upgrade** to the next tier, or **rename** their
   account.
3. Deposits and withdrawals check a rolling-window cap — not a midnight-reset cap — so splitting a transaction
   across midnight never counts against two separate windows.
4. **Interest** accrues continuously as a fraction of the bank balance per 24h, clamped at the player's tier
   `Max_Balance`.
5. **Weekly** and **monthly loans** are free currency deposits issued on a fixed cadence per tier.
6. On death, the player loses a fraction of their **account balance** (not bank balance) — the loss is reduced
   by their tier's `Death_Loss_Discount`.

---

## Tier Ladder

Four tiers ship by default. Tiers are **global** — every Banker NPC offers the same ladder. Players upgrade
by paying `Upgrade_Cost` out of their bank balance.

| Tier    | Order | Max Balance | Upgrade Cost | Daily Deposit | Interest / day | Death-Loss Discount | Weekly Loan | Monthly Loan |
|---------|-------|-------------|--------------|---------------|----------------|---------------------|-------------|--------------|
| Basic   | 0     | 100,000     | 0            | 10,000        | 0 %            | 10 %                | 500         | 2,000        |
| Premium | 1     | 1,000,000   | 50,000       | 100,000       | 0.25 %         | 25 %                | 2,000       | 10,000       |
| Elite   | 2     | 10,000,000  | 500,000      | 1,000,000     | 0.75 %         | 50 %                | 10,000      | 50,000       |
| Vault   | 3     | 100,000,000 | 5,000,000    | 10,000,000    | 1.5 %          | 75 %                | 50,000      | 250,000      |

Every value is editable in `npc/bank_tiers.yml`. Add, remove, or re-price tiers freely; the loader re-reads
the file on `/glw reload`.

---

## Banker NPC

- Stationary — does not wander, doesn't accept combat targets. Faces the nearest player within interaction range.
- Damageable flag is controlled by the Banker settings block (default: invulnerable).
- Saved by the plugin's own `BankerRepository`, not Citizens' `saves.yml` (`SHOULD_SAVE = false` at spawn).
- Right-click on the NPC opens the bank UI for that player.

### Placement

Admins use the banker commands (see below) to create and remove Bankers. Identical-looking NPCs can be scattered
across your map — the tier ladder and economy are global, so there's no per-NPC configuration.

---

## Commands

### Player

| Command                                | Description                                                |
|----------------------------------------|------------------------------------------------------------|
| `/glw bank`                            | Shows current bank stats or help page.                     |
| `/glw bank help <page>`                | Shows the specified page of the bank help menu.            |
| `/glw bank create <name>`              | Creates a bank account with the specified name.            |
| `/glw bank balance`                    | Shows the current bank balance.                            |
| `/glw bank deposit <amount> [player]`  | Deposits cash into the bank. Admin: credit another player. |
| `/glw bank withdraw <amount> [player]` | Withdraws from the bank. Admin: debit another player.      |
| `/glw balance`                         | Shows the player's cash (account) balance.                 |
| `/glw balance <player>`                | Shows another player's cash balance.                       |

### Admin

| Command                            | Description                                                                  |
|------------------------------------|------------------------------------------------------------------------------|
| `/glw bank resetcap <player\|all>` | Resets the rolling-window deposit / withdraw cap for one player or everyone. |
| `/glw balance set <player> <n>`    | Sets a player's cash balance.                                                |
| `/glw balance add <player> <n>`    | Adds to a player's cash balance.                                             |
| `/glw balance reset [player]`      | Resets a player's cash balance (or the caller's, if omitted).                |

The `[player]` argument on `deposit` / `withdraw` is admin-only — the permission check gates who can target another
player.

---

## Configuration

### Tier Catalogue (`npc/bank_tiers.yml`)

Players start at the lowest `Order`. Interacting with any Banker offers a one-click upgrade to the next tier;
the upgrade cost is debited from the bank balance and every tier-scoped knob below takes effect.

```yaml
Basic:
   Display_Name: "&7Basic Account"
   Max_Balance: 100_000
   Upgrade_Cost: 0                # first tier should be 0
   Order: 0                       # ascending; lower = earlier in the ladder
   Daily_Deposit_Limit: 10_000    # 0 = uncapped
   Interest_Rate: 0.0             # fraction per 24h (0.015 = 1.5%/day)
   Death_Loss_Discount: 0.10      # 0–1; fraction subtracted from death tax
   Weekly_Loan_Amount: 500        # 0 = disabled
   Monthly_Loan_Amount: 2_000
```

### Bank Settings (`settings.yml` → `User.Bank`)

```yaml
User:
   Account:
      Initial_Balance: 0               # cash granted to new players
      Maximum_Balance: 10_000_000      # hard cap on cash on hand
   Bank:
      Initial_Balance: 0               # bank balance granted on account creation
      Create_Cost: 5_000               # charged from cash when a player creates their bank row
      Rename_Fee: 1_000                # charged from cash on every rename at a Banker. 0 disables.
      Reset_Period: 86_400             # rolling-window length in seconds (86 400 = 24h)
```

**Rolling-window vs. midnight:** the rolling window measures from the player's **first** transaction in the
window — not from midnight. A deposit at 23:58 starts a fresh window that expires at 23:58 the next day. This
stops players from double-dipping the daily cap by crossing midnight.

---

## Precision — BigDecimal Money Handling

Bank arithmetic uses `BigDecimal` internally rather than `double`. This matters once balances get into the
millions — floating-point drift on deposits, withdrawals, and interest accrual compounds over time.

- **Where BigDecimal is used:** bank-side math (deposit, withdraw, interest accrual, tier upgrade cost,
  rolling-window totals).
- **Where `double` is still fine:** pickup money drops, cash-in-hand arithmetic (small magnitudes, single-op
  transactions), shop transaction totals handled by shop-api.

When you write integrations that touch the bank, prefer the BigDecimal-returning overloads.

---

## Death-Loss Discount

On death the player loses a configurable fraction of their **account balance** (cash). The tier's
`Death_Loss_Discount` subtracts from that fraction:

```
actual_loss = base_loss_fraction × (1 - Death_Loss_Discount)
```

- `Basic` (0.10 discount): keeps 10 % more of the configured loss.
- `Vault` (0.75 discount): keeps 75 % more.

Bank balance is never lost on death — only cash-on-hand is touched.

---

## Weekly & Monthly Loans

Each tier's `Weekly_Loan_Amount` and `Monthly_Loan_Amount` are deposited directly into the player's **bank
balance** on a fixed cadence:

- Weekly loan fires every 7 real-time days since the player's bank-row creation.
- Monthly loan fires every 30 real-time days.
- `0` disables the loan for that tier.

Loans bypass the daily deposit cap (the server owner is gifting the currency, not the player depositing it) and
clamp to the tier's `Max_Balance`.

---

## Interest

Interest is applied continuously, not on a fixed schedule. Implementation-wise the last interest-accrual
timestamp is stored on the bank row; on the next read, the delta since that timestamp is multiplied by the
per-second equivalent of `Interest_Rate`.

- `Interest_Rate: 0.015` = 1.5 % per 24h.
- Interest accrues up to `Max_Balance` and then stops — it does not push the balance over the tier cap.
- Tier upgrades take effect immediately; the new rate applies from the upgrade timestamp forward.

---

## Related

- [Traders](./traders.md) — the other NPC-driven economy surface.
- [Jail & Detainment](./jail-detainment.md) — bail and bribes debit the same account balance the bank
  interacts with.
- [Cops N Crooks](./cops-n-crooks.md) — module that ships the Banker NPC.

---

[← Traders](./traders.md) | [Back to Index](../README.md) | [Next: Jail & Detainment →](./jail-detainment.md)
