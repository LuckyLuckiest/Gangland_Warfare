package me.luckyraven.command.sub.gang.invite;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.mail.MailItem;
import me.luckyraven.mail.MailManager;
import me.luckyraven.mail.MailStatus;
import me.luckyraven.mail.MailType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GangInviteCommand extends SubArgument {

	private static final long INVITE_EXPIRY_MS = 60_000L;

	private final Gangland                   gangland;
	private final Tree<Argument>             tree;
	private final Argument                   gangArgumentParent;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RankManager                rankManager;
	private final MailManager                mailManager;

	public GangInviteCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                         UserManager<OfflinePlayer> offlineUserManager, MemberManager memberManager,
	                         GangManager gangManager, RankManager rankManager, MailManager mailManager) {
		super(gangland, new String[]{"invite", "add"}, tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.gangArgumentParent = parent;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;
		this.mailManager        = mailManager;

		// Add the literal `cancel` SubArgument before the `<player>` OptionalArgument so the argument tree resolves
		// `/glw gang invite cancel` to the cancel branch instead of treating "cancel" as a target player name.
		this.addSubArgument(
				new GangInviteCancelCommand(gangland, tree, this, userManager, offlineUserManager, mailManager));
		this.addSubArgument(invitePlayerArgument());
	}

	/**
	 * Builds the {@code /glw gang accept} argument. It lives as a sibling of {@code /glw gang invite} (not as
	 * {@code /glw gang invite accept}) so it's produced here and the parent {@code GangCommand} registers it next to
	 * this one in the argument tree.
	 */
	public Argument gangAccept() {
		return new GangInviteAcceptCommand(gangland, tree, gangArgumentParent, userManager, memberManager, gangManager,
		                                   rankManager, mailManager);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		// `/glw gang invite` (no extra token) prints the sender gang's outgoing pending invites — moved off the
		// "Arguments missing" fallback per the user's request.
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			List<MailItem> outgoing = mailManager.findPendingFromSenderGang(user.getGangId(), MailType.GANG_INVITE);
			if (outgoing.isEmpty()) {
				sender.sendMessage(Messages.GANG_INVITE_PENDING_NONE.toString());
				return;
			}

			String string  = Messages.GANG_INVITE_PENDING_HEADER.toString();
			String replace = string.replace("%count%", String.valueOf(outgoing.size()));
			sender.sendMessage(ChatUtil.color(replace));

			for (MailItem mail : outgoing) {
				String name = "?";
				if (mail.getRecipientUuid() != null) {
					OfflinePlayer p = Bukkit.getOfflinePlayer(mail.getRecipientUuid());
					if (p.getName() != null) name = p.getName();
				}
				sender.sendMessage(Messages.GANG_INVITE_PENDING_ENTRY.toString().replace("%player%", name));
			}
		};
	}

	private OptionalArgument invitePlayerArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			String  targetStr      = args[2];
			User<?> resolvedTarget = NameLookup.findByName(targetStr, userManager, offlineUserManager);

			if (resolvedTarget == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			if (resolvedTarget.hasGang()) {
				sender.sendMessage(Messages.TARGET_IN_GANG.toString().replace("%player%", targetStr));
				return;
			}

			UUID targetUuid = resolvedTarget.getUser().getUniqueId();
			Gang gang       = gangManager.getGang(user.getGangId());

			// Reject duplicate pending invites from this same gang to the same player.
			if (mailManager.findPendingByGangAndRecipient(user.getGangId(), targetUuid, MailType.GANG_INVITE)
			               .isPresent()) {
				sender.sendMessage(Messages.GANG_INVITE_ALREADY_SENT.toString().replace("%player%", targetStr));
				return;
			}

			long now      = System.currentTimeMillis();
			long expireAt = now + INVITE_EXPIRY_MS;
			MailItem mail = new MailItem(mailManager.allocateId(), MailType.GANG_INVITE, null, user.getGangId(),
			                             targetUuid, MailItem.NO_GANG, null, now, expireAt, MailStatus.PENDING, false);
			mailManager.send(mail);

			user.sendMessage(Messages.GANG_INVITE_PLAYER.toString().replace("%player%", targetStr));

			Player onlineTarget = Bukkit.getPlayer(targetUuid);
			if (onlineTarget != null) {
				String replace = Messages.GANG_INVITE_TARGET.toString().replace("%gang%", gang.getDisplayNameString());
				onlineTarget.sendMessage(replace);
			}
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;
			if (!user.hasGang()) return null;

			// Tab-completion draws from both caches so an admin can invite a player who isn't online right now.
			// Offline names are deduped against online to avoid double entries when both caches hold the same UUID.
			List<String> possibleUsers = new ArrayList<>();

			for (User<Player> onlineUser : userManager.getUsers().values()) {
				Player onlinePlayer = onlineUser.getUser();
				if (onlinePlayer == null || onlineUser.hasGang()) continue;
				possibleUsers.add(onlinePlayer.getName());
			}

			for (User<OfflinePlayer> offlineUser : offlineUserManager.getUsers().values()) {
				OfflinePlayer offlinePlayer = offlineUser.getUser();
				if (offlinePlayer == null || offlineUser.hasGang()) continue;
				String name = offlinePlayer.getName();
				if (name == null || possibleUsers.contains(name)) continue;
				possibleUsers.add(name);
			}

			return possibleUsers.isEmpty() ? null : possibleUsers;
		});
	}

}
