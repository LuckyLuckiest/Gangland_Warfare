package org.luckyraven.gangland.npcshops.command.bank;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.npcshops.banker.view.BankerFlow;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

/**
 * Phone-banking entry point for the rich banker panel. Routed from {@code phone_banking.yml} as {@code /glw bank menu};
 * the command starts a {@link BankerFlow} with a {@code null} banker so the menu renders with the "Online Banking"
 * display fallback.
 */
public final class BankMenuCommand extends SubArgument {

	private final BankerFlow bankerFlow;

	public BankMenuCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, BankerFlow bankerFlow) {
		super(plugin, "menu", tree, parent);
		this.bankerFlow = bankerFlow;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;
			bankerFlow.startFromPhone(player);
		};
	}

}
