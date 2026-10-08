package org.luckyraven.gangland.turf.database;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;
import org.luckyraven.gangland.turf.powerups.ActiveBuffRepositoryContract;
import org.luckyraven.gangland.turf.powerups.ActiveTurfBuff;
import org.luckyraven.gangland.turf.powerups.EffectType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@CustomLog
@Repository(ActiveTurfBuff.class)
public class ActiveTurfBuffRepository extends AbstractRepository<ActiveTurfBuff>
		implements ActiveBuffRepositoryContract {

	private final JavaPlugin          plugin;
	private final ActiveTurfBuffTable table;

	public ActiveTurfBuffRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.plugin = plugin;
		this.table  = new ActiveTurfBuffTable();
	}

	@Override
	protected Collection<ActiveTurfBuff> doLoadAll() throws SQLException {
		List<ActiveTurfBuff> buffs = new ArrayList<>();
		List<Object[]>       rows  = tableBackend().selectAll();
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

	/**
	 * Same comparison as {@link ActiveTurfBuff#isExpired(long)} ({@code now >= expiresAt}), so SQL and memory agree.
	 */
	@Override
	public void deleteExpired(long now) {
		Runnable work = () -> {
			try {
				tableBackend().delete("expires_at <= ?", now);
			} catch (SQLException exception) {
				log.warn("Could not prune expired turf buffs: {}", exception.getMessage());
			}
		};
		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, work);
		} else {
			work.run();
		}
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
		tableBackend().delete("id = ?", data.getId());
	}
}
