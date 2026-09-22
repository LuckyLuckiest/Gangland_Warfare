package org.luckyraven.gangland.listener.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.RankPermissionApplier;

/**
 * Ensures a joining/bootstrapping player has a cached {@link Member} row and applies its rank permissions once their
 * {@link User} row is hydrated. Replaces both the {@link Member} creation that used to sit inline in
 * {@code CreateAccountListener}/{@code PlayerBootstrapService} and the last 2 of the 7 {@code ponytail}-tagged inline
 * bridges for the deleted {@code UserManager.initializeUserPermission} (WS5 G0, B2) — those two impl classes now
 * only create/hydrate the {@link User} row and fire {@link UserDataInitEvent}; this listener does the rest.
 *
 * <p>Dropped deliberately: the original inline bridges also re-queried the {@code member} DB row directly
 * (`MemberManager.initializeMemberData`) whenever the cached member had no gang yet. That is redundant —
 * {@code MemberManager.initialize()} (a {@code BeanLifecycle} bean) already loads every existing {@link Member} row
 * from the DB into the manager's cache on every boot <em>and</em> every reload, before any player can join, so a
 * cache miss here can only mean the player genuinely has no member row yet (a true first-ever join): a fresh,
 * rank-less {@link Member} is created and cached, exactly like the original's "not found" branch, and no permission
 * attach happens (no rank to apply, same as before).
 *
 * <p>{@link UserDataInitEvent} is fired synchronously by {@code Bukkit.getPluginManager().callEvent(...)} regardless
 * of the {@code async} flag it carries — that flag only records which thread the caller was on, so a handler invoked
 * from an async-fired event still runs on that same async thread and must hop back to the main thread before
 * touching {@link org.bukkit.permissions.PermissionAttachment} (main-thread-only Bukkit API), exactly like the
 * original {@code CreateAccountListener} bridge's {@code Bukkit.getScheduler().runTask(...)} hop.
 */
@ListenerHandler
public final class MemberJoinListener implements Listener {

	private final JavaPlugin    gangland;
	private final MemberManager memberManager;

	public MemberJoinListener(JavaPlugin gangland, MemberManager memberManager) {
		this.gangland      = gangland;
		this.memberManager = memberManager;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onUserDataInit(UserDataInitEvent event) {
		if (event.isAsynchronous()) {
			Bukkit.getScheduler().runTask(gangland, () -> apply(event.getPlayer(), event.getUser()));
			return;
		}

		apply(event.getPlayer(), event.getUser());
	}

	private void apply(Player player, User<Player> user) {
		if (!player.isOnline()) {
			return;
		}

		Member member = memberManager.getMember(player.getUniqueId());

		if (member == null) {
			// Genuinely new — MemberManager.initialize() already preloaded every existing row, so a miss here
			// means this player has never had a member row. No rank to apply yet.
			memberManager.add(new Member(player.getUniqueId()));
			return;
		}

		RankPermissionApplier.initialize(gangland, user, member);
	}

}
