import re, json, html, glob, os, sys
sys.stdout.reconfigure(encoding="utf-8")
SRC = r"E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/workflow-audit-2026-09-02"
BOARD = r"C:\Users\Hashim\.claude\projects\E--Programming-java-Gangland-Warfare--Cubed-GTA-recoded-\4f4d911f-4bbd-44ed-863f-eae6c7c38496\tool-results\artifact-737f910d-1788424445-3122.html"
OUT = os.path.dirname(os.path.abspath(__file__))
URLMAP = {
 "5726570b-bd6c-4c4e-ac33-0f86befcb011":"core-lifecycle",
 "d2dbe24f-1d3f-46ca-9353-0c2775157df9":"commands-messages-platform",
 "c5d60743-8bf7-4897-b357-74354f2457fb":"items-unique",
 "7ad4b853-5bb5-412c-9e96-0e3f465f9836":"ui-inventory-scoreboard",
 "3ca10fb3-4772-4439-94c1-cec3e53af294":"users-levels-economy-bank",
 "c0ae92aa-f60d-4ab4-8776-0c0f512eca2b":"gangs-ranks-mail",
 "dbb55def-1066-4c70-b09d-72c22ea63df2":"wanted-bounty-combat",
 "7b91103f-1e4a-45b8-91eb-25e5c47775c1":"cops-detainment-jail",
 "dad8da79-b977-4cdc-9712-e080406cb0b5":"civilians-traders-shops",
 "7a132f36-b2ce-48a2-8263-4eff270282ed":"turf",
 "48a0fd29-5ad2-43ea-b05f-5d9c38e82196":"weapons",
 "a3c799ae-5752-467c-b9b2-26e7e29e7882":"gadgets-cars-fuel-jetpack",
 "65f0d8f2-4c5b-418c-97b3-23d619d82473":"lootchests-signs-waypoints",
}
RATINGS = {"high","medium","low","medium-high","low-medium","high (unverified)","low (unverified)"}
def norm(s):
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    s = re.sub(r"[`*_]", "", s)
    s = re.sub(r"\s+", " ", s).strip().lower()
    return s
def split_row(line):
    # split on | not inside backticks
    cells, cur, tick = [], "", False
    for ch in line:
        if ch == "`": tick = not tick
        if ch == "|" and not tick:
            cells.append(cur); cur = ""
        else: cur += ch
    cells.append(cur)
    cells = [c.strip() for c in cells]
    if cells and cells[0] == "": cells = cells[1:]
    if cells and cells[-1] == "": cells = cells[:-1]
    return cells
obs = []
for f in sorted(glob.glob(os.path.join(glob.escape(SRC), "*.md"))):
    slug = os.path.basename(f)[:-3]
    if slug == "README": continue
    lines = open(f, encoding="utf-8").read().splitlines()
    p = False
    for ln in lines:
        if ln.startswith("## ") and "Observations" in ln: p = True; continue
        if p and ln.startswith("## "): p = False
        if p and re.match(r"^\|\s*\d+\s*\|", ln):
            c = split_row(ln)
            num = int(c[0]); loc = c[1]; text = c[2]
            rest = c[3:]
            rec = {"sys": slug, "num": num, "location": loc, "observation": text, "impact": None, "risk": None, "confidence": None}
            if len(rest) == 2 and rest[0].strip().lower() in RATINGS:
                rec["risk"] = rest[0]; rec["confidence"] = rest[1]
            elif len(rest) == 2:
                rec["impact"] = rest[0]; rec["confidence"] = rest[1]
            elif len(rest) == 3:
                rec["impact"] = rest[0]; rec["risk"] = rest[1]; rec["confidence"] = rest[2]
            else:
                rec["extra"] = rest
            obs.append(rec)
print("md rows:", len(obs))
# board
bh = open(BOARD, encoding="utf-8").read()
rows = re.findall(r'<tr class="row-([a-z]+)"><td>(.*?)</td><td><a href="https://claude.ai/code/artifact/([0-9a-f-]+)#[^"]*"[^>]*>(.*?)</a></td><td>(.*?)</td><td>(.*?)</td><td>(.*?)</td></tr>', bh, flags=re.S)
print("board rows:", len(rows))
board = []
for cls, riskcell, aid, area, loc, text, conf in rows:
    m = re.search(r'</span>\s*([^<]+)</span>', riskcell)
    risk = m.group(1).strip() if m else cls
    board.append({"sys": URLMAP.get(aid, aid), "area": html.unescape(area), "risk": risk, "loc": norm(loc), "text": norm(text), "conf": html.unescape(re.sub(r"<[^>]+>","",conf)).strip()})
# match
by_sys = {}
for b in board: by_sys.setdefault(b["sys"], []).append(b)
unmatched = 0
for o in obs:
    cands = by_sys.get(o["sys"], [])
    key = norm(o["observation"])[:70]
    hit = None
    for b in cands:
        if b.get("used"): continue
        if b["text"][:70] == key: hit = b; break
    if not hit:
        lk = norm(o["location"])[:60]
        for b in cands:
            if b.get("used"): continue
            if b["loc"][:60] == lk: hit = b; break
    if hit:
        hit["used"] = True
        o["board_risk"] = hit["risk"]; o["area"] = hit["area"]
        if not o["confidence"]: o["confidence"] = hit["conf"] or None
    else:
        unmatched += 1; o["board_risk"] = None
print("unmatched:", unmatched)
from collections import Counter
print(Counter(o["board_risk"] for o in obs))
print("unused board rows:", sum(1 for b in board if not b.get("used")))
for b in board:
    if not b.get("used"): print("  UNUSED", b["sys"], b["risk"], b["loc"][:50])
for o in obs:
    if not o["board_risk"]: print("  NOMATCH", o["sys"], o["num"], o["location"][:50])
json.dump(obs, open(os.path.join(OUT, "observations.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
