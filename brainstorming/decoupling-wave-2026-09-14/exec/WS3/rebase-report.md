# WS3 rebase onto 0.10.0 (post-CUT) — 2026-09-22

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws3`. `git rebase 0.10.0` (5d6cbefc, "0.10.0 CUT: inventory-api is
gone; Keystone 1.11.1") replayed cleanly as a single commit — no `--abort`/manual-reconstruction fallback needed.

**Rebased commit: `61fb6a6ac7f8502128249a919212fe55ca7f52b8`**, message unchanged ("0.10.0 WS3 G1+G2: Gangland
consumes keystone-hologram; loot chests become the gangland-lootchest module"), parent `5d6cbefc`. Working tree
clean after `git rebase --continue`; nothing left uncommitted.

## Conflicted files and how each was resolved

Git's rename-follow heuristic did almost all of the work by itself — of the ~15 lootchest/hologram files both
commits touched, only 5 needed a manual resolution; everything else (`LootChestWand.java`, 369-line CUT rewrite;
`LootChestWandEditCommand.java`; `LootChestWandListener.java`; `LootChestSession.java`; `gangland-ui/lootchest-api/pom.xml`;
`KernelConfig.java`) auto-merged onto my renamed/moved paths with zero markers.

| # | File (module path) | Conflict shape | Resolution |
|---|---|---|---|
| 1 | `.../lootchest/LootChestManager.java` | Import-block only: CUT's side (`Gangland`+`Getter` for a new `InventoryService` field) vs. mine (`JavaPlugin`, D7 rule). Body outside the marker block had already auto-merged CUT's new `@Getter InventoryService inventoryService` field/ctor-param cleanly. | Kept `import lombok.Getter;` (needed by CUT's new field) + `import org.bukkit.plugin.java.JavaPlugin;` (mine); dropped `import org.luckyraven.gangland.Gangland;` — the merged body uses `JavaPlugin gangland` throughout, consistently. |
| 2 | `.../lootchest/LootChestService.java` | Import-block only: mine (`org.luckyraven.keystone.hologram.HologramService` + the now-dead `org.luckyraven.gangland.inventory.InventoryHandler`) vs. CUT's (`org.luckyraven.gangland.hologram.HologramService`, pre-G1). Body already auto-merged onto CUT's `SharedLootInventory`-based rewrite. | Kept `keystone.hologram.HologramService` (G1); dropped both the old `gangland.hologram` import and `InventoryHandler` — the merged body has zero remaining references to either. |
| 3 | `.../lootchest/SharedLootInventory.java` | "File location" advisory, not a text conflict — git detected CUT's brand-new file landed inside a directory my commit renamed and pre-relocated it to the module path, flagged `added by us` pending confirmation. | Confirmed clean (no markers), `git add`ed as-is. |
| 4 | `gangland-impl/.../config/GameplayConfig.java` | CUT's side re-added the whole hologram/lootchest `@Bean` block (`hologramService`, `lootChestManager` — now with a 4th `InventoryService` param, `lootChestService`, `lootChestLoader`) since CUT built on `85299070` and never saw my G2 deletion of that block. My side deletes the block outright (moved to `LootChestModuleConfig` in G2). | Took mine (block deleted) — G2 already relocated this wiring to the module; keeping a duplicate in `GameplayConfig` would double-register `HologramService`/`LootChestManager` beans. Carried CUT's `InventoryService` addition forward into `LootChestModuleConfig.lootChestManager` instead (see "Follow-up fixes" below). |
| 5 | `gangland-ui/pom.xml` | `<modules>` list: CUT's side still has `hologram-api` (never saw my G1 deletion) and removed `inventory-api`; mine has `inventory-api` (never saw CUT's deletion) and removed `hologram-api`. | Removed both — final list is `sign-api`, `lootchest-api`. |

## Follow-up fixes beyond the literal conflict markers (required for compile, not flagged by git as conflicts)

Three files existed only in one branch's diff (no prior version at that path for the other side to conflict
against), so the rebase silently left them where CUT put them — I had to find and relocate them myself:

- `gangland-impl/src/test/.../listener/loot/LootChestWandListenerTest.java` → `git mv` to
  `.../lootchest/listener/LootChestWandListenerTest.java` (module test tree). Package line
  `org.luckyraven.gangland.listener.loot` → `org.luckyraven.gangland.lootchest.listener`; `mock(Gangland.class)` →
  `mock(JavaPlugin.class)` (D7 — the module can't compile against the concrete `Gangland` class, and the test never
  exercised anything `Gangland`-specific).
- `gangland-impl/src/test/.../lootchest/LootChestWandTest.java` → `git mv` to `.../lootchest/LootChestWandTest.java`.
  Package already `org.luckyraven.gangland.lootchest` (matched by coincidence — no edit needed).
  Already `mock(JavaPlugin.class)`, no `Gangland` reference.
- `gangland-ui/lootchest-api/src/test/.../listener/LootChestListenerTest.java` → `git mv` to
  `.../lootchest/listener/LootChestListenerTest.java`. Package already `org.luckyraven.gangland.lootchest.listener`
  — no edit needed.

`LootChestModuleConfig.java` (mine, untouched by the rebase since CUT never saw it) needed a manual follow-up: its
`lootChestManager` `@Bean` still called the 6-arg `LootChestManager` constructor; CUT's rewrite (merged into
`LootChestManager.java` above) added a 7th `InventoryService inventoryService` parameter. Added `InventoryService
inventoryService` to the bean method's own parameter list and threaded it through to the constructor call.

## Module pom (`gangland-features/gangland-lootchest/pom.xml`)

Per the brief: dropped the temporary `inventory-api` dependency (artifact no longer exists — CUT deleted it
outright). Kept `XSeries`/`item-nbt-api-plugin`/`anvilgui` — all three are still directly used by
`LootChestWand.java` (`com.cryptomorin.xseries.XMaterial`, `de.tr7zw.nbtapi.NBT`, `net.wesjd.anvilgui.AnvilGUI`,
confirmed by grep after the merge), so none of the three could be dropped. Added instead:

- `keystone-inventory` (default/provided scope, no explicit `<scope>` tag — same convention `gadget`/`turf`/
  `cops-n-crooks` already use) — required by `LootChestWand.java`'s `ChestMenuBuilder`/`PagedRegion`/`ItemComponent`/
  `FillComponent`/`ClickContext` imports (CUT's Oriel rewrite) and by `LootChestModuleConfig`'s new
  `InventoryService` bean parameter.
- `gangland-core` test-jar, scope test — the exact dependency the brief flagged CUT added to `lootchest-api`'s pom;
  needed because `LootChestWandTest`/`LootChestListenerTest` both call `BukkitRegistryFixture.install()`
  (`org.luckyraven.gangland.core.testsupport`, gangland-core's shared test support).
- `keystone-testkit`, scope test (inherited from root `dependencyManagement`, not explicitly named in the brief) —
  needed because `LootChestWandListenerTest` (the LS-31 pin, already mine pre-rebase) uses
  `org.luckyraven.keystone.testkit.RecordingNbtAccessor`. No other module currently declares this test dependency
  directly (only `gangland-impl` did, pre-move); flagged here since it wasn't in the brief's explicit list.

## Build

`mvn clean verify` (full reactor, one build) → **`BUILD SUCCESS`**, 01:00 min, 0 failures/errors anywhere.
`mvn -pl gangland-build -am package -DskipTests` → **`BUILD SUCCESS`**, confirms
`target/modules/gangland-lootchest-0.10.0.jar` still emits with `module.yml` at its root.

**Postcondition grep** (`grep -rln "org\.luckyraven\.gangland\.inventory" --include="*.java" .` excluding `target/`):
**zero hits** — confirmed.

**Surefire total: 872** (sum of every module's `Tests run` rollup line), not the ~829 named as an expectation.
Reconciled exactly:

- My pre-rebase WS3 gate (G1+G2 report, `exec/WS3/G1-G2-report.md`) measured **861** on top of `85299070`, with
  `gangland-lootchest` at 31 tests.
- CUT's own commit message states **"Reactor 829 tests green"** — that number is CUT's *own* branch total, measured
  on top of `85299070` **without** WS3's module split (i.e., `hologram-api`/`lootchest-api` still separate reactor
  modules on CUT's branch, not yet folded into `gangland-lootchest`). It is not the same baseline as mine, so the
  two numbers aren't directly comparable — CUT's 829 has no `LootChestModuleTest` (2 tests, WS3-only) and reflects
  whatever net test-count change CUT's own `InventoryParserRoundTripTest`/`SimplePagedMenuTest` edits made, counted
  against a different reactor shape.
- Post-rebase, `gangland-lootchest` is now **42** (31 + 11): the 3 new CUT test classes relocated into the module
  add exactly 11 methods — `LootChestWandListenerTest` (1: the LS-31 pin), `LootChestWandTest` (1: the LS-30 pin),
  `LootChestListenerTest` (9: the take-only-guard suite). 861 + 11 = **872**, exactly matching the measured total.
  Every other module's count is unchanged from my pre-rebase gate (`gangland-impl` still 258, `gangland-mail` still
  25, etc. — confirmed against the same per-module rollup lines).

| Module | Tests run |
|---|---|
| gangland-core | 5 |
| gangland-infra/gangland-domain | 119 |
| gangland-infra/gangland-item | 43 |
| gangland-ui/sign-api | 63 |
| gangland-impl | 258 |
| gangland-features/gangland-mail | 25 |
| gangland-features/gangland-civilians | 20 |
| gangland-features/gangland-turf | 91 |
| gangland-features/cops-n-crooks | 76 |
| gangland-features/gangland-gadget | 113 |
| gangland-features/gangland-npc-shops | 17 |
| **gangland-features/gangland-lootchest** | **42** |
| **Total** | **872**, 0 Failures, 0 Errors, 0 Skipped |

## Anything not reconciled

Nothing left unresolved — every conflict got a real resolution (not a mechanical "take theirs"), every orphaned
CUT-only file got relocated and fixed for the module's compile boundary, and the full reactor is green. Two things
worth flagging to the 0.10.0 lead, not because they're broken but because they're outside this gate's authority:

- The **829 vs. 872 test-count mismatch** above is a baseline-comparison artifact, not a regression — reconciled
  in full in the "Build" section. Flagging in case the 829 figure was meant as a hard gate number rather than an
  estimate.
- **No smoke run** (per instruction — the 0.10.0 lead runs the merge smoke after fast-forwarding). The build/test
  gate is the only verification here.

No other commits were made; no reviewers were invoked.
