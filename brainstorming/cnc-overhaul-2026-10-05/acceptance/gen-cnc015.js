// gen-cnc015.js - writes scenarios/*.json for the Cops N Crooks 0.15 acceptance rows (harness step format, see
// E:/Programming/java/wt/_programme/harness/README.md). Run: node gen-cnc015.js. Flat world, ground at Y=-60.
// Cop NPCs are Citizens PLAYER NPCs: selectors must be `@e[...] if entity @s[type=minecraft:player]`, never @e[type=player].
'use strict';
const fs = require('fs'), path = require('path');
const OUT = path.join(__dirname, 'scenarios');
fs.mkdirSync(OUT, { recursive: true });
const write = (name, steps) => fs.writeFileSync(path.join(OUT, name + '.json'), JSON.stringify(steps));

const COPS = (b) => `@e[distance=..160,name=!${b}]`;                       // anything near the bot that is not the bot
const mark = (tag, bot = 'Runner') => ({ console: `execute as ${COPS(bot)} if entity @s[type=minecraft:player] run say CNCCOP:${tag}`, after: 400 });
const say = (t) => ({ console: `say CNCMARK:${t}`, after: 100 });
const tpR = (x, y, z) => ({ console: `tp Runner ${x} ${y} ${z}`, after: 300 });
const SETUP = (...bots) => [
  ...bots.map(b => ({ join: b, after: 800 })),
  { console: 'gamerule spawn_mobs false' }, { console: 'gamerule spawn_monsters false' }, { console: 'gamerule advance_time false' },
  { console: 'time set 6000' }, { console: 'weather clear' }, { console: 'kill @e[type=minecraft:slime]' },
  ...bots.flatMap(b => [{ console: `gamemode survival ${b}`, after: 200 },
    { console: `effect give ${b} minecraft:resistance 100000 4 true`, after: 200 }]),
  { console: 'attribute Runner minecraft:max_health base set 1024', after: 200 },
  { console: 'effect give Runner minecraft:instant_health 1 8 true', after: 200 },
];
const STAGE = { console: 'tp Runner 100 -60 100 0 0', after: 500 };
// a sealed 9x6x9 stone room: cops cannot see in or path in, so the evasion clock sees no sighting (interior y -60..-57)
const ROOM = [{ console: 'fill 96 -61 126 104 -56 134 minecraft:stone hollow', after: 500 }];
const HIDE = [...ROOM, tpR(100, -60, 130)];
const BAL = (tag) => [{ chat: '/glw balance', after: 1200 }, { expectChat: 'balance:', timeout: 4000 }, say('BAL:' + tag)];
const WANT = (n) => [{ chat: `/glw wanted add ${n}`, after: 800 }, { expectChat: 'increased|wanted', timeout: 5000 }];
const STATUS = (tag) => [{ chat: '/glw wanted', after: 1200 }, say('STATUS:' + tag)];
const END = [{ wait: 1500 }, { quit: true }];

// R1 reg-ladder-1-3-5: squad sizes at 1, 3 and 5 stars (compare with Cops.Count + Squad_Composition; cnc-verdict.js prints both)
write('R1-reg-ladder-1-3-5', [...SETUP('Runner'), ...[1, 3, 5].flatMap(n => [
  STAGE, ...WANT(n), { wait: 15000 }, mark(`R1-${n}`), ...STATUS(`R1-${n}`),
  { chat: '/glw wanted clear', after: 800 }, { wait: 8000 }, { console: 'kill @e[type=!minecraft:player]', after: 300 }]), ...END]);

// R2 reg-los-break: the H11 line-of-sight break (copied from scen-h11-losbreak.json, judged by h11-verdict.js "losbreak")
fs.copyFileSync('C:/Users/Hashim/AppData/Local/Temp/claude/E--Programming-java-Keystone/testserver-work/harness/scen-h11-losbreak.json',
  path.join(OUT, 'R2-reg-los-break.json'));

const JAIL = [{ console: 'tp Runner 160 -60 160 0 0', after: 800 }, { chat: '/glw jail create', after: 1500 }, { chat: '/glw jail setexit', after: 1500 }, { chat: '/glw jail list', after: 1000 }];
// R3 reg-cuffed (profile r3: Repeating_Timer.Time 10): 2 stars, stand still, get cuffed; stars must stay 2 while cuffed and
// clear at intake. Wording of the cuff/intake chat lines is matched loosely; the verdict prints the whole transcript.
write('R3-reg-cuffed', [...SETUP('Runner'), ...JAIL, STAGE, ...WANT(2), say('R3:START'),
  { expectChat: 'cuff|detain|handcuff|arrest', timeout: 90000 }, say('R3:CUFFED'),
  ...STATUS('R3-cuffed-0'), { wait: 15000 }, ...STATUS('R3-cuffed-15'), { wait: 15000 }, ...STATUS('R3-cuffed-30'),
  { expectChat: 'jail|booked|intake|processed|sentence|charge', timeout: 120000 }, say('R3:INTAKE'),
  { wait: 3000 }, ...STATUS('R3-after-intake'), ...END]);

// R4 reg-logout (profile r4): phase A = 2 stars then quit; phase B = rejoin on the same server dir (run-row twice, no re-prep)
write('R4a-reg-logout-quit', [...SETUP('Runner'), STAGE, ...WANT(2), { wait: 2000 }, ...STATUS('R4-before-quit'), { quit: true }]);
write('R4b-reg-logout-rejoin', [{ join: 'Runner', after: 3000 }, { console: 'gamemode survival Runner', after: 200 },
  ...STATUS('R4-after-rejoin'), say('R4:REJOINED'),
  { expectChat: 'decreased', timeout: 60000 }, say('R4:DECAYED'), ...STATUS('R4-after-decay'), ...END]);

// N1 evasion-drop: 2 stars, seal yourself in a room, wait <= 3 + 20 + 5 s for "decreased", expect 1 star and no death
write('N1-evasion-drop', [...SETUP('Runner'), STAGE, ...WANT(2), { wait: 5000 }, say('N1:HIDING'), ...HIDE,
  { expectChat: 'decreased', timeout: 40000 }, say('N1:DROPPED'), ...STATUS('N1-after-drop'), ...END]);

// N2 no-money-on-drop: default config AND (second pass, profile legacy-settings) the 0.13.0 settings.yml; balance must not move
write('N2-no-money-on-drop', [...SETUP('Runner'), { chat: '/glw economy set 5000', after: 1000 }, ...BAL('before'),
  STAGE, ...WANT(2), { wait: 5000 }, ...HIDE, { expectChat: 'decreased', timeout: 40000 }, say('N2:DROPPED'),
  ...BAL('after'), ...STATUS('N2-after-drop'), ...END]);

// N3 charge-switched-on (profile n3): a drop at 2 stars takes 50 * 5^2 = 1250 (5000 -> 3750)
write('N3-charge-switched-on', [...SETUP('Runner'), { chat: '/glw economy set 5000', after: 1000 }, ...BAL('before'),
  STAGE, ...WANT(2), { wait: 5000 }, ...HIDE, { expectChat: 'decreased', timeout: 40000 }, say('N3:DROPPED'),
  ...BAL('after'), ...STATUS('N3-after-drop'), ...END]);
// N3b charge-broken-formula (profile n3-broken): two drops (2->1->0); exactly one "Money formula" warning, fallback charged
// 1250 + 250 = 1500 (5000 -> 3500), stars still fall
write('N3b-charge-broken-formula', [...SETUP('Runner'), { chat: '/glw economy set 5000', after: 1000 }, ...BAL('before'),
  STAGE, ...WANT(2), { wait: 5000 }, ...HIDE, { expectChat: 'decreased', timeout: 40000 }, say('N3b:DROP1'),
  { expectChat: 'decreased|cleared', timeout: 60000 }, say('N3b:DROP2'), { wait: 1000 },
  ...BAL('after'), ...STATUS('N3b-after-drops'), ...END]);

// N4 bounty-upgrade, phase A on a 0.13.0 server (profile n4-old): Poster puts a 500 bounty on Target, then both quit
write('N4a-bounty-post-on-0.13.0', [...['Poster', 'Target', 'Hunter'].flatMap(b => [{ join: b, after: 800 }, { console: `gamemode survival ${b}`, after: 200 }]), // no resistance: damage must land
 
  { chat: '/glw economy set 2000', bot: 'Poster', after: 1000 }, { chat: '/glw bounty set Target 500', bot: 'Poster', after: 1500 },
  { chat: '/glw bounty', bot: 'Target', after: 1200 }, say('N4:POSTED'), { chat: '/glw economy set 0', bot: 'Hunter', after: 800 },
  { wait: 1500 }, { quit: true, bot: 'Poster' }, { quit: true, bot: 'Target' }, { quit: true, bot: 'Hunter' }]);
// N4 phase B after `prep-cnc015.sh n4-new` (0.15.0 jars on the same data): Hunter kills Target twice
write('N4b-bounty-kill-on-0.15.0', [
  { join: 'Target', after: 2500 }, { join: 'Hunter', after: 2500 },
  { console: 'gamemode survival Target', after: 200 }, { console: 'gamemode survival Hunter', after: 200 },
  { chat: '/glw balance', bot: 'Hunter', after: 1200 }, say('N4:BAL0'),
  { console: 'damage Target 1000 minecraft:player_attack by Hunter', after: 1500 }, { wait: 3000 },
  { chat: '/glw balance', bot: 'Hunter', after: 1200 }, say('N4:BAL1-after-first-kill'),
  { wait: 6000 },
  { console: 'damage Target 1000 minecraft:player_attack by Hunter', after: 1500 }, { wait: 3000 },
  { chat: '/glw balance', bot: 'Hunter', after: 1200 }, say('N4:BAL2-after-second-kill'),
  { chat: '/glw bounty', bot: 'Target', after: 1200 }, { quit: true, bot: 'Target' }, { quit: true, bot: 'Hunter' }]);

const TAGCOPS = { console: 'execute positioned 100 -60 105 as @e[distance=..100] if entity @s[type=minecraft:player,name=!Runner] run tag @s add cncc', after: 300 }; // T18: Citizens cops are not matched by name=Officer; tag them via the player-type check
const HIT = (dmg) => ({ console: 'damage @e[tag=cncc,limit=1,sort=nearest] ' + dmg + ' minecraft:player_attack by Runner', after: 500 });
// N5 shots-fired (best effort): 2 stars, a pistol shot with a cop inside 48 blocks -> Shots_Fired radio line
write('N5-shots-fired', [...SETUP('Runner'), STAGE, ...WANT(2), { wait: 6000 },
  { console: 'clear Runner', after: 300 }, { console: 'bartizan weapon give Runner pistol 1', after: 1000 }, { hold: 0 }, { wait: 800 },
  say('N5:SHOT1'), { useItem: true }, { expectChat: 'shots fired|gunfire', timeout: 8000 }, say('N5:RADIO'), { wait: 1500 }, { useItem: true }, { wait: 1500 }, say('N5:SHOT2'), ...STATUS('N5'), ...END]);

// N6 assault-cop-star (best effort): one hit on a cop at 0 stars = 1 star (Assault_Cop 100 = the first threshold). A cop must
// exist: raise to 1 star, clear the stars (cops linger briefly), then hit one cop once.
write('N6-assault-cop-star', [...SETUP('Runner'), STAGE, ...WANT(1), { wait: 8000 },
  TAGCOPS, { chat: '/glw wanted clear', after: 800 }, ...STATUS('N6-cleared'),
  HIT(1), { wait: 500 },
  { wait: 1500 }, ...STATUS('N6-after-hit'), ...END]);

// N7 charge-sheet (best effort, profile default): $300, 2 stars, get cuffed and booked -> sheet 700, paid 300, +40 s
write('N7-charge-sheet', [...SETUP('Runner'), { chat: '/glw economy set 300', after: 1000 }, ...BAL('before'), ...JAIL,
  STAGE, ...WANT(2), { expectChat: 'cuff|detain|handcuff|arrest', timeout: 90000 }, say('N7:CUFFED'),
  { expectChat: 'charge sheet|sheet|700|booked', timeout: 120000 }, say('N7:SHEET'), { wait: 3000 },
  ...BAL('after'), ...STATUS('N7'), ...END]);

// N8 regroup (best effort): 3 stars, two cop kills inside 20 s -> Regroup then Regroup_Push radio lines
write('N8-regroup', [...SETUP('Runner'), STAGE, ...WANT(3), { wait: 10000 },
  TAGCOPS, say('N8:KILL1'), HIT(1000),
  { wait: 4000 }, HIT(1000), say('N8:KILL2'),
  { expectChat: 'two down|losing men|backup is coming', timeout: 12000 }, say('N8:REGROUP'), { expectChat: 'backup.s here|push together|everyone move in', timeout: 25000 }, say('N8:PUSH'), ...END]);

// ---- 0.15.2 AUTO rows (profile auto; see auto-drop/PLAN.md section 9). Verdicts read the `AUTO drop ... ending=` debug lines. ----
// Target must be killable: no resistance effect (N4a pattern), so he joins after SETUP.
const TARGET = [{ join: 'Target', after: 800 }, { console: 'gamemode survival Target', after: 200 }];
const KILL = { console: 'damage Target 1000 minecraft:player_attack by Runner', after: 1500 };
const DROPS = (tag, n, timeout = 60000) => Array.from({ length: n }, (_, i) =>
  [{ expectChat: 'decreased|cleared', timeout }, say(`${tag}:DROP${i + 1}`)]).flat();

// A1 auto-hunker: 3 stars, sealed -> three single drops (about 30 / 15 / 6 s), ending=HUNKER_DOWN x3
write('A1-auto-hunker', [...SETUP('Runner'), STAGE, ...WANT(3), { wait: 5000 }, say('A1:HIDING'), ...HIDE, ...DROPS('A1', 3), ...END]);
// A2 auto-petty: one kill (80 heat, no star) then 2 admin stars, sealed -> 2 -> 0 in one drop, ending=PETTY
write('A2-auto-petty', [...SETUP('Runner'), ...TARGET, STAGE, KILL, ...WANT(2), { wait: 3000 }, say('A2:HIDING'), ...HIDE, ...DROPS('A2', 1, 90000), ...END]);
// A3 auto-rampage-lock: 4 stars plus a kill, sealed at once -> first drop 4 -> 3, ending=STILL_HOT reason=rampage
write('A3-auto-rampage-lock', [...SETUP('Runner'), ...TARGET, STAGE, ...WANT(4), KILL, say('A3:HIDING'), ...HIDE, ...DROPS('A3', 1, 90000), ...END]);
// A4 auto-logout-lock (two runs on one server, no re-prep): A4a = A2 setup then quit; A4b = rejoin, wait for the squad, seal
write('A4a-auto-logout-quit', [...SETUP('Runner'), ...TARGET, STAGE, KILL, ...WANT(2), { wait: 2000 }, ...STATUS('A4-before-quit'),
  { quit: true, bot: 'Target' }, { quit: true }]);
write('A4b-auto-logout-rejoin', [{ join: 'Runner', after: 3000 }, { console: 'gamemode survival Runner', after: 200 },
  ...STATUS('A4-after-rejoin'), { wait: 8000 }, mark('A4-squad'), say('A4:HIDING'), ...HIDE, ...DROPS('A4', 1, 90000), ...END]);
// A5 auto-learn-persist: A5a = two PETTY chases in one boot (two kills inside 10 s = 200 heat = 1 star, cause CRIME); the run-all
// script then reads chase_habit with sqlite3 (server stopped); A5b = a third chase after the restart, its debug line shows delta > 0
const CHASE = (tag) => [STAGE, KILL, { console: 'damage Target 1000 minecraft:player_attack by Runner', after: 1500 }, say(`${tag}:START`),
  ...HIDE, ...DROPS(tag, 1, 60000), { console: 'fill 96 -61 126 104 -56 134 minecraft:air', after: 500 }, { wait: 7000 }];
write('A5a-auto-learn-two-chases', [...SETUP('Runner'), ...TARGET, ...CHASE('A5-1'), ...CHASE('A5-2'), ...END]);
write('A5b-auto-learn-after-restart', [{ join: 'Runner', after: 3000 }, { console: 'gamemode survival Runner', after: 200 },
  { join: 'Target', after: 800 }, { console: 'gamemode survival Target', after: 200 }, ...CHASE('A5-3'), ...END]);
// A6 auto-config-compat: boot only, twice (run-all swaps the wanted.yml between the runs)
write('A6-auto-config-compat', [{ join: 'Runner', after: 3000 }, say('A6:BOOTED'), { wait: 1000 }, { quit: true }]);
// A7-A9 (REVIEW): 3 stars, seal, wait for the loss of sight, then hop Runner out of the zone
const HOPS = (n, step) => Array.from({ length: n }, (_, k) => ({ console: `tp Runner ${100 + step * (k + 1)} -60 130`, after: 1000 }));
const OUTRUN = (tag, ...hops) => [...SETUP('Runner'), STAGE, ...WANT(3), { wait: 5000 }, ...HIDE, { wait: 5000 }, say(tag + ':RUN'), ...hops,
  { wait: 3000 }, say(tag + ':DONE'), ...END];
write('A7-auto-clean-break', OUTRUN('A7', ...HOPS(30, 15)));      // 15 blocks a second, each hop under the 32-block teleport rule
write('A8-auto-flee-on-foot', OUTRUN('A8', ...HOPS(40, 5)));     // 5 blocks a second, about a sprint
write('A9-auto-teleport', OUTRUN('A9', tpR(250, -60, 130), { wait: 35000 })); // one jump of 150 blocks
