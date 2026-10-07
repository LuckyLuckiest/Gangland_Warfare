"""Regenerates bartizan-board.html (the "Bartizan Wave" artifact page) from the markdown in this folder.

Run:  python build_board.py
Then republish the HTML to the artifact URL recorded in README.md. Live status (phase tiles, smoke rows, agent
feedback) comes from the artifact database: collections `phases` (A..E), `smoke` (S1..S8), `feedback` (one doc per
agent report) with fields {status|verdict, note, updatedAt, ...}; the orchestrator writes them with write_db.
"""
import html
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
OUT = ROOT / "bartizan-board.html"


def md(rel):
    p = ROOT / rel
    if not p.exists():
        return f"_`{rel}` has not been written yet._"
    text = p.read_text(encoding="utf-8", errors="replace").replace("�", "?")
    return text.replace("</script", "<\\/script")


def folder_docs(sub, title):
    d = ROOT / sub
    if not d.exists():
        return f"_No {title.lower()} yet._"
    files = sorted(d.glob("*.md"))
    if sub == "smoke/reports":
        latest = {}
        for f in files:
            stem = f.stem
            if stem.endswith("-summary") or stem.endswith("-verdicts"):
                latest[stem] = f
            else:
                latest[stem.rsplit("-", 1)[-1]] = f   # ascending sort -> the newest run per row wins
        files = sorted(latest.values())
    if not files:
        return f"_No {title.lower()} yet._"
    return "\n\n---\n\n".join(f"<!-- {f.name} -->\n" + md(f"{sub}/{f.name}") for f in files)


DOCS = [
    ("board", "Board", md("README.md"), "Decisions, topology, phases, smoke matrix, findings"),
    ("explore", "Exploration", folder_docs("exploration", "Exploration reports"), "Opus explorer reports (weapon boundary · item + loader infra · NPC infra)"),
    ("arch", "Architecture", folder_docs("architecture", "Architecture blueprints"), "Three blueprints and the pick"),
    ("review", "Consistency review", md("REVIEW-consistency.md"), "Cross-checklist findings, rulings a–f, execution order, phase-D smoke rows"),
    ("review-ga", "Gate GA review", md("REVIEW-bartizan-GA.md"), "Bartizan groups A+B · pass with fixes (bStats relocation, GC gate criterion)"),
    ("review-gd", "Gate GD review", md("REVIEW-bartizan-GD.md"), "Bartizan api freeze · pass with fixes · the surface P3 may rely on"),
    ("review-gbc", "Gangland B/C review", md("REVIEW-gangland-BC.md"), "Weapon + compatibility deletion · pass with fixes · death message restored"),
    ("review-gg", "Gate GG review", md("REVIEW-bartizan-GG.md"), "Bartizan bootstrap, actions, listeners, item framework · pass with fixes (cleanup period, teardown)"),
    ("review-gdefg", "Gangland D-G review", md("REVIEW-gangland-DEFG.md"), "keystone-item migration, vocabulary fold, item signs · pass with fixes (sign similarity, fold ordering)"),
    ("review-gh", "Gangland H review", md("REVIEW-gangland-H.md"), "gangland-civilians module + legacy sign aliases · pass with fixes · Bartizan-absent blast radius flagged"),
    ("review-bfinal", "Bartizan final review", md("REVIEW-bartizan-FINAL.md"), "groups J-O + whole-plugin runtime pass · pass with fixes (persistence never ran) · phase-D smoke checklist"),
    ("review-gri", "Gangland G-R/I review", md("REVIEW-gangland-RI.md"), "review fixes pass · turf-NPC move pass with fixes · turf-without-Bartizan consequence"),
    ("review-gkl", "Gangland K/L review", md("REVIEW-gangland-KL.md"), "cops rebase + gadget re-point · pass with fixes (false T-K5 claim, dead fuel-sink override, car explosion double hit)"),
    ("review-gfinal", "Gangland final review", md("REVIEW-gangland-FINAL.md"), "groups M-O + runtime pass · FAIL as reviewed: GadgetModuleConfig constructor blocker, three unconditioned Citizens listeners · smoke checklist"),
    ("review-dfix", "Phase D fixes review", md("REVIEW-phaseD-fixes.md"), "Keystone 1.9.1 + Gangland D-fix-1 · pass with fixes (blind-loader test hardened, interface-typed beans scanned) · Citizens-present checks"),
    ("keystone", "Keystone 1.9.0", md("keystone-1.9.0.md"), "Executor checklist · keystone-item, keystone-npc, plugin dependency key"),
    ("bartizan", "Bartizan 0.1.0", md("bartizan.md"), "Executor checklist · the standalone weapons plugin"),
    ("gangland", "Gangland 0.9.0", md("gangland-0.9.0.md"), "Executor checklist · weapon-free core, turf NPCs, npc-shops"),
    ("smoke", "Smoke reports", folder_docs("smoke/reports", "Smoke reports"), "Console smoke runs on the test server"),
    ("feedback", "Agent feedback", folder_docs("feedback", "Agent feedback"), "What the Opus testers and reviewers reported, verbatim"),
]

PHASES = [
    ("A", "0.8.4 stabilisation", "docket · Keystone 1.8.1 · Gangland fixes · harness · matrix"),
    ("B", "Exploration + architecture", "three explorers · three architects · the pick"),
    ("C", "Checklists + execution", "Keystone 1.9.0 → Bartizan → Gangland 0.9.0"),
    ("D", "Smoke of the new topology", "matrix again on Keystone 1.9.0 + Bartizan + 0.9.0"),
    ("E", "Wrap-up", "docs · memory · graphs · commits"),
]

SMOKE = [
    ("S1", "empty modules/", "0 loaded, 0 faults, core-only help"),
    ("S2", "weapon", "weapon loads, /glw weapon list answers"),
    ("S3", "weapon + gadget", "both load, /glw car answers"),
    ("S4", "turf + weapon + cops", "three load, cops/turf answer, detainment migrates"),
    ("S5", "all five", "5 loaded, 0 faults"),
    ("S6", "cops without turf", "cops skipped with module.dependency.missing"),
    ("S7", "weapon + Host_Api 0.7 copy", "copy skipped with a readable fault"),
    ("S8", "all five + /glw reload", "modules keep answering, no duplicate listeners"),
]

FINDINGS = [
    ("T-12", "P0", "cops module present crashes boot: NoClassDefFoundError net/wesjd/anvilgui/AnvilGUI$StateSnapshot (core relocates AnvilGUI, module jars do not)"),
    ("T-13", "P1", "Keystone ListenerService dispatches sibling events sharing a HandlerList → argument type mismatch on every ArmorStand/creature spawn"),
    ("T-14", "P2", "core WearableEquipListener needs the weapon module's WearableEquipService → listener fails to instantiate with no weapon module"),
    ("T-15", "P2", "Keystone UpdateNotifier.start() checks for updates on the server thread → 10 s boot stall when offline"),
    ("T-16", "P3", "DatabaseManager.startBackup warns 'Failed to create a backup' on every shutdown"),
    ("T-17", "P3", "'Found Vault, linking...' / 'Linked Vault' printed twice per boot"),
    ("T-18", "P3", "LanguageLoader reports message_en.yml missing Errors.Bounty.Below_Minimum although the jar default carries it"),
]

css = r"""
:root{
  --ground:#EEF0F2; --surface:#FFFFFF; --surface-2:#E3E7EA; --ink:#1B1E22; --ink-2:#3D444C; --muted:#6C757F;
  --rule:#CFD5DA; --rule-strong:#AEB7BE; --accent:#9A4A22; --accent-ink:#7E3A17; --accent-soft:#F1E1D6;
  --good:#2C6E49; --good-soft:#D9EBDF; --warn:#9A6A12; --warn-soft:#F3E7C6; --bad:#A63232; --bad-soft:#F3D9D9;
  --code:#E6E9EC; --shadow:0 1px 2px rgba(27,30,34,.06),0 10px 24px -18px rgba(27,30,34,.3);
}
@media (prefers-color-scheme: dark){ :root:not([data-theme="light"]){
  --ground:#14171A; --surface:#1C2024; --surface-2:#252B31; --ink:#E7EAEC; --ink-2:#C4CAD0; --muted:#8A949E;
  --rule:#2C333A; --rule-strong:#414A53; --accent:#D98757; --accent-ink:#E9A67E; --accent-soft:#3A2418;
  --good:#63B98A; --good-soft:#18301F; --warn:#D7A946; --warn-soft:#3A2D10; --bad:#E07B7B; --bad-soft:#3B1D1D;
  --code:#111417; --shadow:0 1px 2px rgba(0,0,0,.4),0 10px 24px -18px rgba(0,0,0,.7);
}}
:root[data-theme="dark"]{
  --ground:#14171A; --surface:#1C2024; --surface-2:#252B31; --ink:#E7EAEC; --ink-2:#C4CAD0; --muted:#8A949E;
  --rule:#2C333A; --rule-strong:#414A53; --accent:#D98757; --accent-ink:#E9A67E; --accent-soft:#3A2418;
  --good:#63B98A; --good-soft:#18301F; --warn:#D7A946; --warn-soft:#3A2D10; --bad:#E07B7B; --bad-soft:#3B1D1D;
  --code:#111417; --shadow:0 1px 2px rgba(0,0,0,.4),0 10px 24px -18px rgba(0,0,0,.7);
}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);font:16px/1.55 "IBM Plex Sans","Segoe UI",system-ui,sans-serif;-webkit-font-smoothing:antialiased}
code,pre,.mono{font-family:"IBM Plex Mono","Cascadia Mono",Consolas,monospace;font-size:.86em}
code{background:var(--code);padding:.05em .3em;border-radius:3px;color:var(--ink-2)}
pre{background:var(--code);border:1px solid var(--rule);border-radius:4px;padding:12px 14px;overflow-x:auto;line-height:1.5;margin:12px 0}
pre code{background:none;padding:0;color:var(--ink)}
h1,h2,h3,h4{font-family:"IBM Plex Sans Condensed","Arial Narrow",sans-serif;text-wrap:balance;margin:0;color:var(--ink);line-height:1.1}
h1{font-size:clamp(2rem,3.4vw,2.8rem);font-weight:600;letter-spacing:-.01em}
.eyebrow{font-size:.72rem;letter-spacing:.11em;text-transform:uppercase;color:var(--muted);font-weight:600;font-family:"IBM Plex Sans Condensed",sans-serif}
a{color:var(--accent-ink);text-decoration-thickness:1px;text-underline-offset:2px}
a:focus-visible,button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}

.shell{display:grid;grid-template-columns:224px minmax(0,1fr);min-height:100vh}
aside.nav{position:sticky;top:0;height:100vh;overflow:auto;border-right:1px solid var(--rule);background:var(--surface);padding:22px 14px;display:flex;flex-direction:column;gap:4px}
aside.nav .brand{font-family:"IBM Plex Sans Condensed",sans-serif;font-weight:600;font-size:1.3rem;line-height:1.1;margin-bottom:8px}
aside.nav .brand small{display:block;font-weight:500;color:var(--muted);font-size:.76rem;letter-spacing:.08em;text-transform:uppercase;margin-top:4px}
aside.nav button{all:unset;cursor:pointer;display:block;padding:7px 10px;border-radius:4px;color:var(--ink-2);font-size:.94rem}
aside.nav button:hover{background:var(--surface-2)}
aside.nav button[aria-current="true"]{background:var(--accent-soft);color:var(--accent-ink);font-weight:600}
aside.nav .foot{margin-top:auto;font-size:.76rem;color:var(--muted);line-height:1.45}

main{padding:28px 40px 90px;max-width:1200px}
header.mast{padding-bottom:20px;border-bottom:2px solid var(--rule-strong);margin-bottom:22px}
.mast .lede{color:var(--ink-2);max-width:66ch;margin:10px 0 0}
.facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px 18px;margin-top:18px;font-size:.9rem}
.facts div{border-top:1px solid var(--rule);padding-top:5px}
.facts .k{color:var(--muted);font-size:.7rem;letter-spacing:.08em;text-transform:uppercase;font-family:"IBM Plex Sans Condensed",sans-serif;font-weight:600}

.phases{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px;margin:0 0 24px}
.phase{background:var(--surface);border:1px solid var(--rule);border-radius:4px;padding:10px 12px;display:grid;gap:5px;border-top:3px solid var(--rule-strong)}
.phase[data-state="in progress"]{border-top-color:var(--warn)} .phase[data-state="done"]{border-top-color:var(--good)} .phase[data-state="blocked"]{border-top-color:var(--bad)}
.phase .l{font-family:"IBM Plex Sans Condensed",sans-serif;font-weight:600;font-size:1.05rem;display:flex;justify-content:space-between;align-items:baseline;gap:6px}
.phase .l small{color:var(--muted);font-weight:500;font-size:.78rem}
.phase .d{font-size:.8rem;color:var(--muted)}
.phase .note{font-size:.82rem;color:var(--ink-2);min-height:1.2em}
.pill{display:inline-block;font-size:.7rem;font-weight:600;letter-spacing:.05em;padding:2px 8px;border-radius:3px;white-space:nowrap;font-family:"IBM Plex Sans Condensed",sans-serif;text-transform:uppercase}
.pill.good{background:var(--good-soft);color:var(--good)} .pill.warn{background:var(--warn-soft);color:var(--warn)}
.pill.bad{background:var(--bad-soft);color:var(--bad)} .pill.rec{background:var(--accent-soft);color:var(--accent-ink)}
.pill.neutral{background:var(--surface-2);color:var(--ink-2)}

h2.sec{font-size:1.35rem;margin:26px 0 8px;font-weight:600}
table.grid{border-collapse:collapse;width:100%;font-size:.9rem;background:var(--surface);border:1px solid var(--rule);border-radius:4px}
.tw{overflow-x:auto;margin:0 0 6px}
table.grid th,table.grid td{text-align:left;padding:7px 10px;border-bottom:1px solid var(--rule);vertical-align:top}
table.grid th{font-size:.7rem;letter-spacing:.07em;text-transform:uppercase;color:var(--muted);background:var(--surface-2);font-family:"IBM Plex Sans Condensed",sans-serif;font-weight:600}
table.grid tr:last-child td{border-bottom:0}
table.grid td.id{font-family:"IBM Plex Mono",monospace;font-size:.82rem;white-space:nowrap}
table.grid td.note{color:var(--ink-2);font-size:.84rem}
.tier{font-family:"IBM Plex Mono",monospace;font-size:.78rem;font-weight:600;padding:1px 6px;border-radius:3px}
.tier.P0{background:var(--bad-soft);color:var(--bad)} .tier.P1{background:var(--warn-soft);color:var(--warn)} .tier.P2{background:var(--accent-soft);color:var(--accent-ink)} .tier.P3{background:var(--surface-2);color:var(--ink-2)}
.live{font-size:.78rem;color:var(--muted);margin:4px 0 0}
.live b{color:var(--ink-2);font-weight:600}
.fb{display:grid;gap:10px;margin:8px 0 0}
.fbi{background:var(--surface);border:1px solid var(--rule);border-left:3px solid var(--accent);border-radius:4px;padding:10px 14px;display:grid;gap:4px}
.fbi .h{display:flex;justify-content:space-between;gap:10px;align-items:baseline;flex-wrap:wrap}
.fbi .h b{font-family:"IBM Plex Sans Condensed",sans-serif;font-weight:600;font-size:1.02rem}
.fbi .h small{color:var(--muted);font-size:.78rem}
.fbi p{margin:0;font-size:.9rem;color:var(--ink-2);max-width:90ch}
.empty{color:var(--muted);font-size:.88rem;font-style:italic}

section.doc{display:none}
section.doc[aria-hidden="false"]{display:block}
.doc .dochead{display:flex;align-items:baseline;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:8px}
.doc .dochead .path{font-size:.78rem;color:var(--muted)}
.md h1{font-size:1.9rem;margin:8px 0 14px}
.md h2{font-size:1.45rem;margin:38px 0 10px;padding-top:14px;border-top:2px solid var(--rule-strong)}
.md h3{font-size:1.15rem;margin:26px 0 8px}
.md h4{font-size:1rem;margin:20px 0 6px;font-family:"IBM Plex Sans",sans-serif;font-weight:700}
.md p{margin:10px 0;max-width:80ch}
.md li{margin:4px 0}
.md ul,.md ol{padding-left:1.3em;max-width:82ch}
.md blockquote{margin:14px 0;padding:10px 14px;border-left:4px solid var(--accent);background:var(--surface);border-radius:0 4px 4px 0;max-width:80ch}
.md blockquote p{margin:4px 0}
.md hr{border:0;border-top:1px solid var(--rule);margin:26px 0}
.md table{border-collapse:collapse;width:100%;font-size:.88rem;margin:12px 0}
.md .tw{overflow-x:auto;border:1px solid var(--rule);border-radius:4px;background:var(--surface);margin:12px 0}
.md .tw table{margin:0}
.md th,.md td{text-align:left;padding:7px 10px;border-bottom:1px solid var(--rule);vertical-align:top}
.md th{font-size:.7rem;letter-spacing:.07em;text-transform:uppercase;color:var(--muted);background:var(--surface-2);font-family:"IBM Plex Sans Condensed",sans-serif;font-weight:600}
.md tr:last-child td{border-bottom:0}
.md input[type=checkbox]{margin-right:6px;accent-color:var(--accent)}
@media (max-width:960px){.shell{grid-template-columns:1fr}aside.nav{position:static;height:auto;flex-direction:row;flex-wrap:wrap}aside.nav .foot{display:none}main{padding:20px}.facts{grid-template-columns:repeat(2,1fr)}.phases{grid-template-columns:repeat(2,1fr)}}
@media (prefers-reduced-motion: reduce){*{scroll-behavior:auto!important}}
"""

phase_tiles = "".join(f"""
<div class="phase" data-phase="{p}" data-state="todo">
  <div class="l"><span>{p} · {html.escape(t)}</span><small class="pill neutral" data-f="status">todo</small></div>
  <div class="d">{html.escape(d)}</div>
  <div class="note" data-f="note"></div>
</div>""" for p, t, d in PHASES)

smoke_rows = "".join(f"""
<tr data-row="{s}"><td class="id">{s}</td><td>{html.escape(m)}</td><td class="note">{html.escape(e)}</td><td><span class="pill neutral" data-f="verdict">not run</span></td><td class="note" data-f="note"></td></tr>""" for s, m, e in SMOKE)

finding_rows = "".join(f"""
<tr data-bug="{i}"><td class="id">{i}</td><td><span class="tier {t}">{t}</span></td><td class="note">{html.escape(x)}</td><td><span class="pill neutral" data-f="status">open</span></td><td class="note" data-f="note"></td></tr>""" for i, t, x in FINDINGS)

nav = "".join(f'<button type="button" data-tab="{i}" aria-current="{"true" if i == "board" else "false"}">{html.escape(l)}</button>' for i, l, _, _ in DOCS)

sections = ""
for i, l, body, desc in DOCS:
    sections += f"""
<section class="doc" id="doc-{i}" aria-hidden="{"false" if i == "board" else "true"}">
  <div class="dochead"><div class="eyebrow">{html.escape(desc)}</div><div class="path mono">brainstorming/bartizan-split-2026-09-08/</div></div>
  <div class="md" id="md-{i}"></div>
  <script type="text/markdown" id="src-{i}">{body}</script>
</section>"""

js = r"""
const tabs=[...document.querySelectorAll('aside.nav button')];
const dash=document.getElementById('dash');
function show(id,push){for(const s of document.querySelectorAll('section.doc'))s.setAttribute('aria-hidden',s.id!=='doc-'+id);
 for(const b of tabs)b.setAttribute('aria-current',b.dataset.tab===id);
 dash.hidden=(id!=='board'); render(id); if(push)history.replaceState(null,'','#'+id); window.scrollTo({top:0});}
const rendered=new Set();
function render(id){if(rendered.has(id))return; const src=document.getElementById('src-'+id); if(!src)return;
 const el=document.getElementById('md-'+id); el.innerHTML=marked.parse(src.textContent,{gfm:true,breaks:false});
 for(const t of el.querySelectorAll('table')){const w=document.createElement('div');w.className='tw';t.replaceWith(w);w.appendChild(t);}
 for(const c of el.querySelectorAll('input[type=checkbox]'))c.disabled=true; rendered.add(id);}
tabs.forEach(b=>b.addEventListener('click',()=>show(b.dataset.tab,true)));
const initial=(location.hash||'#board').slice(1); show(document.getElementById('doc-'+initial)?initial:'board',false);
window.addEventListener('hashchange',()=>{const h=location.hash.slice(1); if(document.getElementById('doc-'+h))show(h,false);});

const cls={todo:'neutral','not run':'neutral','in progress':'warn',blocked:'bad',done:'good',pass:'good',fail:'bad',partial:'warn',open:'neutral',fixed:'good','won\'t fix':'neutral'};
function pill(el,v){if(!el||!v)return; el.textContent=v; el.className='pill '+(cls[v.toLowerCase()]||'neutral');}
function stamp(ts){if(!ts)return; const l=document.getElementById('live'); l.innerHTML='Live from the wave database · last write <b>'+new Date(ts).toLocaleString()+'</b>';}
function esc(s){return String(s??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));}
(async()=>{try{const db=await claude.use('db'); if(!db)return;
 db.collection('phases').onSnapshot(s=>{for(const d of s.docs){const t=document.querySelector(`.phase[data-phase="${d.id}"]`); if(!t)continue; const v=d.data();
   pill(t.querySelector('[data-f="status"]'),v.status); t.dataset.state=(v.status||'todo').toLowerCase(); t.querySelector('[data-f="note"]').textContent=v.note||''; stamp(v.updatedAt);}});
 db.collection('smoke').onSnapshot(s=>{for(const d of s.docs){const r=document.querySelector(`tr[data-row="${d.id}"]`); if(!r)continue; const v=d.data();
   pill(r.querySelector('[data-f="verdict"]'),v.verdict); r.querySelector('[data-f="note"]').textContent=v.note||''; stamp(v.updatedAt);}});
 db.collection('findings').onSnapshot(s=>{for(const d of s.docs){const r=document.querySelector(`tr[data-bug="${d.id}"]`); if(!r)continue; const v=d.data();
   pill(r.querySelector('[data-f="status"]'),v.status); r.querySelector('[data-f="note"]').textContent=v.note||''; stamp(v.updatedAt);}});
 db.collection('feedback').onSnapshot(s=>{const box=document.getElementById('fb'); const docs=[...s.docs].map(d=>({id:d.id,...d.data()})).sort((a,b)=>(a.updatedAt||'')<(b.updatedAt||'')?1:-1);
   if(!docs.length){box.innerHTML='<div class="empty">No agent reports yet.</div>';return;}
   box.innerHTML=docs.map(v=>`<div class="fbi"><div class="h"><b>${esc(v.agent||v.id)}</b><span class="pill ${cls[(v.verdict||'').toLowerCase()]||'neutral'}">${esc(v.verdict||'report')}</span><small>${esc(v.role||'')}${v.updatedAt?' · '+new Date(v.updatedAt).toLocaleString():''}</small></div><p>${esc(v.summary||'')}</p></div>`).join('');
   stamp(docs[0].updatedAt);});
}catch(e){}})();
"""

page = f"""<title>Bartizan Wave</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Condensed:wght@500;600&family=IBM+Plex+Sans:wght@400;600;700&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>{css}</style>
<script src="https://cdnjs.cloudflare.com/ajax/libs/marked/12.0.2/marked.min.js"></script>
<div class="shell">
<aside class="nav" aria-label="Sections">
  <div class="brand">Bartizan Wave<small>Gangland 0.9.0 · Keystone 1.9.0 · Bartizan 0.1.0</small></div>
  {nav}
  <div class="foot">Sources live in the repo under <span class="mono">brainstorming/bartizan-split-2026-09-08/</span>. Phase tiles, smoke rows, finding status and agent feedback are written by the orchestrator session as work lands.</div>
</aside>
<main>
<header class="mast">
  <div class="eyebrow">Wave board · opened 8 September 2026</div>
  <h1>Weapons become Bartizan; item and NPC infrastructure move to Keystone</h1>
  <p class="lede">Gangland 0.9.0 drops the weapon feature entirely. It returns as <span class="mono">Bartizan-0.1.0.jar</span>, a standalone Keystone plugin the cops-n-crooks and gadget modules depend on. The generic item framework and the Citizens NPC base go upstream into Keystone 1.9.0; turf NPCs move to turf, traders and bankers to their own module. Every step is smoke-tested on the test server from the console.</p>
  <div class="facts">
    <div><div class="k">Branches</div>Gangland <span class="mono">0.9.0</span> off <span class="mono">0.8.4</span> · Keystone phase branch · new repo <span class="mono">Bartizan</span></div>
    <div><div class="k">Agents</div>Opus explores, designs, reviews and tests · Sonnet executes · Fable orchestrates</div>
    <div><div class="k">Test server</div>Paper 1.21.11 · <span class="mono">ServerStartDebug.bat</span> · console-only, in-game left to you</div>
    <div><div class="k">Definition of done</div>gates GA → GE on the board tab</div>
  </div>
</header>
<div id="dash">
<div class="phases">{phase_tiles}</div>
<p class="live" id="live">Status reflects the board at publish time; it updates live when the wave database has newer rows.</p>

<h2 class="sec">Smoke matrix</h2>
<div class="tw"><table class="grid"><thead><tr><th>Row</th><th>modules/</th><th>Expect</th><th>Verdict</th><th>Note</th></tr></thead><tbody>{smoke_rows}</tbody></table></div>

<h2 class="sec">Findings from your runs last night</h2>
<div class="tw"><table class="grid"><thead><tr><th>Docket</th><th>Tier</th><th>Finding</th><th>Status</th><th>Note</th></tr></thead><tbody>{finding_rows}</tbody></table></div>
<p class="live">Full entries with evidence and fix direction: the <a href="https://claude.ai/code/artifact/4102fb1f-20b0-44e9-b1b7-2893a559fa04">Gangland Warfare Bug Docket</a> (T-12 … T-18).</p>

<h2 class="sec">Agent feedback</h2>
<div class="fb" id="fb"><div class="empty">No agent reports yet.</div></div>
</div>
{sections}
</main>
</div>
<script>{js}</script>
"""
OUT.write_text(page, encoding="utf-8")
print(OUT, len(page) // 1024, "KB")
