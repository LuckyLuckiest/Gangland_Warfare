package org.luckyraven.gangland.turf.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.npc.config.TurfPowerupSettings;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link TurfPowerupManager#onChunkLoaded(World, int, int)} (moved into gangland-turf in group I; zero
 * coverage before this pin — E3 §6, T-N3): draining the pending-spawn queue removes only the entries whose spawn
 * anchor sits in the world/chunk Bukkit just loaded, leaving every other entry queued. {@code pending} has no
 * public accessor, so it is seeded and read back through reflection, matching {@code CaptureServiceHelpersTest}'s
 * house pattern (same module) for a private-only seam. The actual NPC spawn is out of scope — it needs a live
 * Citizens registry — so {@link CivilianSpawnManager#spawnCivilian} is stubbed to return {@code null}, which still
 * exercises every branch of the queue-draining loop under test.
 */
@DisplayName("TurfPowerupManager.onChunkLoaded")
class TurfPowerupManagerTest {

	@Test
	@DisplayName("drains only the pending entries whose spawn chunk matches the loaded world and chunk")
	void onChunkLoaded_drainsMatchingPendingEntries() throws Exception {
		JavaPlugin plugin = mock(JavaPlugin.class);
		@SuppressWarnings("unchecked")
		IRepository<TurfPowerupData> repository = mock(IRepository.class);
		TurfPowerupSettings          settings   = new TurfPowerupSettings("quartermaster", 32.0);
		CivilianSpawnManager         spawnManager = mock(CivilianSpawnManager.class);
		when(spawnManager.spawnCivilian(any(Location.class), anyString())).thenReturn(null);

		TurfPowerupManager manager = new TurfPowerupManager(plugin, repository, () -> settings, spawnManager);

		World loadedWorld = mock(World.class);
		World otherWorld  = mock(World.class);

		TurfPowerupData matchingChunk = new TurfPowerupData(1, new Location(loadedWorld, 20, 64, 20), null); // chunk (1,1)
		TurfPowerupData wrongChunk    = new TurfPowerupData(2, new Location(loadedWorld, 40, 64, 40), null); // chunk (2,2)
		TurfPowerupData wrongWorld    = new TurfPowerupData(3, new Location(otherWorld, 20, 64, 20), null);  // chunk (1,1), other world

		seedPending(manager, List.of(matchingChunk, wrongChunk, wrongWorld));

		manager.onChunkLoaded(loadedWorld, 1, 1);

		assertEquals(List.of(2, 3), turfIds(readPending(manager)),
				"the matching entry drains from the queue; the wrong-chunk and wrong-world entries stay queued");
	}

	@Test
	@DisplayName("remove drops the pending entry, so a later chunk load of its spawn chunk spawns nothing (TF-42)")
	void remove_purgesPendingEntry_soAChunkLoadSpawnsNothing() throws Exception {
		JavaPlugin plugin = mock(JavaPlugin.class);
		@SuppressWarnings("unchecked")
		IRepository<TurfPowerupData> repository = mock(IRepository.class);
		TurfPowerupSettings          settings   = new TurfPowerupSettings("quartermaster", 32.0);
		CivilianSpawnManager         spawnManager = mock(CivilianSpawnManager.class);

		TurfPowerupManager manager = new TurfPowerupManager(plugin, repository, () -> settings, spawnManager);

		World world = mock(World.class);
		seedPending(manager, List.of(new TurfPowerupData(4, new Location(world, 20, 64, 20), null)));

		manager.remove(4);

		assertEquals(List.of(), turfIds(readPending(manager)), "remove must drop the queued Quartermaster");
		manager.onChunkLoaded(world, 1, 1);
		verify(spawnManager, never()).spawnCivilian(any(Location.class), anyString());
	}

	@Test
	@DisplayName("remove deletes one row by turf id and never loads the whole table (TF-33)")
	void remove_neverLoadsTheWholeTable() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		@SuppressWarnings("unchecked")
		IRepository<TurfPowerupData> repository = mock(IRepository.class);
		TurfPowerupSettings          settings   = new TurfPowerupSettings("quartermaster", 32.0);
		CivilianSpawnManager         spawnManager = mock(CivilianSpawnManager.class);

		TurfPowerupManager manager = new TurfPowerupManager(plugin, repository, () -> settings, spawnManager);

		manager.remove(4);

		verify(repository, never()).loadAll();
		verify(repository).delete(argThat(data -> data.getTurfId() == 4));
	}

	@Test
	@DisplayName("W2-Q: place with no name stores the configured Quartermaster name (TF-38)")
	void place_nullName_usesConfiguredName() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		@SuppressWarnings("unchecked")
		IRepository<TurfPowerupData> repository = mock(IRepository.class);
		TurfPowerupSettings          settings   = new TurfPowerupSettings("quartermaster", 32.0, "Boss");
		CivilianSpawnManager         spawnManager = mock(CivilianSpawnManager.class);
		TurfPowerupManager manager = new TurfPowerupManager(plugin, repository, () -> settings, spawnManager);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.place(5, new Location(mock(World.class), 0, 64, 0), null);
		}

		verify(repository).save(argThat(data -> "Boss".equals(data.getDisplayName())));
	}

	@Test
	@DisplayName("T-834: under Hold a Quartermaster past Hold_Radius drops its target and walks home; inside it is left alone")
	void holdTick_leashesAHeldQuartermaster() throws Exception {
		TurfPowerupManager manager = new TurfPowerupManager(mock(JavaPlugin.class), mock(IRepository.class),
		                                                    () -> new TurfPowerupSettings("quartermaster", 32.0),
		                                                    mock(CivilianSpawnManager.class));
		World world = mock(World.class);
		Location anchor = new Location(world, 0, 64, 0);
		CivilianNpc far  = qmAt(manager, 1, anchor, new Location(world, 10, 64, 0));
		CivilianNpc near = qmAt(manager, 2, anchor, new Location(world, 4, 64, 0));
		CivilianNpc free = qmAt(manager, 3, anchor, new Location(world, 40, 64, 0));
		manager.bindHold(id -> id != 3, () -> 6.0);

		manager.holdTick();

		verify(far).setTargetPlayerId(null);
		verify(far).setTargetEntity(null);
		verify(far).transitionTo(CivilianState.IDLE);
		verify(far).navigateTo(anchor);
		verify(near, never()).setTargetPlayerId(any());
		verify(near, never()).navigateTo(any());
		verify(free, never()).navigateTo(any());
	}

	private static CivilianNpc qmAt(TurfPowerupManager manager, int turfId, Location anchor, Location at)
			throws Exception {
		CivilianNpc civilian = mock(CivilianNpc.class);
		LivingEntity body = mock(LivingEntity.class);
		when(body.getLocation()).thenReturn(at);
		when(body.getWorld()).thenReturn(anchor.getWorld());
		when(civilian.getEntity()).thenReturn(body);
		when(civilian.isValid()).thenReturn(true);
		when(civilian.getCurrentState()).thenReturn(CivilianState.COMBAT);
		TurfPowerupNpc qm = new TurfPowerupNpc(mock(JavaPlugin.class), new TurfPowerupData(turfId, anchor, null),
		                                       civilian);
		Field field = TurfPowerupManager.class.getDeclaredField("byTurfId");
		field.setAccessible(true);
		((Map<Integer, TurfPowerupNpc>) field.get(manager)).put(turfId, qm);
		return civilian;
	}

	@SuppressWarnings("unchecked")
	private static void seedPending(TurfPowerupManager manager, List<TurfPowerupData> seed) throws Exception {
		Field field = TurfPowerupManager.class.getDeclaredField("pending");
		field.setAccessible(true);
		((List<TurfPowerupData>) field.get(manager)).addAll(seed);
	}

	@SuppressWarnings("unchecked")
	private static List<TurfPowerupData> readPending(TurfPowerupManager manager) throws Exception {
		Field field = TurfPowerupManager.class.getDeclaredField("pending");
		field.setAccessible(true);
		return (List<TurfPowerupData>) field.get(manager);
	}

	private static List<Integer> turfIds(List<TurfPowerupData> data) {
		return data.stream().map(TurfPowerupData::getTurfId).toList();
	}
}
