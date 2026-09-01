package org.luckyraven.gangland.command.sub.gang.ally;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class GangAllyCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final MailManager         mailManager;

	public GangAllyCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       MemberManager memberManager, GangManager gangManager, MailManager mailManager) {
		super(gangland, "ally", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.mailManager   = mailManager;

		initializeArguments();
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

			sender.sendMessage(
					GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(),
					                              "<request/abandon/accept/reject/pending>"));
		};
	}

	private void initializeArguments() {
		GangAllyRequestCommand request = new GangAllyRequestCommand(gangland, tree, this, userManager, memberManager,
		                                                            gangManager, mailManager);
		GangAllyAbandonCommand abandon = new GangAllyAbandonCommand(gangland, tree, this, userManager, memberManager,
		                                                            gangManager);
		GangAllyAcceptCommand accept = new GangAllyAcceptCommand(gangland, tree, this, userManager, memberManager,
		                                                         gangManager, mailManager);
		GangAllyRejectCommand reject = new GangAllyRejectCommand(gangland, tree, this, userManager, memberManager,
		                                                         gangManager, mailManager);
		GangAllyPendingCommand pending = new GangAllyPendingCommand(gangland, tree, this, userManager, gangManager,
		                                                            mailManager);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(request);
		arguments.add(abandon);
		arguments.add(accept);
		arguments.add(reject);
		arguments.add(pending);

		this.addAllSubArguments(arguments);
	}

}
