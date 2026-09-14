package org.luckyraven.gangland.npcshops.command.bank;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.npcshops.banker.view.BankerFlow;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Attaches {@code /glw bank menu} under the core's {@code bank} command. Registered as a bean by the module's
 * configuration; without the cops-n-crooks module this sub-argument simply does not exist.
 */
public final class BankMenuContribution implements CommandContribution {

	private final JavaPlugin   plugin;
	private final BankerFlow bankerFlow;

	public BankMenuContribution(JavaPlugin plugin, BankerFlow bankerFlow) {
		this.plugin   = plugin;
		this.bankerFlow = bankerFlow;
	}

	@Override
	public String parent() {
		return "bank";
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(new BankMenuCommand(plugin, tree, parent, bankerFlow));
	}
}
