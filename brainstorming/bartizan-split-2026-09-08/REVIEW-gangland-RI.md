# Gate review G-R / I — Gangland 0.9.0 review fixes and the turf-NPC move

Reviewer R-G-RI (Opus, feature-dev `code-reviewer`, read-only), 2026-09-09 ~02:55. Reviewed commits `fbe351dc` (group
G-R) and `538950e1` (group I; some renames absorbed by the docket commit `3c6497fa`) on `0.9.0`. Transcribed by the
orchestrator; orchestrator actions are marked **→**.

## Verdict: G-R PASS · I PASS WITH FIXES

G-R (T-R1…T-R5) is faithful to the D–G rulings; no correctness problem in any of the five fixes. Group I is
mechanically clean inside turf (zero `copsncrooks` imports, table name and bean parameter lists preserved,
`GarrisonDeployListener` reproduces the deleted bridge exactly), but the deletion of `TurfNpcsModuleConfig` left a live
reference in cops.

## Findings

### Major

**I-1 — `TurfNpcsModuleConfig` is deleted but still imported and registered by cops-n-crooks.**
`cops-n-crooks/.../CopsNCrooksModule.java:9,:32` and `CopsNCrooksModuleTest.java:11,:39`. Gate I never builds cops, so it
was never compiled; it sits in the module's `Main` class, so even compiled it would fail `configure()` with
`NoClassDefFoundError` → `module.configure.failed`. Fix: drop the import and the `.configuration(...)` line; reduce the
test's expected list to the five surviving configurations. **→ Task T-IR1 (with group K).**

**I-2 — `TurfPowerupManager.getByEntity:116` reaches `CitizensAPI` with no `NpcSupport` guard**, and its caller is a plain
`EntityDamageByEntityEvent` handler (`TurfFriendlyFireListener.onDamage` → `resolveTurfId`), so on a Citizens-less server
every mob hit throws. Inherited from 0.8.4, but T-I6's contract and smoke row D7 say turf survives without Citizens. Fix:
`if (!NpcSupport.available()) return null;` first line. Secondary (lower confidence): `TurfPowerupInteractListener`
handles `net.citizensnpcs.api.event.NPCRightClickEvent`; Bukkit resolves handler parameter types at registration, so the
listener scan itself may throw without Citizens — needs the D7 smoke row. **→ T-IR2 (guard now; the listener question goes
to the D7 smoke).**

**I-3 — `Depends: [civilians]` makes all turf gameplay unavailable whenever civilians is skipped**, i.e. on every server
without Bartizan (civilians declares `Plugins: [Bartizan]`). Correct per PICK, but the plan's own smoke row D6
(`gangland-0.9.0.md:1575`: "mail, turf, npcshops load → 3 loaded, 3 faults; `/glw turf` still answers") states the
opposite. Fix: (a) correct D6 to `2 loaded, 4 fault(s)` with turf skipped and document it in `migration-0.9.0.md`, or (b)
keep a thin turf-side seam for the four civilian types turf consumes so turf's `Depends:` can drop to none. (a) is the
smaller change. **→ Same decision as review H m8 — with the user (README decisions log). D6 annotated as conditional.**

### Minor

**I-4** — `documentation/module-loader.md:144,:153,:156` still list `TurfNpcContracts`/`TurfNpcContract` and
`TurfPowerupNpcContribution` as live (T-I4's "grep is empty" claim covered Java only). **→ T-IR3 (docs; group O sweep).**
**I-5** — `TurfModule`'s class javadoc says `TurfModuleFileConfig` registers only `turf_powerups.yml` and that
`TurfModuleConfig` holds 21 beans plus the deleted holder; `TurfNpcsConfigLoader:18` says the file feeds "the cops-n-crooks
NPC managers". **→ T-IR4.**
**G-R-1** — `BaseTradeSignSimilarityTest` pins two of the helper's four branches; case 2 would not catch a regression to bare
`isSimilar` (both stacks are plain sticks). Fix: assert a unique-tagged stack and a plain stack of the same material are
NOT the same. **→ T-IR5.**

### Verified clean

T-R1's four branches exactly as specified, both sign classes route through the helper, test registry mirrors
`ItemConfig`; T-R2's fold in `GanglandContext.installItemVocabularies()` via `instantiate(this::installItemVocabularies)`
(`BeanFactory:288-292` confirms after-all-phases-before-`@PostConstruct`), registries resolved at call time, both log
strings byte-identical, `ItemConfig` clean; T-R3 guard shape and fall-through; T-R4 tier order; T-R5 assertions. The
`gangland-core` test-jar is the right vehicle (`keystone-testkit` deliberately does not stub `Registry`; the test-jar is a
documented house pattern, test scope). Group I hygiene: no `copsncrooks` in turf, pom without cops/Bartizan,
`gangland-civilians` provided, table definition byte-identical, `setDataSupplier` kept, nine folded beans with verbatim
parameter lists and concrete return types, the powerup command added in the same seven-argument shape,
`npc.citizens.missing` reported once, `turf_npcs.yml` KERNEL-registered through the module classloader and absent from
cops, ids resolve, key counts turf 17 / cops 31, purity intact.

## What a server sees with turf deployed but civilians absent

Nothing turf-related loads: one `module.dependency.missing` fault, no `/glw turf` tree, no capture/contribution/garrison/
income/inactivity logic, no turf repositories registered (tables dormant). Turf gameplay is coupled to a weapons plugin
through two hops (turf → civilians → Bartizan).

## Not verifiable by the reviewer (no shell)

Cops' compile-error delta; whether `registerEvents` throws on the missing `NPCRightClickEvent` class; the gate numbers;
byte equivalence of the 15 moved files not diffed.
