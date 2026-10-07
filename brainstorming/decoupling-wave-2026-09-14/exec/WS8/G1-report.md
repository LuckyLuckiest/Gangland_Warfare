# WS8 G1 report — 2026-09-20

Status: DONE

## What changed

Executed entirely by me in the worktree (`E:\Programming\java\wt\gangland-0.9.2`, branch `0.9.2`), no subagents —
by the time I had read `Jetpack`/`JetpackKey`/`JetpackAddon`/`JetpackConverter`/`JetpackItemSerializer`/
`GadgetItemPredicates`/`GadgetFileConfig`/`GadgetModuleConfig`/`CarAddon`/`Car`/`CarKey`/`ItemKind`/`jetpacks.yml`/
`JetpackAddonLoadTest`/`JetpackNbtIdentityTest`/`CarNbtIdentityTest` to nail the exact mirror shape, writing the
files myself was the shorter path than re-explaining all of it to a subagent.

1. `gangland-infra/gangland-item/.../item/ItemKind.java` (edit) — added `GRAPPLE("grapple")` beside `JETPACK`
   (WS7's shared-edit slot, per plan §10; WS7's `ItemKind.JETPACK` already landed at `03e60815`, so this is the
   sequential second diff, not a same-commit merge).
2. `gangland-features/gangland-gadget/.../grapple/Grapple.java` (new) — domain entity mirroring `Jetpack`
   (itself mirroring `Car`): identity fields + the full mechanics-knob surface (`maxDistance`, `maxPullSpeed`,
   `pullAcceleration`, `arrivalDistance`, `cooldownSeconds`, `maxDurationTicks`, `fallDamageGraceTicks`,
   `requireLineOfSight`) read directly off this object by G2/G3, same pattern as Jetpack's physics fields. No
   fuel/durability fields, no Bartizan bridge (WS8-D1). Static `isGrappleItem`/`getGrappleId`, `getPermission()` →
   `"gangland.grapples.<id>"`, `buildItem()`/`buildItem(Player)` stamping only `GRAPPLE_ID`.
3. `.../grapple/GrappleKey.java` (new) — one-constant NBT enum, `GRAPPLE_ID("grapple")`.
4. `.../grapple/config/GrappleAddon.java` (new) — loads `items/grapples.yml`, folds the registry role directly in
   (no separate `GrappleManager`, mirrors `JetpackAddon`'s S2-driven shape). Mandatory keys: `Material` +
   `Display_Name` only (mirrors `CarAddon`'s check exactly — grapple has no fuel-critical field like Jetpack's
   `Fuel_Key`/GD-12, so there's no equivalent bug this needs to guard against); every mechanics knob defaults the
   same way Car's Vehicle/Fuel/Repair sections do. Registers the per-item permission at load
   (`permissionRegistrar.accept(grapple.getPermission())`).
5. `.../item/GrappleConverter.java` (new) — mirrors `JetpackConverter` exactly (takes `GrappleAddon`, not a
   separate manager).
6. `.../item/GrappleItemSerializer.java` (new) — mirrors `CarItemSerializer`/`JetpackItemSerializer`, `kind()` →
   `ItemKind.GRAPPLE`.
7. `.../item/GadgetItemPredicates.java` (edit) — added `GRAPPLE` predicate (`GrappleKey.GRAPPLE_ID` tag check).
8. `.../config/GadgetFileConfig.java` (edit) — added `grappleAddon(...)` FILE-phase `@Bean`, identical shape to
   `carAddon`/`jetpackAddon` (`FileHandler(plugin, "grapples", "items", ".yml", moduleLoader.classLoader())`).
9. `.../config/GadgetModuleConfig.java` (edit) — added `grappleConverter(...)`/`grappleItemSerializer(...)` beans,
   registered at **default priority**, mirroring `carConverter`/`carItemSerializer`'s bean bodies (not Jetpack's
   priority-20 escalation — see Deviations). No refresher bean (S2).
10. `gangland-features/gangland-gadget/.../resources/items/grapples.yml` (new) — one `grapple:` entry, house YAML
    style (block maps, `Capitalized_Underscore_Separated` keys, no literal `§`), `Material: FISHING_ROD`.
11. New tests: `GrappleAddonLoadTest` (4 cases — missing-Display_Name/missing-Material skip, defaults-only round
    trip, full-field round-trip) and `GrappleNbtIdentityTest` (6 cases, mirrors `JetpackNbtIdentityTest` including
    the S4 permission assertion and cross-predicate mutual-exclusivity against both `CAR` and `JETPACK`).

## Deviations from the plan

1. **File name: `items/grapples.yml` (plural), not `items/grapple.yml`.** The binding plan document
   (WS8-gadget-catalogue.md §2/§4/§5) writes the singular form throughout, but both `PLAN.md` §9b and this stream's
   own `BRIEF.md` G1 line say `items/grapples.yml`, and the actual house convention is a plural collection-file
   name regardless of entry count (`items/cars.yml`, `items/jetpacks.yml` — both currently hold few entries).
   Went with the plural form and the `FileHandler`/`FileManager` key `"grapples"` to match precedent and two of
   the three binding sources; noting the plan's singular spelling as the outlier.
2. **Converter/serializer priority: default, not 20.** The task brief's shorthand said "priority 20 like the
   jetpack's," but the plan text I was told is binding (§2/§4 G1, C8) explicitly says grapple's beans "mirror the
   `carConverter`/`carItemSerializer` bean bodies" and drops the refresher (S2) — Jetpack's priority-20 escalation
   exists solely to outrank Bartizan's wearable serializer/refresher on a Bartizan-tagged jetpack stack (a
   collision grapple cannot have — nothing stamps a `wearable` tag on it). Registered at the registry's default
   priority, same as `Car`, per the plan's explicit bean-body citation. Flagging this explicitly since it reads as
   a discrepancy between the brief's one-line gloss and the plan's detailed text — happy to change if the
   orchestrator actually wants priority 20 reserved defensively.
3. Mandatory-key validation is `Material`/`Display_Name` only (mirrors `Car`, not `Jetpack`'s stricter
   `Fuel_Key`/`Max_Fuel>0` checks) — there is no fuel field to validate (WS8-D1) and no docket bug analogous to
   GD-12 motivating extra strictness on the numeric mechanics knobs, so they default like Car's Vehicle/Fuel/Repair
   sections.

Everything else matches the plan's G1 row and file tree exactly (no `ItemVocabulary`, no `GadgetType` edit, no
`GrappleItemRefresher`).

## Side check — GD-20 jetpack half (2 min, requested by the orchestrator)

Re-read `Jetpack.java` and `JetpackAddon.loadJetpacks` in full. **Confirmed: no glide/descent-rate field exists.**
`Jetpack`'s only physics fields are `ascendPower`, `maxSpeedY`, `fuelConsumptionRate` — no `glide`/`descent`-named
field anywhere on the class, and `JetpackAddon` parses only `Ascend_Power`/`Max_Speed_Y`/`Fuel_Consumption_Rate`
from YAML (`jetpacks.yml` has no such key either). This matches WS7's own commit message on `4d4ffa28`
("GD-20's jetpack half moot (no glide-descent knob)") — independently re-verified against current source, not
just taken on the commit message's word. Nothing to fix or flag; GD-20's jetpack half remains moot.

## Red-first evidence

New feature code (no pre-existing behavior to regress), so "red" means: temporarily removed `Grapple.java`,
`GrappleKey.java`, `GrappleAddon.java`, `GrappleConverter.java`, `GrappleItemSerializer.java` (renamed to `.bak`,
restored after) and confirmed the module fails to compile without them (`GadgetItemPredicates`/`GadgetFileConfig`/
`GadgetModuleConfig` all reference the missing classes) — proving `GrappleNbtIdentityTest`/`GrappleAddonLoadTest`
genuinely exercise new code, not a vacuous pass.

- Red command: `mvn test -pl gangland-features/gangland-gadget -am -Dtest=GrappleNbtIdentityTest,GrappleAddonLoadTest -Dsurefire.failIfNoSpecifiedTests=false`
- Red line: `[ERROR] .../GadgetItemPredicates.java:[5,46] package org.luckyraven.gangland.gadget.grapple does not exist` (+ 8 more `cannot find symbol` errors across `GadgetFileConfig`/`GadgetModuleConfig`) — `BUILD FAILURE`.
- Green command: same command after restoring the files.
- Green line: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`.

## Build

Final command: `mvn clean install` (full reactor, worktree root).
`BUILD SUCCESS`, all 22 reactor modules `SUCCESS` (Reactor Summary).
`gangland-gadget` module test run (`mvn test -pl gangland-features/gangland-gadget -am`): `Tests run: 84,
Failures: 0, Errors: 0, Skipped: 0` (includes the 10 new grapple tests: 4 `GrappleAddonLoadTest` + 6
`GrappleNbtIdentityTest`), plus green totals across every upstream module the `-am` flag pulled in (`gangland-core`
5, `scoreboard-api` 5, `gangland` 119, `gangland-item` 43, `sign-api` 63, `shop-api` 59 — all `Failures: 0,
Errors: 0`).

## Docket ids touched

None fixed at this gate (new feature work, no bug being fixed). GD-20 (jetpack half) re-confirmed moot, not newly
touched — see side check above.

Docket candidates: none noticed.

## Subagents used

None. Read enough of the WS7 precedent myself that writing the mirror directly was faster and lower-risk than
re-briefing a subagent on the same material (LEAD-RULES permits but does not mandate subagent use).

## Concerns / open questions

1. Flagging deviation #2 (converter/serializer priority) explicitly for the orchestrator to confirm — the brief's
   gate description and the plan's detailed text disagree, and I went with the plan (default priority, no
   refresher) as the more authoritative, more recently reviewed source.
2. Deviation #1 (`grapples.yml` plural) — same kind of brief-vs-plan mismatch, resolved the same direction (two of
   three sources + house convention agree on plural).
3. G2/G3 (`GrappleService`, `GrappleLaunchListener`, anti-abuse, fall-damage) are not started — `Grapple` already
   carries every mechanics knob G2/G3 will need (`maxDistance`, `maxPullSpeed`, `pullAcceleration`,
   `arrivalDistance`, `cooldownSeconds`, `maxDurationTicks`, `fallDamageGraceTicks`, `requireLineOfSight`), so G2
   should not need to re-touch `GrappleAddon`'s YAML parsing.
