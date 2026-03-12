package me.luckyraven.database.repositories.rank;

import me.luckyraven.data.rank.RankPermission;
import me.luckyraven.database.tables.plugin.PermissionTable;
import me.luckyraven.database.tables.rank.RankPermissionTable;
import me.luckyraven.database.tables.rank.RankTable;
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
import java.util.function.Consumer;

@Repository(RankPermission.class)
public class RankPermissionRepository extends AbstractRepository<RankPermission> {

	private final RankPermissionTable rankPermissionTable;

	public RankPermissionRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.rankPermissionTable = new RankPermissionTable(new RankTable(), new PermissionTable());
	}

	@Override
	protected Collection<RankPermission> doLoadAll() throws SQLException {
		List<RankPermission> rankPermissions = new ArrayList<>();
		List<Object[]>       data            = getDatabase().table(rankPermissionTable.getName()).selectAll();

		for (Object[] result : data) {
			int rankId       = (int) result[0];
			int permissionId = (int) result[1];

			rankPermissions.add(new RankPermission(rankId, permissionId));
		}

		return rankPermissions;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<RankPermission> getTable() {
		return rankPermissionTable;
	}

	@Override
	protected void doDelete(RankPermission data) throws SQLException {
		Database table = getDatabase().table(rankPermissionTable.getName());
		table.delete("rank_id", data.rankId(), Types.INTEGER);
	}
}
