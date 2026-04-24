package me.luckyraven.command.sub.turf;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.turf.selection.WandSelectionManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
