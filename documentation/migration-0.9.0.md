# Migrating a server from 0.8.x to Gangland 0.9.0

[← Back to Documentation Index](./README.md)

Gangland 0.9.0 drops the weapon system entirely — no bridge module. This page is the **Gangland-side** upgrade
guide for a server owner going from "Gangland 0.8.x with the weapon module" to "Gangland 0.9.0 + Bartizan". For
what actually moved and how to install Bartizan itself, see Bartizan's own
[`documentation/migration.md`](../../Bartizan/documentation/migration.md) (installing Bartizan, the
`items/wearables.yml` `Jetpack:` → `Extra_Tags:` rename, permission node rename, settings, commands) — this page
does not repeat that content, only the half of the story that happens on Gangland's side.

## 1. Before you upgrade

1. Read [`bartizan-integration.md`](./bartizan-integration.md) — decide whether you want Bartizan installed at all.
   Without it, `gangland-civilians`, `cops-n-crooks` and `gangland-gadget` all skip loading
   (`module.plugin.missing`); only `gangland-mail`, `gangland-npc-shops` and (if civilians is present) `gangland-turf`
   are reachable.
2. If you run MySQL and want weapon data preserved, note your `weapon` table's row count before upgrading —
   Bartizan's MySQL import is a manual one-statement step (§4 below), not automatic.

## 2. Install Keystone 1.9.0 and (optionally) Bartizan

1. Replace `Keystone-<old>.jar` with `Keystone-1.9.0.jar` — Gangland 0.9.0 requires it (`keystone-item`,
   `keystone-npc` are new modules it ships).
2. If you want weapons, ammo, wearables, cop/civilian weapon behavior, or car/jetpack Bartizan interactions:
   install `Bartizan-0.1.0.jar` per its own migration guide (linked above). If you don't, skip this step — the
   server still boots, with the degradations `bartizan-integration.md` lists.
3. Citizens is now **optional** for Gangland itself (`plugin.yml` moved it from `depend:` to `softdepend:`, and
   `Gangland.java`'s dependency check now also verifies the plugin is *enabled*, not merely present — a disabled
   Citizens now logs as absent rather than linked). Without it, every NPC-owning module reports
   `npc.citizens.missing` once and simply spawns no NPCs; the module itself still loads and everything else in it
   still works (turf capture, cop detainment logic that doesn't need a live NPC, trader/banker commands, etc.).

## 3. Module jars — six now, not five

Replace `plugins/Gangland_Warfare-<old>.jar` with `Gangland_Warfare-0.9.0.jar`, and in
`plugins/Gangland_Warfare/modules/`:

- **Delete `gangland-weapon-<old>.jar` by hand.** The module loader does not delete it for you — left in place, it
  is skipped every boot with a `module.host.incompatible` fault (its `Host_Api` no longer matches) and logs a fault
  line forever. It has no 0.9.0 replacement; weapons are Bartizan's now.
- Replace your existing `gangland-mail`, `cops-n-crooks`, `gangland-gadget` and `gangland-turf` jars with their
  0.9.0 builds.
- Add two new jars if you want their features: `gangland-civilians-0.9.0.jar` (civilian NPCs, split out of
  cops-n-crooks) and `gangland-npc-shops-0.9.0.jar` (trader/banker NPC shops, also split out of cops-n-crooks).
  **Both are new dependencies of `cops-n-crooks`** (`Depends: [turf, civilians]`) — cops-n-crooks will not load
  without both present. `gangland-turf` also gained a `Depends: [civilians]`.

See `documentation/module-loader.md`'s jar-layout diagram for the full six-module picture, and
`brainstorming/bartizan-split-2026-09-08/gangland-0.9.0.md`'s group P gate G3/G5 for the exact expected jar names
and descriptor keys if you are verifying a build rather than a production deploy.

## 4. Database — the `weapon` table

Gangland's own database **stops writing to** the `weapon` table on upgrade (nothing in 0.9.0 references it any
more) — the table itself is not dropped, so it stays present and readable for Bartizan's import.

- **SQLite:** if Bartizan is installed and configured for SQLite (its default), it reads Gangland's
  `plugins/Gangland_Warfare/database/gangland.db` `weapon` table automatically on its own first boot, imports every
  row, and writes `plugins/Bartizan/.weapon-import-done` so it never re-reads on subsequent boots. Delete that
  marker file to force a re-import.
- **MySQL:** Bartizan does **not** auto-import from MySQL. Run the one-statement import documented in Bartizan's
  own migration guide (`INSERT INTO bartizan.weapon (uuid, type) SELECT uuid, type FROM gangland.weapon ON
  DUPLICATE KEY UPDATE type = VALUES(type);`), then drop `gangland.weapon` once the row counts match.
- **No Bartizan installed:** the `weapon` table is simply dead weight — safe to leave, or drop it yourself once
  you've confirmed you don't plan to install Bartizan later.

## 5. Placed signs — the six legacy weapon/ammo/wearable sign types

A `[WEAPON-BUY]`, `[WEAPON-SELL]`, `[AMMO-BUY]`, `[AMMO-SELL]`, `[WEARABLE-BUY]` or `[WEARABLE-SELL]` sign placed
before 0.9.0 **keeps working with no action from you** — Gangland 0.9.0 no longer has these as native sign types
(the generic `[ITEM-BUY]`/`[ITEM-SELL]` types replace them, resolving any registered item definition, not only
weapons), but `SignManager.setupSigns()` registers all six old header keys as aliases at every boot, driven by a
new `settings.yml` block:

```yaml
Signs:
   Legacy_Aliases:
      Weapon_Buy: "item-buy:weapon"
      Weapon_Sell: "item-sell:weapon"
      Ammo_Buy: "item-buy:ammo"
      Ammo_Sell: "item-sell:ammo"
      Wearable_Buy: "item-buy:wearable"
      Wearable_Sell: "item-sell:wearable"
```

Each value is `"<generic sign key>:<line-3 item-definition prefix>"` — a sign whose header still reads (for
example) `WEAPON-BUY` is transparently handled as an `item-buy` sign whose content line resolves through the
`weapon:` item-definition prefix, with no change to the sign block in the world and no owner action required. This
is a **pure alias registration at read/interact time** — no world scan runs on boot, no sign text is rewritten on
disk or on the block. The item itself still needs Bartizan's `weapon:`/`ammo:`/`wearable:` vocabulary to actually
resolve (§4 of `bartizan-integration.md`) — without Bartizan, the sign still recognises the header but the trade
fails to find the item, same as any other unresolvable item definition. You may delete the `Legacy_Aliases` block
entirely once every server-owned sign has been re-placed as a native `[ITEM-BUY]`/`[ITEM-SELL]` sign — an
unrecognised header key in a configured alias value is logged and skipped, never a boot failure.

## 6. Other `settings.yml` changes

- **`Block_Regeneration:`** removed — it was weapon-explosion-specific and had no other consumer.
- **`Cops.Starting_Ammo_Magazines`** removed — Bartizan owns the NPC's magazine capacity now; the setting had no
  remaining consumer even before this fix (dead since the cops NPC-base swap earlier in this stream).
- **`Signs.Legacy_Aliases`** added — see §5 above.

## 7. `message/message_en.yml` changes

The `Weapons:` message block, `Death.Weapon`, and `Commands.Weapons` were removed — there is no weapon-specific
messaging left in the core. Death messages still resolve through a generic `Death.Global` list
(`%killer%`/`%victim%`, no `%item%` placeholder) on the downed-player kill path; a killer's specific weapon name on
that path is a known gap tracked in the bug docket, not fixed by this stream (Bartizan would need to publish it).

## 8. Commands that moved

Every weapon-related `/glw` command is gone; use Bartizan's `/bartizan` (aliases `/btz`, `/weapon`) instead:

| Old (`/glw`, Gangland ≤ 0.8.x) | New (Bartizan ≥ 0.1.0) |
|---|---|
| `/glw weapon …` | `/bartizan weapon …` |
| `/glw ammo …` | `/bartizan ammo …` |
| `/glw item wearable …` | `/bartizan wearable …` |
| `/glw debug weapon …` | `/bartizan debug …` |

The core `/glw` command count is unchanged (149 entries) — the weapon module's 12 command keys left with the
module and never touched the core's own `commands.json`.

## 9. Permissions

Wearable permission nodes changed prefix because they now belong to a standalone plugin, not a Gangland feature:
`gangland.wearables.<key>` → `bartizan.wearables.<key>` (see Bartizan's own migration guide §5 for the full list —
this is not a Gangland-side change, listed here only so it isn't missed).

## 10. Every user-visible break, summarised

- `/glw weapon`, `/glw ammo`, `/glw item wearable`, `/glw debug weapon` no longer exist (§8).
- `weapon/*.yml`, `items/ammunition.yml`, `items/wearables.yml` no longer live under
  `plugins/Gangland_Warfare/` — they move to `plugins/Bartizan/` (regenerated fresh, or copied per Bartizan's
  migration guide if customised).
- `gangland-weapon-<old>.jar` must be deleted by hand from `modules/` (§3).
- Citizens is now optional (§2) — a server that never had it installed sees no change; a server that had it as a
  hard dependency now boots without it, with NPCs simply absent and one fault line per NPC-owning module.
- Two new module jars (`gangland-civilians`, `gangland-npc-shops`) are required for `cops-n-crooks` and
  `gangland-turf` to load at all (§3) — a server that only drops in the four "old" module jars will find
  cops-n-crooks and turf both skipped with `module.dependency.missing`.
- Placed legacy weapon/ammo/wearable signs keep working automatically (§5); the item itself still needs Bartizan.
- `Block_Regeneration` and `Cops.Starting_Ammo_Magazines` settings keys are gone (§6); a customised value for
  either is simply ignored on upgrade, not an error.
- Weapon-specific messages are gone from `message_en.yml` (§7); death messages fall back to a generic global list.
- Wearable permission nodes changed prefix (§9).
- The `weapon` database table is no longer written by Gangland; import it into Bartizan if you want the data (§4).

## See also

- [`bartizan-integration.md`](./bartizan-integration.md) — what degrades without Bartizan, and why.
- [`documentation/module-loader.md`](./module-loader.md) — the full six-module topology and the `Depends:`/
  `Plugins:` descriptor mechanism.
- Bartizan's own [`documentation/migration.md`](../../Bartizan/documentation/migration.md) — the Bartizan-side
  install, YAML, database, permission and command migration.
