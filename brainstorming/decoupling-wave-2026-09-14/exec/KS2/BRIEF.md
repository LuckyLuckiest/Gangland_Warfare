# Keystone 1.11.0 lead brief — Phase E6 `keystone-inventory` (K2 of the re-targeted WS2)

Rules: `../LEAD-RULES.md` (read first). Keystone's tracked `CLAUDE.md` binds (generic-only layering, Spigot 1.16.5
API floor, Java 17, shared-classloader rule: no static singletons two consumers could overwrite, Keystone.jar
registers no listener itself).

## Worktree
`E:\Programming\java\wt\keystone-1.11.0` — branch `phase-e6-inventory` (docs-inventory-roadmap → cd5bb9f 1.10.0).
Bump `<revision>` 1.10.0 → **1.11.0**. `mvn clean install` must be green at baseline before you start.

## Binding plan
`docs/extraction-roadmap.md` in the worktree, section **"## Phase E6 — keystone-inventory"** (E6.1 core table,
E6.2 flow + pages, "Deliberately not ported", the decisions list, and the "Review pass (2026-09-17)" note) — read
it whole. Rulings that bind it: `exec/WS2/E6-plan-review.md` (findings + "Orchestrator rulings (W23)").
Gangland's consumer-side plan (context only): `plans/WS2-inventory-keystone.md` §10 "Keystone E6 requirements".

## Sources (read-only)
- Oriel (the port source, committed 00b8b35 on branch 0.8.0): `E:\Programming\java\wt\oriel-0.8.0` — `menu-core`
  (`core/{click,component,registry}`), `menu-inventories/chest` (`chest/internal`, `chest/listener`,
  `chest/component`, `chest/flow`, `chest/ChestMenu.java`, `chest/ChestMenuBuilder.java`), `PageConfig` in
  `chest/internal`. Note the committed G1/G2 work: `MenuFlow.rerender()`/`switchTo(same)`/row-count fallback,
  `ChestMenu.adoptComponentsFrom`/`returnHeldItemsForSlot`, `CloseReason.NAVIGATION` — port these shapes.
- Gangland's inventory-api (what E6 replaces; its unique pieces fold in): `E:\Programming\java\wt\gangland-0.9.2\gangland-ui\inventory-api`
  — `MultiPanelInventory` (in-place switch, suspend/resume, suppressClose), `part/PageConfig`, the interactive-slot
  ("Draggable") flag, `InventoryUtil` fill/border/line shapes.
- Precedents in this worktree: `keystone-hologram` (module pom, `registerProtection` pattern), `keystone-shop`,
  `docs/phase-h10-hologram-shop.md`, `docs/keystone-hologram.md`.

## Gates — STOP and report after each
| Gate | Content | Report |
|---|---|---|
| E6.1 core | module `keystone-inventory` (deps: keystone-common, keystone-bean, keystone-item; XSeries already shaded; no AnvilGUI/NBT-API/new library): `Menu`/`MenuBuilder`/`CloseReason`; `Component`/`RenderContext`/`SlotView` (+`interactive(boolean)`)/`ItemHoldingComponent`; `ClickKind`/`ClickHandler`/`ClickContext`/`SlotHandlers`; `MenuHolder` (carries its `InventoryService`, public `service()`) + `MenuTarget` (defaults, incl. the single `handleDepositClick(InventoryClickEvent)` returning false); one `MenuListener` (click/drag/close/quit; identity = `getHolder() instanceof MenuHolder && holder.service() == this.service`); `OpenMenuTracker`/`DefaultOpenMenuTracker`/`NavigationHistory` instance-scoped (no static `MenuTracking`); `MenuRegistry`/`InMemoryMenuRegistry`/`MenuOpener`; `InventoryService` (`BeanLifecycle`, owns tracker + registry, `registerListeners(JavaPlugin)`, `closeAll(CloseReason)` on `onPreClear`/`onShutdown`); `ChestMenu` (non-final, ~200-line core, protected accessors, overridable `renderAll`/`rerenderSlot`/`applyFillIfEmpty`/`ensureTitleFor`/`resolveTitle`, settable title source, `Menu.rerender()`), extensible `ChestMenuBuilder` (rows/title/permission/fill/border/line/slot/interactive/clickDelay/onOpen/onClose/vetoClose); `ItemComponent` (new: ItemStack + handlers, `ItemBuilder` overloads), `FillComponent`/`BorderComponent`/`LineComponent`; package-private `ChestClickContext`/`ChestRenderContext`/`ChestSlotView`. Tests: ported Oriel tests adapted + new ones — the **two-service case** (Oriel-style and Gangland-style services both registered, one click reaches exactly one), click policy (interactive slot passes, non-interactive cancelled, drag cancelled if any slot non-interactive, deposit routed through `handleDepositClick`), `closeAll` returns held items on RELOAD/PLUGIN_DISABLE, rerender in place. Red-first for new behaviour. | `exec/KS2/E6.1-report.md` |
| E6.2 flow + pages + docs | `MenuFlow`/`MenuFlowBuilder`/`Panel`/`FlowState` (in-place switch when size+title match, `suspend()`/`resume()`, `suppressClose`, `back()`/`hasBack()`, `rerender()`, same-id switch = rerender, row-count fallback); `PageConfig` (Oriel's shape, Gangland's extra arithmetic folded in — diff both first); `PagedRegion`; module joins the shade (`keystone-plugin`), CLAUDE.md dependency graph + layering sentence ("basic inventory mechanism in Keystone, advanced menu features in Oriel") + testkit consumer list; `docs/phase-e6-inventory.md` (h10 shape, `## Files` table), `docs/keystone-inventory.md` (module guide), README/docs tables. Whole reactor `mvn clean install` green; jar audit. | `exec/KS2/E6.2-report.md` |

No Gangland or Oriel file changes in your stream. You may use up to 2 Sonnet subagents (e.g. one porting
`core/` + `chest/internal`, one porting `chest/` components/builder) with exact file lists; verify their work yourself.
