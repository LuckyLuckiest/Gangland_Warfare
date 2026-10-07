package org.luckyraven.gangland.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
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
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
	private UserManager<Player>  userManager;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		Messages.init(new FakeMessageProvider().withString("Death.Ward_Bill",
		                                                   "Ward bill: -%money_symbol%%amount%"));
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

		userManager = mock(UserManager.class);
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

	@Test
	@DisplayName("Hospital.Enable: a down charges nothing yet, the bill waits for the stand-up")
	void downed_withHospital_chargesNothingAtTheDown() throws IOException {
		settings("true", "balance * 0.15", 1000, true);

		listener.onPlayerDowned(new PlayerDownedEvent(player));

		assertEquals(0, Currency.of(10_000).compareTo(user.getEconomy().getAmount()));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the stand-up charges the quoted bill once, with the ward-bill line")
	void undowned_chargesTheQuotedBillOnce() throws IOException {
		settings("true", "balance * 0.15", 1000, true);
		listener.onPlayerDowned(new PlayerDownedEvent(player));

		listener.onPlayerUndowned(new PlayerUndownedEvent(player));
		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		assertEquals(0, Currency.of(8_500).compareTo(user.getEconomy().getAmount()));
		verify(player).sendMessage(contains("Ward bill: -$1.5K"));
	}

	@Test
	@DisplayName("quitting while downed does not dodge the bill")
	void quitWhileDowned_chargesTheBill() throws IOException {
		settings("true", "balance * 0.15", 1000, true);
		listener.onPlayerDowned(new PlayerDownedEvent(player));
		PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(player);

		listener.onQuit(quit);
		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		assertEquals(0, Currency.of(8_500).compareTo(user.getEconomy().getAmount()), "charged once, on the quit");
	}

	@Test
	@DisplayName("the bill is quoted with the wanted level of the down, not the level after the DEATH reset")
	@SuppressWarnings("unchecked")
	void quote_usesTheWantedLevelBeforeTheDeathReset() throws IOException {
		settings("true", "wanted * 100", 1000, true);
		AtomicInteger stars  = new AtomicInteger(3);
		Wanted        wanted = mock(Wanted.class);
		when(wanted.getLevel()).thenAnswer(inv -> stars.get());
		Level level = mock(Level.class);
		when(level.getLevelValue()).thenReturn(1);
		Bounty bounty = mock(Bounty.class);
		when(bounty.getAmount()).thenReturn(BigDecimal.ZERO);
		User<Player>   mocked = mock(User.class);
		EconomyHandler wallet = mock(EconomyHandler.class);
		when(wallet.getAmount()).thenReturn(Currency.of(10_000));
		when(mocked.getEconomy()).thenReturn(wallet);
		when(mocked.getWanted()).thenReturn(wanted);
		when(mocked.getLevel()).thenReturn(level);
		when(mocked.getBounty()).thenReturn(bounty);
		when(mocked.withdraw(any(BigDecimal.class))).thenAnswer(inv -> inv.getArgument(0));
		when(userManager.getUser(player)).thenReturn(mocked);

		listener.onPlayerDowned(new PlayerDownedEvent(player));
		stars.set(0); // the DEATH wanted reset
		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		verify(mocked).withdraw(Currency.of(300));
	}

	@Test
	@DisplayName("Hospital.Enable false charges at the down, as before")
	void hospitalDisabled_chargesAtTheDownAsBefore() throws IOException {
		settings("true", "balance * 0.15", 1000, false);

		listener.onPlayerDowned(new PlayerDownedEvent(player));

		assertEquals(0, Currency.of(8_500).compareTo(user.getEconomy().getAmount()));
		listener.onPlayerUndowned(new PlayerUndownedEvent(player));
		assertEquals(0, Currency.of(8_500).compareTo(user.getEconomy().getAmount()), "nothing more at the stand-up");
	}

	@Test
	@DisplayName("Hospital.Enable false keeps the hard-coded Death penalty line")
	void hospitalDisabled_keepsTheDeathPenaltyLine() throws IOException {
		settings("true", "balance * 0.15", 1000, false);

		listener.handleMoney(user);

		verify(player).sendMessage(contains("Death penalty"));
		verify(player, never()).sendMessage(contains("Ward bill"));
	}

	@Test
	@DisplayName("a downed player who then really dies pays the bill once, not twice")
	@SuppressWarnings("unchecked")
	void downedThenDies_paysOnce() throws ReflectiveOperationException, IOException {
		settings("true", "balance * 0.15", 1000, true);
		PlayerDeathEvent death = mock(PlayerDeathEvent.class);
		when(death.getEntity()).thenReturn(player);

		// the death lands inside the dedup window of the down: the pending bill is the one charge
		listener.onPlayerDowned(new PlayerDownedEvent(player));
		listener.onPlayerDeath(death);
		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		assertEquals(0, Currency.of(8_500).compareTo(user.getEconomy().getAmount()));

		// the death lands after the window: the fresh quote replaces the pending bill
		Field field = PlayerDeathListener.class.getDeclaredField("recentDeaths");
		field.setAccessible(true);
		((Map<UUID, Long>) field.get(listener)).clear();
		user.getEconomy().setAmount(Currency.of(10_000));
		listener.onPlayerDowned(new PlayerDownedEvent(player));
		((Map<UUID, Long>) field.get(listener)).clear();
		listener.onPlayerDeath(death);
		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		assertEquals(0, Currency.of(8_500).compareTo(user.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("a down below the threshold leaves no bill to charge later")
	void belowThreshold_noPendingBill() throws IOException {
		settings("true", "balance * 0.15", 20_000, true);

		listener.onPlayerDowned(new PlayerDownedEvent(player));
		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		assertEquals(0, Currency.of(10_000).compareTo(user.getEconomy().getAmount()));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("US-33/WB-17: a death entry outside the dedup window is dropped on the next put")
	@SuppressWarnings("unchecked")
	void recentDeaths_arePruned() throws ReflectiveOperationException, IOException {
		settings("true", "balance * 0.15", 1000, true);
		Field field = PlayerDeathListener.class.getDeclaredField("recentDeaths");
		field.setAccessible(true);
		Map<UUID, Long> recent = (Map<UUID, Long>) field.get(listener);
		UUID            stale  = UUID.randomUUID();
		recent.put(stale, System.currentTimeMillis() - 60_000L);

		listener.onPlayerDowned(new PlayerDownedEvent(player));

		assertFalse(recent.containsKey(stale), "the stale entry is gone");
		assertTrue(recent.containsKey(player.getUniqueId()), "the fresh one is kept");
	}

	private void settings(String loseMoney, String formula, int threshold) throws IOException {
		settings(loseMoney, formula, threshold, true);
	}

	private void settings(String loseMoney, String formula, int threshold, boolean hospital) throws IOException {
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
				    Hospital:
				      Enable: %s
				""".formatted(loseMoney, formula, threshold, hospital));
		SettingsFixture.initialize(dir);
	}

}
