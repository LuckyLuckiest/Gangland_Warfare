package org.luckyraven.gangland.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.Placeholder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the death money penalty with a real {@link User} and {@link EconomyHandler}, so every assertion is about
 * money that actually moved: {@code Lose_Money: false} charges and pays nothing, and a broken or oversized formula
 * never throws out of the death handler.
 */
@DisplayName("PlayerDeathListener - death money")
class PlayerDeathListenerTest {

	@TempDir
	Path dir;

	private BukkitStatics        bukkit;
	private Player               player;
	private User<Player>         user;
	private PlayerDeathListener  listener;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		EconomyHandler.setVaultEconomy(null);

		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		IdentitySettings.bind(identity);

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		Placeholder placeholder = mock(Placeholder.class);
		when(placeholder.convert(org.mockito.ArgumentMatchers.any(), anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		user = new User<>(mock(JavaPlugin.class), player, placeholder);
		user.getEconomy().setAmount(Currency.of(10_000));

		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);
		listener = new PlayerDeathListener(userManager, mock(GanglandPlaceholder.class), mock(BankTiers.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("Lose_Money false charges nothing and pays nothing")
	void loseMoneyFalse_chargesNothing_andPaysNothing() throws IOException {
		settings("false", "balance * 0.15", 1000);

		listener.handleMoney(user);

		assertEquals(0, Currency.of(10_000).compareTo(user.getEconomy().getAmount()));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a broken formula charges 15 percent of the wallet and does not throw")
	void brokenFormula_charges15PercentFallback_withoutThrowing() throws IOException {
		settings("true", "balance *", 1000);

		listener.handleMoney(user);

		assertEquals(0, Currency.of(8_500).compareTo(user.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("a formula above the balance takes the whole wallet and does not throw")
	void formulaAboveBalance_takesTheWallet_withoutThrowing() throws IOException {
		settings("true", "balance * 3", 1000);

		listener.handleMoney(user);

		assertEquals(0, user.getEconomy().getAmount().signum());
		verify(player).sendMessage(contains("$10K"));
	}

	@Test
	@DisplayName("a formula that comes to 0 sends no message and moves no money")
	void zeroFormula_sendsNoMessage() throws IOException {
		settings("true", "0", 1000);

		listener.handleMoney(user);

		assertEquals(0, Currency.of(10_000).compareTo(user.getEconomy().getAmount()));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a player at the threshold pays nothing")
	void balanceAtThreshold_paysNothing() throws IOException {
		settings("true", "balance * 0.15", 10_000);
		PlayerDeathEvent event = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(player);

		listener.onPlayerDeath(event);

		assertEquals(0, Currency.of(10_000).compareTo(user.getEconomy().getAmount()));
		verify(player, never()).sendMessage(anyString());
	}

	private void settings(String loseMoney, String formula, int threshold) throws IOException {
		SettingsFixture.write(dir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Debug: false
				User:
				  Death:
				    Money:
				      Lose_Money: %s
				      Formula: "%s"
				      Threshold: %d
				""".formatted(loseMoney, formula, threshold));
		SettingsFixture.initialize(dir);
	}

}
