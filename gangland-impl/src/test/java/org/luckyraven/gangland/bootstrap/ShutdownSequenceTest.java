package org.luckyraven.gangland.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.DatabaseManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins CL-05 (core-lifecycle.md observation #5, High risk / High confidence): {@code Gangland.onDisable()} used to
 * call {@code context.shutdownBeans()} unguarded, so a single bean whose {@code onShutdown()} threw skipped both the
 * final {@code PeriodicalUpdates.forceUpdate()} and {@code DatabaseManager.closeConnections()} — silent data loss on
 * every shutdown.
 *
 * <p>Also pins CL-04 (observation #4): the {@code DatabaseBackend} Hikari pool has no owner in Keystone's
 * {@code DatabaseManager.closeConnections()}, so the sequence has to disconnect it explicitly — after the backup
 * pass that still reads through it.
 */
@DisplayName("ShutdownSequence - every disable stage runs even when an earlier one throws")
class ShutdownSequenceTest {

	private GanglandContext   context;
	private PeriodicalUpdates periodicalUpdates;
	private DatabaseManager   databaseManager;
	private GanglandDatabase  database;

	@BeforeEach
	void setUp() {
		context           = mock(GanglandContext.class);
		periodicalUpdates = mock(PeriodicalUpdates.class);
		databaseManager   = mock(DatabaseManager.class);
		database          = mock(GanglandDatabase.class);

		when(context.get(PeriodicalUpdates.class)).thenReturn(periodicalUpdates);
		when(context.get(DatabaseManager.class)).thenReturn(databaseManager);
		when(context.get(GanglandDatabase.class)).thenReturn(database);
		when(databaseManager.getDatabases()).thenReturn(List.of(mock(DatabaseHandler.class)));
	}

	@Test
	@DisplayName("the happy path runs every stage in order")
	void run_healthyContext_runsEveryStage() {
		new ShutdownSequence(context).run();

		verify(context).shutdownBeans();
		verify(context).disableModules();
		verify(periodicalUpdates).forceUpdate();
		verify(databaseManager).closeConnections();
		verify(database).disconnectBackend();
	}

	@Test
	@DisplayName("CL-05: a throwing bean shutdown must not skip the final save, the connection close or the backend "
	             + "disconnect")
	void run_shutdownBeansThrows_stillSavesAndClosesConnections() {
		doThrow(new IllegalStateException("a bean's onShutdown() blew up")).when(context).shutdownBeans();

		assertDoesNotThrow(() -> new ShutdownSequence(context).run(),
				"onDisable() must never propagate a shutdown failure back to the server");

		verify(context).disableModules();
		verify(periodicalUpdates).forceUpdate();
		verify(databaseManager).closeConnections();
		verify(database).disconnectBackend();
	}

	@Test
	@DisplayName("CL-05: a throwing module disable must not skip the final save or the DB close either")
	void run_disableModulesThrows_stillSavesAndClosesConnections() {
		doThrow(new IllegalStateException("a module's onDisabled() blew up")).when(context).disableModules();

		assertDoesNotThrow(() -> new ShutdownSequence(context).run());

		verify(periodicalUpdates).forceUpdate();
		verify(databaseManager).closeConnections();
		verify(database).disconnectBackend();
	}

	@Test
	@DisplayName("CL-04/CL-05: a throwing force-save must not leak the connections or the backend pool")
	void run_forceUpdateThrows_stillClosesConnectionsAndBackend() {
		doThrow(new IllegalStateException("save failed")).when(periodicalUpdates).forceUpdate();

		assertDoesNotThrow(() -> new ShutdownSequence(context).run());

		verify(databaseManager).closeConnections();
		verify(database).disconnectBackend();
	}

	@Test
	@DisplayName("CL-04: a throwing closeConnections() must not leak the DatabaseBackend Hikari pool")
	void run_closeConnectionsThrows_stillDisconnectsTheBackend() {
		doThrow(new IllegalStateException("close failed")).when(databaseManager).closeConnections();

		assertDoesNotThrow(() -> new ShutdownSequence(context).run());

		verify(database).disconnectBackend();
	}

	@Test
	@DisplayName("missing beans (a bootstrap that never completed) are skipped without throwing")
	void run_missingBeans_skipsThoseStages() {
		when(context.get(PeriodicalUpdates.class)).thenReturn(null);
		when(context.get(DatabaseManager.class)).thenReturn(null);
		when(context.get(GanglandDatabase.class)).thenReturn(null);

		assertDoesNotThrow(() -> new ShutdownSequence(context).run());

		verify(periodicalUpdates, never()).forceUpdate();
		verify(databaseManager, never()).closeConnections();
		verify(database, never()).disconnectBackend();
	}

}
