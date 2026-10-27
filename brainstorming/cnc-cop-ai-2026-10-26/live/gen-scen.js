#!/usr/bin/env node
// gen-scen.js - writes live/scen/{boot,dry,spawn-dup,radio-far,brandish}.json (harness step format, run-copai.js).
// The bug scenarios are RECORDERS: hard expects only guard setup preconditions (wand, station saved, first DISPATCH,
// fill done, first warning). Every bug signal is left to the timeline: say CNCMARK:<tag>:<label> markers (land in
// server.log with a timestamp and in chat.txt), console probes and the bot's /glw wanted.
'use strict';
const fs = require('fs');
const path = require('path');
const OUT = path.join(__dirname, 'scen');
fs.mkdirSync(OUT, { recursive: true });
const write = (n, s) => { fs.writeFileSync(path.join(OUT, n + '.json'), JSON.stringify(s, null, 1)); console.log(n, s.length, 'steps'); };

const C = (console, after = 300) => ({ console, after });
const mark = (tag, label) => C(`say CNCMARK:${tag}:${label}`, 200);
// Every Citizens PLAYER NPC (cops, beat cops) with its position; `if entity @s[type=player]` is what matches a
// Citizens NPC (harness README: @e[type=player] walks only real players). data get does not touch the path.
const NPC_POS = 'execute as @e if entity @s[type=minecraft:player,name=!Runner,name=!Drawer] run data get entity @s Pos';
// A probe: marker, the hunted player's squad (CopGroup members incl. stranded RETURNING walkers), every NPC position,
// the bot's own position.
const probe = (tag, label, full = false, who = 'Runner') => [
  mark(tag, label),
  C('glw cop list ' + who, 300),
  C(NPC_POS, 400),
  C('data get entity ' + who + ' Pos', 200),
  // full=true kept as a label only: Citizens' console npc list prints just its header (dry run), so NPC_POS is the world total
];
const world = [
  C('gamerule spawn_mobs false'), C('gamerule spawn_monsters false'), C('gamerule advance_time false'),
  C('time set 6000'), C('weather clear'),
];
const tough = who => [C(`effect give ${who} minecraft:resistance 1200 4 true`), C(`effect give ${who} minecraft:saturation 1200 4 true`)];
// station via the setup wand (PAT-WARN pattern); bot must be in creative holding the wand
const station = (name, x, z) => [
  C(`tp Runner ${x} -60 ${z - 2}`, 400),
  { chat: '/glw cop setup mode station', after: 600, bot: 'Runner' },
  { leftClick: [x, -61, z], after: 400, bot: 'Runner' },
  { expectChat: 'pos1 set', timeout: 3000, bot: 'Runner' },
  { chat: '/glw cop setup save ' + name, after: 800, bot: 'Runner' },
  { expectChat: 'Station .* saved|already exists', timeout: 4000, bot: 'Runner' },
];
const wand = [
  C('gamemode creative Runner', 200), C('clear Runner'),
  { chat: '/glw cop setup wand', after: 800, bot: 'Runner' },
  { expectChat: 'wand given', timeout: 4000, bot: 'Runner' },
  { hold: 0, bot: 'Runner' },
];
const wanted = who => ({ chat: '/glw wanted', after: 500, bot: who });

// ---- boot (no bot) and dry (one bot, every probe command once) ---------------------------------------------------
write('boot', [
  { wait: 10000 }, C('glw module list', 2000), C('version Keystone', 1500), C('version Gangland_Warfare', 1500),
  C('version Bartizan', 1500), C('version Citizens', 1500), C('plugins', 1500), { wait: 2000 },
]);
write('dry', [
  ...world, { join: 'Runner' }, ...tough('Runner'),
  ...wand, ...station('Dry Station', 0, 10), C('gamemode survival Runner', 200), C('clear Runner'),
  C('tp Runner 0 -60 14'), { wait: 6000 },
  ...probe('DRY', 'P0', true),
  C('execute at @a[name=Runner,limit=1] as @e[type=!minecraft:item,type=!minecraft:text_display,type=!minecraft:armor_stand,type=!minecraft:interaction,type=!minecraft:item_display,type=!minecraft:villager,type=!minecraft:experience_orb,name=!Runner,name=!Drawer,distance=..60,sort=nearest,limit=1] at @s run tp @a[name=Runner,limit=1] ~1.5 ~ ~1.5', 500),
  C('data get entity Runner Pos', 300),
  wanted('Runner'), { expectChat: 'wanted|stars?|level', timeout: 4000, bot: 'Runner' },
  mark('DRY', 'END'), { quit: true },
]);

// ---- spawn-dup -----------------------------------------------------------------------------------------------------
// Level 3 (target 4 = Count.Base 2 + 2 x Per_Level 1; Max_Per_Player 8): two leash cycles already exceed the cap, at
// the cost of Perimeter (Min_Level 3) radio lines. Station "Central Station" at (0,70) with one spawner at (0,74):
// every refill is a station batch, so Dispatch_En_Route is radioed (cause R); the bot is never within the patrol's
// Activation_Radius 64 of it (min distance 70). Corridor x -90..90 on z=0, max 114 blocks from the station (ETA <= 12 s),
// and <= 180 blocks long: a walker stranded at one end is outside simulation-distance 10 (160) when the bot is at the
// other (frozen but still a live entity) and never unloads (view-distance 16 = 256), so chunk unloads cannot mask
// cause 1. Bot hops 30 blocks every ~3 s (10 b/s vs a Speed-1.0 cop's ~4.3), holds ~6 s at each end, 4 round trips.
{
  const T = 'SPAWN';
  const s = [...world, { join: 'Runner' }, ...tough('Runner'), ...wand, ...station('Central Station', 0, 70),
    C('tp Runner 0 -60 74', 500), { chat: '/glw cop spawner set', after: 800, bot: 'Runner' },
    { expectChat: 'Cop spawner set', timeout: 4000, bot: 'Runner' },
    C('gamemode survival Runner', 200), C('clear Runner'),
    C('tp Runner -90 -60 0', 800), { wait: 3000 },
    ...probe(T, 'BASE', true),
    mark(T, 'WANTED3'), { chat: '/glw wanted add 3', after: 800, bot: 'Runner' },
    { expectLog: 'DISPATCH Runner', timeout: 20000 },
    { expectLog: 'UNIT Runner', timeout: 45000 },
    wanted('Runner'), { wait: 4000 }, ...probe(T, 'CONTACT'),
  ];
  const X = [-90, -60, -30, 0, 30, 60, 90];
  let n = 0;
  for (let trip = 1; trip <= 4; trip++) {
    for (const leg of ['F', 'B']) {
      const xs = leg === 'F' ? X.slice(1) : X.slice(0, -1).reverse();
      for (const x of xs) {
        s.push(C(`tp Runner ${x} -60 0`, 300), ...probe(T, `T${trip}${leg}-x${x}-h${++n}`), { wait: 1300 });
      }
      s.push(...probe(T, `T${trip}${leg}-HOLD-a`, true), wanted('Runner'), { wait: 2500 }, ...probe(T, `T${trip}${leg}-HOLD-b`), { wait: 1500 });
    }
  }
  // short aftermath at x=-90 (envcheck: standing still ~45 s gets him cuffed, which pauses the spawn task)
  for (let i = 1; i <= 4; i++) s.push(...probe(T, 'AFTER-' + i), { wait: 3800 });
  s.push(wanted('Runner'));
  s.push(mark(T, 'END'), { quit: true });
  write('spawn-dup', s);
}

// ---- radio-far -----------------------------------------------------------------------------------------------------
// Level 2 (Pointman, Assault, Assault; no station: ring spawns). Sealed 9x9x6 stone box pre-built at (0,200) during
// setup (GET-1's fill-hollow pattern, far from any cop). Phases: CONTACT at (0,0) ~6 s (real sightings), OPEN-FAR at
// (0,110) 8 s (every cop >80 blocks away: leash, Handoff, biased refill), ROOM at (0,200) 60 s (no line of sight is
// possible), then CLEAR + exactly 3 stars in the room 75 s (Commander/Marksman squad, so Commander_Orders and
// Overwatch_Set, cause 3, can occur). Probes every ~5 s give every cop position for the judge's distance/LOS check.
{
  const T = 'RADIO';
  const s = [...world, { join: 'Runner' }, ...tough('Runner'), C('gamemode survival Runner', 200), C('clear Runner'),
    C('tp Runner 0 -60 185', 800), C('fill -4 -61 196 4 -56 204 minecraft:stone hollow', 800),
    { expectLog: 'Successfully filled', timeout: 10000 },
    C('tp Runner 0 -60 0 0 0', 1500), { wait: 3000 },
    ...probe(T, 'BASE', true),
    mark(T, 'WANTED2'), { chat: '/glw wanted add 2', after: 800, bot: 'Runner' },
    { expectLog: 'DISPATCH Runner', timeout: 20000 },
    { expectLog: 'UNIT Runner', timeout: 20000 },
    ...probe(T, 'CONTACT-a'), { wait: 2000 }, ...probe(T, 'CONTACT-b'),
    mark(T, 'GO-FAR'), C('tp Runner 0 -60 110 0 0', 500)];
  // 8 s only: the envcheck run showed a Handoff-biased unit spawning 10-50 blocks ahead cuffs him in the open ~10 s in
  for (let i = 1; i <= 2; i++) s.push(...probe(T, 'FAR-' + i), { wait: 2800 });
  s.push(mark(T, 'GO-ROOM'), C('tp Runner 0 -60 200 0 0', 500));
  for (let i = 1; i <= 12; i++) s.push(...probe(T, 'ROOM-' + i, i % 6 === 0), ...(i % 6 === 0 ? [wanted('Runner')] : []), { wait: 3800 });
  // the 2-star chase may have decayed in the room (evasion), so restart cleanly at exactly 3 stars while sealed in
  s.push(mark(T, 'CLEAR'), { chat: '/glw wanted clear', after: 3000, bot: 'Runner' }, ...probe(T, 'CLEARED'),
    mark(T, 'WANTED3'), { chat: '/glw wanted add 3', after: 800, bot: 'Runner' });
  for (let i = 1; i <= 15; i++) s.push(...probe(T, 'ROOM3-' + i, i % 5 === 0), ...(i % 5 === 0 ? [wanted('Runner')] : []), { wait: 3800 });
  s.push(mark(T, 'END'), { quit: true });
  write('radio-far', s);
}

// ---- brandish ------------------------------------------------------------------------------------------------------
// PAT-WARN setup verbatim (Northside Station at (100,40) + district Docks). Variant B first with a second bot Drawer
// (heat and the warning cooldown are per player): warn, put away (hold 1) inside 5 s, 35 s with the weapon away,
// re-draw (hold 0), watch for a second warning. Then the main row A with Runner (0 stars, daylight, Bartizan pistol):
// one pull next to the nearest beat cop for the first warning, 30 s with NO pull (the natural walk-off, cause a), then
// a pull next to the nearest beat cop every 10 s up to ~130 s; /glw wanted every ~20 s; ignored warnings show as
// "WARNING Runner result=ignored" + SUSPECT debug lines; a star shows as DISPATCH Runner + "[DISPATCH] ... level 1".
{
  const T = 'BRANDISH';
  const pull = who => C(`execute at @a[name=${who},limit=1] as @e[type=!minecraft:item,type=!minecraft:text_display,type=!minecraft:armor_stand,type=!minecraft:interaction,type=!minecraft:item_display,type=!minecraft:villager,type=!minecraft:experience_orb,name=!Runner,name=!Drawer,distance=..60,sort=nearest,limit=1] at @s run tp @a[name=${who},limit=1] ~1.5 ~ ~1.5`, 500);
  const s = [...world, { join: 'Runner' }, ...tough('Runner'), ...wand, ...station('Northside Station', 100, 40),
    { console: 'setblock 130 -59 150 minecraft:stone', after: 300 },
    C('tp Runner 80 -60 108', 400),
    { chat: '/glw cop setup mode district', after: 600, bot: 'Runner' },
    { leftClick: [80, -61, 110], after: 400, bot: 'Runner' },
    { expectChat: 'pos1 set', timeout: 3000, bot: 'Runner' },
    C('tp Runner 130 -60 148', 400),
    { activateBlock: [130, -59, 150], after: 400, bot: 'Runner' },
    { expectChat: 'pos2 set', timeout: 3000, bot: 'Runner' },
    { chat: '/glw cop setup save Docks', after: 800, bot: 'Runner' },
    { expectChat: 'Region .* saved', timeout: 4000, bot: 'Runner' },
    C('gamemode survival Runner', 200), C('clear Runner'),
    C('tp Runner 100 -60 170', 400),   // parked 130 blocks from the station while Drawer runs variant B
    // ---- variant B: put away, re-draw after 35 s ----
    { join: 'Drawer' }, ...tough('Drawer'), C('gamemode survival Drawer', 200), C('clear Drawer'),
    C('tp Drawer 100 -60 50', 400), { wait: 6000 },
    ...probe(T, 'B-BASE', true, 'Drawer'),
    mark(T, 'B-DRAW1'), pull('Drawer'),
    { hold: 1, bot: 'Drawer' }, C('bartizan weapon give Drawer pistol', 1000), { hold: 0, bot: 'Drawer' },
    { expectChat: 'Put that away', timeout: 15000, bot: 'Drawer' },
    { wait: 1500 }, mark(T, 'B-AWAY'), { hold: 1, bot: 'Drawer' },
    { expectChat: 'Thanks, carry on', timeout: 4000, bot: 'Drawer' },
  ];
  for (let i = 1; i <= 7; i++) s.push(...probe(T, 'B-AWAY-' + i, false, 'Drawer'), ...(i % 2 ? [pull('Drawer')] : []), { wait: 3500 });
  s.push(mark(T, 'B-REDRAW'), pull('Drawer'), { hold: 0, bot: 'Drawer' });
  for (let i = 1; i <= 4; i++) s.push(...probe(T, 'B-REDRAW-' + i, false, 'Drawer'), { wait: 3500 });
  s.push(wanted('Drawer'), { hold: 1, bot: 'Drawer' }, mark(T, 'B-END'), { quit: true, bot: 'Drawer' }, { wait: 2000 });
  // ---- main row A: keep holding ~130 s ----
  s.push(C('tp Runner 100 -60 50', 400), { wait: 4000 }, ...probe(T, 'A-BASE', true),
    wanted('Runner'), mark(T, 'A-DRAW'), pull('Runner'),
    // pull first, then draw: the give lands in slot 0 while slot 1 is held, so the warning starts at the hold-0 step
    // (after the last action step's chat mark; giving straight into the hand raced the expect in envcheck2)
    { hold: 1, bot: 'Runner' }, C('bartizan weapon give Runner pistol', 1000), { hold: 0, bot: 'Runner' },
    { expectChat: 'Put that away', timeout: 15000, bot: 'Runner' });
  // 0-30 s: no pull (natural walk-off after the verdict)
  for (let i = 1; i <= 6; i++) s.push(...probe(T, 'A-NOPULL-' + i), { wait: 3800 });
  // 30-130 s: pull next to the nearest beat cop every ~10 s
  for (let i = 1; i <= 20; i++) {
    s.push(...probe(T, 'A-HOLD-' + i, i % 10 === 0), ...(i % 2 ? [pull('Runner')] : []), ...(i % 4 === 0 ? [wanted('Runner')] : []), { wait: 3500 });
  }
  s.push(wanted('Runner'), mark(T, 'END'), { quit: true, bot: 'Runner' });
  write('brandish', s);
}

// ---- spawn-dup-b (judge request 2026-10-26): same server prep as spawn-dup, three stand-alone phases -----------------
// PHASE 2 (cause 2: 3D gap, station-before-bias): spawner sealed in a hut on a pillar at (0,-46,74), bot at (-28,-60,0)
//   (horizontal 79.12 <= 80, 3D 80.35 > 80 leash, dy 14 <= 16). Precondition: UNIT line hidden=true at=0,-46,74.
// PHASE 5 (cause 5): level 3 corridor hop, wanted clear while >= 2 RETURNING walkers are > 80 blocks out, then add 1.
// PHASE P (post-escape refill): sealed box at 1 star until the star drops, box removed, hop 90 <-> -90 for 90 s.
// Every expect here is a precondition (a failed expect does not stop the run, the judge reads it as "not exercised").
{
  const T = 'SPAWNB';
  const hop = x => C(`tp Runner ${x} -60 0`, 300);
  const s = [...world, { join: 'Runner' }, ...tough('Runner'), ...wand, ...station('Central Station', 0, 70),
    // hut: pillar + hollow box at x -3..3, y -47..-43, z 71..77 (interior y -46..-44); spawner set from inside
    C('tp Runner 8 -60 68', 600),
    C('fill 0 -60 74 0 -48 74 minecraft:stone', 600), { expectLog: 'Successfully filled', timeout: 10000 },
    C('fill -3 -47 71 3 -43 77 minecraft:stone hollow', 800), { expectLog: 'Successfully filled', timeout: 10000 },
    C('tp Runner 0 -46 74', 800), { chat: '/glw cop spawner set', after: 800, bot: 'Runner' },
    { expectChat: 'Cop spawner set', timeout: 4000, bot: 'Runner' },
    C('gamemode survival Runner', 200), C('clear Runner'),
    C('tp Runner -28 -60 0', 800), { wait: 3000 },
    ...probe(T, 'P2-BASE', true),
    mark(T, 'P2-WANTED3'), { chat: '/glw wanted add 3', after: 800, bot: 'Runner' },
    { expectLog: 'DISPATCH Runner', timeout: 20000 },
    { expectLog: 'UNIT Runner.*hidden=true.*at=0,-46,74', timeout: 30000 },
    wanted('Runner')];
  for (let i = 1; i <= 17; i++) s.push(...probe(T, 'P2-A-' + i), { wait: 900 });        // ~2 s each, ~40 s
  s.push(wanted('Runner'), mark(T, 'P2-HOP-EAST'), hop(2));
  for (let i = 1; i <= 3; i++) s.push(...probe(T, 'P2-E-' + i), { wait: 900 });
  s.push(mark(T, 'P2-HOP-BACK'), hop(-28));
  for (let i = 1; i <= 8; i++) s.push(...probe(T, 'P2-B-' + i), { wait: 900 });
  s.push(mark(T, 'P2-CLEANUP'), C('fill -3 -60 71 3 -43 77 minecraft:air', 800), { expectLog: 'Successfully filled', timeout: 10000 },
    { chat: '/glw wanted clear', after: 3000, bot: 'Runner' }, ...probe(T, 'P2-CLEARED'),
    // the hut spawner is now in mid-air: swap it for a ground spawner at (0,-60,74) as in spawn-dup (ids unknown, unknown ids are harmless)
    { chat: '/glw cop spawner list', after: 800, bot: 'Runner' },
    ...[0, 1, 2, 3].map(i => ({ chat: '/glw cop spawner remove ' + i, after: 500, bot: 'Runner' })),
    { chat: '/glw cop spawner list', after: 800, bot: 'Runner' },
    C('tp Runner 0 -60 74', 600), { chat: '/glw cop spawner set', after: 800, bot: 'Runner' },
    { expectChat: 'Cop spawner set', timeout: 4000, bot: 'Runner' },
    { chat: '/glw cop spawner list', after: 800, bot: 'Runner' },
    C('tp Runner -90 -60 0', 600), { wait: 12000 }, ...probe(T, 'P2-SETTLED'));

  // PHASE 5: level 3, one forward corridor leg with the spawn-dup hop cadence, hold, clear, re-add at 1 star
  s.push(mark(T, 'P5-WANTED3'), { chat: '/glw wanted add 3', after: 800, bot: 'Runner' },
    { expectLog: 'DISPATCH Runner', timeout: 20000 }, { expectLog: 'UNIT Runner', timeout: 45000 });
  s.push(wanted('Runner'), { wait: 4000 }, ...probe(T, 'P5-CONTACT'));
  let n = 0;
  for (const x of [-60, -30, 0, 30, 60, 90]) s.push(hop(x), ...probe(T, `P5-F-x${x}-h${++n}`), { wait: 1300 });
  for (let i = 1; i <= 5; i++) s.push(...probe(T, 'P5-HOLD-' + i), { wait: 1500 });   // stranded walkers pile up at the far end
  s.push(mark(T, 'P5-CLEAR'), { chat: '/glw wanted clear', after: 3000, bot: 'Runner' }, ...probe(T, 'P5-CLEARED'),
    mark(T, 'P5-ADD1'), { chat: '/glw wanted add 1', after: 100, bot: 'Runner' });
  for (let i = 1; i <= 6; i++) s.push(...probe(T, 'P5-R-' + i));                       // ~1.1 s each
  s.push(wanted('Runner'), mark(T, 'P5-END'), { chat: '/glw wanted clear', after: 3000, bot: 'Runner' },
    C('tp Runner -90 -60 0', 600), { wait: 15000 }, ...probe(T, 'P5-SETTLED'));

  // PHASE P: 1 star, sealed box at x 86..94 (radio-far pattern) until the star drops, then open and hop for 90 s
  s.push(C('tp Runner 90 -60 14', 600), C('fill 86 -61 -4 94 -56 4 minecraft:stone hollow', 800),
    { expectLog: 'Successfully filled', timeout: 10000 },
    mark(T, 'PP-WANTED1'), { chat: '/glw wanted add 1', after: 300, bot: 'Runner' }, C('tp Runner 90 -60 0', 500),
    ...probe(T, 'PP-SEALED-0'), wanted('Runner'),
    // the star drop is announced in chat on its own; the bot's /glw wanted polls ride on the probes
    { expectChat: 'decreased ☆☆☆☆☆', timeout: 60000, bot: 'Runner' },
    mark(T, 'PP-DROPPED'), wanted('Runner'), ...probe(T, 'PP-DROP-0'),
    C('fill 86 -61 -4 94 -56 4 minecraft:air', 800), { expectLog: 'Successfully filled', timeout: 10000 },
    mark(T, 'PP-OPEN'));
  for (let i = 1; i <= 30; i++) {
    s.push(hop(i % 2 ? -90 : 90), ...probe(T, 'PP-H' + i), ...(i % 5 === 0 ? [wanted('Runner')] : []), { wait: 1400 });
  }
  s.push(wanted('Runner'), mark(T, 'END'), { quit: true });
  write('spawn-dup-b', s);
}
