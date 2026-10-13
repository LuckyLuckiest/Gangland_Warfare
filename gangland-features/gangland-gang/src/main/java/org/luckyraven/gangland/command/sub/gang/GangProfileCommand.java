package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;
import java.util.UUID;

/**
 * {@code /glw gang profile [player]}: a player's gang card in chat (gang, rank, kills, deaths, kdr, assists and where
 * the gang stands in the kills ranking). The profile menu, when a menu file declares this command, is opened by the core
 * before this runs.
 */
final class GangProfileCommand extends SubArgument {

	private final GangStatsService statsService;
	private final MemberManager    memberManager;
	private final GangManager      gangManager;
	private final GangMessages     messages;

	GangProfileCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangStatsService statsService,
	                   MemberManager memberManager, GangManager gangManager, GangMessages messages) {
		super(gangland, "profile", tree, parent);

		this.statsService  = statsService;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.messages      = messages;

		OptionalArgument player = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			send(sender, args[2]);
		}, sender -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
		player.setDisplayName("player");
		this.addSubArgument(player);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> send(sender, null);
	}

	private void send(CommandSender sender, String name) {
		UUID   id;
		String shown;
		if (name != null) {
			id    = null;
			shown = name;
			Player online = Bukkit.getPlayerExact(name);
			if (online != null) {
				id    = online.getUniqueId();
				shown = online.getName();
			} else {
				// names come from the members' own UUIDs: a by-name lookup can block on a Mojang request
				for (Gang each : gangManager.getAll()) {
					for (Member candidate : each.getMembers()) {
						if (candidate.getUuid() == null) continue;

						OfflinePlayer offline = Bukkit.getOfflinePlayer(candidate.getUuid());
						if (offline != null && name.equalsIgnoreCase(offline.getName())) {
							id    = candidate.getUuid();
							shown = offline.getName();
						}
					}
				}
			}
		} else if (sender instanceof Player self) {
			id    = self.getUniqueId();
			shown = self.getName();
		} else {
			sender.sendMessage(messages.text("Stats.Profile_Needs_Player", "&cName a player: /glw gang profile <player>"));
			return;
		}

		Member member = id == null ? null : memberManager.getMember(id);
		Gang   gang   = member == null || !member.hasGang() ? null : gangManager.getGang(member.getGangId());
		if (gang == null) {
			sender.sendMessage(messages.text("Stats.Profile_No_Gang", "&f%player% &7is not in a gang.",
			                                 "player", shown));
			return;
		}

		String rank = member.getRank() == null ? "-" : member.getRank().getName();
		double kdr  = member.getDeaths() == 0 ? member.getKills() : (double) member.getKills() / member.getDeaths();
		sender.sendMessage(messages.text("Stats.Profile_Card_Title", "&6%player% &7- %gang%",
		                                 "player", shown, "gang", gang.getDisplayNameString()));
		sender.sendMessage(messages.text("Stats.Profile_Card_Rank", "&7Rank: &f%rank% &7- joined &f%joined%",
		                                 "rank", rank, "joined", member.getGangJoinDateString()));
		sender.sendMessage(messages.text("Stats.Profile_Card_Stats",
		                                 "&7Kills &a%kills% &7- deaths &c%deaths% &7- kdr &e%kdr% &7- assists &b%assists%",
		                                 "kills", member.getKills(), "deaths", member.getDeaths(),
		                                 "kdr", GangStatsService.format("kdr", kdr), "assists", member.getAssists()));

		List<GangStatsService.Row> ranked   = statsService.ranking("kills", true);
		int                        position = 0;
		for (int i = 0; i < ranked.size(); i++) {
			if (ranked.get(i).gangId() == gang.getId()) position = i + 1;
		}
		sender.sendMessage(messages.text("Stats.Profile_Card_Gang", "&7Gang ranking by kills: &e#%position%",
		                                 "position", position == 0 ? "-" : String.valueOf(position)));
	}

}
