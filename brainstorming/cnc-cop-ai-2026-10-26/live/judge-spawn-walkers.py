import re, sys, math
log = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
ret = {}; last = {}; maxd = {}; cur=None; rows=[]; npcs={}; runner=None
def flush():
    if cur is None: return
    for nm in rows:
        last[nm]=(cur[0],cur[1])
        p=npcs.get(nm)
        if p and runner and nm in ret: maxd[nm]=max(maxd.get(nm,0), math.dist(p,runner))
for i,l in enumerate(log,1):
    m=re.search(r'\[(\d\d:\d\d:\d\d)[^\]]*\]: \[Not Secure\] \[Server\] CNCMARK:SPAWN:(\S+)', l)
    if m:
        flush(); cur=(m.group(1),m.group(2)); rows=[]; npcs={}; runner=None; continue
    m=re.search(r'\[(\d\d:\d\d:\d\d).*Transitioning cop (\S+) #(\d+)-\d+ from PURSUING state to RETURNING', l)
    if m: ret.setdefault(m.group(2)+'#'+m.group(3), m.group(1)); continue
    m=re.search(r'^\[[^\]]+\]:  - (.+) #(\d+) \(', l)
    if m: rows.append(m.group(1).split()[-1]+'#'+m.group(2)); continue
    m=re.search(r': (\S+) #(\d+) has the following entity data: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]', l)
    if m: npcs[m.group(1)+'#'+m.group(2)]=tuple(map(float,m.group(3,4,5))); continue
    m=re.search(r': Runner has the following entity data: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]', l)
    if m: runner=tuple(map(float,m.group(1,2,3)))
flush()
for nm,t in ret.items():
    print(f"{nm:<10} RETURNING@{t}  lastListed={last.get(nm)}  maxDistWhileListed={maxd.get(nm,0):.0f}")
