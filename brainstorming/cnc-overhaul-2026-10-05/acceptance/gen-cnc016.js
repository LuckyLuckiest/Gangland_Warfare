// gen-cnc016.js - writes scenarios/cnc016-S1..S16-*.json for the Cops N Crooks 0.16 "Where they come from" acceptance rows
// (harness step format, see E:/Programming/java/wt/_programme/harness/README.md). Run: node gen-cnc016.js (16 files).
// Flat world, ground block at Y=-61, feet at Y=-60. Cop NPCs are Citizens PLAYER NPCs: selectors must be
// `@e[...] if entity @s[type=minecraft:player]`, never @e[type=player]. The R/N/A regression rows are the 0.15 files of gen-cnc015.js.
//
// One extra step type is used: {leftClick:[x,y,z]} (block_dig START, the wand's pos1). The stock run.js has no left click, so
// run-row-cnc016.sh runs a generated copy, harness/run-cnc016.js, with that one step added (see mk-run-cnc016.sh).
//
// World layout every station-driven row shares (the wand builds it through STATIONS, so S1 is also the setup of S2..S8, S16):
//   Northside Station  anchor (100,-61,40)             -> 90 blocks from the sealed room = ETA 9 s at Unit_Speed 10
//   Docks (district)   x 80..130, z 110..150, y -61..-59   contains the sealed room below
//   ROOM               x 96..104, z 126..134, y -61..-56 (hollow stone), Runner stands at (100,-60,130) = unseen
//   Boathouse (hideout, S7 only) x 153..159, z 129..133 inside ROOM2 (x 150..162, z 126..136)
'use strict';
const fs = require('fs'), path = require('path');
const OUT = path.join(__dirname, 'scenarios');
fs.mkdirSync(OUT, { recursive: true });
const write = (name, steps) => fs.writeFileSync(path.join(OUT, name + '.json'), JSON.stringify(steps));

const say = (t) => ({ console: `say CNCMARK:${t}`, after: 100 });
// a marker that also moves the harness log cursor past itself, so a later expectLog only sees lines logged after the marker
const mark = (t) => [say(t), { expectLog: 'CNCMARK:' + t, timeout: 4000 }];
const tp = (x, y, z) => ({ console: `tp Runner ${x} ${y} ${z}`, after: 400 });
const SETUP = (...bots) => [
  ...bots.map(b => ({ join: b, after: 800 })),
  { console: 'gamerule spawn_mobs false' }, { console: 'gamerule spawn_monsters false' }, { console: 'gamerule advance_time false' },
  { console: 'time set 6000' }, { console: 'weather clear' }, { console: 'kill @e[type=minecraft:slime]' },
  ...bots.flatMap(b => [{ console: `gamemode survival ${b}`, after: 200 },
    { console: `effect give ${b} minecraft:resistance 100000 4 true`, after: 200 }]),
  { console: 'attribute Runner minecraft:max_health base set 1024', after: 200 },
  { console: 'effect give Runner minecraft:instant_health 1 8 true', after: 200 },
];
// death and PvP rows: nobody is protected, health stays 20
const PLAIN = (...bots) => [
  ...bots.map(b => ({ join: b, after: 800 })),
  { console: 'gamerule spawn_mobs false' }, { console: 'gamerule spawn_monsters false' }, { console: 'gamerule advance_time false' },
  { console: 'time set 6000' }, { console: 'weather clear' }, { console: 'kill @e[type=minecraft:slime]' },
  ...bots.map(b => ({ console: `gamemode survival ${b}`, after: 200 })),
];
const STAGE = { console: 'tp Runner 100 -60 100 0 0', after: 500 };
const ROOM = [{ console: 'fill 96 -61 126 104 -56 134 minecraft:stone hollow', after: 500 }];
const HIDE = [...ROOM, tp(100, -60, 130)];
const BAL = (tag, bot) => [{ chat: '/glw balance', ...(bot ? { bot } : {}), after: 1200 }, { expectChat: 'balance:', timeout: 4000 }, say('BAL:' + tag)];
const WANT = (n) => [{ chat: `/glw wanted add ${n}`, after: 800 }, { expectChat: 'increased|wanted', timeout: 5000 }];
const STATUS = (tag, bot) => [{ chat: '/glw wanted', ...(bot ? { bot } : {}), after: 1200 }, say('STATUS:' + tag)];
const END = [{ wait: 1500 }, { quit: true }];

// ---- the setup wand: pos1 = left click, pos2 = right click, then /glw cop setup save <name> ----
const LEFT = (x, y, z) => ({ leftClick: [x, y, z], after: 400 });
const RIGHT = (x, y, z) => ({ activateBlock: [x, y, z], after: 400 });
const WAND = [{ console: 'gamemode creative Runner', after: 200 }, { console: 'clear Runner', after: 300 },
  { chat: '/glw cop setup wand', after: 800 }, { expectChat: 'wand given', timeout: 4000 }, { hold: 0 }];
const placeStation = (name, x, z) => [tp(x, -60, z - 2), { chat: '/glw cop setup mode station', after: 600 },
  LEFT(x, -61, z), { expectChat: 'pos1 set', timeout: 3000 },
  { chat: `/glw cop setup save ${name}`, after: 800 }, { expectChat: 'Station .* saved|already exists', timeout: 4000 }];
// pos2 is a stone block one block above the feet so the region spans the standing height (regions are bounded in Y)
const placeRegion = (mode, name, x1, z1, x2, z2) => [{ console: `setblock ${x2} -59 ${z2} minecraft:stone`, after: 300 },
  tp(x1, -60, z1 - 2), { chat: `/glw cop setup mode ${mode}`, after: 600 }, LEFT(x1, -61, z1), { expectChat: 'pos1 set', timeout: 3000 },
  tp(x2, -60, z2 - 2), RIGHT(x2, -59, z2), { expectChat: 'pos2 set', timeout: 3000 },
  { chat: `/glw cop setup save ${name}`, after: 800 }, { expectChat: 'Region .* saved', timeout: 4000 }];
const STATIONS = [...WAND, ...placeStation('Northside Station', 100, 40), ...placeRegion('district', 'Docks', 80, 110, 130, 150),
  { console: 'gamemode survival Runner', after: 200 }];

// S1 wand places "Northside Station" + "Docks" district; the list shows both
write('cnc016-S1-wand-station-docks', [...SETUP('Runner'), ...STATIONS, { chat: '/glw cop setup list', after: 1500 }, say('S1:LIST'), ...END]);

// S2 crime in the Docks: Dispatch_En_Route names the station with an ETA, UNIT hidden=true after the delay
write('cnc016-S2-dispatch-from-station', [...SETUP('Runner'), ...STATIONS, ...HIDE, ...mark('S2:CRIME'), ...WANT(1),
  { expectChat: 'en route from', timeout: 8000 }, say('S2:RADIO'),
  { expectLog: 'UNIT Runner .*hidden=', timeout: 45000 }, say('S2:UNIT'), { wait: 2000 }, ...END]);

// S3 three stars: UNIT lines with tiers 2 and 3 (Commander/Defender/Marksman @3, Pointman/Assault @2)
write('cnc016-S3-mixed-tiers', [...SETUP('Runner'), ...STATIONS, ...HIDE, ...mark('S3:CRIME'), ...WANT(3),
  { expectLog: 'UNIT Runner .*tier=', timeout: 45000 }, { wait: 25000 }, say('S3:DONE'), ...END]);

// S4 wipe the squad (kill, no attacker, so no crime): Wipe_Refill + DISPATCH reason=wipe hold>0
const TAGCOPS = { console: 'execute positioned 100 -60 130 as @e[distance=..200] if entity @s[type=minecraft:player,name=!Runner] run tag @s add cncc', after: 300 };
write('cnc016-S4-wipe-breather', [...SETUP('Runner'), ...STATIONS, ...HIDE, ...WANT(3), { wait: 40000 }, say('S4:SQUAD'),
  TAGCOPS, ...mark('S4:WIPED'), { console: 'kill @e[tag=cncc]', after: 800 },
  { expectChat: 'Squad down', timeout: 15000 }, say('S4:REFILL'),
  { expectLog: 'UNIT Runner ', timeout: 60000 }, say('S4:UNIT'), ...END]);

// S5 break contact at 3 stars (sealed room, never seen): PERIMETER start posts=2, two Post_Up lines
write('cnc016-S5-perimeter-posts', [...SETUP('Runner'), ...STATIONS, ...HIDE, ...mark('S5:CRIME'), ...WANT(3),
  { expectLog: 'PERIMETER Runner start', timeout: 90000 }, say('S5:PERIMETER'), { wait: 8000 }, say('S5:DONE'), ...END]);

// S6 outrun the squad in the open (15 blocks a second, +x): HANDOFF, then DISPATCH bias=true and UNIT bias=true ahead=true
const HOPS = (n, step) => Array.from({ length: n }, (_, k) => ({ console: `tp Runner ${100 + step * (k + 1)} -60 100`, after: 1000 }));
write('cnc016-S6-outrun-handoff', [...SETUP('Runner'), ...STATIONS, STAGE, ...WANT(3), { wait: 20000 }, ...mark('S6:RUN'),
  ...HOPS(30, 15), say('S6:HOPPED'),
  { expectLog: 'HANDOFF Runner', timeout: 30000 }, say('S6:HANDOFF'),
  { expectLog: 'UNIT Runner .*bias=true', timeout: 60000 }, say('S6:BIASED'), ...END]);

// S7 hideout reached unseen: Boathouse hideout inside a second sealed room; EVASION hideout=2.0 once Runner is in it
const ROOM2 = [{ console: 'fill 150 -61 126 162 -56 136 minecraft:stone hollow', after: 500 }];
write('cnc016-S7-hideout-faster', [...SETUP('Runner'), ...STATIONS, { console: 'gamemode creative Runner', after: 200 },
  ...placeRegion('hideout', 'Boathouse', 153, 129, 159, 133), { console: 'gamemode survival Runner', after: 200 },
  ...ROOM2, ...HIDE, ...WANT(2), { wait: 15000 }, ...mark('S7:MOVE'), tp(156, -60, 131),
  { expectLog: 'EVASION Runner .*hideout=2', timeout: 40000 }, say('S7:HIDEOUT'), ...END]);

// S8 one quiet minute at 5 stars, sealed: Returning_To_Patrol and EVASION quiet>1
write('cnc016-S8-quiet-minute', [...SETUP('Runner'), ...STATIONS, ...HIDE, ...mark('S8:CRIME'), ...WANT(5),
  { expectChat: 'returning to patrol', timeout: 120000 }, say('S8:PATROL'), { wait: 20000 }, say('S8:DONE'), ...END]);

// S9 phone contact while unseen (sealed room, 2 stars): /glw contact 1 -> Contact.Used text, one star less, 1000 paid
write('cnc016-S9-contact-phone', [...SETUP('Runner'), ...STATIONS, { chat: '/glw economy set 5000', after: 1000 }, ...BAL('before'),
  ...HIDE, ...WANT(2), ...STATUS('S9-before'), { wait: 10000 }, { chat: '/glw contact 1', after: 1000 },
  { expectChat: 'Your contact made', timeout: 5000 }, say('S9:USED'), ...STATUS('S9-after'), ...BAL('after'), ...END]);

// S10 [WANTED] sign (remove 1 star, $500) used while a cop has eyes on Runner (open ground): Contact.Seen, balance unchanged
write('cnc016-S10-sign-refused', [...SETUP('Runner'), { chat: '/glw economy set 5000', after: 1000 }, ...BAL('before'), STAGE,
  { console: 'setblock 102 -60 100 minecraft:oak_sign{front_text:{messages:["[WANTED]","remove","1","$500"]}}', after: 500 },
  ...WANT(2), { wait: 25000 }, say('S10:CLICK'), { activateBlock: [102, -60, 100], after: 1200 },
  { expectChat: 'eyes on you', timeout: 5000 }, say('S10:REFUSED'), ...BAL('after'), ...STATUS('S10-after'), ...END]);

// S11 a rival shoots first (4 damage), Runner kills them: no star. Two such kills inside 10 s would be 160+ heat = a star without
// the self-defence exemption, so zero stars is a real check
write('cnc016-S11-self-defence', [...PLAIN('Runner', 'Rival1', 'Rival2'), STAGE, ...STATUS('S11-before', 'Runner'),
  { console: 'damage Runner 4 minecraft:player_attack by Rival1', after: 600 },
  { console: 'damage Rival1 1000 minecraft:player_attack by Runner', after: 1500 },
  { console: 'damage Runner 4 minecraft:player_attack by Rival2', after: 600 },
  { console: 'damage Rival2 1000 minecraft:player_attack by Runner', after: 1500 },
  { wait: 1500 }, ...STATUS('S11-after', 'Runner'), { quit: true, bot: 'Runner' }, { quit: true, bot: 'Rival1' }, { quit: true, bot: 'Rival2' }]);

// S12 two posted bounties (500 each) claimed by Hunter inside 10 s: no star, and the money is paid
write('cnc016-S12-posted-bounty', [...PLAIN('Poster', 'Target1', 'Target2', 'Hunter'),
  { chat: '/glw economy set 3000', bot: 'Poster', after: 1000 }, { chat: '/glw economy set 0', bot: 'Hunter', after: 1000 },
  { chat: '/glw bounty set Target1 500', bot: 'Poster', after: 1500 }, { chat: '/glw bounty set Target2 500', bot: 'Poster', after: 1500 },
  { chat: '/glw bounty', bot: 'Target1', after: 1200 }, say('S12:POSTED'),
  ...BAL('before', 'Hunter'),
  { console: 'damage Target1 1000 minecraft:player_attack by Hunter', after: 1200 },
  { console: 'damage Target2 1000 minecraft:player_attack by Hunter', after: 1500 },
  { wait: 2000 }, ...BAL('after', 'Hunter'), ...STATUS('S12-after', 'Hunter'),
  { quit: true, bot: 'Poster' }, { quit: true, bot: 'Target1' }, { quit: true, bot: 'Target2' }, { quit: true, bot: 'Hunter' }]);

// S13 two unposted five-star players killed inside 10 s: Hunter gains a star (the S11/S12 pattern with nothing exempting it)
write('cnc016-S13-unposted-kill', [...PLAIN('Victim1', 'Victim2', 'Hunter'),
  { chat: '/glw wanted add 5', bot: 'Victim1', after: 1000 }, { chat: '/glw wanted add 5', bot: 'Victim2', after: 1000 },
  ...STATUS('S13-hunter-before', 'Hunter'), say('S13:KILLING'),
  { console: 'damage Victim1 1000 minecraft:player_attack by Hunter', after: 1200 },
  { console: 'damage Victim2 1000 minecraft:player_attack by Hunter', after: 1500 },
  { wait: 1500 }, ...STATUS('S13-hunter-after', 'Hunter'),
  { quit: true, bot: 'Victim1' }, { quit: true, bot: 'Victim2' }, { quit: true, bot: 'Hunter' }]);

// S14 / S15 share the hospital: a HOSPITAL waypoint named Infirmary at (150,-60,260); death spot (260,-60,200); 5000 in the wallet
//   (bundled Lose_Money 'balance * 0.15' = 750). S14 runs on profile s14 (Death.Respawn.Enable true, Delay 3): downed, then respawned.
//   S15 runs on default-016 (Respawn off): a vanilla death; keepInventory so any item at the body is a cash drop.
const HOSPITAL = [{ chat: '/glw economy set 5000', after: 1000 }, ...BAL('before'), tp(150, -60, 260),
  { chat: '/glw waypoint create Infirmary', after: 800 }, { chat: '/glw waypoint create confirm', after: 1200 },
  { chat: '/glw waypoint type hospital', after: 1000 }, say('HOSPITAL:SET'), tp(260, -60, 200)];
write('cnc016-S14-hospital-downed', [...PLAIN('Runner'), ...HOSPITAL, ...mark('S14:DOWN'),
  { console: 'damage Runner 1000 minecraft:generic', after: 1000 },
  { expectLog: 'HOSPITAL Runner', timeout: 20000 }, say('S14:HOSPITAL'),
  { expectLog: 'SHIELD Runner', timeout: 5000 }, { wait: 2000 }, ...BAL('after'),
  { console: 'data get entity Runner Pos', after: 600 }, say('S14:POS'), ...END]);
write('cnc016-S15-hospital-vanilla-death', [...PLAIN('Runner'), { console: 'gamerule keepInventory true' }, ...HOSPITAL, ...mark('S15:DEATH'),
  { console: 'kill Runner', after: 1000 },
  { expectLog: 'HOSPITAL Runner', timeout: 20000 }, say('S15:HOSPITAL'),
  { expectLog: 'SHIELD Runner', timeout: 5000 }, { wait: 2000 }, ...BAL('after'),
  { console: 'data get entity Runner Pos', after: 600 }, say('S15:POS'),
  { console: 'execute as @e[type=minecraft:item,x=260,y=-60,z=200,distance=..12] run say CNCITEM', after: 500 }, say('S15:ITEMS'), ...END]);

// S16 logout mid-chase at 1 star, rejoin: DISPATCH reason=restore hold=15s, EVASION hold=enroute, the star outlives the grace
write('cnc016-S16-logout-restore', [...SETUP('Runner'), ...STATIONS, ...HIDE, ...WANT(1), { wait: 2000 }, ...STATUS('S16-before-quit'),
  { quit: true }, { wait: 3000 },
  { join: 'Runner', after: 3000 }, { console: 'gamemode survival Runner', after: 200 }, ...mark('S16:REJOIN'), ...STATUS('S16-after-rejoin'),
  { expectLog: 'UNIT Runner ', timeout: 60000 }, say('S16:FIRSTUNIT'), ...STATUS('S16-at-first-unit'), ...END]);
