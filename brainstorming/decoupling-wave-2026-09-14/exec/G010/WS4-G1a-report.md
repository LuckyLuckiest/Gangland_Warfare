# WS4 G1a report — shop-api deleted, keystone-shop provided, admin UI relocated

Batch 6a of the 0.10.0 decoupling-wave execution. Spec: `brainstorming/decoupling-wave-2026-09-14/plans/WS4-shop.md`
§4 G1a steps 7-12 (+ §0, §0b, §2, §3, §0d), with the coordinator's step-7-through-12 corrections applied verbatim
(keystone.version already 1.11.0, "Oriel" = keystone-inventory not touched this gate, module-owned settings YAML
convention, Messages untouched). Worktree: `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, based on
164d549e (WS2 G5). No commits made — per LEAD-RULES.md the worktree stays uncommitted for the coordinator to
commit at the gate.

## Summary

`gangland-ui/shop-api` (37 main + 8 test files) is deleted. Its 26 headless classes were already promoted into
Keystone's `keystone-shop` 1.11.0 in Keystone's own G0 (confirmed done before this batch started — 27 classes incl.
`DefaultShopDisplayResolver` verified present in the `~/.m2` jar and in the built `Keystone-1.11.0.jar` plugin jar
used for the smoke run). This batch is the Gangland-side G1a: `keystone-shop` wired in as `provided` everywhere
(never re-exported, B2), the 9 UI-coupled admin-editor files relocated bodily into `gangland-impl`'s new
`shop/admin/{view,listener}` package (still on the OLD inventory-api, completely unchanged — G1b's job), a new
`ShopAdminOpener` seam (B1) lets `npcshops` open the admin editor without naming a `gangland-impl` type, and the
Trader/Banker NPC settings blocks moved out of core `settings.yml` into module-owned YAML shipped inside the
npc-shops jar.

**Gate: PASS.** Whole-reactor `mvn clean install` green (815 tests, 0 failures/errors/skipped). C9 shading audit
clean. Console smoke PASS (0 errors, 0 faults, npcshops loads, both new module YAMLs extracted correctly at boot) —
with one important correction to the plan's framing: **`/glw shop create|list|title|remove` turned out to be
player-only commands**, not console-drivable as assumed; see "Smoke" below.

## File-by-file

### Deleted (whole module)
- `gangland-ui/shop-api/` — all 37 main files + 8 test files + `pom.xml`. Removed from `gangland-ui/pom.xml`'s
  `<module>` list.
- `gangland-api/.../file/configuration/shop/GanglandShopDisplayResolver.java` — replaced by Keystone's
  `DefaultShopDisplayResolver` (identical constructor signature, verified via `javap`).
- `gangland-impl/src/test/.../GanglandShopDisplayResolverTest.java` — pinned T-R4 ("live name before pristine")
  against the now-deleted class; the class it tested moved to Keystone's `keystone-shop` in G0, so this exact
  behavior needs its own pin there. **Not independently verified from this repo** — the sibling `E:\Programming\
  java\Keystone` checkout was on an unrelated branch (`fix-brigadier-alias-trees`), not the keystone-shop work, so
  I could not confirm a replacement test exists on the Keystone side. Flagging this as a real gap for the
  coordinator/clerk rather than asserting it's covered.

### Relocated (byte-for-byte content, package/import changes only)
| Old path (`gangland-ui/shop-api/…`) | New path | Import changes |
|---|---|---|
| `shop/view/ShopAdminFlow.java` | `gangland-impl/…/shop/admin/view/ShopAdminFlow.java` | `BarterCategory`/`SellCategory`/`ShopDefinition`/`ShopEditedEvent` → `keystone.shop.*` |
| `shop/view/ShopAdminFlowSession.java` | `…/shop/admin/view/ShopAdminFlowSession.java` | `keystone.shop.*` wildcard + `event.ShopEditedEvent` |
| `shop/view/ShopAdminView.java` | `…/shop/admin/view/ShopAdminView.java` | `BarterCategory`/`EntryKind`/`SellCategory`/`ShopItemEntry`/`message.ShopDisplayResolver`/`message.ShopMessageContract` → `keystone.shop.*`; `config.ShopUiSettings` unchanged; inventory-api imports unchanged |
| `shop/view/PriceEditorView.java` | `…/shop/admin/view/PriceEditorView.java` | only shop-domain import is `config.ShopUiSettings`, unchanged |
| `shop/view/SellCategoryItemsAdminView.java` | `…/shop/admin/view/SellCategoryItemsAdminView.java` | `SellCategory`/`message.ShopDisplayResolver`/`valuation.CategorySellValuator` → `keystone.shop.*` |
| `shop/view/BarterCategoryItemsAdminView.java` | `…/shop/admin/view/BarterCategoryItemsAdminView.java` | `BarterCategory`/`message.ShopDisplayResolver`/`valuation.CategorySellValuator` → `keystone.shop.*` |
| `shop/listener/ShopAdminListener.java` | `…/shop/admin/listener/ShopAdminListener.java` | `view.ShopAdminFlow`/`view.ShopAdminView` → `admin.view.*` (sibling relocation, not keystone) |
| `shop/listener/SellCategoryAdminListener.java` | `…/shop/admin/listener/SellCategoryAdminListener.java` | `view.SellCategoryItemsAdminView` → `admin.view.*` |
| `shop/listener/BarterCategoryAdminListener.java` | `…/shop/admin/listener/BarterCategoryAdminListener.java` | `view.BarterCategoryItemsAdminView` → `admin.view.*` |
| `shop/config/ShopUiSettings.java` | `gangland-api/…/shop/config/ShopUiSettings.java` | **unchanged FQCN** — byte-identical, only the hosting module moved (S2/B5) |
| `shop/handler/ShopEditPersistenceHandler.java` | `gangland-impl/…/shop/handler/ShopEditPersistenceHandler.java` | `ShopRegistry`/`event.ShopEditedEvent`/`message.ShopMessageContract` → `keystone.shop.*` (S1: stayed in impl, only `keystone-bean` user among the shop files) |

None of the 8 `host.rerender()` call sites in the relocated `ShopAdminView.java`/`PriceEditorView.java` were
touched — still `MultiPanelInventory.rerender()`, exactly as the coordinator specified (G1b's job).

### New files
- `gangland-api/.../shop/ShopAdminOpener.java` — B1 seam, `void openAdmin(Player, ShopDefinition)`, signature
  verified against the two real call sites it replaces (`ShopViewOpenerImpl.openFor`'s sneak-admin branch,
  `ShopViewOpenerImpl.openAdminView`'s `/glw shop edit` path).
- `gangland-impl/.../shop/ShopAdminOpenerImpl.java` — the only implementation, wraps the relocated `ShopAdminFlow`.
- `gangland-features/gangland-npc-shops/src/main/resources/npc/trader_settings.yml`,
  `npc/banker_settings.yml` — module-owned defaults, same shape as `npc/trader_traits.yml`.

### Modified — poms
- Root `pom.xml`: `shop-api` dependencyManagement entry removed; `keystone-shop` added right after
  `keystone-inventory` (`${keystone.version}`, `provided`).
- `gangland-api/pom.xml`: `shop-api` compile-scope re-export removed; `keystone-shop` added to the Keystone
  `provided` block (B2 — not re-exported, unlike the old shop-api pattern).
- `gangland-impl/pom.xml`, `gangland-features/gangland-npc-shops/pom.xml`: `shop-api` → `keystone-shop`.
- `gangland-ui/pom.xml`: `<module>shop-api</module>` line removed.
- `gangland-features/cops-n-crooks/pom.xml`: `shop-api` dependency **removed entirely**, not swapped — a
  reactor-wide grep found zero actual `.java` imports of any shop-api type in cops-n-crooks; this was a dead pom
  entry, not mentioned in the plan, discovered and cleaned up as build hygiene during the pom pass.

### Modified — npc-shops (20-file mechanical import rename)
`sed 's/org\.luckyraven\.gangland\.shop\./org.luckyraven.keystone.shop./g'` across every file a reactor-wide grep
found importing the old package: `TraderCommand`, `TraderCreateCommand`, `TraderEditCommand`,
`TraderEditShopCommand`, `TraderModuleConfig`, `TraderBarterEvent`, `TraderBuyRequestEvent`,
`TraderBarterListener`, `TraderBuyListener`, `TraderSellListener`, `ShopViewOpenerImpl`, `TraderSettings`,
`BarterView`, `NegotiationView`, `QuantitySelectorView`, `SellView`, `ShopView`, `TraderFlow`,
`TraderFlowSession`, `BarterViewItemSurvivalTest`, `SellViewItemSurvivalTest` — 20 files, 100% mechanical except
2 hand-fixed exceptions:
- `TraderSettings.java` — its `config.ShopUiSettings` import was reverted back to `org.luckyraven.gangland.shop.*`
  (the sed swept it into `keystone.shop.config.ShopUiSettings`, which doesn't exist — `ShopUiSettings` kept its
  Gangland FQCN per S2/B5).
- `TraderModuleConfig.java` / `ShopViewOpenerImpl.java` — `ShopAdminFlow` field/param → `ShopAdminOpener`
  (`org.luckyraven.gangland.shop.ShopAdminOpener`), and `adminFlow.start(...)` → `adminFlow.openAdmin(...)` in
  `ShopViewOpenerImpl`'s two call sites.
- `TraderModuleConfig.java` also had a **stray, unused** `import …shop.GanglandShopDisplayResolver` (a leftover
  that pre-dated this batch, never referenced in the method bodies) — removed.
- `TraderModuleConfig.traderSettings(...)` / `BankerModuleConfig.bankerSettings(...)` bean methods gained a
  `FileManager` parameter, threaded into the new `TraderSettingsImpl(fileManager)` / `BankerSettingsImpl
  (fileManager)` constructors (see settings split below). The pre-existing `@SuppressWarnings("unused") Settings
  settings` ordering-edge parameter was kept as-is (feedback_bean_ordering_via_params.md) since
  `getMaxModeMultiplier()`/fill-name/fill-item still lazily delegate to core `Settings`.

### Modified — gangland-impl commands
`ShopCommand`, `ShopCreateCommand`, `ShopEditCommand`, `ShopListCommand`, `ShopRemoveCommand`,
`ShopTitleCommand` — mechanical import retarget: `ShopRegistry`/`ShopDefinition` → `keystone.shop.*`;
`ShopAdminFlow` → the relocated `gangland.shop.admin.view.ShopAdminFlow` (these commands stay wired to the real
flow directly, per `ShopConfig`'s own javadoc — only the module boundary needs the `ShopAdminOpener` seam).
`GanglandShopMessages.java`: `ShopMessageContract` import → `keystone.shop.message.*`.

### Rewritten — `ShopConfig.java`
10 headless `@Bean`s retargeted at `keystone.shop.*` constructors (all verified via `javap` against the actual
`keystone-shop-1.11.0.jar` — every constructor signature matched what was already being called, no call-site
changes needed beyond the import): `ShopYamlReader`, `ShopYamlWriter`, `ShopMessageContract` (still
`GanglandShopMessages`), `ShopDisplayResolver` (now `DefaultShopDisplayResolver`), `ShopPurchaseService`,
`ShopBarterService`, `ShopSellService`, `SellValuator`/`CategorySellValuator`, `CategoryBarterValuator`,
`ShopRegistry` (constructor now takes `JavaPlugin` not `Gangland` — Keystone can't reference a Gangland type;
`Gangland extends JavaPlugin` so the existing `new ShopRegistry(gangland, ...)` call needed no change). The 5
admin-view beans (`priceEditorView`, `sellCategoryItemsAdminView`, `barterCategoryItemsAdminView`,
`shopAdminView`, `shopAdminFlow`) kept their exact wiring, now pointing at the relocated `admin.view.*` classes.
New `shopAdminOpener(ShopAdminFlow)` bean wraps it in `ShopAdminOpenerImpl`.

### Settings split (step 11)
6 `Trader:` keys (excluding `Max_Mode_Multiplier` per B5) and all 4 `Banker:` keys moved out of core
`settings.yml` into the module-owned YAMLs:

| Old core key | New location | Old Settings.java getter (deleted) |
|---|---|---|
| `Trader.Respawn_Cooldown` | `npc/trader_settings.yml: Respawn_Cooldown` | `getTraderRespawnCooldownSeconds()` |
| `Trader.Head_Track_Radius` | `npc/trader_settings.yml: Head_Track_Radius` | `getTraderHeadTrackRadius()` |
| `Trader.Fallback_Trait_Id` | `npc/trader_settings.yml: Fallback_Trait_Id` | `getTraderFallbackTraitId()` |
| `Trader.Sell.Max_Offer_Slots` | `npc/trader_settings.yml: Sell.Max_Offer_Slots` | `getTraderSellMaxOfferSlots()` |
| `Trader.Sell.Mood_Per_Sale` | `npc/trader_settings.yml: Sell.Mood_Per_Sale` | `getTraderMoodPerSale()` |
| `Trader.Tip_Amount` | `npc/trader_settings.yml: Tip_Amount` | `getTraderTipAmount()` |
| `Banker.Head_Track_Radius` | `npc/banker_settings.yml: Head_Track_Radius` | `getBankerHeadTrackRadius()` |
| `Banker.Max_Health` | `npc/banker_settings.yml: Max_Health` | `getBankerMaxHealth()` |
| `Banker.Invulnerable` | `npc/banker_settings.yml: Invulnerable` | `isBankerInvulnerable()` |
| `Banker.Fallback_Tier_Id` | `npc/banker_settings.yml: Fallback_Tier_Id` | `getBankerFallbackTierId()` |
| `Trader.Max_Mode_Multiplier` | **stayed core**, new `settings.yml: Shop.Max_Mode_Multiplier` | `getTraderMaxModeMultiplier()` **renamed** → `getShopMaxModeMultiplier()` |

Reactor-wide grep for every one of the 10 deleted getters, before deletion: the **only** callers were
`TraderSettingsImpl`/`BankerSettingsImpl` in npc-shops — no other module or gangland-impl code touched them. Both
classes now implement `BeanLifecycle` (`onInitialize` re-reads on `/glw reload`, matching the existing
`TraderTraitsLoader`/`BankTiersLoader` pattern) and read from a `FileHandler` obtained via
`fileManager.checkFileLoaded("trader_settings"|"banker_settings")` + `fileManager.getFile(...)`, registered in
`NpcShopsYamlConfig` alongside `trader_traits`/`bank_tiers`. `getMaxModeMultiplier()`/`getInventoryFillName()`/
`getInventoryFillItem()` in both classes still delegate to core `Settings` (unchanged, generic/shared knobs).
`GanglandShopUiSettings.getMaxModeMultiplier()` updated to the renamed `Settings.getShopMaxModeMultiplier()`.

**Upgrade note (worth a docket entry, not filed by me — see "For the clerk" below):** a server upgrading from an
older revision keeps its old `Trader:`/`Banker:` blocks in `settings.yml`; the new `Settings.java` no longer reads
them (confirmed live in the smoke run — `settings.yml:28:1 at Trader | unknown key 'Trader'` /
`... at Banker | unknown key 'Banker'` warnings, harmless but real). Any values a server owner had customized
there are **not auto-migrated** — they silently revert to `npc/trader_settings.yml`'s/`npc/banker_settings.yml`'s
shipped defaults unless the owner manually copies them over after upgrading. `settings.yml`'s own `Config_Version`
sweep doesn't touch this since these are now orphaned keys, not migrated ones.

## Build

Per-module targeted builds first (`gangland-api`, `gangland-impl`, `gangland-npc-shops`, each `-am`) — all green.
Then the full reactor:

**`mvn clean install` (whole reactor, exit code 0) → BUILD SUCCESS.**
Aggregate test count, summed fresh from every module's own `target/surefire-reports/*.txt`:
**Tests run: 815, Failures: 0, Errors: 0, Skipped: 0** (152 test-report files).

**Count delta vs. the last gate (WS2 G5, `WS2-G5-report.md`: 875 whole-reactor):** **-60 tests**, and this is a
*complete, verified* accounting, not an estimate — `git show HEAD:<path>` on each of the 8 deleted shop-api test
files/classes gives exactly `7 + 16 + 7 + 6 + 9 + 6 + 8 = 59` `@Test` methods (`ShopDefinitionTest`,
`ShopYamlReaderTest`, `ShopBarterServiceTest`, `ShopPurchaseServiceTest`, `ShopSellServiceTest`,
`CategoryBarterValuatorTest`, `CategorySellValuatorTest`) plus the 1 deleted `GanglandShopDisplayResolverTest` =
**60**, exactly matching 875 − 815. These moved to `keystone-shop`'s own test suite in Keystone's G0 (confirmed
done, per the coordinator) — with the one caveat noted above about `GanglandShopDisplayResolverTest`'s T-R4 pin
not being independently re-verified from this repo.

## C9 shading audit

`unzip -l target/gangland_warfare-0.10.0.jar | grep keystone/shop` → **0 matches, empty**. B2's provided-scope
discipline held — no Keystone shop code leaked into the shaded core jar. Also checked the npc-shops module jar
(`target/modules/gangland-npc-shops-0.10.0.jar`) for the same reason: also 0 matches — `keystone-shop` stays
provided (server-supplied via `Keystone-*.jar`) in the module too, never shaded.

## Smoke

Console-drivable half of M-SHOP-1, run against the real test server
(`E:\Documents\Minecraft\Test Server`) via `brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`. Added a new
permanent scenario `ws4-g1a-shop-boot` to `scenarios.json` (npcshops deployed, probes `/glw shop
create|list|title|remove`, `/glw trader`, `/glw banker`, `/glw debug inv-data`). Temporarily repointed
`scenarios.json`'s `paths.repo_dir`/`paths.keystone_jar_dir` from the stale `gangland-0.9.2`/`keystone-1.10.0`
worktrees to `gangland-0.10.0`/`keystone-1.11.0` for this run, then **restored both back** to their prior values
afterward (the new scenario row itself was left in place, matching the `npcshops-boot`/`ws2-g5-boot` precedent of
growing the matrix permanently).

**Result: PASS.** `boot=True stop=True modules=['npcshops'] errors=0`. `Loaded module npcshops 0.10.0`,
`Runtime modules: 1 loaded, 0 fault(s)`, zero ERROR lines in the whole log. Both new module YAMLs were correctly
extracted from the module jar at boot (`Created file: ...\npc\trader_settings.yml` / `...\npc\banker_settings.yml
(from module resources)`), and confirmed present on disk afterward — end-to-end proof the settings-split mechanism
works at runtime, not just compiles. The only WARN lines were the two expected `unknown key 'Trader'`/`'Banker'`
lines discussed above (the deployed test server's `settings.yml` is a stale copy the harness never touches).

**Important correction to the plan's framing, discovered during this run:** `/glw shop create|list|title|remove`,
`/glw trader`, and `/glw banker` are **all player-only commands** ("You need to be a player to use this!" for
every one of them in the transcript) — they are **not** actually console-drivable, contrary to how the dispatch
described "the console-drivable half of M-SHOP-1". The smoke row still proves real value (module boot, bean-graph
construction for `ShopConfig`/`TraderModuleConfig`/`BankerModuleConfig` including the new `ShopAdminOpener`/
`TraderSettingsImpl`/`BankerSettingsImpl` wiring, zero `NoSuchMethodError`/`NoClassDefFoundError`/
`ClassCastException`/`at org.luckyraven.gangland.shop`/`at org.luckyraven.keystone.shop` stack frames — exactly
the regression class this batch risks), but it does **not** exercise any shop command's actual body. All of
`/glw shop create|list|title|remove`, `/glw shop edit`, trader buy/sell/barter and banker deposit/withdraw now sit
in `exec/G010/WS2-manual-checklist.md`'s new "WS4 G1a" section (rows 28-33; the file's title was widened to "WS2/
WS4" to reflect this).

## Docket notes for the clerk

- **CT-06** (Slot never validated against Size), **CT-07** (purchase overflow drops item, test-pinned "#7"),
  **CT-23** (concurrent admin saves last-writer-wins) — plan §11's three rows now pinned in `keystone-shop`
  (moved with the headless classes in Keystone's G0). Not re-verified from this repo (Keystone worktree here was
  on an unrelated branch) — the clerk should confirm the pins landed in the Keystone repo before updating status.
- **CT-04** (ESC on `/glw shop edit` strips comments), **CT-05** (save renumbers Slot), **CT-22** (price edit
  mutates shared ItemStack) — unaffected by this batch, still open, still in the relocated `gangland-impl` files
  (not moved to Keystone since they're UI-layer, G1b territory at best).
- **New finding, not previously triaged**: the `Trader:`/`Banker:` → module-YAML settings split has no
  auto-migration path. An upgrading server's customized `Trader:`/`Banker:` values in `settings.yml` silently
  revert to `npc/trader_settings.yml`'s/`npc/banker_settings.yml`'s shipped defaults, with only a passive
  `unknown key` warning as a signal. Confirmed live in the smoke log. Recommend a `triage/` entry
  (`settings-trader-banker-no-migration.txt`) if the coordinator wants this docketed before 0.10.0 ships — I did
  not add it myself since the docket-update step is explicitly the clerk's per this batch's dispatch.
- `GanglandShopDisplayResolverTest.java`'s T-R4 pin ("live name before pristine") — deleted here since its class
  moved to Keystone; **not confirmed** re-pinned on the Keystone side from this session. Worth the clerk checking
  keystone-shop's own test suite for an equivalent `DefaultShopDisplayResolver` test before treating this as
  covered.

## Concerns / left out

- `/glw shop create|list|title|remove` player-only discovery (above) — the `scenarios.json` row still passes
  because its `expect` block only asserted boot/fault/error-string conditions, never command *success*, so no
  false-green risk, but the row's own docstring in the file states the intended scope accurately (I wrote it after
  discovering this, not before).
- `GanglandShopDisplayResolverTest.java` deletion — see docket note above; genuinely uncertain whether the T-R4
  behavior still has independent test coverage anywhere in this reactor's dependency chain.
- Docs sweep (plan steps 16-18, if any) — out of scope for this hand-back; the coordinator's dispatch named only
  steps 7-12 for G1a.
- `/glw shop edit` and every trader/banker interactive flow — untested live this batch (no in-game player in a
  console-only run); now tracked as WS2/WS4 manual-checklist rows 28-33.

## Deliverables

- `exec/G010/WS4-G1a-report.md` — this file.
- `exec/G010/WS4-G1a-package.diff` — `git add -N . && git diff HEAD` (then `git reset`, worktree left uncommitted
  and clean of intent-to-add). 4553 lines. Deleted+relocated files appear as delete+add pairs (paths/packages
  changed), not renames.
- `exec/G010/WS2-manual-checklist.md` — retitled "WS2/WS4 manual checklist", new "WS4 G1a" section (rows 28-33)
  added.
- `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` — new permanent `ws4-g1a-shop-boot` scenario;
  `paths.repo_dir`/`paths.keystone_jar_dir` were temporarily repointed for the run and restored afterward.
