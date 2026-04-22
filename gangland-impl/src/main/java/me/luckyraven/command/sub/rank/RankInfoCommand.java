package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.rank.Permission;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

class RankInfoCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
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

			StringBuilder    permBuilder = new StringBuilder();
			List<Permission> permissions = rank.getPermissions();
			for (int i = 0; i < permissions.size(); i++) {
				permBuilder.append(permissions.get(i));
				if (i < permissions.size() - 1) permBuilder.append(", ");
			}

			StringBuilder parentBuilder = new StringBuilder();
			for (int i = 0; i < rank.getNode().getChildren().size(); i++) {
				parentBuilder.append(rank.getNode().getChildren().get(i).getData().getName());
				if (i < rank.getNode().getChildren().size() - 1) parentBuilder.append(", ");
			}

			String vaultGroup = rank.getVaultGroup();
			if (vaultGroup == null || vaultGroup.isEmpty()) vaultGroup = "None";

			String string = Messages.RANK_INFO_PRIMARY.toString();
			String replace = string.replace("%rank%", rank.getName())
			                       .replace("%id%", String.valueOf(rank.getUsedId()))
			                       .replace("%parent%", parentBuilder.toString())
			                       .replace("%vault_group%", vaultGroup);

			sender.sendMessage(replace);

			String string1  = Messages.RANK_INFO_SECONDARY.toString();
			String replace1 = string1.replace("%permissions%", permBuilder.toString());

			sender.sendMessage(replace1);
		}, sender -> {
			Collection<Rank> values = rankManager.getRanks().values();

			if (values.isEmpty()) return List.of("<name>");

			return values.stream().map(Rank::getName).toList();
		});

		this.addSubArgument(infoName);
	}
}
