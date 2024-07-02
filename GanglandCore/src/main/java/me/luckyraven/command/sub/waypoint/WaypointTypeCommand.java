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

class WaypointTypeCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;

	protected WaypointTypeCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "type", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.waypointManager = gangland.getInitializer().getWaypointManager();

		waypointType();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<value>"));
		};
	}

	private void waypointType() {
		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player   player   = (Player) sender;
			Waypoint waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				player.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			String value = args[2].toUpperCase();

			// check if the type exists
			Waypoint.WaypointType type;
			try {
				type = Waypoint.WaypointType.valueOf(value);
				player.sendMessage(MessageAddon.WAYPOINT_CONFIGURATION_SUCCESS.toString());
			} catch (IllegalArgumentException exception) {
				StringBuilder           builder       = new StringBuilder();
				Waypoint.WaypointType[] waypointTypes = Waypoint.WaypointType.values();

				for (int i = 0; i < waypointTypes.length; i++) {
					builder.append(waypointTypes[i].getName());

					if (i < waypointTypes.length - 1) builder.append(", ");
				}

				player.sendMessage(ChatUtil.errorMessage("Invalid Waypoint Type. Select from the list:"),
								   ChatUtil.color("&7" + builder));

				type = waypoint.getType();
			}

			waypoint.setType(type);
		});

		this.addSubArgument(optional);
	}

}
