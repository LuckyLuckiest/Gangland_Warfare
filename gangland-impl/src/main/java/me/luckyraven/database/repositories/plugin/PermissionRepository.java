package me.luckyraven.database.repositories.plugin;

import me.luckyraven.data.rank.Permission;
import me.luckyraven.database.tables.plugin.PermissionTable;
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
		List<Object[]>   data        = getDatabase().table(permissionTable.getName()).selectAll();

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
