package org.luckyraven.gangland.weapon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.gangland.weapon.database.WeaponRepository;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.InOrder;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WeaponDataCleanupTask} — the {@link org.luckyraven.gangland.data.plugin.DataCleanupTask} the weapon module
 * registers for {@code PluginDataCleanupService}. Migrated from the deleted impl test
 * {@code PluginDataCleanupServiceTest.resetWeapons_nonWeaponRepositoryImplementation_skipsDeleteAll} (flip 4, T23):
 * {@code cleanup()} returns the pre-clear weapon count, calls {@code deleteAll()} on the repository before
 * {@code clear()} on the manager, and the {@code instanceof WeaponRepository} guard means a plain
 * {@link IRepository} double never throws a {@code ClassCastException}.
 */
@DisplayName("WeaponDataCleanupTask")
class WeaponDataCleanupTaskTest {

	@Test
	@DisplayName("cleanup() returns the pre-clear count, deletes every row, then clears the cache")
	void cleanup_deletesThenClears_returnsPreClearCount() {
		WeaponManager    weaponManager    = mock(WeaponManager.class);
		WeaponRepository weaponRepository = mock(WeaponRepository.class);
		when(weaponManager.getWeapons()).thenReturn(Map.of(UUID.randomUUID(), mock(Weapon.class),
		                                                    UUID.randomUUID(), mock(Weapon.class)));

		WeaponDataCleanupTask task = new WeaponDataCleanupTask(weaponManager, weaponRepository);

		int cleared = task.cleanup();

		assertEquals(2, cleared);
		InOrder inOrder = inOrder(weaponRepository, weaponManager);
		inOrder.verify(weaponRepository).deleteAll();
		inOrder.verify(weaponManager).clear();
	}

	@Test
	@DisplayName("name() identifies the task as 'weapons'")
	void name_isWeapons() {
		WeaponDataCleanupTask task = new WeaponDataCleanupTask(mock(WeaponManager.class), mock(WeaponRepository.class));

		assertEquals("weapons", task.name());
	}

	@Test
	@DisplayName("a plain IRepository<Weapon> (not a WeaponRepository) is skipped by the instanceof guard, but the cache still clears")
	void cleanup_nonWeaponRepositoryImplementation_skipsDeleteAll() {
		WeaponManager weaponManager = mock(WeaponManager.class);
		when(weaponManager.getWeapons()).thenReturn(Map.of());
		@SuppressWarnings("unchecked")
		IRepository<Weapon> genericRepository = mock(IRepository.class);

		WeaponDataCleanupTask task = new WeaponDataCleanupTask(weaponManager, genericRepository);

		assertDoesNotThrow(task::cleanup,
				"the `if (weaponRepository instanceof WeaponRepository repo)` guard is exactly what keeps this from " +
						"a ClassCastException against a plain IRepository<Weapon>");

		verify(weaponManager).clear();
	}
}
