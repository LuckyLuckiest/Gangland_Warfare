package me.luckyraven.data.account.gang;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class GangSearchFilterStore {

	private final ConcurrentMap<UUID, GangSearchFilter> filters = new ConcurrentHashMap<>();

	public GangSearchFilter get(Player player) {
		return filters.getOrDefault(player.getUniqueId(), GangSearchFilter.EMPTY);
	}

	public void set(Player player, GangSearchFilter filter) {
		filters.put(player.getUniqueId(), filter);
	}

	public void clear(Player player) {
		filters.remove(player.getUniqueId());
	}

}
