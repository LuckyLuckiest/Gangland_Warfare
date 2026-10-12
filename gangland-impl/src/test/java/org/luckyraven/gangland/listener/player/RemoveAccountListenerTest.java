package org.luckyraven.gangland.listener.player;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RemoveAccountListener - a quit during the join load must not save defaults")
class RemoveAccountListenerTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("T-293: a load-pending user is evicted without a save and without an offline snapshot")
	void quitWhileLoadPending_savesNothing() {
		UserManager<Player>        online  = mock(UserManager.class);
		UserManager<OfflinePlayer> offline = mock(UserManager.class);
		GanglandDatabase           db      = mock(GanglandDatabase.class);
		RepositoryRegistry         registry = mock(RepositoryRegistry.class);
		when(db.getRepositoryRegistry()).thenReturn(registry);

		Player       player = mock(Player.class);
		User<Player> user   = mock(User.class);
		when(online.getUser(player)).thenReturn(user);
		when(user.isLoadPending()).thenReturn(true);
		when(user.getBounty()).thenReturn(mock(Bounty.class));
		when(user.getWanted()).thenReturn(mock(Wanted.class));
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new RemoveAccountListener(mock(Gangland.class), db, online, offline).onPlayerQuit(event);
		new RemoveAccountListener(mock(Gangland.class), db, online, offline).onPlayerLeave(event);

		verify(online).remove(user);
		verify(registry, never()).getGenericRepository(any());
		verify(offline, never()).add(any());
	}

}
