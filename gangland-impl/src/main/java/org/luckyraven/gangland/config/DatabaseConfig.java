package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.bean.Bean;
import org.luckyraven.gangland.core.bean.Configuration;
import org.luckyraven.gangland.core.bean.Phase;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.exception.PluginException;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.DatabaseManager;
import org.luckyraven.gangland.persistence.database.DatabaseSettingsProvider;
import org.luckyraven.gangland.persistence.repository.RepositoryRegistry;

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
	                                         Settings settingsAddon) {
		int type = Settings.getDatabaseType().equalsIgnoreCase("mysql")
		           ? DatabaseHandler.MYSQL
		           : DatabaseHandler.SQLITE;

		GanglandDatabase database = new GanglandDatabase(gangland, Gangland.FULL_PREFIX, settings);
		database.setType(type);

		// Repository scan must run BEFORE the database is added to the manager so the manager sees the repos when
		// it initializes connections.
		database.getRepositoryRegistry().scanAndRegisterRepositories("org.luckyraven.gangland.database.repositories");

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
	 * Exposes the registry as a first-class bean so {@code GanglandContext}'s DATABASE phase hook can pull it from the
	 * container and republish every repository into the container by its concrete class.
	 */
	@Bean
	public RepositoryRegistry repositoryRegistry(GanglandDatabase database) {
		return database.getRepositoryRegistry();
	}
}
