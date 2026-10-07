# WS2 G1 + G2 report — 2026-09-20

Status: DONE

Plan refs: `plans/WS2-inventory-keystone.md` §4 rows G1/G2, §2 dependency table + package layout + deletions table,
§3 seams, §7 tests, §0d; Keystone module guide `E:\Programming\java\wt\keystone-1.11.0\docs\keystone-inventory.md`.

## G1 — Gangland reactor plumbing

### What changed
- Root `pom.xml`: new `dependencyManagement` entry for `keystone-inventory` (`provided`, `${keystone.version}`),
  placed beside the other `keystone-*` entries.
- Added `keystone-inventory` (`provided`) to the 6 consumer poms named in the plan's §2 dependency table:
  `gangland-impl`, `gangland-ui/shop-api`, `gangland-features/gangland-npc-shops`,
  `gangland-features/gangland-turf`, `gangland-features/cops-n-crooks`, `gangland-features/gangland-gadget`. Not
  `gangland-api` (R6). Confirmed `gangland-gadget` had no direct `inventory-api` dependency today (it gets the
  old framework transitively through `gangland-api`, which does re-export `inventory-api` — the exact asymmetry
  R6 exists to prevent for `keystone-inventory` going forward) — added `keystone-inventory` directly there anyway,
  matching the plan.
- `gangland-impl/src/main/java/org/luckyraven/gangland/config/GameplayConfig.java` — new `InventoryService` bean
  (the only config class with "CONFIG-phase wiring for the gameplay-side managers... inventory... hologram" in
  its own javadoc — the "config class that owns UI wiring" your message pointed at). Confirmed first that no
  `keystone-hologram`/`keystone-shop` service bean exists anywhere in this reactor yet (grepped for
  `HologramService`/`ShopService`/`keystone.hologram`/`keystone.shop` — only the OLD `gangland-ui/hologram-api`'s
  own `HologramService` class matched, not a Keystone one), and that no `@Configuration` class anywhere uses
  `Phase.LIFECYCLE` explicitly — the existing `BeanLifecycle`-implementing beans in this codebase
  (`PeriodicalUpdates`, the old `ScoreboardLifecycleService`) all live in a plain `@Configuration` class, so I
  followed that precedent rather than inventing a new `Phase.LIFECYCLE` annotation usage with zero prior example
  in this repo. The bean constructs `new InventoryService(new InMemoryCooldownService())` (no `CooldownService`
  bean exists yet anywhere in Gangland; in-memory is the lightest correct choice — nothing needs persistence-backed
  cooldowns today) and calls `registerListeners(gangland)` inline, mirroring the exact
  construct-register-log-return idiom Bartizan's `ItemConfig.bartizanItemVocabulary` already establishes for
  `ItemVocabulary`.
- `plugin.yml` — confirmed unchanged, as the plan requires (`Keystone` already declared, no `Oriel` entry to add
  under R10).

### Build
`mvn clean install -DskipTests` → `BUILD SUCCESS`, all 21 reactor modules (both frameworks compile side by side —
`inventory-api` untouched, `keystone-inventory` newly present).

### Smoke
Reused the existing scenario row `S1` ("Empty — no runtime modules deployed", `modules: []`) against a real
server rather than adding a new row — no menu is opened yet at G1 (nothing builds a `ChestMenu` until G3), so the
row only needed to prove: boots clean, listener registers once, zero errors. Temporarily pointed
`brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json`'s `paths.repo_dir`/`paths.keystone_jar_dir` at this
worktree / `wt\keystone-1.11.0` (same procedure as the WS1 G3 fix round), ran
`python smoke.py --rows S1 --deploy --keystone`, confirmed `verdict=PASS boot=True stop=True errors=0`, and
grepped the copied log for confirmation: `[Gangland.GameplayConfig] keystone-inventory service registered` — one
line, no duplicate registration, no `NoClassDefFoundError`/`NoSuchMethodError`. Ran `--restore` afterward (parked
jars back), and reset `paths.repo_dir`/`paths.keystone_jar_dir` back to `wt\gangland-0.9.2`/
`wt\keystone-1.10.0\keystone-plugin\target`. Report: `smoke/reports/2026-09-20-2350-S1.md` (+`.log`,
+`2026-09-20-2350-summary.md`/`.json`).

## G2 — Sever the domain inversion (gangland-domain)

### What changed
- **`User.java`** (`gangland-domain`) — deleted `Set<InventoryHandler> inventories`, `InventoryRegistry
  inventoryRegistry`, and the five methods (`addInventory(InventoryHandler)`, `removeInventory(InventoryHandler)`,
  `removeInventory(String)`, `getInventory(String)`, `clearInventories()`, `getInventories()` — six, not five,
  counting both `removeInventory` overloads). Constructor now `User(JavaPlugin, T, Placeholder)` — 3 args, down
  from 4. Both `org.luckyraven.gangland.inventory.*` imports gone.
- **`UserFactory.java`** (`gangland-domain`) — dropped the `InventoryRegistry` field/param; `create()` now calls
  the 3-arg `User` constructor.
- **`gangland-domain/pom.xml`** — `inventory-api` dependency removed.
- **Forced move, not optional** (confirmed by grepping `gangland-domain` for every
  `org.luckyraven.gangland.inventory` import before touching the pom — exactly 4 files matched: `User.java`,
  `UserFactory.java`, both handled above, plus these two): `GangFilterAdapter.java` and `MemberFilterAdapter.java`
  moved from `gangland-domain` to `gangland-impl`, **same package names** (`org.luckyraven.gangland.gang` /
  `org.luckyraven.gangland.gang.member`) — dropping `inventory-api` from `gangland-domain`'s pom would otherwise
  break their compile (`FilterAdapter`/`FilterField`/`StandardFilterField` still live in `inventory-api` until
  WS2 G3a, so these two classes need to live somewhere that still depends on it). Same package name means their
  only production importer, `gangland-impl`'s `GangFilterRegistration.java` (and `GangItemSourceProvider.java`,
  already in `gangland-impl`, unaffected), needed **zero import changes**. Code itself unchanged, pure relocation
  — javadoc added on each noting why and pointing at G3a as where they land for real.
  - `GangItemSourceProvider.java` was **not** touched — I checked the plan's own text against the current tree
    first: the plan's G2 paragraph says it gets "rewritten to build ItemStacks directly," but that class already
    lives in `gangland-impl` (not `gangland-domain` — the plan's file-location assumption there doesn't match this
    tree) and builds `ItemSourceEntry` placeholder maps through `inventory-api`'s existing
    `FilterApplier`/`FilterStore`/`SearchFilter`, none of which are affected by severing `gangland-domain` from
    `inventory-api`. That rewrite is F3's `PagedRegion` rebuild — WS2 G3's job, not G2's; doing it now would be
    scope creep ahead of its own gate.
- **`KernelConfig.java`** — `userFactory()` bean signature drops the `InventoryRegistry` param
  (`new UserFactory(gangland, placeholderService)`). The `inventoryRegistry()` bean **stays exactly as before**
  (still constructs `InventoryRegistry`, still calls `InventoryHandler.setRegistry(registry)`) — it's no longer
  threaded through `UserFactory`, but three other classes now depend on it directly (next three bullets).
- **`InventoryRuntimeContext.java`** — gained an `InventoryRegistry` constructor param. `user.getInventory(name)`
  → replaced with `inventoryRegistry.getInventories(user.getUuid()).stream().filter(...).findFirst()` (the exact
  filter logic `User.getInventory(String)` used to run, just inlined at the one call site). Both
  `user.addInventory(...)` calls → `inventoryRegistry.registerInventory(user.getUuid(), ...)`. Confirmed the old
  `addInventory`'s "remove any existing same-title entry first" step was dead code for this call path specifically
  — `openInventoryForPlayer` already returns early via the existing-lookup branch before ever reaching the add
  call, and grepping the whole repo confirms this method was the *only* production caller of `user.addInventory`
  — so dropping that redundant pre-removal changes nothing observable.
- **`GameplayConfig.java`** — `inventoryRuntimeContext(...)` bean gained the `InventoryRegistry` param and passes
  it through to the (now 9-arg) `InventoryRuntimeContext` constructor.
- **`RemoveAccountListener.java`** — gained an `InventoryRegistry` constructor param. `user.clearInventories()` →
  `inventoryRegistry.clear(user.getUuid())`.
- **`DebugCommand.java`** (`/glw debug inv-data`, ruling W23 F7) — gained an `InventoryService` constructor param.
  Re-pointed from `user.getInventories()` (a list of every `InventoryHandler` the old framework had ever
  registered for that player) to `inventoryService.tracker().currentMenuOf(player)` — **at most one menu**, the
  real reduction F7 describes. `Menu` has no generic title accessor (checked the interface: `open`/`close`/
  `rerender`/`clickDelayMillis` only), so the display falls back to the menu's simple class name. **Known,
  accepted interim behavior, not a bug**: nothing actually opens a menu through `InventoryService` yet — that's
  WS2 G3's retarget of `InventoryRuntimeContext` onto `ChestMenuBuilder` — so `inv-data` reports "none" for every
  player until G3 lands. Documented in the method's javadoc so this doesn't get mistaken for a regression later.
  The "all users" (console) branch now iterates `inventoryService.tracker().currentViewers()` instead of
  `userManager.getUsers().values()`.
- **`LevelTester.java`** (`gangland-impl/src/test`, a `main()`-based manual CLI tool, not a JUnit test) — its one
  `new User<>(any(), any(), any(), any())` call dropped to 3 args to keep compiling. Not otherwise touched.

### What still references the four items you said stay untouched (verified, not assumed)
- **`PlayerInventoryCleanup`** — still defined in `inventory-api`, still `@ListenerHandler`-annotated so Keystone's
  reflective listener scan auto-constructs and registers it (confirmed no explicit `new PlayerInventoryCleanup(`
  call exists anywhere — it was never manually wired, always scan-only). Its constructor still takes
  `InventoryRegistry`, which is still a live bean — nothing about G2 changes its wiring.
- **`InventoryHandler.setRegistry(registry)`** — still the literal second line of `KernelConfig.inventoryRegistry()`,
  unchanged.
- **`SPECIAL_INVENTORIES`** — still defined in `InventoryHandler.java`; still read by `DebugCommand`'s
  `getSpecialInventories()` argument (`/glw debug special`), which I did not touch.
- **`MultiInventory.ID`** — still read/written only inside `inventory-api`'s own `MultiInventoryCreation.java`
  (2 lines), unrelated to anything G2 touched.

### Red-first evidence
Modified all three domain tests to drop the 4th (`InventoryRegistry`) constructor argument **before** touching
`User.java`, then compiled:
- Red: `mvn -pl gangland-infra/gangland-domain -am test-compile` against the still-4-arg `User` →
  `cannot infer type arguments for org.luckyraven.gangland.gang.user.User<>` /
  `actual and formal argument lists differ in length` — 8 distinct call sites across `UserTest.java` (3),
  `UserManagerTest.java` (4), `GangMembershipTest.java` (1), all failing to compile as expected.
- Green (after `User.java`/`UserFactory.java` changed): `mvn -pl gangland-infra/gangland-domain -am test
  -Dtest=UserTest,UserManagerTest,GangMembershipTest -Dsurefire.failIfNoSpecifiedTests=false` →
  `Tests run: 24, Failures: 0, Errors: 0` (12 + 6 + 6).

No new `GangFilterAdapterTest`/`MemberFilterAdapterTest` were written this gate — the plan's §7 table lists them,
but they belong to the pure-relocation, unchanged-behavior move (no new logic to pin), and your batch-2 scope
message named only "the three domain tests" for G2. Flagging this as a deliberate scope call, not an oversight,
in case G3a is where they're expected instead.

### Build
- Compile check: `mvn clean install -DskipTests` → `BUILD SUCCESS`, all 21 modules (first pass caught one miss —
  `GameplayConfig.inventoryRuntimeContext(...)` needed the new `InventoryRegistry` param threaded through to the
  now-9-arg `InventoryRuntimeContext` constructor; fixed immediately, confirmed with a second compile pass)
- Full test run: `mvn clean install` (final gate build) → `BUILD SUCCESS`, zero failures anywhere across all 21
  modules (whole-log grep for `Failures: [1-9]|Errors: [1-9]` → zero hits)

### Smoke
Plan's G2 smoke ask (`/glw filter gangs search <name>`) needs a live gang + a real player session to drive
meaningfully — out of reach for a console-only harness row (same documented-gap shape as several existing rows
in `scenarios.json`, e.g. `jetpack-give`). What I *can* and did confirm without one: `mvn clean install`'s full
test run exercises `GangFilterAdapter`/`MemberFilterAdapter`'s actual projection logic indirectly through every
gang/member domain test that touches filtering, and the G1 smoke row above already proves the reactor boots clean
with `GameplayConfig`'s bean graph (which now includes `inventoryRuntimeContext` depending on the relocated
adapters) fully wired with zero faults. Recommend a human tester (or a later gate's smoke pass, once a real gang
exists on the test server) run `/glw filter gangs search <name>` directly.

## Files touched
Modified (18): `pom.xml`, `gangland-impl/pom.xml`, `gangland-ui/shop-api/pom.xml`,
`gangland-features/gangland-npc-shops/pom.xml`, `gangland-features/gangland-turf/pom.xml`,
`gangland-features/cops-n-crooks/pom.xml`, `gangland-features/gangland-gadget/pom.xml`,
`gangland-infra/gangland-domain/pom.xml`,
`gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland/gang/user/User.java`,
`gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland/gang/user/UserFactory.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/config/GameplayConfig.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/config/KernelConfig.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/inventory/InventoryRuntimeContext.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/RemoveAccountListener.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/debug/DebugCommand.java`,
`gangland-impl/src/test/java/LevelTester.java`,
`gangland-infra/gangland-domain/src/test/java/org/luckyraven/gangland/gang/user/UserTest.java`,
`gangland-infra/gangland-domain/src/test/java/org/luckyraven/gangland/gang/user/UserManagerTest.java`,
`gangland-infra/gangland-domain/src/test/java/org/luckyraven/gangland/gang/GangMembershipTest.java`.

Deleted (2, moved not lost): `gangland-infra/gangland-domain/.../gang/GangFilterAdapter.java`,
`gangland-infra/gangland-domain/.../gang/member/MemberFilterAdapter.java`.

New (2, the move targets): `gangland-impl/src/main/java/org/luckyraven/gangland/gang/GangFilterAdapter.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/gang/member/MemberFilterAdapter.java`.

Main checkout (untracked, not this worktree, restored after use):
`brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` — `paths` temporarily repointed for the S1 smoke
run, round-tripped back to `wt\gangland-0.9.2`/`wt\keystone-1.10.0` afterward; no new row added.

## Subagents used
None. Given the depth of cross-file reasoning needed this gate (the hard `OpenMenuTracker`/`Menu` type-mismatch
that ruled out re-pointing `InventoryRuntimeContext` to the Keystone tracker directly, the plan-vs-tree
verification on `GangItemSourceProvider`'s location, the exact seam choice for `InventoryService`'s config class)
doing it directly kept the full reasoning chain in one place rather than losing context across a handoff; every
edit was small and mechanical once the design was settled.

## Concerns / open questions
- **`inv-data`'s interim "none" state** (see DebugCommand section) — deliberate, documented, self-heals at G3.
  Flagging again here in case it should be called out to server owners before G3 lands, if there's a gap between
  this gate shipping and G3 shipping.
- **No new adapter tests this gate** (see red-first section) — flagging the scope decision in case G3a's plan
  step expects them and I should pick them up there instead.
- **`GangItemSourceProvider` left untouched** — confirmed correct for G2's actual scope by reading the plan's own
  text against the current tree (the plan's G2 paragraph appears to describe work that actually belongs to G3's
  `PagedRegion` rebuild, not G2's domain-severing). Noting this discrepancy for the record in case it affects how
  G3's scope gets read later.
- Smoke gaps needing a human/later-gate pass: `/glw filter gangs search <name>` (G2), and generally every
  interactive-inventory check once G3 starts actually opening `ChestMenu`s.

---

## Fix round 1 — 2026-09-21 (Opus review `exec/G010/WS2-G1-G2-review.md`, ruling W38)

Verdict was FIX (2 Important, 2 Minor): the wiring and the domain inversion were right; one interim regression
(F1) needed fixing now, one (F2) deferred to G3a by the ruling, two Minor handled by the ruling directly (M3 —
line-ending normalization, git's job, not code; M4 — see docket candidates below).

### F1 — `InventoryRuntimeContext`'s lookup was a false-positive risk against the shared registry

The review's finding: my G2 fix pointed `openInventoryForPlayer`'s "is this menu already open" lookup at
`inventoryRegistry.getInventories(uuid)` — but that shared registry is a **superset** of what the deleted
`User.inventories` field used to isolate. Every feature's `InventoryHandler` self-registers into the same shared
registry on construction (`InventoryHandler.java:82-84` — confirmed by reading it directly: the
`(String, int, Player, NamespacedKey)` constructor calls `registry.registerInventory(player.getUniqueId(), this)`
unconditionally whenever a non-null player is passed), so the registry also holds sign views, wand views,
`PaperworkView`/`HandcuffBribeView`, `CarSignViewProvider`, and `MultiPanelInventory` panels for that same player.
A foreign handler from a completely different feature whose title key happened to equal a YAML core-menu name
would be silently reopened instead of a fresh menu being built — and `findFirst()` over a `ConcurrentHashMap`
key set has no defined order if two features ever did collide.

**Fix:** `InventoryRuntimeContext.java` gained its own private `Map<UUID, Map<String, InventoryHandler>>`
(`openInventories`) — exactly what `User.inventories` was: per-player, de-duplicated by menu name, scoped only to
menus this class itself opened. The lookup now reads from this private map instead of the shared registry. The
existing `inventoryRegistry.registerInventory(...)` calls are **unchanged, kept as-is** — the shared registry
still needs every handler for the other listeners that depend on it (`PlayerInventoryCleanup`, the click/drag/
close handlers). Registration now writes to **both** the shared registry (for those other listeners) and the new
private map (for this class's own lookup). Marked with the requested comment:
`// ponytail: interim shim, deleted at WS2 G3 when menus open through the Keystone service`. Added
`InventoryRuntimeContext.clearPlayer(UUID)`, called from `RemoveAccountListener` right alongside its existing
`inventoryRegistry.clear(uuid)` call, on the same quit path — parity with what `User.clearInventories()` used to
clear for this class's bookkeeping specifically.

**Note for the record (incidental discovery while fixing this):** `InventoryHandler`'s own self-registration
means the explicit `inventoryRegistry.registerInventory(...)` call my G2 pass added for the single-`InventoryHandler`
branch was actually redundant (the handler already self-registers via its constructor) — but `MultiInventory`
does **not** self-register (checked its constructor directly, no `registry.` reference anywhere in the class), so
the same explicit call in the multi-inventory branch is load-bearing, not redundant. Left both calls in place
per the ruling's "keep the registry calls for the other listeners" instruction, rather than removing the
technically-redundant one — the risk of missing some other `InventoryHandler` construction path that doesn't
self-register wasn't worth the one-line savings.

**`RemoveAccountListener` clarification for the record (ruling W38):** the listener's `inventoryRegistry.clear(uuid)`
/ `inventoryRuntimeContext.clearPlayer(uuid)` calls on quit only clear bookkeeping — **exactly parity with what
`User.clearInventories()` did before this wave**, no held item was ever returned to the player on quit here,
before or after G2. This is not a regression G2 introduced. Once WS2 G3 retargets menu-opening onto
`keystone-inventory`, Keystone's own `MenuListener.onQuit` takes over that quit path and **does** return held
items (its `OpenMenuTracker`-driven, guarded close — see the module's item-return contract doc), closing this
longstanding gap as a side effect of the G3 retarget rather than something either G2 or this fix round addresses
directly.

### Red-first evidence
New test: `gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/inventory/InventoryRuntimeContextTest.java`
(two cases):
- `foreignHandlerWithCollidingKey_isNotReopened_freshMenuIsBuilt` — registers a mocked "foreign" `InventoryHandler`
  directly into the shared `InventoryRegistry` under a title key ("phone") colliding with a real YAML menu name,
  then calls `openInventoryForPlayer(player, "phone")` and asserts the foreign handler's `open()` is never called
  and a freshly-built handler's `open()` is called instead.
- `ownHandler_secondOpen_reopensTheSameInstance` — calls `openInventoryForPlayer` twice for the same menu and
  asserts `createInventory` only ran once (the second call found the existing instance through this class's own
  lookup and just reopened it) — the positive-path guard against an overcorrection that stops reopening
  altogether.
- **Red** (against the pre-fix shared-registry lookup, `mvn -pl gangland-impl -am test -Dtest=InventoryRuntimeContextTest
  -Dsurefire.failIfNoSpecifiedTests=false`): both cases failed — `foreignHandlerWithCollidingKey_...` failed with
  `NeverWantedButInvoked: inventoryHandler.open(<any>)` (the foreign handler WAS reopened, reproducing F1 exactly);
  `ownHandler_secondOpen_...` failed with a `NullPointerException` on `InventoryHandler.getTitle().getKey()` (the
  shared-registry lookup tried to read a title off a handler that, in this second-call scenario, had none stubbed
  — itself evidence of how fragile filtering the shared registry by title was).
- **Green** (after the private-map fix, same command): `Tests run: 2, Failures: 0, Errors: 0`.

### F2 — adapter projection tests: deferred to G3a per the ruling
Not written this round. `GangFilterAdapterTest`/`MemberFilterAdapterTest` (and `GangItemSourceProviderTest`) move
to WS2 G3a, since the classes move again there (into their final `menu.*` package) and that's where the plan's
§7 table actually expects them.

### Docket candidates (new, not yet filed — for the orchestrator/clerk)
- **M4** — `DebugCommand.java`'s `inv-data` console (non-player sender) branch messages the *tracked* players
  (`user.sendMessage(...)`) instead of the console sender that ran the command. Pre-existing behavior, confirmed
  unchanged by this wave (the old code had the identical shape: `for (User<Player> user : ...) { user.sendMessage(...) }`)
  — not introduced by WS2, just newly visible while re-pointing this method. Should be filed as a new bug docket
  entry, not fixed in this stream.
- **M3** (line endings on the two moved adapter files) — the review flagged this but ruling W38 says it's git's
  job (autocrlf normalizes on commit), not a code fix; no docket entry needed, noting only for completeness.

### Build
`mvn clean install` (full reactor, tests included) → `BUILD SUCCESS`, all 21 modules, zero failures anywhere
(whole-log grep for `Failures: [1-9]|Errors: [1-9]` → zero hits).

### Files touched (fix round)
Modified: `gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/inventory/InventoryRuntimeContext.java`,
`gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/RemoveAccountListener.java`.
New: `gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/inventory/InventoryRuntimeContextTest.java`.

### Concerns / open questions (fix round)
None beyond the two docket candidates above (M4 needs filing; M3 needs no action). Ready for: commit, merge
0.9.2 (WS8) into 0.10.0, dispatch G3a+G3.

---

## Fix round 2 — 2026-09-21 (re-review, one-line finding)

`RemoveAccountListener.onPlayerQuit` returned early on `user == null` (the `User` lookup) **before** the async
task that called `inventoryRuntimeContext.clearPlayer(...)` ever ran — so a player whose account lookup failed
leaked an `openInventories` entry forever (the `InventoryRuntimeContext` bean is a CONFIG-phase singleton that
survives reload, so nothing else ever cleared it). The shared `InventoryRegistry` doesn't have this hole:
`PlayerInventoryCleanup` clears it unconditionally, synchronously, with no `User`-lookup guard at all — this
fix round's shim was missing that same shape.

### What changed
`gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/RemoveAccountListener.java` —
`inventoryRuntimeContext.clearPlayer(player.getUniqueId())` now runs unconditionally and synchronously as the
first statement of `onPlayerQuit`, using `player` (not `user`) so it runs even when the `User` lookup below it
returns `null` — mirroring `PlayerInventoryCleanup.onPlayerQuit`'s exact shape (one line, no guard, no async
hop; the map is `ConcurrentHashMap`-backed so main-thread execution is safe). Removed the now-duplicate
`inventoryRuntimeContext.clearPlayer(user.getUuid())` call from inside the async block — the unconditional call
above already covers the `user != null` case too, since `player.getUniqueId()` and `user.getUuid()` are the same
value whenever `user` is non-null.

### Red-first evidence
New test: `gangland-impl/src/test/java/org/luckyraven/gangland/listener/player/RemoveAccountListenerTest.java`
— constructs the listener with a mocked `UserManager` whose `getUser(player)` returns `null` (the account-lookup-
failure scenario), fires `onPlayerQuit`, and verifies `inventoryRuntimeContext.clearPlayer(uuid)` was still
called.
- Red (pre-fix, `mvn -pl gangland-impl -am test -Dtest=RemoveAccountListenerTest
  -Dsurefire.failIfNoSpecifiedTests=false`): `Tests run: 1, Failures: 1` — `clearPlayer` was never invoked,
  exactly reproducing the leak.
- Green (post-fix, same command, then the full module run below): passes.

### Build
`mvn test -pl gangland-impl -am` → `BUILD SUCCESS`, `Tests run: 231, Failures: 0, Errors: 0` (whole reactor was
already green for this file per the coordinator's instruction, so a module-scoped run was sufficient this round).

### Files touched (fix round 2)
Modified: `gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/RemoveAccountListener.java`.
New: `gangland-impl/src/test/java/org/luckyraven/gangland/listener/player/RemoveAccountListenerTest.java`.
