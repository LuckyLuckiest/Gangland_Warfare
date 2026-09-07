package org.luckyraven.gangland.bootstrap;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.plugin.PluginManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins CL-02 (core-lifecycle.md observation #2, High risk): the auto-save timer used to be started with
 * {@code RepeatingTimer.start(true)}, i.e. on an async worker. {@code updatingDatabase(...)} walks the live online and
 * offline user caches, clears the offline one, and every repository data supplier copies its manager's live map — all
 * maps that join / quit handling mutates on the main thread. Iterating them off-thread raced into
 * {@code ConcurrentModificationException}s (a partial save that skipped the remaining repositories) and lost writes.
 *
 * <p>The fix keeps every snapshot on the main thread and pushes only the JDBC work async
 * ({@code AbstractRepository}'s own hop inside {@code RepositoryRegistry.saveAll}), so the timer itself must be
 * scheduled synchronously and the offline cache may only be cleared once {@code saveAll} has returned.
 */
@DisplayName("PeriodicalUpdates - the auto-save timer runs on the main thread")
class PeriodicalUpdatesTest {

	private Gangland                   gangland;
	private GanglandDatabase           database;
	private RepositoryRegistry         repositoryRegistry;
	private PluginManager              pluginManager;
	private UserManager<Player>        userManager;
	private UserManager<OfflinePlayer> offlineUserManager;
	private WeaponManager              weaponManager;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		gangland           = mock(Gangland.class);
		database           = mock(GanglandDatabase.class);
		repositoryRegistry = mock(RepositoryRegistry.class);
		pluginManager      = mock(PluginManager.class);
		userManager        = mock(UserManager.class);
		offlineUserManager = mock(UserManager.class);
		weaponManager      = mock(WeaponManager.class);

		when(database.getRepositoryRegistry()).thenReturn(repositoryRegistry);
	}

	@Test
	@DisplayName("CL-02: start() schedules a SYNC repeating task, never an async one")
	void start_schedulesTheAutoSaveOnTheMainThread() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(mock(BukkitTask.class));

			PeriodicalUpdates updates = new PeriodicalUpdates(gangland, database, pluginManager, userManager,
			                                                  offlineUserManager, weaponManager, 300L);

			updates.start();

			verify(bukkit.scheduler()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
			verify(bukkit.scheduler(), never())
					.runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
		}
	}

	@Test
	@DisplayName("start() without a configured interval is a no-op — nothing is scheduled at all")
	void start_withoutATimer_schedulesNothing() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			PeriodicalUpdates updates = new PeriodicalUpdates(gangland, database, pluginManager, userManager,
			                                                  offlineUserManager, weaponManager);

			updates.start();

			verify(bukkit.scheduler(), never())
					.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
			verify(bukkit.scheduler(), never())
					.runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
		}
	}

	@Test
	@DisplayName("CL-02/CL-23: the offline cache is only cleared AFTER saveAll has taken its snapshot")
	void updatingDatabase_clearsTheOfflineCacheAfterTheRepositorySnapshot() {
		PeriodicalUpdates updates = new PeriodicalUpdates(gangland, database, pluginManager, userManager,
		                                                  offlineUserManager, weaponManager);

		updates.updatingDatabase();

		InOrder inOrder = inOrder(repositoryRegistry, offlineUserManager);

		// AbstractRepository.saveAll copies each supplier's collection on the calling thread before its async hop,
		// so the snapshot exists by the time saveAll returns — clearing any earlier would drop every offline user.
		inOrder.verify(repositoryRegistry).saveAll((Runnable) null);
		inOrder.verify(offlineUserManager).clear();
	}

}
