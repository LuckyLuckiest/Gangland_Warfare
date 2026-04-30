package org.luckyraven.gangland.command.sub.jail;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class JailRemoveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final JailService    jailService;
	private final JailRegistry   jailRegistry;

	protected JailRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            JailService jailService,
	                            JailRegistry jailRegistry) {
		super(gangland, "remove", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.jailService  = jailService;
		this.jailRegistry = jailRegistry;

		idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			jailService.removeJail(id);

			sender.sendMessage(Messages.JAIL_REMOVED.toString().replace("%id%", String.valueOf(id)));
		}, sender -> {
			return jailRegistry.getCells()
					.stream().map(jail -> String.valueOf(jail.getId())).toList();
		});

		this.addSubArgument(idArg);
	}
}
