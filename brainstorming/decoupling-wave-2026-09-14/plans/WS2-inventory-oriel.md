> **SUPERSEDED 2026-09-17.** WS2 was re-targeted (ruling R10): `inventory-api` moves onto a new Keystone module
> `keystone-inventory` (Phase E6, 1.11.0) instead of onto Oriel — Gangland gains no Oriel dependency at all.
> See `../plans/WS2-inventory-keystone.md` for the current plan; this file is kept only for its superseded history.

# WS2 Plan — inventory-api is replaced by Oriel

> User's words: *"The inventory-api needs to be basically linked to oriel, thus using oriel features rather than
> being independent."*

Sources: `census/WS2-inventory-oriel.md`, `census/WS4-shop.md` §1-2, `census/WS3-lootchest-hologram.md` §1/§3,
`Oriel/docs/MIGRATION-FROM-GLW.md` (394 lines), `Oriel/docs/USE-CASE-ENABLERS.md` (255 lines),
`brainstorming/module-api-2026-09-10/README.md`, `brainstorming/bartizan-split-2026-09-08/architecture/PICK.md`,
`graphify explain` in both repos, direct reads of poms/plugin.yml/settings.yml/build.gradle.kts, and
**`reviews/REVIEW-WS2.md`** (Opus, verdict REWORK — 5 blockers, 9 corrections, 4 simplifications, incorporated
below) plus the orchestrator's rulings on the review's open items.

---

## 0b. Review response

| ID | Finding | Resolution | Where |
|---|---|---|---|
| B1 | G6 deleted inventory-api while shop-api's 9 files still imported it — self-contradictory gate | **Orchestrator ruling**: renamed "G6 cutover (after WS4 consumer gate)", moved out of WS2's own G0-G5 sequence, gated on a reactor-wide grep instead of an assumed order | §4 (new closing section) |
| B2 | "Zero tests touch inventory-api" is false — 3 domain tests break at G2 | Added as an edit-in-the-same-commit step in G2 (not "new coverage"); §7 reclassified | §4 G2 step 10, §7 |
| B3 | `MenuFlow` has no `rerender()` — 20 call sites, plan never mentioned it | Promoted to the top Oriel ask (~10 lines); per-view consumer fallback stated; promoted to risk #1 | §3, §4 G4, §8, §10 |
| B4 | `OpenMenuTracker` is not on `ServicesManager` — "not replaced" claim had no substitute | Oriel ask added (publish it / expose via `MenuOpener`); `/glw debug inv-data` deleted, not "replaced", until the ask lands | §3, §4 G2/G3, §5, §10 |
| B5 | `Enchanted: true` IS used (4 slots, 2 files) — "deferred, doesn't gate WS2" was wrong | New Decision D5: code-register those 4 items via `ItemProviderRegistry` instead of blocking on Oriel's v0.2 NBT roadmap | §1, §4 G3, §9 D5 |
| C1 | Risk "Panel generics unconfirmed" was wrong — Oriel's `Panel` **is** generic | Risk deleted, §13 item deleted; remaining deltas reclassified as B3 (rerender) + C2 (back semantics) | §8, §13 |
| C2 | `back()` on an empty stack silently no-ops in Oriel (GLW calls `end()`) — root-panel back buttons go dead | Oriel ask + consumer-side `boolean root` fallback added | §3, §4 G4, §10 |
| C3 | `BarterView`/`SellView` are deposit-slot **redesigns** with item-loss exposure, and they're WS2's, not WS4's | Own G4 step, `DepositSlotComponent` design, explicit survival acceptance check + test; A3 ask re-filed WS4→WS2/G4 | §4 G4 step 19, §7, §8, §10 |
| C4 | Placeholder claim wrong for 2 token families (`%money_symbol%`, row-scoped `%member_*%`/`%ally_*%`) | §3 rewritten into 4 token families + the adapter seam Gangland must write | §3, §5 |
| C5 | `MenuConfigService` takes 17 constructor args, 4 unpublished | G1 step 6 resized [M]→[L]; top-priority Oriel ask (`forConsumer` factory) added | §4 G1, §9 D2, §10 |
| C6 | impl/npc-shops file lists partly invented, partly incomplete | File lists corrected throughout G3/G4 (see per-step citations) | §4 G3, G4 |
| C7 | Unique-item YAML source (`Information.Event.UniqueItem`) has no home once its parser is deleted | New replacement step: small `unique_items.yml` side-file | §4 G3 step 12, §5 |
| C8 | Oriel is `0.7.0-SNAPSHOT`, not `0.7.0` | `<oriel.version>` property noted, SNAPSHOT-vs-release open item flagged | §2, §13 |
| C9 | Line-number drift; missing `User.clearInventories()`; `commands.json` has no inventory entry at all | Fixed throughout | §2, §5 |
| S1 | Target Keystone 1.9.2, not 1.10.0, for Oriel G0 | Applied (matches orchestrator ruling); H5-deprecation carried as an FYI note | §4 G0, §9 D4 |
| S2 | No YAML converter script | Already true, unchanged | §4 G3 |
| S3 | `.aroundSlot()` ask correct (consumer-side); extend the logic to `fillInventory`'s preserve/restore dance | `ChestMenu.applyFillIfEmpty` already covers it — booked as a G4 deletion, not a port | §4 G4 step 20 |
| S4 | Don't bake 3 base64 skulls into all 9 YAML files | One shared `HeadMaterialSources` `basehead:`/`texture:` default | §4 G3 step 11, §5 |
| R2 (orchestrator) | `LootChestService`/`LootChestSession` are WS3's (raw `Inventory` wrapper) | Removed from WS2's port list entirely; "wand-preview admin screen" kept as a light forward note, not a build item | §1, §4 (old G6 step 24 deleted), §10 |
| Item 9 (orchestrator) | Anvil-driven paginated search deferred by `MIGRATION-FROM-GLW.md:394` | Folded into G0 step 2's spike + a scoped Oriel ask + manual-anvil-button fallback | §4 G0, §9 D3, §10 |
| Estimate-check misses | `gangland-api/pom.xml:42` re-export never retired; `graphify update --force`; memory note; docket record; `Settings` getters for `Inventory:` orphaned | All added as explicit steps | §4 G5/G6 cutover |

---

## 1. Scope

**In:**
- Add Oriel (`org.luckyraven.oriel:*`, `provided` scope) to the 7 poms that need it directly: `gangland-api`,
  `gangland-impl`, `gangland-ui/shop-api`, `gangland-features/gangland-npc-shops`, `gangland-features/gangland-turf`,
  `gangland-features/cops-n-crooks`, `gangland-features/gangland-gadget`. (`lootchest-api` dropped — R2.)
- Sever `gangland-infra/gangland-domain`'s dependency on inventory-api (pom.xml:**40-43**, corrected) — the one
  dependency inversion in the wave.
- Port the 9 core YAML menus per `MIGRATION-FROM-GLW.md`, including the 4 `Enchanted: true` slots (B5, Decision D5).
- Migrate every WS2-scoped consumer file onto Oriel primitives (`ChestMenu`/`ChestMenuBuilder`, `MenuFlow`/`Panel`/
  `FlowState`, `PaginatedListComponent`, `TraderMenu`, `AnvilMenu`, `FilterRegistry`/`SearchMatcherRegistry`/
  `SortRegistry`, `DepositSlotComponent`) — the exact file lists in §4 supersede any rolled-up count from the
  census, which C6 showed does not survive a grep.
- Add `Oriel` to `plugin.yml`'s `depend:` (plugin.yml:**7-9**, corrected — hardness decided §9 D1).
- Retire `.claude/skills/panel-create/` and rewrite `documentation/developer/ui-framework.md` — **at the shared G6
  cutover gate**, not inside WS2's own sequence (B1).
- Remove `net.wesjd:anvilgui` from `gangland-build/pom.xml` — also at the G6 cutover gate, once inventory-api is
  actually deleted.

**Out (belongs to other workstreams):**
- **`LootChestService.java`/`LootChestSession.java`** (lootchest-api) — **entirely WS3's scope** (orchestrator
  ruling R2). They wrap the loot chest's own raw Bukkit `Inventory`; WS2 makes zero changes to lootchest-api. The
  only loot-chest-adjacent thing WS2 still touches is the wand's `UniqueItemHandler` config source (G3 steps 12-13),
  because that lives in gangland-impl, not lootchest-api.
- shop-api's 9 `MultiPanelInventory`-based files — WS4's job. Per the orchestrator's ruling on B1, WS2 does **not**
  absorb these into its own sequence (the rejected alternative); WS2 only adds Oriel as an additional pom
  dependency to shop-api (additive, doesn't require deleting inventory-api) so WS4 can start porting immediately.
- The gang module extraction (WS5) and the api facade (WS6) — untouched, except that severing `User`/`UserFactory`
  from inventory-api is a prerequisite WS5 benefits from.
- Oriel engine features from `USE-CASE-ENABLERS.md` A1/A2/A4-A7 — planned in Oriel's own backlog, not required for
  a straight port. (A3 is the one exception — re-filed into WS2/G4, see C3.)

**No longer deferred — corrected (B5):** `Enchanted: true` **is** used by the core menus: `gang_info.yml:19,46,59`
and `phone_banking.yml:31` (4 slots, 2 files). The original "grep returned nothing" claim did not reproduce.
Handled via Decision D5 (§9): those 4 slots move to a code-registered single-item source instead of a static YAML
`item:` key, sidestepping Oriel's v0.2 declarative-NBT gap (`MIGRATION-FROM-GLW.md:391`) entirely — this is **not**
a G0 blocker.

---

## 2. Target layout

### Dependency table (corrected — `lootchest-api` removed per R2; `<oriel.version>` added per C8)

| Consumer pom | Oriel artifacts added | Why |
|---|---|---|
| `gangland-api/pom.xml` | `oriel-core`, `oriel-chest` | re-exports Menu/ChestMenu types the way it re-exported inventory-api |
| `gangland-impl/pom.xml` | `oriel-core`, `oriel-chest`, `oriel-anvil`, `oriel-trader`, `oriel-config` | core menus, `/glw debug villager` → `TraderMenu`, YAML loading |
| `gangland-ui/shop-api/pom.xml` | `oriel-core`, `oriel-chest` | replaces its `inventory-api` provided dep — WS4 executes the view rewrite |
| `gangland-features/gangland-npc-shops/pom.xml` | `oriel-core`, `oriel-chest`, `oriel-anvil` | trader/banker flows |
| `gangland-features/gangland-turf/pom.xml` | `oriel-core`, `oriel-chest` | powerup flow |
| `gangland-features/cops-n-crooks/pom.xml` | `oriel-core`, `oriel-chest` | paperwork/bribe views |
| `gangland-features/gangland-gadget/pom.xml` | `oriel-core`, `oriel-chest` | car sign preview list |

`gangland-ui/lootchest-api` is **not** in this table (R2 — WS3 adds its own Oriel dependency when it converts the
module). Every pom above needs a root-level `<oriel.version>0.7.0-SNAPSHOT</oriel.version>` property (C8 — Oriel's
`gradle.properties:2` is a SNAPSHOT today; confirm at G0 whether the 0.8.0 bump ships as a real release or another
SNAPSHOT out of `publishToMavenLocal`, §13).

No module gets `oriel-database` (contract C7 keeps Gangland's own repositories) or `oriel-papi`/`oriel-vault`
(Gangland's own PAPI expansion is unaffected; Oriel's PAPI adapter lives inside Oriel's own process — see §3).

### `plugin.yml` (plugin.yml:**7-9**, corrected line numbers)
`depend: [Keystone, NBTAPI]`, `softdepend: [PlaceholderAPI, Vault, ViaVersion, Citizens, Bartizan]` → add `Oriel`
(hardness §9 D1).

### `net.wesjd:anvilgui`
Unchanged finding: Oriel's own `menu-plugin` shades+relocates anvilgui; `menu-inventories/anvil` only depends on it
`compileOnly`; inventory-api was the only Gangland pom with the compile-scope dependency
(`gangland-ui/inventory-api/pom.xml:50-54`). Removal of `net.wesjd:anvilgui` from `gangland-build/pom.xml` (lines
60, 75) moves to the **G6 cutover gate** (§4), since it can only happen once inventory-api is actually deleted.

### Deletions (corrected — C9 adds `clearInventories()`; R2 removes lootchest-api; timing split per B1)
**At WS2's own gates (G2-G5):** `file/configuration/inventory/{InventoryDefinitionStore,InventoryLoader,
InventoryParser,InventoryRuntimeContext}.java` (with C7's replacement for the unique-item parser),
`gangland-infra/gangland-domain/.../gang/GangFilterAdapter.java` and `.../gang/member/MemberFilterAdapter.java`
(moved, not deleted), `User.java`'s `Set<InventoryHandler> inventories`, `InventoryRegistry inventoryRegistry`
fields and `addInventory/removeInventory(InventoryHandler)/removeInventory(String)/getInventory/getInventories/
clearInventories()` methods (`User.java`:46,48,60,145-211, **and 197-204 for `clearInventories()`**, C9) — not
replaced until the OpenMenuTracker ask (B4) lands.

**At the shared G6 cutover gate (after WS4/WS3), not WS2's own sequence:** `gangland-ui/inventory-api/` itself
(pom + 58 files), `net.wesjd:anvilgui` from `gangland-build`, `.claude/skills/panel-create/`, the `settings.yml`
`Inventory:` block (lines 150-163) **and its matching `Settings` getters in the same commit**.

---

## 3. Seams

| Cross-boundary call | Mechanism | Publisher | Puller | Default when absent |
|---|---|---|---|---|
| Gangland → Oriel menu registry | `ServicesManager.getRegistration(MenuRegistry.class)`, lazy, never cached | Oriel plugin | every `@Configuration` class that opens/registers a menu | Oriel is a **hard** `depend:` (§9 D1) |
| Gangland's own menu YAML | Oriel's own `MenuConfigService` — **corrected (C5)**: its constructor takes **17 arguments** (`MenuConfigService.java`:69-76: plugin, `MenuRegistry`, `FileManager`, `PlaceholderProvider`, `ActionRegistry`, `SourceProviderRegistry`, `FilterRegistry`, `SortRegistry`, `SearchMatcherRegistry`, `ItemProviderRegistry`, `MaterialSourceRegistry`, `PaginatedControlRegistry`, `ComponentRegistry`, `MenuCommandRegistrar`, `RequirementRegistry`, `CooldownService`, `BooleanSupplier`). **4 of the 17 are not published** on `ServicesManager` — `PaginatedControlRegistry` (internal package), `RequirementRegistry` (`config/requirement/RequirementRegistry.java:21`, interface only), `CooldownService` (Keystone's, resolved separately), `MenuCommandRegistrar` (constructible, `config/command/MenuCommandRegistrar.java:44`) | gangland-impl (`OrielMenuConfig` bean) | Oriel ask (§10, top priority): a `MenuConfigService.forConsumer(plugin, fileManager, placeholders)` factory |
| Filter/search/sort in paginated menus | `ChestMenuLoader`'s primary constructor takes `FilterRegistry, SortRegistry, SearchMatcherRegistry, SourceProviderRegistry, PaginatedControlRegistry` (`config/loader/ChestMenuLoader.java`:112-131, three fallback overloads at :136,145,156); `MenuConfigService`:96-97 wires all of them | Oriel plugin | `GangFilterRegistration` (rewritten, §4 G2) | **Basic filtering is shipped and wired** — `MIGRATION-FROM-GLW.md:392`'s "deferred to v0.2+" line is stale. **But line 394 also defers "anvil-driven search on paginated regions"** — exactly what `phone_gang_search.yml:35-76`/`user_stat.yml:26-46` need. G0 step 2 must prove the anvil→`set_search` path (`config/loader/PaginatedControlActions.java`), not just that a filter filters (D3 caveat, item 9) |
| Per-player open-menu tracking | **Corrected (B4)**: `OpenMenuTracker` is an **internal** bean (`MenuTrackingConfig.java:30`) — `OrielPlugin.registerServices()` (`menu-plugin/.../OrielPlugin.java:178-197`) publishes `MenuRegistry, MenuOpener, ActionRegistry, SourceProviderRegistry, FilterRegistry, SortRegistry, SearchMatcherRegistry, ItemProviderRegistry, MaterialSourceRegistry, ComponentRegistry, DatabaseBackendRegistry, PacketAdapter` — **`OpenMenuTracker` is not in that list** | Oriel plugin (internal only) | nobody, today | **This is not a working replacement for `User.inventories`/`InventoryRegistry` until the Oriel ask lands.** `DebugCommand`'s `inv-data` argument (`DebugCommand.java`:458-480), the plan's only stated consumer, loses its data source outright — deleted at G3 step 17, not migrated (§5) |
| Panel re-render on state change | **New row (B3)**: GLW's `MultiPanelInventory.rerender()` (`flow/MultiPanelInventory.java:116-123`) is `current.clear(); panel.render(...)` — re-runs the panel against the **already-open** inventory. Oriel's `MenuFlow` (`chest/flow/MenuFlow.java`:95-161) exposes only `openAt/switchTo/back/suspend/resume/end/currentMenu/currentPanelId/state/viewer/suppressClose` — **no `rerender()`**. Two lossy substitutes: `switchTo(sameId)` re-runs `render` but builds a **new** `ChestMenu` and calls `.open(viewer)` (`MenuFlow.java`:165-190 — real Bukkit reopen, flicker, cursor/drag reset); `ChestMenu.rerender()` (`ChestMenu.java`:432-435 → `renderAll`) re-renders the existing component set but **never re-invokes `Panel.render`**, so a panel adding/removing a button on state change silently goes stale | — | 20 call sites: `BankerAmountView:273,281,337`, `BankerClaimView:188`, `BankerUpgradeView:163`, `NegotiationView:157`, `QuantitySelectorView:231,241`, `ShopView:115,130`, `TurfPowerupBuffCatalogueView:100`, `TurfPowerupGarrisonView:108`, plus 7 in shop-api | Top-priority Oriel ask (§10): `MenuFlow.rerender()`, ~10 lines beside `switchInternal`. Fallback: per-view choice between the two lossy substitutes, decided at G4/G5 execution time — **not a single blanket answer**, neither this rework nor the review read every view's render body |
| `back()` on an empty stack | **New row (C2)**: GLW empty back-stack → `end()` (`MultiPanelInventory.java`:104-112). Oriel empty back-stack → **silent no-op** (`MenuFlow.java`:128-133), and `MenuFlow` exposes no `hasBack()`/stack accessor | — | every root-panel "back" button, e.g. `BankerAmountView.java`:149-152,255, `BarterView.java`:352 | Becomes a dead button. Oriel ask: one-line fix (`if (previous == null) { end(); return; }`) or a `hasBack()` accessor. Fallback: consumer-side `boolean root` flag rendering "close" instead of "back" on root panels. Improvement noted: Oriel's `switchTo(same id)` does not push the back-stack, GLW's always did |
| Gang/member menu data → paginated items | `registry.registerSource(name, Function<Player,List<ItemStack>>)` | `GangItemSourceProvider` (rewritten) | Oriel's `PaginatedListComponent`/`ChestMenuLoader` | source not registered → empty page |
| `%gangland_*%` placeholders | **Corrected (C4) — four separate token families, not one mechanism:** (a) true PAPI tokens (`%gangland_gang_name%`, `%gangland_user_has-gang%`, …) resolve via Oriel's `PlaceholderProvider`→PAPI chain, unchanged; (b) **`%money_symbol%`** (11 uses across the 9 menus) is **not** PAPI — registered directly on Gangland's own `PlaceholderService` (`config/KernelConfig.java`:105, `service.register((player, text) -> text.replace("%money_symbol%", Settings.getMoneySymbol()))`); (c) **`%member_name\|rank\|contribution\|join_date\|online_status%`** / **`%ally_id\|name\|online\|total\|created%`** are **row-scoped**, built per list entry by `GangItemSourceProvider.java`:74-104 as `ItemSourceEntry(Map<String,String>)` — no PAPI expansion can resolve these, it doesn't know which row a slot shows; (d) **`%gangland_anvil_output%`** (7 uses) isn't a placeholder at all — it maps to `ChestMenu.bindArguments(ArgumentBinding)`/`AnvilClickContext` | Gangland | Oriel exposes `ChestMenuBuilder.placeholders(...)` (`ChestMenuBuilder.java`:158) and a `placeholders` parameter on `MenuConfigService` — **the seam exists; Gangland must write a `PlaceholderProvider` adapter** wrapping (b) and threading (c)'s per-entry map through the paginated-source registration. (d) needs no placeholder work at all. **No new Oriel ask** for this row |
| Sign → menu open | `ViewInventoryAspect` → `MenuOpener` | Oriel | sign-api's existing seam, unchanged | n/a |
| `CarSignViewProvider` (gadget) | Existing `SignViewProvider` seam, unchanged body swap | gadget module | core `SignManager` | n/a |
| Villager trade UI | Oriel `TraderMenu`/`TradeOffer` — near line-for-line match of `VillagerInventory`/`VillagerTrade`. **Clarified**: `KernelConfig.java`:25-27's `villagerInventoryRegistry()` bean is a **live, unconditional KERNEL-phase registration** (not itself debug-gated), even though its only current call sites (`DebugCommand.java`, `VillagerDebugPanel.java`) are debug tooling | Oriel | `/glw debug villager` only | n/a |

---

## 4. Steps

Gates run in the order fixed by contract C1. Each gate ends with a green `mvn clean install`/`./gradlew build` and
a named smoke row (§7).

### G0 — Oriel prerequisites (Oriel repo, branch `0.8.0` off `0.7.0-SNAPSHOT`)
1. **[S]** Bump `keystone.version` from `1.7.0` to **`1.9.2`** (S1/D4 — **not** 1.10.0; Oriel names 92 distinct
   `org.luckyraven.keystone.*` types, all resolving against Keystone's current tree per the review except two edge
   cases — `PacketAdapter.FakeWindowType`, `Severity.ERROR`). `./gradlew build` is the acceptance check. **Carry
   note:** Oriel uses `CommandTabCompleter`/`BrigadierTabRegistrar` statics Keystone's H5 wave marked
   `@Deprecated(forRemoval)` (`docs/phase-h5-upstream-wave.md:78-83`) — compile-clean at 1.9.2, must migrate before
   Keystone 2.0.0; not a WS2 blocker. A **later, separate small gate** bumps Oriel to 1.10.0 once WS3/WS4's
   Keystone modules exist (orchestrator ruling) — out of WS2's own scope.
2. **[M]** Spike (upsized from S — two things to prove, not one): (a) confirm the `paginated:` YAML wiring for
   `FilterRegistry`/`SortRegistry`/`SearchMatcherRegistry` — already confirmed shipped and wired (§3 row 3;
   `MIGRATION-FROM-GLW.md:392` is stale); (b) confirm the **anvil-driven-search** path (`MIGRATION-FROM-GLW.md:394`
   — genuinely still deferred) via `config/loader/PaginatedControlActions.java`'s `set_search` action, since
   `phone_gang_search.yml`/`user_stat.yml` need exactly this UX. If (b) doesn't prove out, fall back to the manual
   anvil-button "Search Gang" pattern (`MIGRATION-FROM-GLW.md:308-310`) rather than block G3.
3. **[S]** `./gradlew publishToMavenLocal`. Smoke: Oriel 0.8.0 boots standalone against Keystone 1.9.2.

### G1 — Gangland reactor plumbing (branch `0.10.0` off `0.9.1`)
4. **[S]** Add the 7-pom Oriel dependency table (§2, `lootchest-api` dropped) with `<oriel.version>0.7.0-SNAPSHOT</oriel.version>`
   (or `0.8.0`/`0.8.0-SNAPSHOT` per G0's own resolution, C8) — compile side by side with inventory-api.
5. **[S]** `plugin.yml`: add `Oriel` to `depend:` (plugin.yml:7-9).
6. **[L]** (upsized from M, C5) `OrielMenuConfig` bean — blocked on the top Oriel ask (`MenuConfigService.forConsumer`,
   §10) for the 4 unpublished constructor args. Until that lands, the executor either hand-constructs all 17
   arguments (reaching into `PaginatedControlRegistry`'s internal package and re-wiring `MenuCommandRegistrar`
   itself) or the gate slips — flag this explicitly to the coordinator rather than silently absorb the risk.
   **Gate:** `mvn clean install` full reactor; smoke row **S1** — server boots with Keystone + Oriel + Gangland, no
   menu opened yet.

### G2 — Sever the domain inversion (gangland-domain)
7. **[M]** Move `GangFilterAdapter.java`/`MemberFilterAdapter.java` gangland-domain → gangland-impl (unchanged from
   the original plan; drops their `inventory.filter.*` imports once G0 step 2(a) confirms the target shape).
8. **[L]** Rewrite `GangItemSourceProvider.java` — build `ItemStack`s directly via `ItemBuilder` instead of
   `ItemSourceEntry(Map<String,String>)`; re-register `BINDING_GANGS`/`BINDING_GANG_MEMBERS` against Oriel's
   `FilterRegistry`. Also the row that now must feed `%member_*%`/`%ally_*%` into the `PlaceholderProvider` adapter
   from §3's placeholder row (C4) — this step and that adapter are the same piece of work, not two.
9. **[M]** `User.java`: delete `Set<InventoryHandler> inventories`, `InventoryRegistry inventoryRegistry`, and
   `addInventory/removeInventory(InventoryHandler)/removeInventory(String)/getInventory/getInventories/
   clearInventories()` (`User.java`:46,48,60,145-211,**197-204**). `UserFactory.java` drops its `InventoryRegistry`
   param. **Correction (B4): this is not "replaced by `OpenMenuTracker`"** — until the Oriel ask lands there is no
   substitute; the only current caller (`DebugCommand`'s `inv-data` argument) is **deleted**, not migrated, at G3
   step 17.
10. **[M]** (upsized from S) `gangland-domain/pom.xml`: remove the inventory-api dependency
    (**pom.xml:40-43**, corrected). **In the same commit (B2)** fix the three domain tests this signature change
    breaks — they are the wave's actual regression guard, not "net-new coverage":
    - `gangland-infra/gangland-domain/src/test/.../user/UserTest.java`:14,44,134,184
    - `.../user/UserManagerTest.java`:11,127,143,154,159,196,203
    - `.../GangMembershipTest.java`:15,145
    all construct `new User<>(plugin, handle, placeholder, new InventoryRegistry())` — drop the fourth argument to
    match `User`'s new 3-arg constructor.
    **Gate:** `mvn clean install -pl gangland-infra/gangland-domain,gangland-impl -am` (the 3 edited tests are the
    build-breaking check, not a grep); smoke row **S2**: `/glw filter gangs search <name>`.

### G3 — Core menus (gangland-impl)
11. **[L]** Port the 9 YAML menus per `MIGRATION-FROM-GLW.md` §1-§7. Two carry `Enchanted: true` on 4 slots total
    (`gang_info.yml:19,46,59`, `phone_banking.yml:31`) — per Decision D5, those 4 slots become a tiny code-registered
    single-item `ItemProviderRegistry` source (real enchant + `HIDE_ENCHANTS`, built in Java) instead of a static
    `slots:` entry, sidestepping Oriel's v0.2 declarative-NBT gap. Also apply S4: the 3 base64 skull textures
    (`Multi_Inventory.{Next_Page,Previous_Page,Home_Page}`) become **one** shared `HeadMaterialSources.registerDefaults`
    (`MenuConfigService.java:92`) `basehead:`/`texture:` default, not 3 copies baked into every paginated menu.
12. **[L]** (upsized from M) Delete `InventoryDefinitionStore/InventoryLoader/InventoryParser/InventoryRuntimeContext`
    — **but (C7)** `InventoryRuntimeContext.registerUniqueItemHandler` (:273-283) is the parser for
    `Information.Event.UniqueItem` in the menu YAML (`phone.yml:9` is the live example), and
    `GameplayConfig.java`:259-260 wires `GanglandUniqueItemInteractionService` off that exact context. Oriel's chest
    schema has no equivalent key. **New replacement, this step:** a small `unique_items.yml` side-file (Gangland's
    own ~20-line reader, not Oriel's schema) feeding the same records into `GanglandUniqueItemInteractionService` —
    without it, `/glw` loses wand/phone action gating silently.
13. **[S]** `UniqueItemHandler` — census correction stands (12-line record, `unique/UniqueItemHandler.java`:7-13);
    its data source is step 12's new side-file.
14. **[M]** (corrected, C6) Real command consumers — `command/sub/filter/FilterCommand.java` (232 lines, backs
    smoke row S2), `command/sub/lootchest/LootChestWandEditCommand.java`, `command/sub/gang/{GangCommand,
    GangColorCommand}.java`. **No file under `bank/`, `bounty/` or `phone/` imports `inventory.*`** — the original
    "7-file `command/sub/{gang,bank,bounty,phone}`" claim does not survive a grep, removed.
15. **[M]** (corrected, C6) `sign/aspect/{ViewInventoryAspect,BountyAspect}.java`, `bootstrap/ReloadPlugin.java`
    (`inventoryReload()`, `ReloadPlugin.java`:93-97 — `/glw reload inventory`), `lootchest/LootChestWand.java`,
    `listener/loot/LootChestWandListener.java`, `file/configuration/inventory/ConditionalSlotParser.java` (the 5th
    file in what §2's deletion list under-counted as 4). **`listener/player/PlayerInventoryListener.java` does not
    exist** — census invention, removed.
16. **[S]** `PlayerInventoryCleanup` — port as a plain `@ListenerHandler`, unchanged reasoning.
17. **[S]** `DebugCommand` villager tool → `TraderMenu.builder()` (unchanged). **Also (B4):** delete `/glw debug
    inv-data` (`DebugCommand.java`:458-480, reads `user.getInventories()`) — its data source is gone with no
    substitute until the Oriel `OpenMenuTracker` ask lands; do not claim a working replacement in `commands.json`
    or `Messages` (§5).
    **Gate:** `mvn clean install -pl gangland-impl -am`; smoke row **S3** (drop the `inv-data` click — it no longer
    exists): `/glw phone`, gang info, bank menu, bounty view, gang search+filter.

### G4 — npc-shops (18 files, corrected per C6)
18. **[L]** Generic `Panel`→`Panel` swap, 16 files: `trader/view/{NegotiationView,QuantitySelectorView,ShopView,
    ModeSelectView,TraderFlow,TraderFlowSession}`, `banker/view/{BankerAmountView,BankerClaimView,
    BankerCreateAccountView,BankerMenuView,BankerUpgradeView,BankerFlow,BankerFlowSession,BankerRenameAccountView}`,
    plus the two `listener/trader/*SessionListener`s. **Removed** (don't import inventory-api, C6): `BuyView`,
    `TraderBuyerHeaderPanel`, `banker/data/BankerData`. **Added** (real files C6 found): `ShopView`, `ModeSelectView`,
    `TraderFlow`, `TraderFlowSession`, `BankerFlow`, `BankerFlowSession`, `BankerRenameAccountView`. Per C1, the
    Panel generic swap itself is mechanical (`Panel<S extends FlowState>` vs GLW's `Panel<S extends FlowSession>` —
    only `size→rows`, drop the `Player viewer` param for `flow.viewer()`, panel receives a builder not a live
    handler); the real cost in this step is resolving each of npc-shops' 8 `rerender()` call sites
    (`BankerAmountView:273,281,337`, `BankerClaimView:188`, `BankerUpgradeView:163`, `NegotiationView:157`,
    `QuantitySelectorView:231,241`) per-view between `MenuFlow.switchTo(sameId)` and `ChestMenu.rerender()` **unless
    the Oriel `rerender()` ask lands first** — read each view's render body at execution time, do not guess a
    blanket answer. Apply the `back()`-empty-stack fallback (C2) to every root-panel back button, notably
    `BankerAmountView.java`:149-152,255.
19. **[L]** `BarterView.java` (516 lines) and `SellView.java` (466 lines) — **not** a Panel port, a deposit-slot
    **redesign** (C3). Both reach through `state.handler.getInventory()` to the raw Bukkit inventory to read
    player-placed items from dropzone slots, park `BARRIER` placeholders across a fill, restore them, and hand
    leftovers back on cancel (`BarterView.java`:189-219,317,376,391,418-432,454-459; `SellView.java`:193-219,295,
    342,359,376-382,404-409). Target: Oriel's `DepositSlotComponent` + `ChestMenu.snapshotDeposits` (`:295`),
    `.consumeDeposits` (`:394`), `.returnDeposits` (`:407`), `.bukkitInventory()` (`:309`). **Acceptance check
    (also §7 test):** items sitting in the deposit slot survive (a) the player closing the menu, (b) the player
    disconnecting mid-barter, (c) a server shutdown/reload while the flow is open. (c) depends on Oriel's A3
    "crash-safe held-item recovery" backlog item — **re-filed here as this step's own Oriel ask**, not WS4's.
20. **[M]** Listener package — click routing onto `ClickHandler`/`ClickKind`. **Booked as a deletion, not a port
    (S3):** `InventoryUtil.fillInventory`'s preserve/restore dance has no Oriel-side equivalent to write, because
    `ChestMenu.applyFillIfEmpty` (`ChestMenu.java`:~460) already skips non-empty slots — the hand-rolled logic
    simply goes away.
    **Gate:** `mvn clean install -pl gangland-features/gangland-npc-shops -am`; smoke row **S4**: trader buy, sell,
    barter, negotiate (plus step 19's deposit-survival check); banker create account, deposit, claim, upgrade.

### G5 — turf, cops-n-crooks, gadget
21. **[M]** turf: 6 files, same generic swap. `TurfPowerupBuffCatalogueView:100` and `TurfPowerupGarrisonView:108`
    are 2 more of the 20 `rerender()` call sites (B3) — same per-view decision as G4 step 18.
22. **[S]** cops: `detainment/paperwork/{PaperworkView,HandcuffBribeView}.java`.
23. **[S]** gadget: `CarSignViewProvider.java` off `InventoryHandler`/`Fill`/`InventoryUtil`.
24. **[S]** (new — Estimate-check miss) Retire `gangland-api/pom.xml:42`'s inventory-api re-export now that every
    runtime module (npc-shops, turf, cops, gadget) is off it. Leave `gangland-api/pom.xml:50`'s shop-api re-export
    until WS4 finishes.
    **Gate:** `mvn clean install -pl gangland-features/gangland-turf,gangland-features/cops-n-crooks,gangland-features/gangland-gadget -am`;
    smoke row **S5**.

### G6 cutover (after WS4 consumer gate) — shared, not WS2-sequenced
Per the orchestrator's ruling on B1: this gate is **not** part of WS2's own G0-G5 sequence. It runs once every
remaining `org.luckyraven.gangland.inventory.*` importer in the reactor is gone. Today that means WS4 finishing
shop-api's 9 files (`shop/view/{ShopAdminFlow,ShopAdminFlowSession,ShopAdminView,PriceEditorView,
BarterCategoryItemsAdminView,SellCategoryItemsAdminView}`, `shop/listener/{ShopAdminListener,
BarterCategoryAdminListener,SellCategoryAdminListener}`) and WS3 finishing lootchest-api's own conversion (R2 — WS2
never touches `LootChestService`/`LootChestSession`). Contract C1 runs WS4 before WS3, but the gate's own
precondition check makes the exact order irrelevant — whichever lands last, the grep below is the actual gate:

**Precondition, verified in the gate, not assumed:**
```
grep -rl "org.luckyraven.gangland.inventory" --include=*.java . | grep -v "gangland-ui/inventory-api/"
```
must return zero hits.

**Steps (owner: whichever workstream lands last, coordinated by the orchestrator):**
25. **[S]** Delete `gangland-ui/inventory-api/` (pom + 58 files) and its `<module>` line in `gangland-ui/pom.xml`.
26. **[S]** `gangland-build/pom.xml`: remove `net.wesjd:anvilgui` from `artifactSet.includes` (line 75) and the
    shade filter include (line 60) — precondition: `grep -rn "net.wesjd" --include=*.java .` returns zero hits.
27. **[S]** Delete `.claude/skills/panel-create/`; rewrite `documentation/developer/ui-framework.md`; update
    `CLAUDE.md:174`'s module-table row.
28. **[S]** (Estimate-check miss) Delete the `settings.yml` `Inventory:` block (lines 150-163) **and retire its
    matching `Settings` getters in the same commit** — don't leave orphaned getters.
29. **[S]** (Estimate-check miss) `graphify update . --force` (CLAUDE.md rule 3 — mandatory, a 58-file module is
    deleted) + a memory note + a docket record for anything the wave surfaced.
    **Gate:** `mvn clean install` full reactor, zero `org.luckyraven.gangland.inventory.*` references anywhere;
    smoke row **S6** (full regression + the Oriel-absent boot test).

---

## 5. Config, messages, permissions

| Item | Today | Destination |
|---|---|---|
| `settings.yml` `Inventory:` block (settings.yml:150-163) | `Fill.Item/Name`, `Line.Item/Name`, `Multi_Inventory` base64 heads | `Fill`/`Line` bake into each YAML's `decorations:` key; `Multi_Inventory` heads become **one shared** `HeadMaterialSources` `basehead:`/`texture:` default (S4), not 3 copies per menu. Block + its `Settings` getters deleted together at **G6 cutover step 28**, not earlier — the YAML files still reference the old block's values until every menu is ported. |
| `commands.json` | **Corrected (C9): no `/glw debug inventory`/`inv-data` entry exists today** (grepped, only unrelated hits at lines 160/580) | Nothing to update — `/glw debug inv-data` is deleted outright (B4), not renamed. |
| Unique-item config (C7) | `Information.Event.UniqueItem` block inside each menu YAML, parsed by `InventoryRuntimeContext.registerUniqueItemHandler` (`:273-283`) | New `unique_items.yml` side-file (G3 step 12), keyed by menu name, read by a small Gangland-owned loader — Oriel's chest schema has no equivalent key. |
| `Enchanted: true` (B5) — `gang_info.yml:19,46,59`, `phone_banking.yml:31` | Static YAML item flag | 4 code-registered single-item `ItemProviderRegistry` sources (D5), not a YAML key — see G3 step 11. |
| `Messages` enum | Zero inventory-specific constants | No change. |
| `.claude/skills/panel-create/` | Scaffolds `Panel<S extends FlowSession>` | Deleted at **G6 cutover step 27**, not earlier — shop-api (WS4) still uses the old `Panel` pattern until then. |
| No permission nodes under `gangland.inventory.*` found | n/a | Per-menu `permission:` keys carry forward unchanged. |

---

## 6. Persistence

Unchanged: no tables move. `User.inventories`/`clearInventories()` (now fully deleted, C9) and `OpenMenuTracker`
are both in-memory — no migration needed.

---

## 7. Tests

**Corrected (B2): the claim "zero existing tests touch inventory-api" is false.** Three domain tests are edited
(not net-new) at G2 step 10 — they are the actual regression coverage for severing `User`/`UserFactory`:

| Test | Change | Why |
|---|---|---|
| `UserTest.java`:14,44,134,184 | Drop the `InventoryRegistry` constructor arg | `User`'s constructor shrinks to 3 args (G2 step 9) |
| `UserManagerTest.java`:11,127,143,154,159,196,203 | Same | Same |
| `GangMembershipTest.java`:15,145 | Same | Same |

New tests:

| Test | Asserts |
|---|---|
| `GangFilterAdapterTest` / `MemberFilterAdapterTest` | Filtering/sorting still matches `GangFilterRegistration`'s declared fields after the domain→impl move |
| `GangItemSourceProviderTest` | Built `ItemStack`s and the registered Oriel source function round-trip correctly, including the `%money_symbol%`/row-scoped placeholder adapter (C4) |
| Oriel-side spike test (G0 step 2) | (a) a registered filter filters, (b) an anvil `set_search` action actually narrows a paginated region |
| **`BarterViewDepositSurvivalTest`/`SellViewDepositSurvivalTest`** (new, C3) | Items in the deposit slot survive menu close, player disconnect, and a simulated shutdown/reload mid-flow — G4 step 19's acceptance check |
| `OrielMenuConfigTest` | **Blocked/deferred** on the `MenuConfigService.forConsumer` Oriel ask (C5) — writing it against the raw 17-arg constructor is not worth doing twice |

### Smoke rows (corrected)

| Row | Setup | Steps | Expect |
|---|---|---|---|
| S1 | Keystone + Oriel + Gangland, no menu opened | boot | Zero `inventory.*`/`menu.*` faults |
| S2 | S1 + a gang | `/glw filter gangs search <name>` | Filtered gang list renders |
| S3 | S1 | `/glw phone`, gang info, bank deposit/withdraw, bounty view, `phone_gang_search` (**no `inv-data` click — deleted, B4**) | Every core menu opens and clicks |
| S4 | S1 + a trader/banker NPC | buy, sell, barter, negotiate, create account, deposit, claim, upgrade, **then disconnect mid-barter and reconnect** | Every flow works; deposited items are intact after reconnect (C3 acceptance check) |
| S5 | S1 + turf/cops | turf powerup menu (rerender'd buff catalogue), detainment paperwork, bribe offer, car sign hover | Renders and clicks |
| S6 | Oriel plugin **removed** | boot | Gangland fails to enable with a named fault, not a partial boot |

---

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **`rerender()` gap (B3)** — the wave's real rewrite risk, not the generics question this review resolved (C1). 20 call sites, no Oriel primitive. | Oriel ask (~10 lines, §10); per-view fallback decided at G4/G5 execution time, not guessed here. Rollback: keep old `Panel`/`FlowSession` un-deleted until G4's gate passes. |
| 2 | **`BarterView`/`SellView` deposit-slot redesign, item-loss exposure (C3)** — two 500-line files, real player-facing risk if items vanish on crash. | Explicit survival acceptance check + dedicated tests (§7); Oriel A3 ask re-filed to gate this step; ship with in-process-only recovery (current GLW parity) if A3 lands late. |
| 3 | **`MenuConfigService`'s 17-arg/4-unpublished constructor (C5)** blocks G1 step 6 cleanly. | Top-priority Oriel ask (`forConsumer` factory); fallback is hand-construction, reaching into an internal package — flagged as brittle, not silently absorbed. |
| 4 | `back()`-empty-stack dead buttons (C2) — UX-only, not data loss. | Oriel ask or consumer `boolean root` flag; low severity. |
| 5 | Filter/search spike (G0 step 2) is now two claims, not one — basic filtering confirmed, anvil-driven search still open. | Explicit dual acceptance criteria in the step; fallback to the manual anvil-button pattern if (b) fails. |
| 6 | Hard `depend: Oriel` (§9 D1) — operational bar for server operators. | §9 documents the softdepend cost so the user can veto with full information. |
| 7 | D5's Enchanted-glow workaround (code-registered single-item sources) is unverified — does `ItemProviderRegistry` actually support a single-item, non-paginated registration the way it supports list sources? | Confirm at G3 step 11 before committing; if it doesn't, fall back to accepting the visual regression (§9 D5's second option) rather than block. |

**Rollback story per gate:** G0-G1 additive, revert by dropping the branch. G2-G5 each touch a bounded file set and
leave inventory-api compiling until the shared G6 cutover — revert any single gate by reverting its commits. The
G6 cutover gate is the only irreversible one (module deletion), and it is explicitly **not** WS2's to trigger alone.

---

## 9. Decisions for the user

### D1 — `Oriel` in `plugin.yml`: hard `depend:` or `softdepend:`?
- **Hard depend (recommended)** — every core menu needs Oriel after this wave, no module boundary exists for "the
  entire UI layer" the way `Plugins:` degrades a single runtime module. **Sharper evidence (review's correction):**
  Keystone's `ReflectionGuard` (`keystone-common/.../diagnostics/ReflectionGuard.java`, used at
  `BeanFactory.java`:234,255,672, `ListenerService.java`:91, `DependencyContainer.java`:179) would stop a
  missing-Oriel *scan* from crashing outright — it skips a class with `reflection.type.missing` — but it only
  guards **signatures**. A bean **body** that calls an Oriel class still throws `NoClassDefFoundError` (docket
  KS-MO-07), so a silently-skipped `@Configuration`/command bean is a **half-wired** plugin, not a gracefully
  degraded one. This is the real argument for hard depend, not "~30 null-checks."
- **What changes if soft wins:** every menu-open call site gains a null-check + `Messages` fallback string; smoke
  S6 changes from "fails to enable" to "boots with menus disabled."

### D2 — Menu YAML home: `plugins/Gangland_Warfare/menus/` or `plugins/Oriel/menus/`?
- **Gangland's own folder (recommended)**, unchanged reasoning. **Resized (C5):** this now costs the top Oriel ask
  (`MenuConfigService.forConsumer`) and makes G1 step 6 **[L]**, not [M].

### D3 — Filter/search/sort: Oriel's registries or GLW's `FilterStore`/`FilterApplier` ported verbatim?
- **Oriel's registries (recommended)** — basic filtering confirmed shipped and wired (§3). **Open sub-question
  (D3 caveat, item 9):** anvil-driven paginated search is genuinely still deferred per
  `MIGRATION-FROM-GLW.md:394` — G0 step 2(b) resolves this before G3 commits; fallback is the manual anvil-button
  pattern, not a blocker either way.

### D4 — Oriel 0.8.0 Keystone target: 1.9.2 or 1.10.0?
- **1.9.2 now (revised — S1, supersedes the original 1.10.0 recommendation).** Pinning to 1.10.0 makes G0, WS2's
  first gate, wait on WS3/WS4's Keystone modules, contradicting contract C1's "WS2 first." Keystone is
  backward-compatible within 1.x and one Keystone jar serves the whole server, so an Oriel compiled against 1.9.2
  runs fine on a 1.10.0 host. A separate small gate bumps Oriel to 1.10.0 later, once WS3/WS4's modules exist.
  Carry the H5-deprecation note (§4 G0 step 1) forward as a pre-2.0.0 migration item, not a WS2 blocker.

### D5 — `Enchanted: true` on 4 slots (`gang_info.yml`:19,46,59, `phone_banking.yml`:31) — new, B5
- **Recommended:** register those 4 items via Oriel's `ItemProviderRegistry` as tiny code-built single-item
  sources (real enchantment + `HIDE_ENCHANTS`, built in Java), sidestepping Oriel's v0.2 declarative-NBT roadmap
  item entirely. Smaller and faster than either blocking G0 on Oriel shipping NBT support, or shipping a silent
  visual regression.
- **Alternative:** accept the regression — the 4 slots lose their glow at G3 step 11, ship anyway, revisit when
  Oriel's v0.2 lands. Only worth taking if risk #7 (§8) proves `ItemProviderRegistry` can't do single-item sources.
- **What changes:** G3 step 11's size/approach for those 4 slots only; nothing else in the plan.

### D6 — WS2-vs-WS4 split of shop-api's 9 files — settled, recorded for the audit trail
The review flagged this as a decision that should have been surfaced rather than left implicit in a
self-contradictory gate (B1). **Ruled by the orchestrator, not open in this plan:** the G6 cutover gate moves out
of WS2's own sequence and runs after WS4 (and, in practice, after WS3), rather than WS2 absorbing shop-api's 9
files into a new `G5.5`. Recorded here so the rejected alternative is visible, not for re-litigation.

---

## 10. WS6 asks / Oriel asks / Keystone asks

### Oriel asks (primitive, why, smallest implementation, size, gates)

| Primitive | Why | Smallest implementation | Size | Gates |
|---|---|---|---|---|
| `MenuConfigService.forConsumer(plugin, fileManager, placeholders)` factory | 17-arg constructor, 4 args not on `ServicesManager` (C5) | Factory resolves the published 13 + constructs/locates the 4 internally | M | **G1 step 6 — top priority, blocking** |
| Publish `OpenMenuTracker` on `ServicesManager` (or expose via `MenuOpener`) | Internal-only bean today (B4); `User.inventories`'s only real consumer has no substitute without it | One `registerServices()` line | S | Unblocks re-adding `/glw debug inv-data` later; not required to ship WS2 (feature deleted instead, §4 G3 step 17) |
| `MenuFlow.rerender()` | No panel-in-place re-render exists; 20 call sites across npc-shops/turf/shop-api (B3) | ~10 lines beside `switchInternal`, re-running `Panel.render` into the live `ChestMenu` without a Bukkit reopen | S | **The wave's largest rewrite risk if declined — §8 risk #1** |
| `back()` end-on-empty-stack (or `hasBack()` accessor) | Root-panel back buttons go dead today (C2) | One-line fix or a boolean accessor | S | Cosmetic severity only |
| Anvil-driven paginated search (`set_search` wired to an anvil prompt) | `MIGRATION-FROM-GLW.md:394` defers it; `phone_gang_search.yml`/`user_stat.yml` need it | Complete/document `PaginatedControlActions.java`'s path — G0 step 2(b) spikes it | S-M | Falls back to the manual anvil-button pattern if declined, not blocking |
| A3 crash-safe `DepositSlotComponent` persistence | `BarterView`/`SellView`'s redesign (C3) needs items to survive a mid-flight crash — **re-filed WS4→WS2/G4** | Already Oriel's own backlog item | M (Oriel-side) | Gates G4 step 19's full acceptance check; ships at reduced (in-process-only) safety if declined |
| Declarative NBT in chest YAML | Oriel's own v0.2 roadmap item | **No longer blocking** — D5's code-registration workaround covers WS2's 4 slots | — | Keep on Oriel's roadmap for WS4's shop item previews |
| Doc fix, `MIGRATION-FROM-GLW.md`:389-394 | Basic pagination filter/sort is shipped and wired; only the anvil-search sub-case is genuinely deferred — the two claims are currently conflated | Split the doc line into two | S | Non-blocking |
| `.aroundSlot()`-equivalent ring helper | No Oriel component for the amount-selector ring pattern | **Not an Oriel ask** (S3 confirmed) — consumer-side 15-line static helper | S (ours) | — |

**Loot chest note (R2):** `LootChestService`/`LootChestSession` are entirely WS3's now — no ask registered here.
Forward note only: WS3 may want an Oriel-based admin preview screen for the wand's chest-editing flow; flagged for
WS3's own plan, not a WS2 deliverable.

### WS4 asks (shop-api, executed by WS4)
- shop-api's 9 files (§4 G6 cutover precondition list) use the same `Panel`/`FlowSession` pattern G4 ports — WS4
  should start once G4's gate passes, reusing G4's resolved generics/rerender/back() patterns. WS4 does **not**
  inherit the deposit-slot redesign problem (C3) — WS2 already solved that pattern in G4 step 19, so WS4's own
  view rewrite is a cleaner mechanical swap unless shop-api's admin views turn out to have their own dropzone code
  (not found in this session's reads — check at WS4 execution time).

### Keystone asks
- None blocking. FYI only: Oriel's `CommandTabCompleter`/`BrigadierTabRegistrar` static usage is
  `@Deprecated(forRemoval)` since Keystone's H5 wave (`docs/phase-h5-upstream-wave.md`:78-83) — track for
  pre-2.0.0 migration, not this wave.

### WS6 asks (api facade)
- `gangland-api` 2.0 should re-export `oriel-core`/`oriel-chest`/`oriel-anvil` the way it re-exported inventory-api
  — confirm in scope for WS6's facade design.

---

## 11. Docket

`brainstorming/bug-docket-2026-09-06/findings/` — grepped for inventory/menu/GUI/panel keywords: zero matches.
**Not re-verified in this rework pass** (the review flagged this as unverified too, §13). `cross-docket-2026-09-10/oriel/findings/`
(9 files) — still unread; check before G0 signs off, since B3/B4/C5 all lean on the exact Oriel classes those
findings cover (`menu-chest`, `menu-config`, `menu-core`). No new Gangland-side bugs found to file under `triage/`
this pass — the gaps this review surfaced (`rerender`, `back()`, `OpenMenuTracker` publication, `MenuConfigService`'s
factory) are Oriel feature gaps, tracked via §10, not Gangland bugs.

---

## 12. Estimate

**Revised per the review's estimate check.** 29 numbered steps across 6 WS2-owned gates (G0-G5) plus the shared,
unscheduled G6 cutover.

| Gate | Wall-clock | Change from original | Why |
|---|---|---|---|
| G0 | 0.5d | unchanged | Spike upsized in scope, not duration |
| G1 | **1d** (was 0.5d) | +0.5d | Step 6 is [L], blocked on an Oriel ask (C5) |
| G2 | 1.5d | unchanged | 3 test fixes are small edits, absorbed |
| G3 | **3d** (was 2d) | +1d | C6's real file list is bigger (`ReloadPlugin`, `FilterCommand`, lootchest wand trio, `BountyAspect`, `ConditionalSlotParser`) + C7's new `unique_items.yml` design |
| G4 | **3d** (was 1.5d) | +1.5d | C3's two 500-line deposit-slot redesigns + per-view `rerender`/`back()` decisions across 8+ call sites |
| G5 | 1d | unchanged | +1 small pom-retirement step, absorbed |
| **WS2 own total** | **~10d** | was ~7.5d | |
| G6 cutover | ~0.5d, **not scheduled by WS2** | new | Shared gate; triggers once WS4 (and WS3) finish their own inventory-api consumers |

---

## 13. Not verified

- **Whether `ChestMenu.rerender()` suffices for any specific GLW view** — neither the original plan, the review,
  nor this rework read every view's render body. G4/G5 execution time must decide per view (§4 steps 18, 21).
- **Whether `RequirementRegistry` has a consumer-reachable in-memory implementation** beside the interface (C5
  assumes one exists next to `InMemoryFilterRegistry`; only the interface was read, by the review, not independently
  confirmed here).
- **Oriel cross-docket findings** (`cross-docket-2026-09-10/oriel/findings/*.txt`, 9 files) — still unread by this
  plan or the review; check before G0 signs off (§11).
- **No compile was attempted in either repo**, in the original plan, the review, or this rework — every signature/
  wiring claim is static analysis (grep + graphify + source reads). The per-module `mvn clean install` gates
  remain the actual verification; §8's rollback story assumes this.
- **The bug docket's "zero inventory/menu entries" claim** was not re-grepped in this rework pass either (§11).
- **Whether Oriel 0.8.0 ships as a Maven/Gradle release or another `-SNAPSHOT`** out of `publishToMavenLocal` (C8)
  — affects how the 7 Gangland poms pin `<oriel.version>`; confirm at G0.
- **D5's `ItemProviderRegistry` single-item workaround** (§9, §8 risk #7) — not implemented or compile-checked in
  this pass; confirm it supports a non-paginated, single-item registration (not just list sources) before G3 step
  11 commits to it.
- **Whether shop-api's own admin views have a `BarterView`/`SellView`-style dropzone problem** (WS4 ask, §10) —
  not checked in this session; flagged for WS4's own planning pass.
