package org.luckyraven.gangland.command.sub.economy;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * GI 7: {@code /glw eco set/reset <amount> <player>} run by an admin on someone else's account used to message
 * only the target - the sender (e.g. Alice running {@code /glw eco set 10000 Bob}) got no confirmation at all.
 */
@DisplayName("EconomySetCommand/EconomyResetCommand - sender confirmation on another player's account")
class EconomySetResetCommandTest {

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
				              .withString("Commands.Economy.Money.Player.Set", "your balance has been set")
				              .withString("Commands.Economy.Money.Target.Set", "%target%'s balance has been set")
				              .withString("Commands.Economy.Money.Player.Reset", "your account has been reset")
				              .withString("Commands.Economy.Money.Target.Reset", "%target%'s account has been reset"));
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

	@Test
	@DisplayName("an admin setting ANOTHER player's balance also gets a sender-facing confirmation")
	void applySet_adminOnOtherTarget_alsoNotifiesSender() {
		EconomySetCommand.applySet(sender, "500", target);

		verify(player).sendMessage(contains("your balance has been set"));
		verify(sender).sendMessage(contains("balance has been set"));
	}

	@Test
	@DisplayName("setting your OWN balance sends only the one player-facing message")
	void applySet_selfTarget_sendsNoDuplicateMessage() {
		EconomySetCommand.applySet(player, "500", target);

		verify(player, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("an admin resetting ANOTHER player's balance also gets a sender-facing confirmation")
	void applyReset_adminOnOtherTarget_alsoNotifiesSender() {
		EconomyResetCommand.applyReset(sender, target);

		verify(economy).setAmount(any());
		verify(player).sendMessage(contains("your account has been reset"));
		verify(sender).sendMessage(contains("account has been reset"));
	}

	@Test
	@DisplayName("resetting your OWN balance sends only the one player-facing message")
	void applyReset_selfTarget_sendsNoDuplicateMessage() {
		EconomyResetCommand.applyReset(player, target);

		verify(player, times(1)).sendMessage(anyString());
	}

}
