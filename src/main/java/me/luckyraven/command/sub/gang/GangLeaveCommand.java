package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.RankManager;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;

class GangLeaveCommand extends SubArgument {

	private final Gangland                              gangland;
	private final Tree<Argument>                        tree;
	private final UserManager<Player>                   userManager;
	private final MemberManager                         memberManager;
	private final GangManager                           gangManager;
	private final RankManager                           rankManager;
	private final HashMap<User<Player>, CountdownTimer> leaveTimer;
	private final ConfirmArgument                       leaveConfirm;

	GangLeaveCommand(Gangland gangland, Tree<Argument> tree, UserManager<Player> userManager,
	                 MemberManager memberManager, GangManager gangManager, RankManager rankManager) {
		super("leave", tree);

		setPermission(getPermission() + ".leave");

		this.gangland = gangland;
		this.tree = tree;

		this.userManager = userManager;
		this.memberManager = memberManager;
		this.gangManager = gangManager;
		this.rankManager = rankManager;

		this.leaveTimer = new HashMap<>();
		this.leaveConfirm = leaveConfirm();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			// need to check if they were the owner or not
			// if it was the owner, then they need to transfer the rank
			if (member.getRank().match(rankManager.get(SettingAddon.getGangRankTail()).getUsedId())) {
				player.sendMessage(MessageAddon.GANG_TRANSFER_OWNERSHIP.toString());
				return;
			}

			if (leaveConfirm.isConfirmed()) return;

			leaveConfirm.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> player.sendMessage(
					ChatUtil.confirmCommand(new String[]{"gang", "leave"})), null, time -> {
				leaveConfirm.setConfirmed(false);
				leaveTimer.remove(user);
			});

			timer.start();
			leaveTimer.put(user, timer);
		};
	}

	private ConfirmArgument leaveConfirm() {
		return new ConfirmArgument(tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			// need to check if they were the owner or not
			// if it was the owner, then they need to transfer the rank
			if (member.getRank().match(rankManager.get(SettingAddon.getGangRankTail()).getUsedId())) {
				player.sendMessage(MessageAddon.GANG_TRANSFER_OWNERSHIP.toString());
				return;
			}

			// if they were not the owner they can leave
			// anyone leaving will not get a piece of the pie, thus the contribution would not be counted
			gang.removeMember(user, member);
			player.sendMessage(MessageAddon.GANG_LEAVE.toString());

			// update to database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> gangDatabase.updateMembersTable(member));
				}

				if (handler instanceof UserDatabase userDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> userDatabase.updateAccountTable(user));
				}
			}

			CountdownTimer timer = leaveTimer.get(user);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				leaveTimer.remove(user);
			}
		});
	}

}
