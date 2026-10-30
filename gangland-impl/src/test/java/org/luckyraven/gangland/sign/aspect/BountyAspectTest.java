package org.luckyraven.gangland.sign.aspect;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The {@code [BOUNTY]} sign: unreadable sign content fails the sign instead of throwing out of the interact handler
 * (docket WB-21 / LS-22, the bounty half), and the pay-off lines (line 2 {@code pay}, and the legacy {@code clear})
 * pay the whole bounty off with configured text: no raw economy exception, no generic "missing something" refusal.
 * Real {@link User}s, so every assertion about money is about money that actually moved.
 */
@DisplayName("BountyAspect - the [BOUNTY] sign")
class BountyAspectTest {

	private static final String INVALID = "SIGN-INVALID-STUB";
	private static final String PAID    = "BOUNTY-PAID-STUB %amount%";
	private static final String NO_MONEY = "PAY-NO-MONEY-STUB %amount%";
	private static final String NO_BOUNTY = "NO-BOUNTY-SELF-STUB";

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private Player              player;
	private User<Player>        user;
	private BountyAspect        aspect;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider().withString("Signs.Invalid", INVALID)
		                                       .withString("Commands.Bounty.Paid", PAID)
		                                       .withString("Errors.Bounty.Pay_No_Money", NO_MONEY)
		                                       .withString("Errors.Bounty.No_Bounty_Self", NO_BOUNTY));
		EconomyHandler.setVaultEconomy(null);

		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		IdentitySettings.bind(identity);

		JavaPlugin plugin = mock(JavaPlugin.class);
		player = mock(Player.class);
		when(player.getName()).thenReturn("Payer");
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		user = new User<>(plugin, player, (p, raw) -> raw);

		UserManager<Player> online = mock(UserManager.class);
		when(online.getUser(player)).thenReturn(user);

		aspect = new BountyAspect(plugin, mock(UserManager.class), online, mock(InventoryService.class));
	}

	@AfterEach
	void tearDown() {
		EconomyHandler.setVaultEconomy(null);
		bukkit.close();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"bogus", "  "})
	@DisplayName("content that is not an operation fails the sign instead of throwing")
	void unknownContent_failsInsteadOfThrowing(String content) {
		ParsedSign garbage = sign(content);

		AspectResult result = assertDoesNotThrow(() -> aspect.execute(player, garbage));
		assertFalse(result.isSuccess());
		assertFalse(assertDoesNotThrow(() -> aspect.canExecute(player, garbage)));
		assertTrue(assertDoesNotThrow(() -> aspect.failureReason(player, garbage)).contains(INVALID),
		           "the refusal is the configured Signs.Invalid text");
	}

	@Test
	@DisplayName("pay withdraws the whole bounty, notoriety included, clears it and answers with the unprefixed Paid text")
	void payContent_paysOffWholeBounty() {
		fund(1000);
		bounty(500, 100);

		AspectResult result = aspect.execute(player, sign("pay"));

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals(0, Currency.of(400).compareTo(user.getEconomy().getAmount()), "1000 - (500 + 100)");
		assertFalse(user.getBounty().hasBounty());
		assertTrue(result.getMessage().contains("BOUNTY-PAID-STUB"), result.getMessage());
		assertFalse(result.getMessage().contains("Prefix"), "the sign adds the prefix itself: " + result.getMessage());
	}

	@Test
	@DisplayName("the legacy clear content is the same pay-off")
	void clearContent_isTheSamePayoff() {
		fund(1000);
		bounty(500, 100);

		AspectResult result = aspect.execute(player, sign("clear"));

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals(0, Currency.of(400).compareTo(user.getEconomy().getAmount()));
		assertFalse(user.getBounty().hasBounty());
		assertTrue(result.getMessage().contains("BOUNTY-PAID-STUB"), result.getMessage());
		assertFalse(result.getMessage().contains("Prefix"), result.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"clear", "pay"})
	@DisplayName("a short balance is refused with the configured text, never the raw economy exception")
	void shortBalance_refusesWithConfiguredText(String content) {
		fund(100);
		bounty(500, 0);

		AspectResult result = aspect.execute(player, sign(content));

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains("PAY-NO-MONEY-STUB"), result.getMessage());
		assertFalse(result.getMessage().contains("exceeding"), result.getMessage());
		assertFalse(result.getMessage().contains("Prefix"), result.getMessage());
		assertEquals(0, Currency.of(100).compareTo(user.getEconomy().getAmount()), "wallet untouched");
		assertEquals(0, Currency.of(500).compareTo(user.getBounty().getAmount()), "bounty untouched");
	}

	@ParameterizedTest
	@ValueSource(strings = {"clear", "pay"})
	@DisplayName("no bounty is a configured refusal from execute, not a failed precondition")
	void noBounty_refusesWithConfiguredText(String content) {
		fund(1000);

		assertTrue(aspect.canExecute(player, sign(content)),
		           "canExecute must not gate on hasBounty: that shows the generic 'Might be missing something!'");

		AspectResult result = aspect.execute(player, sign(content));

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains("NO-BOUNTY-SELF-STUB"), result.getMessage());
		assertFalse(result.getMessage().contains("Prefix"), result.getMessage());
		assertEquals(0, Currency.of(1000).compareTo(user.getEconomy().getAmount()), "wallet untouched");
	}

	private void fund(int amount) {
		user.getEconomy().setAmount(Currency.of(amount));
	}

	/** {@code posted} escrowed by a console poster (key {@code console}), plus {@code notoriety} on top. */
	private void bounty(int posted, int notoriety) {
		org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);

		user.getBounty().addBounty(console, Currency.of(posted));
		if (notoriety > 0) user.getBounty().addNotoriety(Currency.of(notoriety));
	}

	private static ParsedSign sign(String content) {
		ParsedSign sign = mock(ParsedSign.class);
		when(sign.getContent()).thenReturn(content);
		return sign;
	}

}
