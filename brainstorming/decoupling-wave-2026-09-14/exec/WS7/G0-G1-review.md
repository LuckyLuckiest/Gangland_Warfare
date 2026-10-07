# Review — WS7 G0 (Bartizan 0.4.0) + G1 (Gangland 0.9.2) + D4 design — 2026-09-16 (Opus, transcribed by the orchestrator)
Verdict: FIX (9 findings — 2 Critical, 4 Important, 3 Minor). G0/G1 as *shipped code* are clean; every blocking finding is against the D4 design and the api hook G0 landed for it.

## Spec table
| Gate/step | | |
|---|---|---|
| G0.1 branch off `0e9e456` | ✅ | diff header confirms HEAD `0e9e456`. |
| G0.2 delete `jetpack:` + `FUEL_EFFICIENT` comment | ✅ | `wearables.yml` −33 lines, comment gone. |
| G0.3 delete `legacyJetpackToExtraTags` + test | ✅ | also removed `sectionToMap`/`asConfigurationSection`/the `readExtraTags` `Jetpack:` branch. The plan's "`readExtraTags()` needs no parser change" (§0b B1 row) was **defective** — the branch called the deleted method, so it could not compile. Lead was right; plan defect, not a finding. |
| G0.4 `<revision>` → 0.4.0 | ✅ | `pom.xml:483`. |
| G0.5 `migration.md` §3.5 + `bartizan-api.md` pointer | ⚠️ | content present but numbered `## 12`, not §3.5 (Minor M2). |
| G0 test column ("existing wearable tests green") | ✅ | 304 green per report; not re-run. |
| G0 + D4 api hook (batch table carve-out) | ❌ | shipped, but **not trivial** — see C1/C2/I1/I2. |
| G1.1 branch `0.9.2` off `0.9.1` | ✅ | HEAD `fb460b35`. |
| G1.2 `isBartizanAvailable()` + `VERSION` → 1.1 | ✅ | `Settings.java:569-571`, `GanglandApi.java:522`. |
| G1.3 `ItemKind.JETPACK` | ✅ | `ItemKind.java:609`. |
| G1.4 root pom `bartizan.version` → 0.4.0 | ✅ | |
| G1.5 gadget `module.yml Host_Api` → 1.1 | ✅ | verified 1.1 on gadget, 1.0 on the other five — Keystone 1.9.2's minor-floor accepts all. |
| G1 build (whole reactor) | ⚠️ | 814 tests green per report; cannot re-run. |
| Red-first | ✅ n/a | plan's G0/G1 test columns require no new/flipped test. |
| §11 docket | ✅ | none land in G0/G1; report states so. |

## Findings — the D4 "other consumers of a catalog `Wearable` / the `wearable` tag" audit
The design's claim is only true of the **damage-reduction/effects** chain (verified: `WearableService.java:229`, `:377` → `getEffects()` short-circuits on `EffectsData.empty()`; `traitLevel` null-checks `traits`). It registers into the **same map every other consumer reads**, and stamps the raw tag on every jetpack unconditionally.

**Critical 1 — a minimally-built `Wearable` throws on every `buildItem()` path.**
`Wearable.buildItem` (`bartizan-api/.../wearable/Wearable.java:344`) does `new ItemBuilder(material)` → `new ItemStack(null)` (Keystone `ItemBuilder.java:60-62`) → throws. Three registered paths reach it for a jetpack once the bridge registers:
- `item/WearableRefresher.java:24,33,36` — `canRefresh` is *any* `wearable` tag; registered at **priority 10** (`item/BartizanItemVocabulary.java:60`), ahead of the planned gadget `JetpackItemRefresher`. Every shop/trader delivery of a jetpack throws. (Unregistered case is safe: `refresh` returns null and `ItemRefresherRegistry.java:62-70` falls through.)
- `item/WearableConverter.java:32,36` — `wearable:<jetpackId>` loot/shop string → throws (unregistered → silently null item).
- `command/wearable/WearableGiveCommand.java:64,100,104` — key appears in tab-completion, `buildItem(player)` throws.
Also `WearableInfoCommand.java:72` `wearable.getMaterial().name()` → NPE.
*Smallest fix (reviewer):* build the bridge's `Wearable` **fully**, and only stamp the `wearable` tag when the bridge actually registered.

**Critical 2 — the jetpack becomes unequippable for non-op players.**
`listener/wearable/WearableEquipListener.java:44-59` cancels the equip when `player.hasPermission(wearable.getPermission())` is false. `Wearable.java:298-301` derives `"bartizan.wearables." + wearableKey` for any non-`temporary` wearable, and the node is registered with Bukkit **only** on the YAML path (`WearableAddon.java:284`) — an externally-registered key is never registered, so a default player is silently blocked from equipping their own jetpack whenever `Bartizan_Traits:` is configured.
*Smallest fix (reviewer):* `.temporary(true)` (`getPermission()` → null), or the bridge registers the node itself.

**Important 3 — serializer collision, wrong string persisted.**
D4 step 2 stamps `wearable=<id>` on *every* jetpack, Bartizan present or not. `BartizanItemPredicates.WEARABLE` + `WearableItemSerializer` are registered at priority **0** (`BartizanItemVocabulary.java:57`), the same tier the planned gadget `JetpackItemSerializer` will use, so which one claims the stack is registration order. If Bartizan wins, shops/lootchests persist `wearable:<id>`, which `WearableConverter` then resolves to null (no `Bartizan_Traits:`) — re-creating exactly the §8 Risk-3 data loss this wave set out to close.
*Smallest fix (reviewer):* stamp the tag only when the bridge registered, and give the gadget serializer a priority above 0.

**Important 4 — shipped api doc asserts a contract the code does not honour.**
`documentation/bartizan-api.md` (new "External wearable registration") and `WearableCatalog.java:40-45` tell any third party a minimally-built `Wearable` is safe — false for `buildItem`/refresher/converter/give/info.

**Important 5 — no re-registration when Bartizan enables after gadget.**
The bridge registers once at `JetpackAddon` load inside `isBartizanAvailable()`. `WearableAddon.load()` never calls `WearableService.clear()` (verified), so a Bartizan config reload does **not** drop the entry — only a Bartizan restart does.

**Important 6 — the api addition ships with zero test.** No Bartizan test covers "external registration → `resolveWearable` → `applyWearableReduction`".

**Minor 7** — `WearableAddon.java:10-11`: `ConfigurationSection` and `MemoryConfiguration` imports unused; `:105` javadoc still `{@link #sectionToMap}` (deleted member).
**Minor 8** — migration note numbered `## 12` where plan §2/§4 said §3.5.
**Minor 9** — `WearableListCommand.java:40` prints literal `null` for an externally-registered wearable's name.

## Cannot verify
- Both build/test runs (304 Bartizan, 814 Gangland) — report is the evidence.
- Branch names/parents — only HEADs are in the package.
- `new ItemStack(null)`'s exact exception type on the pinned API; it throws either way.

## Notes for the orchestrator
- The G0 api hook is not "trivial" as the batch table meant it. Do not let batch 2 build the bridge until the shape is settled.
- Plan defect: §0b B1's "readExtraTags needs no parser change" is wrong (it called the deleted method).
- Root `<revision>` still 0.9.1 (addressed by ruling W10 before this review landed).
- Docket candidates: (a) `WearableRefresher.canRefresh` claims any `wearable`-tagged stack but `WearableService.getWearable` can miss → a foreign/stale tag silently falls through the refresh chain (pre-existing); (b) externally-registered wearables get an unregistered permission node — adjacent to open GD-07.
- Cross-gate: G3's `JetpackItemSerializer`/`JetpackItemRefresher` priorities are now load-bearing (I3/C1).

## Orchestrator rulings on this review (W11)
- C1/C2/I3/I4/I9 → one shape: `Wearable` gains `external` (builder default false). External wearables are *damage-only*: `getPermission()` returns null when external (C2); `WearableRefresher.canRefresh`, `WearableConverter`, `BartizanItemPredicates.WEARABLE`/`WearableItemSerializer`, `WearableGiveCommand` (give + tab), `WearableInfoCommand`, `WearableListCommand` all skip external entries (C1, I3, M9). Bartizan never rebuilds, converts, gives, lists or serialises a foreign item — that is the "Bartizan handles reference for existing gadgets" the user asked for, without Bartizan producing a jetpack that lacks gadget's fuel NBT (which "build the Wearable fully" would have caused through the refresher/converter). Javadoc + bartizan-api.md reworded to the real contract (I4).
- I3 (gadget side, batch 2): stamp `wearable=<id>` only when the bridge registered; `JetpackItemSerializer`/`JetpackItemRefresher` registered at a priority above Bartizan's 10 — pinned in the batch-2 dispatch.
- I5: no `PluginEnableEvent` hook — Gangland's `plugin.yml` `softdepend: [Bartizan]` makes Bukkit enable Bartizan before Gangland, and modules load inside `Gangland.onEnable`, so a present Bartizan is already enabled at `JetpackAddon` load; a runtime plugin manager enabling Bartizan later loses the traits until `/glw reload` — stated in D4-design.md. Cost if wrong: traits missing under PlugMan-style late enable only.
- I6: `WearableExternalRegistrationTest` in Bartizan (round trip + the skip guards), red-first.
- M7: fix. M8: parked — migration.md's numbering is sequential, "§3.5" in the plan predates the doc's structure; `## 12` stands. Docket candidate (a) → Bartizan finding file at G6; (b) → GD-07 note at G6.
