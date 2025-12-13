package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class WaypointCostCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointCostCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "cost", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager     = gangland.getInitializer().getUserManager();
		this.waypointManager = gangland.getInitializer().getWaypointManager();

		waypointCost();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private void waypointCost() {
		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player   = (Player) sender;
			User<Player> user     = userManager.getUser(player);
			Waypoint     waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				user.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			String value = args[2].toUpperCase();

			// verify if it was a number
			double changedValue;
			try {
				changedValue = Double.parseDouble(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// update the timer
			waypoint.setCost(changedValue);
			user.sendMessage(MessageAddon.WAYPOINT_CONFIGURATION_SUCCESS.toString());
		}, sender -> List.of("<cost>"));

		this.addSubArgument(optional);
	}

}
