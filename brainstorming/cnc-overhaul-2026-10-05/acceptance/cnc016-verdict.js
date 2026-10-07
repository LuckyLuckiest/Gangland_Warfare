#!/usr/bin/env node
// cnc016-verdict.js <row> <run-dir> - PASS/FAIL/REVIEW for one 0.16 acceptance row S1..S16 from a harness run.
//   run dir = E:/Programming/java/wt/_programme/runs/<server>--<scenario>. The R/N/A regression rows are judged by cnc-verdict.js.
// Reads chat.txt (bot chat incl. the `say CNCMARK:` markers the scenarios emit) and server.log (the C8-C13 debug lines:
//   DISPATCH, UNIT, PERIMETER, HANDOFF, EVASION, HOSPITAL, WARD_BILL, SHIELD).
// Exit 0 = every check PASS, 1 = a FAIL, 2 = REVIEW only (needs a human line-read).
'use strict';
const fs = require('fs'), path = require('path');
const [row, dir] = process.argv.slice(2);
if (!row || !dir) { console.error('usage: cnc016-verdict.js <S1..S16> <run-dir>'); process.exit(64); }
const NOISE = /FakeSmoke|PlaceholderAPI\] Failed to download|ViaVersion\] Could not check|UpdateChecker\] Unable|Citizens\] Unable to check/;
const strip = s => s.replace(/\u00a7./g, '');
const results = []; let worst = 0;
const rec = (name, state, detail = '') => {
  results.push(`${state.padEnd(6)} ${name}${detail ? ' - ' + detail : ''}`);
  worst = Math.max(worst, state === 'FAIL' ? 1 : state === 'REVIEW' ? 2 : 0);
};
const ok = (name, cond, detail = '', soft = false) => rec(name, cond ? 'PASS' : soft ? 'REVIEW' : 'FAIL', detail);
const rd = f => { try { return fs.readFileSync(path.join(dir, f), 'utf8'); } catch (e) { return ''; } };
const chat = rd('chat.txt').split('\n').map(strip), log = rd('server.log').split('\n').map(strip), summary = rd('summary.txt');

const secs = l => { const m = /^\[(\d+):(\d+):(\d+)/.exec(l || ''); return m ? +m[1] * 3600 + +m[2] * 60 + +m[3] : NaN; };
const idx = (lines, tag) => lines.findIndex(l => l.includes('CNCMARK:' + tag));
const markAt = tag => { const l = log.find(x => /^\[\d+:\d+:\d+/.test(x) && x.includes('CNCMARK:' + tag)); return l ? secs(l) : NaN; };
const after = (lines, tag) => { const i = idx(lines, tag); return i < 0 ? lines : lines.slice(i + 1); };
const money = l => { const m = /([\d,]+(?:\.\d+)?)/.exec((l || '').replace(/[^\d.,\s]/g, ' ')); return m ? parseFloat(m[1].replace(/,/g, '')) : NaN; };
// the balance reply printed just before a BAL:<tag> marker
const balAt = tag => {
  const i = idx(chat, 'BAL:' + tag);
  if (i < 0) return NaN;
  for (let k = i - 1; k >= Math.max(0, i - 6); k--) if (/balance:/i.test(chat[k])) return money(chat[k + 1]);
  return NaN;
};
// the status reply printed just before a STATUS:<tag> marker; stars = count of filled stars in it, -1 when no marker
const statusAt = tag => {
  const i = idx(chat, 'STATUS:' + tag);
  if (i < 0) return null;
  const prev = chat.slice(Math.max(0, i - 8), i);
  const k = prev.map(l => /wanted|star|\u2605|\u2606/i.test(l)).lastIndexOf(true);
  return k < 0 ? '' : prev.slice(k).join(' | ');
};
const stars = s => s === null ? -1 : (s.match(/\u2605/g) || []).length;
const NOWANT = s => s !== null && (/don'?t have a wanted level|not wanted/i.test(s) || (stars(s) === 0));

// ---- debug lines ----
const g = (l, re) => (re.exec(l) || [])[1];
const dispatches = log.filter(l => /DISPATCH \S+ count=/.test(l)).map(l => ({
  at: secs(l), line: l, player: g(l, /DISPATCH (\S+) count=/), count: +g(l, /count=(\d+)/), station: g(l, /station=(.+?) eta=/),
  eta: +g(l, /eta=(\d+)s/), hold: +g(l, /hold=(\d+)s/), reason: g(l, /reason=(\w+)/), bias: g(l, /bias=(\w+)\s*$/) === 'true' }));
const units = log.filter(l => /UNIT \S+ callsign=/.test(l)).map(l => ({
  at: secs(l), line: l, player: g(l, /UNIT (\S+) callsign=/), tier: +g(l, /tier=(\d+)/), role: g(l, /role=(\S+)/),
  fromStation: g(l, /fromStation=(\w+)/) === 'true', hidden: g(l, /hidden=(\w+)/) === 'true', bias: g(l, /bias=(\w+) ahead=/) === 'true',
  ahead: g(l, /ahead=(\w+|-)/) }));
const evasions = log.filter(l => /EVASION \S+ speed=/.test(l)).map(l => ({
  at: secs(l), line: l, speed: +g(l, /speed=([\d.]+)/), zone: +g(l, /zone=([\d.]+)/), hideout: +g(l, /hideout=([\d.]+)/), quiet: +g(l, /quiet=([\d.]+)/) }));
const holds = log.filter(l => /EVASION \S+ hold=enroute/.test(l)).map(l => ({ at: secs(l), line: l }));
const short = l => (l || '').replace(/^.*?(DISPATCH|UNIT|EVASION|PERIMETER|HANDOFF|HOSPITAL|WARD_BILL|SHIELD) /, '$1 ').slice(0, 170);

// ---- every row: no server ERROR / org.luckyraven exception, and the harness steps all passed ----
const errs = log.filter(l => /\/(ERROR|FATAL)\]|^\s+at org\.luckyraven|Exception:/.test(l) && !NOISE.test(l));
rec('no server ERROR / org.luckyraven exception', errs.length ? 'FAIL' : 'PASS', errs.slice(0, 3).join(' // '));
const f = /FAILED STEPS \((\d+)\)/.exec(summary);
rec('harness steps', f && +f[1] === 0 ? 'PASS' : 'FAIL', f ? `${f[1]} failed step(s)` : 'summary.txt missing');

switch (row) {
  case 'S1': {
    const list = chat.slice(0, Math.max(0, idx(chat, 'S1:LIST'))).slice(-20);
    ok('station Northside Station saved (chat)', chat.some(l => /Station .*Northside Station.* saved/i.test(l)));
    ok('district Docks saved (chat)', chat.some(l => /Region .*Docks.*district.* saved/i.test(l)));
    ok('list shows the station row', list.some(l => /station \d+ Northside Station \S+ 100 -61 40/i.test(l)), list.filter(l => /station \d+/.test(l)).join(' | '));
    ok('list shows the Docks region row with its district tag', list.some(l => /region \d+ Docks .*district/i.test(l)), list.filter(l => /region \d+/.test(l)).join(' | '));
    break;
  }
  case 'S2': {
    const radio = chat.find(l => /units? en route from Northside Station, ETA \d+ s/i.test(l));
    ok('radio: units en route from Northside Station with an ETA', !!radio, radio);
    const eta = radio ? +g(radio, /ETA (\d+) s/) : NaN;
    ok(`ETA about 9 s (90 blocks at Unit_Speed 10): ${eta}`, eta >= 8 && eta <= 10);
    const d = dispatches[0];
    ok('DISPATCH line names Northside Station with the same ETA', d && d.station === 'Northside Station' && d.eta === eta, d && short(d.line));
    const u = units[0];
    ok('first UNIT line hidden=true fromStation=true', u && u.hidden && u.fromStation, u && short(u.line));
    if (d && u) {
      const gap = u.at - d.at;
      ok(`first unit arrives after the delay (${gap} s after DISPATCH, ETA ${d.eta})`, gap >= d.eta - 1 && gap <= d.eta + 20, '', true);
    }
    break;
  }
  case 'S3': {
    const mine = units.filter(u => u.player === 'Runner');
    const tiers = [...new Set(mine.map(u => u.tier))].sort();
    ok(`UNIT lines at tiers 2 and 3 (seen ${tiers.join(',') || 'none'} over ${mine.length} units)`, tiers.includes(2) && tiers.includes(3));
    const bad = mine.filter(u => (/^(Pointman|Assault)$/.test(u.role) && u.tier !== 2) || (/^(Commander|Defender|Marksman)$/.test(u.role) && u.tier !== 3));
    ok('roles carry their configured tier (Pointman/Assault 2, Commander/Defender/Marksman 3)', bad.length === 0, bad.slice(0, 3).map(u => short(u.line)).join(' // '));
    break;
  }
  case 'S4': {
    const wipeAt = markAt('S4:WIPED');
    ok('Wipe_Refill radio line', chat.some(l => /Squad down\. Backup inbound in \d+ s/i.test(l)));
    const d = dispatches.find(x => x.reason === 'wipe');
    ok('DISPATCH ... reason=wipe hold>0', d && d.hold > 0, d && short(d.line));
    if (d) ok(`hold = Breather.Seconds for 3 stars (10): ${d.hold}`, d.hold === 10, '', true);
    const firstAfter = units.find(u => u.at >= wipeAt);
    if (d && firstAfter) ok(`no unit before the breather ends (${firstAfter.at - wipeAt} s after the wipe, hold ${d.hold})`, firstAfter.at - wipeAt >= d.hold - 1);
    else ok('a refill unit spawned after the wipe', !!firstAfter);
    break;
  }
  case 'S5': {
    const p = log.find(l => /PERIMETER \S+ start posts=/.test(l));
    ok('PERIMETER start posts=2', p && /posts=2\b/.test(p), p && short(p));
    if (p) { const r = +g(p, /radius=([\d.]+)/); ok(`post ring radius <= 32 (min(zone, 0.8 x Sight_Range 40)): ${r}`, r > 0 && r <= 32.01, '', true); }
    const up = chat.filter(l => /Holding the corner/i.test(l)).length;
    ok(`two Post_Up lines (seen ${up})`, up >= 2, 'radio rate-limiting can fold two lines into one: read the transcript', true);
    results.push('       ' + log.filter(l => /PERIMETER /.test(l)).map(short).join(' | ').slice(0, 300));
    break;
  }
  case 'S6': {
    const h = log.find(l => /HANDOFF \S+ heading=/.test(l));
    ok('HANDOFF line', !!h, h && short(h));
    if (h) ok('heading is east (Runner moved +x)', /heading=east/i.test(h), short(h), true);
    ok('Handoff radio line', chat.some(l => /Lost him heading \w+\. Units ahead/i.test(l)));
    const hAt = h ? secs(h) : NaN;
    const b = dispatches.find(d => d.bias && d.at >= hAt);
    ok('DISPATCH ... bias=true after the hand-off', !!b, b && short(b.line));
    const us = units.filter(u => u.bias && u.at >= hAt);
    ok('UNIT bias=true', us.length > 0, us[0] && short(us[0].line));
    // C8: only a hidden ring spot is filtered by the bias (steps 2-3); a unit that found no hidden spot takes the unbiased
    // fallback (step 4, hidden=false), which the walls in the scenario make rare: REVIEW, not FAIL (T22)
    const hid = us.filter(u => u.hidden), open = us.filter(u => !u.hidden);
    ok('UNIT ahead=true on every hidden biased unit', hid.length > 0 && hid.every(u => u.ahead === 'true'), hid.filter(u => u.ahead !== 'true').map(u => short(u.line)).slice(0, 2).join(' // '));
    if (open.length) ok(`biased units on the unbiased fallback (no hidden spot found): ${open.length}`, false, open.map(u => short(u.line)).slice(0, 2).join(' // '), true);
    break;
  }
  case 'S7': {
    const e = evasions.find(x => x.hideout === 2);
    ok('EVASION line with hideout=2.0', !!e, e && short(e.line));
    if (e) ok(`speed is zone x hideout (capped at Max_Speed 4): ${e.speed}`, Math.abs(e.speed - Math.min(4, e.zone * e.hideout * Math.max(1, e.quiet))) < 0.011, short(e.line), true);
    const before = evasions.filter(x => x.at <= markAt('S7:MOVE'));
    ok('no hideout bonus before Runner entered the Boathouse', before.every(x => x.hideout === 1), before.map(x => short(x.line)).slice(0, 2).join(' // '), true);
    break;
  }
  case 'S8': {
    ok('Returning_To_Patrol radio line', chat.some(l => /Units returning to patrol/i.test(l)));
    const crime = markAt('S8:CRIME'), patrol = markAt('S8:PATROL');
    ok(`after about a quiet minute (${patrol - crime} s from the crime)`, patrol - crime >= 55 && patrol - crime <= 110, '', true);
    const q = evasions.find(x => x.quiet > 1);
    ok('EVASION line with quiet>1', !!q, q && short(q.line));
    break;
  }
  case 'S9': {
    const used = chat.find(l => /Your contact made 1 star\(s\) disappear for/i.test(l));
    ok('Contact.Used text', !!used, used);
    const amt = used ? money(used.replace(/^.*disappear for/i, '')) : NaN;
    ok(`price 1000 (Price_Per_Star x 1): ${amt}`, amt === 1000);
    const a = balAt('before'), b = balAt('after');
    ok(`balance ${a} -> ${b}, paid ${a - b}`, a - b === 1000);
    const s0 = stars(statusAt('S9-before')), s1 = stars(statusAt('S9-after'));
    ok(`star level fell by one (${s0} -> ${s1})`, s0 >= 2 && s1 === s0 - 1, statusAt('S9-after'));
    break;
  }
  case 'S10': {
    const seen = chat.find(l => /Not while a cop has eyes on you/i.test(l));
    ok('Contact.Seen text', !!seen, seen);
    const a = balAt('before'), b = balAt('after');
    ok(`balance unchanged (${a} -> ${b})`, a === b);
    ok('star level unchanged (still wanted)', stars(statusAt('S10-after')) >= 2, statusAt('S10-after'));
    ok('sign did not report an invalid sign', !chat.some(l => /invalid sign/i.test(l)), 'if this fails the console-set sign was not recognised: place it by hand', false);
    break;
  }
  case 'S11': {
    ok('not wanted before', NOWANT(statusAt('S11-before')), statusAt('S11-before'));
    ok('two provoked kills, no star', NOWANT(statusAt('S11-after')), statusAt('S11-after'));
    break;
  }
  case 'S12': {
    const a = balAt('before'), b = balAt('after');
    ok(`two posted bounties claimed, no star`, NOWANT(statusAt('S12-after')), statusAt('S12-after'));
    ok(`both bounties paid (${a} -> ${b})`, b - a >= 1000, 'other rewards also show: read the transcript', true);
    break;
  }
  case 'S13': {
    ok('Hunter not wanted before', NOWANT(statusAt('S13-hunter-before')), statusAt('S13-hunter-before'));
    const s = stars(statusAt('S13-hunter-after'));
    ok(`two unposted five-star kills, Hunter gains a star (${s})`, s >= 1, statusAt('S13-hunter-after'));
    break;
  }
  case 'S14': case 'S15': {
    const t = row;
    const mine = l => /\bRunner\b/.test(l);
    const hosp = log.filter(l => /HOSPITAL \S+ waypoint=/.test(l) && mine(l));
    ok('HOSPITAL line names the Infirmary', hosp.length === 1 && /waypoint=Infirmary/.test(hosp[0]), hosp.map(short).join(' | '));
    const bills = log.filter(l => /WARD_BILL \S+ amount=/.test(l) && mine(l));
    ok(`exactly one WARD_BILL (${bills.length})`, bills.length === 1, bills.map(short).join(' | '));
    if (bills.length) {
      const at = g(bills[0], /at=(\w+)/), amount = money(g(bills[0], /amount=([\d.,]+)/));
      ok(`bill charged at ${t === 'S14' ? 'RESPAWN' : 'DEATH'} (${at})`, at === (t === 'S14' ? 'RESPAWN' : 'DEATH'));
      ok(`bill amount 750 (15% of 5000): ${amount}`, amount === 750, '', true);
    }
    const sh = log.filter(l => /SHIELD \S+ seconds=/.test(l) && mine(l));
    ok('SHIELD line seconds=5 (owner ruling D19)', sh.length >= 1 && /seconds=5\b/.test(sh[0]), sh.map(short).join(' | '));
    const a = balAt('before'), b = balAt('after');
    ok(`wallet paid the one bill (${a} -> ${b})`, a - b === 750, '', true);
    const pos = log.filter(l => /entity data: \[/.test(l)).pop();
    const m = pos && /\[(-?[\d.]+)d?, (-?[\d.]+)d?, (-?[\d.]+)d?\]/.exec(pos);
    if (m) ok(`woke at the Infirmary (150,260): ${(+m[1]).toFixed(1)},${(+m[3]).toFixed(1)}`, Math.hypot(+m[1] - 150, +m[3] - 260) < 4, pos, true);
    else rec('woke at the Infirmary', 'REVIEW', 'no `data get entity` line');
    if (t === 'S15') {
      const items = log.filter(l => /CNCITEM/.test(l) && !/say CNCITEM/.test(l));
      ok(`no cash item at the body with the bundled money.yml (${items.length} item(s))`, items.length === 0);
    }
    break;
  }
  case 'S16': {
    ok('wanted before quit', stars(statusAt('S16-before-quit')) >= 1, statusAt('S16-before-quit'));
    const d = dispatches.find(x => x.reason === 'restore');
    ok('DISPATCH ... reason=restore hold=15s', d && d.hold >= 14 && d.hold <= 15, d && short(d.line));
    const h = holds.find(x => d && x.at >= d.at);
    ok('EVASION hold=enroute after the restore', !!h, h && short(h.line));
    const u = d && units.find(x => x.at >= d.at);
    if (d && u) ok(`first unit only after the rejoin grace (${u.at - d.at} s after the DISPATCH line, hold ${d.hold})`, u.at - d.at >= d.hold - 1);
    else ok('a unit spawned after the rejoin', !!u);
    ok(`the star is still there when the first unit spawns (${stars(statusAt('S16-at-first-unit'))})`, stars(statusAt('S16-at-first-unit')) >= 1, statusAt('S16-at-first-unit'));
    break;
  }
  case 'S17a': {
    const t = chat.find(l => /police bribe star/i.test(l));
    ok('Bribe_Star.Taken text', !!t, t);
    const s0 = stars(statusAt('S17-before')), s1 = stars(statusAt('S17-after'));
    ok(`star level fell by one (${s0} -> ${s1})`, s0 >= 2 && s1 === s0 - 1, statusAt('S17-after'));
    break;
  }
  case 'S17b': {
    const t = chat.find(l => /Not with a cop watching/i.test(l));
    ok('Bribe_Star.Seen text', !!t, t);
    ok('no star taken', !chat.some(l => /police bribe star/i.test(l)));
    const s0 = stars(statusAt('S17-before')), s1 = stars(statusAt('S17-after'));
    ok(`star level unchanged (${s0} -> ${s1})`, s0 >= 2 && s1 === s0, statusAt('S17-after'));
    break;
  }
  default:
    console.error('unknown row ' + row);
    process.exit(64);
}
console.log(`== ${row} ==\n` + results.join('\n') + `\n== ${worst === 0 ? 'PASS' : worst === 1 ? 'FAIL' : 'REVIEW'} ==`);
process.exit(worst);
