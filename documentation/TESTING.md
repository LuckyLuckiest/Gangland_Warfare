# Testing conventions

House rules for the test suites grown out of the 2026-09-02 workflow audit
(`brainstorming/workflow-audit-2026-09-02/`). Concepts follow the sibling **Oriel** project
(`E:\Programming\java\Oriel`): small focused test classes next to the code they cover, a per-module
`support/` package for fixtures, real SQLite for anything that touches persistence, and `keystone-testkit`
for every Bukkit/Keystone seam.

## 1. Dependencies are already wired — do not touch any pom

The root `pom.xml` declares JUnit 5, Mockito, `keystone-testkit` and `sqlite-jdbc` in its **global
`<dependencies>` block**, so *every* module inherits them at test scope. A module grows a `src/test/java`
tree and it just works. Never add test dependencies to a module pom.

## 2. Where tests live

```
<module>/src/test/java/org/luckyraven/<pkg mirroring the class under test>/FooTest.java
<module>/src/test/java/org/luckyraven/<module root pkg>/support/…      # fixtures, fakes, builders
<module>/src/test/resources/…                                          # YAML fixtures
```

Mirror the production package exactly. One test class per production class, named `<Class>Test`. When a
single production class needs several very different setups, split by concern instead of growing a 600-line
file: `CaptureServiceOwnedTurfTest`, `CaptureServiceUnclaimedTest`.

## 3. Naming and documentation

- Test method names state the behaviour: `saveThenLoadAll_roundTrips`, `consumeMoreThanStock_clampsToZero`.
- Add `@DisplayName` on the class (and on non-obvious methods) with a human sentence.
- **Every test class gets a javadoc** saying what it proves and — when it comes from the audit — which
  observation it pins. Cite it as `Observation #<n> (<audit-file>.md)`.
- Java 17 only. Spigot APIs only, never `io.papermc.*`.
- Method braces on their own lines (`CLAUDE.md` rule) — in tests too.

## 4. keystone-testkit — the seams

| Need | Use |
|---|---|
| `Bukkit.getScheduler()` / `getPluginManager()` / `getServicesManager()` / `getServer()` | `try (BukkitStatics bukkit = BukkitStatics.install()) { … }` — scheduler runs submitted runnables **inline**, so timer logic is testable synchronously. Extra stubbing via `bukkit.statics().when(…)`. |
| A `JavaPlugin` | `PluginMocks.plugin(tempDir)` (disabled → async paths fall back to synchronous inline) or `PluginMocks.enabledPlugin(tempDir)` (pair with `BukkitStatics`). |
| SQLite JDBC URLs | `SqliteDbs.file(tempDir.resolve("x.db"))`, `SqliteDbs.inMemory()`, `SqliteDbs.inMemoryShared(name)`. |
| SQLite temp-file cleanup | `DbFiles.release(tempDir)` in `@AfterEach`. |
| Faults reported through `Diagnostics.active()` | `try (CapturingDiagnostics diagnostics = CapturingDiagnostics.install()) { … diagnostics.faults() … }`. |
| `SettingsLookup` for conditional beans | `new FakeSettingsLookup().enable("key")`. |
| Item NBT without an NBT provider | `NbtBridge.install(new RecordingNbtAccessor())`; `NbtBridge.reset()` in teardown. |
| Packet-layer calls | `PacketBridge.install(new RecordingPacketAdapter())`; `PacketBridge.reset()` in teardown. |
| `DatabaseHandler` setup shapes | `DatabaseSettingsMocks.sqliteOnly()` / `.sqliteWithBackup()` / `.mysqlWithSQLiteFallback()` / `.mysqlNoFallback()`. |
| Leaked process-wide statics | `StaticResets.resetAll()` in `@AfterEach` for any suite touching NMS version, bridges, or the resource-pack tracker. |

`BukkitStatics`, `CapturingDiagnostics` and the two bridges are **process-wide static state**. Always
close/reset them, or they leak into every later test on the thread.

## 4a. `BukkitRegistryFixture` — when Bukkit needs a server that isn't there

Lives in `gangland-core`'s test-jar (`org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture`). Modules that
need it declare:

```xml
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>gangland-core</artifactId>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

and call it from `@BeforeAll`:

```java
@BeforeAll
static void bootstrapBukkitRegistry() {
    BukkitRegistryFixture.install();
}
```

**When you need it.** On the 1.21 API `Material.isAir()` is no longer a switch — it resolves `asBlockType()`, which
reads the static `Registry.BLOCK`. `Registry`'s initialiser calls `Bukkit.getRegistry(...)` thirty times and
`requireNonNull`s every result, so a null `Bukkit.server` makes the class fail to initialise. XSeries lookups
(`XAttribute.ARMOR.get()`) and `ItemStack.clone()`/`equals()` (via `Bukkit.getItemFactory()`) hit the same wall.

**The failure is loud but misleading.** Once `Registry` has failed once, every later touch *in the same surefire
fork* throws `NoClassDefFoundError`, so a single unguarded call shows up as a cascade of errors in unrelated test
classes. Symptoms to recognise:

- `NoClassDefFoundError: Could not initialize class org.bukkit.Registry`
- `ExceptionInInitializerError` with `Bukkit.server is null` at the root
- a method that silently returns its input, because production took an `armorAttribute == null` early return

**Two traps it already handles**, so don't "simplify" them away:

1. Mockito's inline mock maker calls `Class.forName(name, true, loader)` on the type it mocks, and
   `Proxy.newProxyInstance` initialises the interfaces it implements. Either one applied to `Registry` *before* the
   server exists re-enters the failing initialiser. So every registry object is built **lazily, inside the
   `getRegistry` answer** — by then `Registry.<clinit>` is running on the same thread, and the JVM permits
   recursive initialisation from that thread.
2. `Registry` latches its static fields permanently on first touch, so `install()` must run before anything else in
   the fork reaches them — hence `@BeforeAll`, not `@BeforeEach`.

It is idempotent (`Bukkit.setServer` refuses to redefine the singleton, so the first caller wins) and composes with
`BukkitStatics`, which still intercepts everything while its try-with-resources block is open.

> **Upstream candidate:** this belongs in `keystone-testkit` beside `BukkitStatics`. It lives in `gangland-core` only
> to avoid a Keystone version bump mid-initiative.

## 4b. `BartizanBlindScan` / `BartizanReferenceScan` — proving a module is safe without Bartizan

Both live in `gangland-core`'s test-jar (`org.luckyraven.gangland.core.testsupport`), reachable the same way as
`BukkitRegistryFixture`/`CitizensBlindScan` (§4a — the identical `test-jar` pom dependency covers all four; no new
dependency needed per module). Written for the WS7 gadget wave (0.9.2, gates G5/G5b) when `gangland-gadget` and
`gangland-civilians` both dropped their hard `Plugins: [Bartizan]` module descriptor — any module whose `module.yml`
does **not** declare `Plugins: [Bartizan]` must survive Bartizan's absence, and these two tools are how you prove it
before a real Bartizan-less server does the proving for you.

**Why two tools, not one.** They check different, complementary things:

| Tool | Checks | Misses |
|---|---|---|
| `BartizanBlindScan` | Every `@Configuration`/`@ListenerHandler`/`@CommandHandler`/`@Repository` class's own declared method/constructor **signature** (`getDeclaredMethods()`/`getMethods()`/`getDeclaredConstructors()`), through a `URLClassLoader` that refuses to resolve `org.luckyraven.bartizan.*` by class name — exactly what Keystone's real bean/listener/repository scan does when Bartizan's jar is off the classpath, so a signature-level `NoClassDefFoundError` shows up here first, in a unit test, not on a live server. | A Bartizan type referenced only in a method **body** (a local variable, a `.class` literal, a field read) — the scan never calls `getDeclaredMethods()`'s equivalent for bytecode instructions, only for signatures. |
| `BartizanReferenceScan` | A raw JVM class-file constant-pool parser: walks every UTF8 constant-pool entry of every compiled `.class` under `target/classes` and flags any class whose pool contains `org/luckyraven/bartizan/` **anywhere** — body references included. | Nothing about *how* a reference is used, or whether it's actually guarded — it is a coarse presence check, compared against an explicit, hand-maintained allowlist, not a correctness proof on its own. |

**The lesson that motivated the second tool**: `BartizanNpcWeapons.create`/`buildItem` had Bartizan-free method
*signatures* (`BartizanBlindScan` passed clean) but referenced `BartizanApi.class` in their *bodies*, before any
`Settings.isBartizanAvailable()` guard — `Bukkit.getServicesManager().getRegistration(BartizanApi.class)` would fail
to resolve that class literal on a real Bartizan-less server, and the pre-existing `rsp == null` null-check never
got a chance to run (WS7 G5b fix round 1, review finding C2). **A Bartizan reference inside a method body needs the
allowlist, even when the method's own signature is already clean.**

**Using `BartizanBlindScan`**:
```java
@Test
void noScannedClassCrashesWithoutBartizan() {
    List<String> unsafe = BartizanBlindScan.findUnsafeClasses("org.luckyraven.gangland.<module>");
    assertTrue(unsafe.isEmpty(), "Classes unsafe without Bartizan: " + unsafe);
}
```
If a Bartizan-typed bean genuinely cannot be made signature-safe (e.g. it must return Bartizan's own interface to
publish under it, the one Gangland→Bartizan direction reversal `CombatEligibilityConfig` uses), isolate it into its
own small `@Configuration` class and assert the unsafe set equals exactly that one class, not empty — Keystone's
`ReflectionGuard.orSkip` skips only that one class per-`@Configuration`, so isolating the unsafe bean keeps every
sibling bean on a bigger config class safe. See `CiviliansBartizanBlindScanTest` for the worked example.

**Using `BartizanReferenceScan`** — build the allowlist test once per module, list every class your own audit found
that legitimately references Bartizan anywhere (signature or body):
```java
@Test
void onlyAuditedClassesReferenceBartizanAnywhere() throws IOException {
    Set<String> found = BartizanReferenceScan.findBartizanReferencingClasses(Path.of("target/classes"));
    Set<String> allowed = Set.of(
        "org.luckyraven.gangland.<module>....SomeClassThatMustTouchBartizan"
        // ... every other audited class
    );
    assertEquals(allowed, found);
}
```
`Path.of("target/classes")` resolves correctly relative to Surefire's own per-module working directory — no
`user.dir` workaround needed. **Adding a class to the allowlist**: only after confirming (a) the reference is
guarded by `Settings.isBartizanAvailable()` before the Bartizan-typed line is ever reached (or the class is
condition-gated so it's never constructed at all without Bartizan) and (b) it is not merely an unaudited leak —
then add its fully-qualified name to the `Set.of(...)` in your module's allowlist test, with a one-line comment
saying why it's there. **Removing** a class from the allowlist (because you deleted the Bartizan-touching code or
moved it out) should make the assertion go green with a *smaller* found set — if it doesn't, something else still
references Bartizan and the allowlist was hiding it.

Both scans should go **red first** against the un-fixed code when you add either test to a module for the first
time — temporarily revert the production fix (via Edit, never `git stash`), run the test, confirm the exact failure
line, then restore. See `GadgetBartizanBlindScanTest`/`GadgetBartizanReferenceAllowlistTest` and their civilians
counterparts for worked red-then-green examples (`exec/WS7/G5-report.md`'s "Fix round 1" section has the exact
quoted output for both).

## 5. Database tests (Windows rules — non-negotiable)

Follow `RankRepositorySpiTest` (`gangland-impl/src/test/java/org/luckyraven/gangland/database/repositories/rank/`):

```java
@TempDir(cleanup = CleanupMode.NEVER)   // Hikari holds the .db handle past the test on Windows
Path tempDir;

@BeforeEach void setUp() throws SQLException {
    backend = new SqliteBackend();
    backend.connect(SqliteDbs.file(tempDir.resolve("turf.db")));
    backend.applySchema(TableSchemas.fromTable(new TurfTable()));
    repository = new TurfRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
}

@AfterEach void tearDown() {
    backend.disconnect();
    DbFiles.release(tempDir);
}
```

- **Never** plain `@TempDir` — always `cleanup = CleanupMode.NEVER`.
- **Always** `disconnect()` before `DbFiles.release(...)`.
- Track every backend/handler a class creates so none leak.
- A repository under test needs `setDataSupplier(...)` before autosave/shutdown paths are exercised.

## 6. Prefer fakes over deep mock chains

Contracts (`TurfMessageContract`, `TurfSoundContract`, `GangLookupContract`, …) are small interfaces —
write a tiny recording fake in `support/` rather than a five-level `when(...).thenReturn(mock(...))` chain.
A fake that records calls into a `List` makes the assertion read as the behaviour being pinned. Reserve
Mockito for wide Bukkit interfaces (`Player`, `World`, `CommandSender`, `ItemStack`).

## 7. What to test, in priority order

1. **Pure logic** — the "Pure-logic candidates" bullets in each audit file's *Test Surface* section. Maths,
   state machines, parsing, clamping, boundary coordinates. No Bukkit at all where possible.
2. **Persistence round-trips** — real SQLite through the DatabaseBackend SPI. Null columns, numeric widths
   (`Long` vs `int` casts), `BigDecimal` precision, load-time pruning.
3. **Seam behaviour with testkit mocks** — listeners, trackers and tasks driven through `BukkitStatics`.
4. **Skip integration-only rows.** Anything the audit files list under *Integration-only (real server)*
   (Citizens NPCs, particle rendering, boss-bar visuals, Vault debits against a live economy) is out of
   scope — leave it to the manual checklists in `documentation/tests/`.

## 8. Audit observations: pin, don't fix

The audit's *Observations & Potential Issues* tables are **findings, not confirmed bugs**. When writing a
test for one:

- If the observation is **real**, write the test so it **passes against today's behaviour** and document the
  defect in the javadoc/`@DisplayName` — e.g. *"pins the current lossy `BigDecimal`→`Double` round trip
  (Observation #15, turf.md); tighten this assertion when the column becomes DECIMAL."* A red suite helps
  nobody; a green suite that documents the bug is a regression net for the fix.
- If the observation turns out to be **wrong**, say so in your report with the evidence. Do not write a test
  asserting something the code does not do.
- **Never change production code** to make a test pass. This initiative is test-only.

## 9. Running

```
mvn test                                   # everything
mvn test -pl gangland-features/gangland-turf     # one module (already installed siblings)
mvn test -pl gangland-features/gangland-turf -am # one module, building its deps too
mvn test -Dtest=CaptureServiceTest -pl <module>  # one class
```

Always run `mvn clean install -DskipTests` once first so sibling module jars resolve.
