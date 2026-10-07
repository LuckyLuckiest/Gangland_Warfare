# Gate review D–G — Gangland 0.9.0 groups D, E, F, G (keystone-item migration, vocabulary fold, item signs)

Reviewer R-G-DEFG (Opus, feature-dev `code-reviewer`, read-only), 2026-09-09 ~00:10. Reviewed commits `de802972`
(D+E) and `1340d97c` (F+G) on branch `0.9.0` — the changed-file list, the 0.8.4 snapshot originals, Keystone 1.9.0's
`keystone-item` sources and `BeanFactory`. Transcribed by the orchestrator; orchestrator actions are marked **→**.

## Verdict: D–G PASS WITH FIXES

The mechanical migration (D, E, most of F) is clean and faithful: no deleted `gangland-item` class survives anywhere,
NBT keys and registration priorities are byte-identical, `lootchest-api`/`shop-api` still resolve lazily, purity greps
are empty. Four behavioural regressions came from the design parts (F2/F4 ordering, G2 similarity, G5 display, D4
fuel); all compile and pass the suite because nothing tests them.

## Findings

### Blocker

**B1 — `[SELL]`/`[ITEM-SELL]` (and `[BUY]`) now match any stack of the same material, ignoring meta.**
`sign/type/trade/SellSign.java:60`, `BuySign.java:62`. T-G2 replaced `a.isSimilar(b)` with
`ItemDefinitions.sameDefinition(serializers, a, b)`; `ItemPredicates.MATERIAL` is an unconditional catch-all, so every
plain item collapses to `material:<type>` and the `isSimilar` fallback is never reached. `ItemTransferAspect.removeItems`
uses this checker to pick which stacks to delete on a TAKE: a `[SELL] DIAMOND_SWORD` sign takes a Sharpness V renamed
sword, any enchanted book, any potion, a full shulker box, damaged tools as whole — on every placed sign of the
pre-existing types too. Fix: one static helper on `BaseTradeSign` — describe both; if the description is null fall back
to `isSimilar`; if the descriptions differ, false; if the kind is the `material` catch-all, require `isSimilar`;
otherwise same definition. Pin with an enchanted-vs-plain test. **→ Task T-R1 (fix group after gate G).**

### Major

**B2 — the vocabulary fold and the two loader deferrals are unordered `@PostConstruct`s.** `ItemConfig.java:164-176`
vs `GameplayConfig.java:289-309`. `BeanFactory.runPostConstruct` walks the configuration classes in the order
`ReflectionUtil.findClasses` returned them — a `HashSet` — so whether `installItemVocabularies()` runs before
`initializeInventoryLoader()`/`initializeLootChestLoader()` is JVM-dependent. `initializeInventoryLoader`'s javadoc
promises the converters are populated by then; that held in 0.8.4 because the weapon module registered its converter in
a CONFIG `@Bean`, not from a sibling `@PostConstruct`. Blast radius today is small (shipped inventories carry no
`weapon:` refs; loot chests parse per roll), but a server owner putting `weapon:awp` in an inventory YAML hits it on some
JVMs and not others. Fix: use Keystone's guaranteed seam — `beanFactory.instantiate(beforeLifecycle)` runs after every
bean phase and before the `@PostConstruct` pass; call `beanFactory.instantiate(this::installItemVocabularies)` from
`GanglandContext.bootstrap()` with the fold body moved there, keeping the two pinned log strings verbatim.
**→ Task T-R2.**

**B3 — `FuelRefuelListener`'s container→wearable branch now fires in both directions and between two containers.**
`gangland-item/.../listener/fuel/FuelRefuelListener.java:84`. 0.8.4 guarded it with `!Wearable.isRegisteredWearable(cursor)
&& Wearable.isRegisteredWearable(clicked)`; T-D4 dropped both halves, so dragging a jetpack onto a gasoline can drains the
jetpack into the can, and clicking one can onto another pools their fuel and cancels the click. Fix: `FuelContract` (already
injected) gains `default boolean isFuelSink(ItemStack) { return false; }`, implemented gadget-side against the wearable
catalog (group L), and the guard becomes `isFuelItem(cursor) && !isFuelSink(cursor) && isFuelItem(clicked) && isFuelSink(clicked)`
— inert when nothing claims a sink, which is the pre-0.9.0 behaviour for plain containers. **→ Task T-R3 (contract +
guard now; the gadget implementation is noted for group L).**

**B4 — `GanglandShopDisplayResolver` reports a random amount for money items.** `:33-35`. Pristine-first is wrong for
a kind whose converter is non-deterministic: `MoneyItemSerializer.extract` keeps only the variation id, so `pristine` re-rolls
the amount into the display name. In 0.8.4 no provider claimed money and it fell through to the live name. Fix: live
stored display name first, `pristine` second, humanised material last (the only thing pristine-first bought — stripping an
ammo count from a weapon name — left with the weapon module). **→ Task T-R4.**

### Minor

**B5** — T-G1's pin tests Keystone's `sameDefinition` contract against a fake vocabulary and never touches the code T-G2
changed, so the sign similarity behaviour has zero coverage (which is why B1 went unnoticed). **→ Folded into T-R1's test.**
**B6** — `LegacySignRewriter.rewrite(null)` throws (`Map.of`), and placed headers are uppercase while the keys are lowercase.
**→ Sent to X-G-H for T-G4b (null-guard + `toLowerCase(Locale.ROOT)` + two test cases).**
**B7** — `SignManagerContributionTest` never asserts the two new types. **→ Task T-R5 (two assertions).**

### Verified clean

Every deleted class has a used Keystone replacement; `ItemDslAdapter`, `item.wearable`, `WearableEquipService`,
`ShopDisplayNameProvider`, `org.luckyraven.bartizan`, `bartizan-api` are absent from the core (one permitted javadoc
mention). `ItemKind` rewrite exact (`UNIQUE/CAR/MONEY/MATERIAL`, labels unchanged, `material` matches
`StandardItemKind.MATERIAL.label()`). Priorities correct (unique @0, money @0, material @`CATCH_ALL_PRIORITY`; gadget's car
trio at default priority in CONFIG `@Bean`s with registry parameters as ordering edges). NBT keys unchanged. Lazy
resolution intact (`LootTable.generateLoot` parses per roll; `ShopPurchaseService` refreshes per copy). `ShopConfig`'s
registry parameters keep the ordering edge. House rules hold; `settings.yml` `Signs.Legacy_Aliases` block-style with
lowercase alias values; `Settings` reads with the existing helpers. Root pom carries `bartizan-api` in
`dependencyManagement` only; no feature pom declares it yet.

## Vocabulary-fold timing

The fold is a one-shot, non-cached pull during Gangland's `onEnable`; it is never re-run. `weapon:` strings work only if
Bartizan enabled first, which `softdepend: [… Bartizan]` (group M, T-M1) guarantees for enable order — if Bartizan fails
to enable, Gangland logs `Item vocabularies installed: none — weapon:/ammo:/wearable: item strings will not resolve` and
every one of the 167 `weapon:`/`ammo:`/`wearable:` refs in `lootchests/loot_chests.yml` rolls nothing for the session
(restart-required by design; the pinned log line is the contract and must not be softened). Even with M landed, B2 means
"Bartizan enabled first" is necessary but not sufficient — fix B2 before the phase-D smoke rows or a green D1 proves
nothing about ordering.

## Keystone upstream note

`keystone-testkit`'s `RecordingNbtAccessor` (`:65-86`) ignores the `ItemStack` argument and serves every read from one
flat map, so with two live stacks it silently returns the other stack's tags — a real testkit defect. Fix upstream with an
`IdentityHashMap<ItemStack, Map<String,Object>>` (keep `writes`/`removes` for existing assertions; keep `values` as the
most-recent stack's map or add `valuesOf(ItemStack)`), after which `ItemDefinitionSimilarityTest`'s in-test
`PerStackNbtAccessor` can go. **→ Keystone 1.9.1 follow-up, recorded in the wave README.**

## Not verifiable by the reviewer (no shell)

Test counts and greenness (read from the status table); that the supplied file list is complete (it omitted the 20 group-D
deletions, which landed in `66b01acc` — the reviewer grepped for the deleted names anyway); compile against the installed
`keystone-item:1.9.0` jar vs the Keystone working tree; the red state of `ItemDefinitionSimilarityTest` before the accessor
substitution; runtime confirmation of the boot log strings and of `SignTypeRegistry` reporting the two new types.
