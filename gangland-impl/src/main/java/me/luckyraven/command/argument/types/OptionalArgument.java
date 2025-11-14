package me.luckyraven.command.argument.types;

import me.luckyraven.command.argument.Argument;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class OptionalArgument extends Argument {

	private final Function<CommandSender, List<String>>        customStrings;
	private final Function<CommandSender, Map<String, String>> displayToValue;

	public OptionalArgument(JavaPlugin plugin, Tree<Argument> tree) {
		this(plugin, tree, null);
	}

	public OptionalArgument(JavaPlugin plugin, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action) {
		this(plugin, tree, action, null);
	}

	public OptionalArgument(JavaPlugin plugin, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action,
							Function<CommandSender, List<String>> customStrings) {
		this(plugin, tree, action, customStrings, null);
	}

	public OptionalArgument(JavaPlugin plugin, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action,
							Function<CommandSender, List<String>> customStrings,
							Function<CommandSender, Map<String, String>> displayToValue) {
		this(plugin, new String[0], tree, action, customStrings, displayToValue);
	}

	public OptionalArgument(JavaPlugin plugin, String[] possibleArguments, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action) {
		this(plugin, possibleArguments, tree, action, null);
	}

	public OptionalArgument(JavaPlugin plugin, String[] possibleArguments, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action,
							Function<CommandSender, List<String>> customStrings) {
		this(plugin, possibleArguments, tree, action, customStrings, null);
	}

	public OptionalArgument(JavaPlugin plugin, String[] possibleArguments, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action,
							Function<CommandSender, List<String>> customStrings,
							Function<CommandSender, Map<String, String>> displayToValue) {
		super(plugin, argumentsWithOptional(possibleArguments), tree, action);

		this.customStrings  = customStrings;
		this.displayToValue = displayToValue;
	}

	private static String[] argumentsWithOptional(String[] possibleArguments) {
		List<String> arguments = new ArrayList<>();

		Arrays.stream(possibleArguments).filter(arg -> !arg.equals(OPTIONAL_ARGUMENT)).forEach(arguments::add);

		arguments.add(OPTIONAL_ARGUMENT);

		return arguments.toArray(String[]::new);
	}

	@Override
	public List<String> getArgumentString(CommandSender sender) {
		if (customStrings != null) {
			List<String> strings = customStrings.apply(sender);

			if (strings == null || strings.isEmpty()) {
				return List.of("");
			}

			return strings;
		}

		if (getArguments().length > 1) {
			Stream<String> stringStream = Arrays.stream(getArguments()).filter(arg -> !arg.equals(OPTIONAL_ARGUMENT));

			return stringStream.toList();
		}

		return List.of(getArguments());
	}

	public String getActualValue(String display, CommandSender sender) {
		if (displayToValue == null) {
			return display;
		}

		Map<String, String> map = displayToValue.apply(sender);

		if (map == null || map.isEmpty()) {
			return display;
		}

		return map.getOrDefault(display, display);
	}

	public boolean hasCustomStrings() {
		return customStrings != null;
	}

}
