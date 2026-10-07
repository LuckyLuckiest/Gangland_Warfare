# WS1 Plan — The scoreboard becomes its own plugin

User's words (README.md:7): *"The scoreboard needs to be its own plugin, thus remove it from the settings."*

Researched with `graphify query/explain/affected` in Gangland (fresh graph) and Keystone (fresh graph); every
code claim below was then confirmed by opening the cited file. No sub-agents used. Two corrections to the
census surfaced during verification are called out inline (§0).

## 0. Corrections to the census before you trust it

| Census claim | What verification found |
|---|---|
| "15 files import `org.luckyraven.gangland.scoreboard.*`" incl. `MoneyAspect`, `GanglandDetainmentEconomyContract`, `TurfFriendlyFireListener` | **False positive.** `graphify affected "Scoreboard"` runs a depth-2 BFS; those three files import `User` (which imports `Scoreboard`), not `Scoreboard` itself — grep confirms zero `Scoreboard`/`scoreboard` token in any of the three (`gangland-api/.../MoneyAspect.java`, `gangland-features/cops-n-crooks/.../GanglandDetainmentEconomyContract.java`, `.../TurfFriendlyFireListener.java`). Ground-truth direct importers via `grep -rl "import org.luckyraven.gangland.scoreboard\."`: 8 files outside `scoreboard-api` itself (`Gangland.java`, `ReloadPlugin.java`, `ScoreboardLifecycleService.java`, `FileConfig.java`, `SchedulingConfig.java`, `PlayerScoreboardListener.java`, `ScoreboardManager.java`, `User.java`) + 3 behavioural callers with no import (`RemoveAccountListener.java` — calls `user.getScoreboard()` without declaring a local of that type; `DownloadResourceCommand.java` — calls `Settings.isScoreboardEnabled()`; `UserManager.java` `onPreClear()` — same pattern as `RemoveAccountListener`). |
| "DriverV3... Interactive driver: disables main scoreboard, uses alternative (ViaVersion-aware)" | **Wrong.** `DriverV3.java:1-123` is a diff/cache algorithm (only pushes a FastBoard update when computed text changed since last tick); it never touches ViaVersion itself. ViaVersion (`ViaAPI`) is consumed once, in the **shared base class** `DriverHandler.FastBoardImpl.hasLinesMaxLength()` (`driver/DriverHandler.java:106-118`), used by **all three** drivers equally (max line length is 40 chars pre-1.13-client-through-ViaVersion, unlimited after). The `settings.yml:142` comment ("Uses interactive scoreboard to temporarily disable the main one...") describes something DriverV3's current code does not do — stale documentation, not a coupling to design around. |
| "ViaVersion tight coupling in DriverV3" (surprise #1) | Real, but repo-wide not driver-specific (see row above); also **`viaversion-api` is used elsewhere in Gangland independent of scoreboard**: `Gangland.java:3-4,55,207-208` wires a `ViaAPI viaAPI` field consumed by `command/sub/debug/DebugCommand.java:517-521` (`/glw debug` reports a player's detected protocol version). **`viaversion-api` stays in the root pom / `gangland-impl` after this workstream.** |
| "UserManager.onPreClear() nuance" (surprise #6) | Confirmed, and it's a **removal point the brief's list omits**: `gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland/gang/user/UserManager.java:168-178` — the scoreboard branch (`if (user.getScoreboard() == null) continue; user.getScoreboard().end(); user.setScoreboard(null);`) sits between the wanted-timer and bounty-timer stops in the same method; only the scoreboard lines are deleted. |
| "scoreboard-api itself imports zero gangland domain types... making it easy to extract" | Confirmed, and the extraction is even cleaner than stated: `scoreboard-api/pom.xml:22-26` declares a `provided`-scope dependency on `gangland-core`, but `grep -rn "import org.luckyraven.gangland" gangland-ui/scoreboard-api/src/main/java` returns **only self-imports** inside `org.luckyraven.gangland.scoreboard.*` — the `gangland-core` dependency is dead weight, not a real coupling. |
| UI-01 / UI-02 (both P0, docket §11) | **Already fixed in the current tree**, not open bugs to carry. `ScoreboardManager.java:58-61` and `driver/DriverHandler.java:31-37` cite "UI-01" in-line and deep-copy lines per board; `Scoreboard.java:25-33` cites "UI-02" and calls `timer.start(false)`. `ScoreboardTest.java` and `part/LineTest.java` pin these fixes and are green today (not red). Port them as-is; do not "fix" them again. |

## 0b. Review response (Opus review, verdict PASS WITH FIXES)

Every blocker/correction/simplification applied in place below; five new claims were re-verified against the
current tree with a fresh `grep -n`/read rather than taken on faith (two disagreed with the review itself —
noted).

| Id | What changed |
|---|---|
| B1 | Added `keystone-persistence` (provided) to the new repo's dependency list — §2, §10; §6 clarified this is config-file loading (`FileHandler`/`FileManager`/`FileInitializer`), not database persistence, so §6's "None" claim about tables/repos still stands |
| B2 | Added `gangland-impl/pom.xml:69-72` and root `pom.xml:251-255` to the removal table (§2); the `fastboard` property (`pom.xml:75`) and its `dependencyManagement` block (`pom.xml:372-377`) are confirmed orphaned (re-grepped, zero other consumers) and moved from "confirm" to "delete" (§2, §4 step 13) |
| B3 | Replaced Risk 1's "not fixable" claim with new **decision D5** (§9): publish `GanglandPlaceholder.asProvider()` on the `ServicesManager`, the same seam already used for `ItemVocabulary`; recommendation given, the PAPI-only path's cost stated |
| B4 | US-19 no longer recorded `fixed`. §11 now reads "one subscriber removed, event still fires async for the item bridge, root fix unchanged," status stays `open` (`partial` if the db vocabulary allows it) |
| B5 | Added the enable-time board sweep (§2 tree, §3, §4 steps 5 and 9) — a `Bukkit.getOnlinePlayers()` sweep on `onEnable` and on reload, replacing what `ScoreboardLifecycleService.onPostInitialize` did |
| C1 | D1(b)'s recommendation now states the `default ->` fallback is `DriverV1`, not `DriverV3` (re-confirmed directly, `ScoreboardManager.java:66`), and must be flipped explicitly with a warning on an unrecognised `Driver:` value — silent-migration note added (§9 D1) |
| C2 | UI-16's disposition changed from "carries in unfixed" to `wontfix`/`moved` under D1(b) — the buggy code (`DriverV1`/`DriverV2`) is deleted outright, so there is no code left to describe (§11) |
| C3 | UI-17 independently re-verified (`Line.java:78-80,88` — `contents.get(index)` and `index = (index+1) % contents.size()` both unguarded) and is a genuine 2-line guard. Moved from "not verified, carried unfixed" to "fixed by this workstream" — added as a port-time step (§4 G1) and closed in §11 |
| C4 | Added: port off the deprecated `org.luckyraven.keystone.util.Placeholder` onto `org.luckyraven.keystone.placeholder.PlaceholderProvider` during the port (§4 G1 step 3) — a prerequisite for D5 |
| C5 | Added the doc-sweep files the original `grep -rli` pass missed (`documentation/README.md:31,63`, `documentation/tests/features/loot_chests.md:65`, `brainstorming/CurrentlyWorking.txt:47,51-52`) and `GanglandContext.java:47,130` javadoc to the removal table and §4 step 15 |
| C6 | Re-checked every disputed citation against the live tree. Review was right on: `gangland-build/pom.xml` relocation is `:43-46` (not my original `:42-45`), its artifactSet include is `:76` (not `:74`), `FileConfig.java`'s two beans are `:115-126` (not `:116-125`). Review was **not** right on two, kept my own after a fresh `grep -n`: `DownloadResourceCommand.java:31` (confirmed by direct grep, not `:30`) and `gangland-domain/pom.xml`'s scoreboard-api dependency block is `:44-47` (confirmed — line 43 closes the *preceding* `inventory-api` dependency, not this one). `Gangland.java`'s bStats chart is actually `:131-145` (neither my original `:131-136` nor the review's `:131-142` reached the closing `}));`). |
| C7 | Named the settings-reader class the file tree was missing — `config/BoardSettings.java` (~20 lines: reads `Enable`/`Driver` from the new `settings.yml`) — §2 |
| C8 | Added the `FlashPlaceholderWrapper.currentTick` cross-plugin static clock (`Scoreboard.java:18` writer → `GanglandPlaceholder.java:99-105` reader, `scoreboard.yml:60`) to the seam table (§3) and a G-final smoke assertion (§4) |
| S1 | Agreed. Under D1(b) the reflection static block and `getDrivers()` (`ScoreboardManager.java:26-38,51-53`) are deleted outright rather than hardcoded — D4 is marked moot; the bStats `AdvancedPie` becomes a `SingleLineChart` or is dropped (§2, §3, §9) |
| S2 | Agreed. `commands.json` is dropped from the new repo entirely — nothing reads it once D2 (plain `CommandExecutor`) is chosen (§2, §5, §4 step 7) |
| Ordering | The orchestrator accepted the reviewer's argument: **C1 is amended, WS1 now runs before WS2** inside Gangland (order becomes WS1 → WS2 → WS4 → WS3 → WS5 → WS6). Stated in §4 and §8 |
| Estimate | Re-estimated honestly to **~16h / ~2 studio days** (§12), folding in the smoke-harness rows, new-repo triage seeding, and the post-wave `graphify update . --force` on both repos |

## 1. Scope

**In** — delete `gangland-ui/scoreboard-api`, all Gangland wiring/config/YAML for it, and `User.scoreboard`; stand
up a new standalone Keystone-powered Spigot plugin, placeholder name **`<ScoreboardPlugin>`**, that renders the
same `scoreboard.yml` format through PlaceholderAPI with no Gangland dependency.

**Out** — any new scoreboard feature (per-board API for other plugins, new drivers, BossBar/ActionBar variants).
Not asked for; adding one is exactly the kind of speculative surface the ponytail rule forbids.

**Deferred, named** — publishing `<ScoreboardPlugin>-api` to Central (moot: no api module recommended, §2);
adding a `-api` module for a second consumer plugin that does not exist yet (§2). **No longer deferred, now in
scope** (reviewer C3/S1): UI-17 is fixed at port time (a 2-line guard, cheaper than carrying it); UI-16 is
deleted along with `DriverV1`/`DriverV2` under D1(b), not carried (§11).

## 2. Target layout

### Does `<ScoreboardPlugin>` need an `-api` module? **No.**

Nobody outside `scoreboard-api` calls it programmatically today — every consumer in Gangland (`ScoreboardManager`,
`ScoreboardLifecycleService`, `PlayerScoreboardListener`, `ReloadPlugin`, `RemoveAccountListener`,
`UserManager.onPreClear`, `Gangland.java` bStats) is being **deleted**, and after the split the only cross-plugin
surface is text: `%gangland_*%` tokens PlaceholderAPI already resolves for any plugin. Bartizan's own rule
(`Bartizan/CLAUDE.md`: "Bartizan does NOT use `keystone-module` — it is a plain plugin... No `ModuleLoader`, no
`module.yml`, no `Host_Api`") settles the module-host question the same way — `<ScoreboardPlugin>` is a plain
Keystone-powered plugin, not a module. A single-module repo (one `pom.xml`, `packaging: jar`, no aggregator) is
the smaller structure than Bartizan's two-module split, because Bartizan's split exists to let a *third* jar
compile against `bartizan-api`; nothing compiles against `<ScoreboardPlugin>`.

**What would force an api later:** (a) another plugin wants to *push* a custom board or line programmatically
instead of through YAML; (b) another plugin wants to query "does player X have a board / which driver" instead of
reading it off PAPI; (c) the studio decides a second consumer plugin should extend the driver set. None of these
exist today — add the module the day one does (ponytail: `-api` for one implementation is a smell).

### Naming — 4 candidates, user decides (placeholder `<ScoreboardPlugin>` used throughout the rest of this plan)

| Candidate | Why it fits | Note |
|---|---|---|
| **Fascia** *(recommended)* | A fascia is literally a flat board/strip fixed along an edge to carry information — closest literal match to "scoreboard" of the four. | |
| Cartouche | An oval/scroll-shaped tablet bearing an inscription — evokes a bordered text panel. | |
| Parapet | A low wall/rail along an edge — reads more like a HUD guard-rail than a board. | |
| Corbel | A supporting bracket/ledge — continues the masonry vocabulary but is the weakest semantic fit for "displays text." | |

A web check found no SpigotMC/Modrinth listing under any of the four names as of 2026-09-14 — **the check was
shallow** (name search only, not a trademark search); re-check before publishing.

### New repo: `E:\Programming\java\<ScoreboardPlugin>` (sibling to Gangland/Keystone/Bartizan/Oriel)

```
<ScoreboardPlugin>/
├── CLAUDE.md                     (copy Bartizan's structure: platform, code style, testing notes)
├── pom.xml                       (single module — packaging: jar, no aggregator; see below)
├── graphify-out/                 (init after first commit)
└── src/main/java/org/luckyraven/<name>/
    ├── <Name>.java                        ← Bartizan.java pattern: JavaPlugin, onEnable/onDisable, BeanFactory, bStats, soft-dep guards
    ├── bootstrap/<Name>Context.java        ← BartizanContext pattern: owns DependencyContainer/BeanFactory, bootstrap()/shutdownBeans()
    ├── board/
    │   ├── Board.java                      ← was scoreboard-api Scoreboard.java (48 lines, unchanged incl. UI-02 fix)
    │   ├── driver/DriverHandler.java        ← unchanged (121 lines, incl. UI-01 fix + ViaVersion FastBoardImpl); Line.update(Placeholder,Player) call site ported onto PlaceholderProvider, not the deprecated Placeholder (C4)
    │   ├── driver/version/DriverV3.java     ← unchanged (122 lines) — see §9 D1/D4; under D1(b) this is the ONLY driver class ported — DriverV1/V2 and the reflection-based getDrivers() scan are deleted, not ported (S1)
    │   ├── part/Line.java, part/StaticLine.java   ← ported with a 2-line fix (C3/UI-17): `getCurrentContent()`/`update()` guard an empty `contents` list instead of throwing `IndexOutOfBounds`/`ArithmeticException`
    │   └── configuration/BoardAddon.java    ← was ScoreboardAddon.java (85 lines); still needs keystone-persistence's FileHandler/FileInitializer/FileManager (B1, see below)
    ├── BoardManager.java                    ← was gangland-impl ScoreboardManager.java (70 lines); Gangland dep → JavaPlugin; under D1(b) the static reflection block + getDrivers() are deleted, not hardcoded (S1, D4 moot)
    ├── config/BoardSettings.java             ← NEW, ~20 lines (C7): reads `Enable`/`Driver` from the new settings.yml — replaces the two `Settings.java` getters this repo has no `Settings` god-class to hold
    ├── listener/PlayerBoardListener.java     ← was PlayerScoreboardListener; UserDataInitEvent → Bukkit PlayerJoinEvent. Does **not** fix US-19 (B4) — it only removes one of that bug's subscribers; see §11
    ├── listener/RemoveBoardListener.java     ← was the scoreboard branch of RemoveAccountListener/UserManager.onPreClear; Bukkit PlayerQuitEvent + onDisable sweep
    ├── command/ReloadCommand.java            ← plain Bukkit CommandExecutor+TabCompleter (see §9 D2), replaces /glw reload scoreboard; also re-runs the enable-time sweep (B5)
    └── placeholder/PapiText.java             ← thin wrapper around keystone-hooks PlaceholderAPIProvider, with an optional second lookup for D5's `ServicesManager`-published `PlaceholderProvider` (see §3, §9 D5)
└── src/main/resources/
    ├── plugin.yml            (name: <ScoreboardPlugin>, depend: [Keystone], softdepend: [PlaceholderAPI, ViaVersion], api-version: '1.16')
    └── settings.yml          (new: Enable, Driver — the old settings.yml Scoreboard: block, byte-compatible values)
    └── scoreboard.yml        (byte-identical schema to Gangland's today — see §5)
```

No `commands.json` in the new repo (S2, reviewer-agreed) — that file exists in Gangland only because Keystone's
`CommandManager` help layer parses it; D2's plain `CommandExecutor` reads nothing from it, so it would be one more
artifact to keep in sync for a single argument-less command.

**Dependencies** (B1 correction — §10's original "no `keystone-persistence`" claim was wrong and would have
failed the new repo's first compile): `keystone-common` (`Placeholder`/`PlaceholderProvider`, `RepeatingTimer`,
`FlashPlaceholderWrapper`, `ColorUtil`), `keystone-bean` (BeanFactory/DI), **`keystone-persistence`**
(`FileHandler`/`FileInitializer`/`FileManager` — `ScoreboardAddon.java:7-9` imports all three today, and
`scoreboard-api/pom.xml:30` already declares this dependency; G2 step 5's FILE-phase `FileManager` load has always
implied it), `keystone-hooks` (`PlaceholderAPIProvider`), `fastboard`, `spigot-api`, `viaversion-api` (provided).
No `keystone-item`, no `keystone-npc`, no `keystone-command` (D2).

Maven properties/profile: copy Bartizan's root `pom.xml:1-118` almost verbatim — `<revision>0.1.0</revision>`,
`maven.compiler.release=17`, `bukkit.version` pinned to the server's actual floor (Bartizan pins 1.21.11 for its
own reasons — no reason for `<ScoreboardPlugin>` to inherit that instead of Keystone's 1.16.5 floor; recommend
1.16.5 here since nothing in the ported code needs a newer Bukkit symbol — confirm during the port, §13; also
confirm FastBoard 2.1.5 (root `pom.xml:75`) supports a 1.16.5 floor, not independently checked), Central
`release` profile (namespace/GPG pending, same as every other repo in this studio), `flatten-maven-plugin`. No
`<modules>` block — the root pom **is** the plugin pom (`packaging: jar`), shade plugin in the same file (Bartizan's
`bartizan-plugin/pom.xml:16-51` shade config, minus the api-jar-must-be-inside note since there is no api jar).

Shaded/relocated: `fastboard` → `org.luckyraven.<name>.dependency.fastboard` (same relocation Gangland used,
`gangland-build/pom.xml:42-45`), `org.bstats` → `org.luckyraven.<name>.dependency.bstats` (Bartizan's own pattern,
`bartizan-plugin/pom.xml:29-33`, since Spigot shares classes across plugin classloaders and both Gangland and
Keystone-powered plugins already relocate bStats independently). `keystone-*` stays `provided`, never shaded.

### Gangland: deletions (verified with `graphify affected "Scoreboard"` + grep, §0)

| Item | File:line | Action |
|---|---|---|
| Module | `gangland-ui/scoreboard-api/` (9 main + 2 test files, `pom.xml`) | delete whole module; drop `<module>scoreboard-api</module>` from `gangland-ui/pom.xml` |
| Domain field | `gangland-infra/gangland-domain/.../gang/user/User.java:26,53` | delete `import Scoreboard;` + `private Scoreboard scoreboard;` + its getter/setter |
| Domain pom | `gangland-infra/gangland-domain/pom.xml:44-47` | delete the `scoreboard-api` dependency block (re-verified against review's proposed `:43-46` — line 43 is the *preceding* `inventory-api` dependency's closing tag, not part of this block) |
| Impl pom | `gangland-impl/pom.xml:69-72` | delete the `scoreboard-api` dependency block (**B2** — omitted from the original table; without this the reactor cannot resolve) |
| Root pom | `pom.xml:251-255` | delete the `scoreboard-api` `dependencyManagement` entry (**B2**) |
| Domain cleanup | `gangland-infra/gangland-domain/.../UserManager.java:168-178` | delete only lines 173-176 (the `if (user.getScoreboard()...` branch); keep the wanted/bounty stop-timer lines either side |
| Impl bean | `FileConfig.java:22-23,115-126` | delete `scoreboardAddon()` + `scoreboardManager()` beans + the two imports |
| Impl bean | `SchedulingConfig.java:8,22,60-66` | delete `scoreboardLifecycleService()` bean + imports; check whether the "PlayerBootstrapService before X" ordering comment block (`SchedulingConfig.java:25-33`) needs rewording once scoreboard is no longer one of the four services it documents |
| Impl lifecycle | `gangland-impl/.../bootstrap/ScoreboardLifecycleService.java` | delete file |
| Impl listener | `gangland-impl/.../listener/player/PlayerScoreboardListener.java` | delete file |
| Impl listener | `gangland-impl/.../listener/player/RemoveAccountListener.java:83-85` | delete the 3-line scoreboard branch |
| Impl reload | `gangland-impl/.../bootstrap/ReloadPlugin.java:13-15,61-85` | delete `scoreboardReload()` + its 3 imports; delete its mention in the class javadoc (line 38, 42) |
| Impl command | `gangland-impl/.../command/sub/ReloadCommand.java:53-56,79` | delete the `scoreboard` `Argument` branch |
| Impl command | `gangland-impl/.../command/sub/DownloadResourceCommand.java:31` | **fix, not delete** — replace `Settings.isScoreboardEnabled()` with `Settings.isResourcePackEnabled()` (this is docket **CM-04**, open, P1 — the gate becomes meaningless once scoreboard leaves anyway, so fixing it is the same diff as removing it). Re-grepped directly (`grep -n isScoreboardEnabled`) and confirmed line 31, not the review's proposed `:30`. |
| Impl bStats | `Gangland.java:35,131-145` | delete the `scoreboard_driver` `AdvancedPie` chart (comment through closing `}));`) + import — re-measured directly, the chart spans further than either the plan's original `:131-136` or the review's `:131-142` |
| Impl config | `KernelConfig.java:104,159` | delete the `scoreboard.yml` `FileHandler` registration + update the comment at L104 |
| Impl config | `GanglandContext.java:47,130` | **C5** — reword the bootstrap-phase javadoc (L47: "...database, scoreboard) via standard..." lists scoreboard among kernel-phase products) and the reload javadoc (L130: "...files have been reloaded and scoreboards have been killed...") — same stale-comment risk already flagged for `SchedulingConfig` |
| Api settings | `gangland-api/.../file/configuration/Settings.java:106-108,556-559` | delete `scoreboardEnabled`/`scoreboardDriver` fields + getters + the `Scoreboard` `NodeReader` block |
| YAML | `gangland-impl/src/main/resources/settings.yml:136-149` | delete the `Scoreboard:` block (header comment through `Driver:`) |
| YAML | `gangland-impl/src/main/resources/scoreboard.yml` | delete file (108 lines) — its content becomes `<ScoreboardPlugin>`'s own `scoreboard.yml` default, §5 |
| Commands | `gangland-impl/src/main/resources/commands.json:46-49` | delete the `reload_scoreboard` entry |
| Build | `gangland-build/pom.xml:43-46,76` | delete the `fastboard` relocation (`:43-46`) + `artifactSet` include (`:76`) — corrected per review (C6), re-verified with `grep -n` |
| Build | root `pom.xml:75,372-377` | delete the `fastboard.version` property (`:75`) and its `dependencyManagement` block (`:372-377`) — **confirmed orphaned** (B2: re-grepped `fr.mrmicky`, zero other consumers), no longer just "confirm" |
| Docs | `README.md:63,78,109,152`; `documentation/README.md:31,63` (**C5**, missed originally — distinct from the `developer/`/`tests/` READMEs); `documentation/FRONT-PAGE.md:306-312,350` + `.bbcode.txt` twin; `documentation/features/scoreboard.md`; `documentation/tests/features/scoreboard.md`; `documentation/tests/features/loot_chests.md:65` (**C5** — cross-references `./scoreboard.md`); scoreboard mentions in `documentation/developer/{architecture,configuration,cops-n-crooks,dependency-injection,modules,ui-framework,README}.md`, `documentation/features/{gangs,unique-items}.md`, `documentation/tests/{commands,config-and-yaml,lifecycle-and-reload,UNIVERSAL-PRE-SHIP,README}.md`, `documentation/bartizan-integration.md`, `documentation/v0.7.3-DEV/CHANGELOG.md`, `documentation/v0.7.5-DEV/CHANGELOG.{md,bbcode.txt}`; `brainstorming/CurrentlyWorking.txt:47,51-52` (**C5**) | delete the scoreboard section/row from each; historical CHANGELOGs are left alone (they describe what shipped in that version, not current state) — only `FRONT-PAGE.md`, both `README.md`s, `documentation/features/*`, `documentation/developer/*`, `documentation/tests/*`, `CurrentlyWorking.txt` need edits. `.claude/skills/` — confirmed zero scoreboard mentions, nothing to do. |

## 3. Seams

| Boundary | Mechanism | Publisher | Puller | Default when absent |
|---|---|---|---|---|
| `<ScoreboardPlugin>` ↔ Gangland (gang/user/bank data) — **primary path** | **Text only**, via real PlaceholderAPI `%gangland_*%` tokens. `<ScoreboardPlugin>` never imports a Gangland class. | Gangland's `GanglandPlaceholder` (`gangland-impl/.../data/placeholder/worker/GanglandPlaceholder.java`), registered as a PAPI expansion by `Gangland.java:175-178` via Keystone's `PapiExpansionAdapter` (`keystone-hooks`, prefix `gangland`) | `<ScoreboardPlugin>`'s `PapiText` wrapper around Keystone's `keystone-hooks.papi.PlaceholderAPIProvider.resolve(Player, String)` (`Keystone/keystone-hooks/.../PlaceholderAPIProvider.java:21-30`) | See §9 D3 — a real behaviour change from today (Gangland currently resolves these even without PAPI) |
| `<ScoreboardPlugin>` ↔ Gangland — **D5 optional second path (B3, new)** | `org.luckyraven.keystone.placeholder.PlaceholderProvider` (Keystone-shared interface, zero Gangland types on either side) published on Bukkit's `ServicesManager`, resolved lazily on every call — the exact seam `ItemVocabulary` already uses (`GanglandContext.java:214-215`) | Gangland: `GanglandPlaceholder.asProvider()` (`PlaceholderHandler.asProvider()`, `Keystone/keystone-common/.../placeholder/PlaceholderHandler.java:71`), registered from `WiringConfig`/`Gangland.onEnable` (~5 new lines) | `<ScoreboardPlugin>`'s `PapiText`: PAPI first, this service second, raw text last (~8 new lines) | If Gangland does not register it (or is absent): falls through to the PAPI-only path above unchanged — this is additive, not a replacement |
| `<ScoreboardPlugin>` ↔ ViaVersion | `keystone-common` has no consuming-side wrapper for `ViaAPI` version lookup; keep the existing pattern: `Bukkit.getPluginManager().getPlugin("ViaVersion")` gate → `Via.getAPI()`, passed into `DriverHandler`'s constructor exactly as `Gangland.java:207-208` → `ScoreboardManager.java:56` do today | ViaVersion plugin | `<Name>.java`'s soft-dep block (Bartizan's `Dependency` inner-class pattern, `Bartizan.java:95-110`) | `viaAPI == null` → `FastBoardImpl.hasLinesMaxLength()` returns `false` (assumes a modern client), unchanged from today |
| Player join/quit → board create/destroy | Plain Bukkit `PlayerJoinEvent`/`PlayerQuitEvent`, **not** Gangland's `UserDataInitEvent` | Bukkit | `PlayerBoardListener`/`RemoveBoardListener` | n/a — no Gangland to be absent; the plugin works even if Gangland/Bartizan/Oriel are never installed |
| Enable/reload → board sweep for already-online players (**B5, new**) | Plain `Bukkit.getOnlinePlayers().forEach(...)` at the end of `onEnable()`, reused by the reload command | `<Name>.java` / `ReloadCommand.java` | itself | replaces what `ScoreboardLifecycleService.onPostInitialize` (`:44-56`) did — without it, a `/reload`, a plugin-manager enable, or any enable with players already on the server leaves them boardless until their next join |
| Full reload | Plain Bukkit command, not `CommandContribution`/`ArgumentTree` | `<Name>ReloadCommand` | admin/console | n/a |
| `FlashPlaceholderWrapper.currentTick` clock (**C8, new**) | A **static** field in Keystone's shared `keystone-common` jar. After the split the writer (`Scoreboard.java:18`, ticked every render) is in `<ScoreboardPlugin>`'s process and the reader (`GanglandPlaceholder.java:99-105`, consumed by `scoreboard.yml:60`'s `%gangland_flashif:...%`) is in Gangland's | `<ScoreboardPlugin>` (writer) | Gangland (reader) | Works today only because Keystone is one shared, unrelocated `provided` jar on the same JVM classloader hierarchy — this is load-bearing and was previously undocumented. No code change needed, but flag it: if Keystone ever namespaces this per-plugin, `flashif` breaks silently across the split |
| bStats `scoreboard_driver` chart | Under D1(b)+S1, one driver enumerates to nothing — `<Name>.java` registers a `SingleLineChart` (or drops the chart) instead of porting the `AdvancedPie`, `Gangland.java:131-145` today | `<ScoreboardPlugin>` | bStats.org | ships id `0` until registered (Bartizan precedent, `Bartizan.java:25`) |

Zero new abstractions beyond D5, which reuses an existing interface (`PlaceholderProvider`) and an existing
pattern (`ItemVocabulary`'s `ServicesManager` lookup) rather than inventing one — every other mechanism already
exists (PAPI text, Bukkit events, a plain command, Keystone's own soft-dep pattern).

## 4. Steps

**Ordering (amended C1, orchestrator-accepted):** WS1 now runs **before** WS2 inside Gangland — the wave order is
WS1 → WS2 → WS4 → WS3 → WS5 → WS6. The reviewer's argument: `gangland-domain/pom.xml:39-46` holds *both* the
`inventory-api` and `scoreboard-api` dependencies, and `User.java` holds both a `Scoreboard` field and inventory
imports — WS1 touches 9 files against WS2's 58, so landing WS1 first removes two edits from WS2's much larger diff
and gives the wave an early, fully independent green gate. Nothing in this plan depends on Oriel/`inventory-api`
(§10), so this reorder costs nothing here and only helps WS2. The gates below assume WS1 runs standalone against
current `0.9.1`, not against a WS2-modified tree.

### G0 — repo + naming (S, no Gangland/Keystone changes)
1. User picks a name from §2; create `E:\Programming\java\<ScoreboardPlugin>` git repo, `CLAUDE.md` (Bartizan's
   structure), root `pom.xml` (single module, §2), `lombok.config` copied from Bartizan. **Repo, S.**
2. `mvn clean install -DskipTests` on the empty skeleton (just plugin.yml + `<Name>.java` stub extending
   `JavaPlugin`) to prove the shade/Central profile resolves. **Repo, S.** Commit boundary: "Scaffold
   `<ScoreboardPlugin>` 0.1.0".

### G1 — port the rendering engine (M, new repo only; Gangland's copy still lives and still works)
3. Copy `board/`, `board/driver/` (`DriverHandler` only — **not** the reflection static block, S1),
   `board/driver/version/DriverV3.java` (§9 D1/D4 — the only driver under D1(b); the `getDrivers()` reflection
   scan is deleted, not ported), `board/part/`, `board/configuration/BoardAddon.java` verbatim (package rename
   only: `org.luckyraven.gangland.scoreboard` → `org.luckyraven.<name>.board`). Two fixes land in this same step,
   not after: **(C4)** `Line.update(Placeholder, Player)` and `DriverHandler.updateLine(Line)` move off the
   `@Deprecated` `org.luckyraven.keystone.util.Placeholder` onto `org.luckyraven.keystone.placeholder.PlaceholderProvider`
   (signature + one call site each); **(C3/UI-17)** `Line.getCurrentContent()` (`contents.get(index)`) and
   `Line.update()` (`index = (index + 1) % contents.size()`) each get a one-line empty-`contents` guard. Copy
   `ScoreboardTest.java` → `BoardTest.java`, `LineTest.java` unchanged plus one new empty-`Lines` case (both are
   already green and Gangland-domain-free, §0). **New repo, M.**
4. `mvn -pl . test` green (JUnit 5 + Mockito, `keystone-testkit`). Commit: "Port scoreboard rendering engine
   (UI-01/UI-02 intact, UI-17 fixed, off the deprecated Placeholder interface)".

### G2 — bootstrap, config, placeholders (M, new repo only)
5. `<Name>.java` + `bootstrap/<Name>Context.java` (Bartizan pattern) with BeanFactory phases: KERNEL (nothing
   special), FILE (`FileManager` loads `settings.yml` + `scoreboard.yml`, needs `keystone-persistence` — B1),
   CONFIG (`BoardAddon`, `BoardManager`, `config/BoardSettings.java` — C7 — beans), LISTENER (`PlayerBoardListener`,
   `RemoveBoardListener`), no COMMAND-phase scan (§9 D2). **End of `onEnable()`: B5's enable-time sweep** —
   `Bukkit.getOnlinePlayers().forEach(player -> ...)` builds and starts a board for every already-online player,
   replacing what `ScoreboardLifecycleService.onPostInitialize` (`:44-56`) did — this is the path that covers a
   `/reload`, a `/plugman enable`, or any enable with players already on. **New repo, M.**
6. `placeholder/PapiText.java` — thin wrapper, PAPI first: guard-constructs `keystone-hooks.PlaceholderAPIProvider`
   only if `Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null` (mirrors `Gangland.java`'s own
   `Dependency` pattern and `PlaceholderAPIProvider`'s own javadoc requirement, §3). **If D5 is accepted (§9):**
   second lookup — `Bukkit.getServicesManager().getRegistration(PlaceholderProvider.class)`, resolved lazily on
   every call, never cached — falls through to it when PAPI itself has no answer or is absent. Raw text last.
   Documented in `<Name>`'s README/settings.yml comment (§9 D3). **New repo, S.**
7. `command/ReloadCommand.java` (plain `CommandExecutor`, also re-runs step 5's enable sweep — kill and rebuild
   every online player's board); `settings.yml` (`Enable`, `Driver`), `scoreboard.yml` (copy Gangland's 108 lines
   verbatim, fix the stale Driver_V3 comment per §0), `plugin.yml`. **No `commands.json`** (S2 — nothing reads it
   under D2). **New repo, S.** `mvn clean package` green. Commit: "Bootstrap, config, PAPI(+optional
   ServicesManager) placeholders, enable-time sweep".
   **Gate G2 smoke row:** drop `<Name>-0.1.0.jar` beside `Keystone-1.9.2.jar` on the test server, no Gangland, no
   PlaceholderAPI installed — boots clean, board shows static text + literal `%gangland_*%` tokens, no error.

### G3 — remove from Gangland (M/L, Gangland only)
8. Delete `gangland-ui/scoreboard-api/`; drop the module line from `gangland-ui/pom.xml`. **Gangland, S.**
9. `User.java` field/getter/setter; `gangland-domain/pom.xml:44-47` dependency; `gangland-impl/pom.xml:69-72`
   dependency (**B2**); root `pom.xml:251-255` `dependencyManagement` entry (**B2**); `UserManager.onPreClear()`
   branch (`:173-176`). **Gangland, S.**
10. `FileConfig.java` (beans `:115-126`), `SchedulingConfig.java`, `KernelConfig.java`,
    `ScoreboardLifecycleService.java` (delete), `PlayerScoreboardListener.java` (delete),
    `RemoveAccountListener.java` branch, `ReloadPlugin.java`, `ReloadCommand.java`, `commands.json`
    `reload_scoreboard` entry, `GanglandContext.java:47,130` javadoc reword (**C5** — same stale-comment class as
    `SchedulingConfig`). **Gangland, M.**
11. `DownloadResourceCommand.java:31` — fix CM-04 (`isScoreboardEnabled()` → `isResourcePackEnabled()`).
    **Gangland, S.**
12. `Settings.java` fields/getters/NodeReader block; `settings.yml` block; `scoreboard.yml` (delete, content
    already copied to the new repo at G2 step 7); `Gangland.java:35,131-145` bStats chart + import. **Gangland, S.**
13. `gangland-build/pom.xml:43-46,76` fastboard relocation + artifactSet include (delete both — confirmed, not
    "if orphaned"); root `pom.xml:75,372-377` fastboard property + `dependencyManagement` (delete both —
    confirmed orphaned, **B2**). **Gangland, S.**
14. `mvn clean install` on the full Gangland reactor — green, zero references to `fr.mrmicky.fastboard` or
    `org.luckyraven.gangland.scoreboard` left (`grep -r` check). Commit: "Remove scoreboard: now
    `<ScoreboardPlugin>` 0.1.0 (standalone)".
    **Gate G3 smoke row:** Gangland `0.10.0` boots with no scoreboard code, no scoreboard-related fault line,
    `/glw reload` has no `scoreboard` argument, `/glw resource` respects `Resource_Pack.Enable` correctly.

### G4 — docs + docket (S)
15. Edit the doc list in §2's table (now includes `documentation/README.md`,
    `documentation/tests/features/loot_chests.md`, `brainstorming/CurrentlyWorking.txt` — C5). **Gangland, S.**
16. Docket writes (see §11) via the artifact's `write_db` — `read_db` first per CLAUDE.md, since live status was
    not independently checked this pass (§13). **S.**
17. Update this repo's `CLAUDE.md` (module table row removal) and `MEMORY.md` per
    `claude-md-management:revise-claude-md`. Seed a `triage/` folder in the new `<ScoreboardPlugin>` repo per the
    standard bug-docket rule (empty at launch unless the port surfaces something new) so future findings have
    somewhere to land immediately, not after the fact. `graphify update . --force` on both Gangland and the new
    repo so their graphs are not stale the moment this lands. **S.**

### G-final — combined smoke (S)
18. Test-server matrix: (a) Gangland alone, no `<ScoreboardPlugin>` — boots, no scoreboard, no error; (b)
    `<ScoreboardPlugin>` alone, no Gangland — boots, static lines render, `%gangland_*%` renders literally; (c)
    `<ScoreboardPlugin>` + Gangland + PlaceholderAPI — gang/user placeholders render live, **and the `flashif`
    wanted-level line animates** (proves the shared `FlashPlaceholderWrapper.currentTick` static still works
    across the two plugins' processes, C8); (d) `/glw reload` on Gangland with `<ScoreboardPlugin>` installed —
    board is untouched (Gangland no longer knows it exists); (e) restart the server with players already online
    (or `/plugman enable <ScoreboardPlugin>`) — every already-online player gets a board immediately, not only on
    their next join (proves B5's enable-time sweep).
    Rollback story: each gate's commit is independently revertable; G3 is the only destructive gate (deletes
    code) — its rollback is `git revert` of that commit range, which restores `scoreboard-api` and all wiring
    verbatim since nothing outside the deleted set was touched (confirmed by the seam table, §3, having zero
    new Gangland-side abstractions to unwind beyond D5, which is additive and safe to leave in place even if
    G3 is reverted).

## 5. Config, messages, permissions

| Item | Today | After |
|---|---|---|
| `settings.yml` `Scoreboard.Enable` | `gangland-impl/src/main/resources/settings.yml:137` | `<ScoreboardPlugin>/settings.yml` `Enable` (same key name, own file) |
| `settings.yml` `Scoreboard.Driver` | `settings.yml:145`, default `"Driver_V3"` | `<ScoreboardPlugin>/settings.yml` `Driver`, same default — **byte-compatible value strings** so an admin's existing choice ports unchanged |
| `scoreboard.yml` (108 lines, title animation + 15 rows) | `gangland-impl/src/main/resources/scoreboard.yml` | `<ScoreboardPlugin>/scoreboard.yml`, **schema untouched** — an admin copies `plugins/Gangland_Warfare/scoreboard.yml` → `plugins/<ScoreboardPlugin>/scoreboard.yml` verbatim (§9 D3 lists the one content caveat: literal `%gangland_*%` tokens now require PAPI) |
| `Settings.isScoreboardEnabled()`/`getScoreboardDriver()` (gangland-api) | `Settings.java:106-108,556-559` | deleted; no replacement in `gangland-api` — nothing in Gangland reads scoreboard config after this workstream. New home: `<ScoreboardPlugin>/config/BoardSettings.java` (~20 lines, C7) reads the same two keys from the new `settings.yml` |
| `commands.json` `reload_scoreboard` | `commands.json:46-49` | deleted from Gangland's; **no `commands.json` in the new repo at all** (S2 — nothing reads it once D2's plain `CommandExecutor` replaces Keystone's `CommandManager` help layer) |
| `/glw reload scoreboard` | `ReloadCommand.java:53-56` | deleted; replaced by `/​<name> reload` in the new plugin |
| Permissions | none existed (census §4: "no scoreboard-specific permissions in plugin.yml") | `<ScoreboardPlugin>`'s own `<name>.command.reload` (op-default, matches Bartizan's `bartizan.command.main` pattern) |
| Messages | none existed (census §4) | none needed — `<ScoreboardPlugin>` has no user-facing chat strings beyond the reload command's confirmation, which can be a literal string (one call site, no localization framework justified for one string, ponytail) |

No config key is **renamed** — every `settings.yml`/`scoreboard.yml` key keeps its exact spelling and default so
copy-paste migration works, per the brief's instruction.

## 6. Persistence

None in the database sense: no repository, no table, no database row (census confirms — it's pure per-session
render state, rebuilt from `scoreboard.yml` + live PAPI lookups on every join). Nothing to migrate.

**Clarification (B1):** the new repo still depends on the Maven module `keystone-persistence` — but only for its
config-file machinery (`FileHandler`/`FileInitializer`/`FileManager`, used by `BoardAddon` to load `scoreboard.yml`
off disk, exactly as `ScoreboardAddon.java:7-9` does today), not for its `AbstractRepository`/`DatabaseBackend`
database half. §10's original "no `keystone-persistence`" claim conflated the two; the module ships both, this
workstream needs only the file-loading one.

## 7. Tests

| Test | Today | After |
|---|---|---|
| `ScoreboardTest.java` (60 lines, pins UI-02: `timer.start(false)`) | `gangland-ui/scoreboard-api/src/test/.../ScoreboardTest.java` | moves verbatim (rename class `BoardTest`, package rename only) to `<ScoreboardPlugin>` — **already green, not a flip** |
| `LineTest.java` (97 lines, pins UI-01: per-board copy) | `gangland-ui/scoreboard-api/src/test/.../part/LineTest.java` | moves with a **new** case (C3): empty `Lines:` no longer throws `IndexOutOfBounds`/`ArithmeticException` — was red against pre-fix code (verified directly, `Line.java:78-80,88` are unguarded today), goes green with the port-time guard |
| New: `PapiTextTest` | — | asserts `PapiText.resolve()` returns raw text unchanged when PlaceholderAPI plugin is absent; delegates to `PlaceholderAPIProvider` when present (mirrors `PlaceholderAPIProviderTest` in `keystone-hooks`); **if D5 lands**, asserts the `ServicesManager` `PlaceholderProvider` fallback fires when PAPI has no answer |
| New: `ReloadCommandTest` | — | asserts the reload command kills and recreates every online player's board |
| New: `BoardManagerEnableSweepTest` (**B5**) | — | asserts `Bukkit.getOnlinePlayers()` all get a board after `onEnable()`/reload, not only after their next join |
| New (Gangland side): compile-level only | — | no new Gangland test needed; `User.java` losing the field is a compiler-enforced check. If a test mocked `User.getScoreboard()`, `mvn test` surfaces it — grep found none outside the deleted `scoreboard-api` tests |
| Smoke rows | none pre-existing | the five rows in §4 step 18 (added the flashif and enable-sweep rows per C8/B5), written in the style of `brainstorming/bartizan-split-2026-09-08/smoke/` (console-harness rows, not JUnit) — actually adding them to `smoke.py`'s matrix, not just describing them in this style, is unbudgeted work folded into §12's re-estimate |

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **Placeholder resolution silently degrades on the PAPI-only path.** Today `%gangland_*%` resolves even with PlaceholderAPI absent (Gangland's own in-process `PlaceholderService.convert` chain, `PlaceholderService.java:60-75`). If **D5 is declined**, `<ScoreboardPlugin>` can only reach that data through real PlaceholderAPI + Gangland's registered expansion — a server admin who removes PlaceholderAPI (keeping Gangland) loses gang/user data on the board with no error, just literal `%...%` text. **Correction from the original plan (B3):** this is not actually unfixable — D5 (§9) gives a PAPI-free path via Keystone's `ServicesManager`, at the cost of ~13 lines split across both repos. | If D5 is accepted: no residual risk — the `ServicesManager` path covers the PAPI-absent case. If D5 is declined: documented loudly in §9 D3, the new `scoreboard.yml`'s header comment, and the plugin's boot log ("PlaceholderAPI not found: dynamic placeholders will render literally") — this becomes a deliberate, user-approved trade-off rather than an unexamined one. |
| 2 | `SchedulingConfig.java`'s ordering javadoc (`:25-33`) documents four lifecycle services including the deleted one; `GanglandContext.java`'s own javadoc (`:47,130`, C5) separately names scoreboard in its bootstrap/reload descriptions — two stale-comment sites, not one. | Step 10 explicitly includes rewording both, not just deleting the bean method. |
| 3 | `DownloadResourceCommand.java`'s fix (CM-04) is bundled into a deletion-heavy gate; easy to skip if the executor treats it as "just another scoreboard reference to delete" rather than "flip the flag." | Called out as its own numbered step (11) with the exact before/after, separate from the deletion steps. |
| 4 | Two already-fixed P0s (UI-01, UI-02) get silently "re-broken" if the port is done from an old branch/tag instead of current `0.9.1` HEAD. | Step 3 explicitly ports from the current files (cited with their in-line "UI-01"/"UI-02" comments as the marker to preserve) and step 4's test run is the safety net — `BoardTest`/`LineTest` fail immediately if either regresses. |
| 5 | The doc sweep (§2 table) is easy to under-scope — some files (e.g. `documentation/developer/ui-framework.md`) may describe scoreboard only in passing among several UI systems; the first pass already missed three files and a javadoc site (C5). | Step 15 is scoped as "the doc list in §2's table," not "grep and guess" — the file list is now the complete `grep -rli` result across both `documentation/` and `brainstorming/` (§0/§2/C5), so the executor edits exactly those files, no more, no less. |
| 6 | **(B5, new)** The enable-time board sweep is easy to drop silently — nothing in a naive join/quit-listener port would fail a build or a unit test if it's missing, it would only show up as "boards are missing after a restart with players online," reported late. | Named as its own bullet in step 5 (not folded into the listener step), plus a dedicated `BoardManagerEnableSweepTest` (§7) and G-final smoke row (e) (§4 step 18). |
| 7 | **(C8, new)** The `flashif` animation clock (`FlashPlaceholderWrapper.currentTick`) is a **static field in a shared Keystone jar**, now written by one plugin's process and read by another's. It works today only because Keystone is unrelocated and shared across plugin classloaders on the same JVM — true, but undocumented before this review, and a future Keystone change (e.g. per-plugin namespacing of statics) would break it silently across the split with no compile error. | Documented in the seam table (§3) and asserted in G-final smoke row (c) (§4 step 18) so a regression is caught by a console check, not a bug report. No code change recommended — the static is deliberate Keystone design (one JVM, one clock), just newly cross-plugin. |
| 8 | **(Ordering, new)** Running WS1 out of the now-amended order (after WS2 instead of before) would hand WS1's executor a `gangland-domain/pom.xml`/`User.java` already modified by WS2's Oriel migration, invalidating this plan's line citations for those two files. | §4's ordering note states the amended order explicitly; if the studio proceeds with WS2 first anyway, re-verify `gangland-domain/pom.xml:44-47` and `User.java:26,53` against the WS2-modified tree before executing G3 step 9. |

## 9. Decisions for the user

| # | Decision | Options | Recommendation | What changes if the other option wins |
|---|---|---|---|---|
| D1 | Ship all three drivers or only V3? | (a) Port V1/V2/V3 unchanged. (b) Port only V3 (today's default). | **(b).** ViaVersion coupling is identical across all three (§0 correction — not a V3-specific cost), so this is purely "does anyone need the old clustering algorithm." No `settings.yml` in the repo is known to set `Driver: Driver_V1/V2`; V3 is strictly newer and lower-overhead per its own javadoc. **(C1 correction — required if (b) is taken:)** `ScoreboardManager.java:66`'s `switch` default branch is `new DriverV1(...)`, **not** V3 — re-verified directly. Dropping V1/V2 means the ported `BoardManager` must **explicitly** default to `DriverV3` and log a warning on an unrecognised `Driver:` value, or an admin whose `settings.yml` still says `Driver_V1`/`Driver_V2` (today's `settings.yml:138-145` documents all three as valid) silently gets V1's known bug (UI-16) with no board-behavior explanation. This is a **silent migration** the user must be told about, not a code detail. | If (a): port `DriverV1.java`/`DriverV2.java` unchanged too (177 extra lines, zero extra risk since they're unmodified), keep `ScoreboardManager`'s reflection-based `getDrivers()` scan as-is; the `switch` in `BoardManager.getDriverHandler` keeps its 3-way branch and its existing (buggy, UI-16) `default -> DriverV1`. |
| D2 | Command framework: Keystone's `keystone-command` (argument tree, `@CommandHandler` scan) or a plain Bukkit `CommandExecutor`? | (a) Keystone command framework, matching Gangland/Bartizan. (b) Plain Bukkit `CommandExecutor`+`TabCompleter`, one command, no subcommands. | **(b).** The plugin has exactly one command (`reload`) with zero arguments — a full argument-tree framework for one leaf command is the "one interface, one implementation" smell the brief tells planners to flag. Saves the `keystone-command` dependency entirely, and drops `commands.json` too (S2). | If (a): add `keystone-command` (provided) to the pom, port the `CommandHandler`/`Argument` pattern from `ReloadCommand.java`, gains uniformity with the rest of the studio's plugins at the cost of a dependency and a COMMAND bootstrap phase for one leaf. |
| D3 | PAPI: `softdepend` (today's framing) or promote to a **documented hard requirement** for dynamic content, still declared as `softdepend` in `plugin.yml` (Spigot has no "optional-but-warn-loudly" descriptor tier)? | (a) Silent graceful degradation (raw `%...%` text). (b) Same technical behavior, but boot-time warning log + `scoreboard.yml` header comment spelling out the PAPI requirement explicitly. | **(b).** Risk 1 above is real and silent failure is the worse outcome; a loud log costs nothing. `plugin.yml` still lists `softdepend: [PlaceholderAPI]` either way (an admin running a board with only static lines genuinely doesn't need PAPI). This decision is now independent of D5 — D5 (below) gives a second resolution path, D3 governs what happens when *neither* PAPI nor the D5 service answers. | If (a): skip the boot warning; an admin debugging "why don't my gang placeholders show" has to notice literal `%gangland_*%` text and work it out unaided. |
| D4 | Keep the reflection-based driver discovery (`ReflectionUtil.findClasses`) in `BoardManager`'s static block, or hardcode `List.of("DriverV1","DriverV3")` (or just `"DriverV3"` under D1(b))? | (a) Keep reflection, unchanged from `ScoreboardManager.java:26-38`. (b) Hardcode the list. | **Moot under D1(b) (S1, reviewer simplification, agreed):** one driver enumerates to nothing worth reflecting over. Delete `getDrivers()` and the reflection static block outright — that is a strictly smaller port than either (a) or (b) here, so this decision only matters if D1(a) is chosen instead. | If D1(a): **(b)** under ponytail (rung 3: stdlib beats a reflective classpath scan for a fixed 3-element list), but genuinely optional — the reflection code already works and costs nothing to leave alone if D1(a) is taken. |
| D5 | **(New — B3.)** How does `<ScoreboardPlugin>` resolve `%gangland_*%` when PlaceholderAPI is absent or has no answer? | (a) PAPI-only (the original plan's framing — `Risk 1` called this "not fixable," which was wrong). (b) PAPI first, then an **optional** Keystone `ServicesManager`-published `PlaceholderProvider` second, then raw text last. | **(b).** A PAPI-free path exists and costs almost nothing: `GanglandPlaceholder extends PlaceholderHandler` already (`GanglandPlaceholder.java:36`), and `PlaceholderHandler.asProvider()` (`Keystone/keystone-common/.../placeholder/PlaceholderHandler.java:71`) returns a `PlaceholderProvider` for free. Gangland publishes it on the `ServicesManager` — the **exact seam already in production** for `ItemVocabulary` (`GanglandContext.java:214-215`) and the house pattern for `BartizanApi`. Both sides name only `org.luckyraven.keystone.placeholder.PlaceholderProvider` (Keystone is one shared `provided` jar, so this crosses no plugin boundary that matters). Cost: ~5 lines in `WiringConfig`/`Gangland.onEnable`, ~8 in `<ScoreboardPlugin>`'s `PapiText` (§3 seam table, §4 step 6). It also aligns with contract C4 — WS6 is already publishing a `ServicesManager` facade for other purposes, so this could be one accessor WS6 owes rather than a bespoke WS1 addition (§10). | If (a): the plan's original framing stands — see Risk 1's PAPI-only branch; nothing to build beyond `PapiText`'s PAPI call, but a server without PAPI genuinely cannot show gang data on the board, full stop. |

## 10. WS6 asks / Oriel asks / Keystone asks

- **WS6 asks:** possibly one, if D5 is accepted: WS6's `ServicesManager` facade (contract C4) already exists to
  publish Gangland accessors for other plugins — `GanglandPlaceholder.asProvider()` (D5, §9) could be one more
  accessor on that same facade rather than a bespoke WS1-only registration. Not a hard dependency either way —
  D5 works as a standalone `WiringConfig` addition if WS6 lands later or differently. Otherwise none:
  `<ScoreboardPlugin>` never touches `gangland-api`.
- **Oriel asks:** none. Scoreboard is a sidebar HUD, not a menu/inventory surface — WS2's Oriel migration is
  irrelevant to this workstream; confirmed nothing in this plan reads or writes an `inventory-api`/Oriel type.
  **Superseded:** the orchestrator has accepted the reviewer's ordering argument — C1 is amended and WS1 now runs
  **before** WS2 (§4, §8), not after.
- **Keystone asks:** none — every primitive `<ScoreboardPlugin>` needs already ships in Keystone 1.9.2:
  `keystone-common` (`Placeholder`/`PlaceholderProvider`, `RepeatingTimer`, `FlashPlaceholderWrapper`, `ColorUtil`,
  `ReflectionUtil` only if D1(a) is chosen), `keystone-bean` (BeanFactory/DI), **`keystone-persistence`**
  (`FileHandler`/`FileInitializer`/`FileManager` — corrected, B1; §6 clarifies this is its config-loading half, not
  its database half), `keystone-hooks` (`PlaceholderAPIProvider`, confirmed at
  `Keystone/keystone-hooks/src/main/java/org/luckyraven/keystone/papi/PlaceholderAPIProvider.java:21-30` — already
  guards PAPI absence, exactly the SPI the brief asked this plan to locate; also the source of D5's
  `PlaceholderHandler.asProvider()` at `PlaceholderHandler.java:71`). No `keystone-item`, no `keystone-command`
  (D2), no `keystone-npc`. Recommend pinning `<keystone.version>1.10.0` at release time for consistency with the
  rest of the wave, even though 1.9.2 already has everything needed.

## 11. Docket

Grepped both docket folders per the brief (`brainstorming/bug-docket-2026-09-06/` and
`brainstorming/cross-docket-2026-09-10/`) for `scoreboard`/`Scoreboard` — the cross-project docket has no
scoreboard-specific rows (file-name-only hits in unrelated content); every relevant id lives in the 2026-09-06
docket:

| Id | Tier | Title | Status found in code | This workstream's disposition |
|---|---|---|---|---|
| UI-01 | P0 | Scoreboard driver appends the title to one shared live line list | **Already fixed** (`ScoreboardManager.java:58-61`) | Carries forward fixed; `write_db` note "ported to `<ScoreboardPlugin>` fixed, code at `board/BoardManager.java`" |
| UI-02 | P0 | Scoreboard updater runs async every tick | **Already fixed** (`Scoreboard.java:25-33`) | Carries forward fixed; same note |
| CM-04 | P1 | `/glw resource` is gated on the scoreboard toggle | Open | **Fixed by this workstream** (step 11) — `write_db` status `fixed`, note the commit + `DownloadResourceCommand.java:31` |
| US-19 | P1 | `UserDataInitEvent` fired async; scoreboard created off-thread | Open | **B4 correction — NOT fixed, status stays `open`.** Deleting the scoreboard subscriber (`PlayerScoreboardListener`) removes only **one** of `UserDataInitEvent`'s async subscribers. `CreateAccountListener.java:105` still constructs it with `new UserDataInitEvent(true, user)` (async), which still reaches `PlayerItemInitBridgeListener.java:20` → `PlayerItemInitEvent` → `LoadUniqueItem.java:37` (whose own comment says "fired async ... so inventory modifications must be hopped back to the main thread"). The docket's fix direction ("fire the event from a sync task after the async load completes") is untouched by this workstream. `write_db` note: "scoreboard subscriber removed in WS1 (`PlayerScoreboardListener` deleted); the event is still fired async for the item-init bridge — root fix unchanged," status `open` (or `partial` if the db vocabulary allows it) |
| UI-16 | P1 | DriverV1/V2 skip rows sharing the title's interval | Appears open (code matches description) | **C2 correction:** under D1(b) (recommended), disposition is `wontfix`/`moved`, not "carries in unfixed" — `DriverV1`/`DriverV2` are **deleted**, not ported, so the row describes code that no longer exists anywhere. `write_db` note: "code deleted in WS1 under D1(b); driver dropped, not fixed." If D1(a) is chosen instead, this reverts to "carries into `<ScoreboardPlugin>` unfixed, file in its own future docket." |
| UI-17 | P2 | Empty `Lines` list throws every tick | **C3 — independently re-verified, open, confirmed at `Line.java:78-80,88`** (`contents.get(index)` and `index = (index+1) % contents.size()`, both unguarded) | **Fixed by this workstream at port time** (§4 G1 step 3) — a 2-line guard, cheaper than carrying it forward. `write_db` status `fixed`, note "fixed during the `<ScoreboardPlugin>` port, `Line.java` guards an empty `Lines:` list" — **close this row**, do not seed it into the new repo's triage |
| CL-10 | P2 | `PlaceholderService.convert` allocates a provider chain per call | Open, but lives in `gangland-impl`'s `PlaceholderService.java` (Gangland core) | **Does not migrate** — `<ScoreboardPlugin>` never calls Gangland's `PlaceholderService` (§3), so this bug is unaffected by and unrelated to this workstream after the split |

**Live status caveat:** the disposition above reads `bugs.json` (the docket's built source snapshot) and the
current code, not the artifact's live `bugs` collection — per CLAUDE.md, `read_db` before every `write_db` in
step 16, since a row may already carry a different status than this table assumes.

`<ScoreboardPlugin>` needs its own docket coverage going forward for anything new found during the port —
UI-16/UI-17 are now resolved one way or another by this plan (C2/C3) and do not need seeding. Recommend a
`triage/` folder in the new repo itself per the standard CLAUDE.md bug-docket rule (step 17), rebuilt into its
own artifact once there are enough entries to justify one.

## 12. Estimate

18 steps: 11 S, 6 M, 1 L-adjacent (none actually L — the largest single step, #3, is a mechanical package-rename
port of ~600 already-correct lines, now also carrying C3/C4's fixes). The original **~11.5h / ~1.5 studio days**
under-counted several things the step list names but does not cost; re-estimated honestly per gate:

| Gate | Original | Revised | Why |
|---|---|---|---|
| G0 | 0.5h | **1.5h** | A new repo is more than a pom edit: git init, `.gitignore`, `lombok.config`, `CLAUDE.md`, a shade config that actually produces a loadable jar, the Central `release` profile + `flatten-maven-plugin`, plus `graphify init` — Bartizan's pom is 118 lines but a fresh repo's first green `mvn clean install` is rarely 30 minutes |
| G1 | 2h | 2h | Unchanged — C3/C4's fixes are small additions to an already-planned port step |
| G2 | 4h | **5h** | Now also owes the settings reader (C7), the enable-time sweep (B5), and — if D5 lands — the lazy `ServicesManager` lookup in `PapiText` |
| G3 | 3h | **3.5h** | Gains three pom edits (B2: `gangland-impl/pom.xml`, root `pom.xml` twice) and the `GanglandContext` javadoc reword (C5) |
| G4 | 1h | **2h** | ~21 doc files (was ~18) plus `CLAUDE.md`'s module table, `MEMORY.md`, the docket `write_db` calls (now with a `read_db` first, B4/§11), and seeding the new repo's `triage/` folder |
| G-final | 1h | **2h** | Unbudgeted in the original: writing the five smoke rows into the actual console harness (`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`'s matrix — "in the style of" is not the same as adding them), plus the post-wave `graphify update . --force` on **both** Gangland and the new repo |

**~16h / ~2 studio days**, still dominated by G2 (new code, now including the enable sweep and D5's optional
lookup) and G3 (deletion precision across `gangland-impl` + `gangland-domain` + `gangland-build` + root pom), with
G4/G-final's previously-unbudgeted documentation and harness work now a real fraction of the total rather than a
rounding error.

## 13. Not verified

- **UI-17 and UI-16 are now resolved, not open unknowns** (C3/C2, both independently re-verified this pass —
  removed from this list; see §11).
- **Central publishing readiness** for the new repo — assumed "same as Bartizan" (profile exists, namespace/GPG
  pending); not independently re-verified for this wave.
- **`bukkit.version` floor for the new repo** — recommended 1.16.5 (Keystone's floor) in §2 since nothing in the
  ported ~600 lines was checked line-by-line against 1.16 API availability (FastBoard, `RepeatingTimer`,
  `Placeholder`, `ChatUtil` are all Keystone/library types already floor-compatible; the scoreboard code itself
  uses no obviously-modern Bukkit API, but the executor should compile against 1.16.5 explicitly before assuming
  it, not inherit Bartizan's 1.21.11 pin by copy-paste).
- **FastBoard 2.1.5's own minimum server version** (root `pom.xml:75` pins `fastboard.version`) — not checked
  against a 1.16.5 `bukkit.version` floor. If FastBoard 2.x needs 1.17+, either the pin drops to a compatible
  FastBoard release or `<ScoreboardPlugin>`'s floor rises above Keystone's own.
- **Whether `commands.json` is read by anything other than Keystone's `CommandManager` help layer** (S2) —
  inferred from house convention (every other Keystone-powered plugin in this studio follows it), not traced
  through `keystone-command`'s source directly.
- **The live docket db statuses** for UI-01/UI-02/CM-04/US-19/UI-16/UI-17/CL-10 — §11's dispositions are built
  from `bugs.json` (the docket's built source snapshot) and the current code, not a `read_db` call against the
  artifact's `bugs` collection. Rows never written default to `open` per CLAUDE.md, so step 16's executor must
  `read_db` before `write_db` — this matters most for US-19, where B4 changes what gets written from the
  original plan's (wrong) `fixed`.
- **Whether any server-side `settings.yml`/`scoreboard.yml` in production already deviates from the shipped
  defaults** in a way that breaks byte-compatible copy — out of scope for a planning pass; call out at release
  time as a migration note ("copy your existing `scoreboard.yml` and the `Scoreboard:` block's two values into
  the new plugin's config files").

## §0d Execution corrections (2026-09-17, orchestrator)

- **D2 is decided by the user, not by this plan's recommendation:** `/plaque reload` is a keystone-command
  `Command` (decisions register WS1-D2 note: "stick with keystone implementation since they already have the whole
  library"; ruling W5). `keystone-command` is `provided`; `PlaqueContext` runs a real COMMAND phase.
- `BoardAddon`/`BoardSettings` are **FILE-phase** beans (they are `FileInitializer`s), not CONFIG as §2/§4 say.
- §7's `BoardManagerEnableSweepTest` shipped as `BoardManagerTest`; `ReloadCommandTest` added in the G2 fix round.
- `ViaAPI` lives on `Plaque` (`@Getter`, set post-bootstrap) and is read inside `BoardManager` method bodies — a
  soft-dependency type in a registered bean's method descriptor trips Keystone's `getDeclaredMethods` scan
  (`reflection.type.missing`) on every ViaVersion-less boot (review PLAQUE G2, Important 1).
- Behavioural delta recorded: the join listener is always registered and gated inside `createBoard`, so `Enable`
  toggles on `/plaque reload` (Gangland needed a restart).
- G1 closed the plan's §13 "compiles at 1.16.5?" item: Plaque compiles against `bukkit.version` 1.16.5.


## §0e Execution correction (2026-09-20, orchestrator)

- §2/C5 listed `brainstorming/CurrentlyWorking.txt` among the files to sweep; it is a historical per-version dev log (same class as the excluded CHANGELOGs) and stays untouched (review WS1 G3).
- The D5 placeholder seam (Gangland publishes `GanglandPlaceholder.asProvider()` as a Keystone `PlaceholderProvider` on the ServicesManager) lands in WS1 G3 itself (ruling W36), not WS6 — Plaque already consumes it.
