# Trader & Shop — Test Checklist

[Back to Test Index](../README.md)

---

## Overview

Stationary, damageable Citizens NPC bound to a shop key, with data-driven traits (mood). Per
`project_shop_api_extraction` the framework lives in `gangland-ui/shop-api`; per `project_trader_simplification_phase1`
the trait record is simplified (no bargain, no anger, no tip-view, no propose-price); trader mood is positive-only
(`project_trader_no_anger_loop`). Purchase flows: **buy**, **barter** (pure item-for-item swap —
`project_barter_pure_swap`), **tip**, **sell** (trade-in). Economy wiring goes through `TraderEconomyContract` so
feature modules never import `Settings` / `Messages` directly (`feedback_settings_contract`).

**Modules involved:** `gangland-ui/shop-api`, `gangland-features/cops-n-crooks` (NPC + mood),
`gangland-impl` (listeners, persistence, commands).

---

## Pre-Conditions

- [ ] `npc/trader_traits.yml` has at least one trait.
- [ ] At least one shop YAML exists (create via `/glw shop create <key>`).
- [ ] Citizens plugin loaded.

---

## Shop Admin

- [ ] `/glw shop create weapons_basic` → shop YAML created with that key.
- [ ] `/glw shop edit weapons_basic` → admin GUI opens.
    - [ ] Drag an item in → entry added.
    - [ ] Left-click an entry → price editor.
    - [ ] Right-click an entry → entry removed.
- [ ] `/glw shop list` → shows `weapons_basic` with entry counts.
- [ ] `/glw shop title weapons_basic` → anvil GUI renames the shop; colour codes render.

---

## Trader Admin

- [ ] `/glw trader create weapons_basic friendly "Gunsmith"` → stationary Citizens NPC spawns at your location,
  bound to the shop, named "Gunsmith".
- [ ] Confirm the NPC has `NPC.Metadata.SHOULD_SAVE = false` (Citizens must not persist it —
  `feedback_citizens_should_save_flag`).
- [ ] Crosshair the trader. `/glw trader edit shop weapons_elite` → now bound to a different shop.
- [ ] `/glw trader edit trait grumpy` → trait swapped.
- [ ] `/glw trader edit name` → anvil GUI renames.
- [ ] `/glw trader remove` → trader despawned (5-block crosshair range).

---

## Purchase Flow — Buy

- [ ] Right-click the trader as a non-op player → shop inventory opens.
- [ ] Click a buy entry → buy confirmation flow.
- [ ] **Buy Amount picker** counts **copies**, not items — per `project_shop_copies_semantics`, each copy delivers
  the configured `template.amount` stack. (E.g. a 16-arrow template with 3 copies = 48 arrows.)
- [ ] Confirm purchase → wallet debited via `TraderEconomyContract`; copies delivered; trader mood ticks up.
- [ ] Insufficient funds → purchase denied; wallet unchanged; mood unchanged.
- [ ] Full inventory → purchase denied; wallet unchanged.

---

## Purchase Flow — Barter

- [ ] Barter is a **pure item-for-item swap** — no money moves (`project_barter_pure_swap`).
- [ ] Offer an item that shares the trader's valuation category → accepted; items swap.
- [ ] Offer an item outside the trader's category → rejected.

---

## Purchase Flow — Tip

- [ ] Tip the trader some currency → wallet debited; mood rises by the configured amount.
- [ ] No currency to tip → rejected.

---

## Purchase Flow — Sell / Trade-In

- [ ] Sell an item → valuation applied; wallet credited; item removed.
- [ ] Sell invalid item → rejected with clear message.

---

## Mood (Positive-Only)

- [ ] Starting mood is the trait's default.
- [ ] Buy / tip increases mood.
- [ ] There is **no anger or negative-mood loop** — `project_trader_no_anger_loop`. Reloading, attacking, or refusing
  purchases does not push mood below the base.
- [ ] Mood per-player persists across restart.

---

## Item Refresher on Delivery

- [ ] Shop entries that carry stateful items (fuel-bearing cars, charged uniques) refresh via `ItemRefresher` on
  **every delivery**, not once at placement — `feedback_item_refresher_pattern`.

---

## Messages

- [ ] Every user-facing string (buy confirmation, insufficient funds, barter rejection, tip thanks) routes through
  `ShopMessageContract` / `TraderMessageContract` → `Messages` enum. Feature modules must not import `Messages`
  directly (`feedback_settings_contract`).

---

## Reload Safety

- [ ] Create trader, buy an item, run `/glw reload` → trader still in place; mood persists; shop inventory still
  opens.
- [ ] Edit `npc/trader_traits.yml`, `/glw reload files` → new trait values apply on next interaction.

---

## Persistence

- [ ] Traders + bindings persist across restart (repository-owned, not Citizens-owned).
- [ ] Shop definitions (YAML) are flat files — unchanged by restart.
- [ ] Per-player mood persists.
- [ ] Verified on SQLite and MySQL.

---

## Regression Risks

- `shop-api` — views, registry, admin, persistence (extracted per `project_shop_api_extraction`).
- `TraderEconomyContract` — currency without importing `gangland-impl`.
- Citizens integration — stationary + damageable + not-persistent.
- NPC base shared with cops/civilians.

---

[Back to Test Index](../README.md)
