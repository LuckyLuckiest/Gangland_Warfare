# Oriel 0.8.0 lead brief — the WS2 asks (prerequisite of Gangland 0.10.0)

Rules: `../LEAD-RULES.md` (read first). Oriel is a separate product with its own docs
(`MIGRATION-FROM-GLW.md`, `USE-CASE-ENABLERS.md`, its README/CLAUDE.md if present) — read them before editing.

## Worktree
`E:\Programming\java\wt\oriel-0.8.0` — branch `0.8.0` off Oriel's current head (`fea7e01`, 0.7.0, pinned to
Keystone 1.7.0). The orchestrator creates it before dispatching you; verify with `git -C <worktree> status`.

## Plan (binding)
- `brainstorming/decoupling-wave-2026-09-14/plans/WS2-inventory-oriel.md` — the G0 section, the "Oriel asks"
  table and §0b (re-review N1/N2/N3).
- `../../PLAN.md` §5 (the asks table with sizes and "blocking?" flags), §2 rulings R4 (target Keystone **1.9.2**
  now, 1.10.0 in a later small gate), R5 (`rerender()` + published `OpenMenuTracker` are first-class asks), R6
  (Gangland's api never re-exports Oriel).
- Cross-docket Oriel findings: `brainstorming/cross-docket-2026-09-10/oriel/findings/` (9 files) — read at G0;
  fix only what an ask touches, list the rest in the report.

## Gates — STOP and report after each
| Gate | Content | Report (under `exec/ORIEL/`) |
|---|---|---|
| G0 | Keystone 1.7.0 → **1.9.2** bump (compile + test green; note every API break you had to adapt), version 0.7.0 → 0.8.0, `MenuConfigService.forConsumer(plugin, fileManager, placeholders)` factory (re-review N2: G1 of Gangland is not independently green without it — the javadoc must state which plugin owns `MenuCommandRegistrar`'s command registration and that `CooldownService` is Keystone's), the two spikes the plan names | `G0-report.md` |
| G1 | `MenuFlow.rerender()` in place; `OpenMenuTracker` published on the `ServicesManager` (or via `MenuOpener`); `back()` on an empty stack / `hasBack()`; doc fix `MIGRATION-FROM-GLW.md:389-394` | `G1-report.md` |
| G2 | Placeholder resolution for a consumer's in-process placeholder service (`%money_symbol%`) and row-scoped `%member_*%`/`%ally_*%` tokens; anvil-driven paginated search; crash-safe `DepositSlotComponent` persistence (Oriel A3); price-entry grid component (mirrored ± rows — house rule: biggest step nearest the item, smallest at the edge) | `G2-report.md` |

Every gate ends with `mvn clean install` green in the worktree (Oriel's reactor) and its docs updated
(CHANGELOG/README/module docs as Oriel keeps them). Nothing in Gangland changes in your stream.
