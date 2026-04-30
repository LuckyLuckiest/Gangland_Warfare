package org.luckyraven.gangland.database.repositories.car;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.car.ParkedCarTable;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.ParkedCar;
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
		List<Object[]>  data = parkedCarTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int    v              = 0;
			String dbId           = String.valueOf(result[v++]);
			String carId          = String.valueOf(result[v++]);
			String world          = String.valueOf(result[v++]);
			double x              = (double) result[v++];
			double y              = (double) result[v++];
			double z              = (double) result[v++];
			float  yaw            = (float) (double) result[v++];
			int    fuel           = (int) result[v++];
			int    maxFuel        = (int) result[v++];
			int    durability     = (int) result[v++];
			Object placerRaw      = result[v++];
			Object exhaustSideRaw = result[v];

			UUID placerUUID = null;
			if (placerRaw != null) {
				String placerStr = placerRaw.toString();
				if (!placerStr.isEmpty()) {
					try {
						placerUUID = UUID.fromString(placerStr);
					} catch (IllegalArgumentException ignored) { }
				}
			}

			ExhaustSide exhaustSide = null;
			if (exhaustSideRaw != null && !exhaustSideRaw.toString().isEmpty()) {
				exhaustSide = ExhaustSide.fromString(exhaustSideRaw.toString());
			}

			cars.add(new ParkedCar(dbId, carId, world, x, y, z, yaw, fuel, maxFuel, durability, placerUUID,
			                       exhaustSide));
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
