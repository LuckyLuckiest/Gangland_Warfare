package org.luckyraven.gangland.mail.command;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.command.ally.GangAllyAcceptCommand;
import org.luckyraven.gangland.mail.command.ally.GangAllyPendingCommand;
import org.luckyraven.gangland.mail.command.ally.GangAllyRejectCommand;
import org.luckyraven.gangland.mail.command.ally.GangAllyRequestCommand;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Attaches the mail-backed alliance flow - {@code request}, {@code accept}, {@code reject}, {@code pending}
 * ({@code + cancel}) - under the core's {@code /glw gang ally} argument. {@code abandon} is not mail and stays in
 * the core.
 */
public final class GangAllyMailContribution implements CommandContribution {

	public static final String PARENT = "gang.ally";

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final MailManager         mailManager;

	public GangAllyMailContribution(Gangland gangland, UserManager<Player> userManager, MemberManager memberManager,
	                                GangManager gangManager, MailManager mailManager) {
		this.gangland      = gangland;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.mailManager   = mailManager;
	}

	@Override
	public String parent() {
		return PARENT;
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(
				new GangAllyRequestCommand(gangland, tree, parent, userManager, memberManager, gangManager, mailManager),
				new GangAllyAcceptCommand(gangland, tree, parent, userManager, memberManager, gangManager, mailManager),
				new GangAllyRejectCommand(gangland, tree, parent, userManager, memberManager, gangManager, mailManager),
				new GangAllyPendingCommand(gangland, tree, parent, userManager, gangManager, mailManager));
	}
}
