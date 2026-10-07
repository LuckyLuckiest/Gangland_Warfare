# REVIEW WS7 — Gadget ownership + Bartizan soft-coupling

Verdict: **PASS WITH FIXES** (B1–B4 must land as a §0b fix pass before any executor starts; the gate skeleton,
ordering and rollback story survive unchanged)

Verification: every `file:line` the plan cites was opened. `graphify affected` run in Gangland on `JetpackService`,
`JetpackSession`, `JetpackTask`, `CarDamageListener`, `GadgetItemPredicates`, `ItemKind`, and in Bartizan on
`WearableAddon`. Note a graph blind spot: `affected "CarDamageListener"` and `affected "GadgetItemPredicates"` both
return **"No affected nodes found"** even though `GadgetItemPredicates.CAR` is referenced at
`GadgetModuleConfig.java:129` — static-field references are not edges, so grep was used to close that gap. The
plan's own claim that its file list is complete **checks out**: the five `listener/jetpack/*` classes and
`jetpack/packet/JetpackInputInterceptor` carry **zero** `org.luckyraven.bartizan` imports (grep over the whole
gadget module returns exactly the 6 main + 1 test files the census lists).

## Blockers (must fix before executors start)

- **B1. §2 and G5 delete the `bartizan-api` dependency from `gangland-gadget/pom.xml`, but the plan simultaneously
  keeps three Bartizan compile-time usages. The module will not compile.** §2: *"`pom.xml` drops the `bartizan-api`
  `provided` dependency entirely"*; G5 repeats it. Yet §3 keeps `JetpackTask.isScoped()` (`JetpackTask.java:250-262`,
  `BartizanApi`/`WeaponCatalog`/`Weapon`/`ScopeData`), keeps `CarDamageListener.resolveMeleeDamage`/`weapons()`
  (`CarDamageListener.java:277-297`, `WeaponCatalog`/`Weapon`/`MeleeWeapon`) **and** creates
  `CarWeaponDamageListener` with two Bartizan event types. The dependency must stay at `provided`; only
  `module.yml`'s `Plugins: [Bartizan]` goes. Fix the §2 row and the G5 step text.

- **B2. The plan's central claim — "gadget can go soft on Bartizan by splitting one listener" — is false as written:
  three surviving method *bodies* touch Bartizan types and will throw `NoClassDefFoundError` at runtime on a
  Bartizan-less server.** §3 says the plain-event handlers "continue to degrade gracefully" and Risk 1 says
  `isScoped` is "already null-safe". Both are only true *today*, where `Plugins: [Bartizan]` fail-fasts and the code
  never runs. Concretely, once `Plugins:` is dropped:
  - `CarDamageListener.onVehicleDamage` declares a Bartizan-typed local at **`:133`** (`WeaponCatalog weapons =
    weapons();`) and `:148` calls `resolveMeleeDamage`, which enters `weapons()` (`:294-296`,
    `Bukkit.getServicesManager().getRegistration(BartizanApi.class)`). `rsp == null` is never reached — resolving the
    `BartizanApi.class` constant throws first. Every car hit on a Bartizan-less server throws.
  - `JetpackTask.isScoped(player)` is called from `:75` on every tick the player holds space. Same failure, per tick.
  - Keystone's `ReflectionGuard` does not help: it guards signature-level scanning, not method bodies (this is
    exactly docket **KS-MO-07**, which the wave README already names).
  **Required change:** every Bartizan-touching body must be either (a) moved into the conditional
  `CarWeaponDamageListener` / a separate `BartizanWeapons` helper class whose *first* call is guarded by
  `Settings.isBartizanAvailable()` in the caller (so the helper class is never loaded without Bartizan), or (b)
  deleted. For `isScoped` the lazy answer is deletion — see the D2 row below. Add a smoke assertion that damages a
  car **and** flies a jetpack with the Bartizan jar removed, not just a boot check.

- **B3. The new `items/jetpacks.yml` schema in §5 is missing the item's entire visual/armour surface, and nothing in
  the plan stamps the fuel NBT — a jetpack built by `/glw jetpack give` would be an unnamed iron chestplate with no
  fuel.** The real entry (`Bartizan .../items/wearables.yml:238-260`) carries `Material: IRON_CHESTPLATE`,
  `Name: "&b&lJetpack"`, three `Lore` lines, `Base_Damage_Reduction: 0.05`, `Leather_Color`, and
  `Traits: REINFORCED 1 / LIGHTWEIGHT 2`. §5's key list has none of them. Separately, the fuel tags on a jetpack
  today are stamped by Bartizan's generic `extraTags` pass (`Wearable.java:355-375`) from the `Extra_Tags` keys
  `fuel`/`fuel_current`/`fuel_max` (`WearableAddon.java:118-120`), which happen to be exactly
  `FuelKey.FUEL_ID/FUEL_CURRENT/FUEL_MAX` (`FuelKey.java:13-15`). `JetpackItemSerializer` must stamp those three
  tags itself or `FuelService.hasFuelOnWearable` (`FuelService.java:228-232`, reads `Fuel.readFuelCurrent(stack)`)
  returns false and the jetpack never flies. State both explicitly in §5 and in G3's test list.

- **B4. The armour consequence of leaving Bartizan is never mentioned and is a user-facing gameplay change.** After
  G0 deletes the `jetpack:` entry, a jetpack chestplate stops being a registered wearable; Bartizan's
  `resolveWearable` falls back to the temporary-vanilla-armour path (`Wearable.java:36-38,183`), so the item keeps
  vanilla iron-chestplate protection but loses `Base_Damage_Reduction: 0.05` and the `REINFORCED: 1` bonus against
  Bartizan weapons. That is a real nerf the plan silently accepts. It must be a decision (`WS7-D4`), with the ponytail
  default = accept the loss and say so in the migration note; the alternative (re-implementing damage reduction in
  gadget) is over-engineering and should be named as rejected, not omitted.

## Corrections (fix in place)

- **C1. The `fuel_efficient` trait analysis is wrong in both places it appears, and the "new bug" derived from it is
  not a bug.** `traitLevel(key)` reads the wearable **definition's** `Map<String,Integer> traits` field
  (`Wearable.java:86,309-311`) — it is *config*, parsed from the `Traits:` block, not per-item NBT. The `wt_` NBT
  prefix (`Wearable.java:56`) is written at build (`:361`) and **never read back** (grep: 2 hits total, one
  declaration, one write). Therefore: Risk 3's *"a server that already sold jetpacks with a Bartizan trait level
  stamped in NBT loses that bonus on migration"* is false — nothing player-owned is lost; porting the number to
  `Fuel_Efficient_Level` in YAML is loss-free. And the shipped jetpack entry carries **no `FUEL_EFFICIENT` trait at
  all** (`wearables.yml:246-248` = `REINFORCED: 1`, `LIGHTWEIGHT: 2`), so `traitLevel("fuel_efficient")` returns 0
  today on stock config. Consequence: **the "new untriaged bug GD-29" in §11 is not real and must not be filed.** Its
  claim ("will silently start returning 0 the moment Bartizan is uninstalled today, before this wave") cannot happen —
  with `Plugins: [Bartizan]` the module is rejected outright (`ModuleResolution.java:92-106`, C2 §4), so there is no
  Bartizan-less runtime in which that line executes. To the reviewer's question: it is **the same finding as WS7-D3,
  described twice, and both framings rest on the same wrong fact**. The genuinely new, file-worthy bug from this pass
  is B2 (soft-Bartizan `NoClassDefFoundError` in surviving method bodies) — file that instead.

- **C2. Risk 4 mis-states which fields are shared, understating the split.** `recentWeaponExplosionDamage` is touched
  by **three** handlers, not two: `:128` (`onVehicleDamage`, which *stays*), `:176` (`onEntityDamage`, stays), `:196-198`
  (`onWeaponEntityDamage`, moves). `pendingRightClickInteract` likewise has three sites: `:96/:99` (write),
  `:136` (`onVehicleDamage` read — the plan only cites `:233`). So **both** sets cross the split and both need the
  shared holder, not just one.

- **C3. §11 omits GD-27, the docket row the split directly aggravates.**
  `triage/gadgets-cars-fuel-jetpack.txt:27` = *"pendingRightClickInteract token race — Consume the token in one
  place."* Splitting the token's readers across two classes is the opposite of that fix direction. Add the row with
  an explicit "worsened / must be consumed in one place by the holder" note.

- **C4. GD-12 is not "test-pinned", so `JetpackAddonLoadTest` is a new red-first test, not a flip.**
  `triage/gadgets-cars-fuel-jetpack.txt:12` ends in `~~-` (no pinning test); the only pinned gadget row is
  GD-10 (`~~CarNbtIdentityTest (pins; flip when fixed)`), which the plan correctly leaves untouched. Reword G2/§7 to
  "new test, proven red against pre-fix `JetpackAddon`". `JetpackTaskConsumptionRateTest` is a genuine flip
  (`:7` imports the Bartizan `Wearable`) — that one is stated correctly.

- **C5. Adding `Settings.isBartizanAvailable()` requires a `GanglandApi.VERSION` minor bump, which the plan denies.**
  `GanglandApi.java:9-13` (`VERSION = "1.0"`) states the rule the repo CLAUDE.md repeats: *"Bump the minor when the
  host adds API a module may rely on."* Gadget's `module.yml` `Host_Api: 1.0` would then become `1.1`. Failure mode
  without the bump is silent, not loud: `ListenerManager.invokeMethod` (`gangland-impl/.../ListenerManager.java:18-26`)
  resolves the condition via `Settings.getSetting(...)` and returns **false** when the method is absent — a 1.1-era
  gadget jar on a 1.0 host would simply never register `CarWeaponDamageListener`. Bump both; add the G1 step.

- **C6. §10's justification is wrong even though the conclusion is right.** `ReflectionGuard` is not what protects a
  foreign-typed listener; `Settings.java:287-292` documents that Keystone's guard "catches the first attempt and falls
  back to Bukkit's un-guarded registration, which throws the same way", and that skipping construction via
  `condition=` "is the only fix that does not touch Keystone". Keep "no Keystone change needed", drop the
  ReflectionGuard reasoning.

- **C7. Small factual/presentation slips.** §5 calls `/glw car give` the "leaf-only shape" — `commands.json:1-22`
  actually ships `car`, `car_help`, `car_give`, `car_info`, `car_list`; state that the jetpack deliberately ships
  only `jetpack_give` plus whatever parent node the command framework requires (and remember the `var list` help
  filter, which §5 never mentions). §2's "Deleted: nothing on the Gangland side" contradicts §3, which deletes
  `isJetpackFuelSink` (`GadgetModuleConfig.java:65-72`) and `JetpackService.wearables()` (`:45-47`).

## Simplifications (ponytail)

- **S1. Delete `JetpackTask.isScoped()` instead of keeping it (flips WS7-D2).** The plan itself prices removal at
  "10 lines, no other ripple" (Risk 1). Keeping it costs: a K1 violation, a user decision that need not exist, and
  B2's per-tick crash risk. Deleting it satisfies K1 literally, removes a decision, and shrinks the diff. The only
  loss is "you cannot thrust while scoped" — a micro-interaction with a Bartizan weapon, which is precisely what a
  soft dependency is allowed to drop.
- **S2. Do not add `Fuel_Efficient_Level` to the YAML at all.** Per C1 the shipped jetpack has level 0 and the
  mechanic has never fired. Porting a dormant knob is a config knob for a constant. Delete the branch
  (`JetpackTask.java:215-227` collapses to `return extraInt(...)`), and note in the migration entry that a server that
  hand-added `FUEL_EFFICIENT` loses it. This removes WS7-D3 entirely.
- **S3. Drop `BartizanBlindScan` as a new test-support class if it only mirrors `CitizensBlindScan`'s constructor.**
  `gangland-core/src/test/.../testsupport/CitizensBlindScan.java` is already on gadget's test classpath — the pom
  already declares the `gangland-core` `test-jar` at test scope (`gangland-gadget/pom.xml:99-102`), so no pom change is
  needed (good), but check first whether the existing class takes the blinded package prefix as a parameter; if it
  does, reuse it and add nothing.
- **S4. `JetpackItemRefresher` may not be needed at G3.** The plan itself says (§6) the refresher must *not* be the
  migration path and is only for admin "factory reset". Unless a jetpack template field can change at runtime, that is
  a third registration for nothing — defer it to WS8 and register converter + serializer only. (`ItemRefresherRegistry`
  registration is one line to add later.)

## Missing consumers found by graphify affected / grep

| Type moved | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `CarDamageListener` split | `onVehicleDamage` reads `pendingRightClickInteract` | `CarDamageListener.java:136` | plan cites only `:233`; the holder must serve three sites |
| `CarDamageListener` split | `onVehicleDamage` removes `recentWeaponExplosionDamage` | `:128` | plan claims this set never crosses the split |
| `CarDamageListener` split | `onVehicleDamage`'s Bartizan-typed local + `resolveMeleeDamage` | `:133`, `:148`, `:277-297` | B2: unconditioned body throws without Bartizan |
| Jetpack identity (`Wearable.NBT_KEY`) | `JetpackTask.isWearingJetpack` compares `Wearable.getWearableKey(chestplate)` to the session key | `JetpackTask.java:246-247` | §6's migration table never says this comparison moves to `JetpackKey`; a migrated item would fail the session check |
| Jetpack wearable entry deletion (G0) | Bartizan armour-reduction path loses the jetpack's `Base_Damage_Reduction`/`Traits` | `wearables.yml:238-248`; `Wearable.java:183,426+` | B4 |
| Fuel NBT stamping | `Wearable.buildItem`'s generic extraTags stamp is what writes `fuel`/`fuel_current`/`fuel_max` | `Wearable.java:355-375`; `WearableAddon.java:118-120` | B3: new serializer must replace it |
| Docket | GD-27 token race | `triage/gadgets-cars-fuel-jetpack.txt:27` | C3 |
| `ItemKind.JETPACK` (Risk 5) | **no consumer** — no `switch` over `ItemKind` exists anywhere in the reactor; `affected "ItemKind"` returns only the three `*ItemSerializer.kind()` sites | `ItemKind.java:14-18` | Risk 5 can be closed, not carried to G1 |
| `wearable:jetpack` vocabulary strings | **none in-repo** — grep over all `gangland-*/src/**/*.yml|json|md` for "jetpack" returns zero | — | good news: no shipped config references it (DB rows still unverified, see below) |

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| WS7-D1 civilians/cops soft vs hard | B (soft), deferred to its own gate | **Agree it is a genuine fork, and neither option is a strawman** — A's cost column is honest, B's "real feature loss, not a crash" is accurate per C2 §1. **Add option C**: soft for civilians, hard for cops-n-crooks (cops' three `WeaponRaytraceImpactEvent` listeners are the deepest weapon coupling; civilians already has written fallbacks at `BartizanNpcWeapons:35-36,54-55`). C is the option that actually unblocks turf, since turf's only exposure is `Depends: [civilians]`. Also carry B2 into that gate: those 7 sites have the same body-level linkage problem. |
| WS7-D2 does `isScoped` count for K1 | keep it | **Disagree — delete it** (S1). B2 turns "keep" from a philosophical reading of K1 into a per-tick crash on the servers this wave exists to support. |
| WS7-D3 keep `fuel_efficient` | A (port as flat YAML field) | **Disagree — not a user decision at all.** Per C1 the level is definition config and is 0 in the shipped YAML; there is no balance to preserve and no data to lose. Delete the mechanic (S2) and retire the decision. |
| *(missing)* WS7-D4 armour/trait loss | — | **Must be added** (B4). This is the one genuine, user-visible trade-off the rehome forces, and the plan does not surface it. |

## Estimate check

**~8.5 executor-days is optimistic; ~11–12 is honest.** Internal inconsistency first: §12 claims 24 steps but the
S/M/L column sums to 18 (8S/8M/2L). Not costed anywhere:

- B2's real work — auditing every surviving Bartizan-touching body, moving them behind a load-deferring guard, and
  smoke-proving it (not a boot check, an *exercise* check). Add ≥1 day to G5.
- B3's YAML surface: `Material`/`Name`/`Lore`/`Custom_Model_Data`/`Leather_Color` parsing plus the fuel-tag stamp —
  G2/G3 are each half a day light.
- `documentation/` sweep: the repo CLAUDE.md module table lists gadget's `Plugins: [Bartizan]`, and
  `documentation/module-loader.md` names the gadget↔Bartizan edge. G-final says "memory/CLAUDE.md note" but not docs.
  Bartizan's own `documentation/bartizan-api.md` also mentions the wearable jetpack example.
- The `Messages` question in §5 is left as "whichever the executor finds matches" — an unmade decision costs an
  executor half a day of archaeology. Decide it in the plan: module-owned YAML, per the api contract.
- C5's `GanglandApi.VERSION` 1.0 → 1.1 + `Host_Api: 1.1` in gadget's `module.yml` (small, but currently absent).
- Correctly *not* needed, and the plan is right to omit them: `plugin.yml` already lists Bartizan under `softdepend`
  (`plugin.yml:10-15`), and `gangland-build/pom.xml` already carries gangland-gadget in both the shade exclude
  (`:84`) and the module copy (`:133`) — no build-list edit this wave. Say so explicitly so a reviewer does not
  re-litigate it.

## Things I could not verify

- **Live-server persisted data.** Whether any shop row, loot-chest entry or barter/sell category in a production
  database stores the serialized id `WEARABLE:jetpack` (the format `SellCategory.java:41-46` documents as
  `"CAR:pickup_truck"`). Nothing in the repo's YAML references it, but a DB row would silently stop resolving once
  G0 deletes the catalogue entry. The executor should run a `SELECT` against the shop/lootchest tables on the test
  server before G0, and the migration note should tell owners to re-add the jetpack to shops as `jetpack:<id>`.
- Whether any server operator has hand-edited `wearables.yml` to give the jetpack a non-zero `FUEL_EFFICIENT` trait
  (C1 shows stock config has none). If so the loss is still config-portable, never NBT — the migration note covers it.
- Runtime behaviour of `@ListenerHandler(condition = "isBartizanAvailable")` — the resolution path is confirmed
  statically (`ListenerManager.java:18-26` → `Settings.getSetting`), but not executed; the plan's §13 item stands.
- Nested `Sounds:` map round-tripping through Gangland's own YAML loader (plan §13) — not checked here; agreed
  as a real executor-time risk, since `JetpackTask.soundTag` (`:284-294`) currently consumes a `Map<String,Object>`
  produced by Bartizan's parser.
- No `mvn` build was run; all findings are static.

---

## Re-review (after fix pass)

Verdict: **PASS WITH FIXES** — B1–B4 and C1–C7 are genuinely fixed, S1–S4 adopted, all four WS8 cross-plan facts
folded in correctly. Two **new** blockers (B5, B6) were introduced *by the B2 fix itself*, both confined to two
lines of §2's file tree (`plan:116-117`). Neither needs a redesign — B2's extraction idea is right; only its
wiring is wrong.

### The planner's pushback on B3 — accepted, my finding was wrong

Verified directly. `Car.buildItem(Player)` (`car/Car.java:86-110`) does all the stamping, including the fuel block
at `:103-107` (`builder.addTag(FuelKey.FUEL_ID.getKey(), fuelKey)` / `FUEL_CURRENT` / `FUEL_MAX`, guarded by
`fuelEnabled && fuelKey != null && !fuelKey.isEmpty() && maxFuel > 0`). `CarItemSerializer` is 26 lines containing
exactly `kind()` and `extract()` — `new ItemBuilder(stack).getStringTagData(CarKey.CAR_ID.getKey())` at `:20-24` —
and never stamps anything. **`Jetpack.buildItem()` is the correct home; my B3 named the wrong class.** The
substance of B3 (the missing YAML surface and the unstamped fuel tags) stood, and §5/§6 now carry it correctly.
Bonus: `Car.buildItem`'s fuel guard is already the shape GD-12's fix needs for `Fuel_Key`.

### New blockers

- **B5. Registering `CarMeleeWeaponLookup` as a `@Bean` (`plan:116-117`) re-opens the exact crash B2 fixed.** §0b
  states the new class's "own method signatures still carry `WeaponCatalog`/`Weapon`/`MeleeWeapon`", and §2 then
  adds it to `GadgetModuleConfig` as a bean. Three separate paths then load it unconditionally on a Bartizan-less
  server: the config class's bean method has it as a **return type**; the bean method is **invoked** at CONFIG
  phase, constructing the instance; and `BeanFactory.runPostConstruct` calls `getDeclaredMethods()` on every bean's
  class — which resolves private method signatures too. That last path is not theory: it is this repo's own P0,
  `triage/new-findings.txt:12`, *"the first cops bean whose declared methods mention an AnvilGUI type fails to
  link"*, stack `Class.getDeclaredMethods0 … BeanFactory.runPostConstruct(BeanFactory.java:543) …
  GanglandContext.bootstrap`. Identical shape, different absent library. (And if Keystone 1.9.1's `ReflectionGuard`
  instead *skips* the bean with `reflection.type.missing`, the bean is then absent and whatever injects it fails —
  a cascade, not a save.) `CitizensBlindScan`'s own javadoc names the same `Class.getDeclaredMethods0` path at
  `:24`. **Required:** make `CarMeleeWeaponLookup` a **static** helper — no `@Bean`, no field, no constructor
  parameter, no `@AutowireTarget` entry — invoked as `CarMeleeWeaponLookup.resolveMeleeDamage(player, fallback)`
  *inside* the `if (Settings.isBartizanAvailable())` branch. A static call site is resolved only when that
  instruction executes, which is precisely the property B2 needs, and it is the only shape that survives the bean
  scan, the field-descriptor resolution and the constructor descriptor at once. State explicitly that
  `CarDamageListener` declares no field and no constructor parameter of that type.
- **B6. Registering `CarWeaponDamageListener` as a `@Bean` (`plan:116-117`) breaks a recorded house rule.**
  `feedback_listener_bean_conflict`: *"Never `@ListenerHandler` + `@Bean` the same class; events go to the
  auto-scanned copy and any `@Bean`-side setup silently dies."* The plan gives that class
  `@ListenerHandler(condition = "isBartizanAvailable")` **and** a bean method. The existing precedent in the same
  package shows the right shape: `CarDamageListener.java:54-56` is `@ListenerHandler` + `@RequiredArgsConstructor`
  + `@AutowireTarget({CarService.class, CarAccessPolicy.class, CarMessageContract.class})` — auto-scanned,
  constructor-injected, no bean. **Required:** drop the `CarWeaponDamageListener` bean; keep only `CarDamageState`
  as a `@Bean` (pure `Set<UUID>` state, no foreign types — correct as a bean) and reach it from both listeners via
  `@AutowireTarget({CarDamageState.class, …})`.
- **B7 (minor, follows from B5).** `CarMeleeWeaponLookupGuardTest` (§7) asserts the listener "falls back to vanilla
  punch damage **without invoking `CarMeleeWeaponLookup` at all**". With a static helper there is no instance to
  verify non-invocation against. Reword it to assert the returned damage equals the vanilla fallback when
  `isBartizanAvailable()` is false — the observable that actually matters.

### Verified fixed (spot-checked against source, not taken on trust)

| Item | Verdict |
|---|---|
| B1 — `bartizan-api` stays `provided` | **Fixed.** §2 (`plan:81`) and G5 step 4 both say "keep"; §1 Scope restates it. |
| B2 — extraction + call-site guard | **Idea correct, wiring wrong** (B5/B6). `CarDamageListener` carrying zero Bartizan-typed signatures is the right target state. |
| B3 — YAML surface + fuel stamping | **Fixed**, in the right class (see above). Schema now has `Material`/`Name`/`Lore`/`Custom_Model_Data`; `Base_Damage_Reduction`/`Traits`/`Leather_Color` explicitly dropped into WS7-D4. |
| B4 — WS7-D4 added | **Fixed.** Two options, A recommended, B named and rejected — a fair fork, not a strawman. |
| C1 — fuel_efficient / GD-29 | **Fixed.** `plan:288` — "No new bug filed"; WS7-D3 retired; trait branch deleted (S2). |
| C2 — shared-state counts | **Fixed.** §8 row 4 now cites `:128`/`:176`/`:196-198` and `:96/:99`/`:136`/`:233` — matches my reading exactly; `CarDamageState` holds both sets. |
| C3 — GD-27 | **Fixed** (`plan:286`), with the honest "centralizes but does not close the race" note. |
| C4 — GD-12 red-first | **Fixed** in §7 and §11 (`plan:283`). |
| C5 — `Host_Api` bump | **Fixed.** `GanglandApi.VERSION` 1.0→1.1 + gadget `module.yml` `Host_Api: 1.1`, both in G1. |
| C6 — ReflectionGuard justification | **Fixed** (`plan:263` cites `Settings.java:287-292`). |
| C7 — commands.json / "deleted nothing" | **Fixed**, incl. the `var list` help filter. |
| S1/S2 — `isScoped` + trait branch deleted | **Adopted.** This also removes the jetpack path from B5's blast radius entirely. |
| S3 — `BartizanBlindScan` | **Adopted, and the planner's counter-check is right:** `CitizensBlindScan.java:214` hardcodes `"net.citizensnpcs."` inside an anonymous `loadClass` override (219-line file), so it takes no prefix parameter and cannot be reused as-is. Scoping the sibling to gadget's own test tree is the narrower choice and is fine (gadget already has the `gangland-core` test-jar at `pom.xml:99-102`, so either location would have compiled). |
| S4 — refresher deferred | **Adopted** (converter + serializer only). |
| Cross-plan facts 1–4 | **All four verified.** Per-item permission (`Car.java:75-77` → `CarAddon.java:145` → `CarInteractListener.java:59`), `GadgetFileConfig.java:38-45` as the FILE-phase template with the explicit "do not put it in `GadgetModuleConfig`" warning, `GadgetType` confirmed unused, per-type give agreed with WS8. |
| Estimate | **Fixed and honest.** 7 gates, 3S/2M/2L = 7 rows (arithmetic now consistent), ~11 executor-days, new G6 for docs/docket/graph — lands inside the 11–12 I asked for. |

### Still open from the original review (unchanged, not regressions)

- Production DB rows holding `WEARABLE:jetpack` — §13 carries it with the right mitigation (`SELECT` before G0).
- `Sounds:` nested-map round-tripping through Gangland's own loader.
- Runtime behaviour of the `condition=` listener and the `isBartizanAvailable()` guard — now doubly worth the
  smoke row, since B5/B6 turn on exactly when classloading happens. The "exercise, not boot"
  smoke rows (§7) are the right instrument and were correctly added.

Once B5–B7 are applied (three edits, all in §2's tree, §3's seam row and §7's test row), this plan is **PASS**.


## Orchestrator closure (2026-09-16)

B5, B6 and B7 were applied in the plan exactly as prescribed above (static helper, auto-scanned conditional listener with no bean, damage-value assertion) and recorded in the plan's **§0c**. S4 was reversed there: `JetpackItemRefresher` is registered in G3 because the house rule `feedback_item_refresher_pattern` requires stateful items to refresh on every shop/trader delivery, and Bartizan's `WearableRefresher` is what provides that for jetpacks today. Verdict per the re-review's own condition: **PASS**. Estimate ~11.5 executor-days.
