package org.luckyraven.gangland.gang.command.debug;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.JsonFormatter;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Collection;
import java.util.List;

/**
 * Attaches {@code /glw debug gang-data|member-data|rank-data} under the core's {@code debug} command (WS5 G2 step
 * 14) — moved out of gangland-impl's {@code DebugCommand} wholesale; impl can no longer name
 * {@code GangManager}/{@code MemberManager}/{@code RankManager}.
 */
public final class GangDebugContribution implements CommandContribution {

	private final JavaPlugin          plugin;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final MemberManager       memberManager;
	private final RankManager         rankManager;

	public GangDebugContribution(JavaPlugin plugin, UserManager<Player> userManager, GangManager gangManager,
	                             MemberManager memberManager, RankManager rankManager) {
		this.plugin        = plugin;
		this.userManager   = userManager;
		this.gangManager   = gangManager;
		this.memberManager = memberManager;
		this.rankManager   = rankManager;
	}

	@Override
	public String parent() {
		return "debug";
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(gangData(tree), memberData(tree), rankData(tree));
	}

	private Argument gangData(Tree<Argument> tree) {
		return new Argument(plugin, "gang-data", tree, (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);

				if (user == null) return;

				if (user.hasGang()) {
					Gang gang = gangManager.getGang(user.getGangId());

					user.sendMessage(convertToJson(gang.toString()));
				} else {
					user.sendMessage("Not in a gang...");
				}
			} else {
				Collection<Gang> values = gangManager.getGangs().values();
				for (Gang gang : values) {
					sender.sendMessage(gang.toString());
				}
			}
		});
	}

	private Argument memberData(Tree<Argument> tree) {
		return new Argument(plugin, "member-data", tree, (argument, sender, args) -> {
			if (sender instanceof Player player) {
				Member member = memberManager.getMember(player.getUniqueId());

				// GR-01: a player with no cached Member would NPE this debug dump.
				if (member == null) return;

				player.sendMessage(convertToJson(member.toString()));
			} else {
				Collection<Member> values = memberManager.getMembers().values();
				for (Member member : values) {
					sender.sendMessage(member.toString());
				}
			}
		});
	}

	private Argument rankData(Tree<Argument> tree) {
		return new Argument(plugin, "rank-data", tree, (argument, sender, args) -> {
			Collection<Rank> values = rankManager.getRanks().values();
			if (sender instanceof Player) {
				for (Rank rank : values) {
					sender.sendMessage(convertToJson(rank.toString()));
				}
			} else {
				for (Rank rank : values) {
					sender.sendMessage(rank.toString());
				}
			}
		});
	}

	private String convertToJson(String input) {
		return new JsonFormatter().formatToJson(input, " ".repeat(3));
	}

}
