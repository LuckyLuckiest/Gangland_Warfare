# Bartizan integration

[← Back to Documentation Index](./README.md)

---

Weapons, ammunition, wearables and the projectile/recoil system left this repo entirely in 0.9.0 for the standalone
sibling plugin **Bartizan** (`E:\Programming\java\Bartizan`, `org.luckyraven.bartizan:bartizan-api:0.1.0`). This
page is the **Gangland side** of the integration — what Gangland consumes, how it degrades without Bartizan, and
which of Gangland's own modules carry the dependency. The API surface itself (the four accessors, event types,
domain types a consumer may name) is Bartizan's own reference and is **not duplicated here** — see
[`bartizan-api.md`](../../Bartizan/documentation/bartizan-api.md) in the Bartizan repo for the full contract.

## Is Bartizan required?

**No.** It is a soft dependency of the Gangland core (`plugin.yml` `softdepend: [..., Bartizan]`) and a **hard,
fail-fast** dependency of exactly three runtime modules, declared through each `module.yml`'s `Plugins:` key
(see `documentation/module-loader.md`'s "Writing a module" section for how `Plugins:` differs from `Depends:`):

| Module | `Plugins:` | What breaks without Bartizan |
|---|---|---|
| `gangland-civilians` | `[Bartizan]` | Skipped entirely (`module.plugin.missing`) — no civilian NPCs spawn at all, and (transitively) `gangland-turf`'s `Depends: [civilians]` means turf is skipped too |
| `cops-n-crooks` | `[Bartizan]` | Skipped entirely (`module.plugin.missing`) — no cops |
| `gangland-gadget` | `[Bartizan]` | Skipped entirely (`module.plugin.missing`) — no cars, no jetpacks |
| `gangland-mail`, `gangland-npc-shops` | none | Load normally — mail and trader/banker shops never touched a weapon type |

A server without Bartizan therefore boots with only `gangland-mail` and `gangland-npc-shops` able to load (turf is
gated behind civilians, which is gated behind Bartizan — see the open decision below); the core itself always
boots regardless. This is the state phase-D smoke row **D6** exercises.

**Open decision (not resolved by this stream, tracked in the Bartizan wave's `README.md` decisions log):**
`gangland-civilians` declaring `Plugins: [Bartizan]` couples turf capture to a weapons plugin transitively (turf
`Depends: [civilians]`). Turf capture itself has no weapon coupling. The alternative — splitting the three
Bartizan-touching classes out of civilians into their own tiny module — has not been implemented. Until it is,
running turf on a Bartizan-less server means running civilians too, i.e. installing Bartizan.

## The three `ServicesManager` keys

Bartizan (like every consumer of it) never caches a `ServicesManager` lookup — every read is fresh, because
Bartizan may enable before or after the consumer, or not be installed at all:

| Service | Direction | Consumed by (Gangland side) |
|---|---|---|
| `org.luckyraven.bartizan.api.BartizanApi` | Bartizan publishes | `gangland-civilians` (`BartizanNpcWeapons`, a factory hook — **not** an `NpcRangedAttack` implementation), `cops-n-crooks` (`CopNpcFactory`/`CivilianNpcFactory` weapon builds), `gangland-gadget` (`GadgetModuleConfig`'s jetpack-fuel-sink predicate, `CarDamageListener`'s melee-damage/explosion-suppression resolution, `JetpackService`/`JetpackTask`'s wearable reads) |
| `org.luckyraven.keystone.item.spi.ItemVocabulary` | Bartizan publishes | The core's `GanglandContext.installItemVocabularies()` (see `module-loader.md`'s "Core seams" section) — folds `weapon:`/`ammo:`/`wearable:` item strings into the core's own registries once per boot |
| `org.luckyraven.bartizan.api.combat.CombatEligibility` | **Gangland publishes**, Bartizan pulls | `gangland-civilians`' `GanglandCombatEligibility` — `canBeHit(player)` is the *negation* of `DownedPlayerRegistry.isDowned(uuid)`; Bartizan's weapon-fire code calls `CombatEligibility.resolve()` before letting a shot register, falling back to `CombatEligibility.DEFAULT` (`!player.isDead()`) when nothing is registered — **without the civilians module, a downed player becomes shootable again**, since nothing installs the real implementation |

Direction is fixed: Bartizan publishes the first two, and pulls the third. Neither side ever looks up the other's
class by name outside these three keys — the core in particular takes **zero** `bartizan-api` dependency of any
scope (enforced by gate G4's boundary greps in the Bartizan-wave checklist).

## What degrades, specifically

- **Item vocabularies.** No Bartizan → `GanglandContext.installItemVocabularies(...)` folds an empty list, logs
  `"Item vocabularies installed: none — weapon:/ammo:/wearable: item strings will not resolve"`, and every
  `weapon:`/`ammo:`/`wearable:` reference in `loot_chests.yml` or on a rewritten legacy sign **silently** resolves
  to nothing. This is the single biggest risk the Bartizan-wave checklist flagged (R-1): the log line is the only
  warning an operator gets.
- **NPC weapons.** `BartizanNpcWeapons.create(...)` (civilians module) returns `NpcRangedAttack.NONE` when
  `BartizanApi` is unresolvable or the configured weapon name is invalid — cops and civilians fall back to whatever
  vanilla weapon-pool item `AbstractNpc`/`CopNpc`/`CivilianNpc#equip()` already applied (bare-handed, or a plain
  item with no weapon behavior). This path is exercised even **with** Bartizan installed, whenever a tier's
  configured weapon name does not resolve — not only in Bartizan's total absence.
- **Weapon trade/view signs.** Not applicable in the literal old sense — the six legacy `[WEAPON-BUY]` /
  `[WEAPON-SELL]` / `[AMMO-BUY]` / `[AMMO-SELL]` / `[WEARABLE-BUY]` / `[WEARABLE-SELL]` sign types no longer exist
  as Gangland sign types. A sign of one of those types placed **before** 0.9.0 keeps resolving through
  `LegacySignRewriter` + `Settings.Signs.Legacy_Aliases`, rewritten onto the generic `item-buy`/`item-sell` sign
  types with a `weapon:`/`ammo:`/`wearable:` definition prefix baked in — but the item definition itself still
  needs Bartizan's item vocabulary to resolve. Without Bartizan, the sign type still registers and the sign still
  "works" mechanically, but every trade attempt fails to find the item, same as any other unresolvable definition
  string. See [`migration-0.9.0.md`](./migration-0.9.0.md) for the exact mechanism.
- **Gadget fuel/weapon interactions.** `FuelService.isFuelSink(ItemStack)` (gangland-item) delegates to a predicate
  `GadgetModuleConfig` installs from Bartizan's `WearableCatalog` — without Bartizan it stays the interface's
  `false` default, so a jetpack can no longer be refuelled from a gasoline can via the inventory-click path (the
  jetpack's own NBT-backed fuel gauge still works; only the container→sink transfer needs Bartizan).
  `CarDamageListener`'s melee-damage and grenade-explosion-suppression logic both no-op gracefully (fall back to
  vanilla punch damage / no double-hit suppression needed, since Bartizan events never fire without Bartizan).

## What never breaks

Gang management, economy, waypoints, loot chests (aside from `weapon:`/`ammo:`/`wearable:` entries), ranks,
mail, gang alliances, turf capture's core mechanics (contribution, income, garrison — separate
from the Quartermaster/garrison-defender NPCs, which are Citizens-gated, not Bartizan-gated directly), and trader/
banker NPC shops (`gangland-npc-shops` carries no `Plugins:` dependency at all).

## See also

- [`documentation/module-loader.md`](./module-loader.md) — the `Plugins:`/`Depends:` descriptor mechanism, the
  `ItemVocabulary` core seam, and the full runtime-module table.
- [`documentation/migration-0.9.0.md`](./migration-0.9.0.md) — the server-owner upgrade guide.
- Bartizan's own [`documentation/bartizan-api.md`](../../Bartizan/documentation/bartizan-api.md) — the full API
  contract, service table, and event catalogue.
- Bartizan's own [`documentation/migration.md`](../../Bartizan/documentation/migration.md) — installing Bartizan
  itself, its YAML/DB/permission/command migration from the deleted Gangland weapon module.
