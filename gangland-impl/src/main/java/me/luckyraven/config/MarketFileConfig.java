package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.market.event.MarketShockLoader;
import me.luckyraven.market.event.MarketShockRegistry;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.Phase;

/**
 * FILE-phase market wiring: produces the {@link MarketShockRegistry} that every other bean consults and the
 * {@link MarketShockLoader} that populates it from {@code market_events.yml}.
 */
@Configuration(phase = Phase.FILE)
public class MarketFileConfig {

	@Bean
	public MarketShockRegistry marketShockRegistry(Gangland gangland) {
		return new MarketShockRegistry(gangland);
	}

	@Bean
	public MarketShockLoader marketShockLoader(FileManager fileManager, MarketShockRegistry registry) {
		MarketShockLoader loader = new MarketShockLoader(fileManager, registry);
		// Registering + initializing explicitly here mirrors the cops-n-crooks loader pattern: the bean factory's
		// FILE-phase hook runs initializeAll() after each bean, but only on initializers registered with FileManager.
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}
}
