package org.luckyraven.gangland.copsncrooks.command.jail;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class JailTeleportCommand extends SubArgument {

	private final JavaPlugin       plugin;
	private final Tree<Argument> tree;
	private final JailRegistry   jailRegistry;

	JailTeleportCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry) {
		super(plugin, new String[]{"teleport", "tp"}, tree, parent);

		this.plugin     = plugin;
		this.tree         = tree;
		this.jailRegistry = jailRegistry;

		this.idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			Location location = jailRegistry.getJailLocation(id);

			if (location == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			player.teleport(location);
			sender.sendMessage(Messages.JAIL_TELEPORTED.toString().replace("%id%", String.valueOf(id)));
		}, sender -> {
			return jailRegistry.getCells()
					.stream().map(jail -> String.valueOf(jail.getId())).toList();
		});

		this.addSubArgument(idArg);
	}
}
