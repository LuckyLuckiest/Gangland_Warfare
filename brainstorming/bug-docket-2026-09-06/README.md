# Bug docket — 2026-09-06 (branch 0.8.2, HEAD 66eeb647, Keystone 1.8.0)

Published page: https://claude.ai/code/artifact/4102fb1f-20b0-44e9-b1b7-2893a559fa04

Every observation from `../workflow-audit-2026-09-02/*.md` (461 rows) plus 139 `T-` bugs found by test suites and
later reviews and 22 post-audit rows (TF-40..49, GR-38, CM-37 from the gang-turf planning wave, then GR-39..42, CM-38, US-43, LS-35, LS-36, CJ-43, WB-43 from its Gangs+ parity pass; all 2026-09-30), triaged into
fix tiers with a fix direction and the test that pins each one. 622 entries in all.

| Tier | Meaning | Count |
|---|---|---|
| P0 | Fix now: money/item creation or loss, data loss/corruption, permission holes, crashes on normal play paths, thread-safety faults | 50 |
| P1 | Fix next: wrong-but-bounded behaviour in normal play, stuck states, uptime leaks, silent failures hidden from admins | 106 |
| P2 | Scheduled: edge-case crashes, backend-specific failures, latent traps, dead features shipped as working, missing UX pieces | 213 |
| P3 | Cleanup: dead code/config, help + i18n drift, convention violations, cosmetics | 250 |
| X | Withdrawn or by design (weapons #17, core-lifecycle #19, turf #26) | 3 |

Ids are `<system code>-<audit observation number>`: CL core-lifecycle, CM commands-messages-platform,
IT items-unique, UI ui-inventory-scoreboard, US users-levels-economy-bank, GR gangs-ranks-mail,
WB wanted-bounty-combat, CJ cops-detainment-jail, CT civilians-traders-shops, TF turf, WP weapons,
GD gadgets-cars-fuel-jetpack, LS lootchests-signs-waypoints, T = found by the test suite.

Files: `triage/<slug>.txt` (hand-written: `num~~tier~~title~~fix~~tests`; a row with no audit observation is dropped
with a `triage without observation` warning unless it carries 8 fields, `...~~location~~observation~~confidence`,
which makes it a post-audit entry with id `<code>-<num>`), `observations.json` (parsed audit
tables + board risk), `bugs.json` (merged), `parse_obs.py` + `build_docket.py` (rebuild), `docket_template.html`
(page with `/*__DATA__*/` placeholder), `gangland-bug-docket.html` (the published page).

Rebuild: `python parse_obs.py && python build_docket.py`, then republish the HTML to the same artifact URL.
Status/notes on the page are stored in the artifact's shared db (collection `bugs`, doc id = bug id).
