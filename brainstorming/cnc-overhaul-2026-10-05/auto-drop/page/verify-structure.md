# Verify (lens: structure) - overhaul-updated.html vs overhaul-v4.html

Script: `auto-drop/page/check_page.py` (re-run: `python -I check_page.py [new.html] [baseline.html]`; exit 1 on any problem the baseline does not have).
Run result: **NEW problems: 0**. Findings by severity: blocker 0, major 0, minor 0.

## Findings

None. (Numbered list is empty: every check below passed with nothing blamed on the edit.)

## What was checked, with evidence

| # | Check | New (68 nodes) | Baseline v4 (67 nodes) |
|---|---|---|---|
| 1 | Tag balance, whole document, stack based (optional-end tags p/li/td/tr allowed) | 0 errors | 0 errors |
| 2 | Duplicate ids (91 ids vs 88) | none | none |
| 3 | `href="#x"` with no matching id (includes `#r0152`, `#n-auto-drop`, `#d-auto-drop`, `#n-cold-trail`) | none | none |
| 4 | Graph: 68 `.gn` nodes each have a card/foundation id; 90 `.ge` edges all point at existing nodes; no duplicate edges; every edge path starts/ends on the box edge at mid-height of its two nodes (including the 3 new edges into `n-auto-drop`); no two node boxes overlap (including the moved `e-bot-acceptance-scenarios`, `n-cnc-regroup`); all boxes inside viewBox 1210x636 | pass | pass |
| 5 | Legend vs computed status classes: Built 11 / Part built 8 / To build 49 = 68 (v4: 11 / 8 / 48 = 67); svg aria-label says 68; masthead "55 features, 25 new, 13 foundations" = 55 `<article>`, 55 `k-feature` nodes, 25 `tag-new` spans inside articles, 13 `k-enabler` nodes | pass | pass |
| 6 | Inline `<script>` (2 blocks, unchanged by the edit): `node --check` | both parse | both parse |
| 7a | Every `<table>` sits inside `.table-wrap`/`.sheet`/`.graph-wrap` (so min-width 640/820/900 scrolls inside its box); no inline `width:` over 360 px | pass | pass |
| 7b | Headless Chrome, page loaded in an iframe 360 px wide (headless Chrome will not open a window under 500 px) and 1000 px wide: `scrollWidth` = viewport (345 of 360 after scrollbar; 985 of 1000), no element past the viewport outside a scroll container, no clipped text in `.rel-v`, `.rel-name`, `.st`, `.rel-deps li`, `.vt-v`, `.meta dd` (so "0.15.2" at 2rem fits the 132 px roadmap column) | pass | pass |

## Checker validated against deliberate breakage

A scratch copy with an unclosed `<div>`, a 900 px inline-width div, an edge to a missing node, a wrong legend count and a duplicate id was run through the same script: it reported 9 new problems (unclosed-before-end, dup-id, dead-href, legend-count, node-without-card, edge-dangling, inline-width, page-h-scroll 916 vs 345, element-past-viewport). A copy with `.rel-v.pt` at 4rem was reported as `text-clipped` (scroll 137, client 132). So a clean run is meaningful.

## Limits (not verified)

- Dark mode: checked only that no width-affecting rule differs (the edit adds colour-neutral rules and reuses existing classes); colours were not rendered or contrast-tested.
- Widths between 360 and 1000 px (for example 700 px, where the roadmap grid has one column and the feature grid has one column) were not probed.
- Content accuracy against PLAN.md is out of scope for this lens.
