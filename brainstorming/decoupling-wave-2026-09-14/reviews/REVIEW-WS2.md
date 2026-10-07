# REVIEW WS2 — inventory-api is replaced by Oriel

Verdict: **REWORK**

The research is the strongest in the wave so far and most census corrections are right. But one gate cannot
execute in the order written (G6 deletes a module nine shop-api files still import), the two facts the test and
YAML sections rest on are false, the flow primitive the 18 npc-shops views use most has no Oriel equivalent, and
the class the plan builds its whole YAML story on takes seventeen constructor arguments, three of which Oriel does
not publish. All fixes are bounded; none require re-planning the workstream.

---

## Blockers (must fix before executors start)

**B1. G6 step 25 deletes inventory-api while shop-api still imports it.** §1 "Out" hands shop-api's view rewrite to
WS4, but G6's gate demands "zero references to `org.luckyraven.gangland.inventory.*` anywhere" while contract C1
runs WS4 *after* WS2. Nine shop-api files import the package today — `shop/view/{ShopAdminFlow,
ShopAdminFlowSession, ShopAdminView, PriceEditorView, BarterCategoryItemsAdminView, SellCategoryItemsAdminView}` and
`shop/listener/{ShopAdminListener, BarterCategoryAdminListener, SellCategoryAdminListener}` (verified by
`grep -rl` over `gangland-ui/shop-api/src/main/java`). Fix: either WS2 absorbs those 9 files into a new gate G5.5
(recommended — they are the same `Panel`/`FlowSession` rewrite as G4 and WS4's real work is the Keystone promotion,
not the views), or G6 moves out of WS2 into a shared cutover gate after WS4. Pick one and say so; do not leave the
gate self-contradictory.

**B2. "Zero existing tests touch inventory-api" (§7) is false — three domain tests break at G2 step 9.**
`gangland-infra/gangland-domain/src/test/.../user/UserTest.java:14,44,134,184`,
`.../user/UserManagerTest.java:11,127,143,154,159,196,203` and `.../GangMembershipTest.java:15,145` all
`import org.luckyraven.gangland.inventory.service.InventoryRegistry` and call
`new User<>(plugin, handle, placeholder, new InventoryRegistry())`. Deleting the `InventoryRegistry` constructor
parameter changes a signature these three exercise. They are not "net-new coverage" — they are three files to edit
in the same commit, and the `-pl gangland-infra/gangland-domain` gate build will fail without them. Add them to §7.

**B3. `MenuFlow` has no `rerender()` — 20 call sites, and the plan never mentions it.** GLW
`MultiPanelInventory.rerender()` (`flow/MultiPanelInventory.java:116-123`) is `current.clear(); panel.render(this,
current, viewer, session)` — it re-runs the panel's render logic **against the already-open Bukkit inventory**.
Oriel's `MenuFlow` (chest `flow/MenuFlow.java:95-161`) exposes `openAt/switchTo/back/suspend/resume/end/
currentMenu/currentPanelId/state/viewer/suppressClose` and nothing else. The two candidate substitutes are both
lossy:
- `MenuFlow.switchTo(samePanelId)` (`MenuFlow.java:118-126`) does re-run `panel.render`, but `switchInternal`
  builds a **new** `ChestMenu` and calls `newMenu.open(viewer)` (`MenuFlow.java:165-190`) — a real Bukkit reopen
  (flicker, cursor/drag reset), which is exactly what GLW's rerender avoids.
- `ChestMenu.rerender()` (`ChestMenu.java:432-435` → `renderAll(viewer)`) re-renders the **existing component set**;
  it never re-invokes `Panel.render`, so a panel that adds/removes a button on state change (confirm appearing,
  mode row flipping) will not update unless every dynamic item is re-expressed as a supplier-backed Component.

Call sites: `BankerAmountView:273,281,337`, `BankerClaimView:188`, `BankerUpgradeView:163`, `NegotiationView:157`,
`QuantitySelectorView:231,241`, `ShopView:115,130`, `TurfPowerupBuffCatalogueView:100`, `TurfPowerupGarrisonView:108`,
plus 7 in shop-api. This is the wave's real rewrite risk, not the generics question in §8 risk #1 (see C1). Either
add an Oriel ask — `MenuFlow.rerender()` re-running the current panel into the live `ChestMenu` is ~10 lines beside
`switchInternal` — or state per view which of the two lossy substitutes it takes. Until then G4's [L]/[L]/[M] is
guesswork.

**B4. `OpenMenuTracker` is not reachable from a consumer, so "not replaced" (§3 row 4, G2 step 9) has no substitute.**
`OrielPlugin.registerServices()` (`menu-plugin/.../OrielPlugin.java:178-197`) publishes `MenuRegistry`, `MenuOpener`,
`ActionRegistry`, `SourceProviderRegistry`, `FilterRegistry`, `SortRegistry`, `SearchMatcherRegistry`,
`ItemProviderRegistry`, `MaterialSourceRegistry`, `ComponentRegistry`, `DatabaseBackendRegistry`, `PacketAdapter`.
`OpenMenuTracker` is **not** in that list — it is an internal bean built in `MenuTrackingConfig.java:30`. The plan's
only stated consumer (`DebugCommand`'s `inv-data` argument, `command/sub/debug/DebugCommand.java:458-480`) therefore
loses its data source entirely. Fix: add an Oriel ask ("register `OpenMenuTracker` alongside `MenuRegistry`", one
line in `registerServices`), or delete `/glw debug inv-data` and say so in §5. Do not leave §3 claiming a publisher
that does not publish.

**B5. `Enchanted: true` IS used by the core menus — the §1 "Deferred" dismissal is wrong.**
`gang_info.yml:19,46,59` and `phone_banking.yml:31`. Oriel defers "Declarative NBT on items (enchantments,
custom_model_data, unbreakable)" to v0.2+ (`MIGRATION-FROM-GLW.md:391`). The plan's justification —
"`grep -l "Enchanted:" …` returned nothing in this session" — does not reproduce. Four slots in two of the nine
menus lose their glow at G3 step 11. Promote declarative NBT from "Oriel-side, WS4-relevant" to a **G0 blocker for
WS2**, or accept the regression explicitly as a decision in §9.

---

## Corrections (fix in place)

**C1. §8 risk #1 and §13 bullet 1 are resolved and can be deleted.** Oriel's `Panel` **is** generic:
`public interface Panel<S extends FlowState>` with `rows(S)`, `title(S)`, `render(MenuFlow<S>, ChestMenuBuilder, S)`
(`menu-inventories/chest/.../flow/Panel.java:11-24`), against GLW's `Panel<S extends FlowSession>` with
`size(S)`, `title(S)`, `render(MultiPanelInventory<S>, InventoryHandler, Player, S)`
(`inventory-api/.../flow/Panel.java:11-18`). The only signature deltas: `size`→`rows`, the `Player viewer` parameter
is gone (use `flow.viewer()`), and the panel receives a **builder**, not a live handler. The "fallback is a per-flow
enum switch" contingency is dead weight — remove it. The genuine differences are B3 above plus C2/C3 below.

**C2. `back()` semantics differ and no view can detect it.** GLW: empty back-stack → `end()`
(`MultiPanelInventory.java:104-112`). Oriel: empty back-stack → silent no-op (`MenuFlow.java:128-133`), and
`MenuFlow` exposes no `hasBack()`/stack accessor. Every "back" button rendered on a root panel (e.g.
`BankerAmountView.java:149-152,255`, `BarterView.java:352`) becomes a dead button instead of closing the flow. One-line
Oriel ask (`if (previous == null) { end(); return; }`) or a consumer-side `boolean root` flag per panel. Also note
the improvement in the other direction: Oriel's `switchTo(same id)` does not push the back-stack, GLW's always did.

**C3. `BarterView` and `SellView` are not `Panel` ports — they are deposit-slot rewrites, and they are in WS2, not WS4.**
Both reach through `state.handler.getInventory()` to the raw Bukkit inventory to read player-placed items out of
dropzone slots, park `BARRIER` placeholders across a fill, restore them, and hand leftovers back on cancel —
`BarterView.java:189-219,317,376,391,418-432,454-459` (516 lines) and `SellView.java:193-219,295,342,359,376-382,404-409`
(466 lines). Oriel has the right primitives (`DepositSlotComponent`, `ChestMenu.snapshotDeposits:295`,
`consumeDeposits:394`, `returnDeposits:407`, `bukkitInventory():309`), and they are strictly better than the
hand-rolled dropzone — but this is a redesign of two 500-line files with real item-loss exposure, not the
"`implements Panel<TraderFlowSession>` → `implements Panel`" swap G4 step 18 describes. Consequently the
`USE-CASE-ENABLERS.md` A3 crash-safe-deposit ask in §10 is **WS2/G4's** problem, not WS4's — re-file it and give
G4 an explicit "items in the dropzone on shutdown / flow end" acceptance check.

**C4. §5's placeholder row and §3's "unchanged / identical degradation" claim are wrong for two token families.**
`%money_symbol%` (11 uses across the 9 menus) is **not** PAPI — it is registered on Gangland's own
`PlaceholderService` at `config/KernelConfig.java:105`
(`service.register((player, text) -> text.replace("%money_symbol%", Settings.getMoneySymbol()))`). And
`%member_name|rank|contribution|join_date|online_status%` / `%ally_id|name|online|total|created%` are **row-scoped**
tokens built per list entry by `GangItemSourceProvider.java:74-104` as `ItemSourceEntry(Map<String,String>)` — no PAPI
expansion can resolve them, since PAPI does not know which member a slot shows. Oriel renders through its own
`PlaceholderProvider` chain, so both families render literally unless Gangland supplies a provider. The seam exists
(`ChestMenuBuilder.placeholders(...)` at `ChestMenuBuilder.java:158`, plus `MenuConfigService`'s `placeholders`
parameter), so this is a correction, not a blocker — but §3's row must name the provider Gangland has to write.
(`%gangland_anvil_output%`, 7 uses, is a third case: it maps to `ChestMenu.bindArguments(ArgumentBinding)` /
`AnvilClickContext`, not PAPI.)

**C5. `MenuConfigService` is not a one-file [M] bean.** Its constructor
(`menu-data/config/.../MenuConfigService.java:69-76`) takes **17 arguments**: plugin, MenuRegistry, FileManager,
PlaceholderProvider, ActionRegistry, SourceProviderRegistry, FilterRegistry, SortRegistry, SearchMatcherRegistry,
ItemProviderRegistry, MaterialSourceRegistry, PaginatedControlRegistry, ComponentRegistry, MenuCommandRegistrar,
RequirementRegistry, CooldownService, BooleanSupplier. Cross-referencing `registerServices()` (B4): **four are not
published** — `PaginatedControlRegistry` (and it lives in `chest/internal/`, an internal package), `RequirementRegistry`
(an interface, `config/requirement/RequirementRegistry.java:21`), `CooldownService` (Keystone's), and
`MenuCommandRegistrar` (constructible, `config/command/MenuCommandRegistrar.java:44`). D2's recommended option is
still the right one, but it costs an Oriel ask — a consumer-facing factory (`MenuConfigService.forConsumer(plugin,
fileManager, placeholders)` resolving the rest from `ServicesManager`) — and G1 step 6 is [L], not [M], until that
exists. Add it to §10 as the top Oriel ask; it is the only one that gates G1.

**C6. The impl and npc-shops file lists in §4 are partly invented and partly incomplete** (all below from
`grep -rl "org.luckyraven.gangland.inventory"` per module):
- Step 14's `command/sub/{gang,bank,bounty,phone}/` — **no** file under `bank/`, `bounty/` or `phone/` imports
  inventory. The real command work is `filter/FilterCommand` (232 lines, the `/glw filter` family backing smoke row
  S2, which the plan marks "TBD"), `lootchest/LootChestWandEditCommand`, `gang/GangCommand`, `gang/GangColorCommand`.
- Step 15's `listener/player/PlayerInventoryListener.java` **does not exist** (census invention; `find` returns only
  inventory-api's own `listener/PlayerInventoryCleanup.java`).
- Unmentioned impl importers: `bootstrap/ReloadPlugin`, `sign/aspect/BountyAspect`, `lootchest/LootChestWand`,
  `listener/loot/LootChestWandListener`, `file/configuration/inventory/ConditionalSlotParser` (a 5th file in the
  package the §2 deletion list gives as 4).
- npc-shops: `BuyView`, `TraderBuyerHeaderPanel` (step 18) and `banker/data/BankerData` (step 19) do **not** import
  inventory-api; the real 18 add `ShopView`, `ModeSelectView`, `TraderFlow(+Session)`, `BankerFlow(+Session)`,
  `BankerRenameAccountView` and the two `listener/trader/*SessionListener`s.

**C7. The unique-item config has no home after the port.** The §4 step 13 correction is right on the facts —
`UniqueItemHandler` is a 12-line record (`inventory-api/.../unique/UniqueItemHandler.java:7-13`) and belongs in
Gangland. What the plan misses is *where its data comes from*: `InventoryRuntimeContext.registerUniqueItemHandler`
(`:273-283`) parses `Information.Event.UniqueItem` out of the menu YAML (`phone.yml:9` is the live example), and
step 12 deletes that parser. Oriel's chest schema has no such key. Name the replacement (a small
`unique_items.yml`, or an `Information:`-style side-file keyed by menu name) in §5, or `/glw` loses the wand/phone
action gating silently — `GameplayConfig.java:259-260` wires `GanglandUniqueItemInteractionService` off the very
context being deleted.

**C8. Oriel's version is `0.7.0-SNAPSHOT` (`gradle.properties:2`), not `0.7.0`.** G1 step 4's eight poms need a
`<oriel.version>` property and the executor must know whether 0.8.0 ships as a release or a SNAPSHOT out of
`publishToMavenLocal`. One line in §2.

**C9. Small factual drift, fix silently:** domain pom's inventory-api dependency is lines **40-43**, not 41-43/42-43
(`gangland-infra/gangland-domain/pom.xml:40-43`); `plugin.yml` `depend:` is lines **7-9**, not 6-9; §2's deletion list
omits `User.clearInventories()` (`User.java:197-204`); `commands.json` has **no** `/glw debug inventory` entry at all
(searched — only unrelated hits at 160/580), so the §5 row should say "none exists; add one if the debug argument
survives B4".

---

## Simplifications (ponytail)

**S1. Drop D4's recommendation — target Keystone 1.9.2, not 1.10.0.** Oriel names 92 distinct
`org.luckyraven.keystone.*` types; every one resolves against Keystone's current tree (checked by name across
`E:\Programming\java\Keystone`, the only two "misses" being the nested `PacketAdapter.FakeWindowType` and
`Severity.ERROR`). Pinning Oriel to 1.10.0 makes G0 — the wave's first gate — wait on WS3/WS4's Keystone modules,
which contradicts C1's "WS2 first". Keystone is backward-compatible within 1.x and one Keystone jar serves the
server, so Oriel compiled against 1.9.2 runs fine on a 1.10.0 host. Ship G0 today against 1.9.2. (Carry one note:
Oriel uses `CommandTabCompleter`/`BrigadierTabRegistrar` statics that H5 marked `@Deprecated(forRemoval)` for 2.0.0
— `docs/phase-h5-upstream-wave.md:78-83`. Compile-clean at 1.9.2, must migrate before Keystone 2.0.0.)

**S2. No converter script for 9 YAML files.** The plan does not propose one — good. Keep it that way; nine hand
ports, one commit each (step 11), is smaller than a parser nobody runs twice.

**S3. The `.aroundSlot()` ask (§10 row 5) is answered correctly** — consumer helper, not an Oriel component. Extend
the same treatment to `InventoryUtil.fillInventory`'s preserve/restore dance (C3): Oriel's `applyFillIfEmpty`
(`ChestMenu.java:~460`) already skips non-empty slots, so that code deletes itself — book it in G4 as a saving.

**S4. §5 bakes three base64 skulls into each of the 9 YAML files.** `HeadMaterialSources.registerDefaults`
(`MenuConfigService.java:92`) supports `basehead:`/`texture:` prefixes — one shared paginated-control default beats
three copies per paginated menu.

---

## Missing consumers found by graphify affected

| Type moved | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `InventoryRegistry` (deleted from `User`) | `UserTest`, `UserManagerTest`, `GangMembershipTest` | `gangland-domain/src/test/.../UserTest.java:44,134,184`; `UserManagerTest.java:127,143,154,159,196,203`; `GangMembershipTest.java:145` | G2 gate build fails (B2) |
| `InventoryHandler` / `InventoryLoader` | `bootstrap/ReloadPlugin.inventoryReload()` | `ReloadPlugin.java:93-97` | `/glw reload inventory` breaks; no step covers it |
| `filter.*` | `command/sub/filter/FilterCommand` (232 lines) | `FilterCommand.java` | The command smoke row S2 depends on; unscheduled |
| `UniqueItemHandler` | `lootchest/LootChestWand`, `listener/loot/LootChestWandListener`, `command/sub/lootchest/LootChestWandEditCommand` | three impl files importing `inventory.*` | Wand gating loses its config source (C7) |
| `InventoryHandler` / `condition.*` | `sign/aspect/BountyAspect`, `file/configuration/inventory/ConditionalSlotParser` | same packages as planned edits | Only `ViewInventoryAspect` and 4 of 5 parser files are planned |
| `MultiPanelInventory` | shop-api's 9 files at G6 delete time | `shop-api/.../view/*`, `.../listener/*` | B1 |
| `Panel`/`FlowSession` | npc-shops `ShopView`, `ModeSelectView`, `TraderFlow(+Session)`, `BankerFlow(+Session)`, `BankerRenameAccountView`, 2 session listeners | `gangland-features/gangland-npc-shops/.../` | G4's named files partly do not exist (C6) |

Docs/tooling the plan **does** catch correctly: `.claude/skills/panel-create/` (exists, `SKILL.md` + `references/`),
`documentation/developer/ui-framework.md` (exists), `CLAUDE.md:174` module-table row. Nothing else references
inventory-api outside `.claude/worktrees/` (stale p0-wave-2/3 copies — ignore them; they inflate every raw grep count).

---

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| D1 Oriel hard `depend:` | Hard | **Agree**, with better evidence | Keystone's `ReflectionGuard` (`keystone-common/.../diagnostics/ReflectionGuard.java`, used at `BeanFactory.java:234,255,672`, `ListenerService.java:91`, `DependencyContainer.java:179`) *would* stop a missing-Oriel scan from crashing — it skips the class with `reflection.type.missing`. But it only guards **signatures**: a bean body calling an Oriel class still throws `NoClassDefFoundError` (docket KS-MO-07), and a silently-skipped `@Configuration`/command bean is a half-wired plugin, not a degraded one. Hard depend is right; cite this instead of the "~30 null-checks" argument. |
| D2 Menu YAML in Gangland's folder | Gangland's | **Agree**, but re-size | Correct call (operator-visible config belongs to the plugin that ships it), but it costs an Oriel ask and G1 step 6 is [L] — see C5. |
| D3 Oriel filter/search/sort registries | Oriel's | **Agree; the doc/census contradiction resolves in the plan's favour** | `ChestMenuLoader`'s primary ctor takes `FilterRegistry, SortRegistry, SearchMatcherRegistry, SourceProviderRegistry, PaginatedControlRegistry` (`config/loader/ChestMenuLoader.java:112-131`, three fallback overloads at `:136,145,156`), and `MenuConfigService:96-97` wires the chest loader with all of them — `MIGRATION-FROM-GLW.md:392` is stale. **Caveat the plan misses:** line 394 also defers *"Anvil-driven search on paginated regions"*, which is exactly the UX `phone_gang_search.yml:35-76` and `user_stat.yml:26-46` ship. G0 step 2's spike must prove the **anvil → `set_search`** path (`config/loader/PaginatedControlActions.java`), not just "a registered filter filters". |
| D4 Oriel 0.8.0 on Keystone 1.10.0 | 1.10.0 | **Disagree — take 1.9.2** | See S1: 1.10.0 serialises WS2's first gate behind WS3/WS4. |
| *(unsurfaced)* B1's WS2-vs-WS4 split of shop-api | — | **Should be a decision** | "WS2 absorbs the 9 shop-api view files" vs "G6 moves after WS4" is the user's call on wave shape; surface it. |

---

## Estimate check

7.5 days is understated by about a third. **G1** 0.5d → 1d (step 6 is [L] until the Oriel factory ask lands, C5).
**G3** 2d → 3d (the 22-file list is wrong both ways, C6; `ReloadPlugin`, `FilterCommand`, the three lootchest-wand
files, `BountyAspect`, `ConditionalSlotParser` are unbudgeted, and C7 needs a new config file + loader).
**G4** 1.5d → 3d (C3's two 500-line deposit-slot redesigns plus a per-view `rerender` decision, B3). **New G5.5**
(shop-api, 9 files) ≈ 1d if B1 resolves toward WS2 absorbing it. G0/G2/G5/G6 look honest. Realistic **≈ 11 days**.

Missing steps: `graphify update . --force` at the end (CLAUDE.md rule 3 — a 58-file module is deleted, so `--force`
is mandatory); a memory note; a docket record; removal of the `inventory-api` re-export at `gangland-api/pom.xml:42`
(the table adds Oriel there but never retires the old line; `shop-api` at `:50` stays until WS4); and retiring the
`Settings` getters for `settings.yml`'s `Inventory:` block in the same commit that deletes lines 150-163.

## Things I could not verify

- Whether `ChestMenu.rerender()` suffices for any *specific* GLW view — I read both implementations and the 20 call
  sites, not each view's render body. B3 asks for a per-view decision, not a blanket one.
- Whether `RequirementRegistry` has a consumer-reachable in-memory implementation (C5 assumes one exists beside
  `InMemoryFilterRegistry`; only the interface was read).
- The Oriel cross-docket findings (`cross-docket-2026-09-10/oriel/findings/*.txt`, 9 files) — the plan skipped them
  too and correctly flags them for G0. They cover `menu-chest`/`menu-config`/`menu-core`, the classes B3/B4/C5 lean
  on. Read before G0 signs off.
- No compile was attempted in either repo; every ordering claim is static analysis. The plan's instinct (§8 risk #3)
  that the per-module `mvn clean install` is the real gate is right — B2 proves greps alone miss things.
- Gangland's bug docket: accepted the plan's "zero inventory/menu entries" without re-grepping the 464 findings.

---

## Re-review (after rework)

Verdict: **PASS WITH FIXES** — all five blockers are closed, the nine corrections landed, and §0b is an honest
map rather than a restatement. Two fixes below are in-place edits, not re-plans; neither blocks an executor start
on G0.

| ID | Status | Evidence in the reworked plan |
|---|---|---|
| B1 shop-api vs G6 | **resolved** | G6 renamed "cutover (after WS4 consumer gate)", lifted out of WS2's sequence, and gated on a reactor-wide grep rather than an assumed order (§4 G6:279-292). Rejected alternative recorded as D6 for the audit trail. |
| B2 three domain tests | **resolved** | Edited in the same commit as the signature change, with the three file:line sets, and reclassified from "new coverage" to the regression guard (§4 G2 step 10:193-202, §7:332-339). |
| B3 no `MenuFlow.rerender()` | **resolved (tracked, not fixed)** | New §3 row 5 with both lossy substitutes and all 20 call sites; promoted to risk #1; top-priority Oriel ask; per-view decision explicitly deferred to execution rather than guessed (§3:138, §8 risk 1, §10:438). Correct handling — the fix is Oriel's. |
| B4 `OpenMenuTracker` unpublished | **resolved** | §3 row 4 now states it is internal-only and that nothing can pull it; `/glw debug inv-data` is **deleted**, not "replaced" (§4 G3 step 17:230-233), and §5 says there is nothing to update in `commands.json`. |
| B5 `Enchanted: true` | **partially** | The fact is accepted and the 4 slots are handled — but by a heavier route than needed, on a false premise. See N1. |
| C1 Panel generics | **resolved** | Risk deleted; the three real signature deltas (`size→rows`, dropped `Player`, builder-not-handler) carried into G4 step 18:244-246. |
| C2 `back()` empty stack | **resolved** | New §3 row 6 with both line refs, Oriel ask, and the consumer-side `boolean root` fallback applied to root-panel buttons (§4 G4 step 18:250-251). |
| C3 Barter/Sell are deposit redesigns | **resolved** | Own [L] step naming `DepositSlotComponent` + `snapshotDeposits/consumeDeposits/returnDeposits`, a three-part survival acceptance check, two new tests, and the A3 ask re-filed WS4→WS2/G4 (§4 G4 step 19:252-260, §7:348, §10:441). |
| C4 placeholders | **resolved** | §3 row 8 splits the four token families correctly and names the adapter Gangland must write; §4 G2 step 8 folds that adapter into the same step as the source rewrite rather than double-counting it. |
| C5 17-arg `MenuConfigService` | **resolved** | Full argument list + the four unpublished ones enumerated (§3 row 2); G1 step 6 resized [M]→[L]; top Oriel ask. See N2 for the scheduling consequence. |
| C6 file lists | **resolved** | `bank/bounty/phone` claim removed, `PlayerInventoryListener` removed as a census invention, `ReloadPlugin`/`FilterCommand`/wand trio/`BountyAspect`/`ConditionalSlotParser` added, npc-shops list now the real 16+2 (§4 G3 steps 14-15, G4 step 18). |
| C7 unique-item config home | **resolved** | New `unique_items.yml` side-file with its own ~20-line reader, tied back to `GameplayConfig:259-260` (§4 G3 step 12:211-217, §5:315). |
| C8 SNAPSHOT | **resolved** | `<oriel.version>0.7.0-SNAPSHOT</oriel.version>` in §2 with the release-vs-SNAPSHOT question carried to §13. |
| C9 drift | **resolved** | pom 40-43, plugin.yml 7-9, `clearInventories()` at 197-204, and "no `commands.json` entry exists" all corrected. |
| S1–S4 | **applied** | Keystone 1.9.2 with the H5-deprecation FYI (G0 step 1), no converter script, `applyFillIfEmpty` booked as a deletion (G4 step 20), one shared `HeadMaterialSources` default (G3 step 11). |

### New issues the rework introduced

**N1 (fix in place, and it is a net simplification).** D5, risk #7 and the "Declarative NBT" Oriel-roadmap row all
rest on `MIGRATION-FROM-GLW.md:94,391` — which is **stale in exactly the same way line 392 was**. Oriel's chest item
schema already parses declarative NBT today: `ItemDefinitionLoader.java:485` (`parseEnchantments`, XSeries-resolved,
implementation at `:691-705`), `:487` `custom_model_data`, `:491` `unbreakable`, `:495` `durability`, `:498`
`flags`, applied at `:545-547,570`. So the four slots are a §7-renames-table row — `Enchanted: true` →
`enchantments: { …: 1 }` plus `flags: [HIDE_ENCHANTS]` — not a code-registered `ItemProviderRegistry` source. Delete
D5 (a decision put to the user that has an obvious default is the mirror of the problem B5 flagged), delete risk #7,
delete the NBT row from §10, and extend the §10 doc-fix ask to cover line 391 as well as 389-394.

**N2 (fix in place).** G1 step 6 now reads "blocked on the top Oriel ask … or the gate slips" (§4:173-176), but G0 —
the only Oriel-side gate — has three steps and none of them builds the `forConsumer` factory. As written, WS2's
first Gangland gate is not independently green, which is the one thing contract C1's "WS2 first" needs. Add the
factory to G0 as step 2b [M]; it is Oriel-side work with no Gangland prerequisite, so it costs ordering, not time.

**N3 (minor).** G5 step 24 retires `gangland-api/pom.xml:42`'s inventory-api re-export, but `:50` still re-exports
`shop-api`, which still declares its own inventory-api dependency until WS4 — so modules keep seeing inventory-api
transitively and the step enforces nothing until the G6 cutover. Keep it (hygiene), but say so rather than implying
the re-export is closed at G5.

### Are the Oriel asks executor-ready?

Mostly yes — five of the eight rows name the insertion point, the size and the gate they block, which is what an
Oriel executor needs (`MenuFlow.rerender()` "beside `switchInternal`"; `back()`'s literal one-line fix;
`OpenMenuTracker` "one `registerServices()` line"). Three need tightening before hand-off:
1. **`MenuConfigService.forConsumer`** — says "resolves the published 13 + constructs/locates the 4 internally" but
   leaves the two real design questions unanswered: which plugin instance `MenuCommandRegistrar` registers a
   consumer's menu commands under (`MenuCommandRegistrar.java:44` takes the plugin), and that `CooldownService` is
   Keystone's, not Oriel's, so the factory must take it or resolve it from the caller. Also state the expected
   folder contract — `MenuConfigService extends FolderLoader` with `MENUS_FOLDER`, so a consumer gets
   `plugins/<Consumer>/menus/`, which is D2's whole premise.
2. **Anvil-driven paginated search** — "complete/document `PaginatedControlActions.java`'s path" conflates a doc
   task with a feature task; the S-M size admits it. G0 step 2(b) resolves which it is; the ask should be written
   after that spike, not before.
3. **Declarative NBT row** — delete per N1.

With N1–N3 applied the plan is executor-ready. No further review round needed on my account.
