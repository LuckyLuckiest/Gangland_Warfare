package org.luckyraven.gangland.command;

import lombok.CustomLog;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.SettingsLookupImpl;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.util.UnhandledError;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gangland's dispatcher — a subclass of Keystone's {@link org.luckyraven.keystone.command.CommandManager}.
 * Keystone owns scanning, two-phase instantiation via the {@link DependencyContainer}, the instance-scoped
 * registry, visibility filtering and per-subcommand help paging; this class keeps the consumer-side behavior:
 *
 * <ul>
 *     <li>{@link Messages}-localized error lines and the {@link GanglandChatUtil} "did you mean" styling;</li>
 *     <li>the dev-only listing filter, installed as a {@link DevCommandVisibilityFilter} through Keystone's
 *     visibility seam (1.7.3 — replaces the old static-hiding {@code getPermissibleCommands} shadow);</li>
 *     <li>the branded {@code /glw} splash in {@link #show}.</li>
 * </ul>
 *
 * <p>Condition evaluation ({@code @CommandHandler(condition = ...)}) runs through {@link SettingsLookupImpl},
 * which resolves dotted settings keys against the loaded settings map.
 */
@CustomLog
public final class CommandManager extends org.luckyraven.keystone.command.CommandManager {

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

		setVisibilityFilter(new DevCommandVisibilityFilter());
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

			for (Map.Entry<String, org.luckyraven.keystone.command.Command> entry : commandView().entrySet()) {
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

				List<org.luckyraven.keystone.command.Command> permissible = permissibleCommands(sender);

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
