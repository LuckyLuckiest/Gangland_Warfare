# CONSTRAINTS - Cops N Crooks 0.16 "Where they come from" (every task obeys all of this)

Spec (binding): `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/v0.16/SPEC-0.16.md`
(untracked, main checkout only); its "Owner rulings" header overrides its body. Exact cross-task signatures: `CONTRACTS.md` (same
folder). Where a task section and CONTRACTS.md disagree, CONTRACTS.md wins; where CONTRACTS.md and PLAN.md "Rulings" disagree, the Ruling wins.

## Where
- Precondition (orchestrator): 0.15.2 (`E:/Programming/java/wt/gangland-0.15.2`, branch `0.15.2`) is merged into master, then the
  integration worktree `E:/Programming/java/wt/gangland-0.16.0` is created on a NEW branch `0.16.0` from that master. AutoDropPlanner,
  ChaseArc(s), ChaseArcListener, ChaseLearner, AutoSettings, `chase_habit`/`chase_level_stat` and the COLD_TRAIL ending then exist.
- Gangland lane: `git -C E:/Programming/java/wt/gangland-0.16.0 worktree add -b cnc-0.16-t<N> E:/Programming/java/wt/cnc016-t<N> 0.16.0`.
- Keystone integration worktree `E:/Programming/java/wt/keystone-1.15.0`, branch `phase-h14-dispatch-posts` from Keystone master
  (b818483 = 1.14.0). Keystone lane: `git -C E:/Programming/java/wt/keystone-1.15.0 worktree add -b h14-ks-<topic>
  E:/Programming/java/wt/ks115-t<N> phase-h14-dispatch-posts`.
- Work only in YOUR lane worktree; never switch branches. Paths in CONTRACTS/PLAN are relative to the repo root of your lane.
  `brainstorming/**` is untracked and lives only in the main checkout: name it absolutely and never commit it.
- Lane reports are saved by the orchestrator to `brainstorming/cnc-overhaul-2026-10-05/v0.16/reports/T<N>.md`; read, never write.
- Orient with graphify FIRST, from the main checkout (the orchestrator refreshes the graph after each wave merge):
  `cd "E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]" && graphify query "<Identifier>"` / `explain "<Class>"` /
  `affected "<Class>"`. Read raw files in your worktree afterwards. Line numbers in CONTRACTS are master 629af929 unless marked.
- Do not spawn sub-agents. The Bash tool blocks `mvn`: run Maven through PowerShell (or ctx_execute).

## Exact values (spec values, or planner Rulings where the spec is silent; do not re-tune)
- Versions: Gangland `<revision>` 0.16.0 (root `pom.xml:57`); `GanglandApi.VERSION` "2.2" -> "2.3" (ONE bump, Task 3);
  `Host_Api: 2.3` ONLY in `cops-n-crooks` and `gangland-turf` module.yml (they implement/consume 2.3 surface); all others unchanged.
  Keystone `<revision>` 1.14.0 -> 1.15.0 (`Keystone/pom.xml:62`); Gangland `<keystone.version>` 1.15.0 (`pom.xml:70`). Bartizan stays 0.6.0.
- Region tags (lowercase strings): `district`, `hideout`, `restricted`, `turf`, `breaker`. No owner = gang id `-1`.
- Dispatch (`copsncrooks/cops.yml` `Cops.Dispatch`): Enabled true, Unit_Speed 10.0 blocks/s, Min_Eta_Seconds 0, Max_Eta_Seconds 40,
  Station_Radius 32.0 (spawners within it of a station join it), Rejoin_Grace_Seconds 15. ETA = clamp(horizontal distance
  from the station anchor / Unit_Speed, Min, Max), rounded up to whole seconds. No station in the player's world -> ring, ETA 0.
- Breather (`Cops.Breather`): Enabled true, Seconds [15, 13, 10, 8, 6] (stars 1..5, clamp), Wipe_Window_Seconds 10.
- Mixed tiers: `cop_roles.yml` Squad_Composition entry `"<Role>"` or `"<Role>@<tier id>"`; bundled 3 stars
  `Commander@3, Pointman@2, Defender@3, Marksman@3, Assault@2`; 4 stars `Commander@4, Pointman@3, Defender@4, Marksman@4, Medic@3,
  Assault@3`; 5 stars `Commander@5, Pointman@4, Defender@5, Marksman@5, Medic@4, Assault@4`; 1-2 stars unchanged (no @).
- Hand-off (`Cops.Handoff`): Enabled true, Heading_Seconds 2, Bias_Seconds 10, Cone_Degrees 60 (half-angle either side of heading).
- Perimeter (`Cops.Perimeter`): Enabled true, Min_Level 3, Posts 2, Roles [Marksman, Defender], Max_Seconds 60, Lane_Length 16.0,
  Sight_Range 40.0, Leash_Radius 4.0. Posts sit on a ring around the zone centre of radius `min(zone radius, 0.8 x Sight_Range)`
  (= 32 blocks with the shipped values; Ruling R23, DECISIONS D18).
- Evasion (`copsncrooks/wanted.yml` `Wanted.Evasion`): `Hideout.Enable` true, `Hideout.Speed` 2.0; `Quiet_Speed.Enable` true,
  `Per_Minute` 0.25, `Max` 2.0, `Backup_Skip_Seconds` 60; `Max_Speed` 4.0 (cap on zone x hideout x quiet);
  `Auto.Rampage_Min_Weight` 80 (risk 11). Gang waypoint hideout radius when the waypoint radius is 0: 8.0, capped at 64.0
  (code constants).
- Code constants (no key, `ponytail:` comment naming the ceiling): pending unit counts as "en route" until 10 s past its
  `arriveAt` (C8 `unitsEnRoute`); self-defence provocation memory 60 s and crime-free takedowns per killer 3 per rolling hour (T9).
- Bribe stars (`Wanted.Bribe_Stars`): Enable true, Stars 1, Respawn_Seconds 300, Pickup_Radius 1.5, Item "NETHER_STAR".
- settings.yml (core features; the ONLY settings.yml keys this wave adds): `Wanted.Self_Defence` Enable true, Window_Seconds 8,
  Min_Damage 2.0, Pair_Cooldown_Seconds 600; `Bounty.Takedown_Minimum` 100; `Wanted.Contacts` Enable true, Price_Per_Star 1000,
  Cooldown_Seconds 600, Max_Stars 2; `User.Death.Hospital.Enable` true, `User.Death.Hospital.Shield_Seconds` 5 (0 = off; its
  off switch, no separate Enable; owner ruling D19). `items/money.yml` bundled `Money.Drop_Sources.PLAYER.Enabled`
  true -> false (key kept; COP unchanged). Existing server files are never rewritten: every key needs a code default = shipped value.
- Unchanged on purpose: charge sheet star pricing `min(10000, 200 + 250 x stars)`; Drop_Mode default ONE_STAR; no
  `WantedEvasionDropEvent`; `Death.Money.Formula` 'balance * 0.15', Threshold 1000.

## Platform and module rules
- Spigot only (`io.papermc.*` banned). Compile floor Spigot 1.16.5 API, Java release 17, no Java-21 API. No NMS. Sight checks use
  `World.rayTraceBlocks` or `LivingEntity.hasLineOfSight(Entity)`, never Paper's `hasLineOfSight(Location)`.
- `gangland-api` additive only within major 2 (also the re-exported gangland-core types): no removal, rename or signature change;
  new types, enum constants, overloads and default methods only. One bump 2.2 -> 2.3, done by Task 3.
- Runtime modules depend on `gangland-api` at provided scope, never `gangland-impl`. The core (impl, api, core, infra, ui) never
  names a module class. cops-n-crooks keeps its existing `Depends: [turf, civilians]` / `Plugins: [Bartizan]`.
- Keystone stays generic: only product-free code goes upstream (no wanted, crime, station or cop word in Keystone). Keystone fixes
  go upstream as 1.15.0 (branch `phase-h14-dispatch-posts`, doc `docs/phase-h14-dispatch-posts.md`, `@since 1.15.0` on new API),
  consumed through `<keystone.version>`. Never fork Keystone code into Gangland. Runtime floor: from 0.16.0 Gangland refuses to
  enable on a Keystone older than its `<keystone.version>` pin (Task 6 guard, DECISIONS D21).
- New module strings and knobs go in the module's OWN YAML (cops-n-crooks: `copsncrooks/{cops,cop_roles,wanted,wanted_messages,
  cop_radio_messages,cop_radio_messages_es,setup}.yml`), never `Messages`/`Settings`. Core features (contacts, self-defence,
  hospital) put knobs in settings.yml and strings in `Messages` + `message/message_{en,es}.yml` (Task 3 owns those files).
- Every feature has its own Enable key in its owner's YAML (cops.yml spells it `Enabled`, wanted.yml/settings.yml `Enable`;
  match the file). Switching a feature off restores 0.15.2 behaviour, except Self_Defence (off = no self-defence exemption) and
  bug fixes that ship regardless (T8's `recentDeaths` prune, docket US-33/WB-17).
- `CopConfigProvider` is NOT a container bean (CopLoader replaces it on every load): never a `@Bean` parameter of that type. Take
  `CopLoader` in the bean method and hand the class `copLoader::getLoadedProvider` (`Supplier<CopConfigProvider>`), read per call
  with `requireNonNullElse(..., X.DEFAULT)` (CONTRACTS C8 "Config reads").
- Citizens is soft: use `NpcSupport`; a listener whose `@EventHandler` parameter is a Citizens type needs
  `@ListenerHandler(condition = "isCitizensAvailable")`. Every Citizens NPC sets `SHOULD_SAVE` false (already done by the factories).
- New repositories: `@Repository(X.class)`, constructor `(JavaPlugin, DatabaseHandler, DatabaseBackend)`, shape of
  `CNC/database/JailExitRepository.java`; the owning registry calls `repository.setDataSupplier(...)` before first save.
  Additive columns are appended LAST (the backend diff engine issues `ALTER TABLE ADD COLUMN`; positional `doLoadAll` reads must
  read the new index only when present).
- Inside the AI budget: every new cop counts toward `Cops.Behaviour.Max_Per_Player`; per-cop sight checks run on `AI_Tick_Rate`.

## Code style (house rules)
- Method braces on their own lines, never `{ return x; }`. Lombok `@CustomLog` + `log.debug/info/warn/error`, never
  `Bukkit.getLogger()`. XSeries for drifting enums (`XMaterial`, `XParticle`); sounds via Keystone `SoundEffect` by String name.
- Colours with `&` codes via `ChatUtil.color`/`GanglandChatUtil.color`; never a literal section sign in source.
- `getWorld()` is nullable: local + null check. Async `Timer.start(true)` only for flag flips; Bukkit/economy/events on main thread.
- YAML: block style only (no `{k: v}`), keys `Capitalized_Underscore`, lookup ids lowercase, 3-space indent in copsncrooks files
  (match the file), every new key commented. Never `setDefaults()`/`copyDefaults()`.
- Listeners in `<module pkg>.listener.<sub>` (auto-scanned, constructor-injected). Never `@ListenerHandler` and `@Bean` on one
  class. `@Bean` parameters are ordering edges: keep them. New commands get a `commands.json` entry in the jar that owns them.
  Positional command input = chained `OptionalArgument` nodes with tab completion.
- Ponytail: shortest correct diff, reuse what exists, no interface with one implementation unless CONTRACTS names it a seam.

## Tests (documentation/TESTING.md is authoritative)
- No pom edits; JUnit + Mockito (inline) + keystone-testkit are wired. No MockBukkit. One test class per production class, mirrored
  package, `@DisplayName` + class javadoc, behaviour-named methods. Seams: `BukkitStatics.install()`, `PluginMocks`,
  `CapturingDiagnostics`, `StaticResets.resetAll()`, `BukkitRegistryFixture.install()` for XSeries/Material; `SettingsFixture`
  for `Settings` statics in impl tests. Thread stubs: stub `Bukkit::isPrimaryThread` true on main-thread paths.
- A test that pins behaviour the pre-change code already has (a "characterization pin") is exempt from red-first; label it so
  in its `@DisplayName` javadoc and in the report. Randomised code (ring angles) is tested with stubs keyed on coordinates, never on
  call order, so the result does not depend on the random draw.
- Mockito mocks return null for new `CopConfigProvider`/settings getters: every caller does `requireNonNullElse(x, X.DEFAULT)`.
- SQLite tests: `@TempDir(cleanup = CleanupMode.NEVER)`, disconnect every backend in `@AfterEach`, then `DbFiles.release(tempDir)`.
- Red first: write each test against the pre-change code, run it, see it fail for the right reason (a compile error only for a
  brand-new type), then implement and see it pass; say in the report how each test was red. A docket-pinned test that asserts
  today's wrong behaviour is flipped in the same change.
- Build in your lane, never `install` (only the orchestrator / Task 6 install; Task 21 uses `clean verify` and `clean package`): targeted
  `mvn -q -pl <module> -am test -Dtest=A,B -Dsurefire.failIfNoSpecifiedTests=false` (classes joined by commas; must print
  `Tests run: N` with N > 0, drop `-q` to see it), then the module's full `mvn -q -pl <module> -am test`, then
  `mvn -q clean verify -DskipTests` for the reactor. Modules: `gangland-core`, `gangland-api`, `gangland-impl`,
  `gangland-features/cops-n-crooks`, `gangland-features/gangland-turf`, `gangland-features/gangland-civilians` (Task 7's T-180
  fix only); Keystone: `keystone-npc`. ALWAYS `-am`.

## Bug docket
- Before changing a behaviour, look it up: https://claude.ai/code/artifact/4102fb1f-20b0-44e9-b1b7-2893a559fa04 (sources
  `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/bug-docket-2026-09-06/bugs.json`; Keystone findings in
  the cross docket https://claude.ai/code/artifact/4a903fb6-cbdd-4810-90b8-88a863e9013c). Read the fix direction and pinning tests.
- Workers never edit `triage/*.txt`, `bugs.json` or any artifact db. The report lists: ids fixed (commit, covering test), ids
  touched but left open, and every NEW bug (title, file:line, observation, impact, fix direction) for the orchestrator to file.

## Ownership and commits
- Touch only files your task OWNS. Need a change elsewhere: stop and report it.
- Commit on your lane branch: `cnc-0.16 T<N>: <summary>` (Keystone lanes: `keystone 1.15.0 T<N>: <summary>`), ending with exactly
  `Co-Authored-By: Claude <your lane's model name, as the orchestrator's prompt gives it> <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01WeecjuhnyeKY8eEN9CZkKF`. Never `--no-verify`, force-push or push.
- Done means: listed tests red-then-green, the touched modules' full suites green, reactor `clean verify -DskipTests` green, report.
