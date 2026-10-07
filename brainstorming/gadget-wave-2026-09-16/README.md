# Gadget wave — 2026-09-16 (plan first; WS7 executes on the user's "go")

## The ask (user, 2026-09-16, verbatim intent)
- "I want to understand why the jetpack was moved to bartizan plugin. I understand that it is a wearable but I need
  it to be in gangland since it is part of gadgets."
- "We can't have a dependency of having it moved to Bartizan, then need Gangland Warfare then need gangland-gadgets
  module."
- "Do a proper scan of such coupling so that we have a proper place that hosts such feature and have a solution for
  them."
- "Give me a plan and combine it to the previous plans set so that you can start working on them."
- "The gadgets at the end needs to have other tools like a grappling hook, or some other functionalities to make
  the game fun."
- Process: lead agents that order specialized subagents; the cheapest model that does the job (Haiku census →
  Sonnet plans → Opus reviews). Fable orchestrates. Ponytail stance: smallest plan that works, delete over add.
  **No code is written during planning.**

## Why the jetpack is coupled to Bartizan today (orchestrator, from git + the Bartizan wave's records)
- Before 0.9.0 the jetpack was modelled as a **wearable**: a chestplate entry in the weapon module's
  `items/wearables.yml` with a `Jetpack:` block (Max_Fuel, thrust, glide…). Wearables (armour with traits such as
  bullet resistance) belonged to the weapon system.
- The Bartizan wave (2026-09-08, `brainstorming/bartizan-split-2026-09-08/architecture/PICK.md` = A2 + amendments)
  ruled "**Bartizan owns wearables**". `Wearable` gained a generic `Map<String,Object> extraTags()`; the jetpack
  scalars became `Extra_Tags:` (`jetpack_*` keys) in **Bartizan's** `bartizan-plugin/src/main/resources/items/wearables.yml`,
  and `WearableAddon.legacyJetpackToExtraTags(...)` (Bartizan) translates the old `Jetpack:` block. Bartizan's
  `Wearable.java:42-44` even documents "whether a wearable is a jetpack: test `extraTags().containsKey("fuel")`".
- The jetpack **behaviour** stayed in `gangland-gadget` (`jetpack/JetpackService|Session|Task`, five listeners
  under `listener/jetpack/`, `jetpack/packet/JetpackInputInterceptor`), but its **item identity and config** live in
  Bartizan, so gadget resolves `BartizanApi.wearables()` on every call and declares `module.yml` `Plugins: [Bartizan]`
  (fail-fast). Commit `7e512679` "Re-point gangland-gadget to Bartizan (gate L)" is where this landed.
- Consequence the user hit: a jetpack that "did not work" on the test server (0.9.1 wave, KS-CM-15 notes) was a
  stale `plugins/Bartizan/items/wearables.yml` — a Gangland gadget broken by another plugin's data file.
- The user's stated philosophy (decoupling artifact, decision WS2-D1 note): soft dependencies; "If the plugin itself
  was not there then no need for the features to appear at all. The idea is to remove coupling and have Gangland use
  the api layer."

## Orientation facts (verified 2026-09-16 morning)
| Repo | Path | Branch / rev | Notes |
|---|---|---|---|
| Gangland Warfare | `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]` | `0.9.1` (HEAD fb460b35, Keystone pin 1.9.2, Bartizan pin **0.1.0**) | graph fresh (graph.json newer than HEAD, 0 newer java files) |
| Bartizan | `E:\Programming\java\Bartizan` | `0.3.0` (HEAD 0e9e456, 2026-09-15) | api = `bartizan-api/src/main/java/org/luckyraven/bartizan/api/**` |
| Keystone | `E:\Programming\java\Keystone` | `phase-h9-host-api` = 1.9.2 | `keystone-item` (ItemDefinitions, ItemVocabulary SPI, converters/serializers/refreshers), `keystone-module` (`Plugins:`/`Depends:`, ReflectionGuard 1.9.1 skips a class whose signature names an absent type, fault `reflection.type.missing`) |

### Every Gangland file that names `org.luckyraven.bartizan` (grep, 15 files — the census starts here)
- gangland-gadget (5 + 1 test): `config/GadgetModuleConfig`, `jetpack/JetpackService`, `jetpack/JetpackSession`,
  `jetpack/JetpackTask`, `listener/car/CarDamageListener` (`WeaponRaytraceImpactEvent`), test `jetpack/JetpackTaskConsumptionRateTest`.
- gangland-civilians (4 + 1 test): `CiviliansModuleConfig`, `listener/gang/GangAllyWeaponImpactListener`,
  `npc/combat/BartizanNpcWeapons` (`NpcWeaponFactory`/`NpcWeaponController`), `npc/combat/GanglandCombatEligibility`
  (`CombatEligibility`), test `BartizanNpcWeaponsTest`.
- cops-n-crooks (4): `listener/detainment/CopListener`, `listener/NpcDamageUnprotectListener`,
  `listener/police/DetainmentListener`, `listener/turf/TurfFriendlyFireListener` (weapon events: shoot / entity-damage /
  raytrace-impact, `WeaponCatalog`, `MeleeWeapon`, `ScopeData`).
- Poms: root `bartizan.version` 0.1.0; `gangland-gadget`, `gangland-civilians`, `cops-n-crooks` depend on
  `bartizan-api` at `provided`. `module.yml`: all three carry `Plugins: [Bartizan]`; mail/turf/npc-shops do not.
- `gangland-api` `Settings.java:804` only mentions Bartizan in a comment (`weapon:` sign prefix).

### Gadget module today (66 files) — the pattern the jetpack must copy
- Cars are already gadget-owned items: `items/cars.yml` in the module jar, `car/config/CarAddon`, `item/CarItemSerializer`,
  `item/GadgetItemPredicates`, `sign/CarSignContribution` (car-buy/car-sell signs), commands `command/Car*Command`
  (`/glw car give|info|list`), `GadgetType {CAR, WEARABLE, JETPACK}`. **Verify in the census how the car item enters
  the core item registries (an `ItemVocabulary`? a converter?) — the jetpack should enter the same way.**
- Fuel is Gangland-owned: `gangland-infra/gangland-item` fuel system (`FuelService`, `FuelContract.isFuelSink`,
  `FuelKey`); commit `0ebd5da0` added the "gadget fuel sink".
- Physics/config: `config/GadgetPhysicsConfig`, `contract/GadgetPhysicsConfigImpl`, `config/GadgetFileConfig`.

### Bartizan-side residue that is Gangland-specific (candidates to leave Bartizan)
- `wearable/WearableAddon.legacyJetpackToExtraTags` (+ `WearableAddonLegacyJetpackTest`), the jetpack entry in
  `items/wearables.yml`, `Wearable.java` javadoc lines 42-44/119-122, `item/WearableRefresher` "jetpack" mention,
  `documentation/migration.md` `Jetpack:`→`Extra_Tags:` section. `Extra_Tags` itself is generic and may stay.

### Previous plan set this wave joins
- `brainstorming/decoupling-wave-2026-09-14/PLAN.md` (WS1–WS6, rulings R1–R9, order G0 → WS1 → WS2 → WS4 consumer →
  cutover → WS3 → WS5 → WS6, estimate ≈ 46–50 executor-days), `PLANNER-BRIEF.md` (contracts C1–C8 + plan template —
  **use its template headings**), `REVIEWER-BRIEF.md` (review template). Artifact
  https://claude.ai/code/artifact/96251df4-f5b3-49a6-9f5f-9afb94407faf, db `decisions` (14 rows filled by the user
  on 2026-09-14/15). R9: gadget/civilians/cops need no `Depends: [gang]` (one core-owned `GangMembership` holder).
- Bartizan wave decisions log `brainstorming/bartizan-split-2026-09-08/README.md` — open item "Bartizan-absent blast
  radius (civilians + turf + cops all dropped)".
- Bug docket rules in the repo `CLAUDE.md` (every bug found → triage entry; existing ids first).

## Workstreams
- **WS7 — Gadget ownership + Bartizan soft-coupling.** The jetpack becomes a gadget-owned item (definition, YAML,
  serializer/refresher, give command, equip detection) with zero Bartizan symbols on its path; Bartizan becomes a
  **soft** dependency of gadget (only the car weapon-damage listener stays, conditionally registered); the jetpack
  residue leaves Bartizan (Bartizan minor bump + migration note); the census classifies every civilians/cops edge.
- **WS8 — Gadget catalogue.** A reusable per-gadget pattern proven by WS7, then the **grappling hook** as the first new
  gadget, then a shortlist of further gadgets (design paragraph + effort each) for the user to pick from.

## Contracts fixed by the orchestrator (plan against them; flag if one breaks)
- **K1** Jetpack definition, config and behaviour are all in `gangland-gadget`; no `org.luckyraven.bartizan` import on
  the jetpack path; the jetpack YAML ships in the gadget jar at the data-folder path (like `items/cars.yml`).
- **K2** Gadget's `module.yml` drops `Plugins: [Bartizan]`. The one remaining edge (`CarDamageListener`) becomes its own
  conditional listener class (`@ListenerHandler(condition = ...)` like the Citizens rule in CLAUDE.md; verify what
  condition method exists for Bartizan, e.g. a `Settings.isBartizanAvailable()`, or propose the smallest addition —
  additive api change only). `BartizanApi` is still resolved from the `ServicesManager` on every call, never cached.
- **K3** Nothing Gangland-specific stays in Bartizan: the legacy `Jetpack:` parsing, the jetpack `wearables.yml` entry
  and the jetpack javadoc/docs leave with a migration note (Bartizan 0.3.0 → 0.4.0). Generic `Extra_Tags` may stay.
  Gangland's Bartizan pin moves 0.1.0 → the new version.
- **K4** The gadget item pattern is reusable: keystone-item `ItemDefinitions`/`ItemBuilder` + the gadget module's item
  vocabulary (so signs, shops and loot chests reach `gadget:`/`jetpack:` items through the core registries without a
  new seam), own YAML per gadget type, a serializer + refresher, `/glw gadget give <id>` (or the existing `/glw car`
  shape — planner picks the smaller), `commands.json` entries, permissions `gangland.gadget.*`.
- **K5** Reuse first: `FuelService`/`FuelKey` in `gangland-item`, the car item classes, `GadgetType`. No abstraction
  with one implementation; a `Gadget` interface is justified only once WS8 gives it a second implementor.
- **K6** Branch/version: the orchestrator's default is Gangland **0.9.2** on a new branch off `0.9.1`, executed
  **before** the decoupling wave's G0 (WS7 needs no `GanglandApi` change — say so explicitly, or flag the addition).
  Bartizan 0.4.0 on its own branch. Keystone untouched unless the census finds a real gap (then Keystone 1.9.3, additive).
- **K7** civilians/cops Bartizan edges: the census classifies each as *weapon-inherent* (an NPC holds/fires a Bartizan
  weapon; a weapon event) or *replaceable* (a plain Bukkit/Keystone seam would do). The plan proposes soft vs hard
  per module but does **not** decide it — it is a user decision (`WS7-D<n>`) carrying the Bartizan-absent blast-radius
  question. Turf's `Depends: [civilians]` transitive Bartizan reach is part of that decision.
- **K8** Every bug found on the way (e.g. a listener that NPEs when Bartizan is absent) is a docket entry
  (`brainstorming/bug-docket-2026-09-06/triage/<slug>.txt`), never only prose in a plan.
- **K9** Estimates in executor-days per gate, Sonnet executors, Opus gate reviews, smoke rows on the console harness
  (`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`) — same execution process as PLAN.md §12.

## Outputs of this wave
- `census/C1-gadget.md`, `census/C2-npc-modules.md`, `census/C3-bartizan-residue.md` (Haiku, consolidated by the lead).
- `plans/WS7-gadget-ownership.md`, `plans/WS8-gadget-catalogue.md` (Sonnet, PLANNER-BRIEF template).
- `reviews/REVIEW-WS7.md`, `reviews/REVIEW-WS8.md` (Opus, REVIEWER-BRIEF template) + fix passes recorded as §0b in
  each plan.
- `SUMMARY.md` (lead): verdicts, decisions for the user, estimate, what the orchestrator must merge into
  `decoupling-wave-2026-09-14/PLAN.md` and the board.
