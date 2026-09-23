package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.GanglandApiImpl;
import org.luckyraven.gangland.command.CommandManager;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.teleportation.WaypointLookupContract;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.module.artifact.ArtifactResolver;
import org.luckyraven.keystone.module.artifact.MavenRepository;
import org.luckyraven.keystone.module.update.ModuleMessages;
import org.luckyraven.keystone.module.update.ModuleUpdateService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.bean.listener.ListenerPriority;
import org.luckyraven.keystone.placeholder.PlaceholderProvider;
import org.luckyraven.keystone.placeholder.replacer.Replacer;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointTeleport;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.listener.ListenerManager;

/**
 * CONFIG-phase wiring for the cross-cutting plugin glue: the {@link ListenerManager} and {@link CommandManager} (which
 * are themselves consumed in the LISTENER and COMMAND post-bootstrap steps driven by {@code GanglandContext}), the
 * PlaceholderAPI bridge {@link GanglandPlaceholder}, and the legacy "dummy waypoint listener" that has to be
 * pre-registered before {@code listenerManager.registerEvents()} is called, and the {@link ModuleUpdateService}
 * behind {@code /glw module}.
 */
@CustomLog
@Configuration
public class WiringConfig {

	private final Gangland gangland;

	public WiringConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public ListenerManager listenerManager(DependencyContainer container) {
		ListenerManager  listenerManager = new ListenerManager(gangland, container);
		Waypoint         dummy           = new Waypoint("dummy", Gangland.FULL_PREFIX);
		WaypointTeleport dummyTeleport   = new WaypointTeleport(dummy);
		// Pre-register the dummy waypoint listener so it is included when GanglandContext.runListenerPhase() calls
		// registerEvents(). Folded inline here so the listener is owned by the same bean that produces the manager.
		listenerManager.addEvent(dummyTeleport, ListenerPriority.NORMAL);
		return listenerManager;
	}

	@Bean
	public CommandManager commandManager(DependencyContainer container) {
		return new CommandManager(gangland, container, Gangland.FULL_PREFIX, Gangland.SHORT_PREFIX);
	}

	/**
	 * The network side of the runtime module system: a resolver over the repository named by
	 * {@code Modules.Repository}, with every operator-facing line routed through {@link Messages}. CONFIG phase, so
	 * {@link Settings} has already been read; the modules folder is the very one {@link ModuleLoader} loaded from.
	 */
	@Bean
	public ModuleUpdateService moduleUpdateService(ModuleLoader moduleLoader) {
		ModuleMessages messages = ModuleMessages.defaults()
				.withUpdateAvailable((id, installed, available) -> Messages.MODULE_UPDATE_AVAILABLE.toString()
						.replace("%module%", id)
						.replace("%installed%", installed)
						.replace("%available%", available))
				.withUpToDate(id -> Messages.MODULE_UP_TO_DATE.toString().replace("%module%", id))
				.withRestartRequired(id -> Messages.MODULE_RESTART_REQUIRED.toString().replace("%module%", id))
				.withDownloadFailed((id, reason) -> Messages.MODULE_DOWNLOAD_FAILED.toString()
						.replace("%module%", id)
						.replace("%reason%", reason));

		return new ModuleUpdateService(gangland, new ArtifactResolver(gangland),
		                               MavenRepository.of("modules", Settings.getModulesRepository()),
		                               moduleLoader.modulesDirectory(), messages);
	}

	/**
	 * Also publishes {@link GanglandPlaceholder#asProvider()} as a Keystone {@link PlaceholderProvider} service on
	 * the {@code ServicesManager} — the same publication idiom Bartizan's {@code ItemConfig.bartizanItemVocabulary}
	 * uses for {@code ItemVocabulary} (construct, register, log, return). This is Plaque's second placeholder path
	 * (behind real PlaceholderAPI, {@code PapiText.java}): a standalone plugin with no Gangland dependency that
	 * resolves this service lazily, never caching it, so it degrades cleanly if Gangland is absent, disabled or
	 * reloaded. {@code Gangland.onDisable()} unregisters every service this plugin owns via
	 * {@code getServicesManager().unregisterAll(this)} as its first statement, so a disable/enable cycle never
	 * leaves a dead provider behind for a consumer to resolve.
	 */
	@Bean
	public GanglandPlaceholder ganglandPlaceholder(@Qualifier("online") UserManager<Player> userManager,
	                                               UniqueItemAddon uniqueItemAddon,
	                                               BankTiers bankTiers,
	                                               DependencyContainer container,
	                                               PlaceholderService placeholderService) {
		GanglandPlaceholder placeholder = new GanglandPlaceholder(Gangland.FULL_PREFIX, Replacer.Closure.PERCENT,
		                                                          userManager, uniqueItemAddon, bankTiers,
		                                                          container, placeholderService);
		Bukkit.getServicesManager()
		      .register(PlaceholderProvider.class, placeholder.asProvider(), gangland, ServicePriority.Normal);
		log.info("Placeholder provider published for external consumers (e.g. Plaque)");
		return placeholder;
	}

	/**
	 * WS6 G1: publishes {@link GanglandApiImpl} as a {@link GanglandApi} service on the {@code ServicesManager} —
	 * same publication idiom as {@link #ganglandPlaceholder} just above (construct, register, log, return). The
	 * only consumer is an external plugin (no runtime module resolves the facade this way — modules already have
	 * constructor injection); it must resolve fresh on every call, never cache the reference, exactly like
	 * Bartizan's own facade documents, since Gangland may disable, reload or not be installed at all.
	 * {@code Gangland.onDisable()}'s {@code getServicesManager().unregisterAll(this)} (already the first statement,
	 * added for {@link #ganglandPlaceholder} — WS1) covers this registration too; nothing further to add there.
	 */
	@Bean
	public GanglandApiImpl ganglandApi(UserLookupContract users, GangMembership gangs,
	                                   WaypointLookupContract waypoints, BankTiers bankTiers) {
		GanglandApiImpl api = new GanglandApiImpl(users, gangs, waypoints, bankTiers);
		Bukkit.getServicesManager().register(GanglandApi.class, api, gangland, ServicePriority.Normal);
		log.info("GanglandApi facade published for external consumers");
		return api;
	}
}
