package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.sub.GanglandDatabase;
import me.luckyraven.database.tables.WaypointTable;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class WaypointDeleteCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;

	protected WaypointDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

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
			Initializer      initializer      = gangland.getInitializer();
			GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
			DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);
			List<Table<?>>   tables           = ganglandDatabase.getTables().stream().toList();

			WaypointTable waypointTable = initializer.getInstanceFromTables(WaypointTable.class, tables);

			helper.runQueries(database -> {
				Map<String, Object> search = waypointTable.searchCriteria(waypoint);
				Database            config = database.table(waypointTable.getName());
				Object[] info = config.select((String) search.get("search"), (Object[]) search.get("info"),
											  (int[]) search.get("type"), new String[]{"*"});

				// if the data was already saved into the database
				if (info.length == 0) return;

				config.delete("id", String.valueOf(waypoint.getUsedId()));

				// refactor the ids
				waypointManager.refactorIds(waypointTable);
			});

			// inform the player
			sender.sendMessage();

			gangland.getInitializer()
					.getPermissionManager()
					.removePermission("gangland.waypoint." + waypoint.getUsedId(), true);

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

			timer.start(true);
			deleteWaypointTimer.put(sender, timer);
		});

		this.addSubArgument(optional);
	}

}
