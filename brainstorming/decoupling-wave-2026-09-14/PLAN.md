# Decoupling wave — consolidated plan (2026-09-14)

Status: **PLANNING ONLY.** Nothing in this wave has been implemented. Sources: `plans/WS1..WS6-*.md` (Sonnet
planners, reworked after review), `reviews/REVIEW-WS1..WS6.md` (Opus reviewers), `census/*.md` (Haiku).
**WS7/WS8 (gadget wave, 2026-09-16)** were planned the same way in `../gadget-wave-2026-09-16/` (README with
contracts K1–K9, census, plans, reviews, SUMMARY) and merged here as §9a/§9b; they execute FIRST, on Gangland 0.9.2.
Orchestrator: Fable. Every `file:line` claim below is quoted from a plan or review that verified it with graphify.
Artifact (board + decisions register, db collections `decisions`/`workstreams`):
https://claude.ai/code/artifact/96251df4-f5b3-49a6-9f5f-9afb94407faf — page source `board.html` in this folder.

## 0. The ask, in one table

| WS | Feature today | After the wave | Owner repo(s) |
|---|---|---|---|
| WS1 | `gangland-ui/scoreboard-api` + impl lifecycle + `settings.yml` `Scoreboard:` + `scoreboard.yml` | A standalone Keystone-powered plugin in a new sibling repo; Gangland has no scoreboard code or settings | new repo, Gangland |
| WS2 | `gangland-ui/inventory-api` (58 files, two god nodes) | Deleted; every Gangland menu is a `keystone-inventory` `ChestMenu`/`MenuFlow`; Gangland keeps its own YAML inventory dialect and gains no Oriel dependency (re-targeted 2026-09-17, R10) | Keystone, Gangland |
| WS3 | `gangland-ui/lootchest-api` + `hologram-api` + impl wiring | `gangland-lootchest` runtime module; holograms are a Keystone service | Keystone, Gangland |
| WS4 | `gangland-ui/shop-api` (37 files) | Headless `keystone-shop` (28 files move as-is); 9 admin-view files rebuilt on Oriel in `gangland-impl` | Keystone, Gangland |
| WS5 | `gangland-domain` `gang.*` (40 files) + 167 impl importers | `gangland-gang` runtime module; identity (`User`) stays core; `gangland-domain` deleted | Gangland |
| WS6 | `gangland-api` 1.0 (module contract only) | `gangland-api` 2.0: `GanglandApi` facade on the `ServicesManager`, service-table doc, `japicmp` wiring | Gangland |
| WS7 (gadget wave, 2026-09-16) | Jetpack identity + config live in Bartizan (`wearables.yml` jetpack entry, `Extra_Tags:`); gadget `Plugins: [Bartizan]` fail-fast | Jetpack fully gadget-owned (`ItemKind.JETPACK`, `items/jetpacks.yml`, own converter/serializer/refresher, NBT identity, `/glw jetpack give`); gadget's Bartizan dependency is soft; jetpack residue leaves Bartizan | Gangland 0.9.2, Bartizan 0.4.0 |
| WS8 (gadget wave, 2026-09-16) | No gadget beyond car + jetpack | The per-gadget pattern WS7 proves, the grappling hook shipped, an 8-candidate shortlist for the user to pick from | Gangland 0.9.2 |

## 1. Repos, branches, versions

| Repo | Today | This wave | Why |
|---|---|---|---|
| Keystone | 1.9.2 on `phase-h9-host-api` | **1.10.0** on a new phase branch: modules `keystone-hologram`, `keystone-shop`; `CLAUDE.md` line 101 amended once (one sentence, shared by WS3/WS4). Then **1.11.0**: `keystone-inventory` — Phase E6, planned in `docs/extraction-roadmap.md` on branch `docs-inventory-roadmap`; the layering line amended again (basic inventory mechanism in Keystone, advanced menu features in Oriel) (R10, 2026-09-17) | new modules = minor |
| Oriel | 0.7.0 (pinned to Keystone 1.7.0) | **0.8.0** G0 (Keystone **1.9.2** bump + `forConsumer`) done pending review, **off the wave's critical path since the re-target (R10)** — the Keystone bump stays useful for Oriel's own rebase, `forConsumer` is no longer a Gangland need. Oriel's next version rebases `menu-core`/`chest` onto `keystone-inventory` and deletes six copy-pasted holder/listener trios, on Oriel's own track after Keystone 1.11.0 | Gangland no longer names an Oriel type |
| Bartizan | 0.3.0 | **0.4.0** on its own branch (WS7 G0): the jetpack `wearables.yml` entry, `WearableAddon.legacyJetpackToExtraTags` + its test and migration.md §3.4 leave; `Wearable`/`Extra_Tags` stay generic. Gangland's pin 0.1.0 → 0.4.0 at WS7 G1 | Gangland-specific residue leaves (K3) |
| Gangland Warfare | 0.9.1 on `0.9.1` | **0.9.2** on branch `0.9.2` off 0.9.1 first (WS7 → WS8; `GanglandApi.VERSION` 1.0 → **1.1** for `Settings.isBartizanAvailable()`, additive); then **0.10.0** on branch `0.10.0` off 0.9.2; **G0 = first commit bumps `GanglandApi.VERSION` and every `module.yml` `Host_Api` to 2.0** (R1) | a gadget rehome is a patch; core features leave with their settings (Bartizan precedent → minor bump) |
| `<ScoreboardPlugin>` | — | **0.1.0**, new sibling repo, single Maven module, no `-api` | new product |

Dependency direction after the wave, never reversed: Keystone ← Oriel ← Gangland core ← Gangland modules;
Keystone ← Bartizan; Keystone ← `<ScoreboardPlugin>`. Bartizan and the scoreboard plugin never name a Gangland type.

## 2. Order, gates and the rulings that shaped them

```
Gangland 0.9.2 (gadget wave, no Keystone/Oriel prerequisite): WS7 (jetpack ownership, Bartizan soft) → WS8 (grappling hook)
                                   │  Bartizan 0.4.0 (WS7 G0) runs first; 0.10.0 branches off 0.9.2
                                   ▼
Keystone 1.10.0 (hologram, shop) → Keystone 1.11.0 (keystone-inventory, E6) ─┐
<ScoreboardPlugin> 0.1.0 ─────────────────────────────────────────────────────┼─► Gangland 0.10.0:  G0(api 2.0) → WS1 → WS2 → WS4 consumer → CUTOVER(delete inventory-api)
                                                                                                        → WS3 → WS5 → WS6 → docs / smoke / graph refresh

Oriel 0.8.0 (G0 done pending review) — off the wave's critical path (R10); its own keystone-inventory rebase
runs after Keystone 1.11.0, on Oriel's track, not this wave's
```

Rulings made during review (binding; each is also a line in the decisions register for the user to veto):

| Id | Ruling | Source |
|---|---|---|
| R1 | Gangland `0.10.0` G0 bumps the api to **2.0** in the first commit, so every later removal from `gangland-api` is inside the breaking major and legal in its own workstream | REVIEW-WS3 B3 |
| R2 | `LootChestService`/`LootChestSession` are **WS3**'s (raw Bukkit `Inventory` wrapper); WS2 does not port them; the wand-preview admin screen is WS3's only Oriel ask | REVIEW-WS3 B4 |
| R3 | Deleting `inventory-api` is a shared **cutover gate** after WS4's consumer gate; precondition = grep proving zero importers of `org.luckyraven.gangland.inventory` | REVIEW-WS2 B1 |
| ~~R4~~ | ~~Oriel G0 targets Keystone 1.9.2 now; 1.10.0 later in a small gate~~ — **obsolete, folded into R10** (Oriel is off the critical path entirely; its own Keystone-version gate is no longer this wave's concern) | REVIEW-WS2 D4 |
| ~~R5~~ | ~~Oriel's missing in-place `rerender()` (20 call sites) and the unpublished `OpenMenuTracker` are first-class Oriel 0.8.0 asks~~ — **obsolete, folded into R10** (`rerender()`/tracker are now `keystone-inventory` E6.1/E6.2 requirements — §5's table — not Oriel asks) | REVIEW-WS2 B3/B4 |
| R6 | `gangland-api` 2.0 never re-exports `keystone-inventory` (or Oriel artifacts, moot after R10); a module that builds a menu declares `keystone-inventory` in its own pom (the `bartizan-api` rule: a consumer declares what it names) | WS6 §10 disagreement with WS2, arbitrated; reworded 2026-09-17 for R10 |
| R10 | **WS2 re-targeted 2026-09-17.** `inventory-api` moves onto a new Keystone module `keystone-inventory` (Phase E6, 1.11.0) instead of onto Oriel; no Oriel dependency at all. Oriel's 0.8.0 G0 (Keystone 1.9.2 bump, `forConsumer`) stays useful for Oriel's own rebase but is no longer a Gangland need; R4/R5 dissolve into this ruling — `rerender()`/`OpenMenuTracker` become `keystone-inventory` E6.1/E6.2 requirements instead of Oriel 0.8.0 asks | Board ruling W23, artifact 96251df4; `exec/WS2/E6-plan-review.md` |
| R11 | `lootchest-api`'s two importers stay WS3's (R2, unchanged); the nine shop admin views stay WS4 G1b's; WS2 G5 = turf · cops-n-crooks · gadget only; the shared CUT gate runs after WS2 G5 **and** WS3 G3 **and** WS4 G1b (R3's grep unchanged) | `exec/WS2/E6-plan-review.md` Orchestrator rulings (W23) |
| R7 (final, 22:40) | `GangLookupContract`/`RankLookupContract` **move into the gang module with `Gang`/`Rank`** (their signatures name those types — REVIEW-WS5 re-review N1 killed the earlier "interfaces stay in the api" wording). The api exposes gang facts only through the `GangMembership` holder (R9); the facade returns it directly (`GangMembership gangs()`, never null), has **no `ranks()`**, and no `Optional` lookups. Stamped into both plans as §0c | REVIEW-WS5 re-review N1; WS5 §0c, WS6 §0c |
| R8 | WS1 runs **before** WS2 (zero Oriel coupling; both edit `gangland-domain/pom.xml` and `User.java`) | REVIEW-WS1 |
| R9 | One core-owned **`GangMembership` holder** (interface in `gangland-api`, inert default registered by the core, installed by the gang module) serves every core and module site that only needs "is/which gang" facts; it replaces WS5's `MembershipLookupContract`, so gadget, civilians and cops-n-crooks need no `Depends: [gang]` | REVIEW-WS5 S1 |

## 3. Review verdicts

| WS | Verdict | Blockers found → fixed in plan | Re-review |
|---|---|---|---|
| WS1 | PASS WITH FIXES | 5 (missing `keystone-persistence`; two pom removal rows; false "PAPI-only" claim → new D5; US-19 mis-recorded; lost enable-time sweep) — all applied, §0b in the plan | — |
| WS2 | REWORK → reworked → **PASS WITH FIXES** | 5 (inventory-api deleted before WS4 → R3; three domain tests break; no `rerender()` → R5; `OpenMenuTracker` unpublished → R5; `Enchanted:` used → D5) — all applied | N1 Oriel already parses `enchantments:`/`flags:` (`ItemDefinitionLoader.java:485-705`) so WS2-D5 collapses to a YAML rename; N2 `MenuConfigService.forConsumer` must be built in Oriel G0 (step 2b) or G1 is not independently green; N3 `gangland-api/pom.xml:50` still re-exports shop-api until WS4 — executor notes, no further rework |
| WS3 | PASS WITH FIXES | 4 (global `initializeAll()` deletion; `GanglandDatabase` in a module; removals inside 1.x → R1; WS2/WS3 file conflict → R2) — all applied | — |
| WS4 | PASS WITH FIXES | 5 (module code names `ShopAdminFlow` → new `ShopAdminOpener` api seam; `provided` is not transitive → npc-shops declares `keystone-shop`; gate collapse invalid under R3 → G1a/G1b restored; 8 `host.rerender()` calls → depends on Oriel ask R5; `Max_Mode_Multiplier` must stay core) — fix pass running | reviewer simplifications accepted: `ShopEditPersistenceHandler` + `ShopUiSettings` stay Gangland-side (listener-scan gap disappears), trader/banker message migration deferred; re-estimate 6–8 days |
| WS5 | REWORK → reworked → **PASS WITH FIXES** | 6 (cops-n-crooks imports `Gang` via `TurfFriendlyFireListener.java:16-19`; `User`/`UserManager` name `GangSettings`/`Rank`/`Permission`/`Member` (`User.java:17-229`, `UserManager.java:12-139`); a missing contract bean aborts boot (`BeanFactory.java:517-522`); the `MemberManager`→`UserManager` ordering edge is real (`DataConfig.java:69-82`); three missed core consumers (`GanglandDatabase.java:136-145` rank seeding, `BountyIncreaseListener`, `LevelUpListener`); WS6's facade question unanswered → R7) | Ruling R9 adopted from the review: one core-owned `GangMembership` holder (`BankTiers` shape) replaces `MembershipLookupContract`, fixes the boot abort and removes `Depends: [gang]` from gadget and civilians (a gangless server loses turf + mail only); `UserDataInitEvent` instead of a new join listener; estimate ≈ 12 executor-days |
| WS6 | PASS WITH FIXES | 3 (api/module compile direction → final R7 in §0c; `UserLookupContract` lives in `gangland-domain` today, so WS5 G0 must move it first; `unregisterAll` placed after `Gangland.onDisable`'s early return → first statement, `Bartizan.java:47`) — fix pass applied; civilians is the worked YAML example (it already has a loader) | Corrections: `DependencyContainer.getInstance` returns null so the `Optional` is one line (no Keystone ask); the service table must actually be written; a module that already ships YAML is the worked example. Ponytail cuts accepted: no `modules()` accessor, no `japicmp` block this wave; estimate 12–14 h |
| WS7 | PASS WITH FIXES → **PASS** (after §0c) | 4 (`bartizan-api` must stay `provided`; Bartizan-touching method *bodies* throw `NoClassDefFoundError` once soft → extraction + call-site guard; YAML surface + fuel stamping missing → `Jetpack.buildItem`; WS7-D4 added) — all applied | B5/B6/B7: the extraction's wiring (a `@Bean` re-opens the crash via `getDeclaredMethods`; listener + bean conflict; test wording) — applied by the orchestrator in the plan's §0c together with keeping `JetpackItemRefresher`; `isScoped()` and the trait branch deleted; `Host_Api` 1.0 → 1.1; estimate ~11.5 days |
| WS8 | **PASS** | 4 (`ItemKind` shared edit with WS7; fall-damage design must not mirror GD-04; `PlayerFishEvent` floor smoke in G2; estimate 3.1 → 4.5–5 days) — all applied | Residual risks noted: stale shop templates until re-issued; a parachute must not mirror the GD-04/GD-07 shapes |

## 4. WS1 — Scoreboard becomes `<ScoreboardPlugin>`

**Scope.** Delete `gangland-ui/scoreboard-api` (8 files + 2 tests, FastBoard + ViaVersion), every impl wiring
site (`ScoreboardManager`, `ScoreboardLifecycleService`, `PlayerScoreboardListener`, `FileConfig`/`SchedulingConfig`/
`KernelConfig` beans, `ReloadPlugin.scoreboardReload`, the bStats chart, `DownloadResourceCommand`'s toggle,
`RemoveAccountListener`, `UserManager.onPreClear()` in gangland-domain), `User.scoreboard`, the `Scoreboard:`
settings block, `scoreboard.yml`, `Settings.getScoreboardDriver/isScoreboardEnabled`, the `gangland-domain`,
`gangland-impl` and root pom entries, the `fastboard` shade include. Stand up a single-module Keystone-powered
plugin (Bartizan's repo layout: `${revision}` + flatten + Central profile, `depend: [Keystone]`, `softdepend:
[PlaceholderAPI, ViaVersion]`) that reads the **same** `scoreboard.yml` schema (owners copy the file).
**Out:** any new scoreboard feature; an `-api` module (no programmatic consumer exists — PAPI text covers every use).

**Placeholders.** Lines resolve PAPI first, then an optional Keystone `PlaceholderProvider` that Gangland publishes
on the `ServicesManager` via `GanglandPlaceholder.asProvider()` (`PlaceholderHandler.java:71`, same seam as
`ItemVocabulary` at `GanglandContext.java:215`) — so gang/user placeholders keep working without PlaceholderAPI
and no Gangland type crosses the boundary (decision WS1-D5).

**Gates.** G0 repo + naming (S) → G1 port the rendering engine, DriverV3 only (M) → G2 bootstrap, config,
placeholders, enable-time board sweep (M) → G3 remove from Gangland incl. CM-04 fix (M/L) → G4 docs + docket (S) →
G-final combined smoke. Rollback: G0–G2 are a separate repo; G3 is one revertible commit range.

**Docket.** UI-01/UI-02 already fixed (ported as-fixed); CM-04 fixed by G3; UI-17 fixed by a 2-line guard at port
time; UI-16 deleted with DriverV1/V2; US-19 stays open (only one subscriber leaves).
**Estimate.** ~16 h / 2 studio days. **Asks:** none of Oriel/Keystone; WS6 may host the placeholder provider accessor.

## 5. WS2 — inventory-api → keystone-inventory

**Re-targeted 2026-09-17 (R10) — superseded summary; full plan moved out of this file.** The reviewed plan above
(PASS WITH FIXES, still true of its shape) moved every Gangland menu onto Oriel. The board ruled a further step:
the *basic* inventory mechanism — one `InventoryHolder`-identified menu, one listener set, click routing, plain
components, open/close tracking, panel flows and page arithmetic — is generic infrastructure of the same kind as
`keystone-command` or `keystone-hologram`, and ships as a new Keystone module, **`keystone-inventory`** (Phase E6,
Keystone 1.11.0, `docs/extraction-roadmap.md`), instead of living on Oriel. Oriel keeps only the *advanced* menu
product on top (animation, requirements, provider/filter/sort/search registries, deposit slots, YAML loaders, the
editor) and is no longer a Gangland dependency at all — `plugin.yml` keeps `depend: [Keystone]` only, WS2-D1
dissolves. The nine core YAML menus are **not** rewritten onto an Oriel schema; `InventoryParser` and the
`handler.*` action dialect stay in `gangland-impl` (moved to `org.luckyraven.gangland.menu.*` at G3a) and target
`ChestMenuBuilder` instead.

Full current plan: **`plans/WS2-inventory-keystone.md`**. Its own E6 plan review (`exec/WS2/E6-plan-review.md`,
verdict PASS WITH FIXES, orchestrator rulings W23) is folded into both that plan and the Keystone `docs/
extraction-roadmap.md` E6 section already. `plans/WS2-inventory-oriel.md` (the section this replaces) carries a
SUPERSEDED banner and is kept only for its history — none of its Oriel-shaped file lists, decisions or asks
survive the re-target; ownership (R11) sends `lootchest-api`'s 2 importers to WS3 and the 9 shop admin views to
WS4 G1b unchanged from before.

**Gates (summary — see the new plan for the full step list).** K2 Keystone 1.11.0 (E6.1 core → E6.2 flow/pages,
own phase, own review) → G1 Gangland reactor plumbing (no Oriel) → G2 sever the domain inversion (three domain
tests) → **G3a** move the ~40 Gangland-dialect inventory-api files into `gangland-impl` (`org.luckyraven.gangland.
menu.*`), no behaviour change → G3 `InventoryParser`/`handler.*` target `ChestMenuBuilder`, nine YAML menus load
unchanged, `Multi.*` rebuilt on `PagedRegion` → G4 npc-shops (18 files) → G5 turf · cops-n-crooks · gadget → shared
CUT (after WS2 G5, WS3 G3 and WS4 G1b, R3's grep).

**Estimate.** Keystone 1.11.0 5–6 executor-days; Gangland WS2 7–9 days + 0.5 cutover; Oriel 0 days in this wave
(off the critical path, R10). **Docket:** two triage rows recorded fixed-by-WS2 (`InventoryHandler.
SPECIAL_INVENTORIES` static map, static `InventoryRegistry registry` — both leak across reloads); no Gangland
entries otherwise.

## 6. WS3 — Loot chest module + `keystone-hologram`

**Scope.** `Hologram`/`HologramService`/`HologramProtectionListener` (3 files, 346 LoC, ArmorStand) become
`keystone-hologram` (new small module; needs `keystone-bean`; per-consumer instance, no static registry;
`registerProtection(JavaPlugin)` instead of shipping a listener, on ponytail grounds; `ponytail:` note for the
TextDisplay path above the 1.16 floor). Loot chests (31 lootchest-api files + 10 impl files) fold into one runtime
module `gangland-features/gangland-lootchest` (id `lootchest`, mail template: pom scopes, `module.yml`,
`LootChestModule`, `LootChestModuleConfig`, `commands.json` at jar root, `database` package with unchanged table
name, YAML defaults `lootchests/{loot_chests.yml,tiers.yml}` + a module `loot_chest.yml` for the 34-line
`Loot_Chest:` block). `Depends:` none, `Plugins:` none (weapon loot only via `ItemVocabulary`). Two commands switch
from `GanglandDatabase` to `RepositoryRegistry`. Only `GameplayConfig.java:267-290` is deleted — the global
`fileManager.initializeAll()` at `:313-319` (T-11 fix) stays. The chest-opening view is a raw Bukkit `Inventory`
wrapper (`SharedLootInventory`, <30 lines); the admin wand-preview screen is the only Oriel surface.
**Revamp = structural (WS3-D5).** Deferred feature items: safe-cracking input UI (`CrackingSession` has no
production caller — dead, docket LS-02), tier/refill/cooldown redesign, per-tier hologram text.

**Gates.** G0 Keystone hologram (install 1.10.0) → G1 Gangland consumes it, deletes `hologram-api` → G2 module
skeleton + persistence → G3 GUI on `keystone-inventory` (after WS2, re-targeted 2026-09-17 R10 — was "on Oriel")
→ G4 config/messages/commands → G5 deletions, docs, docket, smoke (armor-stand count check on disable) → G6
follow-up: `LootChestEarnGoodsListener`'s `User`/`Level` imports after WS5's repackage.

**Docket.** 21 open ids (UI-13/14/15/33, LS-02/05/11-18/23/25/29-33); LS-30/LS-31 fixed for free in G3; the rest
carried with a `note` row recording the new path. **Estimate.** ~6 executor-days.

## 7. WS4 — Shop system → `keystone-shop`

**Scope.** Option A (WS4-D1): headless `keystone-shop` = the 28 files with no `org.luckyraven.gangland.inventory`
import (model, registry, transactions, valuation, yaml io, event, `DefaultShopDisplayResolver` moved from
`gangland-api`) + all 8 tests, behaviour unchanged; `PaymentHandler` already wraps Keystone `EconomyHandler` 1:1
(`TraderBuyListener.java:103-120`) so a second consumer implements ≤2 interfaces. The 9 UI-coupled files (6 admin
views + 3 admin listeners) are rebuilt on Oriel **in `gangland-impl/shop/admin/`** — `/glw shop` is core, not
npc-shops. `ShopEditPersistenceHandler` needs a listener-scan root the core does not have today
(`GanglandContext.java:84,250` hardcodes `org.luckyraven.gangland`) — plus a test. Keystone `CLAUDE.md:101` gains
one sentence (shared with WS3). `Trader:`/`Banker:` settings and the 41 `TRADER_*`/`BANKER_*` messages move to
npc-shops YAML; the ~27 `SHOP_*` messages stay core. Options B (standalone plugin) and C (inside Oriel — Oriel's
own docs disclaim pricing logic, `USE-CASE-ENABLERS.md:235-246`) are recorded with costs.

**Gates.** G0 Keystone module (independent, can start now) → G1a Gangland headless repoint (inventory-api still
alive, per R3) → G1b the 9 admin views on `keystone-inventory` (needs `MenuFlow.rerender()` — re-targeted
2026-09-17 R10, was "on Oriel, needs Oriel `rerender()`, R5") → `shop-api` deleted last, smoke M-SHOP-1. npc-shops
reaches the core admin editor through a new `ShopAdminOpener` interface in `gangland-api` (REVIEW-WS4 B1).
`ShopEditPersistenceHandler`, `ShopUiSettings` and `Trader.Max_Mode_Multiplier` stay Gangland-side.

**Docket.** CT-04..CT-08 carried (CT-06/CT-07 pinning tests move to Keystone, still red). **Estimate.** ≈ 6–8 days
(REVIEW-WS4 re-estimate: docs, graph, docket, memory and testkit-fixture steps were unbudgeted).

## 8. WS5 — Gang module

**Scope.** Identity stays core, repackaged out of `gang.*` into `org.luckyraven.gangland.user` in `gangland-core`:
`User`, `UserManager`, `Level`, `UserFactory`, and (WS5-D2) `Bounty*`/`Wanted*` (cops-n-crooks imports `Wanted`
from 8 files with no `Depends: [gang]`), plus `Permission*` (WS5-D3, generic node catalogue persisted under
`database/plugin/`). Everything else moves to `gangland-features/gangland-gang` (id `gang`): `Gang`, `GangAlliance`,
`GangManager`, `GangSettings`, `Member*`, `Rank*`, `GangPermissions`, `VaultPermissionBridge`, the 31 gang/rank/ally
commands, `listener/gang`, gang/rank/member persistence (table names unchanged), 5 `keystone-inventory` menus
(re-targeted 2026-09-17 R10, was "5 Oriel menus"), the gang
placeholders, the `Gang:` settings block and Gang/Rank/Ally messages. `gangland-domain` ends 100 % empty and is
deleted. Turf keeps `Integer ownerGangId` (zero signature change) and gains `Depends: [gang, civilians]`; `mail`
`Depends: [gang]`; **also** `gadget` (`GanglandCarGangs` uses `MemberManager`) and `civilians`
(`GangAllyWeaponImpactListener` uses `Gang`/`GangManager`) → `Depends: [gang]`; cops-n-crooks and npc-shops unchanged.
Cross-module compile deps at `provided` scope are the established pattern (turf → civilians; Keystone's single
`ModuleClassLoader`, `ModuleLoader.java:53`).

**Seams.** (a) `UserLookupContract`/`PermissionRegistryContract` stay in the api, core publishes; (b)
`GangLookupContract`/`RankLookupContract` move with `Gang`/`Rank` into the module (**final R7**); every core and
module site that only needs gang facts uses the `GangMembership` holder (**R9**); (c) new narrow `MembershipLookupContract { int gangIdOf(UUID) }` replaces `UserDataLoader`'s
`MemberManager` parameter (this parameter edge is what makes `BeanGraph` order core and module beans correctly —
no manual trick); (d) new `PlaceholderContribution` splits `GanglandPlaceholder` (core: user/level/bounty/wanted;
module: gang/rank/member); (e) existing `CommandContribution` reused for `WaypointGangIdCommand` and the gang/rank
debug branches. Two bStats charts (`number_of_gangs`, `number_of_ranks`) are dropped (WS5-D4).

**Gates.** G0 repackage identity (mechanical, 438+161 edges) → G1 module skeleton + persistence → G2 the hard
part (`UserDataLoader`, `CreateAccountListener`/`PlayerBootstrapService` split, placeholder split — 3 L steps)
→ G3 menus on `keystone-inventory` (after WS2, re-targeted 2026-09-17 R10 — was "on Oriel") → G4 re-point the
four consumer modules → G5 delete `gangland-domain`, docs, smoke
(module-absent boot: no `/glw gang`, turf/mail/gadget/civilians skipped with `module.dependency.missing`).

**Docket.** All 37 `GR-` ids move with their files (3 test-pinned: GR-12/13/35; GR-03/GR-08 already fixed).
**Estimate.** ~40 steps after rework ≈ 138 executor-hours (17–18 executor-days); critical path G0→G5 is sequential.
After rework (R9): the `GangMembership` holder carries `gangIdOf(UUID)`, `gangsAllied(int,int)`, `alliedOrSame(UUID,UUID)`;
`GangSettingsContract` splits 11 identity / 3 gang getters; `User.flushPermissions(Rank)` and
`UserManager.initializeUserPermission(User,Member)` move into a module-side `RankPermissionApplier`; the members-before-
users invariant rides on `PlayerBootstrapService`'s `BeanPostInitialize` phase, proven by a real boot at G0; only turf and
mail declare `Depends: [gang]` (cops-n-crooks follows turf transitively).

## 9. WS6 — `gangland-api` 2.0 and the `GanglandApi` facade

**Scope.** `GanglandApi` turns from a constants-only final class into the facade **interface** (constants kept —
~24 static call sites compile unchanged, WS6-D1); `GanglandApiImpl` + one `WiringConfig` bean register it on the
`ServicesManager` (Bartizan's `WiringConfig.bartizanApi(...)` pattern, `unregisterAll` on disable). Six accessors,
each an existing api type: `users()`, `gangs()` (the `GangMembership` holder, never null, inert when the gang module
is absent — final R7), `waypoints()`, `bankTiers()` (no `ranks()`, no `modules()` — REVIEW-WS6 cut) — nothing without a named consumer (no wanted/bounty/shop/
placeholder accessors, WS6-D2; WS1's placeholder provider is a `WiringConfig` registration, not an accessor).
Modules keep constructor injection (WS6-D7). api 2.0 re-export list: minus inventory-api, shop-api,
scoreboard-api, gangland-domain; plus `keystone-shop`; Oriel is **not** re-exported (R6). Events: keep the current
two public ones; loot/shop/gang events stay module-local (WS6-D6). `Messages`/`Settings` stay static (WS6-D4); one
worked module (mail) proves the YAML-migration mechanism; the full 179/~130 sweep stays a per-module follow-up.
`documentation/gangland-api.md` modelled on `bartizan-api.md` (resolution snippet, service table, accessor table,
events). `japicmp` is deferred until a Central release exists (WS6-D5, REVIEW-WS6 cut).

**Gates.** G0 prerequisites (after WS1/2/4/5 pruning) → G1 facade + registration + two tests (ServicesManager mock
pattern from `BartizanNpcWeaponsTest`) → G2 events audit → G3 mail worked example → G4 docs + japicmp + graph.
**Estimate.** ~10.5–14 h. **Docket.** KS-MO-05/06/07 untouched (Keystone-side).

## 9a. WS7 — Jetpack ownership and a soft Bartizan (gadget wave, 2026-09-16)

Sources: `../gadget-wave-2026-09-16/` — `README.md` (the ask, the "why", contracts K1–K9), `census/C1..C3` (Haiku),
`plans/WS7-gadget-ownership.md` (Sonnet; two fix passes + orchestrator §0c), `reviews/REVIEW-WS7.md` (Opus, re-reviewed).
Verdict: PASS WITH FIXES → **PASS** once §0c's three wiring fixes were applied (they are, in the plan text).

- **Why it exists:** before 0.9.0 the jetpack was a wearable; the Bartizan split ruled "Bartizan owns wearables", so
  the jetpack's item identity and config went to Bartizan's `items/wearables.yml` (`Extra_Tags:` `jetpack_*` keys)
  while flight stayed in `gangland-gadget`, which therefore hard-depends on Bartizan (`Plugins: [Bartizan]`) only to
  know what a jetpack *is*. The census found the only weapon-inherent gadget sites are `JetpackTask.isScoped()`
  (deleted) and `CarDamageListener`'s two weapon-event handlers; everything else is a Gangland item in disguise.
- **Target:** `ItemKind.JETPACK`, `items/jetpacks.yml` in the gadget jar, a `Jetpack` domain type + converter /
  serializer / refresher (the car's three-registry pattern), own NBT identity, `/glw jetpack give`, per-item
  permission, `Fuel_Key` mandatory at load (fixes GD-12 for free). Gadget's `module.yml` drops `Plugins: [Bartizan]`;
  `bartizan-api` stays `provided` for the car path. The car weapon-damage handlers move to `CarWeaponDamageListener`
  (`@ListenerHandler(condition = "isBartizanAvailable")`, auto-scanned, no bean); the melee lookup becomes the static
  helper `CarMeleeWeaponLookup` called only inside the guard; `CarDamageState` (a bean) holds the two shared sets.
  `Settings.isBartizanAvailable()` is the one api addition → `GanglandApi.VERSION` 1.0 → **1.1**, gadget `Host_Api: 1.1`.
- **Migration:** a chestplate carrying Bartizan's old `wearable` tag is re-stamped on the equip check with its fuel
  level preserved (`JetpackLegacyMigrationTest`); a `SELECT` for `WEARABLE:jetpack` shop/DB rows before G0.
- **Gates:** G0 Bartizan 0.4.0 residue (S) → G1 api + `ItemKind` + pins (S) → G2 YAML + domain type (M) → G3
  registries + jetpack classes rewritten off `Wearable` (L) → G4 migration + equip permission + give command (M) →
  G5 car split + soft Bartizan + `BartizanBlindScan` test (L) → G6 docs / docket / graph (S). Smoke rows must
  *exercise* the gadget on a Bartizan-less server, not only boot it. ≈ 11.5 executor-days.
- **civilians / cops-n-crooks (K7):** every edge classified (weapon-inherent vs replaceable; turf is 100 % transitive
  through `Depends: [civilians]`); the flip is **not** in scope — WS7-D1.
- **Docket:** GD-12 fixed for free; GD-20's jetpack half moot; GD-27 (right-click token race) tracked because the
  listener split touches it; GD-04/05/07/08/09/10 stay open (not this wave's logic). No new bug found.

## 9b. WS8 — Gadget catalogue: the grappling hook first (gadget wave, 2026-09-16)

Sources: `../gadget-wave-2026-09-16/plans/WS8-gadget-catalogue.md`, `reviews/REVIEW-WS8.md`. Verdict: **PASS**.

- **Pattern (K4/K5):** exactly what WS7 creates — an `ItemKind` entry, YAML in the module jar, converter / serializer /
  refresher, an NBT key, a per-type give command, listeners. No `Gadget` interface; a registry is YAGNI until the
  shortlist grows past ~4 (WS8-D3).
- **Grappling hook:** `ItemKind.GRAPPLE`, `items/grapples.yml`; launch on `PlayerFishEvent` (hook lands on a block
  within `Max_Range`), line-of-sight ray trace before the pull, per-tick pull toward the anchor capped at `Max_Speed`,
  cancel on damage / sneak / timeout / anchor chunk unload, a cooldown map (no fuel, WS8-D1), fall damage with the
  corrected shape (does not reproduce GD-04). Gates G1 item → G2 mechanics (early smoke for `PlayerFishEvent` across
  the 1.16–1.21 floor) → G3 anti-abuse + fall damage → G4 give command + permissions → G-final docs.
  ≈ 4.5–5 executor-days.
- **Shortlist (proposal only, uncosted):** parachute, spike strip, lockpick (item only; the minigame is WS3's),
  smoke/flash (not a Bartizan throwable), drone/camera (highest risk), disguise kit (recommended drop), boombox/radio,
  getaway flare. Recommended first three: grappling hook + parachute + smoke/flash (WS8-D2).
- **Shared edit with WS7:** `ItemKind.java` (`JETPACK` then `GRAPPLE`) — WS8 starts after WS7's G1.

## 10. Decisions register (the user's calls)

Recommendation in bold. "A" = orchestrator assumption, "R" = ruling made during review, "WSn-Dm" = a plan's decision.

| Id | Question | Options → **recommendation** |
|---|---|---|
| A1 | Versions/branches | **Gangland 0.10.0, Keystone 1.10.0, Oriel 0.8.0, scoreboard plugin 0.1.0, `Host_Api` 2.0** (see §1) |
| A2 / WS5-D1 | Gang module shape | **separate `gangland-gang` + turf `Depends: [gang, civilians]`** vs fold turf into the gang module |
| A3 / WS4-D1 | Where the shop system lives | **headless `keystone-shop` + one-sentence `CLAUDE.md:101` amendment** vs standalone plugin vs Oriel |
| A4 / WS3-D1/D2 | Hologram in Keystone | **new `keystone-hologram` module; amend `CLAUDE.md:101` narrowly (hologram only)** vs package in `keystone-common` |
| A5 / WS1-D5 | Scoreboard plugin placeholders | **PAPI first, then Gangland's Keystone `PlaceholderProvider` on the `ServicesManager`, then raw text** vs PAPI-only |
| N1 | Scoreboard plugin name | candidates Cartouche / Fascia / Parapet / Corbel (shallow collision check only) — **user picks** |
| WS1-D1 | Drivers | **port only DriverV3** (today's default; `ScoreboardManager.java:67` falls back to V1 — silent migration note) vs all three |
| WS1-D2 | Command framework in the plugin | **plain Bukkit `CommandExecutor` for the single `reload` command** vs keystone-command |
| WS1-D3 | PAPI absence | **boot warning + header comment, graceful raw text** vs silent |
| ~~WS2-D1~~ | ~~Oriel dependency hardness~~ | **dissolved by R10** — no Oriel dependency exists to size; `plugin.yml` keeps `depend: [Keystone]` only |
| ~~WS2-D2~~ | ~~Menu YAML home~~ | **dissolved by R10** — the nine menus stay Gangland's own YAML dialect in `plugins/Gangland_Warfare/menus/`, no `MenuConfigService.forConsumer`/Oriel folder question exists |
| ~~WS2-D3~~ | ~~Filters/search/sort~~ | **dissolved by R10** — filters/search/sort stay Gangland's own `filter.*` (11 files, moved to `gangland-impl` at G3a), no Oriel-registry option to choose |
| ~~WS2-D5~~ | ~~`Enchanted: true` on 4 slots~~ | **dissolved by R10** (already superseded by re-review N1 before the re-target) — no Oriel/`keystone-inventory` schema question left; the 4 slots stay Gangland's own `Enchanted:` YAML key, unchanged |
| WS3-D3 | `lootchest-api` Maven module | **fold into the feature module** vs keep as shaded library |
| WS3-D5 | Revamp scope | **structural now; LS-30/31 fixed for free; feature items deferred** vs fold in the 21 docket fixes |
| WS4-D2 | Default `ShopDisplayResolver` in Keystone | **ship it as the default** vs consumer-only |
| WS4-D5 | Trader/Banker messages+settings migration | **defer to the per-module follow-up** (REVIEW-WS4 S3; smaller diff) vs bundle into WS4 |
| WS5-D2 | Wanted/Bounty/Level | **stay core (repackaged)**; move to cops-n-crooks later | 
| WS5-D3 | `Permission*` | **stay core** vs move with `gang.rank.*` |
| WS5-D4 | bStats gang/rank charts | **drop** vs metrics seam in the module |
| WS6-D1 | Facade name | **keep `GanglandApi` (interface)** vs `GanglandServices` |
| WS6-D2 | Accessor list | **users, gangs (membership holder), waypoints, bank tiers — only api-safe types** vs also ranks/wanted/bounty/shop/placeholders/modules |
| WS6-D4 | `Messages`/`Settings` | **stay static; migration mechanism proven on mail** vs instance beans now |
| WS6-D5 | `japicmp` | **deferred until a Central release exists** (REVIEW-WS6 cut) vs wire now with `skip=true` |
| WS6-D6 | Public api events | **keep the current two** vs promote loot/shop/gang events |
| R1–R8 | Orchestrator rulings in §2 | **as ruled** — veto any one and its plan section is reworked |
| P1 | Central publishing of Oriel 0.8.0 / `keystone-*` / `gangland-api` 2.0 / scoreboard plugin in this wave | **no — install to `~/.m2`; publish when the namespace/GPG setup lands** |
| K1–K9 | Gadget-wave contracts (`../gadget-wave-2026-09-16/README.md`) | **as fixed** — veto one and its plan section is reworked |
| WS7-D1 | civilians / cops-n-crooks Bartizan dependency | (A) both stay hard — Bartizan absent drops civilians + cops + turf; (B) both soft — unarmed-NPC fallback (`BartizanNpcWeapons` is already null-safe); (C) soft civilians / hard cops — frees turf, cops unchanged. **No forced default; C is the reviewer's suggestion** |
| WS7-D4 | The jetpack loses Bartizan's armour traits (`Base_Damage_Reduction 0.05`, `REINFORCED 1`, `LIGHTWEIGHT 2`) | **accept and document** vs re-implement the reduction inside gadget |
| WS8-D1 | Grappling hook resource model | **cooldown only** vs fuel/durability through `FuelService` |
| WS8-D2 | First three gadgets after the hook | **grappling hook + parachute + smoke/flash** vs another pick from the eight |
| WS8-D3 | Give-command shape | **per-type `/glw <gadget> give` (car precedent)** vs generic `/glw gadget give <id>` + registry |

## 11. Estimate

| Stream | Executor time | Notes |
|---|---|---|
| Keystone 1.10.0 | ~1.5–2 days | hologram 0.5 d (WS3 G0) + shop 1–1.5 d (WS4 G0); independent of Oriel, can start first |
| Keystone 1.11.0 | 5–6 days | `keystone-inventory`, Phase E6 (re-targeted 2026-09-17, R10); E6.1 core + E6.2 flow/pages, own review as a Keystone phase |
| Oriel 0.8.0 | 0 days in this wave | **off the critical path (R10)** — G0 (Keystone 1.9.2 bump + `forConsumer`) already done pending review and stays useful for Oriel's own rebase, but no longer gates or costs this wave |
| `<ScoreboardPlugin>` + WS1 removal | ~2 days | ~16 h |
| WS2 | 7–9 days + 0.5 d cutover | re-targeted onto `keystone-inventory` (R10); G4 (npc-shops, two deposit-slot redesigns) still dominates |
| WS3 | ~6 days | G3 blocked on WS2 |
| WS4 consumer side | ~5–6 days | G1a headless repoint + G1b nine admin views on Oriel (L) |
| WS5 | ~138 h ≈ 17–18 executor-days | ~40 steps after rework; G2 (identity/gang split of `User`, `UserManager`, the join and placeholder paths) is the critical path |
| WS6 | ~1.5–2 days | 12–14 h; mostly receives the others' output |
| WS7 (Gangland 0.9.2 + Bartizan 0.4.0) | ~11.5 days | 7 gates; G3 and G5 are L; runs before 0.10.0's G0, no Keystone/Oriel prerequisite |
| WS8 (Gangland 0.9.2) | ~4.5–5 days | grappling hook only; the shortlist is uncosted |
| **Total** | **≈ 42–47 executor-days (0.10.0, incl. Keystone 1.11.0) + ≈ 16–17 (0.9.2 gadget wave) ≈ 58–64** | re-targeted 2026-09-17 (R10): WS2 and Oriel shrink, Keystone 1.11.0 is added; one reactor build at a time; Keystone and the scoreboard plugin parallelise; Gangland's stream is sequential |

## 12. Execution process (when the user says go)
- Order: **Gangland 0.9.2: WS7 (Bartizan 0.4.0 first) → WS8** → then Keystone 1.10.0 → **Keystone 1.11.0
  (`keystone-inventory`, re-targeted 2026-09-17 R10)** → Gangland 0.10.0 G0 (branched off 0.9.2) → WS1 → WS2 → WS4
  consumer → cutover → WS3 → WS5 → WS6; the scoreboard plugin's G0–G2 run in parallel, and Keystone 1.10.0/1.11.0
  may start while 0.9.2 is in flight (no shared files). Oriel 0.8.0 is off this wave's path entirely (R10) — its
  G0 stands as already done, and its own `keystone-inventory` rebase runs on Oriel's separate track after 1.11.0.
- One reactor build at a time (parallel `-am` builds race on shared `target/`); install order Keystone → Gangland
  (Oriel no longer a Gangland-side install dependency, R10).
- Executors = Sonnet `claude` agents, one gate each, no sub-agents, never commit; Opus reviewers per gate; the
  orchestrator commits at gate boundaries with `git commit -- <paths>`; nothing is pushed without the user.
- Every gate ends green (`mvn clean install` of each touched reactor) with its smoke rows run through the console
  harness (`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`); the "genuinely red first" rule for every
  flipped or new test.
- Bug docket: every touched id gets a status/note row after its gate; new bugs → `triage/`.
- `graphify update . --force` in every touched repo at the end of each gate; memory + CLAUDE.md updated at the end.

## 13. Not in this wave (follow-ups)
- Central publishing (namespace/GPG/token pending user setup); `japicmp` enforcement once a second 2.x exists.
- Wanted/Bounty into cops-n-crooks; the full module-owned `Messages`/`Settings` sweep beyond mail; loot chest
  feature items (safe-cracking input, tier/refill redesign); bStats gang charts via a metrics seam.
- KS-MO-05/06/07 (module bootstrap isolation, Keystone-side); Oriel `CommandTabCompleter` deprecation before
  Keystone 2.0.0; a TextDisplay hologram backend above the 1.16 floor.
