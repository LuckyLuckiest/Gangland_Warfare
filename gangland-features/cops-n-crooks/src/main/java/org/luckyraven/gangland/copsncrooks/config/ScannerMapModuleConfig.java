package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapRenderer;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapView;
import org.luckyraven.gangland.copsncrooks.scanner.SearchZoneCache;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

import java.util.function.Supplier;

/**
 * Beans of the Scanner Map (0.20.0): the zone cache, the renderer and the per-world view.
 *
 * @since 0.20.0
 */
@Configuration
public class ScannerMapModuleConfig {

	/** The loaded Scanner_Map settings, read per call so a reload is seen; the shipped defaults before the first load. */
	public static Supplier<ScannerMapSettings> settingsOf(Supplier<@Nullable CopConfigProvider> provider) {
		return () -> {
			CopConfigProvider current = provider.get();
			if (current == null) {
				return ScannerMapSettings.DEFAULT;
			}
			ScannerMapSettings settings = current.getScannerMapSettings();
			return settings == null ? ScannerMapSettings.DEFAULT : settings;
		};
	}

	@Bean
	public SearchZoneCache searchZoneCache() {
		return new SearchZoneCache();
	}

	@Bean
	public ScannerMapRenderer scannerMapRenderer(CopLoader copLoader, SearchZoneCache cache, CopManager copManager,
	                                             AdminRegionRegistry regions) {
		// ponytail: wall clock / 50 ms as the tick clock; drifts under TPS lag, switch to world full time if refresh cadence matters
		return new ScannerMapRenderer(settingsOf(copLoader::getLoadedProvider), cache, copManager, regions,
		                              () -> System.currentTimeMillis() / 50L);
	}

	@Bean
	public ScannerMapView scannerMapView(ScannerMapRenderer renderer) {
		return new ScannerMapView(renderer, Bukkit::createMap);
	}
}
