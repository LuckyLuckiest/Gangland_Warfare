package org.luckyraven.gangland.turf.map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/**
 * W1-M's beans (the map service and the auto-map task). Lanes add @Bean methods here, never in TurfModuleConfig.
 */
@Configuration
public final class TurfMapConfig {

	@Bean
	public TurfMapService turfMapService(TurfManager turfs, GangLookupContract gangs, UserLookupContract users,
	                                     CaptureEligibility eligibility, TurfMapSettings mapSettings,
	                                     TurfSettings turfSettings, TurfMessages text, TurfMessageContract legacy,
	                                     GarrisonManager garrisons) {
		return new TurfMapService(turfs, gangs, users, eligibility, mapSettings, turfSettings, text, legacy, garrisons);
	}

	@Bean
	public MapAutoTask mapAutoTask(JavaPlugin plugin, MapPrefsService prefs, TurfMapService map,
	                               TurfMapSettings settings) {
		MapAutoTask task = new MapAutoTask(plugin, prefs, uuid -> Bukkit.getPlayer(uuid), map::raster, map::send,
		                                   System::currentTimeMillis, settings::getCellBlocks,
		                                   settings::getAutoMinIntervalSeconds);
		task.start();
		return task;
	}
}
