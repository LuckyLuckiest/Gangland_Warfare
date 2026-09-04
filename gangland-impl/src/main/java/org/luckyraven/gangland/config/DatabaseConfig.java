package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.DatabaseManager;
import org.luckyraven.keystone.persistence.database.DatabaseSettingsProvider;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.diagnostics.DatabaseFaultSink;
import org.luckyraven.keystone.module.LoadedModule;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.io.IOException;
import java.sql.SQLException;

/**
 * DATABASE-phase wiring. Produces the {@link GanglandDatabase} (driven by {@code Settings.getDatabaseType()}, hence the
 * FILE-phase {@link Settings} dependency), runs {@code RepositoryRegistry.scanAndRegisterRepositories} for the
 * {@code org.luckyraven.gangland.database.repositories} package, then exposes the registry as a separate bean so the
 * {@code GanglandContext} DATABASE phase hook can walk it and republish each {@code IRepository} into the root
 * container by its concrete class.
 *
 * <p>Note: {@link DatabaseManager} and {@link DatabaseSettingsProvider} are produced by {@code KernelConfig} in the
 * KERNEL phase. They appear here as {@code @Bean} method parameters.
 */
@CustomLog
@Configuration(phase = Phase.DATABASE)
public class DatabaseConfig {

	private final Gangland gangland;

	public DatabaseConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public GanglandDatabase ganglandDatabase(DatabaseManager databaseManager,
	                                         DatabaseSettingsProvider settings,
	                                         Settings settingsAddon,
	                                         ModuleLoader moduleLoader) {
		int type = Settings.getDatabaseType().equalsIgnoreCase("mysql")
		           ? DatabaseHandler.MYSQL
		           : DatabaseHandler.SQLITE;

		GanglandDatabase database = new GanglandDatabase(gangland, Gangland.FULL_PREFIX, settings);
		// Connects the legacy pool and resolves the MySQL→SQLite fallback — the backend must be built from the
		// RESOLVED type, so it connects only after this call.
		database.setType(type);

		try {
			if (database.getType() == DatabaseHandler.MYSQL) {
				// The backend pool connects straight to the schema; make sure it exists first (idempotent).
				database.createSchema();
			}
			database.connectBackend();
		} catch (SQLException | IOException exception) {
			throw new PluginException("Failed to connect the database backend: " + exception.getMessage(), exception);
		}

		// Repository scan must run BEFORE the database is added to the manager so the manager sees the repos when
		// it initializes connections. Repositories receive the DatabaseBackend via constructor injection and
		// persist through TableBackend; createTables() applies schemas through the backend diff engine.
		database.getRepositoryRegistry().scanAndRegisterRepositories("org.luckyraven.gangland.database.repositories");
		// Runtime modules ship their own @Repository classes in jars the plugin loader cannot see: scan the
		// packages they declared through the module loader so their tables join the same schema pass.
		for (LoadedModule module : moduleLoader.loaded()) {
			for (String repositoryPackage : module.registrations().repositoryPackages()) {
				database.getRepositoryRegistry().scanAndRegisterRepositories(repositoryPackage,
				                                                             moduleLoader.classLoader());
			}
		}

		databaseManager.addDatabase(database);
		databaseManager.initializeDatabases();

		// Validate the manager actually picked up the database after init.
		GanglandDatabase resolved = GanglandDatabase.findInstance(databaseManager);
		if (resolved == null) {
			throw new PluginException("Gangland Database instance is not found.");
		}
		return resolved;
	}

	/**
	 * The connected backend as a first-class bean so later phases (persistent cooldowns, fault sinks, services)
	 * can inject {@link DatabaseBackend} directly.
	 */
	@Bean
	public DatabaseBackend databaseBackend(GanglandDatabase database) {
		return database.getBackend();
	}

	/**
	 * Persists classified faults (dependency failures and internal bugs — never user errors) into
	 * {@code gangland_faults} through the backend. Self-registers into the hub; its schema is applied during the
	 * LIFECYCLE pass, after the backend is connected.
	 */
	@Bean
	public DatabaseFaultSink databaseFaultSink(GanglandDatabase database, Diagnostics diagnostics) {
		return new DatabaseFaultSink(database.getBackend(), diagnostics, "gangland_faults");
	}

	/**
	 * Exposes the registry as a first-class bean so {@code GanglandContext}'s DATABASE phase hook can pull it from the
	 * container and republish every repository into the container by its concrete class.
	 */
	@Bean
	public RepositoryRegistry repositoryRegistry(GanglandDatabase database) {
		return database.getRepositoryRegistry();
	}
}
