package me.luckyraven.command.sub.item.unique;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
