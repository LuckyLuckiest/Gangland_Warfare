package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawner;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.database.component.TableBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 0.16 appends {@code station_id} to {@code cop_spawner}: a table written by 0.15 (seven columns) gains the column
 * through the schema diff, its rows load with no station, and a station id round-trips.
 */
@DisplayName("CopSpawnerRepository: the station_id column")
class CopSpawnerRepositoryStationTest {

	/** {@code cop_spawner} exactly as 0.15 shipped it: no station column. */
	private static final class LegacyTable extends Table<CopSpawner> {

		LegacyTable() {
			super("cop_spawner");
			addAttribute(new Attribute<>("id", true, Integer.class));
			addAttribute(new Attribute<>("world", false, String.class));
			addAttribute(new Attribute<>("x", false, Double.class));
			addAttribute(new Attribute<>("y", false, Double.class));
			addAttribute(new Attribute<>("z", false, Double.class));
			addAttribute(new Attribute<>("yaw", false, Float.class));
			addAttribute(new Attribute<>("pitch", false, Float.class));
		}

		@Override
		public Object[] getData(CopSpawner data) {
			Location l = data.getLocation();
			return new Object[]{data.getId(), l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(),
			                    l.getPitch()};
		}

		@Override
		public Map<String, Object> searchCriteria(CopSpawner data) {
			return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
		}
	}

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private BukkitStatics        bukkit;
	private SqliteBackend        backend;
	private CopSpawnerRepository repository;
	private World                world;

	@BeforeEach
	void setUp() throws SQLException {
		bukkit = BukkitStatics.install();
		world  = mock(World.class);
		when(world.getName()).thenReturn("world");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("spawner.db")));
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		bukkit.close();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("an old table without station_id loads, the column is added, and a station id round-trips")
	void oldTable_gainsStationColumn() throws SQLException {
		LegacyTable legacy = new LegacyTable();
		backend.applySchema(TableSchemas.fromTable(legacy));
		new TableBackend<>(legacy, backend).upsert(new CopSpawner(1, new Location(world, 1, 64, 1, 90f, 5f)));

		backend.applySchema(TableSchemas.fromTable(new CopSpawnerTable()));   // what the repository package scan does
		repository = new CopSpawnerRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);

		List<CopSpawner> loaded = new ArrayList<>(repository.loadAll());
		assertEquals(1, loaded.size());
		assertNull(loaded.get(0).getStationId(), "an old row belongs to no station");
		assertEquals(90f, loaded.get(0).getLocation().getYaw());

		loaded.get(0).setStationId(5);
		repository.save(loaded.get(0));

		assertEquals(5, new ArrayList<>(repository.loadAll()).get(0).getStationId());
	}

	@Test
	@DisplayName("a spawner saved with no station loads with none")
	void noStation_roundTripsNull() throws SQLException {
		backend.applySchema(TableSchemas.fromTable(new CopSpawnerTable()));
		repository = new CopSpawnerRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);

		repository.save(new CopSpawner(1, new Location(world, 1, 64, 1)));

		assertNull(new ArrayList<>(repository.loadAll()).get(0).getStationId());
	}
}
