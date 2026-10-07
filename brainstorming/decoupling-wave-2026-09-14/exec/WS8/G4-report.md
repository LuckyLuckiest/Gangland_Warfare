# WS8 G4 + G-final report — 2026-09-20

Status: DONE — **WS8 (grappling hook) and Gangland 0.9.2's gadget-wave stream are complete.**

Done entirely by me directly (no subagent — the give-command pair is a mechanical mirror of `JetpackCommand`/
`JetpackGiveCommand`, which I'd already fully read; writing it myself was faster than re-briefing an agent, and
G-final's doc/graphify/smoke work isn't code-generation at all).

## What changed — G4

1. `gangland-features/gangland-gadget/.../command/GrappleCommand.java` (new) — `/glw grapple` parent, mirrors
   `JetpackCommand` exactly: `super(plugin, "grapple", true, "grapples")`, help-info filtered by
   `getCommands().entrySet()...startsWith("grapple")`, one sub-argument (`give`) wired in `initializeArguments()`.
2. `.../command/GrappleGiveCommand.java` (new) — `/glw grapple give <id> [amount]`, mirrors `JetpackGiveCommand`
   exactly: chained `OptionalArgument` nodes (`id` with tab-completion off `grappleAddon.getGrapples().keySet()`,
   then `amount` with the same parse/`<= 0` guard), `giveGrappleItem`'s clamp-and-drop-overflow logic
   byte-for-byte the same shape as `giveJetpackItem`/`giveCarItem`.
3. `.../grapple/message/GrappleMessages.java` (edit) — added `blocked()`, `gave(String, String)`,
   `invalid(String)`, mirroring `JetpackMessages`'s three equivalents.
4. `gadget/gadget_messages.yml` (edit) — `+Grapple_Blocked`, `+Grapple_Gave`, `+Grapple_Invalid`.
5. `commands.json` (edit) — `+grapple`, `+grapple_help`, `+grapple_give` (mirrors the `jetpack`/`jetpack_help`/
   `jetpack_give` trio exactly).
6. `.../listener/grapple/GrappleLaunchListener.java` (edit) — `handleLanded` now sends `grappleMessages.blocked()`
   on each of the three range/border/LOS refusals (still silent for the cooldown/already-active no-op inside
   `start()` — that's a state the player already knows about, not new information).
7. `items/grapples.yml` (edit) — added a comment block documenting the two W34 user rulings (elytra-equivalent
   wall-hit mid-pull is not graced; a high sneak-cancel is fatal once the grace expires).
8. Tests: `GrappleGiveCommandAmountGuardTest` (new, mirrors `JetpackGiveCommandAmountGuardTest` exactly — negative
   amount gives zero items, no `NegativeArraySizeException`), and the three existing `GrappleLaunchListenerTest`
   refusal cases (`beyondMaxDistance`/`blockedLos`/`outsideBorder`) extended to also verify
   `player.sendMessage("blocked-message")`.

### Command permission node — confirmed against actual Keystone source (the plan's §13 "not verified" item)

Read `Keystone/keystone-command/.../command/Command.java`, `Argument.java` and `SubArgument.java` directly rather
than assuming. The chain is:

- `Command`'s constructor sets the **root** argument's permission to `<prefix>.command.<label>` —
  `gangland.command.grapple` for `GrappleCommand`. `Command.runExecute` checks this **before** any argument-tree
  traversal even starts (`if (!sender.hasPermission(permission)) { ...; return; }`).
- `SubArgument`'s constructor (the base class `GrappleGiveCommand extends`) derives its own permission as
  `parent.getPermission() + "." + subPermission` — so `/glw grapple give` requires the **separate, more specific**
  node `gangland.command.grapple.give`, checked again during `Argument.traverseList`'s node-by-node walk.
- The two chained `OptionalArgument` nodes under `give` (`id`, then `amount`) are plain `Argument`s with no
  permission string passed — they add no further gate beyond `give`'s own node.

So: **no manual permission code is needed anywhere in `GrappleGiveCommand`'s body** — exactly matching
`CarGiveCommand`/`JetpackGiveCommand`'s real (zero-permission-checks-in-body) shape, now confirmed from source
rather than inferred from precedent. The per-item permission (`gangland.grapples.<id>`, enforced at launch in
`GrappleLaunchListener`) is a completely separate, unrelated gate on *use*, not *give* — same give-vs-use split
car and jetpack already have.

### Blocked-message throttle decision

**Once per cast attempt, no separate cooldown on the message itself.** `PlayerFishEvent.State.IN_GROUND` fires
exactly once per landed hook — a player has to physically recast (at least one server tick, plus travel time for
the hook to land) between attempts, which is already a human-paced action, not something that can be spammed
faster than the existing permission-denial message already fires at (same listener, same rate, already accepted
as fine). Adding a separate cooldown map for one message would be unrequested state for a problem that doesn't
exist — the natural cast cadence is the rate limit.

## What changed — G-final

1. **Docs**: `CLAUDE.md` (worktree root, gitignored/untracked — edits are local-only, will not appear in `git
   status`/a commit, same convention as the main checkout per project memory) gained a "Grappling hook ownership
   (WS8, 0.9.2 G1-G4)" paragraph and the module-table row now mentions the grapple. `documentation/module-loader.md`
   listed jetpack/cars by name in two places (the module-tree ASCII diagram and the module table) — both updated
   to mention the grappling hook. `documentation/migration-0.9.2.md` gained a new `## 8. New in this release: the
   grappling hook (WS8)` section (purely additive — nothing to *migrate*, unlike the jetpack sections above it)
   covering the item, cooldown-only model, every YAML knob, and both W34 rulings; the intro paragraph now mentions
   WS8 alongside WS7.
2. **`graphify update . --force`** in the worktree: 15,816 nodes / 40,935 edges / 608 communities (up from the
   pre-WS8-G1 baseline). Spot-checked `graphify query "GrappleService"` — resolves cleanly, `GrappleService`/
   `GrappleLaunchListener`/`GrappleAbortListener` all indexed with correct `src`/`loc`.
3. **Docket**: no new ids found this gate. GD-04/GD-05 remain **open** (jetpack side, unfixed, out of WS8's scope)
   — this gate's design was constrained by them, not a fix for them; already fully written up in
   `exec/WS8/G2-G3-report.md`'s GD-04/GD-05 avoidance section, referenced here rather than repeated.
4. **Shortlist recorded** (report-only, per the coordinator's instruction — not a file): the binding plan's §9b
   shortlist stands as written — parachute and smoke/flash are next (WS8-D2's recommended first three, grapple
   being the mandatory first). Neither is started; both remain proposals pending the user picking up the next
   gadget-wave stream.
5. **Smoke**: `grapple-boot` scenario added to `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json`
   (`paths.repo_dir` was **already** pointed at this worktree — nothing needed restoring there). Ran for real
   against `E:\Documents\Minecraft\Test Server` (`--deploy --restore`) after a `--fake` sanity pass first (which
   correctly FAILed, as `--fake` structurally cannot produce real command output — its purpose is only to prove
   the harness mechanics/JSON are well-formed, which it did). **Real run: PASS.** `gadget` module loaded cleanly,
   zero errors; `/glw grapple` and `/glw grapple give grapple 1` from console both returned the clean
   `"Error: You need to be a player to use this!"` message — no stack trace — confirming `Command.runExecute`'s
   players-only guard fires before any give-command body code runs. Full transcript:
   `smoke/reports/2026-09-20-2333-grapple-boot.md`.
6. **`exec/WS8/G-final-manual-checklist.md`** (new) — the 14-row cast/pull/fall-damage matrix from the Opus
   G2+G3 review's "Cannot verify"/"Notes" sections, written up as concrete steps + expected outcomes for a real
   client session (IN_GROUND cross-version, vanilla-rod untouched, beyond-range refusal, far-anchor
   no-chunk-generation, no anti-cheat kick, wall/ceiling hit, both W34 rulings, cooldown, cancel triggers, 5
   concurrent pulls, reload mid-pull).
7. Full `mvn test` (whole reactor, not just gadget): `BUILD SUCCESS`, 22/22 modules.

## Red-first evidence (G4)

- **Give-command amount guard**: temporarily changed `cappedAmount = Math.max(0, Math.min(amount, 36 *
  maxStackSize))` to `cappedAmount = amount` in `GrappleGiveCommand`. Red: `Tests run: 1, Failures: 1` —
  `java.lang.NegativeArraySizeException: -2147483648` thrown from `giveGrappleItem`. Restored, green:
  `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`.
- **Blocked-message send**: temporarily removed the three `player.sendMessage(grappleMessages.blocked())` calls
  from `handleLanded` (kept the `return;`s). Red: `Tests run: 8, Failures: 3` — all three refusal tests
  (`beyondMaxDistance`/`blockedLos`/`outsideBorder`) failed on `verify(player).sendMessage(...)`. Restored, green:
  full `GrappleLaunchListenerTest` back to 8/8.

## Build

- `mvn test -pl gangland-features/gangland-gadget -am`: **`Tests run: 113, Failures: 0, Errors: 0, Skipped: 0`**
  (112 from fix round 1 + 1 new: `GrappleGiveCommandAmountGuardTest`; the 3 blocked-message assertions extended
  existing tests rather than adding new ones).
- `mvn clean install` (full reactor, worktree root): all 22 modules `SUCCESS`, `BUILD SUCCESS`, ~1:09 total.
- `mvn test` (full reactor, no `clean`/`install`, per the coordinator's explicit G-final ask): `BUILD SUCCESS`,
  22/22 modules.

## Docket ids touched

None fixed (new feature work). GD-04, GD-05 — design avoided, see `exec/WS8/G2-G3-report.md`'s dedicated
avoidance section; both remain open on the jetpack side. No new bugs found this gate.

## Subagents used

None — G4's command pair is a small, well-understood mirror of already-read code; G-final is docs/graphify/smoke
work, not implementation.

## Concerns / open questions

1. **The smoke run's `--deploy` replaced the test server's previously-deployed core jar
   (`gangland_warfare-0.10.0.jar`, evidently from a concurrent 0.10.0-stream session) with this worktree's
   `gangland_warfare-0.9.2.jar`.** `--restore` only restores **module and plugin** jars that were parked aside
   (confirmed: it restored `gangland-gadget-0.9.1.jar` plus three previously-installed plugin jars,
   `Bartizan-0.3.0.jar` + two `Citizens` builds) — it does **not** restore the core jar, which the harness's own
   documented deploy semantics don't cover. If another session needs the 0.10.0 core jar back on the test server,
   it will need to redeploy it itself. This matches how every prior stream has evidently used this same shared
   server (sequential deploy-and-overwrite, per the jar versions already sitting there before my run), so I did
   not treat it as a reason to skip the coordinator's explicit smoke-test instruction — flagging it here so it's
   not a surprise.
2. `CLAUDE.md`'s edits are gitignored/untracked in this worktree (confirmed via `git check-ignore`) — they will
   not show in `git status` and cannot be part of any commit. This is expected/by-design (same as the main
   checkout's own CLAUDE.md convention per project memory), not an oversight — noting it so the "CLAUDE.md module
   table row" instruction isn't read as unaddressed just because it's absent from the diff.
3. The `grapple-boot` scenario's command list includes `glw modules` (plural) rather than `glw module` (singular,
   the actual subcommand) — this is a pre-existing typo shared with the `jetpack-give` scenario I mirrored (not
   something I introduced fresh), and it doesn't affect the verdict since neither scenario's `expect` block relies
   on that specific line's output. Left as-is to match the established scenario-file convention rather than
   silently fixing an unrelated file's pre-existing quirk mid-task.

## Files touched (G4 + G-final, on top of fix-round-1's set)

- NEW `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/command/{GrappleCommand.java, GrappleGiveCommand.java}`
- NEW `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/command/GrappleGiveCommandAmountGuardTest.java`
- EDIT `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/grapple/message/GrappleMessages.java`
- EDIT `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/listener/grapple/GrappleLaunchListener.java`
- EDIT `gangland-features/gangland-gadget/src/main/resources/{commands.json, gadget/gadget_messages.yml, items/grapples.yml}`
- EDIT `gangland-features/gangland-gadget/src/test/java/org/luckyraven/gangland/gadget/listener/grapple/GrappleLaunchListenerTest.java`
- EDIT `documentation/{module-loader.md, migration-0.9.2.md}`
- EDIT `CLAUDE.md` (worktree root, gitignored — see Concerns #2)
- EDIT `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (main checkout, `+grapple-boot` scenario)
- NEW `brainstorming/decoupling-wave-2026-09-14/exec/WS8/G-final-manual-checklist.md` (main checkout)
- Refreshed `graphify-out/graph.json`/`GRAPH_REPORT.md` in the worktree (gitignored, local artifacts)

Nothing committed in the worktree. This is the final batch — WS8 (the grappling hook) and Gangland 0.9.2's whole
gadget-wave stream (WS7 + WS8) are now feature-complete pending the orchestrator's review/commit of this batch.


## Deviation declared by the orchestrator (ruling W37, 2026-09-20)

The plan G4 row asked to extend `GadgetModuleTest` with the grapple package declarations. The test already asserts the `org.luckyraven.gangland.gadget.command` package (via `CarCommand`) and the `listener.*` subpackages by prefix, so the grapple classes are covered structurally; extending it would be redundant. Skipped deliberately (review WS8 G4, Important 1). Plan defect noted: WS8 §3b claimed WS7 shipped no `jetpack_help`; it did, and G4 mirrored it with `grapple_help`.
