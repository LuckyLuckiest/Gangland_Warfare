# Config & YAML — Test Checklist

[Back to Test Index](./README.md) | [Configuration Reference](../developer/configuration.md)

---

## Overview

Every `.yml` file the plugin ships must be:

1. **Regenerable** — delete the file, restart, `FileInitializer` pulls a fresh copy from the jar.
2. **Reloadable** — edit a value, `/glw reload files`, change applies without restart.
3. **Resilient** — a corrupt YAML does not bring the server down; the plugin logs a clear error.
4. **Stylistically clean** — block-style maps, `Capitalized_Underscore_Separated` keys, no literal `§` colour codes,
   no emojis in config values unless explicitly user-facing.

---

## Config File Inventory

Run the three tests (regenerate / reload / corrupt) against each file below.

### Root

- [ ] `settings.yml` — main runtime config (database type, auto-save interval, economy knobs).
- [ ] `scoreboard.yml` — scoreboard layout and colours.
- [ ] `plugin.yml` — Bukkit metadata. Changes require a restart (Bukkit limitation); do **not** expect reload to
  pick these up. Smoke test: cold-start only.
- [ ] `commands.json` — command descriptions. Per `feedback_commands_json`, any new command added this release must
  have an entry here.

### Messages (localisation)

- [ ] `message/message_en.yml`
- [ ] `message/message_es.yml`
- [ ] No file contains a literal `§`; colour codes use `&` and pass through `ChatUtil.color()`.

### NPCs

- [ ] `npc/civilians.yml`
- [ ] `npc/cops.yml`
- [ ] `npc/trader_traits.yml`

### Weapons

Run the per-file trio against every weapon config:

- [ ] `weapon/awp.yml`
- [ ] `weapon/crowbar.yml`
- [ ] `weapon/flamethrower.yml`
- [ ] `weapon/flashbang.yml`
- [ ] `weapon/golden_ak47.yml`
- [ ] `weapon/grenade.yml`
- [ ] `weapon/knife.yml`
- [ ] `weapon/machete.yml`
- [ ] `weapon/minigun.yml`
- [ ] `weapon/molotov.yml`
- [ ] `weapon/mp5.yml`
- [ ] `weapon/pistol.yml`
- [ ] `weapon/ray_gun.yml`
- [ ] `weapon/revolver.yml`
- [ ] `weapon/rifle.yml`
- [ ] `weapon/rocket_launcher.yml`
- [ ] `weapon/sawn_off.yml`
- [ ] `weapon/shotgun.yml`
- [ ] `weapon/smoke_grenade.yml`
- [ ] `weapon/steyr_aug.yml`
- [ ] `weapon/syringe_gun.yml`
- [ ] `weapon/tomahawk.yml`

Spot-check one weapon per action type: gun (rifle), melee (knife), throwable (grenade), incendiary (molotov),
biological (syringe_gun). Per `feedback_unify_across_weapon_types`, a weapon-system change must cover all five.

### Items

- [ ] `items/ammunition.yml` — key names use commas, not dots (YAML keys cannot contain `.`).
- [ ] `items/cars.yml`
- [ ] `items/money.yml`
- [ ] `items/unique_items.yml`
- [ ] `items/wearables.yml`

### Inventory

- [ ] `inventory/alliance_stat.yml`
- [ ] `inventory/gang_info.yml`
- [ ] `inventory/gang_stat.yml`
- [ ] `inventory/phone.yml`
- [ ] `inventory/phone_gang.yml`
- [ ] `inventory/user_stat.yml`

### Loot Chests

- [ ] `lootchests/loot_chests.yml`
- [ ] `lootchests/tiers.yml`

---

## Per-File Trio

For each file above, run this trio:

### 1. Regenerate

- [ ] Delete the file. Stop/start the server.
- [ ] `FileInitializer` regenerates it from the shaded jar's resources.
- [ ] The regenerated file is byte-identical (or near it) to the jar copy — no stale overrides sneaked in.

### 2. Reload

- [ ] Change a visible value (e.g. a colour code, a display name).
- [ ] Run `/glw reload files` (or the domain-specific reload if one exists).
- [ ] Change takes effect immediately without a server restart.

### 3. Corrupt

- [ ] Append random garbage to the file to make it invalid YAML.
- [ ] Restart the server.
- [ ] Plugin logs a clear error naming the file and (ideally) the line. Server stays up. Feature using that file is
  disabled or falls back to defaults.
- [ ] Per `feedback_file_initializer_recovery`: regenerate-from-jar + retry kicks in where applicable.

---

## Stylistic Gates (CLAUDE.md + memories)

Run these greps across `gangland-impl/src/main/resources/`:

- [ ] No literal `§` anywhere — colour codes must be `&`. Reference: `feedback_chat_color_codes`.
- [ ] No inline flow maps `{ key: value }` — block style only. Reference: `feedback_yaml_inline_braces`.
- [ ] Top-level keys are `Capitalized_Underscore_Separated`. Reference: `feedback_yaml_underscore_keys`. Lookup
  ids (weapon keys, ammo keys, shop keys) stay lowercase.
- [ ] `Sound` fields reference string sound names via `SoundEffect` (keystone `sound` package), never raw `Sound.X` enum literals.
  Reference: `feedback_sound_via_configuration`.
- [ ] No calls to `FileConfiguration.setDefaults()` / `copyDefaults()` for YAML fallback. Reference:
  `feedback_no_bukkit_setdefaults`.

---

## Config-vs-Loader Drift

For each YAML above, spot-check that every top-level key has a matching loader string in Java (or the key is dead
and should be removed). See the `gangland-yaml-review` skill for automated coverage.

- [ ] No dead keys in `settings.yml`.
- [ ] No missing keys (loader reads a path that the YAML does not provide).
- [ ] Domain catalogues live in their own file — settings.yml holds only general knobs. Reference:
  `feedback_split_config_files`.

---

[Back to Test Index](./README.md)
