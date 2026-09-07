package org.luckyraven.gangland.file.configuration.gang;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

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
