package org.luckyraven.gangland.inventory.filter;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-player filter state keyed by {@code (bindingId, playerUuid)}. Replaces the domain-specific stores that used to
 * live in individual features (e.g. the old {@code GangSearchFilterStore}).
 */
public final class FilterStore {

	private final ConcurrentMap<FilterKey, SearchFilter> filters = new ConcurrentHashMap<>();
	private final FilterRegistry                         registry;

	public FilterStore(FilterRegistry registry) {
		this.registry = registry;
	}

	public SearchFilter get(String bindingId, Player player) {
		FilterKey    key    = new FilterKey(bindingId, player.getUniqueId());
		SearchFilter stored = filters.get(key);
		if (stored != null) return stored;
		FilterBinding binding = registry.get(bindingId);
		return binding != null ? binding.empty() : SearchFilter.empty(null);
	}

	public void set(String bindingId, Player player, SearchFilter filter) {
		filters.put(new FilterKey(bindingId, player.getUniqueId()), filter);
	}

	public void clear(String bindingId, Player player) {
		filters.remove(new FilterKey(bindingId, player.getUniqueId()));
	}

	public void clearAllForPlayer(Player player) {
		filters.keySet().removeIf(k -> k.playerId().equals(player.getUniqueId()));
	}

	public void clearAll() {
		filters.clear();
	}

	private record FilterKey(String bindingId, UUID playerId) { }

}
