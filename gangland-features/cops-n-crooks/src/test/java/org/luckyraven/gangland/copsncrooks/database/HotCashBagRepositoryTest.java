package org.luckyraven.gangland.copsncrooks.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.bag.BagEntry;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("HotCashBagRepository: real SQLite through the DatabaseBackend SPI")
class HotCashBagRepositoryTest {

	private static final UUID RUNNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID OTHER  = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend        backend;
	private HotCashBagRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("bag.db")));
		backend.applySchema(TableSchemas.fromTable(new HotCashBagTable()));

		repository = new HotCashBagRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	private static BagEntry entry(UUID id, String amount, String chase, long day, String banked, String seized) {
		return new BagEntry(id, new BigDecimal(amount), new BigDecimal(chase), day, new BigDecimal(banked),
		                    new BigDecimal(seized));
	}

	private static void same(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " vs " + actual);
	}

	@Test
	@DisplayName("a saved bag round-trips every column")
	void roundTrip() {
		repository.save(entry(RUNNER, "1234.56", "2000.5", 20_000L, "300.25", "80"));

		Collection<BagEntry> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		BagEntry row = loaded.iterator().next();
		assertEquals(RUNNER, row.player());
		same("1234.56", row.amount());
		same("2000.5", row.chaseTaken());
		assertEquals(20_000L, row.bankedDay());
		same("300.25", row.bankedToday());
		same("80", row.seized());
	}

	@Test
	@DisplayName("a zeroed bag overwrites its row, so a spill is not undone by a restart")
	void zeroedBagOverwritesRow() {
		repository.save(entry(RUNNER, "500", "500", 1L, "0", "0"));
		repository.save(entry(RUNNER, "0", "0", 1L, "0", "0"));

		Collection<BagEntry> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		same("0", loaded.iterator().next().amount());
	}

	@Test
	@DisplayName("an empty table loads nothing")
	void emptyLoad() {
		assertTrue(repository.loadAll().isEmpty());
	}

	@Test
	@DisplayName("delete removes only that player's row")
	void delete_removesOneRow() {
		repository.save(entry(RUNNER, "1", "1", 1L, "0", "0"));
		repository.save(entry(OTHER, "2", "2", 1L, "0", "0"));

		repository.delete(entry(RUNNER, "1", "1", 1L, "0", "0"));

		Collection<BagEntry> loaded = repository.loadAll();
		assertEquals(1, loaded.size());
		assertEquals(OTHER, loaded.iterator().next().player());
	}
}
