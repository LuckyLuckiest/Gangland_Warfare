import json, os, sys, re, collections
sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
obs = json.load(open(os.path.join(HERE, "observations.json"), encoding="utf-8"))

SYS = collections.OrderedDict([
    ("core-lifecycle",            ("CL", "Core Lifecycle", "01", "5726570b-bd6c-4c4e-ac33-0f86befcb011")),
    ("commands-messages-platform",("CM", "Commands & Messages", "02", "d2dbe24f-1d3f-46ca-9353-0c2775157df9")),
    ("items-unique",              ("IT", "Item Framework", "03", "c5d60743-8bf7-4897-b357-74354f2457fb")),
    ("ui-inventory-scoreboard",   ("UI", "Inventory, Scoreboard & Holograms", "04", "7ad4b853-5bb5-412c-9e96-0e3f465f9836")),
    ("users-levels-economy-bank", ("US", "Users, Economy & Banking", "05", "3ca10fb3-4772-4439-94c1-cec3e53af294")),
    ("gangs-ranks-mail",          ("GR", "Gangs, Ranks & Mail", "06", "c0ae92aa-f60d-4ab4-8776-0c0f512eca2b")),
    ("wanted-bounty-combat",      ("WB", "Wanted, Bounty & Combat", "07", "dbb55def-1066-4c70-b09d-72c22ea63df2")),
    ("cops-detainment-jail",      ("CJ", "Cops, Detainment & Jail", "08", "7b91103f-1e4a-45b8-91eb-25e5c47775c1")),
    ("civilians-traders-shops",   ("CT", "Civilians, Traders & Shops", "09", "dad8da79-b977-4cdc-9712-e080406cb0b5")),
    ("turf",                      ("TF", "Turf Wars", "10", "7a132f36-b2ce-48a2-8263-4eff270282ed")),
    ("weapons",                   ("WP", "Weapons", "11", "48a0fd29-5ad2-43ea-b05f-5d9c38e82196")),
    ("gadgets-cars-fuel-jetpack", ("GD", "Gadgets: Cars, Fuel & Jetpack", "12", "a3c799ae-5752-467c-b9b2-26e7e29e7882")),
    ("lootchests-signs-waypoints",("LS", "Loot Chests, Signs & Waypoints", "13", "65f0d8f2-4c5b-418c-97b3-23d619d82473")),
])
CASE = "https://claude.ai/code/artifact/%s#sec-observations-potential-issues"

def load_triage(slug):
    p = os.path.join(HERE, "triage", slug + ".txt")
    out = {}
    for ln in open(p, encoding="utf-8").read().splitlines():
        if not ln.strip(): continue
        parts = ln.split("~~")
        out[int(parts[0])] = parts
    return out

bugs = []
missing = []
for slug, (code, name, order, aid) in SYS.items():
    tri = load_triage(slug)
    rows = [o for o in obs if o["sys"] == slug]
    for o in rows:
        t = tri.get(o["num"])
        if not t:
            missing.append((slug, o["num"])); continue
        num, tier, title, fix, tests = t[0], t[1], t[2], t[3], t[4]
        risk = o.get("board_risk") or o.get("risk")
        if slug == "weapons" and o["num"] == 17: risk = "Withdrawn"
        if slug == "civilians-traders-shops" and o["num"] == 22: risk = "Low"
        conf = (o.get("confidence") or "").strip()
        m = re.match(r"^(High|Medium|Low|Medium-High|Low-Medium)", conf)
        conf_short = m.group(1) if m else (conf or "n/a")
        unverified = "unverified" in conf.lower() or "unverified" in o["observation"].lower()
        recheck = any(k in (title + " " + fix) for k in ("0.8.2", "gangland-mail", "MailModuleConfig"))
        bugs.append({
            "id": "%s-%02d" % (code, o["num"]),
            "code": code, "sys": slug, "sysName": name, "sysOrder": order, "num": o["num"],
            "tier": tier, "title": title, "fix": fix,
            "tests": None if tests.strip() == "-" else tests.strip(),
            "location": o["location"], "observation": o["observation"], "impact": o.get("impact"),
            "risk": risk, "confidence": conf_short, "unverified": unverified,
            "recheck": recheck,
            "case": CASE % aid, "source": slug + ".md",
        })
    for n in tri:
        if not any(o["num"] == n for o in rows):
            print("triage without observation:", slug, n)

# test-found bugs
for ln in open(os.path.join(HERE, "triage", "new-findings.txt"), encoding="utf-8").read().splitlines():
    if not ln.strip(): continue
    num, tier, title, fix, tests, loc, obs_text, slug = ln.split("~~")
    code, name, order, aid = SYS[slug]
    bugs.append({
        "id": "T-%02d" % int(num), "code": "T", "sys": slug, "sysName": name, "sysOrder": order, "num": int(num),
        "tier": tier, "title": title, "fix": fix, "tests": tests, "location": loc, "observation": obs_text,
        "impact": None, "risk": "n/a", "confidence": "High", "unverified": False, "recheck": True,
        "case": CASE % aid, "source": "test suite (2026-09-04)",
    })

if missing: print("MISSING triage:", missing)
c = collections.Counter(b["tier"] for b in bugs)
print("bugs:", len(bugs), dict(c))
per = collections.defaultdict(collections.Counter)
for b in bugs: per[b["code"]][b["tier"]] += 1
for k, v in per.items(): print(k, dict(v))
print("recheck:", [b["id"] for b in bugs if b["recheck"]])
print("with tests:", sum(1 for b in bugs if b["tests"]))

json.dump(bugs, open(os.path.join(HERE, "bugs.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
tpl = open(os.path.join(HERE, "docket_template.html"), encoding="utf-8").read()
data = json.dumps(bugs, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
html = tpl.replace("/*__DATA__*/", data)
open(os.path.join(HERE, "gangland-bug-docket.html"), "w", encoding="utf-8").write(html)
print("wrote gangland-bug-docket.html", len(html), "bytes")
