package org.luckyraven.gangland.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.command.sub.DownloadPluginCommand;
import org.luckyraven.gangland.command.sub.debug.ComponentExecutorCommand;
import org.luckyraven.gangland.command.sub.debug.DebugCommand;
import org.luckyraven.gangland.command.sub.debug.ReadNBTCommand;
import org.luckyraven.gangland.command.sub.debug.TimerCommand;
import org.luckyraven.keystone.command.CommandVisibilityFilter;

import java.util.List;
import java.util.UUID;

/**
 * Hides the dev-only debug commands from listings, suggestions and tab completion for everyone but the dev
 * accounts. Installed on the {@link CommandManager} via Keystone's visibility seam — dispatch is never filtered,
 * so a dev command still executes when typed (execution stays permission-gated).
 */
public final class DevCommandVisibilityFilter implements CommandVisibilityFilter {

	/**
	 * Classes that shouldn't be displayed in tab completion for non-dev users.
	 */
	private static final List<Class<? extends Command>> FILTERED = List.of(DebugCommand.class,
	                                                                       ComponentExecutorCommand.class,
	                                                                       ReadNBTCommand.class,
	                                                                       TimerCommand.class,
	                                                                       DownloadPluginCommand.class);

	// main & second account
	private static final UUID DEV_UUID_1 = UUID.fromString("4b2d5e4d-a089-4660-b777-dd71f3fbbbfa");
	private static final UUID DEV_UUID_2 = UUID.fromString("ad72b2bb-bc30-4c55-a275-106976e70894");

	@Override
	public boolean isVisible(CommandSender sender, org.luckyraven.keystone.command.Command command) {
		if (isDev(sender)) return true;

		return FILTERED.stream().noneMatch(filterClass -> filterClass.isInstance(command));
	}

	private static boolean isDev(CommandSender sender) {
		if (!(sender instanceof Player player)) return false;

		UUID senderUuid = player.getUniqueId();

		return senderUuid.equals(DEV_UUID_1) || senderUuid.equals(DEV_UUID_2);
	}

}
