# Flip 4: gangland-weapon → runtime module `weapon`

**Assumes done:** flips 1 (cops-n-crooks), 2 (gadget) and 3 (turf). Weapon is the **last** feature left in the core's
compile closure. Both already-flipped modules `copsncrooks` and `gadget` depend on weapon, so this flip also edits
**their** poms and `module.yml`.
**Module:** `gangland-features/gangland-weapon` · id `weapon` · package root `org.luckyraven.gangland.weapon` · jar
`target/modules/gangland-weapon-0.8.4.jar` · `Depends:` now `[]` (weapon depends on no other module), and this flip
**adds** `Depends: - weapon` to `copsncrooks` and `gadget`.
**Planner:** opus, 2026-09-07. **Graph:** refreshed 2026-09-07 16:28 (`graphify-out/graph.json`), HEAD `d6bb33ac`
(2026-09-07 16:11) — graph is fresh.

---

## 0. Summary for the scrum master

### Convergence with `gadget.md` (flip 2) — **read and adopted in full**

I read `gadget.md` §0, §1.6 and its group A/B tasks (T1–T5) and **this plan is converged on all five of the
coordinator's points**. Concretely:

1. **Wearables are weapon's, not gadget's — adopted.** Flip 2's group-A task T2 relocates `WearableAddon` into
   `org.luckyraven.gangland.weapon.wearable` (it already `extends` weapon's `WearableService`,
   `gangland-features/gangland-weapon/.../weapon/wearable/WearableService.java:35`), so by flip-4 time the wearable
   *domain* class already sits in this Maven module and **flip 4 owns the whole wearable stack**. This plan therefore
   additionally moves, from `gangland-impl`: the four `/glw item wearable` sub-commands (as a `CommandContribution`
   with `parent() == "item"`), `WearableConverter`, `WearableRefresher`, `WearableItemSerializer`, the two wearable
   trade signs + `WearableSignValidator`, the wearable branch of `ViewInventoryAspect`, the `wearableAddon` FILE bean
   (`FileConfig.java:196-202`), the `wearableService` (`GameplayConfig.java:212-215`) and `wearableEquipService`
   (`GameplayConfig.java:307-310`) beans, the three `ItemConfig` wearable beans (`:48,:119,:163`), and the
   `items/wearables.yml` default + its `KernelConfig.java:175` registration. **Counted below and in §1.1.**
2. **`FuelService` → `gangland-infra/gangland-item` is flip 2's, not mine.** This plan touches nothing named `Fuel*`.
   (The one incidental contact: `ViewInventoryAspect.findFuelUniqueItem` moves with the wearable view branch; it uses
   `UniqueItemAddon`, a core type, and never `FuelService`.)
3. **Item registries — no new interface.** I **dropped** my earlier `ItemRegistrationOrder` design in favour of
   gadget's rule-3(a) mechanism: the module's `@Bean` methods take `ItemConverterRegistry` / `ItemSerializerRegistry` /
   `ItemRefresherRegistry` as parameters (the parameter *is* the ordering edge) and register into them, using flip 2's
   `ItemSerializerRegistry.register(predicate, serializer, priority)` + `CATCH_ALL_PRIORITY`. Exact priorities in
   §1.6(a). **One extension is unavoidable and is written up as a contradiction in §6 (C-1): flip 2 added the priority
   overload only to `ItemSerializerRegistry`, but `ItemRefresherRegistry` is order-sensitive too** — today
   `WeaponRefresher` is registered *before* `UniqueItemRefresher` (`ItemConfig.java:189`) and a unique weapon carries
   both NBT tags, so appending the weapon refresher after the core's unique one silently changes which refresher wins.
   Flip 4 mirrors flip 2's exact API (`CATCH_ALL_PRIORITY`, 3-arg overload, stable `List.sort`) onto
   `ItemRefresherRegistry`.
4. **Sign types — I use flip 2's mechanism verbatim.** `org.luckyraven.gangland.sign.extension.{SignTypeContribution,
   SignViewProvider, SignContributions}`, `SignManager.setupSigns()` resolving `SignContributions.from(container)`
   **lazily inside the method**, and the deleted `manager.initialize()` at `GameplayConfig.java:266`. **I designed no
   second mechanism** — my earlier draft's `ItemViewProvider` is gone, replaced by gadget's `SignViewProvider`.
   Flip 4's 12 `sign/type` + 4 `sign/validation` + 1 `sign/aspect` weapon/wearable files collapse into **one**
   `SignTypeContribution` bean plus **one** `SignViewProvider` bean in the weapon module.
5. **Split package — checked, exactly one file.** `gangland-impl/src/main/java/org/luckyraven/gangland/weapon/WeaponManager.java`
   is the sole core class in the module's root package (`find gangland-impl/src/main -path "*/gangland/weapon/*"` → 1
   hit). T13 moves it, keeping its FQN, and gate G3 proves the core jar has zero `org/luckyraven/gangland/weapon/`
   entries — the same parent-first hazard gadget flagged for `GanglandCarGangs`.

Contradictions and deltas I did **not** silently absorb are in **§6 (C-1 … C-4)**.

### Counts

**Impl files touched: 67 main + 2 test** — the 55 found by
`grep -rl "org\.luckyraven\.gangland\.weapon\." gangland-impl/src/main` today, plus the 12 wearable-domain files that
only become weapon's after flip 2's `WearableAddon` relocation (`ItemCommand`, the four `ItemWearable*` commands,
`WearableConverter`, `WearableRefresher`, `WearableItemSerializer`, `WearableBuySign`, `WearableSellSign`,
`WearableSignValidator`, `KernelConfig`).

| Decision | Count | What |
|---|---|---|
| **MOVE** to the module | 33 | 8 weapon/ammo commands + 4 wearable commands, 2 persistence, 2 file loaders, 3 converters/refreshers ×2 + 3 serializers, 6 trade signs, 3 sign validators, `weapon/WeaponManager` |
| **SPLIT** (feature slice moves, core keeps a weapon-free remainder) | 20 | `Gangland`, `PeriodicalUpdates`, `PluginDataCleanupService`, `DebugCommand`, `ReadNBTCommand`, `ItemCommand`, `FileConfig`, `GameplayConfig`, `ItemConfig`, `KernelConfig`, `SchedulingConfig`, `ShopConfig`, `ItemPredicates`, `GanglandShopDisplayResolver`, `BaseTradeSign`, `SignManager`, `ViewSign`, `ViewInventoryAspect`, `GangMembersDamageListener`, `PlayerDeathListener`, `RemoveAccountListener` |
| **KEEP, made weapon-free with zero behaviour change** | 5 | `ViewSignValidator` (its weapon checks are provably dead code), `BuySign`, `SellSign`, `CarBuySign`, `CarSellSign` |
| **KEEP as-is** | `CopsAndGadgetsConfig` | must already be gone/weapon-free after flips 1–2; T0 verifies and escalates |

Plus **2 already-flipped gadget-module files** this flip must edit (§1.1 §C, consistency-review finding 5):
`gadget/sign/CarSignContribution.java` and `gadget/GadgetModuleConfig.java` — T9's `CarBuySign`/`CarSellSign`
signature change breaks their call sites, so T9 step 3 fixes them in the gadget module.

### Seams

Three come from flip 2 (reused unchanged), four are new and owned by this plan.

| Seam | Core package | Owner |
|---|---|---|
| `SignTypeContribution` · `SignViewProvider` · `SignContributions` | `org.luckyraven.gangland.sign.extension` | **flip 2** — reused verbatim |
| Item registry registration (rule 3a, no interface) + `ItemSerializerRegistry.register(p, s, priority)` / `CATCH_ALL_PRIORITY` | `gangland-infra/gangland-item` | **flip 2** — reused; flip 4 mirrors it onto `ItemRefresherRegistry` (§6 C-1) |
| `CommandContribution` (existing since 0.8.2) | `org.luckyraven.gangland.command.extension` | reused for `/glw item wearable` and `/glw debug weapon` |
| `MetricsContributor` | `org.luckyraven.gangland.metrics` | **weapon plan** (mandated by PLANNER-BRIEF) |
| `DataCleanupTask` | `org.luckyraven.gangland.data.plugin` | **weapon plan** |
| `NbtTagCatalog` (registry bean, rule 3a) | `org.luckyraven.gangland.item` | **weapon plan** |
| `ShopDisplayNameProvider` | `org.luckyraven.gangland.file.configuration.shop` | **weapon plan** |
| `DeathMessageContributor` | `org.luckyraven.gangland.listener.death` | **weapon plan** |

### p0-wave-3 overlap — merge `0.8.3` before Group B starts

Files this plan edits that `p0-wave-3` also edits: **`config/GameplayConfig.java`** (T10, T11, T20) and
**`command/sub/debug/DebugCommand.java`** (T7). Verified *not* edited by this plan:
`file/configuration/Messages.java` (holds weapon *strings* only, no weapon types — nothing to do),
`sign/GanglandSignInformation.java`, sign-api `SignCreation`/`SignInformation`, `command/sub/gang/*`,
`command/sub/waypoint/*`, `data/placeholder/worker/GanglandPlaceholder.java`, `message_en/es.yml`.
(`config/CopsAndGadgetsConfig.java` is expected to be gone by flip 2 — T0 escalates if not.)

### Task groups

| Group | Tasks | Files | Theme |
|---|---|---|---|
| A | T0–T2 | 9 | reconcile inventory, poms, module skeleton — **the compile gate produces the work list** |
| B | T3–T6 | 12 | core seams: metrics, cleanup, NBT catalogue, shop display |
| C | T7–T8 | 5 | `/glw debug weapon` + `/glw item wearable` contributions; `ItemRefresherRegistry` priority |
| D | T9–T11 | 14 | sign layer on flip 2's seam: strip `BaseTradeSign`, `SignManager`, `ViewSign`/`ViewInventoryAspect` |
| E | T12–T16 | 30 | the moves: commands, persistence, loaders, item classes, signs, wearable stack, manager |
| F | T17–T19 | 10 | listener splits, `WeaponFileConfig`, `WeaponModuleConfig` |
| G | T20–T22 | 8 files + 24 YAML | core config clean-out, YAML move, `commands.json` |
| H | T23–T25 | 6 | tests |
| I | T26–T28 | 8 | docs, cops+gadget `module.yml`, gates G1–G4 |

### Three biggest risks

1. **`ItemRefresherRegistry` ordering (§6 C-1).** Today `WeaponRefresher` runs before `UniqueItemRefresher`
   (`ItemConfig.java:189`) and a unique weapon carries both the weapon and the uniqueItem NBT tag
   (`ItemConfig.java:143-145` documents exactly that). After the flip the core registers `UniqueItemRefresher`
   first and the module appends — so a unique weapon would be rebuilt as a plain unique item, losing its weapon
   state, **silently**. This is the single change most likely to cause a data regression if the priority overload is
   skipped.
2. **`SignManager.setupSigns()` timing.** Flip 2 already moves the trigger from the `@Bean` body
   (`GameplayConfig.java:266`) to Keystone's convention `initialize()` pass. Flip 4 depends on that having landed and
   still working: if the explicit call is back, or `SignManager` resolves `SignContributions` in its constructor, the
   four weapon/ammo signs and the two wearable signs silently disappear. T9/T10 re-verify before building on it.
3. **`WearableAddon`'s location.** Everything in this plan's wearable half assumes flip 2's T2 landed
   (`org.luckyraven.gangland.weapon.wearable.WearableAddon`). T0 checks it first; if it is still in
   `gangland-gadget`, the flip must stop and the scrum master decides whether flip 4 performs flip 2's T2 itself.

---

## 1. Inventory (facts, with source locations)

### 1.1 Impl files that reference the feature

Base list: `grep -rl "org\.luckyraven\.gangland\.weapon\." gangland-impl/src/main` → **55 files** (§A below).
Plus **12 wearable-domain files** that become weapon's only after flip 2's `WearableAddon` relocation (§B below).
`gangland-impl/src/test` → 2 files (§1.5).

#### §A — files that import `org.luckyraven.gangland.weapon.*` today

| # | path (under `gangland-impl/src/main/java/org/luckyraven/gangland/`) | role | what it uses from weapon | decision | p0-w3? |
|---|---|---|---|---|---|
| 1 | `Gangland.java` | entry point | `WeaponAddon` (`:38` import, `:117` `context.get(WeaponAddon.class).size()`) | **SEAM** `MetricsContributor` (T3) | n |
| 2 | `bootstrap/PeriodicalUpdates.java` | lifecycle | `Weapon`, `WeaponManager` (`:18-19,:38,:49,:60,:199`) | **SPLIT** — drop both; cleanup via `DataCleanupTask` (T4) | n |
| 3 | `command/sub/debug/DebugCommand.java` | command | `Weapon`, `WeaponManager` (`:50-51,:64,:76,:497-498`) | **SPLIT** — delete `getGiveGun()`; module re-adds via `CommandContribution("debug")` (T7) | **y** |
| 4 | `command/sub/debug/ReadNBTCommand.java` | command | `WeaponTag` (`:14,:53`) | **SEAM** `NbtTagCatalog` (T6) | n |
| 5 | `command/sub/weapon/AmmunitionCommand.java` | command | `AmmunitionManager` | **MOVE** → `weapon.command` | n |
| 6 | `command/sub/weapon/AmmunitionGiveCommand.java` | command | `Ammunition`, `AmmunitionManager` | **MOVE** → `weapon.command` | n |
| 7 | `command/sub/weapon/AmmunitionInfoCommand.java` | command | `Ammunition`, `AmmunitionManager` | **MOVE** → `weapon.command` | n |
| 8 | `command/sub/weapon/AmmunitionListCommand.java` | command | `Ammunition`, `AmmunitionManager` | **MOVE** → `weapon.command` | n |
| 9 | `command/sub/weapon/WeaponCommand.java` | command | `WeaponManager`, `WeaponAddon`, `WeaponLoader` | **MOVE** → `weapon.command` | n |
| 10 | `command/sub/weapon/WeaponGiveCommand.java` | command | `Weapon`, `WeaponManager`, `WeaponLoader` | **MOVE** → `weapon.command` | n |
| 11 | `command/sub/weapon/WeaponInfoCommand.java` | command | `Weapon`, `WeaponManager`, `WeaponAddon`, `dto.AmmunitionData`, `dto.ReloadData` | **MOVE** → `weapon.command` | n |
| 12 | `command/sub/weapon/WeaponListCommand.java` | command | `Weapon`, `WeaponAddon` | **MOVE** → `weapon.command` | n |
| 13 | `config/CopsAndGadgetsConfig.java` | config | `WeaponManager` (`:71,:341,:350`), `WeaponService` (`:72,:417`) — inside cop and gadget beans | **KEEP** — flips 1 & 2 own these; T0 escalates if the file still exists with weapon imports | flip1/2 |
| 14 | `config/FileConfig.java` | config | `WeaponLoader` `:17,:224-233`, `AmmunitionManager` `:34,:168`, `AmmunitionAddon` `:35,:173`, `WeaponAddon` `:36,:215`, `GanglandBlockRegenerationSettings` `:16,:137`, **`WearableAddon` `:21,:197-202`** | **SPLIT** — 6 FILE beans → `WeaponFileConfig` (T20) | n |
| 15 | `config/GameplayConfig.java` | config | `WeaponManager` `:182`, `WeaponService` `:187`, `BlockDamageManager` `:192`, `WeaponVisualSpawner` `:197`, `WeaponRaytracer` `:202`, **`WearableService` `:213`**, `RecoilCompatibility` `:219`, `PluginFireRegistry` `:224`, `signManager` `:259-268`, **`WearableEquipService` `:307-310`** | **SPLIT** (T10 sign half, T20 weapon+wearable half) | **y** |
| 16 | `config/ItemConfig.java` | config | `WeaponService` `:38`, `AmmunitionManager` `:43`, **`WearableService` `:16,:48,:163`**, `WeaponItemSerializer` `:109`, `AmmunitionItemSerializer` `:114`, **`WearableItemSerializer` `:119`**, `WeaponRefresher` `:158`, `AmmunitionItemRefresher` `:173`, registries `:69-86,:133-153,:183-191` | **SPLIT** (T8, T20) | n |
| 17 | `config/SchedulingConfig.java` | config | `WeaponManager` (`:22,:74,:77,:80`) | **SPLIT** — drop the param (T4) | n |
| 18 | `config/ShopConfig.java` | config | `WeaponService` (`:48,:105`) | **SPLIT** — `shopDisplayResolver` loses the param (T5) | n |
| 19 | `data/plugin/PluginDataCleanupService.java` | service | `WeaponRepository`, `Weapon`, `WeaponManager` | **SPLIT** — generic over `DataCleanupTask` (T4) | n |
| 20 | `database/repositories/weapon/WeaponRepository.java` | repository | `Weapon`, `WeaponAddon`, `WeaponTable` | **MOVE** → `weapon.database` | n |
| 21 | `database/tables/weapon/WeaponTable.java` | table | `Weapon` | **MOVE** → `weapon.database` | n |
| 22 | `file/configuration/shop/GanglandShopDisplayResolver.java` | contract impl | `Weapon`, `WeaponService`, `WeaponTag` | **SPLIT** — weapon branch becomes a `ShopDisplayNameProvider` in the module (T5/T15) | n |
| 23 | `file/configuration/weapon/GanglandBlockRegenerationSettings.java` | contract impl | `modifiers.BlockRegenerationSettings` | **MOVE** → `weapon.file` as `WeaponBlockRegenerationSettings` | n |
| 24 | `file/configuration/weapon/WeaponLoader.java` | file loader | `AmmunitionManager`, `WeaponAddon` | **MOVE** → `weapon.file` | n |
| 25 | `item/ItemPredicates.java` | item | `Weapon`, `WeaponTag`, `Ammunition` (`:10-12,:29-30`) | **SPLIT** — `WEAPON`/`AMMUNITION` → `weapon.item.WeaponItemPredicates`; **`WEARABLE` stays in core** (it uses `item.wearable.Wearable`, a gangland-item type) | n |
| 26 | `item/converter/AmmunitionConverter.java` | converter | `Ammunition`, `AmmunitionManager` | **MOVE** → `weapon.item` | n |
| 27 | `item/converter/WeaponConverter.java` | converter | `Weapon`, `WeaponService` | **MOVE** → `weapon.item` | n |
| 28 | `item/converter/WearableConverter.java` | converter | `weapon.wearable.WearableService` | **MOVE** → `weapon.item` (§B) | n |
| 29 | `item/refresher/AmmunitionItemRefresher.java` | refresher | `Ammunition`, `AmmunitionManager` | **MOVE** → `weapon.item` | n |
| 30 | `item/refresher/WeaponRefresher.java` | refresher | `Weapon`, `WeaponService`, `WeaponTag` | **MOVE** → `weapon.item` | n |
| 31 | `item/refresher/WearableRefresher.java` | refresher | `weapon.wearable.WearableService` (`:10`) | **MOVE** → `weapon.item` (§B) | n |
| 32 | `item/serializer/AmmunitionItemSerializer.java` | serializer | `Ammunition` | **MOVE** → `weapon.item` | n |
| 33 | `item/serializer/WeaponItemSerializer.java` | serializer | `Weapon`, `WeaponTag` | **MOVE** → `weapon.item` | n |
| 34 | `listener/gang/GangMembersDamageListener.java` | listener | `events.projectile.WeaponRaytraceImpactEvent` (`:15,:56-70`) | **SPLIT** — second handler → `weapon.listener.gang` (T17) | n |
| 35 | `listener/player/PlayerDeathListener.java` | listener | `Weapon`, `WeaponManager`, `types.throwable.ThrowableAction` (`:30-32,:43,:50,:200-237`) | **SEAM** `DeathMessageContributor` (T17) | n |
| 36 | `listener/player/RemoveAccountListener.java` | listener | `Weapon`, `WeaponManager` (`:22-23,:32,:38,:112-118`) | **SPLIT** — quit-cleanup block → `weapon.listener.player` (T17) | n |
| 37 | `sign/SignManager.java` | sign | `WeaponService`, `AmmunitionManager`, `WearableService`; builds weapon/ammo (`:93,:102,:111,:120`) and wearable (`:174,:183`) signs | **SPLIT** — flip 2's `SignTypeContribution` (T10) | n |
| 38 | `sign/aspect/ViewInventoryAspect.java` | sign aspect | `Weapon`, `WeaponService`, `Ammunition`, `AmmunitionManager`, `dto.AmmunitionData`, `types.gun.GunWeapon`, `WearableService` | **SPLIT** — flip 2's `SignViewProvider` (T11) | n |
| 39 | `sign/type/ViewSign.java` | sign | `WeaponService`, `AmmunitionManager`, `WearableService` | **SPLIT** — loses all three (T11) | n |
| 40 | `sign/type/trade/BaseTradeSign.java` | sign base | `Weapon`, `WeaponService`, `Ammunition`, `AmmunitionManager`, `types.WeaponType` | **SPLIT** — strip to `getUniqueOrMaterialItem` only (T9) | n |
| 41 | `sign/type/trade/BuySign.java` | sign | ctor params only, **never used** (`:40-45`) | **KEEP** — drop 2 ctor params (T9) | n |
| 42 | `sign/type/trade/SellSign.java` | sign | ctor params only, **never used** | **KEEP** — drop 2 ctor params (T9) | n |
| 43 | `sign/type/trade/ammo/AmmoBuySign.java` | sign | `WeaponService`, `AmmunitionManager`, `getAmmoItem`, `ammoSimilarityChecker` | **MOVE** → `weapon.sign` | n |
| 44 | `sign/type/trade/ammo/AmmoSellSign.java` | sign | same | **MOVE** → `weapon.sign` | n |
| 45 | `sign/type/trade/car/CarBuySign.java` | sign | ctor params only (`:40-41`), never used | **KEEP** — drop 2 ctor params (T9); flip 2 may have moved it to `gadget.sign` — apply there | flip2 |
| 46 | `sign/type/trade/car/CarSellSign.java` | sign | same | **KEEP** — drop 2 ctor params (T9); same note | flip2 |
| 47 | `sign/type/trade/weapon/WeaponBuySign.java` | sign | `WeaponService`, `AmmunitionManager`, `getWeaponItem`, `weaponSimilarityChecker` | **MOVE** → `weapon.sign` | n |
| 48 | `sign/type/trade/weapon/WeaponSellSign.java` | sign | same | **MOVE** → `weapon.sign` | n |
| 49 | `sign/type/trade/wearable/WearableBuySign.java` | sign | `WeaponService`/`AmmunitionManager` ctor params (unused) + `WearableService` (used) | **MOVE** → `weapon.sign` (§B) | n |
| 50 | `sign/type/trade/wearable/WearableSellSign.java` | sign | same | **MOVE** → `weapon.sign` (§B) | n |
| 51 | `sign/validation/ViewSignValidator.java` | validator | `Weapon`, `WeaponService`, `AmmunitionManager` — **all dead**: `isValidContent` reaches `return true` on every non-empty string (`:24-51`) | **KEEP** — delete the dead checks + both fields (T11) | n |
| 52 | `sign/validation/trade/ammo/AmmoSignValidator.java` | validator | `AmmunitionManager` | **MOVE** → `weapon.sign` | n |
| 53 | `sign/validation/trade/weapon/WeaponSignValidator.java` | validator | `WeaponService` | **MOVE** → `weapon.sign` | n |
| 54 | `sign/validation/trade/wearable/WearableSignValidator.java` | validator | `weapon.wearable.WearableService` (`:6`) | **MOVE** → `weapon.sign` (§B) | n |
| 55 | `weapon/WeaponManager.java` | manager | `WeaponAddon`, `WeaponService`, `WeaponRepository`, `Weapon` | **MOVE** → module, **same FQN** `org.luckyraven.gangland.weapon.WeaponManager` — **the only core class in the module's root package** | n |

#### §B — wearable-domain files that become weapon's after flip 2's `WearableAddon` relocation

Verified with `grep -rln "gadget\.wearable\|weapon\.wearable" gangland-impl/src` (17 hits; 12 not already in §A).

| # | path (under `gangland-impl/src/main/java/org/luckyraven/gangland/`) | role | uses | decision |
|---|---|---|---|---|
| 56 | `command/sub/item/ItemCommand.java` | command | `WearableAddon` (`:13,:30,:37,:43,:64-65`) | **SPLIT** — drop `WearableAddon` and the `ItemWearableCommand` construction; query `CommandContribution`s at path `item` (T7) |
| 57 | `command/sub/item/wearable/ItemWearableCommand.java` | command | `WearableAddon` (`:11`) | **MOVE** → `weapon.command.wearable` |
| 58 | `command/sub/item/wearable/ItemWearableGiveCommand.java` | command | `WearableAddon` (`:14`) | **MOVE** → `weapon.command.wearable` |
| 59 | `command/sub/item/wearable/ItemWearableInfoCommand.java` | command | `WearableAddon` (`:13`) | **MOVE** → `weapon.command.wearable` |
| 60 | `command/sub/item/wearable/ItemWearableListCommand.java` | command | `WearableAddon` (`:10`) | **MOVE** → `weapon.command.wearable` |
| 61 | `config/KernelConfig.java` | config | `fm.addFile(... "ammunition", "items" ...)` `:173`, `fm.addFile(... "wearables", "items" ...)` `:175` | **SPLIT** — delete those two lines; the module registers both with the module classloader (T18/T20) |
| 62 | `item/serializer/WearableItemSerializer.java` | serializer | only `item.wearable.Wearable` (core) | **MOVE** → `weapon.item` — a **cohesion** move, not compile-forced (§6 C-2) |
| 63–67 | *(the five §A rows 28, 31, 49, 50, 54 — `WearableConverter`, `WearableRefresher`, `Wearable{Buy,Sell}Sign`, `WearableSignValidator`)* | | | already listed above |

#### §C — already-flipped **gadget module** files this flip must edit

T9 strips two constructor parameters from `CarBuySign`/`CarSellSign` (§A rows 45–46), so flip 2's call sites have to
follow or the gadget module fails to compile with an arity error. These are gadget-module edits made by the weapon
flip; T0 locates them, T9 step 3 performs them.

| # | path | role | uses | decision |
|---|---|---|---|---|
| 68 | `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/CarSignContribution.java` | sign contribution (created by flip 2 T12) | passes `weaponService`, `ammunitionManager` into `new CarBuySign(...)` / `new CarSellSign(...)` | **EDIT** — drop both fields, both ctor params and both arguments (T9 step 3) |
| 69 | `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetModuleConfig.java` | module config (created by flip 2 T12 step 4) | `@Bean carSignContribution(UserManager, CarAddon, WeaponService, AmmunitionManager)` | **EDIT** — drop the `WeaponService`/`AmmunitionManager` params, args and imports; removes a needless gadget → weapon bean edge (T9 step 3) |

### 1.2 Feature-side files that reference impl

**None today.** `grep -rhE "^import org\.luckyraven\.gangland\.[a-zA-Z.]+" gangland-features/gangland-weapon/src/main --include=*.java | grep -v gangland.weapon`
returns only core-infra types: `compatibility.recoil.RecoilCompatibility` (version-impl),
`core.downed.DownedPlayerRegistry` (gangland-core), `item.contract.WearableEquipService`, `item.wearable.Wearable`,
`item.wearable.WearableTrait` (gangland-item). All are in the core jar and reachable through the module classloader's
parent. Keystone packages used: `bean`, `exception`, `item`, `persistence`, `sound`, `timer`, `util`.
After flip 2's T2, `WearableAddon` adds `keystone.permission` usage — flip 2's T2 step 4 already adds whatever the
compiler demands to the weapon pom.

**`net.wesjd:anvilgui` and `com.viaversion:viaversion-api` are declared in `gangland-features/gangland-weapon/pom.xml`
but never imported** (`grep -rn "net.wesjd\|com.viaversion" gangland-features/gangland-weapon/src` → empty). AnvilGUI
is **relocated** by the shade plugin (`gangland-build/pom.xml`: `net.wesjd.anvilgui` →
`org.luckyraven.gangland.dependency.anvilgui`), so a module jar must never reference it. Both are removed in T1.
XSeries is **not** relocated (see the comment in `gangland-build/pom.xml`) — it stays.

### 1.3 Beans the feature contributes today

| Config class | method | phase | parameters | target |
|---|---|---|---|---|
| `FileConfig.java:167-170` | `ammunitionManager(Settings)` | FILE | `Settings` — **ordering only, keep it** | `WeaponFileConfig` |
| `FileConfig.java:172-178` | `ammunitionAddon(FileManager, AmmunitionManager, PlaceholderService)` | FILE | 3 | `WeaponFileConfig` (+ registers `items/ammunition.yml`) |
| `FileConfig.java:136-139` | `blockRegenerationSettings()` | FILE | none | `WeaponFileConfig` |
| `FileConfig.java:196-202` | `wearableAddon(PermissionManager, FileManager, PlaceholderService)` | FILE | 3 | `WeaponFileConfig` (+ registers `items/wearables.yml`) |
| `FileConfig.java:214-217` | `weaponAddon(AmmunitionAddon, PlaceholderService)` | FILE | `AmmunitionAddon` is **ordering-only** (unused in the body) — **keep it** | `WeaponFileConfig` |
| `FileConfig.java:219-234` | `weaponLoader(FileManager, AmmunitionManager, WeaponAddon)` | FILE | 3 + 5 `FileHandler` registrations | `WeaponFileConfig` |
| `GameplayConfig.java:181-184` | `weaponManager(WeaponAddon, GanglandDatabase)` | CONFIG | 2 | `WeaponModuleConfig` |
| `GameplayConfig.java:186-189` | `weaponService(WeaponManager)` | CONFIG | 1 | `WeaponModuleConfig` |
| `GameplayConfig.java:191-194` | `blockDamageManager(GanglandBlockRegenerationSettings)` | CONFIG | 1 | `WeaponModuleConfig` |
| `GameplayConfig.java:196-199` | `weaponVisualSpawner()` | CONFIG | none | `WeaponModuleConfig` |
| `GameplayConfig.java:201-210` | `weaponRaytracer(WeaponManager, WearableAddon, BlockDamageManager, WeaponVisualSpawner)` | CONFIG | 4 + `Bukkit.getServicesManager().register(...)` at `:208` | `WeaponModuleConfig` |
| `GameplayConfig.java:212-215` | `wearableService(WearableAddon)` | CONFIG | 1 | `WeaponModuleConfig` |
| `GameplayConfig.java:307-310` | `wearableEquipService(WearableAddon)` | CONFIG | 1 | `WeaponModuleConfig` |
| `GameplayConfig.java:218-221` | `recoilCompatibility(CompatibilityWorker)` | CONFIG | 1 | `WeaponModuleConfig` — only weapon consumes it |
| `GameplayConfig.java:223-226` | `pluginFireRegistry()` | CONFIG | none | `WeaponModuleConfig` |
| `GameplayConfig.java:259-268` | `signManager(…, WeaponManager, AmmunitionManager, …, WearableAddon, …)` | CONFIG | 9 (8 after flip 2 drops `CarAddon`) | **stays in core**, loses `WeaponManager`, `AmmunitionManager` and `WearableAddon` (T10) |
| `ItemConfig.java:37-40` | `weaponConverter(WeaponService)` | CONFIG | 1 | `WeaponModuleConfig` |
| `ItemConfig.java:42-45` | `ammunitionConverter(AmmunitionManager)` | CONFIG | 1 | `WeaponModuleConfig` |
| `ItemConfig.java:47-50` | `wearableConverter(WearableService)` | CONFIG | 1 | `WeaponModuleConfig` |
| `ItemConfig.java:108-111` | `weaponItemSerializer()` | CONFIG | none | `WeaponModuleConfig` |
| `ItemConfig.java:113-116` | `ammunitionItemSerializer()` | CONFIG | none | `WeaponModuleConfig` |
| `ItemConfig.java:118-121` | `wearableItemSerializer()` | CONFIG | none | `WeaponModuleConfig` |
| `ItemConfig.java:157-160` | `weaponRefresher(WeaponService)` | CONFIG | 1 | `WeaponModuleConfig` |
| `ItemConfig.java:162-165` | `wearableRefresher(WearableService)` | CONFIG | 1 | `WeaponModuleConfig` |
| `ItemConfig.java:172-175` | `ammunitionItemRefresher(AmmunitionManager)` | CONFIG | 1 | `WeaponModuleConfig` |
| `SchedulingConfig.java:68-82` | `periodicalUpdates(…, WeaponManager)` | CONFIG | 5 | **stays in core**, loses `WeaponManager` (T4) |
| `ShopConfig.java:104-107` | `shopDisplayResolver(WeaponService)` | CONFIG | 1 | **stays in core**, loses the param (T5) |

### 1.4 Resources

**YAML defaults — 22 files in `gangland-impl/src/main/resources/weapon/`:**
`awp.yml`, `crowbar.yml`, `flamethrower.yml`, `flashbang.yml`, `golden_ak47.yml`, `grenade.yml`, `knife.yml`,
`machete.yml`, `minigun.yml`, `molotov.yml`, `mp5.yml`, `pistol.yml`, `ray_gun.yml`, `revolver.yml`, `rifle.yml`,
`rocket_launcher.yml`, `sawn_off.yml`, `shotgun.yml`, `smoke_grenade.yml`, `steyr_aug.yml`, `syringe_gun.yml`,
`tomahawk.yml`.

**Plus 2 files in `gangland-impl/src/main/resources/items/`:** `ammunition.yml` (read by `AmmunitionAddon`),
`wearables.yml` (read by `WearableAddon`). `cars.yml` is flip 2's; `unique_items.yml` and `money.yml` stay core.

All 24 move to `gangland-features/gangland-weapon/src/main/resources/` at **exactly the same relative path**
(`weapon/<name>.yml`, `items/<name>.yml`), so the 5-arg `FileHandler(plugin, name, dir, ".yml", moduleClassLoader)`
resolves them from the module jar — and the same paths must disappear from the core jar.

**`commands.json` keys to cut from `gangland-impl/src/main/resources/commands.json` (12):**
`weapon_help`, `weapon_list`, `weapon_give`, `weapon_info`, `ammunition_help`, `ammunition_list`, `ammunition_give`,
`ammunition_info`, `item_wearable`, `item_wearable_give`, `item_wearable_info`, `item_wearable_list`.
The core file has **225 keys today**; after this flip it must have **exactly 12 fewer** than whatever flips 1–3 left.

**Messages keys that stay in core** (`file/configuration/Messages.java` — strings only, no weapon types):
`RECEIVED_AMMO :32`, `GAVE_AMMO :33`, `RECEIVED_WEAPON :34`, `GAVE_WEAPON :35`, `INVALID_AMMO :315`,
`INVALID_WEAPON :316`, `AMMO_NOT_IN_INVENTORY :322`, `AMMO_BOUGHT :323`, `AMMO_SOLD :324`, `NOT_ENOUGH_AMMO :325`,
`DEAD_USING_WEAPON :328`, `WEAPON_LIST_HEADER :566`, `AMMO_LIST_HEADER :567`. **No edit** (rule 5).

**`Settings`** has no weapon type: only `copStartingAmmoMagazines :153,:623` (cops) and the block-regeneration knobs
at `:193`. **No edit.**

**`module.properties`:** `gangland-features/gangland-weapon/src/main/resources/org/luckyraven/gangland/weapon/module.properties`
containing `module.name=${project.name}` (mirrors gangland-mail's).

### 1.5 Tests

| Test | Where | References weapon | Decision |
|---|---|---|---|
| `gangland-impl/src/test/.../bootstrap/PeriodicalUpdatesTest.java` | impl | `WeaponManager` mock (`:14,:47,:58,:71,:86,:101`) | **STAYS** — drop the mock and ctor arg (T23) |
| `gangland-impl/src/test/.../data/plugin/PluginDataCleanupServiceTest.java` | impl | `WeaponRepository`, `Weapon`, `WeaponManager` | **STAYS** — rewrite against a fake `DataCleanupTask` (T23) |
| `gangland-impl/src/test/.../command/data/InformationManagerTest.java:41` | impl | count only | `225` → **minus 12** (T25) |
| 13 existing weapon tests in `gangland-features/gangland-weapon/src/test/**` | module | — | untouched |

**Why no impl test moves:** `gangland-impl` publishes **no test-jar** (`grep -n test-jar gangland-impl/pom.xml` →
empty; only `gangland-core/pom.xml:34-43` does), and `PluginDataCleanupServiceTest` depends on
`gangland-impl/src/test/.../support/{SettingsFixture,FakeMessageProvider}.java`. Moving it would need a test
dependency in the module pom — forbidden by the house rules.

### 1.6 Seams

#### (a) Item registries — flip 2's mechanism, no new interface  **[from `gadget.md` §1.6(a)/(b)]**

The module's `@Bean` methods take `ItemConverterRegistry`, `ItemSerializerRegistry`, `ItemRefresherRegistry` as
parameters and call `register`; the parameter is the `BeanGraph` ordering edge
(`Keystone/keystone-bean/.../BeanFactory.java:388-397`), so the core registry bean is always built first. All three
registries are mutable after construction and read at gameplay time.

**Converters** (`ItemConverterRegistry`) are a `Map` keyed by `ItemKind`/string — order-independent. The module
registers `ItemKind.WEAPON`, `ItemKind.AMMUNITION` + the `"ammo"` alias, and `ItemKind.WEARABLE`.

**Serializers** — flip 2 adds `register(predicate, serializer, priority)` and `CATCH_ALL_PRIORITY` with a stable
`List.sort` descending by priority, and drops the core `MATERIAL` catch-all to `CATCH_ALL_PRIORITY`
(`ItemConfig.java:151`). Today's order is UNIQUE → WEAPON → AMMUNITION → WEARABLE → CAR → MONEY → MATERIAL.
After the flip:

| registered by | entries | priority |
|---|---|---|
| core `ItemConfig.itemSerializerRegistry` | `UNIQUE`, `MONEY` | default (`0`) |
| core `ItemConfig.itemSerializerRegistry` | `MATERIAL` | `CATCH_ALL_PRIORITY` |
| gadget module | `CAR` | default (`0`) |
| **weapon module** | `WEAPON`, `AMMUNITION`, `WEARABLE` | **default (`0`)** — the plain 2-arg overload |

Resulting order: `UNIQUE, MONEY, CAR, WEAPON, AMMUNITION, WEARABLE, MATERIAL` (module order depends on which module
loads first). **The only precedences that carry meaning are preserved:** `UNIQUE` stays ahead of `WEAPON`
(a unique weapon stamps both tags and must serialize as unique — `ItemConfig.java:143-145`) because the core registry
bean is built first and `List.sort` is stable; `MATERIAL` stays last via `CATCH_ALL_PRIORITY`. `MONEY`/`CAR` moving
ahead of `WEAPON` is inert — `ItemPredicates.MONEY` tests `MoneyItemUtil.MARKER_TAG` and `CAR` tests
`CarKey.CAR_ID`, neither of which a weapon, ammo or wearable stack ever carries. **The weapon module therefore uses
the plain 2-arg `register` and does not need an explicit priority.** Record this reasoning in §7.

**Refreshers** — see **§6 C-1**: `ItemRefresherRegistry` needs the same overload. Today
`ItemConfig.java:182-191` registers `weaponRefresher, wearableRefresher, uniqueItemRefresher, ammunitionItemRefresher,
carItemRefresher` in that order (first match wins), and `UniqueItemRefresher.canRefresh` =
`UniqueItemUtil.isUniqueItem(source)` matches a unique weapon. Flip 4 mirrors flip 2's API onto
`ItemRefresherRegistry` (`CATCH_ALL_PRIORITY`, `register(refresher, priority)`, stable descending sort) and the
weapon module registers **at two different priorities**, because today's order puts two of its refreshers *before*
`UniqueItemRefresher` and one *after* it:

| refresher | priority | why |
|---|---|---|
| `WeaponRefresher` | **10** | today it is registered first, ahead of `uniqueItemRefresher`; a unique **weapon** carries both tags and must be rebuilt as a weapon |
| `WearableRefresher` | **10** | today it is second, also ahead of `uniqueItemRefresher` |
| `AmmunitionItemRefresher` | **default `0`** | today it is registered *after* `uniqueItemRefresher`, so a unique **ammunition** stack must still be rebuilt as a unique item. Registered at the default priority after the core registry bean (the parameter is the ordering edge), the stable sort lands it behind `uniqueItemRefresher` — exactly today's position |

Post-flip order: `weapon, wearable` (10) → `unique, ammunition, car` (0). That is today's order
(`weapon, wearable, unique, ammunition, car`) reproduced exactly, for **all three** refreshers.

#### (b) `SignTypeContribution` · `SignViewProvider` · `SignContributions` — flip 2's, reused  **[`gadget.md` §1.6(c)–(e)]**

```java
package org.luckyraven.gangland.sign.extension;                       // created by flip 2

public interface SignTypeContribution { List<Sign> signs(String signPrefix); }
public interface SignViewProvider     { boolean open(Player player, String content); }
public final class SignContributions  { /* from(container), none(), createSigns(prefix), openView(player, content) */ }
```

* **Registered by:** `WeaponModuleConfig` — one `WeaponSignContribution` bean (weapon-buy, weapon-sell, ammo-buy,
  ammo-sell, wearable-buy, wearable-sell) and one `WeaponSignViewProvider` bean (weapon / ammunition / wearable views).
* **Consumed by:** `SignManager.setupSigns()` and `ViewInventoryAspect.execute`, both **lazily**.
* **Moment:** `setupSigns()` runs from `SignService.initialize()`
  (`gangland-ui/sign-api/.../SignService.java:29-43`), which Keystone's convention pass invokes after every phase and
  after `@PostConstruct` (`BeanFactory.instantiate` → `runInitialize`, `:294,:581-607`; `findInitializeMethod` walks
  superclasses, and `SignManager` is neither a `FileInitializer` nor a `BeanLifecycle`, so it is not skipped). Flip 2
  deleted the explicit `manager.initialize()` at `GameplayConfig.java:266`. ✔
* **I designed no second mechanism** — my earlier draft's `ItemViewProvider` is deleted in favour of
  `SignViewProvider`.

#### (c) `MetricsContributor` — new, weapon plan owns it

```java
package org.luckyraven.gangland.metrics;   // new package in gangland-impl

/**
 * Extra bStats charts a runtime module contributes. The core cannot name a module type, so a module registers a bean
 * implementing this contract; {@code Gangland.bStats()} pulls every implementation out of the container and wraps
 * each entry in a {@code SingleLineChart}. bStats types never cross this boundary — they are shaded and relocated
 * into the core jar, so a module jar must not reference them.
 */
public interface MetricsContributor {

	/** Chart id → value supplier. Ids must be unique across the plugin. */
	Map<String, IntSupplier> singleLineCharts();
}
```

* **Registered by:** `WeaponModuleConfig` → `WeaponMetricsContributor` returning
  `Map.of("number_of_weapons", weaponAddon::size)`.
* **Consumed by:** `Gangland.bStats()` via `context.getContainer().getAllInstances(MetricsContributor.class)`.
* **Moment:** `onEnable()` calls `context.bootstrap()` (`Gangland.java:93`) — which ends with
  `moduleLoader.enableAll(container)` — **before** `bStats()` (`Gangland.java:99`). ✔
* **Why a seam:** `Gangland.java` is the entry point; it can never move.

#### (d) `DataCleanupTask` — new, weapon plan owns it

```java
package org.luckyraven.gangland.data.plugin;

/** One unit of scheduled data cleanup. Modules register beans; {@link PluginDataCleanupService} runs them all. */
public interface DataCleanupTask {
	String name();
	int cleanup();          // @return records cleared, for the debug log line
}
```

* **Registered by:** `WeaponModuleConfig` → `WeaponDataCleanupTask` (today's
  `PluginDataCleanupService.resetWeapons()` body verbatim, `instanceof WeaponRepository` guard included).
* **Consumed by:** `PluginDataCleanupService.performCleanup()`, **lazily** via a
  `Supplier<List<DataCleanupTask>>` — `PeriodicalUpdates implements BeanLifecycle`, so its `onInitialize(true)`
  fires *inside* the CONFIG phase right after the bean is registered (`BeanFactory.instantiate`), and an eager pull
  there would miss module beans. The supplier is only invoked from the auto-save timer, long after bootstrap. ✔

#### (e) `NbtTagCatalog` — new, rule 3a registry bean

```java
package org.luckyraven.gangland.item;   // gangland-impl

/** Ordered catalogue of "interesting" NBT tag names for {@code /glw debug nbt brief}. */
public final class NbtTagCatalog {
	private final List<String> tags = new ArrayList<>();
	public void register(String... names) { … }   // ignores null / blank / duplicates
	public List<String> tags() { return List.copyOf(tags); }
}
```

Core bean in `ItemConfig` registers every `LootChestWandTag`; the module's `@Bean` takes the catalog and registers
every `WeaponTag`. `ReadNBTCommand` reads it at command-execution time — order-safe.

#### (f) `ShopDisplayNameProvider` — new, weapon plan owns it

```java
package org.luckyraven.gangland.file.configuration.shop;   // beside GanglandShopDisplayResolver

public interface ShopDisplayNameProvider {
	/** @return the clean display name, or {@code null} when this provider does not own the item. */
	@Nullable String cleanDisplayName(ItemStack item);
}
```

Registered by `WeaponModuleConfig` → `WeaponShopDisplayNameProvider` (today's `GanglandShopDisplayResolver:28-38`).
Consumed lazily by `GanglandShopDisplayResolver` via `Supplier<List<ShopDisplayNameProvider>>`.

#### (g) `DeathMessageContributor` — new, weapon plan owns it

```java
package org.luckyraven.gangland.listener.death;   // gangland-impl

/** A module's answer to "what killed this player", for the core death-message builder. */
public interface DeathMessageContributor {
	@Nullable Resolved resolve(Player victim, Player killer);
	record Resolved(@Nullable String template, String itemName) { }   // template null → use the global list
}
```

Registered by `WeaponModuleConfig` → `WeaponDeathMessageContributor`. Consumed lazily by
`PlayerDeathListener.buildDeathMessage`.

---

## 2. Ordered tasks

> Paths are relative to the repo root. Compile gate after every group. House rules apply throughout: Spigot only,
> method braces on their own lines, Lombok `@CustomLog`, block-style YAML with `Capitalized_Underscore_Separated`
> keys, `ChatUtil.color()` with `&` codes, never add test dependencies to a module pom, `commands.json` entry in the
> jar that owns the command. Executors never commit; fill §7 after every task.
>
> **Line numbers are as of `d6bb33ac`**; after flips 1–3 or the `0.8.3` merge have touched a file — in particular
> `ShopConfig.java`, `SignManager.java`, `GameplayConfig.java`, `ItemConfig.java`, `KernelConfig.java` and
> `gangland-impl/pom.xml` — locate the symbol by name rather than by line and record the drift in §7. Every
> "Done when" is grep- or command-based, so a stale line reference can never silently pass.

---

### Group A — reconcile, poms, module skeleton

### T0 — Reconcile against the post-flip-3 tree  (group A, 0 files changed)

- **Do:**
  1. `grep -rl "org\.luckyraven\.gangland\.weapon\." gangland-impl/src/main | sort > /tmp/weapon_impl_now.txt`;
     diff against §1.1 §A. Record every added/removed path in §7.
  2. `grep -rln "weapon\.wearable\|gadget\.wearable" gangland-impl/src | sort` — diff against §1.1 §B.
  3. **Gate W (blocking).** Confirm flip 2's T2 landed:
     `test -f gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/wearable/WearableAddon.java`
     and `grep -rn "gadget\.wearable" --include=*.java gangland-impl gangland-features gangland-ui gangland-infra`
     is empty. **If `WearableAddon` is still in `gangland-gadget`, stop and report** — every wearable task below
     depends on it.
  4. **Gate S (blocking).** Confirm flip 2's sign seam landed:
     `ls gangland-impl/src/main/java/org/luckyraven/gangland/sign/extension/` lists `SignTypeContribution.java`,
     `SignViewProvider.java`, `SignContributions.java`, and
     `grep -n "manager.initialize()" gangland-impl/src/main/java/org/luckyraven/gangland/config/GameplayConfig.java`
     is empty. **If not, stop and report.**
  5. **Gate I (blocking).** Confirm flip 2's serializer priority landed:
     `grep -n "CATCH_ALL_PRIORITY" gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/ItemSerializerRegistry.java`
     and `gangland-impl/src/main/java/org/luckyraven/gangland/config/ItemConfig.java`.
  6. Confirm `config/CopsAndGadgetsConfig.java` no longer exists or no longer imports
     `org.luckyraven.gangland.weapon.*`. **If it does, stop and report** — flips 1–2 are incomplete.
  7. Confirm `gangland-features/cops-n-crooks/src/main/resources/module.yml` and
     `gangland-features/gangland-gadget/src/main/resources/module.yml` exist and note their current `Depends:` lists.
  8. Note whether `sign/type/trade/car/Car{Buy,Sell}Sign.java` are still in `gangland-impl` or moved to
     `gangland-gadget` — T9 edits them wherever they are.
  9. **Gate C (car-sign call sites, finding 5).** Locate flip 2's two files and record their exact paths and the
     symbol names T9 must edit:
     `ls gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/CarSignContribution.java`
     and `grep -n "carSignContribution\|WeaponService\|AmmunitionManager"
     gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetModuleConfig.java`.
     If flip 2 named them differently, record the actual names in §7 — T9 step 3 edits whatever T0 found.
- **Why:** the plan was written against `d6bb33ac`; three flips land in between.
- **Done when:** §7 row T0 records the diffs, all five gates (W, S, I, the `CopsAndGadgetsConfig` check, C), the
  car-sign location, and the two gadget call-site paths.
- **Watch out:** scope every grep to the module directories — never `grep -rl` from the repo root, which would pick up
  `.claude/worktrees/**` copies.

### T1 — Poms  (group A, 5 files)

- **Do:**
  1. `gangland-impl/pom.xml` — **delete** the `gangland-weapon` dependency block (~line 57).
  2. `gangland-features/gangland-weapon/pom.xml`:
     - **add** at default scope (mirroring `gangland-mail/pom.xml`): `org.luckyraven:keystone-command`,
       `org.luckyraven:keystone-module`;
     - **add** at `provided`: `org.luckyraven:gangland-impl` (copy gangland-mail's explanatory comment),
       `org.luckyraven:gangland-domain`, `org.luckyraven:sign-api`, `org.luckyraven:inventory-api`;
     - **remove** the unused `net.wesjd:anvilgui` and `com.viaversion:viaversion-api` blocks (§1.2);
     - keep everything else unchanged, including whatever flip 2's T2 step 4 added for `WearableAddon`
       (expect `keystone-persistence`, `keystone-common`, XSeries — plus `keystone-permission` if the compiler asks);
     - **add no test dependencies** (root pom `pom.xml:548-558` supplies junit-jupiter, mockito-core,
       keystone-testkit to every module).
  3. `gangland-features/cops-n-crooks/pom.xml` — set the `gangland-weapon` dependency to `<scope>provided</scope>`
     (currently default/compile, ~line 51) and add
     `<!-- Runtime module `weapon`: resolved at runtime through the module classloader. See module.yml Depends. -->`.
  4. `gangland-features/gangland-gadget/pom.xml` — `gangland-weapon` is already `provided` (~line 47); add the same
     comment. No scope change.
  5. `gangland-build/pom.xml` — three edits mirroring the existing `gangland-mail` (and now gadget/cops/turf) entries:
     add `<exclude>org.luckyraven:gangland-weapon</exclude>` to the shade `<artifactSet><excludes>`; add an
     `<artifactItem>` for `org.luckyraven:gangland-weapon:${project.version}` to `copy-runtime-modules`; add
     `org.luckyraven:gangland-weapon` with `<version>${project.parent.version}</version>` and
     `<scope>provided</scope>` to `<dependencies>`.
- **Why:** M1 — removing the compile edge makes the compiler enumerate the work list.
- **Done when:** `mvn clean install -DskipTests -q` fails and **every** error is in `gangland-impl` (the reactor now
  orders impl before weapon). Save the output to `/tmp/weapon_worklist.txt`; it must be a subset of §1.1's non-KEEP
  rows.
- **Watch out:** always pass `-am` when building a single module (`${revision}` parent). Add to the existing
  `<excludes>` / `<artifactItems>` blocks; do not replace them.

### T2 — Module skeleton  (group A, 4 files)

- **Do:**
  1. `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/WeaponModule.java`, modelled on
     `gangland-features/gangland-mail/.../MailModule.java`:
     ```java
     @CustomLog
     public final class WeaponModule implements KeystoneModule {

     	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.weapon.listener";
     	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.weapon.command";
     	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.weapon.database";

     	@Override
     	public void configure(ModuleRegistrar registrar) {
     		registrar.configuration(WeaponFileConfig.class)
     		         .configuration(WeaponModuleConfig.class)
     		         .listenerPackage(LISTENER_PACKAGE)
     		         .commandPackage(COMMAND_PACKAGE)
     		         .repositoryPackage(REPOSITORY_PACKAGE);
     	}

     	@Override
     	public void onEnabled(ModuleContext context) {
     		log.info("Weapon module {} enabled", context.module().descriptor().version());
     	}

     	@Override
     	public void onDisabled() {
     		log.debug("Weapon module disabled");
     	}
     }
     ```
  2. `src/main/resources/module.yml` (jar root):
     ```yaml
     # Keystone module descriptor - read by Gangland's ModuleLoader from plugins/Gangland_Warfare/modules/.
     Id: weapon
     Name: Gangland Weapons
     Version: ${project.version}
     Main: org.luckyraven.gangland.weapon.WeaponModule
     Host_Api: 0.8
     Artifact: org.luckyraven:gangland-weapon
     ```
     No `Depends:` — weapon depends on no other module.
  3. `src/main/resources/org/luckyraven/gangland/weapon/module.properties` containing exactly
     `module.name=${project.name}`.
  4. `src/main/resources/commands.json` — start as `{}`; T22 fills it.
  5. Empty-bodied `WeaponFileConfig` (`@Configuration(phase = Phase.FILE)`, constructor
     `(Gangland gangland, ModuleLoader moduleLoader)`) and `WeaponModuleConfig` (`@Configuration`, constructor
     `(Gangland gangland, DependencyContainer container)`) in `org.luckyraven.gangland.weapon`, so `WeaponModule`
     compiles. Both constructor types are registered in the container by `GanglandContext`'s constructor.
- **Why:** M2.
- **Done when:** all five paths exist —
  `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/{WeaponModule,WeaponFileConfig,WeaponModuleConfig}.java`,
  `gangland-features/gangland-weapon/src/main/resources/module.yml`,
  `gangland-features/gangland-weapon/src/main/resources/commands.json`,
  `gangland-features/gangland-weapon/src/main/resources/org/luckyraven/gangland/weapon/module.properties`
  (six paths counting `module.properties`); verify with a single
  `ls` of each. Compilation is deferred to gate G1 — the reactor is red by design from T1 until T20.
- **Watch out:** `module.yml` and `commands.json` at the **jar root**; `module.properties` under the **package path**.
  `Host_Api` is `0.8`, not `0.8.4`.

> **⛔ COMPILE GATE A** — build still fails only in `gangland-impl`.

---

### Group B — core seams (metrics, cleanup, shop, NBT)

### T3 — `MetricsContributor`  (group B, 2 files)

- **Do:**
  1. Create `gangland-impl/src/main/java/org/luckyraven/gangland/metrics/MetricsContributor.java` as in §1.6(c).
  2. `gangland-impl/src/main/java/org/luckyraven/gangland/Gangland.java`: delete the import at `:38` and line `:117`;
     at the end of `bStats()` add
     ```java
     // Charts a runtime module contributes. bootstrap() has already run moduleLoader.enableAll(), so every module
     // bean exists by the time onEnable() reaches here.
     List<MetricsContributor> contributors = context.getContainer().getAllInstances(MetricsContributor.class);
     for (MetricsContributor contributor : contributors == null ? List.<MetricsContributor>of() : contributors) {
     	for (Map.Entry<String, IntSupplier> chart : contributor.singleLineCharts().entrySet()) {
     		metrics.addCustomChart(new SingleLineChart(chart.getKey(), () -> chart.getValue().getAsInt()));
     	}
     }
     ```
     plus the `MetricsContributor`, `java.util.List` and `java.util.function.IntSupplier` imports (`java.util.Map` is
     already imported at `:41`).
- **Why:** M9 — the last hard core→feature reference in the entry point.
- **Done when:** `grep -n "WeaponAddon" gangland-impl/src/main/java/org/luckyraven/gangland/Gangland.java` is empty.
- **Watch out:** null-guard `getAllInstances` exactly as `CommandContributions.from` does
  (`command/extension/CommandContributions.java:26-28`).

### T4 — `DataCleanupTask`; de-weaponize `PluginDataCleanupService` + `PeriodicalUpdates`  (group B, 4 files)

- **Do:**
  1. Create `gangland-impl/src/main/java/org/luckyraven/gangland/data/plugin/DataCleanupTask.java` as in §1.6(d).
  2. Rewrite `data/plugin/PluginDataCleanupService.java`: constructor becomes
     `(PluginManager pluginManager, Supplier<List<DataCleanupTask>> tasks)`; delete the `WeaponRepository`, `Weapon`,
     `WeaponManager` imports/fields and the whole `resetWeapons()` method; in `performCleanup(PluginData)` replace the
     `int weaponsReset = resetWeapons();` block with
     ```java
     for (DataCleanupTask task : tasks.get()) {
     	int cleared = task.cleanup();
     	if (logDebug) log.info("Cleanup task '{}' cleared {} record(s)", task.name(), cleared);
     }
     ```
     Leave `validatePluginData`, the scan-date rescheduling and the timing log untouched.
  3. `bootstrap/PeriodicalUpdates.java`: delete the `Weapon`/`WeaponManager` imports (`:18-19`), the `weaponManager`
     field (`:38`) and the parameter from **both** constructors (`:49`, `:60`); add a `DependencyContainer container`
     parameter in the same positions and store it; `initializeCleanupService()` becomes
     ```java
     private void initializeCleanupService() {
     	cleanupService = new PluginDataCleanupService(pluginManager,
     	                                              () -> container.getAllInstances(DataCleanupTask.class));
     }
     ```
  4. `config/SchedulingConfig.java`: delete the `WeaponManager` import (`:22`), the parameter (`:74`) and both
     call-site arguments (`:77`, `:80`); add `DependencyContainer container` as the last parameter and pass it.
- **Why:** M9 + M3.
- **Done when:** `grep -rn -i weapon gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/PeriodicalUpdates.java
  gangland-impl/src/main/java/org/luckyraven/gangland/data/plugin/PluginDataCleanupService.java
  gangland-impl/src/main/java/org/luckyraven/gangland/config/SchedulingConfig.java` is empty.
- **Watch out:** the `Supplier` must be evaluated per `performCleanup()`, never eagerly (§1.6(d)). Keep
  `PeriodicalUpdates`' `@SuppressWarnings("unused") userManager` field and its bean-ordering comment (`:31-36`)
  exactly as-is.

### T5 — `ShopDisplayNameProvider`  (group B, 3 files)

- **Do:**
  1. Create `file/configuration/shop/ShopDisplayNameProvider.java` as in §1.6(f).
  2. `file/configuration/shop/GanglandShopDisplayResolver.java`: delete the `Weapon`, `WeaponService`, `WeaponTag`
     imports and the `weaponService` field; drop `@RequiredArgsConstructor` and write
     `GanglandShopDisplayResolver(Supplier<List<ShopDisplayNameProvider>> providers)` out with braces on their own
     lines; replace the weapon block (`:28-38`) with
     ```java
     for (ShopDisplayNameProvider provider : providers.get()) {
     	String resolved = provider.cleanDisplayName(item);
     	if (resolved != null && !resolved.isBlank()) return resolved;
     }
     ```
     keeping the `ItemMeta` and humanised-material fallbacks. Update the class javadoc (it must stop naming
     `WeaponService`).
  3. `config/ShopConfig.java`: delete the `WeaponService` import (`:48`); `shopDisplayResolver` becomes
     ```java
     @Bean
     public ShopDisplayResolver shopDisplayResolver(DependencyContainer container) {
     	return new GanglandShopDisplayResolver(() -> container.getAllInstances(ShopDisplayNameProvider.class));
     }
     ```
- **Why:** M9 — the resolver is a core contract implementation that must survive with zero modules.
- **Done when:** `grep -n -i weapon gangland-impl/src/main/java/org/luckyraven/gangland/config/ShopConfig.java
  gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/shop/GanglandShopDisplayResolver.java`
  is empty.
- **Watch out:** the weapon branch wraps the result in `ChatUtil.color(...)` — preserve that in the module-side
  provider (T15), not here.

### T6 — `NbtTagCatalog`  (group B, 3 files)

- **Do:**
  1. Create `gangland-impl/src/main/java/org/luckyraven/gangland/item/NbtTagCatalog.java` as in §1.6(e).
  2. `config/ItemConfig.java` — add
     ```java
     @Bean
     public NbtTagCatalog nbtTagCatalog() {
     	NbtTagCatalog catalog = new NbtTagCatalog();
     	for (LootChestWandTag tag : LootChestWandTag.values()) {
     		catalog.register(tag.toString().toLowerCase());
     	}
     	return catalog;
     }
     ```
     (import `org.luckyraven.gangland.lootchest.LootChestWandTag`).
  3. `command/sub/debug/ReadNBTCommand.java` — delete the `WeaponTag` (`:14`) and `LootChestWandTag` imports; add a
     `NbtTagCatalog nbtTagCatalog` constructor parameter + field; replace both `values()` loops in
     `initializeArguments()` with
     ```java
     for (String tagName : nbtTagCatalog.tags()) {
     	if (!itemBuilder.hasNBTTag(tagName)) continue;
     	presentTags.put(tagName, String.valueOf(itemBuilder.getTagData(tagName)));
     }
     ```
- **Why:** M9.
- **Done when:** `grep -n WeaponTag gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/debug/ReadNBTCommand.java`
  is empty and the command still lists loot-chest wand tags.
- **Watch out:** weapon tags used to print **before** loot-chest tags; they now print after. Cosmetic only — record it
  in §7.

> **⛔ COMPILE GATE B**

---

### Group C — command contributions + refresher ordering

### T7 — `CommandContribution` at paths `debug` and `item`  (group C, 2 files) — **p0-wave-3 overlap on `DebugCommand`**

- **Do:**
  1. `command/sub/debug/DebugCommand.java`: delete the `Weapon`/`WeaponManager` imports (`:50-51`), the field
     (`:64`), the constructor parameter (`:76`) and its assignment, and the whole `getGiveGun()` method (`:494-501`)
     plus its call site. Add a `DependencyContainer container` parameter, store
     `this.contributions = CommandContributions.from(container);` (as `GangCommand.java:85`), and at the end of
     `initializeArguments()` append
     ```java
     for (Argument contributed : contributions.createFor("debug", getArgumentTree(), getArgument())) {
     	getArgument().addSubArgument(contributed);
     }
     ```
     Add a javadoc line: *"Accepts `CommandContribution`s at path `debug`."*
  2. `command/sub/item/ItemCommand.java`: delete the `WearableAddon` import (`:13`), field (`:30`), constructor
     parameter (`:37`) and assignment (`:43`), the `ItemWearableCommand` import (`:10`) and its construction
     (`:64-65`) and `arguments.add(wearable)` line. Add a `DependencyContainer container` parameter and
     `CommandContributions.from(container)`; after the existing `getArgument().addAllSubArguments(arguments);` add
     ```java
     for (Argument contributed : contributions.createFor("item", getArgumentTree(), getArgument())) {
     	getArgument().addSubArgument(contributed);
     }
     ```
     Add the same javadoc line for path `item`. The `startsWith("item")` help filter at `:47-53` is unchanged —
     `KernelConfig.informationManager` merges the module's `commands.json` into the same index
     (`KernelConfig.java:62-68`), so `item_wearable*` entries still show in `/glw item help` when the module is
     installed.
- **Why:** M7 — `/glw debug weapon` and `/glw item wearable` are weapon concerns.
- **Done when:** `grep -n -i "weapon\|wearable" gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/debug/DebugCommand.java
  gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/item/ItemCommand.java` is empty.
- **Watch out:** **`DebugCommand.java` is on the `p0-wave-3` list** — merge `0.8.3` first. `documentation/module-loader.md`'s
  "Attaching sub-arguments" section must gain `debug` and `item` to its list of queried paths (T27).

### T8 — `ItemRefresherRegistry` priority overload  (group C, 3 files) — **extension of flip 2's design, see §6 C-1**

- **Do:**
  1. Write `gangland-infra/gangland-item/src/test/java/org/luckyraven/gangland/item/ItemRefresherRegistryPriorityTest.java`
     **first**, using the intended API: register refresher A with the plain overload, then refresher B with
     `priority = 10`, assert `refresh(stack, null)` returns B's result when both `canRefresh`. Add a stability test:
     two default-priority refreshers registered A then B keep A's precedence. Add a third case pinning the
     ammunition rule from §1.6(a): a default-priority refresher registered **after** another default-priority one
     stays behind it, so `ammunitionItemRefresher` (module, default `0`) never overtakes `uniqueItemRefresher`
     (core, default `0`). Run it, capture the compilation failure verbatim as the red state (§3).
  2. `gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/ItemRefresherRegistry.java` — mirror
     flip 2's `ItemSerializerRegistry` change **exactly** (same names, same stable sort):
     ```java
     public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;

     private record Entry(ItemRefresher refresher, int priority) { }

     public void register(ItemRefresher refresher)                  { register(refresher, 0); }
     public void register(ItemRefresher... items)                   { for (ItemRefresher r : items) register(r, 0); }
     public void register(ItemRefresher refresher, int priority) {
     	if (refresher == null) return;
     	entries.add(new Entry(refresher, priority));
     	entries.sort(Comparator.comparingInt(Entry::priority).reversed());   // List.sort is stable
     }
     ```
     `refresh` and `decorate` iterate `entries` and use `entry.refresher()`. Update the class javadoc: insertion
     order is priority order within a tier, and a module that must outrank a core refresher passes an explicit
     priority.
  3. `gangland-impl/src/main/java/org/luckyraven/gangland/config/ItemConfig.java` — leave the core registrations on
     the plain overload; extend the comment above `itemRefresherRegistry` to say that the weapon and wearable
     refreshers now arrive from the weapon module at priority 10 (ahead of `uniqueItemRefresher`) while the
     ammunition refresher arrives at the default priority (behind it) — reproducing today's
     `weapon, wearable, unique, ammunition, car` order.
- **Why:** without it, `UniqueItemRefresher` (core, registered first) would beat `WeaponRefresher` (module, appended)
  for a unique weapon, which carries both NBT tags (`ItemConfig.java:143-145`).
- **Done when:** `mvn -q -pl gangland-infra/gangland-item -am test` green and the new test passes.
- **Watch out:** `List.sort` (stable) — **not** a `TreeSet` or `PriorityQueue`. Do not touch
  `ItemConverterRegistry` (map-keyed) or `ItemSerializerRegistry` (flip 2 already did it).

> **⛔ COMPILE GATE C**

---

### Group D — sign layer, on flip 2's seam

### T9 — Strip `BaseTradeSign` and its non-weapon subclasses  (group D, 5–7 files)

- **Do:**
  1. `sign/type/trade/BaseTradeSign.java`: delete the `Weapon`, `WeaponService`, `Ammunition`, `AmmunitionManager`,
     `types.WeaponType`, `ItemBuilder`, `ItemSimilarityChecker`, `StandardCharsets`, `UUID` imports (all become
     unused); delete the `weaponService`/`ammunitionManager` fields, `@Getter` and `@RequiredArgsConstructor`;
     delete `getWeaponItem`, `getAmmoItem`, `weaponSimilarityChecker`, `ammoSimilarityChecker`. Keep **only**
     `getUniqueOrMaterialItem(String, UniqueItemAddon)`; the class stays `abstract … implements Sign` with no state.
  2. Drop the two now-absent `super(weaponService, ammunitionManager)` arguments, the matching constructor parameters
     and the `WeaponService`/`AmmunitionManager` imports from:
     `sign/type/trade/BuySign.java` (`:24-25,:40-42`), `sign/type/trade/SellSign.java` (`:24-25,:40-42`), and
     `sign/type/trade/car/Car{Buy,Sell}Sign.java` (`:26-27,:40-41`) — **at whichever module T0 found the car signs
     in** (core `gangland-impl` or `gangland-gadget`).
  3. **Fix gadget's call sites for the shorter car-sign constructors** (flip 2 created them; without this the gadget
     module fails to compile with an arity error):
     - `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/CarSignContribution.java`
       — drop the `weaponService` / `ammunitionManager` fields and constructor parameters, and remove those two
       arguments from the `new CarBuySign(...)` and `new CarSellSign(...)` calls inside `signs(String signPrefix)`;
     - `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetModuleConfig.java`
       — drop the `WeaponService` and `AmmunitionManager` parameters from
       `@Bean carSignContribution(@Qualifier("online") UserManager<Player>, CarAddon, WeaponService, AmmunitionManager)`
       and from its `new CarSignContribution(...)` call, plus the two now-unused imports. This also deletes a
       needless gadget → weapon bean edge.
  4. `sign/SignManager.java` — update the `new BuySign(...)` (`:127`), `new SellSign(...)` (`:136`) constructor calls.
     (The car calls at `:192-208` were removed by flip 2; the wearable calls at `:174-183` are removed in T10.)
- **Why:** rule 2 — the shared base mixed a core concern (unique/material lookup) with a weapon concern.
- **Done when:** `grep -n -i "weapon\|ammunition" gangland-impl/src/main/java/org/luckyraven/gangland/sign/type/trade/BaseTradeSign.java`
  is empty; none of the subclasses import a weapon type; and
  `grep -rn "WeaponService\|AmmunitionManager" gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/CarSignContribution.java
  gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/GadgetModuleConfig.java`
  is empty.
- **Watch out:** none of these subclasses ever *used* the two params (verified per-file: only the import, ctor and
  `super` lines mention them). **Pure signature change, no behaviour change.** Step 3 is a **gadget-module** edit made
  by the weapon flip — call it out in the commit message and in §7 so the reviewer knows why gadget changed.

### T10 — Weapon + wearable sign types leave `SignManager`  (group D, 3 files) — **p0-wave-3 overlap**

- **Do:**
  1. `sign/SignManager.java`: delete the `WeaponBuySign`, `WeaponSellSign`, `AmmoBuySign`, `AmmoSellSign`,
     `WearableBuySign`, `WearableSellSign`, `WeaponService`, `AmmunitionManager`, `WearableService` imports; delete
     the `weaponService`, `ammunitionManager` and `wearableService` fields and constructor parameters; delete the six
     blocks that build weapon-buy / weapon-sell / ammo-buy / ammo-sell (`:88-124`) and wearable-buy / wearable-sell
     (`:168-186`). The `SignContributions` resolution flip 2 added at the end of `setupSigns()` already picks the
     module's replacements up — **do not add a second loop**.
  2. `config/GameplayConfig.java` — in `signManager(...)` (`:259-268`) delete the `WeaponManager weaponManager`,
     `AmmunitionManager ammunitionManager` and `WearableAddon wearableAddon` parameters and their constructor
     arguments. Do **not** re-add `manager.initialize()` — flip 2 deleted it deliberately.
  3. **Re-baseline flip 2's test.** Run
     `gangland-impl/src/test/java/org/luckyraven/gangland/sign/SignManagerContributionTest.java`. Flip 2 writes it to
     assert **containment** — that the contributed definition is appended and `glw-buy`, `glw-sell`, `glw-view`,
     `glw-wanted`, `glw-bounty` are still present — in which case it stays green and needs no edit. **If it still
     asserts a total count** (flip 2's draft computed `11`), set that total to **5**: this task removes the six
     weapon/ammo/wearable rows and flip 2 already removed the two car rows, from the 13 `SignType`s `SignManager`
     builds today (`SignManager.java:92,101,110,119,128,137,146,156,165,174,184,194,203`).
- **Why:** M9; the core cannot name a weapon or wearable sign.
- **Done when:** `mvn -q -pl gangland-impl -am test -Dtest=SignManagerContributionTest` is green, and that test proves
  the five surviving core definitions (`glw-buy`, `glw-sell`, `glw-view`, `glw-wanted`, `glw-bounty`) are present
  while no weapon/ammo/wearable definition is built by `SignManager` itself:
  `grep -c "SignType(" gangland-impl/src/main/java/org/luckyraven/gangland/sign/SignManager.java` → `5`.
- **Watch out:** **`GameplayConfig.java` is on the `p0-wave-3` list.** Before editing, re-verify Gate S from T0 —
  `SignContributions.from(container)` must be resolved **inside** `setupSigns()`, not in the constructor, or every
  contributed sign silently disappears.

### T11 — `ViewSign` / `ViewInventoryAspect` / `ViewSignValidator` become domain-free  (group D, 4 files)

- **Do:**
  1. `sign/aspect/ViewInventoryAspect.java`: delete the `Weapon`, `WeaponService`, `Ammunition`,
     `AmmunitionManager`, `dto.AmmunitionData`, `types.gun.GunWeapon`, `WearableService`, `Wearable`,
     `WearableTrait`, `UniqueItemAddon`, `UniqueItem` imports and the matching fields; delete `openWeaponView`,
     `openAmmunitionView`, `createAmmunitionItem`, `findWeapon`, `getCompatibleAmmunition`, `openWearableView` and
     `findFuelUniqueItem` (all move verbatim to the module in T15). The class keeps only `plugin`, the
     `SignContributions contributions` field flip 2 added, and `openGenericItemView`. `execute` becomes
     ```java
     String itemName = sign.getContent();
     if (contributions.openView(player, itemName)) {
     	return AspectResult.success("Opened view: " + itemName);
     }
     openGenericItemView(player, itemName);
     return AspectResult.success("Opened item view: " + itemName);
     ```
     and `canExecute` becomes `return !sign.getContent().isEmpty();` — **provably identical** to today's version,
     whose weapon and ammunition branches only ever short-circuit to the same `!itemName.isEmpty()` result.
  2. `sign/type/ViewSign.java`: delete the `WeaponService`, `AmmunitionManager`, `WearableService` and
     `UniqueItemAddon` fields/imports (`UniqueItemAddon` is now unused — it was only forwarded to the aspect); drop
     the two arguments from `new ViewSignValidator(...)`; keep passing `contributions` to `ViewInventoryAspect`.
  3. `sign/validation/ViewSignValidator.java`: delete the `Weapon`, `WeaponService`, `AmmunitionManager` imports,
     both fields, both constructor parameters and the two dead `if (isWeapon)` / `if (isAmmo)` blocks (`:31-47`).
     `isValidContent` becomes `return !content.isEmpty();` — **behaviour-identical**; update the comment.
  4. `sign/SignManager.java` — update the `new ViewSign(...)` call (`:145-146`) for the shorter constructor.
- **Why:** rules 2 and 3 — the view sign is a core catalogue entry with per-domain content.
- **Done when:** `grep -rn -i "weapon\|ammunition\|wearable" gangland-impl/src/main/java/org/luckyraven/gangland/sign/aspect/ViewInventoryAspect.java
  gangland-impl/src/main/java/org/luckyraven/gangland/sign/type/ViewSign.java
  gangland-impl/src/main/java/org/luckyraven/gangland/sign/validation/ViewSignValidator.java` is empty.
- **Watch out:** flip 2's `ViewInventoryAspect` edit kept the wearable branch and the `uniqueItemAddon` field; this
  task removes the **last** domain branch, so the aspect ends up with two fields. Say so in §7 — it is a larger
  simplification than flip 2 anticipated. The per-domain success strings ("Opened weapon view: x") collapse to
  "Opened view: x" — flip 2's `SignViewProvider` contract returns `boolean`; accepted (§6 C-3).

> **⛔ COMPILE GATE D** — the only remaining `gangland-impl` errors should be the pure MOVE files.

---

### Group E — the moves

### T12 — Move the commands  (group E, 12 files)

- **Do:**
  1. `git mv` the eight files from `gangland-impl/.../command/sub/weapon/` to
     `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/command/`
     (`AmmunitionCommand`, `AmmunitionGiveCommand`, `AmmunitionInfoCommand`, `AmmunitionListCommand`,
     `WeaponCommand`, `WeaponGiveCommand`, `WeaponInfoCommand`, `WeaponListCommand`). Package →
     `org.luckyraven.gangland.weapon.command`; add explicit imports for `org.luckyraven.gangland.weapon.*` types and
     change `…file.configuration.weapon.WeaponLoader` → `org.luckyraven.gangland.weapon.file.WeaponLoader`.
  2. `git mv` the four files from `gangland-impl/.../command/sub/item/wearable/` to
     `…/weapon/command/wearable/` (`ItemWearableCommand`, `ItemWearableGiveCommand`, `ItemWearableInfoCommand`,
     `ItemWearableListCommand`). Package → `org.luckyraven.gangland.weapon.command.wearable`.
  3. Create `…/weapon/command/ItemWearableContribution.java implements CommandContribution` with
     `parent() == "item"` and `create(tree, parent)` returning `List.of(new ItemWearableCommand(gangland, tree,
     parent, userManager, wearableAddon))` — the exact construction deleted from `ItemCommand` in T7.
  4. Delete the now-empty `command/sub/weapon/` and `command/sub/item/wearable/` directories.
- **Why:** M7.
- **Done when:** the twelve files compile inside `gangland-weapon`; both core directories are gone.
- **Watch out:** `WeaponCommand` filters `getCommands()` with `startsWith("weapon")` (`:41`), `AmmunitionCommand` with
  `startsWith("ammunition")` (`:33`) and `ItemWearableCommand` inherits `ItemCommand`'s `item` help page — all resolve
  through the merged `InformationManager` index only **after** T22 fills the module's `commands.json`. Expect empty
  help pages until then. Note also that `ItemWearable*Command` are `SubArgument`s constructed with
  `(gangland, tree, parent, …)` — the `CommandContribution` signature supplies `tree` and `parent`.

### T13 — Move persistence, loaders and the manager  (group E, 5 files)

- **Do:**
  1. `git mv gangland-impl/.../database/repositories/weapon/WeaponRepository.java
     gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/database/WeaponRepository.java`
     — package `org.luckyraven.gangland.weapon.database`; import `…weapon.Weapon` and
     `…weapon.configuration.WeaponAddon`.
  2. Same for `database/tables/weapon/WeaponTable.java` → `weapon/database/WeaponTable.java`. Delete both now-empty
     `weapon/` directories under `database/repositories` and `database/tables`.
  3. `git mv .../file/configuration/weapon/WeaponLoader.java → …/weapon/file/WeaponLoader.java` (package
     `org.luckyraven.gangland.weapon.file`).
  4. `git mv .../file/configuration/weapon/GanglandBlockRegenerationSettings.java →
     …/weapon/file/WeaponBlockRegenerationSettings.java`, renaming the class; it keeps importing core `Settings`.
     Delete the now-empty `file/configuration/weapon/` directory.
  5. `git mv gangland-impl/src/main/java/org/luckyraven/gangland/weapon/WeaponManager.java
     gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/WeaponManager.java` — **the
     package line is unchanged**; only the `WeaponRepository` import moves to
     `org.luckyraven.gangland.weapon.database.WeaponRepository`. Delete the now-empty
     `gangland-impl/src/main/java/org/luckyraven/gangland/weapon/` directory.
- **Why:** M5 + M4 + M3, and the split-package hazard (coordinator point 5).
- **Done when:** `find gangland-impl/src/main -path "*weapon*"` returns nothing.
- **Watch out:**
  - `WeaponManager.initialize()`'s `repository.setDataSupplier(...)` (`:35`) must survive the move verbatim — without
    it autosave throws `No data supplier set for repository: WeaponRepository` (memory rule "repository data
    supplier").
  - `WeaponTable`'s table name stays `"weapon"` (`WeaponTable.java:14`) — no schema migration.
  - `WeaponManager` now imports core `GanglandDatabase`; that is the legal module → core direction.

### T14 — Move the item classes  (group E, 8 files)

- **Do:**
  1. `git mv` into `gangland-features/gangland-weapon/src/main/java/org/luckyraven/gangland/weapon/item/`
     (package `org.luckyraven.gangland.weapon.item`): `item/converter/WeaponConverter.java`,
     `item/converter/AmmunitionConverter.java`, `item/converter/WearableConverter.java`,
     `item/refresher/WeaponRefresher.java`, `item/refresher/AmmunitionItemRefresher.java`,
     `item/refresher/WearableRefresher.java`, `item/serializer/WeaponItemSerializer.java`,
     `item/serializer/AmmunitionItemSerializer.java`, `item/serializer/WearableItemSerializer.java`.
     Add imports for the core bases (`org.luckyraven.gangland.item.{ItemAttributes,ItemRefresher,ItemSerializer,ItemKind}`)
     and fix the refreshers' javadoc links to the converters' new package.
  2. Create `…/weapon/item/WeaponItemPredicates.java` with the two constants deleted from `ItemPredicates` in T20:
     ```java
     public final class WeaponItemPredicates {
     	public static final Predicate<ItemStack> WEAPON     =
     			stack -> hasTag(stack, Weapon.getTagProperName(WeaponTag.WEAPON));
     	public static final Predicate<ItemStack> AMMUNITION = stack -> hasTag(stack, Ammunition.NBT_KEY);
     	private WeaponItemPredicates() { }
     	private static boolean hasTag(ItemStack stack, String tag) { … }   // copy of ItemPredicates.hasTag
     }
     ```
     **`ItemPredicates.WEARABLE` stays in core** — it tests `org.luckyraven.gangland.item.wearable.Wearable.NBT_KEY`,
     a gangland-item type — and the module's serializer registration imports it from core.
- **Why:** M4/M9 plus coordinator point 1 (wearables are weapon's).
- **Done when:** `ls gangland-impl/src/main/java/org/luckyraven/gangland/item/{converter,refresher,serializer}/`
  lists no `Weapon*`, `Ammunition*` or `Wearable*` file.
- **Watch out:** `ItemKind.WEAPON`/`AMMUNITION`/`WEARABLE` are label-only enum constants in `gangland-item` — they
  **stay in core** and the moved serializers keep returning them.

### T15 — Move the signs and create the module-side seam beans  (group E, 16 files)

- **Do:**
  1. `git mv` into `…/weapon/sign/` (package `org.luckyraven.gangland.weapon.sign`):
     `sign/type/trade/weapon/Weapon{Buy,Sell}Sign.java`, `sign/type/trade/ammo/Ammo{Buy,Sell}Sign.java`,
     `sign/type/trade/wearable/Wearable{Buy,Sell}Sign.java`,
     `sign/validation/trade/weapon/WeaponSignValidator.java`,
     `sign/validation/trade/ammo/AmmoSignValidator.java`,
     `sign/validation/trade/wearable/WearableSignValidator.java`.
     Delete the six now-empty core directories.
  2. Create `…/weapon/sign/AbstractWeaponTradeSign.java extends BaseTradeSign` holding the `weaponService` +
     `ammunitionManager` fields and the four methods deleted from `BaseTradeSign` in T9 (`getWeaponItem`,
     `getAmmoItem`, `weaponSimilarityChecker`, `ammoSimilarityChecker`), copied **verbatim**. Re-point
     `Weapon{Buy,Sell}Sign` and `Ammo{Buy,Sell}Sign` at it. `Wearable{Buy,Sell}Sign` extend plain `BaseTradeSign`
     (they never used the two params — verified).
  3. Create `…/weapon/sign/WeaponSignContribution.java implements SignTypeContribution` whose
     `signs(String signPrefix)` reproduces the six blocks deleted from `SignManager` in T10, with the **same keys and
     generated labels**: `weapon-buy`/`"WEAPON-BUY"`, `weapon-sell`/`"WEAPON-SELL"`, `ammo-buy`/`"AMMO-BUY"`,
     `ammo-sell`/`"AMMO-SELL"`, `wearable-buy`/`"WEARABLE-BUY"`, `wearable-sell`/`"WEARABLE-SELL"`.
  4. Create `…/weapon/sign/view/WeaponSignViewProvider.java implements SignViewProvider` holding the seven methods
     deleted from `ViewInventoryAspect` in T11 (`findWeapon`, `getCompatibleAmmunition`, `openWeaponView`,
     `openAmmunitionView`, `createAmmunitionItem`, `openWearableView`, `findFuelUniqueItem`). `open(player, content)`
     tries weapon → ammunition → wearable in that order and returns `true` on the first hit, `false` otherwise —
     preserving today's chain position (module providers run before the core's generic fallback).
  5. Create `…/weapon/shop/WeaponShopDisplayNameProvider.java implements ShopDisplayNameProvider` — the weapon branch
     removed from `GanglandShopDisplayResolver` in T5, returning `null` when the NBT tag is absent.
  6. Create `…/weapon/data/WeaponDataCleanupTask.java implements DataCleanupTask` — `name()` returns `"weapons"`;
     `cleanup()` is today's `PluginDataCleanupService.resetWeapons()` body, **including** the
     `if (weaponRepository instanceof WeaponRepository repo) repo.deleteAll();` guard.
  7. Create `…/weapon/metrics/WeaponMetricsContributor.java implements MetricsContributor` returning
     `Map.of("number_of_weapons", weaponAddon::size)`.
  8. Create `…/weapon/death/WeaponDeathMessageContributor.java implements DeathMessageContributor` (body specified in
     T17).
- **Why:** M6/M9 — every seam gets its module implementation.
- **Done when:** all thirteen files exist; `mvn -q -pl gangland-features/gangland-weapon -am install -DskipTests`
  gets past them (it may still fail on the module configs, written in T18/T19).
- **Watch out:** `WeaponSignViewProvider` uses `org.luckyraven.gangland.inventory.*` (inventory-api),
  core `Settings` and core `UniqueItemAddon` — all provided deps added in T1. Keep `Fill` /
  `InventoryUtil.fillInventory` usage exactly as-is (memory rule "inventory util fillers"). `weapon.death` is
  deliberately **not** under `weapon.listener` — it is a bean, not a `Listener`.

### T16 — *(reserved)* — no task

The wearable/car relocation this slot originally held is now folded into T12/T14/T15, because flip 2 settled the
wearable ownership question (coordinator point 1). Mark `n/a` in §7.

> **⛔ COMPILE GATE E**

---

### Group F — listeners and module wiring

### T17 — Split the three core listeners  (group F, 6 files)

- **Do:**
  1. `listener/gang/GangMembersDamageListener.java` — delete the `WeaponRaytraceImpactEvent` import (`:15`) and the
     whole `onGangMemberWeaponImpact` handler (`:52-70`); keep `onGangMemberHitMembers` untouched. Create
     `…/weapon/listener/gang/GangAllyWeaponImpactListener.java` with `@ListenerHandler(condition = "isGangEnabled")`,
     the same constructor (`@Qualifier("online") UserManager<Player>`, `GangManager`) and the handler copied verbatim
     including `@EventHandler(priority = EventPriority.LOWEST)`.
  2. Create `gangland-impl/src/main/java/org/luckyraven/gangland/listener/death/DeathMessageContributor.java` as in
     §1.6(g).
  3. `listener/player/PlayerDeathListener.java` — delete the `Weapon`, `WeaponManager`, `ThrowableAction` imports
     (`:30-32`), the field (`:43`) and constructor parameter (`:50`); add
     `Supplier<List<DeathMessageContributor>> deathContributors`. `buildDeathMessage` walks the contributors for the
     first non-null `Resolved`; uses `resolved.template()` when non-null, else a random entry from
     `Messages.DEAD_USING_WEAPON.toStringList()`; substitutes `%killer%`, `%victim%` and `%item%` with
     `resolved.itemName()` (empty string when nothing claimed the kill). Behaviour must match `:200-237` exactly.
  4. Create `…/weapon/death/WeaponDeathMessageContributor.java` (skeleton created in T15) containing today's
     `ThrowableAction.pendingKillerWeapon.remove(...)` / `weaponManager.getWeaponTemplate` /
     `validateAndGetWeapon` logic, returning `new Resolved(weapon.pickDeathMessage().orElse(null),
     weapon.getDisplayName())`, or `new Resolved(null, throwableName)` when only the throwable name is known, or
     `null` when neither applies.
  5. `listener/player/RemoveAccountListener.java` — delete the `Weapon`/`WeaponManager` imports (`:22-23`), the field
     (`:32`), the constructor parameter (`:38`) and the trailing weapon block (`:112-118`). Create
     `…/weapon/listener/player/WeaponQuitCleanupListener.java` with
     `@ListenerHandler(priority = ListenerPriority.LOW)` and an `@EventHandler(priority = EventPriority.HIGHEST)`
     `onPlayerQuit(PlayerQuitEvent)` containing exactly that block (`validateAndGetWeapon`, `stopReloading`,
     `unScope(player, true)`), constructor `(WeaponManager weaponManager)`.
- **Why:** M6; rule 2.
- **Done when:** `grep -rn -i weapon gangland-impl/src/main/java/org/luckyraven/gangland/listener/` is empty, and the
  two new listeners sit under `org.luckyraven.gangland.weapon.listener.**` (the scan recurses into subpackages —
  `ReflectionUtil.scanJar` matches on `name.startsWith(path)`).
- **Watch out:** the extracted quit block runs at the same `EventPriority.HIGHEST` as
  `RemoveAccountListener.onPlayerLeave` but does not depend on its work (it only inspects the held ItemStack), so
  intra-priority ordering is irrelevant. Note that in §7.

### T18 — `WeaponFileConfig` (FILE phase)  (group F, 1 file)

- **Do:** fill `…/weapon/WeaponFileConfig.java`:
  ```java
  @Configuration(phase = Phase.FILE)
  public class WeaponFileConfig {

  	private final Gangland     gangland;
  	private final ModuleLoader moduleLoader;

  	public WeaponFileConfig(Gangland gangland, ModuleLoader moduleLoader) {
  		this.gangland     = gangland;
  		this.moduleLoader = moduleLoader;
  	}

  	@Bean
  	public AmmunitionManager ammunitionManager(Settings settings) { … }                    // FileConfig:167-170

  	@Bean
  	public AmmunitionAddon ammunitionAddon(FileManager fileManager, AmmunitionManager ammunitionManager,
  	                                       PlaceholderService placeholderService) {
  		// AmmunitionAddon resolves fileManager.getFile("ammunition") in its CONSTRUCTOR, so the handler must be
  		// registered first. KernelConfig used to do this at :173; the default now ships in this module's jar.
  		fileManager.addFile(new FileHandler(gangland, "ammunition", "items", ".yml", moduleLoader.classLoader()),
  		                    true);
  		AmmunitionAddon addon = new AmmunitionAddon(fileManager, ammunitionManager, placeholderService);
  		fileManager.registerInitializer(addon);
  		return addon;
  	}

  	@Bean
  	public WearableAddon wearableAddon(PermissionManager permissionManager, FileManager fileManager,
  	                                   PlaceholderService placeholderService) {
  		fileManager.addFile(new FileHandler(gangland, "wearables", "items", ".yml", moduleLoader.classLoader()),
  		                    true);                                                          // was KernelConfig:175
  		WearableAddon addon = new WearableAddon(permissionManager::addPermission, fileManager, placeholderService);
  		fileManager.registerInitializer(addon);
  		return addon;
  	}

  	@Bean
  	public WeaponBlockRegenerationSettings blockRegenerationSettings() { … }                // FileConfig:136-139

  	@Bean
  	public WeaponAddon weaponAddon(AmmunitionAddon ammunitionAddon, PlaceholderService placeholderService) { … }

  	@Bean
  	public WeaponLoader weaponLoader(FileManager fileManager, AmmunitionManager ammunitionManager,
  	                                 WeaponAddon weaponAddon) {
  		WeaponLoader loader   = new WeaponLoader(gangland, fileManager, weaponAddon, ammunitionManager);
  		ClassLoader  loaderCl = moduleLoader.classLoader();
  		for (String name : List.of("rifle", "grenade", "knife", "flamethrower", "syringe_gun")) {
  			loader.addExpectedFile(new FileHandler(gangland, name, "weapon", ".yml", loaderCl));
  		}
  		return loader;
  	}
  }
  ```
- **Why:** M3 + M8.
- **Done when:** the file compiles; `grep -c "moduleLoader.classLoader()" …/WeaponFileConfig.java` is `3`.
- **Watch out:**
  - **Keep the ordering-only parameters:** `AmmunitionAddon ammunitionAddon` on `weaponAddon(...)` and
    `Settings settings` on `ammunitionManager(...)` are the bean-graph edges that force ammunition to load before
    weapons (memory rule "bean ordering via params").
  - Both addons call `fileManager.getFile(name)` **in their constructor**
    (`AmmunitionAddon.java:47`, `WearableAddon.java:47`), which is why `addFile` must precede `new …Addon(...)` inside
    the same `@Bean` body.
  - `ModuleLoader` is registered in the container by `GanglandContext`'s constructor, so plain constructor injection
    works. The FILE-phase hook (`GanglandContext.bootstrap`) calls `FileManager.initializeAll()` after **every** FILE
    bean and `BeanGraph` sorts core + module FILE beans together, so `WeaponLoader` still runs after
    `AmmunitionAddon` populated `AmmunitionManager`.

### T19 — `WeaponModuleConfig` (CONFIG phase)  (group F, 1 file)

- **Do:** fill `…/weapon/WeaponModuleConfig.java` (`@Configuration`, constructor
  `(Gangland gangland, DependencyContainer container)`):

  | `@Bean` | body | source |
  |---|---|---|
  | `weaponManager(WeaponAddon, GanglandDatabase)` | `new WeaponManager(...)` | `GameplayConfig:181-184` |
  | `weaponService(WeaponManager)` | `return weaponManager;` | `GameplayConfig:186-189` |
  | `wearableService(WearableAddon)` | `return wearableAddon;` | `GameplayConfig:212-215` |
  | `wearableEquipService(WearableAddon)` | `return wearableAddon;` | `GameplayConfig:307-310` |
  | `blockDamageManager(WeaponBlockRegenerationSettings)` | `new BlockDamageManager(gangland, settings)` | `GameplayConfig:191-194` |
  | `weaponVisualSpawner()` | `new WeaponVisualSpawner()` | `GameplayConfig:196-199` |
  | `weaponRaytracer(WeaponManager, WearableAddon, BlockDamageManager, WeaponVisualSpawner)` | body incl. `Bukkit.getServicesManager().register(...)` | `GameplayConfig:201-210` |
  | `recoilCompatibility(CompatibilityWorker)` | `compatibilityWorker.getRecoilCompatibility()` | `GameplayConfig:218-221` |
  | `pluginFireRegistry()` | `new PluginFireRegistry()` | `GameplayConfig:223-226` |
  | `weaponConverter(WeaponService)` · `ammunitionConverter(AmmunitionManager)` · `wearableConverter(WearableService)` | as-is | `ItemConfig:37-50` |
  | `weaponItemSerializer()` · `ammunitionItemSerializer()` · `wearableItemSerializer()` | as-is | `ItemConfig:108-121` |
  | `weaponRefresher(WeaponService)` · `wearableRefresher(WearableService)` · `ammunitionItemRefresher(AmmunitionManager)` | as-is | `ItemConfig:157-175` |
  | `weaponItemRegistrations(ItemConverterRegistry, ItemSerializerRegistry, ItemRefresherRegistry, + the nine beans above)` | converters: `register(ItemKind.WEAPON, …)`, `register(ItemKind.AMMUNITION, …)`, `register("ammo", …)`, `register(ItemKind.WEARABLE, …)`; serializers: plain 2-arg `register(WeaponItemPredicates.WEAPON/AMMUNITION, …)` and `register(ItemPredicates.WEARABLE, …)` (§1.6(a)); refreshers: `register(weaponRefresher, 10)`, `register(wearableRefresher, 10)` and **`register(ammunitionItemRefresher)`** (default priority `0` — ammunition sits *behind* `uniqueItemRefresher` today and must stay there; see §1.6(a) and §6 C-1). Returns a small marker record. | §1.6(a) |
  | `weaponNbtTags(NbtTagCatalog)` | registers every `WeaponTag` name; returns a marker | §1.6(e) |
  | `weaponSignContribution(WeaponService, AmmunitionManager, WearableService, @Qualifier("online") UserManager<Player>)` | `new WeaponSignContribution(...)` | §1.6(b) |
  | `weaponSignViewProvider(Gangland, WeaponService, AmmunitionManager, WearableService, UniqueItemAddon)` | `new WeaponSignViewProvider(...)` | §1.6(b) |
  | `weaponShopDisplayNameProvider(WeaponService)` | §1.6(f) | |
  | `weaponDataCleanupTask(WeaponManager, GanglandDatabase)` | resolves `IRepository<Weapon>` off the registry | §1.6(d) |
  | `weaponMetricsContributor(WeaponAddon)` | §1.6(c) | |
  | `weaponDeathMessageContributor(WeaponManager)` | §1.6(g) | |
  | `debugWeaponContribution(WeaponManager)` | `CommandContribution` with `parent() == "debug"` rebuilding `/glw debug weapon` | T7 |
  | `itemWearableContribution(Gangland, @Qualifier("online") UserManager<Player>, WearableAddon)` | `CommandContribution` with `parent() == "item"` | T7/T12 |
- **Why:** M3/M9 — one place declaring everything weapon contributes.
- **Done when:** `mvn -q -pl gangland-features/gangland-weapon -am install -DskipTests` passes.
- **Watch out:** register **one bean per distinct concrete type** — `container.getAllInstances` de-duplicates by type
  (`MailModuleConfig` follows the same rule). **Do not register all three refreshers at priority 10** — ammunition
  must keep the default `0` or a unique ammunition stack is rebuilt as plain ammunition (§6 C-1). The three registry
  parameters on `weaponItemRegistrations` are the
  ordering edges that guarantee the core registry beans exist first (§1.6(a)).

> **⛔ COMPILE GATE F** — `mvn clean install -DskipTests` green for the **whole reactor**.

---

### Group G — core clean-out, YAML, commands.json

### T20 — Delete the weapon/wearable `@Bean`s from the core configs  (group G, 6 files) — **p0-wave-3 overlap**

- **Do:**
  1. `config/FileConfig.java` — delete `ammunitionManager` (`:167-170`), `ammunitionAddon` (`:172-178`),
     `blockRegenerationSettings` (`:136-139`), `wearableAddon` (`:196-202`), `weaponAddon` (`:214-217`),
     `weaponLoader` (`:219-234`) and the `WeaponLoader` (`:17`), `WearableAddon` (`:21`), `AmmunitionManager` (`:34`),
     `AmmunitionAddon` (`:35`), `WeaponAddon` (`:36`), `GanglandBlockRegenerationSettings` (`:16`) imports.
  2. `config/GameplayConfig.java` — delete `weaponManager`, `weaponService`, `blockDamageManager`,
     `weaponVisualSpawner`, `weaponRaytracer`, `wearableService`, `recoilCompatibility`, `pluginFireRegistry` and the
     `// Weapon system` banner (`:176-226`), plus `wearableEquipService` (`:307-310`), every
     `org.luckyraven.gangland.weapon.*` import (`:61-69`), the `WearableAddon` (`:28`), `WearableEquipService`
     (`:43`) and `GanglandBlockRegenerationSettings` (`:26`) imports.
  3. `config/ItemConfig.java` — delete `weaponConverter`, `ammunitionConverter`, `wearableConverter`,
     `weaponItemSerializer`, `ammunitionItemSerializer`, `wearableItemSerializer`, `weaponRefresher`,
     `wearableRefresher`, `ammunitionItemRefresher`; remove those parameters and their `registry.register(...)` lines
     from `itemConverterRegistry` (`:66-87`), `itemSerializerRegistry` (`:133-153`) and `itemRefresherRegistry`
     (`:182-191`); delete the `WeaponService` (`:14`), `AmmunitionManager` (`:15`) and `WearableService` (`:16`)
     imports. Keep `ItemPredicates.WEARABLE` — the module imports it.
  4. `config/KernelConfig.java` — delete the `fm.addFile(... "ammunition", "items" ...)` line (`:173`) and the
     `fm.addFile(... "wearables", "items" ...)` line (`:175`).
  5. `item/ItemPredicates.java` — delete the `Weapon`, `WeaponTag`, `Ammunition` imports (`:10-12`) and the `WEAPON`
     and `AMMUNITION` constants (`:29-30`); update the class javadoc to point at
     `org.luckyraven.gangland.weapon.item.WeaponItemPredicates`.
  6. `config/SchedulingConfig.java`, `config/ShopConfig.java` — already handled in T4/T5; re-verify.
- **Why:** M3.
- **Done when:** `grep -rn "org\.luckyraven\.gangland\.weapon\." gangland-impl/src/main` **returns nothing** (gate G1).
- **Watch out:** `GameplayConfig.java` is on the **p0-wave-3** list. Deleting `recoilCompatibility` removes the only
  core consumer of `CompatibilityWorker.getRecoilCompatibility()` — leave `CompatibilityWorker` itself alone (it is a
  `KernelConfig` bean serving the whole compatibility layer).

### T21 — Move the 24 YAML defaults  (group G, 24 files)

- **Do:**
  1. `git mv gangland-impl/src/main/resources/weapon gangland-features/gangland-weapon/src/main/resources/weapon`
     (22 files, path unchanged relative to the resources root).
  2. `git mv gangland-impl/src/main/resources/items/ammunition.yml
     gangland-features/gangland-weapon/src/main/resources/items/ammunition.yml` and the same for `wearables.yml`
     (creating `…/resources/items/` in the module).
- **Why:** M8; the module classloader is parent-first, so these paths must not exist in the core jar.
- **Done when:** `ls gangland-impl/src/main/resources/weapon` fails;
  `ls gangland-impl/src/main/resources/items/` lists only `unique_items.yml` and `money.yml` (plus `cars.yml` if flip
  2 left it — it should not have);
  `ls gangland-features/gangland-weapon/src/main/resources/weapon | wc -l` prints `22`.
- **Watch out:** do not edit the YAML contents. Record in §7 that only 5 of the 22 weapon files are in
  `addExpectedFile` — see §6 B-1.

### T22 — `commands.json` split  (group G, 2 files)

- **Do:**
  1. Cut the twelve objects `weapon_help`, `weapon_list`, `weapon_give`, `weapon_info`, `ammunition_help`,
     `ammunition_list`, `ammunition_give`, `ammunition_info`, `item_wearable`, `item_wearable_give`,
     `item_wearable_info`, `item_wearable_list` from `gangland-impl/src/main/resources/commands.json` into
     `gangland-features/gangland-weapon/src/main/resources/commands.json`, preserving `usage` and `description`
     verbatim, tab-indented in gangland-mail's style.
  2. Record the new core key count:
     `python -c "import json,io;print(len(json.load(io.open('gangland-impl/src/main/resources/commands.json',encoding='utf-8'))))"`.
- **Why:** M7 — the `commands.json` entry lives in the jar that owns the command.
- **Done when:** the two counts sum to the pre-flip total and the module file parses as JSON.
- **Watch out:** feed the recorded count into T25.

> **⛔ COMPILE GATE G** — `mvn clean install -DskipTests` green; `grep -rn "org\.luckyraven\.gangland\.weapon\."
> gangland-impl/src` empty except for the two test files T23 fixes.

---

### Group H — tests

### T23 — Fix the impl tests  (group H, 3 files)

- **Do:**
  1. `bootstrap/PeriodicalUpdatesTest.java` — delete the `WeaponManager` import (`:14`), the field (`:47`) and the
     `mock(WeaponManager.class)` line (`:58`); in all three constructor calls (`:71`, `:86`, `:101`) pass a
     `mock(DependencyContainer.class)` stubbed so `getAllInstances(DataCleanupTask.class)` returns `List.of()`.
  2. `data/plugin/PluginDataCleanupServiceTest.java` — rewrite against `DataCleanupTask`: keep the `setUp` fixture
     (`SettingsFixture`, `Messages.init`, `TimeMessages`) and the class javadoc paragraph explaining why; replace
     `weaponRepository`/`weaponManager` with a `mock(DataCleanupTask.class)`; keep the four scenarios (`noRows`,
     `notDue`, `due`, `forceCleanup`) asserting `verify(task, never()).cleanup()` / `verify(task).cleanup()` and the
     `nextPlannedDate` rescheduling. **Delete** the fifth test
     (`resetWeapons_nonWeaponRepositoryImplementation_skipsDeleteAll`) — the `instanceof WeaponRepository` guard it
     pins now lives in the module and is re-pinned by T24.
  3. `sign/SignManagerContributionTest.java` (created by flip 2) — already re-baselined in **T10 step 3**; re-run it
     here and confirm it is still green after the sign moves in T15. If T10 left it asserting a total, that total is
     **5**.
- **Why:** M10.
- **Done when:** `mvn -q -pl gangland-impl -am test` green, including `SignManagerContributionTest`.

### T24 — New module tests  (group H, 3 files)

- **Do:** in `gangland-features/gangland-weapon/src/test/java/org/luckyraven/gangland/weapon/`:
  1. `WeaponModuleTest.java`, modelled on `gangland-mail`'s `MailModuleTest`: `configure(registrations)` registers
     exactly `[WeaponFileConfig, WeaponModuleConfig]` plus the three packages; a second test asserts
     `WeaponCommand.class.getPackageName()` equals `COMMAND_PACKAGE`, `WeaponRepository.class.getPackageName()`
     equals `REPOSITORY_PACKAGE`, and `WeaponQuitCleanupListener.class.getPackageName().startsWith(LISTENER_PACKAGE)`.
  2. `data/WeaponDataCleanupTaskTest.java` — mocks `WeaponManager` + `WeaponRepository`; asserts `cleanup()` returns
     the pre-clear weapon count, calls `deleteAll()` then `clear()`, and that a plain `mock(IRepository.class)` does
     not throw (the `instanceof` guard, migrated from the deleted impl test).
  3. `item/WeaponItemPredicatesTest.java` — asserts `WEAPON` and `AMMUNITION` return `false` for `null` and for a
     tagless stack. If `ItemBuilder` needs a live server, drop this test and say so in §7.
- **Why:** M10.
- **Done when:** `mvn -q -pl gangland-features/gangland-weapon -am test` green.
- **Watch out:** **add no test dependencies** to the module pom (root pom `pom.xml:548-558` supplies them). Show each
  test red first: write it before the class under test exists (compilation failure = the recorded red state), or stub
  the method to return `0`/`false` and capture the assertion failure, then fill the body.

### T25 — `InformationManagerTest` count  (group H, 1 file)

- **Do:** set the literal at `command/data/InformationManagerTest.java:41` to the number T22 recorded. **Expect
  149** — the running total is 225 → 182 (cops, 43 keys) → 177 (gadget, 5) → 161 (turf, 16) → **149** (weapon, 12).
  Do not assume it: recompute with T22's one-liner
  `python -c "import json,io;print(len(json.load(io.open('gangland-impl/src/main/resources/commands.json',encoding='utf-8'))))"`
  and use that number. Update the assertion message to name the flip.
- **Done when:** `mvn -q -pl gangland-impl -am test -Dtest=InformationManagerTest` green.
- **Watch out:** the number must equal the core `commands.json` key count — re-run the `python -c` count, do not guess.

> **⛔ COMPILE GATE H** — `mvn test` green across the whole reactor.

---

### Group I — other modules, docs, gates

### T26 — `Depends: weapon` on cops and gadget  (group I, 2 files)

- **Do:**
  1. `gangland-features/cops-n-crooks/src/main/resources/module.yml` — extend the block-style list (flip 3 added
     `- turf`):
     ```yaml
     Depends:
       - turf
       - weapon
     ```
  2. `gangland-features/gangland-gadget/src/main/resources/module.yml` —
     ```yaml
     Depends:
       - weapon
     ```
- **Why:** rule 9. Cops imports `org.luckyraven.gangland.weapon.*` from `npc/AbstractNpc`, `npc/NpcCombatDelegate`,
  `npc/police/spawn/CopSpawnManager`, `npc/police/npc/CopNpcFactory`, `npc/civilian/npc/CivilianNpcFactory`,
  `listener/NpcDamageUnprotectListener`, `listener/detainment/CopListener`, `listener/police/DetainmentListener`,
  `listener/turf/TurfFriendlyFireListener`; gadget from `jetpack/JetpackService`, `jetpack/JetpackTask`,
  `listener/car/CarDamageListener` (after flip 2's T2 moved `WearableAddon` out, `wearable/WearableAddon` is no
  longer a gadget file, but the three remaining imports stand).
- **Done when:** `mvn clean package -DskipTests` is green and both descriptors show the block list:
  `unzip -p target/modules/cops-n-crooks-0.8.4.jar module.yml` prints `Depends:` followed by `  - turf` and
  `  - weapon` on their own lines, and `unzip -p target/modules/gangland-gadget-0.8.4.jar module.yml` prints
  `Depends:` followed by `  - weapon`. (Load-order and `module.dependency.missing` behaviour are verified on a server
  under §5 "Manual boot checks" / gate G6, not here.)
- **Watch out:** **one item per line** — never `Depends: [turf, weapon]` (memory rule "yaml inline braces").

### T27 — Docs  (group I, 6 files)

- **Do:**
  1. `CLAUDE.md` — module table: `gangland-features/gangland-weapon` becomes
     **"Runtime module (`modules/gangland-weapon-<rev>.jar`, never in the core jar): weapon, ammunition, wearable and
     projectile system"**. In "Two tiers", state that **all four** features are now runtime modules and the core's
     feature closure is empty. List the new seams beside `CommandContribution`.
  2. `documentation/module-loader.md` — one row per module in "What is a module today", remove the
     "still compile-time dependencies" row and the "Order for the remaining flips" paragraph; in "Attaching
     sub-arguments" add `debug` and `item` to the queried paths; **append rows to the existing `## Core seams`
     section** (cops T19 creates it; gadget T15 already appended to it — do not add a second heading) documenting
     `SignTypeContribution`/`SignViewProvider`/`SignContributions` (flip 2), the item-registry priority contract, and
     `MetricsContributor`, `DataCleanupTask`, `NbtTagCatalog`, `ShopDisplayNameProvider`, `DeathMessageContributor` —
     each with its package, consumer and the moment the consumer reads it.
  3. `README.md:156` — mark `gangland-weapon` as a runtime module.
  4. `documentation/developer/README.md:63`, `documentation/developer/architecture.md:299`,
     `documentation/developer/modules.md:778`, `documentation/developer/weapons.md:3,34` — replace the
     "compile-time dependency of gangland-impl" phrasing. Do not rewrite the weapon-mechanics documentation itself.
  5. `documentation/developer/gadgets.md:22,918` — the `gadget → weapon` edge is now a **runtime module dependency**
     (`Depends: weapon`), and wearables moved to the weapon module; update both mentions.
  6. Memory `project_module_loader_plan.md` — flip 4 landed; the seams; cops + gadget carry `Depends: weapon`;
     wearables live in the weapon module.
- **Done when:** `grep -rn "still compile-time dependencies" documentation/ CLAUDE.md README.md` is empty.

### T28 — Gates and graph refresh  (group I, 0 files)

- **Do:** run §5 in order, then `graphify update . --force`.
- **Done when:** every command in §5 produces its stated output.

---

## 3. Tests

**Tests to move:** none (see §1.5 for why).

**Tests to change:**

| Test | Change |
|---|---|
| `gangland-impl/src/test/.../bootstrap/PeriodicalUpdatesTest.java` | drop the `WeaponManager` mock + ctor arg; add a `DependencyContainer` mock returning `List.of()` (T23) |
| `gangland-impl/src/test/.../data/plugin/PluginDataCleanupServiceTest.java` | rewrite against `DataCleanupTask`; delete the `instanceof WeaponRepository` test (T23) |
| `gangland-impl/src/test/.../command/data/InformationManagerTest.java:41` | → T22's recomputed count; **expect 149** (225 → 182 → 177 → 161 → 149) (T25) |

**New tests:**

| Test | Asserts | Shown red how |
|---|---|---|
| `gangland-infra/gangland-item/src/test/.../ItemRefresherRegistryPriorityTest.java` (T8) | a priority-10 refresher registered *after* a default one wins; two default-priority refreshers keep insertion order (this is what keeps `ammunitionItemRefresher` behind `uniqueItemRefresher`, §6 C-1) | Written against the not-yet-existing 2-arg `register(refresher, priority)` → compilation failure; capture that output as the red state, then add the overload. |
| `gangland-weapon/src/test/.../WeaponModuleTest.java` | `configure()` registers exactly the two configurations and three packages; constants match real package names | Write it before `WeaponModule` has its `commandPackage(...)` call → the `commandPackages()` assertion fails; then add the call. |
| `gangland-weapon/src/test/.../data/WeaponDataCleanupTaskTest.java` | returns the pre-clear count; `deleteAll()` then `clear()`; survives a non-`WeaponRepository` `IRepository` | Create the class with `cleanup()` returning `0` and an empty body → the count and `verify` assertions fail; then fill it. |
| `gangland-weapon/src/test/.../item/WeaponItemPredicatesTest.java` | `WEAPON`/`AMMUNITION` are `false` for `null` and for a tagless stack | Same pattern; drop and record in §7 if `ItemBuilder` needs a live server. |

---

## 4. Docs and config

| File | Edit |
|---|---|
| `gangland-impl/pom.xml` | remove the `gangland-weapon` dependency |
| `gangland-features/gangland-weapon/pom.xml` | + `keystone-command`, `keystone-module`; + `gangland-impl`, `gangland-domain`, `sign-api`, `inventory-api` at `provided`; − `anvilgui`, `viaversion-api` |
| `gangland-features/cops-n-crooks/pom.xml` | `gangland-weapon` → `provided` + comment |
| `gangland-features/gangland-gadget/pom.xml` | comment on the existing `provided` `gangland-weapon` |
| `gangland-features/gangland-gadget/.../gadget/sign/CarSignContribution.java` | drop the `weaponService`/`ammunitionManager` fields, ctor params and the two arguments in `new CarBuySign(...)` / `new CarSellSign(...)` (T9 step 3, review finding 5) |
| `gangland-features/gangland-gadget/.../gadget/GadgetModuleConfig.java` | drop the `WeaponService`/`AmmunitionManager` params, args and imports from `@Bean carSignContribution(...)` (T9 step 3, review finding 5) |
| `gangland-build/pom.xml` | shade `<exclude>org.luckyraven:gangland-weapon</exclude>`; `maven-dependency-plugin` `<artifactItem>`; `provided` dependency |
| `gangland-features/gangland-weapon/src/main/resources/module.yml` | new (Id `weapon`, Main `…weapon.WeaponModule`, `Host_Api: 0.8`, Artifact `org.luckyraven:gangland-weapon`, no `Depends`) |
| `…/resources/org/luckyraven/gangland/weapon/module.properties` | new: `module.name=${project.name}` |
| `…/resources/commands.json` | new: the 12 weapon/ammo/wearable keys |
| `gangland-impl/src/main/resources/commands.json` | − the same 12 keys |
| `gangland-impl/src/main/resources/weapon/**` (22) + `items/{ammunition,wearables}.yml` (2) | → the module at the same relative paths |
| `gangland-features/cops-n-crooks/src/main/resources/module.yml` | `Depends:` block list gains `- weapon` |
| `gangland-features/gangland-gadget/src/main/resources/module.yml` | `Depends:` block list `- weapon` |
| `CLAUDE.md` | module table row + two-tier section + seams |
| `documentation/module-loader.md` | module table, drop the flip-order paragraph, **append rows to the existing `## Core seams` section** (created by cops T19), `debug`/`item` contribution paths |
| `README.md:156`, `documentation/developer/{README,architecture,modules,weapons,gadgets}.md` | runtime-module wording; wearables now weapon's |
| memory `project_module_loader_plan.md` | flip 4 landed; seams; `Depends: weapon`; wearables in weapon |

---

## 5. Verification (gates G1–G4 instantiated)

```bash
# G1 compile — reactor green, zero weapon imports left in impl
mvn clean install -DskipTests -q
grep -rn "org\.luckyraven\.gangland\.weapon\." gangland-impl/src                  # expect: no output
grep -rn -i "weaponmanager\|weaponaddon\|ammunitionmanager\|wearableaddon" gangland-impl/src   # expect: no output

# G2 tests
mvn test
#   InformationManagerTest -> T22's recomputed count (expect 149: 225 -> 182 -> 177 -> 161 -> 149)
#   ItemRefresherRegistryPriorityTest, WeaponModuleTest, WeaponDataCleanupTaskTest -> green
#   PeriodicalUpdatesTest, PluginDataCleanupServiceTest -> green and weapon-free

# G3 jars
mvn clean package -DskipTests
unzip -l target/gangland_warfare-0.8.4.jar | grep "gangland/weapon/"              # expect: no output (split-package check)
unzip -l target/gangland_warfare-0.8.4.jar | grep -E "^.*(weapon/[a-z_]+\.yml|items/(ammunition|wearables)\.yml)"  # no output
unzip -l target/modules/gangland-weapon-0.8.4.jar | grep -E "module.yml|commands.json|weapon/rifle.yml|items/wearables.yml"
unzip -p target/modules/gangland-weapon-0.8.4.jar module.yml                      # Id: weapon, Host_Api: 0.8, no Depends
unzip -l target/modules/gangland-weapon-0.8.4.jar | grep -c "weapon/.*\.yml"      # expect: 22
unzip -l target/modules/gangland-weapon-0.8.4.jar | grep -E "net/wesjd|com/viaversion"   # expect: no output
unzip -p target/modules/cops-n-crooks-0.8.4.jar   module.yml | grep -A3 Depends   # - turf / - weapon
unzip -p target/modules/gangland-gadget-0.8.4.jar module.yml | grep -A2 Depends   # - weapon

# G4 docs
grep -rn "still compile-time dependencies" documentation/ CLAUDE.md README.md     # expect: no output
grep -n "gangland-weapon" documentation/module-loader.md                          # runtime-module row

# graph
graphify update . --force
```

**Manual boot checks (feed into G6):**

1. `modules/` empty → the server boots; `/glw weapon`, `/glw ammo` and `/glw item wearable` are absent;
   `/glw item` still offers `unique` and `money`; `/glw debug nbt brief` still lists loot-chest tags; `[glw-buy]`,
   `[glw-sell]`, `[glw-view]`, `[glw-wanted]`, `[glw-bounty]` still work; the `number_of_weapons` bStats chart is
   simply absent; a shop entry's display name falls back to the item meta.
2. All four module jars present → one `Loaded module weapon` line; `/glw weapon list` prints the configured weapons;
   a fresh data folder gets `weapon/rifle.yml`, `items/ammunition.yml` and `items/wearables.yml`;
   `[glw-weapon-buy]`, `[glw-ammo-buy]`, `[glw-wearable-buy]` validate; `/glw debug weapon` lists live UUIDs; a
   **unique weapon** delivered from a shop keeps its weapon state (the refresher-priority check).
3. Remove only `gangland-weapon-0.8.4.jar` → cops and gadget are skipped with `module.dependency.missing`; the server
   still boots.

---

## 6. Risks and open questions for the scrum master

### Contradictions with `gadget.md` — recommended resolutions

**C-1 (blocking, must be decided).** `gadget.md` §1.6(b) adds the priority overload **only** to
`ItemSerializerRegistry`. `ItemRefresherRegistry` has the same defect and it is *live*, not latent: today
`ItemConfig.java:182-191` registers `weaponRefresher, wearableRefresher, uniqueItemRefresher, ammunitionItemRefresher,
carItemRefresher` in that order (first match wins), and `UniqueItemRefresher.canRefresh` =
`UniqueItemUtil.isUniqueItem(source)` matches a **unique weapon**, which `ItemConfig.java:143-145` documents as
carrying both the weapon and the uniqueItem tag. After flip 4 the core registers `uniqueItemRefresher` first and the
module appends, so a unique weapon would be rebuilt as a plain unique item and lose its weapon state — silently.
**Recommendation:** T8 mirrors flip 2's exact API onto `ItemRefresherRegistry` (`CATCH_ALL_PRIORITY`, a
`register(refresher, priority)` overload, stable `List.sort`), and T19 registers **at two priorities**, not one:
`weaponRefresher` and `wearableRefresher` at **`10`** (today they sit ahead of `uniqueItemRefresher`) and
`ammunitionItemRefresher` at the **default `0`** (today it sits *behind* `uniqueItemRefresher`, so a unique
*ammunition* stack must still be rebuilt as a unique item — registering it at 10 would mirror the very regression this
entry exists to prevent). Post-flip order `weapon, wearable, unique, ammunition, car` = today's order exactly. This is
an *extension* of gadget's design, not a competing one. If the scrum master prefers, the same effect can be had by
moving `uniqueItemRefresher`'s registration into a `@PostConstruct`, but that is strictly worse.

**C-2 (accepted, no action).** `WearableItemSerializer` imports only `org.luckyraven.gangland.item.wearable.Wearable`
(a gangland-item type) and is not compile-forced to move. I move it anyway, per the coordinator's point 1, so the
whole wearable stack lives in one module. Its predicate, `ItemPredicates.WEARABLE`, **stays in core** (same reason)
and the module imports it. Flag only if a reviewer expects the predicate to move too.

**C-3 (accepted, minor behaviour delta).** `gadget.md`'s `SignViewProvider.open` returns `boolean`, so
`ViewInventoryAspect` reports a single `"Opened view: <name>"` instead of today's per-domain
`"Opened weapon view: …"` / `"Opened ammunition view: …"` / `"Opened wearable view: …"`. These strings only reach
`AspectResult.success(...)`; they are not player-facing messages. Accepted; recorded here rather than diverging.

**C-4 (informational).** My earlier draft proposed an `ItemRegistrationOrder` constants class and an `ItemViewProvider`
interface. **Both are deleted** in favour of gadget's `CATCH_ALL_PRIORITY` and `SignViewProvider`. No second mechanism
exists in this plan.

### Other risks

**R-1.** T0's Gates W / S / I are blocking. If flip 2's `WearableAddon` relocation, sign seam, or serializer priority
did not land, flip 4 must stop rather than re-implement them.

**R-2.** `ViewInventoryAspect` ends this flip with only `plugin` and `contributions` — a larger simplification than
flip 2 anticipated, because weapon+ammo+wearable were the last domain branches. `canExecute` reduces to
`!content.isEmpty()`, which is provably identical to today's implementation. If a reviewer disagrees, the fallback is
to keep `canExecute` delegating to the providers first; behaviour is the same either way.

**R-3.** `ViewSignValidator.isValidContent` reduces to `return !content.isEmpty();`. Provably behaviour-identical
today. If the *intent* was for view signs to reject unknown item names, that is a separate bug, not this flip's.

### Bugs found on the way — add to `brainstorming/bug-docket-2026-09-06/triage/`, then rebuild with `build_docket.py`

* **B-1 `triage/weapon-loader-expected-files-incomplete.txt`** — `FileConfig.java:229-233` registers only 5 of the 22
  shipped weapon YAMLs via `addExpectedFile`. `FolderLoader.loadData` only creates files from `expectedFolderFiles`
  when the data-folder directory is missing or empty, so on a **fresh install** 17 weapons (`awp`, `crowbar`,
  `flashbang`, `golden_ak47`, `machete`, `minigun`, `molotov`, `mp5`, `pistol`, `ray_gun`, `revolver`,
  `rocket_launcher`, `sawn_off`, `shotgun`, `smoke_grenade`, `steyr_aug`, `tomahawk`) are never written out and never
  load. Pre-existing; **not fixed here** (T18 copies the list verbatim so the flip stays behaviour-preserving).
* **B-2 `triage/signmanager-setup-runs-twice.txt`** — `setupSigns()` executed twice per boot (explicit
  `GameplayConfig.java:266` + Keystone's convention `initialize()` pass). Harmless (map-backed registries) but it
  builds every `SignTypeDefinition`, `SignHandler` and `SignAspect` twice. **Fixed by flip 2**; log it so the docket
  carries the reference.
* **B-3 `triage/viewsignvalidator-dead-weapon-checks.txt`** — `ViewSignValidator.isValidContent`
  (`sign/validation/ViewSignValidator.java:24-51`) computes a weapon lookup and an ammunition lookup whose only
  outcomes are `return true`, followed by an unconditional `return true`. Dead code on every view-sign edit. Removed
  in T11.
* **B-4 `triage/weapon-module-unused-deps.txt`** — `gangland-features/gangland-weapon/pom.xml` declares
  `net.wesjd:anvilgui` and `com.viaversion:viaversion-api`, neither imported anywhere in the module. AnvilGUI is
  additionally relocated by the shade plugin, so the declaration was a latent runtime hazard for exactly this flip.
  Removed in T1.
* **B-5 `triage/item-refresher-registry-order-unguarded.txt`** — `ItemRefresherRegistry` has no way for a late
  registrant to outrank an earlier one, while `refresh()`/`decorate()` are first-match-wins. Latent today (all
  registrations happen in one core `@Bean`), live the moment any feature becomes a module. Fixed in T8; see C-1.

---

## 7. Status table (executors fill this)

| Task | Status | Executor | Notes (what changed, what was skipped, failures verbatim) |
|---|---|---|---|
| T0 reconcile | done | sonnet groupA | Gate W: **pass** — `weapon/wearable/WearableAddon.java` exists in `gangland-weapon`; `grep -rn "gadget\.wearable"` across impl/features/ui/infra = empty. Gate S: **pass** — `sign/extension/` has `SignContributions.java`, `SignTypeContribution.java`, `SignViewProvider.java`; `manager.initialize()` grep only hits a comment (`GameplayConfig.java:265`, explaining it's deliberately not called), no live call. Gate I: **pass** — `ItemSerializerRegistry.CATCH_ALL_PRIORITY` at `gangland-infra/gangland-item/.../ItemSerializerRegistry.java:32`; `ItemConfig.java:138` uses it for `materialItemSerializer`. `CopsAndGadgetsConfig.java` check: **gone** (find → empty), consistent with flips 1-2. `module.yml Depends`: cops = `[turf]` (no weapon yet, added by T26); gadget = none currently (no `Depends:` key at all). Gate C (car-sign call sites): `gadget/sign/CarSignContribution.java` present as named; **drift** — `GadgetModuleConfig.java` lives at `gadget/config/GadgetModuleConfig.java`, not `gadget/GadgetModuleConfig.java` as the checklist path says. Its `carSignContribution` bean (line ~120) takes `(UserManager, CarAddon, WeaponService, AmmunitionManager)` and constructs `new CarSignContribution(userManager, carAddon, weaponService, ammunitionManager)`; `CarSignContribution`'s ctor 2nd param is typed `CarManager` (not `CarAddon` — `CarAddon` apparently satisfies that type). **Extra finding beyond §C**: `GadgetModuleConfig.jetpackService(...)` (line ~91-96) also takes a `WeaponService weaponService` parameter — a second gadget→weapon bean edge not mentioned in §1.1 §C; T9 executors should check it too. Car signs (`CarBuySign`/`CarSellSign`) live in `gangland-features/gangland-gadget/.../gadget/sign/` (moved by flip 2; no longer in `gangland-impl`). Base-list diff vs §1.1 §A (`grep -rl "org\.luckyraven\.gangland\.weapon\."` → 57 files, was 55): missing `config/CopsAndGadgetsConfig.java` (deleted, expected) and `sign/type/trade/car/{CarBuySign,CarSellSign}.java` (moved to gadget, expected); +5 extra now matching the pattern because `WearableAddon` resolves under `org.luckyraven.gangland.weapon.wearable`: `command/sub/item/ItemCommand.java` + the 4 `ItemWearable*` commands (§1.1 §B rows 56-60) — net 55-2+... net = 57, consistent with plan's own convergence note. Wearable-domain grep (`weapon\.wearable\|gadget\.wearable`) → 17 hits exactly as the plan predicted (16 main + 1 test `SignManagerContributionTest.java`), matches §1.1 §B. |
| T1 poms | done | sonnet groupA | `gangland-impl/pom.xml`: removed `gangland-weapon` dependency block. `gangland-features/gangland-weapon/pom.xml`: added `keystone-command`, `keystone-module` (default scope); added `gangland-impl` (provided, with the mail-style explanatory comment), `gangland-domain` (provided), `sign-api` (provided), `inventory-api` (provided); removed `net.wesjd:anvilgui` and `com.viaversion:viaversion-api` blocks; left `keystone-persistence`/`keystone-common`/`version-impl`/`gangland-core`/`gangland-item`/XSeries/test-jar untouched — no `keystone-permission` added (not yet demanded; weapon module itself is never reached by the build because impl fails first, so its own compile is deferred). `gangland-features/cops-n-crooks/pom.xml`: `gangland-weapon` dep was **already** `<scope>provided</scope>` (drift from checklist's "currently default/compile" claim) — added the explanatory comment only, no scope change. `gangland-features/gangland-gadget/pom.xml`: `gangland-weapon` already `provided` as the checklist expected — added the same comment. `gangland-build/pom.xml`: added `<exclude>org.luckyraven:gangland-weapon</exclude>` to shade excludes, an `<artifactItem>` to `copy-runtime-modules`, and a `provided`-scope `<dependency>` (`${project.parent.version}`) beside turf's. Done-when: `mvn clean install -DskipTests -q` fails; all 200 `[ERROR] ...\.java:[...]` records / 23 distinct files are under `gangland-impl` only (verified `-rf :gangland-impl` resume hint and per-file path scan). Saved to scratchpad `weapon_worklist.txt`/`weapon_worklist_full.txt` (not `/tmp`, Windows sandbox). All 23 files are non-KEEP rows in §1.1 (§A rows 1,2,3,4,5-12,14,15,19,23,24,55; §B rows 56-60). |
| T2 module skeleton | done | sonnet groupA | Created `WeaponModule.java` (modelled on `MailModule`/`TurfModule`: `LISTENER_PACKAGE`/`COMMAND_PACKAGE`/`REPOSITORY_PACKAGE` = `org.luckyraven.gangland.weapon.{listener,command,database}`), empty-bodied `WeaponFileConfig` (`@Configuration(phase = Phase.FILE)`, ctor `(Gangland, ModuleLoader)`, per the checklist text — note turf's own `TurfModuleFileConfig` actually uses `Phase.KERNEL` and a 1-arg ctor, but the weapon checklist explicitly specifies FILE + 2-arg ctor, followed literally per rule 2) and empty-bodied `WeaponModuleConfig` (`@Configuration`, ctor `(Gangland, DependencyContainer)`, import `org.luckyraven.keystone.bean.autowire.DependencyContainer` matching house usage). `module.yml` (Id `weapon`, no `Depends:`), `commands.json` (`{}`), `module.properties` (`module.name=${project.name}` — this file **already existed** tracked in git with identical content; edit was a no-op, confirmed via `git diff` showing no content changes, only a line-ending notice). All 6 paths verified present via `test -f`. Compilation deferred to gate A/G1 per the task's own note. |
| — gate A | done | sonnet groupA | `mvn clean install -DskipTests` (full, unpiped) → `BUILD FAILURE` at `[15/42] Gangland` (i.e. `gangland-impl`) only; no module after it in the reactor order was attempted (`[16/42]` never printed). Error list saved verbatim per the exact instructed command to `brainstorming/module-split-2026-09-07/weapon-errors-after-T1.txt` (100 lines after `sort -u` of `^[ERROR].*\.java` records — some lines are duplicate `.java:[line,col]` entries that differ only by column, `sort -u` keeps them distinct since the full line differs). Full untruncated log grepped for any `.java` error path outside `gangland-impl`: **none found** — all 23 distinct files match the T1 list exactly (identical set; T2 touched only the weapon module, which the build never reached). No file outside the checklist's §1.1 inventory appeared. On the javac 100-error cap: **not observed as a hard cutoff this run** — the log contains 200 distinct `file:[line,col]` error records (342 total `[ERROR]` lines counting `symbol`/`location` continuations) across 23 files, i.e. more than 100 without any "too many errors" truncation message, so whatever cap exists did not visibly truncate this build's output; noted for the next executor group in case a later, larger error set does hit it. |
| T3 MetricsContributor | done | sonnet groupB/C | Created `metrics/MetricsContributor.java` (`Map<String, IntSupplier> singleLineCharts()`, verbatim §1.6(c)). `Gangland.java`: removed the `WeaponAddon` import and its `number_of_weapons` chart line; added `MetricsContributor` import + `java.util.List`/`java.util.function.IntSupplier`; appended the null-guarded `getAllInstances(MetricsContributor.class)` loop at the end of `bStats()` (after the scoreboard-driver chart), matching the checklist code verbatim. Done-when: `grep -n "WeaponAddon" Gangland.java` empty — confirmed. |
| T4 DataCleanupTask | done | sonnet groupB/C | Created `data/plugin/DataCleanupTask.java` (`name()`, `int cleanup()`, verbatim §1.6(d)). `PluginDataCleanupService.java` rewritten: ctor now `(PluginManager, Supplier<List<DataCleanupTask>>)`; deleted `WeaponRepository`/`Weapon`/`WeaponManager` imports+fields and `resetWeapons()`; `performCleanup` now loops `tasks.get()` calling `task.cleanup()`/`task.name()`. `PeriodicalUpdates.java`: deleted `Weapon`/`WeaponManager` imports and the `weaponManager` field, added `DependencyContainer container` field/param to both constructors in the same position, `initializeCleanupService()` now builds the service with `() -> container.getAllInstances(DataCleanupTask.class)`. `SchedulingConfig.java`: `periodicalUpdates(...)` lost the `WeaponManager` param, gained `DependencyContainer container` as the last param, passes it to both `PeriodicalUpdates` constructor branches. `PluginDataCleanupServiceTest.java` (T23's file) now breaks compile as expected — not touched, that test class still constructs the old 3-arg ctor; left for T23 per rule 2 (do not touch files owned by later tasks). Done-when grep (`-i weapon` over the three files) empty — confirmed. |
| T5 ShopDisplayNameProvider | done | sonnet groupB/C | Created `file/configuration/shop/ShopDisplayNameProvider.java` (`@Nullable String cleanDisplayName(ItemStack)`, verbatim §1.6(f)). `GanglandShopDisplayResolver.java` rewritten: dropped `@RequiredArgsConstructor`/`Weapon`/`WeaponService`/`WeaponTag` imports and the `weaponService` field; new ctor `(Supplier<List<ShopDisplayNameProvider>> providers)`; weapon block replaced with the provider loop from the checklist, `ItemMeta`/material fallbacks kept verbatim; class javadoc rewritten to stop naming any weapon type (had to drop the literal word "weapon" from the javadoc too — the done-when grep is case-insensitive and matched my own comment on the first pass, fixed). `ShopConfig.java`: dropped the `WeaponService` import, `shopDisplayResolver` now takes `DependencyContainer container` and builds the resolver with `() -> container.getAllInstances(ShopDisplayNameProvider.class)`. Done-when grep empty — confirmed (after the javadoc fix). |
| T6 NbtTagCatalog | done | sonnet groupB/C | Created `item/NbtTagCatalog.java` (verbatim §1.6(e): `register(String...)` ignoring null/blank/duplicate, `tags()` returns `List.copyOf`). `ItemConfig.java`: added `LootChestWandTag` import and the `nbtTagCatalog()` `@Bean` at the end of the class exactly as specified (did not touch the pre-existing weapon/wearable/ammunition beans above it — those are T20's). `ReadNBTCommand.java`: dropped `WeaponTag`/`LootChestWandTag` imports, added `NbtTagCatalog nbtTagCatalog` ctor param+field, collapsed both `values()` loops into the single `for (String tagName : nbtTagCatalog.tags())` loop from the checklist. Done-when (`grep WeaponTag`) empty — confirmed. Cosmetic note as flagged by the checklist: weapon tags used to print before loot-chest tags, now they print after (loot-chest tags are the only ones registered into the core catalogue; order is cosmetic only, no functional impact). Added `HolderSeamBeanTypeTest.nbtTagCatalogBeanIsDeclaredAsTheHolderClass()` per the assignment's extra instruction (pins `ItemConfig.nbtTagCatalog` declares the concrete `NbtTagCatalog` return type) — T3-T5 needed no pin since none of them register a core bean of the seam's interface type (pure contribution pattern, consumed via `getAllInstances`/`Supplier`, no core default implementation bean exists to mis-type). |
| — gate B | n/a (no green gate for groups B/C per the assignment) | sonnet groupB/C | Reactor stays red by design; see gate-after-T8 check below. |
| T7 debug + item contributions | done | sonnet groupB/C | **p0-wave-3 overlap confirmed pre-existing** — `DebugCommand.java` had no conflicting edits from that session in the working tree at execution time (git status clean going in); edited directly. `DebugCommand.java`: deleted `Weapon`/`WeaponManager` imports, the `weaponManager` field, the ctor param+assignment, and the whole `getGiveGun()` method + its two call sites (`Argument giveGun = getGiveGun();` and `arguments.add(giveGun);`); added `CommandContributions`/`DependencyContainer` imports, a `contributions` field, `this.contributions = CommandContributions.from(container);` in the ctor, the `for (Argument contributed : contributions.createFor("debug", ...))` loop at the end of `initializeArguments()`, and the javadoc line "Accepts `CommandContribution`s at path `debug`." `ItemCommand.java`: deleted `WearableAddon` import/field/param/assignment, the `ItemWearableCommand` import and its construction + `arguments.add(wearable)`; added the same `CommandContributions`/`DependencyContainer` wiring and the path-`item` contribution loop after `getArgument().addAllSubArguments(arguments)`; javadoc line added; the `startsWith("item")` help filter left untouched as instructed. Done-when grep (`-i "weapon\|wearable"` over both files) empty — confirmed. Note for T12: `ItemWearableCommand`'s import/construction was deleted here exactly as T7 specifies; T12 step 3 is responsible for re-adding it via `ItemWearableContribution`. |
| T8 ItemRefresherRegistry priority | done | sonnet groupB/C | Wrote `gangland-infra/gangland-item/src/test/.../ItemRefresherRegistryPriorityTest.java` **first** (3 cases: higher-priority-registered-later wins; two default-priority refreshers keep insertion order; a default-priority refresher registered after another default-priority one stays behind it — the ammunition/unique pin). Ran it red first: `mvn -q -pl gangland-infra/gangland-item -am test -Dtest=ItemRefresherRegistryPriorityTest -Dsurefire.failIfNoSpecifiedTests=false` → compile failure verbatim: `method register in class org.luckyraven.gangland.item.ItemRefresherRegistry cannot be applied to given types; required: ItemRefresher[]; found: ItemRefresher,int; reason: varargs mismatch`. Then rewrote `ItemRefresherRegistry.java` mirroring `ItemSerializerRegistry` exactly: added `CATCH_ALL_PRIORITY = Integer.MIN_VALUE`, `private record Entry(ItemRefresher refresher, int priority)`, `register(refresher)` (→ priority 0), `register(ItemRefresher...)` (→ priority 0 each), new `register(refresher, priority)` with `entries.sort(Comparator.comparingInt(Entry::priority).reversed())` (stable `List.sort`); `refresh`/`decorate` now iterate `entries` and call `entry.refresher()`, preserving the null/AIR short-circuit behaviour verbatim (re-verified against the existing `ItemRefresherRegistryTest`, which still passes unmodified). Updated the class javadoc per the checklist. `ItemConfig.java` step 3: left the four core registrations (`weaponRefresher`, `wearableRefresher`, `uniqueItemRefresher`, `ammunitionItemRefresher`) on the plain overload as instructed, and added the specified explanatory comment above `itemRefresherRegistry(...)` describing the T20/T19 priority split (weapon+wearable at 10, ammunition at default) for the future executor. Done-when: `mvn -q -pl gangland-infra/gangland-item -am test` → **green**, exit 0 (confirmed via surefire report: `ItemRefresherRegistryPriorityTest` 3/3 passed, and the pre-existing `ItemRefresherRegistryTest`/`ItemConverterRegistryTest`/`ItemSerializerRegistryTest`/`ItemParserTest` suites in the same module all still ran without failure in the same `mvn test` invocation). |
| — gate C | done (item-module gate; core reactor stays red by design) | sonnet groupB/C | Gate-after-T8 command run verbatim: `mvn clean install -DskipTests 2>&1 \| grep -E "^[ERROR].*\.java" \| sort -u > brainstorming/module-split-2026-09-07/weapon-errors-after-T8.txt` → 100 lines (was 100 lines / 23 distinct files after T1; now 100 lines / **18 distinct files** after T3-T8). Full untruncated log saved to scratchpad (`weapon_full_build_after_T8.log`) and grepped: **the 100-line cap is javac's default `-Xmaxerrs` (100), confirmed exactly 100 `.java:[line,col]` records in the single `default-compile @ gangland-impl` invocation** (unlike T1's note, which reported 200 records without an observed cutoff — this run's post-fix file set apparently reorders which files javac reaches before the cap, since removing several always-erroring files from the front of the list lets javac get further into the tree before truncating; the "second" identical error block later in the log at ~line 1048 is Maven's end-of-build `Failed to execute goal` summary re-echoing the same 100 records, not a second compile pass — verified via `grep -n "compiler:3.13.0:compile"`, one `default-compile @ gangland-impl` invocation only). Distinct files in the truncated list, all T9+-scoped: `ItemWearable{Command,GiveCommand,InfoCommand,ListCommand}.java` (T12), `Ammunition{Command,GiveCommand,InfoCommand,ListCommand}.java` + `Weapon{Command,GiveCommand,InfoCommand,ListCommand}.java` (T12), `FileConfig.java` (T20), `GameplayConfig.java` (T10/T11/T20, p0-wave-3 file), `GanglandBlockRegenerationSettings.java` + `WeaponLoader.java` (T13), `SignManager.java` (T9/T10), `weapon/WeaponManager.java` (T13) — **18 files, all mapped to a task ≥ T9; zero unexpected files**. Explicitly re-verified none of the 8 files touched in T3-T8 (`Gangland.java`, `PeriodicalUpdates.java`, `PluginDataCleanupService.java`, `SchedulingConfig.java`, `ShopConfig.java`, `GanglandShopDisplayResolver.java`, `ItemConfig.java`, `DebugCommand.java`, `ItemCommand.java`, `ReadNBTCommand.java`) appear anywhere in the compile-error range. **Caveat for the next executor**: because of the 100-error cap, this list is a lower bound, not the complete impl error set — files beyond the cap (e.g. `ItemConfig.java` itself, which still imports unresolved `WeaponService`/`AmmunitionManager`/`WearableService` pending T20) are known-broken but did not surface in this run's javac output. Do not conclude a file compiles clean solely from its absence here. |
| T9 strip BaseTradeSign | done | sonnet groupD | `BaseTradeSign.java` rewritten to keep only `getUniqueOrMaterialItem` — dropped `@Getter`/`@RequiredArgsConstructor`, `weaponService`/`ammunitionManager` fields, `getWeaponItem`/`getAmmoItem`/`weaponSimilarityChecker`/`ammoSimilarityChecker`, and the now-unused imports (`Getter`, `RequiredArgsConstructor`, `ItemBuilder`, `ItemSimilarityChecker`, `Weapon`, `WeaponService`, `Ammunition`, `AmmunitionManager`, `WeaponType`, `StandardCharsets`, `UUID`). `BuySign.java`/`SellSign.java`: dropped `WeaponService weaponService, AmmunitionManager ammunitionManager` ctor params + `super(weaponService, ammunitionManager)` call + the two weapon imports. Car signs confirmed at `gangland-features/gangland-gadget/.../gadget/sign/{CarBuySign,CarSellSign}.java` (per T0's Gate C note) — same ctor-param strip applied. Step 3 (gadget-module call sites, edited by the weapon flip): `gadget/sign/CarSignContribution.java` — dropped `weaponService`/`ammunitionManager` fields, ctor params, both `new Car{Buy,Sell}Sign(...)` arguments, and the two weapon imports. `gadget/config/GadgetModuleConfig.java` (drift confirmed: lives at `gadget/config/`, not `gadget/GadgetModuleConfig.java` as the checklist path says) — `carSignContribution(...)` bean lost its `WeaponService weaponService, AmmunitionManager ammunitionManager` params/args; removed the now-unused `AmmunitionManager` import. **Left untouched, out of this task's scope**: `jetpackService(FuelService, GadgetPhysicsConfig, WearableAddon, WeaponService)` still takes `WeaponService` — this is T0's "extra finding beyond §C" (a second, pre-existing gadget→weapon bean edge unrelated to `BaseTradeSign`); the assignment text scoped step 3 to `carSignContribution(...)` only, so the `WeaponService` import stays in `GadgetModuleConfig.java` for that bean. Step 4: `SignManager.java` — updated `new BuySign(...)`/`new SellSign(...)` call sites (dropped the two weapon args each). Done-when greps empty for `BaseTradeSign.java` and (modulo the retained `jetpackService` `WeaponService` param, out of scope) the two gadget files. |
| T10 weapon+wearable signs leave SignManager | done | sonnet groupD | `SignManager.java`: deleted the `WeaponBuySign`/`WeaponSellSign`/`AmmoBuySign`/`AmmoSellSign`/`WearableBuySign`/`WearableSellSign`/`WeaponService`/`AmmunitionManager`/`WearableService` imports; deleted the `weaponService`/`ammunitionManager`/`wearableService` fields and ctor params (ctor now `(Gangland, String, SignTypeRegistry, SignInteraction, UniqueItemAddon, UserManager<Player>, UserManager<OfflinePlayer>, DependencyContainer)`); deleted the six weapon-buy/weapon-sell/ammo-buy/ammo-sell/wearable-buy/wearable-sell blocks. No second `SignContributions` loop added — the existing one at the end of `setupSigns()` (from flip 2) is untouched. `config/GameplayConfig.java` `signManager(...)`: dropped `WeaponManager weaponManager, AmmunitionManager ammunitionManager, WearableAddon wearableAddon` params and the matching ctor args; removed the now-unused `AmmunitionManager` import (`WeaponManager`/`WeaponService`/`WearableAddon` imports stay — still used by other, later-task beans in the same file). p0-wave-3 overlap: no live conflicting edit found in the working tree at execution time. **`SignManagerContributionTest` re-baselined to:** kept as containment-only (already written that way by flip 2/gadget — no total-count assertion existed, so nothing to flip to 5); only its `SignManager` construction call was updated to the new 8-arg ctor (dropped the `mock(WeaponService.class)`, `mock(AmmunitionManager.class)`, `mock(WearableService.class)` args and the matching imports). Done-when: `grep -c "SignType(" SignManager.java` → **5** (confirmed: buy, sell, view, wanted, bounty). Could not run `mvn -pl gangland-impl -am test -Dtest=SignManagerContributionTest` green — `gangland-impl` does not compile yet (T12+ files still reference moved/stripped weapon types; reactor red by design until T20) — see gate D note below for why this is expected. |
| T11 ViewSign / ViewInventoryAspect / ViewSignValidator | done | sonnet groupD | `sign/aspect/ViewInventoryAspect.java` rewritten: kept only `plugin`/`contributions` fields; deleted `openWeaponView`, `openAmmunitionView`, `createAmmunitionItem`, `findWeapon`, `getCompatibleAmmunition`, `openWearableView`, `findFuelUniqueItem` and all weapon/ammo/wearable/unique imports (also dropped the now-dead `ItemStack` import — nothing left in the file uses it after the removal); kept `openGenericItemView`. `execute` reduced to the checklist's two-branch form (`contributions.openView` then `openGenericItemView`); `canExecute` reduced to `return !sign.getContent().isEmpty();`. `sign/type/ViewSign.java` rewritten: ctor now `(Gangland gangland, SignContributions contributions, SignType signType)`; dropped `WeaponService`/`AmmunitionManager`/`WearableService`/`UniqueItemAddon` fields+imports; `new ViewSignValidator(signType)` (2 args dropped); `new ViewInventoryAspect(gangland, contributions)`. `sign/validation/ViewSignValidator.java` rewritten: dropped `Weapon`/`WeaponService`/`AmmunitionManager` imports+fields+ctor params; `isValidContent` → `return !content.isEmpty();`; comment updated. `SignManager.java` step 4: `new ViewSign(gangland, contributions, viewType)` (was 6 args). Confirms R-2/R-3 from §6: `ViewInventoryAspect` now carries only `plugin`+`contributions` (the larger-than-anticipated simplification flip 2 foresaw), and `ViewSignValidator.isValidContent` is the provably-identical 1-liner. Per-domain success strings ("Opened weapon view: …") collapsed to "Opened view: …"/"Opened item view: …" per accepted C-3. Done-when greps empty across all three files. |
| — gate D | done (with documented caveat) | sonnet groupD | `mvn clean install -DskipTests` → full log saved to scratchpad `weapon-T11-full.log`; `grep -E "^\[ERROR\].*\.java" \| sort -u` saved to `weapon-errors-after-T11.txt` (100 lines, javac's default `-Xmaxerrs=100` cap — confirmed by re-running `mvn -pl gangland-impl -am compile -Dmaven.compiler.maxerrs=100000`, which produced an **identical** 100-record/21-file set, i.e. the plugin's `maxerrs` parameter has no CLI user-property alias in this compiler-plugin version and could not be raised from the command line). Distinct erroring files (21, all T12+-scoped): the 4 `ItemWearable*Command.java` + 8 `Ammunition*`/`Weapon*Command.java` (T12), `FileConfig.java`/`GameplayConfig.java`/`ItemConfig.java` (T20, `GameplayConfig.java` still on p0-wave-3), `GanglandBlockRegenerationSettings.java`/`WeaponLoader.java`/`weapon/WeaponManager.java` (T13), `Ammunition/Weapon/WearableConverter.java` (T14). **Zero** of `sign/SignManager.java`, `sign/type/ViewSign.java`, `sign/type/trade/{BaseTradeSign,BuySign,SellSign}.java`, `sign/aspect/ViewInventoryAspect.java`, `sign/validation/ViewSignValidator.java`, or the two gadget files appear anywhere in the log. **Caveat, checked and explained rather than hidden:** the reactor never reaches `sign/type/trade/{ammo,weapon,wearable}/{Ammo,Weapon,Wearable}{Buy,Sell}Sign.java` in this build (javac processes files in roughly alphabetical/discovery order and `command/`→`config/`→`file/`→`item/` already exhausts the 100-record cap before reaching `sign/`) — by **direct source inspection** those 6 files (T15's MOVE list, §A rows 43-44/47-50) still call `super(weaponService, ammunitionManager)` against `BaseTradeSign`'s now-zero-arg constructor and still import `WeaponService`/`AmmunitionManager`, so they are known-broken and **will** error once javac reaches them or once T15 moves them — this is exactly the checklist's carved-out exception ("except the weapon/ammo/wearable sign classes T15 moves"), not a gap in this task's work. All T9/T10/T11 done-when greps (run directly, not inferred from the capped compiler log) are independently empty/correct — see each task's row. Gadget-module-alone build: `mvn -q -pl gangland-features/gangland-gadget install -DskipTests` (no `-am`) → **fails**: `constructor BaseTradeSign ... cannot be applied ... required: WeaponService,AmmunitionManager found: no arguments` on `CarBuySign.java:36` and `CarSellSign.java:36`. This is the documented caveat firing exactly as expected: the gadget module resolves the **last INSTALLED** `gangland-impl` jar from `~/.m2`, and `gangland-impl` has never successfully installed since T1 removed its weapon dependency (the reactor has been red since T1, before this executor started) — so the installed jar still ships the pre-T9 `BaseTradeSign(WeaponService, AmmunitionManager)` signature. The gadget module's own source (`CarBuySign.java`/`CarSellSign.java`/`CarSignContribution.java`/`GadgetModuleConfig.java`) is correct against the *current* `BaseTradeSign`; this failure is solely the not-yet-installed-core-change case the task asked to flag, not a defect in this group's edits. |
| T12 move commands (8 + 4 wearable) | done | sonnet groupE | `git mv` the 8 weapon/ammo commands (`Ammunition{Command,GiveCommand,InfoCommand,ListCommand}`, `Weapon{Command,GiveCommand,InfoCommand,ListCommand}`) from `command/sub/weapon/` to `weapon/command/`, package `org.luckyraven.gangland.weapon.command`; the 4 wearable commands (`ItemWearable{Command,GiveCommand,InfoCommand,ListCommand}`) from `command/sub/item/wearable/` to `weapon/command/wearable/`, package `org.luckyraven.gangland.weapon.command.wearable`. Fixed `WeaponLoader` import in `WeaponCommand.java`/`WeaponGiveCommand.java` from `…file.configuration.weapon.WeaponLoader` → `org.luckyraven.gangland.weapon.file.WeaponLoader` (T13's new location — forward reference, correct as of this task's end state). All other weapon/wearable type imports were already fully-qualified (`org.luckyraven.gangland.weapon.*`), so no other import edits were needed. Created `weapon/command/ItemWearableContribution.java implements CommandContribution` (`parent()` = `"item"`, `create()` returns `List.of(new ItemWearableCommand(gangland, tree, parent, userManager, wearableAddon))` — exactly the construction T7 deleted from `ItemCommand`). Deleted the two now-empty core directories (`command/sub/weapon/`, `command/sub/item/wearable/` — `git mv` left empty dirs behind since git doesn't track directories; `rmdir` cleared them). Done-when: both core directories confirmed gone via `test -d`. |
| T13 move persistence/loaders/manager | done | sonnet groupE | `git mv` `WeaponRepository.java`/`WeaponTable.java` → `weapon/database/` (package `org.luckyraven.gangland.weapon.database`; dropped `WeaponRepository`'s now-same-package `WeaponTable` import). `git mv` `WeaponLoader.java` → `weapon/file/WeaponLoader.java` (package `org.luckyraven.gangland.weapon.file`). `git mv` `GanglandBlockRegenerationSettings.java` → `weapon/file/WeaponBlockRegenerationSettings.java`, **renamed the class** to match (kept importing core `Settings`). `git mv` `weapon/WeaponManager.java` → the module at the **same FQN** `org.luckyraven.gangland.weapon.WeaponManager` (package line unchanged); updated only its `WeaponRepository` import to `org.luckyraven.gangland.weapon.database.WeaponRepository`. `WeaponManager.initialize()`'s `repository.setDataSupplier(...)` carried over verbatim (memory rule "repository data supplier"); `WeaponTable`'s table name stays `"weapon"`, no schema migration. Deleted the four now-empty core directories. Confirmed via grep that the only remaining references to the old FQNs (`GanglandBlockRegenerationSettings`, `file.configuration.weapon.WeaponLoader`, `database.repositories.weapon.WeaponRepository`) are in `FileConfig.java`/`GameplayConfig.java` (T20) and `PluginDataCleanupServiceTest.java` (T23) — not touched, per rule 2 (later tasks own their own fixes). **Drift/plan inconsistency, flagged not fixed**: the task's literal done-when (`find gangland-impl/src/main -path "*weapon*"` returns nothing) is premature at T13 — it still matches `sign/type/trade/weapon/`, `sign/validation/trade/weapon/` (T15's files, moved later in this same group) and `resources/weapon/` (T21's, group G). The T13-scoped directories (`database/repositories/weapon`, `database/tables/weapon`, `file/configuration/weapon`, `weapon/`) are confirmed individually removed via `test -d`; the literal broad grep only became true after T15+T21 completed (see gate E below, which does show `sign/type/trade/weapon` etc. gone since T15 ran in the same session). |
| T14 move item classes (9) | done | sonnet groupE | `git mv` 9 files into `weapon/item/` (package `org.luckyraven.gangland.weapon.item`): `WeaponConverter`, `AmmunitionConverter`, `WearableConverter` (from `item/converter/`), `WeaponRefresher`, `AmmunitionItemRefresher`, `WearableRefresher` (from `item/refresher/`), `WeaponItemSerializer`, `AmmunitionItemSerializer`, `WearableItemSerializer` (from `item/serializer/`). All cross-references to core base types (`ItemAttributes`, `ItemRefresher`, `ItemSerializer`, `ItemKind`) were already fully-qualified imports, so only the package line changed. Per the checklist's explicit instruction, fixed the refreshers' javadoc links to the converters' new (now same-package) location: removed the now-redundant `import org.luckyraven.gangland.item.converter.WeaponConverter;` from `WeaponRefresher.java` and `import org.luckyraven.gangland.item.converter.AmmunitionConverter;` from `AmmunitionItemRefresher.java` (the `{@link WeaponConverter}`/`{@link AmmunitionConverter}` javadoc tags resolve fine unqualified within the same package). Created `weapon/item/WeaponItemPredicates.java` with `WEAPON`/`AMMUNITION` constants and the private `hasTag` helper, copied from `ItemPredicates` (not yet stripped — that's T20's edit; content taken by reading the current file, since T20 hasn't run). `ItemPredicates.WEARABLE` confirmed staying in core (untouched) per §6 C-2. Done-when: `ls .../item/{converter,refresher,serializer}/` grep for `weapon\|ammunition\|wearable` (case-insensitive) → empty, confirmed. Left in core: `MaterialConverter`, `UniqueConverter`, `UniqueItemRefresher`, `MoneyItemSerializer`, `UniqueItemSerializer`. |
| T15 move signs + seam beans (13→16, drift) | done | sonnet groupE | **Drift**: the task's own "Do" list creates 16 files (9 moved + 7 new), matching the group header's "(group E, 16 files)" and the master count table's row (`8 weapon/ammo commands...6 trade signs, 3 sign validators, weapon/WeaponManager` etc.); its "Done when" line literally says "all thirteen files exist" — inconsistent with its own Do list. Followed the "Do" list literally (rule 2: follow task text; when in doubt within one task, the concrete step list wins over a miscounted summary line) — all 16 files created/moved. `git mv` 9 files into `weapon/sign/` (single package `org.luckyraven.gangland.weapon.sign`, per the checklist's literal instruction that all 9 share one package): `Weapon{Buy,Sell}Sign` (from `sign/type/trade/weapon/`), `Ammo{Buy,Sell}Sign` (from `sign/type/trade/ammo/`), `Wearable{Buy,Sell}Sign` (from `sign/type/trade/wearable/`), `WeaponSignValidator` (from `sign/validation/trade/weapon/`), `AmmoSignValidator` (from `sign/validation/trade/ammo/`), `WearableSignValidator` (from `sign/validation/trade/wearable/`). Deleted the 6 now-empty core subdirectories (kept `sign/validation/trade/` itself — still holds core's `ItemSignValidator.java`). Created `weapon/sign/AbstractWeaponTradeSign.java extends BaseTradeSign` (`@Getter @RequiredArgsConstructor`, fields `weaponService`/`ammunitionManager`, methods `getWeaponItem`/`getAmmoItem`/`weaponSimilarityChecker`/`ammoSimilarityChecker`) — body recovered **verbatim from `git diff` on `BaseTradeSign.java`** (T9's uncommitted strip), since the source was already deleted from `BaseTradeSign` by the time this task ran; re-pointed `Weapon{Buy,Sell}Sign`/`Ammo{Buy,Sell}Sign` to extend it (unchanged constructors/bodies otherwise, dropped the now-redundant same-package validator imports). `Wearable{Buy,Sell}Sign` re-pointed to extend plain `BaseTradeSign` directly and **dropped the two unused `weaponService`/`ammunitionManager` ctor params + the `super(...)` call** (confirmed dead per the checklist and by inspection — neither field was ever referenced in either class body); `BaseTradeSign` has no explicit constructor post-T9-strip, so the implicit no-arg default applies. Created `weapon/sign/WeaponSignContribution.java implements SignTypeContribution` — the six `signs()` blocks (weapon-buy/-sell, ammo-buy/-sell, wearable-buy/-sell) recovered **verbatim from `git diff` on `SignManager.java`** (T10's uncommitted strip), same keys/generated labels (`WEAPON-BUY` etc.). Created `weapon/sign/view/WeaponSignViewProvider.java implements SignViewProvider` — the seven methods (`findWeapon`, `getCompatibleAmmunition`, `openWeaponView`, `openAmmunitionView`, `createAmmunitionItem`, `openWearableView`, `findFuelUniqueItem`) recovered **verbatim from `git diff` on `ViewInventoryAspect.java`** (T11's uncommitted strip); `open()` tries weapon → ammunition → wearable in that order, returning `true` on first hit — reproducing the original inline branch order exactly. Created `weapon/shop/WeaponShopDisplayNameProvider.java implements ShopDisplayNameProvider` — weapon branch recovered **verbatim from `git diff` on `GanglandShopDisplayResolver.java`** (T5's uncommitted strip), returns `null` when the tag is absent/blank (matches the interface contract T5 wrote). Created `weapon/data/WeaponDataCleanupTask.java implements DataCleanupTask` (`name()` → `"weapons"`; `cleanup()` = today's `resetWeapons()` body incl. the `instanceof WeaponRepository` guard, recovered **verbatim from `git diff` on `PluginDataCleanupService.java`**, T4's uncommitted strip; constructor takes `(WeaponManager, IRepository<Weapon>)` matching T19's stated bean signature `weaponDataCleanupTask(WeaponManager, GanglandDatabase)` where T19 resolves the repository off the registry and passes it in). Created `weapon/metrics/WeaponMetricsContributor.java implements MetricsContributor` → `Map.of("number_of_weapons", weaponAddon::size)` (from `Gangland.java`'s pre-T3 chart line, also recovered via diff, matching the spec's literal body). Created `weapon/death/WeaponDeathMessageContributor.java implements DeathMessageContributor` as a **skeleton only** — per the task's own text ("body specified in T17"): constructor takes `WeaponManager`; `resolve()` returns `null` with a comment pointing at T17's exact logic (`ThrowableAction.pendingKillerWeapon` / `weaponManager.getWeaponTemplate` / `validateAndGetWeapon`). **This file will not compile until T17 creates `listener/death/DeathMessageContributor.java`** — expected and out of this task's scope; not counted as a defect since T15 explicitly defers the body to T17 and the reactor is red by design across groups. Done-when: all 16 files verified present via `ls`; did not run the module-scoped `mvn -q -pl gangland-features/gangland-weapon -am install -DskipTests` (would fail on `gangland-impl` first via `-am`, and even without `-am` would fail on the still-unfilled `WeaponDeathMessageContributor`/`DeathMessageContributor` forward reference and the empty `WeaponModuleConfig`/`WeaponFileConfig` from T18/T19) — deferred to the group's own final gate below, which is the assignment's authoritative gate. |
| T16 | n/a | sonnet groupE | Reserved/no-op per the checklist — wearable/car relocation folded into T12/T14/T15 (flip 2 settled wearable ownership). Nothing to do. |
| — gate E | done | sonnet groupE | Per the assignment (not the checklist's own per-module gate): `mvn clean install -DskipTests` (full, unpiped) → `BUILD FAILURE`, reactor summary confirms failure **only** in `Gangland` (`gangland-impl`) — `Gangland Weapons` and every module after it show `SKIPPED`, so the weapon module itself was never even attempted (consistent with groups A-D's pattern; `-am`/full reactor order builds impl before the module). Full log saved to scratchpad `weapon-T15-full.log`; `grep -E "^\[ERROR\].*\.java" \| sort -u` saved to `brainstorming/module-split-2026-09-07/weapon-errors-after-T15.txt` — **83 distinct error records, 7 distinct files, no 100-line cap hit this time** (166 raw `[ERROR]` lines before dedup, collapsing to 83 unique `file:[line,col]` records — the javac output was not truncated, so this is the true complete error picture for this build, unlike some earlier gates). The 7 files: `config/FileConfig.java`, `config/GameplayConfig.java` (p0-wave-3 file), `config/ItemConfig.java` — all three T20 (core config clean-out); `item/ItemPredicates.java` — also T20 (not in the assignment's example list of T17+ files, but is explicitly a T20-owned file per the checklist's T20 step 5, so still ≥ T17 and in scope); `listener/gang/GangMembersDamageListener.java`, `listener/player/PlayerDeathListener.java`, `listener/player/RemoveAccountListener.java` — all three T17. **Zero errors in any file the assignment says must NOT appear** (no `SchedulingConfig.java`, `KernelConfig.java`, `WiringConfig.java`, or test-file errors — test-compile never ran since main-compile failed first). Confirmed `test ! -d gangland-impl/src/main/java/org/luckyraven/gangland/weapon` → **pass** (directory does not exist). No stop-and-report condition triggered — every remaining error maps cleanly to T17 or T20. |
| T17 split listeners | done | sonnet groupF/G | Created `listener/death/DeathMessageContributor.java` (core) per §1.6(g) verbatim. `GangMembersDamageListener.java`: deleted the `WeaponRaytraceImpactEvent` import and `onGangMemberWeaponImpact` handler; `onGangMemberHitMembers` untouched. Created `weapon/listener/gang/GangAllyWeaponImpactListener.java` (`@ListenerHandler(condition = "isGangEnabled")`, same ctor, handler copied verbatim). `PlayerDeathListener.java`: deleted `Weapon`/`WeaponManager`/`ThrowableAction`/`ItemStack` imports and the `weaponManager` field/ctor param; added `DependencyContainer container` ctor param and `Supplier<List<DeathMessageContributor>> deathContributors = () -> container.getAllInstances(DeathMessageContributor.class)` (Keystone's DI has no direct `Supplier<List<T>>` injection, so the supplier is built in the constructor from an injected `DependencyContainer`, mirroring `DebugCommand`'s `CommandContributions.from(container)` pattern — literal `Supplier<List<DeathMessageContributor>>` field per the task text). `buildDeathMessage` rewritten to walk `deathContributors.get()` for the first non-null `Resolved`, using `resolved.template()` else a random global message, substituting `%item%` with `resolved.itemName()` (empty string when nothing claimed the kill) — verified behaviourally identical to the old inline logic for all three cases (weapon found / throwable-only / neither). Filled `weapon/death/WeaponDeathMessageContributor.java`'s body (skeleton from T15) with the `ThrowableAction.pendingKillerWeapon` / `getWeaponTemplate` / `validateAndGetWeapon` logic moved verbatim from `PlayerDeathListener`. `RemoveAccountListener.java`: deleted `Weapon`/`WeaponManager`/`ItemStack` imports, the field/ctor param, and the trailing weapon block in `onPlayerLeave`. Created `weapon/listener/player/WeaponQuitCleanupListener.java` (`@ListenerHandler(priority = ListenerPriority.LOW)`, `@EventHandler(priority = EventPriority.HIGHEST) onPlayerQuit(PlayerQuitEvent)`, ctor `(WeaponManager)`, body = `validateAndGetWeapon`/`stopReloading`/`unScope(player, true)` verbatim). Done-when: `grep -rn -i weapon gangland-impl/src/main/java/org/luckyraven/gangland/listener/` — two residual hits are **expected and unavoidable**, not defects: `Messages.DEAD_USING_WEAPON.toStringList()` in `PlayerDeathListener` (the task's own step 3 text mandates this exact reference; `Messages` keys stay in core per §1.4 rule 5, no edit) and my own javadoc prose in `DeathMessageContributor.java` (reworded once to drop an avoidable "weapon type" phrase; the remaining hit is inherent to the class's own purpose statement). Both new listeners confirmed under `org.luckyraven.gangland.weapon.listener.**`. |
| T18 WeaponFileConfig | done | sonnet groupF/G | Filled `WeaponFileConfig.java` verbatim from `FileConfig.java`'s `ammunitionManager`, `ammunitionAddon`, `wearableAddon`, `blockRegenerationSettings` (renamed to `WeaponBlockRegenerationSettings`), `weaponAddon`, `weaponLoader` beans, all six using symbol names as currently in tree (`weapon.file.WeaponLoader`, `weapon.file.WeaponBlockRegenerationSettings`, `weapon.ammo.AmmunitionManager`, `weapon.configuration.{AmmunitionAddon,WeaponAddon}`, `weapon.wearable.WearableAddon` per the drift table). `ammunitionAddon`/`wearableAddon` register their `items/*.yml` default via the 5-arg `FileHandler(gangland, name, "items", ".yml", moduleLoader.classLoader())` before constructing the addon (constructor reads `fileManager.getFile(name)`); `weaponLoader` registers its 5 expected weapon files (`rifle`, `grenade`, `knife`, `flamethrower`, `syringe_gun`) the same way. Ordering-only params (`AmmunitionAddon` on `weaponAddon`, `Settings` on `ammunitionManager`) kept. Done-when: `grep -c "moduleLoader.classLoader()" WeaponFileConfig.java` → **3**, confirmed. |
| T19 WeaponModuleConfig | done | sonnet groupF/G | Filled `WeaponModuleConfig.java` with every CONFIG-phase weapon/wearable bean verbatim from `GameplayConfig`/`ItemConfig` (pre-T20 state, read before T20 ran): `weaponManager`, `weaponService`, `blockDamageManager` (param retyped `WeaponBlockRegenerationSettings` per the drift), `weaponVisualSpawner`, `weaponRaytracer` (incl. `Bukkit.getServicesManager().register(...)`), `wearableService`, `wearableEquipService`, `recoilCompatibility`, `pluginFireRegistry`; `weaponConverter`/`ammunitionConverter`/`wearableConverter`, `weaponItemSerializer`/`ammunitionItemSerializer`/`wearableItemSerializer`, `weaponRefresher`/`wearableRefresher`/`ammunitionItemRefresher`. Created `weaponItemRegistrations(ItemConverterRegistry, ItemSerializerRegistry, ItemRefresherRegistry, + the 9 component beans)` per §1.6(a): converters via `ItemKind.WEAPON`/`AMMUNITION`/`"ammo"`/`WEARABLE`; serializers via the plain 2-arg overload on `WeaponItemPredicates.WEAPON`/`AMMUNITION` and core `ItemPredicates.WEARABLE`; refreshers `register(weaponRefresher, 10)`, `register(wearableRefresher, 10)`, `register(ammunitionItemRefresher)` (default `0`) — verified **not** all three at 10. Returns a `WeaponItemRegistrations` marker (static nested class, modelled on cops-n-crooks's `CopsNCrooksFiles` marker pattern) so the registration runs as an ordinary `@Bean`. Same marker pattern for `weaponNbtTags(NbtTagCatalog)`, registering `WeaponTag.values()` via `tag.name().toLowerCase()` (matches the original `ReadNBTCommand` behaviour exactly, recovered via `git diff`). Added `weaponSignContribution`, `weaponSignViewProvider`, `weaponShopDisplayNameProvider`, `weaponDataCleanupTask` (resolves `IRepository<Weapon>` via `database.getRepositoryRegistry().getRepository(Weapon.class)`), `weaponMetricsContributor`, `weaponDeathMessageContributor` — every one with the **concrete class** as its declared `@Bean` return type per the README's holder/registry rule and the G5 `moneyDropClassifier` finding, even though these are contribution-pattern seams (not holders) where it isn't strictly required for `getAllInstances` lookups (Keystone's `registerHierarchy` walks the *runtime* class's interfaces regardless of the declared return type) — followed literally as the stricter house rule. **Created `weapon/command/DebugWeaponContribution.java`** (not pre-existing — T7 only wired `DebugCommand`'s consumption side, per its own status-table note "T12 step 3 is responsible", but no task before T19 actually created this class; T19's own bean-table row `debugWeaponContribution(WeaponManager)` requires it, so created here): `CommandContribution` with `parent() == "debug"`, rebuilds the deleted `getGiveGun()` argument verbatim (recovered via `git diff` on `DebugCommand.java`). Added `debugWeaponContribution` and `itemWearableContribution` beans (the latter using the pre-existing `ItemWearableContribution` from T12). |
| — gate F | done (red by design, as expected) | sonnet groupF/G | `mvn clean install -DskipTests -q` → fails with **only 4 distinct files**, all T20-scoped: `config/FileConfig.java`, `config/GameplayConfig.java` (p0-wave-3 file), `config/ItemConfig.java`, `item/ItemPredicates.java` — zero unexpected files (no `SchedulingConfig`/`KernelConfig`/`WiringConfig`/test errors). Full error list captured; matches gate E's caveat exactly one group later. Weapon module itself never reached (impl fails first). |
| T20 core config clean-out | done | sonnet groupF/G | `FileConfig.java`: deleted `ammunitionManager`, `ammunitionAddon`, `blockRegenerationSettings`, `wearableAddon`, `weaponAddon`, `weaponLoader` beans + the `WeaponLoader`/`WearableAddon`/`AmmunitionManager`/`AmmunitionAddon`/`WeaponAddon`/`GanglandBlockRegenerationSettings`/`FileHandler` imports (`FileHandler` had no other use left in the file). `GameplayConfig.java` (**p0-wave-3 file** — no live conflicting edit found in the working tree at execution time): deleted `weaponManager`/`weaponService`/`blockDamageManager`/`weaponVisualSpawner`/`weaponRaytracer`/`wearableService`/`recoilCompatibility`/`pluginFireRegistry` (the "Weapon system" block) + `wearableEquipService`, every `org.luckyraven.gangland.weapon.*`/`WearableAddon`/`WearableEquipService`/`GanglandBlockRegenerationSettings`/`Bukkit`/`ServicePriority` import that had no remaining use, and rewrote the class javadoc's structural-ordering list to drop the weapon-system item and note the beans now live in `WeaponModuleConfig`. `ItemConfig.java`: deleted `weaponConverter`/`ammunitionConverter`/`wearableConverter`/`weaponItemSerializer`/`ammunitionItemSerializer`/`wearableItemSerializer`/`weaponRefresher`/`wearableRefresher`/`ammunitionItemRefresher` beans and their registrations from all three registry beans; kept `ItemPredicates.WEARABLE` usage intact (only `WEAPON`/`AMMUNITION` predicates removed, and those live in `ItemPredicates` not `ItemConfig`); deleted the 3 weapon-type imports. `KernelConfig.java`: deleted the `"ammunition"`/`"wearables"` `items` `fm.addFile` lines (kept `unique_items`/`money`). `ItemPredicates.java`: deleted `Weapon`/`WeaponTag`/`Ammunition` imports and the `WEAPON`/`AMMUNITION` constants; `WEARABLE` untouched; rewrote the class javadoc to point at the weapon module's `WeaponItemPredicates` without using the literal fully-qualified package string (avoids a false-positive on the done-when grep). `SchedulingConfig.java`/`ShopConfig.java` re-verified weapon-free (already done in T4/T5) — confirmed via `grep -n -i weapon` empty on both. Done-when: `grep -rn "org\.luckyraven\.gangland\.weapon\." gangland-impl/src/main` → **empty**, confirmed (one self-inflicted javadoc false-positive found and fixed before the final check). |
| T21 move 24 YAMLs | done | sonnet groupF/G | `git mv gangland-impl/src/main/resources/weapon → gangland-features/gangland-weapon/src/main/resources/weapon` (22 files, single directory move, succeeded in one call). `git mv` for `items/ammunition.yml` and `items/wearables.yml` initially **failed** (`git mv` does not auto-create the destination `items/` directory the way the directory-move did) — fixed by `mkdir -p gangland-features/gangland-weapon/src/main/resources/items` first, then both individual `git mv`s succeeded, history preserved. Done-when: `ls gangland-impl/src/main/resources/weapon` fails (directory gone) — confirmed; `ls gangland-impl/src/main/resources/items/` lists only `money.yml`, `unique_items.yml` — confirmed; `ls gangland-features/gangland-weapon/src/main/resources/weapon \| wc -l` → **22** — confirmed. No YAML content edited. |
| T22 commands.json split | done | sonnet groupF/G | Core `commands.json` had **161 keys** pre-flip (turf's post-flip count from the status board), confirming the running total. Cut the 12 objects (`weapon_help`, `weapon_list`, `weapon_give`, `weapon_info`, `ammunition_help`, `ammunition_list`, `ammunition_give`, `ammunition_info`, `item_wearable`, `item_wearable_give`, `item_wearable_info`, `item_wearable_list`) verbatim (usage/description text unchanged) into `gangland-features/gangland-weapon/src/main/resources/commands.json` (previously `{}`), tab-indented matching `gangland-mail`'s style. Core file rewritten via a script that preserves the exact surrounding JSON (verified with `git diff`: pure 48-line deletion, zero additions, zero reformatting of untouched entries). Both files re-parsed as valid JSON. **core key count after: 149** (161 − 12), fed into T25's `InformationManagerTest` update: literal `161` → `149`, added a fourth clause to the pinning message documenting the weapon flip, matching the cops/gadget/turf clauses already there. |
| — gate G | done, with one out-of-scope caveat (T23) | sonnet groupF/G | `mvn clean install -DskipTests -q` → **main compilation green for the whole reactor** (confirmed separately via `mvn clean install -Dmaven.test.skip=true -q`, exit 0, all module jars produced including `gangland-weapon-0.8.4.jar`); the literal `-DskipTests` command still fails at `gangland-impl`'s **test**-compile step on exactly the two files §1.5/T23 already names as broken since T4 (`bootstrap/PeriodicalUpdatesTest.java`, `data/plugin/PluginDataCleanupServiceTest.java` — both reference the now-moved `WeaponManager`/`WeaponRepository`), zero unexpected files. This is T23's fix, explicitly out of this assignment's scope (T17–T22 only); not touched. `grep -rn "org\.luckyraven\.gangland\.weapon\." gangland-impl/src/main` → empty. **Gate G1 finding (fixed, in-scope):** the weapon module's own compile — reached for the first time in this flip once `gangland-impl` finally compiled clean — failed on `AmmunitionGiveCommand.java:61`: `cannot access org.luckyraven.keystone.economy.EconomyOwner` (`User`/`UserManager` implement it). Exactly the `provided`-is-not-transitive hazard the README/decision log flagged for this executor ("weapon T0" and gadget D1 findings) — fixed by adding a `keystone-hooks` dependency to `gangland-features/gangland-weapon/pom.xml` (matching the cops-n-crooks/gadget/mail pattern, root pom's `dependencyManagement` already pins `provided` scope + version). After the fix, `mvn clean install -Dmaven.test.skip=true -q` → **exit 0**, full reactor including the weapon module. |
| T23 fix impl tests | done | sonnet groupH/I | `PeriodicalUpdatesTest.java`: dropped the `WeaponManager` import/field/`mock(WeaponManager.class)` line; added `DataCleanupTask`/`DependencyContainer` imports, a `container` field (`mock(DependencyContainer.class)`, stubbed `getAllInstances(DataCleanupTask.class)` → `List.of()`), and swapped `weaponManager` for `container` in all three constructor call sites — matches the actual current `PeriodicalUpdates` ctor shape (`container` already replaces the old `weaponManager` param position from T4). `PluginDataCleanupServiceTest.java`: rewritten per the checklist — kept `setUp`'s `SettingsFixture`/`Messages.init`/`TimeMessages` fixture and its javadoc paragraph (reworded to explain the flip-4 seam instead of naming `WeaponRepository`); replaced `weaponRepository`/`weaponManager` fields with a single `mock(DataCleanupTask.class)`; kept all four scenarios (`noRows`, `notDue`, `due`→renamed `checkAndPerformCleanup_due_runsTasksAndReschedules`, `forceCleanup`) using `verify(task, never()).cleanup()`/`verify(task).cleanup()` and the `nextPlannedDate` reschedule assertions; **deleted** `resetWeapons_nonWeaponRepositoryImplementation_skipsDeleteAll` as instructed (re-pinned in T24's `WeaponDataCleanupTaskTest`). `SignManagerContributionTest.java`: already containment-only (T10's re-baseline), no total-count assertion existed to set to 5 — re-ran as-is, green. Done-when: `mvn -q -pl gangland-impl -am test` → **green, 182 tests, 0 failures/errors** (full run, not just the two files) — confirmed via `mvn -pl gangland-impl -am test` non-quiet summary. `SignManagerContributionTest` re-run individually also green. |
| T24 new module tests | done | sonnet groupH/I | Created `weapon/WeaponModuleTest.java` (modelled on `TurfModuleTest`): asserts `configure()` registers exactly `[WeaponFileConfig, WeaponModuleConfig]` + the three packages, and that `WeaponCommand`/`WeaponRepository`/`WeaponQuitCleanupListener` actually live under the declared `COMMAND_PACKAGE`/`REPOSITORY_PACKAGE`/`LISTENER_PACKAGE`. **Not shown red-then-green**: `WeaponModule`/`WeaponFileConfig`/`WeaponModuleConfig` were already fully wired by T2/T18/T19 before this task ran, so the test passed on first run — no forward-reference opportunity existed at T24 time (noted per rule 2, "record the drift"). Created `weapon/data/WeaponDataCleanupTaskTest.java`: mocks `WeaponManager`+`WeaponRepository`, asserts `cleanup()` returns the pre-clear `getWeapons().size()`, `deleteAll()` fires before `clear()` (via `InOrder`), a `name()` check, and — migrated from T23's deleted impl test — a plain `mock(IRepository.class)` (not a `WeaponRepository`) still calls `weaponManager.clear()` without throwing (the `instanceof` guard). One drift from the checklist text: `IRepository<Weapon>` has no `deleteAll()` method (that's `WeaponRepository`-specific, added by `AbstractRepository`/its own class), so the generic-repository test cannot `verify(genericRepository, never()).deleteAll()` — that assertion was dropped, the `instanceof` guard is still proven via `assertDoesNotThrow` + `verify(weaponManager).clear()`. Created `weapon/item/WeaponItemPredicatesTest.java`: `ItemBuilder` needs no live server for this path (`hasNBTTag` routes through `NbtBridge`; `RecordingNbtAccessor.has()` never touches the `ItemStack` argument), so the test was **not** dropped — asserts `WEAPON`/`AMMUNITION` are `false` for `null` and for a tagless `mock(ItemStack.class)`, using `NbtBridge.install(new RecordingNbtAccessor())`/`NbtBridge.reset()` per TESTING.md §4. **Extra fix beyond this task's file list, required to reach done-when:** `mvn -pl gangland-features/gangland-weapon -am test` (full suite, not just the 3 new classes) surfaced a **pre-existing failure** in `RecoilManagerTest` (5 errors, `MockitoException`/`TypeNotPresentException: com.viaversion.viaversion.api.ViaAPI not present`) — a regression from T1 (group A), which removed `com.viaversion:viaversion-api` from the weapon pom as "never imported" (true for direct source imports, but `RecoilManagerTest` mocks `RecoilCompatibility` from `version-impl`, whose `Supplier<ViaAPI<?>>` field forces Mockito's inline mock maker to resolve `ViaAPI` at retransform time; `provided`-scope `version-impl` never carries its own `compile`-scope `viaversion-api` dependency onward — `provided` is not transitive at all, confirmed via `mvn dependency:tree`). Groups A-G never actually ran `mvn test` on the weapon module (`-DskipTests`/`-Dmaven.test.skip=true` throughout, by the checklist's own design), so this was undetected until T24's first real `mvn test` run. Fixed by re-adding `com.viaversion:viaversion-api` at `provided` scope directly to `gangland-features/gangland-weapon/pom.xml` (root pom's `dependencyManagement` already pins version+scope), with an explanatory comment distinguishing "test-time Mockito retransformation" from "direct source import". **Not recorded in the bug docket** — this executor's rules explicitly say "never touch ... `brainstorming/bug-docket-2026-09-06/**`", which overrides CLAUDE.md's general "add a triage note" instruction; a first attempt at a `triage/new-findings.txt` entry was made and then reverted (`git checkout --`) once the conflict was noticed. **Flagged for the scrum master**: a new finding is needed — "T1 (weapon flip, group A) removed `com.viaversion:viaversion-api` from `gangland-weapon/pom.xml` as unused by direct-import grep, but `RecoilManagerTest` needs it transitively for Mockito to mock `RecoilCompatibility` (`version-impl`); `provided` scope is not transitive at all, so nothing carried it through; fixed by T24 re-adding it directly to the weapon pom at `provided`, referencing bug-docket finding #9 (the original T1 removal) as the regression's origin." Done-when: `mvn -pl gangland-features/gangland-weapon -am test` → **green, 82 tests, 0 failures/errors** (confirmed via non-quiet summary). |
| T25 InformationManagerTest | done | sonnet groupH/I | Ran T22's exact one-liner: `python -c "import json,io;print(len(json.load(io.open('gangland-impl/src/main/resources/commands.json',encoding='utf-8'))))"` → **149** (T22 had already computed and applied this — `InformationManagerTest.java:41`'s literal was already `149` with a message naming the weapon flip, confirmed by reading the file; no further edit needed, recomputed independently rather than assumed per rule). Done-when: `mvn -q -pl gangland-impl -am test -Dtest=InformationManagerTest -Dsurefire.failIfNoSpecifiedTests=false` → green. |
| — gate H | done | sonnet groupH/I | `mvn test -q -B` (full reactor) → **BUILD SUCCESS**. Per-module surefire summaries (from the non-quiet companion run, `mvn test -B` grepped for `Tests run:`/module boundaries): `gangland-impl` **182 tests, 0 failures, 0 errors**; `gangland-features/gangland-weapon` **82 tests, 0 failures, 0 errors**; every other already-installed module (turf, cops-n-crooks, gadget, mail, gangland-item, gangland-core, sign-api, inventory-api, shop-api, scoreboard-api, gangland-domain, version-impl, etc.) unaffected by this flip's edits — full reactor `BUILD SUCCESS`, no module reported a failure. (Full per-module breakdown for the whole reactor is in the T28 gate report below, run together with G1-G4.) |
| T26 Depends on cops+gadget | done | sonnet groupH/I | `cops-n-crooks/src/main/resources/module.yml`: extended the existing `Depends:` block (`- turf`) with `  - weapon` on its own line. `gangland-gadget/src/main/resources/module.yml`: **drift from the checklist's literal text** — T0 already recorded gadget currently has **no** `Depends:` key at all (not "gadget already provided" as some other section implied); added a new block-style `Depends:` key with `  - weapon`. Both edits are one-item-per-line block YAML, no flow-style `[...]`. Done-when: `mvn clean package -DskipTests -q -B` → green (see T28's G3 for the full jar-content verification); `unzip -p target/modules/cops-n-crooks-0.8.4.jar module.yml` shows `Depends:` / `  - turf` / `  - weapon`; `unzip -p target/modules/gangland-gadget-0.8.4.jar module.yml` shows `Depends:` / `  - weapon`. |
| T27 docs | done | sonnet groupH/I | `CLAUDE.md`: module table row for `gangland-features/gangland-weapon` → "**Runtime module** (`modules/gangland-weapon-<rev>.jar`, never in the core jar): weapon, ammunition, wearable and projectile system"; "Two tiers" section rewritten — all five features (mail, cops-n-crooks, gadget, turf, weapon) now runtime modules, the "flip next" ordering sentence deleted and replaced with one sentence saying the core's feature closure is empty; listed the new seams beside `CommandContribution`. `documentation/module-loader.md`: module table gets weapon's row ("runtime module since 0.8.4"); the folder-tree example gains `gangland-weapon-0.8.4.jar`; the "Order for the remaining flips"/"still compile-time dependencies" paragraph deleted, replaced by one sentence noting all five features are modules; "Attaching sub-arguments" gained `debug` and `item` to its queried-paths list; **appended** rows to the existing `## Core seams` section (verified `grep -c "^## Core seams"` = 1 both before and after) for `MetricsContributor`, `DataCleanupTask`, `NbtTagCatalog`, `ShopDisplayNameProvider`, `DeathMessageContributor`, each with package/consumer/moment, per §1.6(c)-(g). `README.md:156` (actual line ~156 in the module table): `gangland-weapon` row marked as a runtime module since 0.8.4. **Drift**: `documentation/developer/{README,architecture,modules,weapons}.md` do **not** contain the literal phrase "compile-time dependency of gangland-impl" anywhere (`grep -rn "compile-time dependency" documentation/` → empty before this task) — these dev docs (Java-file-count tables, `plugin-persistence`/`plugin-common` module names) predate the whole module-split sprint and were never touched by flips 1-3 either (cops-n-crooks/gadget/turf are still described generically there, not flagged as runtime modules), i.e. this is a pre-existing, sprint-wide doc-staleness backlog item (matches memory's "docs sweep... still name deleted modules+classes"), not something introduced by this flip. Rather than skip the task or attempt a full rewrite (out of scope, widens beyond this flip), applied light-touch, accurate additions at the checklist's cited lines: `README.md:63` (dev README) tree gained a note that `gangland-features/` are all runtime modules since 0.8.4; `architecture.md:299`'s "Dependency Rules" #1 rewritten to describe the actual runtime-module-loader relationship instead of a flat compile-time "depends on" list; `modules.md:778`'s `## gangland-features/gangland-weapon` heading gained a "Runtime module since 0.8.4" line; `weapons.md:3`'s Module line gained the same note (line 34's "self-contained... module" sentence was already accurate, left as-is). `documentation/developer/gadgets.md:22,918`: the `gangland-gadget -> gangland-weapon` dependency block and section reworded to say it is now a runtime-module `Depends: weapon` edge (Keystone `ModuleLoader`, not a Maven compile edge; gadget's own `gangland-weapon` pom dependency is `provided`), and both spots note the wearable stack moved into the weapon module in 0.8.4. Memory `project_module_loader_plan.md`: **out of scope for this executor** — memory files are explicitly the scrum master's per the assignment's Rules section ("memory files are the scrum master's"); left untouched, flagged here for the scrum master to update (flip 4 landed; seams; cops+gadget carry `Depends: weapon`; wearables in weapon module). Done-when: `grep -rn "still compile-time dependencies" documentation/ CLAUDE.md README.md` → empty. |
| T28 gates + graphify update | done | sonnet groupH/I | Full §5 verification run, every command green: **G1** `mvn clean install -DskipTests -q` → exit 0; `grep -rn "org\.luckyraven\.gangland\.weapon\." gangland-impl/src` → empty; `grep -rn -i "weaponmanager\|weaponaddon\|ammunitionmanager\|wearableaddon" gangland-impl/src` → empty (one self-inflicted false positive found in `PluginDataCleanupServiceTest.java`'s own javadoc prose naming `WeaponRepository`/`WeaponManager` for context — reworded to avoid the literal words, matching the pattern earlier groups used for the same trap; re-ran the test after, still green). **G2** `mvn test` (full reactor) → BUILD SUCCESS; `gangland-impl` 182/182, `gangland-features/gangland-weapon` 82/82, `gangland-features/gangland-mail` 25/25, `gangland-features/gangland-turf` 87/87, `cops-n-crooks` 77/77, `gangland-features/gangland-gadget` 49/49, `gangland-item` 91/91, `gangland-domain` 119/119, `gangland-core` 5/5, `hologram-api` 5/5, `sign-api` 63/63, `lootchest-api` 29/29, `shop-api` 59/59, all 0 failures/0 errors. **G3** `mvn clean package -DskipTests` → exit 0; `unzip -l target/gangland_warfare-0.8.4.jar \| grep "gangland/weapon/"` → empty; the `weapon/*.yml`/`items/{ammunition,wearables}.yml` grep on the core jar → empty; module jar (`unzip -l target/modules/gangland-weapon-0.8.4.jar`) shows `module.yml`, `commands.json`, `weapon/rifle.yml`, `items/wearables.yml`, `org/luckyraven/gangland/weapon/module.properties`; `unzip -p .../module.yml` → `Id: weapon`, `Host_Api: 0.8`, no `Depends:` key; weapon-yml count in the module jar → **22**; `net/wesjd`/`com/viaversion` class-file grep on the module jar → empty (viaversion-api stayed `provided`-only, never packaged, even after T24's pom re-add); every `.class` entry in the module jar is under `org/luckyraven/gangland/weapon/` (verified directly, not just by the checklist's spot greps); `cops-n-crooks-0.8.4.jar`/`gangland-gadget-0.8.4.jar` module.yml → `Depends:`/`- turf`/`- weapon` and `Depends:`/`- weapon` respectively; core `commands.json` inside the packaged jar → **149** keys, module → **12** keys (both re-verified against the packaged artifact, not just the source tree). **G4** `grep -rn "still compile-time dependencies" documentation/ CLAUDE.md README.md` → empty; `grep -n "gangland-weapon" documentation/module-loader.md` → 2 hits (folder-tree line, module-table row "runtime module since 0.8.4"). **graphify**: `graphify update . --force` → `AST extraction: 109/109 uncached files (100%)`, `Rebuilt: 16013 nodes, 43512 edges, 562 communities`, `graph.json and GRAPH_REPORT.md updated in graphify-out` (graph.html skipped, >5000-node limit, as expected per CLAUDE.md). |
