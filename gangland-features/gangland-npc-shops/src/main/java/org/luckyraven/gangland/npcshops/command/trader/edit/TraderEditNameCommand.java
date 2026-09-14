package org.luckyraven.gangland.npcshops.command.trader.edit;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

import java.util.Collections;
import java.util.List;

class TraderEditNameCommand extends SubArgument {

	private static final double TARGET_RANGE = 5D;

	private final JavaPlugin      plugin;
	private final TraderManager traderManager;

	protected TraderEditNameCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                                TraderManager traderManager) {
		super(plugin, "name", tree, parent);
		this.plugin      = plugin;
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
				.plugin(plugin)
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
