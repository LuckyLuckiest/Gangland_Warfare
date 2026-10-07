package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Loads every region row whatever its world. */
@Repository(AdminRegion.class)
public class AdminRegionRepository extends AbstractRepository<AdminRegion> {

	private final AdminRegionTable regionTable;

	public AdminRegionRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.regionTable = new AdminRegionTable();
	}

	@Override
	protected Collection<AdminRegion> doLoadAll() throws SQLException {
		List<AdminRegion> regions = new ArrayList<>();
		List<Object[]>    data    = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			int    id    = ((Number) result[v++]).intValue();
			String name  = String.valueOf(result[v++]);
			String world = String.valueOf(result[v++]);
			int    minX  = ((Number) result[v++]).intValue();
			int    minY  = ((Number) result[v++]).intValue();
			int    minZ  = ((Number) result[v++]).intValue();
			int    maxX  = ((Number) result[v++]).intValue();
			int    maxY  = ((Number) result[v++]).intValue();
			int    maxZ  = ((Number) result[v++]).intValue();
			String raw   = result[v] == null ? "" : String.valueOf(result[v]);

			Set<String> tags = new LinkedHashSet<>(Arrays.asList(raw.split(",")));
			regions.add(new AdminRegion(id, name, world, minX, minY, minZ, maxX, maxY, maxZ, tags));
		}

		return regions;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<AdminRegion> getTable() {
		return regionTable;
	}

	@Override
	protected void doDelete(AdminRegion data) throws SQLException {
		tableBackend().delete("id = ?", data.getId());
	}
}
