package org.luckyraven.gangland.mail.command.invite;

import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luckyraven.keystone.bean.Qualifier;

class GangInviteAcceptCommand extends SubArgument {

	private final JavaPlugin            plugin;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;
	private final MailManager         mailManager;
	private final MailMessages        messages;
	private final GangMembershipService membershipService;

	GangInviteAcceptCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, @Qualifier("online") UserManager<Player> userManager,
	                        MemberManager memberManager, GangManager gangManager, RankManager rankManager,
	                        MailManager mailManager, MailMessages messages,
	                        GangMembershipService membershipService) {
		super(plugin, "accept", tree, parent);

		this.plugin      = plugin;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;
		this.mailManager   = mailManager;
		this.messages      = messages;
		this.membershipService = membershipService;

		this.addSubArgument(senderGangArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		// `/glw gang accept` (no extra token) — auto-pick the oldest pending invite, warn if there were more.
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (user.hasGang()) {
				user.sendMessage(Messages.PLAYER_IN_GANG.toString());
				return;
			}

			List<MailItem> pending = mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE);
			if (pending.isEmpty()) {
				user.sendMessage(Messages.NO_GANG_INVITATION.toString());
				return;
			}

			if (pending.size() > 1) {
				// Accepting cancels the other invites, so auto-picking one would take the choice away. Ask for the gang.
				user.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
				return;
			}

			MailItem mail = pending.get(0);
			Gang     gang = gangManager.getGang(mail.getSenderGangId());
			if (gang == null) {
				mailManager.cancel(mail);
				user.sendMessage(Messages.NO_GANG_INVITATION.toString());
				return;
			}

			doAccept(user, player, gang, mail);
		};
	}

	/**
	 * Builds the disambiguated display-name → gang-id map of gangs that have a pending invite waiting for the sender.
	 */
	private Map<String, String> buildPendingInviteSenderMap(CommandSender sender) {
		if (!(sender instanceof Player player)) return new HashMap<>();
		User<Player> user = userManager.getUser(player);
		if (user == null) return new HashMap<>();

		List<MailItem> pending = mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE);

		List<Gang> senders = new ArrayList<>();
		for (MailItem mail : pending) {
			Gang gang = gangManager.getGang(mail.getSenderGangId());
			if (gang != null) senders.add(gang);
		}

		Map<String, Integer> nameCount = new HashMap<>();
		for (Gang g : senders) nameCount.merge(g.getName(), 1, Integer::sum);

		Map<String, String> map = new HashMap<>();
		for (Gang g : senders) {
			String name        = g.getName();
			String displayName = nameCount.get(name) > 1 ? name + ":" + g.getId() : name;
			map.put(displayName, String.valueOf(g.getId()));
		}
		return map;
	}

	private OptionalArgument senderGangArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (user.hasGang()) {
				user.sendMessage(Messages.PLAYER_IN_GANG.toString());
				return;
			}

			String value = optionalArgument.getActualValue(args[2], sender);

			int senderId;
			try {
				senderId = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				// Not a sender id: a gang name with no pending invite from it (the gang token is free text).
				user.sendMessage(Messages.GANG_INVITE_NO_INVITE_FROM.toString().replace("%gang%", value));
				return;
			}

			MailItem match = null;
			for (MailItem mail : mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE)) {
				if (mail.getSenderGangId() == senderId) {
					match = mail;
					break;
				}
			}

			Gang gang = gangManager.getGang(senderId);
			if (match == null || gang == null) {
				String name = gang == null ? value : gang.getDisplayNameString();
				user.sendMessage(Messages.GANG_INVITE_NO_INVITE_FROM.toString().replace("%gang%", name));
				return;
			}

			doAccept(user, player, gang, match);
		}, sender -> new ArrayList<>(buildPendingInviteSenderMap(sender).keySet()), this::buildPendingInviteSenderMap);
	}

	private void doAccept(User<Player> user, Player player, Gang gang, MailItem mail) {
		// the one membership path applies the member cap, tells the gang and fires the join events
		GangMembershipService.Result result = membershipService.join(gang.getId(), player.getUniqueId(),
		                                                             GangMembershipService.JoinSource.INVITE);
		if (!result.ok()) {
			player.sendMessage(membershipService.text(result));
			return;
		}

		player.sendMessage(Messages.GANG_INVITE_ACCEPT.toString().replace("%gang%", gang.getDisplayNameString()));

		// GR-23: accepting cancels the rest of this player's gang invites; say so if there were any.
		long others = mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE)
		                         .stream()
		                         .filter(pending -> pending != mail)
		                         .count();
		mailManager.accept(mail);
		if (others > 0) user.sendMessage(messages.inviteSiblingsCancelled());
	}

}
