package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.entity.Player;

import java.math.BigDecimal;

/**
 * Where a finished hold-up's take goes. The default wallet deposit is replaced when the bag ships.
 */
public interface HoldUpPayout {

	/**
	 * @return the amount paid, {@link BigDecimal#ZERO} when nothing could be paid
	 */
	BigDecimal pay(Player player);

}
