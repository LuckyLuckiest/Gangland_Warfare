# CONTRACTS - Cops N Crooks 0.16 "Where they come from"

Exact seams shared by two or more tasks. "Tn" = the PLAN.md task that creates the member; every other task only calls it.
Abbreviations: `API` = `gangland-api/src/main/java/org/luckyraven/gangland`, `CORE` = `gangland-core/src/main/java/org/luckyraven/gangland/core`,
`IMPL` = `gangland-impl/src/main/java/org/luckyraven/gangland`, `IMPLR` = `gangland-impl/src/main/resources`,
`CNC` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`, `CNCR` =
`gangland-features/cops-n-crooks/src/main/resources`, `CIV` = `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians`, `TURF` = `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf`,
`KS` = `keystone-npc/src/main/java/org/luckyraven/keystone/npc` (Keystone repo). Line numbers: Gangland master 629af929 and
Keystone b818483; files that 0.15.2 rewrites (EvasionClock, EvasionSettings, ChaseConfig, EvasionModuleConfig) have moved, so
find members by name. All bodies follow the house brace style even where this file abbreviates.

## C0. Placement (why each piece lives where it does)
| Piece | Module / package | Why |
|---|---|---|
| RegionShape, PlaceRegion, RegionProvider, PlaceNames | gangland-api `data.region` | Spec: api 2.3. Providers are modules (cops, turf) and impl (gang waypoints); modules only see the api. PlaceNames is a core holder bean (R4 pattern of `API/data/gang/GangMembership.java:17` / `BankTiers`, created in `IMPL/config/DataConfig.java` beside `bankTiers()` :132), so no module needs `Depends:` on another to read places. |
| Waypoint.WaypointType.HOSPITAL | gangland-api (`API/data/teleportation/Waypoint.java:82-90`) | The enum lives there; DB stores the name (`IMPL/database/repositories/waypoint/WaypointRepository.java:56`), no schema change. |
| WantedCause.CONTACT | gangland-core `core.wanted` (`CORE/wanted/WantedCause.java`) | Wanted is core; re-exported by the api, counted in the 2.3 bump. |
| Settings getters + settings.yml keys (Self_Defence, Takedown_Minimum, Contacts, Hospital) and Messages constants | gangland-api `file/configuration` + `IMPLR/settings.yml`, `IMPLR/message/message_{en,es}.yml` | Self-defence (`IMPL/listener/player/EntityDamageListener.java`), the [WANTED] sign (`IMPL/sign/aspect/WantedAspect.java`) and the death/respawn listeners are core code that runs without cops; their knobs are core knobs. |
| Contacts desk, `/glw contact`, phone page, evasion-state cache | gangland-impl | The sign and the phone share one cooldown and one unseen gate; impl cannot name a module class, and the unseen state already reaches the core through the api event `WantedEvasionStateEvent` (2.1). |
| Hospital lookup + bill timing + respawn shield + gang-waypoint RegionProvider | gangland-impl (`WaypointManager`, `CustomPlayerDeathListener`, `PlayerDeathListener`, `HospitalShield` + `HospitalShieldListener`) | Waypoints and death money are core; no module involvement. |
| Stations, admin regions, setup points, wand, dispatch queue, perimeter, hand-off, hideout + quiet multipliers, bribe stars | cops-n-crooks | Gameplay and the only writer (the wand). Admin regions are exposed to everyone through RegionProvider (PLAN Ruling R5). |
| Turf RegionProvider | gangland-turf | Turf owns turfs; cops reads them only through PlaceNames. |
| Out-of-sight spawn helper; NpcPost + hold-post/leash | Keystone 1.15.0 `keystone-npc` | Product-free; cops is the second consumer (owner decision K1). |

## C1. Region SPI (T3) - new files in `API/data/region/`
```java
package org.luckyraven.gangland.data.region;

/** A region's 3D shape. Block coordinates for cuboids (inclusive on both ends, like turf's CuboidRegion). @since api 2.3 */
public sealed interface RegionShape permits RegionShape.Cuboid, RegionShape.Sphere {
	boolean contains(double x, double y, double z);
	/** Horizontal area in square blocks; the "smaller wins" key of PlaceNames. */
	double footprint();

	record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements RegionShape {
		/** Normalises two corners (min/max per axis). */
		public static Cuboid of(int x1, int y1, int z1, int x2, int y2, int z2)
		/** Unbounded in Y (Integer.MIN_VALUE..Integer.MAX_VALUE): a turf column. */
		public static Cuboid column(int x1, int z1, int x2, int z2)
		// contains: floor each coordinate, then min <= v <= max on all three axes
		// footprint: (maxX - minX + 1.0) * (maxZ - minZ + 1.0)
	}

	record Sphere(double x, double y, double z, double radius) implements RegionShape {
		// contains: dx*dx + dy*dy + dz*dz <= radius*radius;   footprint: Math.PI * radius * radius
	}
}

/** One named place. id is globally unique: "<source>:<local id>", e.g. "copsncrooks:12", "turf:3", "waypoint:7". */
public record PlaceRegion(String id, String name, String world, RegionShape shape, int ownerGangId, Set<String> tags) {
	public static final String TAG_DISTRICT = "district";
	public static final String TAG_HIDEOUT = "hideout";
	public static final String TAG_RESTRICTED = "restricted";
	public static final String TAG_TURF = "turf";
	public static final String TAG_BREAKER = "breaker";
	public static final int NO_OWNER = -1;
	// compact ctor: name null -> ""; tags = Set.copyOf(tags) (null -> Set.of()); requireNonNull(id, world, shape)
	public boolean contains(@Nullable Location at)   // at != null, at.getWorld() != null, world name equals, shape.contains(x,y,z)
	public boolean hasTag(String tag)
}

/** SPI a module (or the core) implements to publish places. Main thread. @since api 2.3 */
public interface RegionProvider {
	/** Stable source id, also the id prefix: "copsncrooks", "turf", "waypoint". */
	String source();
	/** Every region of this provider that contains {@code at}; empty, never null. */
	List<PlaceRegion> regionsAt(Location at);
}

/** Core holder bean; inert (empty answers) until a provider registers. @since api 2.3 */
public final class PlaceNames {
	public PlaceNames()
	/** Adds a provider; a second provider with the same source() replaces the first. CopyOnWriteArrayList. */
	public void register(RegionProvider provider)
	public void unregister(String source)
	/** Every provider's regions at {@code at}, smallest footprint first (ties: registration order). A provider that throws a
	 *  RuntimeException is skipped for that call. Empty when at or its world is null. */
	public List<PlaceRegion> regionsAt(@Nullable Location at)
	/** First of regionsAt with a non-blank name. */
	public Optional<PlaceRegion> placeAt(@Nullable Location at)
	/** placeAt(at).map(PlaceRegion::name): the spec's PlaceNames.locate(Location). */
	public Optional<String> locate(@Nullable Location at)
	/** Smallest region at {@code at} carrying {@code tag}. */
	public Optional<PlaceRegion> withTag(@Nullable Location at, String tag)
}
```
Core bean (T3), `IMPL/config/DataConfig.java` next to `bankTiers()` (:132): `@Bean public PlaceNames placeNames()` returning `new PlaceNames()`.
`GanglandApi` keeps its four accessors (its class javadoc's "Exactly four accessors", final ruling R7 of the module-API wave; PLAN Ruling R3); only `VERSION = "2.3"` and a 2.3 javadoc bullet change (`API/GanglandApi.java:26-33`).

Providers (each registers itself from its bean method or `@PostConstruct`, never through `getAllInstances`):
| Provider | Task | source | Regions |
|---|---|---|---|
| `CNC/place/AdminRegionRegistry` | T7 | `copsncrooks` | `cop_region` rows: Cuboid.of(corners), owner -1, tags from the row |
| `TURF/place/TurfRegionProvider` | T14 | `turf` | `TurfManager.findAt(at)` (TURF/manager/TurfManager.java:87): Cuboid.column(minX,minZ,maxX,maxZ), name = displayName, owner = ownerGangId or -1, tags {turf} plus {hideout} when owned |
| `IMPL/data/teleportation/WaypointRegionProvider` | T8 | `waypoint` | GANG waypoints with gangId != -1 and a loaded world: Sphere(x,y,z, radius > 0 ? min(radius, 64.0) : 8.0), name = waypoint name, owner = gangId, tags {hideout} |

## C2. Waypoint, cause, settings, messages (T3)
- `Waypoint.WaypointType`: append `HOSPITAL(true)` after `GLOBAL` (`getName()` -> "hospital"). `WaypointTypeCommand` needs no change.
- `WantedCause`: append `CONTACT` after `UNKNOWN`, javadoc "crooked contact: the phone desk (/glw contact) and bribe-star pickups".
- `API/file/configuration/Settings.java` static `@Getter` fields, loaded with the existing helpers (`bool/str/money/...`) in the same
  load method as `bountyMinimum` (:632) and the Death block (:598-625):
```java
boolean   selfDefenceEnabled;             // Wanted.Self_Defence.Enable            true
int       selfDefenceWindowSeconds;       // Wanted.Self_Defence.Window_Seconds    8
double    selfDefenceMinDamage;           // Wanted.Self_Defence.Min_Damage        2.0
int       selfDefencePairCooldownSeconds; // Wanted.Self_Defence.Pair_Cooldown_Seconds 600
BigDecimal bountyTakedownMinimum;         // Bounty.Takedown_Minimum               "100"
boolean   contactsEnabled;                // Wanted.Contacts.Enable                true
BigDecimal contactsPricePerStar;          // Wanted.Contacts.Price_Per_Star        "1000"
int       contactsCooldownSeconds;        // Wanted.Contacts.Cooldown_Seconds      600
int       contactsMaxStars;               // Wanted.Contacts.Max_Stars             2
boolean   hospitalEnabled;                // User.Death.Hospital.Enable            true
int       hospitalShieldSeconds;          // User.Death.Hospital.Shield_Seconds    5   (0 or less = no shield; DECISIONS D19)
```
  Getter `Settings.getHospitalShieldSeconds()` (the `@Getter` spelling). settings.yml: `Shield_Seconds: 5` directly under
  `User.Death.Hospital.Enable`, commented "Seconds of damage immunity after a hospital respawn; attacking anything ends it. 0 = off".
- The Settings getters and Messages constants here are owner decision D14 = "additive api" (answered ALLOW 2026-10-07,
  `OWNER-RULINGS.md`): eleven Settings getters (ten + the D19 shield) and eight Messages constants (seven + the shield line).
- `Messages` constants (path, `Messages.Type`; `PREFIX` = `Messages.Type.PREFIX`, `OTHER` = `Messages.Type.OTHER`) + `message_en.yml`
  text (`message_es.yml` gets a Spanish line under the same path):
  `CONTACT_USED("Wanted_Level.Contact.Used", PREFIX)` "&aYour contact made &e%stars% &astar(s) disappear for &e%money_symbol%%amount%&a.";
  `CONTACT_SEEN("Wanted_Level.Contact.Seen", PREFIX)` "&cNot while a cop has eyes on you.";
  `CONTACT_COOLDOWN("Wanted_Level.Contact.Cooldown", PREFIX)` "&cYour contact is lying low. Try again in &e%time%&c.";
  `CONTACT_NOT_WANTED("Wanted_Level.Contact.Not_Wanted", PREFIX)` "&7You have no stars to wipe.";
  `CONTACT_NO_MONEY("Wanted_Level.Contact.No_Money", PREFIX)` "&cYou need &e%money_symbol%%amount%&c.";
  `CONTACT_DISABLED("Wanted_Level.Contact.Disabled", PREFIX)` "&7Nobody picks up.";
  `DEATH_WARD_BILL("Death.Ward_Bill", OTHER)` "&3Ward bill: &c&l-%money_symbol%%amount%";
  `DEATH_HOSPITAL_SHIELD("Death.Hospital_Shield", PREFIX)` "&7Hospital protection for &e%seconds%&7 s. Attacking anything ends it."
  (the caller replaces `%seconds%` with the granted whole seconds).
  Money token: `%money_symbol%%amount%`, the spelling of `Wanted_Level.Paid` and `Entity_Drop_Money` (the file header's `%price%` is
  the bulk-sign token, not this). Callers replace `%amount%` with the formatted amount and `%money_symbol%` with the Settings money
  symbol, as the existing `Entity_Drop_Money` caller does.

## C3. Keystone 1.15.0 - out-of-sight spawn helper (T1) - `KS/entity/EntitySpawner.java`
```java
/** True when no player who matters can see {@code spot}: neither {@code target} (at any distance) nor any other player in the
 *  world within getVisibilityCheckDistance(). Observers skip NPCs: a world player for which NpcSupport.isNpc(p) is true (a
 *  Citizens player-type NPC such as a cop or civilian) never counts. A player sees the spot when World.rayTraceBlocks(eye,
 *  direction to spot+1.6, distance, FluidCollisionMode.NEVER, true) returns null (no block in between). False when spot or its
 *  world is null. @since 1.15.0 */
public boolean isOutOfSight(Location spot, Player target)

/** A ring spot around {@code player} that passes {@code allowed} and isOutOfSight: config.getSpawnPhase1Attempts() tries on
 *  [config.getPhase1MinDistance(), config.getMaxSpawnDistance()], then the phase-2 shrinking rings (getMaxSpawnDistance() down to
 *  getMinSpawnDistance() by getSpawnRadiusShrinkStep(), getSpawnPhase2Attempts() each); any angle (no behind-the-player rule);
 *  every other check as findSpawnLocation (loaded chunk, ground, getMaxSpawnYDiff(), same indoor/outdoor through the overridable
 *  isOutdoor, so a subclass override such as CopSpawnManager's anyRoof applies). Null when none. @since 1.15.0 */
@Nullable protected Location findHiddenSpawnLocation(Player player, Predicate<Location> allowed)
```
Reuse `trySingleSpawnAttempt(player, min, max, requireBehind=false, playerOutdoor)` (:246); `findSpawnLocation` (:193) and
`findClosestSpawnerLocation` (:153) are unchanged. Civilians' `CivilianSpawnManager` is unaffected (no override, no call change).
`isOutOfSight` ignores facing ON PURPOSE (a block ray, no cone): it is not `isVisibleToOtherPlayers` (:119-133, a 60-degree facing
cone with no ray, unchanged). Its tests assert ray behaviour only and never copy the cone tests.
`isOutOfSight` ignores facing ON PURPOSE (a block ray, no cone): it is not `isVisibleToOtherPlayers` (:119-133, a 60-degree facing
cone with no ray, unchanged). Its tests assert ray behaviour only and never copy the cone tests.

## C4. Keystone 1.15.0 - hold-post + leash (T2) - new `KS/NpcPost.java`, `KS/AbstractNpc.java`
```java
package org.luckyraven.keystone.npc;
/** Where an NPC stands guard. @since 1.15.0 */
public record NpcPost(Location anchor, double leashRadius, @Nullable Location watch) {
	public static final double ARRIVE_RADIUS = 1.5;
	// compact ctor: anchor = anchor.clone() (requireNonNull), watch = watch == null ? null : watch.clone(), leashRadius >= ARRIVE_RADIUS
	/** Horizontal distance from the anchor <= leashRadius, same world. */
	public boolean withinLeash(@Nullable Location at)
	public boolean arrived(@Nullable Location at)     // horizontal distance <= ARRIVE_RADIUS, same world
}
// AbstractNpc additions (@since 1.15.0); state: private @Nullable NpcPost post
public void holdPost(NpcPost post)        // stores it and navigateTo(post.anchor())
public @Nullable NpcPost getPost()
public void releasePost()                 // post = null, stopNavigation()
/** Call once per AI tick while posted. Not arrived and (outside the leash, or !navigation.isCitizensNavigating()) ->
 *  navigateTo(anchor), false. (isCitizensNavigating is the package-private NpcNavigationDelegate accessor, :351, same package:
 *  no new public API.)
 *  Arrived -> stopNavigation(), face post.watch() when set, true. No post -> false. */
public boolean tickPost()
```
Facing the watch point is NEW code: a private helper taking a Location that sets the entity's yaw/pitch toward it with no aim
error (`faceTarget` :656 is a one-line delegate to `NpcCombatDelegate.faceTarget(Player)`, which applies aim error; it is not
reused). `destroy` (:354) clears the post (`cleanupTransientState` is abstract and subclass-owned, so the clear goes in `destroy`).
No squad, combat or target logic in Keystone: the consumer decides what a post watches and when it ends.

## C5. Stations, admin regions, setup points (T7) - cops-n-crooks
```java
// CNC/station/Station.java (@Getter; mutable only through the registry)
public final class Station {
	public Station(int id, String name, String world, double x, double y, double z, float yaw, @Nullable Integer jailId)
	public @Nullable Location getLocation()   // null when the world is not loaded
}
// CNC/station/StationRegistry.java implements BeanLifecycle (JailExitService shape, CNC/jail/JailExitService.java:17-64)
public final class StationRegistry {
	public StationRegistry(IRepository<Station> repository)       // setDataSupplier(stations::values) in the constructor
	public @Nullable Station create(String name, Location anchor)  // id = max+1, persisted at once (repository.save);
	                                                               // null (nothing stored) when byName(name) already exists
	public boolean remove(int id)
	public void linkJail(int id, @Nullable Integer jailId)         // persisted at once
	public @Nullable Station get(int id)
	public @Nullable Station byName(String name)                   // case-insensitive, first match
	public List<Station> all()                                     // by id
	public @Nullable Station nearest(Location at)                  // same world, horizontal distance, any distance
}
// table "cop_station": id INT PK, name VARCHAR, world VARCHAR, x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, jail_id INT NULL
// District: never stored; resolved live: placeNames.withTag(station.getLocation(), PlaceRegion.TAG_DISTRICT).

// CNC/place/AdminRegion.java: id, name, world, minX..maxZ (int, normalised), Set<String> tags
//   public PlaceRegion toPlace()  -> new PlaceRegion("copsncrooks:" + id, name, world, Cuboid.of(...), NO_OWNER, tags)
// CNC/place/AdminRegionRegistry.java implements BeanLifecycle, RegionProvider (source "copsncrooks")
	public AdminRegion create(String name, Location corner1, Location corner2, String tag)   // same world required, else IllegalArgumentException
	public boolean remove(int id);  public @Nullable AdminRegion get(int id);  public List<AdminRegion> all();
	public List<AdminRegion> withTag(String tag);  public List<PlaceRegion> regionsAt(Location at);
// table "cop_region": id INT PK, name, world, min_x, min_y, min_z, max_x, max_y, max_z INT, tags VARCHAR (comma-separated lowercase)

// CNC/place/SetupPoint.java: id, kind ("pickup" | "breaker_trigger"), name, world, x, y, z; getLocation() nullable
// CNC/place/SetupPointRegistry.java implements BeanLifecycle
	public SetupPoint create(String kind, String name, Location at);  public boolean remove(int id);
	public @Nullable SetupPoint get(int id);  public List<SetupPoint> all();  public List<SetupPoint> ofKind(String kind);
// table "cop_point": id INT PK, kind, name, world, x, y, z DOUBLE
```
Beans in new `CNC/config/RegistryModuleConfig.java` (registered in `CNC/CopsNCrooksModule.java:35-40`):
`stationRegistry(RepositoryRegistry)`, `adminRegionRegistry(RepositoryRegistry, PlaceNames)` (calls `placeNames.register(registry)`),
`setupPointRegistry(RepositoryRegistry)`; repositories found by the existing module package scan, fetched with
`repositoryRegistry.getRepository(X.class)` as `CopsNCrooksModuleConfig.java:171-174` does.

Spawner grouping (T7) - `CNC/npc/police/spawn/CopSpawner.java` gains `@Nullable Integer stationId` (getter/setter);
`CNC/database/CopSpawnerTable.java` appends `station_id INT NULL` LAST; `CopSpawnerRepository.doLoadAll` reads index 7 only when
the row has it. `CopSpawnManager` (`CNC/npc/police/spawn/CopSpawnManager.java`) gains:
```java
public void assignStation(int spawnerId, @Nullable Integer stationId)   // persists that spawner
public int assignNearby(Station station, double radius)                 // every UNASSIGNED spawner within radius (horizontal, same
                                                                        // world); one already in another station is skipped; returns count
public void unassignStation(int stationId)
public List<CopSpawner> spawnersOf(int stationId)
```
`/glw cop spawner set` (`CNC/command/cops/spawner/CopSpawnerSetCommand.java`) assigns the new spawner to `stations.nearest(loc)`
when it is within `Cops.Dispatch.Station_Radius`.

Id floor over unloaded worlds (T7, docket T-180, P1 data loss). Today `doLoadAll` skips a row whose world is not loaded
(`if (world == null) continue`) BEFORE anything records its id, so the next created row reuses that id and the upsert overwrites
the unloaded world's row. Keystone `EntitySpawner.loadStoredSpawners` (private) sets `ID` to the max LOADED id; `JailRepository`
sets `JailService.ID = id` inside the loop after the skip (and last-row, not max). Fix, local to each repository + its manager, NO
Keystone change (`EntitySpawner.ID` is a public field and `onInitialize`/`reloadSpawners` are public, non-final):
```java
// CNC/database/CopSpawnerRepository.java and CIV/database/CivilianSpawnerRepository.java (same shape)
private int highestStoredId;                     // reset to 0 at the top of doLoadAll; = max(highestStoredId, id) for EVERY
public int getHighestStoredId()                  // row, read right after the id column, BEFORE the unloaded-world skip
// CNC/npc/police/spawn/CopSpawnManager.java and CIV/npc/spawn/CivilianSpawnManager.java (same shape; no constructor change)
@Override public void onInitialize(boolean firstLoad)   // existing override: super first, then raiseIdFloor(), then its own work
@Override public void reloadSpawners()                  // new override: super.reloadSpawners(); raiseIdFloor();
private void raiseIdFloor()                             // if (repository instanceof CopSpawnerRepository stored)
                                                        //     ID = Math.max(ID, stored.getHighestStoredId());
                                                        // ponytail: instanceof, a mocked IRepository keeps today's floor;
                                                        // a Keystone hook (protected storedMaxId()) if a third spawner needs it
```
`CNC/database/JailRepository.java`: `JailService.ID = Math.max(JailService.ID, id)` moved ABOVE the `world == null` skip (the
static is never reset, so a reload only raises it). `JailExitRepository` needs no change: `row_id` is the jail id (or
`JailExitTable.GLOBAL_ROW_ID` -1), never a counter, so its skipped rows were overwritten only through a reused jail id, which the
`JailRepository` floor ends; re-setting an exit for the same jail or the global exit replaces it on purpose.
`TurfPowerupNpcRepository` (gangland-turf) needs no change: its key is the turf id (turf rows load whatever the world), one row per
turf, so a new powerup NPC for that turf replaces it on purpose. The new `cop_station`/`cop_region`/`cop_point` registries keep
every row (`getLocation()` is nullable, world stored by name), so their `max+1` already covers unloaded worlds.

`CopRadio` (T7): NO constructor change (both constructors keep their signatures; `CopRadioRolesTest` and `CopRadioTest` construct
it). New field `private PlaceNames places = new PlaceNames()` (inert) and `public void setPlaceNames(PlaceNames places)`, called by
the `CopsNCrooksModuleConfig` bean method that builds the radio (it gains a `PlaceNames` parameter). New
`public String placeOf(@Nullable Location at)` = `places.locate(at)` or the radio file's `Unknown_Place` word (T4).
`%place%` on every line (Ruling R44): `voice(group)` becomes an instance method and its `extras` adds
`place` = `placeOf(group.getSquad().lastKnownLocation())` (last-known, never the live position); both `dispatch` forms merge
`place` = `placeOf(target.getLocation())` unless the caller's extras set it, so `CopManager.java:119` stays the 5-arg call.

## C6. Cop config, composition tiers, radio lines (T4)
Records in `CNC/npc/police/config/` (each with `DEFAULT` = CONSTRAINTS values and, where noted, `DISABLED`):
```java
public record DispatchSettings(boolean enabled, double unitSpeed, int minEtaSeconds, int maxEtaSeconds, double stationRadius,
                               int rejoinGraceSeconds) { DEFAULT; DISABLED; public long etaMs(double distance) }   // clamp, ceil to s, x1000
public record BreatherSettings(boolean enabled, List<Integer> seconds, int wipeWindowSeconds) { DEFAULT; DISABLED; public long breatherMs(int level) }
public record HandoffSettings(boolean enabled, int headingSeconds, int biasSeconds, double coneDegrees) { DEFAULT; }
public record PerimeterSettings(boolean enabled, int minLevel, int posts, List<String> roles, int maxSeconds, double laneLength,
                                double sightRange, double leashRadius) { DEFAULT; }
```
`CopConfigProvider` default methods returning `X.DEFAULT`: `getDispatchSettings()`, `getBreatherSettings()`, `getHandoffSettings()`,
`getPerimeterSettings()`, and `default List<Integer> getSquadTiers(int wantedLevel)` (same length as `getSquadComposition`, 0 =
"the star's tier"; empty when no composition). `YamlCopConfigProvider` reads `Cops.Dispatch|Breather|Handoff|Perimeter` from
cops.yml (missing block = DEFAULT, bad value = default + one warning) and parses `"<Role>@<tier>"` in `Squad_Composition`
(getSquadComposition :469, parse block ~:596-650; an unknown tier id or non-number = 0 with a warning).
`CopRole` (`CNC/npc/police/config/CopRole.java`, nextRole :178): `public static int nextSlot(@Nullable List<CopRole> composition,
List<CopRole> live)` = index of the first unfilled entry, else the last index, -1 when composition is null/empty; `nextRole`
delegates to it (behaviour unchanged).

Radio (`CNCR/copsncrooks/cop_radio_messages.yml` + `_es.yml`, code fallbacks in `CopRadioMessages.DEFAULT_LINES` so an upgraded file
still speaks them; `CNC/npc/police/radio/CopRadioMessages.java:98`):
| Key | Origin | Placeholders | Bundled English |
|---|---|---|---|
| `Dispatch_Wanted` (text change; its `DEFAULT_LINES` fallback, :75, gets the same text) | Dispatch | %target% %level% %place% | "All units, be advised: %target% is wanted in %place%, level %level%." |
| `Dispatch_En_Route` | Dispatch | %count% %station% %eta% %place% | "%count% units en route from %station%, ETA %eta% s." |
| `Wipe_Refill` | Dispatch | %eta% | "Squad down. Backup inbound in %eta% s." |
| `Handoff` | leader | %direction% | "Lost him heading %direction%. Units ahead, pick him up." |
| `Post_Up` | the posted cop | %place% | "Holding the corner." |
| `Eyes_On` | the posted cop | %place% %direction% | "Eyes on suspect near %place%, moving %direction%." |
| `Returning_To_Patrol` | leader | - | "Units returning to patrol." |
Root word `Unknown_Place: "the area"` beside `Compass`. Bundled `Contact_Lost` English (and `_es`) gains the place: "Lost visual
near %place%. Searching the last known position." (its `DEFAULT_LINES` fallback too; an old server file keeps its own text).
Priority (Ruling R43): `Dispatch_En_Route` and `Wipe_Refill` are dispatch lines that must not be swallowed by the 20-tick player gap
right after the priority `Dispatch_Wanted` (`SquadRadio.speak`, gangland-api `npc/radio/SquadRadio.java:183-240`). T4 appends both
to the bundled `Cops.Radio.Priority` list (cops.yml :207-221), to the code default set (`CopConfigProvider.java:49`), and
`YamlCopConfigProvider` unions them into whatever `Priority` list a server file has (a server list still replaces the rest of the
bundled list wholesale, as in 0.15). Cooldown ticks `Handoff 200`, `Post_Up 100`, `Eyes_On 60`, `Returning_To_Patrol 1200`,
`Dispatch_En_Route 0`, `Wipe_Refill 0`.
`CopRadio` overloads (T4): `public boolean dispatch(CopGroup group, Player target, String key, int level, String tier,
Map<String, String> extra)` (merges level/tier with extra; the 5-arg form delegates with `Map.of()`) and
`public boolean sayFromLeader(CopGroup group, String key, Map<String, String> extra)`, and
`public String compassWord(Location from, Location to)` = the radio file's `Compass` word at `RadioSides.compass8(from, to)` (the
lookup `directionTo` :453 already does, made reusable; `directionTo` delegates to it).

## C7. Chase config and the risk-11 fix (T5)
- New records in `CNC/wanted/config/`: `HideoutSettings(boolean enabled, double speed)`, `QuietSpeedSettings(boolean enabled,
  double perMinute, double max, int backupSkipSeconds)` with `double speedFor(long quietMs)` = `enabled ? min(max, 1 + perMinute x
  floor(quietMs / 60000)) : 1.0`, `BribeStarSettings(boolean enabled, int stars, int respawnSeconds, double pickupRadius, String item)`.
  Each has `DEFAULT`.
- `EvasionSettings` gains components `HideoutSettings hideout, QuietSpeedSettings quietSpeed, double maxSpeed` (keep every
  existing constructor as a delegating overload with the defaults, so 0.15.2 callers and `EvasionClockTest` compile unchanged).
  `ChaseConfig` gains a 5th component `BribeStarSettings bribeStars` (`Wanted.Bribe_Stars`), with a 4-arg constructor
  `ChaseConfig(heat, evasion, hud, chargeSheet)` that delegates with `BribeStarSettings.DEFAULT`, and the canonical constructor
  maps a null `bribeStars` to DEFAULT. The 4-arg calls in 0.15.2 tests (JailIntakeServiceTest, ChaseArcListenerTest,
  WantedHudListenerTest, HeatWantedTrackerTest, EvasionClockTest, HeatLedgerTest, HudFixtures, ChaseLearnerLifecycleTest,
  ChaseLearnerTest) compile unchanged.
- `AutoSettings` gains `int rampageMinWeight` (`Wanted.Evasion.Auto.Rampage_Min_Weight`, 80; negative -> 80 + warning; keep the
  old constructor delegating 80).
- The seam (0.15.2 `CNC/wanted/evasion/ChaseArcs.java` `view(UUID, List<CrimeRecord>, AutoSettings)`; find it BY NAME after the
  0.15.2 merge - 0.15.2 commit e441a06c added the private `onChase` prefilter, called first in `view`, and `hadCrime`): new overload
  `public @Nullable AutoDrop.ChaseView view(UUID id, List<CrimeRecord> crimes, AutoSettings settings, ToIntFunction<String> weightOf)`.
  Its body is the 3-arg body with only the opening rule changed: it FIRST keeps `crimes = onChase(arc, crimes, settings)` (a crime
  before `begunAt - Opening_Seconds` counts for nothing, as in 0.15.2), then counts toward `opening` only the on-chase records with
  `weightOf.applyAsInt(record.crimeId()) >= settings.rampageMinWeight()`, and the window starts at the FIRST such heavy on-chase
  record: `cutoff = firstHeavy.at() + openingSeconds*1000` (no heavy record -> opening 0). `crimes` (= the on-chase size), `kill` and
  every other ChaseView field are computed from the on-chase list exactly as the 3-arg body does.
  `CrimeRecord.heat()` is never used for this (it is after multipliers: Car_Theft 60 seen x1.5 = 90). The existing 3-arg `view`
  delegates with `id -> Integer.MAX_VALUE` (every crime heavy = 0.15.2 behaviour), so `ChaseArcsTest`'s and
  `ChaseArcListenerTest:92`'s calls compile and behave unchanged. The one production caller, `EvasionClock.views` (find `arcs.view(`;
  0.15.2 HEAD :293), switches to the 4-arg form with
  `requireNonNullElse(config.get().heat(), HeatSettings.DEFAULT)::weightOf` (`HeatSettings.weightOf(String)` :47; EvasionClockTest
  builds `ChaseConfig(null, settings, null, null)`). EvasionClockTest spies a real ChaseArcs, so only its
  `verify(arcs, never()).view(any(), any(), any())` (find it by text; 0.15.2 HEAD :706) must move to the 4-arg matcher or it turns vacuous; T5 owns that one
  line. `copKilled` and `crimes` are unchanged; PETTY unchanged (PLAN Ruling R38).

## C8. Dispatch queue (T11) - cops-n-crooks
```java
// CNC/npc/police/dispatch/PendingUnit.java
/** bias = the hand-off bias active WHEN THE UNIT WAS ENQUEUED (null when none); it travels with the unit, so a unit that
 *  arrives after the 10 s bias window still spawns ahead and seeded. */
public record PendingUnit(@Nullable CopRole role, int tier, long arriveAt, @Nullable Station station, @Nullable SpawnBias bias) {
	public boolean fromStation()   // station != null; the 0.20 mounted-unit flag reads this
}
// CNC/npc/police/dispatch/SpawnBias.java   (set by the hand-off, T17; consumed by dispatch, T11)
/** lastSeen = the suspect's position at the hand-off (newest heading sample): the seed new units report, never his live position. */
public record SpawnBias(Vector heading, Location lastSeen, long until, double coneDegrees) {
	// compact ctor: lastSeen = lastSeen.clone() (requireNonNull)
	public boolean activeAt(long now)                     // now < until
	public boolean ahead(Location from, Location spot)    // horizontal angle between heading and (spot - from) <= coneDegrees
}
// CNC/npc/police/dispatch/Dispatcher.java
public final class Dispatcher {
	public Dispatcher(StationRegistry stations, Supplier<CopConfigProvider> config)   // bean: dispatcher(StationRegistry, CopLoader)
	                                                                                   // -> new Dispatcher(stations, copLoader::getLoadedProvider)
	public record Plan(@Nullable Station station, long etaMs) { }
	/** Disabled -> Plan(null, 0). Else the nearest station in the target's world; with an active bias, the nearest station
	 *  whose anchor is ahead (SpawnBias.ahead) wins over the nearest one only when its ETA is at most the nearest's ETA +
	 *  Handoff Bias_Seconds (a far station ahead never turns a 5 s response into 40 s); no station -> Plan(null, 0).
	 *  eta = etaMs(distance). */
	public Plan plan(Player target, long now, @Nullable SpawnBias bias)
}
```
Config reads (every 0.16 class): `CopConfigProvider` is NOT a container bean (no config class produces one; `CopLoader` replaces the
instance on every load, `CopLoader.java:44`/`:71`; Keystone `BeanFactory.resolveParameter` throws for an unregistered type). A class
that needs it takes `Supplier<CopConfigProvider>` (its bean method takes `CopLoader` and passes `copLoader::getLoadedProvider`, the
`CopRadio.java:67` / `CopFieldCare` pattern) and reads it on EVERY call with `requireNonNullElse(provider.getXSettings(),
XSettings.DEFAULT)`, so `/glw reload` takes effect. Never a `CopConfigProvider` bean parameter, never a snapshot taken at bean time.
This covers Dispatcher (T11), PerimeterController (C9, T15), HandoffController (C11, T17), the `Station_Radius` read of
`CopSpawnerSetCommand` (T7) and the wand's station save (T12).
Config reads (every 0.16 class): `CopConfigProvider` is NOT a container bean (no config class produces one; `CopLoader` replaces the
instance on every load, `CopLoader.java:44`/`:71`; Keystone `BeanFactory.resolveParameter` throws for an unregistered type). A class
that needs it takes `Supplier<CopConfigProvider>` (its bean method takes `CopLoader` and passes `copLoader::getLoadedProvider`, the
`CopRadio.java:67` / `CopFieldCare` pattern) and reads it on EVERY call with `requireNonNullElse(provider.getXSettings(),
XSettings.DEFAULT)`, so `/glw reload` takes effect. Never a `CopConfigProvider` bean parameter, never a snapshot taken at bean time.
This covers Dispatcher (T11), PerimeterController (C9, T15), HandoffController (C11, T17), the `Station_Radius` read of
`CopSpawnerSetCommand` (T7) and the wand's station save (T12).
`CopManager` gets the Dispatcher as a new LAST constructor parameter (`CopManager.java:80`); its only construction sites are
`CopsNCrooksModuleConfig` and the test `CopManagerFixture`, both T11's.
`CopGroup` additions (`CNC/npc/police/CopGroup.java`):
```java
public void enqueue(PendingUnit unit);   public List<PendingUnit> takeDue(long now);   public void requeue(PendingUnit unit);
public int pendingCount();   public boolean hasPendingUnits();   public void clearPending();
/** Any pending unit with arriveAt + 10_000 > now. ponytail: a unit overdue by 10 s (it keeps failing to spawn) stops counting,
 *  so evasion falls back to 0.15 behaviour instead of holding the stars forever; make it a knob if 10 s proves wrong. */
public boolean unitsEnRoute(long now);
public void setBias(@Nullable SpawnBias bias);   public @Nullable SpawnBias biasAt(long now);   // null once expired
public void setBackupHeld(boolean held);   public boolean isBackupHeld();
// requestBackup (:158) and grantRegroupBackup (:170) return false and grant nothing while held; backupExtra (:179) unchanged
public boolean casualtyWithin(long now, long windowMs);
public long getBreatherUntil();   public void setBreatherUntil(long until);
```
`CopSpawnManager` (T11): NO constructor change (it needs no StationRegistry: the unit carries its Station, and a removed
station's spawners are already unassigned, so `spawnersOf` is empty and the unit falls through to the ring). New:
```java
/** Spawns one due unit: (1) its station's spawners (spawnersOf(unit.station().getId())) within Spawner_Preference_Radius of
 *  target, allowed and isOutOfSight, nearest first; (2) hiddenRing(target, allowed AND (unit.bias() != null ? bias.ahead : station
 *  side within 60 degrees of the target->station bearing; no station: any)); (3) hiddenRing(target, allowed);
 *  (4) today's spawnNearPlayer(target, unit.tier(), allowed, unit.role()). Null when all fail (the unit is requeued). */
@Nullable public CopNpc spawnUnit(Player target, PendingUnit unit, Predicate<Location> allowed)
/** findHiddenSpawnLocation wrapped exactly like findRingLocation (:~93-115): a suspect indoors whose first pass finds nothing
 *  is searched again with anyRoof = true (the isOutdoor override), reset in finally. Package-private. */
@Nullable Location hiddenRing(Player target, Predicate<Location> allowed)
```
`spawnNearPlayer(Player,int,Predicate<Location>,CopRole)` (:79) keeps its signature and behaviour (pinned by `CopSpawnManagerFallbackTest`).
`CopManager` (T11): `public void onWantedStart(Player player, Wanted wanted, WantedCause cause)` (the 2-arg form :108 delegates with
`WantedCause.UNKNOWN`); `CNC/listener/detainment/CopListener.java:43` passes `event.getCause()`. RESTORE: no crime-scene
`reportSighting` seed, and `group.setBreatherUntil(now + Rejoin_Grace_Seconds*1000)` (the hold every enqueued unit waits out;
the evasion clock holds through it, C10). Debug lines (`log.debug`, read by acceptance verdicts): `DISPATCH {player} count={n}
station={name|ring} eta={s}s hold={s}s reason={wipe|restore|none} bias={true|false}` once per enqueued batch; `UNIT {player}
callsign={c} tier={t} role={r} fromStation={b} hidden={b} bias={true|false} ahead={true|false|-} at={x},{y},{z}` per spawn
(`ahead` = `unit.bias().ahead(target location, spawn spot)`, `-` without a bias).

## C9. Perimeter (T15)
- `CNC/npc/police/state/CopState.java`: append `POSTED`. `CopBehaviorFactory.createBehaviors` registers
  `new PostedBehavior()` (`CNC/npc/police/state/behavior/PostedBehavior.java`): each AI tick `cop.tickPost()`; `onExit` calls
  `cop.releasePost()` (so a POSTED cop that `CopManager.fightResisting` (:798-806) forces into COMBAT when the suspect attacks drops
  its post). `fightResisting` only touches a cop whose `getTargetPlayerId()` equals the group target (:800), so `start` posts ONLY
  cops whose `getTargetPlayerId()` is the player (a PURSUING cop can be chasing a civilian or another attacker, :853/:903) and a
  posted cop keeps that target id; tests set it; nothing else (no leash give-up, no cuffing). `CopManager.recycles` (:626-631) already skips non-PURSUING/COMBAT states;
  `EvasionClock.hasLiveCop` counts POSTED as live (non-RETURNING) - both pinned by T16 characterization tests (T16 owns
  `CopManagerStuckTest` and `EvasionClockTest` in W4), neither production file edited for this.
- `CNC/npc/police/perimeter/PerimeterController.java` (bean in `EvasionModuleConfig` taking `CopLoader`, config read per call through
  `Supplier<CopConfigProvider>` (C8 "Config reads"), hook `copManager.addAiTickHook(controller::tick)`):
```java
public void start(Player player, Location centre, double radius, int level)   // from SEARCHING; no-op when disabled, level < Min_Level, active or spent
public void end(Player player, CopState postsTo)                             // releasePost + transitionTo for every post; clears active
public void tick(Player player, @Nullable CopGroup group)                    // sightings; drops posts that are dead or no longer POSTED; Max_Seconds timeout
public boolean isActive(UUID playerId)
```
- Ring radius passed to PostRing = `min(zone radius, 0.8 x Sight_Range)` (CONSTRAINTS; posts on a 90-180 block zone ring could
  never see a suspect near the centre with Sight_Range 40).
- `CNC/npc/police/perimeter/PostRing.java`: `static List<Location> find(Location centre, double radius, int count, double laneLength)`
  = 8 candidates at 45, 225, 135, 315, 0, 180, 90, 270 degrees on the ring, in that order, kept when the chunk is loaded, a floor with
  two air blocks above exists within 8 blocks of centre.getY(), and `world.rayTraceBlocks(spot+1.6, toward centre, min(laneLength,
  radius), NEVER, true) == null`; the first `count` kept that are >= 90 degrees apart.
- `CNC/listener/police/PerimeterListener.java`: `WantedEvasionStateEvent` MONITOR: SEARCHING with non-null centre -> `start`; SEEN ->
  `end(player, PURSUING)`; OFF -> `end(player, RETURNING)`.
- Debug: `PERIMETER {player} start posts={n} radius={r}` / `PERIMETER {player} end reason={CONTACT|OFF|TIMEOUT|SIGHTED}`.

## C10. Evasion multipliers (T16)
- `CNC/wanted/evasion/Hideouts.java`: `public Hideouts(PlaceNames places, GangMembership gangs)`;
  `public @Nullable PlaceRegion at(Player player)` = smallest region at the player's location tagged `hideout` whose
  `ownerGangId` is -1 or `gangs.gangIdOf(player.getUniqueId())`; `public @Nullable String idAt(Location at, UUID player)` (same rule
  at a location; id or null).
- `CNC/wanted/evasion/QuietTrail.java` (bean, aiTickHook): `public QuietTrail(HeatLedger ledger, ChaseArcs arcs,
  ChaseConfigLoader config, CopRadio radio, LongSupplier clock)`; `public long quietMs(UUID id, long now)`: no arc -> 0 ("not
  quiet"). Anchor (Ruling R12): `last = ledger.lastCrime(id)` when non-null AND `arcs.hadCrime(id, List.of(last), auto)` (the same
  on-chase rule as `view`: at or after `begunAt - Opening_Seconds`; `HeatLedger.lastCrime` has no chase bound and the ledger keeps
  sub-threshold crimes with no decay), else the arc's offline-shifted `startedAt()`. So a sign-, admin- or kill-combo-raised chase
  over a stale ledger crime is quiet from its own start, never from the old crime.
  `public double speed(UUID id, long now)` = `quietSpeed.speedFor(quietMs)`; `public void tick(Player, @Nullable CopGroup)` sets
  `group.setBackupHeld(enabled && quietMs >= Backup_Skip_Seconds*1000 && !squad.hasFreshSighting())` and on its false->true edge
  says `Returning_To_Patrol` once (re-armed when held turns false).
  Offline time is never quiet: `ChaseArc` (T16) gains a package-private `long offlineTotal` (+ `public long offlineTotal()`),
  `ChaseArcs.restore` adds each shifted gap to it, and `ChaseArcs` gains `public long offlineTotalMs(UUID id)` (0 when no arc).
  Crime anchor: QuietTrail remembers per player the `offlineTotalMs` seen when it first saw that crime (keyed by its `at()`), and
  `quietMs = now - last.at() - (offlineTotalMs(id) - remembered)`. Start anchor: `quietMs = now - startedAt()` (already shifted).
  An arc pruned after 30 min offline yields no total, but the rejoin is then a RESTORE start with a NEW arc (`begunAt` = the
  rejoin), every older crime is off-chase, and quiet runs from the rejoin: a long absence is never quiet either.
- `EvasionClock`: constructor gains `Hideouts hideouts, QuietTrail quiet`; `Track` gains `@Nullable String seenHideout` (= hideout id
  containing the search centre, set in startSearch); speed = `min(maxSpeed, zone x hideoutFactor x quietFactor)`, quietFactor =
  `max(1.0, quiet.speed(id, now))` (cold trail only ever speeds up; also keeps a mocked QuietTrail from freezing the clock), hideoutFactor =
  `hideout.speed` when enabled and `hideouts.at(player)` is non-null with an id != seenHideout, else 1.0.
- Units en route (Ruling R20): `hasLiveCop(group)` and so `handlesDecay` are also true when `group.unitsEnRoute(now)` (C8), so the
  Repeating_Timer safety net cannot drop a star while units travel. But while the group has NO valid non-RETURNING cop in the world
  (only units en route), `tick` HOLDS: like the tip-off guard it sets `track.lastTick = now` when a track exists and returns before
  the SEEN/search logic - no track is created, no search starts, no progress accrues, no countdown fires. The clock starts with the
  first unit that stands in the world. This covers a far-from-station crime (up to 40 s ETA) and the RESTORE rejoin grace (W3).
- Debug at each countdown change: `EVASION {player} speed={f} zone={f} hideout={f} quiet={f}`; once per hold start
  `EVASION {player} hold=enroute`.

## C11. Hand-off (T17)
`CNC/npc/police/handoff/HandoffController.java` (constructor `(CopManager, CopRadio, Supplier<CopConfigProvider>)`; bean
`handoffController(CopManager, CopRadio, CopLoader)` in `CopsNCrooksModuleConfig`, config read per call (C8 "Config reads"),
`copManager.addAiTickHook(controller::tick)`):
per player a deque of (time, location) samples kept for `Heading_Seconds`; `engaged` = any valid cop of the group in PURSUING,
COMBAT or POSTED. On engaged -> not engaged while the player is wanted and a group cop is RETURNING farther than
`Cops.Pursuit.Max_Distance` from him: heading = newest - oldest sample (horizontal; under 2 blocks of travel -> the player's facing),
`group.setBias(new SpawnBias(heading, newest, now + Bias_Seconds*1000, Cone_Degrees))`, `copRadio.sayFromLeader(group, "Handoff",
Map.of("direction", copRadio.compassWord(oldest, newest)))`. Once per bias. Debug `HANDOFF {player} heading={word}`.
Seeding (T11, inside spawnTick): a unit whose `unit.bias() != null` (the bias active when it was ENQUEUED, C8) gets, when it spawns,
`squad.reportSighting(unit.bias().lastSeen())` (the last-known sighting, never the live position) and `group.markTipOff(now)` (so
EvasionClock holds instead of reading SEEN, `EvasionClock` tip-off guard).

## C12. Contacts and the [WANTED] sign gate (T10) - gangland-impl
```java
// IMPL/data/wanted/ContactDesk.java (core bean in GameplayConfig)
public final class ContactDesk {
	public ContactDesk(LongSupplier clock)
	public void observe(UUID id, EvasionState state)   // OFF removes the SEEN-state entry only
	public void forget(UUID id)                         // quit: removes the SEEN-state entry only
	public boolean seen(UUID id)                        // last observed state == SEEN; no entry -> false
	public long cooldownLeftMs(UUID id)
	public void startCooldown(UUID id)                  // now + Settings.getContactsCooldownSeconds() * 1000
	// Cooldowns live in their OWN map, untouched by observe/forget: they survive OFF (the contact itself ends the chase), a
	// quit/rejoin and a new chase; expired entries are pruned on startCooldown. Memory only (a restart clears them, PLAN risk 7).
	public BigDecimal priceFor(int stars)               // Settings.getContactsPricePerStar() x stars
}
```
`IMPL/listener/wanted/EvasionStateListener.java`: `WantedEvasionStateEvent` MONITOR -> `desk.observe`; `PlayerQuitEvent` -> `forget`.
`/glw contact [stars]` (`IMPL/command/sub/contact/ContactCommand.java`, stars = OptionalArgument 1..Max_Stars, default 1): disabled ->
CONTACT_DISABLED; not wanted -> CONTACT_NOT_WANTED; `desk.seen` -> CONTACT_SEEN; cooldown -> CONTACT_COOLDOWN; n = min(stars, level);
price = priceFor(n); wallet < price -> CONTACT_NO_MONEY; else withdraw, `wanted.setLevel(level - n, WantedCause.CONTACT)`,
`startCooldown`, CONTACT_USED.
`WantedAspect` (`IMPL/sign/aspect/WantedAspect.java`): constructor gains `ContactDesk desk`; for REMOVE/CLEAR while
`Settings.isContactsEnabled()`: `canExecute` (:64) is false when `desk.seen(id)`, or when `sign.getPrice() > 0` and
`desk.cooldownLeftMs(id) > 0`; ADD a `failureReason` override (WantedAspect has none; `SignAspect`'s default returns null) returning
the CONTACT_SEEN / CONTACT_COOLDOWN text; a successful paid REMOVE/CLEAR calls
`startCooldown`. INCREASE and price-0 cooldowns untouched. No money moves on refusal because
`AspectBasedSignHandler.canHandle` (`gangland-ui/sign-api/.../sign/handler/AspectBasedSignHandler.java:41-48`) asks every aspect's
`canExecute` before any executes (the [WANTED] sign's aspects are [money, wanted], `WantedSign.java:43`).
Placeholders (`GanglandPlaceholder`, wired at `IMPL/config/WiringConfig.java:115`): `%gangland_contact_price%` (per star, formatted)
and `%gangland_contact_cooldown%` (seconds left, or "ready").

## C13. Hospital and one bill (T8) - gangland-impl
- `WaypointManager` (`IMPL/data/teleportation/WaypointManager.java`): `public @Nullable Waypoint nearest(Location at,
  Waypoint.WaypointType type)` = same world name, `getLocation() != null`, smallest distanceSquared.
- `PlayerDeathListener`: `handleMoney(User)` (:133) splits into package-private `BigDecimal quote(User)` (Lose_Money, formula with
  variables read NOW, `== 0` skip, bank discount, `<= 0` -> ZERO) and `void charge(User, BigDecimal)` (withdraw clamped, ward-bill
  message when taken > 0); `handleMoney` = `charge(user, quote(user))`. `onPlayerDowned` (:92) with `Settings.isHospitalEnabled()`:
  `handleCommandExecution` unchanged; when it returns false, `pendingBills.put(id, quote(user))` instead of charging. New
  `onPlayerUndowned(PlayerUndownedEvent)` MONITOR and `onQuit(PlayerQuitEvent)` LOWEST: `charge(user, pendingBills.remove(id))` when present.
- `CustomPlayerDeathListener.performRespawn` (:264): when hospital enabled and `waypointManager.nearest(player.getLocation(), HOSPITAL)`
  has a location: `player.teleport(it)` and skip the configured teleport (:285-297); else unchanged. New `PlayerDeathEvent` MONITOR
  (remember `player.getLocation()` per UUID) and `PlayerRespawnEvent` HIGH: hospital enabled, not `isBedSpawn()`, not
  `isAnchorSpawn()`, nearest HOSPITAL in the remembered death world -> `setRespawnLocation`. The jail MONITOR handler
  (`CNC/listener/police/DetainmentListener.java:74-81`) still wins afterwards. The DEATH wanted reset is untouched.
- Detainment on the downed path (T8): `performRespawn` teleports before it fires `PlayerUndownedEvent`, and the jail override
  exists only on `PlayerRespawnEvent`, so a jailed or handcuffed player who is downed would wake at the hospital. New
  `DetainmentListener.onUndowned(PlayerUndownedEvent)` MONITOR (gangland-core `core.downed.PlayerUndownedEvent`): handcuffed ->
  a death-commit (Ruling R48, DECISIONS D23): `transitService.cancel(player)` then `jailIntake.admit(player, true)` (DetainmentListener
  gains a `JailIntakeService` field; it is constructed only by the listener scan); then jailed -> teleport to
  `jailService.getJailRegistry().getJailLocation(id)` and `detainmentService.handleRespawn(player)` (as `onRespawn`).
- One charge for a down that turns into an arrest: `JailIntakeService` gains `public boolean admit(Player player, boolean
  deathCommit)`; `admit(Player)` (the transit callback, `CopsNCrooksModuleConfig` `setOnCommit(intake::admit)`) delegates with
  `player.isDead()`; the sheet is skipped when `deathCommit` (today's `player.isDead() ? 0 : chargeSheet(...)`, :81-82). The
  undowned player is alive, so without the flag he would pay the ward bill (charged on the same event) AND the sheet. He pays
  exactly one charge, the ward bill: the spec keeps 0.15.0's "no sheet when the arrest commits on death" until 0.18.
- Hospital respawn shield (owner ruling D19 = the alternative, 2026-10-07; T8). New `IMPL/data/teleportation/HospitalShield.java`
  (plain class, bean `DataConfig.hospitalShield()`; memory only, PLAN risk 7):
```java
public final class HospitalShield {
	public HospitalShield()                        // System::currentTimeMillis
	HospitalShield(LongSupplier clock)             // package-private, tests
	public void grant(Player player)               // reads Settings.getHospitalShieldSeconds() per call (reload applies);
	                                               // <= 0 -> no-op (no entry, no message); else until = now + s*1000,
	                                               // sends Messages.DEATH_HOSPITAL_SHIELD (%seconds%), debug line below
	public boolean isShielded(UUID id)             // now < until; an expired entry is removed on read
	public void end(UUID id)
}
```
  Granted in `CustomPlayerDeathListener` (constructor gains `HospitalShield`) on BOTH hospital branches, right after the hospital
  is chosen: `performRespawn` after `player.teleport(hospital)` (downed path) and the `PlayerRespawnEvent` HIGH handler after
  `setRespawnLocation(hospital)` (vanilla path). Never on the configured-waypoint fallback, a bed/anchor respawn or with
  `Hospital.Enable` false. A jailed or handcuffed player whom cops-n-crooks then sends to jail keeps the few seconds (harmless).
  New `IMPL/listener/player/HospitalShieldListener.java` (`@ListenerHandler`, constructor `HospitalShield`; never also a `@Bean`):
  `onDamage(EntityDamageEvent)` at `EventPriority.LOWEST`, NOT ignoreCancelled: (1) when it is an `EntityDamageByEntityEvent`
  whose attacker is a Player (the damager, or a `Projectile` whose shooter is a Player) -> `shield.end(attacker)` (attacking any
  entity ends it, even an attack another plugin cancels); (2) then, when the victim is a Player with `isShielded` and the cause
  is not `VOID` -> `event.setCancelled(true)`. Cancelling at LOWEST keeps the hit away from `EntityDamageListener` (HIGH,
  ignoreCancelled: no crime, no self-defence first strike) and from the downed handler (`CustomPlayerDeathListener.onEntityDamage`,
  HIGHEST, ignoreCancelled). `onQuit(PlayerQuitEvent)` MONITOR -> `end`. No timer task: expiry is read on demand.
- Debug: `HOSPITAL {player} waypoint={name}`, `WARD_BILL {player} amount={x} at={DOWN|RESPAWN|QUIT|DEATH}` and
  `SHIELD {player} seconds={n}` (on grant).

## C14. Bribe stars (T13)
`CNC/wanted/bribe/BribeStars.java` (bean in `ChaseModuleConfig`, implements BeanLifecycle; a sync repeating task every 10 ticks):
for each `setupPoints.ofKind("pickup")` with a loaded chunk and no live item and respawn due -> drop a non-persistent item
(`XMaterial` from settings, pickup delay `Integer.MAX_VALUE`, no gravity, zero velocity, invulnerable, `setPersistent(false)`);
a wanted online player within `Pickup_Radius` of a live item and unseen (Ruling R31: skipped while `copManager.groupOf(id)` is
non-null and its squad's `millisSinceSighting() < Lost_Sight_Seconds*1000`; the item stays and the player gets `Bribe_Star_Seen`
from `wanted_messages.yml` at most once per 5 s) -> `wanted.setLevel(max(0, level - Stars), WantedCause.CONTACT)`,
remove the item, respawn at now + Respawn_Seconds, message `Bribe_Star_Taken` from `wanted_messages.yml`. An item that died
otherwise (despawn, chunk unload) is respawned without a timer. Shutdown removes live items.

## C15. Setup wand (T12)
`/glw cop setup wand|mode <station|district|hideout|pickup|restricted|breaker>|save <name...>|list [kind]|remove <kind> <id>|
tp <kind> <id>|link <stationId> <jailId|none>`; kinds `station|region|point`. Save writes ONLY through C5:
STATION `stations.create(name, pos1)` + `copSpawnManager.assignNearby(station, Station_Radius)`; DISTRICT/HIDEOUT/RESTRICTED/BREAKER
`regions.create(name, pos1, pos2, tag)`; BREAKER also `points.create("breaker_trigger", name, admin location)`;
PICKUP `points.create("pickup", name, pos1)`. Station save refuses (message, nothing stored) when `stations.create` returns null
(duplicate name). Station remove also calls `unassignStation`. `list [kind]` prints one line per row:
`<kind> <id> <name> <world> <x> <y> <z> [tags]` (regions print their min corner and tags; points their kind). `tp <kind> <id>`
teleports to the station anchor, the point, or the region's centre (top block + 1). `link <stationId> <jailId|none>` calls
`stations.linkJail(id, none ? null : jailId)`; an unknown station or jail id refuses. Outline: one sync repeating task every
`Interval_Ticks`; for each online player with the setup permission holding the wand and a selection, draw the cuboid's 12 edges
(one particle per block, at most 256 per player per draw) or a 1-block marker for a point mode; nothing for a player not holding
the wand; the task stops (cancelled) when no player holds the wand and restarts on the next wand click. Knobs and strings in new
`CNCR/copsncrooks/setup.yml` (`Setup.Wand.Item` "BLAZE_ROD", `Setup.Outline.Particle` "DUST", `Setup.Outline.Interval_Ticks` 10,
`Setup.Messages.*`).
