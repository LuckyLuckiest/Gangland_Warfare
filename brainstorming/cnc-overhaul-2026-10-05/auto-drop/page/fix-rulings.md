# Fix rulings: verify findings on overhaul-updated.html (2026-10-07)

Every finding was checked again against `auto-drop/PLAN.md` (revision 2), `auto-drop/reviews/rulings.md` and the page
itself before it was ruled on. Edits go to `artifact/overhaul-updated.html` only. PLAN.md is an input, not an edit target
for this pass, so any change it needs is listed for the owner instead.

## Structure (verify-structure.md)

No findings. Nothing to rule on.

## Fidelity (verify-fidelity.md)

| # | Ruling | Reason / what changed |
|---|---|---|
| F1 api 2.2 clash | **Applied** | Confirmed: PLAN.md lines 3-4 say branch 0.15.1 (HEAD f475888d) already has `VERSION = "2.2"` and `Host_Api: 2.2`, while the Gang & Turf vt-scope, its deps chip and the api table still reserve 2.2 for 0.14.0. Added the 0.15.1 sentence to the 0.15.2 vt-scope and rel-why, and "0.15.1 already uses 2.2 (see 0.15.2); the owner must decide which number Gang & Turf takes." to the Gang & Turf vt-scope and the api table 2.2 row. No number picked: PLAN.md names none. The older decision lines (intro at 732, "Decided 2026-10-05" at 2783, cadence at 2800) were left as written: they record what was decided on 2026-10-05, and the two new notes flag the open question. |
| F2 T10 A1-A8 | **Applied on the page; PLAN.md part not applied** | Confirmed: ruling GE8 adds A9 and PLAN section 9 defines it, but PLAN T10 (line 551) still says A1-A8. The page's T10 row now says A1-A9. PLAN.md T10 is left for the owner (outside this pass's files). |
| F3 risk 11 missing | **Applied** | Confirmed: PLAN risk 11 and ruling GE11. The 0.15.2 rel-why carries the follow-up sentence. The 0.17 rel-why (`#r017`) says the follow-up must land before it ships, because this page first publishes a cheap crime there (Brandish_Near_Cop through Patrols, 0.17), and says PLAN.md names 0.16 instead and that the two disagree. Assault_Civilian has no publisher named on the page, so only Brandish_Near_Cop is tied to 0.17. |
| F4 "is filed" | **Applied** | Confirmed: PLAN D9 and T11 say T11 *will* file it. Now "T11 files ... as P3 id 179 in the bug docket (D9)". |
| F5 cop-killer target line | **Applied** | Confirmed: E4 drops 5 to 2 only because quiet = 205 >= 180. Now "who has gone 180 s without a new crime, loses them for good ...". |
| F6 reload fallback | **Applied** | Confirmed: PLAN 3.4 (fallback only while `needMs` is 0) and the `:150-159` row (`needMs` recomputed after each drop). Now "keeps today's timer until the next star falls". |
| F7 D8 "habit" | **Applied** | Confirmed: PLAN D8 alternative clamps `delta`; the 0.6-1.6 factor can never reach 0. Alternative now "clamp the escape habit (delta) at 0 from below". |
| F8 D4 numbering | **Applied** | Confirmed: on the page 0.17 has 2.4 and 0.20 has 2.5. Added "(0.17 and 0.20 then move up one number too)". |
| F9 tests summary | **Applied in part** | Confirmed that the list leaves out red tests. Subhead now "Tests (summary; the full red/pin list is PLAN.md section 9)"; added the missing `EvasionClockTest` items (seen resets, RETURNING respot, narrow 0.5, locked timing, raised-level recompute, teleport stops a clean break, cancelled drop, no arc/ledger reads in ONE_STAR/ALL_STARS), the `ChaseArcsTest`/listener items (`end` appends only with a crime, prune, admin chase, quit with no arc) and the `ChaseConfigTest` no-block pin. The rel-why now says PLAN.md is untracked and must be copied into a 0.15.2 worktree (git status shows `?? brainstorming/cnc-overhaul-2026-10-05/`). **Rejected:** copying in the section 4.2 validation table, the 4.3 message lines, the section 6 columns and the debug-line format. The page is a summary that points at PLAN.md; those tables would roughly double the roadmap entry, and the acceptance table already shows the debug fields A1-A9 read (`ending=`, `reason=`, `teleported=`, delta). |
| F10 Keystone/Bartizan line | **Applied** | Confirmed: the roadmap intro promises it, and PLAN sections 5 and 7 say everything is in cops-n-crooks. Added a "no Keystone or Bartizan change" chip to both the vt-deps and the rel-deps, with no version number. |

## Readability (verify-readability.md)

| # | Ruling | Reason / what changed |
|---|---|---|
| R1 escape habit range | **Applied, reworded** | The suggested "(always caught) ... (always escapes)" is wrong: PLAN 3.5 measures escapes against what is expected (`(actual - expected) / (n + Prior_Chases)`), and PLAN 3.1 says "0 = stranger". Now "from -1 to 1 (0 for a stranger; above 0 he gets away more often than expected, below 0 less often)". |
| R2 U+2212 minus | **Applied** | Confirmed: v4 has no U+2212 anywhere, and the page writes "-3 STARS" with a hyphen. Now "-1". |
| R3 YAML comment | **Applied, different fix** | The suggested single line would put Repeat_Chases and Repeat_Window_Minutes under Learning. PLAN 4.1 (lines 318-320) has them at the `Auto` level, between Momentum and Learning. Now a `# plus Repeat_Chases, Repeat_Window_Minutes` comment sits at the Auto level just above `Learning:`, and the Learning comment names only its own six keys, on two lines. Still 21 values + 8 named keys = 29. |
| R4 "the plan" | **Applied, more specific** | The suggested "implementation plan" is no clearer. Caption now "the full 29-key block is PLAN.md section 4.1, path in the 0.15.2 roadmap entry" with a link to `#r0152`, where the path is given. |
| R5 "clamped to 1 up to" | **Applied** | Now "clamped between 1 and your current stars". |

Totals: 15 findings. 13 applied in full or reworded. 2 applied in part (F2: PLAN.md not edited; F9: PLAN tables not copied). 0 rejected outright.
