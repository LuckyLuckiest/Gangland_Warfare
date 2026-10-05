package org.luckyraven.gangland.core.wanted;

import org.bukkit.entity.Player;

/**
 * A module-installed driver of star decay (cops-n-crooks' evasion clock). Installed through
 * {@link WantedStars#installDecayPolicy(WantedDecayPolicy)}; while it answers true the Repeating_Timer safety net
 * leaves the player alone.
 */
@FunctionalInterface
public interface WantedDecayPolicy {

	/**
	 * True while this policy drives the player's star decay, so the Repeating_Timer tick does nothing. Main thread.
	 * The policy never gets a wallet: it lowers stars only through {@link WantedStars#drop}.
	 */
	boolean handlesDecay(Player player, Wanted wanted);
}
