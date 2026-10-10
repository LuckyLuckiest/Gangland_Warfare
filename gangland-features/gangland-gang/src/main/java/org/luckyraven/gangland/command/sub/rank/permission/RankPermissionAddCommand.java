package org.luckyraven.gangland.command.sub.rank.permission;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.permission.RankPermissionApplier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class RankPermissionAddCommand extends SubArgument {

	private final JavaPlugin          gangland;
	private final Tree<Argument>      tree;
	private final RankManager         rankManager;
	private final PermissionManager   permissionManager;
	private final MemberManager       memberManager;
	private final UserManager<Player> userManager;

	RankPermissionAddCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, RankManager rankManager,
	                         PermissionManager permissionManager, MemberManager memberManager,
	                         UserManager<Player> userManager) {
		super(gangland, "add", tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.rankManager       = rankManager;
		this.permissionManager = permissionManager;
		this.memberManager     = memberManager;
		this.userManager       = userManager;

		rankPermission();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		};
	}

	private void rankPermission() {
		Argument rankArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<permission>"));
		}, sender -> rankManager.getRanks().values()
				.stream().map(Rank::getName).toList());

		Argument permArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			// Saved in lower case: permissionExists and findPermission match case-insensitively, so the stored node must too
			String permString = args[4].toLowerCase(Locale.ROOT);

			// GR-37: only a node the module or the server knows; anything else is refused and never persisted
			if (!rankManager.permissionExists(permString)) {
				sender.sendMessage(Messages.INVALID_RANK_PERMISSION.toString());
				return;
			}

			if (rank.contains(permString)) {
				String string  = Messages.RANK_PERMISSION_EXISTS.toString();
				String replace = string.replace("%rank%", rank.getName()).replace("%permission%", permString);

				sender.sendMessage(replace);
				return;
			}

			rankManager.addPermission(rank, permString);
			memberManager.applyRankPermissionChange(rank, permString, true);
			refreshOnlineMembers(rank);

			String string  = Messages.RANK_PERMISSION_ADD.toString();
			String replace = string.replace("%rank%", rank.getName()).replace("%permission%", permString);

			sender.sendMessage(replace);
		}, sender -> suggestions(permissionManager));

		rankArg.addSubArgument(permArg);
		this.addSubArgument(rankArg);
	}

	/**
	 * Re-applies the rank's nodes onto every ONLINE member of that rank, so a node added now works without a re-login
	 * (the promote path does the same through {@link RankPermissionApplier#flush}).
	 */
	private void refreshOnlineMembers(Rank rank) {
		for (Member member : memberManager.getMembersByRank(rank)) {
			Player player = Bukkit.getPlayer(member.getUuid());
			if (player == null) continue;

			User<Player> user = userManager.getUser(player);
			if (user != null) RankPermissionApplier.flush(user, rank);
		}
	}

	/**
	 * Tab completion: every {@code gangland.gang.*} node, then the permissions the server already knows.
	 */
	static List<String> suggestions(PermissionManager permissionManager) {
		Set<String> nodes = new LinkedHashSet<>(GangPermissions.ALL);
		nodes.addAll(permissionManager.getPermissions());

		return new ArrayList<>(nodes);
	}
}
