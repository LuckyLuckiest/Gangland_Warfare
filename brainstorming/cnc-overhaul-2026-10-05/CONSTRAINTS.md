# CONSTRAINTS - Cops N Crooks 0.15 "Lose them" (every task obeys all of this)

Spec (binding): `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/SPEC-0.15.md`
(main checkout only, untracked); its "Owner rulings" header overrides its body.
Exact cross-task signatures: `CONTRACTS.md` (same folder as the spec). Where a task section and CONTRACTS.md disagree, CONTRACTS.md wins.

## Where
- Integration worktree: `E:/Programming/java/wt/gangland-0.15.0`, branch `cnc-lose-them` (from master 16d1f064 = Gangland 0.13.0).
  Only the orchestrator writes there (merges, installs, the W4 acceptance build).
- Lane worktree: every code/docs task works in ITS OWN worktree `E:/Programming/java/wt/cnc015-t<N>` on branch `cnc-0.15-t<N>`,
  created by the orchestrator at the start of the wave with
  `git -C E:/Programming/java/wt/gangland-0.15.0 worktree add -b cnc-0.15-t<N> E:/Programming/java/wt/cnc015-t<N> cnc-lose-them`.
  Never switch branches, never work in another lane's or the integration worktree. Paths in CONTRACTS.md/PLAN.md are relative
  to the repository root, i.e. to your lane worktree. Exception: `brainstorming/**` is untracked and lives only in the main
  checkout; a task that names a brainstorming path gives it absolute (`E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/..`)
  and never commits it.
- Lane reports: your final report is saved by the orchestrator to the main checkout's
  `brainstorming/cnc-overhaul-2026-10-05/reports/T<N>.md`; later tasks read that folder, never write it.
- Keystone source `E:/Programming/java/Keystone` (pinned 1.14.0), Bartizan source `E:/Programming/java/Bartizan` (pin stays 0.6.0).
- Orient with graphify FIRST, from the main checkout: `cd "E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]" &&
  graphify query "<Identifier>"` / `graphify explain "<Class>"` / `graphify affected "<Class>"`. Read raw files in the worktree after.
- Do not spawn sub-agents. The Bash tool blocks `mvn` (context-mode hook): run Maven through PowerShell (or ctx_execute).

## Exact values (from the spec; do not re-tune)
- Plugin revision 0.15.0 (root `pom.xml:57`). `GanglandApi.VERSION` "2.0" -> "2.1" (ONE minor bump for the whole wave).
  Gang & Turf (0.14) has not started and rebases onto this; it takes 2.2. `module.yml` `Host_Api: 2.1` only for
  `cops-n-crooks` and `gangland-civilians` (the modules that use new api); every other module stays `2.0`.
- settings.yml (the only settings.yml keys this wave adds or re-means):
  `Wanted.Take_Money.Enable` default false (a missing key = false); `Wanted.Take_Money.Formula` default
  `"amount * multiplier ^ wanted"`; Amount 50 / Multiplier 5 stay plain formula inputs. Formula variables: amount, multiplier,
  wanted (stars BEFORE this drop), balance (wallet only), level, experience, bounty. Broken/NaN/Infinity/negative result ->
  fallback `Amount x Multiplier^wanted`, clamped >= 0, one console warning per formula text; the star always drops.
  `Bounty.Pay_Notoriety` default false. `User.Death.Money.Lose_Money: false` = no death charge and NO payout.
  `User.Death.Money.Formula` broken -> fallback `balance * 0.15` (clamped >= 0, warn once). A price of 0 = no withdraw, no message.
- Heat (cops-n-crooks `npc/wanted.yml`): Star_Thresholds [100, 250, 450, 700, 1000]; Streak_Bonus 1.5 (crimes chained inside
  settings.yml `Wanted.Kill_Combo.Reset_After`); Seen_By_Cop_Multiplier 1.5; Turf_War_Multiplier 0.5; crime weights
  Brandish_Near_Cop 25, Assault_Civilian 30, Car_Theft 60, Kill_Player 80, Kill_Civilian 100, Assault_Cop 100,
  Resisting_Arrest 100, Kill_Cop 150, Safe_Cracking 150, Store_Robbery 200, Trespass_Restricted 300, Jailbreak 450.
- Evasion: Enable true, Lost_Sight_Seconds 3, Drop_Mode ONE_STAR (ALL_STARS option), Search_Radius [40, 60, 90, 130, 180],
  Seconds_To_Drop [10, 20, 30, 45, 60], Outside_Zone_Speed 2.0. Repeating_Timer stays as the safety net.
- Shots: noise radius GUN 48, THROWABLE 16, MELEE 0; `Shots_Fired` radio at most once per shooter every 3 s.
- Regroup: 2 casualties within 20 s -> leader radios Regroup, fall back up to 15 s, one regroup per 60 s, cuff-first squads never.
- Kill exemptions (not a crime: no Kill_Player, no star, no notoriety): a posted-bounty takedown; self-defence (the victim's
  first hit on the killer in this fight came before the killer's first hit; a fight ends after 30 s with no hit either way);
  a kill made defending your own turf (your gang owns the CONTESTING turf the kill happens in).
- Charge sheet defaults (owner-free, planner chose; see PLAN Rulings): Base 200, Per_Wanted_Level 250, Maximum 10000,
  Seconds_Per_Unpaid 0.1, Max_Extra_Seconds 600. Wallet only, never the bank, never below zero.

## Platform and module rules
- Spigot only, never Paper (`io.papermc.*` banned). Compile floor Spigot 1.16.5, Java release 17, no Java-21-only APIs.
  The core jar ships no NMS and this wave adds none. Sight checks: `World.rayTraceBlocks` or Bukkit
  `LivingEntity.hasLineOfSight(Entity)`; never Paper's `hasLineOfSight(Location)`.
- `gangland-api` is additive only within major 2: no removal, rename or signature change of any public member (incl. the
  re-exported gangland-core types). New overloads/default methods only. One bump: 2.0 -> 2.1 (Task 3 does it).
- Runtime modules depend on `gangland-api` at provided scope only, never `gangland-impl`. The core (impl, api, core, infra, ui)
  never names a module class or a Bartizan type. cops-n-crooks may name Bartizan api types (`Plugins: [Bartizan]`).
- New user-facing strings and knobs go in the owning module's OWN YAML (cops-n-crooks: `npc/wanted.yml`,
  `npc/wanted_messages.yml`, `npc/cops.yml`, `npc/cop_radio_messages*.yml`), never in `Messages`/`Settings`, EXCEPT the
  settings.yml keys listed above. Existing server files are never rewritten, so every new key needs an in-code default
  equal to the shipped value.
- Every feature sits behind its own Enable key (Heat.Enable, Evasion.Enable, Hud.*.Enable, Charge_Sheet.Enable,
  Cops.Regroup.Enabled, Cops.Shot_Noise.Enabled, Wanted.Take_Money.Enable). Switching one off restores today's behaviour.
- Nothing from 0.16+: no regions/districts/place names, stations, setup wand, vehicle events, arrest-origin enum, stand-down
  flag, hot-cash bag, fence, contacts, surrender, bill-at-respawn, getaway XP, duty state, hideouts.

## Code style (house rules)
- Every method body has its opening and closing braces on their own lines, never `{ return x; }` on one line.
- Logging: Lombok `@CustomLog` + `log.warn/info/debug/error`; never `Bukkit.getLogger()`.
- Version-drifting enums via XSeries (`XParticle`, `XMaterial`, `XPotion`); sounds via Keystone
  `org.luckyraven.keystone.sound.SoundEffect` with the sound name as a String; never `Sound.X` / `player.playSound(.., Sound.X ..)`.
- Colour with `&` codes through `ChatUtil.color` / `GanglandChatUtil.color`; never a literal section sign in source.
- Action bar: leave it alone (Gang & Turf owns it). Titles via `ChatUtil.sendTitle`. Boss bars via `Bukkit.createBossBar`.
- `World getWorld()` is nullable: assign to a local and null-check before chaining.
- YAML: block-style maps only (no `{ k: v }` flow maps), keys `Capitalized_Underscore`, lookup ids lowercase, `&` colours,
  3-space indent in cops-n-crooks files (match the file you edit). Never `setDefaults()` / `copyDefaults()`.
- Listeners live in `<module pkg>.listener.<sub>` (auto-scanned); events in `<module pkg>.events.<area>` (api: `org.luckyraven.gangland.events.<area>`).
- Never put `@ListenerHandler` and `@Bean` on the same class. `@Bean` parameters are ordering edges; keep them.
- A class whose `@EventHandler` parameter is a Citizens type needs `@ListenerHandler(condition = "isCitizensAvailable")`.
- Bartizan-typed listeners only in modules with `Plugins: [Bartizan]` (cops-n-crooks qualifies).
- Async rule: `Timer.start(true)` only for flag flips; anything touching Bukkit/economy/events uses `start(false)` or the main thread.
- Ponytail: shortest correct diff, reuse what exists, no interface with one implementation unless it is a seam named in CONTRACTS.md.

## Tests (documentation/TESTING.md is authoritative; it overrides CLAUDE.md's stale testing paragraph)
- No pom edits; JUnit 6 + Mockito 5 (inline: final classes are mockable) + keystone-testkit are already wired. No MockBukkit.
- One test class per production class, mirrored package, `@DisplayName` on the class, a javadoc on every test class,
  behaviour-named methods. Seams: `BukkitStatics.install()` (try-with-resources; scheduler runs runnables inline),
  `PluginMocks`, `CapturingDiagnostics`, `StaticResets.resetAll()`; `BukkitRegistryFixture.install()` in `@BeforeAll`
  when code touches XSeries / `Material.isAir()` / ItemStack equality. Prefer small fakes over deep mock chains.
- Thread stubs: `BukkitStatics` (and a bare `mockStatic(Bukkit.class)`) leaves `Bukkit.isPrimaryThread()` false and runs
  `runTask` inline, so an "off-thread -> runTask(() -> sameMethod())" hop recurses until StackOverflowError. Main-thread paths
  stub `bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true)`; an off-thread hop test stubs
  `.thenReturn(false, true)` so the inline re-run takes the main-thread branch. `runTaskTimer`/`runTaskLater` are NOT run
  inline: verify them (sync vs `...Asynchronously`) or capture the Runnable.
- SQLite tests: `@TempDir(cleanup = CleanupMode.NEVER)`, disconnect every backend in `@AfterEach`, then `DbFiles.release(tempDir)`.
- Red first: write each new test against today's code, run it, see it FAIL for the right reason (assertion, or for a new
  type a compile error is acceptable only when no behavioural red is possible), then implement and see it pass. Say in
  your report how each test was red. A test that pins today's wrong behaviour (docket "pins") is flipped in the same change.
- Settings statics in impl tests: `SettingsFixture` (gangland-impl test tree). In module tests prime a single static by
  reflection like `CopRadioTest` does for `moneySymbol`, or stub the contract instead.
- Build (in your lane worktree, never `install`: `-am` resolves every sibling, test-jars included, from the reactor, and
  lanes must not race on `~/.m2`; only the orchestrator installs, after a merge): per module
  `mvn -q -pl <module> -am test -Dtest=<Class>[,<Class>...] -Dsurefire.failIfNoSpecifiedTests=false` (classes joined by
  COMMAS; `+` only joins method names after `#`, and `A+B` silently matches nothing), finally the module's full
  `mvn -q -pl <module> -am test`. A targeted run must print `Tests run: N` with N > 0 (drop `-q` if needed); zero means the
  filter is wrong, not that the test is green or red. Modules: `gangland-core`, `gangland-api`, `gangland-impl`,
  `gangland-features/cops-n-crooks`, `gangland-features/gangland-civilians`. ALWAYS `-am`.

## Bug docket
- Before changing a behaviour, look the bug up: Gangland docket artifact https://claude.ai/code/artifact/4102fb1f-20b0-44e9-b1b7-2893a559fa04
  (local sources: the main checkout's `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/bug-docket-2026-09-06/bugs.json`,
  which carries uncommitted rows newer than any worktree copy; ids `<CODE>-<nn>`). Read its fix direction and pinning tests.
- Workers do NOT edit `triage/*.txt`, `bugs.json` or the artifact db (parallel tasks would collide). Your final report lists:
  ids fixed (commit, test that covers it), ids touched but left open, and every NEW bug found (title, file:line,
  observation, impact, fix direction) for the orchestrator to add to `triage/<slug>.txt` and rebuild with `build_docket.py`.

## Ownership and commits
- Touch only the files your task OWNS. If you need a change in a file you do not own, stop and report it; do not edit.
- Commit on your lane branch, in your lane worktree, with a message `cnc-0.15 T<N>: <summary>` ending with exactly these two trailer lines:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01C6s11W4EoURsFJwH8BeF6h`. Never `--no-verify`, never force-push, never push.
- Done means: listed tests red-then-green, the touched module's full test suite green, `mvn -q clean verify -DskipTests`
  green for the reactor (in your lane worktree), no new warning-as-error, and the report above.
