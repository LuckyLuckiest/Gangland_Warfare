package org.luckyraven.gangland.database.repositories.player;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(Bank.class)
public class BankRepository extends AbstractRepository<Bank> {

	private final BankTable bankTable;

	public BankRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		UserTable userTable = new UserTable();
		this.bankTable = new BankTable(userTable);
	}

	private static Instant parseInstant(Object raw) {
		if (raw == null) return null;
		try {
			return Instant.parse(String.valueOf(raw));
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

	@Override
	protected Collection<Bank> doLoadAll() throws SQLException {
		List<Bank>     banks = new ArrayList<>();
		List<Object[]> data  = tableBackend().selectAll();

		for (Object[] result : data) {
			int        v           = 0;
			UUID       uuid        = UUID.fromString(String.valueOf(result[v++]));
			String     name        = String.valueOf(result[v++]);
			BigDecimal bankBalance = Currency.ofYaml(result[v++]);

			Object rawTier        = result[v++];
			String tierId         = rawTier == null ? null : String.valueOf(rawTier);
			double depositedToday = result[v] == null ? 0D : ((Number) result[v]).doubleValue();
			v++;
			Object rawReset = result[v++];

			Instant resetAt = parseInstant(rawReset);

			Object  rawInterest  = v < result.length ? result[v++] : null;
			Instant lastInterest = parseInstant(rawInterest);

			Object  rawWeekly = v < result.length ? result[v++] : null;
			Instant weeklyAt  = parseInstant(rawWeekly);

			Object  rawMonthly = v < result.length ? result[v] : null;
			Instant monthlyAt  = parseInstant(rawMonthly);

			Bank bank = new Bank(uuid, name);
			bank.getEconomy().setAmount(bankBalance);
			bank.setTierId(tierId);
			bank.setDepositedToday(depositedToday);
			bank.setCapResetAt(resetAt);
			bank.setLastInterestAt(lastInterest);
			bank.setLastWeeklyLoanAt(weeklyAt);
			bank.setLastMonthlyLoanAt(monthlyAt);

			banks.add(bank);
		}

		return banks;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Bank> getTable() {
		return bankTable;
	}

	@Override
	protected void doDelete(Bank data) throws SQLException {
		tableBackend().delete("uuid = ?", data.getUuid().toString());
	}
}
