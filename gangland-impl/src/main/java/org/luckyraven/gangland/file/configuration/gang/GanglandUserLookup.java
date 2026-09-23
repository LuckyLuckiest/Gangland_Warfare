package org.luckyraven.gangland.file.configuration.gang;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;

import org.luckyraven.keystone.bean.Qualifier;
/**
 * Adapter over the impl-side {@link UserManager} so feature modules can resolve a {@link User} through
 * {@link UserLookupContract}.
 */
public final class GanglandUserLookup implements UserLookupContract {

	private final UserManager<Player> delegate;

	public GanglandUserLookup(@Qualifier("online") UserManager<Player> delegate) {
		this.delegate = delegate;
	}

	@Override
	public @Nullable User<Player> findByPlayer(Player player) {
		return delegate.getUser(player);
	}
}
