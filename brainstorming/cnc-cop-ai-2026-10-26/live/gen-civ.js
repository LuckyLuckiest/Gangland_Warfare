#!/usr/bin/env node
// gen-civ.js -> scen/civ-boot.json, scen/civ-911.json : 0.21.1 civilian witness / 911 acceptance (CIVILIAN-911-DESIGN.md,
// owner answers Q1-Q10). Recorders like gen-acc.js: hard expects guard setup only (spawn reply, flee ended before the
// cooldown repeat), so rc=1 means setup broke; the A..E verdicts are read from server.log / chat.txt (civ-README.md).
//
// FACING. A teleport's yaw does not hold: the civilian AI ticks every server tick (CivilianService's RepeatingTimer gets
// delay=AI_Tick_Rate, period=0, and Bukkit runs period 0 as 1), so the idle look-around re-aims every 15-35 ticks. The
// idle look always picks the NEAREST PLAYER within a 12-block box first, so facing is set by who stands near W:
//   front  : W 8 blocks south of the bot (X+.5, 8.5): the bot is the nearest player, W keeps facing it (yaw ~180).
//   behind : W 14 blocks south of the bot (X+.5, 14.5), bot Decoy 6 blocks further south (X+.5, 20.5): Decoy is the only
//            player in W's box, W keeps facing it (yaw ~0) and the bot is at its back.
// Every row runs at its own X (600 blocks apart, so no row's cops or civilians reach another) with its own bot at
// (X+.5, .5) and the victim V at (X+2.5, .5). The crime is always Kill_Civilian by fist (Strength 100 = one hit): 100 heat
// = star 1 when the call lands; the 30-heat Assault_Civilian would never star. Row C fires a Bartizan pistol into the
// air first, so the kill is loud (Loud_Shot_Seconds 3).
'use strict';
const fs = require('fs'), path = require('path');
const SC = path.join(__dirname, 'scen');
const write = (n, s) => { fs.writeFileSync(path.join(SC, n + '.json'), JSON.stringify(s, null, 1)); console.log(n, s.length, 'steps'); };

const BOTS = ['Silent', 'Backer', 'Loud', 'Grabber', 'Killer', 'Placer', 'Decoy'];
const FRONT = 8.5, BACK = 14.5, DECOY = 20.5;
const C = (console, after = 300) => ({ console, after });
const W = ms => ({ wait: ms });
const mark = l => C('say CNCMARK:CIV:' + l, 200);
const COPS = C('execute as @e if entity @s[type=minecraft:player,' + BOTS.map(b => 'name=!' + b).join(',') + '] run data get entity @s Pos', 400);
const civ = (tag, what = 'Rotation') => C(`data get entity @e[tag=${tag},limit=1] ${what}`, 150);
const probeCiv = tag => [civ(tag, 'Pos'), civ(tag), civ(tag, 'equipment'), civ(tag, 'Glowing')];
const wanted = bot => ({ chat: '/glw wanted', after: 700, bot });
const world = [C('gamerule spawn_mobs false'), C('gamerule spawn_monsters false'), C('gamerule spawn_wandering_traders false'),
  C('gamerule advance_time false'), C('time set 6000'), C('weather clear')];
const join = (bot, x, z = 0.5) => [{ join: bot },
  ...['resistance 1200 4', 'saturation 1200 4', 'strength 1200 100'].map(e => C(`effect give ${bot} minecraft:${e} true`)),
  C(`gamemode survival ${bot}`), C(`clear ${bot}`), C(`tp ${bot} ${x + 0.5} -60 ${z} 0 0`, 500), W(3000)];
// the tp also sets the yaw (behind row 0, else 180), so facing stays right if the AI tick is ever fixed to 1/s and
// the idle look stops re-aiming every second; today the nearest-player look re-asserts it
const place = (tag, x, z) => C(`tp @e[tag=${tag},limit=1] ${x} -60 ${z} ${z === BACK ? 0 : 180} 0`, 300);
// spawn one pedestrian near the bot, tag the only untagged villager, park it at (x, z)
const spawn = (bot, tag, x, z) => [
  { chat: '/glw civilian spawn pedestrian', after: 1000, bot },
  { expectChat: 'Civilian pedestrian spawned', timeout: 5000, bot },
  C(`execute at ${bot} run tag @e[type=minecraft:villager,tag=!civ,sort=nearest,limit=1,distance=..80] add ${tag}`),
  C(`tag @e[tag=${tag}] add civ`), place(tag, x, z)];
const kill = (bot, x) => [{ lookAt: [x + 2.5, -58.6, 0.5], bot }, { attack: { near: [x + 2.5, -60, 0.5], type: 'villager' }, after: 300, bot }];
// the yaw W has right before the crime (what the view cone sees), then the crime marker
const pre = (row, bot, wTag) => [mark(row + '-PRE'), COPS, ...probeCiv(wTag), wanted(bot), W(1500), civ(wTag), mark(row + '-CRIME')];
// the recorder probes of a call: notice (+0.3 s), dialling (+2.5 s), landed (+11 s; Call_Seconds 8)
const callProbes = (row, bot, wTag) => [mark(row + '-NOTICE'), ...probeCiv(wTag), W(1800),
  mark(row + '-DIAL'), ...probeCiv(wTag), W(8000),
  mark(row + '-LANDED'), ...probeCiv(wTag), COPS, wanted(bot), W(2000)];
const end = bot => [{ chat: '/glw wanted clear', after: 1500, bot }, { quit: true, bot }, W(2000)];

// ---- civ-boot: boot check + placement dry run (front and behind facing, pistol shot) ---------------------------
write('civ-boot', [W(8000), C('glw module list', 2000), ...['Keystone', 'Gangland_Warfare', 'Bartizan', 'Citizens']
  .map(p => C('version ' + p, 1500)), ...world, ...join('Placer', 0),
  ...spawn('Placer', 'P_F', 0.5, FRONT), ...spawn('Placer', 'P_B', 30.5, BACK), ...join('Decoy', 30, DECOY), W(3000),
  mark('BOOT-PLACED'), ...probeCiv('P_F'), ...probeCiv('P_B'), COPS, { chat: '/glw civilian list', after: 800, bot: 'Placer' },
  W(6000), mark('BOOT-PLACED-6S'), ...probeCiv('P_F'), ...probeCiv('P_B'),
  { hold: 1, bot: 'Placer' }, C('bartizan weapon give Placer pistol', 1000), { hold: 0, bot: 'Placer' },
  C('data get entity Placer SelectedItem', 200), { lookAt: [0.5, -40, 0.5], bot: 'Placer' }, { useItem: true, after: 800, bot: 'Placer' },
  mark('BOOT-SHOT'), C('data get entity Placer SelectedItem', 200), { quit: true, bot: 'Placer' }, { quit: true, bot: 'Decoy' }, W(2000)]);

// ---- civ-911 ---------------------------------------------------------------------------------------------------
const s = [...world];

// A (X=0): silent kill in front of W -> Witness_Start, look-at then turn-away, countdown bar, Witness_Landed,
// [DISPATCH] Caller_911 with no cop in the world yet (T-345), star 1 + "Killing a civilian (reported by a witness)"
{
  const X = 0, b = 'Silent';
  s.push(mark('A-START'), ...join(b, X), ...spawn(b, 'W_A', X + 0.5, FRONT), ...spawn(b, 'V_A', X + 2.5, 0.5), W(3000),
    ...pre('A', b, 'W_A'), ...kill(b, X), ...callProbes('A', b, 'W_A'), ...end(b));
}

// B (X=600): silent kill with W's back to the bot (W faces Decoy) -> no call, no star
{
  const X = 600, b = 'Backer';
  s.push(mark('B-START'), ...join(b, X), ...join('Decoy', X, DECOY), ...spawn(b, 'W_B', X + 0.5, BACK),
    ...spawn(b, 'V_B', X + 2.5, 0.5), W(3000), ...pre('B', b, 'W_B'), ...kill(b, X),
    mark('B-NOTICE'), ...probeCiv('W_B'), W(10000), mark('B-AFTER'), ...probeCiv('W_B'), COPS, wanted(b), W(1000), ...end(b));
}

// C (X=1200): same geometry as B, but a pistol shot into the air just before the kill -> W turns to the noise and calls
{
  const X = 1200, b = 'Loud';
  s.push(mark('C-START'), ...join(b, X), C(`tp Decoy ${X + 0.5} -60 ${DECOY} 0 0`, 500), W(3000),
    ...spawn(b, 'W_C', X + 0.5, BACK), ...spawn(b, 'V_C', X + 2.5, 0.5),
    { hold: 1, bot: b }, C(`bartizan weapon give ${b} pistol`, 1000), { hold: 0, bot: b }, W(3000),
    C(`data get entity ${b} SelectedItem`, 200), ...pre('C', b, 'W_C').slice(0, -1),
    mark('C-SHOT'), { lookAt: [X + 0.5, -40, 0.5], bot: b }, { useItem: true, after: 300, bot: b },
    C(`data get entity ${b} SelectedItem`, 150), { hold: 1, bot: b },
    mark('C-CRIME'), ...kill(b, X), ...callProbes('C', b, 'W_C'), ...end(b), { quit: true, bot: 'Decoy' }, W(1000));
}

// D (X=1800): the call starts, the unarmed bot right-clicks W from 2 blocks -> Witness_Scared + "Call stopped.", no
// star. W flees and settles; then a kill in front of W inside Scared_Cooldown_Seconds (60) -> W does not call. Control:
// the same again once 60 s have passed since the stop -> W calls again (the cooldown, not a broken W, held it back).
{
  const X = 1800, b = 'Grabber';
  s.push(mark('D-START'), ...join(b, X), ...spawn(b, 'W_D', X + 0.5, FRONT), ...spawn(b, 'V_D', X + 2.5, 0.5),
    ...spawn(b, 'V2_D', X + 40.5, 0.5), ...spawn(b, 'V3_D', X + 40.5, 4.5), W(3000),
    ...pre('D', b, 'W_D'), ...kill(b, X),
    mark('D-NOTICE'), ...probeCiv('W_D'), W(1800), mark('D-DIAL'), ...probeCiv('W_D'),
    C(`tp ${b} ${X + 0.5} -60 ${FRONT - 2} 0 0`, 600), { hold: 1, bot: b }, { lookAt: [X + 0.5, -58.4, FRONT], bot: b },
    mark('D-GRAB'), { activateEntity: { near: [X + 0.5, -60, FRONT], type: 'villager' }, after: 800, bot: b },
    mark('D-STOPPED'), ...probeCiv('W_D'), wanted(b),
    { expectLog: 'Transitioning civilian .* from FLEEING state to IDLE', timeout: 30000 },
    mark('D-SETTLED'), C(`tp ${b} ${X + 0.5} -60 0.5 0 0`, 300), place('W_D', X + 0.5, FRONT), place('V2_D', X + 2.5, 0.5),
    W(2500), ...pre('D-REPEAT', b, 'W_D'), ...kill(b, X), W(400), mark('D-REPEAT-NOTICE'), ...probeCiv('W_D'), W(10000),
    mark('D-REPEAT-AFTER'), ...probeCiv('W_D'), COPS, wanted(b),
    // control: wait out the rest of the 60 s cooldown (D-GRAB to the control kill is >= ~73 s even on the fastest flee)
    W(50000), place('W_D', X + 0.5, FRONT), place('V3_D', X + 2.5, 0.5), W(2500),
    ...pre('D-CONTROL', b, 'W_D'), ...kill(b, X), ...callProbes('D-CONTROL', b, 'W_D'), ...end(b));
}

// E (X=2400): Kill_Civilian with no civilian within Witness.Range (24) -> no star; then W placed in front and a second
// kill -> the call flow and star 1
{
  const X = 2400, b = 'Killer';
  s.push(mark('E-START'), ...join(b, X), ...spawn(b, 'W_E', X + 40.5, 14.5), ...spawn(b, 'V_E', X + 2.5, 0.5),
    ...spawn(b, 'V2_E', X + 40.5, 0.5), W(3000),
    ...pre('E1', b, 'W_E'), ...kill(b, X), W(10000), mark('E1-AFTER'), ...probeCiv('W_E'), COPS, wanted(b),
    place('W_E', X + 0.5, FRONT), place('V2_E', X + 2.5, 0.5), W(2500),
    ...pre('E2', b, 'W_E'), ...kill(b, X), ...callProbes('E2', b, 'W_E'), ...end(b));
}
s.push(mark('END'), W(1000));
write('civ-911', s);
