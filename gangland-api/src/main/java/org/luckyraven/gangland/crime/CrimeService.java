package org.luckyraven.gangland.crime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;

/**
 * The crime event bus: modules report a crime here and whoever cares (the heat ledger) listens for
 * {@link CrimeCommittedEvent}.
 */
public final class CrimeService {

	/**
	 * Main thread only. Fires {@link CrimeCommittedEvent} through Bukkit.
	 *
	 * @return true when no listener cancelled it
	 */
	public boolean commit(Player player, String crimeId, Location location, boolean seenByCop, int witnesses) {
		CrimeCommittedEvent event = new CrimeCommittedEvent(player, crimeId, location, seenByCop, witnesses);

		Bukkit.getPluginManager().callEvent(event);

		return !event.isCancelled();
	}

	/**
	 * Same as {@link #commit(Player, String, Location, boolean, int)} with {@code seenByCop} false and no witnesses.
	 */
	public boolean commit(Player player, String crimeId, Location location) {
		return commit(player, crimeId, location, false, 0);
	}

}
