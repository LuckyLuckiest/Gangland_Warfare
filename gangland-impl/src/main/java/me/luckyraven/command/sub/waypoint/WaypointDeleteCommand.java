package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.timer.CountdownTimer;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.TableLookup;
import me.luckyraven.database.tables.waypoint.WaypointTable;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.database.query.QueryBuilder;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class WaypointDeleteCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;
	private final GanglandDatabase    ganglandDatabase;
	private final PermissionManager   permissionManager;

	protected WaypointDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager, WaypointManager waypointManager,
	                                GanglandDatabase ganglandDatabase, PermissionManager permissionManager) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.userManager       = userManager;
		this.waypointManager   = waypointManager;
		this.ganglandDatabase  = ganglandDatabase;
		this.permissionManager = permissionManager;

		waypointDelete();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void waypointDelete() {
		Map<CommandSender, AtomicReference<Integer>> deleteWaypointId    = new HashMap<>();
		Map<CommandSender, CountdownTimer>           deleteWaypointTimer = new HashMap<>();

		ConfirmArgument confirm = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			int id = deleteWaypointId.get(sender).get();

			Waypoint waypoint = waypointManager.get(id);

			// check if the waypoint exists
			if (waypoint == null) {
				sender.sendMessage(Messages.INVALID_WAYPOINT.toString());
				return;
			}

			// delete from the database and refactor the remaining IDs in one async task
			DatabaseHelper helper = new DatabaseHelper(gangland, ganglandDatabase);
			List<Table<?>> tables = ganglandDatabase.getTables();

			WaypointTable waypointTable = TableLookup.find(WaypointTable.class, tables);

			helper.runQueriesAsync(database -> {
				QueryBuilder.on(database, waypointTable.getName()).delete().where("id", waypoint.getUsedId()).execute();

				// refactor the ids of the remaining waypoints
				waypointManager.refactorIds();
			});

			// inform the player
			sender.sendMessage(Messages.WAYPOINT_DELETED.toString());

			String format = String.format("%s.waypoint.%d", Gangland.FULL_PREFIX, waypoint.getUsedId());

			permissionManager.removePermission(format, true);

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
			if (confirm.isLocked(sender)) return;

			OptionalArgument optionalArgument = (OptionalArgument) argument;

			// the id would be the second argument
			String argId = optionalArgument.getActualValue(args[2], sender);

			// verify if it was a number
			int id;
			try {
				id = Integer.parseInt(argId);
			} catch (NumberFormatException exception) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", argId));
				return;
			}

			Waypoint waypoint = waypointManager.get(id);

			// check if the waypoint exists
			if (waypoint == null) {
				sender.sendMessage(Messages.INVALID_WAYPOINT.toString());
				return;
			}

			AtomicReference<Integer> verifiedId = new AtomicReference<>(id);

			deleteWaypointId.put(sender, verifiedId);

			// notify the player to confirm the waypoint
			sender.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"waypoint", "delete"}));

			confirm.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, null, time -> {
					confirm.unlock(s);
					deleteWaypointId.remove(s);
					deleteWaypointTimer.remove(s);
				});

				timer.start(true);
				deleteWaypointTimer.put(s, timer);
			});
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

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

			if (user == null) return null;

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
