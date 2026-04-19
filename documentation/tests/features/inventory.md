# Inventory System — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/inventory.md) | [UI Framework Doc](../../developer/ui-framework.md)

---

## Overview

Custom GUI / menu framework used by phones, gang info screens, user stats, shop admin, traders, and more. Menus are
defined in `inventory/*.yml`. Helpers like `InventoryUtil.fillInventory`, `createBoarder`, `horizontalLine`,
`aroundSlot` are the canonical way to paint slots (`feedback_inventory_util_fillers`, `feedback_aroundslot_noclear`).

**Modules involved:** `gangland-ui/inventory-api`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Player online with a populated user record (non-zero balance, a gang, etc.).

---

## Smoke Test — Menu Open Paths

Run each menu once:

- [ ] `inventory/phone.yml` — right-click phone item → personal phone menu opens.
- [ ] `inventory/phone_gang.yml` — phone → gang tab → gang phone opens.
- [ ] `inventory/user_stat.yml` — open user stat view.
- [ ] `inventory/gang_info.yml` — open gang info view.
- [ ] `inventory/gang_stat.yml` — open gang stat view.
- [ ] `inventory/alliance_stat.yml` — open alliance stat view.

Each must:

- [ ] Open with the configured title (colour codes rendered).
- [ ] Show all placeholder slots populated with current data.
- [ ] Navigation slots (back / next / close) dispatch correctly.

---

## Pagination

- [ ] A paginated list (e.g. gang members with more than one page) → `next` / `previous` work; last page shows
  partial row correctly.
- [ ] Paginated list with exactly `perPage` entries → single page; no empty `next` button.

---

## Multiplier / Preview Rows (per `feedback_mirror_adjust_row`)

- [ ] Any menu with paired +/- multiplier rows flanking a preview slot → biggest step is nearest the preview item,
  smallest at the edge. Mirror is symmetrical.

---

## Around-Slot Colouring (per `feedback_aroundslot_noclear`)

- [ ] A menu that calls `InventoryUtil.aroundSlot` during a re-render → the ring is cleared first, then re-coloured.
  (If the old colour persists under a non-null slot, `aroundSlot` skipped it — bug.)

---

## Filler API

- [ ] No menu hand-rolls a glass-pane loop. All fills use `InventoryUtil.fillInventory` / `createBoarder` /
  `horizontalLine` with a `Fill` record.

---

## Edge Cases

- [ ] Open menu, reload plugin → menu closes cleanly; no ghost click handlers.
- [ ] Click a slot the moment the menu closes → no exception; click is ignored.
- [ ] Corrupt `inventory/gang_info.yml` → error logged; feature disabled gracefully.
- [ ] Colour codes use `&`, never `§`.

---

## Reload Safety

- [ ] Edit `inventory/phone.yml` (change title or slot layout).
- [ ] `/glw reload files` → next open picks up the new layout.

---

## Persistence

- [ ] No persistent state — menus are ephemeral.

---

## Regression Risks

- `InventoryUtil` — filler helpers.
- Menu event dispatcher — must use typed handlers, not raw slot indexes.

---

[Back to Test Index](../README.md)
