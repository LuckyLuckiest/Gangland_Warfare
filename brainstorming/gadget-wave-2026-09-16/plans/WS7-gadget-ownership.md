# WS7 — Gadget ownership + Bartizan soft-coupling

Planner: Sonnet, gadget wave 2026-09-16. Graph: Gangland `graphify-out/` fresh (built 2026-09-14 18:43, HEAD
fb460b35 committed 18:10 — graph newer than HEAD, 0 newer `*.java`, not stale); Bartizan `graphify-out/` rebuilt
2026-09-16 (4068 nodes / 11695 edges). Every code claim below is backed by a direct `file:line` read.

## §0b Review response

Reviewed by Opus (`reviews/REVIEW-WS7.md`), verdict **PASS WITH FIXES**. All 4 blockers and every correction are
applied below (this is a fix pass, not a rebuttal) except where noted DISPUTED. Cross-plan facts from the WS8
review are folded in at the end of this section.

### Blockers

| Id | Status | What changed |
|---|---|---|
| **B1** — pom drops `bartizan-api` while 3 usages survive, won't compile | **FIXED** | §2's Bartizan/Gangland file table now keeps `bartizan-api` at `provided` scope in `gangland-gadget/pom.xml` permanently. Only `module.yml`'s `Plugins: [Bartizan]` entry is removed (G5). |
| **B2** — surviving Bartizan-touching method bodies throw `NoClassDefFoundError` once soft, not "already null-safe" | **FIXED** | This was the deepest error in the plan. §3 and G5 are redesigned: `CarDamageListener.weapons()`/`resolveMeleeDamage` are **extracted** into a new plain (non-Keystone-annotated) class `CarMeleeWeaponLookup`, called from `CarDamageListener.onVehicleDamage` only behind an explicit `if (Settings.isBartizanAvailable())` guard at the call site — so `CarMeleeWeaponLookup`'s class (whose own method signatures still carry `WeaponCatalog`/`Weapon`/`MeleeWeapon`) is never loaded or reflected on unless Bartizan is confirmed present. `CarDamageListener` itself now has **zero** methods whose signature names a Bartizan type, so Keystone's `getDeclaredMethods()`/`getMethods()` listener scan (the same mechanism `CitizensBlindScan`'s javadoc documents) never touches a Bartizan symbol for this always-loaded class. `CarWeaponDamageListener` (the two `@EventHandler`-Bartizan-typed methods) keeps the `condition = "isBartizanAvailable"` guard, which was already correct — that mechanism was never in question, only the two extra methods hiding in the "unconditioned but safe" class. New smoke rows exercise, not just boot (§7). |
| **B3** — `items/jetpacks.yml` schema missing `Material`/`Name`/`Lore`/`Base_Damage_Reduction`/`Leather_Color`/`Traits`; nothing stamps fuel NBT | **FIXED, with one correction to the review** — the stamping method is `Jetpack.buildItem()` (mirroring `Car.buildItem()`, `Car.java:86-110`, which is what calls `builder.addTag(FuelKey.FUEL_ID..., ...)` at `:103-107`), **not** `JetpackItemSerializer` (`CarItemSerializer.java:20-24` only ever `extract()`s an id back out — it has no stamping code in the Car pattern either). §5/§6 corrected to name `Jetpack.buildItem()`. Full YAML schema rewritten in §5 with `Material`, `Name`, `Lore`, `Custom_Model_Data`. `Base_Damage_Reduction`/`Traits` are **deliberately dropped**, not ported — see B4/WS7-D4. |
| **B4** — armour/trait loss on leaving Bartizan is a silent, undisclosed nerf | **FIXED** | New decision **WS7-D4** added to §9 (ponytail default: accept the loss, document it in the Bartizan 0.4.0 migration note; re-implementing damage reduction in gadget is named and rejected as over-engineering, per the review's own steer). |

### Corrections

| Id | Status | What changed |
|---|---|---|
| **C1** — `fuel_efficient` analysis wrong; "new bug GD-29" not real | **FIXED** | `traitLevel()` reads the wearable *definition's* config map, not per-item NBT (`Wearable.java:86,309-311`); the shipped jetpack carries no `FUEL_EFFICIENT` trait at all (`REINFORCED: 1`/`LIGHTWEIGHT: 2` only, confirmed by direct read of `wearables.yml:246-260` below). §11's "new bug" bullet is deleted outright, not filed. WS7-D3 is deleted entirely (S2). |
| **C2** — Risk 4 undercounts the shared-state split (3 call sites each, not 1–2) | **FIXED** | Risk table rebuilt (§8) with the real site counts and a named `CarDamageState` holder class carrying both `Set<UUID>`s, constructed once and shared between `CarDamageListener` and `CarWeaponDamageListener`. |
| **C3** — §11 omits GD-27 (token-race), which the split aggravates | **FIXED** | Added to §11 with an explicit "worsened by the split — must be consumed in one place by `CarDamageState`" note. |
| **C4** — GD-12 is not test-pinned; `JetpackAddonLoadTest` is new-red-first, not a flip | **FIXED** | §7/§11 reworded: "new test, proven red against pre-fix `JetpackAddon` (no such guard exists today)". |
| **C5** — `Settings.isBartizanAvailable()` needs a `Host_Api` minor bump | **FIXED** | Confirmed `GanglandApi.java:13` reads `VERSION = "1.0"` today. G1 now bumps it to `1.1` and gadget's `module.yml` `Host_Api: 1.0` → `1.1`. |
| **C6** — §10's ReflectionGuard justification is backwards | **FIXED** | §10 reworded to cite `Settings.java:287-292` directly (Keystone's guard falls back to Bukkit's unguarded registration, which throws the same way; skipping construction via `condition=` is the actual fix) instead of crediting `ReflectionGuard`. |
| **C7** — "leaf-only shape" misdescribes `commands.json`; §2's "Deleted: nothing" contradicts §3 | **FIXED** | §5 now states car ships `car`/`car_help`/`car_give`/`car_info`/`car_list` and jetpack deliberately ships only `jetpack_give` (+ parent node), and mentions the `var list` help filter. §2 reworded: no *files* deleted, but `isJetpackFuelSink`'s Bartizan body and `JetpackService.wearables()` are rewritten/deleted in place. |

### Simplifications adopted

- **S1** — `JetpackTask.isScoped()` **deleted outright** (flips WS7-D2 to "resolved by deletion", not a live decision anymore). Satisfies K1 literally, removes the B2 crash surface for the jetpack path entirely, costs one micro-interaction (can't block thrust while aiming a scoped Bartizan weapon).
- **S2** — `Fuel_Efficient_Level` **not** added to the YAML; the whole trait branch in `getEffectiveConsumptionRate` is deleted (collapses to `return extraInt(...)`). WS7-D3 is retired.
- **S3** — Checked whether `CitizensBlindScan` takes a blinded-prefix parameter (direct read, `gangland-core/.../testsupport/CitizensBlindScan.java:211-216`): it does **not** — `"net.citizensnpcs."` is hardcoded in an anonymous `URLClassLoader.loadClass` override. Reuse-as-is is not possible. Resolution: a small ~30-line sibling class, but scoped to **`gangland-gadget`'s own test tree** (not `gangland-core`, which is shared by every module's tests and would widen the blast radius for a single WS7 consumer) — see revised G5.
- **S4** — adopted at review, **reversed at merge (§0c)**: `JetpackItemRefresher` **is registered in G3** beside the converter and serializer, mirroring `CarItemRefresher`. Reason: the house rule `feedback_item_refresher_pattern` (stateful items refresh on every shop/trader delivery, not once at placement) and the fact that Bartizan's `WearableRefresher` is what keeps a shop-delivered jetpack factory-fresh today — removing the jetpack from Bartizan without a Gangland-side refresher would deliver frozen partial-fuel jetpacks from shop templates. It is still not the migration path (§6).

### Cross-plan facts (from the WS8 review) adopted here

1. **Permission pattern corrected** (closes old §13 item): cars use a **per-item** permission,
   `Car.getPermission()` → `"gangland.cars." + carId` (`Car.java:75-77`), registered via
   `permissionRegistrar.accept(car.getPermission())` at `CarAddon.java:145`, enforced at
   `CarInteractListener.java:59`. Jetpack now mirrors this exactly: `Jetpack.getPermission()` →
   `"gangland.jetpacks." + jetpackId`, registered the same way at `JetpackAddon` load, enforced in
   `JetpackEquipListener`/`JetpackService.activate` at equip time (the jetpack's analogue of "interact with the
   placed car" — there is no give-time check for cars either, so none is added for jetpack's give command).
2. **`items/jetpacks.yml` registration step made explicit**: both this plan and WS8's had the same silent gap.
   `JetpackAddon` must be registered exactly like `CarAddon` is, in `GadgetFileConfig` (**FILE phase**, not
   `GadgetModuleConfig`'s CONFIG phase) — `GadgetFileConfig.java:38-45` is the template:
   `fileManager.addFile(new FileHandler(plugin, "cars", "items", ".yml", moduleLoader.classLoader()), true)`.
   G2 now states this precisely.
3. **`GadgetType` is not a shared-edit risk** — confirmed zero reactor-wide usages (WS8's finding); this plan never
   touched it and still doesn't. The real shared-edit surface with WS8 is named explicitly in §10:
   `GadgetItemPredicates`, `GadgetModuleConfig`, `commands.json`, `ItemKind`, `GadgetFileConfig`.
4. Both plans independently chose per-type give commands over a generic registry — no change needed, noted as
   agreement.

## §0c Orchestrator fixes at merge (2026-09-16, after the re-review)

The re-review (`reviews/REVIEW-WS7.md`, "Re-review") left three wiring blockers and ended "once B5–B7 are applied this
plan is PASS". They are applied in place below (§2 tree, §3 seam row, §4 G3/G5, §7 test row, §12) exactly as the
reviewer prescribed; no design changed:

- **B5** `CarMeleeWeaponLookup` is a **static** helper — no `@Bean`, no field, no constructor parameter, no
  `@AutowireTarget` entry; the only reference is the static call inside the `isBartizanAvailable()` branch.
- **B6** `CarWeaponDamageListener` is `@ListenerHandler(condition = "isBartizanAvailable")` + `@AutowireTarget`,
  auto-scanned like `CarDamageListener.java:54-56`; it has no bean method. `CarDamageState` is the only new bean.
- **B7** `CarMeleeWeaponLookupGuardTest` asserts the vanilla fallback damage value, not "non-invocation".
- **S4 reversed:** `JetpackItemRefresher` is registered in G3 (house rule `feedback_item_refresher_pattern`; Bartizan's
  `WearableRefresher` is what keeps shop-delivered jetpacks factory-fresh today). G3 becomes 3.0 days, total ~11.5.

Verdict after §0c: **PASS** (per the reviewer's stated condition). Executors start from this text.

**§0d Execution corrections (2026-09-16, orchestrator):** §0b B1's "`readExtraTags()` needs no parser change" was wrong — its `Jetpack:` fallback branch called `legacyJetpackToExtraTags`, so G0 also deleted that branch plus `sectionToMap`/`asConfigurationSection` (review G0-G1). WS7-D4 was decided by the user as "Bartizan as the api layer keeps the traits": Bartizan 0.4.0 adds `WearableCatalog.register` + an `external` flag on `Wearable` (damage-only entries Bartizan never builds/converts/gives/serialises); gadget registers the jetpack inside the `isBartizanAvailable()` guard — see `../../decoupling-wave-2026-09-14/exec/WS7/D4-design.md`. Root pom `<revision>` → 0.9.2 is a G1 step (ruling W10). WS7-D1 = C adds gate G5b (civilians soft).

## 1. Scope

User's words (README.md:4-11): *"I want to understand why the jetpack was moved to bartizan plugin... I need it to
be in gangland since it is part of gadgets."* / *"We can't have a dependency of having it moved to Bartizan, then
need Gangland Warfare then need gangland-gadgets module."* / *"Do a proper scan of such coupling."*

| | Item |
|---|---|
| **In** | Jetpack becomes a gadget-owned item (domain type, YAML, converter/serializer, NBT identity, give command, per-item permission) with zero `org.luckyraven.bartizan` **symbols in its own class signatures** (K1 — `bartizan-api` stays a `provided` pom dependency for the car-damage path, see B1). Gadget's `module.yml` `Plugins: [Bartizan]` → dropped; the surviving Bartizan touches (car weapon-damage events, car melee-weapon lookup) are isolated so no always-loaded gadget class ever names a Bartizan type in its own method signatures (K2, B2). Bartizan-side jetpack residue removed, Bartizan 0.3.0 → 0.4.0 (K3). `Settings.isBartizanAvailable()` added, `Host_Api` 1.0 → 1.1 (C5). civilians/cops-n-crooks Bartizan edges classified and a soft-vs-hard recommendation given (K7) — **not decided or executed here**. |
| **Out** | Actually flipping civilians/cops-n-crooks/turf to soft Bartizan (`WS7-D1`, a follow-up gate). A `Gadget` interface (K5). The grappling hook and other new gadgets (WS8). Fixing GD-04/05/07/08/09/10/15/20 (not a side effect of the rehome, see §11). Re-implementing Bartizan's `Base_Damage_Reduction`/armor traits for the jetpack (WS7-D4, named and rejected). |
| **Deferred** | Generalizing `CitizensBlindScan`/`BartizanBlindScan` into one shared parameterized tool — worth doing once WS7-D1 needs the same check for civilians/cops, not before (S3). |

## 2. Target layout

### Gangland (new branch `0.9.2` off `0.9.1`, K6)

| Artifact | Change |
|---|---|
| `gangland-api` (`Settings.java`) | **+1 method**: `isBartizanAvailable()`, mirroring `isCitizensAvailable()` (`Settings.java:294`). **`GanglandApi.java:13` `VERSION` bumped `"1.0"` → `"1.1"`** (C5 — additive api, but the rule in the repo CLAUDE.md is explicit: "bump the minor when the host adds API a module may rely on"). These two are the **only** changes to `gangland-api`'s own Java sources this wave. |
| `gangland-infra/gangland-item` (`ItemKind.java:14-18`) | **+1 enum constant**: `JETPACK("jetpack")`. Re-exported transitively through `gangland-api`; the edited file itself lives in `gangland-item`. Confirmed **zero consumers** do an exhaustive `switch (ItemKind)` anywhere in the reactor (reviewer ran `graphify affected "ItemKind"` — only the three `*ItemSerializer.kind()` sites reference it) — old Risk 5 is **closed**, not carried forward. |
| `gangland-features/gangland-gadget` | New `jetpack/` domain classes + new `CarMeleeWeaponLookup`/`CarDamageState`/`CarWeaponDamageListener` (below). `module.yml`: `Plugins: [Bartizan]` removed, `Host_Api: 1.0` → `1.1`. `pom.xml`: `bartizan-api` **stays** at `provided` (B1 — it is still a real compile-time dependency for the car-damage path). |
| `gangland-features/gangland-civilians`, `cops-n-crooks` | **No file change in WS7.** Census classification recorded in §9 as `WS7-D1` inputs only. |
| Docs (new, per estimate correction §12) | Repo `CLAUDE.md`'s module table (currently lists gadget's `Plugins: [Bartizan]`) and `documentation/module-loader.md` (names the gadget↔Bartizan edge) both need a one-line update; Bartizan's own `documentation/bartizan-api.md` mentions the wearable-jetpack example and gets a pointer to its own migration note. |
| **Explicitly not touched, so no reviewer re-litigates it** | `plugin.yml` already lists Bartizan under `softdepend:` (`plugin.yml:10-15`) — no change. `gangland-build/pom.xml` already carries `gangland-gadget` in both the shade exclude (`:84`) and the module copy (`:133`) — no build-list edit this wave. |

### New/changed files in `gangland-gadget` (mirrors the proven Car pattern — `GadgetModuleConfig.java:120-137`, `GadgetFileConfig.java:38-45` are the templates)

```
gadget/jetpack/
  Jetpack.java                     NEW  — domain entity (mirrors car/Car.java:18-125); buildItem() stamps
                                          identity + fuel NBT (mirrors Car.java:86-110) — see B3 fix
  JetpackKey.java                  NEW  — NBT key enum (mirrors car/CarKey.java): JETPACK_ID("jetpack")
  config/JetpackAddon.java         NEW  — loads items/jetpacks.yml (mirrors car/config/CarAddon.java);
                                          registered as a FILE-phase @Bean in GadgetFileConfig, NOT
                                          GadgetModuleConfig (cross-plan fact #2)
  JetpackService.java              EDIT — drop Bartizan imports, Wearable param → ItemStack/Jetpack
  JetpackSession.java              EDIT — drop Bartizan import (line 6), Wearable field → Jetpack
  JetpackTask.java                 EDIT — drop Bartizan imports; isScoped() DELETED (S1), not rewritten
gadget/item/
  JetpackConverter.java            NEW  — mirrors item/CarConverter.java (extends ItemAttributes)
  JetpackItemSerializer.java       NEW  — mirrors item/CarItemSerializer.java (extract() only, per B3 fix)
  JetpackItemRefresher.java        NEW  — mirrors item/CarItemRefresher.java: rebuilds a delivered jetpack from
                                          its template (full fuel, fresh lore) on every shop/trader delivery (§0c)
  GadgetItemPredicates.java        EDIT — +JETPACK predicate (line 16 pattern)
gadget/listener/jetpack/
  JetpackEquipListener.java        EDIT — scheduleChestplateCheck gains the migration hook (§6) + permission
                                          check (cross-plan fact #1)
gadget/listener/car/
  CarDamageListener.java           EDIT — split (see §3); loses weapons()/resolveMeleeDamage entirely
  CarMeleeWeaponLookup.java        NEW  — STATIC helper (final class, private ctor, one static method
                                          resolveMeleeDamage(Player, double fallback)) holding the extracted
                                          Bartizan-typed melee lookup. NOT a @Bean, NOT a field, NOT a
                                          constructor parameter, NOT in any @AutowireTarget: the class is only
                                          linked when the static call inside the isBartizanAvailable() branch
                                          executes (B2 fix, wired per B5)
  CarDamageState.java              NEW  — shared holder: pendingRightClickInteract + recentWeaponExplosionDamage
                                          (C2 fix); the ONLY new @Bean in this package (pure Set<UUID> state)
  CarWeaponDamageListener.java     NEW  — the two Bartizan-event-typed handlers, @ListenerHandler(condition =
                                          "isBartizanAvailable") + @RequiredArgsConstructor + @AutowireTarget({
                                          CarDamageState.class, ...}) — auto-scanned, constructor-injected, NO
                                          @Bean method (B6; house rule feedback_listener_bean_conflict), same
                                          shape as CarDamageListener.java:54-56
gadget/config/
  GadgetModuleConfig.java          EDIT — isJetpackFuelSink() rewritten off Bartizan onto GadgetItemPredicates;
                                          +3 @Bean methods (JetpackConverter, JetpackItemSerializer,
                                          JetpackItemRefresher — §0c); +1 @Bean CarDamageState. Nothing else
                                          from listener/car/ is a bean (B5/B6)
  GadgetFileConfig.java            EDIT — +jetpackAddon() @Bean (cross-plan fact #2)
items/jetpacks.yml                NEW  — module jar resource (schema in §5)
commands.json                     EDIT — +jetpack_give entry (mirrors car_give at commands.json:10-13)
```

### Bartizan (new branch off current HEAD 0e9e456, 0.3.0 → **0.4.0**, K3)

Full jetpack entry confirmed by direct read (`bartizan-plugin/src/main/resources/items/wearables.yml:238-260`):
`Material: IRON_CHESTPLATE`, `Name: "&b&lJetpack"`, `Base_Damage_Reduction: 0.05`, `Leather_Color: ""`, 3 `Lore`
lines, `Traits: {REINFORCED: 1, LIGHTWEIGHT: 2}` (no `FUEL_EFFICIENT`, closing C1), `Extra_Tags:` with `fuel`,
`fuel_current`, `fuel_max`, `jetpack_fuel_consumption_rate`, `jetpack_ascend_power`, `jetpack_max_speed_y`,
`Sounds.Thrust.Default_Sound.{Sound,Volume,Pitch}` (+ Glide, by the same shape).

| File | Change |
|---|---|
| `wearables.yml:238-260` | Delete the entire `jetpack:` entry. |
| `wearables.yml:45` (nearby comment) | Delete the `FUEL_EFFICIENT` jetpack-specific comment (trait itself stays generic — C3 census §1). |
| `WearableAddon.java:114-127` (`legacyJetpackToExtraTags`) | Delete — `readExtraTags()` (`:415-430`) needs no parser change (it was already a fallback branch). |
| `WearableAddonLegacyJetpackTest.java` | Delete. |
| `documentation/migration.md:44-102` (§3.4) | Mark superseded; add new §3.5: jetpack left Bartizan entirely in Gangland 0.9.2 / Bartizan 0.4.0, **including the armour/trait loss (WS7-D4)** so server owners see it in one place. |
| `Wearable.java:42,44,119,122`, `WearableRefresher.java:13`, `WearableCommand.java:22` | **Untouched** — javadoc/comment examples only (C3 confirmed false positives). |
| `documentation/bartizan-api.md` | One-line pointer to the new migration.md §3.5 (estimate correction, §12). |
| `pom.xml` `<revision>` | `0.3.0` → `0.4.0`. |

Gangland's root pom `bartizan.version` moves `0.1.0` → `0.4.0` in G1. Install order: Bartizan ships first (G0),
then Gangland (G1-G5), since gadget's pom keeps the `bartizan-api` dependency (B1) and must resolve `0.4.0`.

## 3. Seams

| Cross-boundary call (today) | Mechanism | Default when absent | After WS7 |
|---|---|---|---|
| `GadgetModuleConfig.isJetpackFuelSink` (`:65-72`) | `ServicesManager` → `BartizanApi.wearables()` | `false` | **Deleted.** Replaced by `GadgetItemPredicates.JETPACK.test(stack)` — pure NBT check, no service lookup. |
| `JetpackService.wearables()` (`:44-48`) | same | `null` | **Deleted.** `JetpackService` reads `ItemStack`/`JetpackAddon` directly. |
| `JetpackTask.isScoped()` (`:250-262`) | `ServicesManager` → `BartizanApi.weapons()` | `false` | **Deleted (S1)**, not kept. WS7-D2 is resolved by deletion, not left as a live decision. Cost: cannot block jetpack thrust while the player holds a scoped Bartizan weapon — accepted as an acceptable feature loss for a soft dependency. |
| `JetpackTask.getEffectiveConsumptionRate` reading `jetpack.traitLevel("fuel_efficient")` (`:218`) | Bartizan `Wearable.traitLevel(String)` — a **definition-config** read (`Wearable.java:86,309-311`), not per-item NBT (C1 correction) | returns `0` on stock config (no `FUEL_EFFICIENT` trait shipped) | **Deleted (S2).** `getEffectiveConsumptionRate` collapses to `return extraInt(jetpack, "jetpack_fuel_consumption_rate", 0)`. No YAML field added; nothing is lost because nothing was ever set. |
| `CarDamageListener.onWeaponEntityDamage`/`onWeaponRaytraceImpact` (`:186-239`) | `@EventHandler` on Bartizan event **types** | today: unreachable (fail-fast at `module.yml`) | **Moved** to `CarWeaponDamageListener`, `@ListenerHandler(condition = "isBartizanAvailable")` — unchanged from the original plan, this part was already correct. |
| `CarDamageListener.onVehicleDamage`'s melee-weapon lookup (`:133,148` → `weapons()`/`resolveMeleeDamage`, `:277-297`) | today: unconditioned method body on an always-loaded class — **this was the B2 bug** | today: unreachable (same fail-fast) | **Extracted** into the **static** helper `CarMeleeWeaponLookup` (no Keystone annotation, no bean, no field, no constructor parameter anywhere — B5). `CarDamageListener.onVehicleDamage` calls it only inside `if (Settings.isBartizanAvailable()) { damage = CarMeleeWeaponLookup.resolveMeleeDamage(player, fallback); } else { damage = fallback; }` — a static call site is linked only when that instruction executes, so the class (and its Bartizan-typed method signatures) is never loaded/verified on a Bartizan-less server. `CarDamageListener` declares no field and no constructor parameter of that type and carries zero Bartizan-typed method signatures. |
| `CarDamageListener.onCarRightClick`/`onEntityDamage` (`:89-100,161-180`) — plain event params, no Bartizan body left after the extraction above | n/a | n/a | **Unchanged**, always safe to register. |
| Jetpack item enters core registries | `ItemConverterRegistry`/`ItemSerializerRegistry` direct bean injection (`GadgetModuleConfig.java:119-137`, same as Car) | n/a | **Reused unmodified**, zero new seam — gadget has direct bean access, unlike Bartizan which must publish via `ItemVocabulary`/`ServicesManager` (`BartizanItemVocabulary.java:50`). K4's "without a new seam" is satisfied by reuse. |
| Fuel operations | Direct method calls, `FuelService` constructor-injected | n/a | **Unchanged, verified zero-friction**: every `FuelService` wearable method (`FuelService.java:228-271`) takes `Player`/`ItemStack` only — no `Wearable` type anywhere in its signature. |
| Jetpack per-item permission | none today (a jetpack has no id-based gate) | n/a | **New**, mirrors Car exactly: `Jetpack.getPermission()` → `"gangland.jetpacks." + jetpackId`, registered at `JetpackAddon` load (mirrors `CarAddon.java:145`), enforced in `JetpackEquipListener`/`JetpackService.activate` (mirrors `CarInteractListener.java:59`). |

## 4. Steps

Gate order: Bartizan first (its `0.4.0` must exist before Gangland's pom points at it), then Gangland. One reactor
build at a time. Sonnet executors, Opus gate reviews, smoke rows on
`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`.

| Gate | Repo | Steps | Files | Size | Test | Commit boundary |
|---|---|---|---|---|---|---|
| **G0** | Bartizan | 1) New branch off `0e9e456`. 2) Delete `jetpack:` block + `FUEL_EFFICIENT` comment. 3) Delete `legacyJetpackToExtraTags` + its test. 4) Bump `<revision>` → `0.4.0`. 5) `migration.md` §3.5 + `bartizan-api.md` pointer. | `wearables.yml`, `WearableAddon.java`, `WearableAddonLegacyJetpackTest.java` (delete), `pom.xml`, `documentation/migration.md`, `documentation/bartizan-api.md` | S | Existing `WearableAddonTest`/generic wearable tests still green | `mvn clean install` green, commit, tag `0.4.0` |
| **G1** | Gangland | 1) Branch `0.9.2` off `0.9.1`. 2) `Settings.isBartizanAvailable()` + `GanglandApi.VERSION` → `1.1` (C5). 3) `ItemKind.JETPACK`. 4) Root pom `bartizan.version` → `0.4.0`. 5) gadget `module.yml` `Host_Api` → `1.1`. | `Settings.java`, `GanglandApi.java`, `ItemKind.java`, root `pom.xml`, gadget `module.yml` | S | `mvn clean install -DskipTests` compile check, whole reactor | `mvn clean install` green (whole reactor) |
| **G2** | Gangland | 1) `Jetpack`/`JetpackKey` domain classes (full field set — Material/Name/Lore/CustomModelData/fuel, per B3). 2) `JetpackAddon` loading `items/jetpacks.yml`, `Fuel_Key` **mandatory** at load (moots GD-12). 3) Register `JetpackAddon` as a FILE-phase `@Bean` in `GadgetFileConfig` (cross-plan fact #2, `GadgetFileConfig.java:38-45` template) — **do not** put it in `GadgetModuleConfig`. 4) Register `Jetpack.getPermission()` at load, mirroring `CarAddon.java:145`. | new `gadget/jetpack/{Jetpack,JetpackKey}.java`, `gadget/jetpack/config/JetpackAddon.java`, `GadgetFileConfig.java`, `items/jetpacks.yml` | M | `JetpackAddonLoadTest` (new, red-first against today's no-guard `JetpackAddon` — not a flip, C4) | `mvn clean install -pl gangland-features/gangland-gadget -am` green |
| **G3** | Gangland | 1) `JetpackConverter`/`JetpackItemSerializer`/`JetpackItemRefresher` (mirrors `CarItemRefresher`, §0c)/`GadgetItemPredicates.JETPACK`. 2) Wire the 3 new `@Bean`s into `GadgetModuleConfig`. 3) Rewrite `JetpackService`/`JetpackSession`/`JetpackTask` off `Wearable` onto `Jetpack`+`ItemStack`; **delete** `isScoped()` (S1) and the trait branch (S2); rewrite `isJetpackFuelSink`. | `item/Jetpack*.java` (new), `GadgetModuleConfig.java`, `jetpack/{JetpackService,JetpackSession,JetpackTask}.java` | L | `JetpackNbtIdentityTest` (new, mirrors `CarNbtIdentityTest`). Flip `JetpackTaskConsumptionRateTest` (mocks `Jetpack` instead of `Wearable`) | `mvn clean install -pl gangland-features/gangland-gadget -am` green |
| **G4** | Gangland | 1) Legacy migration hook in `JetpackService.scheduleChestplateCheck` (§6). 2) Permission check at equip (cross-plan fact #1). 3) `/glw jetpack give` command + `commands.json` entry. | `JetpackEquipListener.java`, `JetpackService.java`, new `command/JetpackGiveCommand.java` + parent wiring, `commands.json` | M | `JetpackLegacyMigrationTest` (new) — fuel level preserved, not reset, per §6 | `mvn clean install` green + smoke row "jetpack-give" |
| **G5** | Gangland | 1) Extract `CarMeleeWeaponLookup` out of `CarDamageListener` as a **static helper** called only inside the `isBartizanAvailable()` branch (B2, B5). 2) `CarDamageState` shared holder — the only new `@Bean` (C2). 3) `CarWeaponDamageListener` (`@ListenerHandler(condition = "isBartizanAvailable")`, auto-scanned with `@AutowireTarget`, **no** `@Bean` — B6). 4) Drop `module.yml Plugins: [Bartizan]`; **keep** `bartizan-api` pom dependency (B1). 5) New `gangland-gadget`-local `BartizanBlindScan` test-support (S3) + safety test. | `CarDamageListener.java`, new `CarMeleeWeaponLookup.java`/`CarDamageState.java`/`CarWeaponDamageListener.java`, `module.yml`, new `gangland-gadget/src/test/.../testsupport/BartizanBlindScan.java`, new gadget test | L | `GadgetBartizanBlindScanTest` (new) — gadget package loads clean under a Bartizan-blinded classloader | `mvn clean install` green + smoke rows below (must **exercise**, not just boot, per B2) |
| **G6 (new)** | Both | Docs sweep (estimate correction, §12): repo `CLAUDE.md` module table, `documentation/module-loader.md`, Bartizan's `documentation/bartizan-api.md` pointer. Docket rows updated (§11). `graphify update . --force` both repos. | `CLAUDE.md`, `documentation/module-loader.md` | S | full `mvn test` both reactors | tag, no push without the user |

Rollback: every gate is its own commit on `0.9.2`/`0.4.0`, which never touch `0.9.1`/`0.3.0`. G2→G3→G4 are a
dependency chain (each needs the previous gate's types); G5 is independent of G2-G4 (touches only
`CarDamageListener` and its new siblings) and could ship before or after the jetpack-rehome gates with no ordering
requirement between them.

## 5. Config, messages, permissions

| Key / constant | Today | After WS7 | Destination |
|---|---|---|---|
| Bartizan `wearables.yml:238-260` jetpack entry | `Material: IRON_CHESTPLATE`, `Name`, `Base_Damage_Reduction: 0.05`, `Leather_Color`, 3 `Lore` lines, `Traits: {REINFORCED: 1, LIGHTWEIGHT: 2}`, `Extra_Tags: {fuel, fuel_current, fuel_max, jetpack_fuel_consumption_rate, jetpack_ascend_power, jetpack_max_speed_y, Sounds}` | Gangland-owned `items/jetpacks.yml`, block-style, `Capitalized_Underscore_Separated`: **`Material`, `Name`, `Lore` (list), `Custom_Model_Data`, `Fuel_Key` (mandatory), `Max_Fuel`, `Ascend_Power`, `Max_Speed_Y`, `Fuel_Consumption_Rate`, `Sounds:` (nested, unchanged shape)`.** `Base_Damage_Reduction`/`Traits`/`Leather_Color` are **dropped** (WS7-D4) — the item becomes a plain fuel/thrust chestplate, no armor-trait system ported. | `gangland-features/gangland-gadget/src/main/resources/items/jetpacks.yml` |
| `fuel_efficient` trait read | Definition-config only, never fires on stock config (C1) | **Deleted (S2)**, not ported. No YAML field. | n/a |
| No jetpack give command today | — | New `jetpack:<id>` string for loot/sign/shop references (via `ItemConverterRegistry`) + `/glw jetpack give <id> <amount>` | `JetpackConverter.java`, new `JetpackGiveCommand.java` |
| `commands.json` (gadget) | Ships 5 entries: `car`, `car_help`, `car_give`, `car_info`, `car_list` (`commands.json:1-22`) — corrected from the earlier "leaf-only" description (C7) | +`jetpack_give` and whatever minimal parent node (`jetpack`) the command framework requires for a single leaf — no `jetpack_info`/`jetpack_list` (ponytail; `JetpackAddon` parity command deferred). Remember the `var list` help filter picks these up automatically once registered. | same `commands.json` |
| New user-facing strings (`jetpack given`/`invalid jetpack` equivalents of `CAR_GAVE`/`CAR_INVALID`) | Car's are legacy `Messages` enum entries (pre-split precedent) | **Decided, not left open** (estimate correction): module-owned YAML in the gadget module's own resources, per the api contract rule ("a module that needs a new user-facing string puts it in its own YAML, not in `Messages`/`Settings`") — a small `JetpackMessages` constants class reading from a gadget-owned `messages.yml`-style file, not a `gangland-api` `Messages` addition. | new module-owned messages source, gadget module |
| `Settings.isBartizanAvailable()` | does not exist | new static method, same shape as `isCitizensAvailable()`, backed by `Bukkit.getPluginManager().isPluginEnabled("Bartizan")`. Requires `GanglandApi.VERSION` 1.0→1.1 (C5). | `Settings.java`, `GanglandApi.java` |
| Permission | `gangland.cars.<carId>`, per-item, registered `CarAddon.java:145`, enforced `CarInteractListener.java:59` | `gangland.jetpacks.<jetpackId>` — **same pattern, confirmed**, not invented (cross-plan fact #1, closes old §13 item) | `Jetpack.getPermission()`, registered at `JetpackAddon` load, enforced at equip |
| `module.yml` `Plugins: [Bartizan]` (gadget) | fail-fast | **removed**; `Host_Api: 1.0` → `1.1` | `gangland-gadget/src/main/resources/module.yml` |

## 6. Persistence

No new tables — unchanged from the original plan. The jetpack is a YAML-defined catalogue entry stamped onto an
`ItemStack`'s NBT, same as the car. `JetpackService`'s only state is the in-memory `activeSessions` map, ephemeral,
rebuilt on `onInitialize`/`refreshSessions()`.

**Migration story (corrected per B3):**

| | Today | After G4 |
|---|---|---|
| Identity tag | Bartizan's generic `Wearable.NBT_KEY = "wearable"` (`Wearable.java:55`) | Gangland-owned `JetpackKey.JETPACK_ID` (mirrors `CarKey.CAR_ID`) |
| "Is this a jetpack" test | `wearable.extraTags().containsKey("fuel")` | `GadgetItemPredicates.JETPACK.test(stack)` — direct NBT tag check |
| Fuel tags | `FuelKey.FUEL_ID`/`FUEL_CURRENT`/`FUEL_MAX` stamped by Bartizan's generic `extraTags` pass at build time (`Wearable.java:355-375` reading `Extra_Tags: {fuel, fuel_current, fuel_max}`, `WearableAddon.java:118-120`) — **already Gangland's own tag constants**, only the stamping code was Bartizan's | `Jetpack.buildItem()` (not `JetpackItemSerializer` — B3 correction) stamps `JetpackKey.JETPACK_ID` + `FuelKey.FUEL_ID/CURRENT/MAX` directly, mirroring `Car.buildItem()`'s `:103-107` fuel block exactly |
| Session identity check | `JetpackTask.isWearingJetpack` compares `Wearable.getWearableKey(chestplate)` to the session's jetpack key (`JetpackTask.java:246-247`) | Rewritten to compare `JetpackKey.JETPACK_ID`'s stored value instead — **this comparison must move in the same G3 step as everything else**, or a migrated item fails the session check (a gap the review's "missing consumers" table flagged and this plan now states explicitly) |
| Migration trigger | — | `JetpackService.scheduleChestplateCheck` gains one branch: chestplate has the **old** Bartizan `NBT_KEY="wearable"` tag (checked via a raw `ItemBuilder(stack).hasNBTTag("wearable")` string check — no Bartizan import needed for this) + `FuelKey.FUEL_ID` present + no `JetpackKey.JETPACK_ID` yet → stamp `JetpackKey.JETPACK_ID` in place, preserving every existing tag. Deliberately **not** the `ItemRefresherRegistry` path (deferred, S4) — that path resets fuel/durability to factory defaults, which would be wrong for a live migration. |
| Unequipped old stock (chest/shop) | — | Left as-is until first equip; no bulk migration job (ponytail, flagged §13). |

## 7. Tests

| Test | Type | Asserts |
|---|---|---|
| `JetpackAddonLoadTest` (new, **red-first**, not a flip — C4) | unit | Missing/blank `Fuel_Key` → entry skipped + logged, not loaded (fixes GD-12 for free) |
| `JetpackNbtIdentityTest` (new, mirrors `CarNbtIdentityTest`) | unit | `JetpackKey.JETPACK_ID` and fuel tags round-trip through `Jetpack.buildItem()`; `GadgetItemPredicates.CAR`/`JETPACK` mutually exclusive |
| `JetpackTaskConsumptionRateTest` (existing, **flip**) | unit | Currently mocks Bartizan `Wearable` — rewritten to mock `Jetpack` |
| `JetpackLegacyMigrationTest` (new) | unit | Old-format chestplate gets `JetpackKey.JETPACK_ID` added, fuel level **unchanged**, and the rewritten session-identity check (§6) recognizes it |
| `GadgetBartizanBlindScanTest` (new, gadget-local `BartizanBlindScan`, S3) | unit | `gangland.gadget` package loads/scans clean under a Bartizan-blinded classloader — proves `CarDamageListener` carries zero Bartizan-typed method signatures after the B2 extraction |
| `CarMeleeWeaponLookupGuardTest` (new — closes B2 for real, not just at scan time) | unit | With `Settings.isBartizanAvailable()` stubbed `false`, `CarDamageListener.onVehicleDamage` applies exactly the vanilla punch fallback damage (B7 wording: the observable is the damage value, not "non-invocation" — a static helper has no instance to verify against) |
| `WearableAddonLegacyJetpackTest` (Bartizan) | delete | Tested the deleted method |
| `CarNbtIdentityTest` (existing) | untouched | Still pins GD-10 |
| Smoke rows (console harness, **exercise not boot**, per B2) | manual/console | **"jetpack-give"** (`/glw jetpack give`, equip, fly, permission enforced). **"gadget-no-bartizan-exercise"** (Bartizan jar removed: gadget module still loads AND a player successfully punches a parked car for vanilla damage AND a jetpack successfully flies — not merely "server boots"). **"car-damage-with-bartizan"** (Bartizan present, fire a weapon at a car, confirm `CarWeaponDamageListener` fires). **"jetpack-migration"** (pre-0.9.2 NBT fixture, equip, fuel preserved). |

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **(was B2)** Any future edit re-adds a Bartizan-typed method signature to `CarDamageListener`/`JetpackTask`/`JetpackService` without noticing the always-loaded-class hazard | `GadgetBartizanBlindScanTest` (§7) runs on every build and fails loudly the moment that happens — this is the regression guard, not a one-time fix |
| 2 | **(was B4, now WS7-D4)** Jetpack permanently loses `Base_Damage_Reduction: 0.05` + `REINFORCED 1`/`LIGHTWEIGHT 2` on every server, Bartizan present or not, since it is no longer a Bartizan-registered wearable | Ponytail default: accept and document in the Bartizan 0.4.0 migration note (§9 WS7-D4); re-implementing damage reduction in gadget is named and rejected |
| 3 | Legacy migration (§6) never fires for jetpacks that are never re-equipped, silently leaving old-tagged stock in loot/shops or shop/lootchest DB rows referencing `wearable:jetpack` | Documented gap, not fixed (§13); the executor should `SELECT` the shop/lootchest tables on the test server before G0 per the reviewer's "could not verify" item, and the migration note should tell owners to re-add the jetpack as `jetpack:<id>` |
| 4 | **(was Risk 4, corrected — C2)** `CarDamageListener` split has **two** sets of shared mutable state crossing the boundary, not one: `recentWeaponExplosionDamage` (3 sites — `:128` stays, `:176` stays, `:196-198` moves) and `pendingRightClickInteract` (3 sites — `:96/:99` stays, `:136` stays, `:233` moves) | Both move into the new `CarDamageState` holder (§2/§4), constructed once and injected into both `CarDamageListener` and `CarWeaponDamageListener` — not duplicated, not left half-shared |
| 5 | The token race GD-27 (`triage/gadgets-cars-fuel-jetpack.txt:27`) is **worsened**, not fixed, by splitting `pendingRightClickInteract`'s readers across two classes | `CarDamageState` centralizes read/consume in one place (its own method, not a raw field access from either listener) so the split does not make GD-27 structurally worse than today — flagged in §11, not silently absorbed |

## 9. Decisions for the user

### WS7-D1 (K7 — the required one): civilians/cops-n-crooks Bartizan dependency, soft or hard?

From C2 §1/§4: 7 sites are WEAPON-INHERENT, 4 REPLACEABLE. `BartizanNpcWeapons` already has working null-safe
fallback code that is dead today only because `module.yml Plugins: [Bartizan]` fail-fasts first.

| Option | What changes | Cost |
|---|---|---|
| **A — stay hard** | No change | Zero — leaves the coupling in place for two more modules |
| **B — go soft for both** | Drop `Plugins: [Bartizan]` from both; condition all 7 WEAPON-INHERENT sites | Real feature loss (no NPC ranged weapons, no friendly-fire/raytrace detainment) when Bartizan is absent, not a crash — **but B2's exact body-level linkage problem applies to these 7 sites too** and must be re-audited the same way, not assumed safe |
| **C — soft for civilians, hard for cops-n-crooks** (reviewer's addition, adopted here) | Civilians goes soft (it already has written fallbacks at `BartizanNpcWeapons:35-36,54-55`); cops-n-crooks stays hard (its three `WeaponRaytraceImpactEvent` listeners are the deepest weapon coupling) | Unblocks turf (`Depends: [civilians]` only) without touching cops' deepest coupling; smaller diff than B |

**Recommendation**: C, staged as its own follow-up gate, reusing (and likely generalizing, per S3) the
`BartizanBlindScan` this plan adds for gadget.

### WS7-D4 (new, replaces the old WS7-D2/D3 slots): accept the jetpack's armour/trait loss?

| Option | What changes |
|---|---|
| **A — accept the loss** (ponytail default, recommended) | Jetpack ships as a plain fuel/thrust chestplate with vanilla iron-chestplate protection only; `Base_Damage_Reduction 0.05` and `REINFORCED 1`/`LIGHTWEIGHT 2` are gone, documented in the Bartizan 0.4.0 migration note |
| **B — re-implement damage reduction/traits in gadget** | Named and **rejected** as over-engineering for this wave — it would mean porting a chunk of Bartizan's armor-trait system into a module that otherwise has none, for a single item |

(WS7-D2 "does `isScoped` count" and WS7-D3 "keep `fuel_efficient`" are **retired**, not carried forward — resolved
by deletion, S1/S2, per the review.)

## 10. WS6 asks / Oriel asks / Keystone asks

None. `Settings.isBartizanAvailable()` needs no Keystone change: `Settings.java:287-292` already documents that
Keystone's guard falls back to Bukkit's unguarded registration (which throws the same way), and that skipping
construction via `condition=` is the fix that does not touch Keystone — this is the correct citation (C6), not a
`ReflectionGuard` claim. No Oriel involvement (jetpack has no GUI). Predates the decoupling wave's WS6 (K6: WS7
runs before that wave's G0).

**Shared-edit surface with WS8** (both plans touch the gadget module in the same window): `GadgetItemPredicates`,
`GadgetModuleConfig`, `commands.json`, `ItemKind`, `GadgetFileConfig`. `GadgetType` is confirmed **not** a
shared-edit risk (zero reactor-wide usages, cross-plan fact #3).

## 11. Docket

| Id | Priority | Rehome outcome |
|---|---|---|
| GD-04 (empty jetpack grants permanent fall immunity) | P1 | **Stays open** — unrelated logic bug |
| GD-05 (zero-fuel jetpack still glides/steers) | P1 | **Stays open** — unrelated logic bug |
| GD-07 (wearable permission only checked on inventory clicks) | P1 | **Stays open** — a different permission (generic equip-validation gap for all wearables), not the new per-item `gangland.jetpacks.<id>` gate this plan adds |
| GD-08 (input packet class missing on 1.16) | P2 | **Stays open** — packet layer untouched |
| GD-09 (refuelling a stack of cans fuels all N) | P1 | **Stays open** — `FuelService` reused unmodified |
| GD-10 (car items carry FUEL_ID and pass as fuel cans) | P2 | **Stays open** — car-specific, `CarNbtIdentityTest` pins it, untouched |
| GD-12 (jetpack without Fuel_Key starts a session that never flies) | P2 | **Fixed for free** — G2 makes `Fuel_Key` mandatory at load. `JetpackAddonLoadTest` is a **new, red-first test** (C4 — GD-12's triage row has no pinning test, `~~-`; do not describe this as a flip) |
| GD-15 (setAllowFlight(false) unconditional: creative, downed) | P2 | **Stays open** — untouched logic |
| GD-20 (Car.maxHealth and glideDescentRate are dead config) | P3 | **Half-moot, half-open** — jetpack half (`jetpack_glide_descent_rate`) moot by omission from the new YAML; car half (`Car.maxHealth`, display-only, never applied to damage capacity) stays open, untouched |
| **GD-27 (pendingRightClickInteract token race)** — **added, was missing (C3)** | P3 | **Worsened by the `CarDamageListener` split unless mitigated** — the split moves one of three readers into `CarWeaponDamageListener`; `CarDamageState` (§4/§8) centralizes read/consume so the split does not make the underlying race structurally worse, but does not fix it either. Fix direction unchanged: "consume the token in one place" — `CarDamageState` is that one place going forward, but the actual race (two events racing on the same tick) is not closed by this wave. |

**No new bug filed.** The originally-planned "GD-29" (`fuel_efficient` bonus has no Bartizan-absent story) is
**retracted** — per C1, the trait read is definition-config, always 0 on stock config, and cannot lose player data;
it is the same finding as the now-retired WS7-D3, not a distinct bug. The genuinely new finding from this pass is
**B2 itself** (unconditioned method bodies naming Bartizan types throw once the module goes soft) — that is not a
docket-worthy *bug* in shipped behavior (gadget is still hard-dependent today, so it cannot manifest yet); it is
a correctness requirement on *this wave's own deliverable*, tracked via Risk 1 and `GadgetBartizanBlindScanTest`,
not a triage row.

## 12. Estimate

| Gate | Steps | S/M/L | Executor-days |
|---|---|---|---|
| G0 (Bartizan) | 5 | S | 0.5 |
| G1 | 5 | S | 0.75 |
| G2 | 4 | M | 2.0 |
| G3 | 3 | L | 3.0 (+0.5 for `JetpackItemRefresher`, §0c) |
| G4 | 3 | M | 1.5 |
| G5 | 5 | L | 3.0 |
| G6 (docs sweep + docket + graph, new) | 3 | S | 0.75 |
| **Total** | 28 | 3S / 2M / 2L (per-gate size, 7 gates) | **~11.5 executor-days** |

Gate-count check: 3 S-gates (G0, G1, G6) + 2 M-gates (G2, G4) + 2 L-gates (G3, G5) = 7 rows, matching the table
above — this is the arithmetic the reviewer's estimate check flagged as broken in the previous version (24 steps
claimed against an 18-step S/M/L sum that didn't correspond to any row count).

Corrected per the review's estimate check: the old total (~8.5 days, 24 steps against an 18-step S/M/L sum) did not
cost B2's real work (auditing every Bartizan-touching body, extracting `CarMeleeWeaponLookup`, writing an
*exercise* smoke test rather than a boot check — costed into G5, now L), B3's expanded YAML surface (costed into
G2/G3), or the documentation sweep (new G6). The `Messages` question in §5 is now a decision, not an open
archaeology item, removing that cost. Wall-clock with one reactor build at a time, Sonnet executors + Opus gate
reviews: **≈ 2.5 studio-weeks**.

## 13. Not verified

- **Live-server persisted data.** Whether any shop row, loot-chest entry or barter/sell category stores the
  serialized id `WEARABLE:jetpack` (the format `SellCategory.java:41-46` documents as `"CAR:pickup_truck"`).
  Nothing in the repo's YAML references it (grep for "jetpack" across all `gangland-*/src/**/*.yml|json|md`
  returns zero), but a DB row would silently stop resolving once G0 deletes the catalogue entry. Run a `SELECT`
  against the shop/lootchest tables on the test server before G0; the migration note should tell owners to
  re-add the jetpack to shops as `jetpack:<id>`.
- Whether any server operator hand-edited `wearables.yml` to give the jetpack a non-zero `FUEL_EFFICIENT` trait
  (stock config has none, C1). If so the loss is config-portable, never NBT-carried — moot either way since the
  mechanic itself is deleted (S2), not migrated.
- Whether `items/jetpacks.yml`'s `Sounds:` nested-map parsing needs a new loader or can reuse whatever
  `CarAddon`/Keystone YAML-mapping helper already exists — `JetpackTask.soundTag()` currently consumes a
  `Map<String,Object>` Bartizan's own parser produced; the executor must confirm Keystone's YAML wrapper
  round-trips the same nested-map shape without extra glue code.
- Whether a bulk "re-stamp every jetpack in every online player's inventory + every chest" migration command is
  wanted — §6 deliberately scopes the migration to equip-time only; confirm this is acceptable before G4.
- Runtime behaviour of `@ListenerHandler(condition = "isBartizanAvailable")` and the `isBartizanAvailable()` guard
  in `CarDamageListener` — both resolution paths are confirmed statically, not executed; G5's exercise smoke rows
  (§7) are the actual verification, and should run before this plan is considered proven, not just reviewed.
