# Edit log: AUTO Drop_Mode added to the overhaul page (2026-10-07)

Page: `artifact/overhaul-updated.html` (published as version 4 of artifact U6qF8Ey5). Backup before editing:
`artifact/overhaul-v4.html` (byte-identical copy, checked with `cmp`). Source of every fact: `auto-drop/PLAN.md`
revision 2 and `auto-drop/reviews/rulings.md`. Not republished; no source code or git touched.

## Changes

| # | Section | What | Why |
|---|---|---|---|
| 1 | `<style>` (after `.two li p .alt`) | 7 rules: `.two h3 + ul + h3` (gap before the second decisions heading), `.rel-v.pt` (2rem, so "0.15.2" fits the 132 px roadmap column), `.rel-dag` (full-width row in the roadmap grid) + its `.subhead`, `table`, `.backlog` sizes, `.bars .t.wrap` (lets a star-card line wrap; `.bars .t` is `nowrap`) | The new roadmap entry carries two tables and a test list that do not fit the narrow middle column; star-card lines are longer than boss-bar titles |
| 2 | Masthead `facts-line` | 54 -> **55** features, 24 -> **25** new, "6 releases, 0.15 to 0.20" -> "..., plus 0.15.2" | One new `New`-tagged feature card (55 `<article>`, 25 `tag-new` spans) |
| 3 | Versions (`#versions` vtrack) | New `vt vt-planned vt-cnc` entry 0.15.2 "Cops N Crooks · Smart star drops", chip Planned, deps "0.15.1 merged", "gangland-api 2.2, no bump", linked to `#r0152` | The version line lists every release, 0.13.x included; 0.15.2 sits between 0.15 and 0.16 |
| 4 | Graph legend (`#required`) | To build (48) -> **(49)** | New node is `s-open` |
| 5 | Graph `<svg>` aria-label | 67 -> **68** features and foundations | Node total |
| 6 | Graph, 0.15 column | New node `n-auto-drop` (`gn k-feature s-open is-new`, title "Smart star drops (Drop_Mode AUTO) · To build · 0.15.2") at y=433, the first free row above the strip | Placed in the 0.15 column like every other 0.15.x item; it has prerequisites, so it goes above "No prerequisites" |
| 7 | Graph, 0.15 column | "No prerequisites" strip moved 445 -> 484 (label 456 -> 495); `e-bot-acceptance-scenarios` 465 -> 504, `n-cnc-regroup` 504 -> 543 (rect, dot, text) | Make room for the new row; same 51/71 px strip spacing as the 0.16 column. Neither moved node has an edge, so no path changed |
| 8 | Graph edges | 3 new `ge ge-in` paths: `f-evasion` -> `n-auto-drop`, `f-heat` -> `n-auto-drop`, `f-hud` -> `n-auto-drop` (same curve formula as the existing in-column edges) | PLAN.md: AUTO reads the evasion clock and `HeatLedger.chaseCrimes`, and T8 changes `StarCard`/`WantedHud`/`WantedHudListener`. The page's arrows run prerequisite -> needer, so the HUD edge points *into* AUTO (the shipped HUD is extended, it does not need AUTO) |
| 9 | Graph figcaption | "Everything in the 0.15 column shipped in 0.15.0 ..." now adds "except Smart star drops (Drop_Mode AUTO), planned as the 0.15.2 point release ..." | The caption would otherwise be false |
| 10 | Stage 3 "The chase", after `f-hud` | New card `<article class="feature" id="n-auto-drop">` with `tag-new`, h3 "Smart star drops (Drop_Mode AUTO)", Effort L, Ships in `#r0152` 0.15.2, To build. Player paragraph; mechanics: the five endings as a list (when, how many stars), momentum (0.75 / 0.5 narrow / none when locked, floor 0.4, cap 1.6, min 1 s), learning (two tables, outcomes 1 / 0.5 / 0, decay 0.90, known face at 0.20 or 3 crime chases in 30 min, habit 0.6x-1.6x, contact-time typical length clamped half..double, everything it never learns from, AUTO-only, 90-day prune), cold start, ONE_STAR fallback on a planner error, reload safety, opt-in with ONE_STAR default; a note that it is not the 0.16 Cold trail card; three-figure `.bars` sketch (Evaded_Many bar "★★☆☆☆ -3 STARS", small-fry card, still-hot card); trimmed block-YAML sketch of `Wanted.Evasion.Auto` (21 of 29 keys, the other 8 named in a comment, "full 29-key block is in the plan"); a `.target` line (E1, E4); Builds on; Needs first evasion · heat ledger · HUD | Task item 1. Id `n-auto-drop`, not `f-auto-drop`: on this page `n-` marks cards new in a revision (all 24 earlier `tag-new` cards use it). `class="feature"` (not `headline`): every `n-` card is a plain feature with the New tag. No "Inspired by" chips: PLAN.md names no game, and inventing one would break the no-invented-facts rule. Effort L: PLAN.md sizes it as 13 tasks (1 L, 7 M, 5 S), two new tables and ten new classes, above every M card on the page. Needs first includes the HUD so the card matches its incoming graph edges, as every other card does |
| 11 | Roadmap intro | "6 releases ... each playable on its own" + ", plus one patch release, 0.15.2, between 0.15 and 0.16" | The list now has seven entries |
| 12 | Roadmap, new `<li id="r0152">` between `r015` and `r016` | rel-v "0.15.2" (`pt`), name "Smart star drops", chip Planned, deps "0.15.1 merged" / "gangland-api 2.2, no bump"; goal; item `n-auto-drop` (`is-new`); "Foundations: none new" + the three features it extends; rel-why (own branch from 0.15.1, D1 merge order, cops-n-crooks only, VERSION and Host_Api stay 2.2, two new tables, no docket entry fixed, D9 -> P3 id 179, hidden-feature merge up to T7, path to PLAN.md); Done when (A1-A9 + regression + release pass + build); full-width `.rel-dag` block: the 13-task DAG table (Id, Task, Files, Depends on, Size, Model), lane note, test list (planner, learner, arcs/listener, clock/listener/config/HUD/SPI), acceptance table A1-A9 + Regression | Task item 3. **Chose a separate roadmap entry**, not a fold into the 0.15 line: the 0.15 entry is marked Shipped and lists only shipped items, and the page already treats a point release as its own row in the version line (0.13.x). A separate entry keeps 0.15's verdict intact and gives 0.15.2 its own status, "Done when" and anchor |
| 13 | Guardrails, "Decisions for you" column | New `<h3 id="d-auto-drop">Open for 0.15.2: smart star drops</h3>` + `<ul>`: a lead item saying none is decided yet and that the answer shown is the recommended one, then D1-D10, each `<b>Dn Title.</b>` + recommended answer + `<span class="alt">` alternative | Task item 4, in the page's decision format (recommendation as the body text, alternative in `.alt`). A separate heading, because the existing first item says the owner "took every recommended answer below" on 2026-10-05; putting D1-D10 under it would read as already decided |
| 14 | TOC / nav | No change | No new `<section>`; the new anchors (`#r0152`, `#n-auto-drop`, `#d-auto-drop`) live inside existing sections, which the survey says is the only case that needs no TOC edit. The TOC observer only tracks section ids |

## Counts after the edit

- Graph: **68 nodes = 11 built / 8 part built / 49 to build** (was 67 = 11 / 8 / 48); 90 edges (was 87).
- Status chips: Built 12, Part built 9, To build 41 (+1, the card), Shipped 11, Planned 5 (+2: version line and roadmap), This plan 9.
- Features 55 (`<article>`), 25 with the New tag; foundations 13.
- Tag deltas vs v4: article +1, div +22, ul +5, li +25, p +29, b +53, span +94, code +137, table +2, tr +25.

## Structural self-check (Python `html.parser`, scratchpad `check.py`)

Both files: 0 nesting errors, nothing unclosed, no duplicate ids, no `href="#..."` without a target, every graph node
has a card or foundation with its id, every edge endpoint is a node. v4: 67 nodes / 87 edges; updated: 68 / 90.

## Discrepancies noticed (not changed; for the owner)

- PLAN.md says 0.15.1 already declares `GanglandApi.VERSION = "2.2"` and `Host_Api: 2.2`, while the page's version
  tables still give 2.2 to Gang & Turf (0.14.0). The new entries say "2.2, no bump" as PLAN.md does; the Gang & Turf
  rows were left alone.
- PLAN.md T10 says "scenarios A1-A8", but section 9 defines A1-A9. The DAG row copies T10 as written; the acceptance
  table lists all nine.
- The shipped evasion and HUD cards still say `npc/wanted.yml`; PLAN.md says the file is `copsncrooks/wanted.yml`
  since e57da275. The new card uses `copsncrooks/wanted.yml`; the shipped cards were not edited.
- The 0.16 card "Cold trail" (`n-cold-trail`, a clock speed-up) shares a name with AUTO's COLD_TRAIL ending. The new
  card says they are different.

## Fix pass after verification (2026-10-07)

Rulings: `auto-drop/page/fix-rulings.md`. All edits in `artifact/overhaul-updated.html`; still not republished.

| # | Section | What |
|---|---|---|
| 15 | Versions, Gang &amp; Turf vt-scope; api table 2.2 row | + "0.15.1 already uses 2.2 (see 0.15.2); the owner must decide which number Gang &amp; Turf takes." (F1) |
| 16 | Versions, 0.15.2 vt-scope + vt-deps | + the 0.15.1 sentence (branch 0.15.1, HEAD f475888d, not in master, already VERSION "2.2" / Host_Api 2.2, so no bump) (F1); + chip "no Keystone or Bartizan change" (F10) |
| 17 | Roadmap 0.15.2 rel-deps | + chip "no Keystone or Bartizan change" (F10) |
| 18 | Roadmap 0.15.2 rel-why | + the 0.15.1 sentence (F1); "is filed as P3 id 179" -> "T11 files ... as P3 id 179 in the bug docket" (F4); + risk 11 follow-up sentence (F3); PLAN.md path + "untracked ... copy it in before starting" (F9) |
| 19 | Roadmap 0.17 rel-why | + risk 11 must land before 0.17 ships (Brandish_Near_Cop via Patrols is the page's first cheap crime); PLAN.md names 0.16; the two disagree, owner picks (F3) |
| 20 | Roadmap 0.15.2 DAG, T10 | A1-A8 -> A1-A9 (F2). PLAN.md T10 still says A1-A8: owner to fix there |
| 21 | Roadmap 0.15.2 Tests | subhead -> "Tests (summary; the full red/pin list is PLAN.md section 9)"; + 8 EvasionClockTest items, 4 arcs/listener items, the ChaseConfigTest no-block pin (F9) |
| 22 | Card `n-auto-drop` | "clamped to 1 up to" -> "clamped between 1 and" (R5); escape habit "-1 to 1 (0 for a stranger; above 0 ... below 0 ...)", hyphen-minus (R1, R2); reload fallback "until the next star falls" (F6); target line adds "who has gone 180 s without a new crime" (F5) |
| 23 | Card YAML sketch | `# plus Repeat_Chases, Repeat_Window_Minutes` moved to the Auto level above `Learning:`; Learning comment names its six keys on two lines (R3); caption points at PLAN.md section 4.1 and links `#r0152` (R4) |
| 24 | Decisions D4, D8 | D4 alt + "(0.17 and 0.20 then move up one number too)" (F8); D8 alt "clamp the escape habit (delta) at 0 from below" (F7) |

Check: `python -I check_page.py` -> `NEW problems: 0` (68 nodes = 11 built / 8 part built / 49 to build, 90 edges, 55
articles, 25 New, 91 ids; 360 px and 1000 px probes have no horizontal scroll). No CSS change, so dark mode is as before.
