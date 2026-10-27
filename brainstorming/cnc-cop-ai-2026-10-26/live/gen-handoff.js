#!/usr/bin/env node
// gen-handoff.js -> scen/handoff-lead.json : R5 hand-off lead discriminator (r4 judges' request, 2026-10-27).
// A recorder like gen-scen.js / gen-acc.js: hard expects guard setup only (box filled, first DISPATCH / UNIT); the one
// behaviour expect is the last step (HANDOFF_LEAD logged, since the row start), so rc=1 with only that step failed
// means "the hand-off never seeded a lead" (judge FAIL), not a broken setup. judge-acc.py handoff gives the verdicts.
//
// Level 2, no station (ring spawns), shipped Cops.Handoff (Heading_Seconds 2, Bias_Seconds 10, Lead_Blocks 24).
//   CONTACT  (0,0) facing west, ~6 s of real sightings
//   HOPS     2-block hops west every 0.5 s to x=-20 (yaw 90 on every tp, so every UNIT has a known pose for R6): the
//            squad's last-known samples inside Heading_Seconds read "west"
//   BOX      tp into a sealed 9x9x6 stone box at (0,200), yaw 180, OFF the heading line (due south of the last-known
//            spot, ~200 b away, every cop > Pursuit.Max_Distance 80): leash -> HANDOFF -> biased refill -> HANDOFF_LEAD
//            at about (-44,-60,0). 28 probes ~1 s apart (every NPC position + his Pos), he never moves
//   OUT      after the lead window: out at (0,185) facing west, where the hand-off units gathered (open ground, in
//            his view), ~2 s; CLEAR there, so they turn RETURNING while he watches them (R3-in-view); WATCH 10 probes,
//            then AWAY: same spot facing south (out of view, they may go). search-after-clear window = CLEAR..END
//            (in hl-env2 a clear inside the box removed every unit within 3 s, unseen, so R3-in-view had nothing)
'use strict';
const fs = require('fs'), path = require('path');
const OUT = path.join(__dirname, 'scen');
const T = 'HL';
const C = (console, after = 300) => ({ console, after });
const mark = l => C(`say CNCMARK:${T}:${l}`, 200);
const NPC_POS = 'execute as @e if entity @s[type=minecraft:player,name=!Runner] run data get entity @s Pos';
const probe = (l, full = false) => [mark(l), ...(full ? [C('glw cop list Runner', 300)] : []), C(NPC_POS, 400),
  C('data get entity Runner Pos', 200), ...(full ? [C('data get entity Runner Rotation', 150)] : [])];
const tp = (x, z, yaw, after = 300) => C(`tp Runner ${x} -60 ${z} ${yaw} 0`, after);
const wanted = { chat: '/glw wanted', after: 500, bot: 'Runner' };
const WEST = 90, NORTH = 180, SOUTH = 0;

const s = [C('gamerule spawn_mobs false'), C('gamerule spawn_monsters false'), C('gamerule advance_time false'),
  C('time set 6000'), C('weather clear'), { join: 'Runner' },
  C('effect give Runner minecraft:resistance 1200 4 true'), C('effect give Runner minecraft:saturation 1200 4 true'),
  C('gamemode survival Runner', 200), C('clear Runner'),
  tp(0, 185, NORTH, 800), C('fill -4 -61 196 4 -56 204 minecraft:stone hollow', 800),
  { expectLog: 'Successfully filled', timeout: 10000 },
  tp(0, 0, WEST, 1500), { wait: 3000 }, ...probe('BASE', true),
  mark('WANTED2'), { chat: '/glw wanted add 2', after: 800, bot: 'Runner' },
  { expectLog: 'DISPATCH Runner', timeout: 20000 }, { expectLog: 'UNIT Runner', timeout: 20000 },
  ...probe('CONTACT-a', true), { wait: 2000 }, ...probe('CONTACT-b', true),
  mark('HOPS-WEST')];
for (let x = -2; x >= -20; x -= 2) s.push(tp(x, 0, WEST, 500));
s.push(...probe('HOPS-END', true), mark('GO-BOX'), tp(0, 200, NORTH, 300));
for (let i = 1; i <= 28; i++) s.push(...probe('BOX-' + i, i % 5 === 1));
s.push(wanted, mark('GO-OUT'), tp(0, 185, WEST, 300), ...probe('OUT-1', true), ...probe('OUT-2'),
  mark('CLEAR'), { chat: '/glw wanted clear', after: 800, bot: 'Runner' });
for (let i = 1; i <= 10; i++) s.push(...probe('WATCH-' + i, i % 5 === 1), { wait: 300 });
s.push(mark('GO-AWAY'), tp(0, 185, SOUTH, 300));
for (let i = 1; i <= 8; i++) s.push(...probe('AWAY-' + i, i % 4 === 1), { wait: 300 });
s.push(wanted, mark('END'), { expectLog: 'HANDOFF_LEAD Runner', since: 'start', timeout: 1000 }, { quit: true });
fs.writeFileSync(path.join(OUT, 'handoff-lead.json'), JSON.stringify(s, null, 1));
console.log('handoff-lead', s.length, 'steps');
