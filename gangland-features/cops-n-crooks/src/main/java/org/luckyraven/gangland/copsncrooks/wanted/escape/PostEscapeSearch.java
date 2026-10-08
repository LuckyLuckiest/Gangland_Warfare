package org.luckyraven.gangland.copsncrooks.wanted.escape;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.user.UserManager;

import java.util.function.LongSupplier;

/**
 * The post-escape search (0.16.1 T-187): cops keep looking for a player who escaped with a bounty on him.
 * <p>
 * stub (0.16.1 T-187): {@link #begin} does nothing yet; the bounty, the search record and the expiry are not written.
 */
public final class PostEscapeSearch {

	public PostEscapeSearch(UserManager<Player> users, LongSupplier clock) {
	}

	/** An escape by evasion took the last star; {@code oldLevel} is the star count before that drop. */
	public void begin(Player player, int oldLevel) {
	}
}
