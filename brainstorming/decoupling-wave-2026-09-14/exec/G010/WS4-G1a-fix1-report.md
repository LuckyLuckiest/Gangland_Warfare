# WS4 G1a fix round 1 report — F2 (settings migration silent regression)

Review verdict: FIX (2 Important) — `brainstorming/decoupling-wave-2026-09-14/exec/G010/WS4-G1a-review.md`. Only
**F2** is this batch's; **F1** (the lost T-R4 pin, `keystone-shop`'s `DefaultShopDisplayResolverTest`) is a
Keystone-side fix landing upstream via a separate agent on `wt/keystone-1.11.0` — not touched here. Worktree:
`E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, uncommitted on top of 164d549e + the G1a work. No
commits made.

## F2 — targeted migration warning + docs

**Finding**: an upgrading server's customised `Trader:`/`Banker:` values in `settings.yml` are silently ignored
after G1a's settings split (only the generic, unhelpful `unknown key 'Trader'` line appeared in the smoke log),
and `documentation/migration-0.10.0.md` had no WS4 section at all.

### File:line changes

- **`gangland-api/src/main/java/org/luckyraven/gangland/file/configuration/Settings.java`**
  - Line ~383-389: new private static helper `warnIfLegacyShopBlockPresent(boolean present, String legacyKey,
    String movedTo)` — fires one `log.warn(...)` naming the new module-owned file
    (`plugins/Gangland_Warfare/npc/trader_settings.yml` / `npc/banker_settings.yml`) and pointing at
    `documentation/migration-0.10.0.md`, stating customised values are not auto-migrated.
  - Line ~748-749 (inside `init()`, right after the `Shop:` block read): two call sites —
    `warnIfLegacyShopBlockPresent(section(root, "Trader", report) != null, "Trader", "npc/trader_settings.yml")`
    and the `Banker` equivalent. Reuses the file's own existing `section()` helper to check presence — `section()`
    marks the block "touched" as a side effect, which suppresses the *generic* unknown-key sweep for the `Trader`/
    `Banker` top-level key itself (the individual leaf keys underneath, e.g. `Trader.Respawn_Cooldown`, still get
    their own generic "unknown key" lines, since those are never individually read — confirmed in the test run
    below). Net effect: one clear, targeted line per legacy block instead of one useless generic one. Fires once
    per `init()` call, i.e. once at boot and once per `/glw reload`, matching the ruling.

- **`gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/SettingsTest.java`**
  - 3 new `@Test` methods (after the existing 5): `initialize_legacyTraderBlock_logsTargetedMigrationWarning`,
    `initialize_legacyBankerBlock_logsTargetedMigrationWarning`,
    `initialize_noLegacyShopBlock_logsNoMigrationWarning`.
  - New private helper `captureWarnLogs(Runnable action)` at the bottom of the class: attaches a throwaway Log4j2
    `AbstractAppender` directly to the exact `org.apache.logging.log4j.core.Logger` instance `Settings`'s own
    `@CustomLog` field resolves to (same factory call, `org.luckyraven.keystone.logging.Logger.getLogger`, same
    name — Log4j2 hands back the same singleton per name). Detaches afterward in a `finally`.

- **`documentation/migration-0.10.0.md`**: new `## WS4 — Trader/Banker NPC settings moved out of core settings.yml`
  section, inserted between the existing WS1 section (untouched) and `## See also`. Covers: the 10-key table (old
  key → new file → new key), `Trader.Max_Mode_Multiplier` → `Shop.Max_Mode_Multiplier` (stayed core, moved
  sideways not out), the no-auto-migration warning (copy customised values by hand), and the exact warning text to
  watch for as the signal that migration is needed. The tail comment listing which gates still need their own
  section was updated to drop WS4 from the "later gates" list.

### A debugging note worth recording (not a code change, just why the test needed the extra `Configurator.setLevel` line)

No `log4j2.xml`/`log4j2.properties` exists anywhere in this reactor, so Log4j2 falls back to its
`DefaultConfiguration` (root level `ERROR`). A plain `addAppender()` on the target logger silently never received
the `WARN`-level event — the level filter rejects it before any appender sees it, confirmed by first adding a
temporary `System.err.println` inside the helper (removed before finalizing) that showed the method *was* being
called with `present=true` and the correct logger name (`[Gangland.Settings]`) while the test's captured list
stayed empty. Fix: `Configurator.setLevel(coreLogger, Level.WARN)` before running the action, restored to the
original level in `finally`. No production code involved — purely a test-harness detail.

## Red-first verification

Per LEAD-RULES.md's testing convention, verified the 2 positive-case tests were genuinely red against the
pre-fix code before taking them green: temporarily commented out the two `warnIfLegacyShopBlockPresent(...)` call
sites (kept the helper method itself, to get a runtime failure rather than a compile error), rebuilt
`gangland-api`, ran `SettingsTest`:

```
Tests run: 8, Failures: 2, Errors: 0, Skipped: 0
  initialize_legacyTraderBlock_logsTargetedMigrationWarning: expected ... got: []
  initialize_legacyBankerBlock_logsTargetedMigrationWarning: expected ... got: []
```

Exactly the 2 positive-case tests failed (empty-capture, not an error — confirming the capture harness itself
works correctly); the negative-case test (`initialize_noLegacyShopBlock_...`) passed both before and after, as
expected for a true negative control. Restored the two call sites, rebuilt, reran:

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

All green, including a look at the raw log output confirming both the targeted line and the still-present
per-leaf-key generic "unknown key" lines coexist as intended:

```
WARN [Gangland.Settings] - settings.yml still has a legacy 'Trader:' block — those keys moved to
plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT
auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
WARN [Gangland.Settings] - settings.yml:3:3 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
```

## Build

Targeted builds first (`gangland-api -am`, `gangland-impl -am`), both clean, then the full reactor:

**`mvn clean install` (whole reactor, exit code 0) → BUILD SUCCESS.**
**Tests run: 818, Failures: 0, Errors: 0, Skipped: 0** (152 surefire-report files — same file count as G1a since
the 3 new tests landed inside the existing `SettingsTest` class, not a new file). **818 = 815 (G1a) + 3 new
tests**, exactly the expected delta.

## C9 shading audit (re-run)

`unzip -l target/gangland_warfare-0.10.0.jar | grep keystone/shop` → **0 matches**, still clean after the rebuild.

## Smoke

No new smoke row per the coordinator's instruction ("No new smoke row needed") — this fix is a logging/docs
change only, already exercised implicitly by every existing `mvn test` run of `SettingsTest`.

## Concerns / left out

- F1 (T-R4 pin) is explicitly out of scope for this batch — being fixed upstream in Keystone by a separate agent.
- The generic "unknown key" sweep still fires per-leaf-key under a legacy `Trader:`/`Banker:` block (e.g.
  `Trader.Respawn_Cooldown`) even after the targeted block-level warning suppresses the block-level generic line.
  This is intentional/harmless noise, not touched — the leaf-level lines don't name a destination file the way
  the new targeted line does, so they add no useful information but also don't actively mislead; flagging in case
  the coordinator wants that noise fully silenced in a later pass (out of F2's stated scope, which only asked for
  the block-level targeted check).
- Docs sweep (plan step 16 — worktree CLAUDE.md module table, `documentation/module-loader.md`, FRONT-PAGE.md +
  .bbcode.txt, `tests/features/trader-shop.md`, `features/traders.md`, `features/bank.md`) is explicitly folded
  into G1b per the orchestrator ruling, not touched here.
- Docket updates (NS migration-gap row, KS-SH pin row, CT-06/07 carry-forward, T-R4 note) are the clerk's per the
  ruling's "Docket batch 4 after both land" — not done by me.

## Deliverables

- `exec/G010/WS4-G1a-fix1-report.md` — this file.
- `exec/G010/WS4-G1a-fix1-package.diff` — cumulative with G1a (`git add -N . && git diff HEAD`, then `git reset`;
  worktree left uncommitted and clean of intent-to-add). 4767 lines.
