package org.luckyraven.gangland.command.sub.gang.ally;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.extension.CommandContributions;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw gang ally}. The core owns {@code abandon}; the request/accept/reject/pending flow is mail and arrives
 * through the {@code gang.ally} {@link CommandContributions} when the mail module is installed.
 */
public class GangAllyCommand extends SubArgument {

	public static final String CONTRIBUTION_PARENT = "gang.ally";

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final UserManager<Player>  userManager;
	private final MemberManager        memberManager;
	private final GangManager          gangManager;
	private final CommandContributions contributions;

	public GangAllyCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       MemberManager memberManager, GangManager gangManager, CommandContributions contributions) {
		super(gangland, "ally", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.contributions = contributions;

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

			String options = contributions.hasAny(CONTRIBUTION_PARENT)
			                 ? "<request/abandon/accept/reject/pending>"
			                 : "<abandon>";
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), options));
		};
	}

	private void initializeArguments() {
		GangAllyAbandonCommand abandon = new GangAllyAbandonCommand(gangland, tree, this, userManager, memberManager,
		                                                            gangManager);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(abandon);
		arguments.addAll(contributions.createFor(CONTRIBUTION_PARENT, tree, this));

		this.addAllSubArguments(arguments);
	}

}
