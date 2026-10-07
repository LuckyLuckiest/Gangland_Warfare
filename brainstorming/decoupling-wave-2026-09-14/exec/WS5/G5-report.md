# WS5 G5 — delete the empty `gangland-infra/gangland-domain` module + docs sweep

Worktree `E:\Programming\java\wt\gangland-0.10.0-ws5`, branch `0.10.0-ws5`, HEAD `89746105` (0.10.0 merge:
WS5 `9e2c378e`+`f5caaaca` + WS6 G3 on top). Nothing committed this round, per instruction.

## Recovery note

Terminated by a session-limit 429 mid-G5 (last visible step: "verify the reactor still resolves correctly").
On resume, reconciled first: `git status` matched the coordinator's description exactly (4 changes — the 2
`git rm`'d `gangland-domain` files staged, `gangland-infra/pom.xml` and root `pom.xml` modified, no docs
touched yet); `git diff HEAD` on both poms confirmed the edits were exactly the intended ones (the
`<module>gangland-domain</module>` line and the root `dependencyManagement` `<dependency>` block, nothing
else); re-checked `gangland-api/pom.xml`/`gangland-build/pom.xml`/any test-jar reference for a stray mention —
same result as before the interruption (see "G4 confirmation" below). `mvn clean verify -DskipTests` as the
fast probe came back clean (18 reactor modules, all `SUCCESS`). No work was lost or needed redoing.

## G4 confirmation (already satisfied, verified not re-fixed)

```
grep -n "gangland-domain" gangland-features/gangland-mail/pom.xml gangland-features/gangland-gadget/pom.xml
```

Both files' only hit is an explanatory **comment** (from the WS5 fix-round work: `"gangland-domain is empty
(...); pointing this at gangland-gang instead is a pure retarget"` / `"gadget is gang-module-free again,
gangland-domain (now empty; ...) is no longer needed"`). Neither has an actual `<dependency>` block for
`gangland-domain` — confirmed by the absence of any `<artifactId>gangland-domain</artifactId>` line, in either
file. **G4 is satisfied**; nothing needed doing here.

## G5 — the module deletion

Searched every `pom.xml` in the reactor for `gangland-domain` (13 files matched). Of those, only **two** had a
real structural reference (a `<module>` line or a `<dependency>` block); the other eleven were all comments —
left untouched (they're accurate historical explanation of why a dependency was removed earlier in the WS5
stream, not stale claims about current structure):

- **`gangland-infra/pom.xml`** — removed `<module>gangland-domain</module>` from the `<modules>` list (kept
  `gangland-item`).
- **`pom.xml`** (root) — removed the `gangland-domain` `<dependency>` block from `<dependencyManagement>`.
- **`gangland-api/pom.xml`** — checked directly: no dependency block, only the WS5 G0 comment. Nothing to do.
- **`gangland-build/pom.xml`** — checked directly: zero mentions of `gangland-domain` at all, comment or
  otherwise. Unaffected, as expected.
- **No test-jar reference to `gangland-domain` exists anywhere** in the reactor (searched all poms for
  `test-jar` near any `gangland-domain` mention; none found).

Then deleted the module directory itself: `git rm -r gangland-infra/gangland-domain` (2 tracked files —
`pom.xml` and a leftover `module.properties` resource under `src/main/resources/org/luckyraven/gangland/gang/`;
the module's own `src/main/java`/`src/test/java` trees were already fully empty from the earlier WS5 G1-G3 work,
confirmed via "No sources to compile" in every build log since), then `rm -rf` the directory outright to clear
the untracked `.flattened-pom.xml` build leftover too.

## Docs sweep

Surveyed every file the ruling named for `gangland-domain`/`GangManager`/`MemberManager`/`RankManager`
mentions before editing any of them — some needed nothing, which is reported here rather than silently
skipped.

- **Worktree `CLAUDE.md`** (gitignored/untracked — edits are local-only, won't appear in `G5-package.diff`,
  per the project's own established convention):
  - Module table: the `gangland-infra/gangland-domain` row replaced with a `gangland-features/gangland-gang`
    row, matching the table's existing style for a runtime module (jar path, what it owns, `Depends`/`Plugins`
    facts — none for this module itself, but noted that turf/mail declare `Depends: [gang]` since they name
    its types directly).
  - The `-am` flag explanation's example sibling-dependency list: `gangland-domain` → `gangland-gang`.
  - The DI code example (`CreateAccountListener`) that showed `MemberManager memberManager` as a constructor
    parameter — stale since WS5 G2 (that class no longer takes it) regardless of the domain deletion; updated
    to the real current parameter (`UserDataLoader`).
  - The "Service / Manager Layer" list restructured into three groups (core-owned / module-owned /
    other-runtime-modules) instead of one flat list that implied `GangManager`/`MemberManager`/`RankManager`
    are core, with a pointer to the `GangMembership` holder for core code that needs a gang fact without a
    module dependency.
- **`documentation/module-loader.md`** — one real fix, outside the "Core seams" section (untouched, as
  instructed — that's WS6 G4's territory): the "Writing a module" section's transitive-dependency list
  (`gangland-core`, `gangland-domain`, `gangland-item`, `sign-api`) dropped `gangland-domain` and gained an
  explanation of where its former contents live now and how a module that needs `Gang`/`GangManager`/etc.
  directly (declare `gangland-gang` + `Depends: [gang]`) differs from one that just reads a gang fact (the
  `GangMembership` holder, no dependency at all). The one other hit (`WantedKillTrackers`'s row in the "Core
  seams" table) is inside the untouched section — left alone.
- **`documentation/developer/architecture.md`** — the `DataConfig` bean-production table row dropped
  `RankManager, GangManager, MemberManager,` (they're produced by the module's own `GangConfig` now, not
  `DataConfig`). One remaining generic `*Manager` example row still names `GangManager` alongside the
  (independently already-stale, out of scope) `WeaponManager` — left as-is, since it's illustrating the
  naming *pattern*, not asserting where the class lives.
- **`documentation/developer/dependency-injection.md`** — the "Phase 4: CONFIG" paragraph's flat manager list
  gained a clause explaining that module-owned managers (`GangManager`/`MemberManager`/`RankManager`) are
  produced in the exact same phase, by the exact same mechanism, just from a `@Configuration` class that lives
  in the module jar instead of `gangland-impl`. Left the file's illustrative `@Bean`/`@Qualifier` code snippets
  alone (their staleness — e.g. `UserManager` taking a `MemberManager` constructor parameter it never actually
  did — predates WS5 and isn't about domain/core ownership).
- **`documentation/developer/modules.md`** — the `data/` package table's three now-wrong rows
  (`account/gang/`, `account/gang/member/`, `rank/`) removed, replaced with a note above the table pointing to
  `gangland-gang` and `GangMembership` (mirroring `module-loader.md`'s new explanation). **Not fixed, flagged
  instead**: this document's command-group and other package tables (`sub/gang/`, `sub/rank/`, the
  `database/repositories`, `database/tables`, `listener`, `events` sections) also describe gang/rank as if
  still inside `gangland-impl` — but the *same* tables are equally wrong about `sub/car/`, `sub/civilians/`,
  `sub/cops/`, `sub/cuff/`, `sub/jail/`, `sub/fuel/`, `sub/lootchest/` and `sub/weapon/` (all moved to their
  own modules in much earlier waves, well before WS5). Fixing only the gang/rank rows in an already
  broadly-stale reference document would be inconsistent and doesn't reflect what's actually asked here
  (`gangland-domain`/manager-as-core specifically); this is pre-existing documentation debt spanning several
  waves' worth of module splits, not something this gate introduced or is scoped to fully correct.
- **`FRONT-PAGE.md` / `FRONT-PAGE.bbcode.txt`** (found at `documentation/FRONT-PAGE.*`, not the repo root) —
  checked in full for `gangland-domain`/manager mentions and for any module-installation language: **zero**
  hits either way. This is user-facing SpigotMC resource-page marketing copy (features, changelog-style
  entries), not an architecture document — it never named `gangland-domain` or any manager class, and no
  sibling feature section names its own runtime-module requirement either (checked the pattern against every
  other module-backed feature already documented there). **No change made** — there was nothing to fix, and
  inventing a new "requires module X" convention unilaterally, only for gangs, would be inconsistent with
  every other entry on the page.
- **`documentation/features/gangs.md`** — checked in full: zero `gangland-domain`/manager mentions (it's a
  pure command/config reference, correctly still accurate — commands, `settings.yml` keys and behaviour are
  all unchanged by the module move). Checked whether any sibling `documentation/features/*.md` file states its
  own runtime-module requirement, as a convention check before deciding whether to add one here — **none of
  them do** (not `cops-n-crooks.md`, not any other module-backed feature's doc). **No change made**, to stay
  consistent with the established house convention; the module-install fact now lives in
  `documentation/migration-0.10.0.md`'s new WS5 section (below) and `module-loader.md`, which is where every
  other module's install/failure-mode information already lives.
- **Changelogs** — confirmed untouched, per instruction (not searched or edited).

## `documentation/migration-0.10.0.md` — new WS5 section

Added `## WS5 — gangs, gang membership and ranks become the gangland-gang runtime module`, inserted in the
doc's chronological-landing order (immediately before the existing `## WS6 G3` section, since WS5's commits
landed first in the merged branch history). Three subsections, matching the document's established per-gate
style (numbered `### N.` subsections, WS3's loot-chest section as the closest structural precedent for "a
whole feature becomes a runtime module"):

1. **Database tables are unchanged — no migration.** Named the exact tables (`gang`, `gang_ally`, `member`,
   `rank`/`rank_parent`/`rank_permission`).
2. **What a server owner sees without the module installed** — deliberately more nuanced than WS3's loot-chest
   precedent, because the actual failure mode *is* more nuanced: turf/mail hard-fail with
   `module.dependency.missing` (they declare `Depends: [gang]`), civilians/cops-n-crooks/gadget silently
   degrade (`GangMembership` goes inert — friendly-fire/ally/car-sharing checks stop recognising gangmates,
   no error logged), `/glw gang*`/`/glw rank*` simply don't exist, and the gang menus still open but render
   empty (their `ItemSourceProvider` resolves through the same now-inert contribution).
3. **`gangland-infra/gangland-domain` is gone (G5)** — the module-deletion fact itself, framed as build-time
   only with no server-owner action.

## Gate

```
mvn clean verify   (fast probe, -DskipTests, on resume before the doc sweep) → BUILD SUCCESS, 18 modules
mvn clean verify   (full, after the doc sweep, final)                        → BUILD SUCCESS, 18 modules
mvn -pl gangland-build -am package (ran as part of both verify runs, never a separate -DskipTests invocation
                                     since verify already carries package through for a jar-shading module)
  → target/modules/gangland-gang-0.10.0.jar confirmed present, module.yml + commands.json at its root, both runs
```

Never installed at any point.

### Test count — Maven console rollup (W52)

| Module | This round |
|---|---|
| Gangland Core | 67 |
| Gangland Item | 43 |
| Sign API | 63 |
| Gangland API | 14 |
| Gangland (impl) | 234 |
| Gangland Gangs | 102 |
| Gangland Mail | 25 |
| Gangland Civilians | 23 |
| Gangland Turf | 91 |
| Cops N Crooks | 76 |
| Gangland Gadgets | 113 |
| Gangland NPC Shops | 17 |
| Gangland Loot Chests | 53 |
| **Total** | **921** |

**921, exactly matching the expectation ("921 expected minus nothing — domain already reports 0").**
`gangland-domain` no longer appears in the reactor at all (was already reporting 0 tests before this round's
deletion — the module directory held zero source files, main or test — so removing the empty shell changes
the *module count* [19 → 18 modules that build at all] but not the *test count*). The two deltas versus my
own `rebase2-report.md`'s prior total (916) — `Gangland (impl)` 232→234 (+2) and `Gangland Civilians` 20→23
(+3) — are entirely attributable to **WS6 G3** landing on top of my rebased commits in the `89746105` merge
(civilians' own `Messages.CIVILIAN_*` YAML migration work, a different stream), not to anything in this G5
gate.

## Docket notes (for the clerk)

- **GR-new** (gang menus rendering empty after the WS5 relocation) — **fixed**, in the W54 fix round (F1: the
  `GangItemSourceContribution` seam). Already noted in `G1-G3-fix1-report.md`; repeating here per instruction
  for a single consolidated docket-entry point.
- **CM-new** (`/glw waypoint gangid` echoing the raw id / dropping its gang-exists guard) — **fixed**, across
  two rounds: the name display in the W54 fix round (F4, `GangMembership.nameOf`), the dropped
  `GANG_DOESNT_EXIST` guard in the post-rebase re-review commit (`f5caaaca`, `GangMembership.isInstalled` +
  `WaypointGangIdCommand.gangUnknown`).
- **US-new** (no `PermissionAttachment#remove()` call anywhere — a `/glw reload` that rebuilds a `User` re-runs
  `RankPermissionApplier.initialize` and leaks the player's prior attachment) — **still open, not fixed this
  gate**, per the standing ruling. Confirmed still accurate: `grep -rn "removeAttachment"` across the reactor
  still returns nothing.
- **T-45/T-47** — named in the dispatch as pre-existing docket entries to leave untouched. Not investigated or
  modified this gate; noted here only to confirm they were not touched, per instruction.

## Subagents

**Zero** haiku subagents used this round, despite the ≤2 allowance. After surveying the actual scope (13 pom
hits, only 2 real; the doc sweep's genuinely-actionable edits landing in 5 small, well-understood locations;
2 of the 7 named files needing no change at all once actually checked), the total edit volume was small enough
and required enough cross-file judgment (deciding what's in-scope stale vs. pre-existing unrelated stale, per
file) that doing it directly was both faster and lower-risk than writing a precise-enough delegation brief.

## Deliverables

- This file: `exec/WS5/G5-report.md` (main checkout).
- `exec/WS5/G5-package.diff` (main checkout) — `git add -N . && git diff HEAD` in the worktree, then `git
  reset`; 240 lines (the 2 pom edits + the 5 tracked-doc edits; `CLAUDE.md`'s edits are not in this diff since
  the file is gitignored/untracked, per the project's own convention).
