package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;
import org.luckyraven.gangland.gang.GangSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.luckyraven.keystone.util.TimeUtil;
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
import org.luckyraven.gangland.util.TimeMessages;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.luckyraven.keystone.bean.Qualifier;
import lombok.CustomLog;

@CustomLog
class GangDeleteCommand extends SubArgument {

	private final JavaPlugin                   gangland;
	private final Tree<Argument>             tree;
	private final UserManager<Player>        userManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RankManager                rankManager;

	private final HashMap<User<Player>, AtomicReference<String>> deleteGangName;
	private final HashMap<CommandSender, CountdownTimer>         deleteGangTimer;

	private final ConfirmArgument confirmDelete;

	private final GangMembershipService membershipService;

	protected GangDeleteCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                            @Qualifier("online") UserManager<Player> userManager,
	                            MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager,
	                            GangMembershipService membershipService) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.membershipService = membershipService;

		this.gangland           = gangland;
		this.tree               = tree;
		this.userManager        = userManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;

		this.deleteGangName  = new HashMap<>();
		this.deleteGangTimer = new HashMap<>();

		this.confirmDelete = gangDeleteConfirm();
		this.addSubArgument(confirmDelete);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member member = memberManager.getMember(player.getUniqueId());

			if (member == null || !user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			// check if the player is the owner
			if (member.getRank() == null) return;

			Rank tail = rankManager.get(GangSettings.getGangRankTail());

			if (tail == null) return;

			if (!member.getRank().match(tail.getUsedId())) {
				user.sendMessage(Messages.NOT_OWNER.toString().replace("%tail%", GangSettings.getGangRankTail()));
				return;
			}

			if (confirmDelete.isLocked(sender)) return;

			Gang gang = gangManager.getGang(user.getGangId());
			if (gang == null) {
				user.resetGang();
				member.resetGang();
				member.setContribution(0D);
				memberManager.assignRank(member, null);
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"gang", "delete"}));
			deleteGangName.put(user, new AtomicReference<>(gang.getName()));

			confirmDelete.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String string = Messages.GANG_REMOVE_CONFIRM.toString();
					String replace = string.replace("%timer%", TimeUtil.formatTime(time.getTimeLeft(), true,
					                                                               TimeMessages.getInstance()));
					s.sendMessage(replace);
				}, time -> {
					confirmDelete.unlock(s);
					deleteGangName.remove(user);
					deleteGangTimer.remove(s);
				});

				timer.start(false);
				deleteGangTimer.put(s, timer);
			});
		};
	}

	private ConfirmArgument gangDeleteConfirm() {
		return new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member member = memberManager.getMember(player.getUniqueId());

			if (member == null || !user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			if (member.getRank() == null) return;

			// check if the player is the owner
			Rank tail = rankManager.get(GangSettings.getGangRankTail());

			if (tail == null) return;

			if (!member.getRank().match(tail.getUsedId())) {
				user.sendMessage(Messages.NOT_OWNER.toString().replace("%tail%", GangSettings.getGangRankTail()));
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());
			if (gang == null) {
				user.resetGang();
				member.resetGang();
				member.setContribution(0D);
				memberManager.assignRank(member, null);
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Result result = membershipService.disband(gang.getId(), player.getUniqueId(), false);
			if (!result.ok()) {
				user.sendMessage(membershipService.text(result));
				return;
			}

			deleteGangName.remove(user);

			CountdownTimer timer = deleteGangTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				deleteGangTimer.remove(sender);
			}
		});
	}

}
