package me.luckyraven.bukkit;

import me.luckyraven.data.user.User;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InventoryGUI implements Listener {

	private static final List<InventoryGUI> inventoryGUIs = new ArrayList<>();
	private final        Inventory          inventory;
	private final        List<Integer>      draggableSlots;

	public InventoryGUI(String title, int size) {
		this.inventory = Bukkit.createInventory(null, size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		inventoryGUIs.add(this);
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable) {
		ItemBuilder item = new ItemBuilder(material);

		item.setDisplayName(displayName).setLore(lore);

		if (enchanted) item.addEnchantment(Enchantment.DURABILITY, 1);

		setItem(slot, item.build(), draggable);
	}

	public void setItem(int slot, ItemStack itemStack, boolean draggable) {
		inventory.setItem(slot, itemStack);
		if (draggable) draggableSlots.add(slot);
	}

	public void fillInventory() {
		ItemBuilder itemBuilder = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE);

		ItemStack item = itemBuilder.setDisplayName("&k||||").build();

		for (int i = 0; i < inventory.getSize(); i++) {
			if (inventory.getItem(i) != null) continue;

			inventory.setItem(i, item);
		}
	}

	public void open(User<Player> user) {
		user.getUser().openInventory(inventory);
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) return;

		for (InventoryGUI inventoryGUI : inventoryGUIs)
			if (clickedInventory.equals(inventoryGUI.inventory)) {
				event.setCancelled(!draggableSlots.contains(event.getRawSlot()));
				break;
			}

	}

}
