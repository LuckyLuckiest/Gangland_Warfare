# Review — Oriel 0.8.0 G0 — 2026-09-16 (Sonnet, transcribed)
Verdict: FIX (1 Critical, 2 Important)

## Spec table (condensed)
Keystone 1.7.0→1.9.2 ✅ (libs.versions.toml:10, six 1.9.2 artifacts in ~/.m2, zero prod API breaks) · version 0.8.0-SNAPSHOT ✅ · `forConsumer` ⚠ (signature/javadoc satisfy N2, loads the consumer's own `menus/` via `FolderLoader`; two of four self-built args wired wrongly → C1/I1) · spike (a) filter narrows ✅ · spike (b) anvil search already shipped ✅ (`PaginatedControlActions.java:67-75,108-130`, `paginated_search_demo.yml`) → PLAN §5 ask dropped · publishToMavenLocal + cross-docket read ✅

## Findings
**Critical 1** — `MenuConfigService.forConsumer` (`MenuConfigService.java:203`) constructs a private `PaginatedControlRegistry`, but `PaginatedControlActions` is registered once into the shared `ActionRegistry` (`KernelConfig.java:182-190`) bound to Oriel's own registry bean; its five handlers (`:39-96`) resolve state through that instance only, while a consumer's `ChestMenuLoader.java:431` registers into the `forConsumer` copy → `state == null`, every `cycle_sort`/`cycle_filter`/`set_search`/`clear_search`/`toggle_direction` click on a consumer menu no-ops. `PaginatedControlsBugTest` passes only because it pairs one registry into both. `MenuConfigServiceForConsumerTest`'s fixture has no `sort_options`/`filter_options`/`searchable` block, so it cannot catch it.
**Important 2** — the constructor sets `RequirementParser`'s `private static volatile` registry/cooldown service (`RequirementParser.java:78,85`, read at parse time `:526,552`); `forConsumer` installs fresh empty `InMemoryRequirementRegistry`/`InMemoryCooldownService` (`:206`), stomping Oriel's own on the next `/menu reload`.
**Important 3** — no red-first evidence for the two new tests.

## Notes
- C1 and I2 share a root cause: `MenuConfigService` was never multi-instance-safe → one Oriel ask.
- `allowElevatedActions = () -> false` default is fine.
- Drop "Anvil-driven paginated search" from PLAN §5 / WS2 plan (spike b).

## Orchestrator ruling (W15)
Fix in Oriel G0 by **reusing Oriel's singletons, not by refactoring the parser**: publish Oriel's bean-owned `PaginatedControlRegistry`, `RequirementRegistry` and `CooldownService` on the `ServicesManager` in `KernelConfig` (beside `ActionRegistry`) and make `forConsumer` `requireService` them, so a consumer's loader registers into the same registry `PaginatedControlActions` reads and the static parser state is re-set to the very same instances (idempotent). A `ponytail:` comment on the static setter names the ceiling (per-instance parser state if a second Oriel-side tenant ever needs isolation). New test: a consumer menu with a `filter_options` block loaded through `forConsumer`, click `cycle_filter` through the shared `ActionRegistry`, assert the region narrows — red against the current code, green after. Red runs recorded for the two existing new tests as well (revert the factory body → they fail).
