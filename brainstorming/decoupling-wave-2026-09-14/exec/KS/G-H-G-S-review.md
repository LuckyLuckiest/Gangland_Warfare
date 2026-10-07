# Review — KS G-H / G-S / G-D (Keystone 1.10.0) — 2026-09-16 (Opus, transcribed)
Verdict: FIX (7 findings — 1 Important, 6 Minor)

## Spec table (condensed)
- WS3 G0.1 pom + root module ✅ · G0.2 Hologram/HologramService verbatim + `registerProtection(JavaPlugin)` + tests ✅ (6 tests; `registerProtection` untested → F5) · G0.3 listener → POJO ✅ · G0.4 shade + docs ✅
- WS4 G0.1 keystone-shop pom, no keystone-bean ✅ but XSeries added though plan says "not needed" (F2) · G0.2 C6 `BukkitRegistryFixture` port ❌ as written, **legitimately corrected**: it uses `server.getRegistry(any())` + `org.bukkit.block.BlockType`, absent at the 1.16.5 floor; `BukkitServerFixture` replaces it, wired only into `ShopBarterServiceTest`, testkit stays test-scope so nothing reaches the shaded jar · G0.3 27 main + 8 test files, package rename only, no `org.luckyraven.gangland.*` import, no GUI type ✅ · G0.4 `DefaultShopDisplayResolver` ✅ · G0.5 doc present but fixture paragraph wrong (F1) · G0.6 report-only evidence
- G-D one CLAUDE.md sentence at :101 ✅ · phase doc + README/docs tables ✅
- Docket CT-06 (`ShopYamlReaderTest`) and CT-07 (`ShopPurchaseServiceTest:143-157`) moved, still pinned to today's behaviour ✅; CT-04/05/22 UI-coupled, CT-23 carried, CT-08 npc-shops-owned ✅
- API floor / Java 17 ✅ (no getRegistry/Registry<>/BlockType/TextDisplay/EquipmentSlotGroup/setItemName) · behaviour spot checks (CategorySellValuator, ShopPurchaseService, ShopYamlReader) diff-clean ✅ · `StandardItemKind.MATERIAL` = Keystone's own enum, label "material", equivalent ✅

## Findings
1. **Important** `docs/keystone-shop.md:53-57` names `BukkitRegistryFixture.install()` (does not exist in keystone-testkit) and contradicts `docs/phase-h10-hologram-shop.md:62-80`. Fix: name `BukkitServerFixture.install()`, note only `ShopBarterServiceTest` needs it, registry-backed lookups out of scope at the floor.
2. Minor: unused deps — `keystone-shop/pom.xml:46-49` XSeries, `keystone-hologram/pom.xml:40-43` log4j-api.
3. Minor: five promoted javadocs still say "shop-api" (`ShopRegistry.java:17`, `ShopMessageContract.java:7`, `ShopDisplayResolver.java:11`, `PaymentHandler.java:7` — also wrongly says "EconomyHandler in gangland-impl", it is keystone-hooks — `event/ShopEditedEvent.java:12`).
4. Minor: `CLAUDE.md:97` testkit-consumer sentence not extended with the two new modules.
5. Minor: `HologramService.java:41` `registerProtection` has no test (~5 lines with `BukkitStatics`' mocked `PluginManager`).
6. Minor: `BukkitServerFixture.install()` (:34-49) sets a process-global server with no reset; add a `StaticResets` hook or state fork-lifetime intent in the javadoc.
7. Minor: phase doc lacks the closing `## Files` table the h9 doc has.

## Cannot verify
Build/test counts (1106/1044), shade audit, red runs — report-only; statically consistent.

## Notes
- C6 correction is sound; carry into WS4's plan text (done by the orchestrator in PLAN §0d-style note).
- CT-06/CT-07/CT-23 docket `note` rows need the new `keystone-shop/...` paths.
- Keystone `CLAUDE.md` `## Module dependency graph` stale for keystone-npc, keystone-module, keystone-hologram, keystone-shop.

## Orchestrator rulings (W12)
Fix 1–7 in one round (all mechanical; the lead is idle). Also update the `## Module dependency graph` in Keystone's CLAUDE.md with the four missing modules (doc-only, low risk, prevents misleading future agents). Docket note rows for CT-06/07/23 are recorded at the wave's docket step.
