# WS7 gadget wave — Manual Test Checklist (G6)

[Test Index](../../../../../documentation/tests/README.md) | [Migration note](../../../../../documentation/migration-0.9.2.md) | [Module loader](../../../../../documentation/module-loader.md)

---

## Overview

The WS7 gadget wave (Gangland 0.9.2) rehomed the jetpack out of Bartizan's wearable catalog into `gangland-gadget`
as its own item (gates G2/G3), then soft-coupled Bartizan for `gangland-gadget` (G5) and `gangland-civilians` (G5b)
so both load and work on a Bartizan-less server instead of refusing to boot. Every gate was verified by unit tests
and, from G4 onward, by the console-only smoke harness (`brainstorming/bartizan-split-2026-09-08/smoke/`) — but a
console session has no in-game player, so it can never equip an item, punch an entity, or observe a spawned NPC's
held weapon. This checklist is exactly the four gaps every gate's own report flagged as "needs a client," rolled up
into one document now that the wave is closing.

**Modules involved:** `gangland-gadget`, `gangland-civilians`, `gangland-core` (test-support only, not runtime).
**Test server:** `E:\Documents\Minecraft\Test Server` (the same one the smoke harness drives) or any Spigot/Paper
1.16+ server running Gangland 0.9.2 + Keystone 1.9.2+.

---

## Pre-Conditions

- [ ] Server is running Gangland 0.9.2, `gangland-gadget` and `gangland-civilians` at their 0.9.2 builds
  (`Host_Api: 1.1` both).
- [ ] At least one op player online with a real client (not console).
- [ ] Two server configurations available to test against: **with Bartizan installed** (0.4.0+) and **without
  Bartizan at all** (jar removed from `plugins/`) — items 1–3 below each need the Bartizan-less configuration;
  item 4 needs Bartizan present.
- [ ] For item 3 (civilian spawn), Citizens must be installed and enabled — civilian NPCs do not spawn without it
  regardless of Bartizan (unrelated soft dependency, `npc.citizens.missing`).
- [ ] `items/jetpacks.yml`'s stock jetpack entry present at its default id (or note whatever id you use below).

---

## 1. Punch a car without Bartizan

Confirms `CarDamageListener.onVehicleDamage` applies the vanilla punch-damage fallback (`Math.max(1, (int)
Math.ceil(event.getDamage()))`) when Bartizan is absent — the live-server counterpart to the unit-level
`CarMeleeWeaponLookupGuardTest` and the boot-only smoke row `car-damage-with-bartizan` (which only ever ran WITH
Bartizan present — this is its Bartizan-absent, live-punch complement).

**Setup**: Bartizan-less server. `/glw car give sports_car 1`, place the car in the world.

- [ ] Step 1 — Empty-handed, punch the placed car once → durability drops by a small, consistent vanilla-punch
  amount (not a melee-weapon-scaled amount — there is no weapon lookup to scale it, since Bartizan is absent).
- [ ] Step 2 — Repeat holding an arbitrary item (e.g. a stick) that would be a "weapon" if Bartizan resolved it →
  same vanilla-punch damage as step 1, confirming the code path never attempts a Bartizan weapon lookup at all when
  Bartizan is unavailable (no exception, no stack trace in the console either).
- [ ] Step 3 — No `NoClassDefFoundError`/`ClassCastException`/exception of any kind appears in console for either
  punch.

---

## 2. Fly a jetpack without Bartizan

Confirms the rehomed, gadget-owned jetpack (`items/jetpacks.yml`, `/glw jetpack give`) equips and flies
independently of Bartizan's presence — the live-server counterpart to the boot-only `jetpack-give`/
`jetpack-migrated` smoke rows, which could only confirm the module boots and the command resolves, never that the
item actually flies.

**Setup**: Bartizan-less server (or with Bartizan present — flight itself should not differ either way; run once
Bartizan-less to close this specific gap).

- [ ] Step 1 — `/glw jetpack give <id> 1` → item appears in inventory with the expected display name/lore.
- [ ] Step 2 — Equip it (right-click or shift-click into the chestplate slot) → equips cleanly, no denial message
  (assuming the permission node is held).
- [ ] Step 3 — Activate flight (per the jetpack's configured activation — sneak+jump or whatever `items/jetpacks.yml`
  configures) → player actually flies, fuel visibly depletes (boss bar or action bar, per config).
- [ ] Step 4 — Fuel reaches zero → flight stops as configured (fall, or grounded), no exception.
- [ ] Step 5 (migration path) — hand-craft or otherwise obtain a **pre-0.9.2** jetpack item (only the legacy
  Bartizan `wearable` NBT tag, no `jetpack` tag yet) with a known non-default fuel value, equip it → confirms
  `JetpackService.migrateLegacyJetpack` re-stamps it with the `jetpack` identity tag AND that the fuel value
  observed in-game (boss bar/action bar) matches what the item carried before the equip, not the catalogue's
  current `Max_Fuel:` default. Equip again after the re-stamp (module reload not required) → no second migration
  attempt, behaves like a normal jetpack from here on.

---

## 3. A hostile civilian spawn without Bartizan

Confirms `BartizanNpcWeapons.create`/`buildItem`'s live guard (`Settings.isBartizanAvailable()`, WS7 G5b fix round
1 review C2) — this is the ONE item on this checklist that automated console smoke categorically cannot reach at
all, confirmed by direct source read: every civilian-spawn command
(`/glw civilian spawn`, `/glw civilian spawngroup`) requires `sender instanceof Player`, so console always gets
`Messages.NOT_PLAYER` and never actually drives the spawn path (`ws7-no-bartizan-citizens` smoke row, `exec/WS7/G5-report.md`
"Fix round 1"). This is the only way to actually exercise it.

**Setup**: Bartizan-less server, Citizens installed and enabled, op player online.

- [ ] Step 1 — `/glw civilian spawn gang_member` (a `Hostile: true`, `Weapon_Pool`-bearing type,
  `npc/civilians.yml`) as a real player, standing somewhere a civilian can legally spawn → an NPC spawns, no
  exception in console, no stack trace.
- [ ] Step 2 — Inspect the spawned NPC's held item (look at it, or `/citizens` inspection tooling if installed) →
  **empty hand / vanilla item only** — no Bartizan weapon, confirming `create`/`buildItem` both correctly degraded
  to `NpcRangedAttack.NONE`/`null` rather than throwing.
- [ ] Step 3 — Let the NPC engage (if hostile AI is on) or otherwise interact with it → no combat-path exception
  from a null/absent weapon reference anywhere downstream (`CivilianNpcFactory`, `AbstractNpc.setRangedAttack`).
- [ ] Step 4 — Repeat with Bartizan **installed** for contrast (optional but recommended) → the same NPC type now
  spawns armed, confirming the guard is genuinely Bartizan-presence-conditional, not accidentally always-off.

---

## 4. Jetpack armour reduction with Bartizan present

Confirms the opt-in `Bartizan_Traits:` block in `items/jetpacks.yml` (migration note §3) actually reaches
Bartizan's damage-reduction path via the new `WearableCatalog.register(String, Wearable)` external-registration
hook (Bartizan 0.4.0) — this is new behaviour introduced by this wave, not previously covered by any smoke row or
unit test at the live-server level (unit-level coverage is Bartizan-side, on `WearableCatalog.register` itself, not
on Gangland's registration call).

**Setup**: Bartizan 0.4.0+ installed and enabled. `items/jetpacks.yml`'s jetpack entry has a `Bartizan_Traits:`
block configured with a known `Base_Damage_Reduction` (e.g. `0.05`) and at least one trait.

- [ ] Step 1 — Boot the server, confirm no error/exception around Gangland's `WearableCatalog.register(...)` call
  (should be silent on success).
- [ ] Step 2 — `/glw jetpack give <id> 1`, equip it as a chestplate.
- [ ] Step 3 — Take damage from a source Bartizan's reduction path applies to (per however
  `applyWearableReduction`/`reduceCritBonus`/`reduceFireTicks` are wired for a registered wearable) → observed
  damage is reduced versus an equivalent hit with the jetpack unequipped or with `Bartizan_Traits:` absent from
  config, by roughly the configured `Base_Damage_Reduction`.
- [ ] Step 4 — Confirm the jetpack is still excluded from paths that would try to *build* it as a Bartizan item —
  `/bartizan wearable give`/`info`/`list` should NOT list or resolve the jetpack (it's `external(true)`, Bartizan
  never produces the item itself) — only the reduction/effects side applies.
- [ ] Step 5 — Remove Bartizan (or the `Bartizan_Traits:` block) and repeat step 3 → no reduction applied, plain
  `IRON_CHESTPLATE`-only vanilla protection, confirming the opt-in is genuinely optional both ways.

---

## Regression Risks

- `gangland-gadget` — `CarDamageListener`/`CarWeaponDamageListener`/`CarMeleeWeaponLookup`/`CarDamageMath` (car
  damage split), `JetpackService`/`JetpackAddon`/`JetpackBartizanTraitBridge` (jetpack rehome + migration).
- `gangland-civilians` — `BartizanNpcWeapons`, `CombatEligibilityConfig`, `CiviliansModule.configure`,
  `GangAllyWeaponImpactListener`.
- Both modules' `module.yml` (`Host_Api: 1.1`, no `Plugins: [Bartizan]`) and the shared
  `BartizanBlindScan`/`BartizanReferenceScan` test infrastructure in `gangland-core` — if either scan regresses
  (a new unguarded Bartizan reference lands), items 1–3 above are exactly the failure modes that would surface live.

---

[Back to WS7 exec index](.) | [G5 report](./G5-report.md) | [Migration note](../../../../../documentation/migration-0.9.2.md)
