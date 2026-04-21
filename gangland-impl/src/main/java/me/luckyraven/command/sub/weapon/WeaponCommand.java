package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.core.command.CommandHandler;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class WeaponCommand extends Command {

	private final UserManager<Player> userManager;
	private final WeaponManager       weaponManager;
	private final WeaponAddon         weaponAddon;
	private final WeaponLoader        weaponLoader;

	public WeaponCommand(Gangland gangland,
	                     @Qualifier("online") UserManager<Player> userManager,
	                     WeaponManager weaponManager,
	                     WeaponAddon weaponAddon,
	                     WeaponLoader weaponLoader) {
		super(gangland, "weapon", true);

		this.userManager   = userManager;
		this.weaponManager = weaponManager;
		this.weaponAddon   = weaponAddon;
		this.weaponLoader  = weaponLoader;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("weapon"))
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
		Argument give = new WeaponGiveCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                      weaponManager, weaponLoader);
		Argument info = new WeaponInfoCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                      weaponManager, weaponAddon);
		Argument list = new WeaponListCommand(getGangland(), getArgumentTree(), getArgument(), weaponAddon);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Weapon");
	}

}
