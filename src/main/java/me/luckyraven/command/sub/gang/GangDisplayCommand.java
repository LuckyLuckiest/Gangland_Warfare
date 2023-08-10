package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class GangDisplayCommand extends SubArgument {

	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	protected GangDisplayCommand(Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager, GangManager gangManager) {
		super("display", tree, parent);

		this.tree = tree;

		this.userManager = userManager;
		this.gangManager = gangManager;

		gangDisplay();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void gangDisplay() {
		Argument displayName = new OptionalArgument(tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			String displayNameStr = args[2];
			Gang   gang           = gangManager.getGang(user.getGangId());

			gang.setDisplayName(displayNameStr);
			player.sendMessage(MessageAddon.GANG_DISPLAY_SET.toString().replace("%display%", displayNameStr));
		});

		// glw gang display remove
		Argument removeDisplay = new Argument("remove", tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			gang.setDisplayName("");
			player.sendMessage(MessageAddon.GANG_DISPLAY_REMOVED.toString());
		});

		this.addSubArgument(removeDisplay);
		this.addSubArgument(displayName);
	}

}
