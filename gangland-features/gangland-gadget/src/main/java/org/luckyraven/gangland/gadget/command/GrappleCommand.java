package org.luckyraven.gangland.gadget.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /glw grapple} — parent for {@link GrappleGiveCommand}, mirrors {@link JetpackCommand} exactly (WS8-D3:
 * per-type give command, same as car/jetpack).
 */
@CommandHandler
public final class GrappleCommand extends Command {

	private final GrappleAddon    grappleAddon;
	private final GrappleMessages grappleMessages;

	public GrappleCommand(JavaPlugin plugin, GrappleAddon grappleAddon, GrappleMessages grappleMessages) {
		super(plugin, "grapple", true, "grapples");

		this.grappleAddon    = grappleAddon;
		this.grappleMessages = grappleMessages;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("grapple"))
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
		Argument give = new GrappleGiveCommand(getPlugin(), getArgumentTree(), getArgument(), grappleAddon,
		                                       grappleMessages);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(give);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Grapple");
	}

}
