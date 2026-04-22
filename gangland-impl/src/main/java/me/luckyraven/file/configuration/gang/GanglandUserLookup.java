package me.luckyraven.file.configuration.gang;

import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter over the impl-side {@link UserManager} so feature modules can resolve a {@link User} through
 * {@link UserLookupContract}.
 */
public final class GanglandUserLookup implements UserLookupContract {

	private final UserManager<Player> delegate;

	public GanglandUserLookup(UserManager<Player> delegate) {
		this.delegate = delegate;
	}

	@Override
	public @Nullable User<Player> findByPlayer(Player player) {
		return delegate.getUser(player);
	}
}
