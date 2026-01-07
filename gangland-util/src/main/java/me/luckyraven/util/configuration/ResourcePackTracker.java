package me.luckyraven.util.configuration;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ResourcePackTracker {

	private static final Set<UUID> loadedPlayers = new HashSet<>();

	public static void markLoaded(Player player) {
		loadedPlayers.add(player.getUniqueId());
	}

	public static void markUnloaded(Player player) {
		loadedPlayers.remove(player.getUniqueId());
	}

	public static boolean hasResourcePack(Player player) {
		return loadedPlayers.contains(player.getUniqueId());
	}

	public static void clear() {
		loadedPlayers.clear();
	}

}
