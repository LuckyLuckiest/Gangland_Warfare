package org.luckyraven.gangland.command.sub.turf;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

class TurfWandCommand extends SubArgument {

	private final WandSelectionManager selections;

	protected TurfWandCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          WandSelectionManager selections) {
		super(gangland, "wand", tree, parent);

		this.selections = selections;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			Material material = resolveMaterial(Settings.getTurfWandItemType());
			ItemStack wand = new ItemBuilder(material)
					.setDisplayName(ChatUtil.color("&6Turf Wand"))
					.setLore(ChatUtil.color("&7Left-click = pos1, right-click = pos2."))
					.addTag(WandSelectionManager.WAND_NBT_KEY, true)
					.build();
			player.getInventory().addItem(wand);
			player.sendMessage(Messages.TURF_WAND_GIVEN.toString());
			selections.get(player);
		};
	}

	private Material resolveMaterial(String name) {
		return XMaterial.matchXMaterial(name)
		                .orElse(XMaterial.BLAZE_ROD)
		                .get();
	}
}
