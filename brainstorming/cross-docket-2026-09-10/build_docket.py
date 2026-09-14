"""Cross-project bug docket builder (2026-09-10).

Inputs
  ../bug-docket-2026-09-06/bugs.json            Gangland entries (carried over unchanged)
  ../bug-docket-2026-09-06/docket_template.html Base page; patched here into docket_template.html
  <project>/systems.md                          phase-1 system table (| Code | Slug | Name | ... |)
  <project>/findings/<slug>.txt                 phase-2 verified findings
                                                num~~tier~~title~~fix~~tests~~location~~observation~~confidence
Outputs
  bugs.json, docket_template.html, cross-project-bug-docket.html
Run:  python build_docket.py
"""
import json, os, re, subprocess, sys, collections
sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
OLD = os.path.join(HERE, "..", "bug-docket-2026-09-06")

PROJECTS = collections.OrderedDict([
    # key, (prefix, display name, order, repo path, build/verify hint)
    ("gangland", ("",   "Gangland Warfare", "1", r"E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]", "mvn -pl <module> -am test")),
    ("keystone", ("KS", "Keystone",         "2", r"E:\Programming\java\Keystone",  "mvn -pl <module> test (then mvn clean install so consumers see it)")),
    ("bartizan", ("BZ", "Bartizan",         "3", r"E:\Programming\java\Bartizan",  "mvn -pl bartizan-plugin -am test")),
    ("oriel",    ("OR", "Oriel",            "4", r"E:\Programming\java\Oriel",     "./gradlew :<module>:test")),
])

def git(path, *args):
    try:
        return subprocess.run(["git", *args], cwd=path, capture_output=True, text=True, timeout=20).stdout.strip()
    except Exception:
        return "?"

def head_meta():
    out = {}
    for key, (_, name, _, repo, _) in PROJECTS.items():
        out[key] = {"branch": git(repo, "branch", "--show-current"), "head": git(repo, "rev-parse", "--short", "HEAD")}
    return out

def parse_systems(project):
    """Return OrderedDict slug -> (code, name, order) from systems.md's first table."""
    p = os.path.join(HERE, project, "systems.md")
    systems = collections.OrderedDict()
    if not os.path.exists(p):
        return systems
    n = 0
    for ln in open(p, encoding="utf-8"):
        if not ln.startswith("|"):
            continue
        cells = [c.strip() for c in ln.strip().strip("|").split("|")]
        if len(cells) < 3 or cells[0] in ("Code", "") or set(cells[0]) <= set("-: "):
            continue
        code, slug, name = cells[0], cells[1], cells[2]
        if not re.fullmatch(r"[A-Z]{2}", code):
            continue
        n += 1
        systems[slug] = (code, name, "%02d" % n)
    return systems

def load_findings(project):
    prefix, pname, porder, _, _ = PROJECTS[project]
    systems = parse_systems(project)
    bugs, problems = [], []
    fdir = os.path.join(HERE, project, "findings")
    for slug, (code, name, order) in systems.items():
        p = os.path.join(fdir, slug + ".txt")
        if not os.path.exists(p):
            problems.append("%s: no findings file for %s" % (project, slug)); continue
        for raw in open(p, encoding="utf-8").read().splitlines():
            if not raw.strip() or raw.startswith("#"):
                continue
            parts = raw.split("~~")
            if len(parts) < 8:
                problems.append("%s/%s: %d fields: %s" % (project, slug, len(parts), raw[:80])); continue
            num, tier, title, fix, tests, location, observation, confidence = [x.strip() for x in parts[:8]]
            if tier not in ("P0", "P1", "P2", "P3", "X"):
                problems.append("%s/%s #%s bad tier %s" % (project, slug, num, tier)); continue
            bugs.append({
                "id": "%s-%s-%02d" % (prefix, code, int(num)),
                "project": project, "projectName": pname,
                "code": code, "sys": project + "/" + slug, "sysName": pname + " · " + name,
                "sysOrder": porder + "." + order, "num": int(num), "tier": tier,
                "title": title, "fix": fix, "tests": None if tests in ("-", "") else tests,
                "location": location, "observation": observation, "impact": None,
                "risk": None, "confidence": confidence, "unverified": confidence == "Low",
                "recheck": False, "case": None, "source": "cross-project scan 2026-09-10",
            })
    ids = collections.Counter(b["id"] for b in bugs)
    for i, c in ids.items():
        if c > 1:
            problems.append("duplicate id " + i)
    return bugs, problems

def load_gangland():
    _, pname, porder, _, _ = PROJECTS["gangland"]
    out = []
    for b in json.load(open(os.path.join(OLD, "bugs.json"), encoding="utf-8")):
        b = dict(b)
        b["project"] = "gangland"; b["projectName"] = pname
        b["sys"] = "gangland/" + b["sys"]; b["sysName"] = pname + " · " + b["sysName"]
        b["sysOrder"] = porder + "." + str(b["sysOrder"])
        b["num"] = int(b["num"])
        out.append(b)
    return out

def patch_template(meta, counts):
    t = open(os.path.join(OLD, "docket_template.html"), encoding="utf-8").read()
    def rep(old, new, count=1):
        nonlocal t
        assert t.count(old) >= 1, "anchor missing: " + old[:70]
        t = t.replace(old, new, count)
    hm = " · ".join("%s %s@%s" % (PROJECTS[k][1], meta[k]["branch"], meta[k]["head"]) for k in PROJECTS)
    rep("<title>Gangland Warfare Bug Docket</title>", "<title>LuckyRaven Bug Docket</title>")
    rep('<span class="tb-title">Gangland Warfare</span>', '<span class="tb-title">Gangland · Keystone · Bartizan · Oriel</span>')
    rep('<div class="tb-meta"><span>branch 0.8.2</span><span class="dot"></span><span>HEAD 66eeb647</span><span class="dot"></span><span>Keystone 1.8.0</span><span class="dot"></span><span>audit 2026-09-02 · tests 2026-09-04 · docket 2026-09-06</span></div>',
        '<div class="tb-meta"><span>' + hm + '</span><span class="dot"></span><span>docket 2026-09-10</span></div>')
    rep('<span class="eyebrow">Bug docket · every observation from the 13 case files, triaged</span>',
        '<span class="eyebrow">Cross-project bug docket · four repositories, one warrant list</span>')
    rep("<h1>What has to be fixed, in the order it should be fixed</h1>", "<h1>What has to be fixed across the four plugins, in the order it should be fixed</h1>")
    rep('<p class="lede">Each entry is one observation from the 2026-09-02 workflow audit, re-read with the 2026-09-04 test suite beside it and given a fix tier, a one-line fix direction and the test that pins it. Open an entry, copy its brief, hand it to an agent, then mark it fixed here so the next agent sees the live state.</p>',
        '<p class="lede">Gangland Warfare keeps its 2026-09-06 docket entries and their live statuses. Keystone, Bartizan and Oriel were scanned on 2026-09-10: a mapper split each repository into systems, a review lead ordered one scanner per system, then re-read every finding against the code before it entered this list. Filter by project, open an entry, copy its brief, hand it to an agent, then mark it fixed here so the next agent sees the live state.</p>')
    rep('<p class="fine">P0 and P1 are the immediate docket:', '<p class="fine">Counts: ' + counts + '. P0 and P1 are the immediate docket:')
    rep(' Line numbers were re-verified on 0.8.1 on 2026-09-03; the only source change since is the mail module split, and the entries it moved are marked <em>re-checked 0.8.2</em>.</p>',
        ' Gangland line numbers date from its own docket (0.8.1/0.8.2); the other three projects cite the HEADs in the top bar. A dashed <em>unverified</em> pill means the lead kept the finding at low confidence — confirm in code first.</p>')
    # project chips
    chips = "".join('<button class="chip c-proj" data-proj="%s" aria-pressed="true">%s</button>' % (k, PROJECTS[k][1]) for k in PROJECTS)
    rep('<span class="ctl-label">Tier</span>', '<span class="ctl-label">Project</span><span class="chipset" id="projChips">' + chips + '</span>\n    <span class="ctl-label">Tier</span>')
    rep('<option value="sys">by system</option>', '<option value="sys">by system</option><option value="proj">by project</option>')
    rep('<span>Sources: <code>brainstorming/workflow-audit-2026-09-02/*.md</code> (observation tables), the 2026-09-04 test commits (bc0be094 … 7a8a75f5), <code>documentation/TESTING.md</code>.</span>',
        '<span>Sources: Gangland <code>brainstorming/bug-docket-2026-09-06/</code>; the other three projects <code>brainstorming/cross-docket-2026-09-10/&lt;project&gt;/</code> (systems.md, findings/, REVIEW.md) in the Gangland repo.</span>')
    # css
    rep(".pill-unv { border: 1px dashed var(--line); color: var(--muted); }",
        ".pill-unv { border: 1px dashed var(--line); color: var(--muted); }\n.pill-proj { background: var(--surface-2); border: 1px solid var(--line-2); color: var(--muted); }\n.chip.c-proj[aria-pressed=\"true\"] { background: var(--accent); border-color: var(--accent); color: var(--accent-ink); }")
    # js: systems carry project
    rep('SYSTEMS.push({ slug: b.sys, name: b.sysName, order: b.sysOrder, code: b.code === "T" ? null : b.code });',
        'SYSTEMS.push({ slug: b.sys, name: b.sysName, order: b.sysOrder, project: b.project, code: b.code === "T" ? null : b.code });')
    rep('var state = { q: "", tiers: { P0: true, P1: true, P2: true, P3: true, X: false }, sys: "", status: "", testOnly: false, group: "tier", open: {} };',
        'var PROJECTS = ' + json.dumps([[k, PROJECTS[k][1]] for k in PROJECTS]) + ';\n  var REPO_LINE = ' + json.dumps({k: "Repo: %s (branch %s, HEAD %s), Keystone-based, Spigot API only. Run graphify query first, then open the cited files. Verify with %s." % (PROJECTS[k][3], meta[k]["branch"], meta[k]["head"], PROJECTS[k][4]) for k in PROJECTS}) + ';\n'
        '  var state = { q: "", projects: { gangland: true, keystone: true, bartizan: true, oriel: true }, tiers: { P0: true, P1: true, P2: true, P3: true, X: false }, sys: "", status: "", testOnly: false, group: "tier", open: {} };')
    rep('localStorage.getItem("gw-docket-filters")', 'localStorage.getItem("xp-docket-filters")')
    rep('if (saved && saved.tiers) { state.tiers = saved.tiers;', 'if (saved && saved.tiers) { state.tiers = saved.tiers; if (saved.projects) state.projects = saved.projects;')
    rep('localStorage.setItem("gw-docket-filters", JSON.stringify({ tiers: state.tiers,', 'localStorage.setItem("xp-docket-filters", JSON.stringify({ tiers: state.tiers, projects: state.projects,')
    rep('localStorage.getItem("gw-docket-status")', 'localStorage.getItem("xp-docket-status")')
    rep('localStorage.setItem("gw-docket-status"', 'localStorage.setItem("xp-docket-status"')
    # brief
    rep('lines.push("GANGLAND WARFARE FIX BRIEF — " + b.id + " (" + tierLabel(b.tier) + ")");',
        'lines.push(b.projectName.toUpperCase() + " FIX BRIEF — " + b.id + " (" + tierLabel(b.tier) + ")");')
    rep('lines.push("System: " + b.sysName + " · audit source " + b.source + (b.code === "T" ? "" : ", observation #" + b.num) + " · audit risk " + (b.risk || "n/a") + " · tracer confidence " + (b.confidence || "n/a") + (b.unverified ? " (marked unverified — confirm in code first)" : ""));',
        'lines.push("System: " + b.sysName + " · source " + b.source + (b.project === "gangland" && b.code !== "T" ? ", observation #" + b.num : "") + (b.risk ? " · audit risk " + b.risk : "") + " · confidence " + (b.confidence || "n/a") + (b.unverified ? " (marked unverified — confirm in code first)" : ""));')
    rep('lines.push("Repo: branch 0.8.2 (HEAD 66eeb647), Keystone 1.8.0, Spigot API only. Read brainstorming/workflow-audit-2026-09-02/" + (b.code === "T" ? b.sys + ".md" : b.source) + " for the workflow context, run graphify query first, then open the cited files. Verify with mvn -pl <module> -am test.");',
        'lines.push(REPO_LINE[b.project] + (b.project === "gangland" ? " Workflow context: brainstorming/workflow-audit-2026-09-02/" + (b.code === "T" ? b.sys.replace("gangland/", "") + ".md" : b.source) + "." : " Lead review notes: brainstorming/cross-docket-2026-09-10/" + b.project + "/REVIEW.md."));')
    rep('lines.push("House rules: method braces on their own lines; Lombok @CustomLog for logging; SoundEffect/XSeries for version-drifting enums; never io.papermc imports; new commands need commands.json entries.");',
        'lines.push("House rules: read the repo\'s CLAUDE.md first. Method braces on their own lines; Lombok @CustomLog for logging; SoundEffect/XSeries for version-drifting enums; never io.papermc imports; record the fix in the cross-project docket (status + note) when done.");')
    # filtering
    rep('if (!state.tiers[b.tier]) return false;', 'if (!state.projects[b.project]) return false;\n      if (!state.tiers[b.tier]) return false;')
    rep('var hay = (b.id + " " + b.title + " " + b.location', 'var hay = (b.id + " " + b.projectName + " " + b.title + " " + b.location')
    # row pills
    rep("var meta = '<span class=\"pill pill-sys\">' + esc(b.sysName) + '</span>' +",
        "var meta = '<span class=\"pill pill-proj\">' + esc(b.projectName) + '</span><span class=\"pill pill-sys\">' + esc(b.sysName.replace(b.projectName + \" · \", \"\")) + '</span>' +")
    rep("'<dt>Audit</dt><dd>' + (b.code === \"T\" ? 'Found while writing the test suite (2026-09-04); not in the audit tables. Case file: <a href=\"' + b.case + '\" target=\"_blank\" rel=\"noopener\">' + esc(b.sysName) + '</a>' : '<a href=\"' + b.case + '\" target=\"_blank\" rel=\"noopener\">' + esc(b.source) + ' · observation #' + b.num + '</a> · risk ' + esc(b.risk || \"n/a\") + ' · confidence ' + esc(b.confidence)) + '</dd>' +",
        "'<dt>Source</dt><dd>' + (!b.case ? esc(b.source) + ' · scanner + lead re-read · confidence ' + esc(b.confidence || \"n/a\") : b.code === \"T\" ? 'Found while writing the test suite (2026-09-04); not in the audit tables. Case file: <a href=\"' + b.case + '\" target=\"_blank\" rel=\"noopener\">' + esc(b.sysName) + '</a>' : '<a href=\"' + b.case + '\" target=\"_blank\" rel=\"noopener\">' + esc(b.source) + ' · observation #' + b.num + '</a> · risk ' + esc(b.risk || \"n/a\") + ' · confidence ' + esc(b.confidence)) + '</dd>' +")
    # grouping by project
    rep('    } else {\n      SYSTEMS.forEach(function (s) { var g = list.filter(function (b) { return b.sys === s.slug; });',
        '    } else if (state.group === "proj") {\n      PROJECTS.forEach(function (p) { var g = list.filter(function (b) { return b.project === p[0]; }); if (g.length) { var c = {}; g.forEach(function (b) { c[b.tier] = (c[b.tier] || 0) + 1; }); groups.push({ key: p[0], name: p[1], desc: ["P0", "P1", "P2", "P3", "X"].filter(function (k) { return c[k]; }).map(function (k) { return c[k] + " " + (k === "X" ? "withdrawn" : k); }).join(" · "), items: g }); } });\n    } else {\n      SYSTEMS.forEach(function (s) { var g = list.filter(function (b) { return b.sys === s.slug; });')
    # chips sync + sys select rebuilt per project selection
    rep('document.getElementById("testChip").setAttribute("aria-pressed", String(state.testOnly));',
        'document.getElementById("testChip").setAttribute("aria-pressed", String(state.testOnly));\n    Array.prototype.forEach.call(document.querySelectorAll("#projChips .chip"), function (c) { c.setAttribute("aria-pressed", String(!!state.projects[c.getAttribute("data-proj")])); });\n    fillSys();')
    rep('  var sysSel = document.getElementById("sysSel");\n  SYSTEMS.forEach(function (s) { var o = document.createElement("option"); o.value = s.slug; o.textContent = s.order + " · " + s.name; sysSel.appendChild(o); });',
        '  var sysSel = document.getElementById("sysSel");\n  function fillSys() { var cur = state.sys; sysSel.innerHTML = \'<option value="">All systems</option>\'; SYSTEMS.forEach(function (s) { if (!state.projects[s.project]) return; var o = document.createElement("option"); o.value = s.slug; o.textContent = s.name; sysSel.appendChild(o); }); if (cur && !SYSTEMS.some(function (s) { return s.slug === cur && state.projects[s.project]; })) { state.sys = ""; } sysSel.value = state.sys; }\n  Array.prototype.forEach.call(document.querySelectorAll("#projChips .chip"), function (c) { c.addEventListener("click", function () { var k = c.getAttribute("data-proj"); var only = PROJECTS.every(function (p) { return state.projects[p[0]] === (p[0] === k); }); if (only) { PROJECTS.forEach(function (p) { state.projects[p[0]] = true; }); } else { PROJECTS.forEach(function (p) { state.projects[p[0]] = p[0] === k; }); } persistFilters(); syncChips(); render(); }); });')
    rep('document.getElementById("sysSel").value = state.sys;\n', '')
    # fifth status: moved (code left the project; tracked under another project's id)
    rep('var STATUSES = [["open", "Open"], ["progress", "In progress"], ["fixed", "Fixed"], ["wontfix", "Won\'t fix"]];',
        'var STATUSES = [["open", "Open"], ["progress", "In progress"], ["fixed", "Fixed"], ["wontfix", "Won\'t fix"], ["moved", "Moved"]];')
    rep('<option value="wontfix">Won\'t fix</option><option value="unfinished">Not yet fixed</option>',
        '<option value="wontfix">Won\'t fix</option><option value="moved">Moved</option><option value="unfinished">Not yet fixed</option>')
    rep('if (state.status === "unfinished") { if (st === "fixed" || st === "wontfix") return false; }',
        'if (state.status === "unfinished") { if (st === "fixed" || st === "wontfix" || st === "moved") return false; }')
    open(os.path.join(HERE, "docket_template.html"), "w", encoding="utf-8").write(t)
    return t

CROSSREF = re.compile(r"(?:was|also)\s+Gangland\s+([A-Z]{1,2}-\d{1,2})", re.I)

def seed_statuses(bugs):
    """Old artifact rows + derived rows for Gangland entries whose code moved to another project.
    Existing db rows are never overridden. Written to status-seed-out.json for write_db batches."""
    seed = json.load(open(os.path.join(HERE, "gangland", "status-seed.json"), encoding="utf-8"))
    by_id = {b["id"]: b for b in bugs}
    derived = {}
    for b in bugs:
        if b["project"] == "gangland":
            continue
        for ref in CROSSREF.findall(b["observation"] or ""):
            code, num = ref.split("-"); gid = "%s-%02d" % (code.upper(), int(num))
            if gid not in by_id or gid in seed:
                continue
            m = re.search(r"\bwas\s+Gangland\s+" + re.escape(ref), b["observation"], re.I)
            if m and code.upper() == "WP":
                derived.setdefault(gid, {"status": "moved", "note": "Code moved to Bartizan in 0.9.0; tracked as " + b["id"] + ".", "updatedAt": "2026-09-10"})
            else:
                derived.setdefault(gid, {"status": "open", "note": "Also filed as " + b["id"] + " (root cause there).", "updatedAt": "2026-09-10"})
    extra = json.load(open(os.path.join(HERE, "gangland", "status-extra.json"), encoding="utf-8")) if os.path.exists(os.path.join(HERE, "gangland", "status-extra.json")) else {}
    for k, v in extra.items():
        if k not in seed:
            derived[k] = v
    out = dict(seed); out.update(derived)
    json.dump(out, open(os.path.join(HERE, "status-seed-out.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    return len(seed), len(derived)

def main():
    bugs = load_gangland()
    problems = []
    for p in ("keystone", "bartizan", "oriel"):
        b, pr = load_findings(p); bugs += b; problems += pr
    meta = head_meta()
    per = collections.OrderedDict()
    for k in PROJECTS:
        c = collections.Counter(b["tier"] for b in bugs if b["project"] == k)
        per[k] = c
    counts = "; ".join("%s %d (P0 %d · P1 %d · P2 %d · P3 %d)" % (PROJECTS[k][1], sum(c.values()), c["P0"], c["P1"], c["P2"], c["P3"]) for k, c in per.items())
    json.dump(bugs, open(os.path.join(HERE, "bugs.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    t = patch_template(meta, counts)
    data = json.dumps(bugs, ensure_ascii=False).replace("</", "<\\/")
    open(os.path.join(HERE, "cross-project-bug-docket.html"), "w", encoding="utf-8").write(t.replace("/*__DATA__*/", data))
    print("entries:", len(bugs)); print(counts)
    print("status seed: old rows %d, derived rows %d" % seed_statuses(bugs))
    for p in problems:
        print("PROBLEM:", p)

if __name__ == "__main__":
    main()
