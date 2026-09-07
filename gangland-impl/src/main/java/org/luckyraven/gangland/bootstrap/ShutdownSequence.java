package org.luckyraven.gangland.bootstrap;

import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.persistence.database.DatabaseManager;

/**
 * The plugin's disable pipeline, extracted out of {@code Gangland.onDisable()} so every stage is guarded and unit
 * testable.
 *
 * <p>The stages run in order — bean shutdown, module disable, final force-save, connection close, backend
 * disconnect — and <b>each one is isolated</b>: a throwing stage is classified through {@link Diagnostics} and the
 * remaining stages still run. Previously a single misbehaving {@code onShutdown()} skipped both the final
 * {@code forceUpdate()} and {@code closeConnections()}, silently losing every unsaved change on shutdown.
 *
 * <p>The last stage disconnects the {@link org.luckyraven.keystone.persistence.database.backend.DatabaseBackend}
 * pool, which Keystone's {@code DatabaseManager.closeConnections()} does not own — without it the backend's HikariCP
 * pool (and, on SQLite/Windows, its file handles) survives a {@code /reload}.
 */
@CustomLog
public final class ShutdownSequence {

	private final GanglandContext context;

	public ShutdownSequence(@NotNull GanglandContext context) {
		this.context = context;
	}

	/**
	 * Runs every shutdown stage, isolating failures so a later stage is never skipped because an earlier one threw.
	 */
	public void run() {
		// unified bean lifecycle shutdown — deactivates sessions, converts active car data to parked records,
		// despawns NPCs and holograms, all in reverse topological order
		stage("shutdown.beans", context::shutdownBeans);

		// runtime modules: onDisabled in reverse load order, then the module classloader closes
		stage("shutdown.modules", context::disableModules);

		// force save all pending data AFTER bean shutdown so converted records (CarService etc.) are included
		stage("shutdown.save", this::forceSave);

		// closing all legacy connections (and running the configured backup pass)
		stage("shutdown.connections", this::closeConnections);

		// the DatabaseBackend pool is not owned by DatabaseManager — release it last, after the backup pass above
		// has finished reading through it
		stage("shutdown.backend", this::disconnectBackend);
	}

	private void forceSave() {
		PeriodicalUpdates periodicalUpdates = context.get(PeriodicalUpdates.class);

		if (periodicalUpdates == null) return;

		periodicalUpdates.forceUpdate();
	}

	private void closeConnections() {
		DatabaseManager databaseManager = context.get(DatabaseManager.class);

		if (databaseManager == null || databaseManager.getDatabases().isEmpty()) return;

		databaseManager.closeConnections();
	}

	private void disconnectBackend() {
		GanglandDatabase database = context.get(GanglandDatabase.class);

		if (database == null) return;

		database.disconnectBackend();
	}

	private void stage(String code, Runnable body) {
		try {
			body.run();
		} catch (Throwable throwable) {
			Diagnostics hub = Diagnostics.active();

			if (hub != null) {
				hub.report(throwable, code);
			}

			log.error("Shutdown stage '{}' failed; continuing with the remaining stages", code, throwable);
		}
	}

}
