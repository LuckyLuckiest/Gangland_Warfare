package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** {@code /glw gang list [page]}: every gang, most members first, then by name. Open to gangless players and console. */
class GangListCommand extends SubArgument {

	private static final int PAGE_SIZE = 8;

	private final GangManager         gangManager;
	private final UserManager<Player> userManager;
	private final GangMessages        messages;

	protected GangListCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangManager gangManager,
	                          @Qualifier("online") UserManager<Player> userManager, GangMessages messages) {
		super(gangland, "list", tree, parent);

		this.gangManager = gangManager;
		this.userManager = userManager;
		this.messages    = messages;

		this.addSubArgument(pageArgument(gangland, tree));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sendPage(sender, 1);
	}

	private Argument pageArgument(JavaPlugin gangland, Tree<Argument> tree) {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			int page;
			try {
				page = Integer.parseInt(args[2]);
			} catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
				page = 1;
			}
			sendPage(sender, page);
		}, sender -> IntStream.rangeClosed(1, pages())
				.mapToObj(String::valueOf)
				.toList());
	}

	private int pages() {
		int gangs = gangManager.getAll().size();
		return Math.max(1, (gangs + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void sendPage(CommandSender sender, int requested) {
		List<Gang> sorted = gangManager.getAll()
				.stream()
				.sorted(Comparator.comparingInt((Gang gang) -> gang.getMembers().size())
						.reversed()
						.thenComparing(Gang::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
				.toList();

		if (sorted.isEmpty()) {
			sender.sendMessage(messages.text("List.Empty", "&7There are no gangs yet."));
			return;
		}

		int pages = pages();
		int page  = Math.min(Math.max(requested, 1), pages);

		sender.sendMessage(messages.text("List.Header", "&6Gangs &7- page %page%/%pages%",
		                                 "page", page, "pages", pages));

		int from = (page - 1) * PAGE_SIZE;
		int to   = Math.min(from + PAGE_SIZE, sorted.size());
		for (Gang gang : sorted.subList(from, to)) {
			int online = gang.getOnlineMembers(userManager::getUser).size();
			String row = messages.text("List.Row", "&f%gang% &7- members: %members% - online: %online%",
			                           "gang", gang.getDisplayNameString(),
			                           "members", gang.getMembers().size(),
			                           "online", online);
			sender.spigot().sendMessage(new ComponentBuilder(row)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw gang info " + gang.getName()))
					.create());
		}

		if (page < pages) {
			String next = messages.text("List.Next", "&7[Next page &e%page%&7]", "page", page + 1);
			BaseComponent[] footer = new ComponentBuilder(next)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw gang list " + (page + 1)))
					.create();
			sender.spigot().sendMessage(footer);
		}
	}

}
