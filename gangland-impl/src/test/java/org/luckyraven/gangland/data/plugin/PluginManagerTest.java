package org.luckyraven.gangland.data.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginManager#initialize()} against a mocked {@link GanglandDatabase} repository chain. Pins Observation
 * #11 (core-lifecycle.md, Medium/Medium confidence) end to end: reloading a single existing {@code plugin_data} row
 * seeds the static id counter from {@code loadedRow.getId()} instead of {@code loadedRow.getId() + 1}, so the very
 * next freshly-created {@link PluginData} (e.g. from {@link PluginDataCleanupService}, if the list were ever empty
 * after a partial load) hands out the same id again.
 *
 * <p>{@code PluginData.ID} is a process-wide static counter; every test resets it to {@code 0} first.
 */
@DisplayName("PluginManager")
class PluginManagerTest {

	@BeforeEach
	void resetIdCounter() {
		PluginData.setID(0);
	}

	@SuppressWarnings("unchecked")
	private static IRepository<PluginData> repositoryReturning(GanglandDatabase database, List<PluginData> rows) {
		RepositoryRegistry       registry   = mock(RepositoryRegistry.class);
		IRepository<PluginData>  repository = mock(IRepository.class);

		when(database.getRepositoryRegistry()).thenReturn(registry);
		when(registry.getRepository(PluginData.class)).thenReturn(repository);
		when(repository.loadAll()).thenReturn(rows);

		return repository;
	}

	@Test
	@DisplayName("an empty database seeds exactly one fresh PluginData and wires the data supplier")
	void initialize_emptyDatabase_seedsOneRow() {
		GanglandDatabase        database   = mock(GanglandDatabase.class);
		IRepository<PluginData> repository = repositoryReturning(database, List.of());

		PluginManager manager = new PluginManager(database);
		manager.initialize();

		assertEquals(1, manager.getPluginDataList().size());
		verify(repository).setDataSupplier(any());
	}

	@Test
	@DisplayName("a single loaded row is kept as-is and the id counter is seeded from it, not past it")
	void initialize_singleExistingRow_keepsRowAndSeedsCounter() {
		GanglandDatabase database = mock(GanglandDatabase.class);
		PluginData       existing = new PluginData(5, 0L, 0L, 0L);
		repositoryReturning(database, List.of(existing));

		PluginManager manager = new PluginManager(database);
		manager.initialize();

		assertEquals(1, manager.getPluginDataList().size());
		assertSame(existing, manager.getPluginDataList().get(0));
	}

	@Test
	@DisplayName("Observation #11 (core-lifecycle.md): after reloading one existing row, the next created " +
			"PluginData reuses its id instead of getting a fresh one")
	void initialize_singleExistingRow_idIsReusedByNextCreatedPluginData() {
		GanglandDatabase database = mock(GanglandDatabase.class);
		PluginData       existing = new PluginData(5, 0L, 0L, 0L);
		repositoryReturning(database, List.of(existing));

		PluginManager manager = new PluginManager(database);
		manager.initialize();

		PluginData nextCreated = PluginData.createInitial(30);

		assertEquals(5, nextCreated.getId(),
				"current behaviour: PluginManager.initialize() calls PluginData.setID(loadedRow.getId()) for " +
						"the loaded row instead of loadedRow.getId() + 1, so the id counter reuses rather than " +
						"advances past an already-assigned id");
	}

	@Test
	@DisplayName("clear() empties the in-memory list without touching the database")
	void clear_emptiesList() {
		GanglandDatabase database = mock(GanglandDatabase.class);
		repositoryReturning(database, List.of(new PluginData(1, 0L, 0L, 0L)));

		PluginManager manager = new PluginManager(database);
		manager.initialize();
		manager.clear();

		assertEquals(0, manager.getPluginDataList().size());
	}
}
