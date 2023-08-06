package me.luckyraven.bukkit.inventory;

import com.cryptomorin.xseries.XMaterial;
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

import java.util.*;
import java.util.function.BiConsumer;

public class Inventory implements Listener {

	public static final  int                           MAX_SLOTS   = 54;
	private static final Map<NamespacedKey, Inventory> INVENTORIES = new HashMap<>();

	private final @Getter int                                              size;
	private final         List<Integer>                                    draggableSlots;
	private final         Map<Integer, BiConsumer<Inventory, ItemBuilder>> clickableSlots;
	private final         Map<Integer, ItemBuilder>                        clickableItem;
	private final         JavaPlugin                                       plugin;

	private @Getter org.bukkit.inventory.Inventory inventory;
	private @Getter NamespacedKey                  title;
	private @Getter String                         displayTitle;

	public Inventory(JavaPlugin plugin, String title, int size) {
		this.plugin = plugin;
		this.displayTitle = title;
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

		this.displayTitle = name;
		this.title = new NamespacedKey(plugin, titleRefactor(name));
		INVENTORIES.put(this.title, this);
	}

	private String titleRefactor(@NotNull String title) {
		Preconditions.checkNotNull(title, "Title can't be null");

		String pattern = "[^a-z0-9/._-]";
		return ChatUtil.replaceColorCodes(title, "").replaceAll(" ", "_").toLowerCase().replaceAll(pattern, "");
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
					if (inventory.getItem(j) != null || j == slot) continue;

					setItem(j, item, false);
				} catch (ArrayIndexOutOfBoundsException ignored) {
				}
	}

	public void horizontalLine(int row, ItemStack... items) {
		int rows = size / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
		                            String.format("Rows need to be between 1 and %d inclusive", rows));

		// always 9 slots
		int slot = (row - 1) * 9;
		for (int i = 0; i < 9; i++) {

			if (inventory.getItem(slot + i) != null) continue;

			if (i < items.length) inventory.setItem(slot + i, items[i]);
			else inventory.setItem(slot + i, new ItemBuilder(getLineItem()).setDisplayName(
					SettingAddon.getInventoryLineName()).build());
		}
	}

	public void horizontalLine(int row, Material material, String name, boolean all) {
		int rows = size / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
		                            String.format("Rows need to be between 1 and %d inclusive", rows));
		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(name).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[9];

			Arrays.fill(items, item);
		}

		horizontalLine(row, items);
	}

	public void horizontalLine(int row) {
		horizontalLine(row, getLineItem(), SettingAddon.getInventoryLineName(), true);
	}

	public void verticalLine(int column, ItemStack... items) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		// from 1-6
		int rows = size / 9;
		for (int i = 0; i < rows; i++) {
			int slot = (column - 1) + 9 * i;

			if (inventory.getItem(slot) != null) continue;

			if (i < items.length) inventory.setItem(slot, items[i]);
			else inventory.setItem(slot, new ItemBuilder(getLineItem()).setDisplayName(
					SettingAddon.getInventoryLineName()).build());
		}
	}

	public void verticalLine(int column, Material material, String name, boolean all) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(name).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[size / 9];

			Arrays.fill(items, item);
		}

		verticalLine(column, items);
	}

	public void verticalLine(int column) {
		verticalLine(column, getLineItem(), SettingAddon.getInventoryLineName(), true);
	}

	public void createBoarder() {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem());

		ItemStack item = itemBuilder.setDisplayName(SettingAddon.getInventoryFillName()).build();

		int rows = size / 9;

		for (int i = 0; i < size; i++) {
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
		Material item = XMaterial.matchXMaterial(SettingAddon.getInventoryFillItem())
		                         .stream()
		                         .toList()
		                         .get(0)
		                         .parseMaterial();
		return item != null ? item : XMaterial.BLACK_STAINED_GLASS_PANE.parseMaterial();
	}

	private Material getLineItem() {
		Material item = XMaterial.matchXMaterial(SettingAddon.getInventoryLineItem())
		                         .stream()
		                         .toList()
		                         .get(0)
		                         .parseMaterial();
		return item != null ? item : XMaterial.WHITE_STAINED_GLASS_PANE.parseMaterial();
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
