package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.CommandManager;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTierRegistry;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.bean.listener.ListenerPriority;
import org.luckyraven.keystone.placeholder.replacer.Replacer;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointTeleport;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.listener.ListenerManager;

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
	                                               BankTierRegistry bankTierRegistry,
	                                               PlaceholderService placeholderService) {
		return new GanglandPlaceholder(Gangland.FULL_PREFIX, Replacer.Closure.PERCENT,
		                               userManager, memberManager, gangManager,
		                               uniqueItemAddon, bankTierRegistry, placeholderService);
	}
}
