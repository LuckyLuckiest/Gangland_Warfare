package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.LoadOutcome;
import org.luckyraven.gangland.gang.contract.GangMigrationsContract;
import org.luckyraven.gangland.gang.contract.MemberRepositoryContract;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The 0.17.0 one-shot data migrations: every stored gang becomes invite-only once (D7), every stored contribution
 * resets to 0 once (D15). Each runs on initialize, is recorded through {@link GangMigrationsContract}, and a second
 * initialize leaves later data alone.
 */
@DisplayName("Gang data migrations - D7 INVITE default and D15 contribution reset run once")
class GangDataMigrationsTest {

	/** In-memory record of which migrations ran, standing in for the gang_migration table. */
	static final class FakeMigrations implements GangMigrationsContract {

		private final Set<String> done = new HashSet<>();

		@Override
		public boolean isDone(String id) {
			return done.contains(id);
		}

		@Override
		public void markDone(String id) {
			done.add(id);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("T-303: the state migration hands the retry a live view, so a disbanded gang is not written back")
	void stateMigration_retryRowsAreLive() {
		Gang kept = new Gang(1);
		Gang disbanded = new Gang(2);
		IRepository<Gang> repository = mock(IRepository.class);
		when(repository.loadAll()).thenReturn(List.of(kept, disbanded));
		java.util.concurrent.atomic.AtomicReference<java.util.function.Supplier<java.util.Collection<Gang>>> captured =
				new java.util.concurrent.atomic.AtomicReference<>();
		GangMigrationsContract migrations = new GangMigrationsContract() {
			@Override
			public boolean isDone(String id) {
				return false;
			}

			@Override
			public void markDone(String id) {
			}

			@Override
			public <T> void migrate(String id, IRepository<T> repo, java.util.function.Supplier<java.util.Collection<T>> rows) {
				captured.set((java.util.function.Supplier) rows);
			}
		};
		GangManager manager = new GangManager(repository, mock(GangAllianceRepositoryContract.class), migrations);
		manager.initialize();

		manager.remove(disbanded);

		assertEquals(List.of(kept), List.copyOf(captured.get().get()));
	}

	@BeforeEach
	void bindIdentitySettings() {
		// new Gang(id) reads IdentitySettings (bounty defaults); bound here because no bootstrap runs in this test.
		IdentitySettings.bind(new FakeIdentitySettingsContract());
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("stateMigration_runsOnce: OPEN and CLOSE gangs become INVITE once; a later OPEN stays OPEN")
	void stateMigration_runsOnce() {
		Gang open = new Gang(1);
		open.setState(Gang.State.OPEN);
		Gang close = new Gang(2);
		close.setState(Gang.State.CLOSE);
		IRepository<Gang> repository = mock(IRepository.class);
		when(repository.loadAll()).thenReturn(List.of(open, close));
		FakeMigrations migrations = new FakeMigrations();

		new GangManager(repository, mock(GangAllianceRepositoryContract.class), migrations).initialize();

		assertEquals(Gang.State.INVITE, open.getState());
		assertEquals(Gang.State.INVITE, close.getState());
		verify(repository, times(1)).saveAll(anyCollection());
		assertTrue(migrations.isDone(GangManager.STATE_MIGRATION));

		open.setState(Gang.State.OPEN);
		when(repository.loadAll()).thenReturn(List.of(open));
		new GangManager(repository, mock(GangAllianceRepositoryContract.class), migrations).initialize();

		assertEquals(Gang.State.OPEN, open.getState());
	}

	@Test
	@DisplayName("contributionMigration_runsOnce: stored contributions reset to 0 once; a later 40.0 stays")
	void contributionMigration_runsOnce() {
		Member a = new Member(UUID.randomUUID());
		a.setContribution(12.5);
		Member b = new Member(UUID.randomUUID());
		b.setContribution(-3);
		MemberRepositoryContract repository = mock(MemberRepositoryContract.class);
		when(repository.loadAll()).thenReturn(List.of(a, b));
		FakeMigrations migrations = new FakeMigrations();

		newMemberManager(repository, migrations).initialize();

		assertEquals(0.0, a.getContribution());
		assertEquals(0.0, b.getContribution());
		assertTrue(migrations.isDone(MemberManager.CONTRIBUTION_MIGRATION));

		Member later = new Member(UUID.randomUUID());
		later.setContribution(40.0);
		when(repository.loadAll()).thenReturn(List.of(later));
		newMemberManager(repository, migrations).initialize();

		assertEquals(40.0, later.getContribution());
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("stateMigration_skippedWhenGangLoadFailed: a failed gang read is not an empty table (T-249)")
	void stateMigration_skippedWhenGangLoadFailed() {
		IRepository<Gang> repository = mock(IRepository.class, withSettings().extraInterfaces(LoadOutcome.class));
		when(repository.loadAll()).thenReturn(List.of());
		when(((LoadOutcome) repository).lastLoadFailed()).thenReturn(true);
		FakeMigrations migrations = new FakeMigrations();

		new GangManager(repository, mock(GangAllianceRepositoryContract.class), migrations).initialize();

		assertFalse(migrations.isDone(GangManager.STATE_MIGRATION));
		verify(repository, never()).saveAll(anyCollection());
	}

	@Test
	@DisplayName("contributionMigration_skippedWhenMemberLoadFailed: a failed member read is not an empty table (T-249)")
	void contributionMigration_skippedWhenMemberLoadFailed() {
		MemberRepositoryContract repository = mock(MemberRepositoryContract.class,
		                                           withSettings().extraInterfaces(LoadOutcome.class));
		when(repository.loadAll()).thenReturn(List.of());
		when(((LoadOutcome) repository).lastLoadFailed()).thenReturn(true);
		FakeMigrations migrations = new FakeMigrations();

		newMemberManager(repository, migrations).initialize();

		assertFalse(migrations.isDone(MemberManager.CONTRIBUTION_MIGRATION));
		verify(repository, never()).saveAll(anyCollection());
	}

	private static MemberManager newMemberManager(MemberRepositoryContract repository, GangMigrationsContract migrations) {
		return new MemberManager(mock(JavaPlugin.class), mock(DatabaseHandler.class), repository,
		                         mock(GangLookupContract.class), mock(RankLookupContract.class), migrations);
	}

}
