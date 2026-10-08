package org.luckyraven.gangland.copsncrooks.command.jail;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailExitService;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sets a jail exit location:
 * <ul>
 *   <li>{@code /glw jail setexit} — sets the global (universal) exit used by every jail that has no specific exit.</li>
 *   <li>{@code /glw jail setexit <jailId>} — sets the exit for that specific jail, overriding the global.</li>
 * </ul>
 * Players released from a jail resolve their teleport in this order: per-jail exit → global exit → configured
 * fallback waypoint → any waypoint → release on the spot.
 */
class JailSetExitCommand extends SubArgument {

	private final JavaPlugin        plugin;
	private final Tree<Argument>  tree;
	private final JailRegistry    jailRegistry;
	private final JailExitService jailExitService;
	private final CommandMessages commandMessages;

	protected JailSetExitCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry,
	                             JailExitService jailExitService, CommandMessages commandMessages) {
		super(plugin, "setexit", tree, parent);

		this.plugin          = plugin;
		this.tree            = tree;
		this.jailRegistry    = jailRegistry;
		this.jailExitService = jailExitService;
		this.commandMessages = commandMessages;

		specificJail();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		// No-arg form: set the global/universal exit.
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.NOT_PLAYER, Map.of()));
				return;
			}

			jailExitService.setGlobalExit(player.getLocation());
			sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_EXIT_SET_GLOBAL, Map.of()));
		};
	}

	private void specificJail() {
		Argument jailIdArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.NOT_PLAYER, Map.of()));
				return;
			}

			String rawId = args[2];
			int    jailId;
			try {
				jailId = Integer.parseInt(rawId);
			} catch (NumberFormatException e) {
				// a non-numeric id is a bad operand, not an unknown jail
				sender.sendMessage(commandMessages.format(CommandMessages.Key.BAD_ID, Map.of("value", rawId)));
				return;
			}

			Jail jail = jailRegistry.getJail(jailId);
			if (jail == null) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_UNKNOWN, Map.of("id", rawId)));
				return;
			}

			Location location = player.getLocation();
			jailExitService.setExit(jailId, location);

			sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_EXIT_SET,
                                                      Map.of("id", String.valueOf(jailId))));
		}, sender -> {
			List<String> ids = new ArrayList<>();
			for (Jail jail : jailRegistry.getCells()) ids.add(String.valueOf(jail.getId()));
			return ids;
		});

		this.addSubArgument(jailIdArg);
	}
}
