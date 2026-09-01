package org.luckyraven.gangland.command;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.sub.DownloadPluginCommand;
import org.luckyraven.gangland.command.sub.debug.ComponentExecutorCommand;
import org.luckyraven.gangland.command.sub.debug.DebugCommand;
import org.luckyraven.gangland.command.sub.debug.ReadNBTCommand;
import org.luckyraven.gangland.command.sub.debug.TimerCommand;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.SettingsLookupImpl;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.util.UnhandledError;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gangland's dispatcher — a subclass of Keystone's {@link org.luckyraven.keystone.command.CommandManager} (1.7.2
 * migration). Keystone owns scanning, two-phase instantiation via the {@link DependencyContainer}, registration and
 * per-subcommand help paging; this class keeps the consumer-side behavior:
 *
 * <ul>
 *     <li>{@link Messages}-localized error lines and the {@link GanglandChatUtil} "did you mean" styling;</li>
 *     <li>the dev-only tab/suggestion filter for debug commands ({@link #getFilters()});</li>
 *     <li>the branded {@code /glw} splash in {@link #show}.</li>
 * </ul>
 *
 * <p>Condition evaluation ({@code @CommandHandler(condition = ...)}) runs through {@link SettingsLookupImpl},
 * which resolves dotted settings keys against the loaded settings map.
 */
@CustomLog
public final class CommandManager extends org.luckyraven.keystone.command.CommandManager {

	// classes that shouldn't be displayed in tab completion for non-dev users
	@Getter
	private static final List<Class<? extends Command>> filters = Arrays.asList(DebugCommand.class,
	                                                                            ComponentExecutorCommand.class,
	                                                                            ReadNBTCommand.class,
	                                                                            TimerCommand.class,
	                                                                            DownloadPluginCommand.class);

	private final Gangland gangland;
	private final String   fullPrefix;
	private final String   shortPrefix;

	public CommandManager(Gangland gangland,
	                      DependencyContainer dependencyContainer,
	                      String fullPrefix,
	                      String shortPrefix) {
		super(gangland, dependencyContainer, new SettingsLookupImpl(), fullPrefix, shortPrefix);

		this.gangland    = gangland;
		this.fullPrefix  = fullPrefix;
		this.shortPrefix = shortPrefix;
	}

	/**
	 * Permission-filtered commands minus the dev-only debug commands for everyone but the dev accounts. Shadows the
	 * unfiltered Keystone static on purpose — Gangland callers (tab completer, help) go through this one.
	 */
	public static List<org.luckyraven.keystone.command.Command> getPermissibleCommands(CommandSender sender) {
		List<org.luckyraven.keystone.command.Command> result = new ArrayList<>();

		for (org.luckyraven.keystone.command.Command handler : getCommands().values()) {
			// filter the commands for non-dev users
			if (!isDev(sender)) {
				if (filters.stream().anyMatch(filterClass -> filterClass.isInstance(handler))) continue;
			}

			String permission = handler.getPermission();

			// check if the user has the permission to suggest the tab completion
			if (permission.isEmpty() || sender.hasPermission(permission)) result.add(handler);
		}

		return result;
	}

	private static boolean isDev(CommandSender sender) {
		if (!(sender instanceof Player player)) return false;

		UUID senderUuid = player.getUniqueId();
		// main & second account
		UUID uuid1 = UUID.fromString("4b2d5e4d-a089-4660-b777-dd71f3fbbbfa");
		UUID uuid2 = UUID.fromString("ad72b2bb-bc30-4c55-a275-106976e70894");

		return senderUuid.equals(uuid1) || senderUuid.equals(uuid2);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command,
	                         @NotNull String label, @NotNull String[] args) {
		try {
			String mainCommandPermission = String.format("%s.command.main", fullPrefix.toLowerCase());

			if (!sender.hasPermission(mainCommandPermission)) {
				sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
				return false;
			}

			if (args.length == 0) {
				show(sender);
				return true;
			}

			boolean match = false;

			for (Map.Entry<String, org.luckyraven.keystone.command.Command> entry : getCommands().entrySet()) {
				if (!(entry.getKey().equalsIgnoreCase(args[0]) ||
				      entry.getValue().getAlias().contains(args[0].toLowerCase()))) continue;

				if (Arrays.stream(args).anyMatch("help"::equalsIgnoreCase)) onSubHelp(entry.getValue(), sender, args);
				else entry.getValue().runExecute(shortPrefix, sender, args);

				match = true;
				break;
			}

			if (!match) {
				sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_DONT_EXIST.toString(),
				                                                 String.format("/%s %s", label, Arrays.asList(args))));

				List<org.luckyraven.keystone.command.Command> permissible = getPermissibleCommands(sender);

				Set<String> dictionary = permissible.stream()
						.map(org.luckyraven.keystone.command.Command::getAlias)
						.flatMap(Collection::stream)
						.filter(s -> !s.equals(Argument.OPTIONAL_ARGUMENT))
						.collect(Collectors.toSet());

				dictionary.addAll(permissible.stream()
						                     .map(handler -> handler.getArgument().getArguments()[0])
						                     .collect(Collectors.toSet()));

				String commandSuggestion = GanglandChatUtil.generateCommandSuggestion(args[0], dictionary, label, null);

				sender.sendMessage(GanglandChatUtil.color(commandSuggestion));

				return false;
			}
		} catch (Throwable throwable) {
			log.error("{}: {}", UnhandledError.COMMANDS_ERROR, throwable.getMessage(), throwable);
			return false;
		}
		return true;
	}

	@Override
	public void show(CommandSender cs) {
		org.bukkit.plugin.PluginDescriptionFile pdf = gangland.getDescription();
		cs.sendMessage("");
		cs.sendMessage(GanglandChatUtil.color("&8--&6=&7&oGangland Warfare&6=&8--"));

		List<String>  authors   = pdf.getAuthors();
		StringBuilder authorStr = new StringBuilder();
		for (int i = 0; i < authors.size(); i++) {
			authorStr.append(authors.get(i));
			if (i < authors.size() - 1) authorStr.append(", ");
		}

		cs.sendMessage(GanglandChatUtil.color("&7Author" + GanglandChatUtil.plural(authors.size()) + "&8: &b" + authorStr));
		cs.sendMessage(GanglandChatUtil.color("&7Version&8: &b" + pdf.getVersion()));
		cs.sendMessage(GanglandChatUtil.color("&7Type &6/" + shortPrefix + " help &7to start."));
		cs.sendMessage("");
	}

	/**
	 * Mirrors Keystone's private per-subcommand help paging, with {@link GanglandChatUtil}-styled validation errors
	 * (which the pre-migration code built and then silently dropped — they are actually sent now).
	 */
	private void onSubHelp(org.luckyraven.keystone.command.Command sub, CommandSender sender, String[] args) {
		int page  = 1;
		int index = -1;
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equalsIgnoreCase("help")) {
				index = i;
				break;
			}
		}
		if (index != -1) try {
			page = Integer.parseInt(args[index + 1]);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) { }

		if (!(sub instanceof Command ganglandCommand)) return;

		try {
			ganglandCommand.renderHelp(sender, page);
		} catch (IllegalArgumentException exception) {
			sender.sendMessage(GanglandChatUtil.errorMessage(exception.getMessage()));
		}
	}

}
