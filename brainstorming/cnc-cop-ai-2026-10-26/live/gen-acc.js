#!/usr/bin/env node
// gen-acc.js -> scen/acc-*.json : 0.21.1 acceptance scenarios (owner rulings R1-R3, R5, R6 of 2026-10-26).
// Recorders like gen-scen.js (hard expects guard setup only, rc=1 = setup broke); judge-acc.py gives the verdicts.
//   acc-spawn-dup / acc-spawn-dup-b / acc-radio-route : the repro scenarios + a `data get entity <bot> Rotation`
//                                                       after every Pos probe (R6 needs the bot's facing)
//   acc-spawn-view : R6 discriminator - level 3 corridor hops with an explicit yaw, phase LA looking where he runs,
//                    phase LB looking back; then SV-S/SV-N facing toward / away from the station spawner
//   acc-radio-far  : radio-far + Rotation probes + a ~10-block walk WEST while the squad sees him before the hop
//                    south (R5: the squad's last-known history says west; the old live-movement heading says south)
//   acc-brandish   : rows Drawer (complied), Oov (R2: out of view -> Lost_Sight verdict, then pulls from verdict+20 s),
//                    Nighter/Sneaker (range shrink stays), Igner (R1: first ignore = star + cause in chat)
'use strict';
const fs = require('fs'), path = require('path');
const SC = path.join(__dirname, 'scen');
const load = n => JSON.parse(fs.readFileSync(path.join(SC, n + '.json'), 'utf8'));
const write = (n, s) => { fs.writeFileSync(path.join(SC, n + '.json'), JSON.stringify(s, null, 1)); console.log(n, s.length, 'steps'); };
const withRotation = steps => steps.flatMap(st => {
  const m = st.console && /^data get entity (\S+) Pos$/.exec(st.console);
  return m ? [st, { console: 'data get entity ' + m[1] + ' Rotation', after: 150 }] : [st];
});

write('acc-spawn-dup', withRotation(load('spawn-dup')));
write('acc-spawn-dup-b', withRotation(load('spawn-dup-b')));
write('acc-radio-route', withRotation(load('radio-far-route-r2')));

// ---- acc-radio-far ---------------------------------------------------------------------------------------------
{
  const s = withRotation(load('radio-far'));
  const at = s.findIndex(x => x.console === 'say CNCMARK:RADIO:CONTACT-b');
  if (at < 0) throw new Error('CONTACT-b marker missing');
  s.splice(at, 0,
    { console: 'say CNCMARK:RADIO:WALK-WEST', after: 200 },
    { lookAt: [-200, -58.38, 0], bot: 'Runner' },
    { control: { state: 'forward', ms: 2500 }, bot: 'Runner' });
  write('acc-radio-far', s);
}

// ---- acc-spawn-view --------------------------------------------------------------------------------------------
{
  const T = 'SPAWNV';
  const C = (console, after = 300) => ({ console, after });
  const mark = l => C(`say CNCMARK:${T}:${l}`, 200);
  const NPC_POS = 'execute as @e if entity @s[type=minecraft:player,name=!Runner] run data get entity @s Pos';
  const probe = l => [mark(l), C('glw cop list Runner', 300), C(NPC_POS, 400), C('data get entity Runner Pos', 200),
    C('data get entity Runner Rotation', 150)];
  const EAST = -90, WEST = 90, SOUTH = 0, NORTH = 180;
  const hop = (x, yaw) => C(`tp Runner ${x} -60 0 ${yaw} 0`, 300);
  const s = [C('gamerule spawn_mobs false'), C('gamerule spawn_monsters false'), C('gamerule advance_time false'),
    C('time set 6000'), C('weather clear'), { join: 'Runner' },
    C('effect give Runner minecraft:resistance 1200 4 true'), C('effect give Runner minecraft:saturation 1200 4 true'),
    C('gamemode creative Runner', 200), C('clear Runner'),
    { chat: '/glw cop setup wand', after: 800, bot: 'Runner' }, { expectChat: 'wand given', timeout: 4000, bot: 'Runner' },
    { hold: 0, bot: 'Runner' },
    C('tp Runner 0 -60 68', 400), { chat: '/glw cop setup mode station', after: 600, bot: 'Runner' },
    { leftClick: [0, -61, 70], after: 400, bot: 'Runner' }, { expectChat: 'pos1 set', timeout: 3000, bot: 'Runner' },
    { chat: '/glw cop setup save Central Station', after: 800, bot: 'Runner' },
    { expectChat: 'Station .* saved|already exists', timeout: 4000, bot: 'Runner' },
    C('tp Runner 0 -60 74', 500), { chat: '/glw cop spawner set', after: 800, bot: 'Runner' },
    { expectChat: 'Cop spawner set', timeout: 4000, bot: 'Runner' },
    C('gamemode survival Runner', 200), C('clear Runner'),
    hop(-90, EAST), { wait: 3000 }, ...probe('BASE'),
    mark('WANTED3'), { chat: '/glw wanted add 3', after: 800, bot: 'Runner' },
    { expectLog: 'DISPATCH Runner', timeout: 20000 }, { expectLog: 'UNIT Runner', timeout: 45000 },
    { chat: '/glw wanted', after: 500, bot: 'Runner' }, { wait: 3000 }, ...probe('CONTACT')];
  const X = [-90, -60, -30, 0, 30, 60, 90];
  let n = 0;
  // LA: looks where he runs (ahead = in view, so ahead spots are refused); LB: looks back (ahead is allowed)
  for (const [ph, fwdYaw, backYaw] of [['LA', EAST, WEST], ['LB', WEST, EAST]]) {
    for (let trip = 1; trip <= 2; trip++) {
      for (const leg of ['F', 'B']) {
        const xs = leg === 'F' ? X.slice(1) : X.slice(0, -1).reverse();
        const yaw = leg === 'F' ? fwdYaw : backYaw;
        for (const x of xs) s.push(hop(x, yaw), ...probe(`${ph}-T${trip}${leg}-x${x}-h${++n}`), { wait: 1300 });
        s.push(...probe(`${ph}-T${trip}${leg}-HOLD-a`), { chat: '/glw wanted', after: 500, bot: 'Runner' }, { wait: 2500 },
          ...probe(`${ph}-T${trip}${leg}-HOLD-b`), { wait: 1500 });
      }
    }
  }
  s.push(mark('CLEAR'), { chat: '/glw wanted clear', after: 3000, bot: 'Runner' }, hop(-90, EAST), { wait: 15000 },
    ...probe('CLEARED'));
  // SV-S: 54 blocks north of the station spawner (0,-60,74), facing it (inside the leash margin, in his view, open
  // ground): no unit may appear there. SV-N: same spot facing away: the spawner may be used again.
  // ACC9: wait at (-90,0) until the last squad is gone first. In acc8 its RETURNING walkers were still listed at SV-S,
  // '/glw wanted add 2' re-recruited them, the squad was full and no unit was dispatched (0 SV-S spawns to judge).
  // 330 s = Return.Max_Ticks (300 s) + slack; a timeout is a setup failure (rc=1), not a verdict.
  for (const [ph, yaw] of [['SV-S', SOUTH], ['SV-N', NORTH]]) {
    s.push({ poll: 'glw cop list Runner', until: 'Runner is not being chased', every: 3000, timeout: 330000 },
      C(`tp Runner 0 -60 20 ${yaw} 0`, 500), { wait: 2000 }, ...probe(ph + '-BASE'),
      mark(ph + '-WANTED2'), { chat: '/glw wanted add 2', after: 800, bot: 'Runner' },
      { expectLog: 'DISPATCH Runner', timeout: 30000 });
    for (let i = 1; i <= 5; i++) s.push(...probe(`${ph}-${i}`), { wait: 2500 });
    s.push(mark(ph + '-CLEAR'), { chat: '/glw wanted clear', after: 3000, bot: 'Runner' },
      C('tp Runner -90 -60 0 -90 0', 500), { wait: 15000 }, ...probe(ph + '-CLEARED'));
  }
  s.push(mark('END'), { quit: true });
  write('acc-spawn-view', s);
}

// ---- acc-brandish ----------------------------------------------------------------------------------------------
{
  const base = load('brandish');
  const steps = base.slice(0, base.findIndex(x => x.join === 'Drawer'));   // setup: station, district, Runner parked
  const BOTS = ['Runner', 'Drawer', 'Oov', 'Nighter', 'Sneaker', 'Igner'];
  const EX = BOTS.map(b => 'name=!' + b).join(',');
  const COPS = 'execute as @e if entity @s[type=minecraft:player,' + EX + '] run data get entity @s Pos';
  const NEAR = 'type=!minecraft:item,type=!minecraft:text_display,type=!minecraft:armor_stand,type=!minecraft:interaction,type=!minecraft:item_display,type=!minecraft:villager,type=!minecraft:experience_orb,' + EX + ',distance=..60,sort=nearest,limit=1';
  const c = (console, after = 200) => steps.push({ console, after });
  const w = ms => steps.push({ wait: ms });
  const say = l => c('say CNCMARK:ACCBR:' + l);
  const pull = (b, off = '~1.5 ~ ~1.5') => c('execute at @a[name=' + b + ',limit=1] as @e[' + NEAR + '] at @s run tp @a[name=' + b + ',limit=1] ' + off, 500);
  const probe = (b, l) => { say(l); c(COPS, 400); c('data get entity ' + b + ' Pos', 200); };
  const start = b => {
    steps.push({ join: b });
    for (const e of ['resistance', 'saturation']) c('effect give ' + b + ' minecraft:' + e + ' 1200 4 true', 300);
    c('gamemode survival ' + b, 200); c('clear ' + b, 300); c('tp ' + b + ' 100 -60 90', 400); w(6000);
  };
  const arm = b => { steps.push({ hold: 1, bot: b }); c('bartizan weapon give ' + b + ' pistol', 1000); steps.push({ hold: 0, bot: b }); };
  const quit = b => { steps.push({ hold: 1, bot: b }, { quit: true, bot: b }); w(2000); };
  const given = b => steps.push({ expectLog: 'WARNING ' + b + ' result=given', since: 'start', timeout: 15000 });

  // Drawer: put away inside the 5 s -> "Thanks, carry on." (control, unchanged behaviour)
  say('DRAWER-START'); start('Drawer'); arm('Drawer'); pull('Drawer'); probe('Drawer', 'D-NEAR'); given('Drawer');
  w(1500); steps.push({ hold: 1, bot: 'Drawer' });
  steps.push({ expectChat: 'Thanks, carry on', since: 'start', timeout: 4000, bot: 'Drawer' });
  probe('Drawer', 'D-AWAY'); quit('Drawer');

  // Oov (R2): warned, moved 35 blocks out of the cop's range still armed -> verdict at given+5 is Lost_Sight. Pulled
  // next to the nearest beat cop from ~verdict+20 s: the next warning must not come before verdict+30 (old: given+30)
  say('OOV-START'); start('Oov'); arm('Oov'); pull('Oov'); probe('Oov', 'O-NEAR'); given('Oov');
  c('execute as Oov at @s run tp @s ~ ~ ~35', 300); probe('Oov', 'O-FAR');
  steps.push({ expectLog: 'WARNING Oov result=complied', since: 'start', timeout: 9000 });
  steps.push({ expectChat: 'Lost sight of you', since: 'start', timeout: 3000, bot: 'Oov' });
  say('O-VERDICT'); w(20000);
  for (let i = 1; i <= 18; i++) { pull('Oov'); probe('Oov', 'O-PULL-' + i); w(600); }
  quit('Oov');

  // Nighter / Sneaker: the darkness and sneak shrink of the warning range stays (gen-r2b.js D-NIGHT / D-SNEAK)
  const cyc = (b, off, tag, i) => { pull(b, off); probe(b, tag + i + 'a'); w(2000); probe(b, tag + i + 'b'); w(900); };
  say('NIGHT-START'); c('time set 18000', 300); start('Nighter'); probe('Nighter', 'N-BASE'); arm('Nighter');
  for (let i = 1; i <= 8; i++) cyc('Nighter', '~6 ~ ~6', 'N-PULL-', i);
  pull('Nighter'); probe('Nighter', 'N-CONTROL'); w(3500); probe('Nighter', 'N-CONTROL-2'); w(3500); probe('Nighter', 'N-CONTROL-3');
  c('time set 6000', 300); quit('Nighter');
  say('SNEAK-START'); start('Sneaker'); probe('Sneaker', 'S-BASE'); arm('Sneaker');
  steps.push({ sneak: true, bot: 'Sneaker' });
  for (let i = 1; i <= 6; i++) cyc('Sneaker', '~7 ~ ~7', 'S-SNEAK-', i);
  steps.push({ sneak: false, bot: 'Sneaker' });
  for (let i = 1; i <= 3; i++) cyc('Sneaker', '~7 ~ ~7', 'S-STAND-', i);
  w(2000); probe('Sneaker', 'S-STAND-END'); quit('Sneaker');

  // Igner (R1): holds next to the cop through the deadline -> ignored -> cause line + star + short subtitle at once.
  // Then the wanted level is cleared and he stays armed next to cops: a next warning only from verdict+30
  say('IGNER-START'); start('Igner'); arm('Igner'); pull('Igner'); probe('Igner', 'I-NEAR'); given('Igner');
  for (let i = 1; i <= 6; i++) { pull('Igner'); w(500); }
  steps.push({ expectLog: 'WARNING Igner result=ignored', since: 'start', timeout: 10000 });
  steps.push({ expectChat: 'You were warned', since: 'start', timeout: 4000, bot: 'Igner' });
  say('I-VERDICT'); w(1500); steps.push({ chat: '/glw wanted', after: 600, bot: 'Igner' }); probe('Igner', 'I-STAR');
  say('I-CLEAR'); steps.push({ chat: '/glw wanted clear', after: 1500, bot: 'Igner' });
  for (let i = 1; i <= 14; i++) { pull('Igner'); probe('Igner', 'I-PULL-' + i); w(1500); }
  steps.push({ chat: '/glw wanted', after: 600, bot: 'Igner' }); quit('Igner');
  say('END'); steps.push({ quit: true, bot: 'Runner' });
  write('acc-brandish', steps);
}
