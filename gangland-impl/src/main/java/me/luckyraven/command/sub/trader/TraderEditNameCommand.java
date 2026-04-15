package me.luckyraven.command.sub.trader;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
				player.sendMessage(GanglandChatUtil.commandMessage("&cLook at a trader within 5 blocks."));
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
						admin.sendMessage(GanglandChatUtil.commandMessage("&cName cannot be empty."));
						return Collections.emptyList();
					}

					if (traderManager.rename(trader.getData().getId(), text)) {
						admin.sendMessage(GanglandChatUtil.commandMessage(
								"&aTrader renamed to &f" + text + "&a."));
					}
					return List.of(AnvilGUI.ResponseAction.close());
				})
				.open(admin);
	}

}
