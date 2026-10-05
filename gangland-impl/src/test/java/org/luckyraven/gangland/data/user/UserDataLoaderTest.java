package org.luckyraven.gangland.data.user;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.DatabaseSettingsMocks;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login hydration against a real SQLite user table: the saved wanted level is handed to {@link WantedStars#restore}
 * (docket WB-04: it used to be set with a bare {@code setLevel} and the loader started its own async timer).
 *
 * <p>Follows documentation/TESTING.md section 5: {@code CleanupMode.NEVER}, every connection closed in
 * {@code @AfterEach}, then {@code DbFiles.release}.
 */
@DisplayName("UserDataLoader - restoring the saved wanted level at login")
class UserDataLoaderTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private BukkitStatics    bukkit;
	private JavaPlugin       plugin;
	private GanglandDatabase database;
	private WantedStars      stars;
	private UUID             uuid;
	private User<Player>     user;

	@BeforeAll
	static void bindIdentity() {
		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		when(identity.getWantedLevelIncrement()).thenReturn(1);
		when(identity.getWantedMaximumLevel()).thenReturn(5);
		IdentitySettings.bind(identity);
	}

	@BeforeEach
	void setUp() throws SQLException {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(tempDir);
		EconomyHandler.setVaultEconomy(null);

		plugin   = PluginMocks.plugin(tempDir);
		database = new GanglandDatabase(plugin, "userdata", DatabaseSettingsMocks.sqliteOnly());
		database.setType(DatabaseHandler.SQLITE);
		database.connectBackend();

		UserTable userTable = new UserTable();
		database.getBackend().applySchema(TableSchemas.fromTable(userTable));
		database.getBackend().applySchema(TableSchemas.fromTable(new BankTable(userTable)));

		uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getPlayer()).thenReturn(player);
		when(player.isOnline()).thenReturn(true);
		user = new User<>(plugin, player, (p, raw) -> raw);

		stars = mock(WantedStars.class);
	}

	@AfterEach
	void tearDown() {
		database.disconnectBackend();
		if (database.getDatabase() != null) database.getDatabase().disconnect();
		bukkit.close();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("a saved wanted level goes through WantedStars.restore once, and the loader starts no clock of its own")
	void login_restoresTheSavedLevelThroughWantedStars() throws SQLException {
		saveRow(2);

		loader().loadUserData(user, new UserTable(), new BankTable(new UserTable()));

		verify(stars).restore(user, 2);
		verify(bukkit.scheduler(), never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
		                                                               anyLong(), anyLong());
	}

	@Test
	@DisplayName("a saved row with no wanted level restores nothing")
	void login_noWantedLevel_restoresNothing() throws SQLException {
		saveRow(0);

		loader().loadUserData(user, new UserTable(), new BankTable(new UserTable()));

		verify(stars, never()).restore(any(), anyInt());
	}

	/** The only place the loader is constructed, so a constructor change touches one line. */
	private UserDataLoader loader() {
		return new UserDataLoader(mock(Gangland.class), database, new GangMembership(), mock(BountySettings.class),
		                          mock(WantedSettings.class), stars);
	}

	private void saveRow(int wanted) throws SQLException {
		String url = "jdbc:sqlite:" + tempDir.resolve("database").resolve("userdata.db").toAbsolutePath();

		try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
			statement.executeUpdate("INSERT INTO user (uuid, balance, kills, deaths, mob_kills, bounty, level, " +
			                        "experience, wanted) VALUES ('" + uuid + "', 10.0, 1, 2, 3, 0.0, 4, 5.0, " +
			                        wanted + ")");
		}
	}

}
