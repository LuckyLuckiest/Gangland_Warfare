package org.luckyraven.gangland.command.sub.item.unique;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemUniqueCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final UniqueItemAddon     uniqueItemAddon;

	public ItemUniqueCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                         UserManager<Player> userManager,
	                         UniqueItemAddon uniqueItemAddon) {
		super(gangland, "unique", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.userManager     = userManager;
		this.uniqueItemAddon = uniqueItemAddon;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<give/list/info>"));
	}

	private void initializeArguments() {
		Argument give = new ItemUniqueGiveCommand(gangland, tree, this, userManager, uniqueItemAddon);
		Argument info = new ItemUniqueInfoCommand(gangland, tree, this, userManager, uniqueItemAddon);
		Argument list = new ItemUniqueListCommand(gangland, tree, this, uniqueItemAddon);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		this.addAllSubArguments(arguments);
	}

}
