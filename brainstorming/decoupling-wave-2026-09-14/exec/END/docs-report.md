# END docs report — 2026-09-23

Status: DONE

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws6` (branch `0.10.0-ws6`, HEAD `207e7282` = the final 0.10.0
tip). Docs only — no Java, no poms, no smoke, no commits, no subagents. Read the worktree's `CLAUDE.md` and
`brainstorming/decoupling-wave-2026-09-14/exec/LEAD-RULES.md` first, per brief.

## What changed (file paths relative to the worktree)

1. **`documentation/features/inventory.md`** — full rewrite. The old ~540-line doc kept an "API Usage" section
   (~200 lines) documenting the deleted `InventoryHandler`/`MultiInventory`/`MultiPanelInventory` trio as if live
   (`new InventoryHandler(...)`, `handler.open(player)`, `MultiInventoryCreation.dynamicMultiInventory(...)`,
   `new MultiPanelInventory<>(...)`) and a `Multi.Per_Page` YAML field that no longer exists. Verified against
   `documentation/developer/ui-framework.md` (already rewritten at the WS2 CUT gate) and
   `brainstorming/decoupling-wave-2026-09-14/plans/WS2-inventory-keystone.md` §2/§3, then grepped every class name
   against the worktree sources before writing (`InventoryParser`, `InventoryBuilder`, `ChestMenuBuilder`/`ChestMenu`
   at `gangland-impl/src/main/java/org/luckyraven/gangland/menu/*`; `ButtonTags.DEFAULT` at
   `gangland-impl/.../menu/part/ButtonTags.java`; `SharedLootInventory` at
   `gangland-features/gangland-lootchest/.../lootchest/SharedLootInventory.java`). Kept the YAML author-facing
   reference (`Information`/`Slots`/click-actions/conditions) — confirmed still schema-accurate — but fixed
   `Multi.Per_Page` (removed; `InventoryData.java:24`'s own comment confirms T-43/WS2 G3 dropped the field) and the
   nav-button claim (no longer `settings.yml`-configurable — `ButtonTags.DEFAULT` is now a hardcoded constant;
   confirmed by reading `ButtonTags.java`'s javadoc). Replaced the dead API section with a short "Beyond YAML"
   section pointing at `ui-framework.md` for `MenuFlow<S>`/`Panel<S extends FlowState>`, the item-return contract,
   and the take-only loot chest, plus a closing pointer to the full developer doc.
2. **`documentation/developer/modules.md`** — targeted fixes, not a full rewrite:
   - `InventoryParser.java` row (was line 222, "Parses inventory YAML into `InventoryHandler` instances") now
     says it builds a Keystone `ChestMenu`, with a pointer to `ui-framework.md`.
   - Sub-command-group table: split into "still in `gangland-impl`" (bank, bounty, debug, fuel, item, wanted,
     waypoint — verified via `ls gangland-impl/.../command/sub/`) and a new "moved to a runtime module" table for
     car → `gangland-gadget`, civilians → `gangland-civilians`, cops/cuff/jail → `cops-n-crooks`, lootchest →
     `gangland-lootchest`, gang/rank → `gangland-gang` (WS5), each with its real current package (grepped
     `package` lines in each module's command class). `weapon` gets its own one-line "deleted, not moved" note.
   - Database `repositories/`/`tables/` tables: same treatment — car/copsncrooks/gang/lootchest/rank rows moved to
     a "moved or deleted" table with real packages; `weapon/WeaponRepository` and `weapon/WeaponTable` marked
     deleted (grepped, genuinely absent from the whole reactor).
   - `listener/` table: `gang/GangMembersDamage.java`, `loot/{LootChestEarnGoods,LootChestWandHandler}.java`,
     `npc/CivilianDeathRewardListener.java` rows moved to a "moved" table — all three were also **renamed**
     in the move (`GangMembersDamageListener`, `LootChestEarnGoodsListener`, `LootChestWandListener`), caught by
     grepping for the old names and finding nothing before finding the renamed classes.
   - `events/` table: `gang/{GangBountyEvent,GangLevelUpEvent}.java` rows removed with a pointer to
     `gangland-gang`'s `org.luckyraven.gangland.events.gang.*` (package unchanged, jar changed).
   - `file/configuration/` table: `copsncrooks/{GanglandCivilianSettings,GanglandCivilianSpawnConfigProvider}` →
     `gangland-civilians`; `copsncrooks/GanglandCopSettings` → `cops-n-crooks`; `GanglandBountySettings`/
     `GanglandWantedSettings` actually *stayed* in `gangland-impl` but under `configuration/wanted/`, not
     `configuration/copsncrooks/` (fixed in place rather than moved to the "gone" table); `inventory/itemsource/
     GangItemSourceProvider.java` → `gangland-gang`, renamed `GangMenuItemSourceContribution`;
     `lootchest/{GanglandLootChestMessages,LootChestSettings}` → `gangland-lootchest`; `weapon/WeaponLoader.java`
     deleted.
   - `item/` package table: fixed only the two named rows (`CarConverter` → `gangland-gadget`,
     `WeaponConverter` deleted); added a note flagging that `ItemConverterRegistry.java`/`ItemParser.java`
     themselves no longer exist under this package at all (promoted to Keystone's `keystone-item` in 0.9.0) —
     that staleness predates and is unrelated to this wave, explicitly left out of scope per the brief's "leave
     everything else."
   - `sign/` package table: `type/trade/car/` → `gangland-gadget`; `type/trade/weapon/` and the sibling
     ammo/wearable trade-sign subpackages deleted (all four were in the same table row family as the two named
     domains, fixed together for internal consistency); `validation/trade/CarSignValidator` → `gangland-gadget`,
     `WeaponSignValidator` deleted.
   - Whole `lootchest/` and `weapon/` top-level package sections under `gangland-impl` removed and replaced with
     a "moved wholesale" pointer table (`gangland-lootchest` module; `weapon/WeaponManager.java` deleted).
   - Resource Files table: `cops.yml`/`civilians.yml`/`cars.yml`/`loot/*.yml` moved into their owning module's jar;
     `ammunition.yml`/`wearables.yml`/`weapon/*.yml` deleted (Bartizan). Also fixed a row-ordering slip from the
     patch (`message/message_es.yml` had landed after the new "moved" table instead of next to `message_en.yml`).
3. **`documentation/developer/architecture.md`** — two rows in the `*`-suffix naming-convention table:
   `*Handler` example swapped `InventoryHandler` (deleted) for `FileHandler` (real, Keystone's `FileHandler`,
   used by every module per the YAML-dialect convention documented in `ui-framework.md`); `*Manager` example
   swapped `GangManager`/`WeaponManager` for `UserManager`/`WaypointManager` with a note that `GangManager` moved
   to `gangland-gang` (WS5) and `WeaponManager` was deleted (0.9.0) — matched grep hits for the same
   gang/weapon domains named in the brief, in the same table I was already editing for the mandated `*Handler`
   fix, so fixed together rather than leaving an adjacent, equally-wrong row.
4. **`documentation/developer/README.md`** — "Design Patterns Used" table's `Factory` row named
   `MultiInventoryCreation` (deleted, one of the 12 WS2 CUT-gate deletions) as a live example; replaced with
   `SlotItemFactory` (verified still present) plus a note pointing at `ui-framework.md`.
5. **`documentation/module-loader.md`** — the `WantedKillTrackers`/`WantedKillTracker` holder-table row listed
   `org.luckyraven.gangland.gang.wanted` `(gangland-domain)` as the current package; `gangland-infra/gangland-domain`
   is deleted (WS5) and the class actually lives at `gangland-core/src/main/java/org/luckyraven/gangland/core/
   wanted/WantedKillTracker.java` (`org.luckyraven.gangland.core.wanted`) — fixed to the real location.
6. **`CLAUDE.md` (worktree root, gitignored/untracked — local-only, not in `docs-package.diff`)** — the "Module
   Structure" table still listed `gangland-ui/lootchest-api` and `gangland-ui/hologram-api` as live modules, and
   `gangland-infra/gangland-domain` as a live domain-entity module, plus `gangland-api`'s re-export line still
   said "re-exports domain/item/core/sign" (the `domain` re-export was dropped at WS5 G0 per
   `gangland-api/pom.xml`'s own comment). Fixed: `gangland-domain` row now says "Deleted (WS5, 0.10.0)" with a
   pointer to where its types went; the two dead UI-api rows removed and replaced with real
   `gangland-features/gangland-gang` and `gangland-features/gangland-lootchest` rows (module `Id`/`Host_Api`
   verified from each module's `module.yml`); the `gangland-api` row's re-export text fixed. Because this file is
   gitignored, these edits are local to this machine only and do not appear in `docs-package.diff`; recorded here
   as prose instead.

## Deviations from the plan

- The brief's item 4 named "command-group and package tables" for the 10 domains; I additionally fixed a small
  number of directly-adjacent rows in the same tables that named the *same* domains but weren't literally listed
  (the `sign/` package's ammo/wearable trade-sign subpackages alongside car/weapon; `file/configuration/`'s
  `GanglandBountySettings`/`GanglandWantedSettings` package-path fix; `architecture.md`'s `*Manager` row). Did
  **not** touch the deeper, unrelated staleness these tables also exposed (the `item/` package's
  `ItemConverterRegistry`/`ItemParser` predating the keystone-item promotion; `architecture.md`'s
  `GameplayConfig`/`CopsAndGadgetsConfig` bean-phase tables at lines ~104-109, which still name
  `WeaponManager`/`LootChestManager`/`ItemParser` as CONFIG-phase beans and would need re-verifying actual
  `@Bean` method bodies, not just a grep-for-class-existence check; `gangland-compatibility`'s section in
  `modules.md`, which still describes NMS version adapters as live even though the root `CLAUDE.md`'s own
  "Version Compatibility" section says Gangland ships no NMS as of 0.9.0). All three are pre-existing staleness
  unrelated to this wave's deletions — flagged here as docket/follow-up candidates, not fixed.
- CLAUDE.md is out of the four named deliverables but directly matched three sweep-regex hits with actively wrong
  (not historical) text; fixed per the sweep's "every hit must be either historical or fixed" rule. Kept the edit
  surgical (3 rows fixed/added) rather than reconciling the whole Module Structure table (which is also missing
  the "eight modules" update to the "### Two tiers ... (0.9.0: six modules, weapons gone)" heading text below it —
  left alone, out of scope).

## Final sweep grep

```
grep -rn -E "scoreboard-api|inventory-api|hologram-api|lootchest-api|shop-api|gangland-domain|MultiPanelInventory|InventoryHandler|MultiInventory|FlowSession" CLAUDE.md README.md documentation
```

Every hit below is either historical (labeled as deleted/moved/"was X", inside a migration doc, or inside a dated
`v0.7.x-DEV` changelog describing what was true at that release) or was fixed in this pass:

| File | Hit(s) | Status |
|---|---|---|
| `CLAUDE.md:124` | `gangland-domain` (comment, `-am` build note) | historical — illustrative example of a sibling-dependency name, correct as written |
| `CLAUDE.md:195` | `gangland-ui/scoreboard-api` | historical — "Plaque ... left the repo in 0.10.0 WS1" |
| `CLAUDE.md:211` | `gangland-ui/inventory-api`, `gangland-infra/gangland-domain` | **fixed** — re-export line now says "item/core/sign", explains both the `inventory` and `domain` drops |
| `CLAUDE.md:213` | `gangland-infra/gangland-domain` | **fixed** — row now says "Deleted (WS5, 0.10.0)" with pointer |
| `CLAUDE.md:221-222` | `gangland-ui/lootchest-api` (removed) | **fixed** — replaced with real `gangland-gang`/`gangland-lootchest` rows |
| `README.md:147` | `gangland-ui/lootchest-api` | historical — "was the ... library, split 0.10.0" |
| `documentation/developer/modules.md:22,24,25` | TOC entries | historical — each says "deleted"/"became ... at the WS gate" |
| `documentation/developer/modules.md:244` | `InventoryHandler` | **fixed** (item 2 of the brief) |
| `documentation/developer/modules.md:1075,1173,1186,1274,1352,1407` | section headers/body | historical — each section is explicitly a "historical pointer" stub |
| `documentation/developer/README.md:65` | lootchest-api/hologram-api | historical — arrow notation (`lootchest-api -> gangland-features/gangland-lootchest`) |
| `documentation/developer/README.md:112` | `MultiInventoryCreation` | **fixed** — replaced with `SlotItemFactory` + deletion note |
| `documentation/developer/ui-framework.md` (16 hits) | `inventory-api`, `hologram-api`, `MultiPanelInventory`, `InventoryHandler`, `MultiInventory`, `FlowSession` | historical by design — this whole doc is the "what replaced X" developer reference; every hit says "was"/"deleted"/"replaces" |
| `documentation/features/bank.md:146` | `gangland-ui/shop-api` | historical — "moved out of the deleted ..." |
| `documentation/features/inventory.md:14,261` | `gangland-ui/inventory-api`, `MultiPanelInventory`/`FlowSession` | historical — my own rewrite (item 1), both say "is gone"/"deleted ... trio" |
| `documentation/features/traders.md:14` | `gangland-ui/shop-api` | historical — "moved out of the deleted ..." |
| `documentation/migration-0.10.0.md` (12 hits) | various | historical — the whole file is the 0.10.0 migration/changelog doc |
| `documentation/module-loader.md:89,95` | `gangland-domain`, `inventory-api` | historical — "is gone entirely as of WS5" / "itself ..." |
| `documentation/module-loader.md:216` | `gangland-domain` | **fixed** — `WantedKillTracker`'s real package/module corrected |
| `documentation/tests/features/inventory.md:11` | `gangland-ui/inventory-api` | historical — "not the deleted ..." |
| `documentation/tests/features/loot_chests.md:10-11` | lootchest-api/hologram-api | historical — "was ... before 0.10.0 WS3" |
| `documentation/tests/features/trader-shop.md:11,135` | `gangland-ui/shop-api` | historical — "deleted"/"moved out of the deleted ..." |
| `documentation/v0.7.4-DEV/CHANGELOG.{md,bbcode.txt}` | `InventoryHandler` | historical — dated changelog for a past release; `InventoryHandler` was live then |
| `documentation/v0.7.5-DEV/CHANGELOG.{md,bbcode.txt}` | `shop-api` | historical — dated changelog for a past release; `shop-api` was live then |

No hit was left unclassified.

## Docket ids touched

None — this is a docs-only pass with no bug fix. No docket candidates found; the staleness fixed here is
documentation drift from earlier gates' code moves, not a functional bug.

## Files touched

- `E:\Programming\java\wt\gangland-0.10.0-ws6\documentation\features\inventory.md`
- `E:\Programming\java\wt\gangland-0.10.0-ws6\documentation\developer\modules.md`
- `E:\Programming\java\wt\gangland-0.10.0-ws6\documentation\developer\architecture.md`
- `E:\Programming\java\wt\gangland-0.10.0-ws6\documentation\developer\README.md`
- `E:\Programming\java\wt\gangland-0.10.0-ws6\documentation\module-loader.md`
- `E:\Programming\java\wt\gangland-0.10.0-ws6\CLAUDE.md` (gitignored, not in the diff — see item 6 above)

## Concerns / open questions

- `documentation/developer/architecture.md`'s `GameplayConfig`/`CopsAndGadgetsConfig` bean-phase tables (lines
  ~104-109) still name deleted/moved beans (`WeaponManager`, `LootChestManager`, `ItemParser`) — out of this
  pass's named scope (needs re-verifying real `@Bean` method bodies, not a class-existence grep); flag for a
  follow-up gate or the docket.
- `documentation/developer/modules.md`'s `## gangland-compatibility` section (right after the hologram-api stub)
  still describes NMS version adapters as a live part of the plugin; the root `CLAUDE.md`'s own "Version
  Compatibility" section says Gangland ships no NMS as of 0.9.0. Not touched — outside the four named files/items
  and the sweep regex didn't catch it.
- `documentation/developer/modules.md`'s `item/` package table describes `ItemConverterRegistry.java`/
  `ItemParser.java` as if still present under `gangland-impl`'s `item/` package; both are gone entirely (promoted
  to Keystone's `keystone-item` in the 0.9.0 wave, predating this decoupling wave). Left a note in place rather
  than rewriting the section — the brief's item 4 named only the car/weapon rows within it.
