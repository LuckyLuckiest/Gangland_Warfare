package org.luckyraven.gangland.gang.permission;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.keystone.util.Placeholder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link RankPermissionApplier} — the WS5 G2 replacement for all 7 {@code ponytail}-tagged inline bridges
 * that stood in for the deleted {@code UserManager.initializeUserPermission}/{@code User.flushPermissions(Rank)}
 * (WS5 G0, B2). {@link #flush_unsetsRatherThanDenies()} is the same unset-vs-deny distinction
 * {@code UserTest}'s {@code onGangTransfer_clearsOldPermissionsWithUnset}-style case pins on {@link User} itself
 * (WS5 G0 fix round 1, F1) — re-verified here because this class is the new call site every real bridge now goes
 * through, and a regression back to {@code setPermission(_, false)} here would silently break Vault/LuckPerms
 * group-grant fallback again without either test failing on its own.
 */
@DisplayName("RankPermissionApplier - initialize/flush")
class RankPermissionApplierTest {

	@BeforeEach
	void bindSettings() {
		IdentitySettings.bind(new FakeIdentitySettingsContract());
	}

	@Test
	@DisplayName("initialize: a member with a rank gets a fresh attachment carrying every one of the rank's permissions")
	void initialize_grantsEveryRankPermission() {
		JavaPlugin plugin = mock(JavaPlugin.class);

		Rank rank = new Rank("Recruit", 1, List.of(new Permission(1, "gangland.turf.capture"),
		                                           new Permission(2, "gangland.turf.contribute")));

		Member member = new Member(UUID.randomUUID());
		member.setRank(rank);

		User<Player>          user       = newOnlineUser(plugin);
		PermissionAttachment   attachment = mock(PermissionAttachment.class);
		when(user.getUser().addAttachment(plugin)).thenReturn(attachment);
		when(attachment.getPermissions()).thenReturn(Map.of());

		RankPermissionApplier.initialize(plugin, user, member);

		verify(attachment).setPermission("gangland.turf.capture", true);
		verify(attachment).setPermission("gangland.turf.contribute", true);
		verify(user.getUser()).updateCommands();
	}

	@Test
	@DisplayName("initialize: a member with no rank never attaches — a no-op, matching the deleted bridge's `if (rank != null)` gate")
	void initialize_noRank_isNoop() {
		JavaPlugin   plugin = mock(JavaPlugin.class);
		Member       member = new Member(UUID.randomUUID());
		User<Player> user   = newOnlineUser(plugin);

		RankPermissionApplier.initialize(plugin, user, member);

		assertNull(user.getPermissionAttachment(), "no attachment should ever be created for a rank-less member");
	}

	@Test
	@DisplayName("flush: clears every currently-granted node via unsetPermission, never an explicit setPermission(_, false) deny")
	void flush_unsetsRatherThanDenies() {
		JavaPlugin plugin = mock(JavaPlugin.class);

		Rank newRank = new Rank("Officer", 2, List.of(new Permission(3, "gangland.turf.upgrade")));

		User<Player>        user       = newOnlineUser(plugin);
		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(Map.of("old.node", true));
		user.setPermissionAttachment(attachment);

		RankPermissionApplier.flush(user, newRank);

		// The whole point of unsetPermission over setPermission(_, false): an unset node still falls back to a
		// Vault/LuckPerms group grant, an explicit deny would override it.
		verify(attachment).unsetPermission("old.node");
		verify(attachment, never()).setPermission(eq("old.node"), eq(false));

		verify(attachment).setPermission("gangland.turf.upgrade", true);
		verify(user.getUser()).updateCommands();
	}

	@Test
	@DisplayName("flush(user, null): clears every granted node and grants nothing, matching a removed/kicked member")
	void flush_nullRank_clearsOnly() {
		JavaPlugin plugin = mock(JavaPlugin.class);

		User<Player>        user       = newOnlineUser(plugin);
		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(Map.of("stale.node", true));
		user.setPermissionAttachment(attachment);

		RankPermissionApplier.flush(user, null);

		verify(attachment).unsetPermission("stale.node");
		verify(attachment, never()).setPermission(eq("stale.node"), eq(false));
	}

	private User<Player> newOnlineUser(JavaPlugin plugin) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		Placeholder placeholder = mock(Placeholder.class);

		return new User<>(plugin, player, placeholder);
	}

}
