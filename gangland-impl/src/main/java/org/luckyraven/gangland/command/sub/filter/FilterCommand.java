package org.luckyraven.gangland.command.sub.filter;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.inventory.filter.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic entry point for list-view filter mutations. Replaces the old domain-specific {@code /glw gang search ...}
 * command. Shape:
 *
 * <pre>
 *   /glw filter &lt;binding&gt; sort
 *   /glw filter &lt;binding&gt; clear
 *   /glw filter &lt;binding&gt; search &lt;text...&gt;          // shorthand for: set NAME &lt;text...&gt;
 *   /glw filter &lt;binding&gt; set &lt;field&gt; &lt;text...&gt;
 *   /glw filter &lt;binding&gt; cycle &lt;field&gt; &lt;value-list&gt; // value-list = comma-separated enum options
 * </pre>
 * <p>
 * After every mutation the command reopens {@link FilterBinding#targetInventory()} so the player sees the refreshed
 * list.
 */
@CommandHandler
public final class FilterCommand extends Command {

	private static final String ACTION_SORT   = "sort";
	private static final String ACTION_CLEAR  = "clear";
	private static final String ACTION_SEARCH = "search";
	private static final String ACTION_SET    = "set";
	private static final String ACTION_CYCLE  = "cycle";
	private static final String ACTION_NEXT   = "next";

	private final FilterRegistry          registry;
	private final FilterStore             store;
	private final InventoryRuntimeContext inventoryRuntimeContext;

	public FilterCommand(Gangland gangland, FilterRegistry registry, FilterStore store,
	                     InventoryRuntimeContext inventoryRuntimeContext) {
		super(gangland, "filter", true);

		this.registry                = registry;
		this.store                   = store;
		this.inventoryRuntimeContext = inventoryRuntimeContext;
	}

	private static int indexOfIgnoreCase(List<String> options, String target) {
		if (target == null) return -1;
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).equalsIgnoreCase(target)) return i;
		}
		return -1;
	}

	private static String argAt(String[] args, int index) {
		return args != null && args.length > index && args[index] != null ? args[index] : "";
	}

	private static String joinFrom(String[] args, int from) {
		if (args == null || args.length <= from) return "";
		List<String> parts = new ArrayList<>();
		for (int i = from; i < args.length; i++) parts.add(args[i]);
		return String.join(" ", parts);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		// bare /glw filter — nothing to do; rely on tab-completion for discoverability
	}

	@Override
	protected void initializeArguments() {
		getArgument().addSubArgument(bindingArg());
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Filter");
	}

	private OptionalArgument bindingArg() {
		OptionalArgument binding = new OptionalArgument(getGangland(), getArgumentTree(),
		                                                (arg, sender, args) -> {
															// binding only — no action yet; noop
														}, sender -> registry.all()
				.stream()
				.map(FilterBinding::id).toList());
		binding.addSubArgument(actionArg());
		return binding;
	}

	// ── handlers ─────────────────────────────────────────────────────────────

	private OptionalArgument actionArg() {
		OptionalArgument action = new OptionalArgument(getGangland(), getArgumentTree(),
		                                               (arg, sender, args) -> handleNoValue(sender, args),
		                                               sender -> List.of(ACTION_SORT, ACTION_CLEAR, ACTION_SEARCH,
		                                                                 ACTION_SET, ACTION_CYCLE, ACTION_NEXT));
		action.addSubArgument(firstValueArg());
		return action;
	}

	private OptionalArgument firstValueArg() {
		OptionalArgument first = new OptionalArgument(getGangland(), getArgumentTree(),
		                                              (arg, sender, args) -> handleOneValue(sender, args),
		                                              sender -> List.of("<field|text>"));
		first.addSubArgument(secondValueArg());
		return first;
	}

	private OptionalArgument secondValueArg() {
		return new OptionalArgument(getGangland(), getArgumentTree(),
		                            (arg, sender, args) -> handleTwoValues(sender, args),
		                            sender -> List.of("<text>"));
	}

	private void handleNoValue(CommandSender sender, String[] args) {
		// /glw filter <binding> <action>
		if (!(sender instanceof Player player)) return;
		String        bindingId = argAt(args, 1);
		String        action    = argAt(args, 2);
		FilterBinding binding   = registry.get(bindingId);
		if (binding == null) return;

		switch (action.toLowerCase()) {
			case ACTION_SORT -> {
				SearchFilter current = store.get(bindingId, player);
				store.set(bindingId, player, current.withSort(binding.nextSort(current.sort())));
			}
			case ACTION_CLEAR -> store.set(bindingId, player, binding.empty());
			default -> { return; }
		}
		reopen(player, binding);
	}

	private void handleOneValue(CommandSender sender, String[] args) {
		// /glw filter <binding> <action> <value>
		if (!(sender instanceof Player player)) return;
		String        bindingId = argAt(args, 1);
		String        action    = argAt(args, 2);
		String        value     = argAt(args, 3);
		FilterBinding binding   = registry.get(bindingId);
		if (binding == null || value.isEmpty()) return;

		switch (action.toLowerCase()) {
			case ACTION_SEARCH -> applyText(player, bindingId, binding, "NAME", joinFrom(args, 3));
			case ACTION_CYCLE -> applyCycle(player, bindingId, binding, value);
			default -> { return; }
		}
	}

	private void handleTwoValues(CommandSender sender, String[] args) {
		// /glw filter <binding> <action> <field> <text...>
		if (!(sender instanceof Player player)) return;
		String        bindingId = argAt(args, 1);
		String        action    = argAt(args, 2);
		String        field     = argAt(args, 3);
		FilterBinding binding   = registry.get(bindingId);
		if (binding == null) return;

		if (ACTION_SET.equalsIgnoreCase(action)) {
			applyText(player, bindingId, binding, field, joinFrom(args, 4));
			return;
		}
		if (ACTION_NEXT.equalsIgnoreCase(action)) {
			applyNext(player, bindingId, binding, field, argAt(args, 4));
		}
	}

	/**
	 * Advances an enum-valued field through {@code csvOptions} (a single comma-separated token). After the last option
	 * the field is cleared, giving players an "off" state on the cycle. The current value is read via
	 * {@link FilterValue.EnumValue}; other value types are treated as unset and bumped to the first option.
	 */
	private void applyNext(Player player, String bindingId, FilterBinding binding, String fieldId, String csvOptions) {
		FilterField field = binding.findField(fieldId);
		if (field == null || csvOptions == null || csvOptions.isBlank()) return;

		List<String> options = new ArrayList<>();
		for (String part : csvOptions.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) options.add(trimmed);
		}
		if (options.isEmpty()) return;

		SearchFilter current  = store.get(bindingId, player);
		FilterValue  existing = current.get(field);
		int          idx      = -1;
		if (existing instanceof FilterValue.EnumValue ev) idx = indexOfIgnoreCase(options, ev.value());

		int nextIdx = idx + 1;
		SearchFilter next = nextIdx >= options.size()
		                    ? current.without(field)
		                    : current.with(field, new FilterValue.EnumValue(options.get(nextIdx)));
		store.set(bindingId, player, next);
		reopen(player, binding);
	}

	private void applyText(Player player, String bindingId, FilterBinding binding, String fieldId, String text) {
		FilterField field = binding.findField(fieldId);
		if (field == null) return;
		SearchFilter current = store.get(bindingId, player);
		SearchFilter next = (text == null || text.isBlank())
		                    ? current.without(field)
		                    : current.with(field, new FilterValue.TextValue(text.trim()));
		store.set(bindingId, player, next);
		reopen(player, binding);
	}

	private void applyCycle(Player player, String bindingId, FilterBinding binding, String fieldId) {
		FilterField field = binding.findField(fieldId);
		if (field == null) return;
		// Enum cycling is stateful and needs the current index — this command-path only clears the cycled field so
		// players can escape via chat/macro. Use SearchButtonFactory#cycleEnumClick for the in-UI advancing click.
		store.set(bindingId, player, store.get(bindingId, player).without(field));
		reopen(player, binding);
	}

	private void reopen(Player player, FilterBinding binding) {
		String target = binding.targetInventory();
		if (target == null || target.isBlank()) return;
		inventoryRuntimeContext.openInventoryForPlayer(player, target);
	}

}
