package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.luckyraven.keystone.bean.Qualifier;

class GangKickCommand extends SubArgument {

	private final JavaPlugin                   gangland;
	private final Tree<Argument>             tree;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RankManager                rankManager;

	private final GangMembershipService membershipService;

	protected GangKickCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                          @Qualifier("online") UserManager<Player> userManager, @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                          MemberManager memberManager, GangManager gangManager, RankManager rankManager,
								GangMembershipService membershipService) {
		super(gangland, "kick", tree, parent);

		this.membershipService = membershipService;

		this.gangland           = gangland;
		this.tree               = tree;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;

		gangKick();
	}

	protected static List<String> getDescendantRanks(UserManager<Player> userManager, MemberManager memberManager,
	                                                 GangManager gangManager, RankManager rankManager,
	                                                 CommandSender sender) {
		Player       player = (Player) sender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return null;

		Member userMember = memberManager.getMember(player.getUniqueId());

		// GR-01: a player with no cached Member would NPE further down this command.
		if (userMember == null || !user.hasGang()) {
			return null;
		}

		Gang userGang = gangManager.getGang(user.getGangId());
		Rank userRank = userMember.getRank();

		if (userRank == null) {
			return null;
		}

		// get the members in the gang
		List<Member> members = userGang.getMembers();

		// filter the members by rank
		List<String> descendantRanks = new ArrayList<>();

		for (Member member : members) {
			Rank memberRank = member.getRank();

			if (memberRank == null) continue;

			Tree<Rank> rankTree = rankManager.getRankTree();

			// Rank tree is head-rooted (lowest rank = root, tail/owner = deepest leaf), so "user outranks target"
			// means userRank is a descendant of memberRank. Argument order matches GangPromoteCommand's filter.
			if (!rankTree.isDescendant(memberRank.getNode(), userRank.getNode())) continue;

			OfflinePlayer offlinePlayer     = Bukkit.getOfflinePlayer(member.getUuid());
			String        offlinePlayerName = offlinePlayer.getName();

			if (offlinePlayerName == null) continue;

			descendantRanks.add(offlinePlayer.getName());
		}

		// if no descendants found
		if (descendantRanks.isEmpty()) {
			descendantRanks.add("");
		}

		// return the rank names
		return descendantRanks;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void gangKick() {
		Argument kickName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member userMember = memberManager.getMember(player.getUniqueId());

			// GR-01: a player with no cached Member would NPE further down this command.
			if (userMember == null || !user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getMembers()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlineName   = offlinePlayer.getName();

				if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				user.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Result result = membershipService.kick(gang.getId(), player.getUniqueId(), targetMember.getUuid());
			if (!result.ok()) {
				user.sendMessage(membershipService.text(result));
				return;
			}

			user.sendMessage(Messages.GANG_KICKED_TARGET.toString()
			                                            .replace("%player%", Bukkit.getOfflinePlayer(
					                                            targetMember.getUuid()).getName()));
		}, sender -> getDescendantRanks(userManager, memberManager, gangManager, rankManager, sender));

		this.addSubArgument(kickName);
	}

}
