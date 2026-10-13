package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.trespass.TrespassTracker;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.keystone.bean.autowire.Autowired;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Walking or teleporting into an admin region tagged {@code restricted} commits {@code Trespass_Restricted} as seen by
 * a cop. Edge-triggered (entering, not standing) and throttled per player and region.
 *
 * @since 0.20.0
 */
@CustomLog
@ListenerHandler
public class TrespassListener implements Listener {

	/** Permission that exempts a player from trespass crimes. */
	public static final String BYPASS_PERMISSION = "gangland.cop.trespass.bypass";

	@Autowired
	public TrespassListener(AdminRegionRegistry regions, TrespassTracker tracker, DetainmentService detainment,
	                        CrimeService crimes, ChaseConfigLoader config, WantedMessages messages) {
		this(regions, tracker, detainment, crimes, config, messages, System::currentTimeMillis);
	}

	private final AdminRegionRegistry regions;
	private final TrespassTracker     tracker;
	private final DetainmentService   detainment;
	private final CrimeService        crimes;
	private final ChaseConfigLoader   config;
	private final WantedMessages      messages;
	private final LongSupplier        clock;

	TrespassListener(AdminRegionRegistry regions, TrespassTracker tracker, DetainmentService detainment,
	                 CrimeService crimes, ChaseConfigLoader config, WantedMessages messages, LongSupplier clock) {
		this.regions    = regions;
		this.tracker    = tracker;
		this.detainment = detainment;
		this.crimes     = crimes;
		this.config     = config;
		this.messages   = messages;
		this.clock      = clock;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		Location from = event.getFrom();
		Location to   = event.getTo();
		if (to == null) return;

		if (from.getWorld() == to.getWorld() && from.getBlockX() == to.getBlockX() &&
		    from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
			return;
		}

		handle(event.getPlayer(), to);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		Location to = event.getTo();
		if (to == null) return;

		handle(event.getPlayer(), to);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		tracker.clear(event.getPlayer().getUniqueId());
	}

	private void handle(Player player, Location to) {
		World world = to.getWorld();
		if (world == null) return;

		Map<String, String> ids = new HashMap<>();
		for (PlaceRegion region : regions.regionsAt(to)) {
			if (region.hasTag(PlaceRegion.TAG_RESTRICTED)) ids.put(region.id(), region.name());
		}

		// always track, so a player released while inside is not charged for an entry they were exempt for
		Set<String> entered = tracker.entered(player.getUniqueId(), ids.keySet());
		if (entered.isEmpty() || exempt(player)) return;

		long repeatMillis = config.getTrespass().repeatSeconds() * 1000L;
		for (String id : entered) {
			if (!tracker.claim(player.getUniqueId(), id, clock.getAsLong(), repeatMillis)) continue;

			crimes.commit(player, Crimes.TRESPASS_RESTRICTED, to, true, 0);
			player.sendMessage(messages.format(WantedMessages.Key.TRESPASS, Map.of()));
			log.debug("TRESPASS {} region={}", player.getName(), ids.get(id));
		}
	}

	private boolean exempt(Player player) {
		if (!config.getTrespass().enabled() || detainment.isRestrained(player) ||
		    player.hasPermission(BYPASS_PERMISSION)) {
			return true;
		}

		GameMode mode = player.getGameMode();
		return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
	}
}
