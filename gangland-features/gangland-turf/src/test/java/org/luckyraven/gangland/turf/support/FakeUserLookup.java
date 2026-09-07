package org.luckyraven.gangland.turf.support;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.gang.user.User;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Identity-keyed fake for {@link UserLookupContract}. Real {@code User<Player>} construction needs a live
 * {@code JavaPlugin}/{@code Placeholder}/{@code InventoryRegistry} plus {@code GangSettings} bound, so tests hand
 * this fake Mockito mocks of {@code User<Player>} keyed by the exact mock {@code Player} instance.
 */
public final class FakeUserLookup implements UserLookupContract {

	private final Map<Player, User<Player>> byPlayer = new IdentityHashMap<>();

	public void register(Player player, User<Player> user) {
		byPlayer.put(player, user);
	}

	@Override
	public User<Player> findByPlayer(Player player) {
		return byPlayer.get(player);
	}
}
