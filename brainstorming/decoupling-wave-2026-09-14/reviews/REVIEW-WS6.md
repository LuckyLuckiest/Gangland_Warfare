# REVIEW WS6 — Improve `gangland-api` (Bartizan-style facade)

Verdict: **PASS WITH FIXES**

The shape is right and the ponytail argument is genuinely load-bearing: the facade is ~5 accessors over contracts
that already exist, and the plan correctly refuses `shopRegistry()`/`placeholders()`/`wanted()`. Three things stop
an executor cold: the plan's §3/§9 D3/§10b still carry the pre-R7 placement of `GangLookupContract` (which cannot
compile), two contracts the facade takes as constructor params are not in `gangland-api` today, and the
`unregisterAll` placement as written leaks a provider on a failed enable. Everything else is corrections, two real
deletions, and an estimate that is ~40% light.

Verification note: `graphify affected "GanglandApi"` returns **"No affected nodes found"** (depth 2, all relations)
— the extractor records no edges into a constants-only class, so the graph is silent on this workstream's central
type. Every call-site claim below is grep evidence, which is what the plan used too; it should say so rather than
opening with "graphify … oriented every claim".

## Blockers (must fix before executors start)

- **B1. Compile-direction break: `gangland-api` cannot name `GangLookupContract` while WS5 owns it.** §3 row
  "`gangs()`/`ranks()`", §9 D3 and §10b all state "`GangLookupContract`/`RankLookupContract` **move into the
  `gangland-gang` module** … **WS5 wins on placement**", and step 3 puts `Optional<GangLookupContract> gangs()` on
  the `GanglandApi` interface in `gangland-api`. WS5 confirms its half — `plans/WS5-gang.md` §3 row 3: "moves into
  the gang module; `GangManager implements GangLookupContract` directly". `gangland-api/pom.xml:20-93` declares no
  module artifact and cannot (repo `CLAUDE.md` "Two tiers": a module depends on `gangland-api` at `provided`, never
  the reverse), so `gangland-api` would not compile. **Required change:** adopt **R7** verbatim — the two
  *interfaces* land in `gangland-api` (`contract/` package, beside WS5's `MembershipLookupContract`) when
  `gangland-domain` is deleted; the gang module ships only the implementations; the facade keeps its lazy
  `Optional` per-call resolution. Rewrite §3's "Reconciliation with WS5" note, §9 D3 and §10b bullet 2 — all three
  currently argue the opposite. WS5 §3 rows 3/4 need the matching edit (orchestrator's call, not WS6's).

- **B2. `UserLookupContract` is not in `gangland-api` today.** §3 row `users()` and step 4 take it as a *mandatory*
  constructor param, citing WS5 §3 row 1's "**stays in `gangland-api`**". It lives at
  `gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland/gang/contract/UserLookupContract.java`
  (verified by `find`; same for `RankLookupContract`, `GangLookupContract`, `PermissionRegistryContract`). "Stays"
  is wrong in both plans — WS5 must *move* it. **Required change:** G0 gains an explicit precondition —
  "`UserLookupContract` (and, per B1, `GangLookupContract`/`RankLookupContract`/`PermissionRegistryContract`)
  compile from `gangland-api`; if WS5 has not landed them, WS6 G1 does not start." Without it G1's
  `mvn -pl gangland-api,gangland-impl -am` cannot be green on its own.

- **B3. `unregisterAll` placement as written leaks a dead provider.** Step 6 says add it "before/alongside
  `new ShutdownSequence(context).run()`". That call is at `Gangland.java:87`, *after* the early return at
  `Gangland.java:82` (`if (context == null) return;` — "onDisable() also runs when onEnable() never completed").
  A bootstrap that fails after `WiringConfig`'s CONFIG-phase bean has already registered the service would leave a
  provider pointing at a half-built plugin. Bartizan hit exactly this and fixed it by putting the call first:
  `Bartizan.java:44-48` has `unregisterAll(this)` at `:47`, *before* `if (context == null) return;` at `:50`, with
  the comment "a disable/enable cycle must not leave dead providers behind". **Required change:** the line is the
  **first statement** of `onDisable()`, and step 6 says so.

## Corrections (fix in place)

- **C1. `DependencyContainer.tryGet` does not exist — and the stated fallback is wrong.** Risk 3 and §13 bullet 2
  say "a throwing `get(Class)` wrapped in try/catch is the fallback". `DependencyContainer.getInstance(Class)`
  (`keystone-bean/.../autowire/DependencyContainer.java:53-62`) returns **`null`**, never throws; `hasInstance` is
  at `:88`. So `gangs()` is `Optional.ofNullable(container.getInstance(GangLookupContract.class))` — one line,
  **no Keystone ask, no try/catch**. Delete Risk 3 and the §13 bullet; fix step 4's signature.
- **C2. "`Gangland.java` has no `ServicesManager` call at all — confirmed by direct read" is false.**
  `Gangland.java:188` does `getServer().getServicesManager().getRegistration(Economy.class)`. The *conclusion*
  survives (Gangland registers nothing today; the only other use is a read at `GanglandContext.java:215` for
  `ItemVocabulary`), but the sentence is wrong and should say "registers nothing today".
- **C3. Call-site count and where they live.** `grep -rn 'GanglandApi\.'` across the reactor = **32 hits in 26
  files**: `gangland-api` 3, `gangland-impl` 15, **`gangland-features` 8** (cops-n-crooks 5, civilians 2, turf 1,
  e.g. `CopSpawnerInfoCommand.java:64`, `CopsNCrooksModuleConfig.java:140`). No `new GanglandApi`, no
  `GanglandApi.class`, no string-literal reflection anywhere. Risk 1's "re-grep `gangland-features/*` … none
  expected" is wrong — there are eight, all static-field reads, so the interface conversion still compiles. Fix
  the "~24" and the expectation.
- **C4. The service table does not exist.** Step 14 says "service table (**§7 below**)" — §7 is *Tests*. Add a
  real §7-style table and make it the doc's centre, matching `bartizan-api.md:35` ("Service table"). It must name:
  `GanglandApi` (published by `WiringConfig`, Gangland); `ItemVocabulary` (Gangland **consumes**, does not publish
  — `GanglandContext.java:215`); Bartizan's `BartizanApi`/`WeaponRaytracer` (`Bartizan/.../WiringConfig.java:102,144`)
  and the `CombatEligibility` it pulls back; Oriel's `MenuRegistry`/`MenuOpener`/`OpenMenuTracker` (WS2 R5); and —
  explicitly — that WS4's **`ShopAdminOpener` is a DI bean registered under its interface type, not a
  `ServicesManager` service** (`WS4-shop.md:221`), otherwise readers will hunt for it on the wrong seam. Same for
  Keystone's hologram/shop services (beans in the consumer, not services).
- **C5. "From WS1: none" contradicts WS1's own plan.** `plans/WS1-scoreboard.md:355-360` — "**WS6 asks:** possibly
  one, if D5 is accepted: WS6's `ServicesManager` facade (contract C4) already exists … this could be one accessor
  WS6 owes rather than a bespoke WS1 addition". WS1-D5 publishes Keystone's `PlaceholderProvider` from Gangland on
  the `ServicesManager`. Fold it in: WS6 does not need an *accessor*, but the provider is a second Gangland-owned
  registration, which (a) makes B3's `unregisterAll` load-bearing rather than cosmetic and (b) must appear in the
  service table. State that WS1 keeps the register call and WS6 documents it.
- **C6. The mail worked example has no existing YAML plumbing to copy.** Step 11 says the module already loads
  "`npc/cops.yml`-style defaults". `gangland-features/gangland-mail/src/main/resources` contains exactly
  `commands.json`, `module.yml`, `org/luckyraven/gangland/mail/module.properties` — **no YAML, no `FileHandler`,
  no loader**. mail would be the first module to need all three, modelled on `CopsNCrooksYamlConfig.java:30`
  (`fileManager.addFile(new FileHandler(plugin, "cops", "npc", ".yml", loader), true)`) plus a
  `FileHandlerReader`-backed loader. Either name that template and re-size step 11 from M to **L**, or switch the
  worked example to **`gangland-civilians`** (12 `CIVILIAN_*` constants but `npc/civilians.yml` and its
  `FileHandler` already exist) — the point is proving the *constant → module YAML* repointing, and civilians
  proves it without inventing a module's first config pipeline. `Messages.MAIL_` = 4, confirmed.
- **C7. G0 ownership and the `Host_Api` sweep are double-booked.** `PLAN.md` R1 puts the `GanglandApi.VERSION` and
  every existing `module.yml Host_Api` bump to `2.0` in the **branch's first commit**, orchestrator-owned, before
  WS1 — and WS3 already depends on that (`WS3-lootchest-hologram.md:17,133`: "Gangland G0 already bumped the
  major"). WS6's §2 sweep table and §5 row restate it as WS6 work, yet **no numbered step performs it** (steps 1-2
  only *check*). Fix: §2's table becomes "done at branch G0 — WS6 verifies"; the two new modules carry
  `Host_Api: 2.0` at creation (WS3/WS5); the `Depends: [gang]` additions are WS5's rows, cited not owned. Also
  rename WS6's local "G0" — it collides with the branch-wide G0 label.
- **C8. `gangland-build` shade excludes still list six modules.** `gangland-build/pom.xml:80-86` excludes
  `gangland-mail`, `cops-n-crooks`, `gangland-gadget`, `gangland-turf`, `gangland-civilians`, `gangland-npc-shops`
  while `<include>org.luckyraven:*</include>` sweeps everything else in. C3's eighth-module world needs
  `gangland-gang` and `gangland-lootchest` added or both get shaded into the core jar. WS6 owns the "eight-module
  list" documentation row (step 15), so it should at minimum cross-check this at G4 and name WS3/WS5 as owners —
  §5's table mentions no pom but `gangland-api`'s.

## Simplifications (ponytail)

- **S1. Delete `modules()`, the `ModuleInfo` record, and the new `keystone-module` `provided` dependency.** The
  plan itself calls it "the one real new cost in this plan" and names no consumer beyond "external plugin / ops
  tooling". Ops already have `/glw module list` (`command/sub/module/`, console-allowed). `ModuleLoader.loaded()`
  exists (`keystone-module/.../ModuleLoader.java:243`) and is one container lookup away the day somebody asks.
  Cutting it removes a pom line, a record, a risk row (Risk 2 disappears) and lands the facade on **five**
  accessors — literally Bartizan's count (`BartizanApi.java`, 5 methods).
- **S2. Drop the `japicmp` wiring (step 16, D5).** The plan contains no pom snippet: no artifact coordinates, no
  `oldVersion` binding, no repository to resolve a previous `gangland-api` from — and there cannot be one, because
  Central publishing is still pending (census `WS6-api.md:303`: absent from this repo *and* from Bartizan). What
  lands is a permanently-skipped plugin block plus Risk 4, which exists only to worry about the block. Replace
  with one line under "Follow-ups" in `documentation/gangland-api.md`: "wire `japicmp` when a second 2.x release
  exists on a resolvable repository". Deleting config beats documenting why config is inert.
- **S3. Step 8's "scenario test" is a smoke row wearing a JUnit costume.** Asserting `gangs()` populated "in a
  gang-module-present run" needs a real module jar through `ModuleLoader` — that is smoke row M2, which the plan
  already has. The unit half (`Optional.empty()` vs populated against a stubbed `DependencyContainer`) is already
  `GanglandApiImplTest` in §7. Keep M2 + `GanglandApiImplTest`, delete the third artefact; it is the half-day
  hiding inside an "M".
- **S4. §2's two big tables are restatements of WS2/WS4/WS5 decisions.** They have already drifted once (the
  `GangLookupContract` placement, B1). Keep one row + a pointer per item; let the owning plan be the source.

## Missing consumers found by graphify affected

| Type moved | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `GanglandApi` (class→interface) | 8 files in `gangland-features` read its constants; Risk 1 says "none expected" | `CopSpawnerInfoCommand.java:64`, `CopSpawnerListCommand.java:40`, `JailInfoCommand.java:66`, `JailListCommand.java:41`, `CopsNCrooksModuleConfig.java:140`, + civilians ×2, turf ×1 | None at compile time (static field reads), but the claim is wrong and the module reactor must be in the verification build |
| `GanglandApi` | graph has **zero** edges into it (`graphify affected` → "No affected nodes found") | — | The plan's graphify framing is unsupported here; grep is the evidence. Say so in the header |
| `UserLookupContract` (WS5 moves it) | facade's mandatory `users()` param | `gangland-infra/gangland-domain/.../gang/contract/UserLookupContract.java` | B2 |
| `CommandContribution` | untouched by WS6, correctly — 3 module implementors + 1 test | `GangMailContribution.java:23`, `GangAllyMailContribution.java:24`, `BankMenuContribution.java:15`, `CommandContributionsTest.java:70` | No action; confirms the seam list in step 15 is complete |
| `Messages` (static) | 4 `MAIL_*` constants, confirmed by count | `gangland-api/.../file/configuration/Messages.java` | D4 "stay static" is safe — nothing in the 535-edge neighbourhood forces a bean |

**Events — the plan is right and the census is wrong.** A repo-wide `find` returns exactly one `TeleportEvent.java`
and one `UserLevelUpEvent.java`, both in `gangland-api`; `gangland-impl/.../events/` holds only
`gang/GangBountyEvent`, `gang/GangLevelUpEvent`, `user/UserDataInitEvent`. `census/WS6-api.md:371`'s "duplicate of
api version; needs reconciliation" is false. "Keep the current two" is also safe under WS5: `gangland-api/pom.xml:28-31`
declares `gangland-core` with **no `<scope>`** → compile scope, so the `Bounty*`/`Wanted*`/`LevelUpEvent` set WS5
moves into `gangland-core` stays api-visible transitively without an api file. One addition for the doc: mention
`UserDataInitEvent` (`PlayerBootstrapService.java:15`) in the "not public api" list, or a reader will ask why the
user lifecycle has one public event and one private one.

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| D1 name | Keep `GanglandApi`, class→interface | **Agree** | 32 static-field reads compile identically on an interface; the private ctor at `GanglandApi.java:25` is the only deletion |
| D2 accessor list | Ship 5 (+`modules()` = 6) | **Agree, minus `modules()`** (S1) — makes it genuinely 5 | No named consumer; the accessor pulls a new pom dependency for a `/glw module list` duplicate |
| D3 `gangs()`/`ranks()` | "Resolved by WS5, not pending" | **Disagree as written — superseded by R7** | B1: the api cannot name a module type. R7's split (interface in api, impl in module, `Optional` resolved per call) keeps the planner's *mechanism* and fixes only the placement |
| D4 Messages/Settings static | Stay static | **Agree** | A ~600-site accessor refactor buys nothing this wave; the YAML mechanism is the real ask |
| D5 japicmp | Wire now, `skip=true` | **Disagree** (S2) | Inert config whose flip condition depends on work that is not scheduled; a follow-up line costs nothing |
| D6 events | Keep the 2 | **Agree** | Verified: no duplicates, no external consumer for the 9 lootchest / 7 domain events |
| D7 modules use DI not facade | No | **Agree, but it is not a decision** | No fork exists — modules already have DI and the facade is explicitly the external-plugin surface. Demote to a stated rule so the user's decision list is real forks only |

## Estimate check

**~8.5 h claimed; ~12–14 h realistic**, mostly from C6 and the missing service table.

| Gate | Claimed | Reviewer | Why |
|---|---|---|---|
| G0 | 0.5 h | 0.5 h | +B2's precondition, still verification-only |
| G1 | 3 h | 3 h | Correct *if* S3 lands (facade + impl + bean + 2 unit tests). Without S3 it is 6 h |
| G2 | 1 h | 0.5 h | Audit already done in-plan; only the doc note remains |
| G3 | 2.5 h | 5 h | C6: mail has no `FileHandler`, loader or YAML today — three new files before the first constant moves. Civilians instead: ~3 h |
| G4 | 1.5 h | 3 h | S2 removes japicmp (−0.5 h) but C4's service table (six publishers across four repos) is a real hour, and C8's shade cross-check is not costed |

**Missing steps** not in any gate: writing the docket status rows after the wave (§11 lists ids but no step writes
`{status, note}` to the artifact's `bugs` collection, which the repo `CLAUDE.md` requires after a fix lands); the
memory/plan-file note; `gangland-build` shade excludes (C8). `commands.json` and `settings.yml` are correctly
untouched. Gate independence holds **only after B1/B2** — as written, `gangland-api` does not compile at G1.

## Things I could not verify

- Whether `gangland-api/pom.xml:54-83`'s `keystone-*` blocks inherit `provided` from the root pom's
  `dependencyManagement` — no `<scope>` appears on those blocks in the child pom, yet `gangland-build/pom.xml:71-73`
  asserts "keystone-* … are provided scope, so the shade plugin never includes them". S1 moots the question for
  `keystone-module`; if `modules()` survives, the executor confirms this before adding the dependency.
- Whether `context.reloadBeans()` can re-run `WiringConfig`'s `@Bean` body and register the service twice
  (Bukkit appends a second `RegisteredServiceProvider` rather than replacing). Bartizan has the same shape and no
  managed reload, so it is untested there. One `if (Bukkit.getServicesManager().getRegistration(...) == null)`
  guard, or a note that `BeanLifecycle` reload never re-instantiates beans, closes it.
- WS5's `IdentityContractConfig` bean bodies (the plan's own §13 bullet 1 — the class does not exist yet).
- No `mvn` was run; every "compiles unchanged" claim here is grep/AST evidence, same as the plan's.
