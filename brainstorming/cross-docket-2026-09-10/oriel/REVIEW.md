# Oriel (OR) — review lead notes, 2026-09-10

## Repo state as found

`git branch --show-current` → **0.7.0**; `git rev-parse --short HEAD` → **fea7e01**;
last commit `2026-09-01 feat!: consume Keystone 1.7.0 for all infrastructure`.

The phase-1 map's header ("HEAD 0a7d38, branch master") is **wrong** — the earlier check was the correct one.
All findings in this folder were read against `0.7.0 @ fea7e01`. Flagged as an open question below.

Everything else in `systems.md` held up: I re-enumerated the tree with
`find . -path ./build -prune -o -name '*.java' -print | grep src/main` and the per-system counts match the
mapper's exactly (MC 52, CR 61, ED 50, CH 35, IV 42, DB 2, DP 29, DI 6 = 277 main files plus 2 archetype
templates excluded). The only correction is DB: the map names `DatabaseFaultSink` and `HistoryStore` as its
hubs, but the module actually holds `HistoryRow` and `HistoryService`; `DatabaseFaultSink` and
`PersistentCooldownService` live in Keystone's `keystone-persistence` and are only wired here.

## Facts established up front (given to every scanner, so nobody wasted a pass on them)

Verified by direct grep before ordering any scan:

- **Zero** `io.papermc` imports, **zero** `Bukkit.getLogger` calls.
- **Zero** async constructs (`runTaskAsynchronously`, `runTaskTimerAsynchronously`, `CompletableFuture`,
  `ExecutorService`, `new Thread`) in main source — the plugin is single-threaded on the main thread. This
  removes an entire P0 class (async-touching-Bukkit) from the docket and I told every scanner a
  "not thread-safe" finding needed a real second-thread caller or it was not P0.
- `plugin.yml` uses `version: ${version}` filled by Gradle `processResources`/`expand` — no hard-coded version.
- `player.getWorld()` on an online Player is never null, so the five chained `getWorld().dropItemNaturally(...)`
  sites are not NPEs.

## Scope decisions — one order per system

| System | Files | Model | Why |
|---|---|---|---|
| MC menu-core | 52 | sonnet | Registry/session lifecycle, click dispatch, animation timers — semantic reasoning, not pattern matching. |
| CR menu-config | 61 | sonnet | Largest system: YAML loaders, BetterGUI/DeluxeMenus importers, hot reload, and the `allow_elevated_actions` gate. |
| ED menu-editor | 50 | sonnet | Editor sessions, undo/revert, inventory click conflicts — highest risk of item duplication or loss. |
| CH menu-chest | 35 | sonnet | Chest menus, pagination arithmetic, `DepositSlotComponent` item custody. |
| IV menu-inventory | 42 | sonnet | Six fake-window types holding real items; cross-type comparison is the high-signal shape here. |
| DB menu-persistence | 2 (+4 tests) | sonnet | Small but it is the only persistence path; SQLite/MySQL divergence needed real reasoning. |
| DP menu-plugin | 29 | sonnet | Bootstrap phase ordering, reload lifecycle, per-subcommand permissions. |
| DI menu-integration | 6 | sonnet | Only 6 files but it owns every money and permission path — weighted above size. |
| conventions sweep | repo-wide | haiku | Mechanical grep checklist only, findings routed to the owning system. |

Concurrency held at ≤ 3 throughout: batch 1 MC/CH/ED, then CR, IV, DP, DI, DB and the conventions sweep were
fed in as slots freed. Every scanner was told explicitly not to spawn agents; none did.

### Orders given (one line each)

- **MC** — trace `MenuRegistry` register/open/unregister, `Menu.open/close`, `Component.render/onClick`,
  `RequirementHook`, `AnimationTicker` lifecycle, `NavigationHistory`; hunt uncancelled tickers, frame
  arithmetic, requirement mode/minimum logic, `DependencyKey` equality.
- **CR** — trace `MenuConfigService.initialize/reload`, every `*MenuLoader`, `ActionParser`, `RequirementParser`,
  `DepositActions`, both importers; **fully trace the `op`/`permission` elevated grant-and-revoke path** and
  cross-check the 32 shipped demo YAMLs against loader strings.
- **ED** — trace `EditorController` navigation and back-stack, `EditorSession`/`MenuEditor` save/revert/quit,
  the `SlotScreen` item-capture path, `MenuReloader`, the `SlotTarget` family; hunt item dup/loss and
  silently-discarded unsaved work.
- **CH** — trace builder→open→render, both listeners, pagination slot arithmetic, `DepositSlotComponent`
  custody across close/page-change/flow-switch, `MenuFlow`; hunt dupe/loss, cancellation gaps, missing drag handling.
- **IV** — trace all six types side by side; a guard five types have and the sixth lacks is the target shape;
  weight `TraderMenu` (real item exchange) and the furnace NMS/progress path highest.
- **DP** — trace `onEnable`→`bootstrap`→phases→listener/command scans, `onDisable`, `/menu reload`, and
  **verify each subcommand's permission check individually**; check network I/O on the main thread and config-key drift.
- **DI** — for each economy action check non-negative/finite validation, whether `transactionSuccess()` is
  checked, double-fire, and Vault-absent behaviour; check whether `PermissionActions` is covered by the
  `allow_elevated_actions` gate; verify provider freshness per call.
- **conventions (haiku)** — six mechanical checks (YAML key drift, unguarded enum `valueOf`, hard-coded versions,
  listener register/unregister pairing, three named CLAUDE.md design principles, swallowed exceptions), each
  finding tagged with an `OWNER:` system code.

## Results

| System | raw | kept | P0 | P1 | P2 | P3 |
|---|---|---|---|---|---|---|
| MC menu-core | 5 | 3 | 0 | 1 | 2 | 0 |
| CR menu-config (both passes) | 9 | 9 | 0 | 4 | 5 | 0 |
| ED menu-editor | 3 | 3 | 0 | 2 | 1 | 0 |
| CH menu-chest | 6 | 6 | 2 | 1 | 3 | 0 |
| IV menu-inventory | 7 | 6 | 1 | 1 | 3 | 1 |
| DB menu-persistence | 3 | 3 | 1 | 1 | 1 | 0 |
| DP menu-plugin | 5 | 4 | 0 | 1 | 2 | 1 |
| DI menu-integration | 3 | 3 | 2 | 0 | 1 | 0 |
| conventions | 0 | 0 | 0 | 0 | 0 | 0 |
| **total** | **41** | **37** | **6** | **11** | **18** | **2** |

Every kept finding was re-opened at its cited lines and the quoted code confirmed before it entered the docket.

## Culled findings, with reasons

1. **MC — "`ItemHoldingComponent` contract never says whether COMMAND/NAVIGATION closes return held items."**
   A javadoc/spec ambiguity with no demonstrated loss at the interface itself; by the rubric it is the
   speculative "if an implementer read this literally" shape. The *concrete* version of this question is real
   and is filed where the fix belongs, as OR-CH-01 and OR-CH-02.
2. **MC — "`AnimationTicker`'s `int globalTick` overflows after ~3.4 years."** Deterministic but with no
   realistic trigger: it needs 3.4 years of continuous uptime with no `/menu reload`. Minecraft servers restart
   far more often. Not a bug by the rubric.
3. **IV — "`WorkbenchMenu`'s live CRAFTING inventory lets vanilla recipe matching overwrite the result slot."**
   Clicks are cancel-by-default so the crafting matrix never changes through player action, and the admin's
   contents are written once at render; the scanner itself conceded no test can model it. No demonstrated
   trigger — culled, but see open question 6.
4. **DP — "`allow_elevated_actions` stays stale for one full `/menu reload` cycle" (filed as P0).**
   **False premise.** The scanner asserted `ConfigLoaderConfig` is a FILE-phase configuration whose menu
   re-parse therefore precedes the DATABASE-phase `plugin.reloadConfig()`. It is a bare `@Configuration`,
   which defaults to `Phase.CONFIG`; the phase order is FILE → DATABASE → CONFIG, so `reloadConfig()` runs
   *before* the menu re-parse and the gate is read fresh. I confirmed `OrielSettings` is the only other
   `reloadConfig()` caller and it runs once at construction, which does not change the conclusion.

## Tier changes I made against the scanners' calls

- **DB-01 MySQL migration: P2 → P0.** The scanner tiered it P2 under "backend-specific failures". But the
  outcome is not a failure, it is silent permanent data loss: the mis-quoted probe marks the table migrated,
  then Keystone's `AbstractJdbcBackend.applySchema` issues a correctly-quoted `DROP COLUMN` (verified at
  `AbstractJdbcBackend.java:132-136`) that succeeds and destroys the audit column. Data loss is P0.
- **DI-03 non-finite economy amounts: P0 → P2.** A menu author who can write `give_money: 1e999` can already
  write a very large finite number, so there is no privilege gain and no mint an author could not otherwise
  achieve. The genuine residue is balance corruption from a non-finite value, which is a latent trap.
- **IV-03 menu-tracking order: P1 → P2.** The scanner framed it as the "five types have it, one lacks it"
  shape. It is the reverse: four of six types (workbench, dispenser, hopper, trader) track *after* opening and
  only furnace and chest track before. The divergence is real and `FurnaceMenu`'s comment documents why the
  early order matters, but the majority follows the other convention, so the convention itself needs deciding.
- **DP-04 `DevPaginatedMenuConfig`: P2 → P3.** Real doc-vs-code contradiction, but the blast radius is a
  spurious warning on a dev-only demo menu the code already marks for pre-release removal.

## Things checked and found clean (worth recording so nobody re-scans them)

- **Elevated action grant/revoke is correctly implemented.** `ActionParser.runElevated` wraps both
  `player.setOp(true)` and `player.addAttachment(plugin)` in `try`/`finally` blocks that restore the prior op
  state and remove the attachment. This was my top P0 candidate going in; it is sound.
- **All seven `/menu` subcommands are permission-guarded.** Keystone's `Command.runExecute()` checks
  `sender.hasPermission(permission)` before any `onExecute` body runs, so Create/Debug/Download/Edit/Import/
  Open/Reload are uniformly covered by the framework. No per-subcommand permission hole.
- The conventions sweep returned zero findings and I independently re-ran its checks; the verdict holds. Note
  its CHECK 4 reported "no `registerEvents`/`HandlerList.unregisterAll` patterns found", which is simply wrong —
  there are seven call sites. Its *conclusion* (no leak) is still correct, because `EditorController` and
  `MenuFlow` both pair register with unregister and the other two register once at bootstrap. The haiku
  scanner reached a right answer through a failed grep; treat its negative results as weaker evidence than the
  sonnet scanners'.

## Second pass (CR)

Ordered by the orchestrator off open question 2. One sonnet scanner, scoped to the three areas the first CR
pass most likely under-covered: all 37 files of `config/loader/**`, the hot-reload path (`MenuConfigService`,
`ActionLibraryService`, `TemplateLibraryService`, `MenuCommandRegistrar`, `TemplateRegistry`), and both
importers plus the import plumbing — about 51 files. The four already-filed CR findings were listed in the
order as do-not-report, along with the two established non-bugs (`runElevated`'s correct `finally` revocation,
`MaterialLookup`'s deliberate XMaterial use), so the pass could not spend itself re-deriving them.

**Note on the first attempt:** the initial second-pass scanner was killed by a session limit before it wrote
anything, and `menu-config.2.raw.txt` did not exist on resume. I re-launched it with an identical order plus
one addition — write the findings file early and rewrite it as you go — so a repeat crash would not lose the
whole pass. The re-run completed.

| pass | raw | kept | P0 | P1 | P2 | P3 |
|---|---|---|---|---|---|---|
| CR second pass | 5 | 5 | 0 | 3 | 2 | 0 |
| **CR combined** | **9** | **9** | **0** | **4** | **5** | **0** |

Kept findings are appended to `findings/menu-config.txt` as numbers 5-9. Nothing was culled — all five
verified cleanly at their cited lines, and none duplicated the first pass.

Verification notes:

- **OR-CR-05 / OR-CR-06 are genuinely separate bugs, not one finding split.** 06 is the missing upper-bound in
  three pagination slot parsers; 05 is the deferred-validation mechanism that turns *any* builder-level range
  error into a silent reload. Different call sites, different fixes. I confirmed `registerMenu` really only
  calls `registry.register(name, factory)` and never `factory.get()`, and that `ChestMenuBuilder` does throw at
  lines 314-315 and 393/398 — so the validation exists, it just never runs at reload time.
- **The six-loader slot convention actually holds.** The scanner checked the shape I flagged as highest-signal
  and found ChestMenuLoader, HopperMenuLoader, DispenserMenuLoader, WorkbenchMenuLoader and FurnaceMenuLoader
  all route their `slots:` block through `SlotLoader.load`; Trader and Anvil have no slot grid. The gap is not
  between loaders but *inside* ChestMenuLoader, in the pagination button parsers. Recording this so nobody
  re-runs that comparison.
- **OR-CR-07 is a CR/ED cross-cut filed under CR.** The data loss happens when the in-game editor drops an item
  on a slot, but the defect is in `ItemDefinitionWriter.isNamespacedReference` (a `config/loader` class), which
  is where the fix goes. Related to but distinct from the ED findings.

Open question 2 is now answered: the low first-pass density was real under-coverage, not an absence of bugs.
The second pass found three more P1s in the same system, including one data-loss path.

## Open questions for the orchestrator

1. **Branch discrepancy.** The map says `master 0a7d38`; the working tree is `0.7.0 @ fea7e01`. If `master` has
   moved past `0.7.0`, some findings may already be fixed there and need re-verification before filing.
2. ~~**CR deserves a second pass.**~~ **ANSWERED** — see the "Second pass (CR)" section above. The orchestrator
   ordered it; the pass found 5 more findings (3 P1, 2 P2), all verified, none duplicating the first four. The
   low first-pass density was real under-coverage. CR now stands at 9 findings. Remaining residue: the second
   pass concentrated on loaders, hot reload and importers as ordered, so `config/action/**`,
   `config/component/**` and `config/requirement/**` have still only had the first pass over them.
3. **Keystone root causes for dedup.** Three findings have root causes partly or wholly in Keystone:
   DB-01 (`AbstractJdbcBackend.applySchema` drops unknown columns), DB-02 (`AuditLogService.logEvent` logs but
   never reports to Diagnostics), DI-03 (`NodeReader.asDouble().min(0)` cannot reject NaN/Infinity). Filed
   under the Oriel system that hits them, as instructed — hand to the Keystone lead for dedup.
4. **OR-CH-01 is test-pinned.** Two existing green tests (`DepositSlotComponentTest`,
   `DepositSlotMultiSlotStressTest`) assert today's wrong behaviour — that a USER close does *not* return the
   deposited item. They must be flipped as part of the fix, not treated as proof the behaviour is correct.
5. **OR-IV-03 needs an owner decision before a fix direction is chosen** — should all six inventory types track
   before rendering (following `FurnaceMenu`'s documented rationale) or after (following the current majority)?
6. **The `WorkbenchMenu` recipe-recompute question is unresolved, not disproven.** I culled it as
   unverifiable from source alone. Whether a Bukkit-created `InventoryType.WORKBENCH` recomputes its result
   slot when contents are set programmatically can only be settled on a live server. Worth one smoke check if
   anyone touches IV.
