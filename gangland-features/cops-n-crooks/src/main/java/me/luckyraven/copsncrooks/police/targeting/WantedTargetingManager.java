package me.luckyraven.copsncrooks.police.targeting;

import me.luckyraven.copsncrooks.wanted.Wanted;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WantedTargetingManager implements TargetingManager {

	private final Map<UUID, Wanted> wantedPlayers;

	public WantedTargetingManager() {
		this.wantedPlayers = new ConcurrentHashMap<>();
	}

	@Override
	public void registerWanted(Player player, Wanted wanted) {
		wantedPlayers.put(player.getUniqueId(), wanted);
	}

	@Override
	public void unregisterWanted(UUID playerId) {
		wantedPlayers.remove(playerId);
	}

	@Override
	public boolean isWanted(UUID playerId) {
		Wanted wanted = wantedPlayers.get(playerId);
		return wanted != null && wanted.isWanted();
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
			if (!entry.getValue().isWanted()) continue;

			Player candidate = Bukkit.getPlayer(entry.getKey());

			if (candidate == null || !candidate.isOnline()) continue;
			if (!candidate.getWorld().equals(from.getWorld())) continue;

			double distance = candidate.getLocation().distanceSquared(from.getLocation());

			if (distance >= bestDistance) continue;

			bestDistance = distance;
			bestTarget   = candidate;
		}

		return bestTarget;
	}
}