# WS7 gadget wave — docket rows (G6)

Text for the orchestrator to file against the Gangland Bug Docket (`GD-`) and the cross-project docket (`BZ-`,
Bartizan). Every entry below is sourced against the actual docket JSON (`brainstorming/bug-docket-2026-09-06/bugs.json`)
and this wave's own gate reviews — nothing here is recalled from memory alone.

---

## Existing rows — status updates

### GD-12 — FIXED

- **Title** (docket): "A jetpack without `Fuel_Key` starts a session that never flies."
- **Was**: `WearableAddon.loadWearables:155` defaulted `Fuel_Key` to `""`, while `Wearable.isJetpack():228` checked
  `fuelKey != null` — an empty string passed that check, so a misconfigured jetpack entry silently started a
  fuel-tracking session that could never actually consume fuel or fly.
- **Fixed**: Gangland 0.9.2 gate G2 (jetpack rehome out of Bartizan's `WearableAddon` into gadget's own
  `JetpackAddon`). `JetpackAddon.loadJetpacks` now treats a missing/blank `Fuel_Key` as a load error (warn + skip
  the entry), not a silently-degraded jetpack. Covered by `JetpackAddonLoadTest` ("`Fuel_Key` mandatory, field
  round-trip").
- **Branch/commit**: 0.9.2, gates G2/G3 (committed, batch 1 per this wave's exec log).

### GD-20 — jetpack half moot, car half still open

- **Title** (docket): "`Car.maxHealth` and `glideDescentRate` are dead config."
- This id covers **two independent dead-config fields**: `Car.maxHealth` (parsed in `CarAddon`) and
  `Wearable.glideDescentRate` (parsed in the old `WearableAddon`, jetpack-only field).
- **The `glideDescentRate`/jetpack half is now moot**, not fixed — the jetpack no longer lives in `WearableAddon`
  at all (gate G2 rehomed it into gadget's `JetpackAddon`/`Jetpack` domain type), so the specific dead-field
  instance the docket named no longer exists in that form. Whether `JetpackAddon`'s own glide/descent handling (if
  any) has a live or dead field of its own was **not** audited as part of this wave and should not be assumed fixed
  — only the originally-named `WearableAddon.glideDescentRate` instance is moot.
- **The `Car.maxHealth`/`CarAddon` half is unaffected by this wave and remains open** — `CarAddon` was not touched
  for this finding; only its (identical, separate) default-material bug was, see the new triage row below.

### GD-27 — open, unchanged

- **Title** (docket): "`pendingRightClickInteract` token race."
- **Location** (as of 0.9.2): the field moved from `CarDamageListener` to the new shared `CarDamageState`
  (gate G5, `gangland-features/gangland-gadget/.../listener/car/CarDamageState.java`), which centralizes
  read/consume of the token across both `CarDamageListener` and the new `CarWeaponDamageListener` — the split G5
  introduced (one listener class became two) could have made this race structurally worse (two classes racing on
  the same token instead of one), and `CarDamageState` exists specifically to prevent that regression. **It does
  not fix the underlying race** (two events still racing on the same game tick) — the docket's own fix direction
  ("consume the token in one place") is now literally true of the code shape, but the race itself is untouched.
  Still P3, still open.

---

## New triage rows

### Default-material substitution in `CarAddon` (Gangland project, `GD-`)

- **Title**: `CarAddon.loadCars` silently substitutes a default `Material` for an unrecognised config value instead
  of rejecting the entry.
- **Location**: `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/car/config/CarAddon.java:77`
  — `Material material = XMaterial.matchXMaterial(materialString).orElse(XMaterial.MINECART).get();` — an
  unrecognised `Material:` string in `items/cars.yml` resolves to `XMaterial.MINECART` instead of `null`, which
  makes the very next line's "invalid Material" rejection branch (`CarAddon.java:80`) permanently dead code — a
  typo'd material name in config silently produces a Minecart-material car entry instead of a startup warning
  telling the server owner to fix their config.
- **History**: this is the exact same bug pattern `JetpackAddon` (jetpack's own loader) had before this wave —
  `WS7 G2-G3 review` (Minor 3) named both instances as one docket candidate ("silent default-material substitution
  in `CarAddon` and `JetpackAddon`") and the orchestrator's ruling fixed `JetpackAddon` only (now
  `.orElse(null)` + an explicit null-check that warns and skips the entry, `JetpackAddon.java:106-110`),
  **explicitly deferring `CarAddon`'s identical bug to a docket triage row at G6** — this row is that deferral
  being honoured.
- **Fix direction**: mirror `JetpackAddon`'s fix exactly — `XMaterial.matchXMaterial(materialString).map(XMaterial::get).orElse(null)`,
  then warn-and-skip on `null` instead of silently defaulting to `MINECART`.
- **Tier**: P3 (matches sibling `GD-20`'s tier; config-authoring footgun, not a runtime crash).

### Negative/huge `<amount>` in both gadget give commands (Gangland project, `GD-`)

- **Title**: `CarGiveCommand`/`JetpackGiveCommand`'s `<amount>` argument had no lower/upper bound — a negative
  amount threw `NegativeArraySizeException`, a huge one risked an unbounded allocation.
- **Location**: `CarGiveCommand.giveCarItem`/`JetpackGiveCommand.giveJetpackItem` — both are non-stackable
  armour/vehicle items (`maxStackSize == 1`), so `slots = ceil(amount / maxStackSize)` turned any negative `amount`
  straight into a negative array size on the very first bad input.
- **Found**: WS7 G4 review (Important 2).
- **Fixed**: 0.9.2 gate G4, fix round 1 — `amount <= 0` rejected at the command layer (`Messages.MUST_BE_NUMBERS`)
  plus a defensive `Math.max(0, Math.min(amount, 36 * maxStackSize))` clamp inside both private give-methods, so
  the method itself can never crash regardless of caller. Covered by `JetpackGiveCommandAmountGuardTest`/
  `CarGiveCommandAmountGuardTest` (reflection-driven, red-first — both threw `NegativeArraySizeException` before
  the fix).
- **Branch/commit**: 0.9.2, gate G4 fix round 1 (committed).
- **Tier**: was effectively P1/P2 (a crash on ordinary malformed console/command input); already fixed, filed here
  only so it's on the permanent record per house rule (a fix isn't "done" until the docket knows about it).

### Bartizan wearable refresher stale-tag no-op (Bartizan project, `BZ-`)

- **Title**: `WearableRefresher.canRefresh` claims any `wearable`-tagged `ItemStack`, but `WearableService.getWearable`
  can miss for a foreign or stale tag — the refresh silently falls through instead of refreshing or reporting an
  error.
- **Location** (Bartizan repo): `item/WearableRefresher.java:24,33,36`.
- **Found**: WS7 G0-G1 review (this wave's setup gate), recorded as docket candidate (a) in that review's notes.
  **Pre-existing** — not introduced by this wave, and not touched by any WS7 gate (the unregistered case is
  currently safe: `refresh` returns `null` and `ItemRefresherRegistry.java:62-70` falls through cleanly rather than
  throwing — this is a silent-no-op finding, not a crash).
- **Fix direction**: `canRefresh` should distinguish "no wearable tag at all" (correctly not its concern) from
  "has a `wearable` tag `WearableService` doesn't recognise" (arguably worth a diagnostic/fault report instead of
  silent pass-through) — needs a Bartizan-side design decision, not just a one-line fix; filed as triage, not a
  ready-to-fix row.
- **Project**: Bartizan (`BZ-` prefix, cross-project docket).

### External-wearable permission node — adjacent to open GD-07 (Bartizan project, `BZ-`, cross-referenced against `GD-07`)

- **Title**: An externally-registered `Wearable` (`WearableCatalog.register`, Bartizan 0.4.0, the mechanism
  Gangland's jetpack uses to keep its old damage-reduction values — see `documentation/migration-0.9.2.md` §3) has
  `getPermission()` return `null` by design, meaning **none** of Bartizan's own wearable-permission-check machinery
  ever applies to it.
- **Location** (Bartizan repo): `WearableCatalog.java`'s `external` flag handling; `Wearable#getPermission()`.
- **Found**: WS7 G0-G1 review, recorded as docket candidate (b) in that review's notes ("externally-registered
  wearables get an unregistered permission node — adjacent to open GD-07").
- **Relationship to `GD-07`** (Gangland project, open, P1, "the wearable permission is only checked on inventory
  clicks" — `WearableEquipListener.resolveArmorBeingEquipped:69-88`): GD-07 is about Bartizan-**native**
  wearables' permission check being incomplete (only checked on inventory clicks, not right-click-equip/drag/
  dispenser paths). This new finding is about a **different** case entirely — an externally-registered wearable
  (like the rehomed jetpack) has **no permission check at all** on Bartizan's side, by design (Bartizan never
  builds/gives it, so it never runs Bartizan's equip-permission path in the first place). The registering plugin
  (Gangland, for the jetpack) is solely responsible for its own permission gate — which Gangland's jetpack already
  has (`Jetpack.getPermission()`, checked in `JetpackService.activate`) — but this is worth a docket row so a
  *future* external registrant doesn't assume Bartizan enforces a permission node for them the way it does for its
  own catalogued wearables.
- **Fix direction**: none needed for the jetpack specifically (already covered by Gangland's own permission check);
  worth a documentation note in `bartizan-api.md`'s "External wearable registration" section (already present,
  confirmed during this gate — "an unregistered permission node silently blocking equip" is called out) — this
  triage row exists mainly to make the relationship to GD-07 explicit and searchable, not to request a code change.
- **Project**: Bartizan (`BZ-` prefix), cross-referenced against Gangland's `GD-07`.
