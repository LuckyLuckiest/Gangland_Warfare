package org.luckyraven.gangland.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.luckyraven.gangland.data.gang.GangMembership;

import java.util.ArrayList;
import java.util.List;

/**
 * A short label ({@code /gw}, {@code /gwc}, {@code /gwa}) that puts fixed words in front of what the player typed and
 * hands the line to {@code /glw}: {@code /gwc hello} runs {@code /glw gang chat hello}, tab completion included.
 *
 * <p>The labels are registered on the server's {@link CommandMap} at runtime and only when the gang module is
 * installed, so a server without it squats no label and {@code plugin.yml} declares none. The fallback prefix makes a
 * label another plugin already owns still reachable as {@code /gangland_warfare:gw}. This class names no gang-module
 * type: it only forwards words.
 */
public final class GangShortLabelExecutor extends org.bukkit.command.Command {

	private final List<String>                prefix;
	private final CommandExecutor             executor;
	private final TabCompleter                completer;
	private final org.bukkit.command.Command target;
	private final String                      targetLabel;

	public GangShortLabelExecutor(String label, List<String> prefix, String permission, CommandExecutor executor,
	                              TabCompleter completer, org.bukkit.command.Command target, String targetLabel) {
		super(label);

		this.prefix      = List.copyOf(prefix);
		this.executor    = executor;
		this.completer   = completer;
		this.target      = target;
		this.targetLabel = targetLabel;

		setPermission(permission);
		setDescription("Shortcut for /" + targetLabel + " " + String.join(" ", prefix));
		setUsage("/" + label);
	}

	@Override
	public boolean execute(CommandSender sender, String commandLabel, String[] args) {
		if (!testPermission(sender)) return true;

		return executor.onCommand(sender, target, targetLabel, forwarded(args));
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
		if (getPermission() != null && !sender.hasPermission(getPermission())) return List.of();

		List<String> result = completer.onTabComplete(sender, target, targetLabel, forwarded(args));
		return result == null ? List.of() : result;
	}

	private String[] forwarded(String[] args) {
		List<String> all = new ArrayList<>(prefix);
		all.addAll(List.of(args));
		return all.toArray(String[]::new);
	}

	/**
	 * Registers {@code /gw}, {@code /gwc} and {@code /gwa} when the gang module has installed itself.
	 *
	 * @return how many labels were registered; {@code 0} when the gang module is absent
	 */
	public static int registerIfInstalled(String fallbackPrefix, CommandMap map, GangMembership membership,
	                                      CommandExecutor executor, TabCompleter completer,
	                                      org.bukkit.command.Command target, String targetLabel) {
		if (map == null || !membership.isInstalled()) return 0;

		List<GangShortLabelExecutor> labels = List.of(
				new GangShortLabelExecutor("gw", List.of("gang"), "gangland.command.gang", executor, completer,
				                           target, targetLabel),
				new GangShortLabelExecutor("gwc", List.of("gang", "chat"), "gangland.command.gang.chat", executor,
				                           completer, target, targetLabel),
				new GangShortLabelExecutor("gwa", List.of("gang", "chat", "allies"),
				                           "gangland.command.gang.chat.allies", executor, completer, target,
				                           targetLabel));
		labels.forEach(label -> map.register(fallbackPrefix, label));
		return labels.size();
	}

	/** The server's command map, read reflectively (it is on {@code CraftServer}, not on the {@code Server} API). */
	public static CommandMap commandMap() {
		try {
			Object server = Bukkit.getServer();
			return (CommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}
	}

}
