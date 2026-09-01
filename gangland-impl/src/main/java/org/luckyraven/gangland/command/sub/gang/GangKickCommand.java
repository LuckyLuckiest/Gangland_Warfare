package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.data.user.UserDataLoader;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.TableLookup;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class GangKickCommand extends SubArgument {

	private final Gangland                   gangland;
	private final Tree<Argument>             tree;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RankManager                rankManager;
	private final UserDataLoader             userDataLoader;
	private final GanglandDatabase           ganglandDatabase;

	protected GangKickCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          UserManager<Player> userManager, UserManager<OfflinePlayer> offlineUserManager,
	                          MemberManager memberManager, GangManager gangManager, RankManager rankManager,
	                          UserDataLoader userDataLoader,
	                          GanglandDatabase ganglandDatabase) {
		super(gangland, "kick", tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;
		this.userDataLoader     = userDataLoader;
		this.ganglandDatabase   = ganglandDatabase;

		gangKick();
	}

	protected static List<String> getDescendantRanks(UserManager<Player> userManager, MemberManager memberManager,
	                                                 GangManager gangManager, RankManager rankManager,
	                                                 CommandSender sender) {
		Player       player = (Player) sender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return null;

		Member userMember = memberManager.getMember(player.getUniqueId());

		if (!user.hasGang()) {
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

	@SuppressWarnings("unchecked")
	private void gangKick() {
		Argument kickName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
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

			// Self-action is a domain rule, never a permission decision — applied before the rank-hierarchy gate.
			if (targetMember.getUuid().equals(player.getUniqueId())) {
				user.sendMessage(Messages.GANG_CANNOT_ACT_SELF.toString());
				return;
			}

			Rank playerRank = userMember.getRank();
			Rank targetRank = targetMember.getRank();

			if (playerRank == null || targetRank == null) {
				user.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			Tree.Node<Rank> playerNode = userMember.getRank().getNode();
			Tree.Node<Rank> targetNode = targetMember.getRank().getNode();

			if (!rankManager.getRankTree().isDescendant(targetNode, playerNode)) {
				user.sendMessage(Messages.GANG_HIGHER_RANK_ACTION.toString());
				return;
			}

			User<? extends OfflinePlayer> targetUser;
			OfflinePlayer                 offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());

			if (offlinePlayer.isOnline()) {
				targetUser = userManager.getUser(offlinePlayer.getPlayer());
			} else {
				targetUser = offlineUserManager.create(offlinePlayer);

				List<Table<?>> tables = ganglandDatabase.getTables();

				UserTable userTable = TableLookup.find(UserTable.class, tables);
				BankTable bankTable = TableLookup.find(BankTable.class, tables);

				User<OfflinePlayer> offlineUser = (User<OfflinePlayer>) targetUser;

				userDataLoader.loadUserData(offlineUser, userTable, bankTable);

				// no user initializer event called so far (need to work with it until fully compatible)

				offlineUserManager.add(offlineUser);
			}

			if (targetUser == null) return;

			if (targetUser.getUser() instanceof Player targetPlayer) {
				targetPlayer.sendMessage(Messages.KICKED_FROM_GANG.toString());
			}

			gang.removeMember(targetUser, targetMember);

			user.sendMessage(Messages.GANG_KICKED_TARGET.toString()
			                                            .replace("%player%",
			                                                     Objects.requireNonNull(offlinePlayer.getName())));
		}, sender -> getDescendantRanks(userManager, memberManager, gangManager, rankManager, sender));

		this.addSubArgument(kickName);
	}

}
