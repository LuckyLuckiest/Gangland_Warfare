package me.luckyraven.bukkit.inventory;

import lombok.Getter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.data.user.User;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class Inventory implements Listener {

	public static final  int                           MAX_SLOTS   = 54;
	private static final Map<NamespacedKey, Inventory> INVENTORIES = new HashMap<>();

	@Getter
	private final int                                              size;
	private final List<Integer>                                    draggableSlots;
	private final Map<Integer, BiConsumer<Inventory, ItemBuilder>> clickableSlots;
	private final Map<Integer, ItemBuilder>                        clickableItem;
	private final JavaPlugin                                       plugin;

	@Getter
	private org.bukkit.inventory.Inventory inventory;
	private NamespacedKey                  title;

	public Inventory(JavaPlugin plugin, String title, int size) {
		this.plugin = plugin;
		this.title = new NamespacedKey(plugin, title);
		this.size = size;

		this.inventory = Bukkit.createInventory(null, size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		this.clickableSlots = new HashMap<>();
		this.clickableItem = new HashMap<>();

		INVENTORIES.put(this.title, this);
	}

	public void rename(String name) {
		ItemStack[] contents = inventory.getContents();

		inventory = Bukkit.createInventory(null, size, ChatUtil.color(name));
		inventory.setContents(contents);

		// remove the old inventory
		INVENTORIES.remove(title);

		this.title = new NamespacedKey(plugin, name);
		INVENTORIES.put(this.title, this);
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable) {
		setItem(slot, material, displayName, lore, enchanted, draggable, null);
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable, BiConsumer<Inventory, ItemBuilder> clickable) {
		ItemBuilder item = new ItemBuilder(material).setDisplayName(displayName).setLore(lore);

		if (enchanted) item.addEnchantment(Enchantment.DURABILITY, 1);

		setItem(slot, item, draggable, clickable);
	}

	public void setItem(int slot, ItemBuilder itemBuilder, boolean draggable,
	                    BiConsumer<Inventory, ItemBuilder> clickable) {
		setItem(slot, itemBuilder.build(), draggable);

		if (clickable != null) {
			clickableSlots.put(slot, clickable);
			clickableItem.put(slot, itemBuilder);
		}
	}

	public void setItem(int slot, ItemStack itemStack, boolean draggable) {
		inventory.setItem(slot, itemStack);
		if (draggable) draggableSlots.add(slot);
	}

	public void setItem(int slot, ItemStack itemStack, boolean draggable,
	                    BiConsumer<Inventory, ItemBuilder> clickable) {
		setItem(slot, new ItemBuilder(itemStack), draggable, clickable);
	}

	public void aroundSlot(int slot, Material material) {
		ItemBuilder itemBuilder = new ItemBuilder(material);

		ItemStack item = itemBuilder.setDisplayName(null).build();

		// left-mid : 21 -> 21 - 9 = 12, 21 + 9
		// mid      : 22 -> 22 - 9 = 13, 22 + 9
		// right-mid: 23 -> 23 - 9 = 14, 23 + 9

		int topLeft = slot - 10;

		for (int i = topLeft; i < topLeft + 3; i++)
			for (int j = i; j < i + 9 * 2 + 1; j += 9)
				try {
					if (j == slot) continue;

					setItem(j, item, false);
				} catch (ArrayIndexOutOfBoundsException ignored) {
				}
	}

	public void fillInventory() {
		ItemBuilder itemBuilder = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE);

		ItemStack item = itemBuilder.setDisplayName(null).build();

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
		org.bukkit.inventory.Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) return;

		Inventory inv = INVENTORIES.values().stream().filter(
				inventory -> clickedInventory.equals(inventory.getInventory())).findFirst().orElse(null);

		if (inv == null) return;

		int rawSlot = event.getRawSlot();

		inv.clickableSlots.getOrDefault(rawSlot, (i, item) -> {}).accept(this,
		                                                                 inv.clickableItem.getOrDefault(rawSlot, null));
		event.setCancelled(!inv.draggableSlots.contains(rawSlot));
	}

}
