package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.command.CommandManager;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.listener.ListenerPriority;
import me.luckyraven.util.placeholder.replacer.Replacer;
import org.bukkit.entity.Player;

/**
 * CONFIG-phase wiring for the cross-cutting plugin glue: the {@link ListenerManager} and {@link CommandManager} (which
 * are themselves consumed in the LISTENER and COMMAND post-bootstrap steps driven by {@code GanglandContext}), the
 * PlaceholderAPI bridge {@link GanglandPlaceholder}, and the legacy "dummy waypoint listener" that has to be
 * pre-registered before {@code listenerManager.registerEvents()} is called.
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

	@Bean
	public GanglandPlaceholder ganglandPlaceholder(@Qualifier("online") UserManager<Player> userManager,
	                                               MemberManager memberManager,
	                                               GangManager gangManager,
	                                               UniqueItemAddon uniqueItemAddon,
	                                               me.luckyraven.copsncrooks.npc.banker.tier.BankTierRegistry
														   bankTierRegistry,
	                                               PlaceholderService placeholderService) {
		return new GanglandPlaceholder(Gangland.FULL_PREFIX, Replacer.Closure.PERCENT,
		                               userManager, memberManager, gangManager,
		                               uniqueItemAddon, bankTierRegistry, placeholderService);
	}
}
