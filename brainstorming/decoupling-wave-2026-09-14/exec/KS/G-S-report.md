# KS G-S (keystone-shop) + G-D (CLAUDE.md / phase doc) report — 2026-09-16

Status: DONE

## What changed (worktree-relative to `E:\Programming\java\wt\keystone-1.10.0`)

### G-S — new module `keystone-shop`

- `pom.xml`: new `<module>keystone-shop</module>` (after `keystone-hologram`); new `dependencyManagement` entry.
- `keystone-shop/pom.xml` — new, mirrors `keystone-npc/pom.xml`'s shape but **no `keystone-bean`** (S1: zero-DI
  pure library). Deps: `keystone-item`, `keystone-persistence`, `keystone-common`, `XSeries`, `log4j-api`
  (compile); `mockito-core`/`keystone-testkit` (test).
- `keystone-shop/src/main/java/org/luckyraven/keystone/shop/` — 27 files, package rename
  `org.luckyraven.gangland.shop` → `org.luckyraven.keystone.shop`, bodies unchanged:
  - Top-level (6): `ShopDefinition`, `ShopItemEntry`, `ShopRegistry`, `BarterCategory`, `SellCategory`, `EntryKind`.
  - `.event` (1): `ShopEditedEvent`. `.io` (2): `ShopYamlReader`, `ShopYamlWriter`.
  - `.message` (3): `ShopDisplayResolver`, `ShopMessageContract` (ported), plus **`DefaultShopDisplayResolver`**
    — renamed from `gangland-api`'s `GanglandShopDisplayResolver`, package
    `org.luckyraven.gangland.file.configuration.shop` → `org.luckyraven.keystone.shop.message`, "for gangland"
    wording stripped from its javadoc, redundant same-package self-import removed.
  - `.transaction` (11): `BarterOutcome`/`BarterResult`, `PaymentException`, `PaymentHandler`, `PurchaseOutcome`/
    `PurchaseResult`, `SellOutcome`/`SellResult`, `ShopBarterService`/`ShopPurchaseService`/`ShopSellService`.
  - `.valuation` (4): `CategoryBarterValuator`, `CategorySellValuator`, `ItemValuation`, `SellValuator`.
  - Excluded from the move (confirmed against WS4 plan §0/§2, stay in Gangland): `config/ShopUiSettings.java`,
    `handler/ShopEditPersistenceHandler.java`, the 9 UI-coupled files under `shop.view`/`shop.listener`.
- `keystone-shop/src/test/java/org/luckyraven/keystone/shop/` — 8 files ported (package rename only):
  `ShopDefinitionTest`, `io/ShopYamlReaderTest`, `support/FakePaymentHandler`,
  `transaction/{ShopBarterServiceTest,ShopPurchaseServiceTest,ShopSellServiceTest}`,
  `valuation/{CategoryBarterValuatorTest,CategorySellValuatorTest}`. Plus new
  `message/DefaultShopDisplayResolverTest` (3 cases — first-ever coverage for that class).
- `keystone-plugin/pom.xml` — new `keystone-shop` dependency block, shaded into `Keystone-1.10.0.jar`.
- `docs/keystone-shop.md` — new (what-stayed-behind, economy seam, display resolution, class table, test-fixture
  note).

### G-S deviation of substance: `BukkitRegistryFixture` (C6) could not port as planned

WS4 plan's C6/G0-step-2 asked to port Gangland's `gangland-core` test fixture `BukkitRegistryFixture` into
`keystone-testkit`, before moving the 7 tests that call it. **This is not possible at Keystone's API floor**: the
fixture's core mechanism, `Server.getRegistry(Class<T>)` (a generic `Registry<T>` overload), and its
`org.bukkit.block.BlockType` import, do not exist on Spigot 1.16.5 (`bukkit.version` in Keystone's root pom) —
both were added to Bukkit later than that. Gangland compiles against 1.21.11, which is why the fixture worked
there.

Investigated further and found the real need was narrower: of the 7 moved tests, only **one**
(`ShopBarterServiceTest`, via `ItemStack.clone()` → `Server#getItemFactory()`) touches Bukkit API that reads
`Bukkit.server` directly; the other 6 pass with **no** server installed at all under the 1.16.5 test classpath
(the original fixture exists to work around `Material.isAir()`'s 1.21-era `Registry.BLOCK` resolution, which the
1.16.5 API doesn't have — there `isAir()` is still a plain `switch`).

Resolution, verified red→green (see below): a new, smaller `keystone-testkit` fixture —
`keystone-testkit/src/main/java/org/luckyraven/keystone/testkit/BukkitServerFixture.java` — installs a real
`Bukkit.setServer(...)` (not `mockStatic`; `BukkitStatics` can't intercept a direct static-field read) with only
`getItemFactory()` stubbed. Wired into `ShopBarterServiceTest` alone (`@BeforeAll` + import); the other 6 ported
tests carry no fixture call at all (their original `@BeforeAll bootstrapBukkitRegistry()` blocks and the
now-unused `BukkitRegistryFixture`/`BeforeAll` imports were removed). A `ponytail:` class javadoc on
`BukkitServerFixture` records that a consumer needing registry-backed lookups on a newer Bukkit API needs its own
fixture — this one only covers what Keystone's floor can compile.
- Also fixed the two valuator tests' `org.luckyraven.gangland.item.ItemKind.MATERIAL` (a Gangland-specific enum,
  not portable) → `org.luckyraven.keystone.item.StandardItemKind.MATERIAL` (Keystone's own shipped constant for
  the same value — same `label()`, no behaviour change).
- Added `log4j-api` to `keystone-shop/pom.xml` (missed in the first pom draft; needed for `@CustomLog` in
  `ShopRegistry`/`ShopYamlReader`/`ShopYamlWriter`, mirroring `keystone-npc`/`keystone-item`).

### G-D — CLAUDE.md sentence, phase doc, README

- `CLAUDE.md` (line 101, the layering bullet) — **exactly one added sentence**, appended after the existing text:
  *"As of 1.10.0 (phase H10), hologram code lives in `keystone-hologram` and the headless shop
  transaction/valuation system lives in `keystone-shop` — both generic enough to belong here, superseding the
  'hologram' and 'trader shops' exclusions above for those two systems specifically, while UI-coupled shop admin
  views and scoreboard code still stay in the consumer (see `docs/keystone-hologram.md` /
  `docs/keystone-shop.md`)."* The pre-existing "Scoreboard/hologram code stays in Gangland" and "...trader
  shops..." phrases are left in place (they still hold for scoreboard code and for the UI-coupled/economy-specific
  half) — the new sentence explicitly supersedes them for the two promoted systems rather than editing the old
  wording in place.
- `docs/phase-h10-hologram-shop.md` — new, pattern of `docs/phase-h9-host-api.md`: covers both modules, the
  `BukkitRegistryFixture` finding above, the CLAUDE.md/jar-audit summary, and the reactor test count.
- `docs/README.md` — 2 new rows (`keystone-hologram.md`, `keystone-shop.md`) + 1 new row
  (`phase-h10-hologram-shop.md`) in the documentation map.
- `docs/keystone-testkit.md` — 1 new row for `BukkitServerFixture` in the "What's in it" table.
- `README.md` (repo root) — 2 new rows in the `## Modules` table (`keystone-hologram`, `keystone-shop`); the
  `keystone-testkit` row's description extended to mention `BukkitServerFixture`.

Not touched (out of this gate's named scope): the `## Module dependency graph` ASCII diagram in `CLAUDE.md`
(lines ~72-97) does not list `keystone-hologram`/`keystone-shop` — it was already missing `keystone-npc` and
`keystone-module` before this gate, so it's pre-existing staleness, not something this gate's "exactly one
sentence" instruction covers. Flagged under Concerns below.

## Deviations from the plan

1. `BukkitRegistryFixture` — see the G-S section above; not a deviation from the *goal* (7 tests still work,
   plus the 8th new one), but a real deviation from the *mechanism* WS4 plan §4 step 2 specified, driven by a
   genuine Bukkit-API-floor incompatibility the plan's "Not verified" section (§13) had flagged as a risk but not
   confirmed either way.
2. `ItemKind.MATERIAL` fix in the two valuator tests (Gangland-specific enum → Keystone's `StandardItemKind`) —
   not named in the plan at all; found by grepping the test files' imports before porting.
3. `log4j-api` pom dependency — not explicitly listed in either plan's Keystone-side dependency table; added after
   the first compile failure (mirrors the sibling modules' own poms).
4. Everything else (file lists, package layout, exclusions, CLAUDE.md wording, phase-doc shape) matches the plans
   as written.

## Red-first evidence

Every new/ported test in this gate is a **green-on-arrival wiring test or an unchanged carry-over**, per both
plans' own "genuinely red" notes (WS3 §7, WS4's docket table) — no docket bug is fixed here, so nothing was
expected to start red for that reason. Two genuine red→green cycles did happen, both caused by porting mechanics
rather than by a bug in the ported production code:

- Red: `mvn -pl keystone-testkit,keystone-shop -am test` → `COMPILATION ERROR` (`BlockType`/`Server.getRegistry`
  missing at the 1.16.5 floor) → after deleting the incompatible port, `Tests run: 62 (56+... ), Errors: 1` —
  `ShopBarterServiceTest.barter_success_clonesOfferedIntoConsumed` NPEs on `Bukkit.server` being null.
- Green: after adding `BukkitServerFixture` (with the logger/name/version stubs `Bukkit.setServer` needs before it
  will even accept the mock) and wiring it into that one test: `mvn -pl keystone-testkit,keystone-shop -am test`
  exit 0. Per-class summary (`keystone-shop/target/surefire-reports/*.txt`): `ShopYamlReaderTest` 16,
  `DefaultShopDisplayResolverTest` 3, `ShopDefinitionTest` 7, `ShopBarterServiceTest` 7, `ShopPurchaseServiceTest`
  6, `ShopSellServiceTest` 9, `CategoryBarterValuatorTest` 6, `CategorySellValuatorTest` 8 — all 0
  failures/errors.

`keystone-hologram`'s own red→green cycle was reported in `G-H-report.md` (two test-authoring bugs in my own new
tests, not production code).

## Build

Final command: `mvn clean install` (full reactor, worktree root) → exit code 0.
Aggregated across every module's `target/surefire-reports/*.txt`: **Tests run: 1106, Failures: 0, Errors: 0,
Skipped: 0** (up from 1044 at the G-H checkpoint: +10 `keystone-hologram`, +62 `keystone-shop`, +... minus the
removed `BukkitRegistryFixture` attempt which never landed in a green state).
`keystone-plugin/target/Keystone-1.10.0.jar` produced. Shade audit: `unzip -l ... | grep keystone/hologram` → 4
entries; `grep keystone/shop` → 34 entries; `grep -i "testkit\|mockito"` → 0 matches (test-scope fixtures did not
leak into the shaded jar).

## Docket ids touched

- `CT-06` (P3, "Slot never validated against Size") — pinning test `ShopYamlReaderTest` moved unchanged, still
  green (still asserts today's behaviour). Not fixed.
- `CT-07` (P2, "Purchase overflow drops item, INVENTORY_FULL never returned") — pinning test
  `ShopPurchaseServiceTest` moved unchanged, still green. Not fixed.
- `CT-04`, `CT-05`, `CT-22`, `CT-23` — their files (`ShopAdminFlow`/`ShopAdminFlowSession`/
  `SellCategoryItemsAdminView`/`ShopRegistry`) are either UI-coupled (stay in Gangland, not this gate) or, for
  `ShopRegistry` (`CT-23`), moved to `keystone-shop` unchanged — carried forward, not fixed, per WS4 plan §11.

Docket candidates (new bugs noticed): none. The `BukkitRegistryFixture` API-floor incompatibility is a planning
gap (plan §13 already flagged it as unverified), not a product bug — recorded here and in the phase doc rather
than filed to the docket.

## Subagents used

None, in either gate. File counts (26 main + 8 test for shop, 3 main + 2 test for hologram) were small and fully
specified enough from the plans' own §0/§2 tables to script directly (copy + `sed` package rename), verified
immediately by build — faster than subagent round-trips for this size, and the two real findings (API-floor
incompatibility, `ItemKind` fix) needed judgment calls made while reading compiler output, not mechanical
transcription.

## Concerns / open questions

1. **`BukkitRegistryFixture` non-port is the one substantive technical finding from this gate** — worth the
   orchestrator's attention beyond the docs already written: any *future* Keystone-side test needing
   `Material.isAir()`-style registry resolution against a newer Bukkit API cannot use `keystone-testkit` as-is:
   only `BukkitServerFixture`'s narrower `ItemFactory`-only install exists there now.
2. `CLAUDE.md`'s `## Module dependency graph` ASCII diagram is stale (missing `keystone-npc`/`keystone-module`
   already, now also missing `keystone-hologram`/`keystone-shop`) — left untouched, out of this gate's named
   "exactly one added sentence" scope; flagging in case the orchestrator wants it swept separately.
3. Both plans' Oriel-dependent halves (WS3 G3 admin wand-preview rebuild; WS4 G1b Oriel admin-view rebuild) are
   Gangland-side consumer work, explicitly out of this Keystone stream per the brief ("Nothing in Gangland changes
   in your stream") — not started, not this gate's job.
4. Per the brief, Gangland's own consumer gates (repointing `gangland-ui/hologram-api`/`shop-api` at these new
   Keystone modules, deleting the old Gangland modules) are separate 0.10.0 work, not part of this report.

## Fix round 1

Source: Opus review `exec/KS/G-H-G-S-review.md` (verdict FIX, 1 Important + 6 Minor, code itself passed — every
finding is docs/tests/pom hygiene). Orchestrator ruling W12: fix all 7 in one round.

1. **(Important)** `docs/keystone-shop.md`'s "Test fixture note" rewritten: names `BukkitServerFixture.install()`
   (not the non-existent `BukkitRegistryFixture`), states only `ShopBarterServiceTest` needs it, and states
   registry-backed `Material`/XSeries lookups are out of scope at the 1.16.5 floor — now consistent with
   `docs/phase-h10-hologram-shop.md`.
2. Unused deps dropped: `XSeries` from `keystone-shop/pom.xml` (confirmed zero source references before removing),
   `log4j-api` from `keystone-hologram/pom.xml` (confirmed zero `@CustomLog`/log4j references). Both dropped
   cleanly — full reactor build stayed green, no keeper-with-explanation case needed.
3. Javadoc sweep, "shop-api" → "keystone-shop" (and "EconomyHandler in gangland-impl" → "Keystone's own
   EconomyHandler, in keystone-hooks"): `ShopRegistry.java`, `message/ShopMessageContract.java`,
   `message/ShopDisplayResolver.java`, `transaction/PaymentHandler.java`, `event/ShopEditedEvent.java`. Verified
   `grep -rn "shop-api\|gangland-impl" keystone-shop/src/main keystone-hologram/src/main` now empty.
4. `CLAUDE.md`: testkit-consumer sentence (was :97) extended with `keystone-hologram`, `keystone-shop` in the
   "may use it" list. `## Module dependency graph` ASCII block gained `keystone-hologram` (depends on
   common+bean) and `keystone-shop` (depends on common+item+persistence) as new children of `keystone-common`;
   `keystone-npc`'s tree connector changed from `└─` to `├─` (with matching `│` continuation-line prefixes) since
   it's no longer the last sibling. Nothing else in the file touched.
5. New test `HologramServiceTest.registerProtection_registersListenerUnderTheGivenPlugin` — uses
   `BukkitStatics.install()` (mocked `PluginManager`), asserts
   `verify(bukkit.pluginManager()).registerEvents(any(HologramProtectionListener.class), eq(owner))`.
   **Red-first**, demonstrated by temporarily gutting the already-shipped `HologramService.registerProtection`
   body (replaced the `registerEvents` call with a comment) and running
   `mvn -pl keystone-hologram test -Dtest=HologramServiceTest`:
   `Tests run: 7, Failures: 1` — `registerProtection_registersListenerUnderTheGivenPlugin` failed with
   `Wanted but not invoked: pluginManager.registerEvents(<any HologramProtectionListener>, Mock for JavaPlugin) —
   Actually, there were zero interactions with this mock.` Restored the real implementation, reran the same
   command: `Tests run: 7, Failures: 0, Errors: 0` — green, all 6 pre-existing tests plus the new one.
6. `BukkitServerFixture` — picked the javadoc sentence over a `StaticResets` hook (smaller and more honest: real
   `Bukkit.setServer` has no public "unset", so a reflective reset would fight the API's own once-settable
   contract rather than genuinely restore anything). Added a "No reset method on purpose" paragraph stating the
   install is intentionally fork-lifetime, matching `Bukkit.setServer`'s singleton contract.
7. `docs/phase-h10-hologram-shop.md` gained a closing `## Files` table (h9's shape — one row per file/file-group
   with its change), listing every file this phase touched including the fix-round-1 edits above.

### Build (fix round 1)

`mvn clean install` (full reactor, worktree root) → exit code 0.
Aggregated across every module's `target/surefire-reports/*.txt`: **Tests run: 1107, Failures: 0, Errors: 0,
Skipped: 0** (1106 → 1107: +1 for the new `registerProtection` test). Jar audit unchanged and still clean:
`org/luckyraven/keystone/hologram/` 4 entries, `org/luckyraven/keystone/shop/` 34 entries,
`grep -i "testkit|mockito"` 0 matches.
