package org.luckyraven.gangland.mail.command.ally;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

public class GangAllyPendingCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final MailManager         mailManager;

	public GangAllyPendingCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       GangManager gangManager, MailManager mailManager) {
		super(gangland, "pending", tree, parent);

		this.userManager = userManager;
		this.gangManager = gangManager;
		this.mailManager = mailManager;

		this.addSubArgument(new GangAllyPendingCancelCommand(gangland, tree, this, userManager, gangManager,
		                                                     mailManager));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			List<MailItem> outgoing = mailManager.findPendingFromSenderGang(user.getGangId(),
			                                                                MailType.GANG_ALLY_REQUEST);
			if (outgoing.isEmpty()) {
				sender.sendMessage(Messages.GANG_ALLY_PENDING_NONE.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.color(
					Messages.GANG_ALLY_PENDING_HEADER.toString().replace("%count%", String.valueOf(outgoing.size()))));

			for (MailItem mail : outgoing) {
				Gang target = gangManager.getGang(mail.getRecipientGangId());
				String targetName = target == null ? String.valueOf(mail.getRecipientGangId())
				                                   : target.getDisplayNameString();
				sender.sendMessage(Messages.GANG_ALLY_PENDING_ENTRY.toString().replace("%gang%", targetName));
			}
		};
	}

}
