# Re-review — WS7 batch 1 fix round 1 — 2026-09-16 (Sonnet, transcribed)

| Finding | Status | Evidence |
|---|---|---|
| C1 buildItem throws | ADDRESSED | `Wearable.external` (@Builder.Default false, Wearable.java:98-99); `WearableRefresher.canRefresh` :22-26, `WearableConverter.convert` :33, `WearableItemSerializer.claims` :26-31, `WearableGiveCommand` :102 + tab :124-126 all skip external |
| C2 permission blocks equip | ADDRESSED | `getPermission()` Wearable.java:307-308 returns null when temporary/external/null key |
| I3 serializer collision | ADDRESSED | `BartizanItemPredicates.WEARABLE.and(wearableItemSerializer::claims)` at BartizanItemVocabulary.java:53; `claims()` true for unresolved tags (old behaviour), false only for a resolved external entry |
| I4 doc contract | ADDRESSED | WearableCatalog.java:87-102 javadoc; bartizan-api.md ~847-864 |
| I5 late enable | ADDRESSED per ruling | D4-design.md states the softdepend load-order guarantee and the PlugMan-style cost; no hook |
| I6 zero test | ADDRESSED | `WearableExternalRegistrationTest` (6 cases); red run 3F/1E incl. the live `Material cannot be null` crash, then green |
| M7 dead imports/javadoc | ADDRESSED | WearableAddon.java |
| M9 literal null name | ADDRESSED | WearableListCommand.java:29-31 pre-filters external |

New breakage: none (only two `WearableItemSerializer` construction sites, both updated; combined predicate unchanged for YAML wearables; loader never sets `external`). Gangland fix diff = pom.xml revision only.
Lead's concern (command guards not driven end-to-end): acceptable — one reused boolean check, no command test scaffolding exists in Bartizan.
Verdict: all addressed.
Deferred: docket candidates (a) refresher stale-tag no-op, (b) external permission node adjacent to GD-07 → G6; M8 parked.
