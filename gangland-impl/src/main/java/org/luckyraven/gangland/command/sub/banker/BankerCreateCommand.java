package org.luckyraven.gangland.command.sub.banker;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerData;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;
import java.util.UUID;

class BankerCreateCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final BankerManager  bankerManager;

	protected BankerCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              BankerManager bankerManager) {
		super(gangland, "create", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.bankerManager = bankerManager;

		registerArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> spawnBanker(sender, null);
	}

	private void registerArguments() {
		Argument displayArg = new OptionalArgument(gangland, tree,
		                                           (argument, sender, args) -> spawnBanker(sender, args[2]),
		                                           sender -> List.of("<displayName>"));
		this.addSubArgument(displayArg);
	}

	private void spawnBanker(CommandSender sender, String display) {
		if (!(sender instanceof Player player)) return;

		Location   loc  = player.getLocation();
		BankerData data = new BankerData(UUID.randomUUID(), loc, display);
		bankerManager.create(data);

		player.sendMessage(GanglandChatUtil.commandMessage(
				"&aSpawned banker (&f" + (display != null ? display : "unnamed") + "&a)."));
	}

}
