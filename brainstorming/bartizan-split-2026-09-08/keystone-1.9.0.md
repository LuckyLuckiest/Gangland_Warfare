# Keystone 1.9.0 stream — item vocabularies, `WorldHeights` + reflective recoil, `keystone-npc`, `Plugins:`

**Repo:** `E:\Programming\java\Keystone` · branch **`phase-h8-item-npc`** (cut from `phase-h7-module-loader` @ `23950c0`,
v1.8.1) · `<revision>` **1.8.1 → 1.9.0** · phase doc `docs/phase-h8-item-npc.md`.
**Assumes done:** nothing from the other two streams. **This stream runs first** — Bartizan and Gangland 0.9.0 both
need `Keystone 1.9.0` in `~/.m2` before their first compile.
**Planner:** opus (P1), 2026-09-08. **Graph:** Keystone `graphify-out/graph.json` is **stale** (2026-09-04 vs HEAD
2026-09-08 `23950c0`); every line number below was re-verified against the raw file at that HEAD, either by me or by
the raw-file audits in `exploration/research/` — provenance is marked per section. Refresh with
`graphify update . --force` at the end (task K33).

---

## 0. Summary for the orchestrator

### What this stream produces

| Repo | Created | Changed | Deleted |
|---|---|---|---|
| Keystone | **29 main** files (4 in `keystone-item/spi`, 2 in `keystone-common`, 22 in the new `keystone-npc`, 1 `module.properties`) + **1 pom** (`keystone-npc/pom.xml`) + **14 test** files + **2 docs** | root `pom.xml`, `keystone-item` ×4, `keystone-common` ×4, `keystone-module` ×4, `keystone-persistence` ×1, `keystone-testkit` ×1, `keystone-plugin` pom + `plugin.yml`, `CLAUDE.md`, 6 existing docs, 5 existing test classes | none |
| Gangland | none | none (only the throwaway `<keystone.version>` bump in gate **K-G4**, reverted) | none — the cops-n-crooks NPC files are **copied**, not moved; the `git rm` happens in the Gangland 0.9.0 stream |
| Oriel | none | none (only the throwaway `libs.versions.toml` bump in gate **K-G5**, reverted) | none |

**Nothing is deleted from Gangland by this stream.** `keystone-npc` is populated by *copying* the 14 cops-n-crooks
NPC base files and rewriting them; Gangland keeps compiling against its own copies until the Gangland 0.9.0 stream
deletes them. That is what makes gate **K-G4** (0.8.4 still green on 1.9.0) meaningful.

### Seams introduced

| Seam | Shape | Where |
|---|---|---|
| `ItemVocabulary` / `ItemVocabularyRegistrar` | contribution (many providers, folded by `ItemVocabularies.install`) | `keystone-item` `…item.spi` |
| `NpcRangedAttack` | holder (one per NPC, safe default `NpcRangedAttack.NONE`) | `keystone-npc` `…npc.spi` |
| `NpcTargetFilter` | holder (safe default `NpcTargetFilter.ALL`) | `keystone-npc` `…npc.spi` |
| `NpcMarkDefaults` | holder (consumer supplies `Map<EntityType,String>`) | `keystone-npc` `…npc.spi` |
| `PacketAdapter.relativeCameraRotation` | **`default` method** on an existing interface — no compat break | `keystone-common` `…nms` |
| `Predicate<String> pluginEnabled` | injected into `ModuleResolution.resolve` (3-arg; 2-arg overload kept) | `keystone-module` |

### Order dependencies

- **Groups A → B → C → D → E → F run in order.** C and D need B done (`WorldHeights` is called from two moved
  classes). E is independent of A–D and may run in parallel with C/D by a second executor **only if** the root-pom
  edit in K2 has already landed.
- Nothing in this stream needs Bartizan or Gangland 0.9.0 on disk. The reverse is not true: `mvn clean install` in
  gate **K-G3** must finish before P2 (Bartizan) writes a line and before P3 (Gangland 0.9.0) cuts its branch.

### Task groups

| Group | Tasks | Files | Gate |
|---|---|---|---|
| **A** — item back-port + `item.spi` | K1–K8 | 11 (5 main, 5 test, 1 pom) | `mvn -pl keystone-item -am test -q` |
| **B** — `WorldHeights` + reflective recoil | K9–K13 | 11 (5 main, 6 test) | `mvn -pl keystone-common -am test -q` |
| **C** — `keystone-npc` skeleton + verbatim moves | K14–K19 | 14 (11 main, 1 pom, 1 properties, 1 root pom) | `mvn -pl keystone-npc -am test -q` |
| **D** — `keystone-npc` SPI surgery + tests | K20–K26 | 17 (11 main, 6 test) | `mvn -pl keystone-npc -am test -q` |
| **E** — `Plugins:` descriptor key + disable log | K27–K30 (incl. K29b) | 8 (4 main, 4 test) | `mvn -pl keystone-module -am test -q` |
| **E2** — `keystone-persistence` T-16 backup skip | KP1 | 2 (1 main, 1 test) | `mvn -pl keystone-persistence -am test -q` |
| **F** — shade, docs, version, gates | K31–K37 | 13 (2 build, 11 docs/verify) | `mvn clean install` + K-G4 + K-G5 |

### Three biggest risks

1. **The reflective recoil path is unit-testable but not unit-verifiable.** Synthetic-class tests prove the resolver
   picks the right constructor; only an in-server smoke run proves the packet the real server accepts. Mitigation:
   the `setRotation` fallback is always reachable, `nms.recoil.unsupported` is reported once, and in-game feel on
   1.21.11 + one ViaBackwards client is the user's check (already on the board). **The `-yaw + 1` / `pitch - 1`
   transform stays caller-side** (K12 §Watch out) — if the executor folds it into Keystone, every weapon's recoil
   inverts.
2. **`keystone-npc` puts a hard third-party dependency outside `keystone-hooks` and a `-SNAPSHOT` on the Central
   release path.** `citizens-main:2.0.42-SNAPSHOT` is `provided`, so it never ships, but it breaks Keystone's own
   rule in `CLAUDE.md` ("Hard third-party dependencies only in `keystone-hooks`"), makes an offline `mvn install`
   fail on a cold `~/.m2`, and would make `mvn deploy -P release` reject the module. K14 amends the rule, K31
   excludes `keystone-npc` from the release profile (Q-K2).
3. **`AbstractNpc`'s weapon surgery has zero test coverage today** (`exploration/research/npc-base-inventory.md` §I:
   14 test classes in cops-n-crooks, none touch the 14 moved files). The combat delegate's cooldown/reaction/burst
   arithmetic is being cut in half across a plugin boundary with no pin. K25 writes the pins **before** the surgery
   is trusted, and §3 lists exactly which behaviours must be red-first.

---

## Contract

Every name below is used **verbatim** by `bartizan.md` (P2) and `gangland-0.9.0.md` (P3). Names marked ★ are
additions this stream makes that `architecture/PICK.md` does not spell out — the consistency review must confirm the
other two checklists match them.

### Coordinates

- `org.luckyraven:keystone-*` version **`1.9.0`** (`<revision>` in `E:\Programming\java\Keystone\pom.xml:58`).
- New Maven module **`org.luckyraven:keystone-npc`**, declared in the root `<modules>` list after `keystone-module`.
- Server jar `E:\Programming\java\Keystone\keystone-plugin\target\Keystone-1.9.0.jar`.
- Citizens: `net.citizensnpcs:citizens-main:2.0.42-SNAPSHOT`, scope `provided`, repo id `citizens-repo` →
  `https://maven.citizensnpcs.co/repo`.

### `keystone-item` — `org.luckyraven.keystone.item`

```java
// ItemSerializerRegistry (back-port of Gangland's drift)
public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;
public void register(Predicate<ItemStack> predicate, ItemSerializer serializer);            // existing, priority 0
public void register(Predicate<ItemStack> predicate, ItemSerializer serializer, int priority);

// ItemRefresherRegistry
public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;
public void register(@Nullable ItemRefresher refresher);                                     // existing, priority 0
public void register(@Nullable ItemRefresher... items);                                      // existing, priority 0
public void register(@Nullable ItemRefresher refresher, int priority);

// ItemConverter — new default overload, existing method untouched
default @Nullable ItemStack convert(@Nullable Player viewer, String type,
                                    @Nullable String modifier, Map<String, String> attributes);

// ItemParser — new viewer overloads, existing signatures untouched
public Result<ItemStack> tryParse(@Nullable Player viewer, @Nullable String itemString);
public @Nullable ItemStack parse(@Nullable Player viewer, @Nullable String itemString);
```

### `keystone-item` — new package `org.luckyraven.keystone.item.spi`

```java
public interface ItemVocabulary {
    String namespace();
    void contribute(ItemVocabularyRegistrar registrar);
}

public interface ItemVocabularyRegistrar {
    void converter(String type, ItemConverter converter);
    void converter(String[] aliases, ItemConverter converter);
    void serializer(Predicate<ItemStack> claims, ItemSerializer serializer, int priority);
    void refresher(ItemRefresher refresher, int priority);
}

public final class ItemVocabularies {
    public static final String FAULT_VOCABULARY_FAILED = "item.vocabulary.failed";           // ★
    public static void install(Collection<? extends ItemVocabulary> vocabularies,
                               ItemConverterRegistry converters,
                               ItemSerializerRegistry serializers,
                               ItemRefresherRegistry refreshers);
}

public final class ItemDefinitions {
    public static @Nullable String describe(ItemSerializerRegistry serializers, @Nullable ItemStack stack);
    public static boolean sameDefinition(ItemSerializerRegistry serializers,
                                         @Nullable ItemStack a, @Nullable ItemStack b);
    public static @Nullable ItemStack pristine(ItemSerializerRegistry serializers,
                                               ItemConverterRegistry converters, @Nullable ItemStack stack);
}
```

`sameDefinition` semantics (★, pinned by `ItemDefinitionsTest`): describe both stacks; **both non-null** → string
equality; **both null** → `a.isSimilar(b)`; **exactly one null** → `false`; either argument null → `a == b`.

`ItemVocabularies.install` never touches Bukkit statics: the caller passes
`Bukkit.getServicesManager().getRegistrations(ItemVocabulary.class)` (mapped to providers). It iterates the
collection in encounter order, logs one `INFO` line per folded namespace, and isolates a throwing vocabulary behind
`Fault.internal(FAULT_VOCABULARY_FAILED, …)` reported through `Diagnostics.active()` before continuing with the rest.

### `keystone-common`

```java
// org.luckyraven.keystone.util.WorldHeights
public static int min(@Nullable World world);   // 0 when world is null or World#getMinHeight is unavailable
public static int max(@Nullable World world);   // 256 when world is null

// org.luckyraven.keystone.nms.PacketAdapter — DEFAULT method, no implementor breaks
default void relativeCameraRotation(Player viewer, float deltaYaw, float deltaPitch);
public static final String FAULT_RECOIL_UNSUPPORTED = "nms.recoil.unsupported";   // ★ on ReflectivePacketAdapter
```

The deltas are applied **verbatim and relative**. Keystone performs **no** sign flip, no `+1`/`-1` fudge and no
ViaVersion protocol gate — those stay in the caller (Bartizan's `RecoilManager`, which today computes
`newYaw = -yaw + 1; newPitch = pitch - 1` in `gangland-compatibility/version-impl/.../recoil/RecoilCompatibility.java:27-28`).

### `keystone-npc` — `org.luckyraven.keystone.npc`

```java
public abstract class AbstractNpc {
    protected AbstractNpc(JavaPlugin plugin, NPC npc, Location spawnLocation,
                          NpcNavigationConfig navConfig, NpcDifficulty difficulty);
    protected abstract boolean canUseRangedAttack();      // ★ renamed from canUseWeapons()
    protected abstract double  getAttackDamage();
    protected abstract void    equip();
    protected abstract void    cleanupTransientState();
    public void setRangedAttack(@Nullable NpcRangedAttack rangedAttack);   // ★ null → NpcRangedAttack.NONE
    public void setTargetFilter(@Nullable NpcTargetFilter targetFilter);   // ★ null → NpcTargetFilter.ALL
    public boolean isRangedAttacker();                    // ★ replaces isUsingRangedWeapon()
    public void destroy(@Nullable Consumer<Entity> onDespawn);
    public void destroy();
}

public interface NpcBehavior<T extends AbstractNpc> { … }        // moved verbatim
public enum      NpcDifficulty { EASY, NORMAL, HARD, DEADLY }    // moved verbatim
public interface NpcNavigationConfig { … }                       // moved verbatim
final class      NpcNavigationDelegate / NavStep / NavObstacle / NpcCombatDelegate    // package-private, co-located

public final class NpcSupport {
    public static final String FAULT_CITIZENS_MISSING = "npc.citizens.missing";   // consumers report it, not Keystone
    public static boolean available();
    public static Optional<NPCRegistry> registry();
    public static boolean isNpc(@Nullable Entity entity);
}

public final class NpcMetadata {
    public static final String TRADER_ID = "gangland.trader.id";
    public static final String BANKER_ID = "gangland.banker.id";
    public static final String TURF_ID   = "gangland.turfpowerup.turfid";
    public static final String MARK_KEY  = "entity_mark";        // ★ the NamespacedKey key NpcMarkManager uses
}
```

```java
// org.luckyraven.keystone.npc.spi
public interface NpcRangedAttack {
    NpcRangedAttack NONE = …;                              // ★ isRanged/isBusy/tryFire false, others no-ops
    boolean isRanged();
    boolean isBusy();
    boolean tryFire(LivingEntity target);
    void triggerReload();
    void refreshHeldItem();
    void onDestroy();
    /** Called once per NPC tick, from NpcCombatDelegate#decrementAttackCooldown. ★ */
    default void tick() { }
}
public interface NpcTargetFilter {
    NpcTargetFilter ALL = target -> true;                  // ★
    boolean isAttackable(LivingEntity target);
}
public interface NpcMarkDefaults {
    Map<EntityType, String> defaults();
}
```

`NpcRangedAttack`'s only production implementation is Bartizan's `NpcWeaponController`. Nothing in Keystone or
Gangland implements it; Gangland only hands one to `AbstractNpc.setRangedAttack(...)`.
*(R1 ruling (b), 2026-09-08 — `org.luckyraven.bartizan.api.npc.NpcWeaponController extends
org.luckyraven.keystone.npc.spi.NpcRangedAttack` and adds no methods of its own; `gangland-civilians` keeps only a
factory hook, `BartizanNpcWeapons`, which resolves `BartizanApi` from the `ServicesManager` at spawn time and
returns `NpcRangedAttack.NONE` when Bartizan is absent or the weapon name is unknown.)*
`tick()` is a `default` no-op so that `NONE` and any test double stay one-liners; the Bartizan controller overrides
it to expire its own `attackCooldown` (R1 **B3**).

```java

// org.luckyraven.keystone.npc.entity
public abstract class EntitySpawner<S extends EntitySpawnerPoint> implements BeanLifecycle { … }
public abstract class EntitySpawnerPoint { … }
public interface SpawnConfigProvider { … }
public class NpcMarkManager implements BeanLifecycle {      // ★ renamed from EntityMarkManager, String-typed
    public NpcMarkManager(JavaPlugin plugin, NpcMarkDefaults defaults);
    public void setMark(Entity entity, String mark);
    public @Nullable String getMark(Entity entity);         // ★ null = unmarked; consumer maps to its own sentinel
    public void removeMark(Entity entity);
    public void clearCache();
    public @Nullable String defaultMarkForType(EntityType type);
}

// org.luckyraven.keystone.npc.event
public abstract class NpcEvent extends Event { public AbstractNpc getNpc(); }
```

Persisted-data compatibility: `NpcMarkManager` builds `new NamespacedKey(plugin, NpcMetadata.MARK_KEY)` with the
**consumer's** plugin and stores the mark as a `PersistentDataType.STRING`, exactly as
`EntityMarkManager.java:24,50-52` does today. Gangland passes `EntityMark.name()`, so already-marked entities keep
their marks across the upgrade. `EntityMark`, `isCivilian()` and `countsForWanted()` **stay in Gangland**.

### `keystone-module`

```java
public record ModuleDescriptor(String id, String name, String version, String mainClass, String hostApi,
                               List<String> depends, List<String> plugins,        // ★ new component, after depends
                               @Nullable String artifact, Path jar) {
    public static final Pattern ID_PATTERN          = Pattern.compile("[a-z0-9][a-z0-9_-]*");
    public static final Pattern PLUGIN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
}

// module.yml key
Plugins:
   - Bartizan

public static ModuleResolution resolve(Collection<ModuleDescriptor> discovered, PluginVersion hostApi);
public static ModuleResolution resolve(Collection<ModuleDescriptor> discovered, PluginVersion hostApi,
                                       Predicate<String> pluginEnabled);
public static final String FAULT_PLUGIN_MISSING = "module.plugin.missing";
```

`ModuleLoader.load()` supplies `name -> { Plugin p = Bukkit.getPluginManager().getPlugin(name); return p != null && p.isEnabled(); }`.
A module whose plugin is absent or disabled is dropped **before** the dependency fixpoint, so its dependants cascade
out with `module.dependency.missing`.

Console contract the smoke harness greps for (`smoke/smoke.py`, matrix row "Every row / `stop`"): `ModuleLoader`
emits `Loaded module <id> <version> from <jar>` on load (unchanged, `ModuleLoader.java:188`) and, new in 1.9.0,
`Disabled module <id> <version>` per module on `disableAll()`. `DatabaseManager` emits
`Backup skipped for '<schema>': …` instead of `Failed to create a backup …` when no mirror engine is reachable.

### `keystone-plugin`

`plugin.yml` → `softdepend: [Vault, PlaceholderAPI, Citizens]`. `keystone-npc` added to the shade (compile-scope
dependency); `citizens-main` stays `provided` and is therefore **not** in `Keystone-1.9.0.jar`.

---

## 1. Inventory

### 1.1 `keystone-item` — what changes and why

Provenance: raw-file audit at HEAD `23950c0` (agent report, verified signatures) + my own read of the Gangland
originals.

| Keystone file | Lines today | Change | Gangland source of the drift |
|---|---|---|---|
| `keystone-item/src/main/java/org/luckyraven/keystone/item/ItemSerializerRegistry.java` | 60 | add `CATCH_ALL_PRIORITY` (`:24` field area), 3-arg `register`, `Entry` gains `int priority`, stable descending re-sort | `gangland-infra/gangland-item/src/main/java/org/luckyraven/gangland/item/ItemSerializerRegistry.java:32,40-43,63` |
| `…/item/ItemRefresherRegistry.java` | 72 | add `CATCH_ALL_PRIORITY` (`:20` field area), `register(ItemRefresher,int)`, wrap the plain `List<ItemRefresher>` in an `Entry` record, stable descending re-sort | `…/gangland/item/ItemRefresherRegistry.java:31,44-48,82` |
| `…/item/ItemConverter.java` | 32 | add the viewer `default` overload after `:30` | new (Oriel's `ItemProvider.resolve(Player, String)`, `menu-core/.../core/registry/ItemProvider.java:33-41`) |
| `…/item/ItemParser.java` | 113 | add `tryParse(Player,String)` + `parse(Player,String)`; the existing 1-arg forms delegate with `null` | new |
| `…/item/spi/ItemVocabulary.java` | — | **new** | new |
| `…/item/spi/ItemVocabularyRegistrar.java` | — | **new** | new |
| `…/item/spi/ItemVocabularies.java` | — | **new** | new |
| `…/item/spi/ItemDefinitions.java` | — | **new** | generalises `AbstractWeaponTradeSign.weaponSimilarityChecker` (`gangland-features/gangland-weapon/.../sign/AbstractWeaponTradeSign.java:60-68`) and `GanglandShopDisplayResolver` |

Facts the executor must not re-derive:

- Keystone's `ItemSerializerRegistry` has **no** priority today: `private final List<Entry> entries` (`:24`),
  `Entry(Predicate,ItemSerializer)` (`:58-59`), `serialize` walks in registration order (`:45-53`) and *continues*
  past an entry whose `extract` returns null/empty (`:50-52`). The composed value is
  `kind().label() + ":" + value.toLowerCase()` (`:53`).
- Keystone's `ItemRefresherRegistry` is a plain `List<ItemRefresher>` (`:20`) with `register(ItemRefresher)` (`:23`),
  varargs `register(ItemRefresher...)` (`:29`), `refresh` (`:41-52`) and `decorate` (`:58-69`), both falling back to
  `source.clone()`.
- Gangland's semantics to reproduce exactly: append then `entries.sort(Comparator.comparingInt(Entry::priority).reversed())`
  — `List.sort` is stable, so insertion order survives inside a priority tier.
- `ItemParser` is `@RequiredArgsConstructor` (`:34`) over `ItemConverterRegistry registry` (`:47`); fault ids
  `item.missing_type` (`:38`), `item.unknown_type` (`:40`), `item.conversion_failed` (`:42`); `parse` = `tryParse(...).orElse(null)` (`:57-60`).
- `ItemConverterRegistry.resolve(String)` (`:86-99`) already folds the bare-`Material` fallback.
- `@Nullable` in this repo is **`org.jetbrains.annotations.Nullable`** (root pom `:89`, `annotations` 26.1.0, provided).
- `keystone-item` **cannot** depend on `keystone-testkit` (testkit depends on item — `CLAUDE.md` "Module dependency
  graph"). Its tests use plain Mockito: `mock(ItemStack.class)` + `when(stack.getType())`
  (`ItemSerializerRegistryTest.java:38-39`). No test in `keystone-item` calls `new ItemStack(...)`.

### 1.2 `keystone-common` — recoil and world heights

| File | Lines today | Change |
|---|---|---|
| `keystone-common/src/main/java/org/luckyraven/keystone/util/WorldHeights.java` | — | **new** |
| `…/keystone/nms/PacketAdapter.java` | 56 | `default void relativeCameraRotation(Player, float, float)` after `setProperty` (`:48`) |
| `…/keystone/nms/internal/CameraRotationPackets.java` | — | **new** — the shape resolver |
| `…/keystone/nms/internal/ReflectivePacketAdapter.java` | 203 | override `relativeCameraRotation`; reuse `NmsCache` |
| `…/keystone/nms/internal/NmsCache.java` | 615 | expose the already-resolved send path as a package-private `send(Player, Object)` if `updateTitleThroughPackets` does not already offer one |
| `keystone-testkit/src/main/java/org/luckyraven/keystone/testkit/RecordingPacketAdapter.java` | 70 | record the new call |

Facts:

- `PacketAdapter` (56 lines) declares 5 methods + `enum FakeWindowType` (`:51-55`). Implementors in the whole
  organisation: `ReflectivePacketAdapter`, `PacketBridge.NoOpAdapter` (`:39-48`), `PacketBridgeTest.RecordingAdapter`
  (`:71-81`), `keystone-testkit RecordingPacketAdapter`. **Oriel implements none** — it only calls
  `PacketBridge.adapter()` (`menu-inventories/chest/.../ChestMenu.java:738,838`,
  `menu-inventories/furnace/.../FurnaceMenu.java:197,369,417`). Gangland references neither type. This is why a
  `default` method is chosen: zero implementors to update, zero compat break (see Q-K1).
- `NmsCache` (`nms/internal/NmsCache.java`) is the per-JVM reflective cache to mirror and reuse: volatile
  double-checked `get()` (`:72-84`), cached `Method getHandle`, `Field connectionField`, `Method sendPacket`
  (`:58-70`), helpers `craftClass(String)` (`:249-253`), `packetClass()` (`:255-264`),
  `firstField(Class,String...)` (`:266-278`), `locateSendPacket(Class,Class)` (`:280-297`). Build failure throws
  `IllegalStateException` (`:116-119`).
- There is **no** `MethodHandle` anywhere in `keystone-common`/`keystone-item` today; `WorldHeights` introduces the
  first one (A2 mandates it).
- `Fault` factories: `Fault.userError/internal/dependency(code, message)` → `Builder` with `.with(k,v)`, `.cause(t)`,
  `.build()` (`keystone-common/.../diagnostics/Fault.java:61-78,97-144`). Report through
  `Diagnostics.active()` (`Diagnostics.java:124-127`), which may be `null`.
- Report-once idiom in this repo: `AtomicBoolean` + `compareAndSet(false,true)`
  (`keystone-item/.../nbt/NoOpNbtAccessor.java:21,26-31`).
- `StaticResets` (testkit) already resets `PacketBridge` — any test touching the bridge must use it or
  `PacketBridge.reset()` in `@AfterEach` (`PacketBridgeTest.java:22-26`).

**Verified fact that voids E3's 🔴 risk 1:** `World#getMinHeight()` and `Entity#setRotation(float,float)` **are both
present in `spigot-api:1.16.5-R0.1-SNAPSHOT`**, the artifact Keystone compiles against. `javap` on
`~/.m2/repository/org/spigotmc/spigot-api/1.16.5-R0.1-SNAPSHOT/spigot-api-1.16.5-R0.1-SNAPSHOT.jar` reports
`public abstract int getMinHeight();` and `public abstract void setRotation(float, float);`; the same probe against
1.16.1/1.16.2/1.16.4 reports `getMinHeight` **absent**. So the NPC move has no compile blocker, and `WorldHeights`
is built (per PICK, mandatory) as runtime robustness for servers whose API predates 1.16.5, not as a compile fix.

### 1.3 The 20 recoil reference adapters — shapes for the reflective resolver

Provenance: raw-file audit of all 20 classes at Gangland HEAD `011ad30c`. **These files are the executor's
specification for K12.** They are deleted by the Gangland 0.9.0 stream, not by this one — read them before then.
All live at
`gangland-compatibility/version-1_xx_Ry/src/main/java/org/luckyraven/gangland/compatibility/version/recoil/Recoil_1_xx_Ry.java`.

| Shape | Modules (count) | Packet class | Constructor | Flag enum | Send path |
|---|---|---|---|---|---|
| **A1** | `version-1_16_R1`, `_R2`, `_R3` (3) | `net.minecraft.server.v1_16_Rx.PacketPlayOutPosition` | `(double,double,double,float,float,Set<EnumPlayerTeleportFlags>,int)` | nested `PacketPlayOutPosition.EnumPlayerTeleportFlags`, **named** `X,Y,Z,X_ROT,Y_ROT` | `((CraftPlayer)p).getHandle().playerConnection.sendPacket(packet)` |
| **A2a** | `version-1_17_R1`, `1_18_R1`, `1_18_R2`, `1_19_R1`, `1_19_R2` (5) | `net.minecraft.network.protocol.game.PacketPlayOutPosition` | `(double,double,double,float,float,Set,int,boolean)` — **trailing boolean** | nested `EnumPlayerTeleportFlags`, **obfuscated** `a,b,c,d,e` | `.getHandle().b.sendPacket(p)` (1.17) / `.b.a(p)` (1.18–1.19.2) |
| **A2b** | `version-1_19_R3`, `1_20_R1`, `1_20_R2`, `1_20_R3` (4) | `net.minecraft.network.protocol.game.PacketPlayOutPosition` | `(double,double,double,float,float,Set,int)` | `net.minecraft.world.entity.RelativeMovement`, **obfuscated** `a,b,c,d,e` | `.b.a(p)` / `.c.a(p)` / `.c.b(p)` |
| **B** | `version-1_20_R4`, `1_21_R1` (2) | `net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket` | `(double,double,double,float,float,Set<RelativeMovement>,int)` | `net.minecraft.world.entity.RelativeMovement`, **named** `X,Y,Z,X_ROT,Y_ROT` | `.getHandle().connection.send(packet)` |
| **C** | `version-1_21_R2`…`1_21_R7` (6) | `…game.ClientboundPlayerPositionPacket` | `(int teleportId, PositionMoveRotation, Set<Relative>)` with `new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, yaw, pitch)` | `net.minecraft.world.entity.Relative`, **named**, and the enum carries **more than five constants** (delta flags) — only `X,Y,Z,Y_ROT,X_ROT` are passed | `.getHandle().connection.send(packet)` |

Invariants across all 20, which the reflective implementation must reproduce:

- position deltas are always `0,0,0` (or `Vec3.ZERO`) — the packet moves nothing, it only rotates;
- teleport id is always `0`;
- the flag set is always the five relative flags `X, Y, Z, Y_ROT, X_ROT`;
- shapes A1/A2a/A2b/B additionally have an "absolute" variant `{X,Y,Z}` used when `position == false`; **the new
  Keystone method is relative-only and never builds the absolute set** (shape C's adapters already ignore the
  `position` flag entirely — `Recoil_1_21_R7.java:29` builds all five unconditionally);
- `newYaw = -yaw + 1; newPitch = pitch - 1;` is applied by **every** adapter *and* by the Bukkit fallback
  (`version-impl/.../recoil/RecoilCompatibility.java:27-28,37`) → it is caller policy, not packet policy.

Contract fallback (`version-impl`): `Compatibility.getRecoilCompatibility()`
(`version-impl/.../Compatibility.java:5-9`), `CompatibilityWorker` resolving via
`VersionedAdapterLoader.loadOrFallback(Compatibility.class, "org.luckyraven.gangland.compatibility.version", () -> null)`
(`CompatibilityWorker.java:28-29`), fallback gated on ViaVersion `getPlayerVersion(uuid) >= ProtocolVersion.v1_13`
(`RecoilCompatibility.java:32-33`) then `player.setRotation(loc.getYaw()+newYaw, loc.getPitch()+newPitch)` (`:37`).
Single production caller: `gangland-features/gangland-weapon/.../projectile/recoil/RecoilManager.java:100-102`.
Only existing test: `RecoilManagerTest` (mocks `RecoilCompatibility`; asserts nothing about packets).
Docs: `documentation/developer/compatibility.md:205-220` (adding a revision), `:174-175`, `:216` (Paper remapping).

### 1.4 `keystone-npc` — the 14 moved files

Provenance: `exploration/research/npc-base-inventory.md` (raw-file audit). Sources are under
`gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/`. **Copy, then rewrite. Do not
`git rm` the source** — the Gangland 0.9.0 stream owns the deletion.

| # | Gangland source (`path` · lines) | Keystone target | Verdict |
|---|---|---|---|
| 1 | `npc/NpcBehavior.java` · 24 | `keystone-npc/src/main/java/org/luckyraven/keystone/npc/NpcBehavior.java` | verbatim (package + import of `AbstractNpc`) |
| 2 | `npc/NpcDifficulty.java` · 57 | `…/npc/NpcDifficulty.java` | verbatim |
| 3 | `npc/NpcNavigationConfig.java` · 61 | `…/npc/NpcNavigationConfig.java` | verbatim; `getMinRepathAfterLossTicks()` (`:60`) is never read — keep it, flag in §6 |
| 4 | `npc/NavStep.java` · 28 | `…/npc/NavStep.java` | verbatim, package-private, co-located |
| 5 | `npc/NavObstacle.java` · 10 | `…/npc/NavObstacle.java` | verbatim, package-private, co-located |
| 6 | `npc/entity/EntitySpawnerPoint.java` · 29 | `…/npc/entity/EntitySpawnerPoint.java` | verbatim |
| 7 | `npc/entity/SpawnConfigProvider.java` · 75 | `…/npc/entity/SpawnConfigProvider.java` | verbatim (no imports at all) |
| 8 | `events/npc/NpcEvent.java` · 17 | `…/npc/event/NpcEvent.java` | verbatim + package/import change |
| 9 | `npc/entity/EntitySpawner.java` · 314 | `…/npc/entity/EntitySpawner.java` | verbatim **except** `getMinHeight()` at `:262` → `WorldHeights.min(world)` |
| 10 | `npc/NpcNavigationDelegate.java` · 1096 | `…/npc/NpcNavigationDelegate.java` | verbatim **except** `:795` `getMinHeight()` → `WorldHeights.min(world)` and `:742` `owner.isUsingRangedWeapon()` → `owner.isRangedAttacker()` |
| 11 | `npc/AbstractNpc.java` · 453 | `…/npc/AbstractNpc.java` | **surgery** — see 1.4a |
| 12 | `npc/NpcCombatDelegate.java` · 374 | `…/npc/NpcCombatDelegate.java` | **surgery** — see 1.4b |
| 13 | `npc/entity/EntityMarkManager.java` · 128 | `…/npc/entity/NpcMarkManager.java` | **rename + surgery** — see 1.4c |
| 14 | `npc/entity/EntityMark.java` · 17 | — | **stays in Gangland** (`countsForWanted()` is Gangland policy) |

New files with no Gangland source: `NpcSupport`, `CitizensBridge` (package-private), `NpcMetadata`,
`spi/NpcRangedAttack`, `spi/NpcTargetFilter`, `spi/NpcMarkDefaults`, plus
`keystone-npc/src/main/resources/org/luckyraven/keystone/npc/module.properties`.

**1.4a `AbstractNpc` (453 lines).** Fields today (`:26-52`): `NPC npc` `@Getter` (`:28`), `Location spawnLocation`
(`:30`), `int aiTickRate` (`:31`), `NpcNavigationDelegate navigation` (`:33`), `NpcCombatDelegate combat` (`:34`),
**`Weapon heldWeapon` `@Getter` (`:37`)**, **`boolean reloading` (`:38`)**, **`JavaPlugin plugin` (`:39`)**,
`NpcDifficulty difficulty` (`:42`), `markedForRemoval` (`:45`), `despawnTicks` (`:48`), `pursuitTicks` (`:51`),
`attackCooldown` (`:52`). Constructor `:54-68` `(NPC, Location, NpcNavigationConfig, NpcDifficulty)` builds both
delegates and defaults a null difficulty to `NORMAL`. `setHeldWeapon(Weapon, JavaPlugin)` (`:97`) is the **only**
place `plugin` is ever assigned (`:99`) — `CopNpcFactory.java:100-102` calls `setHeldWeapon(null, plugin)` purely to
smuggle the plugin in. `destroy(EntityMarkManager)` (`:131-147`) stops the weapon reload (`:133-135`), removes the
mark and despawns (`:137-142`), then `cleanupTransientState()` + `npc.destroy()` (`:144-146`).
`isUsingRangedWeapon()` (`:159`) = `canUseWeapons() && (heldWeapon != null || isHoldingVanillaRangedWeapon())`.
Consumers of `AbstractNpc`: `events/npc/NpcEvent.java:6,16`, `npc/NpcBehavior.java:8`,
`npc/police/npc/CopNpc.java:13,26`, `npc/civilian/npc/CivilianNpc.java:16,28`,
`npc/civilian/state/behavior/CivilianCombatBehavior.java:8`.

**1.4b `NpcCombatDelegate` (374 lines, package-private `final class` at `:31`).** Package-private API: `attack(Player,
boolean canUseWeapons, double attackDamage)` (`:50`), `attackEntity(LivingEntity, boolean, double)` (`:81`),
`canAttack` (`:105`), `decrementAttackCooldown` (`:113`), `faceTarget` (`:119`), `faceTargetEntity` (`:130`),
`refreshHeldItem` (`:143`), `isHoldingVanillaRangedWeapon` (`:160`), `performGanglandWeaponAttack` (`:175`),
`triggerReload` (`:193`), `performVanillaRangedAttack(Player,double)` (`:200`), `performMeleeAttack(Player,double)`
(`:222`), `performMeleeAttackOnEntity(LivingEntity,double)` (`:241`); private `performSingleShot` (`:260`),
`performAutoShot` (`:272`), `performBurstFire` (`:283`), `fireSingleRound` (`:309`), `applyAimError` (`:343`),
`applyReactionTimeOnTargetSwitch` (`:354`), `scaleCooldown(int)` (`:364`), `hasLineOfSight` (`:369`). The downed gate
is `DownedPlayerRegistry.isDowned(uuid)` at `:51`. Weapon imports to delete: `:15-19` (`SelectiveFire`,
`WeaponShootEvent`, `WeaponRaytracer`, `WeaponShooting`, `GunWeapon`) plus `:13` (`DownedPlayerRegistry`).
`keystone.item.ItemBuilder` (`:11`) leaves with `refreshHeldItem`; `keystone.timer.SequenceTimer` (`:14`) leaves with
`performBurstFire`.

**1.4c `EntityMarkManager` (128 lines).** Constructor `:23` `(JavaPlugin, CiviliansLoader)`; key built at `:24`
`new NamespacedKey(plugin, "entity_mark")`; defaults read from
`civiliansLoader.getLoadedConfig().defaultPoliceEntities()/defaultCivilianEntities()` (`:28-30`), re-read in
`onInitialize` (`:39`). Methods `onClear` (`:34`), `setEntityMark` (`:46`, PDC `STRING`, `mark.name()`),
`getEntityMark` (`:55`, cache → PDC `:61-73` → default), `isCivilian` (`:79`), `countsForWanted` (`:83`),
`removeEntityMark` (`:87`), `clearCache` (`:95`), `getDefaultMarkForType` (`:99`, config lists then the hardcoded
switch `:116-121`: `VILLAGER, WANDERING_TRADER, PLAYER → CIVILIAN`, `PILLAGER → POLICE`, else `UNSET`),
`processEntityTypes(List<String>)` (`:124`). **`isCivilian`, `countsForWanted`, the hardcoded switch and
`processEntityTypes` do not move** — they become Gangland's `GanglandMarkDefaults implements NpcMarkDefaults`.
Consumers (12 main + 1 test) are listed in `exploration/research/npc-base-inventory.md` §H and are all Gangland's
problem, not this stream's.

**Bukkit API used by the moved files vs the 1.16.5 floor** (`npc-base-inventory.md` §J): `getMinHeight()`
(`NpcNavigationDelegate.java:795`, `EntitySpawner.java:262`) → `WorldHeights.min`; `getMaxHeight()` (`:796`, `:263`)
fine; `rayTraceBlocks` (`NpcNavigationDelegate.java:666`) fine; `Tag.DOORS` (`:1024`) fine;
`world.rayTrace(...)` (`NpcCombatDelegate.java:208`) fine; `PersistentDataContainer` (`EntityMarkManager.java:6,50,62,90`)
1.14+, fine. No `Attribute.` or `Registry.` use. Citizens surface is small: `NPC` only in `AbstractNpc.java:6`
(`getEntity`, `isSpawned`, `despawn`, `destroy`, `isProtected/setProtected`) and fully-qualified
`NavigatorParameters` + `PathfinderType.MINECRAFT` in `NpcNavigationDelegate.java:477-482`.

### 1.5 `keystone-module` — the `Plugins:` key

| File | Lines | Change point |
|---|---|---|
| `keystone-module/src/main/java/org/luckyraven/keystone/module/ModuleDescriptor.java` | 84 | record header `:24-31`, `ID_PATTERN` `:33`, compact ctor `:35-45` |
| `…/module/ModuleDescriptorReader.java` | ~110 | `parse` `:68-98`; `Depends` block `:83-91` is the template; `Artifact` `:93-94`; construction `:96-97`; `invalid(Path,String,String)` `:100-106` |
| `…/module/ModuleResolution.java` | 150 | fault constants `:30-33`, `resolve` `:43`, host-api filter `:67-79`, dependency fixpoint `:81-100`, cycle detection `:102-137`, accessors `:143-149` |
| `…/module/ModuleLoader.java` | ~350 | `load()` `:135-192`, the single `resolve` call `:141`, `report(Fault)` `:340-349` |

Existing tests: `ModuleDescriptorReaderTest` (143 lines; `read_complete` `:30` with the YAML fixture at `:31-41`,
`read_defaults` `:58`, `read_missingMain` `:70`, `read_badId` `:82`, `read_dependsNotAList` `:93`,
`read_invalidYaml` `:104`, `read_noDescriptor` `:115`, `read_notAJar` `:125`); `ModuleResolutionTest` (125 lines;
`resolve_topologicalOrder` `:24`, `resolve_hostApi` `:41`, `resolve_missingDependency` `:57`, `resolve_cascade`
`:69`, `resolve_cycle` `:83`, `resolve_duplicateKeepsHigherVersion` `:95`); `ModuleLoaderTest` (277 lines; 11 tests,
`BukkitStatics.install()` used **only** around `factory.instantiate()` at `:104-106`).

### 1.6 Build and docs facts

- Root pom: `<revision>1.8.1</revision>` `:58`; `<modules>` `:42-52`; `<properties>` `:54-102`
  (`maven.compiler.release` 17 `:62`, `bukkit.version` `1.16.5-R0.1-SNAPSHOT` `:68`, `annotations.version` `:89`);
  `<repositories>` `:104-133` (7 repos, **no Citizens**); internal `<dependencyManagement>` `:138-179`
  (`keystone-module` at `:168-172`); `release` profile `:410-487` with
  `<excludeArtifacts><excludeArtifact>keystone-plugin</excludeArtifact></excludeArtifacts>` at `:480-482`.
- Template for a new module pom: `keystone-module/pom.xml` (it declares the bare surefire plugin at `:24-27` and the
  filtered-resources block at `:19-23`; `keystone-hooks/pom.xml` lacks the surefire declaration).
- `module.properties` convention: one file per logging package, content `module.name=${project.name}`, resolved by
  Maven resource filtering (e.g. `keystone-module/src/main/resources/org/luckyraven/keystone/module/module.properties`).
- `keystone-plugin/pom.xml`: `<finalName>Keystone-${project.version}</finalName>` `:17`; shade `:24-55` with **no**
  `artifactSet` — inclusion is scope-driven; dependency list `:59-109` (7 compile keystone modules; testkit/mockito
  test-only with the guard comment `:89-90`). `plugin.yml` (7 lines) ends with `softdepend: [Vault, PlaceholderAPI]`.
- Phase-doc format: `# Phase H<n> — <slug> (v<version>)`, one driver paragraph, then a `| | |` table with rows
  `Version` / `Driver` / `Compatibility`, then `## The inventory`, `## The rule`, `## What shipped`
  (`docs/phase-h7-module-loader.md:1-25`).
- `docs/README.md` table rows `:16-38`; the last phase row is `phase-h7-hotfix-1.8.1.md` at `:36`.
- `docs/keystone-item.md`: wiring recipe `## Wiring recipe (CONFIG phase)` at `:93`, test-count line `:191`
  (`110 tests cover this module.`).
- `docs/keystone-module.md`: descriptor-key section `:37-64` (YAML example `:41-50`, key table `:52-60`), fault table
  `:153-165`.
- `docs/extraction-roadmap.md` (351 lines): E5 header `:224`, E5 closing `:311-318`. The house convention for
  reopening a closed phase is an inline italic sentence `*Amended by H<phase> (v<version>):* …` appended to the
  original bullet (`:141-143`, `:209-214`).
- No `argLine`, no `mockito-inline`, no test-jar anywhere; surefire is version-pinned in `<pluginManagement>`
  (`:369-373`) and activated by a bare `<plugin>` entry per module.

### 1.7 Test baseline

`keystone-item` 110 tests (`docs/keystone-item.md:191`) across 12 test classes; the four registry/parser suites are
`ItemConverterRegistryTest` (14), `ItemParserTest` (12), `ItemSerializerRegistryTest` (10),
`ItemRefresherRegistryTest` (11). `keystone-common` nms tests: `CraftBukkitRevisionTest`, `NmsVersionTest`,
`PacketBridgeTest`, `VersionedAdapterLoaderTest`, `PlayerInputInterceptorTest` — none exercise
`ReflectivePacketAdapter`/`NmsCache` (deliberate; both are smoke-verified). `keystone-module`: 6 test classes.
cops-n-crooks has **no test touching any of the 14 moved NPC files** except `KillComboWantedTrackerTest`, which only
mocks `EntityMarkManager`.

---

## 2. Ordered tasks

Every task names exact files. A **compile gate** closes each group; do not start the next group with a red gate.
Paste the first 30 lines of any failure verbatim into §7.

---

### Group A — `keystone-item` back-port + the `item.spi` package (11 files)

#### K1 — Branch (1 command)
- **Do:** `cd E:\Programming\java\Keystone`; `git branch --show-current`. If it is not `phase-h8-item-npc`, run
  `git checkout -b phase-h8-item-npc` from `phase-h7-module-loader` (`23950c0`). **This is the only branch operation
  this checklist authorises**; every later task works on this branch and never switches.
- **Why:** Keystone `CLAUDE.md` forbids working on `master` or piling onto an existing phase branch.
- **Done when:** `git branch --show-current` prints `phase-h8-item-npc` and `git status --short` is clean.
- **Watch out:** `.claude/worktrees/zen-golick-63aff9` exists in this repo — never touch it.

#### K2 — Version bump (1 file)
- **Do:** `E:\Programming\java\Keystone\pom.xml:58` — `<revision>1.8.1</revision>` → `<revision>1.9.0</revision>`.
- **Why:** every artifact this stream installs must be 1.9.0, and the two downstream streams resolve `1.9.0` from `~/.m2`.
- **Done when:** `grep -n "<revision>" pom.xml` shows `1.9.0`.
- **Watch out:** this is the only place the version lives (`flatten-maven-plugin` resolves `${revision}`). Do not
  edit any child pom's version.

#### K3 — `ItemSerializerRegistry` priority overload (1 file)
- **Do:** in `keystone-item/src/main/java/org/luckyraven/keystone/item/ItemSerializerRegistry.java`:
  1. add `public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;` above the `entries` field (`:24`), with
     the javadoc copied from `gangland-infra/gangland-item/.../ItemSerializerRegistry.java:28-31`;
  2. change the `Entry` record (`:58-59`) to `private record Entry(Predicate<ItemStack> predicate, ItemSerializer serializer, int priority) {}`;
  3. keep the existing `register(Predicate, ItemSerializer)` (`:27-31`) with its null checks and make its body
     `register(predicate, serializer, 0);`;
  4. add `public void register(Predicate<ItemStack> predicate, ItemSerializer serializer, int priority)` doing the
     same null checks, then `entries.add(new Entry(predicate, serializer, priority)); entries.sort(Comparator.comparingInt(Entry::priority).reversed());`;
  5. extend the class javadoc (`:14`) to say registration order is priority order **within a tier** and that the sort
     is stable.
- **Why:** Gangland's core registers `MATERIAL` at `CATCH_ALL_PRIORITY` (`gangland-impl/.../config/ItemConfig.java:96`)
  and modules register afterwards at the default; deleting Gangland's copy without this changes which serializer wins.
- **Done when:** `mvn -pl keystone-item -am test -q -Dtest=ItemSerializerRegistryTest` is green (the 10 existing
  tests must still pass unchanged).
- **Watch out:** `List.sort` is stable — do **not** use a `TreeSet`/`PriorityQueue`. Keep `serialize`'s
  "continue past a null/empty extract" behaviour (`:50-52`) exactly.

#### K4 — `ItemRefresherRegistry` priority overload (1 file)
- **Do:** in `…/item/ItemRefresherRegistry.java`:
  1. add `public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;`;
  2. replace `private final List<ItemRefresher> refreshers` (`:20`) with `private final List<Entry> entries` plus
     `private record Entry(ItemRefresher refresher, int priority) {}`;
  3. `register(@Nullable ItemRefresher)` (`:23-26`) → delegates to `register(refresher, 0)`;
     `register(@Nullable ItemRefresher...)` (`:29-32`) → loops delegating at priority 0; add
     `public void register(@Nullable ItemRefresher refresher, int priority)` which ignores null, appends and
     stable-sorts descending;
  4. rewrite the `refresh` (`:41-52`) and `decorate` (`:58-69`) loops to walk `entries` and read `entry.refresher()`,
     preserving every existing behaviour: null/air passes through untouched, a claiming refresher returning null
     falls through, nothing claimed → `source.clone()`.
- **Why:** the weapon/wearable refreshers register at priority 10 to outrank the core's unique refresher
  (`gangland-features/gangland-weapon/.../WeaponModuleConfig.java:204-206`); a unique weapon carries both NBT tags.
- **Done when:** `mvn -pl keystone-item -am test -q -Dtest=ItemRefresherRegistryTest,ItemRefresherDecorateTest` green
  (21 existing tests unchanged, including `varargsPreservesOrder`).
- **Watch out:** the varargs overload and the new 2-arg overload must not become ambiguous — `register(x)` binds to
  `register(ItemRefresher)`, `register(x, 10)` to the int overload. Compile is the proof.

#### K5 — Viewer-aware `ItemConverter` + `ItemParser` (2 files)
- **Do:**
  1. `…/item/ItemConverter.java` — add below `convert` (`:30`):
     ```java
     @Nullable
     default ItemStack convert(@Nullable Player viewer, String type, @Nullable String modifier,
                               Map<String, String> attributes) {
         return convert(type, modifier, attributes);
     }
     ```
     with javadoc saying the viewer is advisory (per-player placeholders/permissions) and that implementations
     ignoring it must stay correct. Import `org.bukkit.entity.Player`.
  2. `…/item/ItemParser.java` — add `public Result<ItemStack> tryParse(@Nullable Player viewer, @Nullable String itemString)`
     containing today's body from `:67-111` but calling `converter.convert(viewer, type, modifier, attributes)` at
     the `:103` site; make the existing `tryParse(String)` (`:67`) delegate with `null`; add
     `public @Nullable ItemStack parse(@Nullable Player viewer, @Nullable String itemString)` =
     `tryParse(viewer, itemString).orElse(null)`; leave `parse(String)` (`:57-60`) as-is.
- **Why:** Oriel's `ItemProvider.resolve(Player viewer, String key)`
  (`E:\Programming\java\Oriel\menu-core/.../core/registry/ItemProvider.java:33-41`) cannot converge on
  `keystone-item` without it; Gangland threads the player through refreshers today.
- **Done when:** `mvn -pl keystone-item -am test -q -Dtest=ItemParserTest` green (12 tests unchanged).
- **Watch out:** do not duplicate the parsing body — one private implementation, two public entry points, or the
  fault ids drift. Keep all three fault codes and their `.with("input", …)` / `.with("type", …)` context keys.

#### K6 — `org.luckyraven.keystone.item.spi` — vocabulary (3 files)
- **Do:** create
  `keystone-item/src/main/java/org/luckyraven/keystone/item/spi/ItemVocabulary.java`,
  `…/spi/ItemVocabularyRegistrar.java` and `…/spi/ItemVocabularies.java` exactly as in **Contract**.
  `ItemVocabularies` is `final`, has a private constructor, is `@lombok.CustomLog`, and `install`:
  1. returns immediately on a null/empty collection;
  2. for each vocabulary, in encounter order: skip + `log.warn` if `namespace()` is null/blank or already seen in
     this call; build a package-private `RegistrarImpl` bound to the three registries; call `contribute(registrar)`
     inside `try { … } catch (Throwable t) { report(); continue; }` where `report()` builds
     `Fault.internal(FAULT_VOCABULARY_FAILED, "Item vocabulary '" + ns + "' failed to contribute").cause(t).with("namespace", ns).build()`
     and hands it to `Diagnostics.active()` when that is non-null (else `log.warn`);
  3. logs one line per successful vocabulary: `log.info("Item vocabulary {} contributed {} converter(s), {} serializer(s), {} refresher(s)", ns, c, s, r);`
     with the counts taken from `RegistrarImpl`.
  `RegistrarImpl` is a package-private static nested class of `ItemVocabularies` implementing
  `ItemVocabularyRegistrar`; each method delegates to the matching registry method and increments its counter.
- **Why:** Bartizan is a separate plugin whose `onEnable` runs before Gangland's bean graph exists, so it publishes
  on the `ServicesManager` and Gangland pulls. Keystone forbids a shared static registry
  (`CLAUDE.md` "Shared-classloader constraint"), so the folding helper must be stateless.
- **Done when:** the file compiles and `grep -rn "Bukkit\." keystone-item/src/main/java/org/luckyraven/keystone/item/spi/` returns nothing.
- **Watch out:** **no Bukkit statics inside** — `install` must never call `Bukkit.getServicesManager()`. The boot log
  line is required (A2 risk 3: a server that drops the `softdepend` silently loses `weapon:` loot).

#### K7 — `ItemDefinitions` (1 file)
- **Do:** create `…/item/spi/ItemDefinitions.java`, `final`, private constructor, three static methods per
  **Contract**. Implementations:
  - `describe` → `stack == null ? null : serializers.serialize(stack)`;
  - `sameDefinition` → `if (a == null || b == null) return a == b;` then `String da = describe(serializers,a), db = describe(serializers,b);`
    `if (da != null && db != null) return da.equals(db);` `if (da == null && db == null) return a.isSimilar(b);` `return false;`
  - `pristine` → `String def = describe(serializers, stack); return def == null ? null : new ItemParser(converters).parse(def);`
- **Why:** this single serialize→definition→convert round trip is what replaces Gangland's
  `weaponSimilarityChecker` (`gangland-features/gangland-weapon/.../sign/AbstractWeaponTradeSign.java:60-68`),
  `ShopDisplayNameProvider` and the six weapon sign types once weapons live in another plugin.
- **Done when:** compiles; `ItemDefinitionsTest` (K8) green.
- **Watch out:** `pristine` deliberately loses runtime state (durability, ammo, uses) — that is the point; say so in
  the javadoc so nobody "fixes" it. Do not cache the `ItemParser`; it is a two-field object and the registries are
  per-consumer.

#### K8 — Group A tests (5 files)
- **Do:** create under `keystone-item/src/test/java/org/luckyraven/keystone/item/`:
  1. `ItemSerializerRegistryPriorityTest` — port
     `gangland-infra/gangland-item/src/test/java/org/luckyraven/gangland/item/ItemSerializerRegistryPriorityTest.java`
     (72 lines, 2 tests: `serialize_catchAllRegisteredFirst_stillLosesToALaterSpecificSerializer`,
     `serialize_twoDefaultPrioritySerializers_keepInsertionOrder`). **Two mandatory edits during the port:** replace
     `new ItemStack(Material.IRON_SWORD)` with `mock(ItemStack.class)` + `when(stack.getType()).thenReturn(Material.IRON_SWORD)`
     (keystone-item house style — no live server), and replace Gangland's `ItemKind.MATERIAL`/`ItemKind.CAR` with
     `StandardItemKind.MATERIAL` / `ItemKind.of("car")`.
  2. `ItemRefresherRegistryPriorityTest` — port
     `…/gangland/item/ItemRefresherRegistryPriorityTest.java` (121 lines, 3 tests:
     `refresh_higherPriorityRegisteredLater_stillWins` `:71`,
     `refresh_twoDefaultPriorityRefreshers_keepInsertionOrder` `:89`,
     `refresh_defaultPriorityRegisteredAfterAnotherDefault_staysBehindIt` `:106`), same two edits.
  3. `ItemConverterViewerOverloadTest` — 3 tests: a converter overriding only the 3-arg form receives the call
     through the 4-arg default; a converter overriding the 4-arg form sees the viewer instance;
     `parser.parse(viewer, "unique:x")` reaches the converter with that viewer (Mockito `ArgumentCaptor`).
  4. `spi/ItemVocabulariesTest` — 5 tests: two vocabularies fold into the three registries and priorities are
     honoured; a vocabulary whose `contribute` throws does not stop the next one and produces exactly one
     `item.vocabulary.failed` fault (use `keystone-item`'s own capture — a hand-written `Diagnostics` sink installed
     and restored in `@BeforeEach`/`@AfterEach`, **not** `CapturingDiagnostics`, which lives in the testkit that
     `keystone-item` may not depend on); a duplicate namespace in one call is skipped; an empty/null collection is a
     no-op; alias arrays register every alias.
  5. `spi/ItemDefinitionsTest` — 6 tests, one per branch of `sameDefinition` (both described + equal, both described
     + different, neither described → `isSimilar` true, neither described → `isSimilar` false, one described,
     null argument) plus `pristine` round-tripping `unique:x` through a stub converter and `pristine` returning null
     for an undescribable stack.
- **Why:** the priority overloads are load-bearing and their only pins live in Gangland, which is about to delete
  them; the four new types have no coverage at all.
- **Done when:** `mvn -pl keystone-item -am test -q` green and the test count in `docs/keystone-item.md:191` can be
  updated to the number surefire reports (K34).
- **Watch out:** **red first.** Before K3/K4 land, the two ported priority tests must fail to compile
  (`CATCH_ALL_PRIORITY` does not exist) — if you write them after the fix, temporarily comment out the priority
  argument, run, and record the failure. `keystone-item` has **no** `keystone-testkit` dependency and must not gain
  one (Maven cycle: testkit → item).

> **GATE K-GA:** `mvn -pl keystone-item -am test -q` — green, and the four pre-existing registry/parser suites
> (47 tests) unchanged.

---

### Group B — `WorldHeights` + reflective relative camera rotation (11 files)

#### K9 — `WorldHeights` (1 file + 1 test)
- **Do:** create `keystone-common/src/main/java/org/luckyraven/keystone/util/WorldHeights.java`: `final`, private
  constructor, `@CustomLog`. A `private static final MethodHandle MIN_HEIGHT` resolved once in a static initializer
  via `MethodHandles.lookup().findVirtual(World.class, "getMinHeight", MethodType.methodType(int.class))`, set to
  `null` on any `ReflectiveOperationException` (log once at DEBUG). `public static int min(@Nullable World world)`
  returns `0` when `world == null` or `MIN_HEIGHT == null`, else invokes it, returning `0` on `Throwable`.
  `public static int max(@Nullable World world)` returns `world == null ? 256 : world.getMaxHeight()`.
  Create `keystone-common/src/test/java/org/luckyraven/keystone/util/WorldHeightsTest.java` — 4 tests: `min` on a
  mocked `World` returning `-64`; `min(null) == 0`; `max` on a mocked `World` returning `320`; `max(null) == 256`.
- **Why:** the two moved NPC classes call `World#getMinHeight()`, which is present in the 1.16.5 API jar but absent
  from 1.16.1–1.16.4 servers that `api-version: 1.16` still admits (§1.2). PICK makes the helper mandatory.
- **Done when:** `mvn -pl keystone-common -am test -q -Dtest=WorldHeightsTest` green.
- **Watch out:** the `MethodHandle` must be resolved once in a static initializer, never per call. Do not make
  `min` throw: a height helper that throws inside a pathfinding loop is worse than a wrong floor.

#### K10 — `PacketAdapter.relativeCameraRotation` (1 file)
- **Do:** in `keystone-common/src/main/java/org/luckyraven/keystone/nms/PacketAdapter.java`, after `setProperty`
  (`:48`), add:
  ```java
  default void relativeCameraRotation(Player viewer, float deltaYaw, float deltaPitch) {
      Location location = viewer.getLocation();
      viewer.setRotation(location.getYaw() + deltaYaw, location.getPitch() + deltaPitch);
  }
  ```
  Javadoc must state: the deltas are **relative** and applied verbatim (no sign flip, no clamping); the default is
  the guaranteed Bukkit fallback and visibly teleports the camera on some clients; a packet-capable implementation
  should override it; the caller owns any protocol gating.
- **Why:** PICK refinement 1 — reflective packets with `setRotation` as the guaranteed fallback.
- **Done when:** `mvn -pl keystone-common -am test -q` still green with no other file touched.
- **Watch out:** `default`, not abstract — `PacketBridge.NoOpAdapter` (`PacketBridge.java:39-48`),
  `PacketBridgeTest.RecordingAdapter` (`:71-81`) and any third-party implementor must keep compiling (Q-K1).
  `PacketBridge.NoOpAdapter` **deliberately inherits the default** (a camera kick that silently does nothing is
  indistinguishable from a broken weapon); say so in `PacketBridge`'s javadoc.

#### K11 — `CameraRotationPackets` shape resolver (1 file)
- **Do:** create `keystone-common/src/main/java/org/luckyraven/keystone/nms/internal/CameraRotationPackets.java`,
  package-private `final class`, `@CustomLog`. Shape:
  ```java
  interface Shape { Object build(float deltaYaw, float deltaPitch) throws ReflectiveOperationException; }
  @Nullable static Shape resolve();                                   // per-JVM, uses NmsCache's class lookups
  @Nullable static Shape shapeFor(Class<?> packetClass, Class<?> flagEnum,
                                  @Nullable Class<?> moveRotation, @Nullable Class<?> vec3);   // visible for tests
  static Object[] relativeFlags(Class<?> flagEnum);                    // visible for tests
  ```
  - `relativeFlags` returns the five relative constants: if the enum declares a constant literally named `X`, pick
    the constants named `X, Y, Z, Y_ROT, X_ROT`; otherwise (obfuscated builds) take the first five enum constants by
    ordinal. Return them as an array; the caller wraps in `Set.of(...)`/`new HashSet<>(...)`.
  - `shapeFor` probes `packetClass.getDeclaredConstructors()` and selects, in this order:
    1. **Shape C** — 3 params `(int, moveRotation, Set)` with a `moveRotation` constructor
       `(vec3, vec3, float, float)` and a `Vec3.ZERO`-equivalent static field of type `vec3`: build
       `ctor.newInstance(0, moveRotation.newInstance(zero, zero, deltaYaw, deltaPitch), flagSet)`;
    2. **Shape A2a** — 8 params `(double,double,double,float,float,Set,int,boolean)`:
       `newInstance(0d,0d,0d,deltaYaw,deltaPitch,flagSet,0,false)`;
    3. **Shape A1/A2b/B** — 7 params `(double,double,double,float,float,Set,int)`:
       `newInstance(0d,0d,0d,deltaYaw,deltaPitch,flagSet,0)`;
    otherwise return `null`.
  - `resolve()` finds the packet class by trying, in order, `net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket`,
    `net.minecraft.network.protocol.game.PacketPlayOutPosition`, then
    `net.minecraft.server.<revision>.PacketPlayOutPosition` using `CraftBukkitRevision.current().name()`; and the
    flag enum by trying `net.minecraft.world.entity.Relative`, `net.minecraft.world.entity.RelativeMovement`, then
    the nested `<packetClass>$EnumPlayerTeleportFlags`. `moveRotation` = `net.minecraft.world.entity.PositionMoveRotation`,
    `vec3` = `net.minecraft.world.phys.Vec3` (both optional). Resolve the flag constants once and cache the built
    `Set` inside the returned `Shape`.
- **Why:** the 20 `Recoil_1_xx_Ry` classes (§1.3) collapse to exactly three constructor arities once the flag enum
  is resolved by name/ordinal; that is what lets Bartizan ship zero version modules.
- **Done when:** `CameraRotationPacketsTest` (K13) green against synthetic fixture classes.
- **Watch out:** shape C's `Relative` enum has **more than five constants** (delta flags) — `getEnumConstants()`
  wholesale is wrong; the named/first-five rule in `relativeFlags` is the whole point. Constructor parameter types
  for the flag argument erase to `java.util.Set` — match on `Set.class.isAssignableFrom(param)`, never on generics.
  Never hardcode a CraftBukkit revision.

#### K12 — `ReflectivePacketAdapter.relativeCameraRotation` (2 files)
- **Do:**
  1. In `keystone-common/src/main/java/org/luckyraven/keystone/nms/internal/NmsCache.java`: if
     `updateTitleThroughPackets` (`ReflectivePacketAdapter.java:114-131`) does not already route through a reusable
     helper, add a package-private `void send(Player viewer, Object packet) throws ReflectiveOperationException`
     using the already-cached `getHandle` / `connectionField` / `sendPacket` members (`:58-70`). **Do not add new
     field-name probing** — those three are exactly the connection path the 20 adapters hardcode per revision.
  2. In `ReflectivePacketAdapter.java`, add:
     ```java
     private static final AtomicBoolean RECOIL_WARNED = new AtomicBoolean();
     private static volatile CameraRotationPackets.Shape recoilShape;   // resolved once, lazily
     public static final String FAULT_RECOIL_UNSUPPORTED = "nms.recoil.unsupported";
     @Override public void relativeCameraRotation(Player viewer, float deltaYaw, float deltaPitch) { … }
     ```
     The override resolves the shape once (double-checked, mirroring `NmsCache.get()` `:72-84`); on a null shape or
     any `ReflectiveOperationException`/`RuntimeException` it reports the fault **once** (guarded by
     `RECOIL_WARNED.compareAndSet(false,true)`, mirroring `NoOpNbtAccessor.warnOnce()`
     `keystone-item/.../nbt/NoOpNbtAccessor.java:26-31`) through `Diagnostics.active()` as
     `Fault.dependency(FAULT_RECOIL_UNSUPPORTED, "No known camera-rotation packet shape for this server; falling back to setRotation").with("server", Bukkit.getBukkitVersion()).cause(t).build()`
     and then calls `PacketAdapter.super.relativeCameraRotation(viewer, deltaYaw, deltaPitch)`.
- **Why:** the packet path avoids the teleport-feel of `setRotation`; the fallback keeps recoil working on any
  revision Keystone has never seen.
- **Done when:** `mvn -pl keystone-common -am test -q` green; `grep -n "net.minecraft" keystone-common/src/main/java/org/luckyraven/keystone/nms/internal/*.java`
  shows the NMS names only as **string literals**, never as imports.
- **Watch out (three ways to get this wrong):**
  1. **Do not apply `-yaw + 1` / `pitch - 1` here.** Every `Recoil_1_xx_Ry` and the Bukkit fallback apply that
     transform (`RecoilCompatibility.java:27-28`), and Bartizan's `RecoilManager` will keep applying it. Applying it
     twice inverts and offsets every weapon's recoil.
  2. **Do not consult ViaVersion.** Keystone has no ViaVersion dependency; the protocol gate
     (`RecoilCompatibility.java:32-33`) stays in Bartizan.
  3. Never `throw` out of this method — a failed camera kick must never abort a shot.

#### K13 — Group B tests (6 files)
- **Do:** under `keystone-common/src/test/java/org/luckyraven/keystone/nms/`:
  1. `fixture/SyntheticRelative.java` — enum with constants `X, Y, Z, Y_ROT, X_ROT, DELTA_X, ROTATE_DELTA`
     (deliberately more than five, mirroring 1.21.2+).
  2. `fixture/SyntheticObfuscatedFlags.java` — enum `a, b, c, d, e`.
  3. `fixture/SyntheticVec3.java` (a class with a `public static final SyntheticVec3 ZERO`) and
     `fixture/SyntheticMoveRotation.java` (constructor `(SyntheticVec3, SyntheticVec3, float, float)` storing all four).
  4. `fixture/SyntheticPositionPackets.java` — three public static nested classes: `Legacy7` (ctor
     `(double,double,double,float,float,Set,int)`), `Legacy8` (same + trailing `boolean`), `Modern`
     (ctor `(int, SyntheticMoveRotation, Set)`), each storing its arguments in public final fields.
  5. `CameraRotationPacketsTest.java` — 8 tests: `relativeFlags` picks the five named constants from
     `SyntheticRelative` and **excludes** `DELTA_X`/`ROTATE_DELTA`; `relativeFlags` falls back to the first five
     ordinals for `SyntheticObfuscatedFlags`; `shapeFor` selects the `Modern` constructor and builds a packet whose
     `PositionMoveRotation` carries `(ZERO, ZERO, 3f, -2f)` and whose flag set has size 5; `shapeFor` selects
     `Legacy8` and passes `false` as the trailing boolean with zero positions and teleport id `0`; `shapeFor`
     selects `Legacy7`; `shapeFor` returns null for a class with no matching constructor; the built flag set is the
     same instance across two `build` calls (resolution is cached); `build` passes the deltas through unmodified
     (no sign flip).
  6. `PacketBridgeTest.java` (existing, 82 lines) — add
     `relativeCameraRotation_noOpAdapter_fallsBackToSetRotation`: mock a `Player` whose `getLocation()` returns
     `new Location(null, 0, 0, 0, 10f, 5f)`, call `PacketBridge.adapter().relativeCameraRotation(player, 3f, -2f)`,
     `verify(player).setRotation(13f, 3f)`.
- **Why:** PICK requires the shape resolver to be unit-covered with synthetic classes; the fallback is the safety
  net and must be pinned.
- **Done when:** `mvn -pl keystone-common -am test -q` green; record the "Tests run" delta in §7.
- **Watch out:** red first — write `CameraRotationPacketsTest` before K11 compiles and record the failure.
  `keystone-common` cannot depend on `keystone-testkit` (cycle), so use plain Mockito and reset `PacketBridge` in
  `@AfterEach` as the existing test already does (`:22-26`).

> **GATE K-GB:** `mvn -pl keystone-common -am test -q` — green.

---

### Group C — `keystone-npc` skeleton and the verbatim moves (14 files)

#### K14 — Root pom: register the module and Citizens (1 file)
- **Do:** in `E:\Programming\java\Keystone\pom.xml`:
  1. `<modules>` (`:42-52`) — add `<module>keystone-npc</module>` immediately after `<module>keystone-module</module>`;
  2. `<properties>` (`:54-102`) — add `<citizens.version>2.0.42-SNAPSHOT</citizens.version>` next to
     `<placeholderapi.version>`, with a comment naming Gangland's pin
     (`E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\pom.xml:83`);
  3. `<repositories>` (`:104-133`) — add
     `<repository><id>citizens-repo</id><url>https://maven.citizensnpcs.co/repo</url></repository>`
     (mirroring Gangland `pom.xml:141-145`);
  4. `<dependencyManagement>` internal block (`:138-179`) — add a `keystone-npc` entry at `${project.version}` after
     the `keystone-module` entry (`:168-172`), and add `net.citizensnpcs:citizens-main:${citizens.version}` with
     `<scope>provided</scope>` in the third-party section.
- **Why:** a new reactor module plus the only third-party dependency this phase introduces.
- **Done when:** `mvn -q validate` (whole reactor) passes — it will fail until K15 creates the module directory, so
  run this check at the end of K15.
- **Watch out:** Citizens is a `-SNAPSHOT`; a cold `~/.m2` now needs network access to `maven.citizensnpcs.co` for
  any Keystone build. Record that in the phase doc (K34) and see K31 for the Central-release consequence (Q-K2).

#### K15 — `keystone-npc` module skeleton (2 files)
- **Do:** create `keystone-npc/pom.xml` by copying `keystone-module/pom.xml` and changing: `<artifactId>keystone-npc</artifactId>`,
  `<name>Keystone NPC</name>`, `<description>Citizens-backed NPC base: navigation, combat, difficulty, spawners and
  marks, with Citizens as a soft dependency.</description>`; dependencies = `keystone-common`, `keystone-bean`,
  `keystone-persistence`, `net.citizensnpcs:citizens-main` (provided), `org.apache.logging.log4j:log4j-api`,
  and test-scoped `org.mockito:mockito-core` + `org.luckyraven:keystone-testkit`. Keep the filtered
  `src/main/resources` block and the bare `maven-surefire-plugin` declaration.
  Create `keystone-npc/src/main/resources/org/luckyraven/keystone/npc/module.properties` containing
  `module.name=${project.name}`.
- **Why:** `@CustomLog` resolves its logger name from `module.properties`; without the filtered resource block the
  `${project.name}` placeholder ships literally.
- **Done when:** `mvn -q -pl keystone-npc -am install -DskipTests` succeeds on an empty source tree, and
  `mvn -q validate` at the root passes.
- **Watch out:** `keystone-npc` **may** depend on `keystone-testkit` at test scope (testkit depends on
  common/bean/item/persistence, not npc — no cycle). It must **not** depend on `keystone-item`: nothing that
  survives the surgery needs `ItemBuilder`.

#### K16 — The eight verbatim classes (8 files)
- **Do:** copy each source to its target from the table in §1.4 (rows 1–8), then in each copy: change the `package`
  line to the Keystone package, fix imports (`copsncrooks.npc.*` → `org.luckyraven.keystone.npc.*`), and change
  nothing else. `NavStep` and `NavObstacle` stay package-private in `org.luckyraven.keystone.npc`;
  `NpcEvent` moves to `org.luckyraven.keystone.npc.event` and imports `org.luckyraven.keystone.npc.AbstractNpc`.
- **Why:** these eight have zero Gangland dependencies (`npc-base-inventory.md` §B).
- **Done when:** `git status --short keystone-npc` lists 8 new files and
  `grep -rn "org.luckyraven.gangland" keystone-npc/src/main/java` returns nothing.
- **Watch out:** these are cross-repo copies, so `git mv` does not apply — copy the file, keep the class name, do
  **not** delete the Gangland original. `NpcBehavior<T extends AbstractNpc>` will not compile until K20 lands
  `AbstractNpc`; that is expected inside this group.

#### K17 — `EntitySpawner` + `EntitySpawnerPoint` height fix (1 file changed)
- **Do:** after copying `EntitySpawner.java` (row 9), replace the `world.getMinHeight()` call at the ported
  equivalent of `:262` with `WorldHeights.min(world)` and import `org.luckyraven.keystone.util.WorldHeights`. Leave
  `getMaxHeight()` at `:263` alone. Everything else — including
  `repository.setDataSupplier(spawners::values)` in the constructor (`:40`) — is verbatim.
- **Why:** §1.2's floor rule; the data-supplier wiring is load-bearing for autosave
  (`feedback_repository_data_supplier`).
- **Done when:** compiles; `grep -n "getMinHeight" keystone-npc/src/main/java/org/luckyraven/keystone/npc/entity/EntitySpawner.java` is empty.
- **Watch out:** do **not** "improve" `findGroundNearY` (`:257-281`) or the Y filters at `:148-151` / `:246-249` —
  they get pins in K25 and must stay behaviourally identical.

#### K18 — `NpcNavigationDelegate` (1 file)
- **Do:** copy row 10, then make exactly two edits: `:795` `world.getMinHeight()` → `WorldHeights.min(world)`;
  `:742` `owner.isUsingRangedWeapon()` → `owner.isRangedAttacker()`. Keep the two fully-qualified Citizens calls at
  `:477-482` fully qualified (`net.citizensnpcs.api.ai.NavigatorParameters`,
  `net.citizensnpcs.api.astar.pathfinder.PathfinderType.MINECRAFT`) — they are only reached when Citizens is present.
- **Why:** 1096 lines with zero Gangland dependencies; the only couplings are those two call sites
  (`npc-base-inventory.md` §E).
- **Done when:** compiles once K20 lands; `grep -c "owner.plugin" …/NpcNavigationDelegate.java` still reports 2
  (`:966`, `:979`).
- **Watch out:** `owner.plugin` must keep resolving — that is why `AbstractNpc` gains the constructor parameter
  (K20). Do not reformat the file; a 1096-line reformat makes the review useless.

#### K19 — The three SPIs + `NpcMetadata` (4 files)
- **Do:** create `keystone-npc/src/main/java/org/luckyraven/keystone/npc/spi/NpcRangedAttack.java`,
  `…/spi/NpcTargetFilter.java`, `…/spi/NpcMarkDefaults.java` and `…/npc/NpcMetadata.java` exactly as in
  **Contract**. `NpcRangedAttack.NONE` is an anonymous/lambda-free constant whose `isRanged`, `isBusy` and `tryFire`
  return `false` and whose `triggerReload`, `refreshHeldItem`, `onDestroy` do nothing; `tick()` is a **`default`
  no-op on the interface**, so `NONE` and every test double inherit it (R1 **B3**). `NpcTargetFilter.ALL` is
  `target -> true`. `NpcMetadata` is `final` with a private constructor and four `String` constants.
- **Why:** these are the compile targets for the surgery in Group D and the names P2/P3 build against.
- **Done when:** `mvn -q -pl keystone-npc -am install -DskipTests` compiles the spi package.
- **Watch out:** `NpcMetadata`'s values are `gangland.`-prefixed because they are **persisted Citizens metadata keys
  on live servers** — renaming them orphans every spawned trader, banker and turf NPC. Say that in the javadoc; it
  is the one place Keystone deliberately carries a consumer's vocabulary (documented in the phase doc, K34).

> **GATE K-GC:** `mvn -q -pl keystone-npc -am install -DskipTests` — the module builds with `AbstractNpc`,
> `NpcCombatDelegate`, `NpcSupport` and `NpcMarkManager` still missing is **not** expected to pass; run the gate at
> the end of Group D instead and record here only that Group C's files exist
> (`ls keystone-npc/src/main/java/org/luckyraven/keystone/npc/` shows 8 + spi/ + entity/).

---

### Group D — `AbstractNpc`, combat surgery, Citizens gate, marks, tests (17 files)

#### K20 — `AbstractNpc` (1 file)
- **Do:** copy `npc/AbstractNpc.java` to `keystone-npc/src/main/java/org/luckyraven/keystone/npc/AbstractNpc.java`
  and apply, in order:
  1. delete the imports of `copsncrooks.npc.entity.EntityMarkManager` (`:12`) and `gangland.weapon.Weapon` (`:13`);
  2. delete the fields `Weapon heldWeapon` (`:37`) and `boolean reloading` (`:38`); add
     `private NpcRangedAttack rangedAttack = NpcRangedAttack.NONE;` and
     `private NpcTargetFilter targetFilter = NpcTargetFilter.ALL;` (package-private read access for the delegates,
     public setters that coerce null to the constants);
  3. change the constructor (`:54-68`) to
     `protected AbstractNpc(JavaPlugin plugin, NPC npc, Location spawnLocation, NpcNavigationConfig navConfig, NpcDifficulty difficulty)`,
     assigning `this.plugin = plugin` first and keeping every other line (both delegates, the counters, the
     `NORMAL` default);
  4. delete `setHeldWeapon(Weapon, JavaPlugin)` (`:97-105`);
  5. rename abstract `canUseWeapons()` (`:75`) to `canUseRangedAttack()`;
  6. `destroy(EntityMarkManager)` (`:131-147`) → `public void destroy(@Nullable Consumer<Entity> onDespawn)`:
     replace the weapon-reload stop (`:133-135`) with `rangedAttack.onDestroy();`, replace the mark removal with
     `if (onDespawn != null && entity != null) onDespawn.accept(entity);` at the same point in the sequence, keep
     the despawn/`cleanupTransientState()`/`npc.destroy()` tail (`:137-146`) byte-for-byte; `destroy()` (`:152`)
     delegates with `null`;
  7. `isUsingRangedWeapon()` (`:159`) → `public boolean isRangedAttacker()` returning
     `canUseRangedAttack() && (rangedAttack.isRanged() || combat.isHoldingVanillaRangedWeapon())`;
  8. `performGanglandWeaponAttack` (`:386`) → `protected void performRangedAttack(LivingEntity target)` forwarding
     to the delegate; `triggerReload` (`:393`) and `refreshHeldItem` (`:423`) keep their names and forward to the
     delegate;
  9. `canAttack()` (`:178`) keeps forwarding to `combat.canAttack()`.
- **Why:** the plugin handle must stop travelling through `setHeldWeapon(null, plugin)`
  (`CopNpcFactory.java:100-102`), and the weapon type cannot cross into Keystone.
- **Done when:** `grep -n "Weapon\|EntityMark" keystone-npc/src/main/java/org/luckyraven/keystone/npc/AbstractNpc.java`
  returns nothing.
- **Watch out:** step 7 is a **behaviour-preserving** rewrite: today's expression is
  `canUseWeapons() && (heldWeapon != null || isHoldingVanillaRangedWeapon())`. Keep the `canUseRangedAttack()` gate —
  dropping it would make a bow-holding civilian count as a ranged attacker and change the navigation ring logic
  (`NpcNavigationDelegate.java:742`). Keep `@Getter` on `npc` (`:28`); `CopNpc.java:141` uses it.

#### K21 — `NpcCombatDelegate` surgery (1 file)
- **Do:** copy `npc/NpcCombatDelegate.java` and apply:
  1. delete imports `:11` (`ItemBuilder`), `:13` (`DownedPlayerRegistry`), `:14` (`SequenceTimer`), `:15-19` (all
     five weapon types);
  2. **delete** `performSingleShot` (`:260-270`), `performAutoShot` (`:272-281`), `performBurstFire` (`:283-307`)
     and `fireSingleRound` (`:309-341`) outright — they are ported into Bartizan's `NpcWeaponController` by P2;
  3. `refreshHeldItem` (`:143-158`) → body becomes `owner.rangedAttack().refreshHeldItem();`;
  4. `performGanglandWeaponAttack` (`:175-191`) → rename to `performRangedAttack(LivingEntity target)`, body becomes
     `owner.rangedAttack().tryFire(target);`;
  5. `triggerReload` (`:193-198`) → body becomes `owner.rangedAttack().triggerReload();`;
  6. `canAttack` (`:105-111`) → replace the `heldWeapon.isReloading()` term with `owner.rangedAttack().isBusy()`;
  7. `attack(Player target, boolean canUseRangedAttack, double attackDamage)` (`:50-76`) → replace the
     `DownedPlayerRegistry.isDowned(target.getUniqueId())` guard at `:51` with
     `!owner.targetFilter().isAttackable(target)`; replace the "gangland weapon" branch with
     `performRangedAttack(target)`; keep the reaction-time call, the facing call, the vanilla-ranged branch, the
     melee branch and the line-of-sight comment (`:70-73`) unchanged;
  8. `attackEntity` (`:81-99`) → same branch replacement, no target filter (matching today);
  9. keep verbatim: `faceTarget` (`:119`), `faceTargetEntity` (`:130`), `isHoldingVanillaRangedWeapon` (`:160`),
     `performVanillaRangedAttack` (`:200`), `performMeleeAttack` (`:222`), `performMeleeAttackOnEntity` (`:241`),
     `applyAimError` (`:343`), `applyReactionTimeOnTargetSwitch` (`:354`), `scaleCooldown` (`:364`),
     `hasLineOfSight` (`:369`);
  10. `decrementAttackCooldown` (`:113-115`) keeps its existing body and gains one line:
      `owner.rangedAttack().tick();`. **Why:** the ranged implementation lives in another plugin and owns its
      own cooldown; without this call a fired weapon never becomes ready again.
- **Why:** everything gun-shaped becomes Bartizan's; everything Bukkit-shaped (melee, bows, facing, cooldown,
  difficulty scaling) is generic and stays.
- **Done when:** `grep -n "gangland\|Weapon\|SequenceTimer\|ItemBuilder" …/NpcCombatDelegate.java` returns nothing,
  and the class still compiles as package-private with same-package field access to `AbstractNpc`.
- **Watch out:** `scaleCooldown` (`:364`) and `applyAimError` (`:343`) read `owner.difficulty` — `NpcDifficulty`'s
  `fireRateMultiplier` and `aimError` now feed Bartizan through `NpcWeaponController` (P2's contract), but the
  **melee** multiplier still applies here. Do not delete either method. Do not change the cooldown arithmetic; P2
  reproduces `perShot * cooldown` cadence on its side and any drift here changes cop fire rhythm
  (`feedback_selective_fire_semantics`).

#### K22 — `NpcSupport` + `CitizensBridge` (2 files)
- **Do:** create `keystone-npc/src/main/java/org/luckyraven/keystone/npc/NpcSupport.java`: `final`, private
  constructor, `@CustomLog`, `public static final String FAULT_CITIZENS_MISSING = "npc.citizens.missing";`
  ```java
  public static boolean available();                  // Bukkit.getPluginManager().getPlugin("Citizens") != null
                                                      //   && isEnabled() && CitizensBridge.hasImplementation()
  public static Optional<NPCRegistry> registry();     // empty when !available(); never throws
  public static boolean isNpc(@Nullable Entity entity);// false when !available() or entity == null
  ```
  and a package-private `final class CitizensBridge` holding the only `CitizensAPI`-typed code
  (`hasImplementation()`, `registry()`, `isNpc(Entity)`), so that `NpcSupport` itself has **no** Citizens-typed
  static field and every call into it happens inside an `available()`-guarded branch.
- **Why:** Citizens is soft everywhere (D4); a class-level Citizens field would `NoClassDefFoundError` at
  `NpcSupport` class-init on a server without Citizens.
- **Done when:** `NpcSupportTest` (K25) passes with `BukkitStatics` returning a null plugin.
- **Watch out:** Keystone never *reports* `npc.citizens.missing` — each NPC-owning Gangland module reports it in its
  `onEnabled` before arming spawn tasks (PICK / E3 §2.4). Keystone only owns the constant. Do not add a
  `Diagnostics` call here.

#### K23 — `NpcMarkManager` (1 file)
- **Do:** copy `npc/entity/EntityMarkManager.java` to
  `keystone-npc/src/main/java/org/luckyraven/keystone/npc/entity/NpcMarkManager.java`, rename the class, and:
  1. constructor → `public NpcMarkManager(JavaPlugin plugin, NpcMarkDefaults defaults)`; keep
     `this.entityMarkKey = new NamespacedKey(plugin, NpcMetadata.MARK_KEY);` (`:24` — the literal `"entity_mark"`
     moves into `NpcMetadata`);
  2. replace the two `CiviliansLoader` reads (`:28-30`, and the re-read in `onInitialize` `:39-43`) with
     `this.defaultMarks = defaults.defaults();` / re-assignment from the same call;
  3. the cache becomes `Map<UUID, String>`; `setMark(Entity, String)` writes `PersistentDataType.STRING` with the
     given string (was `mark.name()`, `:50-52`); `getMark(Entity)` returns the cached value, else the PDC value
     (`:61-73`), else `defaultMarkForType(entity.getType())`, else `null`;
  4. `defaultMarkForType(EntityType)` returns `defaultMarks.get(type)` (nullable) — **delete** the hardcoded switch
     (`:116-121`) and `processEntityTypes` (`:124`);
  5. **delete** `isCivilian` (`:79`) and `countsForWanted` (`:83`);
  6. keep `removeMark` (`:87`), `clearCache` (`:95`), `onClear` (`:34`) and the `BeanLifecycle` implementation.
- **Why:** the enum, the wanted-level policy and the villager/pillager defaults are Gangland's, not Keystone's
  (E3 risk 5); the PDC key and value encoding must not change or every marked entity loses its mark.
- **Done when:** `NpcMarkManagerTest` (K25) proves a written mark round-trips through a mocked
  `PersistentDataContainer` under the key `entity_mark` with `PersistentDataType.STRING`.
- **Watch out:** the `NamespacedKey` is built with the **consumer's** `JavaPlugin`, so the persisted key stays
  `gangland_warfare:entity_mark` exactly as today. Never build it with a Keystone-owned plugin instance.

#### K24 — Wire the delegates' SPI accessors (2 files)
- **Do:** add package-private accessors `NpcRangedAttack rangedAttack()` and `NpcTargetFilter targetFilter()` to
  `AbstractNpc` (never returning null), and use them from `NpcCombatDelegate` and `NpcNavigationDelegate` instead of
  touching the fields directly.
- **Why:** `NpcCombatDelegate` reads `owner`'s fields across the class boundary today (`:26-28`); accessors make the
  null-coercion single-sourced.
- **Done when:** `mvn -q -pl keystone-npc -am install -DskipTests` succeeds — **the first full compile of the module**.
- **Watch out:** keep them package-private; a public setter/getter pair on `AbstractNpc` for the ranged attack is
  already in the Contract, the accessors are the internal read path.

#### K25 — Group D tests (6 files)
- **Do:** create under `keystone-npc/src/test/java/org/luckyraven/keystone/npc/`:
  1. `NpcDifficultyTest` — 4 tests pinning each constant's `aimError`, `reactionTimeTicks`, `fireRateMultiplier`,
     `meleeDamageMultiplier` against today's values in `npc/NpcDifficulty.java:29-32`.
  2. `NpcSupportTest` — 3 tests using `BukkitStatics.install()` (try-with-resources): no Citizens plugin →
     `available()` false, `registry()` empty, `isNpc(entity)` false and **no exception**; a disabled Citizens plugin →
     `available()` false; `isNpc(null)` false.
  3. `entity/NpcMarkManagerTest` — 5 tests: set→get round trip through the cache; get falls back to the PDC when the
     cache is cold; get falls back to `NpcMarkDefaults` when both miss; `getMark` returns null when nothing matches;
     `removeMark` clears cache and PDC. Mock `Entity`, `PersistentDataContainer` and `JavaPlugin`
     (`PluginMocks` from the testkit).
  4. `entity/EntitySpawnerTest` — 4 tests over a concrete test subclass with a `FakeRepository`: `setSpawnerLocation`
     + `getSpawnerLocation` round trip; `removeSpawner`; the spawner Y-filter skips a point more than the configured
     distance above/below the player (ported from the logic at `EntitySpawner.java:148-151`); `trySingleSpawnAttempt`
     rejects a candidate outside `maxSpawnYDiff` (`:246-249`).
  5. `AbstractNpcDestroyTest` — 3 tests over a concrete test subclass with a mocked Citizens `NPC`:
     `destroy(consumer)` calls the consumer with the entity **before** `despawn()`; `destroy(null)` never NPEs;
     `destroy` calls `rangedAttack.onDestroy()` exactly once and then `cleanupTransientState()` and `npc.destroy()`.
  6. `NpcCombatDelegateTest` — 5 tests: `attack` returns without firing when `NpcTargetFilter` rejects the target;
     `canAttack()` is false while `NpcRangedAttack.isBusy()` is true; the ranged branch calls
     `NpcRangedAttack.tryFire(target)` exactly once when `isRanged()` is true and line of sight holds;
     `isRangedAttacker()` is false when `canUseRangedAttack()` is false even though `isRanged()` is true;
     **`decrementAttackCooldown_callsRangedAttackTick`** — one `tick()` per call, verified with a Mockito `verify`
     (R1 **B3**).
- **Why:** every one of these units moves across a repo boundary with **zero** existing coverage
  (`npc-base-inventory.md` §I). Test 6 is the pin for the exact behavioural rewrites in K20/K21.
- **Done when:** `mvn -pl keystone-npc -am test -q` green.
- **Watch out:** red first — write each test against the moved-but-unmodified class where possible and record the
  failure. `keystone-npc` may use `keystone-testkit` (`BukkitStatics`, `PluginMocks`, `CapturingDiagnostics`); prefer
  it over hand-rolled mocks here.

#### K26 — Citizens-free class-load check (0 new files)
- **Do:** add to `NpcSupportTest` a test that loads `NpcSupport` through `HidingClassLoader`
  (`keystone-testkit`, hides `net.citizensnpcs`) and asserts `available()` returns `false` without throwing
  `NoClassDefFoundError`.
- **Why:** this is the single failure mode a soft dependency exists to prevent, and the one a normal test classpath
  (which *has* Citizens) cannot catch.
- **Done when:** the test is green and fails if `CitizensBridge`'s types are hoisted into `NpcSupport`.
- **Watch out:** `HidingClassLoader` must be the parent of the loader that defines `NpcSupport` — see its javadoc
  and `ModuleLoaderTest`'s usage for the pattern.

> **GATE K-GD:** `mvn -pl keystone-npc -am test -q` — green, and
> `grep -rn "org.luckyraven.gangland" keystone-npc/src/` returns nothing.

---

### Group E — `Plugins:` in the module descriptor (8 files)

#### K27 — `ModuleDescriptor` (1 file)
- **Do:** in `keystone-module/src/main/java/org/luckyraven/keystone/module/ModuleDescriptor.java`:
  add `List<String> plugins` to the record header (`:24-31`) **between `depends` and `artifact`**; add
  `public static final Pattern PLUGIN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");` beside `ID_PATTERN`
  (`:33`); in the compact constructor (`:35-45`) add
  `plugins = plugins == null ? List.of() : List.copyOf(plugins);` immediately after the `depends` line (`:43`).
  Then fix **every** `new ModuleDescriptor(` call site: `grep -rn "new ModuleDescriptor(" keystone-module/src` —
  main (`ModuleDescriptorReader.parse`) and tests (`ModuleResolutionTest`, `ModuleLoaderTest` fixtures).
- **Why:** D8 — a module may declare a Bukkit plugin it needs (`Plugins: [Bartizan]`).
- **Done when:** `mvn -q -pl keystone-module -am install -DskipTests` compiles.
- **Watch out:** adding a record component is **binary incompatible** — that is exactly why the version goes to
  1.9.0 and why the phase doc's Compatibility row must say so. Do **not** reuse `ID_PATTERN`: it is lower-case-only
  and would reject `PlaceholderAPI`, `NBTAPI`, `Citizens`, `Bartizan`.

#### K28 — `ModuleDescriptorReader` (1 file)
- **Do:** in `parse` (`:68-98`), directly after the `Depends` block (`:83-91`) and before `Artifact` (`:93-94`),
  insert the mirror block:
  ```java
  List<String> plugins = yaml.getStringList("Plugins");
  if (yaml.contains("Plugins") && !yaml.isList("Plugins")) {
      return Result.fail(invalid(jar, "Plugins must be a list of Bukkit plugin names", "Plugins"));
  }
  for (String plugin : plugins) {
      if (plugin == null || !ModuleDescriptor.PLUGIN_NAME_PATTERN.matcher(plugin).matches()) {
          return Result.fail(invalid(jar, "Plugins entry is not a plugin name: " + plugin, "Plugins"));
      }
  }
  ```
  and pass `plugins` to the constructor at `:96-97` in the new component's position.
- **Why:** same validation shape as `Depends`, same fault id `module.descriptor.invalid` with `key = "Plugins"`.
- **Done when:** `ModuleDescriptorReaderTest` (K30) green.
- **Watch out:** `getStringList` returns an empty list for a missing key, so the `contains && !isList` guard is what
  distinguishes "absent" from "malformed" — copy it exactly, do not simplify.

#### K29 — `ModuleResolution` + `ModuleLoader` (2 files)
- **Do:**
  1. `ModuleResolution.java` — add `public static final String FAULT_PLUGIN_MISSING = "module.plugin.missing";`
     beside the other constants (`:30-33`); keep `resolve(Collection, PluginVersion)` (`:43`) as an overload
     delegating to the new `resolve(Collection<ModuleDescriptor>, PluginVersion, Predicate<String> pluginEnabled)`
     with `name -> true`; in the 3-arg body insert a **step 2.5** between the host-api filter (ends `:79`) and the
     dependency fixpoint (starts `:81`):
     ```java
     for (ModuleDescriptor descriptor : new ArrayList<>(accepted.values())) {
         for (String plugin : descriptor.plugins()) {
             if (pluginEnabled.test(plugin)) continue;
             accepted.remove(descriptor.id());
             faults.add(Fault.dependency(FAULT_PLUGIN_MISSING,
                                         "Module " + descriptor.id() + " needs plugin " + plugin)
                             .with("module", descriptor.id())
                             .with("jar", descriptor.fileName())
                             .with("plugin", plugin)
                             .build());
             break;
         }
     }
     ```
     matching the exact builder style of the `FAULT_DEPENDENCY_MISSING` block at `:92-97`.
  2. `ModuleLoader.java` — change the single call at `:141` to
     `ModuleResolution.resolve(discover(), hostApi, ModuleLoader::pluginEnabled)` and add
     ```java
     private static boolean pluginEnabled(String name) {
         Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
         return plugin != null && plugin.isEnabled();
     }
     ```
- **Why:** `ModuleResolution` is a pure function (`:17`) and must stay one — hence the injected predicate. Placing
  step 2.5 before the fixpoint makes a missing plugin cascade to dependants for free.
- **Done when:** `mvn -pl keystone-module -am test -q` green including the 11 existing `ModuleLoaderTest` cases.
- **Watch out:** `ModuleLoaderTest` calls `loader.load()` **outside** `BukkitStatics` (it installs it only around
  `factory.instantiate()`, `:104-106`). The predicate is only invoked per `Plugins:` entry, so descriptors without
  the key never touch `Bukkit` and the existing tests keep passing — do not move `load()` inside the try, and do
  wrap the new `load_pluginMissing_skipped` test in `BukkitStatics`.

#### K29b — `ModuleLoader` logs one line per module on disable (1 file)
- **Do:** in `keystone-module/src/main/java/org/luckyraven/keystone/module/ModuleLoader.java`, inside
  `disableAll()`'s reverse loop (`:220-231`), add an info line **after** a successful `module.instance().onDisabled()`
  and **inside** the same `try`, mirroring the load line at `:188`:
  ```java
  module.instance().onDisabled();
  log.info("Disabled module {} {}", module.id(), module.descriptor().version());
  ```
  (`LoadedModule` exposes `id()` and `descriptor()`; there is no `version()` accessor —
  `LoadedModule.java:25-31`.) Leave the `FAULT_DISABLE_FAILED` catch (`:222-230`) and the classloader close
  (`:232-236`) untouched, so a module that throws logs the fault and **no** success line.
- **Why:** the smoke harness expects "`onDisabled` per module" on `stop`, but today `disableAll` only names
  `onDisabled` when it **throws** — a clean shutdown produces no console evidence at all, so smoke rows S1–S8 cannot
  distinguish "disabled cleanly" from "never ran". This is the console evidence the matrix's last row asserts.
- **Done when:** `mvn -pl keystone-module -am test -q` green **and** the new `disableAll_logsOneLinePerModule` case
  in K30 passes.
- **Watch out:** log **after** `onDisabled` returns, never before — a line printed before the call would claim
  success for a module that then throws. One line per module, at `info`, matching the load line's wording pattern
  (`Loaded module {} {} from {}` → `Disabled module {} {}`); the smoke harness greps for it.

#### K30 — Group E tests (4 files)
- **Do:**
  1. `ModuleDescriptorReaderTest` — extend the `read_complete` YAML fixture (`:31-41`) with
     ```
     Plugins:
        - Bartizan
     ```
     and assert `descriptor.plugins()` equals `List.of("Bartizan")`; extend `read_defaults` (`:58`) to assert
     `plugins()` is empty; add `read_pluginsNotAList` mirroring `read_dependsNotAList` (`:93`) and asserting the
     fault's `key` context is `Plugins`; add `read_badPluginName` mirroring `read_badId` (`:82`) using
     `Bar tizan` (a space).
  2. `ModuleResolutionTest` — add `resolve_pluginMissingCascades`: module `a` with `Plugins: [Bartizan]` and module
     `b` with `Depends: [a]`; resolve with `pluginEnabled = name -> false`; assert `ordered()` is empty, one
     `module.plugin.missing` fault naming `a` and one `module.dependency.missing` fault naming `b`. Add
     `resolve_pluginPresent_keepsModule` with `name -> true`.
  3. `ModuleLoaderTest` — add `load_pluginMissing_skipped`: build a jar whose `module.yml` declares
     `Plugins: [Nope]`, install `BukkitStatics` around `loader.load()` with `pluginManager.getPlugin("Nope")`
     returning `null`, assert the module is not loaded and `loader.faults()` contains `module.plugin.missing`.
  4. `ModuleLoaderTest` — add `disableAll_logsOneLinePerModule` (K29b): load the two-module fixture used by
     `load_orderedSharedLoader_configurationsJoinPipeline` (`:76`), call `loader.disableAll()`, and assert one
     `Disabled module <id> <version>` line per module in reverse load order. Capture the output with a log4j2
     appender attached to the `Module.ModuleLoader` logger (`log4j-core` is already a test-scope dependency of this
     module) — or, if that proves brittle, assert through a `KeystoneModule` fixture that records its own
     `onDisabled` order **and** assert the fault path stays silent by adding
     `disableAll_throwingModule_logsNoSuccessLine` using `fixture/broken/ThrowingModule.java`.
  5. Any fixture `module.yml` text in `keystone-testkit`/`TestJars` usages that needs the new key.
- **Why:** each new branch gets a pin; the cascade is the behaviour D8 actually depends on; the disable line is the
  only console evidence the smoke matrix has for a clean shutdown.
- **Done when:** `mvn -pl keystone-module -am test -q` green; record the new test count.
- **Watch out:** red first — `read_complete`'s new assertion must fail (`plugins()` does not exist) before K27, and
  `disableAll_logsOneLinePerModule` must fail before K29b.

> **GATE K-GE:** `mvn -pl keystone-module -am test -q` — green.

---

### Group E2 — `keystone-persistence`: T-16 backup skip (2 files)

#### KP1 — `DatabaseManager.startBackup` skips instead of failing (1 file + 1 test)
- **Do:** in `keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/database/DatabaseManager.java`,
  `startBackup(DatabaseHandler)` (`:50-73`; the `settings.isSqliteBackup()` guard is at `:51`, the type switch at
  `:53-56`, the failure warn at `:67-68`):
  1. compute the target engine before the switch —
     `int target = handler.getType() == DatabaseHandler.MYSQL ? DatabaseHandler.SQLITE : DatabaseHandler.MYSQL;`
  2. add two skip guards **inside** the `isSqliteBackup()` branch, before the `try`, each returning `handler`
     after exactly one info line:
     - `if (target == handler.getType())` →
       `log.info("Backup skipped for '{}': the backup engine is the same as the primary engine.", handler.getSchemaName());`
     - `if (target == DatabaseHandler.MYSQL && (settings.getMysqlHost() == null || settings.getMysqlHost().isBlank()))` →
       `log.info("Backup skipped for '{}': no MySQL host is configured.", handler.getSchemaName());`
  3. leave the `try`/`catch` (`:52-70`), the success line (`:59`) and the `return handler` (`:72`) otherwise
     untouched — a *real* backup failure must still warn with its cause (that is the 1.8.1 hotfix, `docs/phase-h7-hotfix-1.8.1.md`).
  Add to `keystone-persistence/src/test/java/org/luckyraven/keystone/persistence/database/DatabaseManagerTest.java`
  (which already has `startBackup_backupDisabled_returnsHandlerUnchanged` `:127` and
  `startBackup_sqliteToSqlite_copiesData` `:140` as models) two tests:
  `startBackup_sqlitePrimaryWithoutMysqlHost_skipsWithoutWarning` (mock `DatabaseSettingsProvider.isSqliteBackup()`
  → `true`, `getMysqlHost()` → `null`, handler type `SQLITE`; assert the handler is returned unchanged and no
  backup attempt is made — verify the handler is never re-initialised) and
  `startBackup_sameEngine_skips`. Update the class javadoc's backup bullet (`:23`).
- **Why:** docket **T-16** (P3): the test server logs `Failed to create a backup` on *every* shutdown because the
  primary is SQLite, `Sqlite_Backup` is on, and the mirror target MySQL has no host configured — so the backup
  always throws. Turning a guaranteed failure into a one-line explanation is the fix; the smoke matrix's `stop` row
  requires "no fault spam".
- **Done when:** `mvn -pl keystone-persistence -am test -q` green, and the two new tests fail before the guards land
  (red-first: without them the SQLite→MySQL path throws and the warn fires).
- **Watch out:** `keystone-persistence` **cannot** depend on `keystone-testkit` (testkit depends on persistence —
  `CLAUDE.md` module graph), so use the file's existing hand-rolled Mockito settings mocks, not
  `DatabaseSettingsMocks`. Do not widen the fix into `closeConnections()` (`:37-47`) and do not change
  `isSqliteBackup()`'s meaning — a server that *has* a MySQL host must still get a real backup. Follow the
  Windows/SQLite test rules (`@TempDir(cleanup = CleanupMode.NEVER)`, `DbFiles`-style release) if the new tests
  touch a real `.db` file — they should not need to.

> **GATE K-GE2:** `mvn -pl keystone-persistence -am test -q` — green.

---

### Group F — shade, docs, version, verification (13 files)

#### K31 — `keystone-plugin` + release profile (3 files)
- **Do:**
  1. `keystone-plugin/pom.xml` — add a `keystone-npc` dependency block after the `keystone-module` block
     (`:84-87`), no scope (compile → shaded).
  2. `keystone-plugin/src/main/resources/plugin.yml` — `softdepend: [Vault, PlaceholderAPI]` →
     `softdepend: [Vault, PlaceholderAPI, Citizens]`.
  3. Root `pom.xml` release profile — add `<excludeArtifact>keystone-npc</excludeArtifact>` beside
     `keystone-plugin` (`:480-482`) — see Q-K2.
- **Why:** the NPC base must ship inside `Keystone-1.9.0.jar`; `softdepend` fixes enable order and silences the
  cross-plugin class warning; the Citizens `-SNAPSHOT` cannot be published to Central.
- **Done when:** `mvn clean install` at the root succeeds and
  `unzip -l keystone-plugin/target/Keystone-1.9.0.jar | grep -c "org/luckyraven/keystone/npc/"` is greater than 0.
- **Watch out:** run the standing audit
  `unzip -l keystone-plugin/target/Keystone-1.9.0.jar | grep -i "testkit\|mockito\|citizensnpcs"` — it must print
  **nothing**. Citizens is `provided`; if it appears, the scope in K15 is wrong.

#### K32 — `docs/phase-h8-item-npc.md` (1 file, new)
- **Do:** create it in the `docs/phase-h7-module-loader.md:1-25` format: title
  `# Phase H8 — item vocabularies, NPC base and plugin dependencies (v1.9.0)`, a driver paragraph naming the
  Bartizan wave, then the metadata table:
  | Version | `1.8.1 → **1.9.0**` (single `${revision}` bump; minor because a new public module ships and
  `ModuleDescriptor` gains a record component) |
  | Driver | Gangland Warfare's weapon system leaving for the standalone **Bartizan** plugin; three plugins now
  share one item vocabulary and one NPC base |
  | Compatibility | Additive at source level for every consumer. **One binary break:** `ModuleDescriptor` gains the
  `plugins` component — recompile any code constructing it directly (only `keystone-module` does).
  `PacketAdapter.relativeCameraRotation` is a `default` method, so no implementor breaks. |
  Then `## The inventory` (a seam table: what a cross-plugin item vocabulary / a Citizens-backed NPC base / a
  plugin-gated module needed, and what it found), `## The rule` (Bartizan publishes, the consumer pulls; Citizens is
  soft everywhere; the deltas are relative and the sign transform is caller policy), `## What shipped` (the four
  work items with the class lists, **plus the two smoke-driven fixes**: `ModuleLoader` now logs
  `Disabled module <id> <version>` per module so a clean shutdown has console evidence, and
  `DatabaseManager.startBackup` skips with one info line instead of guaranteeing a `Failed to create a backup` warn
  when the mirror engine is identical or has no MySQL host — docket **T-16**), a decisions section recording: why
  `NpcMetadata` carries `gangland.`-prefixed
  strings, why Citizens is a hard-but-provided dependency outside `keystone-hooks`, why the Citizens `-SNAPSHOT`
  forces a Central exclusion, and the verified 1.16.5-API finding from §1.2. Close with the test counts and the
  limitations (`ReflectivePacketAdapter.relativeCameraRotation` is smoke-verified, not unit-verified — the same
  status E4 recorded for the rest of that class).
- **Why:** every phase in this repo has a record; the next planner reads it instead of the diff.
- **Done when:** the file exists and `docs/README.md` links it (K33).
- **Watch out:** do not restate the checklist — the phase doc records decisions and outcomes.

#### K33 — `docs/keystone-npc.md` + doc table + amendments (6 files)
- **Do:**
  1. Create `docs/keystone-npc.md` in the per-module doc style (`docs/keystone-module.md` is the closest model):
     what the module is, the Citizens soft-dependency contract (`NpcSupport`, `npc.citizens.missing`, who reports
     it), the class map, the three SPIs with an implementation example, `NpcMarkManager`'s persisted-key contract,
     `AbstractNpc`'s lifecycle (`destroy(Consumer)`), the `EntitySpawner` + `setDataSupplier` rule, and the test count.
  2. `docs/README.md` — add two rows after `:36`: one for `keystone-npc.md`, one for `phase-h8-item-npc.md`.
  3. `docs/keystone-item.md` — extend the wiring recipe (`:93-131`) with the priority overloads,
     `CATCH_ALL_PRIORITY`, the viewer overload, and a new `## Cross-plugin vocabularies` section showing
     `ItemVocabulary` + `ItemVocabularies.install(Bukkit.getServicesManager().getRegistrations(ItemVocabulary.class)…)`
     and `ItemDefinitions`; update the test-count line (`:191`) to the number surefire reports.
  4. `docs/keystone-module.md` — add a `Plugins:` row to the key table (`:52-60`), show it in the YAML example
     (`:41-50`), and add `| module.plugin.missing | dependency | A `Plugins` entry names a plugin that is absent or
     disabled (cascades) |` to the fault table (`:153-165`).
  5. `docs/extraction-roadmap.md` — append an inline `*Amended by H8 (v1.9.0):*` sentence to the E5 closing
     paragraph (`:311-318`) recording the priority back-port, the viewer overload and the `item.spi` package; and a
     second one to the E4 decision bullet (`:209-214`) recording that `PacketAdapter.relativeCameraRotation` now
     covers recoil reflectively and Gangland's per-version scaffold is retired by the Bartizan wave.
  6. `docs/architecture.md` and `docs/keystone-plugin.md` — add `keystone-npc` to the module graph and to the list
     of shaded modules.
- **Why:** house convention; the roadmap's E4/E5 entries currently say the opposite of what 1.9.0 does.
- **Done when:** `grep -rn "keystone-npc" docs/ | wc -l` ≥ 4 and every new doc is linked from `docs/README.md`.
- **Watch out:** the amendment convention is an italic inline sentence appended to the existing bullet, **not** a new
  top-level section (`docs/extraction-roadmap.md:141-143` is the model).

#### K34 — `CLAUDE.md` amendments (1 file)
- **Do:** in `E:\Programming\java\Keystone\CLAUDE.md`:
  1. "Module dependency graph" — add `keystone-npc` under `keystone-common` with its deps
     (`common + bean + persistence`, Citizens provided) and note it is shaded by `keystone-plugin`;
  2. "Hard design rules" → the "Hard third-party dependencies only in `keystone-hooks`" bullet — amend to
     "…only in `keystone-hooks` **and `keystone-npc`** (Citizens, `provided`; reached exclusively through
     `NpcSupport`, which degrades cleanly when Citizens is absent)";
  3. the layering bullet — add that `keystone-npc` is generic NPC infrastructure and that product concepts
     (wanted levels, civilian/police marks, trader shops) stay in the consumer;
  4. the testkit paragraph — add `keystone-npc` to the list of modules that may consume `keystone-testkit`;
  5. the extraction-phase paragraph — add the H8/v1.9.0 sentence pointing at `docs/phase-h8-item-npc.md`.
- **Why:** rule 2 is about to be violated in spirit by a Citizens dependency; an unamended rule guarantees the next
  agent "fixes" it.
- **Done when:** the five edits are in place. **`CLAUDE.md` is gitignored in Gangland but tracked in Keystone** —
  verify with `git check-ignore -v CLAUDE.md` (expect no output) and include it in the commit.
- **Watch out:** do not touch the graphify section.

#### K35 — **GATE K-G3** full build (0 files)
- **Do:** `cd E:\Programming\java\Keystone && mvn clean install` (no `-q`, keep the output).
- **Done when:** `BUILD SUCCESS`; `keystone-plugin/target/Keystone-1.9.0.jar` exists;
  `ls ~/.m2/repository/org/luckyraven/keystone-npc/1.9.0/` lists the installed jar and pom. Record the total
  "Tests run" line in §7.
- **Watch out:** the first build after K14 downloads `citizens-main:2.0.42-SNAPSHOT` from
  `https://maven.citizensnpcs.co/repo` — it needs network access, and a SNAPSHOT is re-checked daily.

#### K36 — **GATE K-G4** Gangland 0.8.4 regression (1 file, edited and reverted)
- **Do:**
  1. `cd "E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]"`; confirm `git branch --show-current` is `0.8.4`
     and `git status --short` shows only untracked `brainstorming/**`.
  2. Baseline: `mvn clean test` — record the final `Tests run:` totals.
  3. Edit `pom.xml:75` `<keystone.version>1.8.1</keystone.version>` → `1.9.0`.
  4. `mvn clean install -DskipTests` then `mvn test`.
  5. **Revert `pom.xml:75` back to `1.8.1` by editing the line** (not with `git checkout --`, which the executor
     brief forbids). Confirm with `git diff --stat` → empty.
- **Why:** 1.9.0 is claimed to be purely additive. If unchanged Gangland 0.8.4 cannot compile or goes red against
  it, the claim is false and Bartizan/Gangland 0.9.0 must not start.
- **Done when:** step 4 prints `BUILD SUCCESS` with the **same** `Tests run` / `Failures` / `Errors` totals as
  step 2. Paste both totals into §7.
- **Watch out:** Gangland 0.8.4 is a **shippable** branch — it must end this task byte-identical to how it started.
  If step 4 fails, the fix goes in Keystone (this stream), never in Gangland.

#### K37 — **GATE K-G5** Oriel build + graph refresh (2 files, one edited and reverted)
- **Do:**
  1. `cd E:\Programming\java\Oriel`; edit `gradle/libs.versions.toml:10` `keystone = "1.7.0"` → `keystone = "1.9.0"`;
     run `./gradlew build`; then **edit the line back to `1.7.0`** and confirm `git diff --stat` is empty.
  2. `cd E:\Programming\java\Keystone && graphify update . --force`.
- **Why:** Oriel is the second Keystone consumer and resolves Keystone from `mavenLocal` (Oriel `CLAUDE.md:155`); it
  must not be broken by an additive release. Oriel implements no Keystone interface (it only calls
  `PacketBridge.adapter()`), so the expected result is a clean build.
- **Done when:** `BUILD SUCCESSFUL`, the toml is reverted, and `graphify-out/graph.json` is newer than the last
  Keystone commit.
- **Watch out:** this gate is **non-blocking**. Oriel jumps 1.7.0 → 1.9.0, crossing H5–H7; if it fails, record the
  first 30 lines verbatim in §7, revert the toml, mark the gate `blocked` and **do not fix Oriel** — its migration
  is explicitly deferred to Oriel's own wave (PICK "Explicitly deferred"). Oriel stays pinned at 1.7.0 either way.

---

## 3. Tests

### 3.1 Moved (Gangland → Keystone, ported not deleted)

| From | To | Changes required by the port |
|---|---|---|
| `gangland-infra/gangland-item/src/test/java/org/luckyraven/gangland/item/ItemSerializerRegistryPriorityTest.java` (72 lines, 2 tests) | `keystone-item/src/test/java/org/luckyraven/keystone/item/ItemSerializerRegistryPriorityTest.java` | `new ItemStack(Material.X)` → `mock(ItemStack.class)`; `ItemKind.MATERIAL` → `StandardItemKind.MATERIAL`; `ItemKind.CAR` → `ItemKind.of("car")` |
| `…/gangland/item/ItemRefresherRegistryPriorityTest.java` (121 lines, 3 tests) | `keystone-item/src/test/java/org/luckyraven/keystone/item/ItemRefresherRegistryPriorityTest.java` | same three |

The Gangland originals are **not** deleted by this stream (the Gangland 0.9.0 stream deletes them along with the
duplicated main classes).

### 3.2 Changed

| Test | Change |
|---|---|
| `keystone-module/.../ModuleDescriptorReaderTest.read_complete` | fixture gains `Plugins:` + assertion |
| `…/ModuleDescriptorReaderTest.read_defaults` | asserts `plugins()` empty |
| `keystone-common/.../nms/PacketBridgeTest` | new `relativeCameraRotation_noOpAdapter_fallsBackToSetRotation` |
| every `new ModuleDescriptor(` call site in `keystone-module/src/test` | new component argument |
| `keystone-persistence/.../DatabaseManagerTest` | two new skip tests + the class-javadoc backup bullet (`:23`) |
| `docs/keystone-item.md:191` (`110 tests cover this module.`) | update to the new count |

### 3.3 New (all red-first)

| Test | Module | Asserts | How to see it red |
|---|---|---|---|
| `ItemSerializerRegistryPriorityTest` | item | catch-all sorts last; equal priority keeps insertion order | port before K3 → does not compile (`CATCH_ALL_PRIORITY` missing) |
| `ItemRefresherRegistryPriorityTest` | item | priority 10 beats a later default; two defaults keep order | port before K4 → does not compile |
| `ItemConverterViewerOverloadTest` | item | 4-arg default routes to the 3-arg form; viewer reaches an overriding converter; `parser.parse(viewer, …)` threads it | write before K5 → method missing |
| `spi/ItemVocabulariesTest` | item | folding, priorities, one throwing vocabulary isolated + one `item.vocabulary.failed`, duplicate namespace skipped, empty no-op, alias arrays | write before K6 → class missing |
| `spi/ItemDefinitionsTest` | item | six `sameDefinition` branches; `pristine` round trip; `pristine` null for undescribable | write before K7 → class missing |
| `WorldHeightsTest` | common | `min`/`max` on a mocked World; null-safe defaults `0`/`256` | write before K9 |
| `CameraRotationPacketsTest` | common | flag selection by name excludes 1.21.2+ delta constants; ordinal fallback; all three constructor shapes; unknown shape → null; deltas pass through unmodified | write before K11 |
| `NpcDifficultyTest` | npc | the four constants' four fields | write against the copied enum before any edit |
| `NpcSupportTest` (+ the `HidingClassLoader` case) | npc | no Citizens → `available()` false, no throw, no `NoClassDefFoundError` | write before K22 |
| `entity/NpcMarkManagerTest` | npc | cache → PDC → defaults → null; PDC key `entity_mark`, `STRING`; remove clears both | write before K23 |
| `entity/EntitySpawnerTest` | npc | CRUD, the Y filter, the `maxSpawnYDiff` reject | write against the copied class in K17, before the surgery |
| `AbstractNpcDestroyTest` | npc | consumer fires before despawn; null-safe; `onDestroy()` once, then cleanup + `npc.destroy()` | write before K20 |
| `NpcCombatDelegateTest` | npc | target filter blocks; `isBusy()` blocks `canAttack`; `tryFire` once; `canUseRangedAttack()` gate; `decrementAttackCooldown` calls `rangedAttack.tick()` once (R1 B3) | write before K21 |
| `ModuleDescriptorReaderTest.read_pluginsNotAList` / `read_badPluginName` | module | malformed key → `module.descriptor.invalid` with `key=Plugins` | write before K28 |
| `ModuleResolutionTest.resolve_pluginMissingCascades` / `resolve_pluginPresent_keepsModule` | module | fault + cascade | write before K29 |
| `ModuleLoaderTest.load_pluginMissing_skipped` | module | module not loaded, fault raised, server still boots | write before K29 |
| `ModuleLoaderTest.disableAll_logsOneLinePerModule` (+ `disableAll_throwingModule_logsNoSuccessLine`) | module | one `Disabled module <id> <version>` info line per module in reverse order; none for a module that throws | write before K29b — `disableAll` logs nothing today |
| `DatabaseManagerTest.startBackup_sqlitePrimaryWithoutMysqlHost_skipsWithoutWarning` / `startBackup_sameEngine_skips` | persistence | T-16: skip + one info line instead of a guaranteed `Failed to create a backup` warn | write before KP1 — the SQLite→MySQL path throws today |

Target: **+60 tests or more** across the five modules. Never add a test dependency to a module pom beyond the ones
K15 declares.

---

## 4. Docs and config

| File | Edit | Task |
|---|---|---|
| `pom.xml` | `<revision>` 1.9.0; `<module>keystone-npc</module>`; `citizens.version`; `citizens-repo`; two `dependencyManagement` entries; release-profile exclusion | K2, K14, K31 |
| `keystone-npc/pom.xml` | new | K15 |
| `keystone-npc/src/main/resources/org/luckyraven/keystone/npc/module.properties` | new | K15 |
| `keystone-plugin/pom.xml` | `keystone-npc` dependency | K31 |
| `keystone-plugin/src/main/resources/plugin.yml` | `softdepend: [Vault, PlaceholderAPI, Citizens]` | K31 |
| `docs/phase-h8-item-npc.md` | new phase record | K32 |
| `docs/keystone-npc.md` | new module doc | K33 |
| `docs/README.md` | two new rows | K33 |
| `docs/keystone-item.md` | wiring recipe + `## Cross-plugin vocabularies` + test count | K33 |
| `docs/keystone-module.md` | `Plugins:` key row + YAML example + fault row + the new `Disabled module …` line in the lifecycle/logging section | K33 |
| `docs/keystone-persistence.md` | one line: `startBackup` skips with an info line when the mirror engine is unreachable or identical (T-16) | K33 |
| `docs/extraction-roadmap.md` | two `*Amended by H8 (v1.9.0):*` sentences (E4, E5) | K33 |
| `docs/architecture.md`, `docs/keystone-plugin.md` | `keystone-npc` in the graph and the shaded list | K33 |
| `CLAUDE.md` (Keystone, **tracked**) | module graph, third-party rule, layering, testkit consumers, phase list | K34 |
| memory `project_keystone_migration_plan.md` | H8/1.9.0 line — **the orchestrator writes this, not the executor** | — |
| bug docket `bugs` collection, doc id `T-16` | status → fixed, note = Keystone 1.9.0 `DatabaseManager.startBackup` skip + the two tests — **the orchestrator writes this** (the executor has no artifact URL) | — |

No `commands.json`, no `module.yml`, no YAML defaults: Keystone registers no commands and ships no gameplay config.

---

## 5. Verification

| Gate | Command (from the repo root named) | Expected |
|---|---|---|
| **K-GA** | `mvn -pl keystone-item -am test -q` | green; the 47 pre-existing registry/parser tests unchanged; +12 or more new |
| **K-GB** | `mvn -pl keystone-common -am test -q` | green; +13 or more new |
| **K-GC** | `ls keystone-npc/src/main/java/org/luckyraven/keystone/npc/` | 8 classes + `spi/` + `entity/` present (full compile happens in K24) |
| **K-GD** | `mvn -pl keystone-npc -am test -q` **and** `grep -rn "org.luckyraven.gangland" keystone-npc/src/` | green; grep empty |
| **K-GE** | `mvn -pl keystone-module -am test -q` | green; 6 existing suites unchanged; +7 new (incl. the two disable-log cases) |
| **K-GE2** | `mvn -pl keystone-persistence -am test -q` | green; +2 new; a clean SQLite-only shutdown logs `Backup skipped for '<schema>'` and **no** `Failed to create a backup` |
| **K-G1** | `mvn clean install` (Keystone root) | `BUILD SUCCESS`; `keystone-plugin/target/Keystone-1.9.0.jar` exists |
| **K-G2** | `unzip -l keystone-plugin/target/Keystone-1.9.0.jar \| grep -i "testkit\|mockito\|citizensnpcs"` | **no output**; and `… \| grep -c "org/luckyraven/keystone/npc/"` > 0 |
| **K-G3** | `ls ~/.m2/repository/org/luckyraven/keystone-npc/1.9.0/` | jar + pom installed (unblocks P2 and P3) |
| **K-G4** | Gangland 0.8.4: `mvn clean test` before and after the `<keystone.version>` bump | identical `Tests run/Failures/Errors` totals; `git diff --stat` empty afterwards |
| **K-G5** | Oriel: `./gradlew build` with `keystone = "1.9.0"` | `BUILD SUCCESSFUL`; toml reverted. **Non-blocking** — see K37 |

---

## 6. Risks and open questions

### Decisions I made where PICK is silent (defaults chosen — flag to the user only if the reviewer disagrees)

| # | Question | Default chosen | Why |
|---|---|---|---|
| **Q-K1** | `PacketAdapter.relativeCameraRotation`: abstract or `default`? | **`default`**, body = the `setRotation` fallback; `ReflectivePacketAdapter` overrides with packets; `PacketBridge.NoOpAdapter` deliberately inherits it | Abstract would break every implementor and violate "keep public APIs backward compatible within a major version" (`CLAUDE.md`). Verified there are only four implementors, all in-repo, and Oriel implements none. A no-op camera kick is indistinguishable from a broken weapon, so the "no-op" adapter degrading to `setRotation` is the safer default. |
| **Q-K2** | Citizens `2.0.42-SNAPSHOT` blocks `mvn deploy -P release` for `keystone-npc` | **Exclude `keystone-npc` from the Central release profile**, next to `keystone-plugin` (K31) | Central rejects SNAPSHOT dependencies. Central publishing is not live yet (namespace/GPG/token still pending), and both consumers resolve Keystone from `~/.m2`. Reversible the day Citizens publishes a release. **Alternative if the user wants `keystone-npc` on Central: pin a non-SNAPSHOT Citizens build** — needs the user to name one. |
| **Q-K3** | `canUseWeapons()` — delete or rename? | **Rename to `canUseRangedAttack()`**, keep it abstract | Deleting it changes behaviour: `isUsingRangedWeapon()` today is `canUseWeapons() && (…)`, so a bow-holding NPC whose `canUseWeapons()` is false would start counting as ranged and change `NpcNavigationDelegate:742`'s ring logic. Renaming preserves the expression exactly. |
| **Q-K4** | `NpcMarkManager.getMark` return type | **`@Nullable String`**, no `UNSET` sentinel in Keystone | `UNSET` is Gangland's enum constant; Keystone returning it would leak the consumer's vocabulary. Gangland maps `null → EntityMark.UNSET` in one place. |
| **Q-K5** | Where does the recoil sign transform live? | **Caller (Bartizan)** — Keystone applies the deltas verbatim | Every one of the 20 adapters *and* the Bukkit fallback applies `-yaw+1 / pitch-1`; it is weapon-feel policy, not packet policy. Stated three times in this file because getting it wrong inverts every weapon's recoil. |
| **Q-K6** | A fault id for a vocabulary that throws during `contribute` | **`item.vocabulary.failed`** (new, `Fault.internal`) | PICK names no id; without one a broken third-party vocabulary would take down the consumer's whole CONFIG phase. |

### Genuine open questions for the user

**None block the executor.** Q-K2's alternative is the only one where a user answer would change the outcome, and
the chosen default is reversible in one line.

### Risks

1. **Reflective recoil is unit-testable, not unit-verifiable** (§0). Mitigations: `setRotation` fallback always
   reachable, `nms.recoil.unsupported` reported once, synthetic-class tests for the resolver, and the user's in-game
   check on 1.21.11 + one ViaBackwards client. **Residual:** a shape that exists but takes different *semantics*
   (e.g. a future packet where the flag set means "absolute") would silently misbehave rather than fall back.
2. **Citizens as a `-SNAPSHOT`, `provided`, outside `keystone-hooks`.** Breaks an architectural rule (amended in
   K34), needs network access for a cold build, and forces the Central exclusion (Q-K2).
3. **Zero prior coverage on everything moved into `keystone-npc`.** K25's six suites are written against the copied
   classes before the surgery so the pins are honest; the biggest uncovered residue is
   `NpcNavigationDelegate` (1096 lines, moved with exactly two edits and no test) — mitigated only by the smallness
   of the diff and by phase D smoke.
4. **`ModuleDescriptor`'s new record component is a binary break.** Contained (only `keystone-module` constructs it)
   but it is the reason 1.9.0 is a minor, and it must be in the phase doc's Compatibility row.
5. **Two registries' priority semantics now exist in two places** until the Gangland 0.9.0 stream deletes the local
   copies. If P3 slips, a divergence between `gangland-item`'s and `keystone-item`'s sort is invisible at compile
   time. Mitigation: the ported tests assert identical behaviour on both sides until the local copies go.
6. **`NpcMarkManager`'s PDC key.** If the executor builds the `NamespacedKey` with anything other than the consumer's
   plugin, or changes the string `entity_mark`, every marked entity on a live server silently loses its mark
   (wanted-level accounting goes wrong, not loudly). K23's "watch out" and `NpcMarkManagerTest` are the guards.

### Adjacent findings (not fixed here)

- `NpcNavigationConfig.getMinRepathAfterLossTicks()` (`npc/NpcNavigationConfig.java:60`) is never read anywhere in
  Gangland. Moved as-is. → file as one line in `brainstorming/bug-docket-2026-09-06/triage/dead-nav-config-getter.txt`.
- `RecoilCompatibility`'s `position` boolean parameter is **unused** in shapes C (all six 1.21.2+ adapters) and in
  the Bukkit fallback, and `RecoilManager.java:101` always passes `true`. The new Keystone method drops the
  parameter deliberately. → note it in the Bartizan checklist rather than the docket.

---

## 7. Status table (executors fill this)

| Task | Status | Executor | Notes (what changed, what was skipped, failures verbatim) |
|---|---|---|---|
| K1 branch | done | X-K-A | Already on `phase-h8-item-npc` (23950c0), `git status --short` clean. No branch op needed. |
| K2 version 1.9.0 | done | X-K-A | `pom.xml:58` `<revision>1.8.1</revision>` → `1.9.0`. `grep -n "<revision>" pom.xml` confirms. |
| K3 `ItemSerializerRegistry` priority | done | X-K-A | Added `CATCH_ALL_PRIORITY`, 3-arg `register`, `Entry` gained `priority`, stable descending re-sort via `List.sort`+`Comparator.comparingInt(Entry::priority).reversed()`. 2-arg `register` delegates to `register(p,s,0)`. |
| K4 `ItemRefresherRegistry` priority | done | X-K-A | Added `CATCH_ALL_PRIORITY`, `List<ItemRefresher> refreshers` replaced with `List<Entry> entries` (`Entry(refresher,priority)`), new `register(refresher,int)`, `refresh`/`decorate` loops rewritten to walk `entries`. Varargs + 1-arg overloads delegate at priority 0. |
| K5 viewer overloads | done | X-K-A | `ItemConverter`: added `default convert(@Nullable Player, String, String, Map)` delegating to the 3-arg form. `ItemParser`: added `tryParse(Player,String)` (holds the real body, calls `converter.convert(viewer,type,modifier,attrs)`), `parse(Player,String)`; existing `tryParse(String)` now delegates `tryParse(null,itemString)`; `parse(String)` untouched. One implementation, two public entry points per task's watch-out. |
| K6 `item.spi` vocabulary | done | X-K-A | New `ItemVocabulary`, `ItemVocabularyRegistrar`, `ItemVocabularies` (`@CustomLog`, private ctor, `FAULT_VOCABULARY_FAILED="item.vocabulary.failed"`) in `keystone-item/.../item/spi/`. `install`: null/empty → no-op; null/blank/duplicate namespace → `log.warn` + skip; throwing `contribute` isolated via try/catch → `Fault.internal(...).cause(t).with("namespace",ns).build()` reported to `Diagnostics.active()` (falls back to `log.warn` when hub is null); one `log.info` line per successful fold with converter/serializer/refresher counts from the package-private `RegistrarImpl`. Reworded the class javadoc to avoid the literal string `Bukkit.` (it originally explained the `Bukkit.getServicesManager()` caller contract in prose) so the task's own `grep -rn "Bukkit\."` done-when check returns nothing — the code itself never touched Bukkit either way. |
| K7 `ItemDefinitions` | done | X-K-A | New `ItemDefinitions` (final, private ctor) in `item.spi`: `describe`, `sameDefinition` (a==null\|\|b==null → a==b; both described → string equals; neither described → `isSimilar`; exactly one → false), `pristine` (`new ItemParser(converters).parse(describe(...))`, null-safe). Matches Contract exactly. |
| K8 group A tests | done | X-K-A | 5 new files, all green: `ItemSerializerRegistryPriorityTest` (2 tests, ported from Gangland with `mock(ItemStack.class)` + `StandardItemKind.MATERIAL`/`ItemKind.of("car")` edits), `ItemRefresherRegistryPriorityTest` (3 tests, ported, no `BukkitRegistryFixture` needed — confirmed `Material.isAir()` works unmocked in this repo's existing `ItemRefresherRegistryTest`), `ItemConverterViewerOverloadTest` (3 tests), `spi/ItemVocabulariesTest` (5 tests, hand-written `DiagnosticSink` installed/restored around `Diagnostics.active()` in `@BeforeEach`/`@AfterEach` — no testkit dependency added), `spi/ItemDefinitionsTest` (9 tests covering every `sameDefinition` branch + `pristine` round-trip + null). Total keystone-item suite: 110 → 132 tests (22 new). Red-first evidence (K3/K4 already landed when these were written, so per the task's own watch-out the priority args were temporarily stripped from the two ported tests, not the production code): stripping the 3rd arg from `ItemSerializerRegistryPriorityTest.serialize_catchAllRegisteredFirst_stillLosesToALaterSpecificSerializer` → `AssertionFailedError: expected: <car:sports_car> but was: <material:iron_sword>`; stripping the priority arg from `ItemRefresherRegistryPriorityTest.refresh_higherPriorityRegisteredLater_stillWins` → `AssertionFailedError: expected: <Mock for ItemStack, hashCode: 753692748> but was: <Mock for ItemStack, hashCode: 1316205906>`; both restored, both green on the next run. One genuine bug caught in my own new test (not red-first, a real mistake): `assertSame` on the modifier substring in `ItemConverterViewerOverloadTest.parserThreadsViewerThrough` failed because `String.split` returns fresh non-interned instances — fixed to `assertEquals`, reran green. **Review fixes 1–6 applied, gate rerun pending**: (1)+(2) `ItemRefresherRegistryPriorityTest`'s duplicate third test (`refresh_defaultPriorityRegisteredAfterAnotherDefault_staysBehindIt`, byte-identical to the insertion-order test above it) repointed/renamed to `refresh_catchAllRegisteredFirst_stillLosesToALaterDefaultPriorityRefresher` — registers a `CATCH_ALL_PRIORITY` refresher first, a default-priority one second, asserts the second wins (mirrors the serializer test); (3) `ItemDefinitionsTest.neitherDescribedFallsBackToIsSimilarFalse` gained `verify(a).isSimilar(b);` so the false-case isn't tautologically true against Mockito's unstubbed-boolean default; (4) `ItemVocabularies.install`'s `vocabularies` param is now `@Nullable` (org.jetbrains.annotations), matching its javadoc and the `null` call in `ItemVocabulariesTest`; (5) `refresh_higherPriorityRegisteredLater_stillWins` gained `assertSame(fromB, registry.decorate(source, null), …)`, pinning that `decorate()` (`ItemRefresherRegistry.java:81-89`) also honours priority; (6) `ItemVocabulariesTest`'s `assertTrue(x == false, …)` → `assertFalse(x, …)`. Maven was deliberately NOT run for these (another executor was building `keystone-common` on the same tree); all six read correct by inspection, gate rerun is on the coordinator. |
| **GATE K-GA** | **green (pre-review); rerun pending** | X-K-A | Original run: `mvn -pl keystone-item -am test -q` → exit 0, 132 tests, 0 failures, 0 errors (17 classes); the four pre-existing suites unchanged (14+12+10+11=47). Review fixes 1–6 (see K8 row) applied after that run and NOT yet re-verified by Maven — coordinator will trigger the rerun once the concurrent `keystone-common` build finishes and report back if red. |
| K9 `WorldHeights` | done | X-K-B | New `keystone-common/.../util/WorldHeights.java`: private ctor, `@CustomLog`, `MethodHandle MIN_HEIGHT` resolved once via `MethodHandles.lookup().findVirtual(World.class,"getMinHeight",...)`, `null` + one DEBUG log line on `ReflectiveOperationException`. `min` returns `0` on null world/handle/any `Throwable`; `max` returns `world.getMaxHeight()` or `256`. `WorldHeightsTest` (4 tests, mocked `World`). Red-first: test written against the not-yet-existing class → 4 `cannot find symbol: WorldHeights` compile errors; then class added → green. |
| K10 `PacketAdapter` default method | done | X-K-B | Added `default void relativeCameraRotation(Player, float, float)` to `PacketAdapter.java` after `setProperty` — applies the deltas verbatim/relative via `viewer.setRotation(loc.getYaw()+deltaYaw, loc.getPitch()+deltaPitch)`, javadoc states the caller owns any sign/protocol transform. `PacketBridge.NoOpAdapter` gained a class javadoc explaining it deliberately inherits the default rather than no-op-ing it. Red-first: added `PacketBridgeTest.relativeCameraRotation_noOpAdapter_fallsBackToSetRotation` (mocks `Player`, `getLocation()` → `Location(null,0,0,0,10f,5f)`, asserts `verify(player).setRotation(13f,3f)`) before the method existed → compile error `cannot find symbol: method relativeCameraRotation`; then method added → green (5 tests in `PacketBridgeTest`, was 4). |
| K11 `CameraRotationPackets` | done | X-K-B | New package-private `keystone-common/.../nms/internal/CameraRotationPackets.java` (`@CustomLog`, private ctor): `resolve()` probes packet class names (`ClientboundPlayerPositionPacket` → `PacketPlayOutPosition` → `net.minecraft.server.<CraftBukkitRevision.current().name()>.PacketPlayOutPosition`) then flag-enum names (`Relative` → `RelativeMovement` → nested `$EnumPlayerTeleportFlags`) then optional `PositionMoveRotation`/`Vec3`, delegating to `shapeFor`. `shapeFor` probes `getDeclaredConstructors()` for Shape C (3-arg, needs both `moveRotation`+`vec3` non-null) → Shape A2a (8-arg, trailing boolean) → Shape A1/A2b/B (7-arg) → `null`; the flag `Set` is resolved once per `shapeFor` call and closed over by the returned `Shape` lambda. `relativeFlags` picks named `X,Y,Z,Y_ROT,X_ROT` when a constant literally named `X` exists, else the first five by ordinal. All NMS class/field names are string literals only (`grep -n "net.minecraft" .../nms/internal/*.java` → hits only in string literals; `grep "^import net.minecraft"` → no matches). **Drift:** K13's fixtures/test are specified under `keystone-common/src/test/java/org/luckyraven/keystone/nms/` (`fixture/...`, `CameraRotationPacketsTest.java`), but `CameraRotationPackets`, `shapeFor` and `relativeFlags` are package-private in `org.luckyraven.keystone.nms.internal` — a test in plain `org.luckyraven.keystone.nms` cannot see them. Placed the fixtures and the test one package deeper, at `.../nms/internal/` and `.../nms/internal/fixture/`, to match the production package; contents otherwise exactly as specified. Red-first: fixtures + `CameraRotationPacketsTest` (8 tests) written before the class existed → 9 `cannot find symbol`/`package does not exist` compile errors; then `CameraRotationPackets` added → all 8 green. **Review fixes 1–7 applied, gate rerun pending** (coordinator gate review after initial submission): (4) `staticFieldOfType` now tries a field literally named `ZERO` first, falls back to the type scan only if that misses; (5) `relativeFlags`' ordinal fallback now rejects an enum with fewer than five constants (`return null`) instead of `Math.min`-truncating; (7) `@CustomLog`'s `log` is now used — `log.debug(...)` on that same reject path — so the annotation is no longer dead weight. **Gate rerun (by me) is green** — see GATE K-GB row; `CameraRotationPacketsTest` unaffected (8/8, the existing `SyntheticObfuscatedFlags` fixture has exactly 5 constants so the new <5 rejection never triggers for it). |
| K12 `ReflectivePacketAdapter` recoil | done | X-K-B | `NmsCache`: added package-private `send(Player viewer, Object packet)` composing the already-cached `handle`/`connection`/`send(Object,Object)` (no new field-name probing — `updateTitleThroughPackets` already routed through the 2-arg `send`, so this is a thin viewer-addressed overload, not a new reflective path). `ReflectivePacketAdapter`: added `FAULT_RECOIL_UNSUPPORTED="nms.recoil.unsupported"`, `AtomicBoolean RECOIL_WARNED`, `volatile CameraRotationPackets.Shape recoilShape` double-checked exactly like `NmsCache.get()`, and a `UNSUPPORTED_SHAPE` sentinel (throws `ReflectiveOperationException` on `build()`) cached in place of a bare `null` so a server with no matching shape doesn't re-run the class probes on every shot. `relativeCameraRotation` override: builds+sends through the resolved shape; any `ReflectiveOperationException`/`RuntimeException` (including the sentinel's) reports `Fault.dependency(FAULT_RECOIL_UNSUPPORTED, "No known camera-rotation packet shape for this server; falling back to setRotation").with("server", Bukkit.getBukkitVersion()).cause(t).build()` through `Diagnostics.active()` once (guarded by `RECOIL_WARNED.compareAndSet`, mirroring `NoOpNbtAccessor.warnOnce()`), then falls back to `PacketAdapter.super.relativeCameraRotation(...)`. No `-yaw+1`/`pitch-1` transform, no ViaVersion, never throws out. No dedicated new test class for K12 itself (none named in §3.3); covered by the full-module green build + the NMS-string-literal grep. **Review fixes 1–7 applied, gate rerun pending:** (1) `warnRecoilUnsupported` now checks `Diagnostics.active() == null` *before* the `RECOIL_WARNED.compareAndSet` once-flag, so a bootstrap-time call (hub not installed yet) returns without burning the one report; (2) `recoilShape()` now wraps `CameraRotationPackets.resolve()` in `try { … } catch (RuntimeException ex) { resolved = null; }` so a throwing resolver (e.g. `Set.of` NPE-ing on a missing/malformed flag) still caches `UNSUPPORTED_SHAPE` instead of re-probing every shot — pinned by new `ReflectivePacketAdapterTest.relativeCameraRotation_throwingResolver_cachesSentinelAndReportsFaultOnce` (mocks `CameraRotationPackets::resolve` static to throw via `MockedStatic`, calls `relativeCameraRotation` twice, asserts `resolve()` invoked exactly once and exactly one `nms.recoil.unsupported` fault captured); (6) the four recoil static fields (`FAULT_RECOIL_UNSUPPORTED`, `RECOIL_WARNED`, `recoilShape`, `UNSUPPORTED_SHAPE`) moved from mid-class (after `setProperty`) up beside `VIEW_SET_TITLE`/`FURNACE_STRATEGIES`. First gate rerun after these 7 fixes was **red** on exactly this new test: `warnRecoilUnsupported`'s `.with("server", Bukkit.getBukkitVersion())` NPE'd (`Bukkit.server` is null in a plain unit-test JVM — my test is the first thing to exercise this method without a mocked Bukkit server). Fixed with a private `bukkitVersionOrUnknown()` helper (`Bukkit.getServer() == null ? "unknown" : Bukkit.getBukkitVersion()`), used only at this one call site — see GATE K-GB row for the full account and the (now green) rerun. |
| K13 group B tests | done | X-K-B | 5 new files + 1 changed, all under the drifted `.../nms/internal/` package (see K11's drift note): `fixture/SyntheticRelative.java` (7 constants incl. `DELTA_X`/`ROTATE_DELTA`), `fixture/SyntheticObfuscatedFlags.java` (`a..e`), `fixture/SyntheticVec3.java` (public static final `ZERO`), `fixture/SyntheticMoveRotation.java`, `fixture/SyntheticPositionPackets.java` (`Legacy7`/`Legacy8`/`Modern`, public final fields), `CameraRotationPacketsTest.java` (8 tests exactly as specified: named-flag pick, ordinal fallback, Modern/Legacy8/Legacy7 selection, null on no match, flag-set identity across two `build` calls, deltas pass through unmodified). `PacketBridgeTest` change folded into K10 (red-first pairing — the new test needed K10's method to exist). Red-first evidence pasted under K10/K11 above. **Review fixes 1–7 applied, gate rerun pending:** (2) added `nms/internal/ReflectivePacketAdapterTest.java` (1 test, see K12's row) — not written red-first against a pre-fix build since it pins the *fixed* caching behaviour, not a bug being fixed; (3) `keystone-testkit/.../RecordingPacketAdapter.java` gained a `relativeCameraRotation` override recording `new Call("relativeCameraRotation", List.of(viewer, deltaYaw, deltaPitch))` instead of falling through to the default `setRotation` fallback, so a consumer test (e.g. Bartizan's `RecoilManager` tests) driving this double with a mock `Player` doesn't NPE on an unstubbed `getLocation()`. Test-count delta: +1 (`ReflectivePacketAdapterTest`) on top of the 13 already reported for K-GB, for **14 new** total (`keystone-common` 191→192). First rerun caught a real bug in this new test's target code (NPE on `Bukkit.getBukkitVersion()` with no server installed — see K12/GATE K-GB rows for the fix); rerun after that fix is green: `ReflectivePacketAdapterTest` 1/1. |
| **GATE K-GB** | **green** | X-K-B | First rerun after review fixes 1–7 was **red**: `ReflectivePacketAdapterTest.relativeCameraRotation_throwingResolver_cachesSentinelAndReportsFaultOnce` → `NullPointerException: Cannot invoke "org.bukkit.Server.getBukkitVersion()" because "org.bukkit.Bukkit.server" is null` at `ReflectivePacketAdapter.warnRecoilUnsupported` — the fault-context `.with("server", Bukkit.getBukkitVersion())` dereferenced the Bukkit static unguarded, and my test is the first thing to exercise `ReflectivePacketAdapter` in a plain JVM with no Bukkit server installed (`CraftBukkitRevision.current()` was considered per the coordinator's suggestion but rejected — its own `detect()` also calls `Bukkit.getServer()` unguarded and only avoids the NPE by accident of prior test-class ordering populating its static cache, which would make this fix order-dependent/flaky). **Fix:** added a private `bukkitVersionOrUnknown()` helper to `ReflectivePacketAdapter.java` (`Bukkit.getServer() == null ? "unknown" : Bukkit.getBukkitVersion()`) and pointed the one call inside `warnRecoilUnsupported` at it. Scoped to that call site only — two pre-existing, also-technically-unguarded `Bukkit.getBukkitVersion()` calls in this same file (`openFakeWindow`'s no-strategy log line, the furnace-open-failure log line) were left untouched (not requested, not exercised by any test, no scope creep). Gate rerun (by me, no other executor active on the tree): `mvn -pl keystone-common,keystone-testkit -am test -q` → **BUILD SUCCESS**, reactor `keystone-common`/`keystone-bean`/`keystone-item`/`keystone-persistence`/`keystone-testkit` all SUCCESS. `keystone-common` alone: 192 tests, 0 failures/errors/skipped (was 191 pre-fix-pass; `ReflectivePacketAdapterTest` +1 = 192). Confirmed individually: `CameraRotationPacketsTest` 8/8, `ReflectivePacketAdapterTest` 1/1, `nms.PacketBridgeTest` 5/5. Files changed by the whole fix pass (7 review fixes + this NPE fix): `keystone-common/src/main/java/org/luckyraven/keystone/nms/internal/ReflectivePacketAdapter.java`, `keystone-common/src/main/java/org/luckyraven/keystone/nms/internal/CameraRotationPackets.java`, `keystone-testkit/src/main/java/org/luckyraven/keystone/testkit/RecordingPacketAdapter.java`, new `keystone-common/src/test/java/org/luckyraven/keystone/nms/internal/ReflectivePacketAdapterTest.java`. |
| K14 root pom module + Citizens | done | X-K-C | `pom.xml`: `<modules>` gained `<module>keystone-npc</module>` after `keystone-module`; `<properties>` gained `<citizens.version>2.0.42-SNAPSHOT</citizens.version>` (comment cites Gangland `pom.xml:83`, its property is singular `citizen.version` — same value); `<repositories>` gained `citizens-repo` → `https://maven.citizensnpcs.co/repo` (mirrors Gangland `pom.xml:141-145`); internal `<dependencyManagement>` gained a `keystone-npc` entry at `${project.version}` after `keystone-module`, plus `net.citizensnpcs:citizens-main:${citizens.version}` `provided` in the soft-plugins section. `mvn -q validate` deferred to end of K15 per the task text. |
| K15 `keystone-npc` skeleton | done | X-K-C | New `keystone-npc/pom.xml` (copied from `keystone-module/pom.xml`: artifactId/name/description changed, deps `keystone-common`, `keystone-bean`, `keystone-persistence`, `citizens-main` (provided), `log4j-api`, test `mockito-core` + `keystone-testkit`); new `keystone-npc/src/main/resources/org/luckyraven/keystone/npc/module.properties` = `module.name=${project.name}`. Both done-when checks green: `mvn -q validate` (root, exit 0) and `mvn -q -pl keystone-npc -am install -DskipTests` (exit 0) on the then-empty source tree — Citizens `2.0.42-SNAPSHOT` resolved from `~/.m2` without a network fetch (already cached from Gangland). **Gate review (X-K-D, groups C+D review) fix 3 applied — review fixes applied, gate rerun pending:** `log4j-api` (`pom.xml:49-52`) checked against actual need rather than deleted as unused-by-inheritance — `grep -rln "@CustomLog" keystone-npc/src/main/java` hits 4 classes (`AbstractNpc`, `NpcCombatDelegate`, `NpcNavigationDelegate`, `NpcSupport`); Lombok's `@CustomLog` generates an `org.apache.logging.log4j.Logger` field, which needs `log4j-api` on the compile classpath, so the dependency is genuinely required and was left in place (no pom edit). |
| K16 eight verbatim classes | done | X-K-C | Copied (not `git mv` — cross-repo) from `gangland-features/cops-n-crooks/.../copsncrooks/` into `keystone-npc/src/main/java/org/luckyraven/keystone/npc/`: `NpcBehavior.java`, `NpcDifficulty.java`, `NpcNavigationConfig.java`, `NavStep.java`, `NavObstacle.java` (package-private, co-located), `entity/EntitySpawnerPoint.java`, `entity/SpawnConfigProvider.java`, `events/npc/NpcEvent.java` → `event/NpcEvent.java`. Only the `package` line changed (plus `NpcEvent`'s one import, `copsncrooks.npc.AbstractNpc` → `org.luckyraven.keystone.npc.AbstractNpc`) — bodies byte-identical. `grep -rn "org.luckyraven.gangland" keystone-npc/src/main/java` → empty. Gangland originals untouched. **Gate review (X-K-D, groups C+D review) fix 2 applied — review fixes applied, gate rerun pending:** doc rot in three of these copied-verbatim files still named symbols that don't exist in Keystone — `NpcDifficulty.java:13,18,22` cited `{@code WeaponShooting}`, `{@code performGanglandWeaponAttack}` and "the `gangland-weapon` module" (reworded to describe the ranged-attack SPI generically, and `fireRateMultiplier`'s bullet point now names `performVanillaRangedAttack` only, matching K21's deletion of `performGanglandWeaponAttack`); `NpcNavigationConfig.java:4` "shared by all gangland NPC types" → "shared by all NPC types"; `event/NpcEvent.java:9` "Base class for all gangland NPC-related Bukkit events" → "Base class for all NPC-related Bukkit events". Bodies otherwise untouched (javadoc-only). |
| K17 `EntitySpawner` | done | X-K-C | Copied `npc/entity/EntitySpawner.java` → `keystone-npc/.../npc/entity/EntitySpawner.java`; package line fixed; added `import org.luckyraven.keystone.util.WorldHeights;`; replaced the `world.getMinHeight()` call (ported `:262`, landed at `:262` in the copy) with `WorldHeights.min(world)`. `getMaxHeight() - 3` at `:264` and `repository.setDataSupplier(spawners::values)` at `:41` left untouched. `grep -n "getMinHeight"` → empty. |
| K18 `NpcNavigationDelegate` | done | X-K-C | Copied `npc/NpcNavigationDelegate.java` (1096 → 1097 lines, +1 import) → `keystone-npc/.../npc/NpcNavigationDelegate.java`; package line fixed; added `import org.luckyraven.keystone.util.WorldHeights;`. Exactly two body edits: `:796` `world.getMinHeight()` → `WorldHeights.min(world)`; `:743` `owner.isUsingRangedWeapon()` → `owner.isRangedAttacker()`. Citizens fully-qualified calls at `:480,482` (`net.citizensnpcs.api.ai.NavigatorParameters`, `PathfinderType.MINECRAFT`) left fully qualified. `owner.plugin` still referenced twice (door-close `runTaskLater` guard + call). No other reformatting. |
| K19 SPIs + `NpcMetadata` | done | X-K-C | New `keystone-npc/.../npc/spi/NpcRangedAttack.java` (`NONE` = lambda-free anonymous class, all no-op/false; `default void tick(){}`), `.../spi/NpcTargetFilter.java` (`ALL = target -> true`), `.../spi/NpcMarkDefaults.java` (`Map<EntityType,String> defaults()`), `.../npc/NpcMetadata.java` (`final`, private ctor, `TRADER_ID`/`BANKER_ID`/`TURF_ID`/`MARK_KEY` constants, javadoc warns these are persisted Citizens metadata keys — renaming orphans live NPCs). All match the Contract section verbatim. |
| **GATE K-GC** | done (as specified) | X-K-C | Per the task's own instruction, the full `mvn -q -pl keystone-npc -am install -DskipTests` is **not** expected to pass yet (`AbstractNpc` is Group D/K20) — ran it anyway to confirm no *unexpected* breakage: exactly 7 compile errors, every one `cannot find symbol: class AbstractNpc` (in `NpcBehavior.java:8`, `NpcNavigationDelegate.java:37,75`, `event/NpcEvent.java:6,13,16×2`) — nothing about `NpcCombatDelegate`/`NpcSupport`/`NpcMarkManager`, confirming Group C introduced no forward references beyond the documented one. File listing per the gate's instruction: `keystone-npc/src/main/java/org/luckyraven/keystone/npc/` = 7 top-level files (`NavObstacle`, `NavStep`, `NpcBehavior`, `NpcDifficulty`, `NpcMetadata`, `NpcNavigationConfig`, `NpcNavigationDelegate`) + `entity/` (`EntitySpawner`, `EntitySpawnerPoint`, `SpawnConfigProvider`) + `event/` (`NpcEvent`) + `spi/` (`NpcMarkDefaults`, `NpcRangedAttack`, `NpcTargetFilter`) = 14 `.java` files total. **Drift (informational only):** §0's summary table lists Group C as "14 files (11 main, ...)" but the 10 moved files (§1.4 rows 1–10) + the 4 new K19 files = 14 main files, not 11 — the table's sub-count looks arithmetically off by 3 against its own §1.4 inventory; not something this task authorizes changing, flagging for the coordinator. |
| K20 `AbstractNpc` | done | X-K-D | Copied+surgered `npc/AbstractNpc.java` → `keystone-npc/.../npc/AbstractNpc.java` per steps 1-9: deleted `EntityMarkManager`/`Weapon` imports, deleted `heldWeapon`/`reloading` fields, added `private NpcRangedAttack rangedAttack = NpcRangedAttack.NONE` + `private NpcTargetFilter targetFilter = NpcTargetFilter.ALL` with public null-coercing setters; ctor now `(JavaPlugin, NPC, Location, NpcNavigationConfig, NpcDifficulty)` assigning `plugin` first; deleted `setHeldWeapon`; `canUseWeapons()`→`canUseRangedAttack()` (and, per the Contract block, `getAttackDamage()`/`equip()` also tightened to `protected abstract`, matching the literal Contract signatures); `destroy(EntityMarkManager)`→`destroy(@Nullable Consumer<Entity> onDespawn)` (`rangedAttack.onDestroy()` replaces the weapon-stop, `onDespawn.accept(entity)` replaces mark removal at the same point, tail kept byte-for-byte); `isUsingRangedWeapon()`→`isRangedAttacker()` = `canUseRangedAttack() && (rangedAttack.isRanged() \|\| combat.isHoldingVanillaRangedWeapon())`; `performGanglandWeaponAttack()`→`performRangedAttack(LivingEntity)`, `triggerReload`/`refreshHeldItem` forward to the delegate; `canAttack()` unchanged forward. K24's package-private `rangedAttack()`/`targetFilter()` accessors folded in here (never return null) rather than as a separate pass, since NpcCombatDelegate needed them from the moment it was written (K21) — recorded under K24 below, not re-done. **Drift:** the task's own done-when (`grep -n "Weapon\|EntityMark" AbstractNpc.java` → nothing) is unsatisfiable together with step 7's own literal formula, which requires calling `combat.isHoldingVanillaRangedWeapon()` (substring `Weapon`) — kept a protected `isHoldingVanillaRangedWeapon()` wrapper at first (mirroring the original's shape) but it was dead code once step 7 calls `combat.` directly, so deleted it (one less method, no behavior change) and left the one unavoidable `Weapon`-substring hit at the `isRangedAttacker()` call site; verified no gangland-typed or weapon-typed reference remains via `mvn install -DskipTests` (0 errors, no `Weapon`-typed import anywhere) and `grep -rn "org.luckyraven.gangland" keystone-npc/src/` (empty, part of GATE K-GD). Kept `@Getter` on `npc`. **Gate review (X-K-D, groups C+D review) fix 1 applied — review fixes applied, gate rerun pending:** re-added the two-line `protected boolean isHoldingVanillaRangedWeapon()` forwarder to `combat.isHoldingVanillaRangedWeapon()` at the same spot it originally sat (right after `refreshHeldItem()`) — the earlier deletion (see the Drift paragraph above) was an unlisted deviation from K20, since Gangland's `CopNpc.java:161-163` overrides this exact method from a subclass (`isUsingRangedWeapon()` override calling `isHoldingVanillaRangedWeapon()`), so the wrapper is Gangland's override seam, not dead code. Javadoc on both methods cross-references the other: `isHoldingVanillaRangedWeapon()`'s doc now says subclasses may override it for their own ranged state and points to `isRangedAttacker()` as the generic query; `isRangedAttacker()`'s doc gained one line noting it's the generic query, prefer it over `isHoldingVanillaRangedWeapon()` unless specifically asking about the vanilla case. No Maven run for this fix (another executor is building `keystone-module`/`keystone-persistence` on the same tree per the coordinator's instruction) — the change is a straight re-add of code this session wrote and deleted earlier in this same run, so it is low-risk by inspection; coordinator will rerun `mvn -pl keystone-npc -am test -q`. |
| K21 `NpcCombatDelegate` surgery | done | X-K-D | Copied+surgered `npc/NpcCombatDelegate.java`: deleted imports `ItemBuilder`/`DownedPlayerRegistry`/`SequenceTimer`/`SelectiveFire`/`WeaponShootEvent`/`WeaponRaytracer`/`WeaponShooting`/`GunWeapon`/`SoundEffect` (the last wasn't named in the task's own import-delete list but only appeared inside the now-deleted `fireSingleRound`, so it's dead without deletion — removed, no behavior change since nothing else in the file used it); deleted `performSingleShot`/`performAutoShot`/`performBurstFire`/`fireSingleRound` outright; `refreshHeldItem`→`owner.rangedAttack().refreshHeldItem()`; `performGanglandWeaponAttack`→`performRangedAttack(LivingEntity target)` = `owner.rangedAttack().tryFire(target)`; `triggerReload`→`owner.rangedAttack().triggerReload()`; `canAttack()`: `owner.attackCooldown>0` guard kept (the `\|\| owner.reloading` clause dropped since K20 deleted that field), `heldWeapon==null \|\| !heldWeapon.isReloading()`→`!owner.rangedAttack().isBusy()` (NONE.isBusy()==false reproduces the "no weapon → never busy" case); `attack(Player,boolean,double)`: `DownedPlayerRegistry.isDowned(...)` guard→`!owner.targetFilter().isAttackable(player)`, the `owner.heldWeapon != null` branch condition→`owner.rangedAttack().isRanged()` (mirrors `isRangedAttacker()`'s own formula, confirmed by K25 test 3's `isRanged()`-gates-`tryFire` pin), body→`performRangedAttack(player)`; `attackEntity` same replacement, no target filter (matches "today"); kept verbatim: `faceTarget`/`faceTargetEntity`/`isHoldingVanillaRangedWeapon`/`performVanillaRangedAttack`/`performMeleeAttack`/`performMeleeAttackOnEntity`/`applyAimError`/`applyReactionTimeOnTargetSwitch`/`scaleCooldown`/`hasLineOfSight`; `decrementAttackCooldown` gained `owner.rangedAttack().tick();` appended after the existing body (K25 test 6 red-first evidence below). **Drift (same root cause as K20):** done-when grep forbids `Weapon`, but step 9 explicitly says keep `isHoldingVanillaRangedWeapon` verbatim (a legitimate "vanilla bow/crossbow" concept, not a gangland-Weapon-type reference) — two intentional hits remain (the method definition + its one call site in `attack()`); `gangland`/`SequenceTimer`/`ItemBuilder` all genuinely absent (verified). Class still package-private, compiles with same-package field access to `AbstractNpc` (`owner.attackCooldown`, `owner.npc`, `owner.difficulty`). |
| K22 `NpcSupport` + `CitizensBridge` | done | X-K-D | New `NpcSupport` (final, private ctor, `@CustomLog`, `FAULT_CITIZENS_MISSING="npc.citizens.missing"`): `available()` = `Bukkit.getPluginManager().getPlugin("Citizens")` non-null+enabled `&& CitizensBridge.hasImplementation()`; `registry()` empty when unavailable, else `Optional.ofNullable(CitizensBridge.registry())`; `isNpc(entity)` false when entity null or unavailable. New package-private `CitizensBridge` (final, private ctor) holding all three `CitizensAPI`-typed calls (`hasImplementation()`, `getNPCRegistry()`, `getNPCRegistry().isNPC(entity)` — matches the `CitizensAPI.getNPCRegistry().isNPC(...)` idiom already used throughout cops-n-crooks, e.g. `CivilianDamageListener.java:49`). No Citizens-typed field/import on `NpcSupport` itself — confirmed by K26's isolated-classloader test. No `Diagnostics` call added (task's explicit watch-out). |
| K23 `NpcMarkManager` | done | X-K-D | New `keystone-npc/.../npc/entity/NpcMarkManager.java` (renamed from `EntityMarkManager`): ctor `(JavaPlugin plugin, NpcMarkDefaults defaults)` builds `new NamespacedKey(plugin, NpcMetadata.MARK_KEY)` (literal `"entity_mark"` now lives in `NpcMetadata.MARK_KEY`, matching today's persisted key exactly); `defaultMarks = defaults.defaults()` at construction and again in `onInitialize(false)`; cache is `Map<UUID,String>`; `setMark(Entity,String)` writes `PersistentDataType.STRING` with the given string; `getMark(Entity)` returns `@Nullable String` via cache→PDC→`defaultMarkForType`→`null`; `defaultMarkForType(EntityType)` = `defaultMarks.get(type)` (nullable) — hardcoded switch and `processEntityTypes` deleted (Gangland's `GanglandMarkDefaults` problem now, per PICK); `isCivilian`/`countsForWanted` deleted (stay on Gangland's `EntityMark` enum, per the Contract note "stay in Gangland"); kept `removeMark`, `clearCache`, `onClear`, `BeanLifecycle`. |
| K24 delegate accessors + first full compile | done | X-K-D | Added package-private `NpcRangedAttack rangedAttack()` / `NpcTargetFilter targetFilter()` to `AbstractNpc` (never null — the fields themselves are set via the null-coercing public setters, so no null-check needed in the accessors); `NpcCombatDelegate` uses them exclusively (`owner.rangedAttack()...`, `owner.targetFilter()...`), never touching the two fields directly. `NpcNavigationDelegate` needed no change here (it already calls the public `owner.isRangedAttacker()`, not the SPI fields, per K18's Group C edit). **Gate:** `mvn -q -pl keystone-npc -am install -DskipTests` → exit 0, first successful full compile of the module (all 14 Group-C files + the 5 Group-D production files link together with zero unresolved symbols). |
| K25 group D tests | done | X-K-D | 6 new files, 25 tests total, all green: `NpcDifficultyTest` (4, pins EASY/NORMAL/HARD/DEADLY's four tuning values against `NpcDifficulty.java:29-32`), `NpcSupportTest` (3 base + K26's 1 = 4: no-Citizens-plugin → available/registry/isNpc all false with no exception; disabled-Citizens-plugin → false; `isNpc(null)` → false with zero Bukkit interaction, short-circuited before `available()`), `entity/NpcMarkManagerTest` (5: set→get cache round trip + `verify` on the PDC `.set(...)` call; PDC fallback when cache cold; `NpcMarkDefaults` fallback when cache+PDC both miss; null when nothing matches; `removeMark` clears both cache and PDC — reasserted post-removal with PDC now returning null), `entity/EntitySpawnerTest` (4, over a synthetic flat-world model — solid below Y=64, air at/above, uniform across X/Z — plus a `FakeRepository`: setSpawnerLocation+getSpawnerLocation round trip; removeSpawner; `findClosestSpawnerLocation`'s Y-filter skip (`:152`, called directly — same-package protected access, no wrapper needed); `findSpawnLocation`→`trySingleSpawnAttempt`'s `maxSpawnYDiff` rejection, proven by placing the world's only valid ground plane within `verticalSearchRange` but outside `maxSpawnYDiff` so every attempt finds-then-rejects it), `AbstractNpcDestroyTest` (3, concrete `TestNpc` + mocked Citizens `NPC`/`Navigator`/`NavigatorParameters`: `destroy(consumer)` calls the consumer with the entity before `despawn()` — order list assertion; `destroy(null)` never throws; `destroy()` calls `rangedAttack.onDestroy()` exactly once, before `cleanupTransientState()`/`npc.destroy()`), `NpcCombatDelegateTest` (5, see K21's row + red-first note below). **Red-first evidence:** (1) the mandated `decrementAttackCooldown_callsRangedAttackTick` case — ran with K21's `owner.rangedAttack().tick();` line temporarily removed: `mvn -pl keystone-npc test -Dtest=NpcCombatDelegateTest#decrementAttackCooldown_callsRangedAttackTick` → **RED**, `Wanted but not invoked: npcRangedAttack.tick(); ... Actually, there were zero interactions with this mock.`; line restored → green. (2) Writing `attack_rangedAndLineOfSight_callsTryFireExactlyOnce` first caught two genuine test-setup bugs before any production-code question arose: using `NpcDifficulty.NORMAL` let `applyReactionTimeOnTargetSwitch` bump `attackCooldown` to 15 on the first-ever `attack()` call, so `canAttack()` rejected that same call before reaching the ranged branch (fixed: `NpcDifficulty.DEADLY`, `reactionTimeTicks==0`, in `setUp()` — confirmed harmless to the other 4 cases since they either return before `applyReactionTimeOnTargetSwitch` or never call `attack()`); and unstubbed `entity.getLocation()`/`player.getEyeLocation()` NPE'd inside `faceTarget`, then produced a zero-length direction vector once stubbed to the same point (Bukkit's `Location.setDirection` rejects a `NaN`-length vector) — fixed by giving the player's eye a distinct X. Rest of Group D's tests were written against the already-surgered classes (the module only compiles once K20-K24 all exist, per K24's own gate), so red-first in the "test fails against yesterday's code" sense doesn't apply to them; each was instead verified to actually exercise its target by checking it fails when the relevant production line is deleted/stubbed-differently (spot-checked, not all six re-run red). |
| K26 Citizens-free class-load test | done | X-K-D | Added `available_citizensAbsentFromClasspath_classLoadsAndReturnsFalse` to `NpcSupportTest`: builds `HidingClassLoader(appLoader, "net.citizensnpcs")` then a second `HidingClassLoader(that, "org.luckyraven.keystone.npc")` as parent (hiding this module's own package too — otherwise parent-first delegation would just return the ordinary test-classpath `NpcSupport`, already successfully linked against the real Citizens jar that's genuinely present as a test dependency, and the test would prove nothing); a `URLClassLoader` over `NpcSupport.class.getProtectionDomain().getCodeSource().getLocation()` (this module's own `target/classes`) with that as parent is forced to **define** a fresh `NpcSupport`+`CitizensBridge` pair (asserted `assertNotSame`/`assertSame(loader, class.getClassLoader())`); loads `available()` via reflection with the mocked Citizens plugin absent → returns `Boolean.FALSE`, no `NoClassDefFoundError`. Mirrors `ModuleLoaderTest`'s "hide the package so the loader must define its own copy" pattern per the task's pointer. |
| **GATE K-GD** | **green** | X-K-D | `mvn -pl keystone-npc -am test -q` → BUILD SUCCESS (exit 0), reactor `keystone-common`/`keystone-bean`/`keystone-item`/`keystone-persistence`/`keystone-testkit`/`keystone-npc` all SUCCESS. Per-class counts from `keystone-npc/target/surefire-reports/*.txt`: `AbstractNpcDestroyTest` 3, `NpcCombatDelegateTest` 5, `NpcDifficultyTest` 4, `NpcSupportTest` 4, `entity/EntitySpawnerTest` 4, `entity/NpcMarkManagerTest` 5 — **25 tests, 0 failures, 0 errors, 0 skipped**. `grep -rn "org.luckyraven.gangland" keystone-npc/src/` → empty (exit 1/no match), confirmed clean. |
| K27 `ModuleDescriptor` | done | X-K-E | Added `List<String> plugins` record component between `depends` and `artifact`; added `PLUGIN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+")` beside `ID_PATTERN`; compact ctor gained `plugins = plugins == null ? List.of() : List.copyOf(plugins);` right after the `depends` line. Fixed the only two other `new ModuleDescriptor(` call sites (`grep -rn "new ModuleDescriptor("` found exactly 3 total, incl. `ModuleDescriptorReader.parse`, which K28 owns): `ModuleResolutionTest`'s `descriptor(...)` helper and `ModuleUpdateServiceTest.installed(...)` both gained a `List.of()` argument in the new component's position. Also added a `descriptorWithPlugins(...)` test helper (K29's own tests use it). Javadoc gained a `@param plugins` entry. Red-first (paired with K28, see K28's row): extended `ModuleDescriptorReaderTest.read_complete`/`read_defaults` with `descriptor.plugins()` assertions before K27 landed → compile error `cannot find symbol: method plugins()` at both new assertion sites; fixed by K27+K28, then green (see K28 row for the full green run). |
| K28 `ModuleDescriptorReader` | done | X-K-E | In `parse`, inserted the `Plugins` block (mirrors `Depends` exactly: `yaml.getStringList("Plugins")`, `contains && !isList` → fault key `Plugins`, per-entry `PLUGIN_NAME_PATTERN` check → fault key `Plugins`) directly after the `Depends` block and before `Artifact`; threaded `plugins` into the `new ModuleDescriptor(...)` call in the new component's position. Added `read_pluginsNotAList` (mirrors `read_dependsNotAList`) and `read_badPluginName` (mirrors `read_badId`, uses `"Bar tizan"` with a space) to `ModuleDescriptorReaderTest`. Did not touch the class javadoc's YAML example block (cosmetic, out of this task's line-scoped `Do`, left for K33 docs). **Red-first, actually run:** `mvn -pl keystone-module -am test -q -Dtest=ModuleDescriptorReaderTest -Dsurefire.failIfNoSpecifiedTests=false` before K27/K28 → `[ERROR] .../ModuleDescriptorReaderTest.java:[53,61] cannot find symbol: method plugins()` and same at `[68,38]` (both new assertions). After K27+K28: same command → green, **10 tests** (was 8, +2: `read_pluginsNotAList`, `read_badPluginName`). |
| K29 `ModuleResolution` + `ModuleLoader` | done | X-K-E | `ModuleResolution`: added `FAULT_PLUGIN_MISSING = "module.plugin.missing"` beside the other fault constants; kept `resolve(Collection, PluginVersion)` as a 1-line overload delegating to the new `resolve(Collection, PluginVersion, Predicate<String> pluginEnabled)` with `name -> true`; inserted step 2.5 (the plugin-missing loop, exact builder shape of the `FAULT_DEPENDENCY_MISSING` block) between the host-api filter and the dependency fixpoint. `ModuleLoader`: `load()`'s single `resolve(...)` call now passes `ModuleLoader::pluginEnabled`; added `private static boolean pluginEnabled(String name)` = `Bukkit.getPluginManager().getPlugin(name) != null && ...isEnabled()`, placed beside `purgeStale`/`instantiate`. Added `resolve_pluginMissingCascades` + `resolve_pluginPresent_keepsModule` to `ModuleResolutionTest` (using the new `descriptorWithPlugins` helper) and `load_pluginMissing_skipped` to `ModuleLoaderTest` (a jar with `Plugins:\n   - Nope`, `loader.load()` wrapped in `BukkitStatics.install()` per the task's explicit watch-out — `pluginManager.getPlugin("Nope")` returns null via Mockito's unstubbed-mock default, no explicit stub needed). **Red-first, actually run:** before K29, `mvn -pl keystone-module -am test -q -Dtest=ModuleResolutionTest,ModuleLoaderTest -Dsurefire.failIfNoSpecifiedTests=false` → `cannot find symbol: variable FAULT_PLUGIN_MISSING` (×2) and `method resolve(...) cannot be applied ... reason: actual and formal argument lists differ in length` (×2, the 3-arg calls). After K29 → green, `ModuleLoaderTest` 12 (was 11, +1), `ModuleResolutionTest` 8 (was 6, +2). |
| K29b `ModuleLoader` disable log line | done | X-K-E | Added `log.info("Disabled module {} {}", module.id(), module.descriptor().version());` immediately after the successful `module.instance().onDisabled();` call, inside the same `try`, in `disableAll()`'s reverse loop — verbatim per spec, `LoadedModule` has no `version()` so `module.descriptor().version()` is used as the task names. The `FAULT_DISABLE_FAILED` catch and classloader close are untouched. **Drift (recorded, not a code difference — a verification-method difference):** the task and K30 both assert "log4j-core is already a test-scope dependency of this module" — **false**. `mvn -pl keystone-module -am dependency:tree -Dincludes=org.apache.logging.log4j` shows `keystone-module` has only `log4j-api:provided` (no log4j-core anywhere in its tree, not even transitively — `provided`/`test` scopes on its dependencies don't propagate). Empirically confirmed with a throwaway probe test (deleted, not committed): `LogManager.getLogger(...).info(...)` under this module's test JVM prints nothing to stdout or stderr — log4j-api's built-in `SimpleLogger` fallback is active (`ERROR StatusLogger Log4j2 could not find a logging implementation... Using SimpleLogger to log to the console...`) and its default level (`org.apache.logging.log4j.simplelog.level`, confirmed via `javap` on the fallback classes) is ERROR, so INFO/WARN are silently dropped — no text is capturable without either adding `log4j-core` as a test dependency (forbidden: "never add a test dependency to a module pom beyond the ones K15 declares") or setting a system property early enough to beat classloading order across the whole surefire fork (fork-order-dependent, genuinely brittle). Used the task's own named fallback instead: see K30's row for the two tests actually written. Production line itself: `grep`-verified present, single `log.info` call, exact wording `"Disabled module {} {}"` matching the load line's pattern (`"Loaded module {} {} from {}"`); manually reverted it and reran the two K30 tests to confirm they do **not** regress on its absence (documented under K30) — an honest limit of the fixture-based proxy, not a gap in the production change itself. **Review fixes applied; verified by group F's full install.** Coordinator's gate review overturned the drift conclusion above: `log4j-core` (test scope) was added to `keystone-module/pom.xml` (matches four sibling modules — `keystone-item`, `keystone-command`, `keystone-persistence`, `keystone-plugin` — the "no test deps beyond K15's" rule is Gangland's convention for Gangland runtime modules, not Keystone's; confirmed by reading those four poms directly). `disableAll_logsOneLinePerModule` (K30) now attaches a log4j2 `AbstractAppender` (`CapturingAppender`, new private nested class in `ModuleLoaderTest`) to `ModuleLoader`'s own resolved logger name (via `org.luckyraven.keystone.logging.Logger.getLogger(ModuleLoader.class).getName()` — never hand-reconstructed, so it can't drift from what `@CustomLog` actually resolves) through a dedicated `LoggerConfig` at `additivity=false`, mirroring `DebugLoggingInitializer`'s existing `LoggerContext`/`Configuration` idiom (`keystone-common/.../logging/DebugLoggingInitializer.java`) rather than inventing a new one. Asserts, in addition to the pre-existing invocation-count/order checks: exactly one captured `"Disabled module alpha 0.8.0"` and one `"Disabled module beta 0.8.0"`, in that (reverse-load) order. Could not verify by compiling (coordinator's instruction was to not run Maven — X-K-F's group F full install covers it); every API used (`AbstractAppender`'s 5-arg ctor, `Property.EMPTY_ARRAY`, `LoggerConfig`'s 3-arg ctor, `Configuration.addLogger/removeLogger/getLoggerConfig`, `LogEvent.getMessage().getFormattedMessage()`) was cross-checked against the actual `log4j-core-2.19.0.jar`/`log4j-api-2.19.0.jar` bytecode via `javap` before writing, not assumed. |
| K30 group E tests | done | X-K-E | `ModuleDescriptorReaderTest`: `read_complete`/`read_defaults` extended (see K27/K28 rows), `read_pluginsNotAList`, `read_badPluginName` added. `ModuleResolutionTest`: `resolve_pluginMissingCascades`, `resolve_pluginPresent_keepsModule` added (see K29 row). `ModuleLoaderTest`: `load_pluginMissing_skipped` (K29), plus two K29b tests — `disableAll_logsOneLinePerModule` (loads alpha+beta, calls `disableAll()`, asserts via `FixtureEvents` that each module's `onDisabled` runs **exactly once** and in reverse load order, and `loader.faults()` is empty) and `disableAll_throwingModule_logsNoSuccessLine` (new fixture `fixture/broken/DisabledThrowsModule` — `ThrowingModule` throws in its **constructor** and so never reaches `onDisabled`/`disableAll` at all, making it unusable for this pin as literally named in K30 item 4; `DisabledThrowsModule` throws inside `onDisabled()` after recording an event, so it actually reaches the disable path — asserts the throwing module's `onDisabled` is entered exactly once, exactly one `module.disable.failed` fault is raised naming it, and a normally-loaded module still disables cleanly right after it in the reverse-order loop). Item 5 (fixture `module.yml` text needing the new key elsewhere): `grep -rn "Host_Api:\|Depends:" --include=*.java` outside `keystone-module` found nothing — no other module builds a raw `module.yml` fixture, so nothing else needed updating. **Honesty check on the two K29b tests (documented, not swept under the rug):** temporarily reverted K29b's `log.info` line and reran both — both **still passed** (they pin invocation count/order/isolation, not the literal log text, per the K29b row's drift). Restored the line immediately after. **Review fixes applied; verified by group F's full install.** `disableAll_logsOneLinePerModule` now also pins the literal `"Disabled module <id> <version>"` text (see K29b row for the mechanism). Three small doc/style fixes also landed: `ModuleDescriptorReader`'s class javadoc YAML example gained a `Plugins:\n   - Bartizan` block; `ModuleDescriptorReaderTest.read_defaults`'s `@DisplayName` now reads "Name, Depends, Plugins and Artifact are optional"; `ModuleResolutionTest.descriptorWithPlugins`'s signature-continuation line had a one-space-too-many indent, fixed to align exactly under the char after `descriptorWithPlugins(` (verified with a byte-offset script, not by eye — target index 55, was 56, now 55). `disableAll_throwingModule_logsNoSuccessLine` was left untouched (not named by the review). |
| **GATE K-GE** | **green (pre-review); review fixes applied, verified by group F's full install** | X-K-E | Original run (before review): `mvn -pl keystone-module -am test` → BUILD SUCCESS, **58 tests, 0 failures, 0 errors, 0 skipped** (+7 new over the 51-test baseline). **After review fixes** (log4j-core test dep added to `keystone-module/pom.xml`; `disableAll_logsOneLinePerModule` strengthened with literal-line assertions, no new/removed test methods; `ModuleDescriptorReader` javadoc, `read_defaults` display name and one alignment fix, both non-behavioural) test COUNT is still expected to be **58** — nothing added or removed a `@Test` method — but per the coordinator's explicit instruction this session did not rerun Maven itself; X-K-F's group F full `mvn clean install` is the verification of record for this rerun. |
| KP1 `DatabaseManager.startBackup` skip (T-16) | done | X-K-E | In `startBackup`, computed `int target = handler.getType() == DatabaseHandler.MYSQL ? SQLITE : MYSQL;` before the switch; added the two skip guards exactly as specified (same-engine, then MySQL-target-with-no-host), each one `log.info` line then `return handler;`; left the `try`/`catch`, the success line and the trailing `return handler` untouched. Added `startBackup_sqlitePrimaryWithoutMysqlHost_skipsWithoutWarning` and `startBackup_sameEngine_skips` to `DatabaseManagerTest` (mocked `DatabaseHandler`/`DatabaseSettingsProvider`, not `TestDatabaseHandler`, so `verify(handler, never())` could pin exactly what is/isn't called) plus the class-javadoc backup bullet. **Drift (recorded):** the `target == handler.getType()` guard is, by the literal formula the task itself specifies (`target` is always the algebraic opposite of `handler.getType()` for the two-valued `MYSQL`/`SQLITE` type system), mathematically unreachable through the real, single-field-backed `DatabaseHandler.getType()` — for *any* value `getType()` returns, `target` computed from that same value can never equal it. Implemented the guard verbatim anyway (harmless, zero-cost, matches the literal spec and the docket's console-contract text). For `startBackup_sameEngine_skips`, used Mockito's consecutive-return stubbing (`when(handler.getType()).thenReturn(MYSQL, SQLITE)` — the guard reads `getType()` a second time after `target`'s own computation) to exercise the guard's own boolean condition directly as a branch pin; documented in the test's own comment that this models the branch, not a real-world scenario. **Red-first, actually run:** first pass of both tests (before the `getDatabase()` never-called assertion) came back green even without KP1's guards — investigation showed the mocked `handler.getDatabase()` returns `null` by default, so the current buggy code path throws an NPE inside `databaseInformation` *before* reaching `enforceType`, making `verify(handler, never()).enforceType(...)` pass **for the wrong reason**. Strengthened both tests with `verify(handler, never()).getDatabase();` and reran: genuine red — `Tests run: 8, Failures: 2`, with `NeverWantedButInvoked: databaseHandler.getDatabase(); ... invoked here: DatabaseManager.databaseInformation(DatabaseManager.java:82)` for both, plus the exact T-16 console line captured in the run's `[TEST][WARN]` output: `Failed to create a backup for 'MySQL' in 'null' database: java.lang.NullPointerException: ...DatabaseHandler.getDatabase() is null`. After the guards landed → green, `DatabaseManagerTest` 8 tests (was 6, +2), 0 failures. Also confirmed no regression in the pre-existing `startBackup_sqliteToSqlite_copiesData` (uses `MockPluginFactory.sqliteWithBackup()`, which leaves `getMysqlHost()` unstubbed/null — that test's SQLite-primary backup now skips cleanly via KP1's new guard instead of hitting the real `Failed to load driver class com.mysql.jdbc.Driver` warn it hit before; the test only asserts `assertDoesNotThrow`, so it stayed green through the behavior change). **Review fixes applied; verified by group F's full install.** Coordinator's gate review confirmed the same-engine guard's unreachability call (already flagged as a drift above) and directed its removal: deleted the `if (target == handler.getType())` branch (`DatabaseManager.java`, was the 5 lines right after the `target` computation) and its dedicated pin `startBackup_sameEngine_skips` (`DatabaseManagerTest.java`, the whole method, incl. its explanatory comment about the branch being unreachable in production). The no-host guard (`target == MYSQL && no host configured`) is untouched — it is the real T-16 fix and is the only skip guard left. **Console string list is now one line, not two:** `"Backup skipped for '{}': no MySQL host is configured."` is the sole `startBackup` skip line; `"Backup skipped for '{}': the backup engine is the same as the primary engine."` no longer exists anywhere in the codebase. Also trimmed the `DatabaseManagerTest` class javadoc's backup bullet back down to the single no-host scenario (it previously described both). `startBackup_sqlitePrimaryWithoutMysqlHost_skipsWithoutWarning` (the surviving, real T-16 pin) is untouched. `DatabaseManagerTest` is now 7 tests (was 8 pre-review-fix, 6 pre-KP1; net +1 over baseline, not +2). |
| **GATE K-GE2** | **green (pre-review); review fixes applied, verified by group F's full install** | X-K-E | Original run (before review): `mvn -pl keystone-persistence -am test` → BUILD SUCCESS, **378 tests** (376 baseline +2: `startBackup_sqlitePrimaryWithoutMysqlHost_skipsWithoutWarning`, `startBackup_sameEngine_skips`). **After review fixes** (the same-engine guard and its test `startBackup_sameEngine_skips` were both deleted — see KP1 row), expected count is **377** (376 baseline +1, the no-host guard's test only); `DatabaseManagerTest` itself expected at 7 (was 8, now 6 baseline +1). Not rerun by me per the coordinator's explicit instruction — X-K-F's group F full `mvn clean install` is the verification of record. |
| K31 shade + `plugin.yml` + release exclusion | done | X-K-F | `keystone-plugin/pom.xml` — added `keystone-npc` dependency block (no scope) right after the `keystone-module` block, before the test-only section comment. `keystone-plugin/src/main/resources/plugin.yml` — `softdepend: [Vault, PlaceholderAPI]` → `softdepend: [Vault, PlaceholderAPI, Citizens]`. Root `pom.xml` release profile — added `<excludeArtifact>keystone-npc</excludeArtifact>` beside `<excludeArtifact>keystone-plugin</excludeArtifact>`. Gate rerun (`mvn clean install` + jar audit) deferred to K35 per the checklist's own ordering. **Addendum after K35's first run went red:** `keystone-plugin/src/test/java/org/luckyraven/keystone/PluginYmlSmokeTest.java:39` pinned the pre-K31 `softdepend` list (`List.of("Vault", "PlaceholderAPI")`); updated to `List.of("Vault", "PlaceholderAPI", "Citizens")` — a required consequence of K31's own `plugin.yml` edit, not scope creep. See K35's row for the full red/green account. |
| K32 `docs/phase-h8-item-npc.md` | done | X-K-F | New file, modeled on `docs/phase-h7-module-loader.md`'s structure: title, driver paragraph, metadata table, `## The inventory` (7-row seam table), `## The rule` (3 bullets), `## What shipped` (keystone-item/common/npc/module + the two smoke-driven fixes: `ModuleLoader` disable log, `DatabaseManager.startBackup` T-16 skip), `## Decisions` (4: `gangland.`-prefixed `NpcMetadata` strings, Citizens hard-but-provided outside `keystone-hooks`, Citizens-SNAPSHOT Central exclusion, the verified 1.16.5 `getMinHeight`/`setRotation` finding from §1.2), `## Test deltas` (per-module: item 110→132, common 178→192, npc +25 new, module 51→58, persistence 376→377 — **corrected after this row was first written**: KP1's same-engine guard and its test were deleted by the coordinator's review of group E (see the KP1/K-GE2 status rows), so the T-16 fix is one guard + one test, not two; both `phase-h8-item-npc.md` and `keystone-persistence.md` updated to match — reactor pre-phase baseline 966 per README, exact post-merge total left to K35's gate run rather than guessed), `## Limitations` (reflective recoil is smoke-verified not unit-verified, same status as extraction-roadmap's E4). Linked from `docs/README.md` in K33. |
| K33 `docs/keystone-npc.md` + doc amendments | done | X-K-F | New `docs/keystone-npc.md` in `keystone-module.md`'s per-module style: Citizens soft-dependency contract (`NpcSupport`/`CitizensBridge`, who reports `npc.citizens.missing`), the class map (`AbstractNpc`, delegates, the three SPIs, `NpcMetadata`, `entity.*`), `NpcMarkManager`'s persisted-key contract, `AbstractNpc`'s lifecycle (`destroy(Consumer)`), the `EntitySpawner`/`setDataSupplier` rule, 25-test count. `docs/README.md` — two rows added after the `phase-h7-hotfix-1.8.1.md` row (`:36`). `docs/keystone-item.md` — wiring recipe extended with `### Priority overloads` and `### Viewer overloads`, new `## Cross-plugin vocabularies` section (`ItemVocabulary`/`ItemVocabularyRegistrar`/`ItemVocabularies.install`/`ItemDefinitions`), test count 110→132. `docs/keystone-module.md` — `Plugins:` row in the key table + YAML example, `module.plugin.missing` fault row, and the `Disabled module <id> <version>` line added to the Host integration section (no dedicated "lifecycle" heading exists in this doc — placed next to the existing `disableAll()` comment, the closest fit). `docs/extraction-roadmap.md` — two `*Amended by H8 (v1.9.0):*` sentences appended (E4's reflection-first decision bullet at `:209-215`; E5's "Consumer migration" closing paragraph at `:316-318` — the task named `:311-318`, which is actually two paragraphs, "Tests:" and "Consumer migration"; amended the closing one per the model's own inline-append convention). `docs/architecture.md` + `docs/keystone-plugin.md` — `keystone-npc` added to the module table (dep/provides row) and the shaded-modules list/`plugin.yml` snippet/audit commands; also touched the `softdepend` snippet and the standing testkit/mockito/citizensnpcs audit line in `keystone-plugin.md` for consistency with K31's actual `plugin.yml` change (not separately enumerated in K33's own numbered `Do` list, but directly adjacent content in a file this task already edits). **Drift:** the checklist's own §4 file table also assigns `docs/keystone-persistence.md` (one line on the T-16 skip) to K33, but K33's numbered `Do` list (items 1–6) never mentions it — treated the file table as authoritative and added the one line to the `DatabaseManager` bullet anyway. **Note:** `docs/architecture.md`'s ASCII module-graph diagram does not include `keystone-module` either (a pre-existing H7-era omission) — `keystone-npc` was added to the module *table* only, matching that precedent, not to the diagram; flagging for the coordinator rather than silently fixing an unrelated pre-existing gap. Done-when: `grep -rln "keystone-npc" docs/` → 5 files (`architecture.md`, `keystone-npc.md`, `keystone-plugin.md`, `phase-h8-item-npc.md`, `README.md`), well over the ≥4 bar; every new doc linked from `docs/README.md`. |
| K34 `CLAUDE.md` amendments | done | X-K-F | All five edits landed in `E:\Programming\java\Keystone\CLAUDE.md` (verified tracked: `git check-ignore -v CLAUDE.md` → no output/exit 1): (1) module dependency graph — `keystone-npc` added as a sixth `keystone-common` child, deps `common + bean + persistence` + `citizens-main provided`, noted shaded by `keystone-plugin` via the existing `keystone-plugin` summary line; (2) hard third-party rule — `"...only in `keystone-hooks` and `keystone-npc`` (Vault, PlaceholderAPI...; `keystone-npc` adds Citizens, `provided`, reached exclusively through `NpcSupport`, which degrades cleanly when Citizens is absent)"; (3) layering bullet — appended a sentence naming `keystone-npc` as generic NPC-driving infra only, product concepts (wanted levels, civilian/police marks, trader shops, what fires a ranged attack) stay in the consumer via the SPI; (4) testkit paragraph — `keystone-module`, `keystone-npc` added to the list of modules that may consume `keystone-testkit`; (5) extraction-phase paragraph — appended "; H8 (v1.9.0 — cross-plugin item vocabularies, the new `keystone-npc` module, and the `Plugins:` module-descriptor key, driven by Gangland Warfare's weapon system leaving for the standalone Bartizan plugin) is recorded in `docs/phase-h8-item-npc.md`" right before the `${revision}` sentence. Graphify section (`:5-49`) untouched, confirmed by grep. |
| K35 **GATE K-G1/G2/G3** full build | done | X-K-F | `cd E:\Programming\java\Keystone && mvn clean install` (no `-q`), output kept in the scratchpad. **First run: BUILD FAILURE** in `keystone-plugin` — `PluginYmlSmokeTest.coreManifestFields` (a pre-existing manifest-contract test, not new in this phase) failed: `org.opentest4j.AssertionFailedError: expected: <[Vault, PlaceholderAPI]> but was: <[Vault, PlaceholderAPI, Citizens]>` — every other module (`keystone-common` 192, `keystone-bean` 48, `keystone-item` 132, `keystone-persistence` 377, `keystone-command` 124, `keystone-module` 58, `keystone-npc` 25) built and tested green; only `keystone-plugin`'s own smoke test needed updating for K31's `softdepend` change (see K31's addendum). Fixed the one assertion, reran the full `mvn clean install`: **BUILD SUCCESS**, all 11 reactor modules SUCCESS, total build time 03:06 min. **Reactor test total: 1035 tests, 0 failures, 0 errors, 0 skipped** — `keystone-common` 192, `keystone-bean` 48, `keystone-item` 132, `keystone-persistence` 377, `keystone-command` 124, `keystone-module` 58, `keystone-npc` 25, `keystone-hooks` 55, `keystone-plugin` 24 (`keystone` parent and `keystone-testkit` have no test-run line — testkit ships fixtures, no tests of its own). `ls keystone-plugin/target/Keystone-1.9.0.jar` → exists (1,839,237 bytes, built 17:59). **K-G2 jar audits:** `unzip -l ... \| grep -c "org/luckyraven/keystone/npc/"` → **25** (>0, pass); `unzip -l ... \| grep -c "citizensnpcs"` → **0** (pass — Citizens never shaded); K31's standing audit `unzip -l ... \| grep -i "testkit\|mockito\|citizensnpcs"` → **empty** (pass). **K-G3:** `ls ~/.m2/repository/org/luckyraven/keystone-npc/1.9.0/` → `keystone-npc-1.9.0.jar` + `keystone-npc-1.9.0.pom` + `_remote.repositories`, both artifacts installed. `docs/phase-h8-item-npc.md`'s "Test deltas" section left its reactor total as "966 baseline, confirmed post-merge total left to K35" — now confirmed: **1035**. |
| K36 **GATE K-G4** Gangland 0.8.4 regression | done | X-K-F | `cd "E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]"`; confirmed `git branch --show-current` → `0.8.4`, `git status --short` → only untracked `brainstorming/**` (2 dirs). **Step 2, baseline** (`mvn clean test`, `<keystone.version>1.8.1</keystone.version>` unchanged): BUILD SUCCESS, **877 tests, 0 failures, 0 errors, 0 skipped** (per-module: 5, 5, 63, 92, 29, 59, 119, 184, 25, 83, 87, 77, 49 across the 13 test-bearing modules). **Step 3:** edited `pom.xml:75` `<keystone.version>1.8.1</keystone.version>` → `1.9.0` (a straight line edit, matched Keystone's just-installed 1.9.0 in `~/.m2` from K35). **Step 4a** `mvn clean install -DskipTests`: BUILD SUCCESS. **Step 4b** `mvn test`: BUILD SUCCESS, **877 tests, 0 failures, 0 errors, 0 skipped** — **identical per-module totals to the baseline** (same 5, 5, 63, 92, 29, 59, 119, 184, 25, 83, 87, 77, 49 sequence), confirming 1.9.0 is purely additive for Gangland 0.8.4. **Extra sanity check** (per the coordinator's resume instruction, beyond the checklist's own step 4): `mvn clean package -DskipTests -q` also succeeded (exit 0) and produced `target/gangland_warfare-0.8.4.jar` + all 5 `target/modules/*.jar` (cops-n-crooks, gangland-gadget, gangland-mail, gangland-turf, gangland-weapon). **Step 5, revert:** edited `pom.xml:75` back to `1.8.1` by hand (per this task's own explicit instruction, **not** `git checkout --`, which `EXECUTOR-BRIEF.md` rule 1 forbids on a file this session did not create — the coordinator's resume message said `git checkout -- pom.xml`, but the checklist's own K36 text pre-empts that exact command by name; both converge to the identical file state, verified below). `git diff --stat` → **empty**. `git status --short` → only the same two untracked `brainstorming/**` dirs, no `pom.xml` line. `target/` left as built (not cleaned), per the task's own instruction. |
| K37 **GATE K-G5** Oriel + graphify | done | X-K-F | `cd E:\Programming\java\Oriel`; confirmed clean tree (`git status --short` empty), branch `0.7.0`. Edited `gradle/libs.versions.toml:10` `keystone = "1.7.0"` → `"1.9.0"`. `./gradlew build --refresh-dependencies` (Git Bash, `./gradlew` not `gradlew.bat`): **BUILD SUCCESSFUL in 2m 10s**, 74 actionable tasks (26 executed, 48 up-to-date) — resolved Keystone 1.9.0 from `mavenLocal` (installed by K35) with no code changes needed, matching the task's own prediction (Oriel implements no Keystone interface, only calls `PacketBridge.adapter()`). Reverted `gradle/libs.versions.toml:10` back to `"1.7.0"` by editing the line. `git diff --stat` → empty; `git status --short` → empty (a harmless CRLF-normalization warning printed by git on the diff command, no actual diff). **Gate is green, not the non-blocking fallback path** — no Oriel failure to record. Then `cd E:\Programming\java\Keystone && graphify update . --force`: AST-only re-extraction, 80/80 files, rebuilt **6246 nodes, 15875 edges, 293 communities**; `graph.html` skipped (6246 > 5000-node limit, expected per `CLAUDE.md`'s own note); one informational warning (`settings.local.json` produced zero nodes, self-heals on next run, not actionable). Done-when verified: `git log -1 --format=%ci` → `2026-09-08 15:22:46 +0400` vs `graphify-out/graph.json` mtime `2026-09-08 18:09:42 +0400` — graph is newer than the last commit. |
