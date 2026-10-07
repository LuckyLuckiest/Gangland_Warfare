import json, pathlib, html

ROOT = pathlib.Path(r"E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\module-split-2026-09-07")
OUT = pathlib.Path(__file__).with_name("module-split-board.html")

DOCS = [
    ("board",    "Board",        "README.md",             "Sprint board — order, gates, master checklist, decisions"),
    ("feedback", "Report feedback", "REPORT-FEEDBACK.md", "Critique of the design report the sprint executes"),
    ("review",   "Consistency review", "REVIEW-consistency.md", "Cross-plan findings, all patched"),
    ("cops",     "1 · cops-n-crooks", "cops-n-crooks.md",  "Flip 1 checklist"),
    ("gadget",   "2 · gadget",   "gadget.md",             "Flip 2 checklist"),
    ("turf",     "3 · turf",     "turf.md",               "Flip 3 checklist"),
    ("weapon",   "4 · weapon",   "weapon.md",             "Flip 4 checklist"),
    ("briefs",   "Briefs",       None,                    "Planner, executor and template briefs"),
]

def md(name):
    t = (ROOT / name).read_text(encoding="utf-8")
    return t.replace("</script", "<\\/script")

briefs = "\n\n---\n\n".join(
    f"# {n}\n\n" + md(n) for n in ("PLANNER-BRIEF.md", "EXECUTOR-BRIEF.md", "TEMPLATE.md")
)

flips = [
    # id, label, jar, moves, splits, seams, keys, before, after
    ("cops",   "cops-n-crooks", "cops-n-crooks-0.8.4.jar", "90 / 11", "4 holders + 2 contributions", 43, 225, 182),
    ("gadget", "gadget",        "gangland-gadget-0.8.4.jar", "15 / 9", "sign types, sign views, serializer priority", 5, 182, 177),
    ("turf",   "turf",          "gangland-turf-0.8.4.jar",  "26 / 0", "none", 16, 177, 161),
    ("weapon", "weapon",        "gangland-weapon-0.8.4.jar", "33 / 20", "metrics, cleanup, NBT catalogue, shop names, death messages", 12, 161, 149),
]

css = r"""
:root{
  --ground:#F2F3EF; --surface:#FFFFFF; --surface-2:#E8EAE4; --ink:#1B1F24; --ink-2:#3B424B; --muted:#6B7480;
  --rule:#D5D9D2; --rule-strong:#B9BFB6; --accent:#2F5D8A; --accent-ink:#244B70; --accent-soft:#DCE7F2;
  --good:#2E7D5B; --good-soft:#DCEFE5; --warn:#A8701A; --warn-soft:#F5E8CF; --bad:#B23A3A; --bad-soft:#F4DCDC;
  --code:#ECEEE9; --shadow:0 1px 2px rgba(27,31,36,.06),0 10px 28px -16px rgba(27,31,36,.25);
}
@media (prefers-color-scheme: dark){ :root:not([data-theme="light"]){
  --ground:#15181C; --surface:#1E2329; --surface-2:#262C33; --ink:#E6E8EA; --ink-2:#C2C7CD; --muted:#8E97A1;
  --rule:#2E353D; --rule-strong:#414A54; --accent:#7DB0E0; --accent-ink:#A5C9EC; --accent-soft:#1C2E40;
  --good:#5FBF90; --good-soft:#173125; --warn:#D9A24A; --warn-soft:#3A2C12; --bad:#E07A7A; --bad-soft:#3B1F1F;
  --code:#171B20; --shadow:0 1px 2px rgba(0,0,0,.4),0 10px 28px -16px rgba(0,0,0,.7);
}}
:root[data-theme="dark"]{
  --ground:#15181C; --surface:#1E2329; --surface-2:#262C33; --ink:#E6E8EA; --ink-2:#C2C7CD; --muted:#8E97A1;
  --rule:#2E353D; --rule-strong:#414A54; --accent:#7DB0E0; --accent-ink:#A5C9EC; --accent-soft:#1C2E40;
  --good:#5FBF90; --good-soft:#173125; --warn:#D9A24A; --warn-soft:#3A2C12; --bad:#E07A7A; --bad-soft:#3B1F1F;
  --code:#171B20; --shadow:0 1px 2px rgba(0,0,0,.4),0 10px 28px -16px rgba(0,0,0,.7);
}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);font:16px/1.55 "Source Sans 3","Segoe UI",system-ui,sans-serif;-webkit-font-smoothing:antialiased}
code,pre,.mono{font-family:"JetBrains Mono","Cascadia Mono",Consolas,monospace;font-size:.86em}
code{background:var(--code);padding:.05em .3em;border-radius:3px;color:var(--ink-2)}
pre{background:var(--code);border:1px solid var(--rule);border-radius:5px;padding:12px 14px;overflow-x:auto;line-height:1.5;margin:12px 0}
pre code{background:none;padding:0;color:var(--ink)}
h1,h2,h3,h4{font-family:"Barlow Condensed","Arial Narrow",sans-serif;text-wrap:balance;margin:0;color:var(--ink);line-height:1.1}
h1{font-size:clamp(2rem,3.6vw,2.9rem);font-weight:700;letter-spacing:-.005em}
.eyebrow{font-size:.74rem;letter-spacing:.1em;text-transform:uppercase;color:var(--muted);font-weight:600;font-family:"Barlow Condensed",sans-serif}
a{color:var(--accent-ink);text-decoration-thickness:1px;text-underline-offset:2px}
a:focus-visible,button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}

.shell{display:grid;grid-template-columns:232px minmax(0,1fr);min-height:100vh}
aside.nav{position:sticky;top:0;height:100vh;overflow:auto;border-right:1px solid var(--rule);background:var(--surface);padding:22px 16px;display:flex;flex-direction:column;gap:6px}
aside.nav .brand{font-family:"Barlow Condensed",sans-serif;font-weight:700;font-size:1.25rem;line-height:1.1;margin-bottom:6px}
aside.nav .brand small{display:block;font-weight:500;color:var(--muted);font-size:.8rem;letter-spacing:.06em;text-transform:uppercase;margin-top:4px}
aside.nav button{all:unset;cursor:pointer;display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 10px;border-radius:5px;color:var(--ink-2);font-size:.95rem}
aside.nav button:hover{background:var(--surface-2)}
aside.nav button[aria-current="true"]{background:var(--accent-soft);color:var(--accent-ink);font-weight:600}
aside.nav .grp{margin-top:12px}
aside.nav .foot{margin-top:auto;font-size:.78rem;color:var(--muted);line-height:1.45}

main{padding:30px 40px 90px;max-width:1180px}
header.mast{display:grid;grid-template-columns:minmax(0,1.15fr) minmax(0,1fr);gap:28px;align-items:start;padding-bottom:22px;border-bottom:2px solid var(--rule-strong);margin-bottom:22px}
.mast .lede{color:var(--ink-2);max-width:56ch;margin:10px 0 0}
.facts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px 18px;margin-top:18px;font-size:.9rem}
.facts div{border-top:1px solid var(--rule);padding-top:5px}
.facts .k{color:var(--muted);font-size:.72rem;letter-spacing:.07em;text-transform:uppercase;font-family:"Barlow Condensed",sans-serif;font-weight:600}
figure{margin:0}
figure svg{max-width:100%;height:auto;display:block;color:var(--ink)}
figcaption{font-size:.84rem;color:var(--muted);margin-top:6px}

.tiles{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:0 0 26px}
.tile{background:var(--surface);border:1px solid var(--rule);border-radius:6px;padding:12px 14px;display:grid;gap:6px;box-shadow:var(--shadow)}
.tile .n{font-family:"Barlow Condensed",sans-serif;font-weight:700;font-size:1.3rem;display:flex;justify-content:space-between;align-items:baseline}
.tile .n small{font-weight:500;color:var(--muted);font-size:.8rem}
.tile .jar{font-size:.78rem;color:var(--muted)}
.tile .row{display:flex;justify-content:space-between;gap:8px;font-size:.86rem;border-top:1px solid var(--rule);padding-top:5px}
.tile .row span:first-child{color:var(--muted)}
.tile .note{font-size:.82rem;color:var(--ink-2);min-height:1.2em}
.pill{display:inline-block;font-size:.72rem;font-weight:600;letter-spacing:.04em;padding:2px 8px;border-radius:999px;white-space:nowrap;font-family:"Barlow Condensed",sans-serif;text-transform:uppercase}
.pill.good{background:var(--good-soft);color:var(--good)} .pill.warn{background:var(--warn-soft);color:var(--warn)}
.pill.bad{background:var(--bad-soft);color:var(--bad)} .pill.rec{background:var(--accent-soft);color:var(--accent-ink)}
.pill.neutral{background:var(--surface-2);color:var(--ink-2)}
.burn{display:grid;grid-template-columns:auto repeat(4,minmax(0,1fr));gap:0 10px;align-items:end;margin:0 0 30px;padding:14px 16px;background:var(--surface);border:1px solid var(--rule);border-radius:6px}
.burn .lbl{font-size:.8rem;color:var(--muted);align-self:center;max-width:16ch}
.burn .step{display:grid;gap:4px}
.burn .bar{height:10px;border-radius:3px;background:var(--accent);opacity:.85}
.burn .v{font-family:"Barlow Condensed",sans-serif;font-weight:700;font-size:1.25rem;font-variant-numeric:tabular-nums}
.burn .v small{font-weight:500;color:var(--muted);font-size:.78rem;margin-left:4px}
.live{font-size:.8rem;color:var(--muted);margin:-18px 0 22px}
.live b{color:var(--ink-2);font-weight:600}

section.doc{display:none}
section.doc[aria-hidden="false"]{display:block}
.doc .dochead{display:flex;align-items:baseline;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:8px}
.doc .dochead .path{font-size:.8rem;color:var(--muted)}
.md{max-width:none}
.md h1{font-size:2rem;margin:8px 0 14px}
.md h2{font-size:1.5rem;margin:40px 0 10px;padding-top:14px;border-top:2px solid var(--rule-strong)}
.md h3{font-size:1.18rem;margin:28px 0 8px}
.md h4{font-size:1rem;margin:20px 0 6px;font-family:"Source Sans 3",sans-serif;font-weight:700}
.md p{margin:10px 0;max-width:78ch}
.md li{margin:4px 0}
.md ul,.md ol{padding-left:1.3em;max-width:80ch}
.md blockquote{margin:14px 0;padding:10px 14px;border-left:4px solid var(--accent);background:var(--surface);border-radius:0 5px 5px 0;max-width:80ch}
.md blockquote p{margin:4px 0}
.md hr{border:0;border-top:1px solid var(--rule);margin:26px 0}
.md table{border-collapse:collapse;width:100%;font-size:.9rem;margin:12px 0}
.md .tw{overflow-x:auto;border:1px solid var(--rule);border-radius:5px;background:var(--surface);margin:12px 0}
.md .tw table{margin:0}
.md th,.md td{text-align:left;padding:7px 10px;border-bottom:1px solid var(--rule);vertical-align:top}
.md th{font-size:.72rem;letter-spacing:.06em;text-transform:uppercase;color:var(--muted);background:var(--surface-2);font-family:"Barlow Condensed",sans-serif;font-weight:600}
.md tr:last-child td{border-bottom:0}
.md input[type=checkbox]{margin-right:6px;accent-color:var(--accent)}
.md strong{font-weight:700}
@media (max-width:900px){.shell{grid-template-columns:1fr}aside.nav{position:static;height:auto;flex-direction:row;flex-wrap:wrap}aside.nav .foot{display:none}main{padding:20px}header.mast{grid-template-columns:1fr}.tiles{grid-template-columns:repeat(2,1fr)}.burn{grid-template-columns:auto repeat(2,1fr)}}
@media (prefers-reduced-motion: reduce){*{scroll-behavior:auto!important}}
"""

# dependency DAG figure (the reason the order is fixed)
dag = """
<figure>
<svg viewBox="0 0 520 170" role="img" aria-label="Feature dependency graph: gadget depends on weapon; cops-n-crooks depends on weapon and turf. Flip order follows the reverse: cops, gadget, turf, weapon.">
<defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="currentColor"/></marker></defs>
<g font-family="Barlow Condensed, sans-serif" font-size="13" fill="currentColor">
<text x="10" y="18" font-size="11" letter-spacing="1" opacity=".7">DEPENDS ON  →  (flip in the reverse order)</text>
<g><rect x="10" y="40" width="120" height="34" rx="4" fill="none" style="stroke:var(--accent)" stroke-width="2"/><text x="70" y="62" text-anchor="middle" font-weight="700">1 · cops-n-crooks</text></g>
<g><rect x="10" y="110" width="120" height="34" rx="4" fill="none" stroke="currentColor" stroke-width="1.3"/><text x="70" y="132" text-anchor="middle" font-weight="700">2 · gadget</text></g>
<g><rect x="230" y="110" width="110" height="34" rx="4" fill="none" stroke="currentColor" stroke-width="1.3"/><text x="285" y="132" text-anchor="middle" font-weight="700">3 · turf</text></g>
<g><rect x="400" y="75" width="110" height="34" rx="4" fill="none" stroke="currentColor" stroke-width="1.3"/><text x="455" y="97" text-anchor="middle" font-weight="700">4 · weapon</text></g>
<line x1="130" y1="57" x2="398" y2="86" stroke="currentColor" stroke-width="1.3" marker-end="url(#a)"/>
<line x1="130" y1="127" x2="398" y2="98" stroke="currentColor" stroke-width="1.3" marker-end="url(#a)"/>
<line x1="130" y1="62" x2="228" y2="118" stroke="currentColor" stroke-width="1.3" marker-end="url(#a)"/>
<text x="300" y="64" font-size="11" opacity=".75">weapon + turf</text>
<text x="255" y="116" font-size="11" opacity=".75" transform="translate(0,-10)"></text>
<text x="10" y="162" font-size="11" opacity=".75">mail (flipped 0.8.2) depends on nothing. Flipping turf before cops would create the reactor cycle impl → cops → turf → impl.</text>
</g></svg>
<figcaption>The order is not a preference. Only a feature nothing left in the core depends on can leave the core jar.</figcaption>
</figure>
"""

tiles = "".join(f"""
<div class="tile" data-flip="{fid}">
  <div class="n"><span>{n}</span><small>flip {i+1}</small></div>
  <div class="jar mono">{jar}</div>
  <div class="row"><span>moves / splits</span><span class="mono">{ms}</span></div>
  <div class="row"><span>seams</span><span>{seams}</span></div>
  <div class="row"><span>plan</span><span class="pill rec" data-f="plan">ready</span></div>
  <div class="row"><span>execution</span><span class="pill neutral" data-f="execution">not started</span></div>
  <div class="row"><span>review</span><span class="pill neutral" data-f="review">—</span></div>
  <div class="note" data-f="note"></div>
</div>""" for i,(fid,n,jar,ms,seams,keys,b,a) in enumerate(flips))

burn = '<div class="lbl">Core <code>commands.json</code> keys leaving with each flip</div>' + "".join(
    f'<div class="step"><div class="v">{a}<small>−{keys}</small></div><div class="bar" style="width:{a/225*100:.0f}%"></div><div class="eyebrow">{n}</div></div>'
    for (fid,n,jar,ms,seams,keys,b,a) in flips)

nav = "".join(f'<button type="button" data-tab="{i}" aria-current="{"true" if i=="board" else "false"}">{html.escape(l)}</button>' for i,l,_,_ in DOCS)

sections = ""
for i,l,f,desc in DOCS:
    body = briefs if f is None else md(f)
    sections += f"""
<section class="doc" id="doc-{i}" aria-hidden="{"false" if i=="board" else "true"}">
  <div class="dochead"><div class="eyebrow">{html.escape(desc)}</div><div class="path mono">{html.escape("brainstorming/module-split-2026-09-07/" + (f or "PLANNER-BRIEF.md · EXECUTOR-BRIEF.md · TEMPLATE.md"))}</div></div>
  <div class="md" id="md-{i}"></div>
  <script type="text/markdown" id="src-{i}">{body}</script>
</section>"""

js = r"""
const tabs=[...document.querySelectorAll('aside.nav button')];
function show(id,push){for(const s of document.querySelectorAll('section.doc'))s.setAttribute('aria-hidden',s.id!=='doc-'+id);
 for(const b of tabs)b.setAttribute('aria-current',b.dataset.tab===id);
 render(id); if(push)history.replaceState(null,'','#'+id); window.scrollTo({top:0});}
const rendered=new Set();
function render(id){if(rendered.has(id))return; const src=document.getElementById('src-'+id); if(!src)return;
 const el=document.getElementById('md-'+id); el.innerHTML=marked.parse(src.textContent,{gfm:true,breaks:false});
 for(const t of el.querySelectorAll('table')){const w=document.createElement('div');w.className='tw';t.replaceWith(w);w.appendChild(t);}
 for(const c of el.querySelectorAll('input[type=checkbox]'))c.disabled=true; rendered.add(id);}
tabs.forEach(b=>b.addEventListener('click',()=>show(b.dataset.tab,true)));
const initial=(location.hash||'#board').slice(1); show(document.getElementById('doc-'+initial)?initial:'board',false);
window.addEventListener('hashchange',()=>{const h=location.hash.slice(1); if(document.getElementById('doc-'+h))show(h,false);});

// Live status from the artifact database (written by the scrum-master session at flip boundaries).
const cls={ready:'rec','not started':'neutral','in progress':'warn',blocked:'bad',done:'good',green:'good',red:'bad',pending:'neutral',accepted:'good'};
function apply(id,d){const t=document.querySelector(`.tile[data-flip="${id}"]`); if(!t||!d)return;
 for(const f of ['plan','execution','review']){const el=t.querySelector(`[data-f="${f}"]`); if(d[f]){el.textContent=d[f]; el.className='pill '+(cls[d[f].toLowerCase()]||'neutral');}}
 const n=t.querySelector('[data-f="note"]'); n.textContent=d.note||''; if(d.updatedAt){const l=document.getElementById('live'); l.innerHTML='Status tiles are live from the sprint database · last write <b>'+new Date(d.updatedAt).toLocaleString()+'</b>';}}
(async()=>{try{const db=await claude.use('db'); if(!db)return;
 db.collection('flips').onSnapshot(snap=>{for(const doc of snap.docs)apply(doc.id,doc.data());});}catch(e){}})();
"""

page = f"""<title>Module Split Sprint</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@500;600;700&family=Source+Sans+3:wght@400;600;700&family=JetBrains+Mono:wght@400;500&display=swap">
<style>{css}</style>
<script src="https://cdnjs.cloudflare.com/ajax/libs/marked/12.0.2/marked.min.js"></script>
<div class="shell">
<aside class="nav" aria-label="Sections">
  <div class="brand">Module Split Sprint<small>Gangland Warfare · 0.8.4</small></div>
  {nav}
  <div class="foot">Source files live in the repo under <span class="mono">brainstorming/module-split-2026-09-07/</span>. Executors update the status tables in the checklist files; the scrum master mirrors flip status into the tiles above.</div>
</aside>
<main>
<header class="mast">
  <div>
    <div class="eyebrow">Sprint board · opened 7 September 2026</div>
    <h1>Four features leave the core jar</h1>
    <p class="lede">The mail pilot proved the loader. This sprint moves cops-n-crooks, gadget, turf and weapon into <span class="mono">plugins/Gangland_Warfare/modules/</span> one at a time, each planned by an Opus agent, executed by Sonnet agents group by group, and reviewed to green before the next starts.</p>
    <div class="facts">
      <div><div class="k">Branch</div><span class="mono">0.8.4</span> from <span class="mono">0.8.3</span>, cut only after the p0-wave-3 session lands</div>
      <div><div class="k">Keystone</div>1.8.0 · <span class="mono">Host_Api: 0.8</span> unchanged</div>
      <div><div class="k">Impl coupling</div>cops 101 · weapon 55 · turf 30 · gadget 29 files</div>
      <div><div class="k">Definition of done</div>gates G1 compile · G2 tests · G3 jars · G4 docs · G5 review · G6 smoke</div>
    </div>
  </div>
  {dag}
</header>
<div class="tiles">{tiles}</div>
<div class="burn">{burn}</div>
<p class="live" id="live">Status tiles reflect the board at publish time; they update live when the sprint database has newer rows.</p>
{sections}
</main>
</div>
<script>{js}</script>
"""
OUT.write_text(page, encoding="utf-8")
print(OUT, len(page)//1024, "KB")
