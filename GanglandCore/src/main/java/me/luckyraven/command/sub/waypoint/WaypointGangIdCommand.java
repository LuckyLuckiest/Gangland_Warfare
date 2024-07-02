package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WaypointGangIdCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final GangManager     gangManager;
	private final WaypointManager waypointManager;

	protected WaypointGangIdCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "gangId", tree, parent, "gang_id");

		this.gangland = gangland;
		this.tree     = tree;

		this.gangManager     = gangland.getInitializer().getGangManager();
		this.waypointManager = gangland.getInitializer().getWaypointManager();

		waypointGangId();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<gangId>"));
		};
	}

	private void waypointGangId() {
		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player   player   = (Player) sender;
			Waypoint waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				player.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			String value = args[2].toUpperCase();

			// verify if it was a number
			int id;
			try {
				id = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// check if the gang exists
			Gang gang = gangManager.getGang(id);

			if (gang == null) {
				player.sendMessage(MessageAddon.GANG_DOESNT_EXIST.toString());
				return;
			}

			waypoint.setGangId(id);
			player.sendMessage(MessageAddon.WAYPOINT_CONFIGURATION_SUCCESS.toString());
		});

		this.addSubArgument(optional);
	}

}
