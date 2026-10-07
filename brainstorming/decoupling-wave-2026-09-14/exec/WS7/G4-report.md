# WS7 G4 report — 2026-09-16

Status: DONE_WITH_CONCERNS (smoke row not run — see "Smoke" below)

## What changed

Executed by one Sonnet subagent in the Gangland worktree (`E:\Programming\java\wt\gangland-0.9.2`, branch `0.9.2`),
verified by me (read every touched file, ran the builds myself). Gates G2/G3 (committed `4d4ffa28`) already gave
`JetpackService`/`JetpackSession`/`JetpackTask`/`GadgetModuleConfig` their Bartizan-free shape; this gate adds the
three G4 deliverables on top without reopening any of that.

### Part 1 — Legacy migration hook (`JetpackService.java`)

Added `private static final String LEGACY_WEARABLE_TAG = "wearable"` (Bartizan's old `Wearable.NBT_KEY` value, a
raw string — no Bartizan import) and `migrateLegacyJetpack(Player)`: a chestplate carrying the legacy `wearable`
tag + `FuelKey.FUEL_ID` but no `JetpackKey.JETPACK_ID` yet, whose legacy catalogue key still resolves in
`JetpackAddon`, gets `JETPACK_ID` stamped in place via `ItemBuilder` — every other tag, including the fuel level,
untouched — then written back with `player.getInventory().setChestplate(builder.build())`. Called as the first
line of `scheduleChestplateCheck`'s scheduled runnable, before the existing resolve-and-activate read, so a
migrated item is picked up in the same tick. Deliberately not the `ItemRefresherRegistry` path (would reset fuel to
`Max_Fuel`, wrong for a live migration — S4/§6 of the plan).

### Part 2 — Per-item permission check at equip + new module-owned message YAML

`JetpackService.activate(Player, Jetpack)` gained a permission gate as its first real check (after the
already-active short-circuit): `if (!player.hasPermission(jetpack.getPermission())) { player.sendMessage(jetpackMessages.noPermission()); return; }` — the single choke point every equip path (`JetpackEquipListener`'s two handlers → `scheduleChestplateCheck` → `activate`, and the migration path above, which also flows into `activate`) already
funnels through, mirroring `CarInteractListener`'s per-item check exactly (cross-plan fact #1). No give-time check
(matches Car — none exists there either).

The denial message is **not** in `gangland-api`'s `Messages` enum — per the module-API contract rule (repo
`CLAUDE.md`), a module's new user-facing strings live in the module's own YAML. This is genuinely new territory:
no other module in the reactor had a YAML-backed message source before this gate (`CarMessageContract`/
`GanglandCarMessages` are the pre-split legacy precedent that routes to `Messages` — explicitly not mirrored here).
New:
- `gangland-features/gangland-gadget/src/main/resources/messages.yml` (module jar root, sibling to `commands.json`)
  — four keys: `Jetpack_No_Permission`, `Jetpack_Gave`, `Jetpack_Invalid`, `Jetpack_Player_Not_Found`.
- `.../jetpack/message/JetpackMessages.java` — mirrors `JetpackAddon`'s constructor/`FileInitializer` shape,
  reads each string lazily per call (no pre-parsing needed for four flat strings), colors through
  `GanglandChatUtil.color(...)`.
- `GadgetFileConfig.java` (edit) — new `jetpackMessages` FILE-phase `@Bean`, using
  `new FileHandler(plugin, "messages", "", ".yml", moduleLoader.classLoader())` — confirmed directly against
  Keystone's `FileHandler` source that an empty `directory` argument resolves to a jar-root file (`directory.isEmpty() ? name : ...`), matching `commands.json`'s own root placement.
- `JetpackService`/`GadgetModuleConfig.jetpackService(...)` (edit) — both gained a `JetpackMessages` parameter.

### Part 3 — `/glw jetpack give <id> <player>`

**Design decision, flagged for confirmation**: the plan's own §5 table shows `/glw jetpack give <id> <amount>`
(self-give, mirroring `CarGiveCommand` exactly — giving to the command sender). The batch-3 dispatch instead asked
for "chained `OptionalArgument` nodes with tab-completion for the jetpack id and the target player" — I read this
as a deliberate, orchestrator-level divergence from the plan's original self-give shape into a give-to-another-
player admin command, and built `/glw jetpack give <id> <player>` (no amount — one jetpack per call, ponytail).
This is the one place in this gate where my reading of an ambiguous instruction could differ from what was
intended; flagging explicitly rather than silently picking a shape. No existing "give to another player" pattern
exists anywhere else in the reactor to cross-check against (`CarGiveCommand`/`ItemMoneyGiveCommand` both self-give)
— this command is the first of its kind.

New:
- `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/command/JetpackCommand.java` —
  parent command, mirrors `CarCommand.java`'s exact shape (`super(plugin, "jetpack", true, "jetpacks")`).
- `.../command/JetpackGiveCommand.java` — two chained `OptionalArgument` nodes (`id`, tab-completing
  `jetpackAddon.getJetpacks().keySet()`; `player`, tab-completing `Bukkit.getOnlinePlayers()` names), resolves the
  target via `Bukkit.getPlayerExact(name)`, sends `messages.playerNotFound(...)` if offline, otherwise builds and
  hands over one jetpack via `PlayerInventory.addItem` (dropping overflow at the player's feet, with a
  `getWorld()` null-check per house rule). No give-time permission check (cross-plan fact #1 — none for cars
  either).
- `commands.json` (edit) — `jetpack`/`jetpack_give` entries mirroring `car`/`car_give`'s shape.

`GadgetModule.COMMAND_PACKAGE` already covers `org.luckyraven.gangland.gadget.command` — no registration change
needed; `@CommandHandler` scan picks up `JetpackCommand` automatically. `GadgetModuleTest` (which asserts the
module's declared config/listener/command/repository packages) passes untouched.

## Deviations from the brief

1. **Give-command argument shape** (`<id> <player>`, no amount) — see Part 3 above; a judgment call on an
   ambiguous instruction, not a plan violation, but flagged for the reviewer to confirm or correct.
2. `commands.json`'s bare `"jetpack"` parent entry was confirmed **not actually required** by the framework
   (`HelpInfo.displayHelp`/`InformationManager.getCommands()` never look it up by key; `CarCommand`'s constructor
   only prefix-filters `"car"*"`/`"jetpack"*"` entries) — kept anyway for Car-shape parity, at no cost.
3. `JetpackGiveCommand.giveJetpackItem` added an explicit `player.getWorld()` null-check before
   `dropItemNaturally` that the brief's sketch omitted — house rule (`getWorld()` is nullable, local + null-check).

## Red-first evidence

**`JetpackLegacyMigrationTest`** (new): a chestplate stamped with the legacy `wearable=<id>` tag + fuel tags
(`FUEL_CURRENT=1200`, deliberately ≠ the catalogue's `Max_Fuel=3600`) run through
`service.scheduleChestplateCheck(player)` with the scheduler stubbed to invoke its runnable synchronously.
- Red: `migrateLegacyJetpack(player);` temporarily removed from `scheduleChestplateCheck`. `mvn test -pl
  gangland-features/gangland-gadget -am -Dtest=JetpackLegacyMigrationTest,JetpackServiceActivatePermissionTest
  -Dsurefire.failIfNoSpecifiedTests=false` → failing line: `Wanted but not invoked:
  playerInventory.setChestplate(<Capturing argument: ItemStack>); ... there was exactly 1 interaction with this
  mock: playerInventory.getChestplate();` — the migration never fired, so the chestplate was never rewritten.
- Green: call restored → `Tests run: 1, Failures: 0` for this class. Final assertions: `JETPACK_ID` == the legacy
  id, `FUEL_CURRENT` == **1200** (unchanged, not reset to 3600) — the plan's core §6 requirement.

**`JetpackServiceActivatePermissionTest`** (new): a mocked `Player` with `hasPermission(jetpack.getPermission())`
stubbed `false`.
- Red: the permission-check block temporarily removed from `activate`. Same test command → without the gate,
  `activate` fell through toward full session creation and threw `NullPointerException:
  Cannot invoke "BukkitScheduler.runTaskTimer(...)" because the return value of "Bukkit.getScheduler()" is null` —
  valid red evidence that the gate is missing (the test's mocked `Player`/`JavaPlugin` never wired a live
  scheduler, since with the real gate in place nothing beyond it should ever be reached for a denied player).
- Green: check restored → `Tests run: 1, Failures: 0`. `isActive(player)` stays `false`,
  `messages.noPermission()`'s stubbed return value is sent to the player.

## Build

- `mvn clean install -pl gangland-features/gangland-gadget -am` → `BUILD SUCCESS`, `Tests run: 63, Failures: 0,
  Errors: 0, Skipped: 0` (61 prior + 2 new).
- `mvn clean install` (whole reactor, 22 modules) → `BUILD SUCCESS`.
- `mvn test` (whole reactor) → summed every module's `Tests run:` line: **827 run, 0 failures, 0 errors, 0
  skipped**.
- K1/K2 sanity grep, `grep -rln "org.luckyraven.bartizan" gangland-features/gangland-gadget/src/main/java` → still
  exactly `.../jetpack/config/JetpackBartizanTraitBridge.java` and `.../listener/car/CarDamageListener.java` — no
  new Bartizan symbol leaked into anything this gate touched.

## Smoke

`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py` + `scenarios.json` (main checkout, read-only reference)
exist and `E:\Documents\Minecraft\Test Server` is present on disk, so the harness itself is reachable. However,
**no scenario row for "jetpack-give" was run** — `scenarios.json`'s only rows are the legacy `S1`-`S8`
(pre-Bartizan-split, five-module topology, no Bartizan plugin at all) and `D0`-`D9` (0.9.0 topology: six Gangland
modules + a separate Bartizan plugin, added 2026-09-08) — none drives `/glw jetpack give`, refuels a jetpack, or
flies one; jetpack-specific commands did not exist when this harness/scenario file was authored. Defining a new
"jetpack-give" row means editing `scenarios.json` in the **main checkout**, under a different wave's directory —
outside the worktree LEAD-RULES authorizes me to edit ("Never edit, build, stash, checkout or commit there"). I am
reporting this gap rather than silently skipping it or editing outside my worktree: the smoke row named in the
dispatch does not exist yet and building it is a scenario-authoring task, not a code-gate task. The review's own
cross-gate note ("a pre-0.9.2 jetpack is inert until this hook lands, so the smoke row must refuel AND fly a
migrated item") is therefore still unverified against a live server — `JetpackLegacyMigrationTest`/
`JetpackServiceActivatePermissionTest` cover the same logic at the unit level, which is what's actually available
this gate.

## Docket ids touched

None new. GD-12 (fixed in G2) and the docket candidates from earlier rounds are unchanged by this gate.

## Subagents used

- Sonnet, `general-purpose`, one agent: all three G4 parts (migration hook, permission check + message YAML, give
  command). Verified by me (read every file, independent build + whole-reactor run + K1/K2 grep) before writing
  this report.

## Concerns / open questions

1. **Give-command argument order/shape** (`<id> <player>`, no amount) is my reading of an ambiguous dispatch
   instruction against a plan that specified something different (`<id> <amount>`, self-give). Please confirm this
   is the intended shape, or say which of `<player> <id>`/`<id> <amount> <player>`/etc. is wanted instead — this is
   the one design decision in this gate genuinely open to a different call.
2. **Smoke row not run** (see "Smoke" above) — the harness/server are reachable but no scenario exists yet for
   jetpack commands; someone with authority over `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json`
   needs to add a row (or point me at a different harness/scenario file already scoped to WS7) before this can be
   exercised end-to-end.

## Shape correction — 2026-09-16

Two orchestrator rulings, both addressed before the gate review runs.

### Ruling W16 — give command reworked to `/glw jetpack give <id> [amount]`, self-give

The dispatch's "target player" wording was an error; the plan's §5 (self-give, mirroring `CarGiveCommand` exactly)
is binding. Reworked:

- `gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/command/JetpackGiveCommand.java`
  — rewritten to `CarGiveCommand.java:49-96`'s exact shape: two chained `OptionalArgument` nodes, `name` (jetpack
  id, tab-completes `jetpackAddon.getJetpacks().keySet()`) → `amount` (raw `<amount>` placeholder, parsed with a
  `Messages.MUST_BE_NUMBERS` guard on a bad number), `giveJetpackItem(Player, String, int)` mirroring
  `giveCarItem`'s multi-slot stack-splitting loop verbatim (kept the `getWorld()` null-check, a house-rule addition
  from the original build that Car's own version doesn't have but is still correct). Removed all
  `Bukkit.getPlayerExact`/target-player resolution and the `Bukkit.getOnlinePlayers()` tab-completer.
- `.../jetpack/message/JetpackMessages.java` — `gave(String name, String amount)` reworded to the self-give shape
  (`"&aGave %amount% jetpack(s) '%name%'."`, mirroring `CAR_GAVE`'s `%name%`/`%amount%` placeholders); deleted the
  now-unused `playerNotFound(String)` method.
- `gangland-features/gangland-gadget/src/main/resources/messages.yml` — `Jetpack_Gave` reworded to match; deleted
  `Jetpack_Player_Not_Found`.
- `gangland-features/gangland-gadget/src/main/resources/commands.json` — `jetpack_give`'s `usage` back to
  `/glw jetpack give <id> <amount>`, `description` back to `"Gives the specified jetpack item."` (mirroring
  `car_give`'s wording).

No dedicated `JetpackGiveCommand` test existed before or after this correction (confirmed no test anywhere
references the deleted target-player shape — grepped `playerNotFound`/`getPlayerExact`/`JetpackGiveCommand` across
the whole test tree, only the production files themselves matched), so there was nothing to adjust test-side
beyond confirming the build stays green.

**Build after W16**: `mvn clean install -pl gangland-features/gangland-gadget -am` → `BUILD SUCCESS`,
`Tests run: 63, Failures: 0, Errors: 0, Skipped: 0` (unchanged count — no give-command test existed either way).
`mvn clean install` (whole reactor) → `BUILD SUCCESS`.

### Ruling W17 — two smoke scenario rows added, not run

Added to `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (main checkout, authorized by this
ruling as the untracked harness config, not a source tree):

- **`jetpack-give`** — `gadget` module + `Bartizan` plugin, drives `/glw jetpack give jetpack 1` from console.
- **`jetpack-migrated`** — same deploy, boots-clean check only (console cannot inject a pre-tagged legacy item into
  a player's inventory or drive an equip event at all — there is no player in a console-only run).

Both rows' titles are explicit about what a console-only harness genuinely can and cannot verify: it can confirm
the module boots clean, the command resolves without an exception, and (for `jetpack-give`) the Bartizan
item-vocabulary line appears — it **cannot** verify an actual equip or flight, since no in-game player exists in
this harness. This mirrors the harness's own established convention for documenting such gaps (S1's uncounted
`/glw help` check, S4's DB-only "detainment table migrates" check — see `README.md`'s Limitations section) rather
than writing an `expect` block that would silently rubber-stamp something console can't actually observe. The
review's cross-gate note ("smoke row must refuel AND fly a migrated item") is therefore only partially answerable
by this harness even once run; the unit-level tests (`JetpackLegacyMigrationTest`,
`JetpackServiceActivatePermissionTest`, `JetpackNbtIdentityTest`) are named in both rows' titles as the actual
covering evidence for the parts console can't reach.

**Verified, not run** (per the ruling — "Do not run them now"):
- `python3 -c "json.load(...)"` — `scenarios.json` parses, 21 total scenarios, both new ids present.
- `python smoke.py --list` — both rows print correctly, no parse errors.
- `python smoke.py --dry-run --deploy --rows jetpack-give,jetpack-migrated` — deploy plan resolves for both rows
  (module jar `gadget`, plugin `Bartizan`) with **no process started and no file touched** (`--dry-run`'s own
  contract). Both the main checkout's `target/gangland_warfare-*.jar` and `E:\Programming\java\Bartizan\bartizan-plugin\target\Bartizan-0.1.0.jar` reported `NOT FOUND` — expected, since the main checkout hasn't been rebuilt with
  0.9.2 and `scenarios.json`'s `paths.bartizan_jar` still points at the pre-0.4.0 filename. **Not fixed** — this is
  a pre-existing config value every Bartizan-using row already shares (not specific to the two new rows), out of
  scope for this ask; flagging for whoever runs the batch-4 matrix to update `paths.bartizan_jar` to
  `Bartizan-0.4.0.jar` and rebuild both repos' `target/` first.

## Fix round 1 — 2026-09-17

Review `exec/WS7/G4-review.md` (Opus, FIX — 3 Important, 2 Minor) + orchestrator ruling W22 (fix I1–I3, M1, M2;
precedent recorded: a module's own messages file is `<module>/<module>_messages.yml`, FileManager name
`<module>_messages`; I2 applies to `CarGiveCommand` too, one triage row covering both). All five addressed, in
the Gangland worktree, gadget module only.

### What changed

1. **Important 1** — module messages file renamed off the collision-prone bare `messages` name.
   `GadgetFileConfig.java`'s `jetpackMessages` bean now registers `new FileHandler(plugin, "gadget_messages",
   "gadget", ".yml", moduleLoader.classLoader())`; `JetpackMessages.java`'s constructor reads
   `fileManager.getFile("gadget_messages")`. The resource moved from `src/main/resources/messages.yml` to
   `src/main/resources/gadget/gadget_messages.yml` (content unchanged). This is now the recorded precedent for
   every future module's own messages file.
2. **Important 2** — `amount <= 0` now sends `Messages.MUST_BE_NUMBERS` and returns, in **both**
   `JetpackGiveCommand` and `CarGiveCommand` (same hole, same fix, per the ruling). Both `giveJetpackItem`/
   `giveCarItem` additionally gained a defensive `Math.max(0, Math.min(amount, 36 * maxStackSize))` clamp — the
   command-layer guard is the primary fix, but jetpacks (and cars) are non-stackable armour/vehicles
   (`maxStackSize == 1`), so `ceil(amount / 1)` turned any negative amount straight into a negative array size;
   the clamp means the private give-method itself can never crash regardless of caller. New
   `JetpackGiveCommandAmountGuardTest`/`CarGiveCommandAmountGuardTest` (one each, per the ruling) — reflection-based
   (calls the private `give*Item` method directly, since the actual crash site is inside it, not in the
   command-tree callback), red-first.
3. **Important 3** — `JetpackService.activate`'s permission check is now a **silent** `return` (no message) — it
   is reached by every `scheduleChestplateCheck` caller, not just a deliberate equip (join
   `JetpackActivateListener`, dismount/undown `JetpackSessionLifecycleListener`). The denial message moved to
   `JetpackEquipListener`'s two deliberate-interaction paths (`onInventoryClick`/`onInteract`), which now resolve
   the equipped/interacted item to a `Jetpack` themselves (new `JetpackAddon`/`JetpackMessages` constructor
   dependencies + `@AutowireTarget` entries) and send the message only when that specific item is a jetpack the
   player lacks permission for — mirroring `CarInteractListener.java:59`'s own-permission-check shape rather than
   deferring to a shared service-side check. `JetpackMessages` is now unused in `JetpackService` (ponytail:
   deleted the now-dead field/constructor parameter and the `GadgetModuleConfig.jetpackService` bean's matching
   parameter — net effect returns that bean method to its exact pre-G4 (G3-committed) 3-parameter shape).
   `JetpackServiceActivatePermissionTest` rewritten to assert silence (`verify(player, never()).sendMessage(...)`);
   new `JetpackEquipListenerPermissionTest` (3 cases: denied+message, permitted+no message, non-jetpack
   chestplate+no message).
4. **Minor 1** — `commands.json` gained a `jetpack_help` entry (`"/glw jetpack help <page>"`), mirroring
   `car_help`'s exact shape.
5. **Minor 2** — `JetpackLegacyMigrationTest` gained the two no-op cases: a non-jetpack Bartizan wearable (carries
   `wearable` but no fuel tags — e.g. `police_vest`) and an already-migrated item (already carries `JETPACK_ID`),
   both asserting `verify(inventory, never()).setChestplate(any())`. Extracted the shared
   player/inventory/scheduler mock wiring into a `drive(JetpackService, ItemStack)` helper to avoid repeating it
   three times.

### Red-first evidence

- **I2**: with the defensive clamp temporarily reverted to a bare pass-through (`int cappedAmount = amount;`) in
  both `JetpackGiveCommand.giveJetpackItem` and `CarGiveCommand.giveCarItem`, `mvn test -pl
  gangland-features/gangland-gadget -am -Dtest=JetpackGiveCommandAmountGuardTest,CarGiveCommandAmountGuardTest
  -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 2, Failures: 2` — both threw
  `java.lang.NegativeArraySizeException` (wrapped in `InvocationTargetException` through the reflective call),
  exactly the crash class the review named. Clamp restored in both files → `Tests run: 2, Failures: 0`.
- I1, I3, M1, M2 are config/behavior-relocation fixes the ruling did not ask a red-first cycle for beyond I2 (I1 is
  a pure rename with no behavior change to demonstrate red against; I3's new tests are new coverage of relocated,
  not previously-buggy, behavior; M1/M2 are additive). All new/changed tests were run and confirmed green as part
  of the full build below.

### Build

- `mvn clean install -pl gangland-features/gangland-gadget -am` → `BUILD SUCCESS`, `Tests run: 70, Failures: 0,
  Errors: 0, Skipped: 0` (63 prior + 7 new: 2 amount-guard, 2 no-op migration cases, 3 listener-permission cases;
  `JetpackServiceActivatePermissionTest`'s existing 1 case was rewritten in place, not added).
- `mvn clean install` (whole reactor, 22 modules) → `BUILD SUCCESS`.
- `mvn test` (whole reactor) → summed every module's `Tests run:` line: **834 run, 0 failures, 0 errors, 0
  skipped**.
- K1/K2 sanity grep unchanged: `.../jetpack/config/JetpackBartizanTraitBridge.java` and
  `.../listener/car/CarDamageListener.java` only.

### Docket ids touched

**Docket candidates** (per the ruling — one triage row covering both commands, not filed by me this round, queued
for whoever runs `build_docket.py` next):
- **Negative/huge `<amount>` in `CarGiveCommand` + `JetpackGiveCommand`** — `slots = ceil(amount / maxStackSize)`
  produced a negative array size for a negative amount (`NegativeArraySizeException`) and an unbounded allocation
  for a huge one; both are non-stackable-item commands (`maxStackSize == 1` for armour/vehicles), so a negative
  amount crashed on the very first bad input. Found in the WS7 G4 review (Important 2), fixed in 0.9.2 G4
  (this fix round): `amount <= 0` rejected at the command layer + a `Math.max(0, Math.min(amount, 36 *
  maxStackSize))` defensive clamp in both give-methods. Covered by `JetpackGiveCommandAmountGuardTest`/
  `CarGiveCommandAmountGuardTest`.

**Migration note (for G6)**: a migrated legacy jetpack keeps whatever `fuel_max` value Bartizan originally stamped
on it (typically `3600`, its stock default) even if the catalogue's `Max_Fuel:` has since been changed in
`items/jetpacks.yml` — `migrateLegacyJetpack` only ever touches `JETPACK_ID`, never `FUEL_MAX`/`FUEL_CURRENT`
(deliberately, per §6 — a live migration must never reset state a factory-refresh would). A server owner who
raises `Max_Fuel:` after shipping jetpacks will see already-migrated items keep their old, lower ceiling until
next refreshed by the item-refresher path (shop/trader delivery), not on migration.

### Subagents used

None this round — all fix-round work done directly.

### Concerns / open questions

None blocking. Two notes for the record:
1. `GadgetModuleConfig.java`'s net diff from the last commit is empty — G4 added a `JetpackMessages` parameter to
   the `jetpackService` bean, this round's I3 fix removed the same parameter, and no other change touched this
   file in either gate, so `git status` shows it as unmodified (verified: byte-identical to the G2/G3-committed
   baseline). Not a mistake — just flagging so the reviewer isn't surprised it's absent from the diff.
2. Reflection was used for `JetpackGiveCommandAmountGuardTest`/`CarGiveCommandAmountGuardTest` (calling the
   private `give*Item` method directly) rather than driving the full `OptionalArgument`/`Tree` command-tree
   machinery — this codebase has no existing precedent for unit-testing that layer end-to-end (confirmed during
   G4's own dispatch), and the actual crash site is inside the private method, so this is the narrowest test that
   still pins the real bug.

