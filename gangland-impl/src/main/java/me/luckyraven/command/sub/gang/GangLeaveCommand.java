package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
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

	protected GangLeaveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "leave", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
		this.rankManager   = gangland.getInitializer().getRankManager();

		this.leaveTimer   = new HashMap<>();
		this.leaveConfirm = leaveConfirm();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				user.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			// need to check if they were the owner or not
			// if it was the owner, then they need to transfer the rank
			if (member.getRank() == null) return;

			Rank tail = rankManager.get(SettingAddon.getGangRankTail());

			if (tail == null) return;

			if (member.getRank().match(tail.getUsedId())) {
				user.sendMessage(MessageAddon.GANG_TRANSFER_OWNERSHIP.toString());
				return;
			}

			if (leaveConfirm.isConfirmed()) return;

			leaveConfirm.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				user.sendMessage(ChatUtil.confirmCommand(new String[]{"gang", "leave"}));
			}, null, time -> {
				leaveConfirm.setConfirmed(false);
				leaveTimer.remove(user);
			});

			timer.start(false);
			leaveTimer.put(user, timer);
		};
	}

	private ConfirmArgument leaveConfirm() {
		return new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				user.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			// need to check if they were the owner or not
			// if it was the owner, then they need to transfer the rank
			if (member.getRank() == null) return;

			Rank tail = rankManager.get(SettingAddon.getGangRankTail());

			if (tail == null) return;

			if (member.getRank().match(tail.getUsedId())) {
				user.sendMessage(MessageAddon.GANG_TRANSFER_OWNERSHIP.toString());
				return;
			}

			// if they were not the owner, they can leave
			// anyone leaving will not get a piece of the pie, thus the contribution would not be counted
			gang.removeMember(user, member);
			user.sendMessage(MessageAddon.GANG_LEAVE.toString());

			CountdownTimer timer = leaveTimer.get(user);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				leaveTimer.remove(user);
			}
		});
	}

}
