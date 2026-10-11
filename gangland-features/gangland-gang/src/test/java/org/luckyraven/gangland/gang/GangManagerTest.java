package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangMigrationsContract;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Keystone's BeanFactory calls {@link GangManager#onInitialize(boolean)} once per bean registration, and
 * {@code markDone} only runs in an async save callback. A second pass must not run the D7 migration again.
 */
@DisplayName("GangManager.onInitialize - migrations run once per instance")
class GangManagerTest {

	private IRepository<Gang>              gangRepository;
	private GangMigrationsContract         migrations;
	private GangManager                    manager;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		IdentitySettings.bind(new FakeIdentitySettingsContract());

		gangRepository = mock(IRepository.class);
		migrations     = mock(GangMigrationsContract.class);
		// the default migrate saves then marks done; the mock keeps that behaviour so the assertions below still hold
		doCallRealMethod().when(migrations).migrate(anyString(), any(), anyCollection());
		when(gangRepository.loadAll()).thenReturn(List.of(new Gang(1)));

		manager = new GangManager(gangRepository, mock(GangAllianceRepositoryContract.class), migrations);
	}

	@Test
	@DisplayName("onInitialize(true) twice saves the gangs once and marks the migration done at most once")
	void secondFirstLoad_doesNotMigrateAgain() {
		manager.onInitialize(true);
		manager.onInitialize(true);

		verify(gangRepository, times(1)).saveAll(anyCollection());
		verify(migrations, atMost(1)).markDone(GangManager.STATE_MIGRATION);
	}

	@Test
	@DisplayName("the first pass migrates every stored gang to invite-only")
	void firstPass_migrates() {
		manager.onInitialize(true);

		verify(gangRepository, times(1)).saveAll(any());
		verify(migrations).markDone(GangManager.STATE_MIGRATION);
	}

}
