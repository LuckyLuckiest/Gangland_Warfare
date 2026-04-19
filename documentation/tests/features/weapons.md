# Weapons — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/weapons.md) | [Developer Doc](../../developer/weapons.md)

---

## Overview

Fully custom weapon system across **five action types**: gun, melee, projectile (throwable), incendiary, biological.
Per `feedback_unify_across_weapon_types`, any weapon-system change must be verified across all five — this checklist
enforces that.

**Modules involved:** `gangland-features/gangland-weapon`, `gangland-impl`, `gangland-compatibility/*` (recoil NMS).

---

## Pre-Conditions

- [ ] Op player online.
- [ ] Ammo configs present in `items/ammunition.yml`.

---

## Per-Action-Type Smoke Tests

Run one weapon from each category:

### Gun (`rifle`)

- [ ] `/glw weapon give rifle 1` → rifle appears in inventory.
- [ ] `/glw ammo give 5,56 30` → ammo in inventory.
- [ ] Right-click to scope in; scope sound plays.
- [ ] Left-click to fire. Projectile travels at configured speed. Particle trail visible.
- [ ] Headshot bonus damage applies on a head-hit.
- [ ] Flyby sound plays for nearby players within `Flyby_Range`.
- [ ] Empty the magazine, click → empty-magazine sound plays.
- [ ] Reload animation runs for `Reload.Cooldown` seconds; action bar shows reload text.
- [ ] Ammo count decreases correctly per reload (`Reload.Consume`).

### Melee (`knife`)

- [ ] `/glw weapon give knife 1`.
- [ ] Attack a target → melee damage applied.
- [ ] Durability decreases per hit (if configured).

### Throwable (`grenade`)

- [ ] `/glw weapon give grenade 3`.
- [ ] Throw → arcs, lands, detonates after fuse.
- [ ] Explosion damage applied within radius.

### Incendiary (`molotov`)

- [ ] `/glw weapon give molotov 1`.
- [ ] Throw → impact ignites a fire patch.
- [ ] Entities in the patch take fire-tick damage for the configured duration.

### Biological (`syringe_gun`)

- [ ] `/glw weapon give syringe_gun 1`.
- [ ] Hit target → biological effect applied (poison, slow, etc. per config).

---

## Fire Mode Semantics (per `feedback_selective_fire_semantics`)

- [ ] `SINGLE`: one shot per click. Holding the button does nothing.
- [ ] `BURST`: one burst per click. Holding the button does **not** repeat.
- [ ] `AUTO`: holding the button fires continuously at the cooldown rate.

---

## Timer Safety (per `feedback_repeating_timer_async`)

- [ ] Any `Timer.start(true)` call in weapon code only flips flags / calls `cancel()` — no Bukkit world/entity API.
- [ ] Raytracer and projectile physics use `Timer.start(false)` (sync).

---

## Weapon Modifiers

- [ ] `Break_Blocks` — configured block types break after the required hit count.
- [ ] `Penetration` — bullet passes through the configured number of blocks/entities with damage falloff.
- [ ] `Ricochet` — bullet bounces off configured materials, losing damage per bounce.
- [ ] `Armor_Piercing` — bypasses configured % of target armour.
- [ ] `Tracer` — tracer colour + glow + size render correctly.
- [ ] `Flat_Damage` — applied after reductions (confirmed on a fully-armoured target).

---

## Reload Safety

- [ ] Edit `weapon/rifle.yml` to change `Shoot.Projectile.Damage.Base`.
- [ ] `/glw reload files` → new damage applies on next shot.
- [ ] `/glw reload` mid-reload animation → reload animation cancels cleanly; no ghost timer.

---

## Persistence

- [ ] Weapon item in inventory survives server restart with its NBT intact (current magazine ammo, durability).

---

## Edge Cases

- [ ] Fire with `Weapon_Consumed.Consume_On_Shot > 0` → weapon destroyed after N shots.
- [ ] Fire with `Weapon_Consumed.Time > 0` → weapon destroyed N seconds after first shot.
- [ ] No ammo of the configured type → weapon cannot reload; action bar shows appropriate message.
- [ ] Ammo key with a dot in its name uses a comma (`5,56` not `5.56`) in both the weapon config and
  `ammunition.yml`.

---

## Regression Risks

- `gangland-weapon` — projectile, recoil, reload pipeline.
- `gangland-compatibility/version-*` — NMS recoil adapters (test on the oldest and newest supported MC versions).
- Cops firing weapons — cops pull from their own weapon pools; verify cop gunfire still works
  ([see cops-n-crooks](./cops-n-crooks.md)).

---

[Back to Test Index](../README.md)
