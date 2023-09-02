package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.*;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

class WaypointCreateCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;

	protected WaypointCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("create", tree, parent);

		this.gangland = gangland;
		this.tree = tree;

		this.waypointManager = gangland.getInitializer().getWaypointManager();

		waypointCreate();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void waypointCreate() {
		// Need to initialize the data and select the waypoint
		Map<Player, AtomicReference<String>> createWaypointName  = new HashMap<>();
		Map<CommandSender, CountdownTimer>   createWaypointTimer = new HashMap<>();

		// create a new waypoint class after confirmation
		ConfirmArgument confirmWaypoint = new ConfirmArgument(tree, (argument, sender, args) -> {
			Player player = (Player) sender;

			String   name     = createWaypointName.get(player).get();
			Waypoint waypoint = new Waypoint(name);
			Location location = player.getLocation();

			waypoint.setCoordinates(player.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
			                        location.getYaw(), location.getPitch());

			// add the waypoint to waypoint manager
			waypointManager.add(waypoint);

			// inform the player that the waypoint is created
			player.sendMessage(MessageAddon.WAYPOINT_CREATED.toString().replace("%waypoint%", name));

			createWaypointName.remove(player);

			CountdownTimer timer = createWaypointTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createWaypointTimer.remove(player);
			}

			// create waypoint permission
			gangland.getInitializer().getPermissionManager().addPermission("waypoint." + waypoint.getUsedId());

			// select the waypoint
			// using '/glw waypoint select <id>' command to the created waypoint, so it is selected
			player.performCommand(
					Argument.getArgumentSequence(Objects.requireNonNull(tree.find(new Argument("select", tree)))) +
							" " + waypoint.getUsedId());
		});

		this.addSubArgument(confirmWaypoint);

		// get the proposed waypoint name
		Argument createName = new OptionalArgument(tree, (argument, sender, args) -> {
			Player player = (Player) sender;

			if (confirmWaypoint.isConfirmed()) return;

			AtomicReference<String> name = new AtomicReference<>(args[2]);

			createWaypointName.put(player, name);

			// notify the player to confirm the waypoint
			player.sendMessage(ChatUtil.confirmCommand(new String[]{"waypoint", "create"}));
			confirmWaypoint.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(MessageAddon.WAYPOINT_CREATE_CONFIRM.toString()
				                                                       .replace("%timer%",
				                                                                TimeUtil.formatTime(time.getPeriod(),
				                                                                                    true)));
			}, null, time -> {
				confirmWaypoint.setConfirmed(false);
				createWaypointName.remove(player);
				createWaypointTimer.remove(sender);
			});

			timer.start(false);
			createWaypointTimer.put(sender, timer);
		});

		this.addSubArgument(createName);
	}

}
