# 0.16.1 — playtest fixes (owner report 2026-10-08)

Status: planning only, nothing implemented. Base = master 00fb5aa5 (0.16.0 live on the Test Server, server stopped).
Locations below came from graphify orientation plus targeted greps; `path:line` is where to start, not a verified root cause.

## 1. Search continues after the stars drop, with no feedback

Owner's words: "So when they keep searching for me, even after the wanted stars dropped why I don't have feedback."

Interpretation: the stars reach 0 and the player is no longer wanted, but cops keep hunting and searching, and the player sees nothing about it.

Likely files:
- `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/wanted/evasion/EvasionClock.java:261` (`stars.drop`), `:266` (last-star early return, the comment at `:41` says "The last star ends the chase"), `:270` (state set to EVADED), `:231`/`:235` (`startSearch`), `:281` (`startSearch` body), `:360` (`fireCountdown`).
- Code's own comment says the last star ends the chase, but the EVADED/SEARCHING path keeps the cops on him. That contradiction is the likely bug.

Proposed fix direction (smallest change):
- Option (a) cops stand down once stars hit 0. The search ends with the stars. Clear the track and the chase arcs on the last-star path, the same way `WantedEndEvent` already clears him.
- Option (b) keep the search and tell the player: action bar "Cops are still searching the area" while searching, and "Search called off" when it ends. Action bar goes through `ActionBarManager` (see feedback memory), not `ChatUtil`.
- Recommendation: (a). The code already says the last star ends the chase, so the search continuing is the defect. Add (b) only if the owner wants a searching state to be visible.

Test to add: in `EvasionClockTest`, drop the last star, then tick; assert the state is not SEARCHING and no search countdown fires. Verify it is red on 00fb5aa5 first.

**Owner question 1:** When stars hit 0, should cops (a) stop searching at once, or (b) keep searching but tell the player "Cops are still searching the area" / "Search called off"? Recommended: (a).

## 2. Command formatting

Owner's words: "Commands don't have proper formatting similar to the others."

Likely files:
- `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/setup/SetupMessages.java:20-27` — strings hold their own `&c`/`&a` codes and the `USAGE` line has no prefix.
- `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/setup/SetupCommands.java:78`, `:91`, `:143`, `:148` — `sender.sendMessage(messages.format(...))`.
- Older comparison: `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/waypoint/TeleportCommand.java:123-151` uses `Messages` and `GanglandChatUtil.commandMessage`/`GanglandChatUtil.color`.
- `gangland-features/cops-n-crooks/src/main/resources/commands.json:46-58` — the `cop_*` help entries; check whether the new `setup` subcommands have entries.

Proposed fix direction: route the setup messages through the same `GanglandChatUtil` prefix/colour path the older commands use. Add `commands.json` help entries for every `/glw cop setup` subcommand (`wand|mode|save|list|remove|tp|link`). Keep the strings in the module's own YAML or the existing `SetupMessages` enum, not `settings.yml`.

Test to add: a `SetupCommands` test asserting the usage and the `commands.json` entries exist for each setup subcommand.

Owner question: none (a style fix). If the owner has a preferred prefix, use it.

## 3. Turf quartermaster does not attack cops

Owner's words: "The turf quartermaster doesn't attack the cops when they are attacking the players."

Interpretation: a Quartermaster or garrison defender stands by while cops attack a protected player.

Likely files (not verified by grep, start here):
- `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/...` — the Quartermaster and garrison defender NPCs. Targeting is a seam: per `CLAUDE.md`, `NpcMarkManager` / `CombatEligibility` / target-filter seams live in `gangland-civilians` and turf injects them. Grep for `CombatEligibility` in `gangland-features/gangland-turf` and `gangland-features/gangland-civilians`.
- `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/police/handoff/HandoffController.java` is the cop side (no defender logic there).

Proposed fix direction: when a cop NPC damages a player the defender protects, add that cop to the defender's target filter. Smallest form: one predicate at the seam, not a per-caller check.

Test to add: a defender-targeting test asserting a cop that hits a protected player becomes a valid target, and that a non-cop hostile still follows the existing rule.

**Owner question 3:** Should defenders protect (a) only their own gang's members on their own turf, or (b) any player on the turf? Recommended: (a), matching the existing turf protection rules.

## 4. Radio names: colours and rank-only format

Owner's words: "The names shown in the radio needs to be properly formatted so that colors can be shown in the names, and a way so that the rank and name can be shown only without saying Marksman and the number for example."

Likely files:
- `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/police/radio/CopRadio.java:237-262` — the radio name is built as plain text: `tierName` calls `ChatColor.stripColor(GanglandChatUtil.color(...))` (`:262`), and `:242-247` replace the role symbol with the role word ("Officer Medic Bob #1592").
- `CopRadio.java:480-486` — `%role%` comes from `Lines.Role_<key>` or the raw role key.
- `CopRadioMessages.java:62-67` — the radio line templates (`Marksman_Spotted` etc.) that use `%target%`/`%role%`.

Proposed fix direction:
- Keep the colour codes: translate `&` codes on the name through `GanglandChatUtil.color` and stop stripping them for display. The plain name stays for places that need it.
- Add a configurable name format in the module's own `copsncrooks/cops.yml` (NOT `settings.yml`) with placeholders `{rank}`, `{name}`, `{role}`, `{number}`, for example `"&7{rank} &f{name}"`. Default to today's output so nothing changes until the owner edits it.
- The radio then drops `{role}`/`{number}` when the owner's format omits them.

Test to add: a `CopRadio` name test asserting colour codes survive and the `cops.yml` format renders "rank + name" with no role or number.

**Owner question 4:** What exact format do you want (for example "&e{rank} &f{name}")? Should `{role}` and `{number}` be hidden everywhere or only in the radio?

## Resume checklist (tomorrow)

1. Start the Test Server if needed (it is stopped). Use `E:\Documents\Minecraft\Test Server\ServerStartDebug.bat` as in the smoke harness.
2. Get owner answers to Owner questions 1, 3 and 4. Issue 2 needs none.
3. Implement as one small lane: Sonnet executor, Opus review. Use the test-first order above, and confirm each new test is red on 00fb5aa5.
4. Run `mvn -q clean install` and the cops-n-crooks tests.
5. Docket: record the 4 rows (T-187..T-190) with commit, branch and covering test, via the docket rebuild.
6. Deploy to the Test Server and playtest the four items.
