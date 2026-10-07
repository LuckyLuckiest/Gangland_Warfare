# WS6 G3 step 12 — per-module Messages/Settings migration table

Not executed — this is the plan's asked-for table only (`plans/WS6-api.md` §4 step 12). Counts are re-verified
against the actual worktree (`E:\Programming\java\wt\gangland-0.10.0-ws6`, HEAD 837966c3 + this gate's uncommitted
G3 work) as of 2026-09-22, not copied from the plan text — they still match the plan's 2026-09-14 figures exactly
for every `Messages` prefix, confirming nothing shifted in WS1-WS4's landings since. `CIVILIAN` (12 constants) is
excluded from this table — it is this gate's own worked example (see `G3-report.md`), not a remaining item.

## `Messages` prefixes

| Prefix | Count | Owner module | Message-YAML plumbing | Note |
|---|---|---|---|---|
| `GANG` | 66 | `gangland-gang` (WS5) | **doesn't exist yet** — `gangland-features/gangland-gang` isn't in this worktree at all (WS5 still in flight); all 18 callers (17 `command/sub/gang/*` + 1 `command/sub/waypoint/WaypointGangIdCommand`) are still `gangland-impl` | Migration can't start until WS5 creates the module; ownership of the command classes themselves also moves with it |
| `TURF` | 59 | `gangland-turf` (exists) | **has NPC-config YAML** (`turf/turf_npcs.yml`, `TurfFileConfig`-style registration) but no message-YAML file yet; all callers already live in the module | Same shape as civilians before this gate — mechanical build-out, `LocalizedModuleYaml` is ready to receive it |
| `DETAINMENT` | 45 | `cops-n-crooks` (exists) | **has NPC-config YAML** (`npc/cops.yml`) but no message-YAML file yet; all callers already live in the module | Same shape as `TURF` |
| `BANKER` | 29 | `gangland-npc-shops` (exists) | **settings already split** (`npc/banker_settings.yml`, WS4 G1a) but **messages are not** — still read from `gangland-api`'s `Messages` enum | One stray core caller found: `gangland-impl/.../command/sub/bank/BankDepositCommand.java` reads a `BANKER_*` constant directly — flagged below as a docket candidate, not fixed here |
| `TRADER` | 12 | `gangland-npc-shops` (exists) | **settings already split** (`npc/trader_settings.yml`, WS4 G1a) but **messages are not** | All 12 callers confirmed inside the module only |
| `SHOP` | 29 | **none — core** | N/A, moot (per plan) | Confirmed by grep: all 6 callers (`command/sub/shop/Shop*Command`, `file/configuration/shop/GanglandShopMessages`) are `gangland-impl`. `/glw shop` admin is core functionality (the generic item-buy/sell admin editor), not owned by any runtime module — WS4 moved the headless shop *system* to `keystone-shop`, but these are the admin-command strings, which stay core by design |
| `LOOT` | 0 | `gangland-lootchest` | **done** | Fully migrated in WS3 G4 (`lootchests/lootchest_messages.yml` + `_es.yml`, W53) — zero constants remain, confirmed by grep |
| `MODULE` | 22 | **none — core** | N/A | All 5 callers (`command/sub/module/Module{Install,List,Remove,Update}Command`, `config/WiringConfig`) are `gangland-impl`. These are the `/glw module install\|update\|remove` commands that manage the *other* runtime modules — inherently core (you need them before any module is loaded), no runtime module can ever own this prefix |
| `RANK` | 20 | undecided — core today, possibly `gangland-gang` after WS5 | none | All 9 callers are `gangland-impl`'s `command/sub/rank/*`. `RankManager` is listed as a core manager in `CLAUDE.md`'s Service/Manager Layer, not tied to any module — whether WS5 pulls it into `gangland-gang` is a scope question for that plan, not decided in this worktree |
| `CAR` | 10 | `gangland-gadget` (exists) | **mechanism already proven** — `gadget/gadget_messages.yml` already exists and already backs `JetpackMessages`/`GrappleMessages` via the same `<baseName>`/`FileManager` pattern this gate generalised into `LocalizedModuleYaml` | Easiest remaining prefix — the file just needs 10 more `Car_*` keys and a `CarMessages` (or folded into an existing holder) accessor set; no new plumbing to build |
| `FUEL` | 8 | ambiguous — no single owner | N/A today | All 5 callers (`command/sub/fuel/Fuel{Add,Defuel,Info,Refuel,Remove}Command`) are `gangland-impl`. The fuel *system* lives in `gangland-infra/gangland-item` (per `CLAUDE.md`'s module table) but the fuel *command* is core, and fuel is consumed by both cars and jetpacks (both gadget-owned) — no clean single-module target without a scope decision first |

## `Settings` getters (grouped by the same owner, since they track the Messages prefixes above)

Not literally ~130 individual lines (that would just restate the plan's own estimate as a wall of near-duplicate
rows) — grouped per owner module, re-counted from `Settings.java:27-236`'s field declarations as of this gate:

| Owner | Getter count | Status |
|---|---|---|
| `gangland-civilians` | 22 (`civilianAi*` 2, `civilianSpawner*` 7, `civilianSpawn*` 13) | **Still core**, even though civilians already has its own YAML pipeline (this gate extended it for messages) — a real gap: the settings mechanism this gate's `CivilianMessages` doesn't touch. Worth its own follow-up gate |
| `gangland-turf` | 35 (`turfIncome*`/`turfWand*`/`turfVisualization*`/`turfShowEnterTitle` 6, `turfCapture*` 10, `turfCaptureSound*` 15, `turfContribution*` 4) | Still core; matches `TURF`'s message-prefix status above — no YAML plumbing for settings either |
| `cops-n-crooks` (cop + jail + detainment) | 52 (`cop*`/`jail*` core+count+spawn+pursuit+return+misc 32, `detainment*` 20) | Still core; `npc/cops.yml` exists for NPC *definitions* but not for these tunable numeric knobs |
| `gangland-gadget` (car + jetpack) | 8 (`gadgetJetpack*` 5, `gadgetCar*` 3) | Still core; matches `CAR`'s message-prefix status — `gadget_messages.yml`'s mechanism is proven but hasn't been reused for a `gadget_settings.yml` yet |
| `gangland-npc-shops` (trader/banker) | **0** | **Done** (WS4 G1a) — `npc/trader_settings.yml`/`npc/banker_settings.yml` already own every Trader/Banker settings knob; nothing left in core |
| `gang` (pending WS5) | 9 (`gangEnabled`, `gangNameDuplicates`, `gangRankHead`, `gangRankTail`, `gangDisplayNameChar`, `gangInitialBalance`, `gangCreateFee`, `gangMaxBalance`, `gangContributionRate`) | Still core, same as `GANG` messages — blocked on WS5 |
| `Shop.Max_Mode_Multiplier` | 1 | **Stays core by design** (WS4 G1a comment: shared by the admin editor and the trader browser) — not a migration candidate |
| signs legacy aliases | 6 (`signsLegacyWeaponBuy/Sell`, `signsLegacyAmmoBuy/Sell`, `signsLegacyWearableBuy/Sell`) | Core, `sign-api`-adjacent, not one of the runtime-module prefixes above — out of this table's scope |

Everything else in `Settings.java:27-236` (database, economy, user/level/death/respawn, bounty, wanted, inventory,
money drop, debug/update/module-repository/language/resource-pack, signs legacy) is a genuinely core-wide knob
with no single runtime-module owner — correctly not module-owned, not part of this migration.

## Docket row — T-47 (the settings `Inventory:` block)

Confirmed still live and still read directly via `Settings.getInventoryFillItem()`/`getInventoryFillName()` (the
`Inventory:` block, `settings.yml:146-156`, `Fill`/`Line`/`Multi_Inventory`), not through any module-owned YAML:

- `gangland-npc-shops`: `BankerClaimView`, `BankerCreateAccountView` (×2), `BankerMenuView`, `BankerUpgradeView`,
  `BarterView`, `ModeSelectView`, `NegotiationView`, `SellView`, `ShopView`, plus both `TraderSettingsImpl`/
  `BankerSettingsImpl` delegating straight through to `Settings` for exactly these two getters (their own
  javadocs say so explicitly — "stay delegated to core `Settings`").
- `gangland-turf`: `TurfModuleConfig` (3 call sites).
- `gangland-impl` (core): `ShopAdminView`, `GanglandShopUiSettings`, `GangColorCommand` (a comment referencing the
  same quirk).

T-47 is unaffected by this gate — recorded here as the row the dispatch asked for, not fixed.

## Docket candidate noticed while building this table (not filed — WS6 G3's dispatch scope is G3 only)

`gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/bank/BankDepositCommand.java` reads a
`Messages.BANKER_*` constant directly from core, even though every other `BANKER_*` caller lives inside
`gangland-npc-shops`. Either a leftover from before the npc-shops split, or a deliberate core/module boundary
crossing worth a second look when `BANKER` itself gets migrated. Not investigated further — out of this gate's
G3-only scope; flagged for whoever picks up the `BANKER` row above.
