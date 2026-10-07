#!/usr/bin/env node
// h11-verdict.js - Phase H11 acceptance verdicts (spec section 8, items 1-9) from harness runs.
//   node h11-verdict.js           -> runs/h11-<key>-1 and runs/h11-<key>-2 (the new jars)
//   node h11-verdict.js baseline  -> the Keystone 1.11.3 / Gangland 0.10.0 reproduction runs (door and long must FAIL)
'use strict';
const fs = require('fs');
const path = require('path');

const RUNS = process.env.H11_RUNS || path.join(__dirname, '..', 'runs');
const COP = /^(?:Officer|Sergeant|Lieutenant|SWAT|Military)$|^[A-Z][a-z]+ #\d+$/; // T18: 0.15 entity names are '<First> #<badge>'
const NUM = '(-?[\\d.]+(?:E-?\\d+)?)d';
const SAMPLE = new RegExp(`INFO\\]: (.+?) has the following entity data: \\[${NUM}, ${NUM}, ${NUM}\\]`);
const STEP = /^>>> STEP (\d+) (.*)$/;
const TRANS = /Transitioning (cop|civilian) (.+?)-(\d+) from (\w+) state to (\w+) state/;

function load(run) {
  const log = path.join(RUNS, run, 'server.log');
  if (!fs.existsSync(log)) return null;
  const steps = [], samples = [], trans = [], hp = {};
  let step = -1;
  for (const line of fs.readFileSync(log, 'utf8').split('\n')) {
    let m;
    if ((m = STEP.exec(line))) { step = +m[1]; steps[step] = m[2]; }
    else if ((m = SAMPLE.exec(line))) samples.push({ step, name: m[1], x: +m[2], y: +m[3], z: +m[4] });
    else if ((m = TRANS.exec(line)) && m[4] !== m[5]) trans.push({ step, kind: m[1], name: m[2], id: m[3], from: m[4], to: m[5] });
  }
  const win = path.join(RUNS, run, 'windows.txt');
  if (fs.existsSync(win)) for (const m of fs.readFileSync(win, 'utf8').matchAll(/INV\[(\S+)\] \S+ held=\d+ hp=([\d.]+)/g)) hp[m[1]] = +m[2];
  return { steps, samples, trans, hp, at: re => steps.findIndex(s => s !== undefined && re.test(s)) };
}

const h = (s, x, z) => Math.hypot(s.x - x, s.z - z);
const fmt = s => `${s.name}@${s.x.toFixed(1)},${s.y.toFixed(1)},${s.z.toFixed(1)}#${s.step}`;
const onRoof = s => s.y >= -48.6 && s.x >= 96 && s.x < 105 && s.z >= 96 && s.z < 105;
const between = (r, from, to) => r.samples.filter(s => s.step > from && (to < 0 || s.step < to));
const groups = list => {
  const m = new Map();
  for (const s of list) m.set(s.step, [...(m.get(s.step) || []), s]);
  return [...m.keys()].sort((a, b) => a - b).map(k => m.get(k));
};
const reach = (phase, ok, what) => r => {
  const from = r.at(phase);
  if (from < 0) return { pass: false, note: 'phase step not found' };
  const hit = between(r, from, r.at(/wanted clear/)).find(s => COP.test(s.name) && ok(s));
  return { pass: !!hit, note: hit ? `${what}: ${fmt(hit)}` : `no cop ${what}` };
};

const CHECKS = {
  build: { item: '1', label: 'Reported build', check: r => {
    const tp = r.steps.map(s => /tp \S+ (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)"/.exec(s || '')).find(Boolean);
    if (!tp) return { pass: false, note: 'no tp step' };
    const [x, y, z] = tp.slice(1).map(Number);
    const cx = Number.isInteger(x) ? x + 0.5 : x, cz = Number.isInteger(z) ? z + 0.5 : z;
    const hit = r.samples.find(s => COP.test(s.name) && Math.hypot(s.x - cx, s.y - y, s.z - cz) <= 3);
    return { pass: !!hit, note: hit ? `cop reached the player: ${fmt(hit)}` : 'no cop within 3 blocks of the player' };
  } },
  stairs: { item: '2', label: 'Straight-stair tower', check: reach(/tp \S+ 100 -48 100"/, onRoof, 'on the roof') },
  ladder: { item: '2', label: 'Ladder tower', check: reach(/tp \S+ 100 -48 100"/, onRoof, 'on the roof') },
  door: { item: '3', label: 'Door on the route', check: reach(/tp \S+ 100 -48 98"/, s => onRoof(s) && s.z < 102, 'through the door') },
  switchback: { item: '4', label: 'Switchback stairs', check: reach(/tp \S+ 100 -48 100"/, onRoof, 'on the roof') },
  long: { item: '5', label: 'Approach > 64 blocks', check: reach(/tp \S+ 100 -48 100"/, s => onRoof(s) || h(s, 100.5, 100.5) <= 8, 'at the tower') },
  pillar: { item: '6', label: 'Unreachable pillar', check: r => {
    const from = r.at(/tp \S+ 100 -48 100"/), to = r.at(/wanted clear/);
    if (from < 0) return { pass: false, note: 'phase step not found' };
    const late = groups(between(r, from, to)).slice(6);
    const bare = late.filter(ss => !ss.some(s => COP.test(s.name) && h(s, 100.5, 100.5) <= 12)).length;
    const gaveUp = r.trans.filter(t => t.kind === 'cop' && t.to === 'RETURNING' && t.step > from && (to < 0 || t.step < to)).length;
    const bounce = r.trans.filter(t => t.kind === 'cop' && t.from === 'RETURNING' && /PURSUING|COMBAT/.test(t.to)).length;
    const dbg = r.trans.length ? '' : ' (no debug transitions logged)';
    return { pass: late.length >= 20 && bare === 0 && gaveUp === 0 && bounce === 0,
      note: `${late.length} samples after 30 s, ${bare} without a cop within 12 blocks of the base; cop->RETURNING ${gaveUp}; RETURNING->PURSUING/COMBAT ${bounce}${dbg}` };
  } },
  ranged: { item: '7', label: 'Armed tier holds, then climbs', check: r => {
    const p1 = r.at(/tp \S+ 100 -51 107"/), p2 = r.at(/tp \S+ 100 -48 100"/), to = r.at(/wanted clear/);
    if (p1 < 0 || p2 < 0) return { pass: false, note: 'phase steps not found' };
    const band = between(r, p1, p2).filter(s => s.name === 'SWAT' && s.y <= -59
      && Math.hypot(s.x - 100.5, s.y + 51, s.z - 107.5) >= 6.5 && Math.hypot(s.x - 100.5, s.y + 51, s.z - 107.5) <= 13);
    const hp0 = r.hp['hold-start'], hp1 = r.hp['hold-end'];
    const shot = hp0 !== undefined && hp1 !== undefined && hp1 < hp0;
    const climb = between(r, p2, to).find(s => s.name === 'SWAT' && onRoof(s));
    return { pass: band.length >= 3 && shot && !!climb,
      note: `SWAT holding on the ground in the 7-12 band: ${band.length} samples; hp ${hp0} -> ${hp1}; climbed: ${climb ? fmt(climb) : 'no'}` };
  } },
  faction: { item: '8', label: 'Faction alert', check: r => {
    if (!r.trans.length) return { pass: null, note: 'no debug transitions in server.log (Debug.Modules not effective)' };
    const hit = r.at(/attackAt/);
    const combat = r.trans.filter(t => t.kind === 'civilian' && t.to === 'COMBAT' && t.step >= hit);
    const gang = new Set(combat.filter(t => t.name === 'Gang Member').map(t => t.id));
    const turf = combat.filter(t => t.name === 'Turf Defender').length;
    return { pass: hit >= 0 && gang.size === 2 && turf === 0,
      note: `Gang Member ids entering COMBAT: ${[...gang].join(',') || 'none'} (want 2: victim + ally, never the far one); Turf Defender entering COMBAT: ${turf}` };
  } },
  losbreak: { item: '9', label: 'Line of sight broken', check: r => {
    const p2 = r.at(/tp \S+ 100 -60 116"/), p3 = r.at(/tp \S+ 100 -60 94"/), to = r.at(/wanted clear/);
    if (p2 < 0 || p3 < 0) return { pass: false, note: 'phase steps not found' };
    const hidden = groups(between(r, p2, p3).filter(s => COP.test(s.name)));
    const back = between(r, p3, to).filter(s => COP.test(s.name));
    const k = hidden.slice(0, 4).findIndex(ss => ss.some(s => h(s, 100.5, 100.5) <= 5));
    const beeline = hidden.slice(0, 2).some(ss => ss.some(s => s.z >= 112));
    const fan = k >= 0 && hidden.slice(k + 1).some(ss => ss.some((p, i) => ss.slice(i + 1).some(q => h(p, q.x, q.z) >= 6)));
    const found = hidden.some(ss => ss.some(s => h(s, 100.5, 116.5) <= 4)) || back.some(s => h(s, 100.5, 94.5) <= 4);
    return { pass: k >= 0 && !beeline && fan && found,
      note: `last-seen spot reached: ${k >= 0 ? 'sample ' + (k + 1) : 'no'}; beeline behind the wall: ${beeline}; fanned out: ${fan}; re-acquired: ${found}` };
  } },
};

const BASELINE = { stairs: ['npc-elev-a'], ladder: ['npc-elev-c'], door: ['v1-door-1', 'v1-door-2'],
  switchback: ['v2-switchback-1', 'v2-switchback-2'], long: ['v4-longapproach-a', 'v4-longapproach-b'] };
const baseline = process.argv[2] === 'baseline';
const cell = x => (x.pass === true ? 'PASS' : x.pass === false ? 'FAIL' : x.pass === null ? 'UNVERIFIED' : 'NOT RUN');
const rows = ['| # | Scenario | Trial 1 | Trial 2 | Evidence |', '|---|---|---|---|---|'];
for (const [key, c] of Object.entries(CHECKS)) {
  const runs = baseline ? BASELINE[key] : [`h11-${key}-1`, `h11-${key}-2`];
  if (!runs) continue;
  const res = runs.map(run => { const r = load(run); return r ? c.check(r) : { pass: undefined, note: 'not run' }; });
  rows.push(`| ${c.item} | ${c.label} | ${cell(res[0])} | ${res[1] ? cell(res[1]) : '-'} | ${res.map((x, i) => `T${i + 1}: ${x.note}`).join('; ')} |`);
}
console.log(rows.join('\n'));
