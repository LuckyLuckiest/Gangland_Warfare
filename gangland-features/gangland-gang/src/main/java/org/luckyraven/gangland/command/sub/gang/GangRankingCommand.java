package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.stats.GangStatsService.Row;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * {@code /glw gang ranking [stat] [page]}: the gangs ordered by a stat, read from the shared stats snapshot. The wealth
 * stats (balance, money) need {@code gangland.gang.ranking.wealth}.
 */
final class GangRankingCommand extends SubArgument {

	private static final String DEFAULT_STAT = "kills";

	private final GangStatsService statsService;
	private final GangMessages     messages;
	private final GangRules        rules;

	GangRankingCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangStatsService statsService,
	                   GangMessages messages, GangRules rules) {
		super(gangland, "ranking", tree, parent);

		this.statsService = statsService;
		this.messages     = messages;
		this.rules        = rules;

		OptionalArgument page = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			send(sender, args[2].toLowerCase(Locale.ROOT), parsePage(args, 3));
		}, sender -> pageNumbers(sender, null));

		OptionalArgument stat = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			send(sender, args[2].toLowerCase(Locale.ROOT), 1);
		}, this::statNames);
		stat.setDisplayName("stat");
		page.setDisplayName("page");
		stat.addSubArgument(page);
		this.addSubArgument(stat);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> send(sender, DEFAULT_STAT, 1);
	}

	private static int parsePage(String[] args, int index) {
		try {
			return Integer.parseInt(args[index]);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
			return 1;
		}
	}

	private boolean wealth(CommandSender sender) {
		return sender.hasPermission(GangPermissions.RANKING_WEALTH);
	}

	private List<String> statNames(CommandSender sender) {
		return statsService.statKeys().stream().filter(key -> !statsService.isWealth(key) || wealth(sender)).toList();
	}

	private List<String> pageNumbers(CommandSender sender, String stat) {
		int pages = pages(statsService.ranking(stat == null ? DEFAULT_STAT : stat, wealth(sender)).size());
		return IntStream.rangeClosed(1, pages).mapToObj(String::valueOf).toList();
	}

	private int pages(int rows) {
		int size = rules.getRankingPageSize();
		return Math.max(1, (rows + size - 1) / size);
	}

	private void send(CommandSender sender, String stat, int requested) {
		boolean known = statsService.statKeys().contains(stat);
		if (!known || (statsService.isWealth(stat) && !wealth(sender))) {
			sender.sendMessage(messages.text("Stats.Unknown_Stat", "&cUnknown stat. Choose one of: &f%stats%",
			                                 "stats", String.join(", ", statNames(sender))));
			return;
		}

		List<Row> ranked = statsService.ranking(stat, true);
		if (ranked.isEmpty()) {
			sender.sendMessage(messages.text("Stats.Ranking_Empty", "&7No gang is ranked for &f%stat%&7 yet.",
			                                 "stat", stat));
			return;
		}

		int pages = pages(ranked.size());
		int page  = Math.min(Math.max(requested, 1), pages);
		int size  = rules.getRankingPageSize();

		sender.sendMessage(messages.text("Stats.Ranking_Header", "&6Gang ranking &7- &e%stat% &7- page %page%/%pages%",
		                                 "stat", stat, "page", page, "pages", pages));

		int from = (page - 1) * size;
		int to   = Math.min(from + size, ranked.size());
		for (int i = from; i < to; i++) {
			Row row = ranked.get(i);
			sender.sendMessage(messages.text("Stats.Ranking_Row", "&e#%rank% &f%name% &7- &a%value%",
			                                 "rank", i + 1, "name", row.name(), "value", GangStatsService.format(stat, row.value(stat)),
			                                 "level", row.level()));
		}

		if (page < pages) {
			String next = messages.text("Stats.Ranking_Next", "&7[Next page &e%page%&7]", "page", page + 1);
			BaseComponent[] footer = new ComponentBuilder(next)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
					                      "/glw gang ranking " + stat + " " + (page + 1)))
					.create();
			sender.spigot().sendMessage(footer);
		}
	}

}
