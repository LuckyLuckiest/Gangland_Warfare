package org.luckyraven.gangland.command.sub.trader.edit;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderManager;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

import java.util.Collections;
import java.util.List;

class TraderEditNameCommand extends SubArgument {

	private static final double TARGET_RANGE = 5D;

	private final Gangland      gangland;
	private final TraderManager traderManager;

	protected TraderEditNameCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                TraderManager traderManager) {
		super(gangland, "name", tree, parent);
		this.gangland      = gangland;
		this.traderManager = traderManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			TraderNpc trader = traderManager.findTargetedTrader(player, TARGET_RANGE);
			if (trader == null) {
				player.sendMessage(Messages.TRADER_LOOK_AT.toString()
				                                          .replace("%range%", String.valueOf((int) TARGET_RANGE)));
				return;
			}

			openNameAnvil(player, trader);
		};
	}

	private void openNameAnvil(Player admin, TraderNpc trader) {
		String current = trader.getData().getDisplayName();

		new AnvilGUI.Builder()
				.plugin(gangland)
				.title("Trader Name")
				.itemLeft(new ItemStack(Material.NAME_TAG))
				.text(current == null ? "" : current)
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

					String text = state.getText() == null ? "" : state.getText();
					if (text.isBlank()) {
						admin.sendMessage(Messages.TRADER_NAME_EMPTY.toString());
						return Collections.emptyList();
					}

					if (traderManager.rename(trader.getData().getId(), text)) {
						admin.sendMessage(Messages.TRADER_RENAMED.toString().replace("%name%", text));
					}
					return List.of(AnvilGUI.ResponseAction.close());
				})
				.open(admin);
	}

}
