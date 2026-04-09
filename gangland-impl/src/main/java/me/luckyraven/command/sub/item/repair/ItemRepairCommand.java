package me.luckyraven.command.sub.item.repair;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.repair.RepairManager;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ItemRepairCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final RepairManager       repairManager;

	public ItemRepairCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                         UserManager<Player> userManager,
	                         RepairManager repairManager) {
		super(gangland, "repair", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.repairManager = repairManager;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<give/list/info>"));
	}

	private void initializeArguments() {
		Argument give = new ItemRepairGiveCommand(gangland, tree, this, userManager, repairManager);
		Argument info = new ItemRepairInfoCommand(gangland, tree, this, userManager, repairManager);
		Argument list = new ItemRepairListCommand(gangland, tree, this, repairManager);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		this.addAllSubArguments(arguments);
	}

}
