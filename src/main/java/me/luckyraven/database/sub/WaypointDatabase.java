package me.luckyraven.database.sub;

import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class WaypointDatabase extends DatabaseHandler {

	private final String schema;

	public WaypointDatabase(JavaPlugin plugin) {
		super(plugin);
		this.schema = "waypoint";
	}

	@Override
	public void createSchema() throws SQLException, IOException {
		getDatabase().createSchema(getSchema());

		// Switch the schema only when using mysql, because it needs to create the schema from the connection
		// then change the jdbc url to the new database
		if (getType() == MYSQL) getDatabase().switchSchema(getSchema());
	}

	@Override
	public void createTables() throws SQLException {
		Database data = getDatabase().table("data");

		data.createTable("id INT PRIMARY KEY NOT NULL", "name TEXT NOT NULL", "world TEXT NOT NULL",
		                 "x DOUBLE NOT NULL", "y DOUBLE NOT NULL", "z DOUBLE NOT NULL", "yaw DOUBLE NOT NULL",
		                 "pitch DOUBLE NOT NULL", "type TEXT NOT NULL", "gang_id INT NOT NULL", "cooldown INT NOT NULL",
		                 "shield INT NOT NULL", "cost DOUBLE NOT NULL", "radius DOUBLE NOT NULL");
	}

	@Override
	public void insertInitialData() throws SQLException {

	}

	@Override
	public String getSchema() {
		return switch (getType()) {
			case DatabaseHandler.MYSQL -> schema;
			case DatabaseHandler.SQLITE -> "database\\" + this.schema;
			default -> null;
		};
	}

	public void insertDataTable(Waypoint waypoint) throws SQLException {
		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.insert(columns, new Object[]{
				waypoint.getUsedId(), waypoint.getName(), waypoint.getWorld(), waypoint.getX(), waypoint.getY(),
				waypoint.getZ(), waypoint.getYaw(), waypoint.getPitch(), waypoint.getType().getName(),
				waypoint.getGangId(), waypoint.getCooldown(), waypoint.getShield(), waypoint.getCost(),
				waypoint.getRadius()
		}, dataTypes);
	}

	public void updateDataTable(Waypoint waypoint) throws SQLException {
		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.update("id = ?", new Object[]{waypoint.getUsedId()}, new int[]{Types.INTEGER}, columns, new Object[]{
				waypoint.getName(), waypoint.getWorld(), waypoint.getX(), waypoint.getY(), waypoint.getZ(),
				waypoint.getYaw(), waypoint.getPitch(), waypoint.getType().getName(), waypoint.getGangId(),
				waypoint.getCooldown(), waypoint.getShield(), waypoint.getCost(), waypoint.getRadius()
		}, dataTypes);
	}

}
