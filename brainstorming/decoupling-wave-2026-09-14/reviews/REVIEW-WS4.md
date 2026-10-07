# REVIEW WS4 — shop-api becomes a reusable Keystone system

Verdict: **PASS WITH FIXES**

Reviewed against a fresh Gangland graph (`graphify-out/graph.json` Sep 14 18:43 > last commit 18:10 — fresh),
Keystone `phase-h9-host-api`, Oriel 0.7.0, and the orchestrator's rulings R1 (api 2.0 bumped in 0.10.0's first
commit, so `gangland-api` removals are legal everywhere) and R3 (`inventory-api` deletion is a shared cutover that
runs **after** WS4's consumer gate).

**The plan's §0 census corrections are all true and are the best work in this wave.** Re-verified: 37 main files,
`grep -rl "org.luckyraven.gangland.inventory"` → **9** (exactly the 6 `shop/view/*` + 3 `shop/listener/*` named)
→ **28 headless / 9 UI-coupled** confirmed; `gangland-core`/`gangland-item` imports → **0** (dead pom entries,
`shop-api/pom.xml:30-39,50-53`); `ItemParser` → **0 hits** anywhere in shop-api, and `ItemValuation.java` is a
zero-dependency record, so the census's "`ItemParser` in `ShopYamlReader`" (census §1 L17, §3 L38) is false and
Keystone's `keystone-item/.../ItemParser.java` is irrelevant here; `PaymentHandler.java:1-17` imports only
`BigDecimal` and `TraderBuyListener.adapt()` (`.../listener/trader/TraderBuyListener.java:103-120`) is the 1:1
`EconomyHandler` wrapper; `/glw shop` **is core** (`ShopCommand.java:8,16` + five siblings), and
`graphify affected "ShopAdminFlow"` returns only npc-shops' `ShopViewOpenerImpl`/`TraderModuleConfig` plus core
`ShopCommand`/`ShopEditCommand`/`ShopConfig`; `GanglandContext.java:84,250` quoted correctly. The blockers below
are localized to §2/§4/§5/§9 — the architecture (headless `keystone-shop`, views in the consumer) survives review.

## Blockers (must fix before executors start)

- **B1. The rebuilt admin views cannot live in `gangland-impl` — npc-shops names `ShopAdminFlow` directly.**
  `ShopViewOpenerImpl` holds it as a field (`gangland-features/gangland-npc-shops/.../trader/ShopViewOpenerImpl.java:23`,
  called at `:42` and `:55`) and `TraderModuleConfig.java:31,169` constructs it. A runtime module compiles against
  `gangland-api` at `provided` only (`gangland-npc-shops/pom.xml:103-104`; repo CLAUDE.md "Module API contract"),
  never `gangland-impl`. §2/§9-D4's "land them in `gangland-impl/.../shop/admin/`" breaks the npc-shops build.
  Required change: either (a) the rebuilt flow lands in `gangland-api` (where npc-shops reaches it today, via the
  `shop-api` re-export at `gangland-api/pom.xml:50`), or (b) — recommended, smaller — core keeps the views in
  `gangland-impl` and publishes a one-method `ShopAdminOpener { void openAdmin(Player, ShopDefinition); }` in
  `gangland-api`, implemented in impl, injected into `ShopViewOpenerImpl` in place of the flow. One interface, one
  impl, but it *is* the module boundary, so it clears the ponytail bar. Pick one in §9 and say which.

- **B2. `keystone-shop` will not reach npc-shops transitively — step 10 deletes a dependency it must keep.**
  Every `keystone-*` artifact is `provided` in the root `pom.xml` (`pom.xml:177` for `keystone-item`, same block
  for the rest), and `provided` scope is **not transitive** — npc-shops' own pom already says so in a comment
  (`gangland-npc-shops/pom.xml:28`: "Declared directly: provided scope is not transitive, so it cannot arrive via
  gangland-core") and therefore declares keystone-bean/item/persistence/common itself (`:21-31`). Step 10's "drop
  the direct `shop-api` dependency (`pom.xml:60-63` — redundant, comes through `gangland-api`'s re-export)" is true
  only while shop-api is a *compile*-scope Gangland artifact. Replace it with: **swap** `shop-api` → `keystone-shop`
  `provided` in npc-shops' pom, and add the same to `gangland-impl`'s pom. Same fix for `gangland-api/pom.xml:50`:
  it moves out of the "re-exported at compile scope" block (`pom.xml:25-26`) into the Keystone `provided` block
  (`:51-81`), which also keeps `gangland-build`'s `<include>org.luckyraven:*</include>` (`gangland-build/pom.xml:74`)
  from shading Keystone code into the Gangland jar. State this explicitly — the shade wildcard makes it silent.

- **B3. §9 D3's gate collapse rests on a WS2 text that no longer exists, and R3 contradicts it.** D3 argues an
  interim headless gate would be "throwaway code against a Maven module WS2 has already deleted". The revised WS2
  plan says the opposite: `plans/WS2-inventory-oriel.md:19` (ruling on its own B1) renames G6 to "cutover (after
  WS4 consumer gate)", and `:67-69` states WS2 "only adds Oriel as an additional pom dependency to shop-api
  (additive, doesn't require deleting inventory-api) **so WS4 can start porting immediately**". `:91` adds
  `oriel-core`/`oriel-chest` to `shop-api/pom.xml` while `inventory-api` is still there. Under R3, `inventory-api`
  outlives WS4's consumer gate by construction. Required change: restore the two-step Gangland gate —
  **G1a** (steps 6-8, 10-11: Keystone repoint, poms, imports, config; the 9 view files keep compiling on
  inventory-api untouched, so this gate is buildable and smokeable alone) → **G1b** (step 9: the 9 files to Oriel)
  → feeds WS2's G6 cutover. This is not "one doomed release": G1a ships nothing user-visible and G1b's
  precondition (Oriel deps present on shop-api) is WS2 G0/G1's output, not G6's.

- **B4. Step 9 depends on an Oriel primitive that does not exist and that WS4 never asks for.** The 9 files call
  `host.rerender()` **8 times** — `shop/view/PriceEditorView.java:221,232` and
  `shop/view/ShopAdminView.java:155,299,308,346,368,390`. WS2's review promoted this to its top Oriel ask
  (`plans/WS2-inventory-oriel.md:138`, `:438`): Oriel's `MenuFlow` (`chest/flow/MenuFlow.java:95-161`) has **no**
  `rerender()`, and both substitutes are lossy (a real Bukkit reopen, or a re-render that never re-invokes
  `Panel.render`). §10's only Oriel ask is the price-editor's mirrored +/- rows. Required change: add
  `MenuFlow.rerender()` to §10 as WS4's **blocking** Oriel ask (shared with WS2), and make step 9 conditional on it
  or name the per-view fallback for all 8 sites.

- **B5. Moving `Trader.Max_Mode_Multiplier` into npc-shops' YAML breaks the core admin editor.** §5 routes the whole
  `Trader:` block to `npc/trader_settings.yml`, but `GanglandShopUiSettings.getMaxModeMultiplier()`
  (`gangland-impl/.../file/configuration/shop/GanglandShopUiSettings.java:16`) reads
  `Settings.getTraderMaxModeMultiplier()` (`Settings.java:735`), and that object is constructed **inline, never a
  bean**, precisely so `/glw shop edit`'s `PriceEditorView` works with **zero modules installed**
  (`ShopConfig.java:33-37,44`). §5's own note "`TraderSettingsImpl` supplies the only `ShopUiSettings` bean once
  npc-shops is installed" concedes the module-less case has no source. Required change: keep
  `Max_Mode_Multiplier` core (a `Shop:` block in `settings.yml`, or a constant — CT-18 shows the sibling cap is
  hard-coded anyway) and move only the six genuinely trader-owned keys.

## Corrections (fix in place)

- **C1. §2's pom table overstates `keystone-item` usage.** Actual imports in the 28 headless files:
  `ItemRefresherRegistry` (7), `ItemBuilder` (6), `ItemSerializerRegistry` (4). `ItemConverterRegistry` and
  `ItemDefinitions.pristine` appear **only** in `GanglandShopDisplayResolver.java:5-7,41` (the class being renamed
  `DefaultShopDisplayResolver`) — `grep -rn "ItemDefinitions\|pristine" gangland-ui/shop-api/src/main/java` → 0
  hits. The dependency is still justified; the sentence naming `ShopPurchaseService`/`BarterCategory`/the valuators
  as `ItemDefinitions` users is wrong. (The census surprise **is** resolved generically: `ItemDefinitions` is
  Keystone's own SPI, so no Gangland coupling travels with it.)
- **C2. Bean counts.** `ShopConfig` has **10** headless `@Bean`s (`ShopConfig.java:52-108`) and **5** admin-view
  `@Bean`s (`:113-146`), not "6 headless"/"6 then 5". `ShopAdminFlowSession` is not a bean.
- **C3. §5 does not satisfy C6 — enumerate the keys.** Verified totals: `Trader:` = `settings.yml:658-673`,
  **7 keys** (`Respawn_Cooldown`, `Head_Track_Radius`, `Fallback_Trait_Id`, `Max_Mode_Multiplier`,
  `Sell.Max_Offer_Slots`, `Sell.Mood_Per_Sale`, `Tip_Amount` — `Settings.java:729-738`; the plan's "658-671" cuts
  off `Mood_Per_Sale` and `Tip_Amount`); `Banker:` = `:681-689`, **4 keys** (`Settings.java:741-745`). Messages:
  `SHOP_` **29**, `TRADER_` **12**, `BANKER_` **29** (definition-line counts in
  `gangland-api/.../Messages.java`) — the plan's "~27 SHOP_" is close, but C6 wants the table, not the range.
  Also list the 11 `Settings` static getters that get deleted (`Settings.java:196-207`) and the `message_es.yml`
  rows (US-35, CM-15).
- **C4. The "event-in-a-headless-module precedent" conflates two different things.** Keystone shipping an `Event`
  subclass (`keystone-npc/.../npc/event/NpcEvent.java:13`) is precedented; shipping a `@ListenerHandler`-annotated
  `Listener` is **not** — `grep -rl "@ListenerHandler" keystone-*/src/main/java` returns only the framework itself
  (`BeanFactory`, `ListenerService.java:184`, `ModuleRegistrar`), and Keystone's `CLAUDE.md:54` says it "registers
  no commands or listeners". `ShopEditPersistenceHandler` would be the first. See S1 — the cheapest fix deletes
  the problem instead of arguing it.
- **C5. Quote the whole of Keystone `CLAUDE.md:101` in D1 — it is worse for option A than the plan admits.** The
  line's `keystone-npc` carve-out literally names the counter-example: "product concepts riding on top of it —
  wanted levels, civilian/police marks, **trader shops**, what fires a ranged attack — stay in the consumer".
  The amendment is still the right call (the 28 files name no gang/trader/weapon), but the user must see that the
  rule already ruled on this phrase, so the amendment is a reversal, not a clarification.
- **C6. The 8 test files cannot move as a package rename alone.** Four of them call
  `org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture.install()` (`ShopDefinitionTest.java:9,33`,
  `ShopYamlReaderTest.java:10,50`, `ShopBarterServiceTest.java:13`, …) from `gangland-core`'s test-jar
  (`shop-api/pom.xml:66-71`). Keystone can never depend on gangland-core. Make "port the fixture into
  `keystone-testkit` (or map it onto `BukkitStatics`)" an explicit G0 step, not a §13 note — it is the one thing
  that can stall G0.
- **C7. Missing steps.** No step updates: repo `CLAUDE.md`'s module table (`gangland-ui/shop-api` row) and its
  `gangland-api` row; `documentation/features/{bank,traders}.md`, `documentation/tests/features/trader-shop.md`,
  `documentation/module-loader.md`, `documentation/FRONT-PAGE.md` + `.bbcode.txt` (all contain "shop-api");
  `graphify update . --force` in both repos; the docket record per C8; the memory note. WS2's review flagged the
  same class of omission.
- **C8. R1 makes §1's caution moot.** "Leave the ~27 `SHOP_*` in `gangland-api`" is now a *choice* (a good one —
  `/glw shop` is core), not a constraint. Say so, so the executor does not think removals are blocked.
- **C9. `gangland-build` needs no shade edit** (`<include>org.luckyraven:*</include>`, `gangland-build/pom.xml:74`)
  — but only if B2's `provided` scoping is done. Add the audit line
  (`unzip -l target/gangland_warfare-0.10.0.jar | grep keystone/shop` must be empty) to the G1 gate.

## Simplifications (ponytail)

- **S1. Leave `ShopEditPersistenceHandler` in `gangland-impl`; do not move it to Keystone.** It is 12 lines
  (`shop/handler/ShopEditPersistenceHandler.java:22-31`: `shopRegistry.save(...)` + one message + one log) and it
  is the **only** user of `keystone-bean` among the 28 headless files (the other three `ListenerHandler` imports
  are the UI listeners that stay in Gangland anyway). Moving it instead of the consumer keeping it: adds
  `keystone-bean` to `keystone-shop`, creates the first annotated `Listener` in Keystone (C4), forces step 8's
  second scan root, forces the new integration test, and creates §8 risk #1. Keeping it deletes all five of those
  and makes `keystone-shop` a zero-DI pure library (persistence + item + common only). `ShopEditedEvent` still
  ships from Keystone — that part is fine and precedented.
- **S2. `ShopUiSettings` should not go to Keystone.** After the views leave, **no** `keystone-shop` class
  references it (its users are `PriceEditorView.java:18,56`, `ShopAdminView.java:28,72` — both consumer-bound —
  plus `TraderSettings.java:7`, `TraderSettingsImpl.java:8`, `GanglandShopUiSettings.java:12`,
  `ShopConfig.java:15,44`). A headless module shipping a UI-settings interface used by nobody inside it is the
  product-concept leak D1 promises to avoid, and it would make an npc-shops interface inherit a Keystone type.
  Keep it in `gangland-api`. Combined with B5 this makes the settings story coherent.
- **S3. `ShopPipelineScenarioTest` (§7) is optional.** The 8 ported tests already run with zero Gangland classes on
  the classpath once C6's fixture question is settled — that *is* the headlessness proof. Keep it to one method or
  drop it; do not let it grow into a second consumer harness.
- **S4. Do not bundle the 41 `TRADER_*`/`BANKER_*` message constants (§9 D5).** The shop move does not require it;
  it needs a new message-YAML loader in npc-shops plus a Spanish-parity pass, and it is the step most likely to
  silently drop strings (§8 risk 5). The 11 settings keys (minus `Max_Mode_Multiplier`, B5) are genuinely coupled
  and should stay; the messages belong to WS6's per-module migration.

## Missing consumers found by graphify affected

| Type moved | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `ShopAdminFlow` | `ShopViewOpenerImpl` (npc-shops field + 2 calls) | `.../trader/ShopViewOpenerImpl.java:23,42,55` | B1 — module cannot import `gangland-impl` |
| `ShopAdminFlow` | `TraderModuleConfig.shopViewOpener()` | `.../config/TraderModuleConfig.java:31,169` | B1 — bean wiring breaks |
| `ShopUiSettings` | `TraderSettings` / `TraderSettingsImpl` (module interface inheritance) | `.../trader/config/TraderSettings.java:7`, `.../integration/TraderSettingsImpl.java:8` | S2 — module inherits a Keystone type |
| `ShopUiSettings` | `GanglandShopUiSettings` (core, zero-module path) | `.../shop/GanglandShopUiSettings.java:12,16` | B5 — loses its config source |
| `ShopItemEntry` | npc-shops **events** carry it in their signatures | `.../events/trader/TraderBarterEvent.java:11,27`, `TraderBuyRequestEvent.java:10,19` | A Keystone type enters the module's own event API; fine, but state it |
| `ShopDisplayResolver` | 6 npc-shops files beyond the config | `TraderBarterListener.java:13`, `TraderBuyListener.java:18`, `BarterView.java:25`, `NegotiationView.java:24`, `SellView.java:26`, `ShopView.java:20` | Import rename count is **20** npc-shops files, not "~19" |
| `MultiPanelInventory.rerender()` | 8 call sites inside the 9 rewritten files | `PriceEditorView.java:221,232`, `ShopAdminView.java:155,299,308,346,368,390` | B4 |
| `BukkitRegistryFixture` | 4 of the 8 ported tests | `ShopDefinitionTest.java:9`, `ShopYamlReaderTest.java:10`, `ShopBarterServiceTest.java:13`, … | C6 |
| `shop-api` (name) | docs + repo `CLAUDE.md` module table | `documentation/features/{bank,traders}.md`, `documentation/module-loader.md`, `documentation/tests/features/trader-shop.md`, `documentation/FRONT-PAGE.md`, `CLAUDE.md` | C7 |
Negative results (checked, nothing to fix): signs are clean — `BaseTradeSign`
(`gangland-api/.../sign/type/trade/BaseTradeSign.java`) and the `item-buy`/`item-sell` types name **no**
`gangland.shop` type; persistence is clean — `ShopRegistry` extends `FolderLoader`, folder `"shop"`, schema
`Title/Size/Buy_Entries/Sell_Entries/Sell_Categories/Barter_Categories` unchanged, so §6's "no migration" holds.

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| D1 where the shop lives | A. headless `keystone-shop` | **Agree**, with C5's fuller quote | The 28 files name no product concept (verified); B and C are worse. But the user must approve reversing a line that names "trader shops" by hand. Oriel's disclaimer is quoted accurately (`USE-CASE-ENABLERS.md:243-246`); note for fairness that the same doc claims atomic buy/sell as an enabler (`:230`), so C is "wrong layer", not "impossible" |
| D2 ship a default resolver | Ship `DefaultShopDisplayResolver` | **Agree** | `GanglandShopDisplayResolver.java` imports only `keystone-item`/`keystone-util` + Bukkit — nothing Gangland-specific; the javadoc's weapon/money wording needs rewriting, which the plan says |
| D3 collapse 4 gates → 2 | Collapse | **Disagree** — B3 | Premise contradicted by the revised WS2 plan (`:19,67-69`) and by R3. Two Gangland gates (G1a headless / G1b Oriel) are each buildable and smokeable alone |
| D4 where rebuilt views live | `gangland-impl/shop/admin/` | **Disagree as stated** — B1 | The *command* is core (true), but a module names the flow. Needs the `gangland-api` seam |
| D5 bundle Trader/Banker config + messages | Bundle both | **Split**: bundle the settings keys (minus `Max_Mode_Multiplier`), defer the 41 message constants | S4 — messages are a separate loader + i18n job with its own failure mode |
| (not surfaced) `ShopUiSettings` destination | implicitly Keystone | **Should be a decision** | S2 |

## Estimate check

12 steps / 6S 5M 1L / 4.5-5.5 days is **optimistic by roughly a third**.
- Step 9 (L) is under-sized: 9 files + 8 `rerender()` sites (B4) + a `MenuFlow`/`Panel` redesign + an unresolved
  anvil question for `PriceEditorView`. Call it **L+ / 2 days**, and it cannot start before Oriel's `rerender()`
  ask lands.
- Step 11 (M) is under-sized: 11 settings keys, a new YAML file + loader in npc-shops (npc-shops has no message
  YAML today — the module's messages come from the api `Messages` enum), 41 constants, plus an `es` parity diff.
  With S4 applied it is a genuine M; as written it is an L.
- Step 2 (M) hides C6's testkit fixture port — add an S/M step in G0.
- Missing steps entirely: docs sweep (5 files + `CLAUDE.md`), `graphify update --force` ×2, docket record, memory
  note, the shade audit line (C9), and the `gangland-impl` pom's own `keystone-shop` `provided` entry (B2).
- Realistic: **~16 steps, 6-8 days**, split G0 ≈ 1.5 / G1a ≈ 1.5 / G1b ≈ 3-4.
- Docket §11 is **accurate** — I checked every id against `bug-docket-2026-09-06/bugs.json` (498 entries): CT-04
  P1, CT-05 P2, CT-06 P3, CT-07 P2, CT-08 P2, CT-16/17/18/20 P3, CT-22 P3, CT-23 P3, US-28 P3, LS-19 P0, plus
  US-35/CM-15 for the Spanish risk. Titles and the core/npc-shops split match; CT-18's file really is
  `QuantitySelectorView.java:46` (npc-shops), so the "not WS4's files" classification holds. Effects are stated
  per id. Rollback story is sound for G0; under B3 it improves (G1a reverts without touching any view).

## Things I could not verify

- Whether Oriel's `AnvilMenu`/`ButtonComponent` can express `PriceEditorView`'s mirrored +/- ladder — same gap the
  plan admits in §13; I did not read Oriel's component sources.
- Whether `keystone-testkit`'s `BukkitStatics` covers what `BukkitRegistryFixture.install()` does (I listed
  testkit's 12 classes but read neither fixture).
- `ListenerManager.scanAndRegisterListeners`'s reflective scan behaviour over a Keystone-shaded package — the plan
  flags it; S1 makes the question moot rather than answering it.
- Whether any npc-shops **test** breaks on the import rename — no npc-shops test references `gangland.shop`
  (`grep` → 0 outside shop-api's own 8), but I did not run the reactor.
- The exact `message_es.yml` delta for the 41 `TRADER_*`/`BANKER_*` keys.
