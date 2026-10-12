package org.luckyraven.gangland.turf.command;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

class TurfWandCommand extends SubArgument {

	private final WandSelectionManager selections;
	private final TurfSettings         settings;
	private final TurfMessages         text;

	protected TurfWandCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                          WandSelectionManager selections, TurfSettings settings,
	                          TurfMessages text) {
		super(plugin, "wand", tree, parent);

		this.selections = selections;
		this.settings   = settings;
		this.text       = text;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			// GI-35: nested SubArgument permissions only add onto the base "gangland.command.turf" node — they
			// never require the stricter admin one on their own, so the wand must gate on it explicitly.
			if (!TurfPermissions.requireAdmin(sender, text)) {
				return;
			}
			if (!(sender instanceof Player player)) {
				return;
			}
			Material material = resolveMaterial(settings.getWandItemType());
			ItemStack wand = new ItemBuilder(material)
					.setDisplayName(text.text("Turf.Wand_Name", "&6Turf Wand"))
					.setLore(text.text("Turf.Wand_Lore", "&7Left-click = pos1, right-click = pos2."))
					.addTag(WandSelectionManager.WAND_NBT_KEY, true)
					.build();
			player.getInventory().addItem(wand);
			player.sendMessage(Messages.TURF_WAND_GIVEN.toString());
			// A new wand starts a new selection: the old corners may sit in another world (TF-29).
			selections.clear(player.getUniqueId());
		};
	}

	private Material resolveMaterial(String name) {
		return XMaterial.matchXMaterial(name)
		                .orElse(XMaterial.BLAZE_ROD)
		                .get();
	}
}
