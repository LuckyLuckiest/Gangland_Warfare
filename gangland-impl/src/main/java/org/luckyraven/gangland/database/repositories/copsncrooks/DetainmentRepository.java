package org.luckyraven.gangland.database.repositories.copsncrooks;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.database.tables.copsncrooks.DetainmentTable;
import org.luckyraven.gangland.database.tables.copsncrooks.JailTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(DetainedPlayer.class)
public class DetainmentRepository extends AbstractRepository<DetainedPlayer> {

	private final DetainmentTable detainmentTable;

	public DetainmentRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		// DetainmentTable depends on JailTable
		// But JailTable will be created by JailRepository first
		// The dependency sorting in RepositoryRegistry handles this
		JailTable jailTable = new JailTable();
		this.detainmentTable = new DetainmentTable(jailTable);
	}

	@Override
	protected Collection<DetainedPlayer> doLoadAll() throws SQLException {
		List<DetainedPlayer> detained = new ArrayList<>();
		List<Object[]>       data     = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			UUID    uuid      = UUID.fromString(String.valueOf(result[v++]));
			Object  rawJailId = result[v++];
			Integer jailId    = rawJailId == null ? null : ((Number) rawJailId).intValue();
			var     state     = DetainmentState.JAILED;

			try {
				state = DetainmentState.valueOf(String.valueOf(result[v++]));
			} catch (IllegalArgumentException ignored) { v++; }

			Object  rawTransit  = v < result.length ? result[v++] : null;
			Object  rawSentence = v < result.length ? result[v++] : null;
			Object  rawWanted   = v < result.length ? result[v] : null;
			Long    transitAt   = rawTransit == null ? null : ((Number) rawTransit).longValue();
			Long    sentenceAt  = rawSentence == null ? null : ((Number) rawSentence).longValue();
			Integer wantedLevel = rawWanted == null ? null : ((Number) rawWanted).intValue();

			detained.add(new DetainedPlayer(uuid, jailId, state, transitAt, sentenceAt, wantedLevel));
		}

		return detained;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<DetainedPlayer> getTable() {
		return detainmentTable;
	}

	@Override
	protected void doDelete(DetainedPlayer data) throws SQLException {
		tableBackend().delete("player_uuid = ?", data.getPlayerId().toString());
	}
}
