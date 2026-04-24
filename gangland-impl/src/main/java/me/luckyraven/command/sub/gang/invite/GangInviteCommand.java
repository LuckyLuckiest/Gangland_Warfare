package me.luckyraven.command.sub.gang.invite;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.timer.CountdownTimer;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class GangInviteCommand extends SubArgument {

	private final Gangland                              gangland;
	private final Tree<Argument>                        tree;
	private final Argument                              gangArgumentParent;
	private final UserManager<Player>                   userManager;
	private final MemberManager                         memberManager;
	private final GangManager                           gangManager;
	private final RankManager                           rankManager;
	private final HashMap<User<Player>, Gang>           playerInvite;
	private final HashMap<User<Player>, CountdownTimer> inviteTimer;

	public GangInviteCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                         UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                         RankManager rankManager) {
		super(gangland, new String[]{"invite", "add"}, tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.gangArgumentParent = parent;
		this.userManager        = userManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;

		this.playerInvite = new HashMap<>();
		this.inviteTimer  = new HashMap<>();

		gangInvite();
	}

	/**
	 * Builds the {@code /glw gang accept} argument. It lives as a sibling of {@code /glw gang invite} (not as
	 * {@code /glw gang invite accept}) so it's produced here and the parent {@code GangCommand} registers it next to
	 * this one in the argument tree.
	 */
	public Argument gangAccept() {
		return new GangInviteAcceptCommand(gangland, tree, gangArgumentParent, userManager, memberManager,
		                                   gangManager, rankManager, playerInvite, inviteTimer);
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

	private void gangInvite() {
		Argument inviteName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			String targetStr = args[2];
			Player target    = Bukkit.getPlayer(targetStr);

			if (target == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Gang         gang       = gangManager.getGang(user.getGangId());
			User<Player> targetUser = userManager.getUser(target);

			if (targetUser == null) return;

			if (targetUser.hasGang()) {
				sender.sendMessage(Messages.TARGET_IN_GANG.toString().replace("%player%", targetStr));
				return;
			}

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				user.sendMessage(Messages.GANG_INVITE_PLAYER.toString().replace("%player%", targetStr));
				String replace = Messages.GANG_INVITE_TARGET.toString()
				                                            .replace("%gang%", gang.getDisplayNameString());
				targetUser.sendMessage(replace);
			}, null, time -> {
				playerInvite.remove(targetUser);
				inviteTimer.remove(targetUser);
			});

			timer.start(false);

			playerInvite.put(targetUser, gang);
			inviteTimer.put(targetUser, timer);
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) return null;

			List<String> possibleUsers = new ArrayList<>();

			Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

			for (Player onlinePlayer : onlinePlayers) {
				User<Player> onlineUser = userManager.getUser(onlinePlayer);

				if (onlineUser == null || onlineUser.hasGang()) continue;

				possibleUsers.add(onlinePlayer.getName());
			}

			if (possibleUsers.isEmpty()) return null;

			return possibleUsers;
		});

		this.addSubArgument(inviteName);
	}

}
