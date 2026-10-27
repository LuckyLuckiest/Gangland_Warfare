#!/usr/bin/env node
// gen-route.js -> scen/radio-far-route-r2.json : radio-far verbatim through GO-FAR (+FAR-1/2), then a roofed 30-block tunnel
// into the box (1x2 doorway in the north wall z=196), Runner in the SE interior corner (out of the doorway sightline),
// 7 probes (~33 s) at 2 stars, no WANTED3 block. Judge request 2026-10-26 (radio-far cause 1 route test).
// Set PLATFORM=1 for the fallback: 6-high solid stone platform reachable only by ladder (forces planner / Route_High).
'use strict';
const fs = require('fs'), path = require('path');
const PLATFORM = process.env.PLATFORM === '1';
const base = JSON.parse(fs.readFileSync(path.join(__dirname, 'scen/radio-far.json'), 'utf8'));
const idx = l => base.findIndex(s => s.console === 'say CNCMARK:RADIO:' + l);
const fillAt = base.findIndex(s => s.console && s.console.startsWith('fill -4 -61 196'));
// steps up to and including the box fill's expectLog
const s = base.slice(0, fillAt + 2);
const c = (console, after = 300) => s.push({ console, after });
const filled = () => s.push({ expectLog: 'Successfully filled', timeout: 10000 });
c('fill 0 -60 196 0 -59 196 minecraft:air', 600); filled();
c('fill -1 -61 166 1 -57 195 minecraft:stone hollow', 800); filled();
c('fill 0 -60 166 0 -59 196 minecraft:air', 600); filled();
if (PLATFORM) {
  c('fill -4 -61 196 4 -55 204 minecraft:stone', 800); filled();
  c('setblock 0 -60 195 minecraft:ladder[facing=north]', 300);
  for (let y = -59; y <= -55; y++) c(`setblock 0 ${y} 195 minecraft:ladder[facing=north]`, 200);
}
// tail of setup from radio-far: tp to base ... through GO-FAR / FAR-2 probes
const from = base.findIndex((x, i) => i > fillAt + 1 && x.console === 'tp Runner 0 -60 0 0 0');
const goRoom = idx('GO-ROOM');
for (const st of base.slice(from, goRoom)) s.push(st);
const NPC = base[idx('FAR-1') + 2].console, probeAfter = lab => [
  { console: 'say CNCMARK:RADIO:' + lab, after: 200 }, { console: 'glw cop list Runner', after: 300 },
  { console: NPC, after: 400 }, { console: 'data get entity Runner Pos', after: 200 }];
s.push({ console: 'say CNCMARK:RADIO:GO-ROOM', after: 200 });
s.push({ console: PLATFORM ? 'tp Runner 0 -54 200' : 'tp Runner 3 -60 203 0 0', after: 500 });
for (let i = 1; i <= 7; i++) {
  s.push(...probeAfter('ROOM-' + i));
  if (i === 3 || i === 7) s.push({ chat: '/glw wanted', after: 500, bot: 'Runner' });
  s.push({ wait: 3800 });
}
s.push({ console: 'say CNCMARK:RADIO:END', after: 200 }, { quit: true });
const out = PLATFORM ? 'radio-far-route-platform-r2.json' : 'radio-far-route-r2.json';
fs.writeFileSync(path.join(__dirname, 'scen', out), JSON.stringify(s, null, 1)); console.log(out, s.length);
