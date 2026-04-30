package org.luckyraven.gangland.command.sub.item.wearable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gadget.wearable.WearableAddon;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemWearableCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WearableAddon       wearableAddon;

	public ItemWearableCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                           UserManager<Player> userManager,
	                           WearableAddon wearableAddon) {
		super(gangland, "wearable", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.wearableAddon = wearableAddon;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<give/list/info>"));
	}

	private void initializeArguments() {
		Argument give = new ItemWearableGiveCommand(gangland, tree, this, userManager, wearableAddon);
		Argument info = new ItemWearableInfoCommand(gangland, tree, this, userManager, wearableAddon);
		Argument list = new ItemWearableListCommand(gangland, tree, this, wearableAddon);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		this.addAllSubArguments(arguments);
	}

}
