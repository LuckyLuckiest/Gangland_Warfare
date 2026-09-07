package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.bank.Bank;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@code BankCommand.processMoney}, the shared money-move helper behind
 * {@code /glw bank deposit} and {@code /glw bank withdraw}.
 *
 * <p>Regression net for US-03 (Observation #3, users-levels-economy-bank.md). {@code processMoney} only ever
 * guarded the <em>source</em> balance ({@code check.signum() == 0}) and the "more than you have" case
 * ({@code amount.compareTo(check) > 0}) — a negative {@code amount} passed both, and the balances were then
 * written with {@code setAmount}, so {@code deposit -1000} added 1000 to cash and {@code withdraw -1000}
 * added 1000 to the bank. Commands now parse through {@code ParsedAmount}; this guard is the second line of
 * defence and the seam these tests drive.
 *
 * <p>Lives in the production package so it can reach the package-private static.
 */
@DisplayName("BankCommand.processMoney — money-move guards")
class BankCommandTest {

	@TempDir
	static Path tempDir;

	private User<Player>   user;
	private Bank           bank;
	private EconomyHandler userEconomy;
	private EconomyHandler bankEconomy;
	private Player         player;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Economy.Cannot_Take_Less_Than_Zero", "less than zero")
				              .withString("Errors.Economy.Cannot_Take_More_Than_Balance", "more than balance"));
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		user        = mock(User.class);
		bank        = mock(Bank.class);
		userEconomy = mock(EconomyHandler.class);
		bankEconomy = mock(EconomyHandler.class);
		player      = mock(Player.class);

		when(user.getUser()).thenReturn(player);
		when(user.getEconomy()).thenReturn(userEconomy);
		when(bank.getEconomy()).thenReturn(bankEconomy);
	}

	@ParameterizedTest(name = "an amount of {0} is refused and moves no money")
	@ValueSource(strings = {"-1000", "-0.01", "0"})
	@DisplayName("US-03: a zero or negative amount is refused before any balance is written")
	void processMoney_nonPositiveAmount_refusedAndMovesNoMoney(String rawAmount) {
		BigDecimal amount  = new BigDecimal(rawAmount);
		BigDecimal balance = new BigDecimal("500");

		// The arithmetic the caller would have handed in: deposit of a negative amount credits cash.
		boolean processed = BankCommand.processMoney(user, bank, balance, amount,
		                                             balance.add(amount), balance.subtract(amount));

		assertFalse(processed, "a non-positive amount must never be processed");
		verify(userEconomy, never()).setAmount(any());
		verify(bankEconomy, never()).setAmount(any());
		verify(player).sendMessage(contains("less than zero"));
	}

	@Test
	@DisplayName("a source balance of exactly zero is still refused")
	void processMoney_zeroSourceBalance_refused() {
		boolean processed = BankCommand.processMoney(user, bank, BigDecimal.ZERO, new BigDecimal("10"),
		                                             new BigDecimal("10"), new BigDecimal("-10"));

		assertFalse(processed);
		verify(userEconomy, never()).setAmount(any());
		verify(bankEconomy, never()).setAmount(any());
	}

	@Test
	@DisplayName("an amount larger than the source balance is refused")
	void processMoney_amountAboveBalance_refused() {
		BigDecimal balance = new BigDecimal("100");

		boolean processed = BankCommand.processMoney(user, bank, balance, new BigDecimal("250"),
		                                             new BigDecimal("250"), new BigDecimal("-150"));

		assertFalse(processed);
		verify(userEconomy, never()).setAmount(any());
		verify(bankEconomy, never()).setAmount(any());
		verify(player).sendMessage(contains("more than balance"));
	}

	@Test
	@DisplayName("a valid deposit writes both balances and reports success")
	void processMoney_validAmount_writesBothBalances() {
		BigDecimal cash    = new BigDecimal("500");
		BigDecimal inBank  = new BigDecimal("200");
		BigDecimal amount  = new BigDecimal("120");
		BigDecimal newBank = inBank.add(amount);
		BigDecimal newCash = cash.subtract(amount);

		boolean processed = BankCommand.processMoney(user, bank, cash, amount, newBank, newCash);

		assertTrue(processed);
		verify(userEconomy).setAmount(argThat(value -> value.compareTo(newCash) == 0));
		verify(bankEconomy).setAmount(argThat(value -> value.compareTo(newBank) == 0));
	}

	@Test
	@DisplayName("an amount exactly equal to the source balance is allowed")
	void processMoney_amountEqualToBalance_allowed() {
		BigDecimal balance = new BigDecimal("75");

		boolean processed = BankCommand.processMoney(user, bank, balance, balance,
		                                             balance, BigDecimal.ZERO);

		assertTrue(processed);
		verify(userEconomy).setAmount(any());
		verify(bankEconomy).setAmount(any());
	}

}
