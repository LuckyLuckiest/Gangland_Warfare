import re, sys, math
log = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
blocks = []
cur = None
state = {}  # cop short name -> last state
returning_since = {}
events = []
for i, l in enumerate(log, 1):
    m = re.search(r'\[(\d\d:\d\d:\d\d)[^\]]*\]: \[Not Secure\] \[Server\] CNCMARK:SPAWNB:(\S+)', l)
    if m:
        cur = {'line': i, 't': m.group(1), 'label': m.group(2), 'rows': [], 'npcs': {}, 'runner': None, 'states': None}
        blocks.append(cur)
        continue
    m = re.search(r'Transitioning cop (\S+) #(\d+)-\d+ from (\w+) state to (\w+) state', l)
    if m:
        name = m.group(1) + '#' + m.group(2)
        state[name] = m.group(4)
        t = re.search(r'\[(\d\d:\d\d:\d\d)', l).group(1)
        events.append((i, t, 'TRANS', name, m.group(3), m.group(4)))
        continue
    if cur is None:
        continue
    m = re.search(r'^\[[^\]]+\]:  - (.+) #(\d+) \(', l)
    if m:
        nm = m.group(1).split()[-1] + '#' + m.group(2)
        cur['rows'].append(nm)
        if cur['states'] is None:
            cur['states'] = {}
        cur['states'][nm] = state.get(nm, '?')
        continue
    m = re.search(r': (\S+) #(\d+) has the following entity data: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]', l)
    if m:
        cur['npcs'][m.group(1) + '#' + m.group(2)] = (float(m.group(3)), float(m.group(4)), float(m.group(5)))
        continue
    m = re.search(r': Runner has the following entity data: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]', l)
    if m:
        cur['runner'] = (float(m.group(1)), float(m.group(2)), float(m.group(3)))

for b in blocks:
    r = b['runner']
    parts = []
    for nm in b['rows']:
        p = b['npcs'].get(nm)
        d = math.dist(p, r) if (p and r) else None
        st = (b['states'] or {}).get(nm, '?')
        parts.append(f"{nm}[{st[:4]}]{'' if d is None else f'@{d:.0f}' }{'' if p else '(noNPC)'}")
    nonsquad = [n for n in b['npcs'] if n not in b['rows']]
    print(f"L{b['line']} {b['t']} {b['label']:<16} rows={len(b['rows'])} npcs={len(b['npcs'])} runner={r[0] if r else None:} | {' '.join(parts)} | other={','.join(nonsquad)}")
