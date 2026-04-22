package me.luckyraven.gang.contract;

import me.luckyraven.gang.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Read-side contract for looking up online users by Bukkit player handle. Exposed so sibling feature modules can
 * resolve a {@link User} without importing impl's {@code UserManager}.
 */
public interface UserLookupContract {

	@Nullable User<Player> findByPlayer(Player player);
}
