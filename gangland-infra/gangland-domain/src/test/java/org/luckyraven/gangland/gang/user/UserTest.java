package org.luckyraven.gangland.gang.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.keystone.util.Placeholder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins the parts of {@link User} that are pure-ish domain logic rather than persistence wiring: gang membership
 * flags, the {@link User#withdraw(BigDecimal)} clamp-to-balance fallback used by {@code EconomyOwner}, and the
 * offline-player guards on {@link User#flushPermissions} / {@link User#sendMessage(String)}. Bounty/wanted
 * behaviour is out of scope here (owned by the wanted/bounty test suite).
 */
@DisplayName("User - membership flags, withdraw fallback, and offline-player guards")
class UserTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
	}

	private User<Player> newOnlineUser() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		Placeholder placeholder = mock(Placeholder.class);
		InventoryRegistry registry = new InventoryRegistry();
		return new User<>(plugin, player, placeholder, registry);
	}

	@Test
	@DisplayName("hasGang()/resetGang(): a fresh User starts with no gang (gangId == -1)")
	void hasGang_defaultsFalse_untilGangIdSet() {
		User<Player> user = newOnlineUser();

		assertFalse(user.hasGang());

		user.setGangId(7);
		assertTrue(user.hasGang());

		user.resetGang();
		assertFalse(user.hasGang());
	}

	@Test
	void hasBank_falseUntilBankSet() {
		User<Player> user = newOnlineUser();

		assertFalse(user.hasBank());
		user.setBank(mock(org.luckyraven.keystone.economy.bank.Bank.class));
		assertTrue(user.hasBank());
	}

	@Test
	@DisplayName("getKillDeathRatio() returns 0 (not NaN/Infinity) when deaths is 0, regardless of kills")
	void getKillDeathRatio_zeroDeaths_returnsZeroNotInfinity() {
		User<Player> user = newOnlineUser();
		user.setKills(10);
		user.setDeaths(0);

		assertEquals(0D, user.getKillDeathRatio());
	}

	@Test
	void getKillDeathRatio_nonZeroDeaths_dividesNormally() {
		User<Player> user = newOnlineUser();
		user.setKills(9);
		user.setDeaths(3);

		assertEquals(3D, user.getKillDeathRatio());
	}

	@Test
	@DisplayName("withdraw(amount) within the balance debits exactly that amount and returns it")
	void withdraw_withinBalance_debitsExactAmount() {
		User<Player> user = newOnlineUser();
		user.getEconomy().setAmount(BigDecimal.valueOf(100));

		BigDecimal taken = user.withdraw(BigDecimal.valueOf(40));

		assertEquals(0, BigDecimal.valueOf(40).compareTo(taken));
		assertEquals(0, BigDecimal.valueOf(60).compareTo(user.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("withdraw(amount) beyond the balance is clamped: it withdraws everything the user has and returns that, "
	             + "rather than throwing EconomyException to the caller")
	void withdraw_beyondBalance_clampsToWholeBalanceInstead() {
		User<Player> user = newOnlineUser();
		user.getEconomy().setAmount(BigDecimal.valueOf(25));

		BigDecimal taken = user.withdraw(BigDecimal.valueOf(1000));

		assertEquals(0, BigDecimal.valueOf(25).compareTo(taken), "returns whatever the balance actually was");
		assertEquals(0, BigDecimal.ZERO.compareTo(user.getEconomy().getAmount()), "balance drained to zero");
	}

	@Test
	@DisplayName("withdraw(amount) on a zero balance returns ZERO and leaves the balance at zero")
	void withdraw_zeroBalance_returnsZero() {
		User<Player> user = newOnlineUser();
		// fresh User already starts at Currency.ZERO

		BigDecimal taken = user.withdraw(BigDecimal.valueOf(500));

		assertEquals(0, BigDecimal.ZERO.compareTo(taken));
		assertEquals(0, BigDecimal.ZERO.compareTo(user.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("flushPermissions is a no-op for an offline (non-Player) user - no NPE even though permissionAttachment is never set")
	void flushPermissions_offlinePlayer_isNoOpAndDoesNotNpe() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.getUniqueId()).thenReturn(UUID.randomUUID());
		Placeholder placeholder = mock(Placeholder.class);
		User<OfflinePlayer> user = new User<>(plugin, offline, placeholder, new InventoryRegistry());

		assertDoesNotThrow(() -> user.flushPermissions(new Rank("Boss", 1)));
	}

	@Test
	@DisplayName("flushPermissions(rank) clears every currently-set node then applies only the new rank's nodes, and updates the client's command list")
	void flushPermissions_onlinePlayer_clearsThenReapplies() {
		User<Player> user = newOnlineUser();
		Player player = user.getUser();

		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(
				new java.util.HashMap<>(Collections.singletonMap("old.node", true)));
		user.setPermissionAttachment(attachment);

		Rank newRank = new Rank("Boss", 1, List.of(new Permission(1, "new.node")));

		user.flushPermissions(newRank);

		verify(attachment).unsetPermission("old.node");
		verify(attachment).setPermission("new.node", true);
		verify(player).updateCommands();
	}

	@Test
	@DisplayName("flushPermissions(null) clears existing nodes and grants nothing new, but still updates the command list")
	void flushPermissions_nullRank_clearsOnly() {
		User<Player> user = newOnlineUser();
		Player player = user.getUser();

		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(
				new java.util.HashMap<>(Collections.singletonMap("old.node", true)));
		user.setPermissionAttachment(attachment);

		user.flushPermissions(null);

		verify(attachment).unsetPermission("old.node");
		verify(attachment, never()).setPermission(anyString(), anyBoolean());
		verify(player).updateCommands();
	}

	@Test
	@DisplayName("sendMessage(String) is a no-op for an offline (non-Player) user - the placeholder pipeline is never touched")
	void sendMessage_offlinePlayer_isNoOp() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.getUniqueId()).thenReturn(UUID.randomUUID());
		Placeholder placeholder = mock(Placeholder.class);
		User<OfflinePlayer> user = new User<>(plugin, offline, placeholder, new InventoryRegistry());

		user.sendMessage("&aHello");

		verifyNoInteractions(placeholder);
	}

	@Test
	@DisplayName("sendMessage(String) for an online player resolves placeholders, colorizes, and sends to the Player")
	void sendMessage_onlinePlayer_resolvesAndSends() {
		User<Player> user = newOnlineUser();
		Player player = user.getUser();
		Placeholder placeholder = user.getPlaceholder();
		when(placeholder.convert(eq(player), anyString())).thenReturn("&aHello Steve");

		user.sendMessage("&agreeting");

		verify(player).sendMessage(anyString());
	}

}
