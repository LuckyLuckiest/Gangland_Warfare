package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
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
import org.luckyraven.keystone.bean.listener.ListenerHandler;

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

	public TrespassListener(AdminRegionRegistry regions, TrespassTracker tracker, DetainmentService detainment,
	                        CrimeService crimes, ChaseConfigLoader config, WantedMessages messages) {
		this(regions, tracker, detainment, crimes, config, messages, System::currentTimeMillis);
	}

	TrespassListener(AdminRegionRegistry regions, TrespassTracker tracker, DetainmentService detainment,
	                 CrimeService crimes, ChaseConfigLoader config, WantedMessages messages, LongSupplier clock) {
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
