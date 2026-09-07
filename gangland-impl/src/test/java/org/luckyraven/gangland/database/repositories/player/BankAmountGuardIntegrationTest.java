package org.luckyraven.gangland.database.repositories.player;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.sub.bank.BankCommand;
import org.luckyraven.gangland.command.util.ParsedAmount;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for US-03 across the whole write path a bank command actually takes: parse the typed
 * amount, run the money-move guard, then persist through the real {@code BankRepository} on a real SQLite
 * database via the DatabaseBackend SPI, and read the row back.
 *
 * <p>The unit tests prove {@code ParsedAmount} and {@code BankCommand.processMoney} refuse a negative amount
 * in isolation. This one proves the refusal actually protects the stored balance: before the fix,
 * {@code /glw bank deposit -1000} wrote an inflated cash balance and a deflated bank balance, and
 * {@code BankDepositCommand} immediately called {@code repo.save(bank)} — so the minted money survived a
 * restart. Here the same sequence must leave the persisted row untouched.
 *
 * <p>Follows the {@code RankRepositorySpiTest} skeleton and the Windows SQLite rules in
 * {@code documentation/TESTING.md} section 5: a disabled {@code PluginMocks} plugin so repository writes run
 * inline, {@code CleanupMode.NEVER}, and {@code disconnect()} before {@code DbFiles.release}.
 */
@DisplayName("Bank amount guard — end to end through SQLite persistence")
class BankAmountGuardIntegrationTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private SqliteBackend  backend;
	private BankRepository repository;

	private UUID           uuid;
	private Bank           bank;
	private User<Player>   user;
	private EconomyHandler userEconomy;

	@BeforeAll
	static void initStatics(@TempDir Path staticDir) {
		SettingsFixture.initializeMinimal(staticDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Economy.Cannot_Take_Less_Than_Zero", "less than zero")
				              .withString("Errors.Economy.Cannot_Take_More_Than_Balance", "more than balance"));
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("bank.db")));

		UserTable userTable = new UserTable();
		backend.applySchema(TableSchemas.fromTable(userTable));
		backend.applySchema(TableSchemas.fromTable(new BankTable(userTable)));

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		repository = new BankRepository(plugin, mock(DatabaseHandler.class), backend);

		uuid = UUID.randomUUID();
		bank = new Bank(uuid, "Vault");
		bank.getEconomy().setAmount(Currency.of(new BigDecimal("500")));
		repository.save(bank);

		userEconomy = mock(EconomyHandler.class);
		when(userEconomy.getAmount()).thenReturn(Currency.of(new BigDecimal("500")));

		user = mock(User.class);
		when(user.getUser()).thenReturn(mock(Player.class));
		when(user.getEconomy()).thenReturn(userEconomy);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("the seeded balance round-trips, so the assertions below read a real persisted value")
	void seededBalance_roundTrips() {
		assertEquals(0, new BigDecimal("500").compareTo(persistedBalance()));
	}

	@Test
	@DisplayName("US-03: a negative deposit is rejected at parse time and never reaches the database")
	void negativeDeposit_rejectedAtParse_leavesStoredBalanceUntouched() {
		ParsedAmount parsed = ParsedAmount.of("-1000");

		assertFalse(parsed.isValid(), "the command aborts here, before touching any balance");
		assertEquals(ParsedAmount.Failure.NOT_POSITIVE, parsed.failure());

		// The command returns without calling processMoney or repo.save at all.
		assertEquals(0, new BigDecimal("500").compareTo(persistedBalance()));
	}

	@Test
	@DisplayName("US-03: even if a negative amount reached processMoney, the save that follows persists no change")
	void negativeDeposit_reachingProcessMoney_persistsNoChange() {
		BigDecimal cash   = new BigDecimal("500");
		BigDecimal amount = new BigDecimal("-1000");
		BigDecimal inBank = bank.getEconomy().getAmount().add(amount);

		boolean processed = BankCommand.processMoney(user, bank, cash, amount, inBank, cash.subtract(amount));

		assertFalse(processed, "the guard must refuse before any setAmount call");

		// BankDepositCommand only saves when processMoney returned true; save anyway to prove the in-memory
		// Bank was never mutated either.
		repository.save(bank);

		assertEquals(0, new BigDecimal("500").compareTo(persistedBalance()),
		             "a negative deposit must not be able to mint money into the stored row");
	}

	@Test
	@DisplayName("a valid deposit moves the money and the new balance survives a reload")
	void validDeposit_persistsNewBalance() {
		ParsedAmount parsed = ParsedAmount.of("200");
		assertTrue(parsed.isValid());

		BigDecimal amount = parsed.require();
		BigDecimal cash   = new BigDecimal("500");
		BigDecimal inBank = bank.getEconomy().getAmount().add(amount);

		boolean processed = BankCommand.processMoney(user, bank, cash, amount, inBank, cash.subtract(amount));

		assertTrue(processed);
		repository.save(bank);

		assertEquals(0, new BigDecimal("700").compareTo(persistedBalance()));
	}

	@Test
	@DisplayName("a negative withdrawal cannot inflate the bank row either")
	void negativeWithdrawal_persistsNoChange() {
		BigDecimal bankBal = bank.getEconomy().getAmount();
		BigDecimal amount  = new BigDecimal("-750");

		boolean processed = BankCommand.processMoney(user, bank, bankBal, amount,
		                                                bankBal.subtract(amount),
		                                                new BigDecimal("500").add(amount));

		assertFalse(processed);
		repository.save(bank);

		assertEquals(0, new BigDecimal("500").compareTo(persistedBalance()));
	}

	private BigDecimal persistedBalance() {
		Collection<Bank> loaded = repository.loadAll();
		Bank stored = loaded.stream()
				.filter(candidate -> candidate.getUuid().equals(uuid))
				.findFirst()
				.orElseThrow(() -> new AssertionError("bank row was not persisted"));

		return stored.getEconomy().getAmount();
	}

}
