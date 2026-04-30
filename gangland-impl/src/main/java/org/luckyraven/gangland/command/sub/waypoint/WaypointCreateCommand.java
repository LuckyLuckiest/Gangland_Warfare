package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.ArgumentUtil;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.ConfirmArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.core.timer.CountdownTimer;
import org.luckyraven.gangland.core.utilities.TimeUtil;
import org.luckyraven.gangland.data.permission.PermissionManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.util.TimeMessages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

class WaypointCreateCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;
	private final PermissionManager   permissionManager;

	protected WaypointCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager, WaypointManager waypointManager,
	                                PermissionManager permissionManager) {
		super(gangland, "create", tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.userManager       = userManager;
		this.waypointManager   = waypointManager;
		this.permissionManager = permissionManager;

		waypointCreate();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void waypointCreate() {
		// Need to initialize the data and select the waypoint
		Map<Player, AtomicReference<String>> createWaypointName  = new HashMap<>();
		Map<CommandSender, CountdownTimer>   createWaypointTimer = new HashMap<>();

		// create a new waypoint class after confirmation
		ConfirmArgument confirmWaypoint = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String   name     = createWaypointName.get(player).get();
			Waypoint waypoint = new Waypoint(name, Gangland.FULL_PREFIX);
			Location location = player.getLocation();

			waypoint.setCoordinates(player.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
			                        location.getYaw(), location.getPitch());

			// add the waypoint to waypoint manager
			waypointManager.add(waypoint);

			// inform the player that the waypoint is created
			user.sendMessage(Messages.WAYPOINT_CREATED.toString().replace("%waypoint%", name));

			createWaypointName.remove(player);

			CountdownTimer timer = createWaypointTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createWaypointTimer.remove(player);
			}

			// create waypoint permission
			permissionManager.addPermission(waypoint.getPermission());

			// select the waypoint
			// using '/glw waypoint select <id>' command to the created waypoint, so it is selected
			var selectedArgument = Objects.requireNonNull(tree.find(new Argument(gangland, "select", tree)));
			var select = ArgumentUtil.getArgumentSequence(selectedArgument, Gangland.SHORT_PREFIX) + " " +
			             waypoint.getUsedId();

			player.performCommand(select);
		});

		this.addSubArgument(confirmWaypoint);

		// get the proposed waypoint name
		Argument createName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (confirmWaypoint.isLocked(sender)) return;

			AtomicReference<String> name = new AtomicReference<>(args[2]);

			createWaypointName.put(player, name);

			// notify the player to confirm the waypoint
			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"waypoint", "create"}));

			confirmWaypoint.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String createConfirm = Messages.WAYPOINT_CREATE_CONFIRM.toString();
					String confirm = createConfirm.replace("%timer%", TimeUtil.formatTime(time.getTimeLeft(), true,
					                                                                      TimeMessages.getInstance()));

					s.sendMessage(confirm);
				}, time -> {
					confirmWaypoint.unlock(s);
					createWaypointName.remove(player);
					createWaypointTimer.remove(s);
				});

				timer.start(false);
				createWaypointTimer.put(s, timer);
			});
		}, sender -> List.of("<name>"));

		this.addSubArgument(createName);
	}

}
