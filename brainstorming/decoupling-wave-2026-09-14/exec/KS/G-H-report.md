# KS G-H (keystone-hologram) report — 2026-09-16

Status: DONE

## What changed (worktree-relative to `E:\Programming\java\wt\keystone-1.10.0`)

- `pom.xml`: `<revision>1.9.2</revision>` → `1.10.0`; new `<module>keystone-hologram</module>` (after
  `keystone-npc`, before `keystone-hooks`); new `dependencyManagement` entry for `keystone-hologram`.
- `keystone-hologram/pom.xml` — new module scaffold, mirrors `keystone-npc/pom.xml` minus `keystone-persistence`
  (plan §2a: hologram needs no persistence). Deps: `keystone-common`, `keystone-bean` (compile), `log4j-api`
  (compile), `mockito-core`/`keystone-testkit` (test).
- `keystone-hologram/src/main/java/org/luckyraven/keystone/hologram/`:
  - `Hologram.java` — ported verbatim (package rename `org.luckyraven.gangland.hologram` →
    `org.luckyraven.keystone.hologram` only); added the plan's `ponytail:` javadoc note on the class (ArmorStand-only,
    upgrade path `TextDisplay` 1.19.4+ behind an `NmsVersion` gate).
  - `HologramService.java` — ported verbatim + new `registerProtection(JavaPlugin)` method: calls
    `Bukkit.getPluginManager().registerEvents(new HologramProtectionListener(this), owner)` directly (no
    `ListenerService` scan indirection — ponytail per plan §3/C2).
  - `HologramProtectionListener.java` — ported, dropped `@ListenerHandler` + the `keystone.bean.listener` import;
    now a plain `implements Listener` POJO, constructed only from `HologramService.registerProtection`.
- `keystone-plugin/pom.xml` — new `keystone-hologram` dependency block (after `keystone-npc`), shaded into
  `Keystone-1.10.0.jar`.
- `docs/keystone-hologram.md` — new, mirrors `docs/keystone-npc.md`'s shape (usage, `registerProtection` rationale,
  version-floor note, class table).
- `keystone-hologram/src/test/java/org/luckyraven/keystone/hologram/`:
  - `HologramServiceTest.java` — new (0 tests existed before). 6 tests: create registers under both maps and spawns
    N armor stands; remove-by-id despawns + unregisters both maps; remove-by-location; `clear()` despawns every
    hologram and empties both maps; `onShutdown()` delegates to `clear()`; removing an unknown id is a no-op.
  - `HologramProtectionListenerTest.java` — new. 4 tests: manipulate/interact events on a hologram-owned stand are
    cancelled; both event types no-op on an unrelated stand / non-`ArmorStand` entity.

CLAUDE.md amendment and the phase doc are deferred to G-D (bundled with G-S per the brief's gate table) so the one
sentence can name both `keystone-hologram` and `keystone-shop` together — not done in this gate.

## Deviations from the plan

- None structural. Two test-authoring corrections made while writing (not production-code deviations):
  1. `HologramProtectionListenerTest` originally tried `mock(Hologram.class)` + `when(hologram.getLines())…` —
     this produced Mockito `UnfinishedStubbingException` (the mocked getter wasn't intercepted cleanly in this
     module's test setup). Switched to constructing a real `Hologram` spawned against a mocked `World`/`ArmorStand`
     instead — simpler and exercises real production wiring rather than a second layer of mocks.
  2. First draft of `HologramServiceTest.clear_...` read `a.getLines().get(0)` **after** calling `service.clear()`
     — but `Hologram.despawn()` (called by `clear()`) empties `lines` itself, so the list was already empty. Fixed
     by capturing the `ArmorStand` references before calling `clear()`. This is a test-authoring bug I introduced
     and fixed, not a pre-existing production bug — no docket entry.
- `docs/keystone-hologram.md` was not explicitly named in the BRIEF's G-H row but is in WS3 plan's G0 step 4; wrote
  it now since it's hologram-specific (distinct from the shared CLAUDE.md sentence, which the BRIEF explicitly
  defers to G-D).

## Red-first evidence

Not applicable in the "flip a failing production test" sense — per WS3 plan §7's own note, every new test here is
a **green-on-arrival wiring test** (0 prior tests existed on these 3 classes; nothing docket-pinned touches
hologram code). The two test-authoring bugs above (mock misuse, read-after-clear) were caught by running the new
tests before they were correct:
- Red: `mvn -pl keystone-hologram -am test` → `Tests run: 10, Failures: 0, Errors: 3` (2×
  `UnfinishedStubbingException` in `HologramProtectionListenerTest`, 1× `IndexOutOfBoundsException` in
  `HologramServiceTest.clear_despawnsEveryHologramAndEmptiesMaps`).
- Green (after the two test fixes above): `mvn -pl keystone-hologram -am test` → exit 0;
  `HologramServiceTest`: Tests run: 6, Failures: 0, Errors: 0; `HologramProtectionListenerTest`: Tests run: 4,
  Failures: 0, Errors: 0.

## Build

`mvn clean install` (full reactor, worktree root) → exit code 0. Aggregated across every module's
`target/surefire-reports/*.txt`: **Tests run: 1044, Failures: 0, Errors: 0, Skipped: 0.**

## Docket ids touched

None. Hologram docket ids (`UI-13`, `UI-14`, `UI-15`, `UI-33`) are Gangland-side (WS3's own G0/G5, not this
Keystone gate) — this gate only moves the 3 Keystone-owned classes verbatim; no bug fix, no behavior change.

Docket candidates (new bugs noticed): none — `Hologram.spawn()`'s existing `getWorld() == null` guard
(`Hologram.java`, ~line 44 pre-move) was re-confirmed present in the ported code, consistent with WS3 plan §13's
open question about LS-12; not independently re-triaged here (out of this gate's scope).

## Subagents used

None. The module is 3 source files (~350 LoC per the brief) plus 2 new test files — small enough to write and
verify directly, faster than subagent coordination overhead for this size (ponytail: no unneeded abstraction/
delegation for a task this size).

## Concerns / open questions

- WS3 plan §13 flags `LS-12`/`LS-25` as "may already be partially mitigated" but not exhaustively re-verified
  against every `CountdownTimer`/hologram call site — still true after this move; out of scope for a pure code-move
  gate.
- The CLAUDE.md `:101` layering sentence and `docs/phase-h10-hologram-shop.md` are intentionally **not** written
  yet — they land in G-D, together with G-S, so the one added sentence can name both new modules per the brief.
