package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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

	private static TurfDefenderDeployer newDeployer() {
		return new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				mock(CivilianSpawnManager.class));
	}

	/**
	 * Builds one {@code Group} holding one {@code TrackedDefender(npc, Long.MAX_VALUE)} and installs it into the
	 * deployer's private {@code byTurfId} map under {@code turfId} — both nested types are private with no public
	 * constructor, so this goes through reflection rather than the {@link TurfDefenderDeployer#deploy} seam (which
	 * needs a live Citizens registry via {@code NpcSupport.available()}).
	 */
	@SuppressWarnings("unchecked")
	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc) throws Exception {
		Class<?> groupClass           = Class.forName(TurfDefenderDeployer.class.getName() + "$Group");
		Class<?> trackedDefenderClass = Class.forName(TurfDefenderDeployer.class.getName() + "$TrackedDefender");

		Constructor<?> groupCtor = groupClass.getDeclaredConstructor(Supplier.class, double.class);
		groupCtor.setAccessible(true);
		Supplier<Set<UUID>> noChallengers = Set::of;
		Object group = groupCtor.newInstance(noChallengers, 32.0);

		Constructor<?> defenderCtor = trackedDefenderClass.getDeclaredConstructor(CivilianNpc.class, long.class);
		defenderCtor.setAccessible(true);
		Object trackedDefender = defenderCtor.newInstance(npc, Long.MAX_VALUE);

		Field defendersField = groupClass.getDeclaredField("defenders");
		defendersField.setAccessible(true);
		((List<Object>) defendersField.get(group)).add(trackedDefender);

		Field byTurfIdField = TurfDefenderDeployer.class.getDeclaredField("byTurfId");
		byTurfIdField.setAccessible(true);
		((Map<Integer, Object>) byTurfIdField.get(deployer)).put(turfId, group);
	}
}
