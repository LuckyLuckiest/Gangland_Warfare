package org.luckyraven.gangland.copsncrooks.npc.police.targeting;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.wanted.Wanted;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WantedTargetingManager implements TargetingManager {

	private final Map<UUID, Wanted> wantedPlayers;
	/** Players on a post-escape search (0.16.1 T-187): cops keep targeting them though their wanted level is 0. */
	private final Set<UUID>         searching;

	public WantedTargetingManager() {
		this.wantedPlayers = new ConcurrentHashMap<>();
		this.searching     = ConcurrentHashMap.newKeySet();
	}

	/** A new wanted start ends any post-escape search. */
	@Override
	public void registerWanted(Player player, Wanted wanted) {
		wantedPlayers.put(player.getUniqueId(), wanted);
		searching.remove(player.getUniqueId());
	}

	@Override
	public void unregisterWanted(UUID playerId) {
		wantedPlayers.remove(playerId);
		searching.remove(playerId);
	}

	@Override
	public boolean isWanted(UUID playerId) {
		if (searching.contains(playerId)) return true;

		Wanted wanted = wantedPlayers.get(playerId);
		return wanted != null && wanted.isWanted();
	}

	/** Marks {@code playerId} as searched for after an escape (0.16.1 T-187). */
	public void markSearching(UUID playerId) {
		searching.add(playerId);
	}

	/** Ends the post-escape mark of {@code playerId}; the wanted record is untouched. */
	public void clearSearching(UUID playerId) {
		searching.remove(playerId);
	}

	/** Whether {@code playerId} is on a post-escape search. */
	@Override
	public boolean isSearching(UUID playerId) {
		return searching.contains(playerId);
	}

	@Override
	public int getWantedLevel(UUID playerId) {
		Wanted wanted = wantedPlayers.get(playerId);
		return wanted != null ? wanted.getLevel() : 0;
	}

	@Override
	@Nullable
	public Player findBestTarget(Player from) {
		Player bestTarget   = null;
		double bestDistance = Double.MAX_VALUE;

		for (Map.Entry<UUID, Wanted> entry : wantedPlayers.entrySet()) {
			if (!entry.getValue().isWanted() && !searching.contains(entry.getKey())) continue;

			Player candidate = Bukkit.getPlayer(entry.getKey());

			if (candidate == null || !candidate.isOnline() || candidate.isDead()) continue;
			if (!candidate.getWorld().equals(from.getWorld())) continue;

			double distance = candidate.getLocation().distanceSquared(from.getLocation());

			if (distance >= bestDistance) continue;

			bestDistance = distance;
			bestTarget   = candidate;
		}

		return bestTarget;
	}
}