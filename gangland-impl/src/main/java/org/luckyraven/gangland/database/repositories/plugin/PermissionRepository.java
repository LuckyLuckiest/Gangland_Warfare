package org.luckyraven.gangland.database.repositories.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.plugin.PermissionTable;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.repository.AbstractRepository;
import org.luckyraven.gangland.persistence.repository.Repository;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Permission.class)
public class PermissionRepository extends AbstractRepository<Permission> {

	private final PermissionTable permissionTable;

	public PermissionRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.permissionTable = new PermissionTable();
	}

	@Override
	protected Collection<Permission> doLoadAll() throws SQLException {
		List<Permission> permissions = new ArrayList<>();
		List<Object[]>   data        = permissionTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int    id         = (int) result[0];
			String permission = String.valueOf(result[1]);

			permissions.add(new Permission(id, permission));
		}

		return permissions;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Permission> getTable() {
		return permissionTable;
	}

	@Override
	protected void doDelete(Permission data) throws SQLException {
		Database table = getDatabase().table(permissionTable.getName());
		table.delete("id", data.getUsedId(), Types.INTEGER);
	}
}
