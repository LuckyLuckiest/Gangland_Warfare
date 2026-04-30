package org.luckyraven.gangland.gang.contract;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.user.User;

/**
 * Read-side contract for looking up online users by Bukkit player handle. Exposed so sibling feature modules can
 * resolve a {@link User} without importing impl's {@code UserManager}.
 */
public interface UserLookupContract {

	@Nullable User<Player> findByPlayer(Player player);
}
