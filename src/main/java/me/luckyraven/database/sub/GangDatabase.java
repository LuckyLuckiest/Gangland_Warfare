package me.luckyraven.database.sub;

import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.Member;
import me.luckyraven.database.DatabaseHandler;
import org.bukkit.plugin.java.JavaPlugin;

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
		getDatabase().table("data").createTable("id INT PRIMARY KEY NOT NULL", "name TEXT NOT NULL",
		                                        "display_name TEXT NOT NULL", "color TEXT NOT NULL",
		                                        "description TEXT NOT NULL", "balance DOUBLE NOT NULL",
		                                        "level DOUBLE NOT NUll", "bounty DOUBLE NOT NULL",
		                                        "ally LONGTEXT NOT NULL", "created BIGINT NOT NULL");
		getDatabase().table("members").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "gang_id INT NOT NULL",
		                                           "contribution DOUBLE NOT NULL", "position TEXT", "join_date BIGINT");
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

	public void updateDataTable(Gang gang) throws SQLException {
		List<String> tempAlias = new ArrayList<>();

		for (Gang alias : gang.getAlly())
			tempAlias.add(String.valueOf(alias.getId()));

		String alias = getDatabase().createList(tempAlias);

		getDatabase().table("data").update("id = ?", new Object[]{gang.getId()}, new int[]{Types.INTEGER}, new String[]{
				"name", "display_name", "color", "description", "balance", "level", "bounty", "ally", "created"
		}, new Object[]{
				gang.getName(), gang.getDisplayName(), gang.getColor(), gang.getDescription(), gang.getBalance(),
				gang.getLevel().getAmount(), gang.getBounty(), alias, gang.getCreated()
		}, new int[]{
				Types.CHAR, Types.VARCHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.DOUBLE, Types.DOUBLE, Types.DOUBLE,
				Types.LONGVARCHAR, Types.BIGINT
		});
	}

	public void updateMembersTable(Member member) throws SQLException {
		getDatabase().table("members").update("uuid = ?", new Object[]{member.getUuid()}, new int[]{Types.CHAR},
		                                      new String[]{"gang_id", "contribution", "position", "join_date"},
		                                      new Object[]{
				                                      member.getGangId(), member.getContribution(),
				                                      member.getRank() == null ? null : member.getRank().getName(),
				                                      member.getGangJoinDate()
		                                      }, new int[]{Types.INTEGER, Types.DOUBLE, Types.VARCHAR, Types.BIGINT});
	}

}
