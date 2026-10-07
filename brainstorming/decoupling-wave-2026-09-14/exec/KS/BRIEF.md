# Keystone 1.10.0 lead brief — `keystone-hologram` (WS3 G0) + headless `keystone-shop` (WS4 G0)

Rules: `../LEAD-RULES.md` (read first). Keystone's tracked `CLAUDE.md` (in the worktree) adds its own hard rules:
layering (generic infrastructure only), API floor Spigot 1.16.5, Java 17, `NmsVersion` branching, phase docs.

## Worktree
`E:\Programming\java\wt\keystone-1.10.0` — branch `phase-h10-hologram-shop` off `15d5083` (= 1.9.2, the
`phase-h9-host-api` head). Clean checkout. Bump `<revision>` 1.9.2 → **1.10.0** (new modules = minor).

## Sources to promote (read-only, from the Gangland 0.9.2 worktree)
- Hologram: `E:\Programming\java\wt\gangland-0.9.2\gangland-ui\hologram-api\` — `Hologram`, `HologramService`,
  `HologramProtectionListener` (3 files, ~346 LoC, ArmorStand-based).
- Shop: `E:\Programming\java\wt\gangland-0.9.2\gangland-ui\shop-api\` (+ `DefaultShopDisplayResolver` in
  `gangland-api`). Headless subset = every file with **no** `org.luckyraven.gangland.inventory` import (the plan
  counts 28 main files + 8 tests: model, registry, transactions, valuation, yaml io, event, display resolver).
  The 9 UI-coupled files (6 admin views + 3 admin listeners) stay in Gangland — do not port them.

## Plans (binding)
- `brainstorming/decoupling-wave-2026-09-14/plans/WS3-lootchest-hologram.md` — the G0 / hologram sections + §0b.
- `brainstorming/decoupling-wave-2026-09-14/plans/WS4-shop.md` — the G0 / `keystone-shop` sections + §0b.
- `brainstorming/decoupling-wave-2026-09-14/PLAN.md` §6, §7 (summaries), §2 rulings, §10 decisions
  (A3 = headless `keystone-shop`; A4 = new `keystone-hologram` module; WS4-D2 = ship `DefaultShopDisplayResolver`
  as the default).

## Gates — STOP and report after each
| Gate | Content | Report |
|---|---|---|
| G-H | new module `keystone-hologram` (needs `keystone-bean`): per-consumer instance, no static registry, `registerProtection(JavaPlugin)` instead of shipping a listener, `ponytail:` note for a TextDisplay backend above the 1.16 floor; root pom `<module>`, `keystone-plugin` shade/bundle list if that is how modules ship (check how `keystone-npc`/`keystone-item` were added in 1.9.0 — `git log --stat` on the worktree, `docs/phase-h8*.md`); tests ported/adapted; `mvn clean install` green | `exec/KS/G-H-report.md` |
| G-S | new module `keystone-shop`: the headless files, package `org.luckyraven.keystone.shop.*`, behaviour unchanged, the 8 tests green (CT-06/CT-07 pinning tests move and stay red-pinned as the docket says — keep them asserting today's behaviour), `PaymentHandler` on Keystone `EconomyHandler`; root pom + plugin list; `mvn clean install` green | `exec/KS/G-S-report.md` |
| G-D (with G-S) | Keystone `CLAUDE.md` layering bullet: **exactly one added sentence** naming both modules as permitted generic infrastructure; `docs/phase-h10-hologram-shop.md` (pattern: `docs/phase-h9-host-api.md`); README module table if one exists | in `G-S-report.md` |

Nothing in Gangland changes in your stream (its consumer gates are 0.10.0 work). If Keystone has a
`graphify-out/` in `E:\Programming\java\Keystone`, query it there first for Keystone questions; for the Gangland
sources use the Gangland graph (cwd = main Gangland checkout).
