# Economy — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/economy.md)

---

## Overview

Personal wallet, personal bank, gang bank, death penalties, admin deposit/withdraw/set/reset. Economy domain code
lives under `gangland-features/gangland-economy/` per `feedback_economy_in_features` — not in `gangland-impl/data`.
`gangland-impl` owns the wiring via `GanglandMoneyDepositService`.

**Modules involved:** `gangland-features/gangland-economy`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Two online players: `A` and `B`.
- [ ] `A` has a non-zero starting balance.

---

## Smoke Test

- [ ] `/glw balance` → shows `A`'s wallet.
- [ ] `/glw balance B` → shows `B`'s wallet (online).
- [ ] Op: `/glw economy deposit A 500` → `A`'s balance increases by 500.
- [ ] Op: `/glw economy withdraw A 200` → `A`'s balance decreases by 200.
- [ ] Op: `/glw economy set A 1000` → `A`'s balance equals 1000.
- [ ] Op: `/glw economy reset A` → `A`'s balance returns to default.
- [ ] `A`: `/glw bank create MyBank` → bank created.
- [ ] `A`: `/glw bank deposit 100` → wallet debited, bank credited.
- [ ] `A`: `/glw bank withdraw 50` → reverse.
- [ ] `A`: `/glw bank balance` → shows 50.
- [ ] `A`: `/glw bank delete` → bank removed (confirm prompt).

---

## Edge Cases

- [ ] `/glw bank deposit` more than wallet holds → denied with clear message.
- [ ] `/glw bank withdraw` more than bank holds → denied.
- [ ] Negative amount → rejected at parse time.
- [ ] `/glw balance OfflinePlayer` → resolves and shows persisted balance.
- [ ] `/glw economy deposit OfflinePlayer 100` → balance updates on disk; reflected when they next log in.
- [ ] Very large amount (near `Long.MAX_VALUE`) → no overflow; clamped or rejected.

---

## Death Penalty

- [ ] Configure a death penalty in `settings.yml`.
- [ ] Kill `A` in a valid PvP scenario → `A`'s balance decreases by the configured penalty.
- [ ] `A` with 0 balance on death → no underflow; balance stays at 0.

---

## Reload Safety

- [ ] Mid-transaction reload: deposit is in-flight, run `/glw reload data` → transaction completes or is rolled back
  cleanly; no half-written state.
- [ ] After `/glw reload`, all four admin commands still work; balances match pre-reload.

---

## Persistence

- [ ] Wallet balance, personal bank, gang bank all persist across restart.
- [ ] Verified on both SQLite and MySQL.

---

## Regression Risks

- `GanglandMoneyDepositService` — wiring between feature module and impl.
- `EconomyContract` / settings contract — feature modules must not import Settings/Messages directly
  (`feedback_settings_contract`).
- `TraderEconomyContract` — trader purchases piggyback on the same service (see
  [trader-shop](./trader-shop.md)).

---

[Back to Test Index](../README.md)
