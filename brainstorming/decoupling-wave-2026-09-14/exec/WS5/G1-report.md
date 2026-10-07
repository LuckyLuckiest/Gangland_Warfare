# WS5 G1 report — 2026-09-22

Status: NEEDS_CONTEXT

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws5` (branch `0.10.0-ws5`, clean, at `f999ab72`)
Scope requested: plan §4 G1 steps 5, 6, 7, 7b, 8, 9, 9b, 9c.

**No code changes made.** Per the brief's explicit instruction — "The compile boundary question comes first...
If, after step 6, gangland-impl consumers... cannot compile without G2's consumer moves, do NOT add an
impl→module dependency or duplicate classes: stop... and hand back the smallest G1/G2 split that compiles..." —
I answered that question first, with real greps against the current tree rather than reasoning from the plan
text alone, and it comes back **cannot compile**. Stopping here per that instruction. No `G1-package.diff` is
attached — nothing was written to disk.

---

## The compile-boundary finding

After G1 step 6 physically moves `Gang`, `GangAlliance`, `GangManager`, the 7 gang-owned contracts
(`GangAllianceRepositoryContract`, `GangLookupContract`, `GangMessageContract`, `GangPermissionBridgeContract`,
`GangSettingsContract`, `MemberRepositoryContract`, `RankLookupContract` — all 7, not 5+2, per §0c's ruling that
`GangLookupContract`/`RankLookupContract` move wholesale into the module rather than staying in the api),
`Member`/`MemberManager`, `Rank`/`RankManager`/`RankParent`/`RankAssignmentPolicy`/`RankPermission`,
`GangPermissions`, and `VaultPermissionBridge` out of `gangland-domain` and into the new `gangland-gang` module,
**127 files outside `gangland-domain` still import one of these types directly**, with zero contract/holder
indirection (confirmed by grep, not estimated from the plan's own file-count prose):

```
git grep -l -E "org\.luckyraven\.gangland\.gang\.(Gang\b|GangAlliance\b|GangManager\b|GangSettings\b|
  contract\.(GangAllianceRepositoryContract|GangLookupContract|GangMessageContract|GangPermissionBridgeContract|
  GangSettingsContract|MemberRepositoryContract|RankLookupContract)\b|member\.(Member|MemberManager)\b|
  rank\.(Rank|RankManager|RankParent|RankAssignmentPolicy|RankPermission)\b|permission\.GangPermissions\b|
  vault\.permission\.VaultPermissionBridge\b)" -- "*.java" | grep -v "^gangland-infra/gangland-domain/"
```

| Module | Files | Nature of the break |
|---|---|---|
| `gangland-impl` | **76** | **The hard blocker.** `gangland-impl` cannot depend on any module (compiler-enforced architectural rule, confirmed in CLAUDE.md and the plan itself). See breakdown below. |
| `gangland-features/gangland-turf` | 35 | Cheap — package names don't change (`Gang`/`GangManager`/etc. stay `org.luckyraven.gangland.gang.*`, only the Maven artifact they ship in changes), and turf already depends on `gangland-domain` directly. A one-line pom swap (`gangland-domain` → `gangland-gang`, or add alongside) fixes all 35 with zero code change — this is G4 step 21's own content, pulled forward. |
| `gangland-features/gangland-mail` | 12 | Same as turf — already depends on `gangland-domain` directly (added in WS5 G0 for an unrelated reason). One-line pom swap, zero code change — G4 step 22's content, pulled forward. |
| `gangland-features/gangland-gadget` | 2 | `GanglandCarGangs.java` (needs the real G2 step 16d rewrite onto `GangMembership.alliedOrSame` — a genuine behavior-preserving code change, not a pom fix, because S1 explicitly decided gadget must stay gang-module-free) + `GadgetModuleConfig.java`. |
| `gangland-features/gangland-civilians` | 1 | `GangAllyWeaponImpactListener.java` — needs G2 step 16b's rewrite onto `GangMembership.alliedOrSame`. Same S1 constraint as gadget. |
| `gangland-features/cops-n-crooks` | 1 | `TurfFriendlyFireListener.java` — needs G2 step 16c's rewrite onto `GangMembership.gangsAllied`. Same S1 constraint. |

**turf and mail are not actually a problem** — 47 of the 127 files fix themselves with a 2-line pom edit (one
line per module) and zero source change, because the moved classes keep their existing package name. I would do
this regardless of how the rest of this question resolves; it's not the blocker.

**gadget/civilians/cops-n-crooks (4 files) are a real, small, well-scoped piece of G2** (steps 16b/16c/16d) —
S1's whole point was these three modules never get a `Depends: [gang]`/`provided` pom entry, so the fix has to be
the `GangMembership`-based rewrite, not a dependency addition. This is 3-4 files, already fully specified by the
plan (exact target: `GangMembership.alliedOrSame(UUID,UUID)` / `.gangsAllied(int,int)`), and safe to fold into
this gate.

**`gangland-impl`'s 76 files are the real blocker.** Breakdown:

| Category | Count | Already G1's own scope? |
|---|---|---|
| Persistence (`database/repositories/{gang,rank}`, `database/tables/{gang,rank}`, `database/repositories/player/MemberRepository.java`, `database/tables/player/MemberTable.java`) + their 2 SPI test files | 14 | **Yes — step 7.** |
| `GanglandDatabase.java` (rank-seeding body deletion) | 1 | **Yes — step 7b.** |
| `GangModuleConfig.java` (deleted outright) | 1 | **Yes — step 9.** |
| The 4 impl-side binder classes that die with it (`GanglandGangMessages`, `GanglandGangPermissionBridge`, `GanglandGangSettings`, `GanglandRankLookup` — module owns its YAML directly now) | 4 | **Yes — step 9 (implied deletion).** |
| **Subtotal already in G1's own step list** | **20** | — |
| The 31 gang/rank commands (`command/sub/gang/*` 18, `command/sub/rank/**` 13) + 1 companion test | 32 | **No — this is G3 step 20's content** (the plan bundles the physical relocation into the step that also swaps `.open(player)` → Oriel's `MenuOpener`; there is no separate earlier step that moves these files). |
| `listener/gang/GangMembersDamageListener.java` | 1 | **No — G3 step 20b.** |
| `config/{DataConfig,GameplayConfig,SchedulingConfig,WiringConfig}.java` (delete every gang-typed bean method) + `WiringConfigTest.java` | 5 | **No — G2 step 17.** |
| `data/placeholder/worker/GanglandPlaceholder.java` | 1 | **No — G2 step 12.** |
| `data/user/UserDataLoader.java` | 1 | **No — G2 step 10.** |
| `bootstrap/PlayerBootstrapService.java` | 1 | **No — G2 step 11.** |
| `listener/player/{CreateAccountListener,BountyIncreaseListener,LevelUpListener}.java` + `CreateAccountListenerTest.java` | 4 | **No — G2 steps 11/15b/15c.** |
| `Gangland.java` (Vault lifecycle + bStats chart deletion) | 1 | **No — G2 step 16.** |
| `command/sub/debug/{ComponentExecutorCommand,DebugCommand}.java` | 2 | **No — G2 step 14.** |
| `command/sub/waypoint/WaypointGangIdCommand.java` (+ `WaypointCommand.java` loses its sub-argument wiring) | 2 | **No — G2 step 13.** |
| `events/gang/{GangBountyEvent,GangLevelUpEvent}.java` | 2 | **No — G2 step 15.** |
| `file/configuration/inventory/itemsource/GangItemSourceProvider.java` | 1 | **No — G3 step 18b, itself blocked on WS2's Oriel registry mechanism landing (named as an open blocker in the plan's own §10 WS2 ask — not yet confirmed settled).** |
| `gang/GangFilterAdapter.java` + `gang/GangFilterAdapterTest.java` + `gang/member/MemberFilterAdapter.java` + `gang/member/MemberFilterAdapterTest.java` | 4 | **No — S3's "moved once here" (they currently sit in `gangland-impl`, not `gangland-domain` as S3 assumed; the move is real WS5 work with no earlier step number attached — closest to step 6's spirit but not itemized).** |
| **Subtotal genuinely new G2/G3-shaped work** | **56** | — |

56 + 20 = 76, reconciles exactly.

---

## What this means

The plan's own gate sequence (G0 → G1 → G2 → G3 → G4 → G5) is **not independently compiling at the G1 boundary**
— G1 alone (or even G1 + a full `mvn clean install -pl gangland-features/gangland-gang,gangland-impl -am`, the
gate command the plan itself specifies for G1) breaks the reactor, because `gangland-impl` genuinely cannot
compile without either (a) every one of its 76 `gang.*`-touching files being fixed in the same gate, or (b) a
compiler-forbidden impl→module dependency. This is a real gap in the plan's own sequencing, not a
misunderstanding on my part — confirmed by direct grep against the current tree, not by re-deriving it from the
plan's file-count prose (which is what led the plan to (correctly) flag this exact risk as still-open work: G2's
own step list is titled "the hard part" and explicitly exists to do this, but the plan never states that G1's own
gate command can't be green until G2 also lands).

## Proposed smallest compiling split

**Fold into this gate** (on top of G1's own 8 steps, unchanged):
1. **Turf + mail pom fix** (2 files, no code change): swap/add `gangland-gang` in place of `gangland-domain` for
   the classes that moved. Trivial, zero risk, matches G4 steps 21-22's eventual content exactly.
2. **G2 steps 16b/16c/16d** (4 files: `GanglandCarGangs.java`, `GadgetModuleConfig.java`, `GangAllyWeaponImpactListener.java`,
   `TurfFriendlyFireListener.java`) — the `GangMembership`-based rewrite, already fully specified by the plan
   (needs `GangMembership`/`GangMembershipView` to exist first, which is G1's own step 9b — so this genuinely
   belongs right after G1's skeleton is up).
3. **G2 steps 10-17** in full (≈17 files: `UserDataLoader`, `PlayerBootstrapService`, `CreateAccountListener` +
   its test, `GanglandPlaceholder`, `WaypointGangIdCommand` + `WaypointCommand`, `ComponentExecutorCommand` +
   `DebugCommand`, `events/gang/*` (2), `BountyIncreaseListener`/`LevelUpListener`, `Gangland.java`,
   `config/{DataConfig,GameplayConfig,SchedulingConfig,WiringConfig}` + `WiringConfigTest`).
4. **G3 step 20 + 20b's file-relocation content only** (≈33 files: the 31 gang/rank commands + their 1 test +
   `GangMembersDamageListener`) — moved into the module bodily, since they cannot otherwise compile in
   `gangland-impl`. The Oriel-`MenuOpener`-swap *behavior* change that step 20 also describes can be deferred to
   an actual G3 gate later if desired; the file relocation itself cannot be deferred, because leaving them in
   `gangland-impl` is what breaks the build.
5. **`GangFilterAdapter`/`MemberFilterAdapter` + their 2 tests** (4 files) — S3's already-decided single move,
   done here since there's no earlier step number for it and it blocks compilation exactly like row 4.

**Cannot be folded in, name it as a known gap**: `GangItemSourceProvider.java` (G3 step 18b) is blocked on WS2's
Oriel registry-injection mechanism per the plan's own §10 ask, which I have not independently re-verified as
landed. If it hasn't, this file needs either a temporary no-op stub (losing the gang-menu row-scoped
`%member_*%`/`%ally_*%` tokens until a real G3 gate) or the gate stays red on this one file until WS2's mechanism
is confirmed — a genuine open dependency, not something I can resolve unilaterally.

**Total estimated file count for a compiling gate**: G1's own ~40 (module skeleton + the moves already scoped)
+ 56 (gangland-impl consumer work) + 4 (gadget/civilians/cops-n-crooks) + 2 (turf/mail pom lines) ≈ **100 files**,
minus whatever `GangItemSourceProvider`'s resolution needs. This is roughly G1+G2 combined plus the
file-relocation slice of G3 — not a small addition to G1, but it is the actual minimum for `mvn clean verify` to
pass anywhere in this sequence before G4/G5.

---

## Recommendation

I have **not** started implementing any of steps 5-9c, to avoid producing a half-built module that still can't
compile and would need rework once the real scope is confirmed. Options for how to proceed, for the
orchestrator's call:

**(a)** Approve the combined G1+G2+G3(file-relocation) bundle above (~100 files) as this gate's real scope, and
I execute it as one (large) gate — genuinely faithful to "smallest split that compiles," just larger than "G1"
alone ever could be given the architecture.

**(b)** Re-sequence the plan so G1 does the module skeleton + persistence move (steps 5-9c, as originally scoped)
but targets **only** `mvn clean install -pl gangland-features/gangland-gang -am` (the module in isolation,
never touching `gangland-impl`/turf/mail/gadget/civilians/cops-n-crooks) as its gate, explicitly deferring the
full-reactor green build to whatever gate absorbs G2+G3's consumer work — i.e., accept that G1's own gate command
in the plan (`-pl gangland-features/gangland-gang,gangland-impl -am`) is wrong/premature and should drop
`gangland-impl` from the target list until G2 lands.

**(c)** Something else the orchestrator prefers (e.g., splitting the ~100-file bundle into two sub-gates of its
own).

I can start immediately on whichever the orchestrator picks. No commits, no diff, no further changes made this
turn — purely the investigation above.
