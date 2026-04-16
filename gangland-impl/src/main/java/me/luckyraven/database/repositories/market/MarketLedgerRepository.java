package me.luckyraven.database.repositories.market;

import me.luckyraven.database.tables.market.MarketLedgerTable;
import me.luckyraven.market.contract.MarketLedgerRepositoryContract;
import me.luckyraven.market.ledger.LedgerQuery;
import me.luckyraven.market.ledger.TransactionDirection;
import me.luckyraven.market.ledger.TransactionRecord;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Repository(TransactionRecord.class)
public class MarketLedgerRepository extends AbstractRepository<TransactionRecord> implements
		MarketLedgerRepositoryContract {

	private final MarketLedgerTable table;

	public MarketLedgerRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);
		this.table = new MarketLedgerTable();
	}

	private static boolean matchesQuery(TransactionRecord record, LedgerQuery query) {
		if (!query.includeBlackMarket() && !record.marketLinked()) {
			return false;
		}
		if (query.playerId() != null && !query.playerId().equals(record.playerId())) {
			return false;
		}
		if (query.itemId() != null && !query.itemId().equals(record.itemId())) {
			return false;
		}
		if (query.from() != null && record.timestamp().isBefore(query.from())) {
			return false;
		}
		if (query.to() != null && record.timestamp().isAfter(query.to())) {
			return false;
		}
		return true;
	}

	private static boolean toBool(Object raw) {
		if (raw instanceof Boolean b) {
			return b;
		}
		if (raw instanceof Number n) {
			return n.intValue() != 0;
		}
		return Boolean.parseBoolean(String.valueOf(raw));
	}

	@Override
	public void append(TransactionRecord record) {
		save(record);
	}

	@Override
	public List<TransactionRecord> query(LedgerQuery query) {
		// MVP: load all + filter. Transactions accumulate forever so this will need a WHERE-clause optimisation
		// once server ledgers grow — but it is correct and unblocks the admin GUI + price engine.
		List<TransactionRecord> matches = new ArrayList<>();
		for (TransactionRecord record : safeLoad()) {
			if (!matchesQuery(record, query)) {
				continue;
			}
			matches.add(record);
		}
		matches.sort(Comparator.comparing(TransactionRecord::timestamp).reversed());

		int from = Math.min(query.offset(), matches.size());
		int to   = query.limit() <= 0 ? matches.size() : Math.min(from + query.limit(), matches.size());
		return new ArrayList<>(matches.subList(from, to));
	}

	@Override
	public List<TransactionRecord> recentForPricing(String itemId, Instant since) {
		List<TransactionRecord> matches = new ArrayList<>();
		for (TransactionRecord record : safeLoad()) {
			if (!record.marketLinked()) {
				continue;
			}
			if (!record.itemId().equals(itemId)) {
				continue;
			}
			if (record.timestamp().isBefore(since)) {
				continue;
			}
			matches.add(record);
		}
		return matches;
	}

	@Override
	protected Collection<TransactionRecord> doLoadAll() throws SQLException {
		List<TransactionRecord> records = new ArrayList<>();
		List<Object[]>          rows    = table.selectAllTableQuery(getDatabase());

		for (Object[] row : rows) {
			int     v            = 0;
			UUID    txId         = UUID.fromString(String.valueOf(row[v++]));
			UUID    playerId     = UUID.fromString(String.valueOf(row[v++]));
			String  traderStr    = String.valueOf(row[v++]);
			UUID    traderId     = (traderStr == null || traderStr.isEmpty()) ? null : UUID.fromString(traderStr);
			String  itemId       = String.valueOf(row[v++]);
			int     quantity     = ((Number) row[v++]).intValue();
			double  unitPrice    = ((Number) row[v++]).doubleValue();
			double  total        = ((Number) row[v++]).doubleValue();
			String  direction    = String.valueOf(row[v++]);
			boolean marketLinked = toBool(row[v++]);
			long    timestamp    = ((Number) row[v]).longValue();

			records.add(new TransactionRecord(txId, playerId, traderId, itemId, quantity, unitPrice, total,
			                                  TransactionDirection.valueOf(direction), marketLinked,
			                                  Instant.ofEpochMilli(timestamp)));
		}
		return records;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<TransactionRecord> getTable() {
		return table;
	}

	@Override
	protected void doDelete(TransactionRecord data) throws SQLException {
		Database db = getDatabase().table(table.getName());
		db.delete("tx_id", data.txId().toString(), Types.VARCHAR);
	}

	private Collection<TransactionRecord> safeLoad() {
		try {
			return doLoadAll();
		} catch (SQLException e) {
			return List.of();
		}
	}
}
