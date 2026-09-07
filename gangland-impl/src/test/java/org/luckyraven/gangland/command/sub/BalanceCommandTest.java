package org.luckyraven.gangland.command.sub;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression net for CM-05 (Observation #5, commands-messages-platform.md).
 *
 * <p>{@code /glw balance <target>}'s {@code OptionalArgument} completion supplier used to run
 * {@code UserTable.selectAllTableQuery} plus one {@code Bukkit.getOfflinePlayer(uuid)} per registered user,
 * synchronously on the main thread, on <em>every</em> tab keystroke — and the action body then repeated the same
 * scan. Both now read the online/offline {@link UserManager} caches first; those caches are filled from that exact
 * table at bootstrap ({@code PlayerBootstrapService.loadOfflinePlayers}).
 *
 * <p>These tests drive the extracted {@code cachedNames}/{@code findCached} seams. Neither takes a
 * {@code GanglandDatabase}, so a passing suite is itself the proof that completion no longer needs a query.
 */
@DisplayName("BalanceCommand — CM-05 cache-backed target lookup and completion")
class BalanceCommandTest {

	private UserManager<Player>        userManager;
	private UserManager<OfflinePlayer> offlineUserManager;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		userManager        = mock(UserManager.class);
		offlineUserManager = mock(UserManager.class);
	}

	@Test
	@DisplayName("CM-05: completion names come from both caches, de-duplicated and case-insensitively sorted")
	void cachedNames_mergesBothCaches() {
		Map<UUID, User<Player>> online = new LinkedHashMap<>();
		put(online, onlineUser("Zoe"));
		put(online, onlineUser("alice"));

		Map<UUID, User<OfflinePlayer>> offline = new LinkedHashMap<>();
		put(offline, offlineUser("Bob"));
		// the same account cached on both sides must not be completed twice
		put(offline, offlineUser("alice"));

		when(userManager.getUsers()).thenReturn(online);
		when(offlineUserManager.getUsers()).thenReturn(offline);

		List<String> names = BalanceCommand.cachedNames(userManager, offlineUserManager);

		assertEquals(List.of("alice", "Bob", "Zoe"), names);
	}

	@Test
	@DisplayName("CM-05: names Bukkit has not resolved yet are skipped instead of completed as null")
	void cachedNames_skipsNullAndEmptyNames() {
		Map<UUID, User<OfflinePlayer>> offline = new LinkedHashMap<>();
		put(offline, offlineUser(null));
		put(offline, offlineUser(""));
		put(offline, offlineUser("Carol"));

		when(userManager.getUsers()).thenReturn(Map.of());
		when(offlineUserManager.getUsers()).thenReturn(offline);

		assertEquals(List.of("Carol"), BalanceCommand.cachedNames(userManager, offlineUserManager));
	}

	@Test
	@DisplayName("an empty pair of caches completes nothing rather than failing")
	void cachedNames_emptyCaches_returnsEmptyList() {
		when(userManager.getUsers()).thenReturn(Map.of());
		when(offlineUserManager.getUsers()).thenReturn(Map.of());

		assertTrue(BalanceCommand.cachedNames(userManager, offlineUserManager).isEmpty());
	}

	@Test
	@DisplayName("CM-05: an online target resolves from the online cache")
	void findCached_onlineTarget_resolvesFromOnlineCache() {
		User<Player>            user   = onlineUser("Dave");
		Map<UUID, User<Player>> online = new LinkedHashMap<>();
		put(online, user);

		when(userManager.getUsers()).thenReturn(online);
		when(offlineUserManager.getUsers()).thenReturn(Map.of());

		assertSame(user, BalanceCommand.findCached("Dave", userManager, offlineUserManager));
	}

	@Test
	@DisplayName("CM-05: an offline target resolves from the offline cache without any database scan")
	void findCached_offlineTarget_resolvesFromOfflineCache() {
		User<OfflinePlayer>            user    = offlineUser("Erin");
		Map<UUID, User<OfflinePlayer>> offline = new LinkedHashMap<>();
		put(offline, user);

		when(userManager.getUsers()).thenReturn(Map.of());
		when(offlineUserManager.getUsers()).thenReturn(offline);

		assertSame(user, BalanceCommand.findCached("Erin", userManager, offlineUserManager));
	}

	@Test
	@DisplayName("target names match case-insensitively, as the old database scan did")
	void findCached_matchesCaseInsensitively() {
		User<OfflinePlayer>            user    = offlineUser("Frank");
		Map<UUID, User<OfflinePlayer>> offline = new LinkedHashMap<>();
		put(offline, user);

		when(userManager.getUsers()).thenReturn(Map.of());
		when(offlineUserManager.getUsers()).thenReturn(offline);

		assertSame(user, BalanceCommand.findCached("fRaNk", userManager, offlineUserManager));
	}

	@Test
	@DisplayName("an unknown target returns null so the caller can fall back to the database")
	void findCached_unknownTarget_returnsNull() {
		Map<UUID, User<OfflinePlayer>> offline = new LinkedHashMap<>();
		put(offline, offlineUser("Grace"));

		when(userManager.getUsers()).thenReturn(Map.of());
		when(offlineUserManager.getUsers()).thenReturn(offline);

		assertNull(BalanceCommand.findCached("Heidi", userManager, offlineUserManager));
	}

	private static <T extends OfflinePlayer> void put(Map<UUID, User<T>> cache, User<T> user) {
		cache.put(UUID.randomUUID(), user);
	}

	@SuppressWarnings("unchecked")
	private static User<Player> onlineUser(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);

		User<Player> user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		return user;
	}

	@SuppressWarnings("unchecked")
	private static User<OfflinePlayer> offlineUser(String name) {
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		when(offlinePlayer.getName()).thenReturn(name);

		User<OfflinePlayer> user = mock(User.class);
		when(user.getUser()).thenReturn(offlinePlayer);
		return user;
	}
}
