# REVIEW WS1 — The scoreboard becomes its own plugin

Verdict: **PASS WITH FIXES**

The plan is unusually well-verified: its §0 census corrections are all correct, and every `file:line` I spot-checked
landed on the right block (a few off-by-one, §C6). The move itself is right — scoreboard-api is genuinely
domain-free and nothing in the six runtime modules touches it. Five things must be fixed before an executor starts:
two of them break the build, one is a false impossibility claim that hides a real option from the user, one
mis-records a docket row, and one loses a behaviour.

Verification used: `graphify affected "Scoreboard"`, `graphify affected "ScoreboardManager"` (Gangland, fresh graph),
plus `grep -rl` sweeps and reads of every cited file. Keystone repo checked for the placeholder SPI.

---

## Confirmations (the plan's contested claims that hold)

- **Census false positives are real.** `grep -rin scoreboard gangland-features` → **zero hits**; `gangland-api` hits
  are only `Settings.java:106-108,556-559`. `MoneyAspect`, `GanglandDetainmentEconomyContract` and
  `TurfFriendlyFireListener` carry no scoreboard token. The plan is right, the census is wrong.
- **`UserManager.onPreClear()` is a real, census-omitted removal point.**
  `gangland-infra/gangland-domain/.../gang/user/UserManager.java:173-176` — exactly the three lines the plan names.
- **Nothing outside `gangland-impl` + `User`/`UserManager` names a scoreboard type.**
  `graphify affected "ScoreboardManager"` returns 15 edges, all in `gangland-impl`. No module, no sign, no menu, no
  command contribution. Check #4 answered: **PAPI text can replace every cross-boundary use**; there are no
  per-player toggles and no module calls `User.getScoreboard()` (mail/turf/cops: zero hits).
- **UI-01 / UI-02 are already fixed.** `ScoreboardManager.java:62-66` (`Line.copyAll` + `title.copy()`, in-line
  "UI-01" comment) and `Scoreboard.java:24-33` (`timer.start(false)`, in-line "UI-02"). Both green, not flips.
- **CM-04 is real and the fix is valid.** `DownloadResourceCommand.java:30` gates on `Settings.isScoreboardEnabled()`;
  `Settings.isResourcePackEnabled()` exists (`Settings.java:40,410`). Docket fix direction matches verbatim.
- **`viaversion-api` stays.** `DebugCommand.java:517-521` uses `gangland.getViaAPI()` independently of the scoreboard.
- **`.claude/skills/` has zero scoreboard mentions**, and **no persistence** (no repository, table or row) — §6 right.

---

## Blockers (must fix before executors start)

- **B1. "No `keystone-persistence`" (§10) is wrong and would fail the new repo's first compile.**
  `gangland-ui/scoreboard-api/.../configuration/ScoreboardAddon.java:7-9` imports
  `org.luckyraven.keystone.persistence.{FileHandler, FileInitializer, FileManager}`, and
  `gangland-ui/scoreboard-api/pom.xml:30` declares the dependency. The plan's own G2 step 5 says the FILE phase
  loads two YAML files through `FileManager` — §10 contradicts §4. **Required:** `keystone-persistence` (provided)
  in the new pom's dependency list.

- **B2. The removal table misses two pom entries; the reactor will not resolve without them.**
  `gangland-impl/pom.xml:69-72` declares `<artifactId>scoreboard-api</artifactId>`, and root `pom.xml:251-255`
  carries its `dependencyManagement` entry. Neither appears in §2's table. (The fastboard property at
  `pom.xml:75` and its `dependencyManagement` block at `pom.xml:372-377` **are** orphaned once scoreboard-api goes —
  `grep -rn "fr.mrmicky"` finds no other consumer, so the plan's "confirm" in §13 is now confirmed: delete both.)

- **B3. Risk 1's "Not fixable without reintroducing a Gangland dependency" is false, and it hides the user's real
  option.** A PAPI-free path exists and needs zero Gangland types on either side:
  `GanglandPlaceholder extends PlaceholderHandler` (`GanglandPlaceholder.java:36`), and
  `PlaceholderHandler.asProvider()` (`Keystone/keystone-common/.../placeholder/PlaceholderHandler.java:71`) returns
  a Keystone `PlaceholderProvider`. Gangland registers that on Bukkit's `ServicesManager`; the scoreboard plugin
  resolves it **lazily on every call** — the exact seam already in use at `GanglandContext.java:215` for
  `ItemVocabulary`, and the house pattern for `BartizanApi`. Both sides name only
  `org.luckyraven.keystone.placeholder.PlaceholderProvider`, which is safe because Keystone is one shared
  `provided` jar. Cost: ~5 lines in `WiringConfig`/`Gangland.onEnable`, ~8 in the new plugin (PAPI first, service
  second, raw text last). **Required:** add this as decision **D5** with options (PAPI-only / PAPI + optional
  service lookup) and a recommendation; do not assert impossibility. It also aligns with C4 (WS6 publishes a
  `ServicesManager` facade anyway) — this could simply be one accessor WS6 already owes.

- **B4. US-19 must not be written `fixed` in the docket db.** The docket's fix direction is *"Fire the event from a
  sync task after the async load completes."* That is not what this plan does. `UserDataInitEvent` is still
  constructed async at `CreateAccountListener.java:105` (`new UserDataInitEvent(true, user)`) and still reaches
  `PlayerItemInitBridgeListener.java:20` → `PlayerItemInitEvent` → `LoadUniqueItem.java:37` (whose own comment
  says "fired async (bridged from the async UserDataInitEvent)"). Removing the scoreboard removes **one subscriber**,
  not the bug. **Required:** status stays `open` with the note "scoreboard subscriber removed in WS1; the event is
  still fired async for the item bridge — root fix unchanged", or a `partial` if the db vocabulary allows it.
  Writing `fixed` here would falsify the source of truth CLAUDE.md mandates.

- **B5. The enable-time board sweep is lost.** `ScoreboardLifecycleService.onPostInitialize` (`:44-56`) builds a board
  for every already-online player — the path that covers a `/reload`, a plugin-manager enable, or any enable with
  players on the server. The new repo's §2 tree has `PlayerBoardListener` (join), `RemoveBoardListener`
  (quit + onDisable sweep) and a reload command; **no enable sweep**. **Required:** one
  `Bukkit.getOnlinePlayers().forEach(...)` at the end of the plugin's bootstrap. This also answers check #5:
  the `PlayerBootstrapService → ScoreboardLifecycleService` ordering in `SchedulingConfig.java:25-33,59-66`
  becomes **irrelevant** (the new board needs no Gangland user record — it is PAPI text), but the *sweep* it
  performed is not irrelevant and must be reproduced.

---

## Corrections (fix in place)

- **C1. D1(b) silently changes behaviour for `Driver_V1`/`Driver_V2` admins, and needs a code edit the plan omits.**
  `ScoreboardManager.java:62-68`'s switch has `default -> new DriverV1(...)` — **V1, not V3, is the fallback**.
  Dropping V1/V2 requires flipping the default to `DriverV3` and warning on an unrecognised `Driver:` value. Say so
  in D1(b); today's `settings.yml:138-145` documents all three to admins.
- **C2. UI-16's disposition under D1(b) is `wontfix`/`moved`, not "carries in unfixed"** — the buggy code
  (`DriverV1`/`DriverV2`) is deleted, so the row has no code left to describe.
- **C3. UI-17 is open; I verified it (the plan listed it unverified) and it is a 2-line guard at port time.**
  `Line.java` `getCurrentContent()` → `contents.get(index)` and `update()` → `index = (index + 1) % contents.size()`
  are both unguarded: an empty `Lines:` block throws `IndexOutOfBounds` then `ArithmeticException` every tick.
  Fixing it during the port is the same diff as porting it; do that and close the row rather than carrying it.
- **C4. Port off the deprecated seam.** `Line.update(Placeholder, Player)` takes
  `org.luckyraven.keystone.util.Placeholder`, which is `@Deprecated(since = "1.3.0")` in favour of
  `org.luckyraven.keystone.placeholder.PlaceholderProvider`. A brand-new 0.1.0 repo with no back-compat debt should
  not be born on a deprecated interface; the change is the signature plus one call site, and it is a prerequisite
  for B3 anyway.
- **C5. §2's doc list is not the complete `grep -rli` result, contra Risk 5's claim.** Missing:
  `documentation/README.md` (top-level, distinct from the `developer/` and `tests/` READMEs the plan lists),
  `documentation/tests/features/loot_chests.md`, `brainstorming/CurrentlyWorking.txt`, and — in code —
  `GanglandContext.java:47,130` whose javadoc names the scoreboard in the bootstrap and reload descriptions
  (the plan rightly insists on rewording `SchedulingConfig`'s javadoc; same rule applies here).
- **C6. Line drift in five citations** (harmless but the executor greps by line): `gangland-build/pom.xml` relocation
  is `:43-46` and the `artifactSet` include `:76` (plan says 42-45, 74); `DownloadResourceCommand` gate is `:30`
  (plan says :31); `gangland-domain/pom.xml` scoreboard-api block is `:43-46`; `Gangland.java` bStats chart is
  `:131-142`; `FileConfig.java` beans are `:115-126`.
- **C7. §2's file tree has no class that reads the new `settings.yml`.** `BoardAddon` (ex-`ScoreboardAddon`) parses
  `scoreboard.yml` only; `Enable`/`Driver` come from Gangland's 600-line `Settings` today. Name the ~20-line
  replacement in the tree so the executor does not improvise one or, worse, copy `Settings.java`.
- **C8. `flashif` is unmentioned and needs a smoke row.** `scoreboard.yml:60` ships
  `%gangland_flashif:user_wanted-level>0:15:5:user_wanted%`. Its animation clock is the **static**
  `FlashPlaceholderWrapper.currentTick`, written *only* by `Scoreboard.java:18` and read by
  `GanglandPlaceholder.java:99-105`. After the split the writer is in the scoreboard plugin and the reader in
  Gangland; it still works because Keystone is one shared jar — but that is load-bearing, undocumented, and exactly
  the kind of cross-plugin static Keystone's own rules frown on. Add it to the seam table (§3) and assert it in
  G-final row (c).

---

## Simplifications (ponytail)

- **S1. Under D1(b) the driver machinery collapses further than the plan takes it.** One driver means
  `getDrivers()` has nothing to enumerate, D4 is moot, and the bStats `AdvancedPie` (a per-driver breakdown) becomes
  a constant — drop the chart or make it a `SingleLineChart`. Deleting `getDrivers()`, the reflection static block
  (`ScoreboardManager.java:26-38`) and the pie is a strictly smaller port than D4(b)'s "hardcode the list".
- **S2. Drop `commands.json` from the new repo.** It exists in Gangland because Keystone's `CommandManager` help
  layer parses it; with D2 (plain Bukkit `CommandExecutor`) nothing reads it. Verify, then delete the file and the
  §5 row — one less artifact to keep in sync for one command with no arguments.
- Agree with **D2** (plain `CommandExecutor` over `keystone-command` for one leaf) and with **no `-api` module** —
  both are correctly reasoned and the `graphify affected` evidence backs them.

---

## Missing consumers found by graphify affected

| Type moved | Consumer the plan misses | file:line | Impact |
|---|---|---|---|
| `scoreboard-api` (module) | `gangland-impl` Maven dependency | `gangland-impl/pom.xml:69-72` | **Build break** — reactor cannot resolve |
| `scoreboard-api` (module) | root `dependencyManagement` entry | `pom.xml:251-255` | **Build break** / dangling managed dep |
| `ScoreboardAddon` | `keystone-persistence` (`FileInitializer`/`FileManager`/`FileHandler`) | `ScoreboardAddon.java:7-9`, `scoreboard-api/pom.xml:30` | New repo will not compile (§10 says this dep is unneeded) |
| `ScoreboardLifecycleService` | its own already-online sweep | `ScoreboardLifecycleService.java:44-56` | Boards missing for online players on plugin enable/`/reload` |
| `Scoreboard` (tick writer) | `FlashPlaceholderWrapper.currentTick` static, read by `GanglandPlaceholder` | `Scoreboard.java:18` → `GanglandPlaceholder.java:99-105`, `scoreboard.yml:60` | Cross-plugin static clock; works only because Keystone is one shared jar — untested, unmentioned |
| `UserDataInitEvent` subscriber | the two remaining async subscribers | `PlayerItemInitBridgeListener.java:20`, `LoadUniqueItem.java:37` | US-19 survives the move; docket status must not say `fixed` |
| javadoc | bootstrap/reload descriptions | `GanglandContext.java:47,130` | Stale docs, same class of defect the plan fixes in `SchedulingConfig` |

---

## Decisions: agree / disagree with the planner's recommendation

| Decision | Planner rec | Reviewer view | Why |
|---|---|---|---|
| D1 drivers | (b) V3 only | **Agree, with C1** | V1/V2 also carry UI-16; but the `default ->` branch is V1 and must flip, and `Driver_V1` admins migrate silently — that is a user-visible consequence, not a code detail |
| D2 command framework | (b) plain Bukkit | **Agree** | One leaf command; `keystone-command` + a COMMAND phase for it is the one-implementation smell. Also kills `commands.json` (S2) |
| D3 PAPI | (b) same behaviour + loud warning | **Agree** | Free, and silent `%gangland_*%` is the worst failure mode |
| D4 driver discovery | (b) hardcode | **Moot under D1(b)** — delete `getDrivers()` and the reflection block outright (S1) | One driver enumerates to nothing |
| **D5 (new, required)** placeholder transport | — (plan asserts PAPI is the only option) | **Add it.** Recommend PAPI first, optional `ServicesManager` `PlaceholderProvider` second, raw text last | B3: a PAPI-free path exists via `PlaceholderHandler.asProvider()` + the `ItemVocabulary` seam, with zero Gangland types crossing. The user should choose whether Gangland publishes it |
| C1 order (WS2 before WS1) | plan says "sequencing only, nothing blocks WS1 first" | **Agree, and go further: run WS1 first** | Verified — no Oriel/`inventory-api` type appears anywhere in this plan. Both WS1 and WS2 edit the same two files (`gangland-domain/pom.xml:39-46` holds *both* `inventory-api` and `scoreboard-api`; `User.java` holds both a `Scoreboard` field and inventory imports). WS1 is 9 files against WS2's 58 — landing it first removes two edits from WS2's much larger diff and gives the wave an early, fully independent green gate. Recommend the orchestrator amend C1 |
| C2 versions | inherits the wave's `0.10.0` / plugin `0.1.0` | **Agree** | Matches the Bartizan precedent; removing a feature *and* its settings is a minor bump |

---

## Estimate check

**Plan says ~11.5h / ~1.5 studio days. Read ~16h / 2 days.** The step list is honest about what it names; what it
does not name is the gap:

- **G0 (0.5h) is thin** for a new repo: git init, `.gitignore`, `lombok.config`, `CLAUDE.md`, a shade config that
  actually produces a loadable jar, the Central `release` profile + `flatten-maven-plugin`, plus `graphify init`.
  Bartizan's pom is 118 lines but the first green `mvn clean install` on a fresh repo is rarely 30 minutes. **1.5h.**
- **G2 (4h)** now also owes the settings reader (C7), the enable sweep (B5), and — if D5 lands — the lazy
  `ServicesManager` lookup. **5h.**
- **G3 (3h)** gains three pom edits (B1/B2) and the `GanglandContext` javadoc. Roughly right at **3.5h**.
- **G4 (1h)** covers ~20 doc files plus `CLAUDE.md`'s module table, `MEMORY.md`, a wave plan file and the docket
  `write_db` calls. **2h.**
- **Unbudgeted entirely:** writing the four smoke rows into the console harness
  (`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`'s matrix — the plan says "in the style of", which is not
  the same as adding them), seeding the new repo's `triage/` folder per the CLAUDE.md docket rule, and the
  post-wave `graphify update . --force` both repos require. **+2h.**

Step count (18) is accurate; the S/M split is fair — nothing here is secretly an L.

---

## Things I could not verify

- **`bukkit.version` 1.16.5 for the new repo.** Did not compile the ported ~600 lines against the 1.16 API. The
  plan's own §13 flags this; agree it stays an executor check, not a planning claim.
- **FastBoard 2.1.5 against a 1.16.5 `bukkit.version`.** Root `pom.xml:75` pins `fastboard.version 2.1.5`; I did not
  check FastBoard 2.x's own minimum server version. If it needs 1.17+, either the pin drops or the floor rises.
- **Whether `commands.json` is read by anything other than Keystone's `CommandManager` help layer** (S2) — inferred
  from the house convention, not traced through `keystone-command`.
- **Central publishing readiness** for a new repo — same unknown as every other repo in this studio.
- **Whether any production `scoreboard.yml` deviates from the shipped default** in a way that breaks the
  copy-paste migration — out of scope for planning, correctly deferred by the plan to a release note.
- **The live docket db statuses** for UI-01/UI-02/CM-04/US-19/UI-16/UI-17/CL-10 — I read `bugs.json` (the built
  source) and the code, not the artifact's `bugs` collection. Rows never written default to `open`; the executor
  must `read_db` before `write_db` per CLAUDE.md, and B4 changes what gets written for US-19.
