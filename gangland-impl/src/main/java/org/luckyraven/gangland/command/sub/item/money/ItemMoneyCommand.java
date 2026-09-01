package org.luckyraven.gangland.command.sub.item.money;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemMoneyCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService moneyDepositService;

	public ItemMoneyCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                        UserManager<Player> userManager,
	                        MoneyAddon moneyAddon,
	                        MoneyDepositService moneyDepositService) {
		super(gangland, "money", tree, parent);

		this.gangland            = gangland;
		this.tree                = tree;
		this.userManager         = userManager;
		this.moneyAddon          = moneyAddon;
		this.moneyDepositService = moneyDepositService;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<give/list/info>"));
	}

	private void initializeArguments() {
		Argument give = new ItemMoneyGiveCommand(gangland, tree, this, userManager, moneyAddon, moneyDepositService);
		Argument info = new ItemMoneyInfoCommand(gangland, tree, this, userManager, moneyAddon);
		Argument list = new ItemMoneyListCommand(gangland, tree, this, moneyAddon);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		this.addAllSubArguments(arguments);
	}

}
