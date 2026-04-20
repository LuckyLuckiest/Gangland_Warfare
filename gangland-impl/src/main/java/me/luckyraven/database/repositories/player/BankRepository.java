package me.luckyraven.database.repositories.player;

import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

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

	@Override
	protected Collection<Bank> doLoadAll() throws SQLException {
		List<Bank>     banks = new ArrayList<>();
		List<Object[]> data  = bankTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int    v           = 0;
			UUID   uuid        = UUID.fromString(String.valueOf(result[v++]));
			String name        = String.valueOf(result[v++]);
			double bankBalance = ((Number) result[v++]).doubleValue();

			Object rawTier        = result[v++];
			String tierId         = rawTier == null ? null : String.valueOf(rawTier);
			double depositedToday = result[v] == null ? 0D : ((Number) result[v]).doubleValue();
			v++;
			double withdrawnToday = result[v] == null ? 0D : ((Number) result[v]).doubleValue();
			v++;
			Object rawReset = result[v];

			Instant resetAt = null;
			if (rawReset != null) {
				try {
					resetAt = Instant.parse(String.valueOf(rawReset));
				} catch (DateTimeParseException ignored) { }
			}

			Bank bank = new Bank(uuid, name);
			bank.getEconomy().setBalance(bankBalance);
			bank.setTierId(tierId);
			bank.setDepositedToday(depositedToday);
			bank.setWithdrawnToday(withdrawnToday);
			bank.setCapResetAt(resetAt);

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
