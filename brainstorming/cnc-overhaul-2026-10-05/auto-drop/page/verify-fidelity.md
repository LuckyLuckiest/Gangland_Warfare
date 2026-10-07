# Fidelity check: AUTO Drop_Mode content in overhaul-updated.html

Scope: every line that differs between `artifact/overhaul-v4.html` and `artifact/overhaul-updated.html`, checked against
`auto-drop/PLAN.md` (revision 2) and `auto-drop/reviews/rulings.md`. Line numbers are from `overhaul-updated.html`.

Checked and correct (no finding): the five endings and their conditions, all 21 YAML values in the sketch and the 8
named-only keys (21 + 8 = 29), momentum numbers (0.75 / 0.5 / 20 s / Floor 0.4 / cap 1.6 / 1 s minimum), the E1, E2/E8
and E4 timings, learning rule facts (outcomes 1 / 0.5 / 0, decay 0.90, known face at 0.20 or 3 chases in 30 min, habit
0.6x-1.6x, contact time, half..double clamp, the never-learned list, 90-day prune), the star-card and bar texts and
colours, the Builds-on anchors, all 13 DAG rows (ids, dependencies, sizes, models, lanes), acceptance rows A1-A9 and the
release pass, D1-D10 recommended and alternative answers, graph edges (formula and endpoints), and the counts (55
features / 25 new, 68 nodes = 11 / 8 / 49).

## Findings

1. **major - the 0.15.1 prerequisite is never explained, and its api 2.2 clashes with Gang & Turf's 2.2.**
   Evidence: the new entries say `0.15.1 merged` and `gangland-api 2.2, no bump` (line 781, line 2577). PLAN.md lines 3-4
   say this is because branch 0.15.1 already has `GanglandApi.VERSION = "2.2"` and cops-n-crooks `Host_Api: 2.2`. The rest
   of the page still gives 2.2 to Gang & Turf as a planned bump: line 765 (`gangland-api 2.2`), line 764 ("Takes
   gangland-api 2.2 (Host_Api: 2.2), because 0.15 used 2.1"), line 839 (api table: 2.2 Planned, "Gang & Turf bump"),
   lines 732 and 2783. 0.15.1 has no entry anywhere on the page (v4 has 0 mentions). So a reader sees 0.15 at 2.1, then
   0.15.2 at "2.2, no bump", with 2.2 still reserved for 0.14.0. Two releases claim the same api number.
   Fix: in the 0.15.2 `vt-scope` (line 780) and `rel-why` (line 2584), add one sentence taken from PLAN.md's header:
   "Branch 0.15.1 (the settings-ownership merges, HEAD f475888d, not in master yet) already sets `GanglandApi.VERSION` to
   "2.2" and cops-n-crooks `Host_Api: 2.2`, so 0.15.2 adds no bump." Then add one line to the api table row at line 839
   and to the Gang & Turf `vt-scope` at line 764: "0.15.1 already uses 2.2 (see 0.15.2); the owner must decide which
   number Gang & Turf takes." Do not pick the number on the page: PLAN.md does not say.

2. **major - T10 limits the acceptance task to A1-A8, but the plan defines A9.**
   Evidence: the T10 row (line 2604) says "scenarios A1-A8 in `gen-cnc015.js`". Ruling GE8 in rulings.md says "Acceptance
   gains A8 'flee on foot' ... and A9 'teleport'". PLAN.md section 9 and the page's own acceptance table (A9, about line
   2633) and Done-when ("one 150-block teleport logs teleported=true") all need A9. PLAN.md T10 (line 551) was never
   updated after the ruling, and the page copied it. Someone working from the T10 row would skip A9.
   Fix: in the T10 row change "scenarios A1-A8" to "scenarios A1-A9". Fix PLAN.md T10 the same way, so the two stay in
   step.

3. **major - risk 11 (rampage counts crimes, not heat) is missing, but a later release needs it.**
   Evidence: ruling GE11 rejects the change for 0.15.2 and records it as "risk 11, a must-fix in the 0.16 plan before
   cheap crimes ship". PLAN.md risk 11 says: "before that ships, the rampage opening must count only heavy crimes or
   heat". The new content never mentions it, and neither the 0.16 entry (`#r016`, line 2637) nor any later one does. The
   page also disagrees with PLAN about when cheap crimes arrive. PLAN says 0.16 starts reporting `Brandish_Near_Cop` 25 and
   `Assault_Civilian` 30. On the page, `f-heat` says "nothing publishes them until later releases", and `Brandish_Near_Cop`
   is published by Patrols, which ships in 0.17 (line 1375).
   Fix: add one sentence to the new card's "Cold start and fallback" paragraph (line 1672) or to the 0.15.2 `rel-why`:
   "Follow-up (PLAN.md risk 11): the rampage opening counts crimes, not heat. Every crime 0.15 reports weighs 80 or
   more, so this holds today. Before a release starts publishing cheap crimes (Brandish_Near_Cop 25, Assault_Civilian
   30), the rampage opening must count only heavy crimes or heat." Put the same sentence in the `rel-why` of the first
   release that publishes a cheap crime. On this page that is 0.17 (`#r017`, Patrols), not the 0.16 that PLAN.md names.
   Say that the two disagree rather than choosing one.

4. **minor - the rel-why says the docket entry "is filed", but it is not filed yet.**
   Evidence: line 2584: "the inherited progress carry-over on a mid-search raise is filed as P3 id 179 (D9)". PLAN.md D9
   and T11 say T11 *will* file it ("T11 files it as P3 id 179"), and D9 is still open.
   Fix: "T11 files the inherited progress carry-over on a mid-search raise as P3 id 179 in the bug docket (D9)".

5. **minor - the target line reads as if a cop killer always gets a clean break, which contradicts the card.**
   Evidence: line 1727: "A five-star cop killer who loses them for good and runs out of the zone drops 5 to 2 in one go".
   The card's play line says "Kill a cop ... they stay on you one star at a time", and the Still-hot rule locks a cop
   killer. In PLAN.md E4 the clean break happens only because `quiet` = 205 >= 180, so the lock has lifted.
   Fix: "A five-star cop killer who has gone 180 s without a new crime, loses them for good and runs out of the zone
   drops 5 to 2 in one go ...".

6. **minor - the reload fallback is stated too broadly.**
   Evidence: line 1672: "A reload that switches to AUTO mid-search keeps today's timer for that search". PLAN.md 3.4 and
   the `:150-159` row say the fallback applies only while `needMs` is 0. `needMs` is recomputed after the next drop, so
   AUTO timing returns at the next star, not at the end of the search.
   Fix: "... keeps today's timer until the next star falls, so nothing drops at once."

7. **minor - D8 uses "habit" for two different things.**
   Evidence: line 2821. The recommended answer says "the habit alone is bounded at 0.6×", which is the timer factor
   `habit`. The alternative says "clamp the habit at 0 from below". PLAN.md D8 means `delta`, which the card calls the
   "escape habit, from −1 to 1". Clamping the 0.6-1.6 factor at 0 does nothing.
   Fix: alternative text "Tighten only: clamp the escape habit (delta) at 0 from below."

8. **minor - D4's alternative does not follow the page's api numbering.**
   Evidence: line 2817: "Add it now as api 2.3 and move 0.16 to 2.4". That is PLAN.md word for word. But on this page 2.4
   belongs to 0.17 and 2.5 to 0.20 (lines 797, 821, 2658, 2721, 2783), so taking this option shifts every later release.
   Fix: add to the `.alt` span: "(0.17 and 0.20 then move up one number too)".

9. **minor - the Tests list is a summary but does not say so, and the full detail lives only in an untracked file.**
   Evidence: lines 2610-2616 leave out several PLAN.md section 9 red tests. Missing from `EvasionClockTest`: "SEEN resets
   steps, outside time and teleported but not the arc", "a squad that went RETURNING and comes back counts one respot",
   "narrow uses 0.5 when not locked", "locked keeps ONE_STAR timing", "level raised mid-search recomputes needMs",
   "teleported(player) stops a CLEAN_BREAK", "cancelled drop leaves steps alone", "no arc or ledger reads in
   ONE_STAR/ALL_STARS". Missing from `ChaseArcsTest`/`ChaseArcListenerTest`: "`end` appends to `recent` only with a
   crime", "prune", "admin chase leaves no trace in `recent`", "quit with no arc does nothing". Missing from
   `ChaseConfigTest`: the pin "a file without the block gives DEFAULT and an empty report". The page also leaves out the
   section 4.2 validation table, the section 4.3 message lines, the section 6 table columns and the debug-line format
   that A1-A9 parse (`ending=`, `reason=`, `teleported=`, `delta=`). The only pointer is
   `brainstorming/cnc-overhaul-2026-10-05/auto-drop/PLAN.md`, and that folder is untracked (`??` in git status), so it is
   missing from any worktree.
   Fix: change the subhead to "Tests (summary; the full red/pin list is PLAN.md section 9)" and add the missing items
   above to their bullets. In the `rel-why`, add "(untracked, in the main checkout only; copy it into the 0.15.2 worktree
   before starting)".

10. **minor - the 0.15.2 entries skip the Keystone/Bartizan line that the roadmap intro promises.**
    Evidence: the edited intro (line 2555) still says "Every release ... names the Keystone, Bartizan and API versions it
    needs". The 0.15.2 deps (lines 781, 2577) name only "0.15.1 merged" and the api. PLAN.md section 5 says "All in
    cops-n-crooks", and section 7 says that only the cops-n-crooks module version moves.
    Fix: add a third deps chip, "no Keystone or Bartizan change", to both the `vt-deps` and the `rel-deps`. Do not write a
    version number: PLAN.md names none.

Counts: blocker 0, major 3, minor 7.
