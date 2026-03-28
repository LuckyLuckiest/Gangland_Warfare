package me.luckyraven.database.repositories.copsncrooks;

import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.copsncrooks.detainment.DetainmentState;
import me.luckyraven.database.tables.copsncrooks.DetainmentTable;
import me.luckyraven.database.tables.copsncrooks.JailTable;
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
import java.util.UUID;
import java.util.function.Consumer;

@Repository(DetainedPlayer.class)
public class DetainmentRepository extends AbstractRepository<DetainedPlayer> {

	private final DetainmentTable detainmentTable;

	public DetainmentRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		// DetainmentTable depends on JailTable
		// But JailTable will be created by JailRepository first
		// The dependency sorting in RepositoryRegistry handles this
		JailTable jailTable = new JailTable();
		this.detainmentTable = new DetainmentTable(jailTable);
	}

	@Override
	protected Collection<DetainedPlayer> doLoadAll() throws SQLException {
		List<DetainedPlayer> detained = new ArrayList<>();
		List<Object[]>       data     = getDatabase().table(detainmentTable.getName()).selectAll();

		for (Object[] result : data) {
			int v = 0;

			UUID    uuid      = UUID.fromString(String.valueOf(result[v++]));
			Object  rawJailId = result[v++];
			Integer jailId    = rawJailId == null ? null : ((Number) rawJailId).intValue();
			var     state     = DetainmentState.JAILED;

			try {
				state = DetainmentState.valueOf(String.valueOf(result[v]));
			} catch (IllegalArgumentException ignored) { }

			detained.add(new DetainedPlayer(uuid, jailId, state));
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
		Database table = getDatabase().table(detainmentTable.getName());
		table.delete("player_uuid", data.getPlayerId().toString(), Types.VARCHAR);
	}
}
