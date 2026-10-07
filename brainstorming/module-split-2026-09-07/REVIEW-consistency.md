# Cross-plan consistency review — module split sprint

**Reviewer:** consistency pass, 2026-09-07. **Inputs:** `README.md`, `PLANNER-BRIEF.md`, `EXECUTOR-BRIEF.md`,
`TEMPLATE.md`, `cops-n-crooks.md`, `gadget.md`, `turf.md`, `weapon.md`.
**Graph:** `graphify-out/graph.json` 2026-09-07 16:28 > HEAD `d6bb33ac` 16:11 — fresh.
**Method:** every finding below is checked against the tree at `d6bb33ac`, not inferred from the plans.

---

## Findings

### 1. BLOCKER — cops: nothing creates the `TurfNpcContracts` `@Bean`, so the holder never exists

**Plans/tasks:** `cops-n-crooks.md` T8, T13, §1.3, §1.6 row 4 · knock-on to `turf.md` §1.3, T7.

cops §1.6 row 4 and §6 item 2 both state the holder is "registered by: core `@Bean` in `config/TurfConfig.java`", but
**no task does it**. T8's three steps are: create `TurfNpcContracts`, `git mv` three contract impls, change
`GarrisonDeployListener`'s field type. §1.3's bean table has no `TurfConfig` row. Meanwhile:

- `gangland-features/gangland-turf/.../listener/powerups/GarrisonDeployListener.java:29-33` is
  `@ListenerHandler @RequiredArgsConstructor` with `private final TurfNpcContract npcs;` → after T8 it needs a
  `TurfNpcContracts` bean or the listener is skipped and garrison deploy + Quartermaster engage vanish silently.
- The only `TurfNpcContract` bean today is `config/TurfNpcsConfig.java:72`, which T15 **deletes** from core.
- cops T13's `installCoreSeams()` calls `context.get(TurfNpcContracts.class).install(...)` → NPE with no bean.

**Resolution:** add a step 4 to **cops T8**: in `gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfConfig.java`
add `@Bean public TurfNpcContracts turfNpcContracts() { return new TurfNpcContracts(); }` plus the import. Add the row
to cops §1.3 and §4. Then add the same bean to **turf.md §1.3** — that table enumerates 20 beans and turf T7 says
"all **20** `@Bean` methods, verbatim"; after flip 1 the file has **21**. Change turf T7 to "every `@Bean` method in
the file (20 today + `turfNpcContracts()` added by flip 1 = 21)".

### 2. BLOCKER — cops: `TurfSelectionResolver` is package-private and cops T12 moves its only cross-package caller

**Plans/tasks:** `cops-n-crooks.md` T12 step 3 · `turf.md` T0/D, T3 step 6, T9 step 2, §6 Q1.

`TurfSelectionResolver.java:24` is `final class TurfSelectionResolver` (package-private) with a package-private
`static @Nullable Turf resolve(...)` at `:29`. `TurfPowerupNpcCommand.java:61` calls it. cops T12 step 3 moves
`TurfPowerupNpcCommand` to `org.luckyraven.gangland.copsncrooks.command.turf` and says only "make the class and
constructor `public`" — the `resolve` call then does not compile. T12's own "Done when" is a grep on
`gangland-impl/.../command/`, which passes anyway, so the error only surfaces at G1 after nine more tasks.

turf.md already assumes flip 1 fixed this (`T0/D`: "flip 1 must have widened it if C moved"; §6 Q1 option A:
"widened `TurfSelectionResolver` to public"). The two plans disagree about who owns the edit.

**Resolution:** cops T12 step 3 gains: "widen `gangland-impl/.../command/sub/turf/TurfSelectionResolver.java` — class
→ `public final`, `resolve(...)` → `public static`. It stays in `command/sub/turf/` for this flip; flip 3 T3 moves it
to `org.luckyraven.gangland.turf.command`." Add the file to cops §1.1 and §4 as a KEEP-with-visibility-change.

### 3. BLOCKER (flip 2) — gadget T4 resolves `SignContributions` after the point `ViewSign` is constructed

**Plans/tasks:** `gadget.md` T4 steps 2–4 · depended on by `weapon.md` T11 step 2.

T4 step 2 says to add the contributed-signs loop "**At the end of `setupSigns()`, before `return definitions;`**",
creating the local `SignContributions contributions = SignContributions.from(container);` there. T4 step 3 then makes
`ViewSign` take a `SignContributions` and pass it to `ViewInventoryAspect` — but `SignManager.java:147` constructs
`new ViewSign(gangland, weaponService, ammunitionManager, carManager, wearableService, uniqueItemAddon, viewType)`
**mid-method**, well before "the end". As written the local is not in scope at the call site.

**Resolution:** gadget T4 step 2 must say: resolve `SignContributions contributions = SignContributions.from(container);`
**once at the top of `setupSigns()`** (the lazy-vs-constructor point the task makes is preserved — it is still inside
the method, not the constructor), pass that local into `new ViewSign(...)`, and reuse it for the contributed-signs
loop at the end.

### 4. SHOULD-FIX — gadget addresses `config/CopsAndGadgetsConfig.java` by a name flip 1 deletes

**Plans/tasks:** `gadget.md` T1 step 5, T2 step 3, T10 step 6, §1.1 row, §1.3 rows, §4 table, OQ-1 ·
`cops-n-crooks.md` T14.

cops T14 does `git mv CopsAndGadgetsConfig.java → GadgetConfig.java` (README decision log, "gadget OQ-1"). gadget then
names the old path in six places, including three *task steps* an executor follows literally. `EXECUTOR-BRIEF.md`
rule 2 tells the executor to **stop the task** when the named file differs from the tree — gadget's OQ-1 discovery
fallback lives in §6, which is not where the executor is looking.

**Resolution:** rewrite all six mentions to `gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java`.
gadget T1 step 5 and T2 step 3 lose their line numbers (`:61`, `:63`) — locate by symbol. gadget T10 step 6 becomes
"delete the four gadget beans from `config/GadgetConfig.java`; the file is then empty — delete it." Reduce OQ-1 to a
one-line T10 pre-check.

### 5. SHOULD-FIX — weapon T9 changes `CarBuySign`/`CarSellSign` signatures but not gadget's `CarSignContribution`

**Plans/tasks:** `weapon.md` T9 step 2, §1.1 rows 45–46 · `gadget.md` T12 steps 2 and 4.

gadget T12 step 2 creates `gadget/sign/CarSignContribution.java` whose `signs(...)` calls
`new CarBuySign(userManager, carManager, weaponService, ammunitionManager, carBuyType)`, and step 4 declares
`@Bean carSignContribution(@Qualifier("online") UserManager<Player>, CarAddon, WeaponService, AmmunitionManager)`.
weapon T9 step 2 drops the `WeaponService`/`AmmunitionManager` constructor parameters from the car signs and only says
"at whichever module T0 found the car signs in" — it never names `CarSignContribution` or `GadgetModuleConfig`. Result:
arity error in the gadget module, plus (until then) a needless gadget→weapon bean edge.

**Resolution:** weapon T9 step 2 gains: "also strip `weaponService`/`ammunitionManager` from
`gangland-features/gangland-gadget/.../gadget/sign/CarSignContribution.java` and from its `@Bean` in
`GadgetModuleConfig`." Add both files to weapon §1.1 and §4, and add a T0 check for them.

### 6. SHOULD-FIX — weapon T10 invalidates gadget's `SignManagerContributionTest` and never re-baselines it

**Plans/tasks:** `weapon.md` T10, T23 · `gadget.md` T12 step 5, §3 item 3.

gadget T12 step 5 writes `gangland-impl/src/test/.../sign/SignManagerContributionTest.java` asserting the contributed
definition is appended **and that the core definitions are still present**, computing "the core count is 11" (verified:
`SignManager.java` builds 13 `SignType`s today at `:92,101,110,119,128,137,146,156,165,174,184,194,203`; minus the two
car rows = 11). weapon T10 removes six more (weapon-buy/sell, ammo-buy/sell, wearable-buy/sell) → 5. weapon's task list
has no entry for that test; `mvn test` at gate H goes red.

**Resolution:** two edits. (a) gadget T12 step 5: assert *containment* (the contributed definition is present, and
`glw-buy`/`glw-sell`/`glw-view`/`glw-wanted`/`glw-bounty` are present) rather than a total count — those five survive
every flip. (b) weapon T10 gains a step: "re-run `SignManagerContributionTest`; if it still asserts a total, set it to
5." This also gives finding 10 its missing verification.

### 7. SHOULD-FIX — cops T13's "Done when" is not a runnable command

**Plans/tasks:** `cops-n-crooks.md` T13.

`grep -rn "class CopsAndGadgetsConfig" -A400 gangland-impl/.../config/CopsAndGadgetsConfig.java | grep -c copsncrooks`
uses `-r` on a single file, `-A400` on a `-rn` invocation, and names a file that the *next* task renames.

**Resolution:** replace with, run after T14:
`grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java` → `0`, plus
`grep -c "\.install(" gangland-features/cops-n-crooks/.../config/CopsNCrooksModuleConfig.java` → `4`.

### 8. SHOULD-FIX — weapon's refresher priority reproduces weapon-before-unique but breaks unique-before-ammunition

**Plans/tasks:** `weapon.md` §1.6(a) "Refreshers", T19 (`weaponItemRegistrations`), §6 C-1 · `gadget.md` T11 step 5.

Verified today at `ItemConfig.java:182-191`: `register(weaponRefresher, wearableRefresher, uniqueItemRefresher,
ammunitionItemRefresher, carItemRefresher)` — first-match-wins in that order. weapon registers **all three** of its
refreshers at priority 10, so the post-flip order is `weapon, wearable, ammunition` (10) then `unique, car` (0).
**Ammunition now outranks `UniqueItemRefresher`**, where today unique wins — so a unique *ammunition* stack would be
rebuilt as plain ammunition. That is the exact mirror of the regression C-1 exists to prevent, and weapon §1.6(a)
asserts the change "reproduces today's weapon/wearable-before-unique order exactly", which is true only for two of the
three.

**Resolution:** weapon T19 registers `weaponRefresher` and `wearableRefresher` at priority `10` and
`ammunitionItemRefresher` at the **default** `0`. Being registered after the core bean, it lands after
`uniqueItemRefresher` in the stable sort — exactly today's order. Update §1.6(a) and §6 C-1 to say so.
(The serializer analysis in §1.6(a) is fine as written: `UNIQUE` stays ahead of `WEAPON` via the stable sort and
`MATERIAL` stays last via `CATCH_ALL_PRIORITY`.)

### 9. SHOULD-FIX — turf T8 describes the `resources/turf/` directory and `KernelConfig` lines as flip 1 does *not* leave them

**Plans/tasks:** `turf.md` T8 steps 2, "Done when" · `cops-n-crooks.md` T17 steps 1–2.

cops T17 moves `turf/turf_npcs.yml` into the cops module and deletes `KernelConfig.java:188`. So when turf T8 runs:
`gangland-impl/src/main/resources/turf/` holds **only `turf_powerups.yml`** (turf's done-when expects "turf_npcs.yml
only"), and there is no line 188 to "leave alone" — the surviving `turf_powerups` registration has shifted up from 187.

**Resolution:** turf T8 step 2 → "delete the `turf_powerups` `fm.addFile(...)` line from `KernelConfig.java` (locate by
symbol; flip 1 already removed the five `npc/*` + `turf_npcs` lines and shifted the numbering)". Done-when → after the
move `gangland-impl/src/main/resources/turf/` is empty; delete the directory and assert
`test ! -d gangland-impl/src/main/resources/turf`. Same clean-up note belongs in turf §4.

### 10. SHOULD-FIX — four "done when"s an executor cannot actually run

**Plans/tasks:** `weapon.md` T2, T10, T26 · `cops-n-crooks.md` T4.

- **weapon T10:** "with zero modules, `SignTypeRegistry.getDefinitions()` contains exactly `glw-buy`, `glw-sell`,
  `glw-view`, `glw-wanted`, `glw-bounty` (5 entries)" — there is no command or test that produces this. Fix via
  finding 6 (`SignManagerContributionTest`).
- **weapon T26:** "`ModuleLoader` logs weapon before cops and gadget…; removing only `gangland-weapon-0.8.4.jar`
  yields `module.dependency.missing`" — requires a running server. Fix: done-when becomes
  `unzip -p target/modules/cops-n-crooks-0.8.4.jar module.yml` / `…gangland-gadget-0.8.4.jar module.yml` showing the
  block lists plus `mvn clean package -DskipTests` green; the boot behaviour is already listed under §5 "Manual boot
  checks" (G6) and belongs only there.
- **weapon T2:** "`git status` shows nothing else changed" — restate as the five file paths existing.
- **cops T4:** "both files compile in isolation" — the reactor is red by design from T1 to T17. Restate as
  `grep -c copsncrooks` == 0 on both new files, deferring compilation to G1.

### 11. NOTE — line numbers in later plans are stale once an earlier flip has edited the same file

**Plans/tasks:** `gadget.md` T6 step 1 (`gangland-impl/pom.xml:99-102`) · `turf.md` T8 (`KernelConfig.java:187/188`,
covered in 9) · `weapon.md` §1.1 rows 15/16/18/37/38 and T20 steps 1–5.

Verified `gangland-impl/pom.xml` today: weapon `:57`, cops `:89`, gadget `:101`, turf `:109`. cops T1 deletes the block
at `:89` first, so gadget's `:99-102` has moved. Likewise cops T15 deletes 16 beans + three import blocks from
`ShopConfig.java`, and gadget T4 deletes the car blocks from `SignManager.java`/`GameplayConfig.java`, before weapon
reads its own line references. Every affected **done-when** is grep-based so nothing silently passes — but the *Do*
steps are line-addressed.

**Resolution:** one sentence in `EXECUTOR-BRIEF.md` (or each plan's §2 preamble): "line numbers are as of `d6bb33ac`;
after an earlier flip has touched the file, locate by symbol name and record the drift in §7."

### 12. NOTE — three different heading names for the same new section of `documentation/module-loader.md`

cops T19 adds "**Seams the core exposes**", gadget T15 adds "**Seams**", weapon T27 adds "**Core seams**". Three flips
will create three sections instead of extending one. (The file today has no such heading —
`documentation/module-loader.md` headings are: What is a module today / How the core loads modules / Writing a module /
Attaching sub-arguments under a core command / Faults you will see in the console / Smoke checklist.)

**Resolution:** fix the heading to `## Core seams` in cops T19; gadget T15 and weapon T27 say "append rows to the
existing **Core seams** section".

### 13. NOTE — `Depends:` list indentation differs between the two plans that write it

`turf.md` T9 step 1 writes `Depends:` then `   - turf` (three spaces); `weapon.md` T26 writes `  - turf` /
`  - weapon` (two). `gangland-mail`'s `module.yml` has no `Depends` key, so there is no precedent to inherit.

**Resolution:** make turf T9 use two-space indent so weapon T26 is a pure one-line append. Both are already correct on
the important point (block list, never `[turf, weapon]` — memory rule *yaml inline braces*).

### 14. NOTE — `InformationManagerTest` arithmetic is sound; weapon T25's worked example is dead

Verified against `gangland-impl/src/main/resources/commands.json` (**225** top-level keys today):

| flip | keys | verified | running total |
|---|---|---|---|
| cops | 43 listed, 43 unique, all present | ✔ | 225 → **182** |
| gadget | 5 (`car`, `car_help`, `car_give`, `car_info`, `car_list`) | ✔ | → **177** |
| turf | 17 listed; `turf_powerupnpc` goes to cops → **16** | ✔ | → **161** |
| weapon | 12 | ✔ | → **149** |

The four key sets are **disjoint apart from the deliberate `turf_powerupnpc`** (cops takes it in its 43; turf drops to
16 — both plans state this and agree). cops's hard-coded `225 → 182` is right; gadget/turf/weapon all correctly say
"recompute from the file".

The one defect: **weapon T25 and §3 say "If flips 1–3 left it at 225, the new value is 213"** — flips 1–3 cannot leave
it at 225. Replace with "expect **149**; recompute with the `python -c len(json.load(...))` one-liner and use that."

### 15. NOTE — task-size and file-count bookkeeping

No task exceeds the ~25-file guidance. Largest: cops T12 = 23, weapon T21 = 24 (mechanical YAML `git mv`s),
cops T11 = 22. weapon **Group E** totals 30 files but is four separate tasks.

One count error: **weapon T15's header says "13 files"** but the steps enumerate 9 moves (6 signs + 3 validators) plus
7 new classes = **16**. Cosmetic; correct the header.

Also worth renaming for safety: cops creates `CopsNCrooksFilesConfig` (KERNEL, T17) and `CopsNCrooksFileConfig`
(FILE, T16) — a one-letter difference in two class names an executor must register in the right order in T2 and assert
in T18. Suggest `CopsNCrooksYamlConfig` (KERNEL) / `CopsNCrooksFileConfig` (FILE).

### 16. NOTE — README's `p0-wave-3` file list is narrower than what gadget found

README lists: `config/CopsAndGadgetsConfig.java`, `config/GameplayConfig.java`, `command/sub/gang/*`,
`command/sub/waypoint/*`, `command/sub/debug/DebugCommand.java`, `data/placeholder/worker/GanglandPlaceholder.java`,
`file/configuration/Messages.java`, `sign/GanglandSignInformation.java`, gadget `listener/car/*`, `message_*.yml`.

`gadget.md` §0 additionally reports (from `git status --porcelain` in the worktree) new files
`gangland-impl/.../gadget/GanglandCarGangs.java`, `gadget/car/access/{CarAccessPolicy,CarGangContract}.java`,
`sign-api/.../sign/{SignPermissions,listener/SignProtection}.java` and modifications to sign-api's
`SignCreation`/`SignInformation`. cops T14 independently knows about the `carAccessPolicy` bean, so the two plans do
agree — but the README list is the one a cops or weapon executor reads.

**Resolution:** update README's p0-wave-3 bullet with gadget's fuller list (adds included), so weapon T10's
`SignManager`/sign-api work and cops T14's `carAccessPolicy` preservation are both visible from the one source.

---

## Verified consistent (no action)

- **Seam-shape rule (README "contributions vs holders"): respected by all four plans.** cops uses holders
  (`GanglandMoneyDropClassifier`, `BankTiers`, `WantedKillTrackers`, `TurfNpcContracts`) and explicitly *removes* the
  core `ShopUiSettings` bean to avoid the `TraderSettings extends ShopUiSettings` hierarchy collision; gadget and
  weapon use contributions pulled with `getAllInstances`. **No plan registers a second bean of an interface the core
  already publishes.** (One pre-existing wrinkle is carried forward, not introduced: `WeaponModuleConfig.wearableService`
  returns the same `WearableAddon` instance that already registers under `WearableService` — identical to today's
  `GameplayConfig:212`, harmless because it is one object.)
- **`@PostConstruct` timing.** `BeanFactory.instantiate` runs the `@PostConstruct` pass over **all** configs then **all**
  beans (`Keystone/keystone-bean/.../BeanFactory.java:292-293`) after every phase, then the convention `initialize()`
  pass (`:294`). cops T13's cross-config `installCoreSeams()` reaching `BankerModuleConfig`'s `BankTierRegistry` is
  safe, and gadget's claim that `SignManager.setupSigns()` sees every module bean holds.
- **cops's `BankTiers` lambda is behaviour-exact.** All three consumers already fall back to `first()`:
  `BankDepositCommand.java:209-213`, `GanglandPlaceholder.java:233-234`, `PlayerDeathListener.java:173-174`.
- **`gangland-build/pom.xml`.** Today: one mail `<exclude>` (`:79`), one `<artifactItem>` (`:113-117`), one `provided`
  dependency (`:137-139`). Each flip adds exactly one of each "beside the mail one" → four each after flip 4, each
  owned by exactly one task (cops T1, gadget T6, turf T1, weapon T1). No double-add.
- **`module.yml` `Depends:` ownership.** cops `[]` → `[turf]` (turf T9) → `[turf, weapon]` (weapon T26); gadget `[]` →
  `[weapon]` (weapon T26); turf and weapon never gain one. Every edit owned by exactly one task, and every plan's
  header states the same end state.
- **Sign seam signatures are byte-identical between gadget §1.6(c)–(e) and weapon §1.6(b)**
  (`List<Sign> signs(String)`, `boolean open(Player, String)`, `from`/`none`/`createSigns`/`openView`), as is the
  `ItemSerializerRegistry.register(predicate, serializer, priority)` + `CATCH_ALL_PRIORITY` + stable `List.sort`
  contract. weapon correctly says "do not add a second loop" in `setupSigns()`.
- **`ViewInventoryAspect` hand-off.** gadget leaves it with the wearable/weapon/ammo branches and `uniqueItemAddon`;
  weapon T11 removes the last of them and flags the larger-than-anticipated simplification in §6 R-2. Consistent.
- **`turf/turf_npcs.yml`, `TurfNpcsConfig`, `TurfNpcsConfigLoader`, `TurfPowerupNpc{Repository,Table}` ownership.**
  Both cops (§6 item 2) and turf (§6 Q2) independently reach "all cops', no turf remainder", matching README turf-Q2.
- **`TurfPowerupNpcCommand` as a `CommandContribution` at parent `turf`.** cops T12 builds it; turf T0/C, T3 step 5 and
  §6 Q1 option A expect exactly that. Consistent (only the resolver visibility, finding 2, is missing).
- **`WearableAddon` package after relocation** (`org.luckyraven.gangland.weapon.wearable`), `FuelService` →
  `gangland-infra/gangland-item`, `items/wearables.yml` staying in core through flip 2, and weapon T1 preserving
  gadget T2's weapon-pom additions — all four consistent across gadget and weapon.
- **Split-package hazards** are each owned once: `gangland-impl/.../gadget/GanglandCarGangs.java` (gadget T10),
  `gangland-impl/.../weapon/WeaponManager.java` (weapon T13), `file/configuration/copsncrooks/` renamed to
  `wanted/` (cops T16).
- **`CLAUDE.md` / `documentation/module-loader.md` flip-order chain**: cops → "gadget → turf → weapon", gadget →
  "turf → weapon", turf → "weapon", weapon → paragraph deleted. Consistent apart from finding 12.
- **turf has genuinely zero `p0-wave-3` overlap** (it edits none of the listed files and only *reads* `Messages.java`).

---

## Verdict

**Flip 1 cannot start entirely as written — but the patch is three small edits, all inside `cops-n-crooks.md`:**

1. **T8** — add step 4: register the `TurfNpcContracts` `@Bean` in `config/TurfConfig.java` (finding 1). Without it the
   flip boots with garrison deploy dead and `installCoreSeams()` throwing.
2. **T12 step 3** — widen `TurfSelectionResolver` and its `resolve` method to `public` (finding 2). Without it G1 never
   goes green and the failure appears nine tasks late.
3. **T13 "Done when"** — replace the malformed grep (finding 7).

Everything else in flip 1 — the 43 `commands.json` keys, the 225 → 182 count, the four holder seams, the two
contributions, the five YAML moves, the p0-wave-3 flags on T5/T14 — checks out against the tree. With those three
edits, **flip 1 is ready to execute.**

**Patch before flip 2 (gadget):** finding 3 (hoist `SignContributions.from(container)` to the top of `setupSigns()` —
blocker), finding 4 (rename all six `CopsAndGadgetsConfig` references to `GadgetConfig`), finding 6a (make
`SignManagerContributionTest` assert containment, not a count), finding 11 (line numbers).

**Patch before flip 3 (turf):** finding 1's knock-on (§1.3 and T7 must carry 21 beans, not 20), finding 9
(`resources/turf/` and `KernelConfig` end state), finding 13 (`Depends:` indentation). turf's T0 reconnaissance task is
the strongest device in the sprint and already catches most drift — keep it.

**Patch before flip 4 (weapon):** finding 5 (`CarSignContribution` arity — blocker for the gadget module),
finding 6b (`SignManagerContributionTest` re-baseline), finding 8 (register `ammunitionItemRefresher` at the default
priority, not 10 — otherwise a unique ammunition stack regresses), finding 10 (three unrunnable done-whens),
finding 14 (expected count is **149**, not 213), finding 15 (T15 file count).

**Sprint-level:** update README's `p0-wave-3` file list (finding 16) and add the "line numbers are as of `d6bb33ac`"
sentence to `EXECUTOR-BRIEF.md` (finding 11) before any executor starts.
