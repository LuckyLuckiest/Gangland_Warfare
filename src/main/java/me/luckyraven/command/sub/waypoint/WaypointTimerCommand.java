package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WaypointTimerCommand extends SubArgument {

	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;

	protected WaypointTimerCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(new String[]{"timer"}, tree, "timer", parent);

		this.tree = tree;

		this.waypointManager = gangland.getInitializer().getWaypointManager();

		waypointTimer();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private void waypointTimer() {
		Argument optional = new OptionalArgument(tree, (argument, sender, args) -> {
			Player   player   = (Player) sender;
			Waypoint waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				player.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			String value = args[2].toUpperCase();

			// verify if it was a number
			int changedValue;
			try {
				changedValue = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// update the timer
			waypoint.setTimer(changedValue);
			player.sendMessage(MessageAddon.WAYPOINT_CONFIGURATION_SUCCESS.toString());
		});

		this.addSubArgument(optional);
	}

}
