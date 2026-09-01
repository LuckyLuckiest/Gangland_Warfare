package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

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
