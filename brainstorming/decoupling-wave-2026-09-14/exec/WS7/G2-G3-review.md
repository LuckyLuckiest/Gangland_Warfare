# Review — WS7 G2 + G3 (+ D4 bridge) — 2026-09-16 (Opus, transcribed)
Verdict: FIX (2 Important, 5 Minor)

## Spec table (condensed)
G2.1 Jetpack/JetpackKey full field set, buildItem stamps JETPACK_ID + fuel trio ✅ · G2.2 JetpackAddon loads items/jetpacks.yml, Fuel_Key mandatory (GD-12) ✅ · G2.3 FILE-phase bean in GadgetFileConfig:46-53 ✅ · G2.4 permission registered at load (`gangland.jetpacks.<id>`) ✅ · G3.1 converter/serializer/refresher + GadgetItemPredicates.JETPACK on gadget's own tag ✅ · G3.2 beans wired, serializer+refresher at priority 20 (higher sorts first in Keystone registries) ✅ · G3.3 Service/Session/Task off Wearable, isScoped + trait branch deleted, isJetpackFuelSink rewritten ✅ · D4 stamp-only-when-registered ❌ (I1) · D4 bridge static/no Bartizan type in signatures/external(true)/fresh ServicesManager lookup ✅ · YAML schema and values verbatim from Bartizan 0.3.0 wearables.yml:238-267 ✅ · JetpackAddonLoadTest red-first ✅ · JetpackNbtIdentityTest ⚠ (I2) · JetpackTaskConsumptionRateTest flip, numbers unchanged, red proven ✅ · docket GD-12 ✅, GD-20 jetpack half unmentioned (M5)

## Findings
**Important 1** — `JetpackAddon`: `bartizanRegistered = true` is set from the guard branch, while `JetpackBartizanTraitBridge.register` silently returns when the `BartizanApi` service is null → a `wearable` tag can be stamped with no Bartizan definition (safe: `resolveWearable` falls through; gadget's registries outrank Bartizan's), but off the W11 pin. Fix: `register` returns boolean (false on the early return), flag assigned from it, and the dropped guard-test case (guard true, no service → no flag, no tag) added.
**Important 2** — no test asserts `Jetpack.buildItem()` stamps identity + full fuel (`FUEL_CURRENT == Max_Fuel`, `JETPACK_ID` present) — the refresher's whole contract; `JetpackNbtIdentityTest` has no red run. Loss side verified safe (refresh only runs on shop delivery/admin views, never on a worn stack).
**Minor 3** — `XMaterial.matchXMaterial(s).orElse(IRON_CHESTPLATE).get()` makes the "invalid Material" branch dead (copied from `CarAddon.java:77-82`).
**Minor 4** — `buildItem` stamps fuel unconditionally; `Max_Fuel: 0` ships a permanently dry jetpack (Car guards `maxFuel > 0`).
**Minor 5** — GD-20's jetpack half (no `jetpack_glide_descent_rate`) lands here, unrecorded.
**Minor 6** — `JetpackTask.updateActionBar` swapped `✈`/`⚠` escapes for literal glyphs; rewritten files flipped to LF.
**Minor 7** — map key lower-cased while `jetpackId` keeps YAML case → permission `gangland.jetpacks.Jetpack` for a capitalised entry (same as CarAddon).

## Cannot verify
Builds (822 tests); classloader linkage of the bridge on a Bartizan-less server (G5's `GadgetBartizanBlindScanTest` must force this class through the blinded loader, not only `CarDamageListener`); runtime damage-reduction equivalence (smoke at G4/G5).

## Notes
- G4: a pre-0.9.2 jetpack (Bartizan tag only) is inert until the migration hook; the smoke row should refuel and fly a migrated item.
- G6/Risk 3: shop/lootchest rows persisted as `wearable:jetpack` resolve to nothing on both sides; the migration note must say "re-add as `jetpack:<id>`".
- Docket candidate: silent default-material substitution in `CarAddon` and `JetpackAddon` (M3).
- Plan defect: §2's file table omits a registry class; folding the registry into `JetpackAddon` is fine (smaller).

## Orchestrator rulings (W14)
Fix I1, I2, M4, M6 (revert the glyph swap; EOL is a non-issue — git `autocrlf` normalises on commit) and M3 in `JetpackAddon` only (warn + skip on an unmatched `Material`; `CarAddon` gets a docket triage row at G6). M5: record GD-20 in the report. M7 parked (matches CarAddon; a lower-cased id everywhere is a separate tidy-up).
