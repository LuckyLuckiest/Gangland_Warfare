package me.luckyraven.database.repositories.turf;

import me.luckyraven.database.tables.turf.ActiveTurfBuffTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import me.luckyraven.turf.powerups.ActiveBuffRepositoryContract;
import me.luckyraven.turf.powerups.ActiveTurfBuff;
import me.luckyraven.turf.powerups.EffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(ActiveTurfBuff.class)
public class ActiveTurfBuffRepository extends AbstractRepository<ActiveTurfBuff>
		implements ActiveBuffRepositoryContract {

	private final ActiveTurfBuffTable table;

	public ActiveTurfBuffRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.table = new ActiveTurfBuffTable();
	}

	@Override
	protected Collection<ActiveTurfBuff> doLoadAll() throws SQLException {
		List<ActiveTurfBuff> buffs = new ArrayList<>();
		List<Object[]>       rows  = table.selectAllTableQuery(getDatabase());
		for (Object[] result : rows) {
			int    v          = 0;
			long   id         = ((Number) result[v++]).longValue();
			int    turfId     = ((Number) result[v++]).intValue();
			String powerupId  = String.valueOf(result[v++]);
			String effectName = String.valueOf(result[v++]);
			double magnitude  = ((Number) result[v++]).doubleValue();
			long   expiresAt  = ((Number) result[v]).longValue();

			EffectType effectType;
			try {
				effectType = EffectType.valueOf(effectName);
			} catch (IllegalArgumentException exception) {
				// Stored type no longer exists in code (catalogue refactor) — drop the row on next save.
				continue;
			}
			buffs.add(new ActiveTurfBuff(id, turfId, powerupId, effectType, magnitude, expiresAt));
		}
		return buffs;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<ActiveTurfBuff> getTable() {
		return table;
	}

	@Override
	protected void doDelete(ActiveTurfBuff data) throws SQLException {
		Database db = getDatabase().table(table.getName());
		db.delete("id", data.getId(), Types.BIGINT);
	}
}
