# Oriel G0 report — 2026-09-16

Status: DONE

Worktree: `E:\Programming\java\wt\oriel-0.8.0`, branch `0.8.0` off `fea7e01` (verified clean at session start,
still the only branch touched — no commit made, per LEAD-RULES).

## What changed

1. **Keystone bump 1.7.0 → 1.9.2.** `gradle/libs.versions.toml:10` (`keystone = "1.9.2"`). Verified all 6
   Keystone artifacts Oriel actually depends on (`keystone-common/-bean/-command/-persistence/-hooks/-testkit`)
   exist at `1.9.2` in `~/.m2/repository/org/luckyraven/`.
2. **Version bump 0.7.0 → 0.8.0.** `gradle.properties:2` (`version=0.8.0-SNAPSHOT` — see Deviations #2 on the
   SNAPSHOT-vs-release call).
3. **`MenuConfigService.forConsumer(plugin, fileManager, placeholders)` factory** —
   `menu-data/config/src/main/java/org/luckyraven/oriel/config/MenuConfigService.java` (new imports +
   `forConsumer`/`requireService` methods, inserted after the 17-arg constructor). Resolves the 9
   `ServicesManager`-published registries (`MenuRegistry`, `ActionRegistry`, `SourceProviderRegistry`,
   `FilterRegistry`, `SortRegistry`, `SearchMatcherRegistry`, `ItemProviderRegistry`, `MaterialSourceRegistry`,
   `ComponentRegistry`), fails fast with a named `IllegalStateException` if Oriel hasn't published yet, and builds
   the 4 unpublished args itself (fresh `PaginatedControlRegistry`, fresh `InMemoryRequirementRegistry`,
   `MenuCommandRegistrar` built against the **caller's** plugin, and a fresh `InMemoryCooldownService`). Javadoc
   states both required facts: `MenuCommandRegistrar`'s `open:` command registrations belong to the calling
   plugin (not Oriel), and `CooldownService` is Keystone's own contract type (not Oriel's) — Oriel doesn't publish
   one, every Keystone plugin owns its own bean per Keystone's own `getting-started.md` convention; a caller with
   its own persistent `CooldownService` should skip this factory and call the 17-arg constructor directly.
4. **Spike (a) — "a registered filter filters":** confirmed via new coverage. Added
   `menu-data/config/src/test/resources/menus/chest_paginated_filter.yml` (3-item paginated region, 1 enchanted)
   and `PaginatedControlsBugTest.cycleFilterActionNarrowsAPaginatedRegionToARegisteredFilter()` — cycling onto
   `StandardFilters`' `enchanted` filter narrows 3 visible items to 1. This closes a real gap: the existing suite
   only proved `cycle_filter`'s index mechanics and search narrowing, never that a *registered filter* actually
   filters end to end.
5. **Spike (b) — anvil-driven paginated search:** confirmed already shipped, **not** deferred as
   `MIGRATION-FROM-GLW.md:389-394` claimed. Read `config/loader/PaginatedControlActions.java`'s `set_search`
   action in full: it opens a real `AnvilMenu` prompt, sets the viewer's search query on confirm, and reopens the
   menu; `paginated.search_button:` is sugar over the same path. It's demoed in the plugin's own bundled
   `menu-plugin/src/main/resources/menus/paginated_search_demo.yml` (header comment: "v0.3 demo" — shipped after
   the "deferred to v0.2+" doc line was written, which is presumably why the doc never caught up). The narrowing
   behaviour itself is proven by the pre-existing, passing
   `PaginatedControlsBugTest.searchThenClearShowsAllItemsAgain`. The one genuinely untestable piece — the
   click→real-AnvilGUI NMS hop — matches Oriel's own documented test boundary
   (`AnvilMenuBuilderTest`'s class javadoc: "can't be exercised under MockBukkit... covered by the manual smoke
   test on a real Paper server"), which is pre-existing convention, not a new gap WS2 introduced.
6. **Doc fix**, `docs/MIGRATION-FROM-GLW.md:389-394`: removed "Paginated filters and sorting" and "Anvil-driven
   search on paginated regions" from the "deferred to v0.2+" list (both false) and added a dated correction note
   citing the code + tests above. Left "Declarative NBT on items" and "Custom action registration" alone — out of
   G0's scope, not independently verified this pass.
7. **`./gradlew publishToMavenLocal`** — green; Oriel `0.8.0-SNAPSHOT` publishes and is consumable from
   `mavenLocal()` against Keystone 1.9.2 (the plan's G0 step 3 smoke).

## Deviations from the plan

1. **Zero production API breaks from the Keystone bump.** The plan flagged `PacketAdapter.FakeWindowType` and
   `Severity.ERROR` as the two known edge cases to check; both compiled clean with no changes needed anywhere in
   the reactor. Only the pre-existing H5 deprecation warnings on `CommandManager.getCommands()` /
   `CommandTabCompleter` / `BrigadierTabRegistrar` remain (`menu-plugin/.../OrielContext.java:102,144,148`,
   `DebugCommand.java:51`) — already flagged by the plan as a pre-2.0.0 migration item, not a G0 blocker, and
   untouched here.
2. **Version string: `0.8.0-SNAPSHOT`, not a release.** The plan's §13 left this as an open question (C8). Oriel
   has never cut a real release (`gradle.properties` was `0.7.0-SNAPSHOT` before this change, and the whole wave
   consumes it via `publishToMavenLocal`), so I kept the SNAPSHOT pattern rather than inventing a release process
   mid-gate. Flag for the orchestrator if a real release is wanted before Gangland's G1 pulls it.
3. **`allowElevatedActions` in `forConsumer`.** The plan's own accounting says only 4 of the 17 constructor args
   are unpublished (`PaginatedControlRegistry`, `RequirementRegistry`, `CooldownService`, `MenuCommandRegistrar`)
   — it doesn't mention this 17th arg, but it has no `ServicesManager` source either. Defaulted to `() -> false`
   (elevated `op:`/`permission:` actions disabled), matching `ActionParser.setElevatedActionsGate`'s own
   null-gate default. If Gangland's own menus need `op:`/`permission:` actions, its `OrielMenuConfig` bean must
   call the 17-arg constructor directly instead of this factory — noted in the javadoc's elevated-actions
   paragraph.
4. **Spike (b)'s conclusion is stronger than the plan expected.** PLAN.md §5's ask table still lists "Anvil-driven
   paginated search" as an Oriel ask (size S-M, "no — manual anvil button fallback" if declined) and the WS2
   plan's §3/§9 D3 treated it as "genuinely still deferred." Both are now stale — the feature ships today. This
   likely **removes that ask entirely** from Oriel's G0-G2 backlog; `phone_gang_search.yml`/`user_stat.yml` (G3)
   can use `set_search`/`search_button:` directly with no Oriel-side work. Flagging for the orchestrator to update
   PLAN.md/WS2 plan rather than silently absorbing the correction.
5. **No subagents spawned.** The actual G0 workload turned out to be a version bump with zero compile fallout, one
   ~70-line factory method, and verifying two already-implemented mechanisms — small enough to execute directly
   within budget. Noting this as a deviation from the expectation that G0 would need delegated work.

## Red-first evidence

Not applicable in the bug-fix sense — G0 adds new capability (a factory method) and closes test-coverage gaps
around **already-shipped, correctly-behaving** production code; nothing here flips a wrong-behaviour assertion.
Each new/changed test was run and confirmed green immediately after being written:

- `MenuConfigServiceForConsumerTest` (2 tests, new file) —
  `./gradlew :menu-data:config:test --tests "org.luckyraven.oriel.config.MenuConfigServiceForConsumerTest"` →
  `BUILD SUCCESSFUL`, XML: `tests="2" failures="0" errors="0"`.
- `PaginatedControlsBugTest` (+1 test method) —
  `./gradlew :menu-data:config:test --tests "org.luckyraven.oriel.config.PaginatedControlsBugTest"` →
  `BUILD SUCCESSFUL`, XML: `tests="3" failures="0" errors="0"` (all 3, including the 2 pre-existing).

## Build

Final command: `./gradlew clean build` (full reactor, Keystone 1.9.2) → **BUILD SUCCESSFUL in 15s**, 92 actionable
tasks (63 executed, 29 from cache). Aggregated JUnit XML across all 18 modules:
**84 test files, 639 tests, 0 failures, 0 errors, 9 skipped** (pre-existing skips, unrelated to this gate — not
investigated, out of scope). Followed by `./gradlew publishToMavenLocal` → **BUILD SUCCESSFUL**.

## Docket ids touched

None (no Gangland `GR-`/`CL-`/etc. ids apply to the Oriel repo).

**Cross-docket Oriel findings** (`brainstorming/cross-docket-2026-09-10/oriel/findings/`, 9 files) — read in full
per the brief. `conventions.txt` = 0 findings (clean sweep). The other 8 files hold **37 findings**; none required
a fix at G0 (scope was Keystone/version bump + `forConsumer` + the 2 named spikes only — no
`ChestMenu`/`MenuFlow`/`TraderMenu`/vault/persistence/editor behavior change). Full list, `id [priority] title |
test-pinned? | location`:

**menu-chest.txt** (6): 1 `[P0]` ChestMenu.close skips returnHeldItems on CloseReason.USER, destroying
DepositSlotComponent items on Escape | YES | `ChestMenu.java:380-386` — **directly the failure mode G2's
"Crash-safe DepositSlotComponent persistence (Oriel A3)" ask exists to fix, worth reading before G2 starts**.
2 `[P0]` MenuFlow.switchInternal/end() abandon DepositSlotComponent items on every panel switch | no |
`MenuFlow.java:165-185` — **same G2 relevance**. 3 `[P1]` ChestMenuCloseListener fires the outgoing menu's
on_close handler during internal navigation | no | `ChestMenuCloseListener.java:56-70`. 4 `[P2]`
PaginatedListPageState.itemAt never clamps a stale page after the list shrinks | no |
`PaginatedListPageState.java:56-61`. 5 `[P2]` PaginatedControlState's 4 per-viewer maps never evict a departed
player | no | `PaginatedControlState.java:25-28`. 6 `[P2]` ChestMenu.rerenderSlot omits the wasTouched blanking
renderAll performs | no | `ChestMenu.java:452-463`.

**menu-config.txt** (9): 1 `[P1]` ImportService.writeTree truncates an existing imported YAML with no existence
check | no | `ImportService.java:158-174`. 2 `[P2]` 4 bundled demo menus missing from addExpectedFile, never reach
a fresh install | no | `MenuConfigService.java:113-137`. 3 `[P2]` AnvilClickContext substitutes raw player-typed
%input% into elevated op/permission command strings | no | `AnvilClickContext.java:36-50`. 4 `[P2]` give_slot
validates only a lower bound | no | `DepositActions.java:70-75`. 5 `[P1]` MenuConfigService.registerMenu never
calls the factory, validation deferred to first open | no | `MenuConfigService.java:265-275` — **in the file I
edited; did not touch this code path, only added a static factory method above it**. 6 `[P1]` Paginated nav/search
button slots skip the upper-bound check | no | `ChestMenuLoader.java:196-207`. 7 `[P1]`
ItemDefinitionWriter.isNamespacedReference misses dash-prefixed refs | YES | `ItemDefinitionWriter.java:139-145`.
8 `[P2]` RequirementParser.parseItem uses raw Material.matchMaterial | YES | `RequirementParser.java:344-356`.
9 `[P2]` NbtApplier silently narrows an int_array value already reported as ERROR | no | `NbtApplier.java:82-91`.

**menu-core.txt** (3): 1 `[P1]` RequirementHook.evaluateMinimum double-spends consumable gates | YES |
`RequirementHook.java:195-215`. 2 `[P2]` InMemoryMenuRegistry.openPrevious aborts on a required
argument_processor | no | `InMemoryMenuRegistry.java:78-86`. 3 `[P2]` NavigationHistory.back consumes the trail
step even when the popped-to menu is unregistered | YES | `NavigationHistory.java:53-60`.

**menu-editor.txt** (3): 1 `[P1]` MenuEditor.open calls exit() instead of requestExit(), discarding unsaved
session | no | `MenuEditor.java:117-121`. 2 `[P2]` SlotOpScreen.apply deletes an occupied destination slot | no |
`SlotOpScreen.java:65-82`. 3 `[P2]` GridScreen.duplicate/SlotOpScreen bypass targetFor's schema, silently no-op |
no | `GridScreen.java:241-257`.

**menu-integration.txt** (3): 1 `[P0]` give_permission/take_permission bypass allow_elevated_actions | no |
`PermissionActions.java:34-49`. 2 `[P0]` EconomyActions discards the Vault transaction result on a failed
take_money | no | `EconomyActions.java:39-46`. 3 `[P2]` give_money/take_money accept non-finite amounts | no |
`EconomyActions.java:35-46`.

**menu-inventory.txt** (6): 1 `[P0]` TraderMenu.open rebuilds MerchantRecipes each open, resetting maxUses | YES |
`TraderMenu.java:68-78`. 2 `[P1]` TraderMenu.open registers before openMerchant, whose implicit close deletes the
entry | YES | `TraderMenu.java:83-86`. 3 `[P2]` Workbench/Dispenser/Hopper/TraderMenu track after opening,
contradicting FurnaceMenu's order | no | `WorkbenchMenu.java:177-180`. 4 `[P2]` FurnaceProgress.max has no upper
bound, overflows the short-typed window property | YES | `FurnaceProgress.java:19-27`. 5 `[P2]` AnvilMenu
dereferences AnvilGUI with no presence check | no | `AnvilMenu.java:99-106`. 6 `[P3]` TraderMenu.returnInputItems
under-counts returned items | no | `TraderMenu.java:167-171`.

**menu-persistence.txt** (3): 1 `[P0]` HistoryService.quote hardcodes double quotes, player_uuid migration
no-ops on MySQL | YES | `HistoryService.java:131-168`. 2 `[P1]` HistoryService.logEvent reports audit-write
failures nowhere | YES | `HistoryService.java:85-93`. 3 `[P2]` DepositActions.encode returns empty string on
serialization failure | no | `DepositActions.java:89-119`.

**menu-plugin.txt** (4): 1 `[P1]` Failed DB reconnect on /menu reload aborts reloadLifecycleBeans, leaves
AnimationTicker cancelled | no | `PersistenceConfig.java:110-129`. 2 `[P2]` UpdateChecker hardcoded resourceId=0 |
no | `KernelConfig.java:193-196`. 3 `[P2]` ImportService wired to context::reloadBeans, closes every open menu |
no | `ConfigLoaderConfig.java:66-81`. 4 `[P3]` DevPaginatedMenuConfig javadoc claims a Phase pin its annotation
doesn't apply | no | `DevPaginatedMenuConfig.java:23-31`.

No new (un-filed) bugs noticed — my reads this gate were scoped to `MenuConfigService.java`, `OrielPlugin.java`,
`PaginatedControlActions.java`, `ActionParser.java`, `RequirementRegistry.java`, `PaginatedControlRegistry.java`,
`MenuCommandRegistrar.java`, and the demo/fixture YAMLs, all already covered by the finding files above.

## Subagents used

None — see Deviations #5.

## Concerns / open questions

- **Deviation #4 (anvil search ask is moot)** should be reflected in PLAN.md §5 / WS2 plan §9 D3 /§10 before G3
  plans `phone_gang_search.yml`/`user_stat.yml` — recommend the orchestrator drop that Oriel ask rather than carry
  it forward as still-open.
- **`allowElevatedActions` default** (`() -> false`) in `forConsumer` is a judgment call, not dictated by the
  brief's 3-arg signature — flagging for a second look before Gangland's `OrielMenuConfig` bean (G1) relies on it.
- **C8 (SNAPSHOT vs release)** still genuinely open at the product level, just resolved pragmatically for this
  gate (Deviation #2).
- The 37 cross-docket findings are unfiled action items for later gates/other streams, most notably menu-chest #1
  and #2 for G2. I have not filed them anywhere new (per the brief, "fix only what an ask touches, list the rest
  in the report") — they already exist in the cross-docket source files; whether they also have `OR-nn` ids in the
  live LuckyRaven Bug Docket artifact was not checked (out of scope for this Oriel-only worktree).

## Fix round 1 — 2026-09-16

Review: `exec/ORIEL/G0-review.md`, verdict FIX (1 Critical, 2 Important) + Orchestrator ruling (W15). Root cause:
`MenuConfigService.forConsumer` built its own private `PaginatedControlRegistry`/`InMemoryRequirementRegistry`/
`InMemoryCooldownService` instead of reusing Oriel's bean-owned singletons, so a consumer's paginated-menu clicks
(`cycle_sort`/`cycle_filter`/`set_search`/`clear_search`/`toggle_direction`) would silently no-op, and the
consumer's `RequirementParser` static state would stomp Oriel's own on the next `/menu reload`.

### Changes

1. **(Critical, C1)** `menu-plugin/.../OrielPlugin.java` `registerServices()` — publish Oriel's KERNEL-phase
   `PaginatedControlRegistry` bean (the exact instance `PaginatedControlActions` is bound to,
   `KernelConfig.java:182-190`) on the `ServicesManager`, beside the existing 9 registrations.
2. **(Important, I2)** Same method — also publish Oriel's `RequirementRegistry` and `CooldownService` beans.
3. **(Critical+Important)** `menu-data/config/.../MenuConfigService.java` `forConsumer(...)` — `requireService` all
   three instead of constructing fresh ones; removed the now-unused `InMemoryRequirementRegistry`/
   `InMemoryCooldownService` imports. Javadoc rewritten: `forConsumer` reuses 12 of Oriel's own published
   singletons (not 9); the consumer plugin owns only its own menu folder, placeholders, and the
   `MenuCommandRegistrar` (the one argument that's genuinely per-consumer).
4. **(Important, I2)** `menu-data/config/.../RequirementParser.java` — added `ponytail:` comments on
   `setRequirementRegistry`/`setCooldownService` naming the ceiling: process-wide static state, not
   per-`MenuConfigService`; every live instance must share the identical registry/service reference or they
   silently steal each other's parser wiring; upgrade path (instance-scoped, not static) is out of scope here.
   Parser logic itself untouched, per the ruling ("do not refactor the parser").
5. New tests: `MenuConfigServiceForConsumerTest.cycleFilterThroughTheSharedActionRegistryNarrowsAConsumerMenu()`
   (new — publishes the real, shared `ActionRegistry`+`PaginatedControlRegistry` pair bound together via
   `PaginatedControlActions`, exactly like `OrielPlugin`/`KernelConfig` do in production; loads a consumer menu
   with `filter_options: [none, enchanted]` through `forConsumer`; looks up state through the *same*
   `PaginatedControlRegistry` reference published on the `ServicesManager`; cycles the filter and re-opens via
   `MenuRegistry.open(...)`, asserting the region narrows 3→1). `publishOrielRegistries()` updated to also publish
   `PaginatedControlRegistry`/`RequirementRegistry`/`CooldownService` (mechanically required now that `forConsumer`
   calls `requireService` for them). New fixture-equivalent YAML embedded as `FILTERABLE` (same shape as
   `chest_paginated_filter.yml`, ported to a consumer's own menu folder for this test).

### Red-first evidence

- **New C1 test**, run against the pre-fix `forConsumer` body (temporarily reverted in place, then restored):
  `./gradlew :menu-data:config:test --tests "org.luckyraven.oriel.config.MenuConfigServiceForConsumerTest" --tests "org.luckyraven.oriel.config.PaginatedControlsBugTest"`
  → **RED**: `MenuConfigServiceForConsumerTest.cycleFilterThroughTheSharedActionRegistryNarrowsAConsumerMenu()
  FAILED` — `org.opentest4j.AssertionFailedError: the consumer's paginated state must land in the shared
  PaginatedControlRegistry, not a private copy forConsumer built for itself ==> expected: not <null>` (line 183)
  — exactly finding C1's failure mode. The other 5 tests in those two classes stayed green (reverting only
  `forConsumer`'s registry wiring doesn't affect basic menu-load/command-registration or the raw-constructor
  spike-(a) test, which never calls `forConsumer`). Fix restored; re-run → green (see Build below).
- **Spike-(a) test retroactive red-check** (Important 3 — no red-first evidence for the original G0 tests):
  `PaginatedControlsBugTest.cycleFilterActionNarrowsAPaginatedRegionToARegisteredFilter()`'s assertion target was
  temporarily flipped (`assertEquals(1, ...)` → `assertEquals(999, ...)`), then reverted:
  `./gradlew :menu-data:config:test --tests "org.luckyraven.oriel.config.PaginatedControlsBugTest"` → **RED**:
  `FAILED` at `PaginatedControlsBugTest.java:131`, `expected: 999 but was: 1` — proves the test genuinely narrows
  to a real, checked value and isn't vacuously true. Assertion restored to `1`; re-run → green.
- Both temporary reverts were made with `Edit`, run, then reverted with `Edit` back to the fixed/correct source —
  no commit exists of the broken intermediate state.

### Build

Full reactor: `./gradlew clean build` → **BUILD SUCCESSFUL in 7s**, 92 actionable tasks (58 executed, 34 from
cache). Aggregated JUnit XML: **84 test files, 640 tests, 0 failures, 0 errors, 9 skipped** (same 9 pre-existing
skips as G0's original run; +1 test total for the new C1 coverage). `./gradlew publishToMavenLocal` →
**BUILD SUCCESSFUL**.

### Files touched (fix round 1, on top of G0's original set)

- `menu-plugin/src/main/java/org/luckyraven/oriel/plugin/OrielPlugin.java`
- `menu-data/config/src/main/java/org/luckyraven/oriel/config/MenuConfigService.java`
- `menu-data/config/src/main/java/org/luckyraven/oriel/config/loader/RequirementParser.java`
- `menu-data/config/src/test/java/org/luckyraven/oriel/config/MenuConfigServiceForConsumerTest.java`
- `menu-data/config/src/test/java/org/luckyraven/oriel/config/PaginatedControlsBugTest.java` (no net change —
  temporary assertion revert only, restored)

### Concerns

- None new. The G0 concerns (anvil-search ask now moot, `allowElevatedActions` default, C8 SNAPSHOT-vs-release)
  still stand as written above.
