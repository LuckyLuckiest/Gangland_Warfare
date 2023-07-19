package me.luckyraven.bukkit.gui;

import lombok.Getter;
import me.luckyraven.bukkit.ItemBuilder;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class InventoryGUI implements Listener {

	public static final  int                MAX_SLOTS     = 54;
	private static final List<InventoryGUI> inventoryGUIs = new ArrayList<>();
	private final        Inventory          inventory;
	@Getter
	private final        int                size;
	private final        List<Integer>      draggableSlots;

	private final Map<Integer, BiConsumer<InventoryGUI, ItemBuilder>> clickableSlots;
	private final Map<Integer, ItemBuilder>                           clickableItem;

	public InventoryGUI(String title, int size) {
		this.inventory = Bukkit.createInventory(null, size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		this.clickableSlots = new HashMap<>();
		this.clickableItem = new HashMap<>();
		this.size = size;
		inventoryGUIs.add(this);
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable, BiConsumer<InventoryGUI, ItemBuilder> clickable) {
		ItemBuilder item = new ItemBuilder(material).setDisplayName(displayName).setLore(lore);

		if (enchanted) item.addEnchantment(Enchantment.DURABILITY, 1);

		setItem(slot, item.build(), draggable);

		if (clickable != null) {
			clickableSlots.put(slot, clickable);
			clickableItem.put(slot, item);
		}
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable) {
		setItem(slot, material, displayName, lore, enchanted, draggable, null);
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

	public void close(User<Player> user) {
		user.getUser().closeInventory();
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) return;

		for (InventoryGUI inventoryGUI : inventoryGUIs)
			if (clickedInventory.equals(inventoryGUI.inventory)) {
				int rawSlot = event.getRawSlot();

				if (inventoryGUI.clickableSlots.containsKey(rawSlot)) {
					inventoryGUI.clickableSlots.get(rawSlot).accept(this, inventoryGUI.clickableItem.get(rawSlot));
				}
				event.setCancelled(!inventoryGUI.draggableSlots.contains(rawSlot));
				break;
			}

	}

}
