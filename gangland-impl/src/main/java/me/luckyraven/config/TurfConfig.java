package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.database.repositories.turf.TurfRepository;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.turf.capture.CaptureService;
import me.luckyraven.turf.capture.CaptureSettings;
import me.luckyraven.turf.contract.TurfRepositoryContract;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.selection.WandSelectionManager;
import me.luckyraven.turf.task.GangPresenceTracker;
import me.luckyraven.turf.task.InactivityReleaseTask;
import me.luckyraven.turf.task.TurfIncomeDistributor;
import me.luckyraven.turf.task.TurfLocationTracker;

/**
 * Bean wiring for the gang-turf feature. Defaults below match the spec; replace the {@link #captureSettings()} /
 * {@link #turfIncomeDistributor} literals with a {@code turfs-settings.yml} loader once the config file lands.
 */
@Configuration
public final class TurfConfig {

	private static final int   DEFAULT_CAPTURE_SECONDS           = 180;   // 3 minutes
	private static final int   DEFAULT_COOLDOWN_MINUTES          = 15;
	private static final int   DEFAULT_ABANDON_GRACE_SECONDS     = 15;
	private static final int   DEFAULT_POST_LOGOFF_GRACE_MINUTES = 10;
	private static final int   DEFAULT_INACTIVITY_RELEASE_DAYS   = 10;
	private static final int   DEFAULT_INCOME_INTERVAL_MINUTES   = 10;
	private static final int[] DEFAULT_PROGRESS_MILESTONES       = {25, 50, 75};

	@Bean
	public TurfRepositoryContract turfRepositoryContract(TurfRepository repository) {
		return repository;
	}

	@Bean
	public TurfManager turfManager(TurfRepositoryContract repository) {
		TurfManager manager = new TurfManager(repository);
		manager.initialize();
		return manager;
	}

	@Bean
	public WandSelectionManager wandSelectionManager(PermissionManager permissionManager) {
		// Register the wand / turf-admin permission once, alongside manager creation.
		permissionManager.addPermission(WandSelectionManager.ADMIN_PERMISSION);
		return new WandSelectionManager();
	}

	@Bean
	public CaptureSettings captureSettings() {
		return new CaptureSettings(
				DEFAULT_CAPTURE_SECONDS,
				DEFAULT_COOLDOWN_MINUTES,
				DEFAULT_ABANDON_GRACE_SECONDS,
				DEFAULT_POST_LOGOFF_GRACE_MINUTES,
				DEFAULT_INACTIVITY_RELEASE_DAYS,
				DEFAULT_PROGRESS_MILESTONES);
	}

	@Bean
	public CaptureService captureService(TurfManager turfs,
	                                     GangLookupContract gangs,
	                                     UserLookupContract users,
	                                     CaptureSettings settings) {
		return new CaptureService(turfs, gangs, users, settings);
	}

	@Bean
	public TurfLocationTracker turfLocationTracker(Gangland plugin, TurfManager turfs, CaptureService capture) {
		TurfLocationTracker tracker = new TurfLocationTracker(plugin, turfs, capture);
		tracker.start();
		return tracker;
	}

	@Bean
	public TurfIncomeDistributor turfIncomeDistributor(Gangland plugin,
	                                                   TurfManager turfs,
	                                                   GangLookupContract gangs) {
		long                  intervalTicks = DEFAULT_INCOME_INTERVAL_MINUTES * 60L * 20L;
		TurfIncomeDistributor distributor   = new TurfIncomeDistributor(plugin, turfs, gangs, intervalTicks);
		distributor.start();
		return distributor;
	}

	@Bean
	public InactivityReleaseTask inactivityReleaseTask(TurfManager turfs,
	                                                   GangLookupContract gangs,
	                                                   CaptureSettings settings) {
		return new InactivityReleaseTask(turfs, gangs, settings.getInactivityAutoReleaseDays());
	}

	@Bean
	public GangPresenceTracker gangPresenceTracker(Gangland plugin,
	                                               GangLookupContract gangs,
	                                               UserLookupContract users,
	                                               CaptureSettings settings,
	                                               InactivityReleaseTask release) {
		GangPresenceTracker tracker = new GangPresenceTracker(
				plugin, gangs, users, settings.getInactivityAutoReleaseDays(), release);
		tracker.start();
		return tracker;
	}
}
