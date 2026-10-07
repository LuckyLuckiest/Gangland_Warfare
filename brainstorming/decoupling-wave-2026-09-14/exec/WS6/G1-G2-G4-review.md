# Review — Gangland 0.10.0 WS6 G1+G2+G4 (GanglandApi facade, events, docs) — 2026-09-23 (Sonnet, transcribed)
Verdict: FIX (1 Important, docs only).

## Spec table (condensed)
1 interface conversion ✅ (values unchanged; 31 static reads compile — spot-checked GangPromoteCommand:82,161, CopSpawnerInfoCommand:64, GanglandChatUtil:44,52; only the private ctor is gone; javadoc: resolve fresh, inert gangs()) · 2 GanglandApiImpl ✅ (4 mandatory params, no container, `gangs()` = the shared IdentityContractConfig.gangMembership() singleton) · 3 registration ✅ (ganglandPlaceholder idiom; all producers CONFIG-phase; `Gangland.java:61` unregisterAll first; `BeanFactory.reloadLifecycleBeans` (Keystone BeanFactory.java:333) never re-runs @Bean bodies → no double registration on reload — settles plan §13) · 4 tests ✅ (red-by-non-existence; registration test mirrors WiringConfigTest) · 5 G2 ✅ (3 api events; gang events in the module; additive) · 6 docs ⚠ F1 · 7 rebase low risk.

## Findings
**F1 Important** — `documentation/gangland-api.md` "Resolving the facade" chained `.map(...).orElse(null)` on `ServicesManager.getRegistration(...)`, which returns a nullable `RegisteredServiceProvider`, not an Optional (every real call site null-checks). Fixed inline by the orchestrator: `RegisteredServiceProvider<GanglandApi> rsp = …getRegistration(GanglandApi.class); GanglandApi api = rsp != null ? rsp.getProvider() : null;`.

## Cannot verify
924/0; the post-rebase textual merge (structurally non-overlapping with WS5 G5's doc hunks).
