package org.luckyraven.gangland.copsncrooks.command.bank;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.copsncrooks.npc.banker.view.BankerFlow;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Attaches {@code /glw bank menu} under the core's {@code bank} command. Registered as a bean by the module's
 * configuration; without the cops-n-crooks module this sub-argument simply does not exist.
 */
public final class BankMenuContribution implements CommandContribution {

	private final Gangland   gangland;
	private final BankerFlow bankerFlow;

	public BankMenuContribution(Gangland gangland, BankerFlow bankerFlow) {
		this.gangland   = gangland;
		this.bankerFlow = bankerFlow;
	}

	@Override
	public String parent() {
		return "bank";
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(new BankMenuCommand(gangland, tree, parent, bankerFlow));
	}
}
