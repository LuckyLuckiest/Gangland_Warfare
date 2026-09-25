package org.luckyraven.gangland.command.sub.economy;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GI 2 / US-30: {@code applyDeposit}/{@code applyWithdraw} used to parse with raw {@code Currency.parse}, which
 * accepts {@code "-500"}, then fed it straight into the absolute {@code EconomyHandler.setAmount} - a negative
 * deposit credited cash, a negative withdrawal credited the bank. Both now route through {@link
 * org.luckyraven.gangland.command.util.ParsedAmount}, the same seam {@code BankDepositCommand}/{@code
 * BankWithdrawCommand} already use.
 */
@DisplayName("EconomyDepositCommand/EconomyWithdrawCommand - non-positive amount guard")
class EconomyMoneyCommandTest {

	@TempDir
	static Path tempDir;

	private User<Player>   target;
	private EconomyHandler economy;
	private Player         player;
	private CommandSender  sender;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Economy.Cannot_Take_Less_Than_Zero", "less than zero")
				              .withString("Commands.Economy.Money.Player.Add", "added to your account")
				              .withString("Commands.Economy.Money.Target.Add", "added to %target%'s account")
				              .withString("Commands.Economy.Money.Player.Take", "taken from your account")
				              .withString("Commands.Economy.Money.Target.Take", "taken from %target%'s account"));
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		target  = mock(User.class);
		economy = mock(EconomyHandler.class);
		player  = mock(Player.class);
		sender  = mock(CommandSender.class);

		when(target.getEconomy()).thenReturn(economy);
		when(target.getUser()).thenReturn(player);
		when(economy.getAmount()).thenReturn(new BigDecimal("1000"));
		when(player.getName()).thenReturn("Bob");
	}

	@ParameterizedTest(name = "deposit of {0} is refused and moves no money")
	@ValueSource(strings = {"-500", "0"})
	@DisplayName("US-30: a zero or negative deposit is refused before setAmount is called")
	void applyDeposit_nonPositiveAmount_refused(String rawAmount) {
		EconomyDepositCommand.applyDeposit(sender, rawAmount, target);

		verify(economy, never()).setAmount(any());
		verify(sender).sendMessage(contains("less than zero"));
		verify(player, never()).sendMessage(anyString());
	}

	@ParameterizedTest(name = "withdraw of {0} is refused and moves no money")
	@ValueSource(strings = {"-300", "0"})
	@DisplayName("US-30: a zero or negative withdrawal is refused before setAmount is called")
	void applyWithdraw_nonPositiveAmount_refused(String rawAmount) {
		EconomyWithdrawCommand.applyWithdraw(sender, rawAmount, target);

		verify(economy, never()).setAmount(any());
		verify(sender).sendMessage(contains("less than zero"));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("GI 7: an admin depositing into ANOTHER player's account also gets a sender-facing confirmation")
	void applyDeposit_adminOnOtherTarget_alsoNotifiesSender() {
		EconomyDepositCommand.applyDeposit(sender, "500", target);

		verify(player).sendMessage(contains("added to your account"));
		verify(sender).sendMessage(contains("added to") /* target-facing message reaches the admin too */);
	}

	@Test
	@DisplayName("GI 7: depositing into your OWN account sends only the one player-facing message, not a duplicate")
	void applyDeposit_selfTarget_sendsNoDuplicateMessage() {
		// self-use: EconomyCommand.resolveTarget returns userManager.getUser(sender) when no target arg is given,
		// so target.getUser() IS the sender's own Player.
		EconomyDepositCommand.applyDeposit(player, "500", target);

		verify(player, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("GI 7: an admin withdrawing from ANOTHER player's account also gets a sender-facing confirmation")
	void applyWithdraw_adminOnOtherTarget_alsoNotifiesSender() {
		EconomyWithdrawCommand.applyWithdraw(sender, "300", target);

		verify(player).sendMessage(contains("taken from your account"));
		verify(sender).sendMessage(contains("taken from"));
	}

	@Test
	@DisplayName("GI 7: withdrawing from your OWN account sends only the one player-facing message, not a duplicate")
	void applyWithdraw_selfTarget_sendsNoDuplicateMessage() {
		EconomyWithdrawCommand.applyWithdraw(player, "300", target);

		verify(player, times(1)).sendMessage(anyString());
	}

}
