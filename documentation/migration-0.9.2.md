# Migrating a server from 0.9.1 to Gangland 0.9.2

[← Back to Documentation Index](./README.md)

Gangland 0.9.2 (the WS7 "gadget wave") does two things: it moves the jetpack out of Bartizan's wearable catalog and
makes it a Gangland-owned item, and it soft-couples Bartizan for `gangland-gadget` and `gangland-civilians` — both
now load and work on a server that doesn't run Bartizan at all, instead of refusing to boot
(`module.plugin.missing`). Nothing about cars, gangs, turf, mail or NPC shops changes structurally; this page is
short on purpose. For Bartizan's own side of the jetpack move (the `WearableCatalog.register` external-registration
hook, the `items/wearables.yml` `jetpack:` entry's removal), see Bartizan's own
[`documentation/migration.md`](../../bartizan-0.4.0/documentation/migration.md) §12.

## 1. Before you upgrade

1. Decide whether you still want Bartizan installed. As of 0.9.2, **only `cops-n-crooks`** requires it
   (`module.plugin.missing` if absent) — `gangland-gadget` and `gangland-civilians` both load fine without it now.
   If you don't run `cops-n-crooks`, Bartizan is now fully optional for every module you do run.
2. If you run Bartizan, upgrade it to **0.4.0** alongside Gangland 0.9.2 — Gangland's gadget module calls the new
   `WearableCatalog.register(String, Wearable)` api method (added in Bartizan 0.4.0) if you opt into keeping the
   jetpack's old armour values (§3 below); an older Bartizan does not have this method.

## 2. The jetpack is now a Gangland item, not a Bartizan wearable

The jetpack left Bartizan's `items/wearables.yml` entirely and is now defined in `gangland-gadget`'s own
`items/jetpacks.yml` (same data-folder convention as the module's existing `items/cars.yml`). Nothing changes for a
player holding one — equipping, flying and refuelling all work exactly as before.

**A jetpack chestplate given out before 0.9.2** still carries only Bartizan's old `wearable` NBT tag. The first time
it's equipped after the upgrade, `JetpackService.migrateLegacyJetpack` transparently re-stamps it with the new
`jetpack` identity tag — **its fuel (`FUEL_CURRENT`) and fuel ceiling (`FUEL_MAX`) both survive the re-stamp
completely unchanged**, even if `items/jetpacks.yml`'s `Max_Fuel:` has since been set to a different value than
what the item originally shipped with. No player-facing action is needed; this happens automatically, once, per
item, on next equip.

**Give command**: `/glw jetpack give <id> [amount]` (self-give — the same shape as `/glw car give <id> <amount>`).

## 3. Armour/trait loss without an opt-in, if you run Bartizan

The jetpack's old Bartizan-catalogued `Base_Damage_Reduction: 0.05` and `Traits: {REINFORCED: 1, LIGHTWEIGHT: 2}`
are **gone by default** after the move — a rehomed jetpack is a plain fuel/thrust chestplate with vanilla
`IRON_CHESTPLATE` protection only, whether or not Bartizan is installed.

If you want those values back **and run Bartizan 0.4.0+**: add an optional `Bartizan_Traits:` block to
`items/jetpacks.yml`'s jetpack entry. When Bartizan is installed, Gangland registers that definition into Bartizan's
wearable catalog through `WearableCatalog.register(...)` at boot, and Bartizan's existing damage-reduction path
applies to the jetpack exactly as it did before 0.9.2 — no Bartizan-side jetpack code exists any more; this is a
generic external-registration hook any soft-dependent plugin can use. Without Bartizan installed, `Bartizan_Traits:`
is simply never read (nothing to register it with).

## 4. Shop and loot-chest rows — `wearable:jetpack` stops resolving

Any shop entry, loot-chest table row, or sign that still references the item string `wearable:jetpack` (Bartizan's
old vocabulary prefix for it) **stops resolving after the upgrade** — Bartizan no longer knows about the jetpack as
a catalogued wearable, so that string can no longer be looked up. Re-add every such row using Gangland's own item
vocabulary instead: `jetpack:<id>` (the `<id>` matching whatever key you use in `items/jetpacks.yml`, e.g.
`jetpack:jetpack` for the stock default entry). This is a one-time manual re-add — nothing scans or rewrites
existing shop/loot-chest data automatically, the same way earlier item-vocabulary migrations in this repo have
always worked.

## 5. Module jars and `Host_Api`

Replace `gangland-gadget-<old>.jar` and `gangland-civilians-<old>.jar` with their 0.9.2 builds. Both now declare
`Host_Api: 1.1` (up from `1.0`) — this is the module API line, not a breaking change for anything else; every other
module keeps `Host_Api: 1.0` and keeps working unmodified. No jar needs to be deleted, and no new jar is required
(unlike the 0.9.0 upgrade, which added two brand-new module jars).

## 6. What a Bartizan-less server keeps, and what it loses, after 0.9.2

| Module | Without Bartizan (0.9.2+) | Without Bartizan (before 0.9.2) |
|---|---|---|
| `gangland-mail` | Loads, unaffected (never had a Bartizan edge) | Same |
| `gangland-npc-shops` | Loads, unaffected (never had a Bartizan edge) | Same |
| `gangland-gadget` | **Loads.** Cars: vanilla punch damage instead of melee-weapon damage. Jetpacks: fully functional — equip, fly, refuel, migrate legacy items, all unaffected (the jetpack never depended on a live Bartizan connection for its own mechanics, only for the optional armour-trait carry-over, §3). | Skipped entirely (`module.plugin.missing`) |
| `gangland-civilians` | **Loads.** Hostile civilian NPCs spawn unarmed (no weapon in hand) instead of skipping the module. | Skipped entirely (`module.plugin.missing`) |
| `gangland-turf` | **Loads** — its `Depends: [civilians]` is satisfied now that civilians loads without Bartizan; zero code changes to turf itself made this true. | Skipped (`module.dependency.missing`, since civilians itself was skipped) |
| `cops-n-crooks` | **Skipped** (`module.plugin.missing`) — the one module still hard-coupled; cop NPCs need a Bartizan-built weapon in hand and that coupling is unchanged by this wave. | Skipped (same reason) |

In short: a server that drops Bartizan entirely after upgrading to 0.9.2 goes from "three of six modules skipped"
to "one of six modules skipped" — only `cops-n-crooks` is lost, everything else (including the previously-blocked
`turf`) keeps working.

## 7. Every user-visible break, summarised

- `wearable:jetpack` item-vocabulary strings in shops/loot-chests no longer resolve — re-add as `jetpack:<id>` (§4).
- The jetpack's Bartizan-catalogued armour/traits are gone by default, whether or not you run Bartizan — opt back in
  with `Bartizan_Traits:` in `items/jetpacks.yml` if you run Bartizan 0.4.0+ (§3).
- `gangland-gadget`/`gangland-civilians` now require `Host_Api: 1.1` module jars (§5) — replace both, nothing else.
- If you run Bartizan at all, it must be 0.4.0+ for the `Bartizan_Traits:` opt-in to work (§1); an older Bartizan
  still runs fine otherwise, it just doesn't have the new registration hook.
- A legacy (pre-0.9.2) jetpack item keeps working with no owner action — it silently re-stamps itself on next equip,
  fuel and fuel ceiling both preserved (§2).

## See also

- [`documentation/module-loader.md`](./module-loader.md) — the full six-module topology, `Host_Api`, and the
  `Depends:`/`Plugins:` descriptor mechanism, updated for 0.9.2's soft edges.
- [`documentation/TESTING.md`](./TESTING.md) §4b — `BartizanBlindScan`/`BartizanReferenceScan`, the two tests that
  keep gadget and civilians Bartizan-safe going forward.
- Bartizan's own [`documentation/migration.md`](../../bartizan-0.4.0/documentation/migration.md) §12 and
  [`documentation/bartizan-api.md`](../../bartizan-0.4.0/documentation/bartizan-api.md)'s "External wearable
  registration" section — the Bartizan-side half of the jetpack move and the `WearableCatalog.register` mechanism.
