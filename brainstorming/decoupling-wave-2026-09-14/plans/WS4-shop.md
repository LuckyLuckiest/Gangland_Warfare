# WS4 Plan — shop-api becomes a reusable Keystone system

> User's words: *"The shop-api can be a system that is in keystone and not specifically in gangland warfare and
> then we can reuse it again."*

Sources: `README.md` (A1-A5, C1-C8), `PLANNER-BRIEF.md`, `census/WS4-shop.md` (Haiku — **corrected below**, see §0),
`census/WS2-inventory-oriel.md` §3-4, `census/WS6-api.md` §5-6, `module-api-2026-09-10/README.md` (D2/D5/D8 —
`gangland-api` re-export mechanics), `bartizan-split-2026-09-08/architecture/PICK.md` (decision-table house style),
Keystone `CLAUDE.md`, `plans/WS2-inventory-oriel.md` (revised, post-review — its own §0b, §3 rerender row, §9 D-B1
cutover ruling, §10 Oriel asks). graphify (`explain ShopRegistry`, `explain PaymentHandler` in Gangland;
`query ItemParser`, `explain EconomyHandler` in Keystone) oriented every §0 claim; `reviews/REVIEW-WS4.md` (Opus,
PASS WITH FIXES) re-verified §0 against a fresh graph and is applied in full below (§0b).

---

## 0. Correcting the census before planning on it

The Haiku census's "23 of 37 files import inventory-api" (`census/WS4-shop.md` §1) conflates Bukkit's own
`org.bukkit.inventory.*` (used by almost every class that touches an `ItemStack`) with the real Gangland
`org.luckyraven.gangland.inventory.*` Maven module. Verified:

```
grep -rl "org.luckyraven.gangland.inventory" gangland-ui/shop-api/src/main/java  →  9 files
```

Those 9 are exactly `shop.view.*` (6: `ShopAdminFlow`, `ShopAdminFlowSession`, `ShopAdminView`,
`BarterCategoryItemsAdminView`, `SellCategoryItemsAdminView`, `PriceEditorView`) and `shop.listener.*` (3:
`ShopAdminListener`, `BarterCategoryAdminListener`, `SellCategoryAdminListener`). Everything else — including
`ShopPurchaseService`, `ShopSellService`, `ShopBarterService`, `BarterCategory`, `SellCategory`, `PurchaseResult`,
`SellResult`, `BarterResult` — imports only Bukkit + `keystone-item` + `keystone-persistence`. **The real split is
28 headless / 9 UI-coupled, not 14/23.** Review re-verification: 37 main files confirmed, the 9-file grep confirmed
exactly the 6+3 named above. This roughly triples the amount of code that ports to Keystone unchanged.

Other census corrections, all verified by direct read and re-verified by review:
- "`ItemValuation` depends on gangland-item's `ItemParser`" — false. `ItemValuation` (`shop/valuation/ItemValuation.java:1-18`)
  is a zero-dependency record. No file in shop-api imports `ItemParser` at all (`grep -rn ItemParser
  gangland-ui/shop-api/src` — no hits, confirmed by review). `ShopYamlReader` deserializes `ItemStack`s off the raw
  Bukkit `FileConfiguration` (`shop/io/ShopYamlReader.java:64`), not through a parser.
- "1 `SHOP_*` Messages constant" — false. `Messages.java` has **29 distinct `SHOP_*`** constants
  (`Messages.java:358-375,535-545`), **12 `TRADER_*`**, **29 `BANKER_*`** (review's exact recount, §0b C3).
- "`gangland-core`/`gangland-item` provided-scope deps block a clean move" — false. Zero source files import them
  (`shop-api/pom.xml:30-39,50-53`) — dead pom entries; the `gangland-core` test-jar dependency
  (`BukkitRegistryFixture`) is the only legitimate use, and it's test-scope (see §0b C6 — this one *does* matter).
- "PaymentHandler implementation not found" — found. `TraderBuyListener.adapt(EconomyHandler)`
  (`.../listener/trader/TraderBuyListener.java:103-120`) wraps Keystone's own `EconomyHandler`
  (`keystone-hooks/.../EconomyHandler.java:51,68,72`) 1:1. `PaymentHandler` never imports `EconomyHandler`
  (`shop/transaction/PaymentHandler.java:1-17` — `java.math.BigDecimal` only); the economy seam is already fully
  consumer-side and already routes through Keystone, not a Gangland money system.
- "Shop admin editor is npc-shops' problem" — false. `/glw shop {create,edit,list,remove,title}` is core
  (`ShopCommand.java:8,16` + five siblings). `ShopConfig.java:29-32`: "this class must keep working with zero
  modules installed, since `ShopCommand` injects `ShopRegistry` and `ShopAdminFlow` directly." The 9 UI-coupled
  files are **core**, not npc-shops-owned — npc-shops' own trader/banker view files are a separate set tracked by
  WS2 and out of scope here. (Review: `graphify affected "ShopAdminFlow"` confirms this exactly — only
  `ShopViewOpenerImpl`/`TraderModuleConfig` (npc-shops) plus core `ShopCommand`/`ShopEditCommand`/`ShopConfig`.)

---

## 0b. Review response

`reviews/REVIEW-WS4.md` verdict: **PASS WITH FIXES** — 5 blockers, 9 corrections, 4 simplifications. §0's own
corrections were independently re-verified and stand unchanged. Every id below is applied in place; orchestrator
rulings (not the reviewer's own suggested alternative, where they differ) are what's implemented.

| Id | Finding | Resolution | Where |
|---|---|---|---|
| B1 | Rebuilt admin views can't live in `gangland-impl` as bare classes — npc-shops names `ShopAdminFlow` directly (`ShopViewOpenerImpl.java:23,42,55`, `TraderModuleConfig.java:31,169`); a module compiles against `gangland-api` only, never `gangland-impl` | **Orchestrator ruling**: views stay in `gangland-impl`; a new one-method `ShopAdminOpener{void openAdmin(Player,ShopDefinition);}` in `gangland-api`, implemented+registered by core, injected into npc-shops in place of the flow | §2, §4 steps 8/10/14, §9 D4, §10 WS6 asks |
| B2 | `keystone-shop` at `provided` is not transitive — the old step 10 "drop the direct `shop-api` dep, comes through the re-export" only held while shop-api was *compile*-scope; `provided` never propagates (`gangland-npc-shops/pom.xml:28` says so in its own comment) | **Swap**, not drop: npc-shops declares `keystone-shop` directly (`provided`), so does `gangland-impl`; `gangland-api`'s entry moves out of the compile-scope re-export block into the Keystone `provided` block — otherwise `gangland-build`'s `<include>org.luckyraven:*</include>` wildcard silently shades Keystone code into the Gangland jar | §2, §4 steps 7/10, §8 new risk, §12 |
| B3 | D3's "collapse to one Gangland gate" rested on a stale reading of WS2's plan; the **revised** WS2 plan renames its cutover to "G6 (after WS4 consumer gate)" and states it adds Oriel to shop-api's pom *additively* "so WS4 can start porting immediately" — under ruling R3, `inventory-api` outlives WS4's own consumer gate by construction | **Restored the two-step split**: G1a (headless repoint, buildable/smokeable alone, 9 files keep compiling on inventory-api untouched) → G1b (9 files to Oriel) → feeds WS2's G6 cutover | §4, §9 D3 ("split restored"), §8 |
| B4 | Step 9 (old)/13 (new) silently assumed an Oriel primitive that doesn't exist: the 9 files call `host.rerender()` **8 times** (`PriceEditorView.java:221,232`; `ShopAdminView.java:155,299,308,346,368,390`); `MenuFlow` has no equivalent | Added as G1b's **blocking precondition**, shared with WS2's own top-priority Oriel ask (`MenuFlow.rerender()`); named per-site fallback if declined | §4 step 13, §8, §10 |
| B5 | Routing the whole `Trader:` block to npc-shops YAML breaks the core admin editor: `GanglandShopUiSettings.getMaxModeMultiplier()` backs `PriceEditorView`, which must work with **zero modules installed** (`ShopConfig.java:33-37,44`) — the module-supplied `TraderSettingsImpl` is not there in that case | **`Max_Mode_Multiplier` and `ShopUiSettings`/`GanglandShopUiSettings` stay Gangland-side**, unchanged; only the genuinely trader/banker-owned keys migrate | §2, §4 step 11, §5, §9 D6 |
| C1 | §2's pom table overstated `ItemConverterRegistry`/`ItemDefinitions.pristine` usage across all 28 headless files | Corrected: those two appear **only** in `GanglandShopDisplayResolver.java:5-7,41` (→ `DefaultShopDisplayResolver`); the rest use `ItemRefresherRegistry`(7)/`ItemBuilder`(6)/`ItemSerializerRegistry`(4) | §2 |
| C2 | Bean counts wrong ("6 headless"/"6 then 5") | `ShopConfig` has **10** headless `@Bean`s (`ShopConfig.java:52-108`), **5** admin-view `@Bean`s (`:113-146`); `ShopAdminFlowSession` is not a bean | §2, §4 step 9 |
| C3 | §5 didn't enumerate the key/constant table C6 requires | Full table: `Trader:` 7 keys (`settings.yml:658-673`, `Settings.java:729-738`), `Banker:` 4 keys (`:681-689`, `Settings.java:741-745`); `SHOP_` 29 / `TRADER_` 12 / `BANKER_` 29; 11 `Settings` getters deleted (`Settings.java:196-207`); `message_es.yml` gaps US-35/CM-15 | §5 |
| C4 | "Event-in-a-headless-module precedent" conflated a Bukkit `Event` subclass (precedented — `keystone-npc`'s `NpcEvent`) with a `@ListenerHandler`-annotated `Listener` (not precedented — Keystone ships zero today, `CLAUDE.md:54`) | Moot after S1 — `ShopEditPersistenceHandler` no longer ships from Keystone; only `ShopEditedEvent` (the precedented half) moves | §3 |
| C5 | D1 under-quoted `CLAUDE.md:101` — the line's `keystone-npc` carve-out already names "trader shops" as a *counter-example* | Full quote restored in D1; reframed as a **reversal**, not a clarification | §9 D1 |
| C6 | 4 of the 8 tests call `gangland-core`'s `BukkitRegistryFixture.install()` (`ShopDefinitionTest.java:9,33`, `ShopYamlReaderTest.java:10,50`, `ShopBarterServiceTest.java:13`, …) — Keystone can never depend on `gangland-core` | New explicit **G0 step**: port the fixture into `keystone-testkit` (or confirm `BukkitStatics` already covers it) before the test move, not a §13 footnote | §4 step 2, §7, §13 |
| C7 | Missing steps: repo `CLAUDE.md` module table, 5 docs files naming "shop-api", `graphify update --force` ×2, docket record, memory note | Added as explicit hygiene steps | §4 steps 16-18 |
| C8 | R1 (api 2.0 bumped in 0.10.0's first commit) makes §1's "SHOP_* stays" a **choice**, not a rule-forced constraint | Reworded in §1/§5 | §1, §5 |
| C9 | `gangland-build` needs no shade edit — but **only if** B2's scoping is right, and the wildcard shade makes a miss silent | Audit line (`unzip ... \| grep keystone/shop` must be empty) added to the G1a gate | §4 step 12 |
| S1 | `ShopEditPersistenceHandler` is 12 lines and the **only** user of `keystone-bean` among the 28 headless files; moving it forced a `keystone-bean` dependency, the first annotated `Listener` ever shipped from Keystone (C4), a listener-scan fix, a new integration test, and risk #1 | Left in `gangland-impl`; `keystone-shop` drops `keystone-bean` entirely — a **zero-DI pure library** (item + persistence + common only). `ShopEditedEvent` still ships from Keystone (fine, precedented) | §2, §3, §4, §7, §8 |
| S2 | Once the views leave, **no** `keystone-shop` class references `ShopUiSettings` — shipping a UI-settings interface used by nobody inside the module is the exact product-concept leak D1 promises to avoid | Kept in `gangland-api` (combines with B5) | §2, §9 D6 |
| S3 | `ShopPipelineScenarioTest` risked growing into a second consumer harness; the 8 ported tests already prove headlessness on their own | Marked optional — one method, or drop | §7 |
| S4 | Bundling 41 `TRADER_*`/`BANKER_*` constants needs a new message-YAML loader in npc-shops (which has none today) plus a Spanish-parity pass — a separate failure mode from the settings move | **Deferred** to WS6's per-module migration; only the 10 genuinely-owned settings keys (`Max_Mode_Multiplier` excluded, B5) move this wave | §5, §9 D5 (flipped to "defer") |

---

## 1. Scope

**In:**
- New Keystone Maven module `keystone-shop`, a **zero-DI pure library** (S1): 26 headless shop-api files +
  `GanglandShopDisplayResolver` (renamed `DefaultShopDisplayResolver`) + 8 existing tests + 1 new test.
- Delete `gangland-ui/shop-api` (37 files) from the Gangland reactor.
- Rebuild the 9 UI-coupled files (6 admin views + 3 admin listeners) on Oriel primitives, relocated into
  `gangland-impl` — reached from npc-shops only through a new `ShopAdminOpener` interface in `gangland-api` (B1),
  never named directly by a module.
- Repoint `gangland-impl/config/ShopConfig.java` and `gangland-features/gangland-npc-shops` (`TraderModuleConfig.java`
  + 20 files, corrected count per §0b) at `org.luckyraven.keystone.shop.*`, with `keystone-shop` declared directly
  (`provided`) in both poms (B2) — not inherited through `gangland-api`.
- Migrate the 10 genuinely trader/banker-owned `settings.yml` keys (6 `Trader:` + 4 `Banker:`, `Max_Mode_Multiplier`
  excluded — B5) to npc-shops-owned YAML. The `SHOP_*`/`TRADER_*`/`BANKER_*` `Messages` constants are **not** moved
  this wave (S4) — that's a choice enabled by R1's api-2.0 bump, not a rule forcing them to stay (C8).
- Amend Keystone `CLAUDE.md:101` to admit generic commerce primitives — this is a **reversal** of a line that
  already names "trader shops" by hand (C5), not a clarification; see D1.

**Out (explicitly, with reason):**
- npc-shops' own 18 trader/banker view files — WS2's territory; WS4 only changes their `shop.*` **imports** (now 20
  files, §0b table), not their Oriel port.
- A real second (non-Gangland) consumer plugin — proven instead by an optional, one-method scenario test (§7, S3).
- Fixing any docket entry as a side effect — structural move only (§11).
- Auction houses, market price curves, or any economy *domain rule* — Oriel's own docs disclaim this territory
  (`Oriel/docs/USE-CASE-ENABLERS.md:243-246`), reinforcing that `ShopPurchaseService`/valuators belong in a headless
  framework module, not in Oriel.
- Central publication of `keystone-shop` — same pending namespace/GPG/token setup as everything else.
- The 41 `TRADER_*`/`BANKER_*` `Messages` constants — deferred to WS6 (S4/D5).

**Deferred:** `japicmp`/`revapi` on `keystone-shop`; a `ShopRegistry` accessor on the `GanglandApi`/WS6 facade (§9,
nobody has asked for cross-plugin shop lookup).

---

## 2. Target layout

### Keystone: new module `keystone-shop`

```
Keystone/
  pom.xml                                  # + <module>keystone-shop</module>, after keystone-npc (pom.xml:49)
  keystone-shop/
    pom.xml                                # deps below — NO keystone-bean (S1)
    src/main/java/org/luckyraven/keystone/shop/
      ShopDefinition.java                  # ← gangland.shop.ShopDefinition            (unchanged)
      ShopItemEntry.java                   # ← gangland.shop.ShopItemEntry             (unchanged)
      ShopRegistry.java                    # ← gangland.shop.ShopRegistry              (unchanged, extends Keystone's own FolderLoader)
      BarterCategory.java                  # ← gangland.shop.BarterCategory            (unchanged)
      SellCategory.java                    # ← gangland.shop.SellCategory              (unchanged)
      EntryKind.java                       # ← gangland.shop.EntryKind                 (unchanged)
      event/ShopEditedEvent.java           # ← gangland.shop.event.ShopEditedEvent     (unchanged — plain Bukkit Event, §3)
      io/ShopYamlReader.java               # ← gangland.shop.io.ShopYamlReader         (unchanged)
      io/ShopYamlWriter.java               # ← gangland.shop.io.ShopYamlWriter         (unchanged)
      message/ShopDisplayResolver.java     # ← gangland.shop.message.ShopDisplayResolver (unchanged, pure interface)
      message/ShopMessageContract.java     # ← gangland.shop.message.ShopMessageContract (unchanged, pure interface)
      message/DefaultShopDisplayResolver.java  # ← gangland-api's GanglandShopDisplayResolver, RENAMED (new default impl)
      transaction/*.java                   # 11 files ← gangland.shop.transaction.*   (unchanged)
      valuation/*.java                     # 4 files  ← gangland.shop.valuation.*     (unchanged)
    src/test/java/org/luckyraven/keystone/shop/   # 8 files, package-renamed only, assertions unchanged
      + message/DefaultShopDisplayResolverTest.java   # NEW — zero prior coverage
  keystone-plugin/pom.xml                  # + keystone-shop dependency (compile), shaded into Keystone.jar
  keystone-testkit/                        # + BukkitRegistryFixture equivalent (C6, §4 step 2) — verify/port BEFORE step 3
  docs/keystone-shop.md                    # new, mirrors docs/phase-h8-item-npc.md
```

**Removed from the move vs. the original plan (S1, S2/B5):** `config/ShopUiSettings.java` and
`handler/ShopEditPersistenceHandler.java` **stay in Gangland** — see §9 D6 and the Gangland tree below. 26 headless
files (was 28) + `DefaultShopDisplayResolver` = **27 files** move to Keystone, not 29.

`keystone-shop/pom.xml` dependencies (mirrors `keystone-npc/pom.xml`, corrected per C1/S1):

| Dependency | Scope | Why (verified) |
|---|---|---|
| `keystone-item` | compile | `ItemRefresherRegistry` (7 uses), `ItemBuilder` (6), `ItemSerializerRegistry` (4) across the transaction/valuation/category classes; `ItemConverterRegistry` + `ItemDefinitions.pristine` **only** in `DefaultShopDisplayResolver` (C1) |
| `keystone-persistence` | compile | `FileHandler`/`FileManager`/`FolderLoader`/`ConfigReport`/`NodeReader`/`FileHandlerReader` used by `ShopRegistry`, `ShopYamlReader`/`Writer` |
| `keystone-common` | compile (transitive via the above; declared explicitly per house convention) | no direct API call found |
| `keystone-testkit`, `mockito-core` | test | matches every other module |

**Not needed:** `keystone-bean` (S1 — `ShopEditPersistenceHandler`, its only would-be user, stays in the consumer;
`keystone-shop` needs zero DI/listener machinery), `keystone-hooks` (`PaymentHandler` never references
`EconomyHandler`), `XSeries` (UI-only), `gangland-core`/`gangland-item` (dead weight — §0). This is a **smaller**
footprint than `keystone-npc`, not the same size as originally planned.

### Gangland: before → after

```
gangland-ui/shop-api/                       DELETED (37 main + 8 test files)
gangland-ui/pom.xml                         <module>shop-api</module> line removed

gangland-api/.../file/configuration/shop/   DELETED (GanglandShopDisplayResolver moved to Keystone as the default)
gangland-api/pom.xml                        keystone-shop moves OUT of the compile-scope re-export block (was
                                             shop-api's spot, pom.xml:25-26) INTO the Keystone provided block
                                             (:51-81) — B2
gangland-api/.../shop/ShopAdminOpener.java  NEW (B1) — one-method interface:
                                             void openAdmin(Player admin, ShopDefinition definition);

gangland-impl/pom.xml                       + keystone-shop, <scope>provided</scope> (B2 — declared directly,
                                             not inherited)
gangland-impl/.../config/ShopConfig.java    KEPT, repointed at org.luckyraven.keystone.shop.*; 10 headless @Beans
                                             (C2) retarget; 5 admin-view @Beans (C2) stay wiring the relocated
                                             views; new shopAdminOpener() @Bean returns ShopAdminOpenerImpl;
                                             shopUiSettings (GanglandShopUiSettings, inline-constructed) UNCHANGED
                                             (B5/S2); shopDisplayResolver bean now returns DefaultShopDisplayResolver
gangland-impl/.../shop/ShopEditPersistenceHandler.java  STAYS (S1) — imports repointed to keystone.shop.*, no
                                             other change; no listener-scan fix needed, already under the scanned
                                             org.luckyraven.gangland root
gangland-impl/.../shop/admin/view/          RELOCATED (6 files, G1a) — ShopAdminFlow, ShopAdminFlowSession,
                                             ShopAdminView, BarterCategoryItemsAdminView, SellCategoryItemsAdminView,
                                             PriceEditorView — moved bodily, STILL on inventory-api until G1b (B3)
gangland-impl/.../shop/admin/listener/      RELOCATED (3 files, G1a) — ShopAdminListener, BarterCategoryAdminListener,
                                             SellCategoryAdminListener — same, unchanged until G1b
gangland-impl/.../shop/ShopAdminOpenerImpl.java  NEW (B1) — implements ShopAdminOpener, wraps ShopAdminFlow

gangland-features/gangland-npc-shops/       20 files (corrected, §0b): mechanical import rename shop.* →
                                             keystone.shop.*
  .../config/TraderModuleConfig.java          shopViewOpener() param type ShopAdminFlow → ShopAdminOpener (B1)
  .../trader/ShopViewOpenerImpl.java          field type ShopAdminFlow → ShopAdminOpener (B1)
  pom.xml                                     SWAP <artifactId>shop-api</artifactId> → keystone-shop, provided
                                               (B2 — was wrongly "drop" in the earlier draft)
  src/main/resources/npc/trader_settings.yml  NEW — 6 Trader: keys (Max_Mode_Multiplier excluded, B5)
  src/main/resources/npc/banker_settings.yml  NEW — 4 Banker: keys
```

No `module.yml` involvement — `keystone-shop` is a Keystone Maven module shaded into `Keystone-<version>.jar`
exactly like `keystone-npc`/`keystone-item`. Shop *definitions* stay data, not code — §6.

---

## 3. Seams

| Boundary | Mechanism | Publisher | Puller | Default when absent |
|---|---|---|---|---|
| Gangland → `keystone-shop` classes | `provided`-scope compile dependency, declared directly by every consuming pom (B2 — not transitive) | Keystone.jar (shaded) | `gangland-impl`, `gangland-npc-shops`, each with its own pom entry | n/a — same classloader |
| `ShopRegistry`/services construction | Constructor injection via `@Bean` (`ShopConfig.shopRegistry`, unchanged signature `(JavaPlugin, FileManager, ShopYamlReader, ShopYamlWriter)`) | `gangland-impl` CONFIG phase | `TraderModuleConfig` (npc-shops), `ShopCommand` (core) via `DependencyContainer` | n/a — no static singleton |
| **`ShopAdminOpener` (NEW, B1)** | One-method interface in `gangland-api`; `ShopAdminOpenerImpl` (`gangland-impl`) wraps the real `ShopAdminFlow`, registered under the interface type | `gangland-impl` CONFIG phase | `TraderModuleConfig.shopViewOpener()` (npc-shops) | No default — a module reaching for shop-admin UI without core installed has nothing to open, same as today (`/glw shop` is a core command, not a module feature) |
| `ShopEditedEvent` | Plain Bukkit `Event` (unchanged), fired by `ShopAdminFlow` (still `gangland-impl`, relocated not moved to Keystone), caught by `ShopEditPersistenceHandler` (also `gangland-impl`, S1) | `keystone-shop` ships only the event **class** | Gangland's existing `ListenerManager` scan (`GanglandContext.java:84,250`, `LISTENER_PACKAGE = "org.luckyraven.gangland"`) — **no change needed**, the handler never left that root | n/a — no gap (S1 supersedes the earlier "gap found" finding, C4) |
| `PaymentHandler` ↔ economy | Interface adaptation, zero coupling to `EconomyHandler` inside `keystone-shop` | Consumer (`TraderBuyListener.adapt`) | `ShopPurchaseService`/`ShopSellService`/`ShopBarterService` | Consumer decides; a non-Gangland consumer implements 1 interface, 3 methods, no Vault/EconomyHandler required |
| `ShopDisplayResolver` | Interface, one shipped default | `keystone-shop` (`DefaultShopDisplayResolver`, needs only `keystone-item`) | Any consumer's `ShopConfig`-equivalent `@Bean` | Ship the default (§9 D2), same "safe default" shape as Bartizan's `CombatEligibility` |
| `ShopUiSettings` (S2 — stays Gangland) | Interface in `gangland-api` (unmoved) | `GanglandShopUiSettings` (core, inline-constructed) or `TraderSettingsImpl` (npc-shops, extends it) | `PriceEditorView`, `ShopAdminView` (relocated but Gangland-internal, never Keystone) | No `keystone-shop` class references it — it never needed to travel |
| `ShopMessageContract` | Interface, zero shipped default | Consumer (`GanglandShopMessages`, unchanged, `gangland-impl`) | `ShopEditPersistenceHandler`, transaction services | No default — every consumer must supply one |
| Panel re-render (B4) | GLW's `MultiPanelInventory.rerender()` re-runs `Panel.render` into an already-open inventory; Oriel's `MenuFlow` has no equivalent | — | 8 call sites inside the relocated `PriceEditorView`/`ShopAdminView` | **Blocking precondition of G1b** — see §10; fallback named per-site if declined |

**Event-in-a-headless-module precedent, corrected (C4):** Keystone's `keystone-npc` ships a plain `Event` subclass
(`NpcEvent.java:13`) despite "registers no commands or listeners" (`CLAUDE.md:54`) — precedented, and
`ShopEditedEvent` follows it unchanged. Shipping an **annotated `Listener`** is a different claim and was never
actually precedented (`grep -rl "@ListenerHandler" keystone-*/src/main/java` returns only the framework itself);
S1 makes this moot by keeping `ShopEditPersistenceHandler` in the consumer, so `keystone-shop` never becomes the
first Keystone module shipping an active listener.

---

## 4. Steps

### G0 — Keystone (independent of Oriel/WS2; can start immediately, zero blast radius until a consumer depends on it)

| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 1 | Keystone | new `keystone-shop/pom.xml` (mirrors `keystone-npc/pom.xml`, no keystone-bean — S1); root `pom.xml:49` `<module>` line; `keystone-plugin/pom.xml` dependency + shade | S | — | scaffold |
| 2 | Keystone | **(C6, new)** port `gangland-core`'s `testsupport.BukkitRegistryFixture` into `keystone-testkit`, or confirm `keystone-testkit`'s `BukkitStatics` already covers the same Bukkit `Material`/`ItemStack` mocking ground and just re-point the 4 dependent tests at it | M | the 4 tests that call `BukkitRegistryFixture.install()` are the acceptance check | fixture, **must land before step 3** |
| 3 | Keystone | `git mv` the **26** headless shop-api files (main) + 8 tests into `keystone-shop`, `sed` package `org.luckyraven.gangland.shop` → `org.luckyraven.keystone.shop`; `config/ShopUiSettings.java` and `handler/ShopEditPersistenceHandler.java` explicitly **excluded** (S1/S2) | M | existing 8 tests green post-rename, post-fixture-repoint | move+rename |
| 4 | Keystone | move+rename `gangland-api`'s `GanglandShopDisplayResolver.java` → `keystone-shop/.../message/DefaultShopDisplayResolver.java`; strip "gangland" wording | S | new `DefaultShopDisplayResolverTest` | same commit as 3 or its own |
| 5 | Keystone | `docs/keystone-shop.md`; amend `CLAUDE.md:101` with the **full** quoted reversal (C5, §9 D1) | S | — | docs |
| 6 | Keystone | `<revision>` → `1.10.0`; `mvn clean install`; verify `unzip -l keystone-plugin/target/Keystone-1.10.0.jar \| grep shop`, and that `keystone-shop`'s own jar has **no** `keystone-bean` class | S | full reactor `mvn test` | release |

**G0 gate:** `mvn -pl keystone-shop -am test` green, full reactor `mvn clean install` green. **Rollback:** revert
the G0 commit range — nothing outside `keystone-shop`/`keystone-testkit`/`keystone-plugin`'s shade list changed.

### G1a — Gangland, headless repoint (B3: buildable and smokeable **alone**, before Oriel touches anything;
the 9 UI files are relocated but left compiling on inventory-api, untouched, until G1b)

| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 7 | Gangland | `<keystone.version>` → `1.10.0`; `gangland-api/pom.xml`: move `keystone-shop` into the Keystone `provided` block (B2); add `keystone-shop` `provided` directly to `gangland-impl/pom.xml`; delete `gangland-ui/shop-api` + its `<module>` line, relocating its 9 UI files bodily into `gangland-impl/.../shop/admin/{view,listener}` **unchanged** (still importing inventory-api) | M | — | delete+relocate+repoint |
| 8 | Gangland | `gangland-api`: delete the old shop package (`GanglandShopDisplayResolver`, moved); **add** `ShopAdminOpener.java` (B1) | S | — | new seam |
| 9 | Gangland | `gangland-impl/config/ShopConfig.java` — retarget the **10** headless `@Bean`s (C2) at `org.luckyraven.keystone.shop.*`; the 5 admin-view `@Bean`s keep wiring the relocated (still inventory-api) views; `shopUiSettings`/`GanglandShopUiSettings` **unchanged** (B5/S2); `shopDisplayResolver` bean → `new DefaultShopDisplayResolver(...)`; new `shopAdminOpener()` `@Bean` returning `ShopAdminOpenerImpl` (wraps `ShopAdminFlow`); `ShopEditPersistenceHandler` stays, imports repointed only | M | none exists today for `ShopConfig` (flag in §13) | rewire |
| 10 | Gangland | `gangland-features/gangland-npc-shops` — **20-file** (§0b) mechanical import rename `shop.*` → `keystone.shop.*`; `TraderModuleConfig.shopViewOpener()` and `ShopViewOpenerImpl`'s field: `ShopAdminFlow` → `ShopAdminOpener` (B1); `pom.xml` **swap** (not drop) `shop-api` → `keystone-shop` `provided` (B2) | M | existing npc-shops tests green | rename+swap |
| 11 | Gangland | `settings.yml` — migrate **6** `Trader:` keys + **4** `Banker:` keys (10 total, `Max_Mode_Multiplier` excluded — B5) into `npc/trader_settings.yml`/`npc/banker_settings.yml`; delete the corresponding 11 `Settings.java:196-207` getters minus the one backing `Max_Mode_Multiplier`, which becomes a new small `Shop:` block (or a constant, per B5's own suggestion — CT-18 shows a sibling cap already hard-coded) in core `settings.yml`. **Messages are not touched this wave** (S4/D5) | M | — | config split |
| 12 | Gangland | Full reactor `mvn clean install` + `mvn test`; **G1a gate smoke**: `/glw shop create\|list\|remove\|title` work; `/glw shop edit` opens the relocated-but-still-inventory-api admin flow unchanged; a trader buy/sell/barter round-trips through `keystone-shop`'s services; **audit** `unzip -l target/gangland_warfare-0.10.0.jar \| grep keystone/shop` is **empty** (C9 — proves B2's provided scoping actually held) | — | — | **G1a gate** |

**G1a gate:** the state above is genuinely shippable on its own — nothing user-visible regresses, the admin editor
still looks and behaves exactly as before. **Rollback:** revert steps 7-11 as a unit; keep step 7's `shop-api`
deletion last within the batch so a partial revert restores working code from git history.

### G1b — Gangland, Oriel rewrite (blocked on WS2's `MenuFlow.rerender()` ask — B4)

| # | Repo | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|
| 13 | Gangland | Rebuild the 9 relocated files (`gangland-impl/.../shop/admin/{view,listener}`) from `MultiPanelInventory` onto Oriel `MenuFlow`/`Panel`/`ChestMenuBuilder` — **conditional on `MenuFlow.rerender()` landing** (§10); if declined, the 8 call sites each pick `switchTo(sameId)` (real reopen, flicker) or `ChestMenu.rerender()` (no `Panel.render` re-invocation — a panel that adds/removes a button on state change goes stale) per-site, decided at execution time | L | smoke row M-SHOP-1 (below); no prior unit tests to pin against | Oriel rewrite |
| 14 | Gangland | `ShopAdminOpenerImpl` repointed at the now-Oriel-based `ShopAdminFlow` — the interface boundary from step 8 doesn't change, only what's behind it | S | — | same commit as 13 |
| 15 | Gangland | Full reactor `mvn clean install` + `mvn test`; **G1b gate smoke**: full M-SHOP-1 (below), including admin edit on Oriel | — | — | **G1b gate** |

**G1b gate → feeds WS2's G6 cutover** (per B3, `inventory-api`'s deletion is a shared reactor-wide cutover that
runs after this gate, not before it). **Rollback:** revert steps 13-14; G1a's state is the safe fallback — nothing
about G1a depended on G1b landing.

### Hygiene (C7 — previously missing)

| # | Repo | Files | Size |
|---|---|---|---|
| 16 | Gangland | Docs sweep: repo `CLAUDE.md` module table (drop the `gangland-ui/shop-api` row, update the `gangland-api` row), `documentation/features/{bank,traders}.md`, `documentation/tests/features/trader-shop.md`, `documentation/module-loader.md`, `documentation/FRONT-PAGE.md` + its `.bbcode.txt` — all currently name "shop-api" | S |
| 17 | Both | `graphify update . --force` in Gangland and Keystone | S |
| 18 | Gangland | Docket record (§11 — relocated file paths, commit refs, per repo `CLAUDE.md`'s bug-docket rule); project memory note | S |

**Smoke row M-SHOP-1** (style of `bartizan-split-2026-09-08/smoke/`): fresh server, `npcshops` module installed,
console: `/glw shop create test`, `/glw shop edit test` (admin flow opens — on Oriel after G1b — add an entry,
save, `ShopEditedEvent` persists), spawn a trader NPC and buy/sell/barter as a player (`PaymentHandler` →
`EconomyHandler` round-trip), spawn a banker and deposit/withdraw. 0 console errors, 0 faults in
`Diagnostics.active()`.

---

## 5. Config, messages, permissions

Full C6 enumeration (C3):

| Item | Today | Destination | Note |
|---|---|---|---|
| `settings.yml` `Trader:` (658-673, `Settings.java:729-738`, **7 keys**: `Respawn_Cooldown`, `Head_Track_Radius`, `Fallback_Trait_Id`, `Max_Mode_Multiplier`, `Sell.Max_Offer_Slots`, `Sell.Mood_Per_Sale`, `Tip_Amount`) | `gangland-impl/src/main/resources/settings.yml` | **6 keys** → `npc/trader_settings.yml`; `Max_Mode_Multiplier` **stays core** (B5, new small `Shop:` block) | The plan's earlier "658-671" cut off `Mood_Per_Sale`/`Tip_Amount` — corrected range is 658-673 |
| `settings.yml` `Banker:` (681-689, `Settings.java:741-745`, **4 keys**) | same file | all 4 → `npc/banker_settings.yml` | none of the 4 is `Max_Mode_Multiplier`-equivalent, no exclusion needed |
| 11 `Settings` static getters | `Settings.java:196-207` | 10 deleted, 1 (`getTraderMaxModeMultiplier`) kept, repointed at the new core `Shop:` key | not independently re-verified — see §13 |
| 29 `SHOP_*` `Messages` constants (`Messages.java:358-375,535-545`) | `gangland-api` `Messages` enum | **stays** — `/glw shop` is core; R1 makes this a *choice*, not a forced constraint (C8) | |
| 12 `TRADER_*` + 29 `BANKER_*` `Messages` constants | `gangland-api` `Messages` enum | **deferred to WS6** (S4/D5) — not touched this wave | a new npc-shops message-YAML loader + Spanish parity pass is a separate, later job |
| `ShopMessageContract` impl `GanglandShopMessages` | `gangland-impl` | **unchanged location**, repoints its `keystone.shop.message.ShopMessageContract` import | pure adapter |
| `ShopUiSettings`/`GanglandShopUiSettings`/`TraderSettings` | `gangland-api`/`gangland-impl`/npc-shops | **unchanged** (B5/S2) | preserves `ShopConfig.java:33-37`'s existing zero-modules workaround — not a smell, don't "fix" it |
| `gangland.shop.admin` permission | `TraderModuleConfig.registerPermissions()` | **unchanged** | |
| `commands.json` entries for `/glw shop *` | `gangland-impl/src/main/resources/commands.json` | **unchanged** | |
| `message_es.yml` | has existing gaps for Banker/Bank keys (US-35, CM-15) | **not touched** — deferring the message migration (S4) sidesteps this risk entirely this wave | |

---

## 6. Persistence

Unchanged from the original plan — **independently confirmed clean by review** ("persistence is clean" negative
result): `ShopRegistry` extends Keystone's own `FolderLoader`, folder `"shop"`, schema `Title`/`Size`/`Buy_Entries`/
`Sell_Entries`/`Sell_Categories`/`Barter_Categories` unchanged, same `JavaPlugin` instance (`Gangland`) passed in.
Existing shops on disk at `plugins/Gangland_Warfare/shop/*.yml` need **no migration** — package/class rename only.

`TraderData`/`BankerData` (npc-shops' own DB-backed entities) have no repository in shop-api itself — C7 wiring is
**entirely untouched** by WS4. Signs are also independently confirmed clean by review: `BaseTradeSign` and the
`item-buy`/`item-sell` types name no `gangland.shop` type.

---

## 7. Tests

| Test | Today | After |
|---|---|---|
| `ShopDefinitionTest`, `ShopYamlReaderTest`, `ShopBarterServiceTest` (+ others calling `BukkitRegistryFixture`) | `shop-api/src/test/...` | `keystone-shop`, package-rename **plus** the fixture repoint from G0 step 2 (C6) — this is no longer a bare rename |
| `ShopYamlReaderTest` | same | `keystone-shop` — carries the CT-06 pinning test forward (§11) |
| `ShopPurchaseServiceTest` | same | `keystone-shop` — carries the CT-07 pinning test forward (§11, docket "(pins #7; flip when fixed)") |
| `ShopSellServiceTest`, `CategorySellValuatorTest`, `CategoryBarterValuatorTest`, `FakePaymentHandler` | same | `keystone-shop`, rename only |
| `DefaultShopDisplayResolverTest` | does not exist | NEW in `keystone-shop` — first coverage ever for this class |
| Second-consumer proof | none | **Optional** (S3) — if kept, one method in `keystone-plugin/src/test/.../scenario/ShopPipelineScenarioTest.java` (matching the `ItemPipelineScenarioTest.java` precedent); do not grow it into a second consumer harness |

The listener-scan integration test from the earlier draft is **deleted** (S1 — there is no scan gap to test; the
handler never leaves `gangland-impl`). Docket-pinned tests (CT-06, CT-07) stay red exactly as today; this wave does
not flip them.

---

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **(NEW, replaces the deleted listener-scan risk)** B2's `provided`-scope non-transitivity is missed for one pom, and `gangland-build`'s `org.luckyraven:*` wildcard silently shades Keystone code into the Gangland jar | G1a gate's mandatory audit line (step 12, C9): `unzip -l target/gangland_warfare-0.10.0.jar \| grep keystone/shop` must be empty |
| 2 | G1b (Oriel rewrite) cannot start until `MenuFlow.rerender()` lands (B4) — the largest single rewrite risk in the wave | Named per-site fallback (§4 step 13) means G1b is degraded, not fully blocked, if the ask is declined; either way it's isolated to G1b, G1a already shipped |
| 3 | `CLAUDE.md:101` amendment (§9 D1) is a two-plan collision — WS3 (hologram) needs the identical rewording | Coordinate in one PR/commit shared by whichever of WS3/WS4 lands first (§10) |
| 4 | `ShopAdminOpener` (B1) is a genuinely new interface — if its method signature doesn't match what `ShopViewOpenerImpl` actually needs to call, npc-shops' build breaks | Executor reads `ShopViewOpenerImpl.java:23,42,55` and `ShopAdminFlow`'s real public surface before finalizing the interface signature — `openAdmin(Player, ShopDefinition)` is the orchestrator's stated shape, not independently verified against the call sites (§13) |
| 5 | Step 2 (testkit fixture port, C6) stalls G0 if `BukkitStatics` doesn't already cover what `BukkitRegistryFixture` does | Sized M with its own acceptance check (the 4 dependent tests), sequenced explicitly before step 3 so it fails fast in isolation |

**Rollback story:** G0 is isolated until G1a depends on it. G1a is a single revertible unit, shippable and
smokeable on its own (B3) — reverting it loses nothing that shipped. G1b reverts independently of G1a. This is a
**better** rollback story than the collapsed single-gate version the earlier draft proposed.

---

## 9. Decisions for the user

**D1 — Where does the shop system live?** Unchanged recommendation (**A. headless `keystone-shop`**), but the
`CLAUDE.md:101` quote is now complete (C5): *"Keystone holds only low-level, generic infrastructure... product
concepts riding on top of it — wanted levels, civilian/police marks, **trader shops**, what fires a ranged
attack — stay in the consumer."* The line already names "trader shops" as the thing that does **not** belong in
Keystone. The amendment is therefore a **reversal of an explicit prior ruling**, not a clarification of an
ambiguous one — the user should approve it knowing that. The underlying argument still holds (the 28→26 files name
no gang/trader/weapon; they are exactly as generic as `keystone-npc` already is), and Oriel's own disclaimer
(`USE-CASE-ENABLERS.md:235-246`) still rules out C. Recommendation unchanged: **A**, amendment text unchanged from
the original draft, appended after the existing sentence.

**D2 — Ship a default `ShopDisplayResolver`?** Unchanged — **yes**, ship `DefaultShopDisplayResolver`. Review
agrees.

**D3 — Gate ordering.** **RULED (B3): split restored.** The earlier recommendation to collapse G1+G2 into one
Gangland gate rested on a reading of WS2's plan that the review found stale — the *revised* WS2 plan explicitly
adds Oriel to shop-api's pom additively, without requiring inventory-api's deletion first, specifically "so WS4 can
start porting immediately," and renames its own cutover step to run *after* WS4's consumer gate (ruling R3). The
two-step G1a/G1b split in §4 is not a fallback for a rejected studio preference — it is what the corrected
contracts actually allow, and it is a strictly better rollback story than one collapsed gate (§8).

**D4 — Where do the rebuilt core admin views live, and how does npc-shops reach them?** **RULED (B1).** The views
stay in `gangland-impl` (§0's "`/glw shop` is core" finding was right), but a module may never name `gangland-impl`
types — `TraderModuleConfig`/`ShopViewOpenerImpl` do today, which would have broken the npc-shops build outright.
Resolution: a new one-method `ShopAdminOpener` in `gangland-api`, core-implemented and bean-registered, npc-shops
depends on the interface only. One interface, one implementation — but it *is* the module boundary the compiler
enforces, so it clears the ponytail bar the same way `WaypointLookupContract` does for cops.

**D5 — Bundle the Trader/Banker settings + messages migration, or defer?** **RULED (S4): split.** Bundle the 10
settings keys (contained blast radius, `TraderModuleConfig` call sites already being touched by step 10). **Defer**
the 41 message constants — they need a new message-YAML loader npc-shops doesn't have today plus a Spanish-parity
pass, a separate failure mode the shop move doesn't require taking on. Revisit with WS6's general per-module
migration.

**D6 — `ShopUiSettings`/`Max_Mode_Multiplier` destination.** **RULED (B5/S2), previously not surfaced as its own
decision.** Both stay Gangland-side. Moving `ShopUiSettings` to Keystone would have shipped a headless module class
with zero users inside that module (the product-concept leak D1 exists to avoid), and moving `Max_Mode_Multiplier`
into npc-shops YAML would have deleted the core admin editor's only settings source in the zero-modules case.

---

## 10. WS6 asks / Oriel asks / Keystone asks

**WS6 asks:**
- `gangland-api` 2.0's re-export list drops `shop-api`; `keystone-shop` moves to the **provided** block, not a
  compile-scope re-export (B2) — different mechanism than originally stated, WS6 should not copy the old pattern.
- **New (B1):** should `ShopAdminOpener` (and the pattern it establishes — a core-owned view surface reached by
  modules through a one-method interface) become a documented convention on the `GanglandApi`/WS6 facade for other
  workstreams with the same "core owns the UI, a module needs to trigger it" shape? Flagging for WS6 to decide, not
  deciding it here.
- Should the facade expose `ShopRegistry` for external (non-module) plugins? **Recommend no** — nobody has asked
  for cross-plugin shop lookup.

**Oriel asks (shared with WS2, in addition to WS2's own list):**
- **`MenuFlow.rerender()` (B4) — blocking, top priority.** No panel-in-place re-render exists in Oriel today; the
  9 relocated files call the GLW equivalent 8 times. This is WS2's own top-priority ask too (`plans/WS2-inventory-
  oriel.md` §10) — one Oriel change unblocks both workstreams' largest rewrite risk.
- A grid-editable price-entry component equivalent to `PriceEditorView`'s mirrored +/- multiplier rows. Not
  confirmed whether Oriel's `AnvilMenu`+`ButtonComponent` composition already covers this (§13).

**Keystone asks:**
- Land the `CLAUDE.md:101` amendment (D1) in the same PR as whichever of WS3 (hologram) or WS4 (shop) merges
  first — avoid two competing edits to the same line.

---

## 11. Docket

Sourced from `brainstorming/bug-docket-2026-09-06/bugs.json` (498 entries; `CT-*`, system `civilians-traders-shops`).
Review independently checked every id against the docket and confirms titles/tiers/file classification below.

| Id | Tier | Title | File (today) | Disposition |
|---|---|---|---|---|
| CT-04 | P1 | ESC on `/glw shop edit` rewrites the shop file and strips comments | `shop/view/ShopAdminFlow.java:49` + `shop/io/ShopYamlWriter.java:53-57` | **Carry forward, two-stage.** `ShopAdminFlow` relocates unchanged in G1a, rewritten on Oriel in G1b; `ShopYamlWriter` moves to `keystone-shop` unchanged in G0. Fix direction (dirty-flag before firing `ShopEditedEvent`) applies wherever `ShopAdminFlow` ends up; not fixed as part of this wave. |
| CT-05 | P2 | Save renumbers every buy entry `Slot` | `shop/view/ShopAdminFlowSession.java:76-83` | **Carry forward, two-stage** — same relocation path as CT-04. |
| CT-06 | P3 | `Slot` never validated against `Size` (test-pinned) | `shop/io/ShopYamlReader.java:279-282` | **Carry forward, moves to `keystone-shop` in G0 unchanged.** Pinning test moves too, still red. |
| CT-07 | P2 | Purchase overflow drops item, `INVENTORY_FULL` never returned (test-pinned "#7") | `shop/transaction/ShopPurchaseService.java:42-55` | **Carry forward, moves to `keystone-shop` in G0 unchanged.** Becomes shared-infrastructure once a second consumer exists — worth re-triaging, not done here. |
| CT-08, CT-16, CT-17, CT-18, CT-20 | P2-P3 | Various trader-view bugs (`CT-18` confirmed at `QuantitySelectorView.java:46`) | `npc/trader/view/*.java`, `npc/trader/TraderManager.java` | **Not WS4's files** — npc-shops-owned, carried forward by WS2's npc-shops view rebuild. |
| CT-22 | P3 | Price edit may mutate shared `ItemStack` | `shop/view/SellCategoryItemsAdminView.java:245-247` | **Carry forward, two-stage** — same relocation path as CT-04/05. |
| CT-23 | P3 | Concurrent admin saves are last-writer-wins | `shop/ShopRegistry.java:83-92` | **Carry forward, moves to `keystone-shop` in G0 unchanged.** Recommend re-triaging P3→P2 once shared infrastructure (suggestion, not decided here). |
| US-28 | P3 | `setUser` on the bank economy handler has no effect | `GanglandBankerEconomy.java:228`, `BankCreateCommand.java:86` | **Untouched by WS4** — uses `EconomyHandler`/Vault directly, not `PaymentHandler`. |
| LS-19 | P0 (fixed 0.8.3 wave 3, `8a731fd5`) | Shop sign permission | `sign/listener/SignCreation.java` | **Unrelated** — sign framework. No action. |

**New findings not filed:** `shop-api`'s dead `gangland-core`/`gangland-item` pom entries — build hygiene, not a
user-visible defect, fixed as a side effect of step 7's deletion instead of filed to `triage/`.

---

## 12. Estimate

Re-estimated per review (the original 4.5-5.5 days was optimistic by roughly a third — it under-sized the Oriel
rewrite, hid the testkit-fixture dependency inside a bare "rename," and omitted the hygiene steps entirely).

| Gate | Steps | S | M | L |
|---|---|---|---|---|
| G0 (Keystone) | 6 | 4 | 2 | 0 |
| G1a (Gangland, headless) | 6 | 2 | 4 | 0 |
| G1b (Gangland, Oriel) | 3 | 2 | 0 | 1 |
| Hygiene | 3 | 3 | 0 | 0 |
| **Total** | **18** | **11** | **6** | **1** |

Wall-clock: G0 ≈ 1.5 days (mechanical file moves + docs + release build, but step 2's fixture port is a genuine
unknown until scoped). G1a ≈ 1.5-2 days (mostly pom/import mechanics, but three-pom B2 fix and the new
`ShopAdminOpener` seam are real, reviewable changes, not pure rename). G1b ≈ 3-4 days, entirely dominated by step
13 — 9 files, 8 `rerender()` sites, no existing tests to pin against, and it cannot start before the Oriel ask
lands. Hygiene ≈ 0.5 day. **Total ≈ 6.5-8 days elapsed**, matching the review's "16 steps, 6-8 days" re-estimate
(this plan counts 18 due to the 3 explicit hygiene steps the review asked to make visible rather than implicit).

---

## 13. Not verified

- `ShopAdminOpener`'s exact method signature (`openAdmin(Player, ShopDefinition)`) is the orchestrator's stated
  shape, not independently checked against `ShopViewOpenerImpl.java:23,42,55`'s real call sites or `ShopAdminFlow`'s
  actual public API — executor must read both before finalizing the interface (§8 risk 4).
- Whether `keystone-testkit`'s `BukkitStatics` already covers what `BukkitRegistryFixture.install()` does, or a
  genuine port is needed — G0 step 2 is sized M on the assumption of real work; could be S if it already exists.
- Whether Oriel's `AnvilMenu`/`ButtonComponent` composition can reproduce `PriceEditorView`'s mirrored +/- ladder —
  not read on either side (Oriel's component source, or `PriceEditorView.java`'s actual UI logic).
- The exact citation `Settings.java:196-207` for "11 static getters deleted" (§5, C3) is taken from the review as
  given, not independently re-read by this planner.
- Whether any core file beyond `ShopCommand`'s six subcommands, `ShopConfig.java`, and the two npc-shops classes
  named in B1 reaches into shop-api/keystone-shop types — checked via one `grep` and one `graphify affected` call
  (§0, review's independent re-run), not exhaustively cross-verified file by file.
- `TraderEconomyContract`/`BankerEconomyContract` (npc-shops' own economy contracts) — located but not read in
  full; whether the tip/claim/upgrade paths have any shop-api coupling beyond the buy/sell `PaymentHandler` path
  was not checked, since those flows are npc-shops-native and out of WS4's scope either way.

## §0d Execution corrections (2026-09-16, orchestrator)

- C6 ("port `BukkitRegistryFixture` into `keystone-testkit`") is **impossible at Keystone's 1.16.5 API floor**: the
  fixture exists to defeat 1.21-era `Registry.BLOCK` resolution (`Server.getRegistry(Class)`, `Registry<T>`,
  `BlockType`), none of which the 1.16.5 API has. Keystone 1.10.0 ships a smaller `BukkitServerFixture`
  (`Bukkit.setServer` + `ItemFactory` only, fork-lifetime), wired into the one test that needs a live server
  (`ShopBarterServiceTest`); the other promoted tests run fixture-free. Documented in Keystone
  `docs/phase-h10-hologram-shop.md`. Any future Keystone test needing registry-backed `Material` lookups brings
  its own fixture.
- CT-06/CT-07/CT-23 pinning tests now live under `keystone-shop/src/test/java/org/luckyraven/keystone/shop/...`;
  their docket `note` rows record the new paths at the wave's docket step.
