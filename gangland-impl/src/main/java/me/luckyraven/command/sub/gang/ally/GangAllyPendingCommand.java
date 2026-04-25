package me.luckyraven.command.sub.gang.ally;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.mail.MailItem;
import me.luckyraven.mail.MailManager;
import me.luckyraven.mail.MailType;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class GangAllyPendingCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final MailManager         mailManager;

	GangAllyPendingCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
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
