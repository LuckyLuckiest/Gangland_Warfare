# Page Encoding Survey: overhaul-updated.html

Describes exactly how the CNC Overhaul artifact page encodes features, graph, roadmap, decisions and validation rules. All line numbers and verbatim examples refer to the current artifact version (shipped 0.15.0, 2026-10-05).

## (1) Feature Card Structure

Complete template copied from `f-heat` card (lines 1–38 of the `<article>` element, spanning HTML lines ~1400–1437 in the full file):

```html
<article class="feature headline" id="f-heat">
            <div>
              <span class="tag-head">Headline</span>
              <h3>Heat ledger</h3>
              <ul class="src"><li>GTA V</li><li>RDR2</li></ul>
              <dl class="meta">
                <div><dt>Effort</dt><dd><span class="effort" data-e="2" aria-hidden="true"><i></i><i></i><i></i></span>M</dd></div>
                <div><dt>Ships in</dt><dd><a href="#r015">0.15</a></dd></div>
                <div><dt>Status</dt><dd><span class="st st-built">Built</span></dd></div>
              </dl>
            </div>
            <div class="body">
              <p class="play">Every crime pays out heat, and heat turns into stars. ...</p>
              <div class="how"><p>Replace the one-point-per-kill combo with a crime-weight table. ...</p></div>
              <p class="code-cap">Sketch for the module's own config</p>
<pre class="code"><code><span class="k">Heat</span>:
   <span class="k">Star_Thresholds</span>: <span class="v">[100, 250, 450, 700, 1000]</span>
   ...</code></pre>
              <p class="state-note"><span class="lbl">Shipped in 0.15.0</span>
                <code>HeatLedger</code> and <code>CrimeRecord</code> ... tests ref)</p>
              <p class="hooks"><span class="lbl">Builds on</span>
                <code>KillComboTracker.addKill(...)</code> ... WantedLevelChangeEvent</p>
              <p class="hooks needs"><span class="lbl">Needs first</span>
                <a href="#e-crime-events">Crime event bus</a> · <a href="#e-wanted-seam">Wanted decay seam</a></p>
            </div>
</article>
```

**Key structural elements:**
- Card ID: `id="f-{feature-id}"` (e.g., `f-heat`, `f-evasion`, `f-hud`)
- Card class: `class="feature headline"` (all cards use this)
- Status chip: `<span class="st st-{status-class}">` where status is `st-built`, `st-partial`, `st-open`, `st-planned`, `st-proposed`, or `st-shipped`
- Ships-in link: `<a href="#r015">0.15</a>` (anchor to release section)
- Effort rating: `data-e="2"` (1–3) with plural dots in `<i>` tags
- State-note (for shipped/partial): `<p class="state-note">` with `<span class="lbl">` label + content
- Hooks sections: `<p class="hooks">` and `<p class="hooks needs">` with vertical-bar `·` separators between links
- All dependencies are `<a href="#...">` anchors to other features or foundations

**When adding a new 0.15.2 card:**
- Assign unique id like `f-auto-drop` or `n-auto-drop` (existing pattern: `f-` for features, `n-` for new additions)
- Use `class="feature headline"` (matches all others)
- Use `st-open`, `st-partial`, etc. to match shipping status
- Anchor to `#r0152` (next release section) or split to new release
- Use `·` (Unicode 00B7) separator in dependencies

---

## (2) Graph Node and Edge Encoding

**Node list location:** Inline SVG element with `class="graph"` at HTML line ~1900–2100 (approx 200 lines).

**Data structure (from JavaScript, lines 2667–2674):**
```javascript
var edges = Array.prototype.slice.call(svg.querySelectorAll('.ge'));
var nodes = Array.prototype.slice.call(svg.querySelectorAll('.gn'));
var up = {}, down = {};
edges.forEach(function (e) {
  var f = e.getAttribute('data-from'), t = e.getAttribute('data-to');
  (up[t] = up[t] || []).push(f);
  (down[f] = down[f] || []).push(t);
});
```

**Node HTML (inline SVG `<g>` elements):**
- Class: `class="gn node {status-class}"` 
- Status classes: `s-built`, `s-partial`, `s-open`, `s-planned`
- Data attributes: `data-id="f-heat"` (matches the feature card id)
- Example (inferred from JavaScript): `<g class="gn node s-built" data-id="f-heat">...</g>`

**Edge HTML (inline SVG `<line>` elements):**
- Class: `class="ge edge"` 
- Data attributes: `data-from="f-evasion"` and `data-to="f-heat"` (dependency: evasion needs heat)
- Example (inferred): `<line class="ge edge" data-from="f-evasion" data-to="f-heat" x1="..." y1="..." x2="..." y2="..." />`

**Legend and counters:**
- Embedded in page HTML (not SVG)
- Counts are **manually maintained** in the edit-log (section 5 of edit-log.md)
- Current counts (edit-log line 40, after 0.15.0 shipped, pass 2): `Built (11) / Part built (8) / To build (48)` = 67 nodes
- When adding a card: update both the SVG and the legend count
- Legend updated when a node status changes, not automatically

**Validation note:** The edit-log contains a "Structural self-check (Python html.parser)" (lines 71–80 and 126–130) that verifies tag balance and node/edge counts. Before republishing, run a similar check to ensure SVG validity.

---

## (3) Roadmap/Release Entry Encoding

**Roadmap section:** HTML `<ol class="roadmap">` at line 2458, containing six `<li id="r{version}">`  entries (r015, r016, r017, r018, r019, r020).

**Complete 0.15 entry structure (lines 2459–2476):**

```html
<li id="r015">
  <div><div class="rel-v">0.15</div><div class="rel-name">Lose them</div><span class="st st-v-shipped">Shipped</span><ul class="rel-deps"><li>Keystone 1.14.0</li><li>gangland-api 2.1</li></ul></div>
  <div>
    <p class="rel-goal">Make escaping a skill. Stars fall when you stay unseen rather than on a fixed timer, and by default a star dropping costs nothing; ...</p>
    <ul class="rel-items">
      <li><a href="#f-heat">Heat ledger</a></li>
      <li><a href="#f-evasion">Line-of-sight evasion</a></li>
      ...
      <li><a href="#n-paid-bounties" class="is-new">Paid bounties</a></li>
    </ul>
    <p class="rel-found"><span>Foundations</span><a href="#e-wanted-seam">Wanted decay seam</a> · <a href="#e-crime-events">Crime event bus</a> · ...</p>
    <p class="rel-why">Written before 0.15 shipped: this is the biggest lever and it is mostly built: ...</p>
  </div>
  <p class="done"><span>Done when</span>Get two stars and break line of sight behind a building: ...</p>
</li>
```

**Key structural elements:**

| Element | HTML | Purpose |
|---|---|---|
| Release id | `id="r015"` | Anchor target from feature cards, links, and TOC |
| Version number | `<div class="rel-v">0.15</div>` | Visible release version |
| Release name | `<div class="rel-name">Lose them</div>` | Tagline or theme |
| Status chip | `<span class="st st-v-shipped">Shipped</span>` | Status: `st-v-shipped`, `st-v-planned`, etc. |
| Dependencies | `<ul class="rel-deps"><li>Keystone 1.14.0</li><li>gangland-api 2.1</li></ul>` | External library versions required |
| Goal | `<p class="rel-goal">...</p>` | One-sentence overview |
| Features | `<ul class="rel-items"><li><a href="#f-heat">Heat ledger</a></li>...</ul>` | Links to all feature cards in this release; use `class="is-new"` for new additions |
| Foundations | `<p class="rel-found"><span>Foundations</span><a href="#e-...">...</a> · ...</p>` | Prerequisite foundation cards, separated by `·` |
| Why order | `<p class="rel-why">...</p>` | Rationale, blockers, and timing notes (updated per pass in edit-log) |
| Done when | `<p class="done"><span>Done when</span>...</p>` | Acceptance criteria and test checklist (updated with verdict after shipping) |

**When adding a new release (e.g., 0.15.2):**
- Add `<li id="r0152">` with sequential version numbers
- Fill status (`st-v-planned` initially)
- List Keystone and API versions it requires
- Link all feature cards it contains with their ids
- For new features, add `class="is-new"` to the `<li>`
- Mark foundations with anchors to foundation ids (prefix `e-`)

---

## (4) "Decisions for You" Items

**Section location:** `<section id="guardrails" class="sec">` at HTML line 2604.

**Complete structure (lines 2621–2650):**

```html
<h3>Decisions for you</h3>
<ul>
  <li><p><b>Decided 2026-10-05.</b> The owner took every recommended answer below, except the release order: 0.15.0 shipped first, ahead of Gang &amp; Turf W4, which now rebases onto it. That also moved the API numbers: 0.15 took gangland-api 2.1, Gang &amp; Turf takes 2.2, and 0.16, 0.17 and 0.20 keep 2.3, 2.4 and 2.5. Still open after 0.15.0:
    <ol class="backlog">
      <li><b>Bounty.Minimum ships 0 (WB-48).</b> An accomplice who is not an ally can post $0.01 and turn a kill into a crime-free takedown. Fix: a non-trivial Minimum, or a floor on other players' escrow (<code>settings.yml:254</code>).</li>
      <li><b>Notoriety is not cleared on death or arrest.</b> 0.15 keeps today's code, where only a claim clears a bounty. ...</li>
      ...
    </ol>
  </li>
  <li><p><b>Evasion clock vs the H11/H12 decline of a wanted-decay freeze: may stars stop falling while a cop can see you?</b> Approve line-of-sight evasion as a replacement for the fixed timer, not a freeze bolted onto it. Stars fall faster than today when you are unseen (a skill path), Repeating_Timer stays as a safety net, and an Evasion.Enabled switch restores today's behaviour. Decide before 0.15 starts. <span class="alt">Keep the fixed timer and only add the HUD; or let seen time merely slow the timer instead of pausing it.</span></p></li>
  <li><p><b>One star at a time, or all at once?</b> Drop one star per completed clock, with Drop_Mode (ONE/ALL) in cops-n-crooks YAML so a server can switch to GTA V behaviour. <span class="alt">Clear every star on evade (GTA V).</span></p></li>
  ...
</ul>
```

**Key structural elements:**

| Element | HTML | Purpose |
|---|---|---|
| Item container | `<li><p>...</p></li>` | Each decision is one list item |
| Bold summary | `<b>Title of decision (WB-xx if it refs a docket entry).</b>` | First line, bold, names the issue |
| Recommended answer | Text after the `<b>` (same paragraph) | What was approved or recommended |
| Alternative choices | `<span class="alt">Alternative 1; or Alternative 2.</span>` | Options not chosen, separated by `; or` |
| Open subquestions | `<ol class="backlog">` nested inside the first "Decided" item | Points still unresolved after shipping |
| Bug/decision refs | `(WB-48)`, `(settings.yml:254)` | Cross-refs to bug docket or config file |

**When adding AUTO decision items:**
- Add a new `<li>` at the end of the list (before closing `</ul>`)
- Start with `<b>Title and any bug refs.</b>` 
- Follow with description of what was decided or what is still open
- If there are alternatives, wrap them: `<span class="alt">Alt 1; or Alt 2.</span>`
- If it generates open subquestions after implementation, nest them as `<ol class="backlog"><li>...` inside a top-level `<li>`

---

## (5) Task/DAG and "How to Build" Conventions

**Feature dependency graphs:**
- Not a separate table; encoded in card `<p class="hooks needs">` sections
- Example (f-heat, line 35–36): `Needs first <a href="#e-crime-events">Crime event bus</a> · <a href="#e-wanted-seam">Wanted decay seam</a>`
- "Builds on" (line 34–35): lists what existing code is touched, e.g., `KillComboTracker.addKill(...)`

**Release dependency order:**
- Encoded in roadmap `rel-why` paragraphs (e.g., line 2473 for 0.15)
- Plain prose explaining blockers, prerequisites, and timing
- Example: "It starts only after Gang &amp; Turf W4 has merged (0.14.0, or 0.14.1 if W4 splits off), because W4 owns core wanted and EntityDamageListener."

**No separate DAG file:** The page itself is the DAG; the graph SVG visualizes the feature-level dependencies, not a build order.

---

## (6) Table of Contents, Navigation, Filters and Counters

**TOC (table of contents):**
- Located at HTML line ~630 (before main content)
- Class: `class="toc"` 
- Each TOC link has `href="#section-id"` (e.g., `href="#today"`, `href="#roadmap"`, `href="#guardrails"`)
- No dynamic recomputation; links are hardcoded and must be updated if sections are added/renamed

**Section anchors:**
- Each major `<h2>` heading wrapped in a `<section id="{section-id}">` (e.g., `<section id="today" class="sec">`, `<section id="guardrails" class="sec">`)
- Section ids: `today`, `numbers`, `needs`, `loop`, `star-meaning`, `money`, `crime`, `response`, `chase`, `ending`, `record`, `sides`, `roadmap`, `guardrails`
- Add new section and update TOC simultaneously

**Status legend:**
- Located in masthead area (early in the page)
- Counts are embedded in the HTML and manually updated
- Pattern: `Built (11)`, `Part built (8)`, `To build (48)` (edit-log line 40, corrected in pass 2, line 129)
- Counts must match the sum of all node status classes in the SVG

**When adding AUTO feature card:**
1. Assign feature id (`f-auto-drop` or `n-auto-drop`)
2. Add SVG node with `data-id="..."` matching the feature id
3. Add SVG edges if it depends on other features
4. Recount nodes by status class (`s-built`, `s-partial`, `s-open`) and update legend
5. Add feature to roadmap `rel-items` list in the appropriate release
6. If new release, add new roadmap `<li>` with new section id, update TOC, and add `<section>` wrapper

---

## (7) Validation Scripts and Checks

**Pre-publish validation (from edit-log, lines 71–81 and 126–130):**

Python `html.parser` structural check:
- **Tag balance:** Count open/close tags for each type (divs, spans, articles, tables, SVG elements)
- **Node counts:** Sum status classes `s-built`, `s-partial`, `s-open` from SVG and verify against legend
- **Status chip classes:** Track all `<span class="st st-{status}">` and confirm counts
- **Nesting errors:** Zero nesting violations allowed

Pass 1 (after 0.15.0 shipped):
- Graph: 67 nodes = 11 built / 7 partial / 49 open
- Status chips: Built 12, To build 41, Part built 8, Planned 3, This plan 9, Shipped 10, Complete 1, Verifying 0
- Tag deltas: div +1, ul +1, ol +1, li +20, p +12, b +15, span +38, code +128

Pass 2 (after live deploy result):
- Graph: 67 nodes = 11 built / 8 partial / 48 open (self-defence bumped to partial)
- Status chips: Part built 9, To build 40 (matching graph recount)
- Tag deltas: p +2, b +1, span +2, code +13

**How to run the check:**
```python
from html.parser import HTMLParser

class TagCounter(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tags = {}
        self.errors = []
    
    def handle_starttag(self, tag, attrs):
        self.tags[tag] = self.tags.get(tag, 0) + 1
        # Capture status classes for cross-check
        for k, v in attrs:
            if k == 'class' and ('st-' in v or 's-' in v):
                self.tags[f"{tag}@{k}={v}"] = self.tags.get(f"{tag}@{k}={v}", 0) + 1
    
    def handle_endtag(self, tag):
        if tag not in self.tags:
            self.errors.append(f"Unmatched end tag: {tag}")

# Parse and verify counts
```

**Link validation (manual check from edit-log):**
- All `href="#..."` anchors must resolve to existing `id="..."` attributes
- All feature cards must be linked from at least one roadmap release
- All foundation cards (`id="e-..."`) must be linked from at least one feature

**When adding AUTO card:**
1. After editing, recount all status classes and update legend
2. Verify new SVG nodes have matching feature card ids
3. Run tag-balance check to ensure no malformed HTML
4. Verify all intra-page links (`href="#..."`) target existing ids
5. Update the edit-log with counts before and after (new row in "Pass N" section)

---

## (10-Line Summary)

The artifact encodes features as `<article id="f-{id}" class="feature headline">` cards with status chips (`st-built`, etc.), dependency links in hooks sections, and test references. Graph nodes are inline SVG `<g class="gn {status}">` elements with `data-id` attributes, edges are `<line data-from="" data-to="">` pairs, and counts are manually maintained in a visible legend (currently 67 nodes: 11 built / 8 partial / 48 open). Roadmap releases are `<li id="r{version}">` with feature links, foundations, and "Done when" acceptance criteria; each release also has a matching `<section id="r{version}">` for deep-dive prose. "Decisions for you" items are `<li>` entries with bold titles, recommended answers, and `<span class="alt">` alternatives. Validation is a Python HTML parser check (tag counts and balance) cross-checked against manually maintained legend counts. To add AUTO to the page: create feature card(s) with unique ids, add SVG nodes/edges, insert into a roadmap release (new or existing), update all counts and TOC links, then verify with the parser.
