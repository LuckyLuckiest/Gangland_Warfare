package org.luckyraven.gangland.command.sub.weapon;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class AmmunitionCommand extends Command {

	private final UserManager<Player> userManager;
	private final AmmunitionManager   ammunitionManager;

	public AmmunitionCommand(Gangland gangland,
	                         @Qualifier("online") UserManager<Player> userManager,
	                         AmmunitionManager ammunitionManager) {
		super(gangland, "ammo", true, "ammunition");

		this.userManager       = userManager;
		this.ammunitionManager = ammunitionManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("ammunition"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument give = new AmmunitionGiveCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                          ammunitionManager);
		Argument info = new AmmunitionInfoCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                          ammunitionManager);
		Argument list = new AmmunitionListCommand(getGangland(), getArgumentTree(), getArgument(), ammunitionManager);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Ammunition");
	}

}
