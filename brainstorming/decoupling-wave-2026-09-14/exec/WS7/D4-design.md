# WS7-D4 design — Bartizan external wearable-trait hook (settled, fix round 1)

Revised after Opus review `exec/WS7/G0-G1-review.md` (Critical 1/2, Important 3/4/5/6) + orchestrator ruling W11.
The original shape ("build a minimal `Wearable`, stamp the tag unconditionally") was **not safe** — `buildItem()`,
the refresher, converter, `wearable:` serializer, and the give/info/list commands all assumed a *complete*
registered `Wearable` and either threw or misbehaved on a minimal one. This is the settled contract.

## The contract

`Wearable` gains a `boolean external` field (`@Builder.Default false`). An external entry is
**damage-reduction/effects only**:

- `WearableCatalog.register(String key, Wearable wearable)` (`bartizan-api`) — the registrant builds `wearable`
  with `.external(true)` plus `wearableKey`/`baseDamageReduction`/`traits` (optionally `effects`); every other
  field (`material`, `name`, `lore`, …) stays unset.
- `resolveWearable`/`applyWearableReduction`/`reduceCritBonus`/`reduceFireTicks` treat it exactly like a
  `wearables.yml` entry once a worn item carries the matching `wearable=<key>` NBT tag.
- Every path that would **build, convert, give, list or serialise** the item skips an external entry instead of
  calling `Wearable#buildItem()` (which throws — no `Material`): `Wearable#getPermission()` returns `null`
  (C2 — an unregistered permission node must never silently block equip); `WearableRefresher.canRefresh`,
  `WearableConverter.convert`, `WearableItemSerializer#claims` (new, combined with
  `BartizanItemPredicates.WEARABLE` at the registration site — Important 3) all return false/null for it;
  `/bartizan wearable give/info/list` skip it.
- The registering plugin owns building the ItemStack, stamping the tag, and its own give/converter/refresher/list
  surface for that item — Bartizan's role is strictly "read the registered definition to compute a reduction or
  run an effect." (Important 4 — this is now what `WearableCatalog.register`'s javadoc and
  `documentation/bartizan-api.md` actually say.)

Fixed in Bartizan (fix round 1, this worktree): `Wearable.java`, `WearableRefresher.java`,
`WearableConverter.java`, `WearableItemSerializer.java` (+`WearableService` constructor param, wired in
`ItemConfig`/`BartizanItemVocabulary`), `WearableGiveCommand.java`, `WearableInfoCommand.java`,
`WearableListCommand.java`. Covered by `WearableExternalRegistrationTest` (red-first).

## Gadget side (batch 2 — not yet built)

- `Jetpack.buildItem()` stamps the raw `wearable=<id>` NBT tag **only when the bridge actually registered** (i.e.
  only when `Settings.isBartizanAvailable()` was true and `Bartizan_Traits:` was configured for that entry) — never
  unconditionally. This closes Important 3 on the gadget side: a jetpack with no external registration never
  collides with Bartizan's `wearable:` vocabulary claim at all.
- `JetpackItemSerializer`/`JetpackItemRefresher` register at a **priority above Bartizan's 10** (the load-bearing
  number `BartizanItemVocabulary.contribute` uses for `weaponRefresher`/`wearableRefresher`), so gadget's own
  converter/serializer/refresher win the claim for a jetpack stack ahead of Bartizan's — pinned as a requirement
  for whoever builds G3, cross-referenced there.
- `JetpackBartizanTraitBridge` (static helper, same shape as `CarMeleeWeaponLookup`, B5): no `@Bean`, no field, no
  constructor parameter. Called once at `JetpackAddon` load, inside `if (Settings.isBartizanAvailable())`, resolves
  `BartizanApi`/`WearableCatalog` fresh off `Bukkit.getServicesManager()` (never cached), builds the external
  `Wearable`, calls `catalog.register(id, wearable)`.

## Bartizan absent, or enabling late (I5)

No `PluginEnableEvent` hook is added. Gangland's `plugin.yml` declares `softdepend: [Bartizan]`, so Bukkit enables
Bartizan before Gangland, and every module (including gadget) loads inside `Gangland.onEnable` — a present Bartizan
is therefore already enabled by the time `JetpackAddon` load runs the registration branch. The cost this accepts:
a Bartizan installed but enabled *later* than Gangland (a runtime plugin manager reload, PlugMan-style) misses the
registration until the next `/glw reload`. When Bartizan is absent entirely, the guard skips the whole branch, the
bridge class is never linked, and the jetpack carries no `wearable` tag at all — vanilla chestplate protection
only, matching WS7-D4 option A's accepted loss.

## Tests (gadget side, batch 2)

`JetpackBartizanTraitBridgeGuardTest` (mirrors `CarMeleeWeaponLookupGuardTest`, B7): with `isBartizanAvailable()`
stubbed false, assert zero interaction with the services-manager lookup. `GadgetBartizanBlindScanTest` (S3,
already planned) additionally proves the bridge class carries zero Bartizan-typed signatures reachable from an
always-loaded class.
