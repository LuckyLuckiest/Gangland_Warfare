package org.luckyraven.gangland.command.sub.level.experience;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class LevelExperienceCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public LevelExperienceCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager) {
		super(gangland, new String[]{"experience", "exp"}, tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		initializeChildren();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		};
	}

	private void initializeChildren() {
		Argument add    = new LevelExperienceAddCommand(gangland, tree, this, userManager);
		Argument remove = new LevelExperienceRemoveCommand(gangland, tree, this, userManager);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(add);
		arguments.add(remove);

		this.addAllSubArguments(arguments);
	}

}
