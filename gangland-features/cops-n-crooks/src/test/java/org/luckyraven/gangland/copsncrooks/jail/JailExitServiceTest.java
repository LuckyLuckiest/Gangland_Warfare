package org.luckyraven.gangland.copsncrooks.jail;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Proves {@link JailExitService} keeps the in-memory {@link JailExitRegistry} and the {@code jail_exit} table
 * (represented here by a {@link FakeRepository}) in sync: {@code snapshotAll()} (the repository data-supplier)
 * folds the registry's SPECIFIC rows plus a synthesised GLOBAL row into one collection — the {@code row_id = -1}
 * convention documented on {@link JailExit} — and {@code onInitialize} replays a loaded row set back into the
 * registry, restoring both scopes correctly by {@link JailExit#isGlobal()}.
 */
@DisplayName("JailExitService — registry/repository round trip")
class JailExitServiceTest {

	private JailExitRegistry        registry;
	private FakeRepository<JailExit> repository;
	private JailExitService         service;
	private World                    world;

	@BeforeEach
	void setUp() {
		registry   = new JailExitRegistry();
		repository = new FakeRepository<>();
		world      = mock(World.class);
		service    = new JailExitService(registry, repository);
	}

	private Location loc(double x) {
		return new Location(world, x, 64, 0);
	}

	@Test
	@DisplayName("constructing the service wires setDataSupplier so autosave can pull the current registry state")
	void constructor_wiresDataSupplier() {
		registry.setExit(1, loc(10));
		registry.setGlobalExit(loc(500));

		repository.saveAllFromMemory();

		assertEquals(2, repository.saved.size(), "the supplier must be reachable via saveAllFromMemory");
	}

	@Test
	@DisplayName("setExit persists a SPECIFIC row through the repository")
	void setExit_persistsThroughRepository() {
		service.setExit(1, loc(10));

		assertEquals(1, repository.saved.size());
		JailExit saved = repository.saved.get(0);
		assertTrue(!saved.isGlobal());
		assertEquals(1, saved.getJailId());
	}

	@Test
	@DisplayName("setGlobalExit persists a GLOBAL row through the repository")
	void setGlobalExit_persistsThroughRepository() {
		service.setGlobalExit(loc(500));

		assertEquals(1, repository.saved.size());
		assertTrue(repository.saved.get(0).isGlobal());
	}

	@Test
	@DisplayName("removeExit clears the registry entry and calls delete through the repository")
	void removeExit_clearsRegistryAndDeletes() {
		service.setExit(1, loc(10));

		service.removeExit(1);

		assertEquals(null, registry.getExit(1));
		assertEquals(1, repository.deleted.size());
		assertEquals(1, repository.deleted.get(0).getJailId());
	}

	@Test
	@DisplayName("removeGlobalExit clears the registry's global exit and calls delete through the repository")
	void removeGlobalExit_clearsRegistryAndDeletes() {
		service.setGlobalExit(loc(500));

		service.removeGlobalExit();

		assertEquals(null, registry.getGlobalExit());
		assertEquals(1, repository.deleted.size());
		assertTrue(repository.deleted.get(0).isGlobal());
	}

	@Test
	@DisplayName("snapshotAll (via saveAllFromMemory) includes every SPECIFIC row plus one synthesised GLOBAL row")
	void snapshotAll_foldsSpecificAndGlobalIntoOneCollection() {
		registry.setExit(1, loc(10));
		registry.setExit(2, loc(20));
		registry.setGlobalExit(loc(500));

		repository.saveAllFromMemory();

		assertEquals(3, repository.saved.size());
		long globalCount = repository.saved.stream().filter(JailExit::isGlobal).count();
		assertEquals(1, globalCount, "exactly one GLOBAL row, regardless of how many SPECIFIC rows exist");
	}

	@Test
	@DisplayName("snapshotAll omits the GLOBAL row entirely when no global exit is configured")
	void snapshotAll_noGlobalExit_omitsGlobalRow() {
		registry.setExit(1, loc(10));

		repository.saveAllFromMemory();

		assertEquals(1, repository.saved.size());
		assertTrue(!repository.saved.get(0).isGlobal());
	}

	@Test
	@DisplayName("onInitialize restores both GLOBAL and SPECIFIC rows from loaded data into the registry")
	void onInitialize_restoresBothScopesFromLoadedRows() {
		Collection<JailExit> loaded = List.of(
				JailExit.forJail(1, loc(10)),
				JailExit.global(loc(500))
		);
		repository.seed(loaded);

		service.onInitialize(true);

		assertEquals(10, registry.getExit(1).getX());
		assertEquals(500, registry.getGlobalExit().getX());
	}

	@Test
	@DisplayName("onInitialize skips rows whose location is null (e.g. an unloaded world at load time)")
	void onInitialize_skipsNullLocationRows() {
		JailExit nullLocationRow = JailExit.forJail(2, null);
		repository.seed(List.of(nullLocationRow));

		service.onInitialize(true);

		assertEquals(null, registry.getExit(2));
	}

}
