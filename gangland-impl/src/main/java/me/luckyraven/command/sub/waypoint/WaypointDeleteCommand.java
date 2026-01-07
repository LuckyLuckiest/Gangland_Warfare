package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.WaypointTable;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class WaypointDeleteCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		Initializer initializer = gangland.getInitializer();

		this.userManager     = initializer.getUserManager();
		this.waypointManager = initializer.getWaypointManager();

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
		ConfirmArgument confirm = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
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
			List<Table<?>>   tables           = ganglandDatabase.getTables();

			WaypointTable waypointTable = initializer.getInstanceFromTables(WaypointTable.class, tables);

			helper.runQueriesAsync(database -> {
				Map<String, Object> search = waypointTable.searchCriteria(waypoint);
				Database            config = database.table(waypointTable.getName());
				Object[] info = config.select((String) search.get("search"), (Object[]) search.get("info"),
											  (int[]) search.get("type"), new String[]{"*"});

				// if the data was already saved into the database
				if (info.length == 0) return;

				config.delete("id", waypoint.getUsedId(), Types.INTEGER);

				// refactor the ids
				waypointManager.refactorIds(waypointTable);
			});

			// inform the player
			sender.sendMessage(ChatUtil.commandMessage("Removed the waypoint!"));

			String format = String.format("%s.waypoint.%d", gangland.getFullPrefix(), waypoint.getUsedId());

			gangland.getInitializer().getPermissionManager().removePermission(format, true);

			waypointManager.remove(waypoint);
			deleteWaypointId.remove(sender);

			CountdownTimer timer = deleteWaypointTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				deleteWaypointTimer.remove(sender);
			}
		});

		this.addSubArgument(confirm);

		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (confirm.isConfirmed()) return;

			OptionalArgument optionalArgument = (OptionalArgument) argument;

			// the id would be the second argument
			String argId = optionalArgument.getActualValue(args[2], sender);

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
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			List<String> waypoints = new ArrayList<>();

			Collection<Waypoint> allWaypoints = waypointManager.getWaypoints().values();
			if (user.hasGang()) {
				int gangId = user.getGangId();

				List<String> list = allWaypoints.stream()
						.filter(waypoint -> waypoint.getGangId() == gangId)
						.map(Waypoint::getName)
						.toList();

				waypoints.addAll(list);
			}

			List<String> list = allWaypoints.stream()
					.filter(waypoint -> player.hasPermission(waypoint.getPermission()))
					.map(Waypoint::getName)
					.toList();

			waypoints.addAll(list);

			return waypoints;
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			List<Waypoint> waypoints = new ArrayList<>();

			Collection<Waypoint> allWaypoints = waypointManager.getWaypoints().values();
			if (user.hasGang()) {
				int gangId = user.getGangId();

				List<Waypoint> list = allWaypoints.stream().filter(waypoint -> waypoint.getGangId() == gangId).toList();

				waypoints.addAll(list);
			}

			List<Waypoint> list = allWaypoints.stream()
					.filter(waypoint -> player.hasPermission(waypoint.getPermission()))
					.toList();

			waypoints.addAll(list);

			// First pass: count how many times each name appears
			Map<String, Integer> nameCount = new HashMap<>();
			for (Waypoint waypoint : waypoints) {
				String name = waypoint.getName();
				nameCount.put(name, nameCount.getOrDefault(name, 0) + 1);
			}

			// Second pass: build the map with name:id for duplicates
			Map<String, String> waypointMap = new HashMap<>();
			for (Waypoint waypoint : waypoints) {
				String name        = waypoint.getName();
				String displayName = nameCount.get(name) > 1 ? name + ":" + waypoint.getUsedId() : name;

				waypointMap.put(displayName, String.valueOf(waypoint.getUsedId()));
			}

			return waypointMap;
		});

		this.addSubArgument(optional);
	}

}
