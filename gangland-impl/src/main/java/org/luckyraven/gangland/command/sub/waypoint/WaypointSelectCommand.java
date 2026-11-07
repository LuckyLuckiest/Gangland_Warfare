package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointAccess;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import org.luckyraven.keystone.bean.Qualifier;
import java.util.*;

class WaypointSelectCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointSelectCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                                @Qualifier("online") UserManager<Player> userManager, WaypointManager waypointManager) {
		super(gangland, new String[]{"select", "selects"}, tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.userManager     = userManager;
		this.waypointManager = waypointManager;

		waypointSelect();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void waypointSelect() {
		// to select a waypoint, its id should be provided
		Argument select = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			// the id would be the second argument
			String argId = optionalArgument.getActualValue(args[2], sender);

			select(userManager, waypointManager, (Player) sender, argId);
		}, sender -> WaypointCompletion.names(userManager, waypointManager, sender),
				sender -> WaypointCompletion.nameToIdMap(userManager, waypointManager, sender));

		this.addSubArgument(select);
	}

	static void select(UserManager<Player> userManager, WaypointManager waypointManager, Player player, String argId) {
		// T-828: a name (what the completer offers) or a numeric id
		Waypoint waypoint = waypointManager.get(argId);

		if (waypoint == null) {
			try {
				waypoint = waypointManager.get(Integer.parseInt(argId));
			} catch (NumberFormatException ignored) {
				// not an id either
			}
		}

		// check if the waypoint exists
		if (waypoint == null) {
			player.sendMessage(Messages.INVALID_WAYPOINT.toString());
			return;
		}

		// add the waypoint to waypoint manager
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		// LS-01: selecting by id skipped the permission/gang filter the completer applies, and a selected
		// waypoint is what a bare /glw teleport uses.
		if (!WaypointAccess.canAccess(user, waypoint)) {
			user.sendMessage(Messages.WARP_NO_PERM.toString());
			return;
		}

		waypointManager.playerSelect(player, waypoint);

		// inform the player
		user.sendMessage(Messages.WAYPOINT_SELECTED.toString().replace("%waypoint%", waypoint.getName()));
	}

}
