package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
import org.luckyraven.gangland.copsncrooks.place.SetupPointRegistry;
import org.luckyraven.gangland.copsncrooks.setup.SetupCommands;
import org.luckyraven.gangland.copsncrooks.setup.SetupMessages;
import org.luckyraven.gangland.copsncrooks.setup.SetupOutline;
import org.luckyraven.gangland.copsncrooks.setup.SetupSelections;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * Beans of the 0.16 registries: police stations, admin regions (published to {@link PlaceNames}) and setup points,
 * plus the admin setup wand that writes them ({@code /glw cop setup}).
 * Their repositories come from the module's package scan.
 */
@Configuration
public class RegistryModuleConfig {

	@Bean
	public StationRegistry stationRegistry(RepositoryRegistry repositoryRegistry) {
		return new StationRegistry(repositoryRegistry.getRepository(Station.class));
	}

	@Bean
	public AdminRegionRegistry adminRegionRegistry(RepositoryRegistry repositoryRegistry, PlaceNames placeNames) {
		AdminRegionRegistry registry = new AdminRegionRegistry(repositoryRegistry.getRepository(AdminRegion.class));
		placeNames.register(registry);
		return registry;
	}

	@Bean
	public SetupPointRegistry setupPointRegistry(RepositoryRegistry repositoryRegistry) {
		return new SetupPointRegistry(repositoryRegistry.getRepository(SetupPoint.class));
	}

	@Bean
	public SetupSelections setupSelections() {
		return new SetupSelections();
	}

	/** The wand's text and knobs, from {@code copsncrooks/setup.yml} (copied out of the module jar in KERNEL). */
	@Bean
	public SetupMessages setupMessages(FileManager fileManager) {
		SetupMessages messages = new SetupMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}

	/** The reply text of the /glw cop and /glw jail commands, from {@code copsncrooks/commands.yml}. */
	@Bean
	public CommandMessages commandMessages(FileManager fileManager) {
		CommandMessages messages = new CommandMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}

	@Bean
	public SetupOutline setupOutline(JavaPlugin plugin, SetupSelections selections, SetupMessages messages) {
		return new SetupOutline(plugin, selections, messages, Bukkit::getOnlinePlayers,
		                        SetupOutline.particles(messages));
	}

	/** The station save reads Station_Radius per call from the loaded provider (CopLoader replaces it on a reload). */
	@Bean
	public SetupCommands setupCommands(StationRegistry stations, AdminRegionRegistry regions,
	                                   SetupPointRegistry points, CopSpawnManager spawns, JailRegistry jails,
	                                   SetupSelections selections, SetupMessages messages, SetupOutline outline,
	                                   CopLoader copLoader) {
		return new SetupCommands(stations, regions, points, spawns, jails, selections, messages, outline,
		                         copLoader::getLoadedProvider);
	}
}
