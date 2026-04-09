package me.luckyraven.command.sub.item.money;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
