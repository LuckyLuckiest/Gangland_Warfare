"""judge-acc.py <spawn|radio|brandish|handoff> <run dir> [--jar cops-n-crooks.jar]

0.21.1 acceptance judge for the cop-ai runs (owner rulings 2026-10-26). Reads <run>/server.log and <run>/chat.txt as
written by run-copai.js, prints one line per check: PASS / FAIL / INCONCLUSIVE / INFO, then an overall line.
Exit 0 = no FAIL, 1 = at least one FAIL. Run with python -I. Times: server.log has whole seconds, chat.txt has ms on
the same wall clock, so cross-file windows are padded by 1 s.
  spawn    R6 view cone + leash margin on every UNIT, R3 stranded walkers, Dispatch_En_Route spacing (cause R),
           cause 2 (hut spawner, leash at spawn), cause 5 (far walkers re-engaged), live count vs target
  radio    R4 no sight-claiming lines while sealed, no UNREACHABLE standoff, R5 Handoff heard + heading, R6 cone
  brandish R1 ignored -> cause line + star + short subtitle, R2 cooldown from the verdict, Lost_Sight, range shrink
  handoff  (scen/handoff-lead) HANDOFF_LEAD logged, R5 heading west + line heard, biased units go toward the lead and
           never toward the sealed box in the 15 s after it, R6 cone on every open-ground UNIT
  every kind: search-after-clear (no HANDOFF / squad order line after /glw wanted clear); spawn, radio, handoff also
           R3-in-view (no RETURNING walker removed in his view: min(spigot entity-tracking-range players,
           Visibility_Check_Distance) and 60 deg, walker projected into the removal window) and R3-cap (no RETURNING
           walker outlives Return.Max_Ticks + Return.Watched_Cap_Seconds)
ACC9 (2026-10-27): R6-station 0 SV-S units = INCONCLUSIVE; R3-count skips level 0 (T-187 search); R5-lead-go ends each
  unit's track at its first PURSUING->RETURNING; rows printed by a scenario poll step are not probe rows.
ACC8 (2026-10-27): cause2-hut leash half scoped to P2- births with no hop in between; R3-in-view range/cone/projection;
  R5-spawn-between (T-423: hand-off units spawn between his live spot and the lead); R3-cap (watched cap).
"""
import json, math, os, re, sys, zipfile
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

KIND, RUN = sys.argv[1], sys.argv[2].rstrip('/\\')
JAR = sys.argv[sys.argv.index('--jar') + 1] if '--jar' in sys.argv else \
    'E:/Programming/java/wt/gangland-0.21.1/target/modules/cops-n-crooks-0.21.1.jar'


# ---- config of the run (ACC8): the server dir is re-cloned per run from servers/base, cops.yml is shipped default ---
def yget(text, path, default):
    """Block-style YAML scalar at a dotted path (no flow maps in these files); default when absent."""
    want, stack = path.split('.'), []
    for line in (text or '').splitlines():
        m = re.match(r'^(\s*)([A-Za-z_][\w-]*):\s*([^#]*)', line)
        if not m:
            continue
        ind = len(m.group(1))
        while stack and stack[-1][0] >= ind:
            stack.pop()
        stack.append((ind, m.group(2)))
        if [k for _, k in stack] == want and m.group(3).strip():
            return type(default)(m.group(3).strip().strip('"\''))
    return default


def first_text(*srcs):
    for src in srcs:
        try:
            if '!' in src:
                with zipfile.ZipFile(src.split('!')[0]) as z:
                    return z.read(src.split('!')[1]).decode('utf-8'), src
            return open(src, encoding='utf-8', errors='replace').read(), src
        except (OSError, KeyError):
            continue
    return None, 'defaults'


SRV = 'E:/Programming/java/wt/_programme/servers/' + re.sub(r'--.*$', '', os.path.basename(RUN))
SPIGOT, SPIGOT_SRC = first_text(SRV + '/spigot.yml', 'E:/Programming/java/wt/_programme/servers/base/spigot.yml')
COPS, COPS_SRC = first_text(SRV + '/plugins/Gangland_Warfare/copsncrooks/cops.yml', JAR + '!copsncrooks/cops.yml')
TRACK = yget(SPIGOT, 'world-settings.world.entity-tracking-range.players',
             yget(SPIGOT, 'world-settings.default.entity-tracking-range.players', 48.0))
VCD = yget(COPS, 'Cops.Spawn.Visibility_Check_Distance', 48.0)
MAXRET_S = yget(COPS, 'Cops.Return.Max_Ticks', 600.0) * yget(COPS, 'Cops.Behaviour.AI_Tick_Rate', 10.0) / 20.0
CAP_S = yget(COPS, 'Cops.Return.Watched_Cap_Seconds', 60.0)
LOG = open(RUN + '/server.log', encoding='utf-8', errors='replace').read().splitlines()
CHAT = open(RUN + '/chat.txt', encoding='utf-8', errors='replace').read().splitlines()
results = []


def out(status, key, msg):
    results.append(status)
    print(f'{status:<12} {key:<16} {msg}')


def hms(s):
    h, m, x = s.split(':')
    return int(h) * 3600 + int(m) * 60 + float(x)


# ---- server.log as events ------------------------------------------------------------------------------------------
TS = re.compile(r'^\[(\d\d:\d\d:\d\d) (\w+)\]: (.*)$')
POS3 = re.compile(r'(?:^|: )(\S+(?: #\d+)?) has the following entity data: \[([-\d.E]+)d, ([-\d.E]+)d, ([-\d.E]+)d\]')
ROT2 = re.compile(r'(?:^|: )(\S+) has the following entity data: \[([-\d.E]+)f, ([-\d.E]+)f\]')
MARK = re.compile(r'CNCMARK:[^:]+:(\S+)')
TRANS = re.compile(r'Transitioning cop (.+?) #(\d+)-\d+ from (\w+) state to (\w+) state')
UNIT = re.compile(r'UNIT (\S+) callsign=(.+?) tier=.*? hidden=(\w+) bias=(\w+) ahead=(\S+) at=(-?\d+),(-?\d+),(-?\d+)')
ROW = re.compile(r'^ - (.+) #(\d+) \(')
ev = []  # (t, kind, data)
t = 0.0
for line in LOG:
    m = re.match(r'^>>> STEP (\d+) (.*)$', line)
    if m:
        try:
            ev.append((t, 'step', json.loads(m.group(2))))
        except ValueError:
            pass
        continue
    m = TS.match(line)
    if not m:
        continue
    t, msg = hms(m.group(1)), m.group(3)
    if (mm := MARK.search(msg)) and '[Server]' in msg:
        ev.append((t, 'mark', mm.group(1)))
    elif mm := TRANS.search(msg):
        ev.append((t, 'trans', (mm.group(1).split()[-1] + '#' + mm.group(2), mm.group(3), mm.group(4))))
    elif mm := UNIT.search(msg):
        ev.append((t, 'unit', {'bot': mm.group(1), 'cop': mm.group(2).split()[-2] + '#' + mm.group(2).split('#')[-1]
                               if '#' in mm.group(2) else mm.group(2), 'hidden': mm.group(3), 'bias': mm.group(4),
                               'ahead': mm.group(5), 'at': tuple(int(mm.group(i)) for i in (6, 7, 8))}))
    elif mm := POS3.search(msg):
        ev.append((t, 'pos', (mm.group(1), tuple(float(mm.group(i)) for i in (2, 3, 4)))))
    elif mm := ROT2.search(msg):
        ev.append((t, 'rot', (mm.group(1), float(mm.group(2)), float(mm.group(3)))))
    elif mm := ROW.match(msg):
        ev.append((t, 'row', mm.group(1).split()[-1] + '#' + mm.group(2)))
    elif 'not being chased by cops' in msg:
        ev.append((t, 'norows', None))
    else:
        ev.append((t, 'msg', msg))

CH = []  # (t, bot, pos, text)
for line in CHAT:
    m = re.match(r'^\[(\d\d:\d\d:\d\d\.\d+)\]\[([^\]/]+)(?:/([^\]]+))?\] ?(.*)$', line)
    if m:
        CH.append((hms(m.group(1)), m.group(2), m.group(3) or '', m.group(4)))


def chat_in(bot, t0, t1, pattern, pos=None):
    rx = re.compile(pattern)
    return [c for c in CH if c[1] == bot and t0 <= c[0] < t1 and rx.search(c[3]) and (pos is None or c[2] == pos)]


# ---- bot pose and probes -------------------------------------------------------------------------------------------
TP = re.compile(r'^tp (\w+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)(?: (-?[\d.]+) (-?[\d.]+))?$')


def walk(bot, on_unit=None, on_probe=None, on_mark=None):
    """Replays the timeline keeping the bot's pose; probes = rows + NPC positions + bot pos after one marker."""
    pose = {'pos': None, 'yaw': None, 'pitch': None}
    state, probe, label, polling = {}, None, '', False
    for t, k, d in ev:
        if k == 'step':
            polling = 'poll' in d  # ACC9: a poll step's cop-list rows are a setup wait, not a probe
            c = d.get('console', '')
            m = TP.match(c)
            if probe is not None and ((m and m.group(1) == bot) or (bot in c and ' tp ' in c) or
                                      ('control' in d and d.get('bot', bot) == bot)):
                probe['tp'] = True  # he moved between this probe and the next (ACC8 R3-in-view: INCONCLUSIVE there)
            if m and m.group(1) == bot:
                pose['pos'] = tuple(float(m.group(i)) for i in (2, 3, 4))
                if m.group(5) is not None:
                    pose['yaw'], pose['pitch'] = float(m.group(5)), float(m.group(6))
            elif bot in c and ' tp ' in c or 'control' in d or 'lookAt' in d:
                pose['pos'] = None if 'tp' in c or 'control' in d else pose['pos']
                if 'lookAt' in d:
                    pose['yaw'] = None
        elif k == 'mark':
            if probe is not None and on_probe:
                probe['pose'] = dict(pose)  # his pose when the probe closed (the next marker)
                on_probe(probe)
            label = d
            probe = {'t': t, 'label': d, 'rows': [], 'npcs': {}, 'bot': None, 'state': dict(state),
                     'seen': dict(pose), 'tp': False}
            if on_mark:
                on_mark(t, d)
        elif k == 'trans':
            state[d[0]] = d[2]
            if probe is not None:
                probe['state'][d[0]] = d[2]
        elif k == 'row' and probe is not None and not polling:
            probe['rows'].append(d)
        elif k == 'pos':
            if d[0] == bot:
                pose['pos'] = d[1]
                if probe is not None:
                    probe['bot'] = d[1]
                    if not probe['tp']:
                        probe['seen'] = dict(pose)
            elif probe is not None and '#' in d[0]:
                probe['npcs'][d[0].replace(' #', '#')] = d[1]
        elif k == 'rot' and d[0] == bot:
            pose['yaw'], pose['pitch'] = d[1], d[2]
            if probe is not None and not probe['tp']:
                probe['seen'] = dict(pose)  # his pose while this probe listed the NPCs ('pose' = at the next marker)
        elif k == 'unit' and d['bot'] == bot and on_unit:
            on_unit(t, d, dict(pose), label, state)
    if probe is not None and on_probe:
        probe['pose'] = dict(pose)
        on_probe(probe)


def view_angle(pose, at):
    """Degrees between the bot's eye direction and the spot's head (CopSpawnManager.inViewOf geometry)."""
    yaw, pitch = math.radians(pose['yaw']), math.radians(pose['pitch'] or 0.0)
    d = (-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
    px, py, pz = pose['pos']
    v = (at[0] + 0.5 - px, at[1] + 1.6 - (py + 1.62), at[2] + 0.5 - pz)
    n = math.sqrt(sum(x * x for x in v)) or 1e-9
    return math.degrees(math.acos(max(-1.0, min(1.0, sum(a * b for a, b in zip(d, v)) / n))))


def cone_check(bot, sealed_rx, key='R6'):
    """R6 + leash margin (cause 2): every UNIT out of the bot's 60-degree view cone and within 0.75 x 80 = 60 blocks."""
    rows, bad, far, unknown, sealed = [], [], [], 0, 0

    def on_unit(t, d, pose, label, state):
        nonlocal unknown, sealed
        if pose['pos'] is None:
            unknown += 1
            return
        dist = math.dist((d['at'][0] + 0.5, d['at'][1], d['at'][2] + 0.5), pose['pos'])
        if dist > 61.0:
            far.append(f"{d['cop']}@{label} {dist:.1f}b")
        if pose['yaw'] is None:
            unknown += 1
            return
        ang = view_angle(pose, d['at'])
        rows.append((t, label, d, ang, dist))
        if sealed_rx and re.match(sealed_rx, label):
            sealed += 1
        elif ang < 56.0:
            bad.append(f"{d['cop']} at={','.join(map(str, d['at']))} {ang:.0f}deg {dist:.0f}b @{label}")
    walk(bot, on_unit=on_unit)
    n = len(rows)
    if not n and not far:
        out('INCONCLUSIVE', key, f'no UNIT line with a known bot facing ({unknown} without facing)')
    else:
        out('FAIL' if bad else 'PASS', key, f'{n} UNIT spawns judged ({sealed} in sealed phases skipped, {unknown} '
            f'without pose); in view cone (<56 deg, open ground): {len(bad)} ' + '; '.join(bad[:8]))
        border = [r for r in rows if 56.0 <= r[3] < 64.0]
        if border:
            out('INFO', key, f'{len(border)} spawns 56-64 deg off his look (block rounding band)')
        ahead = sum(1 for r in rows if r[2]['ahead'] == 'true')
        out('INFO', key + '-ahead', f"ahead=true spawns {ahead}/{n}; by phase: " + ', '.join(
            f"{p}:{sum(1 for r in rows if r[1].startswith(p) and r[2]['ahead'] == 'true')}/"
            f"{sum(1 for r in rows if r[1].startswith(p))}" for p in sorted({r[1].split('-')[0] for r in rows})))
    out('FAIL' if far else 'PASS', 'cause2-margin', f'UNIT spawns past 61 blocks (3D) of him: {len(far)} ' +
        '; '.join(far[:6]))
    return rows


def probes(bot):
    acc = []
    walk(bot, on_probe=acc.append)
    return acc


# ---- spawn ---------------------------------------------------------------------------------------------------------
def judge_spawn():
    sealed = r'^(PP-SEALED|PP-DROP|PP-WANTED1)'
    units = cone_check('Runner', sealed)
    ps = probes('Runner')
    # R3: a RETURNING walker > 80 blocks out may be listed at most ~one spawn tick; seen stranded across >= 5 s = FAIL
    seen = {}
    for p in ps:
        if not p['bot']:
            continue
        for cop in p['rows']:
            pos = p['npcs'].get(cop)
            if p['state'].get(cop) == 'RETURNING' and pos and math.dist(pos, p['bot']) > 80:
                seen.setdefault(cop, []).append((p['t'], p['label'], math.dist(pos, p['bot'])))
    long = {c: v for c, v in seen.items() if v[-1][0] - v[0][0] >= 5}
    if not seen:
        out('INCONCLUSIVE' if not any('RETURNING' in str(e) for e in ev) else 'PASS', 'R3',
            'no stranded RETURNING walker (>80 b) listed in any probe')
    else:
        out('FAIL' if long else 'PASS', 'R3', f'{len(seen)} walkers listed while stranded >80 b; listed across >=5 s: '
            + '; '.join(f'{c} {v[0][1]}->{v[-1][1]} {v[-1][0] - v[0][0]:.0f}s max {max(x[2] for x in v):.0f}b'
                        for c, v in list(long.items())[:6]))
    # R3 secondary: live squad (rows minus walkers within 80 b, which are kept by design) vs the level's target
    # ACC9: level 0 (0-star row, or his wanted level cleared) has no target: the post-escape search keeps its cops by
    # design (T-187), so those probes are skipped and break a run of over-target probes
    lv = sorted([(c[0], c[3].count('★')) for c in CH if c[1] == 'Runner' and re.search(r'[★☆]{5}', c[3])] +
                [(c[0], 0) for c in CH if c[1] == 'Runner' and 'Your wanted level has been cleared' in c[3]],
                key=lambda x: x[0])  # stable: same-ms rows keep chat order
    over = []
    prev = False
    zero = 0
    for p in ps:
        if not p['bot'] or not p['rows']:
            prev = False
            continue
        level = ([l for tt, l in lv if tt < p['t'] + 1] or [0])[-1]
        if level == 0:
            zero += 1
            prev = False
            continue
        target = min(2 + level - 1, 8)
        live = [c for c in p['rows'] if not (p['state'].get(c) == 'RETURNING' and p['npcs'].get(c)
                                              and math.dist(p['npcs'][c], p['bot']) <= 80)]
        excess = len(live) - target
        if excess > 0 and prev:
            over.append(f"{p['label']} {len(live)}>{target}")
        prev = excess > 0
    out('FAIL' if over else 'PASS', 'R3-count', f'probes over target two probes in a row: {len(over)} ' +
        ''.join(x + '; ' for x in over[:6]) + f'({zero} probes at level 0 not judged)')
    # cause R: Dispatch_En_Route at most once per 30 s per squad (episode = between wanted clear/add steps)
    resets = [tt for tt, k, d in ev if k == 'step' and re.match(r'/glw wanted (clear|add)', d.get('chat', ''))]
    lines = [c[0] for c in CH if c[1] == 'Runner' and re.search(r'\[DISPATCH\] \d+ units en route', c[3])]
    close = [(a, b) for a, b in zip(lines, lines[1:]) if b - a < 29 and not any(a <= r <= b + 1 for r in resets)]
    out('FAIL' if close else 'PASS', 'causeR', f'{len(lines)} "units en route" lines, pairs <29 s apart in one '
        f'episode: {len(close)}')
    # cause 2 (spawn-dup-b P2): no unit from the pillar-hut spawner (80.35 b 3D), none leashed within 3 s of spawning.
    # ACC8: the leash half judges units born in a P2- phase only, and skips a leash with a tp of his between the UNIT
    # and the transition (phase PP hops him 180 b every few seconds: that leash is the hop, not the spawn spot)
    if any(k == 'mark' and d.startswith('P2-') for _, k, d in ev):
        born = {u[2]['cop']: (u[0], u[1]) for u in units}
        tps = [tt for tt, k, d in ev if k == 'step' for m in [TP.match(d.get('console', ''))] if m and m.group(1) ==
               'Runner']
        leashed, hopped, other = [], 0, 0
        for tt, k, d in ev:
            if k != 'trans' or d[1] != 'PURSUING' or d[2] != 'RETURNING' or d[0] not in born:
                continue
            t0, label = born[d[0]]
            if not 0 <= tt - t0 <= 3:
                continue
            if not label.startswith('P2-'):
                other += 1
            elif any(t0 <= x <= tt for x in tps):
                hopped += 1
            else:
                leashed.append(d[0])
        hut_all = [d for _, k, d in ev if k == 'unit' and d['at'] == (0, -46, 74)]
        out('FAIL' if hut_all or leashed else 'PASS', 'cause2-hut',
            f'UNIT at the hut spawner 0,-46,74: {len(hut_all)}; P2- units leashed <=3 s after spawn: {len(leashed)} '
            + ','.join(leashed[:6]))
        if other or hopped:
            out('INFO', 'cause2-hut-skip', f'leashed <=3 s after spawn, not judged: {other} born outside P2-, '
                f'{hopped} with a tp of his in between')
    # cause 5 (spawn-dup-b P5-ADD1): no RETURNING->PURSUING for a cop last probed > 80 b out
    add = [tt for tt, k, d in ev if k == 'mark' and d == 'P5-ADD1']
    if add:
        last = [p for p in ps if p['t'] <= add[0] and p['bot']]
        far = {c for p in last[-1:] for c, pos in p['npcs'].items() if math.dist(pos, p['bot']) > 80}
        back = [d[0] for tt, k, d in ev if k == 'trans' and add[0] <= tt <= add[0] + 3 and d[1] == 'RETURNING'
                and d[2] == 'PURSUING' and d[0] in far]
        out('FAIL' if back else 'PASS', 'cause5', f'{len(far)} cops >80 b at P5-ADD1, re-engaged: {len(back)} '
            + ','.join(back))
    sv = [u for u in units if u[1].startswith('SV-')]
    if not sv and any(k == 'mark' and d.startswith('SV-') for _, k, d in ev):
        out('INCONCLUSIVE', 'R6-station', 'SV-S and SV-N phases ran with no UNIT spawn at all')  # ACC9
    if sv:
        s_hits = [u for u in sv if u[1].startswith('SV-S') and u[2]['at'] == (0, -60, 74)]
        n_hits = [u for u in sv if u[1].startswith('SV-N') and u[2]['at'] == (0, -60, 74)]
        s_all = sum(1 for u in sv if u[1].startswith('SV-S'))  # ACC9: no SV-S unit = nothing judged, not a PASS
        out('FAIL' if s_hits else 'PASS' if s_all else 'INCONCLUSIVE', 'R6-station',
            f'SV-S (facing the spawner 54 b away): spawner used {len(s_hits)}x of {s_all}; SV-N (facing away): '
            f'{len(n_hits)}x of {sum(1 for u in sv if u[1].startswith("SV-N"))} (INFO)')


# ---- radio ---------------------------------------------------------------------------------------------------------
def pools():
    """Lines.<Pool> of the shipped cop_radio_messages.yml (block style: '   Pool:' then '      - "text"'), as regexes."""
    with zipfile.ZipFile(JAR) as z:
        text = z.read('copsncrooks/cop_radio_messages.yml').decode('utf-8')
    rx, key, inside = {}, None, False
    for line in text.splitlines():
        if re.match(r'^Lines:', line):
            inside = True
            continue
        if inside and re.match(r'^[A-Za-z]', line):
            break
        if not inside:
            continue
        if m := re.match(r'^   ([A-Za-z_]+):\s*$', line):
            key = m.group(1)
        elif (m := re.match(r'^\s+- "(.*)"\s*$', line)) and key:
            pat = re.escape(re.sub(r'&[0-9a-fk-or]', '', m.group(1))).replace(r'\%', '%')
            rx.setdefault(key, []).append(re.compile('^' + re.sub(r'%[a-z_]+%', '.+?', pat) + '$'))
    return rx


def judge_radio():
    rx = pools()
    radio = []
    for c in CH:
        m = re.match(r'^\[RADIO\] [^:]+: (.*)$', c[3])
        if c[1] == 'Runner' and m:
            ks = [k for k, rs in rx.items() if any(r.match(m.group(1)) for r in rs)]
            radio.append((c[0], ks, m.group(1)))
    marks = {d: tt for tt, k, d in ev if k == 'mark'}
    room = marks.get('GO-ROOM')
    end = marks.get('END', 1e9)
    HARD = {'Engage', 'Push', 'Flank_Left', 'Flank_Right', 'Overwatch_Set', 'Standoff', 'Route', 'Route_High'}
    SIGHT = {'Contact', 'Marksman_Spotted', 'Eyes_On', 'Overwatch_Spotted'}
    SOFT = {'Commander_Orders', 'Commander_Orders_Basic', 'Flanking', 'Shield_Up', 'Covering_Fire'}
    route = 'ROOM-7' in marks and 'ROOM3-1' not in marks  # acc-radio-route: the tunnel lets cops walk in and see him
    if room is None:
        out('INCONCLUSIVE', 'R4', 'no GO-ROOM marker')
    else:
        t0 = room + 5
        first_sight = next((r[0] for r in radio if r[0] >= t0 and set(r[1]) & SIGHT), None)
        t1 = min(end, first_sight) if route and first_sight else end
        win = [r for r in radio if t0 <= r[0] < t1]
        hard = [r for r in win if set(r[1]) & HARD and not set(r[1]) & {'Route_Search'}]
        sight = [] if route else [r for r in win if set(r[1]) & SIGHT]
        unreach = [tt for tt, k, d in ev if k == 'msg' and t0 <= tt < t1 and 'STANDOFF Runner start trigger=UNREACHABLE' in d]
        out('FAIL' if hard or sight or unreach else 'PASS', 'R4',
            f'sealed window {t1 - t0:.0f}s: radio lines {len(win)}; Flank/Push/Engage/Overwatch/Standoff/"to him": '
            f'{len(hard)}; sight claims: {len(sight)}; UNREACHABLE standoffs: {len(unreach)} ' +
            ' | '.join(r[2] for r in (hard + sight)[:6]))
        soft = [r for r in win if set(r[1]) & SOFT]
        out('INFO', 'R4-soft', f'Commander/Flanking/Shield lines in the window: {len(soft)} ' +
            ' | '.join(r[2] for r in soft[:4]))
        rs = [r for r in win if 'Route_Search' in r[1]]
        out('INFO', 'R4-route', f'Route_Search lines in the window: {len(rs)}' +
            (f'; first real sighting (route variant) at +{first_sight - room:.0f}s' if route and first_sight else ''))
    far = marks.get('GO-FAR')
    if far is not None:
        ho = [(c[0], m.group(1)) for c in CH if c[1] == 'Runner' and c[0] >= far - 1 and c[0] <= far + 12
              for m in [re.search(r'Lost him heading ([\w-]+)\.', c[3])] if m]
        walked = [d for tt, k, d in ev if k == 'pos' and d[0] == 'Runner' and marks.get('CONTACT-b', -1) <= tt <= far]
        dx = walked[0][1][0] if walked else None
        if 'WALK-WEST' in marks:
            if dx is None or dx > -3:
                out('INCONCLUSIVE', 'R5', f'west walk did not happen (Runner x at CONTACT-b = {dx})')
            elif not ho:
                out('FAIL', 'R5', 'no Handoff line reached Runner within 12 s of the hop')
            else:
                ok = 'west' in ho[0][1]
                out('PASS' if ok else 'FAIL', 'R5', f'Handoff heard +{ho[0][0] - far:.1f}s "heading {ho[0][1]}" '
                    f'(squad last-known: walked west to x={dx:.1f}; live hop was south)')
        else:
            out('INFO' if ho else 'FAIL', 'R5', f'Handoff lines after the hop: {[h[1] for h in ho]} (no walk: '
                'heading not discriminating, only "still heard" judged)')
    cone_check('Runner', r'^(GO-ROOM|ROOM|CLEAR|WANTED3|END)')


# ---- brandish ------------------------------------------------------------------------------------------------------
def judge_brandish():
    W = re.compile(r'WARNING (\w+) result=(given|complied|ignored)')
    warns = [(tt, m.group(1), m.group(2)) for tt, k, d in ev if k == 'msg' for m in [W.search(d)] if m]
    bots = sorted({w[1] for w in warns})
    crime = 'Brandishing a weapon near an officer'
    for b in bots:
        seq = [w for w in warns if w[1] == b]
        ign = [w for w in seq if w[2] == 'ignored']
        if ign:
            t0 = ign[0][0]
            cause = chat_in(b, t0, t0 + 3, r'Beat cop: You were warned\. Cause: ' + crime)
            subs = chat_in(b, t0, t0 + 3, r'.', pos='title')
            sub = [c[3] for c in subs if c[3].startswith('[subtitle]')]
            card = chat_in(b, t0, t0 + 3, '^' + crime + ': ')
            disp = [tt for tt, k, d in ev if k == 'msg' and t0 <= tt <= t0 + 3 and f'DISPATCH {b} ' in d]
            short = bool(sub) and all(re.fullmatch(r'\[subtitle\] ?[★☆]{5}', s.strip()) and '★' in s for s in sub)
            first = len([w for w in seq if w[2] == 'ignored' and w[0] <= t0]) == 1
            ok = cause and short and card and disp and first
            out('PASS' if ok else 'FAIL', 'R1-' + b, f'ignored at {ign[0][0]:.0f}: cause line {bool(cause)}, '
                f'subtitle {sub[:1]} short={short}, card in chat {bool(card)}, DISPATCH {bool(disp)}, '
                f'this is his 1st ignore {first}')
        # R2: every verdict -> next given of the same player >= 30 s later (whole-second logs: >= 29)
        for i, w in enumerate(seq):
            if w[2] == 'given':
                continue
            nxt = next((x for x in seq[i + 1:] if x[2] == 'given'), None)
            if nxt:
                gap = nxt[0] - w[0]
                out('PASS' if gap >= 29 else 'FAIL', 'R2-' + b, f'{w[2]} at {w[0]:.0f} -> next warning {gap:.0f}s '
                    'after the verdict (>=30 wanted; old code allows 30 s after the START)')
        if any(w[2] == 'complied' for w in seq):
            c = [w for w in seq if w[2] == 'complied']
            msgs = [m[3] for w in c for m in chat_in(b, w[0], w[0] + 2, r'Thanks, carry on|Lost sight of you')]
            out('PASS' if len(msgs) >= len(c) else 'FAIL', 'verdict-msg-' + b,
                f'{len(c)} complied verdicts, messages {msgs}')
    marks = {d: tt for tt, k, d in ev if k == 'mark'}
    if 'O-VERDICT' in marks:
        seq = [w for w in warns if w[1] == 'Oov']
        v = next((w for w in seq if w[2] == 'complied'), None)
        nxt = v and next((x for x in seq if x[0] > v[0] and x[2] == 'given'), None)
        lost = v and chat_in('Oov', v[0], v[0] + 2, r'Lost sight of you')
        if not v:
            out('INCONCLUSIVE', 'R2-Oov-row', 'no out-of-view verdict')
        else:
            out('PASS' if lost else 'FAIL', 'Lost_Sight', f'out-of-view verdict at {v[0]:.0f} told "Lost sight": '
                f'{bool(lost)}')
            gap = nxt[0] - v[0] if nxt else None
            out('PASS' if nxt and 29 <= gap <= 45 else 'FAIL', 'R2-repeat',
                f'second warning {"none" if gap is None else f"{gap:.0f}s"} after the verdict (wanted 30-45 s; '
                'old code: 25 s)')
    # range shrink stays: distance of the nearest cop at each night / sneak warning
    for b, lim, phase in (('Nighter', 6.5, 'N-'), ('Sneaker', 9.4, 'S-SNEAK')):
        ps = probes(b)
        gv = [w for w in warns if w[1] == b and w[2] == 'given']
        if not gv:
            out('INCONCLUSIVE', 'shrink-' + b, 'no warning given')
            continue
        rows = []
        for w in gv:
            p = ([p for p in ps if p['t'] <= w[0] and p['bot'] and p['npcs']] or [None])[-1]
            if p is None:
                continue
            d = min(math.dist(p['bot'], x) for x in p['npcs'].values())
            # the cops walk between probes: also the next probe's cop positions against his pose before the warning
            q = ([q for q in ps if q['t'] >= w[0] and q['npcs']] or [None])[0]
            if q is not None:
                d = min(d, min(math.dist(p['bot'], x) for x in q['npcs'].values()))
            rows.append((p['label'], d))
        bad = [r for r in rows if r[0].startswith(phase) and r[1] > lim]
        out('FAIL' if bad else 'PASS', 'shrink-' + b, f'warnings at nearest-cop distance ' +
            ', '.join(f'{l}:{d:.1f}' for l, d in rows) + f' (limit {lim} in {phase})')


# ---- shared: R3-in-view, search-after-clear (ACC7, 2026-10-27) -----------------------------------------------------
def returning_since():
    """cop -> times it entered RETURNING (onEnter resets Return.Max_Ticks' count)."""
    ret = {}
    for tt, k, d in ev:
        if k == 'trans' and d[2] == 'RETURNING':
            ret.setdefault(d[0], []).append(tt)
    return ret


def age_at(ret, cop, t):
    since = [e for e in ret.get(cop, []) if e <= t + 1]
    return t - since[-1] if since else None


def in_view(pose, at, rng, fov=60.0):
    """CopSightlines.inView geometry (eye direction vs the body + 1, < fov, < rng); None without his pose."""
    if not pose or pose['pos'] is None or pose['yaw'] is None:
        return None
    a = view_angle(pose, (at[0] - 0.5, at[1] - 0.6, at[2] - 0.5))
    d = math.dist(at, pose['pos'])
    return (d < rng and a < fov, d, a)


def r3_in_view(bot, sealed_rx):
    """R3: no RETURNING walker leaves the map while he can see it, open ground (ACC8 criteria).
    Range = min(spigot entity-tracking-range players, Cops.Spawn.Visibility_Check_Distance): past the tracking range
    his client no longer draws the walker, past Visibility_Check_Distance the code (CopSightlines.watched) may remove
    it. Cone = the code's 60 degrees. A vanish = an NPC in probe i's positions that no later probe lists; the removal
    happened between probe i and i+1. The walker is judged at its probe-i spot and projected along its last two
    listings (speed capped at 6 b/s) to probe i+1's time; his pose is the one seen while each probe ran. In view at
    both = FAIL, at one = ambiguous (INFO). INCONCLUSIVE for that walker when he moved (tp/control) between the two
    probes. Age rules from ReturningBehavior: past Return.Max_Ticks only 24 b holds it, past Max_Ticks +
    Watched_Cap_Seconds the cap removes it (exempt). Line of sight is not traced: flat open world, sealed_rx skipped."""
    rng = min(TRACK, VCD)
    ret = returning_since()
    allp = probes(bot)
    at_idx = {id(x): i for i, x in enumerate(allp)}
    ps = [p for p in allp if p['npcs'] or p['bot']]
    later = set()
    gone = []
    for i in range(len(ps) - 2, -1, -1):
        later |= set(ps[i + 1]['npcs'])
        for cop, at in ps[i]['npcs'].items():
            if cop not in later:
                prev = next((ps[j] for j in range(i - 1, -1, -1) if cop in ps[j]['npcs']), None)
                gone.append((prev, ps[i], ps[i + 1], cop, at))
    bad, amb, moved, unknown, capped, other, sealed, seen = [], [], [], 0, [], 0, 0, 0
    for prev, p, q, cop, at in gone:
        st = q['state'].get(cop) or p['state'].get(cop)
        if st != 'RETURNING':
            other += 1
            continue
        seen += 1
        if sealed_rx and re.match(sealed_rx, p['label']):
            sealed += 1
            continue
        # a hop marker (P2-HOP-EAST, GO-BOX, GO-ROOM...) is its own probe with no data: any move from p up to q counts
        if any(x['tp'] for x in allp[at_idx[id(p)]:at_idx[id(q)]]):
            moved.append(f"{cop} {p['label']}->{q['label']}")
            continue
        age = age_at(ret, cop, q['t'])
        r = rng
        if age is not None and age >= MAXRET_S + CAP_S:
            capped.append(f'{cop} {age:.0f}s')
            continue
        if age is not None and age >= MAXRET_S:
            r = 24.0
        v = (0.0, 0.0, 0.0)
        if prev is not None and p['t'] > prev['t']:
            v = tuple((x - y) / (p['t'] - prev['t']) for x, y in zip(at, prev['npcs'][cop]))
            sp = math.hypot(*v)
            v = tuple(x * min(1.0, 6.0 / sp) for x in v) if sp else v
        end = tuple(x + y * (q['t'] - p['t']) for x, y in zip(at, v))
        vis = [in_view(p['seen'], at, r), in_view(q['seen'], end, r)]
        if None in vis:
            unknown += 1
        elif vis[0][0] and vis[1][0]:
            bad.append(f"{cop} {p['label']}->{q['label']} {vis[0][1]:.0f}->{vis[1][1]:.0f}b "
                       f"{vis[0][2]:.0f}/{vis[1][2]:.0f}deg age {'?' if age is None else f'{age:.0f}s'}")
        elif vis[0][0] or vis[1][0]:
            amb.append(f"{cop} {p['label']}->{q['label']} {vis[0][1]:.0f}->{vis[1][1]:.0f}b")
    out('INFO', 'R3-in-view-cfg', f'range {rng:.0f} b = min(entity-tracking-range players {TRACK:.0f} [{SPIGOT_SRC}], '
        f'Visibility_Check_Distance {VCD:.0f} [{COPS_SRC}]), cone 60 deg; Max_Ticks {MAXRET_S:.0f}s, '
        f'watched cap {CAP_S:.0f}s')
    if not seen:
        out('INCONCLUSIVE', 'R3-in-view', f'no RETURNING walker left the map ({other} other NPCs vanished)')
        return
    judged = seen - sealed - unknown - len(moved) - len(capped)
    status = 'FAIL' if bad else ('PASS' if judged > 0 else 'INCONCLUSIVE')
    out(status, 'R3-in-view', f'{seen} RETURNING walkers removed ({sealed} in sealed phases, {len(moved)} with him '
        f'moving in the window, {len(capped)} past the watched cap, {unknown} without his pose; {judged} judged): in '
        f'view (<{rng:.0f} b, <60 deg) at both ends of the window: {len(bad)} ' + '; '.join(bad[:6]))
    if moved:
        out('INFO', 'R3-in-view-tp', f'INCONCLUSIVE, he moved between the probes: {len(moved)} ' + '; '.join(moved[:6]))
    if amb:
        out('INFO', 'R3-in-view-amb', f'in view at only one end of the window: {len(amb)} ' + '; '.join(amb[:6]))
    if other:
        out('INFO', 'R3-in-view-oth', f'{other} non-RETURNING NPCs vanished (deaths, recycles of hunters)')


def r3_cap(bot):
    """R3 hard cap (owner ruling 2026-10-27): a RETURNING walker a player within 24 b keeps watching is removed anyway
    after Cops.Return.Watched_Cap_Seconds. The cap's start is either Return.Max_Ticks (past it only a 24 b view holds
    a walker) or the watch itself; both put every removal before RETURNING age Max_Ticks + cap, so: FAIL = a walker
    still listed RETURNING past Max_Ticks + cap + 15 s (AI-tick lag slack); PASS = at least one walker was in his view
    (< 24 b, < 60 deg) past Max_Ticks and none outlived the bound; INCONCLUSIVE = no walker watched that long."""
    ret = returning_since()
    lim = MAXRET_S + CAP_S + 15
    over, held, oldest = [], set(), (0.0, '')
    for p in probes(bot):
        for cop, at in p['npcs'].items():
            if p['state'].get(cop) != 'RETURNING':
                continue
            age = age_at(ret, cop, p['t'])
            if age is None:
                continue
            oldest = max(oldest, (age, cop))
            vis = in_view(p['seen'], at, 24.0)
            if age > lim:
                over.append(f"{cop} {age:.0f}s @{p['label']} in view {bool(vis and vis[0])}")
            elif age >= MAXRET_S and vis and vis[0]:
                held.add(cop)
    status = 'FAIL' if over else ('PASS' if held else 'INCONCLUSIVE')
    out(status, 'R3-cap', f'bound Max_Ticks {MAXRET_S:.0f}s + cap {CAP_S:.0f}s + 15s; watched (<24 b) past Max_Ticks: '
        f'{len(held)}; listed past the bound: {len(over)} ' + '; '.join(over[:6]) +
        f' (oldest RETURNING listing {oldest[0]:.0f}s {oldest[1]})')


ORDERS = {'Engage', 'Push', 'Flank_Left', 'Flank_Right', 'Reposition', 'Search', 'Route', 'Route_High',
          'Route_Search', 'Climb', 'Covering_Fire', 'Overwatch_Set', 'Shield_Up', 'Flanking', 'Flanking_Undirected',
          'Commander_Orders', 'Commander_Orders_Undirected', 'Commander_Orders_Basic', 'Regroup', 'Regroup_Push',
          'Post_Up', 'Handoff', 'Standoff', 'Negotiate', 'Stack_Up', 'Breach', 'Flashbang', 'Backup',
          'Dispatch_En_Route', 'Wipe_Refill', 'Escalate', 'Contact', 'Eyes_On', 'Marksman_Spotted',
          'Overwatch_Spotted'}


def search_after_clear():
    """After a /glw wanted clear and until his next /glw wanted add, new crime (an ignored warning, a star subtitle) or
    quit / end of log: no HANDOFF or HANDOFF_LEAD
    debug line for him and no squad order line (ORDERS pools of the shipped cop_radio_messages.yml) in his chat. The
    first second after the clear step is grace (a line already in flight)."""
    rx = pools()
    steps = [(tt, d) for tt, k, d in ev if k == 'step']
    wins = []
    for i, (tt, d) in enumerate(steps):
        b = d.get('bot', 'Runner')
        if d.get('chat') != '/glw wanted clear':
            continue
        end = next((t2 for t2, d2 in steps[i + 1:] if d2.get('bot', 'Runner') == b and
                    (re.match(r'/glw wanted add', d2.get('chat', '')) or d2.get('quit'))), ev[-1][0] + 1)
        # a new crime of his re-wants him (brandish Igner ignores a fresh warning): the window ends there
        crime = [t2 for t2, k2, d2 in ev if k2 == 'msg' and tt < t2 < end and f'WARNING {b} result=ignored' in d2] +                 [c[0] for c in CH if c[1] == b and c[2] == 'title' and tt < c[0] < end and '★' in c[3]]
        wins.append((b, tt + 1, min([end] + crime)))
    if not wins:
        out('INCONCLUSIVE', 'search-clear', 'no /glw wanted clear step')
        return
    for b, t0, t1 in wins:
        ho = [d for tt, k, d in ev if k == 'msg' and t0 <= tt < t1 and re.search(rf'HANDOFF(_LEAD)? {b} ', d)]
        orders, rest = [], []
        for c in CH:
            m = re.match(r'^\[RADIO\] [^:]+: (.*)$', c[3])
            if c[1] != b or not m or not (t0 <= c[0] < t1):
                continue
            ks = {k for k, rs in rx.items() if any(r.match(m.group(1)) for r in rs)}
            (orders if ks & ORDERS or 'Lost him heading' in m.group(1) else rest).append(m.group(1))
        out('FAIL' if ho or orders else 'PASS', 'search-clear', f'{b} clear window {t1 - t0:.0f}s: HANDOFF lines '
            f'{len(ho)}, order lines {len(orders)} ' + ' | '.join(orders[:5]))
        if rest:
            out('INFO', 'search-clear-oth', f'{b} other radio lines: ' + ' | '.join(rest[:5]))


# ---- handoff-lead --------------------------------------------------------------------------------------------------
def spawn_between(lead, t_hand, tol=4.0):
    """R5-spawn-between (owner ruling T-423, 2026-10-27): a hand-off (bias=true) unit spawns on a ring centred between
    his live spot and the R5 lead (default the midpoint, clamped to the spawn range, R6-gated), so its spawn is closer
    to the lead than his live spot is (tol b for block rounding). A unit with a Cops N Crooks debug line saying
    fallback (fallback=true, not =false) within 1 s of its UNIT line is exempt (no valid spot: today's ring).
    Geometry only: today's ring around the live spot already passes when its units happen to land on the lead side
    (hl-env1, hl-env3), so R5-lead-go stays the behaviour verdict."""
    fb = [tt for tt, k, d in ev if k == 'msg' and 'Runner' in d and re.search(r'(?i)\bfallback\b(?!=false)', d)
          and 'Cops N Crooks' in d]
    rows = []

    def on_unit(t, d, pose, label, state):
        if d['bias'] == 'true' and t >= t_hand and pose['pos'] is not None:
            rows.append((t, d, pose['pos']))
    walk('Runner', on_unit=on_unit)
    if not rows:
        out('INCONCLUSIVE', 'R5-spawn-between', 'no bias=true UNIT with his position after the HANDOFF')
        return
    bad, fell, info, gains = [], [], [], []
    for t, d, live in rows:
        at = (d['at'][0] + 0.5, d['at'][2] + 0.5)
        d_live = math.dist((live[0], live[2]), (lead[0], lead[2]))
        gain = d_live - math.dist(at, (lead[0], lead[2]))
        gains.append((at, gain))
        info.append(f"{d['cop']} at={d['at'][0]},{d['at'][2]} {math.dist(at, (live[0], live[2])):.0f} b from him, "
                    f"lead {d_live:.0f}->{d_live - gain:.0f}")
        if gain >= -tol:
            continue
        (fell if any(abs(x - t) <= 1 for x in fb) else bad).append(f"{d['cop']} {gain:+.0f} b")
    out('FAIL' if bad else 'PASS', 'R5-spawn-between', f'{len(rows)} hand-off units: closer to the lead than his live '
        f'spot (tolerance {tol:.0f} b): {len(rows) - len(bad) - len(fell)}, fallback logged: {len(fell)} {fell[:4]}, '
        f'farther: {len(bad)} ' + ', '.join(bad[:6]))
    cx, cz = (sum(g[0][0] for g in gains) / len(gains), sum(g[0][1] for g in gains) / len(gains))
    live = rows[0][2]
    out('INFO', 'R5-spawn-rows', f'centroid {cx:.0f},{cz:.0f} gains toward the lead '
        f'{math.dist((live[0], live[2]), (lead[0], lead[2])) - math.dist((cx, cz), (lead[0], lead[2])):+.0f} b; '
        + '; '.join(info[:8]))



def judge_handoff():
    BOX = (0.5, -60.0, 200.5)
    LEAD = re.compile(r'HANDOFF_LEAD Runner at=(-?\d+),(-?\d+),(-?\d+) heading=(\S+) dir=(-?[\d.]+),(-?[\d.]+) '
                      r'from=(-?\d+),(-?\d+),(-?\d+)')
    marks = {d: tt for tt, k, d in ev if k == 'mark'}
    leads = [(tt, m) for tt, k, d in ev if k == 'msg' for m in [LEAD.search(d)] if m]
    hand = [(tt, d) for tt, k, d in ev if k == 'msg' and re.search(r'HANDOFF Runner heading=', d)]
    gobox = marks.get('GO-BOX')
    if gobox is None:
        out('INCONCLUSIVE', 'setup', 'no GO-BOX marker')
        return
    leads = [x for x in leads if x[0] >= gobox - 1]
    if not leads:
        out('FAIL', 'HANDOFF_LEAD', f'no HANDOFF_LEAD line after the hop (HANDOFF lines: {len(hand)})')
    else:
        t_lead, m = leads[0]
        at = tuple(int(m.group(i)) for i in (1, 2, 3))
        frm = tuple(int(m.group(i)) for i in (7, 8, 9))
        dx, dz = float(m.group(5)), float(m.group(6))
        out('PASS', 'HANDOFF_LEAD', f'+{t_lead - gobox:.0f}s after the hop: at={at} heading={m.group(4)} '
            f'dir={dx},{dz} from={frm} ({len(leads)} lines)')
        out('PASS' if m.group(4) == 'west' and dx / (math.hypot(dx, dz) or 1e-9) < -0.7 else 'FAIL', 'R5-heading',
            f'heading the squad saw: {m.group(4)} dir={dx},{dz} (he hopped west, the box is due south)')
        run = math.dist((at[0], at[2]), (frm[0], frm[2]))
        out('INFO', 'R5-lead-len', f'lead {run:.1f} b from the last-known spot (Lead_Blocks 24; shorter only at a wall)')
    heard = [c for c in CH if c[1] == 'Runner' and gobox - 1 <= c[0] <= gobox + 15 and 'Lost him heading' in c[3]]
    out('PASS' if heard and 'heading west' in heard[0][3] else 'FAIL', 'R5-heard',
        f'Handoff line reached him: {heard[0][3] if heard else "none"}')
    if leads:
        lead = (at[0] + 0.5, float(at[1]), at[2] + 0.5)
        t_hand = hand[0][0] if hand else gobox
        units = {d['cop'] for tt, k, d in ev if k == 'unit' and d['bot'] == 'Runner' and d['bias'] == 'true'
                 and tt >= t_hand}
        win = [p for p in probes('Runner') if p['label'].startswith('BOX-') and t_lead <= p['t'] <= t_lead + 16]
        if len(win) < 12 or not units:
            out('INCONCLUSIVE', 'R5-lead-go', f'{len(win)} probes in the 15 s after HANDOFF_LEAD, {len(units)} '
                'biased units')
        else:
            to_lead, to_box, still, away, rows, short = [], [], [], [], [], []
            # ACC9: a unit's track ends at its first PURSUING->RETURNING line (its walk home is not the lead chase)
            ret = {}
            for tt, k, d in ev:
                if k == 'trans' and tt >= t_hand and d[1] == 'PURSUING' and d[2] == 'RETURNING':
                    ret.setdefault(d[0], tt)
            for cop in sorted(units):
                seq = [p['npcs'][cop] for p in win if cop in p['npcs'] and p['t'] <= ret.get(cop, math.inf)]
                if len(seq) < 3:
                    short.append(cop)
                    continue
                a, z = seq[0], seq[-1]
                mv = (z[0] - a[0], z[2] - a[2])
                n = math.hypot(*mv)

                def ang(v):
                    m2 = math.hypot(*v) or 1e-9
                    return math.degrees(math.acos(max(-1.0, min(1.0, (mv[0] * v[0] + mv[1] * v[1]) / ((n or 1e-9) * m2)))))
                dl0 = math.dist((a[0], a[2]), (lead[0], lead[2]))
                dl1 = math.dist((z[0], z[2]), (lead[0], lead[2]))
                db = [math.dist((x[0], x[2]), (BOX[0], BOX[2])) for x in seq]
                a_box = ang((BOX[0] - a[0], BOX[2] - a[2]))
                a_lead = ang((lead[0] - a[0], lead[2] - a[2]))
                back = f'returning +{ret[cop] - t_lead:.0f}s' if cop in ret else 'no return'
                rows.append(f'{cop} lead {dl0:.0f}->{dl1:.0f} ({back}) box {db[0]:.0f}->{db[-1]:.0f} (min {min(db):.0f}) '
                            f'moved {n:.0f} b, {a_lead:.0f} deg off the lead line, {a_box:.0f} off the box line')
                if min(db) < 7 and db[0] >= 10:
                    to_box.append(cop + ' reached the box')
                elif n < 3:
                    still.append(cop)
                elif a_box < min(45.0, a_lead):
                    to_box.append(cop + f' {a_box:.0f} deg off the box line')
                elif dl1 > dl0 + 3 and db[-1] < db[0] - 3:
                    to_box.append(cop + ' left the lead behind and closed on the box')
                elif dl1 < dl0 - 3 or dl1 <= 8:
                    to_lead.append(cop)
                else:
                    away.append(cop + f' lead {dl0:.0f}->{dl1:.0f}')
            ok = bool(to_lead) and not to_box and not away
            out('PASS' if ok else 'FAIL', 'R5-lead-go', f'{len(rows)} biased units probed over {len(win)} probes: '
                f'toward the lead {len(to_lead)}, toward the sealed box {len(to_box)} {to_box[:4]}, moving but not toward '
                f'the lead {len(away)} {away[:4]}, still {len(still)}, '
                f'under 3 probes before returning {len(short)} {short[:4]}')
            out('INFO', 'R5-lead-rows', '; '.join(rows[:8]))
        spawn_between(lead, t_hand)  # outside the lead-go branch: judged even when R5-lead-go is INCONCLUSIVE
    # the spawner's R6 test is the cone AND a clear line (CopSpawnManager.inViewOf): a spot the box walls hide is fine,
    # so as the other kinds the yaw-only cone is judged on open ground only (contact, hops, OUT, WATCH, AWAY)
    cone_check('Runner', r'^(GO-BOX|BOX-)')


SEALED = {'spawn': r'^(PP-SEALED|PP-DROP|PP-WANTED1)', 'radio': r'^(GO-ROOM|ROOM|CLEAR|WANTED3|END)',
          'handoff': r'^(GO-BOX|BOX-)'}
{'spawn': judge_spawn, 'radio': judge_radio, 'brandish': judge_brandish, 'handoff': judge_handoff}[KIND]()
if KIND in SEALED:
    r3_in_view('Runner', SEALED[KIND])
    r3_cap('Runner')
search_after_clear()
fails = results.count('FAIL')
print(f'OVERALL {"FAIL" if fails else "PASS"} ({fails} FAIL, {results.count("PASS")} PASS, '
      f'{results.count("INCONCLUSIVE")} INCONCLUSIVE) {RUN}')
sys.exit(1 if fails else 0)
