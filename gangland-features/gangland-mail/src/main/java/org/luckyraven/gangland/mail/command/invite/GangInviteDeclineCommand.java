package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.command.OnlineGangMembers;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /glw gang decline [gang]} (GR-24). Mirrors {@code /glw gang accept}: with no argument it declines the only
 * pending invite (and asks for the gang when there are several); with a gang it declines that gang's invite. The
 * inviting gang's online members are told.
 */
class GangInviteDeclineCommand extends SubArgument {

	private final JavaPlugin            plugin;
	private final Tree<Argument>        tree;
	private final UserManager<Player>   userManager;
	private final MemberManager         memberManager;
	private final GangManager           gangManager;
	private final MailManager           mailManager;
	private final MailMessages          messages;

	GangInviteDeclineCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                         @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager,
	                         GangManager gangManager, MailManager mailManager, MailMessages messages) {
		super(plugin, "decline", tree, parent);

		this.plugin        = plugin;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.mailManager   = mailManager;
		this.messages      = messages;

		this.addSubArgument(senderGangArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			List<MailItem> pending = mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE);
			if (pending.isEmpty()) {
				user.sendMessage(messages.inviteDeclineNone());
				return;
			}

			if (pending.size() > 1) {
				user.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
				return;
			}

			decline(user, pending.get(0));
		};
	}

	/**
	 * Builds the disambiguated gang-name → gang-id map of the gangs with a pending invite for the sender.
	 */
	private Map<String, String> buildPendingSenderMap(CommandSender sender) {
		Map<String, String> map = new HashMap<>();
		if (!(sender instanceof Player player)) return map;

		List<Gang> senders = new ArrayList<>();
		for (MailItem mail : mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE)) {
			Gang gang = gangManager.getGang(mail.getSenderGangId());
			if (gang != null) senders.add(gang);
		}

		// Same-named gangs get name:id so each stays selectable (mirrors accept).
		Map<String, Integer> nameCount = new HashMap<>();
		for (Gang gang : senders) nameCount.merge(gang.getName(), 1, Integer::sum);

		for (Gang gang : senders) {
			String displayName = nameCount.get(gang.getName()) > 1 ? gang.getName() + ":" + gang.getId() : gang.getName();
			map.put(displayName, String.valueOf(gang.getId()));
		}
		return map;
	}

	private OptionalArgument senderGangArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String value = optionalArgument.getActualValue(args[2], sender);

			int senderId;
			try {
				senderId = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				// Not a sender id: a gang name with no pending invite from it (the gang token is free text).
				user.sendMessage(messages.inviteDeclineNone());
				return;
			}

			MailItem match = null;
			for (MailItem mail : mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE)) {
				if (mail.getSenderGangId() == senderId) {
					match = mail;
					break;
				}
			}

			if (match == null) {
				user.sendMessage(messages.inviteDeclineNone());
				return;
			}

			decline(user, match);
		}, sender -> new ArrayList<>(buildPendingSenderMap(sender).keySet()), this::buildPendingSenderMap);
	}

	private void decline(User<Player> user, MailItem mail) {
		Gang gang = gangManager.getGang(mail.getSenderGangId());
		if (gang == null) {
			// The sending gang is gone: drop the stale invite, as accept does.
			mailManager.cancel(mail);
			user.sendMessage(Messages.NO_GANG_INVITATION.toString());
			return;
		}

		mailManager.reject(mail);
		user.sendMessage(messages.inviteDeclined(gang.getDisplayNameString()));

		for (Player member : OnlineGangMembers.of(memberManager, gang.getId())) {
			member.sendMessage(messages.inviteDeclinedNotice(user.getUser().getName()));
		}
	}

}
