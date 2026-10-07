# REVIEW WS8 — Gadget catalogue

Verdict: **PASS WITH FIXES** (B1–B4 before executors start; the grapple design itself is sound and the shortlist is
the best-argued section of either plan)

Context: WS8 was written before `plans/WS7-gadget-ownership.md` existed and flagged its WS7-dependent assumptions
honestly (§10, §13). This review resolves every one of them against WS7 **as amended by REVIEW-WS7's B1–B4**, which
is now the real direction. Good news first: **nothing WS8 cites is deleted or renamed by the WS7 fix pass.**
REVIEW-WS7's S1 ("delete `isScoped`") touches `JetpackTask` only; `JetpackFallDamageListener` is untouched by WS7
(its GD-04 row stays open), and `JetpackService.isActive(Player)` survives unchanged at `JetpackService.java:101`.
The collisions are elsewhere — shared files, one contradicted seam decision, and one command shape.

`graphify affected` run on `FuelService`, `JetpackFallDamageListener` (via `JetpackService`), `GadgetItemPredicates`,
`ItemKind`. Same graph blind spot as last time: static-field references are not edges, so `affected
"GadgetItemPredicates"` returns nothing despite `GadgetModuleConfig.java:129`; grep closed the gap.

## Blockers (must fix before executors start)

- **B1. §3a and G1 tell the executor to create a gadget-owned `ItemVocabulary` ("Extend that same gadget-owned
  `ItemVocabulary` with `gadget:grapple` … if WS7 has not landed one yet, WS8's G1 creates the minimal one",
  plan `:60`, `:120`). WS7 explicitly decided the opposite, and WS7 is right.** WS7 §3's last seam row: *"K4's
  'without a new seam' is satisfied by reuse, not by adding a `GadgetItemVocabulary`."* Verified: `ItemVocabulary`
  is the SPI by which an **external plugin** hands converters/serializers/refreshers to the host — Keystone's
  interface is two methods, `namespace()` + `contribute(registrar)`
  (`keystone-item/.../item/spi/ItemVocabulary.java:12-22`), and the host folds them in from the **`ServicesManager`**
  (`GanglandContext.java:204-224`, `getRegistrations(ItemVocabulary.class)`). A runtime module sits *inside* the bean
  graph and registers directly, which is exactly what the car does (`GadgetModuleConfig.java:120/127/134`) — there is
  **no** gadget `ItemVocabulary` today and none is needed. Delete the row and the G1 clause; a class with one
  implementation that duplicates a registration the module already performs is the textbook K5/ponytail violation.
  Knock-on: the `gadget:grapple` prefix idea dies with it. The resolution prefix **is** the `ItemKind` label
  (`ItemKind.java:14-18` → `"car:pickup_truck"`, the format `SellCategory.java:41` documents), so the choice is
  `grapple:<id>` (per-type, matching WS7's `jetpack:`) or one `gadget:` kind for everything — see C1.

- **B2. `ItemKind.GRAPPLE` is used in §3a but never appears in the target layout, and it does not live where §2 says
  everything lives.** §2 asserts *"No new Maven module. Everything lands inside the existing
  `gangland-features/gangland-gadget` reactor module"* (plan `:37`), yet §3a's registration row calls
  `ItemConverterRegistry.register(ItemKind.GRAPPLE, …)`. `ItemKind` is
  `gangland-infra/gangland-item/.../item/ItemKind.java:14-18` (`UNIQUE`, `CAR`, `MONEY`, `MATERIAL`) — a different
  reactor module, re-exported through `gangland-api` at compile scope. Add it to the file tree, note the reactor-wide
  rebuild, and coordinate with WS7 G1, which edits the same enum to add `JETPACK`. (Reassuring finding, shared with
  WS7: `graphify affected "ItemKind"` returns only the three `*ItemSerializer.kind()` sites and there is **no
  exhaustive `switch` over `ItemKind` anywhere in the reactor**, so adding constants is safe.)

- **B3. `items/grapple.yml` will never reach the data folder — the `GadgetFileConfig` edit is missing.** A module's
  YAML is copied out of the module jar by a `FileHandler` built with the **module classloader**;
  `GadgetFileConfig.java:41` is the single line that does it for cars
  (`fileManager.addFile(new FileHandler(plugin, "cars", "items", ".yml", moduleLoader.classLoader()), true)`), and
  the class javadoc at `:20` explains the FILE-phase hook that makes it work. §2's tree lists neither
  `config/GadgetFileConfig.java` nor the edit. Without it `GrappleAddon` loads an absent file and every gate after
  G1 silently has no grapple. **The same hole exists in WS7's §2 tree for `items/jetpacks.yml`** — the orchestrator
  should fix both in one pass.

- **B4. G3 reproduces an open P1 docket bug in new code, and §11's "No existing bug-docket ids are touched" is wrong
  because of it.** `JetpackFallDamageListener` (the class G3 mirrors) cancels `DamageCause.FALL` whenever
  `jetpackService.isActive(player)` — that is verbatim **GD-04** ("An empty jetpack grants permanent fall immunity",
  P1, `triage/gadgets-cars-fuel-jetpack.txt:4`), whose fix direction is *"Cancel fall damage only while hasFuel (and
  recently flying)"*. WS8 copies the shape **and widens it** with `Fall_Damage_Grace_Ticks: 40` after the pull ends
  (plan `:124`). Required: build the GD-04-corrected shape from the start — the grace window must be one-shot
  (consumed by the first FALL event) and bounded, not a rolling 2-second immunity that a player can chain by
  re-grappling inside the window. Add GD-04/GD-05 to §11 as "pattern constrained by", and say the new listener must
  not be written as a copy of the pre-fix jetpack file.

## Corrections (fix in place)

- **C1. Give-command shape collides head-on with WS7; WS7 wins on ponytail grounds.** WS8 §3b picks Option B
  (generic `/glw gadget give <id>` + a `GadgetItemRegistry`, plan `:70-77`); WS7 §5/G4 ships per-type
  `/glw jetpack give` with a `jetpack_give` row in `commands.json`, mirroring `CarGiveCommand`. WS8's flat-growth
  argument is only correct if the shortlist lands — and the shortlist is explicitly the user's optional pick (§9b).
  Building a registry now for gadgets that may never exist is YAGNI; per-type is also what already exists for cars
  (`CarGiveCommand.java:99` `giveCarItem`, verified). **Recommend Option A** (`GrappleCommand`/`GrappleGiveCommand`,
  which WS8 already carries as G4's pre-agreed fallback) and extract the registry when the *fourth* give command
  hurts. Either way the orchestrator must pick one before WS7 G4 and WS8 G4 both merge.
- **C2. The permission analysis is half right — and the half it misses is exactly what K4 asked for.** Verified:
  the auto-derived command namespace is real (`gangland.command.<label>` documented at
  `gangland-api/.../command/Command.java:22`, enforced at `:54` via `sender.hasPermission(getPermission())`), and
  there are indeed **zero** permission literals in any gadget command class. But Gangland also has a **per-item**
  permission layer WS8 never mentions: `Car.getPermission()` → `"gangland.cars." + carId`
  (`car/Car.java:75-77`), registered through `CarAddon.java:145` (`permissionRegistrar.accept(...)`) and enforced at
  `listener/car/CarInteractListener.java:59`. That is precisely K4's `gangland.gadget.*` intent, and it is already a
  house pattern — so this is **not a discrepancy to escalate to the user** (§9a's last row) and needs no
  `Command.java` prefix override. Grapple gets `gangland.grapples.<id>` (or `gangland.gadgets.<id>`) the same way.
  This also closes WS7 §13's "Car give's actual permission gate" unverified item and kills its proposal to invent a
  parallel scheme — reconcile both plans on: command node auto-derived, item node hand-written via
  `permissionRegistrar`.
- **C3. `GadgetType` is dead code, and WS7 never touches it — §10's merge-conflict warning names the wrong file.**
  `GadgetType.java:3-7` (`CAR`, `WEARABLE`, `JETPACK`) has **zero** usages anywhere in the reactor (grep excluding
  its own file returns nothing), and WS7's §2 file list does not include it. Adding `GRAPPLE` changes no behaviour.
  Either drop the edit or (better, ponytail) propose deleting the enum. The files WS7 and WS8 **actually** collide on
  are `GadgetItemPredicates.java`, `GadgetModuleConfig.java`, `commands.json`, `ItemKind.java` and
  `GadgetFileConfig.java` — put those in §10 instead.
- **C4. §3c's reasoning understates the shared shape (the conclusion still holds).** The plan calls the temptation a
  "one-method contract … a single boolean method" (`:96-101`). Reading the real classes: `JetpackService` carries
  `Map<UUID, JetpackSession> activeSessions` (`:29`), `activate` (`:53`), `deactivate` (`:78`), `isActive` (`:101`),
  `getSession` (`:107`), `refreshSessions` (`:152`), plus a `BukkitRunnable` tick body — so grapple's
  start/cancel/isActive/tick/cooldown really is a ~5-member echo, not one method. The right argument for rejecting
  the interface is that **no consumer needs both** (each fall-damage listener injects its own concrete service), not
  that the contract is tiny. Also `JetpackFallDamageListener` is **37 lines**, not the "18 lines total" claimed at
  `:101` (`:18-35` is the annotation-to-method span, which the plan elsewhere cites correctly). Keep the rejection,
  fix the reasoning, keep the `ChanneledGadgetSession` upgrade marker.
- **C5. G0 can be deleted — both questions it exists to answer are already answered.** (a) `GanglandCarMessages`
  reads the **shared `Messages` enum** (`Messages.CAR_NO_PERMISSION`, `CAR_ALREADY_DRIVING`, `CAR_FUEL_CAN_EMPTY`,
  … at `:14-34`), and WS7 §5 plus CLAUDE.md's Module API contract already rule that a **new** string must not copy
  that legacy precedent — grapple's strings go in module-owned YAML. (b) The give-command shape is C1. That removes
  the whole gate. Separately, §13's open question is now closed: **no `JetpackFallDamageListenerTest` exists** — the
  gadget module's test sources are exactly 8 files (`CarAccessPolicyTest`, `CarNbtIdentityTest`, `ExhaustSideTest`,
  `ParkedVehicleTest`, `VehicleSessionTest`, `GadgetModuleConfigConstructorTest`, `GadgetModuleTest`,
  `JetpackTaskConsumptionRateTest`). Write `GrappleFallDamageListenerTest` fresh, red-first.
- **C6. The shortlist breaks the XSeries house rule in two places.** §9b's smoke/flash names
  `PotionEffectType.BLINDNESS`/`SLOWNESS` and `Particle.CAMPFIRE_SIGNAL_SMOKE`; the parachute names
  `PotionEffectType.SLOW_FALLING`. `feedback_xseries_required` (and CLAUDE.md) require XPotion/XParticle for
  version-drifting enums — `SLOWNESS` is literally a drift case (`SLOW` on older API). The plan applies the rule
  correctly to `Material`/XMaterial at `:172`, so this is an oversight, not a disagreement.
- **C7. §7's `GadgetModuleTest` row mis-describes the test.** `GadgetModuleTest.java` is the module's
  *declaration* test (configuration classes in order, listener/command/repository package names, "declared packages
  match where the classes live" — javadoc `:17-20`), not "CarCommand wiring assertions"; `:6-9` are imports.
  Extending it means adding a package/registration declaration assertion, which is a different (smaller) edit.
- **C8. Every `GadgetModuleConfig` line number in WS8 goes stale the moment WS7 lands** — WS7 inserts three jetpack
  `@Bean` methods beside `:119-137`. Cite the bean *names* (`carConverter`/`carItemSerializer`/`carItemRefresher`),
  not the lines, since WS8 executes second.
- **C9. The `ponytail:` fuel upgrade path is not a one-liner.** The `FuelService` signature claim is **correct**
  (`hasFuel(Player, String)` `:139`, `getFuelLevel` `:151`, `consumeFuel(Player, String, int)` `:177` — generic, no
  `Wearable` anywhere, exactly as REVIEW-WS7 confirmed for the `*OnWearable` family at `:228-271`). But the fuel-sink
  side is a **single** predicate: `setFuelSinkPredicate(Predicate<ItemStack>)` (`FuelService.java:50`), and
  `GadgetModuleConfig.java:115` already spends it on the jetpack. A second fuelled gadget must compose the predicate,
  not set it. Reword the marker.

## Simplifications (ponytail)

- **S1.** Delete G0 (C5) and the `GadgetType` edit (C3). Two steps that change nothing.
- **S2.** Drop `GrappleItemRefresher` from G1. Same argument as REVIEW-WS7's S4: the refresher exists for admin
  "factory reset" restamps, and a grapple whose only mutable state is a cooldown map has nothing to refresh.
  `ItemRefresherRegistry.register(...)` is one line to add the day it matters. This also moots §13's open question
  about the refresher touching vanilla `Damage` meta.
- **S3.** Drop the `GadgetItemRegistry` with Option B (C1) — that is the whole of G4's novelty; per-type give is a
  copy of a file that already exists.
- **S4.** Reuse `CarNbtIdentityTest`'s shape for `GrappleNbtIdentityTest` including its permission assertion
  (`CarNbtIdentityTest.java:95` asserts `"gangland.cars.pickup_truck"`), which gives C2's item-permission node a
  pinning test for free.

## Missing consumers found by graphify affected / grep

| Type moved / added | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `items/grapple.yml` | `GadgetFileConfig`'s module-classloader `FileHandler` registration | `GadgetFileConfig.java:41` (+ javadoc `:20`) | B3 — YAML never copied out of the jar |
| `ItemKind.GRAPPLE` | `ItemKind` lives in `gangland-infra/gangland-item`, outside the module | `ItemKind.java:14-18` | B2 — §2's "everything inside gangland-gadget" is false |
| Grapple item permission | per-item node pattern: `Car.getPermission()`, `CarAddon`'s registrar, `CarInteractListener`'s check | `Car.java:75-77`, `CarAddon.java:145`, `CarInteractListener.java:59` | C2 — K4 is satisfiable today; no api change needed |
| `GadgetItemPredicates` / `GadgetModuleConfig` / `commands.json` / `ItemKind` | WS7 edits all four in G1–G4 | WS7 §2, §4 | C3 — real rebase surface; `GadgetType` is not |
| Fall-damage pattern | GD-04 (P1, open) constrains the shape being copied | `triage/gadgets-cars-fuel-jetpack.txt:4` (GD-05 at `:5`) | B4 — §11 claims no docket ids apply |
| `FuelService` fuel-sink | single-predicate setter already claimed by the jetpack | `FuelService.java:50`; `GadgetModuleConfig.java:115` | C9 |

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| `Gadget` interface (K5) | Reject, revisit at a third channeled gadget | **Agree** — but fix the reasoning (C4). The shared shape is ~5 members, not one; the real reason to skip it is that no consumer injects both services. The `ChanneledGadgetSession` trigger condition is well drawn. |
| Give-command shape | Option B (generic + registry) | **Disagree — Option A** (C1). Collides with WS7, and builds flat-growth machinery for a shortlist the user has not picked. WS8 already carries A as its own fallback. |
| Fuel vs durability vs neither | **Cooldown only** | **Agree, strongly.** Best-argued call in either plan; the `FuelService` signatures cited are correct, the durability rejection is honest about what it did not trace, and the upgrade path is named. Only the "one-line" wording needs C9. |
| Permission wording | State the K4 discrepancy to the user | **Disagree — not a decision.** C2: the per-item node pattern already exists and satisfies K4. Escalating it spends a user round-trip on a settled house convention. |
| Smoke/flash avoids Bartizan `ThrowableWeapon` | Vanilla `Snowball` + PDC marker | **Agree, and verified**: `ThrowableWeapon.java` and `ThrowableType.java` do exist in `bartizan-api/.../weapon/`, so the temptation is real; `launchProjectile`/`PersistentDataContainer`/`ProjectileHitEvent`/potion/particle are all pure Bukkit, so the design genuinely carries **zero** Bartizan symbols. Only C6's XSeries wrapping is missing. |
| Recommended first three | grapple → parachute → smoke/flash | **Agree on order, with one caveat**: the parachute inherits B4. It is "`JetpackEquipListener` + `JetpackFallDamageListener` shapes applied to a wearable" — i.e. two open P1 rows (GD-04 fall immunity, GD-07 equip-permission only checked on inventory clicks, `triage:7`) sit directly on the pattern it copies. Cheapest only if those are fixed first or designed around. |

## Estimate check

**≈3.1 executor-days for the grapple is light; ≈4.5–5 is honest** — the same class of gaps REVIEW-WS7 found, at
smaller scale:

- B2 + B3 add two files outside the tree (`ItemKind`, `GadgetFileConfig`) and turn G1 into a reactor-wide build.
- G2 at **1.0 day** is the thin one: a capped-velocity pull loop, arrival detection, a sync `Timer`, a
  `PlayerFishEvent` state machine **and** cross-version hook behaviour. Velocity tuning is playtest-bound work that
  does not converge in a day; 1.5–2.0 is realistic.
- B4's corrected fall-damage logic (one-shot bounded grace, `hasFuel`-equivalent gating) is real design, not a mirror.
- Not costed anywhere: `graphify update . --force` (WS7's G-final has it, WS8's does not), the memory/CLAUDE.md note,
  and the docket row if anything surfaces (§11 handles the policy but budgets nothing).
- Correctly costed and worth keeping: `commands.json` (G4), smoke rows (G-final), docs (G-final, honestly marked
  optional). No `plugin.yml` or `gangland-build` edit is needed — gadget is already listed at
  `gangland-build/pom.xml:84`/`:133` — say so, as WS7 should too.
- Deleting G0 (S1) gives back 0.1; S2/S3 give back ~0.3. Net still up.

The shortlist effort numbers (1–5 days each) are plausible and honestly ranged; the drone/camera "riskiest estimate
on this list" caveat and the disguise-kit **drop** recommendation are both correct calls — the latter is the only
place either plan proposes adding an external dependency and rejects it unprompted.

## Things I could not verify

- `PlayerFishEvent.State` semantics across the 1.16–1.21 floor (WS8 §13, Risk 2) — I did not test any revision;
  the mitigation (smoke at the floor and the newest build) is the right shape. `IN_GROUND` and `getHook()` exist in
  the API; behaviour on a grapple-tagged rod is the untested part.
- Whether `ItemRefresherRegistry`'s reload restamp touches vanilla `Damage` meta (WS8 §13) — not traced. S2 makes it
  moot for grapple; it stays open for any future durability gadget.
- Whether Keystone composes a nested `SubArgument`'s node as `gangland.command.car.give` exactly — I confirmed the
  *namespace* (`Command.java:22`) and the *enforcement* (`:54`), not Keystone's `SubArgument` string composition.
  WS8's own §13 flags this correctly; it does not affect C2's conclusion.
- Performance of concurrent pull timers (Risk 3) — no profiling; the 5-simultaneous-pulls smoke row is the right gate.
- No `mvn` build was run; all findings are static.

---

## Re-review (after fix pass)

Verdict: **PASS** — B1–B4, C1–C9 and S1–S4 are all genuinely applied, each re-verified against source here rather
than taken on the planner's word. No new blockers in WS8. This is the last round, so everything below is written as
executor guidance, not as another fix loop: one item must be carried back to **WS7** (it is my own earlier advice
that was wrong, not a WS8 defect), and two are residual risks to accept knowingly.

### Cross-plan safety check against my WS7 re-review (B5–B7)

**Confirmed clean, not assumed.** Grepping the revised WS8 for `CarMeleeWeaponLookup`, `CarWeaponDamageListener`,
`CarDamageState`, `CarDamageListener` and `isBartizanAvailable` returns exactly **one** hit — §1's Out row
(`plan:63`), which explicitly disclaims that area as WS7's. WS8's actual reuse targets are
`JetpackFallDamageListener` (shape only, and now deliberately *not* copied — see B4), `JetpackService.isActive`,
the three keystone-item registries and `FuelService`; none is touched by WS7's B5–B7, which are confined to
`GadgetModuleConfig`'s bean wiring for the car-damage path. WS8 is unaffected whichever way WS7's B5/B6 are fixed.

### Verified fixed

| # | Verdict |
|---|---|
| **B1** | **Fixed and correct.** The `ItemVocabulary`/`gadget:grapple` design is gone; grapple registers directly via `grappleConverter`/`grappleItemSerializer`. The refresher was also dropped (S2) — **and that is safe for the grapple specifically**: `ItemRefresherRegistry`'s javadoc (`keystone-item/.../ItemRefresherRegistry.java:14-15`) states *"If nothing claims the item, a plain `ItemStack#clone()` is returned, so call sites can always rely on getting a safe-to-hand-out copy"*, so shop/loot delivery still hands out a valid stack. Since a grapple carries no per-instance mutable state (the cooldown lives in a service map keyed by player, never on the item), a clone of the template is indistinguishable from a fresh build. Reload behaviour is not broken. |
| **B2** | **Fixed.** §2's tree now shows `ItemKind.GRAPPLE` in `gangland-infra/gangland-item` as an out-of-module edit with the reactor-wide build called out, and §10 flags it as the shared edit with WS7's `ItemKind.JETPACK`. |
| **B3** | **Fixed.** `grappleAddon(...)` + the `FileHandler` registration are added to `GadgetFileConfig`, mirroring `:41`'s car line, with the FILE-phase reasoning cited from the class javadoc. |
| **B4** | **Fixed, and it holds against both bugs' fix directions, not just the old shape.** GD-04's direction is *"cancel fall damage only while hasFuel (and recently flying)"* (`triage/gadgets-cars-fuel-jetpack.txt:4`) — i.e. gate immunity on the resource **and** bound the recency window. The grapple has no fuel, so its analogue of "the resource" is the live pull, and the design gates on exactly that (`isActive` true only during the mechanical pull, itself bounded by `Max_Duration_Ticks` plus cancel-on-damage/sneak) and then allows **one** consumed post-arrival cancellation via `consumeLandingGrace(Player)`. That closes GD-04's actual failure mode — unbounded immunity that a player can hold or chain — rather than merely avoiding its syntax, and the test asserts the second FALL inside the same window is *not* cancelled. GD-05's direction (*"gate the descent cap and air control on hasFuel"*) has no grapple analogue since there is no glide/steer mechanic, and §11 says so honestly instead of claiming a fix. |
| **C1** | **Fixed.** Per-type Option A, matching WS7's real text (`jetpack_give` mirroring `car_give`); `GadgetItemRegistry` dropped. The two plans now ship the same shape. |
| **C2** | **Fixed, and the planner found a refinement I had not stated:** cars gate *interaction* with the per-item node (`CarInteractListener.java:59`) while `CarGiveCommand` carries no permission check of its own — so grapple gates **launch** with `gangland.grapples.<id>` and leaves the give command on its auto-derived node. That matches car's real behaviour more precisely than my correction did. |
| **C3** | **Fixed.** The dead `GadgetType` edit is dropped entirely (correct — zero reactor usages) and §10 now names the five genuinely shared files. |
| **C4** | **Fixed.** The "no consumer injects both services" reasoning is **verified true**: `graphify affected "JetpackService"` returns only the five `listener/jetpack/*` classes, `JetpackTask` and `GadgetModuleConfig`. The one class that would name both services is `GadgetModuleConfig`, which *produces* them — a producer is not a polymorphic consumer, so no interface is justified. Line count corrected to 37. |
| **C5–C9** | **All fixed.** G0 deleted; XPotion/XParticle applied in §9b; `GadgetModuleTest` re-described as the module *declaration* test; `GadgetModuleConfig` citations switched to bean names; the fuel marker reworded for predicate **composition** (correct — `setFuelSinkPredicate` at `FuelService.java:50` is a single setter already spent by the `jetpackService` bean). S4's addition of `CarNbtIdentityTest`'s permission assertion (`:95`, `assertEquals("gangland.cars.pickup_truck", …)`) gives the new node a pinning test for free. |
| **Estimate** | **Honest.** 5 gates, ≈4.5–5 days, with the give-backs from S1–S3 netted against the reactor build, realistic G2 tuning and real G3 design work. Matches my independent check. |

### Carry-back to WS7 — my own S4 was wrong advice, and WS7 adopted it

Not a WS8 defect; WS8's identical S2 is safe for the reasons in B1 above. But the **jetpack** is not the grapple:
its per-instance state (`fuel_current`) lives *on the ItemStack*. Evidence, all re-verified here:

- Shops refresh on **every delivery**: `ShopPurchaseService.java:49` and `ShopBarterService.java:46` both call
  `refresherRegistry.refresh(entry.getItem(), player)`; the class javadoc at `ShopPurchaseService.java:13` says
  "produces a fresh copy of the sold item".
- With no refresher registered, that call falls through to a plain `clone()` of the admin's stored template
  (`ItemRefresherRegistry.java:14-15`) — so the buyer receives whatever fuel level the template froze.
- `CarItemRefresher`'s javadoc names this exact hazard: it exists to strip "whatever frozen state the admin's
  template carried (**partial fuel**, used durability, a prior owner UUID)".
- The recorded house rule `feedback_item_refresher_pattern` states it outright: stateful items need a refresher
  because shops/traders refresh on every delivery, not once at placement.
- Today this is covered for the jetpack by **Bartizan's** `WearableRefresher` (census C3 §1). WS7 removes the
  jetpack from Bartizan, so skipping `JetpackItemRefresher` converts a working behaviour into a regression.

**Executor action: register `JetpackItemRefresher` in WS7's G3 after all — treat REVIEW-WS7's S4 as withdrawn.**
It is one class mirroring `CarItemRefresher` plus a one-line registration (~0.2 day, inside WS7's current G3
estimate). WS8 needs no change.

### Residual risks to accept knowingly (no further round planned)

1. **Stale grapple templates.** Because there is no grapple refresher, a shop template stamped before a
   `items/grapple.yml` change keeps the old name/lore/model-data on delivery. Cosmetic only, fixable by re-issuing
   the template with `/glw grapple give`. Register a refresher only if a future grapple gains per-item state.
2. **`PlayerFishEvent.State` across the 1.16–1.21 floor** remains untested (§8 Risk 2, §13). The pre-agreed
   fallback (custom projectile + raytrace) is a real cost jump if the floor misbehaves — smoke the floor build
   early in G2 rather than at G-final, so the fallback decision is made before the tuning work is sunk.
3. **The parachute's inherited P1s** (GD-04-style fall handling, GD-07 equip-permission gap) are now stated in
   §9b's caveat. If the user picks it as gadget #2, budget the corrected shapes rather than the "~1.5 days"
   mirror cost.
