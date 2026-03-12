package me.luckyraven.database.repositories.copsncrooks;

import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.database.tables.copsncrooks.JailTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Jail.class)
public class JailRepository extends AbstractRepository<Jail> {

	private final JailTable jailTable;

	public JailRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		// Table is created here, managed by repository
		this.jailTable = new JailTable();
	}

	@Override
	protected Collection<Jail> doLoadAll() throws SQLException {
		List<Jail>     jails = new ArrayList<>();
		List<Object[]> data  = getDatabase().table(jailTable.getName()).selectAll();

		for (Object[] result : data) {
			int v = 0;

			int    id        = (int) result[v++];
			String worldName = String.valueOf(result[v++]);
			double x         = (double) result[v++];
			double y         = (double) result[v++];
			double z         = (double) result[v];

			World world = Bukkit.getWorld(worldName);

			if (world == null) continue;

			Location location = new Location(world, x, y, z);
			jails.add(new Jail(id, location));
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
		Database table = getDatabase().table(jailTable.getName());
		table.delete("id", data.getId(), Types.INTEGER);
	}
}