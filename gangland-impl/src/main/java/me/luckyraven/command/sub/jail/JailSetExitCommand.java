package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.copsncrooks.jail.JailExitService;
import me.luckyraven.copsncrooks.jail.JailRegistry;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final JailRegistry    jailRegistry;
	private final JailExitService jailExitService;

	protected JailSetExitCommand(Gangland gangland, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry,
	                             JailExitService jailExitService) {
		super(gangland, "setexit", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.jailRegistry    = jailRegistry;
		this.jailExitService = jailExitService;

		specificJail();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		// No-arg form: set the global/universal exit.
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			jailExitService.setGlobalExit(player.getLocation());
			sender.sendMessage(Messages.JAIL_EXIT_SET_GLOBAL.toString());
		};
	}

	private void specificJail() {
		Argument jailIdArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			String rawId = args[2];
			int    jailId;
			try {
				jailId = Integer.parseInt(rawId);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.ARGUMENTS_WRONG.toString().replace("%arguments%", "<jailId>"));
				return;
			}

			Jail jail = jailRegistry.getJail(jailId);
			if (jail == null) {
				sender.sendMessage(Messages.JAIL_NOT_JAILED.toString().replace("%target%", rawId));
				return;
			}

			Location location = player.getLocation();
			jailExitService.setExit(jailId, location);

			sender.sendMessage(Messages.JAIL_EXIT_SET.toString().replace("%id%", String.valueOf(jailId)));
		}, sender -> {
			List<String> ids = new ArrayList<>();
			for (Jail jail : jailRegistry.getCells()) ids.add(String.valueOf(jail.getId()));
			return ids;
		});

		this.addSubArgument(jailIdArg);
	}
}
