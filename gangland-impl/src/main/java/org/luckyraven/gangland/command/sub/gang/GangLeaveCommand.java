package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.ArgumentLock;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.HashMap;

class GangLeaveCommand extends SubArgument {

	private static final long CONFIRM_WINDOW_TICKS = 20L * 60L;

	private final Gangland                           gangland;
	private final UserManager<Player>                userManager;
	private final MemberManager                      memberManager;
	private final GangManager                        gangManager;
	private final RankManager                        rankManager;
	private final ArgumentLock                       lock       = new ArgumentLock();
	private final HashMap<CommandSender, BukkitTask> autoUnlock = new HashMap<>();

	protected GangLeaveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                           UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                           RankManager rankManager) {
		super(gangland, "leave", tree, parent);

		this.gangland      = gangland;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;
	}

	/**
	 * DoubleArgument-style override: first invocation runs the pre-checks, locks the sender, and prints the confirm
	 * hint; second invocation (within {@link #CONFIRM_WINDOW_TICKS}) unlocks and falls through to {@link #action()}.
	 * Replaces the previous ConfirmArgument approach, which depended on a {@code /glw gang leave confirm} sub-argument
	 * that was never registered in the argument tree.
	 */
	@Override
	public void executeArgument(CommandSender sender, String[] args) {
		if (!preChecks(sender)) return;

		if (!lock.isLocked(sender)) {
			sender.sendMessage(Messages.ARGUMENT_CONFIRM_HINT.toString());
			lock.lock(sender);
			autoUnlock.put(sender, Bukkit.getScheduler().runTaskLater(gangland, () -> {
				lock.unlock(sender);
				autoUnlock.remove(sender);
			}, CONFIRM_WINDOW_TICKS));
			return;
		}

		lock.unlock(sender);
		BukkitTask task = autoUnlock.remove(sender);
		if (task != null) task.cancel();

		super.executeArgument(sender, args);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
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

			// Re-verify ownership — between hint and confirm the player may have been promoted to owner.
			if (member.getRank() == null) return;

			Rank tail = rankManager.get(Settings.getGangRankTail());
			if (tail == null) return;

			if (member.getRank().match(tail.getUsedId())) {
				user.sendMessage(Messages.GANG_TRANSFER_OWNERSHIP.toString());
				return;
			}

			// anyone leaving will not get a piece of the pie, thus the contribution would not be counted
			gang.removeMember(user, member);
			user.sendMessage(Messages.GANG_LEAVE.toString());

			// Notify the remaining online members. removeMember has already nulled the leaver's gang link, so
			// getOnlineMembers naturally excludes them from the broadcast.
			String broadcast = Messages.GANG_PLAYER_LEFT.toString().replace("%player%", player.getName());
			for (User<Player> onUser : gang.getOnlineMembers(userManager::getUser)) {
				onUser.sendMessage(broadcast);
			}
		};
	}

	private boolean preChecks(CommandSender sender) {
		if (!(sender instanceof Player player)) return false;

		User<Player> user = userManager.getUser(player);
		if (user == null) return false;

		Member member = memberManager.getMember(player.getUniqueId());

		if (!user.hasGang()) {
			user.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return false;
		}

		if (member.getRank() == null) return false;

		Rank tail = rankManager.get(Settings.getGangRankTail());
		if (tail == null) return false;

		if (member.getRank().match(tail.getUsedId())) {
			user.sendMessage(Messages.GANG_TRANSFER_OWNERSHIP.toString());
			return false;
		}

		return true;
	}

}
