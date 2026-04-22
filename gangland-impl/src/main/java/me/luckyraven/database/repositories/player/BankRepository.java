package me.luckyraven.database.repositories.player;

import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.economy.Currency;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
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

	public BankRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

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
		List<Object[]> data  = bankTable.selectAllTableQuery(getDatabase());

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
		Database table = getDatabase().table(bankTable.getName());
		table.delete("uuid", data.getUuid().toString(), Types.VARCHAR);
	}
}
