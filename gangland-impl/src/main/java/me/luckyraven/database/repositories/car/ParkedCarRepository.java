package me.luckyraven.database.repositories.car;

import me.luckyraven.database.tables.car.ParkedCarTable;
import me.luckyraven.gadget.car.ParkedCar;
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

@Repository(ParkedCar.class)
public class ParkedCarRepository extends AbstractRepository<ParkedCar> {

	private final ParkedCarTable parkedCarTable;

	public ParkedCarRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);
		this.parkedCarTable = new ParkedCarTable();
	}

	@Override
	protected Collection<ParkedCar> doLoadAll() throws SQLException {
		List<ParkedCar> cars = new ArrayList<>();
		List<Object[]>  data = getDatabase().table(parkedCarTable.getName()).selectAll();

		for (Object[] result : data) {
			int    v          = 0;
			String dbId       = String.valueOf(result[v++]);
			String carId      = String.valueOf(result[v++]);
			String world      = String.valueOf(result[v++]);
			double x          = (double) result[v++];
			double y          = (double) result[v++];
			double z          = (double) result[v++];
			float  yaw        = (float) (double) result[v++];
			int    fuel       = (int) result[v++];
			int    durability = (int) result[v++];
			Object placerRaw  = result[v];

			UUID placerUUID = null;
			if (placerRaw != null) {
				String placerStr = placerRaw.toString();
				if (!placerStr.isEmpty()) {
					try {
						placerUUID = UUID.fromString(placerStr);
					} catch (IllegalArgumentException ignored) { }
				}
			}

			cars.add(new ParkedCar(dbId, carId, world, x, y, z, yaw, fuel, durability, placerUUID));
		}

		return cars;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<ParkedCar> getTable() {
		return parkedCarTable;
	}

	@Override
	protected void doDelete(ParkedCar data) throws SQLException {
		Database table = getDatabase().table(parkedCarTable.getName());
		table.delete("id", data.getDbId(), Types.VARCHAR);
	}
}
