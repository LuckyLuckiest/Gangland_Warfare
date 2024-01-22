package me.luckyraven.database.sub;

import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class RankDatabase extends DatabaseHandler {

	private final String schema;

	public RankDatabase(JavaPlugin plugin) {
		super(plugin);
		this.schema = "rank_tree";
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

		data.createTable("id INT PRIMARY KEY NOT NULL", "name TEXT NOT NULL", "permissions LONGTEXT NOT NULL",
						 "parent LONGTEXT");
	}

	@Override
	public void insertInitialData() throws SQLException {
		Database dataTable = getDatabase().table("data");

		String head = SettingAddon.getGangRankHead(), tail = SettingAddon.getGangRankTail();

		Object[] tailRow = dataTable.select("name = ?", new Object[]{tail}, new int[]{Types.VARCHAR},
											new String[]{"*"});
		int rows = dataTable.totalRows() + 1;
		if (tailRow.length == 0) dataTable.insert(dataTable.getColumns().toArray(String[]::new),
												  new Object[]{rows, tail, "", ""},
												  new int[]{Types.INTEGER, Types.VARCHAR, Types.VARCHAR,
															Types.VARCHAR});

		Object[] headRow = dataTable.select("name = ?", new Object[]{head}, new int[]{Types.VARCHAR},
											new String[]{"*"});
		if (headRow.length == 0) dataTable.insert(dataTable.getColumns().toArray(String[]::new),
												  new Object[]{rows + 1, head, "", tail},
												  new int[]{Types.INTEGER, Types.VARCHAR, Types.VARCHAR,
															Types.VARCHAR});
	}

	@Override
	public String getSchema() {
		return switch (getType()) {
			case DatabaseHandler.MYSQL -> schema;
			case DatabaseHandler.SQLITE -> "database" + File.separator + this.schema;
			default -> null;
		};
	}

	public void insertDataTable(Rank rank) throws SQLException {
		String permissions = getDatabase().createList(rank.getPermissions());
		String children = getDatabase().createList(
				rank.getNode().getChildren().stream().map(Tree.Node::getData).map(Rank::getName).toList());

		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			 dataTypes[i] = columnsDataType.get(i);

		config.insert(columns, new Object[]{rank.getUsedId(), rank.getName(), permissions, children}, dataTypes);
	}

	public void updateDataTable(Rank rank) throws SQLException {
		String permissions = getDatabase().createList(rank.getPermissions());
		String children = getDatabase().createList(
				rank.getNode().getChildren().stream().map(Tree.Node::getData).map(Rank::getName).toList());

		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			 dataTypes[i] = columnsDataType.get(i);

		config.update("id = ?", new Object[]{rank.getUsedId()}, new int[]{Types.INTEGER}, columns,
					  new Object[]{rank.getName(), permissions, children}, dataTypes);
	}

}
