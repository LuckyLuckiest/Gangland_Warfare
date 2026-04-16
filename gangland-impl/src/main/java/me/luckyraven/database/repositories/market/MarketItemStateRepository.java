package me.luckyraven.database.repositories.market;

import me.luckyraven.database.tables.market.MarketItemStateTable;
import me.luckyraven.market.contract.MarketItemStateRepositoryContract;
import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Repository(MarketItemState.class)
public class MarketItemStateRepository extends AbstractRepository<MarketItemState> implements
		MarketItemStateRepositoryContract {

	private final MarketItemStateTable table;

	public MarketItemStateRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);
		this.table = new MarketItemStateTable();
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
	public void upsert(MarketItemState state) {
		save(state);
	}

	@Override
	public Optional<MarketItemState> find(String itemId) {
		for (MarketItemState state : loadAll()) {
			if (state.getItemId().equals(itemId)) {
				return Optional.of(state);
			}
		}
		return Optional.empty();
	}

	@Override
	protected Collection<MarketItemState> doLoadAll() throws SQLException {
		List<MarketItemState> states = new ArrayList<>();
		List<Object[]>        rows   = table.selectAllTableQuery(getDatabase());

		for (Object[] row : rows) {
			int     v             = 0;
			String  itemId        = String.valueOf(row[v++]);
			double  currentPrice  = ((Number) row[v++]).doubleValue();
			double  basePrice     = ((Number) row[v++]).doubleValue();
			double  volatility    = ((Number) row[v++]).doubleValue();
			double  elasticity    = ((Number) row[v++]).doubleValue();
			boolean frozen        = toBool(row[v++]);
			boolean overridden    = toBool(row[v++]);
			double  overridePrice = ((Number) row[v++]).doubleValue();
			long    lastUpdated   = ((Number) row[v]).longValue();

			MarketItemState state = new MarketItemState(itemId, basePrice, volatility, elasticity);
			state.setCurrentPrice(currentPrice);
			state.setFrozen(frozen);
			state.setOverridePrice(overridden ? overridePrice : null);
			state.setLastUpdatedMillis(lastUpdated);
			states.add(state);
		}
		return states;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<MarketItemState> getTable() {
		return table;
	}

	@Override
	protected void doDelete(MarketItemState data) throws SQLException {
		Database db = getDatabase().table(table.getName());
		db.delete("item_id", data.getItemId(), Types.VARCHAR);
	}
}
