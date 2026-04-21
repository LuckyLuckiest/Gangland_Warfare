package me.luckyraven.command.sub.item.wearable;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
