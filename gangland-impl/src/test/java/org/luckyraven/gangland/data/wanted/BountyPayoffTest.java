package org.luckyraven.gangland.data.wanted;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * The shared pay-off helper behind {@code /glw bounty pay} and the {@code [BOUNTY]} sign: real {@link User}s with
 * their real {@link EconomyHandler}s, so every assertion is about money that actually moved (or did not).
 */
@DisplayName("BountyPayoff: the target pays the whole bounty to the city")
class BountyPayoffTest {

	@TempDir
	Path dir;

	private BukkitStatics bukkit;
	private JavaPlugin    plugin;
	private Player        payer, alice, bob;
	private UUID          aliceId, bobId;
	private User<Player>  payerUser;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider()
				              .withString("Commands.Prefix", "CMD-PREFIX ")
				              .withString("Errors.Prefix", "ERR-PREFIX ")
				              .withString("Commands.Bounty.Paid", "PAID %amount%")
				              .withString("Commands.Bounty.Paid_Notice", "NOTICE %player% %amount%")
				              .withString("Errors.Bounty.No_Bounty_Self", "NO-BOUNTY")
				              .withString("Errors.Bounty.Pay_No_Money", "NO-MONEY %amount%"));
		EconomyHandler.setVaultEconomy(null);

		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		IdentitySettings.bind(identity);

		plugin = mock(JavaPlugin.class);
		payer  = player("Payer");
		alice  = player("Alice");
		bob    = player("Bob");

		aliceId = alice.getUniqueId();
		bobId   = bob.getUniqueId();

		payerUser = new User<>(plugin, payer, (p, raw) -> raw);
		payerUser.getEconomy().setAmount(Currency.of(1000));

		// alice is online, bob is not (the statics default to null for any other id)
		bukkit.statics().when(() -> Bukkit.getPlayer(aliceId)).thenReturn(alice);
		bukkit.statics().when(() -> Bukkit.getPlayer(payer.getUniqueId())).thenReturn(payer);
	}

	@AfterEach
	void tearDown() {
		EconomyHandler.setVaultEconomy(null);
		bukkit.close();
	}

	@Test
	@DisplayName("a user still loading is refused as no bounty: nothing is withdrawn, the bounty stays")
	void loadPending_refusesWithoutWithdraw() {
		payerUser.getBounty().addBounty(alice, Currency.of(500));
		payerUser.setLoadPending(true);

		BountyPayoff.Result result = BountyPayoff.pay(payerUser);

		assertEquals(BountyPayoff.Outcome.NO_BOUNTY, result.outcome());
		assertEquals(0, Currency.of(1000).compareTo(payerUser.getEconomy().getAmount()));
		assertTrue(payerUser.getBounty().hasBounty());
		verify(alice, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the whole total (escrow + notoriety) leaves the wallet and lands nowhere: posters are not refunded")
	void paid_withdrawsWholeTotal_andDepositsNowhere() {
		User<Player> aliceUser = new User<>(plugin, alice, (p, raw) -> raw);
		aliceUser.getEconomy().setAmount(Currency.of(2000));

		payerUser.getBounty().addBounty(alice, Currency.of(500));
		payerUser.getBounty().addNotoriety(Currency.of(100));
		BigDecimal before = payerUser.getEconomy().getAmount().add(aliceUser.getEconomy().getAmount());

		BountyPayoff.Result result = BountyPayoff.pay(payerUser);

		assertEquals(BountyPayoff.Outcome.PAID, result.outcome());
		assertTrue(result.paid());
		assertEquals(0, Currency.of(600).compareTo(result.amount()));
		assertEquals(0, Currency.of(400).compareTo(payerUser.getEconomy().getAmount()));
		assertEquals(0, Currency.of(2000).compareTo(aliceUser.getEconomy().getAmount()), "the poster is not refunded");
		assertEquals(0, before.subtract(Currency.of(600))
		                      .compareTo(payerUser.getEconomy().getAmount().add(aliceUser.getEconomy().getAmount())),
		             "every wallet together lost exactly the total: it went to the city");
	}

	@Test
	@DisplayName("a paid-off bounty and its ledger are gone")
	void paid_clearsBountyAndLedger() {
		payerUser.getBounty().addBounty(alice, Currency.of(500));
		payerUser.getBounty().addNotoriety(Currency.of(100));

		BountyPayoff.pay(payerUser);

		assertFalse(payerUser.getBounty().hasBounty());
		assertTrue(payerUser.getBounty().stakes().isEmpty());
	}

	@Test
	@DisplayName("an online poster is told the payer's name and their OWN stake, not the whole bounty")
	void paid_notifiesOnlinePosterWithOwnStake() {
		payerUser.getBounty().addBounty(alice, Currency.of(300));
		payerUser.getBounty().addBounty(bob, Currency.of(200));

		BountyPayoff.pay(payerUser);

		verify(alice).sendMessage(contains("NOTICE Payer " + Settings.formatAmount(Currency.of(300))));
		verify(alice, times(1)).sendMessage(anyString());
		verify(bob, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("console, legacy, name-keyed and offline posters are skipped without an exception")
	void offlineConsoleLegacyNamePosters_skippedWithoutException() {
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);

		payerUser.getBounty().addBounty(console, Currency.of(100));
		payerUser.getBounty().addBounty(bob, Currency.of(100));
		payerUser.getBounty().restoreLedger("Steve=50:50;" + payerUser.getBounty().serializeLedger());
		payerUser.getBounty().addNotoriety(Currency.of(10));

		BountyPayoff.Result result = assertDoesNotThrow(() -> BountyPayoff.pay(payerUser));

		assertTrue(result.paid());
		verify(bob, never()).sendMessage(anyString());
		verify(alice, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a ledger saved before the ledger existed (legacy key) pays off without a notice or an exception")
	void legacyLedger_skippedWithoutException() {
		payerUser.getBounty().setAmount(Currency.of(250));
		payerUser.getBounty().restoreLedger(null);
		assertEquals(1, payerUser.getBounty().stakes().size());

		BountyPayoff.Result result = assertDoesNotThrow(() -> BountyPayoff.pay(payerUser));

		assertTrue(result.paid());
		assertEquals(0, Currency.of(750).compareTo(payerUser.getEconomy().getAmount()));
		verify(alice, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the payer who posted on himself is never told about his own stake")
	void payerNeverNotifiedOfOwnStake() {
		payerUser.getBounty().addBounty(payer, Currency.of(200));
		payerUser.getBounty().addBounty(alice, Currency.of(100));

		BountyPayoff.pay(payerUser);

		verify(payer, never()).sendMessage(anyString());
		verify(alice).sendMessage(contains("NOTICE"));
	}

	@Test
	@DisplayName("no bounty: NO_BOUNTY, nothing withdrawn")
	void noBounty_returnsNoBounty_noWithdraw() {
		BountyPayoff.Result result = BountyPayoff.pay(payerUser);

		assertEquals(BountyPayoff.Outcome.NO_BOUNTY, result.outcome());
		assertFalse(result.paid());
		assertEquals(0, Currency.of(1000).compareTo(payerUser.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("a short wallet: NO_MONEY with the total, wallet / bounty / ledger untouched, no notice")
	void shortBalance_returnsNoMoney_nothingChanges() {
		payerUser.getEconomy().setAmount(Currency.of(100));
		payerUser.getBounty().addBounty(alice, Currency.of(500));

		BountyPayoff.Result result = BountyPayoff.pay(payerUser);

		assertEquals(BountyPayoff.Outcome.NO_MONEY, result.outcome());
		assertEquals(0, Currency.of(500).compareTo(result.amount()));
		assertEquals(0, Currency.of(100).compareTo(payerUser.getEconomy().getAmount()));
		assertEquals(0, Currency.of(500).compareTo(payerUser.getBounty().getAmount()));
		assertEquals(1, payerUser.getBounty().stakes().size());
		verify(alice, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("Vault refuses the withdraw after the pre-check passed: NO_MONEY, the bounty and ledger survive, no notice")
	void withdrawRace_EconomyException_returnsNoMoney() {
		Economy vault = mock(Economy.class);
		when(vault.isEnabled()).thenReturn(true);
		when(vault.getBalance(any(OfflinePlayer.class))).thenReturn(1000D);
		when(vault.withdrawPlayer(any(OfflinePlayer.class), anyDouble()))
				.thenReturn(new EconomyResponse(0, 1000, EconomyResponse.ResponseType.FAILURE, "x"));
		EconomyHandler.setVaultEconomy(vault);

		payerUser.getBounty().addBounty(alice, Currency.of(500));

		BountyPayoff.Result result = assertDoesNotThrow(() -> BountyPayoff.pay(payerUser));

		assertEquals(BountyPayoff.Outcome.NO_MONEY, result.outcome());
		assertTrue(payerUser.getBounty().hasBounty());
		assertEquals(1, payerUser.getBounty().stakes().size());
		assertEquals(0, Currency.of(500).compareTo(payerUser.getBounty().stakes().get(aliceId.toString())));
		verify(alice, never()).sendMessage(anyString());
		verify(vault).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
	}

	@Test
	@DisplayName("text() carries the Commands/Errors prefix; rawText() (for a sign) does not; the amount is filled in")
	void text_vs_rawText_prefixing() {
		BigDecimal  amount = Currency.of(500);
		String      figure = Settings.formatAmount(amount);

		BountyPayoff.Result paid    = new BountyPayoff.Result(BountyPayoff.Outcome.PAID, amount);
		BountyPayoff.Result noMoney = new BountyPayoff.Result(BountyPayoff.Outcome.NO_MONEY, amount);
		BountyPayoff.Result none    = new BountyPayoff.Result(BountyPayoff.Outcome.NO_BOUNTY, Currency.ZERO);

		assertTrue(paid.text().contains("CMD-PREFIX"));
		assertTrue(paid.text().contains("PAID " + figure));
		assertFalse(paid.rawText().contains("CMD-PREFIX"));
		assertEquals("PAID " + figure, paid.rawText());

		assertTrue(noMoney.text().contains("ERR-PREFIX"));
		assertEquals("NO-MONEY " + figure, noMoney.rawText());

		assertTrue(none.text().contains("ERR-PREFIX"));
		assertEquals("NO-BOUNTY", none.rawText());
	}

	private static Player player(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

}
