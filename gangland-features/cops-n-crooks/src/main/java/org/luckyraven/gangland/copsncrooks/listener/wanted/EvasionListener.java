package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Objects;

/**
 * Ends the evasion tracking when the chase ends or the player leaves, and tells the clock about long teleports. The
 * chase arc itself ends in {@link ChaseArcListener}.
 */
@ListenerHandler
@RequiredArgsConstructor
public class EvasionListener implements Listener {

	/**
	 * A jump longer than this counts as a teleport. A constant: a car dismount moves the player a block or two.
	 */
	private static final double TELEPORT_BLOCKS = 32;

	private final EvasionClock clock;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		clock.clear(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		clock.clear(event.getPlayer());
	}

	/**
	 * A command, plugin or portal jump of more than 32 blocks or into another world rules out a clean break under
	 * {@code Drop_Mode: AUTO}; ender pearls and chorus fruit are ordinary movement.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		TeleportCause cause = event.getCause();
		if (cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CHORUS_FRUIT || cause == TeleportCause.UNKNOWN) {
			return;
		}

		Location from = event.getFrom();
		Location to   = event.getTo();
		if (to == null) return;

		if (!Objects.equals(from.getWorld(), to.getWorld())
		    || from.distanceSquared(to) > TELEPORT_BLOCKS * TELEPORT_BLOCKS) {
			clock.teleported(event.getPlayer());
		}
	}
}
