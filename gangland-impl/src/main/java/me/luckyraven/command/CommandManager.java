package me.luckyraven.command;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.sub.DownloadPluginCommand;
import me.luckyraven.command.sub.debug.ComponentExecutorCommand;
import me.luckyraven.command.sub.debug.DebugCommand;
import me.luckyraven.command.sub.debug.ReadNBTCommand;
import me.luckyraven.command.sub.debug.TimerCommand;
import me.luckyraven.core.UnhandledError;
import me.luckyraven.core.bean.autowire.DependencyContainer;
import me.luckyraven.core.bean.command.CommandService;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static me.luckyraven.util.GanglandChatUtil.color;

@CustomLog
public final class CommandManager extends CommandService<Command> implements CommandExecutor {

	// classes that shouldn't be displayed in tab completion
	@Getter
	private static final List<Class<? extends Command>> filters = Arrays.asList(DebugCommand.class,
	                                                                            ComponentExecutorCommand.class,
	                                                                            ReadNBTCommand.class,
	                                                                            TimerCommand.class,
	                                                                            DownloadPluginCommand.class);

	private static final Map<String, Command> commands = new HashMap<>();

	private final Gangland            gangland;
	private final DependencyContainer dependencyContainer;
	private final String              fullPrefix;
	private final String              shortPrefix;

	public CommandManager(Gangland gangland,
	                      DependencyContainer dependencyContainer,
	                      String fullPrefix,
	                      String shortPrefix) {
		this.gangland            = gangland;
		this.dependencyContainer = dependencyContainer;
		this.fullPrefix          = fullPrefix;
		this.shortPrefix         = shortPrefix;
	}

	public static List<Command> getPermissibleCommands(CommandSender sender) {
		List<Command> commands = new ArrayList<>();

		for (Command handler : CommandManager.commands.values()) {
			// filter the commands for non-dev users
			if (!isDev(sender)) {
				if (filters.stream().anyMatch(filterClass -> filterClass.isInstance(handler))) continue;
			}

			String permission = handler.getPermission();

			// check if the user has the permission to suggest the tab completion
			if (permission.isEmpty() || sender.hasPermission(handler.getPermission())) commands.add(handler);
		}

		return commands;
	}

	public static Map<String, Command> getCommands() {
		return new HashMap<>(commands);
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
	public boolean invokeCondition(String condition) throws InvocationTargetException, IllegalAccessException {
		Method method = Settings.getSetting(condition);

		if (method != null && (method.getReturnType().getSimpleName().equalsIgnoreCase("boolean") ||
		                       method.getReturnType() == Boolean.class)) {
			return (boolean) method.invoke(null);
		}

		return false;
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

			for (Map.Entry<String, Command> entry : commands.entrySet()) {
				if (!(entry.getKey().equalsIgnoreCase(args[0]) ||
				      entry.getValue().getAlias().contains(args[0].toLowerCase()))) continue;

				if (Arrays.stream(args).anyMatch("help"::equalsIgnoreCase)) onHelp(entry, sender, args);
				else entry.getValue().runExecute(sender, args);

				match = true;
				break;
			}

			if (!match) {
				sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_DONT_EXIST.toString(),
				                                                 String.format("/%s %s", label, Arrays.asList(args))));

				List<Command> commands = getPermissibleCommands(sender);

				Set<String> dictionary = commands.stream()
						.map(Command::getAlias)
						.flatMap(Collection::stream)
						.filter(s -> !s.equals(Argument.OPTIONAL_ARGUMENT))
						.collect(Collectors.toSet());

				dictionary.addAll(commands.stream()
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

	public void addCommand(Command sub) {
		commands.put(sub.getLabel(), sub);
	}

	public void show(CommandSender cs) {
		PluginDescriptionFile pdf = gangland.getDescription();
		cs.sendMessage("");
		cs.sendMessage(color("&8--&6=&7&oGangland Warfare&6=&8--"));

		List<String>  authors   = pdf.getAuthors();
		StringBuilder authorStr = new StringBuilder();
		for (int i = 0; i < authors.size(); i++) {
			authorStr.append(authors.get(i));
			if (i < authors.size() - 1) authorStr.append(", ");
		}

		cs.sendMessage(color("&7Author" + GanglandChatUtil.plural(authors.size()) + "&8: &b" + authorStr));
		cs.sendMessage(color("&7Version&8: &b" + pdf.getVersion()));
		cs.sendMessage(color("&7Type &6/" + shortPrefix + " help &7to start."));
		cs.sendMessage("");
	}

	@Override
	protected Command createInstance(Class<?> clazz) throws Exception {
		if (!Command.class.isAssignableFrom(clazz)) {
			throw new IllegalArgumentException(clazz.getName() + " does not extend " + Command.class.getSimpleName());
		}

		// Construct via the root container so commands can declare any registered bean as a constructor parameter
		// (UserManager, GangManager, WeaponManager, etc.) and receive it automatically. The legacy
		// (Gangland) constructor still works because the container resolves Gangland the same as any other bean.
		Command instance = (Command) dependencyContainer.createInstance(clazz);

		// Build sub-arguments AFTER the constructor returns so subclass fields (constructor-injected beans) are
		// fully assigned. The Command base class deliberately does NOT call initializeArguments() in its constructor
		// — Java init order means subclass fields are still null at the point super() runs, so any sub-argument that
		// receives one of those fields would NPE.
		instance.initializeArguments();

		return instance;
	}

	@Override
	protected void registerCommand(Command command) {
		addCommand(command);
	}

	private void onHelp(Map.Entry<String, Command> entry, CommandSender sender, String[] args) {
		// Get the page number if it exists
		int page = 1;
		int index = IntStream.range(0, args.length - 1)
		                     .filter(i -> args[i].equalsIgnoreCase("help"))
		                     .findFirst()
		                     .orElse(-1);
		if (index != -1) try {
			page = Integer.parseInt(args[index + 1]);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) { }

		// display the help of the command (if mentioned)
		try {
			entry.getValue().help(sender, page);
		} catch (IllegalArgumentException exception) {
			GanglandChatUtil.errorMessage(exception.getMessage());
		}
	}

}
