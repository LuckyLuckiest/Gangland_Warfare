package me.luckyraven.database.sub;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class GangDatabase extends DatabaseHandler {

	private final String schema;

	public GangDatabase(JavaPlugin plugin) {
		super(plugin);
		this.schema = "gang";
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

		data.createTable("id INT PRIMARY KEY NOT NULL", "name TEXT NOT NULL", "display_name TEXT NOT NULL",
		                 "color TEXT NOT NULL", "description TEXT NOT NULL", "balance DOUBLE NOT NULL",
		                 "level INT NOT NULL", "experience DOUBLE NOT NUll", "bounty DOUBLE NOT NULL",
		                 "ally LONGTEXT NOT NULL", "created BIGINT NOT NULL");

		Database members = getDatabase().table("members");

		members.createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "gang_id INT NOT NULL",
		                    "contribution DOUBLE NOT NULL", "position TEXT", "join_date BIGINT");
	}

	@Override
	public void insertInitialData() throws SQLException {

	}

	@Override
	public String getSchema() {
		return switch (getType()) {
			case DatabaseHandler.MYSQL -> schema;
			case DatabaseHandler.SQLITE -> "database" + File.separator + this.schema;
			default -> null;
		};
	}

	public void insertDataTable(Gang gang) throws SQLException {
		List<String> tempAlias = new ArrayList<>();

		for (Gang alias : gang.getAlly())
			tempAlias.add(String.valueOf(alias.getId()));

		String alias = getDatabase().createList(tempAlias);

		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.insert(columns, new Object[]{
				gang.getId(), gang.getName(), gang.getDisplayName(), gang.getColor(), gang.getDescription(),
				gang.getEconomy().getBalance(), gang.getLevel().getLevelValue(), gang.getLevel().getExperience(),
				gang.getBounty().getAmount(), alias, gang.getCreated()
		}, dataTypes);
	}

	public void updateDataTable(Gang gang) throws SQLException {
		List<String> tempAlias = new ArrayList<>();

		for (Gang alias : gang.getAlly())
			tempAlias.add(String.valueOf(alias.getId()));

		String alias = getDatabase().createList(tempAlias);

		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.update("id = ?", new Object[]{gang.getId()}, new int[]{Types.INTEGER}, columns, new Object[]{
				gang.getName(), gang.getDisplayName(), gang.getColor(), gang.getDescription(),
				gang.getEconomy().getBalance(), gang.getLevel().getLevelValue(), gang.getLevel().getExperience(),
				gang.getBounty().getAmount(), alias, gang.getCreated()
		}, dataTypes);
	}

	public void insertMemberTable(Member member) throws SQLException {
		Database      config          = getDatabase().table("members");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.insert(columns, new Object[]{
				member.getUuid(), member.getGangId(), member.getContribution(),
				member.getRank() == null ? null : member.getRank().getName(), member.getGangJoinDateLong()
		}, dataTypes);
	}

	public void updateMembersTable(Member member) throws SQLException {
		Database      config          = getDatabase().table("members");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.update("uuid = ?", new Object[]{member.getUuid()}, new int[]{Types.CHAR}, columns, new Object[]{
				member.getGangId(), member.getContribution(),
				member.getRank() == null ? null : member.getRank().getName(), member.getGangJoinDateLong()
		}, dataTypes);
	}

}
