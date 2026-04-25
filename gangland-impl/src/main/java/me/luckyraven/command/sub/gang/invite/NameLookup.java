package me.luckyraven.command.sub.gang.invite;

import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Resolves a player by name through the cached {@link UserManager} maps instead of {@link org.bukkit.Bukkit}'s
 * deprecated string-based offline-player lookup. Online users are checked first; if no match, the offline user cache
 * (DB-backed) is searched. Comparison is case-insensitive.
 */
final class NameLookup {

	private NameLookup() { }

	/**
	 * @return the matching online user, the matching offline user, or {@code null} if no cached user has the given
	 * 		name. Callers may need to cast or branch on the type parameter, but {@link User#getUser()} +
	 *        {@link User#hasGang()} both work uniformly across the bound.
	 */
	static User<?> findByName(String name, UserManager<Player> online, UserManager<OfflinePlayer> offline) {
		if (name == null || name.isEmpty()) return null;

		for (User<Player> u : online.getUsers().values()) {
			Player p = u.getUser();
			if (p != null && name.equalsIgnoreCase(p.getName())) return u;
		}

		for (User<OfflinePlayer> u : offline.getUsers().values()) {
			OfflinePlayer p = u.getUser();
			if (p != null && name.equalsIgnoreCase(p.getName())) return u;
		}

		// Tab completion sources offline names from Bukkit.getOfflinePlayers(); some of those players may not yet have
		// a cached User<OfflinePlayer> (e.g. their data hasn't been persisted, or they joined before this plugin was
		// installed). Construct one on demand and stash it so the rest of the invite flow has a User<?> to work with.
		for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
			String pname = offlinePlayer.getName();
			if (!name.equalsIgnoreCase(pname)) continue;

			// Re-check the cache by OfflinePlayer (UUID-based equals) in case the name comparison above missed it
			// because the cached instance returned a null name.
			User<OfflinePlayer> existing = offline.getUser(offlinePlayer);
			if (existing != null) return existing;

			User<OfflinePlayer> created = offline.create(offlinePlayer);
			offline.add(created);
			return created;
		}

		return null;
	}

}
