package org.luckyraven.gangland.sign.aspect;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;
import org.luckyraven.gangland.sign.extension.SignContributions;
import org.luckyraven.gangland.sign.model.ParsedSign;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ViewInventoryAspect implements SignAspect {

	private final JavaPlugin        plugin;
	private final SignContributions contributions;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		String itemName = sign.getContent();

		if (contributions.openView(player, itemName)) {
			return AspectResult.success("Opened view: " + itemName);
		}

		openGenericItemView(player, itemName);
		return AspectResult.success("Opened item view: " + itemName);
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		return !sign.getContent().isEmpty();
	}

	@Override
	public String getName() {
		return "ViewInventoryAspect";
	}

	private void openGenericItemView(Player player, String itemName) {
		String title = "&6View: &e" + itemName;

		InventoryHandler inventory = new InventoryHandler(plugin, title, 9, player);

		// Try to create item from material name
		Material material = Material.matchMaterial(itemName.toUpperCase().replace(" ", "_"));
		if (material == null) {
			material = Material.BARRIER;
		}

		List<String> lore = new ArrayList<>();
		lore.add("&7Item: &f" + itemName);
		lore.add("");
		lore.add("&cThis item is not configured");

		inventory.setItem(4, material, "&e" + itemName, lore, false, false, null);

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		inventory.open(player);
	}

}
