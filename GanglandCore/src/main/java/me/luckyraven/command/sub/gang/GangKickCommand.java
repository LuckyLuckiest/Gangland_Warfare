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
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
		super("kick", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
		this.rankManager   = gangland.getInitializer().getRankManager();

		gangKick();
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
		Argument kickName = new OptionalArgument(tree, (argument, sender, args) -> {
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

				if (offlinePlayer.getName() == null) continue;

				if (!Objects.requireNonNull(offlinePlayer.getName()).equalsIgnoreCase(targetStr)) continue;

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
				List<Table<?>>   tables           = ganglandDatabase.getTables().stream().toList();

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
		});

		this.addSubArgument(kickName);
	}

}
