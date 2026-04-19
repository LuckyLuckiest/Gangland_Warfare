package me.luckyraven.command.sub.trader;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

class TraderEditTraitCommand extends SubArgument {

	private static final double TARGET_RANGE = 5D;

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final TraderManager       traderManager;
	private final TraderTraitRegistry traitRegistry;

	protected TraderEditTraitCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                 TraderManager traderManager, TraderTraitRegistry traitRegistry) {
		super(gangland, "trait", tree, parent);
		this.gangland      = gangland;
		this.tree          = tree;
		this.traderManager = traderManager;
		this.traitRegistry = traitRegistry;

		registerValueArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<traitId>"));
	}

	private void registerValueArgument() {
		Argument valueArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			String traitId = args[3].toLowerCase();
			if (!traitRegistry.exists(traitId)) {
				player.sendMessage(Messages.TRADER_TRAIT_MISSING.toString()
				                                                .replace("%trait%", traitId)
				                                                .replace("%available%",
				                                                         String.join(", ", traitRegistry.ids())));
				return;
			}

			TraderNpc trader = traderManager.findTargetedTrader(player, TARGET_RANGE);
			if (trader == null) {
				player.sendMessage(Messages.TRADER_LOOK_AT.toString()
				                                          .replace("%range%", String.valueOf((int) TARGET_RANGE)));
				return;
			}

			if (traderManager.retargetTrait(trader.getData().getId(), traitId)) {
				player.sendMessage(Messages.TRADER_TRAIT_CHANGED.toString().replace("%trait%", traitId));
			}
		}, sender -> new ArrayList<>(traitRegistry.ids()));

		this.addSubArgument(valueArg);
	}

}
