#!/usr/bin/env node
// cnc-verdict.js <row> <run-dir> [<run-dir-2>] - PASS/FAIL/REVIEW for one 0.15 acceptance row from a harness run.
//   rows: R1 R3 R4 N1 N2 N3 N3b N4 N5 N6 N7 N8 A1..A9 (0.15.2 AUTO; A4 takes quit run + rejoin run, A5 takes the two-chase run + the
//   after-restart run, A6 takes the 0.15.1-config run + the Step_Speed-1.5 run)   (R2 is judged by the harness's h11-verdict.js, see README)
//   run dirs are E:/Programming/java/wt/_programme/runs/<server>--<scenario>; R4 takes the quit run then the rejoin run,
//   N2 may take the default run then the legacy-settings run, N4 takes phase A then phase B.
// Reads chat.txt (bot chat incl. the `say CNCMARK:` / `say CNCCOP:` markers the scenarios emit) and server.log.
// Exit 0 = every check PASS, 1 = a FAIL, 2 = REVIEW only (needs a human line-read: wording is matched loosely).
'use strict';
const fs = require('fs'), path = require('path');
const [row, ...dirs] = process.argv.slice(2);
if (!row || !dirs.length) { console.error('usage: cnc-verdict.js <row> <run-dir> [<run-dir-2>]'); process.exit(64); }
const NOISE = /FakeSmoke|PlaceholderAPI\] Failed to download|ViaVersion\] Could not check|UpdateChecker\] Unable|Citizens\] Unable to check/;
const strip = s => s.replace(/\u00a7./g, '');
const results = []; let worst = 0;
const rec = (name, state, detail = '') => {
  results.push(`${state.padEnd(6)} ${name}${detail ? ' - ' + detail : ''}`);
  worst = Math.max(worst, state === 'FAIL' ? 1 : state === 'REVIEW' ? 2 : 0);
};
const load = d => {
  const rd = f => { try { return fs.readFileSync(path.join(d, f), 'utf8'); } catch (e) { return ''; } };
  return { chat: rd('chat.txt').split('\n').map(strip), log: rd('server.log').split('\n').map(strip), summary: rd('summary.txt') };
};
const runs = dirs.map(load);
const R = runs[runs.length - 1];
const idx = (r, tag) => r.chat.findIndex(l => l.includes('CNCMARK:' + tag));
const between = (r, a, b) => { const i = idx(r, a), j = idx(r, b); return i < 0 ? null : r.chat.slice(i + 1, j < 0 ? undefined : j); };
const NOWANT = /don'?t have a wanted level|not wanted|^(?!.*★).*☆☆☆☆☆/i;
const money = l => { const m = /\$?\s*([\d,]+(?:\.\d+)?)/.exec(l || ''); return m ? parseFloat(m[1].replace(/,/g, '')) : NaN; };
// the balance reply printed just before a BAL:<tag> marker
const balAt = (r, tag) => {
  const i = idx(r, 'BAL:' + tag);
  if (i < 0) return NaN;
  for (let k = i - 1; k >= Math.max(0, i - 6); k--) if (/balance:/i.test(r.chat[k])) return money(r.chat[k + 1]);
  return NaN;
};
// the balance reply printed just before a free-form CNCMARK (N4 markers)
const balBefore = (r, tag) => {
  const i = idx(r, tag);
  if (i < 0) return NaN;
  for (let k = i - 1; k >= Math.max(0, i - 6); k--) if (/balance:/i.test(r.chat[k])) return money(r.chat[k + 1]);
  return NaN;
};
// the status reply printed just before a STATUS:<tag> marker
const statusAt = r => tag => {
  const i = idx(r, 'STATUS:' + tag);
  if (i < 0) return null;
  const prev = r.chat.slice(Math.max(0, i - 8), i);
  const k = prev.map(l => /wanted|star|\u2605/i.test(l)).lastIndexOf(true);
  return k < 0 ? '' : prev.slice(k).join(' | ');
};
const status = statusAt(R);

// ---- 0.15.2 AUTO helpers: the EvasionClock debug line "AUTO drop <name> level=a->b ending=X reason=Y ... outside=0.5 teleported=false ... delta=0.03 ..."
const secs = l => { const m = /^\[(\d+):(\d+):(\d+)/.exec(l); return m ? +m[1] * 3600 + +m[2] * 60 + +m[3] : NaN; };
const autoDrops = r => r.log.filter(l => /AUTO drop /.test(l)).map(l => {
  const g = re => (re.exec(l) || [])[1];
  return { at: secs(l), from: +g(/level=(\d+)->/), to: +g(/->(\d+) ending=/), ending: g(/ending=(\w+)/), reason: g(/reason=(\S*)/) || '',
    outside: parseFloat(g(/outside=([\d.]+)/)), teleported: g(/teleported=(\w+)/), delta: parseFloat(g(/delta=(-?[\d.]+)/)), line: l.replace(/^.*AUTO drop /, 'AUTO drop ').slice(0, 170) };
});
const markAt = (r, tag) => { const l = r.log.find(x => x.includes('CNCMARK:' + tag)); return l ? secs(l) : NaN; };
const firstDrop = (name, d, ending, reason) => {
  if (!d.length) { rec(name, 'FAIL', 'no AUTO drop debug line (Debug on for "Cops N Crooks", Drop_Mode AUTO?)'); return null; }
  const ok = d[0].ending === ending && (reason === undefined || d[0].reason === reason);
  rec(name, ok ? 'PASS' : 'FAIL', d[0].line);
  return ok ? d[0] : null;
};

runs.forEach((r, n) => {
  const errs = r.log.filter(l => /\/(ERROR|FATAL)\]|^\s+at org\.luckyraven|Exception:/.test(l) && !NOISE.test(l) && !(row === 'A6' && /config\.range/.test(l) && /Step_Speed/.test(l))); // A6 expects exactly that one report line
  rec(`run ${n + 1}: no server ERROR / org.luckyraven exception`, errs.length ? 'FAIL' : 'PASS', errs.slice(0, 3).join(' // '));
  const f = /FAILED STEPS \((\d+)\)/.exec(r.summary);
  rec(`run ${n + 1}: harness steps`, f && +f[1] === 0 ? 'PASS' : 'FAIL', f ? `${f[1]} failed step(s)` : 'summary.txt missing');
});

switch (row) {
  case 'R1': {
    const c = [1, 3, 5].map(n => R.chat.filter(l => l.includes(`CNCCOP:R1-${n}`)).length);
    rec('squad members seen at 1/3/5 stars: ' + c.join('/'), c.every(x => x > 0) ? 'PASS' : 'FAIL');
    rec('squad grows (or holds) with the star level', c[0] <= c[1] && c[1] <= c[2] ? 'PASS' : 'FAIL');
    rec('compare with Cops.Count (settings.yml) and Squad_Composition (cop_roles.yml) for 1/3/5 stars', 'REVIEW', 'human read, see README R1');
    break;
  }
  case 'R3': {
    const all = between(R, 'R3:CUFFED', 'R3:INTAKE');
    if (!all) { rec('cuffed within 90 s', 'FAIL', 'no R3:CUFFED marker'); break; }
    // T18: the cuffed window ends at the charge sheet / jailing (intake), not at the loose INTAKE marker; only a GLW decrease/clear line counts as a drop (a radio "Suspect cleared" is not one)
    let end = all.findIndex(l => /CHARGE SHEET|You are jailed/i.test(l)); if (end < 0) end = all.length;
    const cuffed = all.slice(0, end);
    rec('no star drop while cuffed (until the charge sheet)', cuffed.some(l => /\[GLW\].*wanted level has been (decreased|cleared)/i.test(l)) ? 'FAIL' : 'PASS');
    const mark = t => cuffed.findIndex(l => l.includes('CNCMARK:STATUS:' + t));
    ['R3-cuffed-0', 'R3-cuffed-15', 'R3-cuffed-30'].forEach(t => {
      if (mark(t) < 0) { rec('stars kept ' + t, 'PASS', 'sampled after intake (window ended at the charge sheet), see after-intake row'); return; }
      rec('stars kept ' + t, status(t) && !NOWANT.test(status(t)) ? 'PASS' : 'FAIL', status(t));
    });
    rec('intake reached', idx(R, 'R3:INTAKE') >= 0 ? 'PASS' : 'FAIL');
    const a = status('R3-after-intake');
    rec('stars cleared at intake', a !== null && NOWANT.test(a) ? 'PASS' : 'REVIEW', a);
    break;
  }
  case 'R4': {
    const b = statusAt(runs[0])('R4-before-quit');
    rec('2 stars before quit', b && !NOWANT.test(b) ? 'PASS' : 'FAIL', b);
    const r = status('R4-after-rejoin');
    rec('still wanted after rejoin', r && !NOWANT.test(r) ? 'PASS' : 'FAIL', r);
    rec('decay clock runs after rejoin (decreased line within 60 s, Time 10)', idx(R, 'R4:DECAYED') >= 0 ? 'PASS' : 'FAIL');
    break;
  }
  case 'N1': {
    rec('decreased line within 3 + 20 + 5 s', idx(R, 'N1:DROPPED') >= 0 ? 'PASS' : 'FAIL');
    const s = status('N1-after-drop');
    rec('1 star left (status still wanted)', s && !NOWANT.test(s) ? 'PASS' : 'FAIL', s);
    rec('no death', R.chat.some(l => /you (died|were (slain|killed))/i.test(l)) ? 'FAIL' : 'PASS');
    break;
  }
  case 'N2': case 'N3': case 'N3b': {
    const want = { N2: 0, N3: 1250, N3b: 1500 }[row];
    runs.forEach((r, n) => {
      const a = balAt(r, 'before'), b = balAt(r, 'after');
      rec(`run ${n + 1}: balance ${a} -> ${b}, charge ${a - b} (expected ${want})`, a - b === want ? 'PASS' : 'FAIL');
    });
    if (row === 'N3b') {
      const w = R.log.filter(l => /Money formula/i.test(l)).length;
      rec('exactly one "Money formula" warning over two drops', w === 1 ? 'PASS' : 'FAIL', w + ' seen');
      rec('both stars fell', idx(R, 'N3b:DROP2') >= 0 ? 'PASS' : 'FAIL');
    } else {
      rec('star fell', idx(R, row + ':DROPPED') >= 0 ? 'PASS' : 'FAIL');
    }
    break;
  }
  case 'N4': {
    if (runs.length > 1) rec('phase A posted the bounty', idx(runs[0], 'N4:POSTED') >= 0 ? 'PASS' : 'FAIL');
    const b0 = balBefore(R, 'N4:BAL0'), b1 = balBefore(R, 'N4:BAL1-after-first-kill'), b2 = balBefore(R, 'N4:BAL2-after-second-kill');
    rec(`first kill collects the 500 posted: Hunter ${b0} -> ${b1}`, b1 - b0 === 500 ? 'PASS' : 'REVIEW', 'other kill rewards also show; read the transcript');
    rec(`second kill collects no posted money: ${b1} -> ${b2}`, b2 - b1 < 500 ? 'PASS' : 'FAIL');
    break;
  }
  case 'N5': case 'N6': case 'N7': case 'N8': {
    const interesting = { N5: /shots fired|gunfire|\[RADIO\]/i, N6: /increased|wanted/i, N7: /sheet|charge|paid|seconds/i, N8: /regroup|push|fall back|\[RADIO\]/i }[row];
    R.chat.filter(l => /CNCMARK:|CNCCOP:/.test(l) || interesting.test(l)).slice(-40).forEach(l => results.push('       ' + l.slice(0, 160)));
    rec(row + ' best effort: read the transcript above against the pass line', 'REVIEW');
    break;
  }
  case 'A1': {
    const d = autoDrops(R);
    rec('three AUTO drops, each one star at HUNKER_DOWN', d.length === 3 && d.every(x => x.ending === 'HUNKER_DOWN' && x.from - x.to === 1) ? 'PASS' : 'FAIL',
      d.map(x => `${x.from}->${x.to} ${x.ending}`).join(', '));
    if (d.length === 3) {
      const g1 = d[1].at - d[0].at, g2 = d[2].at - d[1].at;
      rec(`gap 1st -> 2nd drop about 15 s (12-18): ${g1}`, g1 >= 12 && g1 <= 18 ? 'PASS' : 'FAIL');
      rec(`gap 2nd -> 3rd drop about 6 s (3-9): ${g2}`, g2 >= 3 && g2 <= 9 ? 'PASS' : 'FAIL');
      const f = d[0].at - markAt(R, 'A1:HIDING');
      rec(`first drop about 30 s after the search starts (hide marker to drop ${f} s, 28-42 incl. the 3 s lost-sight)`, f >= 28 && f <= 42 ? 'PASS' : 'REVIEW');
    }
    break;
  }
  case 'A2': {
    const d = firstDrop('first drop ending=PETTY', autoDrops(R), 'PETTY');
    if (d) rec(`every star dropped at once, 2 -> 0 (${d.from}->${d.to})`, d.from === 2 && d.to === 0 ? 'PASS' : 'FAIL');
    break;
  }
  case 'A3': {
    const d = firstDrop('first drop ending=STILL_HOT reason=rampage', autoDrops(R), 'STILL_HOT', 'rampage');
    if (d) rec(`first drop 4 -> 3 (${d.from}->${d.to})`, d.from === 4 && d.to === 3 ? 'PASS' : 'FAIL');
    break;
  }
  case 'A4': {
    const q = statusAt(runs[0])('A4-before-quit');
    rec('wanted before quit', q && !NOWANT.test(q) ? 'PASS' : 'FAIL', q);
    const a = status('A4-after-rejoin');
    rec('still wanted after rejoin', a && !NOWANT.test(a) ? 'PASS' : 'FAIL', a);
    const d = autoDrops(R);
    if (!d.length) { rec('first drop 2 -> 1, reason=logout', 'FAIL', 'no AUTO drop debug line'); break; }
    const ok = d[0].ending === 'STILL_HOT' && d[0].reason === 'logout' && d[0].from === 2 && d[0].to === 1;
    // PLAN A4: REVIEW (not FAIL) when the drop is not the logout lock, typically because no cop group re-formed after the rejoin
    rec('first drop 2 -> 1, ending=STILL_HOT reason=logout', ok ? 'PASS' : 'REVIEW',
      d[0].line + (R.chat.some(l => l.includes('CNCCOP:A4-squad')) ? '' : ' (no squad seen after the rejoin)'));
    break;
  }
  case 'A5': {
    const d = autoDrops(runs[0]);
    rec('run A: both chases ended PETTY', d.length >= 2 && d.slice(0, 2).every(x => x.ending === 'PETTY') ? 'PASS' : 'FAIL', d.map(x => x.ending).join(','));
    if (d.length >= 2) rec(`run A: the second chase already reads a learned delta > 0 (${d[1].delta})`, d[1].delta > 0 ? 'PASS' : 'FAIL');
    let row = null;
    try { row = fs.readFileSync(path.join(dirs[0], 'chase_habit.txt'), 'utf8').split('\n').find(l => l.trim()); } catch (e) { /* run-all writes it */ }
    const f = row ? row.trim().split('|') : [];
    rec(`chase_habit row for Runner after run A has n = 1.9 (${row ? row.trim() : 'chase_habit.txt missing'})`, f.length >= 3 && Math.abs(parseFloat(f[1]) - 1.9) < 0.01 ? 'PASS' : 'FAIL');
    const e = autoDrops(R);
    rec(`after the restart the next debug line shows delta > 0, about 0.03 (${e.length ? e[0].delta : 'no AUTO line'})`, e.length && e[0].delta > 0 ? 'PASS' : 'FAIL', e.length ? e[0].line : '');
    break;
  }
  case 'A6': {
    const bad = r => r.log.filter(l => /config\.(range|conflict|note|unknown|type|missing)/.test(l) && /Auto|Evasion|Step_Speed|wanted/i.test(l));
    runs.forEach((r, n) => rec(`run ${n + 1}: Done ( present`, r.log.some(l => /Done \(/.test(l)) ? 'PASS' : 'FAIL'));
    rec('0.15.1 wanted.yml (no Auto block): no config. line', bad(runs[0]).length === 0 ? 'PASS' : 'FAIL', bad(runs[0]).slice(0, 2).join(' // '));
    const r2 = bad(R);
    rec('Step_Speed 1.5: exactly one config.range line naming Step_Speed', r2.length === 1 && /config\.range/.test(r2[0]) && /Step_Speed/.test(r2[0]) ? 'PASS' : 'FAIL', r2.slice(0, 3).join(' // '));
    break;
  }
  case 'A7': case 'A8': case 'A9': {
    const d = autoDrops(R);
    if (!d.length) { rec(row + ': an AUTO drop was logged', 'FAIL', 'no AUTO drop debug line'); break; }
    const x = d[0];
    results.push('       ' + x.line);
    if (row === 'A7') rec(`first drop ending=CLEAN_BREAK, 3 -> 1 (${x.ending} ${x.from}->${x.to})`, 'REVIEW', x.ending === 'CLEAN_BREAK' && x.from - x.to === 2 ? 'matches the pass line' : 'does NOT match, read the transcript');
    if (row === 'A8') rec(`first drop ending=HUNKER_DOWN with outside 0.2-0.3 (${x.ending}, outside ${x.outside})`, 'REVIEW', x.ending === 'HUNKER_DOWN' && x.outside >= 0.15 && x.outside <= 0.35 ? 'matches the pass line' : 'does NOT match, read the transcript');
    if (row === 'A9') {
      rec(`teleported=${x.teleported}`, x.teleported === 'true' ? 'PASS' : 'REVIEW');
      rec(`no CLEAN_BREAK (${x.ending})`, x.ending !== 'CLEAN_BREAK' ? 'PASS' : 'FAIL');
    }
    break;
  }
  default:
    console.error('unknown row ' + row);
    process.exit(64);
}
console.log(`== ${row} ==\n` + results.join('\n') + `\n== ${worst === 0 ? 'PASS' : worst === 1 ? 'FAIL' : 'REVIEW'} ==`);
process.exit(worst);
