package me.luckyraven.bukkit.inventory;

import com.google.common.base.Preconditions;
import lombok.Getter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.file.configuration.SettingAddon;
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
import org.jetbrains.annotations.NotNull;
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
	@Getter
	private NamespacedKey                  title;

	public Inventory(JavaPlugin plugin, String title, int size) {
		this.plugin = plugin;
		this.title = new NamespacedKey(plugin, titleRefactor(title));

		int realSize = factorOfNine(size);
		this.size = Math.min(realSize, MAX_SLOTS);

		this.inventory = Bukkit.createInventory(null, this.size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		this.clickableSlots = new HashMap<>();
		this.clickableItem = new HashMap<>();

		INVENTORIES.put(this.title, this);
	}

	private int factorOfNine(int value) {
		return (int) Math.ceil((double) value / 9) * 9;
	}

	public void rename(String name) {
		ItemStack[] contents = inventory.getContents();

		inventory = Bukkit.createInventory(null, this.size, ChatUtil.color(name));
		inventory.setContents(contents);

		// remove the old inventory
		INVENTORIES.remove(title);

		this.title = new NamespacedKey(plugin, titleRefactor(name));
		INVENTORIES.put(this.title, this);
	}

	private String titleRefactor(@NotNull String title) {
		Preconditions.checkNotNull(title, "Title can't be null");

		String pattern = "[^a-z0-9/._-]";

		String value = title.replaceAll(" ", "_");

		int count = (int) value.chars().filter(c -> c == '&').count();
		for (int i = 0; i < count; i++) {
			int index = value.indexOf('&');
			value = value.substring(0, index) + value.substring(index + 2);
		}

		return value.replaceAll(pattern, "");
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

	public void removeItem(int slot) {
		inventory.setItem(slot, null);

		draggableSlots.remove((Integer) slot);
		clickableSlots.remove(slot);
		clickableItem.remove(slot);
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

	public void createBoarder() {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem());

		ItemStack item = itemBuilder.setDisplayName(SettingAddon.getInventoryFillName()).build();

		int rows = inventory.getSize() / 9;

		for (int i = 0; i < inventory.getSize(); i++) {
			int row = i / 9;

			// place items on the borders only
			if (row == 0 || row == rows - 1 || i % 9 == 0 || i % 9 == 8) {
				if (inventory.getItem(i) != null) continue;
				inventory.setItem(i, item);
			}
		}
	}

	public void fillInventory() {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem());

		ItemStack item = itemBuilder.setDisplayName(SettingAddon.getInventoryFillName()).build();

		for (int i = 0; i < inventory.getSize(); i++) {
			if (inventory.getItem(i) != null) continue;

			inventory.setItem(i, item);
		}
	}

	private Material getFillItem() {
		Material item = Material.getMaterial(SettingAddon.getInventoryFillItem());
		return item != null ? item : Material.BLACK_STAINED_GLASS_PANE;
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

	public void close(Player player) {
		player.closeInventory();
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
