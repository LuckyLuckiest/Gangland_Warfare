package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.mockito.Mockito.*;

/**
 * GI 7: {@code /glw bank deposit/withdraw <amount> <player>} run by an admin on someone else's bank account used
 * to message only the target - the admin got no confirmation at all.
 */
@DisplayName("BankDepositCommand/BankWithdrawCommand target branch - sender confirmation on another player's bank")
class BankTargetCommandTest {

	@TempDir
	static Path tempDir;

	private User<Player>    target;
	private Bank             bank;
	private EconomyHandler   bankEconomy;
	private Player           player;
	private CommandSender    sender;
	private GanglandDatabase ganglandDatabase;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Commands.Bank.Money.Player_Add", "deposited to your bank")
				              .withString("Commands.Bank.Money.Target_Add", "deposited to %target%'s bank")
				              .withString("Commands.Bank.Money.Player_Take", "withdrawn from your bank")
				              .withString("Commands.Bank.Money.Target_Take", "withdrawn from %target%'s bank"));
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		target           = mock(User.class);
		bank             = mock(Bank.class);
		bankEconomy      = mock(EconomyHandler.class);
		player           = mock(Player.class);
		sender           = mock(CommandSender.class);
		ganglandDatabase = mock(GanglandDatabase.class);

		when(target.getBank()).thenReturn(bank);
		when(target.getUser()).thenReturn(player);
		when(bank.getEconomy()).thenReturn(bankEconomy);
		when(bankEconomy.getAmount()).thenReturn(new BigDecimal("1000"));
		when(player.getName()).thenReturn("Bob");

		RepositoryRegistry registry = mock(RepositoryRegistry.class);
		IRepository<Bank>  bankRepo = mock(IRepository.class);
		when(ganglandDatabase.getRepositoryRegistry()).thenReturn(registry);
		when(registry.getRepository(Bank.class)).thenReturn(bankRepo);
	}

	@Test
	@DisplayName("an admin depositing into ANOTHER player's bank also gets a sender-facing confirmation")
	void applyDepositTarget_adminOnOther_alsoNotifiesSender() {
		BankDepositCommand.applyDepositTarget(sender, "500", target, ganglandDatabase);

		verify(player).sendMessage(contains("deposited to your bank"));
		verify(sender).sendMessage(contains("deposited to"));
	}

	@Test
	@DisplayName("depositing into your OWN bank sends only the one player-facing message")
	void applyDepositTarget_self_sendsNoDuplicateMessage() {
		BankDepositCommand.applyDepositTarget(player, "500", target, ganglandDatabase);

		verify(player, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("an admin withdrawing from ANOTHER player's bank also gets a sender-facing confirmation")
	void applyWithdrawTarget_adminOnOther_alsoNotifiesSender() {
		BankWithdrawCommand.applyWithdrawTarget(sender, "300", target, ganglandDatabase);

		verify(player).sendMessage(contains("withdrawn from your bank"));
		verify(sender).sendMessage(contains("withdrawn from"));
	}

	@Test
	@DisplayName("withdrawing from your OWN bank sends only the one player-facing message")
	void applyWithdrawTarget_self_sendsNoDuplicateMessage() {
		BankWithdrawCommand.applyWithdrawTarget(player, "300", target, ganglandDatabase);

		verify(player, times(1)).sendMessage(anyString());
	}

}
