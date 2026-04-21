package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.file.configuration.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class GangMembersCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	protected GangMembersCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager, GangManager gangManager) {
		super(gangland, new String[]{"members", "list"}, tree, parent);

		this.userManager = userManager;
		this.gangManager = gangManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			sender.sendMessage(Messages.GANG_MEMBERS_HEADER.toString()
			                                               .replace("%gang%", gang.getDisplayNameString())
			                                               .replace("%count%",
			                                                        String.valueOf(gang.getMembers().size())));

			String entryTemplate = Messages.GANG_MEMBERS_ENTRY.toString();

			for (Member member : gang.getMembers()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				Rank          memberRank    = member.getRank();
				String        rankName      = memberRank == null ? "-" : memberRank.getName();
				String        name          = offlinePlayer.getName() == null ? "Unknown" : offlinePlayer.getName();
				String        status        = ChatUtil.color(offlinePlayer.isOnline() ? "&aonline" : "&coffline");

				sender.sendMessage(entryTemplate.replace("%player%", name)
				                                .replace("%rank%", rankName)
				                                .replace("%status%", status));
			}
		};
	}

}
