package org.luckyraven.gangland.database.repositories.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.bounty.BountySettings;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.user.UserDataLoader;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.DatabaseHelper;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.DatabaseSettingsMocks;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The poster ledger rides in the {@code bounty_posters} column of the user table: a row saved before the upgrade
 * (NULL) reads as one posted bounty, a saved ledger reloads with its posters and refund figures, and a brand-new user
 * inserts an empty ledger. Real SQLite, per documentation/TESTING.md section 5.
 */
@DisplayName("User bounty ledger - the bounty_posters column against real SQLite")
class UserBountyLedgerIntegrationTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private BukkitStatics    bukkit;
	private JavaPlugin       plugin;
	private GanglandDatabase database;
	private UUID             uuid;

	@BeforeEach
	void setUp() throws SQLException {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(tempDir);
		EconomyHandler.setVaultEconomy(null);

		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		when(identity.getWantedLevelIncrement()).thenReturn(1);
		when(identity.getWantedMaximumLevel()).thenReturn(5);
		IdentitySettings.bind(identity);

		plugin   = PluginMocks.plugin(tempDir);
		database = new GanglandDatabase(plugin, "ledger", DatabaseSettingsMocks.sqliteOnly());
		database.setType(DatabaseHandler.SQLITE);
		database.connectBackend();

		UserTable userTable = new UserTable();
		database.getBackend().applySchema(TableSchemas.fromTable(userTable));
		database.getBackend().applySchema(TableSchemas.fromTable(new BankTable(userTable)));

		uuid = UUID.randomUUID();
	}

	@AfterEach
	void tearDown() {
		database.disconnectBackend();
		if (database.getDatabase() != null) database.getDatabase().disconnect();
		bukkit.close();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("a row saved before the upgrade has a NULL column and reads as one posted bounty")
	void legacyNullColumn_readsAsPosted() throws SQLException {
		sql("INSERT INTO user (uuid, balance, kills, deaths, mob_kills, bounty, level, experience, wanted) VALUES ('"
		    + uuid + "', 0.0, 0, 0, 0, 750.0, 0, 0.0, 0)");

		User<Player> user = login();

		assertEquals(0, BigDecimal.valueOf(750).compareTo(user.getBounty().getPostedAmount()));
		assertEquals(0, user.getBounty().getNotoriety().signum());
	}

	@Test
	@DisplayName("a saved ledger reloads with its posters and the paid refund figure")
	void saveAndReload_keepsPostersAndTheRefund() throws SQLException {
		User<Player> saved = user(uuid);
		CommandSender poster = mock(CommandSender.class);
		when(poster.getName()).thenReturn("poster");
		saved.getBounty().addBounty(poster, BigDecimal.valueOf(100), 5);   // posted 200 (scaled), paid 100
		saved.getBounty().addNotoriety(BigDecimal.valueOf(40));
		save(saved);

		User<Player> loaded = login();

		assertEquals(saved.getBounty().serializeLedger(), loaded.getBounty().serializeLedger());
		assertEquals(0, BigDecimal.valueOf(100).compareTo(loaded.getBounty().getPaidAmount(poster)));
		assertEquals(0, saved.getBounty().getAmount().compareTo(loaded.getBounty().getAmount()));
	}

	@Test
	@DisplayName("a brand-new user inserts an empty (not NULL) ledger")
	void newUser_insertsAnEmptyLedger() throws SQLException {
		save(user(uuid));

		try (Connection connection = DriverManager.getConnection(url()); Statement statement = connection.createStatement();
		     ResultSet rows = statement.executeQuery("SELECT bounty_posters FROM user WHERE uuid = '" + uuid + "'")) {
			assertEquals(true, rows.next());
			assertEquals("", rows.getString(1));
			assertNotNull(rows.getString(1));
		}
	}

	private User<Player> user(UUID id) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getPlayer()).thenReturn(player);
		when(player.isOnline()).thenReturn(false);
		return new User<>(plugin, player, (p, raw) -> raw);
	}

	private void save(User<Player> user) {
		DatabaseHelper helper = new DatabaseHelper(mock(Gangland.class), database);
		UserTable      table  = new UserTable();

		helper.runQueries(db -> table.insertTableQuery(db, user));
	}

	private User<Player> login() {
		User<Player> user = user(uuid);
		UserDataLoader loader = new UserDataLoader(mock(Gangland.class), database, new GangMembership(),
		                                           mock(BountySettings.class), mock(WantedSettings.class),
		                                           mock(WantedStars.class));

		loader.loadUserData(user, new UserTable(), new BankTable(new UserTable()));
		return user;
	}

	private String url() {
		return "jdbc:sqlite:" + tempDir.resolve("database").resolve("ledger.db").toAbsolutePath();
	}

	private void sql(String statementText) throws SQLException {
		try (Connection connection = DriverManager.getConnection(url()); Statement statement = connection.createStatement()) {
			statement.executeUpdate(statementText);
		}
	}

}
