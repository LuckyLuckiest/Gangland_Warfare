package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.data.permission.PermissionManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class WaypointCommand extends Command {

	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;
	private final GangManager         gangManager;
	private final GanglandDatabase    ganglandDatabase;
	private final PermissionManager   permissionManager;

	public WaypointCommand(Gangland gangland,
	                       @Qualifier("online") UserManager<Player> userManager,
	                       WaypointManager waypointManager,
	                       GangManager gangManager,
	                       GanglandDatabase ganglandDatabase,
	                       PermissionManager permissionManager) {
		super(gangland, "waypoint", true);

		this.userManager       = userManager;
		this.waypointManager   = waypointManager;
		this.gangManager       = gangManager;
		this.ganglandDatabase  = ganglandDatabase;
		this.permissionManager = permissionManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("waypoint"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		if (commandSender instanceof Player player) {
			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			Waypoint selected = waypointManager.getSelected(player);

			if (selected != null) {
				user.sendMessage(GanglandChatUtil.color("&6Selected waypoint: &7" + selected.getName()));
				return;
			}
		}

		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument create = new WaypointCreateCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager, permissionManager);
		Argument delete = new WaypointDeleteCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager, ganglandDatabase, permissionManager);

		Argument select = new WaypointSelectCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager);
		Argument deselect = new WaypointDeselectCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                                waypointManager);

		Argument list = new WaypointListCommand(getGangland(), getArgumentTree(), getArgument(), waypointManager);
		Argument info = new WaypointInfoCommand(getGangland(), getArgumentTree(), getArgument(), waypointManager);

		Argument type = new WaypointTypeCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                        waypointManager);
		Argument gangId = new WaypointGangIdCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager, gangManager);
		Argument timer = new WaypointTimerCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                          waypointManager);
		Argument cooldown = new WaypointCooldownCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                                waypointManager);
		Argument shield = new WaypointShieldCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager);
		Argument cost = new WaypointCostCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                        waypointManager);
		Argument radius = new WaypointRadiusCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);

		arguments.add(select);
		arguments.add(deselect);

		arguments.add(list);
		arguments.add(info);

		arguments.add(type);
		arguments.add(gangId);
		arguments.add(timer);
		arguments.add(cooldown);
		arguments.add(shield);
		arguments.add(cost);
		arguments.add(radius);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Waypoint");
	}

}
