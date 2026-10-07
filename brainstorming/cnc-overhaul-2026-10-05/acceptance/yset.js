#!/usr/bin/env node
// yset.js <file.yml> <Dotted.Path> <value> - line-based edit of a 3-space-indented block-style YAML file (house style).
// Sets the key when it exists under the parent chain, else inserts it (and missing parents) as the first child of the
// deepest existing parent. Every other line is kept byte for byte. Exit 1 when the top-level key is missing.
'use strict';
const fs = require('fs');
const [file, dotted, value] = process.argv.slice(2);
if (!file || !dotted || value === undefined) { console.error('usage: yset.js <file> <A.B.C> <value>'); process.exit(2); }
const keys = dotted.split('.');
const text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const lines = text.split(/\r?\n/);
let from = 0, to = lines.length; // window = children of the last matched parent
for (let depth = 0; depth < keys.length; depth++) {
  const indent = ' '.repeat(3 * depth), head = indent + keys[depth] + ':';
  let hit = -1;
  for (let i = from; i < to; i++) {
    if (lines[i].startsWith(head)) { hit = i; break; }
  }
  if (hit < 0) {
    if (depth === 0) { console.error('top-level key not found: ' + keys[0]); process.exit(1); }
    const add = keys.slice(depth).map((k, j, a) => ' '.repeat(3 * (depth + j)) + k + ':' + (j === a.length - 1 ? ' ' + value : ''));
    lines.splice(from, 0, ...add);
    fs.writeFileSync(file, lines.join(eol)); console.log('inserted ' + dotted + ': ' + value); process.exit(0);
  }
  if (depth === keys.length - 1) {
    lines[hit] = head + ' ' + value;
    fs.writeFileSync(file, lines.join(eol)); console.log('set ' + dotted + ': ' + value); process.exit(0);
  }
  from = hit + 1; to = lines.length;
  for (let i = from; i < lines.length; i++) { // block ends at the next real line not indented deeper than this key
    const t = lines[i].trim();
    if (t !== '' && !t.startsWith('#') && !lines[i].startsWith(indent + ' ')) { to = i; break; }
  }
}
