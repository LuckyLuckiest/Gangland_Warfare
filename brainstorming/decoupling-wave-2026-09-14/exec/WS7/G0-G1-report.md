# WS7 G0+G1 report — 2026-09-16

Status: DONE_WITH_CONCERNS

## What changed

### G0 (Bartizan, worktree `E:\Programming\java\wt\bartizan-0.4.0`, branch `0.4.0`)

1. `bartizan-plugin/src/main/resources/items/wearables.yml` — deleted the `jetpack:` entry (Material/Name/
   Base_Damage_Reduction/Traits/Extra_Tags block) and the `FUEL_EFFICIENT` jetpack-specific comment line.
2. `bartizan-plugin/src/main/java/org/luckyraven/bartizan/wearable/WearableAddon.java` — deleted
   `legacyJetpackToExtraTags`, and its two private helpers that existed only to bridge to it (`sectionToMap`,
   `asConfigurationSection`, since nothing else called them — confirmed by grep before deleting); removed the
   `Jetpack:`-block fallback branch from `readExtraTags` (now: `Extra_Tags:` only, `null` otherwise); trimmed the
   class javadoc's `Jetpack:` mention.
3. `bartizan-plugin/src/test/java/org/luckyraven/bartizan/wearable/WearableAddonLegacyJetpackTest.java` — deleted
   (tested the deleted method).
4. `bartizan-plugin/src/test/java/org/luckyraven/bartizan/wearable/WearableAddonTest.java` — dropped its javadoc's
   now-stale reference to the deleted test class.
5. `pom.xml` — `<revision>0.3.0</revision>` → `0.4.0`.
6. `documentation/migration.md` — marked the old `Jetpack:`→`Extra_Tags:` section superseded/historical, added new
   `## 12. 0.4.0 — jetpack leaves Bartizan entirely` documenting the removal, the armour/trait loss, the opt-in
   `Bartizan_Traits:` + `WearableCatalog.register` fix path, and the shop/lootchest `wearable:jetpack` data note.
7. `documentation/bartizan-api.md` — added `register(String, Wearable)` to the `wearables()` accessor table row and
   a new "External wearable registration" subsection pointing at migration.md §12.
8. **D4 api hook, judged trivial per the design note below and implemented now (batch table allows this):**
   `bartizan-api/.../wearable/WearableCatalog.java` — added `void register(String key, Wearable wearable);` to the
   interface. `bartizan-plugin/.../wearable/WearableService.java` — added `@Override` to its existing
   `register(String, Wearable)` (`:65-67`, unchanged body — it already had this exact signature). Zero other
   implementers of `WearableCatalog` exist (confirmed by grep), so this is a source-compatible addition.

### G1 (Gangland, worktree `E:\Programming\java\wt\gangland-0.9.2`, branch `0.9.2`)

1. `gangland-api/.../file/configuration/Settings.java` — added `isBartizanAvailable()` (same shape as
   `isCitizensAvailable()`, backed by `Bukkit.getPluginManager().isPluginEnabled("Bartizan")`) + the `Bukkit` import.
2. `gangland-api/.../GanglandApi.java` — `VERSION` `"1.0"` → `"1.1"`.
3. `gangland-infra/gangland-item/.../item/ItemKind.java` — added `JETPACK("jetpack")` enum constant.
4. Root `pom.xml` — `<bartizan.version>0.1.0</bartizan.version>` → `0.4.0`.
5. `gangland-features/gangland-gadget/src/main/resources/module.yml` — `Host_Api: 1.0` → `1.1` (`Plugins:
   [Bartizan]` left untouched — that drops in G5, not G1).

### D4 design note

`brainstorming/decoupling-wave-2026-09-14/exec/WS7/D4-design.md` written (56 lines). Haiku census
(`graphify query "Wearable damage reduction"` first, cwd = main Bartizan checkout) plus my own direct read of
`WearableCatalog.java`/`WearableService.java`/`Wearable.java` confirmed: `WearableService.register(String,Wearable)`
already existed publicly but wasn't on the api interface — a 1-line interface addition, zero new plugin logic. I
judged this trivial and implemented it in G0 per the batch table's "incl. the D4 api hook if the design is trivial"
clause. The gadget-side bridge class (`JetpackBartizanTraitBridge`) is deferred to batch 2 as instructed — it needs
`Jetpack`/`JetpackAddon`, which don't exist until G2.

## Deviations from the plan

- The D4 api hook was implemented in G0 rather than left fully open — the batch table explicitly permits this when
  trivial; flagged for the orchestrator to confirm the triviality judgment.
- The plan's G1 step list does not include bumping the root `pom.xml` `<revision>` from `0.9.1` to `0.9.2` (only
  `bartizan.version` and gadget's `Host_Api` are listed). I did **not** bump it, staying literal to the gate table —
  see Concerns below.
- `WearableAddonTest.java`'s javadoc got a one-line edit (dropped the dead reference to the deleted test class) —
  not separately itemized in the plan's G0 file list but required for the javadoc to stay accurate; no test
  behavior changed.

## Red-first evidence

Not applicable to this batch — G0 is pure deletion + a trivial interface addition already satisfied by
`WearableService`'s existing method (no new/flipped test required by the plan's own G0/G1 test columns). G1 is
additive constants/config only. Both gates verified by full green suites instead (below); no pre-fix "red" state
exists to demonstrate since no wrong behavior was being fixed.

## Build

Bartizan: `mvn clean install` in `E:\Programming\java\wt\bartizan-0.4.0` → `BUILD SUCCESS`, `Tests run: 304,
Failures: 0, Errors: 0, Skipped: 0`. `bartizan-api-0.4.0`/`bartizan-plugin-0.4.0` installed to `~/.m2`.

Gangland: `mvn clean install` in `E:\Programming\java\wt\gangland-0.9.2` → `BUILD SUCCESS` (all 22 reactor modules
SUCCESS, including `gangland-gadget` resolving `bartizan-api:0.4.0` at `provided` scope). Re-ran `mvn test` and
summed every module's `Tests run:` line: **814 run, 0 failures, 0 errors, 0 skipped**, `BUILD SUCCESS`.

## Docket ids touched

None — G0/G1 are config/constant-only gates; the docket ids this wave touches (GD-12 etc., §11 of the plan) land
in G2+.

**Docket candidates**: none new noticed.

## Subagents used

- Haiku, `general-purpose`, one agent: Bartizan wearable trait-resolution census for the D4 design note
  (`graphify query "Wearable damage reduction"` first, cwd = main Bartizan checkout, read-only). Its findings were
  cross-checked against direct reads of `WearableCatalog.java`/`WearableService.java`/`Wearable.java` before being
  used in the design note — all six of its claims confirmed accurate.

## Concerns / open questions

1. **Project version not bumped.** Root `pom.xml` `<revision>` is still `0.9.1` on the `0.9.2` branch — the plan's
   G1 step list never names this as a step, so I left it alone rather than invent scope. If the wave intends the
   Maven version itself to become `0.9.2` before G6's tag, say so and I'll fold it into a later gate's report.
2. **D4 hook implemented early.** I judged the `WearableCatalog.register` addition trivial (interface-only, zero
   new plugin logic) and shipped it in G0 rather than waiting for batch-2 sign-off, per the batch table's own
   carve-out. Please confirm this reading of "if the design is trivial" before batch 2 builds the gadget-side
   bridge on top of it.
3. The pre-existing `CLAUDE.md` modification in the Bartizan worktree (line-ending only, per `git diff` showing no
   content change beyond a CRLF warning) predates this session and was not touched by me.

## Fix round 1 — 2026-09-16

Review `exec/WS7/G0-G1-review.md` (Opus, FIX — 2 Critical, 4 Important, 3 Minor, all against the D4 hook; G0/G1 as
shipped code otherwise clean) + orchestrator ruling W10 (pom revision) and W11 (D4 settled shape). Both addressed.

### Ruling W10 — root `pom.xml` `<revision>` bump

`E:\Programming\java\wt\gangland-0.9.2\pom.xml`: `<revision>0.9.1</revision>` → `0.9.2`. Edited alone, confirmed via
`git diff` before the fix round's own builds ran. Reactor now builds `gangland-api-0.9.2`/`gangland_warfare-0.9.2.jar`
etc.

### W11 — D4 settled shape (all in the Bartizan worktree, `E:\Programming\java\wt\bartizan-0.4.0`)

**What changed:**

1. `bartizan-api/.../wearable/Wearable.java` — new `boolean external` field (`@Builder.Default false`).
   `getPermission()` returns `null` when `external` (Critical 2 — an unregistered permission node must never
   silently block equip; `WearableEquipListener` needed no change since it already treats a `null` permission as
   "no gate").
2. `bartizan-plugin/.../item/WearableRefresher.java` — `canRefresh` resolves the tagged key and returns `false`
   for an external entry (Critical 1).
3. `bartizan-plugin/.../item/WearableConverter.java` — `convert` returns `null` for an external entry (Critical 1).
4. `bartizan-plugin/.../item/WearableItemSerializer.java` — gained a `WearableService` constructor param and a new
   `claims(ItemStack)` method excluding external entries (Important 3). `bartizan-plugin/.../config/ItemConfig.java`
   — `wearableItemSerializer()` bean now takes `WearableService`. `bartizan-plugin/.../item/BartizanItemVocabulary.java`
   — the priority-0 registration now uses `BartizanItemPredicates.WEARABLE.and(wearableItemSerializer::claims)`
   instead of the bare static predicate.
5. `bartizan-plugin/.../command/wearable/WearableGiveCommand.java` — tab-completion filters out external entries;
   `giveWearable` returns `false` for one. `WearableInfoCommand.java` — treats an external entry as "not
   registered" (same message path, avoids the `wearable.getMaterial().name()` NPE). `WearableListCommand.java` —
   pre-filters external entries out of the listed/joined set (also fixes M9's dangling-comma/`null`-name risk as a
   side effect of the rewrite, not separately chased).
6. `bartizan-api/.../wearable/WearableCatalog.java` + `documentation/bartizan-api.md` — `register`'s javadoc and
   the "External wearable registration" doc section reworded to the real contract (Important 4): external =
   damage-reduction/effects only, Bartizan never builds/converts/gives/lists/serialises it.
7. M7: `bartizan-plugin/.../wearable/WearableAddon.java` — dropped the unused `ConfigurationSection`/
   `MemoryConfiguration` imports and the stale `{@link #sectionToMap}` javadoc reference. M8 parked per the ruling
   (migration.md keeps `## 12`, not `§3.5`).
8. New test `bartizan-plugin/src/test/.../wearable/WearableExternalRegistrationTest.java` (6 cases): register →
   `resolveWearable` finds it → `applyWearableReduction` applies the reduction; the refresher/converter/serializer
   each skip an external entry (and still handle a normal registered one); `getPermission()` is null for external.
   Give/list/info are not independently driven end-to-end — this codebase has zero existing unit tests over the
   `command/` package (verified: no `SubArgument`/`Tree<Argument>` test scaffolding exists anywhere in Bartizan to
   build on), and all three read the exact same `Wearable#isExternal()` flag this test pins directly. Flagged below
   rather than silently skipped.
9. `brainstorming/decoupling-wave-2026-09-14/exec/WS7/D4-design.md` rewritten to the settled shape (the contract
   above, the gadget-side "stamp only when the bridge registered" + priority-above-10 requirement for batch 2, and
   I5's `softdepend`/module-load-order answer — no `PluginEnableEvent` hook).

### Red-first evidence

`WearableExternalRegistrationTest` written against the already-fixed production code (the fix round's instructions
arrived with the shape already specified, so guards were implemented first). Redness was then demonstrated without
git (per LEAD-RULES, no stash/reset) by temporarily reverting each guard's *logic only* back to its pre-fix
behaviour via direct edits (kept every signature/constructor intact so the test still compiled), running the suite,
then restoring the real guard and re-running:

- Red command: `mvn -pl bartizan-plugin -am test -Dtest=WearableExternalRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false`
  with the four guards temporarily disabled → `Tests run: 6, Failures: 3, Errors: 1`. Failing/erroring exactly the
  four guarded cases, the other two (unaffected by any of the four reverts) still green:
  - `converter_skipsExternal` → **ERROR** `IllegalArgumentException: Material cannot be null` at
    `Wearable.buildItem(Wearable.java:355)` — the literal Critical-1 crash, reproduced live.
  - `refresher_skipsExternal` → **FAILURE** `expected: <false> but was: <true>`.
  - `getPermission_nullForExternal` → **FAILURE** `expected: <null> but was: <bartizan.wearables.test_external>`.
  - `serializer_doesNotClaimExternal` → **FAILURE** `expected: <false> but was: <true>`.
- Green command: same command after restoring the four guards → `Tests run: 6, Failures: 0, Errors: 0`.

### Build

Bartizan: `mvn clean install` in `E:\Programming\java\wt\bartizan-0.4.0` → `BUILD SUCCESS`,
`Tests run: 310, Failures: 0, Errors: 0, Skipped: 0` (304 prior + 6 new).

Gangland: `mvn clean install -DskipTests` in `E:\Programming\java\wt\gangland-0.9.2` → `BUILD SUCCESS`, all 22
reactor modules SUCCESS, reactor now reports "Gangland Warfare 0.9.2" and produces `gangland_warfare-0.9.2.jar` /
`gangland-gadget-0.9.2.jar` etc., `gangland-gadget` still resolving `bartizan-api:0.4.0`. Tests were not re-run here
per the fix-round instruction (`-DskipTests`, compile-check only).

### Docket ids touched

None. **Docket candidates** (unchanged from the review's own notes, to be filed at G6 per the ruling): (a)
`WearableRefresher.canRefresh` claims any `wearable`-tagged stack whose key resolves to nothing (a foreign/stale
tag) and only no-ops at `refresh()` — pre-existing, not introduced by this fix; (b) an externally-registered
wearable getting no permission node is the intended fix here, adjacent to the still-open GD-07 permission gap.

### Subagents used

None this round — all fix-round work done directly.

### Concerns / open questions

1. **Give/info/list guards not independently unit-tested end-to-end** (see fix item 8 above) — flagged for the
   reviewer to decide whether that gap is acceptable for this gate or whether standing up minimal `command/`
   package test scaffolding is now in scope.
2. `bartizan-plugin/.../wearable/WearableAddonLegacyJetpackTest.java`'s deletion shows as staged (`git diff --cached
   --stat`) in the Bartizan worktree, not just working-tree-deleted — I did not run any `git add`/stage command;
   noting it factually since LEAD-RULES reserves staging/committing for the orchestrator.
3. Same D4-hook-implemented-early judgment call as fix-round-0 concern #2 — now resolved by this round's contract
   fix, but confirms the original "trivial" read undersold the surrounding-surface audit the reviewer performed.
