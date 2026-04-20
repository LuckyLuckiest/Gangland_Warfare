package me.luckyraven.command.sub.banker;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.banker.BankerManager;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

class BankerEditNameCommand extends SubArgument {

	private static final double TARGET_RANGE = 5D;

	private final Gangland      gangland;
	private final BankerManager bankerManager;

	protected BankerEditNameCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                BankerManager bankerManager) {
		super(gangland, "name", tree, parent);
		this.gangland      = gangland;
		this.bankerManager = bankerManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			BankerNpc banker = bankerManager.findTargetedBanker(player, TARGET_RANGE);
			if (banker == null) {
				player.sendMessage(Messages.BANKER_LOOK_AT.toString()
				                                          .replace("%range%", String.valueOf((int) TARGET_RANGE)));
				return;
			}

			openNameAnvil(player, banker);
		};
	}

	private void openNameAnvil(Player admin, BankerNpc banker) {
		String current = banker.getData().getDisplayName();

		new AnvilGUI.Builder()
				.plugin(gangland)
				.title("Banker Name")
				.itemLeft(new ItemStack(Material.NAME_TAG))
				.text(current == null ? "" : current)
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

					String text = state.getText() == null ? "" : state.getText();
					if (text.isBlank()) {
						admin.sendMessage(Messages.BANKER_NAME_EMPTY.toString());
						return Collections.emptyList();
					}

					if (bankerManager.rename(banker.getData().getId(), text)) {
						admin.sendMessage(Messages.BANKER_RENAMED.toString().replace("%name%", text));
					}
					return List.of(AnvilGUI.ResponseAction.close());
				})
				.open(admin);
	}

}
