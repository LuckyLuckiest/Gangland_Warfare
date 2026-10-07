# WS6 Plan — Improve `gangland-api` (Bartizan-style facade)

User's sentence for this workstream: *"Improve the api system so that most of the features can be used over
there, check bartizan plugin how it was done."*

Every code claim below is **grep/AST evidence**, not graphify: `graphify affected "GanglandApi"` (depth 2, all
relations) returns **"No affected nodes found"** — the extractor records no edges into a constants-only class, so
the graph is silent on this workstream's central type (confirmed by REVIEW-WS6.md's own verification, not
re-disputed here). `graphify explain` was run against `CommandContribution`, `GangLookupContract`,
`UserLookupContract`, `WaypointLookupContract`, `BankTiers`, `WiringConfig`, `PlaceholderService` for orientation,
and those returned real edges; `GanglandApi` itself did not, so every claim naming it is grep-sourced and says so.

## 0b. Review response (REVIEW-WS6.md, PASS WITH FIXES — applied in place)

| Id | Review finding | Change made | Where |
|---|---|---|---|
| B1 | `gangland-api` cannot name `GangLookupContract`/`RankLookupContract` if WS5 owns them — the api never names a module class | Adopted **R7** verbatim: the two *interfaces* live in `gangland-api`; the gang module ships only the implementations; `gangs()`/`ranks()` stay `Optional`, resolved lazily per call | §2 "New/changed classes", §3 seams row + reconciliation note, §9 D3, §10b |
| B2 | `UserLookupContract` (and, per B1, `GangLookupContract`/`RankLookupContract`/`PermissionRegistryContract`) live in `gangland-domain` today, not the api — "stays" was wrong in both plans | Added as an explicit G-Pre precondition: WS6's G1 does not start until all four compile from `gangland-api` | §4 step 1 |
| B3 | `unregisterAll` "before/alongside `ShutdownSequence`" leaks a provider on a failed enable | Moved to the **first statement** of `onDisable()`, before the `context == null` early return, matching `Bartizan.java:47` | §2 "New/changed classes", §4 step 7 |
| C1 | `DependencyContainer.tryGet` does not exist; `getInstance` returns `null`, never throws | `gangs()`/`ranks()` are one-liners — `Optional.ofNullable(container.getInstance(GangLookupContract.class))`; deleted the try/catch fallback, the "Keystone ask," and the uncertainty in old Risk 3/§13 | §3, §4 step 5, §7, §8 (risk removed), §13 (bullet removed) |
| C2 | "`Gangland.java` has no `ServicesManager` call at all" is false — `Gangland.java:188` reads `Economy` | Corrected to "Gangland registers nothing today" | §4 step 7 |
| C3 | Call-site count wrong: 32 hits in 26 files, 8 of them in `gangland-features` (not "none expected") | Fixed the count and named the module files; confirmed all 8 are static-field reads, so the interface conversion still compiles | §4 step 4, §8 risk 1 |
| C4 | The promised service table doesn't exist ("§7 below" pointed at *Tests*) | Added a real service table naming every `ServicesManager` registrant across all four repos, plus an explicit "container beans, not services" callout for `ShopAdminOpener`/`GangMembership` | §3 "Service table" |
| C5 | "From WS1: none" contradicts WS1's own D5 ask (`PlaceholderProvider` on the facade) | Folded in: WS1 keeps the register call, WS6 documents it in the service table | §10, §3 service table |
| C6 | `mail` has no YAML/`FileHandler`/loader today — the worked example as written is really an **L** | Swapped the worked example to **`gangland-civilians`** (already has `CiviliansYamlConfig.java:30`'s `FileHandler`); kept **M** | §4 step 11 |
| C7 | `Host_Api` → 2.0 is the branch G0's job (R1), not WS6's — no numbered step performed it | Retitled WS6's local gate (was "G0", collided with the branch-wide G0) to **G-Pre**; its steps now *verify* the bump, never perform it | §4 heading + steps 1-3 |
| C8 | `gangland-build/pom.xml`'s shade `<excludes>` still lists only six modules; `gangland-gang`/`gangland-lootchest` are missing | Added as a G-Pre/G4 verification step, named WS3/WS5 as owners | §4 steps 3, 15 |
| S1 | Cut `modules()`, `ModuleInfo`, the `keystone-module` dependency — no named consumer, `/glw module list` exists | Deleted throughout; facade is now **5** accessors, not 6 | §2, §3, §4, §5, §6, §7, §8, §9 D2 |
| S2 | Cut the `japicmp` wiring — no pom snippet, nothing to diff against, Bartizan has none either | Deleted the plugin block/step/risk; replaced with one follow-up line in the doc | §4 step 14, §8, §9 D5, §13 |
| S3 | Step 8's "scenario test" is smoke row M2 wearing a JUnit costume | Merged into step 8 (unit tests only); the module-present/absent cross-product stays smoke row M2 | §4 step 8, §7 |
| S4 | §2's two big tables restate WS2/WS4/WS5 decisions and had already drifted once (the B1 placement error) | Compressed to one row + a pointer per item; the owning plan is cited, not re-derived | §2 |

## 0c. Orchestrator amendment — final ruling R7 (2026-09-14 22:40, supersedes every R7 mention below)

REVIEW-WS5's re-review (N1) showed that `GangLookupContract.java:13-16` and `RankLookupContract.java:12-14` name
`Gang`/`Rank` in every signature, so keeping those interfaces in `gangland-api` while the entities ship in the gang
module would invert the module→api direction. Final shape, binding for WS5 and WS6 executors:

- `GangLookupContract` and `RankLookupContract` **move into `gangland-gang`** together with `Gang`/`Rank`. They are
  not in `gangland-api`. turf and mail reach them through `Depends: [gang]` + the `provided` pom (turf→civilians pattern).
- The api exposes gang facts **only** through the core-owned `GangMembership` holder (R9): `int gangIdOf(UUID)`,
  `boolean gangsAllied(int, int)` — **strict**: allied, never same-gang, pinned by a unit test so cops' turf
  friendly-fire keeps today's semantics — and the derived default `alliedOrSame(UUID, UUID)`. Inert until the gang
  module's `@PostConstruct` installs the real view.
- WS6's facade: `GangMembership gangs()` (never null); **no `ranks()` accessor**; the `Optional` lookups are removed.
  The facade has four accessors: `users()`, `gangs()`, `waypoints()`, `bankTiers()`.
- Everything else in this plan stands; where a section says "interface stays in the api" for the two contracts,
  read this block instead.

## 1. Scope

**In**
- Convert `GanglandApi` from a constants-only `final class` into the `ServicesManager` facade interface (Bartizan
  pattern), with a `GanglandApiImpl` and one `WiringConfig` bean, resolved lazily by any consumer, never cached.
- `gangland-api` 2.0 pom pruning — but only the *verification* half. WS1 (scoreboard), WS2 (inventory→Oriel) and
  WS4 (shop→Keystone) each already decided what leaves `gangland-api`'s re-export list and where it goes (§2);
  the `Host_Api`/`VERSION` bump itself is the branch's G0, first commit, orchestrator-owned (R1, C7) — WS6 verifies
  it happened, and owns the parts nobody else claimed (the facade, the events decision, the Messages/Settings
  migration mechanism, docs).
- Events: audit + correction of the census's "13+ events, duplicates" claim (§3), and the rule for future events.
- A worked example (one module) of the module-owned-`Messages`/`Settings`-to-YAML migration mechanism the
  2026-09-10 wave (D8) deferred — not the full 179/~130-constant migration.
- `documentation/gangland-api.md` (new, modelled on `bartizan-api.md`, service table as its centre) +
  `module-loader.md` seam-list update.

**Out**
- Executing WS1/WS2/WS3/WS4/WS5's own moves — this plan only consumes their output per C1's fixed order.
- Migrating all module-owned `Messages`/`Settings` — D8 (2026-09-10) already decided this is a per-module,
  additive-only follow-up; redoing that call here would reopen a settled decision.
- A new interface for `wanted`/`bounty` lookup, or exposing `PlaceholderService`/`SignManager`/`ShopRegistry`
  through the facade — no existing contract, no named consumer (§9 D2).
- `modules()`/`ModuleInfo`/`keystone-module` (S1) — cut, `/glw module list` already exists.
- `japicmp` wiring (S2) — cut, nothing to diff against; one follow-up line instead.
- Central publishing execution (namespace/GPG/token still pending user action per the 2026-09-10 follow-ups).

**Deferred**
- A `HostApi` value type / version ranges, a hot-reload path, a remote module catalogue — already explicitly
  skipped by the 2026-09-10 wave; nothing here changes that calculus.
- `japicmp` — wire it when a second `2.x` release of `gangland-api` exists on a resolvable repository (S2).
- Per-module bootstrap isolation (KS-MO-07) — Keystone-side, needs rollback-capable bean registration; out of a
  Gangland-repo plan's reach.

## 2. Target layout

### `gangland-api` pom — what leaves, what's added, who owns it (compressed per S4)

| Artifact/line | Disposition | Owning plan (cited, not restated) |
|---|---|---|
| `gangland-core` (`:30`) | kept, unchanged, **now carries the identity slice** (`User`/`UserManager`/`Level`/`Bounty*`/`Wanted*`/`Permission`+repo/table/7 events) | WS5-gang.md §2 |
| `gangland-domain` (`:34`) | **removed outright** — the module is 100% `gang/`, WS5 deletes the pom/dir/parent `<module>` entry | WS5-gang.md §1/§2 |
| `gangland-item` (`:38`), `sign-api` (`:46`) | kept, unchanged | — |
| `inventory-api` (`:42`) | **removed**, zero `gangland-api` source-file impact (pom-line only) | WS2 |
| `shop-api` (`:50`) | **removed, replaced by `keystone-shop`** (`provided`) | WS4-shop.md §2/§9 |
| `keystone-*` (`:56-81`) | kept, unchanged. No `keystone-module` dependency added (S1 cut it) | — |

### Source files leaving/entering `gangland-api`

| File | Change | Owner |
|---|---|---|
| `file/configuration/shop/GanglandShopDisplayResolver.java` | moved + renamed to `keystone-shop/.../message/DefaultShopDisplayResolver.java` | WS4-shop.md:122,151,213 |
| `contract/UserLookupContract.java`, `GangLookupContract.java`, `RankLookupContract.java`, `PermissionRegistryContract.java` | **enter** `gangland-api` (currently in `gangland-infra/gangland-domain/.../gang/contract/`, verified by `find`) — **R7**: the api can never name a module class, so these four interfaces move here and the gang module ships only their implementations | WS5 (precondition of WS6 G1, B2 — §4 step 1) |
| `contract/PlaceholderContribution.java` | **enters** `gangland-api`, new: `{ @Nullable String resolve(OfflinePlayer, String); }` — a plain library file, not a facade accessor (§3 service table explains why) | WS5-gang.md §3 row 9 |
| `contract/GangMembership.java` (+ a core-owned default implementation, `BankTiers`-shape: `install(...)`/query methods, inert until the gang module installs) | **enters** `gangland-api`, new — **ruling R9** (`PLAN.md` §2, from REVIEW-WS5 S1): replaces WS5's originally-proposed `MembershipLookupContract`; a container `@Bean`, not a `ServicesManager` service (§3 service table) | WS5, per R9 |
| 4 existing api files (`WaypointTeleport.java:15`, `TeleportEvent.java:12`, `UserLevelUpEvent.java:6-8`, `MoneyAspect.java:10-11`) | import-path fix only (`gang.user.User`→`user.User`, `gang.events.level.LevelUpEvent`→`events.level.LevelUpEvent`) — mechanical, part of WS5's own package rename, not a WS6 action item | WS5 |

### New/changed classes (WS6-owned)

| File | Change | Mirrors |
|---|---|---|
| `gangland-api/.../GanglandApi.java` | `final class` → `interface`; keep `VERSION`/`FULL_PREFIX`/`SHORT_PREFIX` as interface constants; add **5** abstract accessors (§3) | `bartizan-api/.../BartizanApi.java:15-25` (5 accessors) |
| `gangland-impl/.../GanglandApiImpl.java` (new) | thin `implements GanglandApi`; 3 mandatory constructor params + a `DependencyContainer` reference for the 2 `Optional` accessors | `bartizan-plugin/.../BartizanApiImpl.java` |
| `gangland-impl/.../config/WiringConfig.java` | +1 `@Bean ganglandApi(...)` registering on `ServicesManager` | `bartizan-plugin/.../config/WiringConfig.java:157-165` |
| `gangland-impl/.../Gangland.java` `onDisable()` | +1 line, **first statement**: `getServer().getServicesManager().unregisterAll(this)`, before the `if (context == null) return;` at `Gangland.java:83` (B3). Note (C2): `Gangland.java:188` already does one `ServicesManager` *read* (`Economy`) — the accurate baseline is "Gangland registers nothing today," not "no call at all" | `Bartizan.java:47`, before its own `:50` early return, comment: "a disable/enable cycle must not leave dead providers behind" |
| `documentation/gangland-api.md` (new) | modelled on `Bartizan/documentation/bartizan-api.md`, service table as its centre | — |

### `Host_Api` / module descriptor sweep — not WS6's step (C7)

The `GanglandApi.VERSION` bump and the existing six `module.yml`s' `Host_Api: 1.0`→`2.0` land in the **branch's
first commit** (`PLAN.md` R1, orchestrator-owned, before WS1). `gangland-lootchest`/`gangland-gang` carry
`Host_Api: 2.0` at creation (WS3/WS5). The `Depends:` additions for `turf`/`mail`/`civilians`/`gadget` are WS5's
own rows — **cited, not restated here** (S4): WS5-gang.md §2's table is authoritative and is under active rework
per R7/R9 (ruling R9 specifically removes `Depends: [gang]` from gadget/civilians/cops-n-crooks by replacing
per-consumer `MembershipLookupContract` wiring with the shared `GangMembership` holder — WS6 does not restate a
table that is mid-rework; it verifies the final `module.yml` files at G4 instead, §4 steps 2-3, 15).
`gangland-build/pom.xml`'s shade `<excludes>` (currently 6 entries, `:79-86`) needs `gangland-gang`/
`gangland-lootchest` added or both ship inside the core jar by accident (C8) — WS3/WS5 own the fix, WS6
cross-checks at G-Pre and again at G4.

Since 2.0 is a **major** bump, Keystone's minor-floor rule (same major, module minor ≤ host minor) makes every
module built against `Host_Api: 1.x` incompatible with a `2.0` host by design — correct, since 2.0 physically
removes types some 1.x-built module jars may have named. All eight in-repo modules are rebuilt in this same wave;
no third-party module exists yet (no Central publish).

## 3. Seams

| Seam | Mechanism | Publisher | Consumer | Default when absent |
|---|---|---|---|---|
| `GanglandApi` facade | `ServicesManager.register(GanglandApi.class, impl, gangland, Normal)`, lazy `getRegistration(...).getProvider()`, never cached | `WiringConfig.ganglandApi(...)` (new) | any external plugin (no concrete consumer exists today — honest gap, same starting point Bartizan's own facade had) | `getRegistration` returns `null` — caller null-checks, exactly `bartizan-api.md`'s snippet |
| Facade teardown | `Gangland.onDisable()` → `unregisterAll(this)`, **first statement** (B3) | Gangland | — | idempotent; mirrors `Bartizan.java:47` |
| Module access to gang/user/waypoint/bank data | **unchanged**: constructor injection from `DependencyContainer` (D7, 2026-09-10) | core/gang-module config beans | turf/mail/cops-n-crooks/etc. | — |
| `users()` accessor | `UserLookupContract` — **R7**: interface moves into `gangland-api` (was `gangland-domain`, B2); the gang-independent identity slice (`UserManager`) still implements it directly, always core-published | `GanglandApiImpl` constructor param (mandatory) | external plugin via facade | n/a — core always publishes |
| `gangs()`/`ranks()` accessors | **R7** (resolves B1): `GangLookupContract`/`RankLookupContract` **interfaces live in `gangland-api`**; the `gangland-gang` module ships the only implementations (`GangManager implements GangLookupContract` directly, module-internal). `GanglandApiImpl` cannot take these as constructor params (nothing produces the bean when the module is absent), so `gangs()`/`ranks()` are one-line lazy lookups: `Optional.ofNullable(container.getInstance(GangLookupContract.class))` (C1 — `DependencyContainer.getInstance`, `keystone-bean/.../DependencyContainer.java:56-62`, returns `null` on a miss, never throws), resolved fresh on every call | `GanglandApiImpl`, resolving against the shared `DependencyContainer` at call time | external plugin via facade | module absent → `Optional.empty()`; present → populated, same instance modules see via DI |
| `waypoints()` accessor | `WaypointManager implements WaypointLookupContract` directly (`WaypointManager.java:18`), bean at `DataConfig.java:142` | `GanglandApiImpl` constructor param (mandatory) | external plugin | n/a — core-mandatory |
| `bankTiers()` accessor | returns the `BankTiers` holder itself (`gangland-api/.../data/economy/BankTiers.java:14-36`) — already the "optional module" pattern: `install()`/`tierFor()` null-safe until cops-n-crooks installs a lookup | `GanglandApiImpl` constructor param (mandatory) | external plugin | `tierFor(bank)` returns `null` when no bank module installed |
| `PlaceholderContribution`/`GangMembership` (WS5's contracts, R7/R9) | library files in `gangland-api`'s `contract` package, consumed like every other `Contract`/`Contribution` — **not** facade accessors | gang module (`PlaceholderContribution`) / core-owned default + gang-module `.install(...)` (`GangMembership`, R9) | core `GanglandPlaceholder`/any "is/which gang" site | absent module → unresolved placeholder returns `null` / `GangMembership` stays at its inert default — no facade involvement |

**Reconciliation with WS5 (B1, resolved by R7):** this plan's earlier draft argued "WS5 wins on placement" and put
`GangLookupContract`/`RankLookupContract` entirely inside the `gangland-gang` module with no core-facing trace —
that cannot compile: `gangland-api` has no dependency on any module artifact and the repo `CLAUDE.md`'s "Two
tiers" rule forbids the reverse. The orchestrator's ruling (R7) keeps this plan's actual *mechanism* — lazy,
per-call `Optional` resolution, never a `BankTiers`-style inert-default holder nobody in WS5's consumer list
needs — and fixes only the placement: the two interfaces live in `gangland-api` (where any api consumer, module
or external plugin, can compile against them), the gang module ships their sole implementations. No new
abstraction was added to make this compile; `GangLookupContract`/`RankLookupContract` simply join
`WaypointLookupContract` as api-resident seam interfaces with an impl elsewhere.

### Service table (C4 — the doc's centre, `documentation/gangland-api.md` step 14)

| Service class | Registered by | Priority | Consumed by |
|---|---|---|---|
| `org.luckyraven.gangland.GanglandApi` | **Gangland**, `WiringConfig.ganglandApi(...)` (new, this plan) | Normal | any external plugin (no named consumer today) |
| `org.luckyraven.keystone.item.spi.ItemVocabulary` | Bartizan, `ItemConfig.bartizanItemVocabulary(...)` (`ItemConfig.java:104-119`) | Normal | **Gangland core *consumes*, does not publish** — `GanglandContext.java:215` (`getRegistrations(ItemVocabulary.class)`), folded in `installItemVocabularies()` |
| `org.luckyraven.bartizan.api.BartizanApi` | Bartizan, `WiringConfig.bartizanApi(...)` (`WiringConfig.java:157-165`) | Normal | Gangland's civilians/cops-n-crooks/gadget modules |
| `org.luckyraven.bartizan.api.raytrace.WeaponRaytracer` | Bartizan, `WiringConfig.weaponRaytracer(...)` (`WiringConfig.java:99-104`) | Normal | Bartizan's own `NpcWeaponControllerImpl`; open to third parties |
| `org.luckyraven.bartizan.api.combat.CombatEligibility` | **the consumer** registers it (Gangland could, doesn't today); Bartizan **pulls** via `CombatEligibility.resolve()` | Normal | Bartizan's `WeaponInteract`/reload controllers |
| Oriel: `MenuRegistry`, `MenuOpener`, `ActionRegistry`, `SourceProviderRegistry`, `FilterRegistry`, `SortRegistry`, `SearchMatcherRegistry`, `ItemProviderRegistry`, `MaterialSourceRegistry`, `ComponentRegistry`, `DatabaseBackendRegistry`, `PacketAdapter` | Oriel, `OrielPlugin.registerServices()` (`menu-plugin/.../OrielPlugin.java:178-197`) | (per-registry) | Gangland's `OrielMenuConfig` (WS2), every menu-owning module |
| `org.luckyraven.keystone.placeholder.PlaceholderProvider` (**C5**) | **Gangland**, WS1-D5's `WiringConfig` addition — `GanglandPlaceholder.asProvider()` (`GanglandPlaceholder.java:36`), `PlaceholderHandler.asProvider()` (Keystone `keystone-common/.../placeholder/PlaceholderHandler.java:71`) — same seam already proven for `ItemVocabulary` | Normal | `<ScoreboardPlugin>`'s `PapiText` (WS1), when PAPI itself is absent/has no answer |

**Not services — container beans instead (C4):**
- `ShopAdminOpener` (WS4) — a one-method interface in `gangland-api`, registered under its interface type via a
  plain `@Bean`/`DependencyContainer` entry (`WS4-shop.md` §3: "registered under the interface type"), pulled by
  `TraderModuleConfig.shopViewOpener()`. Never touches `ServicesManager`.
- `GangMembership` (WS5, R9) — a core-owned holder in `gangland-api`, `BankTiers`-shaped, installed by the gang
  module's `@PostConstruct`. Also never touches `ServicesManager`.

Zero new abstractions beyond the facade interface itself; every accessor returns an interface that already exists
(or, for `gangs()`/`ranks()`, will once the gang module loads), reused verbatim. This is the load-bearing ponytail
argument: the marginal cost of "most features reachable through the api" is the `ServicesManager.register` call
plus a two-file interface relocation (B1/R7), not new seam design.

## 4. Steps

### G-Pre — prerequisites, verification only (repo: Gangland; not the branch-wide G0 — C7)

1. **S** — [B2] Confirm `UserLookupContract`, `GangLookupContract`, `RankLookupContract`, `PermissionRegistryContract`
   compile from `gangland-api` (moved there by WS5's reworked plan, per R7) — today all four live under
   `gangland-infra/gangland-domain/.../gang/contract/`, verified by `find`. **WS6's G1 does not start until this
   is true**; without it `gangland-api` itself does not compile.
2. **S** — [C7] Confirm the branch's G0 commit (R1) already bumped `GanglandApi.VERSION` and every existing
   `module.yml`'s `Host_Api` to `2.0`, and that WS2's `inventory-api` pom line + WS4's `shop-api` pom line/
   `GanglandShopDisplayResolver.java` are gone from `gangland-api`. WS6 verifies, does not perform, any of this.
3. **S** — [C8] Confirm `gangland-lootchest/module.yml` (WS3) and `gangland-gang/module.yml` (WS5) exist, both
   declare `Host_Api: 2.0` at creation, and that `gangland-build/pom.xml`'s shade `<excludes>` (`:79-86`, 6
   entries today) has grown to 8 or otherwise keeps both new modules out of the core jar. Flag to WS3/WS5 as the
   owning fix if not.

### G1 — facade + registration + tests (repo: Gangland, `gangland-api` + `gangland-impl`)

4. **S** — `GanglandApi.java`: `final class` → `interface`; drop the private constructor; add **5** abstract
   methods (`UserLookupContract users()`, `Optional<GangLookupContract> gangs()`, `Optional<RankLookupContract>
   ranks()`, `WaypointLookupContract waypoints()`, `BankTiers bankTiers()`). [C3] 32 existing call sites across 26
   files read `VERSION`/`FULL_PREFIX`/`SHORT_PREFIX` as static field access (`gangland-api` 3, `gangland-impl` 15,
   `gangland-features` 8 — cops-n-crooks 5, civilians 2, turf 1, e.g. `CopSpawnerInfoCommand.java:64`,
   `CopsNCrooksModuleConfig.java:140`) — all compile unchanged on an interface, verified across the full reactor,
   not just impl/api. No `keystone-module` dependency, no `ModuleInfo` (S1). Commit boundary: this step alone,
   compiles.
5. **S** — `gangland-impl/.../GanglandApiImpl.java` (new): constructor takes `UserLookupContract`,
   `WaypointLookupContract`, `BankTiers` (mandatory) plus a `DependencyContainer` reference. [C1] `gangs()`/
   `ranks()` are one-liners: `Optional.ofNullable(container.getInstance(GangLookupContract.class))` /
   `...(RankLookupContract.class)` — `DependencyContainer.getInstance(Class)` returns `null` on a miss and never
   throws (`keystone-bean/.../autowire/DependencyContainer.java:56-62`), so no try/catch, no Keystone ask. Mirrors
   `BartizanApiImpl.java`'s shape for the 3 mandatory accessors.
6. **S** — `WiringConfig.java`: `@Bean public GanglandApiImpl ganglandApi(UserLookupContract users,
   WaypointLookupContract waypoints, BankTiers bankTiers, DependencyContainer container)` — construct,
   `Bukkit.getServicesManager().register(GanglandApi.class, api, gangland, ServicePriority.Normal)`, return.
   Mirrors `WiringConfig.java:157-165` (Bartizan's `bartizanApi` bean).
7. **S** — [B3] `Gangland.java` `onDisable()`: `getServer().getServicesManager().unregisterAll(this)` is the
   **first statement**, before the `if (context == null) return;` early return at `Gangland.java:83` — a
   bootstrap that fails after `WiringConfig`'s bean has already registered the service must not leave a dead
   provider. Precedent: `Bartizan.java:47` runs `unregisterAll` before its own `:50` early return, comment "a
   disable/enable cycle must not leave dead providers behind." [C2] Baseline correction: `Gangland.java:188`
   already does one `ServicesManager` *read* (`Economy`) — say "Gangland registers nothing today," not "no call
   at all."
8. **M** — [S3] `GanglandApiImplTest` (unit): the 3 mandatory accessors return exactly the constructor-injected
   instances; `gangs()`/`ranks()` return `Optional.empty()` against a stubbed `DependencyContainer` with no
   registration and the wrapped value against one that has it. Plus a `WiringConfig`-focused mock test verifying
   `register(GanglandApi.class, impl, gangland, Normal)` fires once with the right instance (Mockito, static-mock
   `Bukkit` the way `BartizanNpcWeaponsTest.java:41-49`'s `mockBukkit(BartizanApi)` helper does). This is the
   **entire** test step — no separate "scenario test": the enable/disable and module-present/absent cross-product
   is smoke row M2 (§7), not a second JUnit artifact. Gate ends in
   `mvn -pl gangland-api,gangland-impl -am clean install` green + smoke row **M2**.

### G2 — events consolidation (repo: Gangland, `gangland-api`)

9. **S** — **Correct the census.** `gangland-impl/.../events/` holds only `gang/GangBountyEvent.java`,
   `gang/GangLevelUpEvent.java`, `user/UserDataInitEvent.java` — **no** `TeleportEvent`/`UserLevelUpEvent`
   duplicate of the api versions (zero hits by exact filename). The census's "duplicate events need
   consolidation" finding is wrong; no merge step needed. Record this correction in `gangland-api.md`'s changelog
   note. Also name `UserDataInitEvent` (`PlayerBootstrapService.java:15`) explicitly in the doc's "not public api"
   list, so a reader doesn't ask why the user lifecycle has one public event (`UserLevelUpEvent`) and one private
   one.
10. **S** — Decide (and document) `gangland-api`'s `events/*` package stays at its current 2 events
    (`TeleportEvent`, `UserLevelUpEvent`) — not expanded. Lootchest's 9 events are declined by WS3 (no consumer
    named); `ShopEditedEvent` trends toward a Keystone-side hook (WS4); gang/wanted/bounty events move into the
    `gang` module with the rest of `gang.*` (WS5) and, if a future consumer needs one, take the turf→civilians
    `Depends:`+pom-dependency precedent, not an api promotion. **Future-event rule**: cancellable if fired before
    the action completes (`TeleportEvent.java:16`, `LevelUpEvent.java:9` both `implements Cancellable`), not
    cancellable if fired after (a notification) — mirrors Bartizan's `WeaponStatusApplyEvent`(cancellable)/
    `WeaponStatusExpireEvent`(not) split.

### G3 — module-owned Messages/Settings migration mechanism, one worked example (repo: Gangland)

11. **M** — [C6] Pick **`gangland-civilians`** as the worked example, not mail. `Messages.MAIL_*` = 4 constants,
    but `gangland-features/gangland-mail/src/main/resources` holds only `commands.json`/`module.yml`/
    `module.properties` — **no YAML, no `FileHandler`, no loader** — mail would need all three built from
    scratch before the first constant moves, which is really an **L** step. Civilians already has the exact
    plumbing to extend: `CiviliansYamlConfig.java:30`
    (`fileManager.addFile(new FileHandler(plugin, "civilians", "npc", ".yml", moduleLoader.classLoader()), true)`)
    and 12 `CIVILIAN_*` constants. Add a message YAML to civilians' existing pipeline, a small `CivilianMessages`
    holder, delete the 12 constants from `Messages.java` only after every caller is repointed (grep-verified).
    Proves the constant→module-YAML mechanism without inventing a module's first config pipeline — stays **M**.
12. **S** — Write the **per-module migration table** (not execute it) for the remaining prefixes: `GANG` 66,
    `TURF` 59, `DETAINMENT` 45, `BANKER` 29, `SHOP` 29 (moot post-WS4 — `keystone-shop`'s own YAML), `LOOT` 26
    (moot post-WS3 — `gangland-lootchest`'s own YAML), `MODULE` 22, `RANK` 20, `TRADER` 12, `CAR` 10, `FUEL` 8,
    plus the ~130 module-owned `Settings` getters (Lombok `@Getter` on static fields, `Settings.java:27-60`). One
    line per prefix: which module owns it, whether it already has YAML plumbing (like civilians) or needs it
    built (like mail).
13. **S** — Recommendation on instance vs. static (§9 D4): **stay static this wave.** A ~600-call-site refactor
    buys nothing this wave; the YAML-migration mechanism (step 11) is the smaller, in-scope step.

### G4 — docs + graph refresh (repo: Gangland; no `japicmp`, S2)

14. **M** — [C4] Write `documentation/gangland-api.md`: resolution snippet, the **real service table** (§3 — not
    a forward-reference to a nonexistent section), the 5-accessor table, the 2-event table + future-event rule +
    the `UserDataInitEvent` note (step 9), a "what Gangland does not use" section (no `wanted()`/`bounty()`/
    `placeholders()`/`shopRegistry()`/`modules()` — one-line reason each), and an explicit "container beans, not
    `ServicesManager` services" callout for `ShopAdminOpener` (WS4) and `GangMembership` (WS5, R9). [S2] One
    follow-up line: "wire `japicmp` when a second `2.x` release of `gangland-api` exists on a resolvable
    repository" — no plugin config lands this wave.
15. **S** — Update `documentation/module-loader.md` `## Core seams` (`:174`) with the `ServicesManager` facade
    row; note the eight-module list in `## Module descriptor`; [C8] re-check `gangland-build/pom.xml`'s shade
    `<excludes>` (in case WS3/WS5 landed after step 3's check), naming them as owners of any fix still needed.
16. **S** — Write-back check: this wave's docket table (§11) implies a `write_db` pass on the cross-project
    docket artifact (`collection: bugs`) per the CLAUDE.md rule that a fix lands with a status row — WS6 fixes no
    docket-filed bug (KS-MO-05/06/07 stay untouched), so this step records "no bugs fixed by WS6" explicitly
    rather than silently skipping the check out of habit.
17. **S** — `graphify update . --force` + re-run the orientation queries. Note going in: `graphify affected
    "GanglandApi"` returned **"No affected nodes found"** pre-wave (constants-only classes get no edges) —
    re-check post-conversion whether the new interface/accessors produce edges the old class didn't.

Gate-ending build: each of G1/G3/G4 ends in `mvn -pl gangland-api,gangland-impl -am clean install` green (full
reactor only at the wave's own G-final, owned by the orchestrator per C1).

## 5. Config, messages, permissions

| Item | Today | After | Note |
|---|---|---|---|
| `GanglandApi.java` | `final class`, 3 constants | `interface`, 3 constants + 5 methods | §2 |
| `gangland-api/pom.xml:42` `inventory-api` | present | deleted | WS2 |
| `gangland-api/pom.xml:50` `shop-api` | present | deleted, `keystone-shop` provided added | WS4 |
| `gangland-api` `contract/` package | 0 gang-domain contracts | +4 (`UserLookupContract`/`GangLookupContract`/`RankLookupContract`/`PermissionRegistryContract`, B2/R7) +2 new (`PlaceholderContribution`, `GangMembership`/R9) | §2, WS5 lands the move |
| 8× `module.yml` `Host_Api` | `1.0` | `2.0` | branch G0 (R1), WS6 verifies only (C7) |
| `Messages.CIVILIAN_*` (12 constants) | `gangland-api/.../Messages.java` | deleted, moved to civilians' own message YAML | step 11 (C6) |
| `settings.yml` | untouched by this plan | untouched | no core settings knob added/removed here |
| `commands.json` | untouched | untouched | no new command in this plan |
| Permissions | untouched | untouched | facade is read-only, no new permission node |

## 6. Persistence

None. The facade is a stateless pass-through over already-persisted beans (`UserManager` via `UserLookupContract`,
`GangManager` via `GangLookupContract` when the gang module loads, `WaypointManager`, `BankTiers`) — no new
table, no new repository, no migration.

## 7. Tests

| Test | Asserts | New/moved |
|---|---|---|
| `GanglandApiImplTest` | 3 mandatory accessors return exactly the constructor-injected instances; `gangs()`/`ranks()` return `Optional.empty()`/populated per a stubbed `DependencyContainer` (C1) | new (step 8) |
| `WiringConfig` register-call test | `ServicesManager.register(GanglandApi.class, impl, gangland, Normal)` fires once, with the right instance | new (step 8), pattern from `BartizanNpcWeaponsTest.java:41-49` |
| `civilians` message YAML | migrated constants render identically to today's `Messages.CIVILIAN_*` output | new (step 11) |
| Smoke row **M2** | console harness: load Gangland (with and without the `gang` module present), resolve `GanglandApi` from a test plugin, call all 5 accessors, disable, confirm de-registration and, separately, that a failed enable never leaves a provider registered (B3) | new (step 8/7), style of `../bartizan-split-2026-09-08/smoke/` |

No existing test moves or flips in this plan — WS1/2/3/4/5 own the tests that move with their own file relocations.
[S3] The old "scenario test" step is gone — its assertions live in `GanglandApiImplTest` (unit) and smoke row M2
(integration), not a third artifact.

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | `GanglandApi` interface conversion breaks a call site the grep missed | `mvn clean install` at step 4's commit boundary before any further step; [C3] the full-reactor grep found 32 hits/26 files including 8 in `gangland-features` — all static-field reads, all compile unchanged, verified rather than assumed |
| 2 | `gangs()`/`ranks()`'s `Optional` design leaks the api naming a module type | Resolved by R7/B1: the interfaces live in `gangland-api`, the gang module ships only implementations — `gangland-api` never imports anything module-scoped |
| 3 | The `civilians` worked example (step 11) undercounts effort for the harder modules (`GANG`/`TURF`/`DETAINMENT`) since civilians has only 12 constants vs. their 40-66 | Flagged in step 12's table as "not executed, sizing is a guess" — the estimate (§12) does not extrapolate civilians' cost onto the other prefixes |
| 4 | `unregisterAll` ordering (B3) gets silently reverted by a future edit that "cleans up" `onDisable()` | The doc (step 14) states the ordering requirement explicitly with the Bartizan precedent cited, so a future editor has the reasoning, not just the diff |

**Rollback per gate:** G1 — revert the 4 touched files, `gangland-api` returns to a `final class`; no persisted
state, no migration to undo. G2 — doc-only, revert the markdown. G3 — revert civilians' YAML addition, restore
the 12 `Messages` constants (git revert, no data migration). G4 — revert docs; `graphify update --force` is
idempotent, re-run is free.

## 9. Decisions for the user

| # | Decision | Recommendation | If the other option wins |
|---|---|---|---|
| D1 | Facade type name: keep `GanglandApi` (interface, same constants) vs. `GanglandServices` | **Keep `GanglandApi`.** 32 existing call sites (C3) read the constants as static field access, compiles unchanged on an interface | A new name means renaming the type everywhere it's imported for its constants (~15 files) for no functional gain |
| D2 | Facade accessor list: the 5 in §3 vs. also `wanted()`/`bounty()`/`shopRegistry()`/`placeholders()`/`modules()` | **Ship only the 5** (S1 cuts `modules()` too — literally Bartizan's own accessor count). No existing lookup contract for wanted/bounty; WS4 recommends against `shopRegistry()`; `PlaceholderService` has zero WS1-5 consumers; WS5's two contracts (`PlaceholderContribution`, `GangMembership`) are internal seams, not facade-shaped | Adding any of these means designing a new contract first — out of this plan's scope |
| D3 | `gangs()`/`ranks()` placement and optional-module semantics | **R7, not a fork anymore.** Interfaces in `gangland-api` (B1 forced this — the api cannot name a module type), implementations in the gang module, `Optional` resolved lazily per call via `container.getInstance(...)` (C1) | N/A — this is the plan |
| D4 | `Messages`/`Settings` instance beans vs. stay static | **Stay static.** D8 (2026-09-10) already made this call; the YAML-migration mechanism (step 11) is the smaller, in-scope step | Wrapping both in beans is a ~600-call-site refactor, out of budget |
| D5 | `japicmp` timing | **Deferred until a Central release exists** (S2 — no config lands this wave, one follow-up doc line) | Wiring it now means a permanently-skipped plugin block with nothing to diff against — inert config that documents its own inertness |
| D6 | Which events are public in `gangland-api` | **Keep the current 2** (`TeleportEvent`, `UserLevelUpEvent`); decline lootchest/shop/gang-domain promotion | If a future consumer needs a gang/wanted/bounty event, it takes a `Depends: [gang]` pom edge directly |
| D7 | Should runtime modules resolve the facade via `ServicesManager` instead of DI | **No** — not actually a fork (reviewer's note): modules already have DI, the facade is explicitly the external-plugin surface | N/A |

## 10. WS6 asks / Oriel asks / Keystone asks

- **From WS1** (§10, WS1-scoreboard.md, **C5 correction** — the plan previously said "none," which contradicted
  WS1's own D5): WS1's D5 publishes Keystone's `PlaceholderProvider` from Gangland's `WiringConfig`
  (`GanglandPlaceholder.asProvider()`) for `<ScoreboardPlugin>` to pull when PAPI has no answer. This is not an
  *accessor* WS6 owes on the facade — it is a second Gangland-owned `ServicesManager` registration alongside
  `GanglandApi` itself, which (a) makes B3's `unregisterAll`-ordering fix load-bearing for two providers, not one,
  and (b) belongs in the service table (§3). WS1 keeps the register call; WS6 documents it.
- **From WS2** (§10, WS2-inventory-oriel.md): "confirm `gangland-api` 2.0 re-exports `oriel-core`/`oriel-chest`/
  `oriel-anvil`." **Disagreement, arbitrated as R6** (`PLAN.md` §2): `gangland-api` 2.0 does **not** re-export
  Oriel artifacts — a module that builds a menu declares `oriel-*` in its own pom, the same `bartizan-api` rule
  ("a consumer declares what it names"). R6 settles this; no further action.
- **From WS3** (§10, WS3-lootchest-hologram.md): (a) `gangland-lootchest/module.yml` carries `Host_Api: 2.0` at
  creation — noted, not WS6's step (C7). (b) 9 loot-chest events not promoted into the facade — done, §3 step 10.
- **From WS4** (§10, WS4-shop.md): (a) `gangland-api` 2.0 drops `shop-api`, adds `keystone-shop` — done, §2. (b)
  recommend no `ShopRegistry()` facade accessor — done, §9 D2. (c) `GanglandShopDisplayResolver` re-homed to
  `keystone-shop/.../DefaultShopDisplayResolver.java` — done, §2. (d) `ShopAdminOpener` is a DI bean, not a
  service — done, §3 service table.

### §10b — from WS5 (`plans/WS5-gang.md`, under rework per R7/R9)

- **B1/R7, resolved**: `UserLookupContract`/`GangLookupContract`/`RankLookupContract`/`PermissionRegistryContract`
  **interfaces move into `gangland-api`** (not the gang module) — the api can never name a module class. The gang
  module ships their sole implementations. §4 step 1 makes this an explicit G1 precondition.
- **`gangs()`/`ranks()` optional-module semantics**: resolved (§9 D3) — `Optional`, resolved lazily per call
  against `container.getInstance(...)` (C1), no core-side inert-default holder needed since no WS5 consumer
  (turf/mail/gadget/civilians, all hard-`Depends: [gang]`) ever observes absence; the facade is the one consumer
  that can.
- **Which gang events become public api events**: none — `GangBountyEvent`/`GangLevelUpEvent` move into the gang
  module (docket GR-19), `Bounty*`/`Wanted*`/`LevelUpEvent` move into `gangland-core` (transitively api-reachable,
  no new api file). Confirms §3 step 10.
- **Contracts WS5 wants in the api**: `PlaceholderContribution` (unchanged ask) and — **superseded, R9** — the
  originally-proposed `MembershipLookupContract` is replaced by a shared `GangMembership` holder (`BankTiers`
  shape: interface + core-owned inert default + `.install(...)` from the gang module's `@PostConstruct`), which
  also removes `Depends: [gang]` from gadget/civilians/cops-n-crooks in WS5's reworked table (§2's "Host_Api /
  module descriptor sweep" section cites, does not restate, WS5's table for exactly this reason — it is mid-rework).
  Both land as library files in `gangland-api`'s `contract` package, consumed via existing DI — **not** facade
  accessors (§3 service table's "not services" callout).

**Oriel asks:** none beyond R6 (above). **Keystone asks:** none — S1 removed the one new dependency this plan had
proposed (`keystone-module`); nothing else is needed.

## 11. Docket

| Id | Tier | Status | Relevance |
|---|---|---|---|
| KS-MO-05 | — | open (cosmetic) | Keystone-side, module loader; unaffected by this plan |
| KS-MO-06 | — | open (cosmetic) | Keystone-side; unaffected |
| KS-MO-07 | — | open ("bootstrap isolation gap") | **Not fixed by this plan** — the api contract + `Host_Api` floor are documented as "the prevention," not the fix. Confirmed via `read_db` on the cross-project docket: no status rows written for any of the three, meaning open per the CLAUDE.md rule "missing row = unrecorded, not new" |

No Gangland-repo-specific docket id exists for "external plugins can't reach Gangland services" — a design gap,
not a bug; nothing filed to `triage/`. Step 16 (§4) is the explicit write-back check the review flagged as
missing — this wave fixes no docket-filed bug, so it records that fact rather than skipping the check silently.

## 12. Estimate

17 steps across 4 gates + 1 prerequisite gate (was "G0," renamed to G-Pre per C7 to avoid colliding with the
branch-wide G0).

| Gate | Steps | Size mix | Hours |
|---|---|---|---|
| G-Pre | 3 | S, S, S | 0.5 h |
| G1 | 5 | S, S, S, S, M | 3 h |
| G2 | 2 | S, S | 0.5 h |
| G3 | 3 | M, S, S | 3 h |
| G4 | 4 | M, S, S, S | 3.5 h |
| **Total** | **17** | **14 S / 3 M / 0 L** | **~10.5 h** |

The reviewer's own estimate check put this plan at "~12-14h realistic" against the first draft's claimed 8.5h,
with G3 costed at ~5h *if mail stays the worked example* (three new files — `FileHandler`, loader, YAML — before
the first constant moves) and ~3h if swapped to a module that already has the plumbing. C6 makes that swap
(civilians), which is where most of the gap between this table's ~10.5h and the reviewer's 12-14h band comes from;
the rest (docket write-back step 16, `gangland-build` shade cross-check in steps 3/15) is now costed here where the
review found it missing. **~10.5-11h**, landing just under the reviewer's band specifically because of the C6
swap — flagged here rather than silently claiming the higher number without justification.

## 13. Not verified

- **WS5's own `IdentityContractConfig`/`GangMembership` implementation does not exist yet** — WS5 is being
  reworked (R7/R9) as this plan is written; the four relocated contract interfaces and the new `GangMembership`
  holder are this plan's best reading of the orchestrator's rulings, not a re-read of WS5's actual reworked file.
  Re-confirm §2/§3/§10b against `plans/WS5-gang.md`'s next revision before G1 starts (§4 step 1 already makes
  this a hard precondition, not just a caveat).
- **Keystone's `ModuleDescriptor.isCompatibleWith` current line numbers** were not re-confirmed this session —
  relied on the 2026-09-10 README's citation rather than re-reading Keystone's `phase-h9-host-api` branch
  directly.
- **`inventory-api` import scan was limited to `gangland-api`'s own source tree**, not re-run across
  `gangland-features/*`. Re-run `grep -rl "org.luckyraven.gangland.inventory" gangland-features/*/src/main/java`
  before deleting the `inventory-api` pom line.
- **No `mvn` build was run this session** — planning-only pass; every "compiles unchanged" claim (C3, D1) is
  grep-evidenced, not compiler-verified.
- **Whether `context.reloadBeans()` re-runs `WiringConfig`'s `@Bean` body and registers `GanglandApi` a second
  time** (Bukkit appends rather than replaces a `RegisteredServiceProvider`) was not checked — Bartizan has the
  same shape and no managed reload, so it's untested there too. A guard
  (`if (Bukkit.getServicesManager().getRegistration(GanglandApi.class) == null)`) or a note that `BeanLifecycle`
  reload never re-instantiates beans closes this; flagged for the executor, not resolved here.
- **The exact byte-for-byte civilians message-YAML key list** for the 12 `CIVILIAN_*` constants (step 11) was not
  enumerated — the executor reads `Messages.java`'s `CIVILIAN_*` entries directly before drafting the YAML.
