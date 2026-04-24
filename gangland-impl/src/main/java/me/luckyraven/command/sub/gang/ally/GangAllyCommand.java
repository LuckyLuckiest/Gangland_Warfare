package me.luckyraven.command.sub.gang.ally;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.timer.CountdownTimer;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GangAllyCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	// key -> the gang requesting alliance with, value -> the gang sending the request.
	// Shared across request / accept / reject. Retired in Phase 3 when the mail system lands.
	private final HashMap<Gang, Gang>           gangsIdMap       = new HashMap<>();
	private final HashMap<Gang, CountdownTimer> gangRequestTimer = new HashMap<>();

	public GangAllyCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       MemberManager memberManager, GangManager gangManager) {
		super(gangland, "ally", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;

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
					GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<request/abandon>"));
		};
	}

	private void initializeArguments() {
		GangAllyRequestCommand request = new GangAllyRequestCommand(gangland, tree, this, userManager, memberManager,
		                                                            gangManager, gangsIdMap, gangRequestTimer);
		GangAllyAbandonCommand abandon = new GangAllyAbandonCommand(gangland, tree, this, userManager, memberManager,
		                                                            gangManager);
		GangAllyAcceptCommand accept = new GangAllyAcceptCommand(gangland, tree, this, userManager, memberManager,
		                                                         gangManager, gangsIdMap, gangRequestTimer);
		GangAllyRejectCommand reject = new GangAllyRejectCommand(gangland, tree, this, userManager, memberManager,
		                                                         gangManager, gangsIdMap, gangRequestTimer);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(request);
		arguments.add(abandon);
		arguments.add(accept);
		arguments.add(reject);

		this.addAllSubArguments(arguments);
	}

}
