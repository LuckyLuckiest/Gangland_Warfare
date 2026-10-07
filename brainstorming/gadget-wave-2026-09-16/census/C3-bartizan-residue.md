# C3 — Bartizan residue census (repo: `E:\Programming\java\Bartizan`, 0.3.0, HEAD graph refreshed 2026-09-16)

Source: Haiku Explore agent, graphify-first on the freshly rebuilt Bartizan graph (4068 nodes / 11695 edges).

## 1. Jetpack-specific residue (must leave Bartizan with the jetpack)

| file:line | content | classification |
|---|---|---|
| `bartizan-plugin/src/main/resources/items/wearables.yml:238-260` | `jetpack:` entry + full config (Material, Traits, Lore, Base_Damage_Reduction) | config definition — leaves |
| `bartizan-plugin/src/main/resources/items/wearables.yml:45` | `FUEL_EFFICIENT` trait comment (10%/level, max 2; jetpack-specific) | leaves (or generalize if another wearable ever wants it) |
| `WearableAddon.java:114-127` | `legacyJetpackToExtraTags()` static method — translates Gangland 0.8.4 `Jetpack:` YAML block to `Extra_Tags:` map | migration converter — leaves |
| `WearableAddonLegacyJetpackTest.java:14+` | full test class for the legacy jetpack→ExtraTags migration | leaves |
| `documentation/migration.md:44-102` | "§3.4 Jetpack: → Extra_Tags:" section + before/after examples + legacy-load warning | leaves (superseded by a new Bartizan 0.3.0→0.4.0 note) |

**False positives (generic, not jetpack-specific — stay in Bartizan as-is):**
- `Wearable.java:42,44,119,122` — javadoc EXAMPLES using jetpack to illustrate `extraTags()`; no code logic tied to it.
- `WearableRefresher.java:13` — comment lists "jetpack" as an example wearable type; refresher itself is generic.
- `WearableCommand.java:22` — a Gangland CLI reference in a comment; no jetpack logic in Bartizan code.

## 2. `Wearable`/`WearableCatalog` API surface — fully generic, nothing jetpack-only

`Wearable.java:51` (`@Builder @Getter public class Wearable`):

| member | line | purpose | jetpack-only? |
|---|---|---|---|
| `extraTags()` | 325 | generic `Map<String,Object>` container (jetpack fuel/thrust scalars ride here, but so could anything) | no |
| `getWearable(String key)` | via Catalog | registry lookup by key | no |
| `buildItem(Player)` | 343 | stamped `ItemStack` factory | no |
| `traits()`, `traitLevel(String)` | 304, 309 | armor trait access | no |
| `getGenericDamageReduction()` + 5 siblings (projectile/fire/explosion/crit/fire-tick) | 426+ | damage-reduction API | no |
| `isRegisteredWearable(ItemStack)` | 211 | static detection | no |
| `fromItemStack(ItemStack)` | 183 | temp vanilla-armor → `Wearable` | no |

`WearableCatalog.java` (interface): `getWearable(key)`, `getWearables()`, `resolveWearable(ItemStack)`, `applyWearableReduction(damage, entity, projectile)`, `reduceCritBonus()`, `reduceFireTicks()` — all generic.

**Conclusion: zero jetpack-specific members in the public API.** Nothing to shrink here besides the YAML/migration residue above.

## 3. Jetpack treatment in equip/effects/refresh — 100% external today

- `WearableEffectsService.java:1-50` `tick()` loops armor slots, applies `Effects_While_Worn` + fires `On_Equip`/`On_Unequip` hooks generically via `Wearable#effectsWhileWorn()`/`onEquip()`/`onUnequip()`. **No `extraTags().containsKey("fuel")` check, no fuel/thrust logic anywhere in Bartizan.**
- Conclusion confirmed: Bartizan treats every wearable identically; all jetpack behavior already lives in Gangland's `gangland-gadget` module. Nothing behavioral to port besides identity/config.

## 4. `Extra_Tags:` parsing — fully generic, no hardcoded jetpack keys

`WearableAddon.java:415-430` (`readExtraTags()`):
```
extraSection = wearable.get("Extra_Tags").asMapping().orNull();
if (extraSection != null) return nodeToMap(extraSection);          // fully generic
jetpackSection = wearable.get("Jetpack").asMapping().orNull();     // legacy-only fallback
if (jetpackSection != null) { log.warn(...); return legacyJetpackToExtraTags(...); }
```
Jetpack key names (`fuel`, `jetpack_fuel_consumption_rate`, etc.) are **not** hardcoded in the parser — they only ever arrive through the legacy migration path. A planner can delete the jetpack `Extra_Tags:` entry from `wearables.yml` with zero parser changes needed.

## 5. Events + `ItemVocabulary` — nothing wearable/jetpack-specific to hook

- `bartizan-api/.../event/*`: 9 `Weapon*Event` classes (BeamFire, EntityDamage, Kill, Reload, Assist, etc.). **Zero wearable-specific events** — a soft gadget consumer has no Bartizan wearable event to hook; it only has `ItemVocabulary` for identity.
- `BartizanItemVocabulary.java:50` `contribute(ItemVocabularyRegistrar)` registers `weapon:`, `ammo:`, `wearable:` prefixes with Keystone so Gangland loot chests/signs resolve `wearable:jetpack`-style strings — this is the SAME mechanism K4 wants the gadget module to register its own `jetpack:`/`gadget:` prefix through, once the jetpack is gadget-owned. No jetpack-specific registration code exists here today (generic prefix only).

## 6. Versioning conventions

- `pom.xml`: `<revision>0.3.0</revision>`.
- Docs: `documentation/bartizan-api.md` (api reference), `documentation/migration.md` (§3.4 = the jetpack rename guide, Gangland 0.8.4 → Bartizan 0.1.0+), `documentation/weapons-roadmap.md` (feature gates HA-HC).
- No CHANGELOG file; version-bump notes live inline in `migration.md` sections. K3's planned Bartizan 0.3.0 → 0.4.0 bump should add a new migration.md section following this same convention, marking §3.4 superseded/removed.

## Summary for WS7 planning

**Leaves Bartizan (K3):** the `jetpack:` wearables.yml entry (~23 lines), `legacyJetpackToExtraTags()` (~14 lines) + its test class, the `FUEL_EFFICIENT` jetpack-specific comment, migration.md §3.4 (superseded, not necessarily deleted — historical record).

**Stays in Bartizan untouched (generic, serves future wearables):** `Extra_Tags:` parsing, all of `Wearable`/`WearableCatalog`/`WearableEffectsService`, all trait mechanics (REINFORCED/BULLETPROOF/PADDED/TOUGHENED/LIGHTWEIGHT — none jetpack-only), equip/unequip hooks, the `wearable:` `ItemVocabulary` prefix (other wearables still use it).

**Not verified by this census:** whether any OTHER plugin/server config currently references `wearable:jetpack` in production data (migration/backward-compat risk for WS7 to size).
