package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.BankTable;
import me.luckyraven.database.tables.UserTable;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class GangKickCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangKickCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "kick", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
		this.rankManager   = gangland.getInitializer().getRankManager();

		gangKick();
	}

	protected static List<String> getDescendantRanks(UserManager<Player> userManager, MemberManager memberManager,
													 GangManager gangManager, RankManager rankManager,
													 CommandSender sender) {
		Player       player     = (Player) sender;
		User<Player> user       = userManager.getUser(player);
		Member       userMember = memberManager.getMember(player.getUniqueId());

		if (!user.hasGang()) {
			return null;
		}

		Gang userGang = gangManager.getGang(user.getGangId());
		Rank userRank = userMember.getRank();

		if (userRank == null) {
			return null;
		}

		// get the members in the gang
		List<Member> members = userGang.getValue();

		// filter the members by rank
		List<String> descendantRanks = new ArrayList<>();

		for (Member member : members) {
			Rank memberRank = member.getRank();

			if (memberRank == null) continue;

			Tree<Rank> rankTree = rankManager.getRankTree();

			if (!rankTree.isDescendant(userRank.getNode(), memberRank.getNode())) continue;

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

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	@SuppressWarnings("unchecked")
	private void gangKick() {
		Argument kickName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getGroup()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlineName   = offlinePlayer.getName();

				if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				player.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Rank playerRank = userMember.getRank();
			Rank targetRank = targetMember.getRank();

			if (playerRank == null || targetRank == null) {
				player.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			Tree.Node<Rank> playerNode = userMember.getRank().getNode();
			Tree.Node<Rank> targetNode = targetMember.getRank().getNode();

			if (!rankManager.getRankTree().isDescendant(targetNode, playerNode)) {
				player.sendMessage(MessageAddon.GANG_HIGHER_RANK_ACTION.toString());
				return;
			}

			User<? extends OfflinePlayer> targetUser;
			OfflinePlayer                 offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());

			if (offlinePlayer.isOnline()) targetUser = userManager.getUser(offlinePlayer.getPlayer());
			else {
				targetUser = new User<>(offlinePlayer);

				Initializer      initializer      = gangland.getInitializer();
				GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
				List<Table<?>>   tables           = ganglandDatabase.getTables();

				UserTable userTable = initializer.getInstanceFromTables(UserTable.class, tables);
				BankTable bankTable = initializer.getInstanceFromTables(BankTable.class, tables);

				initializer.getUserManager().initializeUserData(targetUser, userTable, bankTable);

				// no user initializer event called so far (need to work with it until fully compatible)

				initializer.getOfflineUserManager().add((User<OfflinePlayer>) targetUser);
			}

			if (targetUser.getUser() instanceof Player p) {
				p.sendMessage(MessageAddon.KICKED_FROM_GANG.toString());
			}

			gang.removeMember(targetUser, targetMember);

			player.sendMessage(MessageAddon.GANG_KICKED_TARGET.toString()
															  .replace("%player%", Objects.requireNonNull(
																	  offlinePlayer.getName())));
		}, sender -> getDescendantRanks(userManager, memberManager, gangManager, rankManager, sender));

		this.addSubArgument(kickName);
	}

}
