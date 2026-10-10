package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
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

@Repository(Jail.class)
public class JailRepository extends AbstractRepository<Jail> {

	/** A SQL NULL reads as 0; any other value is read through Number (MySQL may return Long/BigDecimal). */
	private static Number num(Object raw) {
		return raw instanceof Number number ? number : 0;
	}

	private final JailTable jailTable;

	public JailRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		// Table is created here, managed by repository
		this.jailTable = new JailTable();
	}

	@Override
	protected Collection<Jail> doLoadAll() throws SQLException {
		List<Jail>     jails = new ArrayList<>();
		List<Object[]> data  = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			int    id          = num(result[v++]).intValue();
			String worldName   = String.valueOf(result[v++]);
			double x           = num(result[v++]).doubleValue();
			double y           = num(result[v++]).doubleValue();
			double z           = num(result[v++]).doubleValue();
			int    maxCapacity = num(result[v]).intValue();

			// before the unloaded-world skip, and a floor (never lowered): the next created jail must not reuse this id
			JailService.ID = Math.max(JailService.ID, id);

			World world = Bukkit.getWorld(worldName);

			if (world == null) continue;

			Location location = new Location(world, x, y, z);
			jails.add(new Jail(id, location, maxCapacity));
		}

		return jails;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Jail> getTable() {
		return jailTable;
	}

	@Override
	protected void doDelete(Jail data) throws SQLException {
		tableBackend().delete("id = ?", data.getId());
	}
}