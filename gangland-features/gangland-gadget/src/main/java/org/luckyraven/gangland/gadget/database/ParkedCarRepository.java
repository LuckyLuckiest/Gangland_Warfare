package org.luckyraven.gangland.gadget.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.ParkedCar;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(ParkedCar.class)
public class ParkedCarRepository extends AbstractRepository<ParkedCar> {

	/** A SQL NULL reads as 0; any other value is read through Number (MySQL may return Long/BigDecimal). */
	private static Number num(Object raw) {
		return raw instanceof Number number ? number : 0;
	}

	private final ParkedCarTable parkedCarTable;

	public ParkedCarRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.parkedCarTable = new ParkedCarTable();
	}

	@Override
	protected Collection<ParkedCar> doLoadAll() throws SQLException {
		List<ParkedCar> cars = new ArrayList<>();
		List<Object[]>  data = tableBackend().selectAll();

		for (Object[] result : data) {
			int    v              = 0;
			String dbId           = String.valueOf(result[v++]);
			String carId          = String.valueOf(result[v++]);
			String world          = String.valueOf(result[v++]);
			double x              = num(result[v++]).doubleValue();
			double y              = num(result[v++]).doubleValue();
			double z              = num(result[v++]).doubleValue();
			float  yaw            = (float) num(result[v++]).doubleValue();
			int    fuel           = num(result[v++]).intValue();
			int    maxFuel        = num(result[v++]).intValue();
			int    durability     = num(result[v++]).intValue();
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
		tableBackend().delete("id = ?", data.getDbId());
	}
}
