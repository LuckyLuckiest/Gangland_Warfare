# Final review — Gangland 0.9.0 groups M, N, K-R, O + whole-stream runtime pass

Reviewer R-G-FINAL (Opus, feature-dev `code-reviewer`, read-only), 2026-09-09 ~13:45. Scope: commits `6d354b7f`
(M+N), `0ebd5da0` (K-R), `e2b9d82b` (O) on `0.9.0`, the K/L follow-ups, and a whole-stream pass for runtime hazards.
Transcribed by the orchestrator; orchestrator actions are marked **→**.

## Verdict: FAIL as reviewed — one bootstrap blocker, fixed before the next smoke batch

## Findings

### Blocker

**B-1 — `GadgetModuleConfig` takes a phase-produced bean in its constructor; the plugin cannot enable with the gadget
module deployed.** `gadget/config/GadgetModuleConfig.java:63` `public GadgetModuleConfig(Gangland gangland, FuelService
fuelService)`. `FuelService` is a `@Bean` (`FileConfig.java:129`); Keystone instantiates EVERY `@Configuration` before
any phase runs (`BeanFactory.java:219-233`, phases from `:251`) with only `GanglandContext`, `DependencyContainer`,
`Gangland` and `ModuleLoader` in the container, so `DependencyContainer.resolveConstructorParameters` throws and
`BeanFactory:229` rethrows `IllegalStateException: Failed to instantiate @Configuration class …GadgetModuleConfig`.
Every other configuration in the repo (eight core, nine module) takes only `Gangland`/`GanglandContext`; this outlier
came from T-KR2. No unit test constructs the bean graph, so the green reactor proved nothing. Fix: drop the parameter,
field and `@PostConstruct`; install the fuel-sink predicate inside the `jetpackService(FuelService, GadgetPhysicsConfig)`
bean. **→ Sent to executor X-G-D1 (in the tree), with a red-first test that constructs every configuration with only the
four bootstrap types available.**

### Major

**M-2 — three `@ListenerHandler` classes handle a Citizens event without a registration condition:**
`cops/listener/police/HandcuffBribeListener` (`NPCRightClickEvent`), `npcshops/listener/banker/BankerInteractListener`,
`npcshops/listener/trader/TraderInteractListener`. Without Citizens, `ListenerService.registerGuarded`'s `getMethods()`
raises `NoClassDefFoundError`, is caught and logged, then Bukkit's fallback registration fails the same way (SEVERE
`has failed to register events`); smoke row D7's `must_not_contain` `NoClassDefFoundError` trips, and banker/trader
right-click and the handcuff bribe are dead. Note: Bukkit's `createRegisteredListeners` does catch the error, so the T-M3
"whole-plugin crash" reading was overstated — the condition fix is still right. **→ In X-G-D1's sweep (D-fix-1).**

**M-3 — `smoke/scenarios.json` row D6 contradicted the descriptors as built** (expected `mail, turf, npcshops` / `3
loaded, 3 fault(s)`; turf cascades out through `Depends: [civilians]`). **→ Fixed: `loaded_modules: [mail, npcshops]`,
`Runtime modules: 2 loaded, 4 fault(s)`, faults `module.plugin.missing` + `module.dependency.missing`; the PENDING note
stays.**

### Minor

**m-4** — `README.md` said cops/civilians "fall back to unarmed AI" without Bartizan; as built they are skipped. **→ Reworded.**
**m-5** — `bartizan-integration.md` named a non-existent `ItemConfig.itemVocabularies(...)` and placed
`CivilianNpcFactory` in cops. **→ Corrected.**
**m-6** — stale pages survived group O: `documentation/features/{weapons,wearables}.md`, `developer/weapons.md`,
`tests/features/{weapons,wearables}.md`, plus `Cops.Starting_Ammo_Magazines` in two pages. **→ Pages removed, the two
lines dropped (docket #33 stays open for the deeper developer-internals pages).**
**m-7** — `CLAUDE.md` claimed `module.yml` files name Citizens (none does — it is a degradation gate) and the YAML list
omitted `items/cars.yml` and `turf/turf_powerups.yml`. **→ Corrected.**

## K/L follow-up table

| Item | Status | Verified |
|---|---|---|
| B1 T-K5 guard | fixed | `CopNpcFactory:80` guard; `CopsNCrooksModule:49-54` reports `npc.citizens.missing` once |
| B2 fuel sink | fixed in behaviour, wiring broken by B-1 | `FuelService:40-57`, `GadgetModuleConfig:75-87`, `FuelRefuelListenerTest:142-168` |
| M3 explosion once | fixed | `CarDamageListener:128` |
| M4 Citizens guards | partial | fixed in five classes; M-2's three remained |
| m5 ammo chain | fixed | zero source hits |

## What the phase-D console smoke must look for (Gangland side; complements `REVIEW-bartizan-FINAL.md` §4)

Every row: no `Failed to instantiate @Configuration class`; exactly one `Item vocabularies installed:` line per boot
(also after `/glw reload` in D8); `Found Citizens, linking...` before the first `Loaded module` line when Citizens is
present. Bartizan + Citizens rows: `Item vocabularies installed: [bartizan]`; D5 `Runtime modules: 6 loaded, 0
fault(s)` and six `Disabled module` lines on stop; grenade at a parked car = configured damage exactly once (if both
`VehicleDamageEvent` and `EntityDamageEvent` fire, the single-entry set needs a counter); right-click a banker and a
trader (the only positive proof the interact listeners registered). Bartizan absent (D6): `module.plugin.missing` ×3
(civilians, copsncrooks, gadget) + `module.dependency.missing` ×1 (turf) → `2 loaded, 4 fault(s)`; `/glw turf` must not
answer; `Item vocabularies installed: none …`; `/glw`, mail and npcshops probes answer. Citizens absent (D7):
`npc.citizens.missing` exactly four times (civilians, copsncrooks, turf, npcshops; not gadget/mail); `Runtime modules:
6 loaded, 4 fault(s)`; no `NoClassDefFoundError`, `Guarded registration failed for`, `has failed to register events for`;
`/glw turf|cops|trader` still answer.

## Not verifiable by the reviewer (no shell)

Whether Bukkit's listener registration catches the error on the target build (log noise vs crash — same fix); minecart
explosion event routing; B-1 is a source-level proof; jar contents; test counts after the fixes; graph freshness.
