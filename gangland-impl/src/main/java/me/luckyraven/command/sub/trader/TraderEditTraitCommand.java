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
				player.sendMessage(GanglandChatUtil.commandMessage(
						"&cTrait '" + traitId + "' does not exist. Available: &f"
						+ String.join(", ", traitRegistry.ids())));
				return;
			}

			TraderNpc trader = traderManager.findTargetedTrader(player, TARGET_RANGE);
			if (trader == null) {
				player.sendMessage(GanglandChatUtil.commandMessage("&cLook at a trader within 5 blocks."));
				return;
			}

			if (traderManager.retargetTrait(trader.getData().getId(), traitId)) {
				player.sendMessage(GanglandChatUtil.commandMessage(
						"&aTrader's trait changed to &f" + traitId + "&a."));
			}
		}, sender -> new ArrayList<>(traitRegistry.ids()));

		this.addSubArgument(valueArg);
	}

}
