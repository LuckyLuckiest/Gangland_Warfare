package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.command.CommandManager;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.PostConstruct;
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
		return new ListenerManager(gangland, container);
	}

	@Bean
	public CommandManager commandManager(DependencyContainer container) {
		return new CommandManager(gangland, container, Gangland.FULL_PREFIX, Gangland.SHORT_PREFIX);
	}

	/**
	 * The constructor of {@link GanglandPlaceholder} reads {@code userManager}, {@code memberManager},
	 * {@code gangManager} and {@code uniqueItemAddon} off {@code gangland.getInitializer()} and stores them in
	 * {@code final} fields. Listing those beans as parameters here forces the topo sort to construct them first AND
	 * fires the per-bean hydrate hook in {@code GanglandContext} so that {@code Initializer.memberManager} (etc.) is
	 * non-null by the time the placeholder constructor reads it. Without these parameters, the placeholder captures
	 * null and every scoreboard line silently renders as {@code <>} because {@code getMember(uuid)} NPEs deep inside
	 * the placeholder evaluation chain.
	 */
	@Bean
	public GanglandPlaceholder ganglandPlaceholder(@Qualifier("online") UserManager<Player> userManager,
	                                               MemberManager memberManager,
	                                               GangManager gangManager,
	                                               UniqueItemAddon uniqueItemAddon) {
		return new GanglandPlaceholder(gangland, Gangland.FULL_PREFIX, Replacer.Closure.PERCENT);
	}

	/**
	 * Pre-registers the {@code dummy} waypoint listener with the {@link ListenerManager} before
	 * {@code GanglandContext.runListenerPhase()} calls {@code registerEvents()}. This used to live at the bottom of the
	 * legacy {@code Initializer.events()} method.
	 */
	@PostConstruct
	public void registerDummyWaypointListener() {
		ListenerManager  listenerManager = gangland.getInitializer().getContext().get(ListenerManager.class);
		Waypoint         dummy           = new Waypoint("dummy", Gangland.FULL_PREFIX);
		WaypointTeleport dummyTeleport   = new WaypointTeleport(dummy);
		listenerManager.addEvent(dummyTeleport, ListenerPriority.NORMAL);
	}
}
