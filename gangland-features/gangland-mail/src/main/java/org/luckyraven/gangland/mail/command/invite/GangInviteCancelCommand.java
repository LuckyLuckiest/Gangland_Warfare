package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class GangInviteCancelCommand extends SubArgument {

	private final Gangland                   gangland;
	private final Tree<Argument>             tree;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MailManager                mailManager;

	GangInviteCancelCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                        UserManager<OfflinePlayer> offlineUserManager, MailManager mailManager) {
		super(gangland, "cancel", tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.mailManager        = mailManager;

		this.addSubArgument(targetPlayerArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		// `/glw gang invite cancel` (no extra token) — auto-cancel the oldest outgoing invite, warn if there were more.
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

			MailItem mail       = outgoing.get(0);
			String   targetName = resolveTargetName(mail);

			if (outgoing.size() > 1) {
				user.sendMessage(Messages.GANG_INVITE_CANCEL_MULTIPLE.toString()
				                                                     .replace("%count%",
				                                                              String.valueOf(outgoing.size()))
				                                                     .replace("%player%", targetName));
			}

			doCancel(user, mail, targetName);
		};
	}

	private OptionalArgument targetPlayerArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			String  targetName     = args[3];
			User<?> resolvedTarget = NameLookup.findByName(targetName, userManager, offlineUserManager);

			if (resolvedTarget == null) {
				user.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetName));
				return;
			}

			UUID targetUuid = resolvedTarget.getUser().getUniqueId();

			Optional<MailItem> pending = mailManager.findPendingByGangAndRecipient(user.getGangId(), targetUuid,
			                                                                       MailType.GANG_INVITE);
			if (pending.isEmpty()) {
				user.sendMessage(Messages.GANG_INVITE_CANCEL_NONE.toString().replace("%player%", targetName));
				return;
			}

			doCancel(user, pending.get(), targetName);
		}, sender -> {
			if (!(sender instanceof Player player)) return new ArrayList<>();
			User<Player> user = userManager.getUser(player);
			if (user == null || !user.hasGang()) return new ArrayList<>();

			List<MailItem> pending = mailManager.findPendingFromSenderGang(user.getGangId(), MailType.GANG_INVITE);
			List<String>   names   = new ArrayList<>();
			for (MailItem mail : pending) {
				String name = resolveTargetName(mail);
				if (!"?".equals(name)) names.add(name);
			}
			return names;
		});
	}

	private void doCancel(User<Player> user, MailItem mail, String targetName) {
		mailManager.cancel(mail);
		user.sendMessage(Messages.GANG_INVITE_CANCEL_SENDER.toString().replace("%player%", targetName));

		if (mail.getRecipientUuid() != null) {
			Player onlineTarget = Bukkit.getPlayer(mail.getRecipientUuid());
			if (onlineTarget != null) {
				onlineTarget.sendMessage(Messages.GANG_INVITE_CANCEL_TARGET.toString());
			}
		}
	}

	/**
	 * Resolves a recipient name from a stored mail. Lookup goes online users → offline users (both DB-backed); the
	 * non-deprecated {@code Bukkit.getOfflinePlayer(UUID)} is used as a final fallback for UUIDs not currently in
	 * either cache (e.g. an invite saved before a recent {@code offlineUserManager.clear()}).
	 */
	private String resolveTargetName(MailItem mail) {
		UUID uuid = mail.getRecipientUuid();
		if (uuid == null) return "?";

		for (User<Player> u : userManager.getUsers().values()) {
			Player p = u.getUser();
			if (p != null && uuid.equals(p.getUniqueId())) {
				p.getName();
				return p.getName();
			}
		}

		for (User<OfflinePlayer> u : offlineUserManager.getUsers().values()) {
			OfflinePlayer p = u.getUser();
			if (p != null && uuid.equals(p.getUniqueId()) && p.getName() != null) return p.getName();
		}

		OfflinePlayer fallback = Bukkit.getOfflinePlayer(uuid);
		return fallback.getName() != null ? fallback.getName() : "?";
	}

}
