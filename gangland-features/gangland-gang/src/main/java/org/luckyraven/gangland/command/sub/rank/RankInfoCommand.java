package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.stream.Collectors;

class RankInfoCommand extends SubArgument {

	private final JavaPlugin       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankInfoCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "info", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.rankManager = rankManager;

		rankInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void rankInfo() {
		Argument infoName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			String permissions = rank.getPermissions()
			                         .stream()
			                         .map(Permission::getPermission)
			                         .collect(Collectors.joining(", "));

			String parents = rank.getNode()
			                     .getChildren()
			                     .stream()
			                     .map(child -> child.getData().getName())
			                     .collect(Collectors.joining(", "));

			String vaultGroup = rank.getVaultGroup();
			if (vaultGroup == null || vaultGroup.isEmpty()) vaultGroup = "None";

			String string = Messages.RANK_INFO_PRIMARY.toString();
			String replace = string.replace("%rank%", rank.getName())
			                       .replace("%id%", String.valueOf(rank.getUsedId()))
			                       .replace("%parent%", parents)
			                       .replace("%vault_group%", vaultGroup);

			sender.sendMessage(replace);

			String string1  = Messages.RANK_INFO_SECONDARY.toString();
			String replace1 = string1.replace("%permissions%", permissions);

			sender.sendMessage(replace1);
		}, sender -> RankCommand.rankNameCompletions(rankManager));

		this.addSubArgument(infoName);
	}
}
