package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.Player;

import java.math.BigDecimal;

/**
 * Seam a module installs to divert cash a wanted player would otherwise receive in the wallet ("hot cash").
 *
 * @since 2.7
 */
public interface HotCash {

	/** Where a take went. */
	enum Take {
		WALLET,
		BAGGED,
		FULL
	}

	/**
	 * @param source {@code "pickup"} or {@code "store"}
	 */
	Take take(Player player, BigDecimal amount, String source);

	/** Turns the player's hot cash into wallet cash; true when something was sold. */
	boolean fence(Player player);

}
