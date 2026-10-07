package org.luckyraven.gangland.copsncrooks.config;

import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
import org.luckyraven.gangland.copsncrooks.place.SetupPointRegistry;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * Beans of the 0.16 registries: police stations, admin regions (published to {@link PlaceNames}) and setup points.
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
}
