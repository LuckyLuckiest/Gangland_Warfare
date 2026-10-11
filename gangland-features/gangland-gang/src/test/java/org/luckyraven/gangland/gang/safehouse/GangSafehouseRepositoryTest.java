package org.luckyraven.gangland.gang.safehouse;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.database.repositories.safehouse.GangSafehouseRepository;
import org.luckyraven.gangland.gang.database.tables.gang.GangTable;
import org.luckyraven.gangland.gang.database.tables.safehouse.GangSafehouseTable;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** {@code gang_safehouse} through the DatabaseBackend SPI: the key is {@code (gang_id, name)}. */
@DisplayName("GangSafehouseRepository - per gang unique names")
class GangSafehouseRepositoryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private SqliteBackend           backend;
	private GangSafehouseRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("safehouses.db")));
		backend.applySchema(TableSchemas.fromTable(new GangTable()));
		backend.applySchema(TableSchemas.fromTable(new GangSafehouseTable(new GangTable())));

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		repository = new GangSafehouseRepository(plugin, mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	private static Safehouse house(int gang, String name, double x) {
		return new Safehouse(gang, name, "world", x, 64, 7, 90F, 10F);
	}

	@Test
	@DisplayName("perGangUniqueness: the same name in the same gang is one row, the latest write wins")
	void perGangUniqueness() {
		repository.save(house(1, "base", 1));
		repository.save(house(1, "base", 2));

		Collection<Safehouse> loaded = repository.loadAll();

		assertEquals(1, loaded.size(), "one gang cannot own a name twice");
		Safehouse only = loaded.iterator().next();
		assertEquals(2D, only.x());
		assertEquals(90F, only.yaw());
		assertEquals("world", only.world());
		assertEquals(List.of("gang_id", "name"),
		             TableSchemas.fromTable(new GangSafehouseTable(new GangTable())).primaryKey());
	}

	@Test
	@DisplayName("twoGangsBothOwnBase: the name is unique per gang, not per server")
	void twoGangsBothOwnBase() {
		repository.saveAll(List.of(house(1, "base", 1), house(2, "base", 2)));

		Collection<Safehouse> loaded = repository.loadAll();

		assertEquals(2, loaded.size());
		assertTrue(loaded.stream().anyMatch(s -> s.gangId() == 1 && s.x() == 1D));
		assertTrue(loaded.stream().anyMatch(s -> s.gangId() == 2 && s.x() == 2D));
	}

	@Test
	@DisplayName("delete removes only that gang row")
	void delete_removesOnlyThatRow() {
		repository.saveAll(List.of(house(1, "base", 1), house(2, "base", 2)));

		repository.delete(house(1, "base", 1));

		Collection<Safehouse> loaded = repository.loadAll();
		assertEquals(1, loaded.size());
		assertEquals(2, loaded.iterator().next().gangId());
	}

	@Test
	@DisplayName("deleteOnDisband: removeAll drops every row of the gang and no row of another gang")
	@SuppressWarnings("unchecked")
	void deleteOnDisband() {
		GangSafehouseService service = new GangSafehouseService(mock(JavaPlugin.class), repository,
		                                                        mock(UserManager.class), mock(MemberManager.class),
		                                                        mock(GangManager.class), mock(GangRules.class),
		                                                        mock(GangMessages.class), mock(GangWorldPolicy.class));
		service.add(house(1, "base", 1));
		service.add(house(1, "dock", 2));
		service.add(house(2, "base", 3));

		service.removeAll(1);

		assertEquals(0, service.count(1));
		assertEquals(1, service.count(2));
		Collection<Safehouse> loaded = repository.loadAll();
		assertEquals(1, loaded.size(), "the disbanded gang rows are gone from the table");
		assertEquals(2, loaded.iterator().next().gangId());
	}

	@Test
	@DisplayName("a service started on a stored table finds the rows again")
	@SuppressWarnings("unchecked")
	void restartLoadsRows() {
		repository.saveAll(List.of(house(1, "base", 1), house(2, "base", 2)));
		GangSafehouseService service = new GangSafehouseService(mock(JavaPlugin.class), repository,
		                                                        mock(UserManager.class), mock(MemberManager.class),
		                                                        mock(GangManager.class), mock(GangRules.class),
		                                                        mock(GangMessages.class), mock(GangWorldPolicy.class));

		service.onInitialize(true);

		assertEquals(1, service.count(1));
		assertEquals(2D, service.find(2, "BASE").orElseThrow().x(), "names are looked up ignoring case");
	}
}
