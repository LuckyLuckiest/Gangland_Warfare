package org.luckyraven.gangland.gang.vault.permission;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.keystone.vault.permission.OfflinePermissionService;
import org.luckyraven.keystone.vault.permission.VaultOfflinePermissionService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Static domain facade over Keystone's {@link OfflinePermissionService}. Keystone owns the Vault plumbing —
 * threading dispatch (async for offline targets from the main thread), the protected default-group guard, and
 * fault reporting (see {@link VaultOfflinePermissionService}); this class keeps only Gangland's rank/member
 * semantics. Plugged in from {@code JavaPlugin.onEnable} once Vault is detected, cleared on {@code onDisable};
 * every method is a safe no-op when no service is installed, so callers never guard.
 *
 * <p>The class is intentionally static (not a bean) so {@link Rank} and {@link Member} — both POJOs with no DI —
 * can reach it without injection.
 */
public final class VaultPermissionBridge {

	private static @Nullable OfflinePermissionService service;

	private VaultPermissionBridge() {
	}

	public static void set(@Nullable OfflinePermissionService provider) {
		service = provider;
	}

	public static boolean isEnabled() {
		return service != null;
	}

	/**
	 * Returns every group the backing permission plugin knows about, or an empty list if Vault is absent or the
	 * provider does not expose groups. Used primarily to power tab-completion for rank→group mappings.
	 */
	public static List<String> getGroups() {
		return service == null ? Collections.emptyList() : service.groups();
	}

	/**
	 * Fallback permission read: returns true only if Vault is active and reports the node.
	 */
	public static boolean has(@Nullable OfflinePlayer player, String node) {
		return service != null && service.has(player, node);
	}

	public static void grant(@Nullable OfflinePlayer player, String node) {
		if (service != null) service.grant(player, node);
	}

	public static void revoke(@Nullable OfflinePlayer player, String node) {
		if (service != null) service.revoke(player, node);
	}

	public static void addToGroup(@Nullable OfflinePlayer player, @Nullable String group) {
		if (service != null) service.addToGroup(player, group);
	}

	public static void removeFromGroup(@Nullable OfflinePlayer player, @Nullable String group) {
		if (service != null) service.removeFromGroup(player, group);
	}

	/**
	 * Swaps a single member's Vault state between two ranks: revokes the old rank's nodes/group, grants the new
	 * rank's nodes/group. Either side may be {@code null} (fresh assignment or full clear).
	 */
	public static void onRankTransition(UUID uuid, @Nullable Rank oldRank, @Nullable Rank newRank) {
		if (service == null || uuid == null) return;

		OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

		if (oldRank != null) {
			for (Permission perm : oldRank.getPermissions()) {
				revoke(player, perm.getPermission());
			}
			removeFromGroup(player, oldRank.getVaultGroup());
		}

		if (newRank != null) {
			for (Permission perm : newRank.getPermissions()) {
				grant(player, perm.getPermission());
			}
			addToGroup(player, newRank.getVaultGroup());
		}
	}

	/**
	 * Pushes a single permission-node change onto every member currently wearing the affected rank. Invoked after
	 * {@code RankManager.addPermission / removePermission} completes, so the in-memory rank list is authoritative
	 * and Vault is the downstream mirror.
	 */
	public static void applyPermissionChange(@Nullable Collection<Member> affected, String node, boolean added) {
		if (service == null || affected == null || affected.isEmpty() || node == null || node.isEmpty()) return;

		for (Member member : affected) {
			OfflinePlayer player = Bukkit.getOfflinePlayer(member.getUuid());

			if (added) {
				grant(player, node);
			} else {
				revoke(player, node);
			}
		}
	}

}
