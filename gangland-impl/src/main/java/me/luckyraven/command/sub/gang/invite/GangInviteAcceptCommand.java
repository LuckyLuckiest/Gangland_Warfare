package me.luckyraven.command.sub.gang.invite;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.timer.CountdownTimer;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

class GangInviteAcceptCommand extends SubArgument {

	private final UserManager<Player>                   userManager;
	private final MemberManager                         memberManager;
	@SuppressWarnings("unused") // kept for constructor symmetry with other gang leaves; used once mail lands
	private final GangManager                           gangManager;
	private final RankManager                           rankManager;
	private final HashMap<User<Player>, Gang>           playerInvite;
	private final HashMap<User<Player>, CountdownTimer> inviteTimer;

	GangInviteAcceptCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                        MemberManager memberManager, GangManager gangManager, RankManager rankManager,
	                        HashMap<User<Player>, Gang> playerInvite,
	                        HashMap<User<Player>, CountdownTimer> inviteTimer) {
		super(gangland, "accept", tree, parent);

		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;
		this.playerInvite  = playerInvite;
		this.inviteTimer   = inviteTimer;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!playerInvite.containsKey(user)) {
				user.sendMessage(Messages.NO_GANG_INVITATION.toString());
				return;
			}

			if (user.hasGang()) {
				user.sendMessage(Messages.PLAYER_IN_GANG.toString());
				return;
			}

			Gang   gang   = playerInvite.get(user);
			Member member = memberManager.getMember(player.getUniqueId());
			Rank   rank   = rankManager.get(Settings.getGangRankHead());

			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager::getUser);
			for (User<Player> onUser : gangOnlineMembers) {
				String playerJoined = Messages.GANG_PLAYER_JOINED.toString()
				                                                 .replace("%player%", user.getUser().getName());

				onUser.sendMessage(playerJoined);
			}

			member.setGangJoinDateLong(Instant.now().toEpochMilli());
			gang.addMember(user, member, rank);
			sender.sendMessage(
					Messages.GANG_INVITE_ACCEPT.toString().replace("%gang%", gang.getDisplayNameString()));

			playerInvite.remove(user);

			CountdownTimer timer = inviteTimer.get(user);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				inviteTimer.remove(user);
			}
		};
	}

}
