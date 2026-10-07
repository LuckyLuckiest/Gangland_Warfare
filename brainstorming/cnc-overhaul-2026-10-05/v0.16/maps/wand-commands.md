# Wand Commands Map — 0.16

Admin setup wand for placing stations, districts, hideouts, pickup points, restricted zones, and breaker structures. Writes to registries through repositories.

## Existing Admin Wand Pattern (Turf)

**WandSelectionManager** — `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/selection/WandSelectionManager.java`
- L13-27: Holds per-admin in-memory selections (UUID → Selection map, ConcurrentHashMap)
- `WAND_NBT_KEY = "gangturf_wand"` (L15)
- `ADMIN_PERMISSION = "gangland.turf.admin"` (L16)
- Methods: `get(Player)` (L20-22) computes-if-absent, `clear(UUID)` (L24-26)

**Selection** — `gangland-turf/src/main/java/org/luckyraven/gangland/turf/selection/Selection.java`
- L13-47: Per-admin selection state (pos1/pos2, world, activeTurfId)
- `set(Location, boolean)` (L29-46): Sets pos1 (first=true) or pos2; resets on world mismatch; clears activeTurfId on corner change
- `isComplete()` (L25-27): Both positions set

**WandListener** — `gangland-turf/src/main/java/org/luckyraven/gangland/turf/listener/WandListener.java`
- L20-63: Listens to PlayerInteractEvent; detects wand via NBT tag
- L25-42: Ignores non-block clicks, missing items, lacks `ADMIN_PERMISSION`
- L45-52: LEFT_CLICK_BLOCK → pos1; RIGHT_CLICK_BLOCK → pos2; sends feedback to player
- L56-58: PlayerQuitEvent cleanup via `selections.clear()`

## Particle & Zone Ring Code

**WantedHud.drawRing()** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/wanted/hud/WantedHud.java`
- L184-204: Draws circular ring of particles (XParticle.DustOptions, Color.RED)
- L195: `Math.max(1, hud.zonePoints())` for point count
- L197-203: Polar coordinates loop; each i → angle → location → spawnForced()
- L207-218: `spawnForced(Player, Particle, Location, data)` — tries forced send (1.17+), falls back to unforced
- L220-227: `forcedParticle()` reflexively loads `Player.spawnParticle(Particle, Location, int, double, double, double, double, Object, boolean)` or null
- L229-238: `resolveParticle(String)` via XParticle.valueOf; falls back to DUST

**HudSettings** — configures particle: `zoneRing()`, `zoneParticle()` (string name), `zonePoints()` (int count)

## Command Structure

**CopCommand** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/command/cops/CopCommand.java`
- L17-59: Root `/glw cop` command (@CommandHandler)
- L43-52: `initializeArguments()` adds CopSpawnerCommand and CopListCommand as sub-arguments
- Pattern: extends Command, constructs sub-commands in initializeArguments(), registers via `getArgument().addAllSubArguments()`

**CopSpawnerCommand** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/command/cops/spawner/CopSpawnerCommand.java`
- L16-22: Root `/glw cop spawner` command; routes to set/remove/list/info/tp sub-commands
- Pattern: extends Argument, initializes sub-Argument nodes in constructor

**OptionalArgument** usage — e.g. CopSpawnerInfoCommand L28-34: chains OptionalArgument for ID param with tab-completion via `DependencyProvider.of(...)` or hardcoded supplier

## Cop Spawner Infrastructure

**CopSpawner** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/police/spawn/CopSpawner.java`
- L6-11: extends EntitySpawnerPoint (Keystone); holds id, location only
- Persisted via IRepository<CopSpawner>; auto-discovered in module config phase

**CopSpawnManager** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/npc/police/spawn/CopSpawnManager.java`
- L23: extends EntitySpawner<CopSpawner> (Keystone base)
- L38-52: Constructor wires JavaPlugin, CopLoader, CopConfigProvider, CopNpcFactory, behavior factories
- L78-91: `spawnNearPlayer(Player, int tier, Predicate<Location> allowed, CopRole role)` — finds closest spawner via Keystone, else ring
- L99-109: `findRingLocation(Player)` — ring spawn logic; retries outdoor/any-roof fallback
- L138-142: `getTargetCopCount(int wantedLevel)` → cops.yml Cops.Count
- L151-153: `getTierForWantedLevel(int wantedLevel)` → cops.yml Cops.Tiers (min(level, maxTier))

## Writing Through Repository

**Pattern** (from turf, gang, mail): write via manager, manager calls repository.
- Turf: TurfManager.createTurf() → TurfRepository.save(Turf)
- Gang: GangManager.create() → GangRepository.save(Gang)
- Spoof: CopSpawner repository auto-discovered; command calls manager → manager calls repo.save(spawner)

**IRepository<T>** (Keystone):
- CRUD: save(), saveAll(), delete(), loadAll()
- Routes through DatabaseBackend (upsert, delete)
- Must wire `setDataSupplier()` in manager initialize()

## Command Tree & Permissions

**Permission prefix** — `gangland.cops` (module-specific; CLAUDE.md "Module API rule")
- Admin wand: `gangland.cops.admin.setup` (or similar for wand modes)
- Station list/remove/tp: `gangland.cops.admin.stations`
- Sub-commands inherit parent permission check via Command framework

**commands.json** — Module descriptor at `copsncrooks/` folder (0.15.1 YAML move):
- Location: `gangland-features/cops-n-crooks/src/main/resources/copsncrooks/commands.json`
- Entries: cop (root), cop.spawner, cop.list, etc.; new: cop.setup and setup sub-modes

**CommandContribution** (for cross-module extensions):
- `gangland-api/src/main/java/org/luckyraven/gangland/command/extension/CommandContribution.java` L18-30
- Used by turf (TurfCommand L56-70) to collect runtime module contributions
- Pattern: @Bean CommandContribution → parent().create() → return Argument
- Not needed for cops-n-crooks 0.16 setup wand (internal to cops module)

## Tab-Completion Pattern

**OptionalArgument** with DependencyProvider (Keystone):
- Example: CopSpawnerInfoCommand L28-34 chains OptionalArgument(plugin, tree, executor)
- Executor: (argument, sender, args) → calls command logic
- Supplier: CopSpawnerListCommand provides ID list via repository.loadAll() → map ID → String
- No inline lambda; use static supplier or manager method returning List<String>

## Existing Tests

**WantedHudTest** — `gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/wanted/hud/WantedHudTest.java`
- L195-202: `searching_spawnsZoneRingPointsForThatPlayerOnly()` — asserts ring visible only to player
- Tests zone ring rendering; no particle internals tested (implementation detail)

**CopSpawnerCommandTests** — none found; spawner management tested indirectly via CopSpawnManagerFallbackTest
- CopSpawnManagerFallbackTest L42-145: tests ring fallback logic, outdoor detection, anyRoof flag

**TurfWandListener tests** — none found; wand is tested only as "setpos1/pos2 mechanics work"

## Seams

1. **WandSelectionManager** — stateful; must clear on quit to avoid memory leak (done by WandListener)
2. **Zone ring particle** — uses reflection for forced send (1.17+); graceful fallback to unforced
3. **CopSpawnManager** — relies on Keystone EntitySpawner; findClosestSpawnerLocation() + ring are Keystone methods
4. **Repository** — auto-discovered CopSpawner repo; manager must wire setDataSupplier() or autosave breaks
5. **Permission gate** — WandListener checks permission inline; command framework checks parent route
6. **Parser/Supplier gap** — command argument ID supplier must match repository IDs; no cross-repo dedup

## Spec Drift

- **Wand modes listed**: station, district corners, hideout, pickup point, restricted zone, breaker structure + trigger. 0.16 spec does not name the wand item itself (e.g., `<wand.item>` config key) or how admin equips it.
- **Station registry does not exist yet** — wand write paths (station.save, district.save, etc.) need both registry data class and repository; spec names "station registry" but no detail on schema (district id? name? spawner ids list? jail link?).
- **Place names / RegionProvider** — spec names PlaceNames.locate(Location) and RegionProvider as 0.16 additions (api 2.3) but wand code needs neither yet (0.16 wand stores locations and mode tags, not place lookups).
- **Hideout/pickup/restricted/breaker modes** — spec titles these but 0.16 shipping status is "Hideouts & cooldown" (2.4), "Crooked contacts bribe stars" (2.5), "Restricted zone" (trespass, 0.17), "Breaker structure" (0.19). Wand may ship modes as stored-only placeholders.

