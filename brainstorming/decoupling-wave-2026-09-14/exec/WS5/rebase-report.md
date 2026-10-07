# WS5 G0 rebase report — 2026-09-22

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws5` (branch `0.10.0-ws5`)
Rebased `git rebase 0.10.0`: WS5 G0 (+ fix round 1), previously `578511ba` on top of `85299070`, replayed onto
CUT's `5d6cbefc` (0.10.0: inventory-api deleted, 17 gangland-impl files migrated to menu.*/Keystone dialect,
Keystone 1.11.1).

**Rebased commit: `f999ab72`** — one clean commit, original message preserved verbatim (confirmed via
`git log -1 --format=%B f999ab72`), now sitting directly on `5d6cbefc`
(`git log --oneline 0.10.0..HEAD` shows exactly this one commit).

---

## Conflicted files and their resolution

7 files conflicted, all in the same shape the coordinator predicted: an import-line collision between my
`org.luckyraven.gangland.gang.user.*` → `org.luckyraven.gangland.core.user.*` swap and CUT's own import changes
(new `menu.*`/Keystone imports, or the removal of now-dead `org.luckyraven.gangland.inventory.*` imports). In
every case the resolution was: keep my `core.*` identity imports, keep CUT's new import (when it added one), drop
the deleted-inventory-api import entirely. No file's *body* logic needed reconciling — every conflict was
import-block only, confirmed by checking the surrounding code (and, where relevant, that the "losing" import was
genuinely unused in the file body before dropping it).

| # | File | HEAD (CUT, 0.10.0) side | My (578511ba) side | Resolution |
|---|---|---|---|---|
| 1 | `gangland-impl/.../command/sub/debug/DebugCommand.java` | `gang.user.User/UserManager` + `menu.InventoryBuilder` | `core.user.User/UserManager` + `inventory.InventoryHandler`/`inventory.flow.MultiPanelInventory` | Kept `core.user.User/UserManager` (mine) + `menu.InventoryBuilder` (CUT's); dropped the two `inventory.*` lines (deleted package). |
| 2 | `gangland-impl/.../command/sub/gang/GangColorCommand.java` | `gang.user.User/UserManager` + `menu.InventoryBuilder` | `core.user.User/UserManager` + `inventory.InventoryHandler`/`inventory.part.Fill`/`inventory.util.InventoryUtil` | Same pattern: kept `core.user.*` + `menu.InventoryBuilder`; dropped 3 `inventory.*` lines. |
| 3 | `gangland-impl/.../command/sub/gang/GangCommand.java` | `gang.user.User/UserManager` (no replacement import — CUT deleted `gangStat`/`itemToBalance` outright rather than porting them, per its own left-behind comment at line 174) | `core.user.User/UserManager` + `inventory.InventoryHandler` | Kept `core.user.User/UserManager`; dropped `inventory.InventoryHandler` — confirmed unused anywhere in the file body (CUT's deletion comment explicitly says the GUI method it served had zero callers reactor-wide and is genuinely dead, not ported). **CUT's deletion wins outright here, exactly as the coordinator called it** — no bridge or import of mine collided with the deleted region itself, only with the now-stale import line above it. |
| 4 | `gangland-impl/.../config/KernelConfig.java` | `gang.user.UserFactory` | `core.user.UserFactory` + `inventory.InventoryHandler`/`inventory.service.InventoryRegistry` | Kept `core.user.UserFactory`; dropped both `inventory.*` lines. `InventoryRegistry` still appears in two `{@link}` javadoc comments (CUT's own, documenting "no longer takes InventoryRegistry") — harmless dangling doc reference, not a compile issue, not introduced by me. |
| 5 | `gangland-impl/.../file/configuration/inventory/InventoryRuntimeContext.java` | `gang.user.User/UserManager` | `file.configuration.Settings` + `core.user.User/UserManager` + `inventory.InventoryHandler` | Kept `core.user.User/UserManager`; dropped `inventory.InventoryHandler` (deleted) **and** the `Settings` import, which was confirmed unused anywhere in the file body (a stale leftover from before CUT's rewrite removed its last use). |
| 6 | `gangland-impl/.../listener/player/RemoveAccountListener.java` | `gang.bounty.Bounty` / `gang.user.User/UserManager` / `gang.wanted.Wanted` | `core.bounty.Bounty` / `core.user.User/UserManager` / `core.wanted.Wanted` + `inventory.service.InventoryRegistry` | Kept all 4 `core.*` identity imports; dropped `inventory.service.InventoryRegistry` — confirmed unused (CUT left a comment at line 57 explaining the `InventoryRegistry.clear()` call that used to run here is gone, matching the import's deletion). |
| 7 | `gangland-impl/.../sign/aspect/BountyAspect.java` | `gang.user.User/UserManager` + `menu.InventoryBuilder` | `core.user.User/UserManager` + `inventory.part.Fill` | Kept `core.user.User/UserManager` + `menu.InventoryBuilder` (CUT's); dropped `inventory.part.Fill`. |

No file required "CUT's deletion wins" in the stronger sense the coordinator flagged (an actual logic collision
inside the deleted `gangStat`/`itemToBalance` region) — that region auto-merged cleanly with no conflict marker at
all; the only friction was the import line sitting a few lines above it, resolved as row 3 above.

## Files CUT moved to a new module

The coordinator flagged loot-chest classes moving into `lootchest-api`. Checked: `gangland-ui/lootchest-api`
already existed as its own module before this rebase (an earlier wave's move, unrelated to WS5 or CUT), and none
of its files carry any WS5 edit — WS5 never touched anything under `lootchest/`. The one lootchest-*adjacent* file
WS5 did touch, `gangland-impl/.../listener/loot/LootChestEarnGoodsListener.java` (an import-line-only fix from the
original G0 pass), **stayed at its original path** — CUT didn't move it — and auto-merged with zero conflict; its
`core.user.*`/`core.events.level.*` import lines are intact post-rebase (verified by direct grep).
`gangland-ui/inventory-api` is confirmed fully gone: zero `.java` files remain under it (only a stale, gitignored
`target/` build directory), and it's no longer listed in `gangland-ui/pom.xml`'s `<modules>`.

---

## Post-rebase sweep

```
git grep -n -E "org\.luckyraven\.gang\.user\b|org\.luckyraven\.gangland\.gang\.user\.(User|UserManager|Level|UserFactory)\b|
  org\.luckyraven\.gangland\.gang\.bounty\.|org\.luckyraven\.gangland\.gang\.wanted\.|
  org\.luckyraven\.gangland\.gang\.events\.(bounty|level|user|wanted)\.|
  org\.luckyraven\.gangland\.gang\.rank\.Permission\b" -- "*.java"
```
**Zero matches.** No stray reference to any moved identity package survives anywhere in the post-rebase tree —
including in files CUT itself touched.

---

## Build

Full reactor, one build, never `mvn install`:

```
mvn clean verify   → BUILD SUCCESS
```

(The `ShutdownSequence`/`Log4j`/`GanglandSeizedInventoryService` `ERROR`-level lines in the console output are
expected — they're deliberately-triggered failure-path assertions inside `ShutdownSequenceTest` and
`GanglandSeizedInventoryServiceTest`, not build failures; same noise appeared in the pre-rebase WS5 gate runs.)

**Surefire count**: 156 `.txt` report files, summing to **835 total tests run, 0 Failures, 0 Errors, 0 Skipped** —
exactly matching the gate's expectation of **829 + 6 = 835**.

**Explaining the delta**: 829 is CUT's own reactor baseline on `0.10.0` (before this rebase); the `+6` is WS5's
net test delta across G0 and fix round 1 (G0: −3 obsolete `flushPermissions` tests + 6 new `User`-primitive tests
+ 2 `MemberCachePopulationOrderTest` cases = +5; fix round 1: +1, the `unsetPermission` red/green test) —
5 + 1 = 6, landing at exactly 835. The `.txt` **file** count itself isn't directly comparable across gates (153
files in the last stand-alone WS5 fix1 gate vs. 156 here) because CUT's own migration added/renamed test classes
unrelated to WS5 (e.g. `LootChestWandListenerTest`/`LootChestWandTest`, part of CUT's loot-chest rewrite) — the
+3 file delta is CUT's, not WS5's, and the **tests-run total** (835) is the number that reconciles exactly.

---

## Deliverables

- `exec/WS5/rebase-report.md` (this file)

No package diff requested for this task (rebase, not a fix round) — the rebased commit `f999ab72` on
`0.10.0-ws5` is the artifact. No other commits made, no smoke run (merge smoke — including the step-1c live
gangId-attach proof — runs on `0.10.0` afterwards, per the coordinator's instruction), no reviewers spawned.
