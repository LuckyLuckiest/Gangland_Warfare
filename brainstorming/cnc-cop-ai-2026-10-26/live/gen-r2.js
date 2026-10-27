#!/usr/bin/env node
// gen-r2.js -> scen/brandish-r2.json : brandish setup verbatim (steps before join Drawer), then fresh-bot variants
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
  c('gamemode survival ' + b, 200); c('clear ' + b, 300); c('tp ' + b + ' 100 -60 50', 400);
  w(6000);
}
function arm(b) {
  steps.push({ hold: 1, bot: b });
  c('bartizan weapon give ' + b + ' pistol', 1000);
  steps.push({ hold: 0, bot: b });
}
const quit = b => { steps.push({ quit: true, bot: b }); w(2000); };

// 1 D-NIGHT
say('D-NIGHT-START'); c('time set 18000', 300);
start('Nighter'); probe('Nighter', 'N-BASE'); arm('Nighter');
for (let i = 1; i <= 8; i++) { pull('Nighter', '~6 ~ ~6'); probe('Nighter', 'N-PULL-' + i); w(3300); }
pull('Nighter', '~1.5 ~ ~1.5'); probe('Nighter', 'N-CONTROL'); w(3500); probe('Nighter', 'N-CONTROL-2'); w(3500); probe('Nighter', 'N-CONTROL-3');
c('time set 6000', 300); quit('Nighter');

// 2 D-SNEAK (daylight)
say('D-SNEAK-START');
start('Sneaker'); probe('Sneaker', 'S-BASE'); arm('Sneaker');
steps.push({ sneak: true, bot: 'Sneaker' });
for (let i = 1; i <= 6; i++) { pull('Sneaker', '~7 ~ ~7'); probe('Sneaker', 'S-SNEAK-' + i); w(3300); }
steps.push({ sneak: false, bot: 'Sneaker' });
for (let i = 1; i <= 2; i++) { pull('Sneaker', '~7 ~ ~7'); probe('Sneaker', 'S-STAND-' + i); w(3300); }
w(2000); probe('Sneaker', 'S-STAND-END'); quit('Sneaker');

// 3 F-PERSIST
say('F-PERSIST-START');
start('Faller'); arm('Faller');
pull('Faller', '~1.5 ~ ~1.5'); probe('Faller', 'F-DRAW');
for (let i = 1; i <= 3; i++) { w(3000); probe('Faller', 'F-HOLD-' + i); }
steps.push({ hold: 1, bot: 'Faller' }); say('F-AWAY');
steps.push({ chat: '/glw wanted', after: 500, bot: 'Faller' });
for (let i = 1; i <= 15; i++) {
  pull('Faller', '~1.5 ~ ~1.5'); probe('Faller', 'F-PULL-' + i);
  if (i % 3 === 0) steps.push({ chat: '/glw wanted', after: 500, bot: 'Faller' });
  w(8800);
}
c('item replace entity Faller armor.head with minecraft:iron_helmet', 300); say('F-HELMET');
pull('Faller', '~1.5 ~ ~1.5'); probe('Faller', 'F-HELMET-PULL'); w(5000); probe('Faller', 'F-HELMET-END');
c('item replace entity Faller armor.chest with minecraft:leather_chestplate', 300); say('F-CHEST');
pull('Faller', '~1.5 ~ ~1.5'); probe('Faller', 'F-CHEST-PULL'); w(5000); probe('Faller', 'F-CHEST-END');
steps.push({ chat: '/glw wanted', after: 500, bot: 'Faller' });
quit('Faller');

// 4 B-OOV
say('B-OOV-START');
start('Oov'); arm('Oov'); steps.pop(); // hold 0 comes after the pull so the draw happens next to the cop
steps.push({ hold: 1, bot: 'Oov' });
pull('Oov', '~1.5 ~ ~1.5'); probe('Oov', 'O-NEAR');
steps.push({ hold: 0, bot: 'Oov' });
c('execute as Oov at @s run tp @s ~ ~ ~35', 300);
probe('Oov', 'O-FAR-1'); w(3500); probe('Oov', 'O-FAR-2'); w(3500); probe('Oov', 'O-FAR-3');
steps.push({ hold: 1, bot: 'Oov' }); w(2000); quit('Oov');

// 5 A-NATURAL
say('A-NATURAL-START');
start('Natural'); probe('Natural', 'A-BASE'); steps.push({ hold: 1, bot: 'Natural' });
c('bartizan weapon give Natural pistol', 1000);
pull('Natural', '~1.5 ~ ~1.5');
steps.push({ hold: 0, bot: 'Natural' });
for (let i = 1; i <= 48; i++) { probe('Natural', 'A-T' + i); w(1200); }
steps.push({ chat: '/glw wanted', after: 500, bot: 'Natural' });
say('END'); quit('Natural');
steps.push({ quit: true, bot: 'Runner' });
fs.writeFileSync(path.join(__dirname, 'scen/brandish-r2.json'), JSON.stringify(steps, null, 1));
console.log(steps.length + ' steps');
