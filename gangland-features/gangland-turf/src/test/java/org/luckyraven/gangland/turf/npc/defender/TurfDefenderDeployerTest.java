package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.events.TurfDefenderDownEvent;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.bukkit.event.Event;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link TurfDefenderDeployer#findOwningTurfId(Entity)} (moved into gangland-turf in group I; zero coverage
 * before this pin — E3 §6, T-N3): resolves the turf id whose defender group owns the given entity, and returns
 * {@code -1} both for an untracked entity and for {@code null}. {@code byTurfId} and the private {@code Group}/
 * {@code TrackedDefender} types have no public seam, so the fixture is assembled through reflection — same house
 * pattern as {@code TurfPowerupManagerTest} (this module) and {@code CaptureServiceHelpersTest}.
 */
@DisplayName("TurfDefenderDeployer")
class TurfDefenderDeployerTest {

	@Test
	@DisplayName("findOwningTurfId_isAnIndexLookup: the lookup goes by entity UUID, so a second entity object with the same UUID resolves")
	void findOwningTurfId_isAnIndexLookup() {
		TurfDefenderDeployer deployer = newDeployer();
		UUID id = UUID.randomUUID();
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getUniqueId()).thenReturn(id);
		CivilianNpc npc = liveNpc();
		when(npc.getEntity()).thenReturn(entity);
		putDefender(deployer, 4, npc);

		LivingEntity sameUuid = mock(LivingEntity.class);
		when(sameUuid.getUniqueId()).thenReturn(id);

		assertEquals(4, deployer.findOwningTurfId(sameUuid));
	}

	@Test
	@DisplayName("markKilled_byUuid: a guard tracked by entity UUID counts as down once it is gone, an untracked NPC does not")
	void markKilled_byUuid() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc dying = liveNpc();
		CivilianNpc stranger = liveNpc();
		putDefender(deployer, 1, dying);
		putDefender(deployer, 1, liveNpc());
		when(dying.isValid()).thenReturn(false);
		deployer.markKilled(dying);
		deployer.markKilled(stranger);
		when(stranger.isValid()).thenReturn(false);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), org.mockito.Mockito.times(1)).callEvent(captor.capture());
			assertEquals(1, ((TurfDefenderDownEvent) captor.getValue()).remaining());
		}
	}

	@Test
	@DisplayName("noCitizens_consumesNothing: TF-46, a garrison start without Citizens spawns and spends nothing")
	void noCitizens_consumesNothing() {
		CivilianSpawnManager spawnManager = mock(CivilianSpawnManager.class);
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				spawnManager);
		deployer.setCitizensAvailable(() -> false);
		AtomicInteger consumed = new AtomicInteger();
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 5, n -> {
			consumed.addAndGet(n);
			return n;
		}, n -> {
		});

		deployer.startGarrison(garrison, Set::of, () -> 600);
		deployer.tick();

		assertEquals(0, consumed.get());
		assertEquals(0, deployer.aliveCount(1));
		verifyNoInteractions(spawnManager);
	}

	@Test
	@DisplayName("consume after spawn: the reserve pays one per guard that spawned, a failed spawn costs nothing")
	void wave_consumesOnlyAfterSpawn() {
		CivilianSpawnManager spawnManager = mock(CivilianSpawnManager.class);
		CivilianNpc first  = liveNpc();
		CivilianNpc second = liveNpc();
		when(spawnManager.spawnCivilian(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("turf_defender")))
				.thenReturn(first, second, null);
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				spawnManager);
		deployer.setCitizensAvailable(() -> true);
		AtomicInteger stock  = new AtomicInteger(10);
		AtomicInteger notice = new AtomicInteger();
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", stock::get,
				n -> stock.addAndGet(-n) + n, notice::addAndGet);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			World world = mock(World.class);
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

			deployer.startGarrison(garrison, Set::of, () -> 600);
			assertEquals(10, stock.get(), "arming the garrison spends nothing");
			deployer.tick();
		}

		// the wave asked for 3: two spawned, the third came back null
		assertEquals(8, stock.get());
		assertEquals(2, notice.get());
		assertEquals(2, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("a challenger far outside the leash is ignored: no ray is traced for it")
	void farChallenger_causesNoRayTrace() {
		CivilianSpawnManager spawnManager = mock(CivilianSpawnManager.class);
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				spawnManager);
		deployer.setCitizensAvailable(() -> true);
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 5, n -> n, n -> {
		});
		UUID id = UUID.randomUUID();
		org.bukkit.entity.Player far = mock(org.bukkit.entity.Player.class);
		when(far.getUniqueId()).thenReturn(id);
		when(far.getEyeLocation()).thenReturn(new org.bukkit.Location(null, 2000, 70, 2000));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			World world = mock(World.class);
			when(world.getPlayers()).thenReturn(java.util.List.of(far));
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

			deployer.startGarrison(garrison, () -> Set.of(id), () -> 600);
			deployer.tick();

			verify(world, org.mockito.Mockito.never()).rayTraceBlocks(org.mockito.ArgumentMatchers.any(org.bukkit.Location.class),
					org.mockito.ArgumentMatchers.any(org.bukkit.util.Vector.class), org.mockito.ArgumentMatchers.anyDouble(),
					org.mockito.ArgumentMatchers.any(org.bukkit.FluidCollisionMode.class), org.mockito.ArgumentMatchers.anyBoolean());
		}
	}

	@Test
	@DisplayName("T-751: standDown while a rival is still on the turf keeps the guards, and the next contest re-arms the same group")
	void standDown_rivalStillOnTurf_keepsGuards() {
		TurfDefenderDeployer deployer = newDeployer();
		deployer.setCitizensAvailable(() -> true);
		UUID id = UUID.randomUUID();
		org.bukkit.entity.Player rival = mock(org.bukkit.entity.Player.class);
		when(rival.isOnline()).thenReturn(true);
		when(rival.getLocation()).thenReturn(new org.bukkit.Location(null, 5, 70, 5));
		CivilianNpc guard = liveNpc();
		AtomicInteger returned = new AtomicInteger(-1);
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 0, n -> n, n -> {
		});

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(rival);
			deployer.startGarrison(garrison, () -> Set.of(id), () -> 600);
			deployer.track(1, guard, System.currentTimeMillis(), () -> 600);

			deployer.standDown(1, true, returned::set);
			assertEquals(false, garrison.standingDown, "a rival is on the turf: no stand-down");
			assertEquals(-1, returned.get());

			deployer.startGarrison(new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
					new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 0, n -> n, n -> {
			}), () -> Set.of(id), () -> 600);
			assertEquals(1, deployer.aliveCount(1), "the restarted contest keeps the standing guard");
			verify(guard, org.mockito.Mockito.never()).markForRemoval();
		}
	}

	@Test
	@DisplayName("T-751: once the rival has left, standDown stands the guards down and hands the survivors back")
	void standDown_rivalGone_standsDown() {
		TurfDefenderDeployer deployer = newDeployer();
		deployer.setCitizensAvailable(() -> true);
		UUID id = UUID.randomUUID();
		CivilianNpc guard = liveNpc();
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 0, n -> n, n -> {
		});

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(null);
			deployer.startGarrison(garrison, () -> Set.of(id), () -> 600);
			deployer.track(1, guard, System.currentTimeMillis(), () -> 600);

			deployer.standDown(1, true, n -> {
			});
			assertEquals(true, garrison.standingDown);
		}
	}

	@Test
	@DisplayName("resolves the turf id owning a tracked, valid defender's entity")
	void findOwningTurfId_matchesTrackedDefender() {
		TurfDefenderDeployer deployer = newDeployer();

		LivingEntity entity = mock(LivingEntity.class);
		CivilianNpc  npc    = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		when(npc.getEntity()).thenReturn(entity);
		when(entity.getUniqueId()).thenReturn(UUID.randomUUID());

		putDefender(deployer, 7, npc);

		assertEquals(7, deployer.findOwningTurfId(entity));
	}

	@Test
	@DisplayName("returns -1 for an entity that belongs to no tracked defender, and for null")
	void findOwningTurfId_untrackedEntityOrNull_returnsMinusOne() {
		TurfDefenderDeployer deployer = newDeployer();

		LivingEntity tracked   = mock(LivingEntity.class);
		LivingEntity untracked = mock(LivingEntity.class);
		CivilianNpc  npc       = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		when(npc.getEntity()).thenReturn(tracked);
		when(tracked.getUniqueId()).thenReturn(UUID.randomUUID());
		when(untracked.getUniqueId()).thenReturn(UUID.randomUUID());

		putDefender(deployer, 3, npc);

		assertEquals(-1, deployer.findOwningTurfId(untracked));
		assertEquals(-1, deployer.findOwningTurfId(null));
	}

	@Test
	@DisplayName("aliveCount counts live guards per turf and drops one when a guard turns invalid")
	void aliveCount_isPerTurf() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc a = liveNpc();
		CivilianNpc b = liveNpc();
		CivilianNpc c = liveNpc();
		putDefender(deployer, 1, a);
		putDefender(deployer, 1, b);
		putDefender(deployer, 2, c);

		assertEquals(2, deployer.aliveCount(1));
		assertEquals(1, deployer.aliveCount(2));
		assertEquals(0, deployer.aliveCount(3));

		when(a.isValid()).thenReturn(false);

		assertEquals(1, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("a guard that dies fires one TurfDefenderDownEvent per tick with the guards left")
	void deadDefender_firesDefenderDownOncePerTick() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc alive = liveNpc();
		CivilianNpc dying = liveNpc();
		putDefender(deployer, 1, alive);
		putDefender(deployer, 1, dying);
		when(dying.isValid()).thenReturn(false);
		deployer.markKilled(dying);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), org.mockito.Mockito.times(1)).callEvent(captor.capture());
			assertInstanceOf(TurfDefenderDownEvent.class, captor.getValue());
			TurfDefenderDownEvent down = (TurfDefenderDownEvent) captor.getValue();
			assertEquals(1, down.turfId());
			assertEquals(1, down.remaining());
		}
	}

	@Test
	@DisplayName("T-251: a guard that vanished without dying (despawned or unloaded) fires no TurfDefenderDownEvent")
	void despawnedDefender_firesNoDefenderDown() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc alive     = liveNpc();
		CivilianNpc despawned = liveNpc();
		putDefender(deployer, 1, alive);
		putDefender(deployer, 1, despawned);
		when(despawned.isValid()).thenReturn(false);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			verify(bukkit.pluginManager(), org.mockito.Mockito.never()).callEvent(org.mockito.ArgumentMatchers.any(Event.class));
		}
		assertEquals(1, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("T-269: the lifespan is read on every tick, so a reload extends guards already in the field")
	void lifespanSupplier_isReadEachTick() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc guard = liveNpc();
		AtomicInteger seconds = new AtomicInteger(0);
		putDefender(deployer, 1, guard, System.currentTimeMillis(), seconds::get);

		seconds.set(600);
		deployer.tick();

		assertEquals(1, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("a guard removed because its lifespan ran out fires no TurfDefenderDownEvent")
	void expiredDefender_firesNothing() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc expired = liveNpc();
		putDefender(deployer, 1, expired, System.currentTimeMillis() - 1_000L, () -> 0);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			verify(bukkit.pluginManager(), org.mockito.Mockito.never()).callEvent(org.mockito.ArgumentMatchers.any(Event.class));
		}
	}

	private static CivilianNpc liveNpc() {
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		return npc;
	}

	private static TurfDefenderDeployer newDeployer() {
		return new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				mock(CivilianSpawnManager.class));
	}

	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc) {
		deployer.track(turfId, npc, System.currentTimeMillis(), () -> 1_000_000);
	}

	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc, long deployedAt,
	                                IntSupplier lifespanSeconds) {
		deployer.track(turfId, npc, deployedAt, lifespanSeconds);
	}
}
