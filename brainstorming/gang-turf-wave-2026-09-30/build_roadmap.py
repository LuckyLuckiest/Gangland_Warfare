"""Embed roadmap.json into roadmap.template.html -> roadmap.html (the published artifact page).

The sample map frame in roadmap.json is plain text followed by a paragraph that describes its colours;
this adds a Minecraft &-coded copy (map.sample_colored) and moves that paragraph to map.sample_caption.
"""
import json
import pathlib
import re

here = pathlib.Path(__file__).parent
data = json.loads((here / "roadmap.json").read_text(encoding="utf-8"))

GLYPH = {"V": "&a", "K": "&b&o", "R": "&c", "S": "&c&n", "#": "&7", "-": "&8", "+": "&f"}
LEGEND = [
    (r"(?<![\w&])V (?=Vipers)", "&aV&7 "), (r"(?<![\w&])K (?=Kings)", "&bK&7 "), (r"(?<![\w&])R (?=Rats)", "&cR&7 "),
    (r"(?<![\w&])# ", "&7# "), (r"(?<![\w&])- ", "&8-&7 "), (r"\+1 more", "&f+1 more&7"),
    (r"italic", "&f&oitalic&7"), (r"underlined", "&f&nunderlined&7"),
]


def colour_grid(line):
    out, last = [], None
    for ch in line:
        code = GLYPH.get(ch, "&7")
        if code != last:
            out.append(code)
            last = code
        out.append(ch)
    return "".join(out)


def colour_sample(text):
    frame, _, caption = text.partition("\n\n")
    lines = frame.split("\n")
    coloured = []
    for i, line in enumerate(lines):
        if i == 0:
            coloured.append("&f" + line)
        elif re.fullmatch(r"[-RKSV#+]+", line):
            coloured.append(colour_grid(line))
        else:
            for pattern, repl in LEGEND:
                line = re.sub(pattern, repl, line)
            coloured.append("&7" + line)
    return "\n".join(coloured), caption.strip()


def load_parity():
    """parity/PARITY.md's last ```json block, joined with feature names from competitors/gangsplus.md."""
    parity_md = here / "parity" / "PARITY.md"
    if not parity_md.exists():
        return []
    blocks = re.findall(r"```json\s*(.*?)```", parity_md.read_text(encoding="utf-8"), re.S)
    rows = json.loads(blocks[-1]) if blocks else []
    names = dict(re.findall(r"^- (GP-\d+) (.+)$", (here / "competitors" / "gangsplus.md").read_text(encoding="utf-8"), re.M))
    for row in rows:
        row.setdefault("feature", names.get(row.get("id"), ""))
    return rows


if "parity" not in data:
    data["parity"] = load_parity()

m = data.get("map", {})
if m.get("sample_ascii") and "sample_colored" not in m:
    m["sample_colored"], m["sample_caption"] = colour_sample(m["sample_ascii"])

payload = json.dumps(data, ensure_ascii=False).replace("</", "<\\/").replace("<!--", "<\\!--")
page = (here / "roadmap.template.html").read_text(encoding="utf-8").replace("__ROADMAP_JSON__", payload)
(here / "roadmap.html").write_text(page, encoding="utf-8")
print(f"roadmap.html: {len(page)} bytes, {len(data.get('waves', []))} waves, {len(data.get('decisions', []))} decisions")
print(m.get("sample_colored", ""))
