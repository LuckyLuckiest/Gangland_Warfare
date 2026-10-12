package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * {@code /glw gang members [page]}: the sender's gang roster grouped by rank (the owner rank first, the others by
 * name), ten members to a page, a rank header printed above each group on the page.
 */
class GangMembersCommand extends SubArgument {

	private static final int PAGE_SIZE = 10;

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final GangMessages        messages;

	private final GangStatsService statsService;

	protected GangMembersCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                             @Qualifier("online") UserManager<Player> userManager, GangManager gangManager,
	                             GangMessages messages, GangStatsService statsService) {
		super(gangland, new String[]{"members"}, tree, parent);

		this.statsService = statsService;

		this.userManager = userManager;
		this.gangManager = gangManager;
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
		}, sender -> {
			Gang gang = gangOf(sender);
			int  pages = gang == null ? 1 : pages(gang);
			return IntStream.rangeClosed(1, pages).mapToObj(String::valueOf).toList();
		});
	}

	private Gang gangOf(CommandSender sender) {
		if (!(sender instanceof Player player)) return null;

		User<Player> user = userManager.getUser(player);
		return user == null || !user.hasGang() ? null : gangManager.getGang(user.getGangId());
	}

	private static int pages(Gang gang) {
		return Math.max(1, (gang.getMembers().size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void sendPage(CommandSender sender, int requested) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}

		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		if (!user.hasGang()) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return;
		}

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return;
		}

		// owner rank first, then the rest by name; members of one rank keep their roster order (stable sort)
		String                    tail   = GangSettings.getGangRankTail();
		Map<String, List<Member>> groups = new TreeMap<>(Comparator.<String, Boolean>comparing(
				rank -> !rank.equalsIgnoreCase(tail)).thenComparing(String.CASE_INSENSITIVE_ORDER));
		for (Member member : gang.getMembers()) {
			String rank = member.getRank() == null ? "-" : member.getRank().getName();
			groups.computeIfAbsent(rank, key -> new ArrayList<>()).add(member);
		}

		int pages = pages(gang);
		int page  = Math.min(Math.max(requested, 1), pages);
		int from  = (page - 1) * PAGE_SIZE;
		int to    = from + PAGE_SIZE;

		sender.sendMessage(messages.text("Members.Header", "&6%gang% &7- %count% members - page %page%/%pages%",
		                                 "gang", gang.getDisplayNameString(), "count", gang.getMembers().size(),
		                                 "page", page, "pages", pages));

		int index = 0;
		for (Map.Entry<String, List<Member>> group : groups.entrySet()) {
			boolean headerSent = false;
			for (Member member : group.getValue()) {
				if (index >= from && index < to) {
					if (!headerSent) {
						sender.sendMessage(messages.text("Members.Rank_Header", "&e%rank% &7(%count%)",
						                                 "rank", group.getKey(), "count", group.getValue().size()));
						headerSent = true;
					}
					sender.sendMessage(entry(member));
				}
				index++;
			}
		}

		if (page < pages) {
			String          next   = messages.text("Members.Next", "&7[Next page &e%page%&7]", "page", page + 1);
			BaseComponent[] footer = new ComponentBuilder(next)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw gang members " + (page + 1)))
					.create();
			sender.spigot().sendMessage(footer);
		}
	}

	private String entry(Member member) {
		OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getUuid());
		String        name    = offline.getName() == null ? "Unknown" : offline.getName();
		String status = offline.isOnline() ? messages.text("Members.Online", "&aonline")
		                                   : messages.text("Members.Offline", "&coffline");

		return messages.text("Members.Entry", "  &f%player% &7- %status%", "player", name, "status", status);
	}

}
