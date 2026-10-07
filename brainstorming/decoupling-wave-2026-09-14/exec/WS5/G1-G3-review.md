# Review — Gangland 0.10.0 WS5 combined gate (G1 + G2 + G3 relocation slice, ruling W51) — 2026-09-23 (Opus, transcribed)
Verdict: FIX (1 Critical, 4 Important, 3 Minor). Note: the main-checkout `exec/WS5/G1-G3-*` files were the stale fork hand-back; the real report/diff sat at the worktree root (F3). Reviewed the worktree itself.

## Spec table (condensed, by plan step)
5/6/7 skeleton, type move, persistence (`gang.database.*`, ForeignUserTable/ForeignPermissionTable) ✅ · 7b `RankManager.seedInitialRanks` idempotent before `loadAll()` ✅ · 8/9/9b/9c ✅ · 10 UserDataLoader → GangMembership ✅ · 11b RankPermissionApplier keeps the W48 unset semantics (:65-77), real red ✅ · 12 PlaceholderContribution + lazy resolve ✅ (F7) · 13 WaypointGangIdCommand name → raw id ⚠ (F4) · 14 contributions under "option"/"debug" ✅ · 15-16, 16b-d, turf+mail retarget, 20, 20b ✅ · S3 filter adapters / G3 18b GangItemSourceProvider ❌ deleted + stubbed (F1).

## Findings
**F1 Critical** — `GameplayConfig.java:152` stubs the ItemSourceProvider with an empty list: alliance_stat.yml (`gang_allies`), user_stat.yml (`gang_members`), phone_gang_search.yml (`gangs`), gang_info/gang_stat/phone_gang open with zero rows, silently; `GangFilterRegistration` deleted → `/glw filter gangs …` buttons no-op at `FilterCommand.java:130/150/166`. W51 excluded exactly this. Fix: `GangItemSourceContribution` seam (api) + module impl + GameplayConfig resolving it, filter bindings the same way, adapters + their tests back inside the module.
**F2 Important** — 15 tests unaccounted: 878 + 14 new = 892 expected, 877 reported; `GangFilterAdapterTest` + `MemberFilterAdapterTest` deleted (the pins for F1).
**F3 Important** — deliverables written to an untracked `exec\WS5\` at the worktree root.
**F4 Important** — `/glw waypoint gangid` echoes the id instead of the name, shipped as an ask rather than a decision.
**M5** collapsed brace `RankPermissionApplier.java:28` · **M6** gangland-domain now has zero sources but stays a dependency of impl (pom:91) and npc-shops (pom:73) · **M7** `GanglandPlaceholder` caches contributions forever — safe because modules load once per start; document it.

## Data integrity on join — sound
LISTENER scan runs inside its phase; `@PostConstruct`/`initialize()`/`BeanPostInitialize` run after every phase, so MemberJoinListener is registered and the member cache preloaded before PlayerBootstrapService fires the first UserDataInitEvent (MemberCachePopulationOrderTest now pins the phase boundary). No duplicate rows (cache miss → one Member; rejoin → attach only; reload short-circuits on existing user). Async event hops to main and re-checks isOnline. Unset-vs-deny preserved (defensive copy of grantedPermissionNames). Seeding idempotent. Module absent → inert (-1/false).

## Boundary — clean
impl has no gangland-gang edge; module on gangland-api provided (core test-only); turf/mail on gangland-gang provided + `Depends: [gang]`; civilians/cops/gadget on GangMembership only; all 8 module.yml at Host_Api 2.0; `gangsAllied` strict-allied; UserDataInitEvent kept its package; Vault link null-safe, cleared on disable; docket pins GR-01/02/03/05/06/08, GD-06 intact.

## WS6 asks
1 GangItemSourceContribution — necessary and NOT deferrable (F1). 2 `nameOf(int)` — decide, do not leave open. 3 DebugCommand anvil prefill — drop.

## Cannot verify
877/0, BUILD SUCCESS lines, module jar; `memberManager.add(new Member)` persistence path; the 128-line commands.json deletion.

## Rebase notes vs 837966c3 / ws6
`InformationManagerTest.java:41`: branch 121, tip 149 → correct value 117 (take the number, not the hunk). commands.json three-way (re-apply the 32 deletions onto the tip's file). module.yml Depends edits no overlap. Poms: adjacent `<module>` lines in the root pom. Settings/Messages/message*/settings.yml untouched here. gangland-api gains data/gang, data/placeholder/extension, events/user/UserDataInitEvent — no clash with ws6's LocalizedModuleYaml.

## For the docket
GR-new gang menus empty (fixed in the round) · CM-new waypoint gangid name (fixed) · US-new pre-existing: no `removeAttachment` anywhere; a reload that rebuilds a User leaks the prior PermissionAttachment (open).

## Orchestrator rulings (W54)
Fix round 1: F1 seam + filter bindings + adapters/tests restored (red-first), F2 reconciliation, F3 files moved to the wave folder, F4 `GangMembership.nameOf(int)` (ask #2 closed; ask #3 dropped), M5, M6 (drop the two dead deps; module deletion stays G5), M7 comment. Then rebase onto 837966c3, ws6 rebases on top, merge + smoke.
