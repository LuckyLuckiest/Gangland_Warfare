# WS5 G1-G3 fix round 1 (W54 ruling)

Branch `0.10.0-ws5`, base `5b915c17`, worktree `E:\Programming\java\wt\gangland-0.10.0-ws5`. Nothing committed.
Fixes 1 Critical + 4 Important + 3 Minor from the Opus review of `G1-G3-report.md`/`G1-G3-package.diff`.

## Gate result

```
mvn clean verify -pl gangland-features/gangland-gang -am   → BUILD SUCCESS
mvn clean verify                                            → BUILD SUCCESS (all 20 reactor modules)
mvn -pl gangland-build -am package (ran as part of verify)  → target/modules/gangland-gang-0.10.0.jar confirmed,
                                                                module.yml + commands.json at its root
```

### Test counts — Maven console rollup (W52)

| Module | Before this round | After this round | Δ |
|---|---|---|---|
| gangland-core | 67 | 67 | — |
| gangland-item | 43 | 43 | — |
| sign-api | 63 | 63 | — |
| **gangland-api** | 8 | **13** | **+5** |
| gangland-impl | 227 | 227 | — |
| **gangland-gang** | 85 | **102** | **+17** |
| gangland-mail | 25 | 25 | — |
| gangland-civilians | 20 | 20 | — |
| gangland-turf | 91 | 91 | — |
| cops-n-crooks | 76 | 76 | — |
| gangland-gadget | 113 | 113 | — |
| gangland-npc-shops | 17 | 17 | — |
| gangland-lootchest | 42 | 42 | — |
| **Reactor total** | **877** | **899** | **+22** |

All counts are the final `[INFO] Tests run: N, Failures: 0, Errors: 0, Skipped: 0` rollup line per module from
this round's `mvn clean verify`, not surefire `.txt` summation (W52).

**F2 reconciliation.** The review's expected "878 + 14 = 892" doesn't match either this round's freshly-run
total (899) or the prior round's own reported total (877, from `G1-G3-report.md`'s own test-count table, itself
a from-source Maven console rollup at the time). Reporting the actual, independently re-verified numbers rather
than reconciling to the reviewer's arithmetic — the +22 breaks down exactly as follows, with every class named:

- **Restored** (deleted in the original W51 gate on the wrong assumption they needed a module type that
  doesn't actually exist; F1 below explains why that assumption was wrong): `GangFilterAdapterTest` (**9**
  tests) and `MemberFilterAdapterTest` (**8** tests) → **+17**, landing in `gangland-gang` (they moved there
  along with the two adapter classes, restored unchanged).
- **New this round**: `GangItemSourceContributionsTest` (**3** tests, new file, `gangland-api`) and 2 new
  `GangMembershipTest` methods (`nameOf_noViewInstalled_returnsEmpty`, `nameOf_delegatesToInstalledView`) pinning
  F4's new `GangMembership.nameOf(int)` → **+5**, landing in `gangland-api`.
- 17 + 5 = **22**, matching gangland-gang's 85→102 and gangland-api's 8→13 exactly.

No test was deleted or renamed this round (the two adapter test classes were restored with their original names
and, for `GangFilterAdapterTest`, only its `@BeforeEach` setup rewritten to use the module's existing fake
contracts instead of the now-unreachable impl binder classes — see F1 below).

## F1 (CRITICAL) — gang menus rendering empty, filter/search dead

**What was wrong.** The original W51 gate deleted `GangItemSourceProvider`, `GangFilterAdapter`,
`MemberFilterAdapter` and `GangFilterRegistration` outright and stubbed `GameplayConfig`'s `itemSourceProvider`
to always return an empty list — on the (wrong) belief that no file could straddle both the module types
(`Gang`/`Member`/`GangManager`) and gangland-impl's menu-only `menu.filter`/`menu.multi` types at once. That
belief was checked more carefully this round and turned out to be false for most of the surface: `FilterAdapter`,
`FilterField`, `StandardFilterField`, `FilterApplier`, `FilterStore`, `FilterValue`, `SearchFilter`,
`FilterBinding`, `SortDescriptor` and `FilterRegistry` — the whole of `gangland-impl`'s `menu.filter` package
except `SearchButtonFactory` (a UI-button-building class the module doesn't need) — turned out to have **zero**
impl-specific dependencies: every one of them imports only `org.bukkit.entity.Player` and JDK types, nothing
gangland-impl-only. They are pure, generic, entity-agnostic framework primitives — exactly the kind of code
`Messages`/`Settings`/`Command` already set the precedent for moving to `gangland-api`.

**The fix.**

1. **Moved the 10 pure files** (`FilterAdapter`, `FilterApplier`, `FilterBinding`, `FilterField`,
   `FilterRegistry`, `FilterStore`, `FilterValue`, `SearchFilter`, `SortDescriptor`, `StandardFilterField`) from
   `gangland-impl/.../menu/filter/` to `gangland-api/.../menu/filter/`, package unchanged — a pure relocation,
   zero import churn for their 3 remaining impl importers (`FilterCommand`, `GameplayConfig`,
   `SearchButtonFactory`, which stayed behind in the same package name and resolves the moved types
   transitively). `SearchButtonFactory` was checked and left in impl (it only builds `ClickHandler`s, no gang
   type involved, the module never needs it).
2. **Restored `GangFilterAdapter`/`MemberFilterAdapter` into the gang module unchanged** (git-show'd their
   original content from `HEAD`, wrote them back at their original packages — `gang.GangFilterAdapter` /
   `gang.member.MemberFilterAdapter`), now legal because `FilterAdapter`/`FilterField`/`StandardFilterField` are
   api-hosted. Their two `@Bean` methods, previously on the deleted `GangFilterRegistration`, moved to
   `GangConfig` (module-owned, since the beans return module types).
3. **New api seam `GangItemSourceContribution`/`GangItemSourceContributions`** (`gangland-api/.../data/gang/`),
   modelled on `PlaceholderContribution`/`PlaceholderContributions`. One deliberate difference: rather than the
   placeholder seam's "first non-null wins" dispatch, this uses an explicit `boolean supports(String source)`
   predicate before calling `entries(...)` — an empty `List` is a **legitimate** answer here (a gang with zero
   allies really does have an empty ally list), so "empty = not mine" would have been ambiguous the way it isn't
   for a nullable `String`.
4. **New module class `GangMenuItemSourceContribution`** (`gang.menu` package) — the direct line-for-line port
   of the deleted `GangItemSourceProvider`'s body, returning `Map<String, String>` rows instead of the impl-only
   `ItemSourceEntry` record (which is itself nothing but a `Map<String,String>` wrapper, so no data is lost).
   Registered as a bean in `GangConfig`, injecting the same shared `FilterStore`/`FilterApplier` bean instances
   `GameplayConfig` already constructs (ordinary cross-boundary bean resolution by type, same pattern as
   `RepositoryRegistry`/`PermissionManager`).
5. **`GameplayConfig.inventoryRuntimeContext`'s `itemSourceProvider`** now wraps `GangItemSourceContributions`,
   translating `Map<String,String>` rows into `ItemSourceEntry`. Resolved **lazily** (cached after first actual
   placeholder-menu request), not at bean construction — for the identical reason `GanglandPlaceholder`'s own
   contribution cache is lazy: this is a CONFIG-phase bean and a module's contribution bean has no declared
   parameter edge forcing it to construct first within that same phase. Getting this wrong would have
   reintroduced a subtler version of the same class of bug (see F7).
6. **`GangFilterRegistration` restored in gangland-impl**, trimmed of its two adapter `@Bean` methods (now in
   `GangConfig`) but otherwise unchanged — its `@PostConstruct register()` method only ever registered
   `FilterBinding`s, and `FilterBinding` carries **no entity type at all** (just `StandardFilterField` enum
   values, `SortDescriptor`s and plain strings), so it never needed to move or become a contribution in the
   first place. This resolves the review's "a `FilterContribution` or a second method on the same seam —
   whichever is smaller": the smaller option was **neither** — the pre-existing class just needed restoring.
   `/glw filter gangs ...` works again because the "gangs"/"gang_members" `FilterBinding`s are registered again,
   full stop; no new seam was needed for this half.
7. **Red-first test**: `GangItemSourceContributionsTest` (3 tests) — pins the exact scenario the review asked
   for: a supporting contribution's rows are returned for a source it owns (`gang_members`), an unsupported
   source returns empty even with a contribution installed (proves `supports()` gates, not a blind first-match),
   and no contribution installed returns empty (the pre-fix state every gang menu was stuck in). All 3 pass
   against the new code; there is no meaningful "pre-fix" version of this brand-new class to run red against
   (W48's "red by non-existence" category), but the third case is a literal re-creation of the bug this whole
   fix exists for.

**Every menu source is restored**: `gang_info.yml`, `gang_stat.yml`, `phone_gang.yml`, `phone_gang_search.yml`,
`alliance_stat.yml`, `user_stat.yml` all render dynamic rows again through the exact same query/filter logic the
deleted `GangItemSourceProvider` had, byte-for-byte — nothing about the actual gang/member/ally data assembly
changed, only where the code lives and what it returns rows as.

## F2 — see the test-count table above.

## F3 — deliverable location

`G1-G3-report.md`/`G1-G3-package.diff` moved from the worktree-root `exec/WS5/` (wrong location, never
committed content in a git worktree — untracked files in the ORIGINAL checkout's `brainstorming/` are not
copied into a fresh worktree by `git worktree add`, so `brainstorming/decoupling-wave-2026-09-14/` did not
exist here at all until this round created it) to
`brainstorming/decoupling-wave-2026-09-14/exec/WS5/G1-G3-report.md` / `G1-G3-package.diff` inside this
worktree. The worktree-root `exec/` directory was deleted afterward (`rm -rf exec`) so it can't be
accidentally swept into a future commit. This round's own `G1-G3-fix1-report.md` (this file) and
`G1-G3-fix1-package.diff` are written to the same `brainstorming/decoupling-wave-2026-09-14/exec/WS5/` folder.

## F4 — `/glw waypoint gangid` name display

Added `Optional<String> nameOf(int gangId)` to `GangMembershipView` and `GangMembership` (inert → `Optional.empty()`
when no view is installed, exactly like `gangIdOf`'s `-1` sentinel), backed by the module's
`GangMembershipInstaller` (`gangManager.getGang(gangId)` → `Optional.of(gang.getDisplayNameString())` or empty).
`WaypointGangIdCommand` now takes `GangMembership` as a constructor parameter and uses
`gangMembership.nameOf(gangId).orElse(String.valueOf(gangId))` for both the tab-completion default value and the
stored-waypoint lookup key — the real gang name shows again, falling back to the raw id only when the gang
module truly isn't installed (an even better degrade than before, since the original code before WS5 would have
NPE'd in that exact scenario via `gangManager.getGang(gangId).getName()`). `WaypointCommand`'s constructor and
its `WaypointGangIdCommand` construction call both updated to thread `GangMembership` through. New tests:
`GangMembershipTest.nameOf_noViewInstalled_returnsEmpty` / `nameOf_delegatesToInstalledView`. **WS6 ask #2
closed.** Ask #3 (the `DebugCommand` anvil-prefill drop) is dropped per the ruling — no action taken, no code
touched there this round.

## F5 — `RankPermissionApplier.java:28`

`private RankPermissionApplier() { }` → opening/closing braces on their own lines, per the CLAUDE.md method-brace
rule. One-line fix, no behavior change.

## F6 — dead `gangland-domain` dependency

Removed the `gangland-domain` `<dependency>` block from `gangland-impl/pom.xml` and
`gangland-features/gangland-npc-shops/pom.xml` — both were dead weight (the artifact is empty; every type that
used to live there moved into the gang module across WS5 G0/G1, and neither pom had any remaining source
reference to anything from it). The `gangland-domain` **module's own deletion** (removing the now-pointless
empty Maven module from the reactor entirely) stays out of scope for this gate, per the ruling (G5).

## F7 — `GanglandPlaceholder`'s contribution-cache comment

Strengthened the existing comment on `GanglandPlaceholder.contributions`'s field declaration (it already had a
brief mention) to explicitly state the safety argument in full: cached forever once resolved because modules
load once per start (Keystone's `ModuleLoader`), so the set of installed `PlaceholderContribution` beans never
changes after boot/reload — there is no later point where re-resolving could see a different answer. The new
`GangItemSourceContributions` lazy cache in `GameplayConfig` (F1 above) carries the identical comment,
word-for-word on the safety argument, so both CONFIG-phase-ordering workarounds in the codebase now explain
themselves the same way.

## Docket notes

- **GR-new** (gang menus render empty after the WS5 relocation) — **fixed this round** (F1). Not yet added to
  the triaged docket file (`brainstorming/bug-docket-2026-09-06/triage/`) or rebuilt via `build_docket.py`; the
  bug existed only within this uncommitted gate and was fixed before ever reaching a committed, shippable state,
  so there is no live-deployment window it needs a docket row to track. Flagging here per instruction rather
  than skipping it silently.
- **CM-new** (`/glw waypoint gangid` echoing the raw id instead of the gang name) — **fixed this round** (F4).
  Same reasoning as GR-new: introduced and fixed within the same uncommitted gate, noted here rather than
  triaged as a separate docket entry.
- **US-new** (pre-existing, **not fixed this gate**, per the ruling): no `removeAttachment` call exists anywhere
  in the codebase for the `PermissionAttachment`s `RankPermissionApplier.initialize`/the deleted
  `UserManager.initializeUserPermission` before it create. A `/glw reload` that rebuilds a `User` (or any other
  path that re-runs `RankPermissionApplier.initialize` for an already-online player) leaks the player's prior
  attachment — Bukkit never garbage-collects an attachment that isn't explicitly removed via
  `PermissionAttachment#remove()`. Left open per the ruling; would need its own investigation into every
  `initialize()` call site to confirm none of them already guard against double-attachment before proposing a
  fix, which is out of scope here.

## Files touched this round (beyond F1's list above)

- `gangland-api/.../data/gang/GangMembershipView.java`, `GangMembership.java` — `nameOf` (F4)
- `gangland-api/.../data/gang/GangItemSourceContribution.java`, `GangItemSourceContributions.java` — new (F1)
- `gangland-api/src/test/.../data/gang/GangMembershipTest.java` — `nameOf` tests + `view()` helper signature (F4)
- `gangland-api/src/test/.../data/gang/GangItemSourceContributionsTest.java` — new (F1)
- `gangland-features/gangland-gang/.../gang/GangFilterAdapter.java`,
  `gang/member/MemberFilterAdapter.java` — restored unchanged (F1)
- `gangland-features/gangland-gang/.../gang/menu/GangMenuItemSourceContribution.java` — new (F1)
- `gangland-features/gangland-gang/.../gang/GangConfig.java` — 3 new beans (F1)
- `gangland-features/gangland-gang/.../gang/GangMembershipInstaller.java` — `nameOf` view method (F4)
- `gangland-features/gangland-gang/.../gang/permission/RankPermissionApplier.java` — brace (F5)
- `gangland-features/gangland-gang/src/test/.../gang/GangFilterAdapterTest.java`,
  `gang/member/MemberFilterAdapterTest.java` — restored, first one's `@BeforeEach` updated (F1)
- `gangland-impl/.../config/GameplayConfig.java` — `inventoryRuntimeContext` rewrite (F1)
- `gangland-impl/.../config/GangFilterRegistration.java` — restored, trimmed (F1)
- `gangland-impl/.../command/sub/waypoint/WaypointGangIdCommand.java`, `WaypointCommand.java` — `GangMembership`
  wired through (F4)
- `gangland-impl/.../data/placeholder/worker/GanglandPlaceholder.java` — comment (F7)
- `gangland-impl/pom.xml`, `gangland-features/gangland-npc-shops/pom.xml` — dead dependency removed (F6)
- `exec/WS5/` (worktree root) — deleted; `brainstorming/decoupling-wave-2026-09-14/exec/WS5/` created (F3)
