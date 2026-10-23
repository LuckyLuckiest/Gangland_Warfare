package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.entity.Player;

import java.math.BigDecimal;

/**
 * What a bribe may draw from a player's bag; cops-internal.
 *
 * @since 0.20.0
 */
public interface BagFunding {

	BigDecimal available(Player player);

	void debit(Player player, BigDecimal amount);

	/** After a bribe was paid from the bag: prints {@code Bag.Bribe} and the debug line. */
	void announceBribe(Player player, BigDecimal fromBag, BigDecimal fromWallet);
}
