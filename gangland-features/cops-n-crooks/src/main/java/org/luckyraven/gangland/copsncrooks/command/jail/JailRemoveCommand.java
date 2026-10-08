package org.luckyraven.gangland.copsncrooks.command.jail;

import java.util.Map;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

class JailRemoveCommand extends SubArgument {

	private final JavaPlugin       plugin;
	private final Tree<Argument> tree;
	private final JailService    jailService;
	private final JailRegistry   jailRegistry;
	private final CommandMessages commandMessages;

	protected JailRemoveCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                            JailService jailService,
	                            JailRegistry jailRegistry, CommandMessages commandMessages) {
		super(plugin, "remove", tree, parent);

		this.plugin     = plugin;
		this.tree         = tree;
		this.jailService  = jailService;
		this.jailRegistry = jailRegistry;
		this.commandMessages = commandMessages;

		idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(commandMessages.usage("/glw jail remove <id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.BAD_ID, Map.of("value", idStr)));
				return;
			}

			jailService.removeJail(id);

			sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_REMOVED, Map.of("id", String.valueOf(id))));
		}, sender -> {
			return jailRegistry.getCells()
					.stream().map(jail -> String.valueOf(jail.getId())).toList();
		});

		this.addSubArgument(idArg);
	}
}
