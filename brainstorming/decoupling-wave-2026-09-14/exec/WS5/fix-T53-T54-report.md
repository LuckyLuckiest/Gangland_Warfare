# WS5 fix — T-53 (P0 boot blocker) + T-54 (P1 orphan repository warning)

Worktree `E:\Programming\java\wt\gangland-0.10.0-ws5`, branch `0.10.0-ws5`, HEAD `207e7282` (0.10.0 tip: all
workstreams landed — gangland-domain deleted, GanglandApi facade, WS6 G3). Nothing committed this round, per
instruction; `fix-T53-T54-package.diff` is the sole change artifact.

Both fixes are red-first verified (each red state reproduced and confirmed before the fix, then flipped green
after). ≤2 haiku subagents were not needed — both fixes were small, single-threaded, no doc sweep this round.

## T-53 (P0) — GangMembershipInstaller boot blocker

**Root cause**, confirmed by reading Keystone's `BeanFactory.instantiate()` source directly
(`E:\Programming\java\Keystone\keystone-bean\src\main\java\org\luckyraven\keystone\bean\BeanFactory.java`):
every registered `@Configuration` class is instantiated via its own constructor in **one up-front pass**,
entirely before any `@Bean` method runs in **any** phase. `GangMembershipInstaller` was a bare `@Configuration`
class whose 3-arg constructor named `GangMembership` (a `@Bean`-produced core type) — that can never resolve,
regardless of registration order, because config-class construction and `@Bean` invocation are two separate
passes with no interleaving. This is a shape mismatch, not an ordering bug: reordering `@Configuration`
registrations could never have fixed it.

**Fix**: `GangMembershipInstaller` is no longer its own `@Configuration` class. It is now produced by a new
`@Bean` factory method on `GangConfig`:

```java
@Bean
public GangMembershipInstaller gangMembershipInstaller(GangMembership gangMembership, MemberManager memberManager,
                                                        GangManager gangManager) {
    return new GangMembershipInstaller(gangMembership, memberManager, gangManager);
}
```

These 3 parameters are the real ordering edges (house rule `feedback_bean_ordering_via_params.md`) — they
guarantee `IdentityContractConfig.gangMembership()` (core) and `GangConfig`'s own `gangManager`/`memberManager`
beans have already run before this factory method is invoked. `@PostConstruct install()` needed no change —
confirmed against `BeanFactory.runPostConstruct`, which is called once for `configInstances` and once for
`allRegisteredBeans`, so it fires correctly on a factory-produced bean the same as a direct configuration
instance.

`GangModule.configure()` dropped the now-invalid `.configuration(GangMembershipInstaller.class)` registration.

**Files**:
- `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/gang/GangMembershipInstaller.java` —
  `@Configuration` annotation and import removed; javadoc explains why.
- `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/gang/GangConfig.java` — new
  `gangMembershipInstaller(...)` `@Bean` method appended.
- `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/gang/GangModule.java` — dropped the
  `.configuration(GangMembershipInstaller.class)` line; javadoc updated.
- `gangland-features/gangland-gang/src/test/java/org/luckyraven/gangland/gang/GangModuleTest.java` — assertion
  updated: `registrations.configurations()` is now `List.of(GangConfig.class)` only (was
  `List.of(GangConfig.class, GangMembershipInstaller.class)`).

**Red-first test** (new): `gangland-features/gangland-gang/src/test/java/org/luckyraven/gangland/gang/GangConfigBeanGraphTest.java`
— runs the **real** Keystone `BeanFactory`/`DependencyContainer` against the **real** `GangConfig`, not a
declaration-shape check. `gangland-gang` can never depend on `gangland-impl` (the module boundary this whole
gate protects), so a literal `IdentityContractConfig` can't be registered alongside it; the test instead
registers a minimal in-test fixture `@Configuration` class (`FixtureGangMembershipProducer`, zero-arg,
`@Bean`-produces `GangMembership`) that reproduces the exact same bug shape — a `@Bean`-produced type consumed
by a *different* configuration class's constructor — without violating the boundary. This substitution from
the ruling's literal wording is documented in the test's own class javadoc. Two tests:
1. `gangMembershipInstaller_resolvesAndInstalls` — `factory.instantiate()` must not throw, and
   `GangMembership.isInstalled()` must be true afterward.
2. `bareConfigurationClass_constructorInjectingABeanProducedType_fails` — a standalone minimal repro
   (`BareConfigurationNeedingABeanProducedType`) proving the general failure mode in isolation, independent of
   any fix to `GangMembershipInstaller` itself.

Confirmed genuinely red: reverting the fix (re-annotating `GangMembershipInstaller` as `@Configuration`,
removing `GangConfig`'s new bean method) reproduces the exact production error text:
`"Failed to instantiate @Configuration class ... GangMembershipInstaller ... Cannot resolve required parameter
of type GangMembership"`. Confirmed green after the fix — see gate results below.

**Secondary ask** (grep the module for any other bare `@Configuration` class with core-bean constructor
parameters): `grep -rln "@Configuration" gangland-features/gangland-gang/src/main/java` matches only 3 files —
`GangConfig.java` (the real, trivial-constructor configuration class), and `GangMembershipInstaller.java`/
`GangModule.java` (both matches are javadoc **prose** mentioning `@Configuration` in backticks, not actual
annotations — confirmed with content-mode greps). `GangConfig` is the only real `@Configuration` class left in
the module, matching `GangModuleTest`'s own assertion. No other instance of this hole exists.

## T-54 (P1) — orphan PermissionRepository data-supplier warning

**Root cause**: `RankManager.initialize()` (`gangland-features/gangland-gang/.../rank/RankManager.java`) is the
**only** caller of `PermissionRepository.setDataSupplier` in the whole reactor, resolving it generically via
`repositoryRegistry.getRepository(Permission.class)` — it never imports the concrete `PermissionRepository`
class. But `PermissionRepository`/`PermissionTable` lived in `gangland-impl`
(`database.repositories.plugin`/`database.tables.plugin`) and were scanned **unconditionally** by
`DatabaseConfig.ganglandDatabase()`'s core scan of `org.luckyraven.gangland.database.repositories` — so on a
gang-less server, the repository still got instantiated and registered, but nothing ever called
`setDataSupplier` on it, producing `"No data supplier set for repository: PermissionRepository"` on every
autosave/reload.

**Confirmed no other core caller** before moving anything: a precise import-only grep
(`grep -rln "import.*\.PermissionRepository;\|import.*\.PermissionTable;" --include="*.java" .`) matched only
the core file itself — zero other files anywhere in the reactor import either class by name. The "if nothing
in core uses them" condition in the ruling's preferred fix is satisfied.

**Fix** (preferred fix from the ruling, taken as-is): moved both classes into the gang module's own database
package, mirroring the existing `gang.database.{repositories,tables}.<entity>` convention already used for
`GangRepository`/`MemberRepository`/`RankRepository`/etc.:
- `org.luckyraven.gangland.database.repositories.plugin.PermissionRepository` →
  `org.luckyraven.gangland.gang.database.repositories.permission.PermissionRepository`
- `org.luckyraven.gangland.database.tables.plugin.PermissionTable` →
  `org.luckyraven.gangland.gang.database.tables.permission.PermissionTable`

Core's scan of `org.luckyraven.gangland.database.repositories` no longer finds either class (different package
tree entirely); the gang module's own scan (`GangModule.REPOSITORY_PACKAGE =
"org.luckyraven.gangland.gang.database"`, run by the module loader per `LoadedModule.registrations()`) picks
them up exactly as it already does for the module's other repositories. `RankManager` needed **no change** —
it only ever resolved `Permission`'s repository through the generic `IRepository<Permission>`/registry
interface, never by concrete class name.

**The `Permission` entity itself stays in `gangland-core`** (unchanged, per the ruling's §9 D3 reference) — only
the persistence classes (repository + table) moved. The database table name (`"permission"`) is unchanged, so
this is not a migration; existing data round-trips identically. `RankPermissionTable`'s existing
`ForeignPermissionTable` foreign-key stand-in (`gang.database.fk`) was left untouched — it declares the FK by
table name only and was never coupled to which package the real `PermissionTable` lives in; it predates this
move for the same module-boundary reason and remains valid regardless.

**Files**:
- `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/gang/database/repositories/permission/PermissionRepository.java`
  (moved from gangland-impl via `git mv`, package + `PermissionTable` import updated, javadoc added).
- `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/gang/database/tables/permission/PermissionTable.java`
  (moved from gangland-impl via `git mv`, package updated).

**Red-first test** (new): `gangland-impl/src/test/java/org/luckyraven/gangland/database/repositories/CoreRepositoryScanTest.java`
— runs the real `RepositoryRegistry.scanAndRegisterRepositories("org.luckyraven.gangland.database.repositories", ...)`
against the real classpath (mocked `JavaPlugin`/`DatabaseHandler`/`DatabaseBackend` only; every core repository
in that package has the same trivial `(JavaPlugin, DatabaseHandler, DatabaseBackend)` constructor, so the real
scan-and-construct path is exercised safely). Asserts `!registry.hasRepository(Permission.class)` after the
scan, plus a sanity check that a sibling repository that legitimately stayed in core
(`PluginDataRepository`/`PluginData`) is still found — proving the scan itself still works and the assertion
isn't vacuously true from a broken scan. The test names no concrete class it expects to be missing, so it
stays valid regardless of exactly where the class moved to.

Confirmed genuinely red before the move: `assertFalse(registry.hasRepository(Permission.class))` failed with
`expected: <false> but was: <true>` against the pre-move code (core's scan found `PermissionRepository` at its
old location). Confirmed green after the move — see gate results below.

## Gate

**Module gate** (`gangland-features/gangland-gang` + `gangland-impl`, `-am`): 0 failures, 0 errors (327 tests
by initial surefire `.txt` summation — superseded by the authoritative console-rollup figure below, consistent
with W52: `.txt` summation under-counts `@Nested` classes).

**Full reactor `mvn clean verify`** (never `install`): `BUILD SUCCESS`, exit 0, all 18 reactor modules.

**Console rollup** (W52-compliant: per-module Maven `Tests run:` summary lines from a non-quiet `mvn test`,
not surefire `.txt` summation):

| Module | Tests | Failures/Errors |
|---|---|---|
| Gangland Core | 67 | 0/0 |
| Gangland Item | 43 | 0/0 |
| Sign API | 63 | 0/0 |
| Gangland API | 14 | 0/0 |
| Gangland (impl) | 238 | 0/0 |
| Gangland Gangs | 104 | 0/0 |
| Gangland Mail | 25 | 0/0 |
| Gangland Civilians | 23 | 0/0 |
| Gangland Turf | 91 | 0/0 |
| Cops N Crooks | 76 | 0/0 |
| Gangland Gadgets | 113 | 0/0 |
| Gangland NPC Shops | 17 | 0/0 |
| Gangland Loot Chests | 53 | 0/0 |
| **Total** | **927** | **0/0** |

Reconciles exactly against the coordinator's baseline of 924: **924 + 3 = 927** — the 2 new
`GangConfigBeanGraphTest` tests (T-53) + the 1 new `CoreRepositoryScanTest` test (T-54), both accounted for
individually in the rollup (`"Tests run: 2, ... -- in GangConfig bean graph - real BeanFactory wiring (T-53)"`
under Gangland Gangs; `"Tests run: 1, ... -- in core repository package scan (T-54)"` under Gangland impl). No
other module's count moved.

**`mvn -pl gangland-build -am package -DskipTests` jar check**: `BUILD SUCCESS`, exit 0. Verified by content,
not just build exit code:
- `target/modules/gangland-gang-0.10.0.jar` contains
  `org/luckyraven/gangland/gang/database/repositories/permission/PermissionRepository.class` and
  `.../tables/permission/PermissionTable.class`.
- `target/gangland_warfare-0.10.0.jar` (core shaded jar) contains **zero** classes under any
  `database...permission` path — the old core-hosted classes are gone from the core jar, not just moved on
  disk.
- All 8 module jars present (`cops-n-crooks`, `gangland-civilians`, `gangland-gadget`, `gangland-gang`,
  `gangland-lootchest`, `gangland-mail`, `gangland-npc-shops`, `gangland-turf`), sizes consistent with a normal
  build.

## Not done this round (out of scope per the ruling)

- No commits — diff only, per instruction.
- No smoke test — the 0.10.0 lead re-runs the 8-module rows after fast-forwarding.
- No doc sweep — this ruling asked for code fix + red-first tests + gate only, unlike G5's explicit doc scope.
- Docket: T-53 and T-54 are the coordinator's own filed ids (already in the docket per the dispatch); this
  report is the fix record to attach to both rows — commit hash n/a (uncommitted), branch `0.10.0-ws5`, files
  and covering tests as listed above.
