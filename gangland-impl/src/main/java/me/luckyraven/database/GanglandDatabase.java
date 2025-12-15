package me.luckyraven.database;

import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.*;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GanglandDatabase extends DatabaseHandler {

	private final String              schema;
	private final List<Table<?>>      tables;
	private final PluginDataTable     pluginDataTable;
	private final UserTable           userTable;
	private final BankTable           bankTable;
	private final GangTable           gangTable;
	private final GangAlliesTable     gangAlliesTable;
	private final RankTable           rankTable;
	private final RankParentTable     rankParentTable;
	private final PermissionTable     permissionTable;
	private final RankPermissionTable rankPermissionTable;
	private final MemberTable         memberTable;
	private final WaypointTable       waypointTable;
	private final WeaponTable         weaponTable;

	public GanglandDatabase(JavaPlugin plugin, String schema) {
		super(plugin);

		this.schema = schema;
		this.tables = new ArrayList<>();

		this.pluginDataTable     = new PluginDataTable();
		this.userTable           = new UserTable();
		this.bankTable           = new BankTable(userTable);
		this.gangTable           = new GangTable();
		this.gangAlliesTable     = new GangAlliesTable(gangTable);
		this.rankTable           = new RankTable();
		this.rankParentTable     = new RankParentTable(rankTable);
		this.permissionTable     = new PermissionTable();
		this.rankPermissionTable = new RankPermissionTable(rankTable, permissionTable);
		this.memberTable         = new MemberTable(userTable, rankTable);
		this.waypointTable       = new WaypointTable(gangTable);
		this.weaponTable         = new WeaponTable();

		tables.add(pluginDataTable);
		tables.add(userTable);
		tables.add(bankTable);
		tables.add(gangTable);
		tables.add(gangAlliesTable);
		tables.add(rankTable);
		tables.add(rankParentTable);
		tables.add(permissionTable);
		tables.add(rankPermissionTable);
		tables.add(memberTable);
		tables.add(waypointTable);
		tables.add(weaponTable);
	}

	@Nullable
	public static GanglandDatabase findInstance(DatabaseManager manager) {
		return manager.getDatabases()
				.stream()
				.filter(handler -> handler instanceof GanglandDatabase)
				.map(GanglandDatabase.class::cast)
				.findFirst()
				.orElse(null);
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
		// (1) plugin data table
		Database pluginDataDatabase = getDatabase().table(pluginDataTable.getName());
		pluginDataDatabase.createTable(pluginDataTable.createTableQuery(pluginDataDatabase));

		// (2) user table
		Database userDatabase = getDatabase().table(userTable.getName());
		userDatabase.createTable(userTable.createTableQuery(userDatabase));

		// (3) gang table
		Database gangDatabase = getDatabase().table(gangTable.getName());
		gangDatabase.createTable(gangTable.createTableQuery(gangDatabase));

		// (4) rank table
		Database rankDatabase = getDatabase().table(rankTable.getName());
		rankDatabase.createTable(rankTable.createTableQuery(rankDatabase));

		// (5) permission table
		Database permissionDatabase = getDatabase().table(permissionTable.getName());
		permissionDatabase.createTable(permissionTable.createTableQuery(permissionDatabase));

		// (6) waypoint table
		Database waypointDatabase = getDatabase().table(waypointTable.getName());
		waypointDatabase.createTable(waypointTable.createTableQuery(waypointDatabase));

		// (7) user bank table
		Database bankDatabase = getDatabase().table(bankTable.getName());
		bankDatabase.createTable(bankTable.createTableQuery(bankDatabase));

		// (8) gang allie table
		Database gangAllieDatabase = getDatabase().table(gangAlliesTable.getName());
		gangAllieDatabase.createTable(gangAlliesTable.createTableQuery(gangAllieDatabase));

		// (9) weapon table
		Database weaponDatabase = getDatabase().table(weaponTable.getName());
		weaponDatabase.createTable(weaponTable.createTableQuery(weaponDatabase));

		// (10) member table
		Database memberDatabase = getDatabase().table(memberTable.getName());
		memberDatabase.createTable(memberTable.createTableQuery(memberDatabase));

		// (11) rank parent table
		Database rankParentDatabase = getDatabase().table(rankParentTable.getName());
		rankParentDatabase.createTable(rankParentTable.createTableQuery(rankParentDatabase));

		// (12) rank permission table
		Database rankPermissionDatabase = getDatabase().table(rankPermissionTable.getName());
		rankPermissionDatabase.createTable(rankPermissionTable.createTableQuery(rankPermissionDatabase));
	}

	@Override
	public void insertInitialData() throws SQLException {
		Database dataTable = getDatabase().table(rankTable.getName());
		String   head      = SettingAddon.getGangRankHead(), tail = SettingAddon.getGangRankTail();

		// check if the tail is set
		Object[] tailRow = dataTable.select("name = ?", new Object[]{tail}, new int[]{Types.VARCHAR},
											new String[]{"*"});

		int rows = dataTable.totalRows() + 1;

		if (tailRow.length == 0) dataTable.insert(rankTable.getColumns().toArray(String[]::new),
												  new Object[]{rows, tail}, new int[]{Types.INTEGER, Types.VARCHAR});

		Object[] headRow = dataTable.select("name = ?", new Object[]{head}, new int[]{Types.VARCHAR},
											new String[]{"*"});

		if (headRow.length == 0) {
			dataTable.insert(rankTable.getColumns().toArray(String[]::new), new Object[]{rows + 1, head},
							 new int[]{Types.INTEGER, Types.VARCHAR});
			getDatabase().table(rankParentTable.getName())
						 .insert(rankParentTable.getColumns().toArray(String[]::new), new Object[]{rows + 1, rows},
								 new int[]{Types.INTEGER, Types.INTEGER});
		}
	}

	@Override
	public String getSchema() {
		return switch (getType()) {
			case DatabaseHandler.MYSQL -> schema;
			case DatabaseHandler.SQLITE -> "database" + File.separator + this.schema;
			default -> null;
		};
	}

	public List<Table<?>> getTables() {
		return Collections.unmodifiableList(tables);
	}
}
