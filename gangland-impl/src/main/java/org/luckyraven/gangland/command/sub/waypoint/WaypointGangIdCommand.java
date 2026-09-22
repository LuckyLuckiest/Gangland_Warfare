package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /glw waypoint gangId <id>} — the real validation was always {@code user.getGangId() != id} below (a
 * player can only ever set this to their own gang's id), so no {@code GangManager} existence re-check is
 * needed. The display name (tab-completion + the stored waypoint lookup key) goes through the always-present
 * {@link GangMembership#nameOf(int)} holder (W54 F4 — closes WS6 ask #2's raw-id degrade from the original W51
 * gate) rather than {@code GangManager} directly, falling back to the raw id when no view is installed (module
 * absent) or the id names no real gang.
 */
class WaypointGangIdCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;
	private final GangMembership      gangMembership;

	protected WaypointGangIdCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager, WaypointManager waypointManager,
	                                GangMembership gangMembership) {
		super(gangland, "gangId", tree, parent, "gang_id");

		this.gangland        = gangland;
		this.tree            = tree;
		this.userManager     = userManager;
		this.waypointManager = waypointManager;
		this.gangMembership  = gangMembership;

		waypointGangId();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gangId>"));
		};
	}

	private void waypointGangId() {
		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Waypoint waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				user.sendMessage(Messages.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			OptionalArgument optionalArgument = (OptionalArgument) argument;

			String value = optionalArgument.getActualValue(args[2], sender);

			// verify if it was a number
			int id;
			try {
				id = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// check if the gang is valid — a player's gangId only ever names a real gang (set by Gang.addMember),
			// so this is the real validation; no separate GangManager.getGang(id) existence re-check needed.
			if (user.getGangId() != id) {
				user.sendMessage(Messages.INVALID_GANG_NAME.toString());
				return;
			}

			waypoint.setGangId(id);
			user.sendMessage(Messages.WAYPOINT_CONFIGURATION_SUCCESS.toString());
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				return null;
			}

			int    gangId = user.getGangId();
			String name   = gangMembership.nameOf(gangId).orElse(String.valueOf(gangId));

			return new ArrayList<>(List.of(name));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				return null;
			}

			int gangId = user.getGangId();

			Map<String, String> waypoints = new HashMap<>();

			String name = gangMembership.nameOf(gangId).orElse(String.valueOf(gangId));

			waypoints.put(name, String.valueOf(gangId));

			return waypoints;
		});

		this.addSubArgument(optional);
	}

}
