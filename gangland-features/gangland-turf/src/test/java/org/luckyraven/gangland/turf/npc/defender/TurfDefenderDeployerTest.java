package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.events.TurfDefenderDownEvent;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.bukkit.event.Event;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link TurfDefenderDeployer#findOwningTurfId(Entity)} (moved into gangland-turf in group I; zero coverage
 * before this pin — E3 §6, T-N3): resolves the turf id whose defender group owns the given entity, and returns
 * {@code -1} both for an untracked entity and for {@code null}. {@code byTurfId} and the private {@code Group}/
 * {@code TrackedDefender} types have no public seam, so the fixture is assembled through reflection — same house
 * pattern as {@code TurfPowerupManagerTest} (this module) and {@code CaptureServiceHelpersTest}.
 */
@DisplayName("TurfDefenderDeployer.findOwningTurfId")
class TurfDefenderDeployerTest {

	@Test
	@DisplayName("resolves the turf id owning a tracked, valid defender's entity")
	void findOwningTurfId_matchesTrackedDefender() throws Exception {
		TurfDefenderDeployer deployer = newDeployer();

		LivingEntity entity = mock(LivingEntity.class);
		CivilianNpc  npc    = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		when(npc.getEntity()).thenReturn(entity);

		putDefender(deployer, 7, npc);

		assertEquals(7, deployer.findOwningTurfId(entity));
	}

	@Test
	@DisplayName("returns -1 for an entity that belongs to no tracked defender, and for null")
	void findOwningTurfId_untrackedEntityOrNull_returnsMinusOne() throws Exception {
		TurfDefenderDeployer deployer = newDeployer();

		LivingEntity tracked   = mock(LivingEntity.class);
		LivingEntity untracked = mock(LivingEntity.class);
		CivilianNpc  npc       = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		when(npc.getEntity()).thenReturn(tracked);

		putDefender(deployer, 3, npc);

		assertEquals(-1, deployer.findOwningTurfId(untracked));
		assertEquals(-1, deployer.findOwningTurfId(null));
	}

	@Test
	@DisplayName("aliveCount counts live guards per turf and drops one when a guard turns invalid")
	void aliveCount_isPerTurf() throws Exception {
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
	void deadDefender_firesDefenderDownOncePerTick() throws Exception {
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
	void despawnedDefender_firesNoDefenderDown() throws Exception {
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
	void lifespanSupplier_isReadEachTick() throws Exception {
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
	void expiredDefender_firesNothing() throws Exception {
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

	/**
	 * Builds one {@code Group} holding one {@code TrackedDefender} that expires at {@code Long.MAX_VALUE}, and installs it
	 * into the deployer's private {@code byTurfId} map under {@code turfId} - both nested types are private with no
	 * public constructor, so this goes through reflection rather than the {@link TurfDefenderDeployer#deploy} seam
	 * (which needs a live Citizens registry via {@code NpcSupport.available()}).
	 */
	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc) throws Exception {
		putDefender(deployer, turfId, npc, Long.MAX_VALUE);
	}

	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc, long expiresAt)
			throws Exception {
		putDefender(deployer, turfId, npc, expiresAt, () -> 0);
	}

	/** A guard deployed at {@code deployedAt} whose lifespan (seconds) is read from {@code lifespanSeconds}. */
	@SuppressWarnings("unchecked")
	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc, long deployedAt,
	                                IntSupplier lifespanSeconds) throws Exception {
		Class<?> groupClass           = Class.forName(TurfDefenderDeployer.class.getName() + "$Group");
		Class<?> trackedDefenderClass = Class.forName(TurfDefenderDeployer.class.getName() + "$TrackedDefender");

		Constructor<?> groupCtor = groupClass.getDeclaredConstructor(Supplier.class, double.class);
		groupCtor.setAccessible(true);
		Supplier<Set<UUID>> noChallengers = Set::of;
		Map<Integer, Object> byTurfId = fieldMap(deployer);
		Object group = byTurfId.get(turfId);
		if (group == null) {
			group = groupCtor.newInstance(noChallengers, 32.0);
			byTurfId.put(turfId, group);
		}

		Constructor<?> defenderCtor = trackedDefenderClass.getDeclaredConstructor(CivilianNpc.class, long.class,
		                                                                          IntSupplier.class);
		defenderCtor.setAccessible(true);
		Object trackedDefender = defenderCtor.newInstance(npc, deployedAt, lifespanSeconds);

		Field defendersField = groupClass.getDeclaredField("defenders");
		defendersField.setAccessible(true);
		((List<Object>) defendersField.get(group)).add(trackedDefender);

	}

	@SuppressWarnings("unchecked")
	private static Map<Integer, Object> fieldMap(TurfDefenderDeployer deployer) throws Exception {
		Field byTurfIdField = TurfDefenderDeployer.class.getDeclaredField("byTurfId");
		byTurfIdField.setAccessible(true);
		return (Map<Integer, Object>) byTurfIdField.get(deployer);
	}
}
