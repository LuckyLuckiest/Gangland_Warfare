#!/usr/bin/env node
// gen-r2b.js -> scen/brandish-r2b.json (attempt 2: arm far from cops so no given before the first pull; double probe; B-OOV waits for the given)
// (attempt 1 = gen-r2.js) : brandish setup verbatim (steps before join Drawer), then fresh-bot variants
// D-NIGHT, D-SNEAK, F-PERSIST, B-OOV, A-NATURAL (judge request 2026-10-26).
'use strict';
const fs = require('fs'), path = require('path');
const base = JSON.parse(fs.readFileSync(path.join(__dirname, 'scen/brandish.json'), 'utf8'));
const cut = base.findIndex(s => s.join === 'Drawer');
const steps = base.slice(0, cut);
const BOTS = ['Runner', 'Nighter', 'Sneaker', 'Faller', 'Oov', 'Natural'];
const EX = BOTS.map(b => 'name=!' + b).join(',');
const COPS = 'execute as @e if entity @s[type=minecraft:player,' + EX + '] run data get entity @s Pos';
const NEAR = 'type=!minecraft:item,type=!minecraft:text_display,type=!minecraft:armor_stand,type=!minecraft:interaction,type=!minecraft:item_display,type=!minecraft:villager,type=!minecraft:experience_orb,' + EX + ',distance=..60,sort=nearest,limit=1';
const c = (console, after = 200) => steps.push({ console, after });
const w = ms => steps.push({ wait: ms });
const say = l => c('say CNCMARK:BRANDISH-R2:' + l);
const pull = (b, off) => c('execute at @a[name=' + b + ',limit=1] as @e[' + NEAR + '] at @s run tp @a[name=' + b + ',limit=1] ' + off, 500);
const probe = (b, l) => { say(l); c(COPS, 400); c('data get entity ' + b + ' Pos', 200); };
function start(b) {
  steps.push({ join: b });
  for (const e of ['resistance', 'saturation']) c('effect give ' + b + ' minecraft:' + e + ' 1200 4 true', 300);
  c('gamemode survival ' + b, 200); c('clear ' + b, 300); c('tp ' + b + ' 100 -60 90', 400);
  w(6000);
}
const cyc = (b, off, tag, i) => { pull(b, off); probe(b, tag + i + 'a'); w(2000); probe(b, tag + i + 'b'); w(900); };
function arm(b) {
  steps.push({ hold: 1, bot: b });
  c('bartizan weapon give ' + b + ' pistol', 1000);
  steps.push({ hold: 0, bot: b });
}
const quit = b => { steps.push({ quit: true, bot: b }); w(2000); };


say('D-NIGHT-START'); c('time set 18000', 300);
start('Nighter'); probe('Nighter', 'N-BASE'); arm('Nighter');
for (let i = 1; i <= 8; i++) cyc('Nighter', '~6 ~ ~6', 'N-PULL-', i);
pull('Nighter', '~1.5 ~ ~1.5'); probe('Nighter', 'N-CONTROL'); w(3500); probe('Nighter', 'N-CONTROL-2'); w(3500); probe('Nighter', 'N-CONTROL-3');
c('time set 6000', 300); quit('Nighter');

say('D-SNEAK-START');
start('Sneaker'); probe('Sneaker', 'S-BASE'); arm('Sneaker');
steps.push({ sneak: true, bot: 'Sneaker' });
for (let i = 1; i <= 6; i++) cyc('Sneaker', '~7 ~ ~7', 'S-SNEAK-', i);
steps.push({ sneak: false, bot: 'Sneaker' });
for (let i = 1; i <= 3; i++) cyc('Sneaker', '~7 ~ ~7', 'S-STAND-', i);
w(2000); probe('Sneaker', 'S-STAND-END'); quit('Sneaker');

say('B-OOV-START');
start('Oov'); arm('Oov');
pull('Oov', '~1.5 ~ ~1.5'); probe('Oov', 'O-NEAR');
steps.push({ expectLog: 'WARNING Oov result=given', timeout: 15000 });
c('execute as Oov at @s run tp @s ~ ~ ~35', 300);
probe('Oov', 'O-FAR-1'); w(3500); probe('Oov', 'O-FAR-2'); w(3500); probe('Oov', 'O-FAR-3');
steps.push({ hold: 1, bot: 'Oov' }); w(2000); quit('Oov');
say('END');
steps.push({ quit: true, bot: 'Runner' });
fs.writeFileSync(path.join(__dirname, 'scen/brandish-r2b.json'), JSON.stringify(steps, null, 1));
console.log(steps.length + ' steps');
