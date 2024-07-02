package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WaypointSelectCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;

	protected WaypointSelectCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"select", "selects"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.waypointManager = gangland.getInitializer().getWaypointManager();

		waypointSelect();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void waypointSelect() {
		// to select a waypoint, its id should be provided
		Argument select = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			// the id would be the second argument
			String argId = args[2];

			// verify if it was a number
			int id;
			try {
				id = Integer.parseInt(argId);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", argId));
				return;
			}

			Waypoint waypoint = waypointManager.get(id);

			// check if the waypoint exists
			if (waypoint == null) {
				sender.sendMessage(MessageAddon.INVALID_WAYPOINT.toString());
				return;
			}

			// add the waypoint to waypoint manager
			Player player = (Player) sender;
			waypointManager.playerSelect(player, waypoint);

			// inform the player
			player.sendMessage(MessageAddon.WAYPOINT_SELECTED.toString().replace("%waypoint%", waypoint.getName()));
		});

		this.addSubArgument(select);
	}

}
