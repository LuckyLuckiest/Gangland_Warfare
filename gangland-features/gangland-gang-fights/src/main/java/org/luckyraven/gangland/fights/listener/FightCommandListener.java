package org.luckyraven.gangland.fights.listener;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.gangland.fights.fight.FightState;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.List;
import java.util.Locale;

/**
 * Limits what a fighter may type while his fight is on (GP-31). HIGHEST, so it sees the line after other plugins
 * rewrote it. The line is normalised before it is compared: lower-cased, the leading slash and any {@code plugin:}
 * namespace removed ({@code /minecraft:tp} is {@code tp}) and the command name resolved through the server's command
 * map, so an alias of a blocked command is blocked and an alias of an allowed one is allowed. Console and command
 * blocks are not players and never get here.
 */
@ListenerHandler
public class FightCommandListener implements Listener {

	public static final String BYPASS_NODE = "gangland.gang.fight.bypass_commands";

	private final FightManager  manager;
	private final FightRules    rules;
	private final FightMessages messages;

	public FightCommandListener(FightManager manager, FightRules rules, FightMessages messages) {
		this.manager  = manager;
		this.rules    = rules;
		this.messages = messages;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		Player player = event.getPlayer();
		Fight  fight  = manager.fightOf(player.getUniqueId()).orElse(null);
		if (fight == null || fight.eliminated(player.getUniqueId()) || fight.state() == FightState.PROPOSED) return;
		if (player.hasPermission(BYPASS_NODE)) return;

		String mode = rules.getCommandBlockMode().toUpperCase(Locale.ROOT);
		if (mode.equals("OFF")) return;

		String line = normalise(event.getMessage());
		if (line.isEmpty()) return;

		boolean blocked = mode.equals("BLOCKLIST") ? matches(line, rules.getBlockedCommands())
		                                           : !matches(line, rules.getAllowedCommands());
		if (!blocked) return;

		event.setCancelled(true);
		Fighters.tell(player, messages, "Guards.Command_Blocked");
	}

	/** True when the line equals one of the entries or continues one after a space; the entries are normalised too. */
	private boolean matches(String line, List<String> entries) {
		for (String entry : entries) {
			String listed = normalise(entry);
			if (listed.isEmpty()) continue;
			if (line.equals(listed) || line.startsWith(listed + " ")) return true;
		}
		return false;
	}

	/** {@code /Minecraft:TP  x} becomes {@code tp x}; the first word is replaced by the command's own name. */
	private String normalise(String raw) {
		String text = raw.trim().toLowerCase(Locale.ROOT);
		if (text.startsWith("/")) text = text.substring(1).trim();
		if (text.isEmpty()) return "";

		int    space = text.indexOf(' ');
		String first = space < 0 ? text : text.substring(0, space);
		String rest  = space < 0 ? "" : text.substring(space).replaceAll("\\s+", " ");

		int colon = first.indexOf(':');
		if (colon >= 0) first = first.substring(colon + 1);
		return (canonical(first) + rest).trim();
	}

	/** The command's own name for a label or alias; an unknown label stays as typed. */
	String canonical(String label) {
		CommandMap map = commandMap();
		if (map == null) return label;

		Command command = map.getCommand(label);
		return command == null ? label : command.getName().toLowerCase(Locale.ROOT);
	}

	/** The server's command map, read reflectively (it is on {@code CraftServer}, not on the Spigot 1.16 API). */
	private static CommandMap commandMap() {
		try {
			Object server = Bukkit.getServer();
			return (CommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}
	}
}
