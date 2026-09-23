package org.luckyraven.gangland.gang.database.repositories.permission;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.database.tables.permission.PermissionTable;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Moved from gangland-impl (T-54, W55): {@code Permission}'s only caller is the gang module's
 * {@code RankManager} ({@code repositoryRegistry.getRepository(Permission.class)}); when the module isn't
 * deployed, nothing ever called {@link #setDataSupplier}, so this repository sat in core's unconditional
 * scan as an orphan and logged "No data supplier set" on every autosave. Scanned now only by
 * {@code GangModule.REPOSITORY_PACKAGE}'s module-loader pass. The {@link Permission} entity itself and the
 * {@code permission} table name are unchanged.
 */
@Repository(Permission.class)
public class PermissionRepository extends AbstractRepository<Permission> {

	private final PermissionTable permissionTable;

	public PermissionRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.permissionTable = new PermissionTable();
	}

	@Override
	protected Collection<Permission> doLoadAll() throws SQLException {
		List<Permission> permissions = new ArrayList<>();
		List<Object[]>   data        = tableBackend().selectAll();

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
		tableBackend().delete("id = ?", data.getUsedId());
	}
}
