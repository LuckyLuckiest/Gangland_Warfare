package me.luckyraven.command.sub.level.experience;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
