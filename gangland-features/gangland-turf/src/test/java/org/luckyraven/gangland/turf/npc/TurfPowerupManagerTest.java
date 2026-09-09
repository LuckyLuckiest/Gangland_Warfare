package org.luckyraven.gangland.turf.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.npc.config.TurfPowerupSettings;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
		TurfPowerupSettings          settings   = new TurfPowerupSettings("quartermaster");
		CivilianSpawnManager         spawnManager = mock(CivilianSpawnManager.class);
		when(spawnManager.spawnCivilian(any(Location.class), anyString())).thenReturn(null);

		TurfPowerupManager manager = new TurfPowerupManager(plugin, repository, settings, spawnManager);

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
