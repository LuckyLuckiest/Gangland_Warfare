package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.*;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.WaypointDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class WaypointDeleteCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;

	protected WaypointDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(new String[]{"delete", "remove"}, tree, parent, "delete");

		this.gangland = gangland;
		this.tree = tree;

		this.waypointManager = gangland.getInitializer().getWaypointManager();

		waypointDelete();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void waypointDelete() {
		Map<CommandSender, AtomicReference<Integer>> deleteWaypointId    = new HashMap<>();
		Map<CommandSender, CountdownTimer>           deleteWaypointTimer = new HashMap<>();

		// TODO for all confirm values, they should have a sender and the confirm argument, because you can unlock and
		//  lock for all users who are trying to use the confirm argument, thus make it user specific to take care of
		//  such scenario
		ConfirmArgument confirm = new ConfirmArgument(tree, (argument, sender, args) -> {
			int id = deleteWaypointId.get(sender).get();

			Waypoint waypoint = waypointManager.get(id);

			// check if the waypoint exists
			if (waypoint == null) {
				sender.sendMessage(MessageAddon.INVALID_WAYPOINT.toString());
				return;
			}

			// get the row and delete from the database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof WaypointDatabase waypointDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						Database config = database.table("data");
						Object[] info = config.select("id = ?", new Object[]{id}, new int[]{Types.INTEGER},
						                              new String[]{"*"});

						// if the data was already saved into the database
						if (info.length == 0) return;

						config.delete("id = ?", String.valueOf(waypoint.getUsedId()));

						// refactor the ids
						waypointManager.refactorIds(waypointDatabase);
					});

					break;
				}

			// inform the player
			sender.sendMessage();

			gangland.getInitializer().getPermissionManager().removePermission(
					"gangland.waypoint." + waypoint.getUsedId(), true);

			waypointManager.remove(waypoint);
			deleteWaypointId.remove(sender);

			CountdownTimer timer = deleteWaypointTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				deleteWaypointTimer.remove(sender);
			}
		});

		this.addSubArgument(confirm);

		Argument optional = new OptionalArgument(tree, (argument, sender, args) -> {
			if (confirm.isConfirmed()) return;

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

			AtomicReference<Integer> verifiedId = new AtomicReference<>(id);

			deleteWaypointId.put(sender, verifiedId);

			// notify the player to confirm the waypoint
			sender.sendMessage(ChatUtil.confirmCommand(new String[]{"waypoint", "create"}));
			confirm.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, null, null, time -> {
				confirm.setConfirmed(false);
				deleteWaypointId.remove(sender);
				deleteWaypointTimer.remove(sender);
			});

			timer.start();
			deleteWaypointTimer.put(sender, timer);
		});

		this.addSubArgument(optional);
	}

}
