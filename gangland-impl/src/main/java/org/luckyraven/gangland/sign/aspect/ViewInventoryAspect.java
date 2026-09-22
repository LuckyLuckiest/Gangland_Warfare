package org.luckyraven.gangland.sign.aspect;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.gangland.sign.extension.SignContributions;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ViewInventoryAspect implements SignAspect {

	private final JavaPlugin        plugin;
	private final SignContributions contributions;
	private final InventoryService  inventoryService;

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

		// Try to create item from material name
		Material material = Material.matchMaterial(itemName.toUpperCase().replace(" ", "_"));
		if (material == null) {
			material = Material.BARRIER;
		}

		List<String> lore = new ArrayList<>();
		lore.add("&7Item: &f" + itemName);
		lore.add("");
		lore.add("&cThis item is not configured");

		ItemBuilder item = new ItemBuilder(material).setDisplayName("&e" + itemName).setLore(lore);

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title(title).rows(1);
		builder.slot(4, ItemComponent.of(item));
		builder.fill(FillComponent.of(materialOf(InventoryBuilder.DEFAULT_FILL_ITEM))
		                          .name(InventoryBuilder.DEFAULT_FILL_NAME));

		builder.build().open(player);
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

}
