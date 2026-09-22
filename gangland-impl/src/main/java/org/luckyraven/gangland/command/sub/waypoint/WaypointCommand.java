package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
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

	public WaypointCommand(JavaPlugin gangland,
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
		Argument create = new WaypointCreateCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager, permissionManager);
		Argument delete = new WaypointDeleteCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager, ganglandDatabase, permissionManager);

		Argument select = new WaypointSelectCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager);
		Argument deselect = new WaypointDeselectCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                                waypointManager);

		Argument list = new WaypointListCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        waypointManager);
		Argument info = new WaypointInfoCommand(getPlugin(), getArgumentTree(), getArgument(), waypointManager);

		Argument type = new WaypointTypeCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        waypointManager);
		Argument gangId = new WaypointGangIdCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager, gangManager);
		Argument timer = new WaypointTimerCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          waypointManager);
		Argument cooldown = new WaypointCooldownCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                                waypointManager);
		Argument shield = new WaypointShieldCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            waypointManager);
		Argument cost = new WaypointCostCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        waypointManager);
		Argument radius = new WaypointRadiusCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
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
