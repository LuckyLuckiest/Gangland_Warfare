package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NameLookupTest {

	@Test
	@DisplayName("a name matched only through Bukkit's offline list is cached load-pending, not as a saveable default")
	@SuppressWarnings("unchecked")
	void offlineNameWithoutCachedRow_isLoadPending() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			OfflinePlayer steve = mock(OfflinePlayer.class);
			when(steve.getName()).thenReturn("Steve");
			bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{steve});

			UserManager<Player>        online  = mock(UserManager.class);
			UserManager<OfflinePlayer> offline = mock(UserManager.class);
			User<OfflinePlayer>        created = mock(User.class);
			when(offline.getUser(any(OfflinePlayer.class))).thenReturn(null);
			doReturn(created).when(offline).create(any(OfflinePlayer.class));

			User<?> found = NameLookup.findByName("steve", online, offline);

			assertSame(created, found);
			verify(created).setLoadPending(true);
			verify(offline).add(created);
		}
	}

	@Test
	@DisplayName("B3: a name whose stored row reads fine is cached hydrated, not load-pending")
	@SuppressWarnings("unchecked")
	void offlineNameWithReadableRow_isNotLoadPending() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			OfflinePlayer steve = mock(OfflinePlayer.class);
			when(steve.getName()).thenReturn("Steve");
			bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{steve});

			UserManager<Player>        online  = mock(UserManager.class);
			UserManager<OfflinePlayer> offline = mock(UserManager.class);
			User<OfflinePlayer>        created = mock(User.class);
			when(offline.getUser(any(OfflinePlayer.class))).thenReturn(null);
			doReturn(created).when(offline).create(any(OfflinePlayer.class));
			when(offline.loadStored(created)).thenReturn(true);

			NameLookup.findByName("steve", online, offline);

			verify(created, never()).setLoadPending(true);
			verify(offline).add(created);
		}
	}

}
