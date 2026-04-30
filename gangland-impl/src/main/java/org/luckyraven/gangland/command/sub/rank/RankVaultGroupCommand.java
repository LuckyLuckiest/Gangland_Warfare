package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw rank vaultgroup <rank> <group|clear>} — associates a rank with an external Vault group. When a member is
 * assigned the rank, {@link VaultPermissionBridge} adds them to the configured group; demotion removes them. Passing
 * the literal {@code clear} detaches the group. Non-destructive when Vault is absent.
 */
class RankVaultGroupCommand extends SubArgument {

	private static final String CLEAR_TOKEN = "clear";

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;
	private final MemberManager  memberManager;

	protected RankVaultGroupCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager,
	                                MemberManager memberManager) {
		super(gangland, new String[]{"vaultgroup", "vgroup"}, tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.rankManager   = rankManager;
		this.memberManager = memberManager;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		};
	}

	private void initializeArguments() {
		Argument rankArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			String linked = rank.getVaultGroup();
			if (linked == null || linked.isEmpty()) {
				sender.sendMessage(Messages.RANK_VAULT_GROUP_NONE.toString().replace("%rank%", rank.getName()));
				return;
			}

			sender.sendMessage(Messages.RANK_VAULT_GROUP_CURRENT.toString()
			                                                    .replace("%rank%", rank.getName())
			                                                    .replace("%group%", linked));
		}, sender -> rankManager.getRanks().values()
				.stream().map(Rank::getName).toList());

		Argument groupArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			if (!VaultPermissionBridge.isEnabled()) {
				sender.sendMessage(Messages.VAULT_PERMISSION_NOT_AVAILABLE.toString());
				return;
			}

			String       token    = args[3];
			boolean      clearing = token.equalsIgnoreCase(CLEAR_TOKEN);
			String       newGroup = clearing ? null : token;
			String       oldGroup = rank.getVaultGroup();
			List<Member> wearing  = memberManager.getMembersByRank(rank);

			if (!clearing && VaultPermissionBridge.getGroups()
					.stream().noneMatch(token::equalsIgnoreCase)) {
				sender.sendMessage(Messages.RANK_VAULT_GROUP_INVALID.toString().replace("%group%", token));
				return;
			}

			rank.setVaultGroup(newGroup);

			for (Member member : wearing) {
				OfflinePlayer player = Bukkit.getOfflinePlayer(member.getUuid());
				VaultPermissionBridge.removeFromGroup(player, oldGroup);
				VaultPermissionBridge.addToGroup(player, newGroup);

				if (!clearing) {
					for (Permission perm : rank.getPermissions()) {
						VaultPermissionBridge.grant(player, perm.getPermission());
					}
				}
			}

			String template = clearing ?
			                  Messages.RANK_VAULT_GROUP_CLEARED.toString() :
			                  Messages.RANK_VAULT_GROUP_SET.toString();
			sender.sendMessage(
					template.replace("%rank%", rank.getName()).replace("%group%", newGroup == null ? "" : newGroup));
		}, sender -> {
			List<String> suggestions = new ArrayList<>();

			suggestions.add(CLEAR_TOKEN);
			suggestions.addAll(VaultPermissionBridge.getGroups());

			return suggestions;
		});

		rankArg.addSubArgument(groupArg);
		this.addSubArgument(rankArg);
	}
}
