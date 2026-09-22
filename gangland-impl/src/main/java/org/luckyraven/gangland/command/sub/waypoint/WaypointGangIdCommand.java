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
 * {@code /glw waypoint gangId <id>} — {@code user.getGangId() != id} is the primary validation (a player can
 * only ever set this to their own gang's id), but the original {@code gang == null -> GANG_DOESNT_EXIST} guard
 * is restored on top of it (W54 second re-review) for the rare desync window where a player's cached
 * {@code User.gangId} still names a gang that was deleted (or otherwise stopped existing) in the same tick —
 * {@link #gangUnknown(GangMembership, int)} is the decision, only ever firing when the module is actually
 * installed (an uninstalled module can't tell "no gang" from "don't know," so the raw-id path is trusted
 * instead there). The display name (tab-completion + the stored waypoint lookup key) goes through the
 * always-present {@link GangMembership#nameOf(int)} holder (W54 F4 — closes WS6 ask #2's raw-id degrade from
 * the original W51 gate) rather than {@code GangManager} directly, falling back to the raw id when no view is
 * installed (module absent) or the id names no real gang.
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
			// so this is the primary validation.
			if (user.getGangId() != id) {
				user.sendMessage(Messages.INVALID_GANG_NAME.toString());
				return;
			}

			// Defensive re-check for the rare desync window (the gang was deleted after User.gangId was set but
			// before it was reset) — see the class javadoc and gangUnknown's own javadoc.
			if (gangUnknown(gangMembership, id)) {
				user.sendMessage(Messages.GANG_DOESNT_EXIST.toString());
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

	/**
	 * @return whether {@code id} should be rejected as unknown. Only ever true when the gang module is actually
	 * installed ({@link GangMembership#isInstalled()}) <em>and</em> it confirms {@code id} names no real gang
	 * ({@link GangMembership#nameOf(int)} empty) — {@code isInstalled()} is the piece that distinguishes "the
	 * module answered no" from "there's no module to ask," since {@code nameOf} alone returns the same absent
	 * result either way. When no module is installed this always returns {@code false}, trusting the raw-id
	 * path (the same behaviour a server without the gang module gets everywhere else in this command).
	 */
	static boolean gangUnknown(GangMembership gangMembership, int id) {
		return gangMembership.isInstalled() && gangMembership.nameOf(id).isEmpty();
	}

}
