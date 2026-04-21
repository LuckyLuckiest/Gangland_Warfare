package me.luckyraven.command.sub.permissions;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class PermissionsCommand extends Command {

	static final int PAGE_SIZE = 7;

	private final PermissionManager permissionManager;

	public PermissionsCommand(Gangland gangland, PermissionManager permissionManager) {
		super(gangland, "permissions", false, "perms", "perm");

		this.permissionManager = permissionManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("permissions"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);

		getHelpInfo().addAll(list);
	}

	static void sendOverview(CommandSender sender, PermissionManager permissionManager) {
		Map<String, Integer> counts = permissionManager.getCategoryCounts();

		String string = Messages.PERMISSION_OVERVIEW_HEADER.toString();
		String replace = string.replace("%total%", String.valueOf(permissionManager.size()))
		                       .replace("%categories%", String.valueOf(counts.size()));
		sender.sendMessage(replace);

		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			String stringOverview = Messages.PERMISSION_OVERVIEW_CATEGORY.toString();
			String replaceOverview = stringOverview.replace("%category%", entry.getKey())
			                                       .replace("%count%", String.valueOf(entry.getValue()));
			sender.sendMessage(replaceOverview);
		}
	}

	static void sendHeader(CommandSender sender, String title, int page, int maxPages) {
		String header = GanglandChatUtil.color(
				"&3Oo&3&m------&r &8&l[&bG&fL&bW&8&l]&7 " + title + " &8[&7" + page + "&5/&7" + maxPages +
				"&8] &3&m------&3oO");

		sender.sendMessage("");
		sender.sendMessage(header);
		sender.sendMessage("");
	}

	static int parsePage(String raw) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException ignored) {
			return 1;
		}
	}

	static List<String> categorySuggestions(PermissionManager permissionManager) {
		List<String> suggestions = new ArrayList<>(permissionManager.getCategoryCounts().keySet());
		suggestions.add("<query>");

		return suggestions;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] args) {
		sendOverview(sender, permissionManager);
	}

	@Override
	protected void initializeArguments() {
		getArgument().addSubArgument(
				new PermissionsCategoriesSubArgument(getGangland(), getArgumentTree(), getArgument(),
				                                     permissionManager));
		getArgument().addSubArgument(
				new PermissionsCheckSubArgument(getGangland(), getArgumentTree(), getArgument(), permissionManager));

		Argument pageArg = new OptionalArgument(getGangland(), getArgumentTree(),
		                                        (a, sender, args) -> sendSearch(sender, args[1], parsePage(args[2])),
		                                        sender -> List.of("[page]"));

		Argument queryArg = new OptionalArgument(getGangland(), getArgumentTree(),
		                                         (a, sender, args) -> sendSearch(sender, args[1], 1),
		                                         sender -> categorySuggestions(permissionManager));

		queryArg.addSubArgument(pageArg);
		getArgument().addSubArgument(queryArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Permissions");
	}

	private void sendSearch(CommandSender sender, String query, int page) {
		List<String> hits = permissionManager.findByPrefix(query);
		if (hits.isEmpty()) hits = permissionManager.search(query);

		if (hits.isEmpty()) {
			sender.sendMessage(Messages.PERMISSION_LIST_EMPTY.toString().replace("%query%", query));
			return;
		}

		int max = (hits.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		if (page < 1 || page > max) {
			String string  = Messages.PERMISSION_PAGE_OUT_OF_RANGE.toString();
			String replace = string.replace("%page%", String.valueOf(page)).replace("%max%", String.valueOf(max));
			sender.sendMessage(replace);
			return;
		}

		sendHeader(sender, "Permissions: " + query, page, max);

		int start = (page - 1) * PAGE_SIZE;
		int end   = Math.min(start + PAGE_SIZE, hits.size());

		for (int i = start; i < end; i++) {
			sender.sendMessage(GanglandChatUtil.color("&7- &b" + hits.get(i)));
		}
	}

}
