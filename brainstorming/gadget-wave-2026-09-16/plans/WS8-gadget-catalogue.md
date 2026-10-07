# WS8 — Gadget catalogue

Planner: Sonnet, graphify-first (Gangland graph HEAD `fb460b35`, fresh; Bartizan graph rebuilt 2026-09-16, fresh).
Template: `PLANNER-BRIEF.md` §"Plan template". This revision responds to `reviews/REVIEW-WS8.md` (verdict: PASS WITH
FIXES, 4 blockers) — see §0b immediately below for the blocker-by-blocker response, then the rest of the document
for the applied fixes. `plans/WS7-gadget-ownership.md` now exists (it did not when this plan was first written) and
has been read directly for this revision, not just through the review's quotes.

## 0b. Review response

Every finding below was independently re-verified against source in this revision (not taken on the reviewer's word
alone) — file:line citations in the table are from that re-check.

### Blockers

| # | Finding | Status | How |
|---|---|---|---|
| B1 | §3a/G1 invented a gadget-owned `ItemVocabulary` for a `gadget:grapple` prefix | **FIXED** | Re-verified: `ItemVocabulary` is the SPI an *external* plugin uses to publish items into the host via the `ServicesManager` (this repo's own `BartizanItemVocabulary` is exactly that — an external plugin's registration). A runtime module sits inside the same bean graph and registers directly into the three keystone-item registries, which is what the car does today (confirmed at `GadgetModuleConfig.java:120-124/126-131`, bean names `carConverter`/`carItemSerializer`/`carItemRefresher`). Deleted the `ItemVocabulary` row and the G1 clause entirely (§3a, §4 G1). There is no `gadget:`/`grapple:` prefix — resolution is the `ItemKind` label, confirmed at `ItemKind.java:14-18` (`UNIQUE("unique")`, `CAR("car")`, …), format `"car:<id>"`. |
| B2 | `ItemKind.GRAPPLE` used in §3a but §2 claimed everything lives inside `gangland-gadget` | **FIXED** | Re-verified: `ItemKind` is `gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/ItemKind.java:14-20`, a different reactor module, re-exported through `gangland-api` at compile scope. §2's tree now lists it explicitly as an out-of-module edit and flags the reactor-wide `mvn clean install`. WS7's own plan confirms it makes the identical edit for `ItemKind.JETPACK` at its G1 (`WS7-gadget-ownership.md:97`) and that no exhaustive `switch` over `ItemKind` exists in the reactor (`WS7-gadget-ownership.md:160`) — low-risk shared edit, coordinated in §10. |
| B3 | `items/grapple.yml` never gets copied out of the module jar — the `FileHandler` registration was missing | **FIXED** | Re-read `GadgetFileConfig.java` in full: `carAddon(...)` registers `fileManager.addFile(new FileHandler(plugin, "cars", "items", ".yml", moduleLoader.classLoader()), true)` at `:41`, and the class javadoc at `:17-22` explains why this must run in the FILE phase. §2 and §4 G1 now add the identical `grappleAddon(...)` bean + `FileHandler` registration to `GadgetFileConfig`. |
| B4 | G3's fall-damage design mirrored `JetpackFallDamageListener`'s shape, which is the open P1 bug GD-04 (unconditional immunity while "active"), and widened it with a rolling 40-tick grace window | **FIXED** | Confirmed at `brainstorming/bug-docket-2026-09-06/triage/gadgets-cars-fuel-jetpack.txt:4` — GD-04, P1, "Cancel fall damage only while hasFuel (and recently flying)"; row 5 is GD-05 ("gate descent/steer on hasFuel"), the same family. WS7's own plan confirms both **stay open** and are explicitly out of its scope (`WS7-gadget-ownership.md:205-206`). §4 G3 is rewritten to a one-shot, consumed landing-grace flag instead of a rolling time-window check (detail below) — new code, not a mirror of the buggy shape. §11 now lists both ids. |

### Corrections

| # | Finding | Status | How |
|---|---|---|---|
| C1 | Give-command shape collided with WS7 (this plan picked generic Option B; WS7 ships per-type) | **FIXED** | Re-verified against WS7's actual text: `commands.json EDIT — +jetpack_give entry (mirrors car_give)` (`WS7-gadget-ownership.md:56`), "mirror the smaller `/glw car give` leaf-only shape per K4" (`:116`). Switched §3b/§4 G4 to Option A (per-type `GrappleCommand`/`GrappleGiveCommand`, `grapple`/`grapple_give` in `commands.json`), dropped the `GadgetItemRegistry` entirely (also S3). |
| C2 | Missed the per-item permission layer that is K4's actual intent | **FIXED** | Re-verified directly: `Car.getPermission()` → `"gangland.cars." + carId` (`Car.java:75-77`), registered via `permissionRegistrar.accept(car.getPermission())` at `CarAddon.java:145`, enforced at `CarInteractListener.java:59` (`if (!player.hasPermission(car.getPermission()))`). Also re-verified `CarGiveCommand.java` in full: it has **zero** permission checks in its own body — the give command relies solely on the auto-derived `gangland.command.<label>` node; the per-item node gates *interaction* (mount/drive), not *giving*. Grapple now follows the same split: `gangland.grapples.<id>` gates launch (checked in `GrappleLaunchListener`, mirroring `CarInteractListener.java:59`), the give command is gated only by its auto-derived command node, matching car's real behaviour exactly. Removed the "escalate a discrepancy to the user" framing (§9a) — this is a settled house pattern, not a decision. |
| C3 | §10 named `GadgetType` as the WS7/WS8 collision file; it isn't | **FIXED** | Re-verified: `GadgetType.java:3-7` (`CAR`, `WEARABLE`, `JETPACK`) — grepped the reactor excluding its own file, zero usages found. Dropped the `GRAPPLE` edit from §2/§4 entirely (dead enum, changes no behaviour) and noted it as a candidate for deletion outside this plan's scope. §10 now names the real shared files: `GadgetItemPredicates.java`, `GadgetModuleConfig.java`, `commands.json`, `ItemKind.java`, `GadgetFileConfig.java`. |
| C4 | K5 reasoning understated the shared shape as "one method" | **FIXED** | Re-read `JetpackService.java` in full: `activeSessions` field (`:29`), `activate` (`:53`), `deactivate` (`:78`), `isActive` (`:101`), `getSession` (`:107`), `refreshSessions` (`:152`), plus the `BukkitRunnable`-driven tick body inside `JetpackTask` — a ~5-member echo, not one method. §3c reworded: the rejection now rests on "no consumer injects both services" (each fall-damage listener takes its own concrete service type), not contract size. Also fixed the `JetpackFallDamageListener` line count: re-read the file, it is 37 lines total (`:1-37`), not 18 — `:18-35` was already the correct annotation-to-method span cited elsewhere in this plan; the "18 lines total" phrase elsewhere was wrong and is removed. |
| C5 | G0 was redundant — both questions it existed to answer are already answered | **FIXED** | Re-read `GanglandCarMessages`-equivalent question by checking the module-API contract rule directly (CLAUDE.md "Module API contract": new module strings go in the module's own YAML, not the shared `Messages` enum) — confirms grapple's `GrappleMessageContract` sources its own YAML, no need for a read-first gate. Give-command shape is resolved by C1. G0 deleted (also S1). Confirmed separately: the gadget module's test sources are exactly 8 files (none named `*FallDamage*Test*`), so `GrappleFallDamageListenerTest` is written fresh, genuinely red-first — no mirror-test to find. |
| C6 | Shortlist broke the XSeries house rule for potion effects and particles | **FIXED** | `feedback_xseries_required` requires XPotion/XParticle for version-drifting enums. §9b's smoke/flash and parachute rows now specify `XPotion.BLINDNESS`/`XPotion.SLOWNESS`/`XPotion.SLOW_FALLING` and `XParticle`'s campfire-smoke equivalent instead of raw `PotionEffectType`/`Particle` constants. |
| C7 | §7 mis-described `GadgetModuleTest` as asserting "CarCommand wiring" | **FIXED** | Re-read the class javadoc directly (`GadgetModuleTest.java:17-20`): it is the module's *declaration* test — configuration classes in order, listener/command/repository package names, declared packages matching where classes live. §7 now describes extending it correctly: adding grapple's package/registration declaration, not a command-wiring assertion. |
| C8 | `GadgetModuleConfig` line citations will go stale once WS7 lands (it inserts jetpack `@Bean` methods in the same range) | **FIXED** | Switched every `GadgetModuleConfig` citation in this plan from line numbers to bean method names (`carConverter`, `carItemSerializer`, `carItemRefresher`) per the file as it reads today; grapple's equivalents are named `grappleConverter`/`grappleItemSerializer` (no refresher — see S2). |
| C9 | The `ponytail:` fuel-upgrade comment called it "one line"; the fuel-sink side is a single predicate already spent | **FIXED** | Re-verified: `FuelService.setFuelSinkPredicate(Predicate<ItemStack>)` at `FuelService.java:50` is a single setter, and `GadgetModuleConfig.java`'s `jetpackService(...)` bean already calls `fuelService.setFuelSinkPredicate(this::isJetpackFuelSink)` (confirmed at the bean body, currently around `:113-116`). A second fuelled gadget would need to *compose* the predicate (OR the jetpack check with a grapple check), not set a second one. §9a's marker reworded accordingly. |

### Simplifications (ponytail, from the reviewer)

| # | Applied |
|---|---|
| S1 | G0 deleted (folds into C5). |
| S2 | `GrappleItemRefresher` dropped from G1 — grapple's only mutable state is a cooldown map, nothing to refresh; `ItemRefresherRegistry.register(...)` is a one-line addition the day it matters. This also moots the old §13 question about the refresher touching vanilla `Damage` meta (durability was already rejected in §9a for other reasons). |
| S3 | `GadgetItemRegistry` + Option B dropped (folds into C1). |
| S4 | `GrappleNbtIdentityTest` now mirrors `CarNbtIdentityTest`'s permission assertion too (confirmed at `CarNbtIdentityTest.java:90-96`, `assertEquals("gangland.cars.pickup_truck", car.getPermission())`) — gives the new per-item permission node a pinning test for free. |

### Estimate check

**FIXED** — §12 revised from ≈3.1 to ≈4.5-5 executor-days; see §12 for the itemized reasoning (B2/B3's extra
out-of-tree files and reactor build, G2's realistic 1.5-2.0 days for playtest-bound velocity tuning, B4's corrected
fall-damage logic being real design work, and previously-uncosted `graphify update --force`/memory-note/docket
steps). No disputes — the reviewer's arithmetic checks out against the corrected step list below.

### Disputes

None. Every blocker and correction was independently re-verified against source (not just the review's citations)
during this revision and confirmed accurate; nothing in REVIEW-WS8.md is contested.

### 0d. Correction (G2+G3 fix round 1, 2026-09-20, review finding F2)

§4 G3's `tickSession` description ("anchor chunk unload") was implemented as written (`!anchor.getChunk()
.isLoaded()`) and that expression is **broken**: `Location#getChunk()` *loads* (and generates, if needed) the
chunk it returns, so `.isLoaded()` on its result is always true and the branch was unreachable — a far anchor would
have force-generated its chunk every tick instead of ever cancelling. The fix uses `World#isChunkLoaded(int
chunkX, int chunkZ)` (a pure check, no load/generate side effect; present since at least the 1.16.5 floor) with
`anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4`. Any future plan step referencing "anchor chunk unload" should
read `World#isChunkLoaded(cx, cz)`, not `Location#getChunk().isLoaded()`.

## 1. Scope

| | |
|---|---|
| **In** | The reusable per-gadget item pattern (item/YAML/converter-serializer/give/permission — no `ItemVocabulary`, per B1), stated precisely from K4 + census C1 §2. The grappling hook: item, launch/land/pull mechanics, cooldown, fuel-vs-durability-vs-neither decision, permissions, YAML knobs, anti-abuse, and a fall-damage design that does **not** reproduce GD-04/GD-05. A shortlist of 6-8 further gadgets with reused seams and effort. A recommended first three. |
| **Out** | Implementing any shortlist gadget beyond the grappling hook (proposal only — user picks). Anything WS7 owns (jetpack Bartizan decoupling, `Settings.isBartizanAvailable()`, `CarDamageListener` conditioning). The safe-cracking minigame itself (lootchest-owned; only the lockpick *item* is this plan's concern, §9). Fixing GD-04/GD-05/GD-07 on the *existing* jetpack listener (WS7 leaves them open by its own §11; this plan only ensures its *new* code doesn't repeat the same shape). Deleting the dead `GadgetType` enum (C3 — noted as a candidate, not actioned here). |
| **Deferred** | Extracting a shared session interface — rejected for now (§3c); revisit only if a *third* channeled gadget needs the same start/cancel/isActive/tick shape **and** some consumer needs to call it polymorphically. Extracting a `GadgetItemRegistry` for the give command — rejected as YAGNI for a shortlist the user hasn't picked yet (§3b); revisit at the fourth give command. |

## 2. Target layout

No new Maven module. The grappling hook lands inside the existing `gangland-features/gangland-gadget` reactor
module, **plus two out-of-module edits** shared with WS7 (flagged, not new — WS7 makes the same class of edit for
the jetpack):

```
gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/
└── ItemKind.java                                  # EDIT — add GRAPPLE("grapple") beside CAR("car") (ItemKind.java:14-18)
                                                    #   SHARED EDIT with WS7 G1 (adds JETPACK the same way) — coordinate, §10

gangland-features/gangland-gadget/src/main/
├── resources/
│   ├── items/grapple.yml                          # NEW — mirrors items/cars.yml
│   └── commands.json                              # EDIT — add grapple, grapple_give (mirrors car/car_give, commands.json:1-13)
└── java/org/luckyraven/gangland/gadget/
    ├── grapple/
    │   ├── Grapple.java                            # NEW — domain entity + getPermission() (mirrors car/Car.java:75-77)
    │   ├── GrappleKey.java                         # NEW — NBT tag constants (mirrors car/CarKey.java)
    │   ├── GrappleService.java                     # NEW — cooldown map, isActive(Player), start/cancel/tick,
    │   │                                            #   one-shot landing-grace flag (§4 G3)
    │   └── config/GrappleAddon.java                 # NEW — loads items/grapple.yml, calls permissionRegistrar.accept(...)
    │                                                 #   (mirrors CarAddon.java:143-146)
    ├── item/
    │   ├── GrappleConverter.java                    # NEW — mirrors item/CarConverter.java
    │   ├── GrappleItemSerializer.java                # NEW — mirrors item/CarItemSerializer.java
    │   │                                              #   (NO refresher — S2)
    │   └── GadgetItemPredicates.java                 # EDIT — add GRAPPLE beside CAR (GadgetItemPredicates.java:16)
    ├── listener/grapple/
    │   ├── GrappleLaunchListener.java                # NEW — PlayerFishEvent state machine + permission check
    │   │                                              #   (mirrors listener/car/CarInteractListener.java:57-61)
    │   └── GrappleFallDamageListener.java             # NEW — corrected shape, does NOT mirror JetpackFallDamageListener's
    │                                                   #   GD-04 bug (§4 G3)
    ├── command/
    │   ├── GrappleCommand.java                       # NEW — mirrors command/CarCommand.java (Option A, C1)
    │   └── GrappleGiveCommand.java                    # NEW — mirrors command/CarGiveCommand.java (NO permission check
    │                                                    #   in the command body, matching car's real behaviour — C2)
    └── config/
        ├── GadgetFileConfig.java                     # EDIT — add grappleAddon(...) bean registering a FileHandler
        │                                              #   for items/grapple.yml (mirrors GadgetFileConfig.java:38-46,
        │                                              #   B3)
        └── GadgetModuleConfig.java                    # EDIT — add grappleConverter/grappleItemSerializer @Bean methods
                                                        #   (mirrors carConverter/carItemSerializer bean bodies, C8)
```

Nothing is deleted. No `ItemVocabulary` class is created (B1). No `GadgetType.java` edit (C3 — dead enum, out of
scope). No `GrappleItemRefresher` (S2).

## 3. Seams — the reusable per-gadget pattern (K4) and what grapple adds

### 3a. The pattern itself (proven by the car pipeline today; the jetpack is WS7's second proof)

| Stage | Mechanism | Car precedent (cite) | Grapple does the same |
|---|---|---|---|
| Config | Module-owned YAML in the jar at the data-folder path | `items/cars.yml` | `items/grapple.yml` |
| File-phase load | A `FileHandler` registered with the **module classloader**, in the same `@Configuration(phase = Phase.FILE)` class as the addon bean | `GadgetFileConfig.carAddon(...)`: `fileManager.addFile(new FileHandler(plugin, "cars", "items", ".yml", moduleLoader.classLoader()), true)` (`GadgetFileConfig.java:41`); class javadoc `:17-22` explains why this must be FILE-phase | `GadgetFileConfig.grappleAddon(...)` does the identical call for `"grapple"`/`"items"` (B3) |
| Item identity | keystone-item `ItemBuilder` stamps an NBT tag | `CarKey.CAR_ID` via `CarItemSerializer` | `GrappleKey.GRAPPLE_ID` via `GrappleItemSerializer` |
| Registration | Two-of-three keystone-item registries, wired as `@Bean` methods in `GadgetModuleConfig` (car uses all three; grapple skips the refresher, S2) | `carConverter(...)`: `itemConverterRegistry.register(ItemKind.CAR, converter)`; `carItemSerializer(...)`: `itemSerializerRegistry.register(GadgetItemPredicates.CAR, serializer)` (bean names, not line numbers — C8) | `grappleConverter(...)` / `grappleItemSerializer(...)`, `ItemKind.GRAPPLE` / `GadgetItemPredicates.GRAPPLE` |
| Predicate | A static NBT-tag test used to recognize the stack | `GadgetItemPredicates.CAR = stack -> hasTag(stack, CarKey.CAR_ID.getKey())` (`GadgetItemPredicates.java:16`) | `GadgetItemPredicates.GRAPPLE`, same shape |
| Cross-module/cross-plugin resolution | **Not `ItemVocabulary`** — that SPI is for an *external plugin* publishing into the host via the `ServicesManager` (what Bartizan's own item registration does). A runtime module inside the same bean graph registers directly (B1). Resolution string is the `ItemKind` label, e.g. `"car:pickup_truck"` | `ItemKind.CAR("car")` (`ItemKind.java:16`) | `ItemKind.GRAPPLE("grapple")`, added to `gangland-infra/gangland-item` (shared edit with WS7, §10) |
| Give | A leaf command reading an id from the addon and stacking/dropping the built item — **no permission check in the command body** | `CarGiveCommand.giveCarItem` (`CarGiveCommand.java:99-129`) — re-read in full, zero `hasPermission` calls anywhere in the class | `GrappleGiveCommand`, same shape, same absence of an in-command permission check (C2) |
| Command permission | Auto-derived by Keystone's command framework from the argument-tree path under `gangland.command.<label>` | Namespace documented at `gangland-api/.../command/Command.java:22`, enforced at `:54` (`sender.hasPermission(getPermission())`) | Same auto-derivation for `GrappleCommand`/`GrappleGiveCommand` |
| **Per-item permission** (the actual K4 `gangland.gadget.*` intent) | A `getPermission()` method on the domain entity, registered into `PermissionManager` at load time, checked at the point of *use* (not give) | `Car.getPermission()` → `"gangland.cars." + carId` (`Car.java:75-77`); registered via `permissionRegistrar.accept(car.getPermission())` at `CarAddon.java:145`; enforced at `CarInteractListener.java:59` (mount/drive gate) | `Grapple.getPermission()` → `"gangland.grapples." + grappleId`, registered the same way from `GrappleAddon`, enforced in `GrappleLaunchListener` before starting a pull (C2) |

### 3b. Give-command shape — resolved to Option A (per-type), aligned with WS7 (C1)

WS7 ships per-type (`JetpackGiveCommand`, `+jetpack_give` in `commands.json`, "mirror the smaller `/glw car give`
leaf-only shape per K4" — `WS7-gadget-ownership.md:116`). This plan's earlier Option B (a generic
`/glw gadget give <id>` + `GadgetItemRegistry`) is dropped: it built flat-growth machinery for a shortlist the user
has not picked yet (YAGNI), and diverging from WS7 on the same K4 sentence would leave two incompatible give-command
shapes merging into the same module. **Grapple ships `GrappleCommand`/`GrappleGiveCommand`, mirroring
`CarCommand`/`CarGiveCommand` exactly, with `commands.json` entries `grapple`/`grapple_give`** (no
`grapple_help`/`grapple_info`/`grapple_list` unless a later gadget needs the parity — same ponytail default WS7
applied to jetpack).

If the shortlist (§9b) actually lands three or more of its entries later, revisit consolidating the by-then four+
give commands into one registry-backed command — not before.

### 3c. K5 — is a `Gadget` interface justified now?

**No — same conclusion, corrected reasoning.** The shared shape between `JetpackService` and a would-be
`GrappleService` is real and non-trivial: `activeSessions` (a `Map<UUID, Session>`), `activate`/`start`,
`deactivate`/`cancel`, `isActive`, `getSession`, and a per-tick task body — roughly five members, not one boolean
method. But **no consumer ever needs to call either service through a shared type**: `JetpackFallDamageListener`
constructor-injects `JetpackService` directly (`@AutowireTarget({JetpackService.class})`), and
`GrappleFallDamageListener` will constructor-inject `GrappleService` directly the same way. There is no call site
that takes "some active-gadget-session" polymorphically — each listener is wired to exactly one concrete service by
design. An interface with no polymorphic call site is a marker, not an abstraction earning its keep, regardless of
how many members two implementations happen to share.

**Upgrade path (ponytail marker):** extract a `ChanneledGadgetSession` interface the day some *consumer* — a shared
fall-damage listener, a shared HUD, an admin `/glw gadget list-active` command — actually needs to treat two or more
gadget sessions polymorphically. Two implementations sharing a shape is not the trigger; one consumer needing to
call them uniformly is.

## 4. Steps — grappling hook only (shortlist gadgets are proposals, not steps, per §1 Out)

All gates run inside `gangland-gadget` (G1 also touches `gangland-item`, forcing a reactor-wide build); each ends
with a green `mvn clean install` of the affected reactor and a named row on the console harness
(`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`). G0 from the original plan is deleted (S1/C5) — both
questions it existed to answer are already settled above.

| Gate | Steps | Size | Test | Commit boundary |
|---|---|---|---|---|
| **G1 — item + registration** | `ItemKind.GRAPPLE("grapple")` beside `CAR("car")` in `gangland-item`'s `ItemKind.java:14-18` (**shared-edit coordination with WS7's `ItemKind.JETPACK`, §10** — land in the same commit if WS7 is executing first, per §10's sequencing). New `grapple/Grapple.java` (with `getPermission()`), `grapple/GrappleKey.java`, `grapple/config/GrappleAddon.java` (loads `items/grapple.yml`, calls `permissionRegistrar.accept(grapple.getPermission())` per item, mirrors `CarAddon.java:143-146`). New `item/GrappleConverter.java`, `item/GrappleItemSerializer.java` (no refresher, S2). Add `GadgetItemPredicates.GRAPPLE`. Add `grappleAddon(...)` to `GadgetFileConfig` (registers the `FileHandler`, B3) and `grappleConverter(...)`/`grappleItemSerializer(...)` to `GadgetModuleConfig` (mirrors the `carConverter`/`carItemSerializer` bean bodies, C8). **No `ItemVocabulary`, no `GadgetType` edit** (B1, C3). | M (half day) | `GrappleNbtIdentityTest` — mirrors `CarNbtIdentityTest` including its permission assertion (S4): round-trips a built stack through the serializer, asserts the predicate + NBT id survive, and asserts `grapple.getPermission()` equals `"gangland.grapples.<id>"` | commit: "Grapple item: YAML, NBT identity, keystone-item registration (ItemKind shared with WS7)" |
| **G2 — launch/land/pull mechanics** | `GrappleService` (cooldown map keyed `UUID`→expiry `long`, `isActive(Player)`, `start(Player, Location anchor)`, `cancel(Player)`, a per-tick `pull(Player)` applying a velocity vector toward the anchor capped at `Max_Pull_Speed`, auto-stops within `Arrival_Distance`). `GrappleLaunchListener` on `PlayerFishEvent`: ignore unless held item matches `GadgetItemPredicates.GRAPPLE`; check `player.hasPermission(grapple.getPermission())` before proceeding (mirrors `CarInteractListener.java:59`); on `State.FISHING` do nothing extra (reuse vanilla's own hook-flight physics — no custom projectile/raytracer); on `State.IN_GROUND` capture `event.getHook().getLocation()` as the anchor and call `GrappleService.start(...)`; cancel `State.CAUGHT_FISH`/`State.CAUGHT_ENTITY` transitions on a grapple item so nothing is actually fished. Pull ticks run on a sync repeating `Timer` (`Timer.start(false)` — touches player velocity, a Bukkit entity API, per the house async-timer rule). | L (1.5-2 days — velocity/arrival tuning is playtest-bound, does not converge in a single coding day, per the estimate check) | `GrappleServiceTest` — pure-logic test of cooldown gating and the capped-velocity/arrival-stop math, no Bukkit server needed (mock `Player`/`Location`) | commit: "Grapple launch/land/pull via vanilla FishHook physics" |
| **G3 — anti-abuse + corrected fall-damage design (does not reproduce GD-04)** | Line-of-sight check via `player.getWorld().rayTraceBlocks(eyeLocation, directionToAnchor, distance)` before starting the pull — a hit before the anchor cancels the attempt. `anchor.getChunk().isLoaded()` and `anchor.getWorld().getWorldBorder().isInside(anchor)` — either false cancels. Cancel-on-damage (`EntityDamageEvent` while active → `GrappleService.cancel(player)`), cancel-on-sneak (`PlayerToggleSneakEvent` while active), timeout (`Max_Duration_Ticks`, counted in the pull `Timer`). **Fall damage — corrected shape, not a mirror of `JetpackFallDamageListener`'s GD-04 bug:** `GrappleService` tracks two states, not one — `isActive(Player)` (true only during the live pull; cancelling fall damage here is legitimate, the player is being mechanically moved, not falling under gravity) and a separate one-shot `consumeLandingGrace(Player)` flag, set the instant a pull ends for *any* reason (arrival, timeout, cancel) and valid for `Fall_Damage_Grace_Ticks`. `GrappleFallDamageListener.onFallDamage` cancels `DamageCause.FALL` while `isActive(player)`, **or** cancels it once and *clears the flag* if `consumeLandingGrace(player)` returns true — so at most one fall-damage event per grapple use is ever cancelled after arrival, never a rolling window a player can chain by re-grappling inside it (the exact failure mode GD-04 represents on the jetpack). | M (half day, but real design work per the estimate check — not a copy-paste of the jetpack listener) | `GrappleFallDamageListenerTest` (written fresh — confirmed no `*FallDamage*Test*` exists among the module's 8 test files, genuinely red-first): asserts FALL cancelled while active; asserts FALL cancelled exactly once via the landing-grace flag and NOT a second time inside the same grace window; asserts FALL not cancelled once inactive and the grace flag consumed | commit: "Grapple anti-abuse (line-of-sight, chunk/border, cancel conditions) + one-shot fall-damage grace (does not reproduce GD-04)" |
| **G4 — give command + permission wiring** | `GrappleCommand`/`GrappleGiveCommand` mirroring `CarCommand`/`CarGiveCommand` exactly (Option A, §3b) — no permission check inside the give command body, matching car's verified real behaviour (C2). `commands.json`: add `grapple`, `grapple_give` (mirrors `car`/`car_give`, `commands.json:1-13`). | S (half day) | Extend `GadgetModuleTest` — correctly described (C7): it asserts the module's declared configuration/listener/command/repository packages match reality, so extending it means adding grapple's package declarations to that assertion, not a "command wiring" test | commit: "Grapple give command + commands.json" |
| **G-final — smoke, graph refresh, docs** | Console-harness smoke row: give grapple → launch at a wall within N blocks → confirm no pull (line-of-sight) → launch at open ground → confirm pull stops within `Arrival_Distance` → confirm cooldown blocks immediate re-launch → confirm damage mid-pull cancels it → confirm fall-damage grace fires exactly once on arrival, not on a second fall shortly after → 5-simultaneous-pulls smoke row (Risk 3). `mvn clean install` full reactor. `graphify update . --force` (previously uncosted — added per the estimate check). Update the session's memory/CLAUDE.md note per house convention. Optional: `documentation/FRONT-PAGE.md`'s gadget bullet list gains a grapple line beside the existing jetpack one. | S (half day) | smoke row `grapple-full-cycle` + `grapple-concurrent-pulls` | commit: "Grapple smoke pass + graph refresh + docs" |

## 5. Config, messages, permissions

| Item | Value / destination |
|---|---|
| `items/grapple.yml` (new, block style, `Capitalized_Underscore_Separated`, no inline `{}`) | see block below |
| `Messages` | None added to the shared `gangland-api` `Messages` enum, per the module-API contract (a module's new user-facing string goes in its own YAML). `GrappleMessageContract` sources strings from `items/grapple.yml`'s own `Messages:` block. |
| `commands.json` | new keys `grapple`, `grapple_give` (mirrors `car`/`car_give`, `commands.json:1-13`) |
| Command permission | Auto-derived, `gangland.command.grapple` (+ `.give`), same mechanism as every other gadget command (`Command.java:22/:54`) — not a discrepancy, not escalated to the user (C2) |
| Per-item permission | `gangland.grapples.<id>` — hand-written on `Grapple.getPermission()`, registered via `permissionRegistrar.accept(...)` from `GrappleAddon` (mirrors `Car.getPermission()`/`CarAddon.java:145`), enforced in `GrappleLaunchListener` before a launch is allowed to start a pull (mirrors `CarInteractListener.java:59`) |

```yaml
Grapple:
  Material: FISHING_ROD
  Display_Name: '&b&lGrappling Hook'
  Lore:
    - '&7Right-click to launch. Hooks a block within range and reels you in.'
    - '&7Sneak, take damage, or wait it out to cancel mid-pull.'
  Max_Distance: 25
  Max_Pull_Speed: 1.8
  Pull_Acceleration: 0.35
  Arrival_Distance: 1.5
  Cooldown_Seconds: 8
  Max_Duration_Ticks: 100
  Fall_Damage_Grace_Ticks: 40
  Require_Line_Of_Sight: true
```

(`Material` resolved through XMaterial per the house rule.)

## 6. Persistence

None needed. A grapple session is exactly as transient as a jetpack session — neither `JetpackSession` nor
`JetpackTask` has a repository (only parked cars persist, because a car is a placed world object that must survive
a restart). No new table, no migration, no `setDataSupplier` wiring.

## 7. Tests

| Test | Asserts | New or mirrors |
|---|---|---|
| `GrappleNbtIdentityTest` | build → serialize → predicate/NBT id round-trip, plus `getPermission()` equals `"gangland.grapples.<id>"` (S4) | mirrors `CarNbtIdentityTest.java` |
| `GrappleServiceTest` | cooldown gate blocks re-launch inside the window; pull velocity never exceeds `Max_Pull_Speed`; auto-stop fires within `Arrival_Distance` | new, pure-logic (no Bukkit server) |
| `GrappleFallDamageListenerTest` | FALL cancelled while active; FALL cancelled exactly once via the one-shot landing-grace flag, not a second time inside the same grace window; FALL not cancelled once inactive and the flag is consumed | new, fresh, red-first (no existing fall-damage test to mirror, confirmed — C5) |
| `GadgetModuleTest` (extend) | grapple's configuration/listener/command/repository package declarations match reality | extends the existing declaration-test shape (C7), not a command-wiring assertion |
| Smoke rows (console harness) | full launch→land→pull→arrive cycle; line-of-sight block; cooldown; cancel-on-damage; one-shot fall-damage grace; 5-simultaneous-pulls | new rows, `bartizan-split-2026-09-08/smoke/`'s style |

No docket-pinned tests apply (this is new feature work), but §11 lists the open bugs this design is constrained by.

## 8. Risks

| # | Risk | Mitigation | Rollback |
|---|---|---|---|
| 1 | `PlayerFishEvent` is also fired by genuine vanilla fishing rods; a predicate-check bug could break normal fishing server-wide | Predicate check is the first line of the listener; early-return leaves the event untouched for any non-grapple item; unit-test that a vanilla rod's event is never touched | Revert the listener registration; the item alone is inert |
| 2 | Cross-version behavior of `PlayerFishEvent.State` across the 1.16-1.21 floor is assumed stable, not tested on every revision | Smoke-test the floor (1.16) and the newest supported build before merging | Fall back to a custom raytrace-based launch (more code, pre-decided fallback) |
| 3 | Sync `Timer` ticking every active pull could add up under many concurrent players | `Max_Duration_Ticks` bounds worst case; cooldown limits frequency; smoke-test 5+ simultaneous pulls before the gate is called green | Lower `Max_Duration_Ticks` / add a server-wide concurrent-pull cap if profiling shows it |
| 4 | The corrected one-shot fall-damage grace has a subtle edge case (e.g. a second, unrelated fall starting exactly as the flag is consumed) | `GrappleFallDamageListenerTest` explicitly asserts the flag is consumed on first use, not time-window re-evaluated | Tighten `Fall_Damage_Grace_Ticks` toward 0 if abuse is found in smoke testing; the flag is one-shot regardless of the tick value |
| 5 | `ItemKind.GRAPPLE`/WS7's `ItemKind.JETPACK` edits landing in separate commits could conflict on the same enum file | Sequence per §10: land in the same commit if WS7 executes first (recommended), otherwise whoever lands second rebases a two-line diff | Trivial re-apply — a one-line enum addition |

## 9. Decisions for the user

### 9a. Structural decisions

| Decision | Recommendation | What changes if the other option wins |
|---|---|---|
| `Gadget` interface (K5) | Reject now (§3c) — no consumer needs polymorphic access to two gadget services | If overruled, extract `ChanneledGadgetSession` immediately and retrofit `JetpackService` to implement it — extra WS7 coordination |
| Give-command shape | **Resolved, not a live decision** — Option A (per-type), aligned with WS7's actual shipped shape (§3b) | N/A — both plans now agree; only remains an action item to confirm at merge time |
| Fuel vs. durability vs. neither | **Cooldown only.** `FuelService`'s API is generic (confirmed: `hasFuel`/`getFuelLevel`/`consumeFuel` all take `(Player, String fuelKey, ...)`, no `Wearable` anywhere), but its model fits continuous drain during sustained use (jetpack thrust), not a single instant trigger. Vanilla durability was rejected as unverified-safe against the item refresher (now moot per S2, since grapple has no refresher at all). `ponytail:` if the user wants scarcity later, wire `FuelService.consumeFuel(player, "grapple", 1)` per launch — but note the fuel-sink side is a **single predicate** (`FuelService.setFuelSinkPredicate`, `:50`) already spent on the jetpack (`GadgetModuleConfig`'s `jetpackService` bean), so a second fuelled gadget needs to *compose* the two predicates (e.g. `jetpackPredicate.or(grapplePredicate)`), not just add a second `setFuelSinkPredicate` call | If overruled toward fuel: G2 gains a fuel-item requirement, a `Fuel_Cost` YAML knob, and a predicate-composition step; toward durability: G1's serializer must handle `Damage` meta with no refresher-interaction safety net to fall back on |

### 9b. Shortlist — 6-8 further gadgets

| Gadget | Design (one paragraph) | Reused seams | Effort |
|---|---|---|---|
| **Parachute** | A wearable-slot item (chestplate, like the jetpack) that applies `XPotion.SLOW_FALLING` while equipped and airborne above a height threshold, auto-disarming on landing; single-use per fall, re-arms on touching ground. **Caveat (reviewer-confirmed):** this pattern directly inherits the open P1s it copies — GD-04-style fall-damage handling and GD-07 (wearable permission checked only on inventory clicks) both sit on the jetpack-equip pattern this item would reuse; it is only as cheap as claimed if built with the corrected fall-damage shape from §4 G3 from the start, not a literal mirror of `JetpackEquipListener`/`JetpackFallDamageListener`. | `JetpackEquipListener`'s equip-detection shape, the §4 G3 corrected fall-damage pattern (not the buggy one), the §3a item-registration pipeline. Cooldown-only, same reasoning as grapple. | S/M — ~1.5 executor-days, contingent on the caveat above |
| **Spike strip (car counter)** | A placeable ground item; any `VehicleEntity` driving over it takes a speed-debuff/skid effect, consumed after N triggers or a timer. | `VehicleMovementTask`/`VehicleRegistry` (car collision detection), the §3a pipeline for the placed item itself. | M — ~2.5 executor-days (new collision-check code inside the vehicle tick, the highest-novelty item on this list) |
| **Lockpick** | The *item* only: a durability-consuming tool that, when used on a lockable object, hands off to the safe-cracking minigame. The minigame itself is a **lootchest-api** concern per the roadmap (`documentation/FRONT-PAGE.md:360`, "actively in development"), not gadget's. | §3a pipeline for the item; hands off to lootchest-api's existing (partial) safe-cracking infrastructure rather than building a second minigame. | S for the item slice — ~1 executor-day; the full minigame is untracked here |
| **Smoke/flash device** | **Must not** be a Bartizan throwable — Bartizan already owns `ThrowableWeapon`/`ThrowableType`/`ThrowableData` (confirmed present in `bartizan-api/.../weapon/`), and reusing that puts a Bartizan weapon symbol on a *new* gadget's path, against K1's spirit. Instead: a plain vanilla `Snowball` launched via `player.launchProjectile(...)`, tagged with a `PersistentDataContainer` marker; on `ProjectileHitEvent`, apply `XPotion.BLINDNESS`/`XPotion.SLOWNESS` to entities in a radius + an `XParticle` smoke-particle cosmetic (both through XSeries, not raw `PotionEffectType`/`Particle` — C6). Zero Bartizan coupling. | Vanilla Bukkit projectile/PDC/potion/particle APIs via XSeries wrappers; §3a pipeline for the thrown item; Keystone `SoundEffect` for the pop sound. | M — ~2 executor-days |
| **Drone/camera** | A scouting item giving the player a temporary remote view. Highest-risk item on this list: a true third-person remote camera needs either packet manipulation (the same `PacketAdapter.relativeCameraRotation` seam Bartizan's recoil already uses) or `Player.setSpectatorTarget`, which vanilla only honors while the viewer is in spectator gamemode — awkward in survival. | Keystone's `PacketAdapter` seam if pursued; otherwise a much smaller "reveals nearby markers on a compass-like item" fallback. | L — ~5 executor-days, riskiest estimate on this list |
| **Disguise kit** | Changing a player's visible entity type/skin convincingly needs a disguise library (e.g. LibsDisguises) that is not currently a dependency anywhere in this repo — no existing seam to reuse. A no-new-dependency version is cosmetic-only and would not read as a real disguise to other players. | None existing | **Recommend dropping** — the one candidate on this list that would add an external dependency for a weak payoff |
| **Boombox/radio** | A placed marker (armor stand or similar) that loops a configurable sound to nearby players on an interval via Keystone's `SoundEffect` (XSound-backed). | Keystone `SoundEffect`, a sync repeating `Timer` (same house rule as grapple's pull tick), §3a pipeline for the placed item. | S/M — ~1.5 executor-days |
| **Getaway flare** | A thrown vanilla `Firework` that bursts into a colored particle beacon visible from a distance, marking a rally point — purely cosmetic, deliberately **not** wired into cops-n-crooks' NPC target-filter seam (which would create a `gadget → cops-n-crooks` dependency that doesn't exist today). | Vanilla `Firework`/`XParticle` APIs, §3a pipeline, cooldown-only per grapple's precedent. | S — ~1 executor-day |

### 9c. Recommended first three

1. **Grappling hook** — already fully designed above; ships regardless (mandatory deliverable, not a pick).
2. **Parachute** — cheapest reuse of an existing pattern, **but only cheap if built on the corrected fall-damage
   shape from §4 G3** rather than a literal mirror of the jetpack's current listener (reviewer caveat, now folded
   into the shortlist row above).
3. **Smoke/flash device** — delivers the "make the game fun" novelty the user explicitly asked for, at moderate
   effort, and proves a thrown gadget with zero Bartizan coupling before anyone reaches for
   `ThrowableWeapon` for a future item.

Deprioritized: spike strip (highest genuine-novelty risk, best attempted once the pattern has a couple of successful
gates behind it), lockpick (coupled to an unstarted, larger lootchest minigame), drone/camera and disguise kit
(real risk/new-dependency concerns, not just "later").

## 10. WS7 asks

- **Shared-edit files, coordinated (C3 corrected the list — `GadgetType` is NOT one of them):**
  `ItemKind.java` (WS7 adds `JETPACK`, WS8 adds `GRAPPLE`), `GadgetFileConfig.java` (WS7 adds a `jetpackAddon`-style
  `FileHandler` registration for `items/jetpacks.yml`, WS8 adds one for `items/grapple.yml`), `GadgetModuleConfig.java`
  (WS7 adds jetpack `@Bean` methods, WS8 adds grapple ones — cite by bean name, not line number, per C8),
  `GadgetItemPredicates.java` (both add a constant), `commands.json` (both add rows). None of these are behavior
  conflicts — each side adds independent entries — but whoever lands second should rebase a small diff rather than
  the two edits silently overwriting each other.
- **Give-command shape:** now resolved to Option A on both sides (§3b) — WS7's `JetpackGiveCommand` and WS8's
  `GrappleGiveCommand` should look like siblings; no further alignment needed beyond confirming at merge time.
- **`ItemKind` reactor rebuild:** if WS7 executes first and its G1 already forces a full `mvn clean install` for
  `ItemKind.JETPACK`, consider landing `ItemKind.GRAPPLE` in the *same* commit/gate to avoid a second full-reactor
  build for a two-line enum addition.
- **Sequencing:** confirmed by the review — WS7 first, WS8 second, both inside the same Gangland 0.9.2 branch (K6).
  WS8's steps have no hard code dependency on WS7 (grapple never imports `org.luckyraven.bartizan`), but running WS7
  to completion first keeps the shared-file edits (above) as sequential small diffs instead of a merge.

## 11. Docket

No existing bug-docket ids describe a *bug in this plan's own new code*, but this plan's design is explicitly
**constrained by** two open P1 rows on the pattern it partially mirrors:

| Id | Priority | Description | Relevance to this plan |
|---|---|---|---|
| GD-04 | P1 (open) | "An empty jetpack grants permanent fall immunity" — cancels `DamageCause.FALL` whenever `isActive`, with no fuel/recency gate (`triage/gadgets-cars-fuel-jetpack.txt:4`) | `JetpackFallDamageListener` is the shape §4 G3's `GrappleFallDamageListener` superficially resembles. WS8 does **not** reproduce it — the one-shot `consumeLandingGrace` design (§4 G3) is a deliberately different, bounded shape. WS7 leaves the original jetpack bug open (`WS7-gadget-ownership.md:205`) — this plan does not fix it either, it only avoids repeating it in new code |
| GD-05 | P1 (open) | "A zero-fuel jetpack still glides and steers" — same file family as GD-04 (`triage/gadgets-cars-fuel-jetpack.txt:5`) | Not directly applicable to grapple (no glide/steer mechanic), listed because it shares GD-04's root cause (a gate missing on a continuously-active session) — a pattern this plan's design is careful not to repeat elsewhere |

Per K8/CLAUDE.md, any *new* bug surfaced while implementing (e.g. a `PlayerFishEvent` cross-version quirk found
during G2/G3 smoke testing) gets its own `brainstorming/bug-docket-2026-09-06/triage/<slug>.txt` entry and a
`build_docket.py` rebuild.

## 12. Estimate

Grappling hook only (the shortlist is proposal-only, no steps costed):

| Gate | Size | Executor-days |
|---|---|---|
| G1 | M | 0.5 |
| G2 | L | 1.5-2.0 (playtest-bound velocity/arrival tuning, per the estimate check — not a single coding day) |
| G3 | M | 0.5 (real design work for the corrected fall-damage shape, not a mirror-and-done) |
| G4 | S | 0.5 |
| G-final | S | 0.5 (now includes `graphify update --force` + memory note, previously uncosted) |
| **Total** | | **≈4.5-5 executor-days**, Sonnet executor + Opus gate review per gate, one reactor build at a time |

Deleting G0 (S1) gave back 0.1 day; dropping the refresher (S2) and the registry (S3) gave back roughly another 0.3
day — netted against the additions above (reactor-wide build for `ItemKind`, realistic G2 tuning time, real G3
design work, previously-uncosted graph/memory/docket steps), the total moves up from the original ≈3.1 to ≈4.5-5,
matching the review's independent check. No `plugin.yml`/`gangland-build` edit is needed — gadget is already wired
in both.

## 13. Not verified

- **The exact permission node string Keystone's command framework emits for a nested `SubArgument`** (e.g. whether
  `/glw car give` really is `gangland.command.car.give` or some other composition) — confirmed the *namespace*
  (`Command.java:22`) and the *enforcement* (`:54`), not Keystone's `SubArgument` string-composition source. Does not
  affect the per-item permission conclusion (C2), which is independently confirmed. Confirm with a `graphify explain`
  on Keystone's `SubArgument` before publishing a user-facing permissions doc.
- **Cross-MC-version stability of `PlayerFishEvent.State` values across the full 1.16-1.21 support floor** — assumed
  stable from general Bukkit API knowledge, not checked against this repo's `Version` enum handling or tested on
  each supported build. Risk #2 (§8) covers the mitigation; the underlying assumption is still unverified.
- **Whether `FuelService.setFuelSinkPredicate`'s single-predicate limitation (C9) has already been worked around
  elsewhere in the reactor** (e.g. a composing wrapper predicate class that isn't the car/jetpack path) — not
  searched for in this revision; check before assuming a grapple fuel-sink addition needs new composition code from
  scratch.
- **Whether WS7's `ItemKind.JETPACK` edit and this plan's `ItemKind.GRAPPLE` edit will actually land in the same
  commit** (§10's suggestion) or as two sequential diffs — an orchestrator-level sequencing call, not something this
  plan can settle unilaterally.

- (2026-09-20, orchestrator) §3b claimed WS7 shipped no `jetpack_help` entry; the live `commands.json` has one (added in 0.9.2 G4 fix round), so G4 mirrored it with `grapple_help` — the car/jetpack/grapple help entries are the precedent for every per-type gadget command.
