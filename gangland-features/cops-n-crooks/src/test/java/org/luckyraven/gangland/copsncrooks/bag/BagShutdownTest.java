package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.database.HotCashBagRepository;
import org.luckyraven.gangland.copsncrooks.database.HotCashBagTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.copsncrooks.bag.BagFixture.bd;
import static org.mockito.Mockito.mock;

/** Lane bag: P-BAG-9 - a server stop with a bag in it loses nothing and spills nothing. */
@DisplayName("BagService shutdown - the bag is saved and is there after the restart")
class BagShutdownTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend        backend;
	private HotCashBagRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("shutdown.db")));
		backend.applySchema(TableSchemas.fromTable(new HotCashBagTable()));
		repository = new HotCashBagRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("bag 700, shutdown, quit during the stop, reload from the repository: 700 is there, nothing spilled")
	void shutdownWithBagLosesNothing() {
		BagFixture fx = new BagFixture(tempDir);
		HotCashBags bags = new HotCashBags(repository);
		repository.setDataSupplier(bags::all);
		BagService service = new BagService(bags, () -> BagSettings.DEFAULT, fx.messages, fx);
		Player runner = fx.player("Runner");
		fx.level(runner, 2);
		service.take(runner, bd("700"), "pickup");
		service.settle(runner);

		service.shutdown();
		service.onQuit(runner);

		assertTrue(fx.spills.isEmpty(), "the stop spills nothing");
		assertTrue(service.isShuttingDown());

		HotCashBags reloaded = new HotCashBags(repository);
		reloaded.load();

		assertEquals(0, bd("700").compareTo(reloaded.amount(runner.getUniqueId())));
	}

	@Test
	@DisplayName("a spill zeroes the row and the zero is saved, so a restart does not bring the bag back")
	void spillThenRestartStaysEmpty() {
		BagFixture fx = new BagFixture(tempDir);
		HotCashBags bags = new HotCashBags(repository);
		BagService service = new BagService(bags, () -> BagSettings.DEFAULT, fx.messages, fx);
		Player runner = fx.player("Runner");
		fx.level(runner, 2);
		service.take(runner, bd("700"), "pickup");
		bags.flush();

		service.end(runner, org.luckyraven.gangland.core.wanted.WantedCause.DEATH);
		bags.flush();

		HotCashBags reloaded = new HotCashBags(repository);
		reloaded.load();

		assertEquals(0, bd("0").compareTo(reloaded.amount(runner.getUniqueId())));
		assertEquals(1, fx.spills.size());
	}
}
