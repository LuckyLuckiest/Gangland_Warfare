package org.luckyraven.gangland.gang.permission;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;

/**
 * Applies a member's rank permissions onto their live {@link PermissionAttachment}. Replaces the deleted
 * {@code User.flushPermissions(Rank)}/{@code UserManager.initializeUserPermission(User,Member)} (WS5 G0, B2) and
 * every one of the 7 {@code ponytail}-tagged temporary inline bridges that stood in for them once those methods
 * were deleted from core but the gang module didn't exist yet to receive their bodies (WS5 G0 fix round 1 F1;
 * replaced here per WS5 G1+G2+G3 step 11b): {@code Gang.removeMember}, {@code GangDemoteCommand},
 * {@code GangPromoteCommand}, {@code GangTransferCommand} (×2), {@code PlayerBootstrapService},
 * {@code CreateAccountListener}.
 *
 * <p>Static, not a bean — {@link org.luckyraven.gangland.gang.Gang} is a plain domain object (constructed with
 * {@code new}, never DI-managed), and it's one of the 7 call sites (via {@code removeMember}), so this class has
 * to be reachable without injection, the same way {@code GangPermissions}/{@code VaultPermissionBridge} are.
 */
public final class RankPermissionApplier {

	private RankPermissionApplier() {
	}

	/**
	 * First-time attach: a fresh {@link PermissionAttachment}, granting the member's current rank's nodes.
	 * Mirrors the deleted {@code UserManager.initializeUserPermission(User,Member)} exactly, including its
	 * main-thread-only requirement (the caller must already be on the main thread — {@code PermissionAttachment}/
	 * {@code updateCommands()} are not safe off it).
	 *
	 * @param plugin the plugin instance {@code PermissionAttachment} is registered under
	 * @param user   the online user to attach to; a no-op if {@code member} has no rank
	 * @param member the member whose current rank supplies the granted nodes
	 */
	public static void initialize(JavaPlugin plugin, User<Player> user, Member member) {
		Rank rank = member.getRank();
		if (rank == null) return;

		PermissionAttachment attachment = user.getUser().addAttachment(plugin);
		user.setPermissionAttachment(attachment);

		for (Permission perm : rank.getPermissions()) {
			user.setPermission(perm.getPermission(), true);
		}
		user.updateCommands();
	}

	/**
	 * Re-applies after a promote/demote/transfer/leave/kick/disband: unsets every currently-granted node (not an
	 * explicit {@code setPermission(_, false)} deny — an unset node still falls back to a Vault/LuckPerms group
	 * grant of the same node, which an explicit deny would override), then grants {@code rank}'s nodes if any.
	 * Mirrors the deleted {@code User.flushPermissions(Rank)} exactly. A no-op (beyond the command-list refresh)
	 * when the user never had a {@code PermissionAttachment} — {@link User#grantedPermissionNames()}/
	 * {@link User#setPermission} are already null-safe.
	 *
	 * @param user the user to re-apply onto; both online and offline users are safe (every primitive this calls
	 *             is a null-safe no-op for an offline/attachment-less user)
	 * @param rank the new rank to grant, or {@code null} to clear only (a removed/kicked member has no rank left)
	 */
	public static void flush(User<? extends OfflinePlayer> user, @Nullable Rank rank) {
		for (String permission : user.grantedPermissionNames()) {
			user.unsetPermission(permission);
		}

		if (rank != null) {
			for (Permission perm : rank.getPermissions()) {
				user.setPermission(perm.getPermission(), true);
			}
		}

		user.updateCommands();
	}
}
