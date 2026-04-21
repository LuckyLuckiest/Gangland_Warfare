package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.Phase;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.DatabaseManager;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;
import me.luckyraven.persistence.repository.RepositoryRegistry;

/**
 * DATABASE-phase wiring. Produces the {@link GanglandDatabase} (driven by {@code Settings.getDatabaseType()}, hence the
 * FILE-phase {@link Settings} dependency), runs {@code RepositoryRegistry.scanAndRegisterRepositories} for the
 * {@code me.luckyraven.database.repositories} package, then exposes the registry as a separate bean so the
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
		database.getRepositoryRegistry().scanAndRegisterRepositories("me.luckyraven.database.repositories");

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
