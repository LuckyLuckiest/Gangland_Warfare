# WS8 G2+G3 report — 2026-09-20

Status: DONE

## What changed

Both gates executed in the worktree (`E:\Programming\java\wt\gangland-0.9.2`, branch `0.9.2`) by one Sonnet
subagent each, sequentially (G3 depends on G2's classes, one Maven process at a time throughout), each given an
exact file list + fully-specified design (not just prose — concrete method bodies, since this state machine has
several interacting invariants) and verified independently by me afterward: read every touched/created file myself
and re-ran the build myself before proceeding to the next gate.

### G2 — launch/land/pull mechanics

1. `.../grapple/GrappleSession.java` (new) — session POJO: `player`/`grapple`/`anchor` (final), `elapsedTicks`/
   `currentSpeed` (mutable), mirrors `JetpackSession`'s Lombok shape.
2. `.../grapple/GrappleService.java` (new) — `BeanLifecycle`, `activeSessions`/`cooldownExpiryMs`/
   `landingGraceExpiryMs` maps keyed by `UUID`. `start(Player, Grapple, Location)` rejects if already active or on
   cooldown, else applies the cooldown **at launch** (not at pull-end — deliberately closes the "recast mid-pull"
   bypass) and creates the session. `cancel(Player)` is the single choke point every ending path routes through
   (arrival/timeout/chunk-unload/damage/sneak/teleport/world-change/death) and grants the one-shot
   `landingGraceExpiryMs` entry. `tickSession(GrappleSession)` — package-visible on purpose so tests call it
   directly without the scheduler — does timeout check, anchor-world/chunk-loaded check, capped+accelerated
   velocity toward the anchor, arrival auto-stop. A single shared `org.luckyraven.keystone.timer.RepeatingTimer`
   (not one task per session, unlike `JetpackTask`) ticks all active sessions once a server tick, created lazily in
   `onInitialize` guarded by a null-check so a bean reload never leaks a second timer.
3. `.../grapple/message/GrappleMessages.java` (new) — mirrors `JetpackMessages` exactly, reads the **same** shared
   `gadget_messages.yml` (constructor never calls `addFile`, only `checkFileLoaded`/`getFile`). Only `noPermission()`
   for now (G4 can add `gave`/`invalid` later).
4. `gadget/gadget_messages.yml` (edit) — `+Grapple_No_Permission`.
5. `config/GadgetFileConfig.java` (edit) — `+grappleMessages(FileManager, JetpackMessages)` bean; the unused
   `JetpackMessages` parameter is a deliberate bean-graph ordering edge (feedback_bean_ordering_via_params) so
   `gadget_messages.yml`'s `FileHandler` is guaranteed registered first.
6. `config/GadgetModuleConfig.java` (edit) — `+grappleService()` bean using the class's existing `plugin` field
   (mirrors `carService`/`jetpackService`'s convention).
7. `listener/grapple/GrappleLaunchListener.java` (new) — `PlayerFishEvent` handler. Held item is always looked up
   via `player.getInventory().getItemInMainHand()` — **never `event.getHand()`**, see the version-floor finding
   below. `IN_GROUND` → permission check (deny + message, mirrors `CarInteractListener.java:59`) → `start(...)`.
   `CAUGHT_FISH`/`CAUGHT_ENTITY` → `event.setCancelled(true)` (never actually "fish" with a grapple). Every other
   state is a no-op, vanilla hook physics plays out untouched.
8. `listener/grapple/GrappleAbortListener.java` (new, G2 scope: damage + sneak only) — `onDamage` is deliberately
   `MONITOR` priority + `ignoreCancelled = true` (see the ordering note under G3 below). `onToggleSneak` only reacts
   to the sneak-**start** transition.
9. Tests: `GrappleServiceTest` (7 cases: start/re-start rejection, cooldown rejection, timeout, chunk-unload,
   velocity ramp+cap via `ArgumentCaptor<Vector>`, arrival+grace grant, grace one-shot), `GrappleAbortListenerTest`
   (4 cases), `GrappleLaunchListenerTest` (4 cases, `PlayerFishEvent`/`FishHook` mocked directly — no live server
   needed).

### G3 — anti-abuse + corrected fall-damage design

1. `listener/grapple/GrappleLaunchListener.java` (edit) — added `isWithinWorldBorder(Location)`
   (`world.getWorldBorder().isInside(anchor)`) and `hasLineOfSight(Player, Location)`
   (`world.rayTraceBlocks(eye, direction, distance - 1.0)` — traced **1 block short** of the anchor so the anchor
   block itself, which is always a "hit" if traced the full distance, never counts as an occlusion). Both checks
   run in `handleLanded` after the permission check, before `start(...)`; both refuse silently (no message, matches
   the existing silent cooldown/already-active no-op — a message can be added later if wanted).
2. `listener/grapple/GrappleAbortListener.java` (edit) — `+onTeleport`/`+onChangeWorld`/`+onDeath`, all `MONITOR`
   priority, same shape as `onToggleSneak`. `PlayerDeathEvent.getEntity()` used (not `getPlayer()`, which that event
   doesn't have) — confirmed by inspecting the actual class, `getEntity()` covariantly returns `Player` there.
3. `listener/grapple/GrappleFallDamageListener.java` (new) — `HIGH` priority + `ignoreCancelled = true`. Two-path
   cancel: `isActive(player)` (legitimate — the player is being mechanically moved, not falling) or
   `consumeLandingGrace(player)` (the one-shot post-pull window). See the GD-04/GD-05 section below.
4. Tests: `GrappleFallDamageListenerTest` (new, 3 cases exactly matching the plan's §7 spec), `+3` cases in
   `GrappleAbortListenerTest` (teleport/world-change/death), `+2` cases in `GrappleLaunchListenerTest` (blocked LOS
   refused, outside-border refused) plus the existing happy-path test rewired with `World`/`WorldBorder`/eye-location
   stubs and deliberately set to `requireLineOfSight(true)` so it now exercises both new G3 checks (previously it
   exercised neither, since Lombok's `@Builder` defaults `boolean` fields to `false`, unlike the YAML's own
   `Require_Line_Of_Sight: true` default — flagging this because it's an easy trap to reintroduce later: any new
   `Grapple.builder()...build()` in a test that cares about the LOS/border gate must set it explicitly).

## Version-floor research (requested by the coordinator: "check which `PlayerFishEvent.State` values exist at the
1.16.5 API floor and which ones fire on a block landing per version")

Verified against archived Spigot/Bukkit javadoc (1.16.5, 1.18.2, 1.20.1) rather than relying on memory:

- **All 7 `PlayerFishEvent.State` constants — `FISHING`, `CAUGHT_FISH`, `CAUGHT_ENTITY`, `IN_GROUND`,
  `FAILED_ATTEMPT`, `REEL_IN`, `BITE` — exist unchanged from 1.16.5 through 1.21.11** (the version this reactor
  compiles against). `REEL_IN` was added ~April 2019 fixing SPIGOT-4700 (a report against `v1_13_R2`), well before
  the 1.16.5 floor. **No version branching is needed for the `State` enum itself** — I did not invent a fake
  branch where none is warranted.
- **`PlayerFishEvent.getHand()` is a different, separate finding: it does NOT exist at 1.16.5 or 1.18.2, and DOES
  exist at 1.20.1** — added somewhere in that window. This compiles fine (the reactor builds against the newest
  Spigot API jar, 1.21.11) but would throw `NoSuchMethodError` at runtime on a 1.16.5-1.18.2 server. Both
  subagents were briefed on this explicitly; `GrappleLaunchListener` never calls it — the held item is always
  resolved via `player.getInventory().getItemInMainHand()`, matching `CarInteractListener`'s own convention of
  ignoring off-hand entirely. This is the one concrete floor-compatibility trap this gate needed to avoid, and it
  is avoided.
- `World.rayTraceBlocks(Location, Vector, double)` and `WorldBorder.isInside(Location)` (used by G3's anti-abuse
  checks) are both confirmed present at 1.16.5 — no floor risk from the new LOS/border code either.
- Not smoked on a live server this batch — see "Smoke" below.

## GD-04 / GD-05 avoidance (docket: `brainstorming/bug-docket-2026-09-06/triage/gadgets-cars-fuel-jetpack.txt`,
lines 4-5)

**GD-04** ("An empty jetpack grants permanent fall immunity", P1, open): `JetpackFallDamageListener.onFallDamage`
cancels `DamageCause.FALL` whenever `jetpackService.isActive(player)` — and jetpack "active" has **no expiry of its
own**; deactivation is unequip-only, so an empty/zero-fuel jetpack left worn keeps `isActive()` true (and fall
immunity) indefinitely. `GrappleFallDamageListener` cannot reproduce this shape: `GrappleService.isActive(Player)`
is backed by `activeSessions`, a map entry that only exists between `start()` and `cancel()`, and `tickSession`
forces every session to end within `Max_Duration_Ticks` regardless of anything else, on top of six independent
cancel triggers (damage/sneak/chunk-unload/teleport/world-change/death) that can end it sooner. There is no path
that keeps `isActive` true past the hard timeout — structurally the opposite of GD-04's unbounded boolean. The
second half of the design, post-pull immunity, is deliberately **not** an extension of `isActive` at all: it's the
one-shot `consumeLandingGrace` flag, granted once per `cancel()` and destroyed the instant it's read (win or lose),
so it can absorb at most one fall-damage event ever, no matter how long the player keeps the item afterward.

**GD-05** ("A zero-fuel jetpack still glides and steers", P1, open, same root-cause family as GD-04): has no direct
analog — grapple has no continuous flight/glide/steer mechanic at all (WS8-D1, cooldown-only, no fuel model,
confirmed by `Grapple.java`'s field list: identity + mechanics fields only). The closest parallel — "can a player
pull for free indefinitely" — is prevented by `Max_Duration_Ticks` plus the cooldown applied at **launch** time
(before a pull can even be cancelled), which `GrappleServiceTest.start_onCooldown_rejected` (G2) already pins: a
pull cancelled immediately after launch still leaves the player on cooldown, so "recast mid-pull" cannot bypass it.

Neither GD-04 nor GD-05 is fixed by this gate (both stay open on the jetpack side, out of scope per WS7) — this
gate only ensures its own new code does not repeat either failure mode.

## Deviations from the plan

1. **`RepeatingTimer` constructor argument order.** My spec to the G2 subagent assumed `(plugin, period, delay,
   consumer)` (mirroring how I misread `CivilianService`'s call site). The subagent read the actual Keystone
   1.9.2 source (`Timer.java`'s javadoc explicitly labels param 2 "delay", param 3 "period") and corrected it to
   `new RepeatingTimer(plugin, 0L, 1L, timer -> tickAll())` (delay=0, period=1 tick). I independently re-verified
   this by reading `E:\Programming\java\Keystone\keystone-common\...\Timer.java`/`RepeatingTimer.java` myself —
   the subagent's correction is right, my spec was wrong. Worth flagging since this is a real (if minor) mistake
   on my part that a subagent caught rather than one it introduced.
2. **LOS/world-border refusal is silent** (no denial message), matching the existing silent cooldown/already-active
   no-op — not specified either way by the plan; noted as a possible follow-up if the orchestrator wants feedback
   on a blocked launch.
3. **Live-server "grapple-boot" smoke row: not run this batch, deferred to G-final.** The coordinator's message
   asked me to add and run one "if the test server is reachable" — `E:\Documents\Minecraft\Test Server\
   ServerStartDebug.bat` and the smoke harness (`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`) both
   exist, so it's reachable in principle. I chose not to launch a real server boot mid-batch: the binding plan's
   own gate table (§4) explicitly assigns "smoke, graph refresh, docs" to **G-final** (batch 3), not G2/G3, and a
   boot-only smoke row's main value (no crash on enable) is already strongly covered by two cheaper, already-green
   checks — `GadgetModuleConfigConstructorTest` (a reflective scan proving no `@Configuration` class in
   `org.luckyraven.gangland.gadget`, including the two G2/G3-edited ones, asks for a bean unavailable at
   configuration-instantiation time) and the full reactor `mvn clean install`. There is also nothing to smoke-*use*
   yet — G4 (the give command) hasn't landed, so there's no in-game way to obtain a grapple item. Marking this
   explicitly "not smoked" per the brief's own fallback instruction rather than silently skipping it or forcing a
   long-running server boot into this batch.
4. `GrappleLaunchListenerTest`'s class-level `@DisplayName`/javadoc still says "WS8 G2" even though it now also
   carries G3's LOS/border assertions — cosmetic only, not worth a rebuild cycle to fix; flagging for whoever next
   touches that file.

## Red-first evidence

G2 (new feature code, no pre-existing behavior): production code and tests were written together, so red was
proven by stashing the two config edits and moving the four new production classes out of the source tree, then
test-compiling — `GrappleServiceTest.java:[97,17] cannot find symbol / class GrappleService`,
`GrappleAbortListenerTest.java:[8,46] cannot find symbol / class GrappleService`,
`GrappleLaunchListenerTest.java:[18,46]`/`[20,54] cannot find symbol` — `BUILD FAILURE`. Restored and reran green:
`mvn test -pl gangland-features/gangland-gadget -am -Dtest=GrappleServiceTest,GrappleAbortListenerTest,
GrappleLaunchListenerTest -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 15, Failures: 0, Errors: 0,
Skipped: 0` / `BUILD SUCCESS`.

G3: each of the three edited/new production files was reverted to its pre-G3 state independently and
test-compiled/tested against the new tests. `GrappleLaunchListener` reverted → `Tests run: 6, Failures: 2` (the two
new LOS/border refusal tests failed, since the old code called `start()` unconditionally — the happy-path test
still passed, correctly, since it doesn't assert new-behavior). `GrappleAbortListener` reverted →
`cannot find symbol method onTeleport/onChangeWorld/onDeath` (compile failure — a stronger red than a runtime
assertion failure). `GrappleFallDamageListener` removed (renamed `.bak`) →
`cannot find symbol class GrappleFallDamageListener` (4 locations). All three restored, then green:
`mvn test -pl gangland-features/gangland-gadget -am -Dtest=GrappleFallDamageListenerTest,GrappleAbortListenerTest,
GrappleLaunchListenerTest -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 16, Failures: 0, Errors: 0,
Skipped: 0` / `BUILD SUCCESS`.

I independently re-read every production file both subagents wrote (not just their reports) and confirmed the
logic matches what's described above before accepting either gate.

## Build

Final command (run by me, independently, after both gates): `mvn clean install` from the worktree root. All 21
reactor modules `SUCCESS`, final `BUILD SUCCESS`, ~1:04 total.

`gangland-gadget` module test run (`mvn test -pl gangland-features/gangland-gadget -am`, run by me): **`Tests run:
107, Failures: 0, Errors: 0, Skipped: 0`** (92 pre-existing + 15 new from G2, +8 net-new from G3: 3 fall-damage +
3 abort-listener + 2 net-new launch-listener cases, since one existing launch-listener test was rewired in place
rather than duplicated).

## Docket ids touched

None fixed (new feature work). GD-04 and GD-05 consulted as design constraints per the coordinator's ask — both
stay open on the jetpack side, unaffected by this gate; see the avoidance write-up above.

Docket candidates: none noticed.

## Subagents used

1. Sonnet — G2 (grapple launch/land/pull mechanics: `GrappleSession`/`GrappleService`/`GrappleMessages`/
   `GrappleLaunchListener`/`GrappleAbortListener` + 3 test classes). Caught and correctly fixed a real mistake in
   my own spec (`RepeatingTimer` constructor argument order) by reading the actual Keystone source instead of
   trusting the prompt.
2. Sonnet — G3 (anti-abuse: LOS/world-border checks, teleport/world-change/death cancel,
   `GrappleFallDamageListener`'s corrected one-shot design + 3 test-class edits/additions), run only after G2 was
   fully verified green by me.

Both ran sequentially, one Maven process at a time; I verified each independently (read every file, reran the full
module suite and the full reactor build myself) before proceeding or writing this report.

## Concerns / open questions

1. Live-server "grapple-boot" smoke deferred to G-final (see Deviations #3) — flagging in case the orchestrator
   wants it pulled forward instead.
2. LOS/world-border refusal is silent, no player-facing message (Deviations #2) — a design choice, not a gap;
   easy to add later if wanted.
3. `GrappleLaunchListenerTest`'s stale "WS8 G2" class doc-comment (Deviations #4) — trivial, cosmetic.
4. `Grapple.isRequireLineOfSight()` was dead code from G1 through the start of this batch (the YAML field existed,
   nothing read it) — G3 is what actually wires it up; confirming here that it is no longer dead, in case anyone
   was tracking that as an open item.

## Fix round 1 — 2026-09-20 (Opus review, `exec/WS8/G2-G3-review.md`, verdict FIX: 1 Critical, 2 Important)

Done entirely by me directly (no subagent — small, well-specified surgical edits across files I'd already fully
reviewed in G2/G3, so writing them myself was faster and lower-risk than re-briefing a fresh agent). This session
was killed by the session limit partway through proving F1's red-first evidence (production fix already applied,
call site removed to prove red); on resume I reconciled the worktree (`git status` + read every touched file),
confirmed F2/F3/all minors had survived the kill intact and only F1's call site was still mid-revert, used that
exact state to capture F1's red evidence, restored it, then proved F2 and F3 red-first the same way (temporarily
reverting each production fix in isolation, running its test, restoring). No fix was left half-applied.

### F1 (Critical) — `Max_Distance` unenforced

`GrappleLaunchListener.handleLanded` now calls a new private `isWithinMaxDistance(Player, Grapple, Location)`
before the border/LOS checks: same-world guard, then `player.getLocation().distanceSquared(anchor) <=
maxDistance²` (squared compare, no `sqrt`). Refuses silently, same as the existing border/LOS refusals.

- Red command: `mvn test -pl gangland-features/gangland-gadget -am -Dtest=GrappleLaunchListenerTest -Dsurefire.failIfNoSpecifiedTests=false` (call site removed, method left orphaned)
- Red line: `Tests run: 8, Failures: 1` — `GrappleLaunchListenerTest.onPlayerFish_inGroundHasPermission_beyondMaxDistance_refusesLaunch` — `NeverWantedButInvoked: grappleService.start(...)` (the old code started the pull at any range)
- Green command: same command, call site restored → `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`
- New tests: refused at `Max_Distance + 1`, accepted at `Max_Distance - 1` (both explicit, not just the default-0 Lombok builder trap the earlier G3 tests already had to work around). Also had to add `maxDistance(25)` + a `player.getLocation()` stub to the three pre-existing `handleLanded`-reaching tests (happy path, blocked-LOS, outside-border), since the new distance check runs before all of them and would otherwise NPE/refuse on their unstubbed/zero-default state.

### F2 (Important) — `tickSession`'s chunk check was unreachable and force-generated chunks

`Location#getChunk()` loads (and generates, if needed) the chunk it returns, so the old
`!anchor.getChunk().isLoaded()` branch could never be true — worse, a far anchor would force-generate its chunk
every tick. Replaced with `World#isChunkLoaded(int chunkX, int chunkZ)` (a pure check, confirmed present at the
1.16.5 floor via javadoc), called as `anchorWorld.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)`.
`GrappleServiceTest`'s `loadedWorld()` helper and the chunk-unloaded test were restubbed onto
`World#isChunkLoaded(int,int)` directly (the old `Chunk`/`getChunkAt` stub is gone).

- Red command: `mvn test -pl gangland-features/gangland-gadget -am -Dtest=GrappleServiceTest -Dsurefire.failIfNoSpecifiedTests=false` (chunk check reverted to the old `getChunk().isLoaded()` expression, tests left on the new `isChunkLoaded` stubs)
- Red line: `Tests run: 8, Errors: 4` — `tickSession_timeout_endsSession`/`tickSession_chunkUnloaded_endsSession`/`tickSession_velocity_rampsThenCaps`/`tickSession_arrival_stopsAndGrantsGrace` all `NullPointerException: Cannot invoke "Chunk.isLoaded()" because the return value of "Location.getChunk()" is null` — proving the restubbed mocks (which no longer stub `getChunkAt`) genuinely break the old code path.
- Green command: same command, fix restored → `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`
- Plan correction: `brainstorming/gadget-wave-2026-09-16/plans/WS8-gadget-catalogue.md` gained a new `§0d. Correction` section (after §0c/Disputes, before §1 Scope) recording that `Location#getChunk().isLoaded()` loads the chunk and `World#isChunkLoaded(cx, cz)` is the actual gate.

### F3 (Important) — unbounded per-player cooldown/grace maps, no quit cleanup

`GrappleService.forget(Player)` (new) removes the active session, cooldown and landing-grace entries — all three
maps — for one player, WITHOUT granting a landing grace (the player is leaving, not landing). `GrappleAbortListener
.onQuit(PlayerQuitEvent)` (new, `MONITOR` priority) calls it, mirroring `CarQuitListener`/
`JetpackActivateListener#onQuit`'s own quit-cleanup precedent.

- Red command: `mvn test-compile -pl gangland-features/gangland-gadget -am -q` (both `forget()` and `onQuit()` removed together, since they're interdependent)
- Red line: `GrappleServiceTest.java:[230,24] cannot find symbol: method forget(Player)`; `GrappleAbortListenerTest.java:[142,50] cannot find symbol: method onQuit(PlayerQuitEvent)`; `[144,32] cannot find symbol: method forget(Player)` — compile failure, a stronger red than a runtime assertion.
- Green command: `mvn test -pl gangland-features/gangland-gadget -am -Dtest=GrappleServiceTest,GrappleAbortListenerTest -Dsurefire.failIfNoSpecifiedTests=false` after restoring both → green.
- New tests: `GrappleServiceTest.forget_quitMidPull_clearsEverySessionMap` (session/cooldown/grace all cleared, immediate re-launch succeeds) and `GrappleAbortListenerTest.onQuit_forgetsPlayer` (calls `forget`, never `cancel` — no landing grace on quit).

### Minors

- `Arrival_Distance` clamped to `Math.max(section.getDouble("Arrival_Distance", 1.5), 0.1)` in `GrappleAddon` —
  a 0 (or negative) value would make `tickSession`'s `Vector.normalize()` produce `NaN` velocity the instant the
  player reaches the anchor. New test `GrappleAddonLoadTest.zeroArrivalDistance_clampedToPositiveFloor`
  (`Arrival_Distance: 0` in YAML → loaded as `0.1`).
- Dropped the unused `@CustomLog` (and its now-unused import) from `GrappleService` — it never called `log.*`
  anywhere in the class.
- `gadget_messages.yml`'s header comment no longer says "Jetpack-specific" (it backs every gadget's messages,
  `Jetpack_*` and `Grapple_*` both) — reworded to "Shared gadget-module user-facing strings" with a line
  explaining the per-gadget key prefix convention.
- `GrappleLaunchListenerTest`'s stale "WS8 G2" class doc/`@DisplayName` now says "WS8 G2+G3" and describes the
  Max_Distance/border/LOS refusals it also covers.
- Added a pull-direction assertion to `GrappleServiceTest.tickSession_velocity_rampsThenCaps` — every captured
  velocity's normalized direction is asserted `(1, 0, 0)` (the anchor is due +X of the fixed mocked player
  location), not just its magnitude as before.

### Build

Final commands (worktree root): `mvn test -pl gangland-features/gangland-gadget -am` → **`Tests run: 112,
Failures: 0, Errors: 0, Skipped: 0`** (107 from G2+G3 + 5 new: Max_Distance ×2, `forget`/`onQuit` ×2,
`Arrival_Distance` clamp ×1). `mvn clean install` (full reactor) → all 21 modules `SUCCESS`, `BUILD SUCCESS`,
~1:06 total.

### Files touched (fix round 1, on top of G2/G3's set — no new files)

- EDIT `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/listener/grapple/GrappleLaunchListener.java` (F1)
- EDIT `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/grapple/GrappleService.java` (F2, F3, minor `@CustomLog`)
- EDIT `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/listener/grapple/GrappleAbortListener.java` (F3)
- EDIT `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/grapple/config/GrappleAddon.java` (minor, `Arrival_Distance` clamp)
- EDIT `gangland-features/gangland-gadget/src/main/resources/gadget/gadget_messages.yml` (minor, header)
- EDIT `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/grapple/GrappleServiceTest.java` (F2 restub, F3 test, pull-direction assertion)
- EDIT `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/listener/grapple/GrappleLaunchListenerTest.java` (F1 tests, stale-doc fix, stub additions to 3 existing tests)
- EDIT `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/listener/grapple/GrappleAbortListenerTest.java` (F3 test)
- EDIT `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/grapple/config/GrappleAddonLoadTest.java` (minor, clamp test)
- EDIT `brainstorming/gadget-wave-2026-09-16/plans/WS8-gadget-catalogue.md` (§0d correction, in the main checkout, not the worktree)

Nothing committed. `Grapple_Blocked` message (both the F1 range refusal and the existing border/LOS refusals) is
explicitly deferred to G4 per the coordinator's message, along with the W34 user-decision YAML comments (elytra
`FLY_INTO_WALL` un-graced; high sneak-cancel fatal after the grace).
