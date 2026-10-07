#!/usr/bin/env python3
"""Structural check of the CNC overhaul page, compared against a baseline.

Usage: python check_page.py [new.html] [baseline.html]
Defaults: artifact/overhaul-updated.html vs artifact/overhaul-v4.html (paths relative to this file).
Exit code 1 when the new file has a problem the baseline does not have.

Checks (each run on both files; only NEW problems are blamed):
  1 tag balance (stack based, whole document; errors keyed by tag + nearest ancestor id)
  2 duplicate ids
  3 href="#x" without a matching id
  4 graph: every .gn has a card/foundation id, every .ge endpoint is a .gn, edge path endpoints sit on the
    node boxes, node boxes do not overlap and stay inside the viewBox
  5 legend counts (Built/Part built/To build) == computed from .gn status classes; svg aria-label total;
    masthead "N features, M new, K foundations" == computed
  6 inline <script> parses (node --check)
  7 width: every <table> sits inside an overflow-x scroller; inline px widths over 360; headless Chrome at
    360 px: document scrollWidth and any element poking past the viewport outside a scroll container
"""
import os, re, subprocess, sys, tempfile, shutil, json, html
from html.parser import HTMLParser

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.normpath(os.path.join(HERE, "..", "..", "artifact"))
NEW = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ART, "overhaul-updated.html")
OLD = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ART, "overhaul-v4.html")

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"}
SVG_SELF = {"path", "rect", "circle", "line", "use", "stop", "ellipse", "polygon", "polyline"}
# tags whose end tag may legally be omitted in HTML
OPT_END = {"p", "li", "dt", "dd", "tr", "td", "th", "thead", "tbody", "tfoot", "option"}
SCROLLERS = ("table-wrap", "sheet", "graph-wrap", "vtrack-wrap")


class Doc(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []          # (tag, attrs dict, line)
        self.errors = []         # (kind, tag, ancestor id)
        self.ids = []
        self.hrefs = []
        self.nodes = {}          # data-id -> dict(cls, rect)
        self.edges = []          # (from, to, d, cls)
        self.tables = []         # (line, ancestor classes, ancestor id)
        self.inline_w = []
        self.scripts = []
        self.in_script = False
        self._script_buf = []
        self._script_type = ""
        self._script_line = 0
        self._cur_node = None
        self.articles = 0
        self.tag_new = 0

    def anc_id(self):
        for t, a, _ in reversed(self.stack):
            if a.get("id"):
                return a["id"]
        return "(root)"

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        line = self.getpos()[0]
        if "id" in a:
            self.ids.append(a["id"])
        if tag == "a" and a.get("href", "").startswith("#") and len(a["href"]) > 1:
            self.hrefs.append((a["href"][1:], line))
        if tag == "article":
            self.articles += 1
        if tag == "span" and "tag-new" in a.get("class", "").split() and any(t == "article" for t, _, _ in self.stack):
            self.tag_new += 1
        if tag == "table":
            anc = [x for t, at, _ in self.stack for x in at.get("class", "").split()]
            self.tables.append((line, anc, self.anc_id()))
        m = re.search(r"(?<![-\w])(?:min-)?width\s*:\s*(\d+(?:\.\d+)?)px", a.get("style", ""))
        if m and float(m.group(1)) > 360:
            self.inline_w.append((line, a["style"], self.anc_id()))
        cls = a.get("class", "").split()
        if "gn" in cls:
            self._cur_node = a.get("data-id")
            self.nodes[self._cur_node] = {"cls": cls, "rect": None}
        if self._cur_node and tag == "rect" and self.nodes[self._cur_node]["rect"] is None:
            self.nodes[self._cur_node]["rect"] = tuple(float(a[k]) for k in ("x", "y", "width", "height"))
        if "ge" in cls:
            self.edges.append((a.get("data-from"), a.get("data-to"), a.get("d", ""), cls))
        if tag == "script" and "src" not in a:
            self.in_script = True
            self._script_buf = []
            self._script_type = a.get("type", "text/javascript")
            self._script_line = line
        if tag in VOID or tag in SVG_SELF:
            return
        self.stack.append((tag, a, line))

    def handle_startendtag(self, tag, attrs):
        n = len(self.stack)
        self.handle_starttag(tag, attrs)
        if len(self.stack) > n:
            self.stack.pop()

    def handle_endtag(self, tag):
        if tag == "script" and self.in_script:
            self.in_script = False
            if "json" not in self._script_type:
                self.scripts.append((self._script_line, "".join(self._script_buf)))
        if tag in VOID:
            return
        if tag == "a" and self._cur_node:
            self._cur_node = None
        if not self.stack:
            self.errors.append(("unmatched-end", tag, "(root)"))
            return
        if self.stack[-1][0] == tag:
            self.stack.pop()
            return
        for i in range(len(self.stack) - 1, -1, -1):
            if self.stack[i][0] == tag:
                skipped = [t for t, _, _ in self.stack[i + 1:]]
                bad = [t for t in skipped if t not in OPT_END]
                if bad:
                    self.errors.append(("unclosed-before-end", tag + " over " + ",".join(bad), self.anc_id()))
                del self.stack[i:]
                return
        self.errors.append(("unmatched-end", tag, self.anc_id()))

    def handle_data(self, data):
        if self.in_script:
            self._script_buf.append(data)

    def close(self):
        super().close()
        for t, a, line in self.stack:
            if t not in OPT_END and t not in ("html", "body"):
                self.errors.append(("unclosed", t, a.get("id", "line?")))


def parse(path):
    txt = open(path, encoding="utf-8").read()
    d = Doc()
    d.feed(txt)
    d.close()
    return txt, d


def legend_counts(txt):
    out = {}
    for label, key in (("Built", "built"), ("Part built", "partial"), ("To build", "open")):
        m = re.search(r"<li><span class=\"sw sw-%s\"></span>%s \((\d+)\)</li>" % (key, label), txt)
        out[key] = int(m.group(1)) if m else None
    return out


def graph_checks(txt, d):
    problems = []
    cls_count = {"built": 0, "partial": 0, "open": 0}
    for nid, n in d.nodes.items():
        for k in cls_count:
            if "s-" + k in n["cls"]:
                cls_count[k] += 1
    leg = legend_counts(txt)
    for k in cls_count:
        if leg[k] != cls_count[k]:
            problems.append(("legend-count", "%s legend=%s computed=%s" % (k, leg[k], cls_count[k])))
    m = re.search(r'aria-label="Dependency graph: (\d+) features and foundations', txt)
    if not m or int(m.group(1)) != len(d.nodes):
        problems.append(("svg-aria-total", "aria=%s nodes=%d" % (m.group(1) if m else None, len(d.nodes))))
    feats = sum(1 for n in d.nodes.values() if "k-feature" in n["cls"])
    found = sum(1 for n in d.nodes.values() if "k-enabler" in n["cls"])
    m = re.search(r"<li><b>(\d+)</b> features, <b>(\d+)</b> new</li>\s*<li><b>(\d+)</b> foundations</li>", txt)
    if m:
        f, nw, fo = map(int, m.groups())
        if f != d.articles:
            problems.append(("masthead-features", "says %d, <article> count %d" % (f, d.articles)))
        if f != feats:
            problems.append(("masthead-features-vs-graph", "says %d, k-feature nodes %d" % (f, feats)))
        if nw != d.tag_new:
            problems.append(("masthead-new", "says %d, tag-new spans inside <article> %d" % (nw, d.tag_new)))
        if fo != found:
            problems.append(("masthead-foundations", "says %d, k-enabler nodes %d" % (fo, found)))
    else:
        problems.append(("masthead", "facts-line not found"))
    ids = set(d.ids)
    for nid in d.nodes:
        if nid not in ids:
            problems.append(("node-without-card", nid))
    for f, t, dd, c in d.edges:
        for e, role in ((f, "from"), (t, "to")):
            if e not in d.nodes:
                problems.append(("edge-dangling", "%s->%s: %s %s is not a node" % (f, t, role, e)))
    seen = set()
    for f, t, dd, c in d.edges:
        if (f, t) in seen:
            problems.append(("edge-duplicate", "%s->%s" % (f, t)))
        seen.add((f, t))
    for f, t, dd, c in d.edges:
        mm = re.match(r"M([\d.]+),([\d.]+) C[\d.]+,[\d.]+ [\d.]+,[\d.]+ ([\d.]+),([\d.]+)$", dd.strip())
        if not mm:
            problems.append(("edge-path-unparsed", "%s->%s d=%s" % (f, t, dd)))
            continue
        if f not in d.nodes or t not in d.nodes or not d.nodes[f]["rect"] or not d.nodes[t]["rect"]:
            continue
        x1, y1, x2, y2 = map(float, mm.groups())
        for (x, y, nid) in ((x1, y1, f), (x2, y2, t)):
            r = d.nodes[nid]["rect"]
            ymid = r[1] + r[3] / 2
            onx = abs(x - r[0]) < 6 or abs(x - (r[0] + r[2])) < 6
            ony = abs(y - ymid) < 6
            if not (onx and ony):
                problems.append(("edge-endpoint-off-node", "%s->%s end (%s,%s) not at box edge of %s %s" % (f, t, x, y, nid, r)))
    vb = re.search(r'<svg class="graph" viewBox="0 0 (\d+) (\d+)"', txt)
    W, H = (int(vb.group(1)), int(vb.group(2))) if vb else (None, None)
    items = [(n, v["rect"]) for n, v in d.nodes.items() if v["rect"]]
    for i in range(len(items)):
        n1, a = items[i]
        if W and (a[0] + a[2] > W or a[1] + a[3] > H):
            problems.append(("node-outside-viewbox", n1))
        for j in range(i + 1, len(items)):
            n2, b = items[j]
            if a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]:
                problems.append(("node-overlap", "%s %s" % (n1, n2)))
    return problems, cls_count, leg


def script_checks(d):
    out = []
    tmp = tempfile.mkdtemp()
    try:
        for i, (line, src) in enumerate(d.scripts):
            p = os.path.join(tmp, "s%d.js" % i)
            open(p, "w", encoding="utf-8").write(src)
            r = subprocess.run(["node", "--check", p], capture_output=True, text=True)
            if r.returncode:
                out.append(("script-syntax", "script at line %d: %s" % (line, r.stderr.strip().splitlines()[-1] if r.stderr else "?")))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return out


PROBE = r"""<script>
window.addEventListener('load', function () {
  function scroller(el) {
    for (var p = el.parentElement; p; p = p.parentElement) {
      var o = getComputedStyle(p).overflowX;
      if (o === 'auto' || o === 'scroll' || o === 'hidden') { return p; }
    }
    return null;
  }
  var vw = document.documentElement.clientWidth, out = [];
  document.querySelectorAll('body *').forEach(function (el) {
    var r = el.getBoundingClientRect();
    if (r.width && r.right > vw + 1 && !scroller(el)) {
      var id = ''; for (var p = el; p; p = p.parentElement) { if (p.id) { id = p.id; break; } }
      out.push(el.tagName.toLowerCase() + '.' + (el.getAttribute('class') || '') + ' right=' + Math.round(r.right) + ' in #' + id);
    }
  });
  var clip = [];
  document.querySelectorAll('.rel-v, .rel-name, .st, .rel-deps li, .vt-v, .meta dd').forEach(function (el) {
    if (el.scrollWidth > el.clientWidth + 1 && el.clientWidth > 0) { clip.push(el.className + ' "' + el.textContent.trim().slice(0, 30) + '" scroll=' + el.scrollWidth + ' client=' + el.clientWidth); }
  });
  parent.postMessage(JSON.stringify({clip: clip.slice(0, 20), vw: vw, inner: innerWidth, sw: document.documentElement.scrollWidth, bw: document.body.scrollWidth, off: out.slice(0, 40)}), '*');
});
</script>"""


def chrome_probe(path, width):
    chrome = next((c for c in (r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                               r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe") if os.path.exists(c)), None)
    if not chrome:
        return None
    tmp = tempfile.mkdtemp()
    try:
        txt = open(path, encoding="utf-8").read().replace("</body>", PROBE + "</body>")
        p = os.path.join(tmp, "page.html")
        open(p, "w", encoding="utf-8").write(txt)
        # headless Chrome will not shrink below ~500 px, so the page is loaded in an iframe of the target width
        w = os.path.join(tmp, "wrap.html")
        open(w, "w", encoding="utf-8").write(
            '<!doctype html><body style="margin:0"><iframe src="page.html" width="%d" height="900" style="border:0"></iframe>'
            '<pre id="__probe"></pre><script>addEventListener("message",function(e){document.getElementById("__probe").textContent=e.data;});</script>' % width)
        r = subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-first-run",
                            "--user-data-dir=" + os.path.join(tmp, "prof"), "--window-size=600,1000",
                            "--virtual-time-budget=6000", "--dump-dom", "file:///" + w.replace("\\", "/")],
                           capture_output=True, text=True, timeout=120, encoding="utf-8", errors="replace")
        m = re.search(r'<pre id="__probe">(.*?)</pre>', r.stdout, re.S)
        if not m:
            return {"error": "probe did not run"}
        return json.loads(html.unescape(m.group(1)))
    except Exception as e:
        return {"error": str(e)}
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def run(path):
    txt, d = parse(path)
    res = {}
    res["tags"] = sorted(set(d.errors))
    dup = sorted({i for i in d.ids if d.ids.count(i) > 1})
    res["dup-ids"] = [("dup-id", i) for i in dup]
    idset = set(d.ids)
    res["dead-links"] = sorted({("dead-href", h) for h, _ in d.hrefs if h not in idset})
    gp, cc, leg = graph_checks(txt, d)
    res["graph"] = gp
    res["scripts"] = script_checks(d)
    w = []
    for line, anc, aid in d.tables:
        if not any(c in SCROLLERS for c in anc):
            w.append(("table-not-in-scroller", "table near #%s" % aid))
    for line, st, aid in d.inline_w:
        w.append(("inline-width", "%s near #%s" % (st, aid)))
    res["width-static"] = w
    wp = []
    for width_probe in (360, 1000):
        probe = chrome_probe(path, width_probe)
        if probe is None:
            wp.append(("no-browser", "Chrome/Edge not found; computed-layout check skipped"))
            break
        if "error" in probe:
            wp.append(("probe-failed", "%dpx: %s" % (width_probe, probe["error"])))
            continue
        if probe["inner"] > width_probe + 5:
            wp.append(("probe-viewport", "window did not shrink: innerWidth=%d" % probe["inner"]))
        elif probe["sw"] > probe["vw"] + 1 or probe["bw"] > probe["vw"] + 1:
            wp.append(("page-h-scroll", "%dpx: scrollWidth=%d body=%d viewport=%d" % (width_probe, probe["sw"], probe["bw"], probe["vw"])))
        for o in probe.get("off", []):
            wp.append(("element-past-viewport", "%dpx: %s" % (width_probe, o)))
        for o in probe.get("clip", []):
            wp.append(("text-clipped", "%dpx: %s" % (width_probe, o)))
        wp.append(("info", "probe %dpx vw=%s scrollWidth=%s" % (width_probe, probe["vw"], probe["sw"])))
    res["width-layout"] = wp
    info = {"nodes": len(d.nodes), "edges": len(d.edges), "legend": leg, "computed": cc,
            "articles": d.articles, "tag_new": d.tag_new, "scripts": len(d.scripts), "ids": len(d.ids)}
    return res, info


def main():
    new, ni = run(NEW)
    old, oi = run(OLD)
    print("new:", os.path.basename(NEW), ni)
    print("old:", os.path.basename(OLD), oi)
    blame = 0
    for k in new:
        oldset = set(old[k])
        fresh = [p for p in new[k] if p not in oldset and p[0] != "info"]
        pre = [p for p in new[k] if p in oldset]
        print("\n[%s] total=%d preexisting=%d NEW=%d" % (k, len(new[k]), len(pre), len(fresh)))
        for p in new[k]:
            if p[0] == "info":
                print("  info ", p[1])
        for p in fresh:
            print("  NEW  ", p)
            blame += 1
        for p in pre[:5]:
            print("  (old)", p)
    print("\nNEW problems:", blame)
    sys.exit(1 if blame else 0)


if __name__ == "__main__":
    main()
