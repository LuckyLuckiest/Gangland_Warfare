package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.database.repositories.turf.ActiveTurfBuffRepository;
import me.luckyraven.database.repositories.turf.TurfGarrisonRepository;
import me.luckyraven.database.repositories.turf.TurfRepository;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.turf.GanglandTurfMessages;
import me.luckyraven.file.configuration.turf.GanglandTurfSounds;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.turf.capture.CaptureService;
import me.luckyraven.turf.capture.CaptureSettings;
import me.luckyraven.turf.contract.TurfDisplayContract;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.contract.TurfRepositoryContract;
import me.luckyraven.turf.contract.TurfSoundContract;
import me.luckyraven.turf.contribution.TurfContributionSettings;
import me.luckyraven.turf.contribution.TurfContributionTickTask;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.powerups.*;
import me.luckyraven.turf.selection.WandSelectionManager;
import me.luckyraven.turf.task.GangPresenceTracker;
import me.luckyraven.turf.task.InactivityReleaseTask;
import me.luckyraven.turf.task.TurfIncomeDistributor;
import me.luckyraven.turf.task.TurfLocationTracker;

import java.util.List;

/**
 * Bean wiring for the gang-turf feature. Every tuning number comes from {@link Settings} (the central
 * {@code settings.yml} reader) — see the {@code Turf:} block there for the authoritative defaults and docs.
 */
@Configuration
public final class TurfConfig {

	@Bean
	public TurfRepositoryContract turfRepositoryContract(TurfRepository repository) {
		return repository;
	}

	@Bean
	public PowerupRegistry powerupRegistry() {
		return new PowerupRegistry();
	}

	@Bean
	public PowerupRegistryLoader powerupRegistryLoader(PowerupRegistry registry, FileManager fileManager) {
		return new PowerupRegistryLoader(registry, fileManager);
	}

	@Bean
	public ActiveBuffRepositoryContract activeBuffRepositoryContract(ActiveTurfBuffRepository repository) {
		return repository;
	}

	@Bean
	public GarrisonRepositoryContract garrisonRepositoryContract(TurfGarrisonRepository repository) {
		return repository;
	}

	@Bean
	public ActiveBuffManager activeBuffManager(Gangland plugin, ActiveBuffRepositoryContract repository) {
		ActiveBuffManager manager = new ActiveBuffManager(plugin, repository);
		manager.initialize();
		return manager;
	}

	@Bean
	public GarrisonManager garrisonManager(GarrisonRepositoryContract repository) {
		GarrisonManager manager = new GarrisonManager(repository);
		manager.initialize();
		return manager;
	}

	@Bean
	public TurfMessageContract turfMessageContract() {
		return new GanglandTurfMessages();
	}

	@Bean
	public TurfSoundContract turfSoundContract() {
		return new GanglandTurfSounds();
	}

	@Bean
	public TurfDisplayContract turfDisplayContract() {
		return Settings::isTurfShowEnterTitle;
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
		List<Integer> milestones = Settings.getTurfCaptureProgressMilestones();
		int[]         asArray    = new int[milestones.size()];
		for (int i = 0; i < milestones.size(); i++) {
			asArray[i] = milestones.get(i);
		}
		return new CaptureSettings(
				Settings.getTurfCaptureDurationSeconds(),
				Settings.getTurfCaptureCooldownMinutes(),
				Settings.getTurfCaptureAbandonGraceSeconds(),
				Settings.getTurfCapturePostLogoffProtectionMinutes(),
				Settings.getTurfCaptureInactivityAutoReleaseDays(),
				asArray,
				Settings.isTurfCaptureBroadcastGlobally(),
				Settings.getTurfCaptureUnclaimedPhase1Seconds(),
				Settings.getTurfCaptureUnclaimedPhase2Seconds());
	}

	@Bean
	public CaptureService captureService(TurfManager turfs,
	                                     GangLookupContract gangs,
	                                     UserLookupContract users,
	                                     CaptureSettings settings,
	                                     TurfSoundContract sounds) {
		return new CaptureService(turfs, gangs, users, settings, sounds);
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
	                                                   GangLookupContract gangs,
	                                                   ActiveBuffManager buffs) {
		long                  intervalTicks = Settings.getTurfIncomeIntervalMinutes() * 60L * 20L;
		TurfIncomeDistributor distributor   = new TurfIncomeDistributor(plugin, turfs, gangs, buffs, intervalTicks);
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

	@Bean
	public TurfContributionSettings turfContributionSettings(@SuppressWarnings("unused") Settings settings) {
		return new TurfContributionSettings(
				Settings.getTurfContributionDefenderPresenceTick(),
				Settings.getTurfContributionAttackerPresenceTick(),
				Settings.getTurfContributionCaptureCompleteBonus(),
				Settings.getTurfContributionDefenseSuccessBonus());
	}

	@Bean
	public TurfContributionTickTask turfContributionTickTask(Gangland plugin,
	                                                         TurfManager turfs,
	                                                         GangLookupContract gangs,
	                                                         UserLookupContract users,
	                                                         TurfContributionSettings settings) {
		TurfContributionTickTask task = new TurfContributionTickTask(plugin, turfs, gangs, users, settings);
		task.start();
		return task;
	}

	// TurfBossBarListener and TurfCaptureNotifier are @ListenerHandler classes — the framework instantiates them
	// via constructor injection and registers their @EventHandler methods. Declaring @Bean for them here would
	// produce a second instance whose events never fire (the auto-scanned copy is the one wired up), which is
	// exactly what hid the bossbar-refresh bug: the @Bean copy had its scheduler task running, but its barsByTurf
	// was always empty because events went to the other instance.
}
