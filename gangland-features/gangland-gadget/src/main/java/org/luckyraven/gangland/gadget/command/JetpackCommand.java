package org.luckyraven.gangland.gadget.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class JetpackCommand extends Command {

	private final JetpackAddon    jetpackAddon;
	private final JetpackMessages jetpackMessages;

	public JetpackCommand(JavaPlugin plugin, JetpackAddon jetpackAddon, JetpackMessages jetpackMessages) {
		super(plugin, "jetpack", true, "jetpacks");

		this.jetpackAddon    = jetpackAddon;
		this.jetpackMessages = jetpackMessages;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("jetpack"))
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
		Argument give = new JetpackGiveCommand(getPlugin(), getArgumentTree(), getArgument(), jetpackAddon,
		                                       jetpackMessages);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(give);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Jetpack");
	}

}
