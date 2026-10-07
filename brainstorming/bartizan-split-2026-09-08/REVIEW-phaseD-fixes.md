# Review — phase D fix commits (Keystone 1.9.1 `30cf528`, Gangland D-fix-1 `05996be6`)

Reviewer R-DFIX (Opus, feature-dev `code-reviewer`, read-only), 2026-09-09 ~16:30. Transcribed by the orchestrator;
orchestrator actions are marked **→** (all applied by executor X-DFIX-R, see the status table rows `D-fix-1-review`).

## Verdicts: Keystone PASS WITH FIXES · Gangland PASS WITH FIXES

The guard is correct and correctly narrow; every production fix in Gangland is behaviour-preserving and correctly
gated. The regression tests meant to pin them were likely vacuous under Surefire and definitely incomplete.

## Findings

**1 (critical, test) — the Citizens-blind classloader is probably not blind under Surefire.** `CitizensBlindScan`
filtered `java.class.path` entries whose path contains `citizens`; with Surefire's default manifest-only booter jar the
property is a single jar whose `Class-Path` manifest still reaches Citizens, so the filter removes nothing and the four
safety tests could pass regardless. (The executor's stash-based red runs did fail as expected in this environment, so the
loader was blind here — but not by construction.) **→ Name-based refusal (`loadClass` throws for
`net.citizensnpcs.*`) plus two self-checks: `Class.forName("net.citizensnpcs.api.npc.NPC", false, blind)` must throw and
the scan must find at least one class.**

**2 (important, test) — the scanner missed every `@Bean` whose declared return type is a contract interface**
(`TraderMessageContract` → `GanglandTraderMessages`, `BankerEconomyContract` → …): Keystone reflects on the runtime class.
**→ Concrete implementations of interface/abstract bean types are added from the scanned set; proven by temporarily
poisoning `GanglandTraderEconomy`.**

**3 (important, Keystone) — a `@Configuration` poisoned in its constructor still aborts the bootstrap**: the guard turns
the error into "no suitable constructor", which `BeanFactory` rethrows as `IllegalStateException` with a misleading
message. **→ Skip that one configuration with a WARN (or chain the cause); documented under "What this does not change";
pinned by a constructor-poison fixture case.**

**4 (minor, test)** — `beanReturnTypes` reflected outside the guard; a poisoned configuration errored the test instead of
failing it. **→ Wrapped.**

**5 (minor, Keystone)** — `NoClassDefFoundError: Could not initialize class X` (a static-init failure of a present class)
would be reported as an absent type. Not reachable from the six guarded calls, but cheap to close. **→ Rethrown.**

**6 (minor, docs)** — `FuelService.java:37` still named the deleted `@PostConstruct`; `NpcSupport.java:16` claimed no
Citizens-typed member while `registry()` returns `Optional<NPCRegistry>`. **→ Reworded.**

### Verified correct

`applyHealthBonus(Entity, …)` behaviour-identical; both nested `CitizensBridge` classes are separate class files reached
only behind `NpcSupport.available()`; `condition = "isCitizensAvailable"` is evaluated in `ListenerService` before
`createInstance`/`registerGuarded`; `GadgetModuleConfig` takes only `Gangland`, the predicate lands on the single
`FuelService` singleton and resolves `BartizanApi` per call; `@Bean` ordering parameters kept; pom changes are the
`gangland-core` test-jar at test scope only; versions consistent (Keystone `<revision>1.9.1`, Gangland
`<keystone.version>1.9.1`).

## Residual-risk checks with Citizens PRESENT (not yet exercised — for the user's in-game session)

1. The four Citizens-gated listeners must actually fire: right-click a trader and a banker, get handcuffed and
   right-click that cop, right-click a turf powerup NPC. If Citizens enables after Gangland, `isCitizensAvailable()` is
   false at scan time and all four are silently skipped ("right-click does nothing") — `softdepend` must order Citizens first.
2. Disable Citizens mid-session (`/plugman disable Citizens`) and damage an NPC: no `NoClassDefFoundError`; re-enabling
   Citizens does not re-register the gated listeners (scanned once per start).
3. With Citizens present, spawn a trader and a banker and shoot each: both keep spawn protection (`isShopNpc` covers
   both ids — the banker half is new); spawn a cop indoors and confirm the delayed spawn validation still destroys a
   clipped NPC one tick later.
