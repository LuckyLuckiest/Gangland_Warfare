# Review — WS8 G2 + G3 — 2026-09-20 (Opus, transcribed)
Verdict: FIX (1 Critical, 2 Important, minors)

## Spec table (condensed)
GrappleService cooldown/isActive/start/cancel/capped pull ✅ (cooldown keyed at launch — re-cast bypass closed) · launch only on a tagged rod ✅ · IN_GROUND → start; CAUGHT_FISH/CAUGHT_ENTITY cancelled; others no-op ✅ · **hook within Max_Range ❌** (F1) · sync `RepeatingTimer(plugin, 0L, 1L, …)` + `start(false)` ✅ · LOS ray trace ✅ (1 block short, documented) · world-border gate ✅ (`getWorld()` null-checked) · chunk gate ❌ (F2, plan defect too) · cancel on damage/sneak/timeout/teleport/world change/death ✅ (single `cancel` choke point) · one-shot landing grace, `isActive` bounded by `Max_Duration_Ticks` — not GD-04's shape ✅ (structural) · session cleanup ⚠ no quit handler (F3) · messages via gadget_messages.yml + FILE-phase `GrappleMessages` ✅ · red-first ✅ (launch listener real red; others compile-red) · floor ✅ (no getHand; rayTraceBlocks 1.13+, WorldBorder#isInside, setVelocity)

## Findings
**F1 Critical** — `Max_Distance` parsed (`GrappleAddon.java:113`, `Grapple.java:33`, `items/grapples.yml:45` "25") but read by nothing in production; `GrappleLaunchListener.handleLanded` (:60-75) gates on permission, border, LOS only → anchor range unbounded. Fix: same-world + `distanceSquared(anchor) > maxDistance²` → return; test at `maxDistance+1`.
**F2 Important** — `GrappleService.tickSession`: `!anchor.getChunk().isLoaded()` — `Location#getChunk()` loads/generates the chunk, so the branch is unreachable and a far anchor generates chunks every tick; the test stubs `getChunkAt` so it pins an impossible state. Fix: `world.isChunkLoaded(x >> 4, z >> 4)`; restub the test. Plan §4 G3 prescribed the broken expression — correct it.
**F3 Important** — `cooldownExpiryMs`/`landingGraceExpiryMs` unbounded (one entry per player ever, for the uptime); no quit handler. Fix: `forget(Player)` + `onQuit` in `GrappleAbortListener` (precedents `CarQuitListener.java:30`, `JetpackActivateListener.java:26`), also dropping the active session on the same tick.
**Minor** — unused `@CustomLog`; `gadget_messages.yml:1` header says "Jetpack-specific"; `onDamage` at MONITOR (observe-only contract; comment explains); `Arrival_Distance: 0` → `normalize()` NaN velocity (clamp ≥ 0.1 in the loader); stale "WS8 G2" class doc.

## Cannot verify
`IN_GROUND` firing per version (live); `WorldBorder#isInside` at 1.16.5; the chunk-load semantics (live probe); pull feel vs anti-cheat at `Max_Pull_Speed 1.8`; LOS on slanted geometry.

## Notes
- Entity hooks never pull. Wall/ceiling impact: no vanilla damage horizontally; an elytra `FLY_INTO_WALL` aborts the pull and is not graced — one user ruling. Sneak-cancel mid-air gets the grace but it expires after `Fall_Damage_Grace_Ticks` (40 ≈ 2 s) → a high sneak-cancel is fatal — reads intentional anti-abuse but is undocumented.
- Silent LOS/border refusal acceptable; recommend `Grapple_Blocked` message in G4.
- Missing tests: Max_Distance, quit, pull direction (only magnitude asserted).
- G-final live checklist: IN_GROUND on 1.16.5 + newest; vanilla rod untouched; beyond-range refused; far anchor cancels without generating chunks; no "moved too quickly" kick; grapple into ceiling/wall; sneak-cancel at 40+ blocks (expected fatal); 5 concurrent pulls; reload mid-pull (no second timer).

## Orchestrator rulings (W34)
Fix F1, F2 (+ the plan §4 text), F3, the `Arrival_Distance` clamp, the header/doc nits, the unused `@CustomLog`, and add the pull-direction assertion; `Grapple_Blocked` message goes into G4. User decisions recorded (not blocking): (a) elytra `FLY_INTO_WALL` during a pull stays un-graced; (b) a sneak-cancel high up is fatal after the 2 s grace — both documented in `items/grapples.yml` comments at G4 so owners know.
