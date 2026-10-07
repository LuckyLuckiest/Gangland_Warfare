# WS6 G1+G2+G4 report — the GanglandApi facade, events census, docs

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws6`, branch `0.10.0-ws6`, based on `89746105` (0.10.0, WS6 G3
merged). No commits made; worktree left uncommitted per LEAD-RULES.md. Spec: `plans/WS6-api.md` §0b/§0c (final R7,
binding), §1-§3, §4 G1 steps 4-8, G2 steps 9-10, G4 steps 14-17 minus the graphify run — with the corrections in
the dispatch (four-accessor facade, `GangMembership` not `Optional`, no `ranks()`, no `DependencyContainer` in
`GanglandApiImpl`) layered on top and treated as binding wherever they differ from the 2026-09-14 plan text.

## What changed

| Plan step | What I did |
|---|---|
| 4 | `GanglandApi.java`: `final class` → `interface`, private constructor dropped, `VERSION`/`FULL_PREFIX`/`SHORT_PREFIX` kept as interface constants (implicitly `public static final`). **Four** abstract accessors per R7 (not the plan's original five with `Optional` `gangs()`/`ranks()`): `users()`, `gangs()`, `waypoints()`, `bankTiers()`. Verified reactor-wide (not just impl/api): 31 static-field-read call sites across 25 files (`GanglandApi.VERSION`/`.FULL_PREFIX`/`.SHORT_PREFIX`) all compile unchanged — confirmed via `mvn clean compile` on the full reactor, not just a grep. |
| 5 | `gangland-impl/.../GanglandApiImpl.java` (new): constructor takes all **four** contracts as mandatory params — `UserLookupContract`, `GangMembership`, `WaypointLookupContract`, `BankTiers`. **No `DependencyContainer`** — R7 removed the one accessor (`gangs()`'s old `Optional<GangLookupContract>` lazy container lookup) that needed it; `GangMembership` is a core bean like the other three (`IdentityContractConfig.gangMembership()`, already existed from WS5 R9), so every accessor is now a plain constructor-injected field read, mirroring `BartizanApiImpl`'s shape even more directly than the original plan expected. |
| 6 | `WiringConfig.java`: new `@Bean ganglandApi(UserLookupContract, GangMembership, WaypointLookupContract, BankTiers)` — construct, `Bukkit.getServicesManager().register(GanglandApi.class, api, gangland, ServicePriority.Normal)`, log, return. Same construct-register-log-return idiom as the existing `ganglandPlaceholder` bean immediately above it in the same file. |
| 7 | `Gangland.java` `onDisable()` — **no change needed**. `getServer().getServicesManager().unregisterAll(this)` is already the first statement (added by WS1 for `ganglandPlaceholder`); it unregisters every service this plugin owns, so the new `GanglandApi` registration is already covered. Confirmed by reading the method, not assumed. |
| 8 | Two new test classes (see Tests section). |
| 9-10 (G2) | Events census corrected and documented — **no code moved**, WS5 already executed everything the plan's original G2 decided. `gangland-api`'s `events/` package holds exactly 3 files across 2 subpackages: `TeleportEvent` (public api), `UserLevelUpEvent` (public api), `UserDataInitEvent` (physically there, explicitly documented as **not** public api — an internal bootstrap-timing event, per the dispatch's "UserDataInitEvent is already there — keep its package"). `GangBountyEvent`/`GangLevelUpEvent` are confirmed moved into `gangland-gang` (WS5). Nothing deleted that a shipped module could reference — additive-only, matching the dispatch's rule. |
| 14 | `documentation/gangland-api.md` (new) — resolution snippet, four-accessor table, service table, events table + future-event rule, "what Gangland does not expose" section, "container beans not services" callout (`ShopAdminOpener`, `GangMembership`, `PlaceholderContribution`, `GangItemSourceContribution`), api-contract additions table, follow-ups. |
| 15 | `documentation/module-loader.md` "Core seams" — added `PlaceholderContribution`, `GangItemSourceContribution`, `GangMembership` (WS5 additions, previously undocumented here), `LocalizedModuleYaml` (WS6 G3, also previously undocumented), and the `GanglandApi` facade itself (noted as not a module-facing seam, listed for completeness). |
| 16 | Docket write-back check: plan §11 names only `KS-MO-05`/`KS-MO-06`/`KS-MO-07`, all Keystone-side and unaffected by this gate — recorded below, no `write_db` call made (nothing this gate fixed). |
| 17 | **Not run** — graphify explicitly out of scope this gate. See "What END must refresh" below. |
| — | `documentation/migration-0.10.0.md`: new "## WS6 G1/G2/G4" section appended after the existing "## WS6 G3" section (append order preserved). `CLAUDE.md` (gitignored, local-only): new bullet under "Module API contract" describing the facade. Doc sweep of `documentation/developer/*` for "`GanglandApi.VERSION`/final class" mentions: **zero matches** anywhere in `documentation/` outside the 3 files already listed above — confirmed by a reactor-wide grep before touching anything, so no subagent was needed for this (see "Subagents used"). |

## The facade's final signatures

```java
public interface GanglandApi {

	String VERSION = "2.0";
	String FULL_PREFIX = "gangland";
	String SHORT_PREFIX = "glw";

	UserLookupContract users();
	GangMembership gangs();          // never null; inert until gangland-gang installs a view
	WaypointLookupContract waypoints();
	BankTiers bankTiers();

}
```

```java
public final class GanglandApiImpl implements GanglandApi {
	public GanglandApiImpl(UserLookupContract users, GangMembership gangs,
	                       WaypointLookupContract waypoints, BankTiers bankTiers) { ... }
	// four one-line accessor overrides, each returning the constructor-injected field
}
```

No `ranks()` accessor exists anywhere in this shape — confirmed absent from both the interface and the
implementation.

## Registration lifecycle

1. **CONFIG phase**, `WiringConfig.ganglandApi(UserLookupContract, GangMembership, WaypointLookupContract,
   BankTiers)` constructs `GanglandApiImpl`, registers it on Bukkit's `ServicesManager` under `GanglandApi.class`
   at `ServicePriority.Normal`, logs `"GanglandApi facade published for external consumers"`, returns it as the
   bean.
2. **Consumer side** (external plugin only — no runtime module resolves it this way):
   `Bukkit.getServicesManager().getRegistration(GanglandApi.class).map(RegisteredServiceProvider::getProvider)`,
   **resolved fresh on every call, never cached** — documented in both the interface javadoc and
   `documentation/gangland-api.md`.
3. **Teardown**: `Gangland.onDisable()`'s first statement, `getServer().getServicesManager().unregisterAll(this)`
   (already present, added by WS1 for `ganglandPlaceholder`), unregisters this service too — no code change was
   needed here, only verification that the existing "unregister everything, first, before any early return" rule
   already covers a second registration.

## Events map

| Location | Event | Cancellable | Public api? |
|---|---|---|---|
| `gangland-api/.../events/teleportation/` | `TeleportEvent` | Yes | Yes |
| `gangland-api/.../events/user/` | `UserLevelUpEvent` | Yes | Yes |
| `gangland-api/.../events/user/` | `UserDataInitEvent` | Yes | **No** (documented explicitly, per dispatch — kept its package, not promoted, not demoted) |
| `gangland-features/gangland-gang/.../events/gang/` | `GangBountyEvent`, `GangLevelUpEvent` | — | No (module-owned, WS5) |

Every other `*Event` class in the reactor (turf's 7, lootchest's 9, npc-shops' 3, cops-n-crooks' 4, civilians' 1,
gangland-item's 1, gangland-core's bounty/wanted/level family) stays with its owning module/core package —
confirmed by a reactor-wide `find *Event.java` sweep, none promoted, none deleted.

## Docs list (files touched this gate)

- `documentation/gangland-api.md` — **new**.
- `documentation/module-loader.md` — "Core seams" section extended.
- `documentation/migration-0.10.0.md` — new "## WS6 G1/G2/G4" section.
- `CLAUDE.md` (worktree root, gitignored) — new bullet in "Module API contract".
- `documentation/developer/*` — **no changes**; confirmed zero references to `GanglandApi` anywhere in that
  directory before touching anything.

## Api-contract additions table (within `2.0`, only additions)

| Addition | Kind |
|---|---|
| `GanglandApi` becomes an `interface` (was `final class`) | Widening — 31 existing static-constant reads across 25 files compile unchanged, verified reactor-wide |
| `UserLookupContract users()` | New method |
| `GangMembership gangs()` | New method |
| `WaypointLookupContract waypoints()` | New method |
| `BankTiers bankTiers()` | New method |
| `GanglandApiImpl` (`gangland-impl`) | New class, the facade's sole implementation |

No removal, rename or signature change of any existing public member — the contract rule holds.

## Tests, with reds

| Test | File | Asserts |
|---|---|---|
| `accessors_returnConstructorInjectedInstances`, `gangs_neverNullAndInertUntilInstalled` | `GanglandApiImplTest` (new, `gangland-impl`, root package) | Every accessor returns exactly the constructor-injected instance; `gangs()` is never `null` and stays inert (module-absent defaults: `isInstalled()` false, `gangIdOf` → -1, `gangsAllied`/`alliedOrSame` → false) until `GangMembership.install(...)` is called |
| `ganglandApi_registersGanglandApiService` | `GanglandApiRegistrationTest` (new, `gangland-impl`, `config` package, mirrors `WiringConfigTest`) | `WiringConfig.ganglandApi(...)` registers `GanglandApiImpl` as a `GanglandApi` service at `ServicePriority.Normal`, owned by the plugin |

**Red evidence — "red by non-existence" per W48**, explicitly the accepted shape here: every member under test
(`GanglandApiImpl`, its four accessors, `WiringConfig.ganglandApi(...)`) is entirely new this gate. Both test
classes could not have compiled, let alone run, before this gate's production classes existed — the newly-added-
member carve-out W48 states explicitly. No pre-existing behaviour was being changed (unlike WS3/WS4's legacy-
warning tests, which needed a genuine mutate-and-revert red run because they pinned a *change* to already-live
code), so no mutate-and-revert dance was performed for these two classes. Both ran green on first execution:
`GanglandApiImplTest` 2/2, `GanglandApiRegistrationTest` 1/1 (surefire reports confirmed individually before the
full gate build).

## Build

Targeted: `mvn clean verify -pl gangland-api,gangland-impl -am` → **BUILD SUCCESS** (gangland-api 14 tests,
gangland-impl 237 tests, both 0 failures/errors/skipped).

Full reactor (one build, never `install`): `mvn clean verify` → **BUILD SUCCESS**, ~1:16 min, 19/19 modules green.

**Test count (Maven console rollup, per W52):**

| Module | Tests run |
|---|---|
| gangland-core | 67 |
| gangland-infra/gangland-item | 43 |
| gangland-ui/sign-api | 63 |
| gangland-api | 14 |
| **gangland-impl (shown as "Gangland")** | **237** |
| gangland-features/gangland-gang | 102 |
| gangland-features/gangland-mail | 25 |
| gangland-features/gangland-civilians | 23 |
| gangland-features/gangland-turf | 91 |
| gangland-features/cops-n-crooks | 76 |
| gangland-features/gangland-gadget | 113 |
| gangland-features/gangland-npc-shops | 17 |
| gangland-features/gangland-lootchest | 53 |
| gangland-infra/gangland-domain | 0 |
| **Total** | **924**, 0 Failures, 0 Errors, 0 Skipped |

**Delta vs. the 921 baseline: +3** = `gangland-impl` **234→237** = `GanglandApiImplTest`'s 2 tests +
`GanglandApiRegistrationTest`'s 1 test. Every other module's count is byte-for-byte unchanged from the merged
baseline.

## Deferred smoke rows (per the dispatch, "facade smoke = the merge run")

No smoke run this gate. For the next merge run (a module resolving `GanglandApi` from the `ServicesManager`):

1. Boot: `WiringConfig.ganglandApi(...)` logs `"GanglandApi facade published for external consumers"` once, no
   `NoSuchMethodError`/`NoClassDefFoundError`/`ClassCastException` in the bean graph.
2. A throwaway test plugin resolves `Bukkit.getServicesManager().getRegistration(GanglandApi.class)`, calls all
   four accessors — `users()`/`waypoints()`/`bankTiers()` return non-null; `gangs()` returns non-null and, absent
   the gang module, every query answers its inert default.
3. With `gangland-gang` loaded: `gangs().isInstalled()` is `true`, `gangIdOf`/`gangsAllied`/`nameOf` answer for
   real gang data.
4. `onDisable()`/re-enable cycle: the facade registration is gone immediately after disable
   (`getRegistration(GanglandApi.class)` returns empty), re-appears after the next enable — proving
   `unregisterAll(this)`'s first-statement placement covers this service too, not just `PlaceholderProvider`.

## Docket

Plan §11 names only `KS-MO-05`, `KS-MO-06`, `KS-MO-07` for WS6 — all Keystone-side module-loader items. **None
touched by this gate.** `KS-MO-07` ("bootstrap isolation gap") is explicitly *not* fixed by this plan per the
plan's own text — the api contract + `Host_Api` floor are documented as "the prevention," not the fix; still
true after this gate. No `write_db` call made — nothing this gate fixed in the docket's tracked sense.

## What END (the orchestrator) must refresh — graphify not run this gate

- `graphify update . --force` (AST only, no LLM) — new classes (`GanglandApiImpl`), one interface conversion
  (`GanglandApi` `final class` → `interface`, its constant/method shape changed), two new test classes, and four
  documentation files changed. The graph's own earlier note stands: `graphify affected "GanglandApi"` returned
  "No affected nodes found" pre-wave (constants-only classes get no edges from the extractor) — worth re-checking
  post-conversion whether the new interface/accessors produce real edges the old class didn't, since that was an
  open question in the original plan's step 17 and this gate didn't re-run the query to answer it.

## Subagents used

None. The two tasks the dispatch flagged as good haiku fits both turned out to be cheap enough to do directly:
the 32(→31)-site verification was one `grep`+`mvn clean compile` pass, not worth a subagent round-trip; the doc
sweep came back with **zero** matches in `documentation/developer/*` on the first grep, so there was nothing left
to delegate.

## Concerns / open questions

- The `GanglandApi` facade still has no concrete external consumer, same honest gap Bartizan's own facade started
  with — noted in both `documentation/gangland-api.md` and the interface javadoc, not treated as a defect.
- `graphify affected "GanglandApi"`'s pre-wave "no affected nodes" result was not re-checked post-conversion (see
  "What END must refresh" above) — flagged for whoever runs the graph refresh, not resolved here.

## Deliverables

- `exec/WS6/G1-G2-G4-report.md` — this file.
- `exec/WS6/G1-G2-G4-package.diff` — `git add -N . && git diff HEAD` (then `git reset`, worktree left
  uncommitted). 522 lines, 8 files touched: 4 modified (`documentation/migration-0.10.0.md`,
  `documentation/module-loader.md`, `GanglandApi.java`, `WiringConfig.java`) + 4 new (`documentation/gangland-api
  .md`, `GanglandApiImpl.java`, `GanglandApiImplTest.java`, `GanglandApiRegistrationTest.java`). `CLAUDE.md`'s
  edit is gitignored/local-only and does not appear in the diff, confirmed present on disk separately.

No smoke run, no `scenarios.json`, no commits, no reviewers invoked, no graphify run.
