package org.luckyraven.gangland.gang.vault.permission;

import lombok.CustomLog;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Static facade over Vault's {@link Permission} service. Mirrors the cadence of {@link EconomyHandler#setVaultEconomy}:
 * the bridge is plugged in from {@code JavaPlugin.onEnable} once a Vault {@code Permission} provider is registered, and
 * cleared on {@code onDisable}. Every public method is a safe no-op when the provider is absent, so callers never need
 * to guard.
 *
 * <p>The class is intentionally static (not a bean) so {@link Rank} and {@link Member} — both POJOs
 * with no DI — can reach it without injection.
 */
@CustomLog
public final class VaultPermissionBridge {

	private static @Nullable Permission vault;
	private static @Nullable Plugin     plugin;

	private VaultPermissionBridge() {
	}

	public static void set(@Nullable Permission provider, @Nullable Plugin owner) {
		vault  = provider;
		plugin = owner;
	}

	public static boolean isEnabled() {
		return vault != null;
	}

	/**
	 * Returns every group the backing permission plugin knows about, or an empty list if Vault is absent or the
	 * provider does not expose groups. Used primarily to power tab-completion for rank→group mappings.
	 */
	public static List<String> getGroups() {
		if (vault == null) return Collections.emptyList();
		try {
			String[] groups = vault.getGroups();
			return groups == null ? Collections.emptyList() : List.of(groups);
		} catch (Exception exception) {
			log.warn("Vault getGroups failed: {}", exception.getMessage());
			return Collections.emptyList();
		}
	}

	/**
	 * Fallback permission read: returns true only if Vault is active and reports the node. Safe on offline players —
	 * Vault's contract allows offline queries when the backing permission plugin supports them.
	 */
	public static boolean has(@Nullable OfflinePlayer player, String node) {
		if (vault == null || player == null || node == null || node.isEmpty()) return false;
		try {
			return vault.playerHas(null, player, node);
		} catch (Exception exception) {
			log.warn("Vault playerHas failed for {} / {}: {}", player.getName(), node, exception.getMessage());
			return false;
		}
	}

	public static void grant(@Nullable OfflinePlayer player, String node) {
		if (vault == null || player == null || node == null || node.isEmpty()) return;
		dispatch(player, () -> {
			try {
				vault.playerAdd(null, player, node);
			} catch (Exception exception) {
				log.warn("Vault playerAdd failed for {} / {}: {}", player.getName(), node, exception.getMessage());
			}
		});
	}

	public static void revoke(@Nullable OfflinePlayer player, String node) {
		if (vault == null || player == null || node == null || node.isEmpty()) return;
		dispatch(player, () -> {
			try {
				vault.playerRemove(null, player, node);
			} catch (Exception exception) {
				log.warn("Vault playerRemove failed for {} / {}: {}", player.getName(), node, exception.getMessage());
			}
		});
	}

	public static void addToGroup(@Nullable OfflinePlayer player, @Nullable String group) {
		if (vault == null || player == null || group == null || group.isEmpty()) return;
		dispatch(player, () -> {
			try {
				vault.playerAddGroup(null, player, group);
			} catch (Exception exception) {
				log.warn("Vault playerAddGroup failed for {} / {}: {}", player.getName(), group,
				         exception.getMessage());
			}
		});
	}

	public static void removeFromGroup(@Nullable OfflinePlayer player, @Nullable String group) {
		if (vault == null || player == null || group == null || group.isEmpty()) return;

		// Never strip the player from the permission provider's default group — every player is implicitly a
		// member of it, and dropping them out (e.g. when a rank linked to "default" is cleared) takes down every
		// perm granted at the default level (gangland.command.*, common command access, etc.). This used to
		// surface as "all permissions disappear when kicked from a gang" if an admin linked a gang rank to the
		// LP default group.
		if (isDefaultGroup(group)) return;

		dispatch(player, () -> {
			try {
				vault.playerRemoveGroup(null, player, group);
			} catch (Exception exception) {
				log.warn("Vault playerRemoveGroup failed for {} / {}: {}", player.getName(), group,
				         exception.getMessage());
			}
		});
	}

	/**
	 * Swaps a single member's Vault state between two ranks: revokes the old rank's nodes/group, grants the new rank's
	 * nodes/group. Either side may be {@code null} (fresh assignment or full clear).
	 */
	public static void onRankTransition(UUID uuid, @Nullable Rank oldRank, @Nullable Rank newRank) {
		if (vault == null || uuid == null) return;

		OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

		if (oldRank != null) {
			for (org.luckyraven.gangland.gang.rank.Permission perm : oldRank.getPermissions()) {
				revoke(player, perm.getPermission());
			}
			removeFromGroup(player, oldRank.getVaultGroup());
		}

		if (newRank != null) {
			for (org.luckyraven.gangland.gang.rank.Permission perm : newRank.getPermissions()) {
				grant(player, perm.getPermission());
			}
			addToGroup(player, newRank.getVaultGroup());
		}
	}

	/**
	 * Pushes a single permission-node change onto every member currently wearing the affected rank. Invoked after
	 * {@code RankManager.addPermission / removePermission} completes, so the in-memory rank list is authoritative and
	 * Vault is the downstream mirror.
	 */
	public static void applyPermissionChange(@Nullable Collection<Member> affected, String node, boolean added) {
		if (vault == null || affected == null || affected.isEmpty() || node == null || node.isEmpty()) return;

		for (Member member : affected) {
			OfflinePlayer player = Bukkit.getOfflinePlayer(member.getUuid());

			if (added) {
				grant(player, node);
			} else {
				revoke(player, node);
			}
		}
	}

	private static boolean isDefaultGroup(String group) {
		// Vault's Permission abstraction doesn't expose a default-group lookup, so match the conventional name
		// instead. LuckPerms, PermissionsEx, GroupManager and bPermissions all ship with "default" as the
		// always-applied group; admins who renamed it can extend this guard via configuration if they need to.
		return "default".equalsIgnoreCase(group);
	}

	/**
	 * Runs a Vault mutation on the correct thread: sync when the target is online (LuckPerms already has the user
	 * resident) or when no plugin is available for scheduling; async otherwise, since LuckPerms refuses main-thread
	 * lookups for offline players.
	 */
	private static void dispatch(@Nullable OfflinePlayer player, Runnable call) {
		boolean online = player != null && player.isOnline();
		if (online || plugin == null || !Bukkit.isPrimaryThread()) {
			call.run();
			return;
		}
		Bukkit.getScheduler().runTaskAsynchronously(plugin, call);
	}

}
