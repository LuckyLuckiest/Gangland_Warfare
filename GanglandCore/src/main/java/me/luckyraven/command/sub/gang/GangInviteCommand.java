package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
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
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

class GangInviteCommand extends SubArgument {

	private final Gangland                              gangland;
	private final Tree<Argument>                        tree;
	private final UserManager<Player>                   userManager;
	private final MemberManager                         memberManager;
	private final GangManager                           gangManager;
	private final RankManager                           rankManager;
	private final HashMap<User<Player>, Gang>           playerInvite;
	private final HashMap<User<Player>, CountdownTimer> inviteTimer;

	protected GangInviteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(new String[]{"invite", "add"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
		this.rankManager   = gangland.getInitializer().getRankManager();

		this.playerInvite = new HashMap<>();
		this.inviteTimer  = new HashMap<>();

		gangInvite();
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

	protected Argument gangAccept() {
		return new Argument("accept", tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!playerInvite.containsKey(user)) {
				player.sendMessage(MessageAddon.NO_GANG_INVITATION.toString());
				return;
			}

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
				return;
			}

			Gang   gang   = playerInvite.get(user);
			Member member = memberManager.getMember(player.getUniqueId());
			Rank   rank   = rankManager.get(SettingAddon.getGangRankHead());

			// broadcast in gang join of the player
			// don't broadcast to the joined member
			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);
			for (User<Player> onUser : gangOnlineMembers)
				onUser.getUser()
					  .sendMessage(
							  MessageAddon.GANG_PLAYER_JOINED.toString().replace("%player%", user.getUser().getName()));

			member.setGangJoinDateLong(Instant.now().toEpochMilli());
			gang.addMember(user, member, rank);
			sender.sendMessage(
					MessageAddon.GANG_INVITE_ACCEPT.toString().replace("%gang%", gang.getDisplayNameString()));

			playerInvite.remove(user);

			CountdownTimer timer = inviteTimer.get(user);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				inviteTimer.remove(user);
			}
		}, getPermission() + ".accept");
	}

	private void gangInvite() {
		Argument inviteName = new OptionalArgument(tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			String targetStr = args[2];
			Player target    = Bukkit.getPlayer(targetStr);

			if (target == null) {
				sender.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Gang         gang       = gangManager.getGang(user.getGangId());
			User<Player> targetUser = userManager.getUser(target);

			if (targetUser.hasGang()) {
				sender.sendMessage(MessageAddon.TARGET_IN_GANG.toString().replace("%player%", targetStr));
				return;
			}

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				player.sendMessage(MessageAddon.GANG_INVITE_PLAYER.toString().replace("%player%", targetStr));
				target.sendMessage(
						MessageAddon.GANG_INVITE_TARGET.toString().replace("%gang%", gang.getDisplayNameString()));
			}, null, time -> {
				playerInvite.remove(targetUser);
				inviteTimer.remove(targetUser);
			});

			timer.start(false);

			playerInvite.put(targetUser, gang);
			inviteTimer.put(targetUser, timer);
		});

		this.addSubArgument(inviteName);
	}

}
