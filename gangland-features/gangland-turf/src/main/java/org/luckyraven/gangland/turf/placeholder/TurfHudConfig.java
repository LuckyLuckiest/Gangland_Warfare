package org.luckyraven.gangland.turf.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.map.MapPrefsService;
import org.luckyraven.gangland.turf.map.TurfHudTask;
import org.luckyraven.gangland.turf.task.TurfLocationTracker;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.util.ActionBarManager;

/**
 * W1-H's beans (TurfPlaceholderContribution, TurfHudTask). Lanes add @Bean methods here, never in TurfModuleConfig.
 */
@Configuration
public final class TurfHudConfig {

	@Bean
	public PlaceholderContribution turfPlaceholderContribution(TurfLocationTracker tracker,
	                                                           TurfManager turfs,
	                                                           GangLookupContract gangs,
	                                                           UserLookupContract users,
	                                                           CaptureEligibility eligibility,
	                                                           TurfMessages text) {
		return new TurfPlaceholderContribution(tracker, turfs, gangs, users, eligibility, text,
		                                       System::currentTimeMillis);
	}

	@Bean
	public TurfHudTask turfHudTask(JavaPlugin plugin,
	                               TurfLocationTracker tracker,
	                               TurfManager turfs,
	                               GangLookupContract gangs,
	                               UserLookupContract users,
	                               CaptureEligibility eligibility,
	                               TurfMessages text,
	                               TurfMapSettings settings,
	                               MapPrefsService prefs) {
		TurfHudTask task = new TurfHudTask(tracker, turfs, gangs, users, eligibility, text, settings, prefs,
		                                   ActionBarManager::sendBackground,
		                                   Bukkit::getOnlinePlayers,
		                                   System::currentTimeMillis);
		task.start(plugin);
		return task;
	}
}
