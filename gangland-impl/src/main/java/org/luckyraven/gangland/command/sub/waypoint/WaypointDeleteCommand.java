package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.TableLookup;
import org.luckyraven.gangland.database.tables.waypoint.WaypointTable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.persistence.database.DatabaseHelper;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.database.query.QueryBuilder;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.*;

import org.luckyraven.keystone.bean.Qualifier;

class WaypointDeleteCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;
	private final GanglandDatabase    ganglandDatabase;
	private final PermissionManager   permissionManager;

	protected WaypointDeleteCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                                @Qualifier("online") UserManager<Player> userManager, WaypointManager waypointManager,
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
		Map<CommandSender, Waypoint>       deleteWaypoint      = new HashMap<>();
		Map<CommandSender, CountdownTimer> deleteWaypointTimer = new HashMap<>();

		ConfirmArgument confirm = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Waypoint waypoint = deleteWaypoint.get(sender);

			// ids are compacted after every confirmed delete, so the pending waypoint is checked by instance, not by id
			if (waypointManager.get(waypoint.getUsedId()) != waypoint) {
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

			String format = String.format("%s.waypoint.%d", GanglandApi.FULL_PREFIX, waypoint.getUsedId());

			permissionManager.removePermission(format, true);

			waypointManager.remove(waypoint);
			deleteWaypoint.remove(sender);

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

			deleteWaypoint.put(sender, waypoint);

			// notify the player to confirm the waypoint
			sender.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"waypoint", "delete"}));

			confirm.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, null, time -> {
					confirm.unlock(s);
					deleteWaypoint.remove(s);
					deleteWaypointTimer.remove(s);
				});

				timer.start(true);
				deleteWaypointTimer.put(s, timer);
			});
		}, sender -> WaypointCompletion.names(userManager, waypointManager, sender),
				sender -> WaypointCompletion.nameToIdMap(userManager, waypointManager, sender));

		this.addSubArgument(optional);
	}

}
